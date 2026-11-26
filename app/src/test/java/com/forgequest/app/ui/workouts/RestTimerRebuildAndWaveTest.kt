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
    fun waveTrackLeavesSixDpBetweenRoundCapsAndHidesWhenFull() {
        val start = restWaveTrackStart(316f, 0.5f)!!
        assertEquals(6f, (start - 2f) - (158f + 2f), 0.0001f)
        assertEquals(0f, restWaveTrackStart(316f, 0f)!!, 0.0001f)
        assertEquals(0f, restWaveTrackStart(316f, -1f)!!, 0.0001f)
        assertNull(restWaveTrackStart(316f, 1f))
        assertNull(restWaveTrackStart(316f, 2f))
        assertNull(restWaveTrackStart(316f, 0.99f))
        assertNull(restWaveTrackStart(20f, 0.5f))
        assertNull(restWaveTrackStart(0f, 0.5f))
    }

    @Test
    fun waveTaperIsSmoothAndSymmetricIncludingShortWaves() {
        assertEquals(0f, restWaveTaper(0f, 160f), 0f)
        assertEquals(0f, restWaveTaper(160f, 160f), 0f)
        assertEquals(0.5f, restWaveTaper(16f, 160f), 0.0001f)
        assertEquals(0.5f, restWaveTaper(144f, 160f), 0.0001f)
        assertEquals(1f, restWaveTaper(80f, 160f), 0f)
        assertEquals(0.15625f, restWaveTaper(8f, 16f), 0.0001f)
        assertEquals(0f, restWaveTaper(0f, 0f), 0f)
        assertEquals(0f, restWaveTaper(-1f, 160f), 0f)
        assertEquals(0f, restWaveTaper(161f, 160f), 0f)
        assertEquals(0f, restWaveTaper(0.01f, 160f) / 0.01f, 0.0001f)
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
