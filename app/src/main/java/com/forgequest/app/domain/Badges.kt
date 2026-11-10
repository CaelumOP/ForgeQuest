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
    /** Latest logged body weight in kg; 0 when none, which switches the big-lift badges to fixed kg marks. */
    val bodyKg: Float = 0f,
    val lifetimeSets: Int = 0,
    val lifetimeHours: Int = 0,
    val lifetimeKg: Double = 0.0,
    /** Longest run of consecutive days with a weigh-in. */
    val weighInRun: Int = 0,
    /** Days with at least one meal logged, over all time. */
    val mealDays: Int = 0,
    /** Days whose total landed within 10% of the calorie goal, over all time. */
    val kcalGoalDays: Int = 0,
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
    /** Best reps across [ids], so a chin-up or weighted pull-up set counts for a pull-up badge. */
    private fun repsAny(ids: List<String>, n: Int): (BadgeStats) -> Boolean = { s -> ids.maxOf { s.bestReps[it] ?: 0 } >= n }
    /** Best added weight across [ids]; a plain pull-up or dip logged with added weight counts too. */
    private fun loadAny(ids: List<String>, kg: Float): (BadgeStats) -> Boolean = { s -> ids.maxOf { s.bestWeight[it] ?: 0f } >= kg }

    /** Best [id] weight reaching [bodyMult] x body weight, or [absKg] when no body weight is known. */
    private fun lift(id: String, bodyMult: Float, absKg: Float): (BadgeStats) -> Boolean = { s ->
        val need = if (s.bodyKg > 0f) s.bodyKg * bodyMult else absKg
        (s.bestWeight[id] ?: 0f) >= need
    }

    /** Longest run of consecutive days in [days]. */
    fun longestRun(days: Collection<Long>): Int {
        var best = 0
        var run = 0
        var prev = 0L
        for (d in days.distinct().sorted()) {
            run = if (run > 0 && d == prev + 1L) run + 1 else 1
            best = maxOf(best, run)
            prev = d
        }
        return best
    }

    /** Days (of [dayKcal], epoch day to total kcal) within 10% of [goal]. */
    fun kcalGoalDays(dayKcal: Map<Long, Int>, goal: Int): Int =
        if (goal <= 0) 0 else dayKcal.values.count { it >= goal * 0.9 && it <= goal * 1.1 }

    private val PULL_UPS = listOf("pull_up", "chin_up", "weighted_pull_up", "weighted_chin_up")
    private val DIPS = listOf("dip", "weighted_dip")

    /**
     * How far along a locked counting badge is (done, needed), for "8 / 10" on the badges screen; null for one-off
     * skill and strength badges.
     */
    fun progress(id: String, s: BadgeStats): Pair<Int, Int>? {
        val n = id.substringAfterLast('_').toIntOrNull() ?: return null
        val done = when {
            id.startsWith("workouts_") -> s.workouts
            id.startsWith("streak_") -> s.bestStreakWeeks
            id.startsWith("protein_") -> s.proteinGoalDaysInARow
            id.startsWith("life_sets_") -> s.lifetimeSets
            id.startsWith("life_hours_") -> s.lifetimeHours
            id.startsWith("life_kg_") -> s.lifetimeKg.toInt().coerceAtLeast(0)
            id.startsWith("weigh_streak_") -> s.weighInRun
            id.startsWith("log_days_") -> s.mealDays
            id.startsWith("kcal_days_") -> s.kcalGoalDays
            else -> return null
        }
        return done.coerceAtMost(n) to n
    }

    val all: List<BadgeDef> = listOf(
        BadgeDef("first_workout", "First step", "Finish your first workout", BadgeCategory.CONSISTENCY) { it.workouts >= 1 },
        BadgeDef("workouts_10", "Ten in", "Finish 10 workouts", BadgeCategory.CONSISTENCY) { it.workouts >= 10 },
        BadgeDef("workouts_25", "Quarter century", "Finish 25 workouts", BadgeCategory.CONSISTENCY) { it.workouts >= 25 },
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
        BadgeDef("str_pull_1", "First pull-up", "Your first full pull-up", BadgeCategory.STRENGTH, repsAny(PULL_UPS, 1)),
        BadgeDef("str_pull_10", "Pull-up 10", "10 pull-ups in one set", BadgeCategory.STRENGTH, repsAny(PULL_UPS, 10)),
        BadgeDef("str_dip_15", "Dip 15", "15 dips in one set", BadgeCategory.STRENGTH, repsAny(DIPS, 15)),
        BadgeDef("str_weighted_pull_20", "+20 kg pull-up", "Pull-up with 20 kg added", BadgeCategory.STRENGTH, loadAny(PULL_UPS, 20f)),
        BadgeDef("str_weighted_dip_30", "+30 kg dip", "Dip with 30 kg added", BadgeCategory.STRENGTH, loadAny(DIPS, 30f)),

        BadgeDef("str_bench_1", "Bench: bodyweight", "Bench press your body weight, or 60 kg", BadgeCategory.STRENGTH, lift("bench_press", 1f, 60f)),
        BadgeDef("str_bench_2", "Bench: one and a half", "Bench press 1.5 x your body weight, or 100 kg", BadgeCategory.STRENGTH, lift("bench_press", 1.5f, 100f)),
        BadgeDef("str_squat_1", "Squat: bodyweight", "Back squat your body weight, or 60 kg", BadgeCategory.STRENGTH, lift("back_squat", 1f, 60f)),
        BadgeDef("str_squat_2", "Squat: one and a half", "Back squat 1.5 x your body weight, or 100 kg", BadgeCategory.STRENGTH, lift("back_squat", 1.5f, 100f)),
        BadgeDef("str_deadlift_1", "Deadlift: bodyweight", "Deadlift your body weight, or 60 kg", BadgeCategory.STRENGTH, lift("deadlift", 1f, 60f)),
        BadgeDef("str_deadlift_2", "Deadlift: one and a half", "Deadlift 1.5 x your body weight, or 100 kg", BadgeCategory.STRENGTH, lift("deadlift", 1.5f, 100f)),

        BadgeDef("life_sets_500", "500 sets", "Complete 500 sets", BadgeCategory.CONSISTENCY) { it.lifetimeSets >= 500 },
        BadgeDef("life_hours_50", "50 hours", "Train for 50 hours in total", BadgeCategory.CONSISTENCY) { it.lifetimeHours >= 50 },
        BadgeDef("life_kg_100000", "100 tonnes", "Lift 100,000 kg in total", BadgeCategory.CONSISTENCY) { it.lifetimeKg >= 100_000.0 },
        BadgeDef("weigh_streak_7", "Weigh-in week", "Log your weight 7 days in a row", BadgeCategory.CONSISTENCY) { it.weighInRun >= 7 },
        BadgeDef("weigh_streak_30", "Weigh-in month", "Log your weight 30 days in a row", BadgeCategory.CONSISTENCY) { it.weighInRun >= 30 },

        BadgeDef("food_first", "First bite", "Log your first meal", BadgeCategory.NUTRITION) { it.mealsLogged >= 1 },
        BadgeDef("food_scan", "Snap & log", "Log a meal with the camera", BadgeCategory.NUTRITION) { it.mealsScanned >= 1 },
        BadgeDef("protein_3", "Protein habit", "Hit your protein goal 3 days in a row", BadgeCategory.NUTRITION) { it.proteinGoalDaysInARow >= 3 },
        BadgeDef("protein_7", "Protein week", "Hit your protein goal 7 days in a row", BadgeCategory.NUTRITION) { it.proteinGoalDaysInARow >= 7 },
        BadgeDef("protein_30", "Protein month", "Hit your protein goal 30 days in a row", BadgeCategory.NUTRITION) { it.proteinGoalDaysInARow >= 30 },
        BadgeDef("log_days_30", "30 days logged", "Log meals on 30 days", BadgeCategory.NUTRITION) { it.mealDays >= 30 },
        BadgeDef("log_days_100", "100 days logged", "Log meals on 100 days", BadgeCategory.NUTRITION) { it.mealDays >= 100 },
        BadgeDef("kcal_days_10", "On target x10", "End 10 days within 10 percent of your calorie goal", BadgeCategory.NUTRITION) { it.kcalGoalDays >= 10 },
        BadgeDef("kcal_days_30", "On target x30", "End 30 days within 10 percent of your calorie goal", BadgeCategory.NUTRITION) { it.kcalGoalDays >= 30 },
    )

    /** Ids added in the second badge set: the first evaluation after the update stores the ones already earned as seen. */
    val V2_IDS: Set<String> = setOf(
        "str_bench_1", "str_bench_2", "str_squat_1", "str_squat_2", "str_deadlift_1", "str_deadlift_2",
        "life_sets_500", "life_hours_50", "life_kg_100000", "weigh_streak_7", "weigh_streak_30",
        "log_days_30", "log_days_100", "kcal_days_10", "kcal_days_30",
    )

    /** Splits [fresh] into (silent, loud): on the one-time backfill the V2 badges are silent (stored as seen). */
    fun splitBackfill(fresh: List<BadgeDef>, backfill: Boolean): Pair<List<BadgeDef>, List<BadgeDef>> =
        if (backfill) fresh.partition { it.id in V2_IDS } else emptyList<BadgeDef>() to fresh

    val byId = all.associateBy { it.id }
}
