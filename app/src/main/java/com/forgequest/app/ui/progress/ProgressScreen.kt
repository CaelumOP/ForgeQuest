package com.forgequest.app.ui.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ShowChart
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.forgequest.app.AppContainer
import com.forgequest.app.R
import com.forgequest.app.data.ProgressSnapshot
import com.forgequest.app.domain.Dates
import com.forgequest.app.domain.ExerciseKind
import com.forgequest.app.domain.TrendPoint
import com.forgequest.app.domain.TrendRange
import com.forgequest.app.domain.Trends
import com.forgequest.app.domain.WeightUnit
import com.forgequest.app.ui.appViewModel
import com.forgequest.app.ui.components.BarRow
import com.forgequest.app.ui.components.ChoiceChip
import com.forgequest.app.ui.components.DeltaChip
import com.forgequest.app.ui.components.EmptyState
import com.forgequest.app.ui.components.FitCard
import com.forgequest.app.ui.components.Heatmap
import com.forgequest.app.ui.components.MiniRing
import com.forgequest.app.ui.components.SegmentedTabs
import com.forgequest.app.ui.components.Sparkline
import com.forgequest.app.ui.components.TrendChart
import com.forgequest.app.ui.components.rememberReveal
import com.forgequest.app.ui.theme.Fit
import com.forgequest.app.ui.theme.Manrope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

class ProgressViewModel(val app: AppContainer) : ViewModel() {
    val snapshot: StateFlow<ProgressSnapshot?> = app.progress.snapshot.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
}

/** Everything derived for one range, computed once per snapshot change. */
private class Derived(s: ProgressSnapshot, val range: TrendRange, val unit: WeightUnit) {
    val today = Dates.today()
    val from = range.from(today)
    val sessionDay = s.sessions.associate { it.id to it.epochDay }
    val workoutDays = s.sessions.map { it.epochDay }.toSet()
    val firstDay = s.sessions.minOfOrNull { it.epochDay } ?: today
    val weeksInRange = (range.days?.let { (it + 6) / 7 } ?: ((today - firstDay).toInt() / 7 + 1)).coerceIn(4, 52)
    val weeklySets = Trends.weeklySets(s.doneSets, sessionDay, 12, today)
    val weeklyWorkouts = Trends.weeklyCounts(workoutDays, weeksInRange, today)
    val load = Trends.trainingLoad(weeklySets)
    val topIds = Trends.topExercises(s.doneSets, 6)
    val series: Map<String, List<TrendPoint>> = topIds.associateWith { id ->
        val kind = s.exercises[id]?.kind ?: ExerciseKind.REPS
        Trends.bestSetSeries(s.doneSets.filter { it.exerciseId == id }, kind, sessionDay)
    }
    val recordDays = s.doneSets.filter { it.isPr }.mapNotNull { sessionDay[it.sessionId] }.toSet()
    val grid = Trends.consistencyGrid(workoutDays, recordDays, weeksInRange, today)
    val weightPoints = s.weights.map { TrendPoint(it.epochDay, it.kg) }
    val weightAvg = Trends.movingAverage(weightPoints)
    val proteinWeeks = Trends.proteinDaysPerWeek(s.nutritionByDay.map { it.epochDay to it.protein }, s.settings.proteinGoal, 12, today)
    // Average carbs per logged day, by week; only shown when carbs tracking is on.
    val carbWeeks: List<Int> = (11 downTo 0).map { back ->
        val ws = Dates.weekStart(today) - 7L * back
        val days = s.nutritionByDay.filter { it.epochDay in ws..ws + 6 && it.carbs > 0f }
        if (days.isEmpty()) 0 else (days.sumOf { it.carbs.toDouble() } / days.size).toInt()
    }
    val lifetime = Trends.lifetime(s.sessions, s.doneSets)
    val workoutDaysInRange = workoutDays.count { it >= from }

    fun inRange(points: List<TrendPoint>) = points.filter { it.epochDay >= from }
}

@Composable
fun ProgressScreen(nav: NavHostController) {
    val vm = appViewModel { ProgressViewModel(it) }
    val snap by vm.snapshot.collectAsState()
    val c = Fit.colors
    var rangeIndex by rememberSaveable { mutableIntStateOf(1) }
    val range = TrendRange.entries[rangeIndex]
    val s = snap ?: return Box(Modifier.fillMaxSize().background(c.background))
    val unit = s.settings.weightUnit
    val d = remember(s, range) { Derived(s, range, unit) }
    val reveal = rememberReveal(range)
    val dateFmt = remember { DateTimeFormatter.ofPattern("MMM d") }
    val dateLabel: (Long) -> String = { LocalDate.ofEpochDay(it).format(dateFmt) }

    Column(Modifier.fillMaxSize().background(c.background).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.cd_back), tint = c.textPrimary) }
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.progress_title), style = MaterialTheme.typography.titleLarge, color = c.textPrimary)
                Text(stringResource(R.string.progress_subtitle), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
            }
        }
        if (s.sessions.isEmpty()) {
            FitCard(Modifier.padding(16.dp)) {
                EmptyState(Icons.Rounded.ShowChart, stringResource(R.string.progress_empty_title), stringResource(R.string.progress_empty_body))
            }
            return
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            SegmentedTabs(
                listOf(R.string.progress_range_1m, R.string.progress_range_3m, R.string.progress_range_6m, R.string.progress_range_1y, R.string.progress_range_all).map { stringResource(it) },
                rangeIndex, onSelect = { rangeIndex = it },
            )
            Spacer(Modifier.height(12.dp))

            LoadCard(d, reveal)
            Spacer(Modifier.height(12.dp))

            TileGrid(s, d, unit, reveal)
            Spacer(Modifier.height(12.dp))

            StrengthCard(s, d, unit, reveal, dateLabel)
            Spacer(Modifier.height(12.dp))

            if (d.weightPoints.size >= 2) {
                FitCard {
                    Text(stringResource(R.string.weight_body), style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
                    Text(stringResource(R.string.progress_weight_hint), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                    Spacer(Modifier.height(8.dp))
                    val pts = d.inRange(d.weightPoints).ifEmpty { d.weightPoints.takeLast(2) }
                    TrendChart(
                        pts.map { TrendPoint(it.epochDay, unit.fromKg(it.value)) }, c.chartWeight, reveal = reveal,
                        average = d.inRange(d.weightAvg).ifEmpty { d.weightAvg.takeLast(2) }.map { TrendPoint(it.epochDay, unit.fromKg(it.value)) },
                        showRecords = false, format = { "%.1f".format(Locale.US, it) }, dateLabel = dateLabel,
                    )
                }
                Spacer(Modifier.height(12.dp))
            }

            FitCard {
                Text(stringResource(R.string.progress_consistency), style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
                Text(pluralStringResource(R.plurals.progress_workout_days, d.workoutDaysInRange, d.workoutDaysInRange), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                Spacer(Modifier.height(10.dp))
                Heatmap(d.grid, c.chartStrength, reveal = reveal)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    LegendDot(c.cardHigh, stringResource(R.string.progress_legend_rest))
                    LegendDot(c.chartStrength.copy(alpha = 0.55f), stringResource(R.string.progress_legend_workout))
                    LegendDot(c.gold, stringResource(R.string.progress_legend_record))
                }
            }
            Spacer(Modifier.height(12.dp))

            ThenNowCard(s, d, unit)
            Spacer(Modifier.height(12.dp))

            FitCard {
                Text(stringResource(R.string.progress_all_time), style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth()) {
                    Total("%,d".format(d.lifetime.workouts), stringResource(R.string.progress_total_workouts), Modifier.weight(1f))
                    Total("%,d".format(d.lifetime.reps), stringResource(R.string.progress_total_reps), Modifier.weight(1f))
                    Total("${d.lifetime.minutes / 60} h", stringResource(R.string.progress_total_hours), Modifier.weight(1f))
                    Total("%,d".format(d.lifetime.records), stringResource(R.string.progress_total_records), Modifier.weight(1f))
                }
                if (d.lifetime.kgLifted > 0f) {
                    Spacer(Modifier.height(8.dp))
                    Text("${"%,d".format(unit.fromKg(d.lifetime.kgLifted).roundToInt())} ${unit.label} ${stringResource(R.string.progress_total_lifted)}", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun LoadCard(d: Derived, reveal: Float) {
    val c = Fit.colors
    val thisWeek = d.weeklySets.last()
    val lastWeek = d.weeklySets.getOrNull(d.weeklySets.lastIndex - 1) ?: 0
    val change = Trends.percentChange(lastWeek.toFloat(), thisWeek.toFloat())
    FitCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MiniRing((d.load / 100f).coerceIn(0f, 1f) * reveal, c.chartStrength, c.chartGainTrack, size = 84.dp, stroke = 8.dp) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${(d.load * reveal).roundToInt()}", fontFamily = Manrope, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge, color = c.textPrimary)
                    Text("%", style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.progress_load), style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
                Text(stringResource(R.string.progress_load_hint), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(pluralStringResource(R.plurals.progress_sets_this_week, thisWeek, thisWeek), style = MaterialTheme.typography.labelLarge, color = c.textPrimary, modifier = Modifier.weight(1f, fill = false))
                    Spacer(Modifier.width(8.dp))
                    when {
                        change == null && thisWeek > 0 -> DeltaChip(stringResource(R.string.progress_first_week), positive = true)
                        change != null && change > 0 -> DeltaChip(stringResource(R.string.progress_vs_last_week, "+$change%"), positive = true)
                        change != null -> DeltaChip(stringResource(R.string.progress_vs_last_week, "$change%"), positive = false)
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        BarRow(d.weeklySets, c.chartStrength, c.chartGainTrack, reveal = reveal)
    }
}

@Composable
private fun TileGrid(s: ProgressSnapshot, d: Derived, unit: WeightUnit, reveal: Float) {
    val c = Fit.colors
    data class Tile(val label: String, val value: String, val delta: String?, val positive: Boolean, val spark: List<Float>, val color: Color)
    val tiles = mutableListOf<Tile>()
    d.topIds.take(3).forEach { id ->
        val ex = s.exercises[id] ?: return@forEach
        val pts = d.series[id].orEmpty()
        val latest = pts.lastOrNull()?.value ?: return@forEach
        val pair = Trends.thenNow(pts, d.from)
        val delta = pair?.let { (then, now) -> formatDelta(now - then, ex.kind, unit) }
        tiles += Tile(stringResource(R.string.progress_tile_best, ex.name), formatValue(latest, ex.kind, unit), delta, pair != null && pair.second > pair.first, d.inRange(pts).ifEmpty { pts }.takeLast(12).map { it.value }, c.chartStrength)
    }
    if (d.weightAvg.size >= 2) {
        val pts = d.inRange(d.weightAvg).ifEmpty { d.weightAvg.takeLast(2) }
        val diff = unit.fromKg(pts.last().value - pts.first().value)
        tiles += Tile(stringResource(R.string.progress_tile_weight), unit.number(pts.last().value), if (abs(diff) < 0.15f) stringResource(R.string.progress_holding) else "${if (diff > 0) "+" else "−"}${"%.1f".format(Locale.US, abs(diff))}", false, pts.takeLast(12).map { it.value }, c.chartWeight)
    }
    if (s.settings.trackCarbs && d.carbWeeks.any { it > 0 }) {
        val now = d.carbWeeks.last(); val goal = s.settings.carbsGoal
        val onGoal = goal > 0 && now > 0 && abs(now - goal) <= goal / 10
        tiles += Tile(stringResource(R.string.progress_tile_carbs), stringResource(R.string.progress_carbs_avg, now, goal), if (onGoal) stringResource(R.string.progress_on_target) else null, onGoal, d.carbWeeks.map { it.toFloat() }, c.purple)
    }
    if (d.proteinWeeks.any { it > 0 }) {
        val now = d.proteinWeeks.last(); val before = d.proteinWeeks.getOrNull(d.proteinWeeks.lastIndex - 1) ?: 0
        tiles += Tile(stringResource(R.string.progress_tile_protein), stringResource(R.string.progress_of_seven, now), if (now > before) "+${now - before}" else null, now > before, d.proteinWeeks.map { it.toFloat() }, c.chartProtein)
    }
    run {
        val now = d.weeklyWorkouts.last()
        val target = if (s.settings.weeklyTargetOverride > 0) s.settings.weeklyTargetOverride else s.plan?.trainingDays?.size ?: 0
        tiles += Tile(stringResource(R.string.progress_tile_workouts), "$now", if (target > 0 && now >= target) stringResource(R.string.progress_on_target) else null, target > 0 && now >= target, d.weeklyWorkouts.takeLast(12).map { it.toFloat() }, c.chartCalories)
    }
    tiles.chunked(2).forEach { row ->
        Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            row.forEach { t ->
                Column(Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).background(c.card).padding(14.dp, 12.dp, 14.dp, 10.dp)) {
                    Text(t.label, style = MaterialTheme.typography.labelSmall, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(t.value, fontFamily = Manrope, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge, color = c.textPrimary, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                        t.delta?.let { Spacer(Modifier.width(6.dp)); DeltaChip(it, t.positive) }
                    }
                    Spacer(Modifier.height(4.dp))
                    Sparkline(t.spark, t.color, reveal = reveal)
                }
            }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun StrengthCard(s: ProgressSnapshot, d: Derived, unit: WeightUnit, reveal: Float, dateLabel: (Long) -> String) {
    val c = Fit.colors
    if (d.topIds.isEmpty()) return
    var selected by rememberSaveable { mutableStateOf(d.topIds.first()) }
    if (selected !in d.topIds) selected = d.topIds.first()
    val ex = s.exercises[selected]
    val kind = ex?.kind ?: ExerciseKind.REPS
    val pts = d.inRange(d.series[selected].orEmpty())
    FitCard {
        Text(stringResource(R.string.progress_strength), style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
        Text(stringResource(R.string.progress_strength_hint), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            d.topIds.forEach { id -> ChoiceChip(s.exercises[id]?.name ?: id, selected == id, { selected = id }) }
        }
        Spacer(Modifier.height(12.dp))
        if (pts.size < 2) {
            Text(stringResource(R.string.progress_need_more, ex?.name ?: selected), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary, modifier = Modifier.padding(vertical = 24.dp))
        } else {
            TrendChart(
                pts.map { if (kind == ExerciseKind.WEIGHTED) it.copy(value = unit.fromKg(it.value)) else it },
                c.chartStrength, reveal = reveal,
                format = { v -> if (kind == ExerciseKind.WEIGHTED) unit.number(unit.toKg(v)) else "%.0f".format(v) },
                dateLabel = dateLabel,
            )
        }
    }
}

@Composable
private fun ThenNowCard(s: ProgressSnapshot, d: Derived, unit: WeightUnit) {
    val c = Fit.colors
    data class Pair3(val then: String, val now: String, val label: String, val up: Boolean)
    val rows = mutableListOf<Pair3>()
    d.topIds.take(3).forEach { id ->
        val ex = s.exercises[id] ?: return@forEach
        val (then, now) = Trends.thenNow(d.series[id].orEmpty(), d.from) ?: return@forEach
        rows += Pair3(formatValue(then, ex.kind, unit), formatValue(now, ex.kind, unit), ex.name, now > then)
    }
    if (d.weeklyWorkouts.size >= 2) {
        val then = d.weeklyWorkouts.first(); val now = d.weeklyWorkouts.last()
        rows += Pair3(stringResource(R.string.progress_per_week, then), stringResource(R.string.progress_per_week, now), stringResource(R.string.progress_tile_workouts).substringBefore(" ·"), now > then)
    }
    Trends.thenNow(d.weightAvg, d.from)?.let { (then, now) -> rows += Pair3(unit.format(then), unit.format(now), stringResource(R.string.weight_body), false) }
    if (rows.isEmpty()) return
    FitCard {
        Text(stringResource(R.string.progress_then_now), style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
        Text(stringResource(R.string.progress_then_now_hint), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
        Spacer(Modifier.height(6.dp))
        rows.forEach { r ->
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(r.then, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium, color = c.textSecondary, modifier = Modifier.weight(1f))
                Text("→", fontWeight = FontWeight.Bold, color = if (r.up) c.chartGain else c.textSecondary)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1.4f)) {
                    Text(r.now, fontFamily = Manrope, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = if (r.up) c.chartGain else c.textPrimary)
                    Text(r.label, style = MaterialTheme.typography.labelSmall, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun Total(value: String, label: String, modifier: Modifier) {
    val c = Fit.colors
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontFamily = Manrope, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = c.textPrimary, maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelSmall, color = c.textSecondary, maxLines = 1)
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(5.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = Fit.colors.textSecondary)
    }
}

private fun formatValue(v: Float, kind: ExerciseKind, unit: WeightUnit): String = when (kind) {
    ExerciseKind.HOLD -> "${v.roundToInt()} s"
    ExerciseKind.WEIGHTED -> unit.format(v, 0)
    ExerciseKind.REPS -> "${v.roundToInt()}"
}

private fun formatDelta(diff: Float, kind: ExerciseKind, unit: WeightUnit): String? {
    if (abs(diff) < 0.01f) return null
    val sign = if (diff > 0) "+" else "−"
    return when (kind) {
        ExerciseKind.HOLD -> "$sign${abs(diff).roundToInt()} s"
        ExerciseKind.WEIGHTED -> "$sign${unit.number(abs(diff), 0)} ${unit.label}"
        ExerciseKind.REPS -> "$sign${abs(diff).roundToInt()}"
    }
}
