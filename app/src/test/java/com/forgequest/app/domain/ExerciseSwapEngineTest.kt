package com.forgequest.app.domain

import com.forgequest.app.data.db.ExerciseEntity
import com.forgequest.app.data.db.PlanItemEntity
import com.forgequest.app.data.plan.PlanDraft
import com.forgequest.app.data.plan.PlanDraftDay
import com.forgequest.app.data.plan.PlanDraftItem
import com.forgequest.app.data.seed.ExerciseCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        assertEquals(setOf(Equipment.BARBELL, Equipment.BODYWEIGHT), inferEquipmentFromText("I don't have dumbbells, but I have a barbell"))
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

    @Test
    fun everyCatalogExerciseHasAnArabicNameAndASwapProfile() {
        val ids = ExerciseCatalog.all.map { it.id }
        assertEquals("duplicate ids", ids.size, ids.toSet().size)
        ids.forEach {
            assertTrue("no Arabic name: $it", it in com.forgequest.app.data.seed.ExerciseNamesAr.byId)
            assertTrue("no swap profile: $it", it in ExerciseSwapEngine.catalogProfiles)
            assertTrue("custom_ prefix: $it", !it.startsWith("custom_"))
        }
        ExerciseSwapEngine.catalogProfiles.forEach { (id, profile) ->
            profile.preferredSwaps.forEach { assertTrue("$id swaps to unknown $it", it in ids) }
        }
    }

    @Test
    fun stretchesAreOnlySwappedForStretches() {
        val stretch = ExerciseCatalog.all.first { it.id == "hamstring_stretch" }
        val lift = ExerciseCatalog.all.first { it.id == "romanian_deadlift" }
        assertTrue(ExerciseSwapEngine.rankSubstitutes(stretch, ExerciseCatalog.all).all { it.exercise.id in ExerciseCatalog.STRETCH_IDS })
        assertTrue(ExerciseSwapEngine.rankSubstitutes(lift, ExerciseCatalog.all).none { it.exercise.id in ExerciseCatalog.STRETCH_IDS })
        assertTrue(ExerciseCatalog.STRETCH_IDS.all { ExerciseSwapEngine.catalogProfiles.getValue(it).pattern == MovementPattern.MOBILITY })
        assertEquals(19, ExerciseCatalog.STRETCH_IDS.size)
    }

    @Test
    fun parseEquipmentSetExpandsLegacyTokensAndPresets() {
        val parsed = parseEquipmentSet("BARBELL_RACK,DIP_BARS_OR_BENCH")
        assertTrue(parsed.none { it.legacy })
        assertTrue(Equipment.BARBELL in parsed)
        assertTrue(Equipment.SQUAT_RACK in parsed)
        assertTrue(Equipment.FLAT_BENCH in parsed)
        assertTrue(Equipment.ADJ_BENCH in parsed)
        assertTrue(Equipment.DIP_STATION in parsed)
        assertTrue(Equipment.LOW_BAR in parsed)
        assertTrue(Equipment.ROMAN_CHAIR in parsed)
        assertTrue(Equipment.BODYWEIGHT in parsed)

        val machines = parseEquipmentSet("CABLES_MACHINES")
        assertTrue(machines.none { it.legacy })
        assertTrue(Equipment.CABLE_MACHINE in machines)
        assertTrue(Equipment.LEG_PRESS in machines)
        assertTrue(Equipment.LAT_PULLDOWN in machines)

        assertEquals(Equipment.allActive, parseEquipmentSet("all"))
        assertEquals(TrainingPlace.HOME.equipment, parseEquipmentSet("home_gym"))
    }

    @Test
    fun adjBenchSatisfiesFlatBenchAndCompoundRequiresNeedAllItems() {
        val map = ExerciseCatalog.all.associateBy { it.id }
        val backSquat = ExerciseSwapEngine.profileFor(map.getValue("back_squat"))
        assertFalse(backSquat.fits(setOf(Equipment.BARBELL, Equipment.BODYWEIGHT)))
        assertTrue(backSquat.fits(setOf(Equipment.BARBELL, Equipment.SQUAT_RACK, Equipment.BODYWEIGHT)))

        val benchPress = ExerciseSwapEngine.profileFor(map.getValue("bench_press"))
        assertFalse(benchPress.fits(setOf(Equipment.BARBELL, Equipment.BODYWEIGHT)))
        assertTrue(benchPress.fits(setOf(Equipment.BARBELL, Equipment.FLAT_BENCH, Equipment.BODYWEIGHT)))
        assertTrue(benchPress.fits(setOf(Equipment.BARBELL, Equipment.ADJ_BENCH, Equipment.BODYWEIGHT)))
    }

    @Test
    fun everyVisibleEquipmentItemHasAtLeastOneExerciseAndNoLegacyInCatalog() {
        val profiles = ExerciseCatalog.all.map { ExerciseSwapEngine.profileFor(it) }
        assertTrue(profiles.none { it.equipment.legacy })
        assertTrue(Equipment.selectableEntries.none { it.legacy })
        Equipment.selectableEntries.forEach { eq ->
            assertTrue(
                "no catalog exercise uses ${eq.name}",
                profiles.any { eq == it.equipment || eq in it.requires },
            )
        }
    }
}
