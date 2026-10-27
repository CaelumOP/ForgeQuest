package com.forgequest.app.ui.workouts

import com.forgequest.app.ui.blockLabel
import com.forgequest.app.R

import androidx.compose.ui.res.pluralStringResource

import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.background
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlayCircleOutline
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.forgequest.app.AppContainer
import com.forgequest.app.data.PlanDayWithItems
import com.forgequest.app.data.ProgressSnapshot
import com.forgequest.app.data.db.ExerciseEntity
import com.forgequest.app.data.db.PlanItemEntity
import com.forgequest.app.data.db.SessionEntity
import com.forgequest.app.domain.Dates
import com.forgequest.app.domain.ExerciseKind
import com.forgequest.app.domain.MuscleExercises
import com.forgequest.app.domain.Trends
import com.forgequest.app.domain.WeightUnit
import com.forgequest.app.ui.Routes
import com.forgequest.app.ui.appViewModel
import com.forgequest.app.ui.components.EmptyState
import com.forgequest.app.ui.components.FitCard
import com.forgequest.app.ui.components.MuscleRecoveryMap
import com.forgequest.app.ui.components.PillButton
import com.forgequest.app.ui.components.Sparkline
import com.forgequest.app.ui.components.TonalPill
import com.forgequest.app.ui.theme.Fit
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class WorkoutsViewModel(val app: AppContainer) : ViewModel() {
    val snapshot: StateFlow<ProgressSnapshot?> = app.progress.snapshot.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val openSession: StateFlow<SessionEntity?> = app.workouts.openSession.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    suspend fun start(day: PlanDayWithItems?) = app.workouts.startSession(day)
}

/** "3 × 10 reps", "3 × 30 s", "4 × 6–8 reps" */
@Composable
fun PlanItemEntity.prescription(kind: ExerciseKind?, unit: WeightUnit = WeightUnit.KG): String {
    val per = when {
        kind == ExerciseKind.HOLD || (seconds != null && repsMin == null) -> stringResource(R.string.rx_seconds, seconds ?: 30)
        repsMin != null && repsMax != null && repsMax != repsMin -> stringResource(R.string.rx_rep_range, repsMin, repsMax)
        repsMin != null -> pluralStringResource(R.plurals.rx_reps, repsMin, repsMin)
        else -> stringResource(R.string.rx_reps_only)
    }
    val load = if (kind == ExerciseKind.WEIGHTED && (weightKg ?: 0f) > 0f) " · ${unit.format(weightKg ?: 0f)}" else ""
    return if (circuit) "$per$load" else stringResource(R.string.rx_sets_times, sets, per + load)
}

@Composable
fun WorkoutsScreen(nav: NavHostController) {
    val vm = appViewModel { WorkoutsViewModel(it) }
    val snap by vm.snapshot.collectAsState()
    val open by vm.openSession.collectAsState()
    val c = Fit.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedDay by rememberSaveable { mutableIntStateOf(Dates.todayDayOfWeek()) }
    var muscleSheet by remember { mutableStateOf<com.forgequest.app.domain.Muscle?>(null) }

    val s = snap ?: return Box(Modifier.fillMaxSize().background(c.background))
    val plan = s.plan
    // Best set per session for every exercise on the plan, so the plan reads like a progress board.
    val trends = remember(s.doneSets.size, s.sessions.size) {
        val sessionDay = s.sessions.associate { it.id to it.epochDay }
        s.doneSets.groupBy { it.exerciseId }.mapValues { (id, sets) ->
            Trends.bestSetSeries(sets, s.exercises[id]?.kind ?: ExerciseKind.REPS, sessionDay).takeLast(12).map { it.value }
        }
    }

    Column(Modifier.fillMaxSize().background(c.background).statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.workouts_title), style = MaterialTheme.typography.headlineMedium, color = c.textPrimary)
                Text(plan?.plan?.name ?: stringResource(R.string.today_no_plan), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            // Labelled, so past workouts and the editor are found without guessing what an icon means.
            HeaderAction(Icons.Rounded.History, stringResource(R.string.cd_workout_history)) { nav.navigate(Routes.HISTORY) }
            HeaderAction(Icons.Rounded.Edit, stringResource(R.string.cd_edit_plan)) { nav.navigate(Routes.plan(selectedDay)) }
        }
        Spacer(Modifier.height(16.dp))

        if (open != null) {
            FitCard(onClick = { nav.navigate(Routes.LIVE) }, padding = PaddingValues(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(c.coral))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.today_in_progress), style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
                        Text(open!!.title, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                    }
                    PillButton(stringResource(R.string.action_resume), onClick = { nav.navigate(Routes.LIVE) })
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        DayStrip(plan?.days.orEmpty(), selectedDay) { selectedDay = it }
        Spacer(Modifier.height(12.dp))

        val day = plan?.day(selectedDay)
        if (plan == null) {
            FitCard {
                EmptyState(Icons.Rounded.Edit, stringResource(R.string.today_no_plan), stringResource(R.string.today_no_plan_hint)) {
                    PillButton(stringResource(R.string.workouts_set_up_plan), onClick = { nav.navigate(Routes.plan(selectedDay)) })
                }
            }
        } else {
            DayCard(
                day = day,
                exercises = s.exercises,
                isToday = selectedDay == Dates.todayDayOfWeek(),
                canStart = open == null,
                onStart = {
                    scope.launch {
                        vm.start(day?.takeIf { !it.day.isRest })
                        nav.navigate(Routes.LIVE)
                    }
                },
                onExercise = { nav.navigate(Routes.exercise(it)) },
                onVideo = { openFormVideo(context, s.exercises[it]) },
                onEdit = { nav.navigate(Routes.plan(selectedDay)) },
                unit = s.settings.weightUnit,
                trends = trends,
            )
        }
        Spacer(Modifier.height(16.dp))

        FitCard {
            Text(stringResource(R.string.recovery_title), style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
            Text(stringResource(R.string.recovery_subtitle), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
            Spacer(Modifier.height(16.dp))
            val library = remember(s.exercises) { s.exercises.values.toList() }
            val planIds = remember(plan) { plan?.days.orEmpty().flatMap { d -> d.items.map { it.exerciseId } }.toSet() }
            val equipment = s.settings.configuredEquipment
            MuscleRecoveryMap(
                s.recovery, s.settings.bodyType,
                exerciseCount = { MuscleExercises.list(it, library, planIds, equipment).size },
                onSeeExercises = { muscleSheet = it },
            )
            muscleSheet?.let { m ->
                MuscleExercisesSheet(
                    m, library, planIds, equipment, s.recovery[m],
                    onOpen = { id -> muscleSheet = null; nav.navigate(Routes.exercise(id)) },
                    onDismiss = { muscleSheet = null },
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        TonalPill(stringResource(R.string.workouts_quick), onClick = {
            scope.launch { vm.start(null); nav.navigate(Routes.LIVE) }
        }, modifier = Modifier.fillMaxWidth(), icon = Icons.Rounded.PlayArrow)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun DayStrip(days: List<PlanDayWithItems>, selected: Int, onSelect: (Int) -> Unit) {
    val c = Fit.colors
    val today = Dates.todayDayOfWeek()
    val dayRestCd = stringResource(R.string.day_cd_rest)
    val dayTrainingCd = stringResource(R.string.day_cd_training)
    // Seven chips share the width, so the day names stop growing at 1.3x text (like the bottom bar) instead of turning into "الث…".
    val d = androidx.compose.ui.platform.LocalDensity.current
    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(d.density, d.fontScale.coerceAtMost(1.3f))) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        (1..7).forEach { dow ->
            val isSel = dow == selected
            val rest = days.firstOrNull { it.day.dayOfWeek == dow }?.day?.isRest ?: true
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(if (isSel) c.teal else c.card)
                    .selectable(selected = isSel, role = Role.Tab) { onSelect(dow) }
                    .semantics { contentDescription = if (rest) dayRestCd.format(Dates.dayName(dow)) else dayTrainingCd.format(Dates.dayName(dow)) }
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    Dates.dayShort(dow),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isSel) c.onTeal else if (dow == today) c.teal else c.textSecondary,
                    maxLines = 1,
                    softWrap = false,
                )
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier.size(6.dp).clip(CircleShape).background(
                        when {
                            rest -> androidx.compose.ui.graphics.Color.Transparent
                            isSel -> c.onTeal
                            else -> c.teal
                        }
                    )
                )
            }
        }
    }
    }
}

@Composable
private fun DayCard(
    day: PlanDayWithItems?,
    exercises: Map<String, ExerciseEntity>,
    isToday: Boolean,
    canStart: Boolean,
    onStart: () -> Unit,
    onExercise: (String) -> Unit,
    onVideo: (String) -> Unit,
    onEdit: () -> Unit,
    unit: WeightUnit = WeightUnit.KG,
    trends: Map<String, List<Float>> = emptyMap(),
) {
    val c = Fit.colors
    FitCard {
        if (day == null || day.day.isRest) {
            EmptyState(
                Icons.Rounded.Bedtime,
                stringResource(R.string.today_rest_day),
                day?.day?.note?.ifBlank { null } ?: stringResource(R.string.workouts_rest_hint),
            ) {
                TonalPill(stringResource(R.string.workouts_add_exercises), onClick = onEdit)
            }
            return@FitCard
        }
        Text(day.day.title, style = MaterialTheme.typography.titleLarge, color = c.textPrimary)
        if (day.day.note.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(day.day.note, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
        }
        Spacer(Modifier.height(8.dp))
        val blocks = day.items.groupBy { it.block }
        blocks.forEach { (block, items) ->
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(blockLabel(block), style = MaterialTheme.typography.labelLarge, color = c.teal)
                if (items.first().circuit) {
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Rounded.Repeat, contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(pluralStringResource(R.plurals.workouts_circuit_rounds, items.first().sets, items.first().sets), style = MaterialTheme.typography.labelMedium, color = c.textSecondary)
                }
            }
            items.forEach { item ->
                val ex = exercises[item.exerciseId]
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onExercise(item.exerciseId) }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(ex?.name ?: item.exerciseId, style = MaterialTheme.typography.bodyLarge, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(item.prescription(ex?.kind, unit), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                    }
                    trends[item.exerciseId]?.takeIf { it.size >= 2 }?.let { pts ->
                        Box(Modifier.width(56.dp).padding(horizontal = 4.dp)) { Sparkline(pts, c.chartStrength) }
                    }
                    IconButton(onClick = { onVideo(item.exerciseId) }) {
                        Icon(Icons.Rounded.PlayCircleOutline, contentDescription = stringResource(R.string.cd_watch_form), tint = c.blue)
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        if (canStart) {
            PillButton(if (isToday) stringResource(R.string.workouts_start) else stringResource(R.string.workouts_start_this), onClick = onStart, modifier = Modifier.fillMaxWidth(), icon = Icons.Rounded.PlayArrow)
        }
    }
}

@Composable
private fun HeaderAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = Fit.colors.textPrimary)
        Text(label, style = MaterialTheme.typography.labelSmall, color = Fit.colors.textSecondary)
    }
}
