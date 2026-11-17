package com.forgequest.app.domain

import com.forgequest.app.data.db.ExerciseEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlateMathTest {
    private fun ex(id: String, equipment: String = "") = ExerciseEntity(
        id = id,
        name = id,
        kind = ExerciseKind.WEIGHTED,
        primary = listOf(Muscle.CHEST),
        secondary = emptyList(),
        equipment = equipment,
    )

    @Test
    fun detectsBarbellFromEquipmentOrSwapProfile() {
        assertFalse(PlateMath.isBarbellExercise(null))
        assertTrue(PlateMath.isBarbellExercise(ex("custom_bb", equipment = "BARBELL,BENCH")))
        assertFalse(PlateMath.isBarbellExercise(ex("custom_db", equipment = "DUMBBELL")))
        // Empty equipment falls back to ExerciseSwapEngine.catalogProfiles
        assertTrue(PlateMath.isBarbellExercise(ex("bench_press")))
        assertFalse(PlateMath.isBarbellExercise(ex("db_bench_press")))
    }

    @Test
    fun computesKgPlatesForStandard20KgBar() {
        val b = PlateMath.calculate(targetKg = 100f, unit = WeightUnit.KG, barWeightKg = 20f)
        assertEquals(20f, b.barWeight, 0.01f)
        assertEquals(100f, b.loadedWeight, 0.01f)
        assertEquals(0f, b.remainder, 0.01f)
        assertFalse(b.belowBar)
        assertFalse(b.barOnly)
        assertEquals(listOf(PlateMath.PlateCount(25f, 1), PlateMath.PlateCount(15f, 1)), b.perSide)
        assertEquals("25 + 15", PlateMath.formatPerSideSummary(b.perSide))
    }

    @Test
    fun handlesRepeatedPlatesAndSummaryFormatting() {
        val b = PlateMath.calculate(targetKg = 140f, unit = WeightUnit.KG, barWeightKg = 20f)
        assertEquals(listOf(PlateMath.PlateCount(25f, 2), PlateMath.PlateCount(10f, 1)), b.perSide)
        assertEquals("2×25 + 10", PlateMath.formatPerSideSummary(b.perSide))
    }

    @Test
    fun supportsWomen15KgBarAndBarOnlyAndBelowBar() {
        val barOnly = PlateMath.calculate(targetKg = 15f, unit = WeightUnit.KG, barWeightKg = 15f)
        assertTrue(barOnly.barOnly)
        assertFalse(barOnly.belowBar)
        assertTrue(barOnly.perSide.isEmpty())

        val below = PlateMath.calculate(targetKg = 10f, unit = WeightUnit.KG, barWeightKg = 15f)
        assertTrue(below.belowBar)
        assertFalse(below.barOnly)
    }

    @Test
    fun tracksUnloadedRemainderWhenSmallPlatesMissing() {
        val b = PlateMath.calculate(
            targetKg = 62.5f,
            unit = WeightUnit.KG,
            barWeightKg = 20f,
            plateInventoryKg = listOf(20f, 10f, 5f),
        )
        // Side wants 21.25 kg -> loads 20 kg per side -> 60 kg total, 2.5 kg remainder
        assertEquals(60f, b.loadedWeight, 0.01f)
        assertEquals(2.5f, b.remainder, 0.01f)
        assertEquals(listOf(PlateMath.PlateCount(20f, 1)), b.perSide)
    }

    @Test
    fun computesLbPlatesFor135And225LbTargets() {
        val lb135Kg = WeightUnit.LB.toKg(135f)
        val b135 = PlateMath.calculate(targetKg = lb135Kg, unit = WeightUnit.LB, barWeightKg = 20f)
        assertEquals(45f, b135.barWeight, 0.01f)
        assertEquals(135f, b135.loadedWeight, 0.01f)
        assertEquals(listOf(PlateMath.PlateCount(45f, 1)), b135.perSide)

        val lb225Kg = WeightUnit.LB.toKg(225f)
        val b225 = PlateMath.calculate(targetKg = lb225Kg, unit = WeightUnit.LB, barWeightKg = 20f)
        assertEquals(listOf(PlateMath.PlateCount(45f, 2)), b225.perSide)

        val bar35 = PlateMath.barWeightInUnit(15f, WeightUnit.LB)
        assertEquals(35f, bar35, 0.01f)
    }
}
