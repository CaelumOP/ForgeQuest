package com.forgequest.app.domain

import com.forgequest.app.data.seed.ExerciseCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BaselineTargetsTest {

    private val byId = ExerciseCatalog.all.associateBy { it.id }

    @Test
    fun repsTargetMatchesSpecCases() {
        assertNull(BaselineTargets.repsTarget(0))
        assertNull(BaselineTargets.repsTarget(-5))
        assertEquals(1, BaselineTargets.repsTarget(1))
        assertEquals(7, BaselineTargets.repsTarget(10))
        assertEquals(9, BaselineTargets.repsTarget(14))
    }

    @Test
    fun holdTargetMatchesSpecCases() {
        assertNull(BaselineTargets.holdTarget(0))
        assertNull(BaselineTargets.holdTarget(-3))
        assertEquals(5, BaselineTargets.holdTarget(6))
        assertEquals(20, BaselineTargets.holdTarget(30))
        assertEquals(40, BaselineTargets.holdTarget(60))
    }

    @Test
    fun weightedTargetMatchesFormula() {
        // 60 * (1 + 8/30) = 76; 76 / (1 + 11/30) * 0.9 = 50.04878... -> floor to 2.5 = 50.0
        assertEquals(
            WeightedResult.Target(50.0),
            BaselineTargets.weightedTarget(60.0, reps = 8, repsMin = 8, loadStep = 2.5),
        )
        // reps > 12 -> TooLight
        assertEquals(
            WeightedResult.TooLight,
            BaselineTargets.weightedTarget(20.0, reps = 13, repsMin = 8, loadStep = 2.5),
        )
        // 10 * (1 + 10/30) = 13.333...; 13.333... / (1 + 15/30) * 0.9 = 8.0 -> floor to 2.5 = 7.5
        assertEquals(
            WeightedResult.Target(7.5),
            BaselineTargets.weightedTarget(10.0, reps = 10, repsMin = 12, loadStep = 2.5),
        )
        // 4 * (1 + 5/30) = 4.666...; 4.666... / (1 + 13/30) * 0.9 = 2.9302... -> floor to 2.5 = 2.5
        assertEquals(
            WeightedResult.Target(2.5),
            BaselineTargets.weightedTarget(4.0, reps = 5, repsMin = 10, loadStep = 2.5),
        )
        // Below one step -> NoTarget (3 * (35/30) / (43/30) * 0.9 = 2.1976... < 2.5)
        assertEquals(
            WeightedResult.NoTarget,
            BaselineTargets.weightedTarget(3.0, reps = 5, repsMin = 10, loadStep = 2.5),
        )
        assertEquals(
            WeightedResult.NoTarget,
            BaselineTargets.weightedTarget(0.0, reps = 8, repsMin = 8, loadStep = 2.5),
        )
        assertEquals(
            WeightedResult.NoTarget,
            BaselineTargets.weightedTarget(40.0, reps = 0, repsMin = 8, loadStep = 2.5),
        )
    }

    @Test
    fun easierVariationFollowsTierLineAndEquipment() {
        val pushUp = byId.getValue("push_up")
        val kneePushUp = byId.getValue("knee_push_up")
        val kneeScapular = byId.getValue("knee_scapular_push_up")
        val pullUp = byId.getValue("pull_up")
        val pistolSquat = byId.getValue("pistol_squat")

        assertEquals("knee_push_up", BaselineTargets.easierVariation(pushUp, ExerciseCatalog.all, setOf(Equipment.BODYWEIGHT))?.id)
        assertEquals("knee_scapular_push_up", BaselineTargets.easierVariation(kneePushUp, ExerciseCatalog.all, setOf(Equipment.BODYWEIGHT))?.id)
        assertNull(BaselineTargets.easierVariation(kneeScapular, ExerciseCatalog.all, setOf(Equipment.BODYWEIGHT)))

        assertEquals("negative_pull_up", BaselineTargets.easierVariation(pullUp, ExerciseCatalog.all, setOf(Equipment.PULL_UP_BAR))?.id)
        assertNull(BaselineTargets.easierVariation(pullUp, ExerciseCatalog.all, setOf(Equipment.BODYWEIGHT)))

        assertEquals("lunge", BaselineTargets.easierVariation(pistolSquat, ExerciseCatalog.all, setOf(Equipment.BODYWEIGHT))?.id)
        assertEquals("bulgarian_split_squat", BaselineTargets.easierVariation(pistolSquat, ExerciseCatalog.all, Equipment.allActive)?.id)
    }
}
