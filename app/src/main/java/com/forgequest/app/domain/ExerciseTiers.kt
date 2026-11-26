package com.forgequest.app.domain

import com.forgequest.app.data.db.ExerciseEntity
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

    /**
     * The next step up from [ex] that fits [equipment] (empty = not set, so anything): a bodyweight rep move one tier up
     * in the same movement, else the related movement at the same tier (squat → lunge, rows → negative pull-up), else
     * the related movement one tier up.
     * Null when there is none: the screen then suggests a slower tempo instead.
     */
    fun harderVersion(ex: ExerciseEntity, library: List<ExerciseEntity>, equipment: Set<Equipment>): ExerciseEntity? {
        if (ex.kind != ExerciseKind.REPS) return null
        val tier = of(ex) ?: return null
        val allowed = expandLegacyEquipment(equipment).ifEmpty { Equipment.allActive } + Equipment.BODYWEIGHT
        val byId = library.associateBy { it.id }
        NEXT_IN_TIER[ex.id]?.let { id -> byId[id]?.takeIf { ExerciseSwapEngine.profileFor(it).fits(allowed) }?.let { return it } }
        if (tier == Tier.ADVANCED) return null
        val up = Tier.entries[tier.ordinal + 1]
        val pattern = ExerciseSwapEngine.profileFor(ex).pattern
        val fits = library.filter { it.id != ex.id && it.kind == ExerciseKind.REPS && ExerciseSwapEngine.profileFor(it).fits(allowed) }
        fun pick(p: MovementPattern?, t: Tier) = p?.let { pat ->
            fits.filter { of(it) == t && ExerciseSwapEngine.profileFor(it).pattern == pat }
                // Closest muscles first, so a chest push-up steps up to a chest move, not a shoulder one.
                .maxWithOrNull(compareBy<ExerciseEntity> { c -> c.primary.count { it in ex.primary } }.thenBy { c -> -c.name.length })
        }
        return pick(pattern, up) ?: pick(RELATED[pattern], tier) ?: pick(RELATED[pattern], up)
    }
}
