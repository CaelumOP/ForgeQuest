package com.forgequest.ai.domain.usecase

import com.forgequest.ai.data.local.entity.VaultFoodItemEntity
import com.forgequest.ai.data.remote.GeminiApiClient
import com.forgequest.ai.domain.model.AiModelTier
import com.forgequest.ai.domain.model.HiddenFatAddition
import com.forgequest.ai.domain.model.PortionMultiplier
import com.forgequest.ai.domain.model.VisionDetectedItem
import com.forgequest.ai.domain.model.VisionFoodAnalysisResult
import kotlin.math.roundToInt

enum class CalorieRingUiState(val statusLabel: String) {
    ON_TARGET_CYAN("On Target"),
    FUELLED_GOLD("Fuelled / Surplus")
}

data class MacroCalculationSummary(
    val totalCalories: Int,
    val totalProteinGrams: Float,
    val selectedItemCount: Int
)

/**
 * Domain UseCase for Hand-Scaled AI Food Vision analysis (`AnalyzeFoodPhotoUseCase`)
 * and Food Vault multi-select / portion / hidden fat macro calculations + Zero-Guilt
 * `FUELLED_GOLD` state evaluation as specified in `Engineering_Schema.md` Sections 4.4 & 5.
 */
class AnalyzeFoodPhotoUseCase(
    private val geminiApiClient: GeminiApiClient = GeminiApiClient()
) {

    suspend fun execute(
        jpegBytes: ByteArray,
        apiKey: String,
        preferredModel: AiModelTier = AiModelTier.GEMINI_3_8_FLASH,
        handLengthCm: Float = 18.5f,
        palmWidthCm: Float = 8.5f
    ): VisionFoodAnalysisResult? {
        return geminiApiClient.analyzeFoodPhoto(
            jpegBytes = jpegBytes,
            apiKey = apiKey,
            modelTier = preferredModel,
            handLengthCm = handLengthCm,
            palmWidthCm = palmWidthCm
        )
    }

    companion object {
        /**
         * Computes multi-select checklist totals across selected [VaultFoodItemEntity] items
         * and their stepper multipliers (`0.5x`, `1.0x`, `1.5x`, `2.0x`, `3.0x`).
         */
        fun calculateVaultChecklistTotals(
            selections: List<Pair<VaultFoodItemEntity, Float>>
        ): MacroCalculationSummary {
            val activeSelections = selections.filter { it.second > 0f }
            val totalCalories = activeSelections.sumOf { (item, multiplier) ->
                (item.caloriesPerServing * multiplier).roundToInt()
            }
            val totalProtein = activeSelections.sumOf { (item, multiplier) ->
                (item.proteinGramsPerServing * multiplier).toDouble()
            }.toFloat()

            return MacroCalculationSummary(
                totalCalories = totalCalories,
                totalProteinGrams = totalProtein,
                selectedItemCount = activeSelections.size
            )
        }

        /**
         * Computes 1-tap post-photo confirmation totals applying:
         * - Checked [VisionDetectedItem] items
         * - [PortionMultiplier] (`0.75x`, `1.0x`, `1.25x`)
         * - [HiddenFatAddition] (`Light +0 kcal`, `Normal +50 kcal`, `Heavy +150 kcal`)
         */
        fun calculateVisionConfirmationTotals(
            detectedItems: List<VisionDetectedItem>,
            portionMultiplier: PortionMultiplier,
            hiddenFatAddition: HiddenFatAddition
        ): MacroCalculationSummary {
            val checked = detectedItems.filter { it.selected }
            if (checked.isEmpty()) {
                return MacroCalculationSummary(0, 0f, 0)
            }
            val baseCalories = checked.sumOf { it.calories }
            val baseProtein = checked.sumOf { it.proteinGrams.toDouble() }.toFloat()

            val scaledCalories = (baseCalories * portionMultiplier.factor).roundToInt() +
                hiddenFatAddition.extraCalories
            val scaledProtein = baseProtein * portionMultiplier.factor

            return MacroCalculationSummary(
                totalCalories = scaledCalories,
                totalProteinGrams = scaledProtein,
                selectedItemCount = checked.size
            )
        }

        /**
         * Evaluates the Zero-Guilt Calorie Ring UI State:
         * When [loggedCalories] > [dailyCalorieGoal], returns [CalorieRingUiState.FUELLED_GOLD]
         * ("Fuelled / Surplus") rather than any punitive red/error state.
         */
        fun evaluateCalorieRingState(
            loggedCalories: Int,
            dailyCalorieGoal: Int
        ): CalorieRingUiState {
            return if (loggedCalories > dailyCalorieGoal) {
                CalorieRingUiState.FUELLED_GOLD
            } else {
                CalorieRingUiState.ON_TARGET_CYAN
            }
        }
    }
}
