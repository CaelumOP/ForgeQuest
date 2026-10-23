package com.forgequest.app.domain

import com.forgequest.app.data.db.ExerciseEntity
import com.forgequest.app.data.db.SessionEntity
import com.forgequest.app.data.db.SetEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrendsTest {
    // 2026-10-14 is a Wednesday; its week starts Monday 2026-10-12.
    private val today = java.time.LocalDate.of(2026, 10, 14).toEpochDay()
    private val monday = Dates.weekStart(today)

    private fun set(session: Long, reps: Int, pr: Boolean = false) =
        SetEntity(sessionId = session, exerciseId = "pull_up", itemPosition = 0, block = "Main", circuit = false, setIndex = 0, targetLabel = "", reps = reps, done = true, isPr = pr)

    @Test
    fun weeklyCountsEndWithTheCurrentWeek() {
        val days = setOf(monday, monday + 2, monday - 7, monday - 20)
        assertEquals(listOf(0, 1, 2), Trends.weeklyCounts(days, 3, today))
        assertEquals(listOf(1, 0, 1, 2), Trends.weeklyCounts(days, 4, today))
    }

    @Test
    fun trainingLoadComparesToPreviousTrainedWeeks() {
        assertEquals(100, Trends.trainingLoad(listOf(12)))
        assertEquals(150, Trends.trainingLoad(listOf(10, 0, 10, 15)))
        assertEquals(0, Trends.trainingLoad(emptyList()))
    }

    @Test
    fun bestSetSeriesMarksRecordsAgainstAllEarlierDays() {
        val sessionDay = mapOf(1L to today - 20, 2L to today - 10, 3L to today - 5, 4L to today)
        val sets = listOf(set(1, 6), set(1, 5), set(2, 7), set(3, 7), set(4, 9))
        val series = Trends.bestSetSeries(sets, ExerciseKind.REPS, sessionDay)
        assertEquals(listOf(6f, 7f, 7f, 9f), series.map { it.value })
        assertEquals(listOf(false, true, false, true), series.map { it.isRecord })
    }

    @Test
    fun movingAverageUsesOnlyDaysInsideTheWindow() {
        val pts = listOf(TrendPoint(1, 70f), TrendPoint(2, 72f), TrendPoint(20, 80f))
        assertEquals(listOf(70f, 71f, 80f), Trends.movingAverage(pts, 7).map { it.value })
    }

    @Test
    fun consistencyGridRowsAreWeekdaysAndFutureIsMarked() {
        val grid = Trends.consistencyGrid(setOf(monday, monday - 7), setOf(monday - 7), weeks = 2, today = today)
        assertEquals(7, grid.size)
        assertEquals(listOf(Trends.DayCell.RECORD, Trends.DayCell.DONE), grid[0])
        assertEquals(Trends.DayCell.FUTURE, grid[6][1]) // Sunday of this week hasn't happened
        assertEquals(Trends.DayCell.REST, grid[1][1])
    }

    @Test
    fun thenNowNeedsTwoPointsInRange() {
        val s = listOf(TrendPoint(today - 100, 5f), TrendPoint(today - 10, 7f), TrendPoint(today, 9f))
        assertEquals(7f to 9f, Trends.thenNow(s, today - 30))
        assertNull(Trends.thenNow(s, today - 5))
        assertEquals(5f to 9f, Trends.thenNow(s, Long.MIN_VALUE))
    }

    @Test
    fun lifetimeTotals() {
        val sessions = listOf(SessionEntity(id = 1, planDayId = null, title = "A", epochDay = today, startedAt = 0, finishedAt = 45 * 60_000))
        val sets = listOf(set(1, 8, pr = true), set(1, 7).copy(weightKg = 20f))
        val life = Trends.lifetime(sessions, sets)
        assertEquals(Trends.Lifetime(workouts = 1, reps = 15, minutes = 45, records = 1, kgLifted = 140f), life)
    }

    @Test
    fun proteinDaysAndPercent() {
        val nutrition = listOf(monday to 150f, monday + 1 to 90f, monday - 7 to 160f)
        assertEquals(listOf(1, 1), Trends.proteinDaysPerWeek(nutrition, 140, 2, today))
        assertEquals(50, Trends.percentChange(6f, 9f))
        assertNull(Trends.percentChange(0f, 3f))
    }

    @Test
    fun aLiftWithNoImprovementOverTwoWeeksIsStalled() {
        val days = listOf(today - 20, today - 10, today)
        val sessions = days.mapIndexed { i, d -> SessionEntity(id = i + 1L, planDayId = null, title = "Day", epochDay = d, startedAt = 0L) }
        val sets = sessions.map { s ->
            SetEntity(sessionId = s.id, exerciseId = "bench_press", itemPosition = 0, block = "Main", circuit = false, setIndex = 0, targetLabel = "", reps = 5, weightKg = 80f, done = true)
        }
        val bench = ExerciseEntity("bench_press", "Bench press", ExerciseKind.WEIGHTED, listOf(Muscle.CHEST), emptyList())
        val report = Trends.analyzeFatigueAndPlateaus(sessions, sets, mapOf(bench.id to bench), today = today)
        val lift = report.stalledLifts.single()
        assertEquals("bench_press", lift.exerciseId)
        assertEquals(3, lift.sessionsCount)
        assertEquals(20, lift.spanDays)
    }
}
