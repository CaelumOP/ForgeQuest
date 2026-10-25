package com.forgequest.app.data

import com.forgequest.app.data.health.RefreshNote
import com.forgequest.app.data.health.RefreshResult
import com.forgequest.app.data.health.RefreshWatch
import com.forgequest.app.data.health.refreshResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The readiness card's refresh button: Fitbit reaches Health Connect minutes after its own sync. */
@OptIn(ExperimentalCoroutinesApi::class)
class RefreshWatchTest {
    private val today = 20_000L

    @Test fun resultFromTheDayOnTheCard() {
        assertEquals(RefreshResult.NEW_NIGHT, refreshResult(today - 1, today, today))
        assertEquals(RefreshResult.STILL_WAITING, refreshResult(today - 1, today - 1, today))
        assertEquals(RefreshResult.UPDATED, refreshResult(today, today, today))
        assertEquals(RefreshResult.UPDATED, refreshResult(null, today, today))
        assertEquals(RefreshResult.FAILED, refreshResult(today - 1, null, today))
    }

    /** Hands out the given results in order, then repeats the last one. */
    private class Reads(vararg val results: RefreshResult?) {
        var calls = 0
        suspend fun next(): RefreshResult? = results[minOf(calls++, results.lastIndex)]
    }

    @Test fun tapSpinsThenSaysUpdatedAndTheNoteClears() = runTest {
        val reads = Reads(RefreshResult.UPDATED)
        val watch = RefreshWatch(this) { reads.next() }
        watch.tap()
        runCurrent()
        assertTrue(watch.busy.value)
        advanceTimeBy(800)
        assertFalse(watch.busy.value)
        assertEquals(RefreshNote.UPDATED, watch.note.value)
        advanceUntilIdle()
        assertNull(watch.note.value)
        assertEquals(1, reads.calls)
    }

    @Test fun stillWaitingKeepsCheckingUntilTheNightArrives() = runTest {
        val reads = Reads(RefreshResult.STILL_WAITING, RefreshResult.STILL_WAITING, RefreshResult.STILL_WAITING, RefreshResult.NEW_NIGHT)
        val watch = RefreshWatch(this) { reads.next() }
        watch.tap()
        advanceTimeBy(1_000)
        assertEquals(RefreshNote.WAITING, watch.note.value)
        advanceTimeBy(3 * 60_000L)
        assertEquals(RefreshNote.NEW_NIGHT, watch.note.value)
        advanceUntilIdle()
        assertEquals(4, reads.calls)
    }

    @Test fun givesUpAfterFiveMinutes() = runTest {
        val reads = Reads(RefreshResult.STILL_WAITING)
        val watch = RefreshWatch(this) { reads.next() }
        watch.tap()
        advanceUntilIdle()
        assertEquals(RefreshNote.GAVE_UP, watch.note.value)
        assertEquals(1 + 5, reads.calls)
    }

    @Test fun leavingHomeStopsTheChecks() = runTest {
        val reads = Reads(RefreshResult.STILL_WAITING)
        val watch = RefreshWatch(this) { reads.next() }
        watch.tap()
        advanceTimeBy(61_000)
        watch.pause()
        advanceUntilIdle()
        assertEquals(2, reads.calls)
        assertEquals(RefreshNote.WAITING, watch.note.value)
    }

    @Test fun quietChecksOnReturnOnlySpeakWhenTheNightArrives() = runTest {
        val reads = Reads(RefreshResult.STILL_WAITING)
        val watch = RefreshWatch(this) { reads.next() }
        watch.resume(RefreshResult.STILL_WAITING, waiting = true)
        advanceTimeBy(2 * 60_000L + 1)
        assertNull(watch.note.value)
        assertFalse(watch.busy.value)
        advanceUntilIdle()
        assertNull(watch.note.value)
        assertEquals(5, reads.calls)

        val back = Reads(RefreshResult.STILL_WAITING, RefreshResult.NEW_NIGHT)
        val again = RefreshWatch(this) { back.next() }
        again.resume(RefreshResult.STILL_WAITING, waiting = true)
        advanceTimeBy(2 * 60_000L + 1)
        assertEquals(RefreshNote.NEW_NIGHT, again.note.value)
        advanceUntilIdle()
    }

    @Test fun backFromFitbitWithTheNightAlreadyInSaysSo() = runTest {
        val reads = Reads(RefreshResult.UPDATED)
        val watch = RefreshWatch(this) { reads.next() }
        watch.resume(RefreshResult.NEW_NIGHT, waiting = false)
        assertEquals(RefreshNote.NEW_NIGHT, watch.note.value)
        advanceUntilIdle()
        assertNull(watch.note.value)
        assertEquals(0, reads.calls)
    }

    @Test fun aFailedTapSaysSoAndNothingRunsOnItsOwn() = runTest {
        val reads = Reads(RefreshResult.FAILED)
        val watch = RefreshWatch(this) { reads.next() }
        watch.tap()
        advanceUntilIdle()
        assertEquals(RefreshNote.FAILED, watch.note.value)
        assertEquals(1, reads.calls)
        watch.resume(RefreshResult.UPDATED, waiting = false)
        advanceUntilIdle()
        assertEquals(1, reads.calls)
    }
}
