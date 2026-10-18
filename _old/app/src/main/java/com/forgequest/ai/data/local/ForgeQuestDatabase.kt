package com.forgequest.ai.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.forgequest.ai.data.local.dao.ForgeQuestDao
import com.forgequest.ai.data.local.entity.BodyWeightLogEntity
import com.forgequest.ai.data.local.entity.ChatMessageEntity
import com.forgequest.ai.data.local.entity.LoggedSetEntity
import com.forgequest.ai.data.local.entity.MealLogEntity
import com.forgequest.ai.data.local.entity.MuscleRecoveryEntity
import com.forgequest.ai.data.local.entity.PlannedDayEntity
import com.forgequest.ai.data.local.entity.PlannedExerciseEntity
import com.forgequest.ai.data.local.entity.UserProfileEntity
import com.forgequest.ai.data.local.entity.VaultFoodItemEntity
import com.forgequest.ai.data.local.entity.WorkoutPlanEntity
import com.forgequest.ai.data.local.entity.WorkoutSessionEntity
import com.forgequest.ai.domain.model.AiModelTier
import com.forgequest.ai.domain.model.ExerciseCategory
import com.forgequest.ai.domain.model.FlameHealthState
import com.forgequest.ai.domain.model.FoodCategory
import com.forgequest.ai.domain.model.MuscleGroup
import com.forgequest.ai.domain.model.PlannedDayPayload
import com.forgequest.ai.domain.model.PlannedExercisePayload
import com.forgequest.ai.domain.model.WorkoutPlanPayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

@Database(
    entities = [
        UserProfileEntity::class,
        WorkoutPlanEntity::class,
        PlannedDayEntity::class,
        PlannedExerciseEntity::class,
        WorkoutSessionEntity::class,
        LoggedSetEntity::class,
        MuscleRecoveryEntity::class,
        VaultFoodItemEntity::class,
        MealLogEntity::class,
        BodyWeightLogEntity::class,
        ChatMessageEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(RoomConverters::class)
abstract class ForgeQuestDatabase : RoomDatabase() {

    abstract fun forgeQuestDao(): ForgeQuestDao

    suspend fun seedInitialDataIfNeeded() = withContext(Dispatchers.IO) {
        val dao = forgeQuestDao()
        val todayEpochDay = LocalDate.now().toEpochDay()
        val nowMillis = System.currentTimeMillis()

        // 1. Seed User Profile if missing
        if (dao.getUserProfile() == null) {
            dao.upsertUserProfile(
                UserProfileEntity(
                    id = 1,
                    totalXp = 150L,
                    level = 1,
                    rankCode = "E-Rank",
                    currentWorkoutStreakDays = 1,
                    longestWorkoutStreakDays = 1,
                    consecutiveMissedWorkoutDays = 0,
                    flameHealthState = FlameHealthState.INFERNO,
                    lastStreakEvaluationEpochDay = todayEpochDay,
                    lastRestCheckInEpochDay = null,
                    currentNutritionStreakDays = 0,
                    lastProteinGoalHitEpochDay = null,
                    currentWeightKg = 72.0f
                )
            )
            dao.upsertWeightLog(
                BodyWeightLogEntity(
                    epochDay = todayEpochDay,
                    weightKg = 72.0f,
                    loggedAtEpochMillis = nowMillis
                )
            )
        }

        // 2. Seed Starter Food Vault if empty
        if (dao.getVaultFoodCount() == 0) {
            val starterItems = listOf(
                // Exact 6 required Starter Food Vault items (Breakfast & Drinks)
                VaultFoodItemEntity(
                    name = "Boiled / Fried Egg",
                    category = FoodCategory.BREAKFAST,
                    caloriesPerServing = 72,
                    proteinGramsPerServing = 6.3f,
                    servingDescription = "1 large",
                    photoUri = null,
                    isPinned = true,
                    createdAtEpochMillis = nowMillis
                ),
                VaultFoodItemEntity(
                    name = "Paratha Flatbread",
                    category = FoodCategory.BREAKFAST,
                    caloriesPerServing = 260,
                    proteinGramsPerServing = 5.0f,
                    servingDescription = "1 piece",
                    photoUri = null,
                    isPinned = true,
                    createdAtEpochMillis = nowMillis
                ),
                VaultFoodItemEntity(
                    name = "Ghee / Cooking Fat",
                    category = FoodCategory.BREAKFAST,
                    caloriesPerServing = 45,
                    proteinGramsPerServing = 0.0f,
                    servingDescription = "1 tsp",
                    photoUri = null,
                    isPinned = false,
                    createdAtEpochMillis = nowMillis
                ),
                VaultFoodItemEntity(
                    name = "Cheese Portion",
                    category = FoodCategory.BREAKFAST,
                    caloriesPerServing = 95,
                    proteinGramsPerServing = 6.0f,
                    servingDescription = "30g",
                    photoUri = null,
                    isPinned = false,
                    createdAtEpochMillis = nowMillis
                ),
                VaultFoodItemEntity(
                    name = "Laban",
                    category = FoodCategory.DRINKS,
                    caloriesPerServing = 110,
                    proteinGramsPerServing = 7.5f,
                    servingDescription = "1 cup 250ml",
                    photoUri = null,
                    isPinned = true,
                    createdAtEpochMillis = nowMillis
                ),
                VaultFoodItemEntity(
                    name = "Cappuccino / Mocha (Davidoff + Lactose-Free Milk)",
                    category = FoodCategory.DRINKS,
                    caloriesPerServing = 90,
                    proteinGramsPerServing = 8.0f,
                    servingDescription = "1 mug",
                    photoUri = null,
                    isPinned = true,
                    createdAtEpochMillis = nowMillis
                ),
                // Convenient Lunch / Dinner / Snacks starter cards
                VaultFoodItemEntity(
                    name = "Grilled Chicken Mandi / Kabsa Breast",
                    category = FoodCategory.LUNCH,
                    caloriesPerServing = 480,
                    proteinGramsPerServing = 42.0f,
                    servingDescription = "1 plate (220g chicken + rice)",
                    photoUri = null,
                    isPinned = true,
                    createdAtEpochMillis = nowMillis
                ),
                VaultFoodItemEntity(
                    name = "Tuna & Chickpea Power Bowl",
                    category = FoodCategory.LUNCH,
                    caloriesPerServing = 340,
                    proteinGramsPerServing = 34.0f,
                    servingDescription = "1 bowl",
                    photoUri = null,
                    isPinned = false,
                    createdAtEpochMillis = nowMillis
                ),
                VaultFoodItemEntity(
                    name = "Grilled Shish Tawook Skewers",
                    category = FoodCategory.DINNER,
                    caloriesPerServing = 390,
                    proteinGramsPerServing = 38.0f,
                    servingDescription = "2 skewers + garlic toum",
                    photoUri = null,
                    isPinned = true,
                    createdAtEpochMillis = nowMillis
                ),
                VaultFoodItemEntity(
                    name = "Seared Salmon & Roasted Potato",
                    category = FoodCategory.DINNER,
                    caloriesPerServing = 460,
                    proteinGramsPerServing = 36.0f,
                    servingDescription = "180g fillet",
                    photoUri = null,
                    isPinned = false,
                    createdAtEpochMillis = nowMillis
                ),
                VaultFoodItemEntity(
                    name = "Greek Yogurt + Mixed Nuts",
                    category = FoodCategory.SNACKS,
                    caloriesPerServing = 210,
                    proteinGramsPerServing = 18.0f,
                    servingDescription = "170g cup",
                    photoUri = null,
                    isPinned = true,
                    createdAtEpochMillis = nowMillis
                ),
                VaultFoodItemEntity(
                    name = "Whey Isolate Shake (Water)",
                    category = FoodCategory.DRINKS,
                    caloriesPerServing = 120,
                    proteinGramsPerServing = 25.0f,
                    servingDescription = "1 scoop (30g)",
                    photoUri = null,
                    isPinned = false,
                    createdAtEpochMillis = nowMillis
                )
            )
            dao.insertVaultFoodItems(starterItems)
        }

        // 3. Seed Default 7-Day Calisthenics & Weighted Workout Plan if none active
        if (dao.getActivePlan() == null) {
            dao.replaceActiveWorkoutPlanFromPayload(
                payload = buildDefaultHybridPlanPayload(),
                sourceTag = "DEFAULT"
            )
        }

        // 4. Seed Welcoming AI Coach Message if empty
        if (dao.getAllChatMessages().isEmpty()) {
            dao.insertChatMessage(
                ChatMessageEntity(
                    role = "assistant",
                    content = "Hi! I'm your personal fitness and nutrition coach. Ask me to build a workout plan, adjust your exercises, or review your weekly nutrition goals.",
                    modelUsed = AiModelTier.GEMINI_3_8_FLASH.modelId,
                    proposedPlanJson = null,
                    isPlanApplied = false,
                    timestampEpochMillis = nowMillis
                )
            )
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: ForgeQuestDatabase? = null

        fun getInstance(context: Context): ForgeQuestDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    ForgeQuestDatabase::class.java,
                    "forgequest_database.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }

        fun buildDefaultHybridPlanPayload(): WorkoutPlanPayload {
            return WorkoutPlanPayload(
                planName = "ForgeQuest Hybrid Calisthenics & Iron",
                description = "7-Day Calisthenics Skill, Weighted Compound & Core Hypertrophy Split",
                days = listOf(
                    PlannedDayPayload(
                        dayOfWeek = 1,
                        title = "Day 1: Upper Push & Planche Foundations",
                        isRestDay = false,
                        exercises = listOf(
                            PlannedExercisePayload(
                                exerciseId = "pseudo_planche_pushup",
                                name = "Pseudo Planche Push-Ups",
                                category = ExerciseCategory.CALISTHENICS,
                                targetSets = 4,
                                targetRepsOrSeconds = 10,
                                restDurationSeconds = 90,
                                primaryMuscles = listOf(MuscleGroup.CHEST, MuscleGroup.FRONT_DELTS),
                                secondaryMuscles = listOf(MuscleGroup.TRICEPS, MuscleGroup.ABS_CORE)
                            ),
                            PlannedExercisePayload(
                                exerciseId = "weighted_parallel_dips",
                                name = "Weighted Parallel Bar Dips",
                                category = ExerciseCategory.WEIGHTED,
                                targetSets = 4,
                                targetRepsOrSeconds = 8,
                                restDurationSeconds = 120,
                                primaryMuscles = listOf(MuscleGroup.CHEST, MuscleGroup.TRICEPS),
                                secondaryMuscles = listOf(MuscleGroup.FRONT_DELTS)
                            ),
                            PlannedExercisePayload(
                                exerciseId = "pike_handstand_pushup",
                                name = "Elevated Pike Push-Ups",
                                category = ExerciseCategory.CALISTHENICS,
                                targetSets = 3,
                                targetRepsOrSeconds = 10,
                                restDurationSeconds = 90,
                                primaryMuscles = listOf(MuscleGroup.FRONT_DELTS, MuscleGroup.SIDE_DELTS),
                                secondaryMuscles = listOf(MuscleGroup.TRICEPS, MuscleGroup.UPPER_BACK_TRAPS)
                            ),
                            PlannedExercisePayload(
                                exerciseId = "hollow_body_hold",
                                name = "Hollow Body Isometric Hold",
                                category = ExerciseCategory.ISOMETRIC,
                                targetSets = 3,
                                targetRepsOrSeconds = 45,
                                restDurationSeconds = 60,
                                primaryMuscles = listOf(MuscleGroup.ABS_CORE),
                                secondaryMuscles = listOf(MuscleGroup.OBLIQUES)
                            )
                        )
                    ),
                    PlannedDayPayload(
                        dayOfWeek = 2,
                        title = "Day 2: Upper Pull & Front Lever Strength",
                        isRestDay = false,
                        exercises = listOf(
                            PlannedExercisePayload(
                                exerciseId = "pull_up_standard",
                                name = "Chest-to-Bar Pull-Ups",
                                category = ExerciseCategory.CALISTHENICS,
                                targetSets = 4,
                                targetRepsOrSeconds = 10,
                                restDurationSeconds = 90,
                                primaryMuscles = listOf(MuscleGroup.LATS, MuscleGroup.UPPER_BACK_TRAPS),
                                secondaryMuscles = listOf(MuscleGroup.BICEPS, MuscleGroup.FOREARMS)
                            ),
                            PlannedExercisePayload(
                                exerciseId = "tuck_front_lever_row",
                                name = "Tuck Front Lever Rows",
                                category = ExerciseCategory.CALISTHENICS,
                                targetSets = 3,
                                targetRepsOrSeconds = 8,
                                restDurationSeconds = 90,
                                primaryMuscles = listOf(MuscleGroup.LATS, MuscleGroup.REAR_DELTS),
                                secondaryMuscles = listOf(MuscleGroup.ABS_CORE, MuscleGroup.BICEPS)
                            ),
                            PlannedExercisePayload(
                                exerciseId = "incline_dumbbell_curl",
                                name = "Weighted Chin-Ups / Supinated Curls",
                                category = ExerciseCategory.WEIGHTED,
                                targetSets = 3,
                                targetRepsOrSeconds = 10,
                                restDurationSeconds = 75,
                                primaryMuscles = listOf(MuscleGroup.BICEPS, MuscleGroup.FOREARMS),
                                secondaryMuscles = listOf(MuscleGroup.LATS)
                            )
                        )
                    ),
                    PlannedDayPayload(
                        dayOfWeek = 3,
                        title = "Day 3: Lower Body Power & Pistol Squat Progression",
                        isRestDay = false,
                        exercises = listOf(
                            PlannedExercisePayload(
                                exerciseId = "pistol_squat_progression",
                                name = "Pistol Squat (Single Leg)",
                                category = ExerciseCategory.CALISTHENICS,
                                targetSets = 4,
                                targetRepsOrSeconds = 8,
                                restDurationSeconds = 90,
                                primaryMuscles = listOf(MuscleGroup.QUADS, MuscleGroup.GLUTES),
                                secondaryMuscles = listOf(MuscleGroup.HAMSTRINGS, MuscleGroup.CALVES)
                            ),
                            PlannedExercisePayload(
                                exerciseId = "romanian_deadlift_weighted",
                                name = "Weighted Romanian Deadlift",
                                category = ExerciseCategory.WEIGHTED,
                                targetSets = 4,
                                targetRepsOrSeconds = 10,
                                restDurationSeconds = 105,
                                primaryMuscles = listOf(MuscleGroup.HAMSTRINGS, MuscleGroup.GLUTES),
                                secondaryMuscles = listOf(MuscleGroup.LOWER_BACK, MuscleGroup.FOREARMS)
                            ),
                            PlannedExercisePayload(
                                exerciseId = "hanging_leg_raises",
                                name = "Strict Toes-to-Bar Leg Raises",
                                category = ExerciseCategory.CALISTHENICS,
                                targetSets = 3,
                                targetRepsOrSeconds = 12,
                                restDurationSeconds = 60,
                                primaryMuscles = listOf(MuscleGroup.ABS_CORE, MuscleGroup.OBLIQUES),
                                secondaryMuscles = listOf(MuscleGroup.FOREARMS)
                            )
                        )
                    ),
                    PlannedDayPayload(
                        dayOfWeek = 4,
                        title = "Day 4: Scheduled Rest & Scapular Mobility",
                        isRestDay = true,
                        exercises = emptyList()
                    ),
                    PlannedDayPayload(
                        dayOfWeek = 5,
                        title = "Day 5: Explosive Muscle-Up & Hypertrophy Upper",
                        isRestDay = false,
                        exercises = listOf(
                            PlannedExercisePayload(
                                exerciseId = "explosive_high_pullup",
                                name = "Explosive High Pull-Ups",
                                category = ExerciseCategory.CALISTHENICS,
                                targetSets = 4,
                                targetRepsOrSeconds = 6,
                                restDurationSeconds = 120,
                                primaryMuscles = listOf(MuscleGroup.LATS, MuscleGroup.UPPER_BACK_TRAPS),
                                secondaryMuscles = listOf(MuscleGroup.TRICEPS, MuscleGroup.BICEPS)
                            ),
                            PlannedExercisePayload(
                                exerciseId = "ring_archer_pushup",
                                name = "Archer Push-Ups",
                                category = ExerciseCategory.CALISTHENICS,
                                targetSets = 3,
                                targetRepsOrSeconds = 10,
                                restDurationSeconds = 90,
                                primaryMuscles = listOf(MuscleGroup.CHEST, MuscleGroup.FRONT_DELTS),
                                secondaryMuscles = listOf(MuscleGroup.TRICEPS)
                            ),
                            PlannedExercisePayload(
                                exerciseId = "l_sit_hold",
                                name = "Parallettes L-Sit Hold",
                                category = ExerciseCategory.ISOMETRIC,
                                targetSets = 4,
                                targetRepsOrSeconds = 30,
                                restDurationSeconds = 60,
                                primaryMuscles = listOf(MuscleGroup.ABS_CORE, MuscleGroup.QUADS),
                                secondaryMuscles = listOf(MuscleGroup.TRICEPS)
                            )
                        )
                    ),
                    PlannedDayPayload(
                        dayOfWeek = 6,
                        title = "Day 6: Posterior Chain, Calves & Grip Conditioning",
                        isRestDay = false,
                        exercises = listOf(
                            PlannedExercisePayload(
                                exerciseId = "bulgarian_split_squat",
                                name = "Weighted Bulgarian Split Squats",
                                category = ExerciseCategory.WEIGHTED,
                                targetSets = 3,
                                targetRepsOrSeconds = 10,
                                restDurationSeconds = 90,
                                primaryMuscles = listOf(MuscleGroup.QUADS, MuscleGroup.GLUTES),
                                secondaryMuscles = listOf(MuscleGroup.HAMSTRINGS)
                            ),
                            PlannedExercisePayload(
                                exerciseId = "nordic_hamstring_curl",
                                name = "Nordic Hamstring Curls",
                                category = ExerciseCategory.CALISTHENICS,
                                targetSets = 3,
                                targetRepsOrSeconds = 6,
                                restDurationSeconds = 90,
                                primaryMuscles = listOf(MuscleGroup.HAMSTRINGS),
                                secondaryMuscles = listOf(MuscleGroup.GLUTES, MuscleGroup.CALVES)
                            ),
                            PlannedExercisePayload(
                                exerciseId = "single_leg_calf_raise",
                                name = "Elevated Single-Leg Calf Raises",
                                category = ExerciseCategory.WEIGHTED,
                                targetSets = 4,
                                targetRepsOrSeconds = 15,
                                restDurationSeconds = 60,
                                primaryMuscles = listOf(MuscleGroup.CALVES),
                                secondaryMuscles = emptyList()
                            )
                        )
                    ),
                    PlannedDayPayload(
                        dayOfWeek = 7,
                        title = "Day 7: Full Rest & Sanctuary Recovery",
                        isRestDay = true,
                        exercises = emptyList()
                    )
                )
            )
        }
    }
}
