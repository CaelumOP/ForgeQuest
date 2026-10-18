package com.forgequest.ai.domain.usecase

import com.forgequest.ai.data.local.dao.ForgeQuestDao
import com.forgequest.ai.data.local.entity.LoggedSetEntity
import com.forgequest.ai.data.local.entity.MuscleRecoveryEntity
import com.forgequest.ai.data.local.entity.UserProfileEntity
import com.forgequest.ai.data.local.entity.WorkoutSessionEntity
import com.forgequest.ai.domain.model.ExerciseCategory
import com.forgequest.ai.domain.model.HunterRank
import com.forgequest.ai.domain.model.MuscleGroup
import com.forgequest.ai.domain.model.PrRecordCard
import com.forgequest.ai.domain.model.PrType
import com.forgequest.ai.domain.model.QuestClearSummary
import kotlin.math.max
import kotlin.math.roundToInt

class FinishWorkoutUseCase(
    private val evaluateStreakDecayUseCase: EvaluateStreakDecayUseCase = EvaluateStreakDecayUseCase()
) {

    /**
     * Pure, deterministic calculation for Deferred "Quest Clear" Post-Workout evaluation.
     *
     * CRITICAL: PR detection is ONLY executed here when the user finishes & submits a workout,
     * never during live set checkoffs or rep/weight edits.
     */
    fun evaluateSessionSubmission(
        session: WorkoutSessionEntity,
        sessionSets: List<LoggedSetEntity>,
        historicalCompletedSets: List<LoggedSetEntity>,
        profile: UserProfileEntity,
        alreadyCompletedWorkoutToday: Boolean = false
    ): Pair<QuestClearSummary, UserProfileEntity> {
        val completedSets = sessionSets.filter { it.isCompleted }
        val totalPlannedSetsCount = sessionSets.size
        val completedSetsCount = completedSets.size

        val prCards = detectPersonalRecords(
            completedSessionSets = completedSets,
            totalPlannedSetsCount = totalPlannedSetsCount,
            historicalCompletedSets = historicalCompletedSets.filter {
                it.isCompleted && it.sessionId != session.sessionId
            }
        )

        val setXp = completedSetsCount * XP_PER_COMPLETED_SET
        val completionBonusXp = if (completedSetsCount > 0) XP_WORKOUT_COMPLETION_BONUS else 0
        val prBonusXp = prCards.size * XP_PER_PR_CARD
        val earnedXp = setXp + completionBonusXp + prBonusXp

        val previousTotalXp = profile.totalXp
        val newTotalXp = previousTotalXp + earnedXp.toLong()
        val previousLevel = profile.level
        val newLevel = HunterRank.calculateLevel(newTotalXp)
        val newRank = HunterRank.forLevel(newLevel)

        val streakResult = evaluateStreakDecayUseCase.onWorkoutCompleted(
            profile = profile.copy(
                totalXp = newTotalXp,
                level = newLevel,
                rankCode = newRank.rankCode
            ),
            completedEpochDay = session.epochDay,
            alreadyCompletedWorkoutToday = alreadyCompletedWorkoutToday
        )

        val ignitedMuscles = mutableMapOf<MuscleGroup, Float>()
        completedSets.forEach { set ->
            set.primaryMuscles.forEach { muscle ->
                ignitedMuscles[muscle] = max(ignitedMuscles[muscle] ?: 0f, PRIMARY_MUSCLE_FACTOR)
            }
            set.secondaryMuscles.forEach { muscle ->
                ignitedMuscles[muscle] = max(ignitedMuscles[muscle] ?: 0f, SECONDARY_MUSCLE_FACTOR)
            }
        }

        val summary = QuestClearSummary(
            sessionId = session.sessionId,
            workoutTitle = session.dayTitle,
            completedSetsCount = completedSetsCount,
            totalPlannedSetsCount = totalPlannedSetsCount,
            earnedXp = earnedXp,
            previousTotalXp = previousTotalXp,
            newTotalXp = newTotalXp,
            previousLevel = previousLevel,
            newLevel = newLevel,
            newRank = newRank,
            prCards = prCards,
            reignitedFlame = streakResult.reignitedFlame,
            previousFlameState = streakResult.previousFlameState,
            newFlameState = streakResult.newFlameState,
            newStreakDays = streakResult.updatedProfile.currentWorkoutStreakDays,
            ignitedMuscles = ignitedMuscles
        )

        return summary to streakResult.updatedProfile
    }

    /**
     * Detects Personal Records across all exercises in the submitted session compared
     * against prior submitted sessions.
     */
    fun detectPersonalRecords(
        completedSessionSets: List<LoggedSetEntity>,
        totalPlannedSetsCount: Int,
        historicalCompletedSets: List<LoggedSetEntity>
    ): List<PrRecordCard> {
        if (completedSessionSets.isEmpty()) return emptyList()

        val prCards = mutableListOf<PrRecordCard>()
        val setsByExercise = completedSessionSets.groupBy { it.exerciseId }

        for ((exerciseId, currentSets) in setsByExercise) {
            val firstSet = currentSets.first()
            val exerciseName = firstSet.exerciseName
            val category = firstSet.category

            val priorSets = historicalCompletedSets.filter {
                it.exerciseId == exerciseId || it.exerciseName.equals(exerciseName, ignoreCase = true)
            }

            // 1. All-Time Max Single-Set Reps (or Hold Seconds for Isometric)
            val currentMaxSingle = currentSets.maxOf { primaryMetricForSet(it) }
            val previousMaxSingle = priorSets.maxOfOrNull { primaryMetricForSet(it) } ?: 0
            if (currentMaxSingle > previousMaxSingle && currentMaxSingle > 0) {
                val unit = if (category == ExerciseCategory.ISOMETRIC) "sec" else "reps"
                val prevLabel = if (previousMaxSingle > 0) "$previousMaxSingle $unit" else "First Baseline PR"
                prCards.add(
                    PrRecordCard(
                        exerciseId = exerciseId,
                        exerciseName = exerciseName,
                        prType = PrType.MAX_SINGLE_SET_REPS,
                        newValueLabel = "$currentMaxSingle $unit",
                        previousValueLabel = prevLabel
                    )
                )
            }

            // 2. All-Time Max Exercise Volume in a Single Session
            val currentVolume = calculateExerciseSessionVolume(currentSets)
            val priorMaxSessionVolume = priorSets
                .groupBy { it.sessionId }
                .values
                .maxOfOrNull { calculateExerciseSessionVolume(it) } ?: 0f

            if (currentVolume > priorMaxSessionVolume && currentVolume > 0f) {
                val prevVolumeLabel = if (priorMaxSessionVolume > 0f) {
                    formatVolumeLabel(priorMaxSessionVolume, category)
                } else {
                    "First Session Volume PR"
                }
                prCards.add(
                    PrRecordCard(
                        exerciseId = exerciseId,
                        exerciseName = exerciseName,
                        prType = PrType.MAX_EXERCISE_VOLUME,
                        newValueLabel = formatVolumeLabel(currentVolume, category),
                        previousValueLabel = prevVolumeLabel
                    )
                )
            }
        }

        // 3. 100% Target Quest Clear PR Badge
        if (totalPlannedSetsCount > 0 && completedSessionSets.size >= totalPlannedSetsCount) {
            prCards.add(
                PrRecordCard(
                    exerciseId = "quest_full_clear",
                    exerciseName = "Full Quest Completion",
                    prType = PrType.QUEST_100_PERCENT_CLEAR,
                    newValueLabel = "${completedSessionSets.size}/$totalPlannedSetsCount Sets (100%)",
                    previousValueLabel = "Target Cleared"
                )
            )
        }

        return prCards
    }

    /**
     * Executes the full database transaction to finalize a workout session, persist XP/Rank/Streak,
     * update 48-hour muscle recovery timestamps, and return the [QuestClearSummary].
     */
    suspend fun execute(
        dao: ForgeQuestDao,
        sessionId: Long,
        nowEpochMillis: Long = System.currentTimeMillis()
    ): QuestClearSummary {
        val session = dao.getSessionById(sessionId)
            ?: throw IllegalArgumentException("Workout session $sessionId not found")
        val sessionSets = dao.getSetsForSession(sessionId)
        val allHistoricalCompleted = dao.getAllSubmittedCompletedSets()
        val currentProfile = dao.getUserProfile() ?: UserProfileEntity()

        val priorSessionsToday = dao.getCompletedSessionsForDay(session.epochDay)
            .any { it.sessionId != sessionId }

        val (summary, updatedProfile) = evaluateSessionSubmission(
            session = session,
            sessionSets = sessionSets,
            historicalCompletedSets = allHistoricalCompleted,
            profile = currentProfile,
            alreadyCompletedWorkoutToday = priorSessionsToday
        )

        val finalizedSession = session.copy(
            completedAtEpochMillis = nowEpochMillis,
            isSubmitted = true,
            earnedXp = summary.earnedXp,
            prsBrokenCount = summary.prCards.size,
            reignitedFlame = summary.reignitedFlame
        )

        dao.updateWorkoutSession(finalizedSession)
        dao.upsertUserProfile(updatedProfile)

        if (summary.ignitedMuscles.isNotEmpty()) {
            val recoveryEntities = summary.ignitedMuscles.map { (muscle, intensityFactor) ->
                MuscleRecoveryEntity(
                    muscleGroup = muscle,
                    lastTrainedEpochMillis = nowEpochMillis,
                    lastIntensityFactor = intensityFactor
                )
            }
            dao.upsertMuscleRecoveries(recoveryEntities)
        }

        return summary
    }

    private fun primaryMetricForSet(set: LoggedSetEntity): Int {
        return if (set.category == ExerciseCategory.ISOMETRIC) {
            max(set.holdSeconds, set.reps)
        } else {
            set.reps
        }
    }

    private fun formatVolumeLabel(volume: Float, category: ExerciseCategory): String {
        val rounded = volume.roundToInt()
        return if (category == ExerciseCategory.ISOMETRIC) {
            "$rounded sec·vol"
        } else {
            "$rounded vol"
        }
    }

    companion object {
        const val XP_PER_COMPLETED_SET = 25
        const val XP_WORKOUT_COMPLETION_BONUS = 150
        const val XP_PER_PR_CARD = 100

        const val PRIMARY_MUSCLE_FACTOR = 1.0f
        const val SECONDARY_MUSCLE_FACTOR = 0.5f
        const val RECOVERY_WINDOW_HOURS = 48f

        fun calculateExerciseSessionVolume(sets: List<LoggedSetEntity>): Float {
            return sets.filter { it.isCompleted }.sumOf { set ->
                val baseUnits = if (set.category == ExerciseCategory.ISOMETRIC) {
                    max(set.holdSeconds, set.reps).toFloat()
                } else {
                    set.reps.toFloat()
                }
                val multiplier = max(1f, set.weightKg)
                (baseUnits * multiplier).toDouble()
            }.toFloat()
        }

        /**
         * Computes the 48-hour muscle heatmap intensity (`0.0f` completely recovered -> `1.0f` freshly ignited):
         * - `0–16h`: `1.0 -> 0.67` (Inferno Coral `#FF5252`)
         * - `16–36h`: `0.66 -> 0.25` (Recovering Amber `#FFB74D`)
         * - `36–48h+`: `0.24 -> 0.0` (Ready Emerald `#69F0AE` / Cool Slate)
         */
        fun calculateMuscleIntensity(
            lastTrainedEpochMillis: Long,
            nowEpochMillis: Long,
            baseFactor: Float = 1.0f
        ): Float {
            if (lastTrainedEpochMillis <= 0L) return 0f
            val elapsedMillis = (nowEpochMillis - lastTrainedEpochMillis).coerceAtLeast(0L)
            val hoursSinceTrained = elapsedMillis / 3_600_000f
            val linearDecay = max(0f, 1f - (hoursSinceTrained / RECOVERY_WINDOW_HOURS))
            return (linearDecay * baseFactor.coerceIn(0f, 1f)).coerceIn(0f, 1f)
        }
    }
}
