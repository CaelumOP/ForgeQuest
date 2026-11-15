package com.forgequest.app.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupWeekTargetsTest {
    private fun emptyBackup(
        weekTargets: Map<String, Int>? = null,
        weekStart: String? = null,
    ) = BackupFile(
        createdAt = 1761200000000L,
        exercises = emptyList(),
        plans = emptyList(),
        planDays = emptyList(),
        planItems = emptyList(),
        sessions = emptyList(),
        sets = emptyList(),
        foods = emptyList(),
        meals = emptyList(),
        weights = emptyList(),
        chat = emptyList(),
        badges = emptyList(),
        weekTargets = weekTargets,
        weekStart = weekStart,
    )

    @Test
    fun backupFileWithWeekTargetsRoundTripsThroughJson() {
        val original = emptyBackup(
            weekTargets = mapOf("20499" to 3, "20506" to 4),
            weekStart = "sunday",
        )
        val encoded = backupJson.encodeToString(BackupFile.serializer(), original)
        val decoded = backupJson.decodeFromString(BackupFile.serializer(), encoded)
        assertEquals(original, decoded)
        assertTrue(encoded.indexOf("\"readinessDays\"") < encoded.indexOf("\"weekTargets\""))
        assertTrue(encoded.indexOf("\"weekTargets\"") < encoded.indexOf("\"weekStart\""))
    }

    @Test
    fun oldBackupJsonWithoutWeekTargetsDecodesWithNulls() {
        val oldJson = """
            {
              "version": 2,
              "createdAt": 1761200000000,
              "exercises": [],
              "plans": [],
              "planDays": [],
              "planItems": [],
              "sessions": [],
              "sets": [],
              "foods": [],
              "meals": [],
              "weights": [],
              "chat": [],
              "badges": []
            }
        """.trimIndent()
        val decoded = backupJson.decodeFromString(BackupFile.serializer(), oldJson)
        assertNull(decoded.weekTargets)
        assertNull(decoded.weekStart)
    }

    @Test
    fun restoredWeekTargetsRespectsSundayMarkerAndFiltersInvalidEntries() {
        assertNull(restoredWeekTargets(emptyBackup()))

        val sunday = emptyBackup(
            weekTargets = mapOf("20499" to 3, "20506" to 7, "20513" to 1, "20520" to 0, "20527" to 8, "bad" to 4),
            weekStart = "sunday",
        )
        assertEquals(
            mapOf(20499L to 3, 20506L to 7, 20513L to 1),
            restoredWeekTargets(sunday),
        )

        val legacyMonday = emptyBackup(
            weekTargets = mapOf("20500" to 3, "20507" to 5, "20514" to -1),
            weekStart = null,
        )
        assertEquals(
            mapOf(20499L to 3, 20506L to 5),
            restoredWeekTargets(legacyMonday),
        )
    }

    @Test
    fun uniqueSafetyNameAppendsIncrementingSuffixOnCollisions() {
        val stamp = "2026-11-15_22-00-00-123"
        assertEquals(
            "before-restore_2026-11-15_22-00-00-123.json",
            uniqueSafetyName(emptySet(), stamp),
        )
        assertEquals(
            "before-restore_2026-11-15_22-00-00-123_2.json",
            uniqueSafetyName(setOf("before-restore_2026-11-15_22-00-00-123.json"), stamp),
        )
        assertEquals(
            "before-restore_2026-11-15_22-00-00-123_3.json",
            uniqueSafetyName(
                setOf(
                    "before-restore_2026-11-15_22-00-00-123.json",
                    "before-restore_2026-11-15_22-00-00-123_2.json",
                ),
                stamp,
            ),
        )
    }
}
