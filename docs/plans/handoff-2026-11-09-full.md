# Full handoff: session of 2026-11-09 (ForgeQuest)

For a new Claude session that has none of this conversation. Read it top to bottom once. It says what was decided, why, what was
rejected, what is built, what was and was not tested, and what to do next. Older context: `.claude/scratch/handoff-food-2026-11-04.md`
(food database pipeline), `docs/plans/handoff-2026-11-01.md` (design-system start). The short pointer file is
`.claude/scratch/handoff.md`; this document replaces it for anyone who needs the reasons.

The user (Abdulrahman, project owner) works in English, decides quickly, wants calm simple UI, and approves before anything is
published. Nothing from this session was pushed or released.

---------------------------------------------------------------------------------------------------------------------------------
## 0. READ FIRST: warnings

1. **The working tree has about 29 modified files that this session did NOT make.** When the session ended, `git status` showed
   uncommitted edits (about 808 insertions / 528 deletions) in: CoachRepository, FoodRepository, WorkoutRepository,
   DriveSyncWorker, SettingsRepository, Progression, WorkoutDebriefMath, AppRoot, CoachScreen, BarcodeScan, FoodScreen,
   ScanScreen, HomeScreen, AppTour, OnboardingScreen, ProfileComponents/Screen/Sections/ViewModel, ExercisePicker,
   LiveWorkoutScreen/Sets/ViewModel, PlanEditorScreen, WorkoutCompleteScreen, both strings.xml, ProgressionTest,
   WorkoutDebriefMathTest. This session's last commit (`fa46d27`) left the tree clean of tracked changes, so another session or
   process edited the same folder afterwards. `HomeScreen.kt` and `FoodScreen.kt` are in that list, and this session's work lives
   in those files too. **Do not `git checkout`, `git stash`, reset or commit blindly.** Run `git diff <file>` per file, ask the user
   which session owns them, and keep this session's two commits (`b5abf29`, `fa46d27`) distinct from them.
2. **Nothing is pushed, no version bump, `apk/version.json` untouched.** The user said: test first, publish only if they like it.
3. **A debug APK cannot update the user's installed release app** (signing key mismatch; there is no local `keystore.properties`,
   the release key is in CI). Installing it on their phone would require uninstalling the real app and losing its data.
   Never hand them a debug APK to install over the real app. See section 6.
4. Project rules that always apply (from `CLAUDE.md` files): never delete/rename the items in the "Never delete or rename" list
   (apk/version.json, manifest components, WorkManager names, OAuth ids, Room migrations/schemas, backup field names, web service
   worker, CI workflows); English and formal Arabic kept in sync; UI follows `DESIGN.md` (Soft Coach); the user's global rules
   require an `opus-advisor` Gate 1 (plan) before editing 2+ files or UI design, Gate 2 after two failed builds, Gate 3 diff review
   before saying done; anything Claude installs goes under `D:\Claude` with a line in `D:\Claude\MANIFEST.md`.
5. The Hindsight MCP server was disconnected at the end of the session, so facts were not written through the tool. A digest JSON
   was prepared instead (see section 9).

---------------------------------------------------------------------------------------------------------------------------------
## 1. Branches and commits (all local)

| Branch | Commit | What |
|---|---|---|
| feat/food-database | `aa4fcf3` | refactor: split Profile and Live workout screens into smaller files (pure code moves, no behaviour change) |
| feat/food-database | `51ccd91` | Food: barcode fixes, catalogue rebuild, EN/AR strings (catalog.db, BarcodeLookup, OpenFoodFacts, Barcodes, CatalogRepository, CatalogParts, build_catalog.py, off_bulk.py, off.csv) |
| feat/food-database | `b5abf29` | Food: sort by protein, log again with undo |
| feat/simple-home (branched from b5abf29) | `fa46d27` | Home: simpler look (Soft Coach, quieter) |

Earlier commits already on the branch: `4f8a950` (core Kotlin simplification + ICM routing contracts), `6daa866` (food list and barcode
scanning WIP). `main` is the PR base. Current branch when the session ended: `feat/simple-home`.

Why two commits first: the 17 uncommitted files from the previous session were really two different things. The screen splits
(Profile and Live workout) are a pure refactor; the barcode/catalogue/strings work is food. Separate commits keep each reviewable and
revertable. Untracked files were deliberately NOT committed: `.repowise/` (66 MB), `tools/qa/shots/`, `tools/motion-video/`,
`docs/plans/*` drafts, `docs/ui/`, `.claude/agents/`, `.claude/launch.json`.

**A mistake to know about:** the first food-extras commit accidentally included `tools/motion-video` and `tools/qa/shots` because
`git add -A tools` was used. It was caught immediately and rewritten (soft reset, unstage tools, recommit) before anything was pushed.
Lesson: add explicit paths only. Before the commit that was rewritten was `a8dbd78`; the current one is `b5abf29`.

Build/test status at each commit: `:app:testDebugUnitTest :app:assembleDebug` green (272 tests at the time of the food extras).

---------------------------------------------------------------------------------------------------------------------------------
## 2. Food extras (done, committed in b5abf29)

The previous handoff listed four extras: household units polish, log again / copy meal, "plate", sort by protein. The user chose
"Sort + log again" first. Not started: household units polish, "plate" (nobody knows what the user meant; ask them).

### 2a. Sort by protein
- **What:** a "Most protein" FilterChip next to the "From the food list" heading. When on, the food-list matches show highest protein
  per 100 g first.
- **How (and why):** `Catalog.search(foods, query, limit, byProtein = false)` in `domain/CatalogFood.kt`. It keeps the normal relevance
  pipeline (score, then scans as tie-break), takes the best 40 (`PROTEIN_POOL`), then `sortedByDescending { protein }`, then `take(limit)`.
  Kotlin's sort is stable so equal protein keeps relevance order. Reason for the 40 pool: sorting only the top 12 by relevance would
  hide high-protein matches; sorting everything would surface irrelevant junk. `CatalogRepository.search(query, limit, byProtein)`
  passes it through. In `FoodViewModel` (`ui/food/FoodScreen.kt`): `val sortProtein = MutableStateFlow(false)` and
  `catalogResults = combine(query.debounce(150), sortProtein) {...}.mapLatest { app.catalog.search(q.trim(), 12, byProtein) }`
  (the chip toggle is outside the debounce so it responds instantly).
- **Strings:** `food_sort_protein` = "Most protein" / "الأعلى بروتيناً".
- **Test:** `CatalogTest.byProteinPutsTheHighestProteinFirstAndKeepsTiesInRelevanceOrder` (the test helper `food()` got a `protein`
  parameter). Own test mistake worth remembering: first version asserted the wrong id as lowest-protein; fixed.
- **Rejected:** sorting the user's own foods too (small lists, no need); a sort menu with several options (user asked only for protein).

### 2b. Log again
- **What:** a small replay icon (18 dp, `Icons.Rounded.Replay`, textSecondary, inside a default 48 dp IconButton) on each logged meal
  row, before the delete X. It copies that meal to the day currently open, so it doubles as "copy meal" from a past day.
- **How:** `FoodRepository.logAgain(meal, epochDay): Long` = `dao.insertMeals(listOf(meal.copy(id = 0, epochDay, loggedAt = now))).first()`.
  `Daos.insertMeals` now returns `List<Long>` (return type only; no schema change; the two old callers ignore it).
  `FoodViewModel.logAgain(meal)` logs, buzzes (`haptics.setDone()`), re-evaluates badges, and offers undo via
  `undo.offer(...) { app.food.deleteMeal(id) }` (same pattern as `deleteMeal`). The copy keeps category, quantity, photo and foodId.
- **Why undo:** the replay button sits next to delete; a single tap should be reversible (Opus advisor's point).
- **Known/accepted:** undo does not take back badges unlocked by the extra log (other log paths behave the same).
- **Strings:** `food_log_again` = "Log again" / "سجّل مرة أخرى".
- **Rejected:** a confirmation dialog (too heavy); a separate "copy meal" button (same thing).

Opus Gate 1 (approve with 3 changes: stable sort after take(40), undo via new id, 18 dp icon) and Gate 3 (APPROVED) were both run.

---------------------------------------------------------------------------------------------------------------------------------
## 3. The UI rethink: why

User message (paraphrased): the "premium" Home look went too deep. There is premium in simplicity too. Take what is good from the
premium draft and put it into the current look, keep it simple and pure. Reference: github.com/dickwu/apple-design-skill. "Apple does
simplicity in tech for non-tech people very well."

### 3a. What the Apple design skill actually is (read via a web-fetch subagent)
A review method grounded in Apple's Human Interface Guidelines (123 HIG pages plus a Liquid Glass guide), not a component library.
Principles used: simplicity = every element must earn its place; hierarchy from size and weight, progressive disclosure over density;
few typefaces and a clear scale; one colour means one thing, and meaning never carried by colour alone; motion is purposeful, brief,
cancellable, rare on frequent actions, reduced-motion aware; blur/glass only on floating functional layers, never on content; launch
fast and teach through use. Numbers it gives: body 17 pt default / 11 pt minimum; contrast 4.5:1 (3:1 for large or bold); touch
targets 44 pt default. It does NOT give a spacing scale, radii, or animation durations. Its "don'ts" include template looks (warm
cream + serif + terracotta; near-black + acid green), big-number hero with gradient accent, and decoration mistaken for delight.
The `references/` HIG pages were not fetched.

### 3b. What the premium draft was (`docs/ui/premium-home-v3.html`, also `premium-home.html`)
Drifting blurred gradient "sky" blobs plus film grain; Instrument Serif display font plus Alexandria/Readex/Manrope/Plex Arabic options;
dashed ring guide tracks with end-cap knobs and a scalloped "cookie" check badge; frosted blurred workout card over a photo; a floating
nav pill plus a round "+" button; uppercase letter-spaced labels; a breathing readiness glow; cards rising in with staggered animation.

### 3c. Decision (user approved: "Yes, mock it up", then "build it")
Superseded by the owner on 2026-11-09: serif display text and the floating pill nav are back; gradients, glass, photo and FAB stay out.
**Keep** from premium: big centred rings with the number inside; greeting header plus one calm status line; stats row with big numbers
and thin dividers (Google-Fit style); readiness as one sentence plus three facts; the RTL type fixes (no uppercase/letter-spacing in Arabic).
**Drop:** gradient and grain, frosted card and photo, serif and extra fonts, dashed guides, knobs, pop-in badge, floating nav and FAB,
uppercase spaced labels, breathing glow, staggered card entrance.
**Why:** `DESIGN.md` (Soft Coach, chosen 2026-11-01) already says flat, no shadows/gradients/glows/blur, one teal accent, gentle motion,
and it matches the Apple skill's rules. The premium draft had drifted away from the project's own design system; the right move was to
return to Soft Coach and bring over only the premium structure that helps, not to invent a new look.

**Not done and why (be honest about gaps vs the mock):**
- Body text was NOT raised from 16 to 17 (Apple's default). It is a global typography change touching every screen; the mock did not
  need it. Open question for the user.
- The mock's "This week" card was omitted because `DESIGN.md` fixes the Home order (header, rings, stats, workout, readiness).
- The mock puts calories in the ring centre; the real app keeps **protein** in the centre (a product decision, not visual language).
  Only swap if the user asks.
- The mock's readiness card is simplified; the real `ReadinessCard` (three dials, each opens its own detail page, traffic-light
  recovery colours) was intentionally left unchanged.

### 3d. The mock-up
`docs/ui/simple-home.html` (single file, open in a browser): EN/AR toggle (dir flips), dark/light, 100%/200% text size. Uses only Soft
Coach tokens from `DESIGN.md`; Manrope and IBM Plex Sans Arabic load from Google Fonts for the mock only (the app ships them). During
checking, the calorie number overflowed the ring at 200% text; fixed by capping (`min(2.75em, 46px)`) and re-checked: no horizontal
scroll, text wraps. Arabic light mode verified (RTL mirrored, ring mirrored).

---------------------------------------------------------------------------------------------------------------------------------
## 4. The Android Home build (commit fa46d27)

The real Home is richer than the mock (calorie card, badges, streak, progress, nudges, bridge line), so the mock only defines the
visual language. An `opus-advisor` Gate 1 read the real code and produced a ranked change list; all of it was implemented except
where noted. Files: `ui/home/HomeScreen.kt`, `ui/home/CalorieCard.kt`, `ui/home/ReadinessCard.kt`, `ui/components/Common.kt`.

| # | Change | Why |
|---|---|---|
| 1 | **Rings:** protein ring is now `c.gold`/`goldTrack` (was teal); calories ring is always `c.blue` (the old gold-when-over swap is removed; going over is already shown by the ring's second lap); centre protein number is `textPrimary` (was teal); centre numbers capped at ~1.3x font scale via `capped(sp)` = `sp * min(fontScale,1.3)/fontScale` so they stay in the ~130 dp inner ring (TalkBack still reads the full `description`). | DESIGN.md: teal = tappable/"on" only; blue/gold/purple = data. Protein as teal broke "one colour, one meaning". |
| 2 | **Legend row deleted.** Each stat now has its own coloured dot (protein gold, carbs purple, calories blue, weight none), numbers in `textPrimary`, thin `c.divider` lines between stats in the single-row layout only (not in the 200%-text FlowRow). `StatColumn` (Common.kt) gained an optional `dot: Color? = null`; default null so Profile and WorkoutComplete are unchanged. | The legend duplicated what the dots now say; fewer elements, same information. |
| 3 | **TodayCard:** icon circle removed; title `titleLarge`, max 2 lines; the single action (Start / Resume / Set up / Train anyway) is a full-width pill on its own line below the text; done-today shows a check icon at the end of the title row. The old `big` (reflowText) branch is gone. | Simpler and safe at any text size. |
| 4 | **Header:** date small above, greeting is the one big line (`headlineMedium`, 2 lines); rank letter is `textPrimary` (blue progress arc kept: it is data). | Hierarchy from size and weight. |
| 5 | **Spacing:** every Home card gap `12.dp` became `ForgeSpace.CardGap` (16): HomeScreen, trailing spacer in `CalorieCardView`, three trailing spacers in `ReadinessCard.kt`. The badge card and ProgressCard dropped their `PaddingValues(16.dp)` and use the default 24 dp card padding. | DESIGN.md: gap 16, card padding 24. **Confirm with the user that the 24 dp on those two cards is wanted.** |
| 6 | **Colour fixes:** ProgressCard "open" link `chartGain` -> `c.teal` (it is tappable); bridge-line title `c.purple` -> `c.textSecondary` (purple is data only). | Colour = meaning. |

**Do not touch (advisor):** ReadinessCard structure and recovery traffic-light colours; the bottom nav in `AppRoot.kt` (already flat, labelled,
teal indicator; 5 tabs stay); StreakCard internals; `ConcentricRings`; `PillButton`; the `home_legend_*` strings (FoodScreen and the web
app still use them, even though Home no longer does).

**Opus Gate 3 found a real regression, fixed:** my first TodayCard hid the Resume button when you had already trained today and then
opened a second session (`hasAction = !doneToday && ...` skipped `hasOpenSession`). Fix:
`hasAction = hasOpenSession || (!doneToday && (noPlan || !isRest || excused == null))`, and the check icon only when
`doneToday && !hasOpenSession`. Also removed unused imports `Edit` and `FitnessCenter`. (`Bedtime`, `CircleShape`, `IconCircle` are
still used elsewhere.) Build and unit tests passed after the fix.

---------------------------------------------------------------------------------------------------------------------------------
## 5. What was tested, and what was not

**Tested (emulator `ForgeQuest_Pixel`, 1080x2400):** English light and dark; Arabic dark (RTL: header, rings, dots and dividers mirror
correctly; numbers stay Western digits by app design); 200% font scale (header, stats wrap to two per row, rings centre fine, workout
card wraps, no horizontal clipping); rings with real data (95 g protein and 1,900 kcal gave a filled gold ring and a filled blue ring).
Unit tests (`:app:testDebugUnitTest`) and `:app:assembleDebug` green, before and after the Resume fix.

**NOT tested:**
- Emulator after the last 2-line Resume fix (build + unit tests only).
- TodayCard states: no plan, rest day, excused day, done today, resume-in-progress, "train again". Only the plain "Start" state was seen.
- Arabic light, and Arabic at 200% text.
- A kcal surplus (over goal) ring.
- TalkBack output.
- Web PWA (`web/`): still has the old look; parity is a follow-up (the web CLAUDE.md applies).
- Real phone with Health Connect / Fitbit data: the emulator has no real readiness data.

---------------------------------------------------------------------------------------------------------------------------------
## 6. Open decision: how the user tests it on their phone

Debug APK vs installed release app: same package id, different signing key, so Android refuses to update (and uninstalling loses data).
No local release key exists. Options offered (the user went to sleep before answering):
1. **Side-by-side test APK:** add `applicationIdSuffix = ".test"` (or similar) to the debug build type so it installs next to the real
   app with separate data. Cost: Google sign-in/Drive OAuth and Health Connect are tied to the package id and will not work in it;
   fine for judging the look. This is a build.gradle change (debug only).
2. **Emulator only:** already installed there; send more screenshots.
3. **CI release flow after approval:** the normal route (versionCode + `apk/version.json` + short notes + credits line); the CI workflow
   signs with the real key. Only after the user approves.
Recommendation given: option 1 for a quick on-phone look, option 3 to ship. Ask before doing either.

---------------------------------------------------------------------------------------------------------------------------------
## 7. Next steps (suggested order)

1. Resolve the unexpected 29 modified files (section 0.1) with the user before any commit.
2. Ask the user the test-route question (section 6) and the 24 dp card padding question (section 4, row 5).
3. Re-run the emulator on the final build: the untested states in section 5, Arabic light, a surplus ring.
4. Update `DESIGN.md` and the `forgequest-design` skill if needed: it already says blue/gold/purple are data colours, but the Home rule
   "protein ring" colour should be stated explicitly (gold now, not teal) so web and Android agree.
5. Web PWA: port the same Home changes (gold protein ring, dots instead of legend, thin dividers, simplified workout card, header).
6. Release (only after approval): versionCode (currently 33 / versionName 2.18.2 in `app/build.gradle.kts` and `apk/version.json`),
   notes, credits line, `ForgeQuest-vX.Y.Z.apk` naming untouched.
7. Remaining food items from `handoff-food-2026-11-04.md`: household units polish; "plate" (ask what it means); lane E web lookup of
   the top-100 missing foods (`lookup_found.csv` was never written); UPC-E expansion; a way to edit a food's barcode; look at the
   scanner on screen.
8. Mock-up for the 7-day average with flexible Thu/Fri days, user approval, then build.
9. Later: the "premium Home" mock in `.claude/scratch/mock/` is now superseded by the simple Home; do not build it.

---------------------------------------------------------------------------------------------------------------------------------
## 8. How to build, test and run (this PC)

Gradle (PowerShell or Git Bash), from the repo root:
```
set TEMP=C:\gt ; set TMP=C:\gt ; set ANDROID_HOME=G:\Android ; set JAVA_TOOL_OPTIONS=-Djava.io.tmpdir=C:/gt
./gradlew --console=plain :app:testDebugUnitTest :app:assembleDebug -q
```
(Bash form used this session: `export TEMP='C:\gt' TMP='C:\gt' ANDROID_HOME='G:\Android' JAVA_TOOL_OPTIONS='-Djava.io.tmpdir=C:/gt'`.)
APK: `app/build/outputs/apk/debug/app-debug.apk`. Web: `cd web && npx tsc --noEmit -p . && npx vitest run`.

Emulator: AVD `ForgeQuest_Pixel`. Start: `G:\Android\emulator\emulator.exe -avd ForgeQuest_Pixel -no-snapshot-save -no-audio`.
adb: `G:\Android\platform-tools\adb.exe`. Install: `adb install -r app\build\outputs\apk\debug\app-debug.apk`; launch:
`adb shell am start -n com.forgequest.app/.MainActivity`. Driver script: `tools/qa/fq.py` (`shot [label]`, `ui`, `tap X Y`, `scroll
down|up`, `type "text"`, `back`; coordinates are in a 540x1200 frame, half the real screen; screenshots land in `tools/qa/shots/`,
which is untracked). The app shows a "What's new" dialog on first launch after install (OK button around 410,789).

Handy emulator switches: dark mode `adb shell cmd uimode night yes|no`; text size `adb shell settings put system font_scale 2.0`
(reset to 1.0 afterwards); **app language**: `cmd locale set-app-locales` does NOT work because the app keeps its own pref. The
language is `ui_prefs` -> `language` ("system" / "en" / "ar"); set it on a debug build with `run-as com.forgequest.app` writing
`shared_prefs/ui_prefs.xml` after `am force-stop`, keeping the other keys (`tour_seen`=true, `language_chosen`=true), then relaunch.
Quick-log sheet gotcha: tapping a "Recent" chip only prefills the fields (calories 508, protein 51.9); typing appends to the prefill, so
clear with repeated DEL (`adb shell input keyevent 67`) before typing. Typing before the sheet settles lands in the wrong field.

State left on the emulator: one test meal logged today ("Chicken and rice", 1,900 kcal, 95 g protein), language reset to English,
night mode off, font scale 1.0. It may still be running; close it freely.

---------------------------------------------------------------------------------------------------------------------------------
## 9. Process and environment notes

- Windows 11, PowerShell primary, Bash tool available. Repo `G:\AntiGravity projects\ForgeQuest`. Other worktrees exist under
  `.claude/worktrees/` and siblings (`ForgeQuest-foodtest`, `ForgeQuest-split`); `git stash@{0}` holds "recovery fixes" on
  feat/food-library. Do not delete them without asking.
- `.gitignore` already ignores build outputs, `*.apk`, keystores, `tools/fooddb/cache/`, source PDFs. `.repowise/` is NOT ignored
  (66 MB, untracked): never commit it.
- Subagent routing used: `opus-advisor` for Gate 1 and Gate 3 (cheap, read-only, fast); a `web-fetch` agent for the GitHub skill page.
  Per the previous handoff, `sonnet-builder-g` (agy/Gemini) fails on app work in headless mode; use `sonnet-builder` for big app
  code or do small edits directly (done this session: edits were small, applied with Python replace scripts that assert each
  target string occurs exactly once; one inline bash heredoc failed on quoting, so the script was saved as a file and run).
- CRLF/LF: HEAD stores many files as LF while the working copy may be CRLF; edit scripts here detect and preserve the existing line
  endings. Git prints "LF will be replaced by CRLF" warnings; harmless.
- Hindsight: a knowledge page "Simple Home" (id kp-e8048df3fd53450f8b17e6a020cdce8a) was created via `hindsight_capture_initiative`.
  The MCP server later disconnected. Per the user's global rule (digest before ending), a digest of durable facts is in
  `C:\Users\Administrator\.ai-brain\digests\forgequest-2026-11-09.json` for `python -I ~/.ai-brain/_tools/seed_digest.py <file>`
  (the server must be up on 127.0.0.1:9077; if it is not, load it later).
- Files created this session: `docs/ui/simple-home.html`, `docs/plans/handoff-2026-11-09-full.md` (this file),
  `.claude/scratch/handoff.md` (short pointer; the older one was renamed `handoff-food-2026-11-04.md`),
  `.claude/scratch/{precommit,extras,home,home2}.log` (build logs, safe to delete), scratch script `edit2.py` in the session
  scratchpad (not in the repo).

---------------------------------------------------------------------------------------------------------------------------------
## 10. Decision log (one line each, with the reason)

- Commit the two kinds of pending work separately -> reviewable, revertable.
- Never `git add -A` on broad folders -> an accidental screenshot/tooling commit happened once.
- Protein sort pool of 40 then stable sort -> relevance kept, no junk surfaced.
- Log again = copy to the open day with undo -> doubles as copy-meal; undo because it sits beside delete.
- Return to Soft Coach instead of a new "Apple" style -> the project's own design system already matches the Apple rules; premium
  drifted from it.
- Mock first, then build, then user tests, then publish -> the user's standing rule: approval before release.
- Rings: protein gold, calories always blue -> teal reserved for tappable; over-goal shown by the second lap.
- Legend removed, dots in stats -> same information, one element fewer.
- Keep protein (not calories) in the ring centre -> product decision, not styling.
- Do not raise body text to 17 yet -> global change; needs the user's say.
- Do not touch ReadinessCard/bottom nav/Streak -> already match the language; avoid regressions.
- Debug APK is not for the user's phone -> signing mismatch would force uninstall and data loss.
