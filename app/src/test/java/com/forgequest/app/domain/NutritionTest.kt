package com.forgequest.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class NutritionTest {
    @Test
    fun mifflinStJeorWithActivity() {
        // 75 kg, 178 cm, 25-year-old man, 5 training days: rest 1737.5 × 1.725 = 2997 → 3000
        assertEquals(3000, Nutrition.suggestedKcal(75f, 178f, 25, male = true, trainingDays = 5))
        // same woman, 3 days: rest 1571.5 × 1.55 = 2436 → 2450
        assertEquals(2450, Nutrition.suggestedKcal(75f, 178f, 25, male = false, trainingDays = 3))
    }

    @Test
    fun fallsBackToPerKgWithoutHeightOrAge() {
        assertEquals(2500, Nutrition.suggestedKcal(75f, 0f, 25, true, 5))
        assertEquals(135, Nutrition.suggestedProtein(75f))
        assertEquals(165, Nutrition.suggestedProtein(75f, perKg = 2.2f))
        // 2500 kcal, 135 g protein: 25 % fat leaves 1335 kcal = 335 g; 40 % fat leaves 960 kcal = 240 g.
        assertEquals(335, Nutrition.suggestedCarbs(2500, 135))
        assertEquals(240, Nutrition.suggestedCarbs(2500, 135, fatPct = 40))
        assertEquals(26, Nutrition.ageFromBirthYear(2000, 2026))
        assertEquals(0, Nutrition.ageFromBirthYear(0, 2026))
    }
}
