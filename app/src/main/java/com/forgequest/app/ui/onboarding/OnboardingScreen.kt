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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.forgequest.app.AppContainer
import com.forgequest.app.R
import com.forgequest.app.data.ai.AiError
import com.forgequest.app.data.plan.PlanDraft
import com.forgequest.app.data.plan.PlanDraftDay
import com.forgequest.app.data.prefs.BodyType
import com.forgequest.app.data.seed.ExerciseCatalog
import com.forgequest.app.data.seed.StarterPlans
import com.forgequest.app.domain.Equipment
import com.forgequest.app.domain.EquipmentPreset
import com.forgequest.app.domain.Dates
import com.forgequest.app.domain.ExerciseSwapEngine
import com.forgequest.app.domain.Nutrition
import com.forgequest.app.domain.NutritionGoal
import com.forgequest.app.domain.WeightUnit
import com.forgequest.app.ui.appViewModel
import com.forgequest.app.ui.labelRes
import com.forgequest.app.ui.components.ChoiceChip
import com.forgequest.app.ui.components.IconCircle
import com.forgequest.app.ui.components.NumberStepper
import com.forgequest.app.ui.components.PillButton
import com.forgequest.app.ui.components.ProgressLine
import com.forgequest.app.ui.theme.Fit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import com.forgequest.app.domain.CutPace
import com.forgequest.app.domain.DayActivity
import com.forgequest.app.domain.GainPace
import com.forgequest.app.ui.descRes
import com.forgequest.app.ui.components.SegmentedTabs
import kotlin.math.abs
import kotlin.math.roundToInt
import java.util.Locale

data class OnboardingState(
    val step: Int = 0,
    val name: String = "",
    val bodyType: BodyType = BodyType.MALE,
    val weightKg: Float = 75f,
    val unit: WeightUnit = WeightUnit.KG,
    /** 0 until typed: height and age are required before the body step continues. */
    val heightCm: Float = 0f,
    val age: Int = 0,
    val dayActivity: DayActivity = DayActivity.SITTING,
    val kcal: Int = 2500,
    val protein: Int = 135,
    val goalsTouched: Boolean = false,
    val goal: NutritionGoal = NutritionGoal.MAINTAIN,
    val cutPace: CutPace = CutPace.STANDARD,
    val gainPace: GainPace = GainPace.STANDARD,
    val planIndex: Int = 0,
    val equipmentPreset: EquipmentPreset = EquipmentPreset.CALISTHENICS_PARK,
    val apiKey: String = "",
    val checkingKey: Boolean = false,
    val keyMessage: String? = null,
    val keyOk: Boolean = false,
) {
    val male get() = bodyType == BodyType.MALE
    val bodyReady get() = heightCm in 120f..230f && age in 10..100
    /** Training days of the chosen starter plan (3 when starting empty), for the calorie suggestion. */
    val trainingDays get() = StarterPlans.all.getOrNull(planIndex)?.days?.count { !it.isRest && it.items.isNotEmpty() } ?: 3
    val floor get() = Nutrition.calorieFloor(weightKg, heightCm, age, male)
    val burn get() = Nutrition.dailyBurn(weightKg, heightCm, age, male, trainingDays, base = dayActivity.base)
    val fastBlocked get() = Nutrition.allowedCutPace(CutPace.AGGRESSIVE, weightKg, heightCm, age, male) != CutPace.AGGRESSIVE
}

class OnboardingViewModel(private val app: AppContainer) : ViewModel() {
    val state = MutableStateFlow(OnboardingState())

    /** Applies a change and refreshes both suggestions, unless the person already set the goals by hand. */
    fun update(transform: (OnboardingState) -> OnboardingState) = state.update { s ->
        val next = transform(s)
        if (next.goalsTouched) next else next.copy(
            protein = Nutrition.suggestedProtein(next.weightKg, Nutrition.proteinPerKgFor(next.goal, next.cutPace, next.gainPace), 0f, next.heightCm),
            kcal = Nutrition.suggestedKcal(
                next.weightKg, next.heightCm, next.age, next.male, next.trainingDays, goal = next.goal,
                base = next.dayActivity.base, cutPace = next.cutPace, gainPace = next.gainPace,
            ),
        )
    }

    fun setWeight(kg: Float) = update { it.copy(weightKg = kg.coerceIn(30f, 250f)) }

    fun next() = state.update { it.copy(step = (it.step + 1).coerceAtMost(LAST_STEP)) }
    fun back() = state.update { it.copy(step = (it.step - 1).coerceAtLeast(0)) }

    /** [then] runs once the key checks out, e.g. finishing setup from the main button. */
    fun checkKey(then: () -> Unit = {}) {
        val key = state.value.apiKey.trim()
        if (key.isEmpty()) return
        state.update { it.copy(checkingKey = true, keyMessage = null) }
        viewModelScope.launch {
            val result = app.gemini.discoverModels(key)
            state.update { s ->
                result.fold(
                    onSuccess = { s.copy(checkingKey = false, keyOk = true, keyMessage = app.str(R.string.onb_key_works)) },
                    onFailure = { e -> s.copy(checkingKey = false, keyOk = false, keyMessage = (e as? AiError)?.userMessage ?: app.str(R.string.onb_key_check_failed)) },
                )
            }
            result.getOrNull()?.let { models -> app.models.save(models); then() }
        }
    }

    private val emptyPlan by lazy {
        PlanDraft(
            app.str(R.string.onb_my_plan),
            app.str(R.string.onb_my_plan_desc),
            (1..7).map { PlanDraftDay(it, app.str(R.string.today_rest_day), isRest = true) },
        )
    }

    fun finish() {
        val s = state.value
        viewModelScope.launch {
            if (s.keyOk) app.settings.setApiKey(s.apiKey)
            app.workouts.seedLibrary()
            val selectedPlan = StarterPlans.all.getOrNull(s.planIndex) ?: emptyPlan
            val catalogById = ExerciseCatalog.all.associateBy { it.id }
            val adaptedPlan = if (selectedPlan === emptyPlan) {
                emptyPlan
            } else {
                ExerciseSwapEngine.adaptPlanToEquipment(selectedPlan, catalogById, s.equipmentPreset.equipment).first
            }
            app.workouts.applyPlan(adaptedPlan, if (selectedPlan === emptyPlan) "manual" else "starter")
            app.progress.logWeight(s.weightKg)
            app.settings.update {
                it.copy(
                    onboarded = true,
                    name = s.name.trim(),
                    bodyType = s.bodyType,
                    kcalGoal = s.kcal.coerceAtLeast(s.floor),
                    proteinGoal = s.protein.coerceAtLeast(60),
                    unit = s.unit.code,
                    heightCm = s.heightCm,
                    birthYear = if (s.age > 0) java.time.Year.now().value - s.age else 0,
                    equipment = s.equipmentPreset.encode(),
                    nutritionGoal = s.goal,
                    goalSetDay = Dates.today(),
                    proteinPerKg = Nutrition.proteinPerKgFor(s.goal, s.cutPace, s.gainPace),
                    dayActivity = s.dayActivity,
                    cutPace = s.cutPace,
                    gainPace = s.gainPace,
                    targetWeightKg = s.weightKg,
                    targetSetDay = Dates.today(),
                    // A new person starts on the new suggestion, so the one-time "more accurate target" card never shows.
                    calorieV2Seen = true,
                )
            }
        }
    }

    companion object {
        /** Name, body, plan, goal, AI key. */
        const val LAST_STEP = 4
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
                    1 -> StepBody(s, vm)
                    2 -> StepPlan(s, vm)
                    3 -> StepGoal(s, vm)
                    else -> StepKey(s, vm)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (s.step > 0) TextButton(onClick = vm::back) { Text(stringResource(R.string.action_back), color = c.textSecondary) }
            Spacer(Modifier.weight(1f))
            val canGo = when (s.step) { 0 -> s.name.isNotBlank(); 1 -> s.bodyReady; else -> true }
            // Say why Continue is greyed out instead of leaving people guessing.
            if (!canGo && s.step == 1) Text(stringResource(R.string.onb_body_needed), style = MaterialTheme.typography.bodySmall, color = c.textSecondary, modifier = Modifier.padding(end = 12.dp).weight(1f, fill = false))
            when {
                s.step < OnboardingViewModel.LAST_STEP -> PillButton(stringResource(R.string.action_continue), onClick = vm::next, enabled = canGo)
                // A typed key is checked and kept by the main button too, not thrown away by "Skip".
                s.keyOk || s.apiKey.isBlank() -> PillButton(if (s.keyOk) stringResource(R.string.onb_lets_go) else stringResource(R.string.onb_skip), onClick = vm::finish)
                else -> PillButton(stringResource(if (s.checkingKey) R.string.onb_checking else R.string.onb_save_key), onClick = { vm.checkKey(then = vm::finish) }, enabled = !s.checkingKey)
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
    Title(stringResource(R.string.onb_welcome_title), stringResource(R.string.onb_welcome_body))
    // Keyboard up on arrival: the first thing asked for is typing a name.
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    OutlinedTextField(
        value = s.name,
        onValueChange = { v -> vm.update { it.copy(name = v.take(24)) } },
        label = { Text(stringResource(R.string.onb_your_name)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
        shape = RoundedCornerShape(16.dp),
        colors = fieldColors(),
        modifier = Modifier.fillMaxWidth().focusRequester(focus),
    )
    Spacer(Modifier.height(28.dp))
    Text(stringResource(R.string.onb_body_type), style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ChoiceChip(stringResource(R.string.body_male), s.bodyType == BodyType.MALE, { vm.update { it.copy(bodyType = BodyType.MALE) } })
        ChoiceChip(stringResource(R.string.body_female), s.bodyType == BodyType.FEMALE, { vm.update { it.copy(bodyType = BodyType.FEMALE) } })
    }
}

/** Step 2: units, weight, height and age (one calm row each), then the normal day outside workouts. */
@Composable
private fun StepBody(s: OnboardingState, vm: OnboardingViewModel) {
    Title(stringResource(R.string.onb_body_title), stringResource(R.string.onb_body_body))
    Row(Modifier.padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ChoiceChip(stringResource(R.string.unit_kilograms), s.unit == WeightUnit.KG, { vm.update { it.copy(unit = WeightUnit.KG) } })
        ChoiceChip(stringResource(R.string.unit_pounds), s.unit == WeightUnit.LB, { vm.update { it.copy(unit = WeightUnit.LB) } })
    }
    GoalRow(stringResource(R.string.weight_body), s.unit.fromKg(s.weightKg), { vm.setWeight(s.unit.toKg(it)) }, step = s.unit.bodyStep, min = s.unit.fromKg(30f), max = s.unit.fromKg(250f), unit = s.unit.label, decimals = 1)
    NumberRow(stringResource(R.string.profile_height), stringResource(R.string.unit_cm), s.heightCm.toInt(), 120..230) { v -> vm.update { it.copy(heightCm = v.toFloat()) } }
    NumberRow(stringResource(R.string.profile_age), stringResource(R.string.unit_years), s.age, 10..100) { v -> vm.update { it.copy(age = v) } }
    Spacer(Modifier.height(16.dp))
    Text(stringResource(R.string.day_title), style = MaterialTheme.typography.titleSmall, color = Fit.colors.textPrimary)
    Spacer(Modifier.height(8.dp))
    DayActivity.entries.forEach { d ->
        ChoiceCard(stringResource(d.labelRes), stringResource(d.descRes), s.dayActivity == d) { vm.update { it.copy(dayActivity = d) } }
    }
}

/** A whole number typed straight into a row (height, age), Arabic digits too. Reports 0 while empty or out of range. */
@Composable
private fun NumberRow(label: String, unit: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    val c = Fit.colors
    var text by remember { mutableStateOf(if (value > 0) value.toString() else "") }
    Row(
        Modifier.fillMaxWidth().padding(bottom = 12.dp).clip(RoundedCornerShape(20.dp)).background(c.card).padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.titleSmall, color = c.textPrimary, modifier = Modifier.weight(1f))
        OutlinedTextField(
            value = text,
            onValueChange = { v ->
                text = v.filter { Character.isDigit(it) }.take(3)
                val n = text.map { Character.getNumericValue(it) }.fold(0) { acc, d -> acc * 10 + d }.takeIf { text.isNotEmpty() }
                onChange(if (n != null && n in range) n else 0)
            },
            placeholder = { Text("–", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
            singleLine = true,
            textStyle = MaterialTheme.typography.titleMedium.copy(textAlign = TextAlign.Center),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
            shape = RoundedCornerShape(14.dp),
            colors = fieldColors(),
            modifier = Modifier.width(96.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(unit, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary, modifier = Modifier.width(32.dp))
    }
}

/** Big radio card with a title and a one-line description (goal, normal day). */
@Composable
private fun ChoiceCard(name: String, desc: String, selected: Boolean, onClick: () -> Unit) {
    val c = Fit.colors
    Row(
        Modifier.fillMaxWidth().padding(bottom = 12.dp).clip(RoundedCornerShape(20.dp)).background(c.card)
            .then(if (selected) Modifier.border(2.dp, c.teal, RoundedCornerShape(20.dp)) else Modifier)
            .clickable(onClick = onClick).padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
            Spacer(Modifier.height(4.dp))
            Text(desc, style = MaterialTheme.typography.bodySmall, color = c.textSecondary, maxLines = 3)
        }
        Spacer(Modifier.width(12.dp))
        Box(Modifier.size(24.dp).clip(CircleShape).background(if (selected) c.teal else c.cardHigh), contentAlignment = Alignment.Center) {
            if (selected) Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = c.onTeal, modifier = Modifier.size(24.dp))
        }
    }
}

/** Step 4 (after the plan, so the target counts its training days): the goal, its pace and the calculated target. */
@Composable
private fun StepGoal(s: OnboardingState, vm: OnboardingViewModel) {
    val c = Fit.colors
    Title(stringResource(R.string.goal_title), stringResource(R.string.goal_step_body))
    NutritionGoal.entries.forEach { g ->
        ChoiceCard(stringResource(g.labelRes), stringResource(g.descRes), s.goal == g) { vm.update { it.copy(goal = g) } }
    }
    when (s.goal) {
        NutritionGoal.CUT -> {
            Text(stringResource(R.string.pace_title), style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
            Spacer(Modifier.height(8.dp))
            val paces = CutPace.entries
            SegmentedTabs(
                options = paces.map { stringResource(it.labelRes) },
                selected = paces.indexOf(s.cutPace),
                onSelect = { i -> vm.update { it.copy(cutPace = paces[i]) } },
                subs = paces.map { stringResource(R.string.pace_week, s.unit.format(s.weightKg * it.weeklyPct / 100f)) },
                disabled = if (s.fastBlocked) setOf(paces.indexOf(CutPace.AGGRESSIVE)) else emptySet(),
            )
            if (s.fastBlocked) Text(stringResource(R.string.pace_blocked), style = MaterialTheme.typography.bodySmall, color = c.textSecondary, modifier = Modifier.padding(top = 6.dp))
            Spacer(Modifier.height(12.dp))
        }
        NutritionGoal.GAIN -> {
            Text(stringResource(R.string.pace_title), style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
            Spacer(Modifier.height(8.dp))
            val paces = GainPace.entries
            SegmentedTabs(
                options = paces.map { stringResource(it.labelRes) },
                selected = paces.indexOf(s.gainPace),
                onSelect = { i -> vm.update { it.copy(gainPace = paces[i]) } },
                subs = paces.map { stringResource(R.string.pace_month, s.unit.format(s.weightKg * it.weeklyPct * 4.35f / 100f)) },
            )
            Spacer(Modifier.height(12.dp))
        }
        NutritionGoal.MAINTAIN -> Unit
    }
    Target(s, vm)
}

/** The calculated calories and protein with the daily burn behind them; editable after a tap on Adjust. */
@Composable
private fun Target(s: OnboardingState, vm: OnboardingViewModel) {
    val c = Fit.colors
    if (!s.bodyReady) {
        Text(stringResource(R.string.target_missing), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
        return
    }
    var editing by remember { mutableStateOf(false) }
    val nf = remember { java.text.NumberFormat.getIntegerInstance() }
    val burn = (s.burn / 10f).roundToInt() * 10
    // The difference keeps its sign next to the number in Arabic too (left-to-right isolate).
    val diff = "⁦${if (s.kcal >= burn) "+" else "−"}${nf.format(abs(s.kcal - burn))}⁩"
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.card).padding(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.target_title), style = MaterialTheme.typography.titleSmall, color = c.textPrimary, modifier = Modifier.weight(1f))
            if (!editing) TextButton(onClick = { editing = true }) { Text(stringResource(R.string.target_adjust), color = c.teal) }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${nf.format(s.kcal)} ${stringResource(R.string.unit_kcal)}", style = MaterialTheme.typography.headlineSmall, color = c.blue)
                Text(stringResource(R.string.profile_daily_calories), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${s.protein} ${stringResource(R.string.unit_g)}", style = MaterialTheme.typography.headlineSmall, color = c.teal)
                Text(stringResource(R.string.profile_daily_protein), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
            }
        }
        if (editing) {
            GoalRow(stringResource(R.string.profile_daily_calories), s.kcal.toFloat(), { v -> vm.update { it.copy(kcal = v.toInt().coerceAtLeast(s.floor), goalsTouched = true) } }, step = 50f, min = s.floor.toFloat(), max = 6000f, unit = stringResource(R.string.unit_kcal))
            GoalRow(stringResource(R.string.profile_daily_protein), s.protein.toFloat(), { v -> vm.update { it.copy(protein = v.toInt().coerceAtLeast(60), goalsTouched = true) } }, step = 5f, min = 60f, max = 400f, unit = stringResource(R.string.unit_g))
        }
        Text(
            if (s.goal == NutritionGoal.MAINTAIN) stringResource(R.string.target_burn_only, nf.format(burn))
            else stringResource(R.string.target_burn, nf.format(burn), nf.format(s.kcal), diff),
            style = MaterialTheme.typography.bodySmall, color = c.textSecondary,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            if (s.goalsTouched) stringResource(R.string.target_custom) else stringResource(R.string.target_from, s.trainingDays, stringResource(s.dayActivity.labelRes)),
            style = MaterialTheme.typography.bodySmall, color = c.textSecondary,
        )
    }
}

@Composable
private fun GoalRow(label: String, value: Float, onChange: (Float) -> Unit, step: Float, min: Float, max: Float, unit: String, decimals: Int = 0) {
    val c = Fit.colors
    Row(
        Modifier.fillMaxWidth().padding(bottom = 12.dp).clip(RoundedCornerShape(20.dp)).background(c.card).padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.titleSmall, color = c.textPrimary, modifier = Modifier.weight(1f))
        NumberStepper(value, onChange, step, min, max, unit = unit, decimals = decimals, title = label, minValueWidth = 84.dp)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StepPlan(s: OnboardingState, vm: OnboardingViewModel) {
    val c = Fit.colors
    val isArabic = Locale.getDefault().language == "ar"
    Title(stringResource(R.string.onb_plan_title), stringResource(R.string.onb_plan_body))

    Text(
        text = stringResource(R.string.onb_available_equipment),
        style = MaterialTheme.typography.titleSmall,
        color = c.textPrimary,
    )
    Spacer(Modifier.height(8.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        EquipmentPreset.entries.forEach { preset ->
            ChoiceChip(
                text = if (isArabic) preset.labelAr else preset.labelEn,
                selected = s.equipmentPreset == preset,
                onClick = { vm.update { it.copy(equipmentPreset = preset, planIndex = planFor(preset, it.planIndex)) } },
            )
        }
    }
    Spacer(Modifier.height(6.dp))
    Text(stringResource(R.string.onb_equipment_hint), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
    Spacer(Modifier.height(20.dp))

    val options = StarterPlans.all.map { it.name to it.description } + (stringResource(R.string.onb_start_empty) to stringResource(R.string.onb_start_empty_desc))
    options.forEachIndexed { index, (name, desc) ->
        val selected = s.planIndex == index
        Row(
            Modifier.fillMaxWidth().padding(bottom = 12.dp).clip(RoundedCornerShape(20.dp)).background(c.card)
                .then(if (selected) Modifier.border(2.dp, c.teal, RoundedCornerShape(20.dp)) else Modifier)
                .clickable {
                    val autoPreset = when {
                        index == StarterPlans.GYM_SPLIT_INDEX -> EquipmentPreset.FULL_GYM
                        index == StarterPlans.DUMBBELLS_INDEX && Equipment.DUMBBELLS !in s.equipmentPreset.equipment -> EquipmentPreset.DUMBBELLS_AND_BAR
                        else -> s.equipmentPreset
                    }
                    vm.update { it.copy(planIndex = index, equipmentPreset = autoPreset) }
                }
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

/** The starter plan that fits [preset]: weights when there are dumbbells, bodyweight when there aren't. */
private fun planFor(preset: EquipmentPreset, current: Int): Int {
    val hasDumbbells = Equipment.DUMBBELLS in preset.equipment
    return when {
        hasDumbbells && current == StarterPlans.GYM_SPLIT_INDEX && preset == EquipmentPreset.FULL_GYM -> current
        hasDumbbells && current in listOf(StarterPlans.BODYWEIGHT_INDEX, StarterPlans.DUMBBELLS_INDEX) -> StarterPlans.DUMBBELLS_INDEX
        hasDumbbells && current == 0 && preset in listOf(EquipmentPreset.FULL_GYM, EquipmentPreset.HOME_GYM) -> StarterPlans.DUMBBELLS_INDEX
        !hasDumbbells && current in listOf(StarterPlans.DUMBBELLS_INDEX, StarterPlans.GYM_SPLIT_INDEX) -> StarterPlans.BODYWEIGHT_INDEX
        else -> current
    }
}

@Composable
private fun StepKey(s: OnboardingState, vm: OnboardingViewModel) {
    val c = Fit.colors
    val context = LocalContext.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconCircle(Icons.Rounded.AutoAwesome, c.blue, c.blueTrack)
        Spacer(Modifier.width(12.dp))
        Text(stringResource(R.string.onb_optional), style = MaterialTheme.typography.labelLarge, color = c.textSecondary)
    }
    Spacer(Modifier.height(16.dp))
    Title(
        stringResource(R.string.onb_ai_title),
        stringResource(R.string.onb_ai_body),
    )
    listOf(
        stringResource(R.string.onb_ai_step1),
        stringResource(R.string.onb_ai_step2),
        stringResource(R.string.onb_ai_step3),
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
        stringResource(R.string.onb_get_key),
        onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/apikey"))) },
        icon = Icons.Rounded.OpenInNew,
        container = c.blueTrack,
        content = c.blue,
    )
    Spacer(Modifier.height(20.dp))
    OutlinedTextField(
        value = s.apiKey,
        onValueChange = { v -> vm.update { it.copy(apiKey = v.trim(), keyOk = false, keyMessage = null) } },
        label = { Text(stringResource(R.string.onb_key_label)) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        shape = RoundedCornerShape(16.dp),
        colors = fieldColors(),
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(12.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        PillButton(
            if (s.checkingKey) stringResource(R.string.onb_checking) else stringResource(R.string.onb_check_key),
            onClick = vm::checkKey,
            enabled = s.apiKey.isNotBlank() && !s.checkingKey,
        )
        Spacer(Modifier.width(12.dp))
        s.keyMessage?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = if (s.keyOk) c.teal else c.coral, modifier = Modifier.weight(1f))
        }
    }
    Spacer(Modifier.height(12.dp))
    Text(
        stringResource(R.string.onb_privacy),
        style = MaterialTheme.typography.bodySmall, color = c.textSecondary,
    )
    Spacer(Modifier.height(8.dp))
    Text(stringResource(R.string.onb_groq_hint), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
}