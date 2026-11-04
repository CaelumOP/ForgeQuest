package com.forgequest.app.data

import com.forgequest.app.data.db.FoodCategory
import com.forgequest.app.data.db.FoodEntity
import com.forgequest.app.domain.Barcodes
import com.forgequest.app.domain.CatalogFood
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BarcodeTest {
    private fun listed(name: String) = CatalogFood(1, "product", "6281007033444", name, "", "", 100f, 1f, 2f, null, emptyList(), 0)
    private fun own() = FoodEntity(name = "Mine", category = FoodCategory.SNACKS, kcal = 200, protein = 5f, createdAt = 1, barcode = "6281007033444")

    private class Calls { val log = mutableListOf<String>() }

    private fun lookup(calls: Calls, own: FoodEntity? = null, catalog: CatalogFood? = null, online: OffResult = OffResult.NotFound) = BarcodeLookup(
        own = { calls.log += "own"; own },
        catalog = { calls.log += "catalog"; catalog },
        online = { calls.log += "online"; online },
    )

    @Test fun ownFoodsComeFirstAndStopTheLookup() = runBlocking {
        val calls = Calls()
        val r = lookup(calls, own = own(), catalog = listed("List")).find("6281007033444")
        assertTrue(r is BarcodeResult.Own)
        assertEquals(listOf("own"), calls.log)
    }

    @Test fun thenTheBundledListWithoutGoingOnline() = runBlocking {
        val calls = Calls()
        val r = lookup(calls, catalog = listed("List"), online = OffResult.Found(listed("Online"))).find("6281007033444")
        assertEquals("List", ((r as BarcodeResult.Listed).food).nameEn)
        assertEquals(listOf("own", "catalog"), calls.log)
    }

    @Test fun onlyThenOpenFoodFacts() = runBlocking {
        val calls = Calls()
        val r = lookup(calls, online = OffResult.Found(listed("Online"))).find("6281007033444")
        assertEquals("Online", (r as BarcodeResult.Online).food.nameEn)
        assertEquals(listOf("own", "catalog", "online"), calls.log)
    }

    @Test fun notFoundAndOfflineAreDifferent() = runBlocking {
        assertEquals(BarcodeResult.NotFound, lookup(Calls(), online = OffResult.NotFound).find("6281007033444"))
        assertEquals(BarcodeResult.Offline, lookup(Calls(), online = OffResult.Unreachable).find("6281007033444"))
    }

    @Test fun junkCodesAreNeverLookedUp() = runBlocking {
        val calls = Calls()
        assertEquals(BarcodeResult.NotFound, lookup(calls).find("12ab"))
        assertTrue(calls.log.isEmpty())
    }

    @Test fun upcAAndEan13WithLeadingZeroAreTheSameProduct() {
        assertEquals(listOf("012345678905", "0012345678905"), Barcodes.variants("012345678905"))
        assertEquals(listOf("0012345678905", "012345678905"), Barcodes.variants("0012345678905"))
        assertEquals(listOf("6281007033444"), Barcodes.variants("6281007033444"))
        assertNull(Barcodes.clean("123456789"))
        assertEquals("96385074", Barcodes.clean("9638 5074"))
    }

    @Test fun openFoodFactsPerHundredGrams() {
        val body = """{"code":"1","status":1,"product":{"product_name":"Galaxy","product_name_ar":"جالكسي","brands":"Galaxy, Mars",
            "serving_quantity":"40","nutriments":{"energy-kcal_100g":535,"proteins_100g":"7.2","carbohydrates_100g":56.1}}}"""
        val f = OpenFoodFacts.parse(body, "6281007033444")!!
        assertEquals("Galaxy", f.nameEn)
        assertEquals("جالكسي", f.nameAr)
        assertEquals("Galaxy", f.brand)
        assertEquals(535f, f.kcal, 0.01f)
        assertEquals(7.2f, f.protein, 0.01f)
        assertEquals(56.1f, f.carbs, 0.01f)
        assertEquals(40f, f.servingG)
    }

    @Test fun kilojouleOnlyProductsAreConverted() {
        val body = """{"status":1,"product":{"product_name":"Biscuit","nutriments":{"energy_100g":1046,"proteins_100g":6}}}"""
        val f = OpenFoodFacts.parse(body, "96385074")!!
        assertEquals(250f, f.kcal, 0.5f)
        assertEquals(0f, f.carbs)
        assertNull(f.servingG)
    }

    @Test fun noCaloriesNoNameOrNoProductMeansNotFound() {
        assertNull(OpenFoodFacts.parse("""{"status":1,"product":{"product_name":"X","nutriments":{"proteins_100g":3}}}""", "1"))
        assertNull(OpenFoodFacts.parse("""{"status":1,"product":{"nutriments":{"energy-kcal_100g":50}}}""", "1"))
        assertNull(OpenFoodFacts.parse("""{"status":0,"status_verbose":"product not found"}""", "1"))
        assertNull(OpenFoodFacts.parse("not json", "1"))
    }

    @Test fun labelPerServingBecomesPerHundredGrams() {
        val f = LabelRead(name = "Bar", basis = "serving", servingGrams = 40f, kcal = 200f, protein = 10f, carbs = 20f).toFood("6281007033444")!!
        assertEquals(500f, f.kcal, 0.01f)
        assertEquals(25f, f.protein, 0.01f)
        assertEquals(40f, f.servingG)
        assertEquals("6281007033444", f.barcode)
        assertNotNull(LabelRead(kcal = 80f).toFood("1"))
        assertNull(LabelRead(basis = "serving", kcal = 80f).toFood("1")) // per serving with no serving size
        assertNull(LabelRead(kcal = 0f).toFood("1"))
    }
}
