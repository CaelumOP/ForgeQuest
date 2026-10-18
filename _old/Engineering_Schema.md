* **Complete Decision Record:** This engineering specification synthesizes every architectural, UI, gamification, and multi-agent workflow decision reached across our planning session for **ForgeQuest AI**.


* **Multi-Agent Execution Model:** Designed specifically for **Google Antigravity**, where `gemini-4-argon` acts as the Lead Principal Orchestrator (`ARCHITECTURE.md`, Room SQLite schemas, Gradle build verification, and `report.md` generation) while delegating Jetpack Compose UI/shaders to a `Claude` sub-agent and data/vision/sync pipelines to a specialized data sub-agent.
* **Core Feature Pillars Locked:** Covers the encrypted `datastore-tink` API key store, In-App AI Coach Chat, Two-Way Google Drive `WorkManager` bridge, Strict Categorized Visual Food Vault with editable photos and multi-select checklists, Hand-Scaled AI Food Camera (`gemini-3.8-flash` / `gemini-4-argon`), and the Material 3 Expressive × RPG Dopamine Engine.


* **Verification & Output:** Establishes concrete unit test seams (`EvaluateStreakDecayUseCase`, `FinishWorkoutUseCase`, `GenerateProgressSummaryMarkdownUseCase`) and requires both a compiled, installable `.apk` and a root-level `report.md` diagnostic report.

---

# Engineering Specification: ForgeQuest AI (Native Android Workout & Nutrition System)

## 1. Problem Statement

Standard fitness and nutrition tracking applications suffer from high user drop-off due to three compounding failure modes:

1. **High Logging Friction:** Manual gram weighing for unfamiliar meals and repetitive daily entry for staple foods (when only a subset of a usual meal is eaten) make food tracking tedious.
2. **Psychological Demotivation ("Dopamine Backfire"):** Fragile daily streak counters reset to zero on rest days or single missed workouts, while exceeding daily calorie targets triggers punitive red failure screens that discourage honest logging.
3. **Disconnected AI Coaching:** Chatting with an AI to adjust workout plans requires manual copy-pasting of workout histories and schedules between the chat interface and the tracker app.

The user requires a personal, native Android application that automates meal estimation via hand-scaled AI vision, provides a customizable multi-select visual food vault, synchronizes workout plans and progress bi-directionally with Gemini via Google Drive and an in-app chat, and maximizes long-term retention through a Material 3 Expressive × RPG gamified interface with rich hardware haptics.

---

## 2. Solution

**ForgeQuest AI** is a native Android application built in **Google Antigravity** (Kotlin, Jetpack Compose, Room SQLite, WorkManager, CameraX, Jetpack DataStore + Google Tink) orchestrated by `gemini-4-argon` with specialized sub-agents (`Claude` for UI/shaders and `gemini-3.8-flash` / `gemini-4-argon` for data/integrations).

Key capabilities include:

* **Material 3 Expressive × RPG System HUD:** Combines bouncy spring physics and shape-morphing controls with an RPG Hunter Rank (`E-Rank` to `S-Rank`) and XP leveling system.


* **4-Stage "Ember-to-Inferno" Workout Streak Engine:** Tracks weekly workout consistency, automatically preserves streaks on scheduled rest days without user input, and weakens the flame across missed workout days (`Inferno 100%` -> `Flickering 66%` -> `Dying Ember 33%` -> `Extinguished 0%`) rather than resetting overnight.
* **Distraction-Free Live Tracker + Deferred "Quest Clear" Payout:** Provides a top session progress bar, auto-rest countdown timers, on-the-fly exercise swaps, and `@HemoCalisthenics` YouTube form deep-links during the workout, while deferring all PR celebrations, XP tallies, confetti bursts, and the 48-hour glowing muscle heatmap until the user taps **Submit Workout**.
* **Strict Categorized Visual Food Vault & Hand-Scaled Camera:** Pairs 5 strict meal category tabs (`Breakfast`, `Lunch`, `Dinner`, `Snacks`, `Drinks`) featuring pinned, photo-editable multi-select food cards with a zero-typing AI camera (`gemini-3.8-flash` default / `gemini-4-argon` toggle) calibrated against the user's flat hand on the table.


* **In-App AI Coach & Two-Way Google Drive Sync:** Uses an encrypted on-device Gemini API key to power an in-app coach chat (capable of mutating the local workout plan in one tap) while silently syncing `app_backup.json` and a `<800-token` `gemini_progress_summary.md` with Google Drive and auto-importing exported `Workout Plan` Google Docs.



---

## 3. User Stories

### Nutrition & Visual Food Vault

1. **As a user logging a meal via camera**, I want to place my hand flat on the table next to my plate and snap a single photo with zero typing, so that `gemini-3.8-flash` or `gemini-4-argon` uses my saved hand dimensions to calculate food volume, calories, and protein accurately.


2. **As a user reviewing an AI food scan**, I want a 1-tap confirmation sheet with portion size toggles (`0.75x`, `1.0x`, `1.25x`), cooking fat/sauce toggles (`Light`, `Normal`, `Heavy +150 kcal`), and item checkboxes, so I can correct hidden calories or uncheck uneaten food without typing.
3. **As a user organizing my favorite foods**, I want to save any scanned item into a strict category (`Breakfast`, `Lunch`, `Dinner`, `Snacks`, `Drinks`), pin it to the top of that category, and replace or crop its photo from my gallery or camera, so my daily menu looks clean and personalized.


4. **As a user eating a partial staple meal**, I want each strict category tab to work as a multi-select checklist with `+ / -` quantity steppers (e.g., checking off `Eggs` and `Coffee` while leaving `Paratha` unchecked), so I can log flexible combinations in one tap.
5. **As an honest nutrition logger**, I want the calorie progress ring to transition into a warm gold **"Fuelled / Surplus"** state rather than red when I exceed my daily calorie target, and keep nutrition streaks disabled by default, so I never experience logging guilt.

### Workouts, RPG Dopamine & Streak Mechanics

6. **As a calisthenics and gym trainee**, I want a live workout screen with a top session progress bar, morphing set checkboxes, an automatic rest countdown timer, and a 1-tap `@HemoCalisthenics` YouTube deep-link on every exercise card, so I can execute my routine with proper form and pacing.
7. **As an adaptable trainee**, I want a mid-workout **"Swap / Add Exercise"** drawer, so I can substitute bodyweight exercises for gym machines (such as a Smith machine or cable crossovers) on the fly without breaking my saved plan.
8. **As a focused athlete**, I want live set entries that beat my previous records to log silently during the workout and trigger all PR cards, XP level-ups, particle confetti, and the 48-hour anatomical muscle heatmap only when I tap **Submit Workout**, so my focus is never broken between sets.
9. **As a user building a long-term habit**, I want my workout streak flame to pass scheduled rest days automatically without opening the app, provide a manual "Rest Day Check-In" button for unplanned recovery, and step down gradually (`100% -> 66% -> 33% -> 0%`) across missed workout days, so a busy schedule never causes an unfair reset.


10. **As a tactile mobile user**, I want every button stepper, set completion, PR reveal, and streak reignition to fire distinct hardware haptic primitives (`VibrationEffect.Composition`) with zero audio sound effects, so the app feels physically responsive in total silence.

### Analytics, AI Coaching & Cloud Sync

11. **As a data-driven lifter**, I want each exercise's progression chart to default to **Max Reps** on the Y-axis with a selector pill for `Total Reps`, `Total Volume (Weight × Reps)`, and `Max Hold (Seconds)`, and I want `[7-Day]` and `[30-Day]` macro/weight trend buttons on the dashboard instead of cluttered permanent charts.
12. **As an in-app AI user**, I want a Settings screen to store my Gemini API key encrypted on-device and an **AI Coach** tab that automatically injects my compact progress summary and renders a 1-tap **"Apply Plan to My Schedule"** button whenever we design a new routine.


13. **As a cross-platform Gemini user**, I want the app to automatically import new `Workout Plan` Google Docs from my Google Drive and automatically upload `app_backup.json` and `gemini_progress_summary.md` (<800 tokens) after every workout or meal, so my data is always backed up and readable in web/mobile Gemini chat.

---

## 4. Implementation Decisions

### 4.1 Multi-Agent Build Topology (Google Antigravity)

* **Lead Orchestrator (`gemini-4-argon`):**
* Creates `ARCHITECTURE.md` first to lock all entity definitions, DAO contracts, UseCase signatures, and navigation routes.
* Manages Gradle dependencies (`compileSdk = 35`, Kotlin 2.x, Jetpack Compose BOM, Material 3 Expressive, Room KSP, WorkManager, CameraX, Google Tink, Ktor/OkHttp, Google Play Services Auth & Drive REST API v3).
* Runs unit tests, executes `./gradlew assembleDebug` / release APK packaging, resolves any cross-agent integration bugs, and writes `report.md`.


* **Sub-Agent A — UI, Shaders & Haptics (`Claude`):**
* Implements the OLED Dark theme (`#0B0F19` base, `#131B2E` surface, `#00F2FE` cyan, `#10B981` emerald, `#F59E0B` warm gold, `#FF3366` PR pink).


* Implements all Jetpack Compose screens, `Spring.DampingRatioMediumBouncy` morphing set rows, the 4-stage Streak Flame Canvas animation, the 2D Front/Back 48-Hour Muscle Heatmap Canvas, and the silent `HapticsManager`.


* **Sub-Agent B — Data, Vision, Chat & Sync (`gemini-4-argon` / `gemini-3.8-flash`):**
* Implements Room entities/DAOs, `datastore-tink` encrypted preferences, CameraX capture + Gemini multimodal API calls, In-App Coach Chat repository, and `WorkManager` Google Drive two-way sync workers.



### 4.2 Local Persistence Schema (Room SQLite)

* `WorkoutPlanEntity(planId, title, sourceDocId, updatedAt, isActive)`
* `PlannedDayEntity(dayId, planId, dayOfWeek, title, isRestDay: Boolean)`
* `PlannedExerciseEntity(plannedExerciseId, dayId, exerciseId, orderIndex, targetSets, targetReps, targetHoldSeconds, restDurationSeconds)`
* `ExerciseCatalogEntity(exerciseId, name, trackingType: Enum[CALISTHENICS_REPS, WEIGHTED_VOLUME, ISOMETRIC_SECONDS], primaryMusclesCsv, secondaryMusclesCsv, videoUrl: String?, videoSearchQuery: String)`
* `WorkoutSessionEntity(sessionId, planId, dayId, startedAt, completedAt, isUnplannedRestCheckIn: Boolean, xpEarned: Int)`
* `SetLogEntity(setId, sessionId, exerciseId, setNumber, repsCompleted, weightKg, holdSeconds, isPrMaxReps: Boolean, isPrVolume: Boolean)`
* `FoodVaultItemEntity(itemId, name, category: Enum[BREAKFAST, LUNCH, DINNER, SNACKS, DRINKS], caloriesKcal: Int, proteinGrams: Float, imageUri: String?, isPinned: Boolean, createdAt)`
* `MealLogEntity(mealLogId, loggedAt, category, itemName, multiplier: Float, totalCalories: Int, totalProteinGrams: Float, imageUri: String?, usedAiModel: String?)`
* `UserRpgStateEntity(id = 1, totalXp: Long, currentLevel: Int, rankTier: Enum[E, D, C, B, A, S], workoutStreakCount: Int, flameHealthState: Int, lastStreakEvalDate: String, bodyWeightKg: Float)`

### 4.3 Encrypted Settings & API Contract (`datastore-tink`)

* Stored in `EncryptedSettingsDataStore` using Tink `Aead` encryption backed by Android `MasterKey` (`android-keystore://forgequest_master_key`), with `android:allowBackup="false"` on the keystore preferences file:
* `geminiApiKey: String`
* `preferredModel: String` (`"gemini-3.8-flash"` default, `"gemini-4-argon"` toggle)
* `handLengthCm: Float` (default `18.5f`)
* `palmWidthCm: Float` (default `8.5f`)
* `dailyCalorieGoal: Int` (default `2100`)
* `dailyProteinGoalGrams: Int` (default `140`)
* `enableNutritionStreak: Boolean` (default `false`)



### 4.4 Core Domain Algorithms & Contracts

* **Streak Decay & Reignition (`EvaluateStreakDecayUseCase`):**
* Runs on app launch and midnight `WorkManager` tick for each elapsed calendar day since `lastStreakEvalDate`:
* If `PlannedDayEntity.isRestDay == true` OR a `WorkoutSessionEntity` (workout or manual rest check-in) exists on that date: `flameHealthState` remains unchanged; if a workout was completed, `workoutStreakCount += 1` and `flameHealthState = 100`.
* If a scheduled workout day (`isRestDay == false`) was missed with no check-in:
* 1st consecutive miss: `flameHealthState` steps down `100 -> 66` (`FLICKERING`), `workoutStreakCount` is preserved.
* 2nd consecutive miss: `flameHealthState` steps down `66 -> 33` (`DYING_EMBER`), `workoutStreakCount` is preserved.
* 3rd+ consecutive miss: `flameHealthState` steps down `33 -> 0` (`EXTINGUISHED`), `workoutStreakCount = 0` (`totalXp`, `currentLevel`, and `rankTier` are never decremented).






* **48-Hour Muscle Heatmap Shader Math:**
* For each `MuscleGroup` enum (`CHEST`, `LATS`, `UPPER_BACK`, `ANTERIOR_DELTS`, `LATERAL_DELTS`, `BICEPS`, `TRICEPS`, `FOREARMS`, `ABS_CORE`, `QUADS`, `HAMSTRINGS`, `GLUTES`, `CALVES`), query the latest `completedAt` timestamp where the muscle was trained:

$$\text{intensity} = \max\left(0.0, 1.0 - \frac{\text{hoursSinceTrained}}{48.0}\right) \times \text{roleWeight}$$



where $\text{roleWeight} = 1.0$ for primary muscles and $0.5$ for secondary muscles.


* **Hand-Scaled AI Vision Payload (`AnalyzeFoodPhotoUseCase`):**
* Sends captured JPEG + structured JSON schema request to `[https://generativelanguage.googleapis.com/v1beta/models/](https://generativelanguage.googleapis.com/v1beta/models/){preferredModel}:generateContent`.
* System prompt instructs the model to identify mystery dishes without user text, detect if a flat hand is next to the plate, calibrate pixel-to-centimeter scale using `handLengthCm` and `palmWidthCm`, and return `dishName`, `handScaleDetected`, and `detectedItems[{name, estimatedGrams, calories, proteinGrams}]`.




* **Two-Way Google Drive Sync (`DriveSyncWorker`):**
* **Inbound:** Lists files matching `name contains 'Workout Plan' and mimeType = 'application/vnd.google-apps.document' and trashed = false` ordered by `modifiedTime desc`. Exports text and parses JSON/Markdown plan blocks into Room.
* **Outbound:** Writes `app_backup.json` and `gemini_progress_summary.md` (<800 tokens) to `/WorkoutApp/` on Google Drive after every workout completion or meal log.



---

## 5. Testing Decisions

* **Primary Test Seam:** Domain UseCases and ViewModels tested via JUnit 4 + Kotlin Coroutines `runTest` using in-memory Room databases and fake Drive/Gemini gateways.
* **Required Test Cases:**
1. `EvaluateStreakDecayUseCaseTest`: Verifies scheduled rest days auto-preserve `100%` flame health with zero user action; verifies 1 and 2 missed workout days step flame health down to `66%` and `33%` without losing the streak count; verifies completing a workout at `33%` restores `100%` flame health; verifies 3 consecutive missed workout days reset streak count to `0` while preserving RPG XP and Rank.
2. `FinishWorkoutUseCaseTest`: Verifies live set updates do not emit PR UI events mid-workout; verifies tapping **Submit Workout** accurately calculates Max Reps PRs, Total Volume PRs, XP deltas, Rank promotions, and 48-hour muscle heatmap intensities.
3. `FoodVaultCalculationTest`: Verifies multi-select checklist totals, quantity steppers (`0.5x`, `1x`, `2x`), portion multipliers (`0.75x`, `1.0x`, `1.25x`), and heavy oil/sauce (`+150 kcal`) adjustments compute exact calorie and protein totals, and verifies over-target calories emit the `FUELLED_GOLD` UI state rather than an error state.
4. `GenerateProgressSummaryMarkdownUseCaseTest`: Verifies the generated `gemini_progress_summary.md` string includes active plan, 14-day macros, and exercise PRs while remaining strictly under 800 tokens (~3,200 characters).



---

## 6. Out of Scope

* **Audio / Sound Effects:** Zero MP3/WAV assets, chimes, or `MediaPlayer` instances—feedback is strictly visual and hardware-haptic (`VibrationEffect.Composition`).
* **Punitive Calorie UI & Mandatory Nutrition Streaks:** No red warning banners when exceeding daily calorie goals, and nutrition streaks remain disabled by default.
* **External Platforms & Social Features:** No iOS/Web client, no public leaderboards or social feeds, and no smartwatch/wearable sensor Bluetooth integrations.

---

## 7. Further Notes and Launch Block

* **Diagnostic Deliverable (`report.md`):** The Lead Orchestrator (`gemini-4-argon`) must generate `report.md` in the root directory upon completion, documenting what succeeded on the first pass, what Gradle/Room/CameraX/UI challenges occurred and how they were fixed, unit test execution logs, and the exact path to the compiled `.apk`.
* **Visual Reference:** Align dashboard hierarchy, RPG rank header, streak flame, macro rings, food vault tabs, and hand-scale camera overlay with the generated concept mockup (`image_0.png`).



### Launch Block

* **Status:** Ready for implementation
* **Baseline:** Greenfield Native Android Project (`ForgeQuest AI`, Kotlin + Jetpack Compose, `compileSdk = 35`)
* **Test Seam:** Domain UseCase & ViewModel state layer (`EvaluateStreakDecayUseCase`, `FinishWorkoutUseCase`, `FoodVaultViewModel`, `DriveSyncWorker`)
* **Non-goals:** Audio/sound effects, red over-calorie warnings, mandatory nutrition streaks, iOS/web targets, wearable sync
* **Next Route:** Single-shot multi-agent execution in Google Antigravity (`gemini-4-argon` Orchestrator + `Claude` UI Sub-Agent + Data/Vision Sub-Agent) producing an installable `.apk` and `report.md`