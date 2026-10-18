package com.forgequest.ai.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.forgequest.ai.data.local.entity.BodyWeightLogEntity
import com.forgequest.ai.data.local.entity.ChatMessageEntity
import com.forgequest.ai.data.local.entity.LoggedSetEntity
import com.forgequest.ai.data.local.entity.MealLogEntity
import com.forgequest.ai.data.local.entity.PlannedDayEntity
import com.forgequest.ai.data.local.entity.PlannedExerciseEntity
import com.forgequest.ai.data.local.entity.UserProfileEntity
import com.forgequest.ai.data.local.entity.VaultFoodItemEntity
import com.forgequest.ai.data.local.entity.WorkoutSessionEntity
import com.forgequest.ai.data.repository.ForgeQuestRepository
import com.forgequest.ai.domain.model.AiModelTier
import com.forgequest.ai.domain.model.AppSettings
import com.forgequest.ai.domain.model.DailyTrendPoint
import com.forgequest.ai.domain.model.ExerciseCategory
import com.forgequest.ai.domain.model.ExerciseHistoryPoint
import com.forgequest.ai.domain.model.FoodCategory
import com.forgequest.ai.domain.model.MuscleGroup
import com.forgequest.ai.domain.model.QuestClearSummary
import com.forgequest.ai.domain.model.VisionFoodAnalysisResult
import com.forgequest.ai.domain.usecase.GenerateProgressSummaryMarkdownUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class ForgeQuestViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: ForgeQuestRepository = ForgeQuestRepository.getInstance(application)

    val userProfile: StateFlow<UserProfileEntity> = repository.userProfileFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserProfileEntity())

    val settings: StateFlow<AppSettings> = repository.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    val plannedDays: StateFlow<List<PlannedDayEntity>> = repository.activePlanFlow
        .flatMapLatest { plan ->
            if (plan != null) repository.observeDaysForPlan(plan.planId) else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allPlannedExercises: StateFlow<List<PlannedExerciseEntity>> = repository.allPlannedExercisesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedDayOfWeek = MutableStateFlow(LocalDate.now().dayOfWeek.value)
    val selectedDayOfWeek: StateFlow<Int> = _selectedDayOfWeek.asStateFlow()

    val activeSession: StateFlow<WorkoutSessionEntity?> = repository.activeSessionFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val activeSessionSnapshot: StateFlow<com.forgequest.ai.domain.model.WorkoutSessionSnapshot> =
        repository.activeSessionSnapshotFlow
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.forgequest.ai.domain.model.WorkoutSessionSnapshot(session = null))

    val completedSessions: StateFlow<List<WorkoutSessionEntity>> = repository.completedSessionsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val loggedSets: StateFlow<List<LoggedSetEntity>> = activeSession
        .flatMapLatest { session ->
            if (session != null) repository.observeSetsForSession(session.sessionId) else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val vaultItems: StateFlow<List<VaultFoodItemEntity>> = repository.vaultFoodItemsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val todayMealLogs: StateFlow<List<MealLogEntity>> = repository.observeMealLogsForDay(LocalDate.now().toEpochDay())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val weightLogs: StateFlow<List<BodyWeightLogEntity>> = repository.allWeightLogsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val muscleIntensities: StateFlow<Map<MuscleGroup, Float>> = repository.muscleRecoveriesFlow
        .map { recoveries -> repository.calculateMuscleHeatmapMap(recoveries) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            MuscleGroup.entries.associateWith { 0f }
        )

    val chatMessages: StateFlow<List<ChatMessageEntity>> = repository.chatMessagesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _lastSessionReferenceByExercise = MutableStateFlow<Map<String, String>>(emptyMap())
    val lastSessionReferenceByExercise: StateFlow<Map<String, String>> = _lastSessionReferenceByExercise.asStateFlow()

    private val _exerciseHistoryByExerciseId = MutableStateFlow<Map<String, List<ExerciseHistoryPoint>>>(emptyMap())
    val exerciseHistoryByExerciseId: StateFlow<Map<String, List<ExerciseHistoryPoint>>> = _exerciseHistoryByExerciseId.asStateFlow()

    private val _questClearSummary = MutableStateFlow<QuestClearSummary?>(null)
    val questClearSummary: StateFlow<QuestClearSummary?> = _questClearSummary.asStateFlow()

    private val _trends7Day = MutableStateFlow<List<DailyTrendPoint>>(emptyList())
    val trends7Day: StateFlow<List<DailyTrendPoint>> = _trends7Day.asStateFlow()

    private val _trends30Day = MutableStateFlow<List<DailyTrendPoint>>(emptyList())
    val trends30Day: StateFlow<List<DailyTrendPoint>> = _trends30Day.asStateFlow()

    private val _isAnalyzingPhoto = MutableStateFlow(false)
    val isAnalyzingPhoto: StateFlow<Boolean> = _isAnalyzingPhoto.asStateFlow()

    private val _visionAnalysisResult = MutableStateFlow<VisionFoodAnalysisResult?>(null)
    val visionAnalysisResult: StateFlow<VisionFoodAnalysisResult?> = _visionAnalysisResult.asStateFlow()

    private var lastCapturedPhotoUri: String? = null

    private val _isSendingMessage = MutableStateFlow(false)
    val isSendingMessage: StateFlow<Boolean> = _isSendingMessage.asStateFlow()

    private val _showApiKeyRequiredSheet = MutableStateFlow(false)
    val showApiKeyRequiredSheet: StateFlow<Boolean> = _showApiKeyRequiredSheet.asStateFlow()

    private val _liveProgressSummaryMarkdown = MutableStateFlow("")
    val liveProgressSummaryMarkdown: StateFlow<String> = _liveProgressSummaryMarkdown.asStateFlow()

    private val _progressSummaryTokenEstimate = MutableStateFlow(0)
    val progressSummaryTokenEstimate: StateFlow<Int> = _progressSummaryTokenEstimate.asStateFlow()

    private val _statusBannerMessage = MutableStateFlow<String?>(null)
    val statusBannerMessage: StateFlow<String?> = _statusBannerMessage.asStateFlow()

    init {
        viewModelScope.launch {
            repository.initializeAndEvaluateStreak()
            refreshAnalyticsAndSummary()
        }
        viewModelScope.launch {
            allPlannedExercises.collect { exercises ->
                refreshExerciseMetadata(exercises)
            }
        }
        viewModelScope.launch {
            todayMealLogs.collect {
                refreshAnalyticsAndSummary()
            }
        }
    }

    fun dismissApiKeyRequiredSheet() {
        _showApiKeyRequiredSheet.value = false
    }

    fun triggerApiKeyRequiredSheet() {
        _showApiKeyRequiredSheet.value = true
    }

    private suspend fun refreshExerciseMetadata(exercises: List<PlannedExerciseEntity>) {
        val currentSessionId = activeSession.value?.sessionId ?: -1L
        val refMap = mutableMapOf<String, String>()
        val histMap = mutableMapOf<String, List<ExerciseHistoryPoint>>()
        for (ex in exercises) {
            refMap[ex.exerciseId] = repository.getLastSessionSetsLabel(ex.exerciseId, ex.name, currentSessionId)
            histMap[ex.exerciseId] = repository.getExerciseHistoryPoints(ex.exerciseId, ex.name)
        }
        _lastSessionReferenceByExercise.value = refMap
        _exerciseHistoryByExerciseId.value = histMap
    }

    fun refreshAnalyticsAndSummary() {
        viewModelScope.launch {
            _trends7Day.value = repository.getDailyTrends(7)
            _trends30Day.value = repository.getDailyTrends(30)
            val md = repository.generateProgressSummaryMarkdown()
            _liveProgressSummaryMarkdown.value = md
            _progressSummaryTokenEstimate.value = GenerateProgressSummaryMarkdownUseCase.estimateTokenCount(md)
        }
    }

    fun selectDayOfWeek(dayOfWeek: Int) {
        _selectedDayOfWeek.value = dayOfWeek.coerceIn(1, 7)
    }

    fun checkInManualRestDay() {
        viewModelScope.launch {
            repository.checkInManualRestDay()
            _statusBannerMessage.value = "Unplanned rest day logged — streak protected"
            refreshAnalyticsAndSummary()
        }
    }

    fun logBodyWeight(weightKg: Float) {
        viewModelScope.launch {
            repository.logBodyWeight(weightKg)
            refreshAnalyticsAndSummary()
        }
    }

    fun startOrResumeSession(day: PlannedDayEntity?) {
        val targetDay = day ?: plannedDays.value.find { it.dayOfWeek == _selectedDayOfWeek.value }
            ?: plannedDays.value.firstOrNull() ?: return
        viewModelScope.launch {
            val exercises = allPlannedExercises.value.filter { it.dayId == targetDay.dayId }
            repository.startOrResumeWorkoutSession(targetDay, exercises)
            refreshExerciseMetadata(allPlannedExercises.value)
        }
    }

    fun updateSet(updatedSet: LoggedSetEntity, justCheckedOff: Boolean) {
        viewModelScope.launch {
            if (justCheckedOff) {
                repository.toggleSetCompleted(updatedSet, completed = true)
            } else if (!updatedSet.isCompleted) {
                repository.updateLoggedSet(updatedSet)
            } else {
                repository.updateLoggedSet(updatedSet)
            }
        }
    }

    fun swapOrAddExercise(
        targetExerciseIdToSwap: String?,
        newName: String,
        category: ExerciseCategory,
        primaryMuscles: List<MuscleGroup>,
        targetSets: Int,
        targetRepsOrSeconds: Int
    ) {
        val session = activeSession.value ?: return
        viewModelScope.launch {
            repository.swapOrAddExerciseInSession(
                sessionId = session.sessionId,
                dayId = session.dayId,
                name = newName,
                category = category,
                targetSets = targetSets,
                targetRepsOrSeconds = targetRepsOrSeconds,
                primaryMuscles = primaryMuscles,
                replaceExerciseId = targetExerciseIdToSwap
            )
            refreshExerciseMetadata(allPlannedExercises.value)
        }
    }

    fun submitWorkout() {
        val session = activeSession.value ?: return
        viewModelScope.launch {
            val summary = repository.submitWorkoutSession(session.sessionId)
            _questClearSummary.value = summary
            refreshExerciseMetadata(allPlannedExercises.value)
            refreshAnalyticsAndSummary()
        }
    }

    fun dismissQuestClear() {
        _questClearSummary.value = null
    }

    fun switchModelTier(tier: AiModelTier) {
        viewModelScope.launch {
            repository.updateActiveModel(tier)
        }
    }

    fun saveOrUpdateVaultItem(item: VaultFoodItemEntity) {
        viewModelScope.launch {
            repository.upsertVaultFoodItem(item)
        }
    }

    fun deleteVaultItem(item: VaultFoodItemEntity) {
        viewModelScope.launch {
            repository.deleteVaultFoodItem(item.foodId)
        }
    }

    fun logSelectedVaultItems(selections: List<Pair<VaultFoodItemEntity, Float>>) {
        viewModelScope.launch {
            repository.logBatchVaultFoodItems(selections)
            _statusBannerMessage.value = "Logged ${selections.size} meal item(s)"
            refreshAnalyticsAndSummary()
        }
    }

    fun analyzeFoodPhoto(bitmap: Bitmap?, modelTier: AiModelTier) {
        if (settings.value.geminiApiKey.isBlank()) {
            _showApiKeyRequiredSheet.value = true
            return
        }
        viewModelScope.launch {
            _isAnalyzingPhoto.value = true
            try {
                val targetBitmap = bitmap ?: Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
                val out = ByteArrayOutputStream()
                targetBitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
                val jpegBytes = out.toByteArray()

                if (bitmap != null) {
                    runCatching {
                        val dir = File(getApplication<Application>().filesDir, "vault_photos").apply { mkdirs() }
                        val photoFile = File(dir, "capture_${System.currentTimeMillis()}.jpg")
                        FileOutputStream(photoFile).use { it.write(jpegBytes) }
                        lastCapturedPhotoUri = photoFile.toURI().toString()
                    }
                } else {
                    lastCapturedPhotoUri = null
                }

                val result = repository.analyzeFoodPhotoWithHandScale(jpegBytes, modelTier)
                if (result != null) {
                    _visionAnalysisResult.value = result
                } else {
                    _showApiKeyRequiredSheet.value = true
                }
            } finally {
                _isAnalyzingPhoto.value = false
            }
        }
    }

    fun confirmVisionMealLog(
        dishName: String,
        category: FoodCategory,
        totalCalories: Int,
        totalProteinGrams: Float,
        saveToVault: Boolean,
        pinToTop: Boolean
    ) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            repository.logSingleMeal(
                category = category,
                itemName = dishName,
                multiplier = 1.0f,
                totalCalories = totalCalories,
                totalProteinGrams = totalProteinGrams,
                photoUri = lastCapturedPhotoUri,
                handScaleUsed = true
            )
            if (saveToVault) {
                repository.upsertVaultFoodItem(
                    VaultFoodItemEntity(
                        name = dishName,
                        category = category,
                        caloriesPerServing = totalCalories,
                        proteinGramsPerServing = totalProteinGrams,
                        servingDescription = "1 hand-scaled plate",
                        photoUri = lastCapturedPhotoUri,
                        isPinned = pinToTop,
                        createdAtEpochMillis = now
                    )
                )
            }
            _visionAnalysisResult.value = null
            _statusBannerMessage.value = "Logged $dishName ($totalCalories kcal, ${totalProteinGrams.toInt()}g protein)"
            refreshAnalyticsAndSummary()
        }
    }

    fun clearVisionResult() {
        _visionAnalysisResult.value = null
    }

    fun sendCoachMessage(prompt: String) {
        if (prompt.isBlank()) return
        if (settings.value.geminiApiKey.isBlank()) {
            _showApiKeyRequiredSheet.value = true
            return
        }
        viewModelScope.launch {
            _isSendingMessage.value = true
            try {
                val reply = repository.sendCoachChatMessage(prompt)
                if (reply == null) {
                    _showApiKeyRequiredSheet.value = true
                }
                refreshAnalyticsAndSummary()
            } finally {
                _isSendingMessage.value = false
            }
        }
    }

    fun applyProposedPlan(message: ChatMessageEntity) {
        val jsonPayload = message.proposedPlanJson ?: return
        viewModelScope.launch {
            val ok = repository.applyProposedWorkoutPlan(message.messageId, jsonPayload)
            if (ok) {
                _statusBannerMessage.value = "New workout plan applied to your 7-day schedule"
                refreshAnalyticsAndSummary()
            }
        }
    }

    fun saveAllSettings(updated: AppSettings) {
        viewModelScope.launch {
            repository.updateGeminiApiKey(updated.geminiApiKey)
            repository.updateActiveModel(updated.activeModel)
            repository.updateHandMeasurements(updated.handLengthCm, updated.palmWidthCm)
            repository.updateMacroGoals(updated.dailyCalorieGoal, updated.dailyProteinGoal)
            repository.updateNutritionStreakEnabled(updated.enableDailyNutritionStreak)
            _statusBannerMessage.value = "Settings saved"
            refreshAnalyticsAndSummary()
        }
    }

    fun onGoogleAccountConnected(email: String?) {
        viewModelScope.launch {
            repository.updateGoogleDriveAccount(email)
            if (!email.isNullOrBlank()) {
                repository.syncImportWorkoutPlanDoc(forceImport = false)
                repository.syncExportBackupAndSummary()
                refreshAnalyticsAndSummary()
            }
        }
    }

    fun syncWorkoutPlanDocNow() {
        viewModelScope.launch {
            val result = repository.syncImportWorkoutPlanDoc(forceImport = true)
            _statusBannerMessage.value = result.statusMessage
            refreshAnalyticsAndSummary()
        }
    }

    fun backupToDriveNow() {
        viewModelScope.launch {
            val result = repository.syncExportBackupAndSummary()
            _statusBannerMessage.value = result.statusMessage
            refreshAnalyticsAndSummary()
        }
    }

    fun restoreFromDriveBackup() {
        viewModelScope.launch {
            val result = repository.restoreFromDriveBackup()
            _statusBannerMessage.value = result.statusMessage
            refreshAnalyticsAndSummary()
        }
    }

    fun dismissBanner() {
        _statusBannerMessage.value = null
    }
}
