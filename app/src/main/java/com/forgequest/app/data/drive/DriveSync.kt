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
    private val http: OkHttpClient = OkHttpClient(),
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /**
     * One sign-in covers everything, and both scopes are Google's low-risk ones (no security review, so the OAuth
     * app can be published for anyone): the hidden backup folder, and Docs the app creates itself ("ForgeQuest data",
     * "ForgeQuest from Gemini", "ForgeQuest plan"). The app never sees the person's other files.
     */
    private val backupScope = Scope("https://www.googleapis.com/auth/drive.appdata")
    private val fileScope = Scope("https://www.googleapis.com/auth/drive.file")

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
    suspend fun silentToken(): String? = com.forgequest.app.data.suspendRunCatching { (authorize() as? DriveAuth.Token)?.accessToken }.getOrNull()

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

    /**
     * Reads the app's "ForgeQuest plan" Google Doc. The first time, the app creates it with a short how-to and
     * reports [DriveError.NoDoc] so the person knows where to write the plan. Null when unchanged since [sinceMillis].
     */
    suspend fun fetchPlanDoc(token: String, sinceMillis: Long = 0): DocPlan? = withContext(Dispatchers.IO) {
        val q = "mimeType='application/vnd.google-apps.document' and name='$PLAN_DOC' and trashed=false"
        val url = "$API/files?q=${java.net.URLEncoder.encode(q, "UTF-8")}&orderBy=modifiedTime%20desc&pageSize=1&fields=files(id,name,modifiedTime)"
        val body = call(token, Request.Builder().url(url).get())
        val doc = json.parseToJsonElement(body).jsonObject["files"]?.jsonArray?.firstOrNull()?.jsonObject
        if (doc == null) {
            upsertTextDoc(token, PLAN_DOC, context.getString(R.string.drive_plan_doc_template), null)
            throw DriveError.NoDoc
        }
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

    // ------------------------------------------------------------ Gemini bridge

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

    /**
     * Text and modified time of the app-created Doc named [name], or null if none is newer than [sinceMillis]. When
     * the Doc doesn't exist yet it is created with [placeholder], so another app (Gemini) can fill it in and this one
     * can still read it with the file-only scope.
     */
    suspend fun newestDoc(token: String, name: String, sinceMillis: Long, placeholder: String? = null): Pair<String, Long>? = withContext(Dispatchers.IO) {
        val q = "mimeType='application/vnd.google-apps.document' and name='$name' and trashed=false"
        val url = "$API/files?q=${java.net.URLEncoder.encode(q, "UTF-8")}&orderBy=modifiedTime%20desc&pageSize=1&fields=files(id,modifiedTime)"
        val doc = json.parseToJsonElement(call(token, Request.Builder().url(url).get())).jsonObject["files"]?.jsonArray?.firstOrNull()?.jsonObject
        if (doc == null) {
            if (placeholder != null) upsertTextDoc(token, name, placeholder, null)
            return@withContext null
        }
        val modified = doc["modifiedTime"]?.jsonPrimitive?.contentOrNull?.let { Instant.parse(it).toEpochMilli() } ?: 0L
        if (modified <= sinceMillis) return@withContext null
        call(token, Request.Builder().url("$API/files/${doc["id"]!!.jsonPrimitive.content}/export?mimeType=text/plain").get()) to modified
    }

    data class DocText(val id: String, val text: String, val modifiedAt: Long)

    /** Like [newestDoc], with the Doc's id so the caller can rewrite it. */
    suspend fun readDoc(token: String, name: String, sinceMillis: Long, placeholder: String): DocText? = withContext(Dispatchers.IO) {
        val q = "mimeType='application/vnd.google-apps.document' and name='$name' and trashed=false"
        val url = "$API/files?q=${java.net.URLEncoder.encode(q, "UTF-8")}&orderBy=modifiedTime%20desc&pageSize=1&fields=files(id,modifiedTime)"
        val doc = json.parseToJsonElement(call(token, Request.Builder().url(url).get())).jsonObject["files"]?.jsonArray?.firstOrNull()?.jsonObject
        if (doc == null) {
            upsertTextDoc(token, name, placeholder, null)
            return@withContext null
        }
        val modified = doc["modifiedTime"]?.jsonPrimitive?.contentOrNull?.let { Instant.parse(it).toEpochMilli() } ?: 0L
        if (modified <= sinceMillis) return@withContext null
        val id = doc["id"]!!.jsonPrimitive.content
        DocText(id, call(token, Request.Builder().url("$API/files/$id/export?mimeType=text/plain").get()), modified)
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
        const val PLAN_DOC = "ForgeQuest plan"
    }
}
