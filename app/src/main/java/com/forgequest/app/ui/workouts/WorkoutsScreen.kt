package com.forgequest.app.ui.workouts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlayCircleOutline
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.forgequest.app.AppContainer
import com.forgequest.app.R
import com.forgequest.app.data.PlanDayWithItems
import com.forgequest.app.data.ProgressSnapshot
import com.forgequest.app.data.db.ExerciseEntity
import com.forgequest.app.data.db.PlanItemEntity
import com.forgequest.app.data.db.SessionEntity
import com.forgequest.app.domain.Dates
import com.forgequest.app.domain.ExerciseKind
import com.forgequest.app.domain.Muscle
import com.forgequest.app.domain.MuscleExercises
import com.forgequest.app.domain.Progression
import com.forgequest.app.domain.Trends
import com.forgequest.app.domain.WeightUnit
import com.forgequest.app.domain.WorkoutScore
import com.forgequest.app.ui.Routes
import com.forgequest.app.ui.appViewModel
import com.forgequest.app.ui.blockLabel
import com.forgequest.app.ui.components.FitCard
import com.forgequest.app.ui.components.MuscleRecoveryMap
import com.forgequest.app.ui.components.PillButton
import com.forgequest.app.ui.components.Sparkline
import com.forgequest.app.ui.components.TonalPill
import com.forgequest.app.ui.components.reflowText
import com.forgequest.app.ui.components.rememberFullSheetState
import com.forgequest.app.ui.theme.Fit
import com.forgequest.app.ui.theme.ForgeShape
import com.forgequest.app.ui.theme.ForgeSpace
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class WorkoutsViewModel(val app: AppContainer) : ViewModel() {
    val snapshot: StateFlow<ProgressSnapshot?> = app.progress.snapshot.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val openSession: StateFlow<SessionEntity?> = app.workouts.openSession.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    suspend fun start(day: PlanDayWithItems?) = app.workouts.startSession(day)
}

data class ExerciseFit(val rows: Int, val visibleCount: Int, val hiddenCount: Int)

internal const val ROW_HEIGHT_2COL = 46
internal const val MIN_EXERCISE_ROWS = 2
internal const val MAX_EXERCISE_ROWS = 4

/**
 * Fits [itemCount] exercise glance cells (2 columns, 46 dp rows) into [availableHeight] dp:
 * - normally 2 rows (up to 4 exercises; when > 4, the 4th cell is "+N more");
 * - on tall screens where [availableHeight] allows, grows to 3 or 4 rows (up to 6 or 8 cells).
 */
internal fun fitExerciseRows(availableHeight: Int, itemCount: Int): ExerciseFit {
    if (itemCount <= 0) return ExerciseFit(rows = MIN_EXERCISE_ROWS, visibleCount = 0, hiddenCount = 0)
    val h = maxOf(0, availableHeight)
    val maxRows = (h / ROW_HEIGHT_2COL).coerceIn(MIN_EXERCISE_ROWS, MAX_EXERCISE_ROWS)
    val neededRows = maxOf(MIN_EXERCISE_ROWS, (itemCount + 1) / 2)
    val rows = minOf(maxRows, neededRows)
    val cells = rows * 2
    if (cells >= itemCount) {
        return ExerciseFit(rows = rows, visibleCount = itemCount, hiddenCount = 0)
    }
    val visible = cells - 1
    return ExerciseFit(rows = rows, visibleCount = visible, hiddenCount = itemCount - visible)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutsScreen(nav: NavHostController) {
    val vm = appViewModel { WorkoutsViewModel(it) }
    val snap by vm.snapshot.collectAsState()
    val open by vm.openSession.collectAsState()
    val c = Fit.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val todayDow = Dates.todayDayOfWeek()
    var selectedDay by rememberSaveable { mutableIntStateOf(todayDow) }
    var daySheetOpen by rememberSaveable { mutableStateOf(false) }
    var muscleSheet by remember { mutableStateOf<Muscle?>(null) }

    val s = snap ?: return Box(Modifier.fillMaxSize().background(c.background))
    val plan = s.plan
    // Best set per session for every exercise on the plan, so the plan reads like a progress board.
    val trends = remember(s.doneSets.size, s.sessions.size) {
        val sessionDay = s.sessions.associate { it.id to it.epochDay }
        s.doneSets.groupBy { it.exerciseId }.mapValues { (id, sets) ->
            Trends.bestSetSeries(sets, s.exercises[id]?.kind ?: ExerciseKind.REPS, sessionDay).takeLast(12).map { it.value }
        }
    }

    val library = remember(s.exercises) { s.exercises.values.toList() }
    val planIds = remember(plan) { plan?.days.orEmpty().flatMap { d -> d.items.map { it.exerciseId } }.toSet() }
    val equipment = s.settings.configuredEquipment

    val day = plan?.day(selectedDay)
    val isRest = plan != null && (day == null || day.day.isRest)
    val isToday = selectedDay == todayDow
    val deload = plan != null && day != null && !day.day.isRest && Progression.isDeloadWeek(plan.plan.createdAt, Dates.today())

    val startDay = {
        scope.launch {
            vm.start(day?.takeIf { !it.day.isRest })
            nav.navigate(Routes.LIVE)
        }
        Unit
    }
    val startQuick = {
        scope.launch {
            vm.start(null)
            nav.navigate(Routes.LIVE)
        }
        Unit
    }

    val fontScale = LocalDensity.current.fontScale

    BoxWithConstraints(Modifier.fillMaxSize().background(c.background).statusBarsPadding()) {
        val fallbackScroll = maxHeight < 568.dp || fontScale > 1.3f
        // Spare vertical height left for exercise rows once the map figures reach their 300 dp maximum.
        val availRowDp = (maxHeight - 620.dp).value.toInt().coerceAtLeast(ROW_HEIGHT_2COL * MIN_EXERCISE_ROWS)

        val colMod = Modifier
            .fillMaxSize()
            .let { if (fallbackScroll) it.verticalScroll(rememberScrollState()) else it }
            .padding(horizontal = ForgeSpace.Gutter)
            .padding(bottom = 12.dp)

        Column(colMod, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.workouts_title), style = MaterialTheme.typography.headlineMedium, color = c.textPrimary)
                    Text(
                        plan?.plan?.name ?: stringResource(R.string.today_no_plan),
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                // Labelled, so past workouts and the editor are found without guessing what an icon means.
                HeaderAction(Icons.AutoMirrored.Rounded.MenuBook, stringResource(R.string.workouts_library)) { nav.navigate(Routes.LIBRARY) }
                HeaderAction(Icons.Rounded.History, stringResource(R.string.cd_workout_history)) { nav.navigate(Routes.HISTORY) }
                if (plan != null) {
                    HeaderAction(Icons.Rounded.Edit, stringResource(R.string.cd_edit_plan)) { nav.navigate(Routes.plan(selectedDay)) }
                }
            }

            if (plan != null) {
                DayStrip(plan.days, selectedDay, s.workoutDoneToday) { selectedDay = it }
            }

            val heroMod = if (fallbackScroll) Modifier.fillMaxWidth() else Modifier.weight(1f).fillMaxWidth()
            RecoveryHeroCard(
                s = s,
                open = open,
                planExists = plan != null,
                day = day,
                selectedDay = selectedDay,
                isToday = isToday,
                isRest = isRest,
                fallbackScroll = fallbackScroll,
                onSeeExercises = { muscleSheet = it },
                exerciseCount = { m -> MuscleExercises.list(m, library, planIds, equipment).size },
                modifier = heroMod,
            )

            when {
                plan == null -> {
                    Text(
                        stringResource(R.string.pb_entry_info),
                        style = MaterialTheme.typography.bodySmall,
                        color = c.textSecondary,
                        textAlign = TextAlign.Center,
                        maxLines = if (fallbackScroll) 2 else 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    )
                }
                isRest -> {
                    RestDayCard(
                        note = day?.day?.note.orEmpty(),
                        fallbackScroll = fallbackScroll,
                        onAddExercises = { nav.navigate(Routes.plan(selectedDay)) },
                    )
                }
                day != null -> {
                    ExerciseGlanceCard(
                        day = day,
                        exercises = s.exercises,
                        unit = s.settings.weightUnit,
                        availRowDp = availRowDp,
                        fallbackScroll = fallbackScroll,
                        onOpenDetails = { daySheetOpen = true },
                    )
                }
            }

            BottomActionRow(
                open = open,
                planExists = plan != null,
                isRest = isRest,
                isToday = isToday,
                doneToday = s.workoutDoneToday,
                onResume = { nav.navigate(Routes.LIVE) },
                onBuildPlan = { nav.navigate(Routes.PLAN_BUILDER) },
                onStart = startDay,
                onQuick = startQuick,
            )
        }
    }

    if (daySheetOpen && day != null && !day.day.isRest) {
        ModalBottomSheet(
            sheetState = rememberFullSheetState(),
            onDismissRequest = { daySheetOpen = false },
            containerColor = c.card,
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = ForgeSpace.Gutter)
                    .padding(bottom = 24.dp)
                    .navigationBarsPadding(),
            ) {
                if (deload) {
                    Text(
                        stringResource(R.string.deload_week_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = c.textSecondary,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                DayDetailsContent(
                    day = day,
                    exercises = s.exercises,
                    onExercise = { id -> daySheetOpen = false; nav.navigate(Routes.exercise(id)) },
                    onVideo = { openFormVideo(context, s.exercises[it]) },
                    unit = s.settings.weightUnit,
                    trends = trends,
                )
            }
        }
    }

    muscleSheet?.let { m ->
        MuscleExercisesSheet(
            m, library, planIds, equipment, s.recovery[m],
            onOpen = { id -> muscleSheet = null; nav.navigate(Routes.exercise(id)) },
            onDismiss = { muscleSheet = null },
        )
    }
}

@Composable
private fun RecoveryHeroCard(
    s: ProgressSnapshot,
    open: SessionEntity?,
    planExists: Boolean,
    day: PlanDayWithItems?,
    selectedDay: Int,
    isToday: Boolean,
    isRest: Boolean,
    fallbackScroll: Boolean,
    onSeeExercises: (Muscle) -> Unit,
    exerciseCount: (Muscle) -> Int,
    modifier: Modifier = Modifier,
) {
    val c = Fit.colors
    val tagText = when {
        open != null -> stringResource(R.string.today_in_progress)
        !planExists || isToday -> stringResource(R.string.day_today)
        else -> Dates.dayName(selectedDay)
    }

    val titleText = when {
        open != null -> open.title
        !planExists -> stringResource(R.string.today_no_plan)
        isRest -> stringResource(R.string.today_rest_day)
        else -> day?.day?.title.orEmpty()
    }

    val countText = if (planExists && !isRest && day != null) {
        pluralStringResource(R.plurals.coach_exercises, day.items.size, day.items.size)
    } else ""

    FitCard(
        modifier = modifier,
        padding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    tagText,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = c.teal,
                    maxLines = 1,
                    softWrap = false,
                )
                Text(
                    titleText,
                    style = MaterialTheme.typography.titleLarge,
                    color = c.textPrimary,
                    maxLines = if (fallbackScroll) 2 else 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (countText.isNotEmpty()) {
                Text(
                    countText,
                    style = MaterialTheme.typography.labelMedium,
                    color = c.textSecondary,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        val mapMod = if (fallbackScroll) Modifier.fillMaxWidth() else Modifier.weight(1f).fillMaxWidth()
        MuscleRecoveryMap(
            recovery = s.recovery,
            bodyType = s.settings.bodyType,
            modifier = mapMod,
            fallbackScroll = fallbackScroll,
            onSeeExercises = onSeeExercises,
            exerciseCount = exerciseCount,
        )
    }
}

@Composable
private fun ExerciseGlanceCard(
    day: PlanDayWithItems,
    exercises: Map<String, ExerciseEntity>,
    unit: WeightUnit,
    availRowDp: Int,
    fallbackScroll: Boolean,
    onOpenDetails: () -> Unit,
) {
    val c = Fit.colors
    val blocks = remember(day.items) { day.items.groupBy { it.block } }
    val blockSummary = blocks.entries.map { (block, items) -> "${blockLabel(block)} ${items.size}" }.joinToString(" · ")
    val detailsLabel = stringResource(R.string.workouts_details)
    val fit = fitExerciseRows(availRowDp, day.items.size)
    val visibleItems = day.items.take(fit.visibleCount)
    val totalCells = visibleItems.size + if (fit.hiddenCount > 0) 1 else 0
    val rowPairs = if (fallbackScroll) (day.items.size + 1) / 2 else fit.rows

    FitCard(
        modifier = Modifier.fillMaxWidth().semantics { role = Role.Button },
        onClick = onOpenDetails,
        padding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().height(22.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                blockSummary,
                style = MaterialTheme.typography.labelMedium,
                color = c.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(detailsLabel, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = c.teal)
                Icon(
                    Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = c.teal,
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        val itemsToShow = if (fallbackScroll) day.items else visibleItems
        val hiddenToShow = if (fallbackScroll) 0 else fit.hiddenCount
        val cellsCount = itemsToShow.size + if (hiddenToShow > 0) 1 else 0

        Column(Modifier.fillMaxWidth()) {
            for (r in 0 until rowPairs) {
                val leftIdx = r * 2
                val rightIdx = leftIdx + 1
                Row(
                    Modifier.fillMaxWidth().height(ROW_HEIGHT_2COL.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (leftIdx < cellsCount) {
                        GlanceCellTwoCol(
                            index = leftIdx,
                            visibleItems = itemsToShow,
                            hiddenCount = hiddenToShow,
                            exercises = exercises,
                            unit = unit,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                    if (rightIdx < cellsCount) {
                        GlanceCellTwoCol(
                            index = rightIdx,
                            visibleItems = itemsToShow,
                            hiddenCount = hiddenToShow,
                            exercises = exercises,
                            unit = unit,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun GlanceCellTwoCol(
    index: Int,
    visibleItems: List<PlanItemEntity>,
    hiddenCount: Int,
    exercises: Map<String, ExerciseEntity>,
    unit: WeightUnit,
    modifier: Modifier = Modifier,
) {
    val c = Fit.colors
    val showDivider = index >= 2
    Column(modifier.height(ROW_HEIGHT_2COL.dp)) {
        if (showDivider) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.divider))
        }
        Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
        ) {
            if (index < visibleItems.size) {
                val item = visibleItems[index]
                val ex = exercises[item.exerciseId]
                val isWarmUp = item.block == WorkoutScore.WARM_UP_BLOCK
                Text(
                    ex?.name ?: item.exerciseId,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isWarmUp) FontWeight.Normal else FontWeight.Medium,
                    color = if (isWarmUp) c.textSecondary else c.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    item.prescription(ex?.kind, unit),
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else if (hiddenCount > 0) {
                Text(
                    pluralStringResource(R.plurals.workouts_more_n, hiddenCount, hiddenCount),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = c.teal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun RestDayCard(
    note: String,
    fallbackScroll: Boolean,
    onAddExercises: () -> Unit,
) {
    val c = Fit.colors
    val hint = note.trim().ifEmpty { stringResource(R.string.workouts_rest_hint) }
    FitCard(
        modifier = Modifier.fillMaxWidth(),
        padding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                hint,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                color = c.textSecondary,
                textAlign = TextAlign.Center,
                maxLines = if (fallbackScroll) 2 else 1,
                overflow = TextOverflow.Ellipsis,
            )
            Box(
                Modifier
                    .heightIn(min = 48.dp)
                    .clip(ForgeShape.Full)
                    .clickable(role = Role.Button, onClick = onAddExercises)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.workouts_add_exercises),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = c.teal,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun BottomActionRow(
    open: SessionEntity?,
    planExists: Boolean,
    isRest: Boolean,
    isToday: Boolean,
    doneToday: Boolean,
    onResume: () -> Unit,
    onBuildPlan: () -> Unit,
    onStart: () -> Unit,
    onQuick: () -> Unit,
) {
    val quickCd = stringResource(R.string.workouts_quick)
    val quickShort = stringResource(R.string.workouts_quick_short)

    when {
        open != null -> {
            PillButton(
                stringResource(R.string.action_resume),
                onClick = onResume,
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Rounded.PlayArrow,
            )
        }
        !planExists -> {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                PillButton(
                    stringResource(R.string.action_build_plan),
                    onClick = onBuildPlan,
                    modifier = Modifier.weight(1f),
                )
                TonalPill(
                    quickShort,
                    onClick = onQuick,
                    icon = Icons.Rounded.Bolt,
                    // Plain semantics: clearAndSetSemantics here would also wipe the pill's own click action for TalkBack.
                    modifier = Modifier.semantics { contentDescription = quickCd },
                )
            }
        }
        isRest -> {
            TonalPill(
                quickCd,
                onClick = onQuick,
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Rounded.Bolt,
            )
        }
        else -> {
            val startLabel = when {
                doneToday && isToday -> stringResource(R.string.today_train_again)
                isToday -> stringResource(R.string.workouts_start)
                else -> stringResource(R.string.workouts_start_this)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                PillButton(
                    startLabel,
                    onClick = onStart,
                    modifier = Modifier.weight(1f),
                    icon = Icons.Rounded.PlayArrow,
                )
                TonalPill(
                    quickShort,
                    onClick = onQuick,
                    icon = Icons.Rounded.Bolt,
                    // Plain semantics: clearAndSetSemantics here would also wipe the pill's own click action for TalkBack.
                    modifier = Modifier.semantics { contentDescription = quickCd },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DayStrip(days: List<PlanDayWithItems>, selected: Int, doneToday: Boolean, onSelect: (Int) -> Unit) {
    val c = Fit.colors
    val today = Dates.todayDayOfWeek()
    val dayRestCd = stringResource(R.string.day_cd_rest)
    val dayTrainingCd = stringResource(R.string.day_cd_training)
    // Seven chips share the width. From 1.35x text they wrap to two rows (four and three) so the full day names,
    // Arabic ones too, never turn into "الث…".
    val chips: @Composable RowScope.() -> Unit = {
        (1..7).forEach { dow ->
            val isSel = dow == selected
            val rest = days.firstOrNull { it.day.dayOfWeek == dow }?.day?.isRest ?: true
            Column(
                Modifier.weight(1f).clip(ForgeShape.Md).background(if (isSel) c.teal else c.card)
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
                Spacer(Modifier.height(8.dp))
                if (doneToday && dow == today && !rest) {
                    Icon(Icons.Rounded.Check, contentDescription = null, tint = if (isSel) c.onTeal else c.teal, modifier = Modifier.size(10.dp))
                } else Box(
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
    if (reflowText()) {
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            maxItemsInEachRow = 4,
        ) { chips() }
    } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) { chips() }
}

@Composable
private fun DayDetailsContent(
    day: PlanDayWithItems,
    exercises: Map<String, ExerciseEntity>,
    onExercise: (String) -> Unit,
    onVideo: (String) -> Unit,
    unit: WeightUnit = WeightUnit.KG,
    trends: Map<String, List<Float>> = emptyMap(),
) {
    val c = Fit.colors
    Text(day.day.title, style = MaterialTheme.typography.titleLarge, color = c.textPrimary)
    if (day.day.note.isNotBlank()) {
        Spacer(Modifier.height(4.dp))
        Text(day.day.note, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
    }
    Spacer(Modifier.height(8.dp))
    val blocks = day.items.groupBy { it.block }
    blocks.forEach { (block, items) ->
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(blockLabel(block), style = MaterialTheme.typography.labelLarge, color = c.teal)
            if (items.first().circuit) {
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Rounded.Repeat, contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text(
                    pluralStringResource(R.plurals.workouts_circuit_rounds, items.first().sets, items.first().sets),
                    style = MaterialTheme.typography.labelMedium,
                    color = c.textSecondary,
                )
            }
        }
        items.forEach { item ->
            val ex = exercises[item.exerciseId]
            Row(
                Modifier.fillMaxWidth().clip(ForgeShape.Sm).clickable { onExercise(item.exerciseId) }.padding(vertical = 8.dp),
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
                    Icon(Icons.Rounded.PlayCircleOutline, contentDescription = stringResource(R.string.cd_watch_form), tint = c.textSecondary)
                }
            }
        }
    }
}

@Composable
private fun HeaderAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Column(
        Modifier.clip(ForgeShape.Sm).clickable(role = Role.Button, onClick = onClick)
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp).padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Fit.colors.textPrimary)
        Text(label, style = MaterialTheme.typography.labelSmall, color = Fit.colors.textSecondary)
    }
}
