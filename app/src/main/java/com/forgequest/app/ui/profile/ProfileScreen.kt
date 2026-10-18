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
import com.forgequest.app.data.drive.DriveSync
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

    fun restore() = withDrive("Restoring…") { token ->
        app.drive.restore(token)
        message.value = "Restored from Google Drive"
    }

    fun importDoc() = withDrive("Reading your Workout Plan doc…") { token ->
        if (app.settings.apiKey.value.isBlank()) throw AiError.MissingKey
        docPreview.value = app.drive.fetchPlanDoc(token, 0)
    }

    fun applyDoc() {
        val doc = docPreview.value ?: return
        docPreview.value = null
        viewModelScope.launch {
            app.drive.applyDocPlan(doc)
            message.value = "Imported ${doc.docName}"
        }
    }

    fun disconnectDrive() = viewModelScope.launch { app.settings.update { it.copy(driveEmail = "") } }
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
    val snackbar = LocalSnackbar.current
    val context = LocalContext.current
    val c = Fit.colors
    var keyDialog by remember { mutableStateOf(false) }
    var weightDialog by remember { mutableStateOf(false) }
    var handDialog by remember { mutableStateOf(false) }
    var confirmRestore by remember { mutableStateOf(false) }

    val consentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        vm.onConsentResult(r.resultCode == Activity.RESULT_OK, r.data)
    }
    LaunchedEffect(consent) { consent?.let { consentLauncher.launch(IntentSenderRequest.Builder(it.intentSender).build()) } }
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); vm.message.value = null } }
    DisposableEffect(Unit) { onDispose { vm.markBadgesSeen() } }

    val s = snap ?: return Box(Modifier.fillMaxSize().background(c.background))
    val settings = s.settings

    Column(Modifier.fillMaxSize().background(c.background).statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Text("Profile", style = MaterialTheme.typography.headlineMedium, color = c.textPrimary, modifier = Modifier.padding(top = 12.dp, bottom = 16.dp))

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
                com.forgequest.app.ui.components.StatColumn("${s.sessions.size}", "Workouts", c.teal, Modifier.weight(1f))
                com.forgequest.app.ui.components.StatColumn("${s.streak.best}", "Best streak (wk)", c.gold, Modifier.weight(1f))
                com.forgequest.app.ui.components.StatColumn("${s.badges.size}/${Badges.all.size}", "Badges", c.purple, Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(12.dp))

        // Badges
        FitCard {
            Text("Badges", style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
            val owned = s.badges.associateBy { it.id }
            com.forgequest.app.domain.BadgeCategory.entries.forEach { cat ->
                Spacer(Modifier.height(14.dp))
                Text(cat.label, style = MaterialTheme.typography.labelLarge, color = c.textSecondary)
                Spacer(Modifier.height(8.dp))
                Badges.all.filter { it.category == cat }.chunked(4).forEach { row ->
                    Row(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                        row.forEach { b ->
                            val got = owned[b.id]
                            BadgeChip(b.title, b.description, badgeIcon(cat), unlocked = got != null, isNew = got?.seen == false, modifier = Modifier.weight(1f))
                        }
                        repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
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
                    Text(s.latestWeight?.let { "%.1f kg".format(it) } ?: "Not logged", style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
                }
                TonalPill("Log", onClick = { weightDialog = true })
            }
            val recent = s.weights.takeLast(30)
            if (recent.size >= 2) {
                Spacer(Modifier.height(16.dp))
                LineChart(recent.map { it.kg }, Modifier.fillMaxWidth().height(120.dp))
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

        // Drive
        FitCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconCircle(Icons.Rounded.Cloud, c.teal, c.tealTrack)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Google Drive", style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
                    Text(
                        when {
                            settings.driveEmail.isBlank() -> "Back up your data and import plans from Google Docs"
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
                    TonalPill("Import plan from Docs", onClick = vm::importDoc)
                    TonalPill("Restore", onClick = { confirmRestore = true })
                    TonalPill("Disconnect", onClick = vm::disconnectDrive, tint = c.textSecondary)
                }
                Spacer(Modifier.height(8.dp))
                Text("Backs up daily on its own. Name a Google Doc \"Workout Plan\" and changes to it are imported automatically.", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
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
                    NumberStepper(kg, { kg = it }, step = 0.1f, min = 30f, max = 250f, unit = "kg", decimals = 1, title = "Body weight", minValueWidth = 96.dp)
                }
            },
            confirmButton = { TextButton(onClick = { vm.logWeight(kg); weightDialog = false }) { Text("Save", color = c.teal) } },
            dismissButton = { TextButton(onClick = { weightDialog = false }) { Text("Cancel", color = c.textSecondary) } },
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

    if (confirmRestore) {
        AlertDialog(
            onDismissRequest = { confirmRestore = false },
            containerColor = c.card,
            title = { Text("Restore from Google Drive?", color = c.textPrimary) },
            text = { Text("This replaces everything on this phone with your last Drive backup.", color = c.textSecondary) },
            confirmButton = { TextButton(onClick = { confirmRestore = false; vm.restore() }) { Text("Restore", color = c.coral) } },
            dismissButton = { TextButton(onClick = { confirmRestore = false }) { Text("Cancel", color = c.textSecondary) } },
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

private fun badgeIcon(cat: com.forgequest.app.domain.BadgeCategory): ImageVector = when (cat) {
    com.forgequest.app.domain.BadgeCategory.CONSISTENCY -> Icons.Rounded.LocalFireDepartment
    com.forgequest.app.domain.BadgeCategory.SKILL -> Icons.Rounded.SportsGymnastics
    com.forgequest.app.domain.BadgeCategory.STRENGTH -> Icons.Rounded.FitnessCenter
    com.forgequest.app.domain.BadgeCategory.NUTRITION -> Icons.Rounded.Restaurant
}

@Composable
private fun BadgeChip(title: String, description: String, icon: ImageVector, unlocked: Boolean, isNew: Boolean, modifier: Modifier = Modifier) {
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
