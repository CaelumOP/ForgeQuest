package com.forgequest.app.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Test

/** Same vectors as web/src/domain/forge-score.vectors.json, so both apps score a week identically. */
class ForgeScoreTest {
    @Test
    fun sharedVectors() {
        val text = javaClass.classLoader!!.getResource("forge_score_vectors.json")!!.readText()
        val cases = Json.parseToJsonElement(text).jsonObject["cases"]!!.jsonArray
        check(cases.isNotEmpty())
        for (c in cases) {
            val o = c.jsonObject
            fun longs(k: String) = o[k]!!.jsonArray.map { it.jsonPrimitive.long }.toSet()
            val got = ForgeScore.compute(
                today = o["today"]!!.jsonPrimitive.long,
                scheduledDays = o["scheduled"]!!.jsonArray.map { it.jsonPrimitive.int }.toSet(),
                trainedDays = longs("trained"),
                proteinHitDays = longs("protein"),
            )
            val e = o["expect"]!!.jsonObject
            val want = ForgeWeek(
                e["weekStart"]!!.jsonPrimitive.long, e["done"]!!.jsonPrimitive.int, e["due"]!!.jsonPrimitive.int,
                e["score"]!!.jsonPrimitive.int, e["badge"]!!.jsonPrimitive.content, e["proteinDays"]!!.jsonPrimitive.int,
            )
            assertEquals(o["name"]!!.jsonPrimitive.content, want, got)
        }
    }
}
