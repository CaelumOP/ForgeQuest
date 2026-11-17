# Near-duplicate free-exercise-db entries merged into existing or newly imported exercises.
# Format: fedb_id -> target_forgequest_id
# The merged entry's English name is automatically appended to the target exercise's aliases.

WP1C_MERGES: dict[str, str] = {
    # Merged into existing WP1b built-ins (same movement + same equipment, trivial grip/stance/arm variant)
    "Alternate_Incline_Dumbbell_Curl": "incline_db_curl",
    "Alternating_Cable_Shoulder_Press": "cable_shoulder_press",
    "Barbell_Ab_Rollout": "barbell_ab_rollout",
    "Barbell_Squat": "back_squat",
    "Bench_Press_-_Powerlifting": "bench_press",
    "Close-Grip_Standing_Barbell_Curl": "barbell_curl",
    "Dumbbell_Alternate_Bicep_Curl": "bicep_curl",
    "Incline_Push-Up_Close-Grip": "incline_push_up",
    "Incline_Push-Up_Reverse_Grip": "incline_push_up",
    "Kneeling_Single-Arm_High_Pulley_Row": "kneeling_high_pulley_row",
    "One_Arm_Dumbbell_Bench_Press": "db_bench_press",
    "One_Arm_Pronated_Dumbbell_Triceps_Extension": "single_arm_overhead_db_tricep",
    "One_Arm_Supinated_Dumbbell_Triceps_Extension": "single_arm_overhead_db_tricep",
    "Quad_Stretch": "quad_stretch",
    "Seated_Cable_Shoulder_Press": "cable_shoulder_press",
    "Smith_Machine_Decline_Press": "smith_decline_press",
    "Smith_Machine_One-Arm_Upright_Row": "smith_upright_row",
    "Standing_Cable_Chest_Press": "cable_chest_press",
    "Standing_One-Arm_Cable_Curl": "cable_curl",
    "Two-Arm_Dumbbell_Preacher_Curl": "db_preacher_curl",
    "Two-Arm_Kettlebell_Clean": "kb_clean",
    "Two-Arm_Kettlebell_Row": "kb_row",
    "Wide-Grip_Decline_Barbell_Bench_Press": "decline_bench_press",

    # Merged among newly imported WP1c exercises (keep one canonical, alias the other)
    "Dumbbell_Lying_One-Arm_Rear_Lateral_Raise": "dumbbell_lying_rear_lateral_raise",
    "One-Arm_Kettlebell_Jerk": "two_arm_kettlebell_jerk",
    "One-Arm_Open_Palm_Kettlebell_Clean": "open_palm_kettlebell_clean",
    "Seated_Bent-Over_One-Arm_Dumbbell_Triceps_Extension": "seated_bent_over_two_arm_dumbbell_triceps_extension",
    "Seated_One-Arm_Dumbbell_Palms-Down_Wrist_Curl": "seated_dumbbell_palms_down_wrist_curl",
    "Seated_One-Arm_Dumbbell_Palms-Up_Wrist_Curl": "seated_dumbbell_palms_up_wrist_curl",
    "Standing_Bent-Over_One-Arm_Dumbbell_Triceps_Extension": "standing_bent_over_two_arm_dumbbell_triceps_extension",
    "Supine_One-Arm_Overhead_Throw": "supine_two_arm_overhead_throw",
}

SKIPPED_CATEGORIES: set[str] = {"strongman", "olympic weightlifting"}
