# Play Store wedge plan (2026-11-10)

This plan comes from the owner's Gemini notes ("why would someone switch from Hevy or Strong?"). Each claim was checked against the Android code. Nothing here is built in the critique run of 2026-11-10 unless marked **in this run**.

## What Gemini got right, wrong or half right

| Claim | Verdict | Evidence |
|---|---|---|
| No exercise visuals or form cues | Half true | No pictures or cues anywhere (`ExerciseDetailScreen.kt`, `ExercisePicker.kt`). A form-video button already exists (`openFormVideo`, `ExercisePicker.kt:61`, YouTube search). **In this run:** slice D2 adds 2 cues and 1 common mistake for every exercise. |
| Muscle map uses `react-native-body-highlighter` | Wrong for Android | `ui/components/BodyMap.kt` draws its own Compose `Canvas` from `assets/body_map.json`. The paths came from that library's SVGs, and the app has no React Native dependency. |
| No calisthenics progressions | Half true | `ExerciseTiers.readyForHarder` / `harderVersion` and the `HarderNote` in the live workout already offer the harder variant. The rule is 15+ reps on 2+ sets in 2 sessions in a row. The chain itself is never shown. **In this run:** slice D3 adds a calisthenics level 2 plan. |
| AI needs "programmer homework" | True today | **In this run:** C1 removes the Drive bridge doc and Groq. C2 moves the Gemini-app route and the API key into Settings > Advanced: AI. |
| No lock-screen live timer | Half true | `Notifier.kt` already posts an ongoing countdown notification. It has no action buttons and private lock-screen visibility. |
| No import from Hevy / Strong | True | Only ForgeQuest's own JSON backup exists. |
| Saudi food data, no "verified" flag | Half true | Saudi dishes and chains are in the catalogue and search tests. No `verified` field exists on catalogue foods (`CatalogFood.kt`, `Entities.kt`). |
| No account, no ads | True | No sign-in before use; no ad SDK in `build.gradle.kts`. This is a selling point to say out loud on the store page. |

## Recommended order after this run

### 1. Import from Strong and Hevy (CSV), large, owner decision
- **Why:** the biggest switching barrier. Lifters will not start again from zero.
- **Shape:**
  - Settings > Backup and sync > "Import from another app".
  - Pick a CSV file and detect Strong or Hevy from the header row.
  - Show a preview ("212 workouts, 41 exercises, 3 not matched").
  - Import.
- **Exercise names:** map to catalogue ids with a name table (EN aliases), plus the existing name guesser. Anything unmatched becomes a `custom_*` exercise. Units: kg/lb.
- **Data:** inserts only (sessions, sets). No Room schema change.
- **Records, XP and badges:** they follow from the imported history.
  - Records: the first imported session of each exercise sets its best.
  - Badges: reuse the E1 backfill (insert with `seen = true`, one summary line instead of 12 pop-ups).
  - XP: it is derived, so the level jumps honestly.
- **Must come after E1.**
- **Test fixtures:** one real Strong export and one real Hevy export. Ask the owner for their own files.

### 2. Rest-timer notification actions, small
- Add "Skip rest" and "+30 s" actions to the existing ongoing notification, and set public lock-screen visibility for that channel.
- A "Tick next set" action is possible: the VM already knows the next set (`PickNextSetTest`). It needs a BroadcastReceiver declared in the manifest. That is a new manifest component, so it needs the owner's OK.
- Wear OS: out of scope.

### 3. Progression chains you can see, medium
- On `ExerciseDetailScreen`, show the chain from `ExerciseTiers` (Incline push-up > Push-up > Diamond > Archer), with the user's current step and the rule to move up ("15 reps on 2 sets, two workouts in a row").
- Keep the existing `HarderNote` prompt.
- Consider making the move-up rule follow the top of the plan's rep range (e.g. 4 x 12) instead of a fixed 15.

### 4. "Verified" Saudi foods, medium, data work
- Add a `verified` flag in the food catalogue build (`tools/fooddb`), not in Room, and a small check badge in search results.
- Hand-check the top 200 foods Saudi lifters log:
  - Almarai and Nada high-protein dairy
  - Albaik, Kudu, Herfy, Maestro, Shawarma House and other chain items
  - mandi and kabsa portions, with and without skin
- Needs sources per item (chain nutrition PDFs, SFDA labels). Gemini's numbers are not a source.

### 5. Exercise pictures, large, owner decision
- Choose between start and end line drawings for 130+ exercises (licensed or commissioned) and a highlighted target-muscle silhouette from the existing `BodyMap` (cheap, can ship first).
- **Recommendation:** ship the muscle silhouette on `ExerciseDetailScreen` now, and decide on drawings later.

### 6. Store page wording, small
- Say plainly:
  - one app for workouts and protein
  - no account, no ads
  - your data stays on your phone and your Google Drive
  - full Arabic
  - Saudi foods
- Screenshots in both languages.

## Not recommended
- Retroactive XP as a headline feature. Badges and levels from imported history are fine, but the import has to stand on keeping records and history, not on game points.
- A social feed. The no-feed choice is part of the pitch.

## Questions for the owner
1. Build the Strong/Hevy importer next? Can you share your own export files as test data?
2. OK to add a manifest receiver for a "Tick set" action in the notification?
3. Exercise pictures: muscle silhouette only for now, or budget for drawings?
