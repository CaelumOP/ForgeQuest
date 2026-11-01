# Brief: UX polish (goes out with 2.15.0, after the user tests it)

The user approved all of this. Build it on **Android and web** (parity). Work in a NEW worktree stacked on the score branch, so a single test build has everything:
`git -C "G:/AntiGravity projects/ForgeQuest" worktree add .claude/worktrees/ux-polish -b feat/ux-polish origin/feat/debrief-score`.
Work only there. Never switch branches in the main checkout. Commit as you go (one commit per item is fine). Do NOT push and do NOT open a PR. The coordinator reviews first, and **nothing is released until the user has tested it on the emulator.**

The version stays 27 / 2.15.0 (it ships with the score). Add one bullet to `apk/version.json` notes at the end, in the same short professional style, e.g. `• Smoother app: lists animate, swipe to delete with Undo, drag to reorder plan exercises, larger-text fixes.`

## 1. Lists that animate (instead of jumping)
- Android: add `Modifier.animateItem()` to the items of the LazyColumns where rows are added, removed or reordered: food log entries, the live workout's sets (added/removed sets), plan editor exercises, journal/history rows, coach memory notes, saved meals/food library. Rows need a stable `key` (most already have one; add keys where missing). Don't animate where it would look odd (charts, the first-load list).
- `AnimatedVisibility` for cards that appear or disappear inside a Column (if any obvious ones are missing).
- Web: the equivalent with CSS. Fade and slide in on insert (`@keyframes`), and removal animated before it's dropped where cheap (it's fine if web removal isn't animated). Respect `prefers-reduced-motion` (already used in 7 files: follow that pattern).

## 2. Swipe to delete, with Undo
- Android: `SwipeToDismissBox` (Material 3) on **food log entries** and **journal (history) workout rows**, end-to-start swipe. A red background with a delete icon. Then the existing Undo snackbar pattern (grep `showSnackbar` / `action_undo` to find how deletes undo today). Swipe only where a delete + Undo path already exists or is simple to add. If a workout delete has no undo today, add a soft undo: keep the deleted rows in memory until the snackbar times out, then delete, or re-insert on Undo. Keep the existing delete button/menu, since the swipe is a shortcut and not the only way.
- RTL: swipe direction must feel right in Arabic (end-to-start follows layout direction in Compose, so check it).
- Web: a pointer-events swipe on the same rows (touch and mouse drag). Threshold about 40% width, the same Undo toast. Keep it small, with no new library.
- Screen-reader users: the delete stays reachable without swiping (the existing button or a custom accessibility action).

## 3. Drag to reorder in the plan editor
- Android `ui/workouts/PlanEditorScreen.kt`: drag a handle (`Icons.Rounded.DragHandle`) to reorder exercises within a day. Use `detectDragGesturesAfterLongPress` on the handle with the LazyColumn, or a tiny reorder helper. No new dependency unless it's tiny and well known; if you add one, say why. Haptic `tick()` when an item passes another. Keep the existing Move up/down buttons for accessibility.
- Web: drag with the handle (pointer events), with the same buttons kept.

## 4. Slide transitions + large text + error buzz
- **Transitions** (Android `ui/AppRoot.kt` NavHost): detail screens (workout/session detail, exercise detail, plan editor and similar pushed screens) slide in from the end edge and back out, using `slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start)` / `End` with a fade. That's RTL-aware. Tab switches keep the fade. LIVE keeps no transition. Short: about 250–300 ms. Web: a matching CSS slide on route change if the router makes it simple; otherwise skip and say so.
- **Large text:** start the emulator (`G:\Android\emulator\emulator.exe -avd ForgeQuest_Pixel -memory 2048 -gpu swiftshader_indirect -no-snapshot-save`). The app (2.15.0 QA) is already installed with test data. Run `adb shell settings put system font_scale 2.0`, then use `tools/qa/fq.py` (screenshots go in `FQ_SHOTS` = your scratch folder, not the repo) to walk Home, Workouts, live workout (start one, then discard), Food, Coach, Profile, the journal and the finish screen. Fix anything cut off or overlapping: fixed heights become `heightIn(min=…)`, add `maxLines` + ellipsis where truncation is right, wrap rows that overflow. **Set font_scale back to 1.0 and shut the emulator down when done** (`adb emu kill`). Web: check at 200% browser zoom in the same spirit, and fix only obvious breaks.
- **Error buzz:** add `fun error()` to `ui/Haptics.kt` (a short double buzz, e.g. two quick ticks, with the same supported/fallback pattern as `tick()`). Call it where the user sees a failure: a failed AI reply (coach chat error, debrief failed, meal scan failed), and a failed save/backup if there's a visible error. Web: `navigator.vibrate([30, 60, 30])` where the same errors show, if web already uses vibrate (2 files do: follow them).

## Rules
- The shell is Git Bash on Windows. `MSYS_NO_PATHCONV=1` for adb paths. adb: `G:\Android\platform-tools\adb.exe`. Prefer the Edit tool for multi-line edits. Write `\n` escapes in Kotlin strings, never real line breaks inside a string literal (that broke CI last time).
- There's no local Gradle build. CI compiles. So read every API you call (imports, signatures, named args). Material 3 / Compose versions: check `gradle/libs.versions.toml` before using newer APIs (`animateItem` needs Compose foundation 1.7+, and `SwipeToDismissBox` needs material3 1.2+). If the version is older, use what's available and say so.
- Keep EN and AR strings in sync (Android values/ and values-ar/; web i18n en/ar), formal Arabic, `\'` escaped in Android XML.
- Web: `cd web && npx tsc --noEmit -p . && npx vitest run` must pass. Copy node_modules from `.claude/worktrees/debrief-gemini/web/node_modules`.
- Small, simple changes that match the surrounding style. Commit messages end with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

## Report back (under 300 words)
The commit hashes, what changed per item (Android / web), the large-text problems found and how each was fixed, anything skipped and why, and anything you're unsure compiles.
