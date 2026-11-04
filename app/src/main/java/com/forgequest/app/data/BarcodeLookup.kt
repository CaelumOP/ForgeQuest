package com.forgequest.app.data

import com.forgequest.app.data.db.FoodEntity
import com.forgequest.app.domain.Barcodes
import com.forgequest.app.domain.CatalogFood

sealed interface BarcodeResult {
    /** Already one of the person's foods: logging it again needs no lookup. */
    data class Own(val food: FoodEntity) : BarcodeResult
    data class Listed(val food: CatalogFood) : BarcodeResult
    data class Online(val food: CatalogFood) : BarcodeResult
    data object NotFound : BarcodeResult
    /** Not in the person's foods or the bundled list, and Open Food Facts couldn't be reached. */
    data object Offline : BarcodeResult
}

/**
 * The order a scanned barcode is looked up in: the person's own foods, then the bundled list (both work offline),
 * and only then one request to Open Food Facts.
 */
class BarcodeLookup(
    private val own: suspend (List<String>) -> FoodEntity?,
    private val catalog: suspend (String) -> CatalogFood?,
    private val online: suspend (String) -> OffResult,
) {
    suspend fun find(raw: String): BarcodeResult {
        val codes = Barcodes.variants(raw)
        if (codes.isEmpty()) return BarcodeResult.NotFound
        own(codes)?.let { return BarcodeResult.Own(it) }
        catalog(codes.first())?.let { return BarcodeResult.Listed(it) }
        return when (val r = online(codes.first())) {
            is OffResult.Found -> BarcodeResult.Online(r.food)
            OffResult.NotFound -> BarcodeResult.NotFound
            OffResult.Unreachable -> BarcodeResult.Offline
        }
    }
}
