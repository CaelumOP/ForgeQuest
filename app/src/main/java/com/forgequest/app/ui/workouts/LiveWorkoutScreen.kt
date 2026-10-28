package com.forgequest.app.ui.workouts

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
private sealed interface Group {
    data class Straight(val position: Int, val block: String, val sets: List<SetEntity>) : Group
    data class Circuit(val block: String, val positions: List<Int>, val rounds: List<List<SetEntity>>) : Group
}

private val Group.allSets: List<SetEntity> get() = when (this) { is Group.Straight -> sets; is Group.Circuit -> rounds.flatten() }

private val Group.key: String get() = if (this is Group.Straight) "s$position" else "c${(this as Group.Circuit).positions.first()}"

private fun group(sets: List<SetEntity>): List<Group> {
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
private fun clock(sec: Int): String = "%d:%02d".format(Locale.US, sec / 60, sec % 60)

/** How much larger than the app text size the live pager cards read. */
private const val LIVE_TEXT_BOOST = 1.15f

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

private sealed interface PickerMode {
    data object Add : PickerMode
    data class Swap(val position: Int) : PickerMode
}

/** An exercise or circuit card; once every set is ticked it folds into one line (tap to open it again). */
@Composable
private fun GroupCard(
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
                Spacer(Modifier.width(10.dp))
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
private fun WarmupCard(g: Group.Straight, ex: ExerciseEntity?, vm: LiveWorkoutViewModel) {
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
                Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).clickable { expanded = !expanded }.padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("${ex?.name ?: first.exerciseId} · ${g.sets.size} × $each", style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
                    if (doneCount > 0 && !allDone) Text("$doneCount/${g.sets.size}", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                }
                Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(6.dp))
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
private fun setSummary(set: SetEntity, kind: ExerciseKind, unit: com.forgequest.app.domain.WeightUnit): String {
    val main = if (kind == ExerciseKind.HOLD) stringResource(R.string.rx_seconds, set.seconds ?: 0) else stringResource(R.string.rx_reps_n, set.reps ?: 0)
    val kg = set.weightKg
    return if (kg != null && (kg > 0f || kind == ExerciseKind.WEIGHTED)) "$main · ${unit.format(kg)}" else main
}

@Composable
private fun StraightCard(
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
            Column(Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).clickable(onClick = onDetail)) {
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
private fun HarderNote(ex: ExerciseEntity, vm: LiveWorkoutViewModel) {
    val c = Fit.colors
    val hints by vm.harder.collectAsState()
    val hint = hints[ex.id] ?: return
    val context = androidx.compose.ui.platform.LocalContext.current
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp, end = 8.dp).clip(RoundedCornerShape(12.dp)).background(c.tealTrack).padding(horizontal = 12.dp, vertical = 8.dp),
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
private fun CircuitCard(
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
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.live_add_round), color = c.teal)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SetRow(
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
    Column {
    Row(
        Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(14.dp)).background(rowColor).border(1.dp, rowBorder, RoundedCornerShape(14.dp))
            .combinedClickable(onClick = {}, onLongClick = { noteDialog = true }, onLongClickLabel = stringResource(R.string.cd_add_note))
            .padding(start = 8.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (label != null) {
            Box(Modifier.size(28.dp).clip(CircleShape).background(c.cardHigh), contentAlignment = Alignment.Center) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = c.textSecondary)
            }
            Spacer(Modifier.width(10.dp))
        }
        Column(
            Modifier.weight(1f).let { if (onLabelClick != null) it.clip(RoundedCornerShape(8.dp)).clickable(onClick = onLabelClick) else it },
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
                    Spacer(Modifier.width(6.dp))
                    PrTag()
                }
                if (set.effort.isNotBlank() && effort != SetEffort.OK) {
                    Spacer(Modifier.width(6.dp))
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
        Spacer(Modifier.width(6.dp))
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
        Spacer(Modifier.width(6.dp))
        SetTick(set.done, pr) { vm.toggle(set) }
    }
    // How did that set feel? Optional; not tapping counts as OK. Feeds the next session's progression.
    AnimatedVisibility(visible = set.done && effortSetId == set.id) {
        Column(Modifier.padding(start = 8.dp, top = 6.dp)) {
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
private fun effortColor(e: SetEffort) = Fit.colors.let { c ->
    when (e) { SetEffort.EASY -> c.teal; SetEffort.OK -> c.blue; SetEffort.HARD -> c.purple; SetEffort.PAIN -> c.coral }
}

/**
 * Easy · OK · Hard · Pain as one connected group: round outer ends, 6 dp inner seams. The chosen one morphs into a full
 * pill and fills with its colour. Tapping it again clears it (the view model decides that).
 */
@Composable
private fun EffortGroup(selected: String, onSelect: (SetEffort) -> Unit) {
    val c = Fit.colors
    val entries = SetEffort.entries
    Row(Modifier.fillMaxWidth().padding(end = 8.dp).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        entries.forEachIndexed { i, e ->
            val isSel = selected == e.name
            val full = 50.dp
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
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
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
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = text, onValueChange = { text = it.take(120) }, singleLine = true, shape = RoundedCornerShape(16.dp),
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
private fun SetTick(checked: Boolean, pr: Boolean, onToggle: () -> Unit) {
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
    Box(Modifier.clip(RoundedCornerShape(50)).background(c.goldTrack).padding(horizontal = 6.dp, vertical = 1.dp)) {
        Text(stringResource(R.string.live_pr), style = MaterialTheme.typography.labelSmall, color = c.gold, fontWeight = FontWeight.Bold)
    }
}

/** Full-screen clock for a timed hold: 3-2-1, the countdown to the target, then extra time counting up. */
@Composable
private fun HoldOverlay(h: HoldTimer, now: Long, onStop: () -> Unit, onCancel: () -> Unit) {
    val c = Fit.colors
    val view = androidx.compose.ui.platform.LocalView.current
    androidx.compose.runtime.DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    BackHandler(onBack = onCancel)
    val s = h.elapsed(now)
    val ready = s < 0
    val over = s >= h.target
    val shown = when {
        ready -> "${-s}"
        over -> "+" + clock(s - h.target)
        else -> clock(h.target - s)
    }
    val tint = when { ready -> c.textSecondary; over -> c.gold; else -> c.teal }
    Column(
        Modifier.fillMaxSize().background(c.background).clickable(enabled = false) {}.statusBarsPadding().navigationBarsPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(h.exerciseName, style = MaterialTheme.typography.titleMedium, color = c.textPrimary, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            IconButton(onClick = onCancel) { Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.action_cancel), tint = c.textSecondary) }
        }
        Spacer(Modifier.weight(1f))
        Text(
            stringResource(when { ready -> R.string.hold_get_ready; over -> R.string.hold_overtime; else -> R.string.hold_holding }),
            style = MaterialTheme.typography.titleLarge, color = tint, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(shown, style = MaterialTheme.typography.displayLarge.copy(fontSize = androidx.compose.ui.unit.TextUnit(88f, androidx.compose.ui.unit.TextUnitType.Sp)).tabular(), fontWeight = FontWeight.Bold, color = tint)
        Spacer(Modifier.height(16.dp))
        ProgressLine(if (ready) 0f else (s / h.target.toFloat()).coerceIn(0f, 1f), if (over) c.gold else c.teal, c.tealTrack, height = 6.dp)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.hold_target, h.target), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
        Spacer(Modifier.weight(1f))
        PillButton(
            stringResource(if (over) R.string.hold_done else R.string.hold_stop),
            onClick = if (ready) onCancel else onStop,
            modifier = Modifier.fillMaxWidth(),
        )
        if (!ready && !over) {
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.hold_stop_hint), style = MaterialTheme.typography.bodySmall, color = c.textSecondary, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun RestBar(secondsLeft: Int, total: Int, next: String?, onAdd: () -> Unit, onRemove: () -> Unit, onSkip: () -> Unit) {
    val c = Fit.colors
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(20.dp)).background(c.cardHigh).padding(16.dp)) {
        // At large text sizes the three buttons drop to their own line, so the time never wraps digit by digit.
        val big = com.forgequest.app.ui.components.isLargeText()
        val buttons: @Composable () -> Unit = {
            TextButton(onClick = onRemove) { Text(stringResource(R.string.live_rest_minus), color = c.textPrimary, maxLines = 1) }
            TextButton(onClick = onAdd) { Text(stringResource(R.string.live_rest_plus), color = c.textPrimary, maxLines = 1) }
            TextButton(onClick = onSkip) { Text(stringResource(R.string.live_skip), color = c.teal, maxLines = 1) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.live_rest), style = MaterialTheme.typography.titleSmall, color = c.textSecondary, maxLines = 1)
            Spacer(Modifier.width(10.dp))
            Text(clock(secondsLeft), style = MaterialTheme.typography.titleLarge.tabular(), color = c.blue, maxLines = 1, softWrap = false, modifier = Modifier.weight(1f))
            if (!big) buttons()
        }
        if (big) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { buttons() }
        if (next != null) Text(stringResource(R.string.live_next, next), style = MaterialTheme.typography.bodyMedium, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(8.dp))
        ProgressLine(if (total == 0) 0f else secondsLeft / total.toFloat(), c.blue, c.blueTrack, height = 4.dp)
    }
}

@Composable
private fun WorkoutCompleteScreen(
    sum: WorkoutSummary,
    onAskCoach: (() -> Unit)?,
    onRemind: () -> Unit,
    onDone: () -> Unit,
) {
    val c = Fit.colors
    var filled by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { filled = true }
    val leveledUp = sum.after.level > sum.before.level
    BackHandler(onBack = onDone)
    Column(
        Modifier.fillMaxSize().background(c.background).statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(32.dp))
        IconCircle(Icons.Rounded.Check, c.onTeal, c.teal, 88.dp)
        Spacer(Modifier.height(20.dp))
        Text(stringResource(R.string.live_complete), style = MaterialTheme.typography.headlineMedium, color = c.textPrimary)
        Text(sum.title, style = MaterialTheme.typography.bodyLarge, color = c.textSecondary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(28.dp))
        Row(Modifier.fillMaxWidth()) {
            com.forgequest.app.ui.components.StatColumn("${sum.doneSets}", stringResource(R.string.live_sets), c.teal, Modifier.weight(1f))
            com.forgequest.app.ui.components.StatColumn(stringResource(R.string.hist_minutes, sum.minutes), stringResource(R.string.profile_time), c.blue, Modifier.weight(1f))
            com.forgequest.app.ui.components.StatColumn("⁦+${sum.xpGained}⁩", stringResource(R.string.live_xp), c.gold, Modifier.weight(1f))
        }
        sum.clippedEndAt?.let { end ->
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.live_ended_at_last_set, java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(end - com.forgequest.app.domain.WorkoutEnd.GRACE_MS))),
                style = MaterialTheme.typography.bodySmall, color = c.textSecondary, textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(24.dp))
        FitCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (leveledUp) stringResource(R.string.live_level_up, sum.after.level) else stringResource(R.string.live_level, sum.after.level),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (leveledUp) c.gold else c.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                Text(stringResource(R.string.live_rank, sum.after.rank.letter, sum.after.rank.localTitle()), style = MaterialTheme.typography.labelLarge, color = c.blue)
            }
            Spacer(Modifier.height(12.dp))
            val from = if (leveledUp) 0f else sum.before.progress
            ProgressLine(if (filled) sum.after.progress else from, c.blue, c.blueTrack, height = 8.dp)
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.profile_xp_line, sum.after.xpIntoLevel, sum.after.xpForNextLevel, sum.after.level + 1), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
        }
        if (sum.prs.isNotEmpty() || sum.newBadges.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            FitCard {
                sum.prs.forEach { name ->
                    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        PrTag()
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(R.string.live_new_record, name), style = MaterialTheme.typography.bodyMedium, color = c.textPrimary)
                    }
                }
                sum.newBadges.forEach { badge ->
                    val title = badge.localTitle()
                    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.EmojiEvents, contentDescription = null, tint = c.gold, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(R.string.live_badge_unlocked, title), style = MaterialTheme.typography.bodyMedium, color = c.textPrimary)
                    }
                }
            }
        }
        if (sum.offerReminder) {
            var reminded by remember { mutableStateOf(false) }
            Spacer(Modifier.height(12.dp))
            FitCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconCircle(Icons.Rounded.Notifications, c.gold, c.goldTrack)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(if (reminded) R.string.live_reminder_on else R.string.live_reminder_title), style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
                        Text(stringResource(R.string.live_reminder_body), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                    }
                    if (!reminded) {
                        Spacer(Modifier.width(8.dp))
                        PillButton(stringResource(R.string.live_reminder_button), onClick = { onRemind(); reminded = true })
                    }
                }
            }
        }
        // What comes next, so finishing points forward (Fitbod ends every workout with the next one).
        sum.nextUp?.let { (dow, title) ->
            Spacer(Modifier.height(12.dp))
            FitCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.CalendarMonth, contentDescription = null, tint = c.teal, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.live_next_up, com.forgequest.app.domain.Dates.dayName(dow), title), style = MaterialTheme.typography.bodyMedium, color = c.textPrimary)
                }
            }
        }
        if (sum.score != null || sum.debrief != null) {
            Spacer(Modifier.height(12.dp))
            DebriefCard(sum.debrief, sum.score, sum.exerciseNames, sum.unit, onAskCoach, celebrate = true)
        }
        Spacer(Modifier.height(24.dp))
        PillButton(stringResource(R.string.home_done), onClick = onDone, modifier = Modifier.fillMaxWidth())
    }
}

/** "All 12 sets · Up on 3 of 4 · 1 PR": why the workout got its score. A rough day from pain alone says so. */
@Composable
fun scoreReasons(r: WorkoutScore.Result): String {
    val sets = when {
        r.plannedSets == null -> pluralStringResource(R.plurals.hist_sets, r.doneSets, r.doneSets)
        r.doneSets >= r.plannedSets -> pluralStringResource(R.plurals.score_sets_all, r.doneSets, r.doneSets)
        else -> stringResource(R.string.score_sets_of, r.doneSets, r.plannedSets)
    }
    val up = if (r.compared > 0) stringResource(R.string.score_up, r.up, r.compared) else null
    val prs = if (r.prs > 0) pluralStringResource(R.plurals.score_prs, r.prs, r.prs) else null
    val adjusted = if (r.rough) stringResource(if (r.lowRecovery) R.string.score_adjusted else R.string.score_adjusted_pain) else null
    return listOfNotNull(sets, up, prs, adjusted).joinToString(" · ")
}

/**
 * One debrief line fading in and rising 16 dp, [index] × 70 ms after [go] (DefaultSpatial). Off when not [enabled];
 * reduced motion makes it a short fade with no rise.
 */
@Composable
private fun Modifier.riseIn(enabled: Boolean, go: Boolean, index: Int): Modifier {
    if (!enabled) return this
    val reduced = LocalReducedMotion.current
    val p = remember { Animatable(0f) }
    LaunchedEffect(go) {
        if (!go) return@LaunchedEffect
        delay(70L * index)
        p.animateTo(1f, if (reduced) tween(ForgeMotion.REDUCED_FADE_MS) else ForgeMotion.Springs.DefaultSpatial.spec())
    }
    return graphicsLayer {
        alpha = p.value.coerceIn(0f, 1f)
        translationY = if (reduced) 0f else (1f - p.value) * 16.dp.toPx()
    }
}

/** N/10 only from 8 up is gold; 4 to 7 stay quiet. */
@Composable
private fun scoreColor(score: Int) = if (score >= 8) Fit.colors.gold else Fit.colors.textSecondary

/**
 * The workout debrief: the calculated score with its reasons, then the headline, what went well (teal), what to watch
 * (gold) and next time (blue), all worked out from the person's numbers ([DebriefText]). [onAskCoach] shows the coach
 * button; null hides it (no AI key). [names] maps an exercise id to its name.
 */
@Composable
fun DebriefCard(
    debrief: WorkoutDebriefMath.Result?,
    score: WorkoutScore.Result?,
    names: Map<String, String>,
    unit: WeightUnit,
    onAskCoach: (() -> Unit)? = null,
    /** True on the finish screen only: the score counts up and the lines rise in. Reopened from the journal it just shows. */
    celebrate: Boolean = false,
) {
    val c = Fit.colors
    val context = LocalContext.current
    val res = context.resources
    val locale = androidx.compose.ui.platform.LocalConfiguration.current
    val lines = remember(debrief, names, unit, locale) { debrief?.let { d -> DebriefText.render(d, res, { id -> names[id] ?: id }) { kg -> unit.format(kg) } } }
    val reduced = LocalReducedMotion.current
    val counting = celebrate && score != null && score.showNumber
    // 0 -> score over 650 ms; the arc follows the same value. Reduced motion lands on the score at once.
    val count = remember { Animatable(if (counting && !reduced) 0f else (score?.score ?: 0).toFloat()) }
    // The lines below wait for the count to land (at once when there is no count).
    var linesGo by remember { mutableStateOf(!celebrate) }
    LaunchedEffect(Unit) {
        if (!celebrate) return@LaunchedEffect
        if (counting) {
            if (!reduced) count.animateTo(score!!.score.toFloat(), tween(650, easing = ForgeMotion.Easing.EmphasizedDecelerate))
            context.container.haptics.heavyClick()
        }
        linesGo = true
    }
    var line = 0
    FitCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.debrief_title), style = MaterialTheme.typography.titleMedium, color = c.textPrimary, modifier = Modifier.weight(1f))
            if (score != null && score.showNumber) {
                val arcColor = scoreColor(score.score)
                val track = MaterialTheme.colorScheme.surfaceContainerHighest
                Box(contentAlignment = Alignment.Center) {
                    // A thin arc that sweeps to score/10 with the count.
                    Canvas(Modifier.size(52.dp).clearAndSetSemantics {}) {
                        val w = 3.dp.toPx()
                        val inset = w / 2
                        val arcSize = androidx.compose.ui.geometry.Size(size.width - w, size.height - w)
                        val tl = androidx.compose.ui.geometry.Offset(inset, inset)
                        drawArc(track, 0f, 360f, false, tl, arcSize, style = Stroke(w))
                        drawArc(arcColor, -90f, 360f * (count.value / 10f).coerceIn(0f, 1f), false, tl, arcSize, style = Stroke(w, cap = StrokeCap.Round))
                    }
                    Text(
                        stringResource(R.string.debrief_score, count.value.roundToInt()),
                        style = MaterialTheme.typography.labelLarge.tabular(), color = arcColor,
                        modifier = Modifier.semantics { contentDescription = res.getString(R.string.debrief_score, score.score) },
                    )
                }
            }
        }
        // The first-ever and comeback headlines already say what this line says.
        val sameAsHeadline = debrief?.headline?.type.let { it == WorkoutDebriefMath.Headline.FIRST_EVER || it == WorkoutDebriefMath.Headline.AFTER_BREAK }
        if (score != null && !score.showNumber && !sameAsHeadline) {
            Text(stringResource(if (score.firstEver) R.string.score_first_ever else R.string.score_after_break), style = MaterialTheme.typography.bodyMedium, color = c.textPrimary, modifier = Modifier.padding(top = 2.dp))
        }
        if (score != null) Text(scoreReasons(score), style = MaterialTheme.typography.bodySmall, color = c.textSecondary, modifier = Modifier.padding(top = 2.dp))
        if (lines != null) {
            Spacer(Modifier.height(6.dp))
            Text(lines.headline, style = MaterialTheme.typography.bodyMedium, color = c.textPrimary, modifier = Modifier.riseIn(celebrate, linesGo, line++))
            // Green for what went well, gold for what to watch, blue for next time.
            lines.wins.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, color = c.teal, modifier = Modifier.riseIn(celebrate, linesGo, line++).padding(top = 4.dp)) }
            lines.watch.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, color = c.gold, modifier = Modifier.riseIn(celebrate, linesGo, line++).padding(top = 4.dp)) }
            lines.next.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = c.blue, modifier = Modifier.riseIn(celebrate, linesGo, line++).padding(top = 4.dp)) }
            lines.sameRest?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = c.textSecondary, modifier = Modifier.padding(top = 4.dp)) }
            lines.footnote?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = c.textSecondary, modifier = Modifier.padding(top = 8.dp)) }
        }
        if (onAskCoach != null) {
            TextButton(onClick = onAskCoach, contentPadding = PaddingValues(horizontal = 0.dp)) {
                Text(stringResource(R.string.debrief_ask_coach), color = c.teal)
            }
        }
    }
}
