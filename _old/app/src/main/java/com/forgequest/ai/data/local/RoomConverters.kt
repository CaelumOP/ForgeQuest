package com.forgequest.ai.data.local

import androidx.room.TypeConverter
import com.forgequest.ai.domain.model.ExerciseCategory
import com.forgequest.ai.domain.model.FlameHealthState
import com.forgequest.ai.domain.model.FoodCategory
import com.forgequest.ai.domain.model.MuscleGroup
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class RoomConverters {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @TypeConverter
    fun fromMuscleGroupList(value: List<MuscleGroup>?): String {
        return json.encodeToString(value ?: emptyList())
    }

    @TypeConverter
    fun toMuscleGroupList(value: String?): List<MuscleGroup> {
        if (value.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<MuscleGroup>>(value) }
            .getOrDefault(emptyList())
    }

    @TypeConverter
    fun fromMuscleGroup(value: MuscleGroup?): String =
        (value ?: MuscleGroup.CHEST).name

    @TypeConverter
    fun toMuscleGroup(value: String?): MuscleGroup =
        runCatching { MuscleGroup.valueOf(value ?: MuscleGroup.CHEST.name) }
            .getOrDefault(MuscleGroup.CHEST)

    @TypeConverter
    fun fromFoodCategory(value: FoodCategory?): String =
        (value ?: FoodCategory.BREAKFAST).name

    @TypeConverter
    fun toFoodCategory(value: String?): FoodCategory =
        runCatching { FoodCategory.valueOf(value ?: FoodCategory.BREAKFAST.name) }
            .getOrDefault(FoodCategory.BREAKFAST)

    @TypeConverter
    fun fromExerciseCategory(value: ExerciseCategory?): String =
        (value ?: ExerciseCategory.CALISTHENICS).name

    @TypeConverter
    fun toExerciseCategory(value: String?): ExerciseCategory =
        runCatching { ExerciseCategory.valueOf(value ?: ExerciseCategory.CALISTHENICS.name) }
            .getOrDefault(ExerciseCategory.CALISTHENICS)

    @TypeConverter
    fun fromFlameHealthState(value: FlameHealthState?): String =
        (value ?: FlameHealthState.INFERNO).name

    @TypeConverter
    fun toFlameHealthState(value: String?): FlameHealthState =
        runCatching { FlameHealthState.valueOf(value ?: FlameHealthState.INFERNO.name) }
            .getOrDefault(FlameHealthState.INFERNO)
}
