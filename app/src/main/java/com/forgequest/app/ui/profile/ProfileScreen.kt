package com.forgequest.app.ui.profile

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PanTool
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.ShowChart
import androidx.compose.material.icons.rounded.SportsGymnastics
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.TrackChanges
import androidx.compose.material.icons.rounded.Undo
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
import com.forgequest.app.domain.Dates
import com.forgequest.app.domain.Equipment
import com.forgequest.app.domain.EquipmentPreset
import com.forgequest.app.domain.ExerciseSwapEngine
import com.forgequest.app.domain.Nutrition
import com.forgequest.app.domain.NutritionGoal
import com.forgequest.app.domain.WeightTrend
import com.forgequest.app.domain.WeightUnit
import com.forgequest.app.ui.LocalSnackbar
import com.forgequest.app.ui.appViewModel
import com.forgequest.app.ui.labelRes
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

    /** Switches Cut / Maintain / Gain; null targets are the ones the person set by hand and stay as they are. */
    fun setGoal(goal: NutritionGoal, proteinPerKg: Float, kcal: Int?, protein: Int?, carbs: Int?) = viewModelScope.launch {
        app.settings.update { s ->
            s.copy(
                nutritionGoal = goal,
                goalSetDay = Dates.today(),
                proteinPerKg = proteinPerKg.coerceIn(Nutrition.MIN_PROTEIN_PER_KG, 2.5f),
                kcalGoal = (kcal ?: s.kcalGoal).coerceAtLeast(Nutrition.MIN_SAFE_KCAL),
                proteinGoal = (protein ?: s.proteinGoal).coerceAtLeast(60),
                carbsGoal = carbs ?: s.carbsGoal,
            )
        }
    }

    fun setDebrief(on: Boolean) = viewModelScope.launch { app.settings.update { it.copy(debriefEnabled = on) } }

    fun setCarbs(track: Boolean? = null, goal: Int? = null) = viewModelScope.launch {
        app.settings.update { it.copy(trackCarbs = track ?: it.trackCarbs, carbsGoal = goal ?: it.carbsGoal) }
    }

    fun updateGoals(kcal: Int? = null, protein: Int? = null, weekly: Int? = null) = viewModelScope.launch {
        app.settings.update { s ->
            s.copy(
                kcalGoal = (kcal ?: s.kcalGoal).coerceAtLeast(Nutrition.MIN_SAFE_KCAL),
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

    private fun withDrive(label: String, op: suspend (String) -> Unit) {
        busy.value = label
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
        if (!ok || op == null) {
            busy.value = null
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
        if (on) syncBridge()
    }

    fun syncBridge() = withDrive(app.str(R.string.bridge_syncing)) { token ->
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

    fun pasteBridgeReply(text: String) = viewModelScope.launch {
        val reply = app.bridge.applyText(text)
        message.value = if (reply.ok) app.str(R.string.bridge_read_ok) else app.str(R.string.bridge_read_issues, reply.issues.joinToString(", "))
    }

    fun connectDrive() = withDrive(app.str(R.string.drive_connecting)) { token ->
        val email = app.drive.accountEmail(token)
        app.settings.update { it.copy(driveEmail = email.ifBlank { app.str(R.string.drive_connected) }) }
        app.drive.backup(token)
        message.value = app.str(R.string.drive_connected_backed_up)
    }

    fun backupNow() = withDrive(app.str(R.string.drive_backing_up)) { token ->
        app.drive.backup(token)
        message.value = app.str(R.string.drive_backed_up)
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
                message.value = app.str(R.string.backup_create_failed)
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
                message.value = app.str(R.string.backup_create_failed)
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
                message.value = app.str(R.string.backup_restore_failed)
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(nav: NavHostController) {
    val vm = appViewModel { ProfileViewModel(it) }
    val snap by vm.snapshot.collectAsState()
    val apiKey by vm.apiKey.collectAsState()
    val groqKey by vm.groqKey.collectAsState()
    var groqDialog by remember { mutableStateOf(false) }
    val message by vm.message.collectAsState()
    val busy by vm.busy.collectAsState()
    val consent by vm.consent.collectAsState()
    val docPreview by vm.docPreview.collectAsState()
    val restorePreview by vm.restorePreview.collectAsState()
    val shareUri by vm.shareUri.collectAsState()
    val safetyCopies by vm.safetyCopies.collectAsState()
    val snackbar = LocalSnackbar.current
    val context = LocalContext.current
    val c = Fit.colors
    val isArabic = Locale.getDefault().language == "ar"
    var keyDialog by remember { mutableStateOf(false) }
    var weightDialog by remember { mutableStateOf(false) }
    var handDialog by remember { mutableStateOf(false) }
    var notesDialog by remember { mutableStateOf(false) }

    val consentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        vm.onConsentResult(r.resultCode == Activity.RESULT_OK, r.data)
    }
    LaunchedEffect(consent) { consent?.let { consentLauncher.launch(IntentSenderRequest.Builder(it.intentSender).build()) } }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::previewImport) }
    // Health Connect check: reads in the background and answers in one line; no files, no extra screens.
    val probe = remember { com.forgequest.app.data.health.HealthProbe(context) }
    val hcScope = rememberCoroutineScope()
    fun runHealthCheck() = hcScope.launch {
        context.toast(com.forgequest.app.data.suspendRunCatching { probe.check() }.getOrElse { context.getString(R.string.hc_failed, it.message ?: it.javaClass.simpleName) })
    }
    val hcPerms = rememberLauncherForActivityResult(androidx.health.connect.client.PermissionController.createRequestPermissionResultContract()) { runHealthCheck() }
    fun startHealthCheck() = hcScope.launch {
        // Only show the permission screen when something is still missing.
        val granted = runCatching { probe.granted() }.getOrDefault(emptySet())
        if (granted.containsAll(probe.permissions)) runHealthCheck() else hcPerms.launch(probe.permissions)
    }
    val health by vm.app.health.state.collectAsState()
    LaunchedEffect(Unit) { vm.app.health.refresh() }
    val healthAccess = rememberLauncherForActivityResult(androidx.health.connect.client.PermissionController.createRequestPermissionResultContract()) { vm.refreshHealth() }
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let(vm::exportTo) }
    LaunchedEffect(shareUri) {
        shareUri?.let { uri ->
            val send = Intent(Intent.ACTION_SEND).setType("application/json").putExtra(Intent.EXTRA_STREAM, uri)
                .putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.backup_share_subject)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.startActivity(Intent.createChooser(send, context.getString(R.string.backup_share_title)))
            vm.shareUri.value = null
        }
    }
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); vm.message.value = null } }
    DisposableEffect(Unit) { onDispose { vm.markBadgesSeen() } }

    val s = snap ?: return Box(Modifier.fillMaxSize().background(c.background))
    val settings = s.settings
    val configuredEq = settings.configuredEquipment

    Column(Modifier.fillMaxSize().background(c.background).statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Text(stringResource(R.string.tab_profile), style = MaterialTheme.typography.headlineMedium, color = c.textPrimary, modifier = Modifier.padding(top = 12.dp, bottom = 16.dp))

        if (apiKey.isBlank()) {
            FitCard(onClick = { keyDialog = true }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconCircle(Icons.Rounded.Key, c.blue, c.blueTrack)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.profile_setup_title), style = MaterialTheme.typography.titleMedium, color = c.textPrimary, modifier = Modifier.semantics { heading() })
                        Text(stringResource(R.string.profile_setup_body), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                    }
                    PillButton(stringResource(R.string.profile_add_key), onClick = { keyDialog = true })
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        // Rank
        FitCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MiniRing(s.level.progress, c.blue, c.blueTrack, size = 64.dp, stroke = 4.dp) {
                    Text(s.level.rank.letter, style = MaterialTheme.typography.headlineSmall, color = c.blue, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(settings.name.ifBlank { stringResource(R.string.profile_default_name) }, style = MaterialTheme.typography.titleLarge, color = c.textPrimary)
                    Text(stringResource(R.string.profile_rank_line, s.level.rank.letter, s.level.rank.localTitle(), s.level.level), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
                }
            }
            Spacer(Modifier.height(16.dp))
            ProgressLine(s.level.progress, c.blue, c.blueTrack, height = 8.dp)
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.profile_xp_line, s.level.xpIntoLevel, s.level.xpForNextLevel, s.level.level + 1), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth()) {
                com.forgequest.app.ui.components.StatColumn("${s.sessions.size}", stringResource(R.string.tab_workouts), c.teal, Modifier.weight(1f), onClick = { nav.navigate(com.forgequest.app.ui.Routes.HISTORY) })
                com.forgequest.app.ui.components.StatColumn("${s.streak.best}", stringResource(R.string.profile_best_streak), c.gold, Modifier.weight(1f))
                com.forgequest.app.ui.components.StatColumn("${s.badges.size}/${Badges.all.size}", stringResource(R.string.profile_badges), c.purple, Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(12.dp))

        // Badges
        FitCard(onClick = { nav.navigate(com.forgequest.app.ui.Routes.BADGES) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.profile_badges), style = MaterialTheme.typography.titleMedium, color = c.textPrimary, modifier = Modifier.semantics { heading() })
                    Text(stringResource(R.string.profile_badges_unlocked, s.badges.size, Badges.all.size), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                }
                Text(stringResource(R.string.action_see_all), style = MaterialTheme.typography.labelLarge, color = c.teal)
            }
            val latest = s.badges.sortedByDescending { it.unlockedAt }.take(4).mapNotNull { b -> Badges.byId[b.id]?.let { it to b } }
            if (latest.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth()) {
                    latest.forEach { (def, got) ->
                        BadgeChip(def.localTitle(), def.localDescription(), badgeIcon(def.category), unlocked = true, isNew = !got.seen, modifier = Modifier.weight(1f))
                    }
                    repeat(4 - latest.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        // Body weight stays out: it is logged often.
        // Body weight
        FitCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconCircle(Icons.Rounded.MonitorWeight, c.teal, c.tealTrack)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.weight_body), style = MaterialTheme.typography.titleMedium, color = c.textPrimary, modifier = Modifier.semantics { heading() })
                    Text(s.latestWeight?.let { settings.weightUnit.format(it) } ?: stringResource(R.string.profile_not_logged), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
                }
                TonalPill(stringResource(R.string.action_log), onClick = { weightDialog = true })
            }
            val recent = s.weights.takeLast(30)
            if (recent.size >= 2) {
                Spacer(Modifier.height(16.dp))
                LineChart(recent.map { settings.weightUnit.fromKg(it.kg) }, Modifier.fillMaxWidth().height(120.dp))
            }
        }
        Spacer(Modifier.height(12.dp))

        // Shortcuts to the screens that used to sit among the settings
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ShortcutCard(Icons.Rounded.ShowChart, c.teal, c.tealTrack, stringResource(R.string.profile_progress_title), Modifier.weight(1f)) { nav.navigate(com.forgequest.app.ui.Routes.PROGRESS) }
            ShortcutCard(Icons.Rounded.FitnessCenter, c.blue, c.blueTrack, stringResource(R.string.profile_exercise_library), Modifier.weight(1f)) { nav.navigate(com.forgequest.app.ui.Routes.LIBRARY) }
        }
        Spacer(Modifier.height(12.dp))

        val planDays = s.plan?.trainingDays?.size ?: 0
        val thisYear = java.time.Year.now().value
        val age = Nutrition.ageFromBirthYear(settings.birthYear, thisYear)
        val connected = settings.healthEnabled && (health is com.forgequest.app.data.health.HealthState.Ready || health is com.forgequest.app.data.health.HealthState.Loading)
        val canLock = remember {
            androidx.biometric.BiometricManager.from(context).canAuthenticate(
                androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK or androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL,
            ) == androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS
        }
        val activePreset = remember(settings.equipment) { EquipmentPreset.fromEncoded(settings.equipment) }
        // Settings fold into categories; which ones are open is remembered on the phone.
        val uiPrefs = remember { context.getSharedPreferences("profile_ui", android.content.Context.MODE_PRIVATE) }
        var openGroups by remember { mutableStateOf(uiPrefs.getStringSet("open_groups", null)?.toSet() ?: emptySet()) }
        fun toggleGroup(id: String) {
            openGroups = if (id in openGroups) openGroups - id else openGroups + id
            uiPrefs.edit().putStringSet("open_groups", openGroups).apply()
        }
        fun summary(vararg parts: String?) = parts.filterNotNull().filter { it.isNotBlank() }.joinToString(" · ")

        SettingsSection(stringResource(R.string.settings_section_settings))

        SettingsGroup(
            Icons.Rounded.Accessibility, c.coral, c.coralTrack, stringResource(R.string.settings_group_body),
            if (settings.heightCm <= 0f || age <= 0) summary(stringResource(R.string.summary_body_not_set), if (connected) stringResource(R.string.summary_fitbit_on) else null)
            else summary(
                stringResource(if (settings.bodyType == BodyType.FEMALE) R.string.body_female else R.string.body_male),
                "${settings.heightCm.toInt()} ${stringResource(R.string.unit_cm)}",
                "$age ${stringResource(R.string.unit_years)}",
                if (connected) stringResource(R.string.summary_fitbit_on) else null,
            ),
            open = "body" in openGroups, onToggle = { toggleGroup("body") },
        ) {
            FitCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconCircle(Icons.Rounded.Accessibility, c.coral, c.coralTrack)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.profile_about_you), style = MaterialTheme.typography.titleMedium, color = c.textPrimary, modifier = Modifier.semantics { heading() })
                        Text(stringResource(R.string.profile_about_you_hint), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                    }
                }
                Spacer(Modifier.height(16.dp))
                SegmentedTabs(
                    listOf(stringResource(R.string.body_male), stringResource(R.string.body_female)),
                    if (settings.bodyType == BodyType.FEMALE) 1 else 0,
                    onSelect = { vm.setBody(if (it == 1) BodyType.FEMALE else BodyType.MALE) },
                )
                Spacer(Modifier.height(8.dp))
                SettingStepper(stringResource(R.string.profile_height), if (settings.heightCm > 0f) settings.heightCm else 170f, { vm.setBodyMeasures(heightCm = it) }, step = 1f, min = 120f, max = 230f, unit = stringResource(R.string.unit_cm))
                SettingStepper(stringResource(R.string.profile_age), if (age > 0) age.toFloat() else 25f, { vm.setBodyMeasures(birthYear = thisYear - it.toInt()) }, step = 1f, min = 10f, max = 100f, unit = stringResource(R.string.unit_years))
                SettingStepper(
                    label = if (isArabic) "نسبة الدهون % (اختياري)" else "Body fat % (optional)",
                    value = settings.bodyFatPct,
                    onChange = { vm.setBodyMeasures(bodyFatPct = if (it < 4f) 0f else it) },
                    step = 0.5f,
                    min = 0f,
                    max = 55f,
                    unit = "%",
                    decimals = 1,
                )
                if (settings.heightCm <= 0f || age <= 0) {
                    Text(stringResource(R.string.profile_body_not_set), style = MaterialTheme.typography.bodySmall, color = c.gold)
                }
            }
            FitCard {
                SwitchHeader(
                    Icons.Rounded.MonitorHeart, c.coral, c.coralTrack, stringResource(R.string.profile_health_title),
                    stringResource(if (connected) R.string.profile_health_on else R.string.profile_health_hint), connected,
                ) { on ->
                    when {
                        !on -> vm.setHealth(false)
                        probe.status() != androidx.health.connect.client.HealthConnectClient.SDK_AVAILABLE -> context.toast(context.getString(R.string.hc_unavailable))
                        else -> { vm.setHealth(true); healthAccess.launch(vm.app.health.requestPermissions()) }
                    }
                }
                if (connected) {
                    TextButton(onClick = { startHealthCheck() }) { Text(stringResource(R.string.hc_test_title), color = c.teal) }
                }
            }
            SettingsRow(
                Icons.Rounded.PanTool, c.purple, c.purpleTrack,
                stringResource(R.string.profile_hand_size),
                if (settings.handLengthCm > 0f) stringResource(R.string.profile_hand_value, "%.1f".format(settings.handLengthCm), "%.1f".format(settings.palmWidthCm)) else stringResource(R.string.profile_hand_hint),
            ) { handDialog = true }
        }

        SettingsGroup(
            Icons.Rounded.TrackChanges, c.gold, c.goldTrack, stringResource(R.string.settings_group_goals),
            summary(
                stringResource(settings.nutritionGoal.labelRes),
                "%,d %s".format(settings.kcalGoal, stringResource(R.string.unit_kcal)),
                "${settings.proteinGoal} ${stringResource(R.string.unit_g)}",
                stringResource(R.string.summary_per_week, if (settings.weeklyTargetOverride == 0) planDays else settings.weeklyTargetOverride),
            ),
            open = "goals" in openGroups, onToggle = { toggleGroup("goals") },
        ) {
            FitCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconCircle(Icons.Rounded.TrackChanges, c.gold, c.goldTrack)
                    Spacer(Modifier.width(14.dp))
                    Text(stringResource(R.string.profile_daily_targets), style = MaterialTheme.typography.titleMedium, color = c.textPrimary, modifier = Modifier.semantics { heading() })
                }
                val trainingDaysForKcal = if (settings.weeklyTargetOverride > 0) settings.weeklyTargetOverride else planDays
                val hasBodyStats = (settings.heightCm > 0f && age > 0) || settings.bodyFatPct in 4f..60f
                fun kcalFor(goal: NutritionGoal) = s.latestWeight?.let {
                    Nutrition.suggestedKcal(
                        kg = it,
                        heightCm = settings.heightCm,
                        age = age,
                        male = settings.bodyType == BodyType.MALE,
                        trainingDays = trainingDaysForKcal,
                        bodyFatPct = settings.bodyFatPct,
                        goal = goal,
                    )
                }
                fun proteinFor(perKg: Float) = s.latestWeight?.let { Nutrition.suggestedProtein(it, perKg.coerceAtLeast(Nutrition.MIN_PROTEIN_PER_KG), settings.bodyFatPct) }
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.goal_title), style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
                Row(Modifier.padding(top = 8.dp, bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NutritionGoal.entries.forEach { g ->
                        ChoiceChip(stringResource(g.labelRes), settings.nutritionGoal == g, {
                            if (g != settings.nutritionGoal) {
                                // Targets still on the old suggestion follow the new goal; numbers set by hand stay put.
                                val oldPerKg = Nutrition.proteinPerKgFor(settings.nutritionGoal)
                                val perKg = if (settings.proteinPerKg == oldPerKg) Nutrition.proteinPerKgFor(g) else settings.proteinPerKg
                                val kcal = kcalFor(g)?.takeIf { hasBodyStats && kcalFor(settings.nutritionGoal) == settings.kcalGoal }
                                val protein = proteinFor(perKg)?.takeIf { proteinFor(settings.proteinPerKg) == settings.proteinGoal }
                                val oldCarbs = Nutrition.suggestedCarbs(settings.kcalGoal, settings.proteinGoal, settings.fatSharePct.coerceAtLeast(Nutrition.MIN_FAT_SHARE_PCT), s.latestWeight ?: 0f)
                                val carbs = if (settings.trackCarbs && oldCarbs == settings.carbsGoal) {
                                    Nutrition.suggestedCarbs(kcal ?: settings.kcalGoal, protein ?: settings.proteinGoal, settings.fatSharePct.coerceAtLeast(Nutrition.MIN_FAT_SHARE_PCT), s.latestWeight ?: 0f)
                                } else null
                                vm.setGoal(g, perKg, kcal, protein, carbs)
                            }
                        })
                    }
                }
                val trend = Nutrition.weightTrendNote(settings.nutritionGoal, settings.goalSetDay, s.weights.map { it.epochDay to it.kg }, Dates.today())
                if (trend != null) {
                    Text(
                        stringResource(if (trend == WeightTrend.CUT_TOO_FAST) R.string.goal_trend_cut_fast else R.string.goal_trend_gain_stalled),
                        style = MaterialTheme.typography.bodySmall, color = c.gold, modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
                SettingStepper(
                    stringResource(R.string.profile_daily_calories),
                    settings.kcalGoal.toFloat(),
                    { vm.updateGoals(kcal = it.toInt()) },
                    step = 50f,
                    min = Nutrition.MIN_SAFE_KCAL.toFloat(),
                    max = Nutrition.MAX_SAFE_KCAL.toFloat(),
                    unit = stringResource(R.string.unit_kcal),
                )
                val suggested = kcalFor(settings.nutritionGoal)
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        when {
                            !hasBodyStats -> stringResource(R.string.goals_suggest_missing)
                            suggested != null -> stringResource(R.string.goals_suggested, "%,d".format(suggested), trainingDaysForKcal)
                            else -> stringResource(R.string.goals_suggest_missing)
                        },
                        style = MaterialTheme.typography.bodySmall, color = c.textSecondary, modifier = Modifier.weight(1f),
                    )
                    if (suggested != null && hasBodyStats && suggested != settings.kcalGoal) {
                        Spacer(Modifier.width(8.dp))
                        TonalPill(stringResource(R.string.goals_use), onClick = { vm.updateGoals(kcal = suggested) })
                    }
                }
                SettingStepper(
                    stringResource(R.string.profile_daily_protein),
                    settings.proteinGoal.toFloat(),
                    { vm.updateGoals(protein = it.toInt()) },
                    step = 5f,
                    min = 60f,
                    max = 400f,
                    unit = stringResource(R.string.unit_g),
                )
                SettingStepper(
                    stringResource(R.string.profile_protein_per_kg),
                    settings.proteinPerKg.coerceAtLeast(Nutrition.MIN_PROTEIN_PER_KG),
                    { vm.setRatios(perKg = it) },
                    step = 0.1f,
                    min = Nutrition.MIN_PROTEIN_PER_KG,
                    max = 2.5f,
                    unit = stringResource(R.string.unit_g_per_kg),
                    decimals = 1,
                )
                s.latestWeight?.let { kg ->
                    Nutrition.suggestedProtein(kg, settings.proteinPerKg.coerceAtLeast(Nutrition.MIN_PROTEIN_PER_KG), settings.bodyFatPct)
                }?.takeIf { it != settings.proteinGoal }?.let { suggestedProtein ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.goals_protein_suggested, suggestedProtein, "%.1f".format(settings.proteinPerKg.coerceAtLeast(Nutrition.MIN_PROTEIN_PER_KG))),
                            style = MaterialTheme.typography.bodySmall,
                            color = c.textSecondary,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        TonalPill(stringResource(R.string.goals_use), onClick = { vm.updateGoals(protein = suggestedProtein) })
                    }
                }
                // The whole row toggles, not just the switch.
                Row(Modifier.fillMaxWidth().toggleable(settings.trackCarbs, role = Role.Switch) { vm.setCarbs(track = it) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.profile_track_carbs), style = MaterialTheme.typography.bodyLarge, color = c.textPrimary, modifier = Modifier.weight(1f))
                    FitSwitch(settings.trackCarbs, null)
                }
                if (settings.trackCarbs) {
                    SettingStepper(stringResource(R.string.profile_daily_carbs), settings.carbsGoal.toFloat(), { vm.setCarbs(goal = it.toInt()) }, step = 10f, min = 20f, max = 800f, unit = stringResource(R.string.unit_g))
                    SettingStepper(
                        stringResource(R.string.profile_fat_share),
                        settings.fatSharePct.coerceAtLeast(Nutrition.MIN_FAT_SHARE_PCT).toFloat(),
                        { vm.setRatios(fatPct = it.toInt()) },
                        step = 5f,
                        min = Nutrition.MIN_FAT_SHARE_PCT.toFloat(),
                        max = 40f,
                        unit = "%",
                    )
                    val suggestedCarbs = Nutrition.suggestedCarbs(
                        kcal = settings.kcalGoal,
                        proteinG = settings.proteinGoal,
                        fatPct = settings.fatSharePct.coerceAtLeast(Nutrition.MIN_FAT_SHARE_PCT),
                        bodyWeightKg = s.latestWeight ?: 0f,
                    )
                    if (suggestedCarbs != settings.carbsGoal && suggestedCarbs > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(R.string.profile_carbs_suggested, suggestedCarbs, settings.fatSharePct.coerceAtLeast(Nutrition.MIN_FAT_SHARE_PCT)),
                                style = MaterialTheme.typography.bodySmall,
                                color = c.textSecondary,
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(Modifier.width(8.dp))
                            TonalPill(stringResource(R.string.goals_use), onClick = { vm.setCarbs(goal = suggestedCarbs) })
                        }
                    }
                }
                val weeklyShown = if (settings.weeklyTargetOverride == 0) planDays else settings.weeklyTargetOverride
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.profile_workouts_per_week), style = MaterialTheme.typography.bodyLarge, color = c.textPrimary)
                        if (settings.weeklyTargetOverride == 0) Text(stringResource(R.string.profile_from_plan), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                    }
                    Stepper(
                        "$weeklyShown",
                        onMinus = { vm.updateGoals(weekly = (weeklyShown - 1).coerceAtLeast(1).let { if (it == planDays) 0 else it }) },
                        onPlus = { vm.updateGoals(weekly = (weeklyShown + 1).coerceAtMost(7).let { if (it == planDays) 0 else it }) },
                        minValueWidth = 84.dp,
                    )
                }
                Text(stringResource(R.string.profile_streak_explain), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
            }
        }

        SettingsGroup(
            Icons.Rounded.FitnessCenter, c.teal, c.tealTrack, stringResource(R.string.settings_group_training),
            summary(
                when {
                    activePreset != null -> activePreset.shortLabel()
                    configuredEq.isEmpty() -> stringResource(R.string.summary_equipment_unset)
                    else -> stringResource(R.string.equipment_custom)
                },
                if (settings.reminderHour < 0) stringResource(R.string.summary_reminder_off) else stringResource(R.string.summary_reminder_at, "%02d:00".format(settings.reminderHour)),
            ),
            open = "training" in openGroups, onToggle = { toggleGroup("training") },
        ) {
            EquipmentCard(activePreset, configuredEq, onPreset = { vm.setEquipment(it.encode()) }, onItem = { vm.toggleEquipmentItem(it, configuredEq) })
            FitCard {
                SwitchHeader(
                    Icons.Rounded.Notifications, c.gold, c.goldTrack, stringResource(R.string.profile_reminder),
                    if (settings.reminderHour < 0) stringResource(R.string.profile_reminder_off) else stringResource(R.string.profile_reminder_on, "%02d:00".format(settings.reminderHour)),
                    settings.reminderHour >= 0,
                ) { vm.setReminder(if (it) 18 else -1) }
                if (settings.reminderHour >= 0) {
                    var typingHour by remember { mutableStateOf(false) }
                    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.profile_time), style = MaterialTheme.typography.bodyLarge, color = c.textPrimary, modifier = Modifier.weight(1f))
                        Stepper(
                            "%02d:00".format(settings.reminderHour),
                            onMinus = { vm.setReminder((settings.reminderHour + 23) % 24) },
                            onPlus = { vm.setReminder((settings.reminderHour + 1) % 24) },
                            minValueWidth = 72.dp,
                            onValueClick = { typingHour = true },
                        )
                    }
                    if (typingHour) {
                        NumberEntryDialog(stringResource(R.string.profile_reminder_hour), settings.reminderHour.toFloat(), "h", 0, 0f, 23f, onDismiss = { typingHour = false }, onConfirm = { vm.setReminder(it.toInt()); typingHour = false })
                    }
                }
            }
            FitCard {
                Row(Modifier.fillMaxWidth().toggleable(settings.hapticsEnabled, role = Role.Switch) { vm.setHaptics(it) }, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.profile_vibration), style = MaterialTheme.typography.bodyLarge, color = c.textPrimary)
                        Text(stringResource(R.string.profile_vibration_hint), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                    }
                    FitSwitch(settings.hapticsEnabled, null)
                }
                Spacer(Modifier.height(14.dp))
                Row(
                    Modifier.fillMaxWidth().toggleable(settings.debriefEnabled, role = Role.Switch) { vm.setDebrief(it) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.debrief_setting), style = MaterialTheme.typography.bodyLarge, color = c.textPrimary)
                        Text(stringResource(R.string.debrief_setting_hint), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                    }
                    FitSwitch(settings.debriefEnabled, null)
                }
            }
        }

        SettingsGroup(
            Icons.Rounded.Psychology, c.purple, c.purpleTrack, stringResource(R.string.settings_group_coach),
            summary(
                stringResource(if (apiKey.isBlank()) R.string.summary_gemini_off else R.string.summary_gemini_on),
                stringResource(if (groqKey.isBlank()) R.string.summary_groq_off else R.string.summary_groq_on),
            ),
            open = "coach" in openGroups, onToggle = { toggleGroup("coach") },
        ) {
            SettingsRow(
                Icons.Rounded.Key, c.blue, c.blueTrack,
                stringResource(R.string.profile_ai_key),
                if (apiKey.isBlank()) stringResource(R.string.profile_key_not_set) else stringResource(R.string.profile_key_connected),
            ) { keyDialog = true }
            SettingsRow(
                Icons.Rounded.Bolt, c.gold, c.goldTrack,
                stringResource(R.string.groq_key_title),
                if (groqKey.isBlank()) stringResource(R.string.groq_key_off) else stringResource(R.string.groq_key_on),
            ) { groqDialog = true }
            FitCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconCircle(Icons.Rounded.Psychology, c.purple, c.purpleTrack)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.profile_coach_memory), style = MaterialTheme.typography.titleMedium, color = c.textPrimary, modifier = Modifier.semantics { heading() })
                        Text(
                            if (settings.coachNotes.isBlank()) stringResource(R.string.profile_memory_empty)
                            else stringResource(R.string.profile_memory_hint),
                            style = MaterialTheme.typography.bodySmall, color = c.textSecondary,
                        )
                    }
                    TonalPill(stringResource(R.string.action_add), onClick = { notesDialog = true })
                }
                val notes = settings.coachNotes.lines().filter { it.isNotBlank() }
                if (notes.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    notes.forEach { note ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { vm.removeNote(note) }.padding(vertical = 6.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("•", style = MaterialTheme.typography.bodyMedium, color = c.purple, modifier = Modifier.width(16.dp))
                            Text(note, style = MaterialTheme.typography.bodyMedium, color = c.textPrimary, modifier = Modifier.weight(1f))
                            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.cd_remove), tint = c.textSecondary, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
            var pasteOpen by remember { mutableStateOf(false) }
            val bridgeScope = rememberCoroutineScope()
            val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
            FitCard {
                SwitchHeader(Icons.Rounded.AutoAwesome, c.purple, c.purpleTrack, stringResource(R.string.bridge_title), stringResource(R.string.bridge_hint), settings.bridgeEnabled) { vm.setBridge(it) }
                if (settings.bridgeEnabled) {
                    Spacer(Modifier.height(10.dp))
                    val fmt = java.time.format.DateTimeFormatter.ofPattern("EEE HH:mm", Locale.getDefault())
                    fun at(ms: Long) = java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneId.systemDefault()).format(fmt)
                    Text(
                        (if (settings.bridgeLastWrite > 0) stringResource(R.string.bridge_wrote, at(settings.bridgeLastWrite)) else stringResource(R.string.bridge_never_wrote)) + " · " +
                            (if (settings.bridgeLastReadAt > 0) stringResource(R.string.bridge_read, at(settings.bridgeLastReadAt)) else stringResource(R.string.bridge_never_read)),
                        style = MaterialTheme.typography.bodySmall, color = c.textSecondary,
                    )
                    if (settings.bridgeIssues.isNotBlank()) Text(stringResource(R.string.bridge_issues, settings.bridgeIssues), style = MaterialTheme.typography.bodySmall, color = c.coral)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TonalPill(stringResource(R.string.bridge_sync), onClick = vm::syncBridge)
                        TonalPill(stringResource(R.string.bridge_copy_prompt), onClick = {
                            val prompt = context.resources.openRawResource(R.raw.gemini_bridge_prompt).bufferedReader().use { it.readText() }
                            clipboard.setText(androidx.compose.ui.text.AnnotatedString(prompt))
                            context.toast(context.getString(R.string.bridge_prompt_copied))
                        })
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TonalPill(stringResource(R.string.bridge_copy_data), onClick = {
                            bridgeScope.launch {
                                clipboard.setText(androidx.compose.ui.text.AnnotatedString(vm.bridgeData()))
                                context.toast(context.getString(R.string.bridge_data_copied))
                            }
                        }, tint = c.textSecondary)
                        TonalPill(stringResource(R.string.bridge_paste), onClick = { pasteOpen = true }, tint = c.textSecondary)
                    }
                }
            }
            if (pasteOpen) {
                var text by remember { mutableStateOf("") }
                AlertDialog(
                    onDismissRequest = { pasteOpen = false },
                    containerColor = c.card,
                    title = { Text(stringResource(R.string.bridge_paste_title), color = c.textPrimary) },
                    text = {
                        OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth().height(220.dp), placeholder = { Text("FORGEQUEST-REPLY v1 …") })
                    },
                    confirmButton = { TextButton(onClick = { vm.pasteBridgeReply(text); pasteOpen = false }, enabled = text.isNotBlank()) { Text(stringResource(R.string.bridge_paste_apply), color = c.teal) } },
                    dismissButton = { TextButton(onClick = { pasteOpen = false }) { Text(stringResource(R.string.action_cancel), color = c.textSecondary) } },
                )
            }
        }

        SettingsGroup(
            Icons.Rounded.Palette, c.blue, c.blueTrack, stringResource(R.string.settings_group_app),
            summary(
                stringResource(when (settings.theme) { "light" -> R.string.theme_light; "dark" -> R.string.theme_dark; else -> R.string.theme_system }),
                when (com.forgequest.app.AppLocale.current(context)) { "en" -> "English"; "ar" -> "العربية"; else -> stringResource(R.string.language_system) },
                settings.weightUnit.label,
                if (settings.biometricLock && canLock) stringResource(R.string.summary_lock_on) else null,
            ),
            open = "app" in openGroups, onToggle = { toggleGroup("app") },
        ) {
            FitCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconCircle(Icons.Rounded.Palette, c.blue, c.blueTrack)
                    Spacer(Modifier.width(14.dp))
                    Text(stringResource(R.string.profile_appearance), style = MaterialTheme.typography.titleMedium, color = c.textPrimary, modifier = Modifier.semantics { heading() })
                }
                Spacer(Modifier.height(14.dp))
                Text(stringResource(R.string.profile_theme), style = MaterialTheme.typography.labelLarge, color = c.textSecondary)
                Spacer(Modifier.height(6.dp))
                SegmentedTabs(
                    listOf(stringResource(R.string.theme_system), stringResource(R.string.theme_light), stringResource(R.string.theme_dark)),
                    when (settings.theme) { "light" -> 1; "dark" -> 2; else -> 0 },
                    onSelect = { vm.setTheme(listOf("system", "light", "dark")[it]) },
                )
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.profile_language), style = MaterialTheme.typography.labelLarge, color = c.textSecondary)
                Spacer(Modifier.height(6.dp))
                val languages = listOf(com.forgequest.app.AppLocale.SYSTEM, "en", "ar")
                SegmentedTabs(
                    listOf(stringResource(R.string.language_system), "English", "العربية"),
                    languages.indexOf(com.forgequest.app.AppLocale.current(context)).coerceAtLeast(0),
                    onSelect = { i ->
                        com.forgequest.app.AppLocale.set(context, languages[i])
                        (context as? Activity)?.recreate()
                    },
                )
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.profile_weights), style = MaterialTheme.typography.labelLarge, color = c.textSecondary)
                Spacer(Modifier.height(6.dp))
                SegmentedTabs(
                    listOf(stringResource(R.string.unit_kilograms), stringResource(R.string.unit_pounds)),
                    if (settings.weightUnit == WeightUnit.LB) 1 else 0,
                    onSelect = { vm.setUnit(if (it == 1) "lb" else "kg") },
                )
            }
            FitCard {
                SwitchHeader(
                    Icons.Rounded.Lock, c.coral, c.coralTrack, stringResource(R.string.profile_app_lock),
                    stringResource(if (!canLock) R.string.profile_lock_unavailable else if (settings.biometricLock) R.string.profile_lock_on else R.string.profile_lock_off),
                    settings.biometricLock && canLock, enabled = canLock,
                ) { vm.setLock(it) }
            }
            SettingsRow(Icons.Rounded.AutoAwesome, c.purple, c.purpleTrack, stringResource(R.string.tour_replay), stringResource(R.string.tour_replay_hint)) {
                nav.navigate(com.forgequest.app.ui.Routes.TOUR)
            }
        }

        SettingsGroup(
            Icons.Rounded.Cloud, c.teal, c.tealTrack, stringResource(R.string.settings_group_backup),
            when {
                settings.driveEmail.isBlank() -> stringResource(R.string.summary_drive_off)
                settings.lastBackupAt > 0 -> stringResource(R.string.summary_drive_last, DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(settings.lastBackupAt)))
                else -> stringResource(R.string.summary_drive_none)
            },
            open = "backup" in openGroups, onToggle = { toggleGroup("backup") },
        ) {
            FitCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconCircle(Icons.Rounded.Backup, c.blue, c.blueTrack)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.profile_backup_file), style = MaterialTheme.typography.titleMedium, color = c.textPrimary, modifier = Modifier.semantics { heading() })
                        Text(stringResource(R.string.profile_your_data_hint), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                    }
                }
                Spacer(Modifier.height(14.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillButton(stringResource(R.string.profile_save_file), onClick = { saveLauncher.launch(vm.app.backup.exportFileName()) }, icon = Icons.Rounded.SaveAlt)
                    TonalPill(stringResource(R.string.profile_share_file), onClick = vm::exportData, icon = Icons.Rounded.Share)
                    TonalPill(stringResource(R.string.profile_import), onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }, icon = Icons.Rounded.Download)
                    if (safetyCopies.isNotEmpty()) TonalPill(stringResource(R.string.profile_undo_restore), onClick = vm::previewUndoRestore, icon = Icons.Rounded.Undo)
                }
            }
            FitCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconCircle(Icons.Rounded.Cloud, c.teal, c.tealTrack)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.drive_title), style = MaterialTheme.typography.titleMedium, color = c.textPrimary, modifier = Modifier.semantics { heading() })
                        Text(
                            when {
                                settings.driveEmail.isBlank() -> stringResource(R.string.drive_hint)
                                settings.lastBackupAt > 0 -> stringResource(R.string.drive_backed_up_at, settings.driveEmail, DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(settings.lastBackupAt)))
                                else -> settings.driveEmail
                            },
                            style = MaterialTheme.typography.bodySmall, color = c.textSecondary,
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                if (settings.driveEmail.isBlank()) {
                    PillButton(stringResource(R.string.drive_connect), onClick = vm::connectDrive, modifier = Modifier.fillMaxWidth())
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        PillButton(stringResource(R.string.drive_back_up_now), onClick = vm::backupNow)
                        TonalPill(if (settings.driveDocsEnabled) stringResource(R.string.drive_import_doc) else stringResource(R.string.drive_import_doc_first), onClick = vm::importDoc)
                        if (settings.driveDocsEnabled) TonalPill(stringResource(R.string.drive_stop_docs), onClick = vm::disableDocs, tint = c.textSecondary)
                        TonalPill(stringResource(R.string.action_restore), onClick = vm::restore)
                        TonalPill(stringResource(R.string.drive_disconnect), onClick = vm::disconnectDrive, tint = c.textSecondary)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (settings.driveDocsEnabled) stringResource(R.string.drive_docs_on_hint)
                        else stringResource(R.string.drive_docs_off_hint),
                        style = MaterialTheme.typography.bodySmall, color = c.textSecondary,
                    )
                }
            }
        }

        SettingsGroup(
            Icons.Rounded.Info, c.textSecondary, c.cardHigh, stringResource(R.string.settings_group_about),
            stringResource(R.string.profile_version, com.forgequest.app.BuildConfig.VERSION_NAME),
            open = "about" in openGroups, onToggle = { toggleGroup("about") },
        ) {
            SettingsRow(Icons.Rounded.SystemUpdate, c.teal, c.tealTrack, stringResource(R.string.update_title), stringResource(R.string.update_hint, com.forgequest.app.BuildConfig.VERSION_NAME)) { vm.checkUpdate() }
            FitCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconCircle(Icons.Rounded.Info, c.textSecondary, c.cardHigh)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.profile_credits), style = MaterialTheme.typography.titleMedium, color = c.textPrimary, modifier = Modifier.semantics { heading() })
                        Text(stringResource(R.string.profile_credits_body), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                        Text(stringResource(R.string.profile_version, com.forgequest.app.BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                    }
                }
                Spacer(Modifier.height(10.dp))
                TonalPill(stringResource(R.string.profile_hemo_youtube), onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/@HemoCalisthenics")))
                }, icon = Icons.Rounded.OpenInNew)
            }
        }
        val latest by vm.update.collectAsState()
        latest?.let { v ->
            AlertDialog(
                onDismissRequest = { vm.update.value = null },
                containerColor = c.card,
                title = { Text(stringResource(R.string.update_available, v.versionName), color = c.textPrimary) },
                text = { Text(v.notes.ifBlank { stringResource(R.string.update_no_notes) }, color = c.textSecondary) },
                confirmButton = { TextButton(onClick = vm::installUpdate) { Text(stringResource(R.string.update_now), color = c.teal) } },
                dismissButton = { TextButton(onClick = { vm.update.value = null }) { Text(stringResource(R.string.update_later), color = c.textSecondary) } },
            )
        }
        Spacer(Modifier.height(20.dp))
    }

    if (keyDialog) {
        var key by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { keyDialog = false },
            containerColor = c.card,
            icon = { Icon(Icons.Rounded.Lock, contentDescription = null, tint = c.blue) },
            title = { Text(stringResource(R.string.key_dialog_title), color = c.textPrimary) },
            text = {
                Column {
                    Text(stringResource(R.string.key_dialog_body), color = c.textSecondary, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(R.string.key_dialog_privacy), color = c.textSecondary, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(10.dp))
                    TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/apikey"))) }) {
                        Text(stringResource(R.string.key_dialog_open_studio), color = c.blue)
                    }
                    OutlinedTextField(
                        value = key, onValueChange = { key = it.trim() }, singleLine = true,
                        placeholder = { Text(if (apiKey.isBlank()) stringResource(R.string.key_paste) else stringResource(R.string.key_paste_new)) },
                        visualTransformation = PasswordVisualTransformation(), shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = c.teal, unfocusedBorderColor = c.divider, focusedTextColor = c.textPrimary, unfocusedTextColor = c.textPrimary),
                    )
                }
            },
            confirmButton = { TextButton(onClick = { keyDialog = false; vm.saveKey(key) }, enabled = key.isNotBlank()) { Text(stringResource(R.string.action_save), color = c.teal) } },
            dismissButton = {
                Row {
                    if (apiKey.isNotBlank()) TextButton(onClick = { keyDialog = false; vm.saveKey("") }) { Text(stringResource(R.string.action_remove), color = c.coral) }
                    TextButton(onClick = { keyDialog = false }) { Text(stringResource(R.string.action_cancel), color = c.textSecondary) }
                }
            },
        )
    }

    if (groqDialog) {
        var key by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { groqDialog = false },
            containerColor = c.card,
            icon = { Icon(Icons.Rounded.Bolt, contentDescription = null, tint = c.gold) },
            title = { Text(stringResource(R.string.groq_key_title), color = c.textPrimary) },
            text = {
                Column {
                    Text(stringResource(R.string.groq_dialog_body), color = c.textSecondary, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(10.dp))
                    TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://console.groq.com/keys"))) }) {
                        Text(stringResource(R.string.groq_dialog_open), color = c.blue)
                    }
                    OutlinedTextField(
                        value = key, onValueChange = { key = it.trim() }, singleLine = true,
                        placeholder = { Text(if (groqKey.isBlank()) stringResource(R.string.key_paste) else stringResource(R.string.key_paste_new)) },
                        visualTransformation = PasswordVisualTransformation(), shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = c.teal, unfocusedBorderColor = c.divider, focusedTextColor = c.textPrimary, unfocusedTextColor = c.textPrimary),
                    )
                }
            },
            confirmButton = { TextButton(onClick = { groqDialog = false; vm.saveGroqKey(key) }, enabled = key.isNotBlank()) { Text(stringResource(R.string.action_save), color = c.teal) } },
            dismissButton = {
                Row {
                    if (groqKey.isNotBlank()) TextButton(onClick = { groqDialog = false; vm.saveGroqKey("") }) { Text(stringResource(R.string.action_remove), color = c.coral) }
                    TextButton(onClick = { groqDialog = false }) { Text(stringResource(R.string.action_cancel), color = c.textSecondary) }
                }
            },
        )
    }

    if (weightDialog) {
        var kg by remember { mutableFloatStateOf(s.latestWeight ?: 75f) }
        AlertDialog(
            onDismissRequest = { weightDialog = false },
            containerColor = c.card,
            title = { Text(stringResource(R.string.weight_log_title), color = c.textPrimary) },
            text = {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    val unit = settings.weightUnit
                    NumberStepper(unit.fromKg(kg), { kg = unit.toKg(it) }, step = unit.bodyStep, min = unit.fromKg(30f), max = unit.fromKg(250f), unit = unit.label, decimals = 1, title = stringResource(R.string.weight_body), minValueWidth = 96.dp)
                }
            },
            confirmButton = { TextButton(onClick = { vm.logWeight(kg); weightDialog = false }) { Text(stringResource(R.string.action_save), color = c.teal) } },
            dismissButton = { TextButton(onClick = { weightDialog = false }) { Text(stringResource(R.string.action_cancel), color = c.textSecondary) } },
        )
    }

    if (notesDialog) {
        var note by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { notesDialog = false },
            containerColor = c.card,
            title = { Text(stringResource(R.string.notes_dialog_title), color = c.textPrimary) },
            text = {
                Column {
                    Text(stringResource(R.string.notes_dialog_body), color = c.textSecondary, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = note, onValueChange = { note = it.take(160) }, singleLine = true, shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = c.teal, unfocusedBorderColor = c.divider, focusedTextColor = c.textPrimary, unfocusedTextColor = c.textPrimary),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = { TextButton(onClick = { vm.addNote(note); notesDialog = false }, enabled = note.isNotBlank()) { Text(stringResource(R.string.action_save), color = c.teal) } },
            dismissButton = { TextButton(onClick = { notesDialog = false }) { Text(stringResource(R.string.action_cancel), color = c.textSecondary) } },
        )
    }

    if (handDialog) {
        var length by remember { mutableFloatStateOf(if (settings.handLengthCm > 0f) settings.handLengthCm else 18.5f) }
        var palm by remember { mutableFloatStateOf(if (settings.palmWidthCm > 0f) settings.palmWidthCm else 8.5f) }
        AlertDialog(
            onDismissRequest = { handDialog = false },
            containerColor = c.card,
            title = { Text(stringResource(R.string.hand_dialog_title), color = c.textPrimary) },
            text = {
                Column {
                    Text(stringResource(R.string.hand_dialog_body), color = c.textSecondary, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                    SettingStepper(stringResource(R.string.hand_length), length, { length = it }, step = 0.5f, min = 10f, max = 28f, unit = stringResource(R.string.unit_cm), decimals = 1)
                    SettingStepper(stringResource(R.string.hand_palm), palm, { palm = it }, step = 0.5f, min = 5f, max = 14f, unit = stringResource(R.string.unit_cm), decimals = 1)
                }
            },
            confirmButton = { TextButton(onClick = { vm.setHand(length, palm); handDialog = false }) { Text(stringResource(R.string.action_save), color = c.teal) } },
            dismissButton = {
                Row {
                    if (settings.handLengthCm > 0f) TextButton(onClick = { vm.setHand(0f, 0f); handDialog = false }) { Text(stringResource(R.string.action_clear), color = c.coral) }
                    TextButton(onClick = { handDialog = false }) { Text(stringResource(R.string.action_cancel), color = c.textSecondary) }
                }
            },
        )
    }

    restorePreview?.let { preview ->
        AlertDialog(
            onDismissRequest = { vm.restorePreview.value = null },
            containerColor = c.card,
            title = { Text(stringResource(R.string.restore_title, stringResource(preview.source)), color = c.textPrimary) },
            text = {
                Column {
                    Text(preview.file.localSummary(), color = c.textPrimary, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.restore_body),
                        color = c.textSecondary, style = MaterialTheme.typography.bodyMedium,
                    )
                }
            },
            confirmButton = { TextButton(onClick = vm::applyRestore) { Text(stringResource(R.string.action_restore), color = c.coral) } },
            dismissButton = { TextButton(onClick = { vm.restorePreview.value = null }) { Text(stringResource(R.string.action_cancel), color = c.textSecondary) } },
        )
    }

    busy?.let {
        AlertDialog(onDismissRequest = {}, containerColor = c.card, confirmButton = {}, title = { Text(it, color = c.textPrimary) },
            text = { androidx.compose.material3.LinearProgressIndicator(color = c.teal, trackColor = c.tealTrack, modifier = Modifier.fillMaxWidth()) })
    }

    docPreview?.let { doc ->
        PlanPreviewSheet(
            draft = doc.plan,
            exercises = s.exercises,
            userEquipment = configuredEq,
            onAdapt = { vm.adaptDocPlan(s.exercises, configuredEq) },
            onApply = vm::applyDoc,
            onDismiss = { vm.docPreview.value = null },
        )
    }
}

internal fun badgeIcon(cat: com.forgequest.app.domain.BadgeCategory): ImageVector = when (cat) {
    com.forgequest.app.domain.BadgeCategory.CONSISTENCY -> Icons.Rounded.LocalFireDepartment
    com.forgequest.app.domain.BadgeCategory.SKILL -> Icons.Rounded.SportsGymnastics
    com.forgequest.app.domain.BadgeCategory.STRENGTH -> Icons.Rounded.FitnessCenter
    com.forgequest.app.domain.BadgeCategory.NUTRITION -> Icons.Rounded.Restaurant
}

@Composable
internal fun BadgeChip(title: String, description: String, icon: ImageVector, unlocked: Boolean, isNew: Boolean, modifier: Modifier = Modifier) {
    val c = Fit.colors
    var open by remember { mutableStateOf(false) }
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).clickable { open = true }.padding(vertical = 6.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            Box(Modifier.size(52.dp).clip(CircleShape).background(if (unlocked) c.goldTrack else c.cardHigh), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = if (unlocked) c.gold else c.textSecondary, modifier = Modifier.size(26.dp).alpha(if (unlocked) 1f else 0.4f))
            }
            if (isNew) Box(Modifier.size(12.dp).clip(CircleShape).background(c.coral))
        }
        Spacer(Modifier.height(6.dp))
        Text(title, style = MaterialTheme.typography.labelSmall, color = if (unlocked) c.textPrimary else c.textSecondary, textAlign = TextAlign.Center, maxLines = 2)
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            containerColor = c.card,
            icon = { Icon(icon, contentDescription = null, tint = if (unlocked) c.gold else c.textSecondary) },
            title = { Text(title, color = c.textPrimary) },
            text = { Text(if (unlocked) stringResource(R.string.badge_unlocked_detail, description) else stringResource(R.string.badge_locked_detail, description), color = c.textSecondary) },
            confirmButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.action_ok), color = c.teal) } },
        )
    }
}

/** Small group title between cards, so related settings read as one block. */
@Composable
private fun SettingsSection(title: String) {
    Text(
        title.uppercase(Locale.getDefault()),
        style = MaterialTheme.typography.labelLarge,
        color = Fit.colors.textSecondary,
        modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 8.dp).semantics { heading() },
    )
}

/** Header of a settings card that toggles as a whole: icon, title, current state, switch. */
@Composable
private fun SwitchHeader(icon: ImageVector, tint: androidx.compose.ui.graphics.Color, track: androidx.compose.ui.graphics.Color, title: String, subtitle: String, checked: Boolean, enabled: Boolean = true, onToggle: (Boolean) -> Unit) {
    val c = Fit.colors
    Row(Modifier.fillMaxWidth().toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onToggle), verticalAlignment = Alignment.CenterVertically) {
        IconCircle(icon, tint, track)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = c.textPrimary, modifier = Modifier.semantics { heading() })
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
        }
        FitSwitch(checked, null, enabled = enabled)
    }
}

@Composable
private fun SettingsRow(icon: ImageVector, tint: androidx.compose.ui.graphics.Color, track: androidx.compose.ui.graphics.Color, title: String, subtitle: String, onClick: () -> Unit) {
    val c = Fit.colors
    FitCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconCircle(icon, tint, track)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
            }
        }
    }
}

@Composable
private fun SettingStepper(label: String, value: Float, onChange: (Float) -> Unit, step: Float, min: Float, max: Float, unit: String = "", decimals: Int = 0) {
    val c = Fit.colors
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = c.textPrimary, modifier = Modifier.weight(1f))
        NumberStepper(value, onChange, step, min, max, unit = unit, decimals = decimals, title = label, minValueWidth = 84.dp)
    }
}
/**
 * A folding settings category: one card with an icon, a title and a one-line summary of the current values,
 * so most of the time the summary is enough and the group stays shut. Tap to open the cards inside.
 */
@Composable
private fun SettingsGroup(
    icon: ImageVector,
    tint: androidx.compose.ui.graphics.Color,
    track: androidx.compose.ui.graphics.Color,
    title: String,
    summary: String,
    open: Boolean,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Fit.colors
    val turn by animateFloatAsState(if (open) 180f else 0f, label = "chevron")
    val state = stringResource(if (open) R.string.cd_group_open else R.string.cd_group_closed)
    FitCard(
        modifier = Modifier.semantics { stateDescription = state },
        onClick = onToggle,
        padding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconCircle(icon, tint, track)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = c.textPrimary, modifier = Modifier.semantics { heading() })
                Text(summary, style = MaterialTheme.typography.bodySmall, color = c.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Rounded.ExpandMore, contentDescription = null, tint = c.textSecondary, modifier = Modifier.rotate(turn))
        }
    }
    AnimatedVisibility(open, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
        // Indented a little so the cards read as belonging to the category above them.
        Column(Modifier.padding(start = 12.dp, top = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
    Spacer(Modifier.height(12.dp))
}

/** Half-width card that opens another screen. */
@Composable
private fun ShortcutCard(icon: ImageVector, tint: androidx.compose.ui.graphics.Color, track: androidx.compose.ui.graphics.Color, title: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    FitCard(modifier, onClick = onClick, padding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconCircle(icon, tint, track, size = 40.dp)
            Spacer(Modifier.width(12.dp))
            Text(title, style = MaterialTheme.typography.titleSmall, color = Fit.colors.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** "Home gym" out of "Home gym (barbell, dumbbells, bar)": the items are listed on their own line. */
private fun EquipmentPreset.shortLabel(): String =
    (if (Locale.getDefault().language == "ar") labelAr else labelEn).substringBefore(" (")

/**
 * Equipment in two clearly separate steps: (1) one ready-made setup, picked like a radio button,
 * (2) the single items, ticked like checkboxes. Ticking an item that no setup matches shows "Custom mix".
 */
@Composable
private fun EquipmentCard(active: EquipmentPreset?, configured: Set<Equipment>, onPreset: (EquipmentPreset) -> Unit, onItem: (Equipment) -> Unit) {
    val c = Fit.colors
    FitCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconCircle(Icons.Rounded.FitnessCenter, c.teal, c.tealTrack)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.equipment_title), style = MaterialTheme.typography.titleMedium, color = c.textPrimary, modifier = Modifier.semantics { heading() })
                Text(stringResource(R.string.equipment_hint), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
            }
        }

        EquipmentStep(1, stringResource(R.string.equipment_step_setup), stringResource(R.string.equipment_step_setup_hint))
        Column(Modifier.selectableGroup()) {
            EquipmentPreset.entries.forEach { preset ->
                val items = preset.equipment.filter { it != Equipment.BODYWEIGHT }
                ChoiceRow(
                    title = preset.shortLabel(),
                    subtitle = if (items.isEmpty()) stringResource(R.string.equipment_no_items) else items.joinToString(" · ") { it.localName() },
                    selected = active == preset,
                    radio = true,
                    onClick = { onPreset(preset) },
                )
            }
            if (active == null && configured.isNotEmpty()) {
                ChoiceRow(stringResource(R.string.equipment_custom), stringResource(R.string.equipment_custom_hint), selected = true, radio = true, onClick = null)
            }
        }

        Spacer(Modifier.height(8.dp))
        androidx.compose.material3.HorizontalDivider(color = c.divider)
        EquipmentStep(2, stringResource(R.string.equipment_step_items), stringResource(R.string.equipment_step_items_hint))
        Equipment.entries.forEach { eq ->
            val always = eq == Equipment.BODYWEIGHT
            ChoiceRow(
                title = eq.localName(),
                subtitle = if (always) stringResource(R.string.equipment_always) else null,
                selected = always || eq in configured,
                radio = false,
                onClick = if (always) null else ({ onItem(eq) }),
            )
        }
    }
}

@Composable
private fun EquipmentStep(number: Int, title: String, hint: String) {
    val c = Fit.colors
    Row(Modifier.padding(top = 16.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(24.dp).clip(CircleShape).background(c.tealTrack), contentAlignment = Alignment.Center) {
            Text("$number", style = MaterialTheme.typography.labelMedium, color = c.teal, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall, color = c.textPrimary, modifier = Modifier.semantics { heading() })
            Text(hint, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
        }
    }
}

/** One full-width option row with a radio circle or a checkbox; the whole row is the touch target. */
@Composable
private fun ChoiceRow(title: String, subtitle: String?, selected: Boolean, radio: Boolean, onClick: (() -> Unit)?) {
    val c = Fit.colors
    val shape = RoundedCornerShape(14.dp)
    val base = Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(shape).background(if (selected) c.tealTrack else androidx.compose.ui.graphics.Color.Transparent)
    val touch = when {
        onClick == null -> base
        radio -> base.selectable(selected, role = Role.RadioButton, onClick = onClick)
        else -> base.toggleable(selected, role = Role.Checkbox) { onClick() }
    }
    Row(touch.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            when {
                radio && selected -> Icons.Rounded.RadioButtonChecked
                radio -> Icons.Rounded.RadioButtonUnchecked
                selected -> Icons.Rounded.CheckBox
                else -> Icons.Rounded.CheckBoxOutlineBlank
            },
            contentDescription = null,
            tint = if (selected) c.teal else c.textSecondary,
            modifier = Modifier.size(22.dp).alpha(if (onClick == null && !radio) 0.6f else 1f),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = c.textPrimary)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = c.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}
