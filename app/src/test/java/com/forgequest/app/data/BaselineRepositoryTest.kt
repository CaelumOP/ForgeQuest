package com.forgequest.app.data

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.forgequest.app.data.db.AppDatabase
import com.forgequest.app.data.db.BaselineTestEntity
import com.forgequest.app.data.db.PlanDayEntity
import com.forgequest.app.data.db.PlanEntity
import com.forgequest.app.data.db.PlanItemEntity
import com.forgequest.app.data.db.SessionEntity
import com.forgequest.app.data.db.SetEntity
import com.forgequest.app.data.plan.PlanAnswers
import com.forgequest.app.data.prefs.SettingsRepository
import com.forgequest.app.data.seed.ExerciseCatalog
import com.forgequest.app.domain.ExerciseKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class BaselineRepositoryTest {

    private fun newDb(): AppDatabase =
        Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java).build()

    @Test
    fun daoAndRepoReturnNewestPerExerciseAndObserveAllWithoutCreatingSessions() = runBlocking {
        val db = newDb()
        try {
            val repo = BaselineRepository(db)
            repo.save(BaselineTestEntity(exerciseId = "push_up", kind = "REPS", reps = 8, createdAt = 1000L))
            repo.save(BaselineTestEntity(exerciseId = "push_up", kind = "REPS", reps = 14, createdAt = 2000L))
            repo.save(BaselineTestEntity(exerciseId = "plank", kind = "HOLD", seconds = 45, createdAt = 1500L))

            assertEquals(14, repo.latest("push_up")?.reps)
            assertEquals(45, repo.latest("plank")?.seconds)
            assertNull(repo.latest("pull_up"))

            val latest = repo.latestAll().associateBy { it.exerciseId }
            assertEquals(2, latest.size)
            assertEquals(14, latest.getValue("push_up").reps)
            assertEquals(45, latest.getValue("plank").seconds)

            assertEquals(3, repo.observe().first().size)
            assertTrue(db.sessions().finished().isEmpty())
            assertTrue(db.sessions().allRecentDoneSets().isEmpty())

            repo.deleteAll()
            assertTrue(repo.all().isEmpty())
        } finally {
            db.close()
        }
    }

    @Test
    fun migrateFromPlanAnswersRunsOncePerIdAndKeepsAnswersReadable() = runBlocking {
        val ctx: Context = ApplicationProvider.getApplicationContext()
        val db = newDb()
        try {
            val settings = SettingsRepository(ctx)
            val savedAnswers = PlanAnswers(pushUpMax = 12, pullUpMax = 0)
            settings.update { it.copy(planBuilderAnswers = savedAnswers.encode()) }

            val repo = BaselineRepository(db, settings)
            // Pre-existing push_up baseline must NOT be overwritten by migration.
            repo.save(BaselineTestEntity(exerciseId = "push_up", kind = "REPS", reps = 20, createdAt = 5000L))

            repo.migrateFromPlanAnswers()
            assertEquals(20, repo.latest("push_up")?.reps)
            assertEquals(0, repo.latest("pull_up")?.reps)
            assertEquals("REPS", repo.latest("pull_up")?.kind)

            // Answers field stays readable with original values.
            val decoded = PlanAnswers.decode(settings.current().planBuilderAnswers)
            assertNotNull(decoded)
            assertEquals(12, decoded?.pushUpMax)
            assertEquals(0, decoded?.pullUpMax)

            // Re-running migration does not insert duplicates.
            repo.migrateFromPlanAnswers()
            assertEquals(1, repo.all().count { it.exerciseId == "pull_up" })

            // resolvePlanInput prefers baseline rows, falling back to answers when absent.
            val input = repo.resolvePlanInput(PlanAnswers(pushUpMax = 5, pullUpMax = 3))
            assertEquals(20, input.pushUpMax)
            assertEquals(0, input.pullUpMax)

            repo.deleteAll()
            settings.update { it.copy(planBuilderAnswers = null) }
            val fallback = repo.resolvePlanInput(PlanAnswers(pushUpMax = 9, pullUpMax = 4))
            assertEquals(9, fallback.pushUpMax)
            assertEquals(4, fallback.pullUpMax)
        } finally {
            db.close()
        }
    }

    @Test
    fun buildSetsFollowsPrecedenceHistoryOverBaselineOverColdStartOverBlank() = runBlocking {
        val db = newDb()
        try {
            val workouts = WorkoutRepository(db)
            val baselines = BaselineRepository(db)
            val byId = ExerciseCatalog.byId
            val exercises = listOf(
                byId.getValue("bench_press"),
                byId.getValue("db_bench_press"),
                byId.getValue("overhead_press"),
                byId.getValue("push_up"),
                byId.getValue("pull_up"),
                byId.getValue("plank"),
                byId.getValue("back_squat"),
            )
            db.exercises().upsertAll(exercises)

            // History for bench_press (also acts as a coldStart donor for overhead_press).
            val histSession = db.sessions().insert(SessionEntity(planDayId = null, title = "Past", epochDay = 10L, startedAt = 1000L, finishedAt = 2000L))
            db.sessions().insertSets(
                listOf(
                    SetEntity(sessionId = histSession, exerciseId = "bench_press", itemPosition = 0, block = "Main", circuit = false, setIndex = 0, targetLabel = "8", reps = 8, weightKg = 100f, done = true),
                ),
            )

            // Baselines:
            // - bench_press (has history -> history wins!)
            // - db_bench_press (no history, has baseline 20kg x 8 @ repsMin 8 -> target 15.0kg, beats coldStart!)
            // - overhead_press (no history, baseline is TooLight 15 reps -> falls back to coldStart donor from bench_press!)
            // - back_squat (no history, no baseline, no donor -> blank 0f)
            // - push_up (REPS baseline max 8 -> target 5 < repsMin 8 -> uses 5)
            // - pull_up (REPS baseline max 20 -> target 14 >= repsMin 8 -> keeps plan 8)
            // - plank (HOLD baseline 40s -> target 25s)
            baselines.save(BaselineTestEntity(exerciseId = "bench_press", kind = ExerciseKind.WEIGHTED.name, reps = 5, weightKg = 40f, createdAt = 3000L))
            baselines.save(BaselineTestEntity(exerciseId = "db_bench_press", kind = ExerciseKind.WEIGHTED.name, reps = 8, weightKg = 20f, createdAt = 3000L))
            baselines.save(BaselineTestEntity(exerciseId = "overhead_press", kind = ExerciseKind.WEIGHTED.name, reps = 15, weightKg = 10f, createdAt = 3000L))
            baselines.save(BaselineTestEntity(exerciseId = "push_up", kind = ExerciseKind.REPS.name, reps = 8, createdAt = 3000L))
            baselines.save(BaselineTestEntity(exerciseId = "pull_up", kind = ExerciseKind.REPS.name, reps = 20, createdAt = 3000L))
            baselines.save(BaselineTestEntity(exerciseId = "plank", kind = ExerciseKind.HOLD.name, seconds = 40, createdAt = 3000L))

            val planId = db.plans().insertPlan(PlanEntity(name = "P", description = "", source = "manual", active = true, createdAt = 1L))
            val dayId = db.plans().insertDay(PlanDayEntity(planId = planId, dayOfWeek = 1, title = "Day 1", isRest = false))
            val items = listOf(
                PlanItemEntity(dayId = dayId, position = 0, exerciseId = "bench_press", block = "Main", circuit = false, sets = 1, repsMin = 8, repsMax = 12, weightKg = 0f),
                PlanItemEntity(dayId = dayId, position = 1, exerciseId = "db_bench_press", block = "Main", circuit = false, sets = 1, repsMin = 8, repsMax = 12, weightKg = 0f),
                PlanItemEntity(dayId = dayId, position = 2, exerciseId = "overhead_press", block = "Main", circuit = false, sets = 1, repsMin = 8, repsMax = 12, weightKg = 0f),
                PlanItemEntity(dayId = dayId, position = 3, exerciseId = "back_squat", block = "Main", circuit = false, sets = 1, repsMin = 8, repsMax = 12, weightKg = 0f),
                PlanItemEntity(dayId = dayId, position = 4, exerciseId = "push_up", block = "Main", circuit = false, sets = 1, repsMin = 8, repsMax = 12),
                PlanItemEntity(dayId = dayId, position = 5, exerciseId = "pull_up", block = "Main", circuit = false, sets = 1, repsMin = 8, repsMax = 12),
                PlanItemEntity(dayId = dayId, position = 6, exerciseId = "plank", block = "Core", circuit = false, sets = 1, seconds = 30),
            )
            db.plans().insertItems(items)

            val day = PlanDayWithItems(db.plans().day(dayId)!!, db.plans().items(dayId))
            val sessionId = workouts.startSession(day)
            val sets = db.sessions().sets(sessionId).associateBy { it.exerciseId }

            // bench_press: history (100f) wins over baseline (40f)
            assertEquals(100f, sets.getValue("bench_press").weightKg!!, 0.01f)
            // db_bench_press: 20 * (1 + 8/30) / (1 + 11/30) * 0.9 = 16.68... -> floor to 2.5 = 15.0f
            assertEquals(15f, sets.getValue("db_bench_press").weightKg!!, 0.01f)
            // overhead_press: TooLight baseline falls back to coldStart donor from bench_press
            assertTrue(sets.getValue("overhead_press").weightKg!! > 0f)
            // back_squat: blank (0f)
            assertEquals(0f, sets.getValue("back_squat").weightKg!!, 0.01f)
            // push_up: baseline target 5 < repsMin 8 -> 5
            assertEquals(5, sets.getValue("push_up").reps)
            // pull_up: baseline target 14 >= repsMin 8 -> keeps plan 8
            assertEquals(8, sets.getValue("pull_up").reps)
            // plank: hold target floor(0.7*40/5)*5 = 25
            assertEquals(25, sets.getValue("plank").seconds)
        } finally {
            db.close()
        }
    }
}
