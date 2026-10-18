package com.forgequest.app.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.forgequest.app.R
import com.forgequest.app.data.health.DayReadiness
import com.forgequest.app.data.health.HealthState
import com.forgequest.app.domain.DayCall
import com.forgequest.app.domain.Readiness
import com.forgequest.app.ui.components.FitCard
import com.forgequest.app.ui.components.IconCircle
import com.forgequest.app.ui.components.PillButton
import com.forgequest.app.ui.components.StatColumn
import com.forgequest.app.ui.theme.Fit
import java.util.Locale

/** Whoop-style morning card: recovery, sleep and strain from the Fitbit, and what that means for today's session. */
@Composable
fun ReadinessCard(state: HealthState, onConnect: () -> Unit) {
    when (state) {
        HealthState.Unavailable, HealthState.Loading -> Unit
        HealthState.NeedsAccess -> ConnectCard(onConnect)
        is HealthState.Ready -> DayCard(state.day)
    }
}

@Composable
private fun ConnectCard(onConnect: () -> Unit) {
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
    }
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun DayCard(d: DayReadiness) {
    val c = Fit.colors
    // Without last night's sleep or any heart data a score would just be a guess.
    if (d.night == null && d.hrv == null && d.rhr == null) {
        FitCard {
            Text(stringResource(R.string.ready_title), style = MaterialTheme.typography.labelLarge, color = c.textSecondary)
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.ready_no_data), style = MaterialTheme.typography.bodyMedium, color = c.textPrimary)
        }
        Spacer(Modifier.height(12.dp))
        return
    }
    val (color: Color, title, body) = when (d.call) {
        DayCall.PEAK -> Triple(c.teal, R.string.call_peak_t, R.string.call_peak_b)
        DayCall.READY -> Triple(c.teal, R.string.call_ready_t, R.string.call_ready_b)
        DayCall.STEADY -> Triple(c.gold, R.string.call_steady_t, R.string.call_steady_b)
        DayCall.EASE_OFF -> Triple(c.coral, R.string.call_ease_t, R.string.call_ease_b)
        DayCall.RECOVER -> Triple(c.coral, R.string.call_recover_t, R.string.call_recover_b)
    }
    FitCard {
        Text(stringResource(R.string.ready_title), style = MaterialTheme.typography.labelLarge, color = c.textSecondary)
        Spacer(Modifier.height(4.dp))
        Text(stringResource(title), style = MaterialTheme.typography.headlineSmall, color = color)
        Text(stringResource(body), style = MaterialTheme.typography.bodyMedium, color = c.textPrimary)
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth()) {
            StatColumn("${d.recovery}%", stringResource(R.string.ready_recovery), color, Modifier.weight(1f))
            StatColumn(
                d.night?.let { stringResource(R.string.dur_hm, it.asleepMin / 60, it.asleepMin % 60) } ?: "—",
                stringResource(R.string.ready_sleep) + (d.sleepScore?.let { " · $it" } ?: ""),
                c.purple, Modifier.weight(1f),
            )
            StatColumn("%.1f".format(Locale.US, d.strain), stringResource(R.string.ready_strain), c.blue, Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        // Why the call is what it is, in the person's own numbers.
        val why = buildList {
            d.hrv?.let { h -> add(d.hrvUsual?.let { stringResource(R.string.ready_hrv, h.toInt(), it.toInt()) } ?: stringResource(R.string.ready_hrv_only, h.toInt())) }
            d.rhr?.let { r -> add(d.rhrUsual?.let { stringResource(R.string.ready_rhr, r.toInt(), it.toInt()) } ?: stringResource(R.string.ready_rhr_only, r.toInt())) }
            d.night?.let { add(stringResource(R.string.ready_stages, it.deepMin, it.remMin)) } ?: add(stringResource(R.string.ready_no_sleep))
            add(stringResource(R.string.ready_steps, "%,d".format(Locale.US, d.steps)))
        }
        Text(why.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
        if (d.baselineNights < Readiness.MIN_BASELINE_DAYS) {
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.ready_learning, d.baselineNights, Readiness.MIN_BASELINE_DAYS), style = MaterialTheme.typography.bodySmall, color = c.gold)
        }
    }
    Spacer(Modifier.height(12.dp))
}
