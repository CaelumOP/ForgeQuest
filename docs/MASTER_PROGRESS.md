# Master upgrade progress

Branch `feat/forgequest-complete-upgrade`, cut from `audit/plan-quality` @ `622b58a` (Stage 1 closed, Gate 3 passed).
Spec: `docs/MASTER_SPEC.md` (written after the owner answers the Question Gate). Log: `docs/MASTER_DECISIONS.md`.

## Stages
- [x] Stage 1: close `audit/plan-quality` (Option B, browser Plan check, Gate 3 Codex + Argon, report). Commits 8570f50, debcc00, 622b58a.
- [x] Stage 2: read-only discovery (outputs in shared notes folder of local_a06498bf: stage2-*.md; grader web/src/domain/plan-grader.ts)
  - [x] 2.1 Dev plan grader scorecard + superset/drop/rest-pause audit
  - [x] 2.2 Input & Test Baseline integration map
  - [x] 2.3 Wavy rest timer + Build my plan pills map
  - [x] 2.4 UX rent-check, web 390x844 EN + AR, Android Compose
- [x] Question Gate: owner answered 2026-11-26, locked in docs/MASTER_SPEC.md (defaults + 4 overrides)
- [x] 3A grader + supersets/zero-rest (scorecards docs/master/grader-before.md / grader-after.md; overall 81.65 -> 87.85, min 41.88 -> 68.57, 3x20 coverage 17.8-18.6 -> 22.2-23.8, fat loss 6d balance 3.0-16 -> 15.1-17.6; Android grader port + floors, golden parity)
- [x] 3B Input & Test Baseline (data d790fe3, Android UI fac12be, web UI 5b0cde8; web 794/794 tests)
- [x] 3C cut list, wavy timer, sliding pills, catalog cues (3c1 a6d6476, 3c4 3de06ac, 3c2 a32ea20, 3c5 6f3ed43, 3c3 992195c, 3c6 3867ab5, 3c7 69cc015)
- [x] Stage 4 verification, smoke, Gate 3, report (docs/MASTER_UPGRADE_REPORT.md): Android 665/665 + debug build, web 810/810 + tsc + build, smoke EN/AR clean, Gate 3 APPROVED (Argon)

## Next step
Owner-test fixes 2026-11-26 (three bugs from testing 9188616), committed and installed on ForgeQuest_Pixel at 18:47 (APK = app/build/outputs/apk/debug/app-debug.apk). Not pushed or merged.
- 5d4ff9f Fix 3, wavy rest bar: Android uses the official Material 3 LinearWavyProgressIndicator, progress sampled every frame from endsAt; matching web SVG wave. Android 670, web 821.
- 635ad2a Fix 1, plan builder: No gear means no gear (strict equipment, push/pull capability ceiling, incline push-ups stay no-gear), plus five new no-gear back moves (doorframe row, table inverted row, prone Y-T-W raise, reverse snow angel, towel floor pull; EN/AR) so no-gear plans stay full. Matrix clean 0.9255, every preset >= 0.92, no short plans, grader floors unchanged. Android 671, web 822.
- 480f906 Fix 2, starting point: 25+ reps on a bodyweight move offers a harder version with a Test button (reuses harderVersion); "weighted" moves need a load, so no-gear squat steps up to jump squat. Android 674/674 (the commit message says 676 by mistake), web 825/825, tsc + build OK, goldens matched on both platforms.
- Baseline boundary audit (commit after 1f8bc2d): caps and easier/harder moves for every reps, hold and weighted boundary, ladder fixed (push-up -> diamond, pull-up -> archer, Australian -> pull-up, squat -> jump squat, plank -> hollow hold / incline hollow plank), plan builder ceiling also from squat, hip bridge and plank baselines; see MASTER_DECISIONS. Android 684/684 + assembleDebug, web 882 + tsc + build, goldens unchanged.
Owner to check on the phone: 825 s plank (Start with 1:00 + Test Hollow hold), 2 push-ups (Start with 1 + Test Knee push-up), and the rest bar smoothness, a No gear plan with push-up max 1, and 51 reps on Bodyweight squat in "Find your starting point" (should offer Jump squat).
Follow-ups: tools/catalog/build_catalog.py has drifted from the hand-edited ExerciseCatalog.kt / exercise-catalog.ts (reconcile before its next full run); the five new moves have no images yet (placeholder shown).

Earlier state:
Upgrade committed through 4b8b0a8 (Android 665/665 + debug build, web 810/810 + tsc + build, Gate 3 APPROVED). That build is installed on the ForgeQuest_Pixel emulator (06:12); APK = app/build/outputs/apk/debug/app-debug.apk.

FIXED 2026-11-26 16:30 by Codex Astra (see the commit after 6c1a94d): starting-point list = one staple per Push/Pull/Squat/Hinge/Core fitting the active gear (No gear: push_up, squat, hip_bridge, plank; full gym: bench_press, pull_up or lat_pulldown, back_squat, romanian_deadlift, plank), Build my plan passes its unsaved gear, picker filtered by gear with "Show all moves" (EN/AR exported, no overlay). Android 668/668 + assembleDebug, web 819/819 + tsc + build. Installed on ForgeQuest_Pixel; ready for owner test and merge to main.

Original bug (found in owner testing 2026-11-26): "Find your starting point" suggests the active plan's first 3 weighted lifts (e.g. Around The Worlds, Arnold press, Rear Lateral Raise; no leg move) and ignores unsaved Build my plan gear ("No gear" still shows Pull-up and dumbbell moves). Cause: `buildBaselineMoves` in `app/.../ui/baseline/BaselineViewModel.kt` (~65) and `web/src/ui/baseline/helpers.ts` (~90) reads saved settings gear and adds plan weighted items in plan order. Fix brief ready: `docs/master/briefs/baseline-moves-fix.md` (one staple per Push/Pull/Squat/Hinge/Core fitting the active gear, Build my plan passes its unsaved gear, picker filtered by gear with "Show all moves" EN/AR, identical tests on both platforms). The Gemini Hydrogen run made no edits: quota exhausted (resets in ~135 h); Codex back 10:24. Then verify Android tests + assembleDebug, web tsc + tests + build, export:android, commit.

Other follow-ups: CLAUDE.md routing table still says DB version 10 (now 13); pill slide motion and timer smoothness only checkable on a phone. Run vitest from web/ (golden paths are cwd-relative). Gradle: gw.sh wrapped in python ~/.claude/hooks/quiet.py from the worktree root.
