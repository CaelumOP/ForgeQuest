package com.forgequest.app.domain

import com.forgequest.app.data.db.ExerciseEntity
import com.forgequest.app.data.seed.ExerciseCatalog
import com.forgequest.app.data.seed.ExerciseLevels

/** How hard a movement is to learn and its leverage, not the load (the person picks the weight). */
enum class Tier { FOUNDATION, INTERMEDIATE, ADVANCED }

/**
 * Difficulty of the built-in exercises (docs/plans/exercise-tiers.md) and the "ready for a harder version" ladder for
 * bodyweight rep moves. Custom exercises have no tier. Web: domain/exercise-tiers.ts.
 */
object ExerciseTiers {
    private val INTERMEDIATE = setOf(
        "burpee", "push_up", "diamond_push_up", "pike_push_up", "pull_up", "chin_up", "dip", "bulgarian_split_squat",
        "hollow_hold", "hanging_knee_raise", "weighted_push_up", "bench_press", "overhead_press", "barbell_row", "deadlift",
        "romanian_deadlift", "back_squat", "skull_crusher", "kb_swing", "kb_clean", "turkish_get_up", "db_lunge",
        "copenhagen_plank_short", "cossack_squat",
        "single_leg_rdl", "good_morning", "kb_windmill",
        "neutral_grip_pull_up", "wide_grip_pull_up", "decline_push_up", "single_leg_hip_thrust", "incline_bench_press", "front_squat",
        "table_inverted_row",
    )
    private val ADVANCED = setOf(
        "pistol_squat", "nordic_curl", "hanging_leg_raise", "muscle_up", "l_sit", "handstand_hold", "handstand_push_up",
        "tuck_planche", "pseudo_planche_push_up", "tuck_front_lever", "front_lever_row", "back_lever_tuck",
        "weighted_pull_up", "weighted_chin_up", "weighted_dip", "kb_snatch", "copenhagen_plank_long",
        "archer_pull_up", "l_sit_pull_up", "archer_push_up",
        // Imported levels label explosive and unilateral push-ups as beginner.
        "plyo_push_up", "single_arm_push_up", "incline_push_up_depth_jump", "drop_push",
    )

    fun of(ex: ExerciseEntity): Tier? = when {
        ex.custom -> null
        ex.id in ADVANCED -> Tier.ADVANCED
        ex.id in INTERMEDIATE -> Tier.INTERMEDIATE
        else -> when (ExerciseLevels.byId[ex.id]) {
            "expert" -> Tier.ADVANCED
            "intermediate" -> Tier.INTERMEDIATE
            else -> Tier.FOUNDATION
        }
    }

    /** Every set at this many reps, two sessions in a row, means the move has become easy. */
    const val READY_REPS = 15

    /** Steps inside one tier where the next move is obvious (half squats → full squats). */
    private val NEXT_IN_TIER = mapOf(
        "half_squat" to "squat", "half_wide_squat" to "squat", "half_narrow_squat" to "squat",
        "knee_scapular_push_up" to "knee_push_up",
        "scapular_australian_pull_up" to "australian_pull_up", "scapular_australian_chin_up" to "australian_chin_up",
    )

    private val EASIER_IN_LINE = mapOf(
        // Push-up line
        "pseudo_planche_push_up" to "push_up",
        "archer_push_up" to "push_up",
        "decline_push_up" to "push_up",
        "push_up" to "knee_push_up",
        "wide_knee_push_up" to "knee_scapular_push_up",
        "knee_push_up" to "knee_scapular_push_up",
        "diamond_push_up" to "incline_diamond_knee_push_up",
        "handstand_push_up" to "pike_push_up",
        "dip" to "bent_leg_bench_dip",
        // Pull-up & row lines
        "muscle_up" to "pull_up",
        "archer_pull_up" to "pull_up",
        "l_sit_pull_up" to "pull_up",
        "wide_grip_pull_up" to "pull_up",
        "neutral_grip_pull_up" to "negative_pull_up",
        "pull_up" to "negative_pull_up",
        "chin_up" to "negative_pull_up",
        "negative_pull_up" to "scapular_pull_up",
        "australian_pull_up" to "scapular_australian_pull_up",
        "australian_chin_up" to "scapular_australian_chin_up",
        // Squat & core lines
        "squat" to "half_squat",
        "pistol_squat" to "bulgarian_split_squat",
        "bulgarian_split_squat" to "lunge",
        "hanging_leg_raise" to "hanging_knee_raise",
        "hanging_knee_raise" to "crunch",
        // Hold line
        "plank" to "incline_hollow_plank",
        "hollow_hold" to "plank",
        "l_sit" to "hollow_hold",
    )

    /** Ordered harder candidates tried first by [harderStep] (either kind): the first that is in scope and fits the gear wins. */
    private val HARDER_IN_LINE = mapOf(
        "push_up" to listOf("diamond_push_up", "decline_push_up"),
        "knee_push_up" to listOf("push_up"),
        // Diamond is catalogued as elbow extension, which has no harder move without gear: keep the push-up line climbing.
        "diamond_push_up" to listOf("archer_push_up"),
        "negative_pull_up" to listOf("pull_up"),
        "pull_up" to listOf("archer_pull_up", "l_sit_pull_up"),
        "chin_up" to listOf("archer_pull_up"),
        "australian_pull_up" to listOf("pull_up", "chin_up"),
        "squat" to listOf("jump_squat"),
        "plank" to listOf("hollow_hold"),
        "hollow_hold" to listOf("l_sit"),
        "side_plank" to listOf("copenhagen_plank_short"),
    )

    internal fun prevInLineId(id: String): String? = EASIER_IN_LINE[id]

    /** When a movement has no harder version, the closest one that does: two legs → one leg, rows → pull-ups. */
    private val RELATED = mapOf(
        MovementPattern.SQUAT_BILATERAL to MovementPattern.SQUAT_UNILATERAL,
        MovementPattern.HORIZONTAL_PULL to MovementPattern.VERTICAL_PULL,
    )

    /**
     * True when both of the last two sessions had 2+ sets, all at the ready reps or more, none marked Failure or Pain.
     * The ready reps are [READY_REPS], or two past the plan's top reps [topReps] when that is lower (audit S7): an
     * 8-12 push-up done 14 x 3 twice is ready. [last] and [previous] are the sets of the last two finished sessions of
     * one bodyweight rep exercise.
     */
    fun readyForHarder(last: List<Progression.PastSet>, previous: List<Progression.PastSet>, topReps: Int? = null): Boolean {
        val ready = topReps?.let { minOf(READY_REPS, it + 2) } ?: READY_REPS
        return listOf(last, previous).all { session ->
            session.size >= 2 && session.all { (it.reps ?: 0) >= ready && it.effort != SetEffort.FAILURE && it.effort != SetEffort.PAIN }
        }
    }

    /** True for a move the starting-point ladder reasons about: reps or hold, built-in, not a stretch, not mobility or conditioning. */
    fun isProgressionMove(ex: ExerciseEntity): Boolean {
        if (ex.kind != ExerciseKind.REPS && ex.kind != ExerciseKind.HOLD) return false
        if (ex.custom || ex.id in ExerciseCatalog.STRETCH_IDS) return false
        val p = ExerciseSwapEngine.profileFor(ex).pattern
        return p != MovementPattern.MOBILITY && p != MovementPattern.CARRY_CONDITIONING
    }

    /** The two core patterns share one family; every other pattern is its own. */
    fun patternFamily(p: MovementPattern): String =
        if (p == MovementPattern.CORE_ANTI_EXTENSION || p == MovementPattern.CORE_FLEXION_ROTATION) "core" else p.name

    internal fun allowedEquipment(equipment: Set<Equipment>): Set<Equipment> =
        expandLegacyEquipment(equipment).ifEmpty { Equipment.allActive } + Equipment.BODYWEIGHT

    /** Deterministic pick: same pattern, most primary muscles shared with the source, same kind, shorter name, id. */
    internal fun pickCandidate(src: ExerciseEntity, cands: List<ExerciseEntity>): ExerciseEntity? {
        val pattern = ExerciseSwapEngine.profileFor(src).pattern
        return cands.minWithOrNull(
            compareBy<ExerciseEntity> { c -> if (ExerciseSwapEngine.profileFor(c).pattern == pattern) 0 else 1 }
                .thenBy { c -> -c.primary.count { it in src.primary } }
                .thenBy { c -> if (c.kind == src.kind) 0 else 1 }
                .thenBy { c -> c.name.length }
                .thenBy { c -> c.id },
        )
    }

    /**
     * The next step up from a reps or hold move that fits [equipment] (empty = not set, so anything), either kind:
     * the named line, the next in tier, the same family one tier up, the related pattern (same tier, then one up), the
     * same family two tiers up. Advanced moves only use the named steps. Null at the true top for the gear.
     */
    fun harderStep(ex: ExerciseEntity, library: List<ExerciseEntity>, equipment: Set<Equipment>): ExerciseEntity? {
        if (!isProgressionMove(ex)) return null
        val tier = of(ex) ?: return null
        val allowed = allowedEquipment(equipment)
        fun ok(c: ExerciseEntity) = c.id != ex.id && isProgressionMove(c) && ExerciseSwapEngine.profileFor(c).fits(allowed)
        val byId = library.associateBy { it.id }
        HARDER_IN_LINE[ex.id]?.forEach { id -> byId[id]?.takeIf { ok(it) }?.let { return it } }
        NEXT_IN_TIER[ex.id]?.let { id -> byId[id]?.takeIf { ok(it) }?.let { return it } }
        if (tier == Tier.ADVANCED) return null
        val fits = library.filter { ok(it) }
        val pattern = ExerciseSwapEngine.profileFor(ex).pattern
        val family = patternFamily(pattern)
        fun inFamily(t: Int) = fits.filter { c ->
            patternFamily(ExerciseSwapEngine.profileFor(c).pattern) == family && of(c) == Tier.entries[t]
        }
        fun inPattern(p: MovementPattern, t: Int) = fits.filter { c ->
            ExerciseSwapEngine.profileFor(c).pattern == p && of(c) == Tier.entries[t]
        }
        val idx = tier.ordinal
        pickCandidate(ex, inFamily(idx + 1))?.let { return it }
        RELATED[pattern]?.let { related ->
            pickCandidate(ex, inPattern(related, idx))?.let { return it }
            pickCandidate(ex, inPattern(related, idx + 1))?.let { return it }
        }
        return if (idx + 2 < Tier.entries.size) pickCandidate(ex, inFamily(idx + 2)) else null
    }

    /**
     * The next step up from a REPS [ex] that is itself a reps move (live workout hint and debrief); null when the step
     * is a hold or there is none: the screen then suggests a slower tempo instead.
     */
    fun harderVersion(ex: ExerciseEntity, library: List<ExerciseEntity>, equipment: Set<Equipment>): ExerciseEntity? {
        if (ex.kind != ExerciseKind.REPS) return null
        return harderStep(ex, library, equipment)?.takeIf { it.kind == ExerciseKind.REPS }
    }

    /**
     * The next step down from a reps or hold move (a weighted move stays with weighted moves): the named line first,
     * walking down until one fits, else the same family one tier lower, then two lower. Foundation moves with no named
     * step are the true bottom: null.
     */
    fun easierStep(ex: ExerciseEntity, library: List<ExerciseEntity>, equipment: Set<Equipment>): ExerciseEntity? {
        if (ex.id in ExerciseCatalog.STRETCH_IDS) return null
        val allowed = allowedEquipment(equipment)
        val weighted = ex.kind == ExerciseKind.WEIGHTED
        fun ok(c: ExerciseEntity): Boolean {
            if (c.id == ex.id || c.custom || c.id in ExerciseCatalog.STRETCH_IDS || !ExerciseSwapEngine.profileFor(c).fits(allowed)) return false
            if (weighted) {
                val p = ExerciseSwapEngine.profileFor(c).pattern
                return c.kind == ExerciseKind.WEIGHTED && p != MovementPattern.MOBILITY && p != MovementPattern.CARRY_CONDITIONING
            }
            return isProgressionMove(c)
        }
        val byId = library.associateBy { it.id }
        var curId: String? = ex.id
        val visited = HashSet<String>()
        while (curId != null && visited.add(curId)) {
            val prevId = prevInLineId(curId) ?: break
            byId[prevId]?.takeIf { ok(it) }?.let { return it }
            curId = prevId
        }
        val tier = of(ex) ?: return null
        if (tier == Tier.FOUNDATION) return null
        val family = patternFamily(ExerciseSwapEngine.profileFor(ex).pattern)
        val fits = library.filter { ok(it) }
        fun inFamily(t: Int) = fits.filter { c ->
            patternFamily(ExerciseSwapEngine.profileFor(c).pattern) == family && of(c) == Tier.entries[t]
        }
        val idx = tier.ordinal
        return pickCandidate(ex, inFamily(idx - 1)) ?: if (idx - 2 >= 0) pickCandidate(ex, inFamily(idx - 2)) else null
    }
}
