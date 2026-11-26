package com.forgequest.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.forgequest.app.R
import com.forgequest.app.container
import com.forgequest.app.domain.PlateMath
import com.forgequest.app.ui.theme.Fit
import com.forgequest.app.ui.theme.ForgeShape
import com.forgequest.app.ui.theme.tabular
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** Pure editing state for the thumb-zone keypad, testable without Compose UI. */
internal data class KeypadDraft(
    val text: String,
    val fresh: Boolean = true,
) {
    fun parsed(): Float? = parseNumber(text)

    fun isValid(min: Float, max: Float): Boolean {
        val v = parsed() ?: return false
        return v >= min && v <= max
    }

    fun pressDigit(digit: Char, decimals: Int): KeypadDraft {
        if (digit !in '0'..'9') return this
        val base = if (fresh) "" else text
        if (base.length >= 8) return this
        if (decimals > 0) {
            val dot = base.indexOf('.')
            if (dot >= 0 && base.length - dot - 1 >= decimals) return this
        }
        val next = if (base == "0" && digit == '0') "0" else if (base == "0") digit.toString() else base + digit
        return KeypadDraft(next, fresh = false)
    }

    fun pressDecimal(decimals: Int): KeypadDraft {
        if (decimals <= 0) return this
        val base = if (fresh) "" else text
        if ('.' in base) return this
        val next = if (base.isEmpty()) "0." else "$base."
        return KeypadDraft(next, fresh = false)
    }

    fun pressBackspace(): KeypadDraft {
        if (fresh) return KeypadDraft("", fresh = false)
        if (text.isEmpty()) return this
        return KeypadDraft(text.dropLast(1), fresh = false)
    }

    fun clear(): KeypadDraft = KeypadDraft("", fresh = false)

    fun applyDelta(delta: Float, fallback: Float, min: Float, max: Float, decimals: Int): KeypadDraft {
        val base = parsed() ?: fallback
        val next = (base + delta).coerceIn(min, max)
        return KeypadDraft(formatCompactNumber(next, decimals), fresh = false)
    }

    fun setExact(value: Float, min: Float, max: Float, decimals: Int): KeypadDraft {
        val clamped = value.coerceIn(min, max)
        return KeypadDraft(formatCompactNumber(clamped, decimals), fresh = false)
    }
}

internal fun formatCompactNumber(value: Float, decimals: Int): String {
    if (decimals <= 0) return value.roundToInt().toString()
    val raw = formatNumber(value, decimals)
    return if ('.' in raw) raw.trimEnd('0').trimEnd('.') else raw
}

/**
 * Bottom-docked thumb-zone numeric keypad for live workout entry.
 * Features quick increment chips (+2.5 / +5 / +10 in kg or +5 / +10 / +25 in lb),
 * an optional "Last time" chip, a 3x4 keypad grid, and a Done button.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NumberEntryKeypadSheet(
    title: String,
    initial: Float,
    unit: String,
    decimals: Int,
    min: Float,
    max: Float,
    quickDeltas: List<Float> = emptyList(),
    lastTimeValue: Float? = null,
    lastTimeLabel: String? = null,
    plateCalculator: ((Float) -> PlateMath.Breakdown)? = null,
    onDismiss: () -> Unit,
    onConfirm: (Float) -> Unit,
) {
    val c = Fit.colors
    val haptics = LocalContext.current.container.haptics
    var draft by remember(initial) {
        mutableStateOf(KeypadDraft(formatCompactNumber(initial, decimals), fresh = true))
    }
    var platesExpanded by remember { mutableStateOf(false) }
    val parsed = draft.parsed()
    val valid = draft.isValid(min, max)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberFullSheetState(),
        containerColor = c.card,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(
            topStart = ForgeShape.LgRadius,
            topEnd = ForgeShape.LgRadius,
        ),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = c.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                if (draft.text.isNotEmpty()) {
                    TextButton(onClick = { haptics.tick(); draft = draft.clear() }) {
                        Text(stringResource(R.string.live41_keypad_clear), color = c.textSecondary)
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.cd_close), tint = c.textSecondary)
                }
            }

            // Readout tile
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(ForgeShape.Md)
                    .background(c.cardHigh)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    val shownText = draft.text.ifEmpty { "—" }
                    Text(
                        if (unit.isNotEmpty() && draft.text.isNotEmpty()) "$shownText $unit" else shownText,
                        style = MaterialTheme.typography.headlineMedium.tabular(),
                        color = if (draft.text.isNotEmpty() && !valid) c.coral else c.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        stringResource(
                            R.string.stepper_range,
                            formatCompactNumber(min, decimals),
                            formatCompactNumber(max, decimals) + if (unit.isNotEmpty()) " $unit" else "",
                        ),
                        style = MaterialTheme.typography.bodySmall.tabular(),
                        color = if (draft.text.isNotEmpty() && !valid) c.coral else c.textSecondary,
                    )
                }
            }

            val breakdown = remember(parsed, initial, plateCalculator) {
                val target = parsed ?: initial
                if (plateCalculator != null && target > 0f) plateCalculator(target) else null
            }
            if (breakdown != null && !breakdown.belowBar) {
                Spacer(Modifier.height(10.dp))
                val unitLabel = breakdown.unit.label
                val summaryText = if (breakdown.barOnly) {
                    stringResource(R.string.live41_plates_bar_only, "${PlateMath.formatPlate(breakdown.barWeight)} $unitLabel")
                } else {
                    stringResource(R.string.live41_plates_side, PlateMath.formatPerSideSummary(breakdown.perSide))
                }
                val breakdownCd = stringResource(R.string.live41_plates_breakdown_cd, "${PlateMath.formatPlate(breakdown.targetWeight)} $unitLabel")
                val stateLabel = stringResource(if (platesExpanded) R.string.cd_expanded else R.string.cd_collapsed)
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(ForgeShape.Md)
                        .background(c.cardHigh),
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable(role = Role.Button) { platesExpanded = !platesExpanded }
                            .semantics {
                                contentDescription = breakdownCd
                                stateDescription = stateLabel
                            }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(R.string.live41_plates_title),
                            style = MaterialTheme.typography.labelMedium,
                            color = c.textSecondary,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            summaryText,
                            style = MaterialTheme.typography.labelLarge.tabular(),
                            color = c.blue,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Icon(
                            if (platesExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                            contentDescription = null,
                            tint = c.textSecondary,
                            modifier = Modifier.padding(start = 6.dp).size(18.dp),
                        )
                    }
                    AnimatedVisibility(platesExpanded) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                listOf(
                                    stringResource(R.string.live41_plates_bar, "${PlateMath.formatPlate(breakdown.barWeight)} $unitLabel"),
                                    stringResource(R.string.live41_plates_loaded, "${PlateMath.formatPlate(breakdown.loadedWeight)} $unitLabel"),
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall.tabular(),
                                color = c.textSecondary,
                            )
                            if (breakdown.perSide.isEmpty()) {
                                Text(stringResource(R.string.live41_plates_none_needed), style = MaterialTheme.typography.bodySmall, color = c.textPrimary)
                            } else {
                                breakdown.perSide.forEach { p ->
                                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            "${PlateMath.formatPlate(p.weight)} $unitLabel",
                                            style = MaterialTheme.typography.bodyMedium.tabular(),
                                            color = c.blue,
                                            modifier = Modifier.weight(1f),
                                        )
                                        Text(
                                            "× ${p.count}",
                                            style = MaterialTheme.typography.bodyMedium.tabular(),
                                            color = c.textPrimary,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                    }
                                }
                            }
                            if (breakdown.remainder >= 0.05f) {
                                Text(
                                    stringResource(R.string.live41_plates_remainder, "${PlateMath.formatPlate(breakdown.remainder)} $unitLabel"),
                                    style = MaterialTheme.typography.bodySmall.tabular(),
                                    color = c.gold,
                                )
                            }
                        }
                    }
                }
            }

            if (quickDeltas.isNotEmpty() || lastTimeValue != null) {
                Spacer(Modifier.height(12.dp))
                FlowRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    quickDeltas.forEach { delta ->
                        val chipText = "+${formatCompactNumber(delta, if (delta % 1f != 0f) 1 else 0)}"
                        KeypadChip(
                            text = chipText,
                            onClick = {
                                haptics.lightTick()
                                draft = draft.applyDelta(delta, initial, min, max, decimals)
                            },
                        )
                    }
                    if (lastTimeValue != null) {
                        val valueLabel = lastTimeLabel ?: formatCompactNumber(lastTimeValue, decimals)
                        KeypadChip(
                            text = stringResource(R.string.live41_keypad_last_time, valueLabel),
                            accent = true,
                            onClick = {
                                haptics.tick()
                                draft = draft.setExact(lastTimeValue, min, max, decimals)
                            },
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // 3x4 thumb-zone keypad grid (always LTR like a calculator / dial pad)
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        listOf("1", "2", "3"),
                        listOf("4", "5", "6"),
                        listOf("7", "8", "9"),
                    ).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { key ->
                                KeypadButton(
                                    label = key,
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        haptics.lightTick()
                                        draft = draft.pressDigit(key[0], decimals)
                                    },
                                )
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (decimals > 0) {
                            KeypadButton(
                                label = ".",
                                contentDescription = stringResource(R.string.live41_keypad_decimal),
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    haptics.lightTick()
                                    draft = draft.pressDecimal(decimals)
                                },
                            )
                        } else {
                            Spacer(Modifier.weight(1f).height(52.dp))
                        }
                        KeypadButton(
                            label = "0",
                            modifier = Modifier.weight(1f),
                            onClick = {
                                haptics.lightTick()
                                draft = draft.pressDigit('0', decimals)
                            },
                        )
                        val backspaceDesc = stringResource(R.string.live41_keypad_backspace)
                        Surface(
                            onClick = {
                                haptics.lightTick()
                                draft = draft.pressBackspace()
                            },
                            shape = ForgeShape.Md,
                            color = c.cardHigh,
                            contentColor = c.textPrimary,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 52.dp)
                                .semantics { contentDescription = backspaceDesc },
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.AutoMirrored.Rounded.Backspace, contentDescription = null, modifier = Modifier.size(22.dp))
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            PillButton(
                text = stringResource(R.string.live41_keypad_done),
                onClick = { if (valid && parsed != null) onConfirm(parsed) },
                enabled = valid,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun KeypadChip(
    text: String,
    accent: Boolean = false,
    onClick: () -> Unit,
) {
    val c = Fit.colors
    Surface(
        onClick = onClick,
        shape = ForgeShape.Full,
        color = if (accent) c.tealTrack else c.cardHigh,
        contentColor = if (accent) c.teal else c.textPrimary,
        modifier = Modifier.heightIn(min = 48.dp),
    ) {
        Box(
            Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(text, style = MaterialTheme.typography.labelLarge.tabular(), fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun KeypadButton(
    label: String,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    onClick: () -> Unit,
) {
    val c = Fit.colors
    Surface(
        onClick = onClick,
        shape = ForgeShape.Md,
        color = c.cardHigh,
        contentColor = c.textPrimary,
        modifier = modifier
            .heightIn(min = 52.dp)
            .let { m -> if (contentDescription != null) m.semantics { this.contentDescription = contentDescription } else m },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                label,
                style = MaterialTheme.typography.titleLarge.tabular(),
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            )
        }
    }
}
