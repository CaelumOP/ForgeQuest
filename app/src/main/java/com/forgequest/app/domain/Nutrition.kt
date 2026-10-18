package com.forgequest.app.domain

import kotlin.math.roundToInt

/** Daily calorie and protein suggestions. Protein follows body weight; calories need height, age and sex too. */
object Nutrition {
    /** [perKg] grams per kg (1.8 by default, a common muscle-building target), rounded to 5 g. */
    fun suggestedProtein(kg: Float, perKg: Float = 1.8f): Int = ((kg * perKg) / 5f).roundToInt() * 5

    /** Calories left after protein (4 kcal/g) and a [fatPct] share for fat, as grams of carbs (4 kcal/g), rounded to 5 g. */
    fun suggestedCarbs(kcal: Int, proteinG: Int, fatPct: Int = 25): Int =
        (((kcal * (1f - fatPct / 100f)) - proteinG * 4f) / 4f / 5f).roundToInt().coerceAtLeast(4) * 5

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
