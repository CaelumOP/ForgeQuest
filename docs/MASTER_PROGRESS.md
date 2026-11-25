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
- [ ] 3B Input & Test Baseline
- [ ] 3C wavy timer, sliding pills, approved cuts
- [ ] Stage 4 verification, smoke, Gate 3, report

## Next step
3B Input & Test Baseline: shared spec in the untracked temp folder docs/master/3b-spec.md. Build Android data/domain, then Android UI, then web data/domain, then web UI (Gemini Hydrogen, briefs under 25 min each, no Gradle inside agy). Then 3C (docs/master/3c-spec.md), Stage 4.
