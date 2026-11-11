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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.forgequest.app.data.PlanDayWithItems
import com.forgequest.app.data.ProgressRepository
import com.forgequest.app.data.ProgressSnapshot
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
import com.forgequest.app.data.health.HealthState
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt

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
    // Good readiness news rides as the first row of Today's workout card; every other state keeps its own card above the calorie card.
    val readyDay = (health as? HealthState.Ready)?.day?.takeIf { it.hasData && !justLog && s.settings.healthEnabled }
    // The card's shape comes from the saved settings, which are there on the first frame, not from whether the health
    // data has arrived: with a watch connected the row's place is held while it loads, so the first frames already have
    // the final layout. With no watch there is no row and no gap.
    val healthOn = !justLog && s.settings.healthEnabled && s.settings.healthConnected
    val readinessLoading = healthOn && health == HealthState.Loading
    val readinessInCard = readyDay != null || readinessLoading
    // The sentence under the verdict, and which of its wordings: the same all day, a different one next time.
    val readyLine = readyDay?.let { com.forgequest.app.domain.readinessLine(it.call, todayFocus, it.sleepContext) }
    val readyVariant = remember(readyLine, s.settings.insightHistory) {
        readyLine?.let { com.forgequest.app.domain.dailyVariant(it.historyKind, it.variants, s.settings.insightHistory, Dates.today()) }
    }
    LaunchedEffect(readyLine, readyVariant) {
        if (readyLine != null && readyVariant?.second == true) vm.noteReadinessLine(readyLine.historyKind, readyVariant.first)
    }
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
            readiness = when {
                readyDay != null -> ({ ReadinessRow(readyDay, todayFocus, note = refreshNote, onFeltGreat = vm::setFeltGreat, onOpen = { nav.navigate("readiness/${it.name}") }, line = readyLine ?: com.forgequest.app.domain.ReadinessLine.READY, variant = readyVariant?.first ?: 0) })
                readinessLoading -> ({ ReadinessRowPlaceholder() })
                else -> null
            },
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

    // Worked out before the layout, so the fit can count what is showing today.
    val unseen = if (justLog) emptyList() else s.badges.filter { !it.seen }.mapNotNull { Badges.byId[it.id] }
    // After the first finished workout, the body and food-goal questions that used to sit in onboarding.
    val needsFoodSetup = s.settings.heightCm <= 0f && s.sessions.isNotEmpty()
    val weeklyTarget = StreakCalculator.fullTarget(s.plan?.trainingDays ?: emptySet(), s.settings.weeklyTargetOverride)
    val todayDay = Dates.today()
    val nudge = remember(s.sessions, weeklyTarget, s.settings.onboarded, s.settings.targetNudgeAnswered, todayDay) {
        val finishedDays = s.sessions.map { it.epochDay }.toSet()
        TargetNudge.offer(finishedDays, weeklyTarget, minOf(finishedDays.minOrNull() ?: todayDay, todayDay), todayDay)
            ?.takeIf { s.settings.onboarded && TargetNudge.show(s.settings.targetNudgeAnswered, it) }
    }.takeIf { !justLog }
    val hasCalorieCard = remember(s, health) { com.forgequest.app.ui.CalorieUi.card(s, health) } != null
    // Readiness only shows once a Fitbit is connected, so it counts as an extra too.
    val readinessShown = healthOn || (!justLog && s.settings.healthEnabled && health != HealthState.Unavailable && health != HealthState.Loading)
    val extras = listOf(readinessShown, unseen.isNotEmpty(), needsFoodSetup, nudge != null, hasCalorieCard, s.settings.trackCarbs).count { it }

    // One screen, no scrolling, at normal text on a common phone (about 412 x 919 dp: 780 dp between the status bar and
    // the tab bar). Height budget in dp: header 80, rings 210 + stats 54, streak + month 161, today's workout 154 (+57
    // with the readiness row, which then drops the streak hint), gaps 12: about 776 of 780 with readiness. Spare height is shared between the flexible gaps, which keeps today's workout at the bottom by the
    // thumb. When there is no spare height (large text, small phones, extra cards) the page scrolls; nothing is clipped.
    BoxWithConstraints(Modifier.fillMaxSize().background(c.background).statusBarsPadding()) {
        val fit = homeFit(maxHeight.value, androidx.compose.ui.platform.LocalDensity.current.fontScale, extras)
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = maxHeight).padding(horizontal = ForgeSpace.Gutter),
        ) {
            // An update sits over the greeting row, in the same spot, so nothing below moves; dismissing it shows the header again.
            Box(Modifier.fillMaxWidth()) {
                Header(
                    s, showRank = !justLog, onRankClick = { nav.navigate(Routes.PROFILE) },
                    status = homeStatus(
                        hasOpen = open != null, noPlan = today == null, isRest = today?.day?.isRest == true,
                        done = s.workoutDoneToday, excused = s.excusedToday != null,
                    ),
                    dayTitle = today?.day?.title.orEmpty(),
                )
                UpdateOverlay(updateBanner, onUpdate = vm.app.updateNotice::update, onLater = vm.app.updateNotice::later, modifier = Modifier.align(Alignment.TopCenter))
            }
            whatsNew?.let { WhatsNewDialog(it, onDismiss = vm.app.updateNotice::dismissWhatsNew) }
            CrashCard(Modifier.padding(top = ForgeSpace.Xs))
            FlexGap()
            Hero(s, fit, onFood = { nav.goToTab(Routes.FOOD) }, onWeight = { weightSheet = true })
            FlexGap()

            // This month's line rides at the foot of the streak card; in "just log" mode it gets a slim card of its own.
            val monthRow: (@Composable () -> Unit)? = if (s.sessions.isEmpty()) null else {
                { MonthRow(s, onOpen = { nav.navigate(Routes.PROGRESS) }) }
            }
            if (!justLog) {
                StreakCard(
                    s.streak, onDayClick = { day -> tagDay = day }, showHint = fit == HomeFit.ROOMY && !readinessInCard,
                    nudge = nudge, keepTarget = weeklyTarget, onNudge = vm::answerNudge, footer = monthRow,
                )
                Spacer(Modifier.height(ForgeSpace.Sm))
            } else if (monthRow != null) {
                FitCard(padding = PaddingValues(horizontal = ForgeSpace.CardPaddingCompact, vertical = ForgeSpace.Xxs)) { monthRow() }
                Spacer(Modifier.height(ForgeSpace.Sm))
            }
            tagDay?.let { day ->
                DayTagDialog(day, s.dayTags[day], onPick = { vm.setDayTag(day, it); tagDay = null }, onDismiss = { tagDay = null })
            }

            // Everything above is for reading. What asks for a tap sits below, with today's workout last, by the thumb.
            Spacer(Modifier.weight(1f))
            if (!justLog) ReadinessCard(
                health, enabled = s.settings.healthEnabled, refreshing = refreshing, note = refreshNote,
                onRefresh = vm.refreshWatch::tap, onReconnect = { healthAccess.launch(vm.app.health.requestPermissions()) },
            )
            CalorieCardView(s, health, vm.app.settings)
            if (unseen.isNotEmpty()) {
                FitCard(onClick = { nav.navigate(Routes.PROFILE) }, padding = PaddingValues(ForgeSpace.CardPaddingCompact)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconCircle(Icons.Rounded.EmojiEvents, c.gold, c.goldTrack)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(pluralStringResource(R.plurals.home_new_badges, unseen.size, unseen.size), style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
                            Text(unseen.map { it.localTitle() }.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                Spacer(Modifier.height(ForgeSpace.Sm))
            }
            if (needsFoodSetup) {
                FitCard(onClick = { foodSetup = true }, padding = PaddingValues(ForgeSpace.CardPaddingCompact)) {
                    Text(stringResource(R.string.food_setup_title), style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.food_setup_body), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                }
                Spacer(Modifier.height(ForgeSpace.Sm))
            }
            todayCard()
            Spacer(Modifier.height(ForgeSpace.Sm))
            Spacer(Modifier.navigationBarsPadding())
        }
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

/** How much room Home has. ROOMY shows the larger rings and the streak hint; COMPACT trims both so more phones avoid scrolling. */
internal enum class HomeFit { ROOMY, COMPACT }

/**
 * Picks the fit from the height Home really has ([heightDp], between the status bar and the tab bar), the text size
 * ([fontScale], 1.0 is normal) and how many optional pieces are showing ([extras]: readiness, calorie card, new badges,
 * food setup, target nudge, carbs).
 */
internal fun homeFit(heightDp: Float, fontScale: Float, extras: Int): HomeFit {
    // The base layout needs about 740 dp at normal text; each extra and any larger text adds to that. A screen that
    // falls short gets the smaller rings and no streak hint, which wins back about 50 dp before scrolling takes over.
    val needed = (740f + 24f * extras) * fontScale
    return if (heightDp < needed) HomeFit.COMPACT else HomeFit.ROOMY
}

/** A gap between Home's sections: 12 dp at least, plus an equal share of whatever height is spare. */
@Composable
private fun ColumnScope.FlexGap() {
    Spacer(Modifier.height(ForgeSpace.Sm))
    Spacer(Modifier.weight(1f))
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
    Row(Modifier.fillMaxWidth().padding(top = ForgeSpace.Xxs), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            // The date sits quietly above; the greeting is the one big line (a step smaller, so Home fits one screen).
            Text(LocalDate.now().format(DateTimeFormatter.ofPattern(stringResource(R.string.home_date_pattern))), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
            Text(
                if (s.settings.name.isNotBlank()) stringResource(R.string.home_greeting_name, greeting, s.settings.name) else greeting,
                style = MaterialTheme.typography.headlineMedium,
                color = c.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(statusText(status, dayTitle, s), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        // The rank letter alone meant nothing to new people; the level under it says what the ring is.
        if (showRank) Column(Modifier.clip(ForgeShape.Sm).clickable(onClickLabel = stringResource(R.string.cd_open_profile), role = Role.Button, onClick = onRankClick).padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            MiniRing(s.level.progress, c.blue, c.blueTrack, size = 40.dp, stroke = 3.dp) {
                Text(s.level.rank.mark(s.level.level), style = MaterialTheme.typography.titleMedium, color = c.textPrimary, fontWeight = FontWeight.Bold)
            }
            Text(if (isArabic()) s.level.rank.localTitle() else stringResource(R.string.home_level, s.level.level), style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
        }
    }
}

@Composable
private fun NutritionRings(s: ProgressSnapshot, size: Dp, onOpen: () -> Unit) {
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
        val ringCount = if (carbsOn) 3 else 2
        val stroke = if (carbsOn) 13.dp else 16.dp
        val gap = 6.dp
        val inner = size - (stroke * ringCount + gap * (ringCount - 1)) * 2
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
            size = size,
            stroke = stroke,
            gap = gap,
            description = ringsLabel,
        ) {
            Column(Modifier.widthIn(max = inner - 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                // Units under the number: without them "66" reads like a score. Calories left sit in the stat row below.
                val proteinLeft = tweenedInt(Nutrition.gramsLeft(s.settings.proteinGoal, s.today.protein))
                val proteinMet = proteinLeft <= 0
                Text(if (proteinMet) stringResource(R.string.home_done) else "$proteinLeft", style = androidx.compose.material3.LocalTextStyle.current.tabular(), fontFamily = SerifFont, fontSize = capped(minOf(44f, inner.value * 0.36f)), fontWeight = FontWeight.Normal, color = c.textPrimary)
                Text(stringResource(if (proteinMet) R.string.food_protein_met else R.string.home_center_protein), style = MaterialTheme.typography.labelSmall, color = c.textSecondary, textAlign = TextAlign.Center, maxLines = 2)
            }
        }
    }
}

/** One of the day's numbers, shown as a column in the stats row under the rings. */
private class HomeStat(val value: String, val label: String, val dot: Color?, val onClick: () -> Unit, val accent: Boolean = false)

@Composable
private fun homeStats(s: ProgressSnapshot, onWeight: () -> Unit, onFood: () -> Unit): List<HomeStat> {
    // The numbers glide to their new values after a meal is logged.
    val proteinLeft = tweenedInt(Nutrition.gramsLeft(s.settings.proteinGoal, s.today.protein))
    val kcalLeft = tweenedInt(s.settings.kcalGoal - s.today.kcal)
    val c = Fit.colors
    // The dot names the ring the number belongs to (it replaces the old legend row). Weight comes last.
    return listOfNotNull(
        HomeStat(
            if (proteinLeft > 0) stringResource(R.string.home_protein_left_value, proteinLeft) else stringResource(R.string.home_done),
            if (proteinLeft > 0) stringResource(R.string.home_protein_left) else stringResource(R.string.home_protein_goal),
            c.gold, onFood,
        ),
        if (s.settings.trackCarbs) HomeStat(
            stringResource(R.string.home_protein_left_value, Nutrition.gramsLeft(s.settings.carbsGoal, s.today.carbs).coerceAtLeast(0)),
            stringResource(R.string.home_carbs_left),
            c.purple, onFood,
        ) else null,
        HomeStat(
            "%,d".format(abs(kcalLeft)),
            if (kcalLeft >= 0) stringResource(R.string.home_cal_left) else stringResource(R.string.home_cal_surplus),
            c.blue, onFood,
        ),
        HomeStat(
            s.latestWeight?.let { s.settings.weightUnit.number(it) } ?: "+",
            if (s.latestWeight != null) stringResource(R.string.home_weight_unit, s.settings.weightUnit.label) else stringResource(R.string.home_log_weight),
            null, onWeight, accent = s.latestWeight == null,
        ),
    )
}

/** Big centred rings over the stats row; the rings step down a size when Home is tight or the text is large. */
@Composable
private fun Hero(s: ProgressSnapshot, fit: HomeFit, onFood: () -> Unit, onWeight: () -> Unit) {
    NutritionRings(s, if (fit == HomeFit.ROOMY && !com.forgequest.app.ui.components.reflowText()) 210.dp else 180.dp, onFood)
    Spacer(Modifier.height(ForgeSpace.Md))
    StatRow(homeStats(s, onWeight, onFood))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatRow(stats: List<HomeStat>) {
    val c = Fit.colors
    val reflow = com.forgequest.app.ui.components.reflowText()
    val columns: @Composable RowScope.() -> Unit = {
        stats.forEachIndexed { i, stat ->
            // Thin dividers only in the single row; the wrapped two-per-row layout needs none.
            if (i > 0 && !reflow) Box(Modifier.width(1.dp).fillMaxHeight().padding(vertical = 10.dp).background(c.divider))
            StatColumn(
                stat.value, stat.label, if (stat.accent) c.teal else c.textPrimary, Modifier.weight(1f),
                dot = stat.dot, onClick = stat.onClick, labelColor = if (stat.accent) c.teal else null,
            )
        }
    }
    // From 1.35x text the stats wrap two to a row instead of squeezing four numbers into quarter widths.
    if (reflow) {
        FlowRow(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp), maxItemsInEachRow = 2) { columns() }
    } else Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) { columns() }
}

/** The hook into the Progress screen: this month's workouts and records with a little trend, as one tappable row. */
@Composable
private fun MonthRow(s: ProgressSnapshot, onOpen: () -> Unit) {
    val c = Fit.colors
    val today = Dates.today()
    val weekly = Trends.weeklyCounts(s.sessions.map { it.epochDay }, 8, today)
    val monthWorkouts = s.sessions.count { it.epochDay > today - 30 }
    val sessionDays = s.sessions.associate { it.id to it.epochDay }
    val monthRecords = s.doneSets.count { it.isPr && (sessionDays[it.sessionId] ?: 0L) > today - 30 }
    Row(
        Modifier.fillMaxWidth().clip(ForgeShape.Sm)
            .clickable(onClickLabel = stringResource(R.string.home_progress_open), role = Role.Button, onClick = onOpen)
            .heightIn(min = 48.dp).semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.home_progress_card), style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
            Text(
                pluralStringResource(R.plurals.home_month_workouts, monthWorkouts, monthWorkouts) + " · " + pluralStringResource(R.plurals.home_month_records, monthRecords, monthRecords),
                style = MaterialTheme.typography.titleSmall, color = c.textPrimary,
            )
        }
        Spacer(Modifier.width(ForgeSpace.Sm))
        Box(Modifier.width(72.dp)) { Sparkline(weekly.map { it.toFloat() }, c.chartStrength) }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = c.textSecondary)
    }
}

@Composable
private fun StreakCard(
    streak: WeeklyStreak, onDayClick: (Long) -> Unit, showHint: Boolean,
    nudge: Int? = null, keepTarget: Int = 0, onNudge: (Int?) -> Unit = {}, footer: (@Composable () -> Unit)? = null,
) {
    val c = Fit.colors
    // One line (flame ring, weeks, this week's count), the optional hint, the week's days, then the footer row.
    FitCard(padding = PaddingValues(start = ForgeSpace.CardPaddingCompact, top = ForgeSpace.CardPaddingCompact, end = ForgeSpace.CardPaddingCompact, bottom = if (footer != null) ForgeSpace.Xs else ForgeSpace.CardPaddingCompact)) {
        val title = stringResource(R.string.streak_title)
        val line = pluralStringResource(R.plurals.streak_weeks, streak.weeks, streak.weeks) + " · " + when {
            streak.freeWeek -> stringResource(R.string.streak_starts_next_week)
            streak.headStart -> stringResource(R.string.streak_head_start)
            streak.hitThisWeek -> stringResource(R.string.streak_week_complete)
            else -> stringResource(R.string.streak_done_of_target, streak.doneThisWeek, streak.targetThisWeek)
        }
        // The flame says "streak" to the eye; TalkBack hears the title that used to sit above.
        Row(Modifier.semantics(mergeDescendants = true) { heading(); contentDescription = "$title, $line" }, verticalAlignment = Alignment.CenterVertically) {
            MiniRing(streak.weekProgress, c.gold, c.goldTrack, size = 24.dp, stroke = 2.5.dp) {
                val alive = streak.weekProgress > 0f || streak.weeks > 0
                Icon(
                    Icons.Rounded.LocalFireDepartment,
                    contentDescription = null,
                    tint = if (alive) c.gold.copy(alpha = 0.45f + 0.55f * streak.weekProgress.coerceAtLeast(if (streak.weeks > 0) 0.4f else 0f)) else c.textSecondary.copy(alpha = 0.5f),
                    modifier = Modifier.size(14.dp),
                )
            }
            Spacer(Modifier.width(ForgeSpace.Xs))
            Text(line, style = MaterialTheme.typography.titleSmall, color = if (streak.hitThisWeek) c.gold else c.textPrimary, modifier = Modifier.weight(1f))
        }
        if (showHint) {
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
                modifier = Modifier.padding(top = ForgeSpace.Xxs),
            )
        }
        Spacer(Modifier.height(ForgeSpace.Xs))
        // Seven equal cells, each a full 48 dp target, so a day is easy to hit even though its circle is small.
        Row(Modifier.fillMaxWidth()) {
            val today = Dates.today()
            val weekStart = Dates.weekStart(today)
            streak.days.forEachIndexed { i, state ->
                val day = weekStart + i
                // Today and the last 3 days can be excused (sick, travel...) with a tap; workouts can't.
                val canTag = day in (today - ProgressRepository.EXCUSE_BACK_DAYS)..today && state != DayState.DONE && state != DayState.OFF
                Column(
                    Modifier.weight(1f).heightIn(min = 48.dp).clip(ForgeShape.Sm).let { if (canTag) it.clickable(onClickLabel = stringResource(R.string.daytag_title)) { onDayClick(day) } else it },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
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
        footer?.let {
            Spacer(Modifier.height(ForgeSpace.Xs))
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.divider))
            it()
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
    readiness: (@Composable () -> Unit)? = null,
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
    // Last on Home, right above the tab bar: the one button that matters is where the thumb already is.
    FitCard(onClick = onOpenPlan, padding = PaddingValues(ForgeSpace.CardPaddingCompact)) {
        readiness?.let {
            it()
            Spacer(Modifier.height(ForgeSpace.Xs))
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.divider))
            Spacer(Modifier.height(ForgeSpace.Sm))
        }
        val cardTitle = stringResource(R.string.today_title)
        val title = when {
            hasOpenSession -> stringResource(R.string.today_in_progress)
            noPlan -> stringResource(R.string.today_no_plan)
            isRest && doneToday -> stringResource(R.string.today_extra_done)
            isRest -> stringResource(R.string.today_rest_day)
            else -> day!!.day.title
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                // The "Today's workout" label gave way to save height; TalkBack still hears it before the title.
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    color = c.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading(); contentDescription = "$cardTitle, $title" },
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
        val cantTrain: (@Composable () -> Unit)? = if (showCantTrain && !isRest && !hasOpenSession && !doneToday && excused == null) {
            {
                TextButton(onClick = onCantTrain) {
                    Text(stringResource(R.string.today_cant_train), color = c.textSecondary, style = MaterialTheme.typography.labelLarge)
                }
            }
        } else null
        if (hasAction) {
            Spacer(Modifier.height(ForgeSpace.Sm))
            // "Can't train today" sits beside Start to save a line; at large text it drops under it, so neither is squeezed.
            if (cantTrain != null && !com.forgequest.app.ui.components.reflowText()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { action() }
                    Spacer(Modifier.width(ForgeSpace.Xs))
                    cantTrain()
                }
            } else {
                action()
                cantTrain?.invoke()
            }
        }
        // Already trained today: another session is still one tap away.
        if (doneToday && !hasOpenSession) {
            TextButton(onClick = if (isRest) onTrainAnyway else onStart, modifier = Modifier.padding(top = 4.dp)) {
                Text(stringResource(R.string.today_train_again), color = c.teal, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
