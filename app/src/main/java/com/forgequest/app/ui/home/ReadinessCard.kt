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
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalContext
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
import com.forgequest.app.ui.components.FitCard
import com.forgequest.app.ui.components.IconCircle
import com.forgequest.app.ui.components.PillButton
import com.forgequest.app.ui.theme.Fit
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale


/** Whoop-style card: sleep, recovery and strain dials from the Fitbit, and what that means for today. */
@Composable
fun ReadinessCard(
    state: HealthState,
    enabled: Boolean,
    onFeltGreat: (Boolean) -> Unit,
    onOpen: (ReadinessKind) -> Unit,
    onRefresh: () -> Unit,
    onReconnect: () -> Unit,
) {
    when {
        // Not connected: nothing on Home; Profile › Your body › Fitbit readiness connects it.
        !enabled || state == HealthState.Unavailable -> Unit
        // Same height as the full card, so Home doesn't jump when the numbers arrive.
        state == HealthState.Loading -> {
            FitCard {
                Text(stringResource(R.string.ready_title), style = MaterialTheme.typography.labelLarge, color = Fit.colors.textSecondary)
                Box(Modifier.fillMaxWidth().height(260.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Fit.colors.textSecondary) }
            }
            Spacer(Modifier.height(12.dp))
        }
        state == HealthState.NeedsAccess -> MessageCard(stringResource(R.string.ready_reconnect_body), stringResource(R.string.ready_reconnect), onReconnect)
        state == HealthState.Failed -> MessageCard(stringResource(R.string.ready_failed_body), stringResource(R.string.ready_try_again), onRefresh)
        state is HealthState.Ready -> if (state.day.hasData) DayCard(state.day, onFeltGreat, onOpen, onRefresh) else NoDataCard(onRefresh)
    }
}

@Composable
private fun MessageCard(body: String, action: String, onAction: () -> Unit) {
    val c = Fit.colors
    FitCard {
        Text(stringResource(R.string.ready_title), style = MaterialTheme.typography.labelLarge, color = c.textSecondary)
        Spacer(Modifier.height(4.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = c.textPrimary)
        Spacer(Modifier.height(10.dp))
        PillButton(action, onAction)
    }
    Spacer(Modifier.height(12.dp))
}

/** The card's title with a refresh button: after syncing the Fitbit app, one tap reads the new night. */
@Composable
private fun TitleRow(onRefresh: () -> Unit) {
    val c = Fit.colors
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.ready_title), style = MaterialTheme.typography.labelLarge, color = c.textSecondary, modifier = Modifier.weight(1f))
        IconButton(onClick = onRefresh, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.ready_refresh), tint = c.textSecondary, modifier = Modifier.size(20.dp))
        }
    }
}

/** Opens the Fitbit app so it syncs last night; nothing shows if it isn't installed. */
@Composable
private fun OpenFitbitButton() {
    val context = LocalContext.current
    val launch = remember { context.packageManager.getLaunchIntentForPackage("com.fitbit.FitbitMobile") } ?: return
    TextButton(onClick = { runCatching { context.startActivity(launch) } }) { Text(stringResource(R.string.ready_open_fitbit), color = Fit.colors.gold) }
}

private fun recoveryColor(recovery: Int): Color = recoveryTint(recovery)

private val SleepColor = SleepTint
private val StrainColor = StrainTint

@Composable
private fun NoDataCard(onRefresh: () -> Unit) {
    val c = Fit.colors
    FitCard {
        TitleRow(onRefresh)
        Text(stringResource(R.string.ready_no_data), style = MaterialTheme.typography.bodyMedium, color = c.textPrimary)
        OpenFitbitButton()
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
private fun hm(min: Int) = stringResource(R.string.dur_hm, min / 60, min % 60)

@Composable
private fun DayCard(d: DayReadiness, onFeltGreat: (Boolean) -> Unit, onOpen: (ReadinessKind) -> Unit, onRefresh: () -> Unit) {
    val c = Fit.colors
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
        TitleRow(onRefresh)
        // Before Fitbit has synced last night, these are yesterday's numbers: say so instead of passing them off as today's.
        if (d.epochDay < LocalDate.now().toEpochDay() && java.time.LocalTime.now().hour >= 6) {
            Text(stringResource(R.string.ready_yesterday), style = MaterialTheme.typography.bodySmall, color = c.gold)
            OpenFitbitButton()
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
            Dial((d.sleepPerf ?: 0) / 100f, SleepColor, d.sleepPerf?.let { "$it%" } ?: "—", stringResource(R.string.ready_sleep), 84.dp, 8.dp, false) { onOpen(ReadinessKind.SLEEP) }
            Dial(d.recovery / 100f, recColor, "${d.recovery}%", stringResource(R.string.ready_recovery), 116.dp, 11.dp, true) { onOpen(ReadinessKind.RECOVERY) }
            Dial((d.strain / 21).toFloat(), StrainColor, "%.1f".format(Locale.US, d.strain), stringResource(R.string.ready_strain), 84.dp, 8.dp, false) { onOpen(ReadinessKind.STRAIN) }
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

}
