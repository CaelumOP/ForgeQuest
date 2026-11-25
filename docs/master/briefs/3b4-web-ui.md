# Build brief 3B-4 (WEB UI): "Find your starting point" flow + entry points + labels

Repo G:\AntiGravity projects\ForgeQuest\.claude\worktrees\confident-swanson-e64dfc. Edit ONLY `web/src/ui/**`, `web/src/routes.ts` / `web/src/app.tsx` for the new route, and web i18n. No `web/src/domain/**`, `web/src/data/**` (done already), `app/`, `tools/`, CLAUDE.md, git. Never call any MCP tool. Finish within 25 minutes. Read `web/CLAUDE.md` (hash routes, kit components, logical CSS, >= 44 px targets, timers from Date.now and a saved start time, one bundle).

i18n: the Android app is the reference and web strings are generated from Android `strings.xml` by `npm run export:android`. The Android UI for this feature is being built (or is done) with the same string names; FIRST run `cd web && npm run export:android` and use the generated keys that exist (grep `web/src/i18n/en.json` for baseline / starting point keys). Only for strings missing there, add them to a web overlay file in `web/src/i18n/web/` (EN + AR) per web/CLAUDE.md.

The data/domain layer is done: `web/src/data/baseline-tests.ts`, `web/src/domain/baseline-targets.ts`, status 'FROM_BASELINE'. Spec: `docs/master/3b-spec.md`. Mirror the Android screen `app/src/main/java/com/forgequest/app/ui/baseline/BaselineScreen.kt` (read it if present) for structure and texts.

Do:
1. New route `#/baseline` (optional exercise id after the second slash) and screen `web/src/ui/baseline/BaselineScreen.tsx` with its own Back button: title, intro, moves list (push-up, pull-up only with a pull-up bar in equipment, plank hold, first 3 weighted main lifts of the active plan, "Test another move" via the existing exercise picker), last result + target per row.
2. Testing a move: manual entry (reps / seconds / weight + reps + hint); guided REPS tap counter; guided HOLD reusing `HoldOverlay` from `web/src/ui/live/Overlays.tsx` with a Date.now()-based tick; WEIGHTED manual.
3. Result card: target text / 0-rep easier variation with "test that move" button / too-light hint.
4. Optional 90 s rest between moves with Skip (end time saved, Date.now()).
5. Entry points: Workouts tab card (no finished workout AND no baseline row; dismissible, persisted via the settings data module); Profile > You and your goals row "Starting point" with count; Build my plan obvious row/button "Test your starting point" near the top of "Make it fit me perfectly" (or under Goal), push-up / pull-up fields read/write the baseline store.
6. Live: "From your baseline" label where "Estimated start" renders (LiveWorkoutScreen.tsx); #15 blank weight with "Add weight" placeholder instead of "0 kg" and a "Test this move" text button that navigates to `#/baseline/<exerciseId>` (set row in `web/src/ui/live/SetRowView.tsx`).
7. Never create sessions/sets from this flow.
Run `cd web && npx tsc --noEmit` and `npx vitest run src/ui`. Report files changed.
