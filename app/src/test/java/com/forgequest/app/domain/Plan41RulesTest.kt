package com.forgequest.app.domain

import com.forgequest.app.data.db.PlanEntity
import com.forgequest.app.data.db.PlanItemEntity
import com.forgequest.app.data.db.SessionEntity
import com.forgequest.app.data.db.SetEntity
import com.forgequest.app.data.db.ExerciseEntity
import org.junit.Assert.*
import org.junit.Test

class Plan41RulesTest {
    private val plan = PlanEntity(1, "PPL", source = "manual", active = true,
        createdAt = 0, scheduleMode = "ROTATION")
    private val session = SessionEntity(planDayId = 10, title = "Push", epochDay = 0, startedAt = 0)
    private fun item(position: Int, sets: Int = 3) = PlanItemEntity(
        dayId = 10, position = position, exerciseId = "bench_press", block = "Main",
        circuit = false, sets = sets,
    )

    @Test fun rotationFinishAndRestoredFinishedSessions() {
        val queue = listOf(10L, 20L, 30L)
        assertEquals(1, Rotation.cursorOnFinish(plan, queue, session, false))
        assertNull(Rotation.cursorOnFinish(plan, queue, session.copy(finishedAt = 100), true))
        assertNull(Rotation.cursorOnFinish(plan, queue, session.copy(planDayId = null), false))
        assertNull(Rotation.cursorOnFinish(plan, listOf(20L), session, false))
        assertNull(Rotation.cursorOnFinish(plan.copy(scheduleMode = "WEEKDAY"), queue, session, false))
        assertEquals(0, Rotation.cursorAfterPlanEdit(2, 2))
        assertEquals(0, Rotation.cursorAfterPlanEdit(2, 0))
    }

    @Test fun supersetsRequireEqualCountsAndClearAfterEdit() {
        val paired = SupersetRules.setPairWithNext(listOf(item(0), item(1), item(2)), 0, true)
        assertEquals(paired[0].supersetGroup, paired[1].supersetGroup)
        assertNotNull(paired[0].supersetGroup)
        assertFalse(SupersetRules.canPairWithNext(paired, 1))
        val edited = SupersetRules.normalizeDaySupersets(listOf(paired[0].copy(sets = 4), paired[1]))
        assertTrue(edited.all { it.supersetGroup == null })
        assertFalse(SupersetRules.canPairWithNext(listOf(item(0), item(1, 4)), 0))
    }

    @Test fun warmupRampUsesRoundedLoadsBelowWorkingWeight() {
        assertEquals(listOf(50f, 70f, 85f), WarmupRamp.generate(100f).map { it.weightKg })
        assertEquals(listOf(8, 5, 3), WarmupRamp.generate(100f).map { it.reps })
        assertTrue(WarmupRamp.generate(20f).isEmpty())
        assertTrue(WarmupRamp.generate(0f).isEmpty())
        assertTrue(WarmupRamp.generate(Float.NaN).isEmpty())
        assertEquals(listOf(20f, 20f), WarmupRamp.generate(22.5f).map { it.weightKg })
    }

    @Test fun progressionModesRespectSafetyAndCanonicalKilograms() {
        val target = Progression.Target(3, 5, 5, null, 100f)
        val last = List(3) { Progression.PastSet(5, null, 100f, SetEffort.OK) }
        fun next(mode: ProgressionMode, deload: Boolean = false,
                 history: List<Progression.PastSet> = last) = Progression.next(
            ExerciseKind.WEIGHTED, target, history, emptyList(), WeightUnit.LB,
            false, deload, mode, lowerBody = true,
        )
        assertEquals(105f, next(ProgressionMode.LINEAR).sets[0].weightKg!!, 0.001f)
        assertEquals(102.5f, Progression.stepUpLinear(100f, WeightUnit.LB, false), 0.001f)
        assertEquals(100f, next(ProgressionMode.MAINTAIN).sets[0].weightKg!!, 0.001f)
        val deload = next(ProgressionMode.LINEAR, deload = true)
        assertEquals(2, deload.sets.size)
        assertEquals(100f, deload.sets[0].weightKg!!, 0.001f)
        val pain = next(ProgressionMode.MAINTAIN, history = last.map { it.copy(effort = SetEffort.PAIN) })
        assertTrue(pain.sets[0].weightKg!! < 100f)
    }

    @Test fun sharingRejectsUnknownExercisesAndAvoidsNameCollisions() {
        assertTrue(PlanShare.parseAndValidate("not json", emptyMap()) is PlanShare.ParseResult.Invalid)
        val unknown = """{"name":"PPL","days":[{"dayOfWeek":1,"title":"Push","items":[{"exerciseId":"missing"}]}]}"""
        assertTrue(PlanShare.parseAndValidate(unknown, emptyMap()) is PlanShare.ParseResult.Invalid)
        val custom = unknown.replace("\"exerciseId\":\"missing\"", "\"exerciseId\":\"missing\",\"name\":\"Custom lift\"")
        assertTrue(PlanShare.parseAndValidate(custom, emptyMap()) is PlanShare.ParseResult.Valid)
        assertTrue(PlanShare.parseAndValidate(custom.replace("\"name\":\"PPL\"", "\"name\":\"PPL\",\"format\":\"future-v2\""), emptyMap()) is PlanShare.ParseResult.Invalid)
        assertEquals("PPL (3)", PlanShare.safeImportName("PPL", listOf("PPL", "PPL (2)")))
    }

    @Test fun weeklyVolumeAndEligibilityExcludePreparationSets() {
        val ex = ExerciseEntity(id = "bench_press", name = "Bench", kind = ExerciseKind.WEIGHTED,
            primary = listOf(Muscle.CHEST), secondary = emptyList())
        val shared = PlanShare.SharedPlan(name = "Push", days = listOf(
            PlanShare.SharedDay(1, "Push", items = listOf(
                PlanShare.SharedItem("bench_press", sets = 3),
                PlanShare.SharedItem("bench_press", sets = 2, block = "Warm-up"),
            )),
        ))
        assertEquals(listOf(PlanShare.MuscleSets(Muscle.CHEST, 3)), PlanShare.weeklyMuscleVolume(shared, mapOf(ex.id to ex)))
        val working = SetEntity(sessionId = 1, exerciseId = ex.id, itemPosition = 0,
            block = "Main", circuit = false, setIndex = 0, targetLabel = "", done = true)
        for (type in listOf("WARMUP", "DROPSET", "RESTPAUSE")) {
            val excluded = working.copy(setType = type)
            assertFalse(countsForProgression(excluded))
            assertFalse(countsForScore(excluded))
            assertFalse(countsForPR(excluded))
        }
        assertTrue(countsForProgression(working.copy(setType = "FAILURE")))
        assertFalse(countsForProgression(working.copy(block = "Warm-up")))
    }
}
