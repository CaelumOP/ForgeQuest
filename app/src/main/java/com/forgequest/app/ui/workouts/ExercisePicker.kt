package com.forgequest.app.ui.workouts

import com.forgequest.app.R
import com.forgequest.app.ui.localLabel

import androidx.compose.ui.res.stringResource

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.forgequest.app.data.db.ExerciseEntity
import com.forgequest.app.domain.ExerciseKind
import com.forgequest.app.domain.Muscle
import com.forgequest.app.ui.components.ChoiceChip
import com.forgequest.app.ui.components.PillButton
import com.forgequest.app.ui.theme.Fit

/** Opens the exercise's own tutorial link, or a YouTube search for its form. */
fun openFormVideo(context: Context, exercise: ExerciseEntity?) {
    val url = exercise?.videoUrl?.takeIf { it.isNotBlank() }
        ?: "https://www.youtube.com/results?search_query=" + Uri.encode("${exercise?.name ?: "exercise"} proper form")
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

@Composable
fun ExerciseEntity.musclesLabel(): String =
    (primary + secondary).distinct().take(3).map { it.localLabel() }.joinToString(stringResource(R.string.list_separator))

@Composable
fun ExerciseKind.label(): String = stringResource(
    when (this) {
        ExerciseKind.REPS -> R.string.stepper_reps
        ExerciseKind.HOLD -> R.string.kind_hold
        ExerciseKind.WEIGHTED -> R.string.kind_weighted
    }
)

/**
 * Searchable exercise library with "create custom" at the bottom.
 * [onCreate] receives (name, kind, muscle) and should return the new id.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExercisePickerSheet(
    title: String,
    library: List<ExerciseEntity>,
    onPick: (ExerciseEntity) -> Unit,
    onCreate: (String, ExerciseKind, Muscle) -> Unit,
    onDismiss: () -> Unit,
    /** Swapping starts filtered to the same muscle, so similar exercises are on top (as in Hevy and Fitbod). */
    initialMuscle: Muscle? = null,
) {
    val c = Fit.colors
    var query by remember { mutableStateOf("") }
    var muscle by remember { mutableStateOf(initialMuscle) }
    var creating by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = c.card) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.9f).padding(horizontal = 20.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = c.textPrimary)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.picker_search)) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(50),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = c.teal, unfocusedBorderColor = c.divider,
                    focusedTextColor = c.textPrimary, unfocusedTextColor = c.textPrimary, cursorColor = c.teal,
                    unfocusedLeadingIconColor = c.textSecondary, focusedLeadingIconColor = c.teal,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceChip(stringResource(R.string.picker_all), muscle == null, { muscle = null })
                Muscle.entries.forEach { m -> ChoiceChip(m.localLabel(), muscle == m, { muscle = if (muscle == m) null else m }) }
            }
            Spacer(Modifier.height(8.dp))
            if (creating) {
                CreateExerciseForm(initialName = query, initialMuscle = muscle, onCreate = { n, k, m -> onCreate(n, k, m) }, onCancel = { creating = false })
            } else {
                val filtered = library.filter { ex ->
                    (query.isBlank() || ex.name.contains(query.trim(), ignoreCase = true)) &&
                        (muscle == null || muscle in ex.primary || muscle in ex.secondary)
                }.sortedWith(compareBy({ muscle != null && muscle !in it.primary }, { it.name }))
                LazyColumn(Modifier.weight(1f)) {
                    items(filtered, key = { it.id }) { ex ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { onPick(ex) }.padding(horizontal = 8.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(ex.name, style = MaterialTheme.typography.bodyLarge, color = c.textPrimary)
                                Text("${ex.kind.label()} · ${ex.musclesLabel()}", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                            }
                        }
                    }
                    item {
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { creating = true }.padding(horizontal = 8.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = null, tint = c.teal)
                            Text(
                                if (query.isBlank()) stringResource(R.string.picker_create_custom) else stringResource(R.string.picker_create_named, query.trim()),
                                style = MaterialTheme.typography.bodyLarge, color = c.teal, modifier = Modifier.padding(start = 12.dp),
                            )
                        }
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun CreateExerciseForm(initialName: String, initialMuscle: Muscle?, onCreate: (String, ExerciseKind, Muscle) -> Unit, onCancel: () -> Unit) {
    val c = Fit.colors
    var name by remember { mutableStateOf(initialName.trim()) }
    var kind by remember { mutableStateOf(ExerciseKind.REPS) }
    var muscle by remember { mutableStateOf(initialMuscle ?: Muscle.CHEST) }
    Column(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = name, onValueChange = { name = it.take(40) }, label = { Text(stringResource(R.string.picker_exercise_name)) }, singleLine = true,
            shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = c.teal, unfocusedBorderColor = c.divider, focusedTextColor = c.textPrimary, unfocusedTextColor = c.textPrimary),
        )
        Spacer(Modifier.height(14.dp))
        Text(stringResource(R.string.picker_measured_in), style = MaterialTheme.typography.labelLarge, color = c.textSecondary)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ExerciseKind.entries.forEach { k -> ChoiceChip(k.label(), kind == k, { kind = k }) }
        }
        Spacer(Modifier.height(14.dp))
        Text(stringResource(R.string.picker_main_muscle), style = MaterialTheme.typography.labelLarge, color = c.textSecondary)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Muscle.entries.forEach { m -> ChoiceChip(m.localLabel(), muscle == m, { muscle = m }) }
        }
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PillButton(stringResource(R.string.action_cancel), onClick = onCancel, container = c.cardHigh, content = c.textPrimary)
            PillButton(stringResource(R.string.action_create), onClick = { onCreate(name.trim(), kind, muscle) }, enabled = name.isNotBlank())
        }
    }
}
