# ForgeQuest: all the math in the app (for review)

Prepared 2026-10-24 from the code on `main`. The Android app (Kotlin, `app/src/main/java/com/forgequest/app/domain/`) is the reference, and the web app (`web/src/domain/`) is a line-for-line TypeScript port with the same unit tests. The full source is in the appendix.

---

## Brief for the reviewer

**What ForgeQuest is.** A personal workout and nutrition app for one person and a small group of friends, in English and Arabic. The users range from beginners doing calisthenics in a park, to home workouts with dumbbells or kettlebells, to full-gym lifters. Most are men aged roughly 18–35. Some wear a Fitbit Air, which sends heart rate, HRV, resting HR, breathing rate, sleep stages, steps, distance and calories through Health Connect (no SpO2 or VO2 max).

**The product goal.** Keep people training consistently for months **without burning out or dropping out**. Wherever there's a trade-off, we prefer slow, safe and sustainable over fast and aggressive.

**Constraints.**
- Everything runs on the phone: no server, no account, no data science pipeline.
- Formulas must be simple enough to unit-test and explain in one sentence.
- No inputs we don't have (no lab tests, no VO2 max, no body scans). Body fat % is optional and usually unknown.

**What we want from you.**
1. For each section below: is the math **correct** (no bugs, edge cases handled)? Is it **evidence-based**? Cite sources (guidelines, meta-analyses, validated equations).
2. List problems ranked by how much they would mislead a user, with a concrete case ("85 kg, 173 cm, 21 y, 5 lifting days → X, should be about Y").
3. For each fix, give the **exact replacement formula and constants**, plus 3–5 input → output test cases we can paste into unit tests.
4. Say what you'd **leave alone**. Don't propose changes that add complexity for little gain.
5. Flag anything that could be **unsafe** (too few calories, too fast a weight loss, ignoring pain).

---

## 1. Calories, protein, carbs and fat (`Nutrition.kt`)

Used in onboarding ("Your daily target") and in Profile → Goals. The person can always override.

**Inputs.** Weight (kg), height (cm), age, sex, number of **planned training days** in their plan, goal (Lose fat / Maintain / Build muscle), optional body fat %.

**Resting burn (BMR).**
- Body fat % known (4–60 %): **Katch-McArdle**, 370 + 21.6 × lean kg.
- Else, with height and age: **Mifflin-St Jeor**, 10×kg + 6.25×cm − 5×age + 5 (men) or − 161 (women).
- Else (no height or age): 22 kcal per kg. For the daily burn this fallback is 33 kcal/kg, with no activity factor.
- Weight is clamped to 30–250 kg and age to 10–100.

**Activity factor**, from planned training days only (not steps or job):

| Planned training days a week | Factor |
|---|---|
| 0 | 1.2 |
| 1–3 | 1.375 |
| 4–5 | 1.55 |
| 6–7 | 1.65 (no 1.9 tier) |

**Daily burn (TDEE)** = BMR × factor.

**Goal adjustment.**
- Lose fat: TDEE − min(500, 22 % of TDEE).
- Maintain: TDEE.
- Build muscle: TDEE + 250.
- The result is rounded to the nearest 50 and kept within 1,200–6,000 kcal.

**Protein.**
- 2.0 g/kg when losing fat, otherwise 1.8 g/kg. The g/kg value is clamped to 1.2–2.5.
- If body fat is 30–60 %, it's scaled to lean mass on a 20 % body-fat reference: kg × (1 − bf) / 0.8.
- Rounded to 5 g, with a minimum of 60 g (50 g if the person chose under 1.6 g/kg).

**Fat and carbs.**
- Fat = the larger of (fat share % × kcal / 9) and 0.7 g/kg, rounded to 5 g. The fat share defaults to 25 % and is clamped to 15–45 %.
- Carbs = (kcal − protein × 4 − fat × 9) / 4, rounded to 5 g, minimum 20 g (0 if nothing is left).

**Weight-trend notes** (after at least 14 days and 3 weigh-ins since the goal was set):
- It averages the first n and last n weigh-ins (n = min(3, count/2)), then works out the % change per week between the middles of those two groups.
- Lose fat: a warning if the loss is faster than **1 % of body weight a week** ("eat a little more to protect muscle").
- Build muscle: after 21+ days, a nudge if the gain is ≤ 0.05 %/week ("add a small snack").

**Worked example** (one of our users): man, 85 kg, 173 cm, 21 y, 5 planned training days, Lose fat.
- BMR = 850 + 1,081.25 − 105 + 5 = **1,831**
- TDEE = 1,831 × 1.55 = **2,838**
- Target = 2,838 − 500 = 2,338 → rounded to **2,350 kcal**
- Protein = 2.0 × 85 = **170 g**
- Fat = max(25 % × 2,350 / 9 = 65.3, 0.7 × 85 = 59.5) → **65 g**
- Carbs = (2,350 − 680 − 585) / 4 = 271 → **270 g**
- With a 3-day plan, the same person gets 1,831 × 1.375 − 500 = 2,018 → **2,000 kcal**.

**Our open questions.**
- The user feels 2,350 is too high for fat loss. Is counting training days with the classic 1.375/1.55/1.65 factors a fair estimate for someone who sits most of the day? Should training days and daily steps (from Health Connect, when available) be counted separately?
- Is a fixed 500 kcal deficit (capped at 22 %) the right default for a sustainable cut, or should it be a % of body weight per week (e.g. 0.5–0.75 %)?
- Is 2.0 g/kg protein on total body weight right for people with higher body fat when body fat % is unknown, which is the usual case? Should we use height-based or goal-weight-based protein instead?
- Is +250 kcal for "Build muscle" right for beginners compared with trained lifters?

---

## 2. What to do next session (`Progression.kt`)

Pre-fills each set of today's workout from the last two sessions of that exercise.

**Defaults** when the plan doesn't say: 3 sets; rep range 8–12; holds of 30 s.

**Effort per set.** One tap: Easy, OK (the default), Hard or Pain. Older notes are also read for words like "fail", "rpe 10", "pain", "سهل" and "ألم".

**May progress** only if every set was done, none was Hard or Pain, it wasn't a warm-up, and today isn't a low-readiness day (section 6).

**Weighted lifts** (reps + load):
- **With a rep range** (e.g. 8–12): if every set reached the top of the range, add one load step and go back to the bottom of the range.
- **With a single rep target** (e.g. 3×8): add load only after hitting it in **two sessions in a row** at the same weight, with no Hard or Pain sets.
- Otherwise: +1 rep per set until the top of the range.
- **Pain on any set:** one load step down, never below one step.
- **Load step:** +2.5 kg or +5 lb, snapped to 0.25 kg or 2.5 lb.

**Bodyweight reps:** +1 rep per set each session, up to the top of the range + 2. After that the screen suggests a harder variation.

**Holds:** +5 s per session, up to 1.5 × the plan's target (rounded to 5 s). At that ceiling it suggests a harder version.

**Low-readiness day:** keep last session's numbers. The session can also start "lighter": each exercise with 2+ sets loses its last set.

**Coming back after sick or injured days, or 3+ missed planned days:** a one-tap "Ease in" removes one set per exercise, never going below 2.

**Questions.** Is double progression (range → load) with these steps sensible for beginners and intermediates? Is +1 rep every session for bodyweight work too fast? Should the weight step be smaller for small lifts (e.g. lateral raises, +1 kg)?

---

## 3. Personal records (`Progress.kt` → `Records`)

Each set gets a comparable score. A set is a record when it beats every earlier set of that exercise; the first session never counts.
- **Weighted:** with load, kg × (1 + effectiveReps / 30), where effectiveReps = reps up to 12 and 12 + 3 × ln(1 + (reps − 12)) above 12. This is Epley with diminishing returns above 12 reps. With no load, reps / 100.
- **Bodyweight reps:** reps + 0.35 × added kg (vest or belt).
- **Holds:** seconds + 1.0 × added kg.

**Question.** Is a single number like this fair across rep ranges, for example 5×100 kg against 8×80 kg against 20×60 kg? Is 0.35 reps per added kg sensible for weighted pull-ups and dips?

---

## 4. Muscle recovery map (`Progress.kt` → `Recovery`)

Colours each muscle from "just trained" to "recovered".

**Effective sets.** Each logged set becomes a working-set equivalent, clamped to 0–1.35:
- Bodyweight reps: reps / 10 (reps / 6 with added weight; reps / 4 for high-skill moves like the muscle-up and pistol squat).
- Weighted: reps / 6 with load, otherwise reps / 10.
- Holds: seconds / 30 (seconds / 12 for high-skill holds).
- Each value is then clamped to 0.08–1.25 (0.15–1.25 for weighted).
- Warm-up sets count × 0.25. Hard or Pain sets count × 1.15, and Easy sets × 0.8.

**Stimulus per muscle per session.**
- Each set adds effective sets / 4 to its primary muscles and half that to its secondary muscles.
- The total is capped at 1. Stimulus below 0.02 is ignored.

**Fatigue.**
- Recovery window = 48 h × stimulus.
- Fatigue fades in a straight line: stimulus × (1 − hours since / window).
- The highest remaining fatigue across sessions wins.
- So 4+ hard sets on a muscle = 48 h, 2 sets = 24 h, 1 set = 12 h.

**Questions.**
- Should the window depend on the muscle (calves and abs recover faster than legs or back) and on training age?
- Is a straight-line fade okay?
- Is 4 sets = a full 48 h stimulus reasonable?

---

## 5. Weekly streak, reminders, XP and ranks (`Progress.kt`)

**Weekly streak** (weeks run Monday–Sunday):
- **Target:** the number of planned training days in the plan (at least 1), or the person's own override.
- **First week:** pro-rated, i.e. the planned days left after joining. If none are left, the week is "free": it neither counts nor breaks the streak.
- **Excused days** (sick, injured, travel, rest tag) on planned days lower that week's target.
- **Hitting a week:** workout days ≥ target. A free week with a workout still counts.
- The current week never breaks the streak while it's in progress.
- Finished weeks keep the target they had, even if the plan changes later.
- Rest days never hurt.

**Reminders.** They pause after **3 planned days missed in a row** (not excused), until a workout or a day tag. This avoids nagging someone who has fallen off.

**XP.**
- 50 per workout + 2 per done set + 25 per personal record + 100 per badge.
- Level n → n+1 needs 100 + 50 × (n − 1) XP.
- Ranks by level: E Starter (1+), D Regular (5+), C Committed (10+), B Strong (20+), A Elite (30+), S Legend (45+).

**Questions.**
- Does this streak design support adherence, or are there known pitfalls (the "what-the-hell" effect after a broken streak)? Should there be streak "freezes"?
- Is the XP curve paced well (the time to rank C or B at 3 workouts/week)?
- Does XP per set reward junk volume?

---

## 6. Readiness, sleep and strain from Fitbit (Android only, `Readiness.kt`)

Whoop-style scores. Whoop doesn't publish its formulas, so these follow its documented inputs and published methods.

**Sleep.**
- **Duplicates:** if a piece overlaps another by more than half, it's dropped (Fitbit's copy wins).
- **Joining:** chunks less than 3 h apart are joined into one night. The biggest sleep ending that day is the night; the rest are naps.
- **Sleep need** = 480 min + 4 min per strain point above 10 (yesterday) + sleep debt − naps, clamped to 6–12 h.
  - Sleep debt = a third of the last 3 nights' shortfall below 8 h, capped at 120 min.
- **Sleep performance** = asleep / need × 100, clamped to 0–100.

**Recovery (1–99 %).**
- **Baselines:** the 60 days before the day, needing at least 4 days. Minimum standard deviations: ln HRV 0.05, resting HR 2 bpm, breathing 0.5/min.
- **HRV signal** = 0.6 × ln(tonight's RMSSD) + 0.4 × mean ln(RMSSD) of the last 7 nights.
- **z-scores** have a dead zone of 0.5 SD (the "smallest worthwhile change"), shrink by that 0.5 SD beyond it, and are clamped to ±3.
  - Resting HR and breathing rate are inverted (lower is better).
- **Weights:** HRV 0.50, resting HR 0.25, sleep 0.15, breathing 0.10. Sleep enters as (performance − 85) / 15.
  - Only the inputs that exist are used, and their weights are renormalised.
- **Score** = 100 / (1 + e^−(1.4z + 0.4)). So z = 0 gives about 60 %.

**What the day's score means.**

| Recovery | Call | Strain target |
|---|---|---|
| 85+ | Peak | 15–18 |
| 67–84 | Ready | 13–16 |
| 50–66 | Steady | 10–13 |
| 34–49 | Ease off | 7–10 |
| below 34 | Recover | 4–7 |

Ease off and Recover count as **low readiness**: no progression, and a "lighter session" is offered.

**Strain (0–21).**
- **Max HR** = 208 − 0.7 × age (Tanaka).
- **Heart-rate reserve** = (HR − resting) / (max − resting), with a minimum gap of 40 bpm. Resting HR is the 30-day value.
- **Banister TRIMP** = Σ minutes × r × 0.64 e^(1.92 r) for men, or 0.86 e^(1.67 r) for women.
  - Only r ≥ 0.2 counts. Gaps between samples count as at most 30 s. Sleep time is excluded.
- **Strain** = 21 × (1 − e^(−TRIMP / 110)).
- **Bands:** light under 10, moderate 10+, high 14+, all out 18+.

**Deload suggestion** (`Trends.kt`). Any one of these triggers it:
- This week's sets are ≥ 135 % of the average of the previous weeks that had training, with ≥ 18 sets.
- 3+ of the last 4–5 days have recovery under 45 %.
- 2+ lifts are stalled and the load is ≥ 95 %. "Stalled" = 3+ sessions over 12+ days in the last 35 days, where the best of the last 2 sessions doesn't beat the earlier ones.

**Questions.** Are these weights, the logistic curve, the dead zone and the strain constant (110) defensible? Is a 60-day baseline with a 4-day minimum too unstable early on? What would you change first once we have 1–2 weeks of real data?

---

## 7. Progress screen numbers (`Trends.kt`)

- **Training load %** = this week's sets / the average of previous weeks that had training × 100. A first week reads 100.
- **Best set per day** uses the record score from section 3. The moving average is a trailing 7 days.
- **Lifetime minutes:** each session is clamped to 1–300 min. **kg lifted** = Σ weight × reps.

---

## 8. Badges (`Badges.kt`)

27 badges, each a simple threshold:
- **Workouts:** 1, 10, 50, 100.
- **Weekly streak:** 4, 12, 52 weeks.
- **Skills:** a muscle-up, a pistol squat, a 10 s L-sit, a 30 s handstand, a 10 s tuck planche, a 10 s tuck front lever, a 60 s dead hang.
- **Strength:**
  - 20 knee push-ups, 15 Australian pull-ups, 30 push-ups.
  - 1 pull-up, 10 pull-ups, 15 dips.
  - Pull-up with +20 kg, dip with +30 kg.
- **Food:** first meal logged, first meal scanned with the camera, protein goal hit 3, 7 or 30 days in a row.

**Question.** Are the thresholds well spaced for beginners, so that early wins come within weeks?

---

## 9. Exercise swaps (`ExerciseSwapEngine.kt`)

Used to adapt a plan to the person's equipment, and for "swap this exercise".

Each of the ~90 exercises has a profile: equipment, movement pattern (17 types), joint angle (25 types), and a curated list of preferred swaps.

**Candidate score:**

| Part | Points |
|---|---|
| Same pattern | 45 |
| Related pattern (e.g. vertical pull ↔ horizontal pull) | 18 |
| Same joint angle | 20 |
| Primary muscle overlap (Jaccard) | up to 20 |
| All-muscle overlap (Jaccard) | up to 10 |
| Same measurement kind (reps, hold or weighted) | 5 |
| Curated swap bonus | max(3, 15 − 2 × its position in the list) |

Candidates the person has the equipment for, scoring 45+, come first.

**Load never carries across exercises.** A 100 kg back squat is not a 100 kg goblet squat, so a swapped weighted exercise starts at 0 kg (from its own history). Rest goes up to at least 90 s.

---

## 10. Units

- Everything is stored in kg; 1 kg = 2.2046225 lb.
- **Load steps:** 2.5 kg or 5 lb. **Body-weight steps:** 0.1 kg or 0.2 lb.

---

## Appendix: full source (Android reference)

These are the exact files the sections above summarise. The web copies them in `web/src/domain/*.ts`.

### app/src/main/java/com/forgequest/app/domain/Nutrition.kt

```kotlin
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

```

### app/src/main/java/com/forgequest/app/domain/Progression.kt

```kotlin
package com.forgequest.app.domain

import kotlin.math.roundToInt

/**
 * Today's prefill for one exercise from the last two times it was done. Pure, so the rules are unit tested:
 * - Rep range (e.g. 8–12): top of the range on every set adds one load step and resets to the bottom.
 * - Single rep target (e.g. 3×8): load only goes up after hitting it in two sessions in a row at the same
 *   weight, so one good day never starts a climb every session.
 * - Holds: +5 s per session up to 1.5× the plan's target, then they stop and the screen suggests a harder version.
 * - Hard or Pain on any set, a missed set, or a low-readiness day keeps last session's numbers.
 *   Pain also takes the load back one step (bodyweight moves keep their reps).
 * - Load steps follow the person's unit: +2.5 kg or +5 lb, landing on numbers they can actually load.
 */
object Progression {

    /** One finished set as the rules need it. */
    data class PastSet(val reps: Int?, val seconds: Int?, val weightKg: Float?, val effort: SetEffort)

    data class Target(val sets: Int, val repsMin: Int?, val repsMax: Int?, val seconds: Int?, val weightKg: Float?)

    data class Prefill(val reps: Int?, val seconds: Int?, val weightKg: Float?)

    data class Result(val sets: List<Prefill>, val holdCeilingReached: Boolean = false)

    const val HOLD_STEP_SEC = 5
    const val HOLD_CEILING = 1.5f

    fun next(
        kind: ExerciseKind,
        target: Target?,
        last: List<PastSet>,
        previous: List<PastSet>,
        unit: WeightUnit,
        lowReadiness: Boolean,
        warmup: Boolean,
    ): Result {
        val count = target?.sets ?: 3
        val repsMin = target?.repsMin ?: 8
        val repsMax = target?.repsMax ?: target?.repsMin ?: 12
        val holdTarget = target?.seconds ?: 30
        val hasRange = target?.repsMin != null && target.repsMax != null && target.repsMax > target.repsMin

        val pain = last.any { it.effort == SetEffort.PAIN }
        val strained = pain || last.any { it.effort == SetEffort.HARD }
        // "Completed" means every set was done without a hard or painful one; only then may anything go up.
        val completed = !warmup && last.size >= count && !strained
        val mayProgress = completed && !lowReadiness

        fun hitTop(session: List<PastSet>) = session.size >= count && when (kind) {
            ExerciseKind.HOLD -> session.all { (it.seconds ?: 0) >= holdTarget }
            ExerciseKind.REPS, ExerciseKind.WEIGHTED -> session.all { (it.reps ?: 0) >= repsMax }
        }
        fun sessionWeight(session: List<PastSet>) = session.maxOfOrNull { it.weightKg ?: 0f } ?: 0f

        val holdCeiling = roundTo5((holdTarget * HOLD_CEILING).roundToInt()).coerceAtLeast(holdTarget)
        var ceilingReached = false

        val sets = (0 until count).map { i ->
            val lastSet = last.getOrNull(i) ?: last.lastOrNull()
            val lastWeight = lastSet?.weightKg ?: target?.weightKg ?: 0f
            when (kind) {
                ExerciseKind.HOLD -> {
                    val base = lastSet?.seconds ?: holdTarget
                    val next = if (mayProgress && hitTop(last) && base < holdCeiling) (base + HOLD_STEP_SEC).coerceAtMost(holdCeiling) else base
                    if (hitTop(last) && base >= holdCeiling) ceilingReached = true
                    val w = if (target?.weightKg != null || lastSet?.weightKg != null) lastWeight else null
                    Prefill(null, next, w)
                }
                ExerciseKind.WEIGHTED -> {
                    val baseReps = lastSet?.reps ?: target?.repsMin ?: 10
                    val loadUp = mayProgress && lastWeight > 0f && hitTop(last) && (
                        hasRange || (
                            hitTop(previous) &&
                                previous.none { it.effort == SetEffort.HARD || it.effort == SetEffort.PAIN } &&
                                sameWeight(sessionWeight(previous), sessionWeight(last))
                            )
                        )
                    when {
                        // Never below one load step: at 0 kg every load-up rule above is off, so the weight could never climb back.
                        pain && lastWeight > 0f -> Prefill(baseReps, null, stepDown(lastWeight, unit).coerceAtLeast(minOf(lastWeight, unit.toKg(unit.loadStep))))
                        loadUp && hasRange -> Prefill(repsMin, null, stepUp(lastWeight, unit))
                        loadUp -> Prefill(baseReps, null, stepUp(lastWeight, unit))
                        mayProgress && baseReps < repsMax -> Prefill(baseReps + 1, null, lastWeight)
                        else -> Prefill(baseReps, null, lastWeight)
                    }
                }
                ExerciseKind.REPS -> {
                    val baseReps = lastSet?.reps ?: target?.repsMin ?: 10
                    val next = if (mayProgress && baseReps < repsMax + 2) (baseReps + 1).coerceAtMost(999) else baseReps
                    val w = if (target?.weightKg != null || (lastSet?.weightKg != null && lastSet.weightKg > 0f)) lastWeight else null
                    Prefill(next, null, w)
                }
            }
        }
        return Result(sets, ceilingReached)
    }

    /** One load step up in the person's unit, landing on a loadable number (0.25 kg or 2.5 lb). */
    fun stepUp(kg: Float, unit: WeightUnit): Float = snap(unit.fromKg(kg) + unit.loadStep, unit).let(unit::toKg).coerceAtMost(999f)

    fun stepDown(kg: Float, unit: WeightUnit): Float = snap(unit.fromKg(kg) - unit.loadStep, unit).coerceAtLeast(0f).let(unit::toKg)

    private fun snap(value: Float, unit: WeightUnit): Float {
        val grid = if (unit == WeightUnit.LB) 2.5f else 0.25f
        return (value / grid).roundToInt() * grid
    }

    /** Equal once shown in either unit: 61.2 kg from a 135 lb entry and 61.23 kg are the same weight. */
    private fun sameWeight(a: Float, b: Float) = kotlin.math.abs(a - b) < 0.1f

    private fun roundTo5(sec: Int) = ((sec + 2) / 5) * 5
}

```

### app/src/main/java/com/forgequest/app/domain/Progress.kt

```kotlin
package com.forgequest.app.domain

import com.forgequest.app.data.db.ExerciseEntity
import com.forgequest.app.data.db.SetEntity
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

object Dates {
    fun today(): Long = LocalDate.now().toEpochDay()

    /** Monday of the week containing [epochDay]. */
    fun weekStart(epochDay: Long): Long = epochDay - (LocalDate.ofEpochDay(epochDay).dayOfWeek.value - 1)

    /** 1 = Monday .. 7 = Sunday */
    fun dayOfWeek(epochDay: Long): Int = LocalDate.ofEpochDay(epochDay).dayOfWeek.value

    fun todayDayOfWeek(): Int = LocalDate.now().dayOfWeek.value

    /** One-letter day names, Monday first, in the phone's language ("M T W…", "ن ث ر…"). */
    val shortDayLetters: List<String>
        get() = DayOfWeek.entries.map { it.getDisplayName(java.time.format.TextStyle.NARROW, java.util.Locale.getDefault()) }

    /** Short day names, Monday first ("Mon", "الاثنين"). */
    fun shortDayName(dayOfWeek: Int): String =
        DayOfWeek.of(dayOfWeek).getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault())

    fun dayName(dayOfWeek: Int): String =
        DayOfWeek.of(dayOfWeek).getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault())

    /** "Mon" in English; the full name in Arabic, where cutting it leaves only «ال». */
    fun dayAbbrev(dayOfWeek: Int): String = dayName(dayOfWeek).let { if (java.util.Locale.getDefault().language == "ar") it else it.take(3) }

    /** "Mon" in English, one letter in Arabic: for tight day strips. */
    fun dayChip(dayOfWeek: Int): String =
        if (java.util.Locale.getDefault().language == "ar") shortDayLetters[dayOfWeek - 1] else dayName(dayOfWeek).take(3)
}

// ---------------------------------------------------------------- Weekly streak

enum class DayState { DONE, REST, MISSED, TODAY_PLANNED, UPCOMING, OFF, EXCUSED }

/** Why a day was excused. Sick and Injured also offer an easier first session back. */
enum class DayTag { SICK, INJURED, TRAVEL, REST;
    companion object {
        fun parse(raw: String?): DayTag? = entries.firstOrNull { it.name == raw }
    }
}

data class WeeklyStreak(
    /** Consecutive weeks (ending this week if hit, otherwise last week) where the target was reached. */
    val weeks: Int,
    val best: Int,
    val doneThisWeek: Int,
    val targetThisWeek: Int,
    /** Monday..Sunday */
    val days: List<DayState>,
) {
    val weekProgress: Float get() = if (targetThisWeek <= 0) (if (doneThisWeek > 0) 1f else 0f) else min(1f, doneThisWeek.toFloat() / targetThisWeek)

    /** The week you joined, when no planned workouts were left in it. */
    val freeWeek: Boolean get() = targetThisWeek == 0 && doneThisWeek == 0
    val hitThisWeek: Boolean get() = if (targetThisWeek == 0) doneThisWeek > 0 else doneThisWeek >= targetThisWeek
}

object StreakCalculator {
    /**
     * A week counts when the number of workout days reaches the target. Rest days never hurt.
     * The first week is prorated from [startEpochDay] so joining on a Thursday is fair.
     * The current week never breaks the streak while it's still in progress.
     */
    fun compute(
        workoutDays: Set<Long>,
        plannedTrainingDays: Set<Int>,
        targetOverride: Int,
        startEpochDay: Long,
        today: Long,
        excusedDays: Set<Long> = emptySet(),
        /** Targets saved for finished weeks (Monday epoch day to days); a plan change never rewrites them. */
        lockedTargets: Map<Long, Int> = emptyMap(),
    ): WeeklyStreak {
        val fullTarget = fullTarget(plannedTrainingDays, targetOverride)
        val thisWeek = Dates.weekStart(today)
        val firstWeek = Dates.weekStart(startEpochDay)

        fun doneIn(weekStart: Long) = workoutDays.count { it in weekStart..weekStart + 6 }

        fun targetFor(weekStart: Long): Int {
            val weekTarget = lockedTargets[weekStart] ?: fullTarget
            val base = if (weekStart != firstWeek) {
                weekTarget
            } else {
                val remainingPlanned = (startEpochDay..weekStart + 6).count { Dates.dayOfWeek(it) in plannedTrainingDays }
                // Joined after the last planned day of the week: that week is free, it neither counts nor breaks.
                min(weekTarget, remainingPlanned)
            }
            // Each excused planned day (sick, travel...) lowers the week's target; a fully excused week is free.
            val excused = (weekStart..weekStart + 6).count { it in excusedDays && it !in workoutDays && Dates.dayOfWeek(it) in plannedTrainingDays }
            return (base - excused).coerceAtLeast(0)
        }

        val doneThisWeek = doneIn(thisWeek)
        val targetThisWeek = targetFor(thisWeek)

        // A workout in a free week still counts: effort should never feel wasted.
        fun hit(weekStart: Long) = targetFor(weekStart).let { t -> if (t == 0) doneIn(weekStart) > 0 else doneIn(weekStart) >= t }
        fun free(weekStart: Long) = targetFor(weekStart) == 0 && doneIn(weekStart) == 0

        var streak = 0
        var week = thisWeek - 7
        while (week >= firstWeek && (hit(week) || free(week))) {
            if (hit(week)) streak++
            week -= 7
        }
        if (hit(thisWeek)) streak++

        var best = 0
        var run = 0
        var w = firstWeek
        while (w <= thisWeek) {
            when {
                hit(w) -> { run++; best = max(best, run) }
                free(w) || w == thisWeek -> Unit
                else -> run = 0
            }
            w += 7
        }

        val days = (0..6).map { offset ->
            val day = thisWeek + offset
            val dow = offset + 1
            val planned = dow in plannedTrainingDays
            when {
                day in workoutDays -> DayState.DONE
                day in excusedDays && day <= today -> DayState.EXCUSED
                day == today && planned -> DayState.TODAY_PLANNED
                day > today && planned -> DayState.UPCOMING
                day < startEpochDay -> DayState.OFF
                day < today && planned -> DayState.MISSED
                else -> DayState.REST
            }
        }
        return WeeklyStreak(streak, max(best, streak), doneThisWeek, targetThisWeek, days)
    }

    fun fullTarget(plannedTrainingDays: Set<Int>, targetOverride: Int): Int =
        if (targetOverride > 0) targetOverride else max(1, plannedTrainingDays.size)

    /** Finished weeks since [startEpochDay] with no saved target yet, each given today's [fullTarget] to save. */
    fun weeksToLock(locked: Map<Long, Int>, fullTarget: Int, startEpochDay: Long, today: Long): Map<Long, Int> =
        generateSequence(Dates.weekStart(startEpochDay)) { it + 7 }
            .takeWhile { it < Dates.weekStart(today) }
            .filter { it !in locked }
            .associateWith { fullTarget }
}

// ---------------------------------------------------------------- Reminders & comebacks

object Nudges {
    /** After this many planned days missed in a row (not excused), reminders pause until a workout or a tag. */
    const val MAX_MISSES = 3

    /** Planned days missed back to back, counting back from yesterday; a workout or an excused day ends the run. */
    fun missedInARow(today: Long, plannedTrainingDays: Set<Int>, workoutDays: Set<Long>, excusedDays: Set<Long>, startEpochDay: Long): Int {
        var count = 0
        var day = today - 1
        while (day >= startEpochDay && day >= today - 14) {
            if (day in workoutDays || day in excusedDays) break
            if (Dates.dayOfWeek(day) in plannedTrainingDays) count++
            day--
        }
        return count
    }

    fun shouldRemind(plannedToday: Boolean, doneToday: Boolean, excusedToday: Boolean, missedInARow: Int): Boolean =
        plannedToday && !doneToday && !excusedToday && missedInARow < MAX_MISSES

    /**
     * True for the first workout back after a sick or injured day since the last workout, or after a reminder
     * back-off: the session then offers a one-tap "Ease in" (one set fewer, never below 2).
     */
    fun welcomeBack(
        today: Long,
        workoutDays: Set<Long>,
        tags: Map<Long, DayTag>,
        plannedTrainingDays: Set<Int>,
        startEpochDay: Long,
    ): Boolean {
        val lastWorkout = workoutDays.filter { it < today }.maxOrNull() ?: return false
        if (workoutDays.contains(today)) return false
        val illSince = (lastWorkout + 1 until today).any { tags[it] == DayTag.SICK || tags[it] == DayTag.INJURED }
        return illSince || missedInARow(today, plannedTrainingDays, workoutDays, tags.keys, startEpochDay) >= MAX_MISSES
    }
}

// ---------------------------------------------------------------- XP & rank

data class RankInfo(val letter: String, val title: String)

data class LevelInfo(
    val totalXp: Int,
    val level: Int,
    val xpIntoLevel: Int,
    val xpForNextLevel: Int,
    val rank: RankInfo,
) {
    val progress: Float get() = if (xpForNextLevel == 0) 0f else xpIntoLevel.toFloat() / xpForNextLevel
}

object Xp {
    const val PER_WORKOUT = 50
    const val PER_SET = 2
    const val PER_PR = 25
    const val PER_BADGE = 100

    /** XP needed to go from [level] to [level] + 1. */
    fun stepFor(level: Int) = 100 + 50 * (level - 1)

    fun levelFor(totalXp: Int): LevelInfo {
        var level = 1
        var remaining = totalXp
        while (remaining >= stepFor(level)) {
            remaining -= stepFor(level)
            level++
        }
        return LevelInfo(totalXp, level, remaining, stepFor(level), rankFor(level))
    }

    fun rankFor(level: Int): RankInfo = when {
        level >= 45 -> RankInfo("S", "Legend")
        level >= 30 -> RankInfo("A", "Elite")
        level >= 20 -> RankInfo("B", "Strong")
        level >= 10 -> RankInfo("C", "Committed")
        level >= 5 -> RankInfo("D", "Regular")
        else -> RankInfo("E", "Starter")
    }

    fun total(workouts: Int, doneSets: Int, prs: Int, badges: Int) =
        workouts * PER_WORKOUT + doneSets * PER_SET + prs * PER_PR + badges * PER_BADGE
}

// ---------------------------------------------------------------- Recovery

data class MuscleRecovery(
    /** 0 = fully recovered, 1 = just trained hard (4+ full working sets). */
    val fatigue: Float,
    val hoursUntilReady: Int,
)

object Recovery {
    const val WINDOW_HOURS = 48f
    /** Number of full working sets on a primary muscle that constitutes a 100 % (48-hour) session stimulus. */
    private const val FULL_SESSION_EFFECTIVE_SETS = 4f
    private val HIGH_SKILL_LOW_REP_IDS = setOf(
        "muscle_up", "pistol_squat", "handstand_push_up", "tuck_planche",
        "pseudo_planche_push_up", "tuck_front_lever", "front_lever_row", "back_lever_tuck", "nordic_curl",
    )

    /**
     * Converts a session's completed sets into realistic per-muscle stimulus weights in (0, 1].
     * - 0 reps / 0 seconds -> 0 stimulus (ignored).
     * - 1 bodyweight rep (e.g. 1 push-up) -> ~0.10 of a working set -> 0.025 stimulus (~1.2 h recovery).
     * - 1 full working set (10 reps or 30 s hold) -> 0.25 primary stimulus (12 h recovery, 25 % fatigue).
     * - 2 full working sets -> 0.50 primary stimulus (24 h recovery, 50 % fatigue).
     * - 4+ full working sets -> 1.00 primary stimulus (48 h recovery, 100 % fatigue).
     * - Warm-up block sets are weighted at 25 % of a main set so jumping jacks never mark calves red for 48 hours.
     */
    fun sessionHits(
        sets: List<SetEntity>,
        exercises: Map<String, ExerciseEntity>,
        sessionFinishedAt: Map<Long, Long>,
    ): List<Triple<Muscle, Long, Float>> {
        val result = mutableListOf<Triple<Muscle, Long, Float>>()
        for ((sessionId, sessionSets) in sets.groupBy { it.sessionId }) {
            val finishedAt = sessionFinishedAt[sessionId] ?: continue
            val stimulusByMuscle = mutableMapOf<Muscle, Float>()
            for (set in sessionSets) {
                if (!set.done) continue
                val ex = exercises[set.exerciseId] ?: continue
                val eff = effectiveSetEffort(set, ex)
                if (eff <= 0f) continue
                val primaryDelta = eff / FULL_SESSION_EFFECTIVE_SETS
                val secondaryDelta = primaryDelta * 0.5f
                for (m in ex.primary) {
                    stimulusByMuscle[m] = (stimulusByMuscle[m] ?: 0f) + primaryDelta
                }
                for (m in ex.secondary) {
                    stimulusByMuscle[m] = (stimulusByMuscle[m] ?: 0f) + secondaryDelta
                }
            }
            for ((muscle, totalStimulus) in stimulusByMuscle) {
                val clamped = totalStimulus.coerceIn(0f, 1f)
                if (clamped >= 0.02f) {
                    result += Triple(muscle, finishedAt, clamped)
                }
            }
        }
        return result
    }

    /**
     * Effective working-set equivalent for a single logged set:
     * - Standard full working set = 1.0f.
     * - 1 rep on a bodyweight exercise = 0.10f (so 1 rep is 1/10th of a set, not a full workout).
     */
    internal fun effectiveSetEffort(set: SetEntity, ex: ExerciseEntity): Float {
        val rawEffort = when (ex.kind) {
            ExerciseKind.HOLD -> {
                val sec = set.seconds ?: 0
                if (sec <= 0) return 0f
                val refSec = if (ex.id in HIGH_SKILL_LOW_REP_IDS) 12f else 30f
                (sec / refSec).coerceIn(0.08f, 1.25f)
            }
            ExerciseKind.WEIGHTED -> {
                val r = set.reps ?: 0
                if (r <= 0) return 0f
                val w = set.weightKg ?: 0f
                // Heavy low-rep sets (3-6 reps with external load) still count as a full working set.
                val refReps = if (w > 0f) 6f else 10f
                (r / refReps).coerceIn(0.15f, 1.25f)
            }
            ExerciseKind.REPS -> {
                val r = set.reps ?: 0
                if (r <= 0) return 0f
                val hasExtraWeight = (set.weightKg ?: 0f) > 0f
                val refReps = when {
                    ex.id in HIGH_SKILL_LOW_REP_IDS -> 4f
                    hasExtraWeight -> 6f
                    else -> 10f
                }
                (r / refReps).coerceIn(0.08f, 1.25f)
            }
        }
        val blockFactor = if (set.block.equals("Warm-up", ignoreCase = true) || ex.id == "jumping_jacks") 0.25f else 1.0f
        val rpeFactor = when (SetEffort.of(set.effort, set.note)) {
            SetEffort.HARD, SetEffort.PAIN -> 1.15f
            SetEffort.EASY -> 0.80f
            SetEffort.OK -> 1.0f
        }
        return (rawEffort * blockFactor * rpeFactor).coerceIn(0f, 1.35f)
    }

    /**
     * [hits] = (muscle, trainedAtMillis, weight) where weight in (0, 1] is the session stimulus
     * (1.0 = full primary session needing 48 h, 0.5 = secondary/moderate session needing 24 h,
     * 0.25 = single working set needing 12 h).
     * Fatigue clears at a constant physiological rate of 1 / 48 per hour; the highest remaining fatigue wins.
     */
    fun compute(hits: List<Triple<Muscle, Long, Float>>, now: Long): Map<Muscle, MuscleRecovery> {
        val result = mutableMapOf<Muscle, MuscleRecovery>()
        for ((muscle, at, weight) in hits) {
            if (weight <= 0f) continue
            val hours = (now - at) / 3_600_000f
            val effectiveWeight = weight.coerceIn(0f, 1f)
            val window = WINDOW_HOURS * effectiveWeight
            if (hours < 0f || hours >= window) continue
            val fatigue = effectiveWeight * (1f - hours / window)
            val ready = (window - hours).toInt().coerceAtLeast(1)
            val prev = result[muscle]
            if (prev == null || fatigue > prev.fatigue) result[muscle] = MuscleRecovery(fatigue, ready)
        }
        return result
    }
}

// ---------------------------------------------------------------- Personal records

object Records {
    /**
     * Comparable score for one set:
     * - WEIGHTED sets use an Epley 1RM model with linear scaling up to 12 reps and logarithmic diminishing
     *   returns above 12 reps so 5×100 kg beats 8×80 kg and 35×20 kg never falsely beats heavy strength sets.
     * - REPS and HOLD sets also credit added external weight (e.g. weighted vest or dip belt on calisthenics)
     *   so progressing from bodyweight to +10 kg is recognized as a personal record.
     */
    fun score(kind: ExerciseKind, reps: Int?, seconds: Int?, weightKg: Float?): Float {
        val extraKg = (weightKg ?: 0f).coerceAtLeast(0f)
        return when (kind) {
            ExerciseKind.REPS -> {
                val r = (reps ?: 0).coerceAtLeast(0)
                if (r == 0) 0f else r.toFloat() + extraKg * 0.35f
            }
            ExerciseKind.HOLD -> {
                val s = (seconds ?: 0).coerceAtLeast(0)
                if (s == 0) 0f else s.toFloat() + extraKg * 1.0f
            }
            ExerciseKind.WEIGHTED -> {
                val r = (reps ?: 0).coerceAtLeast(0)
                if (r == 0) return 0f
                if (extraKg <= 0f) {
                    r / 100f
                } else {
                    val effectiveReps = if (r <= 12) r.toFloat() else 12f + 3f * ln(1f + (r - 12).toFloat())
                    extraKg * (1f + effectiveReps / 30f)
                }
            }
        }
    }

    /**
     * Which sets count as personal records when the whole history of one exercise is replayed in order.
     * [sessions] is oldest first, each holding that session's done sets. The first session has nothing
     * to beat, so it never produces a record; after that, the best set of a session is a record when it
     * beats every set before it. Used when a past session is edited or deleted.
     */
    fun <T> personalRecords(
        kind: ExerciseKind,
        sessions: List<List<T>>,
        reps: (T) -> Int?,
        seconds: (T) -> Int?,
        weightKg: (T) -> Float?,
    ): Set<T> {
        val result = mutableSetOf<T>()
        var best: Float? = null
        for (sets in sessions) {
            if (sets.isEmpty()) continue
            val top = sets.maxBy { score(kind, reps(it), seconds(it), weightKg(it)) }
            val topScore = score(kind, reps(top), seconds(top), weightKg(top))
            val previous = best
            if (previous != null && topScore > previous) result += top
            best = if (previous == null) topScore else max(previous, topScore)
        }
        return result
    }
}

```

### app/src/main/java/com/forgequest/app/domain/Readiness.kt

```kotlin
package com.forgequest.app.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** One stretch of a sleep stage: 0 awake, 1 light, 2 deep, 3 REM. Kept for the hypnogram. */
data class Seg(val stage: Int, val start: Instant, val end: Instant)

/** One sleep record as Health Connect gives it; several apps may write the same night. */
data class SleepPiece(
    val start: Instant,
    val end: Instant,
    val deepMin: Int,
    val remMin: Int,
    val lightMin: Int,
    val awakeMin: Int,
    /** Minutes asleep when the record has no stages. */
    val unstagedMin: Int,
    val fromFitbit: Boolean,
    val segments: List<Seg> = emptyList(),
) {
    val asleepMin get() = deepMin + remMin + lightMin + unstagedMin
}

/**
 * A night after duplicates are dropped and split chunks (2:00–6:00 then 8:00–11:30) are joined. [gaps] are the times up
 * between chunks; they don't count as time in bed.
 */
data class Night(val start: Instant, val end: Instant, val deepMin: Int, val remMin: Int, val lightMin: Int, val awakeMin: Int, val asleepMin: Int, val segments: List<Seg> = emptyList(), val gaps: List<Pair<Instant, Instant>> = emptyList()) {
    val inBedMin get() = ((end.epochSecond - start.epochSecond - gaps.sumOf { it.second.epochSecond - it.first.epochSecond }) / 60).toInt()
}

/** The day's main night plus any naps that ended the same day. */
data class SleepDay(val night: Night, val napMin: Int)

data class Baseline(val mean: Double, val sd: Double, val days: Int)

enum class DayCall { PEAK, READY, STEADY, EASE_OFF, RECOVER }
enum class StrainBand { LIGHT, MODERATE, HIGH, ALL_OUT }

/**
 * Whoop-style recovery, sleep and strain from Fitbit data. Whoop doesn't publish its formulas, so these follow its
 * documented inputs and the published methods behind them:
 * - HRV as ln RMSSD with a 7-day rolling average against the person's own baseline, ignoring changes smaller than
 *   the "smallest worthwhile change" (AIS HRV guidelines; Plews & Buchheit).
 * - Sleep performance = hours slept ÷ hours needed, need = base + strain + sleep debt − naps (Whoop).
 * - Strain = Banister TRIMP (every minute above resting, weighted exponentially by heart-rate reserve), on a 0–21
 *   curve that gets harder to climb (Whoop's scale). Walking counts a little, hard work a lot.
 */
object Readiness {
    private const val JOIN_GAP_SECONDS = 3 * 3600L
    const val BASE_NEED_MIN = 480
    const val MIN_BASELINE_DAYS = 4

    // ---------- Sleep ----------

    /** Keeps one copy of each stretch of sleep (Fitbit's first), joins split chunks, and files each night under the day it ended. */
    fun sleepDays(pieces: List<SleepPiece>, zone: ZoneId): Map<LocalDate, SleepDay> {
        val kept = mutableListOf<SleepPiece>()
        for (p in pieces.sortedWith(compareByDescending<SleepPiece> { it.fromFitbit }.thenBy { it.start })) {
            val dup = kept.any { k ->
                val overlap = minOf(k.end, p.end).epochSecond - maxOf(k.start, p.start).epochSecond
                overlap > 0.5 * (p.end.epochSecond - p.start.epochSecond)
            }
            if (!dup) kept += p
        }
        val groups = mutableListOf<MutableList<SleepPiece>>()
        for (p in kept.sortedBy { it.start }) {
            val last = groups.lastOrNull()
            if (last != null && p.start.epochSecond - last.maxOf { it.end.epochSecond } <= JOIN_GAP_SECONDS) last += p else groups += mutableListOf(p)
        }
        return groups.map { g ->
            Night(g.minOf { it.start }, g.maxOf { it.end }, g.sumOf { it.deepMin }, g.sumOf { it.remMin }, g.sumOf { it.lightMin }, g.sumOf { it.awakeMin }, g.sumOf { it.asleepMin }, g.flatMap { it.segments }.sortedBy { it.start }, gapsBetween(g))
        }.groupBy { it.end.atZone(zone).toLocalDate() }.mapValues { (_, list) ->
            // The biggest sleep that ended that day is the night; the rest were naps.
            val night = list.maxBy { it.asleepMin }
            SleepDay(night, list.filter { it !== night }.sumOf { it.asleepMin })
        }
    }

    /** Stretches of the night covered by none of the chunks. */
    private fun gapsBetween(chunks: List<SleepPiece>): List<Pair<Instant, Instant>> {
        val gaps = mutableListOf<Pair<Instant, Instant>>()
        val sorted = chunks.sortedBy { it.start }
        var coveredTo = sorted.first().end
        for (p in sorted) {
            if (p.start > coveredTo) gaps += coveredTo to p.start
            if (p.end > coveredTo) coveredTo = p.end
        }
        return gaps
    }

    fun nights(pieces: List<SleepPiece>, zone: ZoneId): Map<LocalDate, Night> = sleepDays(pieces, zone).mapValues { it.value.night }

    /** Extra sleep a hard day asks for: about 4 minutes per strain point above 10. */
    fun strainNeedMin(strain: Double): Int = (maxOf(0.0, strain - 10) * 4).roundToInt()

    /** A third of the last three nights' shortfall carries over, capped at two hours. */
    fun debtMin(previousAsleep: List<Int>): Int = (previousAsleep.takeLast(3).sumOf { maxOf(0, BASE_NEED_MIN - it) } / 3.0).roundToInt().coerceAtMost(120)

    fun sleepNeed(strainBefore: Double, previousAsleep: List<Int>, napMin: Int): Int =
        (BASE_NEED_MIN + strainNeedMin(strainBefore) + debtMin(previousAsleep) - napMin).coerceIn(360, 720)

    /** 0–100: hours slept against hours needed, like Whoop's sleep performance. */
    fun sleepPerformance(asleepMin: Int, needMin: Int): Int = if (needMin <= 0) 0 else (100.0 * asleepMin / needMin).roundToInt().coerceIn(0, 100)

    // ---------- Recovery ----------

    fun baseline(values: List<Double>, floorSd: Double): Baseline? {
        if (values.size < MIN_BASELINE_DAYS) return null
        val mean = values.average()
        val denom = (values.size - 1).coerceAtLeast(1)
        val sd = sqrt(values.sumOf { (it - mean) * (it - mean) } / denom)
        return Baseline(mean, maxOf(sd, floorSd), values.size)
    }

    /** ln RMSSD of tonight blended with the 7-day rolling average, which tracks real change better than one night. */
    fun hrvSignal(tonightMs: Double?, last7Ms: List<Double>): Double? {
        val logs = last7Ms.filter { it > 0 }.map { ln(it) }
        val tonight = tonightMs?.takeIf { it > 0 }?.let { ln(it) }
        return when {
            tonight != null && logs.isNotEmpty() -> 0.6 * tonight + 0.4 * logs.average()
            tonight != null -> tonight
            logs.isNotEmpty() -> logs.average()
            else -> null
        }
    }

    /** z-score with a dead zone of half a standard deviation: wiggles inside the normal range count as zero. */
    fun zWithSwc(diff: Double, sd: Double): Double {
        val swc = 0.5 * sd
        return if (abs(diff) <= swc) 0.0 else (diff - (if (diff > 0) swc else -swc)) / sd
    }

    /** 1–99 %. HRV weighs most, then resting heart rate, then sleep, then breathing rate. A normal day lands near 60 %. */
    fun recovery(hrvLn: Double?, rhr: Double?, resp: Double?, sleepPerf: Int?, hrvBase: Baseline?, rhrBase: Baseline?, respBase: Baseline?): Int {
        var sum = 0.0
        var weight = 0.0
        fun add(w: Double, z: Double) { sum += w * z.coerceIn(-3.0, 3.0); weight += w }
        if (hrvLn != null && hrvBase != null) add(0.50, zWithSwc(hrvLn - hrvBase.mean, hrvBase.sd))
        if (rhr != null && rhrBase != null) add(0.25, zWithSwc(rhrBase.mean - rhr, rhrBase.sd))
        if (resp != null && respBase != null) add(0.10, zWithSwc(respBase.mean - resp, respBase.sd))
        if (sleepPerf != null) add(0.15, (sleepPerf - 85) / 15.0)
        val z = if (weight > 0) sum / weight else 0.0
        return (100 / (1 + exp(-(1.4 * z + 0.4)))).roundToInt().coerceIn(1, 99)
    }

    fun call(recovery: Int): DayCall = when {
        recovery >= 85 -> DayCall.PEAK
        recovery >= 67 -> DayCall.READY
        recovery >= 50 -> DayCall.STEADY
        recovery >= 34 -> DayCall.EASE_OFF
        else -> DayCall.RECOVER
    }

    /** Whoop's recovery colours: green 67+, yellow 34–66, red below. */
    fun recoveryZone(recovery: Int): Int = when { recovery >= 67 -> 2; recovery >= 34 -> 1; else -> 0 }

    // ---------- Strain ----------

    /** Tanaka's 208 − 0.7 × age, closer than 220 − age, especially past 40. */
    fun hrMax(age: Int): Int = if (age in 10..100) (208 - 0.7 * age).roundToInt() else 190

    /**
     * Minutes in five heart-rate-reserve zones (20/40/60/75/90 % of the gap between resting and max): light movement,
     * moderate, hard, very hard, max. Gaps over 30 s count as 30 s so a lost signal doesn't inflate them.
     */
    fun zoneMinutes(samples: List<Pair<Instant, Long>>, hrMax: Int, hrRest: Double): DoubleArray {
        val zones = DoubleArray(5)
        val reserve = (hrMax - hrRest).coerceAtLeast(40.0)
        val sorted = samples.sortedBy { it.first }
        for (i in sorted.indices) {
            val next = sorted.getOrNull(i + 1)?.first ?: break
            val seconds = minOf(30L, next.epochSecond - sorted[i].first.epochSecond).coerceAtLeast(0)
            val pct = (sorted[i].second - hrRest) / reserve
            // Same floor as the strain load (20 % of reserve), so the zones explain the number above them.
            val zone = when { pct >= 0.9 -> 4; pct >= 0.75 -> 3; pct >= 0.6 -> 2; pct >= 0.4 -> 1; pct >= 0.2 -> 0; else -> -1 }
            if (zone >= 0) zones[zone] += seconds / 60.0
        }
        return zones
    }

    /**
     * Banister TRIMP: minutes × heart-rate reserve × 0.64·e^(1.92·reserve) (0.86·e^(1.67·reserve) for women). A quiet
     * day with some walking lands near 90, an hour of hard training adds about 140.
     */
    fun trimp(samples: List<Pair<Instant, Long>>, hrMax: Int, hrRest: Double, female: Boolean = false): Double {
        val reserve = (hrMax - hrRest).coerceAtLeast(40.0)
        val (a, b) = if (female) 0.86 to 1.67 else 0.64 to 1.92
        val sorted = samples.sortedBy { it.first }
        var load = 0.0
        for (i in sorted.indices) {
            val next = sorted.getOrNull(i + 1)?.first ?: break
            val minutes = minOf(30L, next.epochSecond - sorted[i].first.epochSecond).coerceAtLeast(0) / 60.0
            val r = ((sorted[i].second - hrRest) / reserve).coerceIn(0.0, 1.0)
            // Sitting a little above resting isn't effort; below 20 % of reserve nothing counts.
            if (r < 0.2) continue
            load += minutes * r * a * exp(b * r)
        }
        return load
    }

    /** 0–21 on a curve that gets harder to climb: a day with an hour of walking about 5–6, a hard training day 15–17. */
    fun strainFromLoad(load: Double): Double = 21 * (1 - exp(-load / 110))

    fun band(strain: Double): StrainBand = when {
        strain >= 18 -> StrainBand.ALL_OUT
        strain >= 14 -> StrainBand.HIGH
        strain >= 10 -> StrainBand.MODERATE
        else -> StrainBand.LIGHT
    }

    /** Today's strain target, matching the call so "go lighter" never comes with a hard target. */
    fun strainTarget(recovery: Int): ClosedFloatingPointRange<Double> = when (call(recovery)) {
        DayCall.PEAK -> 15.0..18.0
        DayCall.READY -> 13.0..16.0
        DayCall.STEADY -> 10.0..13.0
        DayCall.EASE_OFF -> 7.0..10.0
        DayCall.RECOVER -> 4.0..7.0
    }

    fun median(values: List<Double>): Double? = values.sorted().let { if (it.isEmpty()) null else if (it.size % 2 == 1) it[it.size / 2] else (it[it.size / 2 - 1] + it[it.size / 2]) / 2 }
}

```

### app/src/main/java/com/forgequest/app/domain/Trends.kt

```kotlin
package com.forgequest.app.domain

import com.forgequest.app.data.db.ExerciseEntity
import com.forgequest.app.data.db.ReadinessDayEntity
import com.forgequest.app.data.db.SessionEntity
import com.forgequest.app.data.db.SetEntity
import kotlin.math.roundToInt

/** How far back the Progress screen looks. [days] null means everything. */
enum class TrendRange(val days: Int?) { M1(30), M3(91), M6(182), Y1(365), ALL(null);
    fun from(today: Long): Long = days?.let { today - it + 1 } ?: Long.MIN_VALUE
}

data class TrendPoint(val epochDay: Long, val value: Float, val isRecord: Boolean = false)

data class StalledLift(
    val exerciseId: String,
    val exerciseName: String,
    val sessionsCount: Int,
    val spanDays: Int,
    val bestScore: Float,
)

data class FatigueAndPlateauReport(
    val trainingLoadPct: Int,
    val stalledLifts: List<StalledLift>,
    val needsDeload: Boolean,
    val deloadReasons: List<String>,
)

/** Everything the Progress screen shows, computed from sessions, sets, weights and meals. Pure, so it's unit-tested. */
object Trends {

    /** Workouts per ISO week, oldest first, for the [weeks] weeks ending with the current one. */
    fun weeklyCounts(workoutDays: Collection<Long>, weeks: Int, today: Long): List<Int> {
        val thisWeek = Dates.weekStart(today)
        val starts = (weeks - 1 downTo 0).map { thisWeek - 7L * it }
        return starts.map { ws -> workoutDays.count { it in ws..ws + 6 } }
    }

    /** Sets done per week, same shape as [weeklyCounts]. */
    fun weeklySets(sets: List<SetEntity>, sessionDay: Map<Long, Long>, weeks: Int, today: Long): List<Int> {
        val thisWeek = Dates.weekStart(today)
        val starts = (weeks - 1 downTo 0).map { thisWeek - 7L * it }
        val days = sets.mapNotNull { sessionDay[it.sessionId] }
        return starts.map { ws -> days.count { it in ws..ws + 6 } }
    }

    /**
     * This week's sets as a percentage of the average of the previous full weeks that had any training.
     * 100 means "a normal week for you"; the first ever week reads 100 too, so nothing scolds a beginner.
     */
    fun trainingLoad(weeklySets: List<Int>): Int {
        if (weeklySets.isEmpty()) return 0
        val current = weeklySets.last()
        val previous = weeklySets.dropLast(1).filter { it > 0 }
        if (previous.isEmpty()) return if (current > 0) 100 else 0
        return (current * 100f / previous.average().toFloat()).roundToInt()
    }

    /**
     * Best set per training day for one exercise, oldest first, over the whole history. A point is a record when it
     * beats everything before it (the first day never is). Filter by range afterwards so records stay truthful.
     */
    fun bestSetSeries(sets: List<SetEntity>, kind: ExerciseKind, sessionDay: Map<Long, Long>): List<TrendPoint> {
        val byDay = sets.groupBy { sessionDay[it.sessionId] ?: return@groupBy -1L }.filterKeys { it >= 0 }
        var best = Float.NEGATIVE_INFINITY
        var first = true
        return byDay.keys.sorted().map { day ->
            val top = byDay.getValue(day).maxOf { Records.score(kind, it.reps, it.seconds, it.weightKg) }
            val record = !first && top > best
            if (top > best) best = top
            first = false
            TrendPoint(day, top, record)
        }
    }

    /** Trailing [window]-day mean of a daily series (gaps allowed); each point averages the entries within the window. */
    fun movingAverage(points: List<TrendPoint>, window: Int = 7): List<TrendPoint> {
        val sorted = points.sortedBy { it.epochDay }
        return sorted.map { p ->
            val inWindow = sorted.filter { it.epochDay in (p.epochDay - window + 1)..p.epochDay }
            TrendPoint(p.epochDay, inWindow.map { it.value }.average().toFloat())
        }
    }

    enum class DayCell { FUTURE, REST, DONE, RECORD }

    /** 7 rows (Mon..Sun) × [weeks] columns (oldest first) for the consistency heatmap. */
    fun consistencyGrid(workoutDays: Set<Long>, recordDays: Set<Long>, weeks: Int, today: Long): List<List<DayCell>> {
        val thisWeek = Dates.weekStart(today)
        return (0 until 7).map { row ->
            (weeks - 1 downTo 0).map { back ->
                val day = thisWeek - 7L * back + row
                when {
                    day > today -> DayCell.FUTURE
                    day in recordDays -> DayCell.RECORD
                    day in workoutDays -> DayCell.DONE
                    else -> DayCell.REST
                }
            }
        }
    }

    /** First and last value of a series inside the range, or null when the range holds fewer than two points. */
    fun thenNow(series: List<TrendPoint>, from: Long): Pair<Float, Float>? {
        val inRange = series.filter { it.epochDay >= from }
        if (inRange.size < 2) return null
        return inRange.first().value to inRange.last().value
    }

    /** Days per week on which protein reached [goal], oldest week first. Only logged days count. */
    fun proteinDaysPerWeek(nutrition: List<Pair<Long, Float>>, goal: Int, weeks: Int, today: Long): List<Int> {
        val thisWeek = Dates.weekStart(today)
        val hits = nutrition.filter { it.second >= goal }.map { it.first }
        return (weeks - 1 downTo 0).map { back -> val ws = thisWeek - 7L * back; hits.count { it in ws..ws + 6 } }
    }

    data class Lifetime(val workouts: Int, val reps: Int, val minutes: Int, val records: Int, val kgLifted: Float)

    fun lifetime(sessions: List<SessionEntity>, sets: List<SetEntity>): Lifetime = Lifetime(
        workouts = sessions.size,
        reps = sets.sumOf { it.reps ?: 0 },
        minutes = sessions.sumOf { s -> s.finishedAt?.let { ((it - s.startedAt) / 60_000).toInt().coerceIn(1, 300) } ?: 0 },
        records = sets.count { it.isPr },
        kgLifted = sets.sumOf { ((it.weightKg ?: 0f) * (it.reps ?: 0)).toDouble() }.toFloat(),
    )

    /** Exercise ids by how often they appear in done sets, most first. */
    fun topExercises(sets: List<SetEntity>, n: Int): List<String> =
        sets.groupingBy { it.exerciseId }.eachCount().entries.sortedByDescending { it.value }.take(n).map { it.key }

    /** Percent change between two values, rounded, or null when the base is zero. */
    fun percentChange(then: Float, now: Float): Int? = if (then == 0f) null else ((now - then) / then * 100f).roundToInt()

    /**
     * Evaluates recent training history for:
     * 1. Stagnant lifts: exercises trained >= 3 times across >= 14 days in the last 35 days where the best score
     *    of the most recent 2 sessions failed to beat the earlier sessions in that window.
     * 2. Systemic deload triggers: acute weekly volume > 135 % of 4-week chronic average (with >= 18 sets),
     *    >= 3 of the last 4 readiness days below 45 % recovery, or >= 2 simultaneous stalled main lifts.
     */
    fun analyzeFatigueAndPlateaus(
        sessions: List<SessionEntity>,
        doneSets: List<SetEntity>,
        exercises: Map<String, ExerciseEntity>,
        readinessDays: List<ReadinessDayEntity> = emptyList(),
        today: Long,
    ): FatigueAndPlateauReport {
        val sessionDay = sessions.associate { it.id to it.epochDay }
        val weekly = weeklySets(doneSets, sessionDay, 5, today)
        val loadPct = trainingLoad(weekly)
        val currentWeekSets = weekly.lastOrNull() ?: 0

        val windowStart = today - 35
        val recentSets = doneSets.filter { s ->
            val d = sessionDay[s.sessionId] ?: return@filter false
            d in windowStart..today && !s.block.equals("Warm-up", ignoreCase = true)
        }

        val stalled = mutableListOf<StalledLift>()
        for ((exerciseId, exSets) in recentSets.groupBy { it.exerciseId }) {
            val ex = exercises[exerciseId] ?: continue
            if (ex.id == "jumping_jacks") continue
            val series = bestSetSeries(exSets, ex.kind, sessionDay)
            if (series.size < 3) continue
            val span = (series.last().epochDay - series.first().epochDay).toInt()
            if (span < 12) continue
            val earlierBest = series.dropLast(2).maxOf { it.value }
            val recentBest = series.takeLast(2).maxOf { it.value }
            if (recentBest <= earlierBest + 0.01f) {
                stalled += StalledLift(
                    exerciseId = exerciseId,
                    exerciseName = ex.name,
                    sessionsCount = series.size,
                    spanDays = span,
                    bestScore = maxOf(earlierBest, recentBest),
                )
            }
        }

        val reasons = mutableListOf<String>()
        if (loadPct >= 135 && currentWeekSets >= 18) {
            reasons += "acute weekly volume is $loadPct% of your 4-week average ($currentWeekSets sets)"
        }
        val recentReadiness = readinessDays.filter { it.epochDay in (today - 4)..today }
        val lowRecoveryDays = recentReadiness.count { it.recovery in 1..44 }
        if (lowRecoveryDays >= 3) {
            reasons += "recovery has been below 45% on $lowRecoveryDays of the last ${recentReadiness.size} days"
        }
        if (stalled.size >= 2 && loadPct >= 95) {
            reasons += "${stalled.size} lifts have plateaued for 2+ weeks (${stalled.take(3).joinToString(", ") { it.exerciseName }})"
        }

        return FatigueAndPlateauReport(
            trainingLoadPct = loadPct,
            stalledLifts = stalled.sortedByDescending { it.sessionsCount },
            needsDeload = reasons.isNotEmpty(),
            deloadReasons = reasons,
        )
    }
}

```

### app/src/main/java/com/forgequest/app/domain/Badges.kt

```kotlin
package com.forgequest.app.domain

enum class BadgeCategory(val label: String) { CONSISTENCY("Consistency"), SKILL("Skills"), STRENGTH("Strength"), NUTRITION("Nutrition") }

/** Everything badge rules need, computed once from the database. */
data class BadgeStats(
    val workouts: Int = 0,
    val bestStreakWeeks: Int = 0,
    val bestReps: Map<String, Int> = emptyMap(),
    val bestSeconds: Map<String, Int> = emptyMap(),
    val bestWeight: Map<String, Float> = emptyMap(),
    val mealsLogged: Int = 0,
    val mealsScanned: Int = 0,
    val proteinGoalDaysInARow: Int = 0,
)

data class BadgeDef(
    val id: String,
    val title: String,
    val description: String,
    val category: BadgeCategory,
    val unlocked: (BadgeStats) -> Boolean,
)

object Badges {
    private fun reps(id: String, n: Int): (BadgeStats) -> Boolean = { (it.bestReps[id] ?: 0) >= n }
    private fun hold(id: String, s: Int): (BadgeStats) -> Boolean = { (it.bestSeconds[id] ?: 0) >= s }
    private fun load(id: String, kg: Float): (BadgeStats) -> Boolean = { (it.bestWeight[id] ?: 0f) >= kg }

    val all: List<BadgeDef> = listOf(
        BadgeDef("first_workout", "First step", "Finish your first workout", BadgeCategory.CONSISTENCY) { it.workouts >= 1 },
        BadgeDef("workouts_10", "Ten in", "Finish 10 workouts", BadgeCategory.CONSISTENCY) { it.workouts >= 10 },
        BadgeDef("workouts_50", "Half century", "Finish 50 workouts", BadgeCategory.CONSISTENCY) { it.workouts >= 50 },
        BadgeDef("workouts_100", "Hundred club", "Finish 100 workouts", BadgeCategory.CONSISTENCY) { it.workouts >= 100 },
        BadgeDef("streak_4", "One month strong", "Hit your weekly target 4 weeks in a row", BadgeCategory.CONSISTENCY) { it.bestStreakWeeks >= 4 },
        BadgeDef("streak_12", "Quarter of steel", "Hit your weekly target 12 weeks in a row", BadgeCategory.CONSISTENCY) { it.bestStreakWeeks >= 12 },
        BadgeDef("streak_52", "Full year", "Hit your weekly target 52 weeks in a row", BadgeCategory.CONSISTENCY) { it.bestStreakWeeks >= 52 },

        BadgeDef("skill_muscle_up", "First muscle-up", "Log a muscle-up", BadgeCategory.SKILL, reps("muscle_up", 1)),
        BadgeDef("skill_pistol", "Pistol", "Log a pistol squat", BadgeCategory.SKILL, reps("pistol_squat", 1)),
        BadgeDef("skill_l_sit", "L-sit 10 s", "Hold an L-sit for 10 seconds", BadgeCategory.SKILL, hold("l_sit", 10)),
        BadgeDef("skill_handstand", "Handstand 30 s", "Hold a handstand for 30 seconds", BadgeCategory.SKILL, hold("handstand_hold", 30)),
        BadgeDef("skill_tuck_planche", "Tuck planche", "Hold a tuck planche for 10 seconds", BadgeCategory.SKILL, hold("tuck_planche", 10)),
        BadgeDef("skill_tuck_lever", "Tuck front lever", "Hold a tuck front lever for 10 seconds", BadgeCategory.SKILL, hold("tuck_front_lever", 10)),
        BadgeDef("skill_dead_hang", "Iron grip", "Dead hang for 60 seconds", BadgeCategory.SKILL, hold("dead_hang", 60)),

        BadgeDef("str_knee_push_20", "Foundation push", "20 knee push-ups in one set", BadgeCategory.STRENGTH, reps("knee_push_up", 20)),
        BadgeDef("str_aus_pull_15", "Foundation pull", "15 Australian pull-ups in one set", BadgeCategory.STRENGTH, reps("australian_pull_up", 15)),
        BadgeDef("str_push_30", "Push-up 30", "30 push-ups in one set", BadgeCategory.STRENGTH, reps("push_up", 30)),
        BadgeDef("str_pull_1", "First pull-up", "Your first full pull-up", BadgeCategory.STRENGTH, reps("pull_up", 1)),
        BadgeDef("str_pull_10", "Pull-up 10", "10 pull-ups in one set", BadgeCategory.STRENGTH, reps("pull_up", 10)),
        BadgeDef("str_dip_15", "Dip 15", "15 dips in one set", BadgeCategory.STRENGTH, reps("dip", 15)),
        BadgeDef("str_weighted_pull_20", "+20 kg pull-up", "Pull-up with 20 kg added", BadgeCategory.STRENGTH, load("weighted_pull_up", 20f)),
        BadgeDef("str_weighted_dip_30", "+30 kg dip", "Dip with 30 kg added", BadgeCategory.STRENGTH, load("weighted_dip", 30f)),

        BadgeDef("food_first", "First bite", "Log your first meal", BadgeCategory.NUTRITION) { it.mealsLogged >= 1 },
        BadgeDef("food_scan", "Snap & log", "Log a meal with the camera", BadgeCategory.NUTRITION) { it.mealsScanned >= 1 },
        BadgeDef("protein_3", "Protein habit", "Hit your protein goal 3 days in a row", BadgeCategory.NUTRITION) { it.proteinGoalDaysInARow >= 3 },
        BadgeDef("protein_7", "Protein week", "Hit your protein goal 7 days in a row", BadgeCategory.NUTRITION) { it.proteinGoalDaysInARow >= 7 },
        BadgeDef("protein_30", "Protein month", "Hit your protein goal 30 days in a row", BadgeCategory.NUTRITION) { it.proteinGoalDaysInARow >= 30 },
    )

    val byId = all.associateBy { it.id }
}

```

### app/src/main/java/com/forgequest/app/domain/ExerciseSwapEngine.kt

```kotlin
package com.forgequest.app.domain

import com.forgequest.app.data.db.ExerciseEntity
import com.forgequest.app.data.db.PlanItemEntity
import com.forgequest.app.data.plan.PlanDraft
import com.forgequest.app.data.plan.PlanDraftItem
import kotlin.math.max

/** Equipment categories for filtering and biomechanical exercise substitution. */
enum class Equipment(val labelEn: String, val labelAr: String) {
    BODYWEIGHT("Bodyweight", "وزن الجسم"),
    PULL_UP_BAR("Pull-up bar", "عقلة"),
    DIP_BARS_OR_BENCH("Dip bars / rings / bench", "متوازي أو حلقات أو مقعد"),
    DUMBBELLS("Dumbbells", "دمبل"),
    KETTLEBELL("Kettlebell", "كيتل بيل"),
    BARBELL_RACK("Barbell & rack", "بار وراك"),
    CABLES_MACHINES("Cables & gym machines", "أجهزة وكوابل النادي"),
}

/** Parses a comma-separated equipment setting string into a Set<Equipment>. Empty string means "not configured". */
fun parseEquipmentSet(raw: String): Set<Equipment> {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return emptySet()
    if (trimmed.equals("all", ignoreCase = true) || trimmed.equals("full_gym", ignoreCase = true)) {
        return Equipment.entries.toSet()
    }
    EquipmentPreset.entries.firstOrNull { it.id.equals(trimmed, ignoreCase = true) }?.let { return it.equipment }
    val parsed = trimmed.split(",")
        .mapNotNull { token -> runCatching { Equipment.valueOf(token.trim().uppercase()) }.getOrNull() }
        .toSet()
    return if (parsed.isEmpty()) emptySet() else parsed + Equipment.BODYWEIGHT
}

/**
 * Scans coach memory notes and user messages for explicit equipment mentions so the coach knows whether
 * equipment intake has already been answered.
 */
fun inferEquipmentFromText(vararg texts: String): Set<Equipment>? {
    val combined = texts.joinToString("\n").lowercase()
    if (combined.isBlank()) return null

    if (words("""full gym|commercial gym|gym member|all equipment|نادي متكامل|مشترك في نادي|في النادي|جيم كامل""").containsMatchIn(combined)) {
        return Equipment.entries.toSet()
    }
    if (words("""no equipment|bodyweight only|pure bodyweight|nothing at home|بدون معدات|وزن الجسم فقط|وزن جسمي فقط|ما عندي معدات|لا أملك معدات""").containsMatchIn(combined)) {
        return setOf(Equipment.BODYWEIGHT)
    }

    val found = mutableSetOf<Equipment>()
    if (words("""pull-?up bar|chin-?up bar|doorway bar|عقلة|بار عقلة""").hasPositive(combined)) {
        found += Equipment.PULL_UP_BAR
    }
    if (words("""dip bar|parallel bar|rings|gymnastic rings|متوازي|حلقات""").hasPositive(combined)) {
        found += Equipment.DIP_BARS_OR_BENCH
    }
    if (words("""dumbbell|dumbbells|دمبل|دامبل""").hasPositive(combined)) {
        found += Equipment.DUMBBELLS
    }
    if (words("""kettlebell|kettlebells|كيتل بيل|كيتلبيل""").hasPositive(combined)) {
        found += Equipment.KETTLEBELL
    }
    // Not a lone «بار»: «بار عقلة» is a pull-up bar.
    if (words("""barbell|squat rack|power rack|bench press|باربل|بار حديد|بار أولمبي|راك""").hasPositive(combined)) {
        found += Equipment.BARBELL_RACK
    }
    // Not a lone «أجهزة»: it means any device.
    if (words("""cable|cables|lat pulldown|leg press|smith machine|كيبل|كوابل|أجهزة النادي|أجهزة الجيم""").hasPositive(combined)) {
        found += Equipment.CABLES_MACHINES
    }
    if (words("""calisthenics|home workout|at home|في البيت|في المنزل|كاليستنكس""").containsMatchIn(combined) && found.isEmpty()) {
        if (words("""bodyweight|وزن الجسم""").containsMatchIn(combined)) {
            return setOf(Equipment.BODYWEIGHT)
        }
    }
    return if (found.isNotEmpty()) found + Equipment.BODYWEIGHT else null
}

/**
 * Whole words or phrases from [alternatives] ("a|b c"). Spelled out instead of `\b`, which newer Java and
 * JavaScript treat as English-only, so Arabic words would never match.
 */
private fun words(alternatives: String) = Regex("""(?<![\p{L}\p{N}_])($alternatives)(?![\p{L}\p{N}_])""")

private val NEGATION = words("""no|not|don['’]?t|doesn['’]?t|without|never|بدون|لا|ما|ليس|ماعندي|مافي""")
private val CLAUSE_BREAK = Regex("""[.,،;!?؟\n]|(?<![\p{L}])(but|لكن|ولكن|بل|بس)(?![\p{L}])""")

/** True when some match is not negated in its own clause: "no dumbbells" and «ما عندي دمبل» don't count. */
private fun Regex.hasPositive(text: String): Boolean = findAll(text).any { m ->
    val before = text.substring(maxOf(0, m.range.first - 30), m.range.first).split(CLAUSE_BREAK).last()
    !NEGATION.containsMatchIn(before)
}

/** Quick presets for Onboarding and Profile settings. */
enum class EquipmentPreset(
    val id: String,
    val labelEn: String,
    val labelAr: String,
    val equipment: Set<Equipment>,
) {
    FULL_GYM(
        id = "full_gym",
        labelEn = "Full gym",
        labelAr = "نادي متكامل",
        equipment = Equipment.entries.toSet(),
    ),
    HOME_GYM(
        id = "home_gym",
        labelEn = "Home gym (barbell, dumbbells, bar)",
        labelAr = "نادي منزلي (بار، دمبل، عقلة)",
        equipment = setOf(
            Equipment.BODYWEIGHT, Equipment.PULL_UP_BAR, Equipment.DIP_BARS_OR_BENCH,
            Equipment.DUMBBELLS, Equipment.KETTLEBELL, Equipment.BARBELL_RACK,
        ),
    ),
    DUMBBELLS_AND_BAR(
        id = "dumbbells_bar",
        labelEn = "Dumbbells & pull-up bar",
        labelAr = "دمبل وعقلة",
        equipment = setOf(Equipment.BODYWEIGHT, Equipment.PULL_UP_BAR, Equipment.DIP_BARS_OR_BENCH, Equipment.DUMBBELLS),
    ),
    CALISTHENICS_PARK(
        id = "calisthenics",
        labelEn = "Calisthenics (pull-up bar & dip bars)",
        labelAr = "كاليستنكس (عقلة ومتوازي)",
        equipment = setOf(Equipment.BODYWEIGHT, Equipment.PULL_UP_BAR, Equipment.DIP_BARS_OR_BENCH),
    ),
    PULL_UP_BAR_ONLY(
        id = "pull_up_bar",
        labelEn = "Doorway pull-up bar only",
        labelAr = "عقلة منزلية فقط",
        equipment = setOf(Equipment.BODYWEIGHT, Equipment.PULL_UP_BAR),
    ),
    BODYWEIGHT_ONLY(
        id = "bodyweight",
        labelEn = "Pure bodyweight (no equipment)",
        labelAr = "وزن الجسم فقط (بدون معدات)",
        equipment = setOf(Equipment.BODYWEIGHT),
    );

    fun encode(): String = equipment.joinToString(",") { it.name }

    companion object {
        fun fromEncoded(raw: String): EquipmentPreset? {
            if (raw.isBlank()) return null
            byId[raw.trim().lowercase()]?.let { return it }
            val parsed = parseEquipmentSet(raw)
            return entries.firstOrNull { it.equipment == parsed }
        }

        private val byId = entries.associateBy { it.id }
    }
}

enum class MovementPattern(val labelEn: String, val labelAr: String) {
    HORIZONTAL_PUSH("Horizontal push", "دفع أفقي"),
    DIP_PUSH("Dip / decline push", "دفع سفلي / متوازي"),
    VERTICAL_PUSH("Vertical push", "دفع علوي"),
    LATERAL_RAISE("Shoulder abduction", "رفرفة وكتف جانبي"),
    VERTICAL_PULL("Vertical pull", "سحب عمودي"),
    HORIZONTAL_PULL("Horizontal row", "سحب أفقي / تجديف"),
    UPPER_BACK_PULL("Upper back / rear delt", "أعلى الظهر والكتف الخلفي"),
    SQUAT_BILATERAL("Bilateral squat", "قرفصاء ثنائية"),
    SQUAT_UNILATERAL("Unilateral leg / split squat", "أرجل فردية / طعن"),
    HIP_HINGE("Hip hinge / posterior chain", "مفصل الورك / السلسلة الخلفية"),
    KNEE_FLEXION("Knee flexion (hamstrings)", "ثني الركبة (خلفية الفخذ)"),
    ELBOW_FLEXION("Elbow flexion (biceps)", "ثني المرفق (بايسبس)"),
    ELBOW_EXTENSION("Elbow extension (triceps)", "مدّ المرفق (ترايسبس)"),
    CORE_ANTI_EXTENSION("Core anti-extension hold", "ثبات الجذع ومقاومة التمدد"),
    CORE_FLEXION_ROTATION("Core flexion / rotation", "ثني والتواء البطن"),
    ANKLE_FLEXION("Calf / ankle flexion", "بطة وقصبة الساق"),
    CARRY_CONDITIONING("Loaded carry / conditioning", "حمل ولياقة عامة"),
}

enum class JointAngle {
    FLAT_HORIZONTAL,
    INCLINE_UPWARD,
    DECLINE_DOWNWARD,
    OVERHEAD_VERTICAL,
    FRONTAL_ABDUCTION,
    OVERHEAD_PRONATED,
    OVERHEAD_SUPINATED,
    MID_ROW_PRONATED,
    MID_ROW_SUPINATED,
    HIGH_EXTERNAL_ROTATION,
    KNEE_DOMINANT_BILATERAL,
    KNEE_DOMINANT_SPLIT,
    HIP_EXTENSION_SUPINE,
    HIP_HINGE_STANDING,
    HAMSTRING_CURL,
    SAGITTAL_SUPINATED,
    SAGITTAL_NEUTRAL,
    SAGITTAL_TRICEP,
    ISOMETRIC_PLANK,
    SPINAL_FLEXION,
    HANGING_HIP_FLEXION,
    ROTATIONAL,
    PLANTAR_FLEXION,
    DORSI_FLEXION,
    LOCOMOTION,
}

data class BiomechanicalProfile(
    val equipment: Equipment,
    val pattern: MovementPattern,
    val angle: JointAngle,
    val preferredSwaps: List<String> = emptyList(),
)

data class SwapCandidate(
    val exercise: ExerciseEntity,
    val profile: BiomechanicalProfile,
    val score: Int,
    val matchesUserEquipment: Boolean,
)

object ExerciseSwapEngine {

    private fun prof(
        eq: Equipment,
        pat: MovementPattern,
        ang: JointAngle,
        vararg swaps: String,
    ) = BiomechanicalProfile(eq, pat, ang, swaps.toList())

    /** Explicit biomechanical profiles for all 81 built-in exercises in ExerciseCatalog. */
    val catalogProfiles: Map<String, BiomechanicalProfile> = mapOf(
        // Warm-up / conditioning
        "jumping_jacks" to prof(Equipment.BODYWEIGHT, MovementPattern.CARRY_CONDITIONING, JointAngle.LOCOMOTION, "burpee", "calf_raise"),
        "plank_shoulder_taps" to prof(Equipment.BODYWEIGHT, MovementPattern.CORE_ANTI_EXTENSION, JointAngle.ISOMETRIC_PLANK, "plank", "incline_hollow_plank", "hollow_hold"),
        "burpee" to prof(Equipment.BODYWEIGHT, MovementPattern.CARRY_CONDITIONING, JointAngle.LOCOMOTION, "jumping_jacks", "push_up", "squat"),

        // Hemo foundation: core
        "incline_hollow_plank" to prof(Equipment.BODYWEIGHT, MovementPattern.CORE_ANTI_EXTENSION, JointAngle.ISOMETRIC_PLANK, "plank", "hollow_hold", "plank_shoulder_taps"),
        "incline_reverse_plank" to prof(Equipment.BODYWEIGHT, MovementPattern.HIP_HINGE, JointAngle.HIP_EXTENSION_SUPINE, "hip_bridge", "superman_hold", "hip_thrust"),
        "hip_bridge" to prof(Equipment.BODYWEIGHT, MovementPattern.HIP_HINGE, JointAngle.HIP_EXTENSION_SUPINE, "hip_thrust", "kb_swing", "romanian_deadlift", "incline_reverse_plank"),
        "crunch" to prof(Equipment.BODYWEIGHT, MovementPattern.CORE_FLEXION_ROTATION, JointAngle.SPINAL_FLEXION, "cable_crunch", "hanging_knee_raise", "hollow_hold"),
        "superman_hold" to prof(Equipment.BODYWEIGHT, MovementPattern.HIP_HINGE, JointAngle.HIP_EXTENSION_SUPINE, "hip_bridge", "incline_reverse_plank", "romanian_deadlift"),

        // Hemo foundation: upper body
        "knee_scapular_push_up" to prof(Equipment.BODYWEIGHT, MovementPattern.HORIZONTAL_PUSH, JointAngle.FLAT_HORIZONTAL, "knee_push_up", "plank_shoulder_taps", "push_up"),
        "scapular_australian_pull_up" to prof(Equipment.DIP_BARS_OR_BENCH, MovementPattern.HORIZONTAL_PULL, JointAngle.MID_ROW_PRONATED, "australian_pull_up", "dead_hang", "db_row"),
        "bent_leg_bench_dip" to prof(Equipment.DIP_BARS_OR_BENCH, MovementPattern.DIP_PUSH, JointAngle.DECLINE_DOWNWARD, "reverse_grip_bench_dip", "dip", "diamond_push_up", "incline_diamond_knee_push_up"),
        "scapular_australian_chin_up" to prof(Equipment.DIP_BARS_OR_BENCH, MovementPattern.HORIZONTAL_PULL, JointAngle.MID_ROW_SUPINATED, "australian_chin_up", "scapular_australian_pull_up", "chin_up"),
        "knee_push_up" to prof(Equipment.BODYWEIGHT, MovementPattern.HORIZONTAL_PUSH, JointAngle.FLAT_HORIZONTAL, "wide_knee_push_up", "push_up", "incline_diamond_knee_push_up", "db_bench_press"),
        "australian_pull_up" to prof(Equipment.DIP_BARS_OR_BENCH, MovementPattern.HORIZONTAL_PULL, JointAngle.MID_ROW_PRONATED, "db_row", "kb_row", "barbell_row", "seated_cable_row", "negative_pull_up", "pull_up"),
        "reverse_grip_bench_dip" to prof(Equipment.DIP_BARS_OR_BENCH, MovementPattern.DIP_PUSH, JointAngle.DECLINE_DOWNWARD, "bent_leg_bench_dip", "dip", "diamond_push_up"),
        "australian_chin_up" to prof(Equipment.DIP_BARS_OR_BENCH, MovementPattern.HORIZONTAL_PULL, JointAngle.MID_ROW_SUPINATED, "australian_pull_up", "chin_up", "negative_pull_up", "db_row"),
        "wide_knee_push_up" to prof(Equipment.BODYWEIGHT, MovementPattern.HORIZONTAL_PUSH, JointAngle.FLAT_HORIZONTAL, "knee_push_up", "push_up", "db_bench_press"),
        "dead_hang" to prof(Equipment.PULL_UP_BAR, MovementPattern.VERTICAL_PULL, JointAngle.OVERHEAD_PRONATED, "scapular_australian_pull_up", "negative_pull_up", "farmers_carry"),
        "incline_diamond_knee_push_up" to prof(Equipment.BODYWEIGHT, MovementPattern.ELBOW_EXTENSION, JointAngle.INCLINE_UPWARD, "diamond_push_up", "bent_leg_bench_dip", "knee_push_up", "tricep_pushdown"),

        // Hemo foundation: legs
        "half_squat" to prof(Equipment.BODYWEIGHT, MovementPattern.SQUAT_BILATERAL, JointAngle.KNEE_DOMINANT_BILATERAL, "squat", "half_wide_squat", "half_narrow_squat", "goblet_squat"),
        "half_wide_squat" to prof(Equipment.BODYWEIGHT, MovementPattern.SQUAT_BILATERAL, JointAngle.KNEE_DOMINANT_BILATERAL, "half_squat", "squat", "goblet_squat"),
        "half_narrow_squat" to prof(Equipment.BODYWEIGHT, MovementPattern.SQUAT_BILATERAL, JointAngle.KNEE_DOMINANT_BILATERAL, "half_squat", "squat", "lunge"),
        "calf_raise_turned_out" to prof(Equipment.BODYWEIGHT, MovementPattern.ANKLE_FLEXION, JointAngle.PLANTAR_FLEXION, "calf_raise", "standing_calf_raise"),
        "calf_raise" to prof(Equipment.BODYWEIGHT, MovementPattern.ANKLE_FLEXION, JointAngle.PLANTAR_FLEXION, "standing_calf_raise", "calf_raise_turned_out"),

        // Calisthenics basics
        "push_up" to prof(Equipment.BODYWEIGHT, MovementPattern.HORIZONTAL_PUSH, JointAngle.FLAT_HORIZONTAL, "db_bench_press", "bench_press", "weighted_push_up", "diamond_push_up", "pseudo_planche_push_up", "knee_push_up"),
        "diamond_push_up" to prof(Equipment.BODYWEIGHT, MovementPattern.ELBOW_EXTENSION, JointAngle.SAGITTAL_TRICEP, "dip", "skull_crusher", "tricep_pushdown", "push_up", "bent_leg_bench_dip"),
        "pike_push_up" to prof(Equipment.BODYWEIGHT, MovementPattern.VERTICAL_PUSH, JointAngle.OVERHEAD_VERTICAL, "db_shoulder_press", "overhead_press", "kb_press", "handstand_push_up", "incline_db_press"),
        "pull_up" to prof(Equipment.PULL_UP_BAR, MovementPattern.VERTICAL_PULL, JointAngle.OVERHEAD_PRONATED, "chin_up", "lat_pulldown", "weighted_pull_up", "negative_pull_up", "australian_pull_up", "db_row"),
        "chin_up" to prof(Equipment.PULL_UP_BAR, MovementPattern.VERTICAL_PULL, JointAngle.OVERHEAD_SUPINATED, "pull_up", "weighted_chin_up", "lat_pulldown", "australian_chin_up", "db_row"),
        "negative_pull_up" to prof(Equipment.PULL_UP_BAR, MovementPattern.VERTICAL_PULL, JointAngle.OVERHEAD_PRONATED, "australian_pull_up", "lat_pulldown", "pull_up", "chin_up"),
        "dip" to prof(Equipment.DIP_BARS_OR_BENCH, MovementPattern.DIP_PUSH, JointAngle.DECLINE_DOWNWARD, "weighted_dip", "push_up", "diamond_push_up", "db_bench_press", "bench_press", "bent_leg_bench_dip"),
        "squat" to prof(Equipment.BODYWEIGHT, MovementPattern.SQUAT_BILATERAL, JointAngle.KNEE_DOMINANT_BILATERAL, "goblet_squat", "back_squat", "lunge", "bulgarian_split_squat", "leg_press"),
        "lunge" to prof(Equipment.BODYWEIGHT, MovementPattern.SQUAT_UNILATERAL, JointAngle.KNEE_DOMINANT_SPLIT, "db_lunge", "bulgarian_split_squat", "squat", "goblet_squat"),
        "bulgarian_split_squat" to prof(Equipment.DIP_BARS_OR_BENCH, MovementPattern.SQUAT_UNILATERAL, JointAngle.KNEE_DOMINANT_SPLIT, "db_lunge", "lunge", "pistol_squat", "goblet_squat", "back_squat"),
        "pistol_squat" to prof(Equipment.BODYWEIGHT, MovementPattern.SQUAT_UNILATERAL, JointAngle.KNEE_DOMINANT_SPLIT, "bulgarian_split_squat", "db_lunge", "lunge", "goblet_squat"),
        "nordic_curl" to prof(Equipment.BODYWEIGHT, MovementPattern.KNEE_FLEXION, JointAngle.HAMSTRING_CURL, "leg_curl", "romanian_deadlift", "hip_bridge", "kb_swing"),
        "plank" to prof(Equipment.BODYWEIGHT, MovementPattern.CORE_ANTI_EXTENSION, JointAngle.ISOMETRIC_PLANK, "hollow_hold", "incline_hollow_plank", "plank_shoulder_taps", "l_sit"),
        "hollow_hold" to prof(Equipment.BODYWEIGHT, MovementPattern.CORE_ANTI_EXTENSION, JointAngle.ISOMETRIC_PLANK, "plank", "l_sit", "incline_hollow_plank", "hanging_knee_raise"),
        "hanging_knee_raise" to prof(Equipment.PULL_UP_BAR, MovementPattern.CORE_FLEXION_ROTATION, JointAngle.HANGING_HIP_FLEXION, "hanging_leg_raise", "crunch", "hollow_hold", "l_sit"),
        "hanging_leg_raise" to prof(Equipment.PULL_UP_BAR, MovementPattern.CORE_FLEXION_ROTATION, JointAngle.HANGING_HIP_FLEXION, "hanging_knee_raise", "l_sit", "hollow_hold", "cable_crunch"),

        // Calisthenics skills
        "muscle_up" to prof(Equipment.PULL_UP_BAR, MovementPattern.VERTICAL_PULL, JointAngle.OVERHEAD_PRONATED, "weighted_pull_up", "pull_up", "dip"),
        "l_sit" to prof(Equipment.DIP_BARS_OR_BENCH, MovementPattern.CORE_ANTI_EXTENSION, JointAngle.ISOMETRIC_PLANK, "hollow_hold", "hanging_leg_raise", "plank"),
        "handstand_hold" to prof(Equipment.BODYWEIGHT, MovementPattern.VERTICAL_PUSH, JointAngle.OVERHEAD_VERTICAL, "pike_push_up", "handstand_push_up", "overhead_press", "db_shoulder_press"),
        "handstand_push_up" to prof(Equipment.BODYWEIGHT, MovementPattern.VERTICAL_PUSH, JointAngle.OVERHEAD_VERTICAL, "pike_push_up", "overhead_press", "db_shoulder_press", "kb_press"),
        "tuck_planche" to prof(Equipment.BODYWEIGHT, MovementPattern.HORIZONTAL_PUSH, JointAngle.FLAT_HORIZONTAL, "pseudo_planche_push_up", "push_up", "dip"),
        "pseudo_planche_push_up" to prof(Equipment.BODYWEIGHT, MovementPattern.HORIZONTAL_PUSH, JointAngle.FLAT_HORIZONTAL, "push_up", "weighted_push_up", "tuck_planche", "dip", "db_bench_press"),
        "tuck_front_lever" to prof(Equipment.PULL_UP_BAR, MovementPattern.HORIZONTAL_PULL, JointAngle.MID_ROW_PRONATED, "front_lever_row", "pull_up", "australian_pull_up", "barbell_row"),
        "front_lever_row" to prof(Equipment.PULL_UP_BAR, MovementPattern.HORIZONTAL_PULL, JointAngle.MID_ROW_PRONATED, "tuck_front_lever", "barbell_row", "db_row", "australian_pull_up", "pull_up"),
        "back_lever_tuck" to prof(Equipment.PULL_UP_BAR, MovementPattern.UPPER_BACK_PULL, JointAngle.HIGH_EXTERNAL_ROTATION, "tuck_front_lever", "dip", "pseudo_planche_push_up"),

        // Weighted calisthenics
        "weighted_pull_up" to prof(Equipment.PULL_UP_BAR, MovementPattern.VERTICAL_PULL, JointAngle.OVERHEAD_PRONATED, "pull_up", "weighted_chin_up", "lat_pulldown", "chin_up"),
        "weighted_chin_up" to prof(Equipment.PULL_UP_BAR, MovementPattern.VERTICAL_PULL, JointAngle.OVERHEAD_SUPINATED, "chin_up", "weighted_pull_up", "lat_pulldown", "pull_up"),
        "weighted_dip" to prof(Equipment.DIP_BARS_OR_BENCH, MovementPattern.DIP_PUSH, JointAngle.DECLINE_DOWNWARD, "dip", "bench_press", "db_bench_press", "weighted_push_up"),
        "weighted_push_up" to prof(Equipment.BODYWEIGHT, MovementPattern.HORIZONTAL_PUSH, JointAngle.FLAT_HORIZONTAL, "push_up", "bench_press", "db_bench_press", "pseudo_planche_push_up"),

        // Gym (Barbell, Dumbbells, Cables & Machines)
        "bench_press" to prof(Equipment.BARBELL_RACK, MovementPattern.HORIZONTAL_PUSH, JointAngle.FLAT_HORIZONTAL, "db_bench_press", "weighted_push_up", "push_up", "dip", "pseudo_planche_push_up"),
        "incline_db_press" to prof(Equipment.DUMBBELLS, MovementPattern.HORIZONTAL_PUSH, JointAngle.INCLINE_UPWARD, "db_bench_press", "bench_press", "pike_push_up", "push_up", "incline_diamond_knee_push_up"),
        "overhead_press" to prof(Equipment.BARBELL_RACK, MovementPattern.VERTICAL_PUSH, JointAngle.OVERHEAD_VERTICAL, "db_shoulder_press", "kb_press", "pike_push_up", "handstand_push_up"),
        "lateral_raise" to prof(Equipment.DUMBBELLS, MovementPattern.LATERAL_RAISE, JointAngle.FRONTAL_ABDUCTION, "pike_push_up", "db_shoulder_press", "face_pull", "plank_shoulder_taps"),
        "face_pull" to prof(Equipment.CABLES_MACHINES, MovementPattern.UPPER_BACK_PULL, JointAngle.HIGH_EXTERNAL_ROTATION, "scapular_australian_pull_up", "australian_pull_up", "db_row", "barbell_row"),
        "barbell_row" to prof(Equipment.BARBELL_RACK, MovementPattern.HORIZONTAL_PULL, JointAngle.MID_ROW_PRONATED, "db_row", "kb_row", "seated_cable_row", "australian_pull_up", "pull_up"),
        "lat_pulldown" to prof(Equipment.CABLES_MACHINES, MovementPattern.VERTICAL_PULL, JointAngle.OVERHEAD_PRONATED, "pull_up", "chin_up", "negative_pull_up", "australian_pull_up", "db_row"),
        "seated_cable_row" to prof(Equipment.CABLES_MACHINES, MovementPattern.HORIZONTAL_PULL, JointAngle.MID_ROW_PRONATED, "db_row", "barbell_row", "kb_row", "australian_pull_up"),
        "deadlift" to prof(Equipment.BARBELL_RACK, MovementPattern.HIP_HINGE, JointAngle.HIP_HINGE_STANDING, "romanian_deadlift", "kb_deadlift", "kb_swing", "hip_thrust", "hip_bridge"),
        "romanian_deadlift" to prof(Equipment.BARBELL_RACK, MovementPattern.HIP_HINGE, JointAngle.HIP_HINGE_STANDING, "kb_deadlift", "deadlift", "kb_swing", "hip_thrust", "nordic_curl", "hip_bridge"),
        "back_squat" to prof(Equipment.BARBELL_RACK, MovementPattern.SQUAT_BILATERAL, JointAngle.KNEE_DOMINANT_BILATERAL, "goblet_squat", "leg_press", "bulgarian_split_squat", "db_lunge", "squat"),
        "leg_press" to prof(Equipment.CABLES_MACHINES, MovementPattern.SQUAT_BILATERAL, JointAngle.KNEE_DOMINANT_BILATERAL, "back_squat", "goblet_squat", "bulgarian_split_squat", "db_lunge", "squat"),
        "leg_extension" to prof(Equipment.CABLES_MACHINES, MovementPattern.SQUAT_BILATERAL, JointAngle.KNEE_DOMINANT_BILATERAL, "goblet_squat", "bulgarian_split_squat", "squat", "lunge", "pistol_squat"),
        "leg_curl" to prof(Equipment.CABLES_MACHINES, MovementPattern.KNEE_FLEXION, JointAngle.HAMSTRING_CURL, "nordic_curl", "romanian_deadlift", "kb_deadlift", "hip_bridge", "kb_swing"),
        "hip_thrust" to prof(Equipment.BARBELL_RACK, MovementPattern.HIP_HINGE, JointAngle.HIP_EXTENSION_SUPINE, "hip_bridge", "kb_swing", "romanian_deadlift", "kb_deadlift"),
        "standing_calf_raise" to prof(Equipment.CABLES_MACHINES, MovementPattern.ANKLE_FLEXION, JointAngle.PLANTAR_FLEXION, "calf_raise", "calf_raise_turned_out"),
        "bicep_curl" to prof(Equipment.DUMBBELLS, MovementPattern.ELBOW_FLEXION, JointAngle.SAGITTAL_SUPINATED, "hammer_curl", "chin_up", "australian_chin_up"),
        "hammer_curl" to prof(Equipment.DUMBBELLS, MovementPattern.ELBOW_FLEXION, JointAngle.SAGITTAL_NEUTRAL, "bicep_curl", "chin_up", "australian_chin_up", "pull_up"),
        "tricep_pushdown" to prof(Equipment.CABLES_MACHINES, MovementPattern.ELBOW_EXTENSION, JointAngle.SAGITTAL_TRICEP, "skull_crusher", "diamond_push_up", "dip", "bent_leg_bench_dip"),
        "skull_crusher" to prof(Equipment.BARBELL_RACK, MovementPattern.ELBOW_EXTENSION, JointAngle.SAGITTAL_TRICEP, "tricep_pushdown", "diamond_push_up", "dip", "bent_leg_bench_dip"),
        "shrug" to prof(Equipment.DUMBBELLS, MovementPattern.UPPER_BACK_PULL, JointAngle.HIGH_EXTERNAL_ROTATION, "farmers_carry", "face_pull", "scapular_australian_pull_up"),
        "cable_crunch" to prof(Equipment.CABLES_MACHINES, MovementPattern.CORE_FLEXION_ROTATION, JointAngle.SPINAL_FLEXION, "crunch", "hanging_knee_raise", "hanging_leg_raise", "hollow_hold"),
        "tibialis_raise" to prof(Equipment.BODYWEIGHT, MovementPattern.ANKLE_FLEXION, JointAngle.DORSI_FLEXION, "calf_raise", "calf_raise_turned_out"),

        // Kettlebells and dumbbells at home
        "kb_swing" to prof(Equipment.KETTLEBELL, MovementPattern.HIP_HINGE, JointAngle.HIP_HINGE_STANDING, "kb_deadlift", "romanian_deadlift", "hip_bridge", "hip_thrust"),
        "goblet_squat" to prof(Equipment.DUMBBELLS, MovementPattern.SQUAT_BILATERAL, JointAngle.KNEE_DOMINANT_BILATERAL, "back_squat", "bulgarian_split_squat", "db_lunge", "squat"),
        "kb_deadlift" to prof(Equipment.KETTLEBELL, MovementPattern.HIP_HINGE, JointAngle.HIP_HINGE_STANDING, "romanian_deadlift", "deadlift", "kb_swing", "hip_bridge"),
        "kb_clean" to prof(Equipment.KETTLEBELL, MovementPattern.HIP_HINGE, JointAngle.HIP_HINGE_STANDING, "kb_swing", "kb_snatch", "db_shoulder_press"),
        "kb_press" to prof(Equipment.KETTLEBELL, MovementPattern.VERTICAL_PUSH, JointAngle.OVERHEAD_VERTICAL, "db_shoulder_press", "overhead_press", "pike_push_up"),
        "kb_snatch" to prof(Equipment.KETTLEBELL, MovementPattern.VERTICAL_PUSH, JointAngle.OVERHEAD_VERTICAL, "kb_clean", "kb_swing", "db_shoulder_press", "burpee"),
        "kb_row" to prof(Equipment.KETTLEBELL, MovementPattern.HORIZONTAL_PULL, JointAngle.MID_ROW_PRONATED, "db_row", "barbell_row", "australian_pull_up", "seated_cable_row"),
        "turkish_get_up" to prof(Equipment.KETTLEBELL, MovementPattern.CARRY_CONDITIONING, JointAngle.LOCOMOTION, "plank_shoulder_taps", "db_shoulder_press", "plank"),
        "farmers_carry" to prof(Equipment.DUMBBELLS, MovementPattern.CARRY_CONDITIONING, JointAngle.LOCOMOTION, "dead_hang", "shrug", "plank"),
        "db_bench_press" to prof(Equipment.DUMBBELLS, MovementPattern.HORIZONTAL_PUSH, JointAngle.FLAT_HORIZONTAL, "bench_press", "push_up", "weighted_push_up", "incline_db_press", "dip"),
        "db_shoulder_press" to prof(Equipment.DUMBBELLS, MovementPattern.VERTICAL_PUSH, JointAngle.OVERHEAD_VERTICAL, "overhead_press", "kb_press", "pike_push_up", "handstand_push_up"),
        "db_row" to prof(Equipment.DUMBBELLS, MovementPattern.HORIZONTAL_PULL, JointAngle.MID_ROW_PRONATED, "barbell_row", "kb_row", "australian_pull_up", "seated_cable_row", "pull_up"),
        "db_lunge" to prof(Equipment.DUMBBELLS, MovementPattern.SQUAT_UNILATERAL, JointAngle.KNEE_DOMINANT_SPLIT, "bulgarian_split_squat", "lunge", "goblet_squat", "back_squat"),
        "russian_twist" to prof(Equipment.BODYWEIGHT, MovementPattern.CORE_FLEXION_ROTATION, JointAngle.ROTATIONAL, "crunch", "plank_shoulder_taps", "hanging_knee_raise"),
    )

    fun profileFor(ex: ExerciseEntity): BiomechanicalProfile {
        catalogProfiles[ex.id]?.let { return it }
        val lower = (ex.id + " " + ex.name).lowercase()
        val eq = when {
            "cable" in lower || "machine" in lower || "pulldown" in lower || "leg_press" in lower || "pushdown" in lower -> Equipment.CABLES_MACHINES
            "barbell" in lower || "bench_press" in lower || "back_squat" in lower || "deadlift" in lower -> Equipment.BARBELL_RACK
            "kb_" in lower || "kettlebell" in lower -> Equipment.KETTLEBELL
            "db_" in lower || "dumbbell" in lower || "curl" in lower || "lateral" in lower -> Equipment.DUMBBELLS
            "pull_up" in lower || "chin_up" in lower || "hang" in lower || "lever" in lower || "muscle_up" in lower -> Equipment.PULL_UP_BAR
            "dip" in lower || "australian" in lower || "row" in lower -> Equipment.DIP_BARS_OR_BENCH
            ex.kind == ExerciseKind.WEIGHTED -> Equipment.DUMBBELLS
            else -> Equipment.BODYWEIGHT
        }
        val mainMuscle = ex.primary.firstOrNull()
        val pattern = when {
            "pike" in lower || "handstand" in lower || "overhead" in lower || "shoulder_press" in lower -> MovementPattern.VERTICAL_PUSH
            "incline" in lower && "push" in lower -> MovementPattern.HORIZONTAL_PUSH
            "dip" in lower -> MovementPattern.DIP_PUSH
            "push_up" in lower || "bench" in lower || "planche" in lower || mainMuscle == Muscle.CHEST -> MovementPattern.HORIZONTAL_PUSH
            "pull_up" in lower || "chin_up" in lower || "pulldown" in lower -> MovementPattern.VERTICAL_PULL
            "row" in lower || "australian" in lower || "lever" in lower || mainMuscle == Muscle.UPPER_BACK -> MovementPattern.HORIZONTAL_PULL
            "split" in lower || "lunge" in lower || "pistol" in lower -> MovementPattern.SQUAT_UNILATERAL
            "squat" in lower || "leg_press" in lower || mainMuscle == Muscle.QUADS -> MovementPattern.SQUAT_BILATERAL
            "curl" in lower && mainMuscle == Muscle.HAMSTRINGS -> MovementPattern.KNEE_FLEXION
            "deadlift" in lower || "bridge" in lower || "thrust" in lower || "swing" in lower || mainMuscle == Muscle.GLUTES || mainMuscle == Muscle.HAMSTRINGS || mainMuscle == Muscle.LOWER_BACK -> MovementPattern.HIP_HINGE
            mainMuscle == Muscle.BICEPS -> MovementPattern.ELBOW_FLEXION
            mainMuscle == Muscle.TRICEPS -> MovementPattern.ELBOW_EXTENSION
            ex.kind == ExerciseKind.HOLD && (mainMuscle == Muscle.ABS || mainMuscle == Muscle.OBLIQUES) -> MovementPattern.CORE_ANTI_EXTENSION
            mainMuscle == Muscle.ABS || mainMuscle == Muscle.OBLIQUES -> MovementPattern.CORE_FLEXION_ROTATION
            mainMuscle == Muscle.CALVES || mainMuscle == Muscle.TIBIALIS -> MovementPattern.ANKLE_FLEXION
            mainMuscle == Muscle.SHOULDERS -> MovementPattern.VERTICAL_PUSH
            else -> MovementPattern.CARRY_CONDITIONING
        }
        val angle = when (pattern) {
            MovementPattern.HORIZONTAL_PUSH -> if ("incline" in lower) JointAngle.INCLINE_UPWARD else JointAngle.FLAT_HORIZONTAL
            MovementPattern.DIP_PUSH -> JointAngle.DECLINE_DOWNWARD
            MovementPattern.VERTICAL_PUSH -> JointAngle.OVERHEAD_VERTICAL
            MovementPattern.LATERAL_RAISE -> JointAngle.FRONTAL_ABDUCTION
            MovementPattern.VERTICAL_PULL -> if ("chin" in lower) JointAngle.OVERHEAD_SUPINATED else JointAngle.OVERHEAD_PRONATED
            MovementPattern.HORIZONTAL_PULL -> if ("chin" in lower) JointAngle.MID_ROW_SUPINATED else JointAngle.MID_ROW_PRONATED
            MovementPattern.UPPER_BACK_PULL -> JointAngle.HIGH_EXTERNAL_ROTATION
            MovementPattern.SQUAT_BILATERAL -> JointAngle.KNEE_DOMINANT_BILATERAL
            MovementPattern.SQUAT_UNILATERAL -> JointAngle.KNEE_DOMINANT_SPLIT
            MovementPattern.HIP_HINGE -> if ("bridge" in lower || "thrust" in lower) JointAngle.HIP_EXTENSION_SUPINE else JointAngle.HIP_HINGE_STANDING
            MovementPattern.KNEE_FLEXION -> JointAngle.HAMSTRING_CURL
            MovementPattern.ELBOW_FLEXION -> JointAngle.SAGITTAL_SUPINATED
            MovementPattern.ELBOW_EXTENSION -> JointAngle.SAGITTAL_TRICEP
            MovementPattern.CORE_ANTI_EXTENSION -> JointAngle.ISOMETRIC_PLANK
            MovementPattern.CORE_FLEXION_ROTATION -> JointAngle.SPINAL_FLEXION
            MovementPattern.ANKLE_FLEXION -> JointAngle.PLANTAR_FLEXION
            MovementPattern.CARRY_CONDITIONING -> JointAngle.LOCOMOTION
        }
        return BiomechanicalProfile(eq, pattern, angle)
    }

    fun rankSubstitutes(
        source: ExerciseEntity,
        library: List<ExerciseEntity>,
        userEquipment: Set<Equipment> = Equipment.entries.toSet(),
    ): List<SwapCandidate> {
        val srcProf = profileFor(source)
        val effectiveEquipment = userEquipment.ifEmpty { Equipment.entries.toSet() } + Equipment.BODYWEIGHT
        return library
            .filter { it.id != source.id }
            .map { candidate ->
                val candProf = profileFor(candidate)
                val patternPts = when {
                    candProf.pattern == srcProf.pattern -> 45
                    areSynergisticPatterns(srcProf.pattern, candProf.pattern) -> 18
                    else -> 0
                }
                val anglePts = if (candProf.angle == srcProf.angle) 20 else 0
                val primaryPts = (20f * jaccard(source.primary, candidate.primary)).toInt()
                val secondaryPts = (10f * jaccard(source.primary + source.secondary, candidate.primary + candidate.secondary)).toInt()
                val kindPts = if (source.kind == candidate.kind) 5 else 0
                val curatedIdx = srcProf.preferredSwaps.indexOf(candidate.id)
                val curatedBonus = if (curatedIdx >= 0) max(3, 15 - curatedIdx * 2) else 0
                val totalScore = patternPts + anglePts + primaryPts + secondaryPts + kindPts + curatedBonus
                val hasEq = candProf.equipment in effectiveEquipment
                SwapCandidate(
                    exercise = candidate,
                    profile = candProf,
                    score = totalScore,
                    matchesUserEquipment = hasEq,
                )
            }
            .sortedWith(
                compareByDescending<SwapCandidate> { it.matchesUserEquipment && it.score >= 45 }
                    .thenByDescending { it.score }
                    .thenBy { it.exercise.name }
            )
    }

    private fun areSynergisticPatterns(a: MovementPattern, b: MovementPattern): Boolean {
        val pairs = setOf(
            MovementPattern.HORIZONTAL_PUSH to MovementPattern.DIP_PUSH,
            MovementPattern.DIP_PUSH to MovementPattern.ELBOW_EXTENSION,
            MovementPattern.VERTICAL_PUSH to MovementPattern.LATERAL_RAISE,
            MovementPattern.VERTICAL_PULL to MovementPattern.HORIZONTAL_PULL,
            MovementPattern.HORIZONTAL_PULL to MovementPattern.UPPER_BACK_PULL,
            MovementPattern.SQUAT_BILATERAL to MovementPattern.SQUAT_UNILATERAL,
            MovementPattern.HIP_HINGE to MovementPattern.KNEE_FLEXION,
            MovementPattern.CORE_ANTI_EXTENSION to MovementPattern.CORE_FLEXION_ROTATION,
        )
        return (a to b) in pairs || (b to a) in pairs
    }

    private fun <T> jaccard(a: List<T>, b: List<T>): Float {
        if (a.isEmpty() && b.isEmpty()) return 0f
        val sa = a.toSet()
        val sb = b.toSet()
        val inter = sa.intersect(sb).size
        val union = sa.union(sb).size
        return if (union == 0) 0f else inter.toFloat() / union
    }

    fun translatePlanItem(oldItem: PlanItemEntity, newExercise: ExerciseEntity): PlanItemEntity {
        return when (newExercise.kind) {
            ExerciseKind.HOLD -> oldItem.copy(
                exerciseId = newExercise.id,
                seconds = oldItem.seconds ?: 30,
                repsMin = null,
                repsMax = null,
                weightKg = null,
            )
            ExerciseKind.REPS -> {
                val baseMin = oldItem.repsMin ?: 10
                val baseMax = oldItem.repsMax ?: max(baseMin, 12)
                val bumpedMin = if (oldItem.weightKg != null && oldItem.weightKg > 0f) max(8, baseMin + 2) else baseMin
                val bumpedMax = if (oldItem.weightKg != null && oldItem.weightKg > 0f) max(12, baseMax + 2) else baseMax
                oldItem.copy(
                    exerciseId = newExercise.id,
                    repsMin = bumpedMin,
                    repsMax = bumpedMax,
                    seconds = null,
                    weightKg = null,
                )
            }
            ExerciseKind.WEIGHTED -> oldItem.copy(
                exerciseId = newExercise.id,
                repsMin = oldItem.repsMin ?: 8,
                repsMax = oldItem.repsMax ?: 12,
                seconds = null,
                // Never carry a load across exercises (100 kg back squat is not a 100 kg goblet squat): the first
                // session starts from the new exercise's own history, like translateDraftItem.
                weightKg = 0f,
                restSeconds = max(oldItem.restSeconds, 90),
            )
        }
    }

    fun adaptPlanToEquipment(
        draft: PlanDraft,
        library: Map<String, ExerciseEntity>,
        userEquipment: Set<Equipment>,
    ): Pair<PlanDraft, Int> {
        val allowed = userEquipment.ifEmpty { return draft to 0 } + Equipment.BODYWEIGHT
        if (allowed.containsAll(Equipment.entries)) return draft to 0
        val allExercises = library.values.toList()
        var swapsCount = 0
        val adaptedDays = draft.days.map { day ->
            if (day.isRest || day.items.isEmpty()) return@map day
            val usedInDay = day.items.map { it.exerciseId }.toMutableSet()
            val newItems = day.items.map { item ->
                val ex = library[item.exerciseId] ?: ExerciseEntity(
                    id = item.exerciseId,
                    name = item.name ?: item.exerciseId,
                    kind = runCatching { ExerciseKind.valueOf(item.kind ?: "") }.getOrDefault(
                        if (item.seconds != null) ExerciseKind.HOLD else if (item.weightKg != null) ExerciseKind.WEIGHTED else ExerciseKind.REPS
                    ),
                    primary = item.muscles.mapNotNull { m -> runCatching { Muscle.valueOf(m) }.getOrNull() }.take(2),
                    secondary = emptyList(),
                )
                val prof = profileFor(ex)
                if (prof.equipment in allowed) return@map item
                val ranked = rankSubstitutes(ex, allExercises, allowed)
                val replacement = ranked.firstOrNull { it.matchesUserEquipment && it.exercise.id !in usedInDay }
                    ?: ranked.firstOrNull { it.matchesUserEquipment }
                    ?: return@map item
                swapsCount++
                usedInDay += replacement.exercise.id
                translateDraftItem(item, replacement.exercise)
            }
            day.copy(items = newItems)
        }
        return draft.copy(days = adaptedDays) to swapsCount
    }

    fun translateDraftItem(item: PlanDraftItem, target: ExerciseEntity): PlanDraftItem = when (target.kind) {
        ExerciseKind.HOLD -> item.copy(
            exerciseId = target.id,
            name = null,
            kind = null,
            muscles = emptyList(),
            seconds = item.seconds ?: 30,
            repsMin = null,
            repsMax = null,
            weightKg = null,
        )
        ExerciseKind.REPS -> item.copy(
            exerciseId = target.id,
            name = null,
            kind = null,
            muscles = emptyList(),
            repsMin = item.repsMin ?: 10,
            repsMax = item.repsMax ?: 12,
            seconds = null,
            weightKg = null,
        )
        ExerciseKind.WEIGHTED -> item.copy(
            exerciseId = target.id,
            name = null,
            kind = null,
            muscles = emptyList(),
            repsMin = item.repsMin ?: 8,
            repsMax = item.repsMax ?: 12,
            seconds = null,
            weightKg = null,
            restSeconds = max(item.restSeconds, 90),
        )
    }
}
```

### app/src/main/java/com/forgequest/app/domain/Effort.kt

```kotlin
package com.forgequest.app.domain

/** How a set felt, from the one-tap chips under a finished set. Not tapping counts as OK. */
enum class SetEffort {
    EASY, OK, HARD, PAIN;

    companion object {
        /** Reads the stored name; empty or unknown is OK. */
        fun parse(raw: String?): SetEffort = entries.firstOrNull { it.name == raw } ?: OK

        // The one word list for notes: Progression and Recovery both read effort through [of].
        private val strainWords = listOf("rir 0", "rpe 10", "fail", "صعب", "فشل")
        private val painWords = listOf("pain", "twinge", "hurt", "ألم")
        private val easyWords = listOf("easy", "rir 3", "سهل")

        /**
         * The effort of a set, from its chip or, for sets logged before the chips existed, from words in its note.
         * A tapped chip always wins over the note.
         */
        fun of(effort: String, note: String): SetEffort {
            if (effort.isNotBlank()) return parse(effort)
            val n = note.lowercase()
            return when {
                painWords.any { it in n } -> PAIN
                strainWords.any { it in n } -> HARD
                easyWords.any { it in n } -> EASY
                else -> OK
            }
        }
    }
}

```

### app/src/main/java/com/forgequest/app/domain/Muscle.kt

```kotlin
package com.forgequest.app.domain

/**
 * Muscle groups the app tracks. [slug] matches the region names in assets/body_map.json
 * so recovery colors can be painted straight onto the anatomy art.
 */
enum class Muscle(val slug: String, val label: String) {
    CHEST("chest", "Chest"),
    SHOULDERS("deltoids", "Shoulders"),
    BICEPS("biceps", "Biceps"),
    TRICEPS("triceps", "Triceps"),
    FOREARMS("forearm", "Forearms"),
    ABS("abs", "Abs"),
    OBLIQUES("obliques", "Obliques"),
    TRAPS("trapezius", "Traps"),
    UPPER_BACK("upper-back", "Lats & upper back"),
    LOWER_BACK("lower-back", "Lower back"),
    GLUTES("gluteal", "Glutes"),
    QUADS("quadriceps", "Quads"),
    HAMSTRINGS("hamstring", "Hamstrings"),
    ADDUCTORS("adductors", "Adductors"),
    CALVES("calves", "Calves"),
    TIBIALIS("tibialis", "Shins");

    companion object {
        private val bySlug = entries.associateBy { it.slug }
        fun fromSlug(slug: String): Muscle? = bySlug[slug]
    }
}

/** How a set is measured. */
enum class ExerciseKind {
    /** Bodyweight reps, e.g. push-ups. */
    REPS,

    /** Timed hold in seconds, e.g. plank, dead hang. */
    HOLD,

    /** Reps with added or external load in kg, e.g. weighted dips, bench press. */
    WEIGHTED,
}

```

### app/src/main/java/com/forgequest/app/domain/Units.kt

```kotlin
package com.forgequest.app.domain

import java.util.Locale

/** Display unit for weights. Everything is stored in kilograms; only the screen converts. */
enum class WeightUnit(
    val code: String,
    private val arabic: String,
    private val perKg: Float,
    val loadStep: Float,
    val bodyStep: Float,
) {
    KG("kg", "كغ", 1f, 2.5f, 0.1f),
    LB("lb", "رطل", 2.2046225f, 5f, 0.2f);

    private val isArabic get() = Locale.getDefault().language == "ar"

    /** The unit as the reader writes it: "kg" / "كغ". */
    val label: String get() = if (isArabic) arabic else code

    fun fromKg(kg: Float): Float = kg * perKg
    fun toKg(value: Float): Float = value / perKg

    /** "72.5 kg" / "160.0 lb" */
    // Isolated so the number and unit keep their order: left-to-right "72.5 kg", right-to-left "72.5 كغ".
    fun format(kg: Float, decimals: Int = 1): String =
        (if (isArabic) "\u2067%.${decimals}f %s\u2069" else "\u2066%.${decimals}f %s\u2069").format(Locale.US, fromKg(kg), label)

    /** Just the number, e.g. for compact "8×60" labels. */
    fun number(kg: Float, decimals: Int = 1): String = "%.${decimals}f".format(Locale.US, fromKg(kg)).removeSuffix(".0")

    companion object {
        /** Resolves both canonical codes ("kg", "lb") and localized labels ("كغ", "رطل") safely. */
        fun fromCodeOrLabel(raw: String?): WeightUnit {
            val cleaned = raw?.trim().orEmpty()
            return if (cleaned.equals("lb", ignoreCase = true) || cleaned.equals("lbs", ignoreCase = true) || cleaned == "رطل") {
                LB
            } else {
                KG
            }
        }
    }
}

```
