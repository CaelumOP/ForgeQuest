package com.forgequest.app.domain

import com.forgequest.app.data.db.BaselineTestEntity
import com.forgequest.app.data.db.ExerciseEntity
import com.forgequest.app.data.seed.ExerciseCatalog
import com.forgequest.app.ui.baseline.BaselineLevel
import com.forgequest.app.ui.baseline.BaselineNudge
import com.forgequest.app.ui.baseline.BaselineTargetDisplay
import com.forgequest.app.ui.baseline.baselineNudge
import com.forgequest.app.ui.baseline.computeTargetDisplay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Exhaustive invariants for the starting-point progression graph. Same assertions as web progression-graph.test.ts. */
class ProgressionGraphTest {
    private val catalog = ExerciseCatalog.all
    private fun ex(id: String) = catalog.first { it.id == id }
    private val moves = catalog.filter { ExerciseTiers.isProgressionMove(it) }

    private val gears: List<Pair<String, Set<Equipment>>> =
        EquipmentPreset.entries.map { it.name to it.equipment } + listOf(
            "BODYWEIGHT" to setOf(Equipment.BODYWEIGHT),
            "DUMBBELLS_ONLY" to setOf(Equipment.BODYWEIGHT, Equipment.DUMBBELLS),
            "DUMBBELLS_BENCH" to setOf(Equipment.BODYWEIGHT, Equipment.DUMBBELLS, Equipment.FLAT_BENCH),
        )

    private fun allowedFor(gear: Set<Equipment>) = expandLegacyEquipment(gear) + Equipment.BODYWEIGHT
    private fun fits(e: ExerciseEntity, allowed: Set<Equipment>) = ExerciseSwapEngine.profileFor(e).fits(allowed)
    private fun tier(e: ExerciseEntity) = ExerciseTiers.of(e)!!.ordinal
    private fun family(e: ExerciseEntity) = ExerciseTiers.patternFamily(ExerciseSwapEngine.profileFor(e).pattern)

    private class Cell(val move: ExerciseEntity, val gear: Set<Equipment>, val gearName: String, val allowed: Set<Equipment>)

    private val cells: List<Cell> = gears.flatMap { (gearName, gear) ->
        val allowed = allowedFor(gear)
        moves.filter { fits(it, allowed) }.map { Cell(it, gear, gearName, allowed) }
    }

    private fun walk(c: Cell, step: (ExerciseEntity) -> ExerciseEntity?): List<ExerciseEntity> {
        val path = mutableListOf(c.move)
        repeat(10) {
            val next = step(path.last()) ?: return path
            check(path.none { it.id == next.id }) { "cycle at ${next.id} from ${c.move.id} on ${c.gearName}" }
            path += next
        }
        error("no end within 10 steps from ${c.move.id} on ${c.gearName}")
    }

    private fun harder(c: Cell, e: ExerciseEntity) = BaselineTargets.harderVariation(e, catalog, c.gear)
    private fun easier(c: Cell, e: ExerciseEntity) = BaselineTargets.easierVariation(e, catalog, c.gear)

    @Test fun walkingHarderTerminatesAndStaysValid() {
        for (c in cells) {
            val path = walk(c) { harder(c, it) }
            for (i in 1 until path.size) {
                val from = path[i - 1]
                val to = path[i]
                val label = "${from.id} -> ${to.id} on ${c.gearName}"
                assertTrue("${to.id} in scope", ExerciseTiers.isProgressionMove(to))
                assertTrue("${to.id} fits", fits(to, c.allowed))
                assertTrue("tier $label", tier(to) >= tier(from))
                if (tier(to) - tier(from) == 2) {
                    val plusOne = moves.any { it.id != from.id && fits(it, c.allowed) && family(it) == family(from) && tier(it) == tier(from) + 1 }
                    assertFalse("skipped a tier $label", plusOne)
                }
            }
        }
    }

    @Test fun walkingEasierTerminatesAndStaysValid() {
        for (c in cells) {
            val path = walk(c) { easier(c, it) }
            for (i in 1 until path.size) {
                val from = path[i - 1]
                val to = path[i]
                assertTrue("${to.id} in scope", ExerciseTiers.isProgressionMove(to))
                assertTrue("${to.id} fits", fits(to, c.allowed))
                assertTrue("tier ${from.id} -> ${to.id} on ${c.gearName}", tier(to) <= tier(from))
            }
        }
    }

    @Test fun noPrematureDeadEnd() {
        for (c in cells) {
            val sameFamily = moves.filter { it.id != c.move.id && fits(it, c.allowed) && family(it) == family(c.move) }
            if (harder(c, c.move) == null) {
                assertFalse(
                    "${c.move.id} has no harder on ${c.gearName} but one fits",
                    sameFamily.any { tier(it) > tier(c.move) },
                )
            }
            if (easier(c, c.move) == null) {
                assertTrue(
                    "${c.move.id} has no easier on ${c.gearName} but one fits",
                    tier(c.move) == 0 || sameFamily.none { tier(it) < tier(c.move) },
                )
            }
        }
    }

    @Test fun nudgeNeverNamesAMissingMoveAndCopyFollows() {
        val values = listOf(ExerciseKind.REPS to 30, ExerciseKind.REPS to 2, ExerciseKind.REPS to 0,
            ExerciseKind.HOLD to 120, ExerciseKind.HOLD to 5, ExerciseKind.HOLD to 0)
        var checked = 0
        for (c in cells) {
            for ((kind, value) in values) {
                if (c.move.kind != kind) continue
                checked++
                val display = computeTargetDisplay(
                    exercise = c.move,
                    test = BaselineTestEntity(
                        exerciseId = c.move.id,
                        kind = kind.name,
                        reps = if (kind == ExerciseKind.REPS) value else null,
                        seconds = if (kind == ExerciseKind.HOLD) value else null,
                        createdAt = 0L,
                    ),
                    library = catalog,
                    equipment = c.gear,
                    unit = WeightUnit.KG,
                )
                val nudge = baselineNudge(display)
                val high = if (kind == ExerciseKind.REPS) value >= 25 else value >= 90
                val low = value < (if (kind == ExerciseKind.REPS) 4 else 15)
                val h = harder(c, c.move)
                val e = easier(c, c.move)
                val label = "${c.move.id}/$kind$value/${c.gearName}"
                assertEquals(label, high && h == null, nudge == BaselineNudge.TopLevel)
                assertEquals(label, low && e == null, nudge == BaselineNudge.BottomLevel)
                assertEquals(label, high && h != null, nudge is BaselineNudge.TryHarder)
                assertEquals(label, low && e != null, nudge is BaselineNudge.TryEasier)
                if (nudge is BaselineNudge.TryHarder) assertEquals(label, h?.id, nudge.exercise.id)
                if (nudge is BaselineNudge.TryEasier) assertEquals(label, e?.id, nudge.exercise.id)
                // The level on the display drives the nudge: outgrown or too hard, never both.
                val level = when (display) {
                    is BaselineTargetDisplay.Reps -> display.level
                    is BaselineTargetDisplay.Hold -> display.level
                    else -> BaselineLevel.TOO_HARD
                }
                assertEquals(label, high, level == BaselineLevel.OUTGROWN)
            }
        }
        assertTrue(checked > 1000)
    }

    @Test fun ownerCases() {
        val noGear = setOf(Equipment.BODYWEIGHT)
        assertEquals("hollow_hold", BaselineTargets.harderVariation(ex("plank"), catalog, noGear)?.id)
        assertNull(BaselineTargets.harderVariation(ex("hollow_hold"), catalog, noGear))
        val logged = computeTargetDisplay(
            exercise = ex("hollow_hold"),
            test = BaselineTestEntity(exerciseId = "hollow_hold", kind = ExerciseKind.HOLD.name, seconds = 205, createdAt = 0L),
            library = catalog,
            equipment = noGear,
            unit = WeightUnit.KG,
        ) as BaselineTargetDisplay.Hold
        assertEquals(60, logged.seconds)
        assertEquals(BaselineLevel.OUTGROWN, logged.level)
        assertNull(logged.harderExercise)
        assertEquals(BaselineNudge.TopLevel, baselineNudge(logged))
        for (preset in listOf(EquipmentPreset.CALISTHENICS_PARK, EquipmentPreset.FULL_GYM)) {
            assertEquals("l_sit", BaselineTargets.harderVariation(ex("hollow_hold"), catalog, preset.equipment)?.id)
        }
        val start = Cell(ex("push_up"), noGear, "BODYWEIGHT", allowedFor(noGear))
        val path = walk(start) { harder(start, it) }
        assertTrue(path.size > 1)
        assertEquals(Tier.ADVANCED, ExerciseTiers.of(path.last()))
        assertNotNull(path.last())
        println("progression graph: ${moves.size} moves, ${cells.size} move x gear cells checked, premature dead ends 0, cycles 0")
    }
}
