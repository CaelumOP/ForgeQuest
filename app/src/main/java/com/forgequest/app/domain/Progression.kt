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
    enum class Reason { LOAD_UP, MORE_REPS, MORE_TIME, ONCE_MORE, SAME, HARD, PAIN, MISSED, BACK_OFF, FATIGUE, EASIER }

    data class Result(val sets: List<Prefill>, val holdCeilingReached: Boolean = false, val reason: Reason? = null)

    const val HOLD_STEP_SEC = 5
    const val HOLD_CEILING = 1.5f

    /** One done row as the rules read it; a Failure-type row with no tag counts as Failure (A11). */
    fun pastSet(set: SetEntity): PastSet {
        val effort = if (set.effort.isBlank() && set.setType == SetType.FAILURE.name) SetEffort.FAILURE else SetEffort.of(set.effort, set.note)
        return PastSet(set.reps, set.seconds, set.weightKg, effort)
    }

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
        fun past(id: Long?) = eligible.filter { it.sessionId == id }.sortedBy { it.setIndex }.map(::pastSet)
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
     *   EASIER (two missed bodyweight workouts in a row) -> FATIGUE (missed at Failure once) -> Failure hold (target
     *   reps met) -> missed reps/sets -> mode rules. BACK_OFF also follows two workouts at the same top weight each with a
     *   set 2+ reps under the minimum; when one load step is over 10 % of the weight it keeps the weight (shown as MISSED).
     * - Weighted sets all start from the session's top weight; only sets at that weight count, lighter ones restart at
     *   the bottom of the range.
     * - Weighted, with a rep range: one more rep per workout; once every set reaches the top of the range, the weight
     *   goes up one load step of [unit] and the reps return to the bottom (double progression).
     * - High-rep bridge: in weighted DOUBLE progression with a rep range, when `stepUp(top, unit) > 1.2 * top`
     *   (more than 20 % heavier), reps climb to `repsMax + 3` (including on all-Easy sets) before the weight steps up
     *   and reps return to the bottom.
     * - Weighted, with a fixed rep count: the weight goes up only after two clean workouts in a row at the same weight.
     * - A Pain set on a weighted exercise takes the weight one step down (never below one load step).
     * - Bodyweight reps: one more rep, up to two past the top of the range, only when minimum met and not Failure; a
     *   missed workout puts every set at min(minimum, best set), never below 1.
     * - Holds read the best set rounded down to 5 s: missed (best under target) -> best + 5 s up to the target, or the
     *   best after Failure; met on every set -> 5 s more up to a ceiling above the target. Every set gets the same value.
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
        fun sessionWeight(session: List<PastSet>) = session.maxOfOrNull { it.weightKg ?: 0f } ?: 0f
        // Weighted: only the sets at the session's top weight say whether the weight was handled (audit S1, Gate 5).
        fun topSets(session: List<PastSet>): List<PastSet> =
            if (kind != ExerciseKind.WEIGHTED) session else sessionWeight(session).let { top -> session.filter { sameWeight(it.weightKg ?: 0f, top) } }
        fun missedAtFailure(session: List<PastSet>) =
            kind != ExerciseKind.HOLD && session.any { it.effort == SetEffort.FAILURE } && session.any { (it.reps ?: 0) < repsMin }
        // Two or more reps under the minimum, tagged or not (audit S4).
        fun farUnder(session: List<PastSet>) = kind != ExerciseKind.HOLD && session.any { (it.reps ?: 0) <= repsMin - 2 }
        fun bestSeconds(session: List<PastSet>) = session.maxOfOrNull { it.seconds ?: 0 } ?: 0
        // A missed bodyweight session: a rep set under the minimum, or a hold whose best stayed under the target (S2, S3).
        fun missedSession(session: List<PastSet>) = session.isNotEmpty() && when (kind) {
            ExerciseKind.REPS -> session.any { (it.reps ?: 0) < repsMin }
            ExerciseKind.HOLD -> bestSeconds(session) < holdTarget
            ExerciseKind.WEIGHTED -> false
        }
        val fatigue = missedAtFailure(last)
        // Back-off pairs two workouts at the same top weight only (Gate 16).
        val backOff = previous.isNotEmpty() &&
            ((fatigue && missedAtFailure(previous)) || (farUnder(last) && farUnder(previous))) &&
            (kind != ExerciseKind.WEIGHTED || sameWeight(sessionWeight(last), sessionWeight(previous)))
        // Two missed bodyweight workouts in a row suggest an easier version (S2, Gate 6: needs a previous workout).
        val easier = !warmup && missedSession(last) && missedSession(previous)
        val complete = last.size >= count
        val metMin = complete && when (kind) {
            ExerciseKind.HOLD -> last.all { (it.seconds ?: 0) >= holdTarget }
            ExerciseKind.WEIGHTED -> topSets(last).all { (it.reps ?: 0) >= repsMin }
            ExerciseKind.REPS -> last.all { (it.reps ?: 0) >= repsMin }
        }
        // Every set Easy (3+ reps left): add load without waiting for the top of the rep range.
        val allEasy = last.isNotEmpty() && last.all { it.effort == SetEffort.EASY }
        val mayProgress = !warmup && last.isNotEmpty() && metMin && !pain && !failed && !deload && mode != ProgressionMode.MAINTAIN

        // Holds read the session's best seconds, rounded down to 5 s, never under 5 s (audit S3).
        val holdBest = (bestSeconds(last) / HOLD_STEP_SEC * HOLD_STEP_SEC).coerceAtLeast(HOLD_STEP_SEC)
        fun hitTop(session: List<PastSet>, topReps: Int = repsMax) = session.size >= count && when (kind) {
            ExerciseKind.HOLD -> session.all { (it.seconds ?: 0) >= maxOf(holdTarget, holdBest) }
            ExerciseKind.REPS -> session.all { (it.reps ?: 0) >= repsMax }
            ExerciseKind.WEIGHTED -> topSets(session).all { (it.reps ?: 0) >= topReps }
        }
        fun stepDownFloor(w: Float) = stepDown(w, unit).coerceAtLeast(minOf(w, unit.toKg(unit.loadStep)))

        val lastTop = sessionWeight(last)
        val backOffWeighted = kind == ExerciseKind.WEIGHTED && backOff && lastTop > 0f
        // One load step is more than 10 % of the weight: a step down is too big, so back-off keeps the weight (audit S5).
        val bigStep = lastTop > 0f && unit.loadStep > 0.1f * unit.fromKg(lastTop)
        val holdCeiling = roundTo5((holdTarget * HOLD_CEILING).roundToInt()).coerceAtLeast(holdTarget)
        val holdMissed = kind == ExerciseKind.HOLD && !warmup && missedSession(last)
        var ceilingReached = false

        val sets = (0 until count).map { i ->
            val lastSet = last.getOrNull(i) ?: last.lastOrNull()
            val lastWeight = lastSet?.weightKg ?: target?.weightKg ?: 0f
            when (kind) {
                ExerciseKind.HOLD -> {
                    val next = when {
                        warmup || last.isEmpty() -> lastSet?.seconds ?: holdTarget
                        // Missed: Failure keeps the best, otherwise 5 s more, never past the target.
                        holdMissed -> if (failed) holdBest else (holdBest + HOLD_STEP_SEC).coerceAtMost(holdTarget).coerceAtLeast(holdBest)
                        mayProgress && hitTop(last) && holdBest < holdCeiling -> (holdBest + HOLD_STEP_SEC).coerceAtMost(holdCeiling)
                        else -> holdBest
                    }
                    if (!warmup && !holdMissed && !pain && !failed && !deload && mode != ProgressionMode.MAINTAIN &&
                        last.isNotEmpty() && hitTop(last) && holdBest >= holdCeiling
                    ) {
                        ceilingReached = true
                    }
                    val w = if (target?.weightKg != null || lastSet?.weightKg != null) lastWeight else null
                    Prefill(null, next, w)
                }
                ExerciseKind.WEIGHTED -> {
                    // Every set starts from the session's top weight; a set done lighter restarts at the bottom of the range (S1).
                    val top = if (lastTop > 0f) lastTop else lastWeight
                    val atTop = lastSet != null && sameWeight(lastSet.weightKg ?: 0f, lastTop)
                    // One missed set (4 of 8) starts next time at the bottom of the range, not below it.
                    val baseReps = if (atTop) lastSet?.reps?.coerceAtLeast(repsMin) ?: repsMin else repsMin
                    when {
                        warmup || last.isEmpty() -> Prefill(lastSet?.reps ?: target?.repsMin ?: 10, null, lastWeight)
                        pain && top > 0f -> Prefill(baseReps, null, stepDownFloor(top))
                        pain -> Prefill(baseReps, null, top)
                        backOffWeighted && bigStep -> Prefill(repsMin, null, top)
                        backOffWeighted -> Prefill(repsMin, null, stepDownFloor(top))
                        deload || mode == ProgressionMode.MAINTAIN ->
                            Prefill(if (mode == ProgressionMode.MAINTAIN && atTop) lastSet?.reps ?: baseReps else baseReps, null, top)
                        fatigue -> Prefill(repsMin, null, top)
                        failed -> Prefill(baseReps, null, top)
                        !metMin -> Prefill(baseReps, null, top)
                        mode == ProgressionMode.LINEAR -> {
                            if (top > 0f) Prefill(repsMin, null, stepUpLinear(top, unit, lowerBody))
                            else Prefill(baseReps, null, top)
                        }
                        else -> {
                            // High-rep bridge: when the next step is > 20 % heavier, climb to repsMax + 3 before loading up.
                            val bridge = hasRange && top > 0f && stepUp(top, unit) > 1.2f * top
                            val topReps = if (bridge) repsMax + 3 else repsMax
                            val easyUp = allEasy && top > 0f && !bridge
                            val loadUp = easyUp || (top > 0f && hitTop(last, topReps) && (
                                hasRange || (
                                    hitTop(previous) &&
                                        previous.none { it.effort == SetEffort.FAILURE || it.effort == SetEffort.PAIN } &&
                                        sameWeight(sessionWeight(previous), lastTop)
                                    )
                                ))
                            when {
                                loadUp && hasRange -> Prefill(repsMin, null, stepUp(top, unit))
                                loadUp -> Prefill(baseReps, null, stepUp(top, unit))
                                // A set done lighter restarts at the minimum at the top weight, with no extra rep (S1).
                                !atTop -> Prefill(repsMin, null, top)
                                baseReps < topReps -> Prefill(baseReps + 1, null, top)
                                else -> Prefill(baseReps, null, top)
                            }
                        }
                    }
                }
                ExerciseKind.REPS -> {
                    val baseReps = (lastSet?.reps ?: target?.repsMin ?: 10).coerceAtLeast(1)
                    val next = when {
                        // Missed: every set at the reps the best set reached, up to the minimum, never 0 (audit S2).
                        !warmup && missedSession(last) -> minOf(repsMin, last.maxOf { it.reps ?: 0 }).coerceAtLeast(1)
                        mayProgress && baseReps < repsMax + 2 -> (baseReps + 1).coerceAtMost(999)
                        else -> baseReps
                    }
                    val w = if (target?.weightKg != null || (lastSet?.weightKg != null && lastSet.weightKg > 0f)) lastWeight else null
                    Prefill(next, null, w)
                }
            }
        }

        val nextTop = sets.maxOfOrNull { it.weightKg ?: 0f } ?: 0f
        val reason = when {
            warmup || last.isEmpty() || ceilingReached -> null
            pain -> if (kind == ExerciseKind.WEIGHTED && nextTop < lastTop - 0.1f) Reason.PAIN else Reason.HARD
            // A back-off that keeps the weight reads as a missed workout (Gate 7).
            backOffWeighted -> if (bigStep) Reason.MISSED else Reason.BACK_OFF
            deload || mode == ProgressionMode.MAINTAIN -> Reason.SAME
            easier -> Reason.EASIER
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
