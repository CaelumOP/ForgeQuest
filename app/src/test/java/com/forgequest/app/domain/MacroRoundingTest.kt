package com.forgequest.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/** Audit item 15: one rounding rule for calories and macros; web/src/domain/macro-rounding.test.ts has the same cases. */
class MacroRoundingTest {
    @Test fun scaledCaloriesRoundToTheNearestKcal() {
        assertEquals(63, Nutrition.scaledKcal(125, 0.5f)) // logging used to cut this to 62
        assertEquals(188, Nutrition.scaledKcal(125, 1.5f))
        assertEquals(325, Nutrition.scaledKcal(250, 1.3f))
        assertEquals(0, Nutrition.scaledKcal(0, 2f))
        assertEquals(140, Nutrition.scaledKcal(140, 1f))
    }

    @Test fun theLogButtonTotalIsWhatTheDayGains() {
        // Two half servings of a 101 kcal food: each is logged as 51, so the button says 102, not round(101) = 101.
        val picks = listOf(101 to 0.5f, 101 to 0.5f)
        val logged = picks.map { (kcal, qty) -> Nutrition.scaledKcal(kcal, qty) }
        assertEquals(listOf(51, 51), logged)
        assertEquals(102, picks.sumOf { (kcal, qty) -> Nutrition.scaledKcal(kcal, qty) })
    }

    @Test fun aScannedMealIsTheSumOfItsSavedParts() {
        // 102 + 102 kcal at a small portion: 76.5 each, saved as two 77 kcal parts, so the meal logs 154 (not 153).
        assertEquals(154, Nutrition.scanKcal(listOf(102, 102), 0.75f, 0))
        assertEquals(154 + 50, Nutrition.scanKcal(listOf(102, 102), 0.75f, 50))
        assertEquals(500 + 251 + 50, Nutrition.scanKcal(listOf(400, 201), 1.25f, 50))
        assertEquals(0, Nutrition.scanKcal(emptyList(), 1f, 150))
    }

    @Test fun gramsLeftMatchTheEatenNumberOnScreen() {
        assertEquals(0, Nutrition.gramsLeft(100, 99.6f)) // shows 100 eaten, so nothing left (it said 1 left)
        assertEquals(0, Nutrition.gramsLeft(100, 99.5f))
        assertEquals(1, Nutrition.gramsLeft(100, 99.4f))
        assertEquals(-5, Nutrition.gramsLeft(150, 155.2f))
    }

    @Test fun weeklyCarbAveragesRoundInsteadOfCutting() {
        val today = 20_000L
        val weeks = Trends.carbWeeks(listOf(today to 100f, today to 101f, today - 1 to 0f), 12, today)
        assertEquals(12, weeks.size)
        assertEquals(101, weeks.last()) // 100.5 used to show as 100
        assertEquals(0, weeks.first())
    }
}
