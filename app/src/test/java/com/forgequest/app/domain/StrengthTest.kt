package com.forgequest.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.ln

class StrengthTest {

    @Test
    fun singleIsTheWeight() {
        assertEquals(100f, Strength.e1rm(100f, 1), 0.0001f)
        assertEquals(62.5f, Strength.e1rm(62.5f, 1), 0.0001f)
    }

    @Test
    fun epleyBetweenTwoAndTwelve() {
        assertEquals(100f * (1f + 2f / 30f), Strength.e1rm(100f, 2), 0.001f)
        assertEquals(116.6667f, Strength.e1rm(100f, 5), 0.01f)
        assertEquals(100f * (1f + 12f / 30f), Strength.e1rm(100f, 12), 0.001f)
    }

    @Test
    fun highRepsAreSoftCapped() {
        val linear20 = 60f * (1f + 20f / 30f)
        val expected20 = 60f * (1f + (12f + 3f * ln(9f)) / 30f)
        val actual20 = Strength.e1rm(60f, 20)
        assertEquals(expected20, actual20, 0.001f)
        assertTrue(actual20 < linear20)
        assertTrue(actual20 > Strength.e1rm(60f, 12))
    }

    @Test
    fun twentyRepsNeverBeatsAnHonestFive() {
        val fiveBy100 = Strength.e1rm(100f, 5)
        val twentyBy60 = Strength.e1rm(60f, 20)
        assertTrue("$fiveBy100 should beat $twentyBy60", fiveBy100 > twentyBy60)
    }

    @Test
    fun zeroAndNegativeAreZero() {
        assertEquals(0f, Strength.e1rm(0f, 5), 0.0001f)
        assertEquals(0f, Strength.e1rm(-40f, 5), 0.0001f)
        assertEquals(0f, Strength.e1rm(100f, 0), 0.0001f)
        assertEquals(0f, Strength.e1rm(100f, -3), 0.0001f)
        assertEquals(0f, Strength.e1rm(Float.NaN, 5), 0.0001f)
        assertEquals(0f, Strength.e1rm(Float.POSITIVE_INFINITY, 5), 0.0001f)
    }
}
