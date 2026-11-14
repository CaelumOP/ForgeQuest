package com.forgequest.app.ui.profile

import com.forgequest.app.ui.theme.ForgeShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.SportsGymnastics
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.forgequest.app.R
import com.forgequest.app.ui.components.FitCard
import com.forgequest.app.ui.components.FitSwitch
import com.forgequest.app.ui.components.IconCircle
import com.forgequest.app.ui.components.NumberStepper
import com.forgequest.app.ui.theme.Fit

internal fun badgeIcon(cat: com.forgequest.app.domain.BadgeCategory): ImageVector = when (cat) {
    com.forgequest.app.domain.BadgeCategory.CONSISTENCY -> Icons.Rounded.LocalFireDepartment
    com.forgequest.app.domain.BadgeCategory.SKILL -> Icons.Rounded.SportsGymnastics
    com.forgequest.app.domain.BadgeCategory.STRENGTH -> Icons.Rounded.FitnessCenter
    com.forgequest.app.domain.BadgeCategory.NUTRITION -> Icons.Rounded.Restaurant
}

@Composable
internal fun BadgeChip(title: String, description: String, icon: ImageVector, unlocked: Boolean, isNew: Boolean, modifier: Modifier = Modifier, progress: Pair<Int, Int>? = null) {
    val c = Fit.colors
    var open by remember { mutableStateOf(false) }
    Column(
        modifier.clip(ForgeShape.Md).clickable { open = true }.padding(vertical = 8.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            Box(Modifier.size(52.dp).clip(CircleShape).background(if (unlocked) c.goldTrack else c.cardHigh), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = if (unlocked) c.gold else c.textSecondary, modifier = Modifier.size(26.dp).alpha(if (unlocked) 1f else 0.4f))
            }
            if (isNew) Box(Modifier.size(12.dp).clip(CircleShape).background(c.coral))
        }
        Spacer(Modifier.height(8.dp))
        Text(title, style = MaterialTheme.typography.labelSmall, color = if (unlocked) c.textPrimary else c.textSecondary, textAlign = TextAlign.Center, maxLines = 2)
        if (!unlocked && progress != null && progress.first > 0) Text("⁦${progress.first} / ${progress.second}⁩", style = MaterialTheme.typography.labelSmall, color = c.gold)
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            containerColor = c.card,
            icon = { Icon(icon, contentDescription = null, tint = if (unlocked) c.gold else c.textSecondary) },
            title = { Text(title, color = c.textPrimary) },
            text = { Text(if (unlocked) stringResource(R.string.badge_unlocked_detail, description) else stringResource(R.string.badge_locked_detail, description), color = c.textSecondary) },
            confirmButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.action_ok), color = c.teal) } },
        )
    }
}

/** Header of a settings card that toggles as a whole: icon, title, current state, switch. */
@Composable
internal fun SwitchHeader(icon: ImageVector, tint: androidx.compose.ui.graphics.Color, track: androidx.compose.ui.graphics.Color, title: String, subtitle: String, checked: Boolean, enabled: Boolean = true, onToggle: (Boolean) -> Unit) {
    val c = Fit.colors
    Row(Modifier.fillMaxWidth().toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onToggle), verticalAlignment = Alignment.CenterVertically) {
        IconCircle(icon, tint, track)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = c.textPrimary, modifier = Modifier.semantics { heading() })
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
        }
        FitSwitch(checked, null, enabled = enabled)
    }
}

@Composable
internal fun SettingsRow(icon: ImageVector, tint: androidx.compose.ui.graphics.Color, track: androidx.compose.ui.graphics.Color, title: String, subtitle: String, onClick: () -> Unit) {
    val c = Fit.colors
    FitCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconCircle(icon, tint, track)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = c.textSecondary)
        }
    }
}

@Composable
internal fun SettingStepper(label: String, value: Float, onChange: (Float) -> Unit, step: Float, min: Float, max: Float, unit: String = "", decimals: Int = 0, placeholder: String? = null) {
    val c = Fit.colors
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = c.textPrimary, modifier = Modifier.weight(1f))
        NumberStepper(value, onChange, step, min, max, unit = unit, decimals = decimals, title = label, minValueWidth = 84.dp, placeholder = placeholder)
    }
}
/** How a [SettingsGroup] draws: a tappable row in the settings list, or the group's own page. */
internal enum class GroupMode { Row, Page }

internal val LocalGroupMode = androidx.compose.runtime.staticCompositionLocalOf { GroupMode.Row }

/**
 * A settings category. In the list ([GroupMode.Row]) it is one card with an icon, a title, a one-line summary of
 * the current values and a chevron, and tapping it opens that group's page. On the page ([GroupMode.Page]) only the
 * cards inside are drawn.
 */
@Composable
internal fun SettingsGroup(
    icon: ImageVector,
    tint: androidx.compose.ui.graphics.Color,
    track: androidx.compose.ui.graphics.Color,
    title: String,
    summary: String,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Fit.colors
    if (LocalGroupMode.current == GroupMode.Page) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
        return
    }
    FitCard(
        onClick = onToggle,
        padding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconCircle(icon, tint, track)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = c.textPrimary, modifier = Modifier.semantics { heading() })
                // From 1.35x text the summary wraps in full instead of stopping at two lines.
                Text(summary, style = MaterialTheme.typography.bodySmall, color = c.textSecondary, maxLines = if (com.forgequest.app.ui.components.reflowText()) Int.MAX_VALUE else 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(8.dp))
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = c.textSecondary)
        }
    }
    Spacer(Modifier.height(12.dp))
}

/**
 * "Check for updates", first on the settings list and on Profile. When Home's update check already knows of a newer
 * build, the row says which version is waiting and shows a dot.
 */
@Composable
internal fun UpdateCheckRow(vm: SettingsViewModel) {
    val c = Fit.colors
    val notice = vm.app.updateNotice
    androidx.compose.runtime.LaunchedEffect(Unit) { notice.refresh() }
    val waiting by notice.banner.collectAsState()
    val newer = waiting?.latest?.versionName
    FitCard(onClick = { vm.checkUpdate() }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box {
                IconCircle(Icons.Rounded.SystemUpdate, c.teal, c.tealTrack)
                if (newer != null) Box(Modifier.align(Alignment.TopEnd).size(10.dp).clip(CircleShape).background(c.coral))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.update_title), style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
                Text(
                    if (newer != null) stringResource(R.string.update_available, newer)
                    else stringResource(R.string.update_hint, com.forgequest.app.BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (newer != null) c.teal else c.textSecondary,
                )
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = c.textSecondary)
        }
    }
}

/** The update dialogs for the Check for updates row on Profile: "version X is available", the progress dialog and the result message. */
@Composable
internal fun UpdateCheckDialogs(vm: SettingsViewModel) {
    val c = Fit.colors
    val latest by vm.update.collectAsState()
    latest?.let { v ->
        AlertDialog(
            onDismissRequest = { vm.update.value = null },
            containerColor = c.card,
            title = { Text(stringResource(R.string.update_available, v.versionName), color = c.textPrimary) },
            // What's new shows once, after the update installs (WhatsNewDialog), not here as well.
            text = { Text(stringResource(R.string.update_notes_after), color = c.textSecondary) },
            confirmButton = { TextButton(onClick = vm::installUpdate) { Text(stringResource(R.string.update_now), color = c.teal) } },
            dismissButton = { TextButton(onClick = { vm.update.value = null }) { Text(stringResource(R.string.update_later), color = c.textSecondary) } },
        )
    }
    val busy by vm.busy.collectAsState()
    val message by vm.message.collectAsState()
    val snackbar = com.forgequest.app.ui.LocalSnackbar.current
    androidx.compose.runtime.LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); vm.message.value = null } }
    busy?.let {
        AlertDialog(onDismissRequest = {}, containerColor = c.card, confirmButton = {}, title = { Text(it, color = c.textPrimary) },
            text = { androidx.compose.material3.LinearProgressIndicator(color = c.teal, trackColor = c.tealTrack, modifier = Modifier.fillMaxWidth()) })
    }
}
