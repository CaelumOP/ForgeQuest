package com.forgequest.app.domain

import com.forgequest.app.data.db.ExerciseEntity
import com.forgequest.app.data.db.PlanItemEntity
import com.forgequest.app.data.plan.PlanDraft
import com.forgequest.app.data.plan.PlanDraftDay
import com.forgequest.app.data.plan.PlanDraftItem
import com.forgequest.app.data.seed.ExerciseCatalog
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

    @Test
    fun rankSubstitutesPutsAnOwnedSquatFirst() {
        val squat = ExerciseCatalog.all.first { it.id == "back_squat" }
        val top = ExerciseSwapEngine.rankSubstitutes(squat, ExerciseCatalog.all, EquipmentPreset.DUMBBELLS_AND_BAR.equipment).first()
        assertTrue(top.exercise.id, top.matchesUserEquipment)
        assertTrue(top.profile.pattern.name, top.profile.pattern == MovementPattern.SQUAT_BILATERAL || top.profile.pattern == MovementPattern.SQUAT_UNILATERAL)
    }

    @Test
    fun adaptPlanSwapsABenchPressForABodyweightMove() {
        val bodyweight = EquipmentPreset.BODYWEIGHT_ONLY.equipment
        val draft = PlanDraft(
            name = "Test",
            days = listOf(PlanDraftDay(dayOfWeek = 1, title = "Push", items = listOf(PlanDraftItem(exerciseId = "bench_press", repsMin = 5, repsMax = 8, weightKg = 80f)))),
        )
        val (adapted, swaps) = ExerciseSwapEngine.adaptPlanToEquipment(draft, ExerciseCatalog.all.associateBy { it.id }, bodyweight)
        assertEquals(1, swaps)
        val newId = adapted.days.single().items.single().exerciseId
        assertTrue(newId != "bench_press")
        val replacement = ExerciseCatalog.all.first { it.id == newId }
        assertEquals(Equipment.BODYWEIGHT, ExerciseSwapEngine.profileFor(replacement).equipment)
    }
}
