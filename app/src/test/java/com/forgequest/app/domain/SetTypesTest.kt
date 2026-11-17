package com.forgequest.app.domain

import com.forgequest.app.data.db.SetEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class SetTypesTest {
    private fun set(type: String, block: String = "Main", autoTicked: Boolean = false) = SetEntity(
        sessionId = 1, exerciseId = "bench_press", itemPosition = 0, block = block, circuit = false, setIndex = 0,
        targetLabel = "", reps = 5, done = true, setType = type, autoTicked = autoTicked,
    )

    @Test
    fun countsAsWorkingSetKeepsRealSetsAndDropsWarmUps() {
        for (type in SetType.entries) assertEquals(type.name, type != SetType.WARMUP, countsAsWorkingSet(set(type.name)))
        assertEquals(true, countsAsWorkingSet(set(SetType.WORKING.name, autoTicked = true)))
        for (block in listOf("Warm-up", "Cool-down")) assertEquals(block, false, countsAsWorkingSet(set(SetType.DROPSET.name, block)))
    }
}
