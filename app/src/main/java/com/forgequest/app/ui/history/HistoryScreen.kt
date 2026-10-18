package com.forgequest.app.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.forgequest.app.AppContainer
import com.forgequest.app.data.ProgressSnapshot
import com.forgequest.app.data.db.ExerciseEntity
import com.forgequest.app.data.db.SessionEntity
import com.forgequest.app.data.db.SetEntity
import com.forgequest.app.domain.ExerciseKind
import com.forgequest.app.domain.WeightUnit
import com.forgequest.app.ui.Routes
import com.forgequest.app.ui.appViewModel
import com.forgequest.app.ui.components.EmptyState
import com.forgequest.app.ui.components.FitCard
import com.forgequest.app.ui.components.NumberStepper
import com.forgequest.app.ui.components.PillButton
import com.forgequest.app.ui.theme.Fit
import com.forgequest.app.ui.workouts.LiveWorkoutViewModel.Field
import com.forgequest.app.ui.workouts.PrTag
import com.forgequest.app.ui.workouts.musclesLabel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

class HistoryViewModel(val app: AppContainer) : ViewModel() {
    val snapshot: StateFlow<ProgressSnapshot?> = app.progress.snapshot.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
}

/** Every finished workout, newest first, grouped by month. */
@Composable
fun HistoryScreen(nav: NavHostController) {
    val vm = appViewModel { HistoryViewModel(it) }
    val snap by vm.snapshot.collectAsState()
    val c = Fit.colors
    val s = snap ?: return Box(Modifier.fillMaxSize().background(c.background))
    val setsBySession = s.doneSets.groupBy { it.sessionId }
    val months = s.sessions.groupBy { YearMonth.from(LocalDate.ofEpochDay(it.epochDay)) }

    Column(Modifier.fillMaxSize().background(c.background).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = c.textPrimary) }
            Column(Modifier.weight(1f)) {
                Text("Workout history", style = MaterialTheme.typography.titleLarge, color = c.textPrimary)
                Text(
                    if (s.sessions.size == 1) "1 workout" else "${s.sessions.size} workouts",
                    style = MaterialTheme.typography.bodySmall, color = c.textSecondary,
                )
            }
        }
        if (s.sessions.isEmpty()) {
            FitCard(Modifier.padding(16.dp)) {
                EmptyState(Icons.Rounded.History, "No workouts yet", "Finished workouts show up here. Tap one to fix a set or remove it.") {
                    PillButton("Go to Workouts", onClick = { nav.popBackStack() })
                }
            }
            return
        }
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            months.forEach { (month, sessions) ->
                item(key = "m$month") {
                    Text(
                        month.format(DateTimeFormatter.ofPattern("MMMM yyyy")),
                        style = MaterialTheme.typography.titleSmall, color = c.textSecondary,
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                    )
                }
                items(sessions, key = { it.id }) { session ->
                    SessionRow(session, setsBySession[session.id].orEmpty(), s.exercises) { nav.navigate(Routes.session(session.id)) }
                }
            }
        }
    }
}

@Composable
private fun SessionRow(session: SessionEntity, sets: List<SetEntity>, exercises: Map<String, ExerciseEntity>, onClick: () -> Unit) {
    val c = Fit.colors
    val date = LocalDate.ofEpochDay(session.epochDay)
    val minutes = session.finishedAt?.let { ((it - session.startedAt) / 60_000).toInt().coerceAtLeast(1) }
    val names = sets.map { it.exerciseId }.distinct().mapNotNull { exercises[it]?.name }
    val prs = sets.count { it.isPr }
    FitCard(onClick = onClick, padding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(c.tealTrack),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
            ) {
                Text(date.format(DateTimeFormatter.ofPattern("d")), style = MaterialTheme.typography.titleMedium, color = c.teal, fontWeight = FontWeight.Bold)
                Text(date.format(DateTimeFormatter.ofPattern("EEE")), style = MaterialTheme.typography.labelSmall, color = c.teal)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(session.title, style = MaterialTheme.typography.titleSmall, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (prs > 0) {
                        Spacer(Modifier.width(8.dp))
                        PrTag()
                        if (prs > 1) Text(" ×$prs", style = MaterialTheme.typography.labelSmall, color = c.gold)
                    }
                }
                Text(
                    listOfNotNull("${sets.size} sets", minutes?.let { "$it min" }).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = c.textSecondary,
                )
                if (names.isNotEmpty()) {
                    Text(names.take(4).joinToString(", ") + if (names.size > 4) " +${names.size - 4}" else "", style = MaterialTheme.typography.bodySmall, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Session detail

@OptIn(ExperimentalCoroutinesApi::class)
class SessionDetailViewModel(val app: AppContainer, private val sessionId: Long) : ViewModel() {
    /** Null while loading; wraps the session, which is itself null once deleted. */
    val session: StateFlow<Loaded?> = app.workouts.observeSession(sessionId).map { Loaded(it) }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val sets: StateFlow<List<SetEntity>> = app.workouts.observeSets(sessionId).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val exercises: StateFlow<Map<String, ExerciseEntity>> = app.workouts.library.map { l -> l.associateBy { it.id } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val unit: StateFlow<WeightUnit> = app.settings.settings.map { it.weightUnit }.stateIn(viewModelScope, SharingStarted.Eagerly, WeightUnit.KG)

    data class Loaded(val session: SessionEntity?)

    fun setField(set: SetEntity, field: Field, value: Float) {
        app.haptics.tick()
        val updated = when (field) {
            Field.REPS -> set.copy(reps = value.roundToInt().coerceIn(0, 999))
            Field.SECONDS -> set.copy(seconds = value.roundToInt().coerceIn(0, 3600))
            Field.WEIGHT -> set.copy(weightKg = ((value * 10).roundToInt() / 10f).coerceIn(0f, 999f))
        }
        viewModelScope.launch { app.workouts.updateFinishedSet(updated) }
    }

    fun deleteSet(set: SetEntity) = viewModelScope.launch { app.workouts.deleteFinishedSet(set) }

    /** The screen closes itself once the session flow emits null. */
    fun deleteWorkout() = viewModelScope.launch { app.workouts.deleteSession(sessionId) }
}

/** One finished workout: fix a number, remove a set, or delete the whole thing. Records are re-checked after each change. */
@Composable
fun SessionDetailScreen(nav: NavHostController, sessionId: Long) {
    val vm = appViewModel { SessionDetailViewModel(it, sessionId) }
    val loaded by vm.session.collectAsState()
    val sets by vm.sets.collectAsState()
    val exercises by vm.exercises.collectAsState()
    val unit by vm.unit.collectAsState()
    val c = Fit.colors
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var pendingSetDelete by remember { mutableStateOf<SetEntity?>(null) }

    val session = loaded?.session
    if (session == null) {
        if (loaded != null) LaunchedEffect(Unit) { nav.popBackStack() }
        Box(Modifier.fillMaxSize().background(c.background))
        return
    }
    val date = LocalDate.ofEpochDay(session.epochDay).format(DateTimeFormatter.ofPattern("EEEE, MMM d"))
    val minutes = session.finishedAt?.let { ((it - session.startedAt) / 60_000).toInt().coerceAtLeast(1) }
    val groups = sets.groupBy { it.itemPosition }.toSortedMap().values.toList()

    Column(Modifier.fillMaxSize().background(c.background).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = c.textPrimary) }
            Column(Modifier.weight(1f)) {
                Text(session.title, style = MaterialTheme.typography.titleLarge, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(listOfNotNull(date, minutes?.let { "$it min" }, "${sets.size} sets").joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = "More", tint = c.textSecondary) }
                DropdownMenu(menu, { menu = false }, containerColor = c.cardHigh) {
                    DropdownMenuItem(text = { Text("Delete workout", color = c.coral) }, onClick = { menu = false; confirmDelete = true })
                }
            }
        }
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text("Tap a number to change it. Records are re-checked after every edit.", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
            }
            items(groups, key = { it.first().itemPosition }) { group ->
                val ex = exercises[group.first().exerciseId]
                val kind = ex?.kind ?: ExerciseKind.REPS
                FitCard(padding = PaddingValues(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp)) {
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { nav.navigate(Routes.exercise(group.first().exerciseId)) }) {
                        Text(ex?.name ?: group.first().exerciseId, style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
                        Text(ex?.musclesLabel().orEmpty().ifBlank { group.first().block }, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                    }
                    Spacer(Modifier.height(4.dp))
                    group.sortedBy { it.setIndex }.forEachIndexed { index, set ->
                        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(28.dp).clip(CircleShape).background(c.cardHigh), contentAlignment = Alignment.Center) {
                                Text("${index + 1}", style = MaterialTheme.typography.labelMedium, color = c.textSecondary)
                            }
                            Spacer(Modifier.width(10.dp))
                            if (set.isPr) PrTag()
                            Spacer(Modifier.weight(1f))
                            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                when (kind) {
                                    ExerciseKind.HOLD -> NumberStepper((set.seconds ?: 0).toFloat(), { vm.setField(set, Field.SECONDS, it) }, 5f, 0f, 3600f, unit = "s", title = "Hold time", minValueWidth = 52.dp)
                                    ExerciseKind.REPS -> NumberStepper((set.reps ?: 0).toFloat(), { vm.setField(set, Field.REPS, it) }, 1f, 0f, 999f, title = "Reps", minValueWidth = 40.dp)
                                    ExerciseKind.WEIGHTED -> {
                                        NumberStepper((set.reps ?: 0).toFloat(), { vm.setField(set, Field.REPS, it) }, 1f, 0f, 999f, unit = "reps", title = "Reps", minValueWidth = 64.dp)
                                        NumberStepper(unit.fromKg(set.weightKg ?: 0f), { vm.setField(set, Field.WEIGHT, unit.toKg(it)) }, unit.loadStep, 0f, unit.fromKg(999f), unit = unit.label, decimals = 1, title = "Weight", minValueWidth = 64.dp, valueColor = c.blue)
                                    }
                                }
                            }
                            IconButton(onClick = { pendingSetDelete = set }) {
                                Icon(Icons.Rounded.Close, contentDescription = "Remove set", tint = c.textSecondary, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
            if (groups.isEmpty()) {
                item { EmptyState(Icons.Rounded.FitnessCenter, "No sets", "This workout has no sets left.") }
            }
        }
    }

    pendingSetDelete?.let { set ->
        AlertDialog(
            onDismissRequest = { pendingSetDelete = null },
            containerColor = c.card,
            title = { Text("Remove this set?", color = c.textPrimary) },
            text = {
                Text(
                    if (sets.size == 1) "It's the last set, so the whole workout will be removed too." else "Records for this exercise will be re-checked.",
                    color = c.textSecondary,
                )
            },
            confirmButton = { TextButton(onClick = { pendingSetDelete = null; vm.deleteSet(set) }) { Text("Remove", color = c.coral) } },
            dismissButton = { TextButton(onClick = { pendingSetDelete = null }) { Text("Cancel", color = c.textSecondary) } },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = c.card,
            title = { Text("Delete this workout?", color = c.textPrimary) },
            text = { Text("All ${sets.size} sets go with it, and your streak, XP and records are recalculated. This can't be undone.", color = c.textSecondary) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; vm.deleteWorkout() }) { Text("Delete", color = c.coral) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep", color = c.teal) } },
        )
    }
}
