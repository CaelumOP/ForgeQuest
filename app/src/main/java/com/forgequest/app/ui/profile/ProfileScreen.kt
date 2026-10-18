package com.forgequest.app.ui.profile

import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.SaveAlt
import com.forgequest.app.R
import com.forgequest.app.ui.localTitle
import com.forgequest.app.ui.localDescription
import com.forgequest.app.ui.localSummary

import androidx.compose.ui.res.stringResource

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Accessibility
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.ShowChart
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.SportsGymnastics
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MonitorWeight
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PanTool
import androidx.compose.material.icons.rounded.TrackChanges
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Switch
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.forgequest.app.AppContainer
import com.forgequest.app.data.ProgressSnapshot
import com.forgequest.app.data.ai.AiError
import com.forgequest.app.data.drive.DriveAuth
import com.forgequest.app.data.drive.DriveError
import com.forgequest.app.data.backup.BackupError
import com.forgequest.app.data.backup.BackupFile
import com.forgequest.app.data.drive.DriveSync
import com.forgequest.app.data.notify.ReminderWorker
import com.forgequest.app.data.prefs.BodyType
import com.forgequest.app.domain.Badges
import com.forgequest.app.domain.Nutrition
import com.forgequest.app.ui.LocalSnackbar
import com.forgequest.app.ui.appViewModel
import com.forgequest.app.ui.components.SegmentedTabs
import com.forgequest.app.ui.components.FitCard
import com.forgequest.app.ui.components.IconCircle
import com.forgequest.app.ui.components.MiniRing
import com.forgequest.app.ui.components.PillButton
import com.forgequest.app.ui.components.ProgressLine
import com.forgequest.app.ui.components.NumberEntryDialog
import com.forgequest.app.ui.components.NumberStepper
import com.forgequest.app.ui.components.Stepper
import com.forgequest.app.ui.components.TonalPill
import com.forgequest.app.ui.theme.Fit
import com.forgequest.app.ui.workouts.LineChart
import com.forgequest.app.ui.workouts.PlanPreviewSheet
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

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

    fun setBodyMeasures(heightCm: Float? = null, birthYear: Int? = null) = viewModelScope.launch {
        app.settings.update { it.copy(heightCm = heightCm ?: it.heightCm, birthYear = birthYear ?: it.birthYear) }
    }

    fun setCarbs(track: Boolean? = null, goal: Int? = null) = viewModelScope.launch {
        app.settings.update { it.copy(trackCarbs = track ?: it.trackCarbs, carbsGoal = goal ?: it.carbsGoal) }
    }

    fun updateGoals(kcal: Int? = null, protein: Int? = null, weekly: Int? = null) = viewModelScope.launch {
        app.settings.update { s ->
            s.copy(
                kcalGoal = kcal ?: s.kcalGoal,
                proteinGoal = protein ?: s.proteinGoal,
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
    fun setUnit(unit: String) = viewModelScope.launch { app.settings.update { it.copy(unit = unit) } }
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

    private fun withDrive(label: String, withDocs: Boolean = false, op: suspend (String) -> Unit) {
        busy.value = label
        viewModelScope.launch {
            try {
                when (val auth = app.drive.authorize(withDocs)) {
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

    fun importDoc() = withDrive(app.str(R.string.drive_reading_doc), withDocs = true) { token ->
        if (app.settings.apiKey.value.isBlank()) throw AiError.MissingKey
        app.settings.update { it.copy(driveDocsEnabled = true) }
        docPreview.value = app.drive.fetchPlanDoc(token, 0)
    }

    fun disableDocs() = viewModelScope.launch { app.settings.update { it.copy(driveDocsEnabled = false) } }

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
    var keyDialog by remember { mutableStateOf(false) }
    var weightDialog by remember { mutableStateOf(false) }
    var handDialog by remember { mutableStateOf(false) }
    var notesDialog by remember { mutableStateOf(false) }

    val consentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        vm.onConsentResult(r.resultCode == Activity.RESULT_OK, r.data)
    }
    LaunchedEffect(consent) { consent?.let { consentLauncher.launch(IntentSenderRequest.Builder(it.intentSender).build()) } }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::previewImport) }
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
            val owned = s.badges.associateBy { it.id }
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

        // Goals
        FitCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconCircle(Icons.Rounded.TrackChanges, c.gold, c.goldTrack)
                Spacer(Modifier.width(14.dp))
                Text(stringResource(R.string.profile_goals), style = MaterialTheme.typography.titleMedium, color = c.textPrimary, modifier = Modifier.semantics { heading() })
            }
            Spacer(Modifier.height(12.dp))
            SettingStepper(stringResource(R.string.profile_daily_protein), settings.proteinGoal.toFloat(), { vm.updateGoals(protein = it.toInt()) }, step = 5f, min = 40f, max = 400f, unit = stringResource(R.string.unit_g))
            SettingStepper(stringResource(R.string.profile_daily_calories), settings.kcalGoal.toFloat(), { vm.updateGoals(kcal = it.toInt()) }, step = 50f, min = 1000f, max = 6000f, unit = stringResource(R.string.unit_kcal))
            // The whole row toggles, not just the switch.
            Row(Modifier.fillMaxWidth().toggleable(settings.trackCarbs, role = Role.Switch) { vm.setCarbs(track = it) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.profile_track_carbs), style = MaterialTheme.typography.bodyLarge, color = c.textPrimary, modifier = Modifier.weight(1f))
                Switch(settings.trackCarbs, null, colors = SwitchDefaults.colors(checkedTrackColor = c.teal, checkedThumbColor = c.card))
            }
            if (settings.trackCarbs) {
                SettingStepper(stringResource(R.string.profile_daily_carbs), settings.carbsGoal.toFloat(), { vm.setCarbs(goal = it.toInt()) }, step = 10f, min = 20f, max = 800f, unit = stringResource(R.string.unit_g))
                // Calories left after protein (4 kcal/g) and a 25 % fat share, as grams of carbs (4 kcal/g).
                val suggested = (((settings.kcalGoal * 0.75f) - settings.proteinGoal * 4f) / 4f / 5f).roundToInt().coerceAtLeast(4) * 5
                if (suggested != settings.carbsGoal) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.profile_carbs_suggested, suggested), style = MaterialTheme.typography.bodySmall, color = c.textSecondary, modifier = Modifier.weight(1f))
                        TextButton(onClick = { vm.setCarbs(goal = suggested) }) { Text(stringResource(R.string.profile_use), color = c.teal) }
                    }
                }
            }
            val planDays = s.plan?.trainingDays?.size ?: 0
            val thisYear = java.time.Year.now().value
            val age = Nutrition.ageFromBirthYear(settings.birthYear, thisYear)
            SettingStepper(stringResource(R.string.profile_height), if (settings.heightCm > 0f) settings.heightCm else 170f, { vm.setBodyMeasures(heightCm = it) }, step = 1f, min = 120f, max = 230f, unit = stringResource(R.string.unit_cm))
            SettingStepper(stringResource(R.string.profile_age), if (age > 0) age.toFloat() else 25f, { vm.setBodyMeasures(birthYear = thisYear - it.toInt()) }, step = 1f, min = 10f, max = 100f, unit = stringResource(R.string.unit_years))
            val trainingDaysForKcal = if (settings.weeklyTargetOverride > 0) settings.weeklyTargetOverride else planDays
            val suggested = s.latestWeight?.let { Nutrition.suggestedKcal(it, settings.heightCm, age, settings.bodyType == BodyType.MALE, trainingDaysForKcal) }
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when {
                        settings.heightCm <= 0f || age <= 0 -> stringResource(R.string.goals_suggest_missing)
                        suggested != null -> stringResource(R.string.goals_suggested, "%,d".format(suggested), trainingDaysForKcal)
                        else -> stringResource(R.string.goals_suggest_missing)
                    },
                    style = MaterialTheme.typography.bodySmall, color = c.textSecondary, modifier = Modifier.weight(1f),
                )
                if (suggested != null && settings.heightCm > 0f && age > 0 && suggested != settings.kcalGoal) {
                    Spacer(Modifier.width(8.dp))
                    TonalPill(stringResource(R.string.goals_use), onClick = { vm.updateGoals(kcal = suggested) })
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
                    // Going below the plan's count sets an explicit target; landing back on the plan's count clears it.
                    onMinus = { vm.updateGoals(weekly = (weeklyShown - 1).coerceAtLeast(1).let { if (it == planDays) 0 else it }) },
                    onPlus = { vm.updateGoals(weekly = (weeklyShown + 1).coerceAtMost(7).let { if (it == planDays) 0 else it }) },
                    minValueWidth = 84.dp,
                )
            }
            Text(stringResource(R.string.profile_streak_explain), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
        }
        Spacer(Modifier.height(12.dp))

        // AI key
        SettingsRow(
            Icons.Rounded.Key, c.blue, c.blueTrack,
            stringResource(R.string.profile_ai_key),
            if (apiKey.isBlank()) stringResource(R.string.profile_key_not_set) else stringResource(R.string.profile_key_connected),
        ) { keyDialog = true }
        Spacer(Modifier.height(12.dp))
        SettingsRow(
            Icons.Rounded.Bolt, c.gold, c.goldTrack,
            stringResource(R.string.groq_key_title),
            if (groqKey.isBlank()) stringResource(R.string.groq_key_off) else stringResource(R.string.groq_key_on),
        ) { groqDialog = true }
        Spacer(Modifier.height(12.dp))

        // Appearance & feel
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
                    (context as? android.app.Activity)?.recreate()
                },
            )
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.profile_weights), style = MaterialTheme.typography.labelLarge, color = c.textSecondary)
            Spacer(Modifier.height(6.dp))
            SegmentedTabs(listOf(stringResource(R.string.unit_kilograms), stringResource(R.string.unit_pounds)), if (settings.unit == "lb") 1 else 0, onSelect = { vm.setUnit(if (it == 1) "lb" else "kg") })
            Row(Modifier.fillMaxWidth().padding(top = 10.dp).toggleable(settings.hapticsEnabled, role = Role.Switch) { vm.setHaptics(it) }, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.profile_vibration), style = MaterialTheme.typography.bodyLarge, color = c.textPrimary)
                    Text(stringResource(R.string.profile_vibration_hint), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                }
                Switch(settings.hapticsEnabled, null, colors = SwitchDefaults.colors(checkedTrackColor = c.teal, checkedThumbColor = c.card))
            }
        }
        Spacer(Modifier.height(12.dp))

        // Reminders
        FitCard {
            Row(Modifier.toggleable(settings.reminderHour >= 0, role = Role.Switch) { vm.setReminder(if (it) 18 else -1) }, verticalAlignment = Alignment.CenterVertically) {
                IconCircle(Icons.Rounded.Notifications, c.gold, c.goldTrack)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.profile_reminder), style = MaterialTheme.typography.titleMedium, color = c.textPrimary, modifier = Modifier.semantics { heading() })
                    Text(
                        if (settings.reminderHour < 0) stringResource(R.string.profile_reminder_off)
                        else stringResource(R.string.profile_reminder_on, "%02d:00".format(settings.reminderHour)),
                        style = MaterialTheme.typography.bodySmall, color = c.textSecondary,
                    )
                }
                Switch(settings.reminderHour >= 0, null, colors = SwitchDefaults.colors(checkedTrackColor = c.teal, checkedThumbColor = c.card))
            }
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
        Spacer(Modifier.height(12.dp))

        // Coach memory
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
        Spacer(Modifier.height(12.dp))

        // App lock
        FitCard {
            val canLock = remember {
                androidx.biometric.BiometricManager.from(context).canAuthenticate(
                    androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK or androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL,
                ) == androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS
            }
            Row(Modifier.toggleable(settings.biometricLock && canLock, enabled = canLock, role = Role.Switch) { vm.setLock(it) }, verticalAlignment = Alignment.CenterVertically) {
                IconCircle(Icons.Rounded.Lock, c.coral, c.coralTrack)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.profile_app_lock), style = MaterialTheme.typography.titleMedium, color = c.textPrimary, modifier = Modifier.semantics { heading() })
                    Text(
                        if (!canLock) stringResource(R.string.profile_lock_unavailable)
                        else if (settings.biometricLock) stringResource(R.string.profile_lock_on)
                        else stringResource(R.string.profile_lock_off),
                        style = MaterialTheme.typography.bodySmall, color = c.textSecondary,
                    )
                }
                Switch(settings.biometricLock && canLock, null, enabled = canLock, colors = SwitchDefaults.colors(checkedTrackColor = c.teal, checkedThumbColor = c.card))
            }
        }
        Spacer(Modifier.height(12.dp))

        // Progress
        SettingsRow(
            Icons.Rounded.ShowChart, c.teal, c.tealTrack,
            stringResource(R.string.profile_progress_title),
            stringResource(R.string.profile_progress_hint),
        ) { nav.navigate(com.forgequest.app.ui.Routes.PROGRESS) }
        Spacer(Modifier.height(12.dp))

        // Exercise library
        SettingsRow(
            Icons.Rounded.FitnessCenter, c.teal, c.tealTrack,
            stringResource(R.string.profile_exercise_library),
            stringResource(R.string.profile_exercise_library_hint),
        ) { nav.navigate(com.forgequest.app.ui.Routes.LIBRARY) }
        Spacer(Modifier.height(12.dp))

        // Hand + body
        SettingsRow(
            Icons.Rounded.PanTool, c.purple, c.purpleTrack,
            stringResource(R.string.profile_hand_size),
            if (settings.handLengthCm > 0f) stringResource(R.string.profile_hand_value, "%.1f".format(settings.handLengthCm), "%.1f".format(settings.palmWidthCm)) else stringResource(R.string.profile_hand_hint),
        ) { handDialog = true }
        Spacer(Modifier.height(12.dp))
        FitCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconCircle(Icons.Rounded.Accessibility, c.coral, c.coralTrack)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.profile_muscle_map), style = MaterialTheme.typography.titleMedium, color = c.textPrimary, modifier = Modifier.semantics { heading() })
                    Text(stringResource(R.string.profile_muscle_map_hint), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                }
            }
            Spacer(Modifier.height(16.dp))
            SegmentedTabs(
                listOf(stringResource(R.string.body_male), stringResource(R.string.body_female)),
                if (settings.bodyType == BodyType.FEMALE) 1 else 0,
                onSelect = { vm.setBody(if (it == 1) BodyType.FEMALE else BodyType.MALE) },
            )
        }
        Spacer(Modifier.height(12.dp))

        // Local backup file
        FitCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconCircle(Icons.Rounded.Backup, c.blue, c.blueTrack)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.profile_your_data), style = MaterialTheme.typography.titleMedium, color = c.textPrimary, modifier = Modifier.semantics { heading() })
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
        Spacer(Modifier.height(12.dp))

        // Drive
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
        Spacer(Modifier.height(12.dp))

        // About
        FitCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconCircle(Icons.Rounded.Info, c.textSecondary, c.cardHigh)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.profile_credits), style = MaterialTheme.typography.titleMedium, color = c.textPrimary, modifier = Modifier.semantics { heading() })
                    Text(stringResource(R.string.profile_credits_body), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                }
            }
            Spacer(Modifier.height(10.dp))
            TonalPill(stringResource(R.string.profile_hemo_youtube), onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/@HemoCalisthenics")))
            }, icon = Icons.Rounded.OpenInNew)
        }
        Spacer(Modifier.height(32.dp))
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
        PlanPreviewSheet(doc.plan, s.exercises, onApply = vm::applyDoc, onDismiss = { vm.docPreview.value = null })
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
