package com.forgequest.app.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Close
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.IntOffset
import com.forgequest.app.ui.theme.ForgeMotion
import com.forgequest.app.ui.theme.LocalReducedMotion
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.forgequest.app.R
import com.forgequest.app.data.update.LatestVersion
import com.forgequest.app.data.update.UpdateBanner
import com.forgequest.app.ui.components.FitCard
import com.forgequest.app.ui.components.IconCircle
import com.forgequest.app.ui.theme.Fit

/**
 * The update card laid over Home's greeting row: it fades and slides down from the top when an update arrives and goes
 * back up when dismissed. Reduced motion: no slide, a short fade.
 */
@Composable
fun UpdateOverlay(banner: UpdateBanner?, onUpdate: () -> Unit, onLater: () -> Unit, modifier: Modifier = Modifier) {
    val reduced = LocalReducedMotion.current
    // Keeps the last banner on screen while the card animates out.
    var last by remember { mutableStateOf(banner) }
    if (banner != null) last = banner
    AnimatedVisibility(
        visible = banner != null,
        modifier = modifier,
        enter = fadeIn(ForgeMotion.effects(reduced)) + slideInVertically(ForgeMotion.spatial(reduced, visibilityThreshold = IntOffset.VisibilityThreshold)) { -it },
        exit = fadeOut(ForgeMotion.effects(reduced)) + slideOutVertically(ForgeMotion.spatial(reduced, visibilityThreshold = IntOffset.VisibilityThreshold)) { -it },
    ) {
        last?.let { UpdateCard(it, onUpdate, onLater, Modifier.padding(top = 8.dp)) }
    }
}

/**
 * "There's an update!" kept to one row: the version, then Update (download with progress, then Android's installer)
 * and an X that hides it until later. The notes wait for the What's new dialog.
 */
@Composable
fun UpdateCard(b: UpdateBanner, onUpdate: () -> Unit, onLater: () -> Unit, modifier: Modifier = Modifier) {
    val c = Fit.colors
    val accent = if (b.latest.critical) c.coral else c.teal
    FitCard(modifier = modifier, padding = PaddingValues(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconCircle(Icons.Rounded.SystemUpdate, accent, if (b.latest.critical) c.coralTrack else c.tealTrack, size = 32.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(if (b.latest.critical) R.string.update_critical_title else R.string.update_banner_title), style = MaterialTheme.typography.titleSmall, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(stringResource(R.string.update_available, b.latest.versionName), style = MaterialTheme.typography.bodySmall, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (b.progress == null) {
                TextButton(onClick = onUpdate) { Text(stringResource(R.string.update_now), color = accent, style = MaterialTheme.typography.labelLarge) }
            }
            // The X does what Later did. A critical update can't be put off, as before.
            if (!b.latest.critical) IconButton(onClick = onLater) {
                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.cd_dismiss), tint = c.textSecondary)
            }
        }
        val progress = b.progress
        when {
            progress != null -> {
                Spacer(Modifier.height(8.dp))
                if (progress < 0f) LinearProgressIndicator(Modifier.fillMaxWidth().padding(end = 6.dp), color = accent)
                else LinearProgressIndicator({ progress }, Modifier.fillMaxWidth().padding(end = 6.dp), color = accent)
                Spacer(Modifier.height(4.dp))
                Text(
                    if (progress < 0f) stringResource(R.string.update_downloading) else stringResource(R.string.update_progress, (progress * 100).toInt()),
                    style = MaterialTheme.typography.bodySmall, color = c.textSecondary,
                )
            }
            b.failed || b.needsPermission -> {
                Spacer(Modifier.height(4.dp))
                Text(stringResource(if (b.notReady) R.string.update_not_ready else if (b.failed) R.string.update_download_failed else R.string.update_allow), style = MaterialTheme.typography.bodySmall, color = c.coral)
            }
        }
    }
}

/** Shown once, the first time a new version opens: the release notes from version.json. */
@Composable
fun WhatsNewDialog(v: LatestVersion, onDismiss: () -> Unit) {
    val c = Fit.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.card,
        icon = { IconCircle(Icons.Rounded.NewReleases, c.purple, c.purpleTrack, size = 40.dp) },
        title = { Text(stringResource(R.string.whats_new_title, v.versionName), style = MaterialTheme.typography.titleLarge, color = c.textPrimary) },
        text = {
            Text(v.notes, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary, modifier = Modifier.verticalScroll(rememberScrollState()))
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_ok), color = c.teal) } },
    )
}
