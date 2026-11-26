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
Upgrade committed through 4b8b0a8 (Android 665/665 + debug build, web 810/810 + tsc + build, Gate 3 APPROVED). That build is installed on the ForgeQuest_Pixel emulator (06:12); APK = app/build/outputs/apk/debug/app-debug.apk.

FIXED 2026-11-26 16:30 by Codex Astra (see the commit after 6c1a94d): starting-point list = one staple per Push/Pull/Squat/Hinge/Core fitting the active gear (No gear: push_up, squat, hip_bridge, plank; full gym: bench_press, pull_up or lat_pulldown, back_squat, romanian_deadlift, plank), Build my plan passes its unsaved gear, picker filtered by gear with "Show all moves" (EN/AR exported, no overlay). Android 668/668 + assembleDebug, web 819/819 + tsc + build. Installed on ForgeQuest_Pixel; ready for owner test and merge to main.

Original bug (found in owner testing 2026-11-26): "Find your starting point" suggests the active plan's first 3 weighted lifts (e.g. Around The Worlds, Arnold press, Rear Lateral Raise; no leg move) and ignores unsaved Build my plan gear ("No gear" still shows Pull-up and dumbbell moves). Cause: `buildBaselineMoves` in `app/.../ui/baseline/BaselineViewModel.kt` (~65) and `web/src/ui/baseline/helpers.ts` (~90) reads saved settings gear and adds plan weighted items in plan order. Fix brief ready: `docs/master/briefs/baseline-moves-fix.md` (one staple per Push/Pull/Squat/Hinge/Core fitting the active gear, Build my plan passes its unsaved gear, picker filtered by gear with "Show all moves" EN/AR, identical tests on both platforms). The Gemini Hydrogen run made no edits: quota exhausted (resets in ~135 h); Codex back 10:24. Then verify Android tests + assembleDebug, web tsc + tests + build, export:android, commit.

Other follow-ups: CLAUDE.md routing table still says DB version 10 (now 13); pill slide motion and timer smoothness only checkable on a phone. Run vitest from web/ (golden paths are cwd-relative). Gradle: gw.sh wrapped in python ~/.claude/hooks/quiet.py from the worktree root.
