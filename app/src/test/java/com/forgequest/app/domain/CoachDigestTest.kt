package com.forgequest.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoachDigestTest {
    private fun r(f: Float, h: Int) = MuscleRecovery(f, h)

    @Test fun splitsFreshFromRecoveringAndCaps() {
        val map = mapOf(
            Muscle.CHEST to r(0f, 0), Muscle.BICEPS to r(0f, 0), Muscle.SHOULDERS to r(0f, 0), Muscle.TRICEPS to r(0f, 0),
            Muscle.QUADS to r(1f, 40), Muscle.GLUTES to r(0.5f, 10), Muscle.CALVES to r(0.25f, 5), Muscle.ABS to r(0.7f, 20),
        )
        val (fresh, rec) = recoverySplit(map)
        assertEquals(3, fresh.size)
        assertEquals(listOf(Muscle.CALVES, Muscle.GLUTES, Muscle.ABS), rec)
    }

    @Test fun emptyMapGivesEmptyLists() {
        val (fresh, rec) = recoverySplit(emptyMap())
        assertTrue(fresh.isEmpty() && rec.isEmpty())
    }
}
