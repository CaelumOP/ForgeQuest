package com.forgequest.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TargetNudgeTest {
    private val today = java.time.LocalDate.of(2026, 10, 28).toEpochDay()
    private val thisWeek = Dates.weekStart(today)
    private val start = thisWeek - 35

    /** [a] days trained two weeks ago, [b] last week. */
    private fun days(a: Int, b: Int): Set<Long> =
        ((0 until a).map { thisWeek - 14 + it } + (0 until b).map { thisWeek - 7 + it }).toSet()

    @Test fun twoWeeksAtThreeWithTargetFiveOffersThree() = assertEquals(3, TargetNudge.offer(days(3, 3), 5, start, today))

    @Test fun offersTheLowerWeek() = assertEquals(2, TargetNudge.offer(days(4, 2), 5, start, today))

    @Test fun quietWhenAWeekHitTheTargetWasUnderTwoOrTargetIsLow() {
        assertNull(TargetNudge.offer(days(5, 3), 5, start, today))
        assertNull(TargetNudge.offer(days(3, 1), 5, start, today))
        assertNull(TargetNudge.offer(days(3, 3), 3, start, today))
    }

    @Test fun needsTwoFinishedWeeksSinceTheFirstWorkout() {
        assertNull(TargetNudge.offer(days(3, 3), 5, thisWeek - 7, today))
        assertEquals(3, TargetNudge.offer(days(3, 3), 5, thisWeek - 14, today))
    }

    @Test fun onceAnsweredNeverAgain() {
        assertTrue(TargetNudge.show(false, 3))
        assertFalse(TargetNudge.show(true, 3))
        assertFalse(TargetNudge.show(false, null))
    }
}
