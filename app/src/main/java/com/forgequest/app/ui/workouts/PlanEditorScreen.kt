package com.forgequest.app.ui.workouts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.forgequest.app.AppContainer
import com.forgequest.app.R
import com.forgequest.app.container
import com.forgequest.app.data.ProgressSnapshot
import com.forgequest.app.data.ai.AiError
import com.forgequest.app.data.db.ExerciseEntity
import com.forgequest.app.data.db.PlanDayEntity
import com.forgequest.app.data.db.PlanEntity
import com.forgequest.app.data.db.PlanItemEntity
import com.forgequest.app.data.plan.PlanDraft
import com.forgequest.app.data.plan.PlanJson
import com.forgequest.app.data.seed.StarterPlans
import com.forgequest.app.domain.Dates
import com.forgequest.app.domain.Equipment
import com.forgequest.app.domain.ExerciseKind
import com.forgequest.app.domain.ExerciseSwapEngine
import com.forgequest.app.domain.Muscle
import com.forgequest.app.domain.WeightUnit
import com.forgequest.app.ui.LocalSnackbar
import com.forgequest.app.ui.appViewModel
import com.forgequest.app.ui.goToTab
import com.forgequest.app.ui.blockLabel
import com.forgequest.app.ui.components.FitCard
import com.forgequest.app.ui.components.UndoSnackbarEffect
import com.forgequest.app.ui.components.UndoState
import com.forgequest.app.ui.components.IconCircle
import com.forgequest.app.ui.components.NumberStepper
import com.forgequest.app.ui.components.PillButton
import com.forgequest.app.ui.components.TonalPill
import com.forgequest.app.ui.theme.Fit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale

class PlanEditorViewModel(val app: AppContainer) : ViewModel() {
    val snapshot: StateFlow<ProgressSnapshot?> = app.progress.snapshot.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val library: StateFlow<List<ExerciseEntity>> = app.workouts.library.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** A plan waiting for the user to confirm, from a template, pasted text or Google Docs. */
    val pending = MutableStateFlow<PlanDraft?>(null)
    val busy = MutableStateFlow<String?>(null)
    val message = MutableStateFlow<String?>(null)

    fun preview(draft: PlanDraft) { pending.value = draft }

    fun adaptPending(exercises: Map<String, ExerciseEntity>, userEquipment: Set<Equipment>) {
        val draft = pending.value ?: return
        val (adapted, count) = ExerciseSwapEngine.adaptPlanToEquipment(draft, exercises, userEquipment)
        pending.value = adapted
        if (count > 0) {
            message.value = app.plural(R.plurals.plan_adapted_swaps, count, count)
        }
    }

    fun applyPending(source: String) {
        val draft = pending.value ?: return
        pending.value = null
        viewModelScope.launch {
            app.workouts.applyPlan(draft, source)
            message.value = app.str(R.string.plan_switched_to, draft.name)
        }
    }

    fun parsePasted(text: String) {
        PlanJson.parsePasted(text)?.let { pending.value = it; return }
        busy.value = app.str(R.string.plan_reading)
        viewModelScope.launch {
            try {
                pending.value = app.coach.parsePlanText(text)
            } catch (e: AiError) {
                message.value = e.userMessage
            } finally {
                busy.value = null
            }
        }
    }

    fun updateItem(item: PlanItemEntity) = viewModelScope.launch { app.workouts.updatePlanItem(item) }
    fun swapItem(item: PlanItemEntity, newExerciseId: String) = viewModelScope.launch {
        app.workouts.swapPlanItem(item, newExerciseId)
    }

    val undo = UndoState(viewModelScope)

    fun deleteItem(item: PlanItemEntity, name: String) = viewModelScope.launch {
        app.workouts.deletePlanItem(item.id)
        undo.offer(app.str(R.string.food_removed, name)) { app.workouts.restorePlanItem(item) }
    }

    fun addItem(dayId: Long, exerciseId: String) = viewModelScope.launch { app.workouts.addPlanItem(dayId, exerciseId) }
    fun setRest(day: PlanDayEntity, rest: Boolean) = viewModelScope.launch {
        app.workouts.updatePlanDay(day.copy(isRest = rest, title = if (rest) app.str(R.string.today_rest_day) else if (day.title == app.str(R.string.today_rest_day) || day.title == "Rest day") app.str(R.string.plan_workout) else day.title))
    }
    val allPlans: StateFlow<List<PlanEntity>> = app.workouts.allPlans.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun activate(plan: PlanEntity) = viewModelScope.launch { app.workouts.activatePlan(plan.id); message.value = app.str(R.string.plan_switched, plan.name) }
    fun deletePlan(plan: PlanEntity) = viewModelScope.launch { app.workouts.deletePlan(plan.id); message.value = app.str(R.string.lib_deleted, plan.name) }
    fun move(item: PlanItemEntity, delta: Int) = viewModelScope.launch { app.workouts.movePlanItem(item, delta) }
    fun swapDays(planId: Long, a: Int, b: Int) = viewModelScope.launch {
        app.workouts.swapPlanDays(planId, a, b)
        message.value = app.str(R.string.plan_days_swapped, Dates.dayName(a), Dates.dayName(b))
    }
    fun rename(day: PlanDayEntity, title: String) = viewModelScope.launch { app.workouts.updatePlanDay(day.copy(title = title)) }
    fun createExercise(name: String, kind: ExerciseKind, muscle: Muscle, dayId: Long) = viewModelScope.launch {
        val id = app.workouts.addCustomExercise(name, kind, listOf(muscle), null)
        app.workouts.addPlanItem(dayId, id)
    }
    fun createAndSwapExercise(name: String, kind: ExerciseKind, muscle: Muscle, item: PlanItemEntity) = viewModelScope.launch {
        val id = app.workouts.addCustomExercise(name, kind, listOf(muscle), null)
        app.workouts.swapPlanItem(item, id)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanEditorScreen(nav: NavHostController, initialDay: Int = 0) {
    val vm = appViewModel { PlanEditorViewModel(it) }
    val snap by vm.snapshot.collectAsState()
    val library by vm.library.collectAsState()
    val pending by vm.pending.collectAsState()
    val busy by vm.busy.collectAsState()
    val message by vm.message.collectAsState()
    val snackbar = LocalSnackbar.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val c = Fit.colors
    var selectedDay by rememberSaveable { mutableIntStateOf(initialDay.takeIf { it in 1..7 } ?: Dates.todayDayOfWeek()) }
    var picker by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<PlanItemEntity?>(null) }
    var swappingItem by remember { mutableStateOf<PlanItemEntity?>(null) }
    var templates by remember { mutableStateOf(false) }
    var changePlan by remember { mutableStateOf(false) }
    var paste by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var previous by remember { mutableStateOf(false) }
    var moveDay by remember { mutableStateOf(false) }
    val allPlans by vm.allPlans.collectAsState()

    androidx.compose.runtime.LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); vm.message.value = null }
    }
    UndoSnackbarEffect(vm.undo)

    val s = snap ?: return Box(Modifier.fillMaxSize().background(c.background))
    val day = s.plan?.day(selectedDay)
    val userEquipment = s.settings.configuredEquipment

    Column(Modifier.fillMaxSize().background(c.background).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.cd_back), tint = c.textPrimary) }
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.plan_your_plan), style = MaterialTheme.typography.titleLarge, color = c.textPrimary)
                Text(s.plan?.plan?.name ?: stringResource(R.string.plan_no_plan), style = MaterialTheme.typography.bodySmall, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            // Changing plans is occasional, so it sits behind one button and the week plan gets the screen.
            TonalPill(stringResource(R.string.plan_change), onClick = { changePlan = true }, icon = Icons.Rounded.SwapHoriz, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                (1..7).forEach { dow ->
                    val sel = dow == selectedDay
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(if (sel) c.teal else c.card)
                            .selectable(selected = sel, role = Role.Tab) { selectedDay = dow }
                            .semantics { contentDescription = Dates.dayName(dow) }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(Dates.dayShort(dow), style = MaterialTheme.typography.labelMedium, color = if (sel) c.onTeal else c.textSecondary, maxLines = 1, softWrap = false)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            if (day != null) {
                FitCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Row(
                                Modifier.clip(RoundedCornerShape(8.dp)).clickable { moveDay = true }.padding(end = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(Dates.dayName(selectedDay), style = MaterialTheme.typography.labelLarge, color = c.textSecondary)
                                Icon(Icons.Rounded.SwapHoriz, contentDescription = stringResource(R.string.cd_move_day), tint = c.textSecondary, modifier = Modifier.padding(start = 4.dp).size(16.dp))
                            }
                            Text(
                                day.day.title, style = MaterialTheme.typography.titleMedium, color = c.textPrimary,
                                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(enabled = !day.day.isRest) { renaming = true },
                            )
                        }
                        Text(stringResource(R.string.today_rest_day), style = MaterialTheme.typography.labelLarge, color = c.textSecondary)
                        Spacer(Modifier.width(8.dp))
                        Switch(
                            checked = day.day.isRest,
                            onCheckedChange = { vm.setRest(day.day, it) },
                            colors = com.forgequest.app.ui.components.fitSwitchColors(c.blue),
                        )
                    }
                    if (!day.day.isRest) {
                        Spacer(Modifier.height(8.dp))
                        // Drag a row by its handle (long press) to reorder; the Move up / Move down buttons in the editor stay.
                        val haptics = LocalContext.current.container.haptics
                        var order by remember(day.items) { mutableStateOf(day.items.map { it.id }) }
                        var dragId by remember { mutableStateOf<Long?>(null) }
                        var dragOffset by remember { mutableFloatStateOf(0f) }
                        var dragStart by remember { mutableIntStateOf(0) }
                        val heights = remember { mutableMapOf<Long, Int>() }
                        val dragLabel = stringResource(R.string.cd_drag_reorder)
                        order.mapNotNull { id -> day.items.firstOrNull { it.id == id } }.forEach { item ->
                          key(item.id) {
                            val ex = s.exercises[item.exerciseId]
                            val dragging = dragId == item.id
                            Row(
                                Modifier.fillMaxWidth()
                                    .zIndex(if (dragging) 1f else 0f)
                                    .graphicsLayer { translationY = if (dragging) dragOffset else 0f; alpha = if (dragging) 0.92f else 1f }
                                    .onSizeChanged { heights[item.id] = it.height }
                                    .clip(RoundedCornerShape(12.dp)).background(if (dragging) c.cardHigh else androidx.compose.ui.graphics.Color.Transparent)
                                    .clickable { editing = item }.padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Rounded.DragHandle, contentDescription = dragLabel, tint = c.textSecondary,
                                    modifier = Modifier.size(40.dp).pointerInput(item.id) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = { dragId = item.id; dragOffset = 0f; dragStart = order.indexOf(item.id); haptics.tick() },
                                            onDrag = { change, amount ->
                                                change.consume()
                                                dragOffset += amount.y
                                                val i = order.indexOf(item.id)
                                                val below = order.getOrNull(i + 1)?.let { heights[it] }
                                                val above = order.getOrNull(i - 1)?.let { heights[it] }
                                                if (dragOffset > 0f && below != null && dragOffset > below / 2f) {
                                                    order = order.toMutableList().also { it.add(i + 1, it.removeAt(i)) }
                                                    dragOffset -= below
                                                    haptics.tick()
                                                } else if (dragOffset < 0f && above != null && -dragOffset > above / 2f) {
                                                    order = order.toMutableList().also { it.add(i - 1, it.removeAt(i)) }
                                                    dragOffset += above
                                                    haptics.tick()
                                                }
                                            },
                                            onDragEnd = {
                                                val delta = order.indexOf(item.id) - dragStart
                                                dragId = null; dragOffset = 0f
                                                if (delta != 0) vm.move(item, delta)
                                            },
                                            onDragCancel = { dragId = null; dragOffset = 0f; order = day.items.map { it.id } },
                                        )
                                    }.padding(end = 12.dp),
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(ex?.name ?: item.exerciseId, style = MaterialTheme.typography.bodyLarge, color = c.textPrimary)
                                    Text(
                                        listOfNotNull(item.block.ifBlank { null }?.let { blockLabel(it) }, if (item.circuit) pluralStringResource(R.plurals.plan_circuit_rounds, item.sets, item.sets) else null, item.prescription(ex?.kind, s.settings.weightUnit), stringResource(R.string.plan_rest_seconds, item.restSeconds)).joinToString(" · "),
                                        style = MaterialTheme.typography.bodySmall, color = c.textSecondary,
                                    )
                                }
                                IconButton(onClick = { swappingItem = item }) {
                                    Icon(Icons.Rounded.SwapHoriz, contentDescription = stringResource(R.string.live_swap_exercise), tint = c.teal)
                                }
                                IconButton(onClick = { vm.deleteItem(item, ex?.name ?: item.exerciseId) }) {
                                    Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.cd_remove), tint = c.textSecondary)
                                }
                            }
                          }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    TonalPill(stringResource(R.string.live_add_exercise), onClick = { picker = true }, icon = Icons.Rounded.Add, modifier = Modifier.fillMaxWidth())
                }
            }
            Spacer(Modifier.height(24.dp))
            Spacer(Modifier.navigationBarsPadding())
        }
    }

    if (picker && day != null) {
        ExercisePickerSheet(
            title = stringResource(R.string.plan_add_to_day, Dates.dayName(selectedDay)),
            library = library,
            onPick = { vm.addItem(day.day.id, it.id); picker = false },
            onCreate = { n, k, m -> vm.createExercise(n, k, m, day.day.id); picker = false },
            onDismiss = { picker = false },
            userEquipment = userEquipment,
        )
    }

    swappingItem?.let { itemToSwap ->
        val srcEx = s.exercises[itemToSwap.exerciseId]
        ExercisePickerSheet(
            title = stringResource(R.string.live_swap_exercise),
            library = library,
            onPick = { picked ->
                vm.swapItem(itemToSwap, picked.id)
                swappingItem = null
            },
            onCreate = { n, k, m ->
                vm.createAndSwapExercise(n, k, m, itemToSwap)
                swappingItem = null
            },
            onDismiss = { swappingItem = null },
            initialMuscle = srcEx?.primary?.firstOrNull(),
            sourceExercise = srcEx,
            userEquipment = userEquipment,
        )
    }

    editing?.let { item ->
        val kind = s.exercises[item.exerciseId]?.kind ?: ExerciseKind.REPS
        val siblings = day?.items?.sortedBy { it.position }.orEmpty()
        val index = siblings.indexOfFirst { it.id == item.id }
        ItemEditorSheet(
            item = item,
            kind = kind,
            name = s.exercises[item.exerciseId]?.name ?: item.exerciseId,
            unit = s.settings.weightUnit,
            canMoveUp = index > 0,
            canMoveDown = index >= 0 && index < siblings.lastIndex,
            onMove = { delta -> vm.move(item, delta); editing = null },
            onSwap = { editing = null; swappingItem = item },
            onSave = { vm.updateItem(it); editing = null },
            onDismiss = { editing = null },
        )
    }

    if (changePlan) {
        // Most used first; each choice has an ⓘ that explains what it does.
        val context = LocalContext.current
        val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
        val copied = stringResource(R.string.plan_prompt_copied)
        ModalBottomSheet(onDismissRequest = { changePlan = false }, containerColor = c.card) {
            Column(Modifier.padding(bottom = 32.dp)) {
                Text(stringResource(R.string.plan_change), style = MaterialTheme.typography.titleLarge, color = c.textPrimary, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                ChoiceRow(Icons.Rounded.AutoAwesome, stringResource(R.string.plan_gemini), stringResource(R.string.plan_gemini_info)) {
                    Row(Modifier.padding(start = 70.dp, end = 16.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TonalPill(stringResource(R.string.plan_gemini_copy), onClick = {
                            // The app copies res/raw/plan_prompt.txt, mirrored in docs/prompts/plan-from-youtube.md.
                            val prompt = context.resources.openRawResource(R.raw.plan_prompt).bufferedReader().use { it.readText() }
                            clipboard.setText(androidx.compose.ui.text.AnnotatedString(prompt))
                            if (android.os.Build.VERSION.SDK_INT < 33) scope.launch { snackbar.showSnackbar(copied) }
                        }, icon = Icons.Rounded.ContentCopy)
                        TonalPill(stringResource(R.string.plan_gemini_paste), onClick = { changePlan = false; paste = true }, icon = Icons.Rounded.ContentPaste)
                    }
                }
                ChoiceRow(Icons.Rounded.ViewList, stringResource(R.string.plan_ready_made), stringResource(R.string.plan_ready_made_info), onClick = { changePlan = false; templates = true })
                if (allPlans.size > 1) {
                    ChoiceRow(Icons.Rounded.History, stringResource(R.string.plan_previous), stringResource(R.string.plan_previous_info), onClick = { changePlan = false; previous = true })
                }
                ChoiceRow(Icons.Rounded.Description, stringResource(R.string.plan_import_docs), stringResource(R.string.plan_docs_info), onClick = { changePlan = false; nav.goToTab(com.forgequest.app.ui.Routes.PROFILE) })
            }
        }
    }

    if (templates) {
        ModalBottomSheet(onDismissRequest = { templates = false }, containerColor = c.card) {
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
                Text(stringResource(R.string.plan_ready_made_title), style = MaterialTheme.typography.titleLarge, color = c.textPrimary)
                Spacer(Modifier.height(12.dp))
                StarterPlans.all.forEach { plan ->
                    Column(Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(RoundedCornerShape(18.dp)).background(c.cardHigh).clickable { templates = false; vm.preview(plan) }.padding(16.dp)) {
                        Text(plan.name, style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
                        Spacer(Modifier.height(4.dp))
                        Text(plan.description, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                    }
                }
            }
        }
    }

    if (previous) {
        var toDelete by remember { mutableStateOf<PlanEntity?>(null) }
        ModalBottomSheet(onDismissRequest = { previous = false }, containerColor = c.card) {
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
                Text(stringResource(R.string.plan_previous), style = MaterialTheme.typography.titleLarge, color = c.textPrimary)
                Text(stringResource(R.string.plan_previous_tap), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                Spacer(Modifier.height(12.dp))
                allPlans.forEach { plan ->
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(RoundedCornerShape(18.dp)).background(if (plan.active) c.tealTrack else c.cardHigh)
                            .clickable(enabled = !plan.active) { previous = false; vm.activate(plan) }.padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(plan.name, style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
                            Text(
                                listOf(
                                    when (plan.source) { "coach" -> stringResource(R.string.plan_src_coach); "docs" -> stringResource(R.string.plan_src_docs); "starter" -> stringResource(R.string.plan_src_starter); else -> stringResource(R.string.plan_src_you) },
                                    java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(java.util.Date(plan.createdAt)),
                                    if (plan.active) stringResource(R.string.plan_current) else null,
                                ).filterNotNull().joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall, color = c.textSecondary,
                            )
                        }
                        if (!plan.active) {
                            IconButton(onClick = { toDelete = plan }) { Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.cd_delete_plan), tint = c.textSecondary) }
                        }
                    }
                }
            }
        }
        toDelete?.let { plan ->
            AlertDialog(
                onDismissRequest = { toDelete = null },
                containerColor = c.card,
                title = { Text(stringResource(R.string.food_delete_title, plan.name), color = c.textPrimary) },
                text = { Text(stringResource(R.string.plan_delete_body), color = c.textSecondary) },
                confirmButton = { TextButton(onClick = { vm.deletePlan(plan); toDelete = null }) { Text(stringResource(R.string.action_delete), color = c.coral) } },
                dismissButton = { TextButton(onClick = { toDelete = null }) { Text(stringResource(R.string.action_cancel), color = c.textSecondary) } },
            )
        }
    }

    if (moveDay && day != null && s.plan != null) {
        AlertDialog(
            onDismissRequest = { moveDay = false },
            containerColor = c.card,
            title = { Text(stringResource(R.string.plan_move_title, Dates.dayName(selectedDay)), color = c.textPrimary) },
            text = {
                Column {
                    Text(stringResource(R.string.plan_move_body), color = c.textSecondary, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                    (1..7).filter { it != selectedDay }.forEach { dow ->
                        val target = s.plan.day(dow)
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { vm.swapDays(s.plan.plan.id, selectedDay, dow); moveDay = false; selectedDay = dow }.padding(vertical = 10.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(Dates.dayName(dow), style = MaterialTheme.typography.bodyLarge, color = c.textPrimary, modifier = Modifier.weight(1f))
                            Text(if (target == null || target.day.isRest) stringResource(R.string.live_rest) else target.day.title, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { moveDay = false }) { Text(stringResource(R.string.action_cancel), color = c.textSecondary) } },
        )
    }

    if (paste) {
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { paste = false },
            containerColor = c.card,
            title = { Text(stringResource(R.string.plan_paste), color = c.textPrimary) },
            text = {
                OutlinedTextField(
                    value = text, onValueChange = { text = it },
                    placeholder = { Text(stringResource(R.string.plan_paste_hint_text)) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = c.teal, unfocusedBorderColor = c.divider, focusedTextColor = c.textPrimary, unfocusedTextColor = c.textPrimary),
                )
            },
            confirmButton = { TextButton(onClick = { paste = false; vm.parsePasted(text) }, enabled = text.isNotBlank()) { Text(stringResource(R.string.plan_read), color = c.teal) } },
            dismissButton = { TextButton(onClick = { paste = false }) { Text(stringResource(R.string.action_cancel), color = c.textSecondary) } },
        )
    }

    busy?.let {
        AlertDialog(onDismissRequest = {}, containerColor = c.card, confirmButton = {}, title = { Text(it, color = c.textPrimary) },
            text = { androidx.compose.material3.LinearProgressIndicator(color = c.teal, trackColor = c.tealTrack, modifier = Modifier.fillMaxWidth()) })
    }

    pending?.let { draft ->
        PlanPreviewSheet(
            draft = draft,
            exercises = s.exercises,
            userEquipment = userEquipment,
            onAdapt = { vm.adaptPending(s.exercises, userEquipment) },
            onApply = { vm.applyPending("manual") },
            onDismiss = { vm.pending.value = null },
        )
    }

    if (renaming && day != null) {
        var title by remember { mutableStateOf(day.day.title) }
        val workoutLabel = stringResource(R.string.plan_workout)
        AlertDialog(
            onDismissRequest = { renaming = false },
            containerColor = c.card,
            title = { Text(stringResource(R.string.plan_day_name), color = c.textPrimary) },
            text = {
                OutlinedTextField(value = title, onValueChange = { title = it.take(40) }, singleLine = true, shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = c.teal, unfocusedBorderColor = c.divider, focusedTextColor = c.textPrimary, unfocusedTextColor = c.textPrimary))
            },
            confirmButton = { TextButton(onClick = { vm.rename(day.day, title.trim().ifEmpty { workoutLabel }); renaming = false }) { Text(stringResource(R.string.action_save), color = c.teal) } },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text(stringResource(R.string.action_cancel), color = c.textSecondary) } },
        )
    }
}

/** A choice in the Change plan sheet; the ⓘ opens a short explanation under it. */
@Composable
private fun ChoiceRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    info: String,
    onClick: (() -> Unit)? = null,
    extra: (@Composable () -> Unit)? = null,
) {
    val c = Fit.colors
    var showInfo by remember { mutableStateOf(false) }
    Column {
        Row(
            Modifier.fillMaxWidth().let { if (onClick != null) it.clickable(onClick = onClick) else it }.padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconCircle(icon, c.teal, c.tealTrack, 40.dp)
            Spacer(Modifier.width(14.dp))
            Text(title, style = MaterialTheme.typography.titleSmall, color = c.textPrimary, modifier = Modifier.weight(1f))
            IconButton(onClick = { showInfo = !showInfo }) {
                Icon(Icons.Rounded.Info, contentDescription = stringResource(R.string.cd_what_is_this, title), tint = if (showInfo) c.teal else c.textSecondary)
            }
        }
        if (showInfo) Text(info, style = MaterialTheme.typography.bodySmall, color = c.textSecondary, modifier = Modifier.padding(start = 70.dp, end = 20.dp, bottom = 8.dp))
        extra?.invoke()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ItemEditorSheet(
    item: PlanItemEntity,
    kind: ExerciseKind,
    name: String,
    unit: WeightUnit,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMove: (Int) -> Unit,
    onSwap: () -> Unit,
    onSave: (PlanItemEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = Fit.colors
    var draft by remember { mutableStateOf(item) }
    ModalBottomSheet(sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), onDismissRequest = onDismiss, containerColor = c.card) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, style = MaterialTheme.typography.titleLarge, color = c.textPrimary, modifier = Modifier.weight(1f))
                TonalPill(stringResource(R.string.live_swap_exercise), onClick = onSwap, icon = Icons.Rounded.SwapHoriz)
            }
            Spacer(Modifier.height(16.dp))
            EditorRow(if (draft.circuit) stringResource(R.string.plan_rounds) else stringResource(R.string.live_sets), draft.sets.toFloat(), { draft = draft.copy(sets = it.toInt()) }, step = 1f, min = 1f, max = 10f)
            if (kind == ExerciseKind.HOLD) {
                EditorRow(stringResource(R.string.kind_hold), (draft.seconds ?: 30).toFloat(), { draft = draft.copy(seconds = it.toInt()) }, step = 5f, min = 5f, max = 3600f, unit = stringResource(R.string.unit_s))
            } else {
                val minReps = draft.repsMin ?: 10
                val maxReps = draft.repsMax ?: minReps
                EditorRow(stringResource(R.string.plan_reps_min), minReps.toFloat(), { v -> draft = draft.copy(repsMin = v.toInt(), repsMax = draft.repsMax?.coerceAtLeast(v.toInt())) }, step = 1f, min = 1f, max = 999f)
                EditorRow(stringResource(R.string.plan_reps_max), maxReps.toFloat(), { v -> draft = draft.copy(repsMax = v.toInt().coerceAtLeast(minReps)) }, step = 1f, min = minReps.toFloat(), max = 999f)
            }
            // Weighted moves always carry kg; bodyweight moves (planks, push-ups) can add it here, e.g. a plate or a vest.
            if (kind != ExerciseKind.WEIGHTED) {
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.plan_add_weight), style = MaterialTheme.typography.bodyLarge, color = c.textPrimary)
                        Text(stringResource(R.string.plan_add_weight_hint), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                    }
                    Switch(checked = draft.weightKg != null, onCheckedChange = { draft = draft.copy(weightKg = if (it) 0f else null) }, colors = com.forgequest.app.ui.components.fitSwitchColors(c.teal))
                }
            }
            if (kind == ExerciseKind.WEIGHTED || draft.weightKg != null) {
                EditorRow(stringResource(R.string.stepper_weight), unit.fromKg(draft.weightKg ?: 0f), { draft = draft.copy(weightKg = unit.toKg(it)) }, step = unit.loadStep, min = 0f, max = unit.fromKg(999f), unit = unit.label, decimals = 1)
            }
            EditorRow(stringResource(R.string.live_rest), draft.restSeconds.toFloat(), { draft = draft.copy(restSeconds = it.toInt()) }, step = 15f, min = 0f, max = 900f, unit = stringResource(R.string.unit_s))
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.plan_part_circuit), style = MaterialTheme.typography.bodyLarge, color = c.textPrimary)
                    Text(stringResource(R.string.plan_circuit_hint, draft.block), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                }
                Switch(checked = draft.circuit, onCheckedChange = { draft = draft.copy(circuit = it) }, colors = com.forgequest.app.ui.components.fitSwitchColors(c.teal))
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.plan_order), style = MaterialTheme.typography.bodyLarge, color = c.textPrimary, modifier = Modifier.weight(1f))
                TonalPill(stringResource(R.string.plan_move_up), onClick = { onMove(-1) }, icon = Icons.Rounded.ArrowUpward, tint = if (canMoveUp) c.textPrimary else c.textSecondary)
                TonalPill(stringResource(R.string.plan_move_down), onClick = { onMove(1) }, icon = Icons.Rounded.ArrowDownward, tint = if (canMoveDown) c.textPrimary else c.textSecondary)
            }
            Spacer(Modifier.height(16.dp))
            PillButton(stringResource(R.string.action_save), onClick = { onSave(draft) }, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun EditorRow(label: String, value: Float, onChange: (Float) -> Unit, step: Float, min: Float, max: Float, unit: String = "", decimals: Int = 0) {
    val c = Fit.colors
    val haptics = LocalContext.current.container.haptics
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = c.textPrimary, modifier = Modifier.weight(1f))
        NumberStepper(value, onChange, step, min, max, unit = unit, decimals = decimals, title = label, minValueWidth = 72.dp, onTick = haptics::tick)
    }
}

/** Shows a whole proposed week before it replaces the current plan. Used by the plan editor and coach. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanPreviewSheet(
    draft: PlanDraft,
    exercises: Map<String, ExerciseEntity>,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
    userEquipment: Set<Equipment> = emptySet(),
    onAdapt: (() -> Unit)? = null,
) {
    val c = Fit.colors
    val incompatibleCount = remember(draft, exercises, userEquipment) {
        if (userEquipment.isEmpty() || userEquipment.containsAll(Equipment.entries)) 0
        else ExerciseSwapEngine.adaptPlanToEquipment(draft, exercises, userEquipment).second
    }

    ModalBottomSheet(sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), onDismissRequest = onDismiss, containerColor = c.card) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp).verticalScroll(rememberScrollState())) {
            Text(draft.name, style = MaterialTheme.typography.titleLarge, color = c.textPrimary)
            if (draft.description.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(draft.description, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
            }
            Spacer(Modifier.height(12.dp))
            draft.days.sortedBy { it.dayOfWeek }.forEach { d ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Text(Dates.dayShort(d.dayOfWeek), style = MaterialTheme.typography.labelLarge, color = c.teal, modifier = Modifier.widthIn(min = 44.dp).padding(end = 8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (d.isRest || d.items.isEmpty()) stringResource(R.string.live_rest) else d.title, style = MaterialTheme.typography.bodyMedium, color = c.textPrimary)
                        if (!d.isRest && d.items.isNotEmpty()) {
                            Text(
                                d.items.joinToString(", ") { exercises[it.exerciseId]?.name ?: it.name ?: it.exerciseId },
                                style = MaterialTheme.typography.bodySmall, color = c.textSecondary,
                            )
                        }
                    }
                }
            }
            if (incompatibleCount > 0 && onAdapt != null) {
                Spacer(Modifier.height(12.dp))
                TonalPill(
                    text = pluralStringResource(R.plurals.plan_adapt_button, incompatibleCount, incompatibleCount),
                    onClick = onAdapt,
                    icon = Icons.Rounded.SwapHoriz,
                    tint = c.teal,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.plan_replaces), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
            Spacer(Modifier.height(12.dp))
            PillButton(stringResource(R.string.plan_use_this), onClick = onApply, modifier = Modifier.fillMaxWidth())
        }
    }
}