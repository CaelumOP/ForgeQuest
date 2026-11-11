package com.forgequest.app.ui.home

import com.forgequest.app.ui.theme.ForgeSpace
import com.forgequest.app.ui.theme.ForgeShape
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.forgequest.app.R
import com.forgequest.app.data.health.DayReadiness
import com.forgequest.app.data.health.HealthState
import com.forgequest.app.data.health.RefreshNote
import com.forgequest.app.data.health.showsYesterday
import com.forgequest.app.domain.DayCall
import com.forgequest.app.domain.TodayFocus
import com.forgequest.app.domain.Readiness
import com.forgequest.app.ui.bodyRes
import com.forgequest.app.ui.components.FitCard
import com.forgequest.app.ui.components.PillButton
import com.forgequest.app.ui.theme.Fit
import com.forgequest.app.ui.theme.tabular


/** Everything Home says about readiness except the good case, which is [ReadinessRow] inside the workout card. */
@Composable
fun ReadinessCard(
    state: HealthState,
    enabled: Boolean,
    refreshing: Boolean,
    note: RefreshNote?,
    onRefresh: () -> Unit,
    onReconnect: () -> Unit,
) {
    when {
        // Not connected: nothing on Home; Profile › Your body › Fitbit readiness connects it.
        !enabled || state == HealthState.Unavailable -> Unit
        // Still reading: no card of its own. The workout card already holds the row's place ([ReadinessRowPlaceholder]),
        // so nothing on Home moves when the numbers arrive.
        state == HealthState.Loading -> Unit
        state == HealthState.NeedsAccess -> MessageCard(stringResource(R.string.ready_reconnect_body), stringResource(R.string.ready_reconnect), onReconnect)
        state == HealthState.Failed -> MessageCard(stringResource(R.string.ready_failed_body), stringResource(R.string.ready_try_again), onRefresh)
        state is HealthState.Ready -> if (!state.day.hasData) NoDataCard(refreshing, note, onRefresh)
    }
}

@Composable
private fun MessageCard(body: String, action: String, onAction: () -> Unit) {
    val c = Fit.colors
    FitCard(padding = PaddingValues(ForgeSpace.CardPaddingCompact)) {
        Text(stringResource(R.string.ready_title), style = MaterialTheme.typography.labelLarge, color = c.textSecondary)
        Spacer(Modifier.height(4.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = c.textPrimary)
        Spacer(Modifier.height(12.dp))
        PillButton(action, onAction)
    }
    Spacer(Modifier.height(ForgeSpace.Sm))
}

/** The card's title with a refresh button: after syncing the Fitbit app, one tap reads the new night. It spins while reading. */
@Composable
private fun TitleRow(refreshing: Boolean, onRefresh: () -> Unit) {
    val c = Fit.colors
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.ready_title), style = MaterialTheme.typography.labelLarge, color = c.textSecondary, modifier = Modifier.weight(1f))
        RefreshButton(refreshing, onRefresh, Modifier.size(32.dp))
    }
}

@Composable
private fun RefreshButton(refreshing: Boolean, onRefresh: () -> Unit, modifier: Modifier = Modifier) {
    val c = Fit.colors
    val angle = if (refreshing) {
        rememberInfiniteTransition(label = "refresh").animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "angle").value
    } else 0f
    IconButton(onClick = onRefresh, enabled = !refreshing, modifier = modifier) {
        Icon(
            Icons.Rounded.Refresh, contentDescription = stringResource(if (refreshing) R.string.ready_refreshing else R.string.ready_refresh),
            tint = if (refreshing) c.gold else c.textSecondary, modifier = Modifier.size(20.dp).rotate(angle),
        )
    }
}

/** What the last refresh found, under the title; "still waiting" lives in the yesterday line instead. */
@Composable
private fun NoteLine(note: RefreshNote?) {
    val c = Fit.colors
    val (text, color) = when (note) {
        RefreshNote.UPDATED -> R.string.ready_updated to c.teal
        RefreshNote.NEW_NIGHT -> R.string.ready_new_night to c.teal
        RefreshNote.FAILED -> R.string.ready_refresh_failed to c.coral
        else -> return
    }
    Text(stringResource(text), style = MaterialTheme.typography.bodySmall, color = color, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
}

/** Opens the Fitbit app so it syncs last night; nothing shows if it isn't installed. */
@Composable
private fun OpenFitbitButton() {
    val context = LocalContext.current
    val launch = remember { context.packageManager.getLaunchIntentForPackage("com.fitbit.FitbitMobile") } ?: return
    TextButton(onClick = { runCatching { context.startActivity(launch) } }) { Text(stringResource(R.string.ready_open_fitbit), color = Fit.colors.gold) }
}

@Composable
private fun NoDataCard(refreshing: Boolean, note: RefreshNote?, onRefresh: () -> Unit) {
    val c = Fit.colors
    FitCard(padding = PaddingValues(ForgeSpace.CardPaddingCompact)) {
        TitleRow(refreshing, onRefresh)
        NoteLine(note)
        Text(stringResource(R.string.ready_no_data), style = MaterialTheme.typography.bodyMedium, color = c.textPrimary)
        OpenFitbitButton()
    }
    Spacer(Modifier.height(ForgeSpace.Sm))
}

/**
 * Holds [ReadinessRow]'s place in the workout card while Health Connect is still being read: the same height, an empty
 * ring and the title. Home decides to show it from the saved "connected" setting, which is known on the first frame.
 */
@Composable
fun ReadinessRowPlaceholder() {
    val c = Fit.colors
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val w = 5.dp.toPx()
                drawArc(c.textSecondary.copy(alpha = 0.18f), 0f, 360f, false, androidx.compose.ui.geometry.Offset(w / 2, w / 2), androidx.compose.ui.geometry.Size(size.width - w, size.height - w), style = Stroke(w))
            }
        }
        Spacer(Modifier.width(ForgeSpace.Sm))
        Text(stringResource(R.string.ready_title), style = MaterialTheme.typography.titleSmall, color = c.textSecondary, maxLines = 1)
    }
}

/** One slim row at the top of Today's workout card: recovery ring and verdict with the day's advice, and the "I feel great" bolt. */
@Composable
fun ReadinessRow(
    d: DayReadiness, focus: TodayFocus?, note: RefreshNote?, onFeltGreat: (Boolean) -> Unit, onOpen: (ReadinessKind) -> Unit,
    /** Which sentence to show and which of its wordings; Home picks the wording so it stays the same all day. */
    line: com.forgequest.app.domain.ReadinessLine = com.forgequest.app.domain.readinessLine(d.call, focus, d.sleepContext),
    variant: Int = 0,
) {
    val c = Fit.colors
    val recColor = recoveryTint(d.recovery)
    val (title, _) = when (d.call) {
        DayCall.PEAK -> R.string.call_peak_t to R.string.call_peak_b
        DayCall.READY -> R.string.call_ready_t to R.string.call_ready_b
        DayCall.STEADY -> R.string.call_steady_t to R.string.call_steady_b
        DayCall.EASE_OFF -> R.string.call_ease_t to R.string.call_ease_b
        DayCall.RECOVER -> R.string.call_recover_t to R.string.call_recover_b
    }
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.weight(1f).clip(ForgeShape.Sm)
                .clickable(onClickLabel = stringResource(R.string.ready_title)) { onOpen(ReadinessKind.RECOVERY) }
                .semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val w = 5.dp.toPx()
                    val arcSize = androidx.compose.ui.geometry.Size(size.width - w, size.height - w)
                    val topLeft = androidx.compose.ui.geometry.Offset(w / 2, w / 2)
                    drawArc(recColor.copy(alpha = 0.18f), 0f, 360f, false, topLeft, arcSize, style = Stroke(w))
                    drawArc(recColor, -90f, 360f * (d.recovery / 100f).coerceIn(0f, 1f), false, topLeft, arcSize, style = Stroke(w, cap = StrokeCap.Round))
                }
                Text("${d.recovery}", style = MaterialTheme.typography.labelMedium.tabular(), color = c.textPrimary, maxLines = 1)
            }
            Spacer(Modifier.width(ForgeSpace.Sm))
            Column(Modifier.weight(1f)) {
                Text(stringResource(title), style = MaterialTheme.typography.titleSmall, color = recColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                // Before Fitbit has synced last night, these are yesterday's numbers: say so instead of passing them off as today's.
                if (d.showsYesterday()) {
                    val line = when (note) {
                        RefreshNote.WAITING -> R.string.ready_waiting
                        RefreshNote.GAVE_UP -> R.string.ready_gave_up
                        else -> R.string.ready_yesterday
                    }
                    Text(stringResource(line), style = MaterialTheme.typography.bodySmall, color = c.gold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                } else {
                    // The short-sleep sentence names how long the person slept; the others take no number.
                    val slept = d.sleepContext?.lastNightMin ?: 0
                    val text = if (line == com.forgequest.app.domain.ReadinessLine.EASE_SHORT_SLEEP || line == com.forgequest.app.domain.ReadinessLine.RECOVER_SHORT_SLEEP) {
                        stringResource(line.bodyRes(variant), stringResource(R.string.dur_hm, slept / 60, slept % 60))
                    } else stringResource(line.bodyRes(variant))
                    Text(text, style = MaterialTheme.typography.bodySmall, color = c.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        // Only the good mornings get a tap: people often miss feeling groggy but always notice feeling great.
        IconToggleButton(checked = d.feltGreat, onCheckedChange = onFeltGreat) {
            Icon(Icons.Rounded.Bolt, contentDescription = stringResource(R.string.ready_felt_great), tint = if (d.feltGreat) c.teal else c.textSecondary)
        }
    }
}
