package com.forgequest.app.ui.friends

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OptimisticRowStateTest {
    @Test
    fun beginHideAddsPendingAndHiddenAndRejectsDuplicateTap() {
        val initial = OptimisticRowState()
        val started = initial.beginHide("u1")!!
        assertEquals(setOf("u1"), started.pending)
        assertEquals(setOf("u1"), started.hidden)
        assertNull(started.beginHide("u1"))
    }

    @Test
    fun endHideRollsBackOnFailure() {
        val started = OptimisticRowState().beginHide("u1")!!
        val rolledBack = started.endHide("u1", success = false, stillPresent = true)
        assertEquals(emptySet<String>(), rolledBack.pending)
        assertEquals(emptySet<String>(), rolledBack.hidden)
    }

    @Test
    fun endHideUnhidesOnceRemovedFromSourceListSoAcceptedFriendCanShowInBoard() {
        val started = OptimisticRowState().beginHide("u1")!!
        val finished = started.endHide("u1", success = true, stillPresent = false)
        assertEquals(emptySet<String>(), finished.pending)
        assertEquals(emptySet<String>(), finished.hidden)
    }

    @Test
    fun endHideKeepsHiddenIfSuccessButStillInStaleSourceList() {
        val started = OptimisticRowState().beginHide("u1")!!
        val finished = started.endHide("u1", success = true, stillPresent = true)
        assertEquals(emptySet<String>(), finished.pending)
        assertEquals(setOf("u1"), finished.hidden)
    }

    @Test
    fun independentItemsStayInteractive() {
        val s1 = OptimisticRowState().beginHide("u1")!!
        val s2 = s1.beginHide("u2")!!
        assertEquals(setOf("u1", "u2"), s2.pending)
        assertEquals(setOf("u1", "u2"), s2.hidden)

        val afterU1Failed = s2.endHide("u1", success = false, stillPresent = true)
        assertEquals(setOf("u2"), afterU1Failed.pending)
        assertEquals(setOf("u2"), afterU1Failed.hidden)
    }
}
