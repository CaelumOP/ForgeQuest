package com.forgequest.app.domain

import com.forgequest.app.data.db.ExerciseEntity
import com.forgequest.app.data.db.SetEntity
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

object Dates {
    fun today(): Long = LocalDate.now().toEpochDay()

    /** Monday of the week containing [epochDay]. */
    fun weekStart(epochDay: Long): Long = epochDay - (LocalDate.ofEpochDay(epochDay).dayOfWeek.value - 1)

    /** 1 = Monday .. 7 = Sunday */
    fun dayOfWeek(epochDay: Long): Int = LocalDate.ofEpochDay(epochDay).dayOfWeek.value

    fun todayDayOfWeek(): Int = LocalDate.now().dayOfWeek.value

    /**
     * Short day name for day strips: "Mon" in English; in Arabic the name without «ال» ("اثنين", "ثلاثاء"), which reads
     * naturally and fits a seventh of the width. Java's Arabic short name is the full one (cut to "الث…"), and its narrow
     * one is a single letter where ث could be Tuesday or Wednesday.
     */
    fun dayShort(dayOfWeek: Int): String =
        if (java.util.Locale.getDefault().language == "ar") AR_DAYS[dayOfWeek - 1] else dayName(dayOfWeek).take(3)

    private val AR_DAYS = listOf("اثنين", "ثلاثاء", "أربعاء", "خميس", "جمعة", "سبت", "أحد")

    fun dayName(dayOfWeek: Int): String =
        DayOfWeek.of(dayOfWeek).getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault())

    /** "Mon" in English; the full name in Arabic, where cutting it leaves only «ال». */
    fun dayAbbrev(dayOfWeek: Int): String = dayName(dayOfWeek).let { if (java.util.Locale.getDefault().language == "ar") it else it.take(3) }
}

// ---------------------------------------------------------------- Weekly streak

enum class DayState { DONE, REST, MISSED, TODAY_PLANNED, UPCOMING, OFF, EXCUSED }

/** Why a day was excused. Sick and Injured also offer an easier first session back. */
enum class DayTag { SICK, INJURED, TRAVEL, REST;
    companion object {
        fun parse(raw: String?): DayTag? = entries.firstOrNull { it.name == raw }
    }
}

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
    /** Trained in that joining week anyway: it counts, but "Week complete" would contradict the weekly target. */
    val headStart: Boolean get() = targetThisWeek == 0 && doneThisWeek > 0
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
        excusedDays: Set<Long> = emptySet(),
        /** Targets saved for finished weeks (Monday epoch day to days); a plan change never rewrites them. */
        lockedTargets: Map<Long, Int> = emptyMap(),
    ): WeeklyStreak {
        val fullTarget = fullTarget(plannedTrainingDays, targetOverride)
        val thisWeek = Dates.weekStart(today)
        val firstWeek = Dates.weekStart(startEpochDay)

        fun doneIn(weekStart: Long) = workoutDays.count { it in weekStart..weekStart + 6 }

        fun targetFor(weekStart: Long): Int {
            val weekTarget = lockedTargets[weekStart] ?: fullTarget
            val base = if (weekStart != firstWeek) {
                weekTarget
            } else {
                val remainingPlanned = (startEpochDay..weekStart + 6).count { Dates.dayOfWeek(it) in plannedTrainingDays }
                // Joined after the last planned day of the week: that week is free, it neither counts nor breaks.
                min(weekTarget, remainingPlanned)
            }
            // Each excused planned day (sick, travel...) lowers the week's target; a fully excused week is free.
            val excused = (weekStart..weekStart + 6).count { it in excusedDays && it !in workoutDays && Dates.dayOfWeek(it) in plannedTrainingDays }
            return (base - excused).coerceAtLeast(0)
        }

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
                day in excusedDays && day <= today -> DayState.EXCUSED
                day == today && planned -> DayState.TODAY_PLANNED
                day > today && planned -> DayState.UPCOMING
                day < startEpochDay -> DayState.OFF
                day < today && planned -> DayState.MISSED
                else -> DayState.REST
            }
        }
        return WeeklyStreak(streak, max(best, streak), doneThisWeek, targetThisWeek, days)
    }

    fun fullTarget(plannedTrainingDays: Set<Int>, targetOverride: Int): Int =
        if (targetOverride > 0) targetOverride else max(1, plannedTrainingDays.size)

    /** Finished weeks since [startEpochDay] with no saved target yet, each given today's [fullTarget] to save. */
    fun weeksToLock(locked: Map<Long, Int>, fullTarget: Int, startEpochDay: Long, today: Long): Map<Long, Int> =
        generateSequence(Dates.weekStart(startEpochDay)) { it + 7 }
            .takeWhile { it < Dates.weekStart(today) }
            .filter { it !in locked }
            .associateWith { fullTarget }
}

// ---------------------------------------------------------------- Reminders & comebacks

object Nudges {
    /** After this many planned days missed in a row (not excused), reminders pause until a workout or a tag. */
    const val MAX_MISSES = 3

    /**
     * Planned days missed back to back, counting back from yesterday; a workout or an excused day ends the run.
     * Looks back 4 weeks, so a 1-day-a-week plan can still reach [MAX_MISSES] (14 days held only 2).
     */
    fun missedInARow(today: Long, plannedTrainingDays: Set<Int>, workoutDays: Set<Long>, excusedDays: Set<Long>, startEpochDay: Long): Int {
        var count = 0
        var day = today - 1
        while (day >= startEpochDay && day >= today - 28) {
            if (day in workoutDays || day in excusedDays) break
            if (Dates.dayOfWeek(day) in plannedTrainingDays) count++
            day--
        }
        return count
    }

    fun shouldRemind(plannedToday: Boolean, doneToday: Boolean, excusedToday: Boolean, missedInARow: Int): Boolean =
        plannedToday && !doneToday && !excusedToday && missedInARow < MAX_MISSES

    /**
     * True for the first workout back after a sick or injured day since the last workout, or after a reminder
     * back-off: the session then offers a one-tap "Ease in" (one set fewer, never below 2).
     */
    fun welcomeBack(
        today: Long,
        workoutDays: Set<Long>,
        tags: Map<Long, DayTag>,
        plannedTrainingDays: Set<Int>,
        startEpochDay: Long,
    ): Boolean {
        val lastWorkout = workoutDays.filter { it < today }.maxOrNull() ?: return false
        if (workoutDays.contains(today)) return false
        val illSince = (lastWorkout + 1 until today).any { tags[it] == DayTag.SICK || tags[it] == DayTag.INJURED }
        return illSince || missedInARow(today, plannedTrainingDays, workoutDays, tags.keys, startEpochDay) >= MAX_MISSES
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
    /** 0 = fully recovered, 1 = just trained hard (4+ full working sets). */
    val fatigue: Float,
    val hoursUntilReady: Int,
)

object Recovery {
    const val WINDOW_HOURS = 48f
    /** Number of full working sets on a primary muscle that constitutes a 100 % (48-hour) session stimulus. */
    private const val FULL_SESSION_EFFECTIVE_SETS = 4f
    private val HIGH_SKILL_LOW_REP_IDS = setOf(
        "muscle_up", "pistol_squat", "handstand_push_up", "tuck_planche",
        "pseudo_planche_push_up", "tuck_front_lever", "front_lever_row", "back_lever_tuck", "nordic_curl",
    )

    /**
     * Converts a session's completed sets into realistic per-muscle stimulus weights in (0, 1].
     * - 0 reps / 0 seconds -> 0 stimulus (ignored).
     * - 1 bodyweight rep (e.g. 1 push-up) -> ~0.10 of a working set -> 0.025 stimulus (~1.2 h recovery).
     * - 1 full working set (10 reps or 30 s hold) -> 0.25 primary stimulus (12 h recovery, 25 % fatigue).
     * - 2 full working sets -> 0.50 primary stimulus (24 h recovery, 50 % fatigue).
     * - 4+ full working sets -> 1.00 primary stimulus (48 h recovery, 100 % fatigue).
     * - Warm-up block sets are weighted at 25 % of a main set so jumping jacks never mark calves red for 48 hours.
     */
    fun sessionHits(
        sets: List<SetEntity>,
        exercises: Map<String, ExerciseEntity>,
        sessionFinishedAt: Map<Long, Long>,
    ): List<Triple<Muscle, Long, Float>> {
        val result = mutableListOf<Triple<Muscle, Long, Float>>()
        for ((sessionId, sessionSets) in sets.groupBy { it.sessionId }) {
            val finishedAt = sessionFinishedAt[sessionId] ?: continue
            val stimulusByMuscle = mutableMapOf<Muscle, Float>()
            for (set in sessionSets) {
                if (!set.done) continue
                val ex = exercises[set.exerciseId] ?: continue
                val eff = effectiveSetEffort(set, ex)
                if (eff <= 0f) continue
                val primaryDelta = eff / FULL_SESSION_EFFECTIVE_SETS
                val secondaryDelta = primaryDelta * 0.5f
                for (m in ex.primary) {
                    stimulusByMuscle[m] = (stimulusByMuscle[m] ?: 0f) + primaryDelta
                }
                for (m in ex.secondary) {
                    stimulusByMuscle[m] = (stimulusByMuscle[m] ?: 0f) + secondaryDelta
                }
            }
            for ((muscle, totalStimulus) in stimulusByMuscle) {
                val clamped = totalStimulus.coerceIn(0f, 1f)
                if (clamped >= 0.02f) {
                    result += Triple(muscle, finishedAt, clamped)
                }
            }
        }
        return result
    }

    /**
     * Effective working-set equivalent for a single logged set:
     * - Standard full working set = 1.0f.
     * - 1 rep on a bodyweight exercise = 0.10f (so 1 rep is 1/10th of a set, not a full workout).
     */
    internal fun effectiveSetEffort(set: SetEntity, ex: ExerciseEntity): Float {
        val rawEffort = when (ex.kind) {
            ExerciseKind.HOLD -> {
                val sec = set.seconds ?: 0
                if (sec <= 0) return 0f
                val refSec = if (ex.id in HIGH_SKILL_LOW_REP_IDS) 12f else 30f
                (sec / refSec).coerceIn(0.08f, 1.25f)
            }
            ExerciseKind.WEIGHTED -> {
                val r = set.reps ?: 0
                if (r <= 0) return 0f
                val w = set.weightKg ?: 0f
                // With external load, 6 reps already count as a full working set (10 without); a heavy triple counts half.
                val refReps = if (w > 0f) 6f else 10f
                (r / refReps).coerceIn(0.15f, 1.25f)
            }
            ExerciseKind.REPS -> {
                val r = set.reps ?: 0
                if (r <= 0) return 0f
                val hasExtraWeight = (set.weightKg ?: 0f) > 0f
                val refReps = when {
                    ex.id in HIGH_SKILL_LOW_REP_IDS -> 4f
                    hasExtraWeight -> 6f
                    else -> 10f
                }
                (r / refReps).coerceIn(0.08f, 1.25f)
            }
        }
        val blockFactor = if (set.block.equals("Warm-up", ignoreCase = true) || ex.id == "jumping_jacks") 0.25f else 1.0f
        val rpeFactor = when (SetEffort.of(set.effort, set.note)) {
            SetEffort.HARD, SetEffort.PAIN -> 1.15f
            SetEffort.EASY -> 0.80f
            SetEffort.OK -> 1.0f
        }
        return (rawEffort * blockFactor * rpeFactor).coerceIn(0f, 1.35f)
    }

    /**
     * [hits] = (muscle, trainedAtMillis, weight) where weight in (0, 1] is the session stimulus
     * (1.0 = full primary session needing 48 h, 0.5 = secondary/moderate session needing 24 h,
     * 0.25 = single working set needing 12 h).
     * Fatigue clears at a constant physiological rate of 1 / 48 per hour. Sessions add up: what's left of an earlier
     * session plus the new one (capped at 1), so training a muscle twice in a day counts both (audit 2026-10-24).
     */
    fun compute(hits: List<Triple<Muscle, Long, Float>>, now: Long): Map<Muscle, MuscleRecovery> {
        val result = mutableMapOf<Muscle, MuscleRecovery>()
        for ((muscle, muscleHits) in hits.filter { it.third > 0f && it.second <= now }.groupBy { it.first }) {
            var fatigue = 0f
            var lastAt = Long.MIN_VALUE
            for ((_, at, weight) in muscleHits.sortedBy { it.second }) {
                if (lastAt != Long.MIN_VALUE) fatigue = (fatigue - (at - lastAt) / 3_600_000f / WINDOW_HOURS).coerceAtLeast(0f)
                fatigue = (fatigue + weight.coerceIn(0f, 1f)).coerceAtMost(1f)
                lastAt = at
            }
            fatigue -= (now - lastAt) / 3_600_000f / WINDOW_HOURS
            if (fatigue <= 0f) continue
            // + 0.001 so float noise never turns 36 h into 35.
            result[muscle] = MuscleRecovery(fatigue, (fatigue * WINDOW_HOURS + 0.001f).toInt().coerceAtLeast(1))
        }
        return result
    }
}

// ---------------------------------------------------------------- Personal records

object Records {
    /**
     * Comparable score for one set:
     * - WEIGHTED sets use an Epley 1RM model with linear scaling up to 12 reps and logarithmic diminishing
     *   returns above 12 reps so 5×100 kg beats 8×80 kg and 35×20 kg never falsely beats heavy strength sets.
     * - REPS and HOLD sets also credit added external weight (e.g. weighted vest or dip belt on calisthenics)
     *   so progressing from bodyweight to +10 kg is recognized as a personal record.
     * - Weighted pull-ups, chin-ups, dips and push-ups move the body too, so their Epley score uses body + added
     *   load (then takes the body back off): +10 kg × 10 beats +15 kg × 1. A fixed 75 kg reference body keeps one
     *   person's sets in the right order without needing their weight (audit 2026-10-24).
     */
    fun score(kind: ExerciseKind, reps: Int?, seconds: Int?, weightKg: Float?, exerciseId: String = ""): Float {
        val extraKg = (weightKg ?: 0f).coerceAtLeast(0f)
        return when (kind) {
            ExerciseKind.REPS -> {
                val r = (reps ?: 0).coerceAtLeast(0)
                if (r == 0) 0f else r.toFloat() + extraKg * 0.35f
            }
            ExerciseKind.HOLD -> {
                val s = (seconds ?: 0).coerceAtLeast(0)
                if (s == 0) 0f else s.toFloat() + extraKg * 1.0f
            }
            ExerciseKind.WEIGHTED -> {
                val r = (reps ?: 0).coerceAtLeast(0)
                if (r == 0) return 0f
                val body = REFERENCE_BODY_KG * (BODY_SHARE[exerciseId] ?: 0f)
                if (extraKg <= 0f && body <= 0f) {
                    r / 100f
                } else {
                    val effectiveReps = if (r <= 12) r.toFloat() else 12f + 3f * ln(1f + (r - 12).toFloat())
                    (body + extraKg) * (1f + effectiveReps / 30f) - body
                }
            }
        }
    }

    const val REFERENCE_BODY_KG = 75f

    /** Share of body weight each rep moves: pull-ups, chin-ups and dips the whole body, push-ups about 65 %. */
    private val BODY_SHARE = mapOf("weighted_pull_up" to 1f, "weighted_chin_up" to 1f, "weighted_dip" to 1f, "weighted_push_up" to 0.65f)

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
        exerciseId: String = "",
    ): Set<T> {
        val result = mutableSetOf<T>()
        var best: Float? = null
        for (sets in sessions) {
            if (sets.isEmpty()) continue
            val top = sets.maxBy { score(kind, reps(it), seconds(it), weightKg(it), exerciseId) }
            val topScore = score(kind, reps(top), seconds(top), weightKg(top), exerciseId)
            val previous = best
            if (previous != null && topScore > previous) result += top
            best = if (previous == null) topScore else max(previous, topScore)
        }
        return result
    }
}
