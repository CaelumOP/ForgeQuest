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
                // A load step over 10 % of the weight is too big a cut: keep the weight, cap the reps (audit S5).
                val newWeightKg = if (displayW <= step + 1e-3f || step > 0.1f * displayW) {
                    wKg
                } else {
                    val floored = floorToStep(displayW * factor, step)
                    val newDisplay = minOf(floored, displayW - step).coerceAtLeast(step)
                    unit.toKg(newDisplay)
                }
                val droppedLoad = newWeightKg < wKg - 1e-4f
                // A lighter load aims for the bottom of the range again; at the lightest load, aim for what was done.
                // Never prescribes 0 reps (Gate 17).
                val targetReps = (if (droppedLoad) repsMin ?: source.reps else source.reps)?.coerceAtLeast(1)
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

    private fun isFailure(source: SetEntity): Boolean = source.effort == SetEffort.FAILURE.name ||
        (source.effort.isBlank() && source.setType == SetType.FAILURE.name)

    /** Whether [source] triggers an intra-session adjustment: a working set tagged Failure (A11 included) or 2+ reps short. */
    fun triggers(source: SetEntity, kind: ExerciseKind, repsMin: Int?): Boolean {
        if (!countsForProgression(source)) return false
        if (kind == ExerciseKind.HOLD) return isFailure(source)
        val repsMiss = repsMin != null && source.reps != null && source.reps <= repsMin - 2
        return isFailure(source) || repsMiss
    }

    /**
     * [set] unticked: effort cleared, and a FAILURE type the Failure tag gave goes back to WORKING (same rule as
     * [SetEffort.pick] leaving Failure), or A11 would still read the row as Failure and a re-tick would cut again.
     */
    fun unticked(set: SetEntity): SetEntity = set.copy(
        done = false,
        doneAt = null,
        effort = "",
        setType = if (set.effort == SetEffort.FAILURE.name && set.setType == SetType.FAILURE.name) {
            SetType.WORKING.name
        } else {
            set.setType
        },
    )

    /** Copies [values]' numbers onto matching rows from [current], skipping rows ticked or hand-edited in the meantime. */
    fun numbersToRestore(values: List<SetEntity>, current: List<SetEntity>, isEdited: (Long) -> Boolean): List<SetEntity> {
        val byId = current.associateBy { it.id }
        return values.mapNotNull { v ->
            val cur = byId[v.id] ?: return@mapNotNull null
            if (cur.done || isEdited(v.id)) return@mapNotNull null
            cur.copy(reps = v.reps, seconds = v.seconds, weightKg = v.weightKg).takeIf { it != cur }
        }
    }

    /**
     * What one trigger writes. [toWrite]: rows to write now (against the current rows). [cut]: the adjustment for the
     * cue and Undo, or null for a silent restore of pre-carry numbers. [before]: rows to record in the [Ledger].
     */
    data class Plan(val toWrite: List<SetEntity>, val cut: Adjustment?, val before: List<SetEntity>)

    /**
     * Plans the adjustment for [source]: targets come from [preCarry] (the later sets just before its tick), the write
     * compares against [current] (the rows now, after carry-forward). Null when [source] doesn't trigger.
     * A carry-forward that raised targets (did 12 of 10, then Failure) goes back silently when the cut changes nothing;
     * a real cut keeps its cue and Undo even when carry-forward already wrote the same numbers.
     */
    fun plan(
        source: SetEntity,
        preCarry: List<SetEntity>,
        current: List<SetEntity>,
        kind: ExerciseKind,
        repsMin: Int?,
        unit: WeightUnit,
        isEdited: (Long) -> Boolean,
    ): Plan? {
        if (!triggers(source, kind, repsMin)) return null
        // Only rows still open now: one ticked or hand-edited since the tick must not give a phantom cut or cue.
        val openIds = current.filter { !it.done && !isEdited(it.id) }.map { it.id }.toSet()
        val base = preCarry.filter { it.id in openIds }
        val cut = compute(source, base, kind, repsMin, unit)
        val changedById = cut?.changed.orEmpty().associateBy { it.id }
        val toWrite = numbersToRestore(base.map { changedById[it.id] ?: it }, current, isEdited)
        return Plan(toWrite = toWrite, cut = cut, before = base.filter { it.id in changedById })
    }

    /**
     * Which adjustment last wrote each open row. A row keeps a stack of source set ids (newest last) so reverting an
     * older adjustment never overwrites numbers a newer adjustment wrote since.
     */
    class Ledger {
        private val owners = mutableMapOf<Long, MutableList<Long>>()
        private val before = mutableMapOf<Long, List<SetEntity>>()

        val isEmpty: Boolean get() = owners.isEmpty() && before.isEmpty()

        /** Records that [sourceId]'s adjustment wrote the rows in [rows] (their numbers before that write). */
        fun record(sourceId: Long, rows: List<SetEntity>) {
            before[sourceId] = rows
            rows.forEach { owners.getOrPut(it.id) { mutableListOf() } += sourceId }
        }

        /**
         * Forgets [sourceId]'s adjustment and returns the rows whose numbers should go back: only rows it still owns.
         * A row a newer adjustment rewrote stays as it is; that newer adjustment inherits the older pre-adjustment numbers.
         */
        fun release(sourceId: Long): List<SetEntity> {
            val prev = before.remove(sourceId).orEmpty()
            val out = mutableListOf<SetEntity>()
            prev.forEach { v ->
                val stack = owners[v.id] ?: return@forEach
                val idx = stack.lastIndexOf(sourceId)
                if (idx < 0) return@forEach
                if (idx == stack.lastIndex) {
                    out += v
                } else {
                    val newer = stack[idx + 1]
                    before[newer]?.let { rows -> before[newer] = rows.map { if (it.id == v.id) v else it } }
                }
                stack.removeAt(idx)
                if (stack.isEmpty()) owners -= v.id
            }
            return out
        }
    }

    private fun floorToStep(value: Float, step: Float): Float =
        (floor((value / step).toDouble() + 1e-4) * step).toFloat()
}
