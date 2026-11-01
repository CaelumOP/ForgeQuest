package com.forgequest.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class JournalWeekTest {
    @Test
    fun sundayWorkoutLandsInTheWeekThatStartedOnMonday() {
        val sunday = LocalDate.of(2026, 10, 11)
        val monday = LocalDate.of(2026, 10, 5)
        assertEquals(DayOfWeek.SUNDAY, sunday.dayOfWeek)
        assertEquals(monday.toEpochDay(), Dates.weekStart(sunday.toEpochDay()))
    }

    @Test
    fun mondayStartsItsOwnWeek() {
        val monday = LocalDate.of(2026, 10, 12).toEpochDay()
        assertEquals(monday, Dates.weekStart(monday))
    }
}
