package com.forgequest.app.ui.workouts

import com.forgequest.app.ui.theme.ForgeShape
import com.forgequest.app.ui.theme.tabular
import com.forgequest.app.ui.theme.itemMotion
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material.icons.rounded.Star
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import com.forgequest.app.ui.theme.ForgeMotion
import com.forgequest.app.container
import androidx.compose.ui.semantics.clearAndSetSemantics
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import com.forgequest.app.ui.theme.LocalReducedMotion
import com.forgequest.app.ui.theme.effectsSpec
import com.forgequest.app.ui.theme.spatialSpec
import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.map
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.forgequest.app.R
import com.forgequest.app.data.db.ExerciseEntity
import com.forgequest.app.data.db.SetEntity
import com.forgequest.app.data.DebriefText
import com.forgequest.app.domain.WeightUnit
import com.forgequest.app.domain.WorkoutDebriefMath
import com.forgequest.app.ui.goToTab
import kotlinx.coroutines.launch
import com.forgequest.app.domain.ExerciseKind
import com.forgequest.app.domain.SetEffort
import com.forgequest.app.domain.WorkoutScore
import com.forgequest.app.ui.labelRes
import com.forgequest.app.ui.Routes
import com.forgequest.app.ui.appViewModel
import com.forgequest.app.ui.blockLabel
import com.forgequest.app.ui.components.ChoiceChip
import com.forgequest.app.ui.components.FitCard
import com.forgequest.app.ui.components.toast
import com.forgequest.app.ui.components.IconCircle
import com.forgequest.app.ui.components.NumberStepper
import com.forgequest.app.ui.components.PillButton
import com.forgequest.app.ui.components.steadyToggleable
import com.forgequest.app.ui.components.ProgressLine
import com.forgequest.app.ui.components.TonalPill
import com.forgequest.app.ui.localTitle
import com.forgequest.app.ui.theme.Fit
import com.forgequest.app.ui.workouts.LiveWorkoutViewModel.Field
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun SetRow(
    label: String?,
    sublabel: String?,
    set: SetEntity,
    kind: ExerciseKind,
    vm: LiveWorkoutViewModel,
    onLabelClick: (() -> Unit)? = null,
    previous: SetEntity? = null,
) {
    val c = Fit.colors
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
    if (noteDialog) {
        SetNoteDialog(set.note, onDismiss = { noteDialog = false }, onSave = { vm.setNote(set, it); noteDialog = false })
    }
    val effortSetId by vm.effortSetId.collectAsState()
    val effort = SetEffort.parse(set.effort.ifBlank { null })
    // From 1.35x text the row splits in two tiers: set number, last-time hint and tick on top, the steppers below.
    val reflow = com.forgequest.app.ui.components.reflowText()
    val rowModifier = Modifier.fillMaxWidth().padding(top = 8.dp).clip(ForgeShape.Sm).background(rowColor).border(1.dp, rowBorder, ForgeShape.Sm)
        .combinedClickable(onClick = {}, onLongClick = { noteDialog = true }, onLongClickLabel = stringResource(R.string.cd_add_note))
        .padding(start = 8.dp, end = 4.dp, top = 8.dp, bottom = 8.dp)
    val number: @Composable () -> Unit = {
        if (label != null) {
            Box(Modifier.size(28.dp).clip(CircleShape).background(c.cardHigh), contentAlignment = Alignment.Center) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = c.textSecondary)
            }
            Spacer(Modifier.width(12.dp))
        }
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
            if (previous != null) {
                val unit by vm.unit.collectAsState()
                val last = when (kind) {
                    ExerciseKind.HOLD -> stringResource(R.string.rx_seconds, previous.seconds ?: 0)
                    ExerciseKind.WEIGHTED -> "${previous.reps ?: 0} × ${unit.format(previous.weightKg ?: 0f)}"
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
    val steppers: @Composable () -> Unit = {
            when (kind) {
                ExerciseKind.HOLD -> Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!set.done) IconButton(onClick = { vm.startHold(set) }) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = stringResource(R.string.cd_start_hold), tint = c.teal)
                    }
                    NumberStepper(
                        (set.seconds ?: 0).toFloat(), { vm.setField(set, Field.SECONDS, it) }, step = 5f, min = 0f, max = 3600f,
                        unit = stringResource(R.string.unit_s), title = stringResource(R.string.stepper_hold_time), minValueWidth = 52.dp, valueColor = flashColor,
                    )
                }
                // "reps" beside the number: a bare 8 read as seconds or kilos to beginners.
                ExerciseKind.REPS -> NumberStepper(
                    (set.reps ?: 0).toFloat(), { vm.setField(set, Field.REPS, it) }, step = 1f, min = 0f, max = 999f,
                    unit = stringResource(R.string.unit_reps), title = stringResource(R.string.stepper_reps), minValueWidth = 64.dp, valueColor = flashColor,
                )
                ExerciseKind.WEIGHTED -> {
                    NumberStepper(
                        (set.reps ?: 0).toFloat(), { vm.setField(set, Field.REPS, it) }, step = 1f, min = 0f, max = 999f,
                        unit = stringResource(R.string.unit_reps), title = stringResource(R.string.stepper_reps), minValueWidth = 64.dp, valueColor = flashColor,
                    )
                    val unit by vm.unit.collectAsState()
                    NumberStepper(
                        unit.fromKg(set.weightKg ?: 0f), { vm.setField(set, Field.WEIGHT, unit.toKg(it)) }, step = unit.loadStep, min = 0f, max = unit.fromKg(999f),
                        unit = unit.label, decimals = 1, title = stringResource(R.string.stepper_weight), minValueWidth = 64.dp, valueColor = if (flashing) flashColor else c.blue,
                    )
                }
            }
            // Bodyweight moves with weight added in the plan editor (a plate on the back, a vest).
            val extra = set.weightKg
            if (kind != ExerciseKind.WEIGHTED && extra != null) {
                val unit by vm.unit.collectAsState()
                NumberStepper(
                    unit.fromKg(extra), { vm.setField(set, Field.WEIGHT, unit.toKg(it)) }, step = unit.loadStep, min = 0f, max = unit.fromKg(999f),
                    unit = unit.label, decimals = 1, title = stringResource(R.string.stepper_weight), minValueWidth = 64.dp, valueColor = c.blue,
                )
            }
    }
    Column {
    if (reflow) {
        Column(rowModifier) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                number()
                info(Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                SetTick(set.done, pr) { vm.toggle(set) }
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
        SetTick(set.done, pr) { vm.toggle(set) }
    }
    // How did that set feel? Optional; not tapping counts as OK. Feeds the next session's progression.
    AnimatedVisibility(visible = set.done && effortSetId == set.id) {
        Column(Modifier.padding(start = 8.dp, top = 8.dp)) {
            EffortGroup(selected = set.effort, onSelect = { vm.setEffort(set, it) })
            // Pain is worth a word: one tap opens the same note editor as a long-press.
            if (set.effort == SetEffort.PAIN.name) {
                TextButton(onClick = { noteDialog = true }) { Text(stringResource(R.string.live_add_note), color = c.coral) }
            }
        }
    }
    }
}

/** Easy teal, OK blue, Hard purple, Pain coral. */
@Composable
internal fun effortColor(e: SetEffort) = Fit.colors.let { c ->
    when (e) { SetEffort.EASY -> c.teal; SetEffort.OK -> c.blue; SetEffort.HARD -> c.purple; SetEffort.PAIN -> c.coral }
}

/**
 * Easy · OK · Hard · Pain as one connected group: round outer ends, 6 dp inner seams. The chosen one morphs into a full
 * pill and fills with its colour. Tapping it again clears it (the view model decides that).
 */
@Composable
internal fun EffortGroup(selected: String, onSelect: (SetEffort) -> Unit) {
    val c = Fit.colors
    val entries = SetEffort.entries
    Row(Modifier.fillMaxWidth().padding(end = 8.dp).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        entries.forEachIndexed { i, e ->
            val isSel = selected == e.name
            val full = 20.dp // half the 40 dp minimum height
            val seam = 6.dp
            val startCorner by animateDpAsState(if (isSel || i == 0) full else seam, spatialSpec(ForgeMotion.Springs.FastSpatial, Dp.VisibilityThreshold), label = "effortStart")
            val endCorner by animateDpAsState(if (isSel || i == entries.lastIndex) full else seam, spatialSpec(ForgeMotion.Springs.FastSpatial, Dp.VisibilityThreshold), label = "effortEnd")
            val accent = effortColor(e)
            val fill by animateColorAsState(if (isSel) accent else c.cardHigh, effectsSpec(), label = "effortFill")
            val ink by animateColorAsState(if (isSel) (if (accent == c.teal) c.onTeal else c.background) else c.textPrimary, effectsSpec(), label = "effortInk")
            val shape = RoundedCornerShape(topStart = startCorner, bottomStart = startCorner, topEnd = endCorner, bottomEnd = endCorner)
            Box(
                Modifier.weight(1f).heightIn(min = 40.dp).clip(shape).background(fill)
                    .selectable(selected = isSel, role = Role.RadioButton) { onSelect(e) }
                    .padding(horizontal = 4.dp, vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(e.labelRes), style = MaterialTheme.typography.labelLarge, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            }
        }
    }
}

/** Long-press a set to jot something down or tap a quick RPE/RIR chip for automatic load progression & recovery tuning. */
@Composable
fun SetNoteDialog(initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    val c = Fit.colors
    var text by remember { mutableStateOf(initial) }
    val quickTags = listOf(
        stringResource(R.string.live_tag_easy) to stringResource(R.string.live_tag_easy),
        stringResource(R.string.live_tag_solid) to stringResource(R.string.live_tag_solid),
        stringResource(R.string.live_tag_max) to stringResource(R.string.live_tag_max_note),
        stringResource(R.string.live_tag_joint) to stringResource(R.string.live_tag_joint_note),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.card,
        title = { Text(stringResource(R.string.live_note_title), color = c.textPrimary) },
        text = {
            Column {
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
                OutlinedTextField(
                    value = text, onValueChange = { text = it.take(120) }, singleLine = true, shape = ForgeShape.Md,
                    placeholder = { Text(stringResource(R.string.live_note_hint)) },
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
