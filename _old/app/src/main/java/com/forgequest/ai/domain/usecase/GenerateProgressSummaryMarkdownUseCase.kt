package com.forgequest.ai.domain.usecase

import com.forgequest.ai.data.local.dao.ForgeQuestDao
import com.forgequest.ai.data.local.entity.BodyWeightLogEntity
import com.forgequest.ai.data.local.entity.LoggedSetEntity
import com.forgequest.ai.data.local.entity.MealLogEntity
import com.forgequest.ai.data.local.entity.PlannedDayEntity
import com.forgequest.ai.data.local.entity.PlannedExerciseEntity
import com.forgequest.ai.data.local.entity.UserProfileEntity
import com.forgequest.ai.data.local.entity.WorkoutPlanEntity
import com.forgequest.ai.data.local.entity.WorkoutSessionEntity
import com.forgequest.ai.domain.model.ExerciseCategory
import com.forgequest.ai.domain.model.HunterRank
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

class GenerateProgressSummaryMarkdownUseCase {

    private val shortDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MM-dd")

    /**
     * Pure markdown generator that creates the compact progress summary markdown
     * containing:
     * 1. Hunter RPG Rank, Level, Total XP, and Current Workout Streak (`flameHealthState`).
     * 2. Active Weekly Workout Plan summary (Days 1–7, rest days, target sets/reps).
     * 3. Markdown table of the last 14 days of Total Calories (`kcal`), Total Protein (`g`), and Body Weight (`kg`).
     * 4. Markdown table of tracked exercises showing All-Time Max Reps PR, All-Time Max Volume PR,
     *    and the last 3 logged sessions (`Date: Set1, Set2, Set3`).
     */
    fun generateMarkdown(
        profile: UserProfileEntity,
        activePlan: WorkoutPlanEntity?,
        plannedDays: List<PlannedDayEntity>,
        plannedExercises: List<PlannedExerciseEntity>,
        mealLogsLast14Days: List<MealLogEntity>,
        weightLogsLast14Days: List<BodyWeightLogEntity>,
        completedSessions: List<WorkoutSessionEntity>,
        completedSets: List<LoggedSetEntity>,
        todayEpochDay: Long = LocalDate.now().toEpochDay(),
        dailyCalorieGoal: Int = 2100,
        dailyProteinGoal: Int = 140
    ): String {
        val sb = StringBuilder(2048)
        val hunterRank = HunterRank.forLevel(profile.level)

        // Section 1: Hunter RPG Status & Streak Flame
        sb.appendLine("# ForgeQuest AI — Progress Summary")
        sb.appendLine(
            "**Rank:** ${hunterRank.rankCode} (${hunterRank.title}) | " +
                "**Lv:** ${profile.level} | **XP:** ${profile.totalXp} | " +
                "**Streak:** ${profile.currentWorkoutStreakDays}d (Best ${profile.longestWorkoutStreakDays}d) | " +
                "**Flame:** ${profile.flameHealthState.name} (${profile.flameHealthState.healthPercent}%)"
        )
        sb.appendLine("**Targets:** ${dailyCalorieGoal}kcal, ${dailyProteinGoal}g Protein | **Weight:** ${"%.1f".format(profile.currentWeightKg)}kg")
        sb.appendLine()

        // Section 2: Active Weekly Workout Plan Summary (Days 1-7)
        val planTitle = activePlan?.name ?: "ForgeQuest Hybrid Calisthenics & Iron"
        sb.appendLine("## 1. Active 7-Day Plan: $planTitle")
        val exercisesByDayId = plannedExercises.groupBy { it.dayId }
        val sortedDays = plannedDays.sortedBy { it.dayOfWeek }
        if (sortedDays.isEmpty()) {
            sb.appendLine("- Default 7-Day Calisthenics & Weighted Split (Rest: Day 4, Day 7)")
        } else {
            for (day in sortedDays) {
                if (day.isRestDay) {
                    sb.appendLine("- **D${day.dayOfWeek} (${day.title.take(28)}):** REST DAY")
                } else {
                    val exSummary = (exercisesByDayId[day.dayId] ?: emptyList())
                        .sortedBy { it.orderIndex }
                        .take(5)
                        .joinToString(", ") { ex ->
                            val unit = if (ex.category == ExerciseCategory.ISOMETRIC) "s" else ""
                            "${ex.name.take(22)} (${ex.targetSets}x${ex.targetRepsOrSeconds}$unit)"
                        }
                    sb.appendLine("- **D${day.dayOfWeek} (${day.title.take(26)}):** $exSummary")
                }
            }
        }
        sb.appendLine()

        // Section 3: Last 14 Days Nutrition & Body Weight Table
        sb.appendLine("## 2. Last 14 Days Nutrition & Weight")
        sb.appendLine("| Date | kcal | Protein(g) | Weight(kg) |")
        sb.appendLine("|---|---|---|---|")

        val mealsByDay = mealLogsLast14Days.groupBy { it.epochDay }
        val weightsByDay = weightLogsLast14Days.associateBy { it.epochDay }
        var runningWeight = profile.currentWeightKg

        val startDay = todayEpochDay - 13L
        for (day in startDay..todayEpochDay) {
            val dateStr = LocalDate.ofEpochDay(day).format(shortDateFormatter)
            val dayMeals = mealsByDay[day].orEmpty()
            val cals = dayMeals.sumOf { it.totalCalories }
            val prot = dayMeals.sumOf { it.totalProteinGrams.toDouble() }.roundToInt()
            weightsByDay[day]?.let { runningWeight = it.weightKg }
            val weightStr = "%.1f".format(runningWeight)
            sb.appendLine("| $dateStr | $cals | ${prot}g | $weightStr |")
        }
        sb.appendLine()

        // Section 4: Exercise PRs & Last 3 Logged Sessions Table
        sb.appendLine("## 3. Exercise PRs & Last 3 Sessions")
        sb.appendLine("| Exercise | Max Set PR | Max Vol PR | Last 3 Sessions (Date: Sets) |")
        sb.appendLine("|---|---|---|---|")

        val sessionsById = completedSessions.associateBy { it.sessionId }
        val validSets = completedSets.filter { it.isCompleted && sessionsById.containsKey(it.sessionId) }
        val setsByExerciseName = validSets.groupBy { it.exerciseName.trim() }

        if (setsByExerciseName.isEmpty()) {
            val seededExercises = plannedExercises.distinctBy { it.name }.take(8)
            for (ex in seededExercises) {
                sb.appendLine("| ${ex.name.take(24)} | -- | -- | No sessions logged yet |")
            }
        } else {
            val exerciseEntries = setsByExerciseName.entries.take(MAX_EXERCISES_IN_SUMMARY)
            for ((exName, exSets) in exerciseEntries) {
                val category = exSets.first().category
                val maxSingle = exSets.maxOf {
                    if (it.category == ExerciseCategory.ISOMETRIC) max(it.holdSeconds, it.reps) else it.reps
                }
                val unit = if (category == ExerciseCategory.ISOMETRIC) "s" else "r"
                val setsBySession = exSets.groupBy { it.sessionId }
                val maxVol = setsBySession.values.maxOfOrNull {
                    FinishWorkoutUseCase.calculateExerciseSessionVolume(it).roundToInt()
                } ?: 0

                val last3Sessions = setsBySession.entries
                    .sortedByDescending { (sessionId, _) ->
                        sessionsById[sessionId]?.completedAtEpochMillis
                            ?: sessionsById[sessionId]?.startedAtEpochMillis ?: 0L
                    }
                    .take(3)
                    .joinToString("; ") { (sessionId, sSets) ->
                        val epochDay = sessionsById[sessionId]?.epochDay ?: todayEpochDay
                        val dStr = LocalDate.ofEpochDay(epochDay).format(shortDateFormatter)
                        val setsCompact = sSets.sortedBy { it.setNumber }.take(4).joinToString(",") { s ->
                            when {
                                s.category == ExerciseCategory.ISOMETRIC -> "${max(s.holdSeconds, s.reps)}s"
                                s.weightKg > 0f -> "${s.reps}x${s.weightKg.roundToInt()}kg"
                                else -> "${s.reps}"
                            }
                        }
                        "$dStr: $setsCompact"
                    }

                sb.appendLine("| ${exName.take(22)} | $maxSingle$unit | $maxVol | $last3Sessions |")
            }
        }

        return enforceTokenBudget(sb.toString())
    }

    suspend fun execute(
        dao: ForgeQuestDao,
        todayEpochDay: Long = LocalDate.now().toEpochDay(),
        dailyCalorieGoal: Int = 2100,
        dailyProteinGoal: Int = 140
    ): String {
        val profile = dao.getUserProfile() ?: UserProfileEntity()
        val activePlan = dao.getActivePlan()
        val plannedDays = if (activePlan != null) {
            dao.getDaysForPlan(activePlan.planId)
        } else {
            emptyList()
        }
        val plannedExercises = dao.getAllPlannedExercises()
        val startEpochDay = todayEpochDay - 13L
        val mealLogs = dao.getMealLogsInRange(startEpochDay, todayEpochDay)
        val weightLogs = dao.getWeightLogsInRange(startEpochDay - 30L, todayEpochDay)
        val completedSessions = dao.getCompletedSessions()
        val completedSets = dao.getAllSubmittedCompletedSets()

        return generateMarkdown(
            profile = profile,
            activePlan = activePlan,
            plannedDays = plannedDays,
            plannedExercises = plannedExercises,
            mealLogsLast14Days = mealLogs,
            weightLogsLast14Days = weightLogs,
            completedSessions = completedSessions,
            completedSets = completedSets,
            todayEpochDay = todayEpochDay,
            dailyCalorieGoal = dailyCalorieGoal,
            dailyProteinGoal = dailyProteinGoal
        )
    }

    private fun enforceTokenBudget(rawMarkdown: String): String {
        if (estimateTokenCount(rawMarkdown) < MAX_TOKEN_BUDGET && rawMarkdown.length <= MAX_CHAR_BUDGET) {
            return rawMarkdown.trim()
        }
        val lines = rawMarkdown.lines()
        val kept = StringBuilder()
        for (line in lines) {
            if (kept.length + line.length + 1 > MAX_CHAR_BUDGET) break
            val candidate = if (kept.isEmpty()) line else "$kept\n$line"
            if (estimateTokenCount(candidate) >= MAX_TOKEN_BUDGET - 8) break
            if (kept.isNotEmpty()) kept.append('\n')
            kept.append(line)
        }
        return kept.toString().trim()
    }

    companion object {
        const val MAX_TOKEN_BUDGET = 800
        const val MAX_CHAR_BUDGET = 2850
        private const val MAX_EXERCISES_IN_SUMMARY = 12

        /**
         * Conservative token estimator (~4 chars per token or whitespace/table delimiter tokens)
         * to guarantee the summary stays strictly under 800 tokens.
         */
        fun estimateTokenCount(markdown: String): Int {
            if (markdown.isBlank()) return 0
            val charBasedEstimate = ceil(markdown.length / 3.8).toInt()
            val wordBasedEstimate = markdown.trim().split(Regex("\\s+")).size
            return max(charBasedEstimate, wordBasedEstimate)
        }
    }
}
