# ForgeQuest

Workout and nutrition tracker for Android: Hemo's calisthenics plan, a live set logger, a muscle recovery map, photo meal scanning and an AI coach. It uses a Google Fit-style light and dark design.

The product decisions are in [SPEC.md](SPEC.md). The starting routine comes from [docs/hemo-foundation.md](docs/hemo-foundation.md). The old Antigravity build is kept for reference in `_old/`.

## Install

Copy `ForgeQuest.apk` to the phone, open it and allow "install unknown apps" when Android asks.

To build it yourself:

```
./gradlew assembleDebug        # → app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest    # streak, XP, recovery, records, plan parsing
```

## AI features (meal scan + coach)

Each person uses their own free Gemini key:

1. Open https://aistudio.google.com/apikey and sign in.
2. Tap **Create API key** and copy it.
3. In the app, go to **Profile → AI key**, paste the key and tap Save.

The app checks the key with Google and picks the newest Flash and Pro models available to it. The key is stored encrypted on the phone and isn't included in backups.

## Google Drive (backup + "Workout Plan" doc import): one-time setup

Google only lets an app use Drive after its owner registers it. You do this once, and friends who install the same APK can then use it too.

1. Go to https://console.cloud.google.com and create a project, e.g. "ForgeQuest".
2. Go to **APIs & Services → Library**, search **Google Drive API** and click **Enable**.
3. Go to **APIs & Services → OAuth consent screen**:
   - User type: **External**. App name: ForgeQuest. Use your email for the support and developer contact fields.
   - Scopes: add `.../auth/drive.appdata` and `.../auth/drive.readonly`.
   - Test users: add your Google account and each friend's Gmail address.
4. Go to **APIs & Services → Credentials → Create credentials → OAuth client ID**:
   - Application type: **Android**
   - Package name: `com.forgequest.app`
   - SHA-1 certificate fingerprint (from this PC's debug signing key):
     `A9:32:CB:ED:4F:AD:FD:9E:E8:15:8A:86:FD:C5:B6:17:2A:B9:75:53`
5. Done. No code change or rebuild is needed. In the app, go to **Profile → Google Drive → Connect**. Google will warn that the app is unverified; tap Continue.

Keep a copy of `C:\Users\Administrator\.android\debug.keystore`. APKs are signed with it, and the SHA-1 above belongs to it. If it's lost, you'd need a new OAuth client and everyone would have to reinstall.

What Drive does once connected:
- **Backup:** daily, plus a **Back up now** button. It goes to the app's hidden Drive folder, so it doesn't clutter your files. Photos aren't included.
- **Restore:** replaces the data on this phone with the latest backup.
- **Docs import:** name a Google Doc "Workout Plan" and write your week in plain text. The app turns it into a plan using your AI key. **Import plan from Docs** shows a preview first. The daily sync re-imports the doc automatically when it changes.

## Credits

- Foundation routine: [Hemo Calisthenics](https://www.youtube.com/@HemoCalisthenics), with each exercise linked to the moment it's shown in his videos.
- Anatomy art: [react-native-body-highlighter](https://github.com/HichamELBSI/react-native-body-highlighter) 3.2.0, MIT License © 2022 ELABBASSI Hicham. Converted into `app/src/main/assets/body_map.json`.
