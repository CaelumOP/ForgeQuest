package com.forgequest.app.ui.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.forgequest.app.R
import com.forgequest.app.data.MeasurementMath
import com.forgequest.app.data.MeasurementMetric
import com.forgequest.app.data.ProgressSnapshot
import com.forgequest.app.data.db.BodyMeasurementEntity
import com.forgequest.app.domain.Dates
import com.forgequest.app.domain.ExerciseKind
import com.forgequest.app.domain.TrendPoint
import com.forgequest.app.domain.TrendRange
import com.forgequest.app.domain.Trends
import com.forgequest.app.domain.WeightUnit
import com.forgequest.app.domain.countsForScore
import com.forgequest.app.ui.components.ChoiceChip
import com.forgequest.app.ui.components.FitCard
import com.forgequest.app.ui.components.NumberStepper
import com.forgequest.app.ui.components.TonalPill
import com.forgequest.app.ui.components.TrendChart
import com.forgequest.app.ui.theme.Fit
import com.forgequest.app.ui.theme.ForgeShape
import com.forgequest.app.ui.theme.NumberFont
import com.forgequest.app.ui.theme.tabular
import java.util.Locale
import kotlin.math.abs

@Composable
internal fun MeasurementMetric.labelRes(): Int = when (this) {
    MeasurementMetric.WAIST -> R.string.prog41_meas_metric_waist
    MeasurementMetric.CHEST -> R.string.prog41_meas_metric_chest
    MeasurementMetric.ARMS -> R.string.prog41_meas_metric_arms
    MeasurementMetric.THIGHS -> R.string.prog41_meas_metric_thighs
    MeasurementMetric.HIPS -> R.string.prog41_meas_metric_hips
}

@Composable
internal fun WeeklyVolumeCard(s: ProgressSnapshot) {
    val c = Fit.colors
    val volume = remember(s.sessions, s.doneSets, s.exercises) {
        ProgressMetrics.weeklyMuscleVolume(s.sessions, s.doneSets, s.exercises, Dates.today())
    }
    FitCard {
        Text(stringResource(R.string.prog41_vol_title), style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
        Text(stringResource(R.string.prog41_vol_subtitle), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
        Spacer(Modifier.height(12.dp))
        WeeklyVolumeBodyMap(volume = volume, bodyType = s.settings.bodyType)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.prog41_vol_explain), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun OneRmCalculatorCard(s: ProgressSnapshot, unit: WeightUnit) {
    val c = Fit.colors
    val bestSample = remember(s.doneSets, s.exercises) {
        s.doneSets
            .asSequence()
            .filter { s.exercises[it.exerciseId]?.kind == ExerciseKind.WEIGHTED && countsForScore(it) && (it.weightKg ?: 0f) > 0f && (it.reps ?: 0) > 0 }
            .maxByOrNull { ProgressMetrics.epley1Rm(it.weightKg ?: 0f, it.reps ?: 0) }
    }
    var weightKg by rememberSaveable { mutableFloatStateOf(bestSample?.weightKg ?: 60f) }
    var reps by rememberSaveable { mutableIntStateOf((bestSample?.reps ?: 5).coerceIn(1, 20)) }
    val estKg = ProgressMetrics.epley1Rm(weightKg, reps)
    val ratio = ProgressMetrics.strengthToBodyweight(estKg, s.latestWeight)

    FitCard {
        Text(stringResource(R.string.prog41_1rm_title), style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
        Text(stringResource(R.string.prog41_1rm_hint), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
        if (bestSample != null) {
            val exName = s.exercises[bestSample.exerciseId]?.name ?: bestSample.exerciseId
            Spacer(Modifier.height(8.dp))
            TonalPill(
                stringResource(R.string.prog41_1rm_use_best, "$exName (${bestSample.reps}×${unit.number(bestSample.weightKg ?: 0f)})"),
                onClick = {
                    weightKg = bestSample.weightKg ?: 60f
                    reps = (bestSample.reps ?: 5).coerceIn(1, 20)
                },
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.prog41_1rm_weight, unit.label), style = MaterialTheme.typography.bodyLarge, color = c.textPrimary, modifier = Modifier.weight(1f))
            NumberStepper(
                value = unit.fromKg(weightKg),
                onValueChange = { weightKg = unit.toKg(it).coerceIn(1f, 500f) },
                step = unit.loadStep,
                min = unit.fromKg(1f),
                max = unit.fromKg(500f),
                unit = unit.label,
                decimals = 1,
                title = stringResource(R.string.prog41_1rm_weight, unit.label),
                minValueWidth = 90.dp,
            )
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.prog41_1rm_reps), style = MaterialTheme.typography.bodyLarge, color = c.textPrimary, modifier = Modifier.weight(1f))
            NumberStepper(
                value = reps.toFloat(),
                onValueChange = { reps = it.toInt().coerceIn(1, 20) },
                step = 1f,
                min = 1f,
                max = 20f,
                decimals = 0,
                title = stringResource(R.string.prog41_1rm_reps),
                minValueWidth = 90.dp,
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth().clip(ForgeShape.Md).background(c.cardHigh).padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.prog41_1rm_result_label), style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
                Text(
                    unit.format(estKg, 1),
                    fontFamily = NumberFont,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.headlineSmall.tabular(),
                    color = c.blue,
                )
            }
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.prog41_1rm_ratio_label), style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
                Text(
                    if (ratio != null) stringResource(R.string.prog41_1rm_ratio_val, String.format(Locale.US, "%.2f", ratio))
                    else stringResource(R.string.prog41_1rm_ratio_missing),
                    fontFamily = NumberFont,
                    fontWeight = FontWeight.SemiBold,
                    style = if (ratio != null) MaterialTheme.typography.titleMedium.tabular() else MaterialTheme.typography.bodySmall,
                    color = if (ratio != null) c.purple else c.textSecondary,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ProgressMetrics.epleyPercentages(estKg).forEach { (pct, kg) ->
                Box(
                    Modifier.clip(ForgeShape.Sm).background(c.blueTrack).padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Text(
                        "$pct% · ${unit.format(kg, 0)}",
                        style = MaterialTheme.typography.labelMedium.tabular(),
                        color = c.blue,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MeasurementsCard(
    rows: List<BodyMeasurementEntity>,
    range: TrendRange,
    reveal: Float,
    dateLabel: (Long) -> String,
    onLog: (MeasurementMetric, Float) -> Unit,
    onDelete: (Long) -> Unit,
    openAdd: Boolean = false,
    onOpenAddHandled: () -> Unit = {},
) {
    val c = Fit.colors
    var selectedIdx by rememberSaveable { mutableIntStateOf(0) }
    val metric = MeasurementMetric.entries[selectedIdx.coerceIn(0, MeasurementMetric.entries.lastIndex)]
    val allPoints = remember(rows, metric) { MeasurementMath.trend(rows, metric) }
    val from = range.from(Dates.today())
    val shown = remember(allPoints, from) {
        val filtered = allPoints.filter { it.epochDay >= from }
        if (filtered.size >= 2) filtered else allPoints.takeLast(10)
    }
    val latest = allPoints.lastOrNull()
    val prev = allPoints.getOrNull(allPoints.lastIndex - 1)
    var showDialog by remember { mutableStateOf(false) }
    LaunchedEffect(openAdd) {
        if (openAdd) {
            showDialog = true
            onOpenAddHandled()
        }
    }

    FitCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.prog41_meas_title), style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
                Text(stringResource(R.string.prog41_meas_hint), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
            }
            Spacer(Modifier.width(8.dp))
            com.forgequest.app.ui.components.CoachMarkBox(
                com.forgequest.app.ui.onboarding.AppTour.PROGRESS,
                R.string.v42_coach_progress_t,
                R.string.v42_coach_progress_b,
                below = true,
            ) {
                TonalPill(stringResource(R.string.prog41_meas_log), icon = Icons.Rounded.Add, onClick = { showDialog = true })
            }
        }
        Spacer(Modifier.height(12.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MeasurementMetric.entries.forEachIndexed { idx, m ->
                ChoiceChip(stringResource(m.labelRes()), selected = idx == selectedIdx, onClick = { selectedIdx = idx })
            }
        }
        Spacer(Modifier.height(12.dp))
        if (latest == null) {
            Text(stringResource(R.string.prog41_meas_empty), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    stringResource(R.string.prog41_meas_latest, String.format(Locale.US, "%.1f", latest.value)),
                    fontFamily = NumberFont,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge.tabular(),
                    color = c.purple,
                )
                if (prev != null && abs(latest.value - prev.value) >= 0.05f) {
                    val diff = latest.value - prev.value
                    val sign = if (diff > 0f) "+" else "−"
                    Box(Modifier.clip(ForgeShape.Full).background(c.purpleTrack).padding(horizontal = 8.dp, vertical = 2.dp)) {
                        Text(
                            "$sign${String.format(Locale.US, "%.1f", abs(diff))} cm",
                            style = MaterialTheme.typography.labelSmall.tabular(),
                            color = c.purple,
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            if (shown.size >= 2) {
                TrendChart(
                    points = shown,
                    color = c.purple,
                    reveal = reveal,
                    showRecords = false,
                    format = { String.format(Locale.US, "%.1f", it) },
                    dateLabel = dateLabel,
                )
            } else {
                Text(stringResource(R.string.prog41_meas_need_two), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
            }
            val recent = remember(rows, metric) {
                rows.filter { it.metric == metric.name }
                    .sortedWith(compareByDescending<BodyMeasurementEntity> { it.epochDay }.thenByDescending { it.loggedAt })
                    .take(5)
            }
            Spacer(Modifier.height(8.dp))
            recent.forEach { row ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(dateLabel(row.epochDay), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary, modifier = Modifier.weight(1f))
                    Text("${String.format(Locale.US, "%.1f", row.valueCm)} cm", fontFamily = NumberFont, style = MaterialTheme.typography.bodyMedium.tabular(), color = c.textPrimary)
                    IconButton(onClick = { onDelete(row.id) }) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = stringResource(R.string.prog41_meas_delete_cd), tint = c.textSecondary)
                    }
                }
            }
        }
    }

    if (showDialog) {
        LogMeasurementDialog(
            initialMetric = metric,
            initialCm = latest?.value ?: defaultCmFor(metric),
            onDismiss = { showDialog = false },
            onConfirm = { m, cm ->
                onLog(m, cm)
                selectedIdx = MeasurementMetric.entries.indexOf(m)
                showDialog = false
            },
        )
    }
}

private fun defaultCmFor(m: MeasurementMetric): Float = when (m) {
    MeasurementMetric.WAIST -> 82f
    MeasurementMetric.CHEST -> 100f
    MeasurementMetric.ARMS -> 35f
    MeasurementMetric.THIGHS -> 56f
    MeasurementMetric.HIPS -> 96f
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LogMeasurementDialog(
    initialMetric: MeasurementMetric,
    initialCm: Float,
    onDismiss: () -> Unit,
    onConfirm: (MeasurementMetric, Float) -> Unit,
) {
    val c = Fit.colors
    var picked by remember { mutableStateOf(initialMetric) }
    var text by remember { mutableStateOf(String.format(Locale.US, "%.1f", initialCm)) }
    val parsed = text.replace(',', '.').toFloatOrNull()
    val valid = parsed != null && parsed in MeasurementMath.MIN_CM..MeasurementMath.MAX_CM

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.card,
        title = { Text(stringResource(R.string.prog41_meas_log_title), color = c.textPrimary) },
        text = {
            Column {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MeasurementMetric.entries.forEach { m ->
                        ChoiceChip(stringResource(m.labelRes()), selected = picked == m, onClick = { picked = m })
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(R.string.prog41_meas_value_cm)) },
                    singleLine = true,
                    isError = text.isNotEmpty() && !valid,
                    supportingText = if (text.isNotEmpty() && !valid) {
                        { Text(stringResource(R.string.prog41_meas_invalid), color = c.coral) }
                    } else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = ForgeShape.Md,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = c.teal,
                        unfocusedBorderColor = c.divider,
                        focusedTextColor = c.textPrimary,
                        unfocusedTextColor = c.textPrimary,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { parsed?.let { onConfirm(picked, it) } }, enabled = valid) {
                Text(stringResource(R.string.action_save), color = c.teal)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel), color = c.textSecondary)
            }
        },
    )
}
