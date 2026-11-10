package com.forgequest.app.domain

import com.forgequest.app.data.db.SetEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class PrPickerTest {
    private var nextId = 1L
    private fun set(reps: Int, id: String = "ex", done: Boolean = true, block: String = "Main") = SetEntity(
        id = nextId++, sessionId = 1, exerciseId = id, itemPosition = 0, block = block, circuit = false, setIndex = 0,
        targetLabel = "", reps = reps, done = done,
    )

    private fun pick(sets: List<SetEntity>, best: Map<String, Float>) = PrPicker.pick(sets, { ExerciseKind.REPS }, best)

    @Test
    fun onlyTheHighestSetAboveTheBestIsFlagged() {
        val a = set(11); val b = set(13); val c = set(12)
        assertEquals(setOf(b.id), pick(listOf(a, b, c), mapOf("ex" to 10f)))
    }

    @Test
    fun theEarliestSetWinsATie() {
        val a = set(12); val b = set(12)
        assertEquals(setOf(a.id), pick(listOf(a, b), mapOf("ex" to 10f)))
    }

    @Test
    fun nothingAboveTheBestOrOnAFirstSession() {
        val a = set(10); val b = set(9)
        assertEquals(emptySet<Long>(), pick(listOf(a, b), mapOf("ex" to 10f)))
        assertEquals(emptySet<Long>(), pick(listOf(a, b), emptyMap()))
    }

    @Test
    fun untickedAndExtraSetsAreSkipped() {
        val unticked = set(20, done = false); val warm = set(20, block = "Warm-up"); val cool = set(20, block = "Cool-down")
        val real = set(12)
        assertEquals(setOf(real.id), pick(listOf(unticked, warm, cool, real), mapOf("ex" to 10f)))
    }

    @Test
    fun eachExerciseGetsItsOwnRecord() {
        val a = set(12, "a"); val b = set(12, "b")
        assertEquals(setOf(a.id, b.id), pick(listOf(a, b), mapOf("a" to 10f, "b" to 10f)))
    }
}
