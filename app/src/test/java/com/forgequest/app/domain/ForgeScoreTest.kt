package com.forgequest.app.domain

import java.io.File
import kotlinx.serialization.json.*
import org.junit.Assert.assertEquals
import org.junit.Test

class ForgeScoreTest {
    @Test fun sharedVectors() {
        val fixture = listOf(File("../worker/test/forge-score-v2.vectors.json"), File("worker/test/forge-score-v2.vectors.json"))
            .first { it.isFile }
        val cases = Json.parseToJsonElement(fixture.readText()).jsonObject.getValue("cases").jsonArray
        check(cases.isNotEmpty())
        for (c in cases) {
            val v = c.jsonObject
            fun n(k: String) = v.getValue(k).jsonPrimitive.int
            val got = ForgeScore.breakdown(n("done"), n("target"), n("prCount"), n("proteinDays"))
            val e = v.getValue("expect").jsonObject
            fun want(k: String) = e.getValue(k).jsonPrimitive.int
            assertEquals(v.getValue("name").jsonPrimitive.content,
                ForgeScoreBreakdown(want("base"), want("prBonus"), want("proteinBonus"), want("extraBonus"), want("bonus"), want("score")), got)
        }
    }

    @Test fun scoringTimeIgnoresDuplicateDaysAndCappedEvents() {
        val events = listOf(ForgeScoreEvent(10, workoutDay = 1), ForgeScoreEvent(20, workoutDay = 1),
            ForgeScoreEvent(30, pr = true), ForgeScoreEvent(40, pr = true), ForgeScoreEvent(50, pr = true))
        assertEquals(40L, ForgeScore.reachedAt(1, events.reversed(), 0))
        assertEquals(0L, ForgeScore.reachedAt(3, emptyList(), 0))
    }

    @Test fun weeklyTargetAndSundayReset() {
        val ws = 20772L
        val scheduled = setOf(1, 3, 5)
        assertEquals(0, ForgeScore.compute(ws, scheduled, setOf(ws - 1)).score)
        val week = ForgeScore.compute(ws + 2, scheduled, setOf(ws, ws + 1, ws + 8), setOf(ws - 1, ws), prCount = 1)
        assertEquals(74, week.score)
        assertEquals(3, week.target)
        assertEquals(2, week.done)
        assertEquals(1, week.proteinDays)
        assertEquals(50, ForgeScore.compute(ws + 1, scheduled, setOf(ws), weeklyTargetOverride = 2).score)
        assertEquals(0, ForgeScore.compute(ws, emptySet(), emptySet()).score)
    }
}
