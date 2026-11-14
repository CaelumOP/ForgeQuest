package com.forgequest.app.ui.profile

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Accessibility
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.TrackChanges
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.forgequest.app.R
import com.forgequest.app.container
import com.forgequest.app.data.ProgressSnapshot
import com.forgequest.app.data.health.HealthState
import com.forgequest.app.data.prefs.BodyType
import com.forgequest.app.data.prefs.Settings
import com.forgequest.app.domain.CutPace
import com.forgequest.app.domain.Dates
import com.forgequest.app.domain.DayActivity
import com.forgequest.app.domain.GainPace
import com.forgequest.app.domain.Nutrition
import com.forgequest.app.domain.NutritionGoal
import com.forgequest.app.domain.WeightTrend
import com.forgequest.app.ui.CalorieUi
import com.forgequest.app.ui.components.ChoiceChip
import com.forgequest.app.ui.components.FitCard
import com.forgequest.app.ui.components.FitSwitch
import com.forgequest.app.ui.components.IconCircle
import com.forgequest.app.ui.components.SegmentedTabs
import com.forgequest.app.ui.components.TonalPill
import com.forgequest.app.ui.components.toast
import com.forgequest.app.ui.descRes
import com.forgequest.app.ui.labelRes
import com.forgequest.app.ui.theme.ForgeShape
import com.forgequest.app.ui.theme.Fit
import kotlin.math.roundToInt

/** Card title with its icon, the same on every card of this page. */
@Composable
private fun CardTitle(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, hint: String? = null) {
    val c = Fit.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconCircle(icon, c.teal, c.tealTrack)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = c.textPrimary, modifier = Modifier.semantics { heading() })
            if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
        }
    }
}

/** "Suggested … " with a Use pill, under a target. */
@Composable
private fun SuggestionLine(text: String, showUse: Boolean, onUse: () -> Unit) {
    val c = Fit.colors
    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.bodySmall, color = c.textSecondary, modifier = Modifier.weight(1f))
        if (showUse) {
            Spacer(Modifier.width(8.dp))
            TonalPill(stringResource(R.string.goals_use), onClick = onUse)
        }
    }
}

/**
 * "You and your goals": who you are (details), what you want (goal and pace), and the daily numbers that follow
 * (one calorie and one protein target, each with its suggestion). Body fat sits under Advanced.
 */
@Composable
internal fun YouSettingsGroup(vm: SettingsViewModel, s: ProgressSnapshot, toggleGroup: (String) -> Unit) {
    val c = Fit.colors
    val settings = s.settings
    val health by vm.app.health.state.collectAsState()
    val thisYear = java.time.Year.now().value
    val age = Nutrition.ageFromBirthYear(settings.birthYear, thisYear)
    val steps = CalorieUi.stepsBase(health, s.sessions)
    val planDays = s.plan?.trainingDays?.size ?: 0
    val calInputs = CalorieUi.inputs(settings, s.latestWeight, planDays, steps, thisYear)
    val kg = s.latestWeight
    var editing by remember { mutableStateOf<Target?>(null) }

    SettingsGroup(
        Icons.Rounded.TrackChanges, c.teal, c.tealTrack, stringResource(R.string.settings_group_you),
        summary(
            if (settings.heightCm <= 0f || age <= 0) stringResource(R.string.summary_body_not_set) else null,
            stringResource(settings.nutritionGoal.labelRes),
            "%,d %s".format(settings.kcalGoal, stringResource(R.string.unit_kcal)),
            stringResource(R.string.summary_protein, settings.proteinGoal),
        ),
        onToggle = { toggleGroup("you") },
    ) {
        // 1. Your details.
        FitCard {
            CardTitle(Icons.Rounded.Accessibility, stringResource(R.string.profile_about_you), stringResource(R.string.profile_about_you_hint))
            Spacer(Modifier.height(16.dp))
            // Local state owns the text while typing (no key: a saved value landing must not overwrite newer keystrokes).
            var nameText by rememberSaveable { mutableStateOf(settings.name) }
            OutlinedTextField(
                value = nameText,
                onValueChange = { v -> nameText = v.take(24); vm.setName(nameText.trim()) },
                label = { Text(stringResource(R.string.onb_your_name)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                shape = ForgeShape.Md,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = c.teal, unfocusedBorderColor = c.divider, focusedTextColor = c.textPrimary, unfocusedTextColor = c.textPrimary,
                    cursorColor = c.teal, focusedLabelColor = c.teal, unfocusedLabelColor = c.textSecondary,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            SegmentedTabs(
                listOf(stringResource(R.string.body_male), stringResource(R.string.body_female)),
                if (settings.bodyType == BodyType.FEMALE) 1 else 0,
                onSelect = { vm.setBody(if (it == 1) BodyType.FEMALE else BodyType.MALE) },
            )
            Spacer(Modifier.height(8.dp))
            // Not set yet: a dash, not a made-up 170 cm / 25 years that looks like a real value.
            SettingStepper(
                stringResource(R.string.profile_age), if (age > 0) age.toFloat() else 25f, { vm.setBodyMeasures(birthYear = thisYear - it.toInt()) },
                step = 1f, min = 10f, max = 100f, unit = stringResource(R.string.unit_years),
                placeholder = if (age > 0) null else "– ${stringResource(R.string.unit_years)}",
            )
            SettingStepper(
                stringResource(R.string.profile_height), if (settings.heightCm > 0f) settings.heightCm else 170f, { vm.setBodyMeasures(heightCm = it) },
                step = 1f, min = 120f, max = 230f, unit = stringResource(R.string.unit_cm),
                placeholder = if (settings.heightCm > 0f) null else "– ${stringResource(R.string.unit_cm)}",
            )
            // Weight comes from the weight log, so it is shown here, not edited.
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.you_current_weight), style = MaterialTheme.typography.bodyLarge, color = c.textPrimary)
                    Text(stringResource(R.string.you_weight_from_log), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                }
                Text(kg?.let { settings.weightUnit.format(it) } ?: "–", style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
            }
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.day_short_title), style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
            Spacer(Modifier.height(8.dp))
            SegmentedTabs(
                DayActivity.entries.map { stringResource(it.labelRes) },
                DayActivity.entries.indexOf(settings.dayActivity),
                onSelect = { i -> vm.setNutrition { it.copy(dayActivity = DayActivity.entries[i]) } },
                wrap = true,
            )
            Spacer(Modifier.height(8.dp))
            // With a watch, the steps decide; the choice above is the fallback for days it isn't worn.
            Text(
                steps?.let { stringResource(R.string.steps_using, "%,d".format(it.avgSteps)) } ?: stringResource(settings.dayActivity.descRes),
                style = MaterialTheme.typography.bodySmall, color = c.textSecondary,
            )
            if (settings.heightCm <= 0f || age <= 0) {
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.profile_body_not_set), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
            }
        }

        // 2. Your goal and pace.
        FitCard {
            CardTitle(Icons.Rounded.Flag, stringResource(R.string.goal_title))
            Row(Modifier.padding(top = 12.dp, bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NutritionGoal.entries.forEach { g ->
                    ChoiceChip(stringResource(g.labelRes), settings.nutritionGoal == g, {
                        if (g != settings.nutritionGoal) vm.setNutrition { it.copy(nutritionGoal = g) }
                    })
                }
            }
            when (settings.nutritionGoal) {
                NutritionGoal.CUT -> {
                    val paces = CutPace.entries
                    val fastBlocked = kg != null &&
                        Nutrition.allowedCutPace(CutPace.AGGRESSIVE, kg, settings.heightCm, age, settings.bodyType == BodyType.MALE, settings.bodyFatPct) != CutPace.AGGRESSIVE
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.pace_title), style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
                    Spacer(Modifier.height(8.dp))
                    SegmentedTabs(
                        options = paces.map { stringResource(it.labelRes) },
                        selected = paces.indexOf(settings.cutPace),
                        onSelect = { i -> vm.setNutrition { it.copy(cutPace = paces[i]) } },
                        subs = if (kg == null) emptyList() else paces.map { stringResource(R.string.pace_week, settings.weightUnit.format(kg * it.weeklyPct / 100f)) },
                        disabled = if (fastBlocked) setOf(paces.indexOf(CutPace.AGGRESSIVE)) else emptySet(),
                        wrap = true,
                    )
                    if (fastBlocked) Text(stringResource(R.string.pace_blocked), style = MaterialTheme.typography.bodySmall, color = c.textSecondary, modifier = Modifier.padding(top = 8.dp))
                }
                NutritionGoal.GAIN -> {
                    val paces = GainPace.entries
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.pace_title), style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
                    Spacer(Modifier.height(8.dp))
                    SegmentedTabs(
                        options = paces.map { stringResource(it.labelRes) },
                        selected = paces.indexOf(settings.gainPace),
                        onSelect = { i -> vm.setNutrition { it.copy(gainPace = paces[i]) } },
                        subs = if (kg == null) emptyList() else paces.map { stringResource(R.string.pace_month, settings.weightUnit.format(kg * it.weeklyPct * 4.35f / 100f)) },
                        wrap = true,
                    )
                }
                NutritionGoal.MAINTAIN -> Unit
            }
            val trend = Nutrition.weightTrendNote(
                settings.nutritionGoal, settings.goalSetDay, s.weights.map { it.epochDay to it.kg }, Dates.today(),
                settings.cutPace, settings.gainPace, settings.bodyType == BodyType.FEMALE,
            )
            if (trend != null) {
                Text(
                    stringResource(
                        when (trend) {
                            WeightTrend.CUT_TOO_FAST -> R.string.goal_trend_cut_fast
                            WeightTrend.GAIN_STALLED -> R.string.goal_trend_gain_stalled
                            WeightTrend.GAIN_TOO_FAST -> R.string.goal_trend_gain_fast
                            WeightTrend.DIET_BREAK_DUE -> R.string.goal_trend_diet_break
                        },
                    ),
                    style = MaterialTheme.typography.bodySmall, color = c.gold, modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        // 3. Daily targets: one number each, with the suggestion and where it comes from.
        FitCard {
            CardTitle(Icons.Rounded.TrackChanges, stringResource(R.string.profile_daily_targets), stringResource(R.string.you_targets_hint))
            Spacer(Modifier.height(8.dp))
            val trainingDaysForKcal = CalorieUi.trainingDays(settings, planDays)
            val hasBodyStats = CalorieUi.hasBodyStats(settings, age)
            fun kcalFor(goal: NutritionGoal) = calInputs?.let { CalorieUi.suggestion(it, settings, goal) }
            val floor = calInputs?.let(CalorieUi::floor) ?: Nutrition.MIN_SAFE_KCAL
            SettingStepper(
                stringResource(R.string.profile_daily_calories), settings.kcalGoal.toFloat(), { vm.setKcal(it.toInt()) },
                step = 50f, min = floor.toFloat(), max = Nutrition.MAX_SAFE_KCAL.toFloat(), unit = stringResource(R.string.unit_kcal),
            )
            val suggested = kcalFor(settings.nutritionGoal)
            SuggestionLine(
                if (hasBodyStats && suggested != null) stringResource(R.string.profile_suggested_v2, "%,d".format(suggested), trainingDaysForKcal, stringResource(settings.dayActivity.labelRes))
                else stringResource(R.string.goals_suggest_missing),
                showUse = suggested != null && hasBodyStats && suggested != settings.kcalGoal,
                onUse = { suggested?.let(vm::setKcal) },
            )
            if (settings.kcalGoal <= floor) {
                Text(stringResource(R.string.profile_floor_hint, "%,d".format(floor)), style = MaterialTheme.typography.bodySmall, color = c.textSecondary, modifier = Modifier.padding(bottom = 8.dp))
            }
            // What the Fitbit measured against what the formula guesses, once there are two weeks of it.
            val fitbitKcal = (health as? HealthState.Ready)?.day?.kcalAvg14
            val maintain = kcalFor(NutritionGoal.MAINTAIN)
            if (fitbitKcal != null && maintain != null && hasBodyStats) {
                Text(
                    stringResource(R.string.goals_fitbit_burn, "%,d".format(maintain), "%,d".format(fitbitKcal)),
                    style = MaterialTheme.typography.bodySmall, color = c.textSecondary, modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            androidx.compose.material3.HorizontalDivider(color = c.divider)

            // Protein and carbs: one number each in grams. How it is worked out (g per kg, fat share), and the suggestion, live in its editor.
            TargetRow(
                stringResource(R.string.profile_daily_protein),
                kg?.takeIf { it > 0f }?.let { stringResource(R.string.you_protein_ratio, "%.1f".format(settings.proteinGoal / it)) } ?: stringResource(R.string.you_protein_tap),
                settings.proteinGoal,
            ) { editing = Target.PROTEIN }
            androidx.compose.material3.HorizontalDivider(color = c.divider)

            // Carbs are optional: tracked, they fill the calories left after protein and fat.
            Row(Modifier.fillMaxWidth().toggleable(settings.trackCarbs, role = Role.Switch) { vm.setCarbs(track = it) }.heightIn(min = 48.dp).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.profile_track_carbs), style = MaterialTheme.typography.bodyLarge, color = c.textPrimary, modifier = Modifier.weight(1f))
                FitSwitch(settings.trackCarbs, null)
            }
            if (settings.trackCarbs) {
                val fatPct = settings.fatSharePct.coerceAtLeast(Nutrition.MIN_FAT_SHARE_PCT)
                TargetRow(stringResource(R.string.profile_daily_carbs), stringResource(R.string.you_carbs_ratio, impliedFatPct(settings) ?: fatPct), settings.carbsGoal) { editing = Target.CARBS }
            }
        }

        // 4. Advanced, folded away: body fat changes the protein and calorie maths only a little.
        FitCard {
            var open by rememberSaveable { mutableStateOf(false) }
            val expandedText = stringResource(if (open) R.string.cd_expanded else R.string.cd_collapsed)
            Row(
                Modifier.fillMaxWidth().clickable(role = Role.Button) { open = !open }
                    .semantics { stateDescription = expandedText }.heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.you_advanced), style = MaterialTheme.typography.titleMedium, color = c.textPrimary, modifier = Modifier.semantics { heading() })
                    Text(
                        if (settings.bodyFatPct > 0f) stringResource(R.string.you_advanced_bodyfat, "%.1f".format(settings.bodyFatPct)) else stringResource(R.string.you_advanced_hint),
                        style = MaterialTheme.typography.bodySmall, color = c.textSecondary,
                    )
                }
                Icon(if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = null, tint = c.textSecondary)
            }
            AnimatedVisibility(open) {
                Column {
                    SettingStepper(
                        label = stringResource(R.string.you_body_fat), value = settings.bodyFatPct,
                        onChange = { vm.setBodyMeasures(bodyFatPct = if (it < 4f) 0f else it) },
                        step = 0.5f, min = 0f, max = 55f, unit = "%", decimals = 1,
                    )
                    Text(stringResource(R.string.profile_bodyfat_hint), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                }
            }
        }
    }

    when (editing) {
        Target.PROTEIN -> {
            val hasKg = kg != null && kg > 0f
            TargetDialog(
                title = stringResource(R.string.profile_daily_protein),
                body = stringResource(if (hasKg) R.string.you_protein_dialog_body else R.string.you_protein_no_weight),
                ratio = if (hasKg) ((settings.proteinGoal / kg!! * 10f).roundToInt() / 10f).coerceIn(Nutrition.MIN_PROTEIN_PER_KG, 2.5f) else null,
                range = Nutrition.MIN_PROTEIN_PER_KG..2.5f, stepSize = 0.1f,
                ratioText = { stringResource(R.string.you_protein_slider_state, "%.1f".format(it)) },
                gramsFor = { CalorieUi.protein(kg, settings, it) ?: settings.proteinGoal },
                grams = settings.proteinGoal, gramsStep = 5f, gramsMin = 60f, gramsMax = 400f,
                onDismiss = { editing = null },
                onSave = { r, g -> vm.setProtein(r, g); editing = null },
            )
        }
        Target.CARBS -> {
            val rawRatio = (impliedFatPct(settings) ?: settings.fatSharePct).coerceIn(Nutrition.MIN_FAT_SHARE_PCT, 45)
            val snapped = ((rawRatio / 5f).roundToInt() * 5).coerceIn(Nutrition.MIN_FAT_SHARE_PCT, 45).toFloat()
            TargetDialog(
                title = stringResource(R.string.profile_daily_carbs),
                body = stringResource(R.string.you_carbs_dialog_body),
                ratio = snapped,
                displayRatio = rawRatio.toFloat(),
                range = Nutrition.MIN_FAT_SHARE_PCT.toFloat()..45f, stepSize = 5f,
                ratioText = { stringResource(R.string.you_carbs_ratio, it.roundToInt()) },
                gramsFor = { CalorieUi.carbs(settings.copy(fatSharePct = it.roundToInt()), kg, settings.kcalGoal, settings.proteinGoal) },
                grams = settings.carbsGoal, gramsStep = 10f, gramsMin = 20f, gramsMax = 800f,
                onDismiss = { editing = null },
                onSave = { r, g -> vm.setCarbsTarget(r?.roundToInt(), g); editing = null },
            )
        }
        null -> Unit
    }
}

private enum class Target { PROTEIN, CARBS }

/** The share of calories the protein and carb grams leave for fat (like protein's real g per kg), or null with no calorie target. */
private fun impliedFatPct(s: Settings): Int? =
    if (s.kcalGoal > 0) ((s.kcalGoal - 4 * (s.proteinGoal + s.carbsGoal)) * 100f / s.kcalGoal).roundToInt().coerceIn(0, 100) else null

/** A daily target as one number: name, how it is worked out, the grams in teal and a pencil. Tap to edit. */
@Composable
private fun TargetRow(title: String, sub: String, grams: Int, onClick: () -> Unit) {
    val c = Fit.colors
    Row(
        Modifier.fillMaxWidth().clickable(onClickLabel = stringResource(R.string.cd_edit), role = Role.Button, onClick = onClick)
            .heightIn(min = 56.dp).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = c.textPrimary)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
        }
        Text("$grams ${stringResource(R.string.unit_g)}", style = MaterialTheme.typography.titleMedium, color = c.teal, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(8.dp))
        Icon(Icons.Rounded.Edit, contentDescription = null, tint = c.teal, modifier = Modifier.size(18.dp))
    }
}

/**
 * The editor for one daily target: a slider for how it is worked out ([ratio]: g per kg, or fat share) that sets the
 * grams through [gramsFor] (the same formula as the suggestion, so goal changes keep following it), and the grams
 * themselves for a round number. It opens at the ratio the current grams give; only a moved slider saves a new ratio.
 * No [ratio] (no weight logged yet): grams only. Saved once, on Save.
 */
@Composable
private fun TargetDialog(
    title: String,
    body: String,
    ratio: Float?,
    displayRatio: Float? = null,
    range: ClosedFloatingPointRange<Float>,
    stepSize: Float,
    ratioText: @Composable (Float) -> String,
    gramsFor: (Float) -> Int,
    grams: Int,
    gramsStep: Float,
    gramsMin: Float,
    gramsMax: Float,
    onDismiss: () -> Unit,
    onSave: (Float?, Int) -> Unit,
) {
    val c = Fit.colors
    val haptics = LocalContext.current.container.haptics
    var r by remember { mutableFloatStateOf(ratio ?: range.start) }
    var g by remember { mutableIntStateOf(grams) }
    // Only a moved slider saves a ratio: saving hand-set grams untouched must not store a ratio that "explains" them.
    var moved by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.card,
        title = { Text(title, color = c.textPrimary) },
        text = {
            Column {
                Text(body, color = c.textSecondary, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                if (ratio != null) {
                    val shownRatio = if (moved || displayRatio == null) r else displayRatio
                    val label = ratioText(shownRatio)
                    Text(label, style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
                    Slider(
                        value = r,
                        // Snap to the step and round to one decimal, so a saved 1.8 compares equal later.
                        onValueChange = { v ->
                            val snapped = ((((v - range.start) / stepSize).roundToInt() * stepSize + range.start) * 10f).roundToInt() / 10f
                            if (snapped != r) {
                                haptics.lightTick()
                                r = snapped
                                g = gramsFor(r)
                                moved = true
                            }
                        },
                        valueRange = range,
                        steps = ((range.endInclusive - range.start) / stepSize).roundToInt() - 1,
                        colors = SliderDefaults.colors(thumbColor = c.teal, activeTrackColor = c.teal, inactiveTrackColor = c.tealTrack, activeTickColor = c.onTeal, inactiveTickColor = c.teal),
                        modifier = Modifier.semantics { stateDescription = label },
                    )
                }
                // Label above, so a long translation never squeezes the stepper.
                val gramsLabel = stringResource(R.string.you_protein_grams)
                Spacer(Modifier.height(8.dp))
                Text(gramsLabel, style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
                Spacer(Modifier.height(8.dp))
                com.forgequest.app.ui.components.NumberStepper(
                    g.toFloat(), { g = it.toInt() }, step = gramsStep, min = gramsMin, max = gramsMax,
                    modifier = Modifier.fillMaxWidth(), unit = stringResource(R.string.unit_g), title = gramsLabel, minValueWidth = 84.dp,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(if (ratio != null && moved) r else null, g) }) { Text(stringResource(R.string.action_save), color = c.teal) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel), color = c.textSecondary) } },
    )
}

/** Fitbit through Health Connect: a connection, so it has its own group. */
@Composable
internal fun HealthSettingsGroup(
    vm: SettingsViewModel,
    s: ProgressSnapshot,
    toggleGroup: (String) -> Unit,
    probe: com.forgequest.app.data.health.HealthProbe,
    startHealthCheck: () -> Unit,
    healthAccess: androidx.activity.compose.ManagedActivityResultLauncher<Set<String>, Set<String>>,
) {
    val c = Fit.colors
    val context = LocalContext.current
    val settings = s.settings
    val health by vm.app.health.state.collectAsState()
    val connected = settings.healthEnabled && (health is HealthState.Ready || health is HealthState.Loading)

    SettingsGroup(
        Icons.Rounded.MonitorHeart, c.teal, c.tealTrack, stringResource(R.string.settings_group_health),
        stringResource(if (connected) R.string.summary_fitbit_on else R.string.summary_fitbit_off),
        onToggle = { toggleGroup("health") },
    ) {
        FitCard {
            SwitchHeader(
                Icons.Rounded.MonitorHeart, c.teal, c.tealTrack, stringResource(R.string.profile_health_title),
                stringResource(if (connected) R.string.profile_health_on else R.string.profile_health_hint), connected,
            ) { on ->
                when {
                    !on -> vm.setHealth(false)
                    probe.status() != androidx.health.connect.client.HealthConnectClient.SDK_AVAILABLE -> context.toast(context.getString(R.string.hc_unavailable))
                    else -> { vm.setHealth(true); healthAccess.launch(vm.app.health.requestPermissions()) }
                }
            }
            if (connected) {
                TextButton(onClick = startHealthCheck) { Text(stringResource(R.string.hc_test_title), color = c.teal) }
            }
            // Switched on here but access was taken back in Health Connect: say so rather than look switched off.
            if (settings.healthEnabled && health == HealthState.NeedsAccess) {
                Text(stringResource(R.string.ready_reconnect_body), style = MaterialTheme.typography.bodySmall, color = c.gold)
                TextButton(onClick = { healthAccess.launch(vm.app.health.requestPermissions()) }) { Text(stringResource(R.string.ready_reconnect), color = c.gold) }
            }
        }
    }
}
