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

internal fun summary(vararg parts: String?) = parts.filterNotNull().filter { it.isNotBlank() }.joinToString(" · ")

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
    // In "Just log" mode badges aren't shown here, so they stay new for when it's turned off.
    DisposableEffect(Unit) { onDispose { if (vm.snapshot.value?.settings?.justLog != true) vm.markBadgesSeen() } }

    val s = snap ?: return Box(Modifier.fillMaxSize().background(c.background))
    val settings = s.settings
    val configuredEq = settings.configuredEquipment
    val justLog = settings.justLog

    Column(Modifier.fillMaxSize().background(c.background).statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = ForgeSpace.Gutter)) {
        Text(stringResource(R.string.tab_profile), style = MaterialTheme.typography.headlineMedium, color = c.textPrimary, modifier = Modifier.padding(top = 12.dp, bottom = 16.dp))

        if (apiKey.isBlank()) {
            FitCard(onClick = { keyDialog = true }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconCircle(Icons.Rounded.Key, c.teal, c.tealTrack)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.profile_setup_title), style = MaterialTheme.typography.titleMedium, color = c.textPrimary, modifier = Modifier.semantics { heading() })
                        Text(stringResource(R.string.profile_setup_body), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                    }
                    PillButton(stringResource(R.string.profile_add_key), onClick = { keyDialog = true })
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        // Rank ("Just log" mode: the name and workout count only; XP keeps adding up out of sight)
        FitCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!justLog) {
                    MiniRing(s.level.progress, c.blue, c.blueTrack, size = 64.dp, stroke = 4.dp) {
                        Text(s.level.rank.mark(s.level.level), style = MaterialTheme.typography.headlineSmall, color = c.blue, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(16.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(settings.name.ifBlank { stringResource(R.string.profile_default_name) }, style = MaterialTheme.typography.titleLarge, color = c.textPrimary)
                    if (!justLog) Text(stringResource(R.string.profile_rank_line, s.level.rank.letter, s.level.rank.localTitle(), s.level.level), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
                }
            }
            if (!justLog) {
            Spacer(Modifier.height(16.dp))
            ProgressLine(s.level.progress, c.blue, c.blueTrack, height = 8.dp)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.profile_xp_line, s.level.xpIntoLevel, s.level.xpForNextLevel, s.level.level + 1), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
            // How XP is earned and what the letter means, which nothing else on screen says.
            Text(
                stringResource(R.string.profile_xp_how, com.forgequest.app.domain.Xp.PER_WORKOUT, com.forgequest.app.domain.Xp.PER_SET, com.forgequest.app.domain.Xp.PER_PR, com.forgequest.app.domain.Xp.PER_BADGE),
                style = MaterialTheme.typography.bodySmall, color = c.textSecondary, modifier = Modifier.padding(top = 4.dp),
            )
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth()) {
                com.forgequest.app.ui.components.StatColumn("${s.sessions.size}", stringResource(R.string.tab_workouts), c.teal, Modifier.weight(1f), onClick = { nav.navigate(com.forgequest.app.ui.Routes.HISTORY) })
                if (!justLog) {
                    com.forgequest.app.ui.components.StatColumn("${s.streak.best}", stringResource(R.string.profile_best_streak), c.gold, Modifier.weight(1f))
                    com.forgequest.app.ui.components.StatColumn("${s.badges.size}/${Badges.all.size}", stringResource(R.string.profile_badges), c.purple, Modifier.weight(1f))
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        // Badges
        if (!justLog) FitCard(onClick = { nav.navigate(com.forgequest.app.ui.Routes.BADGES) }) {
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
        if (!justLog) Spacer(Modifier.height(12.dp))

        // Body weight stays out: it is logged often.
        // Body weight
        FitCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconCircle(Icons.Rounded.MonitorWeight, c.teal, c.tealTrack)
                Spacer(Modifier.width(12.dp))
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
        Row(Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ShortcutCard(Icons.AutoMirrored.Rounded.ShowChart, c.teal, c.tealTrack, stringResource(R.string.profile_progress_title), Modifier.weight(1f).fillMaxHeight()) { nav.navigate(com.forgequest.app.ui.Routes.PROGRESS) }
            ShortcutCard(Icons.Rounded.FitnessCenter, c.teal, c.tealTrack, stringResource(R.string.profile_exercise_library), Modifier.weight(1f).fillMaxHeight()) { nav.navigate(com.forgequest.app.ui.Routes.LIBRARY) }
        }
        Spacer(Modifier.height(12.dp))

        // Settings fold into categories; which ones are open is remembered on the phone.
        val uiPrefs = remember { context.getSharedPreferences("profile_ui", android.content.Context.MODE_PRIVATE) }
        var openGroups by remember { mutableStateOf(uiPrefs.getStringSet("open_groups", null)?.toSet() ?: emptySet()) }
        fun toggleGroup(id: String) {
            openGroups = if (id in openGroups) openGroups - id else openGroups + id
            uiPrefs.edit().putStringSet("open_groups", openGroups).apply()
        }

        SettingsSection(stringResource(R.string.settings_section_settings))

        BodySettingsGroup(
            vm, s, openGroups, { toggleGroup(it) }, probe, { startHealthCheck() }, healthAccess,
            onOpenHandDialog = { handDialog = true },
        )

        GoalsSettingsGroup(vm, s, openGroups, { toggleGroup(it) })

        TrainingSettingsGroup(vm, s, openGroups, { toggleGroup(it) })

        CoachSettingsGroup(
            vm, s, apiKey, groqKey, openGroups, { toggleGroup(it) },
            onOpenKeyDialog = { keyDialog = true },
            onOpenGroqDialog = { groqDialog = true },
            onOpenNotesDialog = { notesDialog = true },
        )

        AppSettingsGroup(vm, s, nav, openGroups, { toggleGroup(it) })

        BackupSettingsGroup(vm, s, openGroups, { toggleGroup(it) }, saveLauncher, importLauncher)

        AboutSettingsGroup(vm, openGroups, { toggleGroup(it) })
        val latest by vm.update.collectAsState()
        latest?.let { v ->
            AlertDialog(
                onDismissRequest = { vm.update.value = null },
                containerColor = c.card,
                title = { Text(stringResource(R.string.update_available, v.versionName), color = c.textPrimary) },
                // What's new shows once, after the update installs (WhatsNewDialog), not here as well.
                text = { Text(stringResource(R.string.update_notes_after), color = c.textSecondary) },
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
            icon = { Icon(Icons.Rounded.Lock, contentDescription = null, tint = c.teal) },
            title = { Text(stringResource(R.string.key_dialog_title), color = c.textPrimary) },
            text = {
                Column {
                    Text(stringResource(R.string.key_dialog_body), color = c.textSecondary, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.key_dialog_privacy), color = c.textSecondary, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/apikey"))) }) {
                        Text(stringResource(R.string.key_dialog_open_studio), color = c.teal)
                    }
                    OutlinedTextField(
                        value = key, onValueChange = { key = it.trim() }, singleLine = true,
                        placeholder = { Text(if (apiKey.isBlank()) stringResource(R.string.key_paste) else stringResource(R.string.key_paste_new)) },
                        visualTransformation = PasswordVisualTransformation(), shape = ForgeShape.Md,
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
            icon = { Icon(Icons.Rounded.Bolt, contentDescription = null, tint = c.teal) },
            title = { Text(stringResource(R.string.groq_key_title), color = c.textPrimary) },
            text = {
                Column {
                    Text(stringResource(R.string.groq_dialog_body), color = c.textSecondary, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://console.groq.com/keys"))) }) {
                        Text(stringResource(R.string.groq_dialog_open), color = c.teal)
                    }
                    OutlinedTextField(
                        value = key, onValueChange = { key = it.trim() }, singleLine = true,
                        placeholder = { Text(if (groqKey.isBlank()) stringResource(R.string.key_paste) else stringResource(R.string.key_paste_new)) },
                        visualTransformation = PasswordVisualTransformation(), shape = ForgeShape.Md,
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
                        value = note, onValueChange = { note = it.take(160) }, singleLine = true, shape = ForgeShape.Md,
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
