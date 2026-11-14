package com.forgequest.app.ui.profile

import androidx.compose.foundation.layout.heightIn
import com.forgequest.app.ui.theme.ForgeShape
import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Accessibility
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PanTool
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.TrackChanges
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.forgequest.app.R
import com.forgequest.app.data.ProgressSnapshot
import com.forgequest.app.data.prefs.BodyType
import com.forgequest.app.domain.CutPace
import com.forgequest.app.domain.DayActivity
import com.forgequest.app.domain.Dates
import com.forgequest.app.domain.GainPace
import com.forgequest.app.domain.Nutrition
import com.forgequest.app.domain.NutritionGoal
import com.forgequest.app.domain.WeightTrend
import com.forgequest.app.domain.WeightUnit
import com.forgequest.app.ui.descRes
import com.forgequest.app.ui.labelRes
import com.forgequest.app.ui.CalorieUi
import com.forgequest.app.ui.components.ChoiceChip
import com.forgequest.app.ui.components.FitCard
import com.forgequest.app.ui.components.FitSwitch
import com.forgequest.app.ui.components.IconCircle
import com.forgequest.app.ui.components.NumberEntryDialog
import com.forgequest.app.ui.components.PillButton
import com.forgequest.app.ui.components.SegmentedTabs
import com.forgequest.app.ui.components.Stepper
import com.forgequest.app.ui.components.TonalPill
import com.forgequest.app.ui.components.toast
import com.forgequest.app.ui.theme.Fit
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@Composable
internal fun CoachSettingsGroup(
    vm: SettingsViewModel,
    nav: NavHostController,
    s: ProgressSnapshot,
    apiKey: String,
    aiOn: Boolean,
    toggleGroup: (String) -> Unit,
    onOpenKeyDialog: () -> Unit,
) {
    val c = Fit.colors
    val context = LocalContext.current
    val settings = s.settings

    SettingsGroup(
        Icons.Rounded.Psychology, c.teal, c.tealTrack, stringResource(R.string.settings_group_coach),
        if (aiOn) stringResource(if (apiKey.isBlank()) R.string.summary_gemini_off else R.string.summary_gemini_on) else stringResource(R.string.summary_ai_off),
        onToggle = { toggleGroup("coach") },
    ) {
        // 1. The Gemini app (recommended): plan prompt and reply, and meals.
        Text(stringResource(R.string.settings_ai_gemini_title), style = MaterialTheme.typography.titleMedium, color = c.textPrimary, modifier = Modifier.padding(top = 4.dp).semantics { heading() })
        Text(stringResource(R.string.settings_ai_gemini_body), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
        FitCard {
            com.forgequest.app.ui.components.GeminiPlanPills(onPaste = { nav.navigate(com.forgequest.app.ui.Routes.plan(paste = true)) })
        }
        val clipboard = com.forgequest.app.ui.components.rememberTextClipboard()
        FitCard {
            SwitchHeader(Icons.Rounded.Restaurant, c.teal, c.tealTrack, stringResource(R.string.meals_title), stringResource(R.string.meals_hint), settings.geminiMealsEnabled) { vm.setGeminiMeals(it) }
            if (settings.geminiMealsEnabled) {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TonalPill(stringResource(R.string.bridge_sync), onClick = vm::syncMeals)
                    TonalPill(stringResource(R.string.meals_copy_gem), onClick = {
                        clipboard.copy(com.forgequest.app.ui.components.SkillPrompts.food(context, settings.handLengthCm, settings.palmWidthCm))
                        context.toast(context.getString(R.string.bridge_prompt_copied))
                    })
                }
            }
        }
        // 2. The free API key: basic, so it comes second.
        Text(stringResource(R.string.settings_ai_key_title), style = MaterialTheme.typography.titleMedium, color = c.textPrimary, modifier = Modifier.padding(top = 8.dp).semantics { heading() })
        Text(stringResource(R.string.settings_ai_key_body), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
        FitCard {
            SwitchHeader(Icons.Rounded.Psychology, c.teal, c.tealTrack, stringResource(R.string.ai_toggle_title), stringResource(R.string.ai_toggle_body), aiOn) { on -> vm.setAiEnabled(on) }
        }
        if (aiOn) {
            SettingsRow(
                Icons.Rounded.Key, c.teal, c.tealTrack,
                stringResource(R.string.profile_ai_key),
                if (apiKey.isBlank()) stringResource(R.string.profile_key_not_set) else stringResource(R.string.profile_key_connected),
            ) { onOpenKeyDialog() }
        }
    }
}

@Composable
internal fun AppSettingsGroup(
    vm: SettingsViewModel,
    s: ProgressSnapshot,
    toggleGroup: (String) -> Unit,
) {
    val c = Fit.colors
    val context = LocalContext.current
    val settings = s.settings
    val canLock = remember {
        androidx.biometric.BiometricManager.from(context).canAuthenticate(
            androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK or androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL,
        ) == androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS
    }

    SettingsGroup(
        Icons.Rounded.Palette, c.teal, c.tealTrack, stringResource(R.string.settings_group_app),
        summary(
            stringResource(when (settings.theme) { "light" -> R.string.theme_light; "dark" -> R.string.theme_dark; else -> R.string.theme_system }),
            when (com.forgequest.app.AppLocale.current(context)) { "en" -> "English"; "ar" -> "العربية"; else -> stringResource(R.string.language_system) },
            settings.weightUnit.label,
            if (settings.biometricLock && canLock) stringResource(R.string.summary_lock_on) else null,
            when {
                settings.justLog -> stringResource(R.string.just_log_title)
                settings.reminderHour < 0 -> stringResource(R.string.summary_reminder_off)
                else -> stringResource(R.string.summary_reminder_at, "%02d:00".format(settings.reminderHour))
            },
        ),
        onToggle = { toggleGroup("app") },
    ) {
        FitCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconCircle(Icons.Rounded.Palette, c.teal, c.tealTrack)
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.profile_appearance), style = MaterialTheme.typography.titleMedium, color = c.textPrimary, modifier = Modifier.semantics { heading() })
            }
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.profile_theme), style = MaterialTheme.typography.labelLarge, color = c.textSecondary)
            Spacer(Modifier.height(8.dp))
            SegmentedTabs(
                listOf(stringResource(R.string.theme_system), stringResource(R.string.theme_light), stringResource(R.string.theme_dark)),
                when (settings.theme) { "light" -> 1; "dark" -> 2; else -> 0 },
                onSelect = { vm.setTheme(listOf("system", "light", "dark")[it]) },
            )
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.profile_language), style = MaterialTheme.typography.labelLarge, color = c.textSecondary)
            Spacer(Modifier.height(8.dp))
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
            Spacer(Modifier.height(8.dp))
            SegmentedTabs(
                listOf(stringResource(R.string.unit_kilograms), stringResource(R.string.unit_pounds)),
                if (settings.weightUnit == WeightUnit.LB) 1 else 0,
                onSelect = { vm.setUnit(if (it == 1) "lb" else "kg") },
            )
        }
        FitCard {
            SwitchHeader(
                Icons.Rounded.Lock, c.teal, c.tealTrack, stringResource(R.string.profile_app_lock),
                stringResource(if (!canLock) R.string.profile_lock_unavailable else if (settings.biometricLock) R.string.profile_lock_on else R.string.profile_lock_off),
                settings.biometricLock && canLock, enabled = canLock,
            ) { vm.setLock(it) }
        }
        val justLog = settings.justLog
        FitCard {
            SwitchHeader(
                Icons.Rounded.Checklist, c.teal, c.tealTrack, stringResource(R.string.just_log_title),
                stringResource(R.string.just_log_subtitle), justLog,
            ) { vm.setJustLog(it) }
        }
        // Workout reminders are part of the consistency side, so "Just log" mode puts them away (the hour is kept).
        val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
        if (!justLog) FitCard {
            SwitchHeader(
                Icons.Rounded.Notifications, c.teal, c.tealTrack, stringResource(R.string.profile_reminder),
                if (settings.reminderHour < 0) stringResource(R.string.profile_reminder_off) else stringResource(R.string.profile_reminder_on, "%02d:00".format(settings.reminderHour)),
                settings.reminderHour >= 0,
            ) {
                if (it && android.os.Build.VERSION.SDK_INT >= 33 && !vm.app.notifier.canPost()) notifPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                vm.setReminder(if (it) 18 else -1)
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
                    NumberEntryDialog(stringResource(R.string.profile_reminder_hour), settings.reminderHour.toFloat(), stringResource(R.string.unit_h), 0, 0f, 23f, onDismiss = { typingHour = false }, onConfirm = { vm.setReminder(it.toInt()); typingHour = false })
                }
            }
        }
        FitCard {
            SwitchHeader(
                Icons.Rounded.Vibration, c.teal, c.tealTrack, stringResource(R.string.profile_vibration),
                stringResource(R.string.profile_vibration_hint), settings.hapticsEnabled,
            ) { vm.setHaptics(it) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BackupSettingsGroup(
    vm: SettingsViewModel,
    s: ProgressSnapshot,
    toggleGroup: (String) -> Unit,
    saveLauncher: androidx.activity.compose.ManagedActivityResultLauncher<String, Uri?>,
    importLauncher: androidx.activity.compose.ManagedActivityResultLauncher<Array<String>, Uri?>,
) {
    val c = Fit.colors
    val settings = s.settings
    val safetyCopies by vm.safetyCopies.collectAsState()

    SettingsGroup(
        Icons.Rounded.Cloud, c.teal, c.tealTrack, stringResource(R.string.settings_group_backup),
        when {
            settings.driveEmail.isBlank() -> stringResource(R.string.summary_drive_off)
            settings.lastBackupAt > 0 -> stringResource(R.string.summary_drive_last, DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(settings.lastBackupAt)))
            else -> stringResource(R.string.summary_drive_none)
        },
        onToggle = { toggleGroup("backup") },
    ) {
        FitCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconCircle(Icons.Rounded.Backup, c.teal, c.tealTrack)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.profile_backup_file), style = MaterialTheme.typography.titleMedium, color = c.textPrimary, modifier = Modifier.semantics { heading() })
                    Text(stringResource(R.string.profile_your_data_hint), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                }
            }
            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton(stringResource(R.string.profile_save_file), onClick = { saveLauncher.launch(vm.app.backup.exportFileName()) }, icon = Icons.Rounded.SaveAlt)
                TonalPill(stringResource(R.string.profile_share_file), onClick = vm::exportData, icon = Icons.Rounded.Share)
                TonalPill(stringResource(R.string.profile_import), onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }, icon = Icons.Rounded.Download)
                if (safetyCopies.isNotEmpty()) TonalPill(stringResource(R.string.profile_undo_restore), onClick = vm::previewUndoRestore, icon = Icons.AutoMirrored.Rounded.Undo)
            }
        }
        FitCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconCircle(Icons.Rounded.Cloud, c.teal, c.tealTrack)
                Spacer(Modifier.width(12.dp))
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
            Spacer(Modifier.height(12.dp))
            if (settings.driveEmail.isBlank()) {
                PillButton(stringResource(R.string.drive_connect), onClick = vm::connectDrive, modifier = Modifier.fillMaxWidth())
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillButton(stringResource(R.string.drive_back_up_now), onClick = vm::backupNow)
                    TonalPill(stringResource(R.string.action_restore), onClick = vm::restore)
                    TonalPill(stringResource(R.string.drive_disconnect), onClick = vm::disconnectDrive, tint = c.textSecondary)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.drive_docs_off_hint),
                    style = MaterialTheme.typography.bodySmall, color = c.textSecondary,
                )
            }
        }
    }
}

@Composable
internal fun AboutSettingsGroup(
    vm: SettingsViewModel,
    toggleGroup: (String) -> Unit,
) {
    val c = Fit.colors
    val context = LocalContext.current

    SettingsGroup(
        Icons.Rounded.Info, c.textSecondary, c.cardHigh, stringResource(R.string.settings_group_about),
        stringResource(R.string.profile_version, com.forgequest.app.BuildConfig.VERSION_NAME),
        onToggle = { toggleGroup("about") },
    ) {
        FitCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconCircle(Icons.Rounded.Info, c.textSecondary, c.cardHigh)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.profile_credits), style = MaterialTheme.typography.titleMedium, color = c.textPrimary, modifier = Modifier.semantics { heading() })
                    Text(stringResource(R.string.profile_credits_body), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                    Text(stringResource(R.string.profile_credits_data), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                }
            }
            Spacer(Modifier.height(12.dp))
            TonalPill(stringResource(R.string.profile_hemo_youtube), onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/@HemoCalisthenics")))
            }, icon = Icons.AutoMirrored.Rounded.OpenInNew)
        }
    }
}
