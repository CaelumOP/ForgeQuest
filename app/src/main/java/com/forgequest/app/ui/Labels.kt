package com.forgequest.app.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.forgequest.app.R
import com.forgequest.app.data.db.FoodCategory
import com.forgequest.app.domain.BadgeCategory
import com.forgequest.app.domain.BadgeDef
import com.forgequest.app.domain.DayTag
import com.forgequest.app.domain.Muscle
import com.forgequest.app.domain.NutritionGoal
import com.forgequest.app.domain.SetEffort

/*
 * Screen text for domain types. The domain keeps English names because the coach prompt and the unit tests
 * use them; everything a person reads goes through these lookups so it follows the app language.
 */

private val badgeText: Map<String, Pair<Int, Int>> = mapOf(
    "first_workout" to (R.string.badge_first_workout_title to R.string.badge_first_workout_desc),
    "workouts_10" to (R.string.badge_workouts_10_title to R.string.badge_workouts_10_desc),
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
