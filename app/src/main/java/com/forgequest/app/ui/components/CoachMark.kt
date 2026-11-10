package com.forgequest.app.ui.components

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.RichTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.forgequest.app.R
import com.forgequest.app.ui.onboarding.TourPrefs
import com.forgequest.app.ui.theme.Fit
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * First-run hints that point at the real button, once each, after the screen has content (replaces the swipe tour).
 * Existing installs already have `tour_seen`, so they never see them. At most one shows at a time.
 */
object CoachMarks {
    private const val PREFS = "ui_prefs"
    var active: String? by mutableStateOf(null)

    fun seen(c: Context, key: String) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(key, false)
    fun markSeen(c: Context, key: String) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(key, true).apply()
    fun shouldShow(c: Context, key: String) = !TourPrefs.tourSeen(c) && !seen(c, key)
}

/** Wraps [content] and shows a small persistent tip above it the first time; one tap on "Got it" or outside closes it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoachMarkBox(key: String, title: Int, body: Int, enabled: Boolean = true, below: Boolean = false, content: @Composable () -> Unit) {
    val context = LocalContext.current
    // Decided once per composition: marking it seen later must not tear the tip down on the next recompose.
    val show = remember(key, enabled) { enabled && CoachMarks.shouldShow(context, key) }
    if (!show) {
        content()
        return
    }
    val c = Fit.colors
    val state = rememberTooltipState(isPersistent = true)
    val scope = rememberCoroutineScope()
    var owner by remember { mutableStateOf(false) }
    LaunchedEffect(key) {
        // Only one tip at a time app-wide. A screen that composes while another tip is still up (a crossfade, or a
        // second set row) waits for it to go, then re-checks: the first row marks it seen, so the others stay quiet.
        snapshotFlow { CoachMarks.active }.first { it == null }
        if (!CoachMarks.shouldShow(context, key)) return@LaunchedEffect
        CoachMarks.active = key
        owner = true
        delay(700) // let the screen and its transition settle so the tip lands on the button
        CoachMarks.markSeen(context, key)
        try {
            state.show()
        } finally {
            // Dismissed: let the next tip (another screen's) take its turn without waiting for this one to leave.
            if (CoachMarks.active == key) CoachMarks.active = null
        }
    }
    DisposableEffect(key) { onDispose { if (owner && CoachMarks.active == key) CoachMarks.active = null } }
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(if (below) TooltipAnchorPosition.Below else TooltipAnchorPosition.Above),
        tooltip = {
            RichTooltip(
                title = { Text(stringResource(title), color = c.textPrimary) },
                action = { TextButton(onClick = { scope.launch { state.dismiss() } }) { Text(stringResource(R.string.coach_got_it), color = c.teal) } },
                colors = TooltipDefaults.richTooltipColors(containerColor = c.card, contentColor = c.textSecondary, titleContentColor = c.textPrimary, actionContentColor = c.teal),
            ) { Text(stringResource(body)) }
        },
        state = state,
        focusable = false,
        enableUserInput = false,
    ) {
        Box { content() }
    }
}
