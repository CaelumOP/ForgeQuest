package com.forgequest.app.data.bridge

import com.forgequest.app.data.plan.PlanJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A real reply copied from the Gemini app (2026-11-01): prose first, a "Plaintext" label, then the reply in a bare ``` fence. */
class GeminiAppReplyTest {
    private val text = javaClass.getResource("/bridge/gemini-app-reply.txt")!!.readText()

    @Test fun planIsReadWithoutAnAiRoundTrip() {
        val r = BridgeFormat.parse(text)
        assertTrue(r.issues.toString(), r.ok)
        val plan = PlanJson.parsePasted(r.planJson!!)
        assertNotNull(plan)
        assertEquals("Foundation Non-Stop Circuit (3-Day)", plan!!.name)
        assertEquals(listOf(1, 3, 5), plan.days.filter { !it.isRest }.map { it.dayOfWeek })
        assertEquals(8, plan.days.first { it.dayOfWeek == 1 }.items.size)
    }
}
