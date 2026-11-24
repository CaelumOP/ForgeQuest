package com.forgequest.app.domain

import com.forgequest.app.data.db.ExerciseEntity
import kotlin.math.roundToInt

/**
 * Auto warm-up ramp generator for heavy barbell compounds:
 * - On by default only for new heavy barbell compounds in new plans; off otherwise.
 * - Generates 2–3 WARMUP sets (~50% / 70% / 85% of the first working weight, rounded to plate steps) before set 1.
 */
object WarmupRamp {

    data class RampSet(val weightKg: Float, val reps: Int)

    private val COMPOUND_PATTERNS = setOf(
        MovementPattern.HORIZONTAL_PUSH,
        MovementPattern.DIP_PUSH,
        MovementPattern.VERTICAL_PUSH,
        MovementPattern.VERTICAL_PULL,
        MovementPattern.HORIZONTAL_PULL,
        MovementPattern.UPPER_BACK_PULL,
        MovementPattern.SQUAT_BILATERAL,
        MovementPattern.SQUAT_UNILATERAL,
        MovementPattern.HIP_HINGE,
    )

    private val RAMP_STEPS = listOf(
        0.50f to 8,
        0.70f to 5,
        0.85f to 3,
    )

    /** True for weighted barbell compound lifts (squat, bench, deadlift, OHP, barbell row, hip thrust, good morning). */
    fun isHeavyBarbellCompound(ex: ExerciseEntity?): Boolean {
        if (ex == null || ex.kind != ExerciseKind.WEIGHTED) return false
        val profile = ExerciseSwapEngine.profileFor(ex)
        val usesBarbell = profile.equipment == Equipment.BARBELL ||
            Equipment.BARBELL in profile.requires ||
            ex.equipment.split(",").any { it.trim().equals(Equipment.BARBELL.name, ignoreCase = true) }
        return usesBarbell && profile.pattern in COMPOUND_PATTERNS
    }

    /**
     * Generates ramp sets below [workingWeightKg], rounded to [unit]'s load steps (2.5 kg / 5 lb).
     * When [isBarbell] is true, sets floor at [barWeightKg] (default 20 kg) and prepend an empty-bar
     * set for 10 reps when working weight is at least 3× the bar; non-barbell lifts floor at one load step.
     * Returns empty when working weight is missing or at/below the floor.
     */
    fun generate(
        workingWeightKg: Float,
        unit: WeightUnit = WeightUnit.KG,
        barWeightKg: Float = 20f,
        isBarbell: Boolean = true,
    ): List<RampSet> {
        if (!workingWeightKg.isFinite() || workingWeightKg <= 0f) return emptyList()
        val step = unit.loadStep
        val workingDisplay = unit.fromKg(workingWeightKg)
        val rawBar = if (barWeightKg.isFinite() && barWeightKg > 0f) barWeightKg else 20f
        val barDisplay = snap(unit.fromKg(rawBar), step).coerceAtLeast(step)
        val floorDisplay = if (isBarbell) barDisplay else step
        if (workingDisplay <= floorDisplay + 0.01f) return emptyList()

        val out = mutableListOf<RampSet>()
        var lastDisplay = -1f
        if (isBarbell && workingDisplay >= 3f * barDisplay - 0.01f) {
            lastDisplay = barDisplay
            out += RampSet(weightKg = unit.toKg(barDisplay), reps = 10)
        }
        for ((pct, reps) in RAMP_STEPS) {
            val targetDisplay = snap(workingDisplay * pct, step).coerceAtLeast(floorDisplay)
            if (targetDisplay >= workingDisplay - 0.01f) continue
            if (kotlin.math.abs(targetDisplay - lastDisplay) < 0.01f) continue
            lastDisplay = targetDisplay
            out += RampSet(weightKg = unit.toKg(targetDisplay), reps = reps)
        }
        // Near the floor, two preparation sets share the only load below working weight.
        if (out.size == 1) out += RampSet(out.first().weightKg, 5)
        return out
    }

    private fun snap(value: Float, step: Float): Float = (value / step).roundToInt() * step
}
