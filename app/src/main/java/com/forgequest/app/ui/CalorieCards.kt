package com.forgequest.app.ui

import com.forgequest.app.data.ProgressSnapshot
import com.forgequest.app.data.db.SessionEntity
import com.forgequest.app.data.health.HealthState
import com.forgequest.app.data.prefs.BodyType
import com.forgequest.app.data.prefs.Settings
import com.forgequest.app.data.prefs.SettingsRepository
import com.forgequest.app.domain.CalorieCheck
import com.forgequest.app.domain.CalorieSignal
import com.forgequest.app.domain.CutPace
import com.forgequest.app.domain.Dates
import com.forgequest.app.domain.Nutrition
import com.forgequest.app.domain.NutritionGoal
import com.forgequest.app.domain.Trends
import kotlin.math.abs
import kotlin.math.roundToInt

/** Everything a calorie suggestion depends on. [stepsAvg] is set when watch steps decided [base]. */
data class CalorieInputs(
    val kg: Float, val heightCm: Float, val age: Int, val male: Boolean, val trainingDays: Int, val bodyFatPct: Float,
    val base: Float, val stepsAvg: Int? = null,
)

/** The day base from the last 7 full days of watch steps, and the average steps behind it. */
data class StepsBase(val base: Float, val avgSteps: Int)

/** The one card Home shows about the calorie target. */
sealed interface CalorieCard {
    data class Migration(val from: Int, val to: Int) : CalorieCard
    data class Adaptive(val burn: Int, val to: Int) : CalorieCard
    data class UnderFuelled(val pace: CutPace) : CalorieCard
    data class WeightChanged(val to: Int) : CalorieCard
    data class ScaleCreep(val to: Int) : CalorieCard
    data object StepDrop : CalorieCard
}

/**
 * Calorie plan v2 on Android: the inputs behind a suggestion (with the watch's steps as the day base when there are
 * enough), Profile's goal/pace/normal-day switch, and the one calorie card on Home. Web: data/calories.ts.
 */
object CalorieUi {

    /** Watch steps over the last 7 full days, workout days counting 1,000 less; null without 5 worn days. */
    fun stepsBase(health: HealthState?, sessions: List<SessionEntity>, today: Long = Dates.today()): StepsBase? {
        val steps = (health as? HealthState.Ready)?.day?.dailySteps ?: return null
        val workoutDays = sessions.map { it.epochDay }.toSet()
        val days = ((today - 7) until today).mapNotNull { d -> steps[d]?.let { it to (d in workoutDays) } }
        val base = Nutrition.stepsBase(days) ?: return null
        return StepsBase(base, days.filter { it.first >= 500 }.map { it.first }.average().roundToInt())
    }

    fun trainingDays(s: Settings, planDays: Int): Int = if (s.weeklyTargetOverride > 0) s.weeklyTargetOverride else planDays

    fun inputs(s: Settings, latestKg: Float?, planDays: Int, steps: StepsBase?, thisYear: Int = java.time.Year.now().value): CalorieInputs? {
        if (latestKg == null) return null
        return CalorieInputs(
            kg = latestKg, heightCm = s.heightCm, age = Nutrition.ageFromBirthYear(s.birthYear, thisYear), male = s.bodyType == BodyType.MALE,
            trainingDays = trainingDays(s, planDays), bodyFatPct = s.bodyFatPct,
            base = steps?.base ?: s.dayActivity.base, stepsAvg = steps?.avgSteps,
        )
    }

    fun inputs(snap: ProgressSnapshot, health: HealthState?, today: Long = Dates.today()): CalorieInputs? =
        inputs(snap.settings, snap.latestWeight, snap.plan?.trainingDays?.size ?: 0, stepsBase(health, snap.sessions, today))

    fun suggestion(i: CalorieInputs, s: Settings, goal: NutritionGoal = s.nutritionGoal, burn: Float? = null): Int =
        Nutrition.suggestedKcal(i.kg, i.heightCm, i.age, i.male, i.trainingDays, i.bodyFatPct, goal, i.base, s.cutPace, s.gainPace, burn)

    fun burn(i: CalorieInputs): Float = Nutrition.dailyBurn(i.kg, i.heightCm, i.age, i.male, i.trainingDays, i.bodyFatPct, i.base)

    fun floor(i: CalorieInputs): Int = Nutrition.calorieFloor(i.kg, i.heightCm, i.age, i.male, i.bodyFatPct)

    fun hasBodyStats(s: Settings, age: Int): Boolean = (s.heightCm > 0f && age > 0) || s.bodyFatPct in 4f..60f

    fun protein(latestKg: Float?, s: Settings, perKg: Float): Int? =
        latestKg?.let { Nutrition.suggestedProtein(it, perKg.coerceAtLeast(Nutrition.MIN_PROTEIN_PER_KG), s.bodyFatPct, s.heightCm) }

    fun carbs(s: Settings, latestKg: Float?, kcal: Int, protein: Int): Int =
        Nutrition.suggestedCarbs(kcal, protein, s.fatSharePct.coerceAtLeast(Nutrition.MIN_FAT_SHARE_PCT), latestKg ?: 0f)

    /** A new calorie target, remembering when and at what weight, so updates wait 14 days and a 2.5 kg change shows. */
    fun withTarget(s: Settings, kcal: Int, latestKg: Float?, today: Long): Settings =
        s.copy(kcalGoal = kcal, targetSetDay = today, targetWeightKg = latestKg ?: s.targetWeightKg)

    /**
     * Changing the goal, pace or normal day: targets still on the old suggestion follow the new one, numbers set by hand
     * stay. A new goal restarts its clock (trend notes, diet break); a changed calorie target records when and at what weight.
     */
    fun applyNutrition(s: Settings, latestKg: Float?, planDays: Int, steps: StepsBase?, today: Long, change: (Settings) -> Settings): Settings {
        val next = change(s)
        val old = inputs(s, latestKg, planDays, steps)
        val new = inputs(next, latestKg, planDays, steps)
        val oldPerKg = Nutrition.proteinPerKgFor(s.nutritionGoal, s.cutPace, s.gainPace)
        val perKg = if (s.proteinPerKg == oldPerKg) Nutrition.proteinPerKgFor(next.nutritionGoal, next.cutPace, next.gainPace) else s.proteinPerKg
        val kcal = if (old != null && new != null && hasBodyStats(s, old.age) && suggestion(old, s) == s.kcalGoal) suggestion(new, next) else null
        val protein = if (protein(latestKg, s, s.proteinPerKg) == s.proteinGoal) protein(latestKg, next, perKg) else null
        val carbs = if (s.trackCarbs && carbs(s, latestKg, s.kcalGoal, s.proteinGoal) == s.carbsGoal) {
            carbs(next, latestKg, kcal ?: s.kcalGoal, protein ?: s.proteinGoal)
        } else null
        val updated = next.copy(
            goalSetDay = if (next.nutritionGoal != s.nutritionGoal) today else s.goalSetDay,
            proteinPerKg = perKg, proteinGoal = protein ?: s.proteinGoal, carbsGoal = carbs ?: s.carbsGoal,
        )
        return if (kcal != null && kcal != s.kcalGoal) withTarget(updated, kcal, latestKg, today) else updated
    }

    /**
     * The one calorie card for Home, or null. Order: the one-time "more accurate target" card for people still on the
     * old suggestion, then the learned real burn, then the signals (under-fuelled, weight changed, scale creep, steps).
     * Nothing while snoozed or without the body numbers a suggestion needs.
     */
    fun card(snap: ProgressSnapshot, health: HealthState?, today: Long = Dates.today()): CalorieCard? {
        val s = snap.settings
        val i = inputs(snap, health, today) ?: return null
        if (i.heightCm <= 0f || i.age <= 0) return null
        val suggested = suggestion(i, s)
        if (!s.calorieV2Seen) {
            val legacy = Nutrition.legacySuggestedKcal(i.kg, i.heightCm, i.age, i.male, i.trainingDays, i.bodyFatPct, s.nutritionGoal)
            if (s.kcalGoal == legacy && abs(suggested - legacy) >= 50) return CalorieCard.Migration(legacy, suggested)
        }
        if (today < s.calorieSnoozeUntil) return null

        val bmr = Nutrition.bmrKcal(i.kg, i.heightCm, i.age, i.male, i.bodyFatPct)
        val intake = snap.nutritionByDay.associate { it.epochDay to it.kcal }
        val weights = snap.weights.map { it.epochDay to it.kg }
        CalorieCheck.realBurn(intake, weights, today, s.goalSetDay, bmr, burn(i))?.let { b ->
            CalorieCheck.nextTarget(s.kcalGoal, suggestion(i, s, burn = b.kcal.toFloat()), s.targetSetDay, today)?.let { return CalorieCard.Adaptive(b.kcal, it) }
        }

        val day = (health as? HealthState.Ready)?.day
        val lowRecoveryDays = day?.week?.takeLast(5)?.count { (it.recovery ?: 0) in 1..44 } ?: 0
        val deload = Trends.analyzeFatigueAndPlateaus(snap.sessions, snap.doneSets, snap.exercises, today = today).stalledLifts.size >= 2 || lowRecoveryDays >= 3
        val signals = CalorieCheck.signals(
            s.nutritionGoal, s.cutPace, s.goalSetDay, today, weights, CalorieCheck.loggedShare(intake, today, bmr),
            day?.dailySteps.orEmpty(), deload, s.targetWeightKg,
        )
        for (signal in signals) when (signal) {
            CalorieSignal.UNDER_FUELLED -> return CalorieCard.UnderFuelled(gentler(s.cutPace))
            CalorieSignal.WEIGHT_CHANGED -> if (suggested != s.kcalGoal) return CalorieCard.WeightChanged(suggested)
            CalorieSignal.SCALE_CREEP -> {
                val to = maxOf(s.kcalGoal - 150, floor(i))
                if (to < s.kcalGoal) return CalorieCard.ScaleCreep(to)
            }
            CalorieSignal.STEP_DROP -> return CalorieCard.StepDrop
        }
        return null
    }

    private fun gentler(p: CutPace): CutPace = if (p == CutPace.AGGRESSIVE) CutPace.STANDARD else CutPace.GENTLE

    /** The target a gentler pace would give, for the under-fuelled card's text and action. */
    fun gentlerTarget(snap: ProgressSnapshot, health: HealthState?, pace: CutPace): Int =
        inputs(snap, health)?.let { suggestion(it, snap.settings.copy(cutPace = pace)) } ?: snap.settings.kcalGoal

    /** Applying a card: the target (or pace) changes and the card goes away. */
    suspend fun accept(card: CalorieCard, snap: ProgressSnapshot, health: HealthState?, settings: SettingsRepository, today: Long = Dates.today()) {
        val kg = snap.latestWeight
        when (card) {
            is CalorieCard.Migration -> settings.update { withTarget(it.copy(calorieV2Seen = true), card.to, kg, today) }
            is CalorieCard.Adaptive -> settings.update { withTarget(it, card.to, kg, today) }
            is CalorieCard.WeightChanged -> settings.update { withTarget(it, card.to, kg, today) }
            is CalorieCard.ScaleCreep -> settings.update { withTarget(it, card.to, kg, today) }
            is CalorieCard.UnderFuelled -> {
                val to = gentlerTarget(snap, health, card.pace)
                settings.update {
                    val perKg = if (it.proteinPerKg == it.cutPace.proteinPerKg) card.pace.proteinPerKg else it.proteinPerKg
                    withTarget(it.copy(cutPace = card.pace, proteinPerKg = perKg), to, kg, today)
                }
            }
            CalorieCard.StepDrop -> snooze(settings, today)
        }
    }

    /** "Not now" (or "Keep mine" on the one-time card): hides calorie cards for 14 days. */
    suspend fun snooze(settings: SettingsRepository, today: Long = Dates.today(), migration: Boolean = false) =
        settings.update { it.copy(calorieSnoozeUntil = today + CalorieCheck.COOLDOWN_DAYS, calorieV2Seen = it.calorieV2Seen || migration) }
}
