package com.forgequest.app.ui.workouts

import com.forgequest.app.data.db.SetEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PickNextSetTest {
    private var id = 0L
    private fun set(pos: Int, idx: Int, doneAt: Long? = null) =
        SetEntity(id = ++id, sessionId = 1, exerciseId = "e$pos", itemPosition = pos, block = "", circuit = false, setIndex = idx, targetLabel = "", done = doneAt != null, doneAt = doneAt)
    private fun straight(pos: Int, vararg doneAt: Long?) = Group.Straight(pos, "", doneAt.mapIndexed { i, d -> set(pos, i, d) })

    @Test fun nothingDoneStartsAtFirst() {
        val g = listOf(straight(0, null, null), straight(1, null))
        assertEquals(g[0].allSets[0].id, pickNextSet(g)?.id)
    }

    @Test fun continuesInTheMostRecentGroup() {
        val g = listOf(straight(0, 10L, null), straight(1, 50L, null), straight(2, null))
        assertEquals(g[1].allSets[1].id, pickNextSet(g)?.id)
    }

    @Test fun finishedGroupDoesNotJumpBackToSkippedWarmup() {
        // Warm-up (group 0) skipped; group 1 just finished: go forward to group 2.
        val g = listOf(straight(0, null), straight(1, 10L, 20L), straight(2, null))
        assertEquals(g[2].allSets[0].id, pickNextSet(g)?.id)
    }

    @Test fun fallsBackToSkippedWhenNothingLater() {
        val g = listOf(straight(0, null), straight(1, 10L, 20L))
        assertEquals(g[0].allSets[0].id, pickNextSet(g)?.id)
    }

    @Test fun allDoneIsNull() {
        assertNull(pickNextSet(listOf(straight(0, 1L), straight(1, 2L))))
    }
}
