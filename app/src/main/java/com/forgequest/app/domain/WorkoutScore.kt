package com.forgequest.app.domain

import com.forgequest.app.data.db.SessionEntity
import com.forgequest.app.data.db.SetEntity

/**
 * The workout score (4..10), calculated from the workout's own numbers so it shows at once, with or without an AI key:
 * 3 for showing up, up to 3 for finishing the plan, up to 3 for beating last time, and 1 for a personal record.
 * Rule v2: docs/math/workout-score.md.
 */
object WorkoutScore {
    enum class Trend { UP, SAME, DOWN }

    /** The block name the starter plans give warm-ups (the stored value, not the translated label). */
    const val WARM_UP_BLOCK = "Warm-up"

    fun isWarmUp(block: String) = block.equals(WARM_UP_BLOCK, ignoreCase = true)

    /** One exercise of the workout: today's done sets and its last time (the most recent earlier workout's done sets, empty when none). */
    class Work(val exerciseId: String, val kind: ExerciseKind, val done: List<SetEntity>, val last: List<SetEntity>)

    data class Result(
        val score: Int,
        val doneSets: Int,
        val plannedSets: Int?,
        val up: Int,
        val compared: Int,
        val prs: Int,
        /** Recovery under 50 or a PAIN set: drops count as holding steady and fewer sets are needed. */
        val rough: Boolean = false,
        /** The person's first finished workout with done sets. */
        val firstEver: Boolean = false,
        /** The first workout after a break (the same rule as the Welcome back card, [Nudges.welcomeBack]). */
        val afterBreak: Boolean = false,
    ) {
        /** False on a first-ever or first-after-a-break workout: show the reasons without the number. */
        val showNumber: Boolean = !(firstEver || afterBreak)
    }

    /**
     * Best set (by [Records.score]), total reps or seconds, and heaviest weight, compared with last time. For WEIGHTED, the
     * total leaves out ramp-up sets lighter than 60 % of that workout's heaviest. A drop of 2 % or less in the best set, or a
     * heavier load with at least half the reps (a reset to fewer reps), counts as holding steady.
     */
    fun trend(kind: ExerciseKind, exerciseId: String, done: List<SetEntity>, last: List<SetEntity>): Trend {
        fun best(l: List<SetEntity>) = l.maxOf { Records.score(kind, it.reps, it.seconds, it.weightKg, exerciseId) }
        fun heaviest(l: List<SetEntity>) = l.maxOf { it.weightKg ?: 0f }
        fun total(l: List<SetEntity>): Int {
            val top = heaviest(l)
            val working = if (kind == ExerciseKind.WEIGHTED && top > 0f) l.filter { (it.weightKg ?: 0f) >= 0.6f * top } else l
            return working.sumOf { (if (kind == ExerciseKind.HOLD) it.seconds else it.reps) ?: 0 }
        }
        val bestNow = best(done)
        val bestLast = best(last)
        val totalNow = total(done)
        val totalLast = total(last)
        val heavyNow = heaviest(done)
        val heavyLast = heaviest(last)
        val reset = heavyNow > heavyLast && totalNow * 2 >= totalLast
        return when {
            bestNow > bestLast || (totalNow > totalLast && heavyNow >= heavyLast) -> Trend.UP
            bestNow < 0.98f * bestLast && totalNow <= totalLast && !reset -> Trend.DOWN
            else -> Trend.SAME
        }
    }

    /**
     * [plannedSets] is null with no plan day (or an empty one); warm-ups are already left out of it. [lowReadiness]: that
     * day's recovery was under 50, so every drop counts as holding steady; a PAIN set does that for its own exercise only.
     * Either way the workout needs only two thirds of the planned sets. Warm-up sets count for nothing but a PR.
     */
    fun compute(work: List<Work>, plannedSets: Int?, lowReadiness: Boolean, firstEver: Boolean = false, afterBreak: Boolean = false): Result {
        val working = work.map { w -> Work(w.exerciseId, w.kind, w.done.filterNot { isWarmUp(it.block) }, w.last.filterNot { isWarmUp(it.block) }) }
        val sets = working.flatMap { it.done }
        val pain = working.filter { w -> w.done.any { SetEffort.parse(it.effort) == SetEffort.PAIN } }.map { it.exerciseId }.toSet()
        val rough = lowReadiness || pain.isNotEmpty()
        val planned = plannedSets?.takeIf { it > 0 }
        // ceil(0.67 × planned) in whole numbers, so 0.67 × 100 can't round up to 68.
        val effective = planned?.let { if (rough) (67 * it + 99) / 100 else it }
        // Twice the value of each exercise (UP 2, SAME 1, DOWN 0), so the sums stay whole numbers.
        var halves = 0
        var up = 0
        var compared = 0
        working.filter { it.done.isNotEmpty() && it.last.isNotEmpty() }.forEach { w ->
            compared++
            when (trend(w.kind, w.exerciseId, w.done, w.last)) {
                Trend.UP -> { up++; halves += 2 }
                Trend.SAME -> halves += 1
                Trend.DOWN -> halves += if (lowReadiness || w.exerciseId in pain) 1 else 0
            }
        }
        val ratio = if (effective == null) 1.0 else sets.size / effective.toDouble()
        val completion = when {
            ratio >= 1.0 -> 3
            ratio >= 0.75 -> 2
            ratio >= 0.5 -> 1
            else -> 0
        }
        // min(3, floor(4 × sum / compared)) with sum = halves / 2; below 75 % of the sets, progress doesn't count.
        val progress = when {
            ratio < 0.75 -> 0
            compared == 0 -> 2
            else -> minOf(3, 2 * halves / compared)
        }
        val prs = work.sumOf { w -> w.done.count { it.isPr } }
        val total = 3 + completion + progress + if (prs > 0) 1 else 0
        return Result(total.coerceIn(4, 10), sets.size, planned, up, compared, prs, rough, firstEver, afterBreak)
    }

    /**
     * The score of every finished workout with done sets, in one pass over data already loaded. A workout's saved
     * [SessionEntity.plannedSets] wins; [plannedByDay] (each plan day's work sets as the plan is now) is the fallback for
     * older workouts. [lowReadinessDays] are the epoch days with recovery under 50; [dayTags] and [trainingDays] feed the
     * Welcome back rule behind [Result.afterBreak]. With [only] set, just that workout is scored (history still comes from all).
     */
    fun forSessions(
        sessions: List<SessionEntity>,
        doneSets: List<SetEntity>,
        kinds: Map<String, ExerciseKind>,
        plannedByDay: Map<Long, Int>,
        lowReadinessDays: Set<Long>,
        dayTags: Map<Long, DayTag> = emptyMap(),
        trainingDays: Set<Int> = emptySet(),
        only: Long? = null,
    ): Map<Long, Result> {
        val bySession = doneSets.groupBy { it.sessionId }
        val byId = sessions.associateBy { it.id }
        // Strictly earlier: equal (day, start) keys never count as before each other.
        fun earlier(o: SessionEntity, s: SessionEntity) = o.epochDay < s.epochDay || (o.epochDay == s.epochDay && o.startedAt < s.startedAt)
        // Per exercise, its workouts oldest first, so "last time" is the nearest one before.
        val history: Map<String, List<Pair<SessionEntity, List<SetEntity>>>> = doneSets.groupBy { it.exerciseId }.mapValues { (_, sets) ->
            sets.groupBy { it.sessionId }.mapNotNull { (id, l) -> byId[id]?.let { it to l } }
                .sortedWith(compareBy({ it.first.epochDay }, { it.first.startedAt }))
        }
        val firstWithSets = sessions.filter { bySession[it.id].orEmpty().isNotEmpty() }
            .minWithOrNull(compareBy({ it.epochDay }, { it.startedAt }))?.id
        val start = sessions.minOfOrNull { it.epochDay }
        fun scoreOf(s: SessionEntity, earlierDays: Set<Long>, lastOf: (String) -> List<SetEntity>): Result? {
            val mine = bySession[s.id].orEmpty()
            if (mine.isEmpty()) return null
            val work = mine.groupBy { it.exerciseId }.map { (id, done) -> Work(id, kinds[id] ?: ExerciseKind.REPS, done, lastOf(id)) }
            // The workout days known when this one started, as the Welcome back card saw them then.
            val afterBreak = Nudges.welcomeBack(s.epochDay, earlierDays, dayTags, trainingDays, start ?: s.epochDay)
            val planned = s.plannedSets ?: s.planDayId?.let { plannedByDay[it] }
            return compute(work, planned, s.epochDay in lowReadinessDays, firstEver = s.id == firstWithSets, afterBreak = afterBreak)
        }
        if (only != null) {
            val s = byId[only] ?: return emptyMap()
            val days = sessions.filter { earlier(it, s) }.map { it.epochDay }.toSet()
            val r = scoreOf(s, days) { id -> history[id].orEmpty().lastOrNull { (o, _) -> earlier(o, s) }?.second.orEmpty() }
            return if (r == null) emptyMap() else mapOf(s.id to r)
        }
        // One walk in time order: the earlier workout days build up, and each exercise keeps a pointer into its history.
        val ordered = sessions.sortedWith(compareBy({ it.epochDay }, { it.startedAt }, { it.id }))
        val pointer = HashMap<String, Int>()
        val earlierDays = HashSet<Long>()
        val out = HashMap<Long, Result>()
        var i = 0
        while (i < ordered.size) {
            var j = i
            while (j < ordered.size && ordered[j].epochDay == ordered[i].epochDay && ordered[j].startedAt == ordered[i].startedAt) j++
            for (k in i until j) {
                val s = ordered[k]
                val r = scoreOf(s, earlierDays) { id ->
                    val l = history[id].orEmpty()
                    var p = pointer[id] ?: 0
                    while (p < l.size && earlier(l[p].first, s)) p++
                    pointer[id] = p
                    if (p == 0) emptyList() else l[p - 1].second
                }
                if (r != null) out[s.id] = r
            }
            for (k in i until j) earlierDays.add(ordered[k].epochDay)
            i = j
        }
        return out
    }
}
