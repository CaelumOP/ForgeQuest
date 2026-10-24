package com.forgequest.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class NutritionTest {
    @Test
    fun mifflinStJeorWithActivity() {
        // 75 kg, 178 cm, 25-year-old man, sitting, 5 training days: rest 1742.5 × (1.25 + 5 × 0.025) = 2396 → 2400
        assertEquals(2400, Nutrition.suggestedKcal(75f, 178f, 25, male = true, trainingDays = 5))
        // same woman, 3 days: rest 1576.5 × 1.325 = 2089 → 2100
        assertEquals(2100, Nutrition.suggestedKcal(75f, 178f, 25, male = false, trainingDays = 3))
        // 6 days: 1742.5 × 1.40 = 2440 → 2450
        assertEquals(2450, Nutrition.suggestedKcal(75f, 178f, 25, male = true, trainingDays = 6))
        assertEquals(1.425f, Nutrition.activityFactor(7), 0.0001f)
    }

    @Test
    fun goalShiftsCaloriesAndProtein() {
        // maintenance 2396: a Standard cut takes min(0.75 % of 75 kg × 1,100 = 619, 25 % = 599); a Standard gain adds 206
        assertEquals(1800, Nutrition.suggestedKcal(75f, 178f, 25, male = true, trainingDays = 5, goal = NutritionGoal.CUT))
        assertEquals(2600, Nutrition.suggestedKcal(75f, 178f, 25, male = true, trainingDays = 5, goal = NutritionGoal.GAIN))
        assertEquals(2.0f, Nutrition.proteinPerKgFor(NutritionGoal.CUT))
        assertEquals(1.8f, Nutrition.proteinPerKgFor(NutritionGoal.GAIN))
    }

    @Test
    fun weightTrendWaitsForTwoWeeksThenFlagsFastCut() {
        val fast = listOf(0L to 80f, 1L to 79.8f, 2L to 79.6f, 13L to 77.6f, 14L to 77.4f, 15L to 77.2f)
        // only 9 days of data since the goal was set: say nothing yet
        assertEquals(null, Nutrition.weightTrendNote(NutritionGoal.CUT, 6, fast, 15))
        // ~1.3 % a week over two weeks: too fast
        assertEquals(WeightTrend.CUT_TOO_FAST, Nutrition.weightTrendNote(NutritionGoal.CUT, 0, fast, 15))
        val steady = listOf(0L to 80f, 1L to 80f, 2L to 80f, 20L to 80f, 21L to 80.1f, 22L to 80f)
        assertEquals(WeightTrend.GAIN_STALLED, Nutrition.weightTrendNote(NutritionGoal.GAIN, 0, steady, 22))
        assertEquals(null, Nutrition.weightTrendNote(NutritionGoal.MAINTAIN, 0, fast, 15))
    }

    @Test
    fun weightTrendWithFewWeighInsSeesTheRealChange() {
        // Three weigh-ins used to be averaged into the same number twice, so a real gain read as "stalled".
        // ~1 % a week is a real gain, and faster than the Standard pace allows (0.5 %).
        val gaining = listOf(0L to 70f, 10L to 71f, 21L to 72f)
        assertEquals(WeightTrend.GAIN_TOO_FAST, Nutrition.weightTrendNote(NutritionGoal.GAIN, 0, gaining, 21))
        // 2.5 % a week with three weigh-ins: too fast
        val fastCut = listOf(0L to 80f, 7L to 78f, 14L to 76f)
        assertEquals(WeightTrend.CUT_TOO_FAST, Nutrition.weightTrendNote(NutritionGoal.CUT, 0, fastCut, 14))
    }

    @Test
    fun fallsBackToPerKgWithoutHeightOrAge() {
        // no height: 22 kcal/kg × 1.375 = 2269 → 2250
        assertEquals(2250, Nutrition.suggestedKcal(75f, 0f, 25, true, 5))
        assertEquals(135, Nutrition.suggestedProtein(75f))
        assertEquals(165, Nutrition.suggestedProtein(75f, perKg = 2.2f))
        // 2500 kcal, 135 g protein: 25 % fat leaves 1335 kcal = 335 g; 40 % fat leaves 960 kcal = 240 g.
        assertEquals(335, Nutrition.suggestedCarbs(2500, 135))
        assertEquals(245, Nutrition.suggestedCarbs(2500, 135, fatPct = 40)) // fat rounded to 110 g first, as on web
        assertEquals(26, Nutrition.ageFromBirthYear(2000, 2026))
        assertEquals(0, Nutrition.ageFromBirthYear(0, 2026))
    }
}
