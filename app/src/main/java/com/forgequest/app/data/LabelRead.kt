package com.forgequest.app.data

import com.forgequest.app.domain.CatalogFood
import kotlinx.serialization.Serializable

/** What the AI read off a nutrition label photo: values for 100 g/ml ([basis] "100g") or for one serving ([basis] "serving"). */
@Serializable
data class LabelRead(
    val name: String = "",
    val basis: String = "100g",
    val servingGrams: Float = 0f,
    val kcal: Float = 0f,
    val protein: Float = 0f,
    val carbs: Float = 0f,
) {
    /** The product per 100 g, or null when the label wasn't readable (no calories, or per serving with no serving size). */
    fun toFood(code: String): CatalogFood? {
        if (kcal <= 0f) return null
        val perServing = basis.equals("serving", ignoreCase = true)
        if (perServing && servingGrams <= 0f) return null
        val k = if (perServing) 100f / servingGrams else 1f
        return CatalogFood(
            id = 0, kind = "product", barcode = code.filter(Char::isDigit), nameEn = name.trim(), nameAr = "", brand = "",
            kcal = kcal * k, protein = protein * k, carbs = carbs * k, servingG = servingGrams.takeIf { it > 0f }, units = emptyList(), scans = 0,
        )
    }
}
