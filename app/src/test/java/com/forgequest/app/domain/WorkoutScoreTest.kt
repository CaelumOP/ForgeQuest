package com.forgequest.app.domain

import com.forgequest.app.data.db.SessionEntity
import com.forgequest.app.data.db.SetEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutScoreTest {
    private fun set(
        reps: Int,
        kg: Float? = null,
        pr: Boolean = false,
        effort: String = "",
        block: String = "Main",
        id: String = "ex",
        session: Long = 1,
        seconds: Int? = null,
    ) = SetEntity(
        sessionId = session, exerciseId = id, itemPosition = 0, block = block, circuit = false, setIndex = 0, targetLabel = "",
        reps = reps, seconds = seconds, weightKg = kg, done = true, isPr = pr, effort = effort,
    )

    private fun sets(n: Int, reps: Int, kg: Float?, pr: Boolean = false, block: String = "Main") = List(n) { set(reps, kg, pr = pr, block = block) }

    private var next = 0
    private fun work(done: List<SetEntity>, last: List<SetEntity>, id: String = "ex${next++}") = WorkoutScore.Work(id, ExerciseKind.WEIGHTED, done, last)

    // Each exercise has 3 sets, so 4 exercises are 12 sets.
    private fun same() = work(sets(3, 8, 50f), sets(3, 8, 50f))
    private fun up() = work(sets(3, 10, 50f), sets(3, 8, 50f))
    // A real drop: fewer reps at a lighter weight.
    private fun down() = work(sets(3, 6, 40f), sets(3, 8, 50f))
    private fun many(n: Int, f: () -> WorkoutScore.Work) = List(n) { f() }

    private fun scoreOf(work: List<WorkoutScore.Work>, planned: Int?, low: Boolean = false) = WorkoutScore.compute(work, planned, low)

    private fun trend(done: List<SetEntity>, last: List<SetEntity>, kind: ExerciseKind = ExerciseKind.WEIGHTED) =
        WorkoutScore.trend(kind, "ex", done, last)

    @Test
    fun allSameAllSetsIsEight() {
        assertEquals(8, scoreOf(many(4, ::same), planned = 12).score)
    }

    @Test
    fun oneUpThreeSameIsEightAndNineWithAPr() {
        assertEquals(8, scoreOf(listOf(up()) + many(3, ::same), planned = 12).score)
        assertEquals(9, scoreOf(listOf(work(sets(3, 10, 50f, pr = true), sets(3, 8, 50f))) + many(3, ::same), planned = 12).score)
    }

    @Test
    fun twoUpTwoSameIsNineAndTenWithAPr() {
        assertEquals(9, scoreOf(many(2, ::up) + many(2, ::same), planned = 12).score)
        val r = scoreOf(listOf(up(), work(sets(3, 10, 50f, pr = true), sets(3, 8, 50f))) + many(2, ::same), planned = 12)
        assertEquals(10, r.score)
        assertEquals(2, r.up)
        assertEquals(4, r.compared)
    }

    @Test
    fun oneDownThreeSameIsSeven() {
        assertEquals(7, scoreOf(listOf(down()) + many(3, ::same), planned = 12).score)
    }

    @Test
    fun twoOfNineSetsWithOneUpAndAPrIsFour() {
        assertEquals(4, scoreOf(listOf(work(listOf(set(10, 50f, pr = true), set(10, 50f)), listOf(set(8, 50f)))), planned = 9).score)
    }

    @Test
    fun nothingToCompareIsEight() {
        val r = scoreOf(many(3) { work(sets(3, 8, 50f), emptyList()) }, planned = 9)
        assertEquals(8, r.score)
        assertEquals(0, r.compared)
    }

    @Test
    fun freeWorkoutTwoUpOfTwoIsNine() {
        val r = scoreOf(many(2, ::up), planned = null)
        assertEquals(9, r.score)
        assertEquals(null, r.plannedSets)
    }

    @Test
    fun roughDayHoldingSteadyOrDroppingIsEight() {
        val r = scoreOf(many(4, ::same), planned = 12, low = true)
        assertEquals(8, r.score)
        assertTrue(r.rough)
        assertTrue(r.lowRecovery)
        assertEquals(8, scoreOf(listOf(down()) + many(3, ::same), planned = 12, low = true).score)
        assertEquals(8, scoreOf(many(4, ::down), planned = 12, low = true).score)
    }

    @Test
    fun roughDayNeedsTwoThirdsOfThePlannedSets() {
        // ceil(0.67 × 12) = ceil(8.04) = 9, so 9 of 12 sets is full completion.
        val w = many(3) { work(sets(2, 8, 40f), sets(3, 8, 50f)) } + down()
        val r = scoreOf(w, planned = 12, low = true)
        assertEquals(8, r.score)
        assertEquals(9, r.doneSets)
        assertEquals(7, scoreOf(w.drop(1), planned = 12, low = true).score)
    }

    @Test
    fun painForgivesOnlyItsOwnExerciseAndCutsThePlannedSets() {
        val painDown = work(sets(2, 8, 40f) + set(8, 40f, effort = "PAIN"), sets(3, 8, 50f))
        val r = scoreOf(many(3, ::down) + painDown, planned = 12)
        assertEquals(6, r.score)
        assertTrue(r.rough)
        assertFalse(r.lowRecovery)
        val stopped = work(listOf(set(8, 40f, effort = "PAIN")), sets(3, 8, 50f))
        assertEquals(8, scoreOf(listOf(stopped) + many(3, ::same), planned = 12).score)
    }

    @Test
    fun heavierResetIsUpButAFailedSingleIsDown() {
        // Rule A: following the app's own reset (3x12 to 3x8, heavier) is progress; it was SAME before.
        assertEquals(WorkoutScore.Trend.UP, trend(sets(3, 8, 62.5f), sets(3, 12, 60f)))
        assertEquals(WorkoutScore.Trend.SAME, trend(sets(3, 5, 67.5f), sets(3, 10, 60f)))
        assertEquals(WorkoutScore.Trend.DOWN, trend(listOf(set(1, 65f)), sets(3, 10, 60f)))
        assertEquals(WorkoutScore.Trend.UP, trend(sets(3, 8, 62.5f), sets(3, 8, 60f)))
    }

    @Test
    fun smallDropIsSameRealDropIsDown() {
        fun hold(s: Int) = set(0, 0f, seconds = s)
        assertEquals(WorkoutScore.Trend.SAME, trend(List(3) { hold(59) }, List(3) { hold(60) }, ExerciseKind.HOLD))
        // WEIGHTED "total fell" uses the volume (kg x reps): 57.5 vs 60 at the same reps is -4 %, so it holds steady.
        assertEquals(WorkoutScore.Trend.SAME, trend(sets(3, 8, 57.5f), sets(3, 8, 60f)))
        // 12 vs 14 at the same reps: the best set and the volume both fell (-14 %), so it is a drop.
        assertEquals(WorkoutScore.Trend.DOWN, trend(sets(3, 10, 12f), sets(3, 10, 14f)))
        assertEquals(420.0, WorkoutScore.stats(ExerciseKind.WEIGHTED, "ex", sets(3, 10, 14f)).volume, 0.0)
        assertEquals(WorkoutScore.Trend.DOWN, trend(sets(3, 6, 57.5f), sets(3, 8, 60f)))
    }

    @Test
    fun ruleAScenarios() {
        fun hold(s: Int) = set(0, null, seconds = s)
        // The reset day.
        assertEquals(WorkoutScore.Trend.UP, trend(sets(3, 8, 62.5f), sets(3, 12, 60f)))
        // A one-rep wobble at the same weight.
        assertEquals(WorkoutScore.Trend.SAME, trend(listOf(set(9, 30f), set(9, 30f), set(9, 30f)), listOf(set(10, 30f), set(9, 30f), set(8, 30f))))
        // Three reps fewer on every set.
        assertEquals(WorkoutScore.Trend.DOWN, trend(sets(3, 7, 50f), sets(3, 10, 50f)))
        // A heavy single on top keeps the 60 kg sets in the total (30 + 1 reps).
        val withSingle = WorkoutScore.stats(ExerciseKind.WEIGHTED, "ex", sets(3, 10, 60f) + set(1, 102.5f))
        assertEquals(31, withSingle.total)
        assertEquals(60f, withSingle.top, 0.001f)
        // Holds: 10 % shorter holds steady, 20 % shorter with a lower total is down.
        assertEquals(WorkoutScore.Trend.SAME, trend(List(3) { hold(54) }, List(3) { hold(60) }, ExerciseKind.HOLD))
        assertEquals(WorkoutScore.Trend.DOWN, trend(List(3) { hold(48) }, List(3) { hold(60) }, ExerciseKind.HOLD))
        // REPS: one rep fewer holds steady.
        assertEquals(WorkoutScore.Trend.SAME, trend(sets(3, 9, null), sets(3, 10, null), ExerciseKind.REPS))
        assertEquals(WorkoutScore.Trend.DOWN, trend(sets(3, 8, null), sets(3, 10, null), ExerciseKind.REPS))
    }

    @Test
    fun rampUpSetsAreLeftOutOfTheTotal() {
        assertEquals(WorkoutScore.Trend.SAME, trend(listOf(set(10, 20f), set(5, 35f)) + sets(3, 8, 60f), sets(3, 8, 60f)))
        assertEquals(WorkoutScore.Trend.UP, trend(sets(3, 8, 60f) + set(10, 45f), sets(3, 8, 60f)))
        assertEquals(WorkoutScore.Trend.UP, trend(listOf(set(9, 0f), set(8, 0f), set(8, 0f)), sets(3, 8, 0f)))
    }

    @Test
    fun skippedWarmUpScoresTheSameAsDoneWarmUp() {
        val warm = sets(5, 10, null, block = "Warm-up")
        val a = scoreOf(listOf(work(warm, warm, "jumping_jacks")) + many(4, ::same), planned = 12)
        val b = scoreOf(many(4, ::same), planned = 12)
        assertEquals(8, a.score)
        assertEquals(b.score, a.score)
        assertEquals(12, a.doneSets)
        assertEquals(b.compared, a.compared)
    }

    private fun session(id: Long, day: Long, plannedSets: Int? = null) =
        SessionEntity(id = id, planDayId = 7, title = "Push", epochDay = day, startedAt = day * 1000, finishedAt = day * 1000 + 1, plannedSets = plannedSets)

    private val kinds = mapOf("ex" to ExerciseKind.WEIGHTED)

    @Test
    fun batchFindsLastTimeAndTheFirstEverHidesTheNumber() {
        val sets = listOf(set(8, 50f, session = 1), set(10, 50f, session = 2))
        val r = WorkoutScore.forSessions(listOf(session(1, 100), session(2, 102)), sets, kinds, mapOf(7L to 1), emptySet())
        val first = r.getValue(1)
        assertEquals(8, first.score)
        assertTrue(first.firstEver)
        assertFalse(first.showNumber)
        val second = r.getValue(2)
        assertEquals(9, second.score)
        assertFalse(second.firstEver)
        assertTrue(second.showNumber)
    }

    @Test
    fun savedPlannedSetsWinOverThePlanAsItIsNow() {
        fun done(s: Long) = List(9) { set(8, 50f, session = s) }
        val sessions = listOf(session(1, 100, 9), session(2, 102, 9), session(3, 104))
        val r = WorkoutScore.forSessions(sessions, done(1) + done(2) + done(3), kinds, mapOf(7L to 15), emptySet())
        assertEquals(9, r.getValue(2).plannedSets)
        assertEquals(8, r.getValue(2).score)
        assertEquals(15, r.getValue(3).plannedSets)
        assertEquals(4, r.getValue(3).score)
    }

    @Test
    fun afterASickDayTheNumberIsHidden() {
        val sets = listOf(set(8, 50f, session = 1), set(8, 50f, session = 2), set(8, 50f, session = 3))
        val r = WorkoutScore.forSessions(
            listOf(session(1, 100), session(2, 103), session(3, 104)), sets, kinds, mapOf(7L to 1), emptySet(), mapOf(101L to DayTag.SICK),
        )
        assertTrue(r.getValue(2).afterBreak)
        assertFalse(r.getValue(2).showNumber)
        assertEquals(8, r.getValue(2).score)
        assertFalse(r.getValue(3).afterBreak)
    }

    @Test
    fun lowRecoveryDaysAreRough() {
        val sets = listOf(set(8, 50f, session = 1), set(6, 50f, session = 2))
        val r = WorkoutScore.forSessions(listOf(session(1, 100), session(2, 102)), sets, kinds, mapOf(7L to 1), setOf(102L))
        assertTrue(r.getValue(2).rough)
        assertEquals(8, r.getValue(2).score)
    }
}
