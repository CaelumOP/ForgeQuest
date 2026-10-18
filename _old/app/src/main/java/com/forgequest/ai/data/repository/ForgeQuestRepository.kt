package com.forgequest.ai.data.repository

import android.content.Context
import com.forgequest.ai.data.local.ForgeQuestDatabase
import com.forgequest.ai.data.local.dao.ForgeQuestDao
import com.forgequest.ai.data.local.entity.BodyWeightLogEntity
import com.forgequest.ai.data.local.entity.ChatMessageEntity
import com.forgequest.ai.data.local.entity.LoggedSetEntity
import com.forgequest.ai.data.local.entity.MealLogEntity
import com.forgequest.ai.data.local.entity.MuscleRecoveryEntity
import com.forgequest.ai.data.local.entity.PlannedDayEntity
import com.forgequest.ai.data.local.entity.PlannedExerciseEntity
import com.forgequest.ai.data.local.entity.UserProfileEntity
import com.forgequest.ai.data.local.entity.VaultFoodItemEntity
import com.forgequest.ai.data.local.entity.WorkoutPlanEntity
import com.forgequest.ai.data.local.entity.WorkoutSessionEntity
import com.forgequest.ai.data.remote.GeminiApiClient
import com.forgequest.ai.data.security.EncryptedSettingsDataStore
import com.forgequest.ai.data.sync.DriveSyncWorker
import com.forgequest.ai.data.sync.GoogleDriveSyncManager
import com.forgequest.ai.data.sync.SyncExecutionResult
import com.forgequest.ai.domain.model.AiModelTier
import com.forgequest.ai.domain.model.AppSettings
import com.forgequest.ai.domain.model.DailyTrendPoint
import com.forgequest.ai.domain.model.ExerciseCategory
import com.forgequest.ai.domain.model.ExerciseHistoryPoint
import com.forgequest.ai.domain.model.FoodCategory
import com.forgequest.ai.domain.model.HiddenFatAddition
import com.forgequest.ai.domain.model.MuscleGroup
import com.forgequest.ai.domain.model.PortionMultiplier
import com.forgequest.ai.domain.model.QuestClearSummary
import com.forgequest.ai.domain.model.VisionFoodAnalysisResult
import com.forgequest.ai.domain.model.WorkoutPlanPayload
import com.forgequest.ai.domain.usecase.AnalyzeFoodPhotoUseCase
import com.forgequest.ai.domain.usecase.EvaluateStreakDecayUseCase
import com.forgequest.ai.domain.usecase.FinishWorkoutUseCase
import com.forgequest.ai.domain.usecase.GenerateProgressSummaryMarkdownUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.max
import kotlin.math.roundToInt

class ForgeQuestRepository(
    private val context: Context,
    val database: ForgeQuestDatabase = ForgeQuestDatabase.getInstance(context),
    val settingsDataStore: EncryptedSettingsDataStore = EncryptedSettingsDataStore.getInstance(context),
    val geminiApiClient: GeminiApiClient = GeminiApiClient(),
    val evaluateStreakDecayUseCase: EvaluateStreakDecayUseCase = EvaluateStreakDecayUseCase(),
    val finishWorkoutUseCase: FinishWorkoutUseCase = FinishWorkoutUseCase(evaluateStreakDecayUseCase),
    val analyzeFoodPhotoUseCase: AnalyzeFoodPhotoUseCase = AnalyzeFoodPhotoUseCase(geminiApiClient),
    val generateProgressSummaryUseCase: GenerateProgressSummaryMarkdownUseCase = GenerateProgressSummaryMarkdownUseCase(),
    val liveWorkoutSessionManager: com.forgequest.ai.domain.usecase.LiveWorkoutSessionManager = com.forgequest.ai.domain.usecase.LiveWorkoutSessionManager(database.forgeQuestDao(), finishWorkoutUseCase)
) {
    val dao: ForgeQuestDao = database.forgeQuestDao()

    val driveSyncManager: GoogleDriveSyncManager = GoogleDriveSyncManager(
        context = context.applicationContext,
        dao = dao,
        settingsDataStore = settingsDataStore,
        generateSummaryUseCase = generateProgressSummaryUseCase
    )

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private val dateFormatter = DateTimeFormatter.ofPattern("MMM d")

    // =========================================================================
    // Reactive Flows for UI Layer
    // =========================================================================

    val userProfileFlow: Flow<UserProfileEntity> =
        dao.observeUserProfile().map { it ?: UserProfileEntity() }

    val settingsFlow: Flow<AppSettings> = settingsDataStore.settingsFlow

    val activePlanFlow: Flow<WorkoutPlanEntity?> = dao.observeActivePlan()

    val allPlannedDaysFlow: Flow<List<PlannedDayEntity>> = dao.observeAllPlannedDays()

    val allPlannedExercisesFlow: Flow<List<PlannedExerciseEntity>> = dao.observeAllPlannedExercises()

    val activeSessionFlow: Flow<WorkoutSessionEntity?> = dao.observeActiveSession()

    val activeSessionSnapshotFlow: Flow<com.forgequest.ai.domain.model.WorkoutSessionSnapshot> =
        liveWorkoutSessionManager.activeSessionSnapshotFlow

    val completedSessionsFlow: Flow<List<WorkoutSessionEntity>> = dao.observeCompletedSessions()

    val muscleRecoveriesFlow: Flow<List<MuscleRecoveryEntity>> = dao.observeAllMuscleRecoveries()

    val vaultFoodItemsFlow: Flow<List<VaultFoodItemEntity>> = dao.observeAllVaultFoodItems()

    val allWeightLogsFlow: Flow<List<BodyWeightLogEntity>> = dao.observeAllWeightLogs()

    val chatMessagesFlow: Flow<List<ChatMessageEntity>> = dao.observeAllChatMessages()

    fun observeDaysForPlan(planId: Long): Flow<List<PlannedDayEntity>> =
        dao.observeDaysForPlan(planId)

    fun observeExercisesForDay(dayId: Long): Flow<List<PlannedExerciseEntity>> =
        dao.observeExercisesForDay(dayId)

    fun observeSetsForSession(sessionId: Long): Flow<List<LoggedSetEntity>> =
        dao.observeSetsForSession(sessionId)

    fun observeMealLogsForDay(epochDay: Long): Flow<List<MealLogEntity>> =
        dao.observeMealLogsForDay(epochDay)

    fun observeMealLogsInRange(startEpochDay: Long, endEpochDay: Long): Flow<List<MealLogEntity>> =
        dao.observeMealLogsInRange(startEpochDay, endEpochDay)

    fun observeWeightLogsInRange(startEpochDay: Long, endEpochDay: Long): Flow<List<BodyWeightLogEntity>> =
        dao.observeWeightLogsInRange(startEpochDay, endEpochDay)

    // =========================================================================
    // Initialization & Streak State Machine
    // =========================================================================

    suspend fun initializeAndEvaluateStreak() = withContext(Dispatchers.IO) {
        database.seedInitialDataIfNeeded()
        val profile = dao.getUserProfile() ?: UserProfileEntity()
        val activePlan = dao.getActivePlan()
        val days = if (activePlan != null) dao.getDaysForPlan(activePlan.planId) else emptyList()
        val restDaysOfWeek = days.filter { it.isRestDay }.map { it.dayOfWeek }.toSet()

        val todayEpochDay = LocalDate.now().toEpochDay()
        val yesterdayEpochDay = todayEpochDay - 1L
        val lastEval = profile.lastStreakEvaluationEpochDay

        if (lastEval != null && lastEval < yesterdayEpochDay) {
            val completedSessions = dao.getCompletedSessionsInRange(lastEval + 1L, yesterdayEpochDay)
            val completedDays = completedSessions.map { it.epochDay }.toSet()
            val updatedProfile = evaluateStreakDecayUseCase.evaluateStreakDecay(
                profile = profile,
                currentEpochDay = yesterdayEpochDay,
                scheduledRestDaysOfWeek = restDaysOfWeek,
                completedWorkoutEpochDays = completedDays
            )
            dao.upsertUserProfile(updatedProfile)
        }

        DriveSyncWorker.schedulePeriodicSync(context)
    }

    suspend fun checkInManualRestDay(todayEpochDay: Long = LocalDate.now().toEpochDay()) =
        withContext(Dispatchers.IO) {
            val profile = dao.getUserProfile() ?: UserProfileEntity()
            val updated = evaluateStreakDecayUseCase.onManualRestDayCheckIn(profile, todayEpochDay)
            dao.upsertUserProfile(updated)
        }

    // =========================================================================
    // Workout Plan & Live Distraction-Free Session Tracking
    // =========================================================================

    suspend fun startOrResumeWorkoutSession(
        day: PlannedDayEntity,
        exercises: List<PlannedExerciseEntity>
    ): Long = liveWorkoutSessionManager.startOrResumeSession(day, exercises)

    /**
     * Updates a live set's reps, weight, holdSeconds, or completion checkbox.
     * Note: Zero PR calculations occur here — PRs are strictly deferred to [submitWorkoutSession].
     */
    suspend fun updateLoggedSet(set: LoggedSetEntity) =
        liveWorkoutSessionManager.updateSetMetrics(set)

    suspend fun toggleSetCompleted(set: LoggedSetEntity, completed: Boolean): com.forgequest.ai.domain.model.SetToggleResult =
        liveWorkoutSessionManager.toggleSetCompleted(set, completed)

    suspend fun addSetToExerciseInSession(
        sessionId: Long,
        exerciseId: String,
        exerciseName: String,
        category: ExerciseCategory,
        primaryMuscles: List<MuscleGroup>,
        secondaryMuscles: List<MuscleGroup>
    ): Long = withContext(Dispatchers.IO) {
        val currentSets = dao.getSetsForSession(sessionId).filter { it.exerciseId == exerciseId }
        val nextSetNumber = (currentSets.maxOfOrNull { it.setNumber } ?: 0) + 1
        val template = currentSets.lastOrNull()
        dao.insertLoggedSet(
            LoggedSetEntity(
                sessionId = sessionId,
                exerciseId = exerciseId,
                exerciseName = exerciseName,
                category = category,
                setNumber = nextSetNumber,
                reps = template?.reps ?: if (category == ExerciseCategory.ISOMETRIC) 0 else 10,
                weightKg = template?.weightKg ?: 0f,
                holdSeconds = template?.holdSeconds ?: if (category == ExerciseCategory.ISOMETRIC) 30 else 0,
                isCompleted = false,
                completedAtEpochMillis = 0L,
                primaryMuscles = primaryMuscles,
                secondaryMuscles = secondaryMuscles
            )
        )
    }

    suspend fun swapOrAddExerciseInSession(
        sessionId: Long,
        dayId: Long?,
        name: String,
        category: ExerciseCategory,
        targetSets: Int,
        targetRepsOrSeconds: Int,
        primaryMuscles: List<MuscleGroup>,
        secondaryMuscles: List<MuscleGroup> = emptyList(),
        replaceExerciseId: String? = null
    ) = liveWorkoutSessionManager.swapOrAddExercise(
        sessionId = sessionId,
        dayId = dayId,
        name = name,
        category = category,
        targetSets = targetSets,
        targetRepsOrSeconds = targetRepsOrSeconds,
        primaryMuscles = primaryMuscles,
        secondaryMuscles = secondaryMuscles,
        replaceExerciseId = replaceExerciseId
    )

    suspend fun getLastSessionSetsLabel(
        exerciseId: String,
        exerciseName: String,
        excludeSessionId: Long = -1L
    ): String = withContext(Dispatchers.IO) {
        val lastSets = dao.getLastSessionSetsForExercise(exerciseId, exerciseName, excludeSessionId)
        if (lastSets.isEmpty()) return@withContext "First session — set your baseline!"
        val isIso = lastSets.first().category == ExerciseCategory.ISOMETRIC
        val summary = lastSets.joinToString(", ") { set ->
            when {
                isIso -> "${max(set.holdSeconds, set.reps)}s"
                set.weightKg > 0f -> "${set.reps}×${set.weightKg.roundToInt()}kg"
                else -> "${set.reps}"
            }
        }
        if (isIso) "Last: $summary hold" else "Last: $summary reps"
    }

    suspend fun getExerciseHistoryPoints(
        exerciseId: String,
        exerciseName: String
    ): List<ExerciseHistoryPoint> = withContext(Dispatchers.IO) {
        val historicalSets = dao.getHistoricalCompletedSetsForExercise(
            exerciseId = exerciseId,
            exerciseName = exerciseName,
            excludeSessionId = -1L
        )
        val sessionsById = dao.getCompletedSessions().associateBy { it.sessionId }

        historicalSets.groupBy { it.sessionId }
            .mapNotNull { (sessionId, sets) ->
                val session = sessionsById[sessionId] ?: return@mapNotNull null
                val ordered = sets.sortedBy { it.setNumber }
                val maxReps = ordered.maxOfOrNull { it.reps } ?: 0
                val totalReps = ordered.sumOf { it.reps }
                val maxHold = ordered.maxOfOrNull { max(it.holdSeconds, it.reps) } ?: 0
                val totalVol = FinishWorkoutUseCase.calculateExerciseSessionVolume(ordered)
                val summary = ordered.joinToString(", ") { s ->
                    when {
                        s.category == ExerciseCategory.ISOMETRIC -> "${max(s.holdSeconds, s.reps)}s"
                        s.weightKg > 0f -> "${s.reps}×${s.weightKg.roundToInt()}kg"
                        else -> "${s.reps}"
                    }
                }
                ExerciseHistoryPoint(
                    sessionId = sessionId,
                    epochDay = session.epochDay,
                    dateLabel = LocalDate.ofEpochDay(session.epochDay).format(dateFormatter),
                    maxRepsSingleSet = maxReps,
                    totalReps = totalReps,
                    totalVolume = totalVol,
                    maxHoldSeconds = maxHold,
                    setsSummary = summary
                )
            }
            .sortedBy { it.epochDay }
    }

    /**
     * Submits the active workout session, triggering the Deferred "Quest Clear" Post-Workout
     * PR detection, XP/Rank calculation, Streak Flame update, 48-hour Muscle Heatmap ignition,
     * and background Google Drive sync.
     */
    suspend fun submitWorkoutSession(sessionId: Long): QuestClearSummary =
        withContext(Dispatchers.IO) {
            val summary = liveWorkoutSessionManager.finishWorkout(sessionId)
            DriveSyncWorker.enqueueOneTimeSync(context, runImport = false, runExport = true)
            summary
        }

    suspend fun discardActiveWorkoutSession(sessionId: Long) =
        liveWorkoutSessionManager.discardSession(sessionId)

    // =========================================================================
    // 48-Hour Muscle Recovery Heatmap Math
    // =========================================================================

    fun calculateMuscleHeatmapMap(
        recoveries: List<MuscleRecoveryEntity>,
        nowEpochMillis: Long = System.currentTimeMillis()
    ): Map<MuscleGroup, Float> {
        val byMuscle = recoveries.associateBy { it.muscleGroup }
        return MuscleGroup.entries.associateWith { muscle ->
            val entity = byMuscle[muscle]
            if (entity == null) {
                0f
            } else {
                FinishWorkoutUseCase.calculateMuscleIntensity(
                    lastTrainedEpochMillis = entity.lastTrainedEpochMillis,
                    nowEpochMillis = nowEpochMillis,
                    baseFactor = entity.lastIntensityFactor
                )
            }
        }
    }

    // =========================================================================
    // Food Vault, Hand-Scaled AI Vision & Zero-Guilt Nutrition
    // =========================================================================

    suspend fun upsertVaultFoodItem(item: VaultFoodItemEntity): Long =
        withContext(Dispatchers.IO) {
            dao.upsertVaultFoodItem(item)
        }

    suspend fun toggleVaultFoodPin(item: VaultFoodItemEntity) = withContext(Dispatchers.IO) {
        dao.upsertVaultFoodItem(item.copy(isPinned = !item.isPinned))
    }

    suspend fun deleteVaultFoodItem(foodId: Long) = withContext(Dispatchers.IO) {
        dao.deleteVaultFoodItem(foodId)
    }

    suspend fun logBatchVaultFoodItems(
        selections: List<Pair<VaultFoodItemEntity, Float>>,
        epochDay: Long = LocalDate.now().toEpochDay()
    ) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val logs = selections.filter { it.second > 0f }.map { (food, multiplier) ->
            MealLogEntity(
                epochDay = epochDay,
                loggedAtEpochMillis = now,
                category = food.category,
                itemName = food.name,
                multiplier = multiplier,
                totalCalories = (food.caloriesPerServing * multiplier).roundToInt(),
                totalProteinGrams = food.proteinGramsPerServing * multiplier,
                photoUri = food.photoUri,
                handScaleUsed = false
            )
        }
        if (logs.isNotEmpty()) {
            dao.insertMealLogs(logs)
            evaluateOptionalNutritionStreak(epochDay)
            DriveSyncWorker.enqueueOneTimeSync(context, runImport = false, runExport = true)
        }
    }

    suspend fun logSingleMeal(
        category: FoodCategory,
        itemName: String,
        multiplier: Float,
        totalCalories: Int,
        totalProteinGrams: Float,
        photoUri: String? = null,
        handScaleUsed: Boolean = false,
        epochDay: Long = LocalDate.now().toEpochDay()
    ) = withContext(Dispatchers.IO) {
        dao.insertMealLog(
            MealLogEntity(
                epochDay = epochDay,
                loggedAtEpochMillis = System.currentTimeMillis(),
                category = category,
                itemName = itemName,
                multiplier = multiplier,
                totalCalories = totalCalories,
                totalProteinGrams = totalProteinGrams,
                photoUri = photoUri,
                handScaleUsed = handScaleUsed
            )
        )
        evaluateOptionalNutritionStreak(epochDay)
        DriveSyncWorker.enqueueOneTimeSync(context, runImport = false, runExport = true)
    }

    suspend fun deleteMealLog(logId: Long) = withContext(Dispatchers.IO) {
        dao.deleteMealLog(logId)
    }

    suspend fun logBodyWeight(
        weightKg: Float,
        epochDay: Long = LocalDate.now().toEpochDay()
    ) = withContext(Dispatchers.IO) {
        val coerced = weightKg.coerceIn(30f, 250f)
        dao.upsertWeightLog(
            BodyWeightLogEntity(
                epochDay = epochDay,
                weightKg = coerced,
                loggedAtEpochMillis = System.currentTimeMillis()
            )
        )
        val profile = dao.getUserProfile() ?: UserProfileEntity()
        dao.upsertUserProfile(profile.copy(currentWeightKg = coerced))
        DriveSyncWorker.enqueueOneTimeSync(context, runImport = false, runExport = true)
    }

    suspend fun getDailyTrends(
        daysCount: Int,
        todayEpochDay: Long = LocalDate.now().toEpochDay()
    ): List<DailyTrendPoint> = withContext(Dispatchers.IO) {
        val startDay = todayEpochDay - (daysCount.coerceAtLeast(1) - 1L)
        val meals = dao.getMealLogsInRange(startDay, todayEpochDay).groupBy { it.epochDay }
        val weights = dao.getWeightLogsInRange(startDay - 30L, todayEpochDay).associateBy { it.epochDay }
        val profile = dao.getUserProfile() ?: UserProfileEntity()

        var lastKnownWeight = profile.currentWeightKg
        (startDay..todayEpochDay).map { day ->
            val dayMeals = meals[day].orEmpty()
            weights[day]?.let { lastKnownWeight = it.weightKg }
            DailyTrendPoint(
                epochDay = day,
                dateLabel = LocalDate.ofEpochDay(day).format(dateFormatter),
                calories = dayMeals.sumOf { it.totalCalories },
                proteinGrams = dayMeals.sumOf { it.totalProteinGrams.toDouble() }.toFloat(),
                weightKg = lastKnownWeight
            )
        }
    }

    suspend fun analyzeFoodPhotoWithHandScale(
        jpegBytes: ByteArray,
        modelOverride: AiModelTier? = null
    ): VisionFoodAnalysisResult? = withContext(Dispatchers.IO) {
        val settings = settingsDataStore.getSettings()
        val activeModel = modelOverride ?: settings.activeModel
        analyzeFoodPhotoUseCase.execute(
            jpegBytes = jpegBytes,
            apiKey = settings.geminiApiKey,
            preferredModel = activeModel,
            handLengthCm = settings.handLengthCm,
            palmWidthCm = settings.palmWidthCm
        )
    }

    suspend fun confirmAndLogVisionResult(
        result: VisionFoodAnalysisResult,
        category: FoodCategory,
        portionMultiplier: PortionMultiplier,
        hiddenFatAddition: HiddenFatAddition,
        photoUri: String? = null,
        saveToVault: Boolean = true,
        epochDay: Long = LocalDate.now().toEpochDay()
    ) = withContext(Dispatchers.IO) {
        val selectedItems = result.detectedItems.filter { it.selected }
        if (selectedItems.isEmpty()) return@withContext

        val baseCals = selectedItems.sumOf { it.calories }
        val baseProtein = selectedItems.sumOf { it.proteinGrams.toDouble() }.toFloat()

        val finalCalories = (baseCals * portionMultiplier.factor).roundToInt() + hiddenFatAddition.extraCalories
        val finalProtein = baseProtein * portionMultiplier.factor
        val now = System.currentTimeMillis()

        dao.insertMealLog(
            MealLogEntity(
                epochDay = epochDay,
                loggedAtEpochMillis = now,
                category = category,
                itemName = result.dishName,
                multiplier = portionMultiplier.factor,
                totalCalories = finalCalories,
                totalProteinGrams = finalProtein,
                photoUri = photoUri,
                handScaleUsed = result.handScaleDetected
            )
        )

        if (saveToVault) {
            dao.upsertVaultFoodItem(
                VaultFoodItemEntity(
                    name = result.dishName,
                    category = category,
                    caloriesPerServing = finalCalories,
                    proteinGramsPerServing = finalProtein,
                    servingDescription = "1 hand-calibrated plate (${portionMultiplier.label})",
                    photoUri = photoUri,
                    isPinned = false,
                    createdAtEpochMillis = now
                )
            )
        }

        evaluateOptionalNutritionStreak(epochDay)
        DriveSyncWorker.enqueueOneTimeSync(context, runImport = false, runExport = true)
    }

    private suspend fun evaluateOptionalNutritionStreak(epochDay: Long) {
        val settings = settingsDataStore.getSettings()
        if (!settings.enableDailyNutritionStreak) return
        val dayMeals = dao.getMealLogsForDay(epochDay)
        val totalProtein = dayMeals.sumOf { it.totalProteinGrams.toDouble() }.toFloat()
        if (totalProtein >= settings.dailyProteinGoal) {
            val profile = dao.getUserProfile() ?: UserProfileEntity()
            if (profile.lastProteinGoalHitEpochDay != epochDay) {
                dao.upsertUserProfile(
                    profile.copy(
                        currentNutritionStreakDays = profile.currentNutritionStreakDays + 1,
                        lastProteinGoalHitEpochDay = epochDay
                    )
                )
            }
        }
    }

    // =========================================================================
    // In-App AI Coach Chat & 1-Tap Plan Application
    // =========================================================================

    suspend fun sendCoachChatMessage(
        userPrompt: String,
        modelOverride: AiModelTier? = null
    ): ChatMessageEntity? = withContext(Dispatchers.IO) {
        val settings = settingsDataStore.getSettings()
        if (settings.geminiApiKey.isBlank()) {
            return@withContext null
        }
        val chosenModel = modelOverride ?: settings.activeModel
        val now = System.currentTimeMillis()

        dao.insertChatMessage(
            ChatMessageEntity(
                role = "user",
                content = userPrompt.trim(),
                modelUsed = chosenModel.modelId,
                proposedPlanJson = null,
                isPlanApplied = false,
                timestampEpochMillis = now
            )
        )

        val progressSummaryMarkdown = generateProgressSummaryUseCase.execute(
            dao = dao,
            dailyCalorieGoal = settings.dailyCalorieGoal,
            dailyProteinGoal = settings.dailyProteinGoal
        )

        val recentMessages = dao.getAllChatMessages()
            .takeLast(8)
            .map { it.role to it.content }

        val response = geminiApiClient.sendCoachMessage(
            userPrompt = userPrompt,
            progressSummaryMarkdown = progressSummaryMarkdown,
            recentHistory = recentMessages,
            apiKey = settings.geminiApiKey,
            modelTier = chosenModel
        ) ?: return@withContext null

        val assistantEntity = ChatMessageEntity(
            role = "assistant",
            content = response.replyMarkdown,
            modelUsed = response.modelUsed,
            proposedPlanJson = response.proposedPlanJson,
            isPlanApplied = false,
            timestampEpochMillis = System.currentTimeMillis()
        )
        val id = dao.insertChatMessage(assistantEntity)
        assistantEntity.copy(messageId = id)
    }

    suspend fun applyProposedWorkoutPlan(
        messageId: Long?,
        proposedPlanJson: String,
        sourceTag: String = "AI_COACH"
    ): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val payload = json.decodeFromString<WorkoutPlanPayload>(proposedPlanJson)
            dao.replaceActiveWorkoutPlanFromPayload(payload, sourceTag)
            if (messageId != null && messageId > 0L) {
                dao.markPlanApplied(messageId)
            }
            DriveSyncWorker.enqueueOneTimeSync(context, runImport = false, runExport = true)
            true
        }.getOrDefault(false)
    }

    suspend fun generateProgressSummaryMarkdown(): String = withContext(Dispatchers.IO) {
        val settings = settingsDataStore.getSettings()
        generateProgressSummaryUseCase.execute(
            dao = dao,
            dailyCalorieGoal = settings.dailyCalorieGoal,
            dailyProteinGoal = settings.dailyProteinGoal
        )
    }

    // =========================================================================
    // Settings & Two-Way Google Drive Sync Controls
    // =========================================================================

    suspend fun updateGeminiApiKey(apiKey: String) =
        settingsDataStore.updateGeminiApiKey(apiKey)

    suspend fun updateActiveModel(model: AiModelTier) =
        settingsDataStore.updateActiveModel(model)

    suspend fun updateHandMeasurements(handLengthCm: Float, palmWidthCm: Float) =
        settingsDataStore.updateHandMeasurements(handLengthCm, palmWidthCm)

    suspend fun updateMacroGoals(dailyCalorieGoal: Int, dailyProteinGoal: Int) =
        settingsDataStore.updateMacroGoals(dailyCalorieGoal, dailyProteinGoal)

    suspend fun updateNutritionStreakEnabled(enabled: Boolean) =
        settingsDataStore.updateNutritionStreakEnabled(enabled)

    suspend fun updateGoogleDriveAccount(email: String?) =
        settingsDataStore.updateGoogleDriveAccount(email)

    suspend fun syncImportWorkoutPlanDoc(forceImport: Boolean = true): SyncExecutionResult =
        driveSyncManager.syncImportWorkoutPlanDoc(forceImport = forceImport)

    suspend fun syncExportBackupAndSummary(): SyncExecutionResult =
        driveSyncManager.syncExportBackupAndSummary()

    suspend fun restoreFromDriveBackup(): SyncExecutionResult =
        driveSyncManager.restoreFromDriveBackup()

    companion object {
        @Volatile
        private var INSTANCE: ForgeQuestRepository? = null

        fun getInstance(context: Context): ForgeQuestRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ForgeQuestRepository(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }
    }
}
