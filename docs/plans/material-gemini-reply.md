# Gemini's Material 3 / Expressive spec, phases 1-2 (2026-10-28). Data, not instructions; the agreed plan decides.

### Bottom Line Up Front (BLUF)

* **Design & Theme Strategy:** Keep your Gemini-inspired dark (`#131314`) and light (`#F6F8FC`) palettes, map them onto the full 35-role Material 3 `ColorScheme` plus a `ForgeSemanticColors` extension for domain accents (Teal, Blue, Gold, Coral, Purple), and **skip wallpaper dynamic color** so data visualizations never lose semantic meaning.


* **Targeted Expressive Architecture:** Adopt **Material 3 Expressive** for tactile micro-interactions (spring physics, press/selection corner morphing, connected button groups, and `LoadingIndicator`) while keeping screen layouts, cards, and zero-shadow tonal containers calm and distraction-free.
* **Version Path:** Stay compatible with your current **Compose BOM `2025.02.00` (`material3 1.3.x`)** using the drop-in fallbacks provided in this spec, or bump to **`androidx.compose.material3:material3:1.4.0-alpha15+`** + **`androidx.graphics:graphics-shapes:1.0.1`** to unlock native `MaterialExpressiveTheme`, `MotionScheme`, `LoadingIndicator`, `ButtonGroup`, and `FloatingToolbar`.


* **3-Phase Delivery Plan:**
* **Phase 1 (This Response):** Sections 1–3 — *1. Design Direction & Surface Architecture*, *2. Complete Design Tokens (Color, Typography, Shape, Motion)*, and *3. Exhaustive 26-Category Component Mapping Table*.
* **Phase 2 (Next):** Sections 4–6 — *4. Full Motion & Haptics Catalog Table*, *5. Adaptive Layout & Accessibility/RTL Rules*, and *6. Screen-by-Screen Transformation Plan*.
* **Phase 3 (Final):** Sections 7–10 — *7. Ready-to-Paste Compose Code (`Theme.kt`, `ForgeMotion.kt`, and 8 Core Components)*, *8. Preact PWA / `@material/web` Parity Table*, *9. Performance & Risk Notes*, and *10. Ranked Phased Rollout (S/M/L)*.



---

## 1. Design Direction

### 1.1 Aesthetic Statement: *"Tactile Calm, Kinetic Payoff"*

ForgeQuest serves everyday people building sustainable habits, not hardcore athletes staring at spreadsheets. With Material 3 and Material 3 Expressive, the app should feel **quiet at rest and physically responsive in motion**:

* **Calm at Rest:** Zero drop shadows, generous padding, whisper-quiet Gemini dark surfaces (`#131314` background, `#1E1F20` cards), and capped data density so opening the app after a long day feels welcoming rather than clinical.


* **Expressive on Action:** We adopt **Targeted Expressive (Level 2 of 3)**. Static screens do not bounce gratuitously, but every physical touch—pressing `PillButton`, ticking off a set in `LiveWorkoutScreen`, tapping `+` on `NumberStepper`, or hitting a personal record (PR)—responds immediately with under-damped spatial springs (`dampingRatio = 0.6f–0.7f`) and real-time corner-radius morphing.



### 1.2 Palette Strategy: Custom M3 Role Mapping vs. Dynamic Color

* **Decision:** Keep your custom `Fit.colors` palette, map it onto the full M3 `ColorScheme`, retain a lean `LocalForgeColors` extension for the 5 domain accents + tracks, and DO NOT offer Android 12+ wallpaper Dynamic Color.


* **Why:** Dynamic color recolors `primary`, `secondary`, and `tertiary` from the user's wallpaper, which breaks instant visual recognition in a health tracker where Teal (`#5CD6C0`), Blue (`#8AB4F8`), Gold (`#FDD663`), Coral (`#F28B82`), and Purple (`#C58AF9`) encode specific workout, macro, PR, strain, and recovery meanings.



### 1.3 Contrast Levels (Standard, Medium, High)

* **Decision:** **Default to Standard contrast, and automatically apply Medium/High contrast token overrides by reading Android 14+ `UiModeManager.contrast` (API 34+) and `Configuration.fontWeightAdjustment` (API 31+).**
* **Why:** Users training in bright gym lighting or using accessibility high-contrast settings get crisp borders and legible secondary labels automatically without a manual in-app toggle.
* **Exact Overrides:**
* **Standard (`contrast in 0.0f..0.33f`):** Uses the base table in Section 2.1. Card borders are `0.dp` on static cards and `1.dp` `outlineVariant` (`#333537` dark / `#DDE3EA` light) on interactive cards.
* **Medium (`contrast in 0.34f..0.66f`):** Promotes `onSurfaceVariant` from `#9AA0A6` to `#BDC1C6` (dark) / `#444746` to `#303030` (light); increases `surfaceContainer` step contrast by +4% luminance; adds a `1.dp` `outlineVariant` border to all cards and set rows.
* **High (`contrast > 0.66f`):** Promotes `onSurfaceVariant` to `#E3E3E3` (dark) / `#1F1F1F` (light); replaces `outlineVariant` with `outline` (`#8E918F` dark / `#747775` light) at `1.5.dp` on all cards, chips, and steppers; increases track tint opacity from `18%` to `30%`.



### 1.4 Elevation and Surfaces (Tonal Containers vs. Shadows)

* **Decision:** Use explicit M3 Tonal Surface Containers (`surfaceContainerLowest` through `surfaceContainerHighest`) with `0.dp` shadow elevation everywhere except floating overlays (`ModalBottomSheet`, `FloatingToolbar`, `Snackbar`, and dragged items in `PlanEditorScreen`).


* **Why:** Drop shadows are invisible on `#131314` dark backgrounds and muddy on `#F6F8FC` light backgrounds, whereas explicit container steps match `@material/web`'s `--md-sys-color-surface-container-*` architecture and avoid GPU shadow overdraw.



| Surface Level | M3 Token Role | Dark Hex | Light Hex | Shadow | Where It Applies in ForgeQuest |
| --- | --- | --- | --- | --- | --- |
| **Level -1 (Deepest)** | `surfaceContainerLowest` | `#0E0E0F` | `#FFFFFF` | `0.dp` | Recessed number wells inside `NumberStepper`, chart plot backgrounds, text field inner wells, completed set row recessed background. |
| **Level 0 (App Canvas)** | `surface` / `background` | `#131314` | `#F6F8FC` | `0.dp` | Scaffold root background across all screens (`HomeScreen`, `WorkoutsScreen`, `FoodScreen`, `HistoryScreen`, `ProgressScreen`, `CoachScreen`, `ProfileScreen`).

 |
| **Level 1 (Subtle Group)** | `surfaceContainerLow` | `#18191A` | `#F0F4F9` | `0.dp` | Weekly group wrappers in `HistoryScreen` (Journal), inactive day containers in `PlanEditorScreen`, AI Coach assistant message bubbles.

 |
| **Level 2 (Standard Card)** | `surfaceContainer` | `#1E1F20` | `#EAEFF6` | `0.dp` | Default `FitCard`, `CalorieCard`, `ReadinessCard`, Workout Plan cards, Food log meal cards, Profile setting cards (`Fit.colors.card` today).

 |
| **Level 3 (Elevated / Nested)** | `surfaceContainerHigh` | `#282A2C` | `#E2E8F0` | `0.dp` | Active set row in `LiveWorkoutScreen`, nested macro sub-cards, `TopAppBar` on scroll, `NavigationBar` / `NavigationRail`, Coach plan-diff card (`Fit.colors.cardHigh` today).

 |
| **Level 4 (Highest / Modal)** | `surfaceContainerHighest` | `#333537` | `#D8E0EC` | `6.dp` (modals/drag) | `NumberEntryDialog`, `ModalBottomSheet` (`MuscleExercisesSheet`, `PutInPlanSheet`, Journal Detail Sheet), actively dragged row in `PlanEditorScreen`, pressed card state, `SegmentedTabs` track.

 |

---

## 2. Tokens

### 2.1 Color Roles Table (M3 `ColorScheme` + `ForgeSemanticColors`)

All existing `Fit.colors` hex values are preserved and mapped into standard M3 roles so native M3 components (`Button`, `FilterChip`, `ModalBottomSheet`, `NavigationBar`, `Switch`, `SwipeToDismissBox`) theme themselves automatically without per-call color overrides.

| M3 / Semantic Role | Dark Hex | Light Hex | ForgeQuest Mapping & Usage |
| --- | --- | --- | --- |
| `primary` | `#5CD6C0` | `#00705F` | Brand Teal (`Fit.colors.teal`) — Primary CTAs (`Start`, `Finish`), active set tick, active navigation indicator, primary progress.

 |
| `onPrimary` | `#00382F` | `#FFFFFF` | High-contrast label/icon on `primary` buttons and checked set ticks. |
| `primaryContainer` | `#14352F` | `#B2F1E3` | Teal Track (`Fit.colors.tealTrack` in dark) — `TonalPill`, selected `FilterChip`, active nav pill, ring track background.

 |
| `onPrimaryContainer` | `#7BF3DC` | `#00201A` | Text/icons inside `primaryContainer` (`TonalPill`, selected chips, active nav icon). |
| `inversePrimary` | `#006B5A` | `#5CD6C0` | Action button text inside inverse `Snackbar` (`Undo`). |
| `secondary` | `#8AB4F8` | `#0B57D0` | Accent Blue (`Fit.colors.blue`) — Secondary actions, hydration/protein macros, warm-up chips, info badges, user chat bubble accent.

 |
| `onSecondary` | `#003062` | `#FFFFFF` | Text/icons on solid `secondary` elements. |
| `secondaryContainer` | `#172B4A` | `#D3E3FD` | Blue Track (`Fit.colors.blueTrack` in dark) — Warm-up pills, secondary tonal buttons, protein/water ring tracks.

 |
| `onSecondaryContainer` | `#D3E3FD` | `#041E49` | Text/icons inside `secondaryContainer`. |
| `tertiary` | `#FDD663` | `#B06000` | Accent Gold (`Fit.colors.gold`) — Personal Records (PRs), streak badges, workout score highlights, tour milestones.

 |
| `onTertiary` | `#3F2E00` | `#FFFFFF` | Text/icons on solid Gold badges/banners. |
| `tertiaryContainer` | `#382D12` | `#FFDF9E` | Gold Track (`Fit.colors.goldTrack` in dark) — PR row highlight background, badge containers, `DeltaChip` positive highlight.

 |
| `onTertiaryContainer` | `#FFE082` | `#261900` | Text/icons inside PR cards and gold containers. |
| `error` | `#F28B82` | `#B3261E` | Accent Coral (`Fit.colors.coral`) — Destructive swipe-to-delete, `Pain` effort chip, calorie over-target alert, validation errors.

 |
| `onError` | `#601410` | `#FFFFFF` | Text/icon on solid `error` surfaces. |
| `errorContainer` | `#3C1D1B` | `#F9DEDC` | Coral Track (`Fit.colors.coralTrack` in dark) — `SwipeToDismissBox` reveal background, `Pain` chip selected container.

 |
| `onErrorContainer` | `#F9DEDC` | `#410E0B` | Text/icon inside `errorContainer`. |
| `background` / `surface` | `#131314` | `#F6F8FC` | Root screen canvas (`Fit.colors.background`).

 |
| `onBackground` / `onSurface` | `#E3E3E3` | `#1F1F1F` | Primary text and icons (`Fit.colors.textPrimary`).

 |
| `surfaceVariant` | `#282A2C` | `#E1E6EF` | Legacy M3 variant fallback (`Stepper` outer container, `ProgressLine` unfilled track). |
| `onSurfaceVariant` | `#9AA0A6` | `#444746` | Secondary text, subtitles, units (`kg`, `kcal`, `reps`), inactive nav icons (`Fit.colors.textSecondary`).

 |
| `outline` | `#8E918F` | `#747775` | Focused text field border, unselected checkbox border (`2.dp`), `OutlinedButton` border. |
| `outlineVariant` | `#333537` | `#C4C7C5` | Subtle `1.dp` card borders, `HorizontalDivider`, connected button group internal dividers. |
| `inverseSurface` | `#E3E3E3` | `#2E3133` | `Snackbar` container (`UndoSnackbarEffect`) and `PlainTooltip` background.

 |
| `inverseOnSurface` | `#1E1F20` | `#F0F1F3` | `Snackbar` message text. |
| `scrim` | `#000000` | `#000000` | Dialog and bottom sheet backdrop (`alpha = 0.5f`). |
| **`ForgeSemanticColors.purple`** | `#C58AF9` | `#681DA8` | Recovery / Sleep / Fat macro / AI Coach accent (`Fit.colors.purple`).

 |
| **`ForgeSemanticColors.purpleContainer`** | `#2D1E3E` | `#F3E8FD` | Purple Track (`Fit.colors.purpleTrack` in dark) — Recovery rings, Coach action card tint, `Hard` effort chip tint.

 |
| **`ForgeSemanticColors.onPurpleContainer`** | `#E9D2FD` | `#23005C` | Text/icon inside purple containers. |

---

### 2.2 Typography Scale & Arabic Font Pairing

* **Decision:** Pair your existing bundled `Manrope` (`res/font/manrope.ttf`) with `IBM Plex Sans Arabic` (`Regular 400`, `Medium 500`, `SemiBold 600`, `Bold 700`), and enforce `fontFeatureSettings = "tnum, lnum"` on all numeric/metric styles.


* **Why:** `Manrope` is already in your APK with open geometric apertures that excel on small workout rows, and `IBM Plex Sans Arabic` shares its exact stroke contrast, low-looped proportions, and modern technical legibility without the vertical bloat of traditional Naskh or the wide horizontal footprint of Noto Kufi.


* **Arabic Line-Height & Clipping Rule:** In Arabic (`Locale("ar")`), multiply `lineHeight` by **`1.15x`**, set `PlatformParagraphStyle(includeFontPadding = false)`, and use `LineHeightStyle(alignment = Alignment.Center, trim = Trim.None)` so tall ascenders and stacked diacritics never clip at `200%` system font scale.

| M3 Type Role | Size (`sp`) | Weight | Letter Spacing | Line Height (EN / AR) | Tabular (`tnum`) | Where It Applies in ForgeQuest |
| --- | --- | --- | --- | --- | --- | --- |
| `displayLarge` | `52.sp` | Bold (700) | `-0.25.sp` | `60.sp` / `68.sp` | **Yes** (`tnum`) | Workout Finish Score (`4–10` count-up hero number).

 |
| `displayMedium` | `40.sp` | Bold (700) | `0.sp` | `48.sp` / `54.sp` | **Yes** (`tnum`) | Live Workout floating Rest Timer countdown (`01:30`), Readiness Detail hero score.

 |
| `displaySmall` | `32.sp` | Bold (700) | `0.sp` | `40.sp` / `46.sp` | **Yes** (`tnum`) | `CalorieCard` remaining calories hero number, `ConcentricRings` center readout.

 |
| `headlineLarge` | `28.sp` | SemiBold (600) | `0.sp` | `36.sp` / `42.sp` | No | Onboarding step titles, Finish Debrief headline.

 |
| `headlineMedium` | `24.sp` | SemiBold (600) | `0.sp` | `32.sp` / `37.sp` | **Yes** (`tnum`) | Top-level screen headers (`Home`, `Workouts`, `Food`, `Progress`, `Profile`).

 |
| `headlineSmall` | `20.sp` | SemiBold (600) | `0.sp` | `28.sp` / `32.sp` | **Yes** (`tnum`) | `ModalBottomSheet` titles, `NumberEntryDialog` header, `SectionTitle`.

 |
| `titleLarge` | `18.sp` | SemiBold (600) | `0.sp` | `24.sp` / `28.sp` | **Yes** (`tnum`) | `FitCard` primary title (Today's workout name, exercise header in `LiveWorkoutScreen`).

 |
| `titleMedium` | `16.sp` | SemiBold (600) | `0.1.sp` | `22.sp` / `26.sp` | **Yes** (`tnum`) | `NumberStepper` center value (`82.5 kg`, `10 reps`), list item primary title.

 |
| `titleSmall` | `14.sp` | SemiBold (600) | `0.1.sp` | `20.sp` / `23.sp` | **Yes** (`tnum`) | Sub-headers, weekly journal headers, `StatColumn` value.

 |
| `bodyLarge` | `16.sp` | Regular (400) | `0.15.sp` | `24.sp` / `28.sp` | No | `CoachScreen` markdown body (`MarkdownText`), Onboarding/Tour descriptions, Debrief bullets.

 |
| `bodyMedium` | `14.sp` | Regular (400) | `0.2.sp` | `20.sp` / `24.sp` | No | Card subtitles, exercise target notes, food log item details, settings subtitles.

 |
| `bodySmall` | `12.sp` | Regular (400) | `0.25.sp` | `16.sp` / `19.sp` | **Yes** (`tnum`) | Previous set history hint (`Last: 80kg × 8`), chart axis labels, timestamps. |
| `labelLarge` | `14.sp` | SemiBold (600) | `0.1.sp` | `20.sp` / `23.sp` | **Yes** (`tnum`) | `PillButton`, `TonalPill`, `SegmentedTabs`, `FilterChip` labels.

 |
| `labelMedium` | `12.sp` | Medium (500) | `0.3.sp` | `16.sp` / `19.sp` | **Yes** (`tnum`) | Effort chips (`Easy/OK/Hard/Pain`), `DeltaChip`, `NavigationBar` labels, macro unit badges.

 |
| `labelSmall` | `11.sp` | Medium (500) | `0.3.sp` | `14.sp` / `16.sp` | **Yes** (`tnum`) | Set index badge (`W`, `1`, `2`, `3`), PR micro-badge, heatmap day initials.

 |

---

### 2.3 Shape Scale & Expressive Shape Morphing

* **Decision:** **Use a concentric M3 shape scale (`20.dp` outer cards, `12.dp` inner rows/tiles, `28.dp` sheets/dialogs) and apply Expressive corner-radius morphing on interactive buttons, chips, and set checkboxes.**
* **Why:** Concentric radii ($R_{\text{inner}} = R_{\text{outer}} - \text{Padding} = 20\text{dp} - 8\text{dp} = 12\text{dp}$) eliminate visual pinch points in dense cards, while corner morphing on press gives immediate tactile confirmation without scaling the layout bounds.

| Shape Token / Component | Rest Corner Radius | Pressed Corner Radius | Selected / Active Radius | Where It Applies |
| --- | --- | --- | --- | --- |
| `shapes.extraSmall` | `4.dp` | `2.dp` | `4.dp` | `Heatmap` cells, chart bar caps (`BarRow`), inline code blocks in `MarkdownText`.

 |
| `shapes.small` | `8.dp` | `4.dp` | `8.dp` | `DeltaChip`, PR tag pill, `PlainTooltip`, stepper `-` / `+` inner buttons inside a group.

 |
| `shapes.medium` (Inner) | `12.dp` | `8.dp` | `16.dp` | Set rows inside `LiveWorkoutScreen`, macro sub-tiles, Coach plan-diff preview cards, `OutlinedTextField`.

 |
| `shapes.large` | `16.dp` | `12.dp` | `16.dp` | `CoachScreen` chat bubbles (with `4.dp` tail corner on sender side), `SearchBar` expanded results card.

 |
| `ForgeShapes.card` (`largeIncreased`) | `20.dp` | `16.dp` (interactive) | `24.dp` (dragged) | `FitCard`, `CalorieCard`, `ReadinessCard`, Workout Plan cards, Profile cards.

 |
| `shapes.extraLarge` | `28.dp` | `28.dp` | `28.dp` | `NumberEntryDialog` (all 4 corners); `ModalBottomSheet` (`topStart = 28.dp, topEnd = 28.dp, bottom = 0.dp`).

 |
| **Morphing Primary/Tonal Button** (`PillButton`, `TonalPill`) | `50%` (`24.dp` on `48.dp` height) | **`12.dp`** (squircle) | `16.dp` | All primary and tonal action buttons (`Start Workout`, `Finish`, `Save`, `Log Meal`).

 |
| **Morphing Set Tick** (`LiveWorkoutScreen`) | `10.dp` (Unchecked square, `40.dp` box inside `48.dp` target) | **`4.dp`** (Compressed squircle) | **`50%` (`20.dp` Circle)**; or **`MaterialShapes.Sunny`** on PR set | The Done tick on every set row in `LiveWorkoutScreen`.

 |
| **Connected Effort Group** (`Easy / OK / Hard / Pain`) | Outer ends: `50%` (`20.dp`); Inner seams: **`6.dp`** | Inner seams: **`2.dp`** | Selected chip: **`50%` (`20.dp` all 4 corners)** | Effort selector row in `LiveWorkoutScreen` and `SegmentedTabs` in `ProfileScreen` / `ProgressScreen`.

 |
| **Expressive `LoadingIndicator**` | Morphs continuously across `SoftBurst` → `Cookie9Sided` → `Pentagon` → `Pill` → `Sunny` | — | — | AI Coach thinking indicator, Food photo scan analysis (`ScanScreen`), Drive/Health sync.

 |

---

### 2.4 Motion Tokens (M3 Easing/Duration + Expressive Springs)

* **Decision:** **Use Expressive Spring tokens (`MotionScheme.expressive()`) for all interactive UI (spatial springs for geometry/position/shape, critically damped effects springs for color/alpha), and reserve cubic-bezier tween tokens for continuous data sweeps (rings, charts) and screen fades.**
* **Why:** Springs handle mid-flight interruption naturally (e.g., rapid tapping on a weight stepper or toggling effort chips during a workout) without snapping or resetting velocity, matching both M3 Expressive and `@material/web`'s motion architecture.



#### A. Expressive Spring Tokens (`ForgeMotion.Springs`)

> **Version Note:** Native `MaterialTheme.motionScheme` requires `material3:1.4.0-alpha04+`. On `material3 1.3.x`, use the exact `spring(dampingRatio, stiffness)` values in `ForgeMotion` (provided in Phase 3 Code).

| Token Name | Damping Ratio ($\zeta$) | Stiffness ($k$) | Overshoot? | What It Drives in ForgeQuest |
| --- | --- | --- | --- | --- |
| `fastSpatial` | `0.6f` (Under-damped) | `800f` | Slight, snappy (~8%) | Set tick check morph, button press corner morph (`24.dp` ↔ `12.dp`), `Stepper` number roll, effort chip selection morph.

 |
| `defaultSpatial` | `0.7f` (Under-damped) | `380f` | Subtle (~4%) | List item reorder (`animateItem`), `PlanEditorScreen` drag lift/drop, expand/collapse set details, bottom sheet snap.

 |
| `slowSpatial` | `0.8f` (Slightly under-damped) | `200f` | Minimal (~1%) | Shared container transforms (`Home` Today Card → `LiveWorkout`, `History` row → Detail Sheet), page transitions.

 |
| `fastEffects` | `1.0f` (Critically damped) | `3800f` | **0% (Never)** | Button state-layer alpha, ripple press, checkbox border/fill color swap, stepper button flash. |
| `defaultEffects` | `1.0f` (Critically damped) | `1600f` | **0% (Never)** | Set row completion background tint (`#1E1F20` → `#14352F`), tab indicator color crossfade, card border highlight.

 |
| `slowEffects` | `1.0f` (Critically damped) | `800f` | **0% (Never)** | PR gold ambient glow fade-out, `BodyMap` muscle recovery heat tint transitions, theme dark/light switch.

 |

#### B. M3 Easing & Duration Tokens (`ForgeMotion.Easing` & `ForgeMotion.Duration`)

Matches `@material/web`'s `--md-sys-motion-*` tokens for deterministic timeline animations:

| Token | Value (Compose / CSS) | Duration Pair | Usage in ForgeQuest |
| --- | --- | --- | --- |
| `EmphasizedAccelerate` | `CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)` | `Short3` (`150ms`) / `Medium1` (`250ms`) | Exiting elements: `Snackbar` exit, dialog dismiss, swipe-to-delete row collapse.

 |
| `EmphasizedDecelerate` | `CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)` | `Medium4` (`400ms`) / `Long2` (`500ms`) | Entering elements & data sweeps: `ConcentricRings` sweep, `TrendChart` draw-in, Finish score count-up, staggered debrief reveal.

 |
| `Emphasized` | `CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)` | `Medium2` (`300ms`) | Screen-level shared axis X/Y/Z transitions when springs are not used. |
| `Standard` | `CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)` | `Short4` (`200ms`) | Utility transitions: icon rotation, chevron flip, top app bar color lerp on scroll. |

---

## 3. Component Mapping Table

Every existing ForgeQuest component and all 26 required Material 3 / Expressive component categories are mapped below with their `@material/web` (`md-*`) counterpart and Jetpack Compose implementation.

| # | Category / ForgeQuest Component | `@material/web` Equivalent

 | Jetpack Compose M3 (`1.3.x` Current & `1.4+` Expressive) | Recommendation & Screens | Why (1-Line Rationale) |
| --- | --- | --- | --- | --- | --- |
| **1** | **Filled Button** (`PillButton`)

 | `<md-filled-button>`

 | `Button` with animated `RoundedCornerShape` (`24.dp` → `12.dp` on press via `InteractionSource`) | **Adopt** on `HomeScreen` (`Start Workout`), `LiveWorkoutScreen` (`Finish`), `OnboardingScreen` (`Continue`), `FoodScreen` (`Log Meal`).

 | Gives the highest-priority action on each screen unmistakable hierarchy and tactile press morphing. |
| **2** | **Filled Tonal Button** (`TonalPill`)

 | `<md-filled-tonal-button>`

 | `FilledTonalButton` (`primaryContainer` / `secondaryContainer`) with press corner morph | **Adopt** on `HomeScreen` (`Quick Log`), `LiveWorkoutScreen` (`+ Add Set`, `One-Tap Warm-up`), `WorkoutsScreen` (`Edit Plan`), `CoachScreen` (`Apply Plan Change`).

 | Delivers strong tap affordance for secondary actions without competing with the single primary CTA. |
| **3** | **Outlined Button** | `<md-outlined-button>`

 | `OutlinedButton` (`1.dp` `outlineVariant` border) | **Adopt** on `LiveWorkoutScreen` (`Swap Exercise`), `ProfileScreen` (`Export Backup`), `CoachScreen` (`Reject/Dismiss diff`).

 | Ideal for low-emphasis alternative actions that sit beside a `FilledTonalButton`. |
| **4** | **Text Button** | `<md-text-button>`

 | `TextButton` | **Adopt** inside `NumberEntryDialog` (`Cancel`), `UpdateCard` (`Later`), and `SectionTitle` trailing action (`See all`).

 | Keeps dialogs and section headers uncluttered while preserving a full `48.dp` min touch target. |
| **5** | **Elevated Button** | `<md-elevated-button>`

 | `ElevatedButton` | **Not worth it.** | Shadows clash with our zero-elevation tonal surface architecture; `FilledTonalButton` replaces it everywhere. |
| **6** | **Icon Button & Toggle Icon Button** (`IconCircle`)

 | `<md-icon-button>`, `<md-filled-tonal-icon-button toggle>`

 | `IconButton` & `FilledTonalIconButton` / `FilledTonalIconToggleButton` | **Adopt** on `Stepper` (`-` / `+`), top bar actions, `LiveWorkoutScreen` rest timer `+15s` / pause, and `IconCircle`.

 | Standardizes `IconCircle` to a guaranteed `48.dp × 48.dp` touch target with built-in state layers and focus rings.

 |
| **7** | **FAB & Extended FAB** | `<md-fab>`, `<md-branded-fab>`

 | `ExtendedFloatingActionButton` (collapses to `FloatingActionButton` on scroll) | **Adopt ONLY on `FoodScreen**` (`+ Log / Scan Meal`); **Not worth it** on `Home` or `LiveWorkout`.

 | `Home` already has an inline `Start` hero card and `LiveWorkout` uses a sticky bottom bar, so a FAB only adds value on the scrolling `FoodScreen` log.

 |
| **8** | **Button Groups & Connected Toggle Buttons** (`SegmentedTabs`, Effort Chips)

 | Custom CSS flex group with `<md-filled-tonal-button>` | **[M3 1.4+ Expressive]** `ButtonGroup` + `ToggleButton`; **[1.3.x Fallback]** Custom `ConnectedToggleGroup` row with animated inner corners (`6.dp` → `50%`) | **Adopt** for `LiveWorkoutScreen` Effort row (`Easy/OK/Hard/Pain`), `ProgressScreen` time range (`7D/30D/90D`), and `ProfileScreen` unit/theme toggles.

 | Connected toggle buttons morph the selected item into a full pill while keeping adjacent options tightly grouped and scannable. |
| **9** | **Split Button** | `<md-filled-button>` + `<md-icon-button>` menu trigger

 | **[M3 1.4+ Expressive]** `SplitButtonLayout`; **[1.3.x Fallback]** `Row(spacedBy(2.dp))` with `24.dp` outer / `4.dp` inner corners + `DropdownMenu` | **Adopt** on `HomeScreen` Today's Workout card (`Start Workout` primary + trailing chevron to `Swap Day / Pick Plan`).

 | Lets 95% of users start their workout in one tap while giving a zero-clutter escape hatch to switch plans. |
| **10** | **Chips: Assist, Filter, Input, Suggestion** (`ChoiceChip`, `DeltaChip`)

 | `<md-assist-chip>`, `<md-filter-chip>`, `<md-input-chip>`, `<md-suggestion-chip>`

 | `AssistChip`, `FilterChip`, `InputChip`, `SuggestionChip` | **Adopt all 4:** `FilterChip` (`ExerciseLibraryScreen` / `ExercisePicker` muscle filters), `AssistChip` (`LiveWorkout` warm-up one-tap), `SuggestionChip` (`CoachScreen` quick prompts & `FoodScreen` recent meals), `InputChip` (logged meal parts in `FoodParts.kt`).

 | Replaces generic `ChoiceChip` with purpose-built M3 semantics, leading checkmark animations, and trailing remove icons.

 |
| **11** | **Cards** (`FitCard`, `CalorieCard`, `ReadinessCard`, `UpdateCard`)

 | Surface container `<div class="md-card">` + `<md-ripple>` + `<md-focus-ring>`

 | `Card` / `OutlinedCard` (`shape = RoundedCornerShape(20.dp)`, `colors = surfaceContainer`, `0.dp` shadow) | **Adopt** across all screens as the base implementation of `FitCard`.

 | Unifies padding, `20.dp` corner radius, and press state layers across every card in the app. |
| **12** | **Lists & List Items** | `<md-list>`, `<md-list-item>`, `<md-divider>`

 | `ListItem` (`colors = Color.Transparent` inside `FitCard`) + `HorizontalDivider(color = outlineVariant)` | **Adopt** on `FoodScreen` search results, `ExerciseLibraryScreen`, `HistoryScreen` rows, and `ProfileScreen` settings rows.

 | Guarantees consistent 1-line, 2-line, and 3-line vertical alignment, RTL mirroring, and `200%` text wrapping.

 |
| **13** | **Navigation: Bar, Rail, Drawer** (`AppRoot.kt`)

 | Custom nav bar / rail with `<md-ripple>` | `NavigationSuiteScaffold` (`NavigationBar` on Compact, `NavigationRail` on Medium/Expanded); **No `ModalNavigationDrawer**` | **Adopt `NavigationBar` + `NavigationRail**` in `AppRoot.kt`; **skip `NavigationDrawer**`.

 | ForgeQuest has 5 top-level destinations, which fit a bottom bar on phones and a rail on tablets/foldables without hiding destinations in a drawer. |
| **14** | **Top App Bars** | `<top-app-bar>`

 | `CenterAlignedTopAppBar` / `TopAppBar` (Pinned) and `MediumTopAppBar` (`exitUntilCollapsedScrollBehavior`) | **Adopt** `MediumTopAppBar` on scrolling overview screens (`Home`, `Progress`, `History`, `Profile`) and pinned compact `TopAppBar` on task screens (`LiveWorkout`, `Food`, `Coach`).

 | Collapsing headers give breathing room at the top of overview screens while maximizing vertical workspace during active logging. |
| **15** | **Floating Toolbar** | Custom pill toolbar (`position: fixed`) | **[M3 1.4+ Expressive]** `HorizontalFloatingToolbar`; **[1.3.x Fallback]** `Surface(shape = CircleShape, color = surfaceContainerHighest, shadowElevation = 6.dp)` | **Adopt** at the bottom of `LiveWorkoutScreen` (housing the active Rest Timer pill + `+15s` + `Skip` + `Finish` CTA).

 | Keeps the rest timer and `Finish` action thumb-reachable above scrolling set rows without a heavy full-width bottom bar. |
| **16** | **Menus** | `<md-menu>`, `<md-menu-item>`

 | `DropdownMenu` + `DropdownMenuItem` (`shape = RoundedCornerShape(16.dp)`, `containerColor = surfaceContainerHighest`) | **Adopt** for secondary overflow actions on `WorkoutsScreen` plan cards (`Duplicate`, `Reset`, `Delete`) and `HistoryScreen` entries.

 | Hides rare destructive/admin actions behind a single overflow icon, honoring the "clean and calm" UX rule. |
| **17** | **Dialogs, Bottom Sheets, Side Sheets** (`NumberEntryDialog`, `MuscleExercisesSheet`, `PutInPlanSheet`)

 | `<md-dialog>`

 | `BasicAlertDialog` / `AlertDialog` (`28.dp`), `ModalBottomSheet` (`skipPartiallyExpanded = true` for short sheets); Side Sheet on Expanded tablets | **Adopt** `AlertDialog` for `NumberEntryDialog`, `ModalBottomSheet` for `MuscleExercisesSheet`, `PutInPlanSheet`, and Journal Workout Detail.

 | Native M3 sheets support predictive back gestures out of the box and keep users in context without full-screen navigation jumps. |
| **18** | **Snackbars & Tooltips** (`UndoSnackbarEffect`)

 | Custom toast / tooltip | `SnackbarHost` + `Snackbar` (`actionOnNewLine = false`, `inverseSurface`) and `TooltipBox` + `PlainTooltip` | **Adopt** `Snackbar` for `UndoSnackbarEffect` (swipe-to-delete undo in `Food` and `History`) and `PlainTooltip` on icon-only buttons and `BodyMap` muscle regions.

 | Supports your background-execution UX rule ("user sees only success or failure" with one-tap Undo). |
| **19** | **Badges** (`BadgesScreen.kt`)

 | Custom badge pill | `Badge` / `BadgedBox` (nav/filter counts) + custom M3 Expressive Polygon (`MaterialShapes.Sunny` / `Cookie9Sided`) for achievement medals in `BadgesScreen`

 | **Adopt** `Badge` on `HomeScreen` `UpdateCard` dot and active filter count; Expressive polygon shapes on `BadgesScreen` and PR icons.

 | Gives achievement badges and PR moments a signature geometric silhouette instead of a generic circle. |
| **20** | **Progress: Linear, Circular, Wavy, Loading Indicator** (`ProgressLine`, `ConcentricRings`, `MiniRing`)

 | `<md-linear-progress>`, `<md-circular-progress>`

 | `LinearProgressIndicator` (`strokeCap = StrokeCap.Round`, `gapSize = 4.dp`), custom `ConcentricRings` with M3 track gap, **[M3 1.4+]** `LinearWavyProgressIndicator` & `LoadingIndicator` | **Adopt** rounded-gap `LinearProgressIndicator` for `ProgressLine` (macros), `LinearWavyProgressIndicator` for the active Rest Timer bar in `LiveWorkout`, and `LoadingIndicator` for AI Coach & Food Scan.

 | The wavy progress bar visually communicates an *active ticking timer*, while `LoadingIndicator` makes AI waits feel fast and alive. |
| **21** | **Sliders** | `<md-slider>`

 | `Slider` (M3 thick track with thumb gap) | **Not worth it for weights/reps** (keep `NumberStepper` + `NumberEntryDialog`); **Adopt ONLY** if adding a quick Readiness/Soreness self-check (`1–5`).

 | Sliders are imprecise for `0.5 kg` weight increments or calorie entry with sweaty hands; steppers are faster and exact. |
| **22** | **Switches, Checkboxes, Radio Buttons** (`FitSwitch`, Set Tick)

 | `<md-switch>`, `<md-checkbox>`, `<md-radio>`

 | `Switch` (with thumb icon), custom morphing `SetTickCheckbox` (`LiveWorkout`), `RadioButton` (inside `OnboardingScreen` single-choice cards)

 | **Adopt** M3 `Switch` to replace `FitSwitch` on `ProfileScreen`, and custom morphing `SetTickCheckbox` on `LiveWorkoutScreen`.

 | M3 `Switch` scales thumb size on press/check for better accessibility, while the custom set tick delivers the core workout payoff. |
| **23** | **Segmented Buttons & Tabs** (`SegmentedTabs`)

 | `<md-tabs>`, `<md-primary-tab>`, `<md-secondary-tab>`

 | `SingleChoiceSegmentedButtonRow` (for 2–4 view/setting switches) and `SecondaryTabRow` (for `FoodScreen` Log / Library / Targets)

 | **Adopt** `SingleChoiceSegmentedButtonRow` (or Expressive `ConnectedToggleGroup`) on `ProfileScreen` & `ProgressScreen`; `SecondaryTabRow` on `FoodScreen`.

 | Separates *mode/setting selection* (segmented buttons) from *content view switching* (tabs). |
| **24** | **Text Fields & Search Bar** (`FoodScreen`, `ExercisePicker`, `CoachScreen`)

 | `<md-filled-text-field>`, `<md-outlined-text-field>`

 | `OutlinedTextField` (`shape = RoundedCornerShape(12.dp)`) and `SearchBar` / `DockedSearchBar` (`shape = CircleShape`) | **Adopt** pill-shaped `DockedSearchBar` on `FoodScreen` and `ExerciseLibraryScreen` / `ExercisePicker`; pill `OutlinedTextField` for `CoachScreen` chat input.

 | `DockedSearchBar` filters inline without pushing a new screen, honoring your "no extra pickers or steps" rule. |
| **25** | **Date/Time Pickers, Carousel, Pull-to-Refresh** | Native / custom | **Skip** `DatePickerDialog` & `TimePickerDialog`; **Adopt** `HorizontalMultiBrowseCarousel` on `AppTour.kt`; **Adopt** `PullToRefreshBox` (uses `LoadingIndicator`) on `HomeScreen`

 | **Skip pickers** (use inline `< Today >` chevrons); **Adopt** `PullToRefreshBox` on `HomeScreen` for Health Connect / Drive sync.

 | Inline day chevrons (`< Today >`) save 3 taps compared to a modal date picker, while pull-to-refresh triggers background sync cleanly. |
| **26** | **Swipe Actions & Drag Handles** (`SwipeToDelete.kt`, `PlanEditorScreen.kt`)

 | Custom gesture + `<md-icon>`

 | `SwipeToDismissBox` (with scale-pop trash icon at threshold) + `BottomSheetDefaults.DragHandle()` + `Icon(Icons.Rounded.DragHandle)` in `PlanEditorScreen`

 | **Enhance** existing `SwipeToDelete.kt` with a threshold spring pop + haptic tick, and add explicit `48.dp` drag handles in `PlanEditorScreen`.

 | Makes swipe-to-delete threshold crossing unmistakable and provides an accessible touch target for drag-reordering exercises. |

---

*Ready for **Phase 2** (Sections 4–6: **4. Full Motion & Haptics Catalog Table**, **5. Adaptive Layout & Accessibility/RTL Rules**, and **6. Screen-by-Screen Transformation Plan**). Reply **"continue"** or **"send phase 2"** whenever you are ready.*
</pasted_content id="6342">

<pasted_content id="6342">
### Bottom Line Up Front (BLUF)

* **Phase 2 Split:** To keep the specification thorough and implementation-ready, **Phase 2A (this response)** delivers **Section 4 (Full Motion & Haptics Catalog)** and **Section 5 (Adaptive Layout, Accessibility & RTL Rules)**. **Phase 2B (next response)** will cover **Section 6 (Screen-by-Screen Transformation Plan)**.
* **Interruptible Spring Physics + Haptic Pairing:** Every interactive element uses Material 3 Expressive springs (`fastSpatial` $\zeta=0.6, k=800$ for micro-interactions; `defaultSpatial` $\zeta=0.7, k=380$ for layout/lists; `criticallyDamped` $\zeta=1.0$ for color/alpha) paired with hardware-calibrated haptics (`HapticFeedbackType` and API 29+ `VibrationEffect` primitives) so logging sets in `LiveWorkoutScreen` feels instantaneous.


* **Zero-Lag Reduced Motion:** Every spring and keyframe animation checks Android's system `ANIMATOR_DURATION_SCALE` (exposed via a `LocalReducedMotion` composition local) and falls back to a `0ms` instant state swap or a `120ms` opacity crossfade with zero spatial translation.
* **Adaptive & 200%-Safe Layout:** `AppRoot` switches from a `NavigationBar` on phones to a `NavigationRail` + 2-pane `ListDetailPaneScaffold` on tablets/foldables, while set rows and setting cards automatically reflow into a 2-tier vertical stack when `fontScale >= 1.35f` so you can remove the `1.3x` font scale cap without clipping at `200%` text.



---

## 4. Motion & Haptics Catalog

### 4.1 System Reduced-Motion Architecture

Before running any spatial animation, read the system animator scale (`Settings.Global.ANIMATOR_DURATION_SCALE == 0f` or `ValueAnimator.areAnimatorsEnabled() == false`) and provide `LocalReducedMotion`:

* **Rule 1 (Spatial Springs Disabled):** When `LocalReducedMotion.current == true`, all `fastSpatial`, `defaultSpatial`, and `slowSpatial` springs resolve to `SnapSpec(delay = 0)` for position/scale/corner-radius, eliminating bounce, slide, and morph motion.
* **Rule 2 (Effects Preserved as Short Fades):** State-layer and container color transitions use `tween(durationMillis = 120, easing = LinearEasing)` so visual state changes remain clear without disorienting movement.
* **Rule 3 (Haptics Preserved):** Haptic feedback (`Haptics.kt`) remains active even when visual animations are reduced, as tactile confirmation aids eyes-free gym logging.



---

### 4.2 Complete Interaction, Motion & Haptics Catalog Table

| # | Category & Interaction | Target Element & What Moves | Exact Motion Spec (Compose M3 / Expressive) | Paired Haptic (`HapticFeedbackType` / `VibrationEffect`) | Reduced-Motion Fallback |
| --- | --- | --- | --- | --- | --- |
| **A1** | **Screen Push / Pop (Peer Hierarchy)** | Incoming screen slides `±15%` width (`Start`/`End` RTL-aware) + fades in; outgoing slides `∓7%` + fades out.

 | **Shared Axis X:** Position: `defaultSpatial` (`spring(0.7f, 380f)`); Alpha: Out `tween(90ms, EmphasizedAccelerate)`, In `tween(210ms, EmphasizedDecelerate)`. | None | `fadeIn(tween(120ms))` / `fadeOut(tween(120ms))` with `0.dp` slide. |
| **A2** | **Vertical Flow Step** (`OnboardingScreen` steps)

 | Next step slides up `+24.dp` → `0.dp` + fades in; previous slides `-24.dp` + fades out. | **Shared Axis Y:** `defaultSpatial` (`spring(0.7f, 380f)`) + Fade Through (`90ms` out / `210ms` in). | `HapticFeedbackType.TextHandleMove` (light tick) | Instant swap with `120ms` crossfade. |
| **A3** | **Depth Drill-Down** (`ReadinessCard` → `ReadinessDetailScreen`, `ExerciseLibrary` → `ExerciseDetailScreen`)

 | Incoming screen scales `0.92f` → `1.0f` + fades in; outgoing scales `1.0f` → `1.04f` + fades out. | **Shared Axis Z:** Scale: `defaultSpatial` (`spring(0.75f, 400f)`); Alpha: `90ms` out / `210ms` in. | None | `120ms` alpha crossfade (`scale = 1.0f`). |
| **A4** | **Bottom Bar / Nav Rail Tab Switch** (`AppRoot.kt`)

 | Active pill indicator expands width `32.dp` → `64.dp`; screen body crossfades. | **Nav Pill:** `fastSpatial` (`spring(0.6f, 800f)`); **Screen Body:** Fade Through (`90ms` `EmphasizedAccelerate` out, `180ms` `EmphasizedDecelerate` in). | `VibrationEffect.EFFECT_TICK` (API 29+) / `CLOCK_TICK` | Instant pill snap; `100ms` screen crossfade. |
| **A5** | **Container Transform: Today Card → Live Workout** (`HomeScreen` → `LiveWorkoutScreen`)

 | Today's `FitCard` bounds expand to full screen; corner radius morphs `20.dp` → `0.dp`.

 | `SharedTransitionLayout` + `Modifier.sharedBounds`: `slowSpatial` (`spring(0.8f, 200f)`), `clipInOverlayDuringTransition(RoundedCornerShape(animatedRadius))`. | `HapticFeedbackType.LongPress` / `EFFECT_CLICK` on `Start` tap | Standard `120ms` crossfade into `LiveWorkoutScreen`. |
| **A6** | **Container Transform: Journal Row → Detail Sheet** (`HistoryScreen`)

 | Tapped workout row expands into `ModalBottomSheet` (Compact) or Side Detail Pane (Expanded).

 | `defaultSpatial` (`spring(0.7f, 380f)`) for sheet Y-translation and corner morph (`12.dp` → `28.dp` top corners). | `EFFECT_TICK` | Sheet appears immediately with `120ms` alpha fade. |
| **A7** | **Predictive Back Gesture** (`PredictiveBackHandler` across all screens & sheets)

 | Active screen/sheet follows finger `progress (0f..1f)`: scales `1.0f` → `0.90f`, corners morph `0.dp` → `28.dp`, shifts `24.dp` toward swipe edge. | Driven directly by `BackEventCompat.progress`; on cancel, snaps back via `fastSpatial` (`spring(0.6f, 800f)`); on commit, exits via `defaultSpatial`. | System back-gesture threshold tick (handled by OS on API 34+) | Direct `alpha = 1f - (progress * 0.3f)` preview without scale/translation. |
| **B1** | **Live Workout: Ticking a Set (Done Tick)** (`LiveWorkoutScreen.kt`)

 | 1) Checkbox box morphs `10.dp` square → `4.dp` pressed → `50%` (`20.dp`) circle; 2) Checkmark path draws `0f..1f`; 3) Set row background tints `#1E1F20` → `#14352F` (`primaryContainer`).

 | **Box Corner & Scale (`1.0f` → `1.15f` → `1.0f`):** `fastSpatial` (`spring(0.6f, 800f)`); **Check Path:** `tween(150ms, EmphasizedDecelerate)`; **Row Tint:** `defaultEffects` (`spring(1.0f, 1600f)`). | `VibrationEffect.EFFECT_CLICK` (crisp single click on check; `EFFECT_TICK` on uncheck)

 | Instant corner/icon swap; `120ms` row background crossfade. |
| **B2** | **Live Workout: Personal Record (PR) Moment** (`LiveWorkoutScreen.kt`)

 | **No confetti overload.** Set tick morphs into 8-point `MaterialShapes.Sunny` in Gold `#FDD663`; single radial gold shimmer sweeps across the row; `+PR` `DeltaChip` pops in.

 | **Star Morph & Scale (`1.0f` → `1.3f` → `1.0f`):** `spring(0.55f, 700f)`; **Gold Shimmer Sweep:** `tween(500ms, EmphasizedDecelerate)`; **Glow Fade:** `slowEffects` (`spring(1.0f, 800f)`). | Custom `VibrationEffect.Waveform` (`[0, 30, 40, 55]ms`, amplitudes `[0, 180, 0, 255]`) — double celebratory thud | Static Gold `Sunny` badge + instant Gold `#382D12` (`tertiaryContainer`) row border and `DeltaChip`.

 |
| **B3** | **Rest Timer: Start / Tick / Finish** (`LiveWorkoutScreen.kt`)

 | Floating toolbar expands to reveal `LinearWavyProgressIndicator` + tabular countdown (`01:30`); wave amplitude animates `3.dp` (running) ↔ `0.dp` (paused); pill pulses Teal at `00:00`.

 | **Toolbar Expand:** `defaultSpatial` (`spring(0.7f, 380f)`); **Wave Phase:** `infiniteRepeatable(tween(1200ms, LinearEasing))`; **Digit Roll:** `fastSpatial` (`spring(0.65f, 900f)`). | **Start:** `EFFECT_TICK`; **Finish (`00:00`):** `VibrationEffect.EFFECT_DOUBLE_CLICK` + notification chime | Static flat `LinearProgressIndicator`; digits update in place (`tnum`); single border flash at `00:00`. |
| **B4** | **Effort Chip Selection** (`Easy / OK / Hard / Pain`)

 | Tapped chip morphs inner corners from `6.dp` → `50%` (`20.dp` pill) and expands horizontal padding `+4.dp`; container color crossfades (`Teal` / `Blue` / `Purple` / `Coral`).

 | **Corner & Width Morph:** `fastSpatial` (`spring(0.6f, 800f)`); **Color Fill:** `fastEffects` (`spring(1.0f, 3800f)`). | `VibrationEffect.EFFECT_TICK` | Instant pill shape and color swap. |
| **B5** | **Stepper Increments (`-` / `+`)** (`NumberStepper` in `Common.kt`)

 | Center weight/rep number rolls vertically like a mechanical odometer (`+` rolls upward, `-` rolls downward); button flashes state layer.

 | `AnimatedContent` with `slideInVertically { ±it } + fadeIn` via `fastSpatial` (`spring(0.65f, 900f)`); `SizeTransform(clip = true)`. | `HapticFeedbackType.TextHandleMove` (ultra-light tick on every step; repeats on hold)

 | Instant text replacement in place (zero vertical slide). |
| **B6** | **Warm-Up One-Tap** (`LiveWorkoutScreen.kt`)

 | Warm-up `AssistChip` leading `+` icon rotates `90°` and morphs into a checkmark; warm-up set rows expand into the exercise card.

 | **Chip Icon:** `fastSpatial` (`spring(0.6f, 800f)`); **Row Insertion:** `LazyColumn` `animateItem(placementSpec = defaultSpatial)`. | `VibrationEffect.EFFECT_CLICK` | Instant chip state change and row appearance. |
| **B7** | **Workout Finish → Score Count-Up (`4–10`) & Staggered Debrief** (`LiveWorkoutScreen` / `Debrief.kt`)

 | 1) Score ring sweeps `0f..score/10f` while hero score counts up (`0.0` → `8.5`); 2) Debrief sections (`Headline`, `Wins`, `Watch`, `Next Time`) stagger in vertically.

 | **Score Sweep & Count-Up:** `tween(650ms, EmphasizedDecelerate)`; **4 Debrief Cards:** Staggered `70ms` apart, each `translateY(16.dp → 0.dp)` via `defaultSpatial` + `fadeIn(200ms)`. | `EFFECT_HEAVY_CLICK` when score count-up lands at `650ms` | Score displays final number immediately; all 4 debrief sections fade in together over `150ms`. |
| **C1** | **List Insert / Remove / Reorder** (`Workouts`, `Food`, `History`)

 | Added rows fade + scale in (`0.95f` → `1.0f`); remaining rows slide smoothly to new Y positions.

 | `Modifier.animateItem(fadeInSpec = spring(1f, 1600f), placementSpec = spring(0.7f, 380f), fadeOutSpec = spring(1f, 1600f))`.

 | None | Instant list layout update. |
| **C2** | **Swipe to Delete + Undo** (`SwipeToDelete.kt`, `UndoSnackbarEffect`)

 | Row translates horizontally revealing `errorContainer` (`#3C1D1B`); at `40%` swipe threshold, trash icon pops `0.8f` → `1.25f` → `1.0f`.

 | **Threshold Icon Pop:** `fastSpatial` (`spring(0.55f, 800f)`); **Dismiss Collapse:** `tween(200ms, EmphasizedAccelerate)`; **Undo Restore:** `defaultSpatial`. | `VibrationEffect.EFFECT_TICK` exactly when crossing the `40%` commit threshold (and on `Undo` tap) | Trash icon stays `1.0f` scale; row disappears/restores with a `120ms` fade. |
| **C3** | **Drag Lift / Drop** (`PlanEditorScreen.kt`)

 | Grabbed exercise card scales `1.0f` → `1.03f`, shadow elevates `0.dp` → `8.dp`, corners morph `12.dp` → `20.dp`, color shifts to `surfaceContainerHighest`.

 | **Lift & Drop:** `fastSpatial` (`spring(0.65f, 700f)`); **Neighbor Swap:** `defaultSpatial` (`spring(0.7f, 380f)`). | **Lift:** `HapticFeedbackType.LongPress`; **Slot Swap:** `TextHandleMove`; **Drop:** `EFFECT_TICK`

 | Grabbed card highlights with a `2.dp` Teal `#5CD6C0` border (`0.dp` scale/shadow lift) and snaps on drop. |
| **C4** | **Expand / Collapse** (Weekly groups in `HistoryScreen`, Exercise notes)

 | Header chevron rotates `0°` ↔ `180°`; body container clips and animates height.

 | **Height:** `expandVertically(spring(0.7f, 380f))` / `shrinkVertically(spring(0.7f, 380f))`; **Chevron:** `fastSpatial`. | `EFFECT_TICK` | Instant height toggle with `0°`/`180°` chevron snap. |
| **D1** | **Ring Fill** (`ConcentricRings`, `MiniRing` in `Rings.kt`)

 | Outer, middle, and inner rings sweep from `0°` to target arc angle with rounded caps.

 | `tween(durationMillis = 650, delayMillis = ringIndex * 60, easing = EmphasizedDecelerate)`. | None | Rings render at final target angle immediately (`0ms`). |
| **D2** | **Chart Draw-In** (`Sparkline`, `TrendChart`, `BarRow` in `Charts.kt`)

 | Line chart draws from oldest to newest data point (`0f..1f` path clip); `BarRow` fills horizontally from `0f` to target fraction.

 | **Line Clip:** `tween(500ms, EmphasizedDecelerate)`; **Bar Fill:** `defaultSpatial` (`spring(0.75f, 380f)`). | `TextHandleMove` when scrubbing data points on `TrendChart` | Charts render at `100%` completion immediately. |
| **D3** | **Consistency Heatmap Reveal** (`Heatmap` in `Charts.kt`)

 | Week columns fade in `0f..1f` and scale `0.85f` → `1.0f` in a left-to-right (or RTL right-to-left) wave.

 | `tween(220ms, delayMillis = (colIndex * 15).coerceAtMost(240), easing = EmphasizedDecelerate)`. | None | All heatmap cells render at `1.0f` alpha/scale immediately. |
| **D4** | **Value Changes / Number Morph** (`CalorieCard`, `ReadinessCard`, Macro totals)

 | Numeric readout interpolates smoothly when food or sets are logged.

 | `animateIntAsState` / `animateFloatAsState` with `spring(dampingRatio = 0.8f, stiffness = 400f)` + mandatory `tnum`. | None | Instant jump to new number. |
| **D5** | **Loading: Expressive `LoadingIndicator` vs. Skeleton** (`CoachScreen`, `ScanScreen`, `HomeScreen` sync)

 | 7-shape morphing polygon (`SoftBurst` → `Cookie9Sided` → `Pentagon` → `Pill` → `Sunny`) rotates continuously for AI/network tasks.

 | Native M3 Expressive `LoadingIndicator` (`650ms` per shape morph); **no moving shimmer** on local DB loads. | None | Static `CircularProgressIndicator` (or static `surfaceContainerHigh` placeholder box). |
| **E1** | **Button Press & Ripple** (`PillButton`, `TonalPill`)

 | Button corner radius morphs from `24.dp` (pill) to `12.dp` (squircle) while held; M3 ripple expands from touch point.

 | **Corner Morph:** `fastSpatial` (`spring(0.6f, 800f)`); **State Layer / Ripple:** `fastEffects` (`spring(1.0f, 3800f)`). | `EFFECT_TICK` on release (`onClick`) | Standard M3 state-layer opacity change (`12%` `onPrimary`) without corner morphing. |
| **E2** | **Error Shake & Validation** (`NumberEntryDialog`, `PlanSafety`, failed API call)

 | Input field or card shakes horizontally and flashes `error` (`#F28B82`) border.

 | `keyframes { durationMillis = 300; -10f at 50; 10f at 100; -6f at 150; 6f at 200; 0f at 300 }`. | Custom `VibrationEffect.Waveform` (`[0, 40, 50, 40]ms`, amplitudes `[0, 220, 0, 220]`) — double error buzz

 | Border highlights in `#F28B82` (`error`) for `400ms` with `0.dp` horizontal shake. |
| **E3** | **Snackbar Enter / Exit** (`UndoSnackbarEffect`)

 | Floating `Snackbar` slides up from bottom nav bar (`+24.dp` → `0.dp`) and fades in.

 | **Enter:** `defaultSpatial` (`spring(0.7f, 380f)`); **Exit:** `tween(150ms, EmphasizedAccelerate)`. | None | `120ms` alpha fade in/out in place. |
| **E4** | **Onboarding & Tour Pager** (`OnboardingScreen`, `AppTour`)

 | Pager slides between steps; active indicator dot morphs width `8.dp` ↔ `24.dp` (`CircleShape`).

 | **Pager:** `defaultSpatial` (`spring(0.75f, 380f)`); **Indicator Dot:** `fastSpatial` (`spring(0.6f, 800f)`). | `EFFECT_TICK` on page settle | Pager snaps without spring overshoot; indicator swaps color. |
| **E5** | **AI Coach Chat & Plan Diff** (`CoachScreen.kt`)

 | User message pops from bottom-end; AI thinking shows `LoadingIndicator`; markdown fades in by block; plan-change card expands.

 | **Bubble Enter:** `fastSpatial` (`scale 0.92f → 1.0f`); **Markdown Block:** `tween(180ms, EmphasizedDecelerate)`; **Plan Diff Card:** `defaultSpatial`. | `EFFECT_TICK` on message send; `EFFECT_CLICK` when AI plan-change card arrives | Messages and plan cards appear immediately with `100ms` alpha fade. |

---

## 5. Adaptive Layout, Accessibility & RTL Rules

### 5.1 Adaptive Layout (`WindowSizeClass` & Foldables)

* **Dependency:** `androidx.compose.material3:material3-window-size-class` and `androidx.compose.material3:material3-adaptive-navigation-suite` (included in Compose BOM `2025.02.00`).



| Window Width Class | Breakpoint | Navigation Component (`AppRoot.kt`)

 | Content Layout & Pane Strategy | Margins & Max Widths |
| --- | --- | --- | --- | --- |
| **Compact** (Phones Portrait) | `width < 600.dp` | `NavigationBar` (`80.dp` height, 5 tabs: `Home`, `Workouts`, `Food`, `Progress`, `Coach`; `Profile` via top-end avatar; `History` inside `Workouts`/`Progress` or 5-tab config).

 | Single-column scrolling layout. Details (`MuscleExercisesSheet`, `PutInPlanSheet`, Journal Workout Detail) open in `ModalBottomSheet`.

 | `16.dp` horizontal padding; cards fill `100%` width. |
| **Medium** (Tablets Portrait, Unfolded Foldables) | `600.dp <= width < 840.dp` | `NavigationRail` (`80.dp` width, pinned to `Start` edge, vertically centered icons).

 | **2-Column Bento Grid** (`LazyVerticalStaggeredGrid(StaggeredGridCells.Fixed(2), spacedBy(16.dp))`) on `HomeScreen` and `ProgressScreen`. Single-column centered (`maxWidth = 640.dp`) on `LiveWorkoutScreen` and `CoachScreen`.

 | `24.dp` horizontal padding. |
| **Expanded** (Tablets Landscape, Desktop PWA) | `width >= 840.dp` | `NavigationRail` (`80.dp` width, pinned to `Start` edge; **no `NavigationDrawer**`). | **Canonical `ListDetailPaneScaffold` (`40%` Start Pane / `60%` End Pane):**<br>

<br>• `WorkoutsScreen` (Start) + `PlanEditorScreen` / `ExerciseDetailScreen` (End)<br>

<br>• `HistoryScreen` Journal list (Start) + Workout Detail Pane (End, replacing bottom sheet)<br>

<br>• `FoodScreen` Meal Log (Start) + Search/Library & Macro Rings (End)<br>

<br>• `ProgressScreen` Charts & Heatmap (Start) + Interactive `BodyMap` & Muscle Sheet (End).

 | `32.dp` outer padding, `24.dp` pane gap, `maxWidth = 1200.dp`. |

#### Live Workout Landscape Mode (Compact Height / Landscape Phone)

* **Rule:** When `windowSizeClass.heightSizeClass == WindowHeightSizeClass.Compact` on `LiveWorkoutScreen`, switch from a vertical stack to a **2-Pane Side-by-Side Split (`Row`)**:


* **Start Pane (`weight(0.42f)`):** Sticky Exercise Title, target muscle pill, active Rest Timer card (`LinearWavyProgressIndicator`), one-tap Warm-Up chip, and the `Finish` button.


* **End Pane (`weight(0.58f)`):** Full-height `LazyColumn` of set rows (`Stepper`s + Done tick + Effort row) so the user can tick sets without scrolling past the header.





#### Edge-to-Edge & IME (Keyboard) Insets

* Call `enableEdgeToEdge()` in `MainActivity.onCreate()` with `SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT)`.


* Apply `contentWindowInsets = WindowInsets.safeDrawing` on the root `Scaffold` in `AppRoot.kt`.


* On `CoachScreen` (bottom chat bar) and `FoodScreen` / `ExercisePicker` (`DockedSearchBar`), apply `Modifier.imePadding()` so the input bar rides smoothly on top of the software keyboard at 60/120fps without jumping.



---

### 5.2 Accessibility (`TalkBack`, Focus, 200% Text & Touch Targets)

#### 1. Custom Component `TalkBack` Semantics

* `ConcentricRings` & `MiniRing` (`Rings.kt`):


* Apply `Modifier.semantics(mergeDescendants = true) { contentDescription = ...; stateDescription = ... }` on the outer container and `Modifier.clearAndSetSemantics {}` on the raw `Canvas`.
* Format `stateDescription` in natural order: `"Readiness 84 percent, Calories 1,850 of 2,200, Workouts 3 of 4 completed"`.




* `NumberStepper` (`Common.kt`):


* Sighted touch users get three distinct `48.dp × 48.dp` hit targets: `-` button, center value (opens `NumberEntryDialog`), and `+` button.


* For `TalkBack`, attach `setProgress` on the parent wrapper (`Modifier.semantics { stateDescription = "$value $unit"; setProgress { target -> onValueChange(target); true } }`) so screen-reader users can adjust weight/reps with a single vertical swipe gesture while keeping the center label double-tappable for direct keyboard entry.




* Charts (`Sparkline`, `TrendChart`, `BarRow`, `Heatmap` in `Charts.kt`):


* Never expose 30+ individual canvas bars to linear `TalkBack` swipe navigation.
* Set `Modifier.semantics(mergeDescendants = true)` on the chart card with a computed summary: e.g., `"Volume trend over 4 weeks: up 8 percent, average 12,400 kilograms per week"`.




* `BodyMap` (`BodyMap.kt`):


* Hide the raw vector/canvas polygon paths from `TalkBack` (`clearAndSetSemantics {}`) and expose an accessible horizontal `LazyRow` of `FilterChip`s directly below the figure (`Chest: Fresh`, `Back: Recovering 60%`, `Legs: Sore 30%`) where each chip opens `MuscleExercisesSheet`.





#### 2. Removing the `1.3x` Font Scale Cap (Zero Clipping at `200%`)

* **Problem Today:** Tight rows cap font scale at `1.3x`, violating the "nothing clipped at 200% text" UX rule.


* **Solution:** Remove the `Density(fontScale = fontScale.coerceAtMost(1.3f))` cap. Instead, branch layout structure at **`LocalDensity.current.fontScale >= 1.35f`**:
* `LiveWorkoutScreen` Set Row:


* *At `< 1.35f` (Standard):* Single horizontal row: `[Set #] [Weight Stepper] [Reps Stepper] [Done Tick]`.
* *At `>= 1.35f` (Large / `200%`):* 2-tier card row: Top line holds `[Set # Badge] + [Previous Hint] + [Done Tick (48.dp)]`; Bottom line stacks `[Weight Stepper (full width)]` and `[Reps Stepper (full width)]` via `FlowRow`.


* `ProfileScreen` & `HomeScreen` Stat Rows: Replace fixed-height `Row` with `FlowRow` and `Modifier.heightIn(min = 56.dp)` (never `Modifier.height(...)`) so titles and subtitles wrap cleanly to 2–3 lines.





#### 3. Touch Targets (`>= 48.dp`) & Focus Order

* Every interactive control (`SetTickCheckbox`, `Stepper` `-`/`+`, `ChoiceChip`, `AssistChip`, `DeltaChip` when clickable, `PlanEditorScreen` drag handle) enforces `Modifier.minimumInteractiveComponentSize()` (`48.dp × 48.dp` minimum physical hit box) even when its visual box is smaller (e.g., a `32.dp` visual chip or `40.dp` visual set checkbox).


* Use `Modifier.focusProperties` and `FocusRequester` in `LiveWorkoutScreen` so completing a set moves keyboard/D-pad focus to the Effort row (`Easy/OK/Hard/Pain`) and then to the next set's Done tick.



---

### 5.3 RTL Mirroring Rules & Western Digits (`Locale("ar")`)

#### 1. Western Arabic Numerals (`0–9`) Enforcement

* **Rule:** All numbers across English and Arabic use Western digits (`0123456789`).


* **Implementation:** In `AppLocale.kt`, construct the Arabic locale with the Unicode numbering extension `Locale.forLanguageTag("ar-SA-u-nu-latn")`, and format all weights, reps, timers, calories, and percentages via a shared `ForgeFormat.number(...)` helper using `DecimalFormatSymbols(Locale.US)`.



#### 2. RTL Mirroring Matrix (What Flips vs. What Stays LTR)

| UI Element / Component | Behavior in Arabic (`LayoutDirection.Rtl`) | Implementation Rule in Compose |
| --- | --- | --- |
| **Screen Transitions (Push / Pop)** | **Flips** (Push enters from Left; Pop/Back exits to Left).

 | Use `AnimatedContentTransitionScope.SlideDirection.Start` / `End` (never `Left` / `Right`). |
| **Navigation Icons (Back arrow, Chevrons, Send, Logout, Undo)** | **Flips** horizontally.

 | Use `Icons.AutoMirrored.Rounded.ArrowBack`, `ArrowForward`, `Send`, `Undo`, `KeyboardArrowRight`. |
| **Set Done Tick Checkmark (`✓`)** | **DOES NOT FLIP** (Checkmark short stroke is always on the left, long stroke on the right). | Draw checkmark path in fixed LTR coordinates inside `Canvas`. |
| **Clocks, Rest Timers (`01:30`) & Circular Rings (`ConcentricRings`)** | **DOES NOT FLIP** (Time and circular progress always run clockwise from `12 o'clock` / `-90°`). | Keep `drawArc(startAngle = -90f, sweepAngle = +progress * 360f)` identical in LTR and RTL; wrap `"01:30"` in `CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr)`. |
| **Linear Progress Bars (`ProgressLine`, Macro Bars, Wavy Rest Bar)** | **Flips** (Fills from Right-to-Left in Arabic).

 | Standard M3 `LinearProgressIndicator` and `LinearWavyProgressIndicator` respect `LocalLayoutDirection.current` automatically. |
| **Time-Series Charts (`Sparkline`, `TrendChart`)** | **Flips** (Oldest date on Right $\rightarrow$ Today on Left, matching reading order) OR stays LTR if labeled with explicit dates; **Pick: Flip X-axis in RTL** so "Today" is always at the `End` edge. | Map index `i` to X-coordinate using `Start` $\rightarrow$ `End` layout direction so `Today` aligns with the trailing edge in both languages. |
| **Horizontal Bar Rows (`BarRow`) & `Heatmap**` | **Flips** (Label on Right, bar grows toward Left; Week 1 on Right $\rightarrow$ Current Week on Left).

 | Standard Compose `Row` / `LazyRow` handles `Start`-to-`End` mirroring automatically. |
| **`NumberStepper` (`-` `82.5` `+`)** | **Keep `-` on Left, `+` on Right** (or `-` on `Start`, `+` on `End`); **Pick: Keep `-` on `Start` (Right in AR) and `+` on `End` (Left in AR)**, while keeping the numeric string `82.5 kg` LTR. | Standard `Row` mirrors `-`/`+` naturally with reading progression, while `tnum` digits stay LTR. |
| **`SwipeToDelete` (`HistoryScreen`, `FoodScreen`)** | **Flips** (`StartToEnd` / `EndToStart` mirrors automatically).

 | Use `SwipeToDismissBoxValue.EndToStart` (swiping from Left-to-Right in Arabic, Right-to-Left in English) for destructive delete.

 |

---

*Ready for **Phase 2B** (**Section 6: Screen-by-Screen Transformation Plan** covering layout, components, motion, and Before $\rightarrow$ After for all 9 screens). Reply **"continue"** or **"send phase 2b"** whenever you are ready.*
</pasted_content id="6342">

<pasted_content id="6342">
Bottom Line Up Front (BLUF)

* Phase 2B Scope: This response delivers Section 6 (Screen-by-Screen Transformation Plan), ordered from your highest-frequency core loops (`LiveWorkoutScreen`, `HomeScreen`, `FoodScreen`) to secondary management screens (`WorkoutsScreen`, `ProgressScreen`, `HistoryScreen`, `CoachScreen`, `ProfileScreen`, `OnboardingScreen`).

* Core Loop Upgrade (`LiveWorkoutScreen` & `HomeScreen`): Today's workout card on `HomeScreen` gains a Split Button (`Start Workout` + plan-swap chevron) that expands via a Shared Container Transform into `LiveWorkoutScreen`, where set rows use a morphing Square $\rightarrow$ Circle/Sunny-Star Done Tick, odometer Number Steppers, a Connected Effort Group (`Easy/OK/Hard/Pain`), and a bottom Floating Toolbar with a Wavy Rest Timer.

* Zero-Clutter Logging (`FoodScreen` & `HistoryScreen`): `FoodScreen` replaces separate search steps with a persistent pill `DockedSearchBar`, a scroll-collapsing `ExtendedFloatingActionButton` (`+ Scan / Log`), and removable `InputChip`s for meal parts (`FoodParts.kt`), while `HistoryScreen` adds threshold-popping `SwipeToDismissBox` rows that expand into a `ModalBottomSheet` (or side pane on tablets).

* 200% Font-Scale Reflow Everywhere: Every screen removes the `1.3x` font-scale cap and switches to `FlowRow` or 2-tier vertical card stacks when `LocalDensity.current.fontScale >= 1.35f`, ensuring zero clipped text in both English and RTL Arabic.

6. Screen-by-Screen Transformation Plan
Below is the exact engineering blueprint for all 9 screens and the root navigation shell, ordered by daily user impact.


6.1 Live Workout & Finish Debrief (`LiveWorkoutScreen.kt`, `Debrief.kt`, `WorkoutScore.kt`)

* Layout & Surface Architecture:

   * Canvas: `surface` (`#131314` dark / `#F6F8FC` light) with a pinned compact `TopAppBar` (`surfaceContainer` on scroll) showing elapsed workout time (`tnum`), an `OutlinedButton` (`Swap Exercise`), and a subtle close/minimize icon.

   * Exercise Cards: `FitCard` at `surfaceContainer` (`#1E1F20`, `20.dp` corners).

   * Set Rows: Nested inside each exercise card with `12.dp` concentric corners:

      * Upcoming set: `surfaceContainerLow` (`#18191A`).

      * Current active set: `surfaceContainerHigh` (`#282A2C`) with a `1.dp` `outlineVariant` border.

      * Completed set: `primaryContainer` (`#14352F` Teal track).

      * PR set: `tertiaryContainer` (`#382D12` Gold track) with a `1.dp` `#FDD663` border.

   * 200% Text Reflow: At `fontScale < 1.35f`, each set row is a single horizontal `Row`: `[Set Badge 32.dp] [Weight Stepper] [Reps Stepper] [Done Tick 48.dp]`. At `fontScale >= 1.35f`, it switches to a 2-tier column: Top row `[Set Badge + Last History + Done Tick]`, Bottom row `[Weight Stepper] + [Reps Stepper]`.

   * Landscape (`Compact` Height): Splits into a 2-column `Row` (`42%` Start pane for exercise header + rest timer + `Finish`; `58%` End pane for set rows).

* Component Upgrades:

   * One-Tap Warm-Ups: Replace plain pill with M3 `AssistChip` (`secondaryContainer` `#172B4A`, leading `+` icon that rotates into a checkmark when tapped).

   * Weight & Reps Steppers (`NumberStepper`): Recessed center well (`surfaceContainerLowest` `#0E0E0F`, `8.dp` radius) flanked by `48.dp` `FilledTonalIconButton`s (`-` / `+`). Tapping the center value opens `NumberEntryDialog` (`AlertDialog`, `28.dp` corners).

   * Set Done Tick: Custom `SetTickCheckbox` (`48.dp` touch target, `40.dp` visual box) that morphs from a `10.dp` rounded square (unchecked) $\rightarrow$ `4.dp` squircle (pressed) $\rightarrow$ `50%` circle (checked), or into an 8-point `MaterialShapes.Sunny` gold badge on a PR.

   * Effort Selector (`Easy / OK / Hard / Pain`): Connected M3 Expressive `ButtonGroup` / `ConnectedToggleGroup` revealed directly beneath the active/last-completed set row. Outer ends have `20.dp` corners; inner seams have `6.dp` corners that morph to `50%` (`20.dp` full pill) when selected, tinted by effort (`Teal` / `Blue` / `Purple` / `Coral`).

   * Rest Timer & Finish Bar: Replace static bottom bar with an M3 Expressive `HorizontalFloatingToolbar` (`surfaceContainerHighest` `#333537`, `CircleShape`, `6.dp` shadow) floating `16.dp` above the navigation bar. Houses the tabular countdown (`01:30`), a `LinearWavyProgressIndicator` (or flat fallback on 1.3.x), `+15s` / `Skip` `IconButton`s, and the morphing `PillButton` (`Finish`).

   * Finish Screen & Debrief Card: Hero score ring (`ConcentricRings` single arc) + `displayLarge` (`52.sp` `tnum`) score (`4–10`), followed by 4 `surfaceContainer` debrief cards (`Headline`, `Wins`, `Watch`, `Next Time`) with leading `IconCircle`s (`Teal`, `Gold`, `Coral`, `Blue`).

* Motion & Haptics:

   * Ticking a set fires `fastSpatial` (`spring(0.6f, 800f)`) corner morph + `EFFECT_CLICK` haptic (or double-thud waveform + single gold radial shimmer on PR).

   * Tapping `-`/`+` rolls digits vertically via `fastSpatial` + `TextHandleMove` micro-tick.

   * Tapping `Finish` sweeps the score ring (`650ms` `EmphasizedDecelerate`), counts up the score (`4.0` $\rightarrow$ `8.5`), fires `EFFECT_HEAVY_CLICK`, and staggers the 4 debrief cards in `70ms` apart via `defaultSpatial`.

* Before $\rightarrow$ After (In Words):

   * Before: Set rows, effort chips, and the rest timer sit in flat, uniform containers where ticking a set or hitting a PR feels like toggling a static form checkbox.

   * After: Every set tick physically squishes and snaps into a teal circle (or a shimmering gold starburst on a PR), weight increments roll like a mechanical counter, effort chips expand into a connected pill, and the rest timer floats in a thumb-friendly pill toolbar with a live wavy progress bar.


6.2 Home Screen (`HomeScreen.kt`, `CalorieCard.kt`, `ReadinessCard.kt`, `ReadinessDetailScreen.kt`, `UpdateCard.kt`)

* Layout & Surface Architecture:

   * Top Bar: Collapsing `MediumTopAppBar` (`exitUntilCollapsedScrollBehavior`) with greeting/date on the `Start` side and a `48.dp` Profile avatar + notification/update dot on the `End` side.

   * Grid: Single-column `LazyColumn` (`spacedBy(12.dp)`) on Compact phones; 2-column Bento `LazyVerticalStaggeredGrid` on Medium/Expanded tablets.

   * Card Hierarchy:

      * Today's Workout Card: Elevated priority at `surfaceContainerHigh` (`#282A2C` dark / `#E2E8F0` light) with a `1.dp` `primaryContainer` border accent.

      * Activity / Readiness Card & Calorie Card: Standard `surfaceContainer` (`#1E1F20`, `20.dp` corners).

      * Setup / Update Notice (`UpdateCard.kt`): `surfaceContainerLow` (`#18191A`) with a leading `tertiaryContainer` badge.

* Component Upgrades:

   * Pull-to-Refresh: Wrap `HomeScreen` body in M3 `PullToRefreshBox` using the 7-shape morphing `LoadingIndicator` to trigger `DriveSync` and `HealthRepository` refresh (`RefreshWatch.kt`).

   * Today's Workout CTA: Upgrade the `Start` `PillButton` into an M3 Expressive Split Button (`SplitButtonLayout` or `Row(spacedBy(2.dp))`): the main pill (`Start Workout`) launches `LiveWorkoutScreen` in one tap, while the trailing chevron opens a compact `DropdownMenu` (`Swap Day`, `Switch Active Plan`, `Preview Exercises`).

   * Readiness & Activity Rings (`ConcentricRings`): Add M3 circular track gaps (`4.dp` gap between arc end and background track) with rounded stroke caps (`StrokeCap.Round`). Tapping `ReadinessCard` transitions via Shared Axis Z into `ReadinessDetailScreen`.

   * Calorie & Macro Cards (`CalorieCard.kt`, `CalorieCards.kt`): Hero remaining calories in `displaySmall` (`32.sp` `tnum`), macro bars (`ProgressLine`) upgraded to M3 `LinearProgressIndicator` with `4.dp` track gap and rounded caps (`Blue` protein, `Gold` carbs, `Purple` fat), plus a `DeltaChip` (`8.dp` radius) for target nudges (`TargetNudge.kt`).

* Motion & Haptics:

   * Tapping `Start Workout` expands the Today Card directly into `LiveWorkoutScreen` via `SharedTransitionLayout` (`slowSpatial` $\zeta=0.8, k=200$).

   * On initial load or pull-to-refresh completion, `ConcentricRings` sweep in (`650ms` `EmphasizedDecelerate`, staggered `60ms` per ring) and calorie numbers interpolate smoothly.

* Before $\rightarrow$ After (In Words):

   * Before: `HomeScreen` is a static stack of equally weighted dark cards where switching today's workout requires navigating away to the Workouts tab, and rings draw without track separation.

   * After: Today's workout stands out on a higher tonal surface with a one-tap Split Button that morphs full-screen into the live workout, rings sweep in with M3 track gaps, and pulling down refreshes Health Connect/Drive with an expressive morphing polygon.


6.3 Food Log, Search & AI Photo Scan (`FoodScreen.kt`, `FoodParts.kt`, `ScanScreen.kt`)

* Layout & Surface Architecture:

   * Top Search Header: Pinned `DockedSearchBar` (`surfaceContainerHigh`, `CircleShape` when collapsed, `16.dp` card when expanded) at the top of `FoodScreen`.

   * View Switcher: M3 `SecondaryTabRow` directly below the search bar for switching between `Log`, `Library`, and `Targets`.

   * Expanded Tablet Split: On Expanded screens (`>= 840.dp`), `Log` + Macro Rings sit in the Start pane (`45%`) while `Search / Library / Scan` stays open in the End pane (`55%`).

* Component Upgrades:

   * Inline Search (`DockedSearchBar`): Typing filters `FoodSearch.kt` results inline without pushing a new screen, with a row of horizontal `SuggestionChip`s below the input for 1-tap recent/frequent foods.

   * Meal Breakdown Chips (`FoodParts.kt`): Render individual ingredients/parts of a logged meal (e.g., `"Paratha × 2"`, `"Eggs × 3"`, `"Ghee 1 tbsp"`) as M3 `InputChip`s (`8.dp` corners) with a trailing `×` icon to remove a part or tap to adjust its multiplier via `NumberEntryDialog`.

   * Primary Action (`ExtendedFloatingActionButton`): Floating bottom-end button (`+ Scan Meal` with camera/sparkle icon) that collapses to a circular `FloatingActionButton` when scrolling down the food log.

   * AI Photo Scan (`ScanScreen.kt`): While `GeminiClient` / `GroqClient` analyzes the meal photo, overlay the M3 Expressive `LoadingIndicator` (`SoftBurst` $\rightarrow$ `Sunny` polygon morph) inside a `surfaceContainerHighest` pill over the image preview.

   * Swipe-to-Delete: Meal log rows use `SwipeToDelete` (`SwipeToDismissBox`) paired with `UndoSnackbarEffect`.

* Motion & Haptics:

   * Removing or adding a meal part chip animates sibling chips via `animateItem(placementSpec = defaultSpatial)` and smoothly morphs the macro progress bars (`ProgressLine`) and `CalorieCard` total.

* Before $\rightarrow$ After (In Words):

   * Before: Searching, logging, and editing meal parts feel like separate form states with plain pill tags and a standard spinner during photo analysis.

   * After: A persistent `DockedSearchBar` and recent `SuggestionChip`s let you log food in one tap, meal components behave as interactive M3 `InputChip`s, and AI meal scanning uses Google's expressive morphing `LoadingIndicator`.


6.4 Workouts, Plan Editor & Exercise Library (`WorkoutsScreen.kt`, `PlanEditorScreen.kt`, `ExerciseLibraryScreen.kt`, `ExerciseDetailScreen.kt`, `ExercisePicker.kt`, `PutInPlanSheet.kt`)

* Layout & Surface Architecture:

   * Plans Overview (`WorkoutsScreen`): `MediumTopAppBar` with a `+ New Plan` `FilledTonalButton`. Active plan card uses `surfaceContainerHigh` (`#282A2C`) with a `primary` (`#5CD6C0`) `2.dp` border and `Active` `Badge`; other plans use `surfaceContainer` (`#1E1F20`).

   * Plan Editor (`PlanEditorScreen`): Day tabs at the top using `SecondaryScrollableTabRow`, followed by a `LazyColumn` of draggable exercise cards (`surfaceContainer`, `12.dp` corners).

   * Adaptive Tablet View: `ListDetailPaneScaffold` shows the Plan / Library list on the `Start` pane and `PlanEditorScreen` or `ExerciseDetailScreen` on the `End` pane.

* Component Upgrades:

   * Plan Card Actions: Primary `FilledTonalButton` (`Edit Plan`) + overflow `IconButton` opening an M3 `DropdownMenu` (`Set Active`, `Duplicate`, `Safety Check`, `Delete`).

   * Plan Safety Banner (`PlanSafety.kt`): If a plan has volume/muscle imbalances, render a collapsible `surfaceContainerLow` banner with a leading `tertiary` (`#FDD663`) or `error` (`#F28B82`) icon and one-tap `AssistChip` fixes.

   * Drag Handle (`PlanEditorScreen`): Explicit `48.dp × 48.dp` `Icon(Icons.Rounded.DragHandle)` on the `Start` edge of every exercise row, plus inline `NumberStepper`s for target sets/reps.

   * Exercise Library & Picker (`ExerciseLibraryScreen.kt`, `ExercisePicker.kt`): Pinned `DockedSearchBar` + a horizontal `LazyRow` of M3 `FilterChip`s for muscle groups (`Chest`, `Back`, `Legs`, `Shoulders`, `Arms`, `Core`) and tier badges (`ExerciseTiers.kt`).

   * Add to Plan (`PutInPlanSheet.kt`): Opens in a `ModalBottomSheet` (`28.dp` top corners, `BottomSheetDefaults.DragHandle()`).

* Motion & Haptics:

   * Grabbing a drag handle in `PlanEditorScreen` triggers `HapticFeedbackType.LongPress`, scales the row `1.0f` $\rightarrow$ `1.03f`, elevates shadow `0.dp` $\rightarrow$ `8.dp`, morphs corners `12.dp` $\rightarrow$ `20.dp`, and shifts background to `surfaceContainerHighest` (`#333537`) via `fastSpatial`. Crossing each neighbor slot fires a `TextHandleMove` micro-tick, and dropping snaps into place with `defaultSpatial` + `EFFECT_TICK`.

* Before $\rightarrow$ After (In Words):

   * Before: Reordering exercises in the plan editor moves flat rows with minimal depth feedback, and plan actions clutter the card face.

   * After: Dragged exercises physically lift off the canvas into a higher tonal surface with corner morphing and slot-by-slot haptic ticks, while muscle filtering uses standard M3 `FilterChip`s with animated leading checkmarks.


6.5 Progress, Charts & Muscle Recovery (`ProgressScreen.kt`, `Charts.kt`, `BodyMap.kt`, `MuscleExercisesSheet.kt`)

* Layout & Surface Architecture:

   * Top Bar & Time Filter: `MediumTopAppBar` paired with a sticky full-width Connected Toggle Group (`7D` / `30D` / `90D` / `All`) pinned below the header.

   * Bento Grid on Medium/Expanded: `Consistency Heatmap`, `Volume TrendChart`, `PR Sparklines`, and `BodyMap` arrange into a 2-column Bento grid on tablets.

* Component Upgrades:

   * Charts (`Sparkline`, `BarRow`, `TrendChart`): Recessed plot area (`surfaceContainerLowest` `#0E0E0F`, `12.dp` radius) inside `FitCard` (`surfaceContainer` `#1E1F20`). `BarRow` uses `4.dp` (`shapes.extraSmall`) rounded bar caps; `TrendChart` adds an interactive touch-scrubbing vertical cursor line with a floating `PlainTooltip` (`inverseSurface`) showing exact date + value (`tnum`).

   * Consistency Heatmap (`Heatmap`): `4.dp` rounded cells using stepped Teal opacity (`#14352F` $\rightarrow$ `#5CD6C0`) for workout days and `#382D12` $\rightarrow$ `#FDD663` (Gold) for PR days.

   * Muscle Recovery Figure (`BodyMap.kt` & `MuscleExercisesSheet.kt`):

      * Interactive Anterior / Posterior switch using a 2-segment `ConnectedToggleGroup` (`Front` / `Back`).

      * Muscle regions tint smoothly across `error` Coral (`#F28B82` sore/fatigued) $\rightarrow$ `Purple` (`#C58AF9` recovering) $\rightarrow$ `primary` Teal (`#5CD6C0` fresh).

      * Directly below the figure, add an accessible horizontal `LazyRow` of M3 `FilterChip`s for each muscle group (`Chest 95%`, `Back 60%`, etc.) so both touch and `TalkBack` users can tap any muscle to open `MuscleExercisesSheet` (`ModalBottomSheet`).

* Motion & Haptics:

   * Switching `7D / 30D / 90D` morphs the active toggle button into a full pill (`fastSpatial`) and sweeps/redraws `TrendChart` and `BarRow` (`500ms` `EmphasizedDecelerate`).

   * Scrubbing across `TrendChart` or tapping a muscle on `BodyMap` fires `TextHandleMove` / `EFFECT_TICK`.

* Before $\rightarrow$ After (In Words):

   * Before: Charts and the body map are static visual blocks, and `TalkBack` users cannot easily inspect individual muscle recovery percentages.

   * After: Time-range toggles morph on tap, charts draw in smoothly with touch-scrubbing tooltips, and the `BodyMap` pairs its recovery figure with accessible muscle chips that open `MuscleExercisesSheet`.


6.6 Journal / Workout History (`HistoryScreen.kt`, `SwipeToDelete.kt`, `UndoSnackbarEffect.kt`)

* Layout & Surface Architecture:

   * Weekly Grouping: Each week is wrapped in a `surfaceContainerLow` (`#18191A` dark / `#F0F4F9` light, `20.dp` corners) section card with a sticky/collapsible week header (`Week of Oct 26 • 4 workouts • 38,400 kg` in `tnum`).

   * Workout Rows: Individual logged workouts inside the week group sit on `surfaceContainer` (`#1E1F20`, `12.dp` concentric corners) using M3 `ListItem` structure: Leading `IconCircle` (workout type/score badge), Headline (workout title), Supporting text (duration, volume, exercises summary), and Trailing PR `DeltaChip` + chevron.

* Component Upgrades:

   * Swipe-to-Delete (`SwipeToDelete.kt`): Uses M3 `SwipeToDismissBox` (`enableDismissFromStartToEnd = false`, `enableDismissFromEndToStart = true`, automatically mirrored in RTL). Background reveals `errorContainer` (`#3C1D1B`) with `onErrorContainer` (`#F9DEDC`) trash icon.

   * Undo Snackbar (`UndoSnackbarEffect.kt`): Floating M3 `Snackbar` (`inverseSurface` `#E3E3E3`, `inverseOnSurface` `#1E1F20`, action color `inversePrimary` `#006B5A`) anchored above the bottom `NavigationBar`.

   * Workout Detail Sheet: Tapping a row opens a `ModalBottomSheet` (`28.dp` top corners) on Compact phones, or populates the End detail pane in `ListDetailPaneScaffold` on Expanded tablets.

* Motion & Haptics:

   * Swiping a row past the `40%` dismiss threshold pops the trash icon scale (`0.8f` $\rightarrow$ `1.25f` $\rightarrow$ `1.0f` via `fastSpatial`) and fires `EFFECT_TICK` so the user knows releasing will delete. Tapping `Undo` restores the row into the week list via `animateItem(placementSpec = defaultSpatial)`.

   * Tapping a row expands it into the Detail Sheet via container transform (`defaultSpatial`).

* Before $\rightarrow$ After (In Words):

   * Before: Journal rows sit in a flat list with basic swipe dismissal and open a sheet without spatial continuity.

   * After: Weeks are cleanly grouped in recessed tonal containers, swiping to delete gives a physical threshold pop + haptic tick with one-tap Undo, and tapping a workout smoothly expands the row into the detail sheet.


6.7 AI Coach (`CoachScreen.kt`, `MarkdownText.kt`, `CoachRepository.kt`)

* Layout & Surface Architecture:

   * Header: Compact pinned `TopAppBar` with Coach status, active model pill (`Gemini` / `Groq` from `ModelStore.kt`), and a clear/reset overflow menu.

   * Message Stream (`LazyColumn`):

      * User Bubble: Aligned `End`, `secondaryContainer` (`#172B4A` dark / `#D3E3FD` light), corners `topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 4.dp` (automatically mirrored in RTL).

      * Coach Assistant Bubble: Aligned `Start`, `surfaceContainerLow` (`#18191A`), corners `topStart = 16.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 4.dp`.

      * Plan-Change Proposal Card (`PlanDraft.kt`): Nested inside or below the assistant message on `surfaceContainerHigh` (`#282A2C`, `16.dp` corners) with a `1.dp` `ForgeSemanticColors.purple` (`#C58AF9`) border.

   * Bottom Input Dock: Sticky bottom surface (`surfaceContainer`, `Modifier.imePadding()`) with a horizontal row of contextual `SuggestionChip`s (`"Swap today for bodyweight"`, `"Adjust calories"`, `"Explain my readiness"`), a pill-shaped `OutlinedTextField` (`24.dp` radius), and an auto-mirrored `FilledIconButton` (`Send`).

* Component Upgrades:

   * Thinking State: Replace static text/spinner with M3 Expressive `LoadingIndicator` (`28.dp` size, tinted `purple` `#C58AF9`) inside a compact assistant bubble.

   * Plan-Change Card Actions: Diff rows use `DeltaChip`s (`Teal` for added exercises, `Coral` for removed, `Blue` for rep/weight changes) with a bottom action row containing a morphing `FilledTonalButton` (`Apply to Plan`) and `OutlinedButton` (`Dismiss`).

* Motion & Haptics:

   * Sending a message pops the user bubble (`fastSpatial`) + `EFFECT_TICK`.

   * Markdown paragraphs in `MarkdownText.kt` fade in smoothly (`180ms` `EmphasizedDecelerate`), and tapping `Apply to Plan` morphs the button into a checkmark pill + fires `EFFECT_CLICK`.

* Before $\rightarrow$ After (In Words):

   * Before: The AI chat feels like a basic text log with a generic loading state and plain buttons on plan-change cards.

   * After: The Coach feels like Google's flagship Gemini app—complete with an expressive morphing polygon while thinking, rich purple-tinted plan-diff cards with `DeltaChip` highlights, and keyboard-hugging `imePadding()` motion.


6.8 Profile, Settings & Badges (`ProfileScreen.kt`, `BadgesScreen.kt`, `SettingsRepository.kt`)

* Layout & Surface Architecture:

   * Top Bar: `MediumTopAppBar` with title and app version badge.

   * Grouped Settings Cards (`ProfileScreen.kt`): Keep your signature pattern—a card with leading `IconCircle`, title, and subtitle, with a full-width segmented control below where applicable—and organize them into logical `surfaceContainer` (`#1E1F20`, `20.dp` corners) cards:

      1. Preferences Card: Language (`English` / `العربية`), Units (`kg` / `lb`), and Theme (`Dark` / `Light` / `System`) using full-width Connected Toggle Groups (`SingleChoiceSegmentedButtonRow` / Expressive `ButtonGroup`).

      2. Health & Sync Card: Health Connect status (`HealthRepository.kt`), Google Drive Backup (`DriveSync.kt`), and Gemini Bridge (`GeminiBridge.kt`) using M3 `ListItem` + M3 `Switch` (replacing custom `FitSwitch`).

      3. AI Model & Keys Card: `GeminiClient` / `GroqClient` selector and API key dialog trigger.

   * 200% Text Reflow: Every card header (`IconCircle` + Title + Subtitle) uses `Row` with `Modifier.heightIn(min = 56.dp)`, while the full-width segmented control sits in its own row below so labels never truncate even at `200%` font scale in Arabic.

* Component Upgrades:

   * Switches (`FitSwitch` $\rightarrow$ M3 `Switch`): Standard M3 `Switch` with thumb icon (`Icons.Rounded.Check` when checked) and automatic thumb diameter expansion on press.

   * Badges Showcase (`BadgesScreen.kt`): Upgrade badge icons from simple circles to M3 Expressive Polygon Shapes (`MaterialShapes.Sunny` for streak badges, `Cookie9Sided` for volume milestones, `SoftBurst` for consistency badges) rendered in Gold `#FDD663` (`tertiary`) on `#382D12` (`tertiaryContainer`) cards for unlocked badges, and `surfaceContainerLow` with `outlineVariant` borders for locked badges.

* Motion & Haptics:

   * Toggling a segmented button (e.g., `English` $\leftrightarrow$ `العربية` or `Dark` $\leftrightarrow$ `Light`) morphs the selected segment into a full pill (`fastSpatial` $\zeta=0.6, k=800$) + `EFFECT_TICK`, and smoothly crossfades the app theme (`slowEffects`).

   * Tapping an unlocked badge in `BadgesScreen` triggers a playful spring rotation/scale pop (`fastSpatial`).

* Before $\rightarrow$ After (In Words):

   * Before: Settings use custom switches and static segmented tabs, and badges use uniform circular icons.

   * After: Full-width segmented controls physically morph into pills when tapped, M3 switches expand their thumbs with checkmarks, and `BadgesScreen` showcases distinct M3 Expressive polygon medals in gold.


6.9 Onboarding, App Tour & Root Navigation (`OnboardingScreen.kt`, `AppTour.kt`, `AppRoot.kt`)

* Layout & Surface Architecture:

   * Onboarding (`OnboardingScreen.kt` - 4 Steps): Top pinned progress header with an M3 `LinearProgressIndicator` (`4.dp` gap between segments or continuous smooth fill for Steps `1/4` $\rightarrow$ `4/4`), centered `headlineLarge` question title, selectable option cards (`surfaceContainer` $\rightarrow$ `primaryContainer` when selected), and a sticky bottom morphing `PillButton` (`Continue`).

   * App Tour (`AppTour.kt` - 3 Slides): Replace standard static slides with an M3 `HorizontalMultiBrowseCarousel` (or `HorizontalPager` with animated pill indicators) showcasing interactive previews of the Rings, Live Workout Set Tick, and AI Coach.

   * Root Navigation (`AppRoot.kt`): `NavigationSuiteScaffold` wrapping the top-level `NavHost` (`navigation-compose 2.8.7`), switching automatically between `NavigationBar` (`Compact`) and `NavigationRail` (`Medium`/`Expanded`).

* Component Upgrades:

   * Onboarding Option Cards: Use M3 `OutlinedCard` (`20.dp` corners) with a trailing M3 `RadioButton` (single-choice steps like Goal) or M3 `Checkbox` (multi-choice steps like Equipment).When selected, the card border thickens to `2.dp` `primary` (`#5CD6C0`), background shifts to `primaryContainer` (`#14352F`), and corners morph `20.dp` $\rightarrow$ `24.dp`.

   * Predictive Back: `NavHost` and all sheets wire into `PredictiveBackHandler` so swiping back between onboarding steps or screens previews the previous destination with a `90%` scale + `28.dp` rounded-corner card shrink.

* Motion & Haptics:

   * Stepping `1` $\rightarrow$ `2` $\rightarrow$ `3` $\rightarrow$ `4` uses Shared Axis X (RTL-aware) via `defaultSpatial` (`spring(0.7f, 380f)`) while the top progress bar smoothly interpolates (`25%` $\rightarrow$ `50%` $\rightarrow$ `75%` $\rightarrow$ `100%`) and fires `EFFECT_TICK`.

* Before $\rightarrow$ After (In Words):

   * Before: Onboarding steps and the 3-slide tour advance with basic slides, and navigation is fixed to a phone-only bottom bar.

   * After: Option cards morph corners on selection, tour slides use an expressive M3 carousel with pill indicators, predictive back previews previous screens smoothly, and tablets/foldables automatically switch to a `NavigationRail`.

Ready for Phase 3 (Final Phase) covering Section 7 (Ready-to-Paste Compose Code: `Theme.kt`, `ForgeMotion.kt`, and the 8 Core Components), Section 8 (Preact PWA / `@material/web` Parity Table), Section 9 (Performance & Risk Notes), and Section 10 (Phased Rollout S/M/L). Reply "continue" or "send phase 3" whenever you are ready.
</pasted_content id="6342">
