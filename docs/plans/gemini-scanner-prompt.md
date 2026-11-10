# Prompt for Gemini: improve the ForgeQuest barcode scanner

Paste this first, then paste the whole of `food-template-for-gemini.md` below it.

---

I am making a barcode scanner for my app ForgeQuest (Android, Kotlin, Jetpack Compose, English and Arabic).
The user scans any product, sees the carbs, protein and kcal, and can instantly log it.
The template below describes the current version: the food database record, the lookup order, and the "how much?" bottom sheet.

## What I want from you

1. **Improvements to the current version.** Find what is wrong or clumsy in the data, the lookup and the screen.
2. **Redundancies.** Anything repeated, wasted or unnecessary (data columns, steps, labels, taps).
3. **Where alternatives do it much better.** Other apps, other libraries, other data sources, other approaches that beat what I have. Name them and say why.
4. **Other UI choices** that make the scanner far more comfortable for the user: fewer taps, no keyboard, faster logging.
5. **Combine it with my main "Scan meal" feature.** That feature sends a photo to the Gemini API (the user's own free key) to get the nutrients.
   Could one camera do both, so that it works automatically when it sees a barcode (and falls back to the AI photo when the barcode has no data)?
   Could the same photo be reused so the user never takes a second one?
6. **Discoverability.** Is it unclear if there is no separate barcode button? Would users hesitate to press an "AI" button for a packaged food?
   Compare: two buttons, one button with modes, or no button at all.

## How to think about it

- Consider many possibilities using everything I said above.
- Include **radical or non-traditional approaches**, not only the usual ones. For each one, say what it costs and what could go wrong.
- Rank everything by (benefit to the user) / (work for one developer).

## Constraints

- Free, offline-first, one developer. No paid APIs, no server of mine. Each user may add their own free Gemini key (BYOK).
- The app tracks **calories and protein**; carbs are shown only for context.
- Target users are in the Gulf; Arabic names and Arabic UI matter.
- The barcode must stay local (ML Kit on the phone), so normal scans use no Gemini quota.

## A previous Gemini answer already suggested these (do not just repeat it)

Dual entry or one camera with a mode toggle; keeping the camera frame when a barcode locks and reusing it for Gemini if the barcode is unknown;
a separate `barcode_map` table so "Pepsi 250 ml" and "330 ml" become one searchable food; dropping the `source` column;
multiplier chips instead of the text field; a zero-tap "instant log" mode; a continuous multi-item scan tray;
filling missing Arabic names and pack sizes with a one-time script; UPC-E expansion.
Critique these: which are good, which are over-engineered, which are risky. Then add ideas that are **better or different**.

## Output format

1. A ranked list of the top 10 changes (one line each: change, benefit, effort S/M/L).
2. For the top 3: the exact screen flow in steps, and what data changes.
3. A short "do not do" list (ideas that look good but are not worth it).
4. Keep it concise.

## How to work

Delegate to sub-agents from the Gemini CLI where useful (Opus, Sonnet, Argon or Flash 3.8).
My usage limit is very close, so be careful: do not repeat work, keep every step short, and stop as soon as the answer is good.
