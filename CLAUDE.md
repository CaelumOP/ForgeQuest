# ForgeQuest

Android app (Kotlin/Compose) in `app/`, the reference. Web PWA port in `web/` (see `web/CLAUDE.md`). English and formal Arabic, kept in sync.

## Never delete or rename without asking the user
These are used from outside the code, so nothing in the repo "references" them, and a dead-code check will flag them anyway:
- `apk/version.json` and the release APK naming (`ForgeQuest-vX.Y.Z.apk`): installed apps read them to find updates.
- Anything declared in `app/src/main/AndroidManifest.xml`: activities, receivers (notifications, boot, alarms), services, providers, intent filters and deep links.
- WorkManager workers and their unique work names (scheduled on users' phones).
- Google sign-in / Drive OAuth callbacks and client IDs, and the Drive `appDataFolder` backup file names.
- Health Connect permission declarations and the rationale activity.
- The web service worker, `manifest.webmanifest`, and the GitHub Pages / CI workflow files in `.github/workflows/`.
- Room migrations and exported schemas (`app/schemas/`), and the web Dexie version history: old installs upgrade through them.
- Backup JSON field names (old backups must still restore).
