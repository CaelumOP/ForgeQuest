package com.forgequest.app.ui.workouts

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.forgequest.app.R
import com.forgequest.app.container
import com.forgequest.app.data.db.ExerciseEntity
import com.forgequest.app.domain.Equipment
import com.forgequest.app.domain.ExerciseKind
import com.forgequest.app.domain.ExerciseSwapEngine
import com.forgequest.app.domain.MovementPattern
import com.forgequest.app.domain.Muscle
import com.forgequest.app.ui.components.ChoiceChip
import com.forgequest.app.ui.components.PillButton
import com.forgequest.app.ui.localLabel
import com.forgequest.app.ui.theme.Fit
import java.util.Locale

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

fun Equipment.localName(): String =
    if (Locale.getDefault().language == "ar") labelAr else labelEn

fun MovementPattern.localName(): String =
    if (Locale.getDefault().language == "ar") labelAr else labelEn

/**
 * Searchable exercise library with biomechanical swap ranking, equipment filtering, and "create custom" at the bottom.
 * When [sourceExercise] is provided (Swap mode), the top biomechanical equivalents matching the user's available
 * equipment are surfaced first.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExercisePickerSheet(
    title: String,
    library: List<ExerciseEntity>,
    onPick: (ExerciseEntity) -> Unit,
    onCreate: (String, ExerciseKind, Muscle) -> Unit,
    onDismiss: () -> Unit,
    initialMuscle: Muscle? = null,
    sourceExercise: ExerciseEntity? = null,
    userEquipment: Set<Equipment> = emptySet(),
) {
    val c = Fit.colors
    val context = LocalContext.current
    val isArabic = Locale.getDefault().language == "ar"
    val configuredEq = remember(userEquipment) {
        userEquipment.ifEmpty {
            runCatching { context.container.settings }.getOrNull()?.let { _ -> emptySet() } ?: emptySet()
        }
    }
    var query by remember { mutableStateOf("") }
    var muscle by remember { mutableStateOf(if (sourceExercise != null) null else initialMuscle) }
    var onlyMyEquipment by remember { mutableStateOf(configuredEq.isNotEmpty() && configuredEq.size < Equipment.entries.size) }
    var selectedEquipment by remember { mutableStateOf<Equipment?>(null) }
    var creating by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val effectiveEquipment = remember(configuredEq, onlyMyEquipment, selectedEquipment) {
        when {
            selectedEquipment != null -> setOf(selectedEquipment!!)
            onlyMyEquipment && configuredEq.isNotEmpty() -> configuredEq + Equipment.BODYWEIGHT
            else -> Equipment.entries.toSet()
        }
    }

    val topSwaps = remember(sourceExercise, library, effectiveEquipment, query, muscle) {
        if (sourceExercise == null || query.isNotBlank() || muscle != null) {
            emptyList()
        } else {
            ExerciseSwapEngine.rankSubstitutes(sourceExercise, library, effectiveEquipment)
                .filter { it.matchesUserEquipment && it.score >= 40 }
                .take(5)
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = c.card) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.9f).padding(horizontal = 20.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = c.textPrimary)
            if (sourceExercise != null) {
                val srcProf = remember(sourceExercise) { ExerciseSwapEngine.profileFor(sourceExercise) }
                Text(
                    "${sourceExercise.name} · ${srcProf.pattern.localName()} · ${srcProf.equipment.localName()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.teal,
                )
            }
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
            // Equipment filter row
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (configuredEq.isNotEmpty() && configuredEq.size < Equipment.entries.size) {
                    ChoiceChip(
                        text = stringResource(R.string.picker_my_equipment),
                        selected = onlyMyEquipment && selectedEquipment == null,
                        onClick = { onlyMyEquipment = !onlyMyEquipment; selectedEquipment = null },
                    )
                }
                Equipment.entries.forEach { eq ->
                    ChoiceChip(
                        text = eq.localName(),
                        selected = selectedEquipment == eq,
                        onClick = {
                            selectedEquipment = if (selectedEquipment == eq) null else eq
                            if (selectedEquipment != null) onlyMyEquipment = false
                        },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            // Muscle filter row
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceChip(stringResource(R.string.picker_all), muscle == null, { muscle = null })
                Muscle.entries.forEach { m -> ChoiceChip(m.localLabel(), muscle == m, { muscle = if (muscle == m) null else m }) }
            }
            Spacer(Modifier.height(8.dp))
            if (creating) {
                CreateExerciseForm(
                    initialName = query,
                    initialMuscle = muscle ?: sourceExercise?.primary?.firstOrNull(),
                    onCreate = { n, k, m -> onCreate(n, k, m) },
                    onCancel = { creating = false },
                )
            } else {
                val topSwapIds = remember(topSwaps) { topSwaps.map { it.exercise.id }.toSet() }
                val filtered = remember(library, query, muscle, effectiveEquipment, sourceExercise, topSwapIds) {
                    library.filter { ex ->
                        val prof = ExerciseSwapEngine.profileFor(ex)
                        val eqOk = prof.equipment in effectiveEquipment
                        val queryOk = query.isBlank() || ex.name.contains(query.trim(), ignoreCase = true)
                        val muscleOk = muscle == null || muscle in ex.primary || muscle in ex.secondary
                        eqOk && queryOk && muscleOk && ex.id != sourceExercise?.id && ex.id !in topSwapIds
                    }.sortedWith(
                        compareBy<ExerciseEntity>(
                            { muscle != null && muscle !in it.primary },
                            { sourceExercise != null && sourceExercise.primary.firstOrNull() !in it.primary },
                            { it.name },
                        )
                    )
                }
                LazyColumn(Modifier.weight(1f)) {
                    if (topSwaps.isNotEmpty()) {
                        item(key = "header_swaps") {
                            Text(
                                text = stringResource(R.string.picker_best_swaps),
                                style = MaterialTheme.typography.labelLarge,
                                color = c.teal,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            )
                        }
                        items(topSwaps, key = { "swap_${it.exercise.id}" }) { cand ->
                            val ex = cand.exercise
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(c.tealTrack.copy(alpha = 0.45f))
                                    .clickable { onPick(ex) }
                                    .padding(horizontal = 12.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(ex.name, style = MaterialTheme.typography.bodyLarge, color = c.textPrimary, fontWeight = FontWeight.Medium)
                                    Text(
                                        "${cand.profile.pattern.localName()} · ${cand.profile.equipment.localName()} · ${ex.musclesLabel()}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = c.textSecondary,
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                Box(
                                    Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(c.tealTrack)
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(ex.kind.label(), style = MaterialTheme.typography.labelSmall, color = c.teal)
                                }
                            }
                        }
                        item(key = "header_all") {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.picker_all),
                                style = MaterialTheme.typography.labelLarge,
                                color = c.textSecondary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    }
                    items(filtered, key = { it.id }) { ex ->
                        val prof = remember(ex.id) { ExerciseSwapEngine.profileFor(ex) }
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { onPick(ex) }.padding(horizontal = 8.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(ex.name, style = MaterialTheme.typography.bodyLarge, color = c.textPrimary)
                                Text(
                                    "${ex.kind.label()} · ${prof.equipment.localName()} · ${ex.musclesLabel()}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = c.textSecondary,
                                )
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