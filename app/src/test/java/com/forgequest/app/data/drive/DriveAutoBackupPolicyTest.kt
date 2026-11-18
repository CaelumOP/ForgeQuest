package com.forgequest.app.data.drive

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.forgequest.app.data.WorkoutRepository
import com.forgequest.app.data.backup.BackupFile
import com.forgequest.app.data.backup.BackupRepository
import com.forgequest.app.data.backup.backupJson
import com.forgequest.app.data.db.AppDatabase
import com.forgequest.app.data.db.BodyMeasurementEntity
import com.forgequest.app.data.db.ExerciseEntity
import com.forgequest.app.data.db.PlanEntity
import com.forgequest.app.data.db.SessionEntity
import com.forgequest.app.data.prefs.Settings
import com.forgequest.app.data.prefs.SettingsRepository
import com.forgequest.app.domain.ExerciseKind
import com.forgequest.app.domain.Muscle
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
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
    fun deleteBackupHandlesAllPagesAndKeepsConnectionOnPartialFailure() = runBlocking {
        val ctx: Context = ApplicationProvider.getApplicationContext()
        val meta = ctx.getSharedPreferences("drive_sync_meta", Context.MODE_PRIVATE)
        meta.edit().clear().putString("mod_user@test.com", "old")
            .putString("mod_other@test.com", "other").commit()
        val db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).build()
        try {
            val settings = SettingsRepository(ctx)
            settings.update { it.copy(driveEmail = "user@test.com", lastBackupAt = 123L) }
            val backups = BackupRepository(ctx, db, settings, WorkoutRepository(db))
            val remaining = mutableSetOf("f1", "f2")
            var failDelete = true
            val deleted = mutableListOf<String>()
            val http = OkHttpClient.Builder().addInterceptor { chain ->
                val req = chain.request()
                var code = 200
                val body = when {
                    req.url.encodedPath.endsWith("/about") ->
                        """{"user":{"emailAddress":"user@test.com"}}"""
                    req.method == "GET" && req.url.encodedPath.endsWith("/files") -> {
                        assertEquals("appDataFolder", req.url.queryParameter("spaces"))
                        assertEquals("name='forgequest_backup.json' and trashed=false", req.url.queryParameter("q"))
                        if (req.url.queryParameter("pageToken") == "page 2") {
                            """{"files":[{"id":"f2"}]}"""
                        } else {
                            val files = if ("f1" in remaining) """{"id":"f1"}""" else ""
                            """{"files":[$files],"nextPageToken":"page 2"}"""
                        }
                    }
                    req.method == "DELETE" -> {
                        val id = req.url.pathSegments.last()
                        assertTrue(id in remaining)
                        if (id == "f2" && failDelete) code = 500 else {
                            remaining.remove(id)
                            deleted += id
                            code = 204
                        }
                        ""
                    }
                    else -> error("Unexpected request: ${req.method} ${req.url}")
                }
                Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(code).message("test")
                    .body(body.toResponseBody("application/json".toMediaType())).build()
            }.build()
            val drive = DriveSync(ctx, settings, backups, http)
            try {
                drive.deleteBackupAndDisconnect("tok")
                fail("Expected deletion failure")
            } catch (_: DriveError.Http) {
                assertEquals("user@test.com", settings.current().driveEmail)
                assertEquals(123L, settings.current().lastBackupAt)
                assertEquals("old", meta.getString("mod_user@test.com", null))
            }
            failDelete = false
            drive.deleteBackupAndDisconnect("tok")
            assertEquals(listOf("f1", "f2"), deleted)
            assertTrue(remaining.isEmpty())
            assertEquals("", settings.current().driveEmail)
            assertEquals(0L, settings.current().lastBackupAt)
            assertFalse(meta.contains("mod_user@test.com"))
            assertEquals("other", meta.getString("mod_other@test.com", null))
            assertFalse(drive.backup("tok"))
        } finally {
            db.close()
        }
    }

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
        assertTrue(DriveConnectPolicy.isRemoteNewer(null, "2026-11-10T00:00:00Z"))
        assertFalse(DriveConnectPolicy.isRemoteNewer("2026-11-10T00:00:00Z", "2026-11-10T00:00:00Z"))
        assertTrue(DriveConnectPolicy.isRemoteNewer("2026-11-10T00:00:00Z", "2026-11-11T00:00:00Z"))
        assertFalse(DriveConnectPolicy.isRemoteNewer(null, null))
        assertFalse(DriveConnectPolicy.needsRemoteChoice(hasRemoteBackup = false, null, null))
        assertTrue(DriveConnectPolicy.needsRemoteChoice(hasRemoteBackup = true, null, "2026-11-10T00:00:00Z"))
        assertFalse(DriveConnectPolicy.needsRemoteChoice(hasRemoteBackup = true, "2026-11-10T00:00:00Z", "2026-11-10T00:00:00Z"))
        assertTrue(DriveConnectPolicy.needsRemoteChoice(hasRemoteBackup = true, "2026-11-10T00:00:00Z", "2026-11-11T00:00:00Z"))
    }

    @Test
    fun fetchBackupDoesNotMarkRemoteSeenUntilMarkRestored() = runBlocking {
        val ctx: Context = ApplicationProvider.getApplicationContext()
        ctx.getSharedPreferences("drive_sync_meta", Context.MODE_PRIVATE).edit().clear().commit()
        val db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).build()
        try {
            db.plans().insertPlan(PlanEntity(name = "Starter", description = "", source = "built", active = true, createdAt = 1L))
            val settings = SettingsRepository(ctx)
            settings.update { it.copy(driveEmail = "user@test.com") }
            val backups = BackupRepository(ctx, db, settings, WorkoutRepository(db))
            val remoteJson = backupJson.encodeToString(BackupFile.serializer(), emptyFile())
            var uploadCount = 0
            val http = OkHttpClient.Builder().addInterceptor { chain ->
                val req = chain.request()
                val url = req.url.toString()
                val body = when {
                    req.method == "GET" && url.contains("/files?spaces=appDataFolder") ->
                        """{"files":[{"id":"f1","modifiedTime":"2026-11-17T10:00:00Z"}]}"""
                    req.method == "GET" && url.contains("/files/f1?alt=media") -> remoteJson
                    req.method == "PATCH" && url.contains("/upload/drive/v3/files/f1") -> {
                        uploadCount++
                        """{"id":"f1","modifiedTime":"2026-11-17T11:00:00Z"}"""
                    }
                    else -> error("Unexpected request: ${req.method} $url")
                }
                Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(body.toResponseBody("application/json".toMediaType()))
                    .build()
            }.build()
            val drive = DriveSync(ctx, settings, backups, http)

            // Preview fetch must NOT mark modifiedTime as seen.
            val fetched = drive.fetchBackup("tok")
            assertEquals("2026-11-17T10:00:00Z", fetched.modifiedTime)
            try {
                drive.backup("tok")
                fail("Expected RemoteNewer after preview-only fetch")
            } catch (_: DriveError.RemoteNewer) {
                // expected
            }
            assertEquals(0, uploadCount)

            // Confirming restore of a Drive backup marks it seen, allowing subsequent backups.
            drive.markRestored("user@test.com", fetched.modifiedTime)
            assertTrue(drive.backup("tok"))
            assertEquals(1, uploadCount)
        } finally {
            db.close()
        }
    }

    @Test
    fun connectWithLocalDataAndUnseenRemoteReturnsConflictWithoutUploading() = runBlocking {
        val ctx: Context = ApplicationProvider.getApplicationContext()
        ctx.getSharedPreferences("drive_sync_meta", Context.MODE_PRIVATE).edit().clear().commit()
        val db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).build()
        try {
            db.plans().insertPlan(PlanEntity(name = "Starter", description = "", source = "built", active = true, createdAt = 1L))
            val settings = SettingsRepository(ctx)
            val backups = BackupRepository(ctx, db, settings, WorkoutRepository(db))
            var uploadCount = 0
            val http = OkHttpClient.Builder().addInterceptor { chain ->
                val req = chain.request()
                val url = req.url.toString()
                val body = when {
                    req.method == "GET" && url.contains("/about") ->
                        """{"user":{"emailAddress":"user@test.com"}}"""
                    req.method == "GET" && url.contains("/files?spaces=appDataFolder") ->
                        """{"files":[{"id":"f1","modifiedTime":"2026-11-17T10:00:00Z"}]}"""
                    url.contains("/upload/") -> {
                        uploadCount++
                        "{}"
                    }
                    else -> error("Unexpected request: ${req.method} $url")
                }
                Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(body.toResponseBody("application/json".toMediaType()))
                    .build()
            }.build()
            val drive = DriveSync(ctx, settings, backups, http)

            val outcome = drive.connectAndInspect("tok")
            assertEquals(DriveConnectOutcome.Conflict("user@test.com", "f1", "2026-11-17T10:00:00Z"), outcome)
            assertEquals(0, uploadCount)
        } finally {
            db.close()
        }
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
