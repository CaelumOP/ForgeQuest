package com.forgequest.app.ui.plan

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.forgequest.app.AppContainer
import com.forgequest.app.R
import com.forgequest.app.data.db.ExerciseEntity
import com.forgequest.app.domain.ExerciseKind
import com.forgequest.app.data.plan.PlanAnswers
import com.forgequest.app.data.plan.PlanDraftDay
import com.forgequest.app.data.plan.PlanDraftItem
import com.forgequest.app.domain.Cardio
import com.forgequest.app.domain.Dates
import com.forgequest.app.domain.Equipment
import com.forgequest.app.domain.EquipmentPreset
import com.forgequest.app.domain.Experience
import com.forgequest.app.domain.Injury
import com.forgequest.app.domain.Muscle
import com.forgequest.app.domain.BuiltPlan
import com.forgequest.app.domain.PlanBuilder
import com.forgequest.app.domain.RepPref
import com.forgequest.app.domain.RestPref
import com.forgequest.app.domain.TrainGoal
import com.forgequest.app.ui.appViewModel
import com.forgequest.app.ui.components.FitCard
import com.forgequest.app.ui.components.FitSwitch
import com.forgequest.app.ui.components.NumberStepper
import com.forgequest.app.ui.components.PillButton
import com.forgequest.app.ui.components.SegmentedTabs
import com.forgequest.app.ui.components.TonalPill
import com.forgequest.app.ui.components.toast
import com.forgequest.app.ui.localLabel
import com.forgequest.app.ui.profile.shortLabel
import com.forgequest.app.ui.theme.Fit
import com.forgequest.app.ui.theme.ForgeShape
import com.forgequest.app.ui.theme.ForgeSpace
import com.forgequest.app.ui.workouts.ExercisePickerSheet
import com.forgequest.app.ui.workouts.localName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Equipment setting text for [equipment]: a preset's own encoding when it matches one, else the item names. */
internal fun equipmentSetting(equipment: Set<Equipment>): String {
    val all = equipment + Equipment.BODYWEIGHT
    return EquipmentPreset.entries.firstOrNull { it.equipment == all }?.encode() ?: all.joinToString(",") { it.name }
}

class PlanBuilderViewModel(private val app: AppContainer) : ViewModel() {
    val library: StateFlow<List<ExerciseEntity>> = app.workouts.library.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** The answers on screen. Starts from the saved ones, else the saved equipment, else defaults. */
    val answers = MutableStateFlow(PlanAnswers())
    val ready = MutableStateFlow(false)

    /** Non-null shows the review: the built plan, with any swaps already made. */
    val built = MutableStateFlow<BuiltPlan?>(null)
    val busy = MutableStateFlow(false)

    /** Exercises already tried per slot ("day:index"), so repeated swaps step on instead of bouncing between two. */
    private val tried = mutableMapOf<String, Set<String>>()

    /** How many times "Show a different version" was used since the answers last changed. */
    private var offset = 0

    init {
        viewModelScope.launch {
            val s = app.settings.current()
            answers.value = PlanAnswers.decode(s.planBuilderAnswers)
                ?: s.configuredEquipment.takeIf { it.isNotEmpty() }?.let { PlanAnswers(equipment = it + Equipment.BODYWEIGHT) }
                ?: PlanAnswers()
            ready.value = true
        }
    }

    fun change(f: (PlanAnswers) -> PlanAnswers) = answers.update(f)

    fun build(different: Boolean = false) {
        if (busy.value) return
        offset = if (different) offset + 1 else 0
        busy.value = true
        viewModelScope.launch {
            try {
                val lib = app.workouts.library.first()
                val input = answers.value.toInput().copy(offset = offset)
                val plan = withContext(Dispatchers.Default) { PlanBuilder.build(input, lib) }
                tried.clear()
                built.value = plan
            } finally {
                busy.value = false
            }
        }
    }

    fun backToAnswers() { built.value = null }

    /** Replaces the exercise at [index] on [dow] with the next best one for the same movement. */
    fun swap(dow: Int, index: Int) {
        val plan = built.value ?: return
        val day = plan.draft.days.firstOrNull { it.dayOfWeek == dow } ?: return
        val old = day.items.getOrNull(index) ?: return
        viewModelScope.launch {
            val lib = app.workouts.library.first()
            val current = lib.firstOrNull { it.id == old.exerciseId } ?: return@launch
            val input = answers.value.toInput()
            val inDay = day.items.map { it.exerciseId }.toSet()
            val key = "$dow:$index"
            val seen = (tried[key] ?: emptySet()) + old.exerciseId
            var next = PlanBuilder.alternatives(current, input, lib, exclude = inDay + seen, limit = 1).firstOrNull()
            var nowSeen = seen
            if (next == null) {
                // Out of new ones: start the loop again rather than doing nothing.
                nowSeen = setOf(old.exerciseId)
                next = PlanBuilder.alternatives(current, input, lib, exclude = inDay, limit = 1).firstOrNull()
            }
            val pick = next ?: return@launch
            tried[key] = nowSeen + pick.id
            val hold = pick.kind == ExerciseKind.HOLD
            val item = old.copy(
                exerciseId = pick.id, name = pick.name, kind = pick.kind.name, muscles = pick.primary.map { it.name },
                repsMin = if (hold) null else old.repsMin ?: 8,
                repsMax = if (hold) null else old.repsMax ?: 12,
                seconds = if (hold) old.seconds ?: 30 else null,
            )
            built.update { b ->
                b?.copy(
                    draft = b.draft.copy(
                        days = b.draft.days.map { d ->
                            if (d.dayOfWeek == dow) d.copy(items = d.items.toMutableList().also { it[index] = item }) else d
                        },
                    ),
                )
            }
        }
    }

    /** Saves the shown plan (day titles and notes written in the app language), remembers the answers and the gear. */
    fun use(onDone: () -> Unit) {
        val plan = built.value ?: return
        if (busy.value) return
        busy.value = true
        val a = answers.value
        viewModelScope.launch {
            try {
                val draft = localizeBuiltPlan(app, plan, a.goal)
                app.workouts.applyPlan(draft, "builder")
                app.settings.update { it.copy(equipment = equipmentSetting(a.equipment), planBuilderAnswers = a.encode()) }
                onDone()
            } finally {
                busy.value = false
            }
        }
    }
}

@Composable
fun PlanBuilderScreen(nav: NavHostController) {
    val vm = appViewModel { PlanBuilderViewModel(it) }
    val ready by vm.ready.collectAsState()
    val a by vm.answers.collectAsState()
    val built by vm.built.collectAsState()
    val busy by vm.busy.collectAsState()
    val library by vm.library.collectAsState()
    val context = LocalContext.current
    val c = Fit.colors
    val review = built

    BackHandler(enabled = review != null) { vm.backToAnswers() }
    if (!ready) return Box(Modifier.fillMaxSize().background(c.background))

    Column(Modifier.fillMaxSize().background(c.background).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (review != null) vm.backToAnswers() else nav.popBackStack() }) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.cd_back), tint = c.textPrimary)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(if (review != null) R.string.pb_review_title else R.string.pb_title),
                    style = MaterialTheme.typography.titleLarge, color = c.textPrimary, modifier = Modifier.semantics { heading() },
                )
                Text(
                    stringResource(if (review != null) R.string.pb_review_hint else R.string.pb_quick_hint),
                    style = MaterialTheme.typography.bodySmall, color = c.textSecondary,
                )
            }
        }
        if (review == null) {
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = ForgeSpace.Gutter).padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                QuickCard(a, vm::change)
                FitMeSection(a, vm::change, library)
            }
            Column(Modifier.fillMaxWidth().background(c.background).padding(horizontal = ForgeSpace.Gutter, vertical = 12.dp).navigationBarsPadding()) {
                PillButton(
                    stringResource(if (busy) R.string.pb_building else R.string.pb_build),
                    onClick = { vm.build() },
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Rounded.Tune,
                    enabled = a.days.isNotEmpty() && library.isNotEmpty() && !busy,
                )
            }
        } else {
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = ForgeSpace.Gutter).padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (review.draft.days.isEmpty()) {
                    FitCard { Text(stringResource(R.string.pb_empty), style = MaterialTheme.typography.bodyLarge, color = c.textPrimary) }
                }
                review.draft.days.sortedBy { it.dayOfWeek }.forEach { day ->
                    ReviewDay(day, review, onSwap = { index -> vm.swap(day.dayOfWeek, index) })
                }
            }
            Column(
                Modifier.fillMaxWidth().background(c.background).padding(horizontal = ForgeSpace.Gutter, vertical = 12.dp).navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PillButton(
                    stringResource(R.string.pb_use),
                    onClick = {
                        vm.use {
                            context.toast(context.getString(R.string.pb_saved))
                            nav.popBackStack()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !busy && review.draft.days.isNotEmpty(),
                )
                TonalPill(stringResource(R.string.pb_another), onClick = { vm.build(different = true) }, modifier = Modifier.fillMaxWidth())
                TonalPill(stringResource(R.string.pb_change), onClick = vm::backToAnswers, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

// ---- the four quick answers ----

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickCard(a: PlanAnswers, change: ((PlanAnswers) -> PlanAnswers) -> Unit) {
    val c = Fit.colors
    val minuteOptions = listOf(20, 30, 45, 60)
    FitCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Label(stringResource(R.string.pb_goal))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TrainGoal.entries.forEach { g ->
                    AnswerChip(stringResource(g.pbRes()), a.goal == g) { change { it.copy(goal = g) } }
                }
            }
            Label(stringResource(R.string.pb_days))
            SegmentedTabs(
                options = (2..6).map { it.toString() },
                selected = (a.days.size - 2).takeIf { it in 0..4 } ?: -1,
                onSelect = { i -> change { it.copy(days = PlanBuilder.spreadDays(i + 2), minutesByDay = emptyMap()) } },
                modifier = Modifier.heightIn(min = 48.dp),
            )
            Label(stringResource(R.string.pb_minutes))
            SegmentedTabs(
                options = minuteOptions.map { it.toString() },
                selected = minuteOptions.indexOf(a.minutes),
                onSelect = { i -> change { it.copy(minutes = minuteOptions[i], minutesByDay = emptyMap()) } },
                modifier = Modifier.heightIn(min = 48.dp),
            )
            Label(stringResource(R.string.pb_equipment))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                EquipmentPreset.entries.forEach { p ->
                    AnswerChip(p.shortLabel(), a.equipment == p.equipment) { change { it.copy(equipment = p.equipment) } }
                }
            }
        }
    }
}

// ---- the optional deep section ----

@Composable
private fun FitMeSection(a: PlanAnswers, change: ((PlanAnswers) -> PlanAnswers) -> Unit, library: List<ExerciseEntity>) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var open by rememberSaveable { mutableStateOf("") }
    TonalPill(
        stringResource(R.string.pb_fit_me),
        onClick = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth(),
        icon = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
    )
    AnimatedVisibility(expanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
        val auto = stringResource(R.string.pb_auto)
        val sep = stringResource(R.string.list_separator)
        fun toggle(id: String) { open = if (open == id) "" else id }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val youSummary = if (a.experience == Experience.SOME && a.pushUpMax == null && a.pullUpMax == null) auto
            else stringResource(a.experience.pbRes()) + if (a.pushUpMax != null || a.pullUpMax != null) " · ${a.pushUpMax ?: "–"} / ${a.pullUpMax ?: "–"}" else ""
            DeepRow(stringResource(R.string.pb_row_you), youSummary, open == "you", { toggle("you") }) { YouRow(a, change) }

            val bodyLabels = a.injuries.sortedBy { it.ordinal }.map { stringResource(it.pbRes()) } + a.focus.sortedBy { it.ordinal }.map { it.localLabel() }
            DeepRow(stringResource(R.string.pb_row_body), bodyLabels.joinToString(sep).ifEmpty { auto }, open == "body", { toggle("body") }) { BodyRow(a, change) }

            val scheduleCustom = a.days != PlanBuilder.spreadDays(a.days.size) || a.minutesByDay.isNotEmpty()
            DeepRow(
                stringResource(R.string.pb_row_schedule),
                if (scheduleCustom) a.days.sorted().joinToString(sep) { Dates.dayShort(it) } else auto,
                open == "schedule", { toggle("schedule") },
            ) { ScheduleRow(a, change) }

            val gearCustom = EquipmentPreset.entries.none { it.equipment == a.equipment }
            DeepRow(stringResource(R.string.pb_row_gear), if (gearCustom) stringResource(R.string.pb_custom) else auto, open == "gear", { toggle("gear") }) { GearRow(a, change) }

            val styleCustom = a.reps != RepPref.AUTO || a.rest != RestPref.AUTO || !a.holdsOk || a.cardio != Cardio.NONE || !a.warmUp || a.coolDown
            DeepRow(stringResource(R.string.pb_row_style), if (styleCustom) stringResource(R.string.pb_custom) else auto, open == "style", { toggle("style") }) { StyleRow(a, change) }

            DeepRow(
                stringResource(R.string.pb_row_avoid),
                if (a.avoidIds.isEmpty()) auto else stringResource(R.string.pb_avoid_count, a.avoidIds.size),
                open == "avoid", { toggle("avoid") },
            ) { AvoidRow(a, change, library) }
        }
    }
}

/** One collapsible row: title and a one-line summary, a chevron, and the few choices when open. */
@Composable
private fun DeepRow(title: String, summary: String, open: Boolean, onToggle: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val c = Fit.colors
    val turn by animateFloatAsState(if (open) 180f else 0f, label = "pbChevron")
    FitCard(padding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(ForgeShape.Sm).clickable(role = Role.Button, onClick = onToggle).padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
                Text(summary, style = MaterialTheme.typography.bodySmall, color = c.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Rounded.ExpandMore, contentDescription = null, tint = c.textSecondary, modifier = Modifier.rotate(turn))
        }
        AnimatedVisibility(open, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(Modifier.padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
        }
    }
}

@Composable
private fun YouRow(a: PlanAnswers, change: ((PlanAnswers) -> PlanAnswers) -> Unit) {
    SegmentedTabs(
        options = Experience.entries.map { stringResource(it.pbRes()) },
        selected = a.experience.ordinal,
        onSelect = { i -> change { it.copy(experience = Experience.entries[i]) } },
        modifier = Modifier.heightIn(min = 48.dp),
        wrap = true,
    )
    StepperLine(stringResource(R.string.pb_pushups), a.pushUpMax) { v -> change { it.copy(pushUpMax = v) } }
    StepperLine(stringResource(R.string.pb_pullups), a.pullUpMax) { v -> change { it.copy(pullUpMax = v) } }
}

/** A count that stays unset (null, shown as a dash) until it is above zero. */
@Composable
private fun StepperLine(label: String, value: Int?, onChange: (Int?) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = Fit.colors.textPrimary, modifier = Modifier.weight(1f))
        NumberStepper(
            value = (value ?: 0).toFloat(),
            onValueChange = { onChange(it.toInt().takeIf { n -> n > 0 }) },
            step = 1f, min = 0f, max = 100f,
            placeholder = if (value == null) "–" else null,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BodyRow(a: PlanAnswers, change: ((PlanAnswers) -> PlanAnswers) -> Unit) {
    Label(stringResource(R.string.pb_injury_hint))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Injury.entries.forEach { injury ->
            val on = injury in a.injuries
            AnswerChip(stringResource(injury.pbRes()), on) { change { it.copy(injuries = if (on) it.injuries - injury else it.injuries + injury) } }
        }
    }
    Label(stringResource(R.string.pb_focus_hint))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FOCUS_MUSCLES.forEach { m ->
            val on = m in a.focus
            AnswerChip(m.localLabel(), on) {
                change { if (on) it.copy(focus = it.focus - m) else if (it.focus.size < 3) it.copy(focus = it.focus + m) else it }
            }
        }
    }
}

private val FOCUS_MUSCLES = listOf(
    Muscle.CHEST, Muscle.SHOULDERS, Muscle.BICEPS, Muscle.TRICEPS, Muscle.ABS,
    Muscle.UPPER_BACK, Muscle.GLUTES, Muscle.QUADS, Muscle.HAMSTRINGS, Muscle.CALVES,
)

@Composable
private fun ScheduleRow(a: PlanAnswers, change: ((PlanAnswers) -> PlanAnswers) -> Unit) {
    val c = Fit.colors
    var editDay by rememberSaveable { mutableIntStateOf(0) }
    Text(stringResource(R.string.pb_schedule_hint), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        (1..7).forEach { dow ->
            val sel = dow in a.days
            Box(
                Modifier.weight(1f).heightIn(min = 48.dp).clip(ForgeShape.Sm).background(if (sel) c.teal else c.cardHigh)
                    .selectable(selected = sel, role = Role.Button) {
                        if (!sel) change { it.copy(days = it.days + dow) } else editDay = if (editDay == dow) 0 else dow
                    }
                    .semantics { contentDescription = Dates.dayName(dow) },
                contentAlignment = Alignment.Center,
            ) {
                Text(Dates.dayShort(dow), style = MaterialTheme.typography.labelMedium, color = if (sel) c.onTeal else c.textSecondary, maxLines = 1, softWrap = false)
            }
        }
    }
    val day = editDay
    if (day != 0 && day in a.days) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.pb_day_minutes, Dates.dayName(day)), style = MaterialTheme.typography.bodyLarge, color = c.textPrimary, modifier = Modifier.weight(1f))
            NumberStepper(
                value = (a.minutesByDay[day] ?: a.minutes).toFloat(),
                onValueChange = { v -> change { it.copy(minutesByDay = it.minutesByDay + (day to v.toInt())) } },
                step = 5f, min = 10f, max = 120f,
            )
        }
        TextButton(
            onClick = { change { it.copy(days = it.days - day, minutesByDay = it.minutesByDay - day) }; editDay = 0 },
            modifier = Modifier.heightIn(min = 48.dp),
        ) { Text(stringResource(R.string.pb_day_remove)) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GearRow(a: PlanAnswers, change: ((PlanAnswers) -> PlanAnswers) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Equipment.entries.filter { it != Equipment.BODYWEIGHT }.forEach { eq ->
            val on = eq in a.equipment
            AnswerChip(eq.localName(), on) { change { it.copy(equipment = if (on) it.equipment - eq else it.equipment + eq + Equipment.BODYWEIGHT) } }
        }
    }
}

@Composable
private fun StyleRow(a: PlanAnswers, change: ((PlanAnswers) -> PlanAnswers) -> Unit) {
    Label(stringResource(R.string.pb_reps))
    SegmentedTabs(
        options = RepPref.entries.map { stringResource(it.pbRes()) },
        selected = a.reps.ordinal,
        onSelect = { i -> change { it.copy(reps = RepPref.entries[i]) } },
        modifier = Modifier.heightIn(min = 48.dp),
        wrap = true,
    )
    Label(stringResource(R.string.pb_rest))
    SegmentedTabs(
        options = RestPref.entries.map { stringResource(it.pbRes()) },
        selected = a.rest.ordinal,
        onSelect = { i -> change { it.copy(rest = RestPref.entries[i]) } },
        modifier = Modifier.heightIn(min = 48.dp),
        wrap = true,
    )
    Label(stringResource(R.string.pb_cardio))
    SegmentedTabs(
        options = Cardio.entries.map { stringResource(it.pbRes()) },
        selected = a.cardio.ordinal,
        onSelect = { i -> change { it.copy(cardio = Cardio.entries[i]) } },
        modifier = Modifier.heightIn(min = 48.dp),
        wrap = true,
    )
    ToggleLine(stringResource(R.string.pb_holds), a.holdsOk) { v -> change { it.copy(holdsOk = v) } }
    ToggleLine(stringResource(R.string.pb_warm_up), a.warmUp) { v -> change { it.copy(warmUp = v) } }
    ToggleLine(stringResource(R.string.pb_cool_down), a.coolDown) { v -> change { it.copy(coolDown = v) } }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AvoidRow(a: PlanAnswers, change: ((PlanAnswers) -> PlanAnswers) -> Unit, library: List<ExerciseEntity>) {
    var picking by remember { mutableStateOf(false) }
    val names = remember(library) { library.associate { it.id to it.name } }
    Label(stringResource(R.string.pb_avoid_hint))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        a.avoidIds.sorted().forEach { id ->
            RemovableChip(names[id] ?: id) { change { it.copy(avoidIds = it.avoidIds - id) } }
        }
    }
    TonalPill(stringResource(R.string.pb_avoid_add), onClick = { picking = true }, modifier = Modifier.fillMaxWidth())
    if (picking) {
        ExercisePickerSheet(
            title = stringResource(R.string.pb_avoid_title),
            library = library,
            onPick = { ex -> picking = false; change { it.copy(avoidIds = it.avoidIds + ex.id) } },
            onCreate = { _, _, _ -> },
            onDismiss = { picking = false },
            userEquipment = a.equipment,
            allowCreate = false,
        )
    }
}

// ---- review ----

@Composable
private fun ReviewDay(day: PlanDraftDay, plan: BuiltPlan, onSwap: (Int) -> Unit) {
    val c = Fit.colors
    val why = plan.why[day.dayOfWeek].orEmpty().map { stringResource(it.pbRes()) }
    FitCard {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(Dates.dayName(day.dayOfWeek), style = MaterialTheme.typography.labelLarge, color = c.textSecondary)
                Text(
                    plan.days[day.dayOfWeek]?.let { stringResource(it.pbRes()) } ?: day.title,
                    style = MaterialTheme.typography.titleMedium, color = c.textPrimary, modifier = Modifier.semantics { heading() },
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.pb_minutes_total, PlanBuilder.estimateMinutes(day)), style = MaterialTheme.typography.labelLarge, color = c.textSecondary)
        }
        if (why.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(why.joinToString(" "), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
        }
        Spacer(Modifier.height(8.dp))
        day.items.forEachIndexed { index, item ->
            val name = item.name ?: item.exerciseId
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("$name · ${scheme(item)}", style = MaterialTheme.typography.bodyLarge, color = c.textPrimary, modifier = Modifier.weight(1f))
                IconButton(onClick = { onSwap(index) }) {
                    Icon(Icons.Rounded.SwapHoriz, contentDescription = stringResource(R.string.pb_swap, name), tint = c.textSecondary)
                }
            }
        }
    }
}

/** "3×8-12" for reps, "3×30s" for holds. */
@Composable
private fun scheme(item: PlanDraftItem): String {
    val sets = item.sets
    val secs = item.seconds
    if (secs != null) return "$sets×$secs${stringResource(R.string.unit_s)}"
    val lo = item.repsMin
    val hi = item.repsMax
    return when {
        lo != null && hi != null && hi != lo -> "$sets×$lo-$hi"
        lo != null || hi != null -> "$sets×${lo ?: hi}"
        else -> "$sets×"
    }
}

// ---- small parts ----

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = Fit.colors.textSecondary)
}

@Composable
private fun ToggleLine(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = Fit.colors.textPrimary, modifier = Modifier.weight(1f))
        FitSwitch(checked, null)
    }
}

/** A choice pill that is at least 48 dp tall and wraps to two lines at large text, unlike the shared ChoiceChip. */
@Composable
private fun AnswerChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val c = Fit.colors
    Surface(
        onClick = onClick,
        shape = ForgeShape.Full,
        color = if (selected) c.teal else c.cardHigh,
        contentColor = if (selected) c.onTeal else c.textPrimary,
        modifier = Modifier.heightIn(min = 48.dp).semantics { this.selected = selected },
    ) {
        Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
            Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 2, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun RemovableChip(text: String, onRemove: () -> Unit) {
    val c = Fit.colors
    val remove = stringResource(R.string.cd_remove)
    Row(
        Modifier.heightIn(min = 48.dp).clip(ForgeShape.Full).background(c.cardHigh)
            .clickable(onClickLabel = remove, role = Role.Button, onClick = onRemove).padding(start = 16.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = c.textPrimary, maxLines = 2)
        Spacer(Modifier.width(8.dp))
        Icon(Icons.Rounded.Close, contentDescription = remove, tint = c.textSecondary, modifier = Modifier.size(18.dp))
    }
}
