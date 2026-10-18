# ForgeQuest AI — Master Architecture & Engineering Schema (`ARCHITECTURE.md`)

**Package Name:** `com.forgequest.ai`
**Min SDK:** `26` (Android 8.0 - supports `VibrationEffect` & modern `java.time`)
**Target / Compile SDK:** `35`
**Architecture Pattern:** Clean Architecture + MVVM (UI Layer -> Domain UseCases -> Data Repositories -> Room / DataStore / Remote APIs)
**Design System:** Google Fit Dark Specification (`#131314` Carbon Canvas, `#1E1F20` Borderless Cards, `#5CD6C0` Primary Mint Teal, `#8AB4F8` Secondary Periwinkle Blue, `#FDD663` Warm Gold)

---

## 1. Domain Enums & Shared Value Types (`com.forgequest.ai.domain.model`)

```kotlin
package com.forgequest.ai.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class AiModelTier(val modelId: String, val displayName: String, val subtitle: String) {
    GEMINI_3_8_FLASH(
        modelId = listOf("gemini", "3.8", "flash").joinToString("-"),
        displayName = "Fast",
        subtitle = "Quick vision & instant coaching"
    ),
    GEMINI_4_ARGON(
        modelId = listOf("gemini", "4", "argon").joinToString("-"),
        displayName = "Deep",
        subtitle = "Deep reasoning for complex meals & periodization"
    );

    companion object {
        fun fromModelId(id: String): AiModelTier =
            entries.find { it.modelId.equals(id, ignoreCase = true) } ?: GEMINI_3_8_FLASH
    }
}

@Serializable
enum class FoodCategory(val displayName: String) {
    BREAKFAST("Breakfast"),
    LUNCH("Lunch"),
    DINNER("Dinner"),
    SNACKS("Snacks"),
    DRINKS("Drinks")
}

@Serializable
enum class ExerciseCategory(val displayName: String) {
    CALISTHENICS("Calisthenics"),
    WEIGHTED("Weighted"),
    ISOMETRIC("Isometric")
}

@Serializable
enum class MuscleGroup(val displayName: String, val isFrontBody: Boolean) {
    CHEST("Chest", true),
    FRONT_DELTS("Front Delts", true),
    SIDE_DELTS("Side Delts", true),
    BICEPS("Biceps", true),
    FOREARMS("Forearms", true),
    ABS_CORE("Abs & Core", true),
    OBLIQUES("Obliques", true),
    QUADS("Quadriceps", true),
    CALVES("Calves", true),
    UPPER_BACK_TRAPS("Upper Back & Traps", false),
    LATS("Latissimus Dorsi", false),
    REAR_DELTS("Rear Delts", false),
    TRICEPS("Triceps", false),
    LOWER_BACK("Lower Back", false),
    GLUTES("Glutes", false),
    HAMSTRINGS("Hamstrings", false)
}

@Serializable
enum class FlameHealthState(val healthPercent: Int, val label: String, val description: String) {
    INFERNO(100, "Inferno · 100% health", "Workouts on schedule. Blazing at peak intensity."),
    FLICKERING(66, "Flickering · 66% health", "1 missed workout day. Streak counter preserved."),
    DYING_EMBER(33, "Dying ember · 33% health", "2 consecutive missed days. Complete a workout to reignite."),
    EXTINGUISHED(0, "Extinguished · 0% health", "3+ missed days. Streak reset to 0; Rank and XP preserved.")
}

@Serializable
enum class HunterRank(val rankCode: String, val title: String, val minLevel: Int, val maxLevel: Int) {
    E_RANK("E-Rank", "Starter Athlete", 1, 4),
    D_RANK("D-Rank", "Iron Vanguard", 5, 9),
    C_RANK("C-Rank", "Shadow Challenger", 10, 19),
    B_RANK("B-Rank", "Phantom Knight", 20, 34),
    A_RANK("A-Rank", "Sovereign Sentinel", 35, 49),
    S_RANK("S-Rank", "Monarch of Iron", 50, Int.MAX_VALUE);

    companion object {
        fun forLevel(level: Int): HunterRank =
            entries.firstOrNull { level in it.minLevel..it.maxLevel } ?: S_RANK
    }
}

@Serializable
enum class ExerciseAnalyticsMetric(val displayName: String) {
    MAX_REPS("Max Reps"),
    TOTAL_REPS("Total Reps"),
    TOTAL_VOLUME("Total Volume (Weight × Reps)"),
    MAX_HOLD_SECONDS("Max Hold (Seconds)")
}

@Serializable
enum class PortionMultiplier(val label: String, val factor: Float) {
    SMALL("Small 0.75x", 0.75f),
    NORMAL("Normal 1.0x", 1.0f),
    LARGE("Large 1.25x", 1.25f)
}

@Serializable
enum class HiddenFatAddition(val label: String, val extraCalories: Int) {
    LIGHT("Light (+0)", 0),
    NORMAL("Normal (+50)", 50),
    HEAVY("Heavy (+150)", 150)
}
```

---

## 2. Google Fit Dark Design System (`com.forgequest.ai.ui.theme`)

- `BackgroundCanvas`: `#131314` (Google Fit Carbon Matte — used on all screen backgrounds)
- `SurfaceCard`: `#1E1F20` (Google Fit Elevated Card — strictly `0.dp` border, `16.dp` rounded corners)
- `SurfaceContainerHigh`: `#282A2C` (Inner pills, text fields, and unselected segmented buttons)
- `PrimaryTeal`: `#5CD6C0` (Outer Ring [Protein], primary buttons, active workout states)
- `PrimaryTealTrack`: `#1C2E2B` (Muted background track for the Outer Ring and active nav pill)
- `SecondaryBlue`: `#8AB4F8` (Inner Ring [Calories], XP bar, secondary accents)
- `SecondaryBlueTrack`: `#1D2536` (Muted background track for the Inner Ring)
- `WarmGold`: `#FDD663` (Streak Flame, PR badges, and Zero-Guilt Calorie Surplus state when `calories > goal`)
- `MuscleHotCoral`: `#F28B82` (`0–16h` since trained — "Just trained")
- `MuscleWarmAmber`: `#FDD663` (`16–36h` since trained — "Recovering")
- `MuscleReadyTeal`: `#5CD6C0` (`36–48h` since trained — "Almost ready")
- `MuscleRestingBase`: `#282A2C` (`48h+` — "Recovered" neutral fill with `#3C4043` `1.dp` contour and `#131314` `1.2.dp` anatomical separator stroke)
- `TextPrimary`: `#E3E3E3` (Soft Off-White)
- `TextSecondary`: `#9AA0A6` (Muted Cool Grey)

---

## 3. Room SQLite Entities & Schema (`com.forgequest.ai.data.local.entity`)

All entities are `@Serializable` so the entire database can be cleanly exported/imported via `app_backup.json` in Google Drive sync.

1. **`UserProfileEntity` (`tableName = "user_profile"`)**:
   - `id: Int = 1` (PrimaryKey)
   - `totalXp: Long = 0L`, `level: Int = 1`, `rankCode: String = "E-Rank"`
   - `currentWorkoutStreakDays: Int = 0`, `longestWorkoutStreakDays: Int = 0`, `consecutiveMissedWorkoutDays: Int = 0`
   - `flameHealthState: FlameHealthState = FlameHealthState.INFERNO`
   - `lastStreakEvaluationEpochDay: Long? = null`, `lastRestCheckInEpochDay: Long? = null`
   - `currentNutritionStreakDays: Int = 0`, `currentWeightKg: Float = 72.0f`
2. **`WorkoutPlanEntity` (`tableName = "workout_plans"`)**
3. **`PlannedDayEntity` (`tableName = "planned_days"`)**
4. **`PlannedExerciseEntity` (`tableName = "planned_exercises"`)**
5. **`WorkoutSessionEntity` (`tableName = "workout_sessions"`)**
6. **`LoggedSetEntity` (`tableName = "logged_sets"`)**
7. **`MuscleRecoveryEntity` (`tableName = "muscle_recovery"`)**
8. **`VaultFoodItemEntity` (`tableName = "vault_food_items"`)**
9. **`MealLogEntity` (`tableName = "meal_logs"`)**
10. **`BodyWeightLogEntity` (`tableName = "body_weight_logs"`)**
11. **`ChatMessageEntity` (`tableName = "chat_messages"`)**

---

## 4. Zero-Mock Gemini REST Client & Markdown Parser

- **`GeminiApiClient`**:
  - Zero mock/fallback meals or pre-written coach replies in production code.
  - `analyzeFoodPhoto` and `sendCoachMessage` make real HTTPS POST calls to `https://generativelanguage.googleapis.com/v1beta/models/{modelId}:generateContent?key={apiKey}`.
  - Returns `null` when the API key is blank or the network request fails, triggering `ApiKeyRequiredBottomSheet` (*"Connect your Gemini API key in Settings to use AI features"* -> **"Open Settings"**).
- **`MarkdownText` (`com.forgequest.ai.ui.components.MarkdownText`)**:
  - `stripFencedJsonBlocks` removes ```json ... ``` blocks from visible chat bubbles.
  - `parseMarkdownToAnnotatedString` & `FormattedMarkdownText` parse headings, bold spans, inline code, and bullet lists into clean `AnnotatedString` spans so raw Markdown syntax (`**`, `###`, `` ` ``) is never shown.

---

## 5. 5-Tab Bottom Navigation & Screen Architecture

1. **`Home` (`DashboardScreen.kt`)**:
   - Top Greeting + `40.dp` RPG Rank Emblem badge (taps to `Profile`).
   - Direct Floating Concentric Rings (`220.dp` Canvas on `#131314`) with 12 o'clock (`-90f`) `6.dp` solid zero-state dots (`#5CD6C0` outer Protein, `#8AB4F8` inner Calories), legend row, and 3-column stat summary (`Protein left | Cal left | kg` tappable weight modal).
   - `Your workout streak` card (4-stage flame + 7 mini day rings `M T W T F S S`).
   - `Today's workout` card (`48.dp` `#1C2E2B` icon badge + `"Start"` / `"Plan"` button).
   - `Muscle recovery` card (`AnatomicalMuscleMapCanvas` with Front & Back organic Bezier musculature + interactive tap tooltips).
   - `7-day trends` & `30-day trends` cards.
2. **`Workouts` (`WorkoutScreen.kt`)**:
   - Top horizontal progress bar (`#5CD6C0` on `#1C2E2B`), borderless `#1E1F20` exercise cards with `Watch form` (`@HemoCalisthenics` deep-link) and `Swap exercise` sheet, M3 Expressive Morphing Checkboxes, floating rest timer bar, and deferred `Workout complete` modal with `AnatomicalMuscleMapCanvas`.
3. **`Food` (`FoodVaultScreen.kt`)**:
   - `Scan meal` & `Add custom` pills, 5 strict category pills (`Breakfast | Lunch | Dinner | Snacks | Drinks`), pinned-first borderless food cards with editable `48.dp` thumbnails, and collision-free AI Camera Confirmation Sheet (`Small 0.75x | Normal 1.0x | Large 1.25x` and `Light (+0) | Normal (+50) | Heavy (+150)`).
4. **`Coach` (`AiCoachScreen.kt`)**:
   - `Fast` | `Deep` pill selector, borderless `#1E1F20` bubbles with `FormattedMarkdownText`, and 1-tap `Apply workout plan` cards.
5. **`Profile` (`SettingsScreen.kt`)**:
   - Hunter Rank & XP card, Body Weight History & Quick Log card, and 4 Google Fit-style pastel circular icon badge cards (`AI key & engine`, `Hand scale calibration`, `Daily nutrition goals`, `Backup & Google Drive sync`).

---

## 6. Deep Module Architecture & Seams (`improve-codebase-architecture`)

1. **`LiveWorkoutSessionManager` (`com.forgequest.ai.domain.usecase.LiveWorkoutSessionManager`)**:
   - **Depth:** Replaces loose coordinator logic across ViewModel and Repository with a single deep module encapsulating active session lifecycle, automatic set templating from previous sessions, atomic set progression with haptic/rest cues, dynamic exercise swapping, and post-workout deferred PR/streak calculation.
   - **Architectural Seam (`WorkoutSessionStore`):** Decouples the manager from Room's 60-method DAO behind a focused interface (`observeActiveSession`, `observeSetsForSession`, `insertWorkoutSession`, `updateLoggedSet`, `insertLoggedSets`, `executeFinishWorkout`). Tested with 100% pure in-memory test doubles with zero mocking framework dependencies.
   - **Locality:** Deletes redundant boilerplate and concentrates set mutation rules in the domain layer, keeping UI composables and ViewModel callers minimal and testable.

