package com.forgequest.app.data.prefs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreakTargetsMigrationTest {
    @Test
    fun mondayKeysShiftBackOneDayToSundayOnceAndOnlyOnce() {
        val rawBefore: Map<String, Any> = mapOf(
            "20500" to 3,
            "20507" to 4,
            "unexpected_flag" to true,
        )
        val (shifted, changedFirst) = migrateWeekTargetEntries(rawBefore)
        assertTrue(changedFirst)
        assertEquals(mapOf(20499L to 3, 20506L to 4), shifted)

        val persistedAfterFirstPass: Map<String, Any> = buildMap {
            put(SUNDAY_WEEKS_KEY, true)
            shifted.forEach { (k, v) -> put(k.toString(), v) }
        }
        val (secondRead, changedSecond) = migrateWeekTargetEntries(persistedAfterFirstPass)
        assertFalse(changedSecond)
        assertEquals(mapOf(20499L to 3, 20506L to 4), secondRead)
    }

    @Test
    fun emptyPrefsMarksMigratedWithoutAddingKeys() {
        val (shifted, changed) = migrateWeekTargetEntries(emptyMap<String, Any>())
        assertTrue(changed)
        assertTrue(shifted.isEmpty())

        val (second, changedSecond) = migrateWeekTargetEntries(mapOf(SUNDAY_WEEKS_KEY to true))
        assertFalse(changedSecond)
        assertTrue(second.isEmpty())
    }
}
