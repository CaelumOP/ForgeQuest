package com.forgequest.app.ui.coach

import com.forgequest.app.ui.theme.ForgeShape
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
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.runtime.rememberCoroutineScope
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
import com.forgequest.app.container
import com.forgequest.app.ui.components.toast
import com.forgequest.app.data.CoachRepository
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
    val aiOn: StateFlow<Boolean> = app.settings.aiOn
    val sending = MutableStateFlow(false)
    val applied = MutableStateFlow<String?>(null)
    /** Text of the reply still arriving, shown live. */
    val draft: StateFlow<String?> = app.coach.draft

    init {
        viewModelScope.launch { app.coach.warmUp() }
    }

    /** [aboutWorkout]: the debrief button's message (see CoachRepository.debriefChatMessage). */
    fun send(text: String, aboutWorkout: Boolean = false) {
        if (text.isBlank() || sending.value) return
        sending.value = true
        viewModelScope.launch {
            app.coach.send(text, aboutWorkout)
            if (app.coach.lastReplyFailed()) app.haptics.error()
            sending.value = false
        }
    }

    /** A card made before the plan was edited by hand; the screen asks before overwriting those edits. */
    val staleCard = MutableStateFlow<ChatEntity?>(null)

    fun apply(message: ChatEntity, force: Boolean = false) = viewModelScope.launch {
        when (app.coach.applyPlan(message.id, force)) {
            CoachRepository.ApplyResult.APPLIED -> {
                app.haptics.celebrate()
                applied.value = app.str(R.string.coach_plan_applied)
            }
            CoachRepository.ApplyResult.STALE -> staleCard.value = message
            CoachRepository.ApplyResult.FAILED -> Unit
        }
    }

    fun clear() = viewModelScope.launch { app.coach.clear() }

    val lastAnswer = app.coach.lastAnswer

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
    val hasKey by vm.hasKey.collectAsState()
    val aiOn by vm.aiOn.collectAsState()
    val applied by vm.applied.collectAsState()
    val draft by vm.draft.collectAsState()
    val snackbar = LocalSnackbar.current
    val c = Fit.colors
    var input by remember { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var preview by remember { mutableStateOf<ChatEntity?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size, sending, draft?.length) {
        if (messages.isNotEmpty()) listState.scrollToItem(listState.layoutInfo.totalItemsCount.coerceAtLeast(1) - 1)
    }
    LaunchedEffect(applied) { applied?.let { snackbar.showSnackbar(it); vm.applied.value = null } }
    // "Ask the coach about this workout" on a debrief: sent the moment the tab opens, no typing.
    val pending by vm.app.coach.pendingMessage.collectAsState()
    LaunchedEffect(pending) { pending?.let { vm.app.coach.pendingMessage.value = null; vm.send(it, aboutWorkout = true) } }

    Column(Modifier.fillMaxSize().background(c.background).statusBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.tab_coach), style = MaterialTheme.typography.headlineMedium, color = c.textPrimary, modifier = Modifier.weight(1f))
            if (aiOn) Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.cd_more), tint = c.textSecondary) }
                DropdownMenu(menu, { menu = false }, containerColor = c.cardHigh) {
                    val last by vm.lastAnswer.collectAsState()
                    last?.let { (who, ms) ->
                        if (ms >= 0) Text(stringResource(R.string.coach_answered_by, who, "%.1f".format(ms / 1000f)), style = MaterialTheme.typography.bodySmall, color = c.teal, modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp))
                    }
                    androidx.compose.material3.HorizontalDivider(color = c.divider)
                    DropdownMenuItem(text = { Text(stringResource(R.string.coach_clear)) }, onClick = { menu = false; confirmClear = true })
                }
            }
        }
        // The Gemini app round trip, one tap each: send the data with a question, paste the reply back.
        val ctx = androidx.compose.ui.platform.LocalContext.current
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TonalPill(stringResource(R.string.ask_gemini), onClick = {
                val question = input
                val arabic = ctx.resources.configuration.locales[0].language == "ar"
                scope.launch {
                    val data = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { ctx.container.bridge.dataText() }
                    val opened = com.forgequest.app.data.bridge.AskGemini.send(ctx, com.forgequest.app.data.bridge.AskGemini.prompt(data, question, arabic))
                    ctx.toast(ctx.getString(if (opened) R.string.ask_gemini_sent else R.string.ask_gemini_copied))
                }
            }, icon = Icons.Rounded.AutoAwesome, modifier = Modifier.weight(1f))
            TonalPill(stringResource(R.string.paste_gemini_reply), onClick = {
                val clip = (ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager)
                    .primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(ctx)?.toString().orEmpty()
                if (clip.contains(com.forgequest.app.data.bridge.BridgeFormat.HEADER)) {
                    scope.launch {
                        ctx.container.bridge.applyText(clip)
                        ctx.toast(ctx.getString(R.string.paste_gemini_done))
                    }
                } else {
                    ctx.toast(ctx.getString(R.string.paste_gemini_none))
                }
            }, icon = Icons.Rounded.ContentPaste, modifier = Modifier.weight(1f))
        }
        // Once per Gem: the instructions that make Gemini answer in the format "Paste reply" reads.
        val skillClipboard = com.forgequest.app.ui.components.rememberTextClipboard()
        TextButton(
            onClick = {
                skillClipboard.copy(com.forgequest.app.ui.components.SkillPrompts.coach(ctx))
                ctx.toast(ctx.getString(R.string.skill_copied))
            },
            modifier = Modifier.padding(horizontal = 8.dp),
        ) { Text(stringResource(R.string.coach_copy_skill), color = c.teal) }
        // Everything above works through the Gemini app, with no API key; only the chat below needs the coach on.
        if (!aiOn) {
            TextButton(onClick = { nav.navigate(Routes.settingsGroup("coach")) }, modifier = Modifier.padding(horizontal = 8.dp)) { Text(stringResource(R.string.coach_turn_on_ai), color = c.teal) }
            return@Column
        }
        // Out of the way while typing, so a tap near Send never sends a suggestion instead.
        val showChips = messages.isNotEmpty() && hasKey && !sending && input.isBlank()
        Box(Modifier.weight(1f)) {
            LazyColumn(
                Modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = if (showChips) 72.dp else 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (!hasKey) {
                    item {
                        Column(Modifier.fillMaxWidth().clip(ForgeShape.Lg).background(c.card).padding(20.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconCircle(Icons.Rounded.Key, c.teal, c.tealTrack, 40.dp)
                                Spacer(Modifier.width(12.dp))
                                Text(stringResource(R.string.ai_key_needed_coach), style = MaterialTheme.typography.titleMedium, color = c.textPrimary, modifier = Modifier.weight(1f))
                            }
                            Spacer(Modifier.height(12.dp))
                            PillButton(stringResource(R.string.ai_key_needed_button), onClick = { nav.navigate(Routes.settingsGroup("coach")) })
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
                item { Spacer(Modifier.height(4.dp)) }
            }
            // Suggestions float over the conversation. Text scrolling underneath fades out behind a short scrim
            // instead of showing through the pills, where it made both unreadable.
            if (showChips) {
                Row(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                        .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(c.background.copy(alpha = 0f), c.background.copy(alpha = 0.92f), c.background)))
                        .horizontalScroll(rememberScrollState())
                        .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    suggestions.take(3).map { stringResource(it) }.forEach { s ->
                        Box(
                            Modifier.clip(ForgeShape.Full).background(c.card)
                                .border(1.dp, c.divider.copy(alpha = 0.7f), ForgeShape.Full)
                                .clickable { vm.send(s) }.padding(horizontal = 12.dp, vertical = 8.dp),
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
                shape = ForgeShape.Lg,
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

    val stale by vm.staleCard.collectAsState()
    stale?.let { m ->
        AlertDialog(
            onDismissRequest = { vm.staleCard.value = null },
            containerColor = c.card,
            title = { Text(stringResource(R.string.coach_stale_title), color = c.textPrimary) },
            text = { Text(stringResource(R.string.coach_stale_body), color = c.textSecondary) },
            confirmButton = { TextButton(onClick = { vm.staleCard.value = null; vm.apply(m, force = true) }) { Text(stringResource(R.string.coach_stale_apply), color = c.coral) } },
            dismissButton = { TextButton(onClick = { vm.staleCard.value = null }) { Text(stringResource(R.string.action_cancel), color = c.textSecondary) } },
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
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.coach_welcome_body),
            style = MaterialTheme.typography.bodyMedium, color = c.textSecondary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            suggestions.map { stringResource(it) }.forEach { s ->
                Box(
                    Modifier.clip(ForgeShape.Full).background(c.card).clickable(enabled = enabled) { onSuggestion(s) }
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
            Modifier.widthIn(max = 300.dp).clip(RoundedCornerShape(topStart = ForgeShape.MdRadius, topEnd = ForgeShape.MdRadius, bottomStart = ForgeShape.MdRadius, bottomEnd = 4.dp))
                .background(c.tealTrack).padding(horizontal = 16.dp, vertical = 12.dp),
        ) { Text(text, style = MaterialTheme.typography.bodyMedium, color = c.textPrimary) }
    }
}

@Composable
private fun CoachMessage(m: ChatEntity, plan: PlanDraft?, onPreview: () -> Unit, onApply: () -> Unit) {
    val c = Fit.colors
    val isError = m.kind == "error"
    Column(Modifier.fillMaxWidth().padding(end = 24.dp)) {
        if (m.kind == "checkin") {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                Icon(Icons.Rounded.Insights, contentDescription = null, tint = c.teal, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.coach_weekly_checkin), style = MaterialTheme.typography.labelLarge, color = c.teal)
            }
        }
        Box(
            Modifier.clip(RoundedCornerShape(topStart = 4.dp, topEnd = ForgeShape.MdRadius, bottomStart = ForgeShape.MdRadius, bottomEnd = ForgeShape.MdRadius))
                .background(if (isError) c.coralTrack else c.card).padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            MarkdownText(m.text, color = c.textPrimary)
        }
        if (isError && m.detail.isNotBlank()) {
            val clipboard = com.forgequest.app.ui.components.rememberTextClipboard()
            val snackbar = LocalSnackbar.current
            val scope = rememberCoroutineScope()
            val copiedLabel = stringResource(R.string.coach_copied)
            Row(
                Modifier.padding(top = 2.dp).clip(ForgeShape.Full).clickable {
                    clipboard.copy("${m.text}\n${m.detail}")
                    // Android 13+ shows its own copied-to-clipboard preview.
                    if (android.os.Build.VERSION.SDK_INT < 33) scope.launch { snackbar.showSnackbar(copiedLabel) }
                }.padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.ContentCopy, contentDescription = null, tint = c.textSecondary, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.coach_copy_details), style = MaterialTheme.typography.labelMedium, color = c.textSecondary)
            }
        }
        if (plan != null) {
            Spacer(Modifier.height(8.dp))
            Column(Modifier.fillMaxWidth().clip(ForgeShape.Md).background(c.card).padding(16.dp)) {
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
            Modifier.clip(RoundedCornerShape(topStart = 4.dp, topEnd = ForgeShape.MdRadius, bottomStart = ForgeShape.MdRadius, bottomEnd = ForgeShape.MdRadius))
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
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(color = c.teal, trackColor = c.tealTrack, modifier = Modifier.fillMaxWidth().clip(CircleShape))
                } else {
                    Box(Modifier.padding(top = 4.dp).size(width = 10.dp, height = 3.dp).alpha(a).clip(CircleShape).background(c.teal))
                }
            }
        }
    }
}

/** The coach is thinking: an Expressive morphing shape in the coach purple. */
@Composable
private fun TypingDots() {
    val c = Fit.colors
    Box(Modifier.clip(ForgeShape.Md).background(c.card).padding(horizontal = 10.dp, vertical = 4.dp)) {
        com.forgequest.app.ui.components.MorphLoader(c.purple, Modifier.size(40.dp))
    }
}
