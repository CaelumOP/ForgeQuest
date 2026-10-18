package com.forgequest.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
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
import com.forgequest.app.data.ProgressSnapshot
import com.forgequest.app.data.db.SessionEntity
import com.forgequest.app.domain.Badges
import com.forgequest.app.domain.DayState
import com.forgequest.app.domain.Dates
import com.forgequest.app.domain.WeeklyStreak
import com.forgequest.app.ui.Routes
import com.forgequest.app.ui.appViewModel
import com.forgequest.app.ui.components.ConcentricRings
import com.forgequest.app.ui.components.FitCard
import com.forgequest.app.ui.components.IconCircle
import com.forgequest.app.ui.components.MiniRing
import com.forgequest.app.ui.components.PillButton
import com.forgequest.app.ui.components.RingSpec
import com.forgequest.app.ui.components.StatColumn
import com.forgequest.app.ui.components.NumberStepper
import com.forgequest.app.ui.goToTab
import com.forgequest.app.ui.theme.Fit
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

    suspend fun startToday(day: PlanDayWithItems?) = app.workouts.startSession(day)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(nav: NavHostController) {
    val vm = appViewModel { HomeViewModel(it) }
    val snap by vm.snapshot.collectAsState()
    val open by vm.openSession.collectAsState()
    val c = Fit.colors
    val scope = rememberCoroutineScope()
    var weightSheet by remember { mutableStateOf(false) }

    val s = snap ?: return Box(Modifier.fillMaxSize().background(c.background))

    Column(
        Modifier.fillMaxSize().background(c.background).statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
    ) {
        Header(s, onRankClick = { nav.goToTab(Routes.PROFILE) })
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
                        Text(if (unseen.size == 1) "New badge unlocked" else "${unseen.size} new badges", style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
                        Text(unseen.joinToString(" · ") { it.title }, style = MaterialTheme.typography.bodySmall, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        StreakCard(s.streak)
        Spacer(Modifier.height(12.dp))

        val today = s.plan?.day(Dates.todayDayOfWeek())
        TodayCard(
            day = today,
            exerciseNames = today?.items?.mapNotNull { s.exercises[it.exerciseId]?.name }?.distinct().orEmpty(),
            hasOpenSession = open != null,
            doneToday = s.workoutDoneToday,
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
        var kg by remember { mutableFloatStateOf(s.latestWeight ?: 75f) }
        ModalBottomSheet(onDismissRequest = { weightSheet = false }, containerColor = c.card) {
            Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Log body weight", style = MaterialTheme.typography.titleLarge, color = c.textPrimary)
                Spacer(Modifier.height(20.dp))
                NumberStepper(
                    kg, { kg = it }, step = 0.1f, min = 30f, max = 250f, unit = "kg", decimals = 1,
                    title = "Body weight", minValueWidth = 96.dp, onTick = vm.app.haptics::tick,
                )
                Spacer(Modifier.height(8.dp))
                Text("Tap the number to type it", style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                Spacer(Modifier.height(24.dp))
                PillButton("Save", onClick = { vm.logWeight(kg); weightSheet = false }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun Header(s: ProgressSnapshot, onRankClick: () -> Unit) {
    val c = Fit.colors
    val hour = LocalTime.now().hour
    val greeting = when (hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        else -> "Good evening"
    }
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                if (s.settings.name.isNotBlank()) "$greeting, ${s.settings.name}" else greeting,
                style = MaterialTheme.typography.headlineSmall,
                color = c.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMM d")), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
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
        ConcentricRings(
            outer = RingSpec(s.today.protein / goalProtein, c.teal, c.tealTrack),
            inner = RingSpec(s.today.kcal.toFloat() / goalKcal, kcalColor, kcalTrack),
            size = 210.dp,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${s.today.protein.roundToInt()}", fontSize = 36.sp, fontWeight = FontWeight.Medium, color = c.teal)
                Text("%,d".format(s.today.kcal), fontSize = 18.sp, fontWeight = FontWeight.Medium, color = kcalColor)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Legend(c.teal, "Protein (g)")
            Legend(kcalColor, "Calories")
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
            if (proteinLeft > 0) "$proteinLeft g" else "Done",
            if (proteinLeft > 0) "Protein left" else "Protein goal",
            c.teal,
            Modifier.weight(1f),
        )
        StatColumn(
            "%,d".format(abs(kcalLeft)),
            if (kcalLeft >= 0) "Cal left" else "Cal surplus",
            if (kcalLeft >= 0) c.blue else c.gold,
            Modifier.weight(1f),
        )
        StatColumn(
            s.latestWeight?.let { "%.1f".format(it) } ?: "–",
            "Weight (kg)",
            c.textPrimary,
            Modifier.weight(1f),
            onClick = onWeight,
        )
    }
}

@Composable
private fun StreakCard(streak: WeeklyStreak) {
    val c = Fit.colors
    FitCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Your weekly streak", style = MaterialTheme.typography.titleMedium, color = c.textPrimary, modifier = Modifier.weight(1f))
            Text(
                when {
                    streak.freeWeek -> "Starts next week"
                    streak.hitThisWeek -> "Week complete"
                    else -> "${streak.doneThisWeek} of ${streak.targetThisWeek} this week"
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
                    if (streak.weeks == 1) "1 week" else "${streak.weeks} weeks",
                    style = MaterialTheme.typography.headlineSmall,
                    color = c.textPrimary,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    when {
                        streak.freeWeek -> "Your first full week starts Monday"
                        streak.weeks == 0 && streak.doneThisWeek == 0 -> "Hit your weekly target to start a streak"
                        streak.hitThisWeek -> "Best: ${streak.best} ${if (streak.best == 1) "week" else "weeks"}"
                        else -> "${streak.targetThisWeek - streak.doneThisWeek} more to keep it going"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textSecondary,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            streak.days.forEachIndexed { i, state ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
            Icon(Icons.Rounded.Check, contentDescription = "Done", tint = c.onTeal, modifier = Modifier.size(20.dp))
        }
        DayState.TODAY_PLANNED -> MiniRing(0f, c.teal, c.teal, size = size, stroke = 2.5.dp) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(c.teal))
        }
        DayState.UPCOMING -> MiniRing(0f, c.teal, c.cardHigh, size = size, stroke = 2.5.dp)
        DayState.MISSED -> MiniRing(0f, c.teal, c.cardHigh, size = size, stroke = 2.5.dp) {
            Box(Modifier.size(4.dp).clip(CircleShape).background(c.textSecondary.copy(alpha = 0.5f)))
        }
        DayState.REST -> Box(Modifier.size(size).clip(CircleShape).background(c.blueTrack), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Bedtime, contentDescription = "Rest", tint = c.blue, modifier = Modifier.size(16.dp))
        }
        DayState.OFF -> Box(Modifier.size(size).clip(CircleShape).background(c.cardHigh.copy(alpha = 0.5f)))
    }
}

@Composable
private fun TodayCard(
    day: PlanDayWithItems?,
    exerciseNames: List<String>,
    hasOpenSession: Boolean,
    doneToday: Boolean,
    onStart: () -> Unit,
    onOpenPlan: () -> Unit,
) {
    val c = Fit.colors
    val isRest = day == null || day.day.isRest
    val noPlan = day == null
    FitCard(onClick = onOpenPlan) {
        Text("Today's workout", style = MaterialTheme.typography.labelLarge, color = c.textSecondary)
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (noPlan && !hasOpenSession) IconCircle(Icons.Rounded.Edit, c.teal, c.tealTrack)
            else if (isRest && !hasOpenSession) IconCircle(Icons.Rounded.Bedtime, c.blue, c.blueTrack)
            else IconCircle(Icons.Rounded.FitnessCenter, c.teal, c.tealTrack)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    when {
                        hasOpenSession -> "Workout in progress"
                        noPlan -> "No plan yet"
                        isRest -> "Rest day"
                        else -> day!!.day.title
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = c.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    when {
                        hasOpenSession -> "Pick up where you left off"
                        doneToday -> "Done for today. Nice work."
                        noPlan -> "Pick a starter plan, build your own, or ask the coach."
                        isRest -> day?.day?.note?.ifBlank { null } ?: "Recovery is part of training."
                        else -> "${exerciseNames.size} exercises · " + exerciseNames.take(3).joinToString(", ")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(12.dp))
            when {
                hasOpenSession -> PillButton("Resume", onClick = onStart)
                doneToday -> Icon(Icons.Rounded.Check, contentDescription = null, tint = c.teal)
                noPlan -> PillButton("Set up", onClick = onOpenPlan)
                !isRest -> PillButton("Start", onClick = onStart)
            }
        }
    }
}
