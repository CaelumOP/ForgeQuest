package com.forgequest.app.ui.workouts

import com.forgequest.app.data.notify.RestAlert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class RestTimerRebuildAndWaveTest {

    @Test
    fun restWaveProgressComputesContinuousFractionFromEndsAt() {
        assertEquals(1f, restWaveProgress(endsAtMillis = 90_000L, totalSeconds = 90, nowMillis = 0L), 0.0001f)
        assertEquals(0.5f, restWaveProgress(endsAtMillis = 90_000L, totalSeconds = 90, nowMillis = 45_000L), 0.0001f)
        assertEquals(0.255f, restWaveProgress(endsAtMillis = 90_000L, totalSeconds = 90, nowMillis = 67_050L), 0.0001f)
        assertEquals(0f, restWaveProgress(endsAtMillis = 90_000L, totalSeconds = 90, nowMillis = 95_000L), 0.0001f)
        assertEquals(0f, restWaveProgress(endsAtMillis = 90_000L, totalSeconds = 0, nowMillis = 10_000L), 0.0001f)
        assertEquals(1f, restWaveProgress(endsAtMillis = 120_000L, totalSeconds = 60, nowMillis = 0L), 0.0001f)
    }

    @Test
    fun rebuildRestFromSavedRestoresActiveRestAfterProcessDeath() {
        val saved = RestAlert.SavedRest(
            endsAtMillis = 150_000L,
            totalSeconds = 90,
            nextExercise = "Overhead Press",
            isRestPause = false,
            setId = 19L,
            sessionId = 5L,
        )
        val rebuilt = rebuildRestFromSaved(saved, sessionId = 5L, currentRest = null) { 77L }!!
        assertEquals(90, rebuilt.totalSeconds)
        assertEquals(150_000L, rebuilt.endsAt)
        assertEquals("Overhead Press", rebuilt.nextExercise)
        assertFalse(rebuilt.isRestPause)
        assertEquals(19L, rebuilt.setId)
        assertFalse(rebuilt.isShortTransition)
        assertEquals(77L, rebuilt.instanceId)
    }

    @Test
    fun rebuildRestFromSavedSkipsMismatchedSessionOrExistingRest() {
        val saved = RestAlert.SavedRest(
            endsAtMillis = 150_000L,
            totalSeconds = 90,
            nextExercise = "Squat",
            isRestPause = false,
            setId = 19L,
            sessionId = 5L,
        )
        assertNull(rebuildRestFromSaved(saved, sessionId = 6L, currentRest = null))
        assertNull(
            rebuildRestFromSaved(
                saved = saved,
                sessionId = 5L,
                currentRest = RestTimer(totalSeconds = 60, endsAt = 120_000L),
            ),
        )
        assertNull(rebuildRestFromSaved(saved = null, sessionId = 5L, currentRest = null))
    }
}
