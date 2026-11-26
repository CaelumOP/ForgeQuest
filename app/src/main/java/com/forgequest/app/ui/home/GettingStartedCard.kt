package com.forgequest.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.forgequest.app.R
import com.forgequest.app.ui.components.FitCard
import com.forgequest.app.ui.components.ProgressLine
import com.forgequest.app.ui.theme.Fit
import com.forgequest.app.ui.theme.ForgeShape
import com.forgequest.app.ui.theme.ForgeSpace

data class GettingStartedInput(
    val hasWorkout: Boolean,
    val hasMeal: Boolean,
    val hasWeight: Boolean,
)

data class GettingStartedState(
    val workoutDone: Boolean,
    val foodDone: Boolean,
    val weightDone: Boolean,
) {
    val doneCount: Int
        get() = listOf(workoutDone, foodDone, weightDone).count { it }
    val total: Int get() = TOTAL_ITEMS
    val allDone: Boolean get() = doneCount == total

    companion object {
        const val TOTAL_ITEMS = 3
    }
}

fun computeGettingStarted(input: GettingStartedInput): GettingStartedState = GettingStartedState(
    workoutDone = input.hasWorkout,
    foodDone = input.hasMeal,
    weightDone = input.hasWeight,
)

fun isGettingStartedVisible(
    eligible: Boolean,
    dismissed: Boolean,
    allDone: Boolean,
    showingAllDoneVisit: Boolean = false,
): Boolean = eligible && !dismissed && (!allDone || showingAllDoneVisit)

@Composable
fun GettingStartedCard(
    state: GettingStartedState,
    onDismiss: () -> Unit,
    onWorkout: () -> Unit,
    onFood: () -> Unit,
    onWeight: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Fit.colors
    val doneLabel = stringResource(R.string.v42_gs_item_done)
    val todoLabel = stringResource(R.string.v42_gs_item_todo)

    FitCard(modifier = modifier, padding = PaddingValues(ForgeSpace.CardPaddingCompact)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.v42_gs_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = c.textPrimary,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    stringResource(R.string.v42_gs_progress, state.doneCount, state.total),
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textSecondary,
                )
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
            ) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.v42_gs_hide),
                    tint = c.textSecondary,
                )
            }
        }
        Spacer(Modifier.height(ForgeSpace.Xs))
        ProgressLine(
            progress = state.doneCount / state.total.toFloat(),
            color = c.teal,
            track = c.tealTrack,
            height = 5.dp,
        )
        Spacer(Modifier.height(ForgeSpace.Xs))
        if (state.allDone) {
            Text(
                stringResource(R.string.v42_gs_all_done),
                style = MaterialTheme.typography.bodyMedium,
                color = c.teal,
                modifier = Modifier.padding(vertical = ForgeSpace.Xs),
            )
        } else {
            val rows = listOf(
                Triple(R.string.v42_gs_workout, state.workoutDone, onWorkout),
                Triple(R.string.v42_gs_food, state.foodDone, onFood),
                Triple(R.string.v42_gs_weight, state.weightDone, onWeight),
            )
            rows.forEach { (labelRes, done, onClick) ->
                GettingStartedRow(
                    label = stringResource(labelRes),
                    done = done,
                    stateDesc = if (done) doneLabel else todoLabel,
                    onClick = onClick,
                )
            }
        }
    }
}

@Composable
private fun GettingStartedRow(
    label: String,
    done: Boolean,
    stateDesc: String,
    onClick: () -> Unit,
) {
    val c = Fit.colors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(ForgeShape.Sm)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { stateDescription = stateDesc }
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (done) {
            Box(
                Modifier.size(22.dp).clip(CircleShape).background(c.teal),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Check, contentDescription = null, tint = c.onTeal, modifier = Modifier.size(14.dp))
            }
        } else {
            Box(Modifier.size(22.dp).border(1.5.dp, c.divider, CircleShape))
        }
        Spacer(Modifier.width(ForgeSpace.Sm))
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (done) c.textSecondary else c.textPrimary,
            modifier = Modifier.weight(1f),
        )
        Icon(
            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = c.textSecondary,
            modifier = Modifier.size(18.dp),
        )
    }
}
