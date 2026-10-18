package com.forgequest.app.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.forgequest.app.ui.theme.Fit

val CardShape = RoundedCornerShape(24.dp)

/** Borderless Google Fit card. */
@Composable
fun FitCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    padding: PaddingValues = PaddingValues(20.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Fit.colors
    val base = modifier.fillMaxWidth().clip(CardShape).background(c.card)
    Column(
        modifier = (if (onClick != null) base.clickable(onClick = onClick) else base).padding(padding),
        content = content,
    )
}

/** 48 dp pastel circle with an icon, the Google Fit list-row badge. */
@Composable
fun IconCircle(icon: ImageVector, tint: Color, track: Color, size: Dp = 48.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(track), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.5f))
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleMedium, color = Fit.colors.textPrimary, modifier = Modifier.weight(1f))
        trailing()
    }
}

/** Filled pill button. */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    container: Color = Fit.colors.teal,
    content: Color = Fit.colors.onTeal,
    enabled: Boolean = true,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(50),
        color = if (enabled) container else Fit.colors.cardHigh,
        contentColor = if (enabled) content else Fit.colors.textSecondary,
        modifier = modifier,
    ) {
        Row(
            Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Quiet secondary pill. */
@Composable
fun TonalPill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, tint: Color = Fit.colors.textPrimary) {
    PillButton(text, onClick, modifier, icon, container = Fit.colors.cardHigh, content = tint)
}

/** Selectable chip used for tabs and options. */
@Composable
fun ChoiceChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, accent: Color = Fit.colors.teal) {
    val c = Fit.colors
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (selected) accent else c.cardHigh,
        contentColor = if (selected) (if (accent == c.teal) c.onTeal else c.background) else c.textPrimary,
        modifier = modifier,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Equal-width segmented tabs that always fit the screen (no clipped chips).
 * [dots] marks segments that have something selected inside them.
 */
@Composable
fun SegmentedTabs(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    dots: Set<Int> = emptySet(),
) {
    val c = Fit.colors
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(c.cardHigh).padding(4.dp)) {
        options.forEachIndexed { i, label ->
            val isSel = i == selected
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(50)).background(if (isSel) c.teal else Color.Transparent)
                    .clickable { onSelect(i) }.padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isSel) c.onTeal else c.textPrimary,
                        maxLines = 1,
                        softWrap = false,
                    )
                    if (i in dots) {
                        Spacer(Modifier.width(3.dp))
                        Box(Modifier.size(5.dp).clip(CircleShape).background(if (isSel) c.onTeal else c.teal))
                    }
                }
            }
        }
    }
}

/** "−  value  +" control. */
@Composable
fun Stepper(
    value: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    modifier: Modifier = Modifier,
    valueColor: Color = Fit.colors.textPrimary,
    minValueWidth: Dp = 40.dp,
) {
    val c = Fit.colors
    Row(
        modifier.clip(RoundedCornerShape(50)).background(c.cardHigh),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(36.dp).clip(CircleShape).clickable(onClick = onMinus), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Remove, contentDescription = "Less", tint = c.textSecondary, modifier = Modifier.size(18.dp))
        }
        Text(
            value,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = valueColor,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(minValueWidth),
            maxLines = 1,
        )
        Box(Modifier.size(36.dp).clip(CircleShape).clickable(onClick = onPlus), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Add, contentDescription = "More", tint = c.textSecondary, modifier = Modifier.size(18.dp))
        }
    }
}

/** Thin rounded progress bar. */
@Composable
fun ProgressLine(progress: Float, color: Color, track: Color, modifier: Modifier = Modifier, height: Dp = 6.dp) {
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), spring(stiffness = Spring.StiffnessLow), label = "progress")
    Box(modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(50)).background(track)) {
        Box(Modifier.fillMaxWidth(animated).height(height).clip(RoundedCornerShape(50)).background(color))
    }
}

/** Big number with a small caption underneath, used in stat rows. */
@Composable
fun StatColumn(value: String, label: String, color: Color, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).let { if (onClick != null) it.clickable(onClick = onClick) else it }.padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = color, fontWeight = FontWeight.Medium, maxLines = 1)
        Text(label, style = MaterialTheme.typography.bodySmall, color = Fit.colors.textSecondary, maxLines = 1)
    }
}

/** Big friendly message for empty or error states. */
@Composable
fun EmptyState(icon: ImageVector, title: String, body: String, modifier: Modifier = Modifier, action: @Composable () -> Unit = {}) {
    val c = Fit.colors
    Column(modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        IconCircle(icon, c.teal, c.tealTrack, 64.dp)
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = c.textPrimary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        action()
    }
}
