package com.forgequest.app.domain

import com.forgequest.app.data.db.ExerciseEntity
import com.forgequest.app.data.db.ReadinessDayEntity
import com.forgequest.app.data.db.SessionEntity
import com.forgequest.app.data.db.SetEntity
import kotlin.math.roundToInt

/** How far back the Progress screen looks. [days] null means everything. */
enum class TrendRange(val days: Int?) { M1(30), M3(91), M6(182), Y1(365), ALL(null);
    fun from(today: Long): Long = days?.let { today - it + 1 } ?: Long.MIN_VALUE
}

data class TrendPoint(val epochDay: Long, val value: Float, val isRecord: Boolean = false)

data class StalledLift(
    val exerciseId: String,
    val exerciseName: String,
    val sessionsCount: Int,
    val spanDays: Int,
    val bestScore: Float,
)

data class FatigueAndPlateauReport(
    val trainingLoadPct: Int,
    val stalledLifts: List<StalledLift>,
    val needsDeload: Boolean,
    val deloadReasons: List<String>,
)

/** Everything the Progress screen shows, computed from sessions, sets, weights and meals. Pure, so it's unit-tested. */
object Trends {

    /** Workouts per ISO week, oldest first, for the [weeks] weeks ending with the current one. */
    fun weeklyCounts(workoutDays: Collection<Long>, weeks: Int, today: Long): List<Int> {
        val thisWeek = Dates.weekStart(today)
        val starts = (weeks - 1 downTo 0).map { thisWeek - 7L * it }
        return starts.map { ws -> workoutDays.count { it in ws..ws + 6 } }
    }

    /**
     * Working sets (no warm-ups) in rolling 7-day windows of finished days (the last one ends yesterday), oldest first,
     * for the deload check. Ending yesterday keeps a Mon/Wed/Fri week whole on a Monday morning.
     */
    fun rollingWeeklySets(sets: List<SetEntity>, sessionDay: Map<Long, Long>, weeks: Int, today: Long): List<Int> {
        val days = sets.filterNot { it.block.equals("Warm-up", ignoreCase = true) }.mapNotNull { sessionDay[it.sessionId] }
        return (weeks - 1 downTo 0).map { back -> val end = today - 1 - 7L * back; days.count { it in end - 6..end } }
    }

    /** Sets done per week, same shape as [weeklyCounts]. */
    fun weeklySets(sets: List<SetEntity>, sessionDay: Map<Long, Long>, weeks: Int, today: Long): List<Int> {
        val thisWeek = Dates.weekStart(today)
        val starts = (weeks - 1 downTo 0).map { thisWeek - 7L * it }
        val days = sets.mapNotNull { sessionDay[it.sessionId] }
        return starts.map { ws -> days.count { it in ws..ws + 6 } }
    }

    /**
     * This week's sets as a percentage of the average of the previous full weeks that had any training.
     * 100 means "a normal week for you"; the first ever week reads 100 too, so nothing scolds a beginner.
     */
    fun trainingLoad(weeklySets: List<Int>): Int {
        if (weeklySets.isEmpty()) return 0
        val current = weeklySets.last()
        val previous = weeklySets.dropLast(1).filter { it > 0 }
        if (previous.isEmpty()) return if (current > 0) 100 else 0
        return (current * 100f / previous.average().toFloat()).roundToInt()
    }

    /**
     * Best set per training day for one exercise, oldest first, over the whole history. A point is a record when it
     * beats everything before it (the first day never is). Filter by range afterwards so records stay truthful.
     */
    fun bestSetSeries(sets: List<SetEntity>, kind: ExerciseKind, sessionDay: Map<Long, Long>): List<TrendPoint> {
        val byDay = sets.groupBy { sessionDay[it.sessionId] ?: return@groupBy -1L }.filterKeys { it >= 0 }
        var best = Float.NEGATIVE_INFINITY
        var first = true
        return byDay.keys.sorted().map { day ->
            val top = byDay.getValue(day).maxOf { Records.score(kind, it.reps, it.seconds, it.weightKg, it.exerciseId) }
            val record = !first && top > best
            if (top > best) best = top
            first = false
            TrendPoint(day, top, record)
        }
    }

    /** Trailing [window]-day mean of a daily series (gaps allowed); each point averages the entries within the window. */
    fun movingAverage(points: List<TrendPoint>, window: Int = 7): List<TrendPoint> {
        val sorted = points.sortedBy { it.epochDay }
        return sorted.map { p ->
            val inWindow = sorted.filter { it.epochDay in (p.epochDay - window + 1)..p.epochDay }
            TrendPoint(p.epochDay, inWindow.map { it.value }.average().toFloat())
        }
    }

    enum class DayCell { FUTURE, REST, DONE, RECORD }

    /** 7 rows (Mon..Sun) × [weeks] columns (oldest first) for the consistency heatmap. */
    fun consistencyGrid(workoutDays: Set<Long>, recordDays: Set<Long>, weeks: Int, today: Long): List<List<DayCell>> {
        val thisWeek = Dates.weekStart(today)
        return (0 until 7).map { row ->
            (weeks - 1 downTo 0).map { back ->
                val day = thisWeek - 7L * back + row
                when {
                    day > today -> DayCell.FUTURE
                    day in recordDays -> DayCell.RECORD
                    day in workoutDays -> DayCell.DONE
                    else -> DayCell.REST
                }
            }
        }
    }

    /** First and last value of a series inside the range, or null when the range holds fewer than two points. */
    fun thenNow(series: List<TrendPoint>, from: Long): Pair<Float, Float>? {
        val inRange = series.filter { it.epochDay >= from }
        if (inRange.size < 2) return null
        return inRange.first().value to inRange.last().value
    }

    /** Days per week on which protein reached [goal], oldest week first. Only logged days count. */
    fun proteinDaysPerWeek(nutrition: List<Pair<Long, Float>>, goal: Int, weeks: Int, today: Long): List<Int> {
        val thisWeek = Dates.weekStart(today)
        val hits = nutrition.filter { it.second >= goal }.map { it.first }
        return (weeks - 1 downTo 0).map { back -> val ws = thisWeek - 7L * back; hits.count { it in ws..ws + 6 } }
    }

    data class Lifetime(val workouts: Int, val reps: Int, val minutes: Int, val records: Int, val kgLifted: Float)

    fun lifetime(sessions: List<SessionEntity>, sets: List<SetEntity>): Lifetime = Lifetime(
        workouts = sessions.size,
        reps = sets.sumOf { it.reps ?: 0 },
        minutes = sessions.sumOf { s -> s.finishedAt?.let { ((it - s.startedAt) / 60_000).toInt().coerceIn(1, 300) } ?: 0 },
        records = sets.count { it.isPr },
        kgLifted = sets.sumOf { ((it.weightKg ?: 0f) * (it.reps ?: 0)).toDouble() }.toFloat(),
    )

    /** Exercise ids by how often they appear in done sets, most first. */
    fun topExercises(sets: List<SetEntity>, n: Int): List<String> =
        sets.groupingBy { it.exerciseId }.eachCount().entries.sortedByDescending { it.value }.take(n).map { it.key }

    /** Percent change between two values, rounded, or null when the base is zero. */
    fun percentChange(then: Float, now: Float): Int? = if (then == 0f) null else ((now - then) / then * 100f).roundToInt()

    /**
     * Evaluates recent training history for:
     * 1. Stagnant lifts: exercises trained >= 3 times across >= 14 days in the last 35 days where the best score
     *    of the most recent 2 sessions failed to beat the earlier sessions in that window.
     * 2. Systemic deload triggers: acute weekly volume > 135 % of 4-week chronic average (with >= 18 sets),
     *    >= 3 of the last 4 readiness days below 45 % recovery, or >= 2 simultaneous stalled main lifts.
     */
    fun analyzeFatigueAndPlateaus(
        sessions: List<SessionEntity>,
        doneSets: List<SetEntity>,
        exercises: Map<String, ExerciseEntity>,
        readinessDays: List<ReadinessDayEntity> = emptyList(),
        today: Long,
    ): FatigueAndPlateauReport {
        val sessionDay = sessions.associate { it.id to it.epochDay }
        // Rolling 7-day windows, not calendar weeks: on a Monday the calendar week has 0 sets and the check went blind.
        val weekly = rollingWeeklySets(doneSets, sessionDay, 5, today)
        val loadPct = trainingLoad(weekly)
        val currentWeekSets = weekly.lastOrNull() ?: 0

        val windowStart = today - 35
        val recentSets = doneSets.filter { s ->
            val d = sessionDay[s.sessionId] ?: return@filter false
            d in windowStart..today && !s.block.equals("Warm-up", ignoreCase = true)
        }

        val stalled = mutableListOf<StalledLift>()
        for ((exerciseId, exSets) in recentSets.groupBy { it.exerciseId }) {
            val ex = exercises[exerciseId] ?: continue
            if (ex.id == "jumping_jacks") continue
            // A plank or dead hang held at the same time is maintenance, not a stalled lift.
            if (ex.kind == ExerciseKind.HOLD && exSets.none { (it.weightKg ?: 0f) > 0f }) continue
            val series = bestSetSeries(exSets, ex.kind, sessionDay)
            if (series.size < 3) continue
            val span = (series.last().epochDay - series.first().epochDay).toInt()
            if (span < 12) continue
            val earlierBest = series.dropLast(2).maxOf { it.value }
            val recentBest = series.takeLast(2).maxOf { it.value }
            if (recentBest <= earlierBest + 0.01f) {
                stalled += StalledLift(
                    exerciseId = exerciseId,
                    exerciseName = ex.name,
                    sessionsCount = series.size,
                    spanDays = span,
                    bestScore = maxOf(earlierBest, recentBest),
                )
            }
        }

        val reasons = mutableListOf<String>()
        if (loadPct >= 135 && currentWeekSets >= 18) {
            reasons += "acute weekly volume is $loadPct% of your 4-week average ($currentWeekSets sets)"
        }
        val recentReadiness = readinessDays.filter { it.epochDay in (today - 4)..today }
        val lowRecoveryDays = recentReadiness.count { it.recovery in 1..44 }
        if (lowRecoveryDays >= 3) {
            reasons += "recovery has been below 45% on $lowRecoveryDays of the last ${recentReadiness.size} days"
        }
        if (stalled.size >= 2 && loadPct >= 95) {
            reasons += "${stalled.size} lifts have plateaued for 2+ weeks (${stalled.take(3).joinToString(", ") { it.exerciseName }})"
        }

        return FatigueAndPlateauReport(
            trainingLoadPct = loadPct,
            stalledLifts = stalled.sortedByDescending { it.sessionsCount },
            needsDeload = reasons.isNotEmpty(),
            deloadReasons = reasons,
        )
    }
}
