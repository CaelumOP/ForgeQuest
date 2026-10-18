package com.forgequest.ai.domain.usecase

import com.forgequest.ai.data.local.entity.VaultFoodItemEntity
import com.forgequest.ai.domain.model.FoodCategory
import com.forgequest.ai.domain.model.HiddenFatAddition
import com.forgequest.ai.domain.model.PortionMultiplier
import com.forgequest.ai.domain.model.VisionDetectedItem
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * JUnit 4 verification suite for `FoodVaultCalculationTest` as specified in
 * `Engineering_Schema.md` Section 5 (Test Case #3).
 */
class FoodVaultCalculationTest {

    private val eggItem = VaultFoodItemEntity(
        foodId = 1L,
        name = "Boiled / Fried Egg",
        category = FoodCategory.BREAKFAST,
        caloriesPerServing = 72,
        proteinGramsPerServing = 6.3f,
        isPinned = true
    )

    private val parathaItem = VaultFoodItemEntity(
        foodId = 2L,
        name = "Paratha Flatbread",
        category = FoodCategory.BREAKFAST,
        caloriesPerServing = 260,
        proteinGramsPerServing = 5.0f,
        isPinned = true
    )

    private val cappuccinoItem = VaultFoodItemEntity(
        foodId = 3L,
        name = "Cappuccino / Mocha (Davidoff + Lactose-Free Milk)",
        category = FoodCategory.DRINKS,
        caloriesPerServing = 90,
        proteinGramsPerServing = 8.0f,
        isPinned = true
    )

    @Test
    fun `multi-select checklist and quantity steppers 0_5x 1x 2x compute exact calorie and protein totals`() {
        // User eats 2x Eggs (144 kcal, 12.6g), 0.5x Paratha (130 kcal, 2.5g), and 1x Cappuccino (90 kcal, 8.0g)
        val selections = listOf(
            eggItem to 2.0f,
            parathaItem to 0.5f,
            cappuccinoItem to 1.0f
        )

        val summary = AnalyzeFoodPhotoUseCase.calculateVaultChecklistTotals(selections)

        assertEquals(3, summary.selectedItemCount)
        assertEquals(364, summary.totalCalories) // 144 + 130 + 90 = 364 kcal
        assertEquals(23.1f, summary.totalProteinGrams, 0.01f) // 12.6 + 2.5 + 8.0 = 23.1g
    }

    @Test
    fun `vision confirmation portion multipliers 0_75x 1_0x 1_25x and heavy oil +150 kcal compute exact totals`() {
        val detectedItems = listOf(
            VisionDetectedItem(
                name = "Grilled Chicken Breast",
                estimatedGrams = 200,
                calories = 320,
                proteinGrams = 48.0f,
                selected = true
            ),
            VisionDetectedItem(
                name = "Basmati Rice",
                estimatedGrams = 150,
                calories = 200,
                proteinGrams = 4.0f,
                selected = true
            ),
            VisionDetectedItem(
                name = "Bread Roll (Uneaten)",
                estimatedGrams = 60,
                calories = 160,
                proteinGrams = 5.0f,
                selected = false // Unchecked by user
            )
        )

        // Base checked: 520 kcal, 52.0g protein
        // 1. Small (0.75x) + Light (0 kcal) -> 390 kcal, 39.0g protein
        val smallLight = AnalyzeFoodPhotoUseCase.calculateVisionConfirmationTotals(
            detectedItems = detectedItems,
            portionMultiplier = PortionMultiplier.SMALL,
            hiddenFatAddition = HiddenFatAddition.LIGHT
        )
        assertEquals(2, smallLight.selectedItemCount)
        assertEquals(390, smallLight.totalCalories)
        assertEquals(39.0f, smallLight.totalProteinGrams, 0.01f)

        // 2. Normal (1.0x) + Normal (+50 kcal) -> 570 kcal, 52.0g protein
        val normalStandard = AnalyzeFoodPhotoUseCase.calculateVisionConfirmationTotals(
            detectedItems = detectedItems,
            portionMultiplier = PortionMultiplier.NORMAL,
            hiddenFatAddition = HiddenFatAddition.NORMAL
        )
        assertEquals(570, normalStandard.totalCalories)
        assertEquals(52.0f, normalStandard.totalProteinGrams, 0.01f)

        // 3. Large (1.25x) + Heavy Oil/Ghee/Sauce (+150 kcal) -> (520 * 1.25) + 150 = 650 + 150 = 800 kcal, 65.0g protein
        val largeHeavy = AnalyzeFoodPhotoUseCase.calculateVisionConfirmationTotals(
            detectedItems = detectedItems,
            portionMultiplier = PortionMultiplier.LARGE,
            hiddenFatAddition = HiddenFatAddition.HEAVY
        )
        assertEquals(800, largeHeavy.totalCalories)
        assertEquals(65.0f, largeHeavy.totalProteinGrams, 0.01f)
    }

    @Test
    fun `over-target calories emit FUELLED_GOLD UI state rather than an error state`() {
        val dailyGoal = 2100

        // Under or at target -> ON_TARGET_CYAN
        assertEquals(
            CalorieRingUiState.ON_TARGET_CYAN,
            AnalyzeFoodPhotoUseCase.evaluateCalorieRingState(loggedCalories = 1850, dailyCalorieGoal = dailyGoal)
        )
        assertEquals(
            CalorieRingUiState.ON_TARGET_CYAN,
            AnalyzeFoodPhotoUseCase.evaluateCalorieRingState(loggedCalories = 2100, dailyCalorieGoal = dailyGoal)
        )

        // Over target -> FUELLED_GOLD ("Fuelled / Surplus")
        val overTargetState = AnalyzeFoodPhotoUseCase.evaluateCalorieRingState(
            loggedCalories = 2450,
            dailyCalorieGoal = dailyGoal
        )
        assertEquals(CalorieRingUiState.FUELLED_GOLD, overTargetState)
        assertEquals("Fuelled / Surplus", overTargetState.statusLabel)
    }
}
