# Dev-only plan quality scorecard

Deterministic sample: 7700 / 23,100 cases; all 700 goal x days (2-6) x preset x minute groups, 11 cases/group. All 11 focuses in each group; experience = (group index + focus index) mod 3. Experience counts rotate 4/4/3. These are sample means, not exhaustive population results.

Overall mean **87.85/100**; minimum **68.57/100**. Matrix generation/grading: 63.60 seconds.

Formula source: web/src/domain/plan-grader.ts header. Scores are heuristic comparisons, not measured outcomes. Goal targets are explicit rubric assumptions; builder targets are experience-based. Missing muscles dominate coverage. Duration uses auditor ceil(work seconds/60) plus 5 minutes per warm-up/cool-down block; overhead above 20% is penalized.

Pairing checks catalog muscle overlap and named spinal-loading lifts; it cannot establish individual safety. FAT_LOSS items can be circuits despite absent explicit supersets. Same-block circuits are checked pairwise and excluded from additional pairing savings. Duration remains the auditor estimate for comparability.

## Mean by minutes

| Group | N | Total /100 | Efficiency /25 | Coverage /25 | Balance /20 | Quality /15 | Pairing /15 |
|---|---:|---:|---:|---:|---:|---:|---:|
| 20 minutes | 1925 | 89.97 | 21.72 | 23.76 | 18.37 | 11.13 | 15.00 |
| 30 minutes | 1925 | 89.51 | 21.11 | 24.38 | 18.17 | 10.86 | 15.00 |
| 45 minutes | 1925 | 86.54 | 18.49 | 24.17 | 18.04 | 10.84 | 15.00 |
| 60 minutes | 1925 | 85.40 | 16.92 | 24.58 | 18.04 | 10.86 | 15.00 |

## Mean by days

| Group | N | Total /100 | Efficiency /25 | Coverage /25 | Balance /20 | Quality /15 | Pairing /15 |
|---|---:|---:|---:|---:|---:|---:|---:|
| 2 days | 1540 | 90.04 | 20.84 | 23.21 | 19.96 | 11.03 | 15.00 |
| 3 days | 1540 | 90.26 | 20.46 | 24.01 | 19.92 | 10.87 | 15.00 |
| 4 days | 1540 | 88.49 | 19.52 | 24.55 | 18.35 | 11.07 | 15.00 |
| 5 days | 1540 | 86.77 | 18.72 | 24.63 | 17.52 | 10.90 | 15.00 |
| 6 days | 1540 | 83.71 | 18.25 | 24.71 | 15.02 | 10.72 | 15.00 |

## 15 worst groups (goal | days | preset | minutes)

| Group | N | Total /100 | Efficiency /25 | Coverage /25 | Balance /20 | Quality /15 | Pairing /15 |
|---|---:|---:|---:|---:|---:|---:|---:|
| ENDURANCE / 6 / CALISTHENICS_PARK / 60 | 11 | 77.42 | 13.47 | 25.00 | 14.36 | 9.58 | 15.00 |
| ENDURANCE / 6 / HOME_GYM / 60 | 11 | 77.50 | 13.49 | 24.73 | 13.36 | 10.92 | 15.00 |
| ENDURANCE / 6 / BODYWEIGHT_ONLY / 60 | 11 | 78.00 | 13.61 | 25.00 | 15.18 | 9.21 | 15.00 |
| ENDURANCE / 6 / FULL_GYM / 60 | 11 | 78.21 | 13.26 | 24.71 | 13.73 | 11.51 | 15.00 |
| ENDURANCE / 6 / DUMBBELLS_AND_KETTLEBELL / 60 | 11 | 78.26 | 13.13 | 24.94 | 13.91 | 11.28 | 15.00 |
| ENDURANCE / 6 / DUMBBELLS_AND_BAR / 60 | 11 | 78.44 | 13.70 | 24.95 | 13.27 | 11.53 | 15.00 |
| MUSCLE / 6 / HOME_GYM / 60 | 11 | 78.48 | 15.84 | 24.51 | 13.18 | 9.94 | 15.00 |
| ENDURANCE / 6 / CALISTHENICS_PARK / 45 | 11 | 78.48 | 15.17 | 25.00 | 13.82 | 9.50 | 15.00 |
| GENERAL / 6 / CALISTHENICS_PARK / 60 | 11 | 78.55 | 15.25 | 24.99 | 13.82 | 9.50 | 15.00 |
| FAT_LOSS / 6 / BODYWEIGHT_ONLY / 60 | 11 | 78.60 | 13.10 | 24.77 | 16.55 | 9.17 | 15.00 |
| ENDURANCE / 6 / PULL_UP_BAR_ONLY / 60 | 11 | 78.64 | 13.45 | 25.00 | 15.09 | 10.10 | 15.00 |
| ENDURANCE / 6 / PULL_UP_BAR_ONLY / 45 | 11 | 78.78 | 14.96 | 24.97 | 14.36 | 9.48 | 15.00 |
| GENERAL / 6 / PULL_UP_BAR_ONLY / 60 | 11 | 78.83 | 15.06 | 24.92 | 14.36 | 9.48 | 15.00 |
| MUSCLE / 6 / DUMBBELLS_AND_BAR / 60 | 11 | 79.19 | 16.25 | 24.59 | 13.09 | 10.27 | 15.00 |
| GENERAL / 6 / DUMBBELLS_AND_BAR / 60 | 11 | 79.20 | 15.36 | 24.84 | 13.55 | 10.47 | 15.00 |

## Fat loss 6 days: every preset x minutes

| Group | N | Total /100 | Efficiency /25 | Coverage /25 | Balance /20 | Quality /15 | Pairing /15 |
|---|---:|---:|---:|---:|---:|---:|---:|
| FULL_GYM / 20 | 11 | 88.95 | 19.76 | 24.99 | 16.91 | 12.29 | 15.00 |
| FULL_GYM / 30 | 11 | 85.49 | 17.17 | 24.75 | 16.18 | 12.39 | 15.00 |
| FULL_GYM / 45 | 11 | 82.31 | 13.94 | 24.76 | 16.00 | 12.61 | 15.00 |
| FULL_GYM / 60 | 11 | 82.27 | 12.93 | 24.99 | 17.00 | 12.35 | 15.00 |
| HOME_GYM / 20 | 11 | 88.24 | 19.91 | 24.73 | 16.45 | 12.13 | 15.00 |
| HOME_GYM / 30 | 11 | 85.47 | 16.70 | 24.93 | 16.55 | 12.29 | 15.00 |
| HOME_GYM / 45 | 11 | 83.89 | 14.14 | 25.00 | 17.64 | 12.11 | 15.00 |
| HOME_GYM / 60 | 11 | 81.35 | 13.01 | 24.75 | 16.45 | 12.14 | 15.00 |
| DUMBBELLS_AND_BAR / 20 | 11 | 86.77 | 20.25 | 24.92 | 15.09 | 11.51 | 15.00 |
| DUMBBELLS_AND_BAR / 30 | 11 | 84.38 | 17.21 | 24.95 | 15.64 | 11.59 | 15.00 |
| DUMBBELLS_AND_BAR / 45 | 11 | 82.20 | 14.25 | 24.99 | 16.00 | 11.97 | 15.00 |
| DUMBBELLS_AND_BAR / 60 | 11 | 79.43 | 12.97 | 24.92 | 15.09 | 11.45 | 15.00 |
| DUMBBELLS_AND_KETTLEBELL / 20 | 11 | 87.74 | 20.04 | 24.93 | 16.82 | 10.96 | 15.00 |
| DUMBBELLS_AND_KETTLEBELL / 30 | 11 | 84.43 | 17.16 | 25.00 | 15.91 | 11.37 | 15.00 |
| DUMBBELLS_AND_KETTLEBELL / 45 | 11 | 81.26 | 14.14 | 24.98 | 16.09 | 11.05 | 15.00 |
| DUMBBELLS_AND_KETTLEBELL / 60 | 11 | 80.62 | 12.95 | 24.93 | 16.82 | 10.92 | 15.00 |
| CALISTHENICS_PARK / 20 | 11 | 88.44 | 20.79 | 25.00 | 16.55 | 11.11 | 15.00 |
| CALISTHENICS_PARK / 30 | 11 | 83.96 | 17.38 | 24.96 | 16.09 | 10.53 | 15.00 |
| CALISTHENICS_PARK / 45 | 11 | 80.85 | 14.20 | 25.00 | 16.36 | 10.29 | 15.00 |
| CALISTHENICS_PARK / 60 | 11 | 80.85 | 13.11 | 25.00 | 16.55 | 11.20 | 15.00 |
| PULL_UP_BAR_ONLY / 20 | 11 | 86.68 | 20.54 | 24.97 | 15.82 | 10.35 | 15.00 |
| PULL_UP_BAR_ONLY / 30 | 11 | 83.55 | 17.08 | 24.77 | 16.36 | 10.34 | 15.00 |
| PULL_UP_BAR_ONLY / 45 | 11 | 81.60 | 14.39 | 25.00 | 16.55 | 10.67 | 15.00 |
| PULL_UP_BAR_ONLY / 60 | 11 | 79.36 | 13.13 | 24.96 | 15.82 | 10.45 | 15.00 |
| BODYWEIGHT_ONLY / 20 | 11 | 86.01 | 20.46 | 24.77 | 16.55 | 9.24 | 15.00 |
| BODYWEIGHT_ONLY / 30 | 11 | 83.93 | 17.57 | 24.96 | 16.82 | 9.58 | 15.00 |
| BODYWEIGHT_ONLY / 45 | 11 | 80.50 | 14.58 | 24.96 | 16.91 | 9.05 | 15.00 |
| BODYWEIGHT_ONLY / 60 | 11 | 78.60 | 13.10 | 24.77 | 16.55 | 9.17 | 15.00 |

## Three days x 20 minutes: every equipment preset

| Group | N | Total /100 | Efficiency /25 | Coverage /25 | Balance /20 | Quality /15 | Pairing /15 |
|---|---:|---:|---:|---:|---:|---:|---:|
| FULL_GYM | 55 | 91.34 | 21.57 | 22.24 | 20.00 | 12.53 | 15.00 |
| HOME_GYM | 55 | 91.38 | 21.83 | 22.25 | 19.96 | 12.33 | 15.00 |
| DUMBBELLS_AND_BAR | 55 | 91.80 | 21.91 | 23.52 | 20.00 | 11.37 | 15.00 |
| DUMBBELLS_AND_KETTLEBELL | 55 | 91.00 | 21.86 | 23.11 | 20.00 | 11.02 | 15.00 |
| CALISTHENICS_PARK | 55 | 91.25 | 22.01 | 23.81 | 20.00 | 10.43 | 15.00 |
| PULL_UP_BAR_ONLY | 55 | 90.65 | 22.02 | 23.77 | 20.00 | 9.85 | 15.00 |
| BODYWEIGHT_ONLY | 55 | 89.50 | 21.94 | 24.00 | 20.00 | 8.56 | 15.00 |

BODYWEIGHT_ONLY = no gear; HOME_GYM and both DUMBBELLS presets cover home/dumbbell setups; FULL_GYM = full gym. All remaining presets are shown separately.

### Safe-pair opportunity for three 20-minute sessions

Means across 55 sampled cases per preset. Savings are weekly (divide by 3 for a session). Zero transition rest; preserve recovery before repeating a lift. Disjoint greedy pairing saves at most 40 seconds per matched round. Extra sets are whole pairs fitting both the saved time and 20-minute limit, capped at daily-primary 10 and weekly MRV. Existing idle time earns no extra-set credit. Fixed 40-second sets and no setup/fatigue cost: theoretical capacity, not a prescription.

| Preset | Mean existing weekly sets | Mean saved min/week | Mean extra sets/week | Extra sets range |
|---|---:|---:|---:|---:|
| FULL_GYM | 39.75 | 0.65 | 0.00 | 0-0 |
| HOME_GYM | 39.29 | 0.85 | 0.00 | 0-0 |
| DUMBBELLS_AND_BAR | 50.29 | 0.88 | 0.00 | 0-0 |
| DUMBBELLS_AND_KETTLEBELL | 43.51 | 0.90 | 0.00 | 0-0 |
| CALISTHENICS_PARK | 48.60 | 0.70 | 0.00 | 0-0 |
| PULL_UP_BAR_ONLY | 50.00 | 0.62 | 0.00 | 0-0 |
| BODYWEIGHT_ONLY | 50.69 | 0.68 | 0.00 | 0-0 |

## Three lowest-scoring concrete plans

### ENDURANCE|6|FULL_GYM|SEASONED|CHEST|60

Score 68.57/100; sub-scores {"efficiency":12.51,"coverage":25,"balance":9,"quality":7.06,"pairing":15}.

- Day 1 -> Jumping jacks (jumping_jacks) 2 x 10-15 reps [Warm-up; rest 30s]; Band pull-apart (band_pull_apart) 2 x 10-15 reps [Warm-up; rest 30s]; Archer push-up (archer_push_up) 2 x 15-20 reps [Main; rest 120s]; Handstand hold (handstand_hold) 2 x 45s [Main; rest 120s]; Weighted dip (weighted_dip) 2 x 15-20 reps [Main; rest 120s]; Tuck back lever (back_lever_tuck) 2 x 45s [Main; rest 60s]; Diamond push-up (diamond_push_up) 2 x 15-20 reps [Main; rest 60s]; Barbell ab rollout (barbell_ab_rollout) 2 x 15-20 reps [Main; rest 60s]
- Day 2 -> Jumping jacks (jumping_jacks) 2 x 10-15 reps [Warm-up; rest 30s]; Band pull-apart (band_pull_apart) 2 x 10-15 reps [Warm-up; rest 30s]; Muscle-up (muscle_up) 2 x 15-20 reps [Main; rest 120s]; Kettlebell renegade row (kb_renegade_row) 2 x 15-20 reps [Main; rest 120s]; Tuck back lever (back_lever_tuck) 2 x 45s [Main; rest 60s]; Barbell drag curl (drag_curl) 5 x 15-20 reps [Main; rest 60s]; L-sit (l_sit) 2 x 45s [Main; rest 60s]
- Day 3 -> Jumping jacks (jumping_jacks) 2 x 10-15 reps [Warm-up; rest 30s]; Half-rep squat (half_squat) 2 x 10-15 reps [Warm-up; rest 30s]; Barbell Squat To A Bench (barbell_squat_to_a_bench) 2 x 15-20 reps [Main; rest 120s]; Deadlift with Bands (deadlift_with_bands) 5 x 15-20 reps [Main; rest 120s]; Kettlebell pistol squat (kb_pistol_squat) 4 x 15-20 reps [Main; rest 120s]; Nordic hamstring curl (nordic_curl) 3 x 15-20 reps [Main; rest 60s]; Dumbbell calf raise (db_calf_raise) 2 x 15-20 reps [Main; rest 60s]; Barbell ab rollout (barbell_ab_rollout) 2 x 15-20 reps [Main; rest 60s]
- Day 4 -> Jumping jacks (jumping_jacks) 2 x 10-15 reps [Warm-up; rest 30s]; Band pull-apart (band_pull_apart) 2 x 10-15 reps [Warm-up; rest 30s]; Plyo Kettlebell Pushups (plyo_kettlebell_pushups) 2 x 15-20 reps [Main; rest 120s]; Handstand push-up (handstand_push_up) 2 x 15-20 reps [Main; rest 120s]; Weighted dip (weighted_dip) 2 x 15-20 reps [Main; rest 120s]; Pseudo planche push-up (pseudo_planche_push_up) 2 x 15-20 reps [Main; rest 120s]; Barbell ab rollout (barbell_ab_rollout) 2 x 15-20 reps [Main; rest 60s]
- Day 5 -> Jumping jacks (jumping_jacks) 2 x 10-15 reps [Warm-up; rest 30s]; Band pull-apart (band_pull_apart) 2 x 10-15 reps [Warm-up; rest 30s]; Muscle-up (muscle_up) 2 x 15-20 reps [Main; rest 120s]; Kettlebell renegade row (kb_renegade_row) 2 x 15-20 reps [Main; rest 120s]; Tuck back lever (back_lever_tuck) 2 x 45s [Main; rest 60s]; Dumbbell Lying Pronation (dumbbell_lying_pronation) 2 x 15-20 reps [Main; rest 60s]; Barbell ab rollout (barbell_ab_rollout) 2 x 15-20 reps [Main; rest 60s]
- Day 6 -> Jumping jacks (jumping_jacks) 2 x 10-15 reps [Warm-up; rest 30s]; Half-rep squat (half_squat) 2 x 10-15 reps [Warm-up; rest 30s]; Box Squat with Bands (box_squat_with_bands) 2 x 15-20 reps [Main; rest 120s]; Deadlift with Chains (deadlift_with_chains) 5 x 15-20 reps [Main; rest 120s]; Pistol squat (pistol_squat) 2 x 15-20 reps [Main; rest 120s]; Nordic hamstring curl (nordic_curl) 2 x 15-20 reps [Main; rest 60s]; Dumbbell calf raise (db_calf_raise) 2 x 15-20 reps [Main; rest 60s]; Barbell ab rollout (barbell_ab_rollout) 2 x 15-20 reps [Main; rest 60s]

Reasons / diagnostics:

- Efficiency: 43 main sets / 198 capacity; 203 min including 30 extra-block min; 69.77% work/rest time resting; 4 duplicate abs/calf sets
- Quality: 6/6 compound-first days; 11/78 staple sets; 10 odd sets; 4 unexplained repeats
- Recovery: 11 sub-threshold consecutive muscle overlaps; 0 spinal lifts beyond two/day

### ENDURANCE|6|FULL_GYM|SEASONED|GLUTES|60

Score 69.12/100; sub-scores {"efficiency":12.75,"coverage":25,"balance":9,"quality":7.37,"pairing":15}.

- Day 1 -> Jumping jacks (jumping_jacks) 2 x 10-15 reps [Warm-up; rest 30s]; Band pull-apart (band_pull_apart) 2 x 10-15 reps [Warm-up; rest 30s]; Archer push-up (archer_push_up) 2 x 15-20 reps [Main; rest 120s]; Kettlebell snatch (kb_snatch) 2 x 15-20 reps [Main; rest 120s]; Weighted dip (weighted_dip) 4 x 15-20 reps [Main; rest 120s]; Dumbbell Lying Rear Lateral Raise (dumbbell_lying_rear_lateral_raise) 2 x 15-20 reps [Main; rest 60s]; Diamond push-up (diamond_push_up) 2 x 15-20 reps [Main; rest 60s]; Barbell ab rollout (barbell_ab_rollout) 2 x 15-20 reps [Main; rest 60s]
- Day 2 -> Jumping jacks (jumping_jacks) 2 x 10-15 reps [Warm-up; rest 30s]; Band pull-apart (band_pull_apart) 2 x 10-15 reps [Warm-up; rest 30s]; Archer pull-up (archer_pull_up) 5 x 15-20 reps [Main; rest 120s]; Tuck front lever row (front_lever_row) 5 x 15-20 reps [Main; rest 120s]; Tuck back lever (back_lever_tuck) 2 x 45s [Main; rest 60s]; Barbell drag curl (drag_curl) 5 x 15-20 reps [Main; rest 60s]; Face pull (face_pull) 2 x 15-20 reps [Main; rest 60s]; L-sit (l_sit) 4 x 45s [Main; rest 60s]
- Day 3 -> Jumping jacks (jumping_jacks) 2 x 10-15 reps [Warm-up; rest 30s]; Half-rep squat (half_squat) 2 x 10-15 reps [Warm-up; rest 30s]; Barbell Squat To A Bench (barbell_squat_to_a_bench) 2 x 15-20 reps [Main; rest 120s]; Deadlift with Bands (deadlift_with_bands) 2 x 15-20 reps [Main; rest 120s]; Kettlebell pistol squat (kb_pistol_squat) 2 x 15-20 reps [Main; rest 120s]; Pistol squat (pistol_squat) 2 x 15-20 reps [Main; rest 120s]; Dumbbell calf raise (db_calf_raise) 2 x 15-20 reps [Main; rest 60s]; Barbell ab rollout (barbell_ab_rollout) 2 x 15-20 reps [Main; rest 60s]
- Day 4 -> Jumping jacks (jumping_jacks) 2 x 10-15 reps [Warm-up; rest 30s]; Band pull-apart (band_pull_apart) 2 x 10-15 reps [Warm-up; rest 30s]; Bench Press with Chains (bench_press_with_chains) 2 x 15-20 reps [Main; rest 120s]; Kettlebell snatch (kb_snatch) 2 x 15-20 reps [Main; rest 120s]; Weighted dip (weighted_dip) 2 x 15-20 reps [Main; rest 120s]; Skull crusher (skull_crusher) 2 x 15-20 reps [Main; rest 60s]; Barbell ab rollout (barbell_ab_rollout) 2 x 15-20 reps [Main; rest 60s]
- Day 5 -> Jumping jacks (jumping_jacks) 2 x 10-15 reps [Warm-up; rest 30s]; Band pull-apart (band_pull_apart) 2 x 10-15 reps [Warm-up; rest 30s]; L-sit pull-up (l_sit_pull_up) 2 x 15-20 reps [Main; rest 120s]; Kettlebell renegade row (kb_renegade_row) 2 x 15-20 reps [Main; rest 120s]; Tuck back lever (back_lever_tuck) 2 x 45s [Main; rest 60s]; Dumbbell Lying Pronation (dumbbell_lying_pronation) 3 x 15-20 reps [Main; rest 60s]; Barbell ab rollout (barbell_ab_rollout) 2 x 15-20 reps [Main; rest 60s]
- Day 6 -> Jumping jacks (jumping_jacks) 2 x 10-15 reps [Warm-up; rest 30s]; Half-rep squat (half_squat) 2 x 10-15 reps [Warm-up; rest 30s]; Box Squat with Bands (box_squat_with_bands) 2 x 15-20 reps [Main; rest 120s]; Deadlift with Chains (deadlift_with_chains) 2 x 15-20 reps [Main; rest 120s]; Kettlebell pistol squat (kb_pistol_squat) 2 x 15-20 reps [Main; rest 120s]; Kettlebell snatch (kb_snatch) 2 x 15-20 reps [Main; rest 120s]; Dumbbell calf raise (db_calf_raise) 2 x 15-20 reps [Main; rest 60s]; Barbell ab rollout (barbell_ab_rollout) 2 x 15-20 reps [Main; rest 60s]

Reasons / diagnostics:

- Efficiency: 46 main sets / 198 capacity; 213 min including 30 extra-block min; 69.74% work/rest time resting; 4 duplicate abs/calf sets
- Quality: 6/6 compound-first days; 10/82 staple sets; 4 odd sets; 3 unexplained repeats
- Recovery: 11 sub-threshold consecutive muscle overlaps; 0 spinal lifts beyond two/day

### MUSCLE|6|HOME_GYM|SEASONED|GLUTES|60

Score 69.32/100; sub-scores {"efficiency":15,"coverage":24.83,"balance":7,"quality":7.49,"pairing":15}.

- Day 1 -> Jumping jacks (jumping_jacks) 2 x 10-15 reps [Warm-up; rest 30s]; Band pull-apart (band_pull_apart) 2 x 10-15 reps [Warm-up; rest 30s]; Bench Press with Chains (bench_press_with_chains) 3 x 8-12 reps [Main; rest 150s]; Kettlebell snatch (kb_snatch) 2 x 8-12 reps [Main; rest 150s]; Weighted dip (weighted_dip) 3 x 8-12 reps [Main; rest 150s]; Dumbbell Lying Rear Lateral Raise (dumbbell_lying_rear_lateral_raise) 2 x 10-15 reps [Main; rest 90s]; Skull crusher (skull_crusher) 2 x 10-15 reps [Main; rest 90s]; Barbell ab rollout (barbell_ab_rollout) 2 x 10-15 reps [Main; rest 90s]
- Day 2 -> Jumping jacks (jumping_jacks) 2 x 10-15 reps [Warm-up; rest 30s]; Band pull-apart (band_pull_apart) 2 x 10-15 reps [Warm-up; rest 30s]; Weighted chin-up (weighted_chin_up) 5 x 8-12 reps [Main; rest 150s]; Kettlebell renegade row (kb_renegade_row) 2 x 8-12 reps [Main; rest 150s]; Tuck back lever (back_lever_tuck) 2 x 45s [Main; rest 90s]; Barbell drag curl (drag_curl) 4 x 10-15 reps [Main; rest 90s]; L-sit (l_sit) 5 x 45s [Main; rest 90s]
- Day 3 -> Jumping jacks (jumping_jacks) 2 x 10-15 reps [Warm-up; rest 30s]; Half-rep squat (half_squat) 2 x 10-15 reps [Warm-up; rest 30s]; Barbell Squat To A Bench (barbell_squat_to_a_bench) 2 x 8-12 reps [Main; rest 150s]; Deadlift with Bands (deadlift_with_bands) 2 x 8-12 reps [Main; rest 150s]; Kettlebell pistol squat (kb_pistol_squat) 2 x 8-12 reps [Main; rest 150s]; Kettlebell snatch (kb_snatch) 2 x 8-12 reps [Main; rest 150s]; Dumbbell calf raise (db_calf_raise) 2 x 10-15 reps [Main; rest 90s]; Barbell ab rollout (barbell_ab_rollout) 2 x 10-15 reps [Main; rest 90s]
- Day 4 -> Jumping jacks (jumping_jacks) 2 x 10-15 reps [Warm-up; rest 30s]; Band pull-apart (band_pull_apart) 2 x 10-15 reps [Warm-up; rest 30s]; Plyo Kettlebell Pushups (plyo_kettlebell_pushups) 2 x 8-12 reps [Main; rest 150s]; Kettlebell snatch (kb_snatch) 2 x 8-12 reps [Main; rest 150s]; Weighted dip (weighted_dip) 2 x 8-12 reps [Main; rest 150s]; Skull crusher (skull_crusher) 2 x 10-15 reps [Main; rest 90s]; Barbell ab rollout (barbell_ab_rollout) 2 x 10-15 reps [Main; rest 90s]
- Day 5 -> Jumping jacks (jumping_jacks) 2 x 10-15 reps [Warm-up; rest 30s]; Band pull-apart (band_pull_apart) 2 x 10-15 reps [Warm-up; rest 30s]; Weighted pull-up (weighted_pull_up) 5 x 8-12 reps [Main; rest 150s]; Kettlebell renegade row (kb_renegade_row) 2 x 8-12 reps [Main; rest 150s]; Tuck back lever (back_lever_tuck) 2 x 45s [Main; rest 90s]; Dumbbell Lying Pronation (dumbbell_lying_pronation) 2 x 10-15 reps [Main; rest 90s]; Middle Back Shrug (middle_back_shrug) 2 x 10-15 reps [Main; rest 90s]; Barbell ab rollout (barbell_ab_rollout) 2 x 10-15 reps [Main; rest 90s]
- Day 6 -> Jumping jacks (jumping_jacks) 2 x 10-15 reps [Warm-up; rest 30s]; Half-rep squat (half_squat) 2 x 10-15 reps [Warm-up; rest 30s]; Box Squat with Bands (box_squat_with_bands) 2 x 8-12 reps [Main; rest 150s]; Deadlift with Chains (deadlift_with_chains) 2 x 8-12 reps [Main; rest 150s]; Kettlebell pistol squat (kb_pistol_squat) 2 x 8-12 reps [Main; rest 150s]; Kettlebell snatch (kb_snatch) 2 x 8-12 reps [Main; rest 150s]; Dumbbell calf raise (db_calf_raise) 2 x 10-15 reps [Main; rest 90s]; Barbell ab rollout (barbell_ab_rollout) 2 x 10-15 reps [Main; rest 90s]

Reasons / diagnostics:

- CALVES: 8/10 credited sets
- Efficiency: 45 main sets / 123.75 capacity; 251 min including 30 extra-block min; 75.4% work/rest time resting; 7 duplicate abs/calf sets
- Quality: 6/6 compound-first days; 8/81 staple sets; 0 odd sets; 3 unexplained repeats
- Recovery: 13 sub-threshold consecutive muscle overlaps; 0 spinal lifts beyond two/day

## Verification and limits

Command: `cd web && npx vitest run src/domain/plan-grader.dev.test.ts`. Unit checks cover purity/determinism, missing coverage, extra-block exclusion, unsafe circuits, spinal exclusion, and exact pairing capacity. Matrix asserts bounded finite totals and all 700 coarse groups.

Gemini Gate 1 and Gate 3 did not run: agy-read writes scratch/log files outside the three authorized paths. Caller must run these reviews. Sampling may miss worse unsampled plans. Spinal/odd-pick classification is a catalog heuristic; equipment transitions, loads and effort cannot be inferred.
