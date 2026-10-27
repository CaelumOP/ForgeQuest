package com.forgequest.app.domain

import com.forgequest.app.data.db.SessionEntity
import com.forgequest.app.data.db.SetEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkoutScoreTest {
    private fun set(reps: Int, kg: Float? = null, pr: Boolean = false, effort: String = "", id: String = "ex", session: Long = 1) =
        SetEntity(sessionId = session, exerciseId = id, itemPosition = 0, block = "Main", circuit = false, setIndex = 0, targetLabel = "", reps = reps, weightKg = kg, done = true, isPr = pr, effort = effort)

    private fun work(done: List<SetEntity>, last: List<SetEntity>) = WorkoutScore.Work("ex", ExerciseKind.WEIGHTED, done, last)

    private val same = work(listOf(set(8, 50f)), listOf(set(8, 50f)))
    private val up = work(listOf(set(10, 50f)), listOf(set(8, 50f)))
    private val down = work(listOf(set(6, 40f)), listOf(set(8, 50f)))

    private fun many(w: WorkoutScore.Work, n: Int) = List(n) { w }

    private fun scoreOf(work: List<WorkoutScore.Work>, planned: Int?, low: Boolean = false) = WorkoutScore.compute(work, planned, low)

    @Test
    fun everythingSameNormalDayIsSeven() {
        assertEquals(7, scoreOf(many(same, 4), planned = 4).score)
    }

    @Test
    fun threeUpOneSameAndAPrIsNine() {
        val withPr = work(listOf(set(10, 50f, pr = true)), listOf(set(8, 50f)))
        val r = scoreOf(many(up, 2) + withPr + same, planned = 4)
        assertEquals(9, r.score)
        assertEquals(3, r.up)
        assertEquals(4, r.compared)
        assertEquals(1, r.prs)
    }

    @Test
    fun firstWorkoutEverIsSeven() {
        val r = scoreOf(many(work(listOf(set(8, 50f)), emptyList()), 3), planned = 3)
        assertEquals(7, r.score)
        assertEquals(0, r.compared)
    }

    @Test
    fun roughDayHoldingSteadyIsNine() {
        assertEquals(9, scoreOf(many(same, 4), planned = 4, low = true).score)
    }

    @Test
    fun painSetCountsAsARoughDay() {
        val pain = work(listOf(set(8, 50f, effort = "PAIN")), listOf(set(8, 50f)))
        assertEquals(9, scoreOf(listOf(pain), planned = 1).score)
    }

    @Test
    fun halfDoneAllDownIsFour() {
        assertEquals(4, scoreOf(many(down, 6), planned = 12).score)
    }

    @Test
    fun twoOfTwelveDoneIsClampedToFour() {
        assertEquals(4, scoreOf(many(down, 2), planned = 12).score)
    }

    @Test
    fun freeWorkoutTwoUpOfTwoIsNine() {
        val r = scoreOf(many(up, 2), planned = null)
        assertEquals(9, r.score)
        assertEquals(null, r.plannedSets)
    }

    @Test
    fun batchFindsLastTimeAndPlannedSets() {
        fun session(id: Long, day: Long) = SessionEntity(id = id, planDayId = 7, title = "Push", epochDay = day, startedAt = day * 1000, finishedAt = day * 1000 + 1)
        val sets = listOf(set(8, 50f, session = 1), set(10, 50f, session = 2))
        val r = WorkoutScore.forSessions(listOf(session(1, 100), session(2, 102)), sets, mapOf("ex" to ExerciseKind.WEIGHTED), mapOf(7L to 1), emptySet())
        assertEquals(7, r.getValue(1).score)
        assertEquals(9, r.getValue(2).score)
    }
}
