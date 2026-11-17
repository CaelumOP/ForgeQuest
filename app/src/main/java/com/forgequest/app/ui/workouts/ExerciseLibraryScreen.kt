package com.forgequest.app.ui.workouts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.forgequest.app.AppContainer
import com.forgequest.app.R
import com.forgequest.app.data.WorkoutRepository
import com.forgequest.app.data.db.ExerciseEntity
import com.forgequest.app.data.prefs.BodyType
import com.forgequest.app.data.seed.ExerciseSearch
import com.forgequest.app.domain.Equipment
import com.forgequest.app.domain.ExerciseKind
import com.forgequest.app.domain.ExerciseSwapEngine
import com.forgequest.app.domain.ExerciseTiers
import com.forgequest.app.domain.MovementPattern
import com.forgequest.app.domain.Muscle
import com.forgequest.app.ui.LocalSnackbar
import com.forgequest.app.ui.Routes
import com.forgequest.app.ui.appViewModel
import com.forgequest.app.ui.components.ChoiceChip
import com.forgequest.app.ui.components.ExerciseThumbnail
import com.forgequest.app.ui.components.MuscleFilterSheet
import com.forgequest.app.ui.components.PillButton
import com.forgequest.app.ui.components.TonalPill
import com.forgequest.app.ui.components.rememberFullSheetState
import com.forgequest.app.ui.localLabel
import com.forgequest.app.ui.theme.Fit
import com.forgequest.app.ui.theme.ForgeShape
import com.forgequest.app.ui.theme.ForgeSpace
import com.forgequest.app.ui.theme.itemMotion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ExerciseLibraryViewModel(val app: AppContainer) : ViewModel() {
    val library: StateFlow<List<ExerciseEntity>> = app.workouts.library.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val bodyType: StateFlow<BodyType> = app.settings.settings.map { it.bodyType }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BodyType.MALE)
    val message = MutableStateFlow<String?>(null)
    val usage = MutableStateFlow<WorkoutRepository.ExerciseUsage?>(null)

    fun loadUsage(id: String) = viewModelScope.launch { usage.value = app.workouts.usage(id) }

    fun create(
        name: String,
        kind: ExerciseKind,
        muscle: Muscle,
        secondary: List<Muscle> = emptyList(),
        equipment: String = "",
        pattern: String = "",
        unitOverride: String = "",
    ) = viewModelScope.launch {
        val cleanName = name.trim()
        val id = app.workouts.addCustomExercise(cleanName, kind, listOf(muscle), null)
        val cleanSec = secondary.distinct().filter { it != muscle }
        if (cleanSec.isNotEmpty() || equipment.isNotBlank() || pattern.isNotBlank() || unitOverride.isNotBlank()) {
            app.workouts.updateExercise(
                ExerciseEntity(
                    id = id,
                    name = cleanName,
                    kind = kind,
                    primary = listOf(muscle),
                    secondary = cleanSec,
                    custom = true,
                    equipment = equipment,
                    pattern = pattern,
                    unitOverride = unitOverride,
                )
            )
        }
        message.value = app.str(R.string.lib_saved, cleanName)
    }

    fun save(ex: ExerciseEntity) = viewModelScope.launch { app.workouts.updateExercise(ex); message.value = app.str(R.string.lib_saved, ex.name) }

    fun merge(from: ExerciseEntity, into: ExerciseEntity) = viewModelScope.launch {
        app.workouts.mergeExercise(from.id, into.id)
        message.value = app.str(R.string.lib_merged, from.name, into.name)
    }

    fun delete(ex: ExerciseEntity) = viewModelScope.launch {
        message.value = if (app.workouts.deleteExercise(ex.id)) app.str(R.string.lib_deleted, ex.name) else app.str(R.string.lib_still_used, ex.name)
    }
}

/** Every exercise the app knows. Custom ones (yours or the coach's) can be renamed, merged or deleted. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseLibraryScreen(nav: NavHostController) {
    val vm = appViewModel { ExerciseLibraryViewModel(it) }
    val library by vm.library.collectAsState()
    val bodyType by vm.bodyType.collectAsState()
    val message by vm.message.collectAsState()
    val usage by vm.usage.collectAsState()
    val snackbar = LocalSnackbar.current
    val c = Fit.colors
    var query by remember { mutableStateOf("") }
    var muscle by remember { mutableStateOf<Muscle?>(null) }
    var selectedEquipment by remember { mutableStateOf<Equipment?>(null) }
    var mapFilterOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ExerciseEntity?>(null) }
    var creating by remember { mutableStateOf(false) }
    var merging by remember { mutableStateOf<ExerciseEntity?>(null) }
    var confirmDelete by remember { mutableStateOf<ExerciseEntity?>(null) }

    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); vm.message.value = null } }
    LaunchedEffect(editing?.id) { editing?.let { vm.loadUsage(it.id) } }

    val filtered = remember(library, query, muscle, selectedEquipment) {
        val searched = if (query.isBlank()) library else ExerciseSearch.filter(library, query)
        searched.filter { ex ->
            val muscleOk = muscle == null || muscle in ex.primary || muscle in ex.secondary
            val eqOk = selectedEquipment?.let { picked ->
                val prof = ExerciseSwapEngine.profileFor(ex)
                picked == prof.equipment || picked in prof.requires
            } ?: true
            muscleOk && eqOk
        }
    }
    val custom = remember(filtered, query) {
        val list = filtered.filter { it.custom }
        if (query.isBlank()) list.sortedBy { it.name } else list
    }
    val builtIn = remember(filtered, query) {
        val list = filtered.filter { !it.custom }
        if (query.isBlank()) list.sortedBy { it.name } else list
    }

    Column(Modifier.fillMaxSize().background(c.background).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.cd_back), tint = c.textPrimary) }
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.profile_exercise_library), style = MaterialTheme.typography.titleLarge, color = c.textPrimary)
                Text(stringResource(R.string.lib_counts, library.count { it.custom }, library.count { !it.custom }), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
            }
            IconButton(onClick = { creating = true }) { Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.picker_create_custom), tint = c.teal) }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = query, onValueChange = { query = it }, placeholder = { Text(stringResource(R.string.lib_search)) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) }, singleLine = true, shape = ForgeShape.Full,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = c.teal, unfocusedBorderColor = c.divider, focusedTextColor = c.textPrimary, unfocusedTextColor = c.textPrimary,
                    cursorColor = c.teal, unfocusedLeadingIconColor = c.textSecondary, focusedLeadingIconColor = c.teal,
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
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ChoiceChip(stringResource(R.string.picker_all), selectedEquipment == null, { selectedEquipment = null })
            (listOf(Equipment.BODYWEIGHT) + Equipment.selectableEntries).forEach { eq ->
                ChoiceChip(eq.localName(), selectedEquipment == eq, { selectedEquipment = if (selectedEquipment == eq) null else eq })
            }
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ChoiceChip(stringResource(R.string.muscles_all), muscle == null, { muscle = null })
            Muscle.entries.forEach { m -> ChoiceChip(m.localLabel(), muscle == m, { muscle = if (muscle == m) null else m }) }
        }
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())) {
            if (custom.isNotEmpty()) {
                item { Text(stringResource(R.string.lib_yours), style = MaterialTheme.typography.titleSmall, color = c.textSecondary, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)) }
                items(custom, key = { it.id }) { ex -> Box(itemMotion()) { LibraryRow(ex, onClick = { nav.navigate(Routes.exercise(ex.id)) }, onEdit = { editing = ex }) } }
            } else if (query.isBlank() && muscle == null && selectedEquipment == null) {
                item {
                    Text(
                        stringResource(R.string.lib_none_custom),
                        style = MaterialTheme.typography.bodySmall, color = c.textSecondary, modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
            if (builtIn.isNotEmpty()) {
                item { Text(stringResource(R.string.lib_built_in), style = MaterialTheme.typography.titleSmall, color = c.textSecondary, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)) }
            } else if (custom.isEmpty()) {
                item { Text(stringResource(R.string.lib_search_none), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary, modifier = Modifier.padding(top = 16.dp)) }
            }
            items(builtIn, key = { it.id }) { ex -> Box(itemMotion()) { LibraryRow(ex, onClick = { nav.navigate(Routes.exercise(ex.id)) }) } }
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

    if (creating) {
        ModalBottomSheet(sheetState = rememberFullSheetState(), onDismissRequest = { creating = false }, containerColor = c.card) {
            CustomExerciseEditorSheet(
                title = stringResource(R.string.picker_create_custom),
                initialName = query.trim(),
                initialKind = ExerciseKind.REPS,
                initialMuscle = muscle ?: Muscle.CHEST,
                initialSecondary = emptyList(),
                initialEquipment = selectedEquipment?.name ?: Equipment.BODYWEIGHT.name,
                initialPattern = "",
                initialUnitOverride = "",
                usageText = null,
                onSave = { name, kind, m, sec, eq, pat, uo ->
                    vm.create(name, kind, m, sec, eq, pat, uo)
                    creating = false
                },
                onCancel = { creating = false },
            )
        }
    }

    editing?.let { ex ->
        val usageText = usage?.let { u ->
            stringResource(R.string.lib_usage, pluralStringResource(R.plurals.lib_logged_sets, u.sets, u.sets), pluralStringResource(R.plurals.lib_plan_items, u.planItems, u.planItems))
        }
        ModalBottomSheet(sheetState = rememberFullSheetState(), onDismissRequest = { editing = null }, containerColor = c.card) {
            CustomExerciseEditorSheet(
                title = stringResource(R.string.lib_edit),
                initialName = ex.name,
                initialKind = ex.kind,
                initialMuscle = ex.primary.firstOrNull() ?: Muscle.CHEST,
                initialSecondary = ex.secondary,
                initialEquipment = ex.equipment,
                initialPattern = ex.pattern,
                initialUnitOverride = ex.unitOverride,
                usageText = usageText,
                onSave = { name, kind, m, sec, eq, pat, uo ->
                    vm.save(
                        ex.copy(
                            name = name.ifBlank { ex.name },
                            kind = kind,
                            primary = listOf(m),
                            secondary = sec.distinct().filter { it != m },
                            equipment = eq,
                            pattern = pat,
                            unitOverride = uo,
                        )
                    )
                    editing = null
                },
                onCancel = { editing = null },
                onMerge = { merging = ex; editing = null },
                onDelete = { confirmDelete = ex; editing = null },
            )
        }
    }

    merging?.let { from ->
        ExercisePickerSheet(
            title = stringResource(R.string.lib_merge_title, from.name),
            library = library.filter { it.id != from.id },
            onPick = { into -> vm.merge(from, into); merging = null },
            onCreate = { _, _, _ -> merging = null },
            onDismiss = { merging = null },
            allowCreate = false,
        )
    }

    confirmDelete?.let { ex ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            containerColor = c.card,
            title = { Text(stringResource(R.string.food_delete_title, ex.name), color = c.textPrimary) },
            text = { Text(stringResource(R.string.lib_delete_body), color = c.textSecondary) },
            confirmButton = { TextButton(onClick = { vm.delete(ex); confirmDelete = null }) { Text(stringResource(R.string.action_delete), color = c.coral) } },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text(stringResource(R.string.action_cancel), color = c.textSecondary) } },
        )
    }
}

@Composable
private fun CustomExerciseEditorSheet(
    title: String,
    initialName: String,
    initialKind: ExerciseKind,
    initialMuscle: Muscle,
    initialSecondary: List<Muscle>,
    initialEquipment: String,
    initialPattern: String,
    initialUnitOverride: String,
    usageText: String?,
    onSave: (String, ExerciseKind, Muscle, List<Muscle>, String, String, String) -> Unit,
    onCancel: () -> Unit,
    onMerge: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    val c = Fit.colors
    var name by remember { mutableStateOf(initialName) }
    var kind by remember { mutableStateOf(initialKind) }
    var muscle by remember { mutableStateOf(initialMuscle) }
    var secondary by remember { mutableStateOf(initialSecondary) }
    var equipment by remember {
        mutableStateOf(
            initialEquipment.split(",").mapNotNull { runCatching { Equipment.valueOf(it.trim().uppercase()) }.getOrNull() }.firstOrNull()
                ?: Equipment.BODYWEIGHT
        )
    }
    var pattern by remember {
        mutableStateOf(
            runCatching { MovementPattern.valueOf(initialPattern.trim().uppercase()) }.getOrNull()
        )
    }
    var unitOverride by remember { mutableStateOf(initialUnitOverride) }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = ForgeSpace.Gutter)
            .padding(bottom = 32.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = c.textPrimary)
        if (usageText != null) {
            Text(usageText, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = name, onValueChange = { name = it.take(40) }, label = { Text(stringResource(R.string.label_name)) }, singleLine = true, shape = ForgeShape.Md,
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = c.teal, unfocusedBorderColor = c.divider, focusedTextColor = c.textPrimary, unfocusedTextColor = c.textPrimary),
            modifier = Modifier.fillMaxWidth(),
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
            Muscle.entries.forEach { m ->
                ChoiceChip(m.localLabel(), muscle == m, {
                    muscle = m
                    secondary = secondary.filter { it != m }
                })
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.editor_secondary_muscles), style = MaterialTheme.typography.labelLarge, color = c.textSecondary)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Muscle.entries.filter { it != muscle }.forEach { m ->
                val selected = m in secondary
                ChoiceChip(m.localLabel(), selected, {
                    secondary = if (selected) secondary - m else secondary + m
                })
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.editor_equipment), style = MaterialTheme.typography.labelLarge, color = c.textSecondary)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (listOf(Equipment.BODYWEIGHT) + Equipment.selectableEntries).forEach { eq ->
                ChoiceChip(eq.localName(), equipment == eq, { equipment = eq })
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.editor_pattern), style = MaterialTheme.typography.labelLarge, color = c.textSecondary)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MovementPattern.entries.forEach { p ->
                ChoiceChip(p.localName(), pattern == p, { pattern = if (pattern == p) null else p })
            }
        }
        if (kind == ExerciseKind.WEIGHTED) {
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.editor_unit_override), style = MaterialTheme.typography.labelLarge, color = c.textSecondary)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceChip(stringResource(R.string.editor_unit_default), unitOverride.isBlank(), { unitOverride = "" })
                ChoiceChip(stringResource(R.string.editor_unit_kg), unitOverride.equals("KG", true), { unitOverride = "KG" })
                ChoiceChip(stringResource(R.string.editor_unit_lb), unitOverride.equals("LB", true), { unitOverride = "LB" })
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onMerge != null) TonalPill(stringResource(R.string.lib_merge_into), onClick = onMerge)
            if (onDelete != null) TonalPill(stringResource(R.string.action_delete), onClick = onDelete, tint = c.coral)
            if (onMerge == null && onDelete == null) {
                PillButton(stringResource(R.string.action_cancel), onClick = onCancel, container = c.cardHigh, content = c.textPrimary)
            }
            Spacer(Modifier.weight(1f))
            PillButton(
                stringResource(R.string.action_save),
                onClick = { onSave(name.trim(), kind, muscle, secondary, equipment.name, pattern?.name.orEmpty(), unitOverride) },
                enabled = name.isNotBlank(),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LibraryRow(ex: ExerciseEntity, onClick: () -> Unit, onEdit: (() -> Unit)? = null) {
    val c = Fit.colors
    val prof = remember(ex.id, ex.equipment, ex.pattern) { ExerciseSwapEngine.profileFor(ex) }
    val tier = remember(ex.id, ex.custom) { ExerciseTiers.of(ex) }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(ForgeShape.Md)
            .clickable(onClick = onClick)
            .padding(start = 8.dp, end = if (onEdit != null) 0.dp else 8.dp, top = 8.dp, bottom = 8.dp),
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
                Text(ex.name, style = MaterialTheme.typography.bodyLarge, color = c.textPrimary)
                if (ex.custom) {
                    SmallDataPill(stringResource(R.string.lib_custom), c.textSecondary, c.cardHigh)
                } else if (tier != null) {
                    TierBadge(tier)
                }
            }
            val holdTag = if (ex.kind == ExerciseKind.HOLD) " · ${ex.kind.label()}" else ""
            Text(
                "${prof.equipment.localName()} · ${ex.primaryMusclesLabel()}$holdTag",
                style = MaterialTheme.typography.bodySmall,
                color = c.textSecondary,
            )
        }
        if (onEdit != null) IconButton(onClick = onEdit) { Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.cd_edit), tint = c.textSecondary) }
    }
}
