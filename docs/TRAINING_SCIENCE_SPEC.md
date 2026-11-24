# Training-science spec (locked 2026-11-24)

Source of truth for the Android set-engine overhaul. Paths are under `app/src/main/java/com/forgequest/app/`. Every rule here is deterministic and lives in `domain/` (pure, unit tested) or the ViewModel, never in a `@Composable`.

## 0. Locked owner answers (Question Gate, 2026-11-24: "Accept all defaults")

1. Keep the Easy chip ("3+ left"), optional; all-Easy sessions may still add load early.
2. Hard = "1-3 left" and is progress-eligible. Only Failure (0 left) holds.
3. Stored legacy "OK" shows as no chip (neutral), matching the math.
4. Pain button unchanged: separate, one load step down next time.
5. Missed reps untagged -> hold. Missed at Failure -> hold + fatigue flag. Missed at Failure two workouts in a row -> one load step down.
6. Intra-session: trigger = a non-last working set tagged Failure or 2+ reps under the bottom of the range. Weighted: -5 % (at least one load step), -10 % if missed by 3+. Bodyweight: target = reps done. Applied automatically with an "Adjusted" cue and Undo. Hand-edited sets never touched.
7. Rest: +45 s to the running rest after a Failure tag, total capped at 240 s. No change for Easy/Hard.
8. Baseline computed from logged sets on the fly. No new Room table.
9. Cold start from a same-pattern lift x fixed ratio x 0.9; nothing to go on -> blank. No onboarding question. Add CORE bucket. Push-up/pull-up steppers accept 0.
10. Routine auditor = warnings only (plan builder review + plan editor). Never rewrites plans.
11. Generator: isolations 8-15 reps and 60-90 s rest; compounds 120-180 s by goal; core last. Existing plans untouched.
12. XP unchanged (it is recomputed from history; excluding warm-ups would lower existing levels).
13. Recovery: warm-ups and stretches excluded; cost Failure 1.25x, Hard 1.05x, Easy 0.8x.
14. New strings in English and formal Arabic. No web changes; parity gaps listed in the report.
15. Local commits on `overhaul/set-engine-and-ux` only. No push, PR, version bump or APK.
16. No Learn-by-Doing pauses.
17. (Owner request at the gate) Hold sets: stopping the hold timer between target and target + 3 s saves exactly the target (reaction-time grace). The hold stepper's +/- moves 1 s (long-press repeat stays); keypad quick deltas 5/10/30 stay.

## 1. Effort semantics (`domain/SetEffort.kt`)

| Stored | Chip | Meaning | RIR used in math | Progression | Fatigue multiplier |
|---|---|---|---|---|---|
| `""` / `OK` | none | implicit normal | 2 | progresses | 1.00 |
| `EASY` | Easy, "3+ left" | easy | 3 | progresses; all-Easy may load up early | 0.80 |
| `HARD` | Hard, "1-3 left" | hard, productive | 1 | progresses | 1.05 |
| `FAILURE` | Failure, "0 left" | failure | 0 | holds (see 2) | 1.25 |
| `PAIN` | Pain button | it hurt | n/a | one step down | 1.15 |

- `null`/blank is never failure, never zero stimulus, never a weight drop.
- `chipOf("OK")` returns `""`. `pick()` keeps the Failure <-> FAILURE set-type link.
- `isStrain` is replaced by explicit checks: the progression hold is `== FAILURE`; Pain keeps its own path.
- Note-word fallback (`SetEffort.of`) stays as is ("rir 0", "fail", «فشل» -> today HARD). Legacy note words stay HARD (no retro-escalation to FAILURE).
- `SetEffort.rir(e)` = EASY 3, OK 2, HARD 1, FAILURE 0, PAIN 2.

## 2. Next-session progression (`domain/Progression.kt`)

Definitions for the `last` session (working sets only, `countsForProgression`): `count` = planned sets (deload halves); `complete` = `last.size >= count`; `metMin` = complete and every set `reps >= repsMin` (REPS/WEIGHTED) or `seconds >= target` is NOT required for holds (holds keep current rules); `failed` = any set FAILURE; `pain` = any PAIN; `missedAtFailure(s)` = s has FAILURE and some set `reps < repsMin`.

Precedence (first match wins):
1. warm-up / cool-down item -> unchanged (no reason).
2. `pain` and weight > 0 -> one load step down (never below one step). Reason PAIN.
3. `missedAtFailure(last) && missedAtFailure(previous)` and weight > 0 -> one load step down. Reason BACK_OFF (new).
4. `missedAtFailure(last)` -> hold weight, reps = `repsMin`. Reason FATIGUE (new; the fatigue flag).
5. `failed` (target reps met) -> hold weight and reps (consolidate). Reason HARD (existing string, wording updated to "last time was to failure").
6. not `metMin` (missed reps or missing sets, untagged) -> hold weight, reps = `repsMin`. Reason MISSED.
7. deload week or MAINTAIN -> hold. Reason SAME.
8. Mode rules (unchanged otherwise): DOUBLE range -> +1 rep per workout, all sets at `repsMax` -> +1 load step and reset to `repsMin`; DOUBLE single target -> two clean workouts at the same weight; all-Easy -> load step once `repsMin` met; LINEAR -> +2.5/+5 kg, in lb +5/+10 lb (snapped). HARD sets count as clean.
- Bodyweight REPS: +1 rep up to `repsMax + 2` only when `metMin && !failed`; missed -> hold at the reps done (never inflated). `ExerciseTiers.readyForHarder` excludes FAILURE and PAIN.
- HOLD: unchanged, except FAILURE holds (no +5 s) and HARD no longer blocks.
- The debrief (`WorkoutDebriefMath.nextTime`) passes the same `deload` and `lowerBody` as the live prefill.
- Remove the stale `lowReadiness` KDoc mentions.

## 3. Intra-session adjustment (new pure `domain/IntraSession.kt`; wired in `LiveWorkoutViewModel`)

`IntraSession.adjust(source, remaining, kind, repsMin, unit): List<SetEntity>` returns the changed sets.
- Source = a done set with `countsForProgression`. Remaining = same `itemPosition`/`exerciseId`, higher `setIndex`, not done, not hand-edited (`edited`), `countsForProgression`. Empty remaining -> no change.
- Trigger: `source.effort == FAILURE` or (`repsMin != null` and `source.reps <= repsMin - 2`). `miss = repsMin - reps` (0 when only Failure).
- WEIGHTED with weight w > 0: `factor = if (miss >= 3) 0.90 else 0.95`; `new = snapDown(w * factor)` to the unit grid (0.25 kg / 2.5 lb, and the plate step `unit.loadStep`), then `new = min(new, w - loadStep)`, floor 0; if `w <= loadStep` keep w. Remaining reps = `repsMin` when known, else `source.reps`.
- REPS (bodyweight; extra weight untouched): remaining reps = `source.reps` (min 1).
- HOLD with FAILURE: remaining seconds = `source.seconds` (min 1).
- Called from `setEffort` (after the Failure tag) and from `toggle` (reps miss) after `carryForward`, so it overrides the carried values. Applied once per source set (track `adjustedBy`). The VM keeps the pre-change rows for Undo and exposes `adjustment: StateFlow<Adjustment?>` (exercise, percent or reps, undo token) for the UI cue.
- Rest: `IntraSession.extendRest(remaining, current total) ` -> +45 s, total capped at 240 s, only when a rest for that same set is running.

## 4. Baseline engine (new pure `domain/Baseline.kt`)

- Buckets: PUSH = HORIZONTAL_PUSH, DIP_PUSH, VERTICAL_PUSH; PULL = VERTICAL_PULL, HORIZONTAL_PULL, UPPER_BACK_PULL; SQUAT = SQUAT_BILATERAL, SQUAT_UNILATERAL; HINGE = HIP_HINGE; CORE = CORE_ANTI_EXTENSION, CORE_FLEXION_ROTATION. Others: no bucket. Pattern from `ExerciseSwapEngine.profileFor(ex).pattern`.
- Per-exercise e1RM (source of truth once >= 1 working set exists): over working sets with weight > 0 and 1..12 reps (sets > 12 reps ignored): `e = w` when reps == 1 and effort FAILURE, else `w * (1 + min(reps + rir, 12) / 30)`. Baseline = max over the last 2 sessions with that exercise. Status CALIBRATED.
- Cold start (WEIGHTED exercise, zero working sets ever): donor = most recent CALIBRATED exercise, preferring the same `MovementPattern`, else the same bucket (extra x 0.8). Equipment ratio to a barbell equivalent: BARBELL 1.0, MACHINE 1.0, SMITH 0.9, CABLE 0.6, DUMBBELL 0.4 (per hand), KETTLEBELL 0.4; anything else -> no estimate. `targetE1rm = donorE1rm / ratio(donor) * ratio(target) * 0.9 [* 0.8]`; working weight for `repsMin` at 3 RIR: `w = targetE1rm / (1 + (repsMin + 3) / 30)`, snapped down to the unit grid; < one load step -> blank. Status ESTIMATED. No donor -> blank, status NONE.
- Bodyweight: capacity = best reps in the last session; cold start leaves the plan default.
- Use: `WorkoutRepository.buildSetsFor` fills weight only when the exercise has no history and the plan weight is null/0. The live card shows the status (estimated / calibrating); the UI does no math.
- PlanBuilder steppers: distinguish "not entered" from 0 so `pullUpMax = 0` reaches FOUNDATION.

## 5. Build Planner (`domain/PlanBuilder.kt`, new pure `domain/RoutineAuditor.kt`)

Generator (new plans only):
- Compound = pattern in {HORIZONTAL_PUSH, DIP_PUSH, VERTICAL_PUSH, VERTICAL_PULL, HORIZONTAL_PULL, SQUAT_*, HIP_HINGE}; everything else isolation.
- Isolation rep range: STRENGTH 8-12, MUSCLE 10-15, others keep their range if its min >= 8, else 8-15.
- Rest before the RestPref factor: compound STRENGTH 180, MUSCLE 150, GENERAL 120, FAT_LOSS 120, ENDURANCE 120; isolation STRENGTH 90, MUSCLE 90, GENERAL 75, FAT_LOSS 60, ENDURANCE 60; clamp 30-240. Conditioning/finisher rest unchanged.
- Order inside a day: compounds, then isolations, then core.
Auditor (`RoutineAuditor.audit(days, exercises)`; warm-up/cool-down/extra blocks excluded; volume = primary 1.0 + secondary 0.5 weekly working sets):
- Landmarks (MEV / MRV): CHEST 8/22, UPPER_BACK 10/25, SHOULDERS 8/26, BICEPS 8/20, TRICEPS 6/18, QUADS 8/20, HAMSTRINGS 6/20, GLUTES 4/16, CALVES 8/20, ABS 0/25, TRAPS 0/26, others 0/20. BELOW_MEV only for CHEST, UPPER_BACK, SHOULDERS, QUADS, HAMSTRINGS, GLUTES; ABOVE_MRV for all.
- PUSH_PULL: push-bucket primary sets > 1.2 x pull-bucket primary sets (and push >= 6).
- ISOLATION_FIRST: in a day, an isolation placed before a compound that shares its primary muscle.
- JUNK_VOLUME: > 10 primary sets of one muscle in one day.
- RECOVERY_OVERLAP: one muscle with >= 6 primary sets on two training days one calendar day apart (only when days have weekdays; rotation plans skip this).
- Output: `List<Finding(kind, muscle?, dayIndex?, value, limit)>`, sorted by severity; UI shows text from string resources (EN + AR).

## 6. PR, e1RM, warm-ups, recovery, debrief

- One e1RM: `Strength.e1rm(w, reps)`: reps <= 0 or w <= 0 -> 0; reps == 1 -> w; 2..12 -> Epley; > 12 -> `12 + 3 ln(1 + reps - 12)` effective reps. `ProgressMetrics.epley1Rm` and `Records.score` (weighted) use it; PRs use raw reps (no RIR credit). PrPicker rules unchanged.
- `ExerciseDetailScreen` charts use only `countsForScore` sets. The live PR pill shows for `set.isPr || set.id in prIds` on done sets.
- Warm-up ramp: the bar floor applies only to barbell lifts (`PlateMath.isBarbellExercise`); others start at 50 % snapped to the load step; barbell ramps prepend an empty-bar set when working >= 3 x bar. `addPlanItem` defaults `autoWarmup` for heavy barbell compounds.
- Recovery (`ProgressRules.kt`): sets that fail `countsAsWorkingSet` or are stretches give 0; multipliers per section 1; a weighted set with load counts at least 0.5 of a set (heavy singles no longer 0.17).
- `WorkoutScore` reads pain with `SetEffort.of`. XP unchanged. Editing a set in history clears `autoTicked`.

## 7. Hold timer (`LiveWorkoutViewModel.stopHold`, `LiveWorkoutSets.kt`)

`HoldMath.recorded(held, target) = if (held in target..target + 3) target else held`. Stepper step 1 s.

## 8. Room

No schema change planned (stays v12). If a step needs one: additive column with default, new `MIGRATION_12_13` in `AppDatabase.MIGRATIONS`, exported schema, and a populated-v12 upgrade case in `data/db/MigrationTest.kt`.

## 9. Required tests per subsystem

Every subsystem ships JUnit tests in `app/src/test/java/com/forgequest/app/domain/` covering: blank vs HARD vs FAILURE paths; weighted and bodyweight paths; cold start vs calibrated baseline; Set 1 failure -> Set 2 adjusted (and hand-edited untouched, Undo restores); auditor junk volume, push/pull imbalance, recovery overlap, isolation-first; hold snap; e1RM high-rep cap. Gate: `./gradlew testDebugUnitTest` exit 0 before each commit.

## 10. UI rules (Phase 4)

Follow `DESIGN.md` (Soft Coach) tokens and components; no new libraries. Untagged stays zero-tap; Hard/Failure one tap, >= 48 dp. The adjustment cue and baseline status sit under the exercise header, not in the set row. Previews: cold start, untagged, Hard, Failure, adjusted next set. EN + formal AR strings.

## 11. Gate 1 amendments (Gemini 4 Argon review, 2026-11-24; these override earlier sections where they differ)

A1. Live `repsMin`: the VM exposes `planItemsByPos: StateFlow<Map<Int, PlanItemEntity>>` (built like `supersetGroups`); lookup by `itemPosition` with matching `exerciseId`; fallback 8 for weighted ad-hoc sets, null for holds.
A2. Intra-session snap: work in display units, `new = floorToStep(fromKg(w) * factor, unit.loadStep)`, capped at `fromKg(w) - loadStep`, floored at `loadStep` when `w > loadStep`; convert back to kg.
A3. `stopHold()` sets `effortSetId = updated.id` so the effort chips appear after a timed hold.
A4. `RestTimer` gains `setId` and `isShortTransition`; the +45 s Failure extension applies only when the running rest belongs to that set, is not rest-pause and not a short superset transition.
A5. Baseline equipment ratios use the real `Equipment` names: BARBELL 1.0, SMITH_MACHINE 0.9, DUMBBELLS 0.4, KETTLEBELL 0.4, CABLE_MACHINE / LAT_PULLDOWN / SEATED_ROW_MACHINE 0.6, other machines 1.0. `buildSetsFor` applies the baseline before computing `firstWorkingKg` so estimated barbell loads also get warm-up ramps.
A6. Progression precedence becomes: warm-up -> pain (weighted w > 0: step down, reason PAIN; bodyweight/hold/0 kg: hold, reason HARD) -> BACK_OFF (step down, reps = repsMin) -> deload / MAINTAIN (SAME) -> FATIGUE -> failure hold (HARD) -> MISSED -> mode rules. `stepUpLinear` uses +5/+10 lb for lb users.
A7. `WorkoutDebriefMath`: HoldSteady uses `== FAILURE`; EffortMastery keeps `in setOf(HARD, FAILURE)`. Tests whose expectations change are updated, not deleted (ProgressionTest, WorkoutDebriefMathTest, ProgressTest RecoveryTest "صعب" 1.05, PlanBuilderTest strength ranges).
A8. PlanBuilder: `restFor(pick)` knows compound vs isolation; sorting keeps FINISHER last and sorts the rest by (core last, compounds first); `finalizeDay` trims CORE before FOCUS so `focusMuscleGetsMoreWeeklySets` holds.
A9. RoutineAuditor takes the plan's `scheduleMode`; recovery overlap only for WEEKDAY plans (days 1 apart, incl. Sun-Mon wrap); push/pull counts `item.sets` once per exercise by pattern bucket.
A10. `WarmupRamp.generate(..., isBarbell: Boolean = true)`: non-barbell floors at one load step; barbell with working >= 3 x bar prepends bar x 10. `addPlanItem` sets `autoWarmup = isHeavyBarbellCompound(ex)`.
A11. `Records.score` keeps the `r / 100f` no-load fallback. A set with `setType == FAILURE` and blank effort reads as FAILURE in `Progression.inputs` and `Recovery`.
A12. Also in scope: History `WeekCard` volume uses `countsAsWorkingSet`; Phase 4 raises `ChoiceChip` and the Stepper value box to 48 dp and lets a done set's effort label reopen the chips in the live workout. Deferred (report): effort editing inside History's session detail.
A13. (Phase 5 correction) "Missed reps -> reps = repsMin" in section 2 rules 6 and the A6 MISSED step means: each set holds the weight; a set that hit `repsMin` or more keeps its reps, a set below it restarts at `repsMin` (last `[12, 12, 6]` -> next `[12, 12, 8]`). This keeps the earlier audited rule `AuditFixesTest.oneFailedSetStartsAtTheBottomOfTheRange` (2026-10-24) and never throws away reps that were met. FATIGUE and BACK_OFF still reset every set to `repsMin`.
