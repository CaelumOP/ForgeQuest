# Food library: restaurants, forgiving search, items with parts

Goal: Gemini is only needed the first time you eat something. After that you log from your own list like a shopping
cart: search a food or a restaurant, tick what you had, set the amount (half a can), log.

Status: built in 2.13.0 (Android and web), approved 2026-10-26. Refinement while building: a ticked search result logs under the open tab (which opens on the meal for the time of day); foods ticked in a tab keep that tab; drinks always Drinks. Applies to Android (`app/`) and web (`web/`), English and
formal Arabic.

## Decisions so far (2026-10-26)
- A saved food is one item with removable parts (Chicken shawarma: bread, chicken, garlic sauce, pickles, ketchup).
  Drinks and simple foods have no parts.
- The five Food tabs stay: Breakfast, Lunch, Dinner, Snacks, Drinks.
- Search is forgiving like Google, but only over the person's own saved foods (nothing from outside).
- A new restaurant name that matches an existing one is suggested ("Same as ...?"), never merged silently.
- The cart, amounts (½ step, 0.25 to 20 typed) and the Log button already exist and are kept.

## Already in both apps (no work)
- Tick saved foods, amount per food, one "Log N items" button (`FoodScreen.kt` selection, `web/src/ui/food/FoodScreen.tsx`).
- The Gemini review sheet lets you untick items and change grams before logging.

## Step 1: restaurants and search

1. Data (both apps, one change for both steps)
   - Saved foods get two optional fields: `place` (restaurant name) and `parts` (list of name, grams, kcal, protein,
     carbs, on/off; used in step 2).
   - Android: Room version 7 to 8, a migration that adds the two columns. Web: no Dexie version change (not indexed).
   - Backups: both formats carry the new fields. An older app restoring a new backup ignores them; totals stay right.
     Old backups restore with no restaurant and no parts.
2. Search rules (pure code, same in both apps, one shared table of test cases both test suites read)
   - Clean-up: case, tashkeel and ـ, أ/إ/آ = ا, ة = ه, ى = ي, ؤ/ئ, Arabic digits, apostrophes, leading "ال".
   - Sound match across Arabic and English with vowel letters dropped: كفتيريا = كافتيرا = cafeteria, شاورما = shawarma,
     برجر = burger, كبسة = kabsa, لاتيه = latte.
   - Typos: one wrong letter in short words, two in long ones, swapped letters.
   - Search inside joined words (كولا finds كوكاكولا), and words typed apart or together (كوكا كولا = كوكاكولا).
   - Every typed word must match the food's name or its restaurant.
   - Order: best match first, then what you log most often, pinned foods on top.
3. Food tab
   - A search box above the tabs. While typing, results come from all tabs and work like the cart (tick, amount, Log).
     Empty box: the tabs as now.
   - Recent restaurants as one-tap chips under the box; a tap shows that restaurant's foods.
4. Food editor: a "Restaurant (optional)" field that suggests your restaurants as you type, and shows
   "Same as شاورما افراح وتهاني?" when the name matches one you have (venue words like كفتيريا، مطعم، شاورما don't count).
5. Meal type from the time you eat: cart items logged today go under Breakfast/Lunch/Dinner/Snacks by the clock, like the
   scan already does. Drinks stay Drinks. Logging for another day keeps the food's own tab, since the time is unknown.
6. No duplicate copies: saving a meal (Gemini, scan or by hand) with the same name and the same restaurant updates the
   saved food instead of adding another.

## Step 2: items with parts, Gemini fills the library

1. Saving a Gemini or scanned meal saves one food with its parts; parts you unticked are kept, switched off.
2. The Gem can be told the restaurant ("from افراح وتهاني"): its JSON gets an optional `place`, filled into the
   restaurant field. Prompt updated in `docs/prompts/food-gem.md` and the web copy.
3. Cart: a food with parts shows "4 of 5 parts" under its name; tap to tick parts on or off. Totals = ticked parts x
   amount. Your choice is remembered for next time.
4. The day log shows what was left out, e.g. "Chicken shawarma, no ketchup".

## Checks before each step ships
- Unit tests: the shared search table, the migration, backup round trips Android to web and back, old backups.
- Real Android emulator screenshots and the iPhone Simulator run, English and Arabic.
- Release: Android version bump and `version.json` in the PR; web deploys on merge.

## Risks
- Gemini's first estimate is reused every time: editing a food stays one tap away from the cart.
- Very short words match loosely; harmless with a personal list and usage ordering.
- Two restaurants sharing a name word (كافتيريا الريم, مطعم الريم) look the same: that is why it's a suggestion.
