# ForgeQuest Web: bug fixes + Phase 3 plan (2026-10-24)

Draft for review (yours and Gemini's). Nothing is built yet.

## Where the web app stands

- On main: all 5 tabs and every screen (Phases 0-2). 29 test files, 181 tests, all passing.
- Missing from the plan: any backup (no Drive, and `backup.ts` is built and tested but has no button), the
  "Update ready" notice, and a test on a real iPhone or with live AI.

## Findings (ponytail audit of web/ + your bug reports)

| # | Problem | Fix |
|---|---|---|
| 1 | **No way to back up.** Deleting the Home Screen icon, or iOS clearing site data, wipes every workout. | Drive auto-backup (plan v2) plus Save / Restore backup file, using the existing `backup.ts`. |
| 2 | **Height and age are shown but not saved.** Onboarding shows 170 cm / 25 yrs, but the saved value is 0 unless you tap +/-, so calories ignore height, age and sex (33 kcal per kg instead). Profile has the same display. | Make the shown value the real value (decision B). |
| 3 | **Steppers are too slow.** Weight moves 0.1 kg per tap: 75 to 110 kg is 350 taps. The same steppers are used 36 times, including live workout weights. | Tap the number to type it, in the shared `Stepper`, so all 36 uses get it. |
| 4 | **iPhone tab bar sits too high.** There's an empty strip under it in Home Screen mode. Likely cause: `.shell { height: 100dvh }` is shorter than the screen in iOS standalone mode with the translucent status bar. | `.shell { position: fixed; inset: 0 }`. Needs your iPhone to confirm. |
| 5 | **Calories look like manual input.** They *are* calculated (Mifflin-St Jeor x plan days x goal), but they're shown as two editable steppers, so it reads as "type your own". | Show the result as a summary card, with "Adjust" to edit (decision A). |
| 6 | **Food category chips are cut off at the screen edge** (المشروبات). | 5 equal-width segmented tabs, no sideways scroll (your spacing rule). |
| 7 | **No update notice.** A Home Screen app can stay open for days on old code. | On open and on return to the app, check for a new version; show an "Update ready, tap to restart" pill, never during a live workout. |
| 8 | **Week start disagrees.** Home starts on Monday, the journal by language, so the weekly counts can differ. | Use one rule everywhere, the same as Android. |

## Decisions for you

**A. Calories and protein in onboarding**
- a) (Recommended) Summary card: "Your daily target: 2,450 kcal · 135 g protein (from your weight, height, age and plan)", plus an "Adjust" link that opens the editors.
- b) Keep the two steppers, just add tap-to-type.

**B. Height and age before the person sets them**
- a) (Recommended) Empty number fields that must be filled to continue. Calories are then always right.
- b) Prefill 170 cm and 25 yrs as real saved values. One less step, but calories are wrong for anyone who skips it.

**C. Backup order**
- a) (Recommended) One PR with Drive auto-backup as the main path, plus "Save backup file" and "Restore from file" as the fallback (plan v2).
- b) File save/restore first, Drive later.

**D. Week start**
- a) (Recommended) Same as Android, everywhere.
- b) Monday everywhere.

## Build order (one builder at a time, verified at each step)

1. **PR 1, quick fixes:** findings 3, 2, 5, 6, 4. Check in the browser at iPhone size in EN and AR, deploy, then you send one iPhone screenshot to confirm the tab bar.
2. **PR 2, backup:** finding 1 (Drive appDataFolder JSON, same as Android, a "Tap to back up" pill when the 1 h token has expired, save and restore file). Add a test that a web backup restores on Android's format and back.
3. **PR 3:** findings 7 and 8, then a ponytail-review of PRs 1-3.
4. **Real iPhone pass:** you run a short checklist (install, onboarding, live workout with the screen locked, backup and restore, an AI scan).

## Status (2026-10-24)

Approved: A-a, B-a, C-a, D-a. Built and merged: PR #45 (findings 2-6), #46 (finding 1), #47 (findings 7, 8 and a review fix).
The week-start fix uses Monday everywhere on the web, matching the streak. Android's journal still follows the phone's locale.

## iPhone checklist (you, about 10 minutes)

1. **Install:** open the site in Safari. Tap the Share button on the install screen. Does the sheet show Add to Home Screen? If not, use ⋯ → Share. Open the app from the Home Screen.
2. **Tab bar:** is there still an empty strip under the tab bar? Send one screenshot either way.
3. **Onboarding:** tap the weight number and type yours. Fill in height and age, and check the daily target looks right. Try Adjust.
4. **Typing in a stepper:** with the keyboard open, does the screen jump or does the tab bar overlap anything?
5. **Drive:** Profile → Backup → Connect Google Drive. Sign in and pick your account. You should see "Google Drive connected and backed up".
6. **Workout:** do a short workout and finish it. Is "backed up" in Profile updated?
7. **Backup file:** Save backup file → Save to Files. Then Restore from file with that file. You should see the confirm dialog with counts.
8. **Food tab:** do all 5 category tabs fit on screen?
9. **Update pill:** after the next deploy, does "Update ready · tap to restart" appear, and does the tap work?

## Not checked

The coach prompt logic in depth, and every screen's CSS. Nothing was run on a real iPhone, in iOS Safari, or with a live AI key.
