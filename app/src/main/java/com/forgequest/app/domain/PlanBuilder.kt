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

    private val LOAD_EQUIPMENT = setOf(Equipment.DUMBBELLS, Equipment.KETTLEBELL, Equipment.BARBELL_RACK, Equipment.CABLES_MACHINES)
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
        DayFocus.PULL -> listOf(main(V_PULL), main(H_PULL), main(BACK_PULL), main(CORE), acc(CURL), acc(HINGE))
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
        private val allowedEq = input.equipment + Equipment.BODYWEIGHT
        private val loadEquip = input.equipment.any { it in LOAD_EQUIPMENT }
        private val lib = library.distinctBy { it.id }.sortedBy { it.id }
        private val profiles = lib.associate { it.id to ExerciseSwapEngine.profileFor(it) }
        private val focus = input.focus.sortedBy { it.ordinal }.take(3).toSet()
        private val weekUsed = mutableSetOf<String>()

        private fun prof(e: ExerciseEntity) = profiles.getValue(e.id)

        private val equipOk = lib.filter { prof(it).equipment in allowedEq && it.id !in input.avoidIds }
        private val eligible = equipOk.filter { it.id !in ExerciseCatalog.STRETCH_IDS && !blocked(it, prof(it), input.injuries) }
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

        private fun best(cands: List<ExerciseEntity>, role: Role): ExerciseEntity {
            val sorted = cands.sortedWith(compareBy<ExerciseEntity>({ -score(it, role) }, { it.id }))
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
                val fresh = cands.filter { it.id !in weekUsed }.ifEmpty { cands }
                val chosen = best(fresh, slot.role)
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
            }.sortedWith(compareBy<ExerciseEntity>({ -score(it, Role.ACC) }, { it.id })).take(limit)
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

        private fun baseRest(): Int {
            val seconds = when (input.goal) {
                TrainGoal.STRENGTH -> 150
                TrainGoal.MUSCLE -> 90
                TrainGoal.FAT_LOSS -> 45
                TrainGoal.ENDURANCE -> 30
                TrainGoal.GENERAL -> 75
            }
            val factor = when (input.rest) { RestPref.AUTO -> 1.0; RestPref.SHORT -> 0.6; RestPref.LONG -> 1.5 }
            return (seconds * factor).roundToInt().coerceIn(30, 240)
        }

        private fun restFor(role: Role) = if (role == Role.COND || role == Role.FINISHER) CONDITIONING_REST else baseRest()

        private fun target(m: Muscle): Double {
            val weekly = when (input.experience) { Experience.NEW -> 8; Experience.SOME -> 12; Experience.SEASONED -> 16 }
            return weekly * (if (m in MAJOR_MUSCLES) 1.0 else 0.5) + (if (m in focus) 4 else 0)
        }

        private fun assignSets(builds: Collection<DayBuild>) {
            val counts = HashMap<Muscle, Int>()
            for (b in builds) for (p in b.picks) if (p.role == Role.MAIN || p.role == Role.FOCUS || p.role == Role.ACC) {
                counts.merge(p.ex.primary.firstOrNull() ?: Muscle.ABS, 1, Int::plus)
            }
            for (b in builds) for (p in b.picks) {
                p.sets = when (p.role) {
                    Role.COND -> 3
                    Role.FINISHER -> 2
                    else -> {
                        val m = p.ex.primary.firstOrNull() ?: Muscle.ABS
                        (target(m) / counts.getValue(m)).roundToInt().coerceIn(2, 5)
                    }
                }
                if (b.light) p.sets = min(p.sets, 3)
            }
        }

        private fun item(p: Pick): PlanDraftItem {
            val (lo, hi) = if (p.role == Role.COND || p.role == Role.FINISHER) 12 to 20 else repRange()
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
                sets = p.sets, repsMin = repsMin, repsMax = repsMax, seconds = seconds, restSeconds = restFor(p.role),
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

        private fun workSeconds(picks: List<Pick>) = picks.sumOf { it.sets * (SET_SECONDS + restFor(it.role)) }

        /** Turns a day's picks into a draft day that fits the time: extras and accessories go first, then sets down to 2. */
        private fun finalizeDay(d: Int, day: DayBuild): PlanDraftDay? {
            if (day.picks.isEmpty()) return null
            val warm = warmUpItems(day.picks.map { it.ex.id }.toSet())
            val cool = coolDownItems(day.picks.map { it.ex.id }.toSet(), day.picks.flatMap { it.ex.primary }.toSet())
            val reservedSeconds = ((if (warm.isNotEmpty()) 5 else 0) + (if (cool.isNotEmpty()) 5 else 0)) * 60
            val budgetSeconds = (dayMinutes(d) + TOLERANCE_MIN) * 60
            val picks = day.picks
            var trimmed = false
            while (workSeconds(picks) + reservedSeconds > budgetSeconds) {
                val drop = listOf(Role.FINISHER, Role.ACC, Role.FOCUS).firstNotNullOfOrNull { r -> picks.indexOfLast { it.role == r }.takeIf { it >= 0 } }
                if (drop != null) {
                    picks.removeAt(drop)
                } else {
                    val cut = picks.indices.filter { picks[it].sets > 2 }.maxWithOrNull(compareBy<Int>({ picks[it].sets }, { it }))
                    if (cut != null) picks[cut].sets-- else if (picks.size > 1) picks.removeAt(picks.lastIndex) else break
                }
                trimmed = true
            }
            if (trimmed) day.tags += WhyTag.TIME_TRIMMED
            return PlanDraftDay(
                dayOfWeek = d,
                title = titleFor(day.focus),
                items = warm + picks.map { item(it) } + cool,
            )
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
            assignSets(builds.values)
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
