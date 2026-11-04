package com.forgequest.app.data

import com.forgequest.app.data.backup.BackupFile
import com.forgequest.app.data.db.FoodCategory
import com.forgequest.app.data.db.FoodEntity
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupBarcodeTest {
    // The same settings BackupRepository uses.
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun file(vararg foods: FoodEntity) = BackupFile(
        createdAt = 1, exercises = emptyList(), plans = emptyList(), planDays = emptyList(), planItems = emptyList(),
        sessions = emptyList(), sets = emptyList(), foods = foods.toList(), meals = emptyList(), weights = emptyList(),
        chat = emptyList(), badges = emptyList(),
    )

    private fun food(barcode: String) = FoodEntity(name = "Galaxy", category = FoodCategory.SNACKS, kcal = 214, protein = 3f, createdAt = 5, barcode = barcode)

    @Test fun barcodeSurvivesABackup() {
        val text = json.encodeToString(BackupFile.serializer(), file(food("6281007033444")))
        assertTrue(text.contains("\"barcode\":\"6281007033444\""))
        assertEquals("6281007033444", json.decodeFromString(BackupFile.serializer(), text).foods.single().barcode)
    }

    @Test fun backupsFromBeforeBarcodesStillRestore() {
        val old = json.encodeToString(BackupFile.serializer(), file(food(""))).replace(",\"barcode\":\"\"", "")
        assertTrue(!old.contains("barcode"))
        val restored = json.decodeFromString(BackupFile.serializer(), old).foods.single()
        assertEquals("", restored.barcode)
        assertEquals("Galaxy", restored.name)
        assertEquals(214, restored.kcal)
    }
}
