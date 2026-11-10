package com.forgequest.app.domain

import com.forgequest.app.data.db.ExerciseEntity
import com.forgequest.app.data.seed.ExerciseCatalog

/** One row of a muscle's exercise list: [main] when the muscle is a primary one, [tier] null for custom exercises. */
data class MuscleExercise(val exercise: ExerciseEntity, val main: Boolean, val tier: Tier?)

/** The exercises for one muscle: the plan's first, then the rest. [hidden] = how many the equipment filter left out. */
data class MuscleExerciseList(val inPlan: List<MuscleExercise>, val others: List<MuscleExercise>, val hidden: Int) {
    val size get() = inPlan.size + others.size
}

/**
 * "Tap a muscle, see its exercises" (docs/plans/muscle-exercises-plan.md). Pure, so it's unit tested.
 * Web: domain/muscle-exercises.ts.
 */
object MuscleExercises {
    /**
     * Muscles with only a handful of built-in exercises show together with the bigger neighbour they are usually trained
     * with, so the list never opens nearly empty: shins with calves, forearms with biceps, traps with the upper back.
     */
    val COMPANIONS: Map<Muscle, List<Muscle>> = mapOf(
        Muscle.TIBIALIS to listOf(Muscle.CALVES),
        Muscle.FOREARMS to listOf(Muscle.BICEPS),
        Muscle.TRAPS to listOf(Muscle.UPPER_BACK),
    )

    /**
     * Exercises that work [muscle] (or its companions) as a primary or secondary muscle. Plan exercises come first;
     * the rest are main-muscle first, then Foundation → Advanced, then by name. Unless [allEquipment], the rest only
     * include exercises the person's [equipment] allows (empty = not set, so everything).
     */
    fun list(
        muscle: Muscle,
        library: List<ExerciseEntity>,
        planExerciseIds: Set<String>,
        equipment: Set<Equipment>,
        allEquipment: Boolean = false,
    ): MuscleExerciseList {
        val targets = setOf(muscle) + COMPANIONS[muscle].orEmpty()
        val allowed = equipment.ifEmpty { Equipment.entries.toSet() } + Equipment.BODYWEIGHT
        val rows = library
            .filter { ex -> ex.id !in ExerciseCatalog.STRETCH_IDS && (ex.primary.any { it in targets } || ex.secondary.any { it in targets }) }
            .map { MuscleExercise(it, it.primary.any { m -> m in targets }, ExerciseTiers.of(it)) }
            .sortedWith(compareBy<MuscleExercise> { !it.main }.thenBy { it.tier?.ordinal ?: Tier.entries.size }.thenBy { it.exercise.name })
        val (inPlan, rest) = rows.partition { it.exercise.id in planExerciseIds }
        val shown = if (allEquipment) rest else rest.filter { ExerciseSwapEngine.profileFor(it.exercise).equipment in allowed }
        return MuscleExerciseList(inPlan, shown, rest.size - shown.size)
    }

    /**
     * A plan day's exercises in the order to offer them for replacing with [ex], closest movement first; the flag
     * marks the ones doing the same movement. [ex] itself is left out, since replacing it with itself does nothing.
     */
    fun replaceOrder(ex: ExerciseEntity, dayExercises: List<ExerciseEntity>): List<Pair<ExerciseEntity, Boolean>> =
        ExerciseSwapEngine.rankSubstitutes(ex, dayExercises.distinctBy { it.id }).map { it.exercise to (it.profile.pattern == ExerciseSwapEngine.profileFor(ex).pattern) }
}
