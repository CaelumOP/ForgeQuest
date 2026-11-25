# Master upgrade: Question Gate (single stop)

Answer once, e.g. "All defaults except #7". Answers get locked into `docs/MASTER_SPEC.md`.
Evidence (outside the repo, shared notes folder of session local_a06498bf): `stage2-code-map.md` (file:line map), `stage2-grader-scorecard.md`, `stage2-ux-web-notes.md`. Dev grader: `web/src/domain/plan-grader.ts` (test-only).

## A. Plan builder, grader, supersets (3A)
1. Where the choice lives: "Workout style" inside Build my plan > Make it fit me perfectly > Style, options Auto / Straight sets / Supersets / Circuit. **Default: Auto** = supersets when the session is 30 min or less, straight sets otherwise; Fat loss keeps circuits.
2. Pairing rules: push with pull first, then upper with lower or core; never the same main muscle back to back; never two spine-loading lifts (squat, deadlift, RDL, good morning, barbell row) in one pair; the day's first big lift stays alone for Strength. **Default: yes.**
3. Rest inside a pair: **Default 0 s** (today it is a 10 s transition), the exercise's normal rest after each full round.
4. Fat-loss circuits currently carry 120 s rest per exercise. **Default:** rest only after the round (60 s), none between moves.
5. Fat loss 6 days scores worst (57-64/100: same muscles hard on back-to-back days). **Default:** alternate A/B full-body days so a muscle is not hit hard two days running.
6. Warm-up picks: calf raise is the warm-up on push and pull days. **Default:** warm-up matches the day (arm circles / band pull-aparts upper, bodyweight squats lower).
7. Plan editor: "Pair with next" and "Part of a circuit" are two competing toggles. **Default:** one control "Group with next" (stored fields unchanged).
8. Quick workouts (no plan) stay without supersets. **Default: yes.**
9. Grader: port to Android, same scores both platforms, lock floors in tests. **Default:** floors = the after-change scores minus 2 points per group, plus every single plan >= 60.

## B. Input & Test Baseline (3B)
10. Entry points. **Default:** a "Find your starting point" card on the Workouts tab until the first workout, a row in Profile > You and your goals, and the push-up / pull-up fields in Build my plan > You open the same flow.
11. Push-up / pull-up max fields. **Default:** become baseline entries (one store); the builder reads them from there.
12. Storage. **Default:** new table `baseline_tests` (Room migration 10 -> 11, new Dexie version), included in backup JSON as a new optional field (old backups still restore). Never stored as a workout, so no streak / XP / history / PR / volume.
13. Working targets. **Default:** bodyweight reps 70 % of max (at least 1; 0 suggests the easier version); holds 70 % of max hold on the 5 s grid (at least 5 s); weighted: capped estimate from a submax set (reps over 12 asks for a heavier retest), start at the plan's minimum reps with ~3 reps left, x 0.9, snapped down to the plate step. Never asks for a 1-rep max.
14. Precedence and labels. **Default:** real history > your baseline > estimate from a related lift > blank; card says "From your baseline" or "Estimated start". Weighted lifts never set bodyweight targets.
15. Dumbbell moves with no data show "0 kg" today. **Default:** show "Add weight" (blank) and offer "Test this move".
16. Rest between tested moves. **Default:** optional 90 s timer, skippable.

## C. UX rent-check cut list (3C)
KILL
17. Home status line "Push today · 140 g protein to go" (repeats the ring and the Today card). Nielsen minimalism.
18. Progress tagline "Every number with its direction". Minimalism.
19. Progress "This week · Workout days · Sets" line (repeats Training load). Minimalism.
20. Progress "Recovery" text card (repeats the Workouts muscle map). Minimalism.
21. Progress "Then vs now" card (repeats the tile grid). Minimalism.
22. Live workout: "How to do it" and "Swap exercise" pills on every card (header tap and the ⋮ menu do both). Hick.
23. Warm-up "Mark warm-up done" button (the tick box does the same). Hick.
24. Profile top-bar Settings gear (the Settings card is right there). Minimalism.
25. About page copy of "How to use ForgeQuest". Minimalism.
26. Health Connect "Test connection" button (debug tool). Tesler.
HIDE (one tap deeper)
27. Live: the form cue sentence on every card -> inside "How to do it". Progressive disclosure.
28. Rest panel preset chips 60/90/120/180 and "Copy last set" -> tap the timer to show them; keep -15 s, +15 s, Skip. Hick + Fitts.
29. Plate breakdown chip on every barbell row -> tap the weight. Progressive disclosure.
30. Workouts muscle map (legend + 16 chips, all "Fresh" for a new user) -> one line until the first workout, then the map. Minimalism.
31. Home "Getting started": drop "Add a body measurement" and "Add a friend" (3 items left). Hick.
32. Profile banner "Paste a free Gemini key" -> shown on the Food scan screen only. Real-world match.
33. Plan editor Schedule card (Weekdays / Rotation) -> inside "Change plan". Progressive disclosure.
34. Plan item Double / Linear / Maintain -> "Advanced", renamed "Add reps first / Add weight each time / Keep the same". Jargon.
35. History cards: score line, "Add a note", "Workout debrief" rows -> inside the workout detail. Minimalism.
36. Workout complete: at most 1 extra insight sentence. Minimalism.
AUTOMATE / MERGE
37. Coach tips never open over the rest timer or a set row; one tip per screen, after the first tick. Tesler.
38. Home protein stat repeats the ring -> merged into the ring. Minimalism.
39. "Where do you train?": web uses pills, Android uses 3 big cards -> sliding pills on both (with 3C pills). Consistency.
40. "Training load 64 %" ring -> plain "This week vs your usual: +12 %". Real-world match.
41. Change plan sheet "Share/Import plan JSON", Gemini page "API key", "Gem" -> "Share plan", "Import a plan", "Connect Gemini". Jargon.
42. Broken catalog cue text (e.g. Lying One-Arm Lateral Raise ends "...the leg of the.") -> audit and fix every truncated cue, EN + AR. Real-world match.
43. Goal subtitle duplicate "Build muscle / Build muscle · 8-12 reps" -> "Moderate weights · 8-12 reps · 90 s rest" (AR «أوزان متوسطة · 8–12 تكرار · راحة 90 ث»). Minimalism.

## D. Timer and pills notes (no question, will do)
- Android rest wave polls every 250 ms and emits once per second, smoothed by a spring; switch to the frame clock (`withFrameMillis`) from `endsAt`. Web WaveBar same with requestAnimationFrame. Web live dock has no max width (stretches on desktop): cap at the 640 px column. Android cold process death does not rebuild the running rest from the saved alarm time.
- Wave today: Android M3 `LinearWavyProgressIndicator` 4 dp stroke, 40 dp wavelength; web 40 px wavelength, 3 px amplitude. Target per mission: 3-4 stroke, 28-36 wavelength, 3-4 amplitude, visible flat track 14-18 % contrast.
