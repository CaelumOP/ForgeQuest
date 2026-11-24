package com.forgequest.app.domain

import com.forgequest.app.data.db.SetEntity
import kotlin.math.floor

/**
 * Pure intra-session adjustment rules: after a working set hits Failure or misses the target rep minimum by 2+,
 * adjusts remaining unticked, unedited working sets of the same exercise, and computes Failure rest extensions.
 */
object IntraSession {

    const val FAILURE_REST_BONUS_SEC = 45
    const val FAILURE_REST_CAP_SEC = 240

    /**
     * Resulting adjusted rows plus the cue payload for the ViewModel banner:
     * [percent] when weighted load dropped, [reps] for bodyweight (or lightest-load fallback), [seconds] for holds.
     */
    data class Adjustment(
        val changed: List<SetEntity>,
        val percent: Int? = null,
        val reps: Int? = null,
        val seconds: Int? = null,
    )

    /** Percentage drop for a triggered weighted set: 10% when 3+ reps under [repsMin], else 5%. */
    fun percentCut(source: SetEntity, repsMin: Int?): Int {
        val miss = if (repsMin != null && source.reps != null) (repsMin - source.reps).coerceAtLeast(0) else 0
        return if (miss >= 3) 10 else 5
    }

    /** Extends [currentTotalSec] by 45 s capped at 240 s, never reducing an already longer timer. */
    fun extendedRest(currentTotalSec: Int): Int =
        maxOf(currentTotalSec, minOf(currentTotalSec + FAILURE_REST_BONUS_SEC, FAILURE_REST_CAP_SEC))

    /**
     * Computes adjusted copies of [remaining] when [source] triggers an intra-session adjustment, or null if none.
     * Caller filters [remaining] (same itemPosition + exerciseId, higher setIndex, not done, not hand-edited, countsForProgression).
     */
    fun compute(
        source: SetEntity,
        remaining: List<SetEntity>,
        kind: ExerciseKind,
        repsMin: Int?,
        unit: WeightUnit,
    ): Adjustment? {
        if (!countsForProgression(source) || remaining.isEmpty()) return null
        val isFailure = source.effort == SetEffort.FAILURE.name ||
            (source.effort.isBlank() && source.setType == SetType.FAILURE.name)
        val repsMiss = kind != ExerciseKind.HOLD &&
            repsMin != null &&
            source.reps != null &&
            source.reps <= repsMin - 2
        if (!isFailure && !repsMiss) return null

        return when (kind) {
            ExerciseKind.WEIGHTED -> {
                val wKg = source.weightKg ?: 0f
                if (wKg <= 0f) return null
                val cut = percentCut(source, repsMin)
                val factor = if (cut == 10) 0.90f else 0.95f
                val step = unit.loadStep
                val displayW = unit.fromKg(wKg)
                val newWeightKg = if (displayW <= step + 1e-3f) {
                    wKg
                } else {
                    val floored = floorToStep(displayW * factor, step)
                    val newDisplay = minOf(floored, displayW - step).coerceAtLeast(step)
                    unit.toKg(newDisplay)
                }
                val droppedLoad = newWeightKg < wKg - 1e-4f
                // A lighter load aims for the bottom of the range again; at the lightest load, aim for what was done.
                val targetReps = if (droppedLoad) repsMin ?: source.reps else source.reps
                val changed = remaining.mapNotNull { next ->
                    val updated = next.copy(
                        weightKg = newWeightKg,
                        reps = targetReps?.let { t -> if (droppedLoad) t else minOf(t, next.reps ?: t) } ?: next.reps,
                    )
                    updated.takeIf { it != next }
                }
                if (changed.isEmpty()) return null
                Adjustment(
                    changed = changed,
                    percent = if (droppedLoad) cut else null,
                    reps = if (!droppedLoad) targetReps else null,
                )
            }
            ExerciseKind.REPS -> {
                val sourceReps = source.reps ?: return null
                val targetReps = sourceReps.coerceAtLeast(1)
                // Never raises a target: failing at 12 when 10 was planned leaves the 10.
                val changed = remaining.mapNotNull { next ->
                    val updated = next.copy(reps = minOf(targetReps, next.reps ?: targetReps))
                    updated.takeIf { it != next }
                }
                if (changed.isEmpty()) return null
                Adjustment(changed = changed, reps = targetReps)
            }
            ExerciseKind.HOLD -> {
                if (!isFailure) return null
                val sourceSec = source.seconds ?: return null
                val targetSec = sourceSec.coerceAtLeast(1)
                val changed = remaining.mapNotNull { next ->
                    val updated = next.copy(seconds = minOf(targetSec, next.seconds ?: targetSec))
                    updated.takeIf { it != next }
                }
                if (changed.isEmpty()) return null
                Adjustment(changed = changed, seconds = targetSec)
            }
        }
    }

    /** Returns only the changed copies of [remaining], or empty when no trigger/change applies. */
    fun adjust(
        source: SetEntity,
        remaining: List<SetEntity>,
        kind: ExerciseKind,
        repsMin: Int?,
        unit: WeightUnit,
    ): List<SetEntity> = compute(source, remaining, kind, repsMin, unit)?.changed.orEmpty()

    private fun floorToStep(value: Float, step: Float): Float =
        (floor((value / step).toDouble() + 1e-4) * step).toFloat()
}
