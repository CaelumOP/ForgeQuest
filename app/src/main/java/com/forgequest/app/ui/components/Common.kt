package com.forgequest.app.ui.components

import com.forgequest.app.R

import androidx.compose.ui.res.stringResource

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.runtime.LaunchedEffect
import java.util.Locale
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
        modifier = modifier.semantics { this.selected = selected },
        color = if (selected) accent else c.cardHigh,
        contentColor = if (selected) (if (accent == c.teal) c.onTeal else c.background) else c.textPrimary,
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
                    .selectable(selected = isSel, role = Role.Tab) { onSelect(i) }.padding(vertical = 9.dp, horizontal = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isSel) c.onTeal else c.textPrimary,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
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

/**
 * "−  value  +" control. When [onValueClick] is set the number itself is tappable (used to type a value directly),
 * and it's drawn slightly brighter so the affordance is visible.
 */
@Composable
fun Stepper(
    value: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    modifier: Modifier = Modifier,
    valueColor: Color = Fit.colors.textPrimary,
    minValueWidth: Dp = 40.dp,
    onValueClick: (() -> Unit)? = null,
) {
    val c = Fit.colors
    val typeLabel = stringResource(R.string.cd_type_value)
    Row(
        modifier.clip(RoundedCornerShape(50)).background(c.cardHigh),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onMinus, role = Role.Button), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Remove, contentDescription = stringResource(R.string.cd_decrease, value), tint = c.textSecondary, modifier = Modifier.size(18.dp))
        }
        Box(
            Modifier.heightIn(min = 40.dp).widthIn(min = minValueWidth)
                .let { if (onValueClick != null) it.clip(RoundedCornerShape(10.dp)).clickable(onClick = onValueClick, role = Role.Button, onClickLabel = typeLabel) else it }
                .padding(horizontal = 2.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                value,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = valueColor,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
        Box(Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onPlus, role = Role.Button), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.cd_increase, value), tint = c.textSecondary, modifier = Modifier.size(18.dp))
        }
    }
}

/** Formats a number for display and parsing with a fixed locale so "72.5" round-trips through the keyboard. */
fun formatNumber(value: Float, decimals: Int): String =
    if (decimals <= 0) "%,d".format(Locale.US, value.toInt()).replace(",", "") else "%.${decimals}f".format(Locale.US, value)

/**
 * Stepper over a numeric value: ± nudge by [step], tap the number to type it.
 * [unit] is shown after the value ("kg", "s", "reps"); [decimals] controls display and rounding.
 */
@Composable
fun NumberStepper(
    value: Float,
    onValueChange: (Float) -> Unit,
    step: Float,
    min: Float,
    max: Float,
    modifier: Modifier = Modifier,
    unit: String = "",
    decimals: Int = 0,
    title: String = stringResource(R.string.stepper_enter_value),
    valueColor: Color = Fit.colors.textPrimary,
    minValueWidth: Dp = 40.dp,
    onTick: (() -> Unit)? = null,
) {
    var editing by remember { mutableStateOf(false) }
    fun commit(v: Float) {
        val rounded = if (decimals <= 0) kotlin.math.round(v) else kotlin.math.round(v * 10f.pow(decimals)) / 10f.pow(decimals)
        onValueChange(rounded.coerceIn(min, max))
    }
    val shown = formatNumber(value, decimals) + if (unit.isNotEmpty()) " $unit" else ""
    Stepper(
        value = shown,
        onMinus = { onTick?.invoke(); commit(value - step) },
        onPlus = { onTick?.invoke(); commit(value + step) },
        modifier = modifier,
        valueColor = valueColor,
        minValueWidth = minValueWidth,
        onValueClick = { editing = true },
    )
    if (editing) {
        NumberEntryDialog(
            title = title,
            initial = value,
            unit = unit,
            decimals = decimals,
            min = min,
            max = max,
            onDismiss = { editing = false },
            onConfirm = { editing = false; onTick?.invoke(); commit(it) },
        )
    }
}

private fun Float.pow(n: Int): Float { var r = 1f; repeat(n) { r *= this }; return r }

/** Parses what a person typed, accepting a comma as the decimal separator. */
fun parseNumber(text: String): Float? = text.trim().replace(',', '.').toFloatOrNull()

/** Full-keyboard entry for one number. The field opens focused with the current value selected, so typing replaces it. */
@Composable
fun NumberEntryDialog(
    title: String,
    initial: Float,
    unit: String,
    decimals: Int,
    min: Float,
    max: Float,
    onDismiss: () -> Unit,
    onConfirm: (Float) -> Unit,
) {
    val c = Fit.colors
    val start = formatNumber(initial, decimals)
    var text by remember { mutableStateOf(TextFieldValue(start, selection = TextRange(0, start.length))) }
    val focus = remember { FocusRequester() }
    val parsed = parseNumber(text.text)
    val valid = parsed != null && parsed >= min && parsed <= max
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.card,
        title = { Text(title, color = c.textPrimary) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { v -> text = v.copy(text = v.text.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' }.take(8)) },
                    singleLine = true,
                    suffix = if (unit.isNotEmpty()) ({ Text(unit, color = c.textSecondary) }) else null,
                    isError = text.text.isNotEmpty() && !valid,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (decimals > 0) KeyboardType.Decimal else KeyboardType.Number,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { if (valid) onConfirm(parsed!!) }),
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = c.teal, unfocusedBorderColor = c.divider, errorBorderColor = c.coral,
                        focusedTextColor = c.textPrimary, unfocusedTextColor = c.textPrimary, cursorColor = c.teal,
                    ),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.stepper_range, formatNumber(min, decimals), formatNumber(max, decimals) + if (unit.isNotEmpty()) " $unit" else ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (text.text.isNotEmpty() && !valid) c.coral else c.textSecondary,
                )
            }
        },
        confirmButton = { TextButton(onClick = { if (valid) onConfirm(parsed!!) }, enabled = valid) { Text(stringResource(R.string.action_save), color = c.teal) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel), color = c.textSecondary) } },
    )
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
