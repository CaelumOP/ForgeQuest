package com.forgequest.app.data

import com.forgequest.app.data.db.ExerciseEntity
import com.forgequest.app.data.plan.PlanChange
import com.forgequest.app.data.plan.PlanDraft
import com.forgequest.app.data.plan.PlanDraftDay
import com.forgequest.app.data.plan.PlanDraftItem
import com.forgequest.app.data.plan.PlanHash
import com.forgequest.app.data.plan.PlanJson
import com.forgequest.app.data.plan.PlanPatch
import com.forgequest.app.data.plan.PlanPatches
import com.forgequest.app.data.plan.PlanValidator
import com.forgequest.app.domain.ExerciseKind
import com.forgequest.app.domain.Muscle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanSafetyTest {
    private val lib = listOf(
        ExerciseEntity("push_up", "Push-up", ExerciseKind.REPS, listOf(Muscle.CHEST), listOf(Muscle.TRICEPS)),
        ExerciseEntity("dips", "Dips", ExerciseKind.REPS, listOf(Muscle.CHEST), listOf(Muscle.TRICEPS)),
        ExerciseEntity("bench_press", "Bench Press", ExerciseKind.WEIGHTED, listOf(Muscle.CHEST), listOf(Muscle.TRICEPS)),
        ExerciseEntity("plank", "Plank", ExerciseKind.HOLD, listOf(Muscle.ABS), emptyList()),
    ).associateBy { it.id }

    private val week = PlanDraft(
        "Test", days = (1..7).map { d ->
            if (d == 2) PlanDraftDay(2, "Push", items = listOf(PlanDraftItem("dips", sets = 3, repsMin = 8, repsMax = 12), PlanDraftItem("plank", sets = 3, seconds = 30)))
            else PlanDraftDay(d, "Rest", isRest = true)
        },
    )

    @Test
    fun nearMissIdsAreFixedAndBareUnknownsReported() {
        val draft = week.copy(days = week.days.map { d ->
            if (d.dayOfWeek == 2) d.copy(items = listOf(PlanDraftItem("push_ups"), PlanDraftItem("Bench Press"), PlanDraftItem("zercher_thing"), PlanDraftItem("nordic_curl", name = "Nordic curl", muscles = listOf("HAMSTRINGS"))))
            else d
        })
        val r = PlanValidator.validate(draft, lib)
        val ids = r.draft.days.first { it.dayOfWeek == 2 }.items.map { it.exerciseId }
        assertEquals(listOf("push_up", "bench_press", "zercher_thing", "nordic_curl"), ids)
        assertEquals(setOf("zercher_thing"), r.unknownIds)
        assertEquals(1, r.errors.size)
    }

    @Test
    fun numbersAreClamped() {
        val draft = week.copy(days = listOf(PlanDraftDay(1, "A", items = listOf(PlanDraftItem("push_up", sets = 25, repsMin = 12, repsMax = 8)))))
        val item = PlanValidator.validate(draft, lib).draft.days[0].items[0]
        assertEquals(10, item.sets)
        assertEquals(12, item.repsMax)
    }

    @Test
    fun patchSwapsOnlyTheNamedItemAndRecordsTheBase() {
        val patch = PlanPatch(listOf(PlanChange("swap", 2, exerciseId = "dips", newExerciseId = "push_ups")))
        val out = PlanPatches.apply(week, patch, lib)
        val tue = out.draft!!.days.first { it.dayOfWeek == 2 }
        assertEquals(listOf("push_up", "plank"), tue.items.map { it.exerciseId })
        assertEquals(PlanHash.of(week), out.draft!!.basedOnHash)
        assertEquals(week.days.filter { it.dayOfWeek != 2 }, out.draft!!.days.filter { it.dayOfWeek != 2 })
    }

    @Test
    fun patchErrorsApplyNothing() {
        val patch = PlanPatch(listOf(PlanChange("edit", 2, exerciseId = "plank", seconds = 45), PlanChange("remove", 3, exerciseId = "dips")))
        val out = PlanPatches.apply(week, patch, lib)
        assertNull(out.draft)
        assertEquals(1, out.errors.size)
        val ok = PlanPatches.apply(week, PlanPatch(listOf(PlanChange("edit", 2, exerciseId = "plank", seconds = 45))), lib)
        assertEquals(45, ok.draft!!.days.first { it.dayOfWeek == 2 }.items[1].seconds)
    }

    @Test
    fun hashChangesWithContentNotWithNames() {
        assertEquals(PlanHash.of(week), PlanHash.of(week.copy(name = "Renamed")))
        val tweaked = PlanPatches.apply(week, PlanPatch(listOf(PlanChange("edit", 2, exerciseId = "dips", sets = 4))), lib).draft!!
        assertNotEquals(PlanHash.of(week), PlanHash.of(tweaked))
    }

    @Test
    fun brokenBlocksAreSpotted() {
        assertTrue(PlanJson.hasBrokenBlock("Here you go\n```json\n{\"changes\": [ {\"type\": \"swap\", \n```"))
        assertEquals(false, PlanJson.hasBrokenBlock("```json\n{\"changes\":[{\"type\":\"remove\",\"dayOfWeek\":2,\"exerciseId\":\"dips\"}]}\n```"))
    }
}

class DebriefTest {
    @Test
    fun parsesFencedJsonAndIgnoresAnyScore() {
        val d = com.forgequest.app.data.plan.WorkoutDebrief.parse(
            "Sure!\n```json\n{\"score\": 2, \"summary\": \"Solid work.\", \"highlights\": [\"PR on dips\", \"all sets done\", \"extra\"], \"watch\": [\"knee note\"], \"nextTime\": [\"add 1 rep\"]}\n```",
        )!!
        assertNull(d.score)
        assertEquals(2, d.highlights.size)
        assertEquals("knee note", d.watch.single())
    }

    @Test
    fun junkGivesNull() {
        assertNull(com.forgequest.app.data.plan.WorkoutDebrief.parse("offline"))
        assertNull(com.forgequest.app.data.plan.WorkoutDebrief.parse("{\"score\": 7}"))
    }

    @Test
    fun summaryAloneIsEnough() {
        assertEquals("No score", com.forgequest.app.data.plan.WorkoutDebrief.parse("{\"summary\": \"No score\"}")!!.summary)
    }

    @Test
    fun oldSavedDebriefsWithAScoreStillDecode() {
        val d = com.forgequest.app.data.plan.WorkoutDebrief.decode("{\"score\":8,\"summary\":\"Strong session.\"}")!!
        assertEquals(8, d.score)
        assertNull(com.forgequest.app.data.plan.WorkoutDebrief.decode(com.forgequest.app.data.plan.WorkoutDebrief.encode(d.copy(score = null)))!!.score)
    }

    @Test
    fun forgivesDriftingShapes() {
        // Fast models sometimes send single strings where lists were asked for.
        val d = com.forgequest.app.data.plan.WorkoutDebrief.parse(
            "{\"summary\": \"Strong session.\", \"highlights\": \"12 dips, 2 more than last time\", \"watch\": [], \"next_time\": \"Add a set of rows\"}",
        )!!
        assertEquals(listOf("12 dips, 2 more than last time"), d.highlights)
        assertEquals(listOf("Add a set of rows"), d.nextTime)
    }
}
