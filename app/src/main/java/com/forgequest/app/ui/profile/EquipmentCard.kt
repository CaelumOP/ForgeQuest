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

/** "Home gym" out of "Home gym (barbell, dumbbells, bar)": the items are listed on their own line. */
internal fun EquipmentPreset.shortLabel(): String =
    (if (Locale.getDefault().language == "ar") labelAr else labelEn).substringBefore(" (")

/**
 * Equipment in two clearly separate steps: (1) one ready-made setup, picked like a radio button,
 * (2) the single items, ticked like checkboxes. Ticking an item that no setup matches shows "Custom mix".
 */
@Composable
internal fun EquipmentCard(active: EquipmentPreset?, configured: Set<Equipment>, onPreset: (EquipmentPreset) -> Unit, onItem: (Equipment) -> Unit) {
    val c = Fit.colors
    FitCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconCircle(Icons.Rounded.FitnessCenter, c.teal, c.tealTrack)
            Spacer(Modifier.width(12.dp))
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
internal fun EquipmentStep(number: Int, title: String, hint: String) {
    val c = Fit.colors
    Row(Modifier.padding(top = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(24.dp).clip(CircleShape).background(c.tealTrack), contentAlignment = Alignment.Center) {
            Text("$number", style = MaterialTheme.typography.labelMedium, color = c.teal, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall, color = c.textPrimary, modifier = Modifier.semantics { heading() })
            Text(hint, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
        }
    }
}
