package com.forgequest.app.ui.workouts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.forgequest.app.data.db.ExerciseEntity
import com.forgequest.app.domain.Equipment
import com.forgequest.app.domain.Muscle
import com.forgequest.app.domain.MuscleExercise
import com.forgequest.app.domain.MuscleExercises
import com.forgequest.app.domain.MuscleRecovery
import com.forgequest.app.domain.Tier
import com.forgequest.app.ui.components.SegmentedTabs
import com.forgequest.app.ui.localLabel
import com.forgequest.app.ui.theme.Fit

/**
 * The exercises for one muscle (docs/plans/muscle-exercises-plan.md): a note while it is still recovering, the plan's
 * exercises first, then the rest for the person's equipment. A row opens the exercise page.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MuscleExercisesSheet(
    muscle: Muscle,
    library: List<ExerciseEntity>,
    planExerciseIds: Set<String>,
    equipment: Set<Equipment>,
    recovery: MuscleRecovery?,
    onOpen: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = Fit.colors
    var allEquipment by rememberSaveable { mutableStateOf(false) }
    val list = remember(muscle, library, planExerciseIds, equipment, allEquipment) {
        MuscleExercises.list(muscle, library, planExerciseIds, equipment, allEquipment)
    }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.card) {
        LazyColumn(Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding()) {
            item {
                Text(muscle.localLabel(), style = MaterialTheme.typography.titleLarge, color = c.textPrimary, modifier = Modifier.semantics { heading() })
                MuscleExercises.COMPANIONS[muscle]?.let { more ->
                    Text(
                        stringResource(R.string.muscle_shown_with, more.map { it.localLabel() }.joinToString(stringResource(R.string.list_separator))),
                        style = MaterialTheme.typography.bodySmall, color = c.textSecondary,
                    )
                }
                if (recovery != null && recovery.hoursUntilReady > 0) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        stringResource(R.string.muscle_recovering_note, recovery.hoursUntilReady),
                        style = MaterialTheme.typography.bodySmall, color = c.textPrimary,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.goldTrack).padding(12.dp),
                    )
                }
                if (list.hidden > 0 || allEquipment) {
                    Spacer(Modifier.height(12.dp))
                    SegmentedTabs(
                        listOf(stringResource(R.string.muscle_my_equipment), stringResource(R.string.muscle_all_equipment)),
                        if (allEquipment) 1 else 0,
                        onSelect = { allEquipment = it == 1 },
                    )
                }
            }
            if (list.inPlan.isNotEmpty()) {
                item { SectionTitle(stringResource(R.string.muscle_in_plan)) }
                items(list.inPlan, key = { "plan_" + it.exercise.id }) { ExerciseRow(it, onOpen) }
            }
            item { SectionTitle(stringResource(R.string.muscle_more)) }
            if (list.others.isEmpty()) {
                item { Text(stringResource(R.string.muscle_none), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary) }
            }
            items(list.others, key = { it.exercise.id }) { ExerciseRow(it, onOpen) }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text, style = MaterialTheme.typography.titleSmall, color = Fit.colors.textSecondary,
        modifier = Modifier.padding(top = 20.dp, bottom = 4.dp).semantics { heading() },
    )
}

@Composable
private fun ExerciseRow(row: MuscleExercise, onOpen: (String) -> Unit) {
    val c = Fit.colors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onOpen(row.exercise.id) }.padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(row.exercise.name, style = MaterialTheme.typography.bodyLarge, color = c.textPrimary)
            Text(
                stringResource(if (row.main) R.string.muscle_main else R.string.muscle_also),
                style = MaterialTheme.typography.bodySmall, color = c.textSecondary,
            )
        }
        row.tier?.let { TierBadge(it) }
        Spacer(Modifier.width(4.dp))
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = c.textSecondary)
    }
}

/** Foundation / Intermediate / Advanced as a small coloured pill. */
@Composable
fun TierBadge(tier: Tier) {
    val c = Fit.colors
    val (fg, bg, label) = when (tier) {
        Tier.FOUNDATION -> Triple(c.teal, c.tealTrack, R.string.tier_foundation)
        Tier.INTERMEDIATE -> Triple(c.gold, c.goldTrack, R.string.tier_intermediate)
        Tier.ADVANCED -> Triple(c.coral, c.coralTrack, R.string.tier_advanced)
    }
    Text(
        stringResource(label), style = MaterialTheme.typography.labelSmall, color = fg,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 10.dp, vertical = 4.dp),
    )
}
