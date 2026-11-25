# Dev-only plan quality scorecard

Deterministic sample: 7700 / 23,100 cases; all 700 goal x days (2-6) x preset x minute groups, 11 cases/group. All 11 focuses in each group; experience = (group index + focus index) mod 3. Experience counts rotate 4/4/3. These are sample means, not exhaustive population results.

Overall mean **81.65/100**; minimum **41.88/100**. Matrix generation/grading: 47.13 seconds.

Formula source: web/src/domain/plan-grader.ts header. Scores are heuristic comparisons, not measured outcomes. Goal targets are explicit rubric assumptions; builder targets are experience-based. Missing muscles dominate coverage. Duration uses auditor ceil(work seconds/60) plus 5 minutes per warm-up/cool-down block; overhead above 20% is penalized.

Pairing checks catalog muscle overlap and named spinal-loading lifts; it cannot establish individual safety. FAT_LOSS items can be circuits despite absent explicit supersets. Same-block circuits are checked pairwise and excluded from additional pairing savings. Duration remains the auditor estimate for comparability.

## Mean by minutes

| Group | N | Total /100 | Efficiency /25 | Coverage /25 | Balance /20 | Quality /15 | Pairing /15 |
|---|---:|---:|---:|---:|---:|---:|---:|
| 20 minutes | 1925 | 76.83 | 19.90 | 18.54 | 14.55 | 10.74 | 13.10 |
| 30 minutes | 1925 | 84.08 | 19.72 | 23.12 | 18.30 | 10.83 | 12.10 |
| 45 minutes | 1925 | 83.35 | 18.52 | 24.08 | 17.95 | 10.79 | 12.00 |
| 60 minutes | 1925 | 82.34 | 16.99 | 24.57 | 17.94 | 10.84 | 12.00 |

## Mean by days

| Group | N | Total /100 | Efficiency /25 | Coverage /25 | Balance /20 | Quality /15 | Pairing /15 |
|---|---:|---:|---:|---:|---:|---:|---:|
| 2 days | 1540 | 84.23 | 19.85 | 21.04 | 19.96 | 10.94 | 12.43 |
| 3 days | 1540 | 84.90 | 19.61 | 22.17 | 19.94 | 10.86 | 12.33 |
| 4 days | 1540 | 82.59 | 18.72 | 23.06 | 17.55 | 10.99 | 12.27 |
| 5 days | 1540 | 80.57 | 18.17 | 23.29 | 16.19 | 10.70 | 12.23 |
| 6 days | 1540 | 75.94 | 17.56 | 23.34 | 12.28 | 10.52 | 12.24 |

## 15 worst groups (goal | days | preset | minutes)

| Group | N | Total /100 | Efficiency /25 | Coverage /25 | Balance /20 | Quality /15 | Pairing /15 |
|---|---:|---:|---:|---:|---:|---:|---:|
| FAT_LOSS / 6 / PULL_UP_BAR_ONLY / 20 | 11 | 57.05 | 18.12 | 22.07 | 3.00 | 7.95 | 5.91 |
| FAT_LOSS / 6 / BODYWEIGHT_ONLY / 20 | 11 | 57.58 | 17.70 | 21.19 | 4.27 | 8.96 | 5.45 |
| FAT_LOSS / 6 / HOME_GYM / 20 | 11 | 58.65 | 16.90 | 20.55 | 6.82 | 12.56 | 1.82 |
| FAT_LOSS / 6 / DUMBBELLS_AND_KETTLEBELL / 20 | 11 | 59.00 | 17.10 | 21.04 | 4.55 | 11.77 | 4.55 |
| FAT_LOSS / 6 / DUMBBELLS_AND_BAR / 20 | 11 | 59.41 | 17.47 | 21.26 | 4.82 | 10.41 | 5.45 |
| FAT_LOSS / 6 / FULL_GYM / 20 | 11 | 59.80 | 16.78 | 20.92 | 5.73 | 12.74 | 3.64 |
| FAT_LOSS / 6 / CALISTHENICS_PARK / 20 | 11 | 59.81 | 17.83 | 21.24 | 3.73 | 9.74 | 7.27 |
| FAT_LOSS / 6 / PULL_UP_BAR_ONLY / 60 | 11 | 61.37 | 13.53 | 25.00 | 13.18 | 9.66 | 0.00 |
| FAT_LOSS / 6 / DUMBBELLS_AND_BAR / 60 | 11 | 62.39 | 13.88 | 24.97 | 12.91 | 10.63 | 0.00 |
| FAT_LOSS / 6 / FULL_GYM / 60 | 11 | 62.67 | 13.37 | 24.73 | 13.36 | 11.20 | 0.00 |
| FAT_LOSS / 6 / BODYWEIGHT_ONLY / 60 | 11 | 63.48 | 13.43 | 24.97 | 15.91 | 9.18 | 0.00 |
| FAT_LOSS / 6 / CALISTHENICS_PARK / 60 | 11 | 63.91 | 13.38 | 25.00 | 14.91 | 10.63 | 0.00 |
| FAT_LOSS / 6 / CALISTHENICS_PARK / 45 | 11 | 63.96 | 15.01 | 25.00 | 14.36 | 9.58 | 0.00 |
| FAT_LOSS / 6 / HOME_GYM / 45 | 11 | 64.01 | 15.00 | 24.73 | 13.36 | 10.91 | 0.00 |
| FAT_LOSS / 6 / HOME_GYM / 60 | 11 | 64.04 | 13.30 | 24.92 | 13.73 | 12.10 | 0.00 |

## Three days x 20 minutes: every equipment preset

| Group | N | Total /100 | Efficiency /25 | Coverage /25 | Balance /20 | Quality /15 | Pairing /15 |
|---|---:|---:|---:|---:|---:|---:|---:|
| FULL_GYM | 55 | 83.71 | 19.91 | 18.12 | 20.00 | 12.86 | 12.82 |
| HOME_GYM | 55 | 83.03 | 19.90 | 17.80 | 20.00 | 12.70 | 12.64 |
| DUMBBELLS_AND_BAR | 55 | 83.17 | 20.25 | 18.63 | 20.00 | 11.12 | 13.18 |
| DUMBBELLS_AND_KETTLEBELL | 55 | 83.06 | 20.17 | 18.53 | 20.00 | 11.36 | 13.00 |
| CALISTHENICS_PARK | 55 | 82.44 | 20.57 | 18.55 | 20.00 | 9.86 | 13.45 |
| PULL_UP_BAR_ONLY | 55 | 82.10 | 20.61 | 18.48 | 20.00 | 9.38 | 13.64 |
| BODYWEIGHT_ONLY | 55 | 80.65 | 20.64 | 18.26 | 20.00 | 8.56 | 13.18 |

BODYWEIGHT_ONLY = no gear; HOME_GYM and both DUMBBELLS presets cover home/dumbbell setups; FULL_GYM = full gym. All remaining presets are shown separately.

### Safe-pair opportunity for three 20-minute sessions

Means across 55 sampled cases per preset. Savings are weekly (divide by 3 for a session). Zero transition rest; preserve recovery before repeating a lift. Disjoint greedy pairing saves at most 40 seconds per matched round. Extra sets are whole pairs fitting both the saved time and 20-minute limit, capped at daily-primary 10 and weekly MRV. Existing idle time earns no extra-set credit. Fixed 40-second sets and no setup/fatigue cost: theoretical capacity, not a prescription.

| Preset | Mean existing weekly sets | Mean saved min/week | Mean extra sets/week | Extra sets range |
|---|---:|---:|---:|---:|
| FULL_GYM | 20.33 | 2.23 | 0.00 | 0-0 |
| HOME_GYM | 20.07 | 2.19 | 0.00 | 0-0 |
| DUMBBELLS_AND_BAR | 20.29 | 3.15 | 0.00 | 0-0 |
| DUMBBELLS_AND_KETTLEBELL | 20.31 | 2.55 | 0.00 | 0-0 |
| CALISTHENICS_PARK | 20.07 | 3.10 | 0.00 | 0-0 |
| PULL_UP_BAR_ONLY | 20.33 | 3.27 | 0.00 | 0-0 |
| BODYWEIGHT_ONLY | 20.07 | 3.22 | 0.00 | 0-0 |

## Three lowest-scoring concrete plans

### FAT_LOSS|6|BODYWEIGHT_ONLY|NEW|TRICEPS|20

Score 41.88/100; sub-scores {"efficiency":17.38,"coverage":17.5,"balance":0,"quality":7,"pairing":0}.

- Day 1 -> Jumping jacks (jumping_jacks) 2 x 10-15 reps [Warm-up; rest 30s]; Calf raise (calf_raise) 2 x 10-15 reps [Warm-up; rest 30s]; Bodyweight squat (squat) 2 x 12-15 reps [Main; rest 120s; circuit]; Incline push-up (incline_push_up) 2 x 12-15 reps [Main; rest 120s; circuit]; Inverted Row with Straps (inverted_row_with_straps) 2 x 12-15 reps [Main; rest 120s; circuit]; Body Tricep Press (body_tricep_press) 2 x 12-15 reps [Main; rest 60s; circuit]
- Day 2 -> Jumping jacks (jumping_jacks) 2 x 10-15 reps [Warm-up; rest 30s]; Calf raise (calf_raise) 2 x 10-15 reps [Warm-up; rest 30s]; Bodyweight squat (squat) 2 x 12-15 reps [Main; rest 120s; circuit]; Knee push-up (knee_push_up) 2 x 12-15 reps [Main; rest 120s; circuit]; Inverted Row with Straps (inverted_row_with_straps) 2 x 12-15 reps [Main; rest 120s; circuit]; Standing Towel Triceps Extension (standing_towel_triceps_extension) 2 x 12-15 reps [Main; rest 60s; circuit]
- Day 3 -> Jumping jacks (jumping_jacks) 2 x 10-15 reps [Warm-up; rest 30s]; Calf raise (calf_raise) 2 x 10-15 reps [Warm-up; rest 30s]; Bodyweight squat (squat) 2 x 12-15 reps [Main; rest 120s; circuit]; Incline push-up (incline_push_up) 2 x 12-15 reps [Main; rest 120s; circuit]; Inverted Row with Straps (inverted_row_with_straps) 2 x 12-15 reps [Main; rest 120s; circuit]; Body Tricep Press (body_tricep_press) 2 x 12-15 reps [Main; rest 60s; circuit]
- Day 4 -> Jumping jacks (jumping_jacks) 2 x 10-15 reps [Warm-up; rest 30s]; Calf raise (calf_raise) 2 x 10-15 reps [Warm-up; rest 30s]; Bodyweight squat (squat) 2 x 12-15 reps [Main; rest 120s; circuit]; Incline push-up (incline_push_up) 2 x 12-15 reps [Main; rest 120s; circuit]; Inverted Row with Straps (inverted_row_with_straps) 2 x 12-15 reps [Main; rest 120s; circuit]; Body Tricep Press (body_tricep_press) 2 x 12-15 reps [Main; rest 60s; circuit]
- Day 5 -> Jumping jacks (jumping_jacks) 2 x 10-15 reps [Warm-up; rest 30s]; Calf raise (calf_raise) 2 x 10-15 reps [Warm-up; rest 30s]; Bodyweight squat (squat) 2 x 12-15 reps [Main; rest 120s; circuit]; Incline push-up (incline_push_up) 2 x 12-15 reps [Main; rest 120s; circuit]; Inverted Row with Straps (inverted_row_with_straps) 2 x 12-15 reps [Main; rest 120s; circuit]; Body Tricep Press (body_tricep_press) 2 x 12-15 reps [Main; rest 60s; circuit]
- Day 6 -> Jumping jacks (jumping_jacks) 2 x 10-15 reps [Warm-up; rest 30s]; Calf raise (calf_raise) 2 x 10-15 reps [Warm-up; rest 30s]; Bodyweight squat (squat) 2 x 12-15 reps [Main; rest 120s; circuit]; Incline push-up (incline_push_up) 2 x 12-15 reps [Main; rest 120s; circuit]; Inverted Row with Straps (inverted_row_with_straps) 2 x 12-15 reps [Main; rest 120s; circuit]; Body Tricep Press (body_tricep_press) 2 x 12-15 reps [Main; rest 60s; circuit]

Reasons / diagnostics:

- HAMSTRINGS: 0/4.5 credited sets (missing)
- CALVES: 0/6 credited sets (missing)
- ABS: 0/3 credited sets (missing)
- Efficiency: 36 main sets / 54 capacity; 150 min including 30 extra-block min; 72.41% work/rest time resting; 0 duplicate abs/calf sets
- Quality: 6/6 compound-first days; 24/48 staple sets; 24 odd sets; 3 unexplained repeats
- Plan check SHORT_PLAN: 6/2
- Recovery: 20 sub-threshold consecutive muscle overlaps; 0 spinal lifts beyond two/day
- Pairing: 6 unsafe circuit/superset pairs

### FAT_LOSS|6|PULL_UP_BAR_ONLY|NEW|SHOULDERS|20

Score 45.87/100; sub-scores {"efficiency":20.05,"coverage":19.32,"balance":2,"quality":4.5,"pairing":0}.

- Day 1 -> Jumping jacks (jumping_jacks) 2 x 10-15 reps [Warm-up; rest 30s]; Calf raise (calf_raise) 2 x 10-15 reps [Warm-up; rest 30s]; Bodyweight squat (squat) 2 x 12-15 reps [Main; rest 120s; circuit]; Knee scapular push-up (knee_scapular_push_up) 2 x 12-15 reps [Main; rest 120s; circuit]; Inverted Row with Straps (inverted_row_with_straps) 2 x 12-15 reps [Main; rest 120s; circuit]; Battle ropes (battle_ropes) 2 x 20s [Main; rest 60s; circuit]
- Day 2 -> Jumping jacks (jumping_jacks) 2 x 10-15 reps [Warm-up; rest 30s]; Calf raise (calf_raise) 2 x 10-15 reps [Warm-up; rest 30s]; Bodyweight squat (squat) 2 x 12-15 reps [Main; rest 120s; circuit]; Return Push from Stance (return_push_from_stance) 2 x 12-15 reps [Main; rest 120s; circuit]; Suspended Row (suspended_row) 2 x 12-15 reps [Main; rest 120s; circuit]; Kneeling Arm Drill (kneeling_arm_drill) 2 x 12-15 reps [Main; rest 60s; circuit]
- Day 3 -> Jumping jacks (jumping_jacks) 2 x 10-15 reps [Warm-up; rest 30s]; Calf raise (calf_raise) 2 x 10-15 reps [Warm-up; rest 30s]; Bodyweight squat (squat) 2 x 12-15 reps [Main; rest 120s; circuit]; Knee scapular push-up (knee_scapular_push_up) 2 x 12-15 reps [Main; rest 120s; circuit]; Inverted Row with Straps (inverted_row_with_straps) 2 x 12-15 reps [Main; rest 120s; circuit]; Battle ropes (battle_ropes) 2 x 20s [Main; rest 60s; circuit]
- Day 4 -> Jumping jacks (jumping_jacks) 2 x 10-15 reps [Warm-up; rest 30s]; Calf raise (calf_raise) 2 x 10-15 reps [Warm-up; rest 30s]; Bodyweight squat (squat) 2 x 12-15 reps [Main; rest 120s; circuit]; Knee scapular push-up (knee_scapular_push_up) 2 x 12-15 reps [Main; rest 120s; circuit]; Inverted Row with Straps (inverted_row_with_straps) 2 x 12-15 reps [Main; rest 120s; circuit]; Battle ropes (battle_ropes) 2 x 20s [Main; rest 60s; circuit]
- Day 5 -> Jumping jacks (jumping_jacks) 2 x 10-15 reps [Warm-up; rest 30s]; Calf raise (calf_raise) 2 x 10-15 reps [Warm-up; rest 30s]; Bodyweight squat (squat) 2 x 12-15 reps [Main; rest 120s; circuit]; Knee scapular push-up (knee_scapular_push_up) 2 x 12-15 reps [Main; rest 120s; circuit]; Inverted Row with Straps (inverted_row_with_straps) 2 x 12-15 reps [Main; rest 120s; circuit]; Battle ropes (battle_ropes) 2 x 20s [Main; rest 60s; circuit]
- Day 6 -> Jumping jacks (jumping_jacks) 2 x 10-15 reps [Warm-up; rest 30s]; Calf raise (calf_raise) 2 x 10-15 reps [Warm-up; rest 30s]; Bodyweight squat (squat) 2 x 12-15 reps [Main; rest 120s; circuit]; Knee scapular push-up (knee_scapular_push_up) 2 x 12-15 reps [Main; rest 120s; circuit]; Inverted Row with Straps (inverted_row_with_straps) 2 x 12-15 reps [Main; rest 120s; circuit]; Battle ropes (battle_ropes) 2 x 20s [Main; rest 60s; circuit]

Reasons / diagnostics:

- TRICEPS: 1/4.5 credited sets
- HAMSTRINGS: 0/4.5 credited sets (missing)
- CALVES: 0/6 credited sets (missing)
- Efficiency: 48 main sets / 54 capacity; 150 min including 30 extra-block min; 72.41% work/rest time resting; 0 duplicate abs/calf sets
- Quality: 6/6 compound-first days; 12/48 staple sets; 36 odd sets; 3 unexplained repeats
- Plan check SHORT_PLAN: 6/2
- Recovery: 18 sub-threshold consecutive muscle overlaps; 0 spinal lifts beyond two/day
- Pairing: 6 unsafe circuit/superset pairs

### FAT_LOSS|6|CALISTHENICS_PARK|SEASONED|ABS|20

Score 46.88/100; sub-scores {"efficiency":17.38,"coverage":25,"balance":0,"quality":4.5,"pairing":0}.

- Day 1 -> Jumping jacks (jumping_jacks) 2 x 10-15 reps [Warm-up; rest 30s]; Calf raise (calf_raise) 2 x 10-15 reps [Warm-up; rest 30s]; Bodyweight jump squat (jump_squat) 2 x 12-15 reps [Main; rest 120s; circuit]; Archer push-up (archer_push_up) 2 x 12-15 reps [Main; rest 120s; circuit]; Tuck front lever row (front_lever_row) 2 x 12-15 reps [Main; rest 120s; circuit]; Hanging leg raise (hanging_leg_raise) 2 x 12-15 reps [Main; rest 60s; circuit]
- Day 2 -> Jumping jacks (jumping_jacks) 2 x 10-15 reps [Warm-up; rest 30s]; Calf raise (calf_raise) 2 x 10-15 reps [Warm-up; rest 30s]; Weighted Squat (weighted_squat) 2 x 12-15 reps [Main; rest 120s; circuit]; Pseudo planche push-up (pseudo_planche_push_up) 2 x 12-15 reps [Main; rest 120s; circuit]; Tuck front lever (tuck_front_lever) 2 x 45s [Main; rest 120s; circuit]; Hanging Pike (hanging_pike) 2 x 12-15 reps [Main; rest 60s; circuit]
- Day 3 -> Jumping jacks (jumping_jacks) 2 x 10-15 reps [Warm-up; rest 30s]; Calf raise (calf_raise) 2 x 10-15 reps [Warm-up; rest 30s]; Bodyweight jump squat (jump_squat) 2 x 12-15 reps [Main; rest 120s; circuit]; Tuck planche (tuck_planche) 2 x 45s [Main; rest 120s; circuit]; Tuck front lever row (front_lever_row) 2 x 12-15 reps [Main; rest 120s; circuit]; Hanging leg raise (hanging_leg_raise) 2 x 12-15 reps [Main; rest 60s; circuit]
- Day 4 -> Jumping jacks (jumping_jacks) 2 x 10-15 reps [Warm-up; rest 30s]; Calf raise (calf_raise) 2 x 10-15 reps [Warm-up; rest 30s]; Bodyweight jump squat (jump_squat) 2 x 12-15 reps [Main; rest 120s; circuit]; Archer push-up (archer_push_up) 2 x 12-15 reps [Main; rest 120s; circuit]; Tuck front lever row (front_lever_row) 2 x 12-15 reps [Main; rest 120s; circuit]; Hanging leg raise (hanging_leg_raise) 2 x 12-15 reps [Main; rest 60s; circuit]
- Day 5 -> Jumping jacks (jumping_jacks) 2 x 10-15 reps [Warm-up; rest 30s]; Calf raise (calf_raise) 2 x 10-15 reps [Warm-up; rest 30s]; Bodyweight jump squat (jump_squat) 2 x 12-15 reps [Main; rest 120s; circuit]; Archer push-up (archer_push_up) 2 x 12-15 reps [Main; rest 120s; circuit]; Tuck front lever row (front_lever_row) 2 x 12-15 reps [Main; rest 120s; circuit]; Hanging leg raise (hanging_leg_raise) 2 x 12-15 reps [Main; rest 60s; circuit]
- Day 6 -> Jumping jacks (jumping_jacks) 2 x 10-15 reps [Warm-up; rest 30s]; Calf raise (calf_raise) 2 x 10-15 reps [Warm-up; rest 30s]; Bodyweight jump squat (jump_squat) 2 x 12-15 reps [Main; rest 120s; circuit]; Archer push-up (archer_push_up) 2 x 12-15 reps [Main; rest 120s; circuit]; Tuck front lever row (front_lever_row) 2 x 12-15 reps [Main; rest 120s; circuit]; Hanging leg raise (hanging_leg_raise) 2 x 12-15 reps [Main; rest 60s; circuit]

Reasons / diagnostics:

- Efficiency: 36 main sets / 54 capacity; 150 min including 30 extra-block min; 72.41% work/rest time resting; 0 duplicate abs/calf sets
- Quality: 6/6 compound-first days; 0/48 staple sets; 24 odd sets; 3 unexplained repeats
- Recovery: 22 sub-threshold consecutive muscle overlaps; 0 spinal lifts beyond two/day
- Pairing: 12 unsafe circuit/superset pairs

## Verification and limits

Command: `cd web && npx vitest run src/domain/plan-grader.dev.test.ts`. Unit checks cover purity/determinism, missing coverage, extra-block exclusion, unsafe circuits, spinal exclusion, and exact pairing capacity. Matrix asserts bounded finite totals and all 700 coarse groups.

Gemini Gate 1 and Gate 3 did not run: agy-read writes scratch/log files outside the three authorized paths. Caller must run these reviews. Sampling may miss worse unsampled plans. Spinal/odd-pick classification is a catalog heuristic; equipment transitions, loads and effort cannot be inferred.
