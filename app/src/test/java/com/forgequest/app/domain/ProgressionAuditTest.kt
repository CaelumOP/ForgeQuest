package com.forgequest.app.domain

import com.forgequest.app.data.db.SetEntity
import com.forgequest.app.data.seed.ExerciseCatalog
import org.junit.Assert.*
import org.junit.Test

class ProgressionAuditTest {
    private val weighted = Progression.Target(3, 8, 12, null, 100f)
    private val bodyweight = weighted.copy(weightKg = null)
    private val hold = Progression.Target(3, null, null, 30, null)

    private fun past(reps: Int = 10, weight: Float? = null, seconds: Int? = null,
                     effort: SetEffort = SetEffort.OK) = Progression.PastSet(reps, seconds, weight, effort)

    private fun next(kind: ExerciseKind, target: Progression.Target, last: List<Progression.PastSet>,
                     previous: List<Progression.PastSet> = emptyList()) =
        Progression.next(kind, target, last, previous, WeightUnit.KG, warmup = false)

    private fun holds(vararg seconds: Int, effort: SetEffort = SetEffort.OK) =
        seconds.map { past(seconds = it, effort = effort) }

    private fun assertSeconds(expected: Int, result: Progression.Result) {
        assertEquals(listOf(expected, expected, expected), result.sets.map { it.seconds })
    }

    @Test fun cutSetRestartsAtMinimumAtTheSessionTopWeight() {
        val result = next(ExerciseKind.WEIGHTED, weighted,
            listOf(past(10, 100f), past(10, 100f), past(8, 90f)))
        assertEquals(listOf(100f, 100f, 100f), result.sets.map { it.weightKg })
        assertEquals(listOf(11, 11, 8), result.sets.map { it.reps })
    }

    @Test fun cutSetBelowMinimumDoesNotBlockProgressAtTopWeight() {
        val result = next(ExerciseKind.WEIGHTED, weighted,
            listOf(past(10, 100f), past(10, 100f), past(5, 90f)))
        assertEquals(listOf(11, 11), result.sets.take(2).map { it.reps })
        assertEquals(Progression.Reason.MORE_REPS, result.reason)
    }

    @Test fun missedTopWeightSetBlocksProgress() {
        val result = next(ExerciseKind.WEIGHTED, weighted,
            listOf(past(7, 100f), past(10, 100f), past(12, 90f)))
        assertEquals(listOf(8, 10, 8), result.sets.map { it.reps })
        assertEquals(Progression.Reason.MISSED, result.reason)
    }

    @Test fun missedBodyweightSessionUsesBestRepsCappedAtMinimum() {
        for ((reps, expected) in listOf(listOf(5, 3, 2) to 5, listOf(10, 6, 4) to 8)) {
            val result = next(ExerciseKind.REPS, bodyweight, reps.map { past(it) })
            assertEquals(List(3) { expected }, result.sets.map { it.reps })
        }
    }

    @Test fun bodyweightNeverPrescribesBelowOneRep() {
        for (reps in listOf(0, -1)) {
            val result = next(ExerciseKind.REPS, bodyweight, List(3) { past(reps) })
            assertEquals(listOf(1, 1, 1), result.sets.map { it.reps })
        }
    }

    @Test fun twoMissedBodyweightSessionsSuggestEasierBeforeFailure() {
        val missed = List(3) { past(5, effort = SetEffort.FAILURE) }
        assertEquals(Progression.Reason.EASIER,
            next(ExerciseKind.REPS, bodyweight, missed, missed).reason)
    }

    @Test fun oneMissedBodyweightSessionDoesNotSuggestEasier() {
        val result = next(ExerciseKind.REPS, bodyweight, List(3) { past(5) })
        assertEquals(Progression.Reason.MISSED, result.reason)
    }

    @Test fun missedHoldRoundsBestDownThenAddsFive() {
        assertSeconds(25, next(ExerciseKind.HOLD, hold, holds(12, 23, 17)))
    }

    @Test fun missedHoldWithoutFailureCapsAtTarget() {
        assertSeconds(30, next(ExerciseKind.HOLD, hold, holds(27, 29, 26)))
    }

    @Test fun missedHoldAtFailureKeepsRoundedBest() {
        assertSeconds(20, next(ExerciseKind.HOLD, hold, holds(12, 23, 17, effort = SetEffort.FAILURE)))
    }

    @Test fun failedHoldNeverPrescribesBelowFiveSeconds() {
        assertSeconds(5, next(ExerciseKind.HOLD, hold, holds(0, 2, 4, effort = SetEffort.FAILURE)))
    }

    @Test fun allHoldsMetAdvanceInFiveSecondStepsToCeiling() {
        assertSeconds(35, next(ExerciseKind.HOLD, hold, holds(30, 30, 30)))
        assertSeconds(45, next(ExerciseKind.HOLD, hold, holds(40, 40, 40)))
        val ceiling = next(ExerciseKind.HOLD, hold, holds(45, 45, 45))
        assertSeconds(45, ceiling)
        assertTrue(ceiling.holdCeilingReached)
    }

    @Test fun oneHoldBelowTargetBlocksProgressDespiteBestMeetingTarget() {
        val result = next(ExerciseKind.HOLD, hold, holds(30, 20, 30))
        assertSeconds(30, result)
        assertEquals(Progression.Reason.MISSED, result.reason)
    }

    @Test fun twoMissedHoldSessionsSuggestEasierButEmptyPreviousDoesNot() {
        val missed = holds(15, 20, 25, effort = SetEffort.FAILURE)
        assertEquals(Progression.Reason.EASIER, next(ExerciseKind.HOLD, hold, missed, missed).reason)
        assertNotEquals(Progression.Reason.EASIER, next(ExerciseKind.HOLD, hold, missed).reason)
    }

    @Test fun twoUntaggedLargeMissesAtSameTopWeightBackOff() {
        val missed = listOf(past(10, 100f), past(6, 100f), past(8, 90f))
        val result = next(ExerciseKind.WEIGHTED, weighted, missed, missed)
        assertEquals(Progression.Reason.BACK_OFF, result.reason)
        assertEquals(listOf(97.5f, 97.5f, 97.5f), result.sets.map { it.weightKg })
        assertEquals(listOf(8, 8, 8), result.sets.map { it.reps })
    }

    @Test fun largeMissesAtDifferentTopWeightsDoNotBackOff() {
        val last = List(3) { past(6, 100f) }
        val previous = List(3) { past(6, 95f) }
        val result = next(ExerciseKind.WEIGHTED, weighted, last, previous)
        assertNotEquals(Progression.Reason.BACK_OFF, result.reason)
        assertEquals(listOf(100f, 100f, 100f), result.sets.map { it.weightKg })
    }

    @Test fun oversizedLoadStepKeepsWeightAndReportsMissed() {
        val missed = List(3) { past(5, 10f, effort = SetEffort.FAILURE) }
        val result = next(ExerciseKind.WEIGHTED, weighted.copy(weightKg = 10f), missed, missed)
        assertEquals(Progression.Reason.MISSED, result.reason)
        assertEquals(listOf(10f, 10f, 10f), result.sets.map { it.weightKg })
        assertEquals(listOf(8, 8, 8), result.sets.map { it.reps })
    }

    private fun row(id: Long, reps: Int, done: Boolean = false, effort: String = "") = SetEntity(
        id = id, sessionId = 1L, exerciseId = "db_bench_press", itemPosition = 0,
        block = "Main", circuit = false, setIndex = id.toInt() - 1, targetLabel = "8-12",
        reps = reps, weightKg = 10f, done = done, effort = effort, setType = SetType.WORKING.name,
    )

    @Test fun liveFailureAtLightLoadKeepsWeightAndCapsRepsAtFive() {
        val result = IntraSession.compute(row(1, 5, true, SetEffort.FAILURE.name),
            listOf(row(2, 8), row(3, 4)), ExerciseKind.WEIGHTED, 8, WeightUnit.KG)!!
        assertNull(result.percent)
        assertEquals(5, result.reps)
        assertEquals(listOf(row(2, 5)), result.changed)
    }

    @Test fun zeroRepLiveFailurePrescribesAtLeastOneRep() {
        val result = IntraSession.compute(row(1, 0, true, SetEffort.FAILURE.name),
            listOf(row(2, 8), row(3, 8)), ExerciseKind.WEIGHTED, 8, WeightUnit.KG)!!
        assertEquals(listOf(1, 1), result.changed.map { it.reps })
        assertEquals(listOf(10f, 10f), result.changed.map { it.weightKg })
    }

    @Test fun baselineTransferUsesMovementSpecificFactors() {
        assertEquals(0.9f, Baseline.transfer(MovementPattern.HORIZONTAL_PUSH, MovementPattern.HORIZONTAL_PUSH), 0.0001f)
        assertEquals(0.54f, Baseline.transfer(MovementPattern.HORIZONTAL_PUSH, MovementPattern.VERTICAL_PUSH), 0.0001f)
        assertEquals(0.54f, Baseline.transfer(MovementPattern.SQUAT_BILATERAL, MovementPattern.SQUAT_UNILATERAL), 0.0001f)
        assertEquals(0.72f, Baseline.transfer(MovementPattern.HORIZONTAL_PULL, MovementPattern.VERTICAL_PULL), 0.0001f)
    }

    @Test fun flyTargetReceivesNoBaselineFromPress() {
        val library = ExerciseCatalog.all.associateBy { it.id }
        val donor = row(1, 8, true).copy(weightKg = 40f)
        val result = Baseline.coldStart(library.getValue("db_fly"), 8, WeightUnit.KG,
            mapOf(donor.exerciseId to listOf(donor)), library)
        assertEquals(BaselineStatus.NONE, result.status)
        assertNull(result.weightKg)
        assertNull(result.donorId)
    }

    @Test fun topRepsTwelveRequiresFourteenInBothSessions() {
        val ready = List(3) { past(14) }
        val below = List(3) { past(13) }
        assertTrue(ExerciseTiers.readyForHarder(ready, ready, topReps = 12))
        assertFalse(ExerciseTiers.readyForHarder(below, below, topReps = 12))
        assertFalse(ExerciseTiers.readyForHarder(ready, below, topReps = 12))
    }

    @Test fun absentOrHighTopRepsRequiresFifteen() {
        val ready = List(3) { past(15) }
        val below = List(3) { past(14) }
        assertTrue(ExerciseTiers.readyForHarder(ready, ready))
        assertFalse(ExerciseTiers.readyForHarder(below, below))
        assertTrue(ExerciseTiers.readyForHarder(ready, ready, topReps = 20))
        assertFalse(ExerciseTiers.readyForHarder(below, below, topReps = 20))
    }
}
