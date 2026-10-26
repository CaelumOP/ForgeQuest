package com.forgequest.app.data

import com.forgequest.app.data.db.Converters
import com.forgequest.app.data.db.FoodCategory
import com.forgequest.app.data.db.FoodEntity
import com.forgequest.app.data.db.FoodPart
import com.forgequest.app.data.db.withParts
import org.junit.Assert.assertEquals
import org.junit.Test

/** Saved foods with removable parts; web/src/data/food.test.ts has the same cases. */
class FoodPartsTest {
    private val shawarma = listOf(
        FoodPart("Bread", 80, 220, 7f, 44f),
        FoodPart("Chicken", 120, 250, 30f, 0f),
        FoodPart("Garlic sauce", 30, 180, 0.5f, 2f),
        FoodPart("Ketchup", 15, 17, 0.2f, 4f, on = false),
    )
    private val food = FoodEntity(name = "Chicken shawarma", category = FoodCategory.LUNCH, kcal = 0, protein = 0f, createdAt = 0)

    @Test fun totalsComeFromThePartsThatAreOn() {
        val f = food.withParts(shawarma)
        assertEquals(650, f.kcal)
        assertEquals(37.5f, f.protein, 0.01f)
        assertEquals(46f, f.carbs, 0.01f)
        assertEquals(4, f.parts.size) // the unticked ketchup is kept, switched off
    }

    @Test fun noPartsKeepsTheTypedNumbers() {
        val f = food.copy(kcal = 300, protein = 20f).withParts(emptyList())
        assertEquals(300, f.kcal)
        assertEquals(20f, f.protein, 0f)
    }

    @Test fun partsSurviveTheDatabaseColumn() {
        val c = Converters()
        assertEquals(shawarma, c.stringToParts(c.partsToString(shawarma)))
        assertEquals(emptyList<FoodPart>(), c.stringToParts("[]"))
        assertEquals(emptyList<FoodPart>(), c.stringToParts("not json"))
    }
}
