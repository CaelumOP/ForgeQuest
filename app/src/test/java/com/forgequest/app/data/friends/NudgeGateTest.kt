package com.forgequest.app.data.friends

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class NudgeGateTest {
    private val riyadh = ZoneId.of("Asia/Riyadh")
    private val newYork = ZoneId.of("America/New_York")

    private fun epochSec(year: Int, month: Int, day: Int, hour: Int, min: Int, zone: ZoneId): Long =
        LocalDateTime.of(year, month, day, hour, min, 0).atZone(zone).toEpochSecond()

    @Test
    fun allowedWhenServerAndLocalAreBothNull() {
        assertNull(NudgeGate.blockedUntil(server = null, local = null, nowSec = 1_700_000_000L))
    }

    @Test
    fun oldServerFallsBackToLocalMidnightInDeviceZone() {
        val now = epochSec(2026, 11, 17, 15, 30, riyadh)
        val expectedMidnight = epochSec(2026, 11, 18, 0, 0, riyadh)
        assertEquals(expectedMidnight, NudgeGate.nextBlockedUntil(serverNextAt = null, nowSec = now, zone = riyadh))

        val serverNext = now + 4 * 3600L
        assertEquals(serverNext, NudgeGate.nextBlockedUntil(serverNextAt = serverNext, nowSec = now, zone = riyadh))
    }

    @Test
    fun blockedUntilUsesMaxOfServerAndLocalAndAllowsAtBoundary() {
        val now = 1_700_000_000L
        assertEquals(now + 7200L, NudgeGate.blockedUntil(server = now + 7200L, local = now + 3600L, nowSec = now))
        assertEquals(now + 9000L, NudgeGate.blockedUntil(server = now + 3600L, local = now + 9000L, nowSec = now))
        assertEquals(now + 3600L, NudgeGate.blockedUntil(server = null, local = now + 3600L, nowSec = now))
        assertEquals(now + 1800L, NudgeGate.blockedUntil(server = now + 1800L, local = null, nowSec = now))
        // Exactly at expiry -> allowed
        assertNull(NudgeGate.blockedUntil(server = now, local = now - 10L, nowSec = now))
        // Past expiry -> allowed
        assertNull(NudgeGate.blockedUntil(server = now - 1L, local = null, nowSec = now))
    }

    @Test
    fun labelKindDistinguishesAgainAtVsTomorrowAtDeviceMidnightBoundary() {
        val now = epochSec(2026, 11, 17, 14, 0, newYork)
        val startOfTomorrow = epochSec(2026, 11, 18, 0, 0, newYork)
        assertEquals(NudgeGate.LabelKind.AGAIN_AT, NudgeGate.labelKind(startOfTomorrow - 1L, now, newYork))
        assertEquals(NudgeGate.LabelKind.TOMORROW, NudgeGate.labelKind(startOfTomorrow, now, newYork))
        assertEquals(NudgeGate.LabelKind.TOMORROW, NudgeGate.labelKind(startOfTomorrow + 3600L, now, newYork))
    }

    @Test
    fun pruneRemovesPastAndBoundaryEntries() {
        val now = 1_700_000_000L
        val pruned = NudgeGate.prune(
            mapOf(
                "past" to now - 5L,
                "exact" to now,
                "future" to now + 1L,
            ),
            nowSec = now,
        )
        assertEquals(mapOf("future" to now + 1L), pruned)
    }

    @Test
    fun effectiveMapMergesServerAndLocalAndDropsExpired() {
        val now = 1_700_000_000L
        val friends = listOf(
            Friend(id = "f1", nudgeNextAt = now + 3600L),
            Friend(id = "f2", nudgeNextAt = now - 10L),
            Friend(id = "f3", nudgeNextAt = null),
        )
        val local = mapOf(
            "f1" to now + 7200L,
            "f2" to now + 500L,
            "f3" to now - 1L,
            "f4" to now + 1200L,
        )
        val merged = NudgeGate.effectiveMap(friends, local, nowSec = now)
        assertEquals(
            mapOf(
                "f1" to now + 7200L,
                "f2" to now + 500L,
                "f4" to now + 1200L,
            ),
            merged,
        )
    }
}
