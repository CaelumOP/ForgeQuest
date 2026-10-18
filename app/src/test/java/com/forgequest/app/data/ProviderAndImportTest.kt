package com.forgequest.app.data

import com.forgequest.app.data.ai.GroqClient
import com.forgequest.app.data.plan.PlanJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class GroqModelPickTest {
    private val client = GroqClient()

    @Test
    fun prefersLlama4ScoutThenInstant() {
        assertEquals(
            "meta-llama/llama-4-scout-17b-16e-instruct",
            client.pickFastest(listOf("llama-3.3-70b-versatile", "meta-llama/llama-4-scout-17b-16e-instruct", "llama-3.1-8b-instant")),
        )
        assertEquals("llama-3.1-8b-instant", client.pickFastest(listOf("llama-guard-4-12b", "llama-3.1-8b-instant")))
        assertEquals(GroqClient.FALLBACK_MODEL, client.pickFastest(emptyList()))
    }
}

class PastedPlanTest {
    private val plan = """{"name":"Test","description":"d","days":[{"dayOfWeek":1,"title":"Push","isRest":false,"items":[{"exerciseId":"push_up","sets":3,"repsMin":10,"restSeconds":60}]}]}"""

    @Test
    fun importsJsonWithOrWithoutAFence() {
        assertNotNull(PlanJson.parsePasted(plan))
        assertNotNull(PlanJson.parsePasted("Here you go:\n```json\n$plan\n```"))
        assertEquals("Test", PlanJson.parsePasted("  $plan  ")?.name)
    }

    @Test
    fun leavesFreeTextForTheAi() {
        assertNull(PlanJson.parsePasted("Monday: push-ups 3x10, dips 3x8"))
    }
}
