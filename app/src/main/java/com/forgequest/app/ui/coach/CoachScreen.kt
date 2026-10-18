package com.forgequest.app.ui.coach

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
    val applied = MutableStateFlow<String?>(null)
    /** Text of the reply still arriving, shown live. */
    val draft: StateFlow<String?> = app.coach.draft

    init {
        viewModelScope.launch {
            sending.value = true
            runCatching { app.coach.weeklyCheckinIfDue() }
            sending.value = false
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
            applied.value = "New plan applied. Check the Workouts tab."
        }
    }

    fun clear() = viewModelScope.launch { app.coach.clear() }

    fun planOf(message: ChatEntity): PlanDraft? = message.planJson?.let(PlanJson::decode)
}

private val suggestions = listOf(
    "Review my week",
    "How do I get my first pull-up?",
    "Build me a 4-day plan",
    "High-protein breakfast ideas",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CoachScreen(nav: NavHostController) {
    val vm = appViewModel { CoachViewModel(it) }
    val messages by vm.messages.collectAsState()
    val exercises by vm.exercises.collectAsState()
    val sending by vm.sending.collectAsState()
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
            Text("Coach", style = MaterialTheme.typography.headlineMedium, color = c.textPrimary, modifier = Modifier.weight(1f))
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = "More", tint = c.textSecondary) }
                DropdownMenu(menu, { menu = false }, containerColor = c.cardHigh) {
                    DropdownMenuItem(text = { Text("Clear conversation") }, onClick = { menu = false; confirmClear = true })
                }
            }
        }
        LazyColumn(
            Modifier.weight(1f),
            state = listState,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!hasKey) {
                item {
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(c.card).padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconCircle(Icons.Rounded.Key, c.blue, c.blueTrack, 40.dp)
                            Spacer(Modifier.width(12.dp))
                            Text("Add your free AI key", style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("The coach runs on Google Gemini with your own free key. It takes about a minute.", style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
                        Spacer(Modifier.height(14.dp))
                        PillButton("Set up in Profile", onClick = { nav.goToTab(Routes.PROFILE) })
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
                        onApply = { vm.apply(m) },
                    )
                }
            }
            val live = draft?.let { PlanJson.stripPartial(it) }
            if (sending && !live.isNullOrBlank()) item(key = "draft") { StreamingBubble(live) }
            else if (sending) item(key = "typing") { TypingDots() }
            item { Spacer(Modifier.height(4.dp)) }
        }
        if (messages.isNotEmpty() && hasKey && !sending) {
            FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                suggestions.take(2).forEach { s ->
                    Box(Modifier.clip(RoundedCornerShape(50)).background(c.card).clickable { vm.send(s) }.padding(horizontal = 14.dp, vertical = 8.dp)) {
                        Text(s, style = MaterialTheme.typography.labelLarge, color = c.textPrimary)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            TextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text("Ask your coach…") },
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
                Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Send", tint = if (canSend) c.onTeal else c.textSecondary)
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            containerColor = c.card,
            title = { Text("Clear the conversation?", color = c.textPrimary) },
            text = { Text("Messages and weekly check-ins are deleted. Plans you already applied stay.", color = c.textSecondary) },
            confirmButton = { TextButton(onClick = { confirmClear = false; vm.clear() }) { Text("Clear", color = c.coral) } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel", color = c.textSecondary) } },
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
        Text("Your training coach", style = MaterialTheme.typography.titleLarge, color = c.textPrimary)
        Spacer(Modifier.height(6.dp))
        Text(
            "It knows your plan, workouts, records and meals. Ask for advice or a new plan you can apply in one tap.",
            style = MaterialTheme.typography.bodyMedium, color = c.textSecondary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            suggestions.forEach { s ->
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
                Text("Weekly check-in", style = MaterialTheme.typography.labelLarge, color = c.gold)
            }
        }
        Box(
            Modifier.clip(RoundedCornerShape(topStart = 6.dp, topEnd = 22.dp, bottomStart = 22.dp, bottomEnd = 22.dp))
                .background(if (isError) c.coralTrack else c.card).padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            MarkdownText(m.text, color = if (isError) c.textPrimary else c.textPrimary)
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
                        Text("$training training days · ${plan.days.sumOf { it.items.size }} exercises", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TonalPill("Preview", onClick = onPreview)
                    if (m.planApplied) {
                        PillButton("Applied", onClick = {}, icon = Icons.Rounded.Check, container = c.tealTrack, content = c.teal)
                    } else {
                        PillButton("Apply plan", onClick = onApply)
                    }
                }
            }
        }
    }
}

/** The coach's reply as it streams in, with a soft pulsing caret at the end. */
@Composable
private fun StreamingBubble(text: String) {
    val c = Fit.colors
    val t = rememberInfiniteTransition(label = "caret")
    val a by t.animateFloat(0.2f, 1f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "caretAlpha")
    Column(Modifier.fillMaxWidth().padding(end = 24.dp)) {
        Box(
            Modifier.clip(RoundedCornerShape(topStart = 6.dp, topEnd = 22.dp, bottomStart = 22.dp, bottomEnd = 22.dp))
                .background(c.card).padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Column {
                MarkdownText(text, color = c.textPrimary)
                Box(Modifier.padding(top = 4.dp).size(width = 10.dp, height = 3.dp).alpha(a).clip(CircleShape).background(c.teal))
            }
        }
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
