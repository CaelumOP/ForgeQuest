package com.forgequest.app.ui.profile

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.forgequest.app.R
import com.forgequest.app.ui.LocalSnackbar
import com.forgequest.app.ui.Routes
import com.forgequest.app.ui.appViewModel
import com.forgequest.app.ui.components.toast
import com.forgequest.app.ui.localSummary
import com.forgequest.app.ui.theme.Fit
import com.forgequest.app.ui.theme.ForgeShape
import com.forgequest.app.ui.theme.ForgeSpace
import com.forgequest.app.ui.workouts.PlanPreviewSheet
import kotlinx.coroutines.launch

/** Group ids in list order, with the title each page shows. */
private val groupTitles = listOf(
    "body" to R.string.settings_group_body,
    "goals" to R.string.settings_group_goals,
    "training" to R.string.settings_group_training,
    "app" to R.string.settings_group_app,
    "backup" to R.string.settings_group_backup,
    "coach" to R.string.settings_group_coach,
    "about" to R.string.settings_group_about,
)

/**
 * The settings list (groupId null), or one group's page. The Drive, import, Health Connect and key dialogs live
 * here because the backup, body and coach groups need them.
 */
@Composable
fun SettingsScreen(nav: NavHostController, groupId: String?) {
    val vm = appViewModel { ProfileViewModel(it) }
    val snap by vm.snapshot.collectAsState()
    val apiKey by vm.apiKey.collectAsState()
    val aiOn by vm.aiOn.collectAsState()
    val message by vm.message.collectAsState()
    val busy by vm.busy.collectAsState()
    val consent by vm.consent.collectAsState()
    val restorePreview by vm.restorePreview.collectAsState()
    val shareUri by vm.shareUri.collectAsState()
    val snackbar = LocalSnackbar.current
    val context = LocalContext.current
    val c = Fit.colors
    var keyDialog by remember { mutableStateOf(false) }
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

    val s = snap ?: return Box(Modifier.fillMaxSize().background(c.background))
    val configuredEq = s.settings.configuredEquipment
    val settings = s.settings
    val id = groupId?.takeIf { g -> groupTitles.any { it.first == g } }
    val onGroup: (String) -> Unit = { nav.navigate(Routes.settingsGroup(it)) }

    Column(Modifier.fillMaxSize().background(c.background).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.cd_back), tint = c.textPrimary)
            }
            Text(
                stringResource(groupTitles.firstOrNull { it.first == id }?.second ?: R.string.settings_section_settings),
                style = MaterialTheme.typography.titleLarge, color = c.textPrimary, modifier = Modifier.weight(1f).semantics { heading() },
            )
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = ForgeSpace.Gutter)) {
            Spacer(Modifier.height(8.dp))
            CompositionLocalProvider(LocalGroupMode provides if (id == null) GroupMode.Row else GroupMode.Page) {
                // Language and theme are what people look for first, so "App and appearance" leads the list.
                if (id == null || id == "app") AppSettingsGroup(vm, s, onGroup)
                if (id == null || id == "body") BodySettingsGroup(
                    vm, s, onGroup, probe, { startHealthCheck() }, healthAccess,
                    onOpenHandDialog = { handDialog = true },
                )
                if (id == null || id == "goals") GoalsSettingsGroup(vm, s, onGroup)
                if (id == null || id == "training") TrainingSettingsGroup(vm, s, nav, onGroup)
                if (id == null || id == "backup") BackupSettingsGroup(vm, s, onGroup, saveLauncher, importLauncher)
                if (id == null || id == "coach") CoachSettingsGroup(
                    vm, nav, s, apiKey, aiOn, onGroup,
                    onOpenKeyDialog = { keyDialog = true },
                    onOpenNotesDialog = { notesDialog = true },
                )
                if (id == null || id == "about") AboutSettingsGroup(vm, onGroup)
            }
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
}
