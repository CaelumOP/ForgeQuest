# Catalogue schema (shared by every lane)

Every source writes a CSV in `tools/fooddb/out/` with these columns (UTF-8, header row):

| column | meaning |
|---|---|
| kind | `product` (packaged, has barcode), `dish` (Saudi dish), `basic` (everyday food) |
| barcode | digits only; empty for dishes/basics |
| name_en, name_ar | at least one filled; Arabic is formal standard Arabic |
| brand | empty if none |
| brand_ar | Arabic brand name (sources/brands_ar.csv); empty if unknown |
| kcal, protein, carbs | per 100 g (or 100 ml for drinks); decimals allowed; kcal required |
| serving_g | grams in one usual serving/pack; empty if unknown |
| units | household units as `label_en|label_ar|grams;...` e.g. `piece|حبة|50;cup|كوب|160` |
| source | `off`, `off-twin:<code>`, `web:<url>`, `sfda`, `usda:<fdc_id>` |
| scans | popularity (OFF unique scans); 0 for others |

`build_catalog.py` (later) merges the CSVs into `app/src/main/assets/catalog.db`:
table `food(id INTEGER PRIMARY KEY, kind TEXT, barcode TEXT, name_en TEXT, name_ar TEXT, brand TEXT, brand_ar TEXT, kcal REAL,
protein REAL, carbs REAL, serving_g REAL, units TEXT, source TEXT, scans INTEGER)`, index on barcode.
