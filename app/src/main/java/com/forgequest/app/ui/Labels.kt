package com.forgequest.app.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.forgequest.app.R
import com.forgequest.app.data.db.FoodCategory
import com.forgequest.app.domain.BadgeCategory
import com.forgequest.app.domain.BadgeDef
import com.forgequest.app.domain.DayAdvice
import com.forgequest.app.domain.DayTag
import com.forgequest.app.domain.HEADLINE_VARIANTS
import com.forgequest.app.domain.InsightKind
import com.forgequest.app.domain.Muscle
import com.forgequest.app.domain.NutritionGoal
import com.forgequest.app.domain.ScoreTier
import com.forgequest.app.domain.SetEffort
import com.forgequest.app.domain.WorkoutDebriefMath.Headline

/*
 * Screen text for domain types. The domain keeps English names because the coach prompt and the unit tests
 * use them; everything a person reads goes through these lookups so it follows the app language.
 */

private val badgeText: Map<String, Pair<Int, Int>> = mapOf(
    "first_workout" to (R.string.badge_first_workout_title to R.string.badge_first_workout_desc),
    "workouts_10" to (R.string.badge_workouts_10_title to R.string.badge_workouts_10_desc),
    "workouts_25" to (R.string.badge_workouts_25_title to R.string.badge_workouts_25_desc),
    "workouts_50" to (R.string.badge_workouts_50_title to R.string.badge_workouts_50_desc),
    "workouts_100" to (R.string.badge_workouts_100_title to R.string.badge_workouts_100_desc),
    "streak_4" to (R.string.badge_streak_4_title to R.string.badge_streak_4_desc),
    "streak_12" to (R.string.badge_streak_12_title to R.string.badge_streak_12_desc),
    "streak_52" to (R.string.badge_streak_52_title to R.string.badge_streak_52_desc),
    "skill_muscle_up" to (R.string.badge_skill_muscle_up_title to R.string.badge_skill_muscle_up_desc),
    "skill_pistol" to (R.string.badge_skill_pistol_title to R.string.badge_skill_pistol_desc),
    "skill_l_sit" to (R.string.badge_skill_l_sit_title to R.string.badge_skill_l_sit_desc),
    "skill_handstand" to (R.string.badge_skill_handstand_title to R.string.badge_skill_handstand_desc),
    "skill_tuck_planche" to (R.string.badge_skill_tuck_planche_title to R.string.badge_skill_tuck_planche_desc),
    "skill_tuck_lever" to (R.string.badge_skill_tuck_lever_title to R.string.badge_skill_tuck_lever_desc),
    "skill_dead_hang" to (R.string.badge_skill_dead_hang_title to R.string.badge_skill_dead_hang_desc),
    "str_knee_push_20" to (R.string.badge_str_knee_push_20_title to R.string.badge_str_knee_push_20_desc),
    "str_aus_pull_15" to (R.string.badge_str_aus_pull_15_title to R.string.badge_str_aus_pull_15_desc),
    "str_push_30" to (R.string.badge_str_push_30_title to R.string.badge_str_push_30_desc),
    "str_pull_1" to (R.string.badge_str_pull_1_title to R.string.badge_str_pull_1_desc),
    "str_pull_10" to (R.string.badge_str_pull_10_title to R.string.badge_str_pull_10_desc),
    "str_dip_15" to (R.string.badge_str_dip_15_title to R.string.badge_str_dip_15_desc),
    "str_weighted_pull_20" to (R.string.badge_str_weighted_pull_20_title to R.string.badge_str_weighted_pull_20_desc),
    "str_weighted_dip_30" to (R.string.badge_str_weighted_dip_30_title to R.string.badge_str_weighted_dip_30_desc),
    "food_first" to (R.string.badge_food_first_title to R.string.badge_food_first_desc),
    "food_scan" to (R.string.badge_food_scan_title to R.string.badge_food_scan_desc),
    "protein_3" to (R.string.badge_protein_3_title to R.string.badge_protein_3_desc),
    "protein_7" to (R.string.badge_protein_7_title to R.string.badge_protein_7_desc),
    "protein_30" to (R.string.badge_protein_30_title to R.string.badge_protein_30_desc),
    "str_bench_1" to (R.string.badge_str_bench_1_title to R.string.badge_str_bench_1_desc),
    "str_bench_2" to (R.string.badge_str_bench_2_title to R.string.badge_str_bench_2_desc),
    "str_squat_1" to (R.string.badge_str_squat_1_title to R.string.badge_str_squat_1_desc),
    "str_squat_2" to (R.string.badge_str_squat_2_title to R.string.badge_str_squat_2_desc),
    "str_deadlift_1" to (R.string.badge_str_deadlift_1_title to R.string.badge_str_deadlift_1_desc),
    "str_deadlift_2" to (R.string.badge_str_deadlift_2_title to R.string.badge_str_deadlift_2_desc),
    "life_sets_500" to (R.string.badge_life_sets_500_title to R.string.badge_life_sets_500_desc),
    "life_hours_50" to (R.string.badge_life_hours_50_title to R.string.badge_life_hours_50_desc),
    "life_kg_100000" to (R.string.badge_life_kg_100000_title to R.string.badge_life_kg_100000_desc),
    "weigh_streak_7" to (R.string.badge_weigh_streak_7_title to R.string.badge_weigh_streak_7_desc),
    "weigh_streak_30" to (R.string.badge_weigh_streak_30_title to R.string.badge_weigh_streak_30_desc),
    "log_days_30" to (R.string.badge_log_days_30_title to R.string.badge_log_days_30_desc),
    "log_days_100" to (R.string.badge_log_days_100_title to R.string.badge_log_days_100_desc),
    "kcal_days_10" to (R.string.badge_kcal_days_10_title to R.string.badge_kcal_days_10_desc),
    "kcal_days_30" to (R.string.badge_kcal_days_30_title to R.string.badge_kcal_days_30_desc),
)

@Composable
fun BadgeDef.localTitle(): String = badgeText[id]?.let { stringResource(it.first) } ?: title

@Composable
fun BadgeDef.localDescription(): String = badgeText[id]?.let { stringResource(it.second) } ?: description

@Composable
fun BadgeCategory.localLabel(): String = stringResource(
    when (this) {
        BadgeCategory.CONSISTENCY -> R.string.badge_cat_consistency
        BadgeCategory.SKILL -> R.string.badge_cat_skill
        BadgeCategory.STRENGTH -> R.string.badge_cat_strength
        BadgeCategory.NUTRITION -> R.string.badge_cat_nutrition
    }
)

@get:StringRes
val Muscle.labelRes: Int
    get() = when (this) {
        Muscle.CHEST -> R.string.muscle_chest
        Muscle.SHOULDERS -> R.string.muscle_shoulders
        Muscle.BICEPS -> R.string.muscle_biceps
        Muscle.TRICEPS -> R.string.muscle_triceps
        Muscle.FOREARMS -> R.string.muscle_forearms
        Muscle.ABS -> R.string.muscle_abs
        Muscle.OBLIQUES -> R.string.muscle_obliques
        Muscle.TRAPS -> R.string.muscle_traps
        Muscle.UPPER_BACK -> R.string.muscle_upper_back
        Muscle.LOWER_BACK -> R.string.muscle_lower_back
        Muscle.GLUTES -> R.string.muscle_glutes
        Muscle.QUADS -> R.string.muscle_quads
        Muscle.HAMSTRINGS -> R.string.muscle_hamstrings
        Muscle.ADDUCTORS -> R.string.muscle_adductors
        Muscle.CALVES -> R.string.muscle_calves
        Muscle.TIBIALIS -> R.string.muscle_tibialis
    }

@Composable
fun Muscle.localLabel(): String = stringResource(labelRes)

@get:StringRes
val BadgeDef.titleRes: Int
    get() = badgeText[id]?.first ?: R.string.app_name

@get:StringRes
val FoodCategory.labelRes: Int
    get() = when (this) {
        FoodCategory.BREAKFAST -> R.string.food_cat_breakfast
        FoodCategory.LUNCH -> R.string.food_cat_lunch
        FoodCategory.DINNER -> R.string.food_cat_dinner
        FoodCategory.SNACKS -> R.string.food_cat_snacks
        FoodCategory.DRINKS -> R.string.food_cat_drinks
    }

@Composable
fun FoodCategory.localLabel(): String = stringResource(labelRes)

@get:StringRes
val SetEffort.labelRes: Int
    get() = when (this) {
        SetEffort.EASY -> R.string.effort_easy
        SetEffort.OK -> R.string.effort_ok
        SetEffort.HARD -> R.string.effort_hard
        SetEffort.PAIN -> R.string.effort_pain
    }

@get:StringRes
val DayTag.labelRes: Int
    get() = when (this) {
        DayTag.SICK -> R.string.daytag_sick
        DayTag.INJURED -> R.string.daytag_injured
        DayTag.TRAVEL -> R.string.daytag_travel
        DayTag.REST -> R.string.daytag_rest
    }

@get:StringRes
val NutritionGoal.labelRes: Int
    get() = when (this) {
        NutritionGoal.CUT -> R.string.goal_cut
        NutritionGoal.MAINTAIN -> R.string.goal_maintain
        NutritionGoal.GAIN -> R.string.goal_gain
    }

@get:StringRes
val NutritionGoal.descRes: Int
    get() = when (this) {
        NutritionGoal.CUT -> R.string.goal_cut_desc
        NutritionGoal.MAINTAIN -> R.string.goal_maintain_desc
        NutritionGoal.GAIN -> R.string.goal_gain_desc
    }

@get:StringRes
val com.forgequest.app.domain.DayActivity.labelRes: Int
    get() = when (this) {
        com.forgequest.app.domain.DayActivity.SITTING -> R.string.day_sitting
        com.forgequest.app.domain.DayActivity.MIXED -> R.string.day_mixed
        com.forgequest.app.domain.DayActivity.PHYSICAL -> R.string.day_physical
    }

@get:StringRes
val com.forgequest.app.domain.DayActivity.descRes: Int
    get() = when (this) {
        com.forgequest.app.domain.DayActivity.SITTING -> R.string.day_sitting_desc
        com.forgequest.app.domain.DayActivity.MIXED -> R.string.day_mixed_desc
        com.forgequest.app.domain.DayActivity.PHYSICAL -> R.string.day_physical_desc
    }

/** Shown as Gentle / Standard / Fast (AGGRESSIVE in code). */
@get:StringRes
val com.forgequest.app.domain.CutPace.labelRes: Int
    get() = when (this) {
        com.forgequest.app.domain.CutPace.GENTLE -> R.string.pace_gentle
        com.forgequest.app.domain.CutPace.STANDARD -> R.string.pace_standard
        com.forgequest.app.domain.CutPace.AGGRESSIVE -> R.string.pace_fast
    }

@get:StringRes
val com.forgequest.app.domain.GainPace.labelRes: Int
    get() = when (this) {
        com.forgequest.app.domain.GainPace.SLOW -> R.string.pace_slow
        com.forgequest.app.domain.GainPace.STANDARD -> R.string.pace_standard
    }

@Composable
fun com.forgequest.app.domain.RankInfo.localTitle(): String = stringResource(
    when (letter) {
        "S" -> R.string.rank_legend
        "A" -> R.string.rank_elite
        "B" -> R.string.rank_strong
        "C" -> R.string.rank_committed
        "D" -> R.string.rank_regular
        else -> R.string.rank_starter
    }
)

/** "12 workouts, 30 meals, 4 weight entries · saved Oct 18, 2026" */
@Composable
fun com.forgequest.app.data.backup.BackupFile.localSummary(): String {
    val finished = sessions.count { it.finishedAt != null }
    val made = java.time.Instant.ofEpochMilli(createdAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
    val parts = listOf(
        androidx.compose.ui.res.pluralStringResource(R.plurals.backup_workouts, finished, finished),
        androidx.compose.ui.res.pluralStringResource(R.plurals.backup_meals, meals.size, meals.size),
        androidx.compose.ui.res.pluralStringResource(R.plurals.backup_weights, weights.size, weights.size),
    )
    val date = made.format(java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM))
    return stringResource(R.string.backup_summary, parts.joinToString(stringResource(R.string.list_separator)), date)
}

/** Block names from the starter plans ("Warm-up", "Core", "Main", "Legs") in the app language; others as typed. */
@Composable
fun blockLabel(block: String): String = when (block) {
    "Warm-up" -> stringResource(R.string.block_warm_up)
    "Core" -> stringResource(R.string.block_core)
    "Main" -> stringResource(R.string.block_main)
    "Legs" -> stringResource(R.string.block_legs)
    else -> block
}

fun isArabic(): Boolean = java.util.Locale.getDefault().language == "ar"

/** The badge in the rank circle: the E…S letter in English; in Arabic, where a Latin letter means nothing, the level. */
fun com.forgequest.app.domain.RankInfo.mark(level: Int): String = if (isArabic()) level.toString() else letter

private val exerciseCueText: Map<String, Triple<Int, Int, Int>> = mapOf(
    "jumping_jacks" to Triple(R.string.cue_jumping_jacks_1, R.string.cue_jumping_jacks_2, R.string.cue_jumping_jacks_mistake),
    "plank_shoulder_taps" to Triple(R.string.cue_plank_shoulder_taps_1, R.string.cue_plank_shoulder_taps_2, R.string.cue_plank_shoulder_taps_mistake),
    "burpee" to Triple(R.string.cue_burpee_1, R.string.cue_burpee_2, R.string.cue_burpee_mistake),
    "incline_hollow_plank" to Triple(R.string.cue_incline_hollow_plank_1, R.string.cue_incline_hollow_plank_2, R.string.cue_incline_hollow_plank_mistake),
    "incline_reverse_plank" to Triple(R.string.cue_incline_reverse_plank_1, R.string.cue_incline_reverse_plank_2, R.string.cue_incline_reverse_plank_mistake),
    "hip_bridge" to Triple(R.string.cue_hip_bridge_1, R.string.cue_hip_bridge_2, R.string.cue_hip_bridge_mistake),
    "crunch" to Triple(R.string.cue_crunch_1, R.string.cue_crunch_2, R.string.cue_crunch_mistake),
    "superman_hold" to Triple(R.string.cue_superman_hold_1, R.string.cue_superman_hold_2, R.string.cue_superman_hold_mistake),
    "knee_scapular_push_up" to Triple(R.string.cue_knee_scapular_push_up_1, R.string.cue_knee_scapular_push_up_2, R.string.cue_knee_scapular_push_up_mistake),
    "scapular_australian_pull_up" to Triple(R.string.cue_scapular_australian_pull_up_1, R.string.cue_scapular_australian_pull_up_2, R.string.cue_scapular_australian_pull_up_mistake),
    "bent_leg_bench_dip" to Triple(R.string.cue_bent_leg_bench_dip_1, R.string.cue_bent_leg_bench_dip_2, R.string.cue_bent_leg_bench_dip_mistake),
    "scapular_australian_chin_up" to Triple(R.string.cue_scapular_australian_chin_up_1, R.string.cue_scapular_australian_chin_up_2, R.string.cue_scapular_australian_chin_up_mistake),
    "knee_push_up" to Triple(R.string.cue_knee_push_up_1, R.string.cue_knee_push_up_2, R.string.cue_knee_push_up_mistake),
    "australian_pull_up" to Triple(R.string.cue_australian_pull_up_1, R.string.cue_australian_pull_up_2, R.string.cue_australian_pull_up_mistake),
    "reverse_grip_bench_dip" to Triple(R.string.cue_reverse_grip_bench_dip_1, R.string.cue_reverse_grip_bench_dip_2, R.string.cue_reverse_grip_bench_dip_mistake),
    "australian_chin_up" to Triple(R.string.cue_australian_chin_up_1, R.string.cue_australian_chin_up_2, R.string.cue_australian_chin_up_mistake),
    "wide_knee_push_up" to Triple(R.string.cue_wide_knee_push_up_1, R.string.cue_wide_knee_push_up_2, R.string.cue_wide_knee_push_up_mistake),
    "dead_hang" to Triple(R.string.cue_dead_hang_1, R.string.cue_dead_hang_2, R.string.cue_dead_hang_mistake),
    "incline_diamond_knee_push_up" to Triple(R.string.cue_incline_diamond_knee_push_up_1, R.string.cue_incline_diamond_knee_push_up_2, R.string.cue_incline_diamond_knee_push_up_mistake),
    "half_squat" to Triple(R.string.cue_half_squat_1, R.string.cue_half_squat_2, R.string.cue_half_squat_mistake),
    "half_wide_squat" to Triple(R.string.cue_half_wide_squat_1, R.string.cue_half_wide_squat_2, R.string.cue_half_wide_squat_mistake),
    "half_narrow_squat" to Triple(R.string.cue_half_narrow_squat_1, R.string.cue_half_narrow_squat_2, R.string.cue_half_narrow_squat_mistake),
    "calf_raise_turned_out" to Triple(R.string.cue_calf_raise_turned_out_1, R.string.cue_calf_raise_turned_out_2, R.string.cue_calf_raise_turned_out_mistake),
    "calf_raise" to Triple(R.string.cue_calf_raise_1, R.string.cue_calf_raise_2, R.string.cue_calf_raise_mistake),
    "push_up" to Triple(R.string.cue_push_up_1, R.string.cue_push_up_2, R.string.cue_push_up_mistake),
    "diamond_push_up" to Triple(R.string.cue_diamond_push_up_1, R.string.cue_diamond_push_up_2, R.string.cue_diamond_push_up_mistake),
    "pike_push_up" to Triple(R.string.cue_pike_push_up_1, R.string.cue_pike_push_up_2, R.string.cue_pike_push_up_mistake),
    "pull_up" to Triple(R.string.cue_pull_up_1, R.string.cue_pull_up_2, R.string.cue_pull_up_mistake),
    "chin_up" to Triple(R.string.cue_chin_up_1, R.string.cue_chin_up_2, R.string.cue_chin_up_mistake),
    "negative_pull_up" to Triple(R.string.cue_negative_pull_up_1, R.string.cue_negative_pull_up_2, R.string.cue_negative_pull_up_mistake),
    "dip" to Triple(R.string.cue_dip_1, R.string.cue_dip_2, R.string.cue_dip_mistake),
    "squat" to Triple(R.string.cue_squat_1, R.string.cue_squat_2, R.string.cue_squat_mistake),
    "lunge" to Triple(R.string.cue_lunge_1, R.string.cue_lunge_2, R.string.cue_lunge_mistake),
    "bulgarian_split_squat" to Triple(R.string.cue_bulgarian_split_squat_1, R.string.cue_bulgarian_split_squat_2, R.string.cue_bulgarian_split_squat_mistake),
    "pistol_squat" to Triple(R.string.cue_pistol_squat_1, R.string.cue_pistol_squat_2, R.string.cue_pistol_squat_mistake),
    "nordic_curl" to Triple(R.string.cue_nordic_curl_1, R.string.cue_nordic_curl_2, R.string.cue_nordic_curl_mistake),
    "plank" to Triple(R.string.cue_plank_1, R.string.cue_plank_2, R.string.cue_plank_mistake),
    "hollow_hold" to Triple(R.string.cue_hollow_hold_1, R.string.cue_hollow_hold_2, R.string.cue_hollow_hold_mistake),
    "hanging_knee_raise" to Triple(R.string.cue_hanging_knee_raise_1, R.string.cue_hanging_knee_raise_2, R.string.cue_hanging_knee_raise_mistake),
    "hanging_leg_raise" to Triple(R.string.cue_hanging_leg_raise_1, R.string.cue_hanging_leg_raise_2, R.string.cue_hanging_leg_raise_mistake),
    "muscle_up" to Triple(R.string.cue_muscle_up_1, R.string.cue_muscle_up_2, R.string.cue_muscle_up_mistake),
    "l_sit" to Triple(R.string.cue_l_sit_1, R.string.cue_l_sit_2, R.string.cue_l_sit_mistake),
    "handstand_hold" to Triple(R.string.cue_handstand_hold_1, R.string.cue_handstand_hold_2, R.string.cue_handstand_hold_mistake),
    "handstand_push_up" to Triple(R.string.cue_handstand_push_up_1, R.string.cue_handstand_push_up_2, R.string.cue_handstand_push_up_mistake),
    "tuck_planche" to Triple(R.string.cue_tuck_planche_1, R.string.cue_tuck_planche_2, R.string.cue_tuck_planche_mistake),
    "pseudo_planche_push_up" to Triple(R.string.cue_pseudo_planche_push_up_1, R.string.cue_pseudo_planche_push_up_2, R.string.cue_pseudo_planche_push_up_mistake),
    "tuck_front_lever" to Triple(R.string.cue_tuck_front_lever_1, R.string.cue_tuck_front_lever_2, R.string.cue_tuck_front_lever_mistake),
    "front_lever_row" to Triple(R.string.cue_front_lever_row_1, R.string.cue_front_lever_row_2, R.string.cue_front_lever_row_mistake),
    "back_lever_tuck" to Triple(R.string.cue_back_lever_tuck_1, R.string.cue_back_lever_tuck_2, R.string.cue_back_lever_tuck_mistake),
    "weighted_pull_up" to Triple(R.string.cue_weighted_pull_up_1, R.string.cue_weighted_pull_up_2, R.string.cue_weighted_pull_up_mistake),
    "weighted_chin_up" to Triple(R.string.cue_weighted_chin_up_1, R.string.cue_weighted_chin_up_2, R.string.cue_weighted_chin_up_mistake),
    "weighted_dip" to Triple(R.string.cue_weighted_dip_1, R.string.cue_weighted_dip_2, R.string.cue_weighted_dip_mistake),
    "weighted_push_up" to Triple(R.string.cue_weighted_push_up_1, R.string.cue_weighted_push_up_2, R.string.cue_weighted_push_up_mistake),
    "bench_press" to Triple(R.string.cue_bench_press_1, R.string.cue_bench_press_2, R.string.cue_bench_press_mistake),
    "incline_db_press" to Triple(R.string.cue_incline_db_press_1, R.string.cue_incline_db_press_2, R.string.cue_incline_db_press_mistake),
    "overhead_press" to Triple(R.string.cue_overhead_press_1, R.string.cue_overhead_press_2, R.string.cue_overhead_press_mistake),
    "lateral_raise" to Triple(R.string.cue_lateral_raise_1, R.string.cue_lateral_raise_2, R.string.cue_lateral_raise_mistake),
    "face_pull" to Triple(R.string.cue_face_pull_1, R.string.cue_face_pull_2, R.string.cue_face_pull_mistake),
    "barbell_row" to Triple(R.string.cue_barbell_row_1, R.string.cue_barbell_row_2, R.string.cue_barbell_row_mistake),
    "lat_pulldown" to Triple(R.string.cue_lat_pulldown_1, R.string.cue_lat_pulldown_2, R.string.cue_lat_pulldown_mistake),
    "seated_cable_row" to Triple(R.string.cue_seated_cable_row_1, R.string.cue_seated_cable_row_2, R.string.cue_seated_cable_row_mistake),
    "deadlift" to Triple(R.string.cue_deadlift_1, R.string.cue_deadlift_2, R.string.cue_deadlift_mistake),
    "romanian_deadlift" to Triple(R.string.cue_romanian_deadlift_1, R.string.cue_romanian_deadlift_2, R.string.cue_romanian_deadlift_mistake),
    "back_squat" to Triple(R.string.cue_back_squat_1, R.string.cue_back_squat_2, R.string.cue_back_squat_mistake),
    "leg_press" to Triple(R.string.cue_leg_press_1, R.string.cue_leg_press_2, R.string.cue_leg_press_mistake),
    "leg_extension" to Triple(R.string.cue_leg_extension_1, R.string.cue_leg_extension_2, R.string.cue_leg_extension_mistake),
    "leg_curl" to Triple(R.string.cue_leg_curl_1, R.string.cue_leg_curl_2, R.string.cue_leg_curl_mistake),
    "hip_thrust" to Triple(R.string.cue_hip_thrust_1, R.string.cue_hip_thrust_2, R.string.cue_hip_thrust_mistake),
    "standing_calf_raise" to Triple(R.string.cue_standing_calf_raise_1, R.string.cue_standing_calf_raise_2, R.string.cue_standing_calf_raise_mistake),
    "bicep_curl" to Triple(R.string.cue_bicep_curl_1, R.string.cue_bicep_curl_2, R.string.cue_bicep_curl_mistake),
    "hammer_curl" to Triple(R.string.cue_hammer_curl_1, R.string.cue_hammer_curl_2, R.string.cue_hammer_curl_mistake),
    "tricep_pushdown" to Triple(R.string.cue_tricep_pushdown_1, R.string.cue_tricep_pushdown_2, R.string.cue_tricep_pushdown_mistake),
    "skull_crusher" to Triple(R.string.cue_skull_crusher_1, R.string.cue_skull_crusher_2, R.string.cue_skull_crusher_mistake),
    "shrug" to Triple(R.string.cue_shrug_1, R.string.cue_shrug_2, R.string.cue_shrug_mistake),
    "cable_crunch" to Triple(R.string.cue_cable_crunch_1, R.string.cue_cable_crunch_2, R.string.cue_cable_crunch_mistake),
    "tibialis_raise" to Triple(R.string.cue_tibialis_raise_1, R.string.cue_tibialis_raise_2, R.string.cue_tibialis_raise_mistake),
    "heel_walk" to Triple(R.string.cue_heel_walk_1, R.string.cue_heel_walk_2, R.string.cue_heel_walk_mistake),
    "band_dorsiflexion" to Triple(R.string.cue_band_dorsiflexion_1, R.string.cue_band_dorsiflexion_2, R.string.cue_band_dorsiflexion_mistake),
    "copenhagen_plank_short" to Triple(R.string.cue_copenhagen_plank_short_1, R.string.cue_copenhagen_plank_short_2, R.string.cue_copenhagen_plank_short_mistake),
    "copenhagen_plank_long" to Triple(R.string.cue_copenhagen_plank_long_1, R.string.cue_copenhagen_plank_long_2, R.string.cue_copenhagen_plank_long_mistake),
    "side_lying_hip_adduction" to Triple(R.string.cue_side_lying_hip_adduction_1, R.string.cue_side_lying_hip_adduction_2, R.string.cue_side_lying_hip_adduction_mistake),
    "cossack_squat" to Triple(R.string.cue_cossack_squat_1, R.string.cue_cossack_squat_2, R.string.cue_cossack_squat_mistake),
    "band_hip_adduction" to Triple(R.string.cue_band_hip_adduction_1, R.string.cue_band_hip_adduction_2, R.string.cue_band_hip_adduction_mistake),
    "side_plank" to Triple(R.string.cue_side_plank_1, R.string.cue_side_plank_2, R.string.cue_side_plank_mistake),
    "bicycle_crunch" to Triple(R.string.cue_bicycle_crunch_1, R.string.cue_bicycle_crunch_2, R.string.cue_bicycle_crunch_mistake),
    "wrist_curl" to Triple(R.string.cue_wrist_curl_1, R.string.cue_wrist_curl_2, R.string.cue_wrist_curl_mistake),
    "kb_swing" to Triple(R.string.cue_kb_swing_1, R.string.cue_kb_swing_2, R.string.cue_kb_swing_mistake),
    "goblet_squat" to Triple(R.string.cue_goblet_squat_1, R.string.cue_goblet_squat_2, R.string.cue_goblet_squat_mistake),
    "kb_deadlift" to Triple(R.string.cue_kb_deadlift_1, R.string.cue_kb_deadlift_2, R.string.cue_kb_deadlift_mistake),
    "kb_clean" to Triple(R.string.cue_kb_clean_1, R.string.cue_kb_clean_2, R.string.cue_kb_clean_mistake),
    "kb_press" to Triple(R.string.cue_kb_press_1, R.string.cue_kb_press_2, R.string.cue_kb_press_mistake),
    "kb_snatch" to Triple(R.string.cue_kb_snatch_1, R.string.cue_kb_snatch_2, R.string.cue_kb_snatch_mistake),
    "kb_row" to Triple(R.string.cue_kb_row_1, R.string.cue_kb_row_2, R.string.cue_kb_row_mistake),
    "turkish_get_up" to Triple(R.string.cue_turkish_get_up_1, R.string.cue_turkish_get_up_2, R.string.cue_turkish_get_up_mistake),
    "farmers_carry" to Triple(R.string.cue_farmers_carry_1, R.string.cue_farmers_carry_2, R.string.cue_farmers_carry_mistake),
    "db_bench_press" to Triple(R.string.cue_db_bench_press_1, R.string.cue_db_bench_press_2, R.string.cue_db_bench_press_mistake),
    "db_shoulder_press" to Triple(R.string.cue_db_shoulder_press_1, R.string.cue_db_shoulder_press_2, R.string.cue_db_shoulder_press_mistake),
    "db_row" to Triple(R.string.cue_db_row_1, R.string.cue_db_row_2, R.string.cue_db_row_mistake),
    "db_lunge" to Triple(R.string.cue_db_lunge_1, R.string.cue_db_lunge_2, R.string.cue_db_lunge_mistake),
    "russian_twist" to Triple(R.string.cue_russian_twist_1, R.string.cue_russian_twist_2, R.string.cue_russian_twist_mistake),
    "seated_leg_curl" to Triple(R.string.cue_seated_leg_curl_1, R.string.cue_seated_leg_curl_2, R.string.cue_seated_leg_curl_mistake),
    "single_leg_rdl" to Triple(R.string.cue_single_leg_rdl_1, R.string.cue_single_leg_rdl_2, R.string.cue_single_leg_rdl_mistake),
    "db_romanian_deadlift" to Triple(R.string.cue_db_romanian_deadlift_1, R.string.cue_db_romanian_deadlift_2, R.string.cue_db_romanian_deadlift_mistake),
    "good_morning" to Triple(R.string.cue_good_morning_1, R.string.cue_good_morning_2, R.string.cue_good_morning_mistake),
    "band_leg_curl" to Triple(R.string.cue_band_leg_curl_1, R.string.cue_band_leg_curl_2, R.string.cue_band_leg_curl_mistake),
    "barbell_curl" to Triple(R.string.cue_barbell_curl_1, R.string.cue_barbell_curl_2, R.string.cue_barbell_curl_mistake),
    "incline_db_curl" to Triple(R.string.cue_incline_db_curl_1, R.string.cue_incline_db_curl_2, R.string.cue_incline_db_curl_mistake),
    "concentration_curl" to Triple(R.string.cue_concentration_curl_1, R.string.cue_concentration_curl_2, R.string.cue_concentration_curl_mistake),
    "preacher_curl" to Triple(R.string.cue_preacher_curl_1, R.string.cue_preacher_curl_2, R.string.cue_preacher_curl_mistake),
    "cable_curl" to Triple(R.string.cue_cable_curl_1, R.string.cue_cable_curl_2, R.string.cue_cable_curl_mistake),
    "band_curl" to Triple(R.string.cue_band_curl_1, R.string.cue_band_curl_2, R.string.cue_band_curl_mistake),
    "seated_calf_raise" to Triple(R.string.cue_seated_calf_raise_1, R.string.cue_seated_calf_raise_2, R.string.cue_seated_calf_raise_mistake),
    "single_leg_calf_raise" to Triple(R.string.cue_single_leg_calf_raise_1, R.string.cue_single_leg_calf_raise_2, R.string.cue_single_leg_calf_raise_mistake),
    "db_calf_raise" to Triple(R.string.cue_db_calf_raise_1, R.string.cue_db_calf_raise_2, R.string.cue_db_calf_raise_mistake),
    "back_extension" to Triple(R.string.cue_back_extension_1, R.string.cue_back_extension_2, R.string.cue_back_extension_mistake),
    "bird_dog" to Triple(R.string.cue_bird_dog_1, R.string.cue_bird_dog_2, R.string.cue_bird_dog_mistake),
    "cable_pull_through" to Triple(R.string.cue_cable_pull_through_1, R.string.cue_cable_pull_through_2, R.string.cue_cable_pull_through_mistake),
    "pallof_press" to Triple(R.string.cue_pallof_press_1, R.string.cue_pallof_press_2, R.string.cue_pallof_press_mistake),
    "cable_woodchop" to Triple(R.string.cue_cable_woodchop_1, R.string.cue_cable_woodchop_2, R.string.cue_cable_woodchop_mistake),
    "db_side_bend" to Triple(R.string.cue_db_side_bend_1, R.string.cue_db_side_bend_2, R.string.cue_db_side_bend_mistake),
    "mountain_climber" to Triple(R.string.cue_mountain_climber_1, R.string.cue_mountain_climber_2, R.string.cue_mountain_climber_mistake),
    "cable_fly" to Triple(R.string.cue_cable_fly_1, R.string.cue_cable_fly_2, R.string.cue_cable_fly_mistake),
    "pec_deck" to Triple(R.string.cue_pec_deck_1, R.string.cue_pec_deck_2, R.string.cue_pec_deck_mistake),
    "chest_press_machine" to Triple(R.string.cue_chest_press_machine_1, R.string.cue_chest_press_machine_2, R.string.cue_chest_press_machine_mistake),
    "shoulder_press_machine" to Triple(R.string.cue_shoulder_press_machine_1, R.string.cue_shoulder_press_machine_2, R.string.cue_shoulder_press_machine_mistake),
    "cable_lateral_raise" to Triple(R.string.cue_cable_lateral_raise_1, R.string.cue_cable_lateral_raise_2, R.string.cue_cable_lateral_raise_mistake),
    "reverse_pec_deck" to Triple(R.string.cue_reverse_pec_deck_1, R.string.cue_reverse_pec_deck_2, R.string.cue_reverse_pec_deck_mistake),
    "straight_arm_pulldown" to Triple(R.string.cue_straight_arm_pulldown_1, R.string.cue_straight_arm_pulldown_2, R.string.cue_straight_arm_pulldown_mistake),
    "overhead_cable_triceps_extension" to Triple(R.string.cue_overhead_cable_triceps_extension_1, R.string.cue_overhead_cable_triceps_extension_2, R.string.cue_overhead_cable_triceps_extension_mistake),
    "band_pull_apart" to Triple(R.string.cue_band_pull_apart_1, R.string.cue_band_pull_apart_2, R.string.cue_band_pull_apart_mistake),
    "band_row" to Triple(R.string.cue_band_row_1, R.string.cue_band_row_2, R.string.cue_band_row_mistake),
    "band_chest_press" to Triple(R.string.cue_band_chest_press_1, R.string.cue_band_chest_press_2, R.string.cue_band_chest_press_mistake),
    "kb_sumo_deadlift" to Triple(R.string.cue_kb_sumo_deadlift_1, R.string.cue_kb_sumo_deadlift_2, R.string.cue_kb_sumo_deadlift_mistake),
    "kb_halo" to Triple(R.string.cue_kb_halo_1, R.string.cue_kb_halo_2, R.string.cue_kb_halo_mistake),
    "kb_windmill" to Triple(R.string.cue_kb_windmill_1, R.string.cue_kb_windmill_2, R.string.cue_kb_windmill_mistake),
    "quad_stretch" to Triple(R.string.cue_quad_stretch_1, R.string.cue_quad_stretch_2, R.string.cue_quad_stretch_mistake),
    "hamstring_stretch" to Triple(R.string.cue_hamstring_stretch_1, R.string.cue_hamstring_stretch_2, R.string.cue_hamstring_stretch_mistake),
    "hip_flexor_stretch" to Triple(R.string.cue_hip_flexor_stretch_1, R.string.cue_hip_flexor_stretch_2, R.string.cue_hip_flexor_stretch_mistake),
    "pigeon_stretch" to Triple(R.string.cue_pigeon_stretch_1, R.string.cue_pigeon_stretch_2, R.string.cue_pigeon_stretch_mistake),
    "figure_four_stretch" to Triple(R.string.cue_figure_four_stretch_1, R.string.cue_figure_four_stretch_2, R.string.cue_figure_four_stretch_mistake),
    "calf_stretch" to Triple(R.string.cue_calf_stretch_1, R.string.cue_calf_stretch_2, R.string.cue_calf_stretch_mistake),
    "childs_pose" to Triple(R.string.cue_childs_pose_1, R.string.cue_childs_pose_2, R.string.cue_childs_pose_mistake),
    "chest_doorway_stretch" to Triple(R.string.cue_chest_doorway_stretch_1, R.string.cue_chest_doorway_stretch_2, R.string.cue_chest_doorway_stretch_mistake),
    "cross_body_shoulder_stretch" to Triple(R.string.cue_cross_body_shoulder_stretch_1, R.string.cue_cross_body_shoulder_stretch_2, R.string.cue_cross_body_shoulder_stretch_mistake),
    "triceps_overhead_stretch" to Triple(R.string.cue_triceps_overhead_stretch_1, R.string.cue_triceps_overhead_stretch_2, R.string.cue_triceps_overhead_stretch_mistake),
    "biceps_wall_stretch" to Triple(R.string.cue_biceps_wall_stretch_1, R.string.cue_biceps_wall_stretch_2, R.string.cue_biceps_wall_stretch_mistake),
    "neck_side_stretch" to Triple(R.string.cue_neck_side_stretch_1, R.string.cue_neck_side_stretch_2, R.string.cue_neck_side_stretch_mistake),
    "thread_the_needle" to Triple(R.string.cue_thread_the_needle_1, R.string.cue_thread_the_needle_2, R.string.cue_thread_the_needle_mistake),
    "butterfly_stretch" to Triple(R.string.cue_butterfly_stretch_1, R.string.cue_butterfly_stretch_2, R.string.cue_butterfly_stretch_mistake),
    "frog_stretch" to Triple(R.string.cue_frog_stretch_1, R.string.cue_frog_stretch_2, R.string.cue_frog_stretch_mistake),
    "wrist_flexor_stretch" to Triple(R.string.cue_wrist_flexor_stretch_1, R.string.cue_wrist_flexor_stretch_2, R.string.cue_wrist_flexor_stretch_mistake),
    "seated_spinal_twist" to Triple(R.string.cue_seated_spinal_twist_1, R.string.cue_seated_spinal_twist_2, R.string.cue_seated_spinal_twist_mistake),
    "lat_stretch" to Triple(R.string.cue_lat_stretch_1, R.string.cue_lat_stretch_2, R.string.cue_lat_stretch_mistake),
    "shin_stretch" to Triple(R.string.cue_shin_stretch_1, R.string.cue_shin_stretch_2, R.string.cue_shin_stretch_mistake),
)

/** Two form cues and the common mistake for a built-in exercise, as string resources (explicit map: release builds shrink getIdentifier). Null for custom exercises. */
fun exerciseCues(id: String): Triple<Int, Int, Int>? = exerciseCueText[id]

/** A resolved insight sentence: a string resource, or a plurals resource when [plural] (quantity: the first Count slot). */
data class InsightRes(val id: Int, val plural: Boolean)

private val insightText: Map<InsightKind, List<Int>> = mapOf(
    InsightKind.FIRST_ROUND_REPS to listOf(R.plurals.insight_first_round_reps_1, R.plurals.insight_first_round_reps_2, R.plurals.insight_first_round_reps_3),
    InsightKind.FIRST_ROUND_HOLD to listOf(R.string.insight_first_round_hold_1, R.string.insight_first_round_hold_2, R.string.insight_first_round_hold_3),
    InsightKind.CROSS_WORKOUTS to listOf(R.string.insight_cross_workouts_1, R.string.insight_cross_workouts_2, R.string.insight_cross_workouts_3),
    InsightKind.ANNIVERSARY to listOf(R.plurals.insight_anniversary_1, R.plurals.insight_anniversary_2, R.plurals.insight_anniversary_3),
    InsightKind.NEAR_RECORD_WEIGHT to listOf(R.string.insight_near_record_weight_1, R.string.insight_near_record_weight_2, R.string.insight_near_record_weight_3),
    InsightKind.NEAR_RECORD_REPS to listOf(R.string.insight_near_record_reps_1, R.string.insight_near_record_reps_2, R.string.insight_near_record_reps_3),
    InsightKind.CROSS_HOURS to listOf(R.plurals.insight_cross_hours_1, R.plurals.insight_cross_hours_2, R.plurals.insight_cross_hours_3),
    InsightKind.CROSS_KG to listOf(R.string.insight_cross_kg_1, R.string.insight_cross_kg_2, R.string.insight_cross_kg_3),
    InsightKind.CROSS_SETS to listOf(R.plurals.insight_cross_sets_1, R.plurals.insight_cross_sets_2, R.plurals.insight_cross_sets_3),
    InsightKind.CROSS_REPS to listOf(R.plurals.insight_cross_reps_1, R.plurals.insight_cross_reps_2, R.plurals.insight_cross_reps_3),
    InsightKind.BACK_AFTER_GAP to listOf(R.plurals.insight_back_after_gap_1, R.plurals.insight_back_after_gap_2, R.plurals.insight_back_after_gap_3),
    InsightKind.VS_FOUR_WEEKS to listOf(R.string.insight_vs_four_weeks_1, R.string.insight_vs_four_weeks_2, R.string.insight_vs_four_weeks_3),
    InsightKind.EASY_STREAK to listOf(R.string.insight_easy_streak_1, R.string.insight_easy_streak_2, R.string.insight_easy_streak_3),
    InsightKind.HARD_TWICE to listOf(R.string.insight_hard_twice_1, R.string.insight_hard_twice_2, R.string.insight_hard_twice_3),
    InsightKind.SAME_WEEKDAY to listOf(R.plurals.insight_same_weekday_1, R.plurals.insight_same_weekday_2, R.plurals.insight_same_weekday_3),
    InsightKind.STREAK_NEXT to listOf(R.plurals.insight_streak_next_1, R.plurals.insight_streak_next_2, R.plurals.insight_streak_next_3),
    InsightKind.PACE_FASTER to listOf(R.plurals.insight_pace_faster_1, R.plurals.insight_pace_faster_2, R.plurals.insight_pace_faster_3),
    InsightKind.MUSCLE_NEGLECTED to listOf(R.plurals.insight_muscle_neglected_1, R.plurals.insight_muscle_neglected_2, R.plurals.insight_muscle_neglected_3),
    InsightKind.MUSCLE_WEEK to listOf(R.plurals.insight_muscle_week_1, R.plurals.insight_muscle_week_2, R.plurals.insight_muscle_week_3),
    InsightKind.AVG_REST to listOf(R.string.insight_avg_rest_1, R.string.insight_avg_rest_2, R.string.insight_avg_rest_3),
    InsightKind.FORM_TIP to listOf(R.string.insight_form_tip_1, R.string.insight_form_tip_2, R.string.insight_form_tip_3),
)

/** The lifetime-weight sentence in pounds, for people who log in lb (the count is converted by the caller). */
private val crossLbText = listOf(R.string.insight_cross_lb_1, R.string.insight_cross_lb_2, R.string.insight_cross_lb_3)

/** The sentence for an insight kind and phrasing (0 until [InsightKind.variants]); the arguments are the slots in order. */
fun insightRes(kind: InsightKind, variant: Int, pounds: Boolean = false): InsightRes {
    val ids = if (pounds && kind == InsightKind.CROSS_KG) crossLbText else insightText.getValue(kind)
    return InsightRes(ids[variant.coerceIn(0, ids.size - 1)], kind.plural)
}

private val headlineText: Map<Headline, List<Int>> = mapOf(
    Headline.PR_AND_UP to listOf(
        R.string.insight_h_pr_and_up_1,
        R.string.insight_h_pr_and_up_2,
        R.string.insight_h_pr_and_up_3,
        R.string.insight_h_pr_and_up_4,
        R.string.insight_h_pr_and_up_5,
        R.string.insight_h_pr_and_up_6,
        R.string.insight_h_pr_and_up_7,
        R.string.insight_h_pr_and_up_8,
    ),
    Headline.MOSTLY_UP to listOf(
        R.string.insight_h_mostly_up_1,
        R.string.insight_h_mostly_up_2,
        R.string.insight_h_mostly_up_3,
        R.string.insight_h_mostly_up_4,
        R.string.insight_h_mostly_up_5,
        R.string.insight_h_mostly_up_6,
        R.string.insight_h_mostly_up_7,
        R.string.insight_h_mostly_up_8,
    ),
    Headline.ALL_SAME to listOf(
        R.string.insight_h_all_same_1,
        R.string.insight_h_all_same_2,
        R.string.insight_h_all_same_3,
        R.string.insight_h_all_same_4,
        R.string.insight_h_all_same_5,
        R.string.insight_h_all_same_6,
        R.string.insight_h_all_same_7,
        R.string.insight_h_all_same_8,
    ),
    Headline.SOME_UP to listOf(
        R.string.insight_h_some_up_1,
        R.string.insight_h_some_up_2,
        R.string.insight_h_some_up_3,
        R.string.insight_h_some_up_4,
        R.string.insight_h_some_up_5,
        R.string.insight_h_some_up_6,
        R.string.insight_h_some_up_7,
        R.string.insight_h_some_up_8,
    ),
    Headline.MIXED to listOf(
        R.string.insight_h_mixed_1,
        R.string.insight_h_mixed_2,
        R.string.insight_h_mixed_3,
        R.string.insight_h_mixed_4,
        R.string.insight_h_mixed_5,
        R.string.insight_h_mixed_6,
        R.string.insight_h_mixed_7,
        R.string.insight_h_mixed_8,
    ),
)

/** One of the [HEADLINE_VARIANTS] phrasings for the common headline types; null for the others (they use their own). */
@StringRes
fun headlineRes(type: Headline, variant: Int): Int? = headlineText[type]?.let { it[variant.coerceIn(0, it.size - 1)] }

@get:StringRes
val ScoreTier.labelRes: Int
    get() = when (this) {
        ScoreTier.RECORD_DAY -> R.string.score_tier_record_day
        ScoreTier.STEP_FORWARD -> R.string.score_tier_step_forward
        ScoreTier.HELD_STEADY -> R.string.score_tier_held_steady
        ScoreTier.LIGHT_DAY -> R.string.score_tier_light_day
    }

/** The readiness sentence for a plan-aware [DayAdvice]. */
@get:StringRes
val DayAdvice.bodyRes: Int
    get() = when (this) {
        DayAdvice.LEGS_PEAK -> R.string.call_legs_peak_b
        DayAdvice.LEGS_STEADY -> R.string.call_legs_steady_b
        DayAdvice.LEGS_EASE -> R.string.call_legs_ease_b
        DayAdvice.LEGS_RECOVER -> R.string.call_legs_recover_b
        DayAdvice.UPPER_EASE -> R.string.call_upper_ease_b
        DayAdvice.UPPER_RECOVER -> R.string.call_upper_recover_b
    }
