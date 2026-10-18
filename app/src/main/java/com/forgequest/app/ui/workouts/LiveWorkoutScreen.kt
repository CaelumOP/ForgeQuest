package com.forgequest.app.ui.workouts

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.forgequest.app.data.db.ExerciseEntity
import com.forgequest.app.data.db.SetEntity
import com.forgequest.app.domain.ExerciseKind
import com.forgequest.app.ui.Routes
import com.forgequest.app.ui.appViewModel
import com.forgequest.app.ui.components.FitCard
import com.forgequest.app.ui.components.IconCircle
import com.forgequest.app.ui.components.PillButton
import com.forgequest.app.ui.components.ProgressLine
import com.forgequest.app.ui.components.NumberStepper
import com.forgequest.app.ui.components.TonalPill
import com.forgequest.app.ui.theme.Fit
import com.forgequest.app.ui.workouts.LiveWorkoutViewModel.Field

/** One visual group: a circuit block (shown round by round) or a single straight-sets exercise. */
private sealed interface Group {
    data class Straight(val position: Int, val block: String, val sets: List<SetEntity>) : Group
    data class Circuit(val block: String, val positions: List<Int>, val rounds: List<List<SetEntity>>) : Group
}

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

@Composable
fun LiveWorkoutScreen(nav: NavHostController) {
    val vm = appViewModel { LiveWorkoutViewModel(it) }
    val sessionState by vm.sessionState.collectAsState()
    val sets by vm.sets.collectAsState()
    val exercises by vm.exercises.collectAsState()
    val library by vm.library.collectAsState()
    val rest by vm.rest.collectAsState()
    val now by vm.now.collectAsState()
    val summary by vm.summary.collectAsState()
    val finishing by vm.finishing.collectAsState()
    val context = LocalContext.current
    val c = Fit.colors

    var confirmExit by remember { mutableStateOf(false) }
    var confirmFinish by remember { mutableStateOf(false) }
    var picker by remember { mutableStateOf<PickerMode?>(null) }

    summary?.let { sum ->
        WorkoutCompleteScreen(sum, onDone = { nav.popBackStack(Routes.HOME, inclusive = false).also { ok -> if (!ok) nav.navigate(Routes.HOME) } })
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

    Box(Modifier.fillMaxSize().background(c.background)) {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.background(c.background).statusBarsPadding()) {
                ProgressLine(if (sets.isEmpty()) 0f else doneCount / sets.size.toFloat(), c.teal, c.tealTrack, height = 4.dp)
                Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.Rounded.Close, contentDescription = "Minimize", tint = c.textPrimary) }
                    Column(Modifier.weight(1f)) {
                        Text(s.title, style = MaterialTheme.typography.titleMedium, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("%d:%02d · %d of %d sets".format(elapsed / 60, elapsed % 60, doneCount, sets.size), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                    }
                    TextButton(onClick = { confirmExit = true }) { Text("Discard", color = c.textSecondary) }
                }
            }
            LazyColumn(
                Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 160.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(groups, key = { g -> if (g is Group.Straight) "s${g.position}" else "c${(g as Group.Circuit).positions.first()}" }) { g ->
                    when (g) {
                        is Group.Straight -> StraightCard(
                            g, exercises[g.sets.first().exerciseId], vm,
                            onVideo = { openFormVideo(context, it) },
                            onSwap = { picker = PickerMode.Swap(g.position) },
                            onDetail = { nav.navigate(Routes.exercise(g.sets.first().exerciseId)) },
                        )
                        is Group.Circuit -> CircuitCard(
                            g, exercises, vm,
                            onVideo = { openFormVideo(context, it) },
                            onSwap = { picker = PickerMode.Swap(it) },
                        )
                    }
                }
                item {
                    TonalPill("Add exercise", onClick = { picker = PickerMode.Add }, icon = Icons.Rounded.Add, modifier = Modifier.fillMaxWidth())
                }
            }
        }

        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
            AnimatedVisibility(rest != null, enter = slideInVertically { it }, exit = slideOutVertically { it }) {
                rest?.let { r ->
                    val left = ((r.endsAt - now) / 1000).coerceAtLeast(0)
                    RestBar(left.toInt(), r.totalSeconds, onAdd = { vm.addRest(15) }, onSkip = vm::skipRest)
                }
            }
            Box(Modifier.fillMaxWidth().background(c.background).navigationBarsPadding().padding(16.dp)) {
                PillButton(
                    if (finishing) "Saving…" else "Finish workout",
                    onClick = { if (sets.any { !it.done }) confirmFinish = true else vm.finish() },
                    enabled = doneCount > 0 && !finishing,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    if (confirmFinish) {
        val left = sets.count { !it.done }
        AlertDialog(
            onDismissRequest = { confirmFinish = false },
            containerColor = c.card,
            title = { Text("Finish with $left set${if (left == 1) "" else "s"} unticked?", color = c.textPrimary) },
            text = { Text("Only ticked sets are saved. The $left you didn't tick ${if (left == 1) "is" else "are"} dropped.", color = c.textSecondary) },
            confirmButton = { TextButton(onClick = { confirmFinish = false; vm.finish() }) { Text("Finish", color = c.teal) } },
            dismissButton = { TextButton(onClick = { confirmFinish = false }) { Text("Keep going", color = c.textSecondary) } },
        )
    }

    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            containerColor = c.card,
            title = { Text("Discard this workout?", color = c.textPrimary) },
            text = { Text("Logged sets from this session will be deleted.", color = c.textSecondary) },
            confirmButton = { TextButton(onClick = { confirmExit = false; vm.discard { nav.popBackStack() } }) { Text("Discard", color = c.coral) } },
            dismissButton = { TextButton(onClick = { confirmExit = false }) { Text("Keep going", color = c.teal) } },
        )
    }

    picker?.let { mode ->
        ExercisePickerSheet(
            title = if (mode is PickerMode.Swap) "Swap exercise" else "Add exercise",
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
        )
    }
}

private sealed interface PickerMode {
    data object Add : PickerMode
    data class Swap(val position: Int) : PickerMode
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
    var menu by remember { mutableStateOf(false) }
    FitCard(padding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).clickable(onClick = onDetail)) {
                Text(ex?.name ?: g.sets.first().exerciseId, style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
                val target = g.sets.first().targetLabel
                Text(if (target.isNotBlank()) "Target ${g.sets.size} × $target" else ex?.musclesLabel().orEmpty(), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = "Options", tint = c.textSecondary) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = c.cardHigh) {
                    DropdownMenuItem(text = { Text("Watch form") }, onClick = { menu = false; onVideo(ex) })
                    DropdownMenuItem(text = { Text("Swap exercise") }, onClick = { menu = false; onSwap() })
                    DropdownMenuItem(text = { Text("Add set") }, onClick = { menu = false; vm.addSet(g.position) })
                    DropdownMenuItem(text = { Text("Remove last set") }, onClick = { menu = false; vm.removeLastSet(g.position) })
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        g.sets.forEach { set ->
            SetRow(label = "${set.setIndex + 1}", sublabel = null, set = set, kind = ex?.kind ?: ExerciseKind.REPS, vm = vm)
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
            Text(g.block, style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Rounded.Repeat, contentDescription = null, tint = c.teal, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Circuit", style = MaterialTheme.typography.labelMedium, color = c.teal)
        }
        Text("One set of each, then repeat.", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
        g.rounds.forEachIndexed { r, round ->
            val roundDone = round.all { it.done }
            Spacer(Modifier.height(12.dp))
            Text(
                "Round ${r + 1} of ${g.rounds.size}" + if (roundDone) " · done" else "",
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
                    )
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = c.cardHigh) {
                        DropdownMenuItem(text = { Text("Watch form") }, onClick = { menu = false; onVideo(ex) })
                        DropdownMenuItem(text = { Text("Swap exercise") }, onClick = { menu = false; onSwap(set.itemPosition) })
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = { vm.addRound(g.positions) }) {
            Icon(Icons.Rounded.Add, contentDescription = null, tint = c.teal, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Add round", color = c.teal)
        }
    }
}

@Composable
private fun SetRow(
    label: String?,
    sublabel: String?,
    set: SetEntity,
    kind: ExerciseKind,
    vm: LiveWorkoutViewModel,
    onLabelClick: (() -> Unit)? = null,
) {
    val c = Fit.colors
    val rowColor by animateColorAsState(if (set.done) c.tealTrack else androidx.compose.ui.graphics.Color.Transparent, label = "row")
    Row(
        Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(14.dp)).background(rowColor).padding(start = 8.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
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
                Text(sublabel, style = MaterialTheme.typography.bodyMedium, color = c.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (set.targetLabel.isNotBlank() && sublabel != null) {
                    Text(set.targetLabel, style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
                }
                if (set.isPr) {
                    Spacer(Modifier.width(6.dp))
                    PrTag()
                }
            }
        }
        Spacer(Modifier.width(6.dp))
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            when (kind) {
                ExerciseKind.HOLD -> NumberStepper(
                    (set.seconds ?: 0).toFloat(), { vm.setField(set, Field.SECONDS, it) }, step = 5f, min = 0f, max = 3600f,
                    unit = "s", title = "Hold time", minValueWidth = 52.dp,
                )
                ExerciseKind.REPS -> NumberStepper(
                    (set.reps ?: 0).toFloat(), { vm.setField(set, Field.REPS, it) }, step = 1f, min = 0f, max = 999f,
                    title = "Reps", minValueWidth = 40.dp,
                )
                ExerciseKind.WEIGHTED -> {
                    NumberStepper(
                        (set.reps ?: 0).toFloat(), { vm.setField(set, Field.REPS, it) }, step = 1f, min = 0f, max = 999f,
                        unit = "reps", title = "Reps", minValueWidth = 64.dp,
                    )
                    val unit by vm.unit.collectAsState()
                    NumberStepper(
                        unit.fromKg(set.weightKg ?: 0f), { vm.setField(set, Field.WEIGHT, unit.toKg(it)) }, step = unit.loadStep, min = 0f, max = unit.fromKg(999f),
                        unit = unit.label, decimals = 1, title = "Weight", minValueWidth = 64.dp, valueColor = c.blue,
                    )
                }
            }
        }
        Spacer(Modifier.width(6.dp))
        MorphCheck(set.done) { vm.toggle(set) }
    }
}

/** Squircle that springs into a filled circle with a check when ticked. */
@Composable
private fun MorphCheck(checked: Boolean, onToggle: () -> Unit) {
    val c = Fit.colors
    val corner by animateDpAsState(if (checked) 22.dp else 10.dp, spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "corner")
    val color by animateColorAsState(if (checked) c.teal else c.cardHigh, label = "fill")
    val checkScale by animateFloatAsState(if (checked) 1f else 0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium), label = "check")
    Box(
        Modifier.size(44.dp).clip(RoundedCornerShape(corner)).background(color)
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle() })
            .semantics { contentDescription = "Set done" },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.Check, contentDescription = null, tint = c.onTeal, modifier = Modifier.size(24.dp).scale(checkScale))
    }
}

@Composable
fun PrTag() {
    val c = Fit.colors
    Box(Modifier.clip(RoundedCornerShape(50)).background(c.goldTrack).padding(horizontal = 6.dp, vertical = 1.dp)) {
        Text("PR", style = MaterialTheme.typography.labelSmall, color = c.gold, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun RestBar(secondsLeft: Int, total: Int, onAdd: () -> Unit, onSkip: () -> Unit) {
    val c = Fit.colors
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(20.dp)).background(c.cardHigh).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Rest", style = MaterialTheme.typography.titleSmall, color = c.textSecondary)
            Spacer(Modifier.width(10.dp))
            Text("%d:%02d".format(secondsLeft / 60, secondsLeft % 60), style = MaterialTheme.typography.titleLarge, color = c.blue, modifier = Modifier.weight(1f))
            TextButton(onClick = onAdd) { Text("+15 s", color = c.textPrimary) }
            TextButton(onClick = onSkip) { Text("Skip", color = c.teal) }
        }
        Spacer(Modifier.height(8.dp))
        ProgressLine(if (total == 0) 0f else secondsLeft / total.toFloat(), c.blue, c.blueTrack, height = 4.dp)
    }
}

@Composable
private fun WorkoutCompleteScreen(sum: WorkoutSummary, onDone: () -> Unit) {
    val c = Fit.colors
    var filled by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { filled = true }
    val leveledUp = sum.after.level > sum.before.level
    BackHandler(onBack = onDone)
    Column(
        Modifier.fillMaxSize().background(c.background).statusBarsPadding().navigationBarsPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(32.dp))
        IconCircle(Icons.Rounded.Check, c.onTeal, c.teal, 88.dp)
        Spacer(Modifier.height(20.dp))
        Text("Workout complete", style = MaterialTheme.typography.headlineMedium, color = c.textPrimary)
        Text(sum.title, style = MaterialTheme.typography.bodyLarge, color = c.textSecondary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(28.dp))
        Row(Modifier.fillMaxWidth()) {
            com.forgequest.app.ui.components.StatColumn("${sum.doneSets}", "Sets", c.teal, Modifier.weight(1f))
            com.forgequest.app.ui.components.StatColumn("${sum.minutes} min", "Time", c.blue, Modifier.weight(1f))
            com.forgequest.app.ui.components.StatColumn("+${sum.xpGained}", "XP", c.gold, Modifier.weight(1f))
        }
        Spacer(Modifier.height(24.dp))
        FitCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (leveledUp) "Level up! Level ${sum.after.level}" else "Level ${sum.after.level}",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (leveledUp) c.gold else c.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                Text("Rank ${sum.after.rank.letter} · ${sum.after.rank.title}", style = MaterialTheme.typography.labelLarge, color = c.blue)
            }
            Spacer(Modifier.height(12.dp))
            val from = if (leveledUp) 0f else sum.before.progress
            ProgressLine(if (filled) sum.after.progress else from, c.blue, c.blueTrack, height = 8.dp)
            Spacer(Modifier.height(6.dp))
            Text("${sum.after.xpIntoLevel} / ${sum.after.xpForNextLevel} XP to level ${sum.after.level + 1}", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
        }
        if (sum.prs.isNotEmpty() || sum.newBadges.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            FitCard {
                sum.prs.forEach { name ->
                    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        PrTag()
                        Spacer(Modifier.width(10.dp))
                        Text("New record: $name", style = MaterialTheme.typography.bodyMedium, color = c.textPrimary)
                    }
                }
                sum.newBadges.forEach { title ->
                    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.EmojiEvents, contentDescription = null, tint = c.gold, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("Badge unlocked: $title", style = MaterialTheme.typography.bodyMedium, color = c.textPrimary)
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        PillButton("Done", onClick = onDone, modifier = Modifier.fillMaxWidth())
    }
}
