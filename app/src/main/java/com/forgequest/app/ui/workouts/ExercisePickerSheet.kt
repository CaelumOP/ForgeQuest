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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.forgequest.app.R
import com.forgequest.app.data.db.ExerciseEntity
import com.forgequest.app.data.prefs.BodyType
import com.forgequest.app.data.seed.ExerciseSearch
import com.forgequest.app.domain.Equipment
import com.forgequest.app.domain.ExerciseKind
import com.forgequest.app.domain.ExerciseSwapEngine
import com.forgequest.app.domain.ExerciseTiers
import com.forgequest.app.domain.MovementPattern
import com.forgequest.app.domain.Muscle
import com.forgequest.app.domain.SwapDifficulty
import com.forgequest.app.domain.expandLegacyEquipment
import com.forgequest.app.ui.components.ChoiceChip
import com.forgequest.app.ui.components.ExerciseThumbnail
import com.forgequest.app.ui.components.MuscleFilterSheet
import com.forgequest.app.ui.components.PillButton
import com.forgequest.app.ui.components.rememberFullSheetState
import com.forgequest.app.ui.localLabel
import com.forgequest.app.ui.theme.Fit
import com.forgequest.app.ui.theme.ForgeShape
import com.forgequest.app.ui.theme.ForgeSpace
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
fun ExerciseEntity.primaryMusclesLabel(): String =
    primary.ifEmpty { secondary }.distinct().take(2).map { it.localLabel() }.joinToString(stringResource(R.string.list_separator))

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
 * Searchable exercise library with biomechanical swap ranking, equipment filtering, body-map muscle filter,
 * pinned/recent sections, and "create custom" at the bottom.
 * When [sourceExercise] is provided (Swap mode), the top biomechanical equivalents matching the user's available
 * equipment are surfaced first, ranked against [sourceExercise] (the plan anchor).
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
    /** False hides "create custom", for pickers that only choose from the library. */
    allowCreate: Boolean = true,
    /** Currently active exercise in this live slot (marked "Current" in Swap mode). */
    currentExerciseId: String? = null,
    /** Recently used exercise IDs shown at the top of the picker when unfiltered. */
    recentIds: List<String> = emptyList(),
    bodyType: BodyType = BodyType.MALE,
    showAllMovesToggle: Boolean = false,
) {
    val c = Fit.colors
    // "My equipment" is only offered when the person owns some, but not all, of it.
    val expandedUserEquipment = remember(userEquipment) { expandLegacyEquipment(userEquipment) }
    val partialEquipment = expandedUserEquipment.isNotEmpty() && !expandedUserEquipment.containsAll(Equipment.allActive)
    var query by remember { mutableStateOf("") }
    var muscle by remember { mutableStateOf(if (sourceExercise != null) null else initialMuscle) }
    var onlyMyEquipment by remember { mutableStateOf(showAllMovesToggle || partialEquipment) }
    var selectedEquipment by remember { mutableStateOf<Equipment?>(null) }
    var creating by remember { mutableStateOf(false) }
    var mapFilterOpen by remember { mutableStateOf(false) }
    val sheetState = rememberFullSheetState()

    val effectiveEquipment = remember(expandedUserEquipment, onlyMyEquipment, selectedEquipment) {
        val picked = selectedEquipment
        when {
            picked != null -> setOf(picked)
            onlyMyEquipment -> expandedUserEquipment + Equipment.BODYWEIGHT
            else -> Equipment.allActive
        }
    }

    val topSwaps = remember(sourceExercise, library, effectiveEquipment, query, muscle) {
        if (sourceExercise == null || query.isNotBlank() || muscle != null) {
            emptyList()
        } else {
            ExerciseSwapEngine.rankSwapSheet(sourceExercise, library, effectiveEquipment)
                .filter { it.matchesUserEquipment && it.score >= 45 }
                .take(8)
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = c.card) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.9f).padding(horizontal = ForgeSpace.Gutter)) {
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
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(stringResource(R.string.picker_search)) },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    singleLine = true,
                    shape = ForgeShape.Full,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = c.teal, unfocusedBorderColor = c.divider,
                        focusedTextColor = c.textPrimary, unfocusedTextColor = c.textPrimary, cursorColor = c.teal,
                        unfocusedLeadingIconColor = c.textSecondary, focusedLeadingIconColor = c.teal,
                    ),
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = { mapFilterOpen = true },
                    modifier = Modifier.size(48.dp).clip(ForgeShape.Full).background(if (muscle != null) c.blueTrack else c.cardHigh),
                ) {
                    Icon(
                        Icons.Rounded.AccessibilityNew,
                        contentDescription = stringResource(R.string.picker_map_filter),
                        tint = if (muscle != null) c.blue else c.teal,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            // Equipment filter row
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (partialEquipment) {
                    ChoiceChip(
                        text = stringResource(if (showAllMovesToggle) R.string.baseline_show_all_moves else R.string.picker_my_equipment),
                        selected = (if (showAllMovesToggle) !onlyMyEquipment else onlyMyEquipment) && selectedEquipment == null,
                        onClick = { onlyMyEquipment = !onlyMyEquipment; selectedEquipment = null },
                    )
                }
                (listOf(Equipment.BODYWEIGHT) + Equipment.selectableEntries).forEach { eq ->
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
                    onCreate = onCreate,
                    onCancel = { creating = false },
                )
            } else {
                val showQuickSections = sourceExercise == null && query.isBlank() && muscle == null && selectedEquipment == null
                val pinnedList = remember(library, effectiveEquipment, showQuickSections) {
                    if (!showQuickSections) emptyList()
                    else library.filter { it.pinned && ExerciseSwapEngine.profileFor(it).fits(effectiveEquipment) }
                        .sortedBy { it.name }
                }
                val recentList = remember(library, recentIds, effectiveEquipment, showQuickSections, pinnedList) {
                    if (!showQuickSections || recentIds.isEmpty()) emptyList()
                    else {
                        val byId = library.associateBy { it.id }
                        val pinnedIds = pinnedList.mapTo(HashSet()) { it.id }
                        recentIds.distinct()
                            .mapNotNull { byId[it] }
                            .filter { it.id !in pinnedIds && ExerciseSwapEngine.profileFor(it).fits(effectiveEquipment) }
                            .take(6)
                    }
                }
                val filtered = remember(library, query, muscle, effectiveEquipment, selectedEquipment, sourceExercise, topSwaps) {
                    val topSwapIds = topSwaps.map { it.exercise.id }.toSet()
                    val searched = if (query.isBlank()) library else ExerciseSearch.filter(library, query)
                    val matching = searched.filter { ex ->
                        val prof = ExerciseSwapEngine.profileFor(ex)
                        val eqOk = selectedEquipment?.let { picked -> picked == prof.equipment || picked in prof.requires }
                            ?: prof.fits(effectiveEquipment)
                        val muscleOk = muscle == null || muscle in ex.primary || muscle in ex.secondary
                        eqOk && muscleOk && ex.id != sourceExercise?.id && ex.id !in topSwapIds
                    }
                    if (query.isNotBlank()) matching
                    else matching.sortedWith(
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
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                            )
                        }
                        items(topSwaps, key = { "swap_${it.exercise.id}" }) { cand ->
                            PickerExerciseRow(
                                ex = cand.exercise,
                                prof = cand.profile,
                                swapDifficulty = cand.difficulty,
                                isCurrent = cand.exercise.id == currentExerciseId,
                                highlighted = true,
                                onClick = { onPick(cand.exercise) },
                            )
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
                    if (pinnedList.isNotEmpty()) {
                        item(key = "header_pinned") {
                            Text(
                                text = stringResource(R.string.picker_pinned),
                                style = MaterialTheme.typography.labelLarge,
                                color = c.textSecondary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            )
                        }
                        items(pinnedList, key = { "pin_${it.id}" }) { ex ->
                            val prof = remember(ex.id, ex.equipment, ex.pattern) { ExerciseSwapEngine.profileFor(ex) }
                            PickerExerciseRow(ex = ex, prof = prof, onClick = { onPick(ex) })
                        }
                    }
                    if (recentList.isNotEmpty()) {
                        item(key = "header_recent") {
                            Text(
                                text = stringResource(R.string.picker_recent),
                                style = MaterialTheme.typography.labelLarge,
                                color = c.textSecondary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            )
                        }
                        items(recentList, key = { "rec_${it.id}" }) { ex ->
                            val prof = remember(ex.id, ex.equipment, ex.pattern) { ExerciseSwapEngine.profileFor(ex) }
                            PickerExerciseRow(ex = ex, prof = prof, onClick = { onPick(ex) })
                        }
                    }
                    if (pinnedList.isNotEmpty() || recentList.isNotEmpty()) {
                        item(key = "header_all_lib") {
                            Text(
                                text = stringResource(R.string.picker_all),
                                style = MaterialTheme.typography.labelLarge,
                                color = c.textSecondary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            )
                        }
                    }
                    items(filtered, key = { it.id }) { ex ->
                        val prof = remember(ex.id, ex.equipment, ex.pattern) { ExerciseSwapEngine.profileFor(ex) }
                        val diff = remember(sourceExercise?.id, ex.id) {
                            sourceExercise?.let { ExerciseSwapEngine.compareTier(it, ex) }
                        }
                        PickerExerciseRow(
                            ex = ex,
                            prof = prof,
                            swapDifficulty = diff,
                            isCurrent = sourceExercise != null && ex.id == currentExerciseId,
                            onClick = { onPick(ex) },
                        )
                    }
                    if (filtered.isEmpty() && topSwaps.isEmpty()) item {
                        Text(
                            stringResource(R.string.lib_search_none),
                            style = MaterialTheme.typography.bodyMedium, color = c.textSecondary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
                        )
                    }
                    if (allowCreate) item {
                        Row(
                            Modifier.fillMaxWidth().clip(ForgeShape.Md).clickable(role = Role.Button) { creating = true }.padding(horizontal = 8.dp, vertical = 16.dp),
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

    if (mapFilterOpen) {
        MuscleFilterSheet(
            selected = muscle,
            bodyType = bodyType,
            onSelect = { muscle = it },
            onDismiss = { mapFilterOpen = false },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PickerExerciseRow(
    ex: ExerciseEntity,
    prof: com.forgequest.app.domain.BiomechanicalProfile,
    swapDifficulty: SwapDifficulty? = null,
    isCurrent: Boolean = false,
    highlighted: Boolean = false,
    onClick: () -> Unit,
) {
    val c = Fit.colors
    val tier = remember(ex.id, ex.custom) { ExerciseTiers.of(ex) }
    val bgModifier = if (highlighted) Modifier.padding(vertical = 3.dp).clip(ForgeShape.Md).background(c.cardHigh) else Modifier.clip(ForgeShape.Md)

    Row(
        bgModifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ExerciseThumbnail(ex)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    ex.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = c.textPrimary,
                    fontWeight = if (highlighted) FontWeight.Medium else FontWeight.Normal,
                )
                if (ex.custom) {
                    SmallDataPill(stringResource(R.string.lib_custom), c.textSecondary, c.cardHigh)
                } else if (tier != null && swapDifficulty == null) {
                    TierBadge(tier)
                }
                if (swapDifficulty != null) {
                    SwapDifficultyPill(swapDifficulty)
                }
                if (isCurrent) {
                    SmallDataPill(stringResource(R.string.swap_current), c.blue, c.blueTrack)
                }
            }
            val holdTag = if (ex.kind == ExerciseKind.HOLD) " · ${ex.kind.label()}" else ""
            Text(
                "${prof.equipment.localName()} · ${ex.primaryMusclesLabel()}$holdTag",
                style = MaterialTheme.typography.bodySmall,
                color = c.textSecondary,
            )
        }
    }
}

@Composable
internal fun SwapDifficultyPill(difficulty: SwapDifficulty) {
    val c = Fit.colors
    val (label, fg, bg) = when (difficulty) {
        SwapDifficulty.EASIER -> Triple(R.string.swap_easier, c.blue, c.blueTrack)
        SwapDifficulty.SAME -> Triple(R.string.swap_same_level, c.purple, c.purpleTrack)
        SwapDifficulty.HARDER -> Triple(R.string.swap_harder, c.gold, c.goldTrack)
    }
    SmallDataPill(stringResource(label), fg, bg)
}

@Composable
internal fun SmallDataPill(text: String, fg: androidx.compose.ui.graphics.Color, bg: androidx.compose.ui.graphics.Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = fg,
        modifier = Modifier
            .clip(ForgeShape.Full)
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

@Composable
internal fun CreateExerciseForm(initialName: String, initialMuscle: Muscle?, onCreate: (String, ExerciseKind, Muscle) -> Unit, onCancel: () -> Unit) {
    val c = Fit.colors
    var name by remember { mutableStateOf(initialName.trim()) }
    var kind by remember { mutableStateOf(ExerciseKind.REPS) }
    var muscle by remember { mutableStateOf(initialMuscle ?: Muscle.CHEST) }
    Column(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = name, onValueChange = { name = it.take(40) }, label = { Text(stringResource(R.string.picker_exercise_name)) }, singleLine = true,
            shape = ForgeShape.Md, modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = c.teal, unfocusedBorderColor = c.divider, focusedTextColor = c.textPrimary, unfocusedTextColor = c.textPrimary),
        )
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.picker_measured_in), style = MaterialTheme.typography.labelLarge, color = c.textSecondary)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ExerciseKind.entries.forEach { k -> ChoiceChip(k.label(), kind == k, { kind = k }) }
        }
        Spacer(Modifier.height(12.dp))
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
