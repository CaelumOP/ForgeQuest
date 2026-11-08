package com.forgequest.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogTest {
    private fun food(id: Long, en: String, ar: String, kcal: Float = 100f, brand: String = "", scans: Int = 0, units: String = "", protein: Float = 10f) =
        CatalogFood(id, "basic", "", en, ar, brand, kcal, protein, 20f, null, Catalog.parseUnits(units), scans)

    private val foods = listOf(
        food(1, "Chicken Kabsa", "كبسة دجاج", units = "plate|صحن|450;cup|كوب|160"),
        food(2, "Shawarma Chicken Sandwich", "ساندويتش شاورما دجاج"),
        food(3, "Coca-Cola", "كوكا كولا", brand = "Coca-Cola", scans = 2000),
        food(4, "Banana", "موز"),
        food(5, "Dates (Medjool)", "تمر مجدول"),
    )

    @Test fun byProteinPutsTheHighestProteinFirstAndKeepsTiesInRelevanceOrder() {
        val chicken = listOf(
            food(1, "Chicken Soup", "شوربة دجاج", protein = 3f),
            food(2, "Chicken Breast", "صدر دجاج", protein = 31f),
            food(3, "Chicken Wings", "أجنحة دجاج", protein = 18f),
            food(4, "Chicken Roll", "رول دجاج", protein = 18f),
        )
        val byRelevance = Catalog.search(chicken, "chicken", 10).map { it.id }
        val byProtein = Catalog.search(chicken, "chicken", 10, byProtein = true).map { it.id }
        assertEquals(2L, byProtein.first())
        assertEquals(1L, byProtein.last())
        assertEquals(byRelevance.filter { it == 3L || it == 4L }, byProtein.filter { it == 3L || it == 4L })
        assertEquals(2, Catalog.search(chicken, "chicken", 2, byProtein = true).size)
    }

    private fun hits(q: String) = Catalog.search(foods, q, 10).map { it.id }

    @Test fun englishSearch() {
        assertEquals(listOf(1L), hits("kabsa"))
        assertTrue(2L in hits("shawarma"))
    }

    @Test fun arabicSpellingsAndSounds() {
        assertTrue(1L in hits("كبسه")) // ة/ه
        assertTrue(2L in hits("شاورما"))
        assertTrue(2L in hits("shawerma")) // English by sound
        assertTrue(3L in hits("كوكاكولا")) // typed together
    }

    @Test fun blankFindsNothingAndLimitHolds() {
        assertTrue(hits("  ").isEmpty())
        assertEquals(1, Catalog.search(foods, "chicken", 1).size)
    }

    @Test fun unitsParse() {
        val u = Catalog.parseUnits("plate|صحن|450;cup|كوب|160;bad|x;zero|صفر|0")
        assertEquals(2, u.size)
        assertEquals(160f, u[1].grams)
        assertEquals("كوب", u[1].label(true))
        assertEquals("cup", u[1].label(false))
    }

    @Test fun amountMaths() {
        val f = food(9, "Rice", "أرز", kcal = 130f).copy(protein = 2.7f, carbs = 28.2f)
        val cups = CatalogAmount(2f, 160f)
        assertEquals(320f, cups.grams)
        assertEquals(416, Catalog.kcalFor(f, cups.grams))
        assertEquals(8.6f, Catalog.proteinFor(f, cups.grams), 0.001f)
        assertEquals(90.2f, Catalog.carbsFor(f, cups.grams), 0.001f)
        assertEquals(65, Catalog.kcalFor(f, CatalogAmount(50f, 1f).grams))
        assertEquals("21.5", Catalog.formatNumber(21.5f))
        assertEquals("160", Catalog.formatNumber(160f))
    }

    @Test fun arabicBrandIsSearchedAndShown() {
        val pepsi = food(7, "Cola Drink", "مشروب غازي", brand = "Pepsi").copy(brandAr = "بيبسي")
        assertEquals(listOf(7L), Catalog.search(listOf(pepsi), "بيبسي", 5).map { it.id })
        assertEquals(listOf(7L), Catalog.search(listOf(pepsi), "pepsi", 5).map { it.id })
        assertEquals("بيبسي", pepsi.brand(true))
        assertEquals("Pepsi", pepsi.brand(false))
        assertEquals("Pepsi", pepsi.copy(brandAr = "").brand(true))
    }
}
