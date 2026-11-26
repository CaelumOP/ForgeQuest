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
            )
        )
        assertEquals(0, none.doneCount)
        assertEquals(3, none.total)
        assertFalse(none.allDone)

        val partial = computeGettingStarted(
            GettingStartedInput(
                hasWorkout = true,
                hasMeal = false,
                hasWeight = true,
            )
        )
        assertEquals(2, partial.doneCount)
        assertTrue(partial.workoutDone)
        assertFalse(partial.foodDone)
        assertTrue(partial.weightDone)
        assertFalse(partial.allDone)

        val all = computeGettingStarted(
            GettingStartedInput(
                hasWorkout = true,
                hasMeal = true,
                hasWeight = true,
            )
        )
        assertEquals(3, all.doneCount)
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
