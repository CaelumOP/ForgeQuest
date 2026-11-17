package com.forgequest.app.ui.onboarding

import org.junit.Assert.assertEquals
import org.junit.Test

/** Steps: 0 Welcome, 1 About, 2 Goal, 3 Training, 4 Weight. "Just log" skips Training, as on web. */
class OnboardingStepsTest {
    private val vm = OnboardingViewModel

    @Test fun justLogSkipsTrainingBothWays() {
        assertEquals(4, vm.nextStep(2, justLog = true))
        assertEquals(2, vm.prevStep(4, justLog = true))
        assertEquals(3, vm.nextStep(2, justLog = false))
        assertEquals(3, vm.prevStep(4, justLog = false))
        assertEquals(4, vm.nextStep(4, justLog = true))
        assertEquals(0, vm.prevStep(0, justLog = true))
    }

    @Test fun progressCountsFourStepsWhenJustLogging() {
        assertEquals(listOf(1 to 4, 2 to 4, 3 to 4, 4 to 4), listOf(0, 1, 2, 4).map { vm.progress(it, justLog = true) })
        assertEquals(listOf(1 to 5, 4 to 5, 5 to 5), listOf(0, 3, 4).map { vm.progress(it, justLog = false) })
    }
}
