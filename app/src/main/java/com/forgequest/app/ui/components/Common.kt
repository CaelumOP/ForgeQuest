package com.forgequest.app.ui.components

import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.material3.ripple
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.composed
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.runtime.CompositionLocalProvider
import com.forgequest.app.R

import androidx.compose.ui.res.stringResource

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.toggleableState
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.draw.alpha
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
/** The app's switch: teal when on. Pass null for [onCheckedChange] when the whole row toggles. */
@Composable
fun FitSwitch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, enabled: Boolean = true) =
    androidx.compose.material3.Switch(
        // A gap before the switch, so a long subtitle beside it never runs into it.
        checked, onCheckedChange, modifier = Modifier.padding(start = 12.dp), enabled = enabled,
        colors = androidx.compose.material3.SwitchDefaults.colors(checkedTrackColor = Fit.colors.teal, checkedThumbColor = Fit.colors.card),
    )

/** Short message at the bottom of the screen, for results that need no action. */
fun android.content.Context.toast(message: String) = android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_LONG).show()

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
            // Two lines at large text sizes instead of cutting the label off.
            Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.weight(1f, fill = false))
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
    /** Optional smaller second line under each label (e.g. "~0.6 kg a week"); labels may then wrap to 2 lines. */
    subs: List<String> = emptyList(),
    disabled: Set<Int> = emptySet(),
    /** Long labels wrap to 2 lines instead of being cut off. */
    wrap: Boolean = false,
) {
    val c = Fit.colors
    val tall = subs.isNotEmpty() || wrap
    // At large text sizes five equal slices can't hold their labels, so the row scrolls sideways with each label whole.
    val scrolling = !tall && options.size >= 4 && isLargeText()
    // Tall segments share one height, so a one-line option is as tall as a wrapped one.
    Row(
        modifier.fillMaxWidth().height(IntrinsicSize.Min).clip(RoundedCornerShape(if (tall) 24 else 50)).background(c.cardHigh)
            .then(if (scrolling) Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()) else Modifier).padding(4.dp),
    ) {
        options.forEachIndexed { i, label ->
            val isSel = i == selected
            val off = i in disabled
            Box(
                (if (scrolling) Modifier else Modifier.weight(1f)).fillMaxHeight().clip(RoundedCornerShape(if (tall) 20 else 50)).background(if (isSel) c.teal else Color.Transparent)
                    .selectable(selected = isSel, enabled = !off, role = Role.Tab) { onSelect(i) }
                    .alpha(if (off) 0.4f else 1f).padding(vertical = 9.dp, horizontal = if (scrolling) 14.dp else 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (tall) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(label, style = MaterialTheme.typography.labelMedium, color = if (isSel) c.onTeal else c.textPrimary, maxLines = 2, textAlign = TextAlign.Center)
                        subs.getOrNull(i)?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = if (isSel) c.onTeal else c.textSecondary, maxLines = 1, textAlign = TextAlign.Center) }
                    }
                } else Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isSel) c.onTeal else c.textPrimary,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier = if (scrolling) Modifier else Modifier.weight(1f, fill = false),
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
    // Minus stays left and plus right in Arabic too, like a number line.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
    Row(
        modifier.clip(RoundedCornerShape(50)).background(c.cardHigh),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).clip(CircleShape).repeatingClickable(onMinus), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Remove, contentDescription = stringResource(R.string.cd_decrease, value), tint = c.textSecondary, modifier = Modifier.size(22.dp))
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
        Box(Modifier.size(44.dp).clip(CircleShape).repeatingClickable(onPlus), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.cd_increase, value), tint = c.textSecondary, modifier = Modifier.size(22.dp))
        }
    }
    }
}

/**
 * Tap to step once; hold to keep stepping, slowly at first and then faster (Strong and Hevy do the same), so
 * 75 → 85 kg is one press instead of twenty. Keeps a ripple and a normal click for TalkBack.
 */
private fun Modifier.repeatingClickable(onClick: () -> Unit): Modifier = composed {
    val current by rememberUpdatedState(onClick)
    val scope = rememberCoroutineScope()
    val interaction = remember { MutableInteractionSource() }
    this
        .indication(interaction, ripple())
        .semantics { role = Role.Button; onClick { current(); true } }
        .pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown()
                // Ours, not the row underneath: a long hold must not also open the set-note dialog.
                down.consume()
                val press = PressInteraction.Press(down.position)
                scope.launch { interaction.emit(press) }
                // Step on lift or after a still hold, never on touch-down: a scroll that starts on the button must
                // not change the value (people lost calories and workouts per week that way).
                var repeating = false
                val repeat = scope.launch {
                    delay(420)
                    repeating = true
                    var gap = 110L
                    while (true) {
                        current()
                        delay(gap)
                        gap = (gap * 0.9f).toLong().coerceAtLeast(60)
                    }
                }
                var tapped = false
                while (true) {
                    val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                    if (change.changedToUp()) { tapped = !repeating; break }
                    if (change.isConsumed || (change.position - down.position).getDistance() > viewConfiguration.touchSlop) break
                }
                repeat.cancel()
                if (tapped) current()
                scope.launch { interaction.emit(PressInteraction.Release(press)) }
            }
        }
}

/**
 * A toggle for boxes inside scrolling lists (a set's tick): counts only when the finger lifts without having moved,
 * and the press ripple waits a moment, so scrolling over the boxes neither ticks them nor makes them flash.
 */
fun Modifier.steadyToggleable(value: Boolean, onToggle: () -> Unit): Modifier = composed {
    val current by rememberUpdatedState(onToggle)
    val scope = rememberCoroutineScope()
    val interaction = remember { MutableInteractionSource() }
    this
        .indication(interaction, ripple())
        .semantics {
            role = Role.Checkbox
            toggleableState = androidx.compose.ui.state.ToggleableState(value)
            onClick { current(); true }
        }
        .pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown()
                val press = PressInteraction.Press(down.position)
                var pressed = false
                val showPress = scope.launch { delay(90); interaction.emit(press); pressed = true }
                var tapped = false
                while (true) {
                    val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                    if (change.changedToUp()) { tapped = !change.isConsumed; break }
                    if (change.isConsumed || (change.position - down.position).getDistance() > viewConfiguration.touchSlop / 2) break
                }
                showPress.cancel()
                if (pressed) scope.launch { interaction.emit(PressInteraction.Release(press)) }
                if (tapped) { down.consume(); current() }
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
    /** Shown instead of the number until a value is set (e.g. "– cm"). */
    placeholder: String? = null,
) {
    var editing by remember { mutableStateOf(false) }
    // Shown value runs ahead of the stored one, so holding ± keeps counting even while the database write
    // for the previous step is still on its way back.
    var local by remember { mutableStateOf(value) }
    var lastCommit by remember { mutableStateOf(0L) }
    // Echoes of earlier steps arriving mid-hold are ignored; anything else (an edit elsewhere) wins.
    LaunchedEffect(value) { if (android.os.SystemClock.uptimeMillis() - lastCommit > 800) local = value }
    fun commit(v: Float) {
        val rounded = if (decimals <= 0) kotlin.math.round(v) else kotlin.math.round(v * 10f.pow(decimals)) / 10f.pow(decimals)
        val next = rounded.coerceIn(min, max)
        if (next == local) return
        local = next
        lastCommit = android.os.SystemClock.uptimeMillis()
        onTick?.invoke()
        onValueChange(next)
    }
    val shown = placeholder ?: (formatNumber(local, decimals) + if (unit.isNotEmpty()) " $unit" else "")
    Stepper(
        value = shown,
        onMinus = { commit(local - step) },
        onPlus = { commit(local + step) },
        modifier = modifier,
        valueColor = valueColor,
        minValueWidth = minValueWidth,
        onValueClick = { editing = true },
    )
    if (editing) {
        NumberEntryDialog(
            title = title,
            initial = local,
            unit = unit,
            decimals = decimals,
            min = min,
            max = max,
            onDismiss = { editing = false },
            onConfirm = { editing = false; commit(it) },
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

/** The text size (as a multiple of the system default) above which tight layouts switch to their large-text form. */
const val LARGE_TEXT = 1.3f

/** True when the person's text size is above [LARGE_TEXT]. */
@Composable
fun isLargeText(): Boolean = androidx.compose.ui.platform.LocalDensity.current.fontScale > LARGE_TEXT

/** Shows [content] with the text size stopping at [max], for rows whose labels share a fixed width. */
@Composable
fun CappedFontScale(max: Float = LARGE_TEXT, content: @Composable () -> Unit) {
    val d = androidx.compose.ui.platform.LocalDensity.current
    CompositionLocalProvider(
        androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(d.density, d.fontScale.coerceAtMost(max)),
        content = content,
    )
}
