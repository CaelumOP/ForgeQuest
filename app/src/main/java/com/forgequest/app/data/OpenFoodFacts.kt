package com.forgequest.app.data

import com.forgequest.app.domain.CatalogFood
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/** What the online lookup found. */
sealed interface OffResult {
    data class Found(val food: CatalogFood) : OffResult
    /** Open Food Facts has no such product, or it has no calories. */
    data object NotFound : OffResult
    /** Couldn't reach them (offline, slow, or an error answer). */
    data object Unreachable : OffResult
}

/** One request per scan to Open Food Facts, for products that aren't in the bundled list. */
class OpenFoodFacts(base: OkHttpClient, private val userAgent: String) {
    private val http = base.newBuilder().callTimeout(8, TimeUnit.SECONDS).build()

    suspend fun lookup(code: String): OffResult = withContext(Dispatchers.IO) {
        val url = "https://world.openfoodfacts.org/api/v2/product/$code?fields=product_name,product_name_ar,brands,nutriments,serving_quantity"
        try {
            http.newCall(Request.Builder().url(url).header("User-Agent", userAgent).build()).execute().use { r ->
                when {
                    r.code == 404 -> OffResult.NotFound
                    !r.isSuccessful -> OffResult.Unreachable
                    else -> parse(r.body?.string().orEmpty(), code)?.let { OffResult.Found(it) } ?: OffResult.NotFound
                }
            }
        } catch (_: IOException) {
            OffResult.Unreachable
        }
    }

    companion object {
        private const val KJ_PER_KCAL = 4.184

        /** The product in an Open Food Facts answer, per 100 g; null when it isn't there, has no name or has no calories. */
        fun parse(body: String, code: String): CatalogFood? {
            val root = runCatching { Json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
            if ((root["status"] as? JsonPrimitive)?.content?.let { it == "0" || it == "failure" } == true) return null
            val p = root["product"] as? JsonObject ?: return null
            val n = p["nutriments"] as? JsonObject ?: return null
            fun num(o: JsonObject, key: String): Double? = (o[key] as? JsonPrimitive)?.let { it.doubleOrNull ?: it.contentOrNull?.toDoubleOrNull() }
            // Some products list only kilojoules.
            val kcal = num(n, "energy-kcal_100g") ?: (num(n, "energy-kj_100g") ?: num(n, "energy_100g"))?.div(KJ_PER_KCAL)
            if (kcal == null || kcal < 0) return null
            fun text(key: String) = (p[key] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
            val en = text("product_name")
            val ar = text("product_name_ar")
            if (en.isBlank() && ar.isBlank()) return null
            val serving = num(p, "serving_quantity")?.takeIf { it > 0 }?.toFloat()
            return CatalogFood(
                id = 0, kind = "product", barcode = code.filter(Char::isDigit), nameEn = en, nameAr = ar,
                brand = text("brands").substringBefore(',').trim(), kcal = kcal.toFloat(),
                protein = (num(n, "proteins_100g") ?: 0.0).toFloat(), carbs = (num(n, "carbohydrates_100g") ?: 0.0).toFloat(),
                servingG = serving, units = emptyList(), scans = 0,
            )
        }
    }
}
