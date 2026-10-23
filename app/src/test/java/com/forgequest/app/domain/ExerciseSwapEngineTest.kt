package com.forgequest.app.domain

import com.forgequest.app.data.db.ExerciseEntity
import com.forgequest.app.data.db.PlanItemEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseSwapEngineTest {
    private fun ex(id: String) = ExerciseEntity(id, id, ExerciseKind.WEIGHTED, listOf(Muscle.QUADS), emptyList())

    @Test
    fun weightedCalisthenicsNeedBarsNotABarbell() {
        val calisthenics = EquipmentPreset.CALISTHENICS_PARK.equipment
        listOf("weighted_pull_up", "weighted_chin_up", "weighted_dip").forEach {
            assertTrue(it, ExerciseSwapEngine.profileFor(ex(it)).equipment in calisthenics)
        }
    }

    @Test
    fun swappingInThePlanEditorDropsTheOldLoad() {
        val squat = PlanItemEntity(dayId = 1, position = 0, exerciseId = "back_squat", block = "Main", circuit = false, sets = 3, repsMin = 5, repsMax = 8, weightKg = 100f)
        val goblet = ExerciseSwapEngine.translatePlanItem(squat, ex("goblet_squat"))
        assertEquals("goblet_squat", goblet.exerciseId)
        assertEquals(0f, goblet.weightKg)
        assertEquals(5, goblet.repsMin)
    }

    @Test
    fun equipmentFromTextReadsArabicAndNegation() {
        // «بار عقلة» is a pull-up bar, not a barbell.
        assertEquals(setOf(Equipment.PULL_UP_BAR, Equipment.BODYWEIGHT), inferEquipmentFromText("عندي بار عقلة في البيت"))
        assertEquals(setOf(Equipment.DUMBBELLS, Equipment.BODYWEIGHT), inferEquipmentFromText("عندي دمبل، ما عندي كيتل بيل"))
        assertEquals(setOf(Equipment.DUMBBELLS, Equipment.BODYWEIGHT), inferEquipmentFromText("ما عندي كيتل بيل ولكن عندي دمبل"))
        assertEquals(setOf(Equipment.BARBELL_RACK, Equipment.BODYWEIGHT), inferEquipmentFromText("I don't have dumbbells, but I have a barbell"))
        assertEquals(setOf(Equipment.BODYWEIGHT), inferEquipmentFromText("بدون معدات"))
        assertEquals(null, inferEquipmentFromText("ما عندي أجهزة"))
    }
}
