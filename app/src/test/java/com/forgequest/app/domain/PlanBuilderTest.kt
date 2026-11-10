package com.forgequest.app.domain

import com.forgequest.app.data.plan.PlanDraft
import com.forgequest.app.data.seed.ExerciseCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Runs the offline plan builder against the real 100-exercise catalog. */
class PlanBuilderTest {
    private val library = ExerciseCatalog.all
    private val byId = library.associateBy { it.id }
    private val fullGym = EquipmentPreset.FULL_GYM.equipment

    private fun input(
        days: Int = 4,
        equipment: Set<Equipment> = fullGym,
        goal: TrainGoal = TrainGoal.MUSCLE,
        minutes: Int = 60,
        block: (PlanInput) -> PlanInput = { it },
    ) = block(PlanInput(goal = goal, days = PlanBuilder.spreadDays(days), minutes = minutes, equipment = equipment))

    private fun build(i: PlanInput) = PlanBuilder.build(i, library)

    private fun PlanDraft.exercises() = days.flatMap { d -> d.items.map { byId.getValue(it.exerciseId) } }

    @Test fun everyOutputIdExistsInTheCatalog() {
        for (preset in EquipmentPreset.entries) for (n in 2..6) {
            val ids = build(input(n, preset.equipment)).draft.days.flatMap { d -> d.items.map { it.exerciseId } }
            assertTrue(ids.isNotEmpty())
            assertTrue("$preset/$n has unknown ids", ids.all { it in byId })
        }
    }

    @Test fun trainingDayCountEqualsInputDays() {
        for (n in 1..7) {
            val plan = build(input(n))
            val trained = plan.draft.days.filter { !it.isRest && it.items.isNotEmpty() }.map { it.dayOfWeek }.toSet()
            assertEquals("days for n=$n", PlanBuilder.spreadDays(n), trained)
            assertEquals(trained, plan.days.keys)
        }
    }

    @Test fun noEmptyTrainingDayForAnyPresetAndDayCount() {
        for (preset in EquipmentPreset.entries) for (n in 2..6) {
            val plan = build(input(n, preset.equipment))
            assertEquals("$preset/$n", n, plan.draft.days.size)
            assertTrue("$preset/$n", plan.draft.days.all { it.items.isNotEmpty() })
        }
    }

    @Test fun noBlockedExerciseForAnySingleInjuryOrAllTogether() {
        val sets = Injury.entries.map { setOf(it) } + listOf(Injury.entries.toSet())
        for (injuries in sets) for (preset in listOf(EquipmentPreset.FULL_GYM, EquipmentPreset.CALISTHENICS_PARK, EquipmentPreset.BODYWEIGHT_ONLY)) {
            val plan = build(input(5, preset.equipment) { it.copy(injuries = injuries) })
            for (ex in plan.draft.exercises()) {
                assertFalse("${ex.id} blocked for $injuries", blocked(ex, ExerciseSwapEngine.profileFor(ex), injuries))
            }
        }
        // Spot checks that the rules block what the brief names and keep what it protects.
        val knee = build(input(5) { it.copy(injuries = setOf(Injury.KNEE)) }).draft.exercises().map { it.id }
        assertTrue(knee.none { it in setOf("pistol_squat", "burpee", "jumping_jacks") })
        val wrist = build(input(5, EquipmentPreset.CALISTHENICS_PARK.equipment) { it.copy(injuries = setOf(Injury.WRIST)) }).draft.exercises().map { it.id }
        assertTrue(wrist.none { "push_up" in it || "planche" in it || "handstand" in it })
    }

    @Test fun avoidIdsAreHonoured() {
        val avoid = setOf("push_up", "squat", "pull_up", "plank", "back_squat", "bench_press", "deadlift")
        for (preset in listOf(EquipmentPreset.FULL_GYM, EquipmentPreset.BODYWEIGHT_ONLY, EquipmentPreset.CALISTHENICS_PARK)) {
            val ids = build(input(4, preset.equipment) { it.copy(avoidIds = avoid) }).draft.exercises().map { it.id }
            assertTrue(ids.none { it in avoid })
            assertTrue(ids.isNotEmpty())
        }
    }

    @Test fun holdsOffGivesNoHoldItemsWhenAnythingElseFits() {
        for (preset in listOf(EquipmentPreset.FULL_GYM, EquipmentPreset.HOME_GYM, EquipmentPreset.DUMBBELLS_AND_BAR)) {
            val plan = build(input(5, preset.equipment) { it.copy(holdsOk = false) })
            assertTrue("$preset", plan.draft.exercises().none { it.kind == ExerciseKind.HOLD })
        }
        // Documented fallback: a core slot may still use a hold when it has no other option.
        val bodyweight = build(input(3, EquipmentPreset.BODYWEIGHT_ONLY.equipment) { it.copy(holdsOk = false) })
        val holds = bodyweight.draft.exercises().filter { it.kind == ExerciseKind.HOLD }
        assertTrue(holds.all { ExerciseSwapEngine.profileFor(it).pattern in setOf(MovementPattern.HIP_HINGE, MovementPattern.CORE_ANTI_EXTENSION, MovementPattern.CORE_FLEXION_ROTATION) })
    }

    @Test fun zeroPullUpsGivesNoHarderVerticalPull() {
        for (preset in listOf(EquipmentPreset.FULL_GYM, EquipmentPreset.CALISTHENICS_PARK, EquipmentPreset.PULL_UP_BAR_ONLY)) {
            val plan = build(input(5, preset.equipment) { it.copy(pullUpMax = 0, experience = Experience.SEASONED) })
            val pulls = plan.draft.exercises().filter { ExerciseSwapEngine.profileFor(it).pattern == MovementPattern.VERTICAL_PULL }
            assertTrue("$preset has no vertical pull", pulls.isNotEmpty())
            assertTrue("$preset: ${pulls.map { it.id }}", pulls.all { ExerciseTiers.of(it) == Tier.FOUNDATION })
        }
    }

    @Test fun estimatedMinutesStayWithinFiveOfTheBudget() {
        for (preset in EquipmentPreset.entries) for (minutes in listOf(20, 30, 45, 60, 90)) for (n in listOf(2, 4, 6)) {
            val plan = build(input(n, preset.equipment, TrainGoal.STRENGTH, minutes) { it.copy(coolDown = true, cardio = Cardio.FINISHER) })
            for (day in plan.draft.days) {
                val est = PlanBuilder.estimateMinutes(day)
                assertTrue("$preset ${minutes}min n=$n day ${day.dayOfWeek}: $est", est <= minutes + 5)
            }
        }
        val perDay = build(input(3, fullGym) { it.copy(minutesByDay = mapOf(1 to 25, 3 to 40)) })
        assertTrue(PlanBuilder.estimateMinutes(perDay.draft.days.first { it.dayOfWeek == 1 }) <= 30)
        assertTrue(PlanBuilder.estimateMinutes(perDay.draft.days.first { it.dayOfWeek == 3 }) <= 45)
    }

    @Test fun sameInputTwiceGivesEqualResult() {
        val i = input(5) { it.copy(focus = setOf(Muscle.BICEPS, Muscle.GLUTES), injuries = setOf(Injury.KNEE), cardio = Cardio.OWN_DAY) }
        assertEquals(build(i), build(i))
        assertEquals(build(i), PlanBuilder.build(i, library.reversed()))
    }

    @Test fun strengthAndEnduranceUseDifferentRepRanges() {
        fun ranges(goal: TrainGoal) = build(input(3, fullGym, goal)).draft.days
            .flatMap { it.items }.filter { it.kind == "WEIGHTED" }.map { it.repsMin to it.repsMax }.toSet()
        val strength = ranges(TrainGoal.STRENGTH)
        val endurance = ranges(TrainGoal.ENDURANCE)
        assertEquals(setOf(4 to 6), strength)
        assertEquals(setOf(15 to 20), endurance)
        assertNotEquals(strength, endurance)
        val rest = { goal: TrainGoal -> build(input(3, fullGym, goal)).draft.days.first().items.first { it.block == "Main" }.restSeconds }
        assertTrue(rest(TrainGoal.STRENGTH) > rest(TrainGoal.ENDURANCE))
    }

    @Test fun bodyweightRepMovesNeverGoUnderSixReps() {
        val plan = build(input(4, EquipmentPreset.BODYWEIGHT_ONLY.equipment, TrainGoal.STRENGTH))
        val reps = plan.draft.days.flatMap { it.items }.filter { it.kind == "REPS" }
        assertTrue(reps.isNotEmpty())
        assertTrue(reps.all { (it.repsMin ?: 6) >= 6 })
    }

    @Test fun focusMuscleGetsMoreWeeklySets() {
        fun weeklySets(plan: BuiltPlan, m: Muscle) = plan.draft.days.flatMap { it.items }
            .filter { m in byId.getValue(it.exerciseId).primary }.sumOf { it.sets }
        for (m in listOf(Muscle.BICEPS, Muscle.SHOULDERS, Muscle.GLUTES, Muscle.CHEST, Muscle.CALVES)) {
            val without = weeklySets(build(input(4)), m)
            val with = weeklySets(build(input(4) { it.copy(focus = setOf(m)) }), m)
            assertTrue("$m: $with vs $without", with > without)
        }
    }

    @Test fun stretchesOnlyAppearInTheCoolDownBlock() {
        for (preset in EquipmentPreset.entries) for (n in 2..6) {
            val plan = build(input(n, preset.equipment) { it.copy(coolDown = true) })
            val items = plan.draft.days.flatMap { it.items }
            assertTrue("$preset/$n has no cool-down", items.any { it.block == PlanBuilder.COOL_DOWN_BLOCK })
            for (item in items) {
                assertEquals("${item.exerciseId} in ${item.block}", item.exerciseId in ExerciseCatalog.STRETCH_IDS, item.block == PlanBuilder.COOL_DOWN_BLOCK)
            }
            for (day in plan.draft.days.filter { d -> d.items.any { it.block == PlanBuilder.COOL_DOWN_BLOCK } }) {
                assertEquals(2, day.items.count { it.block == PlanBuilder.COOL_DOWN_BLOCK })
            }
        }
        val off = build(input(4)).draft.days.flatMap { it.items }
        assertTrue(off.none { it.exerciseId in ExerciseCatalog.STRETCH_IDS })
    }

    @Test fun offsetZeroMatchesDefaultAndOffsetOneRotates() {
        val base = build(input())
        assertEquals(base, build(input { it.copy(offset = 0) }))
        assertNotEquals(base.draft.exercises().map { it.id }, build(input { it.copy(offset = 1) }).draft.exercises().map { it.id })
    }
}
