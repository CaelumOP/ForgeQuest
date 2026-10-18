package com.forgequest.app.ui.profile

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Accessibility
import androidx.compose.material.icons.rounded.Cloud
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
    data class RestorePreview(val source: String, val file: BackupFile)
    val restorePreview = MutableStateFlow<RestorePreview?>(null)
    val shareUri = MutableStateFlow<Uri?>(null)
    val safetyCopies = MutableStateFlow(app.backup.safetyCopies())
    private var afterConsent: (suspend (String) -> Unit)? = null

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

    fun setTheme(theme: String) = viewModelScope.launch { app.settings.update { it.copy(theme = theme) } }
    fun setUnit(unit: String) = viewModelScope.launch { app.settings.update { it.copy(unit = unit) } }
    fun setHaptics(on: Boolean) = viewModelScope.launch { app.settings.update { it.copy(hapticsEnabled = on) }; if (on) app.haptics.tick() }

    fun setBody(type: BodyType) = viewModelScope.launch { app.settings.update { it.copy(bodyType = type) } }
    fun setHand(length: Float, palm: Float) = viewModelScope.launch { app.settings.update { it.copy(handLengthCm = length, palmWidthCm = palm) } }
    fun logWeight(kg: Float) = viewModelScope.launch { app.progress.logWeight(kg) }
    fun markBadgesSeen() = viewModelScope.launch { app.progress.markBadgesSeen() }

    fun saveKey(key: String) {
        val trimmed = key.trim()
        if (trimmed.isEmpty()) {
            app.settings.setApiKey("")
            message.value = "AI key removed"
            return
        }
        busy.value = "Checking your key…"
        viewModelScope.launch {
            val result = app.gemini.discoverModels(trimmed)
            busy.value = null
            result.onSuccess { models ->
                app.settings.setApiKey(trimmed)
                app.settings.update { it.copy(fastModel = models.fast, smartModel = models.smart) }
                message.value = "Key saved. AI features are on."
            }.onFailure { e ->
                if (e is AiError.Offline) {
                    app.settings.setApiKey(trimmed)
                    message.value = "Saved. Couldn't check it while offline."
                } else {
                    message.value = (e as? AiError)?.userMessage ?: "Couldn't check that key."
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

    fun connectDrive() = withDrive("Connecting Google Drive…") { token ->
        val email = app.drive.accountEmail(token)
        app.settings.update { it.copy(driveEmail = email.ifBlank { "Connected" }) }
        app.drive.backup(token)
        message.value = "Google Drive connected and backed up"
    }

    fun backupNow() = withDrive("Backing up…") { token ->
        app.drive.backup(token)
        message.value = "Backed up to Google Drive"
    }

    fun restore() = withDrive("Reading your Drive backup…") { token ->
        restorePreview.value = RestorePreview("Google Drive", app.drive.fetchBackup(token))
    }

    // ------------------------------------------------------------ Local backup file

    fun exportData() {
        busy.value = "Preparing your backup…"
        viewModelScope.launch {
            try {
                shareUri.value = app.backup.exportForSharing()
            } catch (e: Exception) {
                message.value = "Couldn't create the backup file."
            } finally {
                busy.value = null
            }
        }
    }

    fun previewImport(uri: Uri) {
        busy.value = "Reading the file…"
        viewModelScope.launch {
            try {
                restorePreview.value = RestorePreview("this file", app.backup.read(uri))
            } catch (e: BackupError) {
                message.value = e.userMessage
            } finally {
                busy.value = null
            }
        }
    }

    fun previewUndoRestore() {
        val latest = safetyCopies.value.firstOrNull() ?: return
        busy.value = "Reading the copy…"
        viewModelScope.launch {
            try {
                restorePreview.value = RestorePreview("the copy saved before your last restore", app.backup.read(latest))
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
        busy.value = "Restoring…"
        viewModelScope.launch {
            try {
                app.backup.apply(preview.file)
                safetyCopies.value = app.backup.safetyCopies()
                message.value = "Restored from ${preview.source}"
            } catch (e: Exception) {
                message.value = "Couldn't restore that backup."
            } finally {
                busy.value = null
            }
        }
    }

    fun importDoc() = withDrive("Reading your Workout Plan doc…", withDocs = true) { token ->
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
            message.value = "Imported ${doc.docName}"
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
    LaunchedEffect(shareUri) {
        shareUri?.let { uri ->
            val send = Intent(Intent.ACTION_SEND).setType("application/json").putExtra(Intent.EXTRA_STREAM, uri)
                .putExtra(Intent.EXTRA_SUBJECT, "ForgeQuest backup").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.startActivity(Intent.createChooser(send, "Save your backup"))
            vm.shareUri.value = null
        }
    }
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); vm.message.value = null } }
    DisposableEffect(Unit) { onDispose { vm.markBadgesSeen() } }

    val s = snap ?: return Box(Modifier.fillMaxSize().background(c.background))
    val settings = s.settings

    Column(Modifier.fillMaxSize().background(c.background).statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Text("Profile", style = MaterialTheme.typography.headlineMedium, color = c.textPrimary, modifier = Modifier.padding(top = 12.dp, bottom = 16.dp))

        if (apiKey.isBlank()) {
            FitCard(onClick = { keyDialog = true }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconCircle(Icons.Rounded.Key, c.blue, c.blueTrack)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Turn on the coach and meal scanning", style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
                        Text("Paste a free Gemini key. Takes about a minute.", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                    }
                    PillButton("Add key", onClick = { keyDialog = true })
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
                    Text(settings.name.ifBlank { "Athlete" }, style = MaterialTheme.typography.titleLarge, color = c.textPrimary)
                    Text("Rank ${s.level.rank.letter} · ${s.level.rank.title} · Level ${s.level.level}", style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
                }
            }
            Spacer(Modifier.height(16.dp))
            ProgressLine(s.level.progress, c.blue, c.blueTrack, height = 8.dp)
            Spacer(Modifier.height(6.dp))
            Text("${s.level.xpIntoLevel} / ${s.level.xpForNextLevel} XP to level ${s.level.level + 1}", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth()) {
                com.forgequest.app.ui.components.StatColumn("${s.sessions.size}", "Workouts", c.teal, Modifier.weight(1f), onClick = { nav.navigate(com.forgequest.app.ui.Routes.HISTORY) })
                com.forgequest.app.ui.components.StatColumn("${s.streak.best}", "Best streak (wk)", c.gold, Modifier.weight(1f))
                com.forgequest.app.ui.components.StatColumn("${s.badges.size}/${Badges.all.size}", "Badges", c.purple, Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(12.dp))

        // Badges
        FitCard(onClick = { nav.navigate(com.forgequest.app.ui.Routes.BADGES) }) {
            val owned = s.badges.associateBy { it.id }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Badges", style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
                    Text("${s.badges.size} of ${Badges.all.size} unlocked", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                }
                Text("See all", style = MaterialTheme.typography.labelLarge, color = c.teal)
            }
            val latest = s.badges.sortedByDescending { it.unlockedAt }.take(4).mapNotNull { b -> Badges.byId[b.id]?.let { it to b } }
            if (latest.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth()) {
                    latest.forEach { (def, got) ->
                        BadgeChip(def.title, def.description, badgeIcon(def.category), unlocked = true, isNew = !got.seen, modifier = Modifier.weight(1f))
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
                    Text("Body weight", style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
                    Text(s.latestWeight?.let { settings.weightUnit.format(it) } ?: "Not logged", style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
                }
                TonalPill("Log", onClick = { weightDialog = true })
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
                Text("Goals", style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
            }
            Spacer(Modifier.height(12.dp))
            SettingStepper("Daily protein", settings.proteinGoal.toFloat(), { vm.updateGoals(protein = it.toInt()) }, step = 5f, min = 40f, max = 400f, unit = "g")
            SettingStepper("Daily calories", settings.kcalGoal.toFloat(), { vm.updateGoals(kcal = it.toInt()) }, step = 50f, min = 1000f, max = 6000f, unit = "kcal")
            val planDays = s.plan?.trainingDays?.size ?: 0
            val weeklyShown = if (settings.weeklyTargetOverride == 0) planDays else settings.weeklyTargetOverride
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Workouts per week", style = MaterialTheme.typography.bodyLarge, color = c.textPrimary)
                    if (settings.weeklyTargetOverride == 0) Text("From your plan", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                }
                Stepper(
                    "$weeklyShown",
                    // Going below the plan's count sets an explicit target; landing back on the plan's count clears it.
                    onMinus = { vm.updateGoals(weekly = (weeklyShown - 1).coerceAtLeast(1).let { if (it == planDays) 0 else it }) },
                    onPlus = { vm.updateGoals(weekly = (weeklyShown + 1).coerceAtMost(7).let { if (it == planDays) 0 else it }) },
                    minValueWidth = 84.dp,
                )
            }
            Text("Your weekly streak counts weeks where you reach this number.", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
        }
        Spacer(Modifier.height(12.dp))

        // AI key
        SettingsRow(
            Icons.Rounded.Key, c.blue, c.blueTrack,
            "AI key",
            if (apiKey.isBlank()) "Not set · meal scan and coach are off" else "Connected · stored encrypted on this phone",
        ) { keyDialog = true }
        Spacer(Modifier.height(12.dp))

        // Appearance & feel
        FitCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconCircle(Icons.Rounded.Palette, c.blue, c.blueTrack)
                Spacer(Modifier.width(14.dp))
                Text("Appearance", style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
            }
            Spacer(Modifier.height(14.dp))
            Text("Theme", style = MaterialTheme.typography.labelLarge, color = c.textSecondary)
            Spacer(Modifier.height(6.dp))
            SegmentedTabs(
                listOf("System", "Light", "Dark"),
                when (settings.theme) { "light" -> 1; "dark" -> 2; else -> 0 },
                onSelect = { vm.setTheme(listOf("system", "light", "dark")[it]) },
            )
            Spacer(Modifier.height(12.dp))
            Text("Weights", style = MaterialTheme.typography.labelLarge, color = c.textSecondary)
            Spacer(Modifier.height(6.dp))
            SegmentedTabs(listOf("Kilograms", "Pounds"), if (settings.unit == "lb") 1 else 0, onSelect = { vm.setUnit(if (it == 1) "lb" else "kg") })
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Vibration feedback", style = MaterialTheme.typography.bodyLarge, color = c.textPrimary)
                    Text("Ticks on steppers, a thud when a set is done", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                }
                Switch(settings.hapticsEnabled, { vm.setHaptics(it) }, colors = SwitchDefaults.colors(checkedTrackColor = c.teal, checkedThumbColor = c.card))
            }
        }
        Spacer(Modifier.height(12.dp))

        // Reminders
        FitCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconCircle(Icons.Rounded.Notifications, c.gold, c.goldTrack)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Workout reminder", style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
                    Text(
                        if (settings.reminderHour < 0) "Off. A nudge on planned days if nothing is logged yet."
                        else "On planned days at ${"%02d:00".format(settings.reminderHour)}, only if nothing is logged yet.",
                        style = MaterialTheme.typography.bodySmall, color = c.textSecondary,
                    )
                }
                Switch(settings.reminderHour >= 0, { vm.setReminder(if (it) 18 else -1) }, colors = SwitchDefaults.colors(checkedTrackColor = c.teal, checkedThumbColor = c.card))
            }
            if (settings.reminderHour >= 0) {
                var typingHour by remember { mutableStateOf(false) }
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Time", style = MaterialTheme.typography.bodyLarge, color = c.textPrimary, modifier = Modifier.weight(1f))
                    Stepper(
                        "%02d:00".format(settings.reminderHour),
                        onMinus = { vm.setReminder((settings.reminderHour + 23) % 24) },
                        onPlus = { vm.setReminder((settings.reminderHour + 1) % 24) },
                        minValueWidth = 72.dp,
                        onValueClick = { typingHour = true },
                    )
                }
                if (typingHour) {
                    NumberEntryDialog("Reminder hour (0–23)", settings.reminderHour.toFloat(), "h", 0, 0f, 23f, onDismiss = { typingHour = false }, onConfirm = { vm.setReminder(it.toInt()); typingHour = false })
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
                    Text("What the coach remembers", style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
                    Text(
                        if (settings.coachNotes.isBlank()) "Tell the coach about injuries, equipment or preferences and it keeps them here."
                        else "Used in every conversation. Tap a line to remove it.",
                        style = MaterialTheme.typography.bodySmall, color = c.textSecondary,
                    )
                }
                TonalPill("Add", onClick = { notesDialog = true })
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
                        Icon(Icons.Rounded.Close, contentDescription = "Remove", tint = c.textSecondary, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        // Exercise library
        SettingsRow(
            Icons.Rounded.FitnessCenter, c.teal, c.tealTrack,
            "Exercise library",
            "Rename, merge or delete exercises you or the coach created",
        ) { nav.navigate(com.forgequest.app.ui.Routes.LIBRARY) }
        Spacer(Modifier.height(12.dp))

        // Hand + body
        SettingsRow(
            Icons.Rounded.PanTool, c.purple, c.purpleTrack,
            "Hand size (optional)",
            if (settings.handLengthCm > 0f) "%.1f cm long · %.1f cm palm".format(settings.handLengthCm, settings.palmWidthCm) else "Helps portion estimates when your hand is in the photo",
        ) { handDialog = true }
        Spacer(Modifier.height(12.dp))
        FitCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconCircle(Icons.Rounded.Accessibility, c.coral, c.coralTrack)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Muscle map", style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
                    Text("Body shown on the recovery map", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                }
            }
            Spacer(Modifier.height(16.dp))
            SegmentedTabs(
                listOf("Male", "Female"),
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
                    Text("Your data", style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
                    Text("Save a file to keep, or bring everything to a new phone. Photos and your AI key stay out of it.", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                }
            }
            Spacer(Modifier.height(14.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton("Export file", onClick = vm::exportData, icon = Icons.Rounded.Share)
                TonalPill("Import file", onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }, icon = Icons.Rounded.Download)
                if (safetyCopies.isNotEmpty()) TonalPill("Undo last restore", onClick = vm::previewUndoRestore, icon = Icons.Rounded.Undo)
            }
        }
        Spacer(Modifier.height(12.dp))

        // Drive
        FitCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconCircle(Icons.Rounded.Cloud, c.teal, c.tealTrack)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Google Drive", style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
                    Text(
                        when {
                            settings.driveEmail.isBlank() -> "Daily backup to a hidden app folder in your Drive. Nothing else in your Drive is touched."
                            settings.lastBackupAt > 0 -> "${settings.driveEmail} · backed up ${DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(settings.lastBackupAt))}"
                            else -> settings.driveEmail
                        },
                        style = MaterialTheme.typography.bodySmall, color = c.textSecondary,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            if (settings.driveEmail.isBlank()) {
                PillButton("Connect Google Drive", onClick = vm::connectDrive, modifier = Modifier.fillMaxWidth())
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillButton("Back up now", onClick = vm::backupNow)
                    TonalPill(if (settings.driveDocsEnabled) "Import plan from Docs" else "Import plan from Docs…", onClick = vm::importDoc)
                    if (settings.driveDocsEnabled) TonalPill("Stop reading Docs", onClick = vm::disableDocs, tint = c.textSecondary)
                    TonalPill("Restore", onClick = vm::restore)
                    TonalPill("Disconnect", onClick = vm::disconnectDrive, tint = c.textSecondary)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    if (settings.driveDocsEnabled) "Backs up daily on its own. Your newest Google Doc named \"Workout Plan\" is checked daily too; changes appear in the Coach tab for you to apply."
                    else "Backs up daily on its own. Importing a plan from Google Docs asks separately for permission to read your Drive; you can turn that off any time.",
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
                    Text("Credits", style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
                    Text("Foundation routine by Hemo Calisthenics. Anatomy art from react-native-body-highlighter (MIT).", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                }
            }
            Spacer(Modifier.height(10.dp))
            TonalPill("Hemo Calisthenics on YouTube", onClick = {
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
            title = { Text("Gemini AI key", color = c.textPrimary) },
            text = {
                Column {
                    Text("Get a free key from Google AI Studio, then paste it here. It stays encrypted on this phone.", color = c.textSecondary, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(6.dp))
                    Text("Meal photos and your training data are sent to Google Gemini under this key. Free keys let Google use them to improve its models.", color = c.textSecondary, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(10.dp))
                    TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/apikey"))) }) {
                        Text("Open Google AI Studio", color = c.blue)
                    }
                    OutlinedTextField(
                        value = key, onValueChange = { key = it.trim() }, singleLine = true,
                        placeholder = { Text(if (apiKey.isBlank()) "Paste key" else "Paste a new key") },
                        visualTransformation = PasswordVisualTransformation(), shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = c.teal, unfocusedBorderColor = c.divider, focusedTextColor = c.textPrimary, unfocusedTextColor = c.textPrimary),
                    )
                }
            },
            confirmButton = { TextButton(onClick = { keyDialog = false; vm.saveKey(key) }, enabled = key.isNotBlank()) { Text("Save", color = c.teal) } },
            dismissButton = {
                Row {
                    if (apiKey.isNotBlank()) TextButton(onClick = { keyDialog = false; vm.saveKey("") }) { Text("Remove", color = c.coral) }
                    TextButton(onClick = { keyDialog = false }) { Text("Cancel", color = c.textSecondary) }
                }
            },
        )
    }

    if (weightDialog) {
        var kg by remember { mutableFloatStateOf(s.latestWeight ?: 75f) }
        AlertDialog(
            onDismissRequest = { weightDialog = false },
            containerColor = c.card,
            title = { Text("Log body weight", color = c.textPrimary) },
            text = {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    val unit = settings.weightUnit
                    NumberStepper(unit.fromKg(kg), { kg = unit.toKg(it) }, step = unit.bodyStep, min = unit.fromKg(30f), max = unit.fromKg(250f), unit = unit.label, decimals = 1, title = "Body weight", minValueWidth = 96.dp)
                }
            },
            confirmButton = { TextButton(onClick = { vm.logWeight(kg); weightDialog = false }) { Text("Save", color = c.teal) } },
            dismissButton = { TextButton(onClick = { weightDialog = false }) { Text("Cancel", color = c.textSecondary) } },
        )
    }

    if (notesDialog) {
        var note by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { notesDialog = false },
            containerColor = c.card,
            title = { Text("Something the coach should know", color = c.textPrimary) },
            text = {
                Column {
                    Text("One fact per note, e.g. \"Left knee hurts in deep squats\" or \"Only have a pull-up bar and rings\".", color = c.textSecondary, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = note, onValueChange = { note = it.take(160) }, singleLine = true, shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = c.teal, unfocusedBorderColor = c.divider, focusedTextColor = c.textPrimary, unfocusedTextColor = c.textPrimary),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = { TextButton(onClick = { vm.addNote(note); notesDialog = false }, enabled = note.isNotBlank()) { Text("Save", color = c.teal) } },
            dismissButton = { TextButton(onClick = { notesDialog = false }) { Text("Cancel", color = c.textSecondary) } },
        )
    }

    if (handDialog) {
        var length by remember { mutableFloatStateOf(if (settings.handLengthCm > 0f) settings.handLengthCm else 18.5f) }
        var palm by remember { mutableFloatStateOf(if (settings.palmWidthCm > 0f) settings.palmWidthCm else 8.5f) }
        AlertDialog(
            onDismissRequest = { handDialog = false },
            containerColor = c.card,
            title = { Text("Hand size", color = c.textPrimary) },
            text = {
                Column {
                    Text("Measure from wrist crease to the tip of your middle finger, and across your palm.", color = c.textSecondary, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                    SettingStepper("Length", length, { length = it }, step = 0.5f, min = 10f, max = 28f, unit = "cm", decimals = 1)
                    SettingStepper("Palm width", palm, { palm = it }, step = 0.5f, min = 5f, max = 14f, unit = "cm", decimals = 1)
                }
            },
            confirmButton = { TextButton(onClick = { vm.setHand(length, palm); handDialog = false }) { Text("Save", color = c.teal) } },
            dismissButton = {
                Row {
                    if (settings.handLengthCm > 0f) TextButton(onClick = { vm.setHand(0f, 0f); handDialog = false }) { Text("Clear", color = c.coral) }
                    TextButton(onClick = { handDialog = false }) { Text("Cancel", color = c.textSecondary) }
                }
            },
        )
    }

    restorePreview?.let { preview ->
        AlertDialog(
            onDismissRequest = { vm.restorePreview.value = null },
            containerColor = c.card,
            title = { Text("Restore from ${preview.source}?", color = c.textPrimary) },
            text = {
                Column {
                    Text(preview.file.summary(), color = c.textPrimary, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Everything on this phone is replaced. A copy of what's here now is kept, so you can undo this from the Your data card.",
                        color = c.textSecondary, style = MaterialTheme.typography.bodyMedium,
                    )
                }
            },
            confirmButton = { TextButton(onClick = vm::applyRestore) { Text("Restore", color = c.coral) } },
            dismissButton = { TextButton(onClick = { vm.restorePreview.value = null }) { Text("Cancel", color = c.textSecondary) } },
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
            text = { Text(if (unlocked) "Unlocked. $description." else "$description to unlock.", color = c.textSecondary) },
            confirmButton = { TextButton(onClick = { open = false }) { Text("OK", color = c.teal) } },
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
