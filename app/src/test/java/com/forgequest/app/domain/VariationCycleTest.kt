package com.forgequest.app.domain

import com.forgequest.app.data.seed.ExerciseCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Variation cycling: what a stalled lift is swapped for, and what it never is. */
class VariationCycleTest {
    private val all = ExerciseCatalog.all
    private val gym = Equipment.entries.toSet()
    private fun ex(id: String) = all.first { it.id == id }
    private fun stalled(id: String) = StalledLift(id, ex(id).name, sessionsCount = 4, spanDays = 21, bestScore = 80f)
    private fun next(id: String, equipment: Set<Equipment> = gym, plan: Set<String> = setOf(id), cycled: Set<String> = emptySet()) =
        VariationCycle.next(stalled(id), all, equipment, plan, cycled)?.to

    @Test fun aStalledBenchGetsAnotherHorizontalPressOfTheSameKind() {
        val to = next("bench_press")
        assertNotNull(to)
        assertEquals(MovementPattern.HORIZONTAL_PUSH, ExerciseSwapEngine.profileFor(to!!).pattern)
        assertEquals(ExerciseKind.WEIGHTED, to.kind)
        assertNotEquals("bench_press", to.id)
    }

    @Test fun theSuggestionChangesTheAngleOrTheEquipment() {
        val from = ExerciseSwapEngine.profileFor(ex("bench_press"))
        val to = ExerciseSwapEngine.profileFor(next("bench_press")!!)
        assertTrue(to.angle != from.angle || to.equipment != from.equipment)
    }

    @Test fun onlyTheSixCoreMovementsAreCycled() {
        assertNull(next("bicep_curl"))
        assertNull(next("lateral_raise"))
        assertNull(next("plank"))
    }

    @Test fun squatsMayGoOneLegged() {
        assertEquals(CorePattern.SQUAT, CorePattern.of(MovementPattern.SQUAT_UNILATERAL))
        assertEquals(CorePattern.SQUAT, CorePattern.of(MovementPattern.SQUAT_BILATERAL))
        assertNull(CorePattern.of(MovementPattern.ELBOW_FLEXION))
    }

    @Test fun itRespectsTheEquipmentThePersonOwns() {
        val home = setOf(Equipment.BODYWEIGHT, Equipment.DUMBBELLS)
        val to = next("db_bench_press", equipment = home)
        assertNotNull(to)
        assertTrue(ExerciseSwapEngine.profileFor(to!!).equipment in home)
    }

    @Test fun neverSomethingAlreadyInThePlanOrCycledLately() {
        val first = next("bench_press")!!
        val second = next("bench_press", plan = setOf("bench_press", first.id))!!
        assertNotEquals(first.id, second.id)
        val third = next("bench_press", cycled = setOf(first.id))!!
        assertNotEquals(first.id, third.id)
    }

    @Test fun aPressIsNeverSwappedForAFlyNorAHingeForAKickback() {
        val isolation = setOf("cable_fly", "pec_deck", "db_fly", "cable_crossover_high_to_low", "cable_crossover_low_to_high", "cable_glute_kickback")
        // Walk down the whole list of suggestions for a press and a hinge: none of them is a single-joint move.
        for (id in listOf("bench_press", "db_bench_press", "romanian_deadlift")) {
            val plan = mutableSetOf(id)
            while (true) {
                val to = next(id, plan = plan) ?: break
                assertFalse("$id -> ${to.id}", to.id in isolation)
                plan += to.id
            }
        }
        // With the dumbbell press already in the plan, a stalled bench still gets a press.
        val to = next("bench_press", plan = setOf("bench_press", "db_bench_press"))!!
        assertFalse(to.id in isolation)
    }

    @Test fun theTierStaysWithinOneStep() {
        // A Foundation pull (band-assisted) is never sent straight to an Advanced one.
        val to = next("band_assisted_pull_up")!!
        assertNotEquals(Tier.ADVANCED, ExerciseTiers.of(to))
    }

    @Test fun theAnswerIsStable() {
        assertEquals(next("back_squat")?.id, next("back_squat")?.id)
    }

    @Test fun anUnknownExerciseHasNoSuggestion() {
        assertNull(VariationCycle.next(StalledLift("gone", "Gone", 4, 21, 1f), all, gym, emptySet(), emptySet()))
    }

    @Test fun historyKeepsACooldownOfEightWeeks() {
        val today = 20_000L
        val h = VariationCycle.record("", "bench_press", today)
        assertEquals("bench_press:20000", h)
        assertEquals(setOf("bench_press"), VariationCycle.recentlyCycled(h, today + 55))
        assertTrue(VariationCycle.recentlyCycled(h, today + 56).isEmpty())
        // Recording again replaces the old entry and drops the ones past the cooldown.
        val later = VariationCycle.record(VariationCycle.record(h, "back_squat", today + 10), "bench_press", today + 60)
        assertEquals("back_squat:20010;bench_press:20060", later)
        assertFalse(VariationCycle.recentlyCycled("garbage;;x:y", today).contains("x"))
    }
}
