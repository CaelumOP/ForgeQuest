package com.forgequest.app.data.drive

import com.forgequest.app.R

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.forgequest.app.data.CoachRepository
import com.forgequest.app.data.WorkoutRepository
import com.forgequest.app.data.backup.BackupFile
import com.forgequest.app.data.backup.BackupRepository
import com.forgequest.app.data.plan.PlanDraft
import com.forgequest.app.data.prefs.SettingsRepository
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
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

sealed class DriveError(res: Int, vararg args: Any) : com.forgequest.app.LocalizedException(res, *args) {
    object NotConfigured : DriveError(R.string.drive_err_not_configured)
    object NeedsConsent : DriveError(R.string.drive_err_needs_consent)
    object Offline : DriveError(R.string.drive_err_offline)
    object NoDoc : DriveError(R.string.drive_err_no_doc)
    object NoBackup : DriveError(R.string.drive_err_no_backup)
    class Http(code: Int) : DriveError(R.string.drive_err_http, code)
}

/** Outcome of asking Google for Drive access. */
sealed interface DriveAuth {
    data class Token(val accessToken: String) : DriveAuth
    data class NeedsUi(val intent: PendingIntent) : DriveAuth
}

/**
 * Google Drive backup and Docs import over the Drive REST API.
 * Backups live in the hidden per-app folder; the plan import reads the user's newest "Workout Plan" doc.
 */
class DriveSync(
    private val context: Context,
    private val settings: SettingsRepository,
    private val workouts: WorkoutRepository,
    private val coach: CoachRepository,
    private val backups: BackupRepository,
) {
    private val http = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /** Backup only touches the app's own hidden folder. Reading the person's documents is a separate, opt-in grant. */
    private val backupScope = Scope("https://www.googleapis.com/auth/drive.appdata")
    private val docsScope = Scope("https://www.googleapis.com/auth/drive.readonly")

    suspend fun authorize(withDocs: Boolean = false): DriveAuth {
        val scopes = if (withDocs) listOf(backupScope, docsScope) else listOf(backupScope)
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
    suspend fun silentToken(withDocs: Boolean = false): String? = runCatching { (authorize(withDocs) as? DriveAuth.Token)?.accessToken }.getOrNull()

    suspend fun accountEmail(token: String): String = withContext(Dispatchers.IO) {
        val body = call(token, Request.Builder().url("$API/about?fields=user(emailAddress)").get())
        json.parseToJsonElement(body).jsonObject["user"]?.jsonObject?.get("emailAddress")?.jsonPrimitive?.contentOrNull.orEmpty()
    }

    // ------------------------------------------------------------ Backup

    suspend fun backup(token: String) = withContext(Dispatchers.IO) {
        val content = backups.encode(backups.build())
        val existing = findBackupId(token)
        val metadata = if (existing == null) """{"name":"$BACKUP_NAME","parents":["appDataFolder"]}""" else """{"name":"$BACKUP_NAME"}"""
        val multipart = MultipartBody.Builder().setType("multipart/related".toMediaType())
            .addPart(metadata.toRequestBody("application/json; charset=UTF-8".toMediaType()))
            .addPart(content.toRequestBody("application/json".toMediaType()))
            .build()
        val request = if (existing == null) {
            Request.Builder().url("$UPLOAD/files?uploadType=multipart").post(multipart)
        } else {
            Request.Builder().url("$UPLOAD/files/$existing?uploadType=multipart").patch(multipart)
        }
        call(token, request)
        settings.update { it.copy(lastBackupAt = System.currentTimeMillis()) }
    }

    /** Downloads the Drive backup without applying it, so the person can read what's in it first. */
    suspend fun fetchBackup(token: String): BackupFile = withContext(Dispatchers.IO) {
        val id = findBackupId(token) ?: throw DriveError.NoBackup
        backups.decode(call(token, Request.Builder().url("$API/files/$id?alt=media").get()))
    }

    suspend fun restore(token: String) = backups.apply(fetchBackup(token))

    private fun findBackupId(token: String): String? {
        val body = call(token, Request.Builder().url("$API/files?spaces=appDataFolder&q=name%3D%27$BACKUP_NAME%27&fields=files(id)").get())
        return json.parseToJsonElement(body).jsonObject["files"]?.jsonArray?.firstOrNull()?.jsonObject?.get("id")?.jsonPrimitive?.contentOrNull
    }

    // ------------------------------------------------------------ Docs import

    data class DocPlan(val docName: String, val modifiedAt: Long, val plan: PlanDraft)

    /** Finds the newest "Workout Plan" Google Doc. Returns null when it hasn't changed since [sinceMillis]. */
    suspend fun fetchPlanDoc(token: String, sinceMillis: Long = 0): DocPlan? = withContext(Dispatchers.IO) {
        // Only the person's own docs: a file someone shares with them must never replace their plan.
        val q = "mimeType='application/vnd.google-apps.document' and name contains 'Workout Plan' and trashed=false and 'me' in owners"
        val url = "$API/files?q=${java.net.URLEncoder.encode(q, "UTF-8")}&orderBy=modifiedTime%20desc&pageSize=1&fields=files(id,name,modifiedTime)"
        val body = call(token, Request.Builder().url(url).get())
        val doc = json.parseToJsonElement(body).jsonObject["files"]?.jsonArray?.firstOrNull()?.jsonObject ?: throw DriveError.NoDoc
        val id = doc["id"]!!.jsonPrimitive.content
        val name = doc["name"]?.jsonPrimitive?.contentOrNull ?: "Workout Plan"
        val modified = doc["modifiedTime"]?.jsonPrimitive?.contentOrNull?.let { Instant.parse(it).toEpochMilli() } ?: 0L
        if (modified <= sinceMillis) return@withContext null
        val text = call(token, Request.Builder().url("$API/files/$id/export?mimeType=text/plain").get())
        DocPlan(name, modified, coach.parsePlanText(text))
    }

    suspend fun applyDocPlan(doc: DocPlan) {
        workouts.applyPlan(doc.plan, "docs")
        settings.update { it.copy(lastDocImportModified = doc.modifiedAt) }
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
