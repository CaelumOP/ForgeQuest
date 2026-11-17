package com.forgequest.app.data.drive

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.forgequest.app.R
import com.forgequest.app.data.backup.BackupFile
import com.forgequest.app.data.backup.BackupRepository
import com.forgequest.app.data.prefs.SettingsRepository
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.time.Instant
import java.util.Locale

sealed class DriveError(res: Int, vararg args: Any) : com.forgequest.app.LocalizedException(res, *args) {
    object NotConfigured : DriveError(R.string.drive_err_not_configured)
    object NeedsConsent : DriveError(R.string.drive_err_needs_consent)
    object Offline : DriveError(R.string.drive_err_offline)
    object NoBackup : DriveError(R.string.drive_err_no_backup)
    object RemoteNewer : DriveError(R.string.prog41_drive_remote_newer_title)
    class Http(code: Int) : DriveError(R.string.drive_err_http, code)
}

/** Outcome of asking Google for Drive access. */
sealed interface DriveAuth {
    data class Token(val accessToken: String) : DriveAuth
    data class NeedsUi(val intent: PendingIntent) : DriveAuth
}

/** Outcome of connecting Drive and inspecting remote backup vs local state. */
sealed interface DriveConnectOutcome {
    data class OfferRestore(val email: String, val file: BackupFile, val modifiedTime: String? = null) : DriveConnectOutcome
    data class Conflict(val email: String, val fileId: String, val modifiedTime: String?) : DriveConnectOutcome
    data class Connected(val email: String, val backedUp: Boolean, val autoBackupOn: Boolean) : DriveConnectOutcome
}

data class FetchedBackup(val file: BackupFile, val modifiedTime: String?)

/** Pure policy helpers for Drive backup/restore decisions (unit-tested without network). */
object DriveConnectPolicy {
    /** True when the device holds no meaningful user data (ignores seeded built-in exercises). */
    fun isDeviceEmpty(file: BackupFile): Boolean =
        file.sessions.isEmpty() &&
            file.meals.isEmpty() &&
            file.weights.isEmpty() &&
            file.plans.isEmpty() &&
            file.foods.isEmpty() &&
            file.bodyMeasurements.isEmpty() &&
            file.exercises.none { it.custom }

    fun normalizeAccountKey(email: String): String =
        email.trim().lowercase(Locale.ROOT).ifBlank { "connected" }

    fun shouldOfferRestore(
        local: BackupFile,
        hasRemoteBackup: Boolean,
        email: String,
        restoreOfferShown: Set<String>,
    ): Boolean =
        hasRemoteBackup &&
            isDeviceEmpty(local) &&
            normalizeAccountKey(email) !in restoreOfferShown

    fun isRemoteNewer(knownModifiedTime: String?, remoteModifiedTime: String?): Boolean {
        if (remoteModifiedTime.isNullOrBlank()) return false
        return knownModifiedTime.isNullOrBlank() || remoteModifiedTime != knownModifiedTime
    }

    fun needsRemoteChoice(hasRemoteBackup: Boolean, knownModifiedTime: String?, remoteModifiedTime: String?): Boolean =
        hasRemoteBackup &&
            (knownModifiedTime.isNullOrBlank() || remoteModifiedTime.isNullOrBlank() || remoteModifiedTime != knownModifiedTime)
}

/**
 * Google Drive backup and the app-created meals Doc over the Drive REST API.
 * Backups live in the hidden per-app folder (`drive.appdata`); Meals Doc keeps `drive.file`.
 */
class DriveSync(
    private val context: Context,
    private val settings: SettingsRepository,
    private val backups: BackupRepository,
    private val http: OkHttpClient = OkHttpClient(),
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val syncMutex = Mutex()
    private val metaPrefs by lazy { context.getSharedPreferences("drive_sync_meta", Context.MODE_PRIVATE) }

    private val backupScope = Scope("https://www.googleapis.com/auth/drive.appdata")
    private val fileScope = Scope("https://www.googleapis.com/auth/drive.file")

    suspend fun <T> withSyncLock(block: suspend () -> T): T = syncMutex.withLock { block() }

    suspend fun authorize(): DriveAuth {
        val scopes = listOf(backupScope, fileScope)
        val request = AuthorizationRequest.builder().setRequestedScopes(scopes).build()
        val result = try {
            Identity.getAuthorizationClient(context).authorize(request).await()
        } catch (e: ApiException) {
            throw DriveError.NotConfigured
        }
        return result.toAuth()
    }

    fun tokenFromConsent(data: Intent?): String {
        val result = try {
            Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data)
        } catch (e: ApiException) {
            throw DriveError.NotConfigured
        }
        return (result.toAuth() as? DriveAuth.Token)?.accessToken ?: throw DriveError.NeedsConsent
    }

    private fun AuthorizationResult.toAuth(): DriveAuth {
        val pending = pendingIntent
        return if (hasResolution() && pending != null) DriveAuth.NeedsUi(pending)
        else DriveAuth.Token(accessToken ?: throw DriveError.NeedsConsent)
    }

    /** Silent token for background work; null when the user has to approve something first. */
    suspend fun silentToken(): String? =
        com.forgequest.app.data.suspendRunCatching { (authorize() as? DriveAuth.Token)?.accessToken }.getOrNull()

    suspend fun accountEmail(token: String): String = withContext(Dispatchers.IO) {
        val body = call(token, Request.Builder().url("$API/about?fields=user(emailAddress)").get())
        json.parseToJsonElement(body).jsonObject["user"]?.jsonObject?.get("emailAddress")?.jsonPrimitive?.contentOrNull.orEmpty()
    }

    // ------------------------------------------------------------ Backup

    private data class RemoteFileInfo(val id: String, val modifiedTime: String?)

    private fun knownModified(accountKey: String): String? =
        metaPrefs.getString("mod_$accountKey", null)

    private fun saveKnownModified(accountKey: String, modifiedTime: String?) {
        if (modifiedTime.isNullOrBlank()) return
        metaPrefs.edit().putString("mod_$accountKey", modifiedTime).apply()
    }

    fun markRestored(accountKey: String, modifiedTime: String?) {
        saveKnownModified(DriveConnectPolicy.normalizeAccountKey(accountKey), modifiedTime)
    }

    /**
     * Connect flow: record account, inspect Drive backup, offer one-tap restore once per account/device if
     * this device is empty of meaningful user data, surface a conflict if an unseen remote backup exists, and
     * only otherwise upload (when autoBackupEffective is on).
     */
    suspend fun connectAndInspect(token: String): DriveConnectOutcome = syncMutex.withLock {
        val email = accountEmail(token)
        val accountKey = DriveConnectPolicy.normalizeAccountKey(email)
        val shownEmail = email.ifBlank { context.getString(R.string.drive_connected) }
        settings.update { it.copy(driveEmail = shownEmail) }
        val currentSettings = settings.current()
        val local = backups.build()
        val existing = withContext(Dispatchers.IO) { findBackupInfo(token) }

        if (DriveConnectPolicy.shouldOfferRestore(local, existing != null, accountKey, currentSettings.restoreOfferShown)) {
            settings.markRestoreOfferShown(accountKey)
            val remote = withContext(Dispatchers.IO) { fetchBackupById(token, existing!!.id) }
            return@withLock DriveConnectOutcome.OfferRestore(shownEmail, remote, existing?.modifiedTime)
        }

        // Only when an upload would follow: never overwrite a remote backup this device hasn't seen without asking.
        if (existing != null && currentSettings.autoBackupEffective &&
            DriveConnectPolicy.needsRemoteChoice(true, knownModified(accountKey), existing.modifiedTime)) {
            return@withLock DriveConnectOutcome.Conflict(shownEmail, existing.id, existing.modifiedTime)
        }

        if (currentSettings.autoBackupEffective) {
            val backedUp = backupInternal(token, accountKey, forceOverwrite = false)
            return@withLock DriveConnectOutcome.Connected(shownEmail, backedUp, autoBackupOn = true)
        }
        DriveConnectOutcome.Connected(shownEmail, backedUp = false, autoBackupOn = false)
    }

    suspend fun backup(token: String, forceOverwrite: Boolean = false): Boolean = syncMutex.withLock {
        val accountKey = DriveConnectPolicy.normalizeAccountKey(settings.current().driveEmail)
        backupInternal(token, accountKey, forceOverwrite)
    }

    private suspend fun backupInternal(
        token: String,
        accountKey: String,
        forceOverwrite: Boolean,
    ): Boolean = withContext(Dispatchers.IO) {
        val file = backups.build()
        val existing = findBackupInfo(token)
        if (existing != null && DriveConnectPolicy.isDeviceEmpty(file)) return@withContext false
        if (!forceOverwrite && DriveConnectPolicy.needsRemoteChoice(existing != null, knownModified(accountKey), existing?.modifiedTime)) {
            throw DriveError.RemoteNewer
        }
        val content = backups.encode(file)
        val metadata = if (existing == null) """{"name":"$BACKUP_NAME","parents":["appDataFolder"]}""" else """{"name":"$BACKUP_NAME"}"""
        val multipart = MultipartBody.Builder().setType("multipart/related".toMediaType())
            .addPart(metadata.toRequestBody("application/json; charset=UTF-8".toMediaType()))
            .addPart(content.toRequestBody("application/json".toMediaType()))
            .build()
        val request = if (existing == null) {
            Request.Builder().url("$UPLOAD/files?uploadType=multipart&fields=id,modifiedTime").post(multipart)
        } else {
            Request.Builder().url("$UPLOAD/files/${existing.id}?uploadType=multipart&fields=id,modifiedTime").patch(multipart)
        }
        val resp = call(token, request)
        val mod = runCatching {
            json.parseToJsonElement(resp).jsonObject["modifiedTime"]?.jsonPrimitive?.contentOrNull
        }.getOrNull()
        saveKnownModified(accountKey, mod)
        settings.update { it.copy(lastBackupAt = System.currentTimeMillis()) }
        true
    }

    /** Downloads the Drive backup without applying it or marking it seen, so previewing never overwrites later. */
    suspend fun fetchBackup(token: String): FetchedBackup = syncMutex.withLock {
        withContext(Dispatchers.IO) {
            val info = findBackupInfo(token) ?: throw DriveError.NoBackup
            FetchedBackup(fetchBackupById(token, info.id), info.modifiedTime)
        }
    }

    private fun fetchBackupById(token: String, id: String): BackupFile =
        backups.decode(call(token, Request.Builder().url("$API/files/$id?alt=media").get()))

    private fun findBackupInfo(token: String): RemoteFileInfo? {
        val body = call(
            token,
            Request.Builder().url("$API/files?spaces=appDataFolder&q=name%3D%27$BACKUP_NAME%27&fields=files(id,modifiedTime)").get(),
        )
        val obj = json.parseToJsonElement(body).jsonObject["files"]?.jsonArray?.firstOrNull()?.jsonObject ?: return null
        val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: return null
        val mod = obj["modifiedTime"]?.jsonPrimitive?.contentOrNull
        return RemoteFileInfo(id, mod)
    }

    // ------------------------------------------------------------ App-created Docs

    /** Creates the named Google Doc from plain text, or replaces the text of [fileId]. Returns the doc's id. */
    suspend fun upsertTextDoc(token: String, name: String, text: String, fileId: String?): String = withContext(Dispatchers.IO) {
        if (fileId != null) {
            try {
                call(token, Request.Builder().url("$UPLOAD/files/$fileId?uploadType=media").patch(text.toRequestBody("text/plain; charset=UTF-8".toMediaType())))
                return@withContext fileId
            } catch (e: DriveError.Http) {
                // Deleted or no longer ours: make a new one below.
            }
        }
        val metadata = """{"name":"$name","mimeType":"application/vnd.google-apps.document"}"""
        val multipart = MultipartBody.Builder().setType("multipart/related".toMediaType())
            .addPart(metadata.toRequestBody("application/json; charset=UTF-8".toMediaType()))
            .addPart(text.toRequestBody("text/plain; charset=UTF-8".toMediaType()))
            .build()
        val body = call(token, Request.Builder().url("$UPLOAD/files?uploadType=multipart&fields=id").post(multipart))
        json.parseToJsonElement(body).jsonObject["id"]!!.jsonPrimitive.content
    }

    data class DocText(val id: String, val text: String, val modifiedAt: Long)

    /**
     * Id, text and modified time of the app-created Doc named [name], or null if none is newer than [sinceMillis]. When
     * the Doc doesn't exist yet it is created with [placeholder], so another app (Gemini) can fill it in and this one
     * can still read it with the file-only scope.
     */
    suspend fun readDoc(token: String, name: String, sinceMillis: Long, placeholder: String): DocText? = withContext(Dispatchers.IO) {
        findNewestDoc(token, name, sinceMillis, placeholder)?.let { DocText(it.id, it.text, it.modifiedAt) }
    }

    private class FoundDoc(val id: String, val name: String?, val text: String, val modifiedAt: Long)

    private suspend fun findNewestDoc(token: String, name: String, sinceMillis: Long, placeholder: String?, onMissing: () -> Unit = {}): FoundDoc? {
        val q = "mimeType='application/vnd.google-apps.document' and name='$name' and trashed=false"
        val url = "$API/files?q=${java.net.URLEncoder.encode(q, "UTF-8")}&orderBy=modifiedTime%20desc&pageSize=1&fields=files(id,name,modifiedTime)"
        val doc = json.parseToJsonElement(call(token, Request.Builder().url(url).get())).jsonObject["files"]?.jsonArray?.firstOrNull()?.jsonObject
        if (doc == null) {
            if (placeholder != null) upsertTextDoc(token, name, placeholder, null)
            onMissing()
            return null
        }
        val modified = doc["modifiedTime"]?.jsonPrimitive?.contentOrNull?.let { Instant.parse(it).toEpochMilli() } ?: 0L
        if (modified <= sinceMillis) return null
        val id = doc["id"]!!.jsonPrimitive.content
        val text = call(token, Request.Builder().url("$API/files/$id/export?mimeType=text/plain").get())
        return FoundDoc(id, doc["name"]?.jsonPrimitive?.contentOrNull, text, modified)
    }

    private fun call(token: String, builder: Request.Builder): String {
        val response = try {
            http.newCall(builder.header("Authorization", "Bearer $token").build()).execute()
        } catch (e: IOException) {
            throw DriveError.Offline
        }
        response.use { r ->
            val text = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw if (r.code == 401 || r.code == 403) DriveError.NeedsConsent else DriveError.Http(r.code)
            return text
        }
    }

    private companion object {
        const val API = "https://www.googleapis.com/drive/v3"
        const val UPLOAD = "https://www.googleapis.com/upload/drive/v3"
        const val BACKUP_NAME = "forgequest_backup.json"
    }
}
