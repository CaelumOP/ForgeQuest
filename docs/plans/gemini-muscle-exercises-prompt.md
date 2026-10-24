# Gemini review: "tap a muscle, see its exercises" + a training level

Paste everything inside the fence below into Gemini. Optionally, attach a screenshot of the Workouts tab muscle map.

```
You are stress-testing a feature idea for ForgeQuest. Review it as three people at once: a strength coach who trains beginners to advanced lifters (gym and calisthenics), a mobile UX researcher for fitness apps, and a product manager who has to protect the app's main goal. Be blunt. Cite research or well-known apps where you can.

## The app today (facts, not proposals)
- ForgeQuest is a workout + nutrition app (Android and a web PWA), English and formal Arabic, for men and women aged about 18–40, from total beginners to experienced lifters. Main goal: consistency without burnout, meaning steady training people keep up for months. Design rule: as few steps and pickers as possible.
- Onboarding: name and body type → body (weight, height, age, normal day) → pick a starting plan → goal and calories → optional AI key. The plan step asks for equipment (full gym, home gym, dumbbells + pull-up bar, calisthenics bars, doorway bar, bodyweight only) and offers 3 built-in plans: "Hemo · Foundation level" (5-day calisthenics), "Full body · 3 days", and "Push · Pull · Legs (gym)". There is no explicit "what is your level?" question. Level is only implied by the plan's name. An AI coach can also write a plan.
- The Workouts tab shows a front and back body map coloured by muscle recovery (just trained / recovering / almost ready / fresh), with 16 muscles: chest, shoulders, biceps, triceps, forearms, abs, obliques, traps, lats & upper back, lower back, glutes, quads, hamstrings, adductors, calves, tibialis. Tapping a muscle today only shows one line: "Chest: 60% recovered, ready in 20 h".
- The exercise library has 90 built-in exercises plus the user's own. Each has a primary and secondary muscle list, a kind (reps / weighted / hold) and a video link. A swap engine already knows each exercise's equipment, movement pattern and close substitutes, and the app can adapt a plan to the user's equipment. There is NO difficulty level on exercises. The library is searchable by name but cannot be filtered by muscle.
- Exercises per primary muscle: lats & upper back 17, shoulders 16, glutes 15, quads 13, chest 12, abs 10, triceps 10, hamstrings 6, traps 5, biceps 5, calves 4, lower back 3, forearms 3, adductors 1, obliques 1, tibialis 1.

## The idea
1. During onboarding, the user chooses their training level (e.g. beginner / intermediate / advanced).
2. On the Workouts tab, tapping a muscle on the body map shows a list of exercises for that muscle, suited to the user's level.

## Our own stress test so far (challenge it, don't just agree)
- The list part is cheap and useful (the data exists); the level part is the expensive, risky part: 90 exercises need a difficulty rating, and self-reported level is unreliable (beginners often over-rate themselves).
- A new onboarding question breaks the "fewer steps" rule. Alternatives: put the level inside the existing plan step as a filter on the plan list, infer it from the chosen plan, or infer it from logged performance (e.g. push-up reps, relative lifts).
- The tap would then mean two things (recovery vs exercises). Proposed answer: keep the recovery line and add a "See 12 chest exercises" button that opens a sheet.
- The list must also respect the user's equipment, or it will show barbell moves to someone with no equipment.
- Risk to the main goal: a muscle-by-muscle menu invites random "chest day again" training instead of following the plan. If the muscle is still recovering, the list should say so. And what does tapping an exercise do: just the video/detail, "add to today's workout", or "swap into my plan"? A list with no action may be a dead end.
- Thin coverage: obliques, adductors and tibialis have 1 exercise each, so their lists would look broken, especially after filtering by level and equipment.
- Hard moves for beginners (e.g. one-arm push-up): should we hide them, show them locked, or show a progression ladder ("knee push-up → push-up → diamond push-up → archer push-up")?
- Small muscles (forearms, tibialis, adductors) are small tap targets on the body map; there needs to be another way in (a muscle chip list) for accessibility.

## What we want from you
Part 1, the training side:
- Is a 3-level scale right? How should each of the 90 exercises be rated: by a fixed level, by a progression ladder per movement pattern, or by a strength-standard check? Give a practical rubric a developer can apply to every exercise.
- Is self-reported level good enough? If not, what is the simplest reliable way to set it (a test set, inference from the first 2 weeks of logs, or the chosen plan), and when should the app move someone up a level?
- Which muscles need more exercises (with names) so every muscle has at least 3 options at each level for bodyweight-only users and for full-gym users?
- Safety: which exercises should never be suggested to beginners, and why?

Part 2, UX and product:
- Should level be asked in onboarding at all? Compare: (a) a new question, (b) a filter inside the plan step, (c) inferred silently, (d) asked later, only when the user first opens a muscle list. Pick one and justify it.
- Best interaction for "tap a muscle → exercises" alongside the recovery colours: a bottom sheet, a new screen, or inline? What should the list show per exercise, how should it be sorted (primary vs secondary muscle, level fit, equipment, recently done, recovering), and what single main action should each row have?
- How do apps such as Fitbod, JEFIT, Muscle Wiki, Hevy and Caliverse handle this? What works and what fails?
- Does this feature risk pulling users away from following their plan? How do we keep it a helper for consistency rather than a distraction?
- Arabic/RTL: anything special for a body map and an exercise list in Arabic?

Part 3, verdict:
- Ship, change or drop? Give the smallest first version worth building, what to leave out, and how we would know it works (one or two metrics an offline app can measure on the device).
- List any wrong assumptions in our stress test.
Keep the answer structured with headings and short bullet points, and give your recommendation first.
```
