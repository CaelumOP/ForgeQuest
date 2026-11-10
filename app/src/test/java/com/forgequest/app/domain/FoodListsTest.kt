package com.forgequest.app.domain

import com.forgequest.app.data.db.FoodCategory
import com.forgequest.app.data.db.FoodEntity
import com.forgequest.app.data.db.FoodUsage
import org.junit.Assert.assertEquals
import org.junit.Test

class FoodListsTest {
    private fun food(id: Long, cat: FoodCategory) = FoodEntity(id = id, name = "f$id", category = cat, kcal = 100, protein = 5f, createdAt = 0L)
    private fun use(id: Long, at: Long) = id to FoodUsage(id, 1, at)

    @Test fun newestFirstAndSkipsNeverLogged() {
        val foods = listOf(food(1, FoodCategory.LUNCH), food(2, FoodCategory.DINNER), food(3, FoodCategory.SNACKS))
        val r = FoodLists.recent(foods, mapOf(use(1, 10), use(2, 20)), FoodCategory.BREAKFAST)
        assertEquals(listOf(2L, 1L), r.map { it.id })
    }

    @Test fun excludesOpenMeal() {
        val foods = listOf(food(1, FoodCategory.LUNCH), food(2, FoodCategory.DINNER))
        val r = FoodLists.recent(foods, mapOf(use(1, 10), use(2, 20)), FoodCategory.DINNER)
        assertEquals(listOf(1L), r.map { it.id })
    }

    @Test fun honoursLimit() {
        val foods = (1L..12L).map { food(it, FoodCategory.LUNCH) }
        val r = FoodLists.recent(foods, foods.associate { use(it.id, it.id) }, FoodCategory.DINNER)
        assertEquals(8, r.size)
        assertEquals(12L, r.first().id)
    }
}
