package com.forgequest.ai.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.forgequest.ai.domain.model.ExerciseCategory
import com.forgequest.ai.domain.model.FlameHealthState
import com.forgequest.ai.domain.model.FoodCategory
import com.forgequest.ai.domain.model.MuscleGroup
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey val id: Int = 1,
    val totalXp: Long = 0L,
    val level: Int = 1,
    val rankCode: String = "E-Rank",
    val currentWorkoutStreakDays: Int = 0,
    val longestWorkoutStreakDays: Int = 0,
    val consecutiveMissedWorkoutDays: Int = 0,
    val flameHealthState: FlameHealthState = FlameHealthState.INFERNO,
    val lastStreakEvaluationEpochDay: Long? = null,
    val lastRestCheckInEpochDay: Long? = null,
    val currentNutritionStreakDays: Int = 0,
    val lastProteinGoalHitEpochDay: Long? = null,
    val currentWeightKg: Float = 72.0f
)

@Serializable
@Entity(tableName = "workout_plans")
data class WorkoutPlanEntity(
    @PrimaryKey(autoGenerate = true) val planId: Long = 0L,
    val name: String,
    val description: String,
    val isActive: Boolean = true,
    val sourceTag: String = "DEFAULT",
    val updatedAtEpochMillis: Long = System.currentTimeMillis()
)

@Serializable
@Entity(
    tableName = "planned_days",
    indices = [Index(value = ["planId", "dayOfWeek"])]
)
data class PlannedDayEntity(
    @PrimaryKey(autoGenerate = true) val dayId: Long = 0L,
    val planId: Long,
    val dayOfWeek: Int, // 1 = Monday .. 7 = Sunday
    val title: String,
    val isRestDay: Boolean
)

@Serializable
@Entity(
    tableName = "planned_exercises",
    indices = [Index(value = ["dayId"])]
)
data class PlannedExerciseEntity(
    @PrimaryKey val exerciseId: String,
    val dayId: Long,
    val orderIndex: Int,
    val name: String,
    val category: ExerciseCategory,
    val targetSets: Int,
    val targetRepsOrSeconds: Int,
    val restDurationSeconds: Int = 90,
    val primaryMuscles: List<MuscleGroup>,
    val secondaryMuscles: List<MuscleGroup> = emptyList(),
    val videoUrl: String? = null,
    val videoSearchFallback: String
)

@Serializable
@Entity(
    tableName = "workout_sessions",
    indices = [Index(value = ["epochDay"]), Index(value = ["isSubmitted"])]
)
data class WorkoutSessionEntity(
    @PrimaryKey(autoGenerate = true) val sessionId: Long = 0L,
    val planId: Long? = null,
    val dayId: Long? = null,
    val dayTitle: String,
    val startedAtEpochMillis: Long,
    val completedAtEpochMillis: Long? = null,
    val epochDay: Long,
    val isSubmitted: Boolean = false,
    val earnedXp: Int = 0,
    val prsBrokenCount: Int = 0,
    val reignitedFlame: Boolean = false
)

@Serializable
@Entity(
    tableName = "logged_sets",
    indices = [Index(value = ["sessionId"]), Index(value = ["exerciseId"])]
)
data class LoggedSetEntity(
    @PrimaryKey(autoGenerate = true) val setId: Long = 0L,
    val sessionId: Long,
    val exerciseId: String,
    val exerciseName: String,
    val category: ExerciseCategory,
    val setNumber: Int,
    val reps: Int,
    val weightKg: Float = 0f,
    val holdSeconds: Int = 0,
    val isCompleted: Boolean = false,
    val completedAtEpochMillis: Long = 0L,
    val primaryMuscles: List<MuscleGroup>,
    val secondaryMuscles: List<MuscleGroup> = emptyList()
)

@Serializable
@Entity(tableName = "muscle_recovery")
data class MuscleRecoveryEntity(
    @PrimaryKey val muscleGroup: MuscleGroup,
    val lastTrainedEpochMillis: Long,
    val lastIntensityFactor: Float = 1.0f
)

@Serializable
@Entity(
    tableName = "vault_food_items",
    indices = [Index(value = ["category", "isPinned"])]
)
data class VaultFoodItemEntity(
    @PrimaryKey(autoGenerate = true) val foodId: Long = 0L,
    val name: String,
    val category: FoodCategory,
    val caloriesPerServing: Int,
    val proteinGramsPerServing: Float,
    val servingDescription: String = "1 serving",
    val photoUri: String? = null,
    val isPinned: Boolean = false,
    val createdAtEpochMillis: Long = System.currentTimeMillis()
)

@Serializable
@Entity(
    tableName = "meal_logs",
    indices = [Index(value = ["epochDay"])]
)
data class MealLogEntity(
    @PrimaryKey(autoGenerate = true) val logId: Long = 0L,
    val epochDay: Long,
    val loggedAtEpochMillis: Long,
    val category: FoodCategory,
    val itemName: String,
    val multiplier: Float = 1.0f,
    val totalCalories: Int,
    val totalProteinGrams: Float,
    val photoUri: String? = null,
    val handScaleUsed: Boolean = false
)

@Serializable
@Entity(tableName = "body_weight_logs")
data class BodyWeightLogEntity(
    @PrimaryKey val epochDay: Long,
    val weightKg: Float,
    val loggedAtEpochMillis: Long
)

@Serializable
@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val messageId: Long = 0L,
    val role: String, // "user" or "assistant"
    val content: String,
    val modelUsed: String,
    val proposedPlanJson: String? = null,
    val isPlanApplied: Boolean = false,
    val timestampEpochMillis: Long = System.currentTimeMillis()
)

@Serializable
data class FullAppBackupPayload(
    val exportedAtEpochMillis: Long = System.currentTimeMillis(),
    val userProfile: UserProfileEntity? = null,
    val workoutPlans: List<WorkoutPlanEntity> = emptyList(),
    val plannedDays: List<PlannedDayEntity> = emptyList(),
    val plannedExercises: List<PlannedExerciseEntity> = emptyList(),
    val workoutSessions: List<WorkoutSessionEntity> = emptyList(),
    val loggedSets: List<LoggedSetEntity> = emptyList(),
    val muscleRecovery: List<MuscleRecoveryEntity> = emptyList(),
    val vaultFoodItems: List<VaultFoodItemEntity> = emptyList(),
    val mealLogs: List<MealLogEntity> = emptyList(),
    val bodyWeightLogs: List<BodyWeightLogEntity> = emptyList(),
    val chatMessages: List<ChatMessageEntity> = emptyList()
)
