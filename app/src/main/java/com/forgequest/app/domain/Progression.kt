package com.forgequest.app.domain

import com.forgequest.app.data.db.PlanItemEntity
import com.forgequest.app.data.db.SetEntity
import com.forgequest.app.data.seed.ExerciseCatalog
import kotlin.math.roundToInt

/**
 * Today's prefill for one exercise from the last two times it was done. Pure, so the rules are unit tested:
 * - Rep range (e.g. 8–12): top of the range on every set adds one load step and resets to the bottom.
 * - Single rep target (e.g. 3×8): load only goes up after hitting it in two sessions in a row at the same
 *   weight, so one good day never starts a climb every session.
 * - Holds: +5 s per session up to 1.5× the plan's target, then they stop and the screen suggests a harder version.
 * - Hard or Pain on any set, a missed set, or a low-readiness day keeps last session's numbers.
 *   Pain also takes the load back one step (bodyweight moves keep their reps).
 * - Load steps follow the person's unit: +2.5 kg or +5 lb, landing on numbers they can actually load.
 */
object Progression {

    /** One finished set as the rules need it. */
    data class PastSet(val reps: Int?, val seconds: Int?, val weightKg: Float?, val effort: SetEffort)

    data class Target(val sets: Int, val repsMin: Int?, val repsMax: Int?, val seconds: Int?, val weightKg: Float?)

    data class Prefill(val reps: Int?, val seconds: Int?, val weightKg: Float?)

    /** Why today's numbers are what they are, for the one-line note on the live card. */
    enum class Reason { LOAD_UP, MORE_REPS, MORE_TIME, ONCE_MORE, SAME, HARD, PAIN, MISSED, LOW_READINESS }

    data class Result(val sets: List<Prefill>, val holdCeilingReached: Boolean = false, val reason: Reason? = null)

    const val HOLD_STEP_SEC = 5
    const val HOLD_CEILING = 1.5f

    /** What [next] needs for one exercise, apart from the unit and the readiness. */
    data class Inputs(val target: Target?, val last: List<PastSet>, val previous: List<PastSet>, val warmup: Boolean)

    /**
     * The inputs to [next] for one exercise, shared by the prefill and the debrief so both read history the same way:
     * the target from the plan [item] (null without one), the last two workouts in [recent] (done sets, newest workout
     * first), each in set order with its effort read through [SetEffort.of], and the warm-up check (an item in the
     * Warm-up block, or jumping jacks).
     */
    fun inputs(item: PlanItemEntity?, exerciseId: String?, recent: List<SetEntity>): Inputs {
        val sessionIds = recent.map { it.sessionId }.distinct()
        fun past(id: Long?) = recent.filter { it.sessionId == id }.sortedBy { it.setIndex }
            .map { PastSet(it.reps, it.seconds, it.weightKg, SetEffort.of(it.effort, it.note)) }
        val warmup = item?.block?.equals("Warm-up", ignoreCase = true) == true || exerciseId == "jumping_jacks" ||
            item?.block?.equals(PlanBuilder.COOL_DOWN_BLOCK, ignoreCase = true) == true || exerciseId in ExerciseCatalog.STRETCH_IDS
        return Inputs(
            target = item?.let { Target(it.sets, it.repsMin, it.repsMax, it.seconds, it.weightKg) },
            last = past(sessionIds.getOrNull(0)),
            previous = past(sessionIds.getOrNull(1)),
            warmup = warmup,
        )
    }

    /** Every 4th week since the plan was made is a lighter one: half the sets, same weights. [createdAt] is the plan's creation time in ms. */
    fun isDeloadWeek(createdAt: Long, todayEpochDay: Long): Boolean {
        val start = java.time.Instant.ofEpochMilli(createdAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toEpochDay()
        return ((todayEpochDay - start).coerceAtLeast(0) / 7) % 4 == 3L
    }

    /** Sets in a lighter week: half, rounded up. */
    fun deloadSets(sets: Int): Int = (sets + 1) / 2

    /**
     * What to do next time on one exercise: the sets with their reps, seconds and weight, and the [Reason] shown as "why".
     *
     * [target] is the plan's prescription (3 sets of 8 to 12, or a 30 s hold, when the plan gives none). [last] is the
     * done sets of the most recent workout with this exercise and [previous] those of the one before it.
     *
     * Anything goes up only after a completed workout: every planned set done, none marked Hard or Pain, and not a
     * warm-up. Even then it stays put when [lowReadiness] (recovery calls for an easier day) or in a [deload] week,
     * which also halves the sets ([deloadSets]).
     * - Weighted, with a rep range: one more rep per workout; once every set reaches the top of the range, the weight
     *   goes up one load step of [unit] and the reps return to the bottom (double progression).
     * - Weighted, with a fixed rep count: the weight goes up only after two clean workouts in a row at the same weight.
     * - A Pain set on a weighted exercise takes the weight one step down (never below one load step).
     * - Bodyweight reps: one more rep, up to two past the top of the range. Holds: a few seconds more once every set
     *   reaches the target, up to a ceiling above it.
     */
    fun next(
        kind: ExerciseKind,
        target: Target?,
        last: List<PastSet>,
        previous: List<PastSet>,
        unit: WeightUnit,
        lowReadiness: Boolean,
        warmup: Boolean,
        deload: Boolean = false,
    ): Result {
        val count = (target?.sets ?: 3).let { if (deload) deloadSets(it) else it }
        val repsMin = target?.repsMin ?: 8
        val repsMax = target?.repsMax ?: target?.repsMin ?: 12
        val holdTarget = target?.seconds ?: 30
        // The default 8–12 is a range too, so it resets to 8 after a load step (audit 2026-10-24).
        val hasRange = repsMax > repsMin

        val pain = last.any { it.effort == SetEffort.PAIN }
        val strained = pain || last.any { it.effort == SetEffort.HARD }
        // "Completed" means every set was done without a hard or painful one; only then may anything go up.
        val completed = !warmup && last.size >= count && !strained
        val mayProgress = completed && !lowReadiness && !deload

        fun hitTop(session: List<PastSet>) = session.size >= count && when (kind) {
            ExerciseKind.HOLD -> session.all { (it.seconds ?: 0) >= holdTarget }
            ExerciseKind.REPS, ExerciseKind.WEIGHTED -> session.all { (it.reps ?: 0) >= repsMax }
        }
        fun sessionWeight(session: List<PastSet>) = session.maxOfOrNull { it.weightKg ?: 0f } ?: 0f

        val holdCeiling = roundTo5((holdTarget * HOLD_CEILING).roundToInt()).coerceAtLeast(holdTarget)
        var ceilingReached = false

        val sets = (0 until count).map { i ->
            val lastSet = last.getOrNull(i) ?: last.lastOrNull()
            val lastWeight = lastSet?.weightKg ?: target?.weightKg ?: 0f
            when (kind) {
                ExerciseKind.HOLD -> {
                    val base = lastSet?.seconds ?: holdTarget
                    val next = if (mayProgress && hitTop(last) && base < holdCeiling) (base + HOLD_STEP_SEC).coerceAtMost(holdCeiling) else base
                    if (hitTop(last) && base >= holdCeiling) ceilingReached = true
                    val w = if (target?.weightKg != null || lastSet?.weightKg != null) lastWeight else null
                    Prefill(null, next, w)
                }
                ExerciseKind.WEIGHTED -> {
                    // One failed set (4 of 8) starts next time at the bottom of the range, not below it.
                    val baseReps = lastSet?.reps?.coerceAtLeast(repsMin) ?: target?.repsMin ?: 10
                    val loadUp = mayProgress && lastWeight > 0f && hitTop(last) && (
                        hasRange || (
                            hitTop(previous) &&
                                previous.none { it.effort == SetEffort.HARD || it.effort == SetEffort.PAIN } &&
                                sameWeight(sessionWeight(previous), sessionWeight(last))
                            )
                        )
                    when {
                        // Never below one load step: at 0 kg every load-up rule above is off, so the weight could never climb back.
                        pain && lastWeight > 0f -> Prefill(baseReps, null, stepDown(lastWeight, unit).coerceAtLeast(minOf(lastWeight, unit.toKg(unit.loadStep))))
                        loadUp && hasRange -> Prefill(repsMin, null, stepUp(lastWeight, unit))
                        loadUp -> Prefill(baseReps, null, stepUp(lastWeight, unit))
                        mayProgress && baseReps < repsMax -> Prefill(baseReps + 1, null, lastWeight)
                        else -> Prefill(baseReps, null, lastWeight)
                    }
                }
                ExerciseKind.REPS -> {
                    val baseReps = lastSet?.reps ?: target?.repsMin ?: 10
                    val next = if (mayProgress && baseReps < repsMax + 2) (baseReps + 1).coerceAtMost(999) else baseReps
                    val w = if (target?.weightKg != null || (lastSet?.weightKg != null && lastSet.weightKg > 0f)) lastWeight else null
                    Prefill(next, null, w)
                }
            }
        }

        fun topWeight(w: List<Float?>) = w.maxOfOrNull { it ?: 0f } ?: 0f
        val lastTop = sessionWeight(last)
        val nextTop = topWeight(sets.map { it.weightKg })
        val reason = when {
            warmup || last.isEmpty() || ceilingReached -> null
            strained -> if (pain && nextTop < lastTop - 0.1f) Reason.PAIN else Reason.HARD
            last.size < count -> Reason.MISSED
            lowReadiness && completed -> Reason.LOW_READINESS
            nextTop > lastTop + 0.1f -> Reason.LOAD_UP
            kind == ExerciseKind.HOLD && (sets.firstOrNull()?.seconds ?: 0) > (last.firstOrNull()?.seconds ?: 0) -> Reason.MORE_TIME
            sets.firstOrNull()?.reps?.let { it > (last.firstOrNull()?.reps ?: 0) } == true -> Reason.MORE_REPS
            kind == ExerciseKind.WEIGHTED && hitTop(last) && !hasRange -> Reason.ONCE_MORE
            else -> Reason.SAME
        }
        return Result(sets, ceilingReached, reason)
    }

    /** One load step up in the person's unit, landing on a loadable number (0.25 kg or 2.5 lb). */
    fun stepUp(kg: Float, unit: WeightUnit): Float = snap(unit.fromKg(kg) + unit.loadStep, unit).let(unit::toKg).coerceAtMost(999f)

    fun stepDown(kg: Float, unit: WeightUnit): Float = snap(unit.fromKg(kg) - unit.loadStep, unit).coerceAtLeast(0f).let(unit::toKg)

    private fun snap(value: Float, unit: WeightUnit): Float {
        val grid = if (unit == WeightUnit.LB) 2.5f else 0.25f
        return (value / grid).roundToInt() * grid
    }

    /** Equal once shown in either unit: 61.2 kg from a 135 lb entry and 61.23 kg are the same weight. */
    private fun sameWeight(a: Float, b: Float) = kotlin.math.abs(a - b) < 0.1f

    private fun roundTo5(sec: Int) = ((sec + 2) / 5) * 5
}
