# Calorie targets that fit real days: plan v2 (2026-10-24)

v2 takes in the Gemini review (`ForgeQuest Calorie Plan Review.txt`). Every number below was recomputed and matches its 8 test cases. Nothing is built yet. Android and web change together, with the same numbers and the same tests.

## The problem in one line

The formula is fine (Mifflin-St Jeor × activity factor), but the **activity input is wrong**. The app chooses the factor only from planned training days, so 5 gym days counts as "moderately active all day". For a desk worker that's +300–450 kcal a day too much. Example: 83 kg, 172 cm, 20 y, 5-day plan, Lose fat → 2,300 kcal, which is about his real maintenance.

## Input audit (where each input comes from)

| Input | Today | After this plan |
|---|---|---|
| Weight, height, age | Onboarding and Profile | Same. The target is re-suggested when the 7-day average weight moves 2.5 kg from the weight it was set with |
| Sex | Asked as "Body for the muscle map" | Same question, relabelled so people know it also sets calories |
| Body fat % | Profile, optional | Same, with a warning that smart scales can be off by 5–8 % |
| Training days | The active plan | Same, but worth +0.025 × BMR each, not a whole-day factor |
| Daily activity outside workouts | **Never asked** | 3-card question (Android: watch steps when available) |
| Steps | Android, today only, for the coach | 7-day average, minus 1,000 on workout days |
| Watch "calories burned" | Not used | Still not used: trackers over-count exercise by 27–93 % |
| Goal pace | Fixed −500 / +250 | Gentle / Standard / Fast, and Slow / Standard |
| Real intake and weight | Logged but unused | Change 4 (later) |

## The four changes

### 1 + 2. Activity: a day base plus training

```
base   = SITTING 1.25 · MIXED 1.375 · PHYSICAL 1.50       (from the question)
       or, on Android with steps on ≥ 5 of the last 7 days (days < 500 ignored,
       −1,000 steps on days with a logged workout):
         base = min(1.50, 1.25 + 0.02 × max(0, avgSteps − 4,000) / 1,000)
factor = clamp(base + 0.025 × trainingDays, 1.25, 1.80)
TDEE   = BMR × factor
```
- **Why 0.025 per training day:** it's about 0.175 × BMR per session (≈ 320 kcal for 83 kg). That fits measured costs of 150–320 kcal per session (2024 Compendium; Benito 2016; Lytle 2019).
- **Why the base is 1.25, not 1.20:** in the doubly labeled water studies, 1.20 is the level of someone chair- or bed-bound (Black 1996; FAO/WHO/UNU 2004).
- **Steps formula:** 4,000 steps gives 1.25 (matches "sitting"), about 10,250 gives 1.375, and 16,500+ caps at 1.50.

### 3. Paces, floor and protein

| Goal | Pace (label in the app) | Weekly change | Cap | Protein | Suggest a break after |
|---|---|---|---|---|---|
| Lose fat | Gentle | −0.5 % | 20 % of TDEE | 1.8 g/kg | 16 weeks |
| Lose fat | **Standard** (default) | −0.75 % | 25 % of TDEE | 2.0 g/kg | 12 weeks |
| Lose fat | **Fast** (Aggressive in the code) | −1.0 % | 30 % of TDEE | 2.2 g/kg | 6 weeks |
| Build muscle | Slow | +0.15 % | +125..225 kcal | 1.8 g/kg | — |
| Build muscle | **Standard** (default) | +0.25 % | +200..350 kcal | 1.8 g/kg | — |

- **How the daily deficit or surplus is worked out:** kg × pace % / 100 × 1,100 (7,700 kcal per kg ÷ 7 days), then the cap applies.
- **Safety floor:** max(1,500 men / 1,200 women, 0.85 × BMR), rounded to 50.
- **Fast is blocked** for under-18s, for BMI under 20, and for body fat under 12 % (men) or 20 % (women). Those users get Standard, with the line "Standard pace selected to protect muscle and healthy growth."
- **Protein with high BMI:** when body fat is unknown and BMI is over 30, protein uses an adjusted weight: refKg = 25 × height(m)², adjusted = refKg + 0.25 × (kg − refKg).
- **"Losing too fast" warning:** only past the chosen pace (0.75 / 1.0 / 1.25 % a week). For women, the "stalled" note waits 28 days so the menstrual cycle doesn't fool it.

### 4. Later: real burn from logged food and weight

- **Weight trend:** smoothed first (EWMA, α 0.10, filling missing days in a straight line), not a raw straight-line fit.
- **Which days count:** a food day counts only if it holds at least max(800, 0.70 × BMR) kcal, so half-logged days are ignored.
- **When it's allowed to run:** at least 17 of the last 21 days logged, and at least 6 weigh-ins spanning 14+ days (2 in the first week, 2 in the last).
- **Ignored days:** the first 7 days after a goal change (water weight).
- **Limits:** the estimate stays within 0.80–1.30 × the formula's TDEE. Each update moves the target by at most ±200 kcal, once every 14 days, with a one-tap card.

## The numbers (unit tests, Kotlin + TypeScript)

| # | Person | Today | New |
|---|---|---|---|
| 1 | Man 83 kg, 172 cm, 20 y, 5 days, sitting, Lose fat Standard | 2,300 | **1,850** (TDEE 2,489). Gentle 2,050 · Fast 1,750 · protein 165 g |
| 2 | Same, 3 days | 2,000 | **1,800** (TDEE 2,398) |
| 3 | Same, 5 days, on his feet some of the day | 2,300 | **2,050** (TDEE 2,715) |
| 4 | Woman 60 kg, 165 cm, 30 y, 3 days, sitting | 1,400 | **1,300** · Fast 1,200 (hits the floor) |
| 5 | Man 100 kg, 180 cm, 35 y, 0 days, sitting | 1,850 | **1,850** (TDEE 2,444) · protein 170 g (BMI 30.9, adjusted) |
| 6 | Man 83 kg (as #1), Build muscle Standard | 3,050 | **2,700** (+228) · Slow 2,650 (+137) |
| 7 | Girl 52 kg, 168 cm, 17 y, 3 days, asks for Fast | 1,450 | **1,350** with Fast blocked (or 1,600, see decision F) |
| 8 | Man #1, 4 days, steps [7800, 8200, 300, 8000, 7500, 8500, 8000] | — | **1,950** (base 1.33, TDEE 2,588) |

## Screens

1. **Onboarding → name step:** relabel "Body for the muscle map" to something like "Body type (for calories and the muscle map)".
2. **Onboarding → About your body:** under age, add "Your normal day (outside workouts)" as 3 cards, with **Mostly sitting** selected by default:
   - Mostly sitting: desk work, studying, driving (under about 5,000 steps)
   - On my feet some of the day: teaching, retail, housework, regular walking (about 6,000–10,000 steps)
   - Physical job: warehouse, construction, waiting tables (12,000+ steps)

   The formal Arabic wording is in the review, section C.
3. **Onboarding → Your goal:** after Lose fat, show a 3-way "Weekly pace" control (Gentle ~0.4 kg · Standard ~0.6 kg · Fast ~0.8 kg). After Build muscle, show 2 options (Slow ~0.5 kg/month · Standard ~0.9 kg/month). The control is hidden for Maintain. The target card adds a muted line: "Daily burn ≈ 2,490 · Target 1,850 (−640/day)". In Arabic, the minus is wrapped in a left-to-right isolate.
4. **Home:** a one-time card for existing users whose target equals the old suggestion: "More accurate calorie target available … 1,850 (was 2,300)", with [Use 1,850] [Keep 2,300]. The calorie ring already turns gold, never red, when over the target, so no change is needed there.
5. **Profile:** the day question goes in Body and health ("Using your watch: about 6,200 steps a day" on Android). The pace control goes under Your goal. The suggestion line names all the inputs. A body-fat helper line warns about smart scales.
6. **Android:** port the web's onboarding split (body → plan → goal) so both apps ask the same things.

## Data and existing users

- **New settings fields** in both apps and in the backup file: `dayActivity`, `cutPace`, `gainPace`. Old backups load with the defaults.
- **Existing users:** their saved target never changes silently (see the Home card).

## Decided (2026-10-24)

A yes · B Standard · C above BMI 30 · D one-time Home card · **E all four changes now** · **F b: under-18s only lose the Fast pace** (floor unchanged) · **G a: manual calories can't go below the floor** · **H b: all four real-world signals now**.

## Options that were offered

- **A. Constants:** use Gemini's values (1.25 / 1.375 / 1.50, +0.025, gain paces 0.15 % / 0.25 %). Recommended: **yes**. Your own target stays at 1,850.
- **B. Default cut pace:** Standard. **Settled** (Gemini agrees).
- **C. High-BMI protein:** adjust only above BMI 30. **Settled** (Gemini agrees).
- **D. Existing users:** the one-time Home card. **Settled**.
- **E. Build order:** changes 1–3 now, change 4 later. **Settled**.
- **F. Under-18s** (Gemini contradicts itself: its test case gives 1,350, but its safety section says Gentle only, with a floor of max(1,600, BMR)):
  - a) the stricter rule: under-18s get Gentle only and a floor of max(1,600, BMR). Recommended.
  - b) block Fast only, as in its test case.
- **G. Manual calorie override:** today anyone can set as low as 1,200, including men, which is below the men's floor.
  - a) clamp manual entries at the person's floor. Recommended.
  - b) allow it, but show a warning below the floor.
- **H. The 4 extra real-world signals** (scale creep after 28 days, steps dropping during a cut, an under-fuelled lifter, re-suggesting after a 2.5 kg change):
  - a) build only the 2.5 kg re-suggest now, the rest with change 4. Recommended.
  - b) all now.
