package com.forgequest.ai.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.forgequest.ai.domain.model.DailyTrendPoint
import com.forgequest.ai.domain.model.ExerciseAnalyticsMetric
import com.forgequest.ai.domain.model.ExerciseHistoryPoint
import com.forgequest.ai.domain.model.FlameHealthState
import com.forgequest.ai.domain.model.HunterRank
import com.forgequest.ai.domain.model.MuscleGroup
import com.forgequest.ai.domain.model.PrRecordCard
import com.forgequest.ai.domain.model.QuestClearSummary
import com.forgequest.ai.ui.theme.BackgroundCanvas
import com.forgequest.ai.ui.theme.MuscleHotCoral
import com.forgequest.ai.ui.theme.MuscleReadyTeal
import com.forgequest.ai.ui.theme.MuscleRestingBase
import com.forgequest.ai.ui.theme.MuscleWarmAmber
import com.forgequest.ai.ui.theme.OnPrimaryTeal
import com.forgequest.ai.ui.theme.PastelPurple
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
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Shared API Key Required Modal Bottom Sheet:
 * Prompts the user to connect their Gemini API key in Settings before using AI features.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiKeyRequiredBottomSheet(
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit
) {
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
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(PrimaryTealTrack),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Key,
                    contentDescription = null,
                    tint = PrimaryTeal,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "AI features require an API key",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Connect your Gemini API key in Settings to use AI features",
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onOpenSettings,
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
                    text = "Open Settings",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

/**
 * Inline Card variant for prompting the user to connect their Gemini API key in Settings.
 */
@Composable
fun ApiKeyRequiredInlineCard(
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(PrimaryTealTrack),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Key,
                        contentDescription = null,
                        tint = PrimaryTeal,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "AI features require an API key",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Connect your Gemini API key in Settings to use AI features",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onOpenSettings,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrimaryTeal,
                    contentColor = OnPrimaryTeal
                )
            ) {
                Text(
                    text = "Open Settings",
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

/**
 * Clean human-friendly rank title helper (avoids nerdy RPG jargon).
 */
fun cleanRankTitle(rank: HunterRank): String = when (rank) {
    HunterRank.E_RANK -> "Starter tier"
    HunterRank.D_RANK -> "Bronze tier"
    HunterRank.C_RANK -> "Silver tier"
    HunterRank.B_RANK -> "Gold tier"
    HunterRank.A_RANK -> "Platinum tier"
    HunterRank.S_RANK -> "Master tier"
}

/**
 * 1. HunterRankSummaryCard:
 * Displays the user's Hunter Rank emblem (E-Rank through S-Rank), Level, Total XP,
 * and a smooth #8AB4F8 XP progress bar inside a borderless #1E1F20 card.
 */
@Composable
fun HunterRankSummaryCard(
    totalXp: Long,
    level: Int,
    rankCode: String,
    modifier: Modifier = Modifier
) {
    val rank = remember(level) { HunterRank.forLevel(level) }
    val progressTarget = remember(totalXp) { HunterRank.xpProgressInCurrentLevel(totalXp) }
    val xpIntoLevel = remember(totalXp) { (totalXp % 500L).coerceAtLeast(0L) }

    val animatedProgress by animateFloatAsState(
        targetValue = progressTarget,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "xpBarAnimation"
    )

    val badgeColor = when (rank) {
        HunterRank.S_RANK -> WarmGold
        HunterRank.A_RANK -> MuscleHotCoral
        HunterRank.B_RANK -> PrimaryTeal
        HunterRank.C_RANK -> SecondaryBlue
        HunterRank.D_RANK -> PastelPurple
        HunterRank.E_RANK -> SecondaryBlue
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(SecondaryBlueTrack),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = rank.rankCode.first().toString(),
                            color = badgeColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = badgeColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${rank.rankCode} · Level $level",
                                style = MaterialTheme.typography.titleLarge,
                                color = TextPrimary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${cleanRankTitle(rank)} · $totalXp total XP",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(50),
                    color = SurfaceContainerHigh
                ) {
                    Text(
                        text = "$xpIntoLevel / 500 XP",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = SecondaryBlue
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(SecondaryBlueTrack)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedProgress.coerceIn(0.03f, 1f))
                        .height(8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(SecondaryBlue)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "$xpIntoLevel / 500 XP to Level ${level + 1}",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        }
    }
}

/**
 * 2. EmberToInfernoFlameCanvas:
 * Borderless Google Fit card rendering the 4-stage Workout Streak Flame.
 */
@Composable
fun EmberToInfernoFlameCanvas(
    flameState: FlameHealthState,
    streakDays: Int,
    longestStreakDays: Int,
    onManualRestCheckIn: () -> Unit,
    isRestCheckedInToday: Boolean,
    modifier: Modifier = Modifier
) {
    val accentColor = when (flameState) {
        FlameHealthState.INFERNO -> WarmGold
        FlameHealthState.FLICKERING -> WarmGold
        FlameHealthState.DYING_EMBER -> MuscleHotCoral
        FlameHealthState.EXTINGUISHED -> TextSecondary
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(BackgroundCanvas),
                    contentAlignment = Alignment.Center
                ) {
                    StreakFlameGlyph(flameState = flameState, modifier = Modifier.fillMaxSize())
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "${streakDays}d active streak",
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "${flameState.healthPercent}% health",
                            style = MaterialTheme.typography.bodyMedium,
                            color = accentColor,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = flameState.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Best streak: $longestStreakDays days",
                        style = MaterialTheme.typography.labelMedium,
                        color = PrimaryTeal
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onManualRestCheckIn,
                enabled = !isRestCheckedInToday,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRestCheckedInToday) PrimaryTealTrack else SurfaceContainerHigh,
                    contentColor = if (isRestCheckedInToday) PrimaryTeal else TextPrimary,
                    disabledContainerColor = PrimaryTealTrack,
                    disabledContentColor = PrimaryTeal
                )
            ) {
                Icon(
                    imageVector = if (isRestCheckedInToday) Icons.Default.Check else Icons.Default.LocalFireDepartment,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isRestCheckedInToday) {
                        "Rest day logged for today"
                    } else {
                        "Log unplanned rest day"
                    },
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

/**
 * 3. ZeroGuiltMacroRings:
 * Concentric Protein & Calorie rings with zero-guilt Warm Gold surplus transition.
 */
@Composable
fun ZeroGuiltMacroRings(
    loggedCalories: Int,
    dailyCalorieGoal: Int,
    loggedProteinGrams: Float,
    dailyProteinGoal: Int,
    enableNutritionStreak: Boolean,
    nutritionStreakDays: Int,
    modifier: Modifier = Modifier
) {
    val isCalorieSurplus = loggedCalories > dailyCalorieGoal
    val calorieRatio = (loggedCalories.toFloat() / max(1, dailyCalorieGoal).toFloat()).coerceIn(0f, 1f)
    val proteinRatio = (loggedProteinGrams / max(1, dailyProteinGoal).toFloat()).coerceIn(0f, 1f)

    val animatedCalorieSweep by animateFloatAsState(
        targetValue = calorieRatio * 360f,
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "calorieSweep"
    )

    val animatedProteinSweep by animateFloatAsState(
        targetValue = proteinRatio * 360f,
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "proteinSweep"
    )

    val innerColor by animateColorAsState(
        targetValue = if (isCalorieSurplus) WarmGold else SecondaryBlue,
        animationSpec = tween(450),
        label = "innerRingColor"
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(220.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokePx = 12.dp.toPx()
                val dotRadiusPx = 6.dp.toPx()
                val outerRadiusPx = 95.dp.toPx()
                val innerRadiusPx = 74.dp.toPx()

                // Outer Ring Track (Protein)
                drawCircle(
                    color = PrimaryTealTrack,
                    radius = outerRadiusPx,
                    center = center,
                    style = Stroke(width = strokePx)
                )

                if (loggedProteinGrams.roundToInt() <= 0) {
                    drawCircle(
                        color = PrimaryTeal,
                        radius = dotRadiusPx,
                        center = Offset(center.x, center.y - outerRadiusPx)
                    )
                } else {
                    drawArc(
                        color = PrimaryTeal,
                        startAngle = -90f,
                        sweepAngle = animatedProteinSweep.coerceAtLeast(4f),
                        useCenter = false,
                        topLeft = Offset(center.x - outerRadiusPx, center.y - outerRadiusPx),
                        size = Size(outerRadiusPx * 2f, outerRadiusPx * 2f),
                        style = Stroke(width = strokePx, cap = StrokeCap.Round)
                    )
                }

                // Inner Ring Track (Calories)
                drawCircle(
                    color = SecondaryBlueTrack,
                    radius = innerRadiusPx,
                    center = center,
                    style = Stroke(width = strokePx)
                )

                if (loggedCalories <= 0) {
                    drawCircle(
                        color = SecondaryBlue,
                        radius = dotRadiusPx,
                        center = Offset(center.x, center.y - innerRadiusPx)
                    )
                } else {
                    drawArc(
                        color = innerColor,
                        startAngle = -90f,
                        sweepAngle = animatedCalorieSweep.coerceAtLeast(4f),
                        useCenter = false,
                        topLeft = Offset(center.x - innerRadiusPx, center.y - innerRadiusPx),
                        size = Size(innerRadiusPx * 2f, innerRadiusPx * 2f),
                        style = Stroke(width = strokePx, cap = StrokeCap.Round)
                    )
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val proteinInt = loggedProteinGrams.roundToInt()
                Text(
                    text = if (proteinInt == 0) "0" else "${proteinInt}g",
                    fontSize = 36.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = PrimaryTeal
                )
                Text(
                    text = "$loggedCalories",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Medium,
                    color = innerColor
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
                    text = "$nutritionStreakDays day nutrition streak",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = WarmGold
                )
            }
        }
    }
}

/**
 * 4. AnatomicalMuscleHeatmapCanvas:
 * Wrapper card around [AnatomicalMuscleMapCanvas] with 4-stage recovery legend.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AnatomicalMuscleHeatmapCanvas(
    muscleIntensities: Map<MuscleGroup, Float>,
    modifier: Modifier = Modifier,
    title: String = "Muscle recovery"
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Last 48 hours",
                    style = MaterialTheme.typography.bodyMedium,
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
                RecoveryLegendDot(color = MuscleHotCoral, label = "Just trained")
                RecoveryLegendDot(color = MuscleWarmAmber, label = "Recovering")
                RecoveryLegendDot(color = MuscleReadyTeal, label = "Almost ready")
                RecoveryLegendDot(color = MuscleRestingBase, label = "Recovered")
            }
        }
    }
}

@Composable
private fun RecoveryLegendDot(color: Color, label: String) {
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
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )
    }
}

internal fun muscleHeatColor(intensity: Float): Color = muscleRecoveryColor(intensity)

/**
 * 5. MorphingNeonCheckbox:
 * Material 3 Expressive Morphing Checkbox that animates from a squircle into a #5CD6C0 circle on check
 * using bouncy spring physics (zero borders).
 */
@Composable
fun MorphingNeonCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val bouncySpring = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMedium
    )

    val cornerPercent by animateIntAsStateCompat(
        targetValue = if (checked) 50 else 28
    )

    val scale by animateFloatAsState(
        targetValue = if (checked) 1.06f else 1.0f,
        animationSpec = bouncySpring,
        label = "morphScale"
    )

    val bgColor by animateColorAsState(
        targetValue = if (checked) PrimaryTeal else SurfaceContainerHigh,
        animationSpec = tween(220),
        label = "morphBg"
    )

    Box(
        modifier = modifier
            .scale(scale)
            .size(38.dp)
            .clip(RoundedCornerShape(percent = cornerPercent))
            .background(bgColor)
            .clickable { onCheckedChange(!checked) },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = if (checked) "Completed" else "Mark complete",
            tint = if (checked) OnPrimaryTeal else TextSecondary,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun animateIntAsStateCompat(targetValue: Int) =
    animateFloatAsState(
        targetValue = targetValue.toFloat(),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "intSpring"
    ).let { state ->
        remember(state.value) { mutableIntStateOf(state.value.roundToInt()) }
    }

/**
 * 6A. ExerciseProgressionChartModal:
 * Interactive Compose Canvas line chart for any exercise with a metric selector pill bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseProgressionChartModal(
    exerciseName: String,
    historyPoints: List<ExerciseHistoryPoint>,
    onDismiss: () -> Unit
) {
    var selectedMetric by remember { mutableStateOf(ExerciseAnalyticsMetric.MAX_REPS) }
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
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = exerciseName,
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Progression & volume history",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ExerciseAnalyticsMetric.entries.forEach { metric ->
                    val selected = metric == selectedMetric
                    FilterChip(
                        selected = selected,
                        onClick = { selectedMetric = metric },
                        label = {
                            Text(
                                text = metric.displayName,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                            )
                        },
                        border = null,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = PrimaryTealTrack,
                            selectedLabelColor = PrimaryTeal,
                            containerColor = SurfaceContainerHigh,
                            labelColor = TextSecondary
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            val values = remember(historyPoints, selectedMetric) {
                historyPoints.map { pt ->
                    when (selectedMetric) {
                        ExerciseAnalyticsMetric.MAX_REPS -> pt.maxRepsSingleSet.toFloat()
                        ExerciseAnalyticsMetric.TOTAL_REPS -> pt.totalReps.toFloat()
                        ExerciseAnalyticsMetric.TOTAL_VOLUME -> pt.totalVolume
                        ExerciseAnalyticsMetric.MAX_HOLD_SECONDS -> pt.maxHoldSeconds.toFloat()
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(BackgroundCanvas)
                    .padding(16.dp)
            ) {
                if (values.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Complete your first workout with $exerciseName to see trend lines",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    NeonLineChartCanvas(
                        values = values,
                        lineColor = PrimaryTeal,
                        fillColor = PrimaryTeal.copy(alpha = 0.22f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (historyPoints.isNotEmpty()) {
                Text(
                    text = "Recent sessions",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(8.dp))
                historyPoints.takeLast(5).reversed().forEach { pt ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(SurfaceContainerHigh)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = pt.dateLabel,
                                style = MaterialTheme.typography.labelLarge,
                                color = TextPrimary
                            )
                            Text(
                                text = "Sets: ${pt.setsSummary}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                        Text(
                            text = "Max ${pt.maxRepsSingleSet} reps · Vol ${pt.totalVolume.roundToInt()}",
                            style = MaterialTheme.typography.labelMedium,
                            color = PrimaryTeal
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/**
 * 6B. MacroTrendsBottomSheet:
 * Dedicated 7-day trends and 30-day trends modal sheet displaying Calorie adherence,
 * Protein consistency, and Body Weight trend lines.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MacroTrendsBottomSheet(
    initialDaysRange: Int,
    trends7Day: List<DailyTrendPoint>,
    trends30Day: List<DailyTrendPoint>,
    calorieGoal: Int,
    proteinGoal: Int,
    onDismiss: () -> Unit
) {
    var selectedRange by remember { mutableIntStateOf(initialDaysRange) }
    val points = if (selectedRange == 7) trends7Day else trends30Day
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
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$selectedRange-day trends",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                    fontWeight = FontWeight.Medium
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = selectedRange == 7,
                    onClick = { selectedRange = 7 },
                    label = { Text("7-day trends") },
                    border = null,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = PrimaryTealTrack,
                        selectedLabelColor = PrimaryTeal,
                        containerColor = SurfaceContainerHigh,
                        labelColor = TextSecondary
                    )
                )
                FilterChip(
                    selected = selectedRange == 30,
                    onClick = { selectedRange = 30 },
                    label = { Text("30-day trends") },
                    border = null,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = PrimaryTealTrack,
                        selectedLabelColor = PrimaryTeal,
                        containerColor = SurfaceContainerHigh,
                        labelColor = TextSecondary
                    )
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 1. Protein Trend Chart
            Text(
                text = "Daily protein (Goal: ${proteinGoal}g)",
                style = MaterialTheme.typography.labelLarge,
                color = PrimaryTeal
            )
            Spacer(modifier = Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(BackgroundCanvas)
                    .padding(12.dp)
            ) {
                NeonLineChartCanvas(
                    values = points.map { it.proteinGrams },
                    lineColor = PrimaryTeal,
                    fillColor = PrimaryTeal.copy(alpha = 0.2f)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 2. Calorie Trend Chart
            Text(
                text = "Daily calories (Goal: $calorieGoal kcal)",
                style = MaterialTheme.typography.labelLarge,
                color = SecondaryBlue
            )
            Spacer(modifier = Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(BackgroundCanvas)
                    .padding(12.dp)
            ) {
                NeonLineChartCanvas(
                    values = points.map { it.calories.toFloat() },
                    lineColor = SecondaryBlue,
                    fillColor = SecondaryBlue.copy(alpha = 0.2f)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 3. Body Weight Trend Chart
            Text(
                text = "Body weight (kg)",
                style = MaterialTheme.typography.labelLarge,
                color = WarmGold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(BackgroundCanvas)
                    .padding(12.dp)
            ) {
                NeonLineChartCanvas(
                    values = points.map { it.weightKg },
                    lineColor = WarmGold,
                    fillColor = WarmGold.copy(alpha = 0.2f)
                )
            }

            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}

@Composable
fun NeonLineChartCanvas(
    values: List<Float>,
    lineColor: Color,
    fillColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        if (values.isEmpty()) return@Canvas
        val w = size.width
        val h = size.height
        val maxVal = (values.maxOrNull() ?: 1f).coerceAtLeast(1f)
        val minVal = (values.minOrNull() ?: 0f).coerceAtMost(maxVal - 1f)
        val range = (maxVal - minVal).coerceAtLeast(1f)

        val stepX = if (values.size > 1) w / (values.size - 1).toFloat() else w / 2f
        val coords = values.mapIndexed { idx, v ->
            val x = if (values.size > 1) idx * stepX else w / 2f
            val normalized = (v - minVal) / range
            val y = h - (normalized * (h * 0.8f) + h * 0.1f)
            Offset(x, y)
        }

        if (coords.size > 1) {
            val linePath = Path().apply {
                moveTo(coords.first().x, coords.first().y)
                for (i in 1 until coords.size) {
                    lineTo(coords[i].x, coords[i].y)
                }
            }
            val areaPath = Path().apply {
                addPath(linePath)
                lineTo(coords.last().x, h)
                lineTo(coords.first().x, h)
                close()
            }
            drawPath(
                path = areaPath,
                brush = Brush.verticalGradient(listOf(fillColor, Color.Transparent))
            )
            drawPath(
                path = linePath,
                color = lineColor,
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
            )
        }

        coords.forEach { pt ->
            drawCircle(
                color = BackgroundCanvas,
                radius = 5.dp.toPx(),
                center = pt
            )
            drawCircle(
                color = lineColor,
                radius = 3.5.dp.toPx(),
                center = pt
            )
        }
    }
}

/**
 * 7. QuestClearCelebrationDialog:
 * Celebratory "Workout complete" modal triggered ONLY when "Finish workout" is tapped:
 * - Animated XP tally & Hunter Rank progress bar
 * - Gold PR badges (#FDD663) with Canvas particle burst
 * - AnatomicalMuscleMapCanvas lighting up the exact muscles just trained in #F28B82
 */
@Composable
fun QuestClearCelebrationDialog(
    summary: QuestClearSummary,
    onDismiss: () -> Unit
) {
    val xpCounter = remember { Animatable(0f) }
    val confettiAnim = rememberInfiniteTransition(label = "confetti")
    val confettiProgress by confettiAnim.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "confettiFall"
    )

    LaunchedEffect(summary.earnedXp) {
        xpCounter.animateTo(
            targetValue = summary.earnedXp.toFloat(),
            animationSpec = tween(durationMillis = 1200, easing = FastOutSlowInEasing)
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(BackgroundCanvas.copy(alpha = 0.98f))
        ) {
            // Celebratory Confetti & Spark Particle Burst Canvas
            Canvas(modifier = Modifier.fillMaxSize()) {
                val particleColors = listOf(WarmGold, PrimaryTeal, SecondaryBlue, MuscleHotCoral, PastelPurple)
                for (i in 0 until 32) {
                    val seed = (i * 37) % 100 / 100f
                    val y = ((confettiProgress + seed) % 1f) * size.height
                    val x = ((i * 53) % 100 / 100f) * size.width +
                        sin((confettiProgress * 2 * PI + i).toFloat()) * 24f
                    drawCircle(
                        color = particleColors[i % particleColors.size].copy(alpha = 0.65f),
                        radius = (4 + (i % 4) * 2).toFloat(),
                        center = Offset(x, y)
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(12.dp))

                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(WarmAmberTrack),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.EmojiEvents,
                        contentDescription = null,
                        tint = WarmGold,
                        modifier = Modifier.size(38.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Workout complete",
                    style = MaterialTheme.typography.headlineLarge,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = summary.workoutTitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = PrimaryTeal
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (summary.reignitedFlame) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = WarmAmberTrack),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocalFireDepartment,
                                contentDescription = null,
                                tint = WarmGold,
                                modifier = Modifier.size(30.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Streak flame reignited",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = WarmGold,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Restored to 100% health · ${summary.newStreakDays}d active streak",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextPrimary
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                }

                // Animated XP Tally & Hunter Rank Progress Card
                val levelProgress = HunterRank.xpProgressInCurrentLevel(summary.newTotalXp)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "+${xpCounter.value.roundToInt()} XP",
                            fontSize = 30.sp,
                            color = PrimaryTeal,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${summary.newRank.rankCode} · Level ${summary.newLevel}",
                            style = MaterialTheme.typography.titleMedium,
                            color = SecondaryBlue
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(50))
                                .background(SecondaryBlueTrack)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(levelProgress.coerceIn(0.05f, 1f))
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(SecondaryBlue)
                            )
                        }
                        if (summary.newLevel > summary.previousLevel) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = WarmAmberTrack
                            ) {
                                Text(
                                    text = "Level up · Level ${summary.previousLevel} → Level ${summary.newLevel}",
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = WarmGold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Gold PR Reveal Cards
                if (summary.prCards.isNotEmpty()) {
                    Text(
                        text = "Personal records (${summary.prCards.size})",
                        style = MaterialTheme.typography.titleLarge,
                        color = WarmGold,
                        modifier = Modifier.align(Alignment.Start)
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    summary.prCards.forEach { pr ->
                        PrGoldRevealCard(pr = pr)
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Anatomical Muscle Map lighting up trained muscles in #F28B82
                AnatomicalMuscleHeatmapCanvas(
                    muscleIntensities = summary.ignitedMuscles,
                    title = "Muscles trained"
                )

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(27.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryTeal,
                        contentColor = OnPrimaryTeal
                    )
                ) {
                    Text(
                        text = "Done",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun PrGoldRevealCard(pr: PrRecordCard) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(WarmAmberTrack),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = WarmGold,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = pr.prType.badgeTitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = WarmGold
                    )
                    Text(
                        text = pr.exerciseName,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Previous: ${pr.previousValueLabel}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = WarmAmberTrack
            ) {
                Text(
                    text = pr.newValueLabel,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = WarmGold,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

/**
 * Reusable 4-stage streak flame drawing (INFERNO / FLICKERING / DYING_EMBER / EXTINGUISHED).
 * Uses pure Bezier paths and circles (zero rectangles).
 */
@Composable
fun StreakFlameGlyph(
    flameState: FlameHealthState,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "flamePulse")

    val pulseDuration = when (flameState) {
        FlameHealthState.INFERNO -> 750
        FlameHealthState.FLICKERING -> 1400
        FlameHealthState.DYING_EMBER -> 2100
        FlameHealthState.EXTINGUISHED -> 3000
    }

    val pulsePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(pulseDuration, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulsePhase"
    )

    val particleTime by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "particleTime"
    )

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val baseY = h * 0.82f

        when (flameState) {
            FlameHealthState.INFERNO -> {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            WarmGold.copy(alpha = 0.38f + 0.14f * pulsePhase),
                            PrimaryTeal.copy(alpha = 0.14f),
                            Color.Transparent
                        ),
                        center = Offset(cx, h * 0.52f),
                        radius = w * 0.48f
                    ),
                    center = Offset(cx, h * 0.52f),
                    radius = w * 0.48f
                )

                val outerHeight = h * (0.64f + 0.08f * pulsePhase)
                val outerPath = Path().apply {
                    moveTo(cx, baseY - outerHeight)
                    cubicTo(
                        cx + w * 0.34f, baseY - outerHeight * 0.55f,
                        cx + w * 0.28f, baseY - outerHeight * 0.1f,
                        cx, baseY
                    )
                    cubicTo(
                        cx - w * 0.28f, baseY - outerHeight * 0.1f,
                        cx - w * 0.34f, baseY - outerHeight * 0.55f,
                        cx, baseY - outerHeight
                    )
                    close()
                }
                drawPath(
                    path = outerPath,
                    brush = Brush.verticalGradient(
                        listOf(WarmGold, MuscleHotCoral)
                    )
                )

                val coreHeight = outerHeight * (0.58f + 0.06f * (1f - pulsePhase))
                val corePath = Path().apply {
                    moveTo(cx, baseY - coreHeight)
                    cubicTo(
                        cx + w * 0.17f, baseY - coreHeight * 0.5f,
                        cx + w * 0.15f, baseY - coreHeight * 0.1f,
                        cx, baseY - 4f
                    )
                    cubicTo(
                        cx - w * 0.15f, baseY - coreHeight * 0.1f,
                        cx - w * 0.17f, baseY - coreHeight * 0.5f,
                        cx, baseY - coreHeight
                    )
                    close()
                }
                drawPath(
                    path = corePath,
                    brush = Brush.verticalGradient(
                        listOf(Color.White, PrimaryTeal, WarmGold)
                    )
                )

                for (i in 0 until 6) {
                    val sparkProgress = (particleTime + i * 0.17f) % 1f
                    val sparkX = cx + sin((sparkProgress * 2 * PI + i).toFloat()) * (w * 0.22f)
                    val sparkY = baseY - sparkProgress * (h * 0.72f)
                    val sparkAlpha = (1f - sparkProgress).coerceIn(0f, 1f)
                    drawCircle(
                        color = if (i % 2 == 0) PrimaryTeal.copy(alpha = sparkAlpha)
                        else WarmGold.copy(alpha = sparkAlpha),
                        radius = 3.2f * (1f - sparkProgress * 0.5f),
                        center = Offset(sparkX, sparkY)
                    )
                }
            }

            FlameHealthState.FLICKERING -> {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            WarmGold.copy(alpha = 0.32f + 0.1f * pulsePhase),
                            Color.Transparent
                        ),
                        center = Offset(cx, h * 0.6f),
                        radius = w * 0.38f
                    ),
                    center = Offset(cx, h * 0.6f),
                    radius = w * 0.38f
                )

                val sway = (pulsePhase - 0.5f) * (w * 0.08f)
                val flameH = h * (0.46f + 0.05f * pulsePhase)
                val amberPath = Path().apply {
                    moveTo(cx + sway, baseY - flameH)
                    cubicTo(
                        cx + w * 0.24f, baseY - flameH * 0.5f,
                        cx + w * 0.20f, baseY - flameH * 0.1f,
                        cx, baseY
                    )
                    cubicTo(
                        cx - w * 0.20f, baseY - flameH * 0.1f,
                        cx - w * 0.24f, baseY - flameH * 0.5f,
                        cx + sway, baseY - flameH
                    )
                    close()
                }
                drawPath(
                    path = amberPath,
                    brush = Brush.verticalGradient(
                        listOf(WarmGold, MuscleHotCoral)
                    )
                )
            }

            FlameHealthState.DYING_EMBER -> {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            MuscleHotCoral.copy(alpha = 0.35f + 0.2f * pulsePhase),
                            Color.Transparent
                        ),
                        center = Offset(cx, baseY - 8f),
                        radius = w * 0.32f
                    ),
                    center = Offset(cx, baseY - 8f),
                    radius = w * 0.32f
                )

                val emberPath = Path().apply {
                    moveTo(cx - w * 0.22f, baseY - 6f)
                    quadraticTo(cx, baseY - 18f, cx + w * 0.22f, baseY - 6f)
                    quadraticTo(cx, baseY + 4f, cx - w * 0.22f, baseY - 6f)
                    close()
                }
                drawPath(
                    path = emberPath,
                    brush = Brush.horizontalGradient(
                        listOf(MuscleHotCoral, WarmGold, MuscleHotCoral)
                    )
                )

                for (i in 0 until 5) {
                    val smokeProg = (particleTime + i * 0.2f) % 1f
                    val smokeX = cx + sin((smokeProg * 2 * PI + i).toFloat()) * (w * 0.16f)
                    val smokeY = (baseY - 16f) - smokeProg * (h * 0.55f)
                    drawCircle(
                        color = TextSecondary.copy(alpha = (1f - smokeProg) * 0.55f),
                        radius = 4f + smokeProg * 6f,
                        center = Offset(smokeX, smokeY)
                    )
                }
            }

            FlameHealthState.EXTINGUISHED -> {
                val ashPath = Path().apply {
                    moveTo(cx - w * 0.30f, baseY)
                    quadraticTo(cx - w * 0.15f, baseY - 22f, cx, baseY - 26f)
                    quadraticTo(cx + w * 0.15f, baseY - 22f, cx + w * 0.30f, baseY)
                    close()
                }
                drawPath(
                    path = ashPath,
                    brush = Brush.verticalGradient(
                        listOf(TextSecondary, SurfaceContainerHigh)
                    )
                )
                drawLine(
                    color = SurfaceContainerHigh,
                    start = Offset(cx - 18f, baseY - 4f),
                    end = Offset(cx + 18f, baseY - 12f),
                    strokeWidth = 5f,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}
