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
- [ ] 3A grader + supersets/zero-rest
- [ ] 3B Input & Test Baseline
- [ ] 3C wavy timer, sliding pills, approved cuts
- [ ] Stage 4 verification, smoke, Gate 3, report

## Next step
Start 3A: brief Gemini Hydrogen (Android + web, sequential) for MASTER_SPEC 3A; first measure grader BEFORE scores (web: `npx vitest run src/domain/plan-grader.dev.test.ts`, report in the shared notes stage2-grader-scorecard.md). Gradle: `.claude/scratch/gw.sh` pattern (worktree copy: sed the cd line to this worktree). Autopilot, no questions.
