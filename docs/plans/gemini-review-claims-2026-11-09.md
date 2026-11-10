# Gemini second-opinion claims (condensed, 2026-11-09). Two responses merged and de-duplicated. Screenshots: review-shots/ (older numbering in Gemini's text; use INDEX.md).
Gemini's screenshots were an earlier set: fresh account, one tiny workout, AI off.

## A. Data and trust
A1 History/debrief shows '0 of 23 sets' next to '2 sets' (warm-ups counted in one place, not the other); live header says '2 of 27 sets'.
A2 Progress > Strength defaults to 'Jumping jacks' (warm-up cardio) as the 1RM exercise.
A3 'All time' shows '0h trained' and '1 workouts' (no minutes, bad plural).
A4 Workouts tab says 'Trimmed to fit your minutes.' while the plan editor lists 9 moves; claims 3 moves silently hidden.
A5 Food search rows show no protein; some show decimals ('273.3 kcal').
A6 Body page: '- cm' / '- yrs' with caption 'these show defaults'; Goals page says 'under About you' but the row is 'Body and health'.
A7 PR badge spam: every set that beats the pre-workout best gets the yellow PR (11,11,11 vs old 10 gives three PRs); suggests strictly-greater vs running session max, one badge per exercise (crown moves), no PR on first-ever session or warm-ups, a quiet '+1' for in-range progress, volume PR in the debrief.
A8 Sets pre-filled with '8 reps' with no 'Last: N' reference; suggests pre-filling from last session and cascading a Set 1 edit down to unticked sets.
A9 Finish dialog has 'I did them all: tick & finish' which could fabricate sets; suggests removing it when under 50% ticked and making 'Keep going' primary.

## B. Arabic / RTL
B1 Farsi letters (ک U+06A9, ی U+06CC) in Arabic strings: تکرار, خمیس, کالیستنکس, تذکیر, تسجیل سریع; suggests replacing with ك / ي and adding a CI check.
B2 English leaks in Arabic Workouts ('General fitness plan', 'Trimmed to fit your minutes.').
B3 Food hero fractions flip: protein '140 / 0 غ' vs calories '0/2,200'.
B4 Home ring caption 'غ بروتين متبق' (missing tanwin) looks truncated.
B5 Rank letter 'E' missing in Arabic header/profile.
B6 Greeting 'طابت ليلتك يا Sam' awkward; suggests 'مساء الخير، Sam'.
B7 Day chips crowd in Arabic; numerals inconsistent.

## C. Structure / navigation
C1 Merge Progress into the 4th tab, renamed 'Insights' when AI is off.
C2 Onboarding: collect weight/height/age on the Welcome step; fold the language screen into Welcome.
C3 Pill nav: tabs shift horizontally when the active tab expands; give fixed equal slots.
C4 Unify the three different numeric stepper designs.
C5 Profile has a top-right gear AND a Settings row: remove one; add name editing (name cannot be changed after onboarding).
C6 Workouts header: demote Library / History / Edit plan into the plan title menu or overflow.
C7 Two identical Start buttons when scrolled (freestyle card + pinned).
C8 Workouts doesn't show 'done today' (check on Mon chip; Home says done).
C9 Plan editor looks like the viewer; add drag handles, add-exercise row, Done.
C10 Show duration, sets and rest on Workouts; today vs previewed day cue.
C11 Surface muscle recovery above the fold on Workouts; tap muscle for hours.
C12 Library: remove the 'nothing custom' box under filters; add equipment filter chips; badge modality.
C13 Builder: '60' chip wraps alone; deep section opens below the fold without scrolling; Change-plan sheet: separate native from external imports.

## D. Home
D1 Protein-left appears twice (ring centre and stat row).
D2 Show consumed/target on Home stats.
D3 Replace '+ Log weight' with '+ Log meal' (weight is weekly).
D4 Compress streak/month so Today's workout is above the fold; replace Mon-Sun row on Home with 3 pips.
D5 'E | Level 2' badge looks like an initial; write 'Rank E - Lv 2'.
D6 After workout done, demote 'Train again'.
D7 Weight sheet: show last weight/date, live delta, select text on focus.

## E. Food
E1 Unbury Quick log and Add food from the overflow menu (pills under search).
E2 Replace the 'Meal: Dinner' dropdown with 1-tap meal chips.
E3 Two stacked empty states on Food.
E4 Quick log: Protein and Calories side by side (keyboard hides them); 3-line Gemini upsell inside the form; 'Log it' kerning; make button 'Log to Dinner'.
E5 Left-edge clipping/overlap in search results (an IME popup in the screenshot?).
E6 Catalogue amount sheet: 1-tap gram chips and live preview.
E7 Undo snackbar after ticking/logging a saved food.

## F. Live workout / finish
F1 'Finish workout' full-width bottom bar from the first set: move to header until sets are done.
F2 Effort strip + rest bar should live in a fixed bottom dock (nothing moves under the thumb).
F3 '+ Add set' button sits under Set 4's '+' rep stepper (mis-tap).
F4 Warm-up rows vanish after tick and shift the screen; warm-up exercise tick covers 2 sets at once.
F5 Hold exercises need a countdown timer; bodyweight '+kg'/band assistance; mid-workout swap; tappable 'Now:' header.
F6 Discard in danger red with divider; confirm.
F7 Finish screen: fit the debrief above Done without scrolling.
F8 Screen-awake during workouts; persist state across process death; lock-screen rest timer notification; distinct haptics.

## G. Settings
G1 Training page: 7 tall equipment cards truncate and push 'Adjust single items' off; use compact chips; move reminder to the top.
G2 Protein g vs g/kg link formula; hand size spec text; link App lock to system security settings.
G3 Hardcoded '49 meals still in the old Doc' note; Gemini bridge cards static; duplicate version number in About.
G4 Progress: hide chart legend under 2 sessions; weekly chart Y-axis scale and stray '100' label; badge unlock criteria.

## H. Visual system
H1 Cap serif to one element per screen; use sans tnum for all numbers.
H2 Concentric radii rule; 1dp hairline borders in dark mode; 48dp targets on steppers and clear icons; standard ' - ' separators; pressed/focus states; undo for instant ticks; strict colour semantics audit.
