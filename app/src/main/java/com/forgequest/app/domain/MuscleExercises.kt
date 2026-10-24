package com.forgequest.app.domain

import com.forgequest.app.data.db.ExerciseEntity

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
    /** Muscles with one built-in exercise show with a bigger neighbour, so no list opens nearly empty. */
    val COMPANIONS = mapOf(
        Muscle.OBLIQUES to listOf(Muscle.ABS),
        Muscle.ADDUCTORS to listOf(Muscle.QUADS, Muscle.GLUTES),
        Muscle.TIBIALIS to listOf(Muscle.CALVES),
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
            .filter { ex -> ex.primary.any { it in targets } || ex.secondary.any { it in targets } }
            .map { MuscleExercise(it, it.primary.any { m -> m in targets }, ExerciseTiers.of(it)) }
            .sortedWith(compareBy<MuscleExercise> { !it.main }.thenBy { it.tier?.ordinal ?: Tier.entries.size }.thenBy { it.exercise.name })
        val (inPlan, rest) = rows.partition { it.exercise.id in planExerciseIds }
        val shown = if (allEquipment) rest else rest.filter { ExerciseSwapEngine.profileFor(it.exercise).equipment in allowed }
        return MuscleExerciseList(inPlan, shown, rest.size - shown.size)
    }

    /**
     * The plan exercise [ex] could replace: the only one in the plan with the same movement pattern. Null when [ex] is
     * already in the plan or there are none or several (then the plan editor is the place to choose).
     */
    fun planSwapTarget(ex: ExerciseEntity, planExercises: List<ExerciseEntity>): ExerciseEntity? {
        if (planExercises.any { it.id == ex.id }) return null
        val pattern = ExerciseSwapEngine.profileFor(ex).pattern
        return planExercises.distinctBy { it.id }.filter { ExerciseSwapEngine.profileFor(it).pattern == pattern }.singleOrNull()
    }
}
