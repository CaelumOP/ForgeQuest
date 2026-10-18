Branch `claude/hopeful-meitner-lkx0er` has 3 new commits on top of main (08b238d, ff46732, f943f2b). They add a Progress screen and were written without a build; verify on the emulator, fix compile errors if any, and keep changes minimal. Build once, then test these on the device, in dark and light, English and Arabic:

1. Home → tap the rank ring (top right) or the new "This month" card → Progress screen. Check: range chips switch everything; training-load ring and weekly bars; metric tiles with sparklines and gain chips; Strength chart (chips pick the exercise, gold dots are records, dashed all-time-high line, drag to read a value); body weight chart with 7-day average; consistency heatmap (7 rows, no horizontal overflow at 52 weeks); Then vs now; All time. Empty state when there are no workouts.
2. Workouts tab → each exercise row shows a small sparkline (needs 2+ sessions of that exercise). Row must not clip the name at 1.3× font.
3. Live workout → type a value above your best for that exercise: the number turns gold for ~1.4 s with a haptic, once per set.
4. Onboarding step 2 and Profile → Goals: Height and Age steppers; "Suggested N kcal…" line with a Use pill. Formula is Mifflin-St Jeor × activity factor from training days (domain/Nutrition.kt, unit-tested).
5. Chart colours are new FitColors tokens (chartStrength, chartWeight, chartCalories, chartProtein, chartOther, chartGain, chartGainTrack). If any chart looks wrong in light mode, those are the knobs.

Files: domain/Trends.kt, domain/Nutrition.kt (tests in app/src/test), ui/components/Charts.kt, ui/progress/ProgressScreen.kt, small edits to HomeScreen, WorkoutsScreen, LiveWorkoutScreen/ViewModel, OnboardingScreen, ProfileScreen, Theme.kt, SettingsRepository, BackupRepository, CoachRepository, strings.xml (en + ar).

Not built, optional later: Monday recap card with Share-as-image; Food tab 14-day protein strip.
