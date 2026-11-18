package com.forgequest.app.ui.onboarding

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.forgequest.app.data.plan.PlanAnswers
import com.forgequest.app.ui.components.reflowText
import com.forgequest.app.ui.theme.ForgeMotion
import com.forgequest.app.ui.theme.ForgeSpace
import com.forgequest.app.ui.theme.ForgeShape
import com.forgequest.app.ui.theme.LocalReducedMotion
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Restaurant
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.forgequest.app.AppContainer
import com.forgequest.app.R
import com.forgequest.app.data.plan.PlanDraft
import com.forgequest.app.data.plan.PlanDraftDay
import com.forgequest.app.data.prefs.BodyType
import com.forgequest.app.domain.EquipmentPreset
import com.forgequest.app.domain.Dates
import com.forgequest.app.domain.Nutrition
import com.forgequest.app.domain.NutritionGoal
import com.forgequest.app.domain.Experience
import com.forgequest.app.domain.PlanBuilder
import com.forgequest.app.domain.PlanInput
import com.forgequest.app.domain.TrainGoal
import com.forgequest.app.ui.plan.localizeBuiltPlan
import kotlinx.coroutines.flow.first
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

/** Main goal choices in 4.2 onboarding, mapped to nutrition goal, just-log mode, and plan builder goal. */
enum class OnboardingGoalOption(
    val nutritionGoal: NutritionGoal,
    val justLog: Boolean,
    val trainGoal: TrainGoal,
) {
    MUSCLE(NutritionGoal.GAIN, justLog = false, TrainGoal.MUSCLE),
    FAT(NutritionGoal.CUT, justLog = false, TrainGoal.FAT_LOSS),
    FIT(NutritionGoal.MAINTAIN, justLog = false, TrainGoal.GENERAL),
    JUST_LOG(NutritionGoal.MAINTAIN, justLog = true, TrainGoal.GENERAL),
    ;

    companion object {
        fun from(goal: NutritionGoal, justLog: Boolean): OnboardingGoalOption = when {
            justLog -> JUST_LOG
            goal == NutritionGoal.GAIN -> MUSCLE
            goal == NutritionGoal.CUT -> FAT
            else -> FIT
        }
    }
}

data class OnboardingState(
    val step: Int = 0,
    val name: String = "",
    val bodyType: BodyType = BodyType.MALE,
    val weightKg: Float = 75f,
    /** False until the person edits or confirms a weight in Step 5. */
    val weightTouched: Boolean = false,
    val unit: WeightUnit = WeightUnit.KG,
    /** 0 until typed: height and age are needed for the food targets (set up from Home after the first workout). */
    val heightCm: Float = 0f,
    val age: Int = 0,
    val dayActivity: DayActivity = DayActivity.SITTING,
    val kcal: Int = 2500,
    val protein: Int = 135,
    val goalsTouched: Boolean = false,
    val goal: NutritionGoal = NutritionGoal.MAINTAIN,
    val goalOption: OnboardingGoalOption = OnboardingGoalOption.FIT,
    val cutPace: CutPace = CutPace.STANDARD,
    val gainPace: GainPace = GainPace.STANDARD,
    /** Days a week the person can train: becomes the weekly streak target. */
    val daysPerWeek: Int = 3,
    /** "Just log my training" picked on the goal step: no plan, streaks, levels, badges or readiness. */
    val justLog: Boolean = false,
    /** "Built for me" card picked (the default): the plan comes from PlanBuilder instead of starting empty. */
    val builtForMe: Boolean = true,
    val equipmentPreset: EquipmentPreset = EquipmentPreset.CALISTHENICS_PARK,
    val experience: Experience = Experience.SOME,
) {
    val male get() = bodyType == BodyType.MALE
    val bodyReady get() = heightCm in 120f..230f && age in 10..100
    /** Training days for the calorie suggestion: the weekly target. */
    val trainingDays get() = daysPerWeek
    val floor get() = Nutrition.calorieFloor(weightKg, heightCm, age, male)
    val burn get() = Nutrition.dailyBurn(weightKg, heightCm, age, male, trainingDays, base = dayActivity.base)
    val fastBlocked get() = Nutrition.allowedCutPace(CutPace.AGGRESSIVE, weightKg, heightCm, age, male) != CutPace.AGGRESSIVE

    fun selectGoal(option: OnboardingGoalOption): OnboardingState = copy(
        goalOption = option,
        goal = option.nutritionGoal,
        justLog = option.justLog,
    )
}

/** Tour-only first run (no setup screens): no starting plan, 3 days a week target, everything editable later in Profile. */
suspend fun quickStart(app: AppContainer) {
    app.workouts.seedLibrary()
    TourPrefs.setGsEligible(app.appContext)
    app.settings.update { it.copy(onboarded = true, weeklyTargetOverride = 3, calorieV2Seen = true) }
}

class OnboardingViewModel(private val app: AppContainer) : ViewModel() {
    val state = MutableStateFlow(OnboardingState())
    private var completing = false

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

    fun selectGoal(option: OnboardingGoalOption) = update { it.selectGoal(option) }

    fun setWeight(kg: Float) = update { it.copy(weightKg = kg.coerceIn(30f, 250f), weightTouched = true) }

    fun next() = state.update { it.copy(step = nextStep(it.step, it.justLog)) }
    fun back() = state.update { it.copy(step = prevStep(it.step, it.justLog)) }

    private val emptyPlan by lazy {
        PlanDraft(
            app.str(R.string.onb_my_plan),
            app.str(R.string.onb_my_plan_desc),
            (1..7).map { PlanDraftDay(it, app.str(R.string.today_rest_day), isRest = true) },
        )
    }

    fun skipSetup() {
        if (completing) return
        completing = true
        viewModelScope.launch { quickStart(app) }
    }

    fun finish(logWeight: Boolean = state.value.weightTouched) {
        if (completing) return
        completing = true
        val s = state.value
        val trainGoal = s.goalOption.trainGoal
        val nutritionGoal = s.goalOption.nutritionGoal
        val justLog = s.goalOption.justLog
        val builtDays = PlanBuilder.spreadDays(s.daysPerWeek)
        viewModelScope.launch {
            app.workouts.seedLibrary()
            if (!justLog && s.builtForMe) {
                val library = app.workouts.library.first()
                val input = PlanInput(
                    goal = trainGoal, days = builtDays, minutes = 45,
                    equipment = s.equipmentPreset.equipment, experience = s.experience,
                )
                val built = PlanBuilder.build(input, library)
                app.workouts.applyPlan(localizeBuiltPlan(app, built, trainGoal), "builder")
            } else {
                app.workouts.applyPlan(emptyPlan, "manual")
            }
            if (logWeight && s.weightTouched) {
                app.progress.logWeight(s.weightKg)
            }
            TourPrefs.setGsEligible(app.appContext)
            app.settings.update {
                it.copy(
                    onboarded = true,
                    name = s.name.trim(),
                    bodyType = s.bodyType,
                    unit = s.unit.code,
                    nutritionGoal = nutritionGoal,
                    goalSetDay = Dates.today(),
                    proteinPerKg = Nutrition.proteinPerKgFor(nutritionGoal, it.cutPace, it.gainPace),
                    equipment = s.equipmentPreset.encode(),
                    weeklyTargetOverride = s.daysPerWeek,
                    justLog = justLog,
                    // A new person starts on the new suggestion, so the one-time "more accurate target" card never shows.
                    calorieV2Seen = true,
                    planBuilderAnswers = if (!justLog && s.builtForMe) {
                        PlanAnswers(
                            goal = trainGoal,
                            days = builtDays,
                            equipment = s.equipmentPreset.equipment,
                            experience = s.experience,
                        ).encode()
                    } else it.planBuilderAnswers,
                )
            }
        }
    }

    /** Opens the food-targets setup from Home with what is already known. */
    fun startFoodSetup(settings: com.forgequest.app.data.prefs.Settings, latestWeight: Float?) = update {
        OnboardingState(
            bodyType = settings.bodyType,
            unit = settings.weightUnit,
            weightKg = latestWeight ?: 75f,
            weightTouched = latestWeight != null,
            daysPerWeek = if (settings.weeklyTargetOverride > 0) settings.weeklyTargetOverride else 3,
            goal = settings.nutritionGoal,
            goalOption = OnboardingGoalOption.from(settings.nutritionGoal, settings.justLog),
            justLog = settings.justLog,
        )
    }

    /** What onboarding used to write for body and goal. */
    fun saveFoodTargets(onDone: () -> Unit) {
        val s = state.value
        viewModelScope.launch {
            app.progress.logWeight(s.weightKg)
            app.settings.update {
                it.copy(
                    bodyType = s.bodyType,
                    kcalGoal = s.kcal.coerceAtLeast(s.floor),
                    proteinGoal = s.protein.coerceAtLeast(60),
                    unit = s.unit.code,
                    heightCm = s.heightCm,
                    birthYear = if (s.age > 0) java.time.Year.now().value - s.age else 0,
                    nutritionGoal = s.goal,
                    goalSetDay = Dates.today(),
                    proteinPerKg = Nutrition.proteinPerKgFor(s.goal, s.cutPace, s.gainPace),
                    dayActivity = s.dayActivity,
                    cutPace = s.cutPace,
                    gainPace = s.gainPace,
                    targetWeightKg = s.weightKg,
                    targetSetDay = Dates.today(),
                    calorieV2Seen = true,
                )
            }
            onDone()
        }
    }

    companion object {
        const val TOTAL_STEPS = 5
        const val LAST_STEP = TOTAL_STEPS - 1
        private const val TRAINING_STEP = 3

        /** "Just log my training" skips the Training step (days, equipment, plan), as on web. */
        fun nextStep(step: Int, justLog: Boolean): Int =
            if (justLog && step == TRAINING_STEP - 1) TRAINING_STEP + 1 else (step + 1).coerceAtMost(LAST_STEP)

        fun prevStep(step: Int, justLog: Boolean): Int =
            if (justLog && step == TRAINING_STEP + 1) TRAINING_STEP - 1 else (step - 1).coerceAtLeast(0)

        /** 1-based position and total for the "Step x of y" line. */
        fun progress(step: Int, justLog: Boolean): Pair<Int, Int> =
            if (justLog) (if (step > TRAINING_STEP) step else step + 1) to TOTAL_STEPS - 1 else step + 1 to TOTAL_STEPS
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnboardingScreen(onImportCsv: (() -> Unit)? = null) {
    val vm = appViewModel { OnboardingViewModel(it) }
    val s by vm.state.collectAsState()
    val c = Fit.colors
    val reduced = LocalReducedMotion.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    BackHandler(enabled = s.step > 0) { vm.back() }

    Column(
        Modifier.fillMaxSize().background(c.background).statusBarsPadding().navigationBarsPadding().imePadding().padding(horizontal = ForgeSpace.Lg),
    ) {
        Spacer(Modifier.height(ForgeSpace.Md))
        val (stepAt, stepsTotal) = OnboardingViewModel.progress(s.step, s.justLog)
        ProgressLine(stepAt / stepsTotal.toFloat(), c.teal, c.cardHigh, height = 4.dp)
        Spacer(Modifier.height(ForgeSpace.Xs))
        Text(
            stringResource(R.string.v42_onb_step, stepAt, stepsTotal),
            style = MaterialTheme.typography.labelSmall,
            color = c.textSecondary,
        )
        Spacer(Modifier.height(ForgeSpace.Md))
        AnimatedContent(
            targetState = s.step,
            transitionSpec = {
                if (reduced) {
                    fadeIn(ForgeMotion.timed(true, 0, ForgeMotion.Easing.Standard)) togetherWith
                        fadeOut(ForgeMotion.timed(true, 0, ForgeMotion.Easing.Standard))
                } else {
                    val base = if (targetState > initialState) 1 else -1
                    val dir = if (rtl) -base else base
                    (slideInHorizontally { it / 4 * dir } + fadeIn()) togetherWith (slideOutHorizontally { -it / 4 * dir } + fadeOut())
                }
            },
            modifier = Modifier.weight(1f),
            label = "step",
        ) { step ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                when (step) {
                    0 -> StepWelcome(onImportCsv)
                    1 -> StepAbout(s, vm)
                    2 -> StepGoalChoice(s, vm)
                    3 -> StepTraining(s, vm)
                    else -> StepWeight(s, vm)
                }
            }
        }
        val reflow = reflowText()
        FlowRow(
            Modifier.fillMaxWidth().padding(vertical = ForgeSpace.Md),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (s.step) {
                0 -> {
                    TextButton(onClick = vm::skipSetup, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.v42_onb_skip_setup), color = c.textSecondary)
                    }
                    if (!reflow) Spacer(Modifier.weight(1f))
                    PillButton(stringResource(R.string.v42_onb_get_started), onClick = vm::next)
                }
                OnboardingViewModel.LAST_STEP -> {
                    TextButton(onClick = vm::back, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.action_back), color = c.textSecondary)
                    }
                    if (!reflow) Spacer(Modifier.weight(1f))
                    TextButton(onClick = { vm.finish(logWeight = false) }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.v42_onb_skip_step), color = c.textSecondary)
                    }
                    PillButton(stringResource(R.string.onb_finish), onClick = { vm.finish(logWeight = s.weightTouched) })
                }
                else -> {
                    TextButton(onClick = vm::back, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.action_back), color = c.textSecondary)
                    }
                    if (!reflow) Spacer(Modifier.weight(1f))
                    PillButton(stringResource(R.string.action_continue), onClick = vm::next)
                }
            }
        }
    }
}

@Composable
private fun Title(title: String, body: String) {
    val c = Fit.colors
    Text(title, style = MaterialTheme.typography.headlineMedium, color = c.textPrimary, modifier = Modifier.semantics { heading() })
    Spacer(Modifier.height(8.dp))
    Text(body, style = MaterialTheme.typography.bodyLarge, color = c.textSecondary)
    Spacer(Modifier.height(24.dp))
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
private fun StepWelcome(onImportCsv: (() -> Unit)? = null) {
    val c = Fit.colors
    Title(stringResource(R.string.v42_onb_welcome_title), stringResource(R.string.v42_onb_welcome_body))
    val values = listOf(
        Icons.Rounded.FitnessCenter to R.string.v42_onb_value_train,
        Icons.Rounded.Restaurant to R.string.v42_onb_value_food,
        Icons.AutoMirrored.Rounded.ShowChart to R.string.v42_onb_value_progress,
    )
    values.forEach { (icon, textRes) ->
        Row(
            Modifier.fillMaxWidth().padding(bottom = ForgeSpace.Sm).clip(ForgeShape.Md).background(c.card).padding(ForgeSpace.Md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconCircle(icon, c.teal, c.tealTrack)
            Spacer(Modifier.width(ForgeSpace.Sm))
            Text(stringResource(textRes), style = MaterialTheme.typography.bodyLarge, color = c.textPrimary, modifier = Modifier.weight(1f))
        }
    }
    if (onImportCsv != null) {
        Spacer(Modifier.height(ForgeSpace.Md))
        Text(stringResource(R.string.prog41_onb_import_csv), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
        TextButton(onClick = onImportCsv, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.prog41_csv_import_title), color = c.teal)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StepAbout(s: OnboardingState, vm: OnboardingViewModel) {
    val c = Fit.colors
    Title(stringResource(R.string.v42_onb_about_title), stringResource(R.string.v42_onb_about_body))
    OutlinedTextField(
        value = s.name,
        onValueChange = { v -> vm.update { it.copy(name = v.take(24)) } },
        label = { Text(stringResource(R.string.v42_onb_name_optional)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
        shape = ForgeShape.Md,
        colors = fieldColors(),
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(ForgeSpace.Lg))
    Text(stringResource(R.string.onb_body_type), style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
    Spacer(Modifier.height(ForgeSpace.Xs))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ChoiceChip(stringResource(R.string.body_male), s.bodyType == BodyType.MALE, { vm.update { it.copy(bodyType = BodyType.MALE) } })
        ChoiceChip(stringResource(R.string.body_female), s.bodyType == BodyType.FEMALE, { vm.update { it.copy(bodyType = BodyType.FEMALE) } })
    }
    Spacer(Modifier.height(ForgeSpace.Lg))
    Text(stringResource(R.string.v42_onb_units), style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
    Spacer(Modifier.height(ForgeSpace.Xs))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ChoiceChip(stringResource(R.string.unit_kilograms), s.unit == WeightUnit.KG, { vm.update { it.copy(unit = WeightUnit.KG) } })
        ChoiceChip(stringResource(R.string.unit_pounds), s.unit == WeightUnit.LB, { vm.update { it.copy(unit = WeightUnit.LB) } })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StepGoalChoice(s: OnboardingState, vm: OnboardingViewModel) {
    val c = Fit.colors
    Title(stringResource(R.string.v42_onb_goal_title), stringResource(R.string.v42_onb_goal_body))
    val options = listOf(
        Triple(OnboardingGoalOption.MUSCLE, R.string.v42_onb_goal_muscle, R.string.v42_onb_goal_muscle_desc),
        Triple(OnboardingGoalOption.FAT, R.string.v42_onb_goal_fat, R.string.v42_onb_goal_fat_desc),
        Triple(OnboardingGoalOption.FIT, R.string.v42_onb_goal_fit, R.string.v42_onb_goal_fit_desc),
        Triple(OnboardingGoalOption.JUST_LOG, R.string.v42_onb_goal_log, R.string.v42_onb_goal_log_desc),
    )
    options.forEach { (opt, titleRes, descRes) ->
        ChoiceCard(stringResource(titleRes), stringResource(descRes), s.goalOption == opt) {
            vm.selectGoal(opt)
        }
    }
    Spacer(Modifier.height(ForgeSpace.Sm))
    Text(stringResource(R.string.v42_onb_experience), style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
    Spacer(Modifier.height(ForgeSpace.Xs))
    val expOptions = listOf(
        Experience.NEW to R.string.v42_onb_exp_new,
        Experience.SOME to R.string.v42_onb_exp_some,
        Experience.SEASONED to R.string.v42_onb_exp_pro,
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        expOptions.forEach { (exp, labelRes) ->
            ChoiceChip(stringResource(labelRes), s.experience == exp, { vm.update { it.copy(experience = exp) } })
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StepTraining(s: OnboardingState, vm: OnboardingViewModel) {
    val c = Fit.colors
    val isArabic = Locale.getDefault().language == "ar"
    Title(stringResource(R.string.v42_onb_train_title), stringResource(R.string.v42_onb_train_body))

    Row(verticalAlignment = Alignment.CenterVertically) {
        IconCircle(Icons.Rounded.LocalFireDepartment, c.gold, c.goldTrack)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.v42_onb_days_week), style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
            Text(stringResource(R.string.onb_days_hint), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
        }
    }
    Spacer(Modifier.height(12.dp))
    val dayOptions = listOf(2, 3, 4, 5, 6)
    SegmentedTabs(
        options = dayOptions.map { java.text.NumberFormat.getIntegerInstance().format(it) },
        selected = dayOptions.indexOf(s.daysPerWeek).coerceAtLeast(0),
        onSelect = { i -> vm.update { it.copy(daysPerWeek = dayOptions[i]) } },
    )
    Spacer(Modifier.height(20.dp))

    Text(stringResource(R.string.v42_onb_where_train), style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
    Spacer(Modifier.height(8.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        EquipmentPreset.entries.forEach { preset ->
            ChoiceChip(
                text = if (isArabic) preset.labelAr else preset.labelEn,
                selected = s.equipmentPreset == preset,
                onClick = { vm.update { it.copy(equipmentPreset = preset) } },
            )
        }
    }
    Spacer(Modifier.height(8.dp))
    Text(stringResource(R.string.onb_equipment_hint), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)

    if (!s.justLog) {
        Spacer(Modifier.height(20.dp))
        ChoiceCard(
            stringResource(R.string.onb_built_title),
            stringResource(R.string.onb_built_body, s.daysPerWeek),
            s.builtForMe,
        ) { vm.update { it.copy(builtForMe = true) } }
        ChoiceCard(
            stringResource(R.string.onb_start_empty),
            stringResource(R.string.onb_start_empty_desc),
            !s.builtForMe,
        ) { vm.update { it.copy(builtForMe = false) } }
    }
}

@Composable
private fun StepWeight(s: OnboardingState, vm: OnboardingViewModel) {
    Title(stringResource(R.string.v42_onb_weight_title), stringResource(R.string.v42_onb_weight_body))
    GoalRow(
        label = stringResource(R.string.weight_body),
        value = s.unit.fromKg(s.weightKg),
        onChange = { vm.setWeight(s.unit.toKg(it)) },
        step = s.unit.bodyStep,
        min = s.unit.fromKg(30f),
        max = s.unit.fromKg(250f),
        unit = s.unit.label,
        decimals = 1,
        placeholder = if (s.weightTouched) null else "– ${s.unit.label}",
    )
}

/** Step 2: units, weight, height and age (one calm row each), then the normal day outside workouts. */
@Composable
internal fun StepBody(s: OnboardingState, vm: OnboardingViewModel) {
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
        Modifier.fillMaxWidth().padding(bottom = 12.dp).clip(ForgeShape.Md).background(c.card).padding(horizontal = ForgeSpace.Gutter, vertical = 10.dp),
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
            shape = ForgeShape.Sm,
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
        Modifier.fillMaxWidth().padding(bottom = 12.dp).clip(ForgeShape.Md).background(c.card)
            .then(if (selected) Modifier.border(2.dp, c.teal, ForgeShape.Md) else Modifier)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick).padding(16.dp),
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
internal fun StepGoal(s: OnboardingState, vm: OnboardingViewModel) {
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
            if (s.fastBlocked) Text(stringResource(R.string.pace_blocked), style = MaterialTheme.typography.bodySmall, color = c.textSecondary, modifier = Modifier.padding(top = 8.dp))
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
    Column(Modifier.fillMaxWidth().clip(ForgeShape.Md).background(c.card).padding(16.dp)) {
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
private fun GoalRow(label: String, value: Float, onChange: (Float) -> Unit, step: Float, min: Float, max: Float, unit: String, decimals: Int = 0, placeholder: String? = null) {
    val c = Fit.colors
    Row(
        Modifier.fillMaxWidth().padding(bottom = 12.dp).clip(ForgeShape.Md).background(c.card).padding(horizontal = ForgeSpace.Gutter, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.titleSmall, color = c.textPrimary, modifier = Modifier.weight(1f))
        NumberStepper(value, onChange, step, min, max, unit = unit, decimals = decimals, title = label, minValueWidth = 84.dp, placeholder = placeholder)
    }
}

/** The body and food-goal steps that used to sit in onboarding, offered from Home after the first workout. */
@Composable
fun FoodTargetsSetup(settings: com.forgequest.app.data.prefs.Settings, latestWeight: Float?, onClose: () -> Unit) {
    val vm = appViewModel { OnboardingViewModel(it) }
    LaunchedEffect(Unit) { vm.startFoodSetup(settings, latestWeight) }
    val s by vm.state.collectAsState()
    val c = Fit.colors
    val step = if (s.step == 0) 0 else 1
    BackHandler { if (step == 0) onClose() else vm.back() }
    Column(
        Modifier.fillMaxSize().background(c.background).pointerInput(Unit) {}.statusBarsPadding().navigationBarsPadding().imePadding().padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(16.dp))
        ProgressLine((step + 1) / 2f, c.teal, c.cardHigh, height = 4.dp)
        Spacer(Modifier.height(24.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            if (step == 0) StepBody(s, vm) else StepGoal(s, vm)
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { if (step == 0) onClose() else vm.back() }) {
                Text(stringResource(if (step == 0) R.string.action_cancel else R.string.action_back), color = c.textSecondary)
            }
            Spacer(Modifier.weight(1f))
            if (step == 0) PillButton(stringResource(R.string.action_continue), onClick = vm::next, enabled = s.bodyReady)
            else PillButton(stringResource(R.string.action_save), onClick = { vm.saveFoodTargets(onClose) })
        }
    }
}
