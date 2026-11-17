package com.forgequest.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class EffortScaleTest {
    private fun sets(effort: SetEffort, n: Int = 3) = List(n) { Progression.PastSet(8, null, 60f, effort) }
    private val target = Progression.Target(3, 8, null, null, 60f)
    private fun next(last: List<Progression.PastSet>, previous: List<Progression.PastSet> = emptyList()) =
        Progression.next(ExerciseKind.WEIGHTED, target, last, previous, WeightUnit.KG, lowReadiness = false, warmup = false).sets[0].weightKg!!

    @Test
    fun oldStoredValuesStillRead() {
        listOf("EASY", "HARD", "PAIN", "FAILURE").forEach { assertEquals(it, SetEffort.parse(it).name) }
        assertEquals(SetEffort.OK, SetEffort.parse("OK"))
        assertEquals("HARD", SetEffort.chipOf("OK"))
        assertEquals("" to "WORKING", SetEffort.pick("OK", "WORKING", SetEffort.HARD))
        assertEquals(SetEffort.OK, SetEffort.parse("nonsense"))
        assertEquals(SetEffort.OK, SetEffort.parse(null))
        assertEquals(listOf(SetEffort.EASY, SetEffort.HARD, SetEffort.FAILURE), SetEffort.chips)
    }

    @Test
    fun allEasyAddsLoadMixedDoesNot() {
        assertEquals(62.5f, next(sets(SetEffort.EASY)), 0.01f)
        assertEquals(60f, next(sets(SetEffort.EASY, 2) + sets(SetEffort.HARD, 1)), 0.01f)
    }

    @Test
    fun failureHoldsTwiceInARowBacksOffPainAtOnce() {
        assertEquals(60f, next(sets(SetEffort.FAILURE), sets(SetEffort.HARD)), 0.01f)
        assertEquals(57.5f, next(sets(SetEffort.FAILURE), sets(SetEffort.FAILURE)), 0.01f)
        assertEquals(57.5f, next(sets(SetEffort.PAIN)), 0.01f)
    }

    @Test
    fun failureSetsTheSetTypeAndLeavingPutsItBack() {
        assertEquals("FAILURE" to "FAILURE", SetEffort.pick("", "WORKING", SetEffort.FAILURE))
        assertEquals("" to "WORKING", SetEffort.pick("FAILURE", "FAILURE", SetEffort.FAILURE))
        assertEquals("EASY" to "WORKING", SetEffort.pick("FAILURE", "FAILURE", SetEffort.EASY))
        assertEquals("FAILURE" to "DROPSET", SetEffort.pick("", "DROPSET", SetEffort.FAILURE))
        assertEquals("PAIN" to "WORKING", SetEffort.pick("HARD", "", SetEffort.PAIN))
    }
}
