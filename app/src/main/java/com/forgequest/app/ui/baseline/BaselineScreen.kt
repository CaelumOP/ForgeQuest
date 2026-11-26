package com.forgequest.app.ui.baseline

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.forgequest.app.R
import com.forgequest.app.container
import com.forgequest.app.data.db.BaselineTestEntity
import com.forgequest.app.data.db.ExerciseEntity
import com.forgequest.app.domain.BaselineTargets
import com.forgequest.app.domain.ExerciseKind
import com.forgequest.app.domain.WeightUnit
import com.forgequest.app.ui.appViewModel
import com.forgequest.app.ui.components.ExerciseThumbnail
import com.forgequest.app.ui.components.FitCard
import com.forgequest.app.ui.components.NumberStepper
import com.forgequest.app.ui.components.PillButton
import com.forgequest.app.ui.components.SegmentedTabs
import com.forgequest.app.ui.components.TonalPill
import com.forgequest.app.ui.components.reflowText
import com.forgequest.app.ui.theme.Fit
import com.forgequest.app.ui.theme.ForgeShape
import com.forgequest.app.ui.theme.ForgeSpace
import com.forgequest.app.ui.theme.tabular
import com.forgequest.app.ui.workouts.ExercisePickerSheet
import com.forgequest.app.ui.workouts.HoldOverlay
import com.forgequest.app.ui.workouts.RestWave
import com.forgequest.app.ui.workouts.clock
import com.forgequest.app.ui.workouts.musclesLabel
import kotlinx.coroutines.delay

@Composable
fun BaselineScreen(
    nav: NavHostController,
    exerciseId: String? = null,
    callerEquipment: Set<com.forgequest.app.domain.Equipment>? = null,
) {
    val vm = appViewModel { BaselineViewModel(it, exerciseId, callerEquipment) }
    val moves by vm.moves.collectAsState()
    val library by vm.library.collectAsState()
    val settings by vm.settings.collectAsState()
    val equipment by vm.userEquipment.collectAsState()
    val bodyType by vm.bodyType.collectAsState()
    val activeExerciseId by vm.activeExerciseId.collectAsState()
    val lastResult by vm.lastResult.collectAsState()
    val holdTimer by vm.hold.collectAsState()
    val restEndsAt by vm.restEndsAt.collectAsState()
    val saving by vm.saving.collectAsState()
    val saveFailed by vm.saveFailed.collectAsState()

    val c = Fit.colors
    var pickerOpen by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    val view = LocalView.current
    DisposableEffect(view, holdTimer != null) {
        val previous = view.keepScreenOn
        if (holdTimer != null) view.keepScreenOn = true
        onDispose { view.keepScreenOn = previous }
    }

    LaunchedEffect(holdTimer, restEndsAt) {
        if (holdTimer != null || restEndsAt != null) {
            while (true) {
                val current = System.currentTimeMillis()
                now = current
                if (holdTimer != null) {
                    vm.onHoldTick(current)
                }
                val end = restEndsAt
                if (end != null && current >= end) {
                    vm.onRestExpired()
                }
                delay(200)
            }
        }
    }

    val activeMove = remember(activeExerciseId, moves, library, settings) {
        val id = activeExerciseId ?: return@remember null
        moves.firstOrNull { it.exercise.id == id }
            ?: library.firstOrNull { it.id == id }?.let { ex ->
                BaselineMoveItem(
                    exercise = ex,
                    latest = null,
                    target = null,
                    unit = settings.weightUnitFor(ex.unitOverride),
                    repsMin = DEFAULT_WEIGHTED_REPS_MIN,
                    isSuggested = false,
                )
            }
    }

    val openedSingleMove = !exerciseId.isNullOrBlank()
    val onBack: () -> Unit = {
        when {
            holdTimer != null -> vm.cancelHold()
            activeMove != null && !openedSingleMove -> vm.closeMove()
            else -> nav.popBackStack()
        }
    }
    BackHandler(onBack = onBack)

    Box(Modifier.fillMaxSize().background(c.background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.cd_back),
                        tint = c.textPrimary,
                    )
                }
                Text(
                    text = activeMove?.exercise?.name ?: stringResource(R.string.baseline_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = c.textPrimary,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
            }

            if (saveFailed) {
                Text(stringResource(R.string.baseline_save_failed), color = c.coral, modifier = Modifier.padding(horizontal = ForgeSpace.Gutter))
            }
            if (activeMove != null) {
                val result = lastResult?.takeIf { it.exercise.id == activeMove.exercise.id }
                if (result != null) {
                    BaselineResultStep(
                        result = result,
                        restEndsAt = restEndsAt,
                        now = now,
                        openedSingleMove = openedSingleMove,
                        onSkipRest = vm::skipRest,
                        onRetest = vm::retest,
                        onTestEasier = { easierId -> vm.openMove(easierId) },
                        onBackToList = vm::closeMove,
                        onDoneSingle = { nav.popBackStack() },
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    key(activeMove.exercise.id) {
                        BaselineTestMoveStep(
                            move = activeMove,
                            saving = saving,
                            onStartHold = { vm.startHold(activeMove.exercise) },
                            onSaveReps = { reps -> vm.saveReps(activeMove.exercise.id, reps) },
                            onSaveHold = { secs -> vm.saveHold(activeMove.exercise.id, secs) },
                            onSaveWeighted = { weight, reps ->
                                vm.saveWeighted(activeMove.exercise.id, weight, reps)
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(
                        start = ForgeSpace.Gutter,
                        end = ForgeSpace.Gutter,
                        top = 4.dp,
                        bottom = 24.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item(key = "intro") {
                        Text(
                            text = stringResource(R.string.baseline_intro),
                            style = MaterialTheme.typography.bodyMedium,
                            color = c.textSecondary,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                    }

                    items(moves, key = { it.exercise.id }) { move ->
                        BaselineMoveRow(
                            move = move,
                            onClick = { vm.openMove(move.exercise.id) },
                        )
                    }

                    item(key = "another") {
                        Spacer(Modifier.height(4.dp))
                        TonalPill(
                            text = stringResource(R.string.baseline_test_another),
                            onClick = { pickerOpen = true },
                            icon = Icons.Rounded.Add,
                            tint = c.teal,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.navigationBarsPadding())
                    }
                }
            }
            if (restEndsAt != null && activeMove == null) {
                restEndsAt?.let { end ->
                    Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = ForgeSpace.Gutter, vertical = 12.dp)) {
                        val left = ((end - now) / 1000L).coerceAtLeast(0L).toInt()
                        BaselineRestBar(secondsLeft = left, restEndsAt = end, onSkip = vm::skipRest)
                    }
                }
            }
        }

        holdTimer?.let { h ->
            HoldOverlay(
                h = h,
                now = now,
                onStop = {
                    val id = activeMove?.exercise?.id ?: return@HoldOverlay
                    vm.stopHold(id, System.currentTimeMillis())
                },
                onCancel = vm::cancelHold,
            )
        }
    }

    if (pickerOpen) {
        ExercisePickerSheet(
            title = stringResource(R.string.baseline_picker_title),
            library = library,
            onPick = { ex ->
                pickerOpen = false
                vm.openMove(ex.id)
            },
            onCreate = { _, _, _ -> },
            onDismiss = { pickerOpen = false },
            userEquipment = equipment,
            allowCreate = false,
            showAllMovesToggle = true,
            bodyType = bodyType,
        )
    }
}

@Composable
private fun BaselineMoveRow(
    move: BaselineMoveItem,
    onClick: () -> Unit,
) {
    val c = Fit.colors
    val tested = move.latest != null
    FitCard(
        onClick = onClick,
        padding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ExerciseThumbnail(move.exercise)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = move.exercise.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = c.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                val lastText = formatLastResult(move.latest, move.exercise.kind, move.unit)
                if (lastText != null) {
                    Text(
                        text = lastText,
                        style = MaterialTheme.typography.bodySmall,
                        color = c.textSecondary,
                    )
                    val targetText = formatTargetText(move.target)
                    if (targetText != null) {
                        Text(
                            text = targetText,
                            style = MaterialTheme.typography.labelMedium,
                            color = c.teal,
                        )
                    }
                    val flag = rowFlagRes(move)
                    if (flag != null) {
                        Text(
                            text = stringResource(flag),
                            style = MaterialTheme.typography.bodySmall,
                            color = c.textSecondary,
                        )
                    }
                } else {
                    Text(
                        text = move.exercise.musclesLabel().ifBlank { stringResource(R.string.baseline_not_tested) },
                        style = MaterialTheme.typography.bodySmall,
                        color = c.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            if (tested) {
                Icon(
                    Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = c.teal,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(4.dp))
            }
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = c.textSecondary,
            )
        }
    }
}

@Composable
private fun BaselineTestMoveStep(
    move: BaselineMoveItem,
    saving: Boolean,
    onStartHold: () -> Unit,
    onSaveReps: (Int) -> Unit,
    onSaveHold: (Int) -> Unit,
    onSaveWeighted: (Float, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Fit.colors
    val haptics = LocalContext.current.container.haptics
    val ex = move.exercise
    var modeIndex by rememberSaveable { mutableIntStateOf(0) } // 0 = Guided, 1 = Manual

    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = ForgeSpace.Gutter)
            .navigationBarsPadding()
            .padding(top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = ex.musclesLabel(),
            style = MaterialTheme.typography.bodySmall,
            color = c.textSecondary,
        )

        when (ex.kind) {
            ExerciseKind.REPS -> {
                SegmentedTabs(
                    options = listOf(
                        stringResource(R.string.baseline_mode_guided),
                        stringResource(R.string.baseline_mode_manual),
                    ),
                    selected = modeIndex,
                    onSelect = { modeIndex = it },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
                if (modeIndex == 0) {
                    var count by rememberSaveable { mutableIntStateOf(0) }
                    val tapCd = stringResource(R.string.baseline_reps_counter_cd, count)
                    FitCard {
                        Column(
                            Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Surface(
                                onClick = {
                                    haptics.check()
                                    if (count < BaselineTargets.MAX_REPS_INPUT) count++
                                },
                                shape = ForgeShape.Lg,
                                color = c.tealTrack,
                                contentColor = c.teal,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 180.dp)
                                    .semantics { contentDescription = tapCd },
                            ) {
                                Column(
                                    Modifier.fillMaxWidth().padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                ) {
                                    Text(
                                        text = count.toString(),
                                        style = MaterialTheme.typography.displayLarge.tabular(),
                                        color = c.teal,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        text = stringResource(R.string.baseline_tap_after_rep),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = c.textPrimary,
                                        textAlign = TextAlign.Center,
                                    )
                                }
                            }
                            if (count > 0) {
                                TextButton(
                                    onClick = {
                                        haptics.tick()
                                        count = (count - 1).coerceAtLeast(0)
                                    },
                                    modifier = Modifier.heightIn(min = 48.dp),
                                ) {
                                    Icon(Icons.Rounded.Remove, contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.baseline_undo_rep), color = c.textSecondary)
                                }
                            }
                        }
                    }
                    PillButton(
                        text = stringResource(R.string.hold_done),
                        onClick = { onSaveReps(count) },
                        enabled = !saving,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    var manualReps by rememberSaveable { mutableIntStateOf(move.latest?.reps ?: 0) }
                    FitCard {
                        Column(
                            Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.baseline_reps_label),
                                style = MaterialTheme.typography.bodyLarge,
                                color = c.textPrimary,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            NumberStepper(
                                value = manualReps.toFloat(),
                                onValueChange = { manualReps = BaselineTargets.clampRepsInput(it.toInt()) },
                                step = 1f,
                                min = 0f,
                                max = BaselineTargets.MAX_REPS_INPUT.toFloat(),
                                unit = stringResource(R.string.unit_reps),
                                title = stringResource(R.string.stepper_reps),
                                minValueWidth = 72.dp,
                                unitPlurals = R.plurals.rx_reps,
                            )
                        }
                    }
                    PillButton(
                        text = stringResource(R.string.action_save),
                        onClick = { onSaveReps(manualReps) },
                        enabled = !saving,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            ExerciseKind.HOLD -> {
                SegmentedTabs(
                    options = listOf(
                        stringResource(R.string.baseline_mode_guided),
                        stringResource(R.string.baseline_mode_manual),
                    ),
                    selected = modeIndex,
                    onSelect = { modeIndex = it },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
                if (modeIndex == 0) {
                    FitCard {
                        Column(
                            Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Box(
                                Modifier.size(56.dp).clip(CircleShape).background(c.tealTrack),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Rounded.Timer, contentDescription = null, tint = c.teal, modifier = Modifier.size(28.dp))
                            }
                            Text(
                                text = stringResource(R.string.baseline_hold_guide_hint),
                                style = MaterialTheme.typography.bodyMedium,
                                color = c.textSecondary,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                    PillButton(
                        text = stringResource(R.string.baseline_start_hold),
                        onClick = onStartHold,
                        icon = Icons.Rounded.PlayArrow,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    var manualSeconds by rememberSaveable {
                        mutableIntStateOf(move.latest?.seconds?.takeIf { it > 0 } ?: 30)
                    }
                    FitCard {
                        Column(
                            Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.baseline_seconds_label),
                                style = MaterialTheme.typography.bodyLarge,
                                color = c.textPrimary,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            NumberStepper(
                                value = manualSeconds.toFloat(),
                                onValueChange = { manualSeconds = BaselineTargets.clampHoldInput(it.toInt()) },
                                step = 5f,
                                min = 0f,
                                max = BaselineTargets.MAX_HOLD_INPUT.toFloat(),
                                unit = stringResource(R.string.unit_s),
                                title = stringResource(R.string.stepper_hold_time),
                                minValueWidth = 72.dp,
                            )
                        }
                    }
                    PillButton(
                        text = stringResource(R.string.action_save),
                        onClick = { onSaveHold(manualSeconds) },
                        enabled = !saving && manualSeconds > 0,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            ExerciseKind.WEIGHTED -> {
                val unit = move.unit
                var displayWeight by rememberSaveable {
                    mutableFloatStateOf(
                        move.latest?.weightKg?.takeIf { it > 0f }?.let { unit.fromKg(it) } ?: 0f,
                    )
                }
                var reps by rememberSaveable {
                    mutableIntStateOf(move.latest?.reps?.takeIf { it > 0 } ?: 8)
                }
                Text(
                    text = stringResource(R.string.baseline_weighted_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.textSecondary,
                )
                FitCard {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Column(
                            Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.baseline_weight_label),
                                style = MaterialTheme.typography.bodyLarge,
                                color = c.textPrimary,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            NumberStepper(
                                value = displayWeight,
                                onValueChange = { displayWeight = it.coerceIn(0f, unit.fromKg(BaselineTargets.MAX_WEIGHT_KG_INPUT.toFloat())) },
                                step = unit.loadStep,
                                min = 0f,
                                max = unit.fromKg(BaselineTargets.MAX_WEIGHT_KG_INPUT.toFloat()),
                                unit = unit.label,
                                decimals = 1,
                                title = stringResource(R.string.stepper_weight),
                                minValueWidth = 84.dp,
                            )
                        }
                        Column(
                            Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.baseline_reps_label),
                                style = MaterialTheme.typography.bodyLarge,
                                color = c.textPrimary,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            NumberStepper(
                                value = reps.toFloat(),
                                onValueChange = { reps = BaselineTargets.clampWeightedRepsInput(it.toInt()).coerceAtLeast(1) },
                                step = 1f,
                                min = 1f,
                                max = BaselineTargets.MAX_WEIGHTED_REPS_INPUT.toFloat(),
                                unit = stringResource(R.string.unit_reps),
                                title = stringResource(R.string.stepper_reps),
                                minValueWidth = 72.dp,
                                unitPlurals = R.plurals.rx_reps,
                            )
                        }
                    }
                }
                PillButton(
                    text = stringResource(R.string.action_save),
                    onClick = { onSaveWeighted(displayWeight, reps) },
                    enabled = !saving && displayWeight > 0f && reps > 0,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun BaselineResultStep(
    result: BaselineSaveResult,
    restEndsAt: Long?,
    now: Long,
    openedSingleMove: Boolean,
    onSkipRest: () -> Unit,
    onRetest: () -> Unit,
    onTestEasier: (String) -> Unit,
    onBackToList: () -> Unit,
    onDoneSingle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Fit.colors
    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = ForgeSpace.Gutter)
            .navigationBarsPadding()
            .padding(top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        FitCard {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = result.exercise.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = c.textPrimary,
                    modifier = Modifier.semantics { heading() },
                )
                formatLastResult(result.test, result.exercise.kind, result.unit)?.let { recorded ->
                    Text(
                        text = recorded,
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.textSecondary,
                    )
                }
                val targetText = formatTargetText(result.target)
                when (val t = result.target) {
                    is BaselineTargetDisplay.Reps,
                    is BaselineTargetDisplay.Hold,
                    is BaselineTargetDisplay.Weight -> {
                        if (targetText != null) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(ForgeShape.Md)
                                    .background(c.tealTrack)
                                    .padding(16.dp),
                            ) {
                                Text(
                                    text = targetText,
                                    style = MaterialTheme.typography.titleLarge,
                                    color = c.teal,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                            val harder = when (t) {
                                is BaselineTargetDisplay.Reps -> t.harderExercise
                                is BaselineTargetDisplay.Hold -> t.harderExercise
                                else -> null
                            }
                            val easierSuggestion = when (t) {
                                is BaselineTargetDisplay.Reps -> t.easierExercise
                                is BaselineTargetDisplay.Hold -> t.easierExercise
                                else -> null
                            }
                            if (harder != null) {
                                Text(
                                    text = stringResource(R.string.baseline_outgrown_try_harder, harder.name),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = c.textPrimary,
                                )
                                Spacer(Modifier.height(4.dp))
                                PillButton(
                                    text = stringResource(R.string.baseline_test_move_named, harder.name),
                                    onClick = { onTestEasier(harder.id) },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            if (easierSuggestion != null) {
                                Text(
                                    text = stringResource(R.string.baseline_try_easier, easierSuggestion.name),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = c.textPrimary,
                                )
                                Spacer(Modifier.height(4.dp))
                                PillButton(
                                    text = stringResource(R.string.baseline_test_move_named, easierSuggestion.name),
                                    onClick = { onTestEasier(easierSuggestion.id) },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                    is BaselineTargetDisplay.EasierVariation -> {
                        val easier = t.easierExercise
                        Text(
                            text = if (easier != null) {
                                stringResource(R.string.baseline_try_easier, easier.name)
                            } else {
                                stringResource(R.string.baseline_try_easier_generic)
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = c.textPrimary,
                        )
                        if (easier != null) {
                            Spacer(Modifier.height(4.dp))
                            PillButton(
                                text = stringResource(R.string.baseline_test_move_named, easier.name),
                                onClick = { onTestEasier(easier.id) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    BaselineTargetDisplay.TooLight,
                    BaselineTargetDisplay.TooHeavy,
                    BaselineTargetDisplay.WeightNoTarget,
                    is BaselineTargetDisplay.HoldTooShort -> {
                        if (targetText != null) {
                            Text(
                                text = targetText,
                                style = MaterialTheme.typography.titleMedium,
                                color = c.textSecondary,
                            )
                        }
                        val holdEasier = (t as? BaselineTargetDisplay.HoldTooShort)?.easierExercise
                        if (holdEasier != null) {
                            Text(
                                text = stringResource(R.string.baseline_try_easier, holdEasier.name),
                                style = MaterialTheme.typography.titleMedium,
                                color = c.textPrimary,
                            )
                            Spacer(Modifier.height(4.dp))
                            PillButton(
                                text = stringResource(R.string.baseline_test_move_named, holdEasier.name),
                                onClick = { onTestEasier(holdEasier.id) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        TonalPill(
                            text = stringResource(R.string.baseline_retest),
                            onClick = onRetest,
                            tint = c.teal,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }

        if (restEndsAt != null) {
            val left = ((restEndsAt - now) / 1000L).coerceAtLeast(0L).toInt()
            BaselineRestBar(secondsLeft = left, restEndsAt = restEndsAt, onSkip = onSkipRest)
        }

        if (openedSingleMove) {
            PillButton(
                text = stringResource(R.string.hold_done),
                onClick = onDoneSingle,
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(
                onClick = onBackToList,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.baseline_see_all_moves), color = c.teal)
            }
        } else {
            PillButton(
                text = stringResource(R.string.baseline_back_to_list),
                onClick = onBackToList,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
internal fun BaselineRestBar(
    secondsLeft: Int,
    restEndsAt: Long,
    onSkip: () -> Unit,
) {
    val c = Fit.colors
    val reflow = reflowText()
    Column(
        Modifier
            .fillMaxWidth()
            .clip(ForgeShape.Md)
            .background(c.cardHigh)
            .padding(16.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.baseline_rest_between),
                    style = MaterialTheme.typography.labelMedium,
                    color = c.textSecondary,
                )
                Text(
                    text = clock(secondsLeft),
                    style = MaterialTheme.typography.titleLarge.tabular(),
                    color = c.blue,
                    maxLines = 1,
                    softWrap = false,
                )
            }
            if (!reflow) {
                TextButton(
                    onClick = onSkip,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.live_skip), color = c.teal, maxLines = 1)
                }
            }
        }
        if (reflow) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(
                    onClick = onSkip,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.live_skip), color = c.teal, maxLines = 1)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        RestWave(endsAt = restEndsAt, totalSeconds = BASELINE_REST_SECONDS)
    }
}

@Composable
private fun formatLastResult(
    test: BaselineTestEntity?,
    kind: ExerciseKind,
    unit: WeightUnit,
): String? {
    if (test == null) return null
    return when (kind) {
        ExerciseKind.REPS -> {
            val r = test.reps ?: return null
            pluralStringResource(R.plurals.baseline_last_reps, r, r)
        }
        ExerciseKind.HOLD -> {
            val s = test.seconds ?: return null
            stringResource(R.string.baseline_last_seconds, BaselineTargets.formatHoldSeconds(s, stringResource(R.string.unit_s)))
        }
        ExerciseKind.WEIGHTED -> {
            val w = test.weightKg ?: return null
            val r = test.reps ?: return null
            stringResource(R.string.baseline_last_weight, unit.format(w), r)
        }
    }
}

@Composable
private fun formatTargetText(target: BaselineTargetDisplay?): String? = when (target) {
    null -> null
    is BaselineTargetDisplay.Reps -> pluralStringResource(R.plurals.baseline_start_reps, target.reps, target.reps)
    is BaselineTargetDisplay.Hold -> stringResource(
        R.string.baseline_start_seconds,
        BaselineTargets.formatHoldSeconds(target.seconds, stringResource(R.string.unit_s)),
    )
    is BaselineTargetDisplay.Weight -> stringResource(R.string.baseline_start_weight, target.unit.format(target.weightKg))
    is BaselineTargetDisplay.EasierVariation -> {
        val easier = target.easierExercise
        if (easier != null) stringResource(R.string.baseline_try_easier, easier.name)
        else stringResource(R.string.baseline_try_easier_generic)
    }
    BaselineTargetDisplay.TooLight,
    BaselineTargetDisplay.WeightNoTarget -> stringResource(R.string.baseline_too_light)
    BaselineTargetDisplay.TooHeavy -> stringResource(R.string.baseline_too_heavy)
    is BaselineTargetDisplay.HoldTooShort -> stringResource(R.string.baseline_hold_zero_hint)
}

/** Subtle second line on a summary row: too easy (outgrown) or hard start (too low), null otherwise. */
private fun rowFlagRes(move: BaselineMoveItem): Int? {
    val latest = move.latest ?: return null
    val value = when (move.exercise.kind) {
        ExerciseKind.REPS -> latest.reps
        ExerciseKind.HOLD -> latest.seconds
        ExerciseKind.WEIGHTED -> null
    } ?: return null
    return when {
        BaselineTargets.isOutgrown(move.exercise.kind, value) -> R.string.baseline_row_too_easy
        BaselineTargets.isTooHard(move.exercise.kind, value) -> R.string.baseline_row_too_hard
        else -> null
    }
}
