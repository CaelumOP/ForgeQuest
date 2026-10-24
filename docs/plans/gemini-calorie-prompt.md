# Gemini Deep Research: calorie plan review

## Attach these files (all in the repo)

1. `docs/plans/calorie-plan.md`: the plan to review (the main input)
2. `docs/math/forgequest-math.md`: section 1 (current nutrition math) and the appendix source of `Nutrition.kt`
3. `docs/plans/calorie-ui/1-onboarding-body.jpg`: About your body
4. `docs/plans/calorie-ui/2-onboarding-plan.jpg`: Pick a starting plan
5. `docs/plans/calorie-ui/3-onboarding-goal.jpg`: Your goal and the daily target (shows today's 2,300 kcal)
6. `docs/plans/calorie-ui/4-home.jpg`: Home rings (protein and calories left)
7. `docs/plans/calorie-ui/5-profile-goals.jpg`: Profile → Goals and nutrition

The screenshots are from the real web app (iPhone size, dark theme). The Android app looks the same.

## Prompt (paste this)

```
You are reviewing a plan to fix how ForgeQuest sets daily calorie and protein targets. Check it as three people at once: a sports dietitian, a researcher in energy expenditure and behaviour change, and a mobile UX designer for fitness apps.

Context: ForgeQuest is a workout and nutrition app for beginners to experienced lifters, men and women aged about 18–40, in English and Arabic. Most users are not very active outside workouts (often under 5,000 steps a day), and many have no fitness watch. Everything runs on the phone, so the math must be simple formulas with unit tests. The app's goal is steady progress people can keep up for months, but someone who wants a faster cut may choose one safely.

The attached "calorie-plan.md" describes the problem (the app picks its activity factor only from planned training days, so a desk worker training 5 days a week gets roughly 2,800 kcal of daily burn instead of about 2,500) and proposes four changes:
1. activity factor = base + 0.035 × training days
2. a "How active is your normal day?" question (1.20 / 1.30 / 1.45), replaced by watch steps on Android
3. cut and gain paces as % of body weight per week, with caps, a calorie floor and guardrails
4. later: learning the real burn from logged food and weight

The screenshots show the current screens.

Part 1 — the math (research it, cite it):
- Is "base + 0.035 per training day" a fair model for resistance-training energy cost? Give the best-supported per-session cost (kcal, by body weight and session length) and the constant you would use instead, if any.
- Are the three day-activity bases (1.20 / 1.30 / 1.45) right for "mostly sitting", "on my feet some of the day" and "physical job", once exercise is excluded? Give better values if the evidence supports them.
- Is the steps formula (1.20 + 0.02 per 1,000 steps above 4,000, capped at 1.45) reasonable? What kcal per 1,000 steps by body weight does the evidence support? Should steps during workouts be excluded?
- Check every pace, cap, floor and protein value in the table. Is 7,700 kcal/kg right for this use, or should early weight loss (water) be treated differently?
- Recompute every row of the "What the numbers become" table with your corrected values.
- For change 4 (adaptive real-burn estimate): what minimum logging and weigh-in rules make it reliable, how should under-reported food intake be handled, and how fast should the target be allowed to change?
- Check the plan's "Where every input comes from" table (the input audit). Which inputs that matter for energy needs are still missing (for example, the drop in daily movement during a diet, water-weight swings, the menstrual cycle)? Which of our inputs are unreliable (self-rated activity, under-logged food, smart-scale body fat)? For each one, say whether to add it, correct for it, or ignore it.
- Real-world check: besides change 4, what simple signals from data the app already has (weigh-ins, food logs, workouts, steps) would show that a target is wrong, and when should the app speak up?

Part 2 — the screens (UX research, cite it):
- Using the screenshots, how should the day-activity question and the pace choice be asked so that people answer honestly? People overrate their activity, so give the wording, the number of options and the order.
- Should the target card show the daily burn and the deficit ("Daily burn ≈ 2,490 · Target 1,850 (−640)"), or does that confuse beginners? What do evidence and well-known apps (MacroFactor, MyFitnessPal, Cronometer, Noom) do?
- How should an existing user be told their suggested target changed (2,300 → 1,850) without losing trust?
- Arabic: any wording or layout pitfalls for these questions in formal Arabic, right to left?

For every claim, give the source (authors, year, title, link) and quote the one sentence you rely on. Label anything without a source as your opinion.

Return:
A. Verdict on each of the four changes: keep / adjust (with the exact new numbers) / drop.
B. The corrected formulas and constants, plus 5 test cases (inputs → expected kcal) to paste into unit tests.
C. UX recommendations for each of the 5 screenshots, with exact wording in English and formal Arabic.
D. Safety risks, especially for women, under-18s, and people with a history of disordered eating.
E. What to leave exactly as it is.

Keep it practical: don't redesign the app or add features beyond these four changes.
```

## Why this is set up this way

- **One focused topic, not the whole repo.** Deep research is strongest on a narrow question with real evidence to find. In the first review it missed files it never saw.
- **Real screenshots and a real persona.** It can check what users actually see and do.
- **"Three people at once".** Naming the kinds of expertise needed works better than one job title.
- **Quote rules.** They make made-up citations easy to spot.
