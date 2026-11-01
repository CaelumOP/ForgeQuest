# Brief: workout score calculated from the numbers (ForgeQuest 2.15.0)

The user approved this. Build it on Android AND web (parity), in a NEW worktree off origin/main:
`git -C "G:/AntiGravity projects/ForgeQuest" worktree add .claude/worktrees/debrief-score -b feat/debrief-score origin/main`.
Work only there. Never switch branches in the main checkout. Commit, but do not push and do not open a PR. The coordinator reviews first.

## Why
Today the debrief "score" (4..10) is the AI's guess and means nothing. Replace it with a score the app calculates from
the workout's own numbers. It shows instantly, even with no AI key or when the AI fails. The AI only writes the words.

## The rule (pure function, unit tested on both platforms)
Android: new `app/src/main/java/com/forgequest/app/domain/WorkoutScore.kt`. Web: `web/src/domain/workout-score.ts`.

Inputs:
- the workout's done sets grouped by exercise, with each exercise's kind and id
- the most recent earlier workout's done sets for each exercise (the same "last time" the debrief already uses: `doneSetsBefore`, the first sessionId)
- planned set count: the sum of `sets` over the plan items of `session.planDayId` (`db.plans().items(dayId)` or its equivalent; web has the same). Null when there is no plan day or the day has no items.
- `roughDay` = readiness for `session.epochDay` is EASE_OFF or RECOVER (`Readiness.call(recovery)`, as in `WorkoutRepository.lowReadinessToday`, but for that day), OR any done set has effort PAIN

Per exercise that has a last time:
- best = max `Records.score(kind, reps, seconds, weightKg, id)` over the sets; total = sum of reps (or seconds for holds); heaviest = max weightKg (null counts as 0)
- **UP** if best > lastBest, OR (total > lastTotal AND heaviest >= lastHeaviest)
- **DOWN** if best < lastBest AND total <= lastTotal
- otherwise **SAME**
- value: UP = 1, SAME = 0.5, DOWN = 0. On a roughDay, SAME and DOWN both count 1 (holding steady is full marks, drops aren't penalised).

Points:
- base 3
- completion: done/planned (planned null means 3): 1.0 = 3, >= 0.75 = 2, >= 0.5 = 1, else 0
- progress: floor(3 × sum(values) / compared). When compared == 0 (nothing to compare yet) it is 1.
- PR: +1 if any done set `isPr`
- score = clamp(total, 4, 10)

Output: `score`, `doneSets`, `plannedSets?`, `up` (count of UP), `compared`, `prs` (count of sets with isPr).

Expected cases (put these in the tests):
- every planned set done, everything SAME, no PR, normal day → 7
- every set done, 3 UP + 1 SAME, 1 PR → 3+3+floor(2.625)=2+1 = 9
- first workout ever (nothing compared), every set done → 7
- roughDay, every set done, all SAME → 9
- half the planned sets done, everything DOWN → 3+1+0 = 4
- 2 of 12 done, DOWN → clamped to 4
- free workout (no plan day), 2 UP of 2 → 3+3+3 = 9

## Reasons line (localised, EN + formal Arabic, Western digits)
`8/10 · All 12 sets · Up on 3 of 4 · 1 PR`
- sets: "All 12 sets" when done >= planned or planned is null (then "12 sets"); otherwise "10 of 12 sets"
- "Up on 3 of 4", only when compared > 0
- "1 PR" / "2 PRs", only when prs > 0 (use plurals; Arabic plural forms properly)
Separator " · ". Keep the same style as existing strings.

## Where it shows
1. **Finish screen** (`ui/workouts/LiveWorkoutScreen.kt` `WorkoutCompleteScreen` / `DebriefCard`; web `ui/live/LiveWorkoutScreen.tsx` + `ui/coach/debrief.ts`): the score + reasons show at once, above the AI text. While the AI writes, or when it fails, the score stays visible. The waiting text, Try again and the AI summary, highlights, watch and tip stay as they are.
2. **Journal rows** (`ui/history/HistoryScreen.kt`; web `ui/history/HistoryScreen.tsx`): every finished workout with done sets shows the score + reasons line, AI key or not. Compute it in the ViewModel or data layer from data already loaded (progress snapshot or one batched query), never one DB query per row inside composition. Show the AI summary line under it when one is saved, as now.
3. **Debrief sheet** (`DebriefSheet` on both): score + reasons at the top.
4. Web `ui/history/SessionScreen.tsx` mentions debrief too, so check it and keep it consistent.

## AI changes
- `WorkoutDebrief.score` becomes optional/nullable (Android `Int? = null`; web the same). `parse` no longer requires a score. Old saved debriefs and backups must still decode. The UI never shows the AI's score any more and always uses the calculated one.
- Debrief payload: add a line `Score (calculated by the app, do not change it): 8/10. All 12 sets, up on 3 of 4, 1 PR.` (English is fine in the payload).
- System prompt: drop "score" from the JSON shape and the "never score below 4" line. Add that the summary must agree with the given score. Keep the "10-second debrief" wording (the QA stand-in matches on it).
- `tools/qa/gemini_standin.py`: the canned DEBRIEF can keep its "score". Parse must ignore it.
- Update existing debrief tests that expected a score to be required (`PlanSafetyTest.kt` DebriefTest; web `coach.test.ts`).

## Release
- `app/build.gradle.kts`: versionCode 27, versionName "2.15.0". (2.14.1/26 ships just before this. If origin/main isn't at 26 yet, tell the coordinator.)
- `apk/version.json`: versionCode 27, versionName 2.15.0, apk URL for v2.15.0, notes: short professional bullets, for example
  `• Workout score: now calculated from your sets, last time and PRs, with the reasons shown. Every journal workout has one, no AI key needed.`
  `• Coach debrief: the coach explains the score instead of guessing it.`

## Checks before reporting
- Web: `cd web && npx tsc --noEmit -p . && npx vitest run` all pass (node_modules can be copied from `.claude/worktrees/debrief-gemini/web/node_modules`).
- Android: there's no local Gradle build on this PC (CI builds). Be careful with Kotlin syntax, imports and named args. Run nothing heavy.
- Keep EN and AR string files in sync (Android values/ and values-ar/; web i18n en/ar). Escape apostrophes in Android XML (`\'`).
- Match the surrounding code style and comment density. Small, simple changes.

## Report back (short)
The commit hash, the files changed, any rule you had to interpret, and anything left unsure.
