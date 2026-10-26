package com.forgequest.app.ui.workouts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.forgequest.app.R
import com.forgequest.app.data.ActivePlan
import com.forgequest.app.data.PlanDayWithItems
import com.forgequest.app.data.db.ExerciseEntity
import com.forgequest.app.data.db.PlanItemEntity
import com.forgequest.app.domain.Dates
import com.forgequest.app.domain.MuscleExercises
import com.forgequest.app.ui.components.TonalPill
import com.forgequest.app.ui.theme.Fit

/**
 * "Put in my plan" from an exercise page: pick a plan day, then the exercise it replaces (the same movement first,
 * sets and reps carried over by the swap engine) or add it to that day.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PutInPlanSheet(
    ex: ExerciseEntity,
    plan: ActivePlan,
    library: Map<String, ExerciseEntity>,
    onReplace: (PlanDayWithItems, PlanItemEntity) -> Unit,
    onAdd: (PlanDayWithItems) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = Fit.colors
    var dayOfWeek by rememberSaveable { mutableStateOf<Int?>(null) }
    val day = dayOfWeek?.let { plan.day(it) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = c.card) {
        LazyColumn(Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding()) {
            if (day == null) {
                item {
                    Text(stringResource(R.string.plan_put_in_title, ex.name), style = MaterialTheme.typography.titleLarge, color = c.textPrimary, modifier = Modifier.semantics { heading() })
                    Text(stringResource(R.string.plan_put_in_pick_day), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                    Spacer(Modifier.height(8.dp))
                }
                items(plan.days.sortedBy { it.day.dayOfWeek }, key = { it.day.id }) { d ->
                    val workout = !d.day.isRest && d.items.isNotEmpty()
                    val subtitle = listOfNotNull(
                        if (workout) d.day.title else stringResource(R.string.today_rest_day),
                        if (d.items.any { it.exerciseId == ex.id }) stringResource(R.string.plan_put_in_already) else null,
                    ).joinToString(" · ")
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { dayOfWeek = d.day.dayOfWeek }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(Dates.dayName(d.day.dayOfWeek), style = MaterialTheme.typography.bodyLarge, color = c.textPrimary)
                            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                        }
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = c.textSecondary)
                    }
                }
            } else {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { dayOfWeek = null }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.cd_back), tint = c.textPrimary) }
                        Text(Dates.dayName(day.day.dayOfWeek), style = MaterialTheme.typography.titleLarge, color = c.textPrimary, modifier = Modifier.semantics { heading() })
                    }
                }
                // One row per plan item, in the order the swap engine ranks their exercises against this one.
                val rank = MuscleExercises.replaceOrder(ex, day.items.mapNotNull { library[it.exerciseId] })
                val rows = rank.flatMap { (e, close) -> day.items.filter { it.exerciseId == e.id }.map { Triple(it, e, close) } }
                if (rows.isNotEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.plan_put_in_replace), style = MaterialTheme.typography.titleSmall, color = c.textSecondary,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp).semantics { heading() },
                        )
                    }
                    items(rows, key = { it.first.id }) { (item, e, close) ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onReplace(day, item) }.padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(e.name, style = MaterialTheme.typography.bodyLarge, color = c.textPrimary)
                                Text(
                                    if (close) stringResource(R.string.plan_put_in_same_movement) else e.musclesLabel(),
                                    style = MaterialTheme.typography.bodySmall, color = if (close) c.teal else c.textSecondary,
                                )
                            }
                            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = c.textSecondary)
                        }
                    }
                }
                item {
                    Spacer(Modifier.height(12.dp))
                    TonalPill(stringResource(R.string.plan_put_in_add), onClick = { onAdd(day) }, modifier = Modifier.fillMaxWidth(), icon = Icons.Rounded.Add, tint = c.teal)
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}
