package com.forgequest.app.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The shared cases in food_search_cases.json; web/src/domain/food-search.test.ts runs the same ones. */
class FoodSearchTest {
    @Serializable data class Food(val name: String, val place: String)
    @Serializable data class Search(val q: String, val has: List<String>, val not: List<String>? = null)
    @Serializable data class Same(val a: String, val b: String, val same: Boolean)
    @Serializable data class SameFood(val a: List<String>, val b: List<String>, val same: Boolean)
    @Serializable data class Cases(
        val foods: List<Food>, val search: List<Search>, val sound: List<List<String>>, val places: List<String>,
        val samePlace: List<Same>, val sameFood: List<SameFood>,
    )

    private val cases: Cases = Json { ignoreUnknownKeys = true }.decodeFromString(
        javaClass.getResource("/food_search_cases.json")!!.readText(),
    )

    private fun found(q: String) = cases.foods.filter { FoodSearch.score(q, it.name, it.place) > 0f }.map { it.name }

    @Test fun searchCases() {
        for (c in cases.search) {
            val hits = found(c.q)
            c.has.forEach { assertTrue("\"${c.q}\" should find $it, found $hits", it in hits) }
            c.not.orEmpty().forEach { assertFalse("\"${c.q}\" should not find $it", it in hits) }
            if (c.has.isEmpty() && c.not == null) assertEquals("\"${c.q}\"", emptyList<String>(), hits)
        }
    }

    @Test fun soundCases() {
        for ((a, b) in cases.sound) {
            assertEquals("$a ~ $b", FoodSearch.soundKey(FoodSearch.words(a).first()), FoodSearch.soundKey(FoodSearch.words(b).first()))
        }
    }

    @Test fun samePlaceCases() {
        for (c in cases.samePlace) {
            assertEquals("${c.a} / ${c.b}: ${FoodSearch.placeSimilarity(c.a, c.b, cases.places)}", c.same, FoodSearch.samePlace(c.a, c.b, cases.places))
        }
    }

    @Test fun sameFoodCases() {
        for (c in cases.sameFood) {
            assertEquals("${c.a} / ${c.b}", c.same, FoodSearch.sameFood(c.a[0], c.a[1], c.b[0], c.b[1], cases.places))
        }
    }

    @Test fun normalizesArabicFormsTashkeelDigitsAndPunctuation() {
        assertEquals("افراح اا ه ي 34", FoodSearch.normalize("أَفْراحٌ إآ ة ى ـ ٣٤"))
        assertEquals("barns coffee", FoodSearch.normalize("Barn's  Coffee!"))
    }

    @Test fun exactNameRanksAboveSoundAlike() {
        assertTrue(FoodSearch.score("shawarma", "Chicken shawarma") > FoodSearch.score("shawarma", "شاورما لحم"))
    }
}
