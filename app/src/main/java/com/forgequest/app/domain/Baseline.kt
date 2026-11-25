package com.forgequest.app.domain

import com.forgequest.app.data.db.ExerciseEntity
import com.forgequest.app.data.db.SetEntity
import kotlin.math.floor

/** Coarse movement-pattern buckets used to fall back across related patterns during cold-start estimation. */
enum class PatternBucket { PUSH, PULL, SQUAT, HINGE, CORE }

/** Readiness of an exercise's load baseline for the current session. */
enum class BaselineStatus { NONE, ESTIMATED, FROM_BASELINE, CALIBRATED }

/**
 * Pure on-the-fly baseline and cold-start load estimation engine (spec section 4 & A5).
 * Computes per-exercise e1RM from recent working sets and transfers load across exercises
 * in the same [MovementPattern] (or [PatternBucket]) using equipment leverage ratios.
 */
object Baseline {

    /** Cold-start recommendation for a weighted exercise with no prior working-set history. */
    data class Estimate(
        val weightKg: Float?,
        val status: BaselineStatus,
        val donorId: String? = null,
    )

    private val LOAD_IMPLEMENT_PRIORITY = listOf(
        Equipment.BARBELL,
        Equipment.SMITH_MACHINE,
        Equipment.CHEST_PRESS_MACHINE,
        Equipment.SHOULDER_PRESS_MACHINE,
        Equipment.PEC_DECK,
        Equipment.PREACHER_CURL_MACHINE,
        Equipment.MACHINE,
        Equipment.LEG_PRESS,
        Equipment.HACK_SQUAT,
        Equipment.LEG_EXTENSION,
        Equipment.LEG_CURL,
        Equipment.CALF_MACHINE,
        Equipment.CABLE_MACHINE,
        Equipment.LAT_PULLDOWN,
        Equipment.SEATED_ROW_MACHINE,
        Equipment.DUMBBELLS,
        Equipment.KETTLEBELL,
    )

    /** Maps a [MovementPattern] to its coarse [PatternBucket], or null for patterns without cross-transfer. */
    fun bucketOf(pattern: MovementPattern): PatternBucket? = when (pattern) {
        MovementPattern.HORIZONTAL_PUSH,
        MovementPattern.DIP_PUSH,
        MovementPattern.VERTICAL_PUSH -> PatternBucket.PUSH

        MovementPattern.VERTICAL_PULL,
        MovementPattern.HORIZONTAL_PULL,
        MovementPattern.UPPER_BACK_PULL -> PatternBucket.PULL

        MovementPattern.SQUAT_BILATERAL,
        MovementPattern.SQUAT_UNILATERAL -> PatternBucket.SQUAT

        MovementPattern.HIP_HINGE -> PatternBucket.HINGE

        MovementPattern.CORE_ANTI_EXTENSION,
        MovementPattern.CORE_FLEXION_ROTATION -> PatternBucket.CORE

        else -> null
    }

    /**
     * Epley-based estimated 1RM for a single working set using RIR from [SetEffort.rir].
     * Only reps in 1..12 with positive finite weight count; a 1-rep set to [SetEffort.FAILURE] returns [weightKg].
     */
    fun e1rm(weightKg: Float, reps: Int, effort: SetEffort): Float {
        if (!weightKg.isFinite() || weightKg <= 0f || reps !in 1..12) return 0f
        if (reps == 1 && effort == SetEffort.FAILURE) return weightKg
        val effectiveReps = minOf(reps + SetEffort.rir(effort), 12)
        return weightKg * (1f + effectiveReps / 30f)
    }

    private fun effortOf(set: SetEntity): SetEffort =
        if (set.effort.isBlank() && set.setType == SetType.FAILURE.name) {
            SetEffort.FAILURE
        } else {
            SetEffort.of(set.effort, set.note)
        }

    /**
     * Best estimated 1RM across working sets (`countsForProgression`, done, weight > 0) from the last 2 sessions.
     * [sets] must be ordered newest session first (matching [Progression.inputs]). Returns null if no eligible set qualifies.
     */
    fun exerciseE1rm(sets: List<SetEntity>): Float? {
        val eligible = sets.filter { countsForProgression(it) && it.done && (it.weightKg ?: 0f) > 0f }
        if (eligible.isEmpty()) return null
        val lastTwoSessions = eligible.map { it.sessionId }.distinct().take(2).toSet()
        return eligible.asSequence()
            .filter { it.sessionId in lastTwoSessions }
            .map { e1rm(it.weightKg ?: 0f, it.reps ?: 0, effortOf(it)) }
            .filter { it > 0f }
            .maxOrNull()
    }

    private fun ratioForEquipment(eq: Equipment): Float? = when (eq) {
        Equipment.BARBELL -> 1.0f
        Equipment.SMITH_MACHINE -> 0.9f
        Equipment.DUMBBELLS, Equipment.KETTLEBELL -> 0.4f
        Equipment.CABLE_MACHINE, Equipment.LAT_PULLDOWN, Equipment.SEATED_ROW_MACHINE -> 0.6f
        Equipment.CHEST_PRESS_MACHINE,
        Equipment.SHOULDER_PRESS_MACHINE,
        Equipment.PEC_DECK,
        Equipment.PREACHER_CURL_MACHINE,
        Equipment.MACHINE,
        Equipment.LEG_PRESS,
        Equipment.HACK_SQUAT,
        Equipment.LEG_EXTENSION,
        Equipment.LEG_CURL,
        Equipment.CALF_MACHINE -> 1.0f
        else -> null
    }

    /**
     * Returns the barbell-equivalent load ratio for [ex] from [ExerciseSwapEngine.profileFor].
     * Rule: check `profile.equipment` first; if it is a support item (e.g. a bench in a multi-equipment custom exercise),
     * pick the primary load implement from `profile.requires` using deterministic priority
     * (`BARBELL` > `SMITH_MACHINE` > machines > cables > `DUMBBELLS` > `KETTLEBELL`). Returns null for bodyweight/bands/other.
     */
    fun equipmentRatio(ex: ExerciseEntity): Float? {
        val profile = ExerciseSwapEngine.profileFor(ex)
        ratioForEquipment(profile.equipment)?.let { return it }
        val primaryLoad = LOAD_IMPLEMENT_PRIORITY.firstOrNull { it in profile.requires } ?: return null
        return ratioForEquipment(primaryLoad)
    }

    private data class Donor(
        val id: String,
        val e1rm: Float,
        val ratio: Float,
        val samePattern: Boolean,
        /** Position of the donor in [coldStart]'s history, which lists the most recent workout first. */
        val recency: Int,
        /** Share of the donor's strength that carries over to the target, see [transfer]. */
        val transfer: Float,
    )

    /**
     * How much of a [donor] movement's strength carries over to [target] (audit S6): the same movement 0.9; a press
     * into an overhead press, or two legs into one leg, 0.9 x 0.6; any other pair in the same [PatternBucket] 0.9 x 0.8.
     */
    fun transfer(donor: MovementPattern, target: MovementPattern): Float = when {
        donor == target -> 0.9f
        donor == MovementPattern.HORIZONTAL_PUSH && target == MovementPattern.VERTICAL_PUSH -> 0.9f * 0.6f
        donor == MovementPattern.SQUAT_BILATERAL && target == MovementPattern.SQUAT_UNILATERAL -> 0.9f * 0.6f
        else -> 0.9f * 0.8f
    }

    /**
     * Estimates a starting working weight for [target] at [repsMin] (targeting 3 RIR) from the most recent
     * (by [history]'s order: callers pass it grouped from sets ordered newest workout first, as the DAO returns them)
     * calibrated weighted donor in [history], preferring the same [MovementPattern] else the same [PatternBucket] (x0.8).
     */
    fun coldStart(
        target: ExerciseEntity,
        repsMin: Int,
        unit: WeightUnit,
        history: Map<String, List<SetEntity>>,
        library: Map<String, ExerciseEntity>,
    ): Estimate {
        val ownWorking = history[target.id].orEmpty().any { countsForProgression(it) && it.done }
        if (ownWorking) return Estimate(weightKg = null, status = BaselineStatus.CALIBRATED)
        if (target.kind != ExerciseKind.WEIGHTED) return Estimate(weightKg = null, status = BaselineStatus.NONE)
        // A fly's load says little about a press and the other way round (audit S6, Gate point 4).
        if (PlanBuilder.isFly(target)) return Estimate(weightKg = null, status = BaselineStatus.NONE)
        val targetRatio = equipmentRatio(target) ?: return Estimate(weightKg = null, status = BaselineStatus.NONE)

        val targetPattern = ExerciseSwapEngine.profileFor(target).pattern
        val targetBucket = bucketOf(targetPattern)

        val donors = mutableListOf<Donor>()
        for ((recency, entry) in history.entries.withIndex()) {
            val (donorId, donorSets) = entry
            if (donorId == target.id) continue
            val donorEx = library[donorId] ?: continue
            if (donorEx.kind != ExerciseKind.WEIGHTED || PlanBuilder.isFly(donorEx)) continue
            val donorRatio = equipmentRatio(donorEx) ?: continue
            val donorPattern = ExerciseSwapEngine.profileFor(donorEx).pattern
            val samePattern = donorPattern == targetPattern
            val sameBucket = targetBucket != null && bucketOf(donorPattern) == targetBucket
            if (!samePattern && !sameBucket) continue

            val eligible = donorSets.filter { countsForProgression(it) && it.done && (it.weightKg ?: 0f) > 0f }
            if (eligible.isEmpty()) continue
            val donorE1rm = exerciseE1rm(donorSets) ?: continue
            donors += Donor(
                id = donorId,
                e1rm = donorE1rm,
                ratio = donorRatio,
                samePattern = samePattern,
                recency = recency,
                transfer = transfer(donorPattern, targetPattern),
            )
        }

        val best = donors.minWithOrNull(
            compareByDescending<Donor> { it.samePattern }
                .thenBy { it.recency }
                .thenBy { it.id },
        ) ?: return Estimate(weightKg = null, status = BaselineStatus.NONE)

        val targetE1rm = best.e1rm / best.ratio * targetRatio * best.transfer
        val effectiveReps = repsMin.coerceAtLeast(1)
        val rawKg = targetE1rm / (1f + (effectiveReps + 3) / 30f)
        val step = unit.loadStep
        val flooredDisplay = floorToStep(unit.fromKg(rawKg), step)
        if (flooredDisplay < step - 1e-4f) {
            return Estimate(weightKg = null, status = BaselineStatus.NONE)
        }
        return Estimate(
            weightKg = unit.toKg(flooredDisplay),
            status = BaselineStatus.ESTIMATED,
            donorId = best.id,
        )
    }

    private fun floorToStep(value: Float, step: Float): Float =
        (floor((value / step).toDouble() + 1e-4) * step).toFloat()
}
