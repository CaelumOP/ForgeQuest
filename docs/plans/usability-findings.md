# First-time user test: findings (2026-10-25)

Plan: [usability-test-plan.md](usability-test-plan.md). Six persona runs on the Android emulator, each a fresh agent that saw only screenshots: Sam, Nora, Omar and Lina (Opus), then Khalid (Arabic) and returning Sam with two weeks of data (Sonnet). AI replies came from a local stand-in server, so advice quality wasn't tested. Full per-persona reports stayed in the session scratchpad; this is the summary.

## Fixed in 2.12.0

| Area | Problem | Fix |
|---|---|---|
| Bug | Scrolling over a −/+ stepper changed the value (calories, workouts per week, reps) | Steppers act on lift or a still hold, never on touch-down; a drag cancels |
| Bug | Scrolling over a set's tick could tick it | Tick counts only when the finger lifts without moving; no ripple while scrolling |
| Bug | "+0 XP" after a workout; first badge never unlocked | XP and badges read the database, not the stale shared snapshot; Home re-checks badges |
| Bug | Plan editor opened on the wrong day | Opens on the day you were looking at |
| Bug | Key typed in setup thrown away by "Skip for now" | Main button becomes "Save key" and checks it |
| Bug | Rest timer lost after leaving and resuming a workout | Timer outlives the screen |
| Bug | Workouts tab stuck after the Docs import jumped to Profile | Switches tabs properly |
| Bug | Gemini bridge switched on by a stray tap and stayed on after a cancelled sign-in | Explains itself first; a cancelled sign-in switches it off |
| Food | Typing a food needed calories the person didn't know | "Estimate calories from the name" (shows what it based the guess on) |
| Food | "No foods in Lunch yet" right after logging lunch | "No saved Lunch foods yet" + how saved foods work; tick hint |
| Training | No way to train on a rest day, or twice a day | "Train anyway" / "Train again" on Home |
| Training | Next set reset to the plan's reps and 0 kg | Reps and weight carry forward to untouched sets |
| Training | Full gym / dumbbells still got a bodyweight plan; no-equipment kept bar moves | Dumbbell 3-day plan picked from equipment; moves that need missing equipment are dropped |
| Training | History and plan editor were unlabelled icons | Labelled buttons |
| Motivation | "E" circle, XP and next badge unexplained | Level under the circle, XP rules in Profile, badge progress (8 / 10) and "Next up" |
| Motivation | "Week complete" after one workout in the joining week | "Head start" message; next week's target shown once the week is done |
| Motivation | Nobody found the reminder setting | Reminder offer on the summary of the first workouts |
| Readiness | Dashes and 0.0 strain unexplained | Sleep-only note, no empty stage lanes, no 0-strain breakdown |
| Wording | Wrong doc name, "nothing else in your Drive is touched", developer text after a cancelled sign-in, "best set" showing an estimated max, and more | Corrected in English and Arabic |
| Arabic | English "XP" and Latin rank letters; number ranges shown backwards (8–12 → 12–8); scary finish dialog; awkward phrases | Arabic XP and rank, ranges isolated left-to-right, rewritten dialog and phrases |

## Left as they are (decide later)

- **YouTube import** stays through the Gemini app (Workouts → Change plan → "Make a plan with Gemini"). The coach now says it can't watch videos. A real in-app YouTube import would be a new feature.
- Body type defaults to Male; one equipment preset at setup (fine-tune in Profile → Training).
- Protein-per-kg suggestion vs the protein target set by the calorie plan.
- History rows show no weights; the weekday chips are single letters; Android's own permission dialogs follow the phone language.
- In Arabic the muscle-map views and charts still run left to right.

## Not verified

The 2.12.0 changes were not checked on the emulator after the last batch (Khalid's Arabic fixes, returning-user fixes, the stricter tick). Earlier batches were exercised by the later persona runs. CI unit tests pass.
