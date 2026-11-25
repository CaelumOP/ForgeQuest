# Master upgrade spec (LOCKED 2026-11-26)

Owner answer to `docs/MASTER_GATE.md`: **all defaults across A, B, C, D, except the overrides below.** Item numbers refer to MASTER_GATE.md; its text is part of this spec. Evidence: shared notes folder of session local_a06498bf (`stage2-code-map.md` with file:line references, `stage2-grader-scorecard.md`, `stage2-ux-web-notes.md`).

## Overrides (win over MASTER_GATE.md)
- **#3 Rest on short plans (<= 30 min sessions):** 0 s inside pairs and circuits, AND shorter rest after the round: 30-45 s after a 2-move superset, 0-30 s after a 3-move non-competing circuit (Push -> Pull -> Legs/Core). Sessions over 30 min: 0 s inside the pair, the exercise's normal rest after the round. Goal: 3 days x 20 min fits clearly more weekly sets; grader coverage for 3 x 20 must rise from ~18/25 (report before/after).
- **#5 Fat loss 6 days:** Day A and Day B split muscle groups cleanly (e.g. Push + Quads vs Pull + Hinge + Core), never the same muscles hard on back-to-back days. Grader balance for Fat loss 6 days must rise clearly from 3-6/20 (report before/after).
- **#22 "How to do it" / "Swap exercise" pills on live cards: HIDE, not kill.** Both stay one tap away: in the card's ⋮ menu and by tapping the exercise title. Features are not removed.
- **#20 Progress "Recovery" card:** kill only if it is a true duplicate and the muscle recovery view still exists elsewhere (Workouts muscle map; #30 only collapses that map before the first workout). If it is the only place showing recovery, hide instead of delete.

## Baseline additions (#10-#13)
- A bodyweight test with **0 reps** suggests an easier variation from the catalog (`ExerciseTiers` / easier tier of the same movement), shown right in the result.
- Build my plan gets an **obvious entry into the baseline flow** (next to Workouts card and Profile > You), not only the push-up / pull-up fields.

## Tool routing
Builds: Gemini Hydrogen (`agy-build ... gemini-4-hydrogen-high`), reads/scans: Gemini Neon. Gate 3 reviews: Codex Astra (`--approve-for-me` + no-edit brief, then `git status` check; `-s read-only` cannot read files here) plus Argon when quota allows. **Never use Gemini Carbon.** Claude: briefs, diff checks, own test/build runs, commits.

## Order
3A (grader port + floors, supersets/circuits, #1-#9 with overrides) -> 3B (baseline #10-#16 + additions) -> 3C (cuts #17-#43 with overrides, timer + pills D) -> Stage 4 (gw.sh testDebugUnitTest + assembleDebug, web tsc + vitest + build, plan golden parity, 390x844 EN + AR smoke per mission, Gate 3 Codex + Argon, `docs/MASTER_UPGRADE_REPORT.md`). Commit after each subsystem; update `docs/MASTER_PROGRESS.md` after every commit.
