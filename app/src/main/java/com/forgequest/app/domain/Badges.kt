package com.forgequest.app.domain

enum class BadgeCategory(val label: String) { CONSISTENCY("Consistency"), SKILL("Skills"), STRENGTH("Strength"), NUTRITION("Nutrition") }

/** Everything badge rules need, computed once from the database. */
data class BadgeStats(
    val workouts: Int = 0,
    val bestStreakWeeks: Int = 0,
    val bestReps: Map<String, Int> = emptyMap(),
    val bestSeconds: Map<String, Int> = emptyMap(),
    val bestWeight: Map<String, Float> = emptyMap(),
    val mealsLogged: Int = 0,
    val mealsScanned: Int = 0,
    val proteinGoalDaysInARow: Int = 0,
)

data class BadgeDef(
    val id: String,
    val title: String,
    val description: String,
    val category: BadgeCategory,
    val unlocked: (BadgeStats) -> Boolean,
)

object Badges {
    private fun reps(id: String, n: Int): (BadgeStats) -> Boolean = { (it.bestReps[id] ?: 0) >= n }
    private fun hold(id: String, s: Int): (BadgeStats) -> Boolean = { (it.bestSeconds[id] ?: 0) >= s }
    private fun load(id: String, kg: Float): (BadgeStats) -> Boolean = { (it.bestWeight[id] ?: 0f) >= kg }

    val all: List<BadgeDef> = listOf(
        BadgeDef("first_workout", "First step", "Finish your first workout", BadgeCategory.CONSISTENCY) { it.workouts >= 1 },
        BadgeDef("workouts_10", "Ten in", "Finish 10 workouts", BadgeCategory.CONSISTENCY) { it.workouts >= 10 },
        BadgeDef("workouts_50", "Half century", "Finish 50 workouts", BadgeCategory.CONSISTENCY) { it.workouts >= 50 },
        BadgeDef("workouts_100", "Hundred club", "Finish 100 workouts", BadgeCategory.CONSISTENCY) { it.workouts >= 100 },
        BadgeDef("streak_4", "One month strong", "Hit your weekly target 4 weeks in a row", BadgeCategory.CONSISTENCY) { it.bestStreakWeeks >= 4 },
        BadgeDef("streak_12", "Quarter of steel", "Hit your weekly target 12 weeks in a row", BadgeCategory.CONSISTENCY) { it.bestStreakWeeks >= 12 },
        BadgeDef("streak_52", "Full year", "Hit your weekly target 52 weeks in a row", BadgeCategory.CONSISTENCY) { it.bestStreakWeeks >= 52 },

        BadgeDef("skill_muscle_up", "First muscle-up", "Log a muscle-up", BadgeCategory.SKILL, reps("muscle_up", 1)),
        BadgeDef("skill_pistol", "Pistol", "Log a pistol squat", BadgeCategory.SKILL, reps("pistol_squat", 1)),
        BadgeDef("skill_l_sit", "L-sit 10 s", "Hold an L-sit for 10 seconds", BadgeCategory.SKILL, hold("l_sit", 10)),
        BadgeDef("skill_handstand", "Handstand 30 s", "Hold a handstand for 30 seconds", BadgeCategory.SKILL, hold("handstand_hold", 30)),
        BadgeDef("skill_tuck_planche", "Tuck planche", "Hold a tuck planche for 10 seconds", BadgeCategory.SKILL, hold("tuck_planche", 10)),
        BadgeDef("skill_tuck_lever", "Tuck front lever", "Hold a tuck front lever for 10 seconds", BadgeCategory.SKILL, hold("tuck_front_lever", 10)),
        BadgeDef("skill_dead_hang", "Iron grip", "Dead hang for 60 seconds", BadgeCategory.SKILL, hold("dead_hang", 60)),

        BadgeDef("str_knee_push_20", "Foundation push", "20 knee push-ups in one set", BadgeCategory.STRENGTH, reps("knee_push_up", 20)),
        BadgeDef("str_aus_pull_15", "Foundation pull", "15 Australian pull-ups in one set", BadgeCategory.STRENGTH, reps("australian_pull_up", 15)),
        BadgeDef("str_push_30", "Push-up 30", "30 push-ups in one set", BadgeCategory.STRENGTH, reps("push_up", 30)),
        BadgeDef("str_pull_1", "First pull-up", "Your first full pull-up", BadgeCategory.STRENGTH, reps("pull_up", 1)),
        BadgeDef("str_pull_10", "Pull-up 10", "10 pull-ups in one set", BadgeCategory.STRENGTH, reps("pull_up", 10)),
        BadgeDef("str_dip_15", "Dip 15", "15 dips in one set", BadgeCategory.STRENGTH, reps("dip", 15)),
        BadgeDef("str_weighted_pull_20", "+20 kg pull-up", "Pull-up with 20 kg added", BadgeCategory.STRENGTH, load("weighted_pull_up", 20f)),
        BadgeDef("str_weighted_dip_30", "+30 kg dip", "Dip with 30 kg added", BadgeCategory.STRENGTH, load("weighted_dip", 30f)),

        BadgeDef("food_first", "First bite", "Log your first meal", BadgeCategory.NUTRITION) { it.mealsLogged >= 1 },
        BadgeDef("food_scan", "Snap & log", "Log a meal with the camera", BadgeCategory.NUTRITION) { it.mealsScanned >= 1 },
        BadgeDef("protein_3", "Protein habit", "Hit your protein goal 3 days in a row", BadgeCategory.NUTRITION) { it.proteinGoalDaysInARow >= 3 },
        BadgeDef("protein_7", "Protein week", "Hit your protein goal 7 days in a row", BadgeCategory.NUTRITION) { it.proteinGoalDaysInARow >= 7 },
        BadgeDef("protein_30", "Protein month", "Hit your protein goal 30 days in a row", BadgeCategory.NUTRITION) { it.proteinGoalDaysInARow >= 30 },
    )

    val byId = all.associateBy { it.id }
}
