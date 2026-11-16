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
        val allowed = setOf(Equipment.BODYWEIGHT, Equipment.DIP_STATION, Equipment.LOW_BAR, Equipment.FLAT_BENCH)
        assertTrue(equipmentOf(StarterPlans.BODYWEIGHT_INDEX).all { it in allowed })
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

    @Test fun displayTitleTranslatesBuiltInNamesBothWays() {
        val prev = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("ar"))
            assertEquals("خطة لياقة عامة", StarterPlans.displayTitle("General fitness plan"))
            assertEquals("الجسم كاملًا", StarterPlans.displayTitle("Full body"))
            assertEquals("تمرين سريع", StarterPlans.displayTitle("Quick workout"))
            assertEquals("Custom plan", StarterPlans.displayTitle("Custom plan"))

            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("en"))
            assertEquals("General fitness plan", StarterPlans.displayTitle("خطة لياقة عامة"))
            assertEquals("Full body", StarterPlans.displayTitle("الجسم كاملًا"))
            assertEquals("Quick workout", StarterPlans.displayTitle("تمرين سريع"))
        } finally {
            java.util.Locale.setDefault(prev)
        }
    }

    @Test fun exerciseNamesArDisplayNameTranslatesBuiltInAndFriendPrNames() {
        val prev = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("ar"))
            assertEquals("سكوات بالبار خلفًا", com.forgequest.app.data.seed.ExerciseNamesAr.displayName("Barbell Back Squat"))
            assertEquals("الضغط فوق الرأس", com.forgequest.app.data.seed.ExerciseNamesAr.displayName("Overhead Press"))
            assertEquals("الرفعة الميتة الرومانية", com.forgequest.app.data.seed.ExerciseNamesAr.displayName("Romanian Deadlift"))
            assertEquals("ضغط البنش", com.forgequest.app.data.seed.ExerciseNamesAr.displayName("Bench Press"))

            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("en"))
            assertEquals("Back squat", com.forgequest.app.data.seed.ExerciseNamesAr.displayName("سكوات بالبار خلفًا"))
            assertEquals("Overhead press", com.forgequest.app.data.seed.ExerciseNamesAr.displayName("الضغط فوق الرأس"))
        } finally {
            java.util.Locale.setDefault(prev)
        }
    }
}
