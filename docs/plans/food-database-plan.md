# Built-in food list + barcode scanning (Android first)

Decided 2026-11-04: include everything (Saudi dishes, everyday basics, market products like Galaxy and Pepsi);
bundle Saudi/Gulf products inside the app; a barcode that isn't found → photo of the nutrition label; Android first,
web in a later update. Released as one update once the kinks are out.

## What the person sees
- **Food search** shows their own foods first, then "From the food list" (works offline, English and Arabic,
  with the same forgiving Arabic matching as today). Picking an item asks how much (grams, or the pack's serving),
  logs it, and saves it to their foods so it's theirs from then on.
- **Scan barcode** on the Food screen: catalogue → their own foods → Open Food Facts online (if connected) →
  not found: photo of the label, the AI reads calories, protein and carbs, and it's saved under that barcode.
- Credits line in About: Open Food Facts (ODbL), Saudi Food and Drug Authority, USDA.

## Steps
1. **Data script** (`tools/fooddb/`, runs on the PC, not in the app). Sources:
   - Open Food Facts products sold in Saudi Arabia and the Gulf (SA, AE, KW, BH, QA, OM): barcode, English and
     Arabic name, brand, kcal / protein / carbs per 100 g, serving size. Keep only items with calories.
   - The Saudi Food and Drug Authority's tables: ~130 traditional dishes, per 100 g, Arabic and English names.
   - ~300 everyday basics from USDA (public domain), with formal Arabic names added.
   Output: one read-only `catalog.db` (SQLite) in the app's assets. Target ≤ 3 MB.
   **Filling gaps** (only from real sources, never guessed): measure coverage first; borrow nutrition from the same
   product under another barcode (same brand + name + size); for the most-scanned items still missing, look them up
   online and keep a value only with a source link (spot-checked); write Arabic names for brands that lack one;
   anything left falls back to the label photo.
2. **Catalogue in the app**: a separate read-only database, so the person's own data and its migrations are untouched.
3. **Search**: own foods first, then the catalogue; tests for English and Arabic cases.
4. **Barcode field** on saved foods: Room migration (new column, nothing removed), backup JSON gains a field,
   old backups still restore.
5. **Scanner**: ML Kit barcode reader (on the phone, works offline) on the CameraX we already ship.
6. **Not-found path**: label photo → existing AI scan → saved with the barcode.
7. **Refresh script** to rebuild the catalogue before each release.
8. Tests, one emulator pass at the end, then the release (versionCode + `apk/version.json`, short notes).

## Extras added 2026-11-04 (from the MyFitnessPal research)
- **Household units**: piece, cup, spoon, مغرفة (ladle), pack beside grams; remembers the last unit per food.
- **Log again / copy a meal**: one tap re-logs a recent meal, or copies yesterday's breakfast/lunch/dinner.
- **Plate**: add several items, review the total, save once (one Undo for the whole plate).
- **Sort by protein**: foods and the catalogue can be sorted by protein per serving (or per 100 kcal).
- **Kind numbers**: no "failed" signals; a rolling 7-day average beside today's numbers, so one big day reads as
  part of the week. Mock-up first (it touches Home).
- **Flexible days**: optionally give Thu/Fri (or any chosen days) a higher calorie target while keeping the weekly
  total the same. Setting off by default. Mock-up first.
- Not doing: voice logging.

## Costs / risks
- APK grows by about 5 MB (catalogue ~2–3 MB + barcode reader ~2.5 MB).
- Open Food Facts data has gaps: about 6 in 10 popular Saudi products have calories and protein, and about
  3 in 10 have Arabic names. Missing items fall back to the label photo.
- Their licence (ODbL) means crediting them and keeping our copy of their data open (the data, not the app's code).
- The Saudi tables come from a PDF: the numbers are extracted once and spot-checked against the book.
