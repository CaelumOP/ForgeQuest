# Screen audit, 2026-11-09 (emulator, English, fresh install, AI off)

Checked against: the 12 UX laws in DESIGN/forgequest-design, the Rule of 3 (log a set in one tap, log a meal in under 5 s, see what is left today), and Soft Coach. Not checked: Arabic RTL, 200% text, dark mode, Live workout after the first set, finish screen, History, Progress, Exercise library. Screenshots: tools/qa/shots/036-049.

## Works well
- First run: language, then Welcome, then plan step, with no slide tour. "Built for me" is the first plan card.
- Food first view: one overflow menu, barcode icon inside the search field, "140 g protein left / 2,200 kcal left" as the hero, one meal dropdown. About 6 fixed controls.
- Plan builder quick card: four answers, one dominant "Build my plan" button. Deep section collapsed behind one pill, six rows each summarised as "Auto".
- Plan review: per-day list with swap icons and "About 50 min".
- Profile settings: seven collapsed groups, each with a one-line summary.
- Coach marks appear once, one at a time, and "Got it" closes them.

## Findings (most important first)
1. **Coach tab is a dead end when AI is off.** One card ("AI coach is off, turn it on in Profile > Coach") and an empty page, on a main tab. Since AI is now off by default, most users land here. Fix: show deterministic content (per-muscle recovery from `Progress.kt`, stalled lifts and deload hints from `Trends.kt`, last-7-day summary, today's remaining protein), with the AI chat added below only when AI is on. This is also the "make the Coach deterministic" step from the plan.
2. **Settings inside Profile: acceptable, but Profile has too many jobs.** It holds identity and XP, badges, body weight, Progress, Exercise library and Settings. Jakob's law says Profile is where people look for settings, and the collapsed groups with summaries follow Hick well, so keep Settings there. Improve it: (a) the XP explainer paragraph is always visible; move it behind an info tap; (b) rename "Coach and AI" to "Advanced: AI" and move it just above About; (c) Progress and Exercise library are not profile things: surface Progress from Home or Workouts later, and keep the library under Workouts. Do not add a sixth tab.
3. **Plan builder: the Build button is far from the deep questions.** After answering rows at the bottom, the person must scroll up to "Build my plan". The review screen has the same problem ("Use this plan" at the bottom of a long list). Fix: a sticky bottom button bar (Fitts, Von Restorff). Also flip the chevron on "Make it fit me perfectly" when expanded.
4. **Change-plan sheet order.** "Make a plan with Gemini" is first, "Build my plan" second. With AI off by default, "Build my plan" should be first, ready-made second, Gemini copy/paste third.
5. **Coach marks cover what they explain.** The Home tip hides the workout title line; the set-tick tip covers the set rows. Place them so the anchor stays visible, or shorten the text to one line.
6. **Food tip text mentioned a camera icon that is hidden when AI is off.** Fixed in this pass (separate text when AI is off).
7. **Notification permission prompt appears the moment the first workout starts.** It interrupts the main loop (Doherty, Fitts). Ask later, for example after the first finished workout, when the rest timer is explained.
8. **Meal dropdown shows only "Dinner" with an arrow.** Clear enough once tapped, but a label such as "Meal: Dinner" would explain it without a tooltip.

## Not judged yet
Home (seen only top part), Progress, History, Exercise library, Badges, Backup, Workout complete, Live workout beyond the first screen, Arabic and 200% text. A second pass on a device with data is needed.

## Second pass: screens seen with data (1 workout finished)
Judged against the three main uses: (1) start and log a set in one tap, (2) log a meal in under 5 seconds, (3) see what is left today and the next target.

9. **Workout complete (the peak-end screen) is a stack of five cards** (level, badge, reminder nudge, next up, debrief), and the loudest button is "Remind me", not the exit. "Done" is only at the very bottom after scrolling. The Peak-End law and Von Restorff ask for one dominant action. Fix: put the result and level on top, merge "Next up" into one line, keep the debrief, make "Done" a sticky bottom button, and make the reminder nudge a quiet text row (it is a good contextual moment to ask, so keep it, but smaller).
10. **Live workout works for loop 1.** One tick per set, the effort row appears after a tick, the rest bar shows next exercise, the tip showed once. Small: the "Now: ..." pointer stays on the first warm-up while the person works another group; rest bar sits over the bottom of the list.
11. **Home**: when today's workout is done the card drops below streak and month cards; before training it sits right under the rings, which is right for loop 1. The "Set up food targets" card is a good prompt for loop 3 and should stay above the streak.
12. **Progress**: dense but each block answers one question. Sparse for new users ("Two sessions ... and the line appears") which is honest. No change needed now.
13. **History** and **Exercise library**: simple lists with search and muscle chips. No change needed now. The library is reached from Profile; Workouts is its natural home.
14. **Profile**: first screen is identity and XP text; the settings start below the fold, after Badges, Body weight, Progress and Library.
