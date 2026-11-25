# Web set-engine overhaul: report (Part B)

Branch `overhaul/web-set-parity`, cut from `2542be5` (Android Part A closure). Scope: port the Android set-engine
overhaul (`docs/TRAINING_SCIENCE_SPEC.md`, amendments A1-A13) to the web PWA in `web/`. Only `web/` and `docs/` changed;
no Dexie schema/version change, no dependency change, no backup format change.

## Raw results

| Check | Result |
|---|---|
| Baseline (W0) | vitest exit 0, 70 files / 617 tests; tsc exit 0; production bundle exit 0 |
| Final vitest | exit 0, 75 files / 725 tests |
| Final tsc (`tsc --noEmit`) | exit 0 |
| Final production build (`npm run build`) | exit 0; JS 2,398.04 kB (gzip 610.89 kB), CSS 136.90 kB, zxing wasm 946.59 kB; PWA precache 17 entries. Vite warns about the >500 kB single app chunk; this work added no dependencies or new chunks. |
| Files outside `web/` + `docs/` in `git diff 8d562f9..HEAD` | none |
| Codex cold review (gpt-6-astra, read-only) | `VERDICT: DEFECTS (4)`; all 4 fixed in W2 (see below) |
| Gemini Argon Gate 3 (gemini-4-argon-high) | First pass `CHANGES REQUESTED` (3 medium, 2 low; one overlaps Codex 1); all fixed. Re-review: ARGON_RERUN |

## Commits

| Phase | Commit | What |
|---|---|---|
| W0 | `04de964` | Baseline + `docs/WEB_SET_AUDIT_LEDGER.md` |
| W1a | `899efd6` | RIR semantics (Hard progresses, Failure holds), BACK_OFF / FATIGUE, A13 missed reps, lb linear steps, deload sessions skipped; EN/AR strings regenerated |
| W1b | `6bc6e39` | Intra-session adjustment with cue / Undo / untick revert, serialised live writes, +45 s Failure rest, hold snap, 1 s hold stepper, chips after timed hold |
| W1c | `2c2d3a6` | Hybrid baseline (RIR e1RM + pattern cold start), Estimated start line, 0 enterable for push-up / pull-up max |
| W1d | `9dcbf63` | Planner compound / isolation reps, rest and order, focus-safe trim; RoutineAuditor warnings in plan editor and builder review |
| W1e | `62f35ad` | Capped e1RM shared by charts and records, recovery skips warm-ups with RIR multipliers, non-barbell warm-up floor and empty-bar set, live PR pill, working-set charts and week volume |
| W1f | `4846a99` | Re-tag a done set's effort, 48 px set-row targets, stale Hard caption fix in the web string overlay |
| W2 | `dcc166a`, W2B_COMMIT | Review fixes (below) + this report |

Progress-only commits (`b03a5bd`, `7c63a8a`, `c010209`, `7f76290`, `4e41363`, `ee58ecb`) tick `docs/PROGRESS.md`.

## W2 review fixes

| # | Source | Severity | Defect | Fix |
|---|---|---|---|---|
| 1 | Codex | high | Hand edits (`save`) wrote whole rows outside the live write queue, so a queued tick / effort / Undo step could overwrite an edit with an older snapshot, or an edit could erase a Failure tag. | `save` now computes the changed fields against the current row, applies them optimistically, and writes them through the same queue onto the row as it is when its turn comes (`LiveWorkoutScreen.tsx`, `save`). |
| 2 | Codex | medium | Failure after a carry-forward that raised targets (e.g. did 12 of 10) left the raised targets when the cut itself changed nothing. | `applyIntraSession` computes intended numbers from the pre-carry rows but compares and writes against the current rows; a no-cut Failure restores the pre-carry targets silently (no cue). |
| 3 | Codex | medium | Unticking (or Undo on) an earlier Failure set restored rows that a newer adjustment had rewritten since. | New `AdjustmentLedger` (`domain/intra-session.ts`: `recordAdjustment` / `releaseAdjustment`) keeps a per-row owner stack; only rows the reverted adjustment still owns go back, and a newer adjustment inherits the older pre-adjustment numbers. |
| 4 | Codex | low | A legacy IndexedDB row with `effort` undefined / null threw in `computeAdjustment` (`.trim()`). | Null-safe check in `computeAdjustment`, and the Dexie `sets` reading hook now defaults `effort` and `note` to `''` (read-time only; no rows rewritten, no version bump). |

Tests added: `adjustment ledger` (3) and `computeAdjustment legacy effort` (1) in `domain/intra-session.test.ts`.

| # | Source | Severity | Defect | Fix |
|---|---|---|---|---|
| 5 | Argon | medium | Unticking a Failure-tagged set cleared `effort` but kept the `FAILURE` set type, so A11 still read it as Failure: re-ticking it untagged cut the later sets again, and a later Easy / Hard tag could not reset the type. Its +45 s rest mark also stayed. | Untick resets a Failure-given `FAILURE` type to `WORKING` (same rule as `pickEffort` leaving Failure) and forgets the set's rest-extension mark. |
| 6 | Argon | medium | Same race as 1, plus: `SetRowView` sent `{ ...renderedRow, ...patch }`, so a stale rendered weight could still overwrite a fresh adjustment. | `onChange` now carries `(set, patch, editedField)`; only the patch is merged. `toggleAll`, copy-last-set and use-last-time pass explicit patches. |
| 7 | Argon | medium | `harderHint` counted rest-pause / drop / warm-up rows and ignored A11 (ledger rows 1d, 2e). | Filters `countsForProgression` and reads a blank-effort `FAILURE` set as Failure. |
| 8 | Argon | low | Closing the Pain note sheet without saving left the effort chips open (Android closes them on any effort pick). | `NoteSheet.onClose` dismisses the chips when it was opened from the Pain chip. |
| 9 | Argon | low | In a deload week, warm-up / cool-down plan items were halved too (Android gates deload with `!isExtra(block)`). | `buildSetsFor` and `progressionReasonFor` gate deload on `item != null && !isExtraBlock(item.block)`. |

Tests added: `W2 review fixes` (3) in `data/workouts.test.ts`; the deload test was checked against a mutant (fails without the gate).

Argon re-review of `dcc166a` found two gaps in fix 2 (both in `applyIntraSession`), fixed in the next commit:

| # | Source | Severity | Defect | Fix |
|---|---|---|---|---|
| 10 | Argon re-review | high | When carry-forward had already written the same lowered numbers (e.g. 6 of 10 push-ups), the "nothing to write" early return skipped the cue, Undo and the ledger, so untick no longer restored the targets. | A real cut always records the ledger and shows the cue; only the write is skipped when there is nothing to write. |
| 11 | Argon re-review | medium | `computeAdjustment` returns `null` when nothing changes against the pre-carry rows, so the silent restore of a carried raise (12 of 10, then Failure) never ran. | New `triggersAdjustment(source, kind, repsMin)` in `domain/intra-session.ts`; when it fires and the cut is `null`, the pre-carry numbers are written back silently (no cue, nothing for Undo). |

Tests added: `triggersAdjustment` (3) in `domain/intra-session.test.ts`.

## Parity checklist (vs `docs/WEB_SET_AUDIT_LEDGER.md`)

| Ledger row | Rule | Status |
|---|---|---|
| 1a-1e | Effort semantics: `OK` reads blank, RIR map, Hard "1-3 left", A11 blank+FAILURE setType, tap done badge to re-tag | Done (W1a, W1f) |
| 2a-2f | Progression A6 precedence, A13 per-set missed reps, Hard progresses, lb linear steps, bodyweight REPS / HOLD, readyForHarder, debrief threading | Done (W1a) |
| 3 | Intra-session adjustment, cue, Undo, untick revert, edited flags, serialised writes | Done (W1b; W2 fixes 1-3) |
| 4 | Failure rest +45 s (cap 240 s, once per set, not rest-pause / short transition) | Done (W1b) |
| 5a-5c | Hold snap, 1 s stepper, chips after timed hold | Done (W1b) |
| 6 | Baseline cold start / RIR e1RM, Estimated start line | Done (W1c) |
| 7a-7c | Planner reps / rest / order / trim, push-up & pull-up 0 | Done (W1c, W1d) |
| 8 | RoutineAuditor + warning card in plan editor and builder | Done (W1d) |
| 9a-9e | Capped e1RM shared, PrPicker, working-set charts, live PR pill, week volume | Done (W1e) |
| 10a-10b | Warm-up ramp non-barbell floor, empty bar x10, `addPlanItem` autoWarmup | Done (W1e) |
| 11 | Recovery excludes warm-ups / stretches, RIR multipliers, loaded floor 0.5 | Done (W1e) |
| 12a-12b | WorkoutScore pain via `effortOf`, history edit clears `autoTicked` | Done (W1e) |
| 13 | Storage / backup | No change needed (W2 adds read-time `effort`/`note` defaults only) |
| 14 | EN/AR strings | Done (W1a regenerated, W1f overlay fix) |

## Browser / PWA smoke checklist

Done in W1f (EN + AR, dev server): live workout tick / untick, effort chips, re-tag done set, Failure cue + Undo,
Failure rest extension, hold timer stop + chips, Estimated start line, plan editor audit card, RTL layout.

To re-check by hand on the production build (`npm run build && npm run preview`):
- [ ] Install as PWA, reload offline, start a workout.
- [ ] Type a new weight on set 2, then quickly tick set 1 and tag Failure: the typed weight stays.
- [ ] Do 12 of 10 reps on a bodyweight set, tick, tag Failure: later targets go back to 10.
- [ ] Failure on set 1, then Failure on set 2, then untick set 1: set 3 keeps set 2's cut.
- [ ] Arabic: adjustment cue, audit card and Estimated start line read right to left.

## Watch items

- Undo restores the pre-carry numbers (same as Android).
- The adjustment cue and the ledger are in-memory: a reload mid-workout keeps the adjusted numbers but loses Undo.
- History effort editing stays deferred, as on Android.
- Android follow-up (out of this part's fence): fixes 2, 3 and 5 apply to `LiveWorkoutViewModel.kt` too (untick at
  line ~474 keeps the `FAILURE` type; the pre-carry compare and adjustment ownership are the same shape there).
