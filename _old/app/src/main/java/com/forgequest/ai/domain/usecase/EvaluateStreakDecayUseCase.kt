package com.forgequest.ai.domain.usecase

import com.forgequest.ai.data.local.entity.UserProfileEntity
import com.forgequest.ai.domain.model.FlameHealthState
import java.time.LocalDate
import kotlin.math.max

data class StreakWorkoutCompletionResult(
    val updatedProfile: UserProfileEntity,
    val reignitedFlame: Boolean,
    val previousFlameState: FlameHealthState,
    val newFlameState: FlameHealthState
)

class EvaluateStreakDecayUseCase {

    /**
     * Evaluates the 4-stage "Ember-to-Inferno" Workout Streak State Machine
     * across each day after [UserProfileEntity.lastStreakEvaluationEpochDay] up to [currentEpochDay].
     *
     * Rules:
     * - Scheduled Rest Days ([scheduledRestDaysOfWeek], 1=Mon..7=Sun), manual rest day check-ins,
     *   or days with a submitted workout preserve the flame health and streak counter.
     * - 1 missed scheduled workout day -> [FlameHealthState.FLICKERING] (66%), streak counter preserved.
     * - 2 consecutive missed scheduled workout days -> [FlameHealthState.DYING_EMBER] (33%), streak counter preserved.
     * - 3+ consecutive missed scheduled workout days -> [FlameHealthState.EXTINGUISHED] (0%),
     *   resets `currentWorkoutStreakDays = 0` while keeping `totalXp`, `level`, and `rankCode` intact.
     */
    fun evaluateStreakDecay(
        profile: UserProfileEntity,
        currentEpochDay: Long,
        scheduledRestDaysOfWeek: Set<Int> = emptySet(),
        completedWorkoutEpochDays: Set<Long> = emptySet(),
        manualRestEpochDays: Set<Long> = emptySet()
    ): UserProfileEntity {
        val lastEval = profile.lastStreakEvaluationEpochDay
        if (lastEval == null) {
            return profile.copy(lastStreakEvaluationEpochDay = currentEpochDay)
        }
        if (currentEpochDay <= lastEval) {
            return profile
        }

        var streakDays = profile.currentWorkoutStreakDays
        var longestStreak = profile.longestWorkoutStreakDays
        var missedDays = profile.consecutiveMissedWorkoutDays
        var flameState = profile.flameHealthState

        for (day in (lastEval + 1L)..currentEpochDay) {
            val dayOfWeek = LocalDate.ofEpochDay(day).dayOfWeek.value // 1 (Mon) .. 7 (Sun)
            val isScheduledRest = scheduledRestDaysOfWeek.contains(dayOfWeek)
            val isManualRest = (profile.lastRestCheckInEpochDay == day) || manualRestEpochDays.contains(day)
            val hasCompletedWorkout = completedWorkoutEpochDays.contains(day)

            when {
                hasCompletedWorkout -> {
                    missedDays = 0
                    flameState = FlameHealthState.INFERNO
                    streakDays += 1
                    longestStreak = max(longestStreak, streakDays)
                }
                isScheduledRest || isManualRest -> {
                    // Rest days (scheduled or manual check-in) automatically pass and preserve flame & streak
                }
                else -> {
                    missedDays += 1
                    flameState = flameStateForMissedDays(missedDays)
                    if (missedDays >= 3) {
                        streakDays = 0
                    }
                }
            }
        }

        return profile.copy(
            currentWorkoutStreakDays = streakDays,
            longestWorkoutStreakDays = longestStreak,
            consecutiveMissedWorkoutDays = missedDays,
            flameHealthState = flameState,
            lastStreakEvaluationEpochDay = currentEpochDay
        )
    }

    /**
     * Updates the user's streak & flame state when a workout session is submitted.
     * - Detects if the flame was reignited from [FlameHealthState.FLICKERING] or [FlameHealthState.DYING_EMBER].
     * - Restores [FlameHealthState.INFERNO], resets `consecutiveMissedWorkoutDays = 0`,
     *   and increments `currentWorkoutStreakDays` (unless [alreadyCompletedWorkoutToday] is true).
     */
    fun onWorkoutCompleted(
        profile: UserProfileEntity,
        completedEpochDay: Long,
        alreadyCompletedWorkoutToday: Boolean = false
    ): StreakWorkoutCompletionResult {
        val previousFlame = profile.flameHealthState
        val reignited = previousFlame == FlameHealthState.FLICKERING ||
            previousFlame == FlameHealthState.DYING_EMBER

        val newStreak = if (alreadyCompletedWorkoutToday) {
            max(1, profile.currentWorkoutStreakDays)
        } else {
            profile.currentWorkoutStreakDays + 1
        }
        val newLongest = max(profile.longestWorkoutStreakDays, newStreak)

        val updated = profile.copy(
            currentWorkoutStreakDays = newStreak,
            longestWorkoutStreakDays = newLongest,
            consecutiveMissedWorkoutDays = 0,
            flameHealthState = FlameHealthState.INFERNO,
            lastStreakEvaluationEpochDay = max(profile.lastStreakEvaluationEpochDay ?: completedEpochDay, completedEpochDay)
        )

        return StreakWorkoutCompletionResult(
            updatedProfile = updated,
            reignitedFlame = reignited,
            previousFlameState = previousFlame,
            newFlameState = FlameHealthState.INFERNO
        )
    }

    /**
     * Records a 1-Tap Manual Rest Day Check-In for [todayEpochDay] so the user's flame
     * does not decay on an unplanned recovery day.
     */
    fun onManualRestDayCheckIn(
        profile: UserProfileEntity,
        todayEpochDay: Long
    ): UserProfileEntity {
        return profile.copy(
            lastRestCheckInEpochDay = todayEpochDay,
            lastStreakEvaluationEpochDay = max(profile.lastStreakEvaluationEpochDay ?: todayEpochDay, todayEpochDay)
        )
    }

    companion object {
        fun flameStateForMissedDays(consecutiveMissedDays: Int): FlameHealthState {
            return when {
                consecutiveMissedDays <= 0 -> FlameHealthState.INFERNO
                consecutiveMissedDays == 1 -> FlameHealthState.FLICKERING
                consecutiveMissedDays == 2 -> FlameHealthState.DYING_EMBER
                else -> FlameHealthState.EXTINGUISHED
            }
        }
    }
}
