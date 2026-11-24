package com.forgequest.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WarmupRampTest {

    @Test
    fun dumbbellRampIsNotFlooredAtTheBar() {
        val ramp = WarmupRamp.generate(20f, unit = WeightUnit.KG, isBarbell = false)
        assertEquals(listOf(10f, 15f, 17.5f), ramp.map { it.weightKg })
        assertEquals(listOf(8, 5, 3), ramp.map { it.reps })
    }

    @Test
    fun heavyBarbellStartsWithEmptyBarForTen() {
        val ramp = WarmupRamp.generate(60f, unit = WeightUnit.KG, barWeightKg = 20f, isBarbell = true)
        assertEquals(listOf(20f, 30f, 42.5f, 50f), ramp.map { it.weightKg })
        assertEquals(listOf(10, 8, 5, 3), ramp.map { it.reps })
    }

    @Test
    fun lightBarbellHasNoEmptyBarSet() {
        val ramp = WarmupRamp.generate(40f, unit = WeightUnit.KG, barWeightKg = 20f, isBarbell = true)
        assertEquals(listOf(20f, 27.5f, 35f), ramp.map { it.weightKg })
        assertEquals(listOf(8, 5, 3), ramp.map { it.reps })
    }

    @Test
    fun poundsSnapToLoadStep() {
        val ramp = WarmupRamp.generate(
            workingWeightKg = WeightUnit.LB.toKg(135f),
            unit = WeightUnit.LB,
            barWeightKg = WeightUnit.LB.toKg(45f),
            isBarbell = true,
        )
        assertEquals(listOf(45f, 70f, 95f, 115f), ramp.map { WeightUnit.LB.fromKg(it.weightKg) })
        assertEquals(listOf(10, 8, 5, 3), ramp.map { it.reps })

        val dbRamp = WarmupRamp.generate(
            workingWeightKg = WeightUnit.LB.toKg(30f),
            unit = WeightUnit.LB,
            isBarbell = false,
        )
        assertEquals(listOf(15f, 20f, 25f), dbRamp.map { WeightUnit.LB.fromKg(it.weightKg) })
        assertEquals(listOf(8, 5, 3), dbRamp.map { it.reps })
    }

    @Test
    fun nothingBelowTheBar() {
        assertTrue(WarmupRamp.generate(15f, unit = WeightUnit.KG, barWeightKg = 20f, isBarbell = true).isEmpty())
        assertTrue(WarmupRamp.generate(20f, unit = WeightUnit.KG, barWeightKg = 20f, isBarbell = true).isEmpty())
        assertTrue(WarmupRamp.generate(2.5f, unit = WeightUnit.KG, isBarbell = false).isEmpty())
    }
}
