# Brief: audit fixes, Android lane (2.17.0)

The audit report is at `C:\Users\ADMINI~1\AppData\Local\Temp\claude\G--AntiGravity-projects-ForgeQuest\4575fb44-3da7-461e-8f11-8f1fb723869d\scratchpad\launchworthy-forgequest.md`. Read the items named below.

**TASK:**
1. **Crash reports you can send** (issue #79):
   - Install a default uncaught-exception handler in `ForgeQuestApp` (chain to the previous handler). It writes the last crash (time, app version, Android version, device model, stack trace; no user data) to a small file in app storage.
   - On the next launch, show a one-time, non-blocking prompt (a snackbar or small card on Home): "ForgeQuest closed unexpectedly last time. Send a crash report?" with **Send** (opens the share sheet with the report text, addressed to nothing) and **Dismiss**. Either choice deletes the file.
   - No network and no tracking service. EN + formal AR strings.
   - Test the formatting part with a unit test.
2. **Updater checks** (issue #83, audit item 8): in `data/update/UpdateChecker.kt` and the install path:
   - accept the `apk` URL only if it starts with `https://github.com/CaelumOP/ForgeQuest/releases/download/`
   - before `install()`, check the downloaded APK's `packageName` equals ours and its signing certificate matches the installed app's (`PackageManager.getPackageArchiveInfo` with `GET_SIGNING_CERTIFICATES` on API 28+, `GET_SIGNATURES` below)
   - On a mismatch, fail with the existing update-failed message and delete the file.
   - Unit-test the URL rule. Keep every existing behaviour otherwise.
3. **Database upgrade tests** (issue #81): a test that walks Room migrations 1→9 using `app/schemas/`.
   - Prefer a local JVM/Robolectric `MigrationTestHelper` if the project can run it. If it needs an instrumented (androidTest) setup that CI doesn't run, add it as an instrumented test anyway, and also add a JVM test that checks every `MIGRATION_x_y` exists and chains 1→9 without gaps.
   - Read `AppDatabase.kt` and the existing test setup first. Don't change any migration or schema.
4. **Strip timing logs from release builds** (issue #84): add `-assumenosideeffects class android.util.Log { public static int v(...); public static int d(...); public static int i(...); }` to `app/proguard-rules.pro`. Keep `w` and `e`.

**Verify:** `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest :app:assembleRelease`, with `TEMP=C:\gt`, `TMP=C:\gt`, `ANDROID_HOME=G:\Android`, `JAVA_TOOL_OPTIONS=-Djava.io.tmpdir=C:/gt`.

**DON'T:**
- touch `web/`, `docs/` or `.github/` (the other lane owns those)
- change migrations, schemas or the signing config
- push
- stage other files: commit with `git commit -- <paths>`, one commit per item, ending each message with `Closes #N` for its issue plus `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`

Work in `G:\AntiGravity projects\ForgeQuest\.claude\worktrees\leftovers` (branch `feat/leftovers`).

**If you reach 25 minutes:** stop at a clean commit and report what's left as a handoff.

**End with exactly:**
```
STATUS: done | blocked | failed
FILES: paths changed
CHECK: commands run, pass/fail; on fail only the failing lines (max 30)
NOTES: per item what was done; the migration test type (JVM or instrumented) and whether CI runs it (max 100 words)
```
