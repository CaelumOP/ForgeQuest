package com.forgequest.app.ui.workouts

import com.forgequest.app.R

import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.PlayArrow
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.forgequest.app.AppContainer
import com.forgequest.app.data.ProgressSnapshot
import com.forgequest.app.data.db.SetEntity
import com.forgequest.app.domain.ExerciseKind
import com.forgequest.app.domain.WeightUnit
import com.forgequest.app.domain.Records
import com.forgequest.app.ui.LocalSnackbar
import com.forgequest.app.ui.Routes
import com.forgequest.app.ui.appViewModel
import com.forgequest.app.ui.components.SegmentedTabs
import com.forgequest.app.ui.components.FitCard
import com.forgequest.app.ui.components.PillButton
import com.forgequest.app.ui.components.TonalPill
import com.forgequest.app.ui.theme.Fit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class ExerciseDetailViewModel(val app: AppContainer) : ViewModel() {
    val snapshot: StateFlow<ProgressSnapshot?> = app.progress.snapshot.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val message = MutableStateFlow<String?>(null)
    fun setVideo(id: String, url: String) = viewModelScope.launch {
        if (!app.workouts.setVideoUrl(id, url)) message.value = app.str(R.string.detail_bad_link)
    }
}

private data class Metric(val label: String, val unit: String, val value: (List<SetEntity>, ExerciseKind) -> Float)

@Composable
private fun metricsFor(kind: ExerciseKind, wu: WeightUnit = WeightUnit.KG): List<Metric> = when (kind) {
    ExerciseKind.REPS -> listOf(
        Metric(stringResource(R.string.metric_max_reps), stringResource(R.string.unit_reps)) { s, _ -> (s.maxOfOrNull { it.reps ?: 0 } ?: 0).toFloat() },
        Metric(stringResource(R.string.metric_total_reps), stringResource(R.string.unit_reps)) { s, _ -> s.sumOf { it.reps ?: 0 }.toFloat() },
    )
    ExerciseKind.HOLD -> listOf(
        Metric(stringResource(R.string.metric_max_hold), stringResource(R.string.unit_s)) { s, _ -> (s.maxOfOrNull { it.seconds ?: 0 } ?: 0).toFloat() },
        Metric(stringResource(R.string.metric_total_time), stringResource(R.string.unit_s)) { s, _ -> s.sumOf { it.seconds ?: 0 }.toFloat() },
    )
    ExerciseKind.WEIGHTED -> listOf(
        Metric(stringResource(R.string.metric_best_set), stringResource(R.string.metric_est_1rm, wu.label)) { s, k -> wu.fromKg(s.maxOfOrNull { Records.score(k, it.reps, it.seconds, it.weightKg) } ?: 0f) },
        Metric(stringResource(R.string.metric_max_weight), wu.label) { s, _ -> wu.fromKg(s.maxOfOrNull { it.weightKg ?: 0f } ?: 0f) },
        Metric(stringResource(R.string.metric_volume), wu.label) { s, _ -> wu.fromKg(s.sumOf { ((it.weightKg ?: 0f) * (it.reps ?: 0)).toDouble() }.toFloat()) },
        Metric(stringResource(R.string.metric_max_reps), stringResource(R.string.unit_reps)) { s, _ -> (s.maxOfOrNull { it.reps ?: 0 } ?: 0).toFloat() },
    )
}

@Composable
fun ExerciseDetailScreen(nav: NavHostController, exerciseId: String) {
    val vm = appViewModel { ExerciseDetailViewModel(it) }
    val snap by vm.snapshot.collectAsState()
    val c = Fit.colors
    val context = LocalContext.current
    var metricIndex by remember { mutableIntStateOf(0) }
    var editLink by remember { mutableStateOf(false) }
    val message by vm.message.collectAsState()
    val snackbar = LocalSnackbar.current
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); vm.message.value = null } }

    val s = snap ?: return Box(Modifier.fillMaxSize().background(c.background))
    val ex = s.exercises[exerciseId]
    val kind = ex?.kind ?: ExerciseKind.REPS
    val unit = s.settings.weightUnit
    val metrics = metricsFor(kind, unit)
    val metric = metrics[metricIndex.coerceIn(0, metrics.lastIndex)]
    val sessionsById = s.sessions.associateBy { it.id }
    val history = s.doneSets.filter { it.exerciseId == exerciseId }.groupBy { it.sessionId }
        .mapNotNull { (sid, sets) -> sessionsById[sid]?.let { it to sets.sortedBy { set -> set.setIndex } } }
        .sortedBy { it.first.finishedAt }
    val points = history.map { (_, sets) -> metric.value(sets, kind) }

    Column(Modifier.fillMaxSize().background(c.background).statusBarsPadding().verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.cd_back), tint = c.textPrimary) }
            Text(ex?.name ?: exerciseId, style = MaterialTheme.typography.titleLarge, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(Modifier.padding(horizontal = 16.dp)) {
            Text(
                listOfNotNull(ex?.kind?.label(), ex?.musclesLabel()?.ifBlank { null }).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = c.textSecondary,
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton(stringResource(R.string.cd_watch_form), onClick = { openFormVideo(context, ex) }, icon = Icons.Rounded.PlayArrow, container = c.blueTrack, content = c.blue)
                TonalPill(if (ex?.videoUrl.isNullOrBlank()) stringResource(R.string.detail_add_link) else stringResource(R.string.detail_change_link), onClick = { editLink = true }, icon = Icons.Rounded.Link)
            }
            Spacer(Modifier.height(16.dp))
            FitCard {
                Text(stringResource(R.string.detail_progress), style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
                Spacer(Modifier.height(10.dp))
                SegmentedTabs(metrics.map { it.label }, metricIndex.coerceIn(0, metrics.lastIndex), { metricIndex = it })
                Spacer(Modifier.height(16.dp))
                if (points.size < 2) {
                    Text(
                        if (points.isEmpty()) stringResource(R.string.detail_chart_empty) else stringResource(R.string.detail_chart_one),
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.textSecondary,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                } else {
                    LineChart(points, Modifier.fillMaxWidth().height(160.dp))
                }
                points.maxOrNull()?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.detail_best, "%.0f".format(it), metric.unit), style = MaterialTheme.typography.labelLarge, color = c.gold)
                }
            }
            Spacer(Modifier.height(12.dp))
            if (history.isNotEmpty()) {
                FitCard {
                    Text(stringResource(R.string.detail_history), style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
                    history.reversed().take(20).forEach { (session, sets) ->
                        Spacer(Modifier.height(4.dp))
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { nav.navigate(Routes.session(session.id)) }.padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                LocalDate.ofEpochDay(session.epochDay).format(DateTimeFormatter.ofPattern(stringResource(R.string.detail_date_pattern))),
                                style = MaterialTheme.typography.bodyMedium, color = c.textSecondary, modifier = Modifier.weight(0.45f),
                            )
                            Text(
                                sets.joinToString("  ") { set ->
                                    when (kind) {
                                        ExerciseKind.HOLD -> "${set.seconds}s"
                                        ExerciseKind.WEIGHTED -> "${set.reps}×${unit.number(set.weightKg ?: 0f)}"
                                        ExerciseKind.REPS -> "${set.reps}"
                                    }
                                },
                                style = MaterialTheme.typography.bodyMedium, color = c.textPrimary, modifier = Modifier.weight(0.55f),
                            )
                            if (sets.any { it.isPr }) PrTag()
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (editLink) {
        var url by remember { mutableStateOf(ex?.videoUrl.orEmpty()) }
        AlertDialog(
            onDismissRequest = { editLink = false },
            containerColor = c.card,
            title = { Text(stringResource(R.string.detail_link_title), color = c.textPrimary) },
            text = {
                Column {
                    Text(stringResource(R.string.detail_link_body), color = c.textSecondary, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = url, onValueChange = { url = it }, singleLine = true, shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = c.teal, unfocusedBorderColor = c.divider, focusedTextColor = c.textPrimary, unfocusedTextColor = c.textPrimary),
                    )
                }
            },
            confirmButton = { TextButton(onClick = { vm.setVideo(exerciseId, url); editLink = false }) { Text(stringResource(R.string.action_save), color = c.teal) } },
            dismissButton = { TextButton(onClick = { editLink = false }) { Text(stringResource(R.string.action_cancel), color = c.textSecondary) } },
        )
    }
}

@Composable
fun LineChart(points: List<Float>, modifier: Modifier = Modifier) {
    val c = Fit.colors
    Canvas(modifier) {
        // Range hugs the data with a small margin, so a 75 → 76 kg month shows a real slope, not a flat line at the top.
        val lo = points.min()
        val hi = points.max()
        val margin = ((hi - lo) * 0.25f).coerceAtLeast(if (hi >= 20f) hi * 0.02f else 1f)
        val min = lo - margin
        val max = hi + margin
        val stepX = size.width / (points.size - 1).coerceAtLeast(1)
        fun pos(i: Int) = Offset(i * stepX, size.height - (points[i] - min) / (max - min) * size.height * 0.9f - size.height * 0.05f)
        for (g in 0..3) {
            val y = size.height * g / 3f
            drawLine(c.divider, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        }
        val path = Path().apply {
            moveTo(pos(0).x, pos(0).y)
            for (i in 1 until points.size) {
                val p0 = pos(i - 1)
                val p1 = pos(i)
                val mid = (p0.x + p1.x) / 2f
                cubicTo(mid, p0.y, mid, p1.y, p1.x, p1.y)
            }
        }
        drawPath(path, c.teal, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
        points.indices.forEach { i -> drawCircle(c.teal, radius = 4.dp.toPx(), center = pos(i)) }
        drawCircle(c.card, radius = 2.dp.toPx(), center = pos(points.lastIndex))
    }
}
