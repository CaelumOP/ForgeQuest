package com.forgequest.app.data

import com.forgequest.app.data.plan.PlanJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

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
