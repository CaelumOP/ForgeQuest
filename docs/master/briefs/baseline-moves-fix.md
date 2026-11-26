# Fix brief: "Find your starting point" move list (Android + web, same behaviour)

Repo G:\AntiGravity projects\ForgeQuest\.claude\worktrees\confident-swanson-e64dfc. Never call any MCP tool. No git. Do NOT run Gradle (the main thread does). Finish within 25 minutes. Edit only `app/` and `web/src/`. Android is the reference; web must give identical lists for identical inputs. New strings (if any) in EN + formal AR (Android values + values-ar, then `cd web && npm run export:android`). No other scope.

Bug seen on a phone: the list showed Push-up, Pull-up, Plank, Around The Worlds, Arnold dumbbell press, Dumbbell Lying Rear Lateral Raise (no leg move), and still the same after switching gear to "No gear" in Build my plan (Pull-up too, with no bar).
Cause: `buildBaselineMoves` (Android `app/src/main/java/com/forgequest/app/ui/baseline/BaselineViewModel.kt` ~line 65; web `web/src/ui/baseline/helpers.ts` ~line 90) adds push-up, pull-up (if PULL_UP_BAR in saved settings), plank, then the first 3 WEIGHTED non-extra plan items in plan order, whatever they are; and it reads gear only from saved settings.

Do:
1. Gear source: the list uses the ACTIVE gear = gear passed in by the caller when present, else `configuredEquipment` from settings (expanded as today). Build my plan's "Test your starting point" must pass its current (possibly unsaved) gear selection: Android via the nav argument / a shared handoff (e.g. an optional `equipment` arg on the baseline route, encoded as the Equipment names), web via the `#/baseline` route param or the existing sessionStorage draft (`fq_plan_builder_draft`) read on mount. Other entry points (Workouts card, Profile row, live "Test this move") use saved settings.
2. Default checklist = one well-known staple per pattern that FITS the active gear (use the existing equipment-fit check, Android `ExerciseSwapEngine` `fits(...)` ~line 374 and its web counterpart): Push, Pull, Squat, Hinge, Core, in that order; at most 5 suggested moves. For each pattern pick the first catalog id from a fixed preference list that exists in the catalog and fits the gear (verify ids in the catalog; adjust names to real ids):
   - Push: barbell bench press, dumbbell bench press, push-up
   - Pull: pull-up (only with PULL_UP_BAR), lat pulldown, barbell row, one-arm dumbbell row, inverted row (only if it fits), else skip Pull
   - Squat: back squat, goblet squat, bodyweight squat
   - Hinge: Romanian deadlift (barbell or dumbbell), glute bridge
   - Core: plank (HOLD)
   Plan moves: if an active plan exists, a weighted plan main lift may replace the staple of the SAME pattern only when it fits the active gear and is a compound (not an isolation; use the catalog's movement pattern / mechanic field if present, else limit to the pattern's preference list). No other plan moves are added. Already-tested moves that are not suggested still appear below (as today), but only if they fit the active gear.
   No gear must give: push-up, bodyweight squat, glute bridge, plank (no pull, no dumbbell, barbell, bar or machine moves).
3. "Test another move" picker opened from this screen: filter to moves that fit the active gear by default, with a "Show all moves" toggle (EN "Show all moves" / AR «اعرض كل التمارين») if the picker has room for it; reuse the picker's existing filter support if any.
4. Tests on both platforms (same cases, same expected ids): No gear -> exactly [push-up, bodyweight squat, glute bridge, plank] ids, none WEIGHTED, no pull-up; dumbbells only (+ bench if needed) -> dumbbell bench press, one-arm dumbbell row, goblet squat, dumbbell RDL, plank; full gym -> barbell bench press, pull-up or lat pulldown per bar, back squat, RDL, plank; an active plan containing around_the_worlds / dumbbell_lying_rear_lateral_raise / arnold_dumbbell_press never puts those in the list; caller-passed gear overrides saved settings. Update existing tests that assumed the old list.

Run `cd web && npx tsc --noEmit` and `npx vitest run src/ui src/domain src/data` (from `web/`). Report per item what you changed (file:line), the exact ids chosen per gear, and test results.
