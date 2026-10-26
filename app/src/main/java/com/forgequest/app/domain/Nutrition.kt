package com.forgequest.app.domain

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class NutritionGoal { CUT, MAINTAIN, GAIN }

/** How active a normal day is outside workouts; [base] is the activity factor before training days are added. */
enum class DayActivity(val base: Float) {
    SITTING(1.25f), MIXED(1.375f), PHYSICAL(1.50f);

    companion object {
        fun parse(raw: String?): DayActivity = entries.firstOrNull { it.name == raw } ?: SITTING
    }
}

/**
 * Fat-loss pace as % of body weight per week, its deficit cap (share of TDEE), protein, the weeks before a diet break
 * is suggested, and the weekly loss (%) past which the app warns. Shown as Gentle / Standard / Fast.
 */
enum class CutPace(val weeklyPct: Float, val maxTdeeShare: Float, val proteinPerKg: Float, val breakWeeks: Int, val fastWarnPct: Float) {
    GENTLE(0.50f, 0.20f, 1.8f, 16, -0.75f),
    STANDARD(0.75f, 0.25f, 2.0f, 12, -1.00f),
    AGGRESSIVE(1.00f, 0.30f, 2.2f, 6, -1.25f);

    companion object {
        fun parse(raw: String?): CutPace = entries.firstOrNull { it.name == raw } ?: STANDARD
    }
}

/** Muscle-gain pace as % of body weight per week, its surplus range, protein, and the weekly gain (%) past which the app warns. */
enum class GainPace(val weeklyPct: Float, val minSurplus: Float, val maxSurplus: Float, val proteinPerKg: Float, val fastWarnPct: Float) {
    SLOW(0.15f, 125f, 225f, 1.8f, 0.30f),
    STANDARD(0.25f, 200f, 350f, 1.8f, 0.50f);

    companion object {
        fun parse(raw: String?): GainPace = entries.firstOrNull { it.name == raw } ?: STANDARD
    }
}

/**
 * Daily calorie, protein, fat, and carbohydrate targets (calorie plan v2, 2026-10-24, reviewed against DLW and
 * calorimetry evidence):
 * - BMR via Katch-McArdle (when body fat % is known) or Mifflin-St Jeor.
 * - TDEE = BMR × (day base + 0.025 per weekly training day): lifting no longer counts as an active day.
 * - Cut and gain paces as % of body weight per week, capped, never below a body-based floor.
 * - Protein per kg of body weight (lean mass when body fat is 30–60 %, adjusted weight above BMI 30 when unknown).
 * - Essential fatty acid minimum of 0.7 g/kg body weight before filling carbs.
 */
object Nutrition {
    const val MIN_SAFE_KCAL = 1200
    const val MIN_SAFE_KCAL_MALE = 1500
    const val MAX_SAFE_KCAL = 6000
    const val MIN_PROTEIN_PER_KG = 1.6f
    const val DEFAULT_PROTEIN_PER_KG = 1.8f
    const val MIN_FAT_PER_KG = 0.7f
    const val MIN_FAT_SHARE_PCT = 20
    const val DEFAULT_FAT_SHARE_PCT = 25
    /** Each weekly training day adds this × BMR to every day (about 0.175 × BMR per session). */
    const val PER_TRAINING_DAY = 0.025f
    /** kcal per day for 1 kg of body weight change per week (7,700 kcal/kg ÷ 7). */
    const val KCAL_PER_KG_WEEK = 1100f

    private fun round50(x: Float) = (x / 50f).roundToInt() * 50

    /**
     * [kcal] times [factor] (servings, a portion, a corrected weight), rounded to the nearest whole kcal. The one rule
     * for scaled calories, so the total on the Log button is what the day's total gains. web: scaledKcal in nutrition.ts.
     */
    fun scaledKcal(kcal: Int, factor: Float): Int = (kcal * factor).roundToInt()

    /** A scanned meal's calories: each ticked item at the portion, rounded like its saved part, plus the extra fat. */
    fun scanKcal(itemKcal: List<Int>, portion: Float, fatKcal: Int): Int =
        if (itemKcal.isEmpty()) 0 else itemKcal.sumOf { scaledKcal(it, portion) } + fatKcal

    /** Grams still to eat: the goal minus the eaten grams as shown, so 99.6 of 100 g reads 100 and done, not "1 g left". */
    fun gramsLeft(goal: Int, eaten: Float): Int = goal - eaten.roundToInt()

    /**
     * [perKg] grams per kg (clamped to 1.2..2.5), rounded to 5 g. When [bodyFatPct] is 30–60 %, scales against lean
     * mass on a 20 % body-fat reference. When body fat is unknown and BMI is over 30, uses an adjusted weight
     * (BMI-25 weight + a quarter of the rest), so a 120 kg person isn't told to eat 240 g.
     */
    fun suggestedProtein(kg: Float, perKg: Float = DEFAULT_PROTEIN_PER_KG, bodyFatPct: Float = 0f, heightCm: Float = 0f): Int {
        if (kg <= 0f) return 120
        val effectiveMassKg = when {
            // Lean mass scaled to a 20% body-fat reference frame: [perKg] ÷ 0.8 per kg of lean mass (2.25 g at the default 1.8).
            bodyFatPct in 30f..60f -> kg * (1f - bodyFatPct / 100f) / 0.80f
            bodyFatPct <= 0f && heightCm > 0f && kg / ((heightCm / 100f) * (heightCm / 100f)) > 30f -> {
                val refKg = 25f * (heightCm / 100f) * (heightCm / 100f)
                refKg + 0.25f * (kg - refKg)
            }
            else -> kg
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
        // Fat rounded to 5 g first, so protein + fat + carbs add up to the goal; no carb minimum that could pass it.
        val fatG = (max(fatFromPctG, minEssentialFatG) / 5f).roundToInt() * 5
        val remainingKcal = safeKcal - (proteinG * 4f) - (fatG * 9f)
        if (remainingKcal <= 0f) return 0
        return ((remainingKcal / 4f) / 5f).roundToInt() * 5
    }

    /**
     * Resting BMR in kcal/day:
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

    /** Day base + 0.025 per weekly training day, kept within 1.25..1.80. */
    fun activityFactor(trainingDays: Int, base: Float = DayActivity.SITTING.base): Float =
        (base + PER_TRAINING_DAY * trainingDays.coerceIn(0, 7)).coerceIn(1.25f, 1.80f)

    /**
     * The day base from a watch: needs steps on at least 5 of the last 7 days (days under 500 steps mean the watch
     * wasn't worn). Days with a logged workout count 1,000 steps less, since training is added separately.
     * 4,000 steps = 1.25 (sitting), about 10,250 = 1.375, capped at 1.50. Null when there isn't enough data.
     * [days] = (steps, had a workout) for each of the last 7 days.
     */
    fun stepsBase(days: List<Pair<Long, Boolean>>): Float? {
        val valid = days.filter { it.first >= 500 }.map { (steps, workout) -> if (workout) max(0L, steps - 1000) else steps }
        if (valid.size < 5) return null
        return min(1.50f, 1.25f + 0.02f * max(0f, valid.average().toFloat() - 4000f) / 1000f)
    }

    /** Estimated daily burn: BMR × activity factor. */
    fun dailyBurn(kg: Float, heightCm: Float, age: Int, male: Boolean, trainingDays: Int, bodyFatPct: Float = 0f, base: Float = DayActivity.SITTING.base): Float =
        bmrKcal(kg, heightCm, age, male, bodyFatPct) * activityFactor(trainingDays, base)

    /** The lowest daily target the app suggests or accepts: 1,500 men / 1,200 women, and never under 85 % of BMR. */
    fun calorieFloor(kg: Float, heightCm: Float, age: Int, male: Boolean, bodyFatPct: Float = 0f): Int =
        round50(max(if (male) MIN_SAFE_KCAL_MALE.toFloat() else MIN_SAFE_KCAL.toFloat(), 0.85f * bmrKcal(kg, heightCm, age, male, bodyFatPct)))

    /**
     * The Fast pace isn't offered to under-18s or to people who are already lean (BMI under 20, or body fat under
     * 12 % for men / 20 % for women); they get Standard.
     */
    fun allowedCutPace(requested: CutPace, kg: Float, heightCm: Float, age: Int, male: Boolean, bodyFatPct: Float = 0f): CutPace {
        if (requested != CutPace.AGGRESSIVE) return requested
        val bmi = if (heightCm > 0f) kg / ((heightCm / 100f) * (heightCm / 100f)) else 25f
        val leanByFat = bodyFatPct in 4f..60f && bodyFatPct < (if (male) 12f else 20f)
        return if (age in 1..17 || bmi < 20f || leanByFat) CutPace.STANDARD else requested
    }

    /**
     * Daily target: [dailyBurn], minus the pace's deficit (kg × weekly % × 1,100, capped at a share of TDEE) or plus
     * its surplus, rounded to 50 and never below [calorieFloor]. A Fast pace the person can't have becomes Standard.
     * [burn] replaces the formula's TDEE when the app has learned the real one from logs (change 4).
     */
    fun suggestedKcal(
        kg: Float,
        heightCm: Float,
        age: Int,
        male: Boolean,
        trainingDays: Int,
        bodyFatPct: Float = 0f,
        goal: NutritionGoal = NutritionGoal.MAINTAIN,
        base: Float = DayActivity.SITTING.base,
        cutPace: CutPace = CutPace.STANDARD,
        gainPace: GainPace = GainPace.STANDARD,
        burn: Float? = null,
    ): Int {
        val safeKg = kg.coerceIn(30f, 250f)
        val tdee = burn ?: dailyBurn(safeKg, heightCm, age, male, trainingDays, bodyFatPct, base)
        val adjusted = when (goal) {
            NutritionGoal.MAINTAIN -> tdee
            NutritionGoal.CUT -> {
                val pace = allowedCutPace(cutPace, safeKg, heightCm, age, male, bodyFatPct)
                tdee - min(safeKg * pace.weeklyPct / 100f * KCAL_PER_KG_WEEK, tdee * pace.maxTdeeShare)
            }
            NutritionGoal.GAIN -> tdee + (safeKg * gainPace.weeklyPct / 100f * KCAL_PER_KG_WEEK).coerceIn(gainPace.minSurplus, gainPace.maxSurplus)
        }
        return round50(adjusted).coerceIn(calorieFloor(safeKg, heightCm, age, male, bodyFatPct), MAX_SAFE_KCAL)
    }

    /**
     * The target the app suggested before calorie plan v2 (activity from training days only, −500 / +250). Kept only
     * to spot people still on it, so Home can offer the new suggestion once.
     */
    fun legacySuggestedKcal(kg: Float, heightCm: Float, age: Int, male: Boolean, trainingDays: Int, bodyFatPct: Float = 0f, goal: NutritionGoal = NutritionGoal.MAINTAIN): Int {
        val safeKg = kg.coerceIn(30f, 250f)
        val factor = when { trainingDays <= 0 -> 1.2f; trainingDays <= 3 -> 1.375f; trainingDays <= 5 -> 1.55f; else -> 1.65f }
        val tdee = if (bodyFatPct !in 4f..60f && (heightCm <= 0f || age <= 0)) safeKg * 33f else bmrKcal(safeKg, heightCm, age, male, bodyFatPct) * factor
        val adjusted = when (goal) {
            NutritionGoal.MAINTAIN -> tdee
            NutritionGoal.CUT -> tdee - minOf(500f, tdee * 0.22f)
            NutritionGoal.GAIN -> tdee + 250f
        }
        return round50(adjusted).coerceIn(MIN_SAFE_KCAL, MAX_SAFE_KCAL)
    }

    /** Protein per kg behind the suggestion: it rises with the cut pace to hold on to muscle. */
    fun proteinPerKgFor(goal: NutritionGoal, cutPace: CutPace = CutPace.STANDARD, gainPace: GainPace = GainPace.STANDARD): Float = when (goal) {
        NutritionGoal.CUT -> cutPace.proteinPerKg
        NutritionGoal.GAIN -> gainPace.proteinPerKg
        NutritionGoal.MAINTAIN -> DEFAULT_PROTEIN_PER_KG
    }

    fun ageFromBirthYear(birthYear: Int, currentYear: Int): Int =
        if (birthYear <= 0) 0 else (currentYear - birthYear).coerceIn(0, 120)

    /**
     * Weekly % change of body weight since [goalSetDay], from the first and last few weigh-ins, or null with fewer than
     * 3 weigh-ins spanning [MIN_TREND_DAYS] days (so first-week water loss never counts). Pairs are (epochDay, kg).
     */
    fun weeklyChangePct(goalSetDay: Long, weights: List<Pair<Long, Float>>, today: Long, minDays: Int = MIN_TREND_DAYS): Pair<Float, Long>? {
        val since = weights.filter { it.first >= goalSetDay && it.first <= today }.sortedBy { it.first }
        if (since.size < 3) return null
        val spanDays = since.last().first - since.first().first
        if (spanDays < minDays) return null
        // Average the first and last few weigh-ins so one salty dinner doesn't decide it. The two groups never share
        // a weigh-in, and the change is spread over the days between their middles, so few weigh-ins don't shrink it.
        val n = minOf(3, since.size / 2)
        val headGroup = since.take(n)
        val tailGroup = since.takeLast(n)
        val head = headGroup.map { it.second }.average().toFloat()
        val tail = tailGroup.map { it.second }.average().toFloat()
        val betweenDays = (tailGroup.map { it.first }.average() - headGroup.map { it.first }.average()).toFloat()
        if (betweenDays <= 0f || head <= 0f) return null
        return (tail - head) / head * 100f / (betweenDays / 7f) to spanDays
    }

    /**
     * A gentle note on the weight trend under the current goal and pace, or null when there's nothing to say:
     * losing faster than the chosen pace allows, a diet break due after the pace's weeks, gaining too fast, or a
     * stalled gain. For women a stall needs 28 days, so a menstrual cycle's water swing can't fake one.
     */
    fun weightTrendNote(
        goal: NutritionGoal,
        goalSetDay: Long,
        weights: List<Pair<Long, Float>>,
        today: Long,
        cutPace: CutPace = CutPace.STANDARD,
        gainPace: GainPace = GainPace.STANDARD,
        female: Boolean = false,
    ): WeightTrend? {
        if (goal == NutritionGoal.MAINTAIN) return null
        val trend = weeklyChangePct(goalSetDay, weights, today)
        return when (goal) {
            NutritionGoal.CUT -> when {
                trend != null && trend.first < cutPace.fastWarnPct -> WeightTrend.CUT_TOO_FAST
                goalSetDay > 0 && today - goalSetDay >= cutPace.breakWeeks * 7L -> WeightTrend.DIET_BREAK_DUE
                else -> null
            }
            NutritionGoal.GAIN -> when {
                trend == null -> null
                trend.first > gainPace.fastWarnPct -> WeightTrend.GAIN_TOO_FAST
                trend.second >= (if (female) 28 else 21) && trend.first <= 0.05f -> WeightTrend.GAIN_STALLED
                else -> null
            }
            NutritionGoal.MAINTAIN -> null
        }
    }

    const val MIN_TREND_DAYS = 14
}

enum class WeightTrend { CUT_TOO_FAST, GAIN_STALLED, GAIN_TOO_FAST, DIET_BREAK_DUE }
