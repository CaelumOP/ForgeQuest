package com.forgequest.app.domain

import com.forgequest.app.data.db.ExerciseEntity
import com.forgequest.app.data.db.PlanItemEntity
import com.forgequest.app.data.plan.PlanDraft
import com.forgequest.app.data.plan.PlanDraftItem
import kotlin.math.max

/** Equipment categories for filtering and biomechanical exercise substitution. */
enum class Equipment(val labelEn: String, val labelAr: String) {
    BODYWEIGHT("Bodyweight", "وزن الجسم"),
    PULL_UP_BAR("Pull-up bar", "عقلة"),
    DIP_BARS_OR_BENCH("Dip bars / rings / bench", "متوازي أو حلقات أو مقعد"),
    DUMBBELLS("Dumbbells", "دمبل"),
    KETTLEBELL("Kettlebell", "كيتل بيل"),
    BARBELL_RACK("Barbell & rack", "بار وراك"),
    CABLES_MACHINES("Cables & gym machines", "أجهزة وكوابل النادي"),
}

/** Parses a comma-separated equipment setting string into a Set<Equipment>. Empty string means "not configured". */
fun parseEquipmentSet(raw: String): Set<Equipment> {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return emptySet()
    if (trimmed.equals("all", ignoreCase = true) || trimmed.equals("full_gym", ignoreCase = true)) {
        return Equipment.entries.toSet()
    }
    EquipmentPreset.entries.firstOrNull { it.id.equals(trimmed, ignoreCase = true) }?.let { return it.equipment }
    val parsed = trimmed.split(",")
        .mapNotNull { token -> runCatching { Equipment.valueOf(token.trim().uppercase()) }.getOrNull() }
        .toSet()
    return if (parsed.isEmpty()) emptySet() else parsed + Equipment.BODYWEIGHT
}

/**
 * Scans coach memory notes and user messages for explicit equipment mentions so the coach knows whether
 * equipment intake has already been answered.
 */
fun inferEquipmentFromText(vararg texts: String): Set<Equipment>? {
    val combined = texts.joinToString("\n").lowercase()
    if (combined.isBlank()) return null

    if (words("""full gym|commercial gym|gym member|all equipment|نادي متكامل|مشترك في نادي|في النادي|جيم كامل""").containsMatchIn(combined)) {
        return Equipment.entries.toSet()
    }
    if (words("""no equipment|bodyweight only|pure bodyweight|nothing at home|بدون معدات|وزن الجسم فقط|وزن جسمي فقط|ما عندي معدات|لا أملك معدات""").containsMatchIn(combined)) {
        return setOf(Equipment.BODYWEIGHT)
    }

    val found = mutableSetOf<Equipment>()
    if (words("""pull-?up bar|chin-?up bar|doorway bar|عقلة|بار عقلة""").hasPositive(combined)) {
        found += Equipment.PULL_UP_BAR
    }
    if (words("""dip bar|parallel bar|rings|gymnastic rings|متوازي|حلقات""").hasPositive(combined)) {
        found += Equipment.DIP_BARS_OR_BENCH
    }
    if (words("""dumbbell|dumbbells|دمبل|دامبل""").hasPositive(combined)) {
        found += Equipment.DUMBBELLS
    }
    if (words("""kettlebell|kettlebells|كيتل بيل|كيتلبيل""").hasPositive(combined)) {
        found += Equipment.KETTLEBELL
    }
    // Not a lone «بار»: «بار عقلة» is a pull-up bar.
    if (words("""barbell|squat rack|power rack|bench press|باربل|بار حديد|بار أولمبي|راك""").hasPositive(combined)) {
        found += Equipment.BARBELL_RACK
    }
    // Not a lone «أجهزة»: it means any device.
    if (words("""cable|cables|lat pulldown|leg press|smith machine|كيبل|كوابل|أجهزة النادي|أجهزة الجيم""").hasPositive(combined)) {
        found += Equipment.CABLES_MACHINES
    }
    if (words("""calisthenics|home workout|at home|في البيت|في المنزل|كاليستنكس""").containsMatchIn(combined) && found.isEmpty()) {
        if (words("""bodyweight|وزن الجسم""").containsMatchIn(combined)) {
            return setOf(Equipment.BODYWEIGHT)
        }
    }
    return if (found.isNotEmpty()) found + Equipment.BODYWEIGHT else null
}

/**
 * Whole words or phrases from [alternatives] ("a|b c"). Spelled out instead of `\b`, which newer Java and
 * JavaScript treat as English-only, so Arabic words would never match.
 */
private fun words(alternatives: String) = Regex("""(?<![\p{L}\p{N}_])($alternatives)(?![\p{L}\p{N}_])""")

private val NEGATION = words("""no|not|don['’]?t|doesn['’]?t|without|never|بدون|لا|ما|ليس|ماعندي|مافي""")
private val CLAUSE_BREAK = Regex("""[.,،;!?؟\n]|(?<![\p{L}])(but|لكن|ولكن|بل|بس)(?![\p{L}])""")

/** True when some match is not negated in its own clause: "no dumbbells" and «ما عندي دمبل» don't count. */
private fun Regex.hasPositive(text: String): Boolean = findAll(text).any { m ->
    val before = text.substring(maxOf(0, m.range.first - 30), m.range.first).split(CLAUSE_BREAK).last()
    !NEGATION.containsMatchIn(before)
}

/** Quick presets for Onboarding and Profile settings. */
enum class EquipmentPreset(
    val id: String,
    val labelEn: String,
    val labelAr: String,
    val equipment: Set<Equipment>,
) {
    FULL_GYM(
        id = "full_gym",
        labelEn = "Full gym",
        labelAr = "نادي متكامل",
        equipment = Equipment.entries.toSet(),
    ),
    HOME_GYM(
        id = "home_gym",
        labelEn = "Home gym (barbell, dumbbells, bar)",
        labelAr = "نادي منزلي (بار، دمبل، عقلة)",
        equipment = setOf(
            Equipment.BODYWEIGHT, Equipment.PULL_UP_BAR, Equipment.DIP_BARS_OR_BENCH,
            Equipment.DUMBBELLS, Equipment.KETTLEBELL, Equipment.BARBELL_RACK,
        ),
    ),
    DUMBBELLS_AND_BAR(
        id = "dumbbells_bar",
        labelEn = "Dumbbells & pull-up bar",
        labelAr = "دمبل وعقلة",
        equipment = setOf(Equipment.BODYWEIGHT, Equipment.PULL_UP_BAR, Equipment.DIP_BARS_OR_BENCH, Equipment.DUMBBELLS),
    ),
    CALISTHENICS_PARK(
        id = "calisthenics",
        labelEn = "Calisthenics (pull-up bar & dip bars)",
        labelAr = "كاليستنكس (عقلة ومتوازي)",
        equipment = setOf(Equipment.BODYWEIGHT, Equipment.PULL_UP_BAR, Equipment.DIP_BARS_OR_BENCH),
    ),
    PULL_UP_BAR_ONLY(
        id = "pull_up_bar",
        labelEn = "Doorway pull-up bar only",
        labelAr = "عقلة منزلية فقط",
        equipment = setOf(Equipment.BODYWEIGHT, Equipment.PULL_UP_BAR),
    ),
    BODYWEIGHT_ONLY(
        id = "bodyweight",
        labelEn = "Pure bodyweight (no equipment)",
        labelAr = "وزن الجسم فقط (بدون معدات)",
        equipment = setOf(Equipment.BODYWEIGHT),
    );

    fun encode(): String = equipment.joinToString(",") { it.name }

    companion object {
        fun fromEncoded(raw: String): EquipmentPreset? {
            if (raw.isBlank()) return null
            byId[raw.trim().lowercase()]?.let { return it }
            val parsed = parseEquipmentSet(raw)
            return entries.firstOrNull { it.equipment == parsed }
        }

        private val byId = entries.associateBy { it.id }
    }
}

enum class MovementPattern(val labelEn: String, val labelAr: String) {
    HORIZONTAL_PUSH("Horizontal push", "دفع أفقي"),
    DIP_PUSH("Dip / decline push", "دفع سفلي / متوازي"),
    VERTICAL_PUSH("Vertical push", "دفع علوي"),
    LATERAL_RAISE("Shoulder abduction", "رفرفة وكتف جانبي"),
    VERTICAL_PULL("Vertical pull", "سحب عمودي"),
    HORIZONTAL_PULL("Horizontal row", "سحب أفقي / تجديف"),
    UPPER_BACK_PULL("Upper back / rear delt", "أعلى الظهر والكتف الخلفي"),
    SQUAT_BILATERAL("Bilateral squat", "قرفصاء ثنائية"),
    SQUAT_UNILATERAL("Unilateral leg / split squat", "أرجل فردية / طعن"),
    HIP_HINGE("Hip hinge / posterior chain", "مفصل الورك / السلسلة الخلفية"),
    KNEE_FLEXION("Knee flexion (hamstrings)", "ثني الركبة (خلفية الفخذ)"),
    ELBOW_FLEXION("Elbow flexion (biceps)", "ثني المرفق (بايسبس)"),
    ELBOW_EXTENSION("Elbow extension (triceps)", "مدّ المرفق (ترايسبس)"),
    CORE_ANTI_EXTENSION("Core anti-extension hold", "ثبات الجذع ومقاومة التمدد"),
    CORE_FLEXION_ROTATION("Core flexion / rotation", "ثني والتواء البطن"),
    ANKLE_FLEXION("Calf / ankle flexion", "بطة وقصبة الساق"),
    CARRY_CONDITIONING("Loaded carry / conditioning", "حمل ولياقة عامة"),
}

enum class JointAngle {
    FLAT_HORIZONTAL,
    INCLINE_UPWARD,
    DECLINE_DOWNWARD,
    OVERHEAD_VERTICAL,
    FRONTAL_ABDUCTION,
    OVERHEAD_PRONATED,
    OVERHEAD_SUPINATED,
    MID_ROW_PRONATED,
    MID_ROW_SUPINATED,
    HIGH_EXTERNAL_ROTATION,
    KNEE_DOMINANT_BILATERAL,
    KNEE_DOMINANT_SPLIT,
    HIP_EXTENSION_SUPINE,
    HIP_HINGE_STANDING,
    HAMSTRING_CURL,
    SAGITTAL_SUPINATED,
    SAGITTAL_NEUTRAL,
    SAGITTAL_TRICEP,
    ISOMETRIC_PLANK,
    SPINAL_FLEXION,
    HANGING_HIP_FLEXION,
    ROTATIONAL,
    PLANTAR_FLEXION,
    DORSI_FLEXION,
    LOCOMOTION,
}

data class BiomechanicalProfile(
    val equipment: Equipment,
    val pattern: MovementPattern,
    val angle: JointAngle,
    val preferredSwaps: List<String> = emptyList(),
)

data class SwapCandidate(
    val exercise: ExerciseEntity,
    val profile: BiomechanicalProfile,
    val score: Int,
    val matchesUserEquipment: Boolean,
)

object ExerciseSwapEngine {

    fun parseEquipmentSet(raw: String): Set<Equipment> = com.forgequest.app.domain.parseEquipmentSet(raw)

    fun inferEquipmentFromText(vararg texts: String): Set<Equipment>? = com.forgequest.app.domain.inferEquipmentFromText(*texts)

    private fun prof(
        eq: Equipment,
        pat: MovementPattern,
        ang: JointAngle,
        vararg swaps: String,
    ) = BiomechanicalProfile(eq, pat, ang, swaps.toList())

    /** Explicit biomechanical profiles for all 81 built-in exercises in ExerciseCatalog. */
    val catalogProfiles: Map<String, BiomechanicalProfile> = mapOf(
        // Warm-up / conditioning
        "jumping_jacks" to prof(Equipment.BODYWEIGHT, MovementPattern.CARRY_CONDITIONING, JointAngle.LOCOMOTION, "burpee", "calf_raise"),
        "plank_shoulder_taps" to prof(Equipment.BODYWEIGHT, MovementPattern.CORE_ANTI_EXTENSION, JointAngle.ISOMETRIC_PLANK, "plank", "incline_hollow_plank", "hollow_hold"),
        "burpee" to prof(Equipment.BODYWEIGHT, MovementPattern.CARRY_CONDITIONING, JointAngle.LOCOMOTION, "jumping_jacks", "push_up", "squat"),

        // Hemo foundation: core
        "incline_hollow_plank" to prof(Equipment.BODYWEIGHT, MovementPattern.CORE_ANTI_EXTENSION, JointAngle.ISOMETRIC_PLANK, "plank", "hollow_hold", "plank_shoulder_taps"),
        "incline_reverse_plank" to prof(Equipment.BODYWEIGHT, MovementPattern.HIP_HINGE, JointAngle.HIP_EXTENSION_SUPINE, "hip_bridge", "superman_hold", "hip_thrust"),
        "hip_bridge" to prof(Equipment.BODYWEIGHT, MovementPattern.HIP_HINGE, JointAngle.HIP_EXTENSION_SUPINE, "hip_thrust", "kb_swing", "romanian_deadlift", "incline_reverse_plank"),
        "crunch" to prof(Equipment.BODYWEIGHT, MovementPattern.CORE_FLEXION_ROTATION, JointAngle.SPINAL_FLEXION, "cable_crunch", "hanging_knee_raise", "hollow_hold"),
        "superman_hold" to prof(Equipment.BODYWEIGHT, MovementPattern.HIP_HINGE, JointAngle.HIP_EXTENSION_SUPINE, "hip_bridge", "incline_reverse_plank", "romanian_deadlift"),

        // Hemo foundation: upper body
        "knee_scapular_push_up" to prof(Equipment.BODYWEIGHT, MovementPattern.HORIZONTAL_PUSH, JointAngle.FLAT_HORIZONTAL, "knee_push_up", "plank_shoulder_taps", "push_up"),
        "scapular_australian_pull_up" to prof(Equipment.DIP_BARS_OR_BENCH, MovementPattern.HORIZONTAL_PULL, JointAngle.MID_ROW_PRONATED, "australian_pull_up", "dead_hang", "db_row"),
        "bent_leg_bench_dip" to prof(Equipment.DIP_BARS_OR_BENCH, MovementPattern.DIP_PUSH, JointAngle.DECLINE_DOWNWARD, "reverse_grip_bench_dip", "dip", "diamond_push_up", "incline_diamond_knee_push_up"),
        "scapular_australian_chin_up" to prof(Equipment.DIP_BARS_OR_BENCH, MovementPattern.HORIZONTAL_PULL, JointAngle.MID_ROW_SUPINATED, "australian_chin_up", "scapular_australian_pull_up", "chin_up"),
        "knee_push_up" to prof(Equipment.BODYWEIGHT, MovementPattern.HORIZONTAL_PUSH, JointAngle.FLAT_HORIZONTAL, "wide_knee_push_up", "push_up", "incline_diamond_knee_push_up", "db_bench_press"),
        "australian_pull_up" to prof(Equipment.DIP_BARS_OR_BENCH, MovementPattern.HORIZONTAL_PULL, JointAngle.MID_ROW_PRONATED, "db_row", "kb_row", "barbell_row", "seated_cable_row", "negative_pull_up", "pull_up"),
        "reverse_grip_bench_dip" to prof(Equipment.DIP_BARS_OR_BENCH, MovementPattern.DIP_PUSH, JointAngle.DECLINE_DOWNWARD, "bent_leg_bench_dip", "dip", "diamond_push_up"),
        "australian_chin_up" to prof(Equipment.DIP_BARS_OR_BENCH, MovementPattern.HORIZONTAL_PULL, JointAngle.MID_ROW_SUPINATED, "australian_pull_up", "chin_up", "negative_pull_up", "db_row"),
        "wide_knee_push_up" to prof(Equipment.BODYWEIGHT, MovementPattern.HORIZONTAL_PUSH, JointAngle.FLAT_HORIZONTAL, "knee_push_up", "push_up", "db_bench_press"),
        "dead_hang" to prof(Equipment.PULL_UP_BAR, MovementPattern.VERTICAL_PULL, JointAngle.OVERHEAD_PRONATED, "scapular_australian_pull_up", "negative_pull_up", "farmers_carry"),
        "incline_diamond_knee_push_up" to prof(Equipment.BODYWEIGHT, MovementPattern.ELBOW_EXTENSION, JointAngle.INCLINE_UPWARD, "diamond_push_up", "bent_leg_bench_dip", "knee_push_up", "tricep_pushdown"),

        // Hemo foundation: legs
        "half_squat" to prof(Equipment.BODYWEIGHT, MovementPattern.SQUAT_BILATERAL, JointAngle.KNEE_DOMINANT_BILATERAL, "squat", "half_wide_squat", "half_narrow_squat", "goblet_squat"),
        "half_wide_squat" to prof(Equipment.BODYWEIGHT, MovementPattern.SQUAT_BILATERAL, JointAngle.KNEE_DOMINANT_BILATERAL, "half_squat", "squat", "goblet_squat"),
        "half_narrow_squat" to prof(Equipment.BODYWEIGHT, MovementPattern.SQUAT_BILATERAL, JointAngle.KNEE_DOMINANT_BILATERAL, "half_squat", "squat", "lunge"),
        "calf_raise_turned_out" to prof(Equipment.BODYWEIGHT, MovementPattern.ANKLE_FLEXION, JointAngle.PLANTAR_FLEXION, "calf_raise", "standing_calf_raise"),
        "calf_raise" to prof(Equipment.BODYWEIGHT, MovementPattern.ANKLE_FLEXION, JointAngle.PLANTAR_FLEXION, "standing_calf_raise", "calf_raise_turned_out"),

        // Calisthenics basics
        "push_up" to prof(Equipment.BODYWEIGHT, MovementPattern.HORIZONTAL_PUSH, JointAngle.FLAT_HORIZONTAL, "db_bench_press", "bench_press", "weighted_push_up", "diamond_push_up", "pseudo_planche_push_up", "knee_push_up"),
        "diamond_push_up" to prof(Equipment.BODYWEIGHT, MovementPattern.ELBOW_EXTENSION, JointAngle.SAGITTAL_TRICEP, "dip", "skull_crusher", "tricep_pushdown", "push_up", "bent_leg_bench_dip"),
        "pike_push_up" to prof(Equipment.BODYWEIGHT, MovementPattern.VERTICAL_PUSH, JointAngle.OVERHEAD_VERTICAL, "db_shoulder_press", "overhead_press", "kb_press", "handstand_push_up", "incline_db_press"),
        "pull_up" to prof(Equipment.PULL_UP_BAR, MovementPattern.VERTICAL_PULL, JointAngle.OVERHEAD_PRONATED, "chin_up", "lat_pulldown", "weighted_pull_up", "negative_pull_up", "australian_pull_up", "db_row"),
        "chin_up" to prof(Equipment.PULL_UP_BAR, MovementPattern.VERTICAL_PULL, JointAngle.OVERHEAD_SUPINATED, "pull_up", "weighted_chin_up", "lat_pulldown", "australian_chin_up", "db_row"),
        "negative_pull_up" to prof(Equipment.PULL_UP_BAR, MovementPattern.VERTICAL_PULL, JointAngle.OVERHEAD_PRONATED, "australian_pull_up", "lat_pulldown", "pull_up", "chin_up"),
        "dip" to prof(Equipment.DIP_BARS_OR_BENCH, MovementPattern.DIP_PUSH, JointAngle.DECLINE_DOWNWARD, "weighted_dip", "push_up", "diamond_push_up", "db_bench_press", "bench_press", "bent_leg_bench_dip"),
        "squat" to prof(Equipment.BODYWEIGHT, MovementPattern.SQUAT_BILATERAL, JointAngle.KNEE_DOMINANT_BILATERAL, "goblet_squat", "back_squat", "lunge", "bulgarian_split_squat", "leg_press"),
        "lunge" to prof(Equipment.BODYWEIGHT, MovementPattern.SQUAT_UNILATERAL, JointAngle.KNEE_DOMINANT_SPLIT, "db_lunge", "bulgarian_split_squat", "squat", "goblet_squat"),
        "bulgarian_split_squat" to prof(Equipment.DIP_BARS_OR_BENCH, MovementPattern.SQUAT_UNILATERAL, JointAngle.KNEE_DOMINANT_SPLIT, "db_lunge", "lunge", "pistol_squat", "goblet_squat", "back_squat"),
        "pistol_squat" to prof(Equipment.BODYWEIGHT, MovementPattern.SQUAT_UNILATERAL, JointAngle.KNEE_DOMINANT_SPLIT, "bulgarian_split_squat", "db_lunge", "lunge", "goblet_squat"),
        "nordic_curl" to prof(Equipment.BODYWEIGHT, MovementPattern.KNEE_FLEXION, JointAngle.HAMSTRING_CURL, "leg_curl", "romanian_deadlift", "hip_bridge", "kb_swing"),
        "plank" to prof(Equipment.BODYWEIGHT, MovementPattern.CORE_ANTI_EXTENSION, JointAngle.ISOMETRIC_PLANK, "hollow_hold", "incline_hollow_plank", "plank_shoulder_taps", "l_sit"),
        "hollow_hold" to prof(Equipment.BODYWEIGHT, MovementPattern.CORE_ANTI_EXTENSION, JointAngle.ISOMETRIC_PLANK, "plank", "l_sit", "incline_hollow_plank", "hanging_knee_raise"),
        "hanging_knee_raise" to prof(Equipment.PULL_UP_BAR, MovementPattern.CORE_FLEXION_ROTATION, JointAngle.HANGING_HIP_FLEXION, "hanging_leg_raise", "crunch", "hollow_hold", "l_sit"),
        "hanging_leg_raise" to prof(Equipment.PULL_UP_BAR, MovementPattern.CORE_FLEXION_ROTATION, JointAngle.HANGING_HIP_FLEXION, "hanging_knee_raise", "l_sit", "hollow_hold", "cable_crunch"),

        // Calisthenics skills
        "muscle_up" to prof(Equipment.PULL_UP_BAR, MovementPattern.VERTICAL_PULL, JointAngle.OVERHEAD_PRONATED, "weighted_pull_up", "pull_up", "dip"),
        "l_sit" to prof(Equipment.DIP_BARS_OR_BENCH, MovementPattern.CORE_ANTI_EXTENSION, JointAngle.ISOMETRIC_PLANK, "hollow_hold", "hanging_leg_raise", "plank"),
        "handstand_hold" to prof(Equipment.BODYWEIGHT, MovementPattern.VERTICAL_PUSH, JointAngle.OVERHEAD_VERTICAL, "pike_push_up", "handstand_push_up", "overhead_press", "db_shoulder_press"),
        "handstand_push_up" to prof(Equipment.BODYWEIGHT, MovementPattern.VERTICAL_PUSH, JointAngle.OVERHEAD_VERTICAL, "pike_push_up", "overhead_press", "db_shoulder_press", "kb_press"),
        "tuck_planche" to prof(Equipment.BODYWEIGHT, MovementPattern.HORIZONTAL_PUSH, JointAngle.FLAT_HORIZONTAL, "pseudo_planche_push_up", "push_up", "dip"),
        "pseudo_planche_push_up" to prof(Equipment.BODYWEIGHT, MovementPattern.HORIZONTAL_PUSH, JointAngle.FLAT_HORIZONTAL, "push_up", "weighted_push_up", "tuck_planche", "dip", "db_bench_press"),
        "tuck_front_lever" to prof(Equipment.PULL_UP_BAR, MovementPattern.HORIZONTAL_PULL, JointAngle.MID_ROW_PRONATED, "front_lever_row", "pull_up", "australian_pull_up", "barbell_row"),
        "front_lever_row" to prof(Equipment.PULL_UP_BAR, MovementPattern.HORIZONTAL_PULL, JointAngle.MID_ROW_PRONATED, "tuck_front_lever", "barbell_row", "db_row", "australian_pull_up", "pull_up"),
        "back_lever_tuck" to prof(Equipment.PULL_UP_BAR, MovementPattern.UPPER_BACK_PULL, JointAngle.HIGH_EXTERNAL_ROTATION, "tuck_front_lever", "dip", "pseudo_planche_push_up"),

        // Weighted calisthenics
        "weighted_pull_up" to prof(Equipment.PULL_UP_BAR, MovementPattern.VERTICAL_PULL, JointAngle.OVERHEAD_PRONATED, "pull_up", "weighted_chin_up", "lat_pulldown", "chin_up"),
        "weighted_chin_up" to prof(Equipment.PULL_UP_BAR, MovementPattern.VERTICAL_PULL, JointAngle.OVERHEAD_SUPINATED, "chin_up", "weighted_pull_up", "lat_pulldown", "pull_up"),
        "weighted_dip" to prof(Equipment.DIP_BARS_OR_BENCH, MovementPattern.DIP_PUSH, JointAngle.DECLINE_DOWNWARD, "dip", "bench_press", "db_bench_press", "weighted_push_up"),
        "weighted_push_up" to prof(Equipment.BODYWEIGHT, MovementPattern.HORIZONTAL_PUSH, JointAngle.FLAT_HORIZONTAL, "push_up", "bench_press", "db_bench_press", "pseudo_planche_push_up"),

        // Gym (Barbell, Dumbbells, Cables & Machines)
        "bench_press" to prof(Equipment.BARBELL_RACK, MovementPattern.HORIZONTAL_PUSH, JointAngle.FLAT_HORIZONTAL, "db_bench_press", "weighted_push_up", "push_up", "dip", "pseudo_planche_push_up"),
        "incline_db_press" to prof(Equipment.DUMBBELLS, MovementPattern.HORIZONTAL_PUSH, JointAngle.INCLINE_UPWARD, "db_bench_press", "bench_press", "pike_push_up", "push_up", "incline_diamond_knee_push_up"),
        "overhead_press" to prof(Equipment.BARBELL_RACK, MovementPattern.VERTICAL_PUSH, JointAngle.OVERHEAD_VERTICAL, "db_shoulder_press", "kb_press", "pike_push_up", "handstand_push_up"),
        "lateral_raise" to prof(Equipment.DUMBBELLS, MovementPattern.LATERAL_RAISE, JointAngle.FRONTAL_ABDUCTION, "pike_push_up", "db_shoulder_press", "face_pull", "plank_shoulder_taps"),
        "face_pull" to prof(Equipment.CABLES_MACHINES, MovementPattern.UPPER_BACK_PULL, JointAngle.HIGH_EXTERNAL_ROTATION, "scapular_australian_pull_up", "australian_pull_up", "db_row", "barbell_row"),
        "barbell_row" to prof(Equipment.BARBELL_RACK, MovementPattern.HORIZONTAL_PULL, JointAngle.MID_ROW_PRONATED, "db_row", "kb_row", "seated_cable_row", "australian_pull_up", "pull_up"),
        "lat_pulldown" to prof(Equipment.CABLES_MACHINES, MovementPattern.VERTICAL_PULL, JointAngle.OVERHEAD_PRONATED, "pull_up", "chin_up", "negative_pull_up", "australian_pull_up", "db_row"),
        "seated_cable_row" to prof(Equipment.CABLES_MACHINES, MovementPattern.HORIZONTAL_PULL, JointAngle.MID_ROW_PRONATED, "db_row", "barbell_row", "kb_row", "australian_pull_up"),
        "deadlift" to prof(Equipment.BARBELL_RACK, MovementPattern.HIP_HINGE, JointAngle.HIP_HINGE_STANDING, "romanian_deadlift", "kb_deadlift", "kb_swing", "hip_thrust", "hip_bridge"),
        "romanian_deadlift" to prof(Equipment.BARBELL_RACK, MovementPattern.HIP_HINGE, JointAngle.HIP_HINGE_STANDING, "kb_deadlift", "deadlift", "kb_swing", "hip_thrust", "nordic_curl", "hip_bridge"),
        "back_squat" to prof(Equipment.BARBELL_RACK, MovementPattern.SQUAT_BILATERAL, JointAngle.KNEE_DOMINANT_BILATERAL, "goblet_squat", "leg_press", "bulgarian_split_squat", "db_lunge", "squat"),
        "leg_press" to prof(Equipment.CABLES_MACHINES, MovementPattern.SQUAT_BILATERAL, JointAngle.KNEE_DOMINANT_BILATERAL, "back_squat", "goblet_squat", "bulgarian_split_squat", "db_lunge", "squat"),
        "leg_extension" to prof(Equipment.CABLES_MACHINES, MovementPattern.SQUAT_BILATERAL, JointAngle.KNEE_DOMINANT_BILATERAL, "goblet_squat", "bulgarian_split_squat", "squat", "lunge", "pistol_squat"),
        "leg_curl" to prof(Equipment.CABLES_MACHINES, MovementPattern.KNEE_FLEXION, JointAngle.HAMSTRING_CURL, "nordic_curl", "romanian_deadlift", "kb_deadlift", "hip_bridge", "kb_swing"),
        "hip_thrust" to prof(Equipment.BARBELL_RACK, MovementPattern.HIP_HINGE, JointAngle.HIP_EXTENSION_SUPINE, "hip_bridge", "kb_swing", "romanian_deadlift", "kb_deadlift"),
        "standing_calf_raise" to prof(Equipment.CABLES_MACHINES, MovementPattern.ANKLE_FLEXION, JointAngle.PLANTAR_FLEXION, "calf_raise", "calf_raise_turned_out"),
        "bicep_curl" to prof(Equipment.DUMBBELLS, MovementPattern.ELBOW_FLEXION, JointAngle.SAGITTAL_SUPINATED, "hammer_curl", "chin_up", "australian_chin_up"),
        "hammer_curl" to prof(Equipment.DUMBBELLS, MovementPattern.ELBOW_FLEXION, JointAngle.SAGITTAL_NEUTRAL, "bicep_curl", "chin_up", "australian_chin_up", "pull_up"),
        "tricep_pushdown" to prof(Equipment.CABLES_MACHINES, MovementPattern.ELBOW_EXTENSION, JointAngle.SAGITTAL_TRICEP, "skull_crusher", "diamond_push_up", "dip", "bent_leg_bench_dip"),
        "skull_crusher" to prof(Equipment.BARBELL_RACK, MovementPattern.ELBOW_EXTENSION, JointAngle.SAGITTAL_TRICEP, "tricep_pushdown", "diamond_push_up", "dip", "bent_leg_bench_dip"),
        "shrug" to prof(Equipment.DUMBBELLS, MovementPattern.UPPER_BACK_PULL, JointAngle.HIGH_EXTERNAL_ROTATION, "farmers_carry", "face_pull", "scapular_australian_pull_up"),
        "cable_crunch" to prof(Equipment.CABLES_MACHINES, MovementPattern.CORE_FLEXION_ROTATION, JointAngle.SPINAL_FLEXION, "crunch", "hanging_knee_raise", "hanging_leg_raise", "hollow_hold"),
        "tibialis_raise" to prof(Equipment.BODYWEIGHT, MovementPattern.ANKLE_FLEXION, JointAngle.DORSI_FLEXION, "calf_raise", "calf_raise_turned_out"),

        // Kettlebells and dumbbells at home
        "kb_swing" to prof(Equipment.KETTLEBELL, MovementPattern.HIP_HINGE, JointAngle.HIP_HINGE_STANDING, "kb_deadlift", "romanian_deadlift", "hip_bridge", "hip_thrust"),
        "goblet_squat" to prof(Equipment.DUMBBELLS, MovementPattern.SQUAT_BILATERAL, JointAngle.KNEE_DOMINANT_BILATERAL, "back_squat", "bulgarian_split_squat", "db_lunge", "squat"),
        "kb_deadlift" to prof(Equipment.KETTLEBELL, MovementPattern.HIP_HINGE, JointAngle.HIP_HINGE_STANDING, "romanian_deadlift", "deadlift", "kb_swing", "hip_bridge"),
        "kb_clean" to prof(Equipment.KETTLEBELL, MovementPattern.HIP_HINGE, JointAngle.HIP_HINGE_STANDING, "kb_swing", "kb_snatch", "db_shoulder_press"),
        "kb_press" to prof(Equipment.KETTLEBELL, MovementPattern.VERTICAL_PUSH, JointAngle.OVERHEAD_VERTICAL, "db_shoulder_press", "overhead_press", "pike_push_up"),
        "kb_snatch" to prof(Equipment.KETTLEBELL, MovementPattern.VERTICAL_PUSH, JointAngle.OVERHEAD_VERTICAL, "kb_clean", "kb_swing", "db_shoulder_press", "burpee"),
        "kb_row" to prof(Equipment.KETTLEBELL, MovementPattern.HORIZONTAL_PULL, JointAngle.MID_ROW_PRONATED, "db_row", "barbell_row", "australian_pull_up", "seated_cable_row"),
        "turkish_get_up" to prof(Equipment.KETTLEBELL, MovementPattern.CARRY_CONDITIONING, JointAngle.LOCOMOTION, "plank_shoulder_taps", "db_shoulder_press", "plank"),
        "farmers_carry" to prof(Equipment.DUMBBELLS, MovementPattern.CARRY_CONDITIONING, JointAngle.LOCOMOTION, "dead_hang", "shrug", "plank"),
        "db_bench_press" to prof(Equipment.DUMBBELLS, MovementPattern.HORIZONTAL_PUSH, JointAngle.FLAT_HORIZONTAL, "bench_press", "push_up", "weighted_push_up", "incline_db_press", "dip"),
        "db_shoulder_press" to prof(Equipment.DUMBBELLS, MovementPattern.VERTICAL_PUSH, JointAngle.OVERHEAD_VERTICAL, "overhead_press", "kb_press", "pike_push_up", "handstand_push_up"),
        "db_row" to prof(Equipment.DUMBBELLS, MovementPattern.HORIZONTAL_PULL, JointAngle.MID_ROW_PRONATED, "barbell_row", "kb_row", "australian_pull_up", "seated_cable_row", "pull_up"),
        "db_lunge" to prof(Equipment.DUMBBELLS, MovementPattern.SQUAT_UNILATERAL, JointAngle.KNEE_DOMINANT_SPLIT, "bulgarian_split_squat", "lunge", "goblet_squat", "back_squat"),
        "russian_twist" to prof(Equipment.BODYWEIGHT, MovementPattern.CORE_FLEXION_ROTATION, JointAngle.ROTATIONAL, "crunch", "plank_shoulder_taps", "hanging_knee_raise"),
    )

    fun profileFor(ex: ExerciseEntity): BiomechanicalProfile {
        catalogProfiles[ex.id]?.let { return it }
        val lower = (ex.id + " " + ex.name).lowercase()
        val eq = when {
            "cable" in lower || "machine" in lower || "pulldown" in lower || "leg_press" in lower || "pushdown" in lower -> Equipment.CABLES_MACHINES
            "barbell" in lower || "bench_press" in lower || "back_squat" in lower || "deadlift" in lower -> Equipment.BARBELL_RACK
            "kb_" in lower || "kettlebell" in lower -> Equipment.KETTLEBELL
            "db_" in lower || "dumbbell" in lower || "curl" in lower || "lateral" in lower -> Equipment.DUMBBELLS
            "pull_up" in lower || "chin_up" in lower || "hang" in lower || "lever" in lower || "muscle_up" in lower -> Equipment.PULL_UP_BAR
            "dip" in lower || "australian" in lower || "row" in lower -> Equipment.DIP_BARS_OR_BENCH
            ex.kind == ExerciseKind.WEIGHTED -> Equipment.DUMBBELLS
            else -> Equipment.BODYWEIGHT
        }
        val mainMuscle = ex.primary.firstOrNull()
        val pattern = when {
            "pike" in lower || "handstand" in lower || "overhead" in lower || "shoulder_press" in lower -> MovementPattern.VERTICAL_PUSH
            "incline" in lower && "push" in lower -> MovementPattern.HORIZONTAL_PUSH
            "dip" in lower -> MovementPattern.DIP_PUSH
            "push_up" in lower || "bench" in lower || "planche" in lower || mainMuscle == Muscle.CHEST -> MovementPattern.HORIZONTAL_PUSH
            "pull_up" in lower || "chin_up" in lower || "pulldown" in lower -> MovementPattern.VERTICAL_PULL
            "row" in lower || "australian" in lower || "lever" in lower || mainMuscle == Muscle.UPPER_BACK -> MovementPattern.HORIZONTAL_PULL
            "split" in lower || "lunge" in lower || "pistol" in lower -> MovementPattern.SQUAT_UNILATERAL
            "squat" in lower || "leg_press" in lower || mainMuscle == Muscle.QUADS -> MovementPattern.SQUAT_BILATERAL
            "curl" in lower && mainMuscle == Muscle.HAMSTRINGS -> MovementPattern.KNEE_FLEXION
            "deadlift" in lower || "bridge" in lower || "thrust" in lower || "swing" in lower || mainMuscle == Muscle.GLUTES || mainMuscle == Muscle.HAMSTRINGS || mainMuscle == Muscle.LOWER_BACK -> MovementPattern.HIP_HINGE
            mainMuscle == Muscle.BICEPS -> MovementPattern.ELBOW_FLEXION
            mainMuscle == Muscle.TRICEPS -> MovementPattern.ELBOW_EXTENSION
            ex.kind == ExerciseKind.HOLD && (mainMuscle == Muscle.ABS || mainMuscle == Muscle.OBLIQUES) -> MovementPattern.CORE_ANTI_EXTENSION
            mainMuscle == Muscle.ABS || mainMuscle == Muscle.OBLIQUES -> MovementPattern.CORE_FLEXION_ROTATION
            mainMuscle == Muscle.CALVES || mainMuscle == Muscle.TIBIALIS -> MovementPattern.ANKLE_FLEXION
            mainMuscle == Muscle.SHOULDERS -> MovementPattern.VERTICAL_PUSH
            else -> MovementPattern.CARRY_CONDITIONING
        }
        val angle = when (pattern) {
            MovementPattern.HORIZONTAL_PUSH -> if ("incline" in lower) JointAngle.INCLINE_UPWARD else JointAngle.FLAT_HORIZONTAL
            MovementPattern.DIP_PUSH -> JointAngle.DECLINE_DOWNWARD
            MovementPattern.VERTICAL_PUSH -> JointAngle.OVERHEAD_VERTICAL
            MovementPattern.LATERAL_RAISE -> JointAngle.FRONTAL_ABDUCTION
            MovementPattern.VERTICAL_PULL -> if ("chin" in lower) JointAngle.OVERHEAD_SUPINATED else JointAngle.OVERHEAD_PRONATED
            MovementPattern.HORIZONTAL_PULL -> if ("chin" in lower) JointAngle.MID_ROW_SUPINATED else JointAngle.MID_ROW_PRONATED
            MovementPattern.UPPER_BACK_PULL -> JointAngle.HIGH_EXTERNAL_ROTATION
            MovementPattern.SQUAT_BILATERAL -> JointAngle.KNEE_DOMINANT_BILATERAL
            MovementPattern.SQUAT_UNILATERAL -> JointAngle.KNEE_DOMINANT_SPLIT
            MovementPattern.HIP_HINGE -> if ("bridge" in lower || "thrust" in lower) JointAngle.HIP_EXTENSION_SUPINE else JointAngle.HIP_HINGE_STANDING
            MovementPattern.KNEE_FLEXION -> JointAngle.HAMSTRING_CURL
            MovementPattern.ELBOW_FLEXION -> JointAngle.SAGITTAL_SUPINATED
            MovementPattern.ELBOW_EXTENSION -> JointAngle.SAGITTAL_TRICEP
            MovementPattern.CORE_ANTI_EXTENSION -> JointAngle.ISOMETRIC_PLANK
            MovementPattern.CORE_FLEXION_ROTATION -> JointAngle.SPINAL_FLEXION
            MovementPattern.ANKLE_FLEXION -> JointAngle.PLANTAR_FLEXION
            MovementPattern.CARRY_CONDITIONING -> JointAngle.LOCOMOTION
        }
        return BiomechanicalProfile(eq, pattern, angle)
    }

    fun rankSubstitutes(
        source: ExerciseEntity,
        library: List<ExerciseEntity>,
        userEquipment: Set<Equipment> = Equipment.entries.toSet(),
    ): List<SwapCandidate> {
        val srcProf = profileFor(source)
        val effectiveEquipment = userEquipment.ifEmpty { Equipment.entries.toSet() } + Equipment.BODYWEIGHT
        return library
            .filter { it.id != source.id }
            .map { candidate ->
                val candProf = profileFor(candidate)
                val patternPts = when {
                    candProf.pattern == srcProf.pattern -> 45
                    areSynergisticPatterns(srcProf.pattern, candProf.pattern) -> 18
                    else -> 0
                }
                val anglePts = if (candProf.angle == srcProf.angle) 20 else 0
                val primaryPts = (20f * jaccard(source.primary, candidate.primary)).toInt()
                val secondaryPts = (10f * jaccard(source.primary + source.secondary, candidate.primary + candidate.secondary)).toInt()
                val kindPts = if (source.kind == candidate.kind) 5 else 0
                val curatedIdx = srcProf.preferredSwaps.indexOf(candidate.id)
                val curatedBonus = if (curatedIdx >= 0) max(3, 15 - curatedIdx * 2) else 0
                val totalScore = patternPts + anglePts + primaryPts + secondaryPts + kindPts + curatedBonus
                val hasEq = candProf.equipment in effectiveEquipment
                SwapCandidate(
                    exercise = candidate,
                    profile = candProf,
                    score = totalScore,
                    matchesUserEquipment = hasEq,
                )
            }
            .sortedWith(
                compareByDescending<SwapCandidate> { it.matchesUserEquipment && it.score >= 45 }
                    .thenByDescending { it.score }
                    .thenBy { it.exercise.name }
            )
    }

    private fun areSynergisticPatterns(a: MovementPattern, b: MovementPattern): Boolean {
        val pairs = setOf(
            MovementPattern.HORIZONTAL_PUSH to MovementPattern.DIP_PUSH,
            MovementPattern.DIP_PUSH to MovementPattern.ELBOW_EXTENSION,
            MovementPattern.VERTICAL_PUSH to MovementPattern.LATERAL_RAISE,
            MovementPattern.VERTICAL_PULL to MovementPattern.HORIZONTAL_PULL,
            MovementPattern.HORIZONTAL_PULL to MovementPattern.UPPER_BACK_PULL,
            MovementPattern.SQUAT_BILATERAL to MovementPattern.SQUAT_UNILATERAL,
            MovementPattern.HIP_HINGE to MovementPattern.KNEE_FLEXION,
            MovementPattern.CORE_ANTI_EXTENSION to MovementPattern.CORE_FLEXION_ROTATION,
        )
        return (a to b) in pairs || (b to a) in pairs
    }

    private fun <T> jaccard(a: List<T>, b: List<T>): Float {
        if (a.isEmpty() && b.isEmpty()) return 0f
        val sa = a.toSet()
        val sb = b.toSet()
        val inter = sa.intersect(sb).size
        val union = sa.union(sb).size
        return if (union == 0) 0f else inter.toFloat() / union
    }

    fun translatePlanItem(oldItem: PlanItemEntity, newExercise: ExerciseEntity): PlanItemEntity {
        return when (newExercise.kind) {
            ExerciseKind.HOLD -> oldItem.copy(
                exerciseId = newExercise.id,
                seconds = oldItem.seconds ?: 30,
                repsMin = null,
                repsMax = null,
                weightKg = null,
            )
            ExerciseKind.REPS -> {
                val baseMin = oldItem.repsMin ?: 10
                val baseMax = oldItem.repsMax ?: max(baseMin, 12)
                val bumpedMin = if (oldItem.weightKg != null && oldItem.weightKg > 0f) max(8, baseMin + 2) else baseMin
                val bumpedMax = if (oldItem.weightKg != null && oldItem.weightKg > 0f) max(12, baseMax + 2) else baseMax
                oldItem.copy(
                    exerciseId = newExercise.id,
                    repsMin = bumpedMin,
                    repsMax = bumpedMax,
                    seconds = null,
                    weightKg = null,
                )
            }
            ExerciseKind.WEIGHTED -> oldItem.copy(
                exerciseId = newExercise.id,
                repsMin = oldItem.repsMin ?: 8,
                repsMax = oldItem.repsMax ?: 12,
                seconds = null,
                // Never carry a load across exercises (100 kg back squat is not a 100 kg goblet squat): the first
                // session starts from the new exercise's own history, like translateDraftItem.
                weightKg = 0f,
                restSeconds = max(oldItem.restSeconds, 90),
            )
        }
    }

    fun adaptPlanToEquipment(
        draft: PlanDraft,
        library: Map<String, ExerciseEntity>,
        userEquipment: Set<Equipment>,
    ): Pair<PlanDraft, Int> {
        val allowed = userEquipment.ifEmpty { return draft to 0 } + Equipment.BODYWEIGHT
        if (allowed.containsAll(Equipment.entries)) return draft to 0
        val allExercises = library.values.toList()
        var swapsCount = 0
        val adaptedDays = draft.days.map { day ->
            if (day.isRest || day.items.isEmpty()) return@map day
            val usedInDay = day.items.map { it.exerciseId }.toMutableSet()
            val newItems = day.items.map { item ->
                val ex = library[item.exerciseId] ?: ExerciseEntity(
                    id = item.exerciseId,
                    name = item.name ?: item.exerciseId,
                    kind = runCatching { ExerciseKind.valueOf(item.kind ?: "") }.getOrDefault(
                        if (item.seconds != null) ExerciseKind.HOLD else if (item.weightKg != null) ExerciseKind.WEIGHTED else ExerciseKind.REPS
                    ),
                    primary = item.muscles.mapNotNull { m -> runCatching { Muscle.valueOf(m) }.getOrNull() }.take(2),
                    secondary = emptyList(),
                )
                val prof = profileFor(ex)
                if (prof.equipment in allowed) return@map item
                val replacement = rankSubstitutes(ex, allExercises, allowed)
                    .firstOrNull { it.matchesUserEquipment && it.exercise.id !in usedInDay }
                    ?: rankSubstitutes(ex, allExercises, allowed).firstOrNull { it.matchesUserEquipment }
                    ?: return@map item
                swapsCount++
                usedInDay += replacement.exercise.id
                translateDraftItem(item, replacement.exercise)
            }
            day.copy(items = newItems)
        }
        return draft.copy(days = adaptedDays) to swapsCount
    }

    fun translateDraftItem(item: PlanDraftItem, target: ExerciseEntity): PlanDraftItem = when (target.kind) {
        ExerciseKind.HOLD -> item.copy(
            exerciseId = target.id,
            name = null,
            kind = null,
            muscles = emptyList(),
            seconds = item.seconds ?: 30,
            repsMin = null,
            repsMax = null,
            weightKg = null,
        )
        ExerciseKind.REPS -> item.copy(
            exerciseId = target.id,
            name = null,
            kind = null,
            muscles = emptyList(),
            repsMin = item.repsMin ?: 10,
            repsMax = item.repsMax ?: 12,
            seconds = null,
            weightKg = null,
        )
        ExerciseKind.WEIGHTED -> item.copy(
            exerciseId = target.id,
            name = null,
            kind = null,
            muscles = emptyList(),
            repsMin = item.repsMin ?: 8,
            repsMax = item.repsMax ?: 12,
            seconds = null,
            weightKg = null,
            restSeconds = max(item.restSeconds, 90),
        )
    }
}