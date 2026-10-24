# ForgeQuest setup

Everything needed to build ForgeQuest, sign releases and set up the AI and Google Drive features. Back to the [README](../README.md).

## Build from source

```
./gradlew assembleDebug        # → app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest    # streak, XP, recovery, records, plan parsing
```

## Release signing

ForgeQuest's release key is the debug keystore of the PC that built the first releases
(`%USERPROFILE%\.android\debug.keystore`, SHA-1 `A9:32:CB:…:75:53`). Every APK on testers' phones is signed with it, and
it's the SHA-1 registered for Drive sign-in, so it stays the key for good. Android refuses an update signed with a
different key, so switching would make everyone uninstall (and lose their data) to update.

- **Cloud builds** sign with this key from the repo secrets (next section). This is the normal way to release.
- **On that PC**, `./gradlew assembleRelease` signs with the same key on its own: with no `keystore.properties`, Gradle
  falls back to the debug key (it logs a warning; that's expected). Output: `app/build/outputs/apk/release/app-release.apk`.
- **On any other PC**, the local debug key is different, so a release APK built there won't install over the app.

Back up `debug.keystore` somewhere safe. If it's lost, no one can update the installed app and Drive sign-in needs a
new OAuth client.

## Cloud builds (GitHub Actions)

`.github/workflows/android.yml` runs the unit tests and builds the release APK on every PR (download it from the run's
Artifacts). After a merge to main it commits the APK to `apk/ForgeQuest.apk`, but only when `versionCode` equals
`apk/version.json`, the APK is signed with the same key as the committed one, and it is newer. So a release is just: bump
`versionCode`/`versionName` in `app/build.gradle.kts` and `apk/version.json` in the PR, then merge.

It signs with four repo secrets (Settings → Secrets and variables → Actions): `KEYSTORE_BASE64` (the keystore file as
base64), `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`. All four hold the release key above and were set on
2026-10-23. Only if they're ever lost, set them again from PowerShell on that PC:

```
$ks = "$env:USERPROFILE\.android\debug.keystore"
[Convert]::ToBase64String([IO.File]::ReadAllBytes($ks)) | gh secret set KEYSTORE_BASE64 -R CaelumOP/ForgeQuest
gh secret set KEYSTORE_PASSWORD -R CaelumOP/ForgeQuest -b android
gh secret set KEY_ALIAS -R CaelumOP/ForgeQuest -b androiddebugkey
gh secret set KEY_PASSWORD -R CaelumOP/ForgeQuest -b android
```

The workflow refuses to publish an APK whose signer differs from the committed one.

## AI features (meal scan + coach)

Each person uses their own free Gemini key:

1. Open https://aistudio.google.com/apikey and sign in.
2. Tap **Create API key** and copy it.
3. In the app, go to **Profile → AI key**, paste the key and tap Save.

The app checks the key with Google and picks the newest Flash and Pro models available to it. The key is stored encrypted on the phone and isn't included in backups.

What the coach sees with every message: your goals, weight trend, this week's done and missed days, today's session, the
full plan, muscles still recovering, your last 5 workouts, 4-week trends on the exercises you do most, personal bests,
7-day nutrition and badges. Replies stream in as they're written, and chat and meal scans run with the model's
"thinking" off so they land in a few seconds; plans keep it on. If you mention something lasting (an injury, your
equipment, a preference) the coach saves it under **Profile → What the coach remembers**, where you can also add or
remove notes yourself. Photos and this data go to Google under your key; free keys allow Google to use them to improve
its models.

## Backup without Google Drive

**Profile → Your data → Export file** writes a JSON backup and opens the share sheet, so you can save it to Files,
Drive, Telegram or wherever you like. **Import file** reads one back and shows what's in it before replacing anything.
Every restore first keeps a copy of what was on the phone; **Undo last restore** brings it back. Settings (name, goals,
hand size) are included; meal photos and the AI key are not.

## Google Drive (backup + "Workout Plan" doc import): one-time setup

Google only lets an app use Drive after its owner registers it. You do this once, and friends who install the same APK can then use it too.

1. Go to https://console.cloud.google.com and create a project, e.g. "ForgeQuest".
2. Go to **APIs & Services → Library**, search **Google Drive API** and click **Enable**.
3. Go to **APIs & Services → OAuth consent screen**:
   - User type: **External**. App name: ForgeQuest. Use your email for the support and developer contact fields.
   - Scopes: add `.../auth/drive.appdata` (backup) and `.../auth/drive.file` (Docs the app creates: "ForgeQuest data", "ForgeQuest from Gemini", "ForgeQuest plan"). Both are non-sensitive, so no verification is needed.
   - Publishing status: press **Publish app** (In production). Anyone can then sign in; no test-user list. (Google may still show an "unverified app" notice; tap Continue.)
4. Go to **APIs & Services → Credentials → Create credentials → OAuth client ID**:
   - Application type: **Android**
   - Package name: `com.forgequest.app`
   - SHA-1 certificate fingerprint (from this PC's debug signing key, which also signs the release APK):
     `A9:32:CB:ED:4F:AD:FD:9E:E8:15:8A:86:FD:C5:B6:17:2A:B9:75:53`
5. For the web app, create a second OAuth client ID: Application type **Web application**, Authorized JavaScript
   origins `https://caelumop.github.io` and `http://localhost:5173`. Put its client ID (public, not a secret) in
   `web/src/config.ts`. Done on 2026-10-23.
6. Done. No code change or rebuild is needed for Android. Until this is done, picking an account closes the sign-in straight away. In the app, go to **Profile → Google Drive → Connect**. Google will warn that the app is unverified; tap Continue.

Keep a copy of `C:\Users\Administrator\.android\debug.keystore`. APKs are signed with it, and the SHA-1 above belongs to it. If it's lost, you'd need a new OAuth client and everyone would have to reinstall.

What Drive does once connected:
- **Backup:** daily, plus a **Back up now** button. It goes to the app's hidden Drive folder, so it doesn't clutter your files. Photos aren't included.
- **Restore:** replaces the data on this phone with the latest backup.
- **Docs import (optional):** name a Google Doc "Workout Plan" and write your week in plain text. The first **Import plan from Docs** asks for read access to your Drive and shows a preview. After that, the daily sync checks the doc and, when it changed, posts the new week in the Coach tab for you to review and apply. **Stop reading Docs** turns it off.
