package com.forgequest.app.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GettingStartedLogicTest {
    @Test
    fun computeGettingStartedCountsEachCompletedItem() {
        val none = computeGettingStarted(
            GettingStartedInput(
                hasWorkout = false,
                hasMeal = false,
                hasWeight = false,
                hasMeasurement = false,
                friendDone = false,
            )
        )
        assertEquals(0, none.doneCount)
        assertEquals(5, none.total)
        assertFalse(none.allDone)

        val partial = computeGettingStarted(
            GettingStartedInput(
                hasWorkout = true,
                hasMeal = false,
                hasWeight = true,
                hasMeasurement = false,
                friendDone = true,
            )
        )
        assertEquals(3, partial.doneCount)
        assertTrue(partial.workoutDone)
        assertFalse(partial.foodDone)
        assertTrue(partial.weightDone)
        assertFalse(partial.measureDone)
        assertTrue(partial.friendDone)
        assertFalse(partial.allDone)

        val all = computeGettingStarted(
            GettingStartedInput(
                hasWorkout = true,
                hasMeal = true,
                hasWeight = true,
                hasMeasurement = true,
                friendDone = true,
            )
        )
        assertEquals(5, all.doneCount)
        assertTrue(all.allDone)
    }

    @Test
    fun visibilityRespectsEligibilityDismissalAndAllDoneVisit() {
        assertFalse(isGettingStartedVisible(eligible = false, dismissed = false, allDone = false))
        assertTrue(isGettingStartedVisible(eligible = true, dismissed = false, allDone = false))
        assertFalse(isGettingStartedVisible(eligible = true, dismissed = true, allDone = false))
        assertTrue(
            isGettingStartedVisible(
                eligible = true,
                dismissed = false,
                allDone = true,
                showingAllDoneVisit = true,
            )
        )
        assertFalse(
            isGettingStartedVisible(
                eligible = true,
                dismissed = true,
                allDone = true,
                showingAllDoneVisit = false,
            )
        )
    }
}
