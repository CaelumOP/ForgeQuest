package com.forgequest.app.domain

import com.forgequest.app.data.db.ExerciseEntity
import com.forgequest.app.data.plan.PlanDraft
import com.forgequest.app.data.plan.PlanDraftItem
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * TEST ONLY: heuristic scorecard ported from `web/src/domain/plan-grader.ts`.
 * Never move to main sources. No clinical claims.
 */
object PlanGrader {

    private val MAJOR = listOf(
        Muscle.CHEST,
        Muscle.UPPER_BACK,
        Muscle.SHOULDERS,
        Muscle.BICEPS,
        Muscle.TRICEPS,
        Muscle.QUADS,
        Muscle.HAMSTRINGS,
        Muscle.GLUTES,
        Muscle.CALVES,
        Muscle.ABS,
    )

    private val MAIN = setOf(
        Muscle.CHEST,
        Muscle.UPPER_BACK,
        Muscle.SHOULDERS,
        Muscle.QUADS,
        Muscle.HAMSTRINGS,
        Muscle.GLUTES,
    )

    private val SPINAL_REGEX = Regex("^(back_squat|front_squat|deadlift|sumo_deadlift|romanian_deadlift|barbell_row|good_morning|overhead_press)$")
    private val ODD_REGEX = Regex("jump|hop|burpee")

    private fun clamp(n: Double): Double = max(0.0, min(1.0, n))
    private fun round(n: Double): Double = java.lang.Math.round(n * 100.0) / 100.0
    private fun spinal(e: ExerciseEntity): Boolean = SPINAL_REGEX.matches(e.id)

    fun safePair(a: ExerciseEntity, b: ExerciseEntity): Boolean =
        a.id != b.id &&
            !spinal(a) &&
            !spinal(b) &&
            a.primary.none { m -> m in b.primary || m in b.secondary } &&
            b.primary.none { m -> m in a.secondary }

    data class Scores(
        val efficiency: Double,
        val coverage: Double,
        val balance: Double,
        val quality: Double,
        val pairing: Double,
    )

    data class Grade(
        val total: Double,
        val scores: Scores,
        val estimatedMinutes: Int,
        val workingSets: Int,
        val savedMinutes: Double,
        val extraSets: Int,
        val reasons: List<String>,
    )

    private data class Opportunity(
        val day: Int,
        val a: ExerciseEntity,
        val b: ExerciseEntity,
        val cycle: Int,
    )

    fun gradePlan(
        draft: PlanDraft,
        catalog: Map<String, ExerciseEntity>,
        goal: TrainGoal,
        minutes: Int,
    ): Grade {
        val days = draft.days.filter { !it.isRest }
        val auditDays = RoutineAuditor.fromDraft(draft)
        val estimated = auditDays.sumOf { RoutineAuditor.estimateMinutes(it) }
        val weekly = LinkedHashMap<Muscle, Double>()
        val repeated = LinkedHashMap<String, Int>()
        val reasons = mutableListOf<String>()
        val restTarget = when (goal) {
            TrainGoal.STRENGTH -> 150
            TrainGoal.MUSCLE -> 120
            TrainGoal.GENERAL -> 90
            else -> 60
        }
        var work = 0
        var main = 0
        var rests = 0
        var extras = 0
        var duplicates = 0
        var staple = 0
        var odd = 0
        var first = 0
        var spinalExcess = 0
        var unsafe = 0
        var saved = 0
        val dayPrimary = mutableListOf<MutableMap<Muscle, Int>>()
        val opportunities = mutableListOf<Opportunity>()
        val daySavings = mutableListOf<Int>()

        days.forEachIndexed { index, day ->
            val items = day.items.filter { !WorkoutScore.isExtra(it.block) && it.sets > 0 }
            val primary = LinkedHashMap<Muscle, Int>()
            dayPrimary.add(primary)
            val seenSmall = HashSet<Muscle>()
            val seenIds = HashSet<String>()
            var spinalCount = 0
            val groups = LinkedHashMap<String, MutableList<PlanDraftItem>>()

            for (i in items) {
                val key = when {
                    i.supersetGroup != null -> "s:${i.supersetGroup}"
                    i.circuit -> "c:${i.block}"
                    else -> null
                }
                if (key != null) groups.getOrPut(key) { mutableListOf() }.add(i)
                work += i.sets
                rests += i.sets * i.restSeconds
                val e = catalog[i.exerciseId]
                if (e == null) {
                    reasons.add("Unknown catalog exercise: ${i.exerciseId}")
                    continue
                }
                if (e.primary.any { it in MAIN }) main += i.sets
                for ((m, c) in RoutineAuditor.credits(e)) {
                    weekly[m] = (weekly[m] ?: 0.0) + i.sets * c
                }
                for (m in e.primary.distinct()) {
                    primary[m] = (primary[m] ?: 0) + i.sets
                }
                val small = e.primary.filter { it == Muscle.ABS || it == Muscle.CALVES }
                if (small.any { it in seenSmall }) duplicates += i.sets
                seenSmall.addAll(small)
                if (e.id in PlanBuilder.STAPLE_IDS) staple += i.sets
                if ((e.id !in PlanBuilder.STAPLE_IDS && e.kind != ExerciseKind.WEIGHTED && (e.equipment.isEmpty() || e.equipment == "BODYWEIGHT")) ||
                    (goal == TrainGoal.STRENGTH && ODD_REGEX.containsMatchIn(e.id))
                ) {
                    odd += i.sets
                }
                if (spinal(e)) spinalCount++
                if (e.id !in seenIds) repeated[e.id] = (repeated[e.id] ?: 0) + 1
                seenIds.add(e.id)
            }

            val firstExercise = if (items.isNotEmpty()) catalog[items[0].exerciseId] else null
            if (firstExercise != null && PlanBuilder.isCompoundExercise(firstExercise)) first++
            spinalExcess += max(0, spinalCount - 2)
            extras += RoutineAuditor.estimateMinutes(auditDays[index]) - ceil(items.sumOf { it.sets * (40 + it.restSeconds) } / 60.0).toInt()

            for (group in groups.values) {
                for (a in 0 until group.size) {
                    for (b in a + 1 until group.size) {
                        val ea = catalog[group[a].exerciseId]
                        val eb = catalog[group[b].exerciseId]
                        if (ea == null || eb == null || !safePair(ea, eb)) unsafe++
                    }
                }
            }

            val used = HashSet<Int>()
            var saving = 0
            for (a in items.indices) {
                if (a in used || items[a].circuit || items[a].supersetGroup != null) continue
                for (b in a + 1 until items.size) {
                    if (b in used || items[b].circuit || items[b].supersetGroup != null) continue
                    val ea = catalog[items[a].exerciseId]
                    val eb = catalog[items[b].exerciseId]
                    if (ea == null || eb == null || !safePair(ea, eb)) continue
                    val savingPerRound = minOf(40, items[a].restSeconds, items[b].restSeconds)
                    saving += minOf(items[a].sets, items[b].sets) * savingPerRound
                    opportunities.add(Opportunity(index, ea, eb, 80 + items[a].restSeconds + items[b].restSeconds - savingPerRound))
                    used.add(a)
                    used.add(b)
                    break
                }
            }
            saved += saving
            daySavings.add(saving)
        }

        val findings = RoutineAuditor.audit(auditDays, catalog)
        val penalties = mapOf(
            RoutineAuditor.Kind.ABOVE_MRV to 3,
            RoutineAuditor.Kind.RECOVERY_OVERLAP to 2,
            RoutineAuditor.Kind.JUNK_VOLUME to 2,
            RoutineAuditor.Kind.PUSH_PULL to 3,
            RoutineAuditor.Kind.ISOLATION_FIRST to 1,
            RoutineAuditor.Kind.SHORT_PLAN to 0,
            RoutineAuditor.Kind.BELOW_MEV to 0,
        )
        var overlap = 0
        days.forEachIndexed { i, day ->
            val nextDow = day.dayOfWeek % 7 + 1
            val next = days.indexOfFirst { it.dayOfWeek == nextDow }
            if (next < 0) return@forEachIndexed
            for ((m, n) in dayPrimary[i]) {
                val other = dayPrimary[next][m] ?: 0
                if (other > 0 && (n < 6 || other < 6)) overlap++
            }
        }

        val factor = when (goal) {
            TrainGoal.MUSCLE -> 1.25
            TrainGoal.ENDURANCE, TrainGoal.FAT_LOSS -> 0.75
            else -> 1.0
        }
        var coverage = 0.0
        for (m in MAJOR) {
            val value = weekly[m] ?: 0.0
            val lm = RoutineAuditor.landmarkFor(m)
            val mevOr4 = if (lm.mev != 0) lm.mev.toDouble() else 4.0
            val target = min(lm.mrv.toDouble(), mevOr4 * factor)
            coverage += 25.0 / MAJOR.size * (0.65 * (if (value > 0.0) 1.0 else 0.0) + 0.35 * clamp(value / target))
            if (value < target) reasons.add("$m: $value/$target credited sets${if (value == 0.0) " (missing)" else ""}")
        }

        var repeats = 0
        for ((id, count) in repeated) {
            val e = catalog.getValue(id)
            if (id !in PlanBuilder.STAPLE_IDS && !PlanBuilder.isCompoundExercise(e)) {
                repeats += max(0, count - 2)
            }
        }

        val capacity = max(days.size.toDouble(), (minutes * days.size - extras) * 60.0 / (40 + restTarget))
        val density = clamp(main / max(1.0, capacity)) * clamp((minutes * days.size).toDouble() / max(1, estimated))
        val restQuality = clamp(1.0 - max(0.0, rests.toDouble() / max(1, work * 40 + rests) - restTarget.toDouble() / (40 + restTarget)))
        val useful = clamp(1.0 - duplicates.toDouble() / max(1, work) - max(0.0, extras.toDouble() / max(1, estimated) - 0.2))
        val scores = Scores(
            efficiency = round(if (work != 0) 15.0 * density + 5.0 * restQuality + 5.0 * useful else 0.0),
            coverage = round(coverage),
            balance = round(max(0.0, 20.0 - findings.sumOf { penalties.getValue(it.kind) } - 2 * spinalExcess - overlap)),
            quality = round(
                max(
                    0.0,
                    5.0 * first / max(1, days.size) +
                        5.0 * staple / max(1, work) +
                        (if (work != 0) 5.0 * (1.0 - odd.toDouble() / work) else 0.0) -
                        min(3, repeats),
                ),
            ),
            pairing = max(0, 15 - 5 * unsafe).toDouble(),
        )

        reasons.add(
            "Efficiency: $main main sets / ${round(capacity)} capacity; $estimated min including $extras extra-block min; " +
                "${round(100.0 * rests / max(1, work * 40 + rests))}% work/rest time resting; $duplicates duplicate abs/calf sets",
        )
        reasons.add("Quality: $first/${days.size} compound-first days; $staple/$work staple sets; $odd odd sets; $repeats unexplained repeats")
        findings.forEach { f -> reasons.add("Plan check ${f.kind}${if (f.muscle != null) " ${f.muscle}" else ""}: ${f.value}/${f.limit}") }
        if (overlap != 0 || spinalExcess != 0) {
            reasons.add("Recovery: $overlap sub-threshold consecutive muscle overlaps; $spinalExcess spinal lifts beyond two/day")
        }
        if (unsafe != 0) reasons.add("Pairing: $unsafe unsafe circuit/superset pairs")

        var extraSets = 0
        val projected = HashMap(weekly)
        days.forEachIndexed { index, _ ->
            var budget = max(0, min(daySavings[index], minutes * 60 - RoutineAuditor.estimateMinutes(auditDays[index]) * 60 + daySavings[index]))
            val options = opportunities.filter { it.day == index }.sortedBy { it.cycle }
            for (o in options) {
                val added = LinkedHashMap<Muscle, Double>()
                for (e in listOf(o.a, o.b)) {
                    for ((m, c) in RoutineAuditor.credits(e)) {
                        added[m] = (added[m] ?: 0.0) + c
                    }
                }
                while (budget >= o.cycle &&
                    added.all { (m, c) -> (projected[m] ?: 0.0) + c <= RoutineAuditor.landmarkFor(m).mrv } &&
                    (o.a.primary + o.b.primary).all { m -> (dayPrimary[index][m] ?: 0) + 1 <= 10 }
                ) {
                    budget -= o.cycle
                    extraSets += 2
                    for ((m, c) in added) projected[m] = (projected[m] ?: 0.0) + c
                    for (m in o.a.primary.distinct() + o.b.primary.distinct()) {
                        dayPrimary[index][m] = (dayPrimary[index][m] ?: 0) + 1
                    }
                }
            }
        }

        val total = round(scores.efficiency + scores.coverage + scores.balance + scores.quality + scores.pairing)
        return Grade(
            total = total,
            scores = scores,
            estimatedMinutes = estimated,
            workingSets = work,
            savedMinutes = round(saved / 60.0),
            extraSets = extraSets,
            reasons = reasons,
        )
    }
}
