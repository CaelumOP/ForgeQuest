package com.forgequest.app.domain

import com.forgequest.app.data.db.ExerciseEntity
import kotlin.math.floor
import kotlin.math.max

sealed interface WeightedResult {
    data class Target(val weightKg: Double) : WeightedResult
    data object TooLight : WeightedResult
    data object TooHeavy : WeightedResult
    data object NoTarget : WeightedResult
}

/** Pure starting-target calculations from a baseline test (3B spec #13). */
object BaselineTargets {

    /** Highest working-rep target we ever suggest. */
    const val MAX_REPS_TARGET = 20

    /** A bodyweight reps test at or above this many reps means the move is outgrown (progression caps near 15). */
    const val OUTGROWN_REPS = 25

    /** A reps test of 1..LOW_REPS means the move is too hard to start with. */
    const val LOW_REPS = 3

    const val MAX_HOLD_TARGET = 60
    const val OUTGROWN_HOLD = 90
    const val LOW_HOLD = 15

    const val MAX_REPS_INPUT = 200
    const val MAX_HOLD_INPUT = 600
    const val MAX_WEIGHT_KG_INPUT = 500.0
    const val MAX_WEIGHTED_REPS_INPUT = 100

    fun clampRepsInput(v: Int): Int = v.coerceIn(0, MAX_REPS_INPUT)
    fun clampHoldInput(v: Int): Int = v.coerceIn(0, MAX_HOLD_INPUT)
    fun clampWeightKgInput(v: Double): Double = if (v.isFinite()) v.coerceIn(0.0, MAX_WEIGHT_KG_INPUT) else 0.0
    fun clampWeightedRepsInput(v: Int): Int = v.coerceIn(0, MAX_WEIGHTED_REPS_INPUT)

    /** Working reps from a bodyweight max-rep test: 70% floored, minimum 1, capped at [MAX_REPS_TARGET]; null when max <= 0. */
    fun repsTarget(max: Int): Int? =
        if (max <= 0) null else minOf(MAX_REPS_TARGET, max(1, floor(0.7 * max + 1e-9).toInt()))

    /** True for a REPS max-reps result of [OUTGROWN_REPS]+ or a HOLD of [OUTGROWN_HOLD]+ seconds; WEIGHTED never. */
    fun isOutgrown(kind: ExerciseKind, value: Int): Boolean = when (kind) {
        ExerciseKind.REPS -> value >= OUTGROWN_REPS
        ExerciseKind.HOLD -> value >= OUTGROWN_HOLD
        ExerciseKind.WEIGHTED -> false
    }

    /** True for 1..[LOW_REPS] reps or 1..[LOW_HOLD]-1 seconds; 0 keeps the no-target path. */
    fun isTooHard(kind: ExerciseKind, value: Int): Boolean = when (kind) {
        ExerciseKind.REPS -> value in 1..LOW_REPS
        ExerciseKind.HOLD -> value in 1..(LOW_HOLD - 1)
        ExerciseKind.WEIGHTED -> false
    }

    /** Hold time text: under 60 s -> "45 [unitSuffix]" (suffix is "s" or "ث"), else "m:ss" with Latin digits ("1:00", "13:45"). */
    fun formatHoldSeconds(seconds: Int, unitSuffix: String): String {
        val s = max(0, seconds)
        return if (s < 60) "$s $unitSuffix" else "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
    }

    /** The next step up that fits [equipment] (reps or hold, either kind of move): [ExerciseTiers.harderStep]. Weighted never. */
    fun harderVariation(
        exercise: ExerciseEntity,
        library: List<ExerciseEntity>,
        equipment: Set<Equipment> = emptySet(),
    ): ExerciseEntity? = when (exercise.kind) {
        ExerciseKind.REPS, ExerciseKind.HOLD -> ExerciseTiers.harderStep(exercise, library, equipment)
        ExerciseKind.WEIGHTED -> null
    }

    /** Working hold seconds from a max-hold test: 70% floored to the 5 s grid (min 5), never above the max or [MAX_HOLD_TARGET]; null when <= 0. */
    fun holdTarget(maxSeconds: Int): Int? {
        if (maxSeconds <= 0) return null
        val grid = max(5, floor(0.7 * maxSeconds / 5.0 + 1e-9).toInt() * 5)
        return minOf(grid, maxSeconds, MAX_HOLD_TARGET)
    }

    /**
     * Working load from a submax weighted set: capped at 12 reps (`TooLight` above),
     * targets ~3 RIR at `repsMin` with 10% safety factor, snapped down to `loadStep`.
     */
    fun weightedTarget(weightKg: Double, reps: Int, repsMin: Int, loadStep: Double): WeightedResult {
        if (reps <= 0 || !weightKg.isFinite() || weightKg <= 0.0 || !loadStep.isFinite() || loadStep <= 0.0) {
            return WeightedResult.NoTarget
        }
        if (reps > 12) return WeightedResult.TooLight
        if (reps in 1..2) return WeightedResult.TooHeavy
        val effectiveRepsMin = max(1, repsMin)
        val e1rm = weightKg * (1.0 + reps / 30.0)
        val raw = e1rm / (1.0 + (effectiveRepsMin + 3) / 30.0) * 0.9
        val snapped = floor(raw / loadStep + 1e-9) * loadStep
        return if (snapped < loadStep) WeightedResult.NoTarget else WeightedResult.Target(snapped)
    }

    /** The next step down: the named line, else the same family one tier lower (see [ExerciseTiers.easierStep]). Null is the bottom for the gear. */
    fun easierVariation(
        exercise: ExerciseEntity,
        library: List<ExerciseEntity>,
        equipment: Set<Equipment> = emptySet(),
    ): ExerciseEntity? = ExerciseTiers.easierStep(exercise, library, equipment)
}
