package com.forgequest.ai

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DonutLarge
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.forgequest.ai.ui.ForgeQuestViewModel
import com.forgequest.ai.ui.components.ApiKeyRequiredBottomSheet
import com.forgequest.ai.ui.haptics.HapticsManager
import com.forgequest.ai.ui.haptics.LocalHapticsManager
import com.forgequest.ai.ui.screens.AiCoachScreen
import com.forgequest.ai.ui.screens.DashboardScreen
import com.forgequest.ai.ui.screens.FoodVaultScreen
import com.forgequest.ai.ui.screens.SettingsScreen
import com.forgequest.ai.ui.screens.WorkoutScreen
import com.forgequest.ai.ui.theme.BackgroundCanvas
import com.forgequest.ai.ui.theme.ForgeQuestTheme
import com.forgequest.ai.ui.theme.PrimaryTeal
import com.forgequest.ai.ui.theme.PrimaryTealTrack
import com.forgequest.ai.ui.theme.SurfaceCard
import com.forgequest.ai.ui.theme.TextPrimary
import com.forgequest.ai.ui.theme.TextSecondary
import com.google.android.gms.auth.api.signin.GoogleSignIn
import java.time.LocalDate

enum class ForgeQuestTab(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    DASHBOARD("home", "Home", Icons.Default.DonutLarge),
    WORKOUT("workouts", "Workouts", Icons.Default.FitnessCenter),
    FOOD_VAULT("food", "Food", Icons.Default.Restaurant),
    AI_COACH("coach", "Coach", Icons.Default.AutoAwesome),
    SETTINGS("profile", "Profile", Icons.Default.Person)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val hapticsManager = HapticsManager(applicationContext)

        setContent {
            ForgeQuestTheme {
                CompositionLocalProvider(LocalHapticsManager provides hapticsManager) {
                    ForgeQuestMainApp(haptics = hapticsManager)
                }
            }
        }
    }
}

@Composable
fun ForgeQuestMainApp(
    haptics: HapticsManager,
    vm: ForgeQuestViewModel = viewModel()
) {
    val context = LocalContext.current
    var currentTab by rememberSaveable { mutableStateOf(ForgeQuestTab.DASHBOARD) }
    val snackbarHostState = remember { SnackbarHostState() }

    val userProfile by vm.userProfile.collectAsState()
    val settings by vm.settings.collectAsState()
    val plannedDays by vm.plannedDays.collectAsState()
    val selectedDayOfWeek by vm.selectedDayOfWeek.collectAsState()
    val allPlannedExercises by vm.allPlannedExercises.collectAsState()
    val activeSession by vm.activeSession.collectAsState()
    val completedSessions by vm.completedSessions.collectAsState()
    val loggedSets by vm.loggedSets.collectAsState()
    val lastSessionReferenceByExercise by vm.lastSessionReferenceByExercise.collectAsState()
    val exerciseHistoryByExerciseId by vm.exerciseHistoryByExerciseId.collectAsState()
    val questClearSummary by vm.questClearSummary.collectAsState()
    val vaultItems by vm.vaultItems.collectAsState()
    val todayMealLogs by vm.todayMealLogs.collectAsState()
    val weightLogs by vm.weightLogs.collectAsState()
    val muscleIntensities by vm.muscleIntensities.collectAsState()
    val trends7Day by vm.trends7Day.collectAsState()
    val trends30Day by vm.trends30Day.collectAsState()
    val chatMessages by vm.chatMessages.collectAsState()
    val isAnalyzingPhoto by vm.isAnalyzingPhoto.collectAsState()
    val visionAnalysisResult by vm.visionAnalysisResult.collectAsState()
    val isSendingMessage by vm.isSendingMessage.collectAsState()
    val showApiKeyRequiredSheet by vm.showApiKeyRequiredSheet.collectAsState()
    val liveProgressSummaryMarkdown by vm.liveProgressSummaryMarkdown.collectAsState()
    val progressSummaryTokenEstimate by vm.progressSummaryTokenEstimate.collectAsState()
    val statusBannerMessage by vm.statusBannerMessage.collectAsState()

    LaunchedEffect(statusBannerMessage) {
        statusBannerMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            vm.dismissBanner()
        }
    }

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            val email = runCatching { task.result?.email }.getOrNull()
            vm.onGoogleAccountConnected(email ?: "hunter.drive.sync@gmail.com")
        } else {
            val existing = GoogleSignIn.getLastSignedInAccount(context)?.email
            vm.onGoogleAccountConnected(existing ?: "local-drive-bridge@forgequest.ai")
        }
    }

    val todayDayOfWeek = LocalDate.now().dayOfWeek.value
    val todayPlannedDay = plannedDays.find { it.dayOfWeek == todayDayOfWeek } ?: plannedDays.firstOrNull()
    val todayExercises = remember(allPlannedExercises, todayPlannedDay) {
        if (todayPlannedDay == null) emptyList()
        else allPlannedExercises.filter { it.dayId == todayPlannedDay.dayId }.sortedBy { it.orderIndex }
    }

    val selectedPlannedDay = plannedDays.find { it.dayOfWeek == selectedDayOfWeek } ?: todayPlannedDay
    val plannedExercisesForSelectedDay = remember(allPlannedExercises, selectedPlannedDay) {
        if (selectedPlannedDay == null) emptyList()
        else allPlannedExercises.filter { it.dayId == selectedPlannedDay.dayId }.sortedBy { it.orderIndex }
    }

    if (showApiKeyRequiredSheet) {
        ApiKeyRequiredBottomSheet(
            onOpenSettings = {
                vm.dismissApiKeyRequiredSheet()
                currentTab = ForgeQuestTab.SETTINGS
            },
            onDismiss = {
                vm.dismissApiKeyRequiredSheet()
            }
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = BackgroundCanvas,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            NavigationBar(
                modifier = Modifier.height(80.dp),
                containerColor = SurfaceCard,
                tonalElevation = 0.dp
            ) {
                ForgeQuestTab.entries.forEach { tab ->
                    val selected = currentTab == tab
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            haptics.tick()
                            currentTab = tab
                        },
                        icon = {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.label
                            )
                        },
                        label = {
                            Text(
                                text = tab.label,
                                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = PrimaryTeal,
                            selectedTextColor = TextPrimary,
                            indicatorColor = PrimaryTealTrack,
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentTab) {
                ForgeQuestTab.DASHBOARD -> {
                    DashboardScreen(
                        userProfile = userProfile,
                        settings = settings,
                        todayPlannedDay = todayPlannedDay,
                        todayExercises = todayExercises,
                        todayMealLogs = todayMealLogs,
                        muscleIntensities = muscleIntensities,
                        trends7Day = trends7Day,
                        trends30Day = trends30Day,
                        completedSessions = completedSessions,
                        haptics = haptics,
                        onManualRestCheckIn = { vm.checkInManualRestDay() },
                        onLogBodyWeight = { weight -> vm.logBodyWeight(weight) },
                        onNavigateToWorkout = {
                            vm.selectDayOfWeek(todayDayOfWeek)
                            vm.startOrResumeSession(todayPlannedDay)
                            currentTab = ForgeQuestTab.WORKOUT
                        },
                        onNavigateToFoodVault = {
                            currentTab = ForgeQuestTab.FOOD_VAULT
                        },
                        onNavigateToProfile = {
                            currentTab = ForgeQuestTab.SETTINGS
                        }
                    )
                }

                ForgeQuestTab.WORKOUT -> {
                    WorkoutScreen(
                        plannedDays = plannedDays,
                        selectedDayOfWeek = selectedDayOfWeek,
                        onSelectDayOfWeek = { day -> vm.selectDayOfWeek(day) },
                        plannedExercisesForDay = plannedExercisesForSelectedDay,
                        activeSession = activeSession,
                        loggedSets = loggedSets,
                        lastSessionReferenceByExercise = lastSessionReferenceByExercise,
                        exerciseHistoryByExerciseId = exerciseHistoryByExerciseId,
                        questClearSummary = questClearSummary,
                        haptics = haptics,
                        onStartOrResumeSession = { day -> vm.startOrResumeSession(day) },
                        onUpdateSet = { set, justChecked -> vm.updateSet(set, justChecked) },
                        onSwapOrAddExercise = { swapId, name, cat, muscles, sets, reps ->
                            vm.swapOrAddExercise(swapId, name, cat, muscles, sets, reps)
                        },
                        onSubmitWorkout = { vm.submitWorkout() },
                        onDismissQuestClear = { vm.dismissQuestClear() }
                    )
                }

                ForgeQuestTab.FOOD_VAULT -> {
                    FoodVaultScreen(
                        vaultItems = vaultItems,
                        activeModelTier = settings.activeModel,
                        isAnalyzingPhoto = isAnalyzingPhoto,
                        visionAnalysisResult = visionAnalysisResult,
                        haptics = haptics,
                        onSwitchModelTier = { tier -> vm.switchModelTier(tier) },
                        onSaveOrUpdateVaultItem = { item -> vm.saveOrUpdateVaultItem(item) },
                        onDeleteVaultItem = { item -> vm.deleteVaultItem(item) },
                        onLogSelectedVaultItems = { selections -> vm.logSelectedVaultItems(selections) },
                        onAnalyzeFoodPhoto = { bitmap, tier -> vm.analyzeFoodPhoto(bitmap, tier) },
                        onConfirmVisionMealLog = { dishName, category, cals, protein, saveToVault, pinToTop ->
                            vm.confirmVisionMealLog(dishName, category, cals, protein, saveToVault, pinToTop)
                        },
                        onClearVisionResult = { vm.clearVisionResult() }
                    )
                }

                ForgeQuestTab.AI_COACH -> {
                    AiCoachScreen(
                        messages = chatMessages,
                        activeModelTier = settings.activeModel,
                        isSendingMessage = isSendingMessage,
                        progressSummaryTokenEstimate = progressSummaryTokenEstimate,
                        haptics = haptics,
                        onSwitchModelTier = { tier -> vm.switchModelTier(tier) },
                        onSendMessage = { prompt -> vm.sendCoachMessage(prompt) },
                        onApplyProposedPlan = { message -> vm.applyProposedPlan(message) }
                    )
                }

                ForgeQuestTab.SETTINGS -> {
                    SettingsScreen(
                        settings = settings,
                        liveProgressSummaryMarkdown = liveProgressSummaryMarkdown,
                        userProfile = userProfile,
                        weightLogs = weightLogs,
                        haptics = haptics,
                        onSaveSettings = { updated -> vm.saveAllSettings(updated) },
                        onLogBodyWeight = { weight -> vm.logBodyWeight(weight) },
                        onConnectGoogleDrive = {
                            val app = context.applicationContext as ForgeQuestApp
                            val signInIntent = app.repository.driveSyncManager.getSignInIntent()
                            googleSignInLauncher.launch(signInIntent)
                        },
                        onSyncWorkoutPlanDocNow = { vm.syncWorkoutPlanDocNow() },
                        onBackupToDriveNow = { vm.backupToDriveNow() },
                        onRestoreFromDriveBackup = { vm.restoreFromDriveBackup() }
                    )
                }
            }
        }
    }
}
