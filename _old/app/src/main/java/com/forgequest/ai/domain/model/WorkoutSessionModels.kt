package com.forgequest.ai.domain.model

import com.forgequest.ai.data.local.entity.LoggedSetEntity
import com.forgequest.ai.data.local.entity.WorkoutSessionEntity

/**
 * State snapshot of an active or pending workout tracking session.
 * Exposes only the domain view needed by UI callers.
 */
data class WorkoutSessionSnapshot(
    val session: WorkoutSessionEntity?,
    val sets: List<LoggedSetEntity> = emptyList(),
    val totalSetsCount: Int = 0,
    val completedSetsCount: Int = 0,
    val progressFraction: Float = 0f,
    val isSessionActive: Boolean = false
) {
    companion object {
        fun from(session: WorkoutSessionEntity?, sets: List<LoggedSetEntity>): WorkoutSessionSnapshot {
            val total = sets.size
            val completed = sets.count { it.isCompleted }
            val progress = if (total > 0) completed.toFloat() / total.toFloat() else 0f
            return WorkoutSessionSnapshot(
                session = session,
                sets = sets,
                totalSetsCount = total,
                completedSetsCount = completed,
                progressFraction = progress,
                isSessionActive = session != null
            )
        }
    }
}

/**
 * Result of completing or unchecking a workout set.
 * Informs callers about haptic cues and rest timer guidance without leaking database details.
 */
data class SetToggleResult(
    val updatedSet: LoggedSetEntity,
    val isCompletedNow: Boolean,
    val shouldTriggerHaptic: Boolean,
    val recommendedRestDurationSeconds: Int = 90
)
