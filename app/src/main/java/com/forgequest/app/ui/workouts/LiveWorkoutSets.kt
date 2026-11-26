package com.forgequest.app.ui.workouts

import androidx.annotation.PluralsRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Healing
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.forgequest.app.R
import com.forgequest.app.data.db.ExerciseEntity
import com.forgequest.app.data.db.SetEntity
import com.forgequest.app.domain.ExerciseKind
import com.forgequest.app.domain.Muscle
import com.forgequest.app.domain.PlateMath
import com.forgequest.app.domain.SetEffort
import com.forgequest.app.domain.SetType
import com.forgequest.app.domain.WeightUnit
import com.forgequest.app.ui.components.ChoiceChip
import com.forgequest.app.ui.components.KeypadNumberStepper
import com.forgequest.app.ui.components.Stepper
import com.forgequest.app.ui.components.formatNumber
import com.forgequest.app.ui.components.rememberFullSheetState
import com.forgequest.app.ui.components.steadyToggleable
import com.forgequest.app.ui.captionRes
import com.forgequest.app.ui.labelRes
import com.forgequest.app.ui.theme.Fit
import com.forgequest.app.ui.theme.ForgeMotion
import com.forgequest.app.ui.theme.ForgeQuestTheme
import com.forgequest.app.ui.theme.ForgeShape
import com.forgequest.app.ui.theme.ForgeSpace
import com.forgequest.app.ui.theme.LocalReducedMotion
import com.forgequest.app.ui.theme.effectsSpec
import com.forgequest.app.ui.theme.spatialSpec
import com.forgequest.app.ui.theme.tabular
import com.forgequest.app.ui.workouts.LiveWorkoutViewModel.Field

internal fun parseSetType(raw: String?): SetType =
    SetType.entries.firstOrNull { it.name == raw } ?: SetType.WORKING

internal fun showPrPill(set: SetEntity, livePr: Boolean): Boolean =
    set.done && (set.isPr || livePr)

@Composable
private fun setTypeLabel(type: SetType): String = stringResource(
    when (type) {
        SetType.WORKING -> R.string.live41_set_type_working
        SetType.WARMUP -> R.string.live41_set_type_warmup
        SetType.DROPSET -> R.string.live41_set_type_dropset
        SetType.FAILURE -> R.string.live41_set_type_failure
        SetType.RESTPAUSE -> R.string.live41_set_type_restpause
    },
)

@Composable
private fun setTypeTag(type: SetType): String? = when (type) {
    SetType.WORKING -> null
    SetType.WARMUP -> stringResource(R.string.live41_tag_warmup)
    SetType.DROPSET -> stringResource(R.string.live41_tag_dropset)
    SetType.FAILURE -> stringResource(R.string.live41_tag_failure)
    SetType.RESTPAUSE -> stringResource(R.string.live41_tag_restpause)
}

@Composable
internal fun SetRow(
    label: String?,
    sublabel: String?,
    set: SetEntity,
    kind: ExerciseKind,
    exercise: ExerciseEntity? = null,
    vm: LiveWorkoutViewModel,
    onLabelClick: (() -> Unit)? = null,
    previous: SetEntity? = null,
) {
    val settings by vm.settings.collectAsState()
    val effectiveUnit = settings.weightUnitFor(exercise?.unitOverride.orEmpty())
    val prIds by vm.prSetIds.collectAsState()
    val pr = set.isPr || set.id in prIds
    val flashingId by vm.newBest.collectAsState()
    SetRowContent(
        label = label,
        sublabel = sublabel,
        set = set,
        kind = kind,
        effectiveUnit = effectiveUnit,
        pr = pr,
        flashing = flashingId == set.id,
        exercise = exercise,
        barWeightKg = settings.barWeightKg,
        plateInventory = settings.plateInventory,
        previous = previous,
        onLabelClick = onLabelClick,
        onToggle = { vm.toggle(set) },
        onStartHold = { vm.startHold(set) },
        onFieldChange = { field, value -> vm.setField(set, field, value) },
        onNoteSave = { vm.setNote(set, it) },
        onSetTypeSelect = { vm.setSetType(set, it) },
        onReopenEffort = { vm.reopenEffort(set.id) },
    )
}

@OptIn(ExperimentalFoundationApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun SetRowContent(
    label: String?,
    sublabel: String?,
    set: SetEntity,
    kind: ExerciseKind,
    effectiveUnit: WeightUnit,
    pr: Boolean,
    flashing: Boolean,
    exercise: ExerciseEntity? = null,
    barWeightKg: Float = 20f,
    plateInventory: List<Float> = emptyList(),
    previous: SetEntity? = null,
    onLabelClick: (() -> Unit)? = null,
    onToggle: () -> Unit = {},
    onStartHold: () -> Unit = {},
    onFieldChange: (Field, Float) -> Unit = { _, _ -> },
    onNoteSave: (String) -> Unit = {},
    onSetTypeSelect: (SetType) -> Unit = {},
    onReopenEffort: () -> Unit = {},
) {
    val c = Fit.colors
    val scheme = MaterialTheme.colorScheme
    // Ticked rows tint primaryContainer; a PR set tints tertiaryContainer with a thin gold edge.
    val rowColor by animateColorAsState(
        when { set.done && pr -> scheme.tertiaryContainer; set.done -> scheme.primaryContainer; else -> Color.Transparent },
        effectsSpec(), label = "row",
    )
    val rowBorder by animateColorAsState(if (set.done && pr) scheme.tertiary else Color.Transparent, effectsSpec(), label = "rowBorder")
    val flashColor by animateColorAsState(if (flashing) c.gold else c.textPrimary, label = "flash")
    var noteDialog by remember { mutableStateOf(false) }
    var typeSheet by remember { mutableStateOf(false) }

    if (noteDialog) {
        SetNoteDialog(
            initial = set.note,
            isPain = set.effort == SetEffort.PAIN.name,
            onDismiss = { noteDialog = false },
            onSave = { onNoteSave(it); noteDialog = false },
        )
    }
    val setType = parseSetType(set.setType)
    if (typeSheet) {
        SetTypeSheet(
            selected = setType,
            onSelect = { chosen -> onSetTypeSelect(chosen); typeSheet = false },
            onDismiss = { typeSheet = false },
        )
    }

    val effort = SetEffort.parse(set.effort.ifBlank { null })
    // From 1.35x text the row splits in two tiers: set number, last-time hint and tick on top, the steppers below.
    val reflow = com.forgequest.app.ui.components.reflowText()
    val rowModifier = Modifier.fillMaxWidth().padding(top = 8.dp).clip(ForgeShape.Sm).background(rowColor).border(1.dp, rowBorder, ForgeShape.Sm)
        .combinedClickable(onClick = {}, onLongClick = { noteDialog = true }, onLongClickLabel = stringResource(R.string.cd_add_note))
        .padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 8.dp)

    val tagText = setTypeTag(setType)
    val badgeBg = when (setType) {
        SetType.WORKING -> c.cardHigh
        SetType.WARMUP -> c.cardHigh
        SetType.DROPSET -> c.purpleTrack
        SetType.FAILURE -> c.goldTrack
        SetType.RESTPAUSE -> c.blueTrack
    }
    val badgeInk = when (setType) {
        SetType.WORKING -> c.textSecondary
        SetType.WARMUP -> c.textSecondary
        SetType.DROPSET -> c.purple
        SetType.FAILURE -> c.gold
        SetType.RESTPAUSE -> c.blue
    }
    val badgeText = tagText ?: label ?: "${set.setIndex + 1}"
    val badgeDesc = stringResource(R.string.live41_set_badge_cd, label ?: "${set.setIndex + 1}", setTypeLabel(setType))

    val number: @Composable () -> Unit = {
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .clickable(role = Role.Button) { typeSheet = true }
                .semantics { contentDescription = badgeDesc },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(badgeBg),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    badgeText,
                    style = MaterialTheme.typography.labelMedium.tabular(),
                    color = badgeInk,
                    fontWeight = if (tagText != null) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                )
            }
        }
        Spacer(Modifier.width(4.dp))
    }
    val info: @Composable (Modifier) -> Unit = { m ->
        Column(
            m.let { if (onLabelClick != null) it.clip(ForgeShape.Sm).clickable(onClick = onLabelClick) else it },
        ) {
            if (sublabel != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(sublabel, style = MaterialTheme.typography.bodyMedium, color = c.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (onLabelClick != null) Icon(Icons.Rounded.ExpandMore, contentDescription = stringResource(R.string.cd_options), tint = c.textSecondary, modifier = Modifier.size(18.dp))
                }
            }
            if (setType != SetType.WORKING) {
                Text(setTypeLabel(setType), style = MaterialTheme.typography.labelSmall, color = badgeInk, fontWeight = FontWeight.Medium)
            }
            if (previous != null) {
                val last = when (kind) {
                    ExerciseKind.HOLD -> stringResource(R.string.rx_seconds, previous.seconds ?: 0)
                    ExerciseKind.WEIGHTED -> "${previous.reps ?: 0} × ${effectiveUnit.format(previous.weightKg ?: 0f)}"
                    ExerciseKind.REPS -> "${previous.reps ?: 0}"
                }
                Text(stringResource(R.string.live_last_time, last), style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                if (set.targetLabel.isNotBlank() && sublabel != null) {
                    Text(localizeTargetLabel(set.targetLabel, stringResource(R.string.rx_reps_only)), style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
                }
                if (showPrPill(set, pr)) {
                    PrTag()
                }
                if (set.effort.isNotBlank() && effort != SetEffort.OK) {
                    if (set.done) {
                        val editEffortLabel = stringResource(R.string.cd_edit_effort)
                        Box(
                            Modifier
                                .heightIn(min = 48.dp)
                                .widthIn(min = 48.dp)
                                .clip(ForgeShape.Full)
                                .clickable(role = Role.Button, onClickLabel = editEffortLabel, onClick = onReopenEffort)
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                stringResource(effort.labelRes),
                                style = MaterialTheme.typography.labelSmall,
                                color = effortColor(effort),
                            )
                        }
                    } else {
                        Text(
                            stringResource(effort.labelRes),
                            style = MaterialTheme.typography.labelSmall,
                            color = effortColor(effort),
                        )
                    }
                }
            }
            if (set.note.isNotBlank()) {
                Text(set.note, style = MaterialTheme.typography.labelSmall, color = c.blue, maxLines = 2, overflow = TextOverflow.Ellipsis, fontStyle = FontStyle.Italic)
            }
        }
    }

    val isBarbell = remember(exercise?.id, exercise?.equipment) { PlateMath.isBarbellExercise(exercise) }
    val plateCalculator: ((Float) -> PlateMath.Breakdown)? = remember(isBarbell, effectiveUnit, barWeightKg, plateInventory) {
        if (!isBarbell) null
        else { displayWeight ->
            PlateMath.calculate(effectiveUnit.toKg(displayWeight), effectiveUnit, barWeightKg, plateInventory)
        }
    }
    val weightQuickDeltas = remember(effectiveUnit) {
        if (effectiveUnit == WeightUnit.LB) listOf(5f, 10f, 25f) else listOf(2.5f, 5f, 10f)
    }

    val steppers: @Composable () -> Unit = {
        when (kind) {
            ExerciseKind.HOLD -> Row(verticalAlignment = Alignment.CenterVertically) {
                if (!set.done) IconButton(onClick = onStartHold) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = stringResource(R.string.cd_start_hold), tint = c.teal)
                }
                SetRowStepper(
                    (set.seconds ?: 0).toFloat(), { onFieldChange(Field.SECONDS, it) }, step = 1f, min = 0f, max = 3600f,
                    unit = stringResource(R.string.unit_s), title = stringResource(R.string.stepper_hold_time), minValueWidth = 52.dp, valueColor = flashColor,
                    quickDeltas = listOf(5f, 10f, 30f),
                    lastTimeValue = previous?.seconds?.toFloat(),
                    lastTimeLabel = previous?.seconds?.let { stringResource(R.string.rx_seconds, it) },
                )
            }
            // "reps" beside the number: a bare 8 read as seconds or kilos to beginners.
            ExerciseKind.REPS -> SetRowStepper(
                (set.reps ?: 0).toFloat(), { onFieldChange(Field.REPS, it) }, step = 1f, min = 0f, max = 999f,
                unit = stringResource(R.string.unit_reps), title = stringResource(R.string.stepper_reps), minValueWidth = 64.dp, valueColor = flashColor,
                unitPlurals = R.plurals.rx_reps,
                quickDeltas = listOf(1f, 2f, 5f),
                lastTimeValue = previous?.reps?.toFloat(),
            )
            ExerciseKind.WEIGHTED -> {
                SetRowStepper(
                    (set.reps ?: 0).toFloat(), { onFieldChange(Field.REPS, it) }, step = 1f, min = 0f, max = 999f,
                    unit = stringResource(R.string.unit_reps), title = stringResource(R.string.stepper_reps), minValueWidth = 64.dp, valueColor = flashColor,
                    unitPlurals = R.plurals.rx_reps,
                    quickDeltas = listOf(1f, 2f, 5f),
                    lastTimeValue = previous?.reps?.toFloat(),
                )
                val lastWeight = previous?.weightKg?.takeIf { it > 0f }
                SetRowStepper(
                    effectiveUnit.fromKg(set.weightKg ?: 0f), { onFieldChange(Field.WEIGHT, effectiveUnit.toKg(it)) }, step = effectiveUnit.loadStep, min = 0f, max = effectiveUnit.fromKg(999f),
                    unit = effectiveUnit.label, decimals = 1, title = stringResource(R.string.stepper_weight), minValueWidth = 64.dp, valueColor = if (flashing) flashColor else c.blue,
                    placeholder = if ((set.weightKg ?: 0f) <= 0f) stringResource(R.string.live_add_weight) else null,
                    quickDeltas = weightQuickDeltas,
                    lastTimeValue = lastWeight?.let { effectiveUnit.fromKg(it) },
                    lastTimeLabel = lastWeight?.let { effectiveUnit.format(it) },
                    plateCalculator = plateCalculator,
                )
            }
        }
        // Bodyweight moves with weight added in the plan editor (a plate on the back, a vest).
        val extra = set.weightKg
        if (kind != ExerciseKind.WEIGHTED && extra != null) {
            val lastExtra = previous?.weightKg?.takeIf { it > 0f }
            SetRowStepper(
                effectiveUnit.fromKg(extra), { onFieldChange(Field.WEIGHT, effectiveUnit.toKg(it)) }, step = effectiveUnit.loadStep, min = 0f, max = effectiveUnit.fromKg(999f),
                unit = effectiveUnit.label, decimals = 1, title = stringResource(R.string.stepper_weight), minValueWidth = 64.dp, valueColor = c.blue,
                quickDeltas = weightQuickDeltas,
                lastTimeValue = lastExtra?.let { effectiveUnit.fromKg(it) },
                lastTimeLabel = lastExtra?.let { effectiveUnit.format(it) },
            )
        }
    }
    val tick: @Composable () -> Unit = {
        SetTick(set.done, pr, onToggle)
    }
    Column {
        if (reflow) {
            Column(rowModifier) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    number()
                    info(Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    tick()
                }
                FlowRow(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) { steppers() }
            }
        } else Row(rowModifier, verticalAlignment = Alignment.CenterVertically) {
            number()
            info(Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) { steppers() }
            Spacer(Modifier.width(8.dp))
            tick()
        }
    }
}

@Composable
private fun SetRowStepper(
    value: Float,
    onValueChange: (Float) -> Unit,
    step: Float,
    min: Float,
    max: Float,
    unit: String = "",
    decimals: Int = 0,
    title: String = stringResource(R.string.stepper_enter_value),
    valueColor: Color = Fit.colors.textPrimary,
    minValueWidth: Dp = 40.dp,
    @PluralsRes unitPlurals: Int? = null,
    quickDeltas: List<Float> = emptyList(),
    lastTimeValue: Float? = null,
    lastTimeLabel: String? = null,
    placeholder: String? = null,
    plateCalculator: ((Float) -> PlateMath.Breakdown)? = null,
) {
    if (LocalInspectionMode.current) {
        val shown = placeholder ?: (formatNumber(value, decimals) + if (unit.isNotEmpty()) " $unit" else "")
        Stepper(
            value = shown,
            onMinus = {},
            onPlus = {},
            valueColor = valueColor,
            minValueWidth = minValueWidth,
            onValueClick = {},
        )
    } else {
        KeypadNumberStepper(
            value = value,
            onValueChange = onValueChange,
            step = step,
            min = min,
            max = max,
            unit = unit,
            decimals = decimals,
            title = title,
            valueColor = valueColor,
            minValueWidth = minValueWidth,
            unitPlurals = unitPlurals,
            quickDeltas = quickDeltas,
            lastTimeValue = lastTimeValue,
            lastTimeLabel = lastTimeLabel,
            placeholder = placeholder,
            plateCalculator = plateCalculator,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SetTypeSheet(
    selected: SetType,
    onSelect: (SetType) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = Fit.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberFullSheetState(),
        containerColor = c.card,
        shape = RoundedCornerShape(topStart = ForgeShape.LgRadius, topEnd = ForgeShape.LgRadius),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.live41_set_type_title), style = MaterialTheme.typography.titleLarge, color = c.textPrimary, modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.cd_close), tint = c.textSecondary)
                }
            }
            SetType.entries.forEach { type ->
                val isSel = type == selected
                val desc = stringResource(
                    when (type) {
                        SetType.WORKING -> R.string.live41_set_type_working_desc
                        SetType.WARMUP -> R.string.live41_set_type_warmup_desc
                        SetType.DROPSET -> R.string.live41_set_type_dropset_desc
                        SetType.FAILURE -> R.string.live41_set_type_failure_desc
                        SetType.RESTPAUSE -> R.string.live41_set_type_restpause_desc
                    },
                )
                Surface(
                    onClick = { onSelect(type) },
                    shape = ForgeShape.Md,
                    color = if (isSel) c.tealTrack else c.cardHigh,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                ) {
                    Row(
                        Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val tag = setTypeTag(type)
                        Box(
                            Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(if (isSel) c.teal else c.card),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                tag ?: "1",
                                style = MaterialTheme.typography.labelMedium.tabular(),
                                color = if (isSel) c.onTeal else c.textSecondary,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(setTypeLabel(type), style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
                            Text(desc, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                        }
                        if (isSel) {
                            Icon(Icons.Rounded.Check, contentDescription = null, tint = c.teal, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
    }
}

/** Easy teal, Hard purple, Failure gold, Pain coral. */
@Composable
internal fun effortColor(e: SetEffort) = Fit.colors.let { c ->
    when (e) { SetEffort.EASY -> c.teal; SetEffort.OK -> c.blue; SetEffort.HARD -> c.purple; SetEffort.FAILURE -> c.gold; SetEffort.PAIN -> c.coral }
}

/**
 * Easy · Hard · Failure as one connected group (reps left: 3+, 1-2, 0), each with a small caption, then a separate
 * "it hurt" button for Pain. The chosen chip morphs into a full pill and fills with its colour. Tapping it again clears
 * it (the view model decides that).
 */
@Composable
internal fun EffortGroup(selected: String, onSelect: (SetEffort) -> Unit) {
    val c = Fit.colors
    val entries = SetEffort.chips
    Row(Modifier.fillMaxWidth().padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.weight(1f).height(IntrinsicSize.Min).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            entries.forEachIndexed { i, e ->
                val isSel = SetEffort.chipOf(selected) == e.name
                val full = 24.dp // half the 48 dp minimum height
                val seam = 6.dp
                val startCorner by animateDpAsState(if (isSel || i == 0) full else seam, spatialSpec(ForgeMotion.Springs.FastSpatial, Dp.VisibilityThreshold), label = "effortStart")
                val endCorner by animateDpAsState(if (isSel || i == entries.lastIndex) full else seam, spatialSpec(ForgeMotion.Springs.FastSpatial, Dp.VisibilityThreshold), label = "effortEnd")
                val accent = effortColor(e)
                val fill by animateColorAsState(if (isSel) accent else c.cardHigh, effectsSpec(), label = "effortFill")
                val ink by animateColorAsState(if (isSel) (if (accent == c.teal) c.onTeal else c.background) else c.textPrimary, effectsSpec(), label = "effortInk")
                val shape = RoundedCornerShape(topStart = startCorner, bottomStart = startCorner, topEnd = endCorner, bottomEnd = endCorner)
                Column(
                    Modifier.weight(1f).fillMaxHeight().heightIn(min = 48.dp).clip(shape).background(fill)
                        .selectable(selected = isSel, role = Role.RadioButton) { onSelect(e) }
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(stringResource(e.labelRes), style = MaterialTheme.typography.labelLarge, color = ink, textAlign = TextAlign.Center)
                    Text(stringResource(e.captionRes), style = MaterialTheme.typography.labelSmall, color = ink, textAlign = TextAlign.Center)
                }
            }
        }
        val hurt = selected == SetEffort.PAIN.name
        val painLabel = stringResource(R.string.effort41_pain_cd)
        Box(
            Modifier.size(48.dp).clip(CircleShape).background(if (hurt) c.coral else c.cardHigh)
                .selectable(selected = hurt, role = Role.Button) { onSelect(SetEffort.PAIN) }
                .semantics { contentDescription = painLabel },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Healing, contentDescription = null, tint = if (hurt) c.background else c.coral)
        }
    }
}

/** Long-press a set to jot something down, or add a pain location note after tapping "It hurt". */
@Composable
fun SetNoteDialog(initial: String, isPain: Boolean = false, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    val c = Fit.colors
    var text by remember { mutableStateOf(initial) }
    val quickTags = if (isPain) {
        listOf(
            stringResource(R.string.v42_pain_tag_sharp) to stringResource(R.string.v42_pain_tag_sharp),
            stringResource(R.string.v42_pain_tag_ache) to stringResource(R.string.v42_pain_tag_ache),
            stringResource(R.string.v42_pain_tag_joint) to stringResource(R.string.v42_pain_tag_joint),
        )
    } else {
        emptyList()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.card,
        title = {
            Text(
                stringResource(if (isPain) R.string.v42_pain_note_title else R.string.live_note_title),
                color = c.textPrimary,
            )
        },
        text = {
            Column {
                if (quickTags.isNotEmpty()) {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        quickTags.forEach { (chipLabel, tagValue) ->
                            val active = text.contains(tagValue, ignoreCase = true)
                            ChoiceChip(
                                text = chipLabel,
                                selected = active,
                                onClick = {
                                    text = if (active) {
                                        text.replace(tagValue, "").trim().trim('·', ',', ' ')
                                    } else if (text.isBlank()) {
                                        tagValue
                                    } else {
                                        "$tagValue · $text".take(120)
                                    }
                                },
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
                OutlinedTextField(
                    value = text, onValueChange = { text = it.take(120) }, singleLine = true, shape = ForgeShape.Md,
                    placeholder = { Text(stringResource(if (isPain) R.string.v42_pain_note_hint else R.string.v42_note_hint)) },
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = c.teal, unfocusedBorderColor = c.divider, focusedTextColor = c.textPrimary, unfocusedTextColor = c.textPrimary),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text(stringResource(R.string.action_save), color = c.teal) } },
        dismissButton = {
            Row {
                if (initial.isNotBlank()) TextButton(onClick = { onSave("") }) { Text(stringResource(R.string.action_clear), color = c.coral) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel), color = c.textSecondary) }
            }
        },
    )
}

/**
 * The set tick: a 40 dp box in a 48 dp target. Empty it is a rounded square with an outline; pressed it tightens to
 * 4 dp corners and shrinks a little; ticked it becomes a teal circle with the check drawn in. A set that beats the
 * person's best goes gold with a star and a small pop. Reduced motion: shapes snap, colours fade in 120 ms.
 */
@Composable
internal fun SetTick(checked: Boolean, pr: Boolean, onToggle: () -> Unit) {
    val c = Fit.colors
    val scheme = MaterialTheme.colorScheme
    val reduced = LocalReducedMotion.current
    val setDoneLabel = stringResource(R.string.cd_set_done)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val gold = checked && pr
    val corner by animateDpAsState(
        when { pressed -> 4.dp; checked -> 20.dp; else -> 10.dp },
        spatialSpec(ForgeMotion.Springs.FastSpatial, Dp.VisibilityThreshold), label = "tickCorner",
    )
    val pressScale by animateFloatAsState(if (pressed) 0.9f else 1f, spatialSpec(ForgeMotion.Springs.FastSpatial), label = "tickPress")
    val fill by animateColorAsState(
        when { gold -> scheme.tertiary; checked -> scheme.primary; else -> Color.Transparent },
        effectsSpec(), label = "tickFill",
    )
    val outline by animateColorAsState(if (checked) Color.Transparent else scheme.outline, effectsSpec(), label = "tickOutline")
    // The check draws itself in from its short stroke to its long one.
    val draw by animateFloatAsState(
        if (checked) 1f else 0f,
        if (reduced || !checked) snap() else tween(ForgeMotion.Duration.SHORT3, easing = ForgeMotion.Easing.EmphasizedDecelerate),
        label = "tickDraw",
    )
    // The gold pop: 1 -> 1.3 -> 1, only when a set turns into a PR while on screen (not when scrolled back into view).
    val pop = remember { Animatable(1f) }
    var seen by remember { mutableStateOf(false) }
    LaunchedEffect(gold) {
        if (!seen) { seen = true; return@LaunchedEffect }
        if (gold && !reduced) {
            pop.animateTo(1.3f, ForgeMotion.Springs.FastSpatial.spec())
            pop.animateTo(1f, ForgeMotion.Springs.FastSpatial.spec())
        }
    }
    Box(
        Modifier.size(48.dp).clip(CircleShape)
            .steadyToggleable(checked, onToggle, interaction)
            .semantics { contentDescription = setDoneLabel },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(40.dp)
                .graphicsLayer { val k = pressScale * pop.value; scaleX = k; scaleY = k }
                .clip(RoundedCornerShape(corner))
                .background(fill)
                .border(2.dp, outline, RoundedCornerShape(corner)),
            contentAlignment = Alignment.Center,
        ) {
            if (gold) {
                Icon(Icons.Rounded.Star, contentDescription = null, tint = scheme.onTertiary, modifier = Modifier.size(24.dp))
            } else {
                val ink = if (checked) scheme.onPrimary else c.textSecondary.copy(alpha = 0.45f)
                Canvas(Modifier.size(24.dp)) {
                    val u = size.minDimension / 24f
                    val path = Path().apply {
                        moveTo(5.5f * u, 12.5f * u); lineTo(10f * u, 17f * u); lineTo(18.5f * u, 7.5f * u)
                    }
                    val stroke = Stroke(width = 2.6f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    if (!checked) {
                        // A faint check in the empty box says "tap to tick" before the first tap (Strong does the same).
                        drawPath(path, ink, style = stroke)
                    } else {
                        val measure = PathMeasure().apply { setPath(path, false) }
                        val part = Path()
                        measure.getSegment(0f, measure.length * draw, part, true)
                        drawPath(part, ink, style = stroke)
                    }
                }
            }
        }
    }
}

@Composable
fun PrTag() {
    val c = Fit.colors
    Box(Modifier.clip(ForgeShape.Full).background(c.goldTrack).padding(horizontal = 8.dp, vertical = 1.dp)) {
        Text(stringResource(R.string.live_pr), style = MaterialTheme.typography.labelSmall, color = c.gold, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SetRowPreviewFrame(content: @Composable () -> Unit) {
    ForgeQuestTheme(dark = true) {
        Box(Modifier.background(Fit.colors.card).padding(horizontal = ForgeSpace.Sm, vertical = ForgeSpace.Xs)) {
            content()
        }
    }
}

private val previewBench = ExerciseEntity(
    id = "bench_press",
    name = "Bench Press",
    kind = ExerciseKind.WEIGHTED,
    primary = listOf(Muscle.CHEST),
    secondary = listOf(Muscle.TRICEPS),
)

@Preview(name = "Cold start")
@Composable
private fun SetRowColdStartPreview() {
    SetRowPreviewFrame {
        SetRowContent(
            label = "1",
            sublabel = null,
            set = SetEntity(
                id = 1L,
                sessionId = 1L,
                exerciseId = "bench_press",
                itemPosition = 0,
                block = "Main",
                circuit = false,
                setIndex = 0,
                targetLabel = "8-12",
                reps = 8,
                weightKg = 0f,
                done = false,
            ),
            kind = ExerciseKind.WEIGHTED,
            effectiveUnit = WeightUnit.KG,
            pr = false,
            flashing = false,
            exercise = previewBench,
            previous = null,
        )
    }
}

@Preview(name = "Untagged done")
@Composable
private fun SetRowUntaggedDonePreview() {
    SetRowPreviewFrame {
        SetRowContent(
            label = "1",
            sublabel = null,
            set = SetEntity(
                id = 2L,
                sessionId = 1L,
                exerciseId = "bench_press",
                itemPosition = 0,
                block = "Main",
                circuit = false,
                setIndex = 0,
                targetLabel = "8-12",
                reps = 10,
                weightKg = 80f,
                done = true,
                effort = "",
            ),
            kind = ExerciseKind.WEIGHTED,
            effectiveUnit = WeightUnit.KG,
            pr = false,
            flashing = false,
            exercise = previewBench,
        )
    }
}

@Preview(name = "Hard effort")
@Composable
private fun SetRowHardPreview() {
    SetRowPreviewFrame {
        SetRowContent(
            label = "2",
            sublabel = null,
            set = SetEntity(
                id = 3L,
                sessionId = 1L,
                exerciseId = "bench_press",
                itemPosition = 0,
                block = "Main",
                circuit = false,
                setIndex = 1,
                targetLabel = "8-12",
                reps = 10,
                weightKg = 80f,
                done = true,
                effort = SetEffort.HARD.name,
            ),
            kind = ExerciseKind.WEIGHTED,
            effectiveUnit = WeightUnit.KG,
            pr = false,
            flashing = false,
            exercise = previewBench,
        )
    }
}

@Preview(name = "Failure effort")
@Composable
private fun SetRowFailurePreview() {
    SetRowPreviewFrame {
        SetRowContent(
            label = "3",
            sublabel = null,
            set = SetEntity(
                id = 4L,
                sessionId = 1L,
                exerciseId = "bench_press",
                itemPosition = 0,
                block = "Main",
                circuit = false,
                setIndex = 2,
                targetLabel = "8-12",
                reps = 8,
                weightKg = 80f,
                done = true,
                effort = SetEffort.FAILURE.name,
                setType = SetType.FAILURE.name,
            ),
            kind = ExerciseKind.WEIGHTED,
            effectiveUnit = WeightUnit.KG,
            pr = false,
            flashing = false,
            exercise = previewBench,
        )
    }
}

@Preview(name = "Adjusted next set")
@Composable
private fun SetRowAdjustedNextPreview() {
    SetRowPreviewFrame {
        Column {
            AdjustmentCueContent(
                adjustment = LiveAdjustment(exerciseId = "bench_press", sourceSetId = 4L, percent = 5),
                onUndo = {},
            )
            SetRowContent(
                label = "2",
                sublabel = null,
                set = SetEntity(
                    id = 5L,
                    sessionId = 1L,
                    exerciseId = "bench_press",
                    itemPosition = 0,
                    block = "Main",
                    circuit = false,
                    setIndex = 1,
                    targetLabel = "8-12",
                    reps = 8,
                    weightKg = 75f,
                    done = false,
                ),
                kind = ExerciseKind.WEIGHTED,
                effectiveUnit = WeightUnit.KG,
                pr = false,
                flashing = false,
                exercise = previewBench,
                previous = SetEntity(
                    id = 4L,
                    sessionId = 0L,
                    exerciseId = "bench_press",
                    itemPosition = 0,
                    block = "Main",
                    circuit = false,
                    setIndex = 1,
                    targetLabel = "8-12",
                    reps = 10,
                    weightKg = 80f,
                    done = true,
                ),
            )
        }
    }
}

@Preview(name = "PR pill visible")
@Composable
private fun SetRowPrPillPreview() {
    SetRowPreviewFrame {
        SetRowContent(
            label = "1",
            sublabel = null,
            set = SetEntity(
                id = 6L,
                sessionId = 1L,
                exerciseId = "bench_press",
                itemPosition = 0,
                block = "Main",
                circuit = false,
                setIndex = 0,
                targetLabel = "8-12",
                reps = 10,
                weightKg = 85f,
                done = true,
                isPr = true,
            ),
            kind = ExerciseKind.WEIGHTED,
            effectiveUnit = WeightUnit.KG,
            pr = true,
            flashing = false,
            exercise = previewBench,
        )
    }
}
