package com.forgequest.app.data

import com.forgequest.app.data.seed.ExerciseCatalog
import com.forgequest.app.data.seed.StarterPlans
import com.forgequest.app.domain.Equipment
import com.forgequest.app.domain.ExerciseSwapEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Onboarding picks plans by position when the equipment changes, so the positions must stay true. */
class StarterPlanIndexTest {
    private val catalog = ExerciseCatalog.all.associateBy { it.id }
    private fun equipmentOf(index: Int) = StarterPlans.all[index].days.flatMap { it.items }
        .map { ExerciseSwapEngine.profileFor(catalog.getValue(it.exerciseId)).equipment }.toSet()

    @Test fun bodyweightPlanNeedsNoWeights() {
        assertTrue(equipmentOf(StarterPlans.BODYWEIGHT_INDEX).all { it == Equipment.BODYWEIGHT || it == Equipment.DIP_BARS_OR_BENCH })
    }

    @Test fun dumbbellPlanUsesDumbbells() {
        assertTrue(Equipment.DUMBBELLS in equipmentOf(StarterPlans.DUMBBELLS_INDEX))
        assertTrue(equipmentOf(StarterPlans.DUMBBELLS_INDEX).all { it == Equipment.DUMBBELLS || it == Equipment.BODYWEIGHT })
    }

    @Test fun noEquipmentSetupLeavesNothingThatNeedsABar() {
        val adapted = ExerciseSwapEngine.adaptPlanToEquipment(StarterPlans.all[StarterPlans.BODYWEIGHT_INDEX], catalog, setOf(Equipment.BODYWEIGHT), dropUnfit = true).first
        val needs = adapted.days.flatMap { it.items }.map { ExerciseSwapEngine.profileFor(catalog.getValue(it.exerciseId)).equipment }.toSet()
        assertEquals(setOf(Equipment.BODYWEIGHT), needs)
        assertTrue(adapted.days.any { it.items.isNotEmpty() })
    }

    @Test fun gymSplitIsLast() {
        assertEquals(StarterPlans.all.lastIndex, StarterPlans.GYM_SPLIT_INDEX)
    }
}
