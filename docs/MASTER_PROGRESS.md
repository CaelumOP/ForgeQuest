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
- [ ] 3C wavy timer, sliding pills, approved cuts
- [ ] Stage 4 verification, smoke, Gate 3, report

## Next step
3C: 3c1 a6d6476, 3c4 3de06ac, 3c2 a32ea20, 3c5 6f3ed43, 3c3 992195c done; 3c6 web D and 3c7 catalog building. Then Stage 4. Run vitest from web/ (golden paths are cwd-relative). Gradle: gw.sh wrapped in python ~/.claude/hooks/quiet.py from the worktree root.
