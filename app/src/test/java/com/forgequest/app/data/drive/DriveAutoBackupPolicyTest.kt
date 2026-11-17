package com.forgequest.app.data.drive

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.forgequest.app.data.backup.BackupFile
import com.forgequest.app.data.db.BodyMeasurementEntity
import com.forgequest.app.data.db.ExerciseEntity
import com.forgequest.app.data.db.PlanEntity
import com.forgequest.app.data.db.SessionEntity
import com.forgequest.app.data.prefs.Settings
import com.forgequest.app.data.prefs.SettingsRepository
import com.forgequest.app.domain.ExerciseKind
import com.forgequest.app.domain.Muscle
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class DriveAutoBackupPolicyTest {

    private fun emptyFile() = BackupFile(
        createdAt = 1L,
        exercises = listOf(ExerciseEntity("push_up", "Push-up", ExerciseKind.REPS, listOf(Muscle.CHEST), emptyList(), custom = false)),
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
    )

    @Test
    fun emptyDetectionIgnoresSeededCatalogAndDetectsMeaningfulUserData() {
        assertTrue(DriveConnectPolicy.isDeviceEmpty(emptyFile()))
        assertFalse(DriveConnectPolicy.isDeviceEmpty(emptyFile().copy(sessions = listOf(SessionEntity(1, null, "W", 1, 1, 2)))))
        assertFalse(DriveConnectPolicy.isDeviceEmpty(emptyFile().copy(plans = listOf(PlanEntity(1, "P", "", "manual", true, 1)))))
        assertFalse(DriveConnectPolicy.isDeviceEmpty(emptyFile().copy(bodyMeasurements = listOf(BodyMeasurementEntity(1, 1, "WAIST", 80f, 1)))))
        assertFalse(
            DriveConnectPolicy.isDeviceEmpty(
                emptyFile().copy(
                    exercises = emptyFile().exercises + ExerciseEntity("c1", "My Press", ExerciseKind.WEIGHTED, listOf(Muscle.CHEST), emptyList(), custom = true),
                ),
            ),
        )
    }

    @Test
    fun restoreOfferTriggersOncePerAccountWhenEmptyAndRemoteExists() {
        val local = emptyFile()
        assertFalse(DriveConnectPolicy.shouldOfferRestore(local, hasRemoteBackup = false, "User@Test.com", emptySet()))
        assertTrue(DriveConnectPolicy.shouldOfferRestore(local, hasRemoteBackup = true, "User@Test.com", emptySet()))
        assertFalse(DriveConnectPolicy.shouldOfferRestore(local, hasRemoteBackup = true, "user@test.com", setOf("user@test.com")))
    }

    @Test
    fun remoteNewerDetectsChangedModifiedTime() {
        assertFalse(DriveConnectPolicy.isRemoteNewer(null, "2026-11-10T00:00:00Z"))
        assertFalse(DriveConnectPolicy.isRemoteNewer("2026-11-10T00:00:00Z", "2026-11-10T00:00:00Z"))
        assertTrue(DriveConnectPolicy.isRemoteNewer("2026-11-10T00:00:00Z", "2026-11-11T00:00:00Z"))
    }

    @Test
    fun autoBackupEnableAndOptOutRememberedAcrossReconnects() = runBlocking {
        val repo = SettingsRepository(ApplicationProvider.getApplicationContext())
        // Default: autoBackup is true, but not effective until Drive connects.
        assertFalse(repo.current().autoBackupEffective)

        repo.update { it.copy(driveEmail = "u@example.com") }
        assertTrue(repo.current().autoBackupEffective)

        // Opt out
        repo.setAutoBackup(false)
        assertFalse(repo.current().autoBackupEffective)
        assertTrue(repo.current().autoBackupOptedOut)

        // Disconnect and reconnect: opt-out must stay remembered.
        repo.update { it.copy(driveEmail = "") }
        assertFalse(repo.current().autoBackupEffective)
        repo.update { it.copy(driveEmail = "u@example.com") }
        assertFalse(repo.current().autoBackupEffective)

        // Re-enable explicitly
        repo.setAutoBackup(true)
        assertTrue(repo.current().autoBackupEffective)
        assertEquals("u@example.com", repo.current().driveEmail)
    }
}
