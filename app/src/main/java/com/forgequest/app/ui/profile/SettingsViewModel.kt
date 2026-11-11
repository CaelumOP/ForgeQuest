package com.forgequest.app.ui.profile

import com.forgequest.app.ui.components.UndoState
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.forgequest.app.AppContainer
import com.forgequest.app.R
import com.forgequest.app.data.ProgressSnapshot
import com.forgequest.app.data.ai.AiError
import com.forgequest.app.data.backup.BackupError
import com.forgequest.app.data.backup.BackupFile
import com.forgequest.app.data.drive.DriveAuth
import com.forgequest.app.data.drive.DriveError
import com.forgequest.app.data.notify.ReminderWorker
import com.forgequest.app.data.prefs.BodyType
import com.forgequest.app.domain.Dates
import com.forgequest.app.domain.Equipment
import com.forgequest.app.domain.Nutrition
import com.forgequest.app.domain.WeightUnit
import com.forgequest.app.ui.CalorieUi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Everything the Settings screen does, with its passing state (the snackbar, the busy line, the Google consent sheet, the
 * restore check, the update offer). One per visit to Settings, so none of that comes back the next time the screen opens.
 * The progress [snapshot] is the one [ProfileViewModel] already holds, shared with Profile and Badges.
 */
class SettingsViewModel(val app: AppContainer, val snapshot: StateFlow<ProgressSnapshot?>) : ViewModel() {
    val apiKey: StateFlow<String> = app.settings.apiKey
    val message = MutableStateFlow<String?>(null)
    val busy = MutableStateFlow<String?>(null)
    val consent = MutableStateFlow<android.app.PendingIntent?>(null)

    /** A backup waiting for the person to confirm: where it came from, and its contents. */
    data class RestorePreview(@androidx.annotation.StringRes val source: Int, val file: BackupFile)
    val restorePreview = MutableStateFlow<RestorePreview?>(null)
    val shareUri = MutableStateFlow<Uri?>(null)
    val safetyCopies = MutableStateFlow(app.backup.safetyCopies())
    private var afterConsent: (suspend (String) -> Unit)? = null

    fun setName(name: String) = viewModelScope.launch { app.settings.update { it.copy(name = name.take(24)) } }

    fun setBodyMeasures(heightCm: Float? = null, birthYear: Int? = null, bodyFatPct: Float? = null) = viewModelScope.launch {
        app.settings.update {
            it.copy(
                heightCm = heightCm ?: it.heightCm,
                birthYear = birthYear ?: it.birthYear,
                bodyFatPct = bodyFatPct ?: it.bodyFatPct,
            )
        }
    }

    fun setEquipment(encoded: String) = viewModelScope.launch {
        app.settings.update { it.copy(equipment = encoded) }
    }

    fun toggleEquipmentItem(eq: Equipment, current: Set<Equipment>) = viewModelScope.launch {
        if (eq == Equipment.BODYWEIGHT) return@launch
        val base = current.ifEmpty { setOf(Equipment.BODYWEIGHT) }
        val updated = (if (eq in base) base - eq else base + eq) + Equipment.BODYWEIGHT
        app.settings.update { it.copy(equipment = updated.joinToString(",") { e -> e.name }) }
    }

    fun setRatios(perKg: Float? = null, fatPct: Int? = null) = viewModelScope.launch {
        app.settings.update {
            it.copy(
                proteinPerKg = (perKg ?: it.proteinPerKg).coerceIn(Nutrition.MIN_PROTEIN_PER_KG, 2.5f),
                fatSharePct = (fatPct ?: it.fatSharePct).coerceIn(Nutrition.MIN_FAT_SHARE_PCT, 45),
            )
        }
    }

    fun refreshHealth() = viewModelScope.launch { app.health.refresh(force = true) }

    // ------------------------------------------------------------ Updates

    val update = MutableStateFlow<com.forgequest.app.data.update.LatestVersion?>(null)

    fun checkUpdate() = viewModelScope.launch {
        busy.value = app.str(R.string.update_checking)
        com.forgequest.app.data.suspendRunCatching { app.updates.check() }
            .onSuccess { latest -> if (latest == null) message.value = app.str(R.string.update_latest) else update.value = latest }
            .onFailure { message.value = app.str(R.string.update_failed) }
        busy.value = null
    }

    fun installUpdate() = viewModelScope.launch {
        val latest = update.value ?: return@launch
        if (!app.updates.canInstall()) { app.updates.openInstallPermission(); message.value = app.str(R.string.update_allow); return@launch }
        update.value = null
        busy.value = app.str(R.string.update_downloading)
        com.forgequest.app.data.suspendRunCatching { app.updates.download(latest) }
            .onSuccess { app.updates.install(it) }
            .onFailure { message.value = app.str(R.string.update_failed) }
        busy.value = null
    }

    fun setHealth(enabled: Boolean) = viewModelScope.launch {
        app.settings.update { it.copy(healthEnabled = enabled) }
        app.health.refresh(force = true)
    }

    /** Goal, pace or normal day; targets still on the old suggestion follow the new one, numbers set by hand stay. */
    fun setNutrition(change: (com.forgequest.app.data.prefs.Settings) -> com.forgequest.app.data.prefs.Settings) = viewModelScope.launch {
        val snap = snapshot.value ?: return@launch
        val steps = CalorieUi.stepsBase(app.health.state.value, snap.sessions)
        app.settings.update { CalorieUi.applyNutrition(it, snap.latestWeight, snap.plan?.trainingDays?.size ?: 0, steps, Dates.today(), change) }
    }

    /** A calorie target set here: never below the person's floor, remembered with the day and weight it was set at. */
    fun setKcal(kcal: Int) = viewModelScope.launch {
        val snap = snapshot.value
        val floor = snap?.let { CalorieUi.inputs(it, app.health.state.value) }?.let(CalorieUi::floor) ?: Nutrition.MIN_SAFE_KCAL
        app.settings.update { CalorieUi.withTarget(it, kcal.coerceIn(floor, Nutrition.MAX_SAFE_KCAL), snap?.latestWeight, Dates.today()) }
    }


    fun setCarbs(track: Boolean? = null, goal: Int? = null) = viewModelScope.launch {
        app.settings.update { it.copy(trackCarbs = track ?: it.trackCarbs, carbsGoal = goal ?: it.carbsGoal) }
    }

    fun updateGoals(protein: Int? = null, weekly: Int? = null) = viewModelScope.launch {
        app.settings.update { s ->
            s.copy(
                proteinGoal = (protein ?: s.proteinGoal).coerceAtLeast(60),
                weeklyTargetOverride = weekly ?: s.weeklyTargetOverride,
            )
        }
    }

    fun addNote(note: String) = viewModelScope.launch { app.coach.setNotes(app.settings.current().coachNotes + "\n" + note.trim()) }
    val undo = UndoState(viewModelScope)

    fun removeNote(note: String) = viewModelScope.launch {
        val before = app.settings.current().coachNotes
        app.coach.setNotes(before.lines().filter { it.trim() != note.trim() }.joinToString("\n"))
        // Undo puts the whole list back as it was, so the note returns to its old place.
        val short = note.trim().let { if (it.length > 32) it.take(32).trimEnd() + "…" else it }
        undo.offer(app.str(R.string.food_removed, short)) { app.coach.setNotes(before) }
    }

    fun setReminder(hour: Int) = viewModelScope.launch {
        app.settings.update { it.copy(reminderHour = hour) }
        ReminderWorker.schedule(app.appContext, hour)
    }

    fun setLock(on: Boolean) = viewModelScope.launch { app.settings.update { it.copy(biometricLock = on) } }

    fun setTheme(theme: String) = viewModelScope.launch { app.settings.update { it.copy(theme = theme) } }
    fun setUnit(unit: String) = viewModelScope.launch { app.settings.update { it.copy(unit = WeightUnit.fromCodeOrLabel(unit).code) } }
    fun setJustLog(on: Boolean) = viewModelScope.launch { app.settings.update { it.copy(justLog = on) } }
    fun setHaptics(on: Boolean) = viewModelScope.launch { app.settings.update { it.copy(hapticsEnabled = on) }; if (on) app.haptics.tick() }

    fun setBody(type: BodyType) = viewModelScope.launch { app.settings.update { it.copy(bodyType = type) } }
    fun setHand(length: Float, palm: Float) = viewModelScope.launch { app.settings.update { it.copy(handLengthCm = length, palmWidthCm = palm) } }

    val aiOn: StateFlow<Boolean> = app.settings.aiOn
    fun setAiEnabled(on: Boolean) = viewModelScope.launch { app.settings.setAiEnabled(on) }

    fun saveKey(key: String) {
        val trimmed = key.trim()
        if (trimmed.isEmpty()) {
            app.settings.setApiKey("")
            message.value = app.str(R.string.profile_key_removed)
            return
        }
        busy.value = app.str(R.string.profile_checking_key)
        viewModelScope.launch {
            val result = app.gemini.discoverModels(trimmed)
            busy.value = null
            result.onSuccess { models ->
                app.settings.setApiKey(trimmed)
                app.models.save(models)
                message.value = app.str(R.string.profile_key_saved)
            }.onFailure { e ->
                if (e is AiError.Offline) {
                    app.settings.setApiKey(trimmed)
                    message.value = app.str(R.string.profile_key_saved_offline)
                } else {
                    message.value = (e as? AiError)?.userMessage ?: app.str(R.string.profile_key_check_failed)
                }
            }
        }
    }

    // ------------------------------------------------------------ Drive

    /** Undoes what was switched on when the Google sign-in for it is cancelled. */
    private var afterCancel: (suspend () -> Unit)? = null

    private fun withDrive(label: String, onCancel: (suspend () -> Unit)? = null, op: suspend (String) -> Unit) {
        busy.value = label
        afterCancel = onCancel
        viewModelScope.launch {
            try {
                when (val auth = app.drive.authorize()) {
                    is DriveAuth.Token -> op(auth.accessToken)
                    is DriveAuth.NeedsUi -> {
                        afterConsent = op
                        consent.value = auth.intent
                    }
                }
            } catch (e: DriveError) {
                message.value = e.userMessage
            } catch (e: AiError) {
                message.value = e.userMessage
            } finally {
                if (consent.value == null) busy.value = null
            }
        }
    }

    fun onConsentResult(ok: Boolean, data: Intent?) {
        consent.value = null
        val op = afterConsent
        afterConsent = null
        val cancel = afterCancel
        afterCancel = null
        if (!ok || op == null) {
            busy.value = null
            if (!ok && cancel != null) viewModelScope.launch { cancel() }
            // Google closes the account picker straight away when the app isn't registered for Drive yet; say so.
            if (!ok) message.value = app.str(R.string.drive_signin_cancelled)
            return
        }
        viewModelScope.launch {
            try {
                op(app.drive.tokenFromConsent(data))
            } catch (e: DriveError) {
                message.value = e.userMessage
            } catch (e: AiError) {
                message.value = e.userMessage
            } finally {
                busy.value = null
            }
        }
    }

    // ------------------------------------------------------------ Meals from the Gemini Gem

    fun setGeminiMeals(on: Boolean) = viewModelScope.launch {
        app.settings.update { it.copy(geminiMealsEnabled = on) }
        if (on) syncMeals()
    }

    fun syncMeals() = withDrive(app.str(R.string.meals_syncing)) { token ->
        if (app.settings.current().driveEmail.isBlank()) {
            val email = app.drive.accountEmail(token)
            app.settings.update { it.copy(driveEmail = email.ifBlank { app.str(R.string.drive_connected) }) }
        }
        val added = app.meals.sync(token)
        message.value = if (added > 0) app.str(R.string.meals_synced, added) else app.str(R.string.meals_none_new)
    }

    fun connectDrive() = withDrive(app.str(R.string.drive_connecting)) { token ->
        val email = app.drive.accountEmail(token)
        app.settings.update { it.copy(driveEmail = email.ifBlank { app.str(R.string.drive_connected) }) }
        // An empty phone keeps the Drive backup untouched so it can still be restored.
        message.value = app.str(if (app.drive.backup(token)) R.string.drive_connected_backed_up else R.string.drive_err_nothing_to_back_up)
    }

    fun backupNow() = withDrive(app.str(R.string.drive_backing_up)) { token ->
        message.value = app.str(if (app.drive.backup(token)) R.string.drive_backed_up else R.string.drive_err_nothing_to_back_up)
    }

    fun restore() = withDrive(app.str(R.string.drive_reading_backup)) { token ->
        restorePreview.value = RestorePreview(R.string.restore_src_drive, app.drive.fetchBackup(token))
    }

    // ------------------------------------------------------------ Local backup file

    fun exportData() {
        busy.value = app.str(R.string.backup_preparing)
        viewModelScope.launch {
            try {
                shareUri.value = app.backup.exportForSharing()
            } catch (e: Exception) {
                app.haptics.error(); message.value = app.str(R.string.backup_create_failed)
            } finally {
                busy.value = null
            }
        }
    }

    /** Saves to a place the person picks (Downloads, Files, Drive) instead of only offering the share sheet. */
    fun exportTo(uri: Uri) {
        busy.value = app.str(R.string.backup_preparing)
        viewModelScope.launch {
            try {
                app.backup.exportTo(uri)
                message.value = app.str(R.string.backup_saved)
            } catch (e: Exception) {
                app.haptics.error(); message.value = app.str(R.string.backup_create_failed)
            } finally {
                busy.value = null
            }
        }
    }

    fun previewImport(uri: Uri) {
        busy.value = app.str(R.string.backup_reading_file)
        viewModelScope.launch {
            try {
                restorePreview.value = RestorePreview(R.string.restore_src_file, app.backup.read(uri))
            } catch (e: BackupError) {
                message.value = e.userMessage
            } finally {
                busy.value = null
            }
        }
    }

    fun previewUndoRestore() {
        val latest = safetyCopies.value.firstOrNull() ?: return
        busy.value = app.str(R.string.backup_reading_copy)
        viewModelScope.launch {
            try {
                restorePreview.value = RestorePreview(R.string.restore_src_copy, app.backup.read(latest))
            } catch (e: BackupError) {
                message.value = e.userMessage
            } finally {
                busy.value = null
            }
        }
    }

    fun applyRestore() {
        val preview = restorePreview.value ?: return
        restorePreview.value = null
        busy.value = app.str(R.string.backup_restoring)
        viewModelScope.launch {
            try {
                app.backup.apply(preview.file)
                safetyCopies.value = app.backup.safetyCopies()
                message.value = app.str(R.string.backup_restored_from, app.str(preview.source))
            } catch (e: Exception) {
                app.haptics.error(); message.value = app.str(R.string.backup_restore_failed)
            } finally {
                busy.value = null
            }
        }
    }

    fun disconnectDrive() = viewModelScope.launch { app.settings.update { it.copy(driveEmail = "") } }
}
