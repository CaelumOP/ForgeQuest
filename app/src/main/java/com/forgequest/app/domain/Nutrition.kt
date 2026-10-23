package com.forgequest.app.domain

import kotlin.math.max
import kotlin.math.roundToInt

enum class NutritionGoal { CUT, MAINTAIN, GAIN }

/**
 * Daily calorie, protein, fat, and carbohydrate calculations audited against sports nutrition standards:
 * - BMR via Katch-McArdle (when body fat % is known) or Mifflin-St Jeor.
 * - Strict minimum caloric floor of 1,200 kcal/day.
 * - Protein floor of 1.6–2.2 g/kg of body weight (or lean mass when body fat > 30%).
 * - Essential fatty acid (EFA) minimum of 0.7 g/kg body weight (0.6–0.8 g/kg range) before filling carbs.
 */
object Nutrition {
    const val MIN_SAFE_KCAL = 1200
    const val MAX_SAFE_KCAL = 6000
    const val MIN_PROTEIN_PER_KG = 1.6f
    const val DEFAULT_PROTEIN_PER_KG = 1.8f
    const val MIN_FAT_PER_KG = 0.7f
    const val MIN_FAT_SHARE_PCT = 20
    const val DEFAULT_FAT_SHARE_PCT = 25
    const val MAX_DEFICIT_PCT = 0.22f

    /**
     * [perKg] grams per kg (1.8 by default, clamped to 1.2..2.5 for manual overrides and 1.6..2.4 for active defaults),
     * rounded to 5 g. When [bodyFatPct] > 30%, scales against estimated lean mass so high-adiposity users aren't
     * prescribed excessive protein.
     */
    fun suggestedProtein(kg: Float, perKg: Float = DEFAULT_PROTEIN_PER_KG, bodyFatPct: Float = 0f): Int {
        if (kg <= 0f) return 120
        val effectiveMassKg = if (bodyFatPct in 30f..60f) {
            // Use lean mass scaled to a 20% body-fat reference frame so protein stays in the 2.0–2.2g/kg LBM zone.
            kg * (1f - bodyFatPct / 100f) / 0.80f
        } else {
            kg
        }
        val clampedRatio = perKg.coerceIn(1.2f, 2.5f)
        val raw = ((effectiveMassKg * clampedRatio) / 5f).roundToInt() * 5
        return if (perKg >= MIN_PROTEIN_PER_KG) raw.coerceAtLeast(60) else raw.coerceAtLeast(50)
    }

    /**
     * Essential-fat-aware carbohydrate partitioning:
     * 1. Deducts protein calories (4 kcal/g).
     * 2. Computes dietary fat as the GREATER of [fatPct]% of total calories OR the essential fatty acid minimum
     *    ([MIN_FAT_PER_KG] = 0.7 g/kg of [bodyWeightKg], i.e. within the 0.6–0.8 g/kg physiological floor).
     * 3. Allocates remaining calories to carbohydrates (4 kcal/g), rounded to 5 g, never exceeding total calories.
     */
    fun suggestedCarbs(kcal: Int, proteinG: Int, fatPct: Int = DEFAULT_FAT_SHARE_PCT, bodyWeightKg: Float = 0f): Int {
        val safeKcal = kcal.coerceAtLeast(MIN_SAFE_KCAL)
        val fatFromPctG = (safeKcal * (fatPct.coerceIn(15, 45) / 100f)) / 9f
        val minEssentialFatG = if (bodyWeightKg > 0f) bodyWeightKg * MIN_FAT_PER_KG else 0f
        val effectiveFatG = max(fatFromPctG, minEssentialFatG)
        val remainingKcal = safeKcal - (proteinG * 4f) - (effectiveFatG * 9f)
        if (remainingKcal <= 0f) return 0
        return ((remainingKcal / 4f) / 5f).roundToInt().coerceAtLeast(4) * 5
    }

    /**
     *Resting BMR in kcal/day:
     * - Uses Katch-McArdle (370 + 21.6 × LBM) when [bodyFatPct] is known (4%..60%).
     * - Uses Mifflin-St Jeor (10×kg + 6.25×cm − 5×age + 5/−161) when [heightCm] and [age] are positive.
     * - Falls back to 22 kcal/kg otherwise.
     */
    fun bmrKcal(kg: Float, heightCm: Float, age: Int, male: Boolean, bodyFatPct: Float = 0f): Float {
        val safeKg = kg.coerceIn(30f, 250f)
        if (bodyFatPct in 4f..60f) {
            val lbmKg = safeKg * (1f - bodyFatPct / 100f)
            return 370f + 21.6f * lbmKg
        }
        if (heightCm <= 0f || age <= 0) return safeKg * 22f
        return 10f * safeKg + 6.25f * heightCm - 5f * age.coerceIn(10, 100) + if (male) 5f else -161f
    }

    /**
     * Mifflin-St Jeor (or Katch-McArdle when [bodyFatPct] > 0) times an activity factor from planned training days,
     * rounded to 50 kcal and strictly floored at [MIN_SAFE_KCAL] (1,200 kcal/day).
     * Without height or age (and without bodyFatPct) it falls back to 33 kcal per kg (floored at 1,200 kcal).
     */
    fun suggestedKcal(
        kg: Float,
        heightCm: Float,
        age: Int,
        male: Boolean,
        trainingDays: Int,
        bodyFatPct: Float = 0f,
        goal: NutritionGoal = NutritionGoal.MAINTAIN,
    ): Int {
        val safeKg = kg.coerceIn(30f, 250f)
        val tdee = if (bodyFatPct !in 4f..60f && (heightCm <= 0f || age <= 0)) {
            safeKg * 33f
        } else {
            val rest = bmrKcal(safeKg, heightCm, age, male, bodyFatPct)
            val activity = activityFactor(trainingDays)
            rest * activity
        }
        val adjusted = when (goal) {
            NutritionGoal.MAINTAIN -> tdee
            NutritionGoal.CUT -> {
                val maxDeficit = minOf(500f, tdee * MAX_DEFICIT_PCT)
                tdee - maxDeficit
            }
            NutritionGoal.GAIN -> tdee + 250f
        }
        return (((adjusted / 50f).roundToInt() * 50)).coerceIn(MIN_SAFE_KCAL, MAX_SAFE_KCAL)
    }

    /**
     * Activity multiplier from planned training days. Resistance sessions burn less than the classic "very active"
     * tables assume, so 6–7 lifting days stop at 1.65 and there is no 1.9 tier (that one is for physical jobs).
     */
    fun activityFactor(trainingDays: Int): Float = when {
        trainingDays <= 0 -> 1.2f
        trainingDays <= 3 -> 1.375f
        trainingDays <= 5 -> 1.55f
        else -> 1.65f
    }

    /** Protein per kg behind the suggestion: a cut keeps more protein to hold on to muscle. */
    fun proteinPerKgFor(goal: NutritionGoal): Float = when (goal) {
        NutritionGoal.CUT -> 2.0f
        NutritionGoal.MAINTAIN, NutritionGoal.GAIN -> DEFAULT_PROTEIN_PER_KG
    }

    fun ageFromBirthYear(birthYear: Int, currentYear: Int): Int =
        if (birthYear <= 0) 0 else (currentYear - birthYear).coerceIn(0, 120)

    /**
     * A gentle weekly note on the weight trend under the current goal, or null when there's nothing to say.
     * Needs weigh-ins spanning at least [MIN_TREND_DAYS] days since the goal was set, so first-week water
     * loss on a cut never triggers a false warning. [weights] are (epochDay, kg) pairs in any order.
     */
    fun weightTrendNote(goal: NutritionGoal, goalSetDay: Long, weights: List<Pair<Long, Float>>, today: Long): WeightTrend? {
        if (goal == NutritionGoal.MAINTAIN) return null
        val since = weights.filter { it.first >= goalSetDay && it.first <= today }.sortedBy { it.first }
        if (since.size < 3) return null
        val spanDays = since.last().first - since.first().first
        if (spanDays < MIN_TREND_DAYS) return null
        // Average the first and last few weigh-ins so one salty dinner doesn't decide it. The two groups never share
        // a weigh-in, and the change is spread over the days between their middles, so few weigh-ins don't shrink it.
        val n = minOf(3, since.size / 2)
        val headGroup = since.take(n)
        val tailGroup = since.takeLast(n)
        val head = headGroup.map { it.second }.average().toFloat()
        val tail = tailGroup.map { it.second }.average().toFloat()
        val betweenDays = (tailGroup.map { it.first }.average() - headGroup.map { it.first }.average()).toFloat()
        val weeklyChangePct = (tail - head) / head * 100f / (betweenDays / 7f)
        return when (goal) {
            NutritionGoal.CUT -> if (weeklyChangePct < -1f) WeightTrend.CUT_TOO_FAST else null
            NutritionGoal.GAIN -> if (spanDays >= 21 && weeklyChangePct <= 0.05f) WeightTrend.GAIN_STALLED else null
            NutritionGoal.MAINTAIN -> null
        }
    }

    const val MIN_TREND_DAYS = 14
}

enum class WeightTrend { CUT_TOO_FAST, GAIN_STALLED }
