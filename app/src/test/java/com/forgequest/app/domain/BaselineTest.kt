package com.forgequest.app.domain

import com.forgequest.app.data.db.ExerciseEntity
import com.forgequest.app.data.db.SetEntity
import com.forgequest.app.data.seed.ExerciseCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BaselineTest {

    private val byId = ExerciseCatalog.all.associateBy { it.id }

    private fun set(
        id: Long,
        sessionId: Long,
        exerciseId: String,
        weightKg: Float?,
        reps: Int? = 8,
        effort: String = "",
        setType: SetType = SetType.WORKING,
        done: Boolean = true,
        block: String = "Main",
        doneAt: Long? = sessionId * 1000L,
    ) = SetEntity(
        id = id,
        sessionId = sessionId,
        exerciseId = exerciseId,
        itemPosition = 0,
        block = block,
        circuit = false,
        setIndex = id.toInt(),
        targetLabel = "8-12",
        reps = reps,
        weightKg = weightKg,
        done = done,
        effort = effort,
        doneAt = doneAt,
        setType = setType.name,
    )

    @Test
    fun e1rmCapsAtTwelveRepsAndIgnoresHigher() {
        assertEquals(140f, Baseline.e1rm(100f, 10, SetEffort.EASY), 0.01f)
        assertEquals(140f, Baseline.e1rm(100f, 12, SetEffort.OK), 0.01f)
        assertEquals(0f, Baseline.e1rm(100f, 13, SetEffort.FAILURE), 0.01f)
        assertEquals(0f, Baseline.e1rm(100f, 15, SetEffort.OK), 0.01f)
        assertEquals(0f, Baseline.e1rm(100f, 0, SetEffort.OK), 0.01f)
    }

    @Test
    fun e1rmUsesRirFromEffort() {
        assertEquals(130f, Baseline.e1rm(100f, 6, SetEffort.EASY), 0.01f)
        assertEquals(126.67f, Baseline.e1rm(100f, 6, SetEffort.OK), 0.02f)
        assertEquals(123.33f, Baseline.e1rm(100f, 6, SetEffort.HARD), 0.02f)
        assertEquals(120f, Baseline.e1rm(100f, 6, SetEffort.FAILURE), 0.01f)
    }

    @Test
    fun blankEffortCountsTwoInReserve() {
        assertEquals(
            Baseline.e1rm(100f, 6, SetEffort.OK),
            Baseline.e1rm(100f, 6, SetEffort.of("", "")),
            0.001f,
        )
        val workingBlank = listOf(set(1L, 1L, "bench_press", 100f, reps = 6, effort = ""))
        assertEquals(126.67f, Baseline.exerciseE1rm(workingBlank)!!, 0.02f)
    }

    @Test
    fun singleToFailureIsTheWeight() {
        assertEquals(120f, Baseline.e1rm(120f, 1, SetEffort.FAILURE), 0.001f)
        assertEquals(132f, Baseline.e1rm(120f, 1, SetEffort.OK), 0.01f)
    }

    @Test
    fun zeroOrNegativeWeightGivesZero() {
        assertEquals(0f, Baseline.e1rm(0f, 8, SetEffort.OK), 0.001f)
        assertEquals(0f, Baseline.e1rm(-10f, 8, SetEffort.OK), 0.001f)
        assertEquals(0f, Baseline.e1rm(Float.NaN, 8, SetEffort.OK), 0.001f)
    }

    @Test
    fun exerciseE1rmIgnoresWarmupsAndUsesLastTwoSessions() {
        // Caller passes newest session first: 30, 20, 10.
        val sets = listOf(
            set(1L, 30L, "bench_press", 150f, reps = 8, setType = SetType.WARMUP),
            set(2L, 30L, "bench_press", 200f, reps = 5, done = false),
            set(3L, 30L, "bench_press", 80f, reps = 5, effort = SetEffort.OK.name),
            set(4L, 20L, "bench_press", 90f, reps = 5, effort = SetEffort.HARD.name),
            set(5L, 10L, "bench_press", 200f, reps = 5, effort = SetEffort.FAILURE.name),
        )
        // Session 30: 80*(1+7/30)=98.67; Session 20: 90*(1+6/30)=108; Session 10 ignored.
        assertEquals(108f, Baseline.exerciseE1rm(sets)!!, 0.01f)
        assertNull(Baseline.exerciseE1rm(emptyList()))
    }

    @Test
    fun coldStartFromSamePatternBarbellToDumbbell() {
        val bench = byId.getValue("bench_press")
        val dbBench = byId.getValue("db_bench_press")
        val history = mapOf(
            bench.id to listOf(set(1L, 10L, bench.id, 100f, reps = 1, effort = SetEffort.FAILURE.name)),
        )
        val est = Baseline.coldStart(dbBench, repsMin = 8, WeightUnit.KG, history, byId)
        assertEquals(BaselineStatus.ESTIMATED, est.status)
        assertEquals(bench.id, est.donorId)
        // 100 / 1.0 * 0.4 * 0.9 = 36 -> 36 / (1 + 11/30) = 26.34 -> floor to 2.5 = 25.0
        assertEquals(25f, est.weightKg!!, 0.01f)
    }

    private fun dbBenchOf() = byId.getValue("db_bench_press")

    @Test
    fun coldStartPressToOverheadUsesSmallerTransfer() {
        val bench = byId.getValue("bench_press")
        val ohp = byId.getValue("overhead_press")
        val history = mapOf(
            bench.id to listOf(set(1L, 10L, bench.id, 100f, reps = 1, effort = SetEffort.FAILURE.name)),
        )
        val est = Baseline.coldStart(ohp, repsMin = 8, WeightUnit.KG, history, byId)
        assertEquals(BaselineStatus.ESTIMATED, est.status)
        assertEquals(bench.id, est.donorId)
        // Horizontal -> vertical push transfers 0.9 x 0.6 (audit S6): 100 * 0.54 = 54 -> 54 / (1 + 11/30) = 39.5 -> 37.5
        assertEquals(37.5f, est.weightKg!!, 0.01f)
        // Flyes never donate (Gate point 4).
        val fly = byId.getValue("db_fly")
        val flyOnly = mapOf(fly.id to listOf(set(1L, 10L, fly.id, 20f, reps = 10, effort = SetEffort.OK.name)))
        assertEquals(BaselineStatus.NONE, Baseline.coldStart(dbBenchOf(), repsMin = 8, WeightUnit.KG, flyOnly, byId).status)
    }

    @Test
    fun backDatedWorkoutDoesNotWinRecency() {
        val bench = byId.getValue("bench_press")
        val machine = byId.getValue("chest_press_machine")
        val dbBench = byId.getValue("db_bench_press")
        // History lists the newest workout first. The machine workout was logged later (higher session id) but dated earlier.
        val history = linkedMapOf(
            bench.id to listOf(set(1L, 10L, bench.id, 100f, reps = 1, effort = SetEffort.FAILURE.name)),
            machine.id to listOf(set(2L, 99L, machine.id, 200f, reps = 1, effort = SetEffort.FAILURE.name)),
        )
        val est = Baseline.coldStart(dbBench, repsMin = 8, WeightUnit.KG, history, byId)
        assertEquals(bench.id, est.donorId)
    }

    @Test
    fun donorsOnlyInOtherPatternsGiveNoEstimate() {
        val squat = byId.getValue("back_squat")
        val dbBench = byId.getValue("db_bench_press")
        // Plenty of history, but nothing that pushes: no guessed load for a pressing lift.
        val history = linkedMapOf(squat.id to listOf(set(1L, 10L, squat.id, 140f, reps = 5)))
        val est = Baseline.coldStart(dbBench, repsMin = 8, WeightUnit.KG, history, byId)
        assertEquals(BaselineStatus.NONE, est.status)
        assertNull(est.weightKg)
        assertNull(est.donorId)
    }

    @Test
    fun newBodyweightExerciseNeverGetsAGuessedLoad() {
        val bench = byId.getValue("bench_press")
        val pushUp = byId.getValue("push_up")
        val history = linkedMapOf(bench.id to listOf(set(1L, 10L, bench.id, 100f, reps = 5)))
        val est = Baseline.coldStart(pushUp, repsMin = 8, WeightUnit.KG, history, byId)
        // Bodyweight moves keep the plan's own reps (and tier from the plan builder); no external load is invented.
        assertEquals(BaselineStatus.NONE, est.status)
        assertNull(est.weightKg)
    }

    @Test
    fun coldStartWithNoDonorIsBlank() {
        val bench = byId.getValue("bench_press")
        val squat = byId.getValue("back_squat")
        val history = mapOf(
            squat.id to listOf(set(1L, 10L, squat.id, 140f, reps = 1, effort = SetEffort.FAILURE.name)),
        )
        val est = Baseline.coldStart(bench, repsMin = 8, WeightUnit.KG, history, byId)
        assertEquals(BaselineStatus.NONE, est.status)
        assertNull(est.weightKg)
        assertNull(est.donorId)
        assertEquals(BaselineStatus.NONE, Baseline.coldStart(bench, 8, WeightUnit.KG, emptyMap(), byId).status)
    }

    @Test
    fun coldStartBelowOneLoadStepIsBlank() {
        val bbCurl = byId.getValue("barbell_curl")
        val dbCurl = byId.getValue("bicep_curl")
        val history = mapOf(
            bbCurl.id to listOf(set(1L, 10L, bbCurl.id, 5f, reps = 1, effort = SetEffort.FAILURE.name)),
        )
        // 5 / 1.0 * 0.4 * 0.9 = 1.8 -> 1.8 / (1 + 13/30) = 1.26 kg < 2.5 kg load step.
        val est = Baseline.coldStart(dbCurl, repsMin = 10, WeightUnit.KG, history, byId)
        assertEquals(BaselineStatus.NONE, est.status)
        assertNull(est.weightKg)
    }

    @Test
    fun coldStartInPoundsFloorsToFivePounds() {
        val bench = byId.getValue("bench_press")
        val incline = byId.getValue("incline_bench_press")
        val history = mapOf(
            bench.id to listOf(set(1L, 10L, bench.id, 100f, reps = 1, effort = SetEffort.FAILURE.name)),
        )
        val est = Baseline.coldStart(incline, repsMin = 8, WeightUnit.LB, history, byId)
        assertEquals(BaselineStatus.ESTIMATED, est.status)
        // 90 / (1 + 11/30) = 65.85 kg = 145.18 lb -> floors to 145 lb.
        assertEquals(145f, WeightUnit.LB.fromKg(est.weightKg!!), 0.01f)
    }

    @Test
    fun calibratedExerciseReturnsCalibratedStatus() {
        val bench = byId.getValue("bench_press")
        val incline = byId.getValue("incline_bench_press")
        val history = mapOf(
            bench.id to listOf(set(1L, 10L, bench.id, 80f, reps = 8)),
            incline.id to listOf(set(2L, 11L, incline.id, 90f, reps = 8)),
        )
        val est = Baseline.coldStart(bench, repsMin = 8, WeightUnit.KG, history, byId)
        assertEquals(BaselineStatus.CALIBRATED, est.status)
        assertNull(est.weightKg)
        assertNull(est.donorId)
    }

    @Test
    fun bucketMapping() {
        assertEquals(PatternBucket.PUSH, Baseline.bucketOf(MovementPattern.HORIZONTAL_PUSH))
        assertEquals(PatternBucket.PUSH, Baseline.bucketOf(MovementPattern.DIP_PUSH))
        assertEquals(PatternBucket.PUSH, Baseline.bucketOf(MovementPattern.VERTICAL_PUSH))

        assertEquals(PatternBucket.PULL, Baseline.bucketOf(MovementPattern.VERTICAL_PULL))
        assertEquals(PatternBucket.PULL, Baseline.bucketOf(MovementPattern.HORIZONTAL_PULL))
        assertEquals(PatternBucket.PULL, Baseline.bucketOf(MovementPattern.UPPER_BACK_PULL))

        assertEquals(PatternBucket.SQUAT, Baseline.bucketOf(MovementPattern.SQUAT_BILATERAL))
        assertEquals(PatternBucket.SQUAT, Baseline.bucketOf(MovementPattern.SQUAT_UNILATERAL))

        assertEquals(PatternBucket.HINGE, Baseline.bucketOf(MovementPattern.HIP_HINGE))

        assertEquals(PatternBucket.CORE, Baseline.bucketOf(MovementPattern.CORE_ANTI_EXTENSION))
        assertEquals(PatternBucket.CORE, Baseline.bucketOf(MovementPattern.CORE_FLEXION_ROTATION))

        assertNull(Baseline.bucketOf(MovementPattern.MOBILITY))
        assertNull(Baseline.bucketOf(MovementPattern.LATERAL_RAISE))
        assertNull(Baseline.bucketOf(MovementPattern.ELBOW_FLEXION))
        assertNull(Baseline.bucketOf(MovementPattern.ELBOW_EXTENSION))
    }

    @Test
    fun equipmentRatioCoversRealEquipmentAndMultiEquipmentCustom() {
        assertEquals(1.0f, Baseline.equipmentRatio(byId.getValue("bench_press"))!!, 0.001f)
        assertEquals(0.9f, Baseline.equipmentRatio(byId.getValue("smith_squat"))!!, 0.001f)
        assertEquals(0.4f, Baseline.equipmentRatio(byId.getValue("db_bench_press"))!!, 0.001f)
        assertEquals(0.4f, Baseline.equipmentRatio(byId.getValue("kb_swing"))!!, 0.001f)
        assertEquals(0.6f, Baseline.equipmentRatio(byId.getValue("cable_fly"))!!, 0.001f)
        assertEquals(0.6f, Baseline.equipmentRatio(byId.getValue("lat_pulldown"))!!, 0.001f)
        assertEquals(0.6f, Baseline.equipmentRatio(byId.getValue("seated_cable_row"))!!, 0.001f)
        assertEquals(1.0f, Baseline.equipmentRatio(byId.getValue("leg_press"))!!, 0.001f)
        assertNull(Baseline.equipmentRatio(byId.getValue("push_up")))
        assertNull(Baseline.equipmentRatio(byId.getValue("band_row")))

        val customMulti = ExerciseEntity(
            id = "custom_bench_bar",
            name = "Custom Floor Bench",
            kind = ExerciseKind.WEIGHTED,
            primary = listOf(Muscle.CHEST),
            secondary = emptyList(),
            custom = true,
            equipment = "FLAT_BENCH,BARBELL",
            pattern = "HORIZONTAL_PUSH",
        )
        assertEquals(1.0f, Baseline.equipmentRatio(customMulti)!!, 0.001f)
    }
}
