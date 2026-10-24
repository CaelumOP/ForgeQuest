package com.forgequest.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.roundToInt

/** The eight people from docs/plans/calorie-plan.md (v2) plus the real-burn checks; web calorie-plan.test.ts has the same cases. */
class CaloriePlanTest {
    private fun cut(kg: Float, cm: Float, age: Int, male: Boolean, days: Int, pace: CutPace = CutPace.STANDARD, base: Float = DayActivity.SITTING.base) =
        Nutrition.suggestedKcal(kg, cm, age, male, days, goal = NutritionGoal.CUT, cutPace = pace, base = base)

    @Test fun deskWorkerFiveDays() {
        assertEquals(2489, Nutrition.dailyBurn(83f, 172f, 20, true, 5).roundToInt())
        assertEquals(1850, cut(83f, 172f, 20, true, 5))
        assertEquals(2050, cut(83f, 172f, 20, true, 5, CutPace.GENTLE))
        assertEquals(1750, cut(83f, 172f, 20, true, 5, CutPace.AGGRESSIVE))
        assertEquals(165, Nutrition.suggestedProtein(83f, Nutrition.proteinPerKgFor(NutritionGoal.CUT), 0f, 172f))
        assertEquals(2300, Nutrition.legacySuggestedKcal(83f, 172f, 20, true, 5, goal = NutritionGoal.CUT))
    }

    @Test fun fewerDaysOrAMoreActiveDay() {
        assertEquals(1800, cut(83f, 172f, 20, true, 3))
        assertEquals(2050, cut(83f, 172f, 20, true, 5, base = DayActivity.MIXED.base))
    }

    @Test fun womanHitsTheFloorOnlyOnFast() {
        assertEquals(1300, cut(60f, 165f, 30, false, 3))
        assertEquals(1200, cut(60f, 165f, 30, false, 3, CutPace.AGGRESSIVE))
        assertEquals(1200, Nutrition.calorieFloor(60f, 165f, 30, false))
    }

    @Test fun highBmiProteinUsesAdjustedWeight() {
        assertEquals(1850, cut(100f, 180f, 35, true, 0))
        assertEquals(170, Nutrition.suggestedProtein(100f, 2.0f, 0f, 180f))
        assertEquals(165, Nutrition.suggestedProtein(83f, 2.0f, 0f, 172f))
    }

    @Test fun buildMusclePaces() {
        assertEquals(2700, Nutrition.suggestedKcal(83f, 172f, 20, true, 5, goal = NutritionGoal.GAIN))
        assertEquals(2650, Nutrition.suggestedKcal(83f, 172f, 20, true, 5, goal = NutritionGoal.GAIN, gainPace = GainPace.SLOW))
    }

    @Test fun fastBlockedForUnder18AndLean() {
        assertEquals(CutPace.STANDARD, Nutrition.allowedCutPace(CutPace.AGGRESSIVE, 52f, 168f, 17, false))
        assertEquals(1350, cut(52f, 168f, 17, false, 3, CutPace.AGGRESSIVE))
        assertEquals(CutPace.STANDARD, Nutrition.allowedCutPace(CutPace.AGGRESSIVE, 60f, 180f, 25, true))
        assertEquals(CutPace.STANDARD, Nutrition.allowedCutPace(CutPace.AGGRESSIVE, 80f, 180f, 25, true, 10f))
        assertEquals(CutPace.AGGRESSIVE, Nutrition.allowedCutPace(CutPace.AGGRESSIVE, 83f, 172f, 20, true))
    }

    @Test fun watchStepsReplaceTheDayAnswer() {
        val base = Nutrition.stepsBase(listOf(7800L, 8200L, 300L, 8000L, 7500L, 8500L, 8000L).map { it to false })!!
        assertEquals(1.33f, base, 0.001f)
        assertEquals(1950, cut(83f, 172f, 20, true, 4, base = base))
        assertEquals(1.33f, Nutrition.stepsBase(List(5) { 9000L to true })!!, 0.001f)
        assertNull(Nutrition.stepsBase(listOf(9000L, 9000L, 100L, 100L, 9000L, 9000L).map { it to false }))
        assertEquals(1.5f, Nutrition.stepsBase(List(7) { 30000L to false })!!, 0.0001f)
    }

    @Test fun floors() {
        assertEquals(1550, Nutrition.calorieFloor(83f, 172f, 20, true))
        assertEquals(1500, Nutrition.calorieFloor(55f, 160f, 40, true))
        assertEquals(1810, Nutrition.bmrKcal(83f, 172f, 20, true).roundToInt())
    }

    @Test fun paceChangesWarningsAndBreaks() {
        val w = listOf(0L to 80f, 1L to 80f, 2L to 80f, 14L to 78.56f, 15L to 78.56f, 16L to 78.56f)
        assertNull(Nutrition.weightTrendNote(NutritionGoal.CUT, 0, w, 16))
        assertEquals(WeightTrend.CUT_TOO_FAST, Nutrition.weightTrendNote(NutritionGoal.CUT, 0, w, 16, CutPace.GENTLE))
        assertEquals(WeightTrend.DIET_BREAK_DUE, Nutrition.weightTrendNote(NutritionGoal.CUT, 1, emptyList(), 1 + 6 * 7L, CutPace.AGGRESSIVE))
        assertNull(Nutrition.weightTrendNote(NutritionGoal.CUT, 1, emptyList(), 1 + 6 * 7L, CutPace.STANDARD))
        val flat = listOf(0L to 60f, 1L to 60f, 2L to 60f, 21L to 60f, 22L to 60f, 23L to 60f)
        assertEquals(WeightTrend.GAIN_STALLED, Nutrition.weightTrendNote(NutritionGoal.GAIN, 0, flat, 23))
        assertNull(Nutrition.weightTrendNote(NutritionGoal.GAIN, 0, flat, 23, female = true))
    }

    // ---- real burn and signals
    private val today = 100L
    private val intake = (79L..99L).associateWith { 2000 }
    private val weights = listOf(79L, 81L, 83L, 90L, 95L, 97L, 99L).map { d -> d to 85f - (0.5f / 7f) * (d - 79) }

    @Test fun realBurnFromIntakeAndTrend() {
        val b = CalorieCheck.realBurn(intake, weights, today, 0, 1810f, 2489f)!!
        assertEquals(2550, b.kcal)
        assertEquals(21, b.loggedDays)
        assertEquals(-0.5f, b.kgPerWeek, 0.01f)
    }

    @Test fun oneSaltyDayBarelyMovesIt() {
        val salty = weights.map { (d, kg) -> d to if (d == 95L) kg + 1.2f else kg }
        assertTrue(abs(CalorieCheck.realBurn(intake, salty, today, 0, 1810f, 2489f)!!.kcal - 2550) <= 60)
        assertEquals(1f, CalorieCheck.theilSen(listOf(0L to 1f, 1L to 2f, 2L to 3f)), 0.0001f)
    }

    @Test fun waitsForFullLogsWeighInsAndAWeek() {
        val partial = intake + listOf(80L, 82L, 84L, 86L, 88L).associateWith { 600 }
        assertNull(CalorieCheck.realBurn(partial, weights, today, 0, 1810f, 2489f))
        assertNull(CalorieCheck.realBurn(intake, weights.take(5), today, 0, 1810f, 2489f))
        assertNull(CalorieCheck.realBurn(intake, weights, today, 75, 1810f, 2489f))
        assertEquals(16f / 21f, CalorieCheck.loggedShare(partial, today, 1810f), 0.0001f)
    }

    @Test fun staysNearTheFormula() {
        val tiny = (79L..99L).associateWith { 1300 }
        assertEquals((0.8f * 2489f / 10f).roundToInt() * 10, CalorieCheck.realBurn(tiny, weights, today, 0, 1810f, 2489f)!!.kcal)
    }

    @Test fun targetMovesInSmallSteps() {
        assertEquals(2100, CalorieCheck.nextTarget(2300, 1850, 0, 20))
        assertNull(CalorieCheck.nextTarget(2300, 1850, 10, 20))
        assertNull(CalorieCheck.nextTarget(1900, 1850, 0, 20))
    }

    @Test fun theFourSignals() {
        fun s(goal: NutritionGoal = NutritionGoal.CUT, pace: CutPace = CutPace.STANDARD, setDay: Long = 0, w: List<Pair<Long, Float>> = emptyList(),
              logged: Float = 1f, steps: Map<Long, Long> = emptyMap(), deload: Boolean = false, targetKg: Float = 0f) =
            CalorieCheck.signals(goal, pace, setDay, today, w, logged, steps, deload, targetKg)
        assertEquals(listOf(CalorieSignal.UNDER_FUELLED), s(deload = true))
        assertEquals(emptyList<CalorieSignal>(), s(pace = CutPace.GENTLE, deload = true))
        assertEquals(listOf(CalorieSignal.WEIGHT_CHANGED), s(targetKg = 83f, w = listOf(96L to 80.4f, 99L to 80.5f)))
        val flat = listOf(60L to 83f, 62L to 83f, 64L to 83.1f, 88L to 83f, 92L to 82.9f, 96L to 83f)
        assertEquals(listOf(CalorieSignal.SCALE_CREEP), s(setDay = 60, w = flat, logged = 0.3f))
        assertEquals(emptyList<CalorieSignal>(), s(setDay = 60, w = flat, logged = 0.9f))
        val steps = (50L until 64L).associateWith { 8000L } + ((today - 14) until today).associateWith { 5500L }
        assertTrue(CalorieCheck.stepDrop(steps, 50, today))
        assertEquals(listOf(CalorieSignal.STEP_DROP), s(setDay = 50, steps = steps))
        assertEquals(emptyList<CalorieSignal>(), s(goal = NutritionGoal.MAINTAIN, deload = true, steps = steps))
    }
}
