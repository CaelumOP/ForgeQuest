package com.forgequest.app.ui.onboarding

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.forgequest.app.AppContainer
import com.forgequest.app.data.ai.AiError
import com.forgequest.app.data.plan.PlanDraft
import com.forgequest.app.data.plan.PlanDraftDay
import com.forgequest.app.data.prefs.BodyType
import com.forgequest.app.data.seed.StarterPlans
import com.forgequest.app.ui.appViewModel
import com.forgequest.app.ui.components.ChoiceChip
import com.forgequest.app.ui.components.IconCircle
import com.forgequest.app.ui.components.PillButton
import com.forgequest.app.ui.components.ProgressLine
import com.forgequest.app.ui.components.Stepper
import com.forgequest.app.ui.theme.Fit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

data class OnboardingState(
    val step: Int = 0,
    val name: String = "",
    val bodyType: BodyType = BodyType.MALE,
    val weightKg: Float = 75f,
    val kcal: Int = 2500,
    val protein: Int = 135,
    val goalsTouched: Boolean = false,
    val planIndex: Int = 0,
    val apiKey: String = "",
    val checkingKey: Boolean = false,
    val keyMessage: String? = null,
    val keyOk: Boolean = false,
)

class OnboardingViewModel(private val app: AppContainer) : ViewModel() {
    val state = MutableStateFlow(OnboardingState())

    fun update(transform: (OnboardingState) -> OnboardingState) = state.update(transform)

    fun setWeight(kg: Float) = state.update { s ->
        val w = kg.coerceIn(30f, 250f)
        if (s.goalsTouched) s.copy(weightKg = w)
        else s.copy(weightKg = w, protein = suggestedProtein(w), kcal = suggestedKcal(w))
    }

    fun next() = state.update { it.copy(step = (it.step + 1).coerceAtMost(LAST_STEP)) }
    fun back() = state.update { it.copy(step = (it.step - 1).coerceAtLeast(0)) }

    fun checkKey() {
        val key = state.value.apiKey.trim()
        if (key.isEmpty()) return
        state.update { it.copy(checkingKey = true, keyMessage = null) }
        viewModelScope.launch {
            val result = app.gemini.discoverModels(key)
            state.update { s ->
                result.fold(
                    onSuccess = { s.copy(checkingKey = false, keyOk = true, keyMessage = "Key works. AI features are ready.") },
                    onFailure = { e -> s.copy(checkingKey = false, keyOk = false, keyMessage = (e as? AiError)?.userMessage ?: "Couldn't check the key.") },
                )
            }
            result.getOrNull()?.let { models -> app.settings.update { it.copy(fastModel = models.fast, smartModel = models.smart) } }
        }
    }

    fun finish() {
        val s = state.value
        viewModelScope.launch {
            if (s.keyOk) app.settings.setApiKey(s.apiKey)
            val plan = StarterPlans.all.getOrNull(s.planIndex) ?: emptyPlan
            app.workouts.seedLibrary()
            app.workouts.applyPlan(plan, if (plan === emptyPlan) "manual" else "starter")
            app.progress.logWeight(s.weightKg)
            app.settings.update {
                it.copy(onboarded = true, name = s.name.trim(), bodyType = s.bodyType, kcalGoal = s.kcal, proteinGoal = s.protein)
            }
        }
    }

    companion object {
        const val LAST_STEP = 3
        fun suggestedProtein(kg: Float) = ((kg * 1.8f) / 5f).roundToInt() * 5
        fun suggestedKcal(kg: Float) = ((kg * 33f) / 50f).roundToInt() * 50
        val emptyPlan = PlanDraft("My plan", "Build it day by day.", (1..7).map { PlanDraftDay(it, "Rest day", isRest = true) })
    }
}

@Composable
fun OnboardingScreen() {
    val vm = appViewModel { OnboardingViewModel(it) }
    val s by vm.state.collectAsState()
    val c = Fit.colors
    BackHandler(enabled = s.step > 0) { vm.back() }

    Column(
        Modifier.fillMaxSize().background(c.background).statusBarsPadding().navigationBarsPadding().imePadding().padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(16.dp))
        ProgressLine((s.step + 1) / (OnboardingViewModel.LAST_STEP + 1f), c.teal, c.cardHigh, height = 4.dp)
        Spacer(Modifier.height(24.dp))
        AnimatedContent(
            targetState = s.step,
            transitionSpec = {
                val dir = if (targetState > initialState) 1 else -1
                (slideInHorizontally { it / 4 * dir } + fadeIn()) togetherWith (slideOutHorizontally { -it / 4 * dir } + fadeOut())
            },
            modifier = Modifier.weight(1f),
            label = "step",
        ) { step ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                when (step) {
                    0 -> StepWelcome(s, vm)
                    1 -> StepGoals(s, vm)
                    2 -> StepPlan(s, vm)
                    else -> StepKey(s, vm)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (s.step > 0) TextButton(onClick = vm::back) { Text("Back", color = c.textSecondary) }
            Spacer(Modifier.weight(1f))
            when {
                s.step < OnboardingViewModel.LAST_STEP -> PillButton("Continue", onClick = vm::next, enabled = s.step != 0 || s.name.isNotBlank())
                else -> PillButton(if (s.keyOk) "Let's go" else "Skip for now", onClick = vm::finish)
            }
        }
    }
}

@Composable
private fun Title(title: String, body: String) {
    val c = Fit.colors
    Text(title, style = MaterialTheme.typography.headlineMedium, color = c.textPrimary)
    Spacer(Modifier.height(8.dp))
    Text(body, style = MaterialTheme.typography.bodyLarge, color = c.textSecondary)
    Spacer(Modifier.height(28.dp))
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Fit.colors.teal,
    unfocusedBorderColor = Fit.colors.divider,
    focusedTextColor = Fit.colors.textPrimary,
    unfocusedTextColor = Fit.colors.textPrimary,
    cursorColor = Fit.colors.teal,
    focusedLabelColor = Fit.colors.teal,
    unfocusedLabelColor = Fit.colors.textSecondary,
)

@Composable
private fun StepWelcome(s: OnboardingState, vm: OnboardingViewModel) {
    val c = Fit.colors
    Title("Welcome to ForgeQuest", "Train, eat well and watch yourself level up. First, what should we call you?")
    OutlinedTextField(
        value = s.name,
        onValueChange = { v -> vm.update { it.copy(name = v.take(24)) } },
        label = { Text("Your name") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
        shape = RoundedCornerShape(16.dp),
        colors = fieldColors(),
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(28.dp))
    Text("Body for the muscle map", style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ChoiceChip("Male", s.bodyType == BodyType.MALE, { vm.update { it.copy(bodyType = BodyType.MALE) } })
        ChoiceChip("Female", s.bodyType == BodyType.FEMALE, { vm.update { it.copy(bodyType = BodyType.FEMALE) } })
    }
}

@Composable
private fun StepGoals(s: OnboardingState, vm: OnboardingViewModel) {
    Title("Your body & goals", "We suggest goals from your weight. Adjust them any time in Profile.")
    GoalRow("Body weight", "%.1f kg".format(s.weightKg), { vm.setWeight(s.weightKg - 0.5f) }, { vm.setWeight(s.weightKg + 0.5f) })
    GoalRow("Daily protein", "${s.protein} g", { vm.update { it.copy(protein = (it.protein - 5).coerceAtLeast(40), goalsTouched = true) } }, { vm.update { it.copy(protein = (it.protein + 5).coerceAtMost(400), goalsTouched = true) } })
    GoalRow("Daily calories", "%,d kcal".format(s.kcal), { vm.update { it.copy(kcal = (it.kcal - 50).coerceAtLeast(1000), goalsTouched = true) } }, { vm.update { it.copy(kcal = (it.kcal + 50).coerceAtMost(6000), goalsTouched = true) } })
    Spacer(Modifier.height(8.dp))
    Text(
        "Protein suggestion: about 1.8 g per kg of body weight, a common target for building muscle.",
        style = MaterialTheme.typography.bodySmall,
        color = Fit.colors.textSecondary,
    )
}

@Composable
private fun GoalRow(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    val c = Fit.colors
    Row(
        Modifier.fillMaxWidth().padding(bottom = 12.dp).clip(RoundedCornerShape(20.dp)).background(c.card).padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.titleSmall, color = c.textPrimary, modifier = Modifier.weight(1f))
        Stepper(value, onMinus, onPlus, minValueWidth = 84.dp)
    }
}

@Composable
private fun StepPlan(s: OnboardingState, vm: OnboardingViewModel) {
    val c = Fit.colors
    Title("Pick a starting plan", "You can edit it, ask the coach to change it, or import your own later.")
    val options = StarterPlans.all.map { it.name to it.description } + ("Start empty" to "No plan yet. Add your own exercises day by day.")
    options.forEachIndexed { index, (name, desc) ->
        val selected = s.planIndex == index
        Row(
            Modifier.fillMaxWidth().padding(bottom = 12.dp).clip(RoundedCornerShape(20.dp)).background(c.card)
                .then(if (selected) Modifier.border(2.dp, c.teal, RoundedCornerShape(20.dp)) else Modifier)
                .clickable { vm.update { it.copy(planIndex = index) } }
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
                Spacer(Modifier.height(4.dp))
                Text(desc, style = MaterialTheme.typography.bodySmall, color = c.textSecondary, maxLines = 3)
            }
            Spacer(Modifier.width(12.dp))
            Box(
                Modifier.size(24.dp).clip(CircleShape).background(if (selected) c.teal else c.cardHigh),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = c.onTeal, modifier = Modifier.size(24.dp))
            }
        }
    }
}

@Composable
private fun StepKey(s: OnboardingState, vm: OnboardingViewModel) {
    val c = Fit.colors
    val context = LocalContext.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconCircle(Icons.Rounded.AutoAwesome, c.blue, c.blueTrack)
        Spacer(Modifier.width(12.dp))
        Text("Optional", style = MaterialTheme.typography.labelLarge, color = c.textSecondary)
    }
    Spacer(Modifier.height(16.dp))
    Title(
        "Turn on AI features",
        "The meal scanner and coach run on Google Gemini. A personal key is free and takes about a minute.",
    )
    listOf(
        "Open Google AI Studio and sign in with your Google account.",
        "Tap \"Create API key\", then copy it.",
        "Paste it below.",
    ).forEachIndexed { i, step ->
        Row(Modifier.padding(bottom = 10.dp), verticalAlignment = Alignment.Top) {
            Box(Modifier.size(24.dp).clip(CircleShape).background(c.tealTrack), contentAlignment = Alignment.Center) {
                Text("${i + 1}", style = MaterialTheme.typography.labelMedium, color = c.teal, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(12.dp))
            Text(step, style = MaterialTheme.typography.bodyMedium, color = c.textPrimary)
        }
    }
    Spacer(Modifier.height(8.dp))
    PillButton(
        "Get a free key",
        onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/apikey"))) },
        icon = Icons.Rounded.OpenInNew,
        container = c.blueTrack,
        content = c.blue,
    )
    Spacer(Modifier.height(20.dp))
    OutlinedTextField(
        value = s.apiKey,
        onValueChange = { v -> vm.update { it.copy(apiKey = v.trim(), keyOk = false, keyMessage = null) } },
        label = { Text("Gemini API key") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        shape = RoundedCornerShape(16.dp),
        colors = fieldColors(),
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(12.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        PillButton(
            if (s.checkingKey) "Checking…" else "Check key",
            onClick = vm::checkKey,
            enabled = s.apiKey.isNotBlank() && !s.checkingKey,
        )
        Spacer(Modifier.width(12.dp))
        s.keyMessage?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = if (s.keyOk) c.teal else c.coral, modifier = Modifier.weight(1f))
        }
    }
    Spacer(Modifier.height(12.dp))
    Text("Your key stays encrypted on this phone.", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
}
