package com.forgequest.app.domain

import com.forgequest.app.data.db.ExerciseEntity
import com.forgequest.app.data.db.PlanItemEntity
import com.forgequest.app.data.db.SessionEntity
import com.forgequest.app.data.db.SetEntity
import com.forgequest.app.domain.WorkoutDebriefMath.Headline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class InsightsTest {
    // A Wednesday, so the weekday and week-based kinds have something to say.
    private val today = LocalDate.of(2026, 11, 11).toEpochDay()

    private val exercises = mapOf(
        "bench" to ExerciseEntity("bench", "Bench", ExerciseKind.WEIGHTED, listOf(Muscle.CHEST), emptyList()),
        "pull_up" to ExerciseEntity("pull_up", "Pull-up", ExerciseKind.REPS, listOf(Muscle.UPPER_BACK), emptyList()),
        "hang" to ExerciseEntity("hang", "Hang", ExerciseKind.HOLD, listOf(Muscle.FOREARMS), emptyList()),
    )

    private var nextId = 1L

    private fun session(day: Long, plan: Long? = null, minutes: Int = 45) =
        SessionEntity(id = nextId++, planDayId = plan, title = "T", epochDay = day, startedAt = day * 86_400_000L, finishedAt = day * 86_400_000L + minutes * 60_000L)

    private fun set(
        s: SessionEntity, ex: String, reps: Int? = null, kg: Float? = null, sec: Int? = null,
        effort: String = "", block: String = "Main", doneAt: Long? = null, index: Int = 0,
    ) = SetEntity(sessionId = s.id, exerciseId = ex, itemPosition = 0, block = block, circuit = false, setIndex = index, targetLabel = "", reps = reps, seconds = sec, weightKg = kg, done = true, effort = effort, doneAt = doneAt)

    private fun input(sessions: List<SessionEntity>, sets: List<SetEntity>, focus: Long? = null, streak: WeeklyStreak? = null, targets: Map<Muscle, Int> = emptyMap()) =
        InsightInput(sessions, sets, exercises, streak, targets, focus)

    private fun kinds(i: InsightInput, history: String = "", day: Long = today) = pick(i, history, day, max = 99).map { it.kind }

    private fun fires(kind: InsightKind, i: InsightInput) = assertTrue("$kind should fire", kind in kinds(i))
    private fun quiet(kind: InsightKind, i: InsightInput) = assertFalse("$kind should not fire", kind in kinds(i))

    /** An earlier bench session and a focus one, so there is something to compare. */
    private fun twoBench(then: Float, now: Float, thenReps: Int = 5, nowReps: Int = 5, gap: Long = 3): InsightInput {
        val a = session(today - gap)
        val b = session(today)
        return input(listOf(a, b), listOf(set(a, "bench", thenReps, then), set(b, "bench", nowReps, now)))
    }

    // ---- triggers ----

    @Test fun nearRecordWeight() {
        val near = twoBench(then = 60f, now = 57.5f)
        fires(InsightKind.NEAR_RECORD_WEIGHT, near)
        val slots = pick(near, "", today, 99).first { it.kind == InsightKind.NEAR_RECORD_WEIGHT }.slots
        assertEquals(listOf(Slot.Exercise("bench"), Slot.Kg(2.5f)), slots)
        quiet(InsightKind.NEAR_RECORD_WEIGHT, twoBench(then = 60f, now = 55f))
        quiet(InsightKind.NEAR_RECORD_WEIGHT, twoBench(then = 60f, now = 62.5f))
    }

    @Test fun nearRecordReps() {
        val a = session(today - 3)
        val b = session(today)
        fun with(now: Int) = input(listOf(a, b), listOf(set(a, "pull_up", 10), set(b, "pull_up", now)))
        fires(InsightKind.NEAR_RECORD_REPS, with(9))
        quiet(InsightKind.NEAR_RECORD_REPS, with(8))
        quiet(InsightKind.NEAR_RECORD_REPS, with(10))
    }

    @Test fun firstRoundNumber() {
        val a = session(today - 3)
        val b = session(today)
        fun reps(was: Int, now: Int) = input(listOf(a, b), listOf(set(a, "pull_up", was), set(b, "pull_up", now)))
        fires(InsightKind.FIRST_ROUND_REPS, reps(8, 10))
        val slots = pick(reps(8, 10), "", today, 99).first { it.kind == InsightKind.FIRST_ROUND_REPS }.slots
        assertEquals(listOf(Slot.Exercise("pull_up"), Slot.Count(10)), slots)
        quiet(InsightKind.FIRST_ROUND_REPS, reps(10, 12))
        quiet(InsightKind.FIRST_ROUND_REPS, reps(6, 9))
        fun hold(was: Int, now: Int) = input(listOf(a, b), listOf(set(a, "hang", sec = was), set(b, "hang", sec = now)))
        fires(InsightKind.FIRST_ROUND_HOLD, hold(50, 60))
        quiet(InsightKind.FIRST_ROUND_HOLD, hold(60, 65))
    }

    @Test fun versusFourWeeks() {
        fires(InsightKind.VS_FOUR_WEEKS, twoBench(then = 50f, now = 60f, gap = 28))
        quiet(InsightKind.VS_FOUR_WEEKS, twoBench(then = 50f, now = 60f, gap = 10))
        quiet(InsightKind.VS_FOUR_WEEKS, twoBench(then = 60f, now = 50f, gap = 28))
    }

    @Test fun lifetimeCrossings() {
        val old = (1..9).map { session(today - 30 - it) }
        val last = session(today)
        val oldSets = old.flatMap { s -> (0 until 10).map { set(s, "pull_up", 10, index = it) } }
        // 9 workouts before, the 10th now.
        fires(InsightKind.CROSS_WORKOUTS, input(old + last, oldSets + set(last, "pull_up", 10)))
        quiet(InsightKind.CROSS_WORKOUTS, input(old.drop(1) + last, oldSets.filter { it.sessionId != old.first().id } + set(last, "pull_up", 10)))
        // 90 sets before, the 100th with 10 more now.
        val withTen = oldSets + (0 until 10).map { set(last, "pull_up", 10, index = it) }
        fires(InsightKind.CROSS_SETS, input(old + last, withTen))
        // 900 reps before, 1000 with the workout.
        fires(InsightKind.CROSS_REPS, input(old + last, withTen))
        // 9 workouts of 45 minutes is under 7 hours; a 250-minute one takes the total past 10.
        val long = session(today, minutes = 250)
        fires(InsightKind.CROSS_HOURS, input(old + long, oldSets + set(long, "pull_up", 10)))
        // kg: 4,000 before, 6,000 with the workout.
        val heavyOld = old.take(4).map { s -> set(s, "bench", 10, 100f) }
        val heavyNow = session(today)
        fires(InsightKind.CROSS_KG, input(old.take(4) + heavyNow, heavyOld + set(heavyNow, "bench", 20, 100f)))
        quiet(InsightKind.CROSS_KG, input(old.take(4) + heavyNow, heavyOld + set(heavyNow, "bench", 10, 50f)))
    }

    @Test fun totalsLeaveOutWarmUpAndCoolDown() {
        val s = session(today)
        val sets = listOf(
            set(s, "bench", 10, 20f, block = "Warm-up"),
            set(s, "bench", 5, 100f),
            set(s, "pull_up", 8, block = "Cool-down"),
        )
        val t = lifetimeTotals(listOf(s), sets)
        assertEquals(1, t.workouts)
        assertEquals(1, t.sets)
        assertEquals(5, t.reps)
        assertEquals(500.0, t.kgLifted, 0.001)
        assertEquals(0, lifetimeTotals(listOf(s), listOf(set(s, "bench", 10, block = "Warm-up"))).workouts)
    }

    @Test fun anniversary() {
        val start = LocalDate.ofEpochDay(today).minusMonths(6).toEpochDay()
        val a = session(start)
        val b = session(today - 1)
        val i = input(listOf(a, b), listOf(set(a, "pull_up", 5), set(b, "pull_up", 5)))
        fires(InsightKind.ANNIVERSARY, i)
        assertEquals(listOf<Slot>(Slot.Count(6)), pick(i, "", today, 99).first { it.kind == InsightKind.ANNIVERSARY }.slots)
        assertFalse(InsightKind.ANNIVERSARY in kinds(i, day = today + 20))
    }

    @Test fun sameWeekday() {
        val days = (0..4).map { session(today - 7L * it) }
        val sets = days.map { set(it, "pull_up", 5) }
        fires(InsightKind.SAME_WEEKDAY, input(days, sets))
        val broken = days.filterIndexed { i, _ -> i != 2 }
        quiet(InsightKind.SAME_WEEKDAY, input(broken, broken.map { set(it, "pull_up", 5) }))
    }

    @Test fun streakNextBadge() {
        val s = session(today)
        val sets = listOf(set(s, "pull_up", 5))
        fun streak(weeks: Int) = WeeklyStreak(weeks, weeks, 1, 3, emptyList())
        fires(InsightKind.STREAK_NEXT, input(listOf(s), sets, streak = streak(2)))
        quiet(InsightKind.STREAK_NEXT, input(listOf(s), sets, streak = streak(0)))
        quiet(InsightKind.STREAK_NEXT, input(listOf(s), sets, streak = streak(6)))
        quiet(InsightKind.STREAK_NEXT, input(listOf(s), sets, streak = null))
    }

    @Test fun backAfterGap() {
        val a = session(today - 12)
        val b = session(today)
        fires(InsightKind.BACK_AFTER_GAP, input(listOf(a, b), listOf(set(a, "pull_up", 5), set(b, "pull_up", 5))))
        val c = session(today - 9)
        quiet(InsightKind.BACK_AFTER_GAP, input(listOf(c, b), listOf(set(c, "pull_up", 5), set(b, "pull_up", 5))))
    }

    private fun timed(s: SessionEntity, spanMinutes: Int, n: Int = 6): List<SetEntity> {
        val t0 = 1_000_000L
        return (0 until n).map { i -> set(s, "pull_up", 8, doneAt = t0 + i * spanMinutes * 60_000L / (n - 1), index = i) }
    }

    @Test fun paceFaster() {
        val a = session(today - 7, plan = 1)
        val b = session(today, plan = 1)
        fires(InsightKind.PACE_FASTER, input(listOf(a, b), timed(a, 50) + timed(b, 40)))
        quiet(InsightKind.PACE_FASTER, input(listOf(a, b), timed(a, 50) + timed(b, 48)))
        quiet(InsightKind.PACE_FASTER, input(listOf(a, b), timed(a, 40) + timed(b, 50)))
        // Less work than last time is not "faster".
        quiet(InsightKind.PACE_FASTER, input(listOf(a, b), timed(a, 50) + timed(b, 40, n = 4)))
        // No plan day to compare with.
        val c = session(today - 7)
        val d = session(today)
        quiet(InsightKind.PACE_FASTER, input(listOf(c, d), timed(c, 50) + timed(d, 40)))
    }

    @Test fun averageRest() {
        val s = session(today)
        fires(InsightKind.AVG_REST, input(listOf(s), timed(s, 10, n = 6)))
        val slots = pick(input(listOf(s), timed(s, 10, n = 6)), "", today, 99).first { it.kind == InsightKind.AVG_REST }.slots
        assertEquals(listOf<Slot>(Slot.Seconds(120)), slots)
        quiet(InsightKind.AVG_REST, input(listOf(s), timed(s, 10, n = 3)))
        quiet(InsightKind.AVG_REST, input(listOf(s), listOf(set(s, "pull_up", 5))))
    }

    @Test fun muscleNeglected() {
        val a = session(today - 14)
        val b = session(today)
        val sets = listOf(set(a, "bench", 5, 60f), set(b, "pull_up", 5))
        val targets = mapOf(Muscle.CHEST to 10, Muscle.UPPER_BACK to 10)
        fires(InsightKind.MUSCLE_NEGLECTED, input(listOf(a, b), sets, targets = targets))
        val slots = pick(input(listOf(a, b), sets, targets = targets), "", today, 99).first { it.kind == InsightKind.MUSCLE_NEGLECTED }.slots
        assertEquals(listOf(Slot.Group(Muscle.CHEST), Slot.Count(14)), slots)
        quiet(InsightKind.MUSCLE_NEGLECTED, input(listOf(a, b), sets, targets = emptyMap()))
        val recent = session(today - 5)
        quiet(InsightKind.MUSCLE_NEGLECTED, input(listOf(recent, b), listOf(set(recent, "bench", 5, 60f), set(b, "pull_up", 5)), targets = targets))
    }

    @Test fun muscleWeek() {
        val friday = today + 2
        val s = session(friday)
        val sets = (0 until 2).map { set(s, "pull_up", 5, index = it) }
        val targets = mapOf(Muscle.CHEST to 10, Muscle.UPPER_BACK to 4)
        val i = input(listOf(s), sets, targets = targets)
        assertTrue(InsightKind.MUSCLE_WEEK in kinds(i, day = friday))
        val slots = pick(i, "", friday, 99).first { it.kind == InsightKind.MUSCLE_WEEK }.slots
        assertEquals(listOf(Slot.Group(Muscle.CHEST), Slot.Count(10), Slot.Count(0)), slots)
        // Too early in the week, and with nothing trained this week.
        assertFalse(InsightKind.MUSCLE_WEEK in kinds(i, day = today))
        assertFalse(InsightKind.MUSCLE_WEEK in kinds(i, day = friday + 7))
    }

    @Test fun easyAndHardRuns() {
        val days = (0..2).map { session(today - 3L * (2 - it)) }
        fun runs(effort: String, sess: List<SessionEntity>, ex: String = "pull_up") = input(sess, sess.flatMap { s -> (0 until 3).map { set(s, ex, 8, effort = effort, index = it) } })
        fires(InsightKind.EASY_STREAK, runs("EASY", days))
        quiet(InsightKind.EASY_STREAK, runs("EASY", days.drop(1)))
        quiet(InsightKind.EASY_STREAK, runs("OK", days))
        // Easy, but the numbers moved up, so no "move up" nudge.
        val moved = days.mapIndexed { i, s -> set(s, "pull_up", 8 + i * 2, effort = "EASY") }
        quiet(InsightKind.EASY_STREAK, input(days, moved))
        fires(InsightKind.HARD_TWICE, runs("HARD", days.drop(1)))
        quiet(InsightKind.HARD_TWICE, runs("HARD", days.take(1)))
        quiet(InsightKind.HARD_TWICE, runs("PAIN", days.drop(1)))
    }

    @Test fun focusDefaultsToLatestAndHonoursTheId() {
        val a = session(today - 12)
        val b = session(today)
        val sets = listOf(set(a, "pull_up", 5), set(b, "pull_up", 5))
        fires(InsightKind.BACK_AFTER_GAP, input(listOf(a, b), sets))
        // Focus on the older one: nothing came before it.
        quiet(InsightKind.BACK_AFTER_GAP, input(listOf(a, b), sets, focus = a.id))
    }

    // ---- picking ----

    @Test fun cooldownBlocksUntilItEnds() {
        val i = twoBench(then = 60f, now = 57.5f)
        val kind = InsightKind.NEAR_RECORD_WEIGHT
        val h = appendHistory("", kind.name, 0, today - 2)
        assertFalse(kind in kinds(i, h))
        assertTrue(kind in kinds(i, appendHistory("", kind.name, 0, today - kind.cooldownDays.toLong())))
    }

    @Test fun picksHighestRankFirstAndCapsAtMax() {
        val a = session(today - 12)
        val b = session(today)
        // Back after a gap (75), near a record in reps (84) and first round number (95).
        val i = input(listOf(a, b), listOf(set(a, "pull_up", 11), set(b, "pull_up", 10)))
        val all = pick(i, "", today, 99)
        assertEquals(all.sortedByDescending { it.rank }, all)
        assertEquals(2, pick(i, "", today).size)
        assertEquals(all.take(2), pick(i, "", today))
        assertTrue(pick(i, "", today, max = 0).isEmpty())
    }

    @Test fun sameDataGivesTheSamePicks() {
        val i = twoBench(then = 60f, now = 57.5f)
        assertEquals(pick(i, "", today, 5), pick(i, "", today, 5))
    }

    @Test fun leastRecentlyShownVariantWins() {
        val kind = "NEAR_RECORD_REPS"
        assertEquals(0, leastRecentVariant(kind, 3, emptyList()))
        assertEquals(2, leastRecentVariant(kind, 3, parseHistory("$kind:0:1;$kind:1:2")))
        // All shown: the one shown longest ago.
        assertEquals(1, leastRecentVariant(kind, 3, parseHistory("$kind:1:1;$kind:0:2;$kind:2:3")))
        // Another kind's history is ignored, and so is an out-of-range variant.
        assertEquals(0, leastRecentVariant(kind, 3, parseHistory("OTHER:0:1;$kind:7:2")))
        // Same day: the later entry is the more recent.
        assertEquals(0, leastRecentVariant(kind, 2, parseHistory("$kind:0:5;$kind:1:5")))
    }

    @Test fun pickRotatesVariantsAcrossShowings() {
        val i = twoBench(then = 60f, now = 57.5f)
        var history = ""
        val seen = mutableListOf<Int>()
        var day = today
        repeat(4) {
            val p = pick(i, history, day, 99).first { it.kind == InsightKind.NEAR_RECORD_WEIGHT }
            seen += p.variant
            history = appendHistory(history, listOf(p), day)
            day += 10
        }
        assertEquals(listOf(0, 1, 2, 0), seen)
    }

    // ---- history ----

    @Test fun historyRoundTripsAndSkipsJunk() {
        val raw = "A:0:100;B:2:101"
        assertEquals(listOf(HistoryEntry("A", 0, 100), HistoryEntry("B", 2, 101)), parseHistory(raw))
        assertEquals(raw, formatHistory(parseHistory(raw)))
        assertTrue(parseHistory("").isEmpty())
        assertEquals(listOf(HistoryEntry("A", 0, 100)), parseHistory("junk;A:0:100;B:x:1;:1:2;C:1"))
    }

    @Test fun historyIsCapped() {
        var raw = ""
        for (d in 1..100) raw = appendHistory(raw, "K", d % 3, d.toLong())
        val entries = parseHistory(raw)
        assertEquals(HISTORY_CAP, entries.size)
        assertEquals(100L, entries.last().day)
        assertEquals(41L, entries.first().day)
        assertEquals(5, parseHistory(appendHistory("A:0:1;A:0:2;A:0:3;A:0:4;A:0:5", "A", 1, 6, cap = 5)).size)
    }

    @Test fun appendingPickedInsights() {
        val ins = Insight(InsightKind.CROSS_KG, 81, 3, 2, emptyList())
        assertEquals("X:0:1;CROSS_KG:2:50", appendHistory("X:0:1", listOf(ins), 50))
    }

    // ---- score word, day type, headline ----

    private fun score(
        total: Int = 8, done: Int = 10, planned: Int? = 10, up: Int = 0, compared: Int = 0, prs: Int = 0,
        rough: Boolean = false, first: Boolean = false, back: Boolean = false, low: Boolean = false,
    ) = WorkoutScore.Result(total, done, planned, up, compared, prs, rough, first, back, planned, emptyList(), low)

    private fun debrief(type: Headline = Headline.ALL_SAME, wins: List<WorkoutDebriefMath.Win> = emptyList()) =
        WorkoutDebriefMath.Result(WorkoutDebriefMath.HeadlineLine(type, 0), wins, emptyList(), emptyList(), false, false)

    @Test fun scoreTiers() {
        assertEquals(ScoreTier.RECORD_DAY, scoreTier(score(prs = 1, up = 1, compared = 3)))
        assertEquals(ScoreTier.RECORD_DAY, scoreTier(score(prs = 2, done = 3)))
        assertEquals(ScoreTier.STEP_FORWARD, scoreTier(score(up = 2, compared = 3)))
        assertEquals(ScoreTier.STEP_FORWARD, scoreTier(score(up = 1, compared = 2)))
        assertEquals(ScoreTier.HELD_STEADY, scoreTier(score(up = 1, compared = 3)))
        assertEquals(ScoreTier.HELD_STEADY, scoreTier(score(compared = 3)))
        assertEquals(ScoreTier.LIGHT_DAY, scoreTier(score(done = 5, planned = 10)))
        assertEquals(ScoreTier.LIGHT_DAY, scoreTier(score(total = 5, planned = null, done = 4)))
        // Three quarters of the sets exactly is not light.
        assertEquals(ScoreTier.HELD_STEADY, scoreTier(score(done = 8, planned = 10, total = 7, compared = 2)))
    }

    @Test fun dayTypes() {
        val none = emptyList<Insight>()
        val milestone = listOf(Insight(InsightKind.CROSS_WORKOUTS, 92, 3, 0, listOf(Slot.Count(50))))
        val minor = listOf(Insight(InsightKind.AVG_REST, 30, 10, 0, listOf(Slot.Seconds(60))))
        assertEquals(DayType.ORDINARY, dayType(score(), debrief(), none))
        assertEquals(DayType.ORDINARY, dayType(score(), debrief(), minor))
        assertEquals(DayType.PR, dayType(score(prs = 1), debrief(), none))
        assertEquals(DayType.PR, dayType(score(), debrief(wins = listOf(WorkoutDebriefMath.Win.Pr("bench", WorkoutScore.PrKind.WEIGHT, 60f, 5, null))), none))
        assertEquals(DayType.MILESTONE, dayType(score(), debrief(), milestone))
        assertEquals(DayType.PR, dayType(score(prs = 1), debrief(), milestone))
        assertEquals(DayType.FIRST_OR_BACK, dayType(score(first = true), debrief(), milestone))
        assertEquals(DayType.FIRST_OR_BACK, dayType(score(back = true, prs = 1), debrief(), none))
        assertEquals(DayType.FIRST_OR_BACK, dayType(score(), debrief(Headline.AFTER_BREAK), none))
        assertEquals(DayType.ROUGH, dayType(score(rough = true), debrief(), none))
        assertEquals(DayType.ROUGH, dayType(score(low = true), debrief(), minor))
        assertEquals(DayType.MILESTONE, dayType(score(rough = true), debrief(), milestone))
    }

    @Test fun headlineVariantsRotateThroughEight() {
        assertEquals(8, HEADLINE_VARIANTS)
        var history = ""
        val seen = (1..9).map { day ->
            val v = pickHeadlineVariant(Headline.MOSTLY_UP, history)!!
            history = appendHistory(history, headlineKey(Headline.MOSTLY_UP), v, day.toLong())
            v
        }
        assertEquals(listOf(0, 1, 2, 3, 4, 5, 6, 7, 0), seen)
    }

    @Test fun headlineHistoryIsPerType() {
        val h = appendHistory("", headlineKey(Headline.ALL_SAME), 0, 1)
        assertEquals(0, pickHeadlineVariant(Headline.MIXED, h))
        assertEquals(1, pickHeadlineVariant(Headline.ALL_SAME, h))
        assertNotNull(pickHeadlineVariant(Headline.PR_AND_UP, ""))
        assertNotNull(pickHeadlineVariant(Headline.SOME_UP, ""))
        for (t in listOf(Headline.FIRST_EVER, Headline.AFTER_BREAK, Headline.SHORT, Headline.MOSTLY_DOWN, Headline.ALL_NEW)) {
            assertNull(pickHeadlineVariant(t, ""))
        }
    }

    @Test fun everyKindHasPositiveCooldownAndDistinctRank() {
        assertTrue(InsightKind.entries.size >= 15)
        assertTrue(InsightKind.entries.all { it.cooldownDays > 0 && it.variants >= 2 })
        assertEquals(InsightKind.entries.size, InsightKind.entries.map { it.rank }.toSet().size)
    }

    @Test fun muscleWeekTargetsSumWorkingSetsPerPrimaryMuscle() {
        fun item(ex: String, sets: Int, block: String) = PlanItemEntity(dayId = 1, position = 0, exerciseId = ex, block = block, circuit = false, sets = sets)
        val t = muscleWeekTargets(
            listOf(item("bench", 3, "Main"), item("bench", 2, "Main"), item("bench", 2, "Warm-up"), item("pull_up", 4, "Main"), item("gone", 5, "Main")),
            exercises,
        )
        assertEquals(mapOf(Muscle.CHEST to 5, Muscle.UPPER_BACK to 4), t)
    }
}
