package com.forgequest.ai.domain.model

import kotlinx.serialization.Serializable
import kotlin.math.max

@Serializable
enum class AiModelTier(val modelId: String, val displayName: String, val subtitle: String) {
    GEMINI_3_8_FLASH(
        modelId = listOf("gemini", "3.8", "flash").joinToString("-"),
        displayName = "Fast",
        subtitle = "Quick vision & instant coaching"
    ),
    GEMINI_4_ARGON(
        modelId = listOf("gemini", "4", "argon").joinToString("-"),
        displayName = "Deep",
        subtitle = "Deep reasoning for complex meals & periodization"
    );

    companion object {
        fun fromModelId(id: String): AiModelTier =
            entries.find { it.modelId.equals(id, ignoreCase = true) } ?: GEMINI_3_8_FLASH
    }
}

@Serializable
enum class FoodCategory(val displayName: String) {
    BREAKFAST("Breakfast"),
    LUNCH("Lunch"),
    DINNER("Dinner"),
    SNACKS("Snacks"),
    DRINKS("Drinks")
}

@Serializable
enum class ExerciseCategory(val displayName: String) {
    CALISTHENICS("Calisthenics"),
    WEIGHTED("Weighted"),
    ISOMETRIC("Isometric")
}

@Serializable
enum class MuscleGroup(val displayName: String, val isFrontBody: Boolean) {
    CHEST("Chest", true),
    FRONT_DELTS("Front Delts", true),
    SIDE_DELTS("Side Delts", true),
    BICEPS("Biceps", true),
    FOREARMS("Forearms", true),
    ABS_CORE("Abs & Core", true),
    OBLIQUES("Obliques", true),
    QUADS("Quadriceps", true),
    CALVES("Calves", true),
    UPPER_BACK_TRAPS("Upper Back & Traps", false),
    LATS("Latissimus Dorsi", false),
    REAR_DELTS("Rear Delts", false),
    TRICEPS("Triceps", false),
    LOWER_BACK("Lower Back", false),
    GLUTES("Glutes", false),
    HAMSTRINGS("Hamstrings", false)
}

@Serializable
enum class FlameHealthState(val healthPercent: Int, val label: String, val description: String) {
    INFERNO(100, "Inferno · 100% health", "Workouts on schedule. Blazing at peak intensity."),
    FLICKERING(66, "Flickering · 66% health", "1 missed workout day. Streak counter preserved."),
    DYING_EMBER(33, "Dying ember · 33% health", "2 consecutive missed days. Complete a workout to reignite."),
    EXTINGUISHED(0, "Extinguished · 0% health", "3+ missed days. Streak reset to 0; Rank and XP preserved.")
}

@Serializable
enum class HunterRank(val rankCode: String, val title: String, val minLevel: Int, val maxLevel: Int) {
    E_RANK("E-Rank", "Starter Athlete", 1, 4),
    D_RANK("D-Rank", "Iron Vanguard", 5, 9),
    C_RANK("C-Rank", "Shadow Challenger", 10, 19),
    B_RANK("B-Rank", "Phantom Knight", 20, 34),
    A_RANK("A-Rank", "Sovereign Sentinel", 35, 49),
    S_RANK("S-Rank", "Monarch of Iron", 50, Int.MAX_VALUE);

    companion object {
        fun forLevel(level: Int): HunterRank =
            entries.firstOrNull { level in it.minLevel..it.maxLevel } ?: S_RANK

        fun calculateLevel(totalXp: Long): Int = max(1, 1 + (totalXp / 500L).toInt())

        fun xpProgressInCurrentLevel(totalXp: Long): Float =
            ((totalXp % 500L).coerceAtLeast(0L).toFloat() / 500f).coerceIn(0f, 1f)

        fun xpRemainingForNextLevel(totalXp: Long): Long =
            500L - (totalXp % 500L).coerceAtLeast(0L)
    }
}

@Serializable
enum class ExerciseAnalyticsMetric(val displayName: String) {
    MAX_REPS("Max Reps"),
    TOTAL_REPS("Total Reps"),
    TOTAL_VOLUME("Total Volume (Weight × Reps)"),
    MAX_HOLD_SECONDS("Max Hold (Seconds)")
}

@Serializable
enum class PortionMultiplier(val label: String, val factor: Float) {
    SMALL("Small 0.75x", 0.75f),
    NORMAL("Normal 1.0x", 1.0f),
    LARGE("Large 1.25x", 1.25f)
}

@Serializable
enum class HiddenFatAddition(val label: String, val extraCalories: Int) {
    LIGHT("Light (+0)", 0),
    NORMAL("Normal (+50)", 50),
    HEAVY("Heavy (+150)", 150)
}

@Serializable
data class AppSettings(
    val geminiApiKey: String = "",
    val activeModel: AiModelTier = AiModelTier.GEMINI_3_8_FLASH,
    val handLengthCm: Float = 18.5f,
    val palmWidthCm: Float = 8.5f,
    val dailyCalorieGoal: Int = 2100,
    val dailyProteinGoal: Int = 140,
    val enableDailyNutritionStreak: Boolean = false,
    val googleDriveAccountEmail: String? = null,
    val lastImportedDriveDocModifiedMillis: Long = 0L,
    val lastDriveSyncStatus: String = "Not synced yet"
)

@Serializable
data class VisionDetectedItem(
    val name: String,
    val estimatedGrams: Int,
    val calories: Int,
    val proteinGrams: Float,
    val selected: Boolean = true
)

@Serializable
data class VisionFoodAnalysisResult(
    val dishName: String,
    val confidence: Float = 0.92f,
    val handScaleDetected: Boolean = true,
    val calibrationNote: String = "Calibrated via flat hand reference",
    val detectedItems: List<VisionDetectedItem> = emptyList()
)

@Serializable
data class WorkoutPlanPayload(
    val planName: String,
    val description: String = "Custom AI Coach Schedule",
    val days: List<PlannedDayPayload>
)

@Serializable
data class PlannedDayPayload(
    val dayOfWeek: Int,
    val title: String,
    val isRestDay: Boolean,
    val exercises: List<PlannedExercisePayload> = emptyList()
)

@Serializable
data class PlannedExercisePayload(
    val exerciseId: String,
    val name: String,
    val category: ExerciseCategory = ExerciseCategory.CALISTHENICS,
    val targetSets: Int = 3,
    val targetRepsOrSeconds: Int = 10,
    val restDurationSeconds: Int = 90,
    val primaryMuscles: List<MuscleGroup> = listOf(MuscleGroup.CHEST),
    val secondaryMuscles: List<MuscleGroup> = emptyList(),
    val videoUrl: String? = null
)

@Serializable
enum class PrType(val badgeTitle: String) {
    MAX_SINGLE_SET_REPS("All-Time Max Reps (Single Set)"),
    MAX_EXERCISE_VOLUME("All-Time Exercise Volume"),
    QUEST_100_PERCENT_CLEAR("100% Target Quest Clear")
}

@Serializable
data class PrRecordCard(
    val exerciseId: String,
    val exerciseName: String,
    val prType: PrType,
    val newValueLabel: String,
    val previousValueLabel: String
)

@Serializable
data class QuestClearSummary(
    val sessionId: Long,
    val workoutTitle: String,
    val completedSetsCount: Int,
    val totalPlannedSetsCount: Int,
    val earnedXp: Int,
    val previousTotalXp: Long,
    val newTotalXp: Long,
    val previousLevel: Int,
    val newLevel: Int,
    val newRank: HunterRank,
    val prCards: List<PrRecordCard>,
    val reignitedFlame: Boolean,
    val previousFlameState: FlameHealthState,
    val newFlameState: FlameHealthState,
    val newStreakDays: Int,
    val ignitedMuscles: Map<MuscleGroup, Float>
)

@Serializable
data class DailyTrendPoint(
    val epochDay: Long,
    val dateLabel: String,
    val calories: Int,
    val proteinGrams: Float,
    val weightKg: Float
)

@Serializable
data class ExerciseHistoryPoint(
    val sessionId: Long,
    val epochDay: Long,
    val dateLabel: String,
    val maxRepsSingleSet: Int,
    val totalReps: Int,
    val totalVolume: Float,
    val maxHoldSeconds: Int,
    val setsSummary: String
)
