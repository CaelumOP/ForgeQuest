package com.forgequest.app.domain

import com.forgequest.app.data.db.ExerciseEntity
import com.forgequest.app.data.db.PlanItemEntity

/** What the workout reminder says under "Today: <day>". */
sealed interface ReminderBody {
    /** The first main lift goes up: "Bench moves to 62.5 kg". */
    data class LoadUp(val exerciseId: String, val kg: Float) : ReminderBody
    /** Nothing moves, or no data: how many exercises are on the plan. */
    data class Count(val exercises: Int) : ReminderBody
}

object ReminderText {
    /** The day's first working weighted lift (warm-up and cool-down items do not count), or null if it has none. */
    fun firstMainLift(items: List<PlanItemEntity>, exercises: Map<String, ExerciseEntity>): PlanItemEntity? =
        items.sortedBy { it.position }.firstOrNull {
            !WorkoutScore.isExtra(it.block) && exercises[it.exerciseId]?.kind == ExerciseKind.WEIGHTED
        }

    /** A load-up line when [nextKg] is a real step above [lastKg] (more than 0.1 kg), otherwise the exercise count. */
    fun body(exerciseId: String?, lastKg: Float?, nextKg: Float?, exerciseCount: Int): ReminderBody =
        if (exerciseId != null && lastKg != null && nextKg != null && lastKg > 0f && nextKg > lastKg + 0.1f) ReminderBody.LoadUp(exerciseId, nextKg)
        else ReminderBody.Count(exerciseCount)
}
