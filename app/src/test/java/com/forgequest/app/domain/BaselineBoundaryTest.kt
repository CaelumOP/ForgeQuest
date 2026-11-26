package com.forgequest.app.domain

import com.forgequest.app.data.db.BaselineTestEntity
import com.forgequest.app.data.seed.ExerciseCatalog
import com.forgequest.app.ui.baseline.BaselineTargetDisplay
import com.forgequest.app.ui.baseline.computeTargetDisplay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Table-driven boundary suite. Same cases and expected values as web baseline-boundary.test.ts. */
class BaselineBoundaryTest {
    private val catalog = ExerciseCatalog.all
    private fun ex(id: String) = catalog.first { it.id == id }

    @Test fun repsBoundaries() {
        // reps, target, tooHard, outgrown
        val cases = listOf(
            listOf(0, null, false, false),
            listOf(1, 1, true, false),
            listOf(2, 1, true, false),
            listOf(3, 2, true, false),
            listOf(4, 2, false, false),
            listOf(10, 7, false, false),
            listOf(24, 16, false, false),
            listOf(25, 17, false, true),
            listOf(29, 20, false, true),
            listOf(51, 20, false, true),
            listOf(200, 20, false, true),
        )
        for (c in cases) {
            val reps = c[0] as Int
            assertEquals("target $reps", c[1], BaselineTargets.repsTarget(reps))
            assertEquals("tooHard $reps", c[2], BaselineTargets.isTooHard(ExerciseKind.REPS, reps))
            assertEquals("outgrown $reps", c[3], BaselineTargets.isOutgrown(ExerciseKind.REPS, reps))
        }
        assertEquals(200, BaselineTargets.clampRepsInput(201))
        assertEquals(0, BaselineTargets.clampRepsInput(-4))
    }

    @Test fun holdBoundaries() {
        // seconds, target, tooHard, outgrown
        val cases = listOf(
            listOf(0, null, false, false),
            listOf(3, 3, true, false),
            listOf(10, 5, true, false),
            listOf(14, 5, true, false),
            listOf(15, 10, false, false),
            listOf(45, 30, false, false),
            listOf(89, 60, false, false),
            listOf(90, 60, false, true),
            listOf(825, 60, false, true),
        )
        for (c in cases) {
            val s = c[0] as Int
            assertEquals("target $s", c[1], BaselineTargets.holdTarget(s))
            assertEquals("tooHard $s", c[2], BaselineTargets.isTooHard(ExerciseKind.HOLD, s))
            assertEquals("outgrown $s", c[3], BaselineTargets.isOutgrown(ExerciseKind.HOLD, s))
        }
        assertEquals(600, BaselineTargets.clampHoldInput(601))
        val fmt = listOf(0 to "0 s", 45 to "45 s", 59 to "59 s", 60 to "1:00", 61 to "1:01", 575 to "9:35", 825 to "13:45")
        for ((s, text) in fmt) assertEquals("format $s", text, BaselineTargets.formatHoldSeconds(s, "s"))
        assertEquals(false, BaselineTargets.isOutgrown(ExerciseKind.WEIGHTED, 500))
        assertEquals(false, BaselineTargets.isTooHard(ExerciseKind.WEIGHTED, 1))
    }

    @Test fun weightedBoundaries() {
        assertEquals(WeightedResult.NoTarget, BaselineTargets.weightedTarget(0.0, 8, 8, 2.5))
        assertEquals(WeightedResult.NoTarget, BaselineTargets.weightedTarget(60.0, 0, 8, 2.5))
        assertEquals(WeightedResult.TooHeavy, BaselineTargets.weightedTarget(60.0, 1, 8, 2.5))
        assertEquals(WeightedResult.TooHeavy, BaselineTargets.weightedTarget(60.0, 2, 8, 2.5))
        assertEquals(WeightedResult.Target(50.0), BaselineTargets.weightedTarget(60.0, 8, 8, 2.5))
        assertEquals(WeightedResult.TooLight, BaselineTargets.weightedTarget(20.0, 13, 8, 2.5))
        assertEquals(WeightedResult.TooLight, BaselineTargets.weightedTarget(20.0, 16, 8, 2.5))
        assertEquals(500.0, BaselineTargets.clampWeightKgInput(9999.0), 0.0)
        assertEquals(100, BaselineTargets.clampWeightedRepsInput(300))
    }

    private val gears: List<Pair<String, Set<Equipment>>> =
        EquipmentPreset.entries.map { it.name to it.equipment } + listOf(
            "BODYWEIGHT" to setOf(Equipment.BODYWEIGHT),
            "DUMBBELLS_ONLY" to setOf(Equipment.BODYWEIGHT, Equipment.DUMBBELLS),
            "DUMBBELLS_BENCH" to setOf(Equipment.BODYWEIGHT, Equipment.DUMBBELLS, Equipment.FLAT_BENCH),
        )

    private fun allowedFor(gear: Set<Equipment>) = expandLegacyEquipment(gear) + Equipment.BODYWEIGHT

    @Test fun rungTable() {
        // move -> easier id, harder id, number of gear sets the move fits; the same on every gear it fits
        val rungs = mapOf(
            "push_up" to Triple("knee_push_up", "diamond_push_up", 10),
            "pull_up" to Triple("negative_pull_up", "archer_pull_up", 5),
            "australian_pull_up" to Triple("scapular_australian_pull_up", "pull_up", 4),
            "squat" to Triple("half_squat", "jump_squat", 10),
            "hip_bridge" to Triple(null, "single_leg_hip_thrust", 10),
            "plank" to Triple("incline_hollow_plank", "hollow_hold", 10),
        )
        for ((move, expected) in rungs) {
            val (easier, harder, fitCount) = expected
            val src = ex(move)
            var cells = 0
            for ((name, gear) in gears) {
                val allowed = allowedFor(gear)
                if (!ExerciseSwapEngine.profileFor(src).fits(allowed)) continue
                cells++
                val lo = BaselineTargets.easierVariation(src, catalog, gear)
                val hi = BaselineTargets.harderVariation(src, catalog, gear)
                assertEquals("$move easier on $name", easier, lo?.id)
                assertEquals("$move harder on $name", harder, hi?.id)
                for (rung in listOfNotNull(lo, hi)) {
                    assertTrue("${rung.id} fits $name", ExerciseSwapEngine.profileFor(rung).fits(allowed))
                    assertEquals("${rung.id} kind", src.kind, rung.kind)
                    val gap = abs(ExerciseTiers.of(rung)!!.ordinal - ExerciseTiers.of(src)!!.ordinal)
                    assertTrue("${rung.id} is at most one tier from $move", gap <= 1)
                }
            }
            assertEquals("$move gear cells", fitCount, cells)
        }
    }

    @Test fun weightedStaplesProgressByLoad() {
        val staples = listOf(
            "db_bench_press", "db_row", "goblet_squat", "db_romanian_deadlift", "bench_press",
            "lat_pulldown", "barbell_row", "back_squat", "romanian_deadlift",
        )
        for (id in staples) {
            val src = ex(id)
            for ((name, gear) in gears) {
                val allowed = allowedFor(gear)
                if (!ExerciseSwapEngine.profileFor(src).fits(allowed)) continue
                assertNull("$id harder on $name", BaselineTargets.harderVariation(src, catalog, gear))
                val lo = BaselineTargets.easierVariation(src, catalog, gear)
                if (lo != null) assertTrue("${lo.id} fits $name", ExerciseSwapEngine.profileFor(lo).fits(allowed))
            }
        }
    }

    private fun show(id: String, reps: Int? = null, seconds: Int? = null): BaselineTargetDisplay =
        computeTargetDisplay(
            exercise = ex(id),
            test = BaselineTestEntity(exerciseId = id, kind = ex(id).kind.name, reps = reps, seconds = seconds, createdAt = 0L),
            library = catalog,
            equipment = setOf(Equipment.BODYWEIGHT),
            unit = WeightUnit.KG,
        )

    @Test fun displayCases() {
        val long = show("plank", seconds = 825) as BaselineTargetDisplay.Hold
        assertEquals(60, long.seconds)
        assertEquals("hollow_hold", long.harderExercise?.id)
        assertNull(long.easierExercise)

        val short = show("plank", seconds = 3) as BaselineTargetDisplay.Hold
        assertEquals(3, short.seconds)
        assertNull(short.harderExercise)
        assertEquals("incline_hollow_plank", short.easierExercise?.id)

        val low = show("push_up", reps = 2) as BaselineTargetDisplay.Reps
        assertEquals(1, low.reps)
        assertNull(low.harderExercise)
        assertEquals("knee_push_up", low.easierExercise?.id)

        val big = show("squat", reps = 51) as BaselineTargetDisplay.Reps
        assertEquals(20, big.reps)
        assertEquals("jump_squat", big.harderExercise?.id)
        assertNull(big.easierExercise)

        val zero = show("plank", seconds = 0) as BaselineTargetDisplay.HoldTooShort
        assertEquals("incline_hollow_plank", zero.easierExercise?.id)

        val heavy = computeTargetDisplay(
            exercise = ex("db_bench_press"),
            test = BaselineTestEntity(exerciseId = "db_bench_press", kind = "WEIGHTED", reps = 2, weightKg = 60f, createdAt = 0L),
            library = catalog,
            equipment = setOf(Equipment.BODYWEIGHT, Equipment.DUMBBELLS, Equipment.FLAT_BENCH),
            unit = WeightUnit.KG,
        )
        assertEquals(BaselineTargetDisplay.TooHeavy, heavy)
    }
}
