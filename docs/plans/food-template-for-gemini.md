# ForgeQuest food list and barcode scanner: template (for review)

ForgeQuest is an Android fitness app (Kotlin, Jetpack Compose), English and formal Arabic.
The app tracks **calories and protein** (carbs are shown but not tracked). Target users are in the Gulf (Saudi, Kuwait, UAE...).
Goal: scanning a packaged food or drink should open it at its real pack size ("250 ml") with zero extra steps.

## 1. Food record (one row of `catalog.db`, read-only SQLite bundled in the app)

```sql
CREATE TABLE food(
  id INTEGER PRIMARY KEY,
  kind TEXT,        -- 'product' (packaged, has barcode) | 'dish' (Saudi dish) | 'basic' (everyday food)
  barcode TEXT,     -- digits only; empty for dish/basic
  name_en TEXT, name_ar TEXT,     -- at least one filled; Arabic is formal standard Arabic
  brand TEXT, brand_ar TEXT,
  kcal REAL, protein REAL, carbs REAL,   -- per 100 g (or per 100 ml for drinks)
  serving_g REAL,   -- grams (or ml) in one usual serving / one small pack; NULL if unknown
  units TEXT,       -- household units: 'label_en|label_ar|grams;...'  e.g. 'cup|كوب|158'
  source TEXT,      -- 'off' | 'off-twin:<barcode>' | 'sfda:<id>' | 'usda:<fdc_id>' | 'web:<url>'
  scans INTEGER,    -- popularity (Open Food Facts unique scans)
  liquid INTEGER DEFAULT 0   -- 1 = a drink: amounts shown in ml
);
CREATE INDEX food_barcode ON food(barcode);
```

Kotlin model (read from the row):

```kotlin
data class CatalogFood(
    val id: Long, val kind: String, val barcode: String, val nameEn: String, val nameAr: String, val brand: String,
    val kcal: Float, val protein: Float, val carbs: Float,      // per 100 g/ml
    val servingG: Float?, val units: List<CatalogUnit>, val scans: Int,
    val brandAr: String = "", val liquid: Boolean = false,
)
data class CatalogUnit(val en: String, val ar: String, val grams: Float)
```

Counts: 15,395 rows = products 14,936 (10,568 with a serving/pack size, 644 drinks, 731 with an Arabic name),
Saudi dishes 130 (all with serving), basics 329 (280 with serving, all with Arabic names).

Sample rows (kind, barcode, name_en, name_ar, brand, brand_ar, kcal, protein, carbs, serving_g, liquid, units, source, scans):

```
product, 8410076600448, Nature valley crunchy oats and berries, "Crunchy Oats & berries", Nature Valley, نيتشر فالي, 452.4, 7.4, 71.4, 42, 0, "", off, 178
dish,    "",            Qursan,                                  قرصان,                      "", "",                132,   3.7, 10.3, 623, 0, "", sfda:p15, 0
basic,   "",            "White rice, cooked",                    أرز أبيض مطبوخ,              "", "",                130,   2.69, 28.17, 158, 0, "cup|كوب|158", usda:168878, 0
```

Sources: products from Open Food Facts (ODbL), Gulf countries only (SA/AE/KW/BH/QA/OM);
dishes from the Saudi Food Composition Tables (SFDA); basics from USDA FoodData Central.
Pack size rule (build time): the product's printed serving if any, else the pack size from its `quantity` text
("250ml", "40 g"), only if small (drinks <= 750 ml, other foods <= 200 g). Big packs stay unknown.
Rows whose per-100 g values are impossible (over 100 g, or kcal over 900) were dropped; ~20.7k Gulf products have no usable nutrition and are missing.

## 2. Barcode scan result (what the scanner hands to the screen)

Lookup order, stops at the first hit (offline first, then one web request):

1. The user's own saved foods (by barcode, also tries the 12/13-digit variants).
2. The bundled list above (`catalog.db`, works offline).
3. Open Food Facts online: `GET https://world.openfoodfacts.org/api/v2/product/<barcode>?fields=product_name,product_name_ar,brands,nutriments,serving_quantity,quantity,product_quantity,product_quantity_unit`
4. Not found: the user can photograph the nutrition label (AI reads it).

```kotlin
sealed interface BarcodeResult {
    data class Own(val food: FoodEntity)        // already the user's food: log again, no lookup
    data class Listed(val food: CatalogFood)    // from the bundled list
    data class Online(val food: CatalogFood)    // from Open Food Facts (same model as the list)
    data object NotFound
    data object Offline                         // not found locally and the web is unreachable
}
```

An online product becomes the same `CatalogFood`. Rules applied to the web answer:
- calories from `energy-kcal_100g`, else kJ / 4.184; no calories or no name = not found.
- a protein/carbs value over 100 g is "repaired" by moving the decimal point (1001 -> 10.01) if it fits the calories (4 kcal/g), else carbs = 0 and protein = reject.
- `liquid` from the pack unit (ml/cl/dl/l); default serving = printed serving, else small pack size.

## 3. What the user sees after a scan (bottom sheet, "how much?")

```
[Food name, in the app language]
[Brand]
(chips)  Serving · 250 ml      <- if servingG known; plus household units (cup · 158 g) ; plus "Grams" or "Milliliters"
[ amount field ]   default 1 serving (or 100 g / 100 ml if no size known)
60 kcal · 3 g protein · 10 g carbs (250 ml)      <- recalculated live from the amount
[ Log it ]
```

Search results show a row: name, then `brand · 250 ml · 60 kcal per 100 g`.

## 4. Known weaknesses (please look here first)

- ~4,400 products have no pack size; they open at 100 g.
- Dishes and basics have no drink flag (milk, juice, soup still show grams).
- Many Gulf products have no Arabic name (only 731 of 14,936); brands have Arabic names for 226 brands.
- Same product name with different sizes (Pepsi 250 ml vs 330 ml) = separate barcodes; search shows the size to tell them apart.
- Open Food Facts is user-edited: wrong values exist (one record had 1001 g carbs); the repair rule above is a heuristic.
- UPC-E barcodes are not expanded; no UI to edit a food's barcode.

## 5. Question for you

How would you improve (a) the record fields, (b) the lookup order and data cleaning, (c) the scan-result sheet,
so that a person scanning any food or drink in the Gulf gets the right size and numbers with no extra taps?
Keep suggestions realistic for a free, offline-first, solo-developer app (no paid APIs).
