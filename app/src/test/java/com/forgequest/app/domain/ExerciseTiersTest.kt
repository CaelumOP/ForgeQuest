package com.forgequest.app.domain

import com.forgequest.app.data.seed.ExerciseCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tiers, the muscle list and the harder-version ladder (web exercise-tiers.test.ts has the same cases). */
class ExerciseTiersTest {
    private val all = ExerciseCatalog.all
    private fun ex(id: String) = all.first { it.id == id }
    private val bodyweight = EquipmentPreset.BODYWEIGHT_ONLY.equipment
    private val gym = Equipment.entries.toSet()

    @Test fun tierCounts() {
        assertEquals(866, all.size)
        val counts = all.groupingBy { ExerciseTiers.of(it) }.eachCount()
        assertEquals(533, counts[Tier.FOUNDATION])
        assertEquals(266, counts[Tier.INTERMEDIATE])
        assertEquals(67, counts[Tier.ADVANCED])
        assertNull(ExerciseTiers.of(ex("push_up").copy(id = "my_push_up", custom = true)))
    }

    @Test fun ladder() {
        fun next(id: String, eq: Set<Equipment> = gym) = ExerciseTiers.harderVersion(ex(id), all, eq)?.id
        assertEquals("push_up", next("wide_knee_push_up", bodyweight))
        assertEquals("squat", next("half_squat", bodyweight))
        assertEquals("jump_squat", next("squat", bodyweight))
        assertEquals("reverse_lunge", next("lunge"))
        assertEquals("australian_pull_up", next("australian_chin_up"))
        assertEquals("pull_up", next("negative_pull_up"))
        assertEquals("body_up", next("incline_diamond_knee_push_up", bodyweight))
        assertNull(next("calf_raise", bodyweight)) // no harder version yet: the screen suggests a slower tempo
        assertEquals("reverse_lunge", next("lunge", bodyweight)) // the split squat needs a bench or chair
        assertNull(next("bench_press")) // weighted moves add load instead
    }

    @Test fun namedRungsStepUpWithinOneTier() {
        fun next(id: String, eq: Set<Equipment> = gym) = ExerciseTiers.harderVersion(ex(id), all, eq)?.id
        assertEquals("diamond_push_up", next("push_up"))
        assertEquals("diamond_push_up", next("push_up", bodyweight))
        assertEquals("push_up", next("knee_push_up", bodyweight))
        assertEquals("archer_pull_up", next("pull_up"))
        assertEquals("archer_pull_up", next("chin_up"))
        assertEquals("pull_up", next("australian_pull_up"))
        assertEquals("jump_squat", next("squat"))
    }

    @Test fun holdLadderHasHarderAndEasierRungs() {
        fun nextHold(id: String, eq: Set<Equipment> = gym) = ExerciseTiers.harderHoldVersion(ex(id), all, eq)?.id
        assertEquals("hollow_hold", nextHold("plank", bodyweight))
        assertEquals("l_sit", nextHold("hollow_hold"))
        assertEquals("copenhagen_plank_short", nextHold("side_plank"))
        assertNull(nextHold("push_up")) // reps moves use harderVersion
        assertEquals("incline_hollow_plank", BaselineTargets.easierVariation(ex("plank"), all, bodyweight)?.id)
        assertEquals("plank", BaselineTargets.easierVariation(ex("hollow_hold"), all, bodyweight)?.id)
        assertEquals("hollow_hold", BaselineTargets.easierVariation(ex("l_sit"), all, bodyweight)?.id)
    }

    @Test fun readyNeedsTwoCleanSessionsAt15() {
        fun s(vararg reps: Int, effort: SetEffort = SetEffort.OK) = reps.map { Progression.PastSet(it, null, null, effort) }
        assertTrue(ExerciseTiers.readyForHarder(s(20, 18, 15), s(16, 15, 15)))
        assertFalse(ExerciseTiers.readyForHarder(s(20, 18, 14), s(16, 15, 15)))
        assertFalse(ExerciseTiers.readyForHarder(s(20, 18, 15), emptyList()))
        assertTrue(ExerciseTiers.readyForHarder(s(20, 18, 15, effort = SetEffort.HARD), s(16, 15, 15)))
        assertFalse(ExerciseTiers.readyForHarder(s(20, 18, 15, effort = SetEffort.FAILURE), s(16, 15, 15)))
        assertFalse(ExerciseTiers.readyForHarder(s(20, 18, 15, effort = SetEffort.PAIN), s(16, 15, 15)))
    }

    @Test fun muscleList() {
        val chest = MuscleExercises.list(Muscle.CHEST, all, setOf("knee_push_up"), bodyweight)
        assertEquals(listOf("knee_push_up"), chest.inPlan.map { it.exercise.id })
        assertTrue(chest.others.none { it.exercise.id == "bench_press" }) // barbell: filtered out
        assertTrue(chest.hidden > 0)
        // Main muscle before "also works", Foundation before Advanced.
        val firstSecondary = chest.others.indexOfFirst { !it.main }
        assertTrue(firstSecondary == -1 || chest.others.drop(firstSecondary).none { it.main })
        assertEquals(Tier.FOUNDATION, chest.others.first().tier)
        val withAll = MuscleExercises.list(Muscle.CHEST, all, emptySet(), bodyweight, allEquipment = true)
        assertTrue(withAll.others.any { it.exercise.id == "bench_press" })
        // Obliques borrow the abs list until new exercises arrive.
        val obliques = MuscleExercises.list(Muscle.OBLIQUES, all, emptySet(), gym)
        assertTrue(obliques.size >= 5)
        assertTrue(obliques.others.any { it.exercise.id == "crunch" })
    }

    @Test fun smallMusclesShowWithTheirBiggerNeighbour() {
        assertEquals(listOf(Muscle.CALVES), MuscleExercises.COMPANIONS[Muscle.TIBIALIS])
        assertEquals(listOf(Muscle.BICEPS), MuscleExercises.COMPANIONS[Muscle.FOREARMS])
        assertEquals(listOf(Muscle.UPPER_BACK), MuscleExercises.COMPANIONS[Muscle.TRAPS])
        // The shin list now carries calf work too, and still leads with the muscle that was tapped.
        val shins = MuscleExercises.list(Muscle.TIBIALIS, all, emptySet(), gym)
        assertTrue(shins.others.any { it.exercise.id == "calf_raise" })
        assertTrue(shins.others.any { it.exercise.id == "tibialis_raise" })
        // A muscle with no companion is unchanged.
        assertNull(MuscleExercises.COMPANIONS[Muscle.CHEST])
    }

    @Test fun replaceOrderPutsTheSameMovementFirst() {
        val order = MuscleExercises.replaceOrder(ex("push_up"), listOf(ex("squat"), ex("knee_push_up"), ex("push_up")))
        assertEquals(listOf("knee_push_up", "squat"), order.map { it.first.id })
        assertEquals(listOf(true, false), order.map { it.second })
    }
}
