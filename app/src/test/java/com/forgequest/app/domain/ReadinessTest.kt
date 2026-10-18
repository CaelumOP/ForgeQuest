package com.forgequest.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

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

    @Test fun napDoesNotReplaceTheNight() {
        val pieces = listOf(
            SleepPiece(at("2026-10-09T21:02:00Z"), at("2026-10-10T04:19:00Z"), 69, 85, 165, 118, 0, true),
            SleepPiece(at("2026-10-10T15:03:00Z"), at("2026-10-10T16:26:00Z"), 20, 39, 23, 1, 0, true),
        )
        assertEquals(319, Readiness.nights(pieces, zone)[LocalDate.of(2026, 10, 10)]!!.asleepMin)
    }

    @Test fun scoresMoveTheRightWay() {
        val hrv = Baseline(28.0, 4.0, 10); val rhr = Baseline(68.0, 3.0, 10); val resp = Baseline(16.3, 0.6, 10)
        val usual = Readiness.recovery(28.0, 68.0, 16.3, 70, hrv, rhr, resp)
        val good = Readiness.recovery(36.0, 63.0, 15.6, 90, hrv, rhr, resp)
        val bad = Readiness.recovery(20.0, 74.0, 17.5, 40, hrv, rhr, resp)
        assertTrue(usual in 55..65)
        assertTrue(good > 85 && bad < 34)
        assertEquals(DayCall.PEAK, Readiness.call(good))
        assertEquals(DayCall.RECOVER, Readiness.call(bad))
    }

    @Test fun strainCurve() {
        assertEquals(0.0, Readiness.strain(DoubleArray(5)), 0.001)
        val hardHour = doubleArrayOf(0.0, 0.0, 20.0, 30.0, 10.0) // load 230
        assertTrue(Readiness.strain(hardHour) in 12.0..14.0)
        val samples = (0 until 61).map { Instant.ofEpochSecond(it * 2L) to 150L }
        assertEquals(2.0, Readiness.zoneMinutes(samples, 190)[2], 0.01) // 150 bpm = 79 % of 190 → zone 3
    }

    @Test fun sleepScore() {
        val full = Night(at("2026-10-17T23:00:00Z"), at("2026-10-18T07:10:00Z"), 90, 110, 280, 10, 480)
        assertTrue(Readiness.sleepScore(full) >= 95)
        val short = Night(at("2026-10-17T22:08:00Z"), at("2026-10-18T01:34:00Z"), 59, 30, 107, 10, 196)
        assertTrue(Readiness.sleepScore(short) in 40..65)
    }
}
