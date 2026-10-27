package com.forgequest.app.domain

import com.forgequest.app.data.db.SessionEntity
import com.forgequest.app.data.db.SetEntity

/**
 * The workout score (4..10), calculated from the workout's own numbers so it shows at once, with or without an AI key:
 * 3 for showing up, up to 3 for finishing the plan, up to 3 for beating last time, and 1 for a personal record.
 */
object WorkoutScore {
    enum class Trend { UP, SAME, DOWN }

    /** One exercise of the workout: today's done sets and its last time (the most recent earlier workout's done sets, empty when none). */
    class Work(val exerciseId: String, val kind: ExerciseKind, val done: List<SetEntity>, val last: List<SetEntity>)

    data class Result(val score: Int, val doneSets: Int, val plannedSets: Int?, val up: Int, val compared: Int, val prs: Int)

    /** Best set (by [Records.score]), total reps or seconds, and heaviest weight, compared with last time. */
    fun trend(kind: ExerciseKind, exerciseId: String, done: List<SetEntity>, last: List<SetEntity>): Trend {
        fun best(l: List<SetEntity>) = l.maxOf { Records.score(kind, it.reps, it.seconds, it.weightKg, exerciseId) }
        fun total(l: List<SetEntity>) = l.sumOf { (if (kind == ExerciseKind.HOLD) it.seconds else it.reps) ?: 0 }
        fun heaviest(l: List<SetEntity>) = l.maxOf { it.weightKg ?: 0f }
        val bestNow = best(done)
        val bestLast = best(last)
        val totalNow = total(done)
        val totalLast = total(last)
        return when {
            bestNow > bestLast || (totalNow > totalLast && heaviest(done) >= heaviest(last)) -> Trend.UP
            bestNow < bestLast && totalNow <= totalLast -> Trend.DOWN
            else -> Trend.SAME
        }
    }

    /**
     * [plannedSets] is null with no plan day (or an empty one). [lowReadiness]: that day's recovery said ease off or recover;
     * a PAIN set counts the same, and either way holding steady is full marks and a drop isn't penalised.
     */
    fun compute(work: List<Work>, plannedSets: Int?, lowReadiness: Boolean): Result {
        val sets = work.flatMap { it.done }
        val rough = lowReadiness || sets.any { SetEffort.parse(it.effort) == SetEffort.PAIN }
        val planned = plannedSets?.takeIf { it > 0 }
        // Twice the value of each exercise (UP 2, SAME 1, DOWN 0), so the sums stay whole numbers.
        var halves = 0
        var up = 0
        var compared = 0
        work.filter { it.done.isNotEmpty() && it.last.isNotEmpty() }.forEach { w ->
            compared++
            when (trend(w.kind, w.exerciseId, w.done, w.last)) {
                Trend.UP -> { up++; halves += 2 }
                Trend.SAME -> halves += if (rough) 2 else 1
                Trend.DOWN -> halves += if (rough) 2 else 0
            }
        }
        val ratio = if (planned == null) 1.0 else sets.size / planned.toDouble()
        val completion = when {
            ratio >= 1.0 -> 3
            ratio >= 0.75 -> 2
            ratio >= 0.5 -> 1
            else -> 0
        }
        val progress = if (compared == 0) 1 else 3 * halves / (2 * compared)
        val prs = sets.count { it.isPr }
        val total = 3 + completion + progress + if (prs > 0) 1 else 0
        return Result(total.coerceIn(4, 10), sets.size, planned, up, compared, prs)
    }

    /**
     * The score of every finished workout with done sets, in one pass over data already loaded. [plannedByDay] is the sum of
     * `sets` over each plan day's items; [lowReadinessDays] the epoch days whose recovery said ease off or recover.
     */
    fun forSessions(
        sessions: List<SessionEntity>,
        doneSets: List<SetEntity>,
        kinds: Map<String, ExerciseKind>,
        plannedByDay: Map<Long, Int>,
        lowReadinessDays: Set<Long>,
    ): Map<Long, Result> {
        val bySession = doneSets.groupBy { it.sessionId }
        val byId = sessions.associateBy { it.id }
        // Per exercise, its workouts oldest first, so "last time" is the nearest one before.
        val history: Map<String, List<Pair<SessionEntity, List<SetEntity>>>> = doneSets.groupBy { it.exerciseId }.mapValues { (_, sets) ->
            sets.groupBy { it.sessionId }.mapNotNull { (id, l) -> byId[id]?.let { it to l } }
                .sortedWith(compareBy({ it.first.epochDay }, { it.first.startedAt }))
        }
        return sessions.mapNotNull { s ->
            val mine = bySession[s.id].orEmpty()
            if (mine.isEmpty()) return@mapNotNull null
            val work = mine.groupBy { it.exerciseId }.map { (id, done) ->
                val last = history[id].orEmpty().lastOrNull { (o, _) -> o.id != s.id && (o.epochDay < s.epochDay || (o.epochDay == s.epochDay && o.startedAt < s.startedAt)) }
                Work(id, kinds[id] ?: ExerciseKind.REPS, done, last?.second.orEmpty())
            }
            s.id to compute(work, s.planDayId?.let { plannedByDay[it] }, s.epochDay in lowReadinessDays)
        }.toMap()
    }
}
