package com.forgequest.app.data

import com.forgequest.app.data.ai.GeminiClient
import com.forgequest.app.data.plan.CoachMemory
import com.forgequest.app.data.plan.PlanJson
import com.forgequest.app.data.seed.ExerciseCatalog
import com.forgequest.app.data.seed.StarterPlans
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
        (StarterPlans.all + StarterPlans.more).flatMap { p -> p.days.flatMap { d -> d.items.map { it.exerciseId } } }.forEach { id ->
            assertTrue("Missing exercise $id", id in ids)
        }
    }

    @Test
    fun everyPlanTextHasAnArabicPair() {
        val plans = listOf(
            StarterPlans.hemoFoundation, StarterPlans.fullBodyBeginner, StarterPlans.fullBodyDumbbells, StarterPlans.pushPullLegs,
            StarterPlans.fullBodyGym, StarterPlans.upperLowerGym, StarterPlans.upperLowerDumbbells,
            StarterPlans.calisthenicsLevel2, StarterPlans.minimalTwoDay, StarterPlans.zeroEquipment,
        )
        val texts = plans.flatMap { p -> listOf(p.name, p.description) + p.days.flatMap { listOf(it.title, it.note) } }.filter { it.isNotBlank() }
        val missing = texts.filter { it !in StarterPlans.arabic }
        assertTrue("no Arabic for $missing", missing.isEmpty())
        assertEquals(StarterPlans.arabic.size, StarterPlans.arabic.values.toSet().size)
    }

    @Test
    fun morePlansStaySeparateFromOnboarding() {
        assertEquals(4, StarterPlans.all.size)
        assertEquals(6, StarterPlans.more.size)
        assertTrue(StarterPlans.more.all { p -> p.days.any { !it.isRest && it.items.isNotEmpty() } })
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

    @Test
    fun everyMuscleGroupHasAtLeastTwoPrimaryExercises() {
        com.forgequest.app.domain.Muscle.entries.forEach { m ->
            assertTrue("$m has fewer than 2 primary exercises", ExerciseCatalog.all.count { m in it.primary } >= 2)
        }
    }

    @Test
    fun everyCatalogExerciseHasAnArabicName() {
        ExerciseCatalog.all.forEach { assertTrue("${it.id} has no Arabic name", !com.forgequest.app.data.seed.ExerciseNamesAr.byId[it.id].isNullOrBlank()) }
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

class MealFormatTest {
    @Test
    fun textOnlyMealReplyWithArabicNames() {
        val raw = """
            ```json
            {"mealId": "m8p4qz1b", "date": "2026-11-11", "time": "14:15", "dish": "مندي دجاج", "place": "الرومانسية", "items": [{"name": "نصف دجاجة مندي (لحم صافي)", "grams": 195, "kcal": 448, "protein": 47, "carbs": 0, "fat": 29}, {"name": "أرز مندي", "grams": 280, "kcal": 490, "protein": 8.4, "carbs": 84, "fat": 13.5}], "handVisible": false, "confidence": "high", "note": "تقدير نصي بدون صورة؛ خُصم نحو 35% لوزن العظم."}
            ```
        """.trimIndent()
        val meal = com.forgequest.app.data.bridge.MealFormat.parseOne(raw)
        assertNotNull(meal)
        assertFalse(meal!!.handVisible)
        assertEquals("مندي دجاج", meal.dish)
        assertEquals("الرومانسية", meal.place)
        assertEquals(2, meal.items.size)
        assertEquals("نصف دجاجة مندي (لحم صافي)", meal.items[0].name)
        assertEquals(938, meal.kcal)
        val scan = meal.toScan()
        assertFalse(scan.handVisible)
        assertEquals("أرز مندي", scan.items[1].name)
    }
}
