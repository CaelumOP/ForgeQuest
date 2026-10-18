package com.forgequest.ai.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import com.forgequest.ai.data.local.entity.BodyWeightLogEntity
import com.forgequest.ai.data.local.entity.ChatMessageEntity
import com.forgequest.ai.data.local.entity.FullAppBackupPayload
import com.forgequest.ai.data.local.entity.LoggedSetEntity
import com.forgequest.ai.data.local.entity.MealLogEntity
import com.forgequest.ai.data.local.entity.MuscleRecoveryEntity
import com.forgequest.ai.data.local.entity.PlannedDayEntity
import com.forgequest.ai.data.local.entity.PlannedExerciseEntity
import com.forgequest.ai.data.local.entity.UserProfileEntity
import com.forgequest.ai.data.local.entity.VaultFoodItemEntity
import com.forgequest.ai.data.local.entity.WorkoutPlanEntity
import com.forgequest.ai.data.local.entity.WorkoutSessionEntity
import com.forgequest.ai.domain.model.FoodCategory
import com.forgequest.ai.domain.model.WorkoutPlanPayload
import kotlinx.coroutines.flow.Flow
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@Dao
interface ForgeQuestDao {

    // =========================================================================
    // 1. UserProfileEntity
    // =========================================================================

    @Query("SELECT * FROM user_profile WHERE id = 1 LIMIT 1")
    fun observeUserProfile(): Flow<UserProfileEntity?>

    @Query("SELECT * FROM user_profile WHERE id = 1 LIMIT 1")
    suspend fun getUserProfile(): UserProfileEntity?

    @Upsert
    suspend fun upsertUserProfile(profile: UserProfileEntity)

    // =========================================================================
    // 2. WorkoutPlanEntity, PlannedDayEntity, PlannedExerciseEntity
    // =========================================================================

    @Query("SELECT * FROM workout_plans WHERE isActive = 1 ORDER BY updatedAtEpochMillis DESC LIMIT 1")
    fun observeActivePlan(): Flow<WorkoutPlanEntity?>

    @Query("SELECT * FROM workout_plans WHERE isActive = 1 ORDER BY updatedAtEpochMillis DESC LIMIT 1")
    suspend fun getActivePlan(): WorkoutPlanEntity?

    @Query("SELECT * FROM workout_plans ORDER BY updatedAtEpochMillis DESC")
    suspend fun getAllWorkoutPlans(): List<WorkoutPlanEntity>

    @Query("UPDATE workout_plans SET isActive = 0")
    suspend fun deactivateAllPlans()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkoutPlan(plan: WorkoutPlanEntity): Long

    @Upsert
    suspend fun upsertWorkoutPlan(plan: WorkoutPlanEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkoutPlans(plans: List<WorkoutPlanEntity>)

    @Query("SELECT * FROM planned_days WHERE planId = :planId ORDER BY dayOfWeek ASC")
    fun observeDaysForPlan(planId: Long): Flow<List<PlannedDayEntity>>

    @Query("SELECT * FROM planned_days WHERE planId = :planId ORDER BY dayOfWeek ASC")
    suspend fun getDaysForPlan(planId: Long): List<PlannedDayEntity>

    @Query("SELECT * FROM planned_days ORDER BY planId ASC, dayOfWeek ASC")
    fun observeAllPlannedDays(): Flow<List<PlannedDayEntity>>

    @Query("SELECT * FROM planned_days ORDER BY planId ASC, dayOfWeek ASC")
    suspend fun getAllPlannedDays(): List<PlannedDayEntity>

    @Query("SELECT * FROM planned_days WHERE dayId = :dayId LIMIT 1")
    suspend fun getDayById(dayId: Long): PlannedDayEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlannedDay(day: PlannedDayEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlannedDays(days: List<PlannedDayEntity>): List<Long>

    @Upsert
    suspend fun upsertPlannedDay(day: PlannedDayEntity): Long

    @Query("DELETE FROM planned_days WHERE planId = :planId")
    suspend fun deleteDaysForPlan(planId: Long)

    @Query("SELECT * FROM planned_exercises WHERE dayId = :dayId ORDER BY orderIndex ASC")
    fun observeExercisesForDay(dayId: Long): Flow<List<PlannedExerciseEntity>>

    @Query("SELECT * FROM planned_exercises WHERE dayId = :dayId ORDER BY orderIndex ASC")
    suspend fun getExercisesForDay(dayId: Long): List<PlannedExerciseEntity>

    @Query("SELECT * FROM planned_exercises ORDER BY dayId ASC, orderIndex ASC")
    fun observeAllPlannedExercises(): Flow<List<PlannedExerciseEntity>>

    @Query("SELECT * FROM planned_exercises ORDER BY dayId ASC, orderIndex ASC")
    suspend fun getAllPlannedExercises(): List<PlannedExerciseEntity>

    @Query("SELECT * FROM planned_exercises WHERE exerciseId = :exerciseId LIMIT 1")
    suspend fun getPlannedExerciseById(exerciseId: String): PlannedExerciseEntity?

    @Upsert
    suspend fun upsertPlannedExercise(exercise: PlannedExerciseEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlannedExercises(exercises: List<PlannedExerciseEntity>)

    @Query("DELETE FROM planned_exercises WHERE exerciseId = :exerciseId")
    suspend fun deletePlannedExercise(exerciseId: String)

    @Query("DELETE FROM planned_exercises WHERE dayId = :dayId")
    suspend fun deleteExercisesForDay(dayId: Long)

    @Transaction
    suspend fun replaceActiveWorkoutPlanFromPayload(
        payload: WorkoutPlanPayload,
        sourceTag: String
    ): Long {
        deactivateAllPlans()
        val planId = insertWorkoutPlan(
            WorkoutPlanEntity(
                name = payload.planName,
                description = payload.description,
                isActive = true,
                sourceTag = sourceTag,
                updatedAtEpochMillis = System.currentTimeMillis()
            )
        )
        val sortedDays = (1..7).map { dayNum ->
            payload.days.find { it.dayOfWeek == dayNum }
                ?: com.forgequest.ai.domain.model.PlannedDayPayload(
                    dayOfWeek = dayNum,
                    title = if (dayNum == 4 || dayNum == 7) "Active Recovery & Mobility" else "Calisthenics Quest Day $dayNum",
                    isRestDay = (dayNum == 4 || dayNum == 7),
                    exercises = emptyList()
                )
        }
        sortedDays.forEach { dayPayload ->
            val dayId = insertPlannedDay(
                PlannedDayEntity(
                    planId = planId,
                    dayOfWeek = dayPayload.dayOfWeek,
                    title = dayPayload.title,
                    isRestDay = dayPayload.isRestDay
                )
            )
            if (!dayPayload.isRestDay) {
                val exerciseEntities = dayPayload.exercises.mapIndexed { idx, ex ->
                    val encodedQuery = runCatching {
                        URLEncoder.encode("Hemo Calisthenics ${ex.name}", StandardCharsets.UTF_8.name())
                    }.getOrDefault("Hemo+Calisthenics+${ex.name.replace(' ', '+')}")
                    val uniqueExerciseId = if (ex.exerciseId.isNotBlank()) {
                        "${ex.exerciseId}_d${dayPayload.dayOfWeek}_${idx + 1}"
                    } else {
                        "ex_${planId}_d${dayPayload.dayOfWeek}_${idx + 1}"
                    }
                    PlannedExerciseEntity(
                        exerciseId = uniqueExerciseId,
                        dayId = dayId,
                        orderIndex = idx,
                        name = ex.name,
                        category = ex.category,
                        targetSets = ex.targetSets.coerceAtLeast(1),
                        targetRepsOrSeconds = ex.targetRepsOrSeconds.coerceAtLeast(1),
                        restDurationSeconds = ex.restDurationSeconds.coerceAtLeast(15),
                        primaryMuscles = ex.primaryMuscles,
                        secondaryMuscles = ex.secondaryMuscles,
                        videoUrl = ex.videoUrl,
                        videoSearchFallback = "https://www.youtube.com/results?search_query=$encodedQuery"
                    )
                }
                insertPlannedExercises(exerciseEntities)
            }
        }
        return planId
    }

    // =========================================================================
    // 3. WorkoutSessionEntity & LoggedSetEntity
    // =========================================================================

    @Query("SELECT * FROM workout_sessions WHERE isSubmitted = 0 ORDER BY startedAtEpochMillis DESC LIMIT 1")
    fun observeActiveSession(): Flow<WorkoutSessionEntity?>

    @Query("SELECT * FROM workout_sessions WHERE isSubmitted = 0 ORDER BY startedAtEpochMillis DESC LIMIT 1")
    suspend fun getActiveSession(): WorkoutSessionEntity?

    @Query("SELECT * FROM workout_sessions WHERE sessionId = :sessionId LIMIT 1")
    fun observeSessionById(sessionId: Long): Flow<WorkoutSessionEntity?>

    @Query("SELECT * FROM workout_sessions WHERE sessionId = :sessionId LIMIT 1")
    suspend fun getSessionById(sessionId: Long): WorkoutSessionEntity?

    @Query("SELECT * FROM workout_sessions WHERE isSubmitted = 1 ORDER BY completedAtEpochMillis DESC, sessionId DESC")
    fun observeCompletedSessions(): Flow<List<WorkoutSessionEntity>>

    @Query("SELECT * FROM workout_sessions WHERE isSubmitted = 1 ORDER BY completedAtEpochMillis DESC, sessionId DESC")
    suspend fun getCompletedSessions(): List<WorkoutSessionEntity>

    @Query("SELECT * FROM workout_sessions ORDER BY startedAtEpochMillis ASC")
    suspend fun getAllWorkoutSessions(): List<WorkoutSessionEntity>

    @Query("SELECT * FROM workout_sessions WHERE isSubmitted = 1 AND epochDay = :epochDay")
    suspend fun getCompletedSessionsForDay(epochDay: Long): List<WorkoutSessionEntity>

    @Query("SELECT * FROM workout_sessions WHERE isSubmitted = 1 AND epochDay BETWEEN :startEpochDay AND :endEpochDay ORDER BY epochDay ASC")
    suspend fun getCompletedSessionsInRange(startEpochDay: Long, endEpochDay: Long): List<WorkoutSessionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkoutSession(session: WorkoutSessionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkoutSessions(sessions: List<WorkoutSessionEntity>)

    @Update
    suspend fun updateWorkoutSession(session: WorkoutSessionEntity)

    @Upsert
    suspend fun upsertWorkoutSession(session: WorkoutSessionEntity): Long

    @Query("DELETE FROM workout_sessions WHERE sessionId = :sessionId")
    suspend fun deleteWorkoutSession(sessionId: Long)

    @Query("SELECT * FROM logged_sets WHERE sessionId = :sessionId ORDER BY setId ASC, setNumber ASC")
    fun observeSetsForSession(sessionId: Long): Flow<List<LoggedSetEntity>>

    @Query("SELECT * FROM logged_sets WHERE sessionId = :sessionId ORDER BY setId ASC, setNumber ASC")
    suspend fun getSetsForSession(sessionId: Long): List<LoggedSetEntity>

    @Query("SELECT * FROM logged_sets ORDER BY sessionId ASC, setNumber ASC")
    suspend fun getAllLoggedSets(): List<LoggedSetEntity>

    @Query(
        """
        SELECT ls.* FROM logged_sets ls
        INNER JOIN workout_sessions ws ON ls.sessionId = ws.sessionId
        WHERE ws.isSubmitted = 1
          AND ls.isCompleted = 1
          AND (ls.exerciseId = :exerciseId OR LOWER(ls.exerciseName) = LOWER(:exerciseName))
          AND ls.sessionId != :excludeSessionId
        ORDER BY ws.completedAtEpochMillis DESC, ls.setNumber ASC
        """
    )
    suspend fun getHistoricalCompletedSetsForExercise(
        exerciseId: String,
        exerciseName: String,
        excludeSessionId: Long = -1L
    ): List<LoggedSetEntity>

    @Query(
        """
        SELECT ls.* FROM logged_sets ls
        INNER JOIN workout_sessions ws ON ls.sessionId = ws.sessionId
        WHERE ws.isSubmitted = 1
          AND ls.isCompleted = 1
        ORDER BY ws.completedAtEpochMillis ASC, ls.setNumber ASC
        """
    )
    suspend fun getAllSubmittedCompletedSets(): List<LoggedSetEntity>

    @Query(
        """
        SELECT ls.* FROM logged_sets ls
        INNER JOIN workout_sessions ws ON ls.sessionId = ws.sessionId
        WHERE ws.isSubmitted = 1
          AND ls.isCompleted = 1
          AND (ls.exerciseId = :exerciseId OR LOWER(ls.exerciseName) = LOWER(:exerciseName))
          AND ls.sessionId = (
              SELECT ws2.sessionId FROM workout_sessions ws2
              INNER JOIN logged_sets ls2 ON ls2.sessionId = ws2.sessionId
              WHERE ws2.isSubmitted = 1
                AND ls2.isCompleted = 1
                AND (ls2.exerciseId = :exerciseId OR LOWER(ls2.exerciseName) = LOWER(:exerciseName))
                AND ws2.sessionId != :excludeSessionId
              ORDER BY ws2.completedAtEpochMillis DESC, ws2.sessionId DESC
              LIMIT 1
          )
        ORDER BY ls.setNumber ASC
        """
    )
    suspend fun getLastSessionSetsForExercise(
        exerciseId: String,
        exerciseName: String,
        excludeSessionId: Long = -1L
    ): List<LoggedSetEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLoggedSet(set: LoggedSetEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLoggedSets(sets: List<LoggedSetEntity>): List<Long>

    @Update
    suspend fun updateLoggedSet(set: LoggedSetEntity)

    @Upsert
    suspend fun upsertLoggedSet(set: LoggedSetEntity): Long

    @Query("DELETE FROM logged_sets WHERE setId = :setId")
    suspend fun deleteLoggedSet(setId: Long)

    @Query("DELETE FROM logged_sets WHERE sessionId = :sessionId")
    suspend fun deleteSetsForSession(sessionId: Long)

    // =========================================================================
    // 4. MuscleRecoveryEntity
    // =========================================================================

    @Query("SELECT * FROM muscle_recovery")
    fun observeAllMuscleRecoveries(): Flow<List<MuscleRecoveryEntity>>

    @Query("SELECT * FROM muscle_recovery")
    suspend fun getAllMuscleRecoveries(): List<MuscleRecoveryEntity>

    @Upsert
    suspend fun upsertMuscleRecovery(recovery: MuscleRecoveryEntity)

    @Upsert
    suspend fun upsertMuscleRecoveries(recoveries: List<MuscleRecoveryEntity>)

    // =========================================================================
    // 5. VaultFoodItemEntity
    // =========================================================================

    @Query("SELECT * FROM vault_food_items ORDER BY isPinned DESC, name ASC")
    fun observeAllVaultFoodItems(): Flow<List<VaultFoodItemEntity>>

    @Query("SELECT * FROM vault_food_items WHERE category = :category ORDER BY isPinned DESC, name ASC")
    fun observeVaultFoodItemsByCategory(category: FoodCategory): Flow<List<VaultFoodItemEntity>>

    @Query("SELECT * FROM vault_food_items ORDER BY isPinned DESC, name ASC")
    suspend fun getAllVaultFoodItems(): List<VaultFoodItemEntity>

    @Query("SELECT COUNT(*) FROM vault_food_items")
    suspend fun getVaultFoodCount(): Int

    @Upsert
    suspend fun upsertVaultFoodItem(item: VaultFoodItemEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVaultFoodItems(items: List<VaultFoodItemEntity>)

    @Query("DELETE FROM vault_food_items WHERE foodId = :foodId")
    suspend fun deleteVaultFoodItem(foodId: Long)

    // =========================================================================
    // 6. MealLogEntity
    // =========================================================================

    @Query("SELECT * FROM meal_logs WHERE epochDay = :epochDay ORDER BY loggedAtEpochMillis DESC")
    fun observeMealLogsForDay(epochDay: Long): Flow<List<MealLogEntity>>

    @Query("SELECT * FROM meal_logs WHERE epochDay = :epochDay ORDER BY loggedAtEpochMillis DESC")
    suspend fun getMealLogsForDay(epochDay: Long): List<MealLogEntity>

    @Query("SELECT * FROM meal_logs WHERE epochDay BETWEEN :startEpochDay AND :endEpochDay ORDER BY epochDay ASC, loggedAtEpochMillis ASC")
    fun observeMealLogsInRange(startEpochDay: Long, endEpochDay: Long): Flow<List<MealLogEntity>>

    @Query("SELECT * FROM meal_logs WHERE epochDay BETWEEN :startEpochDay AND :endEpochDay ORDER BY epochDay ASC, loggedAtEpochMillis ASC")
    suspend fun getMealLogsInRange(startEpochDay: Long, endEpochDay: Long): List<MealLogEntity>

    @Query("SELECT * FROM meal_logs ORDER BY loggedAtEpochMillis ASC")
    suspend fun getAllMealLogs(): List<MealLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMealLog(log: MealLogEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMealLogs(logs: List<MealLogEntity>): List<Long>

    @Query("DELETE FROM meal_logs WHERE logId = :logId")
    suspend fun deleteMealLog(logId: Long)

    // =========================================================================
    // 7. BodyWeightLogEntity
    // =========================================================================

    @Query("SELECT * FROM body_weight_logs WHERE epochDay BETWEEN :startEpochDay AND :endEpochDay ORDER BY epochDay ASC")
    fun observeWeightLogsInRange(startEpochDay: Long, endEpochDay: Long): Flow<List<BodyWeightLogEntity>>

    @Query("SELECT * FROM body_weight_logs WHERE epochDay BETWEEN :startEpochDay AND :endEpochDay ORDER BY epochDay ASC")
    suspend fun getWeightLogsInRange(startEpochDay: Long, endEpochDay: Long): List<BodyWeightLogEntity>

    @Query("SELECT * FROM body_weight_logs ORDER BY epochDay DESC")
    fun observeAllWeightLogs(): Flow<List<BodyWeightLogEntity>>

    @Query("SELECT * FROM body_weight_logs ORDER BY epochDay ASC")
    suspend fun getAllWeightLogs(): List<BodyWeightLogEntity>

    @Upsert
    suspend fun upsertWeightLog(log: BodyWeightLogEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWeightLogs(logs: List<BodyWeightLogEntity>)

    // =========================================================================
    // 8. ChatMessageEntity
    // =========================================================================

    @Query("SELECT * FROM chat_messages ORDER BY timestampEpochMillis ASC, messageId ASC")
    fun observeAllChatMessages(): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages ORDER BY timestampEpochMillis ASC, messageId ASC")
    suspend fun getAllChatMessages(): List<ChatMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChatMessage(message: ChatMessageEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChatMessages(messages: List<ChatMessageEntity>)

    @Update
    suspend fun updateChatMessage(message: ChatMessageEntity)

    @Query("UPDATE chat_messages SET isPlanApplied = 1 WHERE messageId = :messageId")
    suspend fun markPlanApplied(messageId: Long)

    @Query("DELETE FROM chat_messages")
    suspend fun clearAllChatMessages()

    // =========================================================================
    // 9. Full Database Snapshot Export / Restore Helpers
    // =========================================================================

    @Query("DELETE FROM user_profile")
    suspend fun clearUserProfileTable()

    @Query("DELETE FROM workout_plans")
    suspend fun clearWorkoutPlansTable()

    @Query("DELETE FROM planned_days")
    suspend fun clearPlannedDaysTable()

    @Query("DELETE FROM planned_exercises")
    suspend fun clearPlannedExercisesTable()

    @Query("DELETE FROM workout_sessions")
    suspend fun clearWorkoutSessionsTable()

    @Query("DELETE FROM logged_sets")
    suspend fun clearLoggedSetsTable()

    @Query("DELETE FROM muscle_recovery")
    suspend fun clearMuscleRecoveryTable()

    @Query("DELETE FROM vault_food_items")
    suspend fun clearVaultFoodItemsTable()

    @Query("DELETE FROM meal_logs")
    suspend fun clearMealLogsTable()

    @Query("DELETE FROM body_weight_logs")
    suspend fun clearBodyWeightLogsTable()

    @Transaction
    suspend fun exportFullBackupPayload(): FullAppBackupPayload {
        return FullAppBackupPayload(
            exportedAtEpochMillis = System.currentTimeMillis(),
            userProfile = getUserProfile(),
            workoutPlans = getAllWorkoutPlans(),
            plannedDays = getAllPlannedDays(),
            plannedExercises = getAllPlannedExercises(),
            workoutSessions = getAllWorkoutSessions(),
            loggedSets = getAllLoggedSets(),
            muscleRecovery = getAllMuscleRecoveries(),
            vaultFoodItems = getAllVaultFoodItems(),
            mealLogs = getAllMealLogs(),
            bodyWeightLogs = getAllWeightLogs(),
            chatMessages = getAllChatMessages()
        )
    }

    @Transaction
    suspend fun restoreFullBackupPayload(payload: FullAppBackupPayload) {
        clearUserProfileTable()
        clearWorkoutPlansTable()
        clearPlannedDaysTable()
        clearPlannedExercisesTable()
        clearWorkoutSessionsTable()
        clearLoggedSetsTable()
        clearMuscleRecoveryTable()
        clearVaultFoodItemsTable()
        clearMealLogsTable()
        clearBodyWeightLogsTable()
        clearAllChatMessages()

        payload.userProfile?.let { upsertUserProfile(it) }
        if (payload.workoutPlans.isNotEmpty()) insertWorkoutPlans(payload.workoutPlans)
        if (payload.plannedDays.isNotEmpty()) insertPlannedDays(payload.plannedDays)
        if (payload.plannedExercises.isNotEmpty()) insertPlannedExercises(payload.plannedExercises)
        if (payload.workoutSessions.isNotEmpty()) insertWorkoutSessions(payload.workoutSessions)
        if (payload.loggedSets.isNotEmpty()) insertLoggedSets(payload.loggedSets)
        if (payload.muscleRecovery.isNotEmpty()) upsertMuscleRecoveries(payload.muscleRecovery)
        if (payload.vaultFoodItems.isNotEmpty()) insertVaultFoodItems(payload.vaultFoodItems)
        if (payload.mealLogs.isNotEmpty()) insertMealLogs(payload.mealLogs)
        if (payload.bodyWeightLogs.isNotEmpty()) insertWeightLogs(payload.bodyWeightLogs)
        if (payload.chatMessages.isNotEmpty()) insertChatMessages(payload.chatMessages)
    }
}
