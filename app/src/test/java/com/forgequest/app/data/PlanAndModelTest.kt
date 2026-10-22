package com.forgequest.app.data

import com.forgequest.app.data.ai.GeminiClient
import com.forgequest.app.data.plan.CoachMemory
import com.forgequest.app.data.plan.PlanJson
import com.forgequest.app.data.seed.ExerciseCatalog
import com.forgequest.app.data.seed.StarterPlans
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanJsonTest {
    private val reply = """
        Here's a simple **3-day** plan.

        ```json
        {"name": "Starter", "days": [
          {"dayOfWeek": 1, "title": "Push", "items": [{"exerciseId": "push_up", "sets": 3, "repsMin": 10}]},
          {"dayOfWeek": 2, "title": "Rest", "isRest": true},
          {"dayOfWeek": 9, "title": "Bad day"}
        ]}
        ```
    """.trimIndent()

    @Test
    fun extractsPlanAndDropsInvalidDays() {
        val plan = PlanJson.extract(reply)!!
        assertEquals("Starter", plan.name)
        assertEquals(listOf(1, 2), plan.days.map { it.dayOfWeek })
        assertEquals("push_up", plan.days[0].items[0].exerciseId)
    }

    @Test
    fun stripLeavesOnlyTheProse() {
        assertEquals("Here's a simple **3-day** plan.", PlanJson.strip(reply))
    }

    @Test
    fun noBlockMeansNoPlan() {
        assertNull(PlanJson.extract("Do more pull-ups."))
        assertNull(PlanJson.extract("```json\n{\"name\": \"x\"}\n```"))
    }

    @Test
    fun roundTrip() {
        val plan = StarterPlans.hemoFoundation
        assertEquals(plan, PlanJson.decode(PlanJson.encode(plan)))
    }
}

class StarterPlanTest {
    @Test
    fun everyStarterExerciseExistsInTheLibrary() {
        val ids = ExerciseCatalog.all.map { it.id }.toSet()
        StarterPlans.all.flatMap { p -> p.days.flatMap { d -> d.items.map { it.exerciseId } } }.forEach { id ->
            assertTrue("Missing exercise $id", id in ids)
        }
    }

    @Test
    fun hemoFoundationMatchesTheVideos() {
        val plan = StarterPlans.hemoFoundation
        assertEquals(setOf(1, 2, 3, 5, 6), plan.days.filter { !it.isRest }.map { it.dayOfWeek }.toSet())
        val day1 = plan.days.first { it.dayOfWeek == 1 }
        assertEquals(9, day1.items.size)
        assertEquals("jumping_jacks", day1.items.first().exerciseId)
        assertEquals(5, day1.items.first().sets)
        assertTrue(day1.items.drop(1).all { it.circuit && it.sets == 3 })
        assertEquals(30, day1.items.first { it.exerciseId == "incline_hollow_plank" }.seconds)
    }

    @Test
    fun hemoExercisesLinkToTheirVideo() {
        val byId = ExerciseCatalog.all.associateBy { it.id }
        StarterPlans.hemoFoundation.days.flatMap { it.items }.forEach { item ->
            assertTrue("${item.exerciseId} has no video", byId.getValue(item.exerciseId).videoUrl?.contains("youtube.com") == true)
        }
    }

    @Test
    fun catalogIdsAreUnique() {
        assertEquals(ExerciseCatalog.all.size, ExerciseCatalog.all.map { it.id }.toSet().size)
    }
}

class ModelPickTest {
    private val client = GeminiClient()

    // Real list returned for a free key on 2026-10-18 (trimmed).
    private val models = listOf(
        "gemini-2.5-flash", "gemini-2.5-pro", "gemini-flash-latest", "gemini-pro-latest", "gemini-3-flash-preview",
        "gemini-3.1-pro-preview", "gemini-3.1-flash-lite", "gemini-3.5-flash", "gemini-3.6-flash", "gemini-3.7-flash",
        "gemini-3.8-flash", "gemini-3.8-flash-tts", "embedding-001",
    )

    @Test
    fun picksNewestFlash() {
        assertEquals("gemini-3.8-flash", client.pickNewest(models, "flash"))
    }

    @Test
    fun newerPreviewBeatsOldStable() {
        assertEquals("gemini-3.1-pro-preview", client.pickNewest(models, "pro"))
    }

    @Test
    fun picksNewestFlashLiteWithoutConfusingItWithFlash() {
        assertEquals("gemini-3.5-flash-lite", client.pickNewest(models + "gemini-3.5-flash-lite", "flash-lite"))
        assertEquals("gemini-3.8-flash", client.pickNewest(models + "gemini-3.9-flash-lite", "flash"))
    }

    @Test
    fun readsGooglesRetryDelay() {
        val detail = "limit: 20, model: gemini-3.8-flash\nPlease retry in 12h7m14.002832781s."
        assertEquals((12 * 3600 + 7 * 60 + 14) * 1000L + 2, client.retryAfterMs(detail))
        assertEquals(42_500L, client.retryAfterMs("Please retry in 42.5s."))
        assertEquals(60_000L, client.retryAfterMs("quota exceeded"))
    }

    @Test
    fun stableWinsOnTheSameVersion() {
        assertEquals("gemini-3.1-pro", client.pickNewest(listOf("gemini-3.1-pro-preview", "gemini-3.1-pro"), "pro"))
        assertNull(client.pickNewest(listOf("gemini-flash-latest"), "pro"))
    }
}

class BidiTest {
    @Test
    fun isolatesEnglishInsideArabic() {
        val out = com.forgequest.app.ui.components.isolateLtr("تمارين مثل (Australian pull-up) و(Dead hang) يوميا")
        assertEquals("تمارين مثل ⁦(Australian pull‑up)⁩ و⁦(Dead hang)⁩ يوميا", out)
        assertEquals("بدون إنجليزي", com.forgequest.app.ui.components.isolateLtr("بدون إنجليزي"))
    }
}

class PlanIntentTest {
    @Test
    fun onlyRequestsToMakeOrChangeAPlanCount() {
        assertTrue(PlanIntent.matches("Build me a 4-day plan"))
        assertTrue(PlanIntent.matches("Can you change my routine so legs are on Friday?"))
        assertTrue(PlanIntent.matches("اعمل لي خطة تمارين 3 أيام"))
        assertFalse(PlanIntent.matches("Can you explain how to do a dip?"))
        assertFalse(PlanIntent.matches("How is my plan going?"))
        assertFalse(PlanIntent.matches("I planned to rest tomorrow"))
        assertFalse(PlanIntent.matches("كيف أتحسن في العقلة؟"))
    }

    @Test
    fun editsNamingADayOrPlanExerciseCount() {
        assertTrue(PlanIntent.matches("My shoulder hurts on dips, can we swap them on Tuesday?"))
        assertTrue(PlanIntent.matches("Add more pull-ups please", listOf("Pull-ups", "dips")))
        assertTrue(PlanIntent.matches("استبدل تمرين الضغط يوم الثلاثاء"))
        assertFalse(PlanIntent.matches("Should I add salt to my eggs?", listOf("dips")))
    }
}

class CoachMemoryTest {
    @Test
    fun extractsAndStripsMemoryLines() {
        val reply = "Rest the shoulder this week.\n\n**MEMORY:** Left shoulder hurts on overhead pressing.\nMEMORY: Trains at home with a pull-up bar"
        assertEquals(listOf("Left shoulder hurts on overhead pressing", "Trains at home with a pull-up bar"), CoachMemory.extract(reply))
        assertEquals("Rest the shoulder this week.", CoachMemory.strip(reply).trim())
    }

    @Test
    fun mergeSkipsDuplicatesAndCaps() {
        val merged = CoachMemory.merge("Has rings\nhas rings", listOf("Has Rings", "Vegetarian"))
        assertEquals("Has rings\nhas rings\nVegetarian", merged)
        val many = CoachMemory.merge("", (1..20).map { "note $it" })
        assertEquals(CoachMemory.MAX_NOTES, many.lines().size)
        assertTrue(many.lines().first() == "note 6")
    }

    @Test
    fun partialStreamHidesUnfinishedPlanBlock() {
        val streaming = "Here's your week.\n\n```json\n{\"name\": \"Pl"
        assertEquals("Here's your week.", PlanJson.stripPartial(streaming))
        val done = "Here's your week.\n\n```json\n{\"name\": \"P\", \"days\": [{\"dayOfWeek\": 1, \"title\": \"A\"}]}\n```\nMEMORY: Likes short sessions"
        assertEquals("Here's your week.", PlanJson.stripPartial(done))
    }
}
