package com.forgequest.app.domain

import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.max
import kotlin.math.min

object Dates {
    fun today(): Long = LocalDate.now().toEpochDay()

    /** Monday of the week containing [epochDay]. */
    fun weekStart(epochDay: Long): Long = epochDay - (LocalDate.ofEpochDay(epochDay).dayOfWeek.value - 1)

    /** 1 = Monday .. 7 = Sunday */
    fun dayOfWeek(epochDay: Long): Int = LocalDate.ofEpochDay(epochDay).dayOfWeek.value

    fun todayDayOfWeek(): Int = LocalDate.now().dayOfWeek.value

    val shortDayLetters = listOf("M", "T", "W", "T", "F", "S", "S")

    fun dayName(dayOfWeek: Int): String =
        DayOfWeek.of(dayOfWeek).getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault())
}

// ---------------------------------------------------------------- Weekly streak

enum class DayState { DONE, REST, MISSED, TODAY_PLANNED, UPCOMING, OFF }

data class WeeklyStreak(
    /** Consecutive weeks (ending this week if hit, otherwise last week) where the target was reached. */
    val weeks: Int,
    val best: Int,
    val doneThisWeek: Int,
    val targetThisWeek: Int,
    /** Monday..Sunday */
    val days: List<DayState>,
) {
    val weekProgress: Float get() = if (targetThisWeek <= 0) (if (doneThisWeek > 0) 1f else 0f) else min(1f, doneThisWeek.toFloat() / targetThisWeek)

    /** The week you joined, when no planned workouts were left in it. */
    val freeWeek: Boolean get() = targetThisWeek == 0 && doneThisWeek == 0
    val hitThisWeek: Boolean get() = if (targetThisWeek == 0) doneThisWeek > 0 else doneThisWeek >= targetThisWeek
}

object StreakCalculator {
    /**
     * A week counts when the number of workout days reaches the target. Rest days never hurt.
     * The first week is prorated from [startEpochDay] so joining on a Thursday is fair.
     * The current week never breaks the streak while it's still in progress.
     */
    fun compute(
        workoutDays: Set<Long>,
        plannedTrainingDays: Set<Int>,
        targetOverride: Int,
        startEpochDay: Long,
        today: Long,
    ): WeeklyStreak {
        val fullTarget = if (targetOverride > 0) targetOverride else max(1, plannedTrainingDays.size)
        val thisWeek = Dates.weekStart(today)
        val firstWeek = Dates.weekStart(startEpochDay)

        fun targetFor(weekStart: Long): Int {
            if (weekStart != firstWeek) return fullTarget
            val remainingPlanned = (startEpochDay..weekStart + 6).count { Dates.dayOfWeek(it) in plannedTrainingDays }
            // Joined after the last planned day of the week: that week is free, it neither counts nor breaks.
            return min(fullTarget, remainingPlanned)
        }

        fun doneIn(weekStart: Long) = workoutDays.count { it in weekStart..weekStart + 6 }

        val doneThisWeek = doneIn(thisWeek)
        val targetThisWeek = targetFor(thisWeek)

        // A workout in a free week still counts: effort should never feel wasted.
        fun hit(weekStart: Long) = targetFor(weekStart).let { t -> if (t == 0) doneIn(weekStart) > 0 else doneIn(weekStart) >= t }
        fun free(weekStart: Long) = targetFor(weekStart) == 0 && doneIn(weekStart) == 0

        var streak = 0
        var week = thisWeek - 7
        while (week >= firstWeek && (hit(week) || free(week))) {
            if (hit(week)) streak++
            week -= 7
        }
        if (hit(thisWeek)) streak++

        var best = 0
        var run = 0
        var w = firstWeek
        while (w <= thisWeek) {
            when {
                hit(w) -> { run++; best = max(best, run) }
                free(w) || w == thisWeek -> Unit
                else -> run = 0
            }
            w += 7
        }

        val days = (0..6).map { offset ->
            val day = thisWeek + offset
            val dow = offset + 1
            val planned = dow in plannedTrainingDays
            when {
                day in workoutDays -> DayState.DONE
                day == today && planned -> DayState.TODAY_PLANNED
                day > today && planned -> DayState.UPCOMING
                day < startEpochDay -> DayState.OFF
                day < today && planned -> DayState.MISSED
                else -> DayState.REST
            }
        }
        return WeeklyStreak(streak, max(best, streak), doneThisWeek, targetThisWeek, days)
    }
}

// ---------------------------------------------------------------- XP & rank

data class RankInfo(val letter: String, val title: String)

data class LevelInfo(
    val totalXp: Int,
    val level: Int,
    val xpIntoLevel: Int,
    val xpForNextLevel: Int,
    val rank: RankInfo,
) {
    val progress: Float get() = if (xpForNextLevel == 0) 0f else xpIntoLevel.toFloat() / xpForNextLevel
}

object Xp {
    const val PER_WORKOUT = 50
    const val PER_SET = 2
    const val PER_PR = 25
    const val PER_BADGE = 100

    /** XP needed to go from [level] to [level] + 1. */
    fun stepFor(level: Int) = 100 + 50 * (level - 1)

    fun levelFor(totalXp: Int): LevelInfo {
        var level = 1
        var remaining = totalXp
        while (remaining >= stepFor(level)) {
            remaining -= stepFor(level)
            level++
        }
        return LevelInfo(totalXp, level, remaining, stepFor(level), rankFor(level))
    }

    fun rankFor(level: Int): RankInfo = when {
        level >= 45 -> RankInfo("S", "Legend")
        level >= 30 -> RankInfo("A", "Elite")
        level >= 20 -> RankInfo("B", "Strong")
        level >= 10 -> RankInfo("C", "Committed")
        level >= 5 -> RankInfo("D", "Regular")
        else -> RankInfo("E", "Starter")
    }

    fun total(workouts: Int, doneSets: Int, prs: Int, badges: Int) =
        workouts * PER_WORKOUT + doneSets * PER_SET + prs * PER_PR + badges * PER_BADGE
}

// ---------------------------------------------------------------- Recovery

data class MuscleRecovery(
    /** 0 = fully recovered, 1 = just trained hard. */
    val fatigue: Float,
    val hoursUntilReady: Int,
)

object Recovery {
    const val WINDOW_HOURS = 48f

    /**
     * [hits] = (muscle, trainedAtMillis, weight) where weight is 1 for a primary muscle, 0.5 for secondary.
     * Fatigue fades linearly over 48 h; the freshest, hardest hit wins.
     */
    fun compute(hits: List<Triple<Muscle, Long, Float>>, now: Long): Map<Muscle, MuscleRecovery> {
        val result = mutableMapOf<Muscle, MuscleRecovery>()
        for ((muscle, at, weight) in hits) {
            val hours = (now - at) / 3_600_000f
            if (hours < 0 || hours >= WINDOW_HOURS) continue
            val fatigue = weight * (1f - hours / WINDOW_HOURS)
            val ready = ((WINDOW_HOURS - hours) * weight).toInt().coerceAtLeast(1)
            val prev = result[muscle]
            if (prev == null || fatigue > prev.fatigue) result[muscle] = MuscleRecovery(fatigue, ready)
        }
        return result
    }
}

// ---------------------------------------------------------------- Personal records

object Records {
    /** Comparable score for one set. Weighted sets use estimated 1RM so 5×100 kg beats 8×80 kg. */
    fun score(kind: ExerciseKind, reps: Int?, seconds: Int?, weightKg: Float?): Float = when (kind) {
        ExerciseKind.REPS -> (reps ?: 0).toFloat()
        ExerciseKind.HOLD -> (seconds ?: 0).toFloat()
        ExerciseKind.WEIGHTED -> {
            val w = weightKg ?: 0f
            val r = reps ?: 0
            if (w <= 0f) r / 100f else w * (1f + r / 30f)
        }
    }

    /**
     * Which sets count as personal records when the whole history of one exercise is replayed in order.
     * [sessions] is oldest first, each holding that session's done sets. The first session has nothing
     * to beat, so it never produces a record; after that, the best set of a session is a record when it
     * beats every set before it. Used when a past session is edited or deleted.
     */
    fun <T> personalRecords(
        kind: ExerciseKind,
        sessions: List<List<T>>,
        reps: (T) -> Int?,
        seconds: (T) -> Int?,
        weightKg: (T) -> Float?,
    ): Set<T> {
        val result = mutableSetOf<T>()
        var best: Float? = null
        for (sets in sessions) {
            if (sets.isEmpty()) continue
            val top = sets.maxBy { score(kind, reps(it), seconds(it), weightKg(it)) }
            val topScore = score(kind, reps(top), seconds(top), weightKg(top))
            val previous = best
            if (previous != null && topScore > previous) result += top
            best = if (previous == null) topScore else max(previous, topScore)
        }
        return result
    }
}
