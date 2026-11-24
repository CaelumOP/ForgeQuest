# Set-engine overhaul: final report (Android)

Branch `overhaul/set-engine-and-ux` from `release/4.2` @ `186cc29`. Local commits only (no push, PR, version bump or release APK). Spec: `docs/TRAINING_SCIENCE_SPEC.md` (owner answers in section 0, Gate 1 amendments A1-A12, Phase 5 correction A13). Decisions and tool notes: `docs/SET_DECISIONS.md`. Audit: `docs/SET_AUDIT_LEDGER.md`.

## Verification (raw)

| Check | Command | Result |
|---|---|---|
| Baseline before changes | `./gradlew testDebugUnitTest` | exit 0, 481 tests, 0 failed |
| Final unit tests | `./gradlew testDebugUnitTest assembleDebug` (log `.claude/scratch/gradle-p5fix2.log`) | exit 0, **572 tests, 0 failed** (+91) |
| Final build | same run | `app/build/outputs/apk/debug/app-debug.apk` built 2026-11-24 23:36 |
| Diff | `git diff --stat 186cc29..HEAD` | 53 files changed, 3955 insertions(+), 209 deletions(-), all under `app/` and `docs/` |
| Fence | `git diff --name-only 186cc29..HEAD` filtered | no `web/`, `worker/`, `app/schemas/`, Gradle or `AppDatabase.kt` change; Room stays v12 |
| Gate 1 (plan) | Gemini 4 Argon | CHANGES, 12 points, all applied (A1-A12) |
| Gate 2 (2 strikes) | Gemini 4 Argon | used once (3d focus-volume test), fix applied |
| Gate 3 (final diff) | Gemini 4 Argon | **APPROVED** |
| Cold adversarial review | `codex exec -m gpt-6-astra -s read-only` | DEFECTS: 5 findings; 4 fixed in `b8cd38a`, 1 resolved by spec correction A13 |

Gradle note: on this machine Gradle needs `JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=D:\Claude\tmp\jdk` (JDK 17 cannot create its socket in `%TEMP%`). Wrapper: `.claude/scratch/gw.sh`.

## Commits

| SHA | What |
|---|---|
| `d049f17` | Audit ledger, research, locked spec |
| `50b9a28` | 3a RIR semantics: Hard progresses, Failure holds, missed-rep fix, BACK_OFF / FATIGUE reasons, failure fatigue cost |
| `9eb64e1` | 3b Intra-session adjustment + Undo, +45 s Failure rest, hold snaps to target, 1 s hold stepper |
| `a72933d` | 3c Hybrid baseline (pattern cold start + per-exercise RIR e1RM), 0 pull-ups enterable |
| `9561c51` | 3d Planner compound/isolation rest, reps and order; RoutineAuditor |
| `fc8f06f` | 3e One capped e1RM, recovery skips warm-ups, non-barbell warm-up ramp, live PR pill, warm-ups out of charts |
| `b32ca2b` | 4 UI: stateless set row + previews, tappable effort label, cues, audit card, 48 dp chips/stepper |
| `b8cd38a` | 5 Cold-review fixes |

## Before vs after

| Area | Before (with file:line at `186cc29`) | After |
|---|---|---|
| Effort semantics | Hard ("1-2 left") blocked progression like Failure (`domain/SetEffort.kt:12` `isStrain`; `Progression.kt` `strained`) | Hard "1-3 left" progresses; only Failure holds; blank = implicit normal (RIR 2) everywhere; legacy "OK" shows no chip (`SetEffort.chipOf`) |
| Missed reps | 4 of 8 untagged -> next 9 reps (`Progression.kt` `completed` checked set count only) | MISSED holds the weight; missed sets restart at the bottom of the range, met sets keep their reps (A13); missed at Failure -> FATIGUE; twice -> BACK_OFF one load step |
| Intra-session | None; `carryForward` copied failed reps (`LiveWorkoutViewModel.kt:337`) | `domain/IntraSession.kt`: Failure or 2+ under minimum cuts remaining sets 5 % / 10 % (plate-snapped, lb aware) or caps reps/seconds; cue + Undo; untick reverts; hand-edited sets untouched |
| Rest | Static (`LiveWorkoutViewModel.kt:74`) | +45 s (cap 240 s) when Failure is tagged during that set's rest; never on rest-pause or superset transitions |
| Hold sets | Saved raw elapsed seconds (reaction lag -> 32 s for a 30 s target); stepper 5 s | `domain/HoldMath.kt`: stop within target..target+3 saves the target; stepper 1 s (keypad +5/+10/+30 kept) |
| Baseline | Experience + push-up/pull-up max only; 0 pull-ups unreachable (`PlanBuilderScreen.kt:558`) | `domain/Baseline.kt`: per-exercise RIR-adjusted e1RM (1-12 reps), cold-start weight from the most recent same-pattern / same-bucket lift by equipment ratio x 0.9; "Estimated start" line in the live card; 0 enterable |
| Build Planner | Isolations 4-6 reps and 150 s rest on Strength; no auditor (`PlanBuilder.kt:310-358`) | Compound vs isolation reps and rest, compounds first, core then finisher last; focus volume never drops under time trim; `domain/RoutineAuditor.kt` (MEV/MRV with 0.5 secondary credit, push/pull 1.2, isolation-first, junk volume > 10, weekday recovery overlap) shown in plan editor and builder |
| PR / e1RM | Progress e1RM uncapped (`ProgressMetrics.kt:49`); PR score +3.3 % on singles (`ProgressRules.kt:444`); live PR pill only after finishing (`LiveWorkoutSets.kt:262`); charts included warm-ups (`ExerciseDetailScreen.kt:165`) | `domain/Strength.kt` single capped e1RM shared by both; live pill; charts and History WeekCard use working sets only |
| Warm-ups | Always floored at a 20 kg bar, even for dumbbells (`WarmupRamp.kt:56`) | Non-barbell floor = one load step; heavy barbell ramps start with the empty bar x 10; new heavy barbell items default to auto warm-up |
| Recovery | Warm-up set types counted as full fatigue; Hard/Failure/Pain all 1.15x (`ProgressRules.kt:311-382`) | Warm-ups and stretches excluded; Easy 0.8, Hard 1.05, Failure 1.25, Pain 1.15; loaded sets count at least half a set |
| Debrief / score | Debrief next-time ignored deload/lower body (`WorkoutDebriefMath.kt:324`); score missed pain in notes (`WorkoutScore.kt:116`) | Debrief matches the live prefill; notes count |
| UI | Effort chips gone after rest; ChoiceChip ~38 dp, stepper value 40 dp; no previews | Tap a done set's effort label to re-tag; 48 dp; 8 `@Preview`s (cold start, untagged, Hard, Failure, adjusted next set, PR pill, audit empty/mixed) |

## Test names added (selection; all pass)

`ProgressionRirTest` (16: `blankAtTopOfRangeLoadsUp`, `hardAtTopOfRangeLoadsUp`, `failureAtTargetHoldsWithHardReason`, `missedRepsUntaggedHoldsAtMinimumNotPlusOne`, `missedRepsKeepMetSetsAndRestartTheMissedOneAtTheMinimum`, `missedAtFailureHoldsWithFatigue`, `missedAtFailureTwiceStepsDownBackOff`, `bodyweightMissedRepsHoldsAtRepsDone`, `bodyweightFailureHolds`, `linearInPoundsStepsFiveAndTen`, `legacyOkChipShowsNothing`, `rirMapping`, `recoveryFailureCostsMoreThanHard`, `readyForHarderIgnoresHardButNotFailure`, `debriefNextTimeMatchesLivePrefillInDeloadWeek`), `IntraSessionTest` (16, incl. `set1FailureCutsSet2And3ByFivePercentSnappedToPlateStep`, `poundUserCutsInFivePoundSteps`, `bodyweightFailureNeverRaisesTheNextTarget`), `HoldMathTest` (5), `LiveWorkoutIntraSessionTest` (2), `BaselineTest` (incl. `coldStartFromSamePatternBarbellToDumbbell`, `backDatedWorkoutDoesNotWinRecency`), `PlanBuilderStepperTest`, `LiveWorkoutBaselineTest`, `RoutineAuditorTest` (12), `PlanBuilderTest` (+5), `StrengthTest` (5), `WarmupRampTest` (5), `LiveWorkoutPrPillTest`, `ProgressTest` recovery/records additions.

## Not covered by automated tests (check on the device)

The ViewModel paths need the app container, so these were reviewed (Gate 3 + Codex) but not unit tested:
1. Tick a weighted set at 4 of 8 -> later sets drop 5-10 % and the cue shows; Undo restores; untick the source restores too; re-tick re-applies once.
2. Tick a set, then tag Failure during its rest -> rest grows by 45 s once; no growth on a superset A1 transition or rest-pause.
3. Bodyweight set missed to failure -> cue appears and Undo works (Codex finding 1).
4. Plank 30 s: stop at 31-33 s -> saves 30; stop at 34 s -> saves 34; effort chips appear after a timed hold.
5. A brand-new dumbbell exercise after logging barbell bench -> "Estimated start" and a prefilled weight; warm-up ramp not floored at 20 kg.
6. Plan editor audit card on an existing plan (read-only warnings), Arabic layout (RTL) and 200 % font.

## Deferred / watch list

- Effort editing inside History's session detail (A12, deferred).
- Web PWA parity (out of scope): `web/` still has the old Hard-holds rule, no intra-session adjustment, no auditor, 5 s hold stepper and raw hold seconds.
- Existing saved `isPr` flags are not recalculated; singles now score at the exact weight for new PR checks.
- XP is unchanged on purpose (still counts warm-up sets) so existing levels do not drop.
- `AGENTS.md` says Room "version 10"; it is 12 (not edited: instruction file, outside this task).
- Claude advisor tool was unavailable (plan usage-credits requirement); reviews used Gemini Argon and Codex.
