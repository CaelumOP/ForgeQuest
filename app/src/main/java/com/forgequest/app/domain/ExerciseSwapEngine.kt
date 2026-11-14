package com.forgequest.app.domain

import com.forgequest.app.data.db.ExerciseEntity
import com.forgequest.app.data.db.PlanItemEntity
import com.forgequest.app.data.plan.PlanDraft
import com.forgequest.app.data.plan.PlanDraftItem
import com.forgequest.app.data.seed.ExerciseCatalog
import kotlin.math.max

/** Equipment categories for filtering and biomechanical exercise substitution. */
enum class EquipmentGroup(val labelEn: String, val labelAr: String) {
    FREE_WEIGHTS("Free weights & bands", "أوزان حرة وأحزمة"),
    BENCHES_RACKS("Benches & racks", "مقاعد وحوامل"),
    BARS("Bars", "العقلة والمتوازي"),
    UPPER_MACHINES("Upper cables & machines", "كوابل وأجهزة علوي"),
    LOWER_MACHINES("Lower machines", "أجهزة سفلي"),
}

enum class Equipment(
    val labelEn: String,
    val labelAr: String,
    val group: EquipmentGroup? = null,
    val legacy: Boolean = false,
) {
    BODYWEIGHT("Bodyweight", "وزن الجسم"),

    // Free weights & bands
    DUMBBELLS("Dumbbells", "دمبل", EquipmentGroup.FREE_WEIGHTS),
    KETTLEBELL("Kettlebell", "كيتل بيل", EquipmentGroup.FREE_WEIGHTS),
    BARBELL("Barbell", "بار حديد", EquipmentGroup.FREE_WEIGHTS),
    BAND("Bands", "أحزمة مطاطية", EquipmentGroup.FREE_WEIGHTS),

    // Benches & racks
    SQUAT_RACK("Squat rack", "حامل سكوات", EquipmentGroup.BENCHES_RACKS),
    FLAT_BENCH("Flat bench", "مقعد مستوٍ", EquipmentGroup.BENCHES_RACKS),
    ADJ_BENCH("Adj. bench", "مقعد متغيّر", EquipmentGroup.BENCHES_RACKS),
    ROMAN_CHAIR("Roman chair", "كرسي ظهر", EquipmentGroup.BENCHES_RACKS),

    // Bars
    PULL_UP_BAR("Pull-up bar", "عقلة", EquipmentGroup.BARS),
    DIP_STATION("Dip station", "متوازي", EquipmentGroup.BARS),
    LOW_BAR("Low bar", "بار منخفض", EquipmentGroup.BARS),

    // Upper cables & machines
    CABLE_MACHINE("Cable machine", "جهاز كابل", EquipmentGroup.UPPER_MACHINES),
    LAT_PULLDOWN("Lat pulldown", "سحب علوي", EquipmentGroup.UPPER_MACHINES),
    SEATED_ROW_MACHINE("Seated row", "تجديف جالس", EquipmentGroup.UPPER_MACHINES),
    SMITH_MACHINE("Smith machine", "جهاز سميث", EquipmentGroup.UPPER_MACHINES),
    CHEST_PRESS_MACHINE("Chest press", "جهاز صدر", EquipmentGroup.UPPER_MACHINES),
    SHOULDER_PRESS_MACHINE("Shoulder press", "جهاز أكتاف", EquipmentGroup.UPPER_MACHINES),
    PEC_DECK("Pec deck", "جهاز تفتيح", EquipmentGroup.UPPER_MACHINES),
    PREACHER_CURL_MACHINE("Preacher curl", "جهاز بايسبس", EquipmentGroup.UPPER_MACHINES),

    // Lower machines
    LEG_PRESS("Leg press", "دفع أرجل", EquipmentGroup.LOWER_MACHINES),
    HACK_SQUAT("Hack squat", "هاك سكوات", EquipmentGroup.LOWER_MACHINES),
    LEG_EXTENSION("Leg extension", "مد أرجل", EquipmentGroup.LOWER_MACHINES),
    LEG_CURL("Leg curl", "ثني أرجل", EquipmentGroup.LOWER_MACHINES),
    CALF_MACHINE("Calf machine", "جهاز بطة", EquipmentGroup.LOWER_MACHINES),

    // Legacy grouped values preserved for old DataStore/backup decoding; never shown in pickers.
    DIP_BARS_OR_BENCH("Dip bars / rings / bench", "متوازي أو حلقات أو مقعد", legacy = true),
    BARBELL_RACK("Barbell & rack", "بار وراك", legacy = true),
    CABLES_MACHINES("Cables & gym machines", "أجهزة وكوابل النادي", legacy = true);

    companion object {
        val selectableEntries: List<Equipment> by lazy {
            entries.filter { !it.legacy && it != BODYWEIGHT }
        }

        val allActive: Set<Equipment> by lazy {
            entries.filter { !it.legacy }.toSet()
        }
    }
}

fun expandLegacyEquipment(raw: Set<Equipment>): Set<Equipment> {
    if (raw.none { it.legacy }) return raw
    val out = mutableSetOf<Equipment>()
    raw.forEach { eq ->
        when (eq) {
            Equipment.DIP_BARS_OR_BENCH -> out += setOf(
                Equipment.DIP_STATION, Equipment.LOW_BAR, Equipment.FLAT_BENCH, Equipment.ROMAN_CHAIR,
            )
            Equipment.BARBELL_RACK -> out += setOf(
                Equipment.BARBELL, Equipment.SQUAT_RACK, Equipment.FLAT_BENCH, Equipment.ADJ_BENCH,
            )
            Equipment.CABLES_MACHINES -> out += Equipment.selectableEntries.filter {
                it.group == EquipmentGroup.UPPER_MACHINES || it.group == EquipmentGroup.LOWER_MACHINES
            }
            else -> out += eq
        }
    }
    return out
}

/** Parses a comma-separated equipment setting string into a Set<Equipment>. Empty string means "not configured". */
fun parseEquipmentSet(raw: String): Set<Equipment> {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return emptySet()
    if (trimmed.equals("all", ignoreCase = true) || trimmed.equals("full_gym", ignoreCase = true)) {
        return Equipment.allActive
    }
    EquipmentPreset.entries.firstOrNull { it.id.equals(trimmed, ignoreCase = true) }?.let { return it.equipment }
    val parsed = trimmed.split(",")
        .mapNotNull { token -> runCatching { Equipment.valueOf(token.trim().uppercase()) }.getOrNull() }
        .toSet()
    return if (parsed.isEmpty()) emptySet() else expandLegacyEquipment(parsed) + Equipment.BODYWEIGHT
}

/**
 * Scans coach memory notes and user messages for explicit equipment mentions so the coach knows whether
 * equipment intake has already been answered.
 */
fun inferEquipmentFromText(vararg texts: String): Set<Equipment>? {
    val combined = texts.joinToString("\n").lowercase()
    if (combined.isBlank()) return null

    if (words("""full gym|commercial gym|gym member|all equipment|نادي متكامل|مشترك في نادي|في النادي|جيم كامل""").containsMatchIn(combined)) {
        return Equipment.allActive
    }
    if (words("""no equipment|bodyweight only|pure bodyweight|nothing at home|بدون معدات|وزن الجسم فقط|وزن جسمي فقط|ما عندي معدات|لا أملك معدات""").containsMatchIn(combined)) {
        return setOf(Equipment.BODYWEIGHT)
    }

    val found = mutableSetOf<Equipment>()
    if (words("""pull-?up bar|chin-?up bar|doorway bar|عقلة|بار عقلة""").hasPositive(combined)) {
        found += Equipment.PULL_UP_BAR
    }
    if (words("""dip bar|dip station|parallel bar|rings|gymnastic rings|متوازي|حلقات""").hasPositive(combined)) {
        found += Equipment.DIP_STATION
    }
    if (words("""dumbbell|dumbbells|دمبل|دامبل""").hasPositive(combined)) {
        found += Equipment.DUMBBELLS
    }
    if (words("""kettlebell|kettlebells|كيتل بيل|كيتلبيل""").hasPositive(combined)) {
        found += Equipment.KETTLEBELL
    }
    if (words("""band|bands|resistance band|حزام مطاطي|أحزمة مطاطية""").hasPositive(combined)) {
        found += Equipment.BAND
    }
    // Not a lone «بار»: «بار عقلة» is a pull-up bar.
    if (words("""barbell|باربل|بار حديد|بار أولمبي""").hasPositive(combined)) {
        found += Equipment.BARBELL
    }
    if (words("""squat rack|power rack|راك""").hasPositive(combined)) {
        found += Equipment.SQUAT_RACK
    }
    if (words("""bench press""").hasPositive(combined)) {
        found += Equipment.BARBELL
        found += Equipment.FLAT_BENCH
    }
    if (words("""flat bench|adjustable bench|weight bench|بنش|مقعد""").hasPositive(combined)) {
        found += Equipment.FLAT_BENCH
    }
    // Not a lone «أجهزة»: it means any device.
    if (words("""cable|cables|كيبل|كوابل""").hasPositive(combined)) {
        found += Equipment.CABLE_MACHINE
    }
    if (words("""lat pulldown|سحب علوي""").hasPositive(combined)) {
        found += Equipment.LAT_PULLDOWN
    }
    if (words("""leg press|دفع أرجل""").hasPositive(combined)) {
        found += Equipment.LEG_PRESS
    }
    if (words("""smith machine|جهاز سميث""").hasPositive(combined)) {
        found += Equipment.SMITH_MACHINE
    }
    if (words("""أجهزة النادي|أجهزة الجيم""").hasPositive(combined)) {
        found += Equipment.selectableEntries.filter {
            it.group == EquipmentGroup.UPPER_MACHINES || it.group == EquipmentGroup.LOWER_MACHINES
        }
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

/** High-level training locations for A2 Plan Builder & Gear sheet. */
enum class TrainingPlace(
    val id: String,
    val labelEn: String,
    val labelAr: String,
    val descEn: String,
    val descAr: String,
    val equipment: Set<Equipment>,
) {
    GYM(
        id = "full_gym",
        labelEn = "Gym",
        labelAr = "نادي رياضي",
        descEn = "Machines, cables, benches & free weights",
        descAr = "أجهزة كاملة، كوابل، مقاعد وأوزان حرة",
        equipment = Equipment.allActive,
    ),
    HOME(
        id = "home_gym",
        labelEn = "Home gym",
        labelAr = "نادي منزلي",
        descEn = "Free weights, benches, rack & bars",
        descAr = "أوزان حرة، مقاعد، حامل سكوات وعقلة",
        equipment = setOf(
            Equipment.BODYWEIGHT,
            Equipment.DUMBBELLS, Equipment.KETTLEBELL, Equipment.BARBELL, Equipment.BAND,
            Equipment.SQUAT_RACK, Equipment.FLAT_BENCH, Equipment.ADJ_BENCH,
            Equipment.PULL_UP_BAR, Equipment.DIP_STATION, Equipment.LOW_BAR,
        ),
    ),
    NONE(
        id = "bodyweight",
        labelEn = "No gear",
        labelAr = "بدون معدات",
        descEn = "Bodyweight only",
        descAr = "وزن الجسم فقط",
        equipment = setOf(Equipment.BODYWEIGHT),
    );

    companion object {
        fun match(equipment: Set<Equipment>): TrainingPlace? {
            val normalized = expandLegacyEquipment(equipment) + Equipment.BODYWEIGHT
            return entries.firstOrNull { it.equipment == normalized }
        }
    }
}

/** Quick presets for Onboarding and legacy saved token compatibility. */
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
        equipment = TrainingPlace.GYM.equipment,
    ),
    HOME_GYM(
        id = "home_gym",
        labelEn = "Home gym (barbell, dumbbells, bar)",
        labelAr = "نادي منزلي (بار، دمبل، عقلة)",
        equipment = TrainingPlace.HOME.equipment,
    ),
    DUMBBELLS_AND_BAR(
        id = "dumbbells_bar",
        labelEn = "Dumbbells & pull-up bar",
        labelAr = "دمبل وعقلة",
        equipment = setOf(
            Equipment.BODYWEIGHT, Equipment.DUMBBELLS, Equipment.PULL_UP_BAR,
            Equipment.DIP_STATION, Equipment.LOW_BAR, Equipment.FLAT_BENCH,
        ),
    ),
    DUMBBELLS_AND_KETTLEBELL(
        id = "dumbbells_kb",
        labelEn = "Dumbbells & kettlebell",
        labelAr = "دمبل وكيتلبل",
        equipment = setOf(Equipment.BODYWEIGHT, Equipment.DUMBBELLS, Equipment.KETTLEBELL),
    ),
    CALISTHENICS_PARK(
        id = "calisthenics",
        labelEn = "Calisthenics (pull-up bar & dip bars)",
        labelAr = "كاليستنكس (عقلة ومتوازي)",
        equipment = setOf(
            Equipment.BODYWEIGHT, Equipment.PULL_UP_BAR, Equipment.DIP_STATION,
            Equipment.LOW_BAR, Equipment.FLAT_BENCH,
        ),
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
    HIP_ADDUCTION("Hip adduction (inner thigh)", "ضمّ الورك (باطن الفخذ)"),
    MOBILITY("Stretch / mobility", "تمدد ومرونة"),
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
    FRONTAL_ADDUCTION,
    STRETCH,
}

data class BiomechanicalProfile(
    val equipment: Equipment,
    val pattern: MovementPattern,
    val angle: JointAngle,
    val preferredSwaps: List<String> = emptyList(),
    val requires: Set<Equipment> = setOf(equipment),
) {
    fun fits(available: Set<Equipment>): Boolean = requires.all { req ->
        req in available || (req == Equipment.FLAT_BENCH && Equipment.ADJ_BENCH in available)
    }
}

data class SwapCandidate(
    val exercise: ExerciseEntity,
    val profile: BiomechanicalProfile,
    val score: Int,
    val matchesUserEquipment: Boolean,
)

object ExerciseSwapEngine {
    /** Lowest score a plan auto-swap accepts: a related movement (18) plus some shared muscles, or the same pattern. */
    const val MIN_SWAP_SCORE = 30


    private fun prof(
        eq: Equipment,
        pat: MovementPattern,
        ang: JointAngle,
        vararg swaps: String,
    ) = BiomechanicalProfile(eq, pat, ang, swaps.toList())

    private fun profReq(
        eq: Equipment,
        req: Set<Equipment>,
        pat: MovementPattern,
        ang: JointAngle,
        vararg swaps: String,
    ) = BiomechanicalProfile(eq, pat, ang, swaps.toList(), requires = req + eq)

    /** Explicit biomechanical profiles for all 186 built-in exercises in ExerciseCatalog. */
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
        "scapular_australian_pull_up" to prof(Equipment.LOW_BAR, MovementPattern.HORIZONTAL_PULL, JointAngle.MID_ROW_PRONATED, "australian_pull_up", "dead_hang", "db_row"),
        "bent_leg_bench_dip" to prof(Equipment.FLAT_BENCH, MovementPattern.DIP_PUSH, JointAngle.DECLINE_DOWNWARD, "reverse_grip_bench_dip", "dip", "diamond_push_up", "incline_diamond_knee_push_up"),
        "scapular_australian_chin_up" to prof(Equipment.LOW_BAR, MovementPattern.HORIZONTAL_PULL, JointAngle.MID_ROW_SUPINATED, "australian_chin_up", "scapular_australian_pull_up", "chin_up"),
        "knee_push_up" to prof(Equipment.BODYWEIGHT, MovementPattern.HORIZONTAL_PUSH, JointAngle.FLAT_HORIZONTAL, "wide_knee_push_up", "push_up", "incline_diamond_knee_push_up", "db_bench_press"),
        "australian_pull_up" to prof(Equipment.LOW_BAR, MovementPattern.HORIZONTAL_PULL, JointAngle.MID_ROW_PRONATED, "db_row", "kb_row", "barbell_row", "seated_cable_row", "negative_pull_up", "pull_up"),
        "reverse_grip_bench_dip" to prof(Equipment.FLAT_BENCH, MovementPattern.DIP_PUSH, JointAngle.DECLINE_DOWNWARD, "bent_leg_bench_dip", "dip", "diamond_push_up"),
        "australian_chin_up" to prof(Equipment.LOW_BAR, MovementPattern.HORIZONTAL_PULL, JointAngle.MID_ROW_SUPINATED, "australian_pull_up", "chin_up", "negative_pull_up", "db_row"),
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
        "dip" to prof(Equipment.DIP_STATION, MovementPattern.DIP_PUSH, JointAngle.DECLINE_DOWNWARD, "weighted_dip", "push_up", "diamond_push_up", "db_bench_press", "bench_press", "bent_leg_bench_dip"),
        "squat" to prof(Equipment.BODYWEIGHT, MovementPattern.SQUAT_BILATERAL, JointAngle.KNEE_DOMINANT_BILATERAL, "goblet_squat", "back_squat", "lunge", "bulgarian_split_squat", "leg_press"),
        "lunge" to prof(Equipment.BODYWEIGHT, MovementPattern.SQUAT_UNILATERAL, JointAngle.KNEE_DOMINANT_SPLIT, "db_lunge", "bulgarian_split_squat", "squat", "goblet_squat"),
        "bulgarian_split_squat" to prof(Equipment.FLAT_BENCH, MovementPattern.SQUAT_UNILATERAL, JointAngle.KNEE_DOMINANT_SPLIT, "db_lunge", "lunge", "pistol_squat", "goblet_squat", "back_squat"),
        "pistol_squat" to prof(Equipment.BODYWEIGHT, MovementPattern.SQUAT_UNILATERAL, JointAngle.KNEE_DOMINANT_SPLIT, "bulgarian_split_squat", "db_lunge", "lunge", "goblet_squat"),
        "nordic_curl" to prof(Equipment.BODYWEIGHT, MovementPattern.KNEE_FLEXION, JointAngle.HAMSTRING_CURL, "leg_curl", "romanian_deadlift", "hip_bridge", "kb_swing"),
        "plank" to prof(Equipment.BODYWEIGHT, MovementPattern.CORE_ANTI_EXTENSION, JointAngle.ISOMETRIC_PLANK, "hollow_hold", "incline_hollow_plank", "plank_shoulder_taps", "l_sit"),
        "hollow_hold" to prof(Equipment.BODYWEIGHT, MovementPattern.CORE_ANTI_EXTENSION, JointAngle.ISOMETRIC_PLANK, "plank", "l_sit", "incline_hollow_plank", "hanging_knee_raise"),
        "hanging_knee_raise" to prof(Equipment.PULL_UP_BAR, MovementPattern.CORE_FLEXION_ROTATION, JointAngle.HANGING_HIP_FLEXION, "hanging_leg_raise", "crunch", "hollow_hold", "l_sit"),
        "hanging_leg_raise" to prof(Equipment.PULL_UP_BAR, MovementPattern.CORE_FLEXION_ROTATION, JointAngle.HANGING_HIP_FLEXION, "hanging_knee_raise", "l_sit", "hollow_hold", "cable_crunch"),

        // Calisthenics skills
        "muscle_up" to prof(Equipment.PULL_UP_BAR, MovementPattern.VERTICAL_PULL, JointAngle.OVERHEAD_PRONATED, "weighted_pull_up", "pull_up", "dip"),
        "l_sit" to prof(Equipment.DIP_STATION, MovementPattern.CORE_ANTI_EXTENSION, JointAngle.ISOMETRIC_PLANK, "hollow_hold", "hanging_leg_raise", "plank"),
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
        "weighted_dip" to prof(Equipment.DIP_STATION, MovementPattern.DIP_PUSH, JointAngle.DECLINE_DOWNWARD, "dip", "bench_press", "db_bench_press", "weighted_push_up"),
        "weighted_push_up" to prof(Equipment.BODYWEIGHT, MovementPattern.HORIZONTAL_PUSH, JointAngle.FLAT_HORIZONTAL, "push_up", "bench_press", "db_bench_press", "pseudo_planche_push_up"),

        // Gym (Barbell, Dumbbells, Cables & Machines)
        "bench_press" to profReq(Equipment.BARBELL, setOf(Equipment.BARBELL, Equipment.FLAT_BENCH), MovementPattern.HORIZONTAL_PUSH, JointAngle.FLAT_HORIZONTAL, "db_bench_press", "weighted_push_up", "push_up", "dip", "pseudo_planche_push_up"),
        "incline_db_press" to profReq(Equipment.DUMBBELLS, setOf(Equipment.DUMBBELLS, Equipment.ADJ_BENCH), MovementPattern.HORIZONTAL_PUSH, JointAngle.INCLINE_UPWARD, "db_bench_press", "bench_press", "pike_push_up", "push_up", "incline_diamond_knee_push_up"),
        "overhead_press" to prof(Equipment.BARBELL, MovementPattern.VERTICAL_PUSH, JointAngle.OVERHEAD_VERTICAL, "db_shoulder_press", "kb_press", "pike_push_up", "handstand_push_up"),
        "lateral_raise" to prof(Equipment.DUMBBELLS, MovementPattern.LATERAL_RAISE, JointAngle.FRONTAL_ABDUCTION, "pike_push_up", "db_shoulder_press", "face_pull", "plank_shoulder_taps"),
        "face_pull" to prof(Equipment.CABLE_MACHINE, MovementPattern.UPPER_BACK_PULL, JointAngle.HIGH_EXTERNAL_ROTATION, "scapular_australian_pull_up", "australian_pull_up", "db_row", "barbell_row"),
        "barbell_row" to prof(Equipment.BARBELL, MovementPattern.HORIZONTAL_PULL, JointAngle.MID_ROW_PRONATED, "db_row", "kb_row", "seated_cable_row", "australian_pull_up", "pull_up"),
        "lat_pulldown" to prof(Equipment.LAT_PULLDOWN, MovementPattern.VERTICAL_PULL, JointAngle.OVERHEAD_PRONATED, "pull_up", "chin_up", "negative_pull_up", "australian_pull_up", "db_row"),
        "seated_cable_row" to prof(Equipment.SEATED_ROW_MACHINE, MovementPattern.HORIZONTAL_PULL, JointAngle.MID_ROW_PRONATED, "db_row", "barbell_row", "kb_row", "australian_pull_up"),
        "deadlift" to prof(Equipment.BARBELL, MovementPattern.HIP_HINGE, JointAngle.HIP_HINGE_STANDING, "romanian_deadlift", "kb_deadlift", "kb_swing", "hip_thrust", "hip_bridge"),
        "romanian_deadlift" to prof(Equipment.BARBELL, MovementPattern.HIP_HINGE, JointAngle.HIP_HINGE_STANDING, "kb_deadlift", "deadlift", "kb_swing", "hip_thrust", "nordic_curl", "hip_bridge"),
        "back_squat" to profReq(Equipment.BARBELL, setOf(Equipment.BARBELL, Equipment.SQUAT_RACK), MovementPattern.SQUAT_BILATERAL, JointAngle.KNEE_DOMINANT_BILATERAL, "goblet_squat", "leg_press", "bulgarian_split_squat", "db_lunge", "squat"),
        "leg_press" to prof(Equipment.LEG_PRESS, MovementPattern.SQUAT_BILATERAL, JointAngle.KNEE_DOMINANT_BILATERAL, "back_squat", "goblet_squat", "bulgarian_split_squat", "db_lunge", "squat"),
        "leg_extension" to prof(Equipment.LEG_EXTENSION, MovementPattern.SQUAT_BILATERAL, JointAngle.KNEE_DOMINANT_BILATERAL, "goblet_squat", "bulgarian_split_squat", "squat", "lunge", "pistol_squat"),
        "leg_curl" to prof(Equipment.LEG_CURL, MovementPattern.KNEE_FLEXION, JointAngle.HAMSTRING_CURL, "nordic_curl", "romanian_deadlift", "kb_deadlift", "hip_bridge", "kb_swing"),
        "hip_thrust" to profReq(Equipment.BARBELL, setOf(Equipment.BARBELL, Equipment.FLAT_BENCH), MovementPattern.HIP_HINGE, JointAngle.HIP_EXTENSION_SUPINE, "hip_bridge", "kb_swing", "romanian_deadlift", "kb_deadlift"),
        "standing_calf_raise" to prof(Equipment.CALF_MACHINE, MovementPattern.ANKLE_FLEXION, JointAngle.PLANTAR_FLEXION, "calf_raise", "calf_raise_turned_out"),
        "bicep_curl" to prof(Equipment.DUMBBELLS, MovementPattern.ELBOW_FLEXION, JointAngle.SAGITTAL_SUPINATED, "hammer_curl", "chin_up", "australian_chin_up"),
        "hammer_curl" to prof(Equipment.DUMBBELLS, MovementPattern.ELBOW_FLEXION, JointAngle.SAGITTAL_NEUTRAL, "bicep_curl", "chin_up", "australian_chin_up", "pull_up"),
        "tricep_pushdown" to prof(Equipment.CABLE_MACHINE, MovementPattern.ELBOW_EXTENSION, JointAngle.SAGITTAL_TRICEP, "skull_crusher", "diamond_push_up", "dip", "bent_leg_bench_dip"),
        "skull_crusher" to profReq(Equipment.BARBELL, setOf(Equipment.BARBELL, Equipment.FLAT_BENCH), MovementPattern.ELBOW_EXTENSION, JointAngle.SAGITTAL_TRICEP, "tricep_pushdown", "diamond_push_up", "dip", "bent_leg_bench_dip"),
        "shrug" to prof(Equipment.DUMBBELLS, MovementPattern.UPPER_BACK_PULL, JointAngle.HIGH_EXTERNAL_ROTATION, "farmers_carry", "face_pull", "scapular_australian_pull_up"),
        "cable_crunch" to prof(Equipment.CABLE_MACHINE, MovementPattern.CORE_FLEXION_ROTATION, JointAngle.SPINAL_FLEXION, "crunch", "hanging_knee_raise", "hanging_leg_raise", "hollow_hold"),
        "tibialis_raise" to prof(Equipment.BODYWEIGHT, MovementPattern.ANKLE_FLEXION, JointAngle.DORSI_FLEXION, "band_dorsiflexion", "heel_walk", "calf_raise"),
        "heel_walk" to prof(Equipment.BODYWEIGHT, MovementPattern.ANKLE_FLEXION, JointAngle.DORSI_FLEXION, "tibialis_raise", "band_dorsiflexion"),
        "band_dorsiflexion" to prof(Equipment.BAND, MovementPattern.ANKLE_FLEXION, JointAngle.DORSI_FLEXION, "tibialis_raise", "heel_walk"),

        // Adductors, obliques and forearms
        "copenhagen_plank_short" to prof(Equipment.FLAT_BENCH, MovementPattern.HIP_ADDUCTION, JointAngle.ISOMETRIC_PLANK, "copenhagen_plank_long", "side_plank", "side_lying_hip_adduction"),
        "copenhagen_plank_long" to prof(Equipment.FLAT_BENCH, MovementPattern.HIP_ADDUCTION, JointAngle.ISOMETRIC_PLANK, "copenhagen_plank_short", "side_plank", "side_lying_hip_adduction"),
        "side_lying_hip_adduction" to prof(Equipment.BODYWEIGHT, MovementPattern.HIP_ADDUCTION, JointAngle.FRONTAL_ADDUCTION, "band_hip_adduction", "cossack_squat", "copenhagen_plank_short"),
        "cossack_squat" to prof(Equipment.BODYWEIGHT, MovementPattern.SQUAT_UNILATERAL, JointAngle.KNEE_DOMINANT_SPLIT, "lunge", "side_lying_hip_adduction", "bulgarian_split_squat"),
        "band_hip_adduction" to prof(Equipment.BAND, MovementPattern.HIP_ADDUCTION, JointAngle.FRONTAL_ADDUCTION, "side_lying_hip_adduction", "cossack_squat"),
        "side_plank" to prof(Equipment.BODYWEIGHT, MovementPattern.CORE_ANTI_EXTENSION, JointAngle.ISOMETRIC_PLANK, "plank", "hollow_hold", "copenhagen_plank_short"),
        "bicycle_crunch" to prof(Equipment.BODYWEIGHT, MovementPattern.CORE_FLEXION_ROTATION, JointAngle.ROTATIONAL, "russian_twist", "crunch", "plank_shoulder_taps"),
        "wrist_curl" to prof(Equipment.DUMBBELLS, MovementPattern.ELBOW_FLEXION, JointAngle.SAGITTAL_SUPINATED, "hammer_curl", "farmers_carry", "dead_hang"),

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
        "db_bench_press" to profReq(Equipment.DUMBBELLS, setOf(Equipment.DUMBBELLS, Equipment.FLAT_BENCH), MovementPattern.HORIZONTAL_PUSH, JointAngle.FLAT_HORIZONTAL, "bench_press", "push_up", "weighted_push_up", "incline_db_press", "dip"),
        "db_shoulder_press" to prof(Equipment.DUMBBELLS, MovementPattern.VERTICAL_PUSH, JointAngle.OVERHEAD_VERTICAL, "overhead_press", "kb_press", "pike_push_up", "handstand_push_up"),
        "db_row" to prof(Equipment.DUMBBELLS, MovementPattern.HORIZONTAL_PULL, JointAngle.MID_ROW_PRONATED, "barbell_row", "kb_row", "australian_pull_up", "seated_cable_row", "pull_up"),
        "db_lunge" to prof(Equipment.DUMBBELLS, MovementPattern.SQUAT_UNILATERAL, JointAngle.KNEE_DOMINANT_SPLIT, "bulgarian_split_squat", "lunge", "goblet_squat", "back_squat"),
        "russian_twist" to prof(Equipment.BODYWEIGHT, MovementPattern.CORE_FLEXION_ROTATION, JointAngle.ROTATIONAL, "crunch", "plank_shoulder_taps", "hanging_knee_raise"),

        // More strength moves
        "seated_leg_curl" to prof(Equipment.LEG_CURL, MovementPattern.KNEE_FLEXION, JointAngle.HAMSTRING_CURL, "leg_curl", "nordic_curl", "romanian_deadlift"),
        "single_leg_rdl" to prof(Equipment.DUMBBELLS, MovementPattern.HIP_HINGE, JointAngle.HIP_HINGE_STANDING, "romanian_deadlift", "db_romanian_deadlift", "kb_deadlift"),
        "db_romanian_deadlift" to prof(Equipment.DUMBBELLS, MovementPattern.HIP_HINGE, JointAngle.HIP_HINGE_STANDING, "romanian_deadlift", "kb_deadlift", "single_leg_rdl", "hip_bridge"),
        "good_morning" to profReq(Equipment.BARBELL, setOf(Equipment.BARBELL, Equipment.SQUAT_RACK), MovementPattern.HIP_HINGE, JointAngle.HIP_HINGE_STANDING, "romanian_deadlift", "db_romanian_deadlift", "back_extension"),
        "band_leg_curl" to prof(Equipment.BAND, MovementPattern.KNEE_FLEXION, JointAngle.HAMSTRING_CURL, "leg_curl", "nordic_curl", "hip_bridge"),
        "barbell_curl" to prof(Equipment.BARBELL, MovementPattern.ELBOW_FLEXION, JointAngle.SAGITTAL_SUPINATED, "bicep_curl", "cable_curl", "preacher_curl", "chin_up"),
        "incline_db_curl" to profReq(Equipment.DUMBBELLS, setOf(Equipment.DUMBBELLS, Equipment.ADJ_BENCH), MovementPattern.ELBOW_FLEXION, JointAngle.SAGITTAL_SUPINATED, "bicep_curl", "concentration_curl", "hammer_curl"),
        "concentration_curl" to prof(Equipment.DUMBBELLS, MovementPattern.ELBOW_FLEXION, JointAngle.SAGITTAL_SUPINATED, "bicep_curl", "incline_db_curl", "preacher_curl"),
        "preacher_curl" to prof(Equipment.PREACHER_CURL_MACHINE, MovementPattern.ELBOW_FLEXION, JointAngle.SAGITTAL_SUPINATED, "cable_curl", "barbell_curl", "bicep_curl"),
        "cable_curl" to prof(Equipment.CABLE_MACHINE, MovementPattern.ELBOW_FLEXION, JointAngle.SAGITTAL_SUPINATED, "bicep_curl", "barbell_curl", "band_curl"),
        "band_curl" to prof(Equipment.BAND, MovementPattern.ELBOW_FLEXION, JointAngle.SAGITTAL_SUPINATED, "bicep_curl", "cable_curl", "australian_chin_up"),
        "seated_calf_raise" to prof(Equipment.CALF_MACHINE, MovementPattern.ANKLE_FLEXION, JointAngle.PLANTAR_FLEXION, "standing_calf_raise", "db_calf_raise", "calf_raise"),
        "single_leg_calf_raise" to prof(Equipment.BODYWEIGHT, MovementPattern.ANKLE_FLEXION, JointAngle.PLANTAR_FLEXION, "calf_raise", "db_calf_raise", "standing_calf_raise"),
        "db_calf_raise" to prof(Equipment.DUMBBELLS, MovementPattern.ANKLE_FLEXION, JointAngle.PLANTAR_FLEXION, "standing_calf_raise", "calf_raise", "single_leg_calf_raise"),
        "back_extension" to prof(Equipment.ROMAN_CHAIR, MovementPattern.HIP_HINGE, JointAngle.HIP_EXTENSION_SUPINE, "superman_hold", "hip_bridge", "good_morning"),
        "bird_dog" to prof(Equipment.BODYWEIGHT, MovementPattern.CORE_ANTI_EXTENSION, JointAngle.ISOMETRIC_PLANK, "superman_hold", "plank", "back_extension"),
        "cable_pull_through" to prof(Equipment.CABLE_MACHINE, MovementPattern.HIP_HINGE, JointAngle.HIP_HINGE_STANDING, "kb_swing", "romanian_deadlift", "hip_thrust"),
        "pallof_press" to prof(Equipment.CABLE_MACHINE, MovementPattern.CORE_FLEXION_ROTATION, JointAngle.ROTATIONAL, "cable_woodchop", "side_plank", "russian_twist"),
        "cable_woodchop" to prof(Equipment.CABLE_MACHINE, MovementPattern.CORE_FLEXION_ROTATION, JointAngle.ROTATIONAL, "pallof_press", "russian_twist", "bicycle_crunch"),
        "db_side_bend" to prof(Equipment.DUMBBELLS, MovementPattern.CORE_FLEXION_ROTATION, JointAngle.ROTATIONAL, "cable_woodchop", "russian_twist", "side_plank"),
        "mountain_climber" to prof(Equipment.BODYWEIGHT, MovementPattern.CARRY_CONDITIONING, JointAngle.LOCOMOTION, "burpee", "plank_shoulder_taps", "jumping_jacks"),
        "cable_fly" to prof(Equipment.CABLE_MACHINE, MovementPattern.HORIZONTAL_PUSH, JointAngle.FLAT_HORIZONTAL, "pec_deck", "db_bench_press", "push_up"),
        "pec_deck" to prof(Equipment.PEC_DECK, MovementPattern.HORIZONTAL_PUSH, JointAngle.FLAT_HORIZONTAL, "cable_fly", "chest_press_machine", "push_up"),
        "chest_press_machine" to prof(Equipment.CHEST_PRESS_MACHINE, MovementPattern.HORIZONTAL_PUSH, JointAngle.FLAT_HORIZONTAL, "db_bench_press", "bench_press", "push_up"),
        "shoulder_press_machine" to prof(Equipment.SHOULDER_PRESS_MACHINE, MovementPattern.VERTICAL_PUSH, JointAngle.OVERHEAD_VERTICAL, "db_shoulder_press", "overhead_press", "pike_push_up"),
        "cable_lateral_raise" to prof(Equipment.CABLE_MACHINE, MovementPattern.LATERAL_RAISE, JointAngle.FRONTAL_ABDUCTION, "lateral_raise", "face_pull", "db_shoulder_press"),
        "reverse_pec_deck" to prof(Equipment.PEC_DECK, MovementPattern.UPPER_BACK_PULL, JointAngle.HIGH_EXTERNAL_ROTATION, "face_pull", "band_pull_apart", "seated_cable_row"),
        "straight_arm_pulldown" to prof(Equipment.CABLE_MACHINE, MovementPattern.VERTICAL_PULL, JointAngle.OVERHEAD_PRONATED, "lat_pulldown", "pull_up", "negative_pull_up"),
        "overhead_cable_triceps_extension" to prof(Equipment.CABLE_MACHINE, MovementPattern.ELBOW_EXTENSION, JointAngle.SAGITTAL_TRICEP, "tricep_pushdown", "skull_crusher", "diamond_push_up"),
        "band_pull_apart" to prof(Equipment.BAND, MovementPattern.UPPER_BACK_PULL, JointAngle.HIGH_EXTERNAL_ROTATION, "face_pull", "reverse_pec_deck", "scapular_australian_pull_up"),
        "band_row" to prof(Equipment.BAND, MovementPattern.HORIZONTAL_PULL, JointAngle.MID_ROW_PRONATED, "seated_cable_row", "db_row", "australian_pull_up"),
        "band_chest_press" to prof(Equipment.BAND, MovementPattern.HORIZONTAL_PUSH, JointAngle.FLAT_HORIZONTAL, "push_up", "db_bench_press", "chest_press_machine"),
        "kb_sumo_deadlift" to prof(Equipment.KETTLEBELL, MovementPattern.HIP_HINGE, JointAngle.HIP_HINGE_STANDING, "kb_deadlift", "kb_swing", "romanian_deadlift"),
        "kb_halo" to prof(Equipment.KETTLEBELL, MovementPattern.VERTICAL_PUSH, JointAngle.OVERHEAD_VERTICAL, "kb_press", "db_shoulder_press", "face_pull"),
        "kb_windmill" to prof(Equipment.KETTLEBELL, MovementPattern.CORE_FLEXION_ROTATION, JointAngle.ROTATIONAL, "turkish_get_up", "russian_twist", "side_plank"),
        "smith_squat" to prof(Equipment.SMITH_MACHINE, MovementPattern.SQUAT_BILATERAL, JointAngle.KNEE_DOMINANT_BILATERAL, "back_squat", "leg_press", "hack_squat", "goblet_squat"),
        "smith_bench_press" to profReq(Equipment.SMITH_MACHINE, setOf(Equipment.SMITH_MACHINE, Equipment.FLAT_BENCH), MovementPattern.HORIZONTAL_PUSH, JointAngle.FLAT_HORIZONTAL, "bench_press", "chest_press_machine", "db_bench_press"),
        "smith_incline_press" to profReq(Equipment.SMITH_MACHINE, setOf(Equipment.SMITH_MACHINE, Equipment.ADJ_BENCH), MovementPattern.HORIZONTAL_PUSH, JointAngle.INCLINE_UPWARD, "incline_bench_press", "incline_db_press", "smith_bench_press"),
        "smith_overhead_press" to prof(Equipment.SMITH_MACHINE, MovementPattern.VERTICAL_PUSH, JointAngle.OVERHEAD_VERTICAL, "overhead_press", "shoulder_press_machine", "db_shoulder_press"),
        "smith_row" to prof(Equipment.SMITH_MACHINE, MovementPattern.HORIZONTAL_PULL, JointAngle.MID_ROW_PRONATED, "barbell_row", "seated_cable_row", "chest_supported_row"),
        "smith_romanian_deadlift" to prof(Equipment.SMITH_MACHINE, MovementPattern.HIP_HINGE, JointAngle.HIP_HINGE_STANDING, "romanian_deadlift", "db_romanian_deadlift", "cable_pull_through"),
        "scapular_pull_up" to prof(Equipment.PULL_UP_BAR, MovementPattern.VERTICAL_PULL, JointAngle.OVERHEAD_PRONATED, "dead_hang", "negative_pull_up", "band_assisted_pull_up"),
        "flexed_arm_hang" to prof(Equipment.PULL_UP_BAR, MovementPattern.VERTICAL_PULL, JointAngle.OVERHEAD_SUPINATED, "negative_pull_up", "dead_hang", "band_assisted_pull_up"),
        "band_assisted_pull_up" to profReq(Equipment.PULL_UP_BAR, setOf(Equipment.PULL_UP_BAR, Equipment.BAND), MovementPattern.VERTICAL_PULL, JointAngle.OVERHEAD_PRONATED, "negative_pull_up", "pull_up", "lat_pulldown"),
        "neutral_grip_pull_up" to prof(Equipment.PULL_UP_BAR, MovementPattern.VERTICAL_PULL, JointAngle.OVERHEAD_SUPINATED, "chin_up", "pull_up", "lat_pulldown"),
        "wide_grip_pull_up" to prof(Equipment.PULL_UP_BAR, MovementPattern.VERTICAL_PULL, JointAngle.OVERHEAD_PRONATED, "pull_up", "lat_pulldown", "neutral_grip_pull_up"),
        "archer_pull_up" to prof(Equipment.PULL_UP_BAR, MovementPattern.VERTICAL_PULL, JointAngle.OVERHEAD_PRONATED, "weighted_pull_up", "wide_grip_pull_up", "pull_up"),
        "l_sit_pull_up" to prof(Equipment.PULL_UP_BAR, MovementPattern.VERTICAL_PULL, JointAngle.OVERHEAD_PRONATED, "pull_up", "hanging_leg_raise", "weighted_pull_up"),
        "incline_push_up" to prof(Equipment.BODYWEIGHT, MovementPattern.HORIZONTAL_PUSH, JointAngle.DECLINE_DOWNWARD, "knee_push_up", "push_up", "wide_knee_push_up"),
        "decline_push_up" to prof(Equipment.BODYWEIGHT, MovementPattern.HORIZONTAL_PUSH, JointAngle.INCLINE_UPWARD, "push_up", "pike_push_up", "weighted_push_up"),
        "archer_push_up" to prof(Equipment.BODYWEIGHT, MovementPattern.HORIZONTAL_PUSH, JointAngle.FLAT_HORIZONTAL, "weighted_push_up", "pseudo_planche_push_up", "decline_push_up"),
        "single_arm_cable_row" to prof(Equipment.SEATED_ROW_MACHINE, MovementPattern.HORIZONTAL_PULL, JointAngle.MID_ROW_SUPINATED, "seated_cable_row", "db_row", "chest_supported_row"),
        "cable_rear_delt_fly" to prof(Equipment.CABLE_MACHINE, MovementPattern.UPPER_BACK_PULL, JointAngle.HIGH_EXTERNAL_ROTATION, "reverse_pec_deck", "face_pull", "rear_delt_db_fly"),
        "cable_crossover_high_to_low" to prof(Equipment.CABLE_MACHINE, MovementPattern.HORIZONTAL_PUSH, JointAngle.DECLINE_DOWNWARD, "cable_fly", "pec_deck", "db_fly"),
        "cable_crossover_low_to_high" to prof(Equipment.CABLE_MACHINE, MovementPattern.HORIZONTAL_PUSH, JointAngle.INCLINE_UPWARD, "cable_fly", "incline_db_press", "db_fly"),
        "cable_glute_kickback" to prof(Equipment.CABLE_MACHINE, MovementPattern.HIP_HINGE, JointAngle.HIP_HINGE_STANDING, "hip_thrust", "cable_pull_through", "single_leg_hip_thrust"),
        "step_up" to prof(Equipment.BODYWEIGHT, MovementPattern.SQUAT_UNILATERAL, JointAngle.KNEE_DOMINANT_SPLIT, "lunge", "reverse_lunge", "bulgarian_split_squat"),
        "reverse_lunge" to prof(Equipment.BODYWEIGHT, MovementPattern.SQUAT_UNILATERAL, JointAngle.KNEE_DOMINANT_SPLIT, "lunge", "step_up", "bulgarian_split_squat"),
        "single_leg_hip_thrust" to prof(Equipment.BODYWEIGHT, MovementPattern.HIP_HINGE, JointAngle.HIP_EXTENSION_SUPINE, "hip_thrust", "hip_bridge", "cable_glute_kickback"),
        "single_arm_db_press" to prof(Equipment.DUMBBELLS, MovementPattern.VERTICAL_PUSH, JointAngle.OVERHEAD_VERTICAL, "db_shoulder_press", "kb_press", "overhead_press"),
        "single_arm_lat_pulldown" to prof(Equipment.LAT_PULLDOWN, MovementPattern.VERTICAL_PULL, JointAngle.OVERHEAD_SUPINATED, "lat_pulldown", "straight_arm_pulldown", "neutral_grip_pull_up"),
        "incline_bench_press" to profReq(Equipment.BARBELL, setOf(Equipment.BARBELL, Equipment.ADJ_BENCH), MovementPattern.HORIZONTAL_PUSH, JointAngle.INCLINE_UPWARD, "incline_db_press", "smith_incline_press", "bench_press"),
        "front_squat" to profReq(Equipment.BARBELL, setOf(Equipment.BARBELL, Equipment.SQUAT_RACK), MovementPattern.SQUAT_BILATERAL, JointAngle.KNEE_DOMINANT_BILATERAL, "back_squat", "goblet_squat", "hack_squat"),
        "hack_squat" to prof(Equipment.HACK_SQUAT, MovementPattern.SQUAT_BILATERAL, JointAngle.KNEE_DOMINANT_BILATERAL, "leg_press", "back_squat", "smith_squat"),
        "chest_supported_row" to profReq(Equipment.DUMBBELLS, setOf(Equipment.DUMBBELLS, Equipment.ADJ_BENCH), MovementPattern.HORIZONTAL_PULL, JointAngle.MID_ROW_PRONATED, "db_row", "seated_cable_row", "barbell_row"),
        "db_fly" to profReq(Equipment.DUMBBELLS, setOf(Equipment.DUMBBELLS, Equipment.FLAT_BENCH), MovementPattern.HORIZONTAL_PUSH, JointAngle.FLAT_HORIZONTAL, "cable_fly", "pec_deck", "cable_crossover_high_to_low"),
        "rear_delt_db_fly" to prof(Equipment.DUMBBELLS, MovementPattern.UPPER_BACK_PULL, JointAngle.HIGH_EXTERNAL_ROTATION, "reverse_pec_deck", "cable_rear_delt_fly", "band_pull_apart", "face_pull"),
        "quad_stretch" to prof(Equipment.BODYWEIGHT, MovementPattern.MOBILITY, JointAngle.STRETCH, "hip_flexor_stretch", "shin_stretch"),
        "hamstring_stretch" to prof(Equipment.BODYWEIGHT, MovementPattern.MOBILITY, JointAngle.STRETCH, "calf_stretch", "figure_four_stretch"),
        "hip_flexor_stretch" to prof(Equipment.BODYWEIGHT, MovementPattern.MOBILITY, JointAngle.STRETCH, "quad_stretch", "pigeon_stretch"),
        "pigeon_stretch" to prof(Equipment.BODYWEIGHT, MovementPattern.MOBILITY, JointAngle.STRETCH, "figure_four_stretch", "hip_flexor_stretch"),
        "figure_four_stretch" to prof(Equipment.BODYWEIGHT, MovementPattern.MOBILITY, JointAngle.STRETCH, "pigeon_stretch", "hamstring_stretch"),
        "calf_stretch" to prof(Equipment.BODYWEIGHT, MovementPattern.MOBILITY, JointAngle.STRETCH, "hamstring_stretch", "shin_stretch"),
        "childs_pose" to prof(Equipment.BODYWEIGHT, MovementPattern.MOBILITY, JointAngle.STRETCH, "thread_the_needle", "lat_stretch"),
        "chest_doorway_stretch" to prof(Equipment.BODYWEIGHT, MovementPattern.MOBILITY, JointAngle.STRETCH, "biceps_wall_stretch", "cross_body_shoulder_stretch"),
        "cross_body_shoulder_stretch" to prof(Equipment.BODYWEIGHT, MovementPattern.MOBILITY, JointAngle.STRETCH, "chest_doorway_stretch", "triceps_overhead_stretch"),
        "triceps_overhead_stretch" to prof(Equipment.BODYWEIGHT, MovementPattern.MOBILITY, JointAngle.STRETCH, "cross_body_shoulder_stretch", "biceps_wall_stretch"),
        "biceps_wall_stretch" to prof(Equipment.BODYWEIGHT, MovementPattern.MOBILITY, JointAngle.STRETCH, "chest_doorway_stretch", "wrist_flexor_stretch"),
        "neck_side_stretch" to prof(Equipment.BODYWEIGHT, MovementPattern.MOBILITY, JointAngle.STRETCH, "cross_body_shoulder_stretch", "thread_the_needle"),
        "thread_the_needle" to prof(Equipment.BODYWEIGHT, MovementPattern.MOBILITY, JointAngle.STRETCH, "childs_pose", "seated_spinal_twist"),
        "butterfly_stretch" to prof(Equipment.BODYWEIGHT, MovementPattern.MOBILITY, JointAngle.STRETCH, "frog_stretch", "figure_four_stretch"),
        "frog_stretch" to prof(Equipment.BODYWEIGHT, MovementPattern.MOBILITY, JointAngle.STRETCH, "butterfly_stretch", "pigeon_stretch"),
        "wrist_flexor_stretch" to prof(Equipment.BODYWEIGHT, MovementPattern.MOBILITY, JointAngle.STRETCH, "biceps_wall_stretch", "triceps_overhead_stretch"),
        "seated_spinal_twist" to prof(Equipment.BODYWEIGHT, MovementPattern.MOBILITY, JointAngle.STRETCH, "thread_the_needle", "figure_four_stretch"),
        "lat_stretch" to prof(Equipment.BODYWEIGHT, MovementPattern.MOBILITY, JointAngle.STRETCH, "childs_pose", "triceps_overhead_stretch"),
        "shin_stretch" to prof(Equipment.BODYWEIGHT, MovementPattern.MOBILITY, JointAngle.STRETCH, "calf_stretch", "quad_stretch"),
    )

    fun profileFor(ex: ExerciseEntity): BiomechanicalProfile {
        catalogProfiles[ex.id]?.let { return it }
        val lower = (ex.id + " " + ex.name).lowercase()
        val eq = when {
            "smith" in lower -> Equipment.SMITH_MACHINE
            "pulldown" in lower && "straight" !in lower -> Equipment.LAT_PULLDOWN
            "seated" in lower && "row" in lower -> Equipment.SEATED_ROW_MACHINE
            "chest_press" in lower || "chest press" in lower -> Equipment.CHEST_PRESS_MACHINE
            "shoulder_press" in lower || "shoulder press" in lower -> Equipment.SHOULDER_PRESS_MACHINE
            "pec_deck" in lower || "pec deck" in lower -> Equipment.PEC_DECK
            "preacher" in lower -> Equipment.PREACHER_CURL_MACHINE
            "leg_press" in lower || "leg press" in lower -> Equipment.LEG_PRESS
            "hack_squat" in lower || "hack squat" in lower -> Equipment.HACK_SQUAT
            "leg_extension" in lower || "leg extension" in lower -> Equipment.LEG_EXTENSION
            "leg_curl" in lower || "leg curl" in lower -> Equipment.LEG_CURL
            "calf" in lower && ("machine" in lower || "standing_calf" in lower || "seated_calf" in lower) -> Equipment.CALF_MACHINE
            "cable" in lower || "machine" in lower || "pushdown" in lower -> Equipment.CABLE_MACHINE
            "barbell" in lower || "bench_press" in lower || "back_squat" in lower || "deadlift" in lower -> Equipment.BARBELL
            "kb_" in lower || "kettlebell" in lower -> Equipment.KETTLEBELL
            "db_" in lower || "dumbbell" in lower || "curl" in lower || "lateral" in lower -> Equipment.DUMBBELLS
            "pull_up" in lower || "chin_up" in lower || "hang" in lower || "lever" in lower || "muscle_up" in lower -> Equipment.PULL_UP_BAR
            "band" in lower || "resistance" in lower -> Equipment.BAND
            "australian" in lower -> Equipment.LOW_BAR
            "dip" in lower -> Equipment.DIP_STATION
            "bench" in lower || "split" in lower -> Equipment.FLAT_BENCH
            "row" in lower -> Equipment.SEATED_ROW_MACHINE
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
            mainMuscle == Muscle.ADDUCTORS -> MovementPattern.HIP_ADDUCTION
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
            MovementPattern.HIP_ADDUCTION -> JointAngle.FRONTAL_ADDUCTION
            MovementPattern.MOBILITY -> JointAngle.STRETCH
        }
        return BiomechanicalProfile(eq, pattern, angle)
    }

    fun rankSubstitutes(
        source: ExerciseEntity,
        library: List<ExerciseEntity>,
        userEquipment: Set<Equipment> = Equipment.allActive,
    ): List<SwapCandidate> {
        val srcProf = profileFor(source)
        val effectiveEquipment = expandLegacyEquipment(userEquipment).ifEmpty { Equipment.allActive } + Equipment.BODYWEIGHT
        val sourceIsStretch = source.id in ExerciseCatalog.STRETCH_IDS
        return library
            // A stretch is only ever swapped for another stretch, and a lift never for a stretch.
            .filter { it.id != source.id && (it.id in ExerciseCatalog.STRETCH_IDS) == sourceIsStretch }
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
                val hasEq = candProf.fits(effectiveEquipment)
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
        /** Leave out moves that need missing equipment and have no fair swap, instead of keeping them. */
        dropUnfit: Boolean = false,
    ): Pair<PlanDraft, Int> {
        val allowed = expandLegacyEquipment(userEquipment).ifEmpty { return draft to 0 } + Equipment.BODYWEIGHT
        if (allowed.containsAll(Equipment.allActive)) return draft to 0
        val allExercises = library.values.toList()
        var swapsCount = 0
        val adaptedDays = draft.days.map { day ->
            if (day.isRest || day.items.isEmpty()) return@map day
            val usedInDay = day.items.map { it.exerciseId }.toMutableSet()
            val newItems = day.items.mapNotNull { item ->
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
                if (prof.fits(allowed)) return@mapNotNull item
                val ranked = rankSubstitutes(ex, allExercises, allowed)
                // Only a real match (at least a related pattern or shared muscles): with none, keep the original rather
                // than turning a curl into a push-up or a burpee (audit 2026-10-24).
                val fits = ranked.filter { it.matchesUserEquipment && it.score >= MIN_SWAP_SCORE }
                val replacement = fits.firstOrNull { it.exercise.id !in usedInDay } ?: fits.firstOrNull()
                    ?: return@mapNotNull if (dropUnfit) null.also { swapsCount++ } else item
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