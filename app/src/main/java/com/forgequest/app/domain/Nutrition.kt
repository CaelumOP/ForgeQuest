package com.forgequest.app.domain

import kotlin.math.roundToInt

/** Daily calorie and protein suggestions. Protein follows body weight; calories need height, age and sex too. */
object Nutrition {
    /** About 1.8 g per kg, a common muscle-building target, rounded to 5 g. */
    fun suggestedProtein(kg: Float): Int = ((kg * 1.8f) / 5f).roundToInt() * 5

    /**
     * Mifflin-St Jeor resting energy times an activity factor from planned training days, rounded to 50 kcal.
     * Without height or age it falls back to 33 kcal per kg, which is what the app used before.
     */
    fun suggestedKcal(kg: Float, heightCm: Float, age: Int, male: Boolean, trainingDays: Int): Int {
        if (heightCm <= 0f || age <= 0) return ((kg * 33f) / 50f).roundToInt() * 50
        val rest = 10f * kg + 6.25f * heightCm - 5f * age + if (male) 5f else -161f
        val activity = when {
            trainingDays <= 0 -> 1.2f
            trainingDays <= 2 -> 1.375f
            trainingDays <= 4 -> 1.55f
            trainingDays <= 6 -> 1.725f
            else -> 1.9f
        }
        return ((rest * activity) / 50f).roundToInt() * 50
    }

    fun ageFromBirthYear(birthYear: Int, currentYear: Int): Int = if (birthYear <= 0) 0 else (currentYear - birthYear).coerceIn(0, 120)
}
