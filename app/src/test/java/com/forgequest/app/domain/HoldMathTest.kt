package com.forgequest.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class HoldMathTest {

    @Test
    fun holdStoppedOneSecondLateSavesTarget() {
        assertEquals(30, HoldMath.recorded(held = 31, target = 30))
    }

    @Test
    fun holdStoppedThreeSecondsLateSavesTarget() {
        assertEquals(30, HoldMath.recorded(held = 33, target = 30))
    }

    @Test
    fun holdStoppedFourSecondsLateSavesHeld() {
        assertEquals(34, HoldMath.recorded(held = 34, target = 30))
    }

    @Test
    fun holdStoppedEarlySavesHeld() {
        assertEquals(22, HoldMath.recorded(held = 22, target = 30))
    }

    @Test
    fun zeroTargetSavesHeld() {
        assertEquals(15, HoldMath.recorded(held = 15, target = 0))
    }
}
