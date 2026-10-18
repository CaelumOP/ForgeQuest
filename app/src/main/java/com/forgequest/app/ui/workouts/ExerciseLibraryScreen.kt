package com.forgequest.app.ui.workouts

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

    fun save(ex: ExerciseEntity) = viewModelScope.launch { app.workouts.updateExercise(ex); message.value = "Saved ${ex.name}" }

    fun merge(from: ExerciseEntity, into: ExerciseEntity) = viewModelScope.launch {
        app.workouts.mergeExercise(from.id, into.id)
        message.value = "${from.name} merged into ${into.name}"
    }

    fun delete(ex: ExerciseEntity) = viewModelScope.launch {
        message.value = if (app.workouts.deleteExercise(ex.id)) "Deleted ${ex.name}" else "${ex.name} is still used. Merge it into another exercise instead."
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
    var editing by remember { mutableStateOf<ExerciseEntity?>(null) }
    var merging by remember { mutableStateOf<ExerciseEntity?>(null) }
    var confirmDelete by remember { mutableStateOf<ExerciseEntity?>(null) }

    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); vm.message.value = null } }
    LaunchedEffect(editing?.id) { editing?.let { vm.loadUsage(it.id) } }

    val filtered = library.filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
    val custom = filtered.filter { it.custom }.sortedBy { it.name }
    val builtIn = filtered.filter { !it.custom }.sortedBy { it.name }

    Column(Modifier.fillMaxSize().background(c.background).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = c.textPrimary) }
            Column(Modifier.weight(1f)) {
                Text("Exercise library", style = MaterialTheme.typography.titleLarge, color = c.textPrimary)
                Text("${library.count { it.custom }} custom · ${library.count { !it.custom }} built in", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
            }
        }
        OutlinedTextField(
            value = query, onValueChange = { query = it }, placeholder = { Text("Search") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) }, singleLine = true, shape = RoundedCornerShape(50),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = c.teal, unfocusedBorderColor = c.divider, focusedTextColor = c.textPrimary, unfocusedTextColor = c.textPrimary,
                cursorColor = c.teal, unfocusedLeadingIconColor = c.textSecondary, focusedLeadingIconColor = c.teal,
            ),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp)) {
            if (custom.isNotEmpty()) {
                item { Text("Your exercises", style = MaterialTheme.typography.titleSmall, color = c.textSecondary, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)) }
                items(custom, key = { it.id }) { ex -> LibraryRow(ex, onClick = { editing = ex }) }
            } else if (query.isBlank()) {
                item {
                    Text(
                        "Nothing custom yet. Exercises you create in a workout or the plan editor, and ones the coach invents, show up here.",
                        style = MaterialTheme.typography.bodySmall, color = c.textSecondary, modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
            item { Text("Built in", style = MaterialTheme.typography.titleSmall, color = c.textSecondary, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)) }
            items(builtIn, key = { it.id }) { ex -> LibraryRow(ex, onClick = { nav.navigate(Routes.exercise(ex.id)) }) }
        }
    }

    editing?.let { ex ->
        var name by remember(ex.id) { mutableStateOf(ex.name) }
        var kind by remember(ex.id) { mutableStateOf(ex.kind) }
        var muscle by remember(ex.id) { mutableStateOf(ex.primary.firstOrNull() ?: Muscle.CHEST) }
        ModalBottomSheet(sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), onDismissRequest = { editing = null }, containerColor = c.card) {
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
                Text("Edit exercise", style = MaterialTheme.typography.titleLarge, color = c.textPrimary)
                usage?.let { u ->
                    Text(
                        "Used in ${u.sets} logged set${if (u.sets == 1) "" else "s"} and ${u.planItems} plan item${if (u.planItems == 1) "" else "s"}",
                        style = MaterialTheme.typography.bodySmall, color = c.textSecondary,
                    )
                }
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = name, onValueChange = { name = it.take(40) }, label = { Text("Name") }, singleLine = true, shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = c.teal, unfocusedBorderColor = c.divider, focusedTextColor = c.textPrimary, unfocusedTextColor = c.textPrimary),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Text("Measured in", style = MaterialTheme.typography.labelLarge, color = c.textSecondary)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExerciseKind.entries.forEach { k -> ChoiceChip(k.label(), kind == k, { kind = k }) }
                }
                Spacer(Modifier.height(12.dp))
                Text("Main muscle", style = MaterialTheme.typography.labelLarge, color = c.textSecondary)
                Spacer(Modifier.height(6.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Muscle.entries.forEach { m -> ChoiceChip(m.label, muscle == m, { muscle = m }) }
                }
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TonalPill("Merge into…", onClick = { merging = ex; editing = null })
                    TonalPill("Delete", onClick = { confirmDelete = ex; editing = null }, tint = c.coral)
                    Spacer(Modifier.weight(1f))
                    PillButton("Save", onClick = {
                        vm.save(ex.copy(name = name.trim().ifBlank { ex.name }, kind = kind, primary = listOf(muscle) + ex.primary.filter { it != muscle }.take(1)))
                        editing = null
                    })
                }
            }
        }
    }

    merging?.let { from ->
        ExercisePickerSheet(
            title = "Merge ${from.name} into…",
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
            title = { Text("Delete ${ex.name}?", color = c.textPrimary) },
            text = { Text("Only possible when no workout or plan uses it. Otherwise merge it into another exercise so your history stays intact.", color = c.textSecondary) },
            confirmButton = { TextButton(onClick = { vm.delete(ex); confirmDelete = null }) { Text("Delete", color = c.coral) } },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel", color = c.textSecondary) } },
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
                Text("Custom", style = MaterialTheme.typography.labelSmall, color = c.purple)
            }
        }
    }
}
