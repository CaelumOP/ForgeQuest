package com.forgequest.app.domain

import com.forgequest.app.data.db.ExerciseEntity
import com.forgequest.app.data.seed.ExerciseCatalog

/** The six big movements a plan is built around. Only lifts in one of these get a variation suggested. */
enum class CorePattern(val patterns: Set<MovementPattern>) {
    H_PRESS(setOf(MovementPattern.HORIZONTAL_PUSH)),
    H_PULL(setOf(MovementPattern.HORIZONTAL_PULL)),
    V_PRESS(setOf(MovementPattern.VERTICAL_PUSH)),
    V_PULL(setOf(MovementPattern.VERTICAL_PULL)),
    SQUAT(setOf(MovementPattern.SQUAT_BILATERAL, MovementPattern.SQUAT_UNILATERAL)),
    HINGE(setOf(MovementPattern.HIP_HINGE));

    companion object {
        fun of(pattern: MovementPattern): CorePattern? = entries.firstOrNull { pattern in it.patterns }
    }
}

/**
 * Variation cycling: when a lift has stalled ([StalledLift], from [Trends.analyzeFatigueAndPlateaus]), suggest a close
 * variation to train for a few weeks instead, the usual way past a plateau. Pure, so it is unit tested.
 *
 * A suggestion must be the same core movement (a stalled bench press never becomes a row), fit the person's equipment,
 * be the same kind of exercise (weighted, reps or hold), be a compound lift and not a fly or kickback, and sit at most
 * one difficulty tier away. It is never something
 * already in the plan, and never a lift the person cycled away from in the last [COOLDOWN_DAYS], so two lifts are not
 * swapped back and forth every week.
 */
object VariationCycle {
    /** A lift that was swapped out is not suggested again for 8 weeks. */
    const val COOLDOWN_DAYS = 56L

    data class Suggestion(val from: StalledLift, val to: ExerciseEntity)

    /**
     * Single-joint moves that share a movement pattern with the big lifts (a fly is a "horizontal push" to the swap
     * engine). They are fine as accessories but never stand in for a stalled press or hinge.
     */
    private val ISOLATION = setOf(
        "cable_fly", "pec_deck", "db_fly", "cable_crossover_high_to_low", "cable_crossover_low_to_high", "cable_glute_kickback",
    )

    /**
     * The variation to try for [stalled], or null when there is none (not one of the six core movements, or nothing
     * suitable in [library]). [equipment] empty means "not set", so anything goes. [planIds] are the exercises in the
     * active plan; [recentlyCycled] the ones swapped out lately (see [recentlyCycled]).
     *
     * Among the candidates, one that changes the angle or the equipment comes first (a new stimulus, not the same lift
     * under another name), then the order of the stalled lift's curated swaps, then the id so the answer is stable.
     */
    fun next(
        stalled: StalledLift,
        library: List<ExerciseEntity>,
        equipment: Set<Equipment>,
        planIds: Set<String>,
        recentlyCycled: Set<String>,
    ): Suggestion? {
        val source = library.firstOrNull { it.id == stalled.exerciseId } ?: return null
        val profile = ExerciseSwapEngine.profileFor(source)
        val core = CorePattern.of(profile.pattern) ?: return null
        val tier = ExerciseTiers.of(source)
        val allowed = expandLegacyEquipment(equipment).ifEmpty { Equipment.allActive } + Equipment.BODYWEIGHT
        fun curated(id: String) = profile.preferredSwaps.indexOf(id).let { if (it < 0) Int.MAX_VALUE else it }
        return library.asSequence()
            .filter { it.id != source.id && it.id !in ExerciseCatalog.STRETCH_IDS && it.id !in ISOLATION && it.id !in planIds && it.id !in recentlyCycled }
            .filter { it.kind == source.kind }
            .map { it to ExerciseSwapEngine.profileFor(it) }
            .filter { (_, p) -> p.pattern in core.patterns && p.fits(allowed) }
            .filter { (ex, _) ->
                val other = ExerciseTiers.of(ex)
                tier == null || other == null || kotlin.math.abs(other.ordinal - tier.ordinal) <= 1
            }
            .sortedWith(
                compareBy<Pair<ExerciseEntity, BiomechanicalProfile>> { (_, p) -> if (p.angle != profile.angle || p.equipment != profile.equipment) 0 else 1 }
                    .thenBy { (ex, _) -> curated(ex.id) }
                    .thenBy { (ex, _) -> ex.id },
            )
            .firstOrNull()
            ?.let { Suggestion(stalled, it.first) }
    }

    // ------------------------------------------------------------ History: "exerciseId:epochDay;..."

    private fun parse(history: String): List<Pair<String, Long>> = history.split(';').mapNotNull { entry ->
        val day = entry.substringAfterLast(':', "").toLongOrNull() ?: return@mapNotNull null
        entry.substringBeforeLast(':').takeIf { it.isNotBlank() }?.let { it to day }
    }

    /** Exercises swapped out within the last [COOLDOWN_DAYS], which must not be suggested yet. */
    fun recentlyCycled(history: String, today: Long): Set<String> =
        parse(history).filter { (_, day) -> today - day < COOLDOWN_DAYS }.mapTo(HashSet()) { it.first }

    /** [history] with [exerciseId] swapped out [today]; entries past the cooldown are dropped so the string stays short. */
    fun record(history: String, exerciseId: String, today: Long): String =
        (parse(history).filter { (id, day) -> id != exerciseId && today - day < COOLDOWN_DAYS } + (exerciseId to today))
            .joinToString(";") { (id, day) -> "$id:$day" }
}
