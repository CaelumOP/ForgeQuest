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
        assertEquals(90, all.size)
        val counts = all.groupingBy { ExerciseTiers.of(it) }.eachCount()
        assertEquals(52, counts[Tier.FOUNDATION])
        assertEquals(22, counts[Tier.INTERMEDIATE])
        assertEquals(16, counts[Tier.ADVANCED])
        assertNull(ExerciseTiers.of(ex("push_up").copy(id = "my_push_up", custom = true)))
    }

    @Test fun ladder() {
        fun next(id: String, eq: Set<Equipment> = gym) = ExerciseTiers.harderVersion(ex(id), all, eq)?.id
        assertEquals("push_up", next("wide_knee_push_up", bodyweight))
        assertEquals("squat", next("half_squat", bodyweight))
        assertEquals("lunge", next("squat", bodyweight))
        assertEquals("bulgarian_split_squat", next("lunge"))
        assertEquals("negative_pull_up", next("australian_chin_up"))
        assertEquals("pull_up", next("negative_pull_up"))
        assertEquals("diamond_push_up", next("incline_diamond_knee_push_up", bodyweight))
        assertNull(next("calf_raise", bodyweight)) // no harder version yet: the screen suggests a slower tempo
        assertNull(next("lunge", bodyweight)) // the split squat needs a bench or chair
        assertNull(next("bench_press")) // weighted moves add load instead
    }

    @Test fun readyNeedsTwoCleanSessionsAt15() {
        fun s(vararg reps: Int, effort: SetEffort = SetEffort.OK) = reps.map { Progression.PastSet(it, null, null, effort) }
        assertTrue(ExerciseTiers.readyForHarder(s(20, 18, 15), s(16, 15, 15)))
        assertFalse(ExerciseTiers.readyForHarder(s(20, 18, 14), s(16, 15, 15)))
        assertFalse(ExerciseTiers.readyForHarder(s(20, 18, 15), emptyList()))
        assertFalse(ExerciseTiers.readyForHarder(s(20, 18, 15, effort = SetEffort.HARD), s(16, 15, 15)))
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

    @Test fun planSwapOnlyWithOneSameMovementExercise() {
        assertEquals("knee_push_up", MuscleExercises.planSwapTarget(ex("push_up"), listOf(ex("knee_push_up"), ex("squat")))?.id)
        assertNull(MuscleExercises.planSwapTarget(ex("push_up"), listOf(ex("knee_push_up"), ex("wide_knee_push_up"))))
        assertNull(MuscleExercises.planSwapTarget(ex("push_up"), listOf(ex("push_up"))))
    }
}
