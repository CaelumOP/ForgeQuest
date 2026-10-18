package com.forgequest.ai.domain.usecase

import com.forgequest.ai.data.local.entity.LoggedSetEntity
import com.forgequest.ai.data.local.entity.UserProfileEntity
import com.forgequest.ai.data.local.entity.WorkoutSessionEntity
import com.forgequest.ai.domain.model.ExerciseCategory
import com.forgequest.ai.domain.model.FlameHealthState
import com.forgequest.ai.domain.model.HunterRank
import com.forgequest.ai.domain.model.MuscleGroup
import com.forgequest.ai.domain.model.PrType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FinishWorkoutUseCaseTest {

    private lateinit var useCase: FinishWorkoutUseCase

    @Before
    fun setUp() {
        useCase = FinishWorkoutUseCase()
    }

    @Test
    fun `evaluateSessionSubmission detects single-set reps PR, session volume PR, and 100 percent Quest Clear PR`() {
        val historicalSets = listOf(
            LoggedSetEntity(
                setId = 1L,
                sessionId = 10L,
                exerciseId = "pull_up_standard",
                exerciseName = "Chest-to-Bar Pull-Ups",
                category = ExerciseCategory.CALISTHENICS,
                setNumber = 1,
                reps = 10,
                weightKg = 0f,
                isCompleted = true,
                primaryMuscles = listOf(MuscleGroup.LATS)
            ),
            LoggedSetEntity(
                setId = 2L,
                sessionId = 10L,
                exerciseId = "pull_up_standard",
                exerciseName = "Chest-to-Bar Pull-Ups",
                category = ExerciseCategory.CALISTHENICS,
                setNumber = 2,
                reps = 10,
                weightKg = 0f,
                isCompleted = true,
                primaryMuscles = listOf(MuscleGroup.LATS)
            )
        )

        val currentSession = WorkoutSessionEntity(
            sessionId = 25L,
            dayTitle = "Day 2: Upper Pull & Front Lever",
            startedAtEpochMillis = 1_700_000_000_000L,
            epochDay = 20000L,
            isSubmitted = false
        )

        val currentSets = listOf(
            LoggedSetEntity(
                setId = 101L,
                sessionId = 25L,
                exerciseId = "pull_up_standard",
                exerciseName = "Chest-to-Bar Pull-Ups",
                category = ExerciseCategory.CALISTHENICS,
                setNumber = 1,
                reps = 13, // Beats previous max single set (10)
                weightKg = 0f,
                isCompleted = true,
                primaryMuscles = listOf(MuscleGroup.LATS, MuscleGroup.UPPER_BACK_TRAPS),
                secondaryMuscles = listOf(MuscleGroup.BICEPS)
            ),
            LoggedSetEntity(
                setId = 102L,
                sessionId = 25L,
                exerciseId = "pull_up_standard",
                exerciseName = "Chest-to-Bar Pull-Ups",
                category = ExerciseCategory.CALISTHENICS,
                setNumber = 2,
                reps = 12, // Total volume 25 > previous session volume 20
                weightKg = 0f,
                isCompleted = true,
                primaryMuscles = listOf(MuscleGroup.LATS, MuscleGroup.UPPER_BACK_TRAPS),
                secondaryMuscles = listOf(MuscleGroup.BICEPS)
            )
        )

        val profile = UserProfileEntity(
            totalXp = 1900L,
            level = 4,
            rankCode = "E-Rank",
            currentWorkoutStreakDays = 4,
            longestWorkoutStreakDays = 4,
            flameHealthState = FlameHealthState.FLICKERING
        )

        val (summary, updatedProfile) = useCase.evaluateSessionSubmission(
            session = currentSession,
            sessionSets = currentSets,
            historicalCompletedSets = historicalSets,
            profile = profile,
            alreadyCompletedWorkoutToday = false
        )

        // PRs: MAX_SINGLE_SET_REPS + MAX_EXERCISE_VOLUME + QUEST_100_PERCENT_CLEAR = 3 PR cards
        assertEquals(3, summary.prCards.size)
        assertTrue(summary.prCards.any { it.prType == PrType.MAX_SINGLE_SET_REPS && it.newValueLabel == "13 reps" })
        assertTrue(summary.prCards.any { it.prType == PrType.MAX_EXERCISE_VOLUME && it.newValueLabel == "25 vol" })
        assertTrue(summary.prCards.any { it.prType == PrType.QUEST_100_PERCENT_CLEAR })

        // XP: 2 sets * 25 (50) + 150 completion + 3 PRs * 100 (300) = 500 XP
        assertEquals(500, summary.earnedXp)
        assertEquals(2400L, summary.newTotalXp)
        assertEquals(5, summary.newLevel)
        assertEquals(HunterRank.D_RANK, summary.newRank)
        assertEquals("D-Rank", updatedProfile.rankCode)

        // Reignited from FLICKERING -> INFERNO
        assertTrue(summary.reignitedFlame)
        assertEquals(FlameHealthState.INFERNO, summary.newFlameState)
        assertEquals(5, summary.newStreakDays)

        // Ignited muscles: LATS & UPPER_BACK_TRAPS at 1.0f, BICEPS at 0.5f
        assertEquals(1.0f, summary.ignitedMuscles[MuscleGroup.LATS] ?: 0f, 0.001f)
        assertEquals(0.5f, summary.ignitedMuscles[MuscleGroup.BICEPS] ?: 0f, 0.001f)
    }

    @Test
    fun `no PRs are triggered when performance is equal to or below historical records and sets are partial`() {
        val historicalSets = listOf(
            LoggedSetEntity(
                setId = 1L,
                sessionId = 1L,
                exerciseId = "dips",
                exerciseName = "Weighted Dips",
                category = ExerciseCategory.WEIGHTED,
                setNumber = 1,
                reps = 10,
                weightKg = 20f, // Volume = 200
                isCompleted = true,
                primaryMuscles = listOf(MuscleGroup.CHEST, MuscleGroup.TRICEPS)
            )
        )

        val currentSession = WorkoutSessionEntity(
            sessionId = 2L,
            dayTitle = "Push Day",
            startedAtEpochMillis = 1_700_000_000_000L,
            epochDay = 20001L
        )

        val currentSets = listOf(
            LoggedSetEntity(
                setId = 2L,
                sessionId = 2L,
                exerciseId = "dips",
                exerciseName = "Weighted Dips",
                category = ExerciseCategory.WEIGHTED,
                setNumber = 1,
                reps = 8,
                weightKg = 20f, // Volume = 160 (< 200) and reps 8 (< 10)
                isCompleted = true,
                primaryMuscles = listOf(MuscleGroup.CHEST, MuscleGroup.TRICEPS)
            ),
            LoggedSetEntity(
                setId = 3L,
                sessionId = 2L,
                exerciseId = "dips",
                exerciseName = "Weighted Dips",
                category = ExerciseCategory.WEIGHTED,
                setNumber = 2,
                reps = 8,
                weightKg = 20f,
                isCompleted = false, // Uncompleted set -> not 100% clear
                primaryMuscles = listOf(MuscleGroup.CHEST, MuscleGroup.TRICEPS)
            )
        )

        val (summary, _) = useCase.evaluateSessionSubmission(
            session = currentSession,
            sessionSets = currentSets,
            historicalCompletedSets = historicalSets,
            profile = UserProfileEntity()
        )

        assertTrue(summary.prCards.isEmpty())
        // 1 completed set (25 XP) + 150 completion = 175 XP
        assertEquals(175, summary.earnedXp)
    }

    @Test
    fun `calculateMuscleIntensity linearly decays over 48 hours`() {
        val trainedAt = 1_700_000_000_000L
        val oneHourMs = 3_600_000L

        // 0 hours -> 1.0f
        assertEquals(
            1.0f,
            FinishWorkoutUseCase.calculateMuscleIntensity(trainedAt, trainedAt, baseFactor = 1.0f),
            0.001f
        )

        // 12 hours -> 0.75f (Inferno Coral zone >= 0.67)
        assertEquals(
            0.75f,
            FinishWorkoutUseCase.calculateMuscleIntensity(trainedAt, trainedAt + 12 * oneHourMs, baseFactor = 1.0f),
            0.001f
        )

        // 24 hours -> 0.50f (Recovering Amber zone 0.25..0.66)
        assertEquals(
            0.50f,
            FinishWorkoutUseCase.calculateMuscleIntensity(trainedAt, trainedAt + 24 * oneHourMs, baseFactor = 1.0f),
            0.001f
        )

        // 24 hours for secondary muscle (baseFactor = 0.5f) -> 0.25f
        assertEquals(
            0.25f,
            FinishWorkoutUseCase.calculateMuscleIntensity(trainedAt, trainedAt + 24 * oneHourMs, baseFactor = 0.5f),
            0.001f
        )

        // 48 hours+ -> 0.0f (Fully Recovered Emerald)
        assertEquals(
            0.0f,
            FinishWorkoutUseCase.calculateMuscleIntensity(trainedAt, trainedAt + 48 * oneHourMs, baseFactor = 1.0f),
            0.001f
        )
        assertEquals(
            0.0f,
            FinishWorkoutUseCase.calculateMuscleIntensity(trainedAt, trainedAt + 72 * oneHourMs, baseFactor = 1.0f),
            0.001f
        )
    }
}
