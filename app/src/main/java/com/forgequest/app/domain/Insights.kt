package com.forgequest.app.domain

import com.forgequest.app.data.db.ExerciseEntity
import com.forgequest.app.data.db.PlanItemEntity
import com.forgequest.app.data.db.SessionEntity
import com.forgequest.app.data.db.SetEntity
import com.forgequest.app.data.seed.ExerciseCatalog
import com.forgequest.app.domain.WorkoutDebriefMath.Headline
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The insight engine: short, numbered comments worked out from stored data, with no AI and no randomness. Every kind has
 * a trigger over the data, a rank (higher is more interesting), a cooldown in days and a few phrasings. [pick] gathers the
 * kinds whose trigger is true, drops the ones still inside their cooldown, sorts by rank and takes the top few; for each it
 * chooses the phrasing shown least recently, from a short history string. The text is resolved from `kind` and `variant`
 * in the UI layer (ui/EnumLabels.kt); the slots carry the numbers and names the sentence needs. Pure and unit-tested
 * (docs/plans/app-critique-2026-11-09.md, section 4).
 */

/** A number or name a sentence needs, in the order its strings use them (%1$, %2$ ...). */
sealed interface Slot {
    data class Count(val n: Int) : Slot
    /** A weight in kg; the UI shows it in the person's unit. */
    data class Kg(val kg: Float) : Slot
    data class Seconds(val s: Int) : Slot
    data class Exercise(val id: String) : Slot
    /** One of the form lines of exercise [id]: 0 and 1 are the cues, 2 the common mistake. */
    data class Cue(val id: String, val line: Int) : Slot
    data class Group(val muscle: Muscle) : Slot
    /** ISO day of the week, Monday = 1. */
    data class Weekday(val day: Int) : Slot
}

/**
 * [plural]: the sentence is a plurals resource, and its quantity is the first [Slot.Count] of the insight. [variants]
 * phrasings each. Higher [rank] is shown first.
 */
enum class InsightKind(val rank: Int, val cooldownDays: Int, val plural: Boolean = false, val variants: Int = 3) {
    FIRST_ROUND_REPS(95, 3, plural = true),
    FIRST_ROUND_HOLD(94, 3),
    CROSS_WORKOUTS(92, 3),
    ANNIVERSARY(90, 28, plural = true),
    NEAR_RECORD_WEIGHT(85, 5),
    NEAR_RECORD_REPS(84, 5),
    CROSS_HOURS(82, 3, plural = true),
    CROSS_KG(81, 3),
    CROSS_SETS(80, 3, plural = true),
    CROSS_REPS(79, 3, plural = true),
    BACK_AFTER_GAP(75, 14, plural = true),
    VS_FOUR_WEEKS(70, 14),
    EASY_STREAK(65, 14),
    HARD_TWICE(64, 14),
    SAME_WEEKDAY(60, 21, plural = true),
    STREAK_NEXT(55, 10, plural = true),
    PACE_FASTER(50, 10, plural = true),
    MUSCLE_NEGLECTED(45, 14, plural = true),
    MUSCLE_WEEK(40, 7, plural = true),
    AVG_REST(30, 10),
    FORM_TIP(20, 21),
}

/** One picked comment: which [kind], which phrasing ([variant], 0 until [InsightKind.variants]) and its [slots]. */
data class Insight(val kind: InsightKind, val rank: Int, val cooldownDays: Int, val variant: Int, val slots: List<Slot>)

/**
 * Everything the triggers read, taken from data already loaded. [sessions] are the finished workouts and [sets] their sets
 * (only done ones count; warm-up and cool-down sets are left out). [focusSessionId] is the workout just finished, or null
 * (Home, reminders) to use the latest one. [streak] is [StreakCalculator.compute]'s result for today, [muscleWeekTargets]
 * the plan's sets per week for each muscle that matters (empty: no balance comments).
 */
data class InsightInput(
    val sessions: List<SessionEntity>,
    val sets: List<SetEntity>,
    val exercises: Map<String, ExerciseEntity>,
    val streak: WeeklyStreak? = null,
    val muscleWeekTargets: Map<Muscle, Int> = emptyMap(),
    val focusSessionId: Long? = null,
)

/** Lifetime totals over working sets; [hours] is whole hours of [minutes]. */
data class LifetimeTotals(val workouts: Int, val sets: Int, val reps: Int, val minutes: Int, val kgLifted: Double) {
    val hours: Int get() = minutes / 60
}

/**
 * Workouts, sets, reps, time and kg lifted over [sessions] and their done working [sets] (warm-up and cool-down left out
 * through [WorkoutScore.isExtra]). A workout counts when it has at least one such set; time is start to finish, as
 * [Trends.lifetime] has it. Bodyweight sets and holds lift 0 kg, as everywhere else.
 */
fun lifetimeTotals(sessions: List<SessionEntity>, sets: List<SetEntity>): LifetimeTotals {
    val ids = sessions.mapTo(HashSet()) { it.id }
    val working = sets.filter { it.done && it.sessionId in ids && !WorkoutScore.isExtra(it.block) }
    val withWork = working.mapTo(HashSet()) { it.sessionId }
    val counted = sessions.filter { it.id in withWork }
    return LifetimeTotals(
        workouts = counted.size,
        sets = working.size,
        reps = working.sumOf { it.reps ?: 0 },
        minutes = counted.sumOf { s -> s.finishedAt?.let { ((it - s.startedAt) / 60_000).toInt().coerceIn(1, 300) } ?: 0 },
        kgLifted = WorkoutDebriefMath.volume(working),
    )
}

// ---- History: "kind:variant:epochDay;..." ----

data class HistoryEntry(val kind: String, val variant: Int, val day: Long)

/** The most entries kept; the oldest go first. */
const val HISTORY_CAP = 60

/** Entries oldest first; anything malformed is skipped. */
fun parseHistory(raw: String): List<HistoryEntry> = raw.split(';').mapNotNull { part ->
    val f = part.split(':')
    if (f.size != 3 || f[0].isBlank()) return@mapNotNull null
    val v = f[1].toIntOrNull() ?: return@mapNotNull null
    val d = f[2].toLongOrNull() ?: return@mapNotNull null
    HistoryEntry(f[0], v, d)
}

fun formatHistory(entries: List<HistoryEntry>): String = entries.joinToString(";") { "${it.kind}:${it.variant}:${it.day}" }

/** [raw] with one more shown entry at the end, cut to the newest [cap]. */
fun appendHistory(raw: String, kind: String, variant: Int, day: Long, cap: Int = HISTORY_CAP): String =
    formatHistory((parseHistory(raw) + HistoryEntry(kind, variant, day)).takeLast(cap))

/** [raw] with every picked insight appended, in order. */
fun appendHistory(raw: String, picked: List<Insight>, day: Long, cap: Int = HISTORY_CAP): String =
    formatHistory((parseHistory(raw) + picked.map { HistoryEntry(it.kind.name, it.variant, day) }).takeLast(cap))

/** The variant of [kind] shown longest ago (never shown first, then the lowest number on a tie). */
fun leastRecentVariant(kind: String, variants: Int, history: List<HistoryEntry>): Int {
    val last = IntArray(variants) { -1 }
    history.forEachIndexed { i, e -> if (e.kind == kind && e.variant in 0 until variants) last[e.variant] = i }
    return last.indices.minBy { last[it] }
}

/**
 * The comments for [input] today: every kind that triggers and is not inside its cooldown, highest rank first, at most
 * [max]. [history] is the stored string; the caller appends what it shows with [appendHistory]. Showing a comment starts
 * its cooldown, so a screen that can reopen should keep its picks instead of calling this again.
 */
fun pick(input: InsightInput, history: String, today: Long, max: Int = 2): List<Insight> {
    val entries = parseHistory(history)
    val lastShown = entries.groupBy { it.kind }.mapValues { (_, l) -> l.maxOf { it.day } }
    val ctx = InsightContext(input, today)
    return InsightKind.entries.mapNotNull { k ->
        val slots = ctx.slots(k) ?: return@mapNotNull null
        val seen = lastShown[k.name]
        if (seen != null && today - seen < k.cooldownDays) return@mapNotNull null
        Insight(k, k.rank, k.cooldownDays, leastRecentVariant(k.name, k.variants, entries), slots)
    }.sortedWith(compareByDescending<Insight> { it.rank }.thenBy { it.kind.ordinal }).take(max)
}

// ---- Triggers ----

private val REP_MARKS = listOf(5, 10, 15, 20, 25, 30, 40, 50)
private val HOLD_MARKS = listOf(30, 60, 90, 120, 180)
private val WORKOUT_MARKS = listOf(10L, 25, 50, 100, 150, 200, 250, 300, 400, 500, 750, 1000, 1500, 2000)
private val SET_MARKS = listOf(100L, 250, 500, 1000, 2500, 5000, 10_000, 25_000, 50_000)
private val REP_TOTAL_MARKS = listOf(1000L, 2500, 5000, 10_000, 25_000, 50_000, 100_000, 250_000, 500_000, 1_000_000)
private val HOUR_MARKS = listOf(10L, 25, 50, 100, 250, 500, 1000)
private val KG_MARKS = listOf(5000L, 10_000, 25_000, 50_000, 100_000, 250_000, 500_000, 1_000_000)
private val ANNIVERSARY_MONTHS = listOf(1L, 6, 12, 24, 36, 48, 60)
private val STREAK_BADGE_WEEKS = listOf(4, 12, 52)
private val builtInIds: Set<String> by lazy { ExerciseCatalog.all.mapTo(HashSet()) { it.id } }

private class InsightContext(val input: InsightInput, val today: Long) {
    val sessions = input.sessions.sortedWith(compareBy({ it.epochDay }, { it.startedAt }, { it.id }))
    private val known = sessions.mapTo(HashSet()) { it.id }
    private val done = input.sets.filter { it.done && it.sessionId in known }
    private val work = done.filterNot { WorkoutScore.isExtra(it.block) || it.exerciseId in ExerciseCatalog.STRETCH_IDS }
    private val workBySession = work.groupBy { it.sessionId }
    private val dayOf = sessions.associate { it.id to it.epochDay }

    /** Workouts with working sets, oldest first. */
    val trained = sessions.filter { workBySession[it.id].orEmpty().isNotEmpty() }
    val focus: SessionEntity? = input.focusSessionId?.let { id -> trained.firstOrNull { it.id == id } } ?: if (input.focusSessionId == null) trained.lastOrNull() else null
    val before: List<SessionEntity> = focus?.let { f -> trained.takeWhile { it.id != f.id } } ?: trained

    private fun kindOf(id: String) = input.exercises[id]?.kind ?: ExerciseKind.REPS
    private fun score(id: String, s: SetEntity) = Records.score(kindOf(id), s.reps, s.seconds, s.weightKg, id)
    private fun setsOf(list: List<SessionEntity>, id: String) = list.flatMap { workBySession[it.id].orEmpty() }.filter { it.exerciseId == id }
    private fun primary(id: String) = input.exercises[id]?.primary.orEmpty()

    /** The focus workout's working sets per exercise, by id. */
    private val focusExercises: List<Pair<String, List<SetEntity>>> =
        focus?.let { f -> workBySession[f.id].orEmpty().groupBy { it.exerciseId }.toList().sortedBy { it.first } }.orEmpty()

    fun slots(kind: InsightKind): List<Slot>? = when (kind) {
        InsightKind.FIRST_ROUND_REPS -> firstRoundReps()
        InsightKind.FIRST_ROUND_HOLD -> firstRoundHold()
        InsightKind.CROSS_WORKOUTS -> crossing(WORKOUT_MARKS) { it.workouts.toDouble() }
        InsightKind.ANNIVERSARY -> anniversary()
        InsightKind.NEAR_RECORD_WEIGHT -> nearRecordWeight()
        InsightKind.NEAR_RECORD_REPS -> nearRecordReps()
        InsightKind.CROSS_HOURS -> crossing(HOUR_MARKS) { it.hours.toDouble() }
        InsightKind.CROSS_KG -> crossing(KG_MARKS) { it.kgLifted }
        InsightKind.CROSS_SETS -> crossing(SET_MARKS) { it.sets.toDouble() }
        InsightKind.CROSS_REPS -> crossing(REP_TOTAL_MARKS) { it.reps.toDouble() }
        InsightKind.BACK_AFTER_GAP -> backAfterGap()
        InsightKind.VS_FOUR_WEEKS -> versusFourWeeks()
        InsightKind.EASY_STREAK -> easyStreak()
        InsightKind.HARD_TWICE -> hardTwice()
        InsightKind.SAME_WEEKDAY -> sameWeekday()
        InsightKind.STREAK_NEXT -> streakNext()
        InsightKind.PACE_FASTER -> paceFaster()
        InsightKind.MUSCLE_NEGLECTED -> muscleNeglected()
        InsightKind.MUSCLE_WEEK -> muscleWeek()
        InsightKind.AVG_REST -> averageRest()
        InsightKind.FORM_TIP -> formTip()
    }

    private fun prior(id: String) = setsOf(before, id)

    private fun firstRoundReps(): List<Slot>? = focusExercises
        .filter { (id, _) -> kindOf(id) == ExerciseKind.REPS }
        .mapNotNull { (id, sets) ->
            val prior = prior(id).ifEmpty { return@mapNotNull null }
            // Only bodyweight sets count for the round number, so added weight can't fake it.
            val now = sets.filter { (it.weightKg ?: 0f) <= 0f }.maxOfOrNull { it.reps ?: 0 } ?: return@mapNotNull null
            val was = prior.maxOf { it.reps ?: 0 }
            REP_MARKS.lastOrNull { it > was && it <= now }?.let { id to it }
        }
        .maxWithOrNull(compareBy({ it.second }, { it.first }))
        ?.let { listOf(Slot.Exercise(it.first), Slot.Count(it.second)) }

    private fun firstRoundHold(): List<Slot>? = focusExercises
        .filter { (id, _) -> kindOf(id) == ExerciseKind.HOLD }
        .mapNotNull { (id, sets) ->
            val prior = prior(id).ifEmpty { return@mapNotNull null }
            val now = sets.maxOf { it.seconds ?: 0 }
            val was = prior.maxOf { it.seconds ?: 0 }
            HOLD_MARKS.lastOrNull { it > was && it <= now }?.let { id to it }
        }
        .maxWithOrNull(compareBy({ it.second }, { it.first }))
        ?.let { listOf(Slot.Exercise(it.first), Slot.Seconds(it.second)) }

    /** The biggest mark in [marks] passed by this workout: the totals before it are under it, the totals with it reach it. */
    private fun crossing(marks: List<Long>, metric: (LifetimeTotals) -> Double): List<Slot>? {
        val f = focus ?: return null
        val was = metric(lifetimeTotals(before, done))
        val now = metric(lifetimeTotals(before + f, done))
        val mark = marks.lastOrNull { it > was && it <= now } ?: return null
        return listOf(Slot.Count(mark.toInt()))
    }

    private fun anniversary(): List<Slot>? {
        val first = trained.firstOrNull() ?: return null
        val start = LocalDate.ofEpochDay(first.epochDay)
        val months = ANNIVERSARY_MONTHS.lastOrNull { today - start.plusMonths(it).toEpochDay() in 0..6 } ?: return null
        return listOf(Slot.Count(months.toInt()))
    }

    private fun nearRecordWeight(): List<Slot>? = focusExercises
        .filter { (id, _) -> kindOf(id) == ExerciseKind.WEIGHTED }
        .mapNotNull { (id, sets) ->
            val prior = prior(id).filter { (it.reps ?: 0) >= 1 }.ifEmpty { return@mapNotNull null }
            if (sets.maxOf { score(id, it) } >= prior.maxOf { score(id, it) }) return@mapNotNull null
            val gap = prior.maxOf { it.weightKg ?: 0f } - sets.maxOf { it.weightKg ?: 0f }
            if (gap > 0.05f && gap <= 2.5f + 0.01f) id to gap else null
        }
        .minWithOrNull(compareBy({ it.second }, { it.first }))
        ?.let { listOf(Slot.Exercise(it.first), Slot.Kg(it.second)) }

    private fun nearRecordReps(): List<Slot>? = focusExercises
        .filter { (id, _) -> kindOf(id) == ExerciseKind.REPS }
        .firstNotNullOfOrNull { (id, sets) ->
            val prior = prior(id).ifEmpty { return@firstNotNullOfOrNull null }
            if (sets.maxOf { score(id, it) } >= prior.maxOf { score(id, it) }) return@firstNotNullOfOrNull null
            if (prior.maxOf { it.reps ?: 0 } - sets.maxOf { it.reps ?: 0 } == 1) listOf(Slot.Exercise(id)) else null
        }

    private fun backAfterGap(): List<Slot>? {
        val f = focus ?: return null
        val last = before.lastOrNull() ?: return null
        val days = (f.epochDay - last.epochDay).toInt()
        return if (days >= 10) listOf(Slot.Count(days)) else null
    }

    /** An exercise's best weight (WEIGHTED), reps (REPS) or seconds (HOLD), as a number to compare. */
    private fun bestValue(id: String, sets: List<SetEntity>): Float = when (kindOf(id)) {
        ExerciseKind.WEIGHTED -> sets.filter { (it.reps ?: 0) >= 1 }.maxOfOrNull { it.weightKg ?: 0f } ?: 0f
        ExerciseKind.REPS -> (sets.maxOfOrNull { it.reps ?: 0 } ?: 0).toFloat()
        ExerciseKind.HOLD -> (sets.maxOfOrNull { it.seconds ?: 0 } ?: 0).toFloat()
    }

    private fun valueSlot(id: String, v: Float): Slot = when (kindOf(id)) {
        ExerciseKind.WEIGHTED -> Slot.Kg(v)
        ExerciseKind.REPS -> Slot.Count(v.roundToInt())
        ExerciseKind.HOLD -> Slot.Seconds(v.roundToInt())
    }

    /** The exercise that gained most against its session 21 to 35 days back (the one nearest 28). */
    private fun versusFourWeeks(): List<Slot>? {
        val f = focus ?: return null
        return focusExercises.mapNotNull { (id, sets) ->
            val then = before.filter { f.epochDay - it.epochDay in 21..35 && setsOf(listOf(it), id).isNotEmpty() }
                .minWithOrNull(compareBy({ abs((f.epochDay - it.epochDay) - 28) }, { it.epochDay })) ?: return@mapNotNull null
            val was = bestValue(id, setsOf(listOf(then), id))
            val now = bestValue(id, sets)
            if (was <= 0f || now <= was + 0.05f) return@mapNotNull null
            Triple(id, was, now)
        }.maxWithOrNull(compareBy({ (it.third - it.second) / it.second }, { it.first }))
            ?.let { (id, was, now) -> listOf(Slot.Exercise(id), valueSlot(id, was), valueSlot(id, now)) }
    }

    private fun effortOf(s: SetEntity) = SetEffort.of(s.effort, s.note)

    /** The last [n] workouts up to and including the focus that contain [id], with that exercise's sets. */
    private fun lastWith(id: String, n: Int): List<List<SetEntity>> =
        (before + listOfNotNull(focus)).map { setsOf(listOf(it), id) }.filter { it.isNotEmpty() }.takeLast(n)

    private fun easyStreak(): List<Slot>? = focusExercises.firstNotNullOfOrNull { (id, _) ->
        val runs = lastWith(id, 3)
        if (runs.size < 3) return@firstNotNullOfOrNull null
        val easy = runs.all { sets -> sets.count { effortOf(it) == SetEffort.EASY } * 2 >= sets.size }
        // Without moving up: the newest best is no better than the oldest of the three.
        val flat = runs.last().maxOf { score(id, it) } <= runs.first().maxOf { score(id, it) }
        if (easy && flat) listOf(Slot.Exercise(id)) else null
    }

    private fun hardTwice(): List<Slot>? = focusExercises.firstNotNullOfOrNull { (id, _) ->
        val runs = lastWith(id, 2)
        if (runs.size == 2 && runs.all { sets -> sets.all { effortOf(it) == SetEffort.HARD } }) listOf(Slot.Exercise(id)) else null
    }

    /** Weeks in a row, ending with the focus workout's week, with a workout on the same weekday (4 or more). */
    private fun sameWeekday(): List<Slot>? {
        val f = focus ?: return null
        val days = trained.mapTo(HashSet()) { it.epochDay }
        var weeks = 0
        while (f.epochDay - 7L * weeks in days) weeks++
        return if (weeks >= 4) listOf(Slot.Weekday(Dates.dayOfWeek(f.epochDay)), Slot.Count(weeks)) else null
    }

    /** Weeks to the next streak badge when it is 1 to 3 away. */
    private fun streakNext(): List<Slot>? {
        val weeks = input.streak?.weeks ?: return null
        val next = STREAK_BADGE_WEEKS.firstOrNull { it > weeks } ?: return null
        val toNext = next - weeks
        return if (weeks >= 1 && toNext <= 3) listOf(Slot.Count(toNext), Slot.Count(weeks)) else null
    }

    /** Whole minutes from the first to the last ticked set of a workout; null with fewer than 4 timed sets. */
    private fun spanMinutes(sessionId: Long): Int? {
        val times = done.filter { it.sessionId == sessionId }.mapNotNull { it.doneAt }
        if (times.size < 4) return null
        return ((times.max() - times.min()) / 60_000).toInt()
    }

    private fun paceFaster(): List<Slot>? {
        val f = focus ?: return null
        val plan = f.planDayId ?: return null
        val last = before.lastOrNull { it.planDayId == plan } ?: return null
        val now = spanMinutes(f.id) ?: return null
        val was = spanMinutes(last.id) ?: return null
        // The same work or more, done at least 3 minutes and 10 % quicker.
        if (workBySession[f.id].orEmpty().size < workBySession[last.id].orEmpty().size) return null
        val diff = was - now
        return if (diff >= 3 && diff * 10 >= was) listOf(Slot.Count(diff)) else null
    }

    /** Mean seconds between ticked sets, leaving out pauses over 10 minutes; needs 5 gaps. */
    private fun averageRest(): List<Slot>? {
        val f = focus ?: return null
        val times = done.filter { it.sessionId == f.id }.mapNotNull { it.doneAt }.sorted()
        val gaps = times.zipWithNext { a, b -> (b - a) / 1000 }.filter { it in 5..600 }
        if (gaps.size < 5) return null
        return listOf(Slot.Seconds((gaps.sum().toDouble() / gaps.size).roundToInt()))
    }

    /** The plan's muscle with the longest gap (10 to 60 days) since direct work. */
    private fun muscleNeglected(): List<Slot>? {
        val last = HashMap<Muscle, Long>()
        for (s in work) {
            val day = dayOf[s.sessionId] ?: continue
            for (m in primary(s.exerciseId)) if (day > (last[m] ?: Long.MIN_VALUE)) last[m] = day
        }
        return input.muscleWeekTargets.filterValues { it > 0 }.keys
            .mapNotNull { m -> last[m]?.let { m to (today - it).toInt() } }
            .filter { it.second in 10..60 }
            .maxWithOrNull(compareBy({ it.second }, { -it.first.ordinal }))
            ?.let { listOf(Slot.Group(it.first), Slot.Count(it.second)) }
    }

    /** A form line for one built-in exercise of the workout, chosen by the day so it rotates without randomness. */
    private fun formTip(): List<Slot>? {
        val ids = focusExercises.map { it.first }.filter { it in builtInIds }
        if (ids.isEmpty()) return null
        val id = ids[(today % ids.size).toInt()]
        return listOf(Slot.Exercise(id), Slot.Cue(id, (today % 3).toInt()))
    }

    /** From Friday on: the muscle furthest under half of its weekly sets, when something was trained this week. */
    private fun muscleWeek(): List<Slot>? {
        if (Dates.dayOfWeek(today) < 5) return null
        val weekStart = Dates.weekStart(today)
        val counts = HashMap<Muscle, Int>()
        var total = 0
        for (s in work) {
            if ((dayOf[s.sessionId] ?: continue) !in weekStart..today) continue
            total++
            for (m in primary(s.exerciseId)) counts[m] = (counts[m] ?: 0) + 1
        }
        if (total == 0) return null
        return input.muscleWeekTargets.filterValues { it > 0 }
            .filter { (m, target) -> (counts[m] ?: 0) * 2 < target }
            .entries.maxWithOrNull(compareBy({ it.value - (counts[it.key] ?: 0) }, { -it.key.ordinal }))
            ?.let { (m, target) -> listOf(Slot.Group(m), Slot.Count(target), Slot.Count(counts[m] ?: 0)) }
    }
}

// ---- Day type, score word and headline ----

/** What the finish screen leads with ([dayType]). */
enum class DayType { PR, MILESTONE, FIRST_OR_BACK, ROUGH, ORDINARY }

/** The word for the day, instead of a bare number ([scoreTier]). */
enum class ScoreTier { RECORD_DAY, STEP_FORWARD, HELD_STEADY, LIGHT_DAY }

val MILESTONE_KINDS = setOf(
    InsightKind.CROSS_WORKOUTS, InsightKind.CROSS_SETS, InsightKind.CROSS_REPS, InsightKind.CROSS_HOURS, InsightKind.CROSS_KG,
    InsightKind.ANNIVERSARY, InsightKind.FIRST_ROUND_REPS, InsightKind.FIRST_ROUND_HOLD,
)

/**
 * First workout or back from a break, then a record, then a milestone (a crossing, anniversary or first round number among
 * [insights]), then a rough day (low recovery or pain), else ordinary.
 */
fun dayType(score: WorkoutScore.Result, debrief: WorkoutDebriefMath.Result, insights: List<Insight>): DayType = when {
    score.firstEver || score.afterBreak || debrief.headline.type == Headline.FIRST_EVER || debrief.headline.type == Headline.AFTER_BREAK ->
        DayType.FIRST_OR_BACK
    score.prs > 0 || debrief.wins.any { it is WorkoutDebriefMath.Win.Pr } -> DayType.PR
    insights.any { it.kind in MILESTONE_KINDS } -> DayType.MILESTONE
    score.rough || score.lowRecovery -> DayType.ROUGH
    else -> DayType.ORDINARY
}

/**
 * A record is a record day; fewer than three quarters of the sets needed, or a score of 5 or less, is a light day; up on
 * at least half of the compared exercises is a step forward; anything else held steady.
 */
fun scoreTier(score: WorkoutScore.Result): ScoreTier = when {
    score.prs > 0 -> ScoreTier.RECORD_DAY
    score.effectiveSets?.let { score.doneSets * 4 < it * 3 } == true -> ScoreTier.LIGHT_DAY
    score.compared > 0 && score.up * 2 >= score.compared -> ScoreTier.STEP_FORWARD
    score.score <= 5 -> ScoreTier.LIGHT_DAY
    else -> ScoreTier.HELD_STEADY
}

/** Phrasings for each of the common headline types; the others keep their own (3 or fewer, picked by workout id). */
const val HEADLINE_VARIANTS = 8
val HEADLINE_TYPES = setOf(Headline.MOSTLY_UP, Headline.ALL_SAME, Headline.SOME_UP, Headline.MIXED, Headline.PR_AND_UP)

/** The key a shown headline is stored under in the history. */
fun headlineKey(type: Headline) = "H_${type.name}"

/** The least recently shown of the [HEADLINE_VARIANTS] for [type], or null for a type without its own set. */
fun pickHeadlineVariant(type: Headline, history: String): Int? =
    if (type in HEADLINE_TYPES) leastRecentVariant(headlineKey(type), HEADLINE_VARIANTS, parseHistory(history)) else null

/**
 * The plan's sets per week for each muscle: every working (not warm-up or cool-down) set of the plan's training days,
 * counted once for each primary muscle of its exercise. Feeds [InsightInput.muscleWeekTargets].
 */
fun muscleWeekTargets(items: List<PlanItemEntity>, exercises: Map<String, ExerciseEntity>): Map<Muscle, Int> {
    val out = HashMap<Muscle, Int>()
    for (item in items) {
        if (WorkoutScore.isExtra(item.block)) continue
        for (m in exercises[item.exerciseId]?.primary.orEmpty()) out[m] = (out[m] ?: 0) + item.sets
    }
    return out
}
