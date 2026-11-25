package com.forgequest.app.domain

import com.forgequest.app.data.db.ExerciseEntity
import com.forgequest.app.data.plan.PlanDraft
import com.forgequest.app.data.plan.PlanDraftDay
import com.forgequest.app.data.plan.PlanDraftItem
import com.forgequest.app.data.seed.ExerciseCatalog
import com.forgequest.app.domain.MovementPattern as MP
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

enum class TrainGoal { STRENGTH, MUSCLE, FAT_LOSS, ENDURANCE, GENERAL }
enum class Experience { NEW, SOME, SEASONED }
enum class Cardio { NONE, FINISHER, OWN_DAY }
enum class RepPref { AUTO, LOW, MID, HIGH }
enum class RestPref { AUTO, SHORT, LONG }
enum class WorkoutStyle { AUTO, STRAIGHT, SUPERSET, CIRCUIT }
enum class DayFocus { FULL_BODY, UPPER, LOWER, PUSH, PULL, LEGS, PUSH_LEGS, PULL_HINGE, CONDITIONING }

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
    val style: WorkoutStyle = WorkoutStyle.AUTO,
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
    val CIRCUIT_BLOCKS = listOf("Circuit A", "Circuit B", "Circuit C", "Circuit D")
    const val SUPERSET_POST_ROUND_SHORT = 40
    const val CIRCUIT_POST_ROUND_SHORT = 30
    const val CIRCUIT_POST_ROUND = 60
    val NO_PAIR_IDS: Set<String> = setOf(
        "back_squat", "front_squat", "deadlift", "sumo_deadlift",
        "romanian_deadlift", "barbell_row", "good_morning", "overhead_press",
    )
    const val PROGRESSION_NOTE =
        "Progression: when you hit the top of the range on every set, add load or reps. Every 4th week, halve the sets."

    /** Pairwise safety check for supersets and circuits: distinct, non-spinal, no shared primary or primary/secondary overlap. */
    fun safePair(a: ExerciseEntity, b: ExerciseEntity): Boolean =
        a.id != b.id &&
            a.id !in NO_PAIR_IDS &&
            b.id !in NO_PAIR_IDS &&
            a.primary.none { m -> m in b.primary || m in b.secondary } &&
            b.primary.none { m -> m in a.secondary }

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

    data class GroupedWorkItem(
        val sets: Int,
        val restSeconds: Int,
        val block: String = "Main",
        val circuit: Boolean = false,
        val supersetGroup: Int? = null,
    )

    /**
     * Total working seconds for a day's non-extra items (already filtered to sets > 0).
     * Straight item: sets * (40 + rest). Group (consecutive same non-null supersetGroup, or consecutive circuit=true in the
     * same block with supersetGroup null): rounds = min(sets), postRoundRest = last member's restSeconds, extra sets cost straight.
     */
    fun groupedWorkSeconds(items: List<GroupedWorkItem>): Int {
        var total = 0
        var i = 0
        while (i < items.size) {
            val cur = items[i]
            var end = i + 1
            if (cur.supersetGroup != null) {
                while (end < items.size && items[end].supersetGroup == cur.supersetGroup) end++
            } else if (cur.circuit) {
                while (
                    end < items.size &&
                    items[end].supersetGroup == null &&
                    items[end].circuit &&
                    items[end].block == cur.block
                ) {
                    end++
                }
            }
            if (end - i >= 2) {
                var rounds = items[i].sets
                for (j in i + 1 until end) {
                    if (items[j].sets < rounds) rounds = items[j].sets
                }
                val postRoundRest = items[end - 1].restSeconds
                total += rounds * ((end - i) * SET_SECONDS + postRoundRest)
                for (j in i until end) {
                    val extra = items[j].sets - rounds
                    if (extra > 0) total += extra * (SET_SECONDS + items[j].restSeconds)
                }
            } else {
                total += cur.sets * (SET_SECONDS + cur.restSeconds)
            }
            i = end
        }
        return total
    }

    /** Minutes the day takes: grouped work with 40 s sets + rest, plus a flat 5 for a warm-up and 5 for a cool-down. */
    fun estimateMinutes(day: PlanDraftDay): Int {
        val work = day.items
            .filter { !WorkoutScore.isExtra(it.block) && it.sets > 0 }
            .map { GroupedWorkItem(it.sets, it.restSeconds, it.block, it.circuit, it.supersetGroup) }
        val workSeconds = groupedWorkSeconds(work)
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

    private class Pick(
        val ex: ExerciseEntity,
        val role: Role,
        var sets: Int = 3,
        var supersetGroup: Int? = null,
        var circuitBlock: String? = null,
    )

    private data class PickSnapshot(
        val pick: Pick,
        val sets: Int,
        val supersetGroup: Int?,
        val circuitBlock: String?,
    )

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

    /** Preference order for the two warm-up moves per day focus: bodyweight/band easy rep moves only. */
    private val WARM_UP_UPPER_IDS = listOf("jumping_jacks", "band_pull_apart", "scapular_pull_up", "scapular_australian_pull_up", "knee_scapular_push_up")
    private val WARM_UP_LOWER_IDS = listOf("jumping_jacks", "half_squat", "squat", "hip_bridge")
    private val WARM_UP_FULL_IDS = listOf("jumping_jacks", "half_squat", "squat", "hip_bridge", "knee_scapular_push_up")
    private val WARM_UP_PULL_HINGE_IDS = listOf("jumping_jacks", "hip_bridge", "half_squat", "squat", "knee_scapular_push_up")

    private fun warmUpIdsFor(focus: DayFocus): List<String> = when (focus) {
        DayFocus.UPPER, DayFocus.PUSH, DayFocus.PULL -> WARM_UP_UPPER_IDS
        DayFocus.LOWER, DayFocus.LEGS -> WARM_UP_LOWER_IDS
        DayFocus.PULL_HINGE -> WARM_UP_PULL_HINGE_IDS
        DayFocus.FULL_BODY, DayFocus.PUSH_LEGS, DayFocus.CONDITIONING -> WARM_UP_FULL_IDS
    }

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
        DayFocus.PUSH_LEGS -> listOf(main(SQUAT), main(H_PUSH), main(UNILATERAL), main(V_PUSH), acc(EXTENSION), acc(CALF))
        DayFocus.PULL_HINGE -> listOf(main(HINGE), main(H_PULL), main(V_PULL), main(CORE), acc(KNEE_FLEX), acc(CURL))
        DayFocus.CONDITIONING -> List(3) { Slot(CONDITIONING, Role.COND) } + main(CORE)
    }

    private fun trains(day: DayFocus, m: Muscle): Boolean = when (day) {
        DayFocus.FULL_BODY -> true
        DayFocus.CONDITIONING -> false
        DayFocus.PUSH_LEGS -> m == Muscle.CHEST || m == Muscle.SHOULDERS || m == Muscle.TRICEPS || m == Muscle.QUADS || m == Muscle.CALVES
        DayFocus.PULL_HINGE -> m == Muscle.UPPER_BACK || m == Muscle.BICEPS || m == Muscle.TRAPS || m == Muscle.FOREARMS ||
            m == Muscle.HAMSTRINGS || m == Muscle.GLUTES || m in CORE_MUSCLES
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
        DayFocus.PUSH_LEGS -> "Push and legs"
        DayFocus.PULL_HINGE -> "Pull, hinge and core"
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

        private fun resolveStyle(d: Int): WorkoutStyle = when {
            input.style != WorkoutStyle.AUTO -> input.style
            input.goal == TrainGoal.FAT_LOSS -> WorkoutStyle.CIRCUIT
            dayMinutes(d) <= 30 -> WorkoutStyle.SUPERSET
            else -> WorkoutStyle.STRAIGHT
        }

        private fun assign(sel: List<Int>): Map<Int, DayFocus> {
            val own = input.cardio == Cardio.OWN_DAY && sel.size >= 3
            val extra = if (sel.size == 7 || own) sel.last() else null
            val lifting = if (extra != null) sel.dropLast(1) else sel
            val fatLossAB = input.goal == TrainGoal.FAT_LOSS && lifting.size >= 5
            val split = when {
                fatLossAB -> List(lifting.size) { i -> if (i % 2 == 0) DayFocus.PUSH_LEGS else DayFocus.PULL_HINGE }
                lifting.size <= 3 -> List(lifting.size) { DayFocus.FULL_BODY }
                lifting.size == 4 -> listOf(DayFocus.UPPER, DayFocus.LOWER, DayFocus.UPPER, DayFocus.LOWER)
                lifting.size == 5 -> listOf(DayFocus.UPPER, DayFocus.LOWER, DayFocus.PUSH, DayFocus.PULL, DayFocus.LEGS)
                else -> listOf(DayFocus.PUSH, DayFocus.PULL, DayFocus.LEGS, DayFocus.PUSH, DayFocus.PULL, DayFocus.LEGS)
            }
            val out = linkedMapOf<Int, DayFocus>()
            // Short days: full body for 1-3 lifting days; with 4+ lifting days alternate the clean A/B halves so no muscle
            // is trained hard on two days running.
            var shortIdx = 0
            lifting.forEachIndexed { i, d ->
                out[d] = when {
                    fatLossAB || dayMinutes(d) >= 30 -> split[i]
                    lifting.size < 4 -> DayFocus.FULL_BODY
                    else -> if (shortIdx++ % 2 == 0) DayFocus.PUSH_LEGS else DayFocus.PULL_HINGE
                }
            }
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

        // ---- supersets & circuits ----

        private fun isUpper(e: ExerciseEntity): Boolean =
            e.primary.any { it in UPPER_MUSCLES } &&
                e.primary.none { it in LOWER_MUSCLES || it in CORE_MUSCLES }

        private fun isLowerOrCore(e: ExerciseEntity): Boolean =
            isCore(prof(e).pattern) ||
                (e.primary.any { it in LOWER_MUSCLES || it in CORE_MUSCLES } &&
                    e.primary.none { it in UPPER_MUSCLES })

        private fun isPushPullOpposite(a: ExerciseEntity, b: ExerciseEntity): Boolean {
            val ppA = RoutineAuditor.pushPull(a)
            val ppB = RoutineAuditor.pushPull(b)
            return ppA != 0 && ppB != 0 && ppA == -ppB
        }

        private fun isUpperVsLowerOrCore(a: ExerciseEntity, b: ExerciseEntity): Boolean =
            (isUpper(a) && isLowerOrCore(b)) || (isLowerOrCore(a) && isUpper(b))

        private fun circuitCategory(p: Pick): Int {
            val pp = RoutineAuditor.pushPull(p.ex)
            return when {
                pp == 1 -> 0
                pp == -1 -> 1
                isLowerOrCore(p.ex) -> 2
                else -> 3
            }
        }

        private fun wouldPutIsolationFirst(c: Pick, picks: List<Pick>, skip: Set<Pick>): Boolean {
            if (isCompoundExercise(c.ex)) return false
            return picks.any { other ->
                other !in skip &&
                    other !== c &&
                    isCompoundExercise(other.ex) &&
                    c.ex.primary.any { it in other.ex.primary }
            }
        }

        private fun pairSupersets(picks: MutableList<Pick>) {
            val firstMain = if (input.goal == TrainGoal.STRENGTH) picks.firstOrNull { it.role == Role.MAIN } else null
            fun isCandidate(p: Pick) = (p.role == Role.MAIN || p.role == Role.FOCUS || p.role == Role.ACC) && p !== firstMain
            val paired = HashSet<Pick>()
            var nextGroup = 1
            var i = 0
            while (i < picks.size) {
                val a = picks[i]
                if (!isCandidate(a) || a in paired) {
                    i++
                    continue
                }
                val before = HashSet(paired)
                for (k in 0..i) before.add(picks[k])
                var matchIdx = -1
                for (j in i + 1 until picks.size) {
                    val b = picks[j]
                    if (!isCandidate(b) || b in paired) continue
                    if (safePair(a.ex, b.ex) && isPushPullOpposite(a.ex, b.ex) && !wouldPutIsolationFirst(b, picks, before)) {
                        matchIdx = j
                        break
                    }
                }
                if (matchIdx < 0) {
                    for (j in i + 1 until picks.size) {
                        val b = picks[j]
                        if (!isCandidate(b) || b in paired) continue
                        if (safePair(a.ex, b.ex) && isUpperVsLowerOrCore(a.ex, b.ex) && !wouldPutIsolationFirst(b, picks, before)) {
                            matchIdx = j
                            break
                        }
                    }
                }
                if (matchIdx >= 0) {
                    val b = picks.removeAt(matchIdx)
                    picks.add(i + 1, b)
                    a.supersetGroup = nextGroup
                    b.supersetGroup = nextGroup
                    paired.add(a)
                    paired.add(b)
                    nextGroup++
                    i += 2
                } else {
                    i++
                }
            }
        }

        private fun groupCircuits(picks: MutableList<Pick>) {
            val mainPicks = picks.filter { it.role == Role.MAIN || it.role == Role.FOCUS || it.role == Role.ACC }
            val otherPicks = picks.filter { it.role == Role.COND || it.role == Role.FINISHER }
            val assigned = HashSet<Pick>()
            val groups = mutableListOf<MutableList<Pick>>()
            var circuitCount = 0
            for (first in mainPicks) {
                if (first in assigned) continue
                assigned.add(first)
                val group = mutableListOf(first)
                while (group.size < 3) {
                    val haveCats = group.mapTo(HashSet()) { circuitCategory(it) }
                    var next: Pick? = null
                    for (wantedCat in 0..2) {
                        if (wantedCat in haveCats) continue
                        for (c in mainPicks) {
                            if (c in assigned || circuitCategory(c) != wantedCat) continue
                            if (group.all { m -> safePair(m.ex, c.ex) } && !wouldPutIsolationFirst(c, mainPicks, assigned)) {
                                next = c
                                break
                            }
                        }
                        if (next != null) break
                    }
                    if (next == null) {
                        for (c in mainPicks) {
                            if (c in assigned) continue
                            if (group.all { m -> safePair(m.ex, c.ex) } && !wouldPutIsolationFirst(c, mainPicks, assigned)) {
                                next = c
                                break
                            }
                        }
                    }
                    if (next == null) break
                    assigned.add(next)
                    group.add(next)
                }
                if (group.size == 1) {
                    group[0].circuitBlock = null
                    groups.add(group)
                } else {
                    group.sortBy { circuitCategory(it) }
                    val name = CIRCUIT_BLOCKS.getOrElse(circuitCount) { "Circuit ${'A' + circuitCount}" }
                    for (p in group) p.circuitBlock = name
                    circuitCount++
                    groups.add(group)
                }
            }
            val reordered = groups.flatten() + otherPicks
            picks.clear()
            picks.addAll(reordered)
        }

        private fun removePick(picks: MutableList<Pick>, idx: Int) {
            if (idx !in picks.indices) return
            val removed = picks.removeAt(idx)
            if (removed.supersetGroup != null) {
                for (p in picks) {
                    if (p.supersetGroup == removed.supersetGroup) p.supersetGroup = null
                }
            }
            if (removed.circuitBlock != null) {
                val rem = picks.filter { it.circuitBlock == removed.circuitBlock }
                if (rem.size == 1) rem[0].circuitBlock = null
            }
        }

        private fun renumberSupersets(picks: MutableList<Pick>) {
            var next = 1
            var i = 0
            while (i < picks.size) {
                val g = picks[i].supersetGroup
                if (g != null && i + 1 < picks.size && picks[i + 1].supersetGroup == g) {
                    picks[i].supersetGroup = next
                    picks[i + 1].supersetGroup = next
                    next++
                    i += 2
                } else {
                    picks[i].supersetGroup = null
                    i++
                }
            }
        }

        private fun renumberCircuits(picks: MutableList<Pick>) {
            var nextIdx = 0
            var i = 0
            while (i < picks.size) {
                val blk = picks[i].circuitBlock
                if (blk != null) {
                    var end = i + 1
                    while (end < picks.size && picks[end].circuitBlock == blk) end++
                    if (end - i >= 2) {
                        val name = CIRCUIT_BLOCKS.getOrElse(nextIdx) { "Circuit ${'A' + nextIdx}" }
                        for (j in i until end) picks[j].circuitBlock = name
                        nextIdx++
                    } else {
                        picks[i].circuitBlock = null
                    }
                    i = end
                } else {
                    i++
                }
            }
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

        private fun baseRestFor(p: Pick): Int {
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
            return floor(seconds * factor + 0.5).toInt().coerceIn(30, 240)
        }

        private fun restFor(d: Int, picks: List<Pick>, p: Pick): Int {
            if (p.role == Role.COND || p.role == Role.FINISHER) return CONDITIONING_REST
            if (p.supersetGroup != null) {
                val partner = picks.firstOrNull { it !== p && it.supersetGroup == p.supersetGroup }
                if (partner != null) {
                    return if (dayMinutes(d) <= 30) {
                        SUPERSET_POST_ROUND_SHORT
                    } else {
                        max(baseRestFor(p), baseRestFor(partner))
                    }
                }
            }
            if (p.circuitBlock != null) {
                val size = picks.count { it.circuitBlock == p.circuitBlock }
                if (size >= 2) {
                    if (dayMinutes(d) <= 30) {
                        return if (size == 2) SUPERSET_POST_ROUND_SHORT else CIRCUIT_POST_ROUND_SHORT
                    }
                    return CIRCUIT_POST_ROUND
                }
            }
            return baseRestFor(p)
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
            fun fitsMuscleRules(dayIdx: Int, p: Pick): Boolean = fitsMuscleRulesGroup(dayIdx, listOf(p))

            /** True when +1 set for every pick in [group] on [dayIdx] keeps MRV, day-muscle limit, and back-to-back overlap. */
            fun fitsMuscleRulesGroup(dayIdx: Int, group: List<Pick>): Boolean {
                val weekDelta = HashMap<Muscle, Double>()
                val dayDelta = HashMap<Muscle, Int>()
                for (p in group) {
                    for ((m, c) in RoutineAuditor.credits(p.ex)) {
                        weekDelta[m] = (weekDelta[m] ?: 0.0) + c
                        if (c >= 1.0) dayDelta[m] = (dayDelta[m] ?: 0) + 1
                    }
                }
                for ((m, d) in weekDelta) {
                    if ((week[m] ?: 0.0) + d > RoutineAuditor.landmarkFor(m).mrv + 1e-9) return false
                }
                for ((m, d) in dayDelta) {
                    val after = (dayPrimary[dayIdx][m] ?: 0) + d
                    if (after > RoutineAuditor.DAY_MUSCLE_LIMIT || overlaps(dayIdx, m, after)) return false
                }
                return true
            }

            /** True when one more set of [p] on [dayIdx] keeps every Plan check rule. */
            fun fits(dayIdx: Int, p: Pick): Boolean = fitsGroup(dayIdx, listOf(p))

            /** True when +1 set for every pick in [group] on [dayIdx] keeps every Plan check rule. */
            fun fitsGroup(dayIdx: Int, group: List<Pick>): Boolean {
                if (!fitsMuscleRulesGroup(dayIdx, group)) return false
                var nextPush = push
                var nextPull = pull
                for (p in group) {
                    when (RoutineAuditor.pushPull(p.ex)) {
                        1 -> nextPush++
                        -1 -> nextPull++
                        else -> {}
                    }
                }
                if (nextPush == push) return true
                return !RoutineAuditor.pushPullBroken(nextPush, nextPull)
            }

            /** How much one more set of [p] closes the gap to the weekly targets. */
            fun gain(p: Pick): Double = RoutineAuditor.credits(p.ex).entries.sumOf { (m, c) ->
                minOf(c, maxOf(0.0, target(m) - (week[m] ?: 0.0)))
            }

            /** Portion of one more set of [p] that closes a remaining BELOW_MEV deficit. */
            fun mevGain(p: Pick): Double = RoutineAuditor.credits(p.ex).entries.sumOf { (m, c) ->
                if (m !in RoutineAuditor.BELOW_MEV_MUSCLES) 0.0
                else minOf(c, maxOf(0.0, RoutineAuditor.landmarkFor(m).mev - (week[m] ?: 0.0)))
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

        private fun groupOf(picks: List<Pick>, p: Pick): List<Pick> = when {
            p.supersetGroup != null -> picks.filter { it.supersetGroup == p.supersetGroup }
            p.circuitBlock != null -> picks.filter { it.circuitBlock == p.circuitBlock }
            else -> listOf(p)
        }

        private fun groupAddSeconds(d: Int, picks: List<Pick>, group: List<Pick>): Int {
            if (group.size >= 2) {
                val postRoundRest = restFor(d, picks, group.last())
                return group.size * SET_SECONDS + postRoundRest
            }
            return SET_SECONDS + restFor(d, picks, group[0])
        }

        private fun equalizeGroups(days: List<Pair<Int, DayBuild>>, reserved: Map<Int, Int>) {
            for ((dow, b) in days) {
                val seen = HashSet<Pick>()
                for (p in b.picks) {
                    if (p in seen) continue
                    val group = groupOf(b.picks, p)
                    seen.addAll(group)
                    if (group.size < 2) continue
                    val minSets = group.minOf { it.sets }
                    val maxSetsVal = group.maxOf { it.sets }
                    if (minSets == maxSetsVal) continue
                    for (m in group) m.sets = maxSetsVal
                    val budget = budgetSeconds(dow, reserved.getValue(dow))
                    val withinBudget = workSeconds(dow, b.picks) <= budget
                    val vol = Volume(days)
                    if (!withinBudget || maxSetsVal > maxSets || vol.broken() != null) {
                        for (m in group) m.sets = minSets
                    }
                }
            }
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
                while (workSeconds(d, b.picks) > budget && b.picks.size > 1) {
                    val drop = listOf<(Pick) -> Boolean>(
                        { it.role == Role.FINISHER },
                        { it.role == Role.ACC },
                        { it.role != Role.FOCUS && isCore(prof(it.ex).pattern) },
                        { it.role != Role.FOCUS },
                        { true },
                    ).firstNotNullOfOrNull { match -> b.picks.indexOfLast(match).takeIf { it >= 0 } } ?: break
                    removePick(b.picks, drop)
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
                val idx = days[victim.first].second.picks.indexOf(victim.second)
                if (idx >= 0) removePick(days[victim.first].second.picks, idx)
                vol = Volume(days)
            }
            vol = fill(days, reserved, vol)
            // Still short of a minimum: trade one whole lift that does not feed the short muscle for more sets of those that do.
            for (round in 0 until 20) {
                val short = vol.shortfall()
                if (short <= 1e-9) break
                val shortMuscles = vol.shortMuscles()
                val options = days.indices.flatMap { i ->
                    val (dow, dayBuild) = days[i]
                    if (dayBuild.picks.size <= 2) emptyList()
                    else {
                        val keepFirstMain =
                            if (input.goal == TrainGoal.STRENGTH && resolveStyle(dow) == WorkoutStyle.SUPERSET) {
                                dayBuild.picks.firstOrNull { it.role == Role.MAIN }
                            } else {
                                null
                            }
                        dayBuild.picks.filter { q ->
                            q.role != Role.FOCUS && q !== keepFirstMain &&
                                RoutineAuditor.credits(q.ex).keys.none { it in shortMuscles }
                        }.map { i to it }
                    }
                }.sortedWith(compareBy<Pair<Int, Pick>>({ removalRank(it.second) }, { -it.first }))
                var improved = false
                for ((i, q) in options) {
                    val snapshot = days.map { d ->
                        d.second.picks.map { PickSnapshot(it, it.sets, it.supersetGroup, it.circuitBlock) }
                    }
                    val idx = days[i].second.picks.indexOf(q)
                    if (idx >= 0) removePick(days[i].second.picks, idx)
                    val trial = fill(days, reserved, Volume(days))
                    if (trial.broken() == null && trial.shortfall() < short - 1e-9) {
                        vol = trial
                        improved = true
                        break
                    }
                    for ((d, saved) in days.zip(snapshot)) {
                        d.second.picks.clear()
                        for (s in saved) {
                            s.pick.sets = s.sets
                            s.pick.supersetGroup = s.supersetGroup
                            s.pick.circuitBlock = s.circuitBlock
                            d.second.picks += s.pick
                        }
                    }
                }
                if (!improved) break
            }
            topUpShortDays(days, reserved, vol)
            equalizeGroups(days, reserved)
            for ((_, b) in days) {
                renumberSupersets(b.picks)
                renumberCircuits(b.picks)
            }
        }

        /**
         * When grouped circuits/supersets leave a >25-min request averaging <= SHORT_PLAN_MAX_MINUTES, add extra rounds up
         * to maxSets while every Plan check rule and the day budget hold, so the plan fills the chosen time.
         * Only as a last resort, if the plan still averages <= SHORT_PLAN_MAX_MINUTES, disband circuits one at a time on
         * non-light >30-min days (shortest day first, within budget) so exercises keep normal straight rest instead of 60 s.
         */
        private fun topUpShortDays(days: List<Pair<Int, DayBuild>>, reserved: Map<Int, Int>, vol: Volume) {
            if (days.size <= 2) return
            val longRequested = days.all { (dow, _) -> dayMinutes(dow) > RoutineAuditor.SHORT_PLAN_MAX_MINUTES }
            if (!longRequested) return
            fun estDay(dow: Int, b: DayBuild): Int {
                val resMin = floor((reserved[dow] ?: 0) / 60.0 + 0.5).toInt()
                return ceil(workSeconds(dow, b.picks) / 60.0).toInt() + resMin
            }
            fun estTotal(): Int = days.sumOf { (dow, b) -> estDay(dow, b) }
            val cap = maxSets
            while (estTotal() <= RoutineAuditor.SHORT_PLAN_MAX_MINUTES * days.size) {
                var bestIdx = -1
                var bestGroup: List<Pick>? = null
                var bestScore = -1
                for (i in days.indices) {
                    val (dow, b) = days[i]
                    if (b.light || resolveStyle(dow) == WorkoutStyle.STRAIGHT) continue
                    val spare = budgetSeconds(dow, reserved.getValue(dow)) - workSeconds(dow, b.picks)
                    val seen = HashSet<Pick>()
                    for (p in b.picks) {
                        if (p in seen) continue
                        val group = groupOf(b.picks, p)
                        seen.addAll(group)
                        if (!group.all { it.role != Role.COND && it.role != Role.FINISHER && it.sets < cap }) continue
                        if (groupAddSeconds(dow, b.picks, group) > spare) continue
                        if (!vol.fitsGroup(i, group)) continue
                        val minSets = group.minOf { it.sets }
                        val score = (cap - minSets) * 100 + (cap * group.size) - i
                        if (score > bestScore) {
                            bestIdx = i
                            bestGroup = group
                            bestScore = score
                        }
                    }
                }
                val chosen = bestGroup ?: break
                for (m in chosen) {
                    m.sets++
                    vol.add(bestIdx, m, 1)
                }
            }
            while (estTotal() <= RoutineAuditor.SHORT_PLAN_MAX_MINUTES * days.size) {
                var bestDay: DayBuild? = null
                var bestBlock: String? = null
                var bestDayEst = Int.MAX_VALUE
                for (i in days.indices) {
                    val (dow, b) = days[i]
                    if (b.light || dayMinutes(dow) <= 30) continue
                    val curEst = estDay(dow, b)
                    val curSec = workSeconds(dow, b.picks)
                    val budget = budgetSeconds(dow, reserved.getValue(dow))
                    val seenBlocks = HashSet<String>()
                    for (p in b.picks) {
                        val blk = p.circuitBlock ?: continue
                        if (!seenBlocks.add(blk)) continue
                        val members = b.picks.filter { it.circuitBlock == blk }
                        if (members.size < 2) continue
                        for (m in members) m.circuitBlock = null
                        val nextSec = workSeconds(dow, b.picks)
                        for (m in members) m.circuitBlock = blk
                        if (nextSec > curSec && nextSec <= budget && curEst < bestDayEst) {
                            bestDayEst = curEst
                            bestDay = b
                            bestBlock = blk
                            break
                        }
                    }
                }
                if (bestDay == null || bestBlock == null) break
                for (m in bestDay.picks) {
                    if (m.circuitBlock == bestBlock) m.circuitBlock = null
                }
            }
        }

        /** Adds one set at a time to the pick that best closes the gap to the weekly targets while every rule still holds. */
        private fun fill(days: List<Pair<Int, DayBuild>>, reserved: Map<Int, Int>, start: Volume): Volume {
            val vol = start
            val cap = maxSets
            while (true) {
                val pushWanted = vol.pushBlocked() && days.withIndex().any { (i, d) ->
                    val spare = budgetSeconds(d.first, reserved.getValue(d.first)) - workSeconds(d.first, d.second.picks)
                    !d.second.light && d.second.picks.any { p ->
                        if (p.role == Role.COND || p.role == Role.FINISHER || RoutineAuditor.pushPull(p.ex) != 1) {
                            false
                        } else {
                            val group = groupOf(d.second.picks, p)
                            group.all { it.role != Role.COND && it.role != Role.FINISHER && it.sets < cap } &&
                                groupAddSeconds(d.first, d.second.picks, group) <= spare &&
                                vol.gain(p) >= 0.5 - 1e-9 &&
                                vol.fitsMuscleRulesGroup(i, group)
                        }
                    }
                }
                var bestIdx = -1
                var bestPick: Pick? = null
                var bestGain = 0.0
                for ((i, d) in days.withIndex()) {
                    val (dow, b) = d
                    if (b.light) continue
                    val spare = budgetSeconds(dow, reserved.getValue(dow)) - workSeconds(dow, b.picks)
                    for (p in b.picks) {
                        if (p.role == Role.COND || p.role == Role.FINISHER || p.sets >= cap) continue
                        val group = groupOf(b.picks, p)
                        if (!group.all { it.role != Role.COND && it.role != Role.FINISHER && it.sets < cap }) continue
                        if (groupAddSeconds(dow, b.picks, group) > spare) continue
                        var g = vol.gain(p)
                        val boosted = g < 0.5 - 1e-9 && pushWanted && RoutineAuditor.pushPull(p.ex) == -1
                        if (boosted) g = 0.5
                        if (g < 0.5 - 1e-9 || !vol.fitsGroup(i, group)) continue
                        val baseGain = if (group.size == 1) {
                            g
                        } else {
                            group.sumOf { m -> if (m === p && boosted) 0.5 else vol.gain(m) } / group.size
                        }
                        val mevPart = if (resolveStyle(dow) != WorkoutStyle.STRAIGHT) {
                            group.sumOf { m -> vol.mevGain(m) } / group.size
                        } else {
                            0.0
                        }
                        val rankGain = baseGain + mevPart * 10
                        if (bestPick == null || rankGain > bestGain + 1e-9) {
                            bestIdx = i; bestPick = p; bestGain = rankGain
                        }
                    }
                }
                val p = bestPick ?: break
                val bestGroup = groupOf(days[bestIdx].second.picks, p)
                for (m in bestGroup) {
                    m.sets++
                    vol.add(bestIdx, m, 1)
                }
            }
            return vol
        }

        private fun blockFor(picks: List<Pick>, p: Pick): String {
            if (p.role == Role.FINISHER) return FINISHER_BLOCK
            if (p.circuitBlock != null) {
                val count = picks.count { it.circuitBlock == p.circuitBlock }
                if (count >= 2) return p.circuitBlock!!
            }
            return "Main"
        }

        private fun isCircuit(picks: List<Pick>, p: Pick): Boolean {
            if (p.role == Role.COND || p.role == Role.FINISHER) return true
            if (p.circuitBlock != null) {
                return picks.count { it.circuitBlock == p.circuitBlock } >= 2
            }
            return false
        }

        private fun item(d: Int, picks: List<Pick>, p: Pick): PlanDraftItem {
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
                block = blockFor(picks, p),
                circuit = isCircuit(picks, p),
                supersetGroup = p.supersetGroup,
                sets = p.sets, repsMin = repsMin, repsMax = repsMax, seconds = seconds, restSeconds = restFor(d, picks, p),
            )
        }

        private fun warmUpItems(focus: DayFocus, used: Set<String>): List<PlanDraftItem> {
            if (!input.warmUp) return emptyList()
            val byId = eligible.associateBy { it.id }
            return warmUpIdsFor(focus).mapNotNull { byId[it] }
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

        private fun workSeconds(d: Int, picks: List<Pick>): Int = groupedWorkSeconds(
            picks.map { p ->
                GroupedWorkItem(
                    sets = p.sets,
                    restSeconds = restFor(d, picks, p),
                    block = blockFor(picks, p),
                    circuit = isCircuit(picks, p),
                    supersetGroup = p.supersetGroup,
                )
            },
        )

        private fun orderRank(p: Pick): Int {
            if (p.role == Role.FINISHER) return 3
            val pattern = prof(p.ex).pattern
            return when {
                isCore(pattern) -> 2
                isCompoundExercise(p.ex) -> 0
                else -> 1
            }
        }

        private fun warmFor(day: DayBuild) = warmUpItems(day.focus, day.picks.map { it.ex.id }.toSet())
        private fun coolFor(day: DayBuild) = coolDownItems(day.picks.map { it.ex.id }.toSet(), day.picks.flatMap { it.ex.primary }.toSet())

        /** Turns a day's picks (already sized to the time by [allocate]) into a draft day: big lifts first, core, then finisher (unless paired/circuited). */
        private fun finalizeDay(d: Int, day: DayBuild): PlanDraftDay? {
            if (day.picks.isEmpty()) return null
            val warm = warmFor(day)
            val cool = coolFor(day)
            if (resolveStyle(d) == WorkoutStyle.STRAIGHT) {
                day.picks.sortBy { orderRank(it) }
            }
            return PlanDraftDay(dayOfWeek = d, title = titleFor(day.focus), items = warm + day.picks.map { item(d, day.picks, it) } + cool)
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
            for ((d, b) in builds) {
                b.picks.sortBy { orderRank(it) }
                when (resolveStyle(d)) {
                    WorkoutStyle.SUPERSET -> pairSupersets(b.picks)
                    WorkoutStyle.CIRCUIT -> groupCircuits(b.picks)
                    WorkoutStyle.STRAIGHT, WorkoutStyle.AUTO -> {}
                }
            }
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
