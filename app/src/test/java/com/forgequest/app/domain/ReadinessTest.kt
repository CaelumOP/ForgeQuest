package com.forgequest.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.ln

class ReadinessTest {
    private val zone = ZoneId.of("Asia/Riyadh")
    private fun at(s: String) = Instant.parse(s)

    @Test fun splitNightIsJoinedAndDuplicatesDropped() {
        val pieces = listOf(
            // Real pattern from the owner's Fitbit: 01:59–05:58 then 07:55–11:32 local (UTC+3).
            SleepPiece(at("2026-10-15T22:59:00Z"), at("2026-10-16T02:58:00Z"), 62, 44, 124, 9, 0, fromFitbit = true),
            SleepPiece(at("2026-10-16T04:55:00Z"), at("2026-10-16T08:32:00Z"), 33, 84, 96, 4, 0, fromFitbit = true),
            // Google Fit's copy of the first chunk.
            SleepPiece(at("2026-10-15T23:00:00Z"), at("2026-10-16T02:58:00Z"), 0, 0, 0, 0, 238, fromFitbit = false),
        )
        val night = Readiness.nights(pieces, zone)[LocalDate.of(2026, 10, 16)]!!
        assertEquals(62 + 44 + 124 + 33 + 84 + 96, night.asleepMin)
        assertEquals(95, night.deepMin)
    }

    @Test fun napDoesNotReplaceTheNightButIsCounted() {
        val pieces = listOf(
            SleepPiece(at("2026-10-09T21:02:00Z"), at("2026-10-10T04:19:00Z"), 69, 85, 165, 118, 0, true),
            SleepPiece(at("2026-10-10T15:03:00Z"), at("2026-10-10T16:26:00Z"), 20, 39, 23, 1, 0, true),
        )
        val day = Readiness.sleepDays(pieces, zone)[LocalDate.of(2026, 10, 10)]!!
        assertEquals(319, day.night.asleepMin)
        assertEquals(82, day.napMin)
    }

    @Test fun sleepNeedAndPerformance() {
        // Owner's 3h16m night after rested nights: about 40 %, not the old 61.
        assertEquals(41, Readiness.sleepPerformance(196, Readiness.sleepNeed(8.0, listOf(480, 480, 480), 0)))
        // Three 5-hour nights carry a third of their 9-hour shortfall, capped at 2 hours; strain 18 adds 32 minutes; a 40-minute nap takes some back.
        assertEquals(120, Readiness.debtMin(listOf(300, 300, 300)))
        assertEquals(40, Readiness.debtMin(listOf(480, 480, 360)))
        assertEquals(480 + 32 + 120 - 40, Readiness.sleepNeed(18.0, listOf(300, 300, 300), 40))
    }

    @Test fun recoveryMovesTheRightWayAndIgnoresSmallWiggles() {
        val hrv = Baseline(ln(28.0), 0.12, 20); val rhr = Baseline(68.0, 3.0, 20); val resp = Baseline(16.3, 0.6, 20)
        val usual = Readiness.recovery(ln(28.0), 68.0, 16.3, 85, hrv, rhr, resp)
        assertTrue(usual in 57..63)
        // HRV 29 vs 28 is inside the smallest worthwhile change: same score.
        assertEquals(usual, Readiness.recovery(ln(29.0), 68.0, 16.3, 85, hrv, rhr, resp))
        val good = Readiness.recovery(ln(37.0), 63.0, 15.6, 100, hrv, rhr, resp)
        val bad = Readiness.recovery(ln(20.0), 74.0, 17.5, 40, hrv, rhr, resp)
        assertTrue(good >= 85 && bad < 34)
        assertEquals(DayCall.PEAK, Readiness.call(good))
        assertEquals(DayCall.RECOVER, Readiness.call(bad))
    }

    @Test fun hrvSignalBlendsTonightWithTheWeek() {
        val s = Readiness.hrvSignal(26.0, listOf(26.0, 37.0, 37.0, 37.0, 37.0, 37.0, 37.0))!!
        assertTrue(s > ln(26.0) && s < ln(37.0))
    }

    @Test fun strainUsesHeartRateReserve() {
        assertEquals(0.0, Readiness.strainFromLoad(0.0), 0.001)
        // An hour at 80 % of reserve (rest 65, max 190 → 165 bpm) is about 140 TRIMP; two hours of walking at 100 bpm about 33.
        val hourHard = (0..1800).map { Instant.ofEpochSecond(it * 2L) to 165L }
        val hard = Readiness.trimp(hourHard, 190, 65.0)
        assertTrue(hard in 120.0..150.0)
        val walk2h = (0..3600).map { Instant.ofEpochSecond(it * 2L) to 100L }
        val easy = Readiness.trimp(walk2h, 190, 65.0)
        assertTrue(easy in 25.0..40.0)
        assertTrue(Readiness.strainFromLoad(easy) in 4.0..8.0)
        assertTrue(Readiness.strainFromLoad(easy + hard) in 14.0..18.0)
        // Sitting at 75 bpm (8 % of reserve) all day adds nothing.
        val sitting = (0..21600).map { Instant.ofEpochSecond(it * 2L) to 75L }
        assertEquals(0.0, Readiness.trimp(sitting, 190, 65.0), 0.001)
        // An easy walk at 100 bpm (rest 65, max 190 → 28 % of reserve) is zone 1; 165 bpm (80 %) is zone 4.
        val walk = (0 until 61).map { Instant.ofEpochSecond(it * 2L) to 100L }
        assertEquals(2.0, Readiness.zoneMinutes(walk, 190, 65.0)[0], 0.01)
        val zoneHard = (0 until 61).map { Instant.ofEpochSecond(it * 2L) to 165L }
        assertEquals(2.0, Readiness.zoneMinutes(zoneHard, 190, 65.0)[3], 0.01)
        assertEquals(StrainBand.MODERATE, Readiness.band(11.8))
        assertEquals(4.0..7.0, Readiness.strainTarget(30))
        assertEquals(7.0..10.0, Readiness.strainTarget(34))
    }
}
