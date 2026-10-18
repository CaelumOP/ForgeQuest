package com.forgequest.ai.domain.usecase

import com.forgequest.ai.data.local.dao.ForgeQuestDao
import com.forgequest.ai.data.local.entity.LoggedSetEntity
import com.forgequest.ai.data.local.entity.PlannedDayEntity
import com.forgequest.ai.data.local.entity.PlannedExerciseEntity
import com.forgequest.ai.data.local.entity.WorkoutSessionEntity
import com.forgequest.ai.domain.model.ExerciseCategory
import com.forgequest.ai.domain.model.MuscleGroup
import com.forgequest.ai.domain.model.QuestClearSummary
import com.forgequest.ai.domain.model.SetToggleResult
import com.forgequest.ai.domain.model.WorkoutSessionSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class LiveWorkoutSessionManager(
    private val store: WorkoutSessionStore
) {
    constructor(dao: ForgeQuestDao, finishWorkoutUseCase: FinishWorkoutUseCase = FinishWorkoutUseCase()) :
        this(RoomWorkoutSessionStoreAdapter(dao, finishWorkoutUseCase))

    /**
     * Reactive snapshot of the live workout session.
     * Combines active session state and live sets into a single cohesive contract.
     */
    val activeSessionSnapshotFlow: Flow<WorkoutSessionSnapshot> =
        store.observeActiveSession().flatMapLatest { session ->
            if (session != null) {
                store.observeSetsForSession(session.sessionId).combine(flowOf(session)) { sets, s ->
                    WorkoutSessionSnapshot.from(s, sets)
                }
            } else {
                flowOf(WorkoutSessionSnapshot(session = null))
            }
        }

    /**
     * Starts or resumes a workout session for the specified day and exercises.
     * Idempotent: if a session is already active for this day, returns its id immediately.
     */
    suspend fun startOrResumeSession(
        day: PlannedDayEntity,
        exercises: List<PlannedExerciseEntity>
    ): Long = withContext(Dispatchers.IO) {
        val existing = store.getActiveSession()
        if (existing != null && existing.dayId == day.dayId) {
            return@withContext existing.sessionId
        }
        if (existing != null) {
            store.deleteSetsForSession(existing.sessionId)
            store.deleteWorkoutSession(existing.sessionId)
        }

        val now = System.currentTimeMillis()
        val today = LocalDate.now().toEpochDay()
        val sessionId = store.insertWorkoutSession(
            WorkoutSessionEntity(
                planId = day.planId,
                dayId = day.dayId,
                dayTitle = day.title,
                startedAtEpochMillis = now,
                completedAtEpochMillis = null,
                epochDay = today,
                isSubmitted = false
            )
        )

        val initialSets = mutableListOf<LoggedSetEntity>()
        exercises.sortedBy { it.orderIndex }.forEach { ex ->
            val lastSets = store.getLastSessionSetsForExercise(ex.exerciseId, ex.name, sessionId)
            for (setNum in 1..ex.targetSets.coerceAtLeast(1)) {
                val prevSet = lastSets.find { it.setNumber == setNum } ?: lastSets.lastOrNull()
                val defaultReps = if (ex.category == ExerciseCategory.ISOMETRIC) {
                    0
                } else {
                    prevSet?.reps ?: ex.targetRepsOrSeconds
                }
                val defaultHold = if (ex.category == ExerciseCategory.ISOMETRIC) {
                    prevSet?.holdSeconds?.takeIf { it > 0 } ?: ex.targetRepsOrSeconds
                } else {
                    0
                }
                val defaultWeight = prevSet?.weightKg ?: 0f
                initialSets.add(
                    LoggedSetEntity(
                        sessionId = sessionId,
                        exerciseId = ex.exerciseId,
                        exerciseName = ex.name,
                        category = ex.category,
                        setNumber = setNum,
                        reps = defaultReps,
                        weightKg = defaultWeight,
                        holdSeconds = defaultHold,
                        isCompleted = false,
                        completedAtEpochMillis = 0L,
                        primaryMuscles = ex.primaryMuscles,
                        secondaryMuscles = ex.secondaryMuscles
                    )
                )
            }
        }
        if (initialSets.isNotEmpty()) {
            store.insertLoggedSets(initialSets)
        }
        sessionId
    }

    /**
     * Toggles set completion status with atomic timestamping.
     * Returns a [SetToggleResult] signaling haptic and timer cues.
     */
    suspend fun toggleSetCompleted(
        set: LoggedSetEntity,
        completed: Boolean,
        restDurationSeconds: Int = 90
    ): SetToggleResult = withContext(Dispatchers.IO) {
        val updated = set.copy(
            isCompleted = completed,
            completedAtEpochMillis = if (completed) System.currentTimeMillis() else 0L
        )
        store.updateLoggedSet(updated)
        SetToggleResult(
            updatedSet = updated,
            isCompletedNow = completed,
            shouldTriggerHaptic = completed,
            recommendedRestDurationSeconds = restDurationSeconds
        )
    }

    /**
     * Updates rep count, weight, or hold duration on an individual set.
     */
    suspend fun updateSetMetrics(set: LoggedSetEntity) = withContext(Dispatchers.IO) {
        store.updateLoggedSet(set)
    }

    /**
     * Swaps or appends an exercise in the active session.
     */
    suspend fun swapOrAddExercise(
        sessionId: Long,
        dayId: Long?,
        name: String,
        category: ExerciseCategory,
        targetSets: Int,
        targetRepsOrSeconds: Int,
        primaryMuscles: List<MuscleGroup>,
        secondaryMuscles: List<MuscleGroup> = emptyList(),
        replaceExerciseId: String? = null
    ) = withContext(Dispatchers.IO) {
        val cleanName = name.trim().ifBlank { "Custom Calisthenics Exercise" }
        val slug = cleanName.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_').take(24)
        val newExerciseId = "${slug}_${System.currentTimeMillis() % 100000}"

        if (replaceExerciseId != null) {
            val existingSets = store.getSetsForSession(sessionId).filter { it.exerciseId == replaceExerciseId }
            existingSets.forEach { store.deleteLoggedSet(it.setId) }
        }

        val setsToInsert = (1..targetSets.coerceAtLeast(1)).map { setNum ->
            LoggedSetEntity(
                sessionId = sessionId,
                exerciseId = newExerciseId,
                exerciseName = cleanName,
                category = category,
                setNumber = setNum,
                reps = if (category == ExerciseCategory.ISOMETRIC) 0 else targetRepsOrSeconds,
                weightKg = 0f,
                holdSeconds = if (category == ExerciseCategory.ISOMETRIC) targetRepsOrSeconds else 0,
                isCompleted = false,
                completedAtEpochMillis = 0L,
                primaryMuscles = primaryMuscles.ifEmpty { listOf(MuscleGroup.CHEST) },
                secondaryMuscles = secondaryMuscles
            )
        }
        store.insertLoggedSets(setsToInsert)

        if (dayId != null && dayId > 0L) {
            val dayExercises = store.getExercisesForDay(dayId)
            val nextOrder = (dayExercises.maxOfOrNull { it.orderIndex } ?: -1) + 1
            val encodedQuery = runCatching {
                URLEncoder.encode("Hemo Calisthenics $cleanName", StandardCharsets.UTF_8.name())
            }.getOrDefault("Hemo+Calisthenics+${cleanName.replace(' ', '+')}")
            store.upsertPlannedExercise(
                PlannedExerciseEntity(
                    exerciseId = newExerciseId,
                    dayId = dayId,
                    orderIndex = nextOrder,
                    name = cleanName,
                    category = category,
                    targetSets = targetSets.coerceAtLeast(1),
                    targetRepsOrSeconds = targetRepsOrSeconds.coerceAtLeast(1),
                    restDurationSeconds = 90,
                    primaryMuscles = primaryMuscles.ifEmpty { listOf(MuscleGroup.CHEST) },
                    secondaryMuscles = secondaryMuscles,
                    videoUrl = null,
                    videoSearchFallback = "https://www.youtube.com/results?search_query=$encodedQuery"
                )
            )
        }
    }

    /**
     * Completes and finalizes the active workout session.
     * Evaluates deferred PR detection, level up, XP, streak health, and muscle recovery.
     */
    suspend fun finishWorkout(sessionId: Long): QuestClearSummary = withContext(Dispatchers.IO) {
        store.executeFinishWorkout(sessionId)
    }

    /**
     * Discards the active workout session and its sets.
     */
    suspend fun discardSession(sessionId: Long) = withContext(Dispatchers.IO) {
        store.deleteSetsForSession(sessionId)
        store.deleteWorkoutSession(sessionId)
    }
}
