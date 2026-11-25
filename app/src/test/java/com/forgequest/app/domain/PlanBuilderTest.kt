package com.forgequest.app.domain

import com.forgequest.app.data.db.ExerciseEntity
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
        assertTrue((4 to 6) in strength)
        assertTrue(strength.all { it == (4 to 6) || it == (8 to 12) })
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

    @Test fun isolationsGetEightToFifteenRepsOnMuscle() {
        val plan = build(input(4, fullGym, TrainGoal.MUSCLE, minutes = 90))
        val main = plan.draft.days.flatMap { it.items }.filter { it.block == "Main" && it.kind != "HOLD" }
        val (compounds, isolations) = main.partition {
            PlanBuilder.isCompound(ExerciseSwapEngine.profileFor(byId.getValue(it.exerciseId)).pattern)
        }
        assertTrue(compounds.isNotEmpty())
        assertTrue(isolations.isNotEmpty())
        assertTrue(isolations.all { (it.repsMin ?: 0) in 8..15 && (it.repsMax ?: 0) in 8..15 && it.repsMin == 10 && it.repsMax == 15 })
        assertTrue(compounds.filter { it.kind == "WEIGHTED" }.all { it.repsMin == 8 && it.repsMax == 12 })
    }

    @Test fun isolationsGetEightToTwelveOnStrength() {
        val plan = build(input(4, fullGym, TrainGoal.STRENGTH, minutes = 90))
        val weighted = plan.draft.days.flatMap { it.items }.filter { it.block == "Main" && it.kind == "WEIGHTED" }
        val (compounds, isolations) = weighted.partition {
            PlanBuilder.isCompound(ExerciseSwapEngine.profileFor(byId.getValue(it.exerciseId)).pattern)
        }
        assertTrue(compounds.isNotEmpty())
        assertTrue(isolations.isNotEmpty())
        assertTrue(isolations.all { it.repsMin == 8 && it.repsMax == 12 })
        assertTrue(compounds.all { it.repsMin == 4 && it.repsMax == 6 })
    }

    @Test fun compoundsRestLongerThanIsolations() {
        for (goal in TrainGoal.entries) {
            val main = build(input(4, fullGym, goal, minutes = 90)).draft.days.flatMap { it.items }.filter { it.block == "Main" }
            val (compounds, isolations) = main.partition {
                PlanBuilder.isCompound(ExerciseSwapEngine.profileFor(byId.getValue(it.exerciseId)).pattern)
            }
            assertTrue("$goal compounds empty", compounds.isNotEmpty())
            assertTrue("$goal isolations empty", isolations.isNotEmpty())
            assertTrue("$goal rest", compounds.minOf { it.restSeconds } > isolations.maxOf { it.restSeconds })
        }
        val longStrength = build(input(4, fullGym, TrainGoal.STRENGTH, minutes = 90) {
            it.copy(rest = RestPref.LONG, cardio = Cardio.FINISHER)
        }).draft.days.flatMap { it.items }
        val longCompounds = longStrength.filter {
            it.block == "Main" && PlanBuilder.isCompound(ExerciseSwapEngine.profileFor(byId.getValue(it.exerciseId)).pattern)
        }
        val longIsolations = longStrength.filter {
            it.block == "Main" && !PlanBuilder.isCompound(ExerciseSwapEngine.profileFor(byId.getValue(it.exerciseId)).pattern)
        }
        assertTrue(longCompounds.all { it.restSeconds == 240 })
        assertTrue(longIsolations.all { it.restSeconds == 135 })
        val finishers = longStrength.filter { it.block == PlanBuilder.FINISHER_BLOCK }
        assertTrue(finishers.isNotEmpty())
        assertTrue(finishers.all { it.restSeconds == 30 })
    }

    @Test fun coreAndIsolationsComeAfterCompoundsFinisherLast() {
        val plan = build(input(4, fullGym, TrainGoal.MUSCLE, minutes = 90) {
            it.copy(cardio = Cardio.FINISHER, warmUp = true, coolDown = true)
        })
        fun rankOf(block: String, id: String): Int {
            if (block == PlanBuilder.FINISHER_BLOCK) return 3
            val p = ExerciseSwapEngine.profileFor(byId.getValue(id)).pattern
            return when {
                PlanBuilder.isCore(p) -> 2
                PlanBuilder.isCompound(p) -> 0
                else -> 1
            }
        }
        val seenRanks = mutableSetOf<Int>()
        for (day in plan.draft.days) {
            val working = day.items.filter { !WorkoutScore.isExtra(it.block) }
            val ranks = working.map { rankOf(it.block, it.exerciseId) }
            seenRanks += ranks
            assertEquals("Day ${day.dayOfWeek} out of order: $ranks", ranks.sorted(), ranks)
        }
        assertEquals(setOf(0, 1, 2, 3), seenRanks)
    }

    @Test fun existingFocusVolumeStillHolds() {
        fun weeklySets(plan: BuiltPlan, m: Muscle) = plan.draft.days.flatMap { it.items }
            .filter { !WorkoutScore.isExtra(it.block) && m in byId.getValue(it.exerciseId).primary }
            .sumOf { it.sets }
        for (m in listOf(Muscle.BICEPS, Muscle.SHOULDERS, Muscle.GLUTES, Muscle.CHEST, Muscle.CALVES)) {
            val without = weeklySets(build(input(4, minutes = 45)), m)
            val with = weeklySets(build(input(4, minutes = 45) { it.copy(focus = setOf(m)) }), m)
            assertTrue("$m at 45m: $with vs $without", with > without)
        }
    }

    @Test fun gate3FillBreaksTiesByEarlierDayThenPlanOrder() {
        val calf = byId.getValue("calf_raise")
        val plan = PlanBuilder.build(
            PlanInput(
                goal = TrainGoal.MUSCLE,
                days = setOf(1, 4),
                minutes = 60,
                equipment = setOf(Equipment.BODYWEIGHT),
                experience = Experience.SOME,
                warmUp = false,
            ),
            listOf(calf),
        )
        assertEquals(listOf(4, 2), plan.draft.days.map { it.items.single().sets })
    }

    @Test fun gate3ZeroGainPullAllowanceRequiresPushBlockedOnlyByPushPull() {
        fun ex(id: String, pattern: MovementPattern, primary: List<Muscle>, secondary: List<Muscle> = emptyList()) =
            ExerciseEntity(id = id, name = id, kind = ExerciseKind.REPS, primary = primary, secondary = secondary, pattern = pattern.name)
        val tiny = listOf(
            ex("t_push", MovementPattern.HORIZONTAL_PUSH, listOf(Muscle.CHEST, Muscle.GLUTES), listOf(Muscle.SHOULDERS)),
            ex("t_pull", MovementPattern.HORIZONTAL_PULL, listOf(Muscle.TRAPS)),
            ex("t_squat", MovementPattern.SQUAT_BILATERAL, listOf(Muscle.QUADS, Muscle.GLUTES)),
            ex("t_hinge", MovementPattern.HIP_HINGE, listOf(Muscle.HAMSTRINGS)),
        )
        val plan = PlanBuilder.build(
            PlanInput(
                goal = TrainGoal.MUSCLE,
                days = setOf(1, 3, 5),
                minutes = 90,
                equipment = setOf(Equipment.BODYWEIGHT),
                experience = Experience.SOME,
                warmUp = false,
            ),
            tiny,
        )
        val pullSets = plan.draft.days.flatMap { it.items }.filter { it.exerciseId == "t_pull" }.sumOf { it.sets }
        assertEquals(6, pullSets)
    }

    @Test fun gate3CoreFocusPickIsRemovedLastInRepair() {
        fun ex(id: String, pattern: MovementPattern, primary: List<Muscle>) =
            ExerciseEntity(id = id, name = id, kind = ExerciseKind.REPS, primary = primary, secondary = emptyList(), pattern = pattern.name)
        val tiny = listOf(
            ex("t_sq", MovementPattern.SQUAT_BILATERAL, listOf(Muscle.QUADS, Muscle.ABS)),
            ex("t_hp", MovementPattern.HORIZONTAL_PUSH, listOf(Muscle.CHEST, Muscle.ABS)),
            ex("t_hpl", MovementPattern.HORIZONTAL_PULL, listOf(Muscle.UPPER_BACK, Muscle.ABS)),
            ex("t_hg", MovementPattern.HIP_HINGE, listOf(Muscle.HAMSTRINGS, Muscle.ABS)),
            ex("t_vp", MovementPattern.VERTICAL_PUSH, listOf(Muscle.SHOULDERS, Muscle.ABS)),
            ex("t_core_main", MovementPattern.CORE_ANTI_EXTENSION, listOf(Muscle.ABS)),
            ex("t_core_focus", MovementPattern.CORE_FLEXION_ROTATION, listOf(Muscle.ABS)),
        )
        val plan = PlanBuilder.build(
            PlanInput(
                goal = TrainGoal.MUSCLE,
                days = setOf(1),
                minutes = 90,
                equipment = setOf(Equipment.BODYWEIGHT),
                experience = Experience.SOME,
                focus = setOf(Muscle.ABS),
                warmUp = false,
            ),
            tiny,
        )
        val ids = plan.draft.days.single().items.map { it.exerciseId }
        assertTrue("Expected t_core_focus to survive repair, got $ids", "t_core_focus" in ids)
        assertFalse("Expected t_core_main to be removed first, got $ids", "t_core_main" in ids)
    }
}
