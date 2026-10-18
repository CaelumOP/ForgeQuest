package com.forgequest.app.data.drive

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.room.withTransaction
import com.forgequest.app.data.CoachRepository
import com.forgequest.app.data.WorkoutRepository
import com.forgequest.app.data.db.AppDatabase
import com.forgequest.app.data.db.BadgeEntity
import com.forgequest.app.data.db.ChatEntity
import com.forgequest.app.data.db.ExerciseEntity
import com.forgequest.app.data.db.FoodEntity
import com.forgequest.app.data.db.MealEntity
import com.forgequest.app.data.db.PlanDayEntity
import com.forgequest.app.data.db.PlanEntity
import com.forgequest.app.data.db.PlanItemEntity
import com.forgequest.app.data.db.SessionEntity
import com.forgequest.app.data.db.SetEntity
import com.forgequest.app.data.db.WeightEntity
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
import kotlinx.serialization.Serializable
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

@Serializable
data class BackupFile(
    val version: Int = 1,
    val createdAt: Long,
    val exercises: List<ExerciseEntity>,
    val plans: List<PlanEntity>,
    val planDays: List<PlanDayEntity>,
    val planItems: List<PlanItemEntity>,
    val sessions: List<SessionEntity>,
    val sets: List<SetEntity>,
    val foods: List<FoodEntity>,
    val meals: List<MealEntity>,
    val weights: List<WeightEntity>,
    val chat: List<ChatEntity>,
    val badges: List<BadgeEntity>,
)

sealed class DriveError(val userMessage: String) : Exception(userMessage) {
    object NotConfigured : DriveError("Google Drive isn't set up for this copy of the app yet.")
    object NeedsConsent : DriveError("Connect Google Drive in Profile first.")
    object Offline : DriveError("No internet connection.")
    object NoDoc : DriveError("No Google Doc with \"Workout Plan\" in its name was found in your Drive.")
    object NoBackup : DriveError("No backup found in your Google Drive yet.")
    class Http(code: Int) : DriveError("Google Drive returned an error ($code). Try again later.")
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
    private val db: AppDatabase,
    private val settings: SettingsRepository,
    private val workouts: WorkoutRepository,
    private val coach: CoachRepository,
) {
    private val http = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val scopes = listOf(
        Scope("https://www.googleapis.com/auth/drive.appdata"),
        Scope("https://www.googleapis.com/auth/drive.readonly"),
    )

    suspend fun authorize(): DriveAuth {
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
    suspend fun silentToken(): String? = runCatching { (authorize() as? DriveAuth.Token)?.accessToken }.getOrNull()

    suspend fun accountEmail(token: String): String = withContext(Dispatchers.IO) {
        val body = call(token, Request.Builder().url("$API/about?fields=user(emailAddress)").get())
        json.parseToJsonElement(body).jsonObject["user"]?.jsonObject?.get("emailAddress")?.jsonPrimitive?.contentOrNull.orEmpty()
    }

    // ------------------------------------------------------------ Backup

    suspend fun backup(token: String) = withContext(Dispatchers.IO) {
        val b = db.backup()
        val file = BackupFile(
            createdAt = System.currentTimeMillis(),
            exercises = b.exercises(), plans = b.plans(), planDays = b.planDays(), planItems = b.planItems(),
            sessions = b.sessions(), sets = b.sets(), foods = b.foods(), meals = b.meals(), weights = b.weights(),
            chat = b.chat(), badges = b.badges(),
        )
        val content = json.encodeToString(BackupFile.serializer(), file)
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

    suspend fun restore(token: String) = withContext(Dispatchers.IO) {
        val id = findBackupId(token) ?: throw DriveError.NoBackup
        val body = call(token, Request.Builder().url("$API/files/$id?alt=media").get())
        val f = json.decodeFromString(BackupFile.serializer(), body)
        db.withTransaction {
            db.backup().replaceAll(
                f.exercises, f.plans, f.planDays, f.planItems, f.sessions, f.sets,
                // Photos aren't part of the backup, so drop paths that don't exist on this phone.
                f.foods.map { it.copy(photoPath = it.photoPath?.takeIf { p -> java.io.File(p).exists() }) },
                f.meals.map { it.copy(photoPath = it.photoPath?.takeIf { p -> java.io.File(p).exists() }) },
                f.weights, f.chat, f.badges,
            )
        }
        workouts.seedLibrary()
    }

    private fun findBackupId(token: String): String? {
        val body = call(token, Request.Builder().url("$API/files?spaces=appDataFolder&q=name%3D%27$BACKUP_NAME%27&fields=files(id)").get())
        return json.parseToJsonElement(body).jsonObject["files"]?.jsonArray?.firstOrNull()?.jsonObject?.get("id")?.jsonPrimitive?.contentOrNull
    }

    // ------------------------------------------------------------ Docs import

    data class DocPlan(val docName: String, val modifiedAt: Long, val plan: PlanDraft)

    /** Finds the newest "Workout Plan" Google Doc. Returns null when it hasn't changed since [sinceMillis]. */
    suspend fun fetchPlanDoc(token: String, sinceMillis: Long = 0): DocPlan? = withContext(Dispatchers.IO) {
        val q = "mimeType='application/vnd.google-apps.document' and name contains 'Workout Plan' and trashed=false"
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
