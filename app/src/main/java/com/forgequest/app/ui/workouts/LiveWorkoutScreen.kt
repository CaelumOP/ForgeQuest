package com.forgequest.app.ui.workouts

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavHostController
import com.forgequest.app.R
import com.forgequest.app.container
import com.forgequest.app.data.db.ExerciseEntity
import com.forgequest.app.data.db.SetEntity
import com.forgequest.app.domain.ExerciseKind
import com.forgequest.app.domain.Progression
import com.forgequest.app.domain.SetEffort
import com.forgequest.app.domain.WeightUnit
import com.forgequest.app.domain.WorkoutScore
import com.forgequest.app.domain.countsForScore
import com.forgequest.app.ui.LocalSnackbar
import com.forgequest.app.ui.Routes
import com.forgequest.app.ui.appViewModel
import com.forgequest.app.ui.blockLabel
import com.forgequest.app.ui.components.FitCard
import com.forgequest.app.ui.components.PillButton
import com.forgequest.app.ui.components.ProgressLine
import com.forgequest.app.ui.components.TonalPill
import com.forgequest.app.ui.components.toast
import com.forgequest.app.ui.exerciseCues
import com.forgequest.app.ui.theme.Fit
import com.forgequest.app.ui.theme.ForgeShape
import com.forgequest.app.ui.theme.itemMotion
import com.forgequest.app.ui.theme.tabular
import java.util.Locale

/** One visual group: a circuit/superset block (shown round by round) or a single straight-sets exercise. */
internal sealed interface Group {
    data class Straight(val position: Int, val block: String, val sets: List<SetEntity>) : Group
    data class Circuit(
        val block: String,
        val positions: List<Int>,
        val rounds: List<List<SetEntity>>,
        val supersetGroup: Int? = null,
    ) : Group
}

internal val Group.allSets: List<SetEntity> get() = when (this) { is Group.Straight -> sets; is Group.Circuit -> rounds.flatten() }

internal val Group.key: String get() = if (this is Group.Straight) "s$position" else "c${(this as Group.Circuit).positions.first()}"

/**
 * The set to do next: the first undone set of the group ticked most recently; if that group is finished, the first undone set in a
 * later group (so it never jumps back to a skipped warm-up); otherwise the first undone set overall.
 */
internal fun pickNextSet(groups: List<Group>): SetEntity? {
    val last = groups.indices.filter { i -> groups[i].allSets.any { it.doneAt != null } }
        .maxByOrNull { i -> groups[i].allSets.maxOf { it.doneAt ?: -1L } } ?: -1
    if (last >= 0) {
        groups[last].allSets.firstOrNull { !it.done }?.let { return it }
        groups.drop(last + 1).flatMap { it.allSets }.firstOrNull { !it.done }?.let { return it }
    }
    return groups.flatMap { it.allSets }.firstOrNull { !it.done }
}

internal fun group(
    sets: List<SetEntity>,
    supersetGroupOf: (Int) -> Int? = { null },
): List<Group> {
    val byPosition = sets.groupBy { it.itemPosition }.toSortedMap()
    val result = mutableListOf<Group>()
    var pendingCircuit: MutableList<Int>? = null
    var circuitBlock = ""
    var pendingSuperset: Int? = null
    fun flush() {
        val positions = pendingCircuit ?: return
        if (pendingSuperset != null && positions.size < 2) {
            val p = positions.first()
            val list = byPosition[p].orEmpty().sortedBy { it.setIndex }
            result += Group.Straight(p, list.firstOrNull()?.block.orEmpty(), list)
        } else {
            val maxRounds = positions.maxOf { p -> byPosition[p]?.size ?: 0 }
            val rounds = (0 until maxRounds).map { r ->
                positions.mapNotNull { p -> byPosition[p]?.sortedBy { it.setIndex }?.getOrNull(r) }
            }
            result += Group.Circuit(circuitBlock, positions, rounds, supersetGroup = pendingSuperset)
        }
        pendingCircuit = null
        pendingSuperset = null
    }
    for ((position, list) in byPosition) {
        val first = list.first()
        val sg = supersetGroupOf(position)
        if (sg != null) {
            if (pendingCircuit == null || pendingSuperset != sg) {
                flush()
                pendingCircuit = mutableListOf()
                circuitBlock = first.block
                pendingSuperset = sg
            }
            pendingCircuit!!.add(position)
        } else if (first.circuit) {
            if (pendingCircuit == null || pendingSuperset != null || circuitBlock != first.block) {
                flush()
                pendingCircuit = mutableListOf()
                circuitBlock = first.block
                pendingSuperset = null
            }
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
    val settings by vm.settings.collectAsState()
    val userEquipment by vm.userEquipment.collectAsState()
    val supersetGroups by vm.supersetGroups.collectAsState()
    val recentHistory by vm.recentHistory.collectAsState()
    val rest by vm.rest.collectAsState()
    val now by vm.now.collectAsState()
    val summary by vm.summary.collectAsState()
    val finishing by vm.finishing.collectAsState()
    val context = LocalContext.current
    val c = Fit.colors

    // Foreground-only TTS engine (lazily initialized only when ttsCues is enabled).
    val tts = remember(context) { LiveWorkoutTts(context) }
    DisposableEffect(tts) { onDispose { tts.shutdown() } }

    // While this screen is in front, "rest is over" is a buzz and a short tone (or TTS), not a notification over the set list.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val alert = context.container.restAlert
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> alert.screenVisible = true
                Lifecycle.Event.ON_PAUSE -> {
                    alert.screenVisible = false
                    tts.stop()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            alert.screenVisible = false
            tts.stop()
        }
    }

    LaunchedEffect(settings.ttsCues) {
        if (settings.ttsCues) {
            tts.ensureInitialized(Locale.getDefault().language == "ar")
        } else {
            tts.stop()
        }
    }
    LaunchedEffect(vm, tts) {
        vm.ttsEvents.collect { cue ->
            val currentSettings = vm.settings.value
            val foreground = context.container.restAlert.screenVisible
            val arabic = Locale.getDefault().language == "ar"
            val text = when (cue) {
                is TtsCue.RestOver -> if (cue.nextExercise.isNullOrBlank()) {
                    vm.app.str(R.string.live41_tts_rest_over)
                } else {
                    vm.app.str(R.string.live41_tts_rest_over_next, cue.nextExercise)
                }
                is TtsCue.Hold -> tts.holdCueText(cue.event) { id -> vm.app.str(id) }
            }
            tts.speak(text, arabic, currentSettings.ttsCues, foreground) {
                // Fallback chime when no voice is available (for rest-over, RestAlert already chimes on screen).
                if (cue is TtsCue.Hold) {
                    runCatching {
                        val tg = android.media.ToneGenerator(android.media.AudioManager.STREAM_NOTIFICATION, 75)
                        tg.startTone(android.media.ToneGenerator.TONE_PROP_BEEP, 120)
                    }
                }
            }
        }
    }

    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    var confirmExit by remember { mutableStateOf(false) }
    var confirmFinish by remember { mutableStateOf(false) }
    var pendingSwap by remember { mutableStateOf<Pair<Int, ExerciseEntity>?>(null) }
    var reminded by remember { mutableStateOf(false) }
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) { vm.remindMe(); reminded = true } }
    var picker by remember { mutableStateOf<PickerMode?>(null) }
    var swapAnchors by remember { mutableStateOf(mapOf<Int, String>()) }
    var peekExerciseId by remember { mutableStateOf<String?>(null) }
    var setupNoteExercise by remember { mutableStateOf<ExerciseEntity?>(null) }
    var unitExercise by remember { mutableStateOf<ExerciseEntity?>(null) }

    summary?.let { sum ->
        WorkoutCompleteScreen(sum, reminded = reminded, onRemind = { if (Build.VERSION.SDK_INT >= 33 && !vm.app.notifier.canPost()) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS) else { vm.remindMe(); reminded = true } }, onDone = { nav.popBackStack(Routes.HOME, inclusive = false).also { ok -> if (!ok) nav.navigate(Routes.HOME) } })
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
    // Text counts leave out warm-ups, dropsets, rest-pause and cool-downs; the progress bar counts them all.
    val workSets = remember(sets) { sets.filter { countsForScore(it) } }
    val workDone = workSets.count { it.done }
    val elapsed = ((now - s.startedAt) / 1000).coerceAtLeast(0)
    val groups = remember(sets, supersetGroups) { group(sets) { pos -> supersetGroups[pos] } }
    val supersetLetters = remember(groups) {
        var idx = 0
        groups.filterIsInstance<Group.Circuit>()
            .filter { it.supersetGroup != null }
            .associate { g -> g.key to ('A' + (idx++ % 26)).toString() }
    }
    // In display order, so circuits go exercise by exercise within a round.
    val nextSet = remember(groups) { pickNextSet(groups) }
    val current = groups.indexOfFirst { g -> g.allSets.any { it.id == nextSet?.id } }
    val nextLabel = nextSet?.let { n ->
        exercises[n.exerciseId]?.let { ex ->
            val u = settings.weightUnitFor(ex.unitOverride)
            "${ex.name} · ${setSummary(n, ex.kind, u)}"
        }
    }
    val copySource = remember(nextSet, sets) { vm.copySourceFor(nextSet) }

    // List or focus mode, remembered between workouts.
    val prefs = remember { context.getSharedPreferences("live", android.content.Context.MODE_PRIVATE) }
    var focus by remember { mutableStateOf(prefs.getBoolean("focus", false)) }
    val expandedWarmups = remember { mutableStateMapOf<String, Boolean>() }
    val pager = androidx.compose.foundation.pager.rememberPagerState(initialPage = current.coerceAtLeast(0)) { groups.size.coerceAtLeast(1) }
    val nowExerciseId = remember(groups, nextSet, focus, pager.currentPage) {
        if (focus && groups.isNotEmpty()) {
            val active = groups.getOrNull(pager.currentPage)
            when (active) {
                is Group.Straight -> active.sets.firstOrNull()?.exerciseId
                is Group.Circuit -> nextSet?.takeIf { n -> active.allSets.any { it.id == n.id } }?.exerciseId
                    ?: active.allSets.firstOrNull { !it.done }?.exerciseId
                    ?: active.allSets.firstOrNull()?.exerciseId
                null -> nextSet?.exerciseId
            }
        } else {
            nextSet?.exerciseId
        }
    }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    // Follow along: after a tick, bring the exercise of the next set into view.
    var lastDone by remember { mutableStateOf(doneCount) }
    LaunchedEffect(doneCount) {
        if (doneCount > lastDone && current >= 0 && !focus) listState.animateScrollToItem(current)
        lastDone = doneCount
    }
    // How did that set feel? One strip above the rest bar, so a tick never pushes the next set down.
    val effortSetId by vm.effortSetId.collectAsState()
    val effortSet = sets.firstOrNull { it.id == effortSetId && it.done }
    var noteSet by remember { mutableStateOf<SetEntity?>(null) }
    var menu by remember { mutableStateOf(false) }
    val bottomPad = (if (rest != null) 310.dp else 160.dp) + (if (effortSet != null) 88.dp else 0.dp)
    val openPeek: (String) -> Unit = { id ->
        vm.loadQuickPeekHistory(id)
        peekExerciseId = id
    }
    val cardFor: @Composable (Group, Boolean) -> Unit = { g, forceOpen ->
        GroupCard(
            g = g,
            exercises = exercises,
            vm = vm,
            forceOpen = forceOpen,
            supersetLetter = supersetLetters[g.key],
            warmupExpanded = expandedWarmups[g.key] == true,
            onToggleWarmup = { expandedWarmups[g.key] = !(expandedWarmups[g.key] == true) },
            onVideo = { openFormVideo(context, it) },
            onSwap = { picker = PickerMode.Swap(it) },
            onPeek = openPeek,
            onEditSetupNote = { ex -> setupNoteExercise = ex },
            onEditUnit = { ex -> unitExercise = ex },
        )
    }

    Box(Modifier.fillMaxSize().background(c.background)) {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.background(c.background).statusBarsPadding()) {
                ProgressLine(if (sets.isEmpty()) 0f else doneCount / sets.size.toFloat(), c.teal, c.tealTrack, height = 4.dp)
                Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = stringResource(R.string.cd_minimize), tint = c.textPrimary) }
                    Column(Modifier.weight(1f)) {
                        Text(com.forgequest.app.data.seed.StarterPlans.displayTitle(s.title), style = MaterialTheme.typography.titleMedium, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(pluralStringResource(R.plurals.live_header, workSets.size, elapsed / 60, elapsed % 60, workDone, workSets.size), style = MaterialTheme.typography.bodySmall.tabular(), color = c.textSecondary)
                        nowExerciseId?.let { exercises[it]?.let { ex -> Text(stringResource(R.string.live_now, ex.name), style = MaterialTheme.typography.labelMedium, color = c.teal, maxLines = 1, overflow = TextOverflow.Ellipsis) } }
                    }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.cd_more), tint = c.textPrimary) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = c.cardHigh) {
                            DropdownMenuItem(
                                text = { Text(stringResource(if (focus) R.string.cd_list_mode else R.string.cd_focus_mode), color = c.textPrimary) },
                                leadingIcon = { Icon(if (focus) Icons.Rounded.ViewAgenda else Icons.Rounded.CenterFocusStrong, contentDescription = null, tint = c.textPrimary) },
                                onClick = { menu = false; focus = !focus; prefs.edit().putBoolean("focus", focus).apply() },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.live41_tts_toggle), color = c.textPrimary) },
                                trailingIcon = if (settings.ttsCues) ({ Icon(Icons.Rounded.Check, contentDescription = null, tint = c.teal) }) else null,
                                onClick = { menu = false; vm.toggleTtsCues() },
                            )
                            DropdownMenuItem(text = { Text(stringResource(R.string.live_discard), color = c.textPrimary) }, onClick = { menu = false; confirmExit = true })
                        }
                    }
                }
            }
            val deloadWeek by vm.deloadWeek.collectAsState()
            if (deloadWeek && s.planDayId != null) {
                Text(stringResource(R.string.deload_week_note), style = MaterialTheme.typography.bodySmall, color = c.textSecondary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
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
                LaunchedEffect(current) { if (current >= 0 && current != pager.currentPage) pager.animateScrollToPage(current) }
                Text(
                    stringResource(R.string.live_exercise_of, pager.currentPage + 1, groups.size), style = MaterialTheme.typography.labelLarge, color = c.textSecondary,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                val density = LocalDensity.current
                androidx.compose.foundation.pager.HorizontalPager(
                    pager, Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 16.dp), pageSpacing = 12.dp, key = { groups[it].key },
                ) { i ->
                    androidx.compose.runtime.CompositionLocalProvider(LocalDensity provides Density(density.density, density.fontScale * LIVE_TEXT_BOOST)) {
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 8.dp, bottom = bottomPad)) { cardFor(groups[i], true) }
                    }
                }
            } else LazyColumn(
                Modifier.weight(1f),
                state = listState,
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottomPad),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(groups, key = { it.key }) { g -> Box(itemMotion()) { cardFor(g, false) } }
                item {
                    TonalPill(stringResource(R.string.live_add_exercise), onClick = { picker = PickerMode.Add }, icon = Icons.Rounded.Add, modifier = Modifier.fillMaxWidth())
                }
            }
        }

        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
            AnimatedVisibility(effortSet != null, enter = slideInVertically { it }, exit = slideOutVertically { it }) {
                val shown = remember { mutableStateOf(effortSet) }
                if (effortSet != null) shown.value = effortSet
                shown.value?.let { es ->
                    Column(Modifier.fillMaxWidth().background(c.background).padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text(stringResource(R.string.live_effort_caption), style = MaterialTheme.typography.labelMedium, color = c.textSecondary, modifier = Modifier.padding(bottom = 6.dp))
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            EffortGroup(selected = es.effort, onSelect = {
                                vm.setEffort(es, it)
                                if (it == SetEffort.PAIN) noteSet = es
                            })
                            if (es.effort == SetEffort.PAIN.name) {
                                TextButton(onClick = { noteSet = es }) { Text(stringResource(R.string.live_add_note), color = c.coral) }
                            }
                        }
                    }
                }
            }
            AnimatedVisibility(rest != null, enter = slideInVertically { it }, exit = slideOutVertically { it }) {
                rest?.let { r ->
                    val left = ((r.endsAt - now) / 1000).coerceAtLeast(0)
                    val copiedText = stringResource(R.string.live41_copied_last_set)
                    RestBar(
                        secondsLeft = left.toInt(),
                        total = r.totalSeconds,
                        next = nextLabel,
                        isRestPause = r.isRestPause,
                        canCopyLastSet = nextSet != null && copySource != null,
                        onAdd = { vm.addRest(15) },
                        onRemove = { if (left > 15) vm.addRest(-15) else vm.skipRest() },
                        onSelectPreset = { sec -> vm.setRestPreset(sec) },
                        onCopyLastSet = {
                            nextSet?.let { target ->
                                vm.copyLastSetTo(target)
                                context.toast(copiedText)
                            }
                        },
                        onSkip = vm::skipRest,
                    )
                }
            }
            Box(Modifier.fillMaxWidth().background(c.background).navigationBarsPadding().padding(16.dp)) {
                val finishChecks = if (workSets.isNotEmpty()) workSets else sets
                PillButton(
                    if (finishing) stringResource(R.string.live_saving) else stringResource(R.string.live_finish_workout),
                    onClick = { if (finishChecks.any { !it.done }) confirmFinish = true else vm.finish() },
                    enabled = doneCount > 0 && !finishing,
                    modifier = Modifier.fillMaxWidth(),
                    container = if (finishChecks.all { it.done }) c.teal else c.cardHigh,
                    content = if (finishChecks.all { it.done }) c.onTeal else c.textPrimary,
                )
            }
        }

        val hold by vm.hold.collectAsState()
        hold?.let { HoldOverlay(it, now, onStop = vm::stopHold, onCancel = vm::cancelHold) }
    }

    val prMessage by vm.prMessage.collectAsState()
    val snackbar = LocalSnackbar.current
    LaunchedEffect(prMessage) { prMessage?.let { snackbar.showSnackbar(it); vm.prMessage.value = null } }

    peekExerciseId?.let { exId ->
        val ex = exercises[exId]
        if (ex != null) {
            val exUnit = settings.weightUnitFor(ex.unitOverride)
            val canUseLast = !vm.previous.collectAsState().value[exId].isNullOrEmpty() &&
                sets.any { it.exerciseId == exId && !it.done }
            val appliedMsg = stringResource(R.string.live41_peek_used_last)
            QuickExercisePeekSheet(
                exercise = ex,
                unit = exUnit,
                history = recentHistory[exId].orEmpty(),
                canUseLastTime = canUseLast,
                onDismiss = { peekExerciseId = null },
                onWatchForm = { openFormVideo(context, ex) },
                onEditSetupNote = {
                    peekExerciseId = null
                    setupNoteExercise = ex
                },
                onUseLastTime = {
                    if (vm.useLastSessionFor(exId)) {
                        context.toast(appliedMsg)
                        peekExerciseId = null
                    }
                },
                onFullStats = {
                    peekExerciseId = null
                    nav.navigate(Routes.exercise(exId, fromLive = true))
                },
            )
        }
    }

    setupNoteExercise?.let { ex ->
        val liveEx = exercises[ex.id] ?: ex
        SetupNoteDialog(
            exerciseName = liveEx.name,
            initial = liveEx.setupNote,
            onDismiss = { setupNoteExercise = null },
            onSave = { note ->
                vm.updateSetupNote(liveEx.id, note)
                setupNoteExercise = null
            },
        )
    }

    unitExercise?.let { ex ->
        val liveEx = exercises[ex.id] ?: ex
        ExerciseUnitDialog(
            exerciseName = liveEx.name,
            currentOverride = liveEx.unitOverride,
            defaultUnit = settings.weightUnit,
            onDismiss = { unitExercise = null },
            onSelect = { code ->
                vm.setExerciseUnitOverride(liveEx.id, code)
                unitExercise = null
            },
        )
    }

    val offerExactAlarm by vm.offerExactAlarm.collectAsState()
    if (offerExactAlarm) {
        AlertDialog(
            onDismissRequest = { vm.answerExactAlarm() },
            containerColor = c.card,
            title = { Text(stringResource(R.string.rest_exact_title), color = c.textPrimary) },
            text = { Text(stringResource(R.string.rest_exact_body), color = c.textSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    vm.answerExactAlarm()
                    context.container.restAlarms.exactSettingsIntent()?.let { runCatching { context.startActivity(it) } }
                }) { Text(stringResource(R.string.rest_exact_open), color = c.teal) }
            },
            dismissButton = { TextButton(onClick = { vm.answerExactAlarm() }) { Text(stringResource(R.string.cal_not_now), color = c.textSecondary) } },
        )
    }

    if (confirmFinish) {
        val finishPool = if (workSets.isNotEmpty()) workSets else sets
        val left = finishPool.count { !it.done }
        val doneInPool = finishPool.count { it.done }
        AlertDialog(
            onDismissRequest = { confirmFinish = false },
            containerColor = c.card,
            title = { Text(pluralStringResource(R.plurals.live_finish_unticked_title, left, left), color = c.textPrimary) },
            text = { Text(pluralStringResource(R.plurals.live_finish_unticked_body, left, left), color = c.textSecondary) },
            confirmButton = {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
                    androidx.compose.material3.Button(
                        onClick = { confirmFinish = false; vm.finish() },
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = c.teal, contentColor = c.onTeal),
                    ) { Text(stringResource(R.string.live_finish)) }
                    if (doneInPool * 2 >= finishPool.size) {
                        TextButton(onClick = { confirmFinish = false; vm.tickAllAndFinish() }) { Text(stringResource(R.string.live_tick_all_finish), color = c.teal) }
                    }
                    TextButton(onClick = { confirmFinish = false }) { Text(stringResource(R.string.live_keep_going), color = c.textSecondary) }
                }
            },
        )
    }

    noteSet?.let { ns ->
        val live = sets.firstOrNull { it.id == ns.id } ?: ns
        SetNoteDialog(live.note, isPain = true, onDismiss = { noteSet = null }, onSave = { vm.setNote(live, it); noteSet = null })
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
        val currentExId = (mode as? PickerMode.Swap)?.let { m -> sets.firstOrNull { it.itemPosition == m.position }?.exerciseId }
        val anchorExId = (mode as? PickerMode.Swap)?.let { m -> swapAnchors[m.position] ?: currentExId }
        val swappingEx = anchorExId?.let { exercises[it] }
        ExercisePickerSheet(
            title = if (mode is PickerMode.Swap) stringResource(R.string.live_swap_exercise) else stringResource(R.string.live_add_exercise),
            library = library,
            onPick = { ex ->
                if (mode is PickerMode.Swap) {
                    if (currentExId != null && mode.position !in swapAnchors) {
                        swapAnchors = swapAnchors + (mode.position to currentExId)
                    }
                    if (sets.any { it.itemPosition == mode.position && it.done }) {
                        pendingSwap = mode.position to ex
                    } else {
                        vm.swap(mode.position, ex.id)
                    }
                } else {
                    vm.addExercise(ex.id)
                }
                picker = null
            },
            onCreate = { name, kind, muscle ->
                if (mode is PickerMode.Swap && currentExId != null && mode.position !in swapAnchors) {
                    swapAnchors = swapAnchors + (mode.position to currentExId)
                }
                if (mode is PickerMode.Swap && sets.any { it.itemPosition == mode.position && it.done }) {
                    vm.createExercise(name, kind, muscle) { id ->
                        val created = exercises[id] ?: ExerciseEntity(id, name, kind, listOf(muscle), emptyList(), custom = true)
                        pendingSwap = mode.position to created
                    }
                } else {
                    vm.createExercise(name, kind, muscle) { id -> if (mode is PickerMode.Swap) vm.swap(mode.position, id) else vm.addExercise(id) }
                }
                picker = null
            },
            onDismiss = { picker = null },
            initialMuscle = swappingEx?.primary?.firstOrNull(),
            sourceExercise = swappingEx,
            userEquipment = userEquipment,
            currentExerciseId = currentExId,
        )
    }

    pendingSwap?.let { (pos, newEx) ->
        val oldExName = sets.firstOrNull { it.itemPosition == pos }?.let { exercises[it.exerciseId]?.name } ?: ""
        val doneInGroup = sets.count { it.itemPosition == pos && it.done }
        AlertDialog(
            onDismissRequest = { pendingSwap = null },
            containerColor = c.card,
            title = { Text(stringResource(R.string.live_swap_confirm_title, oldExName), color = c.textPrimary) },
            text = { Text(pluralStringResource(R.plurals.live_swap_confirm_body, doneInGroup, doneInGroup, newEx.name), color = c.textSecondary) },
            confirmButton = {
                TextButton(onClick = { vm.swap(pos, newEx.id); pendingSwap = null }) {
                    Text(stringResource(R.string.live_swap_confirm_action), color = c.coral)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingSwap = null }) {
                    Text(stringResource(R.string.action_cancel), color = c.textSecondary)
                }
            },
        )
    }
}

@Composable
private fun SetupNoteDialog(
    exerciseName: String,
    initial: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    val c = Fit.colors
    var text by remember(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.card,
        title = { Text("${stringResource(R.string.live41_setup_note_label)} · $exerciseName", color = c.textPrimary) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(120) },
                singleLine = true,
                shape = ForgeShape.Md,
                placeholder = { Text(stringResource(R.string.live41_setup_note_hint)) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = c.teal,
                    unfocusedBorderColor = c.divider,
                    focusedTextColor = c.textPrimary,
                    unfocusedTextColor = c.textPrimary,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
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

@Composable
private fun ExerciseUnitDialog(
    exerciseName: String,
    currentOverride: String,
    defaultUnit: WeightUnit,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    val c = Fit.colors
    val normalized = currentOverride.trim().lowercase(Locale.ROOT)
    val options = listOf(
        "" to stringResource(R.string.live41_unit_default, defaultUnit.label),
        "kg" to stringResource(R.string.live41_unit_kg),
        "lb" to stringResource(R.string.live41_unit_lb),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.card,
        title = { Text("${stringResource(R.string.live41_unit_title)} · $exerciseName", color = c.textPrimary) },
        text = {
            Column {
                options.forEach { (code, label) ->
                    val selected = normalized == code
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(ForgeShape.Sm)
                            .clickable { onSelect(code) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = selected,
                            onClick = { onSelect(code) },
                            colors = RadioButtonDefaults.colors(selectedColor = c.teal, unselectedColor = c.textSecondary),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(label, style = MaterialTheme.typography.bodyMedium, color = c.textPrimary)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel), color = c.textSecondary) } },
    )
}

internal sealed interface PickerMode {
    data object Add : PickerMode
    data class Swap(val position: Int) : PickerMode
}

/** An exercise or circuit/superset card; once every set is ticked it folds into one line (tap to open it again). */
@Composable
internal fun GroupCard(
    g: Group,
    exercises: Map<String, ExerciseEntity>,
    vm: LiveWorkoutViewModel,
    forceOpen: Boolean,
    supersetLetter: String? = null,
    warmupExpanded: Boolean = false,
    onToggleWarmup: () -> Unit = {},
    onVideo: (ExerciseEntity?) -> Unit,
    onSwap: (Int) -> Unit,
    onPeek: (String) -> Unit,
    onEditSetupNote: (ExerciseEntity) -> Unit,
    onEditUnit: (ExerciseEntity) -> Unit,
) {
    val c = Fit.colors
    val all = g.allSets
    val allDone = all.isNotEmpty() && all.all { it.done }
    var open by remember(allDone) { mutableStateOf(!allDone) }
    if (g is Group.Straight && WorkoutScore.isWarmUp(g.block)) {
        WarmupCard(
            g = g,
            ex = exercises[g.sets.first().exerciseId],
            vm = vm,
            expanded = forceOpen || warmupExpanded,
            onToggleExpand = onToggleWarmup,
            onSwap = { onSwap(g.position) },
            onPeek = { onPeek(g.sets.first().exerciseId) },
        )
        return
    }
    if (!open && !forceOpen) {
        val title = when (g) {
            is Group.Straight -> exercises[g.sets.first().exerciseId]?.name ?: g.sets.first().exerciseId
            is Group.Circuit -> if (g.supersetGroup != null) {
                g.positions.mapNotNull { pos ->
                    g.allSets.firstOrNull { it.itemPosition == pos }?.let { exercises[it.exerciseId]?.name ?: it.exerciseId }
                }.distinct().joinToString(" + ")
            } else {
                blockLabel(g.block)
            }
        }
        FitCard(onClick = { open = true }, padding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
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
            g = g,
            ex = exercises[g.sets.first().exerciseId],
            vm = vm,
            onVideo = onVideo,
            onSwap = { onSwap(g.position) },
            onPeek = { onPeek(g.sets.first().exerciseId) },
            onEditSetupNote = onEditSetupNote,
            onEditUnit = onEditUnit,
        )
        is Group.Circuit -> CircuitCard(
            g = g,
            exercises = exercises,
            vm = vm,
            supersetLetter = supersetLetter,
            onVideo = onVideo,
            onSwap = onSwap,
            onPeek = onPeek,
            onEditSetupNote = onEditSetupNote,
            onEditUnit = onEditUnit,
        )
    }
}

/** One warm-up exercise as a single row ("Jumping jacks · 5 × 10") with one tick; tapping the row opens the per-set rows. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WarmupCard(
    g: Group.Straight,
    ex: ExerciseEntity?,
    vm: LiveWorkoutViewModel,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onSwap: () -> Unit,
    onPeek: () -> Unit,
) {
    val c = Fit.colors
    val prev by vm.previous.collectAsState()
    val first = g.sets.first()
    val doneCount = g.sets.count { it.done }
    val allDone = doneCount == g.sets.size
    val each = if (ex?.kind == ExerciseKind.HOLD) stringResource(R.string.rx_seconds, first.seconds ?: 0) else "${first.reps ?: 0}"
    FitCard(modifier = Modifier.animateContentSize(), padding = PaddingValues(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.weight(1f).clip(ForgeShape.Sm).clickable(onClick = onToggleExpand).padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("${ex?.name ?: first.exerciseId} · ${g.sets.size} × $each", style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
                    Text(stringResource(R.string.live_warmup_not_counted), style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
                    if (doneCount > 0 && !allDone) Text("$doneCount/${g.sets.size}", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                }
                Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(8.dp))
            SetTick(allDone, pr = false) { vm.toggleAll(g.sets) }
        }
        FlowRow(
            Modifier.padding(start = 4.dp, top = 4.dp, end = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            TonalPill(stringResource(R.string.cues_title), onClick = onPeek, icon = Icons.Rounded.Info, tint = c.teal)
            TonalPill(stringResource(R.string.live_swap_exercise), onClick = onSwap, icon = Icons.Rounded.SwapHoriz, tint = c.teal)
            TonalPill(
                stringResource(if (allDone) R.string.live_warmup_done else R.string.live_mark_warmup_done),
                onClick = { vm.toggleAll(g.sets) },
                icon = Icons.Rounded.Check,
                tint = if (allDone) c.teal else c.textPrimary,
            )
        }
        if (expanded) {
            Spacer(Modifier.height(4.dp))
            g.sets.forEach { set ->
                SetRow(
                    label = "${set.setIndex + 1}",
                    sublabel = null,
                    set = set,
                    kind = ex?.kind ?: ExerciseKind.REPS,
                    exercise = ex,
                    vm = vm,
                    previous = prev[set.exerciseId]?.getOrNull(set.setIndex),
                )
            }
        }
    }
}

/** "12 reps · 10 kg" or "30 s", for the next-up lines. */
@Composable
internal fun setSummary(set: SetEntity, kind: ExerciseKind, unit: WeightUnit): String {
    val reps = set.reps ?: 0
    val main = if (kind == ExerciseKind.HOLD) stringResource(R.string.rx_seconds, set.seconds ?: 0) else pluralStringResource(R.plurals.rx_reps, reps, reps)
    val kg = set.weightKg
    return if (kg != null && (kg > 0f || kind == ExerciseKind.WEIGHTED)) "$main · ${unit.format(kg)}" else main
}

internal fun localizeTargetLabel(raw: String, repsWord: String): String = when {
    raw.endsWith(" reps") -> raw.removeSuffix("reps") + repsWord
    raw.endsWith(" rep") -> raw.removeSuffix("rep") + repsWord
    else -> raw
}

private fun whyRes(r: Progression.Reason) = when (r) {
    Progression.Reason.LOAD_UP -> R.string.rx_why_load_up
    Progression.Reason.MORE_REPS -> R.string.rx_why_more_reps
    Progression.Reason.MORE_TIME -> R.string.rx_why_more_time
    Progression.Reason.ONCE_MORE -> R.string.rx_why_once_more
    Progression.Reason.SAME -> R.string.rx_why_same
    Progression.Reason.HARD -> R.string.rx_why_hard
    Progression.Reason.PAIN -> R.string.rx_why_pain
    Progression.Reason.MISSED -> R.string.rx_why_missed
    Progression.Reason.BACK_OFF -> R.string.rx_why_back_off
    Progression.Reason.FATIGUE -> R.string.rx_why_fatigue
}

@Composable
internal fun StraightCard(
    g: Group.Straight,
    ex: ExerciseEntity?,
    vm: LiveWorkoutViewModel,
    onVideo: (ExerciseEntity?) -> Unit,
    onSwap: () -> Unit,
    onPeek: () -> Unit,
    onEditSetupNote: (ExerciseEntity) -> Unit,
    onEditUnit: (ExerciseEntity) -> Unit,
) {
    val c = Fit.colors
    val prev by vm.previous.collectAsState()
    val reasons by vm.reasons.collectAsState()
    val settings by vm.settings.collectAsState()
    var menu by remember { mutableStateOf(false) }
    val exUnit = settings.weightUnitFor(ex?.unitOverride.orEmpty())

    FitCard(modifier = Modifier.animateContentSize(), padding = PaddingValues(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).clip(ForgeShape.Sm).clickable(onClick = onPeek).padding(horizontal = 4.dp)) {
                Text(ex?.name ?: g.sets.first().exerciseId, style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
                val target = localizeTargetLabel(g.sets.first().targetLabel, stringResource(R.string.rx_reps_only))
                Text(if (target.isNotBlank()) stringResource(R.string.live_target, g.sets.size, target) else ex?.musclesLabel().orEmpty(), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                reasons[g.sets.first().exerciseId]?.let { Text(stringResource(whyRes(it)), style = MaterialTheme.typography.labelSmall, color = c.textSecondary) }
                exerciseCues(g.sets.first().exerciseId)?.let { Text(stringResource(it.first), style = MaterialTheme.typography.labelSmall, color = c.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                if (!ex?.setupNote.isNullOrBlank()) {
                    Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.PushPin, contentDescription = null, tint = c.blue, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            stringResource(R.string.live41_setup_note_prefix, ex!!.setupNote),
                            style = MaterialTheme.typography.labelMedium,
                            color = c.blue,
                        )
                    }
                }
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.cd_options), tint = c.textSecondary) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = c.cardHigh) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.cd_watch_form)) }, onClick = { menu = false; onVideo(ex) })
                    if (ex != null) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.live41_setup_note_label)) }, onClick = { menu = false; onEditSetupNote(ex) })
                        if (ex.kind == ExerciseKind.WEIGHTED) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.live41_unit_menu, exUnit.label)) },
                                onClick = { menu = false; onEditUnit(ex) },
                            )
                        }
                    }
                    DropdownMenuItem(text = { Text(stringResource(R.string.live_swap_exercise)) }, onClick = { menu = false; onSwap() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.live_add_set)) }, onClick = { menu = false; vm.addSet(g.position) })
                    DropdownMenuItem(text = { Text(stringResource(R.string.live_remove_last_set)) }, onClick = { menu = false; vm.removeLastSet(g.position) })
                }
            }
        }
        Row(Modifier.padding(start = 4.dp, top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TonalPill(stringResource(R.string.cues_title), onClick = onPeek, icon = Icons.Rounded.Info, tint = c.teal)
            TonalPill(stringResource(R.string.live_swap_exercise), onClick = onSwap, icon = Icons.Rounded.SwapHoriz, tint = c.teal)
        }
        ex?.let { HarderNote(it, vm) }
        AdjustmentCue(g.sets.first().exerciseId, vm)
        Spacer(Modifier.height(4.dp))
        g.sets.forEach { set ->
            SetRow(
                label = "${set.setIndex + 1}",
                sublabel = null,
                set = set,
                kind = ex?.kind ?: ExerciseKind.REPS,
                exercise = ex,
                vm = vm,
                previous = prev[set.exerciseId]?.getOrNull(set.setIndex),
            )
        }
    }
}

/** "Wide knee push-up is getting easy": one tap switches to the harder version, or a tempo tip when there is none. */
@Composable
internal fun HarderNote(ex: ExerciseEntity, vm: LiveWorkoutViewModel) {
    val c = Fit.colors
    val hints by vm.harder.collectAsState()
    val hint = hints[ex.id] ?: return
    val context = LocalContext.current
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
            val nextTime = stringResource(R.string.v42_harder_next_time, next.name)
            TextButton(onClick = {
                vm.stepUp(ex.id, next.id) { swapped ->
                    context.toast(if (swapped == 0) nextTime else done)
                }
            }) { Text(stringResource(R.string.harder_switch), color = c.teal) }
        }
    }
}

/** One-line cue under an exercise card header after an intra-session adjustment, with an Undo action. */
@Composable
internal fun AdjustmentCue(exerciseId: String, vm: LiveWorkoutViewModel) {
    val c = Fit.colors
    val adj by vm.adjustment.collectAsState()
    val current = adj?.takeIf { it.exerciseId == exerciseId } ?: return
    val text = when {
        current.percent != null -> stringResource(R.string.live_adjusted_load, current.percent)
        current.reps != null -> stringResource(R.string.live_adjusted_reps, current.reps)
        current.seconds != null -> stringResource(R.string.live_adjusted_seconds, current.seconds)
        else -> return
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, end = 8.dp)
            .clip(ForgeShape.Sm)
            .background(c.goldTrack)
            .padding(start = 12.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = c.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = vm::undoAdjustment) {
            Text(stringResource(R.string.live_adjust_undo), color = c.gold)
        }
    }
}

@Composable
internal fun CircuitCard(
    g: Group.Circuit,
    exercises: Map<String, ExerciseEntity>,
    vm: LiveWorkoutViewModel,
    supersetLetter: String? = null,
    onVideo: (ExerciseEntity?) -> Unit,
    onSwap: (Int) -> Unit,
    onPeek: (String) -> Unit,
    onEditSetupNote: (ExerciseEntity) -> Unit,
    onEditUnit: (ExerciseEntity) -> Unit,
) {
    val c = Fit.colors
    val settings by vm.settings.collectAsState()
    val isSuperset = g.supersetGroup != null
    val letter = supersetLetter ?: "A"
    val posLabel = remember(g.positions, letter, isSuperset) {
        if (!isSuperset) emptyMap()
        else g.positions.mapIndexed { idx, pos -> pos to "$letter${idx + 1}" }.toMap()
    }
    FitCard(padding = PaddingValues(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (isSuperset) stringResource(R.string.live41_superset) else blockLabel(g.block),
                style = MaterialTheme.typography.titleMedium,
                color = c.textPrimary,
            )
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Rounded.Repeat, contentDescription = null, tint = c.teal, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(
                if (isSuperset) g.positions.indices.joinToString("/") { "$letter${it + 1}" } else stringResource(R.string.live_circuit),
                style = MaterialTheme.typography.labelMedium,
                color = c.teal,
            )
        }
        Text(
            if (isSuperset) stringResource(R.string.live41_superset_hint, "${letter}1", "${letter}${g.positions.size}") else stringResource(R.string.live_circuit_hint),
            style = MaterialTheme.typography.bodySmall,
            color = c.textSecondary,
        )
        val distinctEx = g.rounds.firstOrNull().orEmpty().mapNotNull { exercises[it.exerciseId] }.distinctBy { it.id }
        distinctEx.forEach { ex ->
            if (!ex.setupNote.isBlank()) {
                Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.PushPin, contentDescription = null, tint = c.blue, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("${ex.name}: ${ex.setupNote}", style = MaterialTheme.typography.labelSmall, color = c.blue)
                }
            }
            HarderNote(ex, vm)
            AdjustmentCue(ex.id, vm)
        }
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
                val slotTag = posLabel[set.itemPosition]
                val exName = ex?.name ?: set.exerciseId
                val rowSublabel = if (slotTag != null) "$slotTag · $exName" else exName
                Box {
                    SetRow(
                        label = slotTag,
                        sublabel = rowSublabel,
                        set = set,
                        kind = ex?.kind ?: ExerciseKind.REPS,
                        exercise = ex,
                        vm = vm,
                        onLabelClick = { menu = true },
                        previous = prev[set.exerciseId]?.getOrNull(r),
                    )
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = c.cardHigh) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.cues_title)) }, onClick = { menu = false; onPeek(set.exerciseId) })
                        DropdownMenuItem(text = { Text(stringResource(R.string.cd_watch_form)) }, onClick = { menu = false; onVideo(ex) })
                        if (ex != null) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.live41_setup_note_label)) }, onClick = { menu = false; onEditSetupNote(ex) })
                            if (ex.kind == ExerciseKind.WEIGHTED) {
                                val exUnit = settings.weightUnitFor(ex.unitOverride)
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.live41_unit_menu, exUnit.label)) },
                                    onClick = { menu = false; onEditUnit(ex) },
                                )
                            }
                        }
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
