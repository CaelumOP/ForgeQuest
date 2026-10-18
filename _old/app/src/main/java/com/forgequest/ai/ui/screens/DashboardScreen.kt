package com.forgequest.ai.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.forgequest.ai.data.local.entity.MealLogEntity
import com.forgequest.ai.data.local.entity.PlannedDayEntity
import com.forgequest.ai.data.local.entity.PlannedExerciseEntity
import com.forgequest.ai.data.local.entity.UserProfileEntity
import com.forgequest.ai.data.local.entity.WorkoutSessionEntity
import com.forgequest.ai.domain.model.AppSettings
import com.forgequest.ai.domain.model.DailyTrendPoint
import com.forgequest.ai.domain.model.FlameHealthState
import com.forgequest.ai.domain.model.HunterRank
import com.forgequest.ai.domain.model.MuscleGroup
import com.forgequest.ai.ui.components.AnatomicalMuscleMapCanvas
import com.forgequest.ai.ui.components.MacroTrendsBottomSheet
import com.forgequest.ai.ui.components.StreakFlameGlyph
import com.forgequest.ai.ui.haptics.HapticsManager
import com.forgequest.ai.ui.theme.BackgroundCanvas
import com.forgequest.ai.ui.theme.MuscleHotCoral
import com.forgequest.ai.ui.theme.MuscleReadyTeal
import com.forgequest.ai.ui.theme.MuscleRestingBase
import com.forgequest.ai.ui.theme.MuscleWarmAmber
import com.forgequest.ai.ui.theme.OnPrimaryTeal
import com.forgequest.ai.ui.theme.PrimaryTeal
import com.forgequest.ai.ui.theme.PrimaryTealTrack
import com.forgequest.ai.ui.theme.SecondaryBlue
import com.forgequest.ai.ui.theme.SecondaryBlueTrack
import com.forgequest.ai.ui.theme.SurfaceCard
import com.forgequest.ai.ui.theme.SurfaceContainerHigh
import com.forgequest.ai.ui.theme.TextPrimary
import com.forgequest.ai.ui.theme.TextSecondary
import com.forgequest.ai.ui.theme.WarmAmberTrack
import com.forgequest.ai.ui.theme.WarmGold
import java.text.NumberFormat
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

private val ScreenHorizontalPadding = 16.dp
private val CardVerticalSpacing = 12.dp
private val CardInternalPadding = 20.dp
private val GoogleFitCardShape = RoundedCornerShape(16.dp)

@Composable
fun DashboardScreen(
    userProfile: UserProfileEntity,
    settings: AppSettings,
    todayPlannedDay: PlannedDayEntity?,
    todayExercises: List<PlannedExerciseEntity>,
    todayMealLogs: List<MealLogEntity>,
    muscleIntensities: Map<MuscleGroup, Float>,
    trends7Day: List<DailyTrendPoint>,
    trends30Day: List<DailyTrendPoint>,
    haptics: HapticsManager?,
    onManualRestCheckIn: () -> Unit,
    onLogBodyWeight: (Float) -> Unit,
    onNavigateToWorkout: () -> Unit,
    onNavigateToFoodVault: () -> Unit,
    plannedDays: List<PlannedDayEntity> = emptyList(),
    completedSessions: List<WorkoutSessionEntity> = emptyList(),
    onNavigateToProfile: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val today = remember { LocalDate.now() }
    val isRestCheckedInToday = userProfile.lastRestCheckInEpochDay == today.toEpochDay()

    val loggedCalories = remember(todayMealLogs) { todayMealLogs.sumOf { it.totalCalories } }
    val loggedProtein = remember(todayMealLogs) {
        todayMealLogs.sumOf { it.totalProteinGrams.toDouble() }.toFloat()
    }

    var showTrendsSheet by remember { mutableStateOf(false) }
    var trendsInitialDays by remember { mutableIntStateOf(7) }
    var showWeightSheet by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundCanvas),
        contentPadding = PaddingValues(
            start = ScreenHorizontalPadding,
            end = ScreenHorizontalPadding,
            top = 12.dp,
            bottom = 32.dp
        ),
        verticalArrangement = Arrangement.spacedBy(CardVerticalSpacing)
    ) {
        // 1. Top App Bar
        item {
            DashboardTopBar(
                today = today,
                totalXp = userProfile.totalXp,
                level = userProfile.level,
                onClickProfile = {
                    haptics?.tick()
                    onNavigateToProfile()
                }
            )
        }

        // 2. Hero Floating Concentric Rings + Legend + 3-Column Stat Row (Direct on #131314 Canvas)
        item {
            HeroConcentricRingsSection(
                loggedCalories = loggedCalories,
                dailyCalorieGoal = settings.dailyCalorieGoal,
                loggedProteinGrams = loggedProtein,
                dailyProteinGoal = settings.dailyProteinGoal,
                currentWeightKg = userProfile.currentWeightKg,
                enableNutritionStreak = settings.enableDailyNutritionStreak,
                nutritionStreakDays = userProfile.currentNutritionStreakDays,
                onRingClick = {
                    haptics?.tick()
                    onNavigateToFoodVault()
                },
                onWeightColumnClick = {
                    haptics?.tick()
                    showWeightSheet = true
                }
            )
        }

        // 3. Card 1 — Your workout streak (Google Fit 7-Day Ring Layout)
        item {
            WorkoutStreakCard(
                today = today,
                flameState = userProfile.flameHealthState,
                streakDays = userProfile.currentWorkoutStreakDays,
                isRestCheckedInToday = isRestCheckedInToday,
                lastRestCheckInEpochDay = userProfile.lastRestCheckInEpochDay,
                todayIsRestDay = todayPlannedDay?.isRestDay == true,
                plannedDays = plannedDays,
                completedSessions = completedSessions,
                onRestCheckIn = {
                    haptics?.tick()
                    onManualRestCheckIn()
                }
            )
        }

        // 4. Card 2 — Today's workout
        item {
            TodayWorkoutCard(
                plannedDay = todayPlannedDay,
                exercises = todayExercises,
                onPrimaryAction = {
                    haptics?.tick()
                    onNavigateToWorkout()
                }
            )
        }

        // 5. Card 3 — Muscle recovery (Hyper-Detailed Anatomical Muscle Map)
        item {
            MuscleRecoveryCard(muscleIntensities = muscleIntensities)
        }

        // 6. Trends Row (7-day trends & 30-day trends)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(CardVerticalSpacing)
            ) {
                TrendSummaryCard(
                    title = "7-day trends",
                    subtitle = "Protein, cal & weight",
                    icon = Icons.AutoMirrored.Filled.ShowChart,
                    iconTint = PrimaryTeal,
                    badgeTrack = PrimaryTealTrack,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        haptics?.tick()
                        trendsInitialDays = 7
                        showTrendsSheet = true
                    }
                )
                TrendSummaryCard(
                    title = "30-day trends",
                    subtitle = "Protein, cal & weight",
                    icon = Icons.Default.CalendarMonth,
                    iconTint = SecondaryBlue,
                    badgeTrack = SecondaryBlueTrack,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        haptics?.tick()
                        trendsInitialDays = 30
                        showTrendsSheet = true
                    }
                )
            }
        }
    }

    if (showWeightSheet) {
        BodyWeightQuickLogSheet(
            savedWeightKg = userProfile.currentWeightKg,
            haptics = haptics,
            onSave = { newWeight ->
                onLogBodyWeight(newWeight)
                showWeightSheet = false
            },
            onDismiss = { showWeightSheet = false }
        )
    }

    if (showTrendsSheet) {
        MacroTrendsBottomSheet(
            initialDaysRange = trendsInitialDays,
            trends7Day = trends7Day,
            trends30Day = trends30Day,
            calorieGoal = settings.dailyCalorieGoal,
            proteinGoal = settings.dailyProteinGoal,
            onDismiss = { showTrendsSheet = false }
        )
    }
}

// ---------------------------------------------------------------------------------------------
// 1. Top App Bar
// ---------------------------------------------------------------------------------------------

@Composable
private fun DashboardTopBar(
    today: LocalDate,
    totalXp: Long,
    level: Int,
    onClickProfile: () -> Unit
) {
    val greeting = remember {
        when (LocalTime.now().hour) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            else -> "Good evening"
        }
    }
    val dateLabel = remember(today) {
        today.format(DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.getDefault()))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = greeting,
                fontSize = 22.sp,
                fontWeight = FontWeight.Normal,
                color = TextPrimary,
                modifier = Modifier.semantics { heading() }
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = dateLabel,
                fontSize = 13.sp,
                fontWeight = FontWeight.Normal,
                color = TextSecondary
            )
        }

        RankBadgeAvatar(
            totalXp = totalXp,
            level = level,
            onClick = onClickProfile
        )
    }
}

@Composable
private fun RankBadgeAvatar(
    totalXp: Long,
    level: Int,
    onClick: () -> Unit
) {
    val rank = HunterRank.forLevel(level)
    val progress by animateFloatAsState(
        targetValue = HunterRank.xpProgressInCurrentLevel(totalXp),
        animationSpec = tween(700, easing = FastOutSlowInEasing),
        label = "avatarXpProgress"
    )
    val rankLetter = rank.rankCode.first().toString()

    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = "Profile rank $rankLetter, level $level"
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 2.5.dp.toPx()
            val diameter = size.minDimension - stroke
            val topLeft = Offset(stroke / 2f, stroke / 2f)
            drawArc(
                color = SecondaryBlueTrack,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = Size(diameter, diameter),
                style = Stroke(width = stroke)
            )
            drawArc(
                color = SecondaryBlue,
                startAngle = -90f,
                sweepAngle = (360f * progress).coerceAtLeast(18f),
                useCenter = false,
                topLeft = topLeft,
                size = Size(diameter, diameter),
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
        }
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(SurfaceCard),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = rankLetter,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = SecondaryBlue
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// 2. Hero Floating Concentric Rings (Direct on #131314 Canvas — NOT inside a card)
// ---------------------------------------------------------------------------------------------

@Composable
private fun HeroConcentricRingsSection(
    loggedCalories: Int,
    dailyCalorieGoal: Int,
    loggedProteinGrams: Float,
    dailyProteinGoal: Int,
    currentWeightKg: Float,
    enableNutritionStreak: Boolean,
    nutritionStreakDays: Int,
    onRingClick: () -> Unit,
    onWeightColumnClick: () -> Unit
) {
    val proteinRounded = loggedProteinGrams.roundToInt()
    val isCalorieSurplus = loggedCalories > dailyCalorieGoal

    val proteinProgress by animateFloatAsState(
        targetValue = (loggedProteinGrams / max(1, dailyProteinGoal).toFloat()).coerceIn(0f, 1f),
        animationSpec = tween(700, easing = FastOutSlowInEasing),
        label = "proteinRingSweep"
    )
    val calorieProgress by animateFloatAsState(
        targetValue = (loggedCalories.toFloat() / max(1, dailyCalorieGoal).toFloat()).coerceIn(0f, 1f),
        animationSpec = tween(700, easing = FastOutSlowInEasing),
        label = "calorieRingSweep"
    )

    val innerRingColor by animateColorAsState(
        targetValue = if (isCalorieSurplus) WarmGold else SecondaryBlue,
        animationSpec = tween(450),
        label = "innerRingColor"
    )

    val proteinLeft = (dailyProteinGoal - proteinRounded).coerceAtLeast(0)
    val calorieDelta = dailyCalorieGoal - loggedCalories

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 220.dp x 220.dp Custom Concentric Rings Canvas
        Box(
            modifier = Modifier
                .size(220.dp)
                .clip(CircleShape)
                .clickable(onClick = onRingClick)
                .semantics {
                    contentDescription = "Protein $proteinRounded of $dailyProteinGoal grams. Calories $loggedCalories of $dailyCalorieGoal."
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidthPx = 12.dp.toPx()
                val dotRadiusPx = 6.dp.toPx()
                val outerRadiusPx = 95.dp.toPx()
                val innerRadiusPx = 74.dp.toPx()

                // 1. Outer Ring Track (Protein)
                drawCircle(
                    color = PrimaryTealTrack,
                    radius = outerRadiusPx,
                    center = center,
                    style = Stroke(width = strokeWidthPx)
                )

                // Zero-State 12 o'clock Dot vs Non-Zero Progress Arc (Protein)
                if (proteinRounded <= 0) {
                    drawCircle(
                        color = PrimaryTeal,
                        radius = dotRadiusPx,
                        center = Offset(center.x, center.y - outerRadiusPx)
                    )
                } else {
                    drawArc(
                        color = PrimaryTeal,
                        startAngle = -90f,
                        sweepAngle = (proteinProgress * 360f).coerceAtLeast(4f),
                        useCenter = false,
                        topLeft = Offset(center.x - outerRadiusPx, center.y - outerRadiusPx),
                        size = Size(outerRadiusPx * 2f, outerRadiusPx * 2f),
                        style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
                    )
                }

                // 2. Inner Ring Track (Calories)
                drawCircle(
                    color = SecondaryBlueTrack,
                    radius = innerRadiusPx,
                    center = center,
                    style = Stroke(width = strokeWidthPx)
                )

                // Zero-State 12 o'clock Dot vs Non-Zero Progress Arc (Calories)
                if (loggedCalories <= 0) {
                    drawCircle(
                        color = SecondaryBlue,
                        radius = dotRadiusPx,
                        center = Offset(center.x, center.y - innerRadiusPx)
                    )
                } else {
                    drawArc(
                        color = innerRingColor,
                        startAngle = -90f,
                        sweepAngle = (calorieProgress * 360f).coerceAtLeast(4f),
                        useCenter = false,
                        topLeft = Offset(center.x - innerRadiusPx, center.y - innerRadiusPx),
                        size = Size(innerRadiusPx * 2f, innerRadiusPx * 2f),
                        style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
                    )
                }
            }

            // Center Stack Inside the Rings
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (proteinRounded == 0) "0" else "${proteinRounded}g",
                    fontSize = 36.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = PrimaryTeal
                )
                Text(
                    text = if (loggedCalories == 0) "0" else formatInt(loggedCalories),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Medium,
                    color = innerRingColor
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Legend Row Beneath Rings
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.FitnessCenter,
                    contentDescription = null,
                    tint = PrimaryTeal,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Protein",
                    fontSize = 13.sp,
                    color = TextPrimary,
                    fontWeight = FontWeight.Medium
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Restaurant,
                    contentDescription = null,
                    tint = innerRingColor,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Calories",
                    fontSize = 13.sp,
                    color = TextPrimary,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        if (enableNutritionStreak) {
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                shape = RoundedCornerShape(50),
                color = WarmAmberTrack
            ) {
                Text(
                    text = "${nutritionStreakDays}d nutrition streak",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    fontSize = 12.sp,
                    color = WarmGold,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // 3-Column Stat Summary Row (Google Fit Cal | km | Move Min style)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Column 1: Protein left
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "$proteinLeft g",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                    color = SecondaryBlue
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Protein left",
                    fontSize = 13.sp,
                    color = TextSecondary
                )
            }

            // Column 2: Cal left (or Cal surplus in WarmGold)
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val calValue = if (isCalorieSurplus) {
                    "+${formatInt(-calorieDelta)}"
                } else {
                    formatInt(calorieDelta.coerceAtLeast(0))
                }
                Text(
                    text = calValue,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isCalorieSurplus) WarmGold else SecondaryBlue
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (isCalorieSurplus) "Cal surplus" else "Cal left",
                    fontSize = 13.sp,
                    color = if (isCalorieSurplus) WarmGold else TextSecondary
                )
            }

            // Column 3: Tappable Body Weight (opens Quick-Log Bottom Sheet)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onWeightColumnClick)
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = formatWeight(currentWeightKg),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                    color = SecondaryBlue
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "kg",
                    fontSize = 13.sp,
                    color = TextSecondary
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// 3. Card 1 — Your workout streak (Google Fit 7-Day Ring Layout)
// ---------------------------------------------------------------------------------------------

@Composable
private fun WorkoutStreakCard(
    today: LocalDate,
    flameState: FlameHealthState,
    streakDays: Int,
    isRestCheckedInToday: Boolean,
    lastRestCheckInEpochDay: Long?,
    todayIsRestDay: Boolean,
    plannedDays: List<PlannedDayEntity>,
    completedSessions: List<WorkoutSessionEntity>,
    onRestCheckIn: () -> Unit
) {
    val accent = when (flameState) {
        FlameHealthState.INFERNO -> WarmGold
        FlameHealthState.FLICKERING -> WarmGold
        FlameHealthState.DYING_EMBER -> MuscleHotCoral
        FlameHealthState.EXTINGUISHED -> TextSecondary
    }
    val flameStateTitle = when (flameState) {
        FlameHealthState.INFERNO -> "Inferno"
        FlameHealthState.FLICKERING -> "Flickering"
        FlameHealthState.DYING_EMBER -> "Dying ember"
        FlameHealthState.EXTINGUISHED -> "Extinguished"
    }

    val startOfWeek = remember(today) {
        today.minusDays((today.dayOfWeek.value - 1).toLong())
    }
    val dayLetters = listOf("M", "T", "W", "T", "F", "S", "S")

    GoogleFitCard {
        // Header row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Your workout streak",
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary,
                modifier = Modifier.semantics { heading() }
            )
            Text(
                text = "$flameStateTitle · ${flameState.healthPercent}% health",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = accent
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Body row: Left Flame + Count, Right 7 Mini Circular Rings (M T W T F S S)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(WarmAmberTrack),
                    contentAlignment = Alignment.Center
                ) {
                    StreakFlameGlyph(
                        flameState = flameState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(6.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "${streakDays}d",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary
                    )
                    Text(
                        text = "Active streak",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                }
            }

            // 7 Mini Circular Rings
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (dayIdx in 1..7) {
                    val dateForDay = startOfWeek.plusDays((dayIdx - 1).toLong())
                    val epochForDay = dateForDay.toEpochDay()
                    val isCompleted = completedSessions.any { it.epochDay == epochForDay && it.isSubmitted }
                    val plannedDay = plannedDays.find { it.dayOfWeek == dayIdx }
                    val isScheduledRest = plannedDay?.isRestDay == true || (lastRestCheckInEpochDay == epochForDay)
                    val isPast = dateForDay.isBefore(today)

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        MiniDayRing(
                            isCompleted = isCompleted,
                            isRestDay = isScheduledRest,
                            isPastMissed = isPast && !isCompleted && !isScheduledRest
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = dayLetters[dayIdx - 1],
                            fontSize = 11.sp,
                            color = if (dayIdx == today.dayOfWeek.value) TextPrimary else TextSecondary,
                            fontWeight = if (dayIdx == today.dayOfWeek.value) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }

        // Footer action: Subtle text button "Log unplanned rest day" if today is a workout day and not checked in
        if (!todayIsRestDay) {
            Spacer(modifier = Modifier.height(10.dp))
            if (isRestCheckedInToday) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = PrimaryTeal,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Rest day logged — your streak is protected today",
                        fontSize = 13.sp,
                        color = PrimaryTeal
                    )
                }
            } else {
                TextButton(
                    onClick = onRestCheckIn,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Bedtime,
                        contentDescription = null,
                        tint = SecondaryBlue,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Log unplanned rest day",
                        fontSize = 13.sp,
                        color = SecondaryBlue,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun MiniDayRing(
    isCompleted: Boolean,
    isRestDay: Boolean,
    isPastMissed: Boolean
) {
    Box(
        modifier = Modifier.size(28.dp),
        contentAlignment = Alignment.Center
    ) {
        when {
            isCompleted -> {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(PrimaryTeal),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Completed",
                        tint = OnPrimaryTeal,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            isRestDay -> {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(
                        color = SecondaryBlueTrack,
                        radius = size.minDimension / 2f
                    )
                    drawCircle(
                        color = SecondaryBlue,
                        radius = (size.minDimension - 2.dp.toPx()) / 2f,
                        style = Stroke(width = 2.dp.toPx())
                    )
                }
                Icon(
                    imageVector = Icons.Default.Bedtime,
                    contentDescription = "Rest day",
                    tint = SecondaryBlue,
                    modifier = Modifier.size(13.dp)
                )
            }
            isPastMissed -> {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(
                        color = WarmAmberTrack,
                        radius = size.minDimension / 2f
                    )
                    drawCircle(
                        color = WarmGold.copy(alpha = 0.7f),
                        radius = (size.minDimension - 2.dp.toPx()) / 2f,
                        style = Stroke(width = 2.dp.toPx())
                    )
                }
            }
            else -> {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(
                        color = SurfaceContainerHigh,
                        radius = (size.minDimension - 2.5.dp.toPx()) / 2f,
                        style = Stroke(width = 2.5.dp.toPx())
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// 4. Card 2 — Today's workout
// ---------------------------------------------------------------------------------------------

@Composable
private fun TodayWorkoutCard(
    plannedDay: PlannedDayEntity?,
    exercises: List<PlannedExerciseEntity>,
    onPrimaryAction: () -> Unit
) {
    val isRestDay = plannedDay?.isRestDay == true
    val totalSets = exercises.sumOf { it.targetSets }
    val estimatedXp = totalSets * 25 + 150

    val title = when {
        plannedDay == null -> "Today's workout"
        isRestDay -> "Rest day"
        else -> plannedDay.title
    }
    val subtitle = when {
        plannedDay == null -> "Open workouts to configure your weekly plan"
        isRestDay -> "Streak protected automatically"
        else -> "${exercises.size} exercises · +$estimatedXp XP"
    }

    GoogleFitCard {
        Text(
            text = "Today's workout",
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            color = TextPrimary,
            modifier = Modifier.semantics { heading() }
        )

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left 48.dp circular pastel badge (#1C2E2B fill with #5CD6C0 icon)
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(PrimaryTealTrack),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isRestDay) Icons.Default.SelfImprovement else Icons.Default.FitnessCenter,
                    contentDescription = null,
                    tint = PrimaryTeal,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Middle: Day title and subtitle
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 13.sp,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Right: Pill button ("Start" in #5CD6C0 with dark #00382E text, or "Plan" on rest days)
            Button(
                onClick = onPrimaryAction,
                shape = RoundedCornerShape(24.dp),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrimaryTeal,
                    contentColor = OnPrimaryTeal
                )
            ) {
                Text(
                    text = if (isRestDay || plannedDay == null) "Plan" else "Start",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// 5. Card 3 — Muscle recovery
// ---------------------------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MuscleRecoveryCard(muscleIntensities: Map<MuscleGroup, Float>) {
    GoogleFitCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Muscle recovery",
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary,
                modifier = Modifier.semantics { heading() }
            )
            Text(
                text = "Last 48 hours",
                fontSize = 13.sp,
                color = TextSecondary
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        AnatomicalMuscleMapCanvas(muscleHeatMap = muscleIntensities)

        Spacer(modifier = Modifier.height(14.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            LegendItem(color = MuscleHotCoral, label = "Just trained")
            LegendItem(color = MuscleWarmAmber, label = "Recovering")
            LegendItem(color = MuscleReadyTeal, label = "Almost ready")
            LegendItem(color = MuscleRestingBase, label = "Recovered")
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            color = TextSecondary
        )
    }
}

// ---------------------------------------------------------------------------------------------
// 6. Trends Row (7-day trends & 30-day trends)
// ---------------------------------------------------------------------------------------------

@Composable
private fun TrendSummaryCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconTint: Color,
    badgeTrack: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = GoogleFitCardShape,
        color = SurfaceCard
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(badgeTrack),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = TextSecondary
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Tappable Body Weight Modal Bottom Sheet
// ---------------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BodyWeightQuickLogSheet(
    savedWeightKg: Float,
    haptics: HapticsManager?,
    onSave: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    var draftWeight by remember(savedWeightKg) { mutableFloatStateOf(savedWeightKg) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SurfaceCard,
        contentColor = TextPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Log body weight",
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Adjust in 0.1 kg increments",
                fontSize = 13.sp,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                FilledTonalIconButton(
                    onClick = {
                        haptics?.tick()
                        draftWeight = roundToTenth(draftWeight - 0.1f).coerceAtLeast(30f)
                    },
                    modifier = Modifier.size(52.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = SurfaceContainerHigh,
                        contentColor = TextPrimary
                    )
                ) {
                    Icon(Icons.Default.Remove, contentDescription = "Decrease weight")
                }

                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = formatWeight(draftWeight),
                        style = TextStyle(
                            fontSize = 36.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = SecondaryBlue
                        )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "kg",
                        fontSize = 16.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }

                FilledTonalIconButton(
                    onClick = {
                        haptics?.tick()
                        draftWeight = roundToTenth(draftWeight + 0.1f).coerceAtMost(250f)
                    },
                    modifier = Modifier.size(52.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = SurfaceContainerHigh,
                        contentColor = PrimaryTeal
                    )
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Increase weight")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    haptics?.setComplete()
                    onSave(draftWeight)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(26.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrimaryTeal,
                    contentColor = OnPrimaryTeal
                )
            ) {
                Text(
                    text = "Save",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Borderless Google Fit Card Container
// ---------------------------------------------------------------------------------------------

@Composable
private fun GoogleFitCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = GoogleFitCardShape,
        color = SurfaceCard
    ) {
        Column(
            modifier = Modifier.padding(CardInternalPadding),
            content = content
        )
    }
}

private fun formatInt(value: Int): String = NumberFormat.getIntegerInstance().format(value)

private fun formatWeight(kg: Float): String = String.format(Locale.getDefault(), "%.1f", kg)

private fun roundToTenth(value: Float): Float = (value * 10f).roundToInt() / 10f
