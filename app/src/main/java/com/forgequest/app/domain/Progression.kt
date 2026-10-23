package com.forgequest.app.domain

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

    data class Result(val sets: List<Prefill>, val holdCeilingReached: Boolean = false)

    const val HOLD_STEP_SEC = 5
    const val HOLD_CEILING = 1.5f

    fun next(
        kind: ExerciseKind,
        target: Target?,
        last: List<PastSet>,
        previous: List<PastSet>,
        unit: WeightUnit,
        lowReadiness: Boolean,
        warmup: Boolean,
    ): Result {
        val count = target?.sets ?: 3
        val repsMin = target?.repsMin ?: 8
        val repsMax = target?.repsMax ?: target?.repsMin ?: 12
        val holdTarget = target?.seconds ?: 30
        val hasRange = target?.repsMin != null && target.repsMax != null && target.repsMax > target.repsMin

        val pain = last.any { it.effort == SetEffort.PAIN }
        val strained = pain || last.any { it.effort == SetEffort.HARD }
        // "Completed" means every set was done without a hard or painful one; only then may anything go up.
        val completed = !warmup && last.size >= count && !strained
        val mayProgress = completed && !lowReadiness

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
                    val baseReps = lastSet?.reps ?: target?.repsMin ?: 10
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
        return Result(sets, ceilingReached)
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
