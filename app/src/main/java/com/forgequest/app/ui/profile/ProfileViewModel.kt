package com.forgequest.app.ui.profile

import com.forgequest.app.ui.theme.ForgeSpace
import com.forgequest.app.ui.theme.ForgeShape
import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Accessibility
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.MonitorWeight
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PanTool
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material.icons.rounded.SportsGymnastics
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.TrackChanges
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.forgequest.app.AppContainer
import com.forgequest.app.R
import com.forgequest.app.data.ProgressSnapshot
import com.forgequest.app.data.ai.AiError
import com.forgequest.app.data.backup.BackupError
import com.forgequest.app.data.backup.BackupFile
import com.forgequest.app.data.db.ExerciseEntity
import com.forgequest.app.data.drive.DriveAuth
import com.forgequest.app.data.drive.DriveError
import com.forgequest.app.data.drive.DriveSync
import com.forgequest.app.data.notify.ReminderWorker
import com.forgequest.app.data.prefs.BodyType
import com.forgequest.app.domain.Badges
import com.forgequest.app.domain.CutPace
import com.forgequest.app.domain.DayActivity
import com.forgequest.app.domain.Dates
import com.forgequest.app.domain.GainPace
import com.forgequest.app.domain.Equipment
import com.forgequest.app.domain.EquipmentPreset
import com.forgequest.app.domain.ExerciseSwapEngine
import com.forgequest.app.domain.Nutrition
import com.forgequest.app.domain.NutritionGoal
import com.forgequest.app.domain.WeightTrend
import com.forgequest.app.domain.WeightUnit
import com.forgequest.app.ui.LocalSnackbar
import com.forgequest.app.ui.appViewModel
import com.forgequest.app.ui.descRes
import com.forgequest.app.ui.labelRes
import com.forgequest.app.ui.CalorieUi
import com.forgequest.app.ui.components.ChoiceChip
import com.forgequest.app.ui.components.FitCard
import com.forgequest.app.ui.components.FitSwitch
import com.forgequest.app.ui.components.IconCircle
import com.forgequest.app.ui.components.MiniRing
import com.forgequest.app.ui.components.NumberEntryDialog
import com.forgequest.app.ui.components.NumberStepper
import com.forgequest.app.ui.components.PillButton
import com.forgequest.app.ui.components.ProgressLine
import com.forgequest.app.ui.components.SegmentedTabs
import com.forgequest.app.ui.components.Stepper
import com.forgequest.app.ui.components.TonalPill
import com.forgequest.app.ui.components.toast
import com.forgequest.app.ui.localDescription
import com.forgequest.app.ui.localSummary
import com.forgequest.app.ui.localTitle
import com.forgequest.app.ui.mark
import com.forgequest.app.ui.theme.Fit
import com.forgequest.app.ui.workouts.LineChart
import com.forgequest.app.ui.workouts.PlanPreviewSheet
import com.forgequest.app.ui.workouts.localName
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import java.util.Locale

class ProfileViewModel(val app: AppContainer) : ViewModel() {
    val snapshot: StateFlow<ProgressSnapshot?> = app.progress.snapshot.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val apiKey: StateFlow<String> = app.settings.apiKey
    val message = MutableStateFlow<String?>(null)
    val busy = MutableStateFlow<String?>(null)
    val consent = MutableStateFlow<android.app.PendingIntent?>(null)
    val docPreview = MutableStateFlow<DriveSync.DocPlan?>(null)

    /** A backup waiting for the person to confirm: where it came from, and its contents. */
    data class RestorePreview(@androidx.annotation.StringRes val source: Int, val file: BackupFile)
    val restorePreview = MutableStateFlow<RestorePreview?>(null)
    val shareUri = MutableStateFlow<Uri?>(null)
    val safetyCopies = MutableStateFlow(app.backup.safetyCopies())
    private var afterConsent: (suspend (String) -> Unit)? = null

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
    fun removeNote(note: String) = viewModelScope.launch {
        app.coach.setNotes(app.settings.current().coachNotes.lines().filter { it.trim() != note.trim() }.joinToString("\n"))
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
    fun logWeight(kg: Float) = viewModelScope.launch { app.progress.logWeight(kg) }
    fun markBadgesSeen() = viewModelScope.launch { app.progress.markBadgesSeen() }

    val groqKey = app.settings.groqKey

    fun saveGroqKey(key: String) {
        busy.value = app.str(R.string.profile_checking_key)
        viewModelScope.launch {
            try {
                app.coach.saveGroqKey(key)
                message.value = app.str(if (key.isBlank()) R.string.groq_key_removed else R.string.groq_key_saved)
            } catch (e: com.forgequest.app.data.ai.AiError) {
                message.value = e.userMessage
            } finally {
                busy.value = null
            }
        }
    }

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

    // ------------------------------------------------------------ Gemini bridge

    fun setBridge(on: Boolean) = viewModelScope.launch {
        app.settings.update { it.copy(bridgeEnabled = on) }
        if (on) com.forgequest.app.data.bridge.GeminiBridgeWorker.schedule(app.appContext) else com.forgequest.app.data.bridge.GeminiBridgeWorker.cancel(app.appContext)
        if (on) syncBridge(undoOnCancel = true)
    }

    /** [undoOnCancel]: turning the bridge on, so a cancelled sign-in switches it back off instead of leaving it half set up. */
    fun syncBridge(undoOnCancel: Boolean = false) = withDrive(
        app.str(R.string.bridge_syncing),
        onCancel = if (undoOnCancel) ({
            app.settings.update { it.copy(bridgeEnabled = false) }
            com.forgequest.app.data.bridge.GeminiBridgeWorker.cancel(app.appContext)
        }) else null,
    ) { token ->
        // The same sign-in connects Drive backup too, so one tap sets up both.
        if (app.settings.current().driveEmail.isBlank()) {
            val email = app.drive.accountEmail(token)
            app.settings.update { it.copy(driveEmail = email.ifBlank { app.str(R.string.drive_connected) }) }
        }
        val reply = app.bridge.sync(token)
        message.value = when {
            reply == null -> app.str(R.string.bridge_no_reply)
            reply.ok -> app.str(R.string.bridge_read_ok)
            else -> app.str(R.string.bridge_read_issues, reply.issues.joinToString(", "))
        }
    }

    suspend fun bridgeData(): String = app.bridge.dataText()

    // ------------------------------------------------------------ Meals from the Gemini Gem

    fun setGeminiMeals(on: Boolean) = viewModelScope.launch {
        app.settings.update { it.copy(geminiMealsEnabled = on) }
        val s = app.settings.current()
        if (s.bridgeEnabled || s.geminiMealsEnabled) com.forgequest.app.data.bridge.GeminiBridgeWorker.schedule(app.appContext)
        else com.forgequest.app.data.bridge.GeminiBridgeWorker.cancel(app.appContext)
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

    fun pasteBridgeReply(text: String) = viewModelScope.launch {
        val reply = app.bridge.applyText(text)
        message.value = if (reply.ok) app.str(R.string.bridge_read_ok) else app.str(R.string.bridge_read_issues, reply.issues.joinToString(", "))
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

    fun importDoc() = withDrive(app.str(R.string.drive_reading_doc)) { token ->
        if (app.settings.apiKey.value.isBlank()) throw AiError.MissingKey
        app.settings.update { it.copy(driveDocsEnabled = true) }
        docPreview.value = app.drive.fetchPlanDoc(token, 0)
    }

    fun disableDocs() = viewModelScope.launch { app.settings.update { it.copy(driveDocsEnabled = false) } }

    fun adaptDocPlan(exercises: Map<String, ExerciseEntity>, userEquipment: Set<Equipment>) {
        val doc = docPreview.value ?: return
        val (adapted, _) = ExerciseSwapEngine.adaptPlanToEquipment(doc.plan, exercises, userEquipment)
        docPreview.value = doc.copy(plan = adapted)
    }

    fun applyDoc() {
        val doc = docPreview.value ?: return
        docPreview.value = null
        viewModelScope.launch {
            app.drive.applyDocPlan(doc)
            message.value = app.str(R.string.drive_imported, doc.docName)
        }
    }

    fun disconnectDrive() = viewModelScope.launch { app.settings.update { it.copy(driveEmail = "", driveDocsEnabled = false) } }
}
