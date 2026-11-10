# Brief: final emulator pass for the 2.17.0 audit fixes

**TASK:** a short emulator check of the audit fixes. Take screenshots and report. Fix nothing.

- Scratch: `C:\Users\ADMINI~1\AppData\Local\Temp\claude\G--AntiGravity-projects-ForgeQuest\4575fb44-3da7-461e-8f11-8f1fb723869d\scratchpad`. Shots go to `<scratch>\qa6\shots` (set `FQ_SHOTS`).
- **Build:** in `G:\AntiGravity projects\ForgeQuest\.claude\worktrees\leftovers`, run `./gradlew :app:assembleRelease`, with `TEMP=C:\gt`, `TMP=C:\gt`, `ANDROID_HOME=G:\Android`, `JAVA_TOOL_OPTIONS=-Djava.io.tmpdir=C:/gt`. It's signed with the same key as the installed app. Find the APK under `app/build/outputs/apk/release/`.
- **Install:** start AVD `ForgeQuest_Pixel` with `-memory 2048` (emulator.exe under `G:\Android`; adb is `G:\Android\platform-tools\adb.exe`). `adb install -r` (never uninstall, never clear data). Drive with `python "G:\AntiGravity projects\ForgeQuest\tools\qa\fq.py"`.

**Checks:**
1. **Crash report card:**
   - Open the app, then force a crash with `adb shell am crash com.forgequest.app` (or the package name from the APK: `aapt dump badging` or `adb shell pm list packages | grep forgequest`).
   - Reopen the app: Home shows the "closed unexpectedly… Send crash report?" card. Screenshot it.
   - Tap **Send**: the share sheet opens with the report text. Screenshot, then back out.
   - Reopen: the card is gone.
   - Repeat the crash once and tap **Dismiss**: gone.
   - Arabic: crash once more, switch the language to Arabic, screenshot the card, Dismiss, switch back to English.
2. **Nothing else broke:** Home (rings, stats, workout card), start and finish a 1-set workout (the debrief shows), open the journal and one sheet, Food, Profile (the Just log switch is there). Screenshots of Home and the finish screen.
3. **Update check:** Profile → Check for updates. It must say up to date, or show no error. Screenshot.

**At the end:** English, shut the emulator down (`adb emu kill`).

**DON'T:** edit the repo, push or commit, or use a real API key.

**End with exactly:**
```
STATUS: done | blocked | failed
BEST: the 5 most telling screenshot paths with short captions
FOUND: per check: OK or what's wrong, and any crash other than the forced ones (max 25 lines)
```
