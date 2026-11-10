# Brief: audit fixes, web + docs lane (2.17.0)

The audit report is at `C:\Users\ADMINI~1\AppData\Local\Temp\claude\G--AntiGravity-projects-ForgeQuest\4575fb44-3da7-461e-8f11-8f1fb723869d\scratchpad\launchworthy-forgequest.md`. Read the items named below.

**TASK:**
1. **Error safety net** (issue #78):
   - In `web/src/app.tsx`, wrap the routed screen in a Preact error boundary (`componentDidCatch` / `getDerivedStateFromError`), keyed by the route path so navigating away resets it.
   - It shows a calm card: "Something went wrong on this screen." with **Back** and **Reload**, in EN + AR, styled like the app's cards.
   - Test it with a component that throws.
2. **Crash reports, web** (issue #79, matching Android's approach):
   - `window.onerror` and `unhandledrejection` handlers in `main.tsx` save the last error (time, app version, user agent, message and stack; no user data) to `localStorage` under one key, wrapped in try/catch.
   - An unhandled rejection also shows the app's existing toast ("Something went wrong").
   - On next launch, a one-time small prompt offers **Send crash report** (`navigator.share` with the text, or copy to clipboard if share is unavailable) and **Dismiss**. Either choice clears it.
   - No network or tracking service. Add tests for the save/clear logic.
3. **Frame protection** (issue #83, audit item 7): GitHub Pages can't send `X-Frame-Options`, so add a frame-buster at the top of `main.tsx`: if `window.top !== window.self`, try to break out with `window.top.location = window.self.location`, else hide the app. Keep it from breaking the app when not framed.
4. **Dev screens out of production** (issue #84): in `web/src/routes.ts`, register `#/check`, `#/kit` and `#/viewport` only when `import.meta.env.DEV`. Check nothing in production links to them.
5. **Privacy policy covers the web app** (issue #82):
   - In `PRIVACY.md`, add a Web app section: data stays in the browser (IndexedDB), the AI key is stored in the browser **unencrypted** (cleared with the site's data), Drive backup is the same appDataFolder file and is optional, and there's no tracking or analytics.
   - Keep the Android parts true. Read the current file and the web code to stay accurate.
6. **Bad-release and incident plan** (issue #80): a "Bad release" section in `docs/SETUP.md`:
   - Android: versionCode can't go down, so revert the change, bump versionCode and `apk/version.json`, and merge. CI publishes the fixed APK; phones offer it as an update.
   - Web: revert and merge; Pages redeploys. Users get it through the "Update ready" pill.
   - What to check after (the release asset exists, `version.json` points to it).
   - Who to tell (testers).
   - Plus a short "If something is on fire" checklist: a broken update banner, a broken web deploy, a lost keystore.
7. **Small cleanups** (issue #84):
   - add `.env*` to `.gitignore`
   - pin GitHub Actions in `.github/workflows/*.yml` to full commit SHAs, with the tag kept in a trailing comment. Resolve each `uses: owner/repo@vX` to its SHA with `gh api repos/owner/repo/commits/vX --jq .sha`. Don't change anything else in the workflows. **The workflows publish the APK and the site, so be exact.**

**Verify:** `cd web && npx tsc --noEmit -p . && npx vitest run && npm run build`. For the workflows, run `gh workflow view` or YAML-parse each file to check it's still valid.

**DON'T:**
- touch `app/` (the Android lane owns it)
- change workflow logic beyond the pins
- push
- stage other files: commit with `git commit -- <paths>`, one commit per item, ending each message with `Closes #N` (or `Part of #N`) plus `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`

Work in `G:\AntiGravity projects\ForgeQuest\.claude\worktrees\leftovers` (branch `feat/leftovers`; another builder is working in `app/` at the same time).

**If you reach 25 minutes:** stop at a clean commit and report what's left as a handoff.

**End with exactly:**
```
STATUS: done | blocked | failed
FILES: paths changed
CHECK: commands run, pass/fail; on fail only the failing lines (max 30)
NOTES: per item what was done; the pinned actions list (max 100 words)
```
