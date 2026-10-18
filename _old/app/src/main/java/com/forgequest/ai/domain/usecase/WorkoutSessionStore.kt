package com.forgequest.ai.domain.usecase

import com.forgequest.ai.data.local.dao.ForgeQuestDao
import com.forgequest.ai.data.local.entity.LoggedSetEntity
import com.forgequest.ai.data.local.entity.PlannedDayEntity
import com.forgequest.ai.data.local.entity.PlannedExerciseEntity
import com.forgequest.ai.data.local.entity.WorkoutSessionEntity
import com.forgequest.ai.domain.model.ExerciseCategory
import com.forgequest.ai.domain.model.MuscleGroup
import com.forgequest.ai.domain.model.QuestClearSummary
import com.forgequest.ai.domain.model.SetToggleResult
import com.forgequest.ai.domain.model.WorkoutSessionSnapshot
import kotlinx.coroutines.flow.Flow

/**
 * Seam: Interface defining the storage operations required by LiveWorkoutSessionManager.
 * Allows pure, lightweight testing and decouples the manager from Room's 60-method DAO.
 */
interface WorkoutSessionStore {
    fun observeActiveSession(): Flow<WorkoutSessionEntity?>
    fun observeSetsForSession(sessionId: Long): Flow<List<LoggedSetEntity>>
    suspend fun getActiveSession(): WorkoutSessionEntity?
    suspend fun insertWorkoutSession(session: WorkoutSessionEntity): Long
    suspend fun deleteWorkoutSession(sessionId: Long)
    suspend fun deleteSetsForSession(sessionId: Long)
    suspend fun insertLoggedSets(sets: List<LoggedSetEntity>): List<Long>
    suspend fun getLastSessionSetsForExercise(exerciseId: String, exerciseName: String, excludeSessionId: Long): List<LoggedSetEntity>
    suspend fun updateLoggedSet(set: LoggedSetEntity)
    suspend fun getSetsForSession(sessionId: Long): List<LoggedSetEntity>
    suspend fun deleteLoggedSet(setId: Long)
    suspend fun getExercisesForDay(dayId: Long): List<PlannedExerciseEntity>
    suspend fun upsertPlannedExercise(exercise: PlannedExerciseEntity)
    suspend fun executeFinishWorkout(sessionId: Long): QuestClearSummary
}

/**
 * Adapter bridging [ForgeQuestDao] and [FinishWorkoutUseCase] to [WorkoutSessionStore].
 */
class RoomWorkoutSessionStoreAdapter(
    private val dao: ForgeQuestDao,
    private val finishWorkoutUseCase: FinishWorkoutUseCase = FinishWorkoutUseCase()
) : WorkoutSessionStore {
    override fun observeActiveSession(): Flow<WorkoutSessionEntity?> = dao.observeActiveSession()
    override fun observeSetsForSession(sessionId: Long): Flow<List<LoggedSetEntity>> = dao.observeSetsForSession(sessionId)
    override suspend fun getActiveSession(): WorkoutSessionEntity? = dao.getActiveSession()
    override suspend fun insertWorkoutSession(session: WorkoutSessionEntity): Long = dao.insertWorkoutSession(session)
    override suspend fun deleteWorkoutSession(sessionId: Long) = dao.deleteWorkoutSession(sessionId)
    override suspend fun deleteSetsForSession(sessionId: Long) = dao.deleteSetsForSession(sessionId)
    override suspend fun insertLoggedSets(sets: List<LoggedSetEntity>): List<Long> = dao.insertLoggedSets(sets)
    override suspend fun getLastSessionSetsForExercise(exerciseId: String, exerciseName: String, excludeSessionId: Long): List<LoggedSetEntity> =
        dao.getLastSessionSetsForExercise(exerciseId, exerciseName, excludeSessionId)
    override suspend fun updateLoggedSet(set: LoggedSetEntity) = dao.updateLoggedSet(set)
    override suspend fun getSetsForSession(sessionId: Long): List<LoggedSetEntity> = dao.getSetsForSession(sessionId)
    override suspend fun deleteLoggedSet(setId: Long) = dao.deleteLoggedSet(setId)
    override suspend fun getExercisesForDay(dayId: Long): List<PlannedExerciseEntity> = dao.getExercisesForDay(dayId)
    override suspend fun upsertPlannedExercise(exercise: PlannedExerciseEntity) = dao.upsertPlannedExercise(exercise)
    override suspend fun executeFinishWorkout(sessionId: Long): QuestClearSummary = finishWorkoutUseCase.execute(dao, sessionId)
}
