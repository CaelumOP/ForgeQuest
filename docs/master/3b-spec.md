# 3B spec: Input & Test Baseline ("Find your starting point")

Same behaviour on Android (`app/`, reference) and web (`web/`). English + formal Arabic for every string. Soft Coach design (`DESIGN.md`, skill notes: one accent, 48 dp/px targets, RTL, 200 % text). Background map: section "2. Input & Test Baseline Map" of
C:\Users\Administrator\AppData\Local\Claude-Data\claude_pzs8sxrjxfjjc\side-session-notes\local_a06498bf-bef1-42bb-920a-c03ca288d21f\stage2-code-map.md

## Storage
- New table `baseline_tests`: `id` (auto), `exerciseId` (string), `kind` ('REPS' | 'HOLD' | 'WEIGHTED'), `reps` (int, nullable), `seconds` (int, nullable), `weightKg` (float/number, nullable), `createdAt` (epoch millis). The newest row per exerciseId is the current baseline; older rows stay (history of tests).
- Android: Room entity + DAO (`observeAll`, `latestFor(exerciseId)`, `latestAll()`, `insert`, `deleteAll` for restore), DB version 10 -> 11 with an explicit `Migration(10, 11)` creating exactly the table Room expects, added to the migrations list, exported schema `app/schemas/.../11.json` produced by the build (do not hand-edit 10.json). Update the routing table comment in AppDatabase if it states the version.
- Web: new Dexie version adding store `baselineTests: '++id, exerciseId, createdAt'`; keep every older version block untouched.
- Backup JSON: new OPTIONAL top-level field `baselineTests` (array of rows). Export includes it; restore reads it when present and treats missing as empty, so old backups still restore. Never rename existing backup fields. Add a test that an old backup without the field restores.
- A baseline test is NEVER stored as a session or set: no streak, XP, PR, volume, history, badges, recovery.

## Push-up / pull-up max (#11)
- The Build my plan > You push-up and pull-up fields read and write the baseline store (exerciseIds `push_up` and `pull_up`, kind REPS; use the real catalog ids for the standard push-up and pull-up, check they exist). The builder input `pushUpMax` / `pullUpMax` comes from the latest baseline rows. On first run migrate any existing value from the saved plan-builder answers into a baseline row once (only if no baseline row exists for that id), keep the old answers field readable.

## Working targets (#13), pure domain function `BaselineTargets` / `baseline-targets.ts`, unit tested on both platforms with identical cases
- Bodyweight REPS: target reps = max(1, floor(0.7 * max)). max 0 -> no target; result says "Try an easier version" and names the easier variation: the easier tier of the same movement from the catalog tier data (`ExerciseTiers` on Android / its web counterpart: same progression line, one tier easier; if none, the easiest exercise with the same movement pattern available with the user's equipment).
- HOLD: target seconds = max(5, floor(0.7 * max / 5) * 5).
- WEIGHTED: input = a submax set (weight, reps). reps > 12 -> no target, result says "Too light to judge: try a heavier weight". Else e1rm = weight * (1 + reps / 30); working weight for the plan's minimum reps with ~3 reps left: e1rm / (1 + (repsMin + 3) / 30) * 0.9, snapped DOWN to the unit's load step (reuse the existing load-step helper); below one step -> no target. Never ask for a 1-rep max.
- Weighted rows never set bodyweight targets and vice versa.

## Precedence and labels (#14)
- When a live session's sets are built for an exercise: real history (any done set that counts for progression) > baseline row for that exercise > existing donor estimate (`Baseline.coldStart`) > blank.
- From baseline: WEIGHTED fills the weight; REPS: when the target is below the plan item's repsMin, use the target as the sets' reps target; otherwise keep the plan's reps; HOLD fills seconds with the hold target.
- Live card label: "From your baseline" (AR «من اختبار البداية») next to the existing "Estimated start" for the donor case. Extend the existing baseline status enum with a `FROM_BASELINE` value.

## Dumbbell moves with no data (#15)
- A weighted set with no weight from any source shows the weight field blank with placeholder "Add weight" (AR «أضف الوزن») instead of "0 kg", and the card offers a "Test this move" (AR «اختبر هذا التمرين») text button that opens the test flow for that exercise. Saving a set still requires the existing validation.

## The flow
- Screen title "Find your starting point" (AR «حدّد نقطة البداية»). Short intro sentence: "Test a few moves so your first workout starts at the right level. Takes about 5 minutes." (AR «اختبر بضعة تمارين ليبدأ تمرينك الأول بالمستوى المناسب. يستغرق نحو 5 دقائق.»).
- List of moves to test: push-up, pull-up (only if the user has a pull-up bar per their equipment), plank (hold), then the first 3 weighted main lifts of the active plan (if any), each with its last result and target if tested. Plus "Test another move" opening the existing exercise picker.
- Each move: two ways. Manual: enter reps (REPS), seconds (HOLD), or weight + reps (WEIGHTED). Guided: REPS = a big tap counter "Tap after each rep" then Done; HOLD = reuse the existing hold overlay (stopwatch) and save its elapsed seconds; WEIGHTED = manual entry with the hint "Pick a weight you can lift 6-10 times with good form".
- After saving: the result card shows the target per the rules above ("Start with 8 reps", "Start with 25 s", "Start with 22.5 kg") or the 0-rep easier suggestion or the too-light hint.
- Between tested moves an optional 90 s rest timer with Skip (#16); never automatic sound beyond the existing timer cues.
- No streak/XP/history side effects (see Storage).

## Entry points (#10 + additions)
- Workouts tab: card "Find your starting point" with one line and a button, shown while there is no finished workout AND no baseline row; dismissible (remember the dismissal).
- Profile > You and your goals: row "Starting point" (AR «نقطة البداية») with the count of tested moves, opens the flow.
- Build my plan: an obvious entry in the main page, near the top of the "Make it fit me perfectly" area or under Goal: a row/button "Test your starting point" (AR «اختبر نقطة بدايتك») opening the flow; the push-up / pull-up fields in You also open/feed the same store.
