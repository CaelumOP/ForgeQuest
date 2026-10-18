package com.forgequest.app.data.bridge

import org.junit.Assert.assertEquals
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
}
