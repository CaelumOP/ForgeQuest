package com.forgequest.app.ui.workouts

import com.forgequest.app.data.db.SetEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveWorkoutPrPillTest {

    private fun set(done: Boolean, isPr: Boolean = false) = SetEntity(
        id = 1L,
        sessionId = 1L,
        exerciseId = "bench_press",
        itemPosition = 0,
        block = "Main",
        circuit = false,
        setIndex = 0,
        targetLabel = "",
        reps = 5,
        weightKg = 100f,
        done = done,
        isPr = isPr,
    )

    @Test
    fun showsWhenDoneAndLivePr() {
        assertTrue(showPrPill(set(done = true, isPr = false), livePr = true))
    }

    @Test
    fun showsWhenDoneAndPersistedPr() {
        assertTrue(showPrPill(set(done = true, isPr = true), livePr = false))
    }

    @Test
    fun hiddenWhenNotDone() {
        assertFalse(showPrPill(set(done = false, isPr = true), livePr = true))
    }

    @Test
    fun hiddenWhenDoneWithoutPr() {
        assertFalse(showPrPill(set(done = true, isPr = false), livePr = false))
    }
}
