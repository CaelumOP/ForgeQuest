package com.forgequest.app.ui.workouts

import com.forgequest.app.data.db.BaselineTestEntity
import com.forgequest.app.data.db.PlanItemEntity
import com.forgequest.app.data.db.SetEntity
import com.forgequest.app.data.seed.ExerciseCatalog
import com.forgequest.app.domain.BaselineStatus
import com.forgequest.app.domain.SetEffort
import com.forgequest.app.domain.SetType
import com.forgequest.app.domain.WeightUnit
import org.junit.Assert.assertEquals
import org.junit.Test

class LiveWorkoutBaselineTest {

    private val byId = ExerciseCatalog.all.associateBy { it.id }

    private fun set(
        id: Long,
        sessionId: Long,
        exerciseId: String,
        weightKg: Float?,
        reps: Int? = 8,
        done: Boolean = true,
        effort: String = "",
        setType: SetType = SetType.WORKING,
    ) = SetEntity(
        id = id,
        sessionId = sessionId,
        exerciseId = exerciseId,
        itemPosition = 0,
        block = "Main",
        circuit = false,
        setIndex = id.toInt(),
        targetLabel = "8-12",
        reps = reps,
        weightKg = weightKg,
        done = done,
        effort = effort,
        setType = setType.name,
    )

    @Test
    fun resolveBaselineStatusReportsCalibratedEstimatedAndNone() {
        val bench = byId.getValue("bench_press")
        val dbBench = byId.getValue("db_bench_press")
        val ohp = byId.getValue("overhead_press")
        val pushUp = byId.getValue("push_up")
        val donorHistory = mapOf(
            bench.id to listOf(set(1L, 10L, bench.id, 100f, reps = 1, effort = SetEffort.FAILURE.name)),
        )

        // Own working history -> CALIBRATED.
        assertEquals(
            BaselineStatus.CALIBRATED,
            resolveBaselineStatus(
                exercise = bench,
                item = null,
                sessionSets = listOf(set(2L, 20L, bench.id, 80f, done = false)),
                recentFinished = donorHistory.getValue(bench.id),
                allHistory = donorHistory,
                library = byId,
                unit = WeightUnit.KG,
            ),
        )

        // Weighted, no own history, plan weight 0/null, session prefilled from cold start -> ESTIMATED.
        assertEquals(
            BaselineStatus.ESTIMATED,
            resolveBaselineStatus(
                exercise = dbBench,
                item = PlanItemEntity(dayId = 1L, position = 0, exerciseId = dbBench.id, block = "Main", circuit = false, sets = 3, repsMin = 8, repsMax = 12, weightKg = 0f),
                sessionSets = listOf(set(3L, 20L, dbBench.id, 25f, done = false)),
                recentFinished = emptyList(),
                allHistory = donorHistory,
                library = byId,
                unit = WeightUnit.KG,
            ),
        )

        // Explicit plan weight > 0 -> NONE (came from plan, not coldStart).
        assertEquals(
            BaselineStatus.NONE,
            resolveBaselineStatus(
                exercise = dbBench,
                item = PlanItemEntity(dayId = 1L, position = 0, exerciseId = dbBench.id, block = "Main", circuit = false, sets = 3, repsMin = 8, repsMax = 12, weightKg = 20f),
                sessionSets = listOf(set(4L, 20L, dbBench.id, 20f, done = false)),
                recentFinished = emptyList(),
                allHistory = donorHistory,
                library = byId,
                unit = WeightUnit.KG,
            ),
        )

        // No weight prefilled (e.g., no donor or 0 kg) -> NONE.
        assertEquals(
            BaselineStatus.NONE,
            resolveBaselineStatus(
                exercise = ohp,
                item = null,
                sessionSets = listOf(set(5L, 20L, ohp.id, 0f, done = false)),
                recentFinished = emptyList(),
                allHistory = emptyMap(),
                library = byId,
                unit = WeightUnit.KG,
            ),
        )

        // Bodyweight exercise -> NONE.
        assertEquals(
            BaselineStatus.NONE,
            resolveBaselineStatus(
                exercise = pushUp,
                item = null,
                sessionSets = listOf(set(6L, 20L, pushUp.id, null, reps = 10, done = false)),
                recentFinished = emptyList(),
                allHistory = donorHistory,
                library = byId,
                unit = WeightUnit.KG,
            ),
        )
    }

    @Test
    fun resolveBaselineStatusPrefersHistoryThenBaselineThenColdStart() {
        val bench = byId.getValue("bench_press")
        val dbBench = byId.getValue("db_bench_press")
        val pushUp = byId.getValue("push_up")
        val plank = byId.getValue("plank")
        val donorHistory = mapOf(
            bench.id to listOf(set(1L, 10L, bench.id, 100f, reps = 1, effort = SetEffort.FAILURE.name)),
        )
        val weightedBaseline = BaselineTestEntity(exerciseId = dbBench.id, kind = "WEIGHTED", reps = 8, weightKg = 20f, createdAt = 1L)

        // History wins over baseline test -> CALIBRATED.
        assertEquals(
            BaselineStatus.CALIBRATED,
            resolveBaselineStatus(
                exercise = dbBench,
                item = null,
                sessionSets = listOf(set(2L, 20L, dbBench.id, 25f, done = false)),
                recentFinished = listOf(set(1L, 10L, dbBench.id, 25f)),
                allHistory = donorHistory,
                library = byId,
                unit = WeightUnit.KG,
                baselineTest = weightedBaseline,
            ),
        )

        // Weighted baseline wins over coldStart -> FROM_BASELINE.
        assertEquals(
            BaselineStatus.FROM_BASELINE,
            resolveBaselineStatus(
                exercise = dbBench,
                item = PlanItemEntity(dayId = 1L, position = 0, exerciseId = dbBench.id, block = "Main", circuit = false, sets = 3, repsMin = 8, repsMax = 12, weightKg = 0f),
                sessionSets = listOf(set(3L, 20L, dbBench.id, 15f, done = false)),
                recentFinished = emptyList(),
                allHistory = donorHistory,
                library = byId,
                unit = WeightUnit.KG,
                baselineTest = weightedBaseline,
            ),
        )

        // TooLight weighted baseline falls back to coldStart donor -> ESTIMATED.
        assertEquals(
            BaselineStatus.ESTIMATED,
            resolveBaselineStatus(
                exercise = dbBench,
                item = PlanItemEntity(dayId = 1L, position = 0, exerciseId = dbBench.id, block = "Main", circuit = false, sets = 3, repsMin = 8, repsMax = 12, weightKg = 0f),
                sessionSets = listOf(set(4L, 20L, dbBench.id, 25f, done = false)),
                recentFinished = emptyList(),
                allHistory = donorHistory,
                library = byId,
                unit = WeightUnit.KG,
                baselineTest = BaselineTestEntity(exerciseId = dbBench.id, kind = "WEIGHTED", reps = 15, weightKg = 10f, createdAt = 1L),
            ),
        )

        // Bodyweight REPS baseline below repsMin -> FROM_BASELINE; above repsMin -> NONE.
        assertEquals(
            BaselineStatus.FROM_BASELINE,
            resolveBaselineStatus(
                exercise = pushUp,
                item = PlanItemEntity(dayId = 1L, position = 0, exerciseId = pushUp.id, block = "Main", circuit = false, sets = 3, repsMin = 8, repsMax = 12),
                sessionSets = listOf(set(5L, 20L, pushUp.id, null, reps = 5, done = false)),
                recentFinished = emptyList(),
                allHistory = emptyMap(),
                library = byId,
                unit = WeightUnit.KG,
                baselineTest = BaselineTestEntity(exerciseId = pushUp.id, kind = "REPS", reps = 8, createdAt = 1L),
            ),
        )
        assertEquals(
            BaselineStatus.NONE,
            resolveBaselineStatus(
                exercise = pushUp,
                item = PlanItemEntity(dayId = 1L, position = 0, exerciseId = pushUp.id, block = "Main", circuit = false, sets = 3, repsMin = 8, repsMax = 12),
                sessionSets = listOf(set(6L, 20L, pushUp.id, null, reps = 8, done = false)),
                recentFinished = emptyList(),
                allHistory = emptyMap(),
                library = byId,
                unit = WeightUnit.KG,
                baselineTest = BaselineTestEntity(exerciseId = pushUp.id, kind = "REPS", reps = 20, createdAt = 1L),
            ),
        )

        // Hold baseline -> FROM_BASELINE.
        assertEquals(
            BaselineStatus.FROM_BASELINE,
            resolveBaselineStatus(
                exercise = plank,
                item = PlanItemEntity(dayId = 1L, position = 0, exerciseId = plank.id, block = "Core", circuit = false, sets = 3, seconds = 30),
                sessionSets = listOf(set(7L, 20L, plank.id, null, reps = null, done = false).copy(seconds = 25)),
                recentFinished = emptyList(),
                allHistory = emptyMap(),
                library = byId,
                unit = WeightUnit.KG,
                baselineTest = BaselineTestEntity(exerciseId = plank.id, kind = "HOLD", seconds = 40, createdAt = 1L),
            ),
        )
    }
}
