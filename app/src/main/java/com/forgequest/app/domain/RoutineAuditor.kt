package com.forgequest.app.domain

import com.forgequest.app.data.ActivePlan
import com.forgequest.app.data.db.ExerciseEntity
import com.forgequest.app.data.plan.PlanDraft

/**
 * Pure weekly routine auditor (spec section 5 & A9).
 * Emits non-destructive coaching warnings for volume landmarks, push/pull balance,
 * exercise order, single-day junk volume, and back-to-back weekday recovery overlap.
 */
object RoutineAuditor {

    /** Warning category ordered from highest to lowest severity. */
    enum class Kind {
        ABOVE_MRV,
        RECOVERY_OVERLAP,
        JUNK_VOLUME,
        PUSH_PULL,
        BELOW_MEV,
        ISOLATION_FIRST,
        /** Info only: a short plan (2 or fewer training days, or about 25 minutes or less a session) replaces every BELOW_MEV. */
        SHORT_PLAN,
    }

    /** Minimal item view shared by [PlanDraft] and [ActivePlan] adapters. */
    data class AuditItem(
        val exerciseId: String,
        val sets: Int,
        val block: String = "Main",
        val position: Int = 0,
        val restSeconds: Int = 90,
        val circuit: Boolean = false,
        val supersetGroup: Int? = null,
    )

    /** One training day in the audited plan; [dayOfWeek] is 1..7 (Monday..Sunday) when scheduled by weekday. */
    data class AuditDay(
        val dayOfWeek: Int? = null,
        val items: List<AuditItem>,
    )

    /** Single deterministic audit finding sorted by [Kind] severity. */
    data class Finding(
        val kind: Kind,
        val muscle: Muscle? = null,
        val dayIndex: Int? = null,
        val value: Double,
        val limit: Double,
        val dayOfWeek: Int? = null,
    )

    /** Weekly working-set landmarks for one muscle: minimum that grows it, maximum most people recover from. */
    data class Landmark(val mev: Int, val mrv: Int)

    const val DAY_MUSCLE_LIMIT = 10
    const val OVERLAP_SETS = 6
    const val PUSH_PULL_MIN = 6
    const val SHORT_PLAN_MAX_DAYS = 2
    const val SHORT_PLAN_MAX_MINUTES = 25

    /**
     * The one counting rule for weekly volume, shared with [PlanBuilder]: each distinct primary muscle earns 1.0 per set,
     * each secondary muscle that is not also primary earns 0.5.
     */
    fun credits(ex: ExerciseEntity): Map<Muscle, Double> {
        val primary = ex.primary.distinct()
        val out = LinkedHashMap<Muscle, Double>()
        for (m in primary) out[m] = 1.0
        for (m in ex.secondary.distinct()) if (m !in primary) out[m] = 0.5
        return out
    }

    /** +1 for a push set, -1 for a pull set, 0 otherwise (same buckets as the baseline). */
    fun pushPull(ex: ExerciseEntity): Int = when (Baseline.bucketOf(ExerciseSwapEngine.profileFor(ex).pattern)) {
        PatternBucket.PUSH -> 1
        PatternBucket.PULL -> -1
        else -> 0
    }

    /** Push sets break balance once there are at least [PUSH_PULL_MIN] and they exceed 1.2 x the pull sets. */
    fun pushPullBroken(push: Int, pull: Int): Boolean = push >= PUSH_PULL_MIN && push * 10 > pull * 12

    val BELOW_MEV_MUSCLES = setOf(
        Muscle.CHEST,
        Muscle.UPPER_BACK,
        Muscle.SHOULDERS,
        Muscle.QUADS,
        Muscle.HAMSTRINGS,
        Muscle.GLUTES,
    )

    fun landmarkFor(muscle: Muscle): Landmark = when (muscle) {
        Muscle.CHEST -> Landmark(8, 22)
        Muscle.UPPER_BACK -> Landmark(10, 25)
        Muscle.SHOULDERS -> Landmark(8, 26)
        Muscle.BICEPS -> Landmark(8, 20)
        Muscle.TRICEPS -> Landmark(6, 18)
        Muscle.QUADS -> Landmark(8, 20)
        Muscle.HAMSTRINGS -> Landmark(6, 20)
        Muscle.GLUTES -> Landmark(4, 16)
        Muscle.CALVES -> Landmark(8, 20)
        Muscle.ABS -> Landmark(0, 25)
        Muscle.TRAPS -> Landmark(0, 26)
        else -> Landmark(0, 20)
    }

    /** Adapts a generated or shared [PlanDraft] into [AuditDay] entries, skipping rest days. */
    fun fromDraft(draft: PlanDraft): List<AuditDay> = draft.days
        .filter { !it.isRest }
        .map { d ->
            AuditDay(
                dayOfWeek = d.dayOfWeek,
                items = d.items.mapIndexed { idx, item ->
                    AuditItem(
                        exerciseId = item.exerciseId,
                        sets = item.sets,
                        block = item.block,
                        position = idx,
                        restSeconds = item.restSeconds,
                        circuit = item.circuit,
                        supersetGroup = item.supersetGroup,
                    )
                },
            )
        }

    /** Adapts the stored [ActivePlan] into [AuditDay] entries, skipping rest days. */
    fun fromActivePlan(plan: ActivePlan): List<AuditDay> = plan.days
        .filter { !it.day.isRest }
        .sortedBy { it.day.dayOfWeek }
        .map { d ->
            AuditDay(
                dayOfWeek = d.day.dayOfWeek,
                items = d.items.sortedBy { it.position }.map { item ->
                    AuditItem(
                        exerciseId = item.exerciseId,
                        sets = item.sets,
                        block = item.block,
                        position = item.position,
                        restSeconds = item.restSeconds,
                        circuit = item.circuit,
                        supersetGroup = item.supersetGroup,
                    )
                },
            )
        }

    /**
     * Audits working items across [days] (excluding warm-up/cool-down/extra blocks) and returns findings
     * sorted by [Kind] severity, then day index, then muscle ordinal.
     */
    fun audit(
        days: List<AuditDay>,
        exercises: Map<String, ExerciseEntity>,
        scheduleMode: ScheduleMode = ScheduleMode.WEEKDAY,
        shortPlanNote: Boolean = true,
    ): List<Finding> {
        val workingDays = days.map { day ->
            day.items
                .filter { !WorkoutScore.isExtra(it.block) && it.sets > 0 && it.exerciseId in exercises }
                .sortedBy { it.position }
        }
        if (workingDays.all { it.isEmpty() }) return emptyList()

        val findings = mutableListOf<Finding>()
        val weeklyVolume = HashMap<Muscle, Double>()
        var pushSets = 0
        var pullSets = 0
        val dayPrimarySetsByDow = HashMap<Int, Map<Muscle, Int>>()
        val indexByDow = HashMap<Int, Int>()

        workingDays.forEachIndexed { dayIdx, items ->
            val day = days[dayIdx]
            val dayPrimary = HashMap<Muscle, Int>()
            val seenIsoMuscles = HashSet<Muscle>()
            val reportedIsoFirst = HashSet<Muscle>()

            for (item in items) {
                val ex = exercises.getValue(item.exerciseId)
                val sets = item.sets
                val primaryMuscles = ex.primary.distinct()
                for ((m, c) in credits(ex)) {
                    weeklyVolume[m] = (weeklyVolume[m] ?: 0.0) + sets * c
                    if (c >= 1.0) dayPrimary[m] = (dayPrimary[m] ?: 0) + sets
                }
                when (pushPull(ex)) {
                    1 -> pushSets += sets
                    -1 -> pullSets += sets
                    else -> {}
                }

                if (PlanBuilder.isCompoundExercise(ex)) {
                    for (m in primaryMuscles) {
                        if (m in seenIsoMuscles && reportedIsoFirst.add(m)) {
                            findings += Finding(
                                kind = Kind.ISOLATION_FIRST,
                                muscle = m,
                                dayIndex = dayIdx,
                                value = 1.0,
                                limit = 0.0,
                                dayOfWeek = day.dayOfWeek,
                            )
                        }
                    }
                } else {
                    seenIsoMuscles.addAll(primaryMuscles)
                }
            }

            for ((m, total) in dayPrimary) {
                if (total > DAY_MUSCLE_LIMIT) {
                    findings += Finding(
                        kind = Kind.JUNK_VOLUME,
                        muscle = m,
                        dayIndex = dayIdx,
                        value = total.toDouble(),
                        limit = DAY_MUSCLE_LIMIT.toDouble(),
                        dayOfWeek = day.dayOfWeek,
                    )
                }
            }

            val dow = day.dayOfWeek
            if (dow != null && dow in 1..7 && items.isNotEmpty()) {
                dayPrimarySetsByDow[dow] = dayPrimary
                indexByDow[dow] = dayIdx
            }
        }

        for (m in Muscle.entries) {
            val vol = weeklyVolume[m] ?: 0.0
            val lm = landmarkFor(m)
            if (vol > lm.mrv) {
                findings += Finding(
                    kind = Kind.ABOVE_MRV,
                    muscle = m,
                    value = vol,
                    limit = lm.mrv.toDouble(),
                )
            } else if (m in BELOW_MEV_MUSCLES && vol < lm.mev) {
                findings += Finding(
                    kind = Kind.BELOW_MEV,
                    muscle = m,
                    value = vol,
                    limit = lm.mev.toDouble(),
                )
            }
        }

        if (pushPullBroken(pushSets, pullSets)) {
            findings += Finding(
                kind = Kind.PUSH_PULL,
                value = pushSets.toDouble(),
                limit = pullSets.toDouble(),
            )
        }

        if (scheduleMode == ScheduleMode.WEEKDAY) {
            for (dow in 1..7) {
                val first = dayPrimarySetsByDow[dow] ?: continue
                val nextDow = if (dow == 7) 1 else dow + 1
                val second = dayPrimarySetsByDow[nextDow] ?: continue
                for (m in Muscle.entries) {
                    val s1 = first[m] ?: 0
                    val s2 = second[m] ?: 0
                    if (s1 >= OVERLAP_SETS && s2 >= OVERLAP_SETS) {
                        findings += Finding(
                            kind = Kind.RECOVERY_OVERLAP,
                            muscle = m,
                            dayIndex = indexByDow[nextDow],
                            value = minOf(s1, s2).toDouble(),
                            limit = OVERLAP_SETS.toDouble(),
                            dayOfWeek = nextDow,
                        )
                    }
                }
            }
        }

        if (shortPlanNote && isShortPlan(days) && findings.any { it.kind == Kind.BELOW_MEV }) {
            findings.removeAll { it.kind == Kind.BELOW_MEV }
            findings += Finding(kind = Kind.SHORT_PLAN, value = workingDays.count { it.isNotEmpty() }.toDouble(), limit = SHORT_PLAN_MAX_DAYS.toDouble())
        }

        return findings.sortedWith(
            compareBy<Finding>(
                { it.kind.ordinal },
                { it.dayIndex ?: Int.MAX_VALUE },
                { it.muscle?.ordinal ?: Int.MAX_VALUE },
            ),
        )
    }

    /** Minutes one day takes: grouped sets/rounds with 40 s work + rest, plus 5 for a warm-up block and 5 for a cool-down. */
    fun estimateMinutes(day: AuditDay): Int {
        val workItems = day.items
            .filter { !WorkoutScore.isExtra(it.block) && it.sets > 0 }
            .sortedBy { it.position }
            .map {
                PlanBuilder.GroupedWorkItem(
                    sets = it.sets,
                    restSeconds = it.restSeconds,
                    block = it.block,
                    circuit = it.circuit,
                    supersetGroup = it.supersetGroup,
                )
            }
        val work = PlanBuilder.groupedWorkSeconds(workItems)
        val extras = (if (day.items.any { WorkoutScore.isWarmUp(it.block) }) 5 else 0) +
            (if (day.items.any { it.block.equals(PlanBuilder.COOL_DOWN_BLOCK, ignoreCase = true) }) 5 else 0)
        return kotlin.math.ceil(work / 60.0).toInt() + extras
    }

    /** At most [SHORT_PLAN_MAX_DAYS] training days, or an average session of [SHORT_PLAN_MAX_MINUTES] minutes or less. */
    fun isShortPlan(days: List<AuditDay>): Boolean {
        val training = days.filter { d -> d.items.any { !WorkoutScore.isExtra(it.block) && it.sets > 0 } }
        if (training.isEmpty()) return false
        if (training.size <= SHORT_PLAN_MAX_DAYS) return true
        return training.sumOf { estimateMinutes(it) } <= SHORT_PLAN_MAX_MINUTES * training.size
    }
}
