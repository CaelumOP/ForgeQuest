package com.forgequest.app.ui.workouts

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.forgequest.app.R
import com.forgequest.app.data.db.ExerciseEntity
import com.forgequest.app.data.db.SetEntity
import com.forgequest.app.domain.ExerciseKind
import com.forgequest.app.domain.PlateMath
import com.forgequest.app.domain.SetEffort
import com.forgequest.app.domain.SetType
import com.forgequest.app.domain.WeightUnit
import com.forgequest.app.ui.components.ChoiceChip
import com.forgequest.app.ui.components.KeypadNumberStepper
import com.forgequest.app.ui.components.rememberFullSheetState
import com.forgequest.app.ui.components.steadyToggleable
import com.forgequest.app.ui.captionRes
import com.forgequest.app.ui.labelRes
import com.forgequest.app.ui.theme.Fit
import com.forgequest.app.ui.theme.ForgeMotion
import com.forgequest.app.ui.theme.ForgeShape
import com.forgequest.app.ui.theme.LocalReducedMotion
import com.forgequest.app.ui.theme.effectsSpec
import com.forgequest.app.ui.theme.spatialSpec
import com.forgequest.app.ui.theme.tabular
import com.forgequest.app.ui.workouts.LiveWorkoutViewModel.Field

internal fun parseSetType(raw: String?): SetType =
    SetType.entries.firstOrNull { it.name == raw } ?: SetType.WORKING

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

@OptIn(ExperimentalFoundationApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
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
    val c = Fit.colors
    val settings by vm.settings.collectAsState()
    val effectiveUnit = settings.weightUnitFor(exercise?.unitOverride.orEmpty())
    val prIds by vm.prSetIds.collectAsState()
    val pr = set.isPr || set.id in prIds
    val scheme = MaterialTheme.colorScheme
    // Ticked rows tint primaryContainer; a PR set tints tertiaryContainer with a thin gold edge.
    val rowColor by animateColorAsState(
        when { set.done && pr -> scheme.tertiaryContainer; set.done -> scheme.primaryContainer; else -> Color.Transparent },
        effectsSpec(), label = "row",
    )
    val rowBorder by animateColorAsState(if (set.done && pr) scheme.tertiary else Color.Transparent, effectsSpec(), label = "rowBorder")
    val flashingId by vm.newBest.collectAsState()
    val flashing = flashingId == set.id
    val flashColor by animateColorAsState(if (flashing) c.gold else c.textPrimary, label = "flash")
    var noteDialog by remember { mutableStateOf(false) }
    var typeSheet by remember { mutableStateOf(false) }
    var plateBreakdown by remember { mutableStateOf<PlateMath.Breakdown?>(null) }

    if (noteDialog) {
        SetNoteDialog(
            initial = set.note,
            isPain = set.effort == SetEffort.PAIN.name,
            onDismiss = { noteDialog = false },
            onSave = { vm.setNote(set, it); noteDialog = false },
        )
    }
    val setType = parseSetType(set.setType)
    if (typeSheet) {
        SetTypeSheet(
            selected = setType,
            onSelect = { chosen -> vm.setSetType(set, chosen); typeSheet = false },
            onDismiss = { typeSheet = false },
        )
    }
    plateBreakdown?.let { breakdown ->
        PlateBreakdownSheet(
            breakdown = breakdown,
            onDismiss = { plateBreakdown = null },
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (set.targetLabel.isNotBlank() && sublabel != null) {
                    Text(set.targetLabel, style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
                }
                if (set.isPr) {
                    Spacer(Modifier.width(8.dp))
                    PrTag()
                }
                if (set.effort.isNotBlank() && effort != SetEffort.OK) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(effort.labelRes),
                        style = MaterialTheme.typography.labelSmall,
                        color = effortColor(effort),
                    )
                }
            }
            if (set.note.isNotBlank()) {
                Text(set.note, style = MaterialTheme.typography.labelSmall, color = c.blue, maxLines = 2, overflow = TextOverflow.Ellipsis, fontStyle = FontStyle.Italic)
            }
        }
    }

    val isBarbell = remember(exercise?.id, exercise?.equipment) { PlateMath.isBarbellExercise(exercise) }
    val weightQuickDeltas = remember(effectiveUnit) {
        if (effectiveUnit == WeightUnit.LB) listOf(5f, 10f, 25f) else listOf(2.5f, 5f, 10f)
    }

    val steppers: @Composable () -> Unit = {
        when (kind) {
            ExerciseKind.HOLD -> Row(verticalAlignment = Alignment.CenterVertically) {
                if (!set.done) IconButton(onClick = { vm.startHold(set) }) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = stringResource(R.string.cd_start_hold), tint = c.teal)
                }
                KeypadNumberStepper(
                    (set.seconds ?: 0).toFloat(), { vm.setField(set, Field.SECONDS, it) }, step = 1f, min = 0f, max = 3600f,
                    unit = stringResource(R.string.unit_s), title = stringResource(R.string.stepper_hold_time), minValueWidth = 52.dp, valueColor = flashColor,
                    quickDeltas = listOf(5f, 10f, 30f),
                    lastTimeValue = previous?.seconds?.toFloat(),
                    lastTimeLabel = previous?.seconds?.let { stringResource(R.string.rx_seconds, it) },
                )
            }
            // "reps" beside the number: a bare 8 read as seconds or kilos to beginners.
            ExerciseKind.REPS -> KeypadNumberStepper(
                (set.reps ?: 0).toFloat(), { vm.setField(set, Field.REPS, it) }, step = 1f, min = 0f, max = 999f,
                unit = stringResource(R.string.unit_reps), title = stringResource(R.string.stepper_reps), minValueWidth = 64.dp, valueColor = flashColor,
                unitPlurals = R.plurals.rx_reps,
                quickDeltas = listOf(1f, 2f, 5f),
                lastTimeValue = previous?.reps?.toFloat(),
            )
            ExerciseKind.WEIGHTED -> {
                KeypadNumberStepper(
                    (set.reps ?: 0).toFloat(), { vm.setField(set, Field.REPS, it) }, step = 1f, min = 0f, max = 999f,
                    unit = stringResource(R.string.unit_reps), title = stringResource(R.string.stepper_reps), minValueWidth = 64.dp, valueColor = flashColor,
                    unitPlurals = R.plurals.rx_reps,
                    quickDeltas = listOf(1f, 2f, 5f),
                    lastTimeValue = previous?.reps?.toFloat(),
                )
                val lastWeight = previous?.weightKg?.takeIf { it > 0f }
                KeypadNumberStepper(
                    effectiveUnit.fromKg(set.weightKg ?: 0f), { vm.setField(set, Field.WEIGHT, effectiveUnit.toKg(it)) }, step = effectiveUnit.loadStep, min = 0f, max = effectiveUnit.fromKg(999f),
                    unit = effectiveUnit.label, decimals = 1, title = stringResource(R.string.stepper_weight), minValueWidth = 64.dp, valueColor = if (flashing) flashColor else c.blue,
                    quickDeltas = weightQuickDeltas,
                    lastTimeValue = lastWeight?.let { effectiveUnit.fromKg(it) },
                    lastTimeLabel = lastWeight?.let { effectiveUnit.format(it) },
                )
            }
        }
        // Bodyweight moves with weight added in the plan editor (a plate on the back, a vest).
        val extra = set.weightKg
        if (kind != ExerciseKind.WEIGHTED && extra != null) {
            val lastExtra = previous?.weightKg?.takeIf { it > 0f }
            KeypadNumberStepper(
                effectiveUnit.fromKg(extra), { vm.setField(set, Field.WEIGHT, effectiveUnit.toKg(it)) }, step = effectiveUnit.loadStep, min = 0f, max = effectiveUnit.fromKg(999f),
                unit = effectiveUnit.label, decimals = 1, title = stringResource(R.string.stepper_weight), minValueWidth = 64.dp, valueColor = c.blue,
                quickDeltas = weightQuickDeltas,
                lastTimeValue = lastExtra?.let { effectiveUnit.fromKg(it) },
                lastTimeLabel = lastExtra?.let { effectiveUnit.format(it) },
            )
        }
        val weightKg = set.weightKg ?: 0f
        if (isBarbell && weightKg > 0f) {
            val breakdown = remember(weightKg, effectiveUnit, settings.barWeightKg, settings.plateInventory) {
                PlateMath.calculate(weightKg, effectiveUnit, settings.barWeightKg, settings.plateInventory)
            }
            if (!breakdown.belowBar) {
                val summaryText = if (breakdown.barOnly) {
                    stringResource(R.string.live41_plates_bar_only, "${PlateMath.formatPlate(breakdown.barWeight)} ${effectiveUnit.label}")
                } else {
                    stringResource(R.string.live41_plates_side, PlateMath.formatPerSideSummary(breakdown.perSide))
                }
                val cd = stringResource(R.string.live41_plates_breakdown_cd, effectiveUnit.format(weightKg))
                Box(
                    Modifier
                        .heightIn(min = 48.dp)
                        .clip(ForgeShape.Full)
                        .clickable(role = Role.Button) { plateBreakdown = breakdown }
                        .semantics { contentDescription = cd }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        summaryText,
                        style = MaterialTheme.typography.labelSmall.tabular(),
                        color = c.blue,
                        maxLines = 1,
                    )
                }
            }
        }
    }
    Column {
        if (reflow) {
            Column(rowModifier) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    number()
                    info(Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    com.forgequest.app.ui.components.CoachMarkBox("coach_tick", R.string.coach_tick_t, R.string.coach_tick_b, below = true) { SetTick(set.done, pr) { vm.toggle(set) } }
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
            com.forgequest.app.ui.components.CoachMarkBox("coach_tick", R.string.coach_tick_t, R.string.coach_tick_b, below = true) { SetTick(set.done, pr) { vm.toggle(set) } }
        }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlateBreakdownSheet(
    breakdown: PlateMath.Breakdown,
    onDismiss: () -> Unit,
) {
    val c = Fit.colors
    val unitLabel = breakdown.unit.label
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
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.live41_plates_title), style = MaterialTheme.typography.titleLarge, color = c.textPrimary, modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.cd_close), tint = c.textSecondary)
                }
            }
            Text(
                listOf(
                    stringResource(R.string.live41_plates_target, "${PlateMath.formatPlate(breakdown.targetWeight)} $unitLabel"),
                    stringResource(R.string.live41_plates_bar, "${PlateMath.formatPlate(breakdown.barWeight)} $unitLabel"),
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium.tabular(),
                color = c.textSecondary,
            )
            if (breakdown.perSide.isEmpty()) {
                Box(
                    Modifier.fillMaxWidth().clip(ForgeShape.Md).background(c.cardHigh).padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(R.string.live41_plates_none_needed), style = MaterialTheme.typography.bodyMedium, color = c.textPrimary)
                }
            } else {
                Column(
                    Modifier.fillMaxWidth().clip(ForgeShape.Md).background(c.cardHigh).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    breakdown.perSide.forEach { p ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${PlateMath.formatPlate(p.weight)} $unitLabel",
                                style = MaterialTheme.typography.titleMedium.tabular(),
                                color = c.blue,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                "× ${p.count}",
                                style = MaterialTheme.typography.titleMedium.tabular(),
                                color = c.textPrimary,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
            Text(
                stringResource(R.string.live41_plates_loaded, "${PlateMath.formatPlate(breakdown.loadedWeight)} $unitLabel"),
                style = MaterialTheme.typography.labelLarge.tabular(),
                color = c.textPrimary,
            )
            if (breakdown.remainder >= 0.05f) {
                Text(
                    stringResource(R.string.live41_plates_remainder, "${PlateMath.formatPlate(breakdown.remainder)} $unitLabel"),
                    style = MaterialTheme.typography.bodySmall.tabular(),
                    color = c.gold,
                )
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
