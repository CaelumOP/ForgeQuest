package com.forgequest.ai.domain.usecase

import com.forgequest.ai.data.local.entity.BodyWeightLogEntity
import com.forgequest.ai.data.local.entity.LoggedSetEntity
import com.forgequest.ai.data.local.entity.MealLogEntity
import com.forgequest.ai.data.local.entity.PlannedDayEntity
import com.forgequest.ai.data.local.entity.PlannedExerciseEntity
import com.forgequest.ai.data.local.entity.UserProfileEntity
import com.forgequest.ai.data.local.entity.WorkoutPlanEntity
import com.forgequest.ai.data.local.entity.WorkoutSessionEntity
import com.forgequest.ai.domain.model.ExerciseCategory
import com.forgequest.ai.domain.model.FlameHealthState
import com.forgequest.ai.domain.model.FoodCategory
import com.forgequest.ai.domain.model.MuscleGroup
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

class GenerateProgressSummaryMarkdownUseCaseTest {

    private lateinit var useCase: GenerateProgressSummaryMarkdownUseCase

    @Before
    fun setUp() {
        useCase = GenerateProgressSummaryMarkdownUseCase()
    }

    @Test
    fun `generateMarkdown includes Rank, 7-day plan, 14-day nutrition table, and exercise PRs while staying strictly under 800 tokens`() {
        val today = LocalDate.of(2026, 10, 17).toEpochDay()
        val profile = UserProfileEntity(
            totalXp = 6200L,
            level = 13,
            rankCode = "C-Rank",
            currentWorkoutStreakDays = 21,
            longestWorkoutStreakDays = 25,
            flameHealthState = FlameHealthState.INFERNO,
            currentWeightKg = 71.4f
        )

        val plan = WorkoutPlanEntity(
            planId = 1L,
            name = "ForgeQuest Hybrid Calisthenics & Iron",
            description = "7-Day Split",
            isActive = true
        )

        val days = (1..7).map { dayNum ->
            PlannedDayEntity(
                dayId = dayNum.toLong(),
                planId = 1L,
                dayOfWeek = dayNum,
                title = if (dayNum == 4 || dayNum == 7) "Rest Day" else "Quest Day $dayNum",
                isRestDay = (dayNum == 4 || dayNum == 7)
            )
        }

        val exercises = (1..20).map { idx ->
            PlannedExerciseEntity(
                exerciseId = "ex_$idx",
                dayId = ((idx % 3) + 1).toLong(),
                orderIndex = idx,
                name = "Calisthenics Skill Progression #$idx",
                category = ExerciseCategory.CALISTHENICS,
                targetSets = 4,
                targetRepsOrSeconds = 10,
                primaryMuscles = listOf(MuscleGroup.LATS, MuscleGroup.CHEST),
                videoSearchFallback = "https://www.youtube.com/results?search_query=Hemo+Calisthenics"
            )
        }

        val mealLogs = ((today - 13L)..today).flatMap { day ->
            listOf(
                MealLogEntity(
                    epochDay = day,
                    loggedAtEpochMillis = day * 86_400_000L,
                    category = FoodCategory.BREAKFAST,
                    itemName = "Boiled / Fried Egg + Paratha Flatbread",
                    totalCalories = 680,
                    totalProteinGrams = 42f
                ),
                MealLogEntity(
                    epochDay = day,
                    loggedAtEpochMillis = day * 86_400_000L + 10_000L,
                    category = FoodCategory.DINNER,
                    itemName = "Charcoal Chicken & Laban",
                    totalCalories = 1480,
                    totalProteinGrams = 108f
                )
            )
        }

        val weightLogs = ((today - 13L)..today).map { day ->
            BodyWeightLogEntity(
                epochDay = day,
                weightKg = 71.0f + ((day - today + 13) * 0.05f),
                loggedAtEpochMillis = day * 86_400_000L
            )
        }

        val sessions = (1..15).map { sIdx ->
            WorkoutSessionEntity(
                sessionId = sIdx.toLong(),
                planId = 1L,
                dayId = 1L,
                dayTitle = "Quest Session $sIdx",
                startedAtEpochMillis = (today - sIdx) * 86_400_000L,
                completedAtEpochMillis = (today - sIdx) * 86_400_000L + 3_600_000L,
                epochDay = today - sIdx,
                isSubmitted = true
            )
        }

        val completedSets = sessions.flatMap { session ->
            (1..12).flatMap { exIdx ->
                (1..4).map { setNum ->
                    LoggedSetEntity(
                        sessionId = session.sessionId,
                        exerciseId = "ex_$exIdx",
                        exerciseName = "Pull-Up Progression #$exIdx",
                        category = ExerciseCategory.WEIGHTED,
                        setNumber = setNum,
                        reps = 10 + (setNum % 3),
                        weightKg = 15f,
                        isCompleted = true,
                        primaryMuscles = listOf(MuscleGroup.LATS)
                    )
                }
            }
        }

        val markdown = useCase.generateMarkdown(
            profile = profile,
            activePlan = plan,
            plannedDays = days,
            plannedExercises = exercises,
            mealLogsLast14Days = mealLogs,
            weightLogsLast14Days = weightLogs,
            completedSessions = sessions,
            completedSets = completedSets,
            todayEpochDay = today
        )

        val estimatedTokens = GenerateProgressSummaryMarkdownUseCase.estimateTokenCount(markdown)
        assertTrue(
            "Expected token count < 800, but was $estimatedTokens (length=${markdown.length})",
            estimatedTokens < GenerateProgressSummaryMarkdownUseCase.MAX_TOKEN_BUDGET
        )
        assertTrue(markdown.contains("C-Rank"))
        assertTrue(markdown.contains("Active 7-Day Plan"))
        assertTrue(markdown.contains("Last 14 Days Nutrition & Weight"))
        assertTrue(markdown.contains("Exercise PRs"))
    }
}
