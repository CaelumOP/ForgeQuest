package com.forgequest.app.domain

import com.forgequest.app.data.db.PlanItemEntity
import com.forgequest.app.data.db.SetEntity
import com.forgequest.app.data.seed.ExerciseCatalog
import kotlin.math.roundToInt

enum class ProgressionMode {
    DOUBLE,
    LINEAR,
    MAINTAIN;

    companion object {
        fun parse(raw: String?): ProgressionMode =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: DOUBLE
    }
}

/**
 * Today's prefill for one exercise from the last two times it was done. Pure, so the rules are unit tested:
 * - DOUBLE (default): rep range climbs reps then adds one load step and resets to bottom; single rep target needs 2 clean sessions.
 * - LINEAR: adds +2.5 kg upper / +5 kg lower (+5 lb / +10 lb in lb) per clean session where every set hit repsMin.
 * - MAINTAIN: holds last session's weight/reps/seconds on clean sessions.
 * - Precedence: warm-up -> pain -> BACK_OFF -> deload / MAINTAIN -> FATIGUE -> failure hold -> missed -> mode rules.
 * - HARD sets count as clean; only FAILURE (or PAIN) blocks progression.
 * - Uses WP1a [countsForProgression] so warm-up/drop/rest-pause sets never count.
 */
object Progression {

    private val LOWER_BODY_MUSCLES = setOf(
        Muscle.QUADS, Muscle.HAMSTRINGS, Muscle.GLUTES, Muscle.ADDUCTORS, Muscle.CALVES, Muscle.TIBIALIS,
    )

    fun isLowerBody(primary: List<Muscle>): Boolean = primary.any { it in LOWER_BODY_MUSCLES }

    /** One finished set as the rules need it. */
    data class PastSet(val reps: Int?, val seconds: Int?, val weightKg: Float?, val effort: SetEffort)

    data class Target(
        val sets: Int,
        val repsMin: Int?,
        val repsMax: Int?,
        val seconds: Int?,
        val weightKg: Float?,
        val mode: ProgressionMode = ProgressionMode.DOUBLE,
    )

    data class Prefill(val reps: Int?, val seconds: Int?, val weightKg: Float?)

    /** Why today's numbers are what they are, for the one-line note on the live card. */
    enum class Reason { LOAD_UP, MORE_REPS, MORE_TIME, ONCE_MORE, SAME, HARD, PAIN, MISSED, BACK_OFF, FATIGUE }

    data class Result(val sets: List<Prefill>, val holdCeilingReached: Boolean = false, val reason: Reason? = null)

    const val HOLD_STEP_SEC = 5
    const val HOLD_CEILING = 1.5f

    /** What [next] needs for one exercise, apart from the unit. */
    data class Inputs(
        val target: Target?,
        val last: List<PastSet>,
        val previous: List<PastSet>,
        val warmup: Boolean,
        val mode: ProgressionMode = target?.mode ?: ProgressionMode.DOUBLE,
    )

    /**
     * The inputs to [next] for one exercise, shared by the prefill and the debrief so both read history the same way:
     * filters [recent] through [countsForProgression] so warm-up, dropset, and rest-pause sets never skew progression.
     */
    fun inputs(item: PlanItemEntity?, exerciseId: String?, recent: List<SetEntity>): Inputs {
        val eligible = recent.filter { countsForProgression(it) }
        val sessionIds = eligible.map { it.sessionId }.distinct()
        fun past(id: Long?) = eligible.filter { it.sessionId == id }.sortedBy { it.setIndex }
            .map {
                val effort = if (it.effort.isBlank() && it.setType == SetType.FAILURE.name) {
                    SetEffort.FAILURE
                } else {
                    SetEffort.of(it.effort, it.note)
                }
                PastSet(it.reps, it.seconds, it.weightKg, effort)
            }
        val warmup = item?.block?.equals("Warm-up", ignoreCase = true) == true || exerciseId == "jumping_jacks" ||
            item?.block?.equals(PlanBuilder.COOL_DOWN_BLOCK, ignoreCase = true) == true || exerciseId in ExerciseCatalog.STRETCH_IDS
        val mode = ProgressionMode.parse(item?.progressionMode)
        return Inputs(
            target = item?.let { Target(it.sets, it.repsMin, it.repsMax, it.seconds, it.weightKg, mode) },
            last = past(sessionIds.getOrNull(0)),
            previous = past(sessionIds.getOrNull(1)),
            warmup = warmup,
            mode = mode,
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
     * Anything goes up only after a completed workout: every planned set done hitting at least [Target.repsMin], none
     * marked Failure or Pain (Hard sets count as clean), and not a warm-up. Even then it stays put in a [deload] week
     * (which also halves the sets via [deloadSets]) or in [ProgressionMode.MAINTAIN].
     * - Precedence: warm-up -> pain -> BACK_OFF (missed at Failure two workouts in a row) -> deload / MAINTAIN ->
     *   FATIGUE (missed at Failure once) -> Failure hold (target reps met) -> missed reps/sets -> mode rules.
     * - Weighted, with a rep range: one more rep per workout; once every set reaches the top of the range, the weight
     *   goes up one load step of [unit] and the reps return to the bottom (double progression).
     * - Weighted, with a fixed rep count: the weight goes up only after two clean workouts in a row at the same weight.
     * - A Pain set on a weighted exercise takes the weight one step down (never below one load step).
     * - Bodyweight reps: one more rep, up to two past the top of the range, only when minimum met and not Failure; missed
     *   holds at the reps done. Holds: a few seconds more once every set reaches the target, up to a ceiling above it.
     */
    fun next(
        kind: ExerciseKind,
        target: Target?,
        last: List<PastSet>,
        previous: List<PastSet>,
        unit: WeightUnit,
        warmup: Boolean,
        deload: Boolean = false,
        mode: ProgressionMode = target?.mode ?: ProgressionMode.DOUBLE,
        lowerBody: Boolean = false,
    ): Result {
        val count = (target?.sets ?: 3).let { if (deload) deloadSets(it) else it }
        val repsMin = target?.repsMin ?: 8
        val repsMax = target?.repsMax ?: target?.repsMin ?: 12
        val holdTarget = target?.seconds ?: 30
        // The default 8–12 is a range too, so it resets to 8 after a load step (audit 2026-10-24).
        val hasRange = repsMax > repsMin

        val pain = last.any { it.effort == SetEffort.PAIN }
        val failed = last.any { it.effort == SetEffort.FAILURE }
        fun missedAtFailure(session: List<PastSet>) =
            kind != ExerciseKind.HOLD && session.any { it.effort == SetEffort.FAILURE } && session.any { (it.reps ?: 0) < repsMin }
        val fatigue = missedAtFailure(last)
        val backOff = fatigue && missedAtFailure(previous)
        val complete = last.size >= count
        val metMin = complete && (kind == ExerciseKind.HOLD || last.all { (it.reps ?: 0) >= repsMin })
        // Every set Easy (3+ reps left): add load without waiting for the top of the rep range.
        val allEasy = last.isNotEmpty() && last.all { it.effort == SetEffort.EASY }
        val mayProgress = !warmup && last.isNotEmpty() && metMin && !pain && !failed && !deload && mode != ProgressionMode.MAINTAIN

        fun hitTop(session: List<PastSet>) = session.size >= count && when (kind) {
            ExerciseKind.HOLD -> session.all { (it.seconds ?: 0) >= holdTarget }
            ExerciseKind.REPS, ExerciseKind.WEIGHTED -> session.all { (it.reps ?: 0) >= repsMax }
        }
        fun sessionWeight(session: List<PastSet>) = session.maxOfOrNull { it.weightKg ?: 0f } ?: 0f
        fun stepDownFloor(w: Float) = stepDown(w, unit).coerceAtLeast(minOf(w, unit.toKg(unit.loadStep)))

        val lastTop = sessionWeight(last)
        val backOffWeighted = kind == ExerciseKind.WEIGHTED && backOff && lastTop > 0f
        val holdCeiling = roundTo5((holdTarget * HOLD_CEILING).roundToInt()).coerceAtLeast(holdTarget)
        var ceilingReached = false

        val sets = (0 until count).map { i ->
            val lastSet = last.getOrNull(i) ?: last.lastOrNull()
            val lastWeight = lastSet?.weightKg ?: target?.weightKg ?: 0f
            when (kind) {
                ExerciseKind.HOLD -> {
                    val base = lastSet?.seconds ?: holdTarget
                    val next = if (mayProgress && hitTop(last) && base < holdCeiling) (base + HOLD_STEP_SEC).coerceAtMost(holdCeiling) else base
                    if (!warmup && !pain && !failed && !deload && mode != ProgressionMode.MAINTAIN && hitTop(last) && base >= holdCeiling) {
                        ceilingReached = true
                    }
                    val w = if (target?.weightKg != null || lastSet?.weightKg != null) lastWeight else null
                    Prefill(null, next, w)
                }
                ExerciseKind.WEIGHTED -> {
                    // One missed set (4 of 8) starts next time at the bottom of the range, not below it.
                    val baseReps = lastSet?.reps?.coerceAtLeast(repsMin) ?: target?.repsMin ?: 10
                    when {
                        warmup || last.isEmpty() -> Prefill(lastSet?.reps ?: target?.repsMin ?: 10, null, lastWeight)
                        pain && lastWeight > 0f -> Prefill(baseReps, null, stepDownFloor(lastWeight))
                        pain -> Prefill(baseReps, null, lastWeight)
                        backOffWeighted -> Prefill(repsMin, null, if (lastWeight > 0f) stepDownFloor(lastWeight) else lastWeight)
                        deload || mode == ProgressionMode.MAINTAIN -> Prefill(if (mode == ProgressionMode.MAINTAIN) lastSet?.reps ?: baseReps else baseReps, null, lastWeight)
                        fatigue -> Prefill(repsMin, null, lastWeight)
                        failed -> Prefill(baseReps, null, lastWeight)
                        !metMin -> Prefill(baseReps, null, lastWeight)
                        mode == ProgressionMode.LINEAR -> {
                            if (lastWeight > 0f) Prefill(repsMin, null, stepUpLinear(lastWeight, unit, lowerBody))
                            else Prefill(baseReps, null, lastWeight)
                        }
                        else -> {
                            val easyUp = allEasy && lastWeight > 0f
                            val loadUp = easyUp || (lastWeight > 0f && hitTop(last) && (
                                hasRange || (
                                    hitTop(previous) &&
                                        previous.none { it.effort == SetEffort.FAILURE || it.effort == SetEffort.PAIN } &&
                                        sameWeight(sessionWeight(previous), lastTop)
                                    )
                                ))
                            when {
                                loadUp && hasRange -> Prefill(repsMin, null, stepUp(lastWeight, unit))
                                loadUp -> Prefill(baseReps, null, stepUp(lastWeight, unit))
                                baseReps < repsMax -> Prefill(baseReps + 1, null, lastWeight)
                                else -> Prefill(baseReps, null, lastWeight)
                            }
                        }
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

        val nextTop = sets.maxOfOrNull { it.weightKg ?: 0f } ?: 0f
        val reason = when {
            warmup || last.isEmpty() || ceilingReached -> null
            pain -> if (kind == ExerciseKind.WEIGHTED && nextTop < lastTop - 0.1f) Reason.PAIN else Reason.HARD
            backOffWeighted -> Reason.BACK_OFF
            deload || mode == ProgressionMode.MAINTAIN -> Reason.SAME
            fatigue -> Reason.FATIGUE
            failed -> Reason.HARD
            !metMin -> Reason.MISSED
            nextTop > lastTop + 0.1f -> Reason.LOAD_UP
            kind == ExerciseKind.HOLD && sets.indices.any { i ->
                (sets[i].seconds ?: 0) > (last.getOrNull(i)?.seconds ?: last.lastOrNull()?.seconds ?: 0)
            } -> Reason.MORE_TIME
            sets.indices.any { i ->
                (sets[i].reps ?: 0) > (last.getOrNull(i)?.reps ?: last.lastOrNull()?.reps ?: 0)
            } -> Reason.MORE_REPS
            mode == ProgressionMode.DOUBLE && kind == ExerciseKind.WEIGHTED && hitTop(last) && !hasRange -> Reason.ONCE_MORE
            else -> Reason.SAME
        }
        return Result(sets, ceilingReached, reason)
    }

    /** Linear step in the person's display unit: +2.5/+5 kg or +5/+10 lb, snapped to the unit grid. */
    fun stepUpLinear(kg: Float, unit: WeightUnit, lowerBody: Boolean): Float {
        val delta = if (lowerBody) unit.loadStep * 2f else unit.loadStep
        return snap(unit.fromKg(kg) + delta, unit).let(unit::toKg).coerceAtMost(999f)
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
