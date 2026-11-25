package com.forgequest.app.domain

import com.forgequest.app.data.db.ExerciseEntity
import com.forgequest.app.data.db.PlanItemEntity
import com.forgequest.app.data.db.SessionEntity
import com.forgequest.app.data.db.SetEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressionRirTest {

    private fun weightedTarget(
        sets: Int = 3,
        min: Int = 8,
        max: Int? = 12,
        kg: Float = 60f,
        mode: ProgressionMode = ProgressionMode.DOUBLE,
    ) = Progression.Target(sets, min, max, null, kg, mode)

    private fun bwTarget(sets: Int = 3, min: Int = 8, max: Int = 12) =
        Progression.Target(sets, min, max, null, null, ProgressionMode.DOUBLE)

    private fun past(
        n: Int,
        reps: Int?,
        kg: Float? = null,
        sec: Int? = null,
        effort: SetEffort = SetEffort.OK,
    ) = List(n) { Progression.PastSet(reps, sec, kg, effort) }

    private fun setEntity(
        sessionId: Long = 1L,
        exerciseId: String = "bench_press",
        setIndex: Int = 0,
        reps: Int? = 8,
        seconds: Int? = null,
        weightKg: Float? = 60f,
        effort: String = "",
        note: String = "",
        setType: String = SetType.WORKING.name,
        block: String = "Main",
    ) = SetEntity(
        sessionId = sessionId,
        exerciseId = exerciseId,
        itemPosition = 0,
        block = block,
        circuit = false,
        setIndex = setIndex,
        targetLabel = "",
        reps = reps,
        seconds = seconds,
        weightKg = weightKg,
        done = true,
        effort = effort,
        note = note,
        setType = setType,
    )

    @Test
    fun blankAtTopOfRangeLoadsUp() {
        val r = Progression.next(
            ExerciseKind.WEIGHTED,
            weightedTarget(),
            past(3, reps = 12, kg = 60f, effort = SetEffort.OK),
            emptyList(),
            WeightUnit.KG,
            warmup = false,
        )
        assertEquals(Progression.Reason.LOAD_UP, r.reason)
        assertEquals(listOf(8, 8, 8), r.sets.map { it.reps })
        assertEquals(62.5f, r.sets[0].weightKg!!, 0.01f)
    }

    @Test
    fun hardAtTopOfRangeLoadsUp() {
        val r = Progression.next(
            ExerciseKind.WEIGHTED,
            weightedTarget(),
            past(3, reps = 12, kg = 60f, effort = SetEffort.HARD),
            emptyList(),
            WeightUnit.KG,
            warmup = false,
        )
        assertEquals(Progression.Reason.LOAD_UP, r.reason)
        assertEquals(listOf(8, 8, 8), r.sets.map { it.reps })
        assertEquals(62.5f, r.sets[0].weightKg!!, 0.01f)

        // Holds also advance on HARD and block on FAILURE
        val holdTarget = Progression.Target(3, null, null, 30, null)
        val hardHold = Progression.next(
            ExerciseKind.HOLD,
            holdTarget,
            past(3, reps = null, sec = 30, effort = SetEffort.HARD),
            emptyList(),
            WeightUnit.KG,
            warmup = false,
        )
        assertEquals(Progression.Reason.MORE_TIME, hardHold.reason)
        assertEquals(35, hardHold.sets[0].seconds)

        val failHold = Progression.next(
            ExerciseKind.HOLD,
            holdTarget,
            past(3, reps = null, sec = 30, effort = SetEffort.FAILURE),
            emptyList(),
            WeightUnit.KG,
            warmup = false,
        )
        assertEquals(Progression.Reason.HARD, failHold.reason)
        assertEquals(30, failHold.sets[0].seconds)
    }

    @Test
    fun failureAtTargetHoldsWithHardReason() {
        val r = Progression.next(
            ExerciseKind.WEIGHTED,
            weightedTarget(),
            past(2, reps = 12, kg = 60f, effort = SetEffort.OK) + past(1, reps = 12, kg = 60f, effort = SetEffort.FAILURE),
            emptyList(),
            WeightUnit.KG,
            warmup = false,
        )
        assertEquals(Progression.Reason.HARD, r.reason)
        assertEquals(listOf(12, 12, 12), r.sets.map { it.reps })
        assertEquals(60f, r.sets[0].weightKg!!, 0.01f)

        // A11: setType == FAILURE with blank effort reads as FAILURE in Progression.inputs
        val item = PlanItemEntity(dayId = 1, position = 0, exerciseId = "bench_press", block = "Main", circuit = false, sets = 3, repsMin = 8, repsMax = 12, weightKg = 60f)
        val recent = listOf(
            setEntity(setIndex = 0, reps = 12, weightKg = 60f),
            setEntity(setIndex = 1, reps = 12, weightKg = 60f),
            setEntity(setIndex = 2, reps = 12, weightKg = 60f, effort = "", setType = SetType.FAILURE.name),
        )
        val inputs = Progression.inputs(item, "bench_press", recent)
        assertEquals(SetEffort.FAILURE, inputs.last.last().effort)
    }

    @Test
    fun missedRepsKeepMetSetsAndRestartTheMissedOneAtTheMinimum() {
        val last = listOf(12, 12, 6).map { Progression.PastSet(it, null, 60f, SetEffort.OK) }
        val r = Progression.next(ExerciseKind.WEIGHTED, weightedTarget(), last, emptyList(), WeightUnit.KG, warmup = false)
        assertEquals(Progression.Reason.MISSED, r.reason)
        assertEquals(listOf(12, 12, 8), r.sets.map { it.reps })
        assertEquals(listOf(60f, 60f, 60f), r.sets.map { it.weightKg })
    }

    @Test
    fun missedRepsUntaggedHoldsAtMinimumNotPlusOne() {
        val r = Progression.next(
            ExerciseKind.WEIGHTED,
            weightedTarget(),
            past(3, reps = 6, kg = 60f, effort = SetEffort.OK),
            emptyList(),
            WeightUnit.KG,
            warmup = false,
        )
        assertEquals(Progression.Reason.MISSED, r.reason)
        assertEquals(listOf(8, 8, 8), r.sets.map { it.reps })
        assertEquals(60f, r.sets[0].weightKg!!, 0.01f)
    }

    @Test
    fun missedAtFailureHoldsWithFatigue() {
        val r = Progression.next(
            ExerciseKind.WEIGHTED,
            weightedTarget(),
            past(2, reps = 8, kg = 60f, effort = SetEffort.OK) + past(1, reps = 6, kg = 60f, effort = SetEffort.FAILURE),
            emptyList(),
            WeightUnit.KG,
            warmup = false,
        )
        assertEquals(Progression.Reason.FATIGUE, r.reason)
        assertEquals(listOf(8, 8, 8), r.sets.map { it.reps })
        assertEquals(60f, r.sets[0].weightKg!!, 0.01f)

        // Deload overrides FATIGUE per A6 precedence
        val deloaded = Progression.next(
            ExerciseKind.WEIGHTED,
            weightedTarget(),
            past(3, reps = 6, kg = 60f, effort = SetEffort.FAILURE),
            emptyList(),
            WeightUnit.KG,
            warmup = false,
            deload = true,
        )
        assertEquals(Progression.Reason.SAME, deloaded.reason)
        assertEquals(2, deloaded.sets.size)
        assertEquals(60f, deloaded.sets[0].weightKg!!, 0.01f)
    }

    @Test
    fun missedAtFailureTwiceStepsDownBackOff() {
        val missedFail = past(2, reps = 8, kg = 60f, effort = SetEffort.OK) + past(1, reps = 6, kg = 60f, effort = SetEffort.FAILURE)
        val r = Progression.next(
            ExerciseKind.WEIGHTED,
            weightedTarget(),
            missedFail,
            missedFail,
            WeightUnit.KG,
            warmup = false,
        )
        assertEquals(Progression.Reason.BACK_OFF, r.reason)
        assertEquals(57.5f, r.sets[0].weightKg!!, 0.01f)
        assertEquals(listOf(8, 8, 8), r.sets.map { it.reps })

        // BACK_OFF takes precedence over deload per A6
        val deloadBackOff = Progression.next(
            ExerciseKind.WEIGHTED,
            weightedTarget(),
            missedFail,
            missedFail,
            WeightUnit.KG,
            warmup = false,
            deload = true,
        )
        assertEquals(Progression.Reason.BACK_OFF, deloadBackOff.reason)
        assertEquals(57.5f, deloadBackOff.sets[0].weightKg!!, 0.01f)
    }

    @Test
    fun bodyweightMissedRepsHoldsAtRepsDone() {
        val r = Progression.next(
            ExerciseKind.REPS,
            bwTarget(),
            listOf(
                Progression.PastSet(8, null, null, SetEffort.OK),
                Progression.PastSet(7, null, null, SetEffort.OK),
                Progression.PastSet(6, null, null, SetEffort.OK),
            ),
            emptyList(),
            WeightUnit.KG,
            warmup = false,
        )
        assertEquals(Progression.Reason.MISSED, r.reason)
        // A missed session: every set at min(minimum, best set) so a cut set does not stay cut (audit S2).
        assertEquals(listOf(8, 8, 8), r.sets.map { it.reps })
    }

    @Test
    fun bodyweightFailureHolds() {
        val r = Progression.next(
            ExerciseKind.REPS,
            bwTarget(),
            past(3, reps = 10, effort = SetEffort.FAILURE),
            emptyList(),
            WeightUnit.KG,
            warmup = false,
        )
        assertEquals(Progression.Reason.HARD, r.reason)
        assertEquals(listOf(10, 10, 10), r.sets.map { it.reps })
    }

    @Test
    fun linearInPoundsStepsFiveAndTen() {
        val unit = WeightUnit.LB
        val startKg = unit.toKg(135f)
        val upper = Progression.next(
            ExerciseKind.WEIGHTED,
            weightedTarget(sets = 3, min = 5, max = 5, kg = startKg, mode = ProgressionMode.LINEAR),
            past(3, reps = 5, kg = startKg, effort = SetEffort.HARD),
            emptyList(),
            unit,
            warmup = false,
            lowerBody = false,
        )
        assertEquals(Progression.Reason.LOAD_UP, upper.reason)
        assertEquals(140f, unit.fromKg(upper.sets[0].weightKg!!), 0.05f)

        val lower = Progression.next(
            ExerciseKind.WEIGHTED,
            weightedTarget(sets = 3, min = 5, max = 5, kg = startKg, mode = ProgressionMode.LINEAR),
            past(3, reps = 5, kg = startKg, effort = SetEffort.OK),
            emptyList(),
            unit,
            warmup = false,
            lowerBody = true,
        )
        assertEquals(Progression.Reason.LOAD_UP, lower.reason)
        assertEquals(145f, unit.fromKg(lower.sets[0].weightKg!!), 0.05f)
    }

    @Test
    fun legacyOkChipShowsNothing() {
        assertEquals("", SetEffort.chipOf("OK"))
        assertEquals("", SetEffort.chipOf(""))
        assertEquals("HARD", SetEffort.chipOf("HARD"))
        assertEquals("EASY", SetEffort.chipOf("EASY"))
        assertEquals("FAILURE", SetEffort.chipOf("FAILURE"))
    }

    @Test
    fun rirMapping() {
        assertEquals(3, SetEffort.rir(SetEffort.EASY))
        assertEquals(2, SetEffort.rir(SetEffort.OK))
        assertEquals(1, SetEffort.rir(SetEffort.HARD))
        assertEquals(0, SetEffort.rir(SetEffort.FAILURE))
        assertEquals(2, SetEffort.rir(SetEffort.PAIN))
    }

    @Test
    fun recoveryFailureCostsMoreThanHard() {
        val ex = ExerciseEntity("bench", "Bench", ExerciseKind.WEIGHTED, listOf(Muscle.CHEST), emptyList())
        val hard = Recovery.effectiveSetEffort(setEntity(reps = 6, weightKg = 60f, effort = "HARD"), ex)
        val failure = Recovery.effectiveSetEffort(setEntity(reps = 6, weightKg = 60f, effort = "FAILURE"), ex)
        val pain = Recovery.effectiveSetEffort(setEntity(reps = 6, weightKg = 60f, effort = "PAIN"), ex)
        val failureFromType = Recovery.effectiveSetEffort(setEntity(reps = 6, weightKg = 60f, effort = "", setType = SetType.FAILURE.name), ex)
        assertEquals(1.05f, hard, 0.001f)
        assertEquals(1.25f, failure, 0.001f)
        assertEquals(1.15f, pain, 0.001f)
        assertEquals(1.25f, failureFromType, 0.001f)
        assertTrue(failure > hard)

        // Section 6: WorkoutScore reads pain from note via SetEffort.of
        val notedPain = setEntity(reps = 6, weightKg = 60f, effort = "", note = "sharp shoulder pain")
        val scoreResult = WorkoutScore.compute(
            listOf(WorkoutScore.Work("bench", ExerciseKind.WEIGHTED, listOf(notedPain), listOf(setEntity(reps = 10, weightKg = 60f)))),
            plannedSets = 3,
        )
        assertTrue(scoreResult.rough)
    }

    @Test
    fun readyForHarderIgnoresHardButNotFailure() {
        val hard = past(3, reps = 15, effort = SetEffort.HARD)
        val ok = past(3, reps = 15, effort = SetEffort.OK)
        val fail = past(3, reps = 15, effort = SetEffort.FAILURE)
        assertTrue(ExerciseTiers.readyForHarder(hard, ok))
        assertFalse(ExerciseTiers.readyForHarder(fail, ok))
    }

    @Test
    fun debriefNextTimeMatchesLivePrefillInDeloadWeek() {
        val squatItem = PlanItemEntity(
            dayId = 10,
            position = 0,
            exerciseId = "back_squat",
            block = "Main",
            circuit = false,
            sets = 3,
            repsMin = 8,
            repsMax = 12,
            weightKg = 100f,
            progressionMode = ProgressionMode.LINEAR.name,
        )
        val squatEx = ExerciseEntity("back_squat", "Back Squat", ExerciseKind.WEIGHTED, listOf(Muscle.QUADS), emptyList())
        val sessions = listOf(
            SessionEntity(id = 1, planDayId = 10, title = "Legs", epochDay = 100, startedAt = 100_000, finishedAt = 100_001),
        )
        val doneSets = (0..2).map { i ->
            setEntity(sessionId = 1, exerciseId = "back_squat", setIndex = i, reps = 12, weightKg = 100f)
        }
        val lib = mapOf("back_squat" to squatEx)
        val scores = WorkoutScore.forSessions(sessions, doneSets, mapOf("back_squat" to ExerciseKind.WEIGHTED), mapOf(10L to 3))

        // During a deload week: live prefill holds weight; debrief must NOT suggest LoadUp.
        val deloadDebrief = WorkoutDebriefMath.forSession(
            session = sessions[0],
            sessions = sessions,
            doneSets = doneSets,
            exercises = lib,
            score = scores.getValue(1L),
            planItems = listOf(squatItem),
            weeklyTarget = 3,
            report = null,
            unit = WeightUnit.KG,
            equipment = emptySet(),
            deload = true,
        )
        assertEquals(emptyList<WorkoutDebriefMath.Next>(), deloadDebrief.next)

        // Outside deload, LINEAR lower-body steps +5 kg (105 kg), matching live prefill.
        val normalDebrief = WorkoutDebriefMath.forSession(
            session = sessions[0],
            sessions = sessions,
            doneSets = doneSets,
            exercises = lib,
            score = scores.getValue(1L),
            planItems = listOf(squatItem),
            weeklyTarget = 3,
            report = null,
            unit = WeightUnit.KG,
            equipment = emptySet(),
            deload = false,
        )
        assertEquals(listOf(WorkoutDebriefMath.Next.LoadUp("back_squat", 105f, 8)), normalDebrief.next)
    }
}
