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
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.LocalFireDepartment
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
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material.icons.rounded.SportsGymnastics
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.TrackChanges
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.forgequest.app.ui.components.PillButton
import com.forgequest.app.ui.components.ProgressLine
import com.forgequest.app.ui.components.SegmentedTabs
import com.forgequest.app.ui.components.Stepper
import com.forgequest.app.ui.components.toast
import com.forgequest.app.ui.localDescription
import com.forgequest.app.ui.localSummary
import com.forgequest.app.ui.localTitle
import com.forgequest.app.ui.mark
import com.forgequest.app.ui.theme.Fit
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
    val aiOn by vm.aiOn.collectAsState()
    val c = Fit.colors
    val isArabic = Locale.getDefault().language == "ar"
    var showXpHow by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }

    // In "Just log" mode badges aren't shown here, so they stay new for when it's turned off.
    DisposableEffect(Unit) { onDispose { if (vm.snapshot.value?.settings?.justLog != true) vm.markBadgesSeen() } }

    val s = snap ?: return Box(Modifier.fillMaxSize().background(c.background))
    val settings = s.settings
    val configuredEq = settings.configuredEquipment
    val justLog = settings.justLog

    Column(Modifier.fillMaxSize().background(c.background).statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = ForgeSpace.Gutter)) {
        Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.tab_profile), style = MaterialTheme.typography.headlineMedium, color = c.textPrimary, modifier = Modifier.weight(1f))
        }

        if (aiOn && apiKey.isBlank()) {
            FitCard(onClick = { nav.navigate(com.forgequest.app.ui.Routes.settingsGroup("coach")) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconCircle(Icons.Rounded.Key, c.teal, c.tealTrack)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.profile_setup_title), style = MaterialTheme.typography.titleMedium, color = c.textPrimary, modifier = Modifier.semantics { heading() })
                        Text(stringResource(R.string.profile_setup_body), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                    }
                    PillButton(stringResource(R.string.profile_add_key), onClick = { nav.navigate(com.forgequest.app.ui.Routes.settingsGroup("coach")) })
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.profile_xp_line, s.level.xpIntoLevel, s.level.xpForNextLevel, s.level.level + 1), style = MaterialTheme.typography.bodySmall, color = c.textSecondary, modifier = Modifier.weight(1f))
                androidx.compose.material3.IconButton(onClick = { showXpHow = !showXpHow }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Outlined.Info, contentDescription = stringResource(R.string.cd_xp_info), tint = c.textSecondary, modifier = Modifier.size(18.dp))
                }
            }
            // How XP is earned and what the letter means, which nothing else on screen says.
            if (showXpHow) Text(
                stringResource(R.string.profile_xp_how, com.forgequest.app.domain.Xp.PER_WORKOUT, com.forgequest.app.domain.Xp.PER_SET, com.forgequest.app.domain.Xp.PER_PR, com.forgequest.app.domain.Xp.PER_BADGE),
                style = MaterialTheme.typography.bodySmall, color = c.textSecondary, modifier = Modifier.padding(top = 4.dp),
            )
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth()) {
                com.forgequest.app.ui.components.StatColumn("${s.sessions.size}", stringResource(R.string.tab_workouts), c.teal, Modifier.weight(1f), onClick = { nav.navigate(com.forgequest.app.ui.Routes.HISTORY) })
                if (!justLog) {
                    com.forgequest.app.ui.components.StatColumn("${s.streak.best}", stringResource(R.string.profile_best_streak), c.gold, Modifier.weight(1f))
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

        // Progress is the fourth tab unless the coach chat took it; then it stays one tap away here. Weight is logged from Home.
        if (com.forgequest.app.data.prefs.chatIsTab(aiOn, apiKey)) FitCard(onClick = { nav.navigate(com.forgequest.app.ui.Routes.PROGRESS) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconCircle(Icons.AutoMirrored.Rounded.ShowChart, c.teal, c.tealTrack)
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.profile_progress_title), style = MaterialTheme.typography.titleMedium, color = c.textPrimary, modifier = Modifier.weight(1f).semantics { heading() })
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = c.textSecondary)
            }
        }
        if (com.forgequest.app.data.prefs.chatIsTab(aiOn, apiKey)) Spacer(Modifier.height(12.dp))

        // Settings live on their own screen: a list, then one page per group.
        FitCard(onClick = { nav.navigate(com.forgequest.app.ui.Routes.SETTINGS) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconCircle(Icons.Rounded.Settings, c.teal, c.tealTrack)
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.settings_section_settings), style = MaterialTheme.typography.titleMedium, color = c.textPrimary, modifier = Modifier.weight(1f).semantics { heading() })
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = c.textSecondary)
            }
        }
        Spacer(Modifier.height(20.dp))
    }


}
