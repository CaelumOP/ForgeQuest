package com.forgequest.app.ui.home

import com.forgequest.app.R
import com.forgequest.app.ui.localTitle

import androidx.compose.ui.res.pluralStringResource

import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import com.forgequest.app.ui.Routes
import com.forgequest.app.ui.appViewModel
import com.forgequest.app.ui.components.ChoiceChip
import com.forgequest.app.ui.labelRes
import com.forgequest.app.ui.components.ConcentricRings
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
import com.forgequest.app.ui.theme.Manrope
import kotlinx.coroutines.flow.SharingStarted
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

    fun logWeight(kg: Float) = viewModelScope.launch { app.progress.logWeight(kg) }
    val health = app.health.state
    fun refreshHealth() = viewModelScope.launch { app.health.refresh() }
    fun setFeltGreat(v: Boolean) = viewModelScope.launch { app.health.setFeltGreat(v) }

    suspend fun startToday(day: PlanDayWithItems?) = app.workouts.startSession(day)

    fun setDayTag(epochDay: Long, tag: DayTag?) = viewModelScope.launch { app.progress.setDayTag(epochDay, tag) }
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
    // Fitbit numbers through Health Connect: re-read whenever Home opens, and right after access is granted.
    androidx.compose.runtime.LaunchedEffect(Unit) { vm.refreshHealth() }
    var weightSheet by remember { mutableStateOf(false) }
    var tagDay by remember { mutableStateOf<Long?>(null) }

    val s = snap ?: return Box(Modifier.fillMaxSize().background(c.background))

    Column(
        Modifier.fillMaxSize().background(c.background).statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
    ) {
        Header(s, onRankClick = { nav.navigate(Routes.PROGRESS) })
        Spacer(Modifier.height(8.dp))
        NutritionRings(s)
        Spacer(Modifier.height(16.dp))
        StatRow(s, onWeight = { weightSheet = true })
        Spacer(Modifier.height(16.dp))

        val unseen = s.badges.filter { !it.seen }.mapNotNull { Badges.byId[it.id] }
        if (unseen.isNotEmpty()) {
            FitCard(onClick = { nav.goToTab(Routes.PROFILE) }, padding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconCircle(Icons.Rounded.EmojiEvents, c.gold, c.goldTrack)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(pluralStringResource(R.plurals.home_new_badges, unseen.size, unseen.size), style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
                        Text(unseen.map { it.localTitle() }.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        ReadinessCard(health, onFeltGreat = vm::setFeltGreat, onOpen = { nav.navigate("readiness/${it.name}") })
        // Today's line from the owner's Gemini automation, when the bridge is on.
        if (s.settings.bridgeEnabled && s.settings.bridgeToday.isNotBlank()) {
            FitCard {
                Text(stringResource(R.string.bridge_home_title), style = MaterialTheme.typography.labelLarge, color = c.purple)
                Spacer(Modifier.height(4.dp))
                Text(s.settings.bridgeToday, style = MaterialTheme.typography.bodyMedium, color = c.textPrimary)
            }
            Spacer(Modifier.height(12.dp))
        }
        tagDay?.let { day ->
            DayTagDialog(day, s.dayTags[day], onPick = { vm.setDayTag(day, it); tagDay = null }, onDismiss = { tagDay = null })
        }
        StreakCard(s.streak, onDayClick = { day -> tagDay = day })
        Spacer(Modifier.height(12.dp))
        ProgressCard(s, onOpen = { nav.navigate(Routes.PROGRESS) })
        Spacer(Modifier.height(12.dp))

        val today = s.plan?.day(Dates.todayDayOfWeek())
        TodayCard(
            day = today,
            exerciseNames = today?.items?.mapNotNull { s.exercises[it.exerciseId]?.name }?.distinct().orEmpty(),
            hasOpenSession = open != null,
            doneToday = s.workoutDoneToday,
            excused = s.excusedToday,
            onCantTrain = { tagDay = Dates.today() },
            onStart = {
                scope.launch {
                    vm.startToday(today?.takeIf { !it.day.isRest })
                    nav.navigate(Routes.LIVE)
                }
            },
            onOpenPlan = { nav.goToTab(Routes.WORKOUTS) },
        )
        Spacer(Modifier.height(24.dp))
        Spacer(Modifier.navigationBarsPadding())
    }

    if (weightSheet) {
        val unit = s.settings.weightUnit
        var kg by remember { mutableFloatStateOf(s.latestWeight ?: 75f) }
        ModalBottomSheet(onDismissRequest = { weightSheet = false }, containerColor = c.card) {
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
                PillButton(stringResource(R.string.action_save), onClick = { vm.logWeight(kg); weightSheet = false }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(16.dp))
            }
        }
    }
    RaadEgg(s.settings.name, vm.app.haptics::celebrate)
}

@Composable
private fun Header(s: ProgressSnapshot, onRankClick: () -> Unit) {
    val c = Fit.colors
    val hour = LocalTime.now().hour
    val greeting = when (hour) {
        in 5..11 -> stringResource(R.string.home_good_morning)
        in 12..16 -> stringResource(R.string.home_good_afternoon)
        in 17..19 -> stringResource(R.string.home_good_evening)
        else -> stringResource(R.string.home_good_night)
    }
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                if (s.settings.name.isNotBlank()) stringResource(R.string.home_greeting_name, greeting, s.settings.name) else greeting,
                style = MaterialTheme.typography.headlineSmall,
                color = c.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(LocalDate.now().format(DateTimeFormatter.ofPattern(stringResource(R.string.home_date_pattern))), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
        }
        Box(Modifier.clip(CircleShape).clickable(onClick = onRankClick)) {
            MiniRing(s.level.progress, c.blue, c.blueTrack, size = 44.dp, stroke = 3.dp) {
                Text(s.level.rank.letter, style = MaterialTheme.typography.titleMedium, color = c.blue, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun NutritionRings(s: ProgressSnapshot) {
    val c = Fit.colors
    val goalKcal = s.settings.kcalGoal.coerceAtLeast(1)
    val goalProtein = s.settings.proteinGoal.coerceAtLeast(1)
    val over = s.today.kcal > goalKcal
    val kcalColor = if (over) c.gold else c.blue
    val kcalTrack = if (over) c.goldTrack else c.blueTrack
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        val carbsOn = s.settings.trackCarbs
        ConcentricRings(
            rings = listOfNotNull(
                RingSpec(s.today.protein / goalProtein, c.teal, c.tealTrack),
                if (carbsOn) RingSpec(s.today.carbs / s.settings.carbsGoal.coerceAtLeast(1), c.purple, c.purpleTrack) else null,
                RingSpec(s.today.kcal.toFloat() / goalKcal, kcalColor, kcalTrack),
            ),
            size = 210.dp,
            stroke = if (carbsOn) 13.dp else 16.dp,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Units under the numbers: without them "66 / 1,005" read like a score (Google Fit labels its ring centre too).
                Text("${s.today.protein.roundToInt()}", fontFamily = Manrope, fontSize = 36.sp, fontWeight = FontWeight.SemiBold, color = c.teal)
                Text(stringResource(R.string.home_center_protein), style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
                Text(stringResource(R.string.home_center_kcal, "%,d".format(s.today.kcal)), fontFamily = Manrope, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = kcalColor, modifier = Modifier.padding(top = 4.dp))
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Legend(c.teal, stringResource(R.string.home_legend_protein))
            if (s.settings.trackCarbs) Legend(c.purple, stringResource(R.string.label_carbs))
            Legend(kcalColor, stringResource(R.string.home_legend_calories))
        }
    }
}

@Composable
private fun Legend(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = Fit.colors.textSecondary)
    }
}

@Composable
private fun StatRow(s: ProgressSnapshot, onWeight: () -> Unit) {
    val c = Fit.colors
    val proteinLeft = (s.settings.proteinGoal - s.today.protein).roundToInt()
    val kcalLeft = s.settings.kcalGoal - s.today.kcal
    Row(Modifier.fillMaxWidth()) {
        StatColumn(
            if (proteinLeft > 0) stringResource(R.string.home_protein_left_value, proteinLeft) else stringResource(R.string.home_done),
            if (proteinLeft > 0) stringResource(R.string.home_protein_left) else stringResource(R.string.home_protein_goal),
            c.teal,
            Modifier.weight(1f),
        )
        if (s.settings.trackCarbs) {
            val carbsLeft = (s.settings.carbsGoal - s.today.carbs).roundToInt()
            StatColumn(
                stringResource(R.string.home_protein_left_value, carbsLeft.coerceAtLeast(0)),
                stringResource(R.string.home_carbs_left),
                c.purple,
                Modifier.weight(1f),
            )
        }
        StatColumn(
            "%,d".format(abs(kcalLeft)),
            if (kcalLeft >= 0) stringResource(R.string.home_cal_left) else stringResource(R.string.home_cal_surplus),
            if (kcalLeft >= 0) c.blue else c.gold,
            Modifier.weight(1f),
        )
        StatColumn(
            s.latestWeight?.let { s.settings.weightUnit.number(it) } ?: "–",
            stringResource(R.string.home_weight_unit, s.settings.weightUnit.label),
            c.textPrimary,
            Modifier.weight(1f),
            onClick = onWeight,
        )
    }
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
    FitCard(onClick = onOpen, padding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.home_progress_card), style = MaterialTheme.typography.labelLarge, color = c.textSecondary)
                Text(
                    "$monthWorkouts ${stringResource(R.string.progress_total_workouts)} · $monthRecords ${stringResource(R.string.progress_total_records)}",
                    style = MaterialTheme.typography.titleSmall, color = c.textPrimary,
                )
                Text(stringResource(R.string.home_progress_open), style = MaterialTheme.typography.labelMedium, color = c.chartGain)
            }
            Spacer(Modifier.width(12.dp))
            Box(Modifier.width(110.dp)) { Sparkline(weekly.map { it.toFloat() }, c.chartStrength) }
        }
    }
}

@Composable
private fun StreakCard(streak: WeeklyStreak, onDayClick: (Long) -> Unit) {
    val c = Fit.colors
    FitCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.streak_title), style = MaterialTheme.typography.titleMedium, color = c.textPrimary, modifier = Modifier.weight(1f))
            Text(
                when {
                    streak.freeWeek -> stringResource(R.string.streak_starts_next_week)
                    streak.hitThisWeek -> stringResource(R.string.streak_week_complete)
                    else -> stringResource(R.string.streak_done_of_target, streak.doneThisWeek, streak.targetThisWeek)
                },
                style = MaterialTheme.typography.labelLarge,
                color = if (streak.hitThisWeek) c.gold else c.textSecondary,
            )
        }
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            MiniRing(streak.weekProgress, c.gold, c.goldTrack, size = 56.dp, stroke = 4.dp) {
                val alive = streak.weekProgress > 0f || streak.weeks > 0
                Icon(
                    Icons.Rounded.LocalFireDepartment,
                    contentDescription = null,
                    tint = if (alive) c.gold.copy(alpha = 0.45f + 0.55f * streak.weekProgress.coerceAtLeast(if (streak.weeks > 0) 0.4f else 0f)) else c.textSecondary.copy(alpha = 0.5f),
                    modifier = Modifier.size(28.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    pluralStringResource(R.plurals.streak_weeks, streak.weeks, streak.weeks),
                    style = MaterialTheme.typography.headlineSmall,
                    color = c.textPrimary,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    when {
                        streak.freeWeek -> stringResource(R.string.streak_first_week)
                        streak.weeks == 0 && streak.doneThisWeek == 0 -> stringResource(R.string.streak_hit_to_start)
                        streak.hitThisWeek -> pluralStringResource(R.plurals.streak_best, streak.best, streak.best)
                        else -> pluralStringResource(R.plurals.streak_more_to_go, streak.targetThisWeek - streak.doneThisWeek, streak.targetThisWeek - streak.doneThisWeek)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textSecondary,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val today = Dates.today()
            val weekStart = Dates.weekStart(today)
            streak.days.forEachIndexed { i, state ->
                val day = weekStart + i
                // Today and the last 3 days can be excused (sick, travel...) with a tap; workouts can't.
                val canTag = day in (today - ProgressRepository.EXCUSE_BACK_DAYS)..today && state != DayState.DONE && state != DayState.OFF
                Column(
                    Modifier.clip(RoundedCornerShape(12.dp)).let { if (canTag) it.clickable(onClickLabel = stringResource(R.string.daytag_title)) { onDayClick(day) } else it },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    DayCircle(state)
                    Spacer(Modifier.height(6.dp))
                    Text(Dates.shortDayLetters[i], style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
                }
            }
        }
    }
}

@Composable
private fun DayCircle(state: DayState) {
    val c = Fit.colors
    val size = 34.dp
    when (state) {
        DayState.DONE -> Box(Modifier.size(size).clip(CircleShape).background(c.teal), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Check, contentDescription = stringResource(R.string.cd_done), tint = c.onTeal, modifier = Modifier.size(20.dp))
        }
        DayState.TODAY_PLANNED -> MiniRing(0f, c.teal, c.teal, size = size, stroke = 2.5.dp) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(c.teal))
        }
        DayState.UPCOMING -> MiniRing(0f, c.teal, c.cardHigh, size = size, stroke = 2.5.dp)
        DayState.MISSED -> MiniRing(0f, c.teal, c.cardHigh, size = size, stroke = 2.5.dp) {
            Box(Modifier.size(4.dp).clip(CircleShape).background(c.textSecondary.copy(alpha = 0.5f)))
        }
        DayState.REST -> Box(Modifier.size(size).clip(CircleShape).background(c.blueTrack), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Bedtime, contentDescription = stringResource(R.string.cd_rest), tint = c.blue, modifier = Modifier.size(16.dp))
        }
        DayState.OFF -> Box(Modifier.size(size).clip(CircleShape).background(c.cardHigh.copy(alpha = 0.5f)))
        DayState.EXCUSED -> Box(Modifier.size(size).clip(CircleShape).background(c.cardHigh), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Pause, contentDescription = stringResource(R.string.cd_excused), tint = c.textSecondary, modifier = Modifier.size(16.dp))
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
    excused: DayTag?,
    onCantTrain: () -> Unit,
    onStart: () -> Unit,
    onOpenPlan: () -> Unit,
) {
    val c = Fit.colors
    val isRest = day == null || day.day.isRest
    val noPlan = day == null
    FitCard(onClick = onOpenPlan) {
        Text(stringResource(R.string.today_title), style = MaterialTheme.typography.labelLarge, color = c.textSecondary)
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (noPlan && !hasOpenSession) IconCircle(Icons.Rounded.Edit, c.teal, c.tealTrack)
            else if (isRest && !hasOpenSession) IconCircle(Icons.Rounded.Bedtime, c.blue, c.blueTrack)
            else IconCircle(Icons.Rounded.FitnessCenter, c.teal, c.tealTrack)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    when {
                        hasOpenSession -> stringResource(R.string.today_in_progress)
                        noPlan -> stringResource(R.string.today_no_plan)
                        isRest -> stringResource(R.string.today_rest_day)
                        else -> day!!.day.title
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = c.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    when {
                        hasOpenSession -> stringResource(R.string.today_resume_hint)
                        doneToday -> stringResource(R.string.today_done)
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
            Spacer(Modifier.width(12.dp))
            when {
                hasOpenSession -> PillButton(stringResource(R.string.action_resume), onClick = onStart)
                doneToday -> Icon(Icons.Rounded.Check, contentDescription = null, tint = c.teal)
                noPlan -> PillButton(stringResource(R.string.action_set_up), onClick = onOpenPlan)
                !isRest -> PillButton(stringResource(R.string.action_start), onClick = onStart)
            }
        }
        if (!isRest && !hasOpenSession && !doneToday && excused == null) {
            TextButton(onClick = onCantTrain, modifier = Modifier.padding(top = 4.dp)) {
                Text(stringResource(R.string.today_cant_train), color = c.textSecondary, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
