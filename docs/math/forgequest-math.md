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

## 3. Personal records (`ProgressRules.kt` → `Records`)

Each set gets a comparable score. A set is a record when it beats every earlier set of that exercise; the first session never counts.
- **Weighted:** with load, kg × (1 + effectiveReps / 30), where effectiveReps = reps up to 12 and 12 + 3 × ln(1 + (reps − 12)) above 12. This is Epley with diminishing returns above 12 reps. With no load, reps / 100.
- **Bodyweight reps:** reps + 0.35 × added kg (vest or belt).
- **Holds:** seconds + 1.0 × added kg.

**Question.** Is a single number like this fair across rep ranges, for example 5×100 kg against 8×80 kg against 20×60 kg? Is 0.35 reps per added kg sensible for weighted pull-ups and dips?

---

## 4. Muscle recovery map (`ProgressRules.kt` → `Recovery`)

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

## 5. Weekly streak, reminders, XP and ranks (`ProgressRules.kt`)

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

## 11. Checks on the calorie target (`CalorieCheck.kt`)

Calorie plan v2 compares the target with what the person actually logs. All of it runs on a 21-day window.

- **A day counts as logged** only with at least `max(800, 0.70 x BMR)` kcal, so a half-logged day cannot fake a low intake.
- **Real daily burn** is average logged intake minus the weight trend times 7,700 kcal per kg. The trend is the median of the slopes between every pair of weigh-ins (Theil-Sen), so one salty day cannot swing it. It needs 17 of the 21 days logged, 6+ weigh-ins spanning 14+ days with 2 in the first week and 2 in the last, and a window that starts 7+ days after the goal changed (week-one water weight). The result is kept within 0.8 to 1.3 times the formula's burn.
- **Moving the target** toward a new suggestion: at most 200 kcal per step, only when it moves 100 kcal or more, rounded to 50, and at most every 14 days.
- **Four signals**, most important first:
  - *Under-fuelled*: on a Standard or Fast cut, and either the deload check fired or weight is falling faster than the pace plus a 0.25 % per week margin. The margin keeps normal scale noise from firing it.
  - *Weight changed*: the 7-day average is 2.5 kg or more from the weight the target was set at.
  - *Scale creep*: on a cut without full logs, 6+ weigh-ins, and the 4-week trend is flatter than -0.15 % per week (that is, barely moving).
  - *Step drop*: on a cut, daily steps fell 2,000 or 25 % from the first two weeks.

## 12. Workout score (`WorkoutScore.kt`)

A score from 4 to 10 worked out from the workout's own numbers: 3 for showing up, up to 3 for finishing the plan, up to 3 for beating last time, and 1 for a personal record. On a rough day (recovery under 50 or a set marked Pain) a drop counts as holding steady and only two thirds of the planned sets are needed. Warm-ups and cool-downs never count. Full rule: `docs/math/workout-score.md`.

## 13. Workout debrief (`WorkoutDebriefMath.kt`)

The debrief is typed lines with their numbers; the UI turns them into English or Arabic (`data/DebriefText.kt`).

- **Headline**: the first rule that matches, in a fixed priority order (first workout, back after a break, rough day held, mostly up, and so on), each with two or three wordings picked by workout id.
- **Wins, watch-outs, next time**: reuse the score's per-exercise trend, `Progression.next` and the plateau and deload check in `Trends.kt`.
- **Plateau**: 4+ workouts over 21+ days with the last two not beating the earlier ones. When `VariationCycle` has a variation, the line names it and the peak the lift reached.
- **Small print**: with a watch and low recovery, "the app eased off"; with a watch but no recovery number that day, "judged on your numbers alone"; with no watch, recovery is never mentioned, apart from a "connect a watch" hint on one workout in five.

## 14. Variation for a stalled lift (`VariationCycle.kt`)

Only the six core movements are cycled: horizontal press, horizontal pull, vertical press, vertical pull, squat (two legs or one) and hinge. A candidate must be the same core movement, fit the person's equipment, be the same kind (weighted, reps or hold), sit at most one difficulty tier away, not be in the plan already, and not have been swapped out in the last 56 days. Among those, a different joint angle or equipment comes first, then the stalled lift's curated swap order, then the id (so the answer is stable).

## 15. Readiness sentence (`ReadinessText.kt`)

A short night is under 6 hours. A steady day after two or more short nights in a row, or an ease-off day after one, gets a sentence that says so. Otherwise a leg or upper-body day gets its plan-aware sentence, and any other day the plain one. Each sentence has up to three wordings: the one shown today stays all day, and the next day's is the one shown longest ago.

## Source

The code is the reference; this document explains it. The appendix that used to copy the source here went stale and was removed.

- [`Nutrition.kt`](../../app/src/main/java/com/forgequest/app/domain/Nutrition.kt): calories and macros
- [`CalorieCheck.kt`](../../app/src/main/java/com/forgequest/app/domain/CalorieCheck.kt): checks on the calorie target from real logs
- [`Progression.kt`](../../app/src/main/java/com/forgequest/app/domain/Progression.kt): next session
- [`ProgressRules.kt`](../../app/src/main/java/com/forgequest/app/domain/ProgressRules.kt): records, muscle recovery, streak, XP (was `Progress.kt`)
- [`Readiness.kt`](../../app/src/main/java/com/forgequest/app/domain/Readiness.kt): sleep, strain and recovery
- [`ReadinessText.kt`](../../app/src/main/java/com/forgequest/app/domain/ReadinessText.kt): which readiness sentence shows
- [`Trends.kt`](../../app/src/main/java/com/forgequest/app/domain/Trends.kt): progress numbers, plateau and deload
- [`WorkoutScore.kt`](../../app/src/main/java/com/forgequest/app/domain/WorkoutScore.kt): the 4 to 10 workout score
- [`WorkoutDebriefMath.kt`](../../app/src/main/java/com/forgequest/app/domain/WorkoutDebriefMath.kt): the debrief lines
- [`VariationCycle.kt`](../../app/src/main/java/com/forgequest/app/domain/VariationCycle.kt): the variation for a stalled lift
- [`Badges.kt`](../../app/src/main/java/com/forgequest/app/domain/Badges.kt): badges
- [`ExerciseSwapEngine.kt`](../../app/src/main/java/com/forgequest/app/domain/ExerciseSwapEngine.kt): swaps
- [`ExerciseTiers.kt`](../../app/src/main/java/com/forgequest/app/domain/ExerciseTiers.kt): difficulty tiers
- [`SetEffort.kt`](../../app/src/main/java/com/forgequest/app/domain/SetEffort.kt): effort chips (was `Effort.kt`)
- [`Muscle.kt`](../../app/src/main/java/com/forgequest/app/domain/Muscle.kt): muscles
- [`WeightUnit.kt`](../../app/src/main/java/com/forgequest/app/domain/WeightUnit.kt): kg and lb (was `Units.kt`)
