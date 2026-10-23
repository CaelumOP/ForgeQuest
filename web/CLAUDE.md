# ForgeQuest Web

A free PWA of the Android app (repo root `app/`) for iPhone users, served at caelumop.github.io/ForgeQuest. The Android app is the reference for look, features and math. Full plan: `docs/decisions.md` and the project plan.

## Stack
Preact + TypeScript + Vite, Dexie (IndexedDB), vite-plugin-pwa, Vitest. No backend: data stays on the phone, backup goes to the user's own Google Drive, AI calls use the user's own Gemini/Groq key straight from the browser.

## Commands (run in `web/`)
- `npm run dev`: local server at localhost:5173
- `npm test`: Vitest
- `npm run build`: type-check + production build into `dist/`

## Folders
- `src/app.tsx`: hash router (`#/progress`). Never use path routes: GitHub Pages would 404 on reload.
- `src/domain/`: pure TypeScript ports of `app/src/main/java/com/forgequest/app/domain/`. No DOM, no storage. Each file has a `.test.ts` with the Android test cases.
- `src/data/`: Dexie tables (same field names as the Room entities, so backups move between apps), Drive, AI clients.
- `src/routes.ts`: every screen and its `#/` route (param after the second slash). Only the manager edits it.
- `src/ui/<feature>/`: one folder per screen group. `src/ui/kit/`: shared components (import from `../kit`); use them, don't restyle.
- `src/data/settings.ts`, `workouts.ts`, `progress.ts`, `food.ts`, `live.ts`: the only way screens read or write data.
- `src/i18n/`: `en.json` / `ar.json`, generated from `app/src/main/res/values*/strings.xml`. Never hand-edit; re-run the export script.

## Rules
- Arabic is formal standard Arabic. Set `dir="rtl"` on `<html>`; use CSS logical properties (`margin-inline-start`, `inset-inline-end`), never left/right.
- Colors only through the CSS variables in `src/styles.css` (from Android `Theme.kt`).
- Tap targets at least 44 px. Inputs 16 px font. Every pushed screen has its own Back button. SVG icons, no emoji.
- Timers use `Date.now()` and a saved start time, never tick counting or `performance.now()`.
- Save live workout data after every tap; no long Dexie transactions.
- Never store photos. Never log or upload API keys.
- One JS bundle (no lazy imports).
- Keep changes small and only in the files your brief names (karpathy-guidelines). Prove the DONE WHEN check before reporting done.

## Never
- Add a dependency without the manager's OK.
- Touch `app/`, `apk/` or `version.json`.
- Edit saved-data schema, the service worker setup or `vite.config.ts` unless the brief says so; report BLOCKED instead.
