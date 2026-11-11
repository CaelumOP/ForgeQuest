package com.forgequest.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/** The readiness sentence: which one fits the day, and how its wordings take turns. */
class ReadinessTextTest {
    private val day = LocalDate.of(2026, 11, 10)
    private fun night(asleep: Int, deep: Int = 60) = Night(Instant.EPOCH, Instant.EPOCH.plusSeconds(asleep * 60L), deepMin = deep, remMin = 60, lightMin = asleep - deep - 60, awakeMin = 0, asleepMin = asleep)

    @Test fun sleepContextCountsShortNightsInARow() {
        val nights = mapOf(day to night(330), day.minusDays(1) to night(340), day.minusDays(2) to night(450), day.minusDays(3) to night(300))
        val s = SleepContext.from(nights, day)
        assertEquals(330, s.lastNightMin)
        assertEquals(2, s.shortNightsInRow)
        assertEquals(60f / 330f, s.deepShare!!, 0.0001f)
    }

    @Test fun sleepContextWithoutLastNight() {
        val s = SleepContext.from(mapOf(day.minusDays(1) to night(300)), day)
        assertNull(s.lastNightMin)
        assertEquals(0, s.shortNightsInRow)
        assertNull(s.deepShare)
    }

    @Test fun aGoodNightBreaksTheRun() {
        assertEquals(0, SleepContext.from(mapOf(day to night(420), day.minusDays(1) to night(300)), day).shortNightsInRow)
    }

    @Test fun sleepExplainsASteadyOrEaseOffDay() {
        val twoShort = SleepContext(330, 2, 0.2f)
        assertEquals(ReadinessLine.STEADY_SHORT_NIGHTS, readinessLine(DayCall.STEADY, TodayFocus.LEGS, twoShort))
        assertEquals(ReadinessLine.EASE_SHORT_SLEEP, readinessLine(DayCall.EASE_OFF, null, SleepContext(300, 1, 0.2f)))
        // A recover day after a short night names the sleep, even on a leg day; after a full night it does not.
        assertEquals(ReadinessLine.RECOVER_SHORT_SLEEP, readinessLine(DayCall.RECOVER, TodayFocus.LEGS, SleepContext(150, 1, 0.2f)))
        assertEquals(ReadinessLine.RECOVER, readinessLine(DayCall.RECOVER, null, SleepContext(420, 0, 0.2f)))
        // One short night does not make a steady day about sleep, and sleep never talks over a good verdict.
        assertEquals(ReadinessLine.STEADY, readinessLine(DayCall.STEADY, null, SleepContext(330, 1, 0.2f)))
        assertEquals(ReadinessLine.READY, readinessLine(DayCall.READY, null, twoShort))
    }

    @Test fun planAwareAdviceThenThePlainCall() {
        assertEquals(ReadinessLine.LEGS_EASE, readinessLine(DayCall.EASE_OFF, TodayFocus.LEGS, null))
        assertEquals(ReadinessLine.UPPER_RECOVER, readinessLine(DayCall.RECOVER, TodayFocus.UPPER, null))
        assertEquals(ReadinessLine.READY, readinessLine(DayCall.READY, TodayFocus.LEGS, null))
        assertEquals(ReadinessLine.PEAK, readinessLine(DayCall.PEAK, TodayFocus.UPPER, null))
        assertEquals(ReadinessLine.RECOVER, readinessLine(DayCall.RECOVER, null, SleepContext(480, 0, 0.2f)))
    }

    @Test fun theWordingStaysForTheDayThenTakesTurns() {
        val kind = ReadinessLine.READY.historyKind
        var history = ""
        val shown = mutableListOf<Int>()
        for (d in 100L..105L) {
            val (variant, isNew) = dailyVariant(kind, 3, history, d)
            assertTrue(isNew)
            history = appendHistory(history, kind, variant, d)
            // Opening Home again the same day gives the same sentence and stores nothing.
            assertEquals(variant to false, dailyVariant(kind, 3, history, d))
            shown += variant
        }
        assertEquals(listOf(0, 1, 2, 0, 1, 2), shown)
    }

    @Test fun otherLinesDoNotDisturbTheRotation() {
        val history = appendHistory("", ReadinessLine.STEADY.historyKind, 0, 100)
        val (variant, isNew) = dailyVariant(ReadinessLine.READY.historyKind, 3, history, 100)
        assertEquals(0, variant)
        assertTrue(isNew)
        assertFalse(dailyVariant(ReadinessLine.STEADY.historyKind, 3, history, 100).second)
    }
}
