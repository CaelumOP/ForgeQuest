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
    }

    /** Minimal item view shared by [PlanDraft] and [ActivePlan] adapters. */
    data class AuditItem(
        val exerciseId: String,
        val sets: Int,
        val block: String = "Main",
        val position: Int = 0,
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

    private data class Landmark(val mev: Int, val mrv: Int)

    private val BELOW_MEV_MUSCLES = setOf(
        Muscle.CHEST,
        Muscle.UPPER_BACK,
        Muscle.SHOULDERS,
        Muscle.QUADS,
        Muscle.HAMSTRINGS,
        Muscle.GLUTES,
    )

    private fun landmarkFor(muscle: Muscle): Landmark = when (muscle) {
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
                val secondaryMuscles = ex.secondary.distinct().filter { it !in primaryMuscles }

                for (m in primaryMuscles) {
                    weeklyVolume[m] = (weeklyVolume[m] ?: 0.0) + sets
                    dayPrimary[m] = (dayPrimary[m] ?: 0) + sets
                }
                for (m in secondaryMuscles) {
                    weeklyVolume[m] = (weeklyVolume[m] ?: 0.0) + sets * 0.5
                }

                val pattern = ExerciseSwapEngine.profileFor(ex).pattern
                when (Baseline.bucketOf(pattern)) {
                    PatternBucket.PUSH -> pushSets += sets
                    PatternBucket.PULL -> pullSets += sets
                    else -> {}
                }

                if (PlanBuilder.isCompound(pattern)) {
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
                if (total > 10) {
                    findings += Finding(
                        kind = Kind.JUNK_VOLUME,
                        muscle = m,
                        dayIndex = dayIdx,
                        value = total.toDouble(),
                        limit = 10.0,
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

        if (pushSets >= 6 && pushSets * 10 > pullSets * 12) {
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
                    if (s1 >= 6 && s2 >= 6) {
                        findings += Finding(
                            kind = Kind.RECOVERY_OVERLAP,
                            muscle = m,
                            dayIndex = indexByDow[nextDow],
                            value = minOf(s1, s2).toDouble(),
                            limit = 6.0,
                            dayOfWeek = nextDow,
                        )
                    }
                }
            }
        }

        return findings.sortedWith(
            compareBy<Finding>(
                { it.kind.ordinal },
                { it.dayIndex ?: Int.MAX_VALUE },
                { it.muscle?.ordinal ?: Int.MAX_VALUE },
            ),
        )
    }
}
