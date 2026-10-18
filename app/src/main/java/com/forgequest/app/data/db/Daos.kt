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

    @Query("DELETE FROM sets WHERE id = :id")
    suspend fun deleteSet(id: Long)

    @Query("DELETE FROM sets WHERE sessionId = :sessionId")
    suspend fun deleteSetsOf(sessionId: Long)
}

@Dao
interface FoodDao {
    @Query("SELECT * FROM foods ORDER BY pinned DESC, name COLLATE NOCASE")
    fun observeFoods(): Flow<List<FoodEntity>>

    @Upsert
    suspend fun upsertFood(food: FoodEntity): Long

    @Query("DELETE FROM foods WHERE id = :id")
    suspend fun deleteFood(id: Long)

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

    @Transaction
    suspend fun replaceAll(
        exercises: List<ExerciseEntity>, plans: List<PlanEntity>, days: List<PlanDayEntity>, items: List<PlanItemEntity>,
        sessions: List<SessionEntity>, sets: List<SetEntity>, foods: List<FoodEntity>, meals: List<MealEntity>,
        weights: List<WeightEntity>, chat: List<ChatEntity>, badges: List<BadgeEntity>,
    ) {
        clearExercises(); clearPlans(); clearDays(); clearItems(); clearSessions(); clearSets()
        clearFoods(); clearMeals(); clearWeights(); clearChat(); clearBadges()
        putExercises(exercises); putPlans(plans); putDays(days); putItems(items); putSessions(sessions); putSets(sets)
        putFoods(foods); putMeals(meals); putWeights(weights); putChat(chat); putBadges(badges)
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
}
