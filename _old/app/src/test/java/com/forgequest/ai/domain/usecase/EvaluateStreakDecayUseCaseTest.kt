package com.forgequest.ai.domain.usecase

import com.forgequest.ai.data.local.entity.UserProfileEntity
import com.forgequest.ai.domain.model.FlameHealthState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

class EvaluateStreakDecayUseCaseTest {

    private lateinit var useCase: EvaluateStreakDecayUseCase

    @Before
    fun setUp() {
        useCase = EvaluateStreakDecayUseCase()
    }

    @Test
    fun `1 missed scheduled workout day transitions INFERNO to FLICKERING and preserves streak`() {
        // 2026-10-12 is Monday (dayOfWeek = 1)
        val mondayEpoch = LocalDate.of(2026, 10, 12).toEpochDay()
        val tuesdayEpoch = mondayEpoch + 1L

        val initial = UserProfileEntity(
            totalXp = 1250L,
            level = 3,
            rankCode = "E-Rank",
            currentWorkoutStreakDays = 7,
            longestWorkoutStreakDays = 10,
            consecutiveMissedWorkoutDays = 0,
            flameHealthState = FlameHealthState.INFERNO,
            lastStreakEvaluationEpochDay = mondayEpoch
        )

        val result = useCase.evaluateStreakDecay(
            profile = initial,
            currentEpochDay = tuesdayEpoch,
            scheduledRestDaysOfWeek = setOf(4, 7),
            completedWorkoutEpochDays = emptySet()
        )

        assertEquals(FlameHealthState.FLICKERING, result.flameHealthState)
        assertEquals(66, result.flameHealthState.healthPercent)
        assertEquals(1, result.consecutiveMissedWorkoutDays)
        assertEquals(7, result.currentWorkoutStreakDays)
        assertEquals(1250L, result.totalXp)
    }

    @Test
    fun `2 consecutive missed scheduled workout days transitions to DYING_EMBER and preserves streak`() {
        val mondayEpoch = LocalDate.of(2026, 10, 12).toEpochDay()
        val wednesdayEpoch = mondayEpoch + 2L

        val initial = UserProfileEntity(
            totalXp = 2500L,
            level = 6,
            rankCode = "D-Rank",
            currentWorkoutStreakDays = 12,
            longestWorkoutStreakDays = 12,
            consecutiveMissedWorkoutDays = 0,
            flameHealthState = FlameHealthState.INFERNO,
            lastStreakEvaluationEpochDay = mondayEpoch
        )

        val result = useCase.evaluateStreakDecay(
            profile = initial,
            currentEpochDay = wednesdayEpoch,
            scheduledRestDaysOfWeek = setOf(4, 7),
            completedWorkoutEpochDays = emptySet()
        )

        assertEquals(FlameHealthState.DYING_EMBER, result.flameHealthState)
        assertEquals(33, result.flameHealthState.healthPercent)
        assertEquals(2, result.consecutiveMissedWorkoutDays)
        assertEquals(12, result.currentWorkoutStreakDays)
        assertEquals(2500L, result.totalXp)
        assertEquals(6, result.level)
        assertEquals("D-Rank", result.rankCode)
    }

    @Test
    fun `3 consecutive missed scheduled workout days transitions to EXTINGUISHED and resets streak while keeping RPG XP and Rank`() {
        val mondayEpoch = LocalDate.of(2026, 10, 12).toEpochDay()
        // Mon -> Tue (missed 1), Wed (missed 2), Thu is NOT rest day here -> (missed 3)
        val thursdayEpoch = mondayEpoch + 3L

        val initial = UserProfileEntity(
            totalXp = 5400L,
            level = 11,
            rankCode = "C-Rank",
            currentWorkoutStreakDays = 19,
            longestWorkoutStreakDays = 19,
            consecutiveMissedWorkoutDays = 0,
            flameHealthState = FlameHealthState.INFERNO,
            lastStreakEvaluationEpochDay = mondayEpoch
        )

        val result = useCase.evaluateStreakDecay(
            profile = initial,
            currentEpochDay = thursdayEpoch,
            scheduledRestDaysOfWeek = setOf(7),
            completedWorkoutEpochDays = emptySet()
        )

        assertEquals(FlameHealthState.EXTINGUISHED, result.flameHealthState)
        assertEquals(0, result.flameHealthState.healthPercent)
        assertEquals(3, result.consecutiveMissedWorkoutDays)
        assertEquals(0, result.currentWorkoutStreakDays)
        assertEquals(19, result.longestWorkoutStreakDays)
        assertEquals(5400L, result.totalXp)
        assertEquals(11, result.level)
        assertEquals("C-Rank", result.rankCode)
    }

    @Test
    fun `scheduled rest days and manual rest check-ins preserve flame health and streak without penalty`() {
        // Wednesday -> Thursday (Day 4, Scheduled Rest Day) -> Friday (Manual Rest Check-In)
        val wednesdayEpoch = LocalDate.of(2026, 10, 14).toEpochDay()
        val thursdayEpoch = wednesdayEpoch + 1L // dayOfWeek = 4
        val fridayEpoch = wednesdayEpoch + 2L   // dayOfWeek = 5

        val initial = UserProfileEntity(
            currentWorkoutStreakDays = 9,
            longestWorkoutStreakDays = 9,
            consecutiveMissedWorkoutDays = 1,
            flameHealthState = FlameHealthState.FLICKERING,
            lastStreakEvaluationEpochDay = wednesdayEpoch,
            lastRestCheckInEpochDay = fridayEpoch
        )

        val result = useCase.evaluateStreakDecay(
            profile = initial,
            currentEpochDay = fridayEpoch,
            scheduledRestDaysOfWeek = setOf(4, 7),
            completedWorkoutEpochDays = emptySet()
        )

        assertEquals(FlameHealthState.FLICKERING, result.flameHealthState)
        assertEquals(1, result.consecutiveMissedWorkoutDays)
        assertEquals(9, result.currentWorkoutStreakDays)
        assertEquals(fridayEpoch, result.lastStreakEvaluationEpochDay)
    }

    @Test
    fun `completing a workout from DYING_EMBER triggers reignitedFlame and restores INFERNO`() {
        val today = LocalDate.of(2026, 10, 17).toEpochDay()
        val dyingEmberProfile = UserProfileEntity(
            currentWorkoutStreakDays = 14,
            longestWorkoutStreakDays = 14,
            consecutiveMissedWorkoutDays = 2,
            flameHealthState = FlameHealthState.DYING_EMBER,
            lastStreakEvaluationEpochDay = today - 1L
        )

        val completion = useCase.onWorkoutCompleted(
            profile = dyingEmberProfile,
            completedEpochDay = today,
            alreadyCompletedWorkoutToday = false
        )

        assertTrue(completion.reignitedFlame)
        assertEquals(FlameHealthState.DYING_EMBER, completion.previousFlameState)
        assertEquals(FlameHealthState.INFERNO, completion.newFlameState)
        assertEquals(0, completion.updatedProfile.consecutiveMissedWorkoutDays)
        assertEquals(15, completion.updatedProfile.currentWorkoutStreakDays)
        assertEquals(15, completion.updatedProfile.longestWorkoutStreakDays)

        // Subsequent workout when already INFERNO does not flag reignitedFlame
        val secondCompletion = useCase.onWorkoutCompleted(
            profile = completion.updatedProfile,
            completedEpochDay = today + 1L,
            alreadyCompletedWorkoutToday = false
        )
        assertFalse(secondCompletion.reignitedFlame)
        assertEquals(16, secondCompletion.updatedProfile.currentWorkoutStreakDays)
    }
}
