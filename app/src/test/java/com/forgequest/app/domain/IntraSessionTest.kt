package com.forgequest.app.domain

import com.forgequest.app.data.db.SetEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IntraSessionTest {

    private fun set(
        id: Long,
        setIndex: Int = id.toInt() - 1,
        reps: Int? = 8,
        seconds: Int? = null,
        weightKg: Float? = 100f,
        done: Boolean = false,
        effort: String = "",
        setType: SetType = SetType.WORKING,
        block: String = "Main",
    ) = SetEntity(
        id = id,
        sessionId = 1L,
        exerciseId = "bench",
        itemPosition = 0,
        block = block,
        circuit = false,
        setIndex = setIndex,
        targetLabel = "8-12",
        reps = reps,
        seconds = seconds,
        weightKg = weightKg,
        done = done,
        setType = setType.name,
        effort = effort,
    )

    @Test
    fun set1FailureCutsSet2And3ByFivePercentSnappedToPlateStep() {
        val s1 = set(1L, setIndex = 0, reps = 8, weightKg = 100f, done = true, effort = SetEffort.FAILURE.name)
        val s2 = set(2L, setIndex = 1, reps = 8, weightKg = 100f)
        val s3 = set(3L, setIndex = 2, reps = 8, weightKg = 100f)

        val changed = IntraSession.adjust(s1, listOf(s2, s3), ExerciseKind.WEIGHTED, repsMin = 8, WeightUnit.KG)

        assertEquals(2, changed.size)
        assertEquals(95f, changed[0].weightKg!!, 0.01f)
        assertEquals(95f, changed[1].weightKg!!, 0.01f)
        assertEquals(8, changed[0].reps)
        assertEquals(8, changed[1].reps)
    }

    @Test
    fun missByThreeCutsTenPercent() {
        val s1 = set(1L, setIndex = 0, reps = 5, weightKg = 100f, done = true, effort = "")
        val s2 = set(2L, setIndex = 1, reps = 8, weightKg = 100f)

        val changed = IntraSession.adjust(s1, listOf(s2), ExerciseKind.WEIGHTED, repsMin = 8, WeightUnit.KG)

        assertEquals(1, changed.size)
        assertEquals(90f, changed[0].weightKg!!, 0.01f)
        assertEquals(8, changed[0].reps)
    }

    @Test
    fun twoRepsUnderMinimumTriggersWithoutTag() {
        val s1 = set(1L, setIndex = 0, reps = 6, weightKg = 100f, done = true, effort = "")
        val s2 = set(2L, setIndex = 1, reps = 8, weightKg = 100f)

        val changed = IntraSession.adjust(s1, listOf(s2), ExerciseKind.WEIGHTED, repsMin = 8, WeightUnit.KG)

        assertEquals(1, changed.size)
        assertEquals(95f, changed[0].weightKg!!, 0.01f)
        assertEquals(8, changed[0].reps)
    }

    @Test
    fun oneRepUnderMinimumDoesNothing() {
        val s1 = set(1L, setIndex = 0, reps = 7, weightKg = 100f, done = true, effort = "")
        val s2 = set(2L, setIndex = 1, reps = 8, weightKg = 100f)

        val changed = IntraSession.adjust(s1, listOf(s2), ExerciseKind.WEIGHTED, repsMin = 8, WeightUnit.KG)

        assertTrue(changed.isEmpty())
    }

    @Test
    fun blankEffortOnTargetDoesNothing() {
        val s1 = set(1L, setIndex = 0, reps = 8, weightKg = 100f, done = true, effort = "")
        val s2 = set(2L, setIndex = 1, reps = 8, weightKg = 100f)

        val changed = IntraSession.adjust(s1, listOf(s2), ExerciseKind.WEIGHTED, repsMin = 8, WeightUnit.KG)

        assertTrue(changed.isEmpty())
    }

    @Test
    fun hardTagDoesNothing() {
        val s1 = set(1L, setIndex = 0, reps = 8, weightKg = 100f, done = true, effort = SetEffort.HARD.name)
        val s2 = set(2L, setIndex = 1, reps = 8, weightKg = 100f)

        val changed = IntraSession.adjust(s1, listOf(s2), ExerciseKind.WEIGHTED, repsMin = 8, WeightUnit.KG)

        assertTrue(changed.isEmpty())
    }

    @Test
    fun lastSetFailureHasNothingToAdjust() {
        val s3 = set(3L, setIndex = 2, reps = 8, weightKg = 100f, done = true, effort = SetEffort.FAILURE.name)

        val changed = IntraSession.adjust(s3, emptyList(), ExerciseKind.WEIGHTED, repsMin = 8, WeightUnit.KG)

        assertTrue(changed.isEmpty())
    }

    @Test
    fun bodyweightFailureSetsRemainingRepsToRepsDone() {
        val s1 = set(1L, setIndex = 0, reps = 6, weightKg = null, done = true, effort = SetEffort.FAILURE.name)
        val s2 = set(2L, setIndex = 1, reps = 10, weightKg = null)
        val s3 = set(3L, setIndex = 2, reps = 10, weightKg = 10f)

        val changed = IntraSession.adjust(s1, listOf(s2, s3), ExerciseKind.REPS, repsMin = 8, WeightUnit.KG)

        assertEquals(2, changed.size)
        assertEquals(6, changed[0].reps)
        assertEquals(6, changed[1].reps)
        assertEquals(10f, changed[1].weightKg!!, 0.01f)
    }

    @Test
    fun holdFailureSetsRemainingSecondsToHeld() {
        val s1 = set(1L, setIndex = 0, reps = null, seconds = 24, weightKg = null, done = true, effort = SetEffort.FAILURE.name)
        val s2 = set(2L, setIndex = 1, reps = null, seconds = 30, weightKg = null)

        val changed = IntraSession.adjust(s1, listOf(s2), ExerciseKind.HOLD, repsMin = null, WeightUnit.KG)

        assertEquals(1, changed.size)
        assertEquals(24, changed[0].seconds)
    }

    @Test
    fun poundUserCutsInFivePoundSteps() {
        val wKg = WeightUnit.LB.toKg(100f)
        val s1 = set(1L, setIndex = 0, reps = 8, weightKg = wKg, done = true, effort = SetEffort.FAILURE.name)
        val s2 = set(2L, setIndex = 1, reps = 8, weightKg = wKg)

        val changed = IntraSession.adjust(s1, listOf(s2), ExerciseKind.WEIGHTED, repsMin = 8, WeightUnit.LB)

        assertEquals(1, changed.size)
        assertEquals(95f, WeightUnit.LB.fromKg(changed[0].weightKg!!), 0.05f)
    }

    @Test
    fun lightestLoadKeepsWeight() {
        val stepKg = WeightUnit.KG.loadStep
        val s1 = set(1L, setIndex = 0, reps = 6, weightKg = stepKg, done = true, effort = SetEffort.FAILURE.name)
        val s2 = set(2L, setIndex = 1, reps = 10, weightKg = stepKg)

        val changed = IntraSession.adjust(s1, listOf(s2), ExerciseKind.WEIGHTED, repsMin = 8, WeightUnit.KG)

        assertEquals(1, changed.size)
        assertEquals(stepKg, changed[0].weightKg!!, 0.01f)
        // The load cannot drop, so the next set aims for the 6 reps actually done, not the 8 that just failed.
        assertEquals(6, changed[0].reps)
    }

    @Test
    fun bodyweightFailureNeverRaisesTheNextTarget() {
        val s1 = set(1L, setIndex = 0, reps = 12, done = true, effort = SetEffort.FAILURE.name)
        val s2 = set(2L, setIndex = 1, reps = 10)
        assertTrue(IntraSession.adjust(s1, listOf(s2), ExerciseKind.REPS, repsMin = 8, WeightUnit.KG).isEmpty())
    }

    @Test
    fun holdFailurePastTheTargetNeverRaisesIt() {
        val s1 = set(1L, setIndex = 0, seconds = 40, done = true, effort = SetEffort.FAILURE.name)
        val s2 = set(2L, setIndex = 1, seconds = 30)
        assertTrue(IntraSession.adjust(s1, listOf(s2), ExerciseKind.HOLD, repsMin = null, WeightUnit.KG).isEmpty())
    }

    @Test
    fun warmupSourceIsIgnored() {
        val s1 = set(1L, setIndex = 0, reps = 4, weightKg = 60f, done = true, effort = SetEffort.FAILURE.name, setType = SetType.WARMUP)
        val s2 = set(2L, setIndex = 1, reps = 8, weightKg = 100f)

        val changed = IntraSession.adjust(s1, listOf(s2), ExerciseKind.WEIGHTED, repsMin = 8, WeightUnit.KG)

        assertTrue(changed.isEmpty())
    }

    @Test
    fun setTypeFailureWithBlankEffortTriggers() {
        val s1 = set(1L, setIndex = 0, reps = 8, weightKg = 100f, done = true, effort = "", setType = SetType.FAILURE)
        val s2 = set(2L, setIndex = 1, reps = 8, weightKg = 100f)

        val changed = IntraSession.adjust(s1, listOf(s2), ExerciseKind.WEIGHTED, repsMin = 8, WeightUnit.KG)

        assertEquals(1, changed.size)
        assertEquals(95f, changed[0].weightKg!!, 0.01f)
    }

    @Test
    fun extendedRestAddsFortyFiveCappedAt240() {
        assertEquals(135, IntraSession.extendedRest(90))
        assertEquals(240, IntraSession.extendedRest(210))
        assertEquals(240, IntraSession.extendedRest(240))
        assertEquals(300, IntraSession.extendedRest(300))
    }

    @Test
    fun gate17NeverWritesZeroRepsWhenRemainingSetHasZeroReps() {
        val weightedKeep = IntraSession.compute(
            set(1L, setIndex = 0, reps = 5, weightKg = 20f, done = true, effort = SetEffort.FAILURE.name),
            listOf(set(2L, setIndex = 1, reps = 0, weightKg = 20f)),
            ExerciseKind.WEIGHTED,
            repsMin = 8,
            WeightUnit.KG,
        )
        assertTrue((weightedKeep?.changed?.single()?.reps ?: 0) >= 1)

        val weightedDrop = IntraSession.compute(
            set(1L, setIndex = 0, reps = 5, weightKg = 100f, done = true, effort = SetEffort.FAILURE.name),
            listOf(set(2L, setIndex = 1, reps = 0, weightKg = 100f)),
            ExerciseKind.WEIGHTED,
            repsMin = 8,
            WeightUnit.KG,
        )
        assertTrue((weightedDrop?.changed?.single()?.reps ?: 0) >= 1)

        val bodyweight = IntraSession.compute(
            set(1L, setIndex = 0, reps = 5, weightKg = null, done = true, effort = SetEffort.FAILURE.name),
            listOf(set(2L, setIndex = 1, reps = 0, weightKg = null)),
            ExerciseKind.REPS,
            repsMin = 8,
            WeightUnit.KG,
        )
        assertTrue((bodyweight?.changed?.single()?.reps ?: 0) >= 1)
    }
}
