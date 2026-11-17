package com.forgequest.app.ui.components

import android.os.SystemClock
import androidx.annotation.PluralsRes
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.forgequest.app.R
import com.forgequest.app.container
import com.forgequest.app.ui.theme.Fit
import kotlin.math.roundToInt

/**
 * Live workout numeric stepper: keeps the ± steppers and opens the thumb-zone bottom keypad
 * ([NumberEntryKeypadSheet]) instead of a center dialog when the number is tapped.
 */
@Composable
fun KeypadNumberStepper(
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
    placeholder: String? = null,
    @PluralsRes unitPlurals: Int? = null,
    quickDeltas: List<Float> = emptyList(),
    lastTimeValue: Float? = null,
    lastTimeLabel: String? = null,
) {
    var keypadOpen by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val haptics = context.container.haptics
    var local by remember { mutableFloatStateOf(value) }
    var lastCommit by remember { mutableLongStateOf(0L) }
    LaunchedEffect(value) { if (SystemClock.uptimeMillis() - lastCommit > 800) local = value }

    fun pow10(n: Int): Float {
        var r = 1f
        repeat(n) { r *= 10f }
        return r
    }

    fun commit(v: Float) {
        val factor = pow10(decimals)
        val rounded = if (decimals <= 0) kotlin.math.round(v) else kotlin.math.round(v * factor) / factor
        val next = rounded.coerceIn(min, max)
        if (next == local) return
        local = next
        lastCommit = SystemClock.uptimeMillis()
        onTick?.invoke() ?: haptics.lightTick()
        onValueChange(next)
    }

    val shown = placeholder ?: (formatNumber(local, decimals) + if (unit.isNotEmpty()) " $unit" else "")
    val spoken = if (placeholder == null && unitPlurals != null) {
        val count = local.roundToInt()
        context.resources.getQuantityString(unitPlurals, count, count)
    } else {
        shown
    }

    Stepper(
        value = shown,
        onMinus = { commit(local - step) },
        onPlus = { commit(local + step) },
        modifier = modifier,
        valueColor = valueColor,
        minValueWidth = minValueWidth,
        onValueClick = { keypadOpen = true },
        valueSemantics = {
            progressBarRangeInfo = ProgressBarRangeInfo(
                local.coerceIn(min, max),
                min..max,
                (((max - min) / step).roundToInt() - 1).coerceAtLeast(0),
            )
            setProgress { target -> commit(min + ((target - min) / step).roundToInt() * step); true }
        },
        spokenValue = spoken,
    )

    if (keypadOpen) {
        NumberEntryKeypadSheet(
            title = title,
            initial = local,
            unit = unit,
            decimals = decimals,
            min = min,
            max = max,
            quickDeltas = quickDeltas,
            lastTimeValue = lastTimeValue,
            lastTimeLabel = lastTimeLabel,
            onDismiss = { keypadOpen = false },
            onConfirm = {
                keypadOpen = false
                commit(it)
            },
        )
    }
}
