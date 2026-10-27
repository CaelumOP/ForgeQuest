package com.forgequest.app.ui.history

import androidx.compose.material.icons.rounded.ExpandMore

import androidx.compose.material.icons.rounded.ExpandLess

import com.forgequest.app.R

import androidx.compose.ui.res.pluralStringResource

import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.background
import com.forgequest.app.ui.workouts.SetNoteDialog
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
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
import com.forgequest.app.domain.WorkoutScore
import com.forgequest.app.ui.Routes
import com.forgequest.app.ui.appViewModel
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import com.forgequest.app.ui.LocalSnackbar
import com.forgequest.app.ui.components.CardShape
import com.forgequest.app.ui.components.SwipeToDelete
import com.forgequest.app.ui.components.EmptyState
import com.forgequest.app.ui.components.FitCard
import com.forgequest.app.ui.components.UndoSnackbarEffect
import com.forgequest.app.ui.components.UndoState
import com.forgequest.app.ui.components.NumberStepper
import com.forgequest.app.ui.components.PillButton
import com.forgequest.app.ui.theme.Fit
import com.forgequest.app.ui.workouts.LiveWorkoutViewModel.Field
import com.forgequest.app.ui.workouts.PrTag
import com.forgequest.app.ui.workouts.musclesLabel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

class HistoryViewModel(val app: AppContainer) : ViewModel() {
    val snapshot: StateFlow<ProgressSnapshot?> = app.progress.snapshot.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val unit: StateFlow<WeightUnit> = app.settings.settings.map { it.weightUnit }.stateIn(viewModelScope, SharingStarted.Eagerly, WeightUnit.KG)
    /** The calculated score of every workout, worked out once per snapshot from what is already loaded, never per row. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val scores: StateFlow<Map<Long, WorkoutScore.Result>> = snapshot
        .distinctUntilChanged { a, b -> a?.sessions == b?.sessions && a?.doneSets == b?.doneSets }
        .mapLatest { snap -> snap?.let { app.workouts.scores(it) }.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    fun setNote(session: SessionEntity, note: String) = viewModelScope.launch { app.db.sessions().update(session.copy(note = note.trim().take(300))) }

    /** A workout deleted by swipe: removed right away, and [undo] puts it back (same ids) while the snackbar is up. */
    val undo = UndoState(viewModelScope)

    fun deleteWorkout(session: SessionEntity) = viewModelScope.launch {
        val sets = app.db.sessions().sets(session.id)
        app.workouts.deleteSession(session.id)
        undo.offer(app.str(R.string.food_removed, session.title)) { app.workouts.restoreSession(session, sets) }
    }

    /** The debrief sheet: which workout it is open for, and whether the coach is writing it or couldn't. */
    val debriefFor = MutableStateFlow<Long?>(null)
    val debriefLoading = MutableStateFlow(false)
    val debriefFailed = MutableStateFlow(false)
    val canDebrief get() = app.coach.canDebrief()

    /** Opens the sheet; a workout with no saved debrief gets one written now. */
    fun openDebrief(session: SessionEntity) {
        debriefFor.value = session.id
        debriefFailed.value = false
        if (com.forgequest.app.data.plan.WorkoutDebrief.decode(session.debrief) == null) writeDebrief(session.id)
    }

    /** App scope so closing the sheet doesn't throw the answer away; it is saved on the workout either way. */
    fun writeDebrief(sessionId: Long) {
        if (debriefLoading.value || !canDebrief) return
        debriefLoading.value = true
        debriefFailed.value = false
        app.appScope.launch {
            val d = app.coach.generateDebrief(sessionId, onDemand = true)
            debriefFailed.value = d == null
            if (d == null) app.haptics.error()
            debriefLoading.value = false
        }
    }
}

/** The journal: a feed of weeks, newest first, each opening into its days and workouts (like Google Fit's journal). */
@Composable
fun HistoryScreen(nav: NavHostController) {
    val vm = appViewModel { HistoryViewModel(it) }
    val snap by vm.snapshot.collectAsState()
    val c = Fit.colors
    val s = snap ?: return Box(Modifier.fillMaxSize().background(c.background))
    val setsBySession = s.doneSets.groupBy { it.sessionId }
    val unit by vm.unit.collectAsState()
    val scores by vm.scores.collectAsState()
    // Weeks start on Monday, like the streak and weekly target (Dates.weekStart), so a journal week's count matches them.
    val firstDay = java.time.DayOfWeek.MONDAY
    val weeks = remember(s.sessions) {
        s.sessions.sortedByDescending { it.startedAt }
            .groupBy { LocalDate.ofEpochDay(it.epochDay).with(java.time.temporal.TemporalAdjusters.previousOrSame(firstDay)) }
    }
    val thisWeek = LocalDate.now().with(java.time.temporal.TemporalAdjusters.previousOrSame(firstDay))
    val open = remember { androidx.compose.runtime.mutableStateMapOf<LocalDate, Boolean>() }
    var noteFor by remember { mutableStateOf<SessionEntity?>(null) }
    noteFor?.let { ses ->
        com.forgequest.app.ui.workouts.SetNoteDialog(ses.note, onDismiss = { noteFor = null }, onSave = { vm.setNote(ses, it); noteFor = null })
    }
    val snackbar = LocalSnackbar.current
    UndoSnackbarEffect(vm.undo, SnackbarDuration.Long)
    val debriefFor by vm.debriefFor.collectAsState()
    s.sessions.firstOrNull { it.id == debriefFor }?.let { ses -> DebriefSheet(vm, ses) }

    Column(Modifier.fillMaxSize().background(c.background).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.cd_back), tint = c.textPrimary) }
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.cd_workout_history), style = MaterialTheme.typography.titleLarge, color = c.textPrimary)
                Text(
                    pluralStringResource(R.plurals.backup_workouts, s.sessions.size, s.sessions.size),
                    style = MaterialTheme.typography.bodySmall, color = c.textSecondary,
                )
            }
        }
        if (s.sessions.isEmpty()) {
            FitCard(Modifier.padding(16.dp)) {
                EmptyState(Icons.Rounded.History, stringResource(R.string.hist_empty_title), stringResource(R.string.hist_empty_body)) {
                    PillButton(stringResource(R.string.hist_go_workouts), onClick = { nav.popBackStack() })
                }
            }
            return
        }
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            weeks.entries.forEachIndexed { index, (week, sessions) ->
                val expanded = open[week] ?: (index == 0)
                item(key = "w$week") {
                    Box(Modifier.animateItem()) { WeekCard(week, thisWeek, sessions, setsBySession, unit, expanded) { open[week] = !expanded } }
                }
                if (expanded) {
                    sessions.groupBy { it.epochDay }.forEach { (day, daySessions) ->
                        item(key = "d$day") {
                            Text(
                                LocalDate.ofEpochDay(day).format(DateTimeFormatter.ofPattern("EEEE d MMMM")),
                                style = MaterialTheme.typography.labelLarge, color = c.textSecondary, modifier = Modifier.animateItem().padding(start = 8.dp, top = 6.dp),
                            )
                        }
                        items(daySessions, key = { it.id }) { session ->
                            Column(Modifier.animateItem().padding(start = 8.dp)) {
                                SwipeToDelete(onDelete = { vm.deleteWorkout(session) }, shape = CardShape) {
                                SessionRow(session, setsBySession[session.id].orEmpty(), s.exercises) { nav.navigate(Routes.session(session.id)) }
                                }
                                scores[session.id]?.let { r ->
                                    Text(
                                        com.forgequest.app.ui.workouts.scoreReasons(r),
                                        style = MaterialTheme.typography.bodySmall, color = c.textSecondary,
                                        modifier = Modifier.padding(start = 12.dp, top = 4.dp, end = 8.dp),
                                    )
                                }
                                com.forgequest.app.data.plan.WorkoutDebrief.decode(session.debrief)?.let { d ->
                                    Text(
                                        d.summary,
                                        style = MaterialTheme.typography.bodySmall, color = c.textSecondary,
                                        modifier = Modifier.padding(start = 12.dp, top = 2.dp, end = 8.dp),
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    TextButton(onClick = { noteFor = session }, modifier = Modifier.weight(1f, fill = false)) {
                                        Text(
                                            session.note.ifBlank { stringResource(R.string.journal_add_note) },
                                            style = MaterialTheme.typography.bodySmall, color = if (session.note.isBlank()) c.teal else c.textSecondary,
                                            fontStyle = if (session.note.isBlank()) null else androidx.compose.ui.text.font.FontStyle.Italic,
                                        )
                                    }
                                    if (setsBySession[session.id].orEmpty().isNotEmpty()) {
                                        TextButton(onClick = { vm.openDebrief(session) }) {
                                            Text(stringResource(R.string.debrief_open), style = MaterialTheme.typography.bodySmall, color = c.teal)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** One week: how many workouts, time, kg lifted and records; tap to open its days. */
/** The journal's coach debrief for one workout: the saved one, or the coach writing it now, with Try again on failure. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun DebriefSheet(vm: HistoryViewModel, session: SessionEntity) {
    val c = Fit.colors
    val loading by vm.debriefLoading.collectAsState()
    val failed by vm.debriefFailed.collectAsState()
    val debrief = com.forgequest.app.data.plan.WorkoutDebrief.decode(session.debrief)
    val score = vm.scores.collectAsState().value[session.id]
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = { vm.debriefFor.value = null }, containerColor = c.background) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).navigationBarsPadding()) {
            Text(session.title, style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
            Text(
                LocalDate.ofEpochDay(session.epochDay).format(DateTimeFormatter.ofPattern("EEEE d MMMM")),
                style = MaterialTheme.typography.bodySmall, color = c.textSecondary,
            )
            Spacer(Modifier.height(12.dp))
            if (debrief == null && !vm.canDebrief) {
                if (score != null) {
                    com.forgequest.app.ui.workouts.DebriefCard(null, score, waiting = false)
                    Spacer(Modifier.height(12.dp))
                }
                Text(stringResource(R.string.debrief_no_key), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
            } else {
                com.forgequest.app.ui.workouts.DebriefCard(debrief, score, failed = failed && !loading, waiting = loading, onRetry = { vm.writeDebrief(session.id) })
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun WeekCard(
    week: LocalDate,
    thisWeek: LocalDate,
    sessions: List<SessionEntity>,
    setsBySession: Map<Long, List<SetEntity>>,
    unit: WeightUnit,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val c = Fit.colors
    val sets = sessions.flatMap { setsBySession[it.id].orEmpty() }
    val minutes = sessions.sumOf { s -> s.finishedAt?.let { ((it - s.startedAt) / 60_000).toInt().coerceAtLeast(1) } ?: 0 }
    val volume = sets.sumOf { ((it.weightKg ?: 0f) * (it.reps ?: 0)).toDouble() }.toFloat()
    val prs = sets.count { it.isPr }
    val title = when (week) {
        thisWeek -> stringResource(R.string.journal_this_week)
        thisWeek.minusWeeks(1) -> stringResource(R.string.journal_last_week)
        else -> stringResource(R.string.journal_range, week.format(DateTimeFormatter.ofPattern("d MMM")), week.plusDays(6).format(DateTimeFormatter.ofPattern("d MMM")))
    }
    FitCard(onClick = onToggle, padding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
                Text(
                    listOfNotNull(
                        pluralStringResource(R.plurals.backup_workouts, sessions.size, sessions.size),
                        stringResource(R.string.hist_minutes, minutes),
                        if (volume > 0f) stringResource(R.string.journal_volume, "%s%,d %s%s".format(Locale.US, if (Locale.getDefault().language == "ar") "⁧" else "⁦", unit.fromKg(volume).roundToInt(), unit.label, "⁩")) else null,
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = c.textSecondary,
                )
            }
            if (prs > 0) {
                PrTag()
                if (prs > 1) Text(" ×$prs", style = MaterialTheme.typography.labelSmall, color = c.gold)
                Spacer(Modifier.width(6.dp))
            }
            Icon(
                if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = null, tint = c.textSecondary,
            )
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
                // Grows with the text size instead of clipping the weekday.
                Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp).clip(RoundedCornerShape(14.dp)).background(c.tealTrack).padding(horizontal = 6.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
            ) {
                Text(date.format(DateTimeFormatter.ofPattern("d")), style = MaterialTheme.typography.titleMedium, color = c.teal, fontWeight = FontWeight.Bold)
                Text(com.forgequest.app.domain.Dates.dayShort(date.dayOfWeek.value), style = MaterialTheme.typography.labelSmall, color = c.teal)
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
                    listOfNotNull(pluralStringResource(R.plurals.hist_sets, sets.size, sets.size), minutes?.let { stringResource(R.string.hist_minutes, it) }).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = c.textSecondary,
                )
                if (names.isNotEmpty()) {
                    Text(names.take(4).joinToString(stringResource(R.string.list_separator)) + if (names.size > 4) " +${names.size - 4}" else "", style = MaterialTheme.typography.bodySmall, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
    /** This workout's calculated score; null until the snapshot loads or when no set is done. */
    val score: StateFlow<WorkoutScore.Result?> = app.progress.snapshot.map { snap -> app.workouts.scores(snap, only = sessionId)[sessionId] }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

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
    fun setNote(set: SetEntity, note: String) = viewModelScope.launch { app.workouts.updateFinishedSet(set.copy(note = note.trim().take(120))) }

    /** The screen closes itself once the session flow emits null. */
    fun deleteWorkout() = viewModelScope.launch { app.workouts.deleteSession(sessionId) }
    fun moveTo(epochDay: Long) = viewModelScope.launch { app.workouts.moveSession(sessionId, epochDay) }
}

/** One finished workout: fix a number, remove a set, or delete the whole thing. Records are re-checked after each change. */
@OptIn(ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SessionDetailScreen(nav: NavHostController, sessionId: Long) {
    val vm = appViewModel { SessionDetailViewModel(it, sessionId) }
    val loaded by vm.session.collectAsState()
    val sets by vm.sets.collectAsState()
    val exercises by vm.exercises.collectAsState()
    val unit by vm.unit.collectAsState()
    val score by vm.score.collectAsState()
    val c = Fit.colors
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    var pendingSetDelete by remember { mutableStateOf<SetEntity?>(null) }
    var noteFor by remember { mutableStateOf<SetEntity?>(null) }

    val session = loaded?.session
    if (session == null) {
        if (loaded != null) LaunchedEffect(Unit) { nav.popBackStack() }
        Box(Modifier.fillMaxSize().background(c.background))
        return
    }
    val date = LocalDate.ofEpochDay(session.epochDay).format(DateTimeFormatter.ofPattern(stringResource(R.string.home_date_pattern)))
    val minutes = session.finishedAt?.let { ((it - session.startedAt) / 60_000).toInt().coerceAtLeast(1) }
    val groups = sets.groupBy { it.itemPosition }.toSortedMap().values.toList()

    Column(Modifier.fillMaxSize().background(c.background).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.cd_back), tint = c.textPrimary) }
            Column(Modifier.weight(1f)) {
                Text(session.title, style = MaterialTheme.typography.titleLarge, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(listOfNotNull(date, minutes?.let { stringResource(R.string.hist_minutes, it) }, pluralStringResource(R.plurals.hist_sets, sets.size, sets.size)).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.cd_more), tint = c.textSecondary) }
                DropdownMenu(menu, { menu = false }, containerColor = c.cardHigh) {
                    // Strong and Hevy let you fix a workout's date; logging a day late shouldn't break the streak.
                    DropdownMenuItem(text = { Text(stringResource(R.string.hist_change_date)) }, onClick = { menu = false; picking = true })
                    DropdownMenuItem(text = { Text(stringResource(R.string.hist_delete_workout), color = c.coral) }, onClick = { menu = false; confirmDelete = true })
                }
            }
        }
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text(stringResource(R.string.hist_edit_hint), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
            }
            val d = com.forgequest.app.data.plan.WorkoutDebrief.decode(session.debrief)
            if (d != null || score != null) {
                item(key = "debrief") { com.forgequest.app.ui.workouts.DebriefCard(d, score, waiting = false) }
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
                        Row(
                            Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(12.dp))
                                .combinedClickable(onClick = {}, onLongClick = { noteFor = set }, onLongClickLabel = stringResource(R.string.cd_add_note)),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(28.dp).clip(CircleShape).background(c.cardHigh), contentAlignment = Alignment.Center) {
                                Text("${index + 1}", style = MaterialTheme.typography.labelMedium, color = c.textSecondary)
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                if (set.isPr) PrTag()
                                if (set.note.isNotBlank()) Text(set.note, style = MaterialTheme.typography.labelSmall, color = c.blue, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                when (kind) {
                                    ExerciseKind.HOLD -> NumberStepper((set.seconds ?: 0).toFloat(), { vm.setField(set, Field.SECONDS, it) }, 5f, 0f, 3600f, unit = stringResource(R.string.unit_s), title = stringResource(R.string.stepper_hold_time), minValueWidth = 52.dp)
                                    ExerciseKind.REPS -> NumberStepper((set.reps ?: 0).toFloat(), { vm.setField(set, Field.REPS, it) }, 1f, 0f, 999f, title = stringResource(R.string.stepper_reps), minValueWidth = 40.dp)
                                    ExerciseKind.WEIGHTED -> {
                                        NumberStepper((set.reps ?: 0).toFloat(), { vm.setField(set, Field.REPS, it) }, 1f, 0f, 999f, unit = stringResource(R.string.unit_reps), title = stringResource(R.string.stepper_reps), minValueWidth = 64.dp)
                                        NumberStepper(unit.fromKg(set.weightKg ?: 0f), { vm.setField(set, Field.WEIGHT, unit.toKg(it)) }, unit.loadStep, 0f, unit.fromKg(999f), unit = unit.label, decimals = 1, title = stringResource(R.string.stepper_weight), minValueWidth = 64.dp, valueColor = c.blue)
                                    }
                                }
                            }
                            IconButton(onClick = { pendingSetDelete = set }) {
                                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.cd_remove_set), tint = c.textSecondary, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
            if (groups.isEmpty()) {
                item { EmptyState(Icons.Rounded.FitnessCenter, stringResource(R.string.hist_no_sets), stringResource(R.string.hist_no_sets_body)) }
            }
        }
    }

    noteFor?.let { set ->
        SetNoteDialog(set.note, onDismiss = { noteFor = null }, onSave = { vm.setNote(set, it); noteFor = null })
    }

    pendingSetDelete?.let { set ->
        AlertDialog(
            onDismissRequest = { pendingSetDelete = null },
            containerColor = c.card,
            title = { Text(stringResource(R.string.hist_remove_set_title), color = c.textPrimary) },
            text = {
                Text(
                    if (sets.size == 1) stringResource(R.string.hist_remove_last_set) else stringResource(R.string.hist_records_rechecked),
                    color = c.textSecondary,
                )
            },
            confirmButton = { TextButton(onClick = { pendingSetDelete = null; vm.deleteSet(set) }) { Text(stringResource(R.string.action_remove), color = c.coral) } },
            dismissButton = { TextButton(onClick = { pendingSetDelete = null }) { Text(stringResource(R.string.action_cancel), color = c.textSecondary) } },
        )
    }

    if (picking) {
        val state = androidx.compose.material3.rememberDatePickerState(
            initialSelectedDateMillis = session.epochDay * 86_400_000L,
            selectableDates = object : androidx.compose.material3.SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis / 86_400_000L <= com.forgequest.app.domain.Dates.today()
            },
        )
        androidx.compose.material3.DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = { state.selectedDateMillis?.let { vm.moveTo(it / 86_400_000L) }; picking = false }) { Text(stringResource(R.string.action_save), color = c.teal) }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text(stringResource(R.string.action_cancel), color = c.textSecondary) } },
        ) { androidx.compose.material3.DatePicker(state) }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = c.card,
            title = { Text(stringResource(R.string.hist_delete_title), color = c.textPrimary) },
            text = { Text(pluralStringResource(R.plurals.hist_delete_body, sets.size, sets.size), color = c.textSecondary) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; vm.deleteWorkout() }) { Text(stringResource(R.string.action_delete), color = c.coral) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_keep), color = c.teal) } },
        )
    }
}
