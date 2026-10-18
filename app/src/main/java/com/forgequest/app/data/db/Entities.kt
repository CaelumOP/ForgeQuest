package com.forgequest.app.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.forgequest.app.domain.ExerciseKind
import com.forgequest.app.domain.Muscle
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "exercises")
data class ExerciseEntity(
    @PrimaryKey val id: String,
    val name: String,
    val kind: ExerciseKind,
    val primary: List<Muscle>,
    val secondary: List<Muscle>,
    /** Specific tutorial link. Null falls back to a YouTube search for the exercise name. */
    val videoUrl: String? = null,
    val custom: Boolean = false,
)

@Serializable
@Entity(tableName = "plans")
data class PlanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String = "",
    /** "starter", "coach", "manual", "docs" */
    val source: String,
    val active: Boolean,
    val createdAt: Long,
)

@Serializable
@Entity(tableName = "plan_days", indices = [Index("planId")])
data class PlanDayEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val planId: Long,
    /** 1 = Monday .. 7 = Sunday */
    val dayOfWeek: Int,
    val title: String,
    val isRest: Boolean,
    val note: String = "",
)

@Serializable
@Entity(tableName = "plan_items", indices = [Index("dayId")])
data class PlanItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dayId: Long,
    val position: Int,
    val exerciseId: String,
    /** Group label like "Warm-up", "Core", "Main". Items sharing a circuit block are done round by round. */
    val block: String,
    val circuit: Boolean,
    val sets: Int,
    val repsMin: Int? = null,
    val repsMax: Int? = null,
    val seconds: Int? = null,
    val weightKg: Float? = null,
    val restSeconds: Int = 60,
    val note: String = "",
)

@Serializable
@Entity(tableName = "sessions", indices = [Index("epochDay")])
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val planDayId: Long?,
    val title: String,
    val epochDay: Long,
    val startedAt: Long,
    val finishedAt: Long? = null,
)

@Serializable
@Entity(tableName = "sets", indices = [Index("sessionId"), Index("exerciseId")])
data class SetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val exerciseId: String,
    /** Position of the exercise inside the session, keeps the plan order. */
    val itemPosition: Int,
    val block: String,
    val circuit: Boolean,
    val setIndex: Int,
    val targetLabel: String,
    val reps: Int? = null,
    val seconds: Int? = null,
    val weightKg: Float? = null,
    val done: Boolean = false,
    val isPr: Boolean = false,
    val restSeconds: Int = 60,
)

@Serializable
enum class FoodCategory(val label: String) { BREAKFAST("Breakfast"), LUNCH("Lunch"), DINNER("Dinner"), SNACKS("Snacks"), DRINKS("Drinks") }

@Serializable
@Entity(tableName = "foods")
data class FoodEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val category: FoodCategory,
    val kcal: Int,
    val protein: Float,
    val serving: String = "",
    val photoPath: String? = null,
    val pinned: Boolean = false,
    val createdAt: Long,
)

@Serializable
@Entity(tableName = "meals", indices = [Index("epochDay")])
data class MealEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    val loggedAt: Long,
    val name: String,
    val category: FoodCategory,
    val kcal: Int,
    val protein: Float,
    val quantity: Float = 1f,
    val photoPath: String? = null,
    val foodId: Long? = null,
)

@Serializable
@Entity(tableName = "weights")
data class WeightEntity(
    @PrimaryKey val epochDay: Long,
    val kg: Float,
    val loggedAt: Long,
)

@Serializable
@Entity(tableName = "chat")
data class ChatEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** "user" or "coach" */
    val role: String,
    val text: String,
    val planJson: String? = null,
    val planApplied: Boolean = false,
    /** "chat", "checkin", "error" */
    val kind: String = "chat",
    val createdAt: Long,
)

@Serializable
@Entity(tableName = "badges")
data class BadgeEntity(
    @PrimaryKey val id: String,
    val unlockedAt: Long,
    val seen: Boolean = false,
)

class Converters {
    @TypeConverter
    fun musclesToString(list: List<Muscle>): String = list.joinToString(",") { it.name }

    @TypeConverter
    fun stringToMuscles(value: String): List<Muscle> =
        value.split(",").filter { it.isNotBlank() }.mapNotNull { runCatching { Muscle.valueOf(it) }.getOrNull() }
}
