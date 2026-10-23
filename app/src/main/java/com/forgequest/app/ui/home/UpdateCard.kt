package com.forgequest.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NewReleases
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
import androidx.compose.ui.unit.dp
import com.forgequest.app.R
import com.forgequest.app.data.update.LatestVersion
import com.forgequest.app.data.update.UpdateBanner
import com.forgequest.app.ui.components.FitCard
import com.forgequest.app.ui.components.IconCircle
import com.forgequest.app.ui.components.PillButton
import com.forgequest.app.ui.components.TonalPill
import com.forgequest.app.ui.theme.Fit

/** "There's an update!" at the top of Home: notes, then Update (download with progress, then Android's installer) or Later. */
@Composable
fun UpdateCard(b: UpdateBanner, onUpdate: () -> Unit, onLater: () -> Unit) {
    val c = Fit.colors
    val accent = if (b.latest.critical) c.coral else c.teal
    FitCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconCircle(Icons.Rounded.SystemUpdate, accent, if (b.latest.critical) c.coralTrack else c.tealTrack, size = 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(if (b.latest.critical) R.string.update_critical_title else R.string.update_banner_title), style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
                Text(stringResource(R.string.update_available, b.latest.versionName), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(b.latest.notes.ifBlank { stringResource(R.string.update_no_notes) }, style = MaterialTheme.typography.bodyMedium, color = c.textPrimary)
        if (b.latest.critical) {
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.update_critical_hint), style = MaterialTheme.typography.bodySmall, color = c.coral)
        }
        Spacer(Modifier.height(12.dp))
        val progress = b.progress
        when {
            progress != null -> {
                if (progress < 0f) LinearProgressIndicator(Modifier.fillMaxWidth(), color = accent)
                else LinearProgressIndicator({ progress }, Modifier.fillMaxWidth(), color = accent)
                Spacer(Modifier.height(6.dp))
                Text(
                    if (progress < 0f) stringResource(R.string.update_downloading) else stringResource(R.string.update_progress, (progress * 100).toInt()),
                    style = MaterialTheme.typography.bodySmall, color = c.textSecondary,
                )
            }
            else -> {
                if (b.failed || b.needsPermission) {
                    Text(stringResource(if (b.failed) R.string.update_download_failed else R.string.update_allow), style = MaterialTheme.typography.bodySmall, color = c.coral)
                    Spacer(Modifier.height(8.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillButton(stringResource(R.string.update_now), onClick = onUpdate, modifier = Modifier.weight(1f), container = accent)
                    if (!b.latest.critical) TonalPill(stringResource(R.string.update_later), onClick = onLater, modifier = Modifier.weight(1f))
                }
            }
        }
    }
    Spacer(Modifier.height(12.dp))
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
