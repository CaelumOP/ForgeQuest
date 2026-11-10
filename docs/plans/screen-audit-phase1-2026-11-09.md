# Phase 1: screen-by-screen audit (2026-11-09)

Base look stays **Soft Coach / Simple Home** (flat, one teal accent, no gradients; do not touch ReadinessCard, bottom nav, StreakCard internals; protein stays in the ring centre; see handoff-2026-11-09-full.md section 3c and 4). This audit is about structure and priority, not decoration.

## How each screen is judged
- **Uses per week** for a typical user (3-4 workouts a week, food most days). A thing used about once a week does not deserve the front; a thing used once ever should not be on a main screen after day one.
- **Tired-thumb test** (the "drunk" test, not literally): one hand, half attention, bad lighting. Can you do the main thing with one big tap and no reading? Do targets move under the thumb? Is there an easy undo? Is the dangerous button away from the common one?
- **Main uses**: (1) tick a set, (2) log a meal in under 5 s, (3) see what is left today and what is next.
- **Verdict**: KEEP / MOVE / DEMOTE / MERGE / CUT, with the reason.

## Tabs
| Screen | Uses/wk | Tired-thumb result | Verdict and why |
|---|---|---|---|
| **Home** | 10+ | Rings and Start button are big. But up to 12 cards; nothing on Home logs a meal, so main use 2 starts with a tab switch and a search. | KEEP structure. **CUT** the "new badges" card (the finish screen and Profile already announce badges, and it is once per badge). **ADD** one calm "Log a meal" action under the stats (main use 2). Streak and Progress cards stay at the bottom: weekly glances. |
| **Workouts** | 4 (start) + 1 (look at plan) | Day chips fine. Start sits at the end of a long list. | KEEP. **MOVE** Start to a pinned bottom button (main use 1 starts here too). **MOVE** Exercise library entry here (a library is a Workouts thing, it is in Profile now). |
| **Food** | 10+ (daily, 3-5 times) | Much better after the declutter. Two real problems: (a) the saved list is per meal, so a new user or a new meal shows "No saved Dinner foods yet" and nothing to tap; (b) the first catalogue search stalls for ~10 s while 15k rows load, and the "No saved food matches" message sits above the real results. | KEEP. **FIX**: default list = recently logged foods (any meal) so logging again is one tap; warm the catalogue when the tab opens; when catalogue results exist, hide the "no saved match / add as new food" block below them. |
| **Coach** | AI off: about 1 (weekly glance); AI on: occasional | With AI off it is a weekly insights page on a prime tab. A weekly screen should not own a tab named "Coach" that promises a chat. | **MERGE/RENAME**: when AI is off the tab is called "Insights" and also links to Progress charts (so Progress stops being buried in Profile and on a Home card only). Keep the AI chat for people who turn it on. Do not change the number of tabs. |
| **Profile** | 1 | Identity, XP explainer, badges, weight card with chart, Progress, Library, Settings: seven jobs. | **DEMOTE** to three things: identity/XP, badges, Settings. **MOVE** weight chart to Progress (logging stays on Home's weight stat); **MOVE** Library to Workouts; Progress is reached from Insights/Home. |

## Pushed screens
| Screen | Uses/wk | Result | Verdict |
|---|---|---|---|
| **Live workout** | 4 sessions, the most important screen | Tick is big and the rest bar is good. Problems: (a) after each tick the effort row (Easy/OK/Hard/Pain) is inserted and pushes the next set's tick down, so the target moves under the thumb; (b) "Discard" sits in the top bar beside Focus mode, a destructive button next to a common one; (c) 27 sets visible at once. | **FIX** (a) show effort as a thin overlay that does not push, auto-fades; (b) Discard into an overflow menu; (c) collapse finished exercises (already partly). |
| **Workout complete** | 4 | Done is pinned now; level card, PR rows. | KEEP. |
| **History / Session detail** | 1 | List with debrief, notes. | KEEP. |
| **Progress** | 1 | Dense, honest for new users. | KEEP; becomes reachable from Insights. Gets the weight chart. |
| **Exercise detail** | 1-2 | History and video for one exercise. | KEEP. |
| **Exercise library / editor** | 0.2 | Search, chips. | KEEP; entry moves to Workouts. |
| **Badges** | 0.3 | Grid of 28. | KEEP. |
| **Plan editor / day editor** | 0.2 after setup | Several sheets (change plan, templates, previous, item editor, preview, move, paste, rename). Power tools. | KEEP but not on any main screen; entry stays under Workouts "Edit plan". |
| **Plan builder** | once, then rarely | Quick card plus optional depth; sticky Build. | KEEP. |
| **Settings list + 7 pages** | 0.1 | Drill-down with summaries; Body page 15 controls; Training page opens with equipment presets; Backup, Advanced AI, About. | KEEP. **FIX**: Training page lists "Rebuild my plan" first then 8 equipment presets then more; fine. Body page: fine. |
| **Readiness detail** | 0.5 (Fitbit users only) | Out of scope: do not touch. | KEEP. |
| **Scan (AI) / Barcode** | AI scan 0 when AI off; barcode 1-2 | Barcode full screen; with AI off the missing-product card now offers scan again / cancel. | KEEP. |
| **Tour (replay)** | 0 | Only via Settings > App. | KEEP, unreachable from the front. |

## Sheets and dialogs (≈18 sheets, ≈30 dialogs)
Most are confirmations or editors behind a deliberate tap, so they are fine. The ones on a main path: Food quick log (now in the overflow menu: 2 taps; fine), Catalog amount sheet (needed), body-weight sheet (from Home stat; fine), debrief sheet (from History). No change.

## Onboarding
Language, Welcome (name, body type), Plan (Built for me first, finish). Two steps. KEEP. Food targets setup appears later on Home when height is missing: fine, it is one card.

## Cross-cutting findings
1. **Main use 2 has no entry on Home** (add one calm action).
2. **Weekly things on prime spots**: Coach tab (weekly), Profile weight card, Progress card; a monthly thing (library) in Profile.
3. **One-time things on Home**: new badges card (duplicates finish screen), food-setup card (fine, conditional), update banner (fine).
4. **Moving targets**: effort row inserted under the thumb in the live workout.
5. **Destructive next to common**: Discard beside Focus mode.
6. **Perceived speed**: catalogue first-search stall.
7. Not changed on purpose: bottom nav, ReadinessCard, StreakCard, ring look, protein in the ring centre.
8. Open for the user: body text 16 vs 17; 24 dp padding on the two Home cards.
