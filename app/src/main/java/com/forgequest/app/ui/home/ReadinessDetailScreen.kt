package com.forgequest.app.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.navigation.NavHostController
import com.forgequest.app.R
import com.forgequest.app.container
import com.forgequest.app.data.health.DayReadiness
import com.forgequest.app.data.health.HealthState
import com.forgequest.app.domain.Readiness
import com.forgequest.app.domain.StrainBand
import com.forgequest.app.ui.components.SegmentedTabs
import com.forgequest.app.ui.theme.Fit
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

/** Which metric a detail page shows. */
enum class ReadinessKind { RECOVERY, SLEEP, STRAIN }

internal val SleepTint = Color(0xFF8E7CF6)
internal val StrainTint = Color(0xFF3A8DFF)
private val TargetGreen = Color(0xFF7BD88F)
private val StageColors = listOf(Color(0xFFF48FB1), Color(0xFF8AB4F8), Color(0xFF7E57C2), Color(0xFFB3E5FC)) // awake, light, deep, REM

internal fun recoveryTint(recovery: Int): Color = when (Readiness.recoveryZone(recovery)) {
    2 -> Color(0xFF16C784)
    1 -> Color(0xFFF2C94C)
    else -> Color(0xFFEB5757)
}

/** Full-page detail in the style of Google Health: Day and Week tabs, a big number, charts with axes, and an About section. */
@Composable
fun ReadinessDetailScreen(nav: NavHostController, kind: ReadinessKind) {
    val c = Fit.colors
    val state by LocalContext.current.container.health.state.collectAsState()
    val d = (state as? HealthState.Ready)?.day
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val title = stringResource(
        when (kind) { ReadinessKind.RECOVERY -> R.string.ready_recovery; ReadinessKind.SLEEP -> R.string.ready_sleep; ReadinessKind.STRAIN -> R.string.ready_strain }
    )
    Column(Modifier.fillMaxSize().background(c.background).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.action_back), tint = c.textPrimary) }
            Text(title, style = MaterialTheme.typography.titleLarge, color = c.textPrimary, modifier = Modifier.semantics { heading() })
        }
        if (d == null || !d.hasData) {
            Text(stringResource(R.string.ready_no_data), style = MaterialTheme.typography.bodyLarge, color = c.textSecondary, modifier = Modifier.padding(20.dp))
            return@Column
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding()) {
            SegmentedTabs(listOf(stringResource(R.string.ready_tab_day), stringResource(R.string.ready_tab_week)), tab, onSelect = { tab = it })
            Spacer(Modifier.height(20.dp))
            when (kind) {
                ReadinessKind.RECOVERY -> if (tab == 0) RecoveryDay(d) else RecoveryWeek(d)
                ReadinessKind.SLEEP -> if (tab == 0) SleepDay(d) else SleepWeek(d)
                ReadinessKind.STRAIN -> if (tab == 0) StrainDay(d) else StrainWeek(d)
            }
            Spacer(Modifier.height(28.dp))
            HorizontalDivider(color = c.divider)
            Spacer(Modifier.height(20.dp))
            Text(stringResource(R.string.ready_about, title), style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(when (kind) { ReadinessKind.RECOVERY -> R.string.ready_recovery_explain; ReadinessKind.SLEEP -> R.string.ready_sleep_explain; ReadinessKind.STRAIN -> R.string.ready_strain_explain }),
                style = MaterialTheme.typography.bodyMedium, color = c.textSecondary,
            )
            Spacer(Modifier.height(32.dp))
        }
    }
}

// ---------------------------------------------------------------- shared pieces

@Composable
private fun hm(min: Int) = stringResource(R.string.dur_hm, min / 60, min % 60)

@Composable
private fun BigNumber(value: String, suffix: String?, sub: String?, color: Color) {
    val c = Fit.colors
    Row(verticalAlignment = Alignment.Bottom) {
        Text(value, style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Medium, color = color)
        if (suffix != null) {
            Spacer(Modifier.width(8.dp))
            Text(suffix, style = MaterialTheme.typography.titleMedium, color = c.textPrimary, modifier = Modifier.padding(bottom = 8.dp))
        }
    }
    if (sub != null) Text(sub, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
}

@Composable
private fun SectionTitle(text: String) {
    Spacer(Modifier.height(24.dp))
    Text(text, style = MaterialTheme.typography.titleMedium, color = Fit.colors.textPrimary, modifier = Modifier.semantics { heading() })
    Spacer(Modifier.height(12.dp))
}

/** A row like Google Health's: label, value, and how it compares with the person's usual. */
@Composable
private fun MetricRow(label: String, value: String, usual: String?, better: Boolean?) {
    val c = Fit.colors
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = c.textPrimary, modifier = Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (better != null) Box(Modifier.padding(end = 8.dp).width(8.dp).height(8.dp).clip(CircleShape).background(if (better) Color(0xFF16C784) else Color(0xFFEB5757)))
                Text(value, style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
            }
            if (usual != null) Text(stringResource(R.string.ready_usual, usual), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
        }
    }
}

private fun dayLetter(epochDay: Long) = LocalDate.ofEpochDay(epochDay).dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()).take(3)

/**
 * Seven days with a value axis on the right and an optional dotted target, like Google Health's week view. Bars for
 * amounts, a joined line for strain.
 */
@Composable
private fun WeekChart(values: List<Float?>, days: List<Long>, maxY: Float, ticks: List<Float>, tickLabel: (Float) -> String, target: Float?, line: Boolean, valueLabel: (Float) -> String, colorFor: (Float) -> Color) {
    val c = Fit.colors
    val grid = c.divider
    Row(Modifier.fillMaxWidth().height(200.dp)) {
        Canvas(Modifier.weight(1f).fillMaxHeight().padding(vertical = 8.dp)) {
            val w = size.width
            val h = size.height
            fun y(v: Float) = h - (v / maxY).coerceIn(0f, 1f) * h
            ticks.forEach { t -> drawLine(grid, Offset(0f, y(t)), Offset(w, y(t)), strokeWidth = 1.dp.toPx()) }
            target?.let { t ->
                drawLine(TargetGreen, Offset(0f, y(t)), Offset(w, y(t)), strokeWidth = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 9.dp.toPx())), cap = StrokeCap.Round)
            }
            val slot = w / values.size
            var prev: Offset? = null
            values.forEachIndexed { i, v ->
                val cx = slot * i + slot / 2
                if (v == null) { prev = null; return@forEachIndexed }
                if (line) {
                    val p = Offset(cx, y(v))
                    prev?.let { drawLine(colorFor(v), it, p, strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round) }
                    drawCircle(colorFor(v), 5.dp.toPx(), p)
                    prev = p
                } else {
                    val bw = slot * 0.56f
                    drawRoundRect(colorFor(v), Offset(cx - bw / 2, y(v)), Size(bw, h - y(v)), CornerRadius(6.dp.toPx()))
                }
            }
        }
        BoxWithConstraints(Modifier.width(44.dp).fillMaxHeight().padding(vertical = 8.dp)) {
            ticks.forEach { t ->
                val frac = 1f - (t / maxY).coerceIn(0f, 1f)
                Text(tickLabel(t), style = MaterialTheme.typography.labelSmall, color = c.textSecondary, modifier = Modifier.offset(x = 8.dp, y = maxHeight * frac - 8.dp))
            }
        }
    }
    // Each day's value under its bar, so the chart reads without guessing from the grid.
    Row(Modifier.fillMaxWidth().padding(end = 44.dp)) {
        values.forEach { v ->
            Text(v?.let(valueLabel) ?: "", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = v?.let(colorFor) ?: c.textSecondary,
                modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 1)
        }
    }
    Row(Modifier.fillMaxWidth().padding(end = 44.dp)) {
        days.forEach { Text(dayLetter(it), style = MaterialTheme.typography.labelSmall, color = c.textSecondary, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
    }
    if (target != null) {
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Text("···", color = TargetGreen, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.ready_target_legend), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
        }
    }
}

/** Names for the workout types other apps record most; anything else is just "Workout". */
private fun exerciseName(type: Int): Int = when (type) {
    ExerciseSessionRecord.EXERCISE_TYPE_RUNNING, ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL -> R.string.ex_type_running
    ExerciseSessionRecord.EXERCISE_TYPE_WALKING -> R.string.ex_type_walking
    ExerciseSessionRecord.EXERCISE_TYPE_BIKING, ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY -> R.string.ex_type_cycling
    ExerciseSessionRecord.EXERCISE_TYPE_SOCCER -> R.string.ex_type_football
    ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL, ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER -> R.string.ex_type_swimming
    ExerciseSessionRecord.EXERCISE_TYPE_HIKING -> R.string.ex_type_hiking
    ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING, ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING -> R.string.ex_type_strength
    else -> R.string.ex_type_workout
}

/** A labelled share bar, like Google Health's "How you earned cardio load". */
@Composable
private fun ShareBar(label: String, share: Float, color: Color) {
    val c = Fit.colors
    Text(label, style = MaterialTheme.typography.bodyMedium, color = c.textPrimary)
    Spacer(Modifier.height(6.dp))
    Box(Modifier.fillMaxWidth().height(28.dp).clip(RoundedCornerShape(14.dp)).background(c.cardHigh)) {
        if (share > 0f) Box(Modifier.fillMaxWidth(share.coerceIn(0.04f, 1f)).fillMaxHeight().clip(RoundedCornerShape(14.dp)).background(color))
    }
    Spacer(Modifier.height(14.dp))
}

// ---------------------------------------------------------------- recovery

@Composable
private fun RecoveryDay(d: DayReadiness) {
    val tint = recoveryTint(d.recovery)
    val callBody = stringResource(
        when (d.call) {
            com.forgequest.app.domain.DayCall.PEAK -> R.string.call_peak_b
            com.forgequest.app.domain.DayCall.READY -> R.string.call_ready_b
            com.forgequest.app.domain.DayCall.STEADY -> R.string.call_steady_b
            com.forgequest.app.domain.DayCall.EASE_OFF -> R.string.call_ease_b
            com.forgequest.app.domain.DayCall.RECOVER -> R.string.call_recover_b
        }
    )
    BigNumber("${d.recovery}%", null, callBody, tint)
    SectionTitle(stringResource(R.string.ready_contributors))
    MetricRow(stringResource(R.string.ready_c_hrv), d.hrv?.let { "${it.toInt()} ms" } ?: "—", d.hrvUsual?.let { "${it.toInt()} ms" }, d.hrv?.let { h -> d.hrvUsual?.let { h >= it } })
    MetricRow(stringResource(R.string.ready_c_rhr), d.rhr?.let { "${it.toInt()} bpm" } ?: "—", d.rhrUsual?.let { "${it.toInt()} bpm" }, d.rhr?.let { r -> d.rhrUsual?.let { r <= it } })
    val brpm = stringResource(R.string.unit_brpm)
    MetricRow(stringResource(R.string.ready_c_resp), d.resp?.let { "%.1f %s".format(Locale.US, it, brpm) } ?: "—", d.respUsual?.let { "%.1f %s".format(Locale.US, it, brpm) }, d.resp?.let { r -> d.respUsual?.let { r <= it } })
    MetricRow(stringResource(R.string.ready_c_sleep), d.sleepPerf?.let { "$it%" } ?: "—", null, d.sleepPerf?.let { it >= 85 })
    if (d.baselineNights < Readiness.MIN_BASELINE_DAYS) {
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.ready_learning, d.baselineNights, Readiness.MIN_BASELINE_DAYS), style = MaterialTheme.typography.bodySmall, color = Fit.colors.gold)
    }
}

@Composable
private fun RecoveryWeek(d: DayReadiness) {
    val vals = d.week.map { it.recovery?.toFloat() }
    val avg = vals.filterNotNull().takeIf { it.isNotEmpty() }?.average()?.toInt()
    BigNumber(avg?.let { "$it%" } ?: "—", stringResource(R.string.ready_week_avg), null, avg?.let { recoveryTint(it) } ?: Fit.colors.textPrimary)
    Spacer(Modifier.height(16.dp))
    WeekChart(vals, d.week.map { it.epochDay }, 100f, listOf(0f, 33f, 67f, 100f), { "${it.toInt()}" }, null, false, { "${it.toInt()}%" }) { recoveryTint(it.toInt()) }
}

// ---------------------------------------------------------------- sleep

@Composable
private fun SleepDay(d: DayReadiness) {
    val c = Fit.colors
    val n = d.night ?: run { Text(stringResource(R.string.ready_no_sleep), color = c.textSecondary); return }
    val zone = ZoneId.systemDefault()
    val fmt = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
    BigNumber(hm(n.asleepMin), "${n.start.atZone(zone).format(fmt)} – ${n.end.atZone(zone).format(fmt)}", null, SleepTint)
    val efficiency = if (n.inBedMin > 0) (100 * n.asleepMin / n.inBedMin) else 0
    Text(stringResource(R.string.ready_sleep_sentence, d.sleepPerf ?: 0, hm(d.sleepNeedLastNight), hm(n.inBedMin), efficiency), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
    SectionTitle(stringResource(R.string.ready_stages_title))
    Hypnogram(n.start, n.end, n.segments, listOf(n.awakeMin, n.remMin, n.lightMin, n.deepMin), n.gaps)
    SectionTitle(stringResource(R.string.ready_c_tonight))
    BigNumber(hm(d.sleepNeedTonight), null, if (d.debtMin > 0) stringResource(R.string.ready_debt, hm(d.debtMin)) else null, c.textPrimary)
}

/** Lanes for awake, REM, light and deep with each stretch drawn at its time, like Google Health's sleep timeline. */
@Composable
private fun Hypnogram(start: Instant, end: Instant, segments: List<com.forgequest.app.domain.Seg>, minutes: List<Int>, gaps: List<Pair<Instant, Instant>>) {
    val c = Fit.colors
    val total = (end.epochSecond - start.epochSecond).coerceAtLeast(1).toFloat()
    val lanes = listOf(0 to R.string.ready_stage_awake, 3 to R.string.ready_stage_rem, 1 to R.string.ready_stage_light, 2 to R.string.ready_stage_deep)
    // REM, light and deep as a share of time asleep, so a good or thin night of deep sleep is easy to spot.
    val asleep = minutes.drop(1).sum()
    lanes.forEachIndexed { i, (stage, label) ->
        val share = if (stage != 0 && asleep > 0) " (" + (100 * minutes[i] / asleep) + "%)" else ""
        Text(stringResource(label) + " · " + hm(minutes[i]) + share, style = MaterialTheme.typography.bodyMedium, color = c.textPrimary)
        Spacer(Modifier.height(6.dp))
        val color = StageColors[stage]
        Canvas(Modifier.fillMaxWidth().height(22.dp)) {
            drawRoundRect(c.cardHigh, Offset.Zero, size, CornerRadius(size.height / 2))
            // Time up between two chunks of a split night: dashed, so it reads as a break rather than missing data.
            gaps.forEach { (a, b) ->
                val x0 = (a.epochSecond - start.epochSecond) / total * size.width
                val x1 = (b.epochSecond - start.epochSecond) / total * size.width
                drawLine(c.textSecondary.copy(alpha = 0.5f), Offset(x0, size.height / 2), Offset(x1, size.height / 2), strokeWidth = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())))
            }
            segments.filter { it.stage == stage }.forEach { s ->
                val x0 = (s.start.epochSecond - start.epochSecond) / total * size.width
                val x1 = (s.end.epochSecond - start.epochSecond) / total * size.width
                drawRoundRect(color, Offset(x0, 0f), Size((x1 - x0).coerceAtLeast(3.dp.toPx()), size.height), CornerRadius(size.height / 2))
            }
        }
        Spacer(Modifier.height(14.dp))
    }
    if (segments.isEmpty()) Text(stringResource(R.string.ready_no_stages), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
    val zone = ZoneId.systemDefault()
    val fmt = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
    // A time mark every two hours between falling asleep and waking.
    val hourFmt = DateTimeFormatter.ofPattern("h a", Locale.getDefault())
    val firstMark = start.atZone(zone).truncatedTo(java.time.temporal.ChronoUnit.HOURS).plusHours(1).let { if (it.hour % 2 == 1) it.plusHours(1) else it }
    val marks = generateSequence(firstMark) { it.plusHours(2) }.takeWhile { it.toInstant() < end }.toList()
    if (marks.isNotEmpty()) {
        // Left to right like the lanes above, which are drawn the same way in Arabic.
        androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr) {
            BoxWithConstraints(Modifier.fillMaxWidth().height(18.dp)) {
                marks.forEach { m ->
                    val frac = (m.toEpochSecond() - start.epochSecond) / total
                    // Skip marks that would sit on the edge labels below.
                    if (frac in 0.08f..0.92f) Text(m.format(hourFmt), style = MaterialTheme.typography.labelSmall, color = c.textSecondary,
                        modifier = Modifier.offset(x = maxWidth * frac - 14.dp))
                }
            }
        }
    }
    gaps.forEach { (a, b) ->
        Text(stringResource(R.string.ready_gap, a.atZone(zone).format(fmt), b.atZone(zone).format(fmt)), style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
    }
    Row(Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text(start.atZone(zone).format(fmt), style = MaterialTheme.typography.labelMedium, color = c.textPrimary)
            Text(stringResource(R.string.ready_to_bed), style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(end.atZone(zone).format(fmt), style = MaterialTheme.typography.labelMedium, color = c.textPrimary)
            Text(stringResource(R.string.ready_out_of_bed), style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
        }
    }
}

@Composable
private fun SleepWeek(d: DayReadiness) {
    val hours = d.week.map { it.sleepMin?.div(60f) }
    val avgMin = d.week.mapNotNull { it.sleepMin }.takeIf { it.isNotEmpty() }?.average()?.toInt()
    BigNumber(avgMin?.let { hm(it) } ?: "—", stringResource(R.string.ready_week_avg_night), null, SleepTint)
    Spacer(Modifier.height(16.dp))
    WeekChart(hours, d.week.map { it.epochDay }, 10f, listOf(0f, 4f, 8f, 10f), { "${it.toInt()}h" }, Readiness.BASE_NEED_MIN / 60f, false, { h -> (h * 60).roundToInt().let { "${it / 60}:" + "%02d".format(Locale.US, it % 60) } }) { SleepTint }
}

// ---------------------------------------------------------------- strain

@Composable
private fun StrainDay(d: DayReadiness) {
    val c = Fit.colors
    val target = Readiness.strainTarget(d.recovery)
    val band = stringResource(
        when (Readiness.band(d.strain)) { StrainBand.LIGHT -> R.string.band_light; StrainBand.MODERATE -> R.string.band_moderate; StrainBand.HIGH -> R.string.band_high; StrainBand.ALL_OUT -> R.string.band_all_out }
    )
    BigNumber("%.1f".format(Locale.US, d.strain), stringResource(R.string.ready_of_21, band), stringResource(R.string.ready_target_sentence, target.start.toInt(), target.endInclusive.toInt()), StrainTint)
    Spacer(Modifier.height(16.dp))
    // Progress toward 21 with today's target band marked.
    Canvas(Modifier.fillMaxWidth().height(14.dp)) {
        drawRoundRect(c.cardHigh, Offset.Zero, size, CornerRadius(size.height / 2))
        val x0 = (target.start / 21).toFloat() * size.width
        val x1 = (target.endInclusive / 21).toFloat() * size.width
        drawRoundRect(TargetGreen.copy(alpha = 0.35f), Offset(x0, 0f), Size(x1 - x0, size.height), CornerRadius(size.height / 2))
        drawRoundRect(StrainTint, Offset.Zero, Size((d.strain / 21).toFloat() * size.width, size.height), CornerRadius(size.height / 2))
    }
    SectionTitle(stringResource(R.string.ready_how_earned))
    ShareBar(stringResource(R.string.ready_earned_workouts, (d.workoutShare * 100).toInt()), d.workoutShare, StrainTint)
    val fq = (d.workoutShare * 100).toInt()
    val other = (d.otherShare * 100).toInt()
    if (d.otherWorkouts.isNotEmpty() || other > 0) {
        val named = d.otherWorkouts.map { (it.title ?: stringResource(exerciseName(it.exerciseType))) + " " + stringResource(R.string.ready_minutes, it.minutes) }.joinToString(" · ")
        ShareBar(stringResource(R.string.ready_earned_other_workouts, other) + if (named.isNotEmpty()) "\n$named" else "", d.otherShare, StrainTint.copy(alpha = 0.8f))
    }
    ShareBar(stringResource(R.string.ready_earned_other, (100 - fq - other).coerceAtLeast(0)), (1f - d.workoutShare - d.otherShare).coerceAtLeast(0f), StrainTint.copy(alpha = 0.6f))
    SectionTitle(stringResource(R.string.ready_zones))
    val names = listOf(R.string.zone_1, R.string.zone_2, R.string.zone_3, R.string.zone_4, R.string.zone_5)
    d.zoneMin.forEachIndexed { i, m ->
        MetricRow(stringResource(names[i]), if (m < 1 && m > 0) "<1 min" else stringResource(R.string.ready_minutes, m.toInt()), null, null)
    }
    SectionTitle(stringResource(R.string.ready_activity))
    MetricRow(stringResource(R.string.ready_c_steps), "%,d".format(Locale.US, d.steps), null, null)
    MetricRow(stringResource(R.string.ready_c_kcal), "%,d".format(Locale.US, d.kcalBurned), null, null)
}

@Composable
private fun StrainWeek(d: DayReadiness) {
    val vals = d.week.map { it.strain.toFloat() }
    val avg = vals.average()
    BigNumber("%.1f".format(Locale.US, avg), stringResource(R.string.ready_week_avg), null, StrainTint)
    Spacer(Modifier.height(16.dp))
    WeekChart(vals, d.week.map { it.epochDay }, 21f, listOf(0f, 7f, 14f, 21f), { "${it.toInt()}" }, Readiness.strainTarget(d.recovery).start.toFloat(), true, { "%.1f".format(Locale.US, it) }) { StrainTint }
}
