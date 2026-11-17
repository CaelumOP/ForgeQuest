package com.forgequest.app.data

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.forgequest.app.data.db.AppDatabase
import com.forgequest.app.data.db.ExerciseEntity
import com.forgequest.app.data.db.PlanDayEntity
import com.forgequest.app.data.db.PlanEntity
import com.forgequest.app.data.db.PlanItemEntity
import com.forgequest.app.data.db.SessionEntity
import com.forgequest.app.data.db.SetEntity
import com.forgequest.app.domain.ExerciseKind
import com.forgequest.app.domain.Muscle
import com.forgequest.app.domain.countsForPR
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class WorkoutRepositoryV42Test {

    private fun newDb(): AppDatabase {
        val ctx: Context = ApplicationProvider.getApplicationContext()
        return Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).build()
    }

    @Test
    fun stepUpSkipsPositionsWithDoneSetsAndUpdatesPlanForNextTime() = runBlocking {
        val db = newDb()
        try {
            val repo = WorkoutRepository(db)
            db.exercises().upsertAll(
                listOf(
                    ExerciseEntity("wall_push_up", "Wall push-up", ExerciseKind.REPS, listOf(Muscle.CHEST), emptyList()),
                    ExerciseEntity("knee_push_up", "Knee push-up", ExerciseKind.REPS, listOf(Muscle.CHEST), emptyList()),
                ),
            )
            val planId = db.plans().insertPlan(PlanEntity(name = "P", description = "", source = "manual", active = true, createdAt = 1L))
            val dayId = db.plans().insertDay(PlanDayEntity(planId = planId, dayOfWeek = 1, title = "Day 1", isRest = false))
            db.plans().insertItems(
                listOf(
                    PlanItemEntity(dayId = dayId, position = 0, exerciseId = "wall_push_up", block = "Main", circuit = false, sets = 2, repsMin = 10, repsMax = 12),
                ),
            )
            val sessionId = db.sessions().insert(SessionEntity(planDayId = dayId, title = "Day 1", epochDay = 100L, startedAt = 1000L))
            db.sessions().insertSets(
                listOf(
                    SetEntity(sessionId = sessionId, exerciseId = "wall_push_up", itemPosition = 0, block = "Main", circuit = false, setIndex = 0, targetLabel = "10", reps = 12, done = true),
                    SetEntity(sessionId = sessionId, exerciseId = "wall_push_up", itemPosition = 0, block = "Main", circuit = false, setIndex = 1, targetLabel = "10", reps = 12, done = false),
                    SetEntity(sessionId = sessionId, exerciseId = "wall_push_up", itemPosition = 1, block = "Main", circuit = false, setIndex = 0, targetLabel = "10", reps = 10, done = false),
                ),
            )

            // Position 0 has a done set so it must be skipped (only position 1 is swapped).
            val session = db.sessions().byId(sessionId)!!
            val swapped = repo.stepUp(session, listOf(0, 1), "wall_push_up", "knee_push_up")
            assertEquals(1, swapped)

            val setsAfter = db.sessions().sets(sessionId)
            val pos0 = setsAfter.filter { it.itemPosition == 0 }
            val pos1 = setsAfter.filter { it.itemPosition == 1 }
            assertTrue(pos0.all { it.exerciseId == "wall_push_up" })
            assertEquals(1, pos0.count { it.done })
            assertTrue(pos1.all { it.exerciseId == "knee_push_up" })
            assertEquals("knee_push_up", db.plans().items(dayId).single().exerciseId)

            // When every position has a done set, 0 positions swap today while the plan still updates.
            db.plans().insertItems(listOf(PlanItemEntity(dayId = dayId, position = 1, exerciseId = "knee_push_up", block = "Main", circuit = false, sets = 1, repsMin = 10, repsMax = 12)))
            val swappedNone = repo.stepUp(session, listOf(0), "wall_push_up", "knee_push_up")
            assertEquals(0, swappedNone)
            assertTrue(db.sessions().sets(sessionId).filter { it.itemPosition == 0 }.all { it.exerciseId == "wall_push_up" })
        } finally {
            db.close()
        }
    }

    @Test
    fun tickAllAndFinishMarksAutoTickedAndNeverBecomesPrOnLaterEdit() = runBlocking {
        val db = newDb()
        try {
            val repo = WorkoutRepository(db)
            db.exercises().upsertAll(
                listOf(ExerciseEntity("bench", "Bench press", ExerciseKind.WEIGHTED, listOf(Muscle.CHEST), emptyList())),
            )
            // Prior finished baseline session so PRs are eligible on the second session.
            val prevId = db.sessions().insert(SessionEntity(planDayId = null, title = "Push", epochDay = 195L, startedAt = 1000L, finishedAt = 2000L))
            db.sessions().insertSets(
                listOf(SetEntity(sessionId = prevId, exerciseId = "bench", itemPosition = 0, block = "Main", circuit = false, setIndex = 0, targetLabel = "8", reps = 8, weightKg = 40f, done = true)),
            )

            val sessionId = db.sessions().insert(SessionEntity(planDayId = null, title = "Push", epochDay = 200L, startedAt = 1000L))
            db.sessions().insertSets(
                listOf(
                    SetEntity(sessionId = sessionId, exerciseId = "bench", itemPosition = 0, block = "Main", circuit = false, setIndex = 0, targetLabel = "8", reps = 8, weightKg = 50f, done = true),
                    SetEntity(sessionId = sessionId, exerciseId = "bench", itemPosition = 0, block = "Main", circuit = false, setIndex = 1, targetLabel = "8", reps = 10, weightKg = 80f, done = false),
                ),
            )

            repo.finishSession(sessionId, tickAll = true)

            val setsAfterFinish = db.sessions().sets(sessionId).sortedBy { it.setIndex }
            val manual = setsAfterFinish[0]
            val auto = setsAfterFinish[1]
            assertFalse(manual.autoTicked)
            assertTrue(manual.isPr)
            assertTrue(auto.done)
            assertTrue(auto.autoTicked)
            assertFalse(auto.isPr)
            assertFalse(countsForPR(auto))

            // Editing the finished workout triggers recomputeRecords; the auto-ticked 80 kg set must still NEVER become a PR.
            repo.updateFinishedSet(manual.copy(weightKg = 52.5f))
            val setsAfterEdit = db.sessions().sets(sessionId).sortedBy { it.setIndex }
            assertTrue(setsAfterEdit[0].isPr)
            assertTrue(setsAfterEdit[1].autoTicked)
            assertFalse(setsAfterEdit[1].isPr)
        } finally {
            db.close()
        }
    }
}
