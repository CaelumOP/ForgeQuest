package com.forgequest.app.ui.workouts

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Edit
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
import androidx.compose.runtime.rememberCoroutineScope
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
import com.forgequest.app.ui.Routes
import com.forgequest.app.ui.appViewModel
import com.forgequest.app.ui.components.EmptyState
import com.forgequest.app.ui.components.FitCard
import com.forgequest.app.ui.components.MuscleRecoveryMap
import com.forgequest.app.ui.components.PillButton
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
fun PlanItemEntity.prescription(kind: ExerciseKind?): String {
    val per = when {
        kind == ExerciseKind.HOLD || (seconds != null && repsMin == null) -> "${seconds ?: 30} s"
        repsMin != null && repsMax != null && repsMax != repsMin -> "$repsMin–$repsMax reps"
        repsMin != null -> "$repsMin reps"
        else -> "reps"
    }
    val load = if (kind == ExerciseKind.WEIGHTED && (weightKg ?: 0f) > 0f) " · ${"%.1f".format(weightKg)} kg" else ""
    return if (circuit) "$per$load" else "$sets × $per$load"
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

    val s = snap ?: return Box(Modifier.fillMaxSize().background(c.background))
    val plan = s.plan

    Column(Modifier.fillMaxSize().background(c.background).statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Workouts", style = MaterialTheme.typography.headlineMedium, color = c.textPrimary)
                Text(plan?.plan?.name ?: "No plan yet", style = MaterialTheme.typography.bodyMedium, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = { nav.navigate(Routes.PLAN) }) {
                Icon(Icons.Rounded.Edit, contentDescription = "Edit plan", tint = c.textPrimary)
            }
        }
        Spacer(Modifier.height(16.dp))

        if (open != null) {
            FitCard(onClick = { nav.navigate(Routes.LIVE) }, padding = PaddingValues(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(c.coral))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Workout in progress", style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
                        Text(open!!.title, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                    }
                    PillButton("Resume", onClick = { nav.navigate(Routes.LIVE) })
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        DayStrip(plan?.days.orEmpty(), selectedDay) { selectedDay = it }
        Spacer(Modifier.height(12.dp))

        val day = plan?.day(selectedDay)
        if (plan == null) {
            FitCard {
                EmptyState(Icons.Rounded.Edit, "No plan yet", "Pick a starter plan, build your own, or ask the coach.") {
                    PillButton("Set up a plan", onClick = { nav.navigate(Routes.PLAN) })
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
                onEdit = { nav.navigate(Routes.PLAN) },
            )
        }
        Spacer(Modifier.height(16.dp))

        FitCard {
            Text("Muscle recovery", style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
            Text("Based on the last 48 hours", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
            Spacer(Modifier.height(16.dp))
            MuscleRecoveryMap(s.recovery, s.settings.bodyType)
        }
        Spacer(Modifier.height(12.dp))
        TonalPill("Quick workout without a plan", onClick = {
            scope.launch { vm.start(null); nav.navigate(Routes.LIVE) }
        }, modifier = Modifier.fillMaxWidth(), icon = Icons.Rounded.PlayArrow)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun DayStrip(days: List<PlanDayWithItems>, selected: Int, onSelect: (Int) -> Unit) {
    val c = Fit.colors
    val today = Dates.todayDayOfWeek()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        (1..7).forEach { dow ->
            val isSel = dow == selected
            val rest = days.firstOrNull { it.day.dayOfWeek == dow }?.day?.isRest ?: true
            Column(
                Modifier.clip(RoundedCornerShape(16.dp)).background(if (isSel) c.teal else c.card).clickable { onSelect(dow) }
                    .padding(vertical = 10.dp).width(42.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    Dates.dayName(dow).take(3),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isSel) c.onTeal else if (dow == today) c.teal else c.textSecondary,
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
) {
    val c = Fit.colors
    FitCard {
        if (day == null || day.day.isRest) {
            EmptyState(
                Icons.Rounded.Bedtime,
                "Rest day",
                day?.day?.note?.ifBlank { null } ?: "Recovery is when muscles grow. Take it easy, stretch, walk.",
            ) {
                TonalPill("Add exercises to this day", onClick = onEdit)
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
                Text(block, style = MaterialTheme.typography.labelLarge, color = c.teal)
                if (items.first().circuit) {
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Rounded.Repeat, contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Circuit · ${items.first().sets} rounds", style = MaterialTheme.typography.labelMedium, color = c.textSecondary)
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
                        Text(item.prescription(ex?.kind), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                    }
                    IconButton(onClick = { onVideo(item.exerciseId) }) {
                        Icon(Icons.Rounded.PlayCircleOutline, contentDescription = "Watch form", tint = c.blue)
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        if (canStart) {
            PillButton(if (isToday) "Start workout" else "Start this workout", onClick = onStart, modifier = Modifier.fillMaxWidth(), icon = Icons.Rounded.PlayArrow)
        }
    }
}
