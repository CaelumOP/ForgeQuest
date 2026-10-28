package com.forgequest.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.graphics.graphicsLayer
import com.forgequest.app.ui.theme.ForgeMotion
import com.forgequest.app.ui.theme.LocalReducedMotion
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.forgequest.app.R
import com.forgequest.app.container
import com.forgequest.app.ui.theme.Fit

/**
 * Swipe a row from its end edge toward its start (right to left in English, left to right in Arabic) to delete it.
 * The caller deletes and shows Undo; the row's own delete button stays as the non-gesture route, and screen readers get
 * the same delete as a custom action.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeToDelete(
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    content: @Composable () -> Unit,
) {
    val c = Fit.colors
    val haptics = LocalContext.current.container.haptics
    val latest by rememberUpdatedState(onDelete)
    val state = rememberSwipeToDismissBoxState(confirmValueChange = { target ->
        if (target == SwipeToDismissBoxValue.EndToStart) { latest(); true } else false
    })
    // A row that was swiped away and then restored by Undo must show settled: the saved state can come back as
    // "dismissed" (new composition) or the same row can stay composed; reset in both cases.
    val startedDismissed = remember { state.currentValue == SwipeToDismissBoxValue.EndToStart }
    LaunchedEffect(Unit) { if (startedDismissed) state.snapTo(SwipeToDismissBoxValue.Settled) }
    LaunchedEffect(state.currentValue) {
        if (state.currentValue == SwipeToDismissBoxValue.EndToStart) {
            kotlinx.coroutines.delay(600)
            state.snapTo(SwipeToDismissBoxValue.Settled)
        }
    }
    // Crossing the delete line: one tick and the bin pops 0.8 -> 1.25 -> 1; backing off shrinks it to 0.8 again.
    val reduced = LocalReducedMotion.current
    val binScale = remember { Animatable(0.8f) }
    LaunchedEffect(state.targetValue) {
        if (state.targetValue == SwipeToDismissBoxValue.EndToStart) {
            haptics.tick()
            if (reduced) binScale.snapTo(1f) else {
                binScale.animateTo(1.25f, ForgeMotion.Springs.FastSpatial.spec())
                binScale.animateTo(1f, ForgeMotion.Springs.FastSpatial.spec())
            }
        } else binScale.animateTo(0.8f, ForgeMotion.spatial(reduced, ForgeMotion.Springs.FastSpatial))
    }
    val label = stringResource(R.string.action_delete)
    SwipeToDismissBox(
        state = state,
        modifier = modifier.semantics { customActions = listOf(CustomAccessibilityAction(label) { latest(); true }) },
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            if (state.dismissDirection == SwipeToDismissBoxValue.EndToStart) {
                Box(Modifier.fillMaxSize().clip(shape).background(c.coral).padding(horizontal = 20.dp), contentAlignment = Alignment.CenterEnd) {
                    Icon(Icons.Rounded.Delete, contentDescription = null, tint = Color.White, modifier = Modifier.graphicsLayer { scaleX = binScale.value; scaleY = binScale.value })
                }
            }
        },
        content = { content() },
    )
}
