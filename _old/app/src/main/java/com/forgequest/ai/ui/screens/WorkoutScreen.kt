package com.forgequest.ai.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.OndemandVideo
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.forgequest.ai.data.local.entity.LoggedSetEntity
import com.forgequest.ai.data.local.entity.PlannedDayEntity
import com.forgequest.ai.data.local.entity.PlannedExerciseEntity
import com.forgequest.ai.data.local.entity.WorkoutSessionEntity
import com.forgequest.ai.domain.model.ExerciseCategory
import com.forgequest.ai.domain.model.ExerciseHistoryPoint
import com.forgequest.ai.domain.model.MuscleGroup
import com.forgequest.ai.domain.model.QuestClearSummary
import com.forgequest.ai.ui.components.ExerciseProgressionChartModal
import com.forgequest.ai.ui.components.MorphingNeonCheckbox
import com.forgequest.ai.ui.components.QuestClearCelebrationDialog
import com.forgequest.ai.ui.haptics.HapticsManager
import com.forgequest.ai.ui.theme.BackgroundCanvas
import com.forgequest.ai.ui.theme.OnPrimaryTeal
import com.forgequest.ai.ui.theme.PrimaryTeal
import com.forgequest.ai.ui.theme.PrimaryTealTrack
import com.forgequest.ai.ui.theme.SecondaryBlue
import com.forgequest.ai.ui.theme.SecondaryBlueTrack
import com.forgequest.ai.ui.theme.SurfaceCard
import com.forgequest.ai.ui.theme.SurfaceContainerHigh
import com.forgequest.ai.ui.theme.TextPrimary
import com.forgequest.ai.ui.theme.TextSecondary
import com.forgequest.ai.ui.theme.WarmGold
import kotlinx.coroutines.delay
import java.net.URLEncoder
import kotlin.math.max
import kotlin.math.roundToInt

data class SwapExercisePreset(
    val name: String,
    val category: ExerciseCategory,
    val primaryMuscles: List<MuscleGroup>,
    val secondaryMuscles: List<MuscleGroup> = emptyList(),
    val targetRepsOrSeconds: Int = 10
)

private val QUICK_SWAP_PRESETS = listOf(
    SwapExercisePreset(
        name = "Wide-Grip Lat Pulldown",
        category = ExerciseCategory.WEIGHTED,
        primaryMuscles = listOf(MuscleGroup.LATS, MuscleGroup.UPPER_BACK_TRAPS),
        secondaryMuscles = listOf(MuscleGroup.BICEPS, MuscleGroup.FOREARMS),
        targetRepsOrSeconds = 10
    ),
    SwapExercisePreset(
        name = "Smith Machine Incline Press",
        category = ExerciseCategory.WEIGHTED,
        primaryMuscles = listOf(MuscleGroup.CHEST, MuscleGroup.FRONT_DELTS),
        secondaryMuscles = listOf(MuscleGroup.TRICEPS),
        targetRepsOrSeconds = 10
    ),
    SwapExercisePreset(
        name = "Cable Seated Row",
        category = ExerciseCategory.WEIGHTED,
        primaryMuscles = listOf(MuscleGroup.UPPER_BACK_TRAPS, MuscleGroup.LATS),
        secondaryMuscles = listOf(MuscleGroup.BICEPS, MuscleGroup.REAR_DELTS),
        targetRepsOrSeconds = 10
    ),
    SwapExercisePreset(
        name = "Dumbbell Incline Press",
        category = ExerciseCategory.WEIGHTED,
        primaryMuscles = listOf(MuscleGroup.CHEST, MuscleGroup.FRONT_DELTS),
        secondaryMuscles = listOf(MuscleGroup.TRICEPS),
        targetRepsOrSeconds = 10
    ),
    SwapExercisePreset(
        name = "Pseudo Planche Push-Ups",
        category = ExerciseCategory.CALISTHENICS,
        primaryMuscles = listOf(MuscleGroup.CHEST, MuscleGroup.FRONT_DELTS),
        secondaryMuscles = listOf(MuscleGroup.TRICEPS, MuscleGroup.ABS_CORE),
        targetRepsOrSeconds = 12
    ),
    SwapExercisePreset(
        name = "Australian Inverted Rows",
        category = ExerciseCategory.CALISTHENICS,
        primaryMuscles = listOf(MuscleGroup.UPPER_BACK_TRAPS, MuscleGroup.LATS, MuscleGroup.REAR_DELTS),
        secondaryMuscles = listOf(MuscleGroup.BICEPS),
        targetRepsOrSeconds = 12
    ),
    SwapExercisePreset(
        name = "Weighted Parallel Bar Dips",
        category = ExerciseCategory.WEIGHTED,
        primaryMuscles = listOf(MuscleGroup.CHEST, MuscleGroup.TRICEPS),
        secondaryMuscles = listOf(MuscleGroup.FRONT_DELTS),
        targetRepsOrSeconds = 8
    ),
    SwapExercisePreset(
        name = "Hollow Body Core Hold",
        category = ExerciseCategory.ISOMETRIC,
        primaryMuscles = listOf(MuscleGroup.ABS_CORE, MuscleGroup.OBLIQUES),
        targetRepsOrSeconds = 45
    ),
    SwapExercisePreset(
        name = "Bulgarian Split Squats",
        category = ExerciseCategory.WEIGHTED,
        primaryMuscles = listOf(MuscleGroup.QUADS, MuscleGroup.GLUTES),
        secondaryMuscles = listOf(MuscleGroup.HAMSTRINGS, MuscleGroup.CALVES),
        targetRepsOrSeconds = 10
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutScreen(
    plannedDays: List<PlannedDayEntity>,
    selectedDayOfWeek: Int,
    onSelectDayOfWeek: (Int) -> Unit,
    plannedExercisesForDay: List<PlannedExerciseEntity>,
    activeSession: WorkoutSessionEntity?,
    loggedSets: List<LoggedSetEntity>,
    lastSessionReferenceByExercise: Map<String, String>,
    exerciseHistoryByExerciseId: Map<String, List<ExerciseHistoryPoint>>,
    questClearSummary: QuestClearSummary?,
    haptics: HapticsManager?,
    onStartOrResumeSession: (PlannedDayEntity?) -> Unit,
    onUpdateSet: (LoggedSetEntity, Boolean) -> Unit,
    onSwapOrAddExercise: (targetExerciseIdToSwap: String?, newName: String, category: ExerciseCategory, primaryMuscles: List<MuscleGroup>, targetSets: Int, targetRepsOrSeconds: Int) -> Unit,
    onSubmitWorkout: () -> Unit,
    onDismissQuestClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val selectedDay = plannedDays.find { it.dayOfWeek == selectedDayOfWeek } ?: plannedDays.firstOrNull()

    // Rest Countdown Timer State
    var restSecondsRemaining by remember { mutableIntStateOf(0) }
    var restTotalSeconds by remember { mutableIntStateOf(90) }

    LaunchedEffect(restSecondsRemaining) {
        if (restSecondsRemaining > 0) {
            delay(1000L)
            restSecondsRemaining -= 1
            if (restSecondsRemaining == 0) {
                haptics?.setComplete()
            }
        }
    }

    // Analytics Modal State
    var analyticsExerciseId by remember { mutableStateOf<String?>(null) }
    var analyticsExerciseName by remember { mutableStateOf("") }

    // Mid-Workout Swap / Add Exercise Bottom Sheet State
    var showSwapAddSheet by remember { mutableStateOf(false) }
    var swapTargetExerciseId by remember { mutableStateOf<String?>(null) }
    var swapTargetExerciseName by remember { mutableStateOf<String?>(null) }

    // Keypad Direct Edit Dialog State
    var editingSet by remember { mutableStateOf<LoggedSetEntity?>(null) }

    val totalSetsCount = loggedSets.size
    val completedSetsCount = loggedSets.count { it.isCompleted }
    val sessionProgressTarget = if (totalSetsCount > 0) {
        completedSetsCount.toFloat() / totalSetsCount.toFloat()
    } else 0f

    val animatedTopProgress by animateFloatAsState(
        targetValue = sessionProgressTarget.coerceIn(0f, 1f),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "topSessionProgress"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundCanvas)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 1. Pinned Top Smooth Horizontal Progress Bar (#5CD6C0 on #1C2E2B track)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .background(PrimaryTealTrack)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedTopProgress)
                        .height(6.dp)
                        .background(PrimaryTeal)
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Screen Title ("Workouts", 28.sp, Sentence case) + Add Exercise Pill
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Workouts",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Normal,
                        color = TextPrimary
                    )

                    Surface(
                        onClick = {
                            haptics?.tick()
                            swapTargetExerciseId = null
                            swapTargetExerciseName = null
                            showSwapAddSheet = true
                        },
                        shape = RoundedCornerShape(20.dp),
                        color = SurfaceContainerHigh
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = PrimaryTeal,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Add exercise",
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // Weekly Plan Day Selector (Day 1 .. Day 7)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    plannedDays.sortedBy { it.dayOfWeek }.forEach { day ->
                        val isSelected = day.dayOfWeek == selectedDayOfWeek
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                haptics?.tick()
                                onSelectDayOfWeek(day.dayOfWeek)
                            },
                            label = {
                                Text(
                                    text = if (day.isRestDay) "Day ${day.dayOfWeek} · Rest" else "Day ${day.dayOfWeek}",
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                )
                            },
                            border = null,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = PrimaryTealTrack,
                                selectedLabelColor = PrimaryTeal,
                                containerColor = SurfaceCard,
                                labelColor = TextSecondary
                            )
                        )
                    }
                }

                // Header Card for Selected Day
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceCard)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = selectedDay?.title ?: "Training session",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (activeSession != null) {
                                "$completedSetsCount of $totalSetsCount sets completed (${(sessionProgressTarget * 100).roundToInt()}%)"
                            } else {
                                "Start your workout session to log sets and earn XP"
                            },
                            fontSize = 13.sp,
                            color = if (activeSession != null) PrimaryTeal else TextSecondary
                        )

                        if (activeSession == null) {
                            Spacer(modifier = Modifier.height(14.dp))
                            Button(
                                onClick = {
                                    haptics?.tick()
                                    onStartOrResumeSession(selectedDay)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                shape = RoundedCornerShape(24.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = PrimaryTeal,
                                    contentColor = OnPrimaryTeal
                                )
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Start workout", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            }
                        }
                    }
                }

                // Group sets by exercise in order
                val setsByExercise = remember(loggedSets) {
                    loggedSets.groupBy { it.exerciseId }
                }

                if (activeSession != null && setsByExercise.isNotEmpty()) {
                    setsByExercise.entries.forEach { (exerciseId, exerciseSets) ->
                        val firstSet = exerciseSets.first()
                        val plannedMeta = plannedExercisesForDay.find { it.exerciseId == exerciseId }
                        val restDuration = plannedMeta?.restDurationSeconds ?: 90
                        val rawRef = lastSessionReferenceByExercise[exerciseId]
                        val lastRef = if (!rawRef.isNullOrBlank()) {
                            rawRef
                        } else {
                            "Last: ${plannedMeta?.targetRepsOrSeconds ?: 10} reps"
                        }

                        LiveExerciseCard(
                            exerciseName = firstSet.exerciseName,
                            category = firstSet.category,
                            sets = exerciseSets.sortedBy { it.setNumber },
                            lastSessionReference = lastRef,
                            onWatchFormClick = {
                                haptics?.tick()
                                val url = plannedMeta?.videoUrl?.takeIf { it.isNotBlank() }
                                    ?: "https://www.youtube.com/results?search_query=Hemo+Calisthenics+${
                                        URLEncoder.encode(firstSet.exerciseName, "UTF-8")
                                    }"
                                try {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                } catch (_: Throwable) {
                                }
                            },
                            onAnalyticsClick = {
                                haptics?.tick()
                                analyticsExerciseId = exerciseId
                                analyticsExerciseName = firstSet.exerciseName
                            },
                            onSwapClick = {
                                haptics?.tick()
                                swapTargetExerciseId = exerciseId
                                swapTargetExerciseName = firstSet.exerciseName
                                showSwapAddSheet = true
                            },
                            onStepRepsOrHold = { set, delta ->
                                haptics?.tick()
                                if (set.category == ExerciseCategory.ISOMETRIC) {
                                    val updated = set.copy(holdSeconds = max(0, set.holdSeconds + delta * 5))
                                    onUpdateSet(updated, false)
                                } else {
                                    val updated = set.copy(reps = max(0, set.reps + delta))
                                    onUpdateSet(updated, false)
                                }
                            },
                            onEditSetKeypad = { set ->
                                haptics?.tick()
                                editingSet = set
                            },
                            onToggleSetComplete = { set, isChecked ->
                                val updated = set.copy(
                                    isCompleted = isChecked,
                                    completedAtEpochMillis = if (isChecked) System.currentTimeMillis() else 0L
                                )
                                if (isChecked) {
                                    // Fires double-thud haptic and launches floating rest timer bar
                                    haptics?.setComplete()
                                    restTotalSeconds = restDuration
                                    restSecondsRemaining = restDuration
                                } else {
                                    haptics?.tick()
                                }
                                onUpdateSet(updated, isChecked)
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Finish workout button -> Launches "Workout complete" celebratory modal
                    Button(
                        onClick = {
                            haptics?.questClearAndPr()
                            restSecondsRemaining = 0
                            onSubmitWorkout()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        shape = RoundedCornerShape(27.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrimaryTeal,
                            contentColor = OnPrimaryTeal
                        )
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Finish workout",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp
                        )
                    }
                } else {
                    // Preview of Planned Exercises when session not yet started
                    plannedExercisesForDay.sortedBy { it.orderIndex }.forEach { ex ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = SurfaceCard)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = ex.name,
                                        fontSize = 18.sp,
                                        color = TextPrimary,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "${ex.targetSets} sets × ${ex.targetRepsOrSeconds} ${if (ex.category == ExerciseCategory.ISOMETRIC) "s hold" else "reps"} · Rest ${ex.restDurationSeconds}s",
                                        fontSize = 13.sp,
                                        color = TextSecondary
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Surface(
                                        onClick = {
                                            haptics?.tick()
                                            val url = ex.videoUrl?.takeIf { it.isNotBlank() }
                                                ?: "https://www.youtube.com/results?search_query=Hemo+Calisthenics+${
                                                    URLEncoder.encode(ex.name, "UTF-8")
                                                }"
                                            try {
                                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                            } catch (_: Throwable) {
                                            }
                                        },
                                        shape = RoundedCornerShape(16.dp),
                                        color = SecondaryBlueTrack
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.OndemandVideo,
                                                contentDescription = null,
                                                tint = SecondaryBlue,
                                                modifier = Modifier.size(15.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "Watch form",
                                                fontSize = 12.sp,
                                                color = SecondaryBlue,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }

                                    IconButton(
                                        onClick = {
                                            haptics?.tick()
                                            analyticsExerciseId = ex.exerciseId
                                            analyticsExerciseName = ex.name
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Analytics,
                                            contentDescription = "Progression chart",
                                            tint = PrimaryTeal
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(100.dp))
            }
        }

        // Floating Auto-Rest Countdown Timer Bar
        if (restSecondsRemaining > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceContainerHigh)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(PrimaryTealTrack),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Timer,
                                    contentDescription = null,
                                    tint = PrimaryTeal,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Rest timer",
                                    fontSize = 12.sp,
                                    color = TextSecondary
                                )
                                val mins = restSecondsRemaining / 60
                                val secs = restSecondsRemaining % 60
                                Text(
                                    text = String.format("%02d:%02d", mins, secs),
                                    fontSize = 20.sp,
                                    color = PrimaryTeal,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(
                                onClick = {
                                    haptics?.tick()
                                    restSecondsRemaining = max(0, restSecondsRemaining - 15)
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Text("-15s", color = TextPrimary, fontWeight = FontWeight.Medium)
                            }
                            TextButton(
                                onClick = {
                                    haptics?.tick()
                                    restSecondsRemaining += 15
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Text("+15s", color = PrimaryTeal, fontWeight = FontWeight.Medium)
                            }
                            Button(
                                onClick = {
                                    haptics?.tick()
                                    restSecondsRemaining = 0
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = PrimaryTeal,
                                    contentColor = OnPrimaryTeal
                                ),
                                shape = RoundedCornerShape(16.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.SkipNext, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Skip", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }

    // Exercise Analytics Line Chart Modal
    if (analyticsExerciseId != null) {
        val points = exerciseHistoryByExerciseId[analyticsExerciseId] ?: emptyList()
        ExerciseProgressionChartModal(
            exerciseName = analyticsExerciseName,
            historyPoints = points,
            onDismiss = { analyticsExerciseId = null }
        )
    }

    // Keypad Dialog for Exact Reps / Weight (kg) / Hold (s)
    editingSet?.let { setToEdit ->
        var repsInput by remember(setToEdit) { mutableStateOf(setToEdit.reps.toString()) }
        var weightInput by remember(setToEdit) { mutableStateOf(setToEdit.weightKg.toString()) }
        var holdInput by remember(setToEdit) { mutableStateOf(setToEdit.holdSeconds.toString()) }

        AlertDialog(
            onDismissRequest = { editingSet = null },
            containerColor = SurfaceCard,
            title = {
                Text(
                    text = "${setToEdit.exerciseName} · Set ${setToEdit.setNumber}",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (setToEdit.category == ExerciseCategory.ISOMETRIC) {
                        OutlinedTextField(
                            value = holdInput,
                            onValueChange = { holdInput = it },
                            label = { Text("Hold (s)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )
                    } else {
                        OutlinedTextField(
                            value = repsInput,
                            onValueChange = { repsInput = it },
                            label = { Text("Reps") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )
                    }
                    OutlinedTextField(
                        value = weightInput,
                        onValueChange = { weightInput = it },
                        label = { Text("Weight (kg)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        haptics?.tick()
                        val updated = setToEdit.copy(
                            reps = repsInput.toIntOrNull() ?: setToEdit.reps,
                            weightKg = weightInput.toFloatOrNull() ?: setToEdit.weightKg,
                            holdSeconds = holdInput.toIntOrNull() ?: setToEdit.holdSeconds
                        )
                        onUpdateSet(updated, updated.isCompleted)
                        editingSet = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryTeal,
                        contentColor = OnPrimaryTeal
                    )
                ) {
                    Text("Save", fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { editingSet = null }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }

    // Mid-Workout "Swap exercise" Bottom Sheet
    if (showSwapAddSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        var customName by remember { mutableStateOf("") }
        var customCategory by remember { mutableStateOf(ExerciseCategory.CALISTHENICS) }
        var customSets by remember { mutableStateOf("3") }
        var customReps by remember { mutableStateOf("10") }

        ModalBottomSheet(
            onDismissRequest = { showSwapAddSheet = false },
            sheetState = sheetState,
            containerColor = SurfaceCard,
            contentColor = TextPrimary
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = if (swapTargetExerciseId != null) {
                        "Swap exercise · ${swapTargetExerciseName ?: ""}"
                    } else {
                        "Add exercise"
                    },
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Substitute bodyweight moves for gym equipment (Smith machine, cables, dumbbells) on the fly.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )

                Text(
                    text = "Quick alternatives",
                    style = MaterialTheme.typography.labelLarge,
                    color = SecondaryBlue
                )

                QUICK_SWAP_PRESETS.forEach { preset ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                haptics?.tick()
                                onSwapOrAddExercise(
                                    swapTargetExerciseId,
                                    preset.name,
                                    preset.category,
                                    preset.primaryMuscles,
                                    3,
                                    preset.targetRepsOrSeconds
                                )
                                showSwapAddSheet = false
                            },
                        colors = CardDefaults.cardColors(containerColor = SurfaceContainerHigh),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = preset.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "${preset.category.displayName} · ${preset.primaryMuscles.joinToString { it.displayName }}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary
                                )
                            }
                            Icon(Icons.Default.SwapHoriz, contentDescription = null, tint = PrimaryTeal)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Custom exercise",
                    style = MaterialTheme.typography.labelLarge,
                    color = PrimaryTeal
                )

                OutlinedTextField(
                    value = customName,
                    onValueChange = { customName = it },
                    label = { Text("Exercise name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExerciseCategory.entries.forEach { cat ->
                        FilterChip(
                            selected = customCategory == cat,
                            onClick = { customCategory = cat },
                            label = { Text(cat.displayName) },
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

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = customSets,
                        onValueChange = { customSets = it },
                        label = { Text("Sets") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = customReps,
                        onValueChange = { customReps = it },
                        label = { Text("Reps / Hold (s)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                Button(
                    onClick = {
                        if (customName.isNotBlank()) {
                            haptics?.tick()
                            val lower = customName.trim().lowercase()
                            val inferredMuscles = when {
                                lower.contains("pull") || lower.contains("chin") || lower.contains("row") || lower.contains("lat") || lower.contains("lever") ->
                                    listOf(MuscleGroup.LATS, MuscleGroup.UPPER_BACK_TRAPS, MuscleGroup.BICEPS)
                                lower.contains("squat") || lower.contains("lunge") || lower.contains("pistol") || lower.contains("quad") || lower.contains("leg press") ->
                                    listOf(MuscleGroup.QUADS, MuscleGroup.GLUTES)
                                lower.contains("deadlift") || lower.contains("rdl") || lower.contains("hamstring") || lower.contains("nordic") || lower.contains("hinge") ->
                                    listOf(MuscleGroup.HAMSTRINGS, MuscleGroup.GLUTES, MuscleGroup.LOWER_BACK)
                                lower.contains("calf") || lower.contains("calves") ->
                                    listOf(MuscleGroup.CALVES)
                                lower.contains("pike") || lower.contains("handstand") || lower.contains("shoulder") || lower.contains("delt") || lower.contains("ohp") ->
                                    listOf(MuscleGroup.FRONT_DELTS, MuscleGroup.SIDE_DELTS, MuscleGroup.TRICEPS)
                                lower.contains("curl") || lower.contains("bicep") ->
                                    listOf(MuscleGroup.BICEPS, MuscleGroup.FOREARMS)
                                lower.contains("tricep") || lower.contains("extension") || lower.contains("skull") ->
                                    listOf(MuscleGroup.TRICEPS)
                                lower.contains("plank") || lower.contains("hollow") || lower.contains("ab") || lower.contains("core") || lower.contains("l-sit") || lower.contains("hanging") ->
                                    listOf(MuscleGroup.ABS_CORE, MuscleGroup.OBLIQUES)
                                else ->
                                    listOf(MuscleGroup.CHEST, MuscleGroup.FRONT_DELTS, MuscleGroup.TRICEPS)
                            }
                            onSwapOrAddExercise(
                                swapTargetExerciseId,
                                customName.trim(),
                                customCategory,
                                inferredMuscles,
                                (customSets.toIntOrNull() ?: 3).coerceIn(1, 10),
                                (customReps.toIntOrNull() ?: 10).coerceAtLeast(1)
                            )
                            showSwapAddSheet = false
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(25.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryTeal,
                        contentColor = OnPrimaryTeal
                    )
                ) {
                    Text(
                        text = if (swapTargetExerciseId != null) "Swap exercise" else "Add exercise",
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // Celebratory "Workout complete" modal (ONLY shown after Finish workout is tapped)
    if (questClearSummary != null) {
        QuestClearCelebrationDialog(
            summary = questClearSummary,
            onDismiss = onDismissQuestClear
        )
    }
}

@Composable
private fun LiveExerciseCard(
    exerciseName: String,
    category: ExerciseCategory,
    sets: List<LoggedSetEntity>,
    lastSessionReference: String,
    onWatchFormClick: () -> Unit,
    onAnalyticsClick: () -> Unit,
    onSwapClick: () -> Unit,
    onStepRepsOrHold: (LoggedSetEntity, Int) -> Unit,
    onEditSetKeypad: (LoggedSetEntity) -> Unit,
    onToggleSetComplete: (LoggedSetEntity, Boolean) -> Unit
) {
    val allSetsCompleted = sets.isNotEmpty() && sets.all { it.isCompleted }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = exerciseName,
                        fontSize = 18.sp,
                        color = if (allSetsCompleted) PrimaryTeal else TextPrimary,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${category.displayName} · $lastSessionReference",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onAnalyticsClick) {
                        Icon(
                            imageVector = Icons.Default.Analytics,
                            contentDescription = "Progression chart",
                            tint = SecondaryBlue
                        )
                    }
                    IconButton(onClick = onSwapClick) {
                        Icon(
                            imageVector = Icons.Default.SwapHoriz,
                            contentDescription = "Swap exercise",
                            tint = WarmGold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Watch form chip (#8AB4F8 icon, #1D2536 fill) + Swap exercise chip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = SecondaryBlueTrack,
                    modifier = Modifier.clickable { onWatchFormClick() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.OndemandVideo,
                            contentDescription = null,
                            tint = SecondaryBlue,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Watch form",
                            fontSize = 12.sp,
                            color = SecondaryBlue,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = SurfaceContainerHigh,
                    modifier = Modifier.clickable { onSwapClick() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.SwapHoriz,
                            contentDescription = null,
                            tint = TextPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Swap exercise",
                            fontSize = 12.sp,
                            color = TextPrimary,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Set Rows
            sets.forEach { set ->
                SetTrackerRow(
                    set = set,
                    lastReference = lastSessionReference,
                    onStepDelta = { delta -> onStepRepsOrHold(set, delta) },
                    onKeypadOpen = { onEditSetKeypad(set) },
                    onCheckedChange = { checked -> onToggleSetComplete(set, checked) }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun SetTrackerRow(
    set: LoggedSetEntity,
    lastReference: String,
    onStepDelta: (Int) -> Unit,
    onKeypadOpen: () -> Unit,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (set.isCompleted) PrimaryTealTrack else BackgroundCanvas
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Set number ("Set 1") and muted previous performance ("Last: 8 reps")
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(
                text = "Set ${set.setNumber}",
                fontSize = 14.sp,
                color = if (set.isCompleted) PrimaryTeal else TextPrimary,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = lastReference,
                fontSize = 12.sp,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Clean pill steppers (#282A2C) for Reps and Weight (kg) / Hold (s)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = SurfaceContainerHigh,
                modifier = Modifier
                    .size(32.dp)
                    .clickable { onStepDelta(-1) }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Remove,
                        contentDescription = "Decrease",
                        tint = TextPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = SurfaceContainerHigh,
                modifier = Modifier.clickable { onKeypadOpen() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val mainLabel = if (set.category == ExerciseCategory.ISOMETRIC) {
                        "${set.holdSeconds}s"
                    } else {
                        "${set.reps} reps"
                    }
                    val weightLabel = if (set.weightKg > 0f) " · ${set.weightKg.roundToInt()} kg" else ""
                    Text(
                        text = mainLabel + weightLabel,
                        fontSize = 13.sp,
                        color = TextPrimary,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit set",
                        tint = TextSecondary,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }

            Surface(
                shape = CircleShape,
                color = SurfaceContainerHigh,
                modifier = Modifier
                    .size(32.dp)
                    .clickable { onStepDelta(1) }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Increase",
                        tint = PrimaryTeal,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        // Bouncy M3 Expressive Morphing Checkbox (squircle -> #5CD6C0 circle on check)
        MorphingNeonCheckbox(
            checked = set.isCompleted,
            onCheckedChange = onCheckedChange
        )
    }
}
