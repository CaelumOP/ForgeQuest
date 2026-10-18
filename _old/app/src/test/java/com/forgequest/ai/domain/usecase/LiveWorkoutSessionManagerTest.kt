package com.forgequest.ai.domain.usecase

import com.forgequest.ai.data.local.entity.LoggedSetEntity
import com.forgequest.ai.data.local.entity.PlannedDayEntity
import com.forgequest.ai.data.local.entity.PlannedExerciseEntity
import com.forgequest.ai.data.local.entity.WorkoutSessionEntity
import com.forgequest.ai.domain.model.ExerciseCategory
import com.forgequest.ai.domain.model.MuscleGroup
import com.forgequest.ai.domain.model.QuestClearSummary
import com.forgequest.ai.domain.model.WorkoutSessionSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Pure, self-contained unit tests for LiveWorkoutSessionManager & WorkoutSessionSnapshot
 * verified through the [WorkoutSessionStore] architectural seam.
 */
class LiveWorkoutSessionManagerTest {

    private lateinit var fakeStore: FakeWorkoutSessionStore
    private lateinit var sessionManager: LiveWorkoutSessionManager

    @Before
    fun setUp() {
        fakeStore = FakeWorkoutSessionStore()
        sessionManager = LiveWorkoutSessionManager(fakeStore)
    }

    @Test
    fun `startOrResumeSession resumes existing session if already active for same day`() = runBlocking {
        val existingSession = WorkoutSessionEntity(
            sessionId = 42L,
            planId = 1L,
            dayId = 101L,
            dayTitle = "Upper Body Calisthenics",
            startedAtEpochMillis = 1000L,
            epochDay = 20000L,
            isSubmitted = false
        )
        fakeStore.activeSession = existingSession

        val day = PlannedDayEntity(
            dayId = 101L,
            planId = 1L,
            dayOfWeek = 1,
            title = "Upper Body Calisthenics",
            isRestDay = false
        )

        val sessionId = sessionManager.startOrResumeSession(day, emptyList())

        assertEquals(42L, sessionId)
    }

    @Test
    fun `toggleSetCompleted updates set atomically and returns haptic recommendation`() = runBlocking {
        val set = LoggedSetEntity(
            setId = 5L,
            sessionId = 10L,
            exerciseId = "pull_up",
            exerciseName = "Weighted Pull-Ups",
            category = ExerciseCategory.WEIGHTED,
            setNumber = 1,
            reps = 8,
            weightKg = 10f,
            isCompleted = false,
            primaryMuscles = listOf(MuscleGroup.LATS)
        )

        val result = sessionManager.toggleSetCompleted(set, completed = true, restDurationSeconds = 90)

        assertTrue(result.isCompletedNow)
        assertTrue(result.shouldTriggerHaptic)
        assertEquals(90, result.recommendedRestDurationSeconds)
        assertTrue(result.updatedSet.isCompleted)
        assertTrue(result.updatedSet.completedAtEpochMillis > 0L)
        assertEquals(1, fakeStore.updatedSets.size)
        assertEquals(5L, fakeStore.updatedSets.first().setId)
    }

    @Test
    fun `WorkoutSessionSnapshot correctly computes completion fraction`() {
        val session = WorkoutSessionEntity(
            sessionId = 1L,
            dayTitle = "Push Day",
            startedAtEpochMillis = 1000L,
            epochDay = 20000L
        )
        val sets = listOf(
            LoggedSetEntity(
                setId = 1L,
                sessionId = 1L,
                exerciseId = "e1",
                exerciseName = "Dips",
                category = ExerciseCategory.CALISTHENICS,
                setNumber = 1,
                reps = 10,
                isCompleted = true,
                primaryMuscles = listOf(MuscleGroup.CHEST)
            ),
            LoggedSetEntity(
                setId = 2L,
                sessionId = 1L,
                exerciseId = "e1",
                exerciseName = "Dips",
                category = ExerciseCategory.CALISTHENICS,
                setNumber = 2,
                reps = 10,
                isCompleted = false,
                primaryMuscles = listOf(MuscleGroup.CHEST)
            )
        )

        val snapshot = WorkoutSessionSnapshot.from(session, sets)

        assertTrue(snapshot.isSessionActive)
        assertEquals(2, snapshot.totalSetsCount)
        assertEquals(1, snapshot.completedSetsCount)
        assertEquals(0.5f, snapshot.progressFraction, 0.001f)
    }

    private class FakeWorkoutSessionStore : WorkoutSessionStore {
        var activeSession: WorkoutSessionEntity? = null
        val updatedSets = mutableListOf<LoggedSetEntity>()
        val insertedSets = mutableListOf<LoggedSetEntity>()

        override fun observeActiveSession(): Flow<WorkoutSessionEntity?> = flowOf(activeSession)
        override fun observeSetsForSession(sessionId: Long): Flow<List<LoggedSetEntity>> = flowOf(emptyList())
        override suspend fun getActiveSession(): WorkoutSessionEntity? = activeSession
        override suspend fun insertWorkoutSession(session: WorkoutSessionEntity): Long = 100L
        override suspend fun deleteWorkoutSession(sessionId: Long) { activeSession = null }
        override suspend fun deleteSetsForSession(sessionId: Long) {}
        override suspend fun insertLoggedSets(sets: List<LoggedSetEntity>): List<Long> {
            insertedSets.addAll(sets)
            return sets.map { it.setId }
        }
        override suspend fun getLastSessionSetsForExercise(
            exerciseId: String,
            exerciseName: String,
            excludeSessionId: Long
        ): List<LoggedSetEntity> = emptyList()

        override suspend fun updateLoggedSet(set: LoggedSetEntity) { updatedSets.add(set) }
        override suspend fun getSetsForSession(sessionId: Long): List<LoggedSetEntity> = emptyList()
        override suspend fun deleteLoggedSet(setId: Long) {}
        override suspend fun getExercisesForDay(dayId: Long): List<PlannedExerciseEntity> = emptyList()
        override suspend fun upsertPlannedExercise(exercise: PlannedExerciseEntity) {}
        override suspend fun executeFinishWorkout(sessionId: Long): QuestClearSummary {
            return QuestClearSummary(
                sessionId = sessionId,
                workoutTitle = "Test Workout",
                completedSetsCount = 1,
                totalPlannedSetsCount = 1,
                earnedXp = 100,
                previousTotalXp = 0L,
                newTotalXp = 100L,
                previousLevel = 1,
                newLevel = 1,
                newRank = com.forgequest.ai.domain.model.HunterRank.E_RANK,
                prCards = emptyList(),
                reignitedFlame = false,
                previousFlameState = com.forgequest.ai.domain.model.FlameHealthState.INFERNO,
                newFlameState = com.forgequest.ai.domain.model.FlameHealthState.INFERNO,
                newStreakDays = 1,
                ignitedMuscles = emptyMap()
            )
        }
    }
}
