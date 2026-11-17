package com.forgequest.app.ui.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.forgequest.app.R
import com.forgequest.app.container
import com.forgequest.app.data.csvimport.CsvExerciseMatcher
import com.forgequest.app.data.csvimport.CsvImporter
import com.forgequest.app.data.csvimport.CsvPreview
import com.forgequest.app.data.csvimport.ExerciseMapping
import com.forgequest.app.data.db.ExerciseEntity
import com.forgequest.app.ui.LocalSnackbar
import com.forgequest.app.ui.components.ChoiceChip
import com.forgequest.app.ui.components.FitCard
import com.forgequest.app.ui.components.IconCircle
import com.forgequest.app.ui.components.PillButton
import com.forgequest.app.ui.components.TonalPill
import com.forgequest.app.ui.localLabel
import com.forgequest.app.ui.theme.Fit
import com.forgequest.app.ui.theme.ForgeShape
import com.forgequest.app.ui.theme.ForgeSpace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CsvImportScreen(nav: NavHostController) {
    val c = Fit.colors
    val context = LocalContext.current
    val app = context.container
    val scope = rememberCoroutineScope()
    val snackbar = LocalSnackbar.current
    val importer = remember(app) { CsvImporter(app.db) }
    val library by app.workouts.library.collectAsState(initial = emptyList())
    val byId = remember(library) { library.associateBy { it.id } }

    var preview by remember { mutableStateOf<CsvPreview?>(null) }
    var busy by remember { mutableStateOf(false) }
    var pickingFor by remember { mutableStateOf<String?>(null) }
    val mappings = remember { mutableStateMapOf<String, ExerciseMapping>() }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            try {
                val text = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                } ?: throw IllegalStateException("unreadable")
                val next = withContext(Dispatchers.Default) { importer.preview(text) }
                mappings.clear()
                preview = next
            } catch (_: Exception) {
                snackbar.showSnackbar(app.str(R.string.prog41_csv_error))
            } finally {
                busy = false
            }
        }
    }

    Column(Modifier.fillMaxSize().background(c.background).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.cd_back), tint = c.textPrimary)
            }
            Text(
                stringResource(R.string.prog41_csv_import_title),
                style = MaterialTheme.typography.titleLarge,
                color = c.textPrimary,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
        }
        if (busy) {
            LinearProgressIndicator(color = c.teal, trackColor = c.tealTrack, modifier = Modifier.fillMaxWidth())
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = ForgeSpace.Gutter)) {
            Spacer(Modifier.height(8.dp))
            FitCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconCircle(Icons.Rounded.UploadFile, c.teal, c.tealTrack)
                    Spacer(Modifier.height(12.dp))
                    Column(Modifier.padding(start = 12.dp).weight(1f)) {
                        Text(stringResource(R.string.prog41_csv_import_title), style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
                        Text(stringResource(R.string.prog41_csv_import_hint), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                    }
                }
                Spacer(Modifier.height(12.dp))
                PillButton(
                    stringResource(R.string.prog41_csv_pick_file),
                    onClick = { picker.launch(arrayOf("text/*", "application/csv", "application/vnd.ms-excel", "application/octet-stream")) },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            preview?.let { p ->
                Spacer(Modifier.height(12.dp))
                FitCard {
                    Text(stringResource(R.string.prog41_csv_detected, p.source.displayName), style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.prog41_csv_preview_summary, p.totalWorkouts, p.newSetsCount, p.duplicateSetsCount),
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.blue,
                    )
                    Spacer(Modifier.height(12.dp))
                    PillButton(
                        stringResource(R.string.prog41_csv_import_btn),
                        enabled = !busy && p.rows.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            scope.launch {
                                busy = true
                                try {
                                    val res = withContext(Dispatchers.IO) { importer.importTransactional(p, mappings.toMap()) }
                                    val msg = if (res.importedSets == 0) {
                                        app.str(R.string.prog41_csv_nothing)
                                    } else {
                                        app.str(R.string.prog41_csv_done, res.importedWorkouts, res.importedSets)
                                    }
                                    snackbar.showSnackbar(msg)
                                    if (res.importedSets > 0) nav.popBackStack()
                                } catch (_: Exception) {
                                    snackbar.showSnackbar(app.str(R.string.prog41_csv_error))
                                } finally {
                                    busy = false
                                }
                            }
                        },
                    )
                }

                if (p.unmatchedNames.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    FitCard {
                        Text(stringResource(R.string.prog41_csv_unmatched_title), style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
                        Text(stringResource(R.string.prog41_csv_unmatched_hint), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                        Spacer(Modifier.height(10.dp))
                        p.unmatchedNames.forEach { rawName ->
                            val current = mappings[rawName]
                            Column(
                                Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(ForgeShape.Md).background(c.cardHigh).padding(12.dp),
                            ) {
                                Text(rawName, style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
                                val statusText = when (current) {
                                    is ExerciseMapping.UseExisting -> stringResource(R.string.prog41_csv_mapped_to, byId[current.exerciseId]?.name ?: current.exerciseId)
                                    is ExerciseMapping.CreateCustom -> stringResource(R.string.prog41_csv_map_create)
                                    ExerciseMapping.Skip -> stringResource(R.string.prog41_csv_map_skip)
                                    null -> null
                                }
                                if (statusText != null) {
                                    Text(statusText, style = MaterialTheme.typography.bodySmall, color = c.blue)
                                }
                                Spacer(Modifier.height(8.dp))
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TonalPill(stringResource(R.string.prog41_csv_map_pick), onClick = { pickingFor = rawName })
                                    ChoiceChip(
                                        stringResource(R.string.prog41_csv_map_create),
                                        selected = current is ExerciseMapping.CreateCustom,
                                        onClick = { mappings[rawName] = ExerciseMapping.CreateCustom(rawName) },
                                    )
                                    ChoiceChip(
                                        stringResource(R.string.prog41_csv_map_skip),
                                        selected = current == ExerciseMapping.Skip,
                                        onClick = { mappings[rawName] = ExerciseMapping.Skip },
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    pickingFor?.let { raw ->
        val candidates = preview?.exerciseMatches?.get(raw)?.candidates.orEmpty()
        ExerciseMapDialog(
            library = library,
            preferredIds = candidates,
            onDismiss = { pickingFor = null },
            onPick = { ex ->
                mappings[raw] = ExerciseMapping.UseExisting(ex.id)
                pickingFor = null
            },
        )
    }
}

@Composable
private fun ExerciseMapDialog(
    library: List<ExerciseEntity>,
    preferredIds: List<String>,
    onDismiss: () -> Unit,
    onPick: (ExerciseEntity) -> Unit,
) {
    val c = Fit.colors
    var query by remember { mutableStateOf("") }
    val filtered = remember(library, query, preferredIds) {
        val norm = CsvExerciseMatcher.normalize(query)
        val base = if (norm.isEmpty()) {
            val pref = preferredIds.toSet()
            library.sortedByDescending { it.id in pref }
        } else {
            library.filter {
                CsvExerciseMatcher.normalize(it.name).contains(norm) ||
                    CsvExerciseMatcher.normalize(it.aliases).contains(norm) ||
                    it.id.contains(norm)
            }
        }
        base.take(40)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.card,
        title = { Text(stringResource(R.string.prog41_csv_map_pick), color = c.textPrimary) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(stringResource(R.string.prog41_csv_search_hint)) },
                    singleLine = true,
                    shape = ForgeShape.Md,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = c.teal,
                        unfocusedBorderColor = c.divider,
                        focusedTextColor = c.textPrimary,
                        unfocusedTextColor = c.textPrimary,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
                    items(filtered, key = { it.id }) { ex ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clip(ForgeShape.Sm)
                                .clickable { onPick(ex) }
                                .heightIn(min = 48.dp)
                                .padding(horizontal = 8.dp, vertical = 10.dp),
                        ) {
                            Text(ex.name, style = MaterialTheme.typography.bodyLarge, color = c.textPrimary)
                            val muscleLabels = ex.primary.map { it.localLabel() }.joinToString(", ")
                            Text(
                                muscleLabels,
                                style = MaterialTheme.typography.bodySmall,
                                color = c.textSecondary,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel), color = c.textSecondary)
            }
        },
    )
}
