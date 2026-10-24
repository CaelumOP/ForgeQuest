package com.forgequest.app.ui

import com.forgequest.app.data.prefs.Settings
import com.forgequest.app.domain.CutPace
import com.forgequest.app.domain.DayActivity
import com.forgequest.app.domain.NutritionGoal
import org.junit.Assert.assertEquals
import org.junit.Test

/** Profile's goal / pace / normal-day switch (web helpers.test.ts applyNutrition has the same idea). */
class CalorieUiTest {
    private val today = 20_000L
    // The desk worker from the calorie plan: 83 kg, 172 cm, 20, 5 training days, Standard cut = 1,850 kcal, 165 g.
    private val onSuggestion = Settings(
        heightCm = 172f, birthYear = java.time.Year.now().value - 20, weeklyTargetOverride = 5,
        nutritionGoal = NutritionGoal.CUT, cutPace = CutPace.STANDARD, kcalGoal = 1850, proteinGoal = 165, proteinPerKg = 2.0f,
        goalSetDay = 100, targetSetDay = 100,
    )

    @Test fun targetsOnTheSuggestionFollowANewPace() {
        val next = CalorieUi.applyNutrition(onSuggestion, 83f, 0, null, today) { it.copy(cutPace = CutPace.GENTLE) }
        assertEquals(2050, next.kcalGoal)
        assertEquals(1.8f, next.proteinPerKg)
        assertEquals(150, next.proteinGoal)
        assertEquals(today, next.targetSetDay)
        assertEquals(83f, next.targetWeightKg)
        assertEquals(100L, next.goalSetDay) // same goal, its clock keeps running
    }

    @Test fun handSetNumbersStay() {
        val mine = onSuggestion.copy(kcalGoal = 2000, proteinGoal = 180)
        val next = CalorieUi.applyNutrition(mine, 83f, 0, null, today) { it.copy(dayActivity = DayActivity.MIXED) }
        assertEquals(2000, next.kcalGoal)
        assertEquals(180, next.proteinGoal)
        assertEquals(100L, next.targetSetDay)
        assertEquals(DayActivity.MIXED, next.dayActivity)
    }

    @Test fun newGoalRestartsItsClock() {
        val next = CalorieUi.applyNutrition(onSuggestion, 83f, 0, null, today) { it.copy(nutritionGoal = NutritionGoal.MAINTAIN) }
        assertEquals(today, next.goalSetDay)
        assertEquals(1.8f, next.proteinPerKg)
    }

    @Test fun watchStepsDecideTheDayBase() {
        val steps = StepsBase(1.40f, 11_500)
        assertEquals(1.40f, CalorieUi.inputs(onSuggestion, 83f, 0, steps)!!.base)
        assertEquals(DayActivity.SITTING.base, CalorieUi.inputs(onSuggestion, 83f, 0, null)!!.base)
        assertEquals(null, CalorieUi.inputs(onSuggestion, null, 0, steps))
    }
}
