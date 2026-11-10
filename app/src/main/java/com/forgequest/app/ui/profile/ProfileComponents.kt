package com.forgequest.app.ui.profile

import com.forgequest.app.ui.theme.ForgeShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
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
import java.util.Locale

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

/** One full-width option row with a radio circle or a checkbox; the whole row is the touch target. */
@Composable
internal fun ChoiceRow(title: String, subtitle: String?, selected: Boolean, radio: Boolean, onClick: (() -> Unit)?) {
    val c = Fit.colors
    val shape = ForgeShape.Sm
    val base = Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(shape).background(if (selected) c.tealTrack else androidx.compose.ui.graphics.Color.Transparent)
    val touch = when {
        onClick == null -> base
        radio -> base.selectable(selected, role = Role.RadioButton, onClick = onClick)
        else -> base.toggleable(selected, role = Role.Checkbox) { onClick() }
    }
    Row(touch.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            when {
                radio && selected -> Icons.Rounded.RadioButtonChecked
                radio -> Icons.Rounded.RadioButtonUnchecked
                selected -> Icons.Rounded.CheckBox
                else -> Icons.Rounded.CheckBoxOutlineBlank
            },
            contentDescription = null,
            tint = if (selected) c.teal else c.textSecondary,
            modifier = Modifier.size(22.dp).alpha(if (onClick == null && !radio) 0.6f else 1f),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = c.textPrimary)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = c.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}
