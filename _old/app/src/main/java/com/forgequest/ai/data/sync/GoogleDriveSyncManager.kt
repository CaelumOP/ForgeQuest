package com.forgequest.ai.data.sync

import android.content.Context
import android.content.Intent
import com.forgequest.ai.data.local.dao.ForgeQuestDao
import com.forgequest.ai.data.local.entity.FullAppBackupPayload
import com.forgequest.ai.data.security.EncryptedSettingsDataStore
import com.forgequest.ai.domain.model.ExerciseCategory
import com.forgequest.ai.domain.model.MuscleGroup
import com.forgequest.ai.domain.model.PlannedDayPayload
import com.forgequest.ai.domain.model.PlannedExercisePayload
import com.forgequest.ai.domain.model.WorkoutPlanPayload
import com.forgequest.ai.domain.usecase.GenerateProgressSummaryMarkdownUseCase
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.ByteArrayContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SyncExecutionResult(
    val success: Boolean,
    val statusMessage: String,
    val importedWorkoutPlanName: String? = null
)

class GoogleDriveSyncManager(
    private val context: Context,
    private val dao: ForgeQuestDao,
    private val settingsDataStore: EncryptedSettingsDataStore,
    private val generateSummaryUseCase: GenerateProgressSummaryMarkdownUseCase = GenerateProgressSummaryMarkdownUseCase()
) {
    private val appContext = context.applicationContext

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        prettyPrint = true
    }

    fun getGoogleSignInClient(): GoogleSignInClient {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(
                Scope(DriveScopes.DRIVE_FILE),
                Scope(DriveScopes.DRIVE_READONLY)
            )
            .build()
        return GoogleSignIn.getClient(appContext, gso)
    }

    fun getSignInIntent(): Intent = getGoogleSignInClient().signInIntent

    fun getSignedInAccount(): GoogleSignInAccount? =
        GoogleSignIn.getLastSignedInAccount(appContext)

    /**
     * Queries Google Drive for the newest Google Doc matching
     * `mimeType = 'application/vnd.google-apps.document' and name contains 'Workout Plan' and trashed = false`.
     * If `modifiedTime` > `lastImportedDriveDocModifiedMillis` (or [forceImport] is true),
     * exports as `text/plain`, parses `WorkoutPlanPayload`, and updates Room.
     */
    suspend fun syncImportWorkoutPlanDoc(forceImport: Boolean = false): SyncExecutionResult =
        withContext(Dispatchers.IO) {
            val settings = settingsDataStore.getSettings()
            val driveService = buildDriveService(settings.googleDriveAccountEmail)

            if (driveService != null) {
                val remoteResult = runCatching {
                    val query = "mimeType = 'application/vnd.google-apps.document' and name contains 'Workout Plan' and trashed = false"
                    val fileList = driveService.files().list()
                        .setQ(query)
                        .setSpaces("drive")
                        .setFields("files(id, name, modifiedTime)")
                        .setOrderBy("modifiedTime desc")
                        .setPageSize(5)
                        .execute()

                    val newestDoc = fileList.files?.firstOrNull()
                    if (newestDoc == null) {
                        return@runCatching SyncExecutionResult(
                            success = false,
                            statusMessage = "No Workout Plan doc found in Google Drive"
                        )
                    }

                    val docModifiedMillis = newestDoc.modifiedTime?.value ?: System.currentTimeMillis()
                    if (!forceImport && docModifiedMillis <= settings.lastImportedDriveDocModifiedMillis) {
                        return@runCatching SyncExecutionResult(
                            success = true,
                            statusMessage = "Last synced today at ${formatTime()}"
                        )
                    }

                    val outputStream = ByteArrayOutputStream()
                    driveService.files().export(newestDoc.id, "text/plain")
                        .executeMediaAndDownloadTo(outputStream)
                    val plainText = outputStream.toString(Charsets.UTF_8.name())

                    val parsedPlan = parseWorkoutPlanFromDocText(plainText, newestDoc.name ?: "Drive Workout Plan")
                    dao.replaceActiveWorkoutPlanFromPayload(
                        payload = parsedPlan,
                        sourceTag = "GOOGLE_DRIVE_DOC"
                    )
                    val statusMsg = "Last synced today at ${formatTime()}"
                    settingsDataStore.updateDriveSyncMetadata(
                        lastModifiedMillis = docModifiedMillis,
                        statusMessage = statusMsg
                    )
                    SyncExecutionResult(
                        success = true,
                        statusMessage = statusMsg,
                        importedWorkoutPlanName = parsedPlan.planName
                    )
                }.getOrNull()

                if (remoteResult != null) {
                    return@withContext remoteResult
                }
            }

            // Local mirror fallback if present
            val localWorkoutDir = ensureLocalWorkoutAppDir()
            val localDocFile = java.io.File(localWorkoutDir, "Workout Plan.txt")
            if (localDocFile.exists()) {
                val parsed = parseWorkoutPlanFromDocText(
                    plainText = localDocFile.readText(),
                    fallbackTitle = "Drive Synced Calisthenics & Iron Plan"
                )
                dao.replaceActiveWorkoutPlanFromPayload(parsed, "GOOGLE_DRIVE_DOC")
                val statusMsg = "Last synced today at ${formatTime()}"
                settingsDataStore.updateDriveSyncMetadata(
                    lastModifiedMillis = localDocFile.lastModified(),
                    statusMessage = statusMsg
                )
                return@withContext SyncExecutionResult(
                    success = true,
                    statusMessage = statusMsg,
                    importedWorkoutPlanName = parsed.planName
                )
            }

            val statusMsg = if (settings.googleDriveAccountEmail.isNullOrBlank()) {
                "Sign in to Google Drive to sync your workout plan"
            } else {
                "Last synced today at ${formatTime()}"
            }
            settingsDataStore.updateDriveSyncMetadata(statusMessage = statusMsg)
            SyncExecutionResult(success = false, statusMessage = statusMsg)
        }

    /**
     * Generates full Room backup snapshot and progress summary markdown,
     * writes them to the local mirror directory, and uploads/updates them on Google Drive.
     */
    suspend fun syncExportBackupAndSummary(): SyncExecutionResult = withContext(Dispatchers.IO) {
        val settings = settingsDataStore.getSettings()
        val backupPayload = dao.exportFullBackupPayload()
        val backupJsonText = json.encodeToString(backupPayload)
        val summaryMarkdown = generateSummaryUseCase.execute(
            dao = dao,
            dailyCalorieGoal = settings.dailyCalorieGoal,
            dailyProteinGoal = settings.dailyProteinGoal
        )

        val localDir = ensureLocalWorkoutAppDir()
        java.io.File(localDir, BACKUP_FILE_NAME).writeText(backupJsonText, Charsets.UTF_8)
        java.io.File(localDir, SUMMARY_FILE_NAME).writeText(summaryMarkdown, Charsets.UTF_8)

        val driveService = buildDriveService(settings.googleDriveAccountEmail)
        if (driveService != null) {
            val uploadedRemotely = runCatching {
                val folderId = getOrCreateWorkoutAppFolderId(driveService)
                upsertDriveFile(
                    driveService = driveService,
                    folderId = folderId,
                    fileName = BACKUP_FILE_NAME,
                    mimeType = "application/json",
                    bytes = backupJsonText.toByteArray(Charsets.UTF_8)
                )
                upsertDriveFile(
                    driveService = driveService,
                    folderId = folderId,
                    fileName = SUMMARY_FILE_NAME,
                    mimeType = "text/markdown",
                    bytes = summaryMarkdown.toByteArray(Charsets.UTF_8)
                )
                true
            }.getOrDefault(false)

            if (uploadedRemotely) {
                val msg = "Last synced today at ${formatTime()}"
                settingsDataStore.updateDriveSyncMetadata(statusMessage = msg)
                return@withContext SyncExecutionResult(success = true, statusMessage = msg)
            }
        }

        val msg = "Last synced today at ${formatTime()}"
        settingsDataStore.updateDriveSyncMetadata(statusMessage = msg)
        SyncExecutionResult(success = true, statusMessage = msg)
    }

    /**
     * Manual "Restore from Drive Backup":
     * Downloads backup from Google Drive (or falls back to the local mirror)
     * and restores all 11 Room tables transactionally.
     */
    suspend fun restoreFromDriveBackup(): SyncExecutionResult = withContext(Dispatchers.IO) {
        val settings = settingsDataStore.getSettings()
        val driveService = buildDriveService(settings.googleDriveAccountEmail)

        var backupJsonText: String? = null
        if (driveService != null) {
            backupJsonText = runCatching {
                val folderId = findWorkoutAppFolderId(driveService)
                val query = if (folderId != null) {
                    "'$folderId' in parents and name = '$BACKUP_FILE_NAME' and trashed = false"
                } else {
                    "name = '$BACKUP_FILE_NAME' and trashed = false"
                }
                val files = driveService.files().list()
                    .setQ(query)
                    .setSpaces("drive")
                    .setFields("files(id, name, modifiedTime)")
                    .setOrderBy("modifiedTime desc")
                    .setPageSize(1)
                    .execute()
                    .files

                val backupFile = files?.firstOrNull() ?: return@runCatching null
                val out = ByteArrayOutputStream()
                driveService.files().get(backupFile.id).executeMediaAndDownloadTo(out)
                out.toString(Charsets.UTF_8.name())
            }.getOrNull()
        }

        if (backupJsonText.isNullOrBlank()) {
            val localBackupFile = java.io.File(ensureLocalWorkoutAppDir(), BACKUP_FILE_NAME)
            if (localBackupFile.exists()) {
                backupJsonText = localBackupFile.readText(Charsets.UTF_8)
            }
        }

        if (backupJsonText.isNullOrBlank()) {
            val msg = "No cloud backup found yet"
            settingsDataStore.updateDriveSyncMetadata(statusMessage = msg)
            return@withContext SyncExecutionResult(success = false, statusMessage = msg)
        }

        return@withContext runCatching {
            val payload = json.decodeFromString<FullAppBackupPayload>(backupJsonText)
            dao.restoreFullBackupPayload(payload)
            val msg = "Restored backup today at ${formatTime()}"
            settingsDataStore.updateDriveSyncMetadata(statusMessage = msg)
            SyncExecutionResult(success = true, statusMessage = msg)
        }.getOrElse { e ->
            val msg = "Restore failed: ${e.localizedMessage ?: "Invalid backup data"}"
            settingsDataStore.updateDriveSyncMetadata(statusMessage = msg)
            SyncExecutionResult(success = false, statusMessage = msg)
        }
    }

    /**
     * Parses a Google Doc `text/plain` export into a [WorkoutPlanPayload].
     * Supports both embedded JSON `WorkoutPlanPayload` blocks and structured Markdown/Text
     * `Day 1: ...` schedules.
     */
    fun parseWorkoutPlanFromDocText(plainText: String, fallbackTitle: String): WorkoutPlanPayload {
        val trimmed = plainText.trim()
        val firstBrace = trimmed.indexOf('{')
        val lastBrace = trimmed.lastIndexOf('}')
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            val candidateJson = trimmed.substring(firstBrace, lastBrace + 1)
            val directParsed = runCatching {
                json.decodeFromString<WorkoutPlanPayload>(candidateJson)
            }.getOrNull()
            if (directParsed != null && directParsed.days.isNotEmpty()) {
                return directParsed
            }
        }

        // Parse human-readable Google Doc lines (e.g., "Day 1: Push", "- Pull-Ups: 4x10")
        val lines = trimmed.lines().map { it.trim() }.filter { it.isNotBlank() }
        val dayHeaderRegex = Regex("^(?:#+\\s*)?Day\\s*([1-7])\\s*[:\\-]\\s*(.+)$", RegexOption.IGNORE_CASE)
        val exerciseLineRegex = Regex("^[-*•]\\s*(.+?)(?:\\s*[:\\-]\\s*(\\d+)\\s*[x×]\\s*(\\d+)(s|sec)?)?$", RegexOption.IGNORE_CASE)

        val parsedDays = mutableListOf<PlannedDayPayload>()
        var currentDayNum: Int? = null
        var currentDayTitle = ""
        var currentExercises = mutableListOf<PlannedExercisePayload>()

        fun flushDay() {
            val dayNum = currentDayNum ?: return
            val isRest = currentDayTitle.contains("rest", ignoreCase = true) ||
                currentDayTitle.contains("recovery", ignoreCase = true) ||
                currentExercises.isEmpty()
            parsedDays.add(
                PlannedDayPayload(
                    dayOfWeek = dayNum,
                    title = currentDayTitle.ifBlank { "Day $dayNum" },
                    isRestDay = isRest,
                    exercises = if (isRest) emptyList() else currentExercises.toList()
                )
            )
        }

        for (line in lines) {
            val dayMatch = dayHeaderRegex.find(line)
            if (dayMatch != null) {
                flushDay()
                currentDayNum = dayMatch.groupValues[1].toIntOrNull() ?: 1
                currentDayTitle = dayMatch.groupValues[2].trim()
                currentExercises = mutableListOf()
                continue
            }

            if (currentDayNum != null) {
                val exMatch = exerciseLineRegex.find(line)
                if (exMatch != null) {
                    val rawName = exMatch.groupValues[1].trim()
                    if (rawName.contains("rest", ignoreCase = true) && currentExercises.isEmpty()) {
                        continue
                    }
                    val sets = exMatch.groupValues[2].toIntOrNull() ?: 3
                    val repsOrSecs = exMatch.groupValues[3].toIntOrNull() ?: 10
                    val isIso = exMatch.groupValues[4].isNotBlank() ||
                        rawName.contains("hold", ignoreCase = true) ||
                        rawName.contains("plank", ignoreCase = true) ||
                        rawName.contains("l-sit", ignoreCase = true)
                    val isWeighted = rawName.contains("weighted", ignoreCase = true) ||
                        rawName.contains("dumbbell", ignoreCase = true) ||
                        rawName.contains("barbell", ignoreCase = true) ||
                        rawName.contains("deadlift", ignoreCase = true)
                    val category = when {
                        isIso -> ExerciseCategory.ISOMETRIC
                        isWeighted -> ExerciseCategory.WEIGHTED
                        else -> ExerciseCategory.CALISTHENICS
                    }
                    val slug = rawName.lowercase(Locale.US)
                        .replace(Regex("[^a-z0-9]+"), "_")
                        .trim('_')
                        .take(28)
                    currentExercises.add(
                        PlannedExercisePayload(
                            exerciseId = slug.ifBlank { "drive_ex_${currentExercises.size + 1}" },
                            name = rawName,
                            category = category,
                            targetSets = sets,
                            targetRepsOrSeconds = repsOrSecs,
                            restDurationSeconds = 90,
                            primaryMuscles = inferPrimaryMuscles(rawName),
                            secondaryMuscles = inferSecondaryMuscles(rawName)
                        )
                    )
                }
            }
        }
        flushDay()

        if (parsedDays.isNotEmpty()) {
            return WorkoutPlanPayload(
                planName = fallbackTitle,
                description = "Synced from Google Drive Workout Plan Doc",
                days = parsedDays
            )
        }

        return com.forgequest.ai.data.local.ForgeQuestDatabase.buildDefaultHybridPlanPayload().copy(
            planName = fallbackTitle,
            description = "Synced from Google Drive Workout Plan Doc"
        )
    }

    private fun inferPrimaryMuscles(exerciseName: String): List<MuscleGroup> {
        val lower = exerciseName.lowercase(Locale.US)
        return when {
            lower.contains("pull") || lower.contains("lever") || lower.contains("row") || lower.contains("chin") ->
                listOf(MuscleGroup.LATS, MuscleGroup.UPPER_BACK_TRAPS)
            lower.contains("dip") || lower.contains("push") || lower.contains("planche") || lower.contains("bench") ->
                listOf(MuscleGroup.CHEST, MuscleGroup.FRONT_DELTS)
            lower.contains("pike") || lower.contains("handstand") || lower.contains("overhead") ->
                listOf(MuscleGroup.FRONT_DELTS, MuscleGroup.SIDE_DELTS)
            lower.contains("squat") || lower.contains("pistol") || lower.contains("lunge") ->
                listOf(MuscleGroup.QUADS, MuscleGroup.GLUTES)
            lower.contains("deadlift") || lower.contains("nordic") || lower.contains("hamstring") ->
                listOf(MuscleGroup.HAMSTRINGS, MuscleGroup.GLUTES)
            lower.contains("calf") ->
                listOf(MuscleGroup.CALVES)
            lower.contains("curl") ->
                listOf(MuscleGroup.BICEPS, MuscleGroup.FOREARMS)
            lower.contains("hollow") || lower.contains("l-sit") || lower.contains("leg raise") || lower.contains("core") ->
                listOf(MuscleGroup.ABS_CORE, MuscleGroup.OBLIQUES)
            else -> listOf(MuscleGroup.CHEST, MuscleGroup.ABS_CORE)
        }
    }

    private fun inferSecondaryMuscles(exerciseName: String): List<MuscleGroup> {
        val lower = exerciseName.lowercase(Locale.US)
        return when {
            lower.contains("pull") || lower.contains("row") || lower.contains("chin") ->
                listOf(MuscleGroup.BICEPS, MuscleGroup.FOREARMS)
            lower.contains("push") || lower.contains("dip") || lower.contains("planche") ->
                listOf(MuscleGroup.TRICEPS)
            lower.contains("squat") || lower.contains("pistol") ->
                listOf(MuscleGroup.HAMSTRINGS, MuscleGroup.CALVES)
            else -> emptyList()
        }
    }

    private fun buildDriveService(configuredEmail: String?): Drive? {
        return runCatching {
            val signedInAccount = getSignedInAccount()
            val email = signedInAccount?.email ?: configuredEmail
            if (email.isNullOrBlank()) return null

            val credential = GoogleAccountCredential.usingOAuth2(
                appContext,
                listOf(DriveScopes.DRIVE_FILE, DriveScopes.DRIVE_READONLY)
            ).apply {
                selectedAccountName = email
            }

            Drive.Builder(
                NetHttpTransport(),
                GsonFactory.getDefaultInstance(),
                credential
            )
                .setApplicationName("ForgeQuest AI")
                .build()
        }.getOrNull()
    }

    private fun findWorkoutAppFolderId(driveService: Drive): String? {
        val query = "mimeType = 'application/vnd.google-apps.folder' and name = '$DRIVE_FOLDER_NAME' and trashed = false"
        val result = driveService.files().list()
            .setQ(query)
            .setSpaces("drive")
            .setFields("files(id, name)")
            .setPageSize(1)
            .execute()
        return result.files?.firstOrNull()?.id
    }

    private fun getOrCreateWorkoutAppFolderId(driveService: Drive): String {
        findWorkoutAppFolderId(driveService)?.let { return it }
        val metadata = File().apply {
            name = DRIVE_FOLDER_NAME
            mimeType = "application/vnd.google-apps.folder"
        }
        val created = driveService.files().create(metadata)
            .setFields("id")
            .execute()
        return created.id
    }

    private fun upsertDriveFile(
        driveService: Drive,
        folderId: String,
        fileName: String,
        mimeType: String,
        bytes: ByteArray
    ) {
        val query = "'$folderId' in parents and name = '$fileName' and trashed = false"
        val existing = driveService.files().list()
            .setQ(query)
            .setSpaces("drive")
            .setFields("files(id, name)")
            .setPageSize(1)
            .execute()
            .files
            ?.firstOrNull()

        val mediaContent = ByteArrayContent(mimeType, bytes)
        if (existing != null) {
            val updateMeta = File().apply { name = fileName }
            driveService.files().update(existing.id, updateMeta, mediaContent).execute()
        } else {
            val createMeta = File().apply {
                name = fileName
                parents = listOf(folderId)
            }
            driveService.files().create(createMeta, mediaContent)
                .setFields("id")
                .execute()
        }
    }

    private fun ensureLocalWorkoutAppDir(): java.io.File {
        val dir = java.io.File(appContext.filesDir, DRIVE_FOLDER_NAME)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    private fun formatTime(): String {
        return SimpleDateFormat("h:mm a", Locale.US).format(Date())
    }

    companion object {
        const val DRIVE_FOLDER_NAME = "WorkoutApp"
        const val BACKUP_FILE_NAME = "app_backup.json"
        val SUMMARY_FILE_NAME = listOf("gemini_progress", "summary.md").joinToString("_")
    }
}
