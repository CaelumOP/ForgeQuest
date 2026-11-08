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

@Composable
internal fun BodySettingsGroup(
    vm: ProfileViewModel,
    s: ProgressSnapshot,
    openGroups: Set<String>,
    toggleGroup: (String) -> Unit,
    probe: com.forgequest.app.data.health.HealthProbe,
    startHealthCheck: () -> Unit,
    healthAccess: androidx.activity.compose.ManagedActivityResultLauncher<Set<String>, Set<String>>,
    onOpenHandDialog: () -> Unit,
) {
    val c = Fit.colors
    val context = LocalContext.current
    val settings = s.settings
    val health by vm.app.health.state.collectAsState()
    val thisYear = java.time.Year.now().value
    val age = Nutrition.ageFromBirthYear(settings.birthYear, thisYear)
    val steps = CalorieUi.stepsBase(health, s.sessions)
    val connected = settings.healthEnabled && (health is com.forgequest.app.data.health.HealthState.Ready || health is com.forgequest.app.data.health.HealthState.Loading)

    SettingsGroup(
        Icons.Rounded.Accessibility, c.teal, c.tealTrack, stringResource(R.string.settings_group_body),
        if (settings.heightCm <= 0f || age <= 0) summary(stringResource(R.string.summary_body_not_set), stringResource(if (connected) R.string.summary_fitbit_on else R.string.summary_fitbit_off))
        else summary(
            stringResource(if (settings.bodyType == BodyType.FEMALE) R.string.body_female else R.string.body_male),
            "${settings.heightCm.toInt()} ${stringResource(R.string.unit_cm)}",
            "$age ${stringResource(R.string.unit_years)}",
            stringResource(settings.dayActivity.labelRes),
            stringResource(if (connected) R.string.summary_fitbit_on else R.string.summary_fitbit_off),
        ),
        open = "body" in openGroups, onToggle = { toggleGroup("body") },
    ) {
        FitCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconCircle(Icons.Rounded.Accessibility, c.teal, c.tealTrack)
                Spacer(Modifier.width(12.dp))
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
            // Not set yet: a dash, not a made-up 170 cm / 25 years that looks like a real value.
            SettingStepper(
                stringResource(R.string.profile_height), if (settings.heightCm > 0f) settings.heightCm else 170f, { vm.setBodyMeasures(heightCm = it) },
                step = 1f, min = 120f, max = 230f, unit = stringResource(R.string.unit_cm),
                placeholder = if (settings.heightCm > 0f) null else "– ${stringResource(R.string.unit_cm)}",
            )
            SettingStepper(
                stringResource(R.string.profile_age), if (age > 0) age.toFloat() else 25f, { vm.setBodyMeasures(birthYear = thisYear - it.toInt()) },
                step = 1f, min = 10f, max = 100f, unit = stringResource(R.string.unit_years),
                placeholder = if (age > 0) null else "– ${stringResource(R.string.unit_years)}",
            )
            SettingStepper(
                label = stringResource(R.string.profile_body_fat_optional),
                value = settings.bodyFatPct,
                onChange = { vm.setBodyMeasures(bodyFatPct = if (it < 4f) 0f else it) },
                step = 0.5f,
                min = 0f,
                max = 55f,
                unit = "%",
                decimals = 1,
            )
            Text(stringResource(R.string.profile_bodyfat_hint), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.day_short_title), style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
            Spacer(Modifier.height(8.dp))
            SegmentedTabs(
                DayActivity.entries.map { stringResource(it.labelRes) },
                DayActivity.entries.indexOf(settings.dayActivity),
                onSelect = { i -> vm.setNutrition { it.copy(dayActivity = DayActivity.entries[i]) } },
                wrap = true,
            )
            Spacer(Modifier.height(8.dp))
            // With a watch, the steps decide; the choice above is the fallback for days it isn't worn.
            Text(
                steps?.let { stringResource(R.string.steps_using, "%,d".format(it.avgSteps)) } ?: stringResource(settings.dayActivity.descRes),
                style = MaterialTheme.typography.bodySmall, color = c.textSecondary,
            )
            if (settings.heightCm <= 0f || age <= 0) {
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.profile_body_not_set), style = MaterialTheme.typography.bodySmall, color = c.gold)
            }
        }
        FitCard {
            SwitchHeader(
                Icons.Rounded.MonitorHeart, c.teal, c.tealTrack, stringResource(R.string.profile_health_title),
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
            // Switched on here but access was taken back in Health Connect: say so rather than look switched off.
            if (settings.healthEnabled && health == com.forgequest.app.data.health.HealthState.NeedsAccess) {
                Text(stringResource(R.string.ready_reconnect_body), style = MaterialTheme.typography.bodySmall, color = c.gold)
                TextButton(onClick = { healthAccess.launch(vm.app.health.requestPermissions()) }) { Text(stringResource(R.string.ready_reconnect), color = c.gold) }
            }
        }
        SettingsRow(
            Icons.Rounded.PanTool, c.teal, c.tealTrack,
            stringResource(R.string.profile_hand_size),
            if (settings.handLengthCm > 0f) stringResource(R.string.profile_hand_value, "%.1f".format(settings.handLengthCm), "%.1f".format(settings.palmWidthCm)) else stringResource(R.string.profile_hand_hint),
        ) { onOpenHandDialog() }
    }
}

@Composable
internal fun GoalsSettingsGroup(
    vm: ProfileViewModel,
    s: ProgressSnapshot,
    openGroups: Set<String>,
    toggleGroup: (String) -> Unit,
) {
    val c = Fit.colors
    val settings = s.settings
    val justLog = settings.justLog
    val health by vm.app.health.state.collectAsState()
    val planDays = s.plan?.trainingDays?.size ?: 0
    val thisYear = java.time.Year.now().value
    val age = Nutrition.ageFromBirthYear(settings.birthYear, thisYear)
    val steps = CalorieUi.stepsBase(health, s.sessions)
    val calInputs = CalorieUi.inputs(settings, s.latestWeight, planDays, steps, thisYear)

    SettingsGroup(
        Icons.Rounded.TrackChanges, c.teal, c.tealTrack, stringResource(R.string.settings_group_goals),
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
                IconCircle(Icons.Rounded.TrackChanges, c.teal, c.tealTrack)
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.profile_daily_targets), style = MaterialTheme.typography.titleMedium, color = c.textPrimary, modifier = Modifier.semantics { heading() })
            }
            val trainingDaysForKcal = CalorieUi.trainingDays(settings, planDays)
            val hasBodyStats = CalorieUi.hasBodyStats(settings, age)
            fun kcalFor(goal: NutritionGoal) = calInputs?.let { CalorieUi.suggestion(it, settings, goal) }
            val floor = calInputs?.let(CalorieUi::floor) ?: Nutrition.MIN_SAFE_KCAL
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.goal_title), style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
            Row(Modifier.padding(top = 8.dp, bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NutritionGoal.entries.forEach { g ->
                    ChoiceChip(stringResource(g.labelRes), settings.nutritionGoal == g, {
                        if (g != settings.nutritionGoal) vm.setNutrition { it.copy(nutritionGoal = g) }
                    })
                }
            }
            val kg = s.latestWeight
            when (settings.nutritionGoal) {
                NutritionGoal.CUT -> {
                    val paces = CutPace.entries
                    val fastBlocked = kg != null &&
                        Nutrition.allowedCutPace(CutPace.AGGRESSIVE, kg, settings.heightCm, age, settings.bodyType == BodyType.MALE, settings.bodyFatPct) != CutPace.AGGRESSIVE
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.pace_title), style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
                    Spacer(Modifier.height(8.dp))
                    SegmentedTabs(
                        options = paces.map { stringResource(it.labelRes) },
                        selected = paces.indexOf(settings.cutPace),
                        onSelect = { i -> vm.setNutrition { it.copy(cutPace = paces[i]) } },
                        subs = if (kg == null) emptyList() else paces.map { stringResource(R.string.pace_week, settings.weightUnit.format(kg * it.weeklyPct / 100f)) },
                        disabled = if (fastBlocked) setOf(paces.indexOf(CutPace.AGGRESSIVE)) else emptySet(),
                        wrap = true,
                    )
                    if (fastBlocked) Text(stringResource(R.string.pace_blocked), style = MaterialTheme.typography.bodySmall, color = c.textSecondary, modifier = Modifier.padding(top = 8.dp))
                    Spacer(Modifier.height(8.dp))
                }
                NutritionGoal.GAIN -> {
                    val paces = GainPace.entries
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.pace_title), style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
                    Spacer(Modifier.height(8.dp))
                    SegmentedTabs(
                        options = paces.map { stringResource(it.labelRes) },
                        selected = paces.indexOf(settings.gainPace),
                        onSelect = { i -> vm.setNutrition { it.copy(gainPace = paces[i]) } },
                        subs = if (kg == null) emptyList() else paces.map { stringResource(R.string.pace_month, settings.weightUnit.format(kg * it.weeklyPct * 4.35f / 100f)) },
                        wrap = true,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                NutritionGoal.MAINTAIN -> Unit
            }
            val trend = Nutrition.weightTrendNote(
                settings.nutritionGoal, settings.goalSetDay, s.weights.map { it.epochDay to it.kg }, Dates.today(),
                settings.cutPace, settings.gainPace, settings.bodyType == BodyType.FEMALE,
            )
            if (trend != null) {
                Text(
                    stringResource(
                        when (trend) {
                            WeightTrend.CUT_TOO_FAST -> R.string.goal_trend_cut_fast
                            WeightTrend.GAIN_STALLED -> R.string.goal_trend_gain_stalled
                            WeightTrend.GAIN_TOO_FAST -> R.string.goal_trend_gain_fast
                            WeightTrend.DIET_BREAK_DUE -> R.string.goal_trend_diet_break
                        },
                    ),
                    style = MaterialTheme.typography.bodySmall, color = c.gold, modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            SettingStepper(
                stringResource(R.string.profile_daily_calories),
                settings.kcalGoal.toFloat(),
                { vm.setKcal(it.toInt()) },
                step = 50f,
                min = floor.toFloat(),
                max = Nutrition.MAX_SAFE_KCAL.toFloat(),
                unit = stringResource(R.string.unit_kcal),
            )
            val suggested = kcalFor(settings.nutritionGoal)
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when {
                        !hasBodyStats -> stringResource(R.string.goals_suggest_missing)
                        suggested != null -> stringResource(R.string.profile_suggested_v2, "%,d".format(suggested), trainingDaysForKcal, stringResource(settings.dayActivity.labelRes))
                        else -> stringResource(R.string.goals_suggest_missing)
                    },
                    style = MaterialTheme.typography.bodySmall, color = c.textSecondary, modifier = Modifier.weight(1f),
                )
                if (suggested != null && hasBodyStats && suggested != settings.kcalGoal) {
                    Spacer(Modifier.width(8.dp))
                    TonalPill(stringResource(R.string.goals_use), onClick = { vm.setKcal(suggested) })
                }
            }
            if (settings.kcalGoal <= floor) {
                Text(stringResource(R.string.profile_floor_hint, "%,d".format(floor)), style = MaterialTheme.typography.bodySmall, color = c.textSecondary, modifier = Modifier.padding(bottom = 8.dp))
            }
            // What the Fitbit measured against what the formula guesses, once there are two weeks of it.
            val fitbitKcal = (health as? com.forgequest.app.data.health.HealthState.Ready)?.day?.kcalAvg14
            val maintain = kcalFor(NutritionGoal.MAINTAIN)
            if (fitbitKcal != null && maintain != null && hasBodyStats) {
                Text(
                    stringResource(R.string.goals_fitbit_burn, "%,d".format(maintain), "%,d".format(fitbitKcal)),
                    style = MaterialTheme.typography.bodySmall, color = c.textSecondary, modifier = Modifier.padding(bottom = 8.dp),
                )
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
                Nutrition.suggestedProtein(kg, settings.proteinPerKg.coerceAtLeast(Nutrition.MIN_PROTEIN_PER_KG), settings.bodyFatPct, settings.heightCm)
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
            Row(Modifier.fillMaxWidth().toggleable(settings.trackCarbs, role = Role.Switch) { vm.setCarbs(track = it) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
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
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
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
            if (!justLog) Text(stringResource(R.string.profile_streak_explain), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
        }
    }
}

@Composable
internal fun TrainingSettingsGroup(
    vm: ProfileViewModel,
    s: ProgressSnapshot,
    openGroups: Set<String>,
    toggleGroup: (String) -> Unit,
) {
    val c = Fit.colors
    val settings = s.settings
    val configuredEq = settings.configuredEquipment
    val justLog = settings.justLog
    val activePreset = remember(settings.equipment) { EquipmentPreset.fromEncoded(settings.equipment) }

    SettingsGroup(
        Icons.Rounded.FitnessCenter, c.teal, c.tealTrack, stringResource(R.string.settings_group_training),
        summary(
            when {
                activePreset != null -> activePreset.shortLabel()
                configuredEq.isEmpty() -> stringResource(R.string.summary_equipment_unset)
                else -> stringResource(R.string.equipment_custom)
            },
            when {
                justLog -> stringResource(R.string.just_log_title)
                settings.reminderHour < 0 -> stringResource(R.string.summary_reminder_off)
                else -> stringResource(R.string.summary_reminder_at, "%02d:00".format(settings.reminderHour))
            },
        ),
        open = "training" in openGroups, onToggle = { toggleGroup("training") },
    ) {
        EquipmentCard(activePreset, configuredEq, onPreset = { vm.setEquipment(it.encode()) }, onItem = { vm.toggleEquipmentItem(it, configuredEq) })
        FitCard {
            SwitchHeader(
                Icons.Rounded.Checklist, c.teal, c.tealTrack, stringResource(R.string.just_log_title),
                stringResource(R.string.just_log_subtitle), justLog,
            ) { vm.setJustLog(it) }
        }
        // Workout reminders are part of the consistency side, so "Just log" mode puts them away (the hour is kept).
        if (!justLog) FitCard {
            SwitchHeader(
                Icons.Rounded.Notifications, c.teal, c.tealTrack, stringResource(R.string.profile_reminder),
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
                    NumberEntryDialog(stringResource(R.string.profile_reminder_hour), settings.reminderHour.toFloat(), stringResource(R.string.unit_h), 0, 0f, 23f, onDismiss = { typingHour = false }, onConfirm = { vm.setReminder(it.toInt()); typingHour = false })
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
        }
    }
}

@Composable
internal fun CoachSettingsGroup(
    vm: ProfileViewModel,
    s: ProgressSnapshot,
    apiKey: String,
    groqKey: String,
    openGroups: Set<String>,
    toggleGroup: (String) -> Unit,
    onOpenKeyDialog: () -> Unit,
    onOpenGroqDialog: () -> Unit,
    onOpenNotesDialog: () -> Unit,
) {
    val c = Fit.colors
    val context = LocalContext.current
    val settings = s.settings

    SettingsGroup(
        Icons.Rounded.Psychology, c.teal, c.tealTrack, stringResource(R.string.settings_group_coach),
        summary(
            stringResource(if (apiKey.isBlank()) R.string.summary_gemini_off else R.string.summary_gemini_on),
            stringResource(if (groqKey.isBlank()) R.string.summary_groq_off else R.string.summary_groq_on),
        ),
        open = "coach" in openGroups, onToggle = { toggleGroup("coach") },
    ) {
        SettingsRow(
            Icons.Rounded.Key, c.teal, c.tealTrack,
            stringResource(R.string.profile_ai_key),
            if (apiKey.isBlank()) stringResource(R.string.profile_key_not_set) else stringResource(R.string.profile_key_connected),
        ) { onOpenKeyDialog() }
        SettingsRow(
            Icons.Rounded.Bolt, c.teal, c.tealTrack,
            stringResource(R.string.groq_key_title),
            if (groqKey.isBlank()) stringResource(R.string.groq_key_off) else stringResource(R.string.groq_key_on),
        ) { onOpenGroqDialog() }
        FitCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconCircle(Icons.Rounded.Psychology, c.teal, c.tealTrack)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.profile_coach_memory), style = MaterialTheme.typography.titleMedium, color = c.textPrimary, modifier = Modifier.semantics { heading() })
                    Text(
                        if (settings.coachNotes.isBlank()) stringResource(R.string.profile_memory_empty)
                        else stringResource(R.string.profile_memory_hint),
                        style = MaterialTheme.typography.bodySmall, color = c.textSecondary,
                    )
                }
                TonalPill(stringResource(R.string.action_add), onClick = { onOpenNotesDialog() })
            }
            val notes = settings.coachNotes.lines().filter { it.isNotBlank() }
            if (notes.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                // The list grows and shrinks smoothly as notes are added or removed.
                Column(Modifier.animateContentSize()) {
                    notes.forEach { note ->
                        Row(
                            Modifier.fillMaxWidth().clip(ForgeShape.Sm).clickable { vm.removeNote(note) }.padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("•", style = MaterialTheme.typography.bodyMedium, color = c.purple, modifier = Modifier.width(16.dp))
                            Text(note, style = MaterialTheme.typography.bodyMedium, color = c.textPrimary, modifier = Modifier.weight(1f))
                            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.cd_remove), tint = c.textSecondary, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
        var pasteOpen by remember { mutableStateOf(false) }
        val bridgeScope = rememberCoroutineScope()
        val clipboard = com.forgequest.app.ui.components.rememberTextClipboard()
        FitCard {
            // Turning it on explains what it is first: a stray tap on the title used to start a Google sign-in out of nowhere.
            var explainBridge by remember { mutableStateOf(false) }
            SwitchHeader(Icons.Rounded.AutoAwesome, c.teal, c.tealTrack, stringResource(R.string.bridge_title), stringResource(R.string.bridge_hint), settings.bridgeEnabled) { on ->
                if (on) explainBridge = true else vm.setBridge(false)
            }
            if (explainBridge) {
                AlertDialog(
                    onDismissRequest = { explainBridge = false },
                    containerColor = c.card,
                    title = { Text(stringResource(R.string.bridge_title), color = c.textPrimary) },
                    text = { Text(stringResource(R.string.bridge_explain), color = c.textSecondary) },
                    confirmButton = { TextButton(onClick = { explainBridge = false; vm.setBridge(true) }) { Text(stringResource(R.string.bridge_turn_on), color = c.teal) } },
                    dismissButton = { TextButton(onClick = { explainBridge = false }) { Text(stringResource(R.string.action_cancel), color = c.textSecondary) } },
                )
            }
            if (settings.bridgeEnabled) {
                Spacer(Modifier.height(12.dp))
                val fmt = java.time.format.DateTimeFormatter.ofPattern("EEE HH:mm", Locale.getDefault())
                fun at(ms: Long) = java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneId.systemDefault()).format(fmt)
                Text(
                    (if (settings.bridgeLastWrite > 0) stringResource(R.string.bridge_wrote, at(settings.bridgeLastWrite)) else stringResource(R.string.bridge_never_wrote)) + " · " +
                        (if (settings.bridgeLastReadAt > 0) stringResource(R.string.bridge_read, at(settings.bridgeLastReadAt)) else stringResource(R.string.bridge_never_read)),
                    style = MaterialTheme.typography.bodySmall, color = c.textSecondary,
                )
                if (settings.bridgeIssues.isNotBlank()) Text(stringResource(R.string.bridge_issues, settings.bridgeIssues), style = MaterialTheme.typography.bodySmall, color = c.coral)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TonalPill(stringResource(R.string.bridge_sync), onClick = { vm.syncBridge() })
                    TonalPill(stringResource(R.string.bridge_copy_prompt), onClick = {
                        val prompt = context.resources.openRawResource(R.raw.gemini_bridge_prompt).bufferedReader().use { it.readText() }
                        clipboard.copy(prompt)
                        context.toast(context.getString(R.string.bridge_prompt_copied))
                    })
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TonalPill(stringResource(R.string.bridge_copy_data), onClick = {
                        bridgeScope.launch {
                            clipboard.copy(vm.bridgeData())
                            context.toast(context.getString(R.string.bridge_data_copied))
                        }
                    }, tint = c.textSecondary)
                    TonalPill(stringResource(R.string.bridge_paste), onClick = { pasteOpen = true }, tint = c.textSecondary)
                }
            }
        }
        FitCard {
            SwitchHeader(Icons.Rounded.Restaurant, c.teal, c.tealTrack, stringResource(R.string.meals_title), stringResource(R.string.meals_hint), settings.geminiMealsEnabled) { vm.setGeminiMeals(it) }
            if (settings.geminiMealsEnabled) {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TonalPill(stringResource(R.string.bridge_sync), onClick = vm::syncMeals)
                    TonalPill(stringResource(R.string.meals_copy_gem), onClick = {
                        // The person's own hand size goes in as the ruler, or the line goes when it isn't set.
                        val hand = if (settings.handLengthCm > 0f) "MY HAND: length ${settings.handLengthCm} cm, palm width ${settings.palmWidthCm} cm." else ""
                        val prompt = context.resources.openRawResource(R.raw.food_gem_prompt).bufferedReader().use { it.readText() }
                            .replace(Regex("MY HAND:[^\n]*\n"), if (hand.isBlank()) "" else hand + " When my hand is in the photo, use it as an exact ruler.\n")
                        clipboard.copy(prompt)
                        context.toast(context.getString(R.string.bridge_prompt_copied))
                    })
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
}

@Composable
internal fun AppSettingsGroup(
    vm: ProfileViewModel,
    s: ProgressSnapshot,
    nav: NavHostController,
    openGroups: Set<String>,
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
        ),
        open = "app" in openGroups, onToggle = { toggleGroup("app") },
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
        SettingsRow(Icons.Rounded.AutoAwesome, c.teal, c.tealTrack, stringResource(R.string.tour_replay), stringResource(R.string.tour_replay_hint)) {
            nav.navigate(com.forgequest.app.ui.Routes.TOUR)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BackupSettingsGroup(
    vm: ProfileViewModel,
    s: ProgressSnapshot,
    openGroups: Set<String>,
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
        open = "backup" in openGroups, onToggle = { toggleGroup("backup") },
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
}

@Composable
internal fun AboutSettingsGroup(
    vm: ProfileViewModel,
    openGroups: Set<String>,
    toggleGroup: (String) -> Unit,
) {
    val c = Fit.colors
    val context = LocalContext.current

    SettingsGroup(
        Icons.Rounded.Info, c.textSecondary, c.cardHigh, stringResource(R.string.settings_group_about),
        stringResource(R.string.profile_version, com.forgequest.app.BuildConfig.VERSION_NAME),
        open = "about" in openGroups, onToggle = { toggleGroup("about") },
    ) {
        SettingsRow(Icons.Rounded.SystemUpdate, c.teal, c.tealTrack, stringResource(R.string.update_title), stringResource(R.string.update_hint, com.forgequest.app.BuildConfig.VERSION_NAME)) { vm.checkUpdate() }
        FitCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconCircle(Icons.Rounded.Info, c.textSecondary, c.cardHigh)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.profile_credits), style = MaterialTheme.typography.titleMedium, color = c.textPrimary, modifier = Modifier.semantics { heading() })
                    Text(stringResource(R.string.profile_credits_body), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                    Text(stringResource(R.string.profile_credits_data), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                    val taps = remember { longArrayOf(0, 0, 0, 0, 0, 0, 0) }
                    Text(
                        stringResource(R.string.profile_version, com.forgequest.app.BuildConfig.VERSION_NAME),
                        style = MaterialTheme.typography.bodySmall, color = c.textSecondary,
                        // Hidden owner switch: 7 taps within 3 seconds.
                        modifier = Modifier.clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null, role = null) {
                            val now = android.os.SystemClock.elapsedRealtime()
                            for (i in 0 until taps.size - 1) taps[i] = taps[i + 1]
                            taps[taps.size - 1] = now
                            if (taps[0] != 0L && now - taps[0] <= 3000) {
                                taps.fill(0)
                                val on = com.forgequest.app.PersonalTools.toggle(context)
                                context.toast(context.getString(if (on) R.string.personal_tools_on else R.string.personal_tools_off))
                            }
                        },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            TonalPill(stringResource(R.string.profile_hemo_youtube), onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/@HemoCalisthenics")))
            }, icon = Icons.AutoMirrored.Rounded.OpenInNew)
        }
    }
}
