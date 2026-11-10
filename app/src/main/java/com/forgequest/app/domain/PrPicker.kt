package com.forgequest.app.domain

import com.forgequest.app.data.db.SetEntity
import com.forgequest.app.data.seed.ExerciseCatalog

/**
 * Which done sets are personal records: one per exercise, the highest score strictly above the previous best
 * (the earliest set wins a tie), and none on an exercise's first ever session. Warm-up and cool-down sets, and stretches, never count.
 * The live workout and `finishSession` both use it so the gold tick and the saved flag agree.
 */
object PrPicker {
    /** [previousBest] holds the best score per exercise before this workout; an exercise missing from it is a first session. */
    fun pick(sets: List<SetEntity>, kindOf: (String) -> ExerciseKind, previousBest: Map<String, Float>): Set<Long> {
        val out = mutableSetOf<Long>()
        sets.filter { it.done && !WorkoutScore.isExtra(it.block) && it.exerciseId !in ExerciseCatalog.STRETCH_IDS }.groupBy { it.exerciseId }.forEach { (id, group) ->
            val prev = previousBest[id] ?: return@forEach
            val kind = kindOf(id)
            val best = group.maxByOrNull { Records.score(kind, it.reps, it.seconds, it.weightKg, id) } ?: return@forEach
            if (Records.score(kind, best.reps, best.seconds, best.weightKg, id) > prev) out += best.id
        }
        return out
    }
}
