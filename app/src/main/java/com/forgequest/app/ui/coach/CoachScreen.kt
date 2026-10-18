package com.forgequest.app.ui.coach

import com.forgequest.app.R

import androidx.compose.ui.res.pluralStringResource

import androidx.compose.ui.res.stringResource

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.forgequest.app.AppContainer
import com.forgequest.app.data.db.ChatEntity
import com.forgequest.app.data.db.ExerciseEntity
import com.forgequest.app.data.plan.PlanDraft
import com.forgequest.app.data.plan.PlanJson
import com.forgequest.app.ui.LocalSnackbar
import com.forgequest.app.ui.Routes
import com.forgequest.app.ui.appViewModel
import com.forgequest.app.ui.components.IconCircle
import com.forgequest.app.ui.components.MarkdownText
import com.forgequest.app.ui.components.PillButton
import com.forgequest.app.ui.components.TonalPill
import com.forgequest.app.ui.goToTab
import com.forgequest.app.ui.theme.Fit
import com.forgequest.app.ui.workouts.PlanPreviewSheet
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CoachViewModel(val app: AppContainer) : ViewModel() {
    val messages: StateFlow<List<ChatEntity>> = app.coach.messages.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val exercises: StateFlow<Map<String, ExerciseEntity>> = app.workouts.library.map { l -> l.associateBy { it.id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())
    val hasKey: StateFlow<Boolean> = app.settings.apiKey.map { it.isNotBlank() }.stateIn(viewModelScope, SharingStarted.Eagerly, app.settings.apiKey.value.isNotBlank())
    val sending = MutableStateFlow(false)
    /** The weekly check-in being written in the background. It never blocks sending a message. */
    val checkingIn = MutableStateFlow(false)
    val applied = MutableStateFlow<String?>(null)
    /** Text of the reply still arriving, shown live. */
    val draft: StateFlow<String?> = app.coach.draft

    init {
        viewModelScope.launch { app.coach.warmUp() }
        viewModelScope.launch {
            checkingIn.value = true
            runCatching { app.coach.weeklyCheckinIfDue() }
            checkingIn.value = false
        }
    }

    fun send(text: String) {
        if (text.isBlank() || sending.value) return
        sending.value = true
        viewModelScope.launch {
            app.coach.send(text)
            sending.value = false
        }
    }

    fun apply(message: ChatEntity) = viewModelScope.launch {
        if (app.coach.applyPlan(message.id)) {
            app.haptics.celebrate()
            applied.value = app.str(R.string.coach_plan_applied)
        }
    }

    fun clear() = viewModelScope.launch { app.coach.clear() }

    val settings = app.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    fun setModel(choice: String) = viewModelScope.launch { app.coach.setCoachModel(choice) }

    fun planOf(message: ChatEntity): PlanDraft? = message.planJson?.let(PlanJson::decode)
}

private val suggestions = listOf(
    R.string.coach_suggest_week,
    R.string.coach_suggest_pull_up,
    R.string.coach_suggest_plan,
    R.string.coach_suggest_breakfast,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CoachScreen(nav: NavHostController) {
    val vm = appViewModel { CoachViewModel(it) }
    val messages by vm.messages.collectAsState()
    val exercises by vm.exercises.collectAsState()
    val sending by vm.sending.collectAsState()
    val checkingIn by vm.checkingIn.collectAsState()
    val hasKey by vm.hasKey.collectAsState()
    val applied by vm.applied.collectAsState()
    val draft by vm.draft.collectAsState()
    val snackbar = LocalSnackbar.current
    val c = Fit.colors
    var input by remember { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<ChatEntity?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size, sending, draft?.length) {
        if (messages.isNotEmpty()) listState.scrollToItem(listState.layoutInfo.totalItemsCount.coerceAtLeast(1) - 1)
    }
    LaunchedEffect(applied) { applied?.let { snackbar.showSnackbar(it); vm.applied.value = null } }

    Column(Modifier.fillMaxSize().background(c.background).statusBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.tab_coach), style = MaterialTheme.typography.headlineMedium, color = c.textPrimary, modifier = Modifier.weight(1f))
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.cd_more), tint = c.textSecondary) }
                DropdownMenu(menu, { menu = false }, containerColor = c.cardHigh) {
                    val s by vm.settings.collectAsState()
                    val current = s?.coachModel ?: "auto"
                    Text(stringResource(R.string.coach_model_header), style = MaterialTheme.typography.labelMedium, color = c.textSecondary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                    listOf(
                        Triple("auto", R.string.coach_model_auto, stringResource(R.string.coach_model_auto_hint)),
                        Triple("lite", R.string.coach_model_lite, (s?.liteModel ?: "").ifBlank { stringResource(R.string.coach_model_lite_hint) }),
                        Triple("flash", R.string.coach_model_flash, stringResource(R.string.coach_model_flash_hint, s?.fastModel.orEmpty())),
                        Triple("pro", R.string.coach_model_pro, stringResource(R.string.coach_model_pro_hint, s?.smartModel.orEmpty())),
                    ).forEach { (id, label, hint) ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(stringResource(label), color = c.textPrimary)
                                    Text(hint, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                                }
                            },
                            leadingIcon = { Icon(Icons.Rounded.Check, contentDescription = null, tint = if (id == current) c.teal else androidx.compose.ui.graphics.Color.Transparent) },
                            onClick = { menu = false; vm.setModel(id) },
                        )
                    }
                    androidx.compose.material3.HorizontalDivider(color = c.divider)
                    DropdownMenuItem(text = { Text(stringResource(R.string.coach_clear)) }, onClick = { menu = false; confirmClear = true })
                }
            }
        }
        val showChips = messages.isNotEmpty() && hasKey && !sending
        Box(Modifier.weight(1f)) {
            LazyColumn(
                Modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = if (showChips) 72.dp else 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (!hasKey) {
                    item {
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(c.card).padding(20.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconCircle(Icons.Rounded.Key, c.blue, c.blueTrack, 40.dp)
                                Spacer(Modifier.width(12.dp))
                                Text(stringResource(R.string.coach_add_key), style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(stringResource(R.string.coach_add_key_body), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
                            Spacer(Modifier.height(14.dp))
                            PillButton(stringResource(R.string.coach_set_up_profile), onClick = { nav.goToTab(Routes.PROFILE) })
                        }
                    }
                }
                if (messages.isEmpty()) {
                    item { Welcome(enabled = hasKey) { vm.send(it) } }
                }
                items(messages, key = { it.id }) { m ->
                    when {
                        m.role == "user" -> UserBubble(m.text)
                        else -> CoachMessage(
                            m,
                            plan = vm.planOf(m),
                            onPreview = { preview = m },
                            onApply = { preview = m },
                        )
                    }
                }
                val live = draft?.let { PlanJson.stripPartial(it) }
                val writingPlan = draft?.let { Regex("```").findAll(it).count() % 2 == 1 } == true
                if (sending && (!live.isNullOrBlank() || writingPlan)) item(key = "draft") { StreamingBubble(live.orEmpty(), writingPlan) }
                else if (sending) item(key = "typing") { TypingDots() }
                if (checkingIn && !sending) item(key = "checkin") { CheckinNote() }
                item { Spacer(Modifier.height(4.dp)) }
            }
            // Suggestions float over the conversation. Text scrolling underneath fades out behind a short scrim
            // instead of showing through the pills, where it made both unreadable.
            if (showChips) {
                Row(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                        .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(c.background.copy(alpha = 0f), c.background.copy(alpha = 0.92f), c.background)))
                        .horizontalScroll(rememberScrollState())
                        .padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    suggestions.take(3).map { stringResource(it) }.forEach { s ->
                        Box(
                            Modifier.clip(RoundedCornerShape(50)).background(c.card)
                                .border(1.dp, c.divider.copy(alpha = 0.7f), RoundedCornerShape(50))
                                .clickable { vm.send(s) }.padding(horizontal = 14.dp, vertical = 8.dp),
                        ) {
                            Text(s, style = MaterialTheme.typography.labelLarge, color = c.textPrimary, maxLines = 1)
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            TextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text(stringResource(R.string.coach_input_hint)) },
                enabled = hasKey,
                shape = RoundedCornerShape(28.dp),
                maxLines = 5,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = c.card, unfocusedContainerColor = c.card, disabledContainerColor = c.card,
                    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    disabledIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    focusedTextColor = c.textPrimary, unfocusedTextColor = c.textPrimary, cursorColor = c.teal,
                    focusedPlaceholderColor = c.textSecondary, unfocusedPlaceholderColor = c.textSecondary,
                ),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            val canSend = input.isNotBlank() && !sending && hasKey
            Box(
                Modifier.size(52.dp).clip(CircleShape).background(if (canSend) c.teal else c.cardHigh)
                    .clickable(enabled = canSend) { vm.send(input.trim()); input = "" },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = stringResource(R.string.cd_send), tint = if (canSend) c.onTeal else c.textSecondary)
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            containerColor = c.card,
            title = { Text(stringResource(R.string.coach_clear_title), color = c.textPrimary) },
            text = { Text(stringResource(R.string.coach_clear_body), color = c.textSecondary) },
            confirmButton = { TextButton(onClick = { confirmClear = false; vm.clear() }) { Text(stringResource(R.string.action_clear), color = c.coral) } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.action_cancel), color = c.textSecondary) } },
        )
    }

    preview?.let { m ->
        vm.planOf(m)?.let { plan ->
            PlanPreviewSheet(plan, exercises, onApply = { vm.apply(m); preview = null }, onDismiss = { preview = null })
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Welcome(enabled: Boolean, onSuggestion: (String) -> Unit) {
    val c = Fit.colors
    Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        IconCircle(Icons.Rounded.AutoAwesome, c.teal, c.tealTrack, 64.dp)
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.coach_welcome_title), style = MaterialTheme.typography.titleLarge, color = c.textPrimary)
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.coach_welcome_body),
            style = MaterialTheme.typography.bodyMedium, color = c.textSecondary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            suggestions.map { stringResource(it) }.forEach { s ->
                Box(
                    Modifier.clip(RoundedCornerShape(50)).background(c.card).clickable(enabled = enabled) { onSuggestion(s) }
                        .padding(horizontal = 16.dp, vertical = 10.dp).alpha(if (enabled) 1f else 0.5f),
                ) { Text(s, style = MaterialTheme.typography.labelLarge, color = c.textPrimary) }
            }
        }
    }
}

@Composable
private fun UserBubble(text: String) {
    val c = Fit.colors
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Box(
            Modifier.widthIn(max = 300.dp).clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomStart = 22.dp, bottomEnd = 6.dp))
                .background(c.blueTrack).padding(horizontal = 16.dp, vertical = 12.dp),
        ) { Text(text, style = MaterialTheme.typography.bodyMedium, color = c.textPrimary) }
    }
}

@Composable
private fun CoachMessage(m: ChatEntity, plan: PlanDraft?, onPreview: () -> Unit, onApply: () -> Unit) {
    val c = Fit.colors
    val isError = m.kind == "error"
    Column(Modifier.fillMaxWidth().padding(end = 24.dp)) {
        if (m.kind == "checkin") {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 6.dp)) {
                Icon(Icons.Rounded.Insights, contentDescription = null, tint = c.gold, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.coach_weekly_checkin), style = MaterialTheme.typography.labelLarge, color = c.gold)
            }
        }
        Box(
            Modifier.clip(RoundedCornerShape(topStart = 6.dp, topEnd = 22.dp, bottomStart = 22.dp, bottomEnd = 22.dp))
                .background(if (isError) c.coralTrack else c.card).padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            MarkdownText(m.text, color = c.textPrimary)
        }
        if (isError && m.detail.isNotBlank()) {
            val clipboard = LocalClipboardManager.current
            val snackbar = LocalSnackbar.current
            val scope = rememberCoroutineScope()
            val copiedLabel = stringResource(R.string.coach_copied)
            Row(
                Modifier.padding(top = 2.dp).clip(RoundedCornerShape(50)).clickable {
                    clipboard.setText(AnnotatedString("${m.text}\n${m.detail}"))
                    // Android 13+ shows its own copied-to-clipboard preview.
                    if (android.os.Build.VERSION.SDK_INT < 33) scope.launch { snackbar.showSnackbar(copiedLabel) }
                }.padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.ContentCopy, contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.coach_copy_details), style = MaterialTheme.typography.labelMedium, color = c.textSecondary)
            }
        }
        if (plan != null) {
            Spacer(Modifier.height(8.dp))
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(c.card).padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconCircle(Icons.Rounded.CalendarMonth, c.teal, c.tealTrack, 40.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(plan.name, style = MaterialTheme.typography.titleSmall, color = c.textPrimary, fontWeight = FontWeight.SemiBold)
                        val training = plan.days.count { !it.isRest && it.items.isNotEmpty() }
                        Text(stringResource(R.string.coach_plan_summary, pluralStringResource(R.plurals.coach_training_days, training, training), pluralStringResource(R.plurals.coach_exercises, plan.days.sumOf { it.items.size }, plan.days.sumOf { it.items.size })), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (m.planApplied) {
                        PillButton(stringResource(R.string.coach_applied), onClick = onPreview, icon = Icons.Rounded.Check, container = c.tealTrack, content = c.teal)
                    } else {
                        PillButton(stringResource(R.string.coach_review_apply), onClick = onApply)
                    }
                }
            }
        }
    }
}

/** The coach's reply as it streams in, with a soft pulsing caret at the end. */
@Composable
private fun StreamingBubble(text: String, writingPlan: Boolean) {
    val c = Fit.colors
    val t = rememberInfiniteTransition(label = "caret")
    val a by t.animateFloat(0.2f, 1f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "caretAlpha")
    Column(Modifier.fillMaxWidth().padding(end = 24.dp)) {
        Box(
            Modifier.clip(RoundedCornerShape(topStart = 6.dp, topEnd = 22.dp, bottomStart = 22.dp, bottomEnd = 22.dp))
                .background(c.card).padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Column {
                if (text.isNotBlank()) MarkdownText(text, color = c.textPrimary)
                if (writingPlan) {
                    // The plan itself streams as JSON the person never sees; show that something is still happening.
                    Spacer(Modifier.height(if (text.isNotBlank()) 10.dp else 0.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.CalendarMonth, contentDescription = null, tint = c.teal, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.coach_writing_week), style = MaterialTheme.typography.labelLarge, color = c.teal)
                    }
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(color = c.teal, trackColor = c.tealTrack, modifier = Modifier.fillMaxWidth().clip(CircleShape))
                } else {
                    Box(Modifier.padding(top = 4.dp).size(width = 10.dp, height = 3.dp).alpha(a).clip(CircleShape).background(c.teal))
                }
            }
        }
    }
}

@Composable
private fun CheckinNote() {
    val c = Fit.colors
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
        Icon(Icons.Rounded.Insights, contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text(stringResource(R.string.coach_checkin_background), style = MaterialTheme.typography.labelMedium, color = c.textSecondary)
    }
}

@Composable
private fun TypingDots() {
    val c = Fit.colors
    val t = rememberInfiniteTransition(label = "typing")
    Row(Modifier.clip(RoundedCornerShape(22.dp)).background(c.card).padding(horizontal = 18.dp, vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        (0..2).forEach { i ->
            val a by t.animateFloat(0.25f, 1f, infiniteRepeatable(tween(500, delayMillis = i * 150), RepeatMode.Reverse), label = "dot$i")
            Box(Modifier.size(8.dp).alpha(a).clip(CircleShape).background(c.textSecondary))
        }
    }
}
