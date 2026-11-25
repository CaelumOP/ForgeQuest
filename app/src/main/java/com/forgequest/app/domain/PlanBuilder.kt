package com.forgequest.app.domain

import com.forgequest.app.data.db.ExerciseEntity
import com.forgequest.app.data.plan.PlanDraft
import com.forgequest.app.data.plan.PlanDraftDay
import com.forgequest.app.data.plan.PlanDraftItem
import com.forgequest.app.data.seed.ExerciseCatalog
import com.forgequest.app.domain.MovementPattern as MP
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class TrainGoal { STRENGTH, MUSCLE, FAT_LOSS, ENDURANCE, GENERAL }
enum class Experience { NEW, SOME, SEASONED }
enum class Cardio { NONE, FINISHER, OWN_DAY }
enum class RepPref { AUTO, LOW, MID, HIGH }
enum class RestPref { AUTO, SHORT, LONG }
enum class DayFocus { FULL_BODY, UPPER, LOWER, PUSH, PULL, LEGS, CONDITIONING }

/** Why the builder did something the person might wonder about. The UI turns these into text (EN and AR). */
enum class WhyTag { SKIPPED_FOR_INJURY, NO_EQUIPMENT, FOCUS_BOOST, TIME_TRIMMED, TIER_FROM_MAX, HOLD_FALLBACK, CARDIO_ADDED, LIGHT_DAY }

data class PlanInput(
    val goal: TrainGoal,
    /** Training weekdays, 1 = Monday .. 7 = Sunday. */
    val days: Set<Int>,
    val minutes: Int,
    val equipment: Set<Equipment>,
    val experience: Experience = Experience.SOME,
    val injuries: Set<Injury> = emptySet(),
    val avoidIds: Set<String> = emptySet(),
    val pushUpMax: Int? = null,
    val pullUpMax: Int? = null,
    /** Muscles to give extra volume (max 3 are used). */
    val focus: Set<Muscle> = emptySet(),
    val minutesByDay: Map<Int, Int> = emptyMap(),
    val cardio: Cardio = Cardio.NONE,
    val warmUp: Boolean = true,
    val coolDown: Boolean = false,
    val holdsOk: Boolean = true,
    val reps: RepPref = RepPref.AUTO,
    val rest: RestPref = RestPref.AUTO,
    /** 0 is the default build; each step picks the next among equally good exercises ('Show a different version'). */
    val offset: Int = 0,
)

/** [days] and [why] are keyed by the training weekdays; the UI writes titles and notes from these. */
data class BuiltPlan(val draft: PlanDraft, val days: Map<Int, DayFocus>, val why: Map<Int, List<WhyTag>>)

/**
 * Offline plan generator: picks catalog exercises per movement slot, sets volume from experience, goal and focus, then
 * trims each day to the time available. Pure and deterministic (ties break by score, then id); only catalog ids are
 * produced so applying the draft never creates custom exercises.
 */
object PlanBuilder {
    const val COOL_DOWN_BLOCK = "Cool-down"
    const val FINISHER_BLOCK = "Finisher"
    const val PROGRESSION_NOTE =
        "Progression: when you hit the top of the range on every set, add load or reps. Every 4th week, halve the sets."

    internal val COMPOUND_PATTERNS = setOf(
        MP.HORIZONTAL_PUSH,
        MP.DIP_PUSH,
        MP.VERTICAL_PUSH,
        MP.VERTICAL_PULL,
        MP.HORIZONTAL_PULL,
        MP.SQUAT_BILATERAL,
        MP.SQUAT_UNILATERAL,
        MP.HIP_HINGE,
    )

    internal fun isCompound(pattern: MP): Boolean = pattern in COMPOUND_PATTERNS

    /** Machines that sit in a compound movement group but work one joint (leg extension). */
    private val ISOLATION_IDS = setOf("leg_extension")

    /** A fly (chest fly, crossover, pec deck) is a one-joint chest move even though it shares the press movement group. */
    fun isFly(ex: ExerciseEntity): Boolean {
        val pattern = ExerciseSwapEngine.profileFor(ex).pattern
        if (pattern != MP.HORIZONTAL_PUSH && pattern != MP.DIP_PUSH) return false
        val id = ex.id
        return "_fly" in id || id.startsWith("fly") || "flye" in id || "crossover" in id || "pec_deck" in id
    }

    /** Multi-joint lift: a compound movement group, not a fly and not a one-joint machine. Shared by the auditor and the baseline. */
    fun isCompoundExercise(ex: ExerciseEntity): Boolean =
        isCompound(ExerciseSwapEngine.profileFor(ex).pattern) && !isFly(ex) && ex.id !in ISOLATION_IDS

    /** The muscles a movement group works; a catalog row whose main muscles miss them is filed in the wrong group. */
    private val PATTERN_MUSCLES: Map<MP, Set<Muscle>> = mapOf(
        MP.HORIZONTAL_PUSH to setOf(Muscle.CHEST, Muscle.TRICEPS, Muscle.SHOULDERS),
        MP.DIP_PUSH to setOf(Muscle.CHEST, Muscle.TRICEPS, Muscle.SHOULDERS),
        MP.VERTICAL_PUSH to setOf(Muscle.SHOULDERS, Muscle.TRICEPS),
        MP.VERTICAL_PULL to setOf(Muscle.UPPER_BACK, Muscle.BICEPS),
        MP.HORIZONTAL_PULL to setOf(Muscle.UPPER_BACK, Muscle.BICEPS, Muscle.TRAPS, Muscle.SHOULDERS),
        MP.UPPER_BACK_PULL to setOf(Muscle.UPPER_BACK, Muscle.TRAPS, Muscle.SHOULDERS),
        MP.SQUAT_BILATERAL to setOf(Muscle.QUADS, Muscle.GLUTES),
        MP.SQUAT_UNILATERAL to setOf(Muscle.QUADS, Muscle.GLUTES),
        MP.HIP_HINGE to setOf(Muscle.HAMSTRINGS, Muscle.GLUTES, Muscle.LOWER_BACK),
        MP.KNEE_FLEXION to setOf(Muscle.HAMSTRINGS),
        MP.LATERAL_RAISE to setOf(Muscle.SHOULDERS),
        MP.CORE_ANTI_EXTENSION to setOf(Muscle.ABS, Muscle.OBLIQUES, Muscle.LOWER_BACK),
        MP.CORE_FLEXION_ROTATION to setOf(Muscle.ABS, Muscle.OBLIQUES),
        MP.ELBOW_FLEXION to setOf(Muscle.BICEPS, Muscle.FOREARMS),
        MP.ELBOW_EXTENSION to setOf(Muscle.TRICEPS),
        MP.ANKLE_FLEXION to setOf(Muscle.CALVES, Muscle.TIBIALIS),
    )

    /** False for a catalog row whose main muscles do not belong to its movement group (a leg jump filed as a chest press). */
    fun fitsItsMovement(ex: ExerciseEntity): Boolean {
        val expected = PATTERN_MUSCLES[ExerciseSwapEngine.profileFor(ex).pattern] ?: return true
        return ex.primary.any { it in expected }
    }

    /** Well-known exercises that win a tie on score, so the builder picks a bench press before an odd variation. */
    val STAPLE_IDS: Set<String> = setOf(
        "bench_press", "db_bench_press", "incline_db_press", "push_up", "incline_push_up", "knee_push_up", "chest_press_machine",
        "chest_dip", "bench_dip", "overhead_press", "db_shoulder_press", "pike_push_up",
        "pull_up", "chin_up", "lat_pulldown", "negative_pull_up", "band_assisted_pull_up",
        "barbell_row", "db_row", "seated_cable_row", "inverted_row", "australian_pull_up",
        "face_pull", "band_pull_apart", "rear_delt_db_fly",
        "back_squat", "front_squat", "goblet_squat", "db_squat", "leg_press",
        "bulgarian_split_squat", "lunge", "reverse_lunge", "step_up",
        "deadlift", "romanian_deadlift", "db_romanian_deadlift", "hip_thrust", "hip_bridge",
        "leg_curl", "seated_leg_curl", "nordic_curl",
        "plank", "side_plank", "dead_bug", "hollow_hold", "crunch", "hanging_knee_raise", "ab_roller", "bird_dog",
        "barbell_curl", "hammer_curl", "incline_db_curl", "cable_curl", "band_curl",
        "skull_crusher", "overhead_db_tricep_ext", "overhead_cable_triceps_extension", "band_overhead_triceps", "close_grip_push_up",
        "lateral_raise", "cable_lateral_raise",
        "calf_raise", "standing_calf_raise", "db_calf_raise", "seated_calf_raise",
        "incline_bench_press", "close_grip_bench_press", "arnold_press", "t_bar_row", "chest_supported_row",
        "sumo_deadlift", "trap_bar_deadlift", "hack_squat", "glute_ham_raise", "single_leg_rdl", "diamond_push_up",
        "db_lunge", "db_step_up", "db_split_squat", "squat",
    )

    internal fun isCore(pattern: MP): Boolean = pattern == MP.CORE_ANTI_EXTENSION || pattern == MP.CORE_FLEXION_ROTATION

    /** Sensible training weekdays for [n] days a week (Monday = 1). */
    fun spreadDays(n: Int): Set<Int> = when {
        n <= 0 -> emptySet()
        n == 1 -> setOf(1)
        n == 2 -> setOf(1, 4)
        n == 3 -> setOf(1, 3, 5)
        n == 4 -> setOf(1, 2, 4, 5)
        n == 5 -> setOf(1, 2, 3, 5, 6)
        n == 6 -> setOf(1, 2, 3, 4, 5, 6)
        else -> (1..7).toSet()
    }

    fun build(input: PlanInput, library: List<ExerciseEntity>): BuiltPlan = Builder(input, library).run()

    /** Other allowed exercises for the slot [current] fills (same movement pattern), best first, for a swap button. */
    fun alternatives(
        current: ExerciseEntity,
        input: PlanInput,
        library: List<ExerciseEntity>,
        exclude: Set<String> = emptySet(),
        limit: Int = 6,
    ): List<ExerciseEntity> = Builder(input, library).alternativesFor(current, exclude, limit)

    /** Minutes the day takes: sets x (40 s + rest) for the work, plus a flat 5 for a warm-up and 5 for a cool-down. */
    fun estimateMinutes(day: PlanDraftDay): Int {
        val workSeconds = day.items.filter { it.block != WorkoutScore.WARM_UP_BLOCK && it.block != COOL_DOWN_BLOCK }
            .sumOf { it.sets * (SET_SECONDS + it.restSeconds) }
        val extras = (if (day.items.any { it.block == WorkoutScore.WARM_UP_BLOCK }) 5 else 0) +
            (if (day.items.any { it.block == COOL_DOWN_BLOCK }) 5 else 0)
        return ceil(workSeconds / 60.0).toInt() + extras
    }

    private const val SET_SECONDS = 40
    private const val TOLERANCE_MIN = 5
    private const val CONDITIONING_REST = 30

    private enum class Role { MAIN, FOCUS, ACC, FINISHER, COND }

    private class Slot(val patterns: List<MP>, val role: Role, val focus: Muscle? = null) {
        val core get() = patterns.any { it == MP.CORE_ANTI_EXTENSION || it == MP.CORE_FLEXION_ROTATION }
    }

    private class Pick(val ex: ExerciseEntity, val role: Role, var sets: Int = 3)

    private class DayBuild(val focus: DayFocus, val light: Boolean) {
        val picks = mutableListOf<Pick>()
        val tags = linkedSetOf<WhyTag>()
    }

    private val LOAD_EQUIPMENT = (
        setOf(Equipment.DUMBBELLS, Equipment.KETTLEBELL, Equipment.BARBELL) +
            Equipment.selectableEntries.filter { it.group == EquipmentGroup.UPPER_MACHINES || it.group == EquipmentGroup.LOWER_MACHINES }
        ).toSet()
    private val PUSH_PATTERNS = setOf(MP.HORIZONTAL_PUSH, MP.DIP_PUSH, MP.VERTICAL_PUSH)
    private val PULL_PATTERNS = setOf(MP.VERTICAL_PULL, MP.HORIZONTAL_PULL, MP.UPPER_BACK_PULL)

    private val MAJOR_MUSCLES = setOf(
        Muscle.CHEST, Muscle.SHOULDERS, Muscle.BICEPS, Muscle.TRICEPS, Muscle.UPPER_BACK,
        Muscle.QUADS, Muscle.HAMSTRINGS, Muscle.GLUTES, Muscle.ABS,
    )
    private val UPPER_MUSCLES = setOf(Muscle.CHEST, Muscle.SHOULDERS, Muscle.BICEPS, Muscle.TRICEPS, Muscle.FOREARMS, Muscle.UPPER_BACK, Muscle.TRAPS)
    private val LOWER_MUSCLES = setOf(Muscle.QUADS, Muscle.HAMSTRINGS, Muscle.GLUTES, Muscle.CALVES, Muscle.ADDUCTORS, Muscle.TIBIALIS)
    private val CORE_MUSCLES = setOf(Muscle.ABS, Muscle.OBLIQUES, Muscle.LOWER_BACK)

    /** Preference order for the two warm-up moves: bodyweight, easy rep moves only. */
    private val WARM_UP_IDS = listOf("jumping_jacks", "calf_raise", "hip_bridge", "half_squat", "knee_scapular_push_up")

    /** Preference order for the two cool-down stretches; the ones that work the muscles trained that day go first. */
    private val COOL_DOWN_IDS = listOf(
        "childs_pose", "hamstring_stretch", "hip_flexor_stretch", "chest_doorway_stretch", "quad_stretch", "lat_stretch",
        "cross_body_shoulder_stretch", "figure_four_stretch", "calf_stretch", "triceps_overhead_stretch",
        "seated_spinal_twist", "biceps_wall_stretch", "butterfly_stretch", "neck_side_stretch",
    )

    private val SQUAT = listOf(MP.SQUAT_BILATERAL, MP.SQUAT_UNILATERAL)
    private val UNILATERAL = listOf(MP.SQUAT_UNILATERAL, MP.SQUAT_BILATERAL)
    private val H_PUSH = listOf(MP.HORIZONTAL_PUSH, MP.DIP_PUSH)
    private val DIP = listOf(MP.DIP_PUSH, MP.HORIZONTAL_PUSH)
    private val H_PULL = listOf(MP.HORIZONTAL_PULL, MP.UPPER_BACK_PULL)
    private val BACK_PULL = listOf(MP.UPPER_BACK_PULL, MP.HORIZONTAL_PULL)
    private val HINGE = listOf(MP.HIP_HINGE, MP.KNEE_FLEXION)
    private val KNEE_FLEX = listOf(MP.KNEE_FLEXION, MP.HIP_HINGE)
    private val V_PUSH = listOf(MP.VERTICAL_PUSH)
    private val V_PULL = listOf(MP.VERTICAL_PULL, MP.HORIZONTAL_PULL)
    private val CORE = listOf(MP.CORE_ANTI_EXTENSION, MP.CORE_FLEXION_ROTATION)
    private val LATERAL = listOf(MP.LATERAL_RAISE, MP.UPPER_BACK_PULL)
    private val CURL = listOf(MP.ELBOW_FLEXION)
    private val EXTENSION = listOf(MP.ELBOW_EXTENSION)
    private val CALF = listOf(MP.ANKLE_FLEXION)
    private val CONDITIONING = listOf(MP.CARRY_CONDITIONING)

    private fun main(p: List<MP>) = Slot(p, Role.MAIN)
    private fun acc(p: List<MP>) = Slot(p, Role.ACC)

    private fun slotsFor(focus: DayFocus): List<Slot> = when (focus) {
        DayFocus.FULL_BODY -> listOf(main(SQUAT), main(H_PUSH), main(H_PULL), main(HINGE), main(V_PUSH), main(V_PULL), main(CORE), acc(CURL), acc(EXTENSION), acc(CALF))
        DayFocus.UPPER -> listOf(main(H_PUSH), main(H_PULL), main(V_PUSH), main(V_PULL), main(CORE), acc(CURL), acc(EXTENSION), acc(LATERAL))
        DayFocus.LOWER -> listOf(main(SQUAT), main(HINGE), main(UNILATERAL), main(CORE), acc(KNEE_FLEX), acc(CALF))
        DayFocus.PUSH -> listOf(main(H_PUSH), main(V_PUSH), main(DIP), main(CORE), acc(LATERAL), acc(EXTENSION))
        DayFocus.PULL -> listOf(main(V_PULL), main(H_PULL), main(BACK_PULL), main(CORE), acc(CURL), acc(BACK_PULL))
        DayFocus.LEGS -> listOf(main(SQUAT), main(HINGE), main(UNILATERAL), main(CORE), acc(KNEE_FLEX), acc(CALF))
        DayFocus.CONDITIONING -> List(3) { Slot(CONDITIONING, Role.COND) } + main(CORE)
    }

    private fun trains(day: DayFocus, m: Muscle): Boolean = when (day) {
        DayFocus.FULL_BODY -> true
        DayFocus.CONDITIONING -> false
        else -> m in CORE_MUSCLES || when (day) {
            DayFocus.UPPER -> m in UPPER_MUSCLES
            DayFocus.LOWER, DayFocus.LEGS -> m in LOWER_MUSCLES
            DayFocus.PUSH -> m == Muscle.CHEST || m == Muscle.SHOULDERS || m == Muscle.TRICEPS
            else -> m == Muscle.UPPER_BACK || m == Muscle.BICEPS || m == Muscle.TRAPS || m == Muscle.FOREARMS
        }
    }

    private fun titleFor(f: DayFocus) = when (f) {
        DayFocus.FULL_BODY -> "Full body"
        DayFocus.UPPER -> "Upper"
        DayFocus.LOWER -> "Lower"
        DayFocus.PUSH -> "Push"
        DayFocus.PULL -> "Pull"
        DayFocus.LEGS -> "Legs"
        DayFocus.CONDITIONING -> "Conditioning"
    }

    private class Builder(private val input: PlanInput, library: List<ExerciseEntity>) {
        private val allowedEq = expandLegacyEquipment(input.equipment) + Equipment.BODYWEIGHT
        private val loadEquip = allowedEq.any { it in LOAD_EQUIPMENT }
        private val lib = library.distinctBy { it.id }.sortedBy { it.id }
        private val profiles = lib.associate { it.id to ExerciseSwapEngine.profileFor(it) }
        private val focus = input.focus.sortedBy { it.ordinal }.take(3).toSet()
        private val weekUsed = mutableSetOf<String>()

        private fun prof(e: ExerciseEntity) = profiles.getValue(e.id)

        private val equipOk = lib.filter { prof(it).fits(allowedEq) && it.id !in input.avoidIds }
        private val eligible = equipOk.filter {
            it.id !in ExerciseCatalog.STRETCH_IDS && prof(it).pattern != MP.MOBILITY && fitsItsMovement(it) &&
                !blocked(it, prof(it), input.injuries)
        }
        private val stretches = equipOk.filter { it.id in ExerciseCatalog.STRETCH_IDS && !blocked(it, prof(it), input.injuries) }

        private val experienceCap = when (input.experience) {
            Experience.NEW -> Tier.FOUNDATION
            Experience.SOME -> Tier.INTERMEDIATE
            Experience.SEASONED -> Tier.ADVANCED
        }

        private fun capFor(pattern: MP): Tier = when (pattern) {
            in PUSH_PATTERNS -> input.pushUpMax?.let { if (it < 5) Tier.FOUNDATION else if (it <= 20) Tier.INTERMEDIATE else Tier.ADVANCED }
            in PULL_PATTERNS -> input.pullUpMax?.let { if (it <= 0) Tier.FOUNDATION else if (it <= 7) Tier.INTERMEDIATE else Tier.ADVANCED }
            else -> null
        } ?: experienceCap

        private fun tierOf(e: ExerciseEntity) = ExerciseTiers.of(e) ?: Tier.FOUNDATION
        private fun withinCap(e: ExerciseEntity) = tierOf(e).ordinal <= capFor(prof(e).pattern).ordinal

        private fun score(e: ExerciseEntity, role: Role): Int {
            var s = 40 - 12 * (capFor(prof(e).pattern).ordinal - tierOf(e).ordinal)
            s += 10 * e.primary.count { it in focus } + 3 * e.secondary.count { it in focus }
            val loaded = role == Role.MAIN || role == Role.FOCUS || role == Role.ACC
            if (loaded && e.kind == ExerciseKind.WEIGHTED && loadEquip && (input.goal == TrainGoal.STRENGTH || input.goal == TrainGoal.MUSCLE)) s += 8
            return s
        }

        /** Within the best score: unused staples, then staples used on another day, then unused others, then the rest. */
        private fun tieRank(e: ExerciseEntity): Int = (if (e.id in STAPLE_IDS) 0 else 2) + (if (e.id in weekUsed) 1 else 0)

        private fun best(cands: List<ExerciseEntity>, role: Role): ExerciseEntity {
            val sorted = cands.sortedWith(compareBy<ExerciseEntity>({ -score(it, role) }, { tieRank(it) }, { it.id }))
            val tied = sorted.takeWhile { score(it, role) == score(sorted[0], role) }
            return tied[input.offset.mod(tied.size)]
        }

        private fun groupsOf(slot: Slot): List<(ExerciseEntity) -> Boolean> =
            if (slot.focus != null) listOf { e -> slot.focus in e.primary }
            else slot.patterns.map { p -> { e: ExerciseEntity -> prof(e).pattern == p } }

        private fun pick(slot: Slot, dayUsed: Set<String>, tags: MutableSet<WhyTag>): ExerciseEntity? {
            val passes = if (input.holdsOk) listOf(true) else if (slot.core) listOf(false, true) else listOf(false)
            for (holdsAllowed in passes) for (match in groupsOf(slot)) {
                val cands = eligible.filter { match(it) && it.id !in dayUsed && (holdsAllowed || it.kind != ExerciseKind.HOLD) && withinCap(it) }
                if (cands.isEmpty()) continue
                val chosen = best(cands, slot.role)
                if (!input.holdsOk && chosen.kind == ExerciseKind.HOLD) tags += WhyTag.HOLD_FALLBACK
                return chosen
            }
            return null
        }

        /** Why a main slot came back empty: an injury rule removed everything that fits the equipment, or nothing fits. */
        private fun reasonFor(slot: Slot): WhyTag {
            val hit = groupsOf(slot).any { match -> equipOk.any { match(it) && blocked(it, prof(it), input.injuries) } }
            return if (hit) WhyTag.SKIPPED_FOR_INJURY else WhyTag.NO_EQUIPMENT
        }

        fun alternativesFor(current: ExerciseEntity, exclude: Set<String>, limit: Int): List<ExerciseEntity> {
            val pattern = ExerciseSwapEngine.profileFor(current).pattern
            return eligible.filter {
                it.id != current.id && it.id !in exclude && prof(it).pattern == pattern && withinCap(it) &&
                    (input.holdsOk || it.kind != ExerciseKind.HOLD)
            }.sortedWith(compareBy<ExerciseEntity>({ -score(it, Role.ACC) }, { if (it.id in STAPLE_IDS) 0 else 1 }, { it.id })).take(limit)
        }

        // ---- week layout ----

        private fun dayMinutes(d: Int) = input.minutesByDay[d] ?: input.minutes

        private fun assign(sel: List<Int>): Map<Int, DayFocus> {
            val own = input.cardio == Cardio.OWN_DAY && sel.size >= 3
            val extra = if (sel.size == 7 || own) sel.last() else null
            val lifting = if (extra != null) sel.dropLast(1) else sel
            val split = when (lifting.size) {
                in 0..3 -> List(lifting.size) { DayFocus.FULL_BODY }
                4 -> listOf(DayFocus.UPPER, DayFocus.LOWER, DayFocus.UPPER, DayFocus.LOWER)
                5 -> listOf(DayFocus.UPPER, DayFocus.LOWER, DayFocus.PUSH, DayFocus.PULL, DayFocus.LEGS)
                else -> listOf(DayFocus.PUSH, DayFocus.PULL, DayFocus.LEGS, DayFocus.PUSH, DayFocus.PULL, DayFocus.LEGS)
            }
            val out = linkedMapOf<Int, DayFocus>()
            lifting.forEachIndexed { i, d -> out[d] = if (dayMinutes(d) < 30) DayFocus.FULL_BODY else split[i] }
            if (extra != null) out[extra] = if (input.cardio != Cardio.NONE) DayFocus.CONDITIONING else DayFocus.FULL_BODY
            return out
        }

        private fun buildDay(focusOfDay: DayFocus, light: Boolean, finisher: Boolean): DayBuild {
            val day = DayBuild(focusOfDay, light)
            if (light && focusOfDay != DayFocus.CONDITIONING) day.tags += WhyTag.LIGHT_DAY
            val base = slotsFor(focusOfDay)
            val focusSlots = focus.filter { trains(focusOfDay, it) }.map { Slot(emptyList(), Role.FOCUS, it) }
            val slots = base.filter { it.role != Role.ACC } + focusSlots + base.filter { it.role == Role.ACC } +
                (if (finisher && focusOfDay != DayFocus.CONDITIONING) listOf(Slot(CONDITIONING, Role.FINISHER)) else emptyList())
            val used = mutableSetOf<String>()
            for (slot in slots) {
                val ex = pick(slot, used, day.tags)
                if (ex == null) {
                    if (slot.role == Role.MAIN) day.tags += reasonFor(slot)
                    continue
                }
                used += ex.id
                weekUsed += ex.id
                day.picks += Pick(ex, slot.role)
                if (slot.role == Role.FOCUS || ex.primary.any { it in focus }) day.tags += WhyTag.FOCUS_BOOST
                if (slot.role == Role.FINISHER || slot.role == Role.COND) day.tags += WhyTag.CARDIO_ADDED
                val pattern = prof(ex).pattern
                if ((pattern in PUSH_PATTERNS && input.pushUpMax != null) || (pattern in PULL_PATTERNS && input.pullUpMax != null)) {
                    day.tags += WhyTag.TIER_FROM_MAX
                }
            }
            return day
        }

        // ---- sets, reps, rest ----

        private fun repRange(): Pair<Int, Int> = when (input.reps) {
            RepPref.LOW -> 5 to 8
            RepPref.MID -> 8 to 12
            RepPref.HIGH -> 12 to 20
            RepPref.AUTO -> when (input.goal) {
                TrainGoal.STRENGTH -> 4 to 6
                TrainGoal.MUSCLE -> 8 to 12
                TrainGoal.FAT_LOSS -> 12 to 15
                TrainGoal.ENDURANCE -> 15 to 20
                TrainGoal.GENERAL -> 8 to 12
            }
        }

        private fun repRangeFor(p: Pick): Pair<Int, Int> {
            if (p.role == Role.COND || p.role == Role.FINISHER) return 12 to 20
            val base = repRange()
            if (isCompoundExercise(p.ex)) return base
            if (input.reps != RepPref.AUTO) return if (base.first >= 8) base else 8 to 15
            return when (input.goal) {
                TrainGoal.STRENGTH -> 8 to 12
                TrainGoal.MUSCLE -> 10 to 15
                else -> if (base.first >= 8) base else 8 to 15
            }
        }

        private fun restFor(p: Pick): Int {
            if (p.role == Role.COND || p.role == Role.FINISHER) return CONDITIONING_REST
            val compound = isCompoundExercise(p.ex)
            val seconds = if (compound) {
                when (input.goal) {
                    TrainGoal.STRENGTH -> 180
                    TrainGoal.MUSCLE -> 150
                    TrainGoal.GENERAL, TrainGoal.FAT_LOSS, TrainGoal.ENDURANCE -> 120
                }
            } else {
                when (input.goal) {
                    TrainGoal.STRENGTH, TrainGoal.MUSCLE -> 90
                    TrainGoal.GENERAL -> 75
                    TrainGoal.FAT_LOSS, TrainGoal.ENDURANCE -> 60
                }
            }
            val factor = when (input.rest) { RestPref.AUTO -> 1.0; RestPref.SHORT -> 0.6; RestPref.LONG -> 1.5 }
            return (seconds * factor).roundToInt().coerceIn(30, 240)
        }

        /** Weekly effective sets the builder aims for, kept between the muscle's minimum and maximum. */
        private fun target(m: Muscle): Double {
            val weekly = when (input.experience) { Experience.NEW -> 10; Experience.SOME -> 12; Experience.SEASONED -> 14 }
            val raw = weekly * (if (m in MAJOR_MUSCLES) 1.0 else 0.5) + (if (m in focus) 4 else 0)
            val lm = RoutineAuditor.landmarkFor(m)
            val floor = if (m in RoutineAuditor.BELOW_MEV_MUSCLES) lm.mev.toDouble() else 0.0
            return raw.coerceIn(floor, lm.mrv.toDouble())
        }

        private val maxSets get() = if (input.experience == Experience.NEW) 4 else 5

        /** Running weekly totals for the plan being built, so each candidate set is checked in a few steps. */
        private inner class Volume(private val days: List<Pair<Int, DayBuild>>) {
            private val week = HashMap<Muscle, Double>()
            private val dayPrimary = days.map { HashMap<Muscle, Int>() }
            private var push = 0
            private var pull = 0
            private val indexByDow = days.withIndex().associate { (i, d) -> d.first to i }
            private val neighbours: List<List<Int>> = days.indices.map { i ->
                val dow = days[i].first
                listOfNotNull(indexByDow[if (dow == 1) 7 else dow - 1], indexByDow[if (dow == 7) 1 else dow + 1]).filter { it != i }.distinct()
            }

            init { for ((i, d) in days.withIndex()) for (p in d.second.picks) add(i, p, p.sets) }

            fun add(dayIdx: Int, p: Pick, n: Int) {
                for ((m, c) in RoutineAuditor.credits(p.ex)) {
                    week[m] = (week[m] ?: 0.0) + c * n
                    if (c >= 1.0) dayPrimary[dayIdx][m] = (dayPrimary[dayIdx][m] ?: 0) + n
                }
                when (RoutineAuditor.pushPull(p.ex)) { 1 -> push += n; -1 -> pull += n; else -> {} }
            }

            private fun overlaps(dayIdx: Int, m: Muscle, after: Int) =
                after >= RoutineAuditor.OVERLAP_SETS && neighbours[dayIdx].any { (dayPrimary[it][m] ?: 0) >= RoutineAuditor.OVERLAP_SETS }

            /** True when one more set of [p] on [dayIdx] keeps MRV, day-muscle limit, and back-to-back overlap. */
            fun fitsMuscleRules(dayIdx: Int, p: Pick): Boolean {
                for ((m, c) in RoutineAuditor.credits(p.ex)) {
                    if ((week[m] ?: 0.0) + c > RoutineAuditor.landmarkFor(m).mrv + 1e-9) return false
                    if (c >= 1.0) {
                        val after = (dayPrimary[dayIdx][m] ?: 0) + 1
                        if (after > RoutineAuditor.DAY_MUSCLE_LIMIT || overlaps(dayIdx, m, after)) return false
                    }
                }
                return true
            }

            /** True when one more set of [p] on [dayIdx] keeps every Plan check rule. */
            fun fits(dayIdx: Int, p: Pick): Boolean =
                fitsMuscleRules(dayIdx, p) && (RoutineAuditor.pushPull(p.ex) != 1 || !RoutineAuditor.pushPullBroken(push + 1, pull))

            /** How much one more set of [p] closes the gap to the weekly targets. */
            fun gain(p: Pick): Double = RoutineAuditor.credits(p.ex).entries.sumOf { (m, c) ->
                minOf(c, maxOf(0.0, target(m) - (week[m] ?: 0.0)))
            }

            /** Sets missing to reach each tracked muscle's minimum, summed over the week. */
            fun shortfall(): Double = RoutineAuditor.BELOW_MEV_MUSCLES.sumOf { m ->
                maxOf(0.0, RoutineAuditor.landmarkFor(m).mev - (week[m] ?: 0.0))
            }

            fun shortMuscles(): Set<Muscle> = RoutineAuditor.BELOW_MEV_MUSCLES.filterTo(HashSet()) { m ->
                (week[m] ?: 0.0) < RoutineAuditor.landmarkFor(m).mev - 1e-9
            }

            /** A push set would still help but only the push/pull balance stops it, so a pull set is worth adding. */
            fun pushBlocked(): Boolean = RoutineAuditor.pushPullBroken(push + 1, pull)

            /** The first broken rule a pick could fix by leaving: (muscle, or null for push/pull) and (day index, or null for the week). */
            fun broken(): Pair<Muscle?, Int?>? {
                for (m in Muscle.entries) if ((week[m] ?: 0.0) > RoutineAuditor.landmarkFor(m).mrv + 1e-9) return m to null
                for (i in days.indices) for (m in Muscle.entries) {
                    val v = dayPrimary[i][m] ?: 0
                    if (v > RoutineAuditor.DAY_MUSCLE_LIMIT || overlaps(i, m, v)) return m to i
                }
                if (RoutineAuditor.pushPullBroken(push, pull)) return null to null
                return null
            }
        }

        private fun budgetSeconds(d: Int, reserved: Int) = (dayMinutes(d) + TOLERANCE_MIN) * 60 - reserved

        private fun removalRank(p: Pick): Int = when {
            p.role == Role.FINISHER || p.role == Role.COND -> 0
            p.role == Role.ACC -> 1
            p.role == Role.FOCUS -> 4
            isCore(prof(p.ex).pattern) -> 2
            p.role == Role.MAIN -> 3
            else -> 4
        }

        /**
         * Sets for every pick. Each starts at 2 (conditioning 3, finisher 2) and the day is fitted to its minutes; picks that
         * break a Plan check rule even at their starting sets leave; then one set at a time goes to the pick that best closes
         * the gap to the weekly targets, as long as every Plan check rule, the per-pick cap and the day's minutes still hold.
         */
        private fun allocate(builds: LinkedHashMap<Int, DayBuild>, reserved: Map<Int, Int>) {
            for (b in builds.values) for (p in b.picks) p.sets = if (p.role == Role.COND) 3 else 2
            val days = builds.entries.map { it.key to it.value }
            for ((d, b) in days) {
                val budget = budgetSeconds(d, reserved.getValue(d))
                while (workSeconds(b.picks) > budget && b.picks.size > 1) {
                    val drop = listOf<(Pick) -> Boolean>(
                        { it.role == Role.FINISHER },
                        { it.role == Role.ACC },
                        { it.role != Role.FOCUS && isCore(prof(it.ex).pattern) },
                        { it.role != Role.FOCUS },
                        { true },
                    ).firstNotNullOfOrNull { match -> b.picks.indexOfLast(match).takeIf { it >= 0 } } ?: break
                    b.picks.removeAt(drop)
                    b.tags += WhyTag.TIME_TRIMMED
                }
            }
            var vol = Volume(days)
            for (round in 0 until 200) {
                val (m, dayIdx) = vol.broken() ?: break
                val candidates = days.indices.flatMap { i -> days[i].second.picks.map { i to it } }.filter { (i, p) ->
                    days[i].second.picks.size > 1 && (dayIdx == null || i == dayIdx) &&
                        if (m == null) RoutineAuditor.pushPull(p.ex) == 1 else (RoutineAuditor.credits(p.ex)[m] ?: 0.0) > 0.0
                }
                val victim = candidates.minWithOrNull(
                    compareBy<Pair<Int, Pick>>(
                        { removalRank(it.second) },
                        { -(if (m == null) 1.0 else RoutineAuditor.credits(it.second.ex)[m] ?: 0.0) },
                        { -it.first },
                        { -days[it.first].second.picks.indexOf(it.second) },
                    ),
                ) ?: break
                days[victim.first].second.picks.remove(victim.second)
                vol = Volume(days)
            }
            vol = fill(days, reserved, vol)
            // Still short of a minimum: trade one whole lift that does not feed the short muscle for more sets of those that do.
            for (round in 0 until 20) {
                val short = vol.shortfall()
                if (short <= 1e-9) break
                val shortMuscles = vol.shortMuscles()
                val options = days.indices.flatMap { i -> days[i].second.picks.map { i to it } }.filter { (i, q) ->
                    days[i].second.picks.size > 2 && q.role != Role.FOCUS &&
                        RoutineAuditor.credits(q.ex).keys.none { it in shortMuscles }
                }.sortedWith(compareBy<Pair<Int, Pick>>({ removalRank(it.second) }, { -it.first }))
                var improved = false
                for ((i, q) in options) {
                    val snapshot = days.map { d -> d.second.picks.map { it to it.sets } }
                    days[i].second.picks.remove(q)
                    val trial = fill(days, reserved, Volume(days))
                    if (trial.broken() == null && trial.shortfall() < short - 1e-9) {
                        vol = trial
                        improved = true
                        break
                    }
                    for ((d, saved) in days.zip(snapshot)) {
                        d.second.picks.clear()
                        for ((pick, sets) in saved) { pick.sets = sets; d.second.picks += pick }
                    }
                }
                if (!improved) break
            }
        }

        /** Adds one set at a time to the pick that best closes the gap to the weekly targets while every rule still holds. */
        private fun fill(days: List<Pair<Int, DayBuild>>, reserved: Map<Int, Int>, start: Volume): Volume {
            val vol = start
            val cap = maxSets
            while (true) {
                val pushWanted = vol.pushBlocked() && days.withIndex().any { (i, d) ->
                    val spare = budgetSeconds(d.first, reserved.getValue(d.first)) - workSeconds(d.second.picks)
                    !d.second.light && d.second.picks.any { p ->
                        p.role != Role.COND && p.role != Role.FINISHER &&
                            RoutineAuditor.pushPull(p.ex) == 1 && p.sets < cap && SET_SECONDS + restFor(p) <= spare &&
                            vol.gain(p) >= 0.5 - 1e-9 && vol.fitsMuscleRules(i, p)
                    }
                }
                var bestIdx = -1
                var bestPick: Pick? = null
                var bestGain = 0.0
                for ((i, d) in days.withIndex()) {
                    val (dow, b) = d
                    if (b.light) continue
                    val spare = budgetSeconds(dow, reserved.getValue(dow)) - workSeconds(b.picks)
                    for (p in b.picks) {
                        if (p.role == Role.COND || p.role == Role.FINISHER || p.sets >= cap) continue
                        if (SET_SECONDS + restFor(p) > spare) continue
                        var g = vol.gain(p)
                        if (g < 0.5 - 1e-9 && pushWanted && RoutineAuditor.pushPull(p.ex) == -1) g = 0.5
                        if (g < 0.5 - 1e-9 || !vol.fits(i, p)) continue
                        if (bestPick == null || g > bestGain + 1e-9) {
                            bestIdx = i; bestPick = p; bestGain = g
                        }
                    }
                }
                val p = bestPick ?: break
                p.sets++
                vol.add(bestIdx, p, 1)
            }
            return vol
        }

        private fun item(p: Pick): PlanDraftItem {
            val (lo, hi) = repRangeFor(p)
            val e = p.ex
            var repsMin: Int? = null
            var repsMax: Int? = null
            var seconds: Int? = null
            when (e.kind) {
                ExerciseKind.HOLD -> seconds = when (tierOf(e)) { Tier.FOUNDATION -> 20; Tier.INTERMEDIATE -> 30; Tier.ADVANCED -> 45 }
                ExerciseKind.REPS -> { repsMin = max(lo, 6); repsMax = max(hi, repsMin + 2) }
                ExerciseKind.WEIGHTED -> { repsMin = lo; repsMax = hi }
            }
            return PlanDraftItem(
                exerciseId = e.id, name = e.name, kind = e.kind.name, muscles = e.primary.map { it.name },
                block = if (p.role == Role.FINISHER) FINISHER_BLOCK else "Main",
                circuit = input.goal == TrainGoal.FAT_LOSS || p.role == Role.COND || p.role == Role.FINISHER,
                sets = p.sets, repsMin = repsMin, repsMax = repsMax, seconds = seconds, restSeconds = restFor(p),
            )
        }

        private fun warmUpItems(used: Set<String>): List<PlanDraftItem> {
            if (!input.warmUp) return emptyList()
            val byId = eligible.associateBy { it.id }
            return WARM_UP_IDS.mapNotNull { byId[it] }
                .filter { it.id !in used && it.kind == ExerciseKind.REPS }
                .take(2)
                .map {
                    PlanDraftItem(
                        exerciseId = it.id, name = it.name, kind = it.kind.name, muscles = it.primary.map { m -> m.name },
                        block = WorkoutScore.WARM_UP_BLOCK, sets = 2, repsMin = 10, repsMax = 15, restSeconds = CONDITIONING_REST,
                    )
                }
        }

        private fun coolDownItems(used: Set<String>, trained: Set<Muscle>): List<PlanDraftItem> {
            if (!input.coolDown) return emptyList()
            val byId = stretches.associateBy { it.id }
            return COOL_DOWN_IDS.mapNotNull { byId[it] }.filter { it.id !in used }
                .sortedByDescending { s -> s.primary.count { it in trained } * 2 + s.secondary.count { it in trained } }
                .take(2).map {
                PlanDraftItem(
                    exerciseId = it.id, name = it.name, kind = it.kind.name, muscles = it.primary.map { m -> m.name },
                    block = COOL_DOWN_BLOCK, sets = 1, seconds = 30, restSeconds = CONDITIONING_REST,
                )
            }
        }

        private fun workSeconds(picks: List<Pick>) = picks.sumOf { it.sets * (SET_SECONDS + restFor(it)) }

        private fun orderRank(p: Pick): Int {
            if (p.role == Role.FINISHER) return 3
            val pattern = prof(p.ex).pattern
            return when {
                isCore(pattern) -> 2
                isCompoundExercise(p.ex) -> 0
                else -> 1
            }
        }

        private fun warmFor(day: DayBuild) = warmUpItems(day.picks.map { it.ex.id }.toSet())
        private fun coolFor(day: DayBuild) = coolDownItems(day.picks.map { it.ex.id }.toSet(), day.picks.flatMap { it.ex.primary }.toSet())

        /** Turns a day's picks (already sized to the time by [allocate]) into a draft day: big lifts first, core, then finisher. */
        private fun finalizeDay(d: Int, day: DayBuild): PlanDraftDay? {
            if (day.picks.isEmpty()) return null
            val warm = warmFor(day)
            val cool = coolFor(day)
            day.picks.sortBy { orderRank(it) }
            return PlanDraftDay(dayOfWeek = d, title = titleFor(day.focus), items = warm + day.picks.map { item(it) } + cool)
        }

        fun run(): BuiltPlan {
            val sel = input.days.filter { it in 1..7 }.sorted()
            val name = "Custom plan"
            if (sel.isEmpty()) return BuiltPlan(PlanDraft(name, PROGRESSION_NOTE, emptyList()), emptyMap(), emptyMap())
            val layout = assign(sel)
            val finisher = input.cardio == Cardio.FINISHER || (input.cardio == Cardio.OWN_DAY && sel.size < 3)
            val builds = linkedMapOf<Int, DayBuild>()
            for ((d, f) in layout) {
                val light = sel.size == 7 && d == sel.last()
                var day = buildDay(f, light, finisher)
                // A conditioning day nothing fits (e.g. knee injury, bodyweight only) becomes a light full-body day.
                if (day.picks.isEmpty() && f == DayFocus.CONDITIONING) day = buildDay(DayFocus.FULL_BODY, true, false)
                builds[d] = day
            }
            for (b in builds.values) b.picks.sortBy { orderRank(it) }
            val reserved = builds.mapValues { (_, b) -> ((if (warmFor(b).isNotEmpty()) 5 else 0) + (if (coolFor(b).isNotEmpty()) 5 else 0)) * 60 }
            allocate(builds, reserved)
            val days = mutableListOf<PlanDraftDay>()
            val focusByDay = linkedMapOf<Int, DayFocus>()
            val why = linkedMapOf<Int, List<WhyTag>>()
            for ((d, b) in builds) {
                val out = finalizeDay(d, b) ?: continue
                days += out
                focusByDay[d] = b.focus
                why[d] = b.tags.toList()
            }
            return BuiltPlan(PlanDraft(name, PROGRESSION_NOTE, days), focusByDay, why)
        }
    }
}
