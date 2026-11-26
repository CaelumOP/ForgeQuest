# ForgeQuest master upgrade: final report

Branch `feat/forgequest-complete-upgrade`, 2026-11-26. Android (`app/`, reference) and web PWA (`web/`) ship the same behaviour, in English and formal Arabic.

## Plan quality: before and after (dev grader, same sample on both platforms)

| Measure | Before | After |
|---|---|---|
| Overall mean score | 81.65 | 87.85 |
| Worst single plan | 41.88 | 68.57 |
| 3 days x 20 min: muscle coverage (of 25) | 17.8-18.6 | 22.2-23.8 |
| Fat loss 6 days: balance (of 20) | 3.0-16 | 15.1-17.6 |
| 20-min plans: balance (of 20) | 14.55 | 18.37 |
| Superset pairing (of 15) | n/a | 15 |

Scorecards: `docs/master/grader-before.md`, `docs/master/grader-after.md`. Floors are enforced by tests on both platforms (`plan-grader-floors.test.ts`, `PlanGraderFloorsTest.kt`, shared digest `tools/plan-grader.golden`). Android and web build byte-identical plans for all 11,340 sampled inputs (`tools/plan-matrix.golden`).

## What changed

### 3A Plan builder
- Workout style option: Auto / Straight sets / Supersets / Circuits.
- Supersets and circuits with no rest inside a round and a short rest after it (40 s after a pair, 30 s after a 3-move circuit on days of 30 min or less; 60 s for circuits and the longer of the two rests for supersets on longer days).
- Clean A/B split ("Push and legs" / "Pull, hinge and core") for fat loss with 5-6 lifting days, and for any goal on days under 30 min with 4+ lifting days.
- Warm-ups that match the day; one "Group with next" control in the plan editor; fresh plans skip the Plan check.

### 3B Find your starting point (#10-#16)
- New screen on both platforms: push-up, pull-up (only with a pull-up bar), plank hold, the first 3 weighted main lifts, plus any move from the picker. Manual entry, tap counter for reps, hold timer, weighted submax.
- Targets: reps 70 % of max; hold 70 % rounded down to 5 s; weighted from an estimated 1RM with a 0.9 safety factor, snapped to the load step; more than 12 reps = "too light to judge"; 0 reps suggests an easier version of the same move (identical progression chain on both platforms).
- Precedence when a workout is built: history > starting-point test > estimate > blank. Live cards say "From your baseline"; a weighted set with no weight shows "Add weight" and a "Test this move" button, and a test saved from there fills that move's undone empty sets right away.
- Storage: Room `baseline_tests` (DB 12 -> 13, migration + exported schema), Dexie store in a new version, optional backup field `baselineTests` (old backups restore unchanged). Old plan-builder push-up / pull-up answers move into test rows (at startup on both platforms).
- Entry points: Workouts card (until the first workout or test; dismissible), Profile > You and your goals > Starting point, Build my plan "Test your starting point" (unsaved builder choices are kept).

### 3C Cut list (#17-#43) and polish (D)
| # | Item | Result |
|---|---|---|
| 17 | Home status line | Killed |
| 18 | Progress tagline | Killed |
| 19 | Progress "This week" line | Killed |
| 20 | Progress Recovery card | Killed (the Workouts muscle map still shows recovery) |
| 21 | Progress "Then vs now" | Killed |
| 22 | Live "How to do it" / "Swap" pills | Hidden: card ⋮ menu has both; tapping the title opens How to do it |
| 23 | "Mark warm-up done" button | Killed (the tick box does it) |
| 24 | Profile Settings gear | Killed |
| 25 | About copy of "How to use" | Killed |
| 26 | Health Connect "Test connection" | Killed (permissions and status check kept) |
| 27 | Form cue sentence on the card | Moved into How to do it |
| 28 | Rest preset chips + Copy last set | Shown only after tapping the timer |
| 29 | Plate breakdown chip | Shown when the weight is tapped |
| 30 | Muscle map before the first workout | One line "Muscle recovery shows here after your first workout"; web chips wrap |
| 31 | Getting started | 3 items (measurement and friend dropped) |
| 32 | Gemini key banner | Food scan screen only |
| 33 | Plan editor Schedule card | Moved into the "Change plan" sheet |
| 34 | Progression mode chips | Under "Advanced" with plain labels; stored values unchanged |
| 35 | History card extras | Moved into the workout detail |
| 36 | Workout complete insights | At most 1 extra sentence |
| 37 | Coach tips | Never over the rest timer or a set row; one per screen; after the first tick |
| 38 | Home protein stat | Merged into the ring |
| 39 | Build my plan Days / Minutes / Where you train | Sliding segmented pills (RTL, reduced motion, radio-group semantics, 48 dp/px) |
| 40 | "Training load 64 %" ring | "This week vs your usual: +12 %" |
| 41 | JSON / API key / Gem jargon | "Share plan", "Import a plan", "Connect Gemini" |
| 42 | Truncated catalog cues | 466 English cues completed (all 2,583 Arabic cues were already complete); `tools/catalog/audit-cues.mjs` + tests on both platforms |
| 43 | Goal card subtitles | "Moderate weights · 8-12 reps · 90 s rest" style |

D: the wavy rest timer now runs at display frame rate from the saved end time (Android frame clock, web requestAnimationFrame), with a calmer wave over a flat track and a flat line under reduced motion; +45 s Failure, -15 / +15, Skip and resume kept. Android rebuilds a running rest after the app is killed, from the saved alarm. Web live dock is capped at 640 px.

## Verification (final)

| Check | Result |
|---|---|
| Android `:app:testDebugUnitTest` | exit 0, 665 tests, 0 failures |
| Android `:app:assembleDebug` | exit 0 |
| Web `npx tsc --noEmit` | exit 0 |
| Web `npx vitest run` | exit 0, 810 tests in 85 files |
| Web `npm run build` | exit 0 |
| Golden parity | plan matrix + grader digests match on both platforms (inside the test runs above) |
| i18n | export clean, EN/AR key sets equal, no `?` corruption in `values-ar` or `ar.json`, no overlay key equal to the generated text |
| Browser smoke, 390x844, EN + AR | PASS: Playwright (headless Chromium) on the dev server, 64 screens across both languages; no console errors, no horizontal overflow, `dir=rtl` on every Arabic screen; supersets (0 s inside the pair, full rest after), all 5 baseline result types, wavy timer with -15/+15/Failure/Skip, cut-list screens. First run found English "8-12 reps" in Arabic superset rows; fixed and re-run clean. Not measured by script: pill slide motion and animation smoothness. "Test this move then return" is covered by unit tests on both platforms, not by the browser run (Codex hit its usage limit before the extended re-run). |
| Gate 3 | APPROVED by Gemini Argon after two fix rounds (Argon: 5 findings + 2 on re-review, all fixed; Codex Astra: 3 findings, 2 fixed, 1 rejected with reason). Codex's own re-check of the fixes did not run (usage limit until 10:24); Argon reviewed all fixes, including Codex's. |

Notes:
- `Overlays.test.tsx` does not assert the unmount cancel (its hand-rolled fake DOM never runs Preact effect cleanup); the cancel is in the component and the smoke run covers the live timer.
- One Codex Gate 3 finding was rejected on purpose (a "migration done" flag would block importing maxima from an old backup restored later); see `docs/MASTER_DECISIONS.md`.
- `CLAUDE.md` still says the Room schema is version 10; it is 13 now. CLAUDE.md was not edited in this branch.

## Commits
- `e1c4517` Master upgrade Gate 3 re-review (web): live baseline fill waits for the active plan; legacy baseline migration has no persisted flag (idempotent, re-imports after an old backup restore) + regression test
- `344aa58` Master upgrade Gate 3 (web): Test this move fills blank sets, easierVariation chain parity with Android, PlanBuilder baseline sync, 48 px targets, localized superset target labels, legacy baseline migration at startup …
- `c33e1cd` Master upgrade Gate 3 (Android): Test this move fills open-session blank sets + FROM_BASELINE refresh, effort.isBlank() after process death, superset set rows localize targetLabel
- `1bc95b5` Master upgrade: progress, 3C done, Stage 4 running
- `69cc015` Master upgrade 3C-7 (#42): audit-cues script, 466 truncated EN catalog cues completed in tools/catalog source, Android strings_catalog and web en.json; AR cues already complete; dangling-ending tests on both platforms …
- `3867ab5` Master upgrade 3C-6 (web D): 60 FPS wavy rest bar from endsAt via requestAnimationFrame (flat track, reduced motion), live dock max 640 px, sliding segmented pills for Days, Minutes and Where do you train (#39)
- `a0685eb` Master upgrade: progress during 3C
- `6f3ed43` Master upgrade 3C-5 (web B): cut list part B mirrored on web (live card pills hidden, cue into How to do it, rest presets on tap, plates on weight tap, recovery line, schedule into Change plan, Advanced progression, co…
- `992195c` Master upgrade 3C-3 (Android D): 60 FPS wavy rest timer from endsAt (frame clock), cold-process rest rebuild from the saved alarm, sliding segmented pills for Days, Minutes and Where do you train (#39)
- `a32ea20` Master upgrade 3C-2 (Android B): #22 hide pills (menu + title tap), #23, #27 cue into How to do it, #28 rest presets on tap, #29 plates on weight tap, #30 recovery line before first workout, #33 schedule into Change pl…
- `3de06ac` Master upgrade 3C-4 (web A): cut list part A mirrored on web (Home, Progress, Profile, History, Workout summary, Scan); 794 tests green
- `a6d6476` Master upgrade 3C-1 (Android A): cut list #17-#21, #24-#26, #31, #32, #35, #36, #38, #40, #41, #43 (Home, Progress, Profile, Settings, History, Workout complete); 651 tests green
- `c557368` Master upgrade: progress after 3B
- `5b0cde8` Master upgrade 3B (web UI): Find your starting point flow, entry points (Workouts card, Profile row, Build my plan), From your baseline label, Add weight + Test this move
- `58587a9` Master upgrade: progress, briefs for 3B web UI and 3C
- `fac12be` Master upgrade 3B (Android UI): Find your starting point flow, entry points (Workouts card, Profile row, Build my plan), From your baseline label, Add weight + Test this move
- `d790fe3` Master upgrade 3B (data): baseline_tests table (Room 12->13 + schema, Dexie version), BaselineTargets, precedence history > baseline > donor, optional backup field
- `a714a56` Master upgrade 3A: workout style, supersets and circuits with zero intra-round rest, fat-loss A/B split, day warm-ups, Group with next, Android grader port + floors
- (this report + progress commit follows)

## Phone checklist (Android APK)
1. Install the debug or release APK over the current app; open it: your workouts, plan and history are still there (DB 12 -> 13 migration).
2. Build my plan: Days / Minutes / Where you train pills slide; pick 3 days, 20 min, Supersets; build.
3. Start the workout: tick the first move of a pair, no rest starts and the next move is highlighted; tick the second, the wavy rest bar runs smoothly; try -15 / +15 / Failure / Skip.
4. During a rest, swipe the app away and reopen it: the rest is still running with the right time left.
5. Profile > Starting point: test push-ups with 0 reps (easier version offered), 10 reps (Start with 7), plank 30 s (Start with 20 s), a dumbbell move 20 kg x 8.
6. Start a workout with a never-used dumbbell move: "Add weight" + "Test this move"; test it and come back: the sets show a weight and "From your baseline".
7. Switch the phone to Arabic: every screen above reads right to left with no English left over.
8. Restore an older backup file: it restores without errors.
