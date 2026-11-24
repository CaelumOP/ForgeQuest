# Set-engine overhaul: progress

- Branch: `overhaul/set-engine-and-ux` (from `release/4.2` @ `186cc29`). Last commit on branch: `b8cd38a` (Phase 5). ALL PHASES DONE.
- Files: `docs/SET_AUDIT_LEDGER.md`, `docs/set_engine_research.md`, `docs/SET_DECISIONS.md` (stands in for DECISIONS.md, see its top), `docs/TRAINING_SCIENCE_SPEC.md` (Phase 2).
- **Next step:** none. Owner: on-device checks listed in docs/SET_OVERHAUL_REPORT.md; decide on merge into release/4.2.

## Checklist
- [x] Phase 0: branch, tool preflight (agy / codex), research -> `docs/set_engine_research.md`
- [x] Phase 1: Neon audit + Claude spot-check -> `docs/SET_AUDIT_LEDGER.md`
- [x] Question Gate answered (all defaults + hold-timer fix)
- [x] Phase 2: spec locked + Gate 1 amendments, committed `d049f17`
- [x] Phase 3a: effort semantics + progression `50b9a28` (gradle exit 0, 495 tests, 0 failed; new ProgressionRirTest x14)
- [x] Phase 3b: intra-session + Failure rest + hold snap `9eb64e1` (exit 0, 518 tests; IntraSessionTest x16, HoldMathTest x5, LiveWorkoutIntraSessionTest x2)
- [x] Phase 3c: baseline engine `a72933d` (exit 0, 534 tests; BaselineTest, PlanBuilderStepperTest, LiveWorkoutBaselineTest)
- [x] Phase 3d: planner + RoutineAuditor `9561c51` (exit 0, 551 tests; RoutineAuditorTest x12, PlanBuilderTest +5; Gate 2 used once)
- [x] Phase 3e: PR/e1RM, warm-up, recovery, history `fc8f06f` (exit 0, 570 tests; StrengthTest, WarmupRampTest, LiveWorkoutPrPillTest, ProgressTest +)
- [x] Phase 4: Compose polish `b32ca2b` (testDebugUnitTest + assembleDebug exit 0, 570 tests; 8 new @Previews)
- [x] Phase 5: Codex cold review (5 findings: 4 fixed, 1 spec A13) + Argon Gate 3 APPROVED; `b8cd38a`; final exit 0, 572 tests, assembleDebug ok; report docs/SET_OVERHAUL_REPORT.md
