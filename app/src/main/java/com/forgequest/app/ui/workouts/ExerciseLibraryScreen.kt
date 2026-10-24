package com.forgequest.app.ui.workouts

import com.forgequest.app.R
import com.forgequest.app.ui.localLabel

import androidx.compose.ui.res.pluralStringResource

import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
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
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.forgequest.app.AppContainer
import com.forgequest.app.data.WorkoutRepository
import com.forgequest.app.data.db.ExerciseEntity
import com.forgequest.app.domain.ExerciseKind
import com.forgequest.app.domain.Muscle
import com.forgequest.app.ui.LocalSnackbar
import com.forgequest.app.ui.Routes
import com.forgequest.app.ui.appViewModel
import com.forgequest.app.ui.components.ChoiceChip
import com.forgequest.app.ui.components.PillButton
import com.forgequest.app.ui.components.TonalPill
import com.forgequest.app.ui.theme.Fit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ExerciseLibraryViewModel(val app: AppContainer) : ViewModel() {
    val library: StateFlow<List<ExerciseEntity>> = app.workouts.library.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val message = MutableStateFlow<String?>(null)
    val usage = MutableStateFlow<WorkoutRepository.ExerciseUsage?>(null)

    fun loadUsage(id: String) = viewModelScope.launch { usage.value = app.workouts.usage(id) }

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
    val message by vm.message.collectAsState()
    val usage by vm.usage.collectAsState()
    val snackbar = LocalSnackbar.current
    val c = Fit.colors
    var query by remember { mutableStateOf("") }
    var muscle by remember { mutableStateOf<Muscle?>(null) }
    var editing by remember { mutableStateOf<ExerciseEntity?>(null) }
    var merging by remember { mutableStateOf<ExerciseEntity?>(null) }
    var confirmDelete by remember { mutableStateOf<ExerciseEntity?>(null) }

    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); vm.message.value = null } }
    LaunchedEffect(editing?.id) { editing?.let { vm.loadUsage(it.id) } }

    val filtered = library.filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
        .filter { ex -> muscle.let { m -> m == null || m in ex.primary || m in ex.secondary } }
    val custom = filtered.filter { it.custom }.sortedBy { it.name }
    val builtIn = filtered.filter { !it.custom }.sortedBy { it.name }

    Column(Modifier.fillMaxSize().background(c.background).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.cd_back), tint = c.textPrimary) }
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.profile_exercise_library), style = MaterialTheme.typography.titleLarge, color = c.textPrimary)
                Text(stringResource(R.string.lib_counts, library.count { it.custom }, library.count { !it.custom }), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
            }
        }
        OutlinedTextField(
            value = query, onValueChange = { query = it }, placeholder = { Text(stringResource(R.string.lib_search)) },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) }, singleLine = true, shape = RoundedCornerShape(50),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = c.teal, unfocusedBorderColor = c.divider, focusedTextColor = c.textPrimary, unfocusedTextColor = c.textPrimary,
                cursorColor = c.teal, unfocusedLeadingIconColor = c.textSecondary, focusedLeadingIconColor = c.teal,
            ),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ChoiceChip(stringResource(R.string.muscles_all), muscle == null, { muscle = null })
            Muscle.entries.forEach { m -> ChoiceChip(m.localLabel(), muscle == m, { muscle = if (muscle == m) null else m }) }
        }
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp)) {
            if (custom.isNotEmpty()) {
                item { Text(stringResource(R.string.lib_yours), style = MaterialTheme.typography.titleSmall, color = c.textSecondary, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)) }
                items(custom, key = { it.id }) { ex -> LibraryRow(ex, onClick = { editing = ex }) }
            } else if (query.isBlank()) {
                item {
                    Text(
                        stringResource(R.string.lib_none_custom),
                        style = MaterialTheme.typography.bodySmall, color = c.textSecondary, modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
            item { Text(stringResource(R.string.lib_built_in), style = MaterialTheme.typography.titleSmall, color = c.textSecondary, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)) }
            items(builtIn, key = { it.id }) { ex -> LibraryRow(ex, onClick = { nav.navigate(Routes.exercise(ex.id)) }) }
        }
    }

    editing?.let { ex ->
        var name by remember(ex.id) { mutableStateOf(ex.name) }
        var kind by remember(ex.id) { mutableStateOf(ex.kind) }
        var muscle by remember(ex.id) { mutableStateOf(ex.primary.firstOrNull() ?: Muscle.CHEST) }
        ModalBottomSheet(sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), onDismissRequest = { editing = null }, containerColor = c.card) {
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
                Text(stringResource(R.string.lib_edit), style = MaterialTheme.typography.titleLarge, color = c.textPrimary)
                usage?.let { u ->
                    Text(
                        stringResource(R.string.lib_usage, pluralStringResource(R.plurals.lib_logged_sets, u.sets, u.sets), pluralStringResource(R.plurals.lib_plan_items, u.planItems, u.planItems)),
                        style = MaterialTheme.typography.bodySmall, color = c.textSecondary,
                    )
                }
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = name, onValueChange = { name = it.take(40) }, label = { Text(stringResource(R.string.label_name)) }, singleLine = true, shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = c.teal, unfocusedBorderColor = c.divider, focusedTextColor = c.textPrimary, unfocusedTextColor = c.textPrimary),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.picker_measured_in), style = MaterialTheme.typography.labelLarge, color = c.textSecondary)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExerciseKind.entries.forEach { k -> ChoiceChip(k.label(), kind == k, { kind = k }) }
                }
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.picker_main_muscle), style = MaterialTheme.typography.labelLarge, color = c.textSecondary)
                Spacer(Modifier.height(6.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Muscle.entries.forEach { m -> ChoiceChip(m.localLabel(), muscle == m, { muscle = m }) }
                }
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TonalPill(stringResource(R.string.lib_merge_into), onClick = { merging = ex; editing = null })
                    TonalPill(stringResource(R.string.action_delete), onClick = { confirmDelete = ex; editing = null }, tint = c.coral)
                    Spacer(Modifier.weight(1f))
                    PillButton(stringResource(R.string.action_save), onClick = {
                        vm.save(ex.copy(name = name.trim().ifBlank { ex.name }, kind = kind, primary = listOf(muscle) + ex.primary.filter { it != muscle }.take(1)))
                        editing = null
                    })
                }
            }
        }
    }

    merging?.let { from ->
        ExercisePickerSheet(
            title = stringResource(R.string.lib_merge_title, from.name),
            library = library.filter { it.id != from.id },
            onPick = { into -> vm.merge(from, into); merging = null },
            onCreate = { _, _, _ -> merging = null },
            onDismiss = { merging = null },
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
private fun LibraryRow(ex: ExerciseEntity, onClick: () -> Unit) {
    val c = Fit.colors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(ex.name, style = MaterialTheme.typography.bodyLarge, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${ex.kind.label()} · ${ex.musclesLabel()}", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
        }
        if (ex.custom) {
            Spacer(Modifier.width(8.dp))
            Box(Modifier.clip(RoundedCornerShape(50)).background(c.purpleTrack).padding(horizontal = 8.dp, vertical = 2.dp)) {
                Text(stringResource(R.string.lib_custom), style = MaterialTheme.typography.labelSmall, color = c.purple)
            }
        }
    }
}
