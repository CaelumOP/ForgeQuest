# Muscle → exercises plan (2026-10-24)

Idea: tap a muscle on the Workouts body map and see the exercises for it. It was stress-tested by Claude and Gemini
(prompt: `gemini-muscle-exercises-prompt.md`).

## Decisions
- **A a.** First version: muscle chip bar, the exercise sheet, difficulty tiers, small muscles merged, rows open the exercise page.
- **B a.** 3 tiers rated by how hard a movement is to learn and its leverage, not by load (barbell lifts are Intermediate at most).
- **C a.** A row opens the exercise page; "Swap into my plan" shows there only when the plan has exactly one exercise with the same movement.
- **D a.** New exercises for obliques, adductors, tibialis, lower back and forearms come later, in their own update.
- **E yes.** "Ready for a harder version" for bodyweight rep moves (below).
- No onboarding level question. Both apps (Android + web), English + Arabic.

What we took from Gemini: no level question; tiers per exercise, not per person; the chip bar; plan-first sorting; a
recovery warning; show Advanced moves with a badge instead of hiding them; merge near-empty muscles for now.
What we left out: the "highest axis wins" tier rule, a swap button on every row, on-device metrics (no analytics),
and its per-person level-up rules (see E below for the part we may keep).

## Steps
1. **Tiers (data).** Add `tier` (1 Foundation / 2 Intermediate / 3 Advanced) to all 90 exercises in
   `ExerciseCatalog.kt` and `web/src/data/seed/exercise-catalog.ts`; custom exercises have no tier. Rule: tier 1 = floor-
   or machine-supported, short lever, easy to learn (knee push-up, goblet squat, lat pulldown); tier 2 = full bodyweight
   or free-weight compounds (push-up, chin-up, dip, barbell squat/bench/deadlift); tier 3 = skill or high-leverage moves
   (archer/one-arm push-up, pistol, muscle-up, Nordic curl). **Show the user the full rated list before step 2** (`exercise-tiers.md`).
2. **Shared logic (pure, unit tested, both apps):** `exercisesFor(muscle, library, equipment, plan, recovery)` returns
   the recovery state, "In your plan" (plan exercises hitting the muscle as primary or secondary), then the rest:
   main-muscle first, then secondary; tier 1 → 3; filtered to the user's equipment (with an "All equipment" switch).
   Merge for now: obliques with abs, adductors with quads and glutes, tibialis with calves.
3. **Body map.** A scrollable row of 16 muscle chips under the map (also the way in for small muscles). A map tap or a
   chip keeps today's recovery line and adds a "See 12 chest exercises" button.
4. **Exercise sheet.** Header: muscle, recovery pill, "My equipment / All" switch. A recovering muscle gets a gold
   note ("Still recovering: preview form or swap an exercise for your next session"). Rows: name, tier badge,
   "main" or "also works", video icon. Tap → existing exercise page.
5. **Exercise page.** "Swap into my plan" only when the plan has exactly one exercise with the same movement pattern
   (swap engine); it swaps that plan item (sets/reps translated as the engine already does) and says so.
6. **Library.** The same muscle chips filter the exercise library.
7. **Strings, EN + AR.** Formal Arabic with the common gym word where it helps (e.g. lats).
8. **Check:** web at 393 px in English and Arabic; Android on the emulator (CI APK); release 2.11.0.

## E (approved): "ready for a harder version" for bodyweight reps
Today bodyweight rep moves add +1 rep a session up to the target + 2, then stop silently; only holds suggest a harder
version. Proposal: when every set reaches 15+ reps in two sessions in a row with no Hard or Pain, the live workout
shows "Ready for a harder version: Push-up" with a Swap button (the next tier in the same movement pattern and the
user's equipment). The new move starts at the bottom of the plan's rep range.
