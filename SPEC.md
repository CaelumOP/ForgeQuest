# ForgeQuest v2 — Spec (from grilling session, 2026-10-18)

Replaces `Engineering_Schema.md` and the Gemini spec. The old app gets archived to `_old/` for reference only.

## Audience & platform
- Me + a few friends, sideloaded APK. Android only for now (iPhone later, maybe).
- Solo app in v1: no accounts, no server. Data model kept sync-friendly so friends/competition can be added in v2.
- Native Kotlin + Jetpack Compose, Material 3.
- Theme follows system: Google Fit-style **light and dark**.

## Navigation
5 tabs: Home · Workouts · Food · Coach · Profile.

## Home
Top to bottom:
1. Concentric protein (outer) / calorie (inner) rings + 3-stat row (protein left · cal left/surplus · weight). Calorie surplus turns gold, never red.
2. Streak card: **weekly** streak (weeks where planned sessions were hit), flame shows this week's progress, M T W T F S S day circles. Planned rest days never hurt.
3. Today's workout card with Start.

The muscle map is NOT on Home; it lives in Workouts.

## Workouts
- Training types: calisthenics basics, skills (holds in seconds, progressions), weighted calisthenics, gym/machines.
- Starting plan: Hemo's 6-day beginner routine (transcribed from his video; link pending from user).
- Plans come from: coach (preview card → Apply), manual builder (exercise library), Google Docs import (real Drive integration).
- Set logging: prefilled from last session, tick to confirm, nudge +/- first if needed. Rest timer.
- Every exercise has an editable **form video link**; empty falls back to a YouTube search for "<exercise> form".
- PRs: quiet gold "PR" tag on the set + a line in the post-workout summary. No confetti.
- Muscle recovery map: real anatomy art (open-source SVG body map, each muscle a separate path, recolored by recovery), front + back. No hand-typed curves.

## Food
- Tracks calories + protein only.
- Categories: Breakfast, Lunch, Dinner, Snacks, Drinks. Tick-what-you-ate checklist with 1x steppers, pinning, editable photos.
- Fresh install starts with an **empty** food list; items get added via scan or quick-add.
- Scan: Google Lens-style full-screen camera (big shutter, gallery thumbnail bottom left, nothing covering the preview). After capture, the photo freezes and a scan animation runs over it with live steps. Then a confirmation sheet (items, portion size, cooking fat, save to list).
- Hand-in-frame is optional: used for portion scale if present, never required.

## Coach (Gemini)
- Each user pastes their own free Gemini key (guided setup screen).
- Builds/edits plans, knows the user's recent workouts/PRs/food/weight, parses pasted plans, writes a weekly check-in.
- No Fast/Deep toggle: the app chooses the model per task. Model IDs are verified against the live API at build time.
- Real errors shown in plain language (bad key / offline / rate limited / couldn't read the photo). Never a fake result, never a blank image sent.

## Motivation
- Weekly streak flame (above).
- Subtle XP + rank: small rank badge, calm level-up moments, plain names (no anime titles).
- Badges: consistency, skill unlocks, strength milestones, nutrition.
- Haptics only, no sounds.

## Profile
Rank/XP, body weight + trends, nutrition goals, AI key, hand calibration (optional), Google Drive (sign-in, backup, restore, Docs import).

## Onboarding (first launch)
4 short steps: name → weight + goals (suggested protein) → starting plan → optional "get your free AI key" guide.

## Backup
Google Drive backup/restore + Docs plan import via Google sign-in. Requires one-time Google Cloud OAuth setup by the owner (friends added as test users).

## Build process
- Claude Code builds it phase by phase. Every phase is checked on a **real Android emulator** with real screenshots before the user approves it. No drawn mock-ups.
- Phases: 0 setup (archive, new project, emulator, theme) → 1 Workouts + muscle map → 2 Home → 3 Food + scan → 4 Coach → 5 XP/badges/onboarding → 6 Drive.

## Status (2026-10-18)
All phases are built and checked on an Android 15 emulator with real screenshots, in dark and light mode. 29 unit tests pass.

### Review pass (2026-10-18, branch claude/hopeful-meitner-lkx0er)
Done from the product review, top down. Logic layers compile and 36 unit tests pass under a standalone Kotlin
compiler; the Compose screens could not be built in that environment, so **check these on the emulator** before
sharing an APK:
- Direct number entry: tap any stepper value (sets, reps, kg, seconds, body weight, goals, servings).
- Workouts → history icon: list by month, open a session, edit/remove sets, delete a workout; PR tags recompute.
- Profile → Your data: Export file (share sheet), Import file, Undo last restore; Drive Restore shows a preview first.
- Light mode: deeper teal/blue/gold/coral; dark mode unchanged.
- Scan: full-res photos are subsampled; tap an item to fix grams; dish name editable; snackbar after logging;
  camera permanently denied → "Open app settings".
- Undo snackbars for meal and plan-item deletes; confirm before finishing with unticked sets, deleting a food,
  clearing the chat.
- Coach: streaming bubble, fast mode (thinking off) for chat/scans/check-in with 400 fallback, richer data summary,
  MEMORY notes under Profile → What the coach remembers. "Review and apply" always previews first.
- Food: day picker (log to past days), 7-day protein card, Quick log sheet.
- Plan editor: Previous plans, Move up/down, swap days. Docs sync posts a staged plan in Coach instead of applying.
- Drive: appdata-only by default; Docs read access is a separate opt-in. Gemini data-use disclosure in onboarding and the key dialog.
- Badges on their own screen; setup card on Profile when no key.
- Snapshot built off the main thread with a 90-day meal window; orphan photo cleanup; stable Material 3 and Room 2.7.2
  (Room 2.7.2 with KSP 2.2.21-2.0.4 is expected to work; if the build complains, pin KSP to the version Room's
  release notes list).
- Release signing via keystore.properties (see README). Shrinking is opt-in with -PminifyRelease=true.

### Second pass (after the first phone test)
- Muscle map: all SVG paths re-tokenised; the broken head on the back view came from compact arc flags that Android's parser misreads.
- Coach: Flash for everything (Pro took 1-2 min per plan), 15/45 s timeouts, check-in no longer blocks sending,
  "Writing your week…" progress while plan JSON streams, reply language locked to the latest message's script,
  Arabic rendered right-to-left, suggestion chips float translucently over the conversation.
- Nice-to-haves done: System/Light/Dark theme, kg/lb, vibration toggle, Manrope display typeface, rest timer in the
  notification shade, workout-day reminder, per-set notes (Room v2 migration), exercise library (rename/merge/delete),
  app lock with fingerprint/face/PIN (MainActivity is now a FragmentActivity; androidx.biometric 1.1.0).
- Not done: Arabic UI localisation. Every string is hardcoded in Kotlin across ~20 screens; extracting them to
  resources plus an Arabic set is a dedicated pass to do once this build is confirmed working on a phone.

### Third pass (2026-10-18, branch claude/project-thread-m895wm, built and run on the emulator)
Verified on an Android 15 emulator with a real free Gemini key, dark and light, 1.0× and 1.3× text, English and Arabic.
- **Coach "No internet" root cause:** Google holds back even the response headers until the first word. Free-tier
  Flash queued 10-65 s (a plan with thinking on: 37 s), past the old 45 s read timeout, and the timeout was labelled
  offline. gemini-3.8-flash also allows only 20 free requests a day.
- **Coach now:** gemini-3.5-flash-lite for chat, plans, check-ins and scans, Flash as fallback; thinking off via
  thinkingLevel "minimal" on Lite and thinkingBudget 0 on Flash. Timeouts say "The coach didn't answer in time";
  only DNS/connect failures say offline. A stream that fails before text retries once without streaming; a stalled
  model moves on. Error bubbles have "Copy error details". Quota-exhausted models cool down for Google's retry delay.
- **Measured (tap Send → first word / done):** before 9.8-12.1 s / 11-14 s on 3.8-flash; after 0.7-0.9 s / 1.2-1.8 s.
  Full week plan 3.8 s, meal scan 2.7 s. System prompt ~1,360 → ~800 tokens (plan schema only for plan requests).
- **Groq (optional key, Profile):** chat and check-in go to Groq's fastest Llama model under Automatic, with silent
  fallback to Gemini before the first word. Gemini stays on its native API (thinking control, model list). The Coach
  ⋮ menu picks the model (Automatic, Flash-Lite, Flash, Pro) and shows who answered last and how fast.
- **Arabic UI:** ~650 strings in values/ and values-ar/ with Arabic plural forms; per-app language; Western digits
  (nu-latn); day names from the locale; steppers and the muscle map stay left-to-right; fractions and weights are
  bidi-isolated; titles use the system face untracked in Arabic.
- **From hands-on use** (inspiration in brackets): hold-to-repeat steppers [Strong, Hevy]; last-time hint per set
  [Strong, Hevy]; faint check in empty set boxes [Strong]; −15 s on the rest bar [Hevy]; swap starts on the same muscle
  [Hevy, Fitbod]; Tick all & finish [Strong]; next workout on the summary [Fitbod]; labelled ring centre [Google Fit];
  recent one-off meals in Quick log [MacroFactor, MyFitnessPal]; Save file export; JSON plan paste; Copy prompt for
  YouTube plans; Home tab no longer restores the previous tab.
- **Checked fine:** muscle map back view (no wedge), light-mode text contrast ≥ 4.5:1, Progress screen renders.
- **Not verified:** Import file through the picker end to end, reminder notification firing, app lock with a real
  screen lock, Groq with a real key.

## Open items
- [x] Hemo routine: the Foundation playlist was transcribed from on-screen labels into docs/hemo-foundation.md. It's 5 training days plus 2 rest days, not 6.
- [x] Body map: react-native-body-highlighter 3.2.0 (MIT), male and female.
- [ ] Google Cloud OAuth setup for Drive (steps in README.md). The code is ready but untested against a real Google account.
- [x] A real Gemini key test: done on the emulator (see Third pass). Phone timing still to confirm.
