package com.forgequest.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseDao {
    @Query("SELECT * FROM exercises ORDER BY name")
    fun observeAll(): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercises")
    suspend fun all(): List<ExerciseEntity>

    @Query("SELECT * FROM exercises WHERE id = :id")
    suspend fun byId(id: String): ExerciseEntity?

    @Query("SELECT COUNT(*) FROM exercises")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(items: List<ExerciseEntity>)

    @Upsert
    suspend fun upsert(item: ExerciseEntity)

    @Upsert
    suspend fun upsertAll(items: List<ExerciseEntity>)

    @Query("DELETE FROM exercises WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT COUNT(*) FROM sets WHERE exerciseId = :id")
    suspend fun setCount(id: String): Int

    @Query("SELECT COUNT(*) FROM plan_items WHERE exerciseId = :id")
    suspend fun planItemCount(id: String): Int

    @Query("UPDATE sets SET exerciseId = :to WHERE exerciseId = :from")
    suspend fun repointSets(from: String, to: String)

    @Query("UPDATE plan_items SET exerciseId = :to WHERE exerciseId = :from")
    suspend fun repointPlanItems(from: String, to: String)
}

@Dao
interface PlanDao {
    @Query("SELECT * FROM plans WHERE active = 1 LIMIT 1")
    fun observeActive(): Flow<PlanEntity?>

    @Query("SELECT * FROM plans WHERE active = 1 LIMIT 1")
    suspend fun active(): PlanEntity?

    @Query("SELECT * FROM plans ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<PlanEntity>>

    @Query("SELECT * FROM plan_days WHERE planId = :planId ORDER BY dayOfWeek")
    fun observeDays(planId: Long): Flow<List<PlanDayEntity>>

    @Query("SELECT * FROM plan_days WHERE planId = :planId ORDER BY dayOfWeek")
    suspend fun days(planId: Long): List<PlanDayEntity>

    @Query("SELECT * FROM plan_days WHERE id = :id")
    suspend fun day(id: Long): PlanDayEntity?

    @Query("SELECT * FROM plan_items WHERE dayId = :dayId ORDER BY position")
    fun observeItems(dayId: Long): Flow<List<PlanItemEntity>>

    @Query("SELECT * FROM plan_items WHERE dayId = :dayId ORDER BY position")
    suspend fun items(dayId: Long): List<PlanItemEntity>

    @Query("SELECT pi.* FROM plan_items pi JOIN plan_days pd ON pi.dayId = pd.id WHERE pd.planId = :planId ORDER BY pd.dayOfWeek, pi.position")
    fun observeAllItems(planId: Long): Flow<List<PlanItemEntity>>

    @Insert
    suspend fun insertPlan(plan: PlanEntity): Long

    @Insert
    suspend fun insertDay(day: PlanDayEntity): Long

    @Insert
    suspend fun insertItems(items: List<PlanItemEntity>)

    @Insert
    suspend fun insertItem(item: PlanItemEntity): Long

    /** Puts a removed plan item back with its original id and position (undo). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restoreItem(item: PlanItemEntity)

    @Update
    suspend fun updateItem(item: PlanItemEntity)

    @Update
    suspend fun updateDay(day: PlanDayEntity)

    @Query("DELETE FROM plan_items WHERE id = :id")
    suspend fun deleteItem(id: Long)

    // Starter-plan texts follow the app's language (see WorkoutRepository.seedLibrary).
    @Query("UPDATE plans SET name = :to WHERE name = :from")
    suspend fun renamePlans(from: String, to: String)

    @Query("UPDATE plans SET description = :to WHERE description = :from")
    suspend fun redescribePlans(from: String, to: String)

    @Query("UPDATE plan_days SET title = :to WHERE title = :from")
    suspend fun retitleDays(from: String, to: String)

    @Query("UPDATE plan_days SET note = :to WHERE note = :from")
    suspend fun renoteDays(from: String, to: String)

    @Query("UPDATE plans SET active = 0")
    suspend fun deactivateAll()

    @Query("UPDATE plans SET active = 1 WHERE id = :id")
    suspend fun activate(id: Long)

    @Query("SELECT * FROM plans WHERE id = :id")
    suspend fun byId(id: Long): PlanEntity?

    @Query("DELETE FROM plan_items WHERE dayId IN (SELECT id FROM plan_days WHERE planId = :planId)")
    suspend fun deleteItemsOfPlan(planId: Long)

    @Query("DELETE FROM plan_days WHERE planId = :planId")
    suspend fun deleteDaysOfPlan(planId: Long)

    @Query("DELETE FROM plans WHERE id = :planId")
    suspend fun deletePlan(planId: Long)

    @Query("SELECT COUNT(*) FROM sessions WHERE planDayId IN (SELECT id FROM plan_days WHERE planId = :planId)")
    suspend fun sessionsUsingPlan(planId: Long): Int
}

@Dao
interface SessionDao {
    @Query("SELECT * FROM sessions WHERE finishedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    fun observeOpen(): Flow<SessionEntity?>

    @Query("SELECT * FROM sessions WHERE finishedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    suspend fun open(): SessionEntity?

    @Query("SELECT * FROM sessions WHERE finishedAt IS NOT NULL ORDER BY finishedAt DESC")
    fun observeFinished(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE finishedAt IS NOT NULL ORDER BY finishedAt DESC")
    suspend fun finished(): List<SessionEntity>

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun byId(id: Long): SessionEntity?

    @Query("SELECT * FROM sessions WHERE id = :id")
    fun observeById(id: Long): Flow<SessionEntity?>

    @Insert
    suspend fun insert(session: SessionEntity): Long

    @Update
    suspend fun update(session: SessionEntity)

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM sets WHERE sessionId = :sessionId ORDER BY itemPosition, setIndex")
    fun observeSets(sessionId: Long): Flow<List<SetEntity>>

    @Query("SELECT * FROM sets WHERE sessionId = :sessionId ORDER BY itemPosition, setIndex")
    suspend fun sets(sessionId: Long): List<SetEntity>

    @Query("SELECT s.* FROM sets s JOIN sessions ss ON s.sessionId = ss.id WHERE ss.finishedAt IS NOT NULL AND s.done = 1")
    fun observeAllDoneSets(): Flow<List<SetEntity>>

    @Query("SELECT s.* FROM sets s JOIN sessions ss ON s.sessionId = ss.id WHERE ss.finishedAt IS NOT NULL AND s.done = 1")
    suspend fun allDoneSets(): List<SetEntity>

    /** Most recent finished sets of one exercise, newest session first. Used to prefill. */
    @Query(
        "SELECT s.* FROM sets s JOIN sessions ss ON s.sessionId = ss.id " +
            "WHERE ss.finishedAt IS NOT NULL AND s.done = 1 AND s.exerciseId = :exerciseId " +
            "ORDER BY ss.finishedAt DESC, s.setIndex ASC LIMIT 20"
    )
    suspend fun recentDoneSets(exerciseId: String): List<SetEntity>

    /** Every finished set of one exercise, for personal-record comparison. */
    @Query(
        "SELECT s.* FROM sets s JOIN sessions ss ON s.sessionId = ss.id " +
            "WHERE ss.finishedAt IS NOT NULL AND s.done = 1 AND s.exerciseId = :exerciseId"
    )
    suspend fun recentDoneSetsAll(exerciseId: String): List<SetEntity>

    @Insert
    suspend fun insertSets(sets: List<SetEntity>)

    @Insert
    suspend fun insertSet(set: SetEntity): Long

    @Update
    suspend fun updateSet(set: SetEntity)

    @Update
    suspend fun updateSets(sets: List<SetEntity>)

    @Query("UPDATE sets SET done = 1 WHERE sessionId = :sessionId AND done = 0")
    suspend fun markAllSetsDone(sessionId: Long)

    @Query("DELETE FROM sets WHERE id = :id")
    suspend fun deleteSet(id: Long)

    @Query("DELETE FROM sets WHERE sessionId = :sessionId")
    suspend fun deleteSetsOf(sessionId: Long)

    @Query("DELETE FROM sets WHERE sessionId = :sessionId AND done = 0")
    suspend fun deleteUntickedSetsOf(sessionId: Long)

    @Query("DELETE FROM sets WHERE sessionId = :sessionId AND itemPosition = :itemPosition")
    suspend fun deleteSetsAtPosition(sessionId: Long, itemPosition: Int)
}

@Dao
interface FoodDao {
    @Query("SELECT * FROM foods ORDER BY pinned DESC, name COLLATE NOCASE")
    fun observeFoods(): Flow<List<FoodEntity>>

    @Upsert
    suspend fun upsertFood(food: FoodEntity): Long

    @Query("DELETE FROM foods WHERE id = :id")
    suspend fun deleteFood(id: Long)

    /** The latest of each one-off meal (quick log or scan) by name, newest first, for one-tap relogging. */
    @Query("SELECT * FROM meals WHERE id IN (SELECT MAX(id) FROM meals WHERE foodId IS NULL GROUP BY lower(name)) ORDER BY loggedAt DESC LIMIT 8")
    fun observeRecentOneOffs(): Flow<List<MealEntity>>

    @Query("SELECT * FROM meals WHERE epochDay = :day ORDER BY loggedAt")
    fun observeMeals(day: Long): Flow<List<MealEntity>>

    @Query("SELECT * FROM meals WHERE epochDay >= :fromDay ORDER BY loggedAt")
    fun observeMealsSince(fromDay: Long): Flow<List<MealEntity>>

    @Query("SELECT * FROM meals WHERE epochDay >= :fromDay ORDER BY loggedAt")
    suspend fun mealsSince(fromDay: Long): List<MealEntity>

    @Query("SELECT * FROM meals ORDER BY loggedAt")
    suspend fun allMeals(): List<MealEntity>

    @Query("SELECT COUNT(*) FROM meals")
    fun observeMealCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM meals WHERE photoPath IS NOT NULL AND foodId IS NULL")
    fun observeScannedCount(): Flow<Int>

    @Query("SELECT * FROM foods")
    suspend fun allFoods(): List<FoodEntity>

    @Insert
    suspend fun insertMeals(meals: List<MealEntity>)

    /** Puts a deleted meal back with its original id (undo). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restoreMeal(meal: MealEntity)

    @Query("DELETE FROM meals WHERE id = :id")
    suspend fun deleteMeal(id: Long)
}

@Dao
interface WeightDao {
    @Query("SELECT * FROM weights ORDER BY epochDay")
    fun observeAll(): Flow<List<WeightEntity>>

    @Query("SELECT * FROM weights ORDER BY epochDay")
    suspend fun all(): List<WeightEntity>

    @Upsert
    suspend fun upsert(weight: WeightEntity)
}

@Dao
interface ChatDao {
    @Query("SELECT * FROM chat ORDER BY createdAt")
    fun observeAll(): Flow<List<ChatEntity>>

    @Query("SELECT * FROM chat ORDER BY createdAt DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<ChatEntity>

    @Insert
    suspend fun insert(message: ChatEntity): Long

    @Update
    suspend fun update(message: ChatEntity)

    @Query("SELECT * FROM chat WHERE id = :id")
    suspend fun byId(id: Long): ChatEntity?

    @Query("DELETE FROM chat")
    suspend fun clear()
}

@Dao
interface BadgeDao {
    @Query("SELECT * FROM badges")
    fun observeAll(): Flow<List<BadgeEntity>>

    @Query("SELECT * FROM badges")
    suspend fun all(): List<BadgeEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(badges: List<BadgeEntity>)

    @Query("UPDATE badges SET seen = 1")
    suspend fun markAllSeen()
}

@Dao
interface BackupDao {
    @Query("SELECT * FROM exercises") suspend fun exercises(): List<ExerciseEntity>
    @Query("SELECT * FROM plans") suspend fun plans(): List<PlanEntity>
    @Query("SELECT * FROM plan_days") suspend fun planDays(): List<PlanDayEntity>
    @Query("SELECT * FROM plan_items") suspend fun planItems(): List<PlanItemEntity>
    @Query("SELECT * FROM sessions") suspend fun sessions(): List<SessionEntity>
    @Query("SELECT * FROM sets") suspend fun sets(): List<SetEntity>
    @Query("SELECT * FROM foods") suspend fun foods(): List<FoodEntity>
    @Query("SELECT * FROM meals") suspend fun meals(): List<MealEntity>
    @Query("SELECT * FROM weights") suspend fun weights(): List<WeightEntity>
    @Query("SELECT * FROM chat") suspend fun chat(): List<ChatEntity>
    @Query("SELECT * FROM badges") suspend fun badges(): List<BadgeEntity>
    @Query("SELECT * FROM day_notes") suspend fun dayNotes(): List<DayNoteEntity>
    @Query("SELECT * FROM readiness_days") suspend fun readinessDays(): List<ReadinessDayEntity>

    @Transaction
    suspend fun replaceAll(
        exercises: List<ExerciseEntity>, plans: List<PlanEntity>, days: List<PlanDayEntity>, items: List<PlanItemEntity>,
        sessions: List<SessionEntity>, sets: List<SetEntity>, foods: List<FoodEntity>, meals: List<MealEntity>,
        weights: List<WeightEntity>, chat: List<ChatEntity>, badges: List<BadgeEntity>, dayNotes: List<DayNoteEntity>,
        readinessDays: List<ReadinessDayEntity>,
    ) {
        clearExercises(); clearPlans(); clearDays(); clearItems(); clearSessions(); clearSets()
        clearFoods(); clearMeals(); clearWeights(); clearChat(); clearBadges(); clearDayNotes()
        putExercises(exercises); putPlans(plans); putDays(days); putItems(items); putSessions(sessions); putSets(sets)
        putFoods(foods); putMeals(meals); putWeights(weights); putChat(chat); putBadges(badges); putDayNotes(dayNotes)
        // Backups from before 2.8.0 have no readiness history; keep this phone's rather than wipe it.
        if (readinessDays.isNotEmpty()) { clearReadiness(); putReadiness(readinessDays) }
    }

    @Query("DELETE FROM exercises") suspend fun clearExercises()
    @Query("DELETE FROM plans") suspend fun clearPlans()
    @Query("DELETE FROM plan_days") suspend fun clearDays()
    @Query("DELETE FROM plan_items") suspend fun clearItems()
    @Query("DELETE FROM sessions") suspend fun clearSessions()
    @Query("DELETE FROM sets") suspend fun clearSets()
    @Query("DELETE FROM foods") suspend fun clearFoods()
    @Query("DELETE FROM meals") suspend fun clearMeals()
    @Query("DELETE FROM weights") suspend fun clearWeights()
    @Query("DELETE FROM chat") suspend fun clearChat()
    @Query("DELETE FROM badges") suspend fun clearBadges()
    @Query("DELETE FROM day_notes") suspend fun clearDayNotes()
    @Query("DELETE FROM readiness_days") suspend fun clearReadiness()
    @Insert suspend fun putReadiness(v: List<ReadinessDayEntity>)

    @Insert suspend fun putExercises(v: List<ExerciseEntity>)
    @Insert suspend fun putPlans(v: List<PlanEntity>)
    @Insert suspend fun putDays(v: List<PlanDayEntity>)
    @Insert suspend fun putItems(v: List<PlanItemEntity>)
    @Insert suspend fun putSessions(v: List<SessionEntity>)
    @Insert suspend fun putSets(v: List<SetEntity>)
    @Insert suspend fun putFoods(v: List<FoodEntity>)
    @Insert suspend fun putMeals(v: List<MealEntity>)
    @Insert suspend fun putWeights(v: List<WeightEntity>)
    @Insert suspend fun putChat(v: List<ChatEntity>)
    @Insert suspend fun putBadges(v: List<BadgeEntity>)
    @Insert suspend fun putDayNotes(v: List<DayNoteEntity>)
}

@Dao
interface DayNoteDao {
    @Query("SELECT * FROM day_notes WHERE epochDay = :day")
    suspend fun get(day: Long): DayNoteEntity?

    @Query("SELECT * FROM day_notes WHERE epochDay >= :from ORDER BY epochDay")
    fun observeSince(from: Long): Flow<List<DayNoteEntity>>

    @Query("SELECT * FROM day_notes WHERE epochDay >= :from ORDER BY epochDay")
    suspend fun since(from: Long): List<DayNoteEntity>

    @Upsert
    suspend fun upsert(note: DayNoteEntity)

    @Query("DELETE FROM day_notes WHERE epochDay = :day")
    suspend fun delete(day: Long)
}

@Dao
interface ReadinessDao {
    @Query("SELECT * FROM readiness_days WHERE epochDay = :day")
    suspend fun get(day: Long): ReadinessDayEntity?

    @Upsert
    suspend fun upsert(day: ReadinessDayEntity)

    @Query("SELECT * FROM readiness_days ORDER BY epochDay")
    suspend fun all(): List<ReadinessDayEntity>

    @Query("SELECT * FROM readiness_days ORDER BY epochDay")
    fun observeAll(): Flow<List<ReadinessDayEntity>>
}
