package com.forgequest.app.data.seed

import com.forgequest.app.data.db.ExerciseEntity
import com.forgequest.app.domain.ExerciseKind
import com.forgequest.app.domain.ExerciseKind.HOLD
import com.forgequest.app.domain.ExerciseKind.REPS
import com.forgequest.app.domain.ExerciseKind.WEIGHTED
import com.forgequest.app.domain.Muscle
import com.forgequest.app.domain.Muscle.ABS
import com.forgequest.app.domain.Muscle.ADDUCTORS
import com.forgequest.app.domain.Muscle.BICEPS
import com.forgequest.app.domain.Muscle.CALVES
import com.forgequest.app.domain.Muscle.CHEST
import com.forgequest.app.domain.Muscle.FOREARMS
import com.forgequest.app.domain.Muscle.GLUTES
import com.forgequest.app.domain.Muscle.HAMSTRINGS
import com.forgequest.app.domain.Muscle.LOWER_BACK
import com.forgequest.app.domain.Muscle.OBLIQUES
import com.forgequest.app.domain.Muscle.QUADS
import com.forgequest.app.domain.Muscle.SHOULDERS
import com.forgequest.app.domain.Muscle.TIBIALIS
import com.forgequest.app.domain.Muscle.TRAPS
import com.forgequest.app.domain.Muscle.TRICEPS
import com.forgequest.app.domain.Muscle.UPPER_BACK

/** Built-in exercise library: Hemo's foundation moves plus the most common calisthenics and gym lifts. */
object ExerciseCatalog {

    private fun ex(
        id: String,
        name: String,
        kind: ExerciseKind,
        primary: List<Muscle>,
        secondary: List<Muscle> = emptyList(),
        video: String? = null,
    ) = ExerciseEntity(id, name, kind, primary, secondary, video)

    private const val D1 = "--W5i-fWBvY"
    private const val D2 = "OZY8i8OTCYA"
    private const val D3 = "jgfyxLtte1g"
    private const val D4 = "PD6y0qK1pDk"
    private const val D5 = "JX3G94SyBeU"

    /** A link that opens Hemo's video at the moment the exercise is demonstrated. */
    fun hemo(videoId: String, seconds: Int) = "https://www.youtube.com/watch?v=$videoId&t=${seconds}s"

    /** Stretches and mobility holds: cool-down material only, never picked for a plan slot, a swap or a record. */
    private val stretches: List<ExerciseEntity> = listOf(
        ex("quad_stretch", "Standing quad stretch", HOLD, listOf(QUADS)),
        ex("hamstring_stretch", "Hamstring stretch", HOLD, listOf(HAMSTRINGS), listOf(CALVES)),
        ex("hip_flexor_stretch", "Kneeling hip flexor stretch", HOLD, listOf(QUADS), listOf(GLUTES)),
        ex("pigeon_stretch", "Pigeon stretch", HOLD, listOf(GLUTES)),
        ex("figure_four_stretch", "Figure-four stretch", HOLD, listOf(GLUTES)),
        ex("calf_stretch", "Wall calf stretch", HOLD, listOf(CALVES)),
        ex("childs_pose", "Child’s pose", HOLD, listOf(LOWER_BACK), listOf(UPPER_BACK, SHOULDERS)),
        ex("chest_doorway_stretch", "Doorway chest stretch", HOLD, listOf(CHEST), listOf(SHOULDERS)),
        ex("cross_body_shoulder_stretch", "Cross-body shoulder stretch", HOLD, listOf(SHOULDERS), listOf(UPPER_BACK)),
        ex("triceps_overhead_stretch", "Overhead triceps stretch", HOLD, listOf(TRICEPS)),
        ex("biceps_wall_stretch", "Wall biceps stretch", HOLD, listOf(BICEPS), listOf(CHEST)),
        ex("neck_side_stretch", "Neck side stretch", HOLD, listOf(TRAPS)),
        ex("thread_the_needle", "Thread the needle", HOLD, listOf(UPPER_BACK), listOf(SHOULDERS)),
        ex("butterfly_stretch", "Butterfly stretch", HOLD, listOf(ADDUCTORS)),
        ex("frog_stretch", "Frog stretch", HOLD, listOf(ADDUCTORS)),
        ex("wrist_flexor_stretch", "Wrist flexor stretch", HOLD, listOf(FOREARMS)),
        ex("seated_spinal_twist", "Seated spinal twist", HOLD, listOf(OBLIQUES), listOf(LOWER_BACK, GLUTES)),
        ex("lat_stretch", "Standing lat stretch", HOLD, listOf(UPPER_BACK), listOf(OBLIQUES)),
        ex("shin_stretch", "Kneeling shin stretch", HOLD, listOf(TIBIALIS)),
    )

    val STRETCH_IDS: Set<String> = stretches.mapTo(HashSet()) { it.id }

    val all: List<ExerciseEntity> = listOf(
        // Warm-up / conditioning
        ex("jumping_jacks", "Jumping jacks", REPS, listOf(CALVES), listOf(SHOULDERS), hemo(D1, 120)),
        ex("plank_shoulder_taps", "Plank shoulder taps", REPS, listOf(ABS), listOf(SHOULDERS, OBLIQUES)),
        ex("burpee", "Burpee", REPS, listOf(QUADS, CHEST), listOf(SHOULDERS, ABS)),

        // Hemo foundation: core
        ex("incline_hollow_plank", "Incline hollow plank", HOLD, listOf(ABS), listOf(SHOULDERS, CHEST), hemo(D1, 136)),
        ex("incline_reverse_plank", "Incline reverse plank", HOLD, listOf(GLUTES, LOWER_BACK), listOf(SHOULDERS, HAMSTRINGS), hemo(D3, 150)),
        ex("hip_bridge", "Hip bridge", REPS, listOf(GLUTES), listOf(HAMSTRINGS, LOWER_BACK), hemo(D1, 150)),
        ex("crunch", "Crunch", REPS, listOf(ABS), listOf(OBLIQUES), hemo(D1, 164)),
        ex("superman_hold", "Superman hold", HOLD, listOf(LOWER_BACK), listOf(GLUTES, TRAPS), hemo(D1, 182)),

        // Hemo foundation: upper body
        ex("knee_scapular_push_up", "Knee scapular push-up", REPS, listOf(SHOULDERS), listOf(CHEST, TRAPS), hemo(D1, 202)),
        ex("scapular_australian_pull_up", "Scapular Australian pull-up", REPS, listOf(UPPER_BACK, TRAPS), listOf(SHOULDERS), hemo(D1, 210)),
        ex("bent_leg_bench_dip", "Bent-leg bench dip", REPS, listOf(TRICEPS), listOf(CHEST, SHOULDERS), hemo(D1, 222)),
        ex("scapular_australian_chin_up", "Scapular Australian chin-up", REPS, listOf(UPPER_BACK, TRAPS), listOf(BICEPS), hemo(D1, 230)),
        ex("knee_push_up", "Knee push-up", REPS, listOf(CHEST), listOf(TRICEPS, SHOULDERS), hemo(D2, 134)),
        ex("australian_pull_up", "Australian pull-up", REPS, listOf(UPPER_BACK), listOf(BICEPS, TRAPS, FOREARMS), hemo(D2, 148)),
        ex("reverse_grip_bench_dip", "Reverse-grip bent-leg bench dip", REPS, listOf(TRICEPS), listOf(SHOULDERS, CHEST), hemo(D2, 158)),
        ex("australian_chin_up", "Australian chin-up", REPS, listOf(UPPER_BACK, BICEPS), listOf(FOREARMS), hemo(D2, 166)),
        ex("wide_knee_push_up", "Wide knee push-up", REPS, listOf(CHEST), listOf(SHOULDERS, TRICEPS), hemo(D4, 190)),
        ex("dead_hang", "Dead hang", HOLD, listOf(FOREARMS), listOf(UPPER_BACK, SHOULDERS), hemo(D4, 198)),
        ex("incline_diamond_knee_push_up", "Incline diamond knee push-up", REPS, listOf(TRICEPS), listOf(CHEST, SHOULDERS), hemo(D5, 136)),

        // Hemo foundation: legs
        ex("half_squat", "Half-rep squat", REPS, listOf(QUADS), listOf(GLUTES), hemo(D3, 192)),
        ex("half_wide_squat", "Half-rep wide squat", REPS, listOf(QUADS, ADDUCTORS), listOf(GLUTES), hemo(D3, 202)),
        ex("half_narrow_squat", "Half-rep narrow squat", REPS, listOf(QUADS), listOf(GLUTES), hemo(D3, 210)),
        ex("calf_raise_turned_out", "Calf raise, feet turned out", REPS, listOf(CALVES), video = hemo(D3, 234)),
        ex("calf_raise", "Calf raise", REPS, listOf(CALVES), video = hemo(D3, 242)),

        // Calisthenics basics
        ex("push_up", "Push-up", REPS, listOf(CHEST), listOf(TRICEPS, SHOULDERS, ABS)),
        ex("diamond_push_up", "Diamond push-up", REPS, listOf(TRICEPS), listOf(CHEST, SHOULDERS)),
        ex("pike_push_up", "Pike push-up", REPS, listOf(SHOULDERS), listOf(TRICEPS, TRAPS)),
        ex("pull_up", "Pull-up", REPS, listOf(UPPER_BACK), listOf(BICEPS, FOREARMS, TRAPS)),
        ex("chin_up", "Chin-up", REPS, listOf(UPPER_BACK, BICEPS), listOf(FOREARMS)),
        ex("negative_pull_up", "Negative pull-up", REPS, listOf(UPPER_BACK), listOf(BICEPS, FOREARMS)),
        ex("dip", "Parallel bar dip", REPS, listOf(TRICEPS, CHEST), listOf(SHOULDERS)),
        ex("squat", "Bodyweight squat", REPS, listOf(QUADS), listOf(GLUTES)),
        ex("lunge", "Lunge", REPS, listOf(QUADS, GLUTES), listOf(HAMSTRINGS)),
        ex("bulgarian_split_squat", "Bulgarian split squat", REPS, listOf(QUADS, GLUTES), listOf(HAMSTRINGS, ADDUCTORS)),
        ex("pistol_squat", "Pistol squat", REPS, listOf(QUADS, GLUTES), listOf(CALVES, ABS)),
        ex("nordic_curl", "Nordic hamstring curl", REPS, listOf(HAMSTRINGS), listOf(GLUTES)),
        ex("plank", "Plank", HOLD, listOf(ABS), listOf(SHOULDERS, OBLIQUES)),
        ex("hollow_hold", "Hollow body hold", HOLD, listOf(ABS), listOf(OBLIQUES)),
        ex("hanging_knee_raise", "Hanging knee raise", REPS, listOf(ABS), listOf(FOREARMS, OBLIQUES)),
        ex("hanging_leg_raise", "Hanging leg raise", REPS, listOf(ABS), listOf(OBLIQUES, FOREARMS)),

        // Calisthenics skills
        ex("muscle_up", "Muscle-up", REPS, listOf(UPPER_BACK, TRICEPS), listOf(CHEST, BICEPS, SHOULDERS)),
        ex("l_sit", "L-sit", HOLD, listOf(ABS), listOf(TRICEPS, QUADS)),
        ex("handstand_hold", "Handstand hold", HOLD, listOf(SHOULDERS), listOf(TRICEPS, TRAPS, ABS)),
        ex("handstand_push_up", "Handstand push-up", REPS, listOf(SHOULDERS, TRICEPS), listOf(TRAPS)),
        ex("tuck_planche", "Tuck planche", HOLD, listOf(SHOULDERS), listOf(CHEST, ABS, TRICEPS)),
        ex("pseudo_planche_push_up", "Pseudo planche push-up", REPS, listOf(SHOULDERS, CHEST), listOf(TRICEPS, ABS)),
        ex("tuck_front_lever", "Tuck front lever", HOLD, listOf(UPPER_BACK), listOf(ABS, BICEPS)),
        ex("front_lever_row", "Tuck front lever row", REPS, listOf(UPPER_BACK), listOf(BICEPS, ABS)),
        ex("back_lever_tuck", "Tuck back lever", HOLD, listOf(SHOULDERS, CHEST), listOf(BICEPS, LOWER_BACK)),

        // Weighted calisthenics
        ex("weighted_pull_up", "Weighted pull-up", WEIGHTED, listOf(UPPER_BACK), listOf(BICEPS, FOREARMS, TRAPS)),
        ex("weighted_chin_up", "Weighted chin-up", WEIGHTED, listOf(UPPER_BACK, BICEPS), listOf(FOREARMS)),
        ex("weighted_dip", "Weighted dip", WEIGHTED, listOf(TRICEPS, CHEST), listOf(SHOULDERS)),
        ex("weighted_push_up", "Weighted push-up", WEIGHTED, listOf(CHEST), listOf(TRICEPS, SHOULDERS)),

        // Gym
        ex("bench_press", "Bench press", WEIGHTED, listOf(CHEST), listOf(TRICEPS, SHOULDERS)),
        ex("incline_db_press", "Incline dumbbell press", WEIGHTED, listOf(CHEST, SHOULDERS), listOf(TRICEPS)),
        ex("overhead_press", "Overhead press", WEIGHTED, listOf(SHOULDERS), listOf(TRICEPS, TRAPS)),
        ex("lateral_raise", "Lateral raise", WEIGHTED, listOf(SHOULDERS)),
        ex("face_pull", "Face pull", WEIGHTED, listOf(SHOULDERS, TRAPS), listOf(UPPER_BACK)),
        ex("barbell_row", "Barbell row", WEIGHTED, listOf(UPPER_BACK), listOf(BICEPS, LOWER_BACK, TRAPS)),
        ex("lat_pulldown", "Lat pulldown", WEIGHTED, listOf(UPPER_BACK), listOf(BICEPS)),
        ex("seated_cable_row", "Seated cable row", WEIGHTED, listOf(UPPER_BACK), listOf(BICEPS, TRAPS)),
        ex("deadlift", "Deadlift", WEIGHTED, listOf(HAMSTRINGS, GLUTES, LOWER_BACK), listOf(TRAPS, FOREARMS, QUADS)),
        ex("romanian_deadlift", "Romanian deadlift", WEIGHTED, listOf(HAMSTRINGS, GLUTES), listOf(LOWER_BACK)),
        ex("back_squat", "Back squat", WEIGHTED, listOf(QUADS, GLUTES), listOf(HAMSTRINGS, LOWER_BACK, ADDUCTORS)),
        ex("leg_press", "Leg press", WEIGHTED, listOf(QUADS), listOf(GLUTES, ADDUCTORS)),
        ex("leg_extension", "Leg extension", WEIGHTED, listOf(QUADS)),
        ex("leg_curl", "Leg curl", WEIGHTED, listOf(HAMSTRINGS)),
        ex("hip_thrust", "Hip thrust", WEIGHTED, listOf(GLUTES), listOf(HAMSTRINGS)),
        ex("standing_calf_raise", "Standing calf raise (machine)", WEIGHTED, listOf(CALVES)),
        ex("bicep_curl", "Dumbbell curl", WEIGHTED, listOf(BICEPS), listOf(FOREARMS)),
        ex("hammer_curl", "Hammer curl", WEIGHTED, listOf(BICEPS, FOREARMS)),
        ex("tricep_pushdown", "Triceps pushdown", WEIGHTED, listOf(TRICEPS)),
        ex("skull_crusher", "Skull crusher", WEIGHTED, listOf(TRICEPS)),
        ex("shrug", "Shrug", WEIGHTED, listOf(TRAPS), listOf(FOREARMS)),
        ex("cable_crunch", "Cable crunch", WEIGHTED, listOf(ABS), listOf(OBLIQUES)),
        ex("tibialis_raise", "Tibialis raise", REPS, listOf(TIBIALIS)),
        ex("heel_walk", "Heel walk", HOLD, listOf(TIBIALIS), listOf(CALVES)),
        ex("band_dorsiflexion", "Band dorsiflexion", REPS, listOf(TIBIALIS)),
        // Adductors, obliques and forearms
        ex("copenhagen_plank_short", "Copenhagen plank, short lever", HOLD, listOf(ADDUCTORS), listOf(OBLIQUES, ABS)),
        ex("copenhagen_plank_long", "Copenhagen plank, long lever", HOLD, listOf(ADDUCTORS), listOf(OBLIQUES, ABS)),
        ex("side_lying_hip_adduction", "Side-lying hip adduction", REPS, listOf(ADDUCTORS)),
        ex("cossack_squat", "Cossack squat", REPS, listOf(ADDUCTORS, GLUTES), listOf(QUADS, HAMSTRINGS)),
        ex("band_hip_adduction", "Band hip adduction", REPS, listOf(ADDUCTORS)),
        ex("side_plank", "Side plank", HOLD, listOf(OBLIQUES), listOf(ABS, SHOULDERS)),
        ex("bicycle_crunch", "Bicycle crunch", REPS, listOf(OBLIQUES, ABS)),
        ex("wrist_curl", "Wrist curl", WEIGHTED, listOf(FOREARMS)),
        // Kettlebells and dumbbells at home
        ex("kb_swing", "Kettlebell swing", WEIGHTED, listOf(GLUTES, HAMSTRINGS), listOf(LOWER_BACK)),
        ex("goblet_squat", "Goblet squat", WEIGHTED, listOf(QUADS, GLUTES), listOf(ABS, ADDUCTORS)),
        ex("kb_deadlift", "Kettlebell deadlift", WEIGHTED, listOf(HAMSTRINGS, GLUTES), listOf(LOWER_BACK)),
        ex("kb_clean", "Kettlebell clean", WEIGHTED, listOf(GLUTES, SHOULDERS), listOf(FOREARMS)),
        ex("kb_press", "Kettlebell press", WEIGHTED, listOf(SHOULDERS), listOf(TRICEPS)),
        ex("kb_snatch", "Kettlebell snatch", WEIGHTED, listOf(SHOULDERS, GLUTES), listOf(HAMSTRINGS)),
        ex("kb_row", "Kettlebell row", WEIGHTED, listOf(UPPER_BACK), listOf(BICEPS)),
        ex("turkish_get_up", "Turkish get-up", WEIGHTED, listOf(SHOULDERS, ABS), listOf(GLUTES)),
        ex("farmers_carry", "Farmer's carry", WEIGHTED, listOf(FOREARMS, TRAPS), listOf(ABS)),
        ex("db_bench_press", "Dumbbell bench press", WEIGHTED, listOf(CHEST), listOf(TRICEPS)),
        ex("db_shoulder_press", "Dumbbell shoulder press", WEIGHTED, listOf(SHOULDERS), listOf(TRICEPS)),
        ex("db_row", "Dumbbell row", WEIGHTED, listOf(UPPER_BACK), listOf(BICEPS)),
        ex("db_lunge", "Dumbbell lunge", WEIGHTED, listOf(QUADS, GLUTES), listOf(HAMSTRINGS)),
        ex("russian_twist", "Russian twist", WEIGHTED, listOf(OBLIQUES), listOf(ABS)),
        // More strength moves: hamstrings, arms, calves, back, obliques, machines, bands, kettlebell
        ex("seated_leg_curl", "Seated leg curl", WEIGHTED, listOf(HAMSTRINGS)),
        ex("single_leg_rdl", "Single-leg Romanian deadlift", WEIGHTED, listOf(HAMSTRINGS, GLUTES), listOf(LOWER_BACK)),
        ex("db_romanian_deadlift", "Dumbbell Romanian deadlift", WEIGHTED, listOf(HAMSTRINGS, GLUTES), listOf(LOWER_BACK)),
        ex("good_morning", "Good morning", WEIGHTED, listOf(HAMSTRINGS, LOWER_BACK), listOf(GLUTES)),
        ex("band_leg_curl", "Band leg curl", REPS, listOf(HAMSTRINGS)),
        ex("barbell_curl", "Barbell curl", WEIGHTED, listOf(BICEPS), listOf(FOREARMS)),
        ex("incline_db_curl", "Incline dumbbell curl", WEIGHTED, listOf(BICEPS), listOf(FOREARMS)),
        ex("concentration_curl", "Concentration curl", WEIGHTED, listOf(BICEPS)),
        ex("preacher_curl", "Preacher curl (machine)", WEIGHTED, listOf(BICEPS)),
        ex("cable_curl", "Cable curl", WEIGHTED, listOf(BICEPS), listOf(FOREARMS)),
        ex("band_curl", "Band curl", REPS, listOf(BICEPS)),
        ex("seated_calf_raise", "Seated calf raise (machine)", WEIGHTED, listOf(CALVES)),
        ex("single_leg_calf_raise", "Single-leg calf raise", REPS, listOf(CALVES)),
        ex("db_calf_raise", "Dumbbell calf raise", WEIGHTED, listOf(CALVES)),
        ex("back_extension", "Back extension", REPS, listOf(LOWER_BACK), listOf(GLUTES, HAMSTRINGS)),
        ex("bird_dog", "Bird dog", REPS, listOf(LOWER_BACK), listOf(GLUTES, ABS)),
        ex("cable_pull_through", "Cable pull-through", WEIGHTED, listOf(GLUTES, HAMSTRINGS), listOf(LOWER_BACK)),
        ex("pallof_press", "Pallof press", WEIGHTED, listOf(OBLIQUES), listOf(ABS)),
        ex("cable_woodchop", "Cable woodchop", WEIGHTED, listOf(OBLIQUES), listOf(ABS, SHOULDERS)),
        ex("db_side_bend", "Dumbbell side bend", WEIGHTED, listOf(OBLIQUES)),
        ex("mountain_climber", "Mountain climber", REPS, listOf(ABS), listOf(OBLIQUES, SHOULDERS)),
        ex("cable_fly", "Cable fly", WEIGHTED, listOf(CHEST), listOf(SHOULDERS)),
        ex("pec_deck", "Pec deck", WEIGHTED, listOf(CHEST), listOf(SHOULDERS)),
        ex("chest_press_machine", "Chest press machine", WEIGHTED, listOf(CHEST), listOf(TRICEPS, SHOULDERS)),
        ex("shoulder_press_machine", "Shoulder press machine", WEIGHTED, listOf(SHOULDERS), listOf(TRICEPS)),
        ex("cable_lateral_raise", "Cable lateral raise", WEIGHTED, listOf(SHOULDERS)),
        ex("reverse_pec_deck", "Reverse pec deck", WEIGHTED, listOf(SHOULDERS, UPPER_BACK), listOf(TRAPS)),
        ex("straight_arm_pulldown", "Straight-arm pulldown", WEIGHTED, listOf(UPPER_BACK), listOf(TRICEPS)),
        ex("overhead_cable_triceps_extension", "Overhead cable triceps extension", WEIGHTED, listOf(TRICEPS)),
        ex("band_pull_apart", "Band pull-apart", REPS, listOf(UPPER_BACK, SHOULDERS), listOf(TRAPS)),
        ex("band_row", "Band row", REPS, listOf(UPPER_BACK), listOf(BICEPS)),
        ex("band_chest_press", "Band chest press", REPS, listOf(CHEST), listOf(TRICEPS, SHOULDERS)),
        ex("kb_sumo_deadlift", "Kettlebell sumo deadlift", WEIGHTED, listOf(GLUTES, HAMSTRINGS), listOf(ADDUCTORS, QUADS)),
        ex("kb_halo", "Kettlebell halo", WEIGHTED, listOf(SHOULDERS), listOf(TRICEPS, ABS)),
        ex("kb_windmill", "Kettlebell windmill", WEIGHTED, listOf(OBLIQUES, SHOULDERS), listOf(HAMSTRINGS, GLUTES)),
    ) + stretches
}
