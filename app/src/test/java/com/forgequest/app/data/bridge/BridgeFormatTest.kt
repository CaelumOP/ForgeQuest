package com.forgequest.app.data.bridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BridgeFormatTest {
    @Test fun fullReplyWithPlan() {
        val r = BridgeFormat.parse(
            """
            Here you go!
            FORGEQUEST-REPLY v1
            ## TODAY
            Recovery is 34 %, so keep it to mobility and a walk.
            ## WEEK
            - HRV has been below your usual for three nights.
            - Protein goal hit on 2 of 6 days.
            ## COACH NOTES
            - Sleeps in two chunks on weekdays.
            ## PLAN
            ```json
            {"name":"Deload","days":[]}
            ```
            """.trimIndent()
        )
        assertTrue(r.ok)
        assertTrue(r.today.startsWith("Recovery is 34"))
        assertEquals(listOf("Sleeps in two chunks on weekdays."), r.notes)
        assertEquals("{\"name\":\"Deload\",\"days\":[]}", r.planJson)
    }

    @Test fun noChangeAndNoneNotes() {
        val r = BridgeFormat.parse("FORGEQUEST-REPLY v1\r\n## TODAY\r\nTrain as planned.\r\n## WEEK\r\n- Good week.\r\n## COACH NOTES\r\n- none\r\n## PLAN\r\nNO CHANGE")
        assertTrue(r.ok)
        assertNull(r.planJson)
        assertTrue(r.notes.isEmpty())
    }

    @Test fun missingSectionsAreNamed() {
        val r = BridgeFormat.parse("## TODAY\nRest.\n## PLAN\nsomething without json")
        assertEquals(listOf("header", "WEEK", "PLAN JSON"), r.issues)
    }

    @Test fun textOnlyMealReplyWithArabicNames() {
        val raw = """
            ```json
            {"mealId": "m8p4qz1b", "date": "2026-11-11", "time": "14:15", "dish": "مندي دجاج", "place": "الرومانسية", "items": [{"name": "نصف دجاجة مندي (لحم صافي)", "grams": 195, "kcal": 448, "protein": 47, "carbs": 0, "fat": 29}, {"name": "أرز مندي", "grams": 280, "kcal": 490, "protein": 8.4, "carbs": 84, "fat": 13.5}], "handVisible": false, "confidence": "high", "note": "تقدير نصي بدون صورة؛ خُصم نحو 35% لوزن العظم."}
            ```
        """.trimIndent()
        val meal = MealFormat.parseOne(raw)
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
