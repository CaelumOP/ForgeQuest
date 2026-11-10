package com.forgequest.app.domain

import com.forgequest.app.data.db.ExerciseEntity

/** Body areas a person can ask the plan builder to work around. Pure data, no text: the UI names them. */
enum class Injury { SHOULDER, KNEE, LOWER_BACK, WRIST, ELBOW, NECK }

private val PATTERN_BLOCKS: Map<Injury, Set<MovementPattern>> = mapOf(
    Injury.SHOULDER to setOf(MovementPattern.VERTICAL_PUSH, MovementPattern.DIP_PUSH, MovementPattern.LATERAL_RAISE),
    Injury.ELBOW to setOf(MovementPattern.DIP_PUSH),
)

/** Ids from ExerciseCatalog (all verified to exist). Half squat and bridges are deliberately not here. */
private val ID_BLOCKS: Map<Injury, Set<String>> = mapOf(
    Injury.SHOULDER to setOf("muscle_up"),
    Injury.KNEE to setOf("pistol_squat", "jumping_jacks", "burpee"),
    Injury.LOWER_BACK to setOf("barbell_row", "back_squat", "kb_swing", "kb_snatch", "russian_twist"),
    Injury.ELBOW to setOf("skull_crusher"),
    Injury.NECK to setOf("shrug"),
)

/** Id fragments, so every variant (handstand_hold, handstand_push_up, kb_deadlift ...) is covered. */
private val ID_PARTS: Map<Injury, List<String>> = mapOf(
    Injury.SHOULDER to listOf("handstand", "planche"),
    Injury.LOWER_BACK to listOf("deadlift", "sit_up"),
    // Floor push-ups load the wrist flat; dumbbell and incline presses stay allowed.
    Injury.WRIST to listOf("push_up", "planche", "handstand"),
    Injury.NECK to listOf("handstand", "crunch"),
)

/** True when [ex] should not appear in a plan for someone with any of [injuries]. */
fun blocked(ex: ExerciseEntity, profile: BiomechanicalProfile, injuries: Set<Injury>): Boolean =
    injuries.any { injury ->
        profile.pattern in PATTERN_BLOCKS[injury].orEmpty() ||
            ex.id in ID_BLOCKS[injury].orEmpty() ||
            ID_PARTS[injury].orEmpty().any { it in ex.id } ||
            (injury == Injury.ELBOW && ex.id.endsWith("dip"))
    }
