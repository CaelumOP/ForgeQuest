package com.forgequest.app.ui.workouts

import com.forgequest.app.ui.theme.ForgeShape
import com.forgequest.app.ui.theme.tabular
import androidx.compose.animation.core.snap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import com.forgequest.app.ui.theme.LocalReducedMotion
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.forgequest.app.R
import com.forgequest.app.ui.components.PillButton
import com.forgequest.app.ui.components.ProgressLine
import com.forgequest.app.ui.theme.Fit

/** Full-screen clock for a timed hold: 3-2-1, the countdown to the target, then extra time counting up. */
@Composable
internal fun HoldOverlay(h: HoldTimer, now: Long, onStop: () -> Unit, onCancel: () -> Unit) {
    val c = Fit.colors
    // The workout screen already keeps the screen on.
    BackHandler(onBack = onCancel)
    val s = h.elapsed(now)
    val ready = s < 0
    val over = s >= h.target
    val shown = when {
        ready -> "${-s}"
        over -> "+" + clock(s - h.target)
        else -> clock(h.target - s)
    }
    val tint = when { ready -> c.textSecondary; over -> c.teal; else -> c.teal }
    Column(
        Modifier.fillMaxSize().background(c.background).clickable(enabled = false) {}.statusBarsPadding().navigationBarsPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(h.exerciseName, style = MaterialTheme.typography.titleMedium, color = c.textPrimary, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            IconButton(onClick = onCancel) { Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.action_cancel), tint = c.textSecondary) }
        }
        Spacer(Modifier.weight(1f))
        Text(
            stringResource(when { ready -> R.string.hold_get_ready; over -> R.string.hold_overtime; else -> R.string.hold_holding }),
            style = MaterialTheme.typography.titleLarge, color = tint, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(shown, style = MaterialTheme.typography.displayLarge.copy(fontSize = androidx.compose.ui.unit.TextUnit(88f, androidx.compose.ui.unit.TextUnitType.Sp)).tabular(), fontWeight = FontWeight.Bold, color = tint)
        Spacer(Modifier.height(16.dp))
        ProgressLine(if (ready) 0f else (s / h.target.toFloat()).coerceIn(0f, 1f), c.teal, c.tealTrack, height = 6.dp)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.hold_target, h.target), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
        Spacer(Modifier.weight(1f))
        PillButton(
            stringResource(if (over) R.string.hold_done else R.string.hold_stop),
            onClick = if (ready) onCancel else onStop,
            modifier = Modifier.fillMaxWidth(),
        )
        if (!ready && !over) {
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.hold_stop_hint), style = MaterialTheme.typography.bodySmall, color = c.textSecondary, textAlign = TextAlign.Center)
        }
    }
}

@Composable
internal fun RestBar(secondsLeft: Int, total: Int, next: String?, onAdd: () -> Unit, onRemove: () -> Unit, onSkip: () -> Unit) {
    val c = Fit.colors
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(ForgeShape.Md).background(c.cardHigh).padding(16.dp)) {
        // At large text sizes the three buttons drop to their own line, so the time never wraps digit by digit.
        val big = com.forgequest.app.ui.components.reflowText()
        val buttons: @Composable () -> Unit = {
            TextButton(onClick = onRemove) { Text(stringResource(R.string.live_rest_minus), color = c.textPrimary, maxLines = 1) }
            TextButton(onClick = onAdd) { Text(stringResource(R.string.live_rest_plus), color = c.textPrimary, maxLines = 1) }
            TextButton(onClick = onSkip) { Text(stringResource(R.string.live_skip), color = c.teal, maxLines = 1) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.live_rest), style = MaterialTheme.typography.titleSmall, color = c.textSecondary, maxLines = 1)
            Spacer(Modifier.width(12.dp))
            Text(clock(secondsLeft), style = MaterialTheme.typography.titleLarge.tabular(), color = c.blue, maxLines = 1, softWrap = false, modifier = Modifier.weight(1f))
            if (!big) buttons()
        }
        if (big) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { buttons() }
        if (next != null) Text(stringResource(R.string.live_next, next), style = MaterialTheme.typography.bodyMedium, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(8.dp))
        RestWave(if (total == 0) 0f else secondsLeft / total.toFloat())
    }
}

/** The rest countdown as an Expressive wavy bar; with reduced motion it is a flat, still bar. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun RestWave(progress: Float) {
    val c = Fit.colors
    val reduced = LocalReducedMotion.current
    val shown by animateFloatAsState(
        progress.coerceIn(0f, 1f),
        if (reduced) snap() else androidx.compose.material3.WavyProgressIndicatorDefaults.ProgressAnimationSpec,
        label = "rest",
    )
    val stroke = with(androidx.compose.ui.platform.LocalDensity.current) { Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round) }
    androidx.compose.material3.LinearWavyProgressIndicator(
        progress = { shown },
        modifier = Modifier.fillMaxWidth(),
        color = c.blue,
        trackColor = c.blueTrack,
        stroke = stroke,
        trackStroke = stroke,
        amplitude = { if (reduced) 0f else 1f },
        waveSpeed = if (reduced) 0.dp else androidx.compose.material3.WavyProgressIndicatorDefaults.LinearDeterminateWavelength,
    )
}
