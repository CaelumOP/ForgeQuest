package com.forgequest.app.data.seed

import com.forgequest.app.data.db.ExerciseEntity
import com.forgequest.app.domain.Muscle

/**
 * Built-in exercises whose equipment or movement group was wrong in an earlier catalog (plan audit 2026-11-25).
 * Existing installs keep stored metadata, so seeding rewrites a row only while it still holds the old wrong value;
 * custom exercises and anything the person changed are left alone.
 */
object CatalogFixes {
    data class Fix(val id: String, val oldEquipment: String, val oldPattern: String, val equipment: String, val pattern: String)

    val metadata: List<Fix> = listOf(
        Fix("sled_overhead_backward_walk", "BODYWEIGHT", "VERTICAL_PUSH", "MACHINE", "CARRY_CONDITIONING"),
        Fix("sled_reverse_flye", "BODYWEIGHT", "UPPER_BACK_PULL", "MACHINE", "UPPER_BACK_PULL"),
        Fix("sled_row", "BODYWEIGHT", "HORIZONTAL_PULL", "MACHINE", "HORIZONTAL_PULL"),
        Fix("sled_overhead_triceps_extension", "BODYWEIGHT", "ELBOW_EXTENSION", "MACHINE", "ELBOW_EXTENSION"),
        Fix("sled_push", "BODYWEIGHT", "CARRY_CONDITIONING", "MACHINE", "CARRY_CONDITIONING"),
        Fix("sledgehammer_swings", "BODYWEIGHT", "CARRY_CONDITIONING", "MACHINE", "CARRY_CONDITIONING"),
        Fix("kipping_muscle_up", "BODYWEIGHT", "HORIZONTAL_PULL", "PULL_UP_BAR", "VERTICAL_PULL"),
        Fix("isometric_neck_exercise_front_and_back", "BODYWEIGHT", "UPPER_BACK_PULL", "BODYWEIGHT", "MOBILITY"),
        Fix("isometric_neck_exercise_sides", "BODYWEIGHT", "UPPER_BACK_PULL", "BODYWEIGHT", "MOBILITY"),
        Fix("seated_head_harness_neck_resistance", "BODYWEIGHT", "UPPER_BACK_PULL", "BODYWEIGHT", "MOBILITY"),
        Fix("lying_face_down_plate_neck_resistance", "BARBELL", "UPPER_BACK_PULL", "BARBELL", "MOBILITY"),
        Fix("lying_face_up_plate_neck_resistance", "BARBELL", "UPPER_BACK_PULL", "BARBELL", "MOBILITY"),
    )

    /** Secondary muscles that were missing: id to (old list, new list) as Muscle enum names. */
    val secondary: Map<String, Pair<List<String>, List<String>>> = mapOf(
        "db_bench_press" to (listOf("TRICEPS") to listOf("TRICEPS", "SHOULDERS")),
    )

    /** [row] with the fixes applied; the same row when none applies. Custom exercises are never touched. */
    fun apply(row: ExerciseEntity): ExerciseEntity {
        if (row.custom) return row
        var out = row
        metadata.firstOrNull { it.id == row.id }?.let { f ->
            val equipment = if (row.equipment == f.oldEquipment) f.equipment else row.equipment
            val pattern = if (row.pattern == f.oldPattern) f.pattern else row.pattern
            if (equipment != row.equipment || pattern != row.pattern) out = out.copy(equipment = equipment, pattern = pattern)
        }
        secondary[row.id]?.let { (old, new) ->
            if (row.secondary.map { it.name } == old) out = out.copy(secondary = new.map { Muscle.valueOf(it) })
        }
        return out
    }
}
