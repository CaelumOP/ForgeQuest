package com.forgequest.app.data.backup

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.room.withTransaction
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
import com.forgequest.app.data.prefs.BodyType
import com.forgequest.app.data.prefs.Settings
import com.forgequest.app.data.prefs.SettingsRepository
import com.forgequest.app.domain.WeightUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** The person's own settings, so a restore on a new phone brings goals and name along. The AI key is never included. */
@Serializable
data class BackupSettings(
    val name: String = "",
    val bodyType: String = "MALE",
    val kcalGoal: Int = 2200,
    val proteinGoal: Int = 140,
    val weeklyTargetOverride: Int = 0,
    val handLengthCm: Float = 0f,
    val palmWidthCm: Float = 0f,
    val coachNotes: String = "",
    val theme: String = "system",
    val hapticsEnabled: Boolean = true,
    val unit: String = "kg",
    val reminderHour: Int = -1,
    val heightCm: Float = 0f,
    val birthYear: Int = 0,
    val trackCarbs: Boolean = false,
    val carbsGoal: Int = 250,
    val proteinPerKg: Float = 1.8f,
    val fatSharePct: Int = 25,
    val equipment: String = "",
    val bodyFatPct: Float = 0f,
) {
    companion object {
        fun from(s: Settings) = BackupSettings(
            name = s.name,
            bodyType = s.bodyType.name,
            kcalGoal = s.kcalGoal.coerceAtLeast(1200),
            proteinGoal = s.proteinGoal,
            weeklyTargetOverride = s.weeklyTargetOverride,
            handLengthCm = s.handLengthCm,
            palmWidthCm = s.palmWidthCm,
            coachNotes = s.coachNotes,
            theme = s.theme,
            hapticsEnabled = s.hapticsEnabled,
            unit = WeightUnit.fromCodeOrLabel(s.unit).code,
            reminderHour = s.reminderHour,
            heightCm = s.heightCm,
            birthYear = s.birthYear,
            trackCarbs = s.trackCarbs,
            carbsGoal = s.carbsGoal,
            proteinPerKg = s.proteinPerKg,
            fatSharePct = s.fatSharePct,
            equipment = s.equipment,
            bodyFatPct = s.bodyFatPct,
        )
    }

    fun applyTo(s: Settings) = s.copy(
        name = name,
        bodyType = runCatching { BodyType.valueOf(bodyType) }.getOrDefault(s.bodyType),
        kcalGoal = kcalGoal.coerceAtLeast(1200),
        proteinGoal = proteinGoal,
        weeklyTargetOverride = weeklyTargetOverride,
        handLengthCm = handLengthCm,
        palmWidthCm = palmWidthCm,
        coachNotes = coachNotes,
        theme = theme,
        hapticsEnabled = hapticsEnabled,
        unit = WeightUnit.fromCodeOrLabel(unit).code,
        reminderHour = reminderHour,
        heightCm = heightCm,
        birthYear = birthYear,
        trackCarbs = trackCarbs,
        carbsGoal = carbsGoal,
        proteinPerKg = proteinPerKg,
        fatSharePct = fatSharePct,
        equipment = equipment,
        bodyFatPct = bodyFatPct,
    )
}

/** Everything the app knows, as one JSON document. Version 1 files (no settings) still load. */
@Serializable
data class BackupFile(
    val version: Int = 2,
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
    val settings: BackupSettings? = null,
) {
    /** One line a person can read before deciding to restore. */
    fun summary(): String {
        val finished = sessions.count { it.finishedAt != null }
        val made = Instant.ofEpochMilli(createdAt).atZone(ZoneId.systemDefault()).toLocalDate()
        val parts = listOf(
            "$finished workout${if (finished == 1) "" else "s"}",
            "${meals.size} meal${if (meals.size == 1) "" else "s"}",
            "${weights.size} weight entr${if (weights.size == 1) "y" else "ies"}",
        )
        return parts.joinToString(", ") + " · saved ${made.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))}"
    }
}

sealed class BackupError(res: Int) : com.forgequest.app.LocalizedException(res) {
    object Unreadable : BackupError(com.forgequest.app.R.string.backup_err_unreadable)
    object CantOpen : BackupError(com.forgequest.app.R.string.backup_err_cant_open)
}

/**
 * Builds, writes and applies backups. Google Drive and the local export/import both go through here,
 * so the file format has one definition and restoring always keeps a safety copy first.
 */
class BackupRepository(
    private val context: Context,
    private val db: AppDatabase,
    private val settings: SettingsRepository,
    private val workouts: WorkoutRepository,
) {
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun build(): BackupFile = withContext(Dispatchers.IO) {
        val b = db.backup()
        BackupFile(
            createdAt = System.currentTimeMillis(),
            exercises = b.exercises(), plans = b.plans(), planDays = b.planDays(), planItems = b.planItems(),
            sessions = b.sessions(), sets = b.sets(), foods = b.foods(), meals = b.meals(), weights = b.weights(),
            chat = b.chat(), badges = b.badges(),
            settings = BackupSettings.from(settings.current()),
        )
    }

    fun encode(file: BackupFile): String = json.encodeToString(BackupFile.serializer(), file)

    fun decode(text: String): BackupFile =
        runCatching { json.decodeFromString(BackupFile.serializer(), text) }.getOrElse { throw BackupError.Unreadable }

    /** Replaces everything on this phone with [file], after saving what's here now to [safetyDir]. */
    suspend fun apply(file: BackupFile) = withContext(Dispatchers.IO) {
        saveSafetyCopy()
        db.withTransaction {
            db.backup().replaceAll(
                file.exercises, file.plans, file.planDays, file.planItems, file.sessions, file.sets,
                // Photos aren't part of a backup, so drop paths that don't exist on this phone.
                file.foods.map { it.copy(photoPath = it.photoPath?.takeIf { p -> File(p).exists() }) },
                file.meals.map { it.copy(photoPath = it.photoPath?.takeIf { p -> File(p).exists() }) },
                file.weights, file.chat, file.badges,
            )
        }
        file.settings?.let { restored -> settings.update { restored.applyTo(it) } }
        workouts.seedLibrary()
    }

    // ------------------------------------------------------------ Local files

    private val safetyDir get() = File(context.filesDir, "backups").apply { mkdirs() }

    /** Keeps the three most recent pre-restore copies so an accidental restore can be undone by importing one. */
    private suspend fun saveSafetyCopy() {
        val current = build()
        if (current.sessions.isEmpty() && current.meals.isEmpty() && current.weights.isEmpty()) return
        val stamp = Instant.ofEpochMilli(current.createdAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm"))
        File(safetyDir, "before-restore_$stamp.json").writeText(encode(current))
        safetyDir.listFiles().orEmpty().sortedByDescending { it.lastModified() }.drop(3).forEach { it.delete() }
    }

    fun safetyCopies(): List<File> = safetyDir.listFiles().orEmpty().sortedByDescending { it.lastModified() }

    /** Writes today's backup to the cache and returns a shareable content URI. */
    suspend fun exportForSharing(): Uri = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "export").apply { mkdirs() }
        dir.listFiles().orEmpty().forEach { it.delete() }
        val file = File(dir, "forgequest-backup-${LocalDate.now().format(DateTimeFormatter.ISO_DATE)}.json")
        file.writeText(encode(build()))
        FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    }

    /** Writes a backup straight to a file the person chose in the system "Save as" screen. */
    suspend fun exportTo(uri: Uri) = withContext(Dispatchers.IO) {
        val text = encode(build())
        context.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use { it.write(text) } ?: throw BackupError.CantOpen
    }

    fun exportFileName(): String = "forgequest-backup-${LocalDate.now().format(DateTimeFormatter.ISO_DATE)}.json"

    /** Reads a backup the person picked, without applying it. */
    suspend fun read(uri: Uri): BackupFile = withContext(Dispatchers.IO) {
        val text = runCatching { context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } }
            .getOrNull() ?: throw BackupError.CantOpen
        decode(text)
    }

    suspend fun read(file: File): BackupFile = withContext(Dispatchers.IO) { decode(file.readText()) }
}
