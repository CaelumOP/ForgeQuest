# 3C spec: UX cut list (#17-#43) + wavy rest timer and sliding pills (D)

Item text is in `docs/MASTER_GATE.md` section C and D; owner overrides in `docs/MASTER_SPEC.md` WIN. Evidence with file:line refs: section "5. Android UX Rent-Check Inventory", "3. Wavy Rest Timer" and "4. Build My Plan Pills" of
C:\Users\Administrator\AppData\Local\Claude-Data\claude_pzs8sxrjxfjjc\side-session-notes\local_a06498bf-bef1-42bb-920a-c03ca288d21f\stage2-code-map.md
Web notes: stage2-ux-web-notes.md in the same folder.

Rules for every item:
- Same result on Android and web. English + formal Arabic together. When a string becomes unused, remove it from every locale (Android `values/strings.xml` + `values-ar/strings.xml`; web per web/CLAUDE.md, then regenerate web i18n and prune `web/src/i18n/web/*.json` overlays).
- KILL = remove the UI element and its dead code. HIDE = keep the feature, move it one tap deeper as described. MERGE = as described. Never remove a feature that has no other entry point.
- Never touch anything on the CLAUDE.md never-delete list (manifest entries, channels, workers, OAuth, Health Connect declarations, service worker, workflows, migrations, backup field names).
- Soft Coach design (DESIGN.md): one accent, 48 dp/px targets, RTL, 200 % text, reduced motion.

Overrides in force:
- #22 HIDE (not kill): remove the "How to do it" / "Swap exercise" pills from every live card; both actions stay one tap away in the card's ⋮ menu AND by tapping the exercise title (title tap opens How to do it; ⋮ has both).
- #20 Progress "Recovery" text card: KILL only if the Workouts muscle map still shows recovery after #30 (it does: #30 only collapses the map to one line before the first workout and shows the map after). If the map would not show recovery, HIDE instead.

Item notes (beyond the gate text):
- #27 the form cue sentence moves into "How to do it" (the sheet/dialog that already shows instructions).
- #28 rest panel: keep -15 s, +15 s, Skip visible; the preset chips 60/90/120/180 and "Copy last set" appear only after tapping the timer (toggle), hidden again on next rest.
- #29 plate breakdown: shown when the weight value is tapped (toggle), not as a permanent chip.
- #30 Workouts muscle map: before the first finished workout show one line "Muscle recovery shows here after your first workout" (AR «تظهر حالة تعافي العضلات هنا بعد أول تمرين»); after it, the full map as today. On web also make the chips wrap instead of a sideways row running off screen.
- #33 Plan editor Schedule card (Weekdays / Rotation) moves inside the "Change plan" sheet.
- #34 progression mode chips go under an "Advanced" expander in the plan item editor, labels "Add reps first" / "Add weight each time" / "Keep the same" (AR «زد التكرارات أولًا» / «زد الوزن كل مرة» / «أبقِه كما هو»); stored values DOUBLE / LINEAR / MAINTAIN unchanged.
- #35 History list cards: drop the score line, "Add a note", "Workout debrief" rows from the list card; they live in the workout detail screen (make sure the detail has all three).
- #36 Workout complete: show at most 1 extra insight sentence (keep the most important per existing priority).
- #37 Coach tips: never open over the rest timer or a set row; one tip per screen; only after the first tick.
- #38 Home protein stat: remove the separate protein tile/stat; the ring already shows protein (add the "g to go" text inside/under the ring if it is not there).
- #39 + D pills: "Where do you train?" uses the same sliding segmented pills as Days and Minutes on both platforms (Android replaces its 3 big cards).
- #40 Training load ring "64 %" -> plain text line "This week vs your usual: +12 %" (AR «هذا الأسبوع مقارنةً بمعدلك: ‎+12٪») with sign; keep the same underlying number.
- #41 rename: "Share plan" / «مشاركة الخطة», "Import a plan" / «استيراد خطة», "Connect Gemini" / «ربط Gemini» (replace "Share/Import plan JSON", "API key", "Gem" wording in those places).
- #42 audit every catalog cue text (EN + AR) for truncated sentences (ends mid-phrase like "...the leg of the."); fix the text in the catalog sources both platforms read (find the source of truth under tools/catalog and the generated app/web assets; regenerate per the tool's README if generated). List every fixed id in the report.
- #43 goal subtitles: no duplicate of the title; format "<load> · <reps> · <rest>": STRENGTH "Heavy weights · 3-6 reps · 150 s rest", MUSCLE "Moderate weights · 8-12 reps · 90 s rest" (AR «أوزان متوسطة · 8–12 تكرار · راحة 90 ث»), and matching lines for the other goals using their real builder rep ranges and rest values (read them from the builder; write formal Arabic in the same pattern).

D. Wavy rest timer (both platforms)
- Android: drive the wave from the rest `endsAt` on the frame clock (`withFrameMillis` loop in a LaunchedEffect keyed on endsAt) so progress updates every frame (60 FPS), no 250 ms polling / per-second emission / spring smoothing for the wave. Text countdown may still tick per second. Calmer wave: stroke 3-4 dp, wavelength 28-36 dp, amplitude 3-4 dp, slower phase speed; a visible flat track behind it at 14-18 % contrast of the accent/onSurface.
- Android cold process death: when the screen is rebuilt and a saved rest alarm time exists in the future, rebuild the running rest from it.
- Web WaveBar: requestAnimationFrame loop from `endsAt` (cancel on unmount), same visual targets (stroke 3-4 px, wavelength 28-36 px, amplitude 3-4 px, flat track 14-18 % contrast). prefers-reduced-motion: no wave motion (static progress line still moves).
- Web live dock / rest panel: max-width 640 px, centered (matches the column).
- Keep: Failure +45 s extension, -15/+15, Skip, resume after reload/app restart, alerts.

D. Sliding segmented pills (both platforms) for Days, Minutes and Where do you train in Build my plan
- A single selection indicator slides between options (~200-250 ms ease), correct in RTL (slides the mirrored way), reduced motion = jump with no animation, every option >= 48 dp/px tall, accessible as a radio group (selected state announced).
- Android: a reusable `SlidingSegmentedPills` composable in ui/components; web: a reusable component + CSS (transform translateX, logical properties for RTL).
