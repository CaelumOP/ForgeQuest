package com.forgequest.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class WorkoutEndTest {
    private val now = 10_000_000_000L
    private val hour = 3_600_000L
    private val minute = 60_000L

    @Test fun idleForHoursEndsTwoMinutesAfterLastSet() {
        val last = now - 3 * hour
        assertEquals(last + 2 * minute, WorkoutEnd.endTime(now, last))
    }

    @Test fun recentSetEndsNow() = assertEquals(now, WorkoutEnd.endTime(now, now - 5 * minute))

    @Test fun noTickTimeEndsNow() = assertEquals(now, WorkoutEnd.endTime(now, null))
}
