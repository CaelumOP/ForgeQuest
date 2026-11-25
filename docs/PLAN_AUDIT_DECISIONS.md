# Plan audit: locked decisions and design (Phases 2-4)

Branch `audit/plan-quality` (from `android/intra-session-ledger` @ `ee42c06`). Before-overhaul baseline: `454bcbc` (origin/main, 4.2).
Phase 0-1 findings: Question Gate report (session transcript) and shared note `plan-audit-phase1-findings.md`.
Owner answers (2026-11-25): Q1-Q12 all (a), plus S6, S7 and the Android `stopHold` lock gap.

## D1 Plan builder (Android `PlanBuilder.kt`, Web `plan-builder.ts`, identical behaviour)

1. **One counting rule.** New `MuscleVolume.credit(ex)` (Android `domain/RoutineAuditor.kt` companion helper, web `routine-auditor.ts`): every distinct primary muscle 1.0, every secondary not in primary 0.5. RoutineAuditor and the builder both use it. Landmarks (MEV/MRV per muscle) move to one shared table used by both.
2. **Set allocation replaces "first primary muscle / pick count".**
   - Every MAIN/FOCUS/ACC pick starts at 2 sets (COND 3, FINISHER 2 unchanged; light day max 3).
   - Weekly target per muscle: major muscles NEW 10, SOME 12, SEASONED 14, minor muscles half; focus +4; then clamped to [MEV, MRV].
   - Repair pass (before adding): while a rule is broken, remove the lowest-priority pick that causes it (FINISHER > ACC > core > MAIN duplicates; never the last pick of a day). Rules: weekly muscle volume <= MRV, one day <= 10 primary sets per muscle, push sets <= 1.2 x pull sets once push >= 6, back-to-back weekdays never both >= 6 primary sets of one muscle.
   - Greedy fill: repeatedly add one set to the pick whose credits best close the remaining gap to target (ties: earlier day, then plan order), only if after the add: no muscle above min(MRV, target + 2) ... (cap = MRV), no day-muscle above 10, push/pull rule holds, overlap rule holds, the pick stays <= 5 sets (NEW 4), and the day still fits its minutes (same 40 s + rest per set estimate, warm-up/cool-down 5 min each). Stops when no add helps.
   - Time fitting happens before the fill: with every pick at 2 sets, drop picks (finisher, accessory, non-focus core, then last non-focus pick) until the day fits. The old "shave sets" loop goes away because the fill never exceeds the time.
3. **Slots.** PULL day accessory HINGE becomes a rear-delt/upper-back pull (`BACK_PULL`), so pull day stops adding leg volume.
4. **Picker.** Score ties break by: staple exercise first (curated `STAPLE_IDS` list per movement, same list on both platforms), then id. Tier caps, focus bonus and "Show a different version" (offset over the tied group) are unchanged.
5. **Catalog fixes (both catalogs + migration for existing installs):**
   - `sled_overhead_backward_walk`: equipment `BODYWEIGHT` -> `MACHINE`, pattern -> `CARRY_CONDITIONING`.
   - `kipping_muscle_up`: equipment `BODYWEIGHT` -> `PULL_UP_BAR`, pattern `HORIZONTAL_PULL` -> `VERTICAL_PULL`.
   - `isometric_neck_exercise_front_and_back`, `isometric_neck_exercise_sides`: pattern `UPPER_BACK_PULL` -> `MOBILITY`.
   - Flyes (`db_fly` and every id with `fly`/`flye`/`crossover`/`pec_deck` in a push pattern): treated as isolation by a shared `isCompoundExercise(ex)` (builder order/reps/rest, auditor isolation-first check, baseline donors).
   - Existing installs: `seedLibrary` (Android) / `seedLibrary` (web) overwrite equipment/pattern of non-custom rows whose stored value equals the old wrong value (a `METADATA_FIXES` map: id -> old equipment, old pattern). Custom or user-edited values are left alone. No Room/Dexie schema change.
6. **Web parity bug:** `assignSets(builds.values())` one-shot iterator (fixed by passing an array).
7. **Short plan note (Q1).** RoutineAuditor gets kind `SHORT_PLAN` (info). When the plan has <= 2 training days or its average estimated session is <= 25 min, all BELOW_MEV findings are replaced by one SHORT_PLAN finding. AuditItem gains optional `restSeconds` (default 90) so the auditor can estimate minutes. Computed from the plan itself, so builder review and plan editor agree; manual edits still get every other warning.

Acceptance: every generated plan for 3-6 days x 45-60 min (all goals, presets, experience, focus) -> zero findings except where the equipment makes a rule impossible (reported with counts; target 100 %). 2-day / 20-min plans -> at most the SHORT_PLAN note. Android == Web on every plan in the matrix (exercise ids, sets, reps, rest).

## D2 Progression (Android `Progression.kt`/`IntraSession.kt`/`Baseline.kt`/`ExerciseTiers.kt`, web mirrors)

- **S1 (Q3):** weighted DOUBLE/LINEAR/MAINTAIN/back-off read the session's top working weight for every set (a cut set no longer carries its lower weight forward). Sets done below the top weight restart at the bottom of the range. Trade-off noted: hand-typed back-off sets also return to the top weight next time.
- **S2 (Q4):** bodyweight reps never prescribe < 1. A missed session (any set below the minimum) prescribes every set at min(repsMin, best reps in that session), >= 1, so a cut set does not stay cut. Two missed sessions in a row (reps or holds) -> new reason `EASIER` ("Two tough workouts in a row. Try an easier version."), EN + formal AR.
- **S3 (Q6):** holds read the session's best seconds, rounded down to 5 s. Missed hold (best < target): no Failure -> best+5 (max target); Failure -> best; minimum 5 s. Met holds keep +5 s steps to the ceiling. All sets get the same value.
- **S4 (Q5):** back-off also when two workouts in a row each had a set 2+ reps under the minimum, tagged or not.
- **S5 (Q6):** when one load step is more than 10 % of the weight, a live cut keeps the weight and caps reps at what was done (>= 1), and back-off keeps the weight at the bottom of the range.
- **S6:** cold start cross-pattern transfer: same pattern 0.9; same bucket default 0.9 x 0.8; horizontal -> vertical push 0.9 x 0.6; two-leg -> one-leg squat 0.9 x 0.6; flyes never donate or receive.
- **S7:** `readyForHarder` triggers at min(15, top of the plan range + 2) so a 8-12 push-up at 14 x 3 twice surfaces the harder version; the live VM passes the plan item's top reps.
- **stopHold:** Android `stopHold` runs inside `liveMutex`, reads fresh rows and uses the pre-carry `IntraSession.plan` like rep sets.

## D3 Wording (Phase 3)
Q7-Q11 strings EN + formal AR in `app/src/main/res/values*/` then web regenerated; "More tools" collapsed section (Progress extras) holds "Max strength estimate" on both platforms. Web overlays (`web/src/i18n/web/*.json`): entries identical to the generated base are removed; jargon entries updated; deliberate web-only wording kept and listed in the report.

## Circuit breaker log
(none yet)

## Gate 1 (Gemini 4 Argon, 2026-11-25): CHANGES, 18 points, all accepted
1 hard cap = MRV only; target only scores. 2 MEV floor only for BELOW_MEV muscles. 3 pull sets may be added (no gain) when a push set with gain is blocked only by push/pull; `db_bench_press` gains SHOULDERS secondary. 4 baseline rejects flyes only (`isFly`), other isolations keep same-pattern transfer. 5 weighted metMin/hitTop count only sets at the top weight; lower sets restart at the minimum. 6 holds: metMin needs every set at the target; hitTop vs max(target, base); EASIER before FATIGUE/HARD and needs a previous session. 7 S5 back-off without a weight drop shows MISSED, not BACK_OFF. 8 web backup restore re-runs the catalog fixes. 9/10 time fit and repair keep the last pick of a day, include FOCUS last, stop when nothing can be removed (200-round cap). 11 also fix `sled_reverse_flye`, `sled_row`, `sled_overhead_triceps_extension`, `sled_push`, `sledgehammer_swings` (equipment MACHINE) and `seated_head_harness_neck_resistance`, `lying_face_down/up_plate_neck_resistance` (pattern MOBILITY). 12 MOBILITY never eligible in the builder. 13 picks sorted big-lifts-first before allocation. 14 Sunday-Monday wrap in overlap. 15 SHORT_PLAN minutes include warm-up/cool-down; only when a BELOW_MEV existed; value = training days, limit = 2; handled in every `when`. 16 back-off pairs only sessions at the same top weight. 17 live cut never prescribes 0 reps. 18 `readyForHarder` callers (live VM after plan items load, debrief, web) pass the plan's top reps; A11 blank-Failure counts.

## Build notes
- B1 (matrix, after first rewrite): 3-6 d x 45-60 min clean 73 % -> remaining BELOW_MEV came from (a) six different lifts at 2 sets in a tight day and (b) the "unused exercise first" filter dropping to odd picks once staples were used. Added: a trade step (drop one lift that does not feed a short muscle, refill, keep only if the shortfall shrinks and no rule breaks) and tie order unused staple > used staple > unused other > used other.
- B2: 31 catalog rows sit in a movement group their main muscles do not match (e.g. `single_leg_push_off` QUADS as HORIZONTAL_PUSH, `decline_reverse_crunch` ABS as DIP_PUSH, `squat_jerk` QUADS as VERTICAL_PUSH). Rather than rewrite 31 rows, the builder ignores a row whose primary muscles miss its group (`fitsItsMovement`, same table on web). Swap screen and library are unchanged.
