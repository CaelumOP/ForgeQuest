package com.forgequest.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class FoodScanClampTest {
    @Test
    fun clampsOutOfRangeAndNonFiniteAiScanNumbers() {
        val raw = FoodScan(
            dish = "Meal",
            items = listOf(
                ScanItem(name = "Huge", grams = 9000, kcal = 12000, protein = 800f, carbs = Float.NaN),
                ScanItem(name = "Neg", grams = -50, kcal = -10, protein = -5f, carbs = Float.POSITIVE_INFINITY),
                ScanItem(name = "Valid", grams = 220, kcal = 410, protein = 35.5f, carbs = 42.0f),
            ),
        )
        val clamped = clampFoodScan(raw)
        assertEquals(ScanItem("Huge", grams = 5000, kcal = 5000, protein = 500f, carbs = 0f), clamped.items[0])
        assertEquals(ScanItem("Neg", grams = 0, kcal = 0, protein = 0f, carbs = 0f), clamped.items[1])
        assertEquals(ScanItem("Valid", grams = 220, kcal = 410, protein = 35.5f, carbs = 42.0f), clamped.items[2])
    }
}
