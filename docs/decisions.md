# ForgeQuest Web decisions

One line each: date, decision, why. Full plan: /mnt/project-files/plans/forgequest-web-plan.md (project files).

1. 2026-10-23: Fresh TypeScript rewrite, Android app as the model. Simpler than sharing Kotlin; browsers handle Arabic natively.
2. 2026-10-23: v1 ships the whole keep list (plans, live workout, journal, progress, food, coach, Drive backup, EN+AR). omar's pick.
3. 2026-10-23: Preact + TS + Vite + Dexie + vite-plugin-pwa. Small bundle for phones on cellular data.
4. 2026-10-23: Code in /web of CaelumOP/ForgeQuest, GitHub Action deploys to Pages. One repo, strings and tests shared.
5. 2026-10-23: Android look with iPhone touches, checked with ui-ux-pro-max (palette passes 4.5:1).
6. 2026-10-23: Drive auto-backup while the 1 h token is valid, "Tap to back up" otherwise, plus a Save backup file button. Same JSON as Android.
7. 2026-10-23: Address caelumop.github.io/ForgeQuest with #/ routes. No other Pages site on the CaelumOP account ever (shared origin = shared storage and keys).
8. 2026-10-23: Android audit items 1-6 fixed in Android 2.9.2 first, then the domain port starts from main. Items 7, 8, 9, 11, 14, 15 fixed during the port.
9. 2026-10-23: Install gate: Safari shows "Add to Home Screen" before onboarding, because Safari and Home Screen apps keep separate storage.
10. 2026-10-23: One JS bundle, everything precached. Updates install automatically until step 18 adds the "Update ready" prompt.
