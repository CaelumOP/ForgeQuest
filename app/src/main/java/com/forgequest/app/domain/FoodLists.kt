package com.forgequest.app.domain

import com.forgequest.app.data.db.FoodCategory
import com.forgequest.app.data.db.FoodEntity
import com.forgequest.app.data.db.FoodUsage

object FoodLists {
    /** Foods logged before, most recent first, leaving out the open meal's own foods (those are listed above). */
    fun recent(foods: List<FoodEntity>, usage: Map<Long, FoodUsage>, excludeCategory: FoodCategory, limit: Int = 8): List<FoodEntity> =
        foods.filter { it.category != excludeCategory && usage[it.id] != null }
            .sortedByDescending { usage[it.id]!!.lastAt }
            .take(limit)
}
