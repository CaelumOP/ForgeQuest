package com.forgequest.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class JournalWeekTest {
    @Test
    fun sundayStartsItsOwnWeekAndSaturdayLandsSixDaysLaterInThatWeek() {
        val sunday = LocalDate.of(2026, 10, 11)
        val monday = LocalDate.of(2026, 10, 12)
        val saturday = LocalDate.of(2026, 10, 17)
        assertEquals(DayOfWeek.SUNDAY, sunday.dayOfWeek)
        assertEquals(DayOfWeek.SATURDAY, saturday.dayOfWeek)
        assertEquals(sunday.toEpochDay(), Dates.weekStart(sunday.toEpochDay()))
        assertEquals(sunday.toEpochDay(), Dates.weekStart(monday.toEpochDay()))
        assertEquals(sunday.toEpochDay(), Dates.weekStart(saturday.toEpochDay()))
        assertEquals(saturday.toEpochDay() - 6, Dates.weekStart(saturday.toEpochDay()))
    }
}
