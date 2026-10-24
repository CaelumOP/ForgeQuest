package com.forgequest.app.domain

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/** Something the logs say about the calorie target, in the order Home shows them. */
enum class CalorieSignal {
    /** On a Standard or Fast cut and lifts stall, recovery stays low, or weight drops faster than the pace: offer a gentler pace. */
    UNDER_FUELLED,
    /** The 7-day average weight moved 2.5 kg from the weight the target was set at: offer a fresh suggestion. */
    WEIGHT_CHANGED,
    /** On a cut without full food logs, weight has barely moved for 4 weeks: offer 150 kcal less or more steps. */
    SCALE_CREEP,
    /** On a cut, daily steps dropped 2,000+ (or 25 %) from the first two weeks: a note that walking keeps fat loss going. */
    STEP_DROP,
}

/**
 * Real-world checks on the calorie target from what the person logs (calorie plan v2): the real daily burn from food
 * logs and weigh-ins, the step-limited move toward it, and the four signals. Pure, so the rules are unit tested.
 */
object CalorieCheck {
    const val WINDOW_DAYS = 21
    const val MIN_LOGGED_DAYS = 17
    const val MIN_WEIGH_INS = 6
    const val SKIP_AFTER_GOAL_DAYS = 7
    const val MAX_STEP_KCAL = 200
    const val COOLDOWN_DAYS = 14
    const val KCAL_PER_KG = 7700f

    /** A food day counts only with at least max(800, 0.70 × BMR) logged, so a half-logged day can't fake a tiny intake. */
    fun fullDayKcal(bmr: Float): Float = max(800f, 0.70f * bmr)

    /** Share of the last 21 days (before today) that were fully logged. */
    fun loggedShare(intake: Map<Long, Int>, today: Long, bmr: Float): Float {
        val full = fullDayKcal(bmr)
        return ((today - WINDOW_DAYS) until today).count { (intake[it] ?: 0) >= full } / WINDOW_DAYS.toFloat()
    }

    data class Burn(val kcal: Int, val loggedDays: Int, val kgPerWeek: Float)

    /**
     * The real daily burn over the last 21 days: average logged intake minus the weight trend × 7,700 kcal/kg.
     * The trend is the median of all pairwise slopes (Theil–Sen): one salty day can't swing it, and unlike a moving
     * average it doesn't lag behind. Null until 17 of 21 days are fully logged, 6+ weigh-ins span 14+ days with 2 in
     * the first and 2 in the last week, and the window starts 7+ days after the goal changed (week-one water weight).
     * Kept within 0.8–1.3 × [formulaBurn]. [intake] = epochDay → kcal; [weights] = (epochDay, kg).
     */
    fun realBurn(intake: Map<Long, Int>, weights: List<Pair<Long, Float>>, today: Long, goalSetDay: Long, bmr: Float, formulaBurn: Float): Burn? {
        val start = today - WINDOW_DAYS
        val end = today - 1
        if (start < goalSetDay + SKIP_AFTER_GOAL_DAYS) return null
        val full = fullDayKcal(bmr)
        val logged = (start..end).mapNotNull { d -> intake[d]?.takeIf { it >= full } }
        if (logged.size < MIN_LOGGED_DAYS) return null
        val w = weights.filter { it.first in start..end }.sortedBy { it.first }
        if (w.size < MIN_WEIGH_INS || w.last().first - w.first().first < 14) return null
        if (w.count { it.first < start + 7 } < 2 || w.count { it.first > end - 7 } < 2) return null
        val slope = theilSen(w)
        val burn = (logged.average().toFloat() - slope * KCAL_PER_KG).coerceIn(0.8f * formulaBurn, 1.3f * formulaBurn)
        return Burn((burn / 10f).roundToInt() * 10, logged.size, slope * 7f)
    }

    /** Median of the slopes between every pair of points (kg per day). */
    fun theilSen(points: List<Pair<Long, Float>>): Float {
        val slopes = mutableListOf<Float>()
        for (i in points.indices) for (j in i + 1 until points.size) {
            val dx = points[j].first - points[i].first
            if (dx > 0) slopes += (points[j].second - points[i].second) / dx
        }
        if (slopes.isEmpty()) return 0f
        val s = slopes.sorted()
        return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2f
    }

    /**
     * The next target on the way to [suggested]: at most ±200 kcal from [current], at most every 14 days after
     * [targetSetDay], and only when it moves 100+ kcal. Null when nothing should change.
     */
    fun nextTarget(current: Int, suggested: Int, targetSetDay: Long, today: Long): Int? {
        if (today - targetSetDay < COOLDOWN_DAYS) return null
        val step = (suggested - current).coerceIn(-MAX_STEP_KCAL, MAX_STEP_KCAL)
        return if (abs(step) < 100) null else ((current + step) / 50f).roundToInt() * 50
    }

    /**
     * The signals that apply today, most important first. [steps] = epochDay → steps (empty without a watch);
     * [deloadNeeded] = the deload check found stalled lifts or several low-recovery days; [loggedShare] from
     * [loggedShare]; [targetWeightKg] = the weight the target was set at (0 when unknown).
     */
    fun signals(
        goal: NutritionGoal,
        cutPace: CutPace,
        goalSetDay: Long,
        today: Long,
        weights: List<Pair<Long, Float>>,
        loggedShare: Float,
        steps: Map<Long, Long>,
        deloadNeeded: Boolean,
        targetWeightKg: Float,
    ): List<CalorieSignal> {
        val out = mutableListOf<CalorieSignal>()
        val cut = goal == NutritionGoal.CUT
        val trend = Nutrition.weeklyChangePct(goalSetDay, weights, today)?.first
        if (cut && cutPace != CutPace.GENTLE && (deloadNeeded || (trend != null && trend < -(cutPace.weeklyPct + 0.25f)))) out += CalorieSignal.UNDER_FUELLED
        val recent = weights.filter { it.first in (today - 6)..today }
        if (targetWeightKg > 0f && recent.size >= 2 && abs(recent.map { it.second }.average() - targetWeightKg) >= 2.5) out += CalorieSignal.WEIGHT_CHANGED
        if (cut && loggedShare < MIN_LOGGED_DAYS / WINDOW_DAYS.toFloat()) {
            val since = weights.filter { it.first >= goalSetDay }
            val month = Nutrition.weeklyChangePct(goalSetDay, weights, today, minDays = 28)?.first
            if (since.size >= MIN_WEIGH_INS && month != null && month > -0.15f) out += CalorieSignal.SCALE_CREEP
        }
        if (cut && stepDrop(steps, goalSetDay, today)) out += CalorieSignal.STEP_DROP
        return out
    }

    /** Average steps in the last 14 days vs the first 14 days of the cut, each needing 10 worn days (500+ steps). */
    fun stepDrop(steps: Map<Long, Long>, goalSetDay: Long, today: Long): Boolean {
        if (today - 14 < goalSetDay + 14) return false
        fun avg(from: Long) = (from until from + 14).mapNotNull { d -> steps[d]?.takeIf { it >= 500 } }.takeIf { it.size >= 10 }?.average()
        val first = avg(goalSetDay) ?: return false
        val last = avg(today - 14) ?: return false
        return first - last >= 2000 || last <= 0.75 * first
    }
}
