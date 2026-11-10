# Handoff: ForgeQuest session of 2026-11-09 (part 2). For a new Claude session with none of this conversation.

Read this top to bottom once. Older context: `docs/plans/handoff-2026-11-09-full.md` (Simple Home, food extras, decisions up to fa46d27) and `.claude/scratch/handoff.md`. The owner (Abdulrahman) works in English, decides fast, wants calm simple UI, approves before anything is published, and told us: NEVER test or design for 200% text. Nothing from this session is committed, pushed or released.

## 0. State right now
- Branch `feat/simple-home`, base commit `fa46d27`. **About 60 modified files and several new files are UNCOMMITTED** (all of this session's work). Do not `git checkout/stash/reset`. The owner has not yet said "commit". Offer to commit in logical groups (see section 3); never `git add -A` (untracked junk: `.repowise/` 66 MB, `tools/qa/shots`, `tools/motion-video`, `.claude/agents`, `docs/ui`, review-shots output).
- `./gradlew :app:testDebugUnitTest :app:assembleDebug` passes. Lint has ~34 old errors in files we did not touch (Notifier.kt permissions, bidi strings, LocalContext resources): ignore.
- Gradle on this PC needs: `JAVA_TOOL_OPTIONS="-Djdk.net.unixdomain.tmpdir=C:/Temp" ./gradlew.bat ...` run unsandboxed (dangerouslyDisableSandbox). Cause: JDK17 AF_UNIX socket in the user Temp is blocked. `quiet.py` wrapper does not work; call gradlew.bat directly. `C:\Temp` is an empty folder an advisor created.
- Emulator: `G:\Android\emulator\emulator.exe -avd ForgeQuest_Pixel -no-snapshot-save -no-audio` (adb at `G:\Android\platform-tools\adb.exe`). Currently running with the latest debug APK, Arabic language, light mode. QA drivers: `tools/qa/fq.py` (tap/ui/shot) and `tools/qa/review_shots.py` (captures every screen into `review-shots/`).
- Builders: `sonnet-builder-g` (Antigravity/agy) FAILS in headless mode (needs shell permission, makes no edits). Use `sonnet-builder` for big edits or edit directly. `opus-advisor` is used for Gate 1 (plan), Gate 2 (2 failures), Gate 3 (diff review) per the owner's global rules. Hindsight MCP is disconnected: write memory with `python -I ~/.ai-brain/_tools/seed_digest.py ~/.ai-brain/digests/forgequest-2026-11-09-session.json` (already contains this session's facts).
- Project rules (CLAUDE.md): never delete/rename apk/version.json, manifest components, WorkManager names, OAuth ids, Room migrations/schemas, backup field names, web service worker, CI files. EN and formal Arabic strings stay in sync. DESIGN.md Soft Coach applies (updated this session).

## 1. What was built this session (all uncommitted)
1. Finish screen: volume vs last workout of the same plan day ("Volume up 8%") appended to the score line (`domain/WorkoutDebriefMath.kt` volumeChange + tests); Done pinned; plain rows for badges/next-up/reminder.
2. Food: scan icons inside the search field, overflow menu (Add food, Quick log, Last 7 days), "Meal: X" dropdown, "left today" hero, recents from any meal (`domain/FoodLists.kt`), catalogue warm-up after first Home frame (`CatalogRepository.warm()`), search tie-break shorter name first, "Loading food list" state, empty-card copy "Search above...".
3. First-run coach marks replace the slide tour (`ui/components/CoachMark.kt`; keys coach_home/coach_tick/coach_food; tour kept as replay under Settings > App).
4. Offline plan builder: `domain/PlanBuilder.kt`, `PlanRules.kt`, `ui/plan/PlanBuilderScreen.kt` (+ PlanBuilderLabels, PlanLocalize), `data/plan/PlanAnswers.kt`; quick card + optional deep questions; "Built for me" card in onboarding; entries in Plan editor sheet and Settings > Training. Tests: PlanBuilderTest, PlanAnswersTest.
5. "Why this weight" line (`Progression.Reason`), AI off by default (`aiOn`: opt-in OR key saved; existing key holders stay on; Gemini key step removed from onboarding; network AI calls gated).
6. Settings: Profile gear/row -> `Routes.SETTINGS` list of 7 category rows -> `Routes.SETTINGS_GROUP/{id}` pages (`ui/profile/SettingsScreen.kt`, `GroupMode` in ProfileComponents). "Coach and AI" renamed "Advanced: AI".
7. Coach tab with AI off: deterministic cards (recovery, stalled lifts, deload hint, week, Progress row, "Turn on AI coach") (`domain/CoachDigest.kt`). The tab is still called "Coach".
8. Home: rings and stats open Food; ring centre = protein LEFT (serif number + caption); weight stat shows teal "+ Log weight" when empty; badge card moved below Readiness.
9. Workouts: pinned Start button, Library header action. Profile reduced to identity, badges, Progress, Settings (weight card removed). Live workout: effort strip above rest bar (no push), Focus/Discard in an overflow menu, Finish is quiet until every set is ticked; `pickNextSet` for the "Now:" pointer.
10. Notification permission is asked only from the finish-screen reminder button and the Training reminder switch.
11. Look (owner override of the earlier "drop serif/nav" decision): Instrument Serif (EN) + Noto Naskh Arabic (AR) for displaySmall/headlineMedium and hero numbers only; floating pill bottom nav (`PillNavBar` in AppRoot.kt: active tab = tealTrack pill with label, others icon-only); DESIGN.md updated. Thmanyah rejected (licence forbids extractable embedding). Fonts + OFL texts are in `res/font` and `assets/licenses`; downloads in `D:\Claude\downloads\fonts-2026-11-09`.
12. Arabic XP line fixed ("54 من 150").
Docs written: `docs/plans/screen-audit-2026-11-09.md`, `screen-audit-phase1-2026-11-09.md`, `steal-list-2026-11-09.md`, `gemini-review-claims-2026-11-09.md`; briefs in `.claude/scratch/` (fixes-plan.md, phase2-plan.md, phase3-plan.md, serif-nav-plan.md...).

## 2. Open decisions for the owner
- Rename the Coach tab to "Insights" when AI is off (and move Progress under it): recommended by Opus and Gemini; touches the fixed tab bar, so ask.
- Commit now? Suggested commits: (a) plan builder + AI-off + settings, (b) food + home + workouts + live workout, (c) coach marks + typography/pill nav + docs.
- Body text 16 vs 17; 24 dp card padding; web PWA parity (none of this is in `web/`).
- Known accepted gaps: Training reminder saves 18:00 even if notification permission is denied; users with an existing reminder/just-log are no longer asked for rest-timer notification permission.

## 3. The Gemini second-opinion triage (the next job)
The owner ran Gemini on `review-shots/` (README + INDEX included). Its ~100 claims are condensed in `docs/plans/gemini-review-claims-2026-11-09.md`. Opus audited them; the verdicts are in section 4 below (worth doing / reject / build order). Next session: confirm the batches with the owner, then build with `sonnet-builder` in compile-safe steps, Gate 3 after each batch.

## 4. Triage result
Opus checked 60 Gemini claims against the code. Paths under `app/src/main/java/com/forgequest/app/`. Nothing below is built yet unless marked DONE.

### 4a. Owner decisions made at the end of this session (also build these)
- **DONE**: Settings list now starts with "App and appearance" (language, theme): `ui/profile/SettingsScreen.kt` render order. Compiles; not yet seen on the emulator.
- The free Gemini API is weak, so API-key AI stays off by default and is "basic". Make the Gemini **app** route the main AI path: in Settings > Advanced: AI add a section "Use the Gemini app (recommended)" with Copy plan prompt, Paste Gemini's reply (reuse `PlanEditorScreen.kt` ~:445-460 `plan_gemini_copy` / `plan_gemini_paste` logic), and the existing Meals-from-Gemini bridge; put the API key under a second, clearly "basic" section.
- Fourth tab: Progress by default (label "Progress", content = the Progress cards + the deterministic insight cards now on the Coach tab); it is replaced by the Coach chat only if the owner-enabled API key is on (then Progress stays reachable from Profile). This changes the fixed tab bar: the owner proposed it, so it is approved in principle; confirm wording and icon with them when building. Touches `ui/AppRoot.kt` tabs, `CoachScreen.kt`, `ProgressScreen.kt`.

### 4b. WORTH DOING (ranked; S = small)
1. A7 live PR (S) `ui/workouts/LiveWorkoutViewModel.kt:305-310` and haptic :187: every set above the old best goes gold (carryForward copies 11 into later sets). Flag only ONE done set per exercise (highest score above best, earliest wins ties) to match what finish saves (`data/WorkoutRepository.kt` ~:500-520, strictly greater, first session skipped).
2. A9 (S) `LiveWorkoutScreen.kt:395` "I did them all: tick & finish" saves sets nobody did (prefilled with the progression's next target) and can award a made-up PR/XP. Show it only when at least half the sets are ticked; in `WorkoutRepository.finishSession` (~:496) remember the unticked ids before `markAllSetsDone` and leave them out of the PR pass.
3. Keep screen on during a live workout (S): `keepScreenOn` is only set in HoldOverlay (`LiveWorkoutTimers.kt:156`); add a DisposableEffect at the top of LiveWorkoutScreen.
4. A5 (S) `ui/food/CatalogParts.kt:58`: round kcal to whole numbers and add "· N g protein" to catalogue rows.
5. Colour meaning (S) `ui/food/FoodScreen.kt:691,695` protein teal -> gold/goldTrack; :702,706 calories over goal gold -> coral; `ProfileSections.kt:261` gold used as a warning -> textSecondary.
6. A1 set counts (S): score excludes warm-ups (`WorkoutRepository.kt:424`) but the live header (`LiveWorkoutScreen.kt:247,281,286`) and History (`HistoryScreen.kt:353,438`) count them; use `filterNot { WorkoutScore.isWarmUp(it.block) }` in those texts (progress bar can keep all sets). Explains "2 Sets" hero vs "0 of 23 sets" (`WorkoutCompleteScreen.kt:175,273-275`).
7. A2 (S) `ProgressScreen.kt:103`: `Trends.topExercises` (`domain/Trends.kt:149`) ranks by set count so warm-up jumping jacks win; pass non-warm-up sets.
8. A3 (S) `ProgressScreen.kt:207` shows "0 h" under an hour: show minutes under 60; :205 make "workouts" a plural resource (Arabic plural forms).
9. C8 (S) `WorkoutsScreen.kt:222-225,232`: when today's workout is done (Home uses `s.workoutDoneToday`) put a check on today's chip and swap the pinned teal Start for a quiet "Train again" like Home :697.
10. E4 (S) `FoodScreen.kt:1072-1074`: with AI off the Quick log shows a "needs a key" upsell; show nothing when `estimate == null`.
11. B6 (S) `HomeScreen.kt:332-336`: "Good night"/"طابت ليلتك" is a goodbye; use the evening greeting 17:00-04:59.
12. C5 (S/M): nothing outside onboarding sets `settings.name`: add a name field at the top of Body and health (`ProfileSections.kt` ~:211) and remove the Profile top-right gear (`ProfileScreen.kt:194`; keep the Settings row :289).
13. A6 (S) strings: `values/strings.xml:826` and `values-ar:931` say "under About you" -> "under Body and health"; `values/strings.xml:901` "these show defaults" is wrong (fields show "– cm", `ProfileSections.kt:226,231`): "Not set yet. Tap + to set yours." (EN + AR).
14. B3 (S, check on device): Arabic Food hero still shows "140 / 0 غ" (protein) vs "0 / 2,200" (calories) although `values-ar:306` has direction marks: build the protein fraction in Kotlin like calories (`FoodScreen.kt:704`: `"⁦%d / %d⁩ ".format(..) + unit`). (The Arabic XP line was already fixed the same way with "من".)
15. B2 (M) `WorkoutRepository.kt:111-119`: built-plan name/day titles/notes are saved in the language active at build time; the language-switch loop only maps `StarterPlans.arabic`. Add en/ar pairs for `pb_plan_name` x goal, `pb_why_*`, `pb_` day titles. Only affects people who switch language after building.
16. G3 (S) `ProfileSections.kt:926`: drop "Version x" from Credits (already in the updates row :912).
17. Optional (M): persist rest timer `endsAt` so it survives process death (`LiveWorkoutViewModel.kt:39`); the rest notification already exists.
18. Optional: CI/unit guard that Arabic strings contain no U+06A9/U+06CC (none exist today).

### 4c. Deeper bugs Opus found
- Live and saved PRs disagree (3 gold sets and 3 PR haptics live, History shows 1): item 1.
- Tick-and-finish can fabricate PRs, XP and next session's targets: item 2.
- Warm-ups can still be PRs; the `WorkoutScore` comment says warm-ups count "for nothing but a PR", so it may be deliberate: ask the owner.
- Plan text language problem root cause: item 15.

### 4d. REJECT (with reason)
- Conflicts with fixed decisions: C1 (tab count; but see 4a), C2 two-step onboarding (body details are asked later by design), C3/H1 floating pill nav and serif display text (owner decision), D1/D4 Home order, rings, StreakCard are fixed (protein repeated in ring + stat is accepted), E1/E2 Quick log + Add food in the overflow menu and meal dropdown were chosen deliberately (note: Quick log still uses 5 meal tabs, `FoodScreen.kt:847`; owner's call), C6 labelled Workouts header actions were deliberate.
- False or already handled: B1 (no Farsi letters in strings or seed plans; only inside the FoodSearch normalizer), B4 (tanwin present values-ar:865), B5/D5 (rank is a word, not a letter), A4 (moves are dropped when the plan is built and the note says so; the editor count includes warm-up and cool-down), A8 ("Last time" hint exists at `LiveWorkoutSets.kt:220`, prefill from last session, carryForward copies down), D6 (Train again already a text button), E7 (undo shipped in b5abf29), F4 (one tick per warm-up card is by design), F5 (hold timer, swap, +kg exist), F2 (rest bar is already docked), F1 (Finish is grey until all ticked), E5 (clipping was the Gboard toolbar in the screenshot), B7, G3 "49 meals" text (no longer hard-coded).
- True but not worth it now: D2, D3 (weight is weekly), D7, C4, C7, C9-C13, E3, E6, F3, F6, F7, G1, G2, G4, and H2's radii/hairline/separator items.

### 4e. Build order
- Batch 1 (trust + live workout): items 1, 2, 3, 6, 7, 8.
- Batch 2 (food + colour + home): items 4, 5, 10, 14, 11, 9.
- Batch 3 (settings + Arabic + the owner's 4a decisions): items 12, 13, 15, 16, then 4a (Gemini-app section, Progress tab).
- Gate 1 (opus-advisor plan) before each batch, `sonnet-builder` to build, Gate 3 after. Keep EN and AR strings in step; do not touch ReadinessCard, StreakCard internals or ConcentricRings.

## 5. Process notes
- Screens are checked by `python tools/qa/review_shots.py <outdir> <mode> [--fresh] [--only=...] [--ar]` (English-text based; tabs found by name since the nav is a pill).
- The emulator's IME suggestion strip appears in some screenshots: it is not an app bug.
- Memory digest: `~/.ai-brain/digests/forgequest-2026-11-09-session.json` (8 facts, seeded).
