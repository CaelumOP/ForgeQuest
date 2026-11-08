package com.forgequest.app.domain

import kotlin.math.round

/** A household unit of a catalogue food ("cup", 160 g). */
data class CatalogUnit(val en: String, val ar: String, val grams: Float) {
    fun label(arabic: Boolean) = if (arabic && ar.isNotBlank()) ar else en.ifBlank { ar }
}

/** One row of the read-only food list (catalog.db). Nutrition is per 100 g (or 100 ml). */
data class CatalogFood(
    val id: Long, val kind: String, val barcode: String, val nameEn: String, val nameAr: String, val brand: String,
    val kcal: Float, val protein: Float, val carbs: Float, val servingG: Float?, val units: List<CatalogUnit>, val scans: Int,
    val brandAr: String = "",
    /** A drink: amounts are shown in ml instead of g (1 ml is counted as 1 g). */
    val liquid: Boolean = false,
) {
    fun name(arabic: Boolean) = if (arabic && nameAr.isNotBlank()) nameAr else tidy(nameEn.ifBlank { nameAr })

    /** The brand is shown on its own line, so "KDD double shot..." under the brand "KDD" becomes "Double shot...". */
    private fun tidy(n: String): String {
        val b = brand.trim()
        val rest = if (b.length >= 2 && n.startsWith(b, ignoreCase = true)) n.substring(b.length).trimStart(' ', '-', ':', ',') else n
        return rest.ifBlank { n }.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }
    fun brand(arabic: Boolean) = if (arabic && brandAr.isNotBlank()) brandAr else brand
}

/** What the person is logging: [count] of something that weighs [gramsEach] (grams: count = grams, gramsEach = 1). */
data class CatalogAmount(val count: Float, val gramsEach: Float) {
    val grams get() = count * gramsEach
}

object Catalog {
    /** `piece|حبة|50;cup|كوب|160` into units; malformed parts are skipped. */
    fun parseUnits(raw: String?): List<CatalogUnit> = raw.orEmpty().split(';').mapNotNull { part ->
        val f = part.split('|')
        val g = f.getOrNull(2)?.trim()?.toFloatOrNull()
        if (f.size < 3 || g == null || g <= 0f) null else CatalogUnit(f[0].trim(), f[1].trim(), g)
    }

    /** 160 -> 160, 21.5 -> 21.5 */
    fun formatNumber(v: Float): String = (round(v * 10f) / 10f).toString().removeSuffix(".0")

    fun kcalFor(food: CatalogFood, grams: Float): Int = round(food.kcal * grams / 100f).toInt()
    fun proteinFor(food: CatalogFood, grams: Float): Float = round(food.protein * grams / 10f) / 10f
    fun carbsFor(food: CatalogFood, grams: Float): Float = round(food.carbs * grams / 10f) / 10f

    /** Best matches first (same matching as the person's own foods); among equals the more scanned/common one. */
    fun search(foods: List<CatalogFood>, query: String, limit: Int, byProtein: Boolean = false): List<CatalogFood> {
        if (query.isBlank()) return emptyList()
        return foods.asSequence()
            .map { f ->
                // The brand counts in both languages: "بيبسي" finds Pepsi, "pepsi" finds the Arabic-named row.
                val brands = "${f.brand} ${f.brandAr}".trim()
                val s = maxOf(
                    if (f.nameEn.isNotBlank()) FoodSearch.score(query, f.nameEn, brands) else 0f,
                    if (f.nameAr.isNotBlank()) FoodSearch.score(query, f.nameAr, brands) else 0f,
                )
                f to s
            }
            .filter { it.second > 0f }
            .sortedWith(compareByDescending<Pair<CatalogFood, Float>> { it.second }.thenByDescending { it.first.scans })
            .map { it.first }
            // Most protein per 100 g first, out of the 40 best matches; the sort is stable, so equals keep their relevance order.
            .let { if (byProtein) it.take(PROTEIN_POOL).sortedByDescending { f -> f.protein } else it }
            .take(limit).toList()
    }

    private const val PROTEIN_POOL = 40
}
