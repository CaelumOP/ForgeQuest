package com.forgequest.app.domain

import com.forgequest.app.data.db.ExerciseEntity
import com.forgequest.app.data.seed.ExerciseCatalog
import kotlin.math.floor
import kotlin.math.max

sealed interface WeightedResult {
    data class Target(val weightKg: Double) : WeightedResult
    data object TooLight : WeightedResult
    data object NoTarget : WeightedResult
}

/** Pure starting-target calculations from a baseline test (3B spec #13). */
object BaselineTargets {

    /** Working reps from a bodyweight max-rep test: 70% floored, minimum 1; null when max <= 0. */
    fun repsTarget(max: Int): Int? =
        if (max <= 0) null else max(1, floor(0.7 * max + 1e-9).toInt())

    /** Working hold seconds from a max-hold test: 70% floored to the 5 s grid, minimum 5 s; null when <= 0. */
    fun holdTarget(maxSeconds: Int): Int? =
        if (maxSeconds <= 0) null else max(5, floor(0.7 * maxSeconds / 5.0 + 1e-9).toInt() * 5)

    /**
     * Working load from a submax weighted set: capped at 12 reps (`TooLight` above),
     * targets ~3 RIR at `repsMin` with 10% safety factor, snapped down to `loadStep`.
     */
    fun weightedTarget(weightKg: Double, reps: Int, repsMin: Int, loadStep: Double): WeightedResult {
        if (reps > 12) return WeightedResult.TooLight
        if (reps <= 0 || !weightKg.isFinite() || weightKg <= 0.0 || !loadStep.isFinite() || loadStep <= 0.0) {
            return WeightedResult.NoTarget
        }
        val effectiveRepsMin = max(1, repsMin)
        val e1rm = weightKg * (1.0 + reps / 30.0)
        val raw = e1rm / (1.0 + (effectiveRepsMin + 3) / 30.0) * 0.9
        val snapped = floor(raw / loadStep + 1e-9) * loadStep
        return if (snapped < loadStep) WeightedResult.NoTarget else WeightedResult.Target(snapped)
    }

    /**
     * Walks easier along the same progression line in [ExerciseTiers] until one fits [equipment],
     * else the easiest same-movement-pattern exercise that [equipment] allows (ties by id), else null.
     */
    fun easierVariation(
        exercise: ExerciseEntity,
        library: List<ExerciseEntity>,
        equipment: Set<Equipment> = emptySet(),
    ): ExerciseEntity? {
        if (exercise.id in ExerciseCatalog.STRETCH_IDS) return null
        val allowed = expandLegacyEquipment(equipment).ifEmpty { Equipment.allActive } + Equipment.BODYWEIGHT
        val byId = library.associateBy { it.id }

        var curId: String? = exercise.id
        val visited = HashSet<String>()
        while (curId != null && visited.add(curId)) {
            val prevId = ExerciseTiers.prevInLineId(curId) ?: break
            val candidate = byId[prevId]
            if (candidate != null &&
                candidate.id !in ExerciseCatalog.STRETCH_IDS &&
                candidate.kind == exercise.kind &&
                ExerciseSwapEngine.profileFor(candidate).fits(allowed)
            ) {
                return candidate
            }
            curId = prevId
        }

        val srcTier = ExerciseTiers.of(exercise)
        if (srcTier == Tier.FOUNDATION) return null
        val pattern = ExerciseSwapEngine.profileFor(exercise).pattern
        val candidates = library.mapNotNull { c ->
            if (c.id == exercise.id || c.id in ExerciseCatalog.STRETCH_IDS || c.kind != exercise.kind) return@mapNotNull null
            val profile = ExerciseSwapEngine.profileFor(c)
            if (profile.pattern != pattern || !profile.fits(allowed)) return@mapNotNull null
            val candTier = ExerciseTiers.of(c) ?: return@mapNotNull null
            if (srcTier != null && candTier.ordinal >= srcTier.ordinal) return@mapNotNull null
            c to candTier
        }
        return candidates
            .minWithOrNull(compareBy<Pair<ExerciseEntity, Tier>> { it.second.ordinal }.thenBy { it.first.id })
            ?.first
    }
}
