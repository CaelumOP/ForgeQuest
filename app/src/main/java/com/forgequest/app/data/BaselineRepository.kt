package com.forgequest.app.data

import androidx.room.withTransaction
import com.forgequest.app.data.db.AppDatabase
import com.forgequest.app.data.db.BaselineTestEntity
import com.forgequest.app.data.plan.PlanAnswers
import com.forgequest.app.data.prefs.SettingsRepository
import com.forgequest.app.domain.ExerciseKind
import com.forgequest.app.domain.PlanInput
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Stores and queries starting-point baseline tests (`baseline_tests`).
 * Baseline rows never create sessions or sets and never affect streaks, XP, PRs, volume, or history.
 */
class BaselineRepository(
    private val db: AppDatabase,
    private val settings: SettingsRepository? = null,
) {
    private val dao = db.baselineTests()
    private val migrateMutex = Mutex()

    fun observe(): Flow<List<BaselineTestEntity>> = dao.observeAll()

    fun observeLatest(): Flow<Map<String, BaselineTestEntity>> =
        observe().map { rows -> rows.groupBy { it.exerciseId }.mapValues { (_, list) -> list.first() } }

    suspend fun save(test: BaselineTestEntity): Long = dao.insert(test)

    suspend fun latest(exerciseId: String): BaselineTestEntity? = dao.latestFor(exerciseId)

    suspend fun latestAll(): List<BaselineTestEntity> = dao.latestAll()

    suspend fun all(): List<BaselineTestEntity> = dao.all()

    suspend fun deleteFor(exerciseId: String) = dao.deleteFor(exerciseId)

    suspend fun deleteAll() = dao.deleteAll()

    /**
     * One-time migration of saved plan-builder `pushUpMax` / `pullUpMax` answers into baseline rows,
     * inserting only when no row already exists for that catalog id. Keeps `planBuilderAnswers` readable.
     */
    suspend fun migrateFromPlanAnswers() {
        val repo = settings ?: return
        migrateMutex.withLock {
            val answers = PlanAnswers.decode(repo.current().planBuilderAnswers) ?: return
            val push = answers.pushUpMax
            val pull = answers.pullUpMax
            if (push == null && pull == null) return
            val now = System.currentTimeMillis()
            db.withTransaction {
                if (push != null && dao.latestFor(PUSH_UP_ID) == null) {
                    dao.insert(
                        BaselineTestEntity(
                            exerciseId = PUSH_UP_ID,
                            kind = ExerciseKind.REPS.name,
                            reps = push,
                            createdAt = now,
                        )
                    )
                }
                if (pull != null && dao.latestFor(PULL_UP_ID) == null) {
                    dao.insert(
                        BaselineTestEntity(
                            exerciseId = PULL_UP_ID,
                            kind = ExerciseKind.REPS.name,
                            reps = pull,
                            createdAt = now,
                        )
                    )
                }
            }
        }
    }

    /**
     * Assembles [PlanInput] using the latest `push_up` / `pull_up` baseline rows when present,
     * falling back to the saved [PlanAnswers] values.
     */
    suspend fun resolvePlanInput(answers: PlanAnswers): PlanInput {
        migrateFromPlanAnswers()
        val push = latest(PUSH_UP_ID)?.takeIf { it.kind == ExerciseKind.REPS.name }?.reps
        val pull = latest(PULL_UP_ID)?.takeIf { it.kind == ExerciseKind.REPS.name }?.reps
        val squat = latest(SQUAT_ID)?.takeIf { it.kind == ExerciseKind.REPS.name }?.reps
        val hinge = latest(HIP_BRIDGE_ID)?.takeIf { it.kind == ExerciseKind.REPS.name }?.reps
        val plank = latest(PLANK_ID)?.takeIf { it.kind == ExerciseKind.HOLD.name }?.seconds
        return answers.toInput(
            baselinePushUpMax = push,
            baselinePullUpMax = pull,
            baselineSquatMax = squat,
            baselineHingeMax = hinge,
            baselinePlankSeconds = plank,
        )
    }

    companion object {
        const val PUSH_UP_ID = "push_up"
        const val PULL_UP_ID = "pull_up"
        const val SQUAT_ID = "squat"
        const val HIP_BRIDGE_ID = "hip_bridge"
        const val PLANK_ID = "plank"
    }
}
