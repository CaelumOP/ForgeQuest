package com.forgequest.app.ui.home

import com.forgequest.app.ui.theme.ForgeSpace
import com.forgequest.app.ui.theme.ForgeShape
import com.forgequest.app.ui.theme.tabular
import com.forgequest.app.R
import com.forgequest.app.domain.Nutrition
import androidx.compose.ui.text.style.TextAlign
import com.forgequest.app.ui.localTitle
import com.forgequest.app.ui.mark
import com.forgequest.app.ui.isArabic

import androidx.compose.ui.res.pluralStringResource

import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import com.forgequest.app.ui.components.rememberFullSheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.forgequest.app.AppContainer
import com.forgequest.app.data.PlanDayWithItems
import com.forgequest.app.data.ProgressRepository
import com.forgequest.app.data.ProgressSnapshot
import com.forgequest.app.data.db.SessionEntity
import com.forgequest.app.domain.Badges
import com.forgequest.app.domain.DayState
import com.forgequest.app.domain.Dates
import com.forgequest.app.domain.DayTag
import com.forgequest.app.domain.Trends
import com.forgequest.app.domain.WeeklyStreak
import com.forgequest.app.domain.StreakCalculator
import com.forgequest.app.domain.TargetNudge
import com.forgequest.app.ui.onboarding.FoodTargetsSetup
import com.forgequest.app.ui.Routes
import com.forgequest.app.ui.appViewModel
import com.forgequest.app.ui.components.ChoiceChip
import com.forgequest.app.ui.components.TonalPill
import com.forgequest.app.ui.components.toast
import com.forgequest.app.ui.labelRes
import com.forgequest.app.ui.components.ConcentricRings
import com.forgequest.app.ui.components.tweenedInt
import com.forgequest.app.ui.components.FitCard
import com.forgequest.app.ui.components.Sparkline
import com.forgequest.app.ui.components.IconCircle
import com.forgequest.app.ui.components.MiniRing
import com.forgequest.app.ui.components.PillButton
import com.forgequest.app.ui.components.RingSpec
import com.forgequest.app.ui.components.StatColumn
import com.forgequest.app.ui.components.NumberStepper
import com.forgequest.app.ui.goToTab
import com.forgequest.app.ui.theme.Fit
import com.forgequest.app.ui.theme.SerifFont
import androidx.compose.foundation.layout.widthIn
import com.forgequest.app.ui.theme.NumberFont
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import com.forgequest.app.data.health.HealthState
import com.forgequest.app.data.health.RefreshWatch
import com.forgequest.app.data.health.showsYesterday
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt

class HomeViewModel(val app: AppContainer) : ViewModel() {
    val snapshot: StateFlow<ProgressSnapshot?> = app.progress.snapshot.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val openSession: StateFlow<SessionEntity?> = app.workouts.openSession.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        // Load the food list quietly, so the first food search isn't empty while it loads.
        viewModelScope.launch { delay(1500); app.catalog.warm() }
    }

    fun logWeight(kg: Float) = viewModelScope.launch { app.progress.logWeight(kg) }
    val health = app.health.state
    fun refreshHealth(force: Boolean = false) = viewModelScope.launch { app.health.refresh(force) }
    val refreshWatch = RefreshWatch(viewModelScope) { app.health.refresh(force = true) }

    /** Home is on screen: re-read Health Connect, and keep looking for a while if last night is still missing. */
    fun resumeHealth() = viewModelScope.launch {
        val found = app.health.refresh()
        refreshWatch.resume(found, waiting = (app.health.state.value as? HealthState.Ready)?.day?.showsYesterday() == true)
    }
    fun setFeltGreat(v: Boolean) = viewModelScope.launch { app.health.setFeltGreat(v) }

    /** Catches badges a workout or meal earned but that were missed (they used to be checked against stale data). */
    fun checkBadges() = viewModelScope.launch { app.progress.evaluateBadges() }

    suspend fun startToday(day: PlanDayWithItems?) = app.workouts.startSession(day)

    fun setDayTag(epochDay: Long, tag: DayTag?) = viewModelScope.launch { app.progress.setDayTag(epochDay, tag) }

    /** Answers the one-time weekly-target question: [make] is the new target, or null to keep the current one. */
    fun answerNudge(make: Int?) = viewModelScope.launch {
        app.settings.update { it.copy(targetNudgeAnswered = true, weeklyTargetOverride = make ?: it.weeklyTargetOverride) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(nav: NavHostController) {
    val vm = appViewModel { HomeViewModel(it) }
    val snap by vm.snapshot.collectAsState()
    val open by vm.openSession.collectAsState()
    val c = Fit.colors
    val scope = rememberCoroutineScope()
    val health by vm.health.collectAsState()
    val refreshing by vm.refreshWatch.busy.collectAsState()
    val refreshNote by vm.refreshWatch.note.collectAsState()
    // Every time the app comes to the front (back from the Fitbit app too): Fitbit numbers through Health Connect, reused
    // for a few minutes unless still waiting on last night; the update notice only goes online every 6 hours.
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) { vm.resumeHealth(); vm.checkBadges(); vm.app.updateNotice.refresh(); onPauseOrDispose { vm.refreshWatch.pause() } }
    val healthAccess = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.health.connect.client.PermissionController.createRequestPermissionResultContract(),
    ) { vm.refreshHealth(force = true) }
    val updateBanner by vm.app.updateNotice.banner.collectAsState()
    val whatsNew by vm.app.updateNotice.whatsNew.collectAsState()
    var weightSheet by remember { mutableStateOf(false) }
    var tagDay by remember { mutableStateOf<Long?>(null) }
    var foodSetup by remember { mutableStateOf(false) }

    val s = snap ?: return Box(Modifier.fillMaxSize().background(c.background))

    val today = s.plan?.day(Dates.todayDayOfWeek())
    // "Just log" mode: no streak, levels, badges or readiness on Home; the data stays and comes back when it's turned off.
    val justLog = s.settings.justLog
    // The done state's line is the day's top insight; read-only here, so Home never starts a cooldown (the finish screen does).
    val res = androidx.compose.ui.platform.LocalContext.current.resources
    val doneInsight = remember(s.sessions, s.doneSets, s.settings.insightHistory, s.settings.weightUnit) {
        if (!s.workoutDoneToday || justLog) null else runCatching {
            com.forgequest.app.domain.pick(
                com.forgequest.app.domain.InsightInput(
                    sessions = s.sessions.filter { it.finishedAt != null },
                    sets = s.doneSets,
                    exercises = s.exercises,
                    streak = s.streak,
                    muscleWeekTargets = com.forgequest.app.domain.muscleWeekTargets(s.plan?.days.orEmpty().flatMap { it.items }, s.exercises),
                ),
                s.settings.insightHistory, Dates.today(), max = 1,
            ).firstOrNull()?.let { com.forgequest.app.ui.workouts.insightText(it, res, s.exercises.mapValues { e -> e.value.name }, s.settings.weightUnit) }
        }.getOrNull()
    }
    // What today's plan trains, so the readiness sentence can name it ("Legs today"); null on rest days and mixed days.
    val todayFocus = if (today == null || today.day.isRest) null else com.forgequest.app.domain.Readiness.focusOf(
        today.items.filter { !com.forgequest.app.domain.WorkoutScore.isExtra(it.block) }.flatMap { s.exercises[it.exerciseId]?.primary.orEmpty() },
    )
    val todayCard: @Composable () -> Unit = {
        TodayCard(
            day = today,
            exerciseNames = today?.items?.mapNotNull { s.exercises[it.exerciseId]?.name }?.distinct().orEmpty(),
            hasOpenSession = open != null,
            doneToday = s.workoutDoneToday,
            doneLine = doneInsight,
            excused = s.excusedToday,
            onCantTrain = { tagDay = Dates.today() },
            showCantTrain = !justLog,
            onStart = {
                scope.launch {
                    vm.startToday(today?.takeIf { !it.day.isRest })
                    nav.navigate(Routes.LIVE)
                }
            },
            // After training, the card leads to what was done; before, to the plan.
            onOpenPlan = { if (s.workoutDoneToday) nav.navigate(Routes.HISTORY) else nav.goToTab(Routes.WORKOUTS) },
            // A rest day can still be a training day: start the next planned workout.
            onTrainAnyway = {
                scope.launch {
                    val next = (1..7).map { (Dates.todayDayOfWeek() + it - 1) % 7 + 1 }
                        .firstNotNullOfOrNull { dow -> s.plan?.day(dow)?.takeIf { !it.day.isRest && it.items.isNotEmpty() } }
                    vm.startToday(next)
                    nav.navigate(Routes.LIVE)
                }
            },
        )
    }

    Column(
        Modifier.fillMaxSize().background(c.background).statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = ForgeSpace.Gutter),
    ) {
        // An update sits over the greeting row, in the same spot, so nothing below moves; dismissing it shows the header again.
        Box(Modifier.fillMaxWidth()) {
            Header(
                s, showRank = !justLog, onRankClick = { nav.navigate(Routes.PROGRESS) },
                status = homeStatus(
                    hasOpen = open != null, noPlan = today == null, isRest = today?.day?.isRest == true,
                    done = s.workoutDoneToday, excused = s.excusedToday != null,
                ),
                dayTitle = today?.day?.title.orEmpty(),
            )
            UpdateOverlay(updateBanner, onUpdate = vm.app.updateNotice::update, onLater = vm.app.updateNotice::later, modifier = Modifier.align(Alignment.TopCenter))
        }
        whatsNew?.let { WhatsNewDialog(it, onDismiss = vm.app.updateNotice::dismissWhatsNew) }
        CrashCard(Modifier.padding(top = 8.dp))
        // Room between the greeting and the rings so the header reads as its own line.
        Spacer(Modifier.height(28.dp))
        NutritionRings(s) { nav.goToTab(Routes.FOOD) }
        Spacer(Modifier.height(16.dp))
        StatRow(s, onWeight = { weightSheet = true }, onFood = { nav.goToTab(Routes.FOOD) })
        Spacer(Modifier.height(16.dp))
        // Today's workout always sits right under the stats, in thumb reach.
        todayCard()
        Spacer(Modifier.height(16.dp))
        CalorieCardView(s, health, vm.app.settings)

        if (!justLog) ReadinessCard(
            health, enabled = s.settings.healthEnabled, focus = todayFocus, onFeltGreat = vm::setFeltGreat, onOpen = { nav.navigate("readiness/${it.name}") },
            refreshing = refreshing, note = refreshNote,
            onRefresh = vm.refreshWatch::tap, onReconnect = { healthAccess.launch(vm.app.health.requestPermissions()) },
        )

        val unseen = s.badges.filter { !it.seen }.mapNotNull { Badges.byId[it.id] }
        if (unseen.isNotEmpty() && !justLog) {
            FitCard(onClick = { nav.goToTab(Routes.PROFILE) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconCircle(Icons.Rounded.EmojiEvents, c.gold, c.goldTrack)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(pluralStringResource(R.plurals.home_new_badges, unseen.size, unseen.size), style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
                        Text(unseen.map { it.localTitle() }.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Spacer(Modifier.height(ForgeSpace.CardGap))
        }
        tagDay?.let { day ->
            DayTagDialog(day, s.dayTags[day], onPick = { vm.setDayTag(day, it); tagDay = null }, onDismiss = { tagDay = null })
        }
        // After the first finished workout, the body and food-goal questions that used to sit in onboarding.
        if (s.settings.heightCm <= 0f && s.sessions.isNotEmpty()) {
            FitCard(onClick = { foodSetup = true }) {
                Text(stringResource(R.string.food_setup_title), style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.food_setup_body), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
            }
            Spacer(Modifier.height(ForgeSpace.CardGap))
        }
        val weeklyTarget = StreakCalculator.fullTarget(s.plan?.trainingDays ?: emptySet(), s.settings.weeklyTargetOverride)
        val todayDay = Dates.today()
        val nudge = remember(s.sessions, weeklyTarget, s.settings.onboarded, s.settings.targetNudgeAnswered, todayDay) {
            val finishedDays = s.sessions.map { it.epochDay }.toSet()
            TargetNudge.offer(finishedDays, weeklyTarget, minOf(finishedDays.minOrNull() ?: todayDay, todayDay), todayDay)
                ?.takeIf { s.settings.onboarded && TargetNudge.show(s.settings.targetNudgeAnswered, it) }
        }
        if (!justLog) {
            StreakCard(s.streak, onDayClick = { day -> tagDay = day }, nudge = nudge, keepTarget = weeklyTarget, onNudge = vm::answerNudge)
            Spacer(Modifier.height(ForgeSpace.CardGap))
        }
        ProgressCard(s, onOpen = { nav.navigate(Routes.PROGRESS) })
        Spacer(Modifier.height(ForgeSpace.CardGap))
        Spacer(Modifier.height(24.dp))
        Spacer(Modifier.navigationBarsPadding())
    }

    if (weightSheet) {
        val unit = s.settings.weightUnit
        var kg by remember { mutableFloatStateOf(s.latestWeight ?: 75f) }
        ModalBottomSheet(sheetState = rememberFullSheetState(), onDismissRequest = { weightSheet = false }, containerColor = c.card) {
            Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.weight_log_title), style = MaterialTheme.typography.titleLarge, color = c.textPrimary)
                Spacer(Modifier.height(20.dp))
                NumberStepper(
                    unit.fromKg(kg), { kg = unit.toKg(it) }, step = unit.bodyStep, min = unit.fromKg(30f), max = unit.fromKg(250f), unit = unit.label, decimals = 1,
                    title = stringResource(R.string.weight_body), minValueWidth = 96.dp, onTick = vm.app.haptics::tick,
                )
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.stepper_tap_to_type), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                Spacer(Modifier.height(24.dp))
                val context = androidx.compose.ui.platform.LocalContext.current
                val saved = stringResource(R.string.weight_saved)
                PillButton(stringResource(R.string.action_save), onClick = { vm.logWeight(kg); weightSheet = false; context.toast(saved) }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(16.dp))
            }
        }
    }
    RaadEgg(s.settings.name, vm.app.haptics::celebrate)
    if (foodSetup) FoodTargetsSetup(s.settings, s.latestWeight) { foodSetup = false }
}

/** What the day is about, for the one calm line under the greeting. */
internal enum class HomeStatus { IN_PROGRESS, TRAIN, DONE, EXCUSED, REST, NO_PLAN }

internal fun homeStatus(hasOpen: Boolean, noPlan: Boolean, isRest: Boolean, done: Boolean, excused: Boolean): HomeStatus {
    // An open workout matters most; a finished one beats an excused or rest day (someone trained anyway).
    return when {
        hasOpen -> HomeStatus.IN_PROGRESS
        done -> HomeStatus.DONE
        excused -> HomeStatus.EXCUSED
        noPlan -> HomeStatus.NO_PLAN
        isRest -> HomeStatus.REST
        else -> HomeStatus.TRAIN
    }
}

@Composable
private fun statusText(status: HomeStatus, dayTitle: String, s: ProgressSnapshot): String {
    val day = when (status) {
        HomeStatus.IN_PROGRESS -> stringResource(R.string.home_status_in_progress)
        HomeStatus.TRAIN -> stringResource(R.string.home_status_train, dayTitle)
        HomeStatus.DONE -> stringResource(R.string.home_status_done)
        HomeStatus.EXCUSED -> stringResource(R.string.home_status_excused)
        HomeStatus.REST -> stringResource(R.string.home_status_rest)
        HomeStatus.NO_PLAN -> stringResource(R.string.home_status_no_plan)
    }
    // A workout in progress is the only thing worth saying; otherwise the day plus protein, the one food number that matters most.
    if (status == HomeStatus.IN_PROGRESS) return day
    val left = Nutrition.gramsLeft(s.settings.proteinGoal, s.today.protein)
    val food = if (left <= 0) stringResource(R.string.home_status_protein_met) else stringResource(R.string.home_status_protein_left, left)
    return day + " · " + food
}

@Composable
private fun Header(s: ProgressSnapshot, showRank: Boolean, onRankClick: () -> Unit, status: HomeStatus, dayTitle: String) {
    val c = Fit.colors
    val hour = LocalTime.now().hour
    val greeting = when (hour) {
        in 5..11 -> stringResource(R.string.home_good_morning)
        in 12..16 -> stringResource(R.string.home_good_afternoon)
        else -> stringResource(R.string.home_good_evening)
    }
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            // The date sits quietly above; the greeting is the one big line.
            Text(LocalDate.now().format(DateTimeFormatter.ofPattern(stringResource(R.string.home_date_pattern))), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
            Text(
                if (s.settings.name.isNotBlank()) stringResource(R.string.home_greeting_name, greeting, s.settings.name) else greeting,
                style = MaterialTheme.typography.displaySmall,
                color = c.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(statusText(status, dayTitle, s), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        // The rank letter alone meant nothing to new people; the level under it says what the ring is.
        if (showRank) Column(Modifier.clip(ForgeShape.Sm).clickable(onClick = onRankClick).padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            MiniRing(s.level.progress, c.blue, c.blueTrack, size = 44.dp, stroke = 3.dp) {
                Text(s.level.rank.mark(s.level.level), style = MaterialTheme.typography.titleMedium, color = c.textPrimary, fontWeight = FontWeight.Bold)
            }
            Text(if (isArabic()) s.level.rank.localTitle() else stringResource(R.string.home_level, s.level.level), style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
        }
    }
}

@Composable
private fun NutritionRings(s: ProgressSnapshot, onOpen: () -> Unit) {
    val c = Fit.colors
    val goalKcal = s.settings.kcalGoal.coerceAtLeast(1)
    val goalProtein = s.settings.proteinGoal.coerceAtLeast(1)
    // Colour means one thing: blue is calories, gold is protein, purple is carbs. Going over is the ring's second lap, not a colour change.
    val kcalColor = c.blue
    val kcalTrack = c.blueTrack
    // The centre numbers stop growing at 1.3x text so they stay inside the inner ring; TalkBack still reads the full sentence.
    val fontScale = androidx.compose.ui.platform.LocalDensity.current.fontScale
    val capped = { sp: Float -> (sp * minOf(fontScale, 1.3f) / fontScale).sp }
    Column(
        Modifier.fillMaxWidth().clip(ForgeShape.Lg).clickable(onClickLabel = stringResource(R.string.cd_open_food), role = Role.Button, onClick = onOpen).semantics(mergeDescendants = true) {},
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val carbsOn = s.settings.trackCarbs
        // TalkBack reads the rings and their centre as one sentence, in ring order.
        val ringsLabel = listOfNotNull(
            stringResource(R.string.a11y_rings_protein, s.today.protein.roundToInt(), s.settings.proteinGoal),
            if (carbsOn) stringResource(R.string.a11y_rings_carbs, s.today.carbs.roundToInt(), s.settings.carbsGoal) else null,
            stringResource(R.string.a11y_rings_calories, "%,d".format(s.today.kcal), "%,d".format(s.settings.kcalGoal)),
            stringResource(R.string.a11y_rings_left, Nutrition.gramsLeft(s.settings.proteinGoal, s.today.protein).coerceAtLeast(0), "%,d".format((s.settings.kcalGoal - s.today.kcal).coerceAtLeast(0))),
        ).joinToString(stringResource(R.string.list_separator))
        ConcentricRings(
            rings = listOfNotNull(
                RingSpec(s.today.protein / goalProtein, c.gold, c.goldTrack),
                if (carbsOn) RingSpec(s.today.carbs / s.settings.carbsGoal.coerceAtLeast(1), c.purple, c.purpleTrack) else null,
                RingSpec(s.today.kcal.toFloat() / goalKcal, kcalColor, kcalTrack),
            ),
            size = 210.dp,
            stroke = if (carbsOn) 13.dp else 16.dp,
            description = ringsLabel,
        ) {
            Column(Modifier.widthIn(max = 104.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                // Units under the number: without them "66" reads like a score. Calories left sit in the stat row below.
                val proteinLeft = tweenedInt(Nutrition.gramsLeft(s.settings.proteinGoal, s.today.protein))
                val proteinMet = proteinLeft <= 0
                Text(if (proteinMet) stringResource(R.string.home_done) else "$proteinLeft", style = androidx.compose.material3.LocalTextStyle.current.tabular(), fontFamily = SerifFont, fontSize = capped(44f), fontWeight = FontWeight.Normal, color = c.textPrimary)
                Text(stringResource(if (proteinMet) R.string.food_protein_met else R.string.home_center_protein), style = MaterialTheme.typography.labelSmall, color = c.textSecondary, textAlign = TextAlign.Center, maxLines = 2)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatRow(s: ProgressSnapshot, onWeight: () -> Unit, onFood: () -> Unit) {
    val c = Fit.colors
    // The numbers glide to their new values after a meal is logged.
    val proteinLeft = tweenedInt(Nutrition.gramsLeft(s.settings.proteinGoal, s.today.protein))
    val kcalLeft = tweenedInt(s.settings.kcalGoal - s.today.kcal)
    val reflow = com.forgequest.app.ui.components.reflowText()
    // Thin dividers only in the single row; the wrapped two-per-row layout needs none.
    val sep: @Composable RowScope.() -> Unit = {
        if (!reflow) Box(Modifier.width(1.dp).fillMaxHeight().padding(vertical = 10.dp).background(c.divider))
    }
    // Numbers in the main text colour; the dot names the ring the number belongs to (it replaces the old legend row).
    val stats: @Composable RowScope.() -> Unit = {
        StatColumn(
            if (proteinLeft > 0) stringResource(R.string.home_protein_left_value, proteinLeft) else stringResource(R.string.home_done),
            if (proteinLeft > 0) stringResource(R.string.home_protein_left) else stringResource(R.string.home_protein_goal),
            c.textPrimary,
            Modifier.weight(1f),
            dot = c.gold,
            onClick = onFood,
        )
        if (s.settings.trackCarbs) {
            sep()
            val carbsLeft = Nutrition.gramsLeft(s.settings.carbsGoal, s.today.carbs)
            StatColumn(
                stringResource(R.string.home_protein_left_value, carbsLeft.coerceAtLeast(0)),
                stringResource(R.string.home_carbs_left),
                c.textPrimary,
                Modifier.weight(1f),
                dot = c.purple,
            onClick = onFood,
            )
        }
        sep()
        StatColumn(
            "%,d".format(abs(kcalLeft)),
            if (kcalLeft >= 0) stringResource(R.string.home_cal_left) else stringResource(R.string.home_cal_surplus),
            c.textPrimary,
            Modifier.weight(1f),
            dot = c.blue,
            onClick = onFood,
        )
        sep()
        StatColumn(
            s.latestWeight?.let { s.settings.weightUnit.number(it) } ?: "+",
            if (s.latestWeight != null) stringResource(R.string.home_weight_unit, s.settings.weightUnit.label) else stringResource(R.string.home_log_weight),
            if (s.latestWeight != null) c.textPrimary else c.teal,
            Modifier.weight(1f),
            onClick = onWeight,
            labelColor = if (s.latestWeight != null) null else c.teal,
        )
    }
    // From 1.35x text the stats wrap two to a row instead of squeezing four numbers into quarter widths.
    if (reflow) {
        FlowRow(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp), maxItemsInEachRow = 2) { stats() }
    } else Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) { stats() }
}

/** The hook into the Progress screen: this month's workouts with a little trend, Whoop-style. */
@Composable
private fun ProgressCard(s: ProgressSnapshot, onOpen: () -> Unit) {
    val c = Fit.colors
    if (s.sessions.isEmpty()) return
    val today = Dates.today()
    val weekly = Trends.weeklyCounts(s.sessions.map { it.epochDay }, 8, today)
    val monthWorkouts = s.sessions.count { it.epochDay > today - 30 }
    val sessionDays = s.sessions.associate { it.id to it.epochDay }
    val monthRecords = s.doneSets.count { it.isPr && (sessionDays[it.sessionId] ?: 0L) > today - 30 }
    FitCard(onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.home_progress_card), style = MaterialTheme.typography.labelLarge, color = c.textSecondary)
                Text(
                    pluralStringResource(R.plurals.home_month_workouts, monthWorkouts, monthWorkouts) + " · " + pluralStringResource(R.plurals.home_month_records, monthRecords, monthRecords),
                    style = MaterialTheme.typography.titleSmall, color = c.textPrimary,
                )
                Text(stringResource(R.string.home_progress_open), style = MaterialTheme.typography.labelMedium, color = c.teal)
            }
            Spacer(Modifier.width(12.dp))
            Box(Modifier.width(110.dp)) { Sparkline(weekly.map { it.toFloat() }, c.chartStrength) }
        }
    }
}

@Composable
private fun StreakCard(streak: WeeklyStreak, onDayClick: (Long) -> Unit, nudge: Int? = null, keepTarget: Int = 0, onNudge: (Int?) -> Unit = {}) {
    val c = Fit.colors
    // Compact: one row (flame ring, title, weeks and this week's count, hint) over smaller day circles.
    FitCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MiniRing(streak.weekProgress, c.gold, c.goldTrack, size = 40.dp, stroke = 3.dp) {
                val alive = streak.weekProgress > 0f || streak.weeks > 0
                Icon(
                    Icons.Rounded.LocalFireDepartment,
                    contentDescription = null,
                    tint = if (alive) c.gold.copy(alpha = 0.45f + 0.55f * streak.weekProgress.coerceAtLeast(if (streak.weeks > 0) 0.4f else 0f)) else c.textSecondary.copy(alpha = 0.5f),
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.streak_title), style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
                Text(
                    pluralStringResource(R.plurals.streak_weeks, streak.weeks, streak.weeks) + " · " + when {
                        streak.freeWeek -> stringResource(R.string.streak_starts_next_week)
                        streak.headStart -> stringResource(R.string.streak_head_start)
                        streak.hitThisWeek -> stringResource(R.string.streak_week_complete)
                        else -> stringResource(R.string.streak_done_of_target, streak.doneThisWeek, streak.targetThisWeek)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (streak.hitThisWeek) c.gold else c.textPrimary,
                )
                Text(
                    when {
                        streak.freeWeek -> stringResource(R.string.streak_first_week)
                        streak.headStart -> stringResource(R.string.streak_head_start_body)
                        streak.weeks == 0 && streak.doneThisWeek == 0 -> stringResource(R.string.streak_hit_to_start)
                        // Done for the week: also say what keeps the streak alive next week.
                        streak.hitThisWeek -> pluralStringResource(R.plurals.streak_best, streak.best, streak.best) + " · " +
                            pluralStringResource(R.plurals.streak_next_week, streak.targetThisWeek.coerceAtLeast(1), streak.targetThisWeek.coerceAtLeast(1))
                        else -> pluralStringResource(R.plurals.streak_more_to_go, streak.targetThisWeek - streak.doneThisWeek, streak.targetThisWeek - streak.doneThisWeek)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textSecondary,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val today = Dates.today()
            val weekStart = Dates.weekStart(today)
            streak.days.forEachIndexed { i, state ->
                val day = weekStart + i
                // Today and the last 3 days can be excused (sick, travel...) with a tap; workouts can't.
                val canTag = day in (today - ProgressRepository.EXCUSE_BACK_DAYS)..today && state != DayState.DONE && state != DayState.OFF
                Column(
                    Modifier.clip(ForgeShape.Sm).let { if (canTag) it.clickable(onClickLabel = stringResource(R.string.daytag_title)) { onDayClick(day) } else it },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    DayCircle(state, size = 28.dp)
                    Spacer(Modifier.height(4.dp))
                    Text(Dates.dayShort(i + 1), maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
                }
            }
        }
        // Asked once, after two finished weeks of 2+ workouts under the target; any answer ends it.
        if (nudge != null) {
            Spacer(Modifier.height(16.dp))
            Text(pluralStringResource(R.plurals.target_nudge_body, nudge, nudge), style = MaterialTheme.typography.bodyMedium, color = c.textPrimary)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                TextButton(onClick = { onNudge(null) }) { Text(stringResource(R.string.target_nudge_keep, keepTarget), color = c.textSecondary) }
                PillButton(stringResource(R.string.target_nudge_make, nudge), onClick = { onNudge(nudge) })
            }
        }
    }
}

@Composable
private fun DayCircle(state: DayState, size: androidx.compose.ui.unit.Dp = 34.dp) {
    val c = Fit.colors
    val small = size < 34.dp
    val iconSize = if (small) 14.dp else 16.dp
    when (state) {
        DayState.DONE -> Box(Modifier.size(size).clip(CircleShape).background(c.teal), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Check, contentDescription = stringResource(R.string.cd_done), tint = c.onTeal, modifier = Modifier.size(if (small) 16.dp else 20.dp))
        }
        DayState.TODAY_PLANNED -> MiniRing(0f, c.teal, c.teal, size = size, stroke = 2.5.dp) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(c.teal))
        }
        DayState.UPCOMING -> MiniRing(0f, c.teal, c.cardHigh, size = size, stroke = 2.5.dp)
        DayState.MISSED -> MiniRing(0f, c.teal, c.cardHigh, size = size, stroke = 2.5.dp) {
            Box(Modifier.size(4.dp).clip(CircleShape).background(c.textSecondary.copy(alpha = 0.5f)))
        }
        DayState.REST -> Box(Modifier.size(size).clip(CircleShape).background(c.cardHigh), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Bedtime, contentDescription = stringResource(R.string.cd_rest), tint = c.textSecondary, modifier = Modifier.size(iconSize))
        }
        DayState.OFF -> Box(Modifier.size(size).clip(CircleShape).background(c.cardHigh.copy(alpha = 0.5f)))
        DayState.EXCUSED -> Box(Modifier.size(size).clip(CircleShape).background(c.cardHigh), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Pause, contentDescription = stringResource(R.string.cd_excused), tint = c.textSecondary, modifier = Modifier.size(iconSize))
        }
    }
}

/** Sick, injured, travelling or resting: the day stops counting against the week, and its reminder stays quiet. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DayTagDialog(epochDay: Long, current: DayTag?, onPick: (DayTag?) -> Unit, onDismiss: () -> Unit) {
    val c = Fit.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.card,
        title = { Text(stringResource(R.string.daytag_title), color = c.textPrimary) },
        text = {
            Column {
                Text(
                    stringResource(R.string.daytag_body, Dates.dayName(Dates.dayOfWeek(epochDay))),
                    style = MaterialTheme.typography.bodyMedium, color = c.textSecondary,
                )
                Spacer(Modifier.height(12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DayTag.entries.forEach { t -> ChoiceChip(stringResource(t.labelRes), current == t, { onPick(t) }) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel), color = c.textSecondary) } },
        dismissButton = if (current != null) {
            { TextButton(onClick = { onPick(null) }) { Text(stringResource(R.string.daytag_clear), color = c.coral) } }
        } else null,
    )
}

@Composable
private fun TodayCard(
    day: PlanDayWithItems?,
    exerciseNames: List<String>,
    hasOpenSession: Boolean,
    doneToday: Boolean,
    doneLine: String?,
    excused: DayTag?,
    onCantTrain: () -> Unit,
    showCantTrain: Boolean,
    onStart: () -> Unit,
    onOpenPlan: () -> Unit,
    onTrainAnyway: () -> Unit = {},
) {
    val c = Fit.colors
    val isRest = day == null || day.day.isRest
    val noPlan = day == null
    // The one action sits on its own full-width line under the text, so it never squeezes the title at any text size.
    val hasAction = hasOpenSession || (!doneToday && (noPlan || !isRest || excused == null))
    val action: @Composable () -> Unit = {
        val full = Modifier.fillMaxWidth()
        // The first-run tip only points at a real Start or Resume button.
        com.forgequest.app.ui.components.CoachMarkBox("coach_home", R.string.coach_home_t, R.string.coach_home_b, enabled = hasOpenSession || (!noPlan && !isRest), below = true) {
            when {
                hasOpenSession -> PillButton(stringResource(R.string.action_resume), onClick = onStart, modifier = full)
                noPlan -> PillButton(stringResource(R.string.action_set_up), onClick = onOpenPlan, modifier = full)
                !isRest -> PillButton(stringResource(R.string.action_start), onClick = onStart, modifier = full)
                excused == null -> TonalPill(stringResource(R.string.today_train_anyway), onClick = onTrainAnyway, modifier = full)
            }
        }
    }
    FitCard(onClick = onOpenPlan) {
        Text(stringResource(R.string.today_title), style = MaterialTheme.typography.labelLarge, color = c.textSecondary)
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    when {
                        hasOpenSession -> stringResource(R.string.today_in_progress)
                        noPlan -> stringResource(R.string.today_no_plan)
                        isRest && doneToday -> stringResource(R.string.today_extra_done)
                        isRest -> stringResource(R.string.today_rest_day)
                        else -> day!!.day.title
                    },
                    style = MaterialTheme.typography.titleLarge,
                    color = c.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    when {
                        hasOpenSession -> stringResource(R.string.today_resume_hint)
                        doneToday -> doneLine ?: stringResource(R.string.today_done)
                        excused != null -> stringResource(R.string.today_excused, stringResource(excused.labelRes))
                        noPlan -> stringResource(R.string.today_no_plan_hint)
                        isRest -> day?.day?.note?.ifBlank { null } ?: stringResource(R.string.today_rest_hint)
                        else -> pluralStringResource(R.plurals.today_exercises, exerciseNames.size, exerciseNames.size, exerciseNames.take(3).joinToString(stringResource(R.string.list_separator)))
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (doneToday && !hasOpenSession) {
                Spacer(Modifier.width(12.dp))
                Icon(Icons.Rounded.Check, contentDescription = null, tint = c.teal)
            }
        }
        if (hasAction) {
            Spacer(Modifier.height(16.dp))
            action()
        }
        // Already trained today: another session is still one tap away.
        if (doneToday && !hasOpenSession) {
            TextButton(onClick = if (isRest) onTrainAnyway else onStart, modifier = Modifier.padding(top = 4.dp)) {
                Text(stringResource(R.string.today_train_again), color = c.teal, style = MaterialTheme.typography.labelLarge)
            }
        }
        if (showCantTrain && !isRest && !hasOpenSession && !doneToday && excused == null) {
            TextButton(onClick = onCantTrain, modifier = Modifier.padding(top = 4.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 0.dp, vertical = 8.dp)) {
                Text(stringResource(R.string.today_cant_train), color = c.textSecondary, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
