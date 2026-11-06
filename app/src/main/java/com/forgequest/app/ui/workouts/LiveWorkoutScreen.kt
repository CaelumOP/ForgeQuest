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

/** One visual group: a circuit block (shown round by round) or a single straight-sets exercise. */
internal sealed interface Group {
    data class Straight(val position: Int, val block: String, val sets: List<SetEntity>) : Group
    data class Circuit(val block: String, val positions: List<Int>, val rounds: List<List<SetEntity>>) : Group
}

internal val Group.allSets: List<SetEntity> get() = when (this) { is Group.Straight -> sets; is Group.Circuit -> rounds.flatten() }

internal val Group.key: String get() = if (this is Group.Straight) "s$position" else "c${(this as Group.Circuit).positions.first()}"

internal fun group(sets: List<SetEntity>): List<Group> {
    val byPosition = sets.groupBy { it.itemPosition }.toSortedMap()
    val result = mutableListOf<Group>()
    var pendingCircuit: MutableList<Int>? = null
    var circuitBlock = ""
    fun flush() {
        val positions = pendingCircuit ?: return
        val maxRounds = positions.maxOf { p -> byPosition[p]?.size ?: 0 }
        val rounds = (0 until maxRounds).map { r -> positions.mapNotNull { p -> byPosition[p]?.sortedBy { it.setIndex }?.getOrNull(r) } }
        result += Group.Circuit(circuitBlock, positions, rounds)
        pendingCircuit = null
    }
    for ((position, list) in byPosition) {
        val first = list.first()
        if (first.circuit) {
            if (pendingCircuit == null || circuitBlock != first.block) { flush(); pendingCircuit = mutableListOf(); circuitBlock = first.block }
            pendingCircuit!!.add(position)
        } else {
            flush()
            result += Group.Straight(position, first.block, list.sortedBy { it.setIndex })
        }
    }
    flush()
    return result
}

/** Seconds as minutes:seconds, with Western digits on every device. */
internal fun clock(sec: Int): String = "%d:%02d".format(Locale.US, sec / 60, sec % 60)

/** How much larger than the app text size the live pager cards read. */
internal const val LIVE_TEXT_BOOST = 1.15f

@Composable
fun LiveWorkoutScreen(nav: NavHostController) {
    val vm = appViewModel { LiveWorkoutViewModel(it) }
    val sessionState by vm.sessionState.collectAsState()
    val sets by vm.sets.collectAsState()
    val exercises by vm.exercises.collectAsState()
    val library by vm.library.collectAsState()
    val userEquipment by vm.userEquipment.collectAsState()
    val rest by vm.rest.collectAsState()
    val now by vm.now.collectAsState()
    val summary by vm.summary.collectAsState()
    val finishing by vm.finishing.collectAsState()
    val context = LocalContext.current
    val c = Fit.colors

    var confirmExit by remember { mutableStateOf(false) }
    var confirmFinish by remember { mutableStateOf(false) }
    // The rest countdown lives in the notification shade too, so the phone can be locked between sets.
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 && !vm.app.notifier.canPost()) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    var picker by remember { mutableStateOf<PickerMode?>(null) }

    summary?.let { sum ->
        val scope = androidx.compose.runtime.rememberCoroutineScope()
        // One tap: the Coach tab opens and sends the workout's facts and the calculated debrief by itself.
        val askCoach: (() -> Unit)? = if (vm.app.coach.canDebrief()) fun() {
            scope.launch { vm.app.coach.pendingMessage.value = vm.app.coach.debriefChatMessage(sum.sessionId); nav.goToTab(Routes.COACH) }
        } else null
        WorkoutCompleteScreen(sum, onAskCoach = askCoach, onRemind = vm::remindMe, onDone ={ nav.popBackStack(Routes.HOME, inclusive = false).also { ok -> if (!ok) nav.navigate(Routes.HOME) } })
        return
    }
    val s = sessionState?.session
    if (s == null) {
        if (sessionState != null && !finishing) LaunchedEffect(Unit) { nav.popBackStack() }
        Box(Modifier.fillMaxSize().background(c.background))
        return
    }
    BackHandler { nav.popBackStack() }

    val doneCount = sets.count { it.done }
    val elapsed = ((now - s.startedAt) / 1000).coerceAtLeast(0)
    val groups = remember(sets) { group(sets) }
    // In display order, so circuits go exercise by exercise within a round.
    val nextSet = remember(groups) { groups.flatMap { it.allSets }.firstOrNull { !it.done } }
    val current = groups.indexOfFirst { g -> g.allSets.any { it.id == nextSet?.id } }
    val unit by vm.unit.collectAsState()
    val nextLabel = nextSet?.let { n -> exercises[n.exerciseId]?.let { ex -> "${ex.name} · ${setSummary(n, ex.kind, unit)}" } }
    // List or focus mode, remembered between workouts.
    val prefs = remember { context.getSharedPreferences("live", android.content.Context.MODE_PRIVATE) }
    var focus by remember { mutableStateOf(prefs.getBoolean("focus", false)) }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    // Follow along: after a tick, bring the exercise of the next set into view.
    var lastDone by remember { mutableStateOf(doneCount) }
    LaunchedEffect(doneCount) {
        if (doneCount > lastDone && current >= 0 && !focus) listState.animateScrollToItem(current)
        lastDone = doneCount
    }
    val bottomPad = if (rest != null) 260.dp else 160.dp
    val cardFor: @Composable (Group, Boolean) -> Unit = { g, forceOpen ->
        GroupCard(g, exercises, vm, forceOpen,
            onVideo = { openFormVideo(context, it) },
            onSwap = { picker = PickerMode.Swap(it) },
            onDetail = { nav.navigate(Routes.exercise(it)) })
    }

    Box(Modifier.fillMaxSize().background(c.background)) {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.background(c.background).statusBarsPadding()) {
                ProgressLine(if (sets.isEmpty()) 0f else doneCount / sets.size.toFloat(), c.teal, c.tealTrack, height = 4.dp)
                Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = stringResource(R.string.cd_minimize), tint = c.textPrimary) }
                    Column(Modifier.weight(1f)) {
                        Text(s.title, style = MaterialTheme.typography.titleMedium, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(pluralStringResource(R.plurals.live_header, sets.size, elapsed / 60, elapsed % 60, doneCount, sets.size), style = MaterialTheme.typography.bodySmall.tabular(), color = c.textSecondary)
                        nextSet?.let { n -> exercises[n.exerciseId]?.let { Text(stringResource(R.string.live_now, it.name), style = MaterialTheme.typography.labelMedium, color = c.teal, maxLines = 1, overflow = TextOverflow.Ellipsis) } }
                    }
                    IconButton(onClick = { focus = !focus; prefs.edit().putBoolean("focus", focus).apply() }) {
                        Icon(
                            if (focus) Icons.Rounded.ViewAgenda else Icons.Rounded.CenterFocusStrong,
                            contentDescription = stringResource(if (focus) R.string.cd_list_mode else R.string.cd_focus_mode), tint = c.textPrimary,
                        )
                    }
                    TextButton(onClick = { confirmExit = true }) { Text(stringResource(R.string.live_discard), color = c.textSecondary) }
                }
            }
            val welcomeBack by vm.welcomeBack.collectAsState()
            AnimatedVisibility(welcomeBack && doneCount == 0) {
                FitCard(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)) {
                    Text(stringResource(R.string.welcome_back_title), style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
                    Text(stringResource(R.string.welcome_back_body), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        TonalPill(stringResource(R.string.welcome_back_ease_in), onClick = vm::easeIn)
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = vm::dismissWelcomeBack) { Text(stringResource(R.string.welcome_back_full), color = c.textSecondary) }
                    }
                }
            }
            if (focus && groups.isNotEmpty()) {
                // Focus mode: one exercise per page with larger text; moves on by itself when an exercise is finished.
                val pager = androidx.compose.foundation.pager.rememberPagerState(initialPage = current.coerceAtLeast(0)) { groups.size }
                LaunchedEffect(current) { if (current >= 0 && current != pager.currentPage) pager.animateScrollToPage(current) }
                Text(
                    stringResource(R.string.live_exercise_of, pager.currentPage + 1, groups.size), style = MaterialTheme.typography.labelLarge, color = c.textSecondary,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                val density = androidx.compose.ui.platform.LocalDensity.current
                // The pager cards are the live screen's focus, so their text runs 15 % larger than the app's.
                androidx.compose.foundation.pager.HorizontalPager(
                    pager, Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp), pageSpacing = 12.dp, key = { groups[it].key },
                ) { i ->
                    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density.density, density.fontScale * LIVE_TEXT_BOOST)) {
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 8.dp, bottom = bottomPad)) { cardFor(groups[i], true) }
                    }
                }
            } else LazyColumn(
                Modifier.weight(1f),
                state = listState,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottomPad),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(groups, key = { it.key }) { g -> Box(itemMotion()) { cardFor(g, false) } }
                item {
                    TonalPill(stringResource(R.string.live_add_exercise), onClick = { picker = PickerMode.Add }, icon = Icons.Rounded.Add, modifier = Modifier.fillMaxWidth())
                }
            }
        }

        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
            AnimatedVisibility(rest != null, enter = slideInVertically { it }, exit = slideOutVertically { it }) {
                rest?.let { r ->
                    val left = ((r.endsAt - now) / 1000).coerceAtLeast(0)
                    RestBar(left.toInt(), r.totalSeconds, nextLabel, onAdd = { vm.addRest(15) }, onRemove = { if (left > 15) vm.addRest(-15) else vm.skipRest() }, onSkip = vm::skipRest)
                }
            }
            Box(Modifier.fillMaxWidth().background(c.background).navigationBarsPadding().padding(16.dp)) {
                PillButton(
                    if (finishing) stringResource(R.string.live_saving) else stringResource(R.string.live_finish_workout),
                    onClick = { if (sets.any { !it.done }) confirmFinish = true else vm.finish() },
                    enabled = doneCount > 0 && !finishing,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        val hold by vm.hold.collectAsState()
        hold?.let { HoldOverlay(it, now, onStop = vm::stopHold, onCancel = vm::cancelHold) }
    }

    if (confirmFinish) {
        val left = sets.count { !it.done }
        AlertDialog(
            onDismissRequest = { confirmFinish = false },
            containerColor = c.card,
            title = { Text(pluralStringResource(R.plurals.live_finish_unticked_title, left, left), color = c.textPrimary) },
            text = { Text(pluralStringResource(R.plurals.live_finish_unticked_body, left, left), color = c.textSecondary) },
            // Three choices stacked, so none of them wraps onto a row of its own.
            confirmButton = {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
                    androidx.compose.material3.Button(
                        onClick = { confirmFinish = false; vm.finish() },
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = c.teal, contentColor = c.onTeal),
                    ) { Text(stringResource(R.string.live_finish)) }
                    TextButton(onClick = { confirmFinish = false; vm.tickAllAndFinish() }) { Text(stringResource(R.string.live_tick_all_finish), color = c.teal) }
                    TextButton(onClick = { confirmFinish = false }) { Text(stringResource(R.string.live_keep_going), color = c.textSecondary) }
                }
            },
        )
    }

    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            containerColor = c.card,
            title = { Text(stringResource(R.string.live_discard_title), color = c.textPrimary) },
            text = { Text(stringResource(R.string.live_discard_body), color = c.textSecondary) },
            confirmButton = { TextButton(onClick = { confirmExit = false; vm.discard { nav.popBackStack() } }) { Text(stringResource(R.string.live_discard), color = c.coral) } },
            dismissButton = { TextButton(onClick = { confirmExit = false }) { Text(stringResource(R.string.live_keep_going), color = c.teal) } },
        )
    }

    picker?.let { mode ->
        val swappingEx = (mode as? PickerMode.Swap)?.let { m ->
            sets.firstOrNull { it.itemPosition == m.position }?.let { exercises[it.exerciseId] }
        }
        ExercisePickerSheet(
            title = if (mode is PickerMode.Swap) stringResource(R.string.live_swap_exercise) else stringResource(R.string.live_add_exercise),
            library = library,
            onPick = { ex ->
                if (mode is PickerMode.Swap) vm.swap(mode.position, ex.id) else vm.addExercise(ex.id)
                picker = null
            },
            onCreate = { name, kind, muscle ->
                vm.createExercise(name, kind, muscle) { id -> if (mode is PickerMode.Swap) vm.swap(mode.position, id) else vm.addExercise(id) }
                picker = null
            },
            onDismiss = { picker = null },
            initialMuscle = swappingEx?.primary?.firstOrNull(),
            sourceExercise = swappingEx,
            userEquipment = userEquipment,
        )
    }
}

internal sealed interface PickerMode {
    data object Add : PickerMode
    data class Swap(val position: Int) : PickerMode
}

/** An exercise or circuit card; once every set is ticked it folds into one line (tap to open it again). */
@Composable
internal fun GroupCard(
    g: Group,
    exercises: Map<String, ExerciseEntity>,
    vm: LiveWorkoutViewModel,
    forceOpen: Boolean,
    onVideo: (ExerciseEntity?) -> Unit,
    onSwap: (Int) -> Unit,
    onDetail: (String) -> Unit,
) {
    val c = Fit.colors
    val all = g.allSets
    val allDone = all.isNotEmpty() && all.all { it.done }
    var open by remember(allDone) { mutableStateOf(!allDone) }
    if (g is Group.Straight && WorkoutScore.isWarmUp(g.block)) {
        WarmupCard(g, exercises[g.sets.first().exerciseId], vm)
        return
    }
    if (!open && !forceOpen) {
        val title = if (g is Group.Straight) exercises[g.sets.first().exerciseId]?.name ?: g.sets.first().exerciseId else blockLabel((g as Group.Circuit).block)
        FitCard(onClick = { open = true }, padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Check, contentDescription = null, tint = c.teal, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Text(title, style = MaterialTheme.typography.titleSmall, color = c.textPrimary, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(pluralStringResource(R.plurals.live_folded, all.size, all.size), style = MaterialTheme.typography.labelMedium, color = c.textSecondary)
                Icon(Icons.Rounded.ExpandMore, contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(20.dp))
            }
        }
        return
    }
    when (g) {
        is Group.Straight -> StraightCard(
            g, exercises[g.sets.first().exerciseId], vm,
            onVideo = onVideo, onSwap = { onSwap(g.position) }, onDetail = { onDetail(g.sets.first().exerciseId) },
        )
        is Group.Circuit -> CircuitCard(g, exercises, vm, onVideo = onVideo, onSwap = onSwap)
    }
}

/** One warm-up exercise as a single row ("Jumping jacks · 5 × 10") with one tick; tapping the row opens the per-set rows. */
@Composable
internal fun WarmupCard(g: Group.Straight, ex: ExerciseEntity?, vm: LiveWorkoutViewModel) {
    val c = Fit.colors
    val prev by vm.previous.collectAsState()
    var expanded by remember { mutableStateOf(false) }
    val first = g.sets.first()
    val doneCount = g.sets.count { it.done }
    val allDone = doneCount == g.sets.size
    val each = if (ex?.kind == ExerciseKind.HOLD) stringResource(R.string.rx_seconds, first.seconds ?: 0) else "${first.reps ?: 0}"
    FitCard(modifier = Modifier.animateContentSize(), padding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.weight(1f).clip(ForgeShape.Sm).clickable { expanded = !expanded }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("${ex?.name ?: first.exerciseId} · ${g.sets.size} × $each", style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
                    if (doneCount > 0 && !allDone) Text("$doneCount/${g.sets.size}", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                }
                Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(8.dp))
            SetTick(allDone, pr = false) { vm.toggleAll(g.sets) }
        }
        if (expanded) {
            Spacer(Modifier.height(4.dp))
            g.sets.forEach { set ->
                SetRow(label = "${set.setIndex + 1}", sublabel = null, set = set, kind = ex?.kind ?: ExerciseKind.REPS, vm = vm, previous = prev[set.exerciseId]?.getOrNull(set.setIndex))
            }
        }
    }
}

/** "12 reps · 10 kg" or "30 s", for the next-up lines. */
@Composable
internal fun setSummary(set: SetEntity, kind: ExerciseKind, unit: com.forgequest.app.domain.WeightUnit): String {
    val main = if (kind == ExerciseKind.HOLD) stringResource(R.string.rx_seconds, set.seconds ?: 0) else stringResource(R.string.rx_reps_n, set.reps ?: 0)
    val kg = set.weightKg
    return if (kg != null && (kg > 0f || kind == ExerciseKind.WEIGHTED)) "$main · ${unit.format(kg)}" else main
}

@Composable
internal fun StraightCard(
    g: Group.Straight,
    ex: ExerciseEntity?,
    vm: LiveWorkoutViewModel,
    onVideo: (ExerciseEntity?) -> Unit,
    onSwap: () -> Unit,
    onDetail: () -> Unit,
) {
    val c = Fit.colors
    val prev by vm.previous.collectAsState()
    var menu by remember { mutableStateOf(false) }
    // A set added or removed grows or shrinks the card smoothly instead of jumping.
    FitCard(modifier = Modifier.animateContentSize(), padding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).clip(ForgeShape.Sm).clickable(onClick = onDetail)) {
                Text(ex?.name ?: g.sets.first().exerciseId, style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
                val target = g.sets.first().targetLabel
                Text(if (target.isNotBlank()) stringResource(R.string.live_target, g.sets.size, target) else ex?.musclesLabel().orEmpty(), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.cd_options), tint = c.textSecondary) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = c.cardHigh) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.cd_watch_form)) }, onClick = { menu = false; onVideo(ex) })
                    DropdownMenuItem(text = { Text(stringResource(R.string.live_swap_exercise)) }, onClick = { menu = false; onSwap() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.live_add_set)) }, onClick = { menu = false; vm.addSet(g.position) })
                    DropdownMenuItem(text = { Text(stringResource(R.string.live_remove_last_set)) }, onClick = { menu = false; vm.removeLastSet(g.position) })
                }
            }
        }
        ex?.let { HarderNote(it, vm) }
        Spacer(Modifier.height(4.dp))
        g.sets.forEach { set ->
            SetRow(label = "${set.setIndex + 1}", sublabel = null, set = set, kind = ex?.kind ?: ExerciseKind.REPS, vm = vm, previous = prev[set.exerciseId]?.getOrNull(set.setIndex))
        }
    }
}

/** "Wide knee push-up is getting easy": one tap switches to the harder version, or a tempo tip when there is none. */
@Composable
internal fun HarderNote(ex: ExerciseEntity, vm: LiveWorkoutViewModel) {
    val c = Fit.colors
    val hints by vm.harder.collectAsState()
    val hint = hints[ex.id] ?: return
    val context = androidx.compose.ui.platform.LocalContext.current
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp, end = 8.dp).clip(ForgeShape.Sm).background(c.tealTrack).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.AutoMirrored.Rounded.TrendingUp, contentDescription = null, tint = c.teal, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        val next = hint.next
        Text(
            if (next != null) stringResource(R.string.harder_ready, ex.name, next.name)
            else stringResource(R.string.harder_tempo, ex.name),
            style = MaterialTheme.typography.bodySmall, color = c.textPrimary, modifier = Modifier.weight(1f),
        )
        if (next != null) {
            Spacer(Modifier.width(8.dp))
            val done = stringResource(R.string.harder_switched, next.name)
            TextButton(onClick = { vm.stepUp(ex.id, next.id); context.toast(done) }) { Text(stringResource(R.string.harder_switch), color = c.teal) }
        }
    }
}

@Composable
internal fun CircuitCard(
    g: Group.Circuit,
    exercises: Map<String, ExerciseEntity>,
    vm: LiveWorkoutViewModel,
    onVideo: (ExerciseEntity?) -> Unit,
    onSwap: (Int) -> Unit,
) {
    val c = Fit.colors
    FitCard(padding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(blockLabel(g.block), style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Rounded.Repeat, contentDescription = null, tint = c.teal, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.live_circuit), style = MaterialTheme.typography.labelMedium, color = c.teal)
        }
        Text(stringResource(R.string.live_circuit_hint), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
        g.rounds.firstOrNull().orEmpty().mapNotNull { exercises[it.exerciseId] }.distinctBy { it.id }.forEach { HarderNote(it, vm) }
        val prev by vm.previous.collectAsState()
        g.rounds.forEachIndexed { r, round ->
            val roundDone = round.all { it.done }
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.live_round, r + 1, g.rounds.size) + if (roundDone) stringResource(R.string.live_round_done) else "",
                style = MaterialTheme.typography.labelLarge,
                color = if (roundDone) c.teal else c.textSecondary,
            )
            round.forEach { set ->
                val ex = exercises[set.exerciseId]
                var menu by remember { mutableStateOf(false) }
                Box {
                    SetRow(
                        label = null,
                        sublabel = ex?.name ?: set.exerciseId,
                        set = set,
                        kind = ex?.kind ?: ExerciseKind.REPS,
                        vm = vm,
                        onLabelClick = { menu = true },
                        previous = prev[set.exerciseId]?.getOrNull(r),
                    )
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = c.cardHigh) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.cd_watch_form)) }, onClick = { menu = false; onVideo(ex) })
                        DropdownMenuItem(text = { Text(stringResource(R.string.live_swap_exercise)) }, onClick = { menu = false; onSwap(set.itemPosition) })
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = { vm.addRound(g.positions) }) {
            Icon(Icons.Rounded.Add, contentDescription = null, tint = c.teal, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.live_add_round), color = c.teal)
        }
    }
}
