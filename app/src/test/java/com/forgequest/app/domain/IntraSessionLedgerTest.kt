package com.forgequest.app.domain

import com.forgequest.app.data.db.SetEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Live-workout fixes ported from the web (WEB_SET_OVERHAUL_REPORT "W2 review fixes" 2, 3, 5, 10, 11). */
class IntraSessionLedgerTest {

    private fun set(
        id: Long,
        reps: Int? = 10,
        weightKg: Float? = null,
        done: Boolean = false,
        effort: String = "",
        setType: SetType = SetType.WORKING,
    ) = SetEntity(
        id = id,
        sessionId = 1L,
        exerciseId = "pushup",
        itemPosition = 0,
        block = "Main",
        circuit = false,
        setIndex = id.toInt() - 1,
        targetLabel = "10",
        reps = reps,
        weightKg = weightKg,
        done = done,
        doneAt = if (done) 100L else null,
        setType = setType.name,
        effort = effort,
    )

    private val notEdited: (Long) -> Boolean = { false }

    // Fix 5: untick of a Failure-tagged set.

    @Test
    fun untickResetsFailureGivenTypeSoAReTickNoLongerTriggers() {
        val tagged = set(1, reps = 8, done = true, effort = SetEffort.FAILURE.name, setType = SetType.FAILURE)
        assertTrue(IntraSession.triggers(tagged, ExerciseKind.REPS, 8))

        val unticked = IntraSession.unticked(tagged)
        assertFalse(unticked.done)
        assertNull(unticked.doneAt)
        assertEquals("", unticked.effort)
        assertEquals(SetType.WORKING.name, unticked.setType)
        // Re-ticked without a tag: A11 must not read it as Failure any more.
        assertFalse(IntraSession.triggers(unticked.copy(done = true), ExerciseKind.REPS, 8))
    }

    @Test
    fun untickKeepsAHandChosenFailureType() {
        // Type picked by hand with no Failure chip: A11 still applies, untick leaves the type alone.
        val chosen = set(1, done = true, effort = "", setType = SetType.FAILURE)
        assertEquals(SetType.FAILURE.name, IntraSession.unticked(chosen).setType)
        val hard = set(1, done = true, effort = SetEffort.HARD.name, setType = SetType.FAILURE)
        assertEquals(SetType.FAILURE.name, IntraSession.unticked(hard).setType)
        assertEquals("", IntraSession.unticked(hard).effort)
    }

    // Fixes 2, 10, 11: Failure after carry-forward.

    @Test
    fun failureAfterCarriedRaiseRestoresPreCarryTargetsSilently() {
        // Did 12 of 10, ticked (carry-forward wrote 12), then Failure.
        val source = set(1, reps = 12, done = true, effort = SetEffort.FAILURE.name, setType = SetType.FAILURE)
        val preCarry = listOf(set(2, reps = 10), set(3, reps = 10))
        val current = listOf(set(2, reps = 12), set(3, reps = 12))

        val plan = IntraSession.plan(source, preCarry, current, ExerciseKind.REPS, 10, WeightUnit.KG, notEdited)

        assertNotNull(plan)
        assertNull("no cut against the plan: no cue, nothing for Undo", plan!!.cut)
        assertTrue(plan.before.isEmpty())
        assertEquals(listOf(10, 10), plan.toWrite.map { it.reps })
        assertEquals(listOf(2L, 3L), plan.toWrite.map { it.id })
    }

    @Test
    fun realCutKeepsCueAndUndoWhenCarryForwardAlreadyWroteTheSameNumbers() {
        // Did 6 of 10, ticked (carry-forward wrote 6), then Failure: the cut to 6 is already there.
        val source = set(1, reps = 6, done = true, effort = SetEffort.FAILURE.name, setType = SetType.FAILURE)
        val preCarry = listOf(set(2, reps = 10), set(3, reps = 10))
        val current = listOf(set(2, reps = 6), set(3, reps = 6))

        val plan = IntraSession.plan(source, preCarry, current, ExerciseKind.REPS, 10, WeightUnit.KG, notEdited)!!

        assertEquals(6, plan.cut?.reps)
        assertTrue("nothing left to write", plan.toWrite.isEmpty())
        assertEquals(listOf(10, 10), plan.before.map { it.reps })
        // Undo / untick through the ledger puts the 10s back.
        val ledger = IntraSession.Ledger()
        ledger.record(source.id, plan.before)
        val restored = IntraSession.numbersToRestore(ledger.release(source.id), current, notEdited)
        assertEquals(listOf(10, 10), restored.map { it.reps })
    }

    @Test
    fun weightedCutWritesAgainstCurrentRows() {
        val source = set(1, reps = 6, weightKg = 100f, done = true, effort = SetEffort.FAILURE.name, setType = SetType.FAILURE)
        val preCarry = listOf(set(2, reps = 8, weightKg = 100f))
        val current = listOf(set(2, reps = 6, weightKg = 100f))

        val plan = IntraSession.plan(source, preCarry, current, ExerciseKind.WEIGHTED, 8, WeightUnit.KG, notEdited)!!

        assertEquals(5, plan.cut?.percent)
        assertEquals(1, plan.toWrite.size)
        assertEquals(95f, plan.toWrite[0].weightKg!!, 0.01f)
        assertEquals(8, plan.toWrite[0].reps)
        assertEquals(listOf(100f), plan.before.map { it.weightKg })
    }

    @Test
    fun planIsNullWithoutTriggerAndSkipsRowsTickedOrEditedSinceTheTick() {
        val untagged = set(1, reps = 10, done = true)
        val pre = listOf(set(2), set(3))
        assertNull(IntraSession.plan(untagged, pre, pre, ExerciseKind.REPS, 10, WeightUnit.KG, notEdited))

        val source = set(1, reps = 6, done = true, effort = SetEffort.FAILURE.name, setType = SetType.FAILURE)
        val current = listOf(set(2, done = true), set(3))
        val plan = IntraSession.plan(source, pre, current, ExerciseKind.REPS, 10, WeightUnit.KG) { it == 3L }!!
        assertNull("both later sets were ticked or edited since: no phantom cue", plan.cut)
        assertTrue(plan.toWrite.isEmpty())
    }

    // Fix 3: per-row owner stack.

    private fun w(id: Long, kg: Float) = set(id, reps = 8, weightKg = kg)

    @Test
    fun releasingAnOlderAdjustmentLeavesRowsANewerOneRewrote() {
        val ledger = IntraSession.Ledger()
        ledger.record(1, listOf(w(2, 100f), w(3, 100f))) // set 1 Failure: 2-3 to 95
        ledger.record(2, listOf(w(3, 95f))) // set 2 Failure: 3 to 90
        assertEquals(listOf(w(2, 100f)), ledger.release(1))
        // Set 2's adjustment now goes back to the numbers from before set 1's.
        assertEquals(listOf(w(3, 100f)), ledger.release(2))
        assertTrue(ledger.isEmpty)
    }

    @Test
    fun releasingNewestFirstRestoresEachStepInTurn() {
        val ledger = IntraSession.Ledger()
        ledger.record(1, listOf(w(2, 100f), w(3, 100f)))
        ledger.record(2, listOf(w(3, 95f)))
        assertEquals(listOf(w(3, 95f)), ledger.release(2))
        assertEquals(listOf(w(2, 100f), w(3, 100f)), ledger.release(1))
        assertTrue(ledger.isEmpty)
    }

    @Test
    fun releasingAnUnknownOrAlreadyReleasedSourceReturnsNothing() {
        val ledger = IntraSession.Ledger()
        ledger.record(1, listOf(w(2, 100f)))
        assertEquals(1, ledger.release(1).size)
        assertTrue(ledger.release(1).isEmpty())
        assertTrue(ledger.release(9).isEmpty())
    }

    @Test
    fun numbersToRestoreSkipsTickedAndEditedRows() {
        val values = listOf(w(2, 100f), w(3, 100f), w(4, 100f))
        val current = listOf(w(2, 95f).copy(done = true), w(3, 95f), w(4, 95f))
        val out = IntraSession.numbersToRestore(values, current) { it == 4L }
        assertEquals(listOf(3L), out.map { it.id })
        assertEquals(100f, out[0].weightKg!!, 0.01f)
    }
}
