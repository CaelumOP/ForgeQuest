package com.forgequest.app.data.backup

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.forgequest.app.data.db.AppDatabase
import com.forgequest.app.data.prefs.Settings
import com.forgequest.app.domain.countsForProgression
import com.forgequest.app.domain.countsForScore
import com.forgequest.app.domain.countsForPR
import com.forgequest.app.domain.SetType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class FoundationBackupTest {
    private fun fixture(name: String): BackupFile = backupJson.decodeFromString(
        BackupFile.serializer(), File("../web/src/data/fixtures/$name").readText(),
    )

    @Test fun oldBackupDefaultsAndSetEligibilityAreCompatible() {
        val old = fixture("android-backup.json")
        assertTrue(old.bodyMeasurements.isEmpty())
        assertEquals("WEEKDAY", old.plans.first().scheduleMode)
        assertEquals("DOUBLE", old.planItems.first().progressionMode)
        assertNull(old.planItems.first().supersetGroup)
        assertEquals("", old.exercises.first().unitOverride)
        val set = old.sets.first()
        for (type in SetType.entries) {
            val typed = set.copy(setType = type.name)
            val expected = type == SetType.WORKING || type == SetType.FAILURE
            assertEquals(expected, countsForProgression(typed))
            assertEquals(expected, countsForScore(typed))
            assertEquals(expected, countsForPR(typed))
        }
        for (block in listOf("Warm-up", "WARM-UP", "Cool-down")) {
            assertFalse(countsForProgression(set.copy(block = block)))
            assertFalse(countsForScore(set.copy(block = block)))
            assertFalse(countsForPR(set.copy(block = block)))
        }
    }

    @Test fun bothPlatformFixturesRestoreAndReexportThroughRoom() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
        try {
            for (name in listOf("backup-4.1.json", "android-backup.json")) {
                val file = fixture(name)
                val b = db.backup()
                b.replaceAll(file.exercises, file.plans, file.planDays, file.planItems, file.sessions, file.sets,
                    file.foods, file.meals, file.weights, file.chat, file.badges, file.dayNotes, file.readinessDays, file.bodyMeasurements)
                val exported = file.copy(exercises = b.exercises(), plans = b.plans(), planItems = b.planItems(),
                    sets = b.sets(), sessions = b.sessions(), bodyMeasurements = b.bodyMeasurements())
                val decoded = backupJson.decodeFromString(BackupFile.serializer(), backupJson.encodeToString(BackupFile.serializer(), exported))
                assertEquals(file, decoded)
                assertNull(decoded.sessions.last().finishedAt)
            }
        } finally { db.close() }
    }

    @Test fun portableSettingsNeverReplaceLocalDriveState() {
        val local = Settings(driveEmail = "a@example.test", autoBackup = false, autoBackupOptedOut = true,
            restoreOfferShown = setOf("a@example.test"))
        val restored = fixture("backup-4.1.json").settings!!.applyTo(local)
        assertFalse(restored.autoBackupEffective)
        assertTrue(restored.autoBackupOptedOut)
        assertEquals(local.restoreOfferShown, restored.restoreOfferShown)
        assertEquals(15f, restored.barWeightKg, 0f)
        assertTrue(restored.ttsCues)
        assertFalse(Settings().autoBackupEffective)
        assertTrue(Settings(driveEmail = "a@example.test").autoBackupEffective)
        val encoded = backupJson.encodeToString(BackupSettings.serializer(), BackupSettings.from(restored))
        assertFalse(encoded.contains("driveEmail")); assertFalse(encoded.contains("restoreOfferShown"))
    }
}
