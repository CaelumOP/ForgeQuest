# ForgeQuest

Android app (Kotlin/Compose) in `app/`, the reference. Web PWA port in `web/` (see `web/CLAUDE.md`). English and formal Arabic, kept in sync.

UI on both platforms follows `DESIGN.md` (Soft Coach); see the `forgequest-design` skill.

## Never delete or rename without asking the user
These are used from outside the code, so nothing in the repo "references" them, and a dead-code check will flag them anyway:
- `apk/version.json` and the release APK naming (`ForgeQuest-vX.Y.Z.apk`): installed apps read them to find updates.
- Anything declared in `app/src/main/AndroidManifest.xml`: activities (`MainActivity`, `ShareMealActivity`, the Health Connect rationale alias), the one receiver (`data/notify/RestAlarmReceiver`, the rest timer alarm), the `SCHEDULE_EXACT_ALARM` permission, the `FileProvider`, intent filters and deep links. There are no services.
- Notification channel ids (`rest`, `rest_done_v2`, `reminders`): Android keeps a channel's sound and importance from the day it was made, so a changed channel needs a new id, never an edit.
- WorkManager workers and their unique work names (scheduled on users' phones).
- Google sign-in / Drive OAuth callbacks and client IDs, and the Drive `appDataFolder` backup file names.
- Health Connect permission declarations and the rationale activity.
- The web service worker, `manifest.webmanifest`, and the GitHub Pages / CI workflow files in `.github/workflows/`.
- Room migrations and exported schemas (`app/schemas/`), and the web Dexie version history: old installs upgrade through them.
- Backup JSON field names (old backups must still restore).

## Where to start, what to skip
- Android: the table below. Skip `web/`, `tools/`, `.repowise/`, `brag-output/`, `.claude/worktrees/`.
- Web PWA: `web/CLAUDE.md`. Skip `app/` except as the behaviour reference.
- Food catalog tools: `tools/fooddb/SCHEMA.md`. Never read `tools/fooddb/out/`, `cache/`, `sources/` raw; ask haiku-scout for counts.
- Never load: build outputs, `node_modules/`, `web/dist/`, `apk/*.apk`, `app/src/main/assets/*.db`.

## Fast routing (Android)
Paths are under `app/src/main/java/com/forgequest/app/`. Open these first; grep only if the task isn't covered.

| Domain | Files |
|---|---|
| Workouts & live session | `ui/workouts/LiveWorkoutScreen.kt` (screen + group cards), `LiveWorkoutSets.kt` (set row, tick, effort, note dialog, PR tag), `LiveWorkoutTimers.kt` (hold overlay, rest bar), `data/notify/RestAlert.kt` + `RestAlarms.kt` (rest-over alert and its alarm), `WorkoutCompleteScreen.kt` (finish screen, debrief card), `ui/workouts/LiveWorkoutViewModel.kt`, `ui/workouts/PlanEditorScreen.kt`, `data/WorkoutRepository.kt`, `domain/ExerciseSwapEngine.kt`, `domain/VariationCycle.kt` (variation for a stalled lift), `domain/WorkoutScore.kt`, `domain/WorkoutDebriefMath.kt` |
| Nutrition & meal scan | `ui/food/FoodScreen.kt`, `ui/food/FoodViewModel.kt`, `ui/food/ScanScreen.kt`, `ShareMealActivity.kt` (package root), `data/FoodRepository.kt`, `data/bridge/MealInbox.kt`, `domain/Nutrition.kt`, `domain/CalorieCheck.kt` |
| AI coach, Gemini bridge | `ui/coach/CoachScreen.kt`, `data/CoachRepository.kt` (chat, plans), `data/CoachPromptBuilder.kt` (system prompt, progress summary), `data/ai/GeminiClient.kt`, `data/bridge/GeminiBridge.kt`, `ui/components/SkillPrompts.kt` (the copyable Gem skills, `res/raw` + `res/raw-ar`) |
| Home, Health Connect & readiness | `ui/home/HomeScreen.kt`, `ui/home/HomeViewModel.kt`, `ui/home/ReadinessCard.kt`, `ui/home/ReadinessDetailScreen.kt`, `data/health/HealthRepository.kt`, `domain/Readiness.kt`, `domain/ReadinessText.kt` (which sentence, which wording) |
| Backup & Drive sync | `data/backup/BackupRepository.kt`, `data/drive/DriveSync.kt`, `data/drive/DriveSyncWorker.kt` |
| Database | `data/db/AppDatabase.kt` (schema version 10; never bump without a migration), `data/db/Entities.kt`, `data/db/Daos.kt` |
| Settings & profile | `ui/profile/ProfileScreen.kt` (screen, dialogs), `SettingsGroups.kt` (one composable per settings group: body, goals, training, coach, app, backup, about), `ProfileViewModel.kt` (shared by Profile, Settings and Badges), `SettingsViewModel.kt` (settings actions, Drive, backup, updates), `SettingsComponents.kt` (settings rows, badge chip), `EquipmentCard.kt` |

Files over 400 lines: read with `offset`/`limit` after a `grep -n "fun \|class "` skeleton.

## Gemini helper
The `gemini` subagent (`.claude/agents/gemini.md`) sends read-only tasks to Gemini via Antigravity CLI (`agy -p`), on the user's Google AI Pro plan.
- Use it for big reads and summaries (many files, long logs, Gradle output), broad searches, research, and second-opinion reviews. Also when the user says "ask Gemini".
- Keep edits, final decisions and verification with Claude; treat Gemini's answer as input to check, not fact.
- Never set up a Gemini or Google API key, Vertex AI, or anything billed per use.
