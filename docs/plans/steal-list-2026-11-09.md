# What to take from Bevel, Vora and the open-source apps (2026-11-09)

Source: the Gemini conversation, not independently verified. Claims about repos, star counts and licences need a check before relying on them. Formulas and product flows are fair to reimplement; do not copy source from AGPL/GPL repos if ForgeQuest stays closed.

Rule of 3 (from the video notes): the main loops are (1) start and log a set in one tap, (2) log a meal in under 5 seconds, (3) see what is left today (protein, calories) and the next target. A feature earns a place only if it makes one of these faster or clearer.

## Already in ForgeQuest (checked in code today)
| Idea | Where it already is |
|---|---|
| Deterministic progression pre-fills sets | `domain/Progression.kt`, used by `WorkoutRepository` |
| Stalls and deload detection | `domain/Trends.kt` |
| Per-muscle recovery from logged sets, body map colours | `domain/Progress.kt` (MuscleRecovery), `ui/components/BodyMap.kt` |
| Warm-up sets excluded from score math | `WorkoutScore.isWarmUp` |
| Finish screen with PRs and set summary | `WorkoutCompleteScreen.kt` |
| Barcode scan, protein sort, log again with undo | food screen |

## Done in this session
- Volume vs last workout of the same plan day on the finish screen.
- Food screen decluttered: scan icons inside the search field, one overflow menu, meal chooser, "left today" as the hero.
- Slide tour replaced by three one-time tips on the real buttons.
- Offline plan builder (in progress): quick path plus an optional detailed questionnaire.

## Steal next (fits, deterministic, no wearable)
| Feature | From | Why it fits | Cost |
|---|---|---|---|
| "Why this weight" badge on pre-filled sets | openGym | Builds trust in the numbers; data already exists in `Progression` | small |
| Adaptive calorie target from weight trend and intake (EWMA, ~7700 kcal/kg) | fud-ai, MacroFactor | Removes guesswork, no hardware, supports loop 3 | medium |
| Muscle recovery summary on Home or Coach | Vora | Data exists; one quiet card, no new screen | small |
| Strong/Hevy/FitNotes CSV import, unknown exercises become custom | openGym, Vora | History is not empty on day one | medium |
| Skip-a-day rotation (next session in queue instead of fixed weekdays) | openGym | Real life shifts weeks; helps retention | medium |
| Simple morning check-in (sleep, soreness) feeding readiness | Bevel | Works without a watch | medium |

## Not now (feature fatigue)
- Habit journal, caffeine/water impact on PRs (Bevel): optional, only as a 3-tap toggle if ever.
- Meditations, cycle tracking, tap-water scanner (Vora): bloat.
- Lab-report OCR, biological age (Bevel Pro): scope creep.
- Wearable-only features (HRV, AFib): skip.
- 3-mode muscle map, structural balance ratios: later, after the main loops are fast.
- Bundling third-party exercise GIFs: licensing risk; keep video links.

## AI direction
Core intelligence is moving to deterministic code. The AI coach stays as an optional power-user feature that needs the user's own key, off by default. Order: plan builder (done in P1-P3), progression badge, adaptive calories, then the Coach tab shows deterministic insights first.

## Licences to check before copying anything
openGym and SparkyFitness are reported as AGPL-3.0, OpenNutriTracker as GPL-3.0. Reimplement formulas from scratch.
