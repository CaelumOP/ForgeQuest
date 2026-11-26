package com.forgequest.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class SlidingSegmentedPillsTest {

    @Test
    fun ltrOffsetTracksSelectedIndex() {
        assertEquals(0f, slidingPillOffsetFraction(0f, count = 3, isRtl = false), 0.0001f)
        assertEquals(1f / 3f, slidingPillOffsetFraction(1f, count = 3, isRtl = false), 0.0001f)
        assertEquals(2f / 3f, slidingPillOffsetFraction(2f, count = 3, isRtl = false), 0.0001f)
    }

    @Test
    fun rtlOffsetMirrorsSelectedIndexFromRightEdge() {
        assertEquals(2f / 3f, slidingPillOffsetFraction(0f, count = 3, isRtl = true), 0.0001f)
        assertEquals(1f / 3f, slidingPillOffsetFraction(1f, count = 3, isRtl = true), 0.0001f)
        assertEquals(0f, slidingPillOffsetFraction(2f, count = 3, isRtl = true), 0.0001f)
        // Mid-animation from index 0 -> 1 in RTL slides leftward from 2/3 toward 1/3
        assertEquals(0.5f, slidingPillOffsetFraction(0.5f, count = 3, isRtl = true), 0.0001f)
    }

    @Test
    fun handlesOutOfBoundsAndZeroCountSafely() {
        assertEquals(0f, slidingPillOffsetFraction(1f, count = 0, isRtl = false), 0.0001f)
        assertEquals(0f, slidingPillOffsetFraction(-1f, count = 4, isRtl = false), 0.0001f)
        assertEquals(0.75f, slidingPillOffsetFraction(10f, count = 4, isRtl = false), 0.0001f)
        assertEquals(0f, slidingPillOffsetFraction(10f, count = 4, isRtl = true), 0.0001f)
    }
}
