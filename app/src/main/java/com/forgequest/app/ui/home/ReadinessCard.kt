package com.forgequest.app.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.forgequest.app.R
import com.forgequest.app.data.health.DayReadiness
import com.forgequest.app.data.health.HealthState
import com.forgequest.app.domain.DayCall
import com.forgequest.app.domain.Readiness
import com.forgequest.app.domain.StrainBand
import com.forgequest.app.ui.components.FitCard
import com.forgequest.app.ui.components.IconCircle
import com.forgequest.app.ui.components.PillButton
import com.forgequest.app.ui.theme.Fit
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

private enum class Detail { RECOVERY, SLEEP, STRAIN }

/** Whoop-style card: sleep, recovery and strain dials from the Fitbit, and what that means for today. */
@Composable
fun ReadinessCard(state: HealthState, onConnect: () -> Unit, onFeltGreat: (Boolean) -> Unit, onHide: () -> Unit) {
    when (state) {
        HealthState.Unavailable, HealthState.Loading -> Unit
        HealthState.NeedsAccess -> ConnectCard(onConnect, onHide)
        is HealthState.Ready -> if (state.day.hasData) DayCard(state.day, onFeltGreat) else NoDataCard()
    }
}

@Composable
private fun recoveryColor(recovery: Int): Color = when (Readiness.recoveryZone(recovery)) {
    2 -> Color(0xFF16C784)
    1 -> Color(0xFFF2C94C)
    else -> Color(0xFFEB5757)
}

private val SleepColor = Color(0xFF8E7CF6)
private val StrainColor = Color(0xFF3A8DFF)

@Composable
private fun ConnectCard(onConnect: () -> Unit, onHide: () -> Unit) {
    val c = Fit.colors
    FitCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconCircle(Icons.Rounded.MonitorHeart, c.coral, c.coralTrack)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.ready_connect_title), style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
                Text(stringResource(R.string.ready_connect_body), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
            }
        }
        Spacer(Modifier.height(12.dp))
        PillButton(stringResource(R.string.ready_connect), onClick = onConnect, modifier = Modifier.fillMaxWidth())
        // Optional: one tap hides it for good; Profile › Your body turns it back on.
        TextButton(onClick = onHide, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text(stringResource(R.string.ready_not_now), color = c.textSecondary) }
    }
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun NoDataCard() {
    val c = Fit.colors
    FitCard {
        Text(stringResource(R.string.ready_title), style = MaterialTheme.typography.labelLarge, color = c.textSecondary)
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.ready_no_data), style = MaterialTheme.typography.bodyMedium, color = c.textPrimary)
    }
    Spacer(Modifier.height(12.dp))
}

/** A Whoop-style ring: a dim full circle with the value arc on top, the number in the middle. */
@Composable
private fun Dial(fraction: Float, color: Color, value: String, label: String, size: Dp, stroke: Dp, big: Boolean, onClick: () -> Unit) {
    val c = Fit.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(6.dp)
        .semantics(mergeDescendants = true) { contentDescription = "$label $value" }) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(size)) {
            Canvas(Modifier.size(size)) {
                val w = stroke.toPx()
                val arcSize = androidx.compose.ui.geometry.Size(this.size.width - w, this.size.height - w)
                val topLeft = androidx.compose.ui.geometry.Offset(w / 2, w / 2)
                drawArc(color.copy(alpha = 0.18f), 0f, 360f, false, topLeft, arcSize, style = Stroke(w))
                drawArc(color, -90f, 360f * fraction.coerceIn(0f, 1f), false, topLeft, arcSize, style = Stroke(w, cap = StrokeCap.Round))
            }
            Text(value, style = if (big) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = c.textPrimary)
        }
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = c.textSecondary)
    }
}

@Composable
private fun bandName(band: StrainBand) = stringResource(
    when (band) { StrainBand.LIGHT -> R.string.band_light; StrainBand.MODERATE -> R.string.band_moderate; StrainBand.HIGH -> R.string.band_high; StrainBand.ALL_OUT -> R.string.band_all_out }
)

@Composable
private fun hm(min: Int) = stringResource(R.string.dur_hm, min / 60, min % 60)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayCard(d: DayReadiness, onFeltGreat: (Boolean) -> Unit) {
    val c = Fit.colors
    var open by remember { mutableStateOf<Detail?>(null) }
    val recColor = recoveryColor(d.recovery)
    val (title, body) = when (d.call) {
        DayCall.PEAK -> R.string.call_peak_t to R.string.call_peak_b
        DayCall.READY -> R.string.call_ready_t to R.string.call_ready_b
        DayCall.STEADY -> R.string.call_steady_t to R.string.call_steady_b
        DayCall.EASE_OFF -> R.string.call_ease_t to R.string.call_ease_b
        DayCall.RECOVER -> R.string.call_recover_t to R.string.call_recover_b
    }
    val target = Readiness.strainTarget(d.recovery)
    FitCard {
        Text(stringResource(R.string.ready_title), style = MaterialTheme.typography.labelLarge, color = c.textSecondary)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
            Dial((d.sleepPerf ?: 0) / 100f, SleepColor, d.sleepPerf?.let { "$it%" } ?: "—", stringResource(R.string.ready_sleep), 84.dp, 8.dp, false) { open = Detail.SLEEP }
            Dial(d.recovery / 100f, recColor, "${d.recovery}%", stringResource(R.string.ready_recovery), 116.dp, 11.dp, true) { open = Detail.RECOVERY }
            Dial((d.strain / 21).toFloat(), StrainColor, "%.1f".format(Locale.US, d.strain), stringResource(R.string.ready_strain), 84.dp, 8.dp, false) { open = Detail.STRAIN }
        }
        Spacer(Modifier.height(14.dp))
        Text(stringResource(title), style = MaterialTheme.typography.headlineSmall, color = recColor)
        Text(stringResource(body), style = MaterialTheme.typography.bodyMedium, color = c.textPrimary)
        Spacer(Modifier.height(8.dp))
        // The numbers behind the call, in the person's own terms.
        val why = buildList {
            d.hrv?.let { h -> add(d.hrvUsual?.let { stringResource(R.string.ready_hrv, h.toInt(), it.toInt()) } ?: stringResource(R.string.ready_hrv_only, h.toInt())) }
            d.rhr?.let { r -> add(d.rhrUsual?.let { stringResource(R.string.ready_rhr, r.toInt(), it.toInt()) } ?: stringResource(R.string.ready_rhr_only, r.toInt())) }
        }
        if (why.isNotEmpty()) Text(why.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
        Text(
            stringResource(R.string.ready_target, target.start.toInt(), target.endInclusive.toInt()) + " · " + stringResource(R.string.ready_need_tonight, hm(d.sleepNeedTonight)),
            style = MaterialTheme.typography.bodySmall, color = c.textSecondary,
        )
        if (d.baselineNights < Readiness.MIN_BASELINE_DAYS) {
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.ready_learning, d.baselineNights, Readiness.MIN_BASELINE_DAYS), style = MaterialTheme.typography.bodySmall, color = c.gold)
        }
        Spacer(Modifier.height(2.dp))
        Text(stringResource(R.string.ready_tap_hint), style = MaterialTheme.typography.labelSmall, color = c.textSecondary.copy(alpha = 0.7f))
        Spacer(Modifier.height(2.dp))
        // Only the good mornings get a tap: people often miss feeling groggy but always notice feeling great.
        TextButton(onClick = { onFeltGreat(!d.feltGreat) }) {
            Icon(Icons.Rounded.Bolt, contentDescription = null, tint = if (d.feltGreat) c.gold else c.textSecondary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(if (d.feltGreat) R.string.ready_felt_great_on else R.string.ready_felt_great), color = if (d.feltGreat) c.gold else c.textSecondary)
        }
    }
    Spacer(Modifier.height(12.dp))

    open?.let { which ->
        ModalBottomSheet(onDismissRequest = { open = null }, containerColor = c.card) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp).navigationBarsPadding()) {
                when (which) {
                    Detail.RECOVERY -> RecoveryDetail(d, recColor)
                    Detail.SLEEP -> SleepDetail(d)
                    Detail.STRAIN -> StrainDetail(d)
                }
            }
        }
    }
}

@Composable
private fun SheetTitle(value: String, label: String, color: Color) {
    val c = Fit.colors
    Text(label, style = MaterialTheme.typography.labelLarge, color = c.textSecondary)
    Text(value, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold, color = color)
    Spacer(Modifier.height(16.dp))
}

/** One contributor: today's value against the person's usual, with an arrow coloured by whether it helps. */
@Composable
private fun Contributor(label: String, today: String, usual: String?, better: Boolean?) {
    val c = Fit.colors
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = c.textPrimary, modifier = Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (better != null) Text(if (better) "▲ " else "▼ ", color = if (better) Color(0xFF16C784) else Color(0xFFEB5757), style = MaterialTheme.typography.bodyMedium)
                Text(today, style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
            }
            if (usual != null) Text(stringResource(R.string.ready_usual, usual), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
        }
    }
}

/** Seven small bars, oldest left, today right. */
@Composable
private fun WeekBars(title: String, values: List<Float?>, max: Float, colorFor: @Composable (Float) -> Color, epochDays: List<Long>) {
    val c = Fit.colors
    Spacer(Modifier.height(16.dp))
    Text(title, style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
    Spacer(Modifier.height(10.dp))
    Row(Modifier.fillMaxWidth().height(96.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
        values.forEachIndexed { i, v ->
            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                val frac = ((v ?: 0f) / max).coerceIn(0.03f, 1f)
                if (frac < 1f) Spacer(Modifier.weight(1f - frac))
                Box(Modifier.fillMaxWidth().weight(frac).clip(RoundedCornerShape(6.dp)).background(if (v == null) c.cardHigh else colorFor(v)))
                Spacer(Modifier.height(4.dp))
                Text(LocalDate.ofEpochDay(epochDays[i]).dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()), style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
            }
        }
    }
}

@Composable
private fun RecoveryDetail(d: DayReadiness, color: Color) {
    SheetTitle("${d.recovery}%", stringResource(R.string.ready_recovery), color)
    Contributor(stringResource(R.string.ready_c_hrv), d.hrv?.let { "${it.toInt()} ms" } ?: "—", d.hrvUsual?.let { "${it.toInt()} ms" }, d.hrv?.let { h -> d.hrvUsual?.let { h >= it } })
    Contributor(stringResource(R.string.ready_c_rhr), d.rhr?.let { "${it.toInt()} bpm" } ?: "—", d.rhrUsual?.let { "${it.toInt()} bpm" }, d.rhr?.let { r -> d.rhrUsual?.let { r <= it } })
    Contributor(stringResource(R.string.ready_c_resp), d.resp?.let { "%.1f".format(Locale.US, it) } ?: "—", d.respUsual?.let { "%.1f".format(Locale.US, it) }, d.resp?.let { r -> d.respUsual?.let { r <= it } })
    Contributor(stringResource(R.string.ready_c_sleep), d.sleepPerf?.let { "$it%" } ?: "—", null, d.sleepPerf?.let { it >= 85 })
    WeekBars(stringResource(R.string.ready_week_recovery), d.week.map { it.recovery?.toFloat() }, 100f, { recoveryColor(it.toInt()) }, d.week.map { it.epochDay })
    Spacer(Modifier.height(12.dp))
    Text(stringResource(R.string.ready_recovery_explain), style = MaterialTheme.typography.bodySmall, color = Fit.colors.textSecondary)
}

@Composable
private fun SleepDetail(d: DayReadiness) {
    val c = Fit.colors
    SheetTitle(d.sleepPerf?.let { "$it%" } ?: "—", stringResource(R.string.ready_sleep_perf), SleepColor)
    val n = d.night
    if (n != null) {
        Contributor(stringResource(R.string.ready_c_asleep), hm(n.asleepMin), null, null)
        Contributor(stringResource(R.string.ready_c_needed), hm(d.sleepNeedLastNight), null, null)
        val fmt = java.time.format.DateTimeFormatter.ofPattern("HH:mm", Locale.US)
        val zone = java.time.ZoneId.systemDefault()
        Contributor(stringResource(R.string.ready_c_window), "${n.start.atZone(zone).format(fmt)} – ${n.end.atZone(zone).format(fmt)}", null, null)
        Spacer(Modifier.height(8.dp))
        // Stage bar: awake, light, deep, REM, in proportion.
        val parts = listOf(n.awakeMin to Color(0xFFE0E0E0), n.lightMin to Color(0xFF6FA8FF), n.deepMin to Color(0xFF5B4BDB), n.remMin to Color(0xFFB57CF6)).filter { it.first > 0 }
        Row(Modifier.fillMaxWidth().height(14.dp).clip(CircleShape)) { parts.forEach { (m, col) -> Box(Modifier.weight(m.toFloat()).fillMaxHeight().background(col)) } }
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.ready_stage_line, hm(n.awakeMin), hm(n.lightMin), hm(n.deepMin), hm(n.remMin)),
            style = MaterialTheme.typography.bodySmall, color = c.textSecondary,
        )
    } else {
        Text(stringResource(R.string.ready_no_sleep), style = MaterialTheme.typography.bodyMedium, color = c.textPrimary)
    }
    Spacer(Modifier.height(8.dp))
    Contributor(stringResource(R.string.ready_c_tonight), hm(d.sleepNeedTonight), null, null)
    if (d.debtMin > 0) Text(stringResource(R.string.ready_debt, hm(d.debtMin)), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
    WeekBars(stringResource(R.string.ready_week_sleep), d.week.map { it.sleepMin?.toFloat()?.div(60) }, 10f, { SleepColor }, d.week.map { it.epochDay })
}

@Composable
private fun StrainDetail(d: DayReadiness) {
    val c = Fit.colors
    val target = Readiness.strainTarget(d.recovery)
    SheetTitle("%.1f".format(Locale.US, d.strain), stringResource(R.string.ready_strain) + " · " + bandName(Readiness.band(d.strain)), StrainColor)
    Contributor(stringResource(R.string.ready_c_target), "${target.start.toInt()}–${target.endInclusive.toInt()}", null, null)
    Contributor(stringResource(R.string.ready_c_steps), "%,d".format(Locale.US, d.steps), null, null)
    Contributor(stringResource(R.string.ready_c_kcal), "%,d".format(Locale.US, d.kcalBurned), null, null)
    Spacer(Modifier.height(8.dp))
    Text(stringResource(R.string.ready_zones), style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
    Spacer(Modifier.height(8.dp))
    val maxZone = (d.zoneMin.maxOrNull() ?: 0.0).coerceAtLeast(1.0)
    d.zoneMin.forEachIndexed { i, m ->
        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Z${i + 1}", style = MaterialTheme.typography.labelLarge, color = c.textSecondary, modifier = Modifier.width(32.dp))
            Box(Modifier.weight(1f).height(10.dp).clip(CircleShape).background(c.cardHigh)) {
                Box(Modifier.fillMaxWidth((m / maxZone).toFloat().coerceIn(0f, 1f)).fillMaxHeight().clip(CircleShape).background(StrainColor.copy(alpha = 0.45f + 0.11f * i)))
            }
            Text(stringResource(R.string.ready_minutes, m.toInt()), style = MaterialTheme.typography.labelLarge, color = c.textSecondary, modifier = Modifier.width(56.dp).padding(start = 8.dp))
        }
    }
    WeekBars(stringResource(R.string.ready_week_strain), d.week.map { it.strain.toFloat() }, 21f, { StrainColor }, d.week.map { it.epochDay })
    Spacer(Modifier.height(12.dp))
    Text(stringResource(R.string.ready_strain_explain), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
}
