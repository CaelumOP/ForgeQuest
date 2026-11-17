package com.forgequest.app.domain

import com.forgequest.app.data.db.ExerciseEntity
import com.forgequest.app.data.prefs.DEFAULT_PLATE_INVENTORY
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Pure plate calculator for barbell exercises.
 * Converts the canonical kilogram target into the exercise's display unit (kg or lb),
 * subtracts the bar weight, and greedily computes plates needed per side.
 */
object PlateMath {
    data class PlateCount(val weight: Float, val count: Int)

    data class Breakdown(
        val unit: WeightUnit,
        val barWeight: Float,
        val targetWeight: Float,
        val loadedWeight: Float,
        val perSide: List<PlateCount>,
        val remainder: Float,
        val belowBar: Boolean,
        val barOnly: Boolean,
    )

    private val DEFAULT_LB_PLATES = listOf(45f, 35f, 25f, 10f, 5f, 2.5f)
    private val KG_TO_LB_PLATE = listOf(
        25f to 45f,
        20f to 45f,
        15f to 35f,
        10f to 25f,
        5f to 10f,
        2.5f to 5f,
        1.25f to 2.5f,
    )

    /** Returns true when [exercise] uses a barbell (persisted equipment field or SwapEngine profile). */
    fun isBarbellExercise(exercise: ExerciseEntity?): Boolean {
        if (exercise == null) return false
        if (exercise.equipment.isNotBlank()) {
            return exercise.equipment.split(",")
                .any { it.trim().equals(Equipment.BARBELL.name, ignoreCase = true) }
        }
        val profile = ExerciseSwapEngine.catalogProfiles[exercise.id] ?: return false
        return profile.equipment == Equipment.BARBELL || Equipment.BARBELL in profile.requires
    }

    /** Bar weight in [unit]: 20/15 kg map cleanly to standard 45/35 lb bars when displaying pounds. */
    fun barWeightInUnit(barWeightKg: Float, unit: WeightUnit): Float {
        val safeKg = barWeightKg.coerceAtLeast(0f)
        if (unit == WeightUnit.KG) return round2(safeKg)
        return when {
            safeKg in 19.5f..20.5f -> 45f
            safeKg in 14.5f..15.5f -> 35f
            else -> roundHalf(unit.fromKg(safeKg))
        }
    }

    fun platesForUnit(plateInventoryKg: List<Float>, unit: WeightUnit): List<Float> {
        val positive = plateInventoryKg.filter { it > 0f }
        if (positive.isEmpty()) return emptyList()
        if (unit == WeightUnit.KG) return positive.map(::round2).distinct().sortedDescending()
        val mapped = positive.mapNotNull { kg ->
            KG_TO_LB_PLATE.firstOrNull { abs(it.first - kg) < 0.05f }?.second
        }
        return (if (mapped.isNotEmpty()) mapped else DEFAULT_LB_PLATES).distinct().sortedDescending()
    }

    fun calculate(
        targetKg: Float,
        unit: WeightUnit,
        barWeightKg: Float = 20f,
        plateInventoryKg: List<Float> = DEFAULT_PLATE_INVENTORY,
    ): Breakdown {
        val bar = barWeightInUnit(barWeightKg, unit)
        val target = if (unit == WeightUnit.KG) round2(unit.fromKg(targetKg)) else roundHalf(unit.fromKg(targetKg))
        if (targetKg <= 0f || target < bar - 0.01f) {
            return Breakdown(
                unit = unit,
                barWeight = bar,
                targetWeight = target.coerceAtLeast(0f),
                loadedWeight = 0f,
                perSide = emptyList(),
                remainder = 0f,
                belowBar = true,
                barOnly = false,
            )
        }
        val plates = platesForUnit(plateInventoryKg, unit)
        var sideLeft = ((target - bar) / 2f).coerceAtLeast(0f)
        val perSide = mutableListOf<PlateCount>()
        for (plate in plates) {
            if (plate <= 0f) continue
            val count = ((sideLeft + 1e-3f) / plate).toInt()
            if (count > 0) {
                perSide += PlateCount(plate, count)
                sideLeft = (sideLeft - count * plate).coerceAtLeast(0f)
            }
        }
        val sideLoaded = perSide.sumOf { (it.weight * it.count).toDouble() }.toFloat()
        val loaded = round2(bar + 2f * sideLoaded)
        val remainder = round2((target - loaded).coerceAtLeast(0f))
        val barOnly = perSide.isEmpty() && remainder < 0.01f
        return Breakdown(
            unit = unit,
            barWeight = bar,
            targetWeight = target,
            loadedWeight = loaded,
            perSide = perSide,
            remainder = remainder,
            belowBar = false,
            barOnly = barOnly,
        )
    }

    fun formatPlate(weight: Float): String {
        val r = round2(weight)
        val whole = r.roundToInt()
        if (abs(r - whole) < 0.005f) return whole.toString()
        val tenths = (r * 10f).roundToInt()
        if (abs(r - tenths / 10f) < 0.005f) return String.format(java.util.Locale.US, "%.1f", r)
        return String.format(java.util.Locale.US, "%.2f", r)
    }

    fun formatPerSideSummary(perSide: List<PlateCount>): String =
        perSide.joinToString(" + ") { p ->
            if (p.count > 1) "${p.count}×${formatPlate(p.weight)}" else formatPlate(p.weight)
        }

    private fun round2(v: Float): Float = (v * 100f).roundToInt() / 100f
    private fun roundHalf(v: Float): Float = (v * 2f).roundToInt() / 2f
}
