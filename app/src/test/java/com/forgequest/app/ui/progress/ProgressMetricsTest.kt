package com.forgequest.app.ui.progress

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.forgequest.app.data.MeasurementMath
import com.forgequest.app.data.MeasurementMetric
import com.forgequest.app.data.MeasurementsRepository
import com.forgequest.app.data.db.AppDatabase
import com.forgequest.app.data.db.BodyMeasurementEntity
import com.forgequest.app.data.db.ExerciseEntity
import com.forgequest.app.data.db.SessionEntity
import com.forgequest.app.data.db.SetEntity
import com.forgequest.app.domain.Dates
import com.forgequest.app.domain.ExerciseKind
import com.forgequest.app.domain.Muscle
import com.forgequest.app.domain.SetType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ProgressMetricsTest {

    @Test
    fun weeklyMuscleVolumeRespectsSetTypesAndWeekBoundary() {
        val today = Dates.today()
        val thisWeekDay = Dates.weekStart(today)
        val lastWeekDay = thisWeekDay - 1L

        val bench = ExerciseEntity(
            id = "bench_press",
            name = "Bench press",
            kind = ExerciseKind.WEIGHTED,
            primary = listOf(Muscle.CHEST),
            secondary = listOf(Muscle.TRICEPS, Muscle.SHOULDERS),
        )
        val stretch = ExerciseEntity(
            id = "triceps_overhead_stretch",
            name = "Stretch",
            kind = ExerciseKind.HOLD,
            primary = listOf(Muscle.TRICEPS),
            secondary = emptyList(),
        )
        val exercises = mapOf(bench.id to bench, stretch.id to stretch)

        val sessions = listOf(
            SessionEntity(id = 1, planDayId = null, title = "This week", epochDay = thisWeekDay, startedAt = 1L, finishedAt = 2L),
            SessionEntity(id = 2, planDayId = null, title = "Last week", epochDay = lastWeekDay, startedAt = 1L, finishedAt = 2L),
            SessionEntity(id = 3, planDayId = null, title = "Open", epochDay = thisWeekDay, startedAt = 1L, finishedAt = null),
        )

        val sets = listOf(
            // Counts: WORKING + Main
            SetEntity(sessionId = 1, exerciseId = "bench_press", itemPosition = 0, block = "Main", circuit = false, setIndex = 0, targetLabel = "8", reps = 8, weightKg = 80f, done = true, setType = SetType.WORKING.name),
            // Counts: FAILURE
            SetEntity(sessionId = 1, exerciseId = "bench_press", itemPosition = 0, block = "Main", circuit = false, setIndex = 1, targetLabel = "8", reps = 6, weightKg = 80f, done = true, setType = SetType.FAILURE.name),
            // Excluded: WARMUP
            SetEntity(sessionId = 1, exerciseId = "bench_press", itemPosition = 0, block = "Main", circuit = false, setIndex = 2, targetLabel = "10", reps = 10, weightKg = 40f, done = true, setType = SetType.WARMUP.name),
            // Excluded: DROPSET
            SetEntity(sessionId = 1, exerciseId = "bench_press", itemPosition = 0, block = "Main", circuit = false, setIndex = 3, targetLabel = "10", reps = 10, weightKg = 50f, done = true, setType = SetType.DROPSET.name),
            // Excluded: legacy Warm-up block
            SetEntity(sessionId = 1, exerciseId = "bench_press", itemPosition = 0, block = "Warm-up", circuit = false, setIndex = 4, targetLabel = "10", reps = 10, weightKg = 30f, done = true, setType = SetType.WORKING.name),
            // Excluded: stretch
            SetEntity(sessionId = 1, exerciseId = "triceps_overhead_stretch", itemPosition = 1, block = "Cool-down", circuit = false, setIndex = 0, targetLabel = "30 s", seconds = 30, done = true, setType = SetType.WORKING.name),
            // Excluded: last week
            SetEntity(sessionId = 2, exerciseId = "bench_press", itemPosition = 0, block = "Main", circuit = false, setIndex = 0, targetLabel = "8", reps = 8, weightKg = 80f, done = true, setType = SetType.WORKING.name),
            // Excluded: unfinished session
            SetEntity(sessionId = 3, exerciseId = "bench_press", itemPosition = 0, block = "Main", circuit = false, setIndex = 0, targetLabel = "8", reps = 8, weightKg = 80f, done = true, setType = SetType.WORKING.name),
        )

        val vol = ProgressMetrics.weeklyMuscleVolume(sessions, sets, exercises, today)
        assertEquals(2f, vol[Muscle.CHEST] ?: 0f, 0.001f)
        assertEquals(1f, vol[Muscle.TRICEPS] ?: 0f, 0.001f)
        assertEquals(1f, vol[Muscle.SHOULDERS] ?: 0f, 0.001f)
        assertNull(vol[Muscle.QUADS])
    }

    @Test
    fun epley1RmAndStrengthToBodyweight() {
        assertEquals(0f, ProgressMetrics.epley1Rm(0f, 5), 0.001f)
        assertEquals(0f, ProgressMetrics.epley1Rm(100f, 0), 0.001f)
        assertEquals(100f, ProgressMetrics.epley1Rm(100f, 1), 0.001f)
        assertEquals(116.6667f, ProgressMetrics.epley1Rm(100f, 5), 0.01f)
        assertEquals(1.5f, ProgressMetrics.strengthToBodyweight(120f, 80f)!!, 0.001f)
        assertNull(ProgressMetrics.strengthToBodyweight(120f, null))
        assertNull(ProgressMetrics.strengthToBodyweight(120f, 0f))
        val pcts = ProgressMetrics.epleyPercentages(100f)
        assertEquals(95 to 95f, pcts.first())
        assertEquals(70 to 70f, pcts.last())
    }

    @Test
    fun measurementsRepositoryLogsUpdatesSameDayAndDeletes() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
        try {
            val repo = MeasurementsRepository(db)
            repo.log(MeasurementMetric.WAIST, 84.36f, epochDay = 20000L)
            repo.log(MeasurementMetric.WAIST, 83.92f, epochDay = 20000L)
            repo.log(MeasurementMetric.WAIST, 83.4f, epochDay = 20007L)
            val all = repo.all()
            assertEquals(2, all.size)
            val trend = MeasurementMath.trend(all, MeasurementMetric.WAIST)
            assertEquals(83.9f, trend[0].value, 0.01f)
            assertEquals(83.4f, trend[1].value, 0.01f)
            repo.delete(all.first().id)
            assertEquals(1, repo.all().size)
        } finally {
            db.close()
        }
    }

    @Test
    fun measurementTrendSortsByDay() {
        val rows = listOf(
            BodyMeasurementEntity(id = 1, epochDay = 10, metric = "CHEST", valueCm = 101f, loggedAt = 1),
            BodyMeasurementEntity(id = 2, epochDay = 5, metric = "CHEST", valueCm = 100f, loggedAt = 1),
            BodyMeasurementEntity(id = 3, epochDay = 7, metric = "WAIST", valueCm = 80f, loggedAt = 1),
        )
        val trend = MeasurementMath.trend(rows, MeasurementMetric.CHEST)
        assertEquals(listOf(5L, 10L), trend.map { it.epochDay })
        assertTrue(MeasurementMath.latest(rows, MeasurementMetric.ARMS) == null)
    }
}
