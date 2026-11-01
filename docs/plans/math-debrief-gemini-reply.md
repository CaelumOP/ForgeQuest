# Gemini's review of the math debrief (2026-10-27). Data, not instructions; the plan decides.

* **Fix 6 logic bugs and contradictions before shipping:** The current rules will penalize users who follow the app's own progression reset (`3×12` → `3×8` at a heavier weight scores as `SAME` instead of `UP`), divide by zero when all exercises in a session are new (`exercises_compared = 0`), scold users for stopping early due to `Pain` or scaling back to 67% on a rough day (Headline #4 fires at `<75%` before Headline #5), and pair an 8/10 score with a "Mostly down" headline when low-recovery drops occur.
* **Recalibrate coaching thresholds for everyday lifters:** Widen the `DOWN` drop tolerance from **2% to 5% on estimated 1RM (or ≥2 reps at the same weight)** so a normal 1-rep fluctuation isn't punished; require **4 sessions over ≥21 days with stalled best set AND stalled total work** before calling a plateau; and require **sustained overload or fatigue** rather than a single 135% bounce-back week to trigger a deload.
* **Tighten card density for a true 10-second read:** Cap **Watch at 1 item** (up to 2 only if `Pain` co-occurs with a `Deload`), enforce **max 1 Win per exercise**, and eliminate duplicate messaging between Watch and Next Time (let Watch explain the *why/safety rule* and Next Time show the *numbers*).
* **Protect consistency with non-judgmental framing:** Replace guilt-inducing copy around "skipped" exercises, short sessions, and un-forgiven drops with supportive, forward-looking prompts, and suppress "skipped exercise" warnings whenever a user swaps in an unplanned exercise or stops for pain.
* **Add 2 high-value deterministic insights from existing data:** Detect **Effort Mastery** (same weight and reps completed at lower perceived effort, e.g., `Hard` → `OK/Easy`) and **Short-Sleep Resilience** (`sleep_minutes < 360` with steady execution), both of which turn apparent "same numbers" sessions into motivating wins.

---

## 1. Findings (Ordered by Impact)

### Finding 1: Load-Step Progression Resets (`8–12` Rep Range) Score as "SAME" Instead of "UP" (Contradiction & Missing Situation)

* **The Flaw:** Under the progression prefill, when a user hits `3×12` at the top of an `8–12` rep range, the app adds one load step (`+2.5 kg`) and resets target reps to `8` (`3×8`). Under the score rule, *"a heavier weight with fewer reps (total at least half of last time) counts as SAME"* unless total work rises. Because dropping from 12 reps to 8 reps reduces total volume by 33.3% (which rarely gets offset by a +2.5 kg load step unless the starting weight is under 5 kg), following the app's exact progression prescription is graded as **SAME** (`0.5` points).
* **Set-by-Set Example:**
* *Last workout:* Incline Dumbbell Press `3 × 12 @ 20 kg` (Total work = $720\text{ kg}$; best set = $20\text{ kg} \times 12$).
* *Today (following prefill):* `3 × 8 @ 22.5 kg` (Total work = $540\text{ kg}$, which is $\ge 360\text{ kg}$ half-work threshold, but $< 720\text{ kg}$).
* *Broken Outcome:* If the user graduates weight like this on all 3 exercises in their workout, every exercise is scored `SAME` (`0.5`), progress points drop to $\lfloor 4 \times 1.5 / 3 \rfloor = 2/3$, and **Headline #8 ("Everything the same")** fires right above **Win #2 ("Incline Press: 20 → 22.5 kg")**—a direct contradiction on a day the user lifted heavier across the board.


* **Concrete Fix:** Classify an exercise as **UP** (`1.0` point) whenever the heaviest working weight increases AND the user completes all planned sets at or above the bottom of the target rep range (with total work $\ge 60\%$ of last time). Keep the `SAME` rule only for heavier sets that fall *below* the target rep floor (with total work $\ge 50\%$ of last time).

### Finding 2: Rough-Day Forgiveness Contradicts Headline #4 (`< 75%` Sets) and Headline #9 ("Mostly Down") (Contradiction)

* **The Flaw:** There are two collisions between the workout score rules and the headline priority list on rough days (`recovery < 50%` or `Pain`):
1. **The 67% vs. 75% Collision:** On a rough day, the score rule scales planned sets to **67%** (so completing 67% of planned sets earns full completion credit). However, **Headline #4 ("Under 75% of planned sets")** sits *above* **Headline #5 ("Rough day and nothing went down")** and checks raw planned sets.
2. **The Forgiven-Drop Collision:** On a `<50%` recovery day, the score rule forgives all drops as `SAME`. However, **Headline #5** explicitly requires *"rough day and nothing went down"*. If a user's numbers drop by 5% on a 35% recovery day, Headline #5 fails, and the user falls through to **Headline #9 ("Mostly down")**—even though their workout score forgave the drops and gave them an **8/10 or 9/10**!


* **Set-by-Set Example:**
* *User State:* Watch recovery = `38%`. Planned workout = 9 sets across 3 exercises.
* *Today:* User smartly scales back to 2 sets per exercise (6/9 sets = `66.7%`) and lowers Squat from `3 × 10 @ 80 kg` to `2 × 8 @ 75 kg` (`Easy/OK`).
* *Broken Outcome:* Score gives `3` (base) + `3` (6/6 effective sets) + `2` (all forgiven as `SAME`) = **8/10**. Yet Headline #4 fires (*"Under 75% of planned sets"*) or, if they did 7 sets, Headline #9 fires (*"Mostly down"*).


* **Concrete Fix:**
1. Define `effective_planned_sets` as $\lceil 0.67 \times \text{planned\_sets} \rceil$ on low-recovery days (`recovery < 50%`) and exclude any sets skipped after logging `Pain` on that exercise. Use `effective_planned_sets` everywhere (Score, Headline #4, Win #4, Watch #5).
2. Move **Rough Day** above **Under 75% of planned sets** in the Headline order, and include forgiven drops inside the Rough Day headline (splitting variants into *held steady* vs. *smartly scaled back*).



### Finding 3: The 12-Day Plateau Trigger Fires False Positives During Normal Rep-Range Progression (Coaching Soundness)

* **The Flaw:** A plateau is currently defined as *"an exercise done 3 or more times over 12 or more days in the last 35 days, where the best of the last 2 workouts didn't beat the earlier ones."*
1. In an `8–12` rep range, a lifter often progresses by adding reps to *later sets* while their *best set* (Set 1) stays at the same number.
2. Under the Single Rep Target rule, the app itself holds weight constant for 2 workouts in a row before increasing load.
3. Three sessions over 12 days is far too short for a recreational lifter to stall; calling a plateau after 12 days creates unnecessary anxiety and triggers premature deloads.


* **Set-by-Set Example:**
* *Target:* Seated Cable Row, `3 × 8–12 @ 45 kg`.
* *Day 1:* `10, 8, 8 @ 45 kg` (Best set: `10 @ 45 kg`; Total work: $1,170\text{ kg}$).
* *Day 6:* `10, 9, 8 @ 45 kg` (Best set: `10 @ 45 kg`; Total work: $1,215\text{ kg}$).
* *Day 13:* `10, 10, 9 @ 45 kg` (Best set: `10 @ 45 kg`; Total work: $1,305\text{ kg}$).
* *Broken Outcome:* Even though the user added `+1 rep` and `+2 reps` to their working sets and increased total work by 11.5%, the "best set" (`10 @ 45 kg`) in Days 6 and 13 did not beat Day 1. The app flags a **Plateau** (Watch #2) on an exercise that is actively progressing!


* **Concrete Fix:** Redefine **Plateau** as: an exercise performed **4 or more times over at least 21 days in the last 42 days** (excluding rough-day sessions), where **neither the best set nor total working volume** in the last 2 workouts beat the earlier workouts in that window, AND the exercise is not waiting on a 2nd confirmation session or held back by `low recovery`.

### Finding 4: The 2% Drop Tolerance Punishes Normal 1-Rep Fluctuations (Coaching Soundness)

* **The Flaw:** Classifying an exercise as `DOWN` whenever the best set falls by more than **2%** (and total work doesn't rise) is biologically unrealistic for everyday lifters. Whether "best set" is evaluated by weight × reps or Epley estimated 1RM ($\text{weight} \times (1 + \text{reps}/30)$), losing **1 single rep** on a 10-rep set at the same weight is a **10% drop in set volume** and a **2.5% drop in e1RM** ($1.30 / 1.333 - 1 = -2.5\%$). Every single 1-rep dip triggers `DOWN` (`0` points) and **Watch #4 ("A drop that wasn't forgiven")**.
* **Set-by-Set Example:**
* *Last workout:* Overhead Press `10, 9, 8 @ 30 kg` (Best set: `10 @ 30 kg`, e1RM = $40.0\text{ kg}$, Total work = $810\text{ kg}$).
* *Today:* Overhead Press `9, 9, 9 @ 30 kg` (Best set: `9 @ 30 kg`, e1RM = $39.0\text{ kg}$ [$-2.5\%$], Total work = $810\text{ kg}$ [did not rise]).
* *Broken Outcome:* The user matched their exact total reps (27 reps) with cleaner pacing across sets, yet because the top set dropped from 10 to 9 ($-2.5\%$) and total work stayed flat, the exercise is marked **DOWN** and flagged in **Watch #4**.


* **Concrete Fix:** Change the `DOWN` threshold to: best set e1RM fell by **more than 5%** (or dropped by **2 or more reps** at the same weight, or **>15%** on timed holds) **AND** total work fell by **more than 5%**. A 1-rep fluctuation at the same weight counts as **SAME**.

### Finding 5: The 60% Heaviest-Weight Filter Invalidates Working Sets When a Heavy Single Is Tested (Missing Situation)

* **The Flaw:** The rule *"for weighted exercises, only sets at 60% or more of the heaviest weight count toward total work"* is meant to filter out unlabelled warm-ups. However, if a user completes their normal hypertrophy sets (`3 × 12 @ 50 kg`) and then tests one heavy single (`1 × 1 @ 85 kg`), $60\%$ of $85\text{ kg}$ is **$51\text{ kg}$**. All three `50 kg` working sets are suddenly discarded from total work!
* **Set-by-Set Example:**
* *Last workout:* Bench Press `3 × 10 @ 60 kg` (Total work = $1,800\text{ kg}$).
* *Today:* Bench Press `3 × 10 @ 60 kg` + `1 × 1 @ 102.5 kg` (PR!).
* *Broken Outcome:* $60\% \text{ of } 102.5\text{ kg} = 61.5\text{ kg}$. The three `60 kg` sets are excluded. Total work drops from $1,800\text{ kg}$ to $102.5\text{ kg}$ ($< 50\%$ of last time!), so Win #3 ("More total work") is lost and the volume comparison breaks.


* **Concrete Fix:** Anchor the 60% working-set filter to **60% of the heaviest set of 3 or more reps** (falling back to the heaviest set overall only if all sets in the exercise are 1–2 reps).

### Finding 6: All-New Exercises (`exercises_compared == 0`) Cause Division by Zero and Misleading Headlines (Missing Situation)

* **The Flaw:** A returning user can create a new routine (e.g., switching from Upper/Lower to Full Body) where **0 exercises** have prior history, even though it isn't their first workout ever (#1) or after a break (#2).
* In the workout score: `min(3, floor(4 × sum / exercises compared))` divides by zero (`0 / 0`).
* In the Headline rules: rules #6, #7, #8, #9, and #10 all assume `exercises_compared > 0`.


* **Set-by-Set Example:**
* *User State:* Active user (trained 2 days ago). Tries a new kettlebell day: Goblet Squat `3 × 10 @ 16 kg`, KB Swing `3 × 15 @ 20 kg`, Plank `3 × 45 s`. None of these 3 exercises have ever been logged before.


* **Concrete Fix:** When `exercises_compared == 0` (and warm-up-only is false):
* Award `2` progress points in the workout score (so a completed baseline workout scores `3 + 3 + 2 = 8/10`).
* Trigger a dedicated Headline (**#4 in revised order: "Baseline set for new exercises"**) and a Win (**"Baseline set for {count} new exercises"**).



### Finding 7: Deload Triggers Fire on Healthy Comeback Weeks and Mild Sleep Dips (Coaching Soundness)

* **The Flaw:**
1. **135% of 4-week average (min 18 sets):** If a user normally does 18 sets/week, takes a 1-week vacation or busy work week (0 or 6 sets), their 4-week average drops to ~13 sets. The very first week they return to their normal 18-set routine ($18 / 13.25 = 136\%$), the app tells them to **Deload**!
2. **3 of 5 days recovery < 45%:** Three nights of poor sleep from life stress when training volume wasn't high does not require a structural training deload; the app's daily rough-day auto-regulation already scales today's session to 67%.
3. **2 plateaued lifts:** Stalling on Lateral Raises and Bicep Curls simultaneously causes zero systemic fatigue.


* **Concrete Fix:** Trigger a **Deload Signal** only when training load and fatigue intersect:
* **Trigger A (Sustained Volume + Effort):** Weekly working sets are $\ge 130\%$ of the 4-week *active-week* average (weeks with $\ge 6$ sets, min 20 sets this week) **AND** at least 30% of sets this week were marked `Hard` or `Pain`; OR
* **Trigger B (Multi-Day Under-Recovery + Strain):** $\ge 3$ of the last 5 days had recovery $< 45\%$ **AND** at least 2 exercises in today's workout dropped or had sets marked `Hard`/`Pain`; OR
* **Trigger C (Fatigued Stalls):** $\ge 2$ exercises are in a Plateau **AND** at least half of their sets across the last 2 sessions were marked `Hard`.



### Finding 8: Pain Handling Needs Bodyweight Fallbacks, Note vs. Tag Separation, and Repeat-Pain Escalation (Coaching Soundness & Safety)

* **The Flaw:**
1. **Bodyweight/Holds have no load step:** Telling a user *"next time is one step lighter"* when they log `Pain` on a bodyweight Push-up or Plank is nonsensical when weight = `0 kg`.
2. **Free-text note false positives:** Even with negation handling (`"no pain"`), triggering a mechanical `-2.5 kg` weight penalty from note keywords will misfire on notes like *"good glute burn / soreness from Monday"* or *"wrist wrap helped old injury"*.
3. **Stopping for pain gets punished as "Skipped":** If a user logs `Pain` on Set 1 of Bench Press and smartly stops (skipping Sets 2 and 3, or skipping the rest of chest day), **Watch #5 ("Planned exercises skipped")** or **Headline #4 ("Under 75% sets")** penalizes them for safe clinical behavior.
4. **Repeat pain:** If an exercise hurt last session, was dropped one load step today, and *still* hurt today, dropping another 2.5 kg isn't safe—they need to swap it immediately.


* **Concrete Fix:**
* **Explicit Tag vs. Note Keyword:** Only apply the automatic load/rep reduction when the user taps the **`Pain`** effort button. If pain words are detected in the note *without* the `Pain` tag, show a supportive prompt in Watch (*"You mentioned discomfort in your note on {exercise}—tap 'Pain' on the set if you want next time's target lowered"*).
* **Bodyweight/Hold Pain Step:** For bodyweight reps, step back by **$-20\%$ reps (min $-2$ reps) or suggest an easier regression**; for holds, step back by **$-10\text{ s}$ (or $-20\%$) settled duration**.
* **Repeat Pain:** If `Pain` is logged **2 workouts in a row** on the same exercise, change both Watch and Next Time to: **"Pain two sessions in a row on {exercise}—swap it for a pain-free variation next time."**
* **Pain Forgiveness on Skipped Sets:** Any remaining planned sets on an exercise where `Pain` was logged are excluded from `effective_planned_sets`.



### Finding 9: Card Density & Priority Caps (Product Design & Motivation)

* **The Flaw:** Showing **2 Wins + 2 Watch + 3 Next Time + "Everything else: same numbers"** creates up to 9 lines of bulleted data, breaking the 10-second read goal and duplicating information between **Watch** and **Next Time**:
* Watch #1 says *"next time is one step lighter"* AND Next Time says *"one step lighter after pain"*.
* Watch #6 says *"sets marked Hard, keeping the same weight"* AND Next Time says *"Everything else: same numbers"*.
* Wins #1, #2, and #3 can all fire on the *exact same exercise* (e.g., Squat hits a weight PR, which is also the biggest gain in best set AND more total work, eating both Win slots for Squat while ignoring a second exercise).


* **Concrete Fix:**
* **Deduplicate Wins by Exercise:** Maximum **1 Win per exercise** (so the user sees up to 2 different wins across their session).
* **Cap Watch at 1 Item (2 only for Pain + Deload):** Everyday trainees feel discouraged when handed multiple critique bullets after finishing a workout. Show only the **single highest-priority Watch item** (allow 2 only if #1 Pain and #3 Deload both occur).
* **Separate Roles of Watch vs. Next Time:**
* **Watch** explains the *coaching pattern or safety rule* (e.g., *"If {exercise} hurts again next session, swap it out"* or *"You've hit a plateau on {exercise}"*).
* **Next Time** owns the *exact prescription changes* (`+2.5 kg`, `+1 rep`, `-2.5 kg after pain`, `Holding at 60 kg (last set was Hard)`).


* **Cut Watch #6 ("Sets marked Hard") and Watch #7 ("Low recovery") as standalone Watch items:** Instead, fold `Hard`-held exercises into a clear Next Time line when the user otherwise hit the top of their rep range (*"{exercise}: holding at {weight} until it feels smoother"*), and let the Rough Day headline + Next Time footnote handle low recovery.
* **Reframe "Skipped" Exercises (Watch #5):** Suppress Watch #5 completely if the user logged an unplanned substitute exercise (treat as a **Swap**), if `Pain` or `Rough day` occurred, or if Headline #4 (short session) is already displayed. When shown, frame it as a neutral scheduling tip rather than a failure.



### Finding 10: Two High-Value Deterministic Insights Worth Adding (Item 7)

Using only the fields the app already collects, add these two rules to reward consistency when raw weight/reps stay flat:

1. **Effort Mastery ("Same Numbers, Easier Effort") — New Win:**
* *Trigger:* An exercise is classified as `SAME` (same weight and $\ge$ last session's reps/time), has `0` Pain sets, and the number of sets marked `Hard` decreased compared to last time (or shifted from `OK` to `Easy` on at least half the sets).
* *Why it matters:* Under the Single Rep Target rule (or after holding weight due to a `Hard` set), users often repeat the exact same numbers before earning a weight jump. Celebrating that `3×10 @ 50 kg` felt `OK` today after feeling `Hard` last week proves they got stronger even before the iron goes up.


2. **Short-Sleep Smart Execution — Rough-Day Equivalent & Win:**
* *Trigger:* `sleep_minutes > 0` and `sleep_minutes < 360` (under 6 hours of sleep, even when watch `recovery %` is missing), and the user completes $\ge 67\%$ of planned sets with `0` Pain sets.
* *Why it matters:* Many wearables export sleep duration without a proprietary recovery percentage. Treating `< 360` sleep minutes as a **Rough Day** protects users after a short night's sleep and praises them for showing up safely.



---

## 2. Revised Rules (Full Ordered Lists)

### A. Core Definitions & Contradiction Prevention Rules

1. **Rough Day (`is_rough_day`):** `true` if `recovery_pct < 50`, OR `sleep_minutes < 360` (when logged), OR `Pain` is tagged on any working set.
2. **Comeback Day (`is_comeback`):** `true` if it has been $\ge 14$ days since the user's last non-warmup workout.
3. **Effective Planned Sets (`effective_planned_sets`):**
* Start with `planned_sets` (excluding warm-ups).
* If `Pain` is tagged on an exercise, subtract any uncompleted planned sets for that exercise.
* If `recovery_pct < 50` or `sleep_minutes < 360`, multiply remaining planned sets by `0.67` (rounded up: $\lceil 0.67 \times \text{sets} \rceil$).


4. **Working Sets for Total Work (`working_sets`):**
* Exclude warm-up sets.
* For weighted exercises, let `ref_weight` be the heaviest weight among sets with `reps >= 3` (or the heaviest weight overall if all sets have `reps < 3`). Include all non-warmup sets with $\text{weight} \ge 0.60 \times \text{ref\_weight}$.


5. **Exercise Comparison Status (`UP`, `SAME`, `FORGIVEN_DOWN`, `UNFORGIVEN_DOWN`):**
* Only compare exercises that have at least 1 prior non-warmup session (`exercises_compared`).
* **UP (`1.0` pt):**
* Heaviest working weight increased AND all completed working sets hit at least the bottom of the target rep range (with total work $\ge 60\%$ of last time); OR
* Best set (by e1RM $\text{weight} \times (1 + \text{reps}/30)$, reps for bodyweight, or seconds for holds) increased by $> 0\%$ without dropping total work below $80\%$ of last time; OR
* Best set matched last time AND total work increased.


* **RAW DOWN:** Best set fell by **$> 5\%$ in e1RM** (or dropped by **$\ge 2$ reps** at the same weight, or **$> 15\%$** in hold seconds) **AND** total work fell by **$> 5\%$** (and does not qualify as heavier-weight `SAME` where weight rose and total work $\ge 50\%$ of last time).
* **FORGIVEN_DOWN (`0.5` pts, counts as `SAME` in score):** Exercise is `RAW DOWN`, AND (`is_comeback` is true, OR `recovery_pct < 50`, OR `sleep_minutes < 360`, OR `Pain` was tagged on this exercise today).
* **UNFORGIVEN_DOWN (`0.0` pts):** Exercise is `RAW DOWN` and not forgiven.
* **SAME (`0.5` pts):** Everything else (including 1-rep fluctuations at the same weight, or heavier weight below target rep floor with total work $\ge 50\%$ of last time).


6. **Workout Score (4–10):**
* Hidden (replaced by kind line) if `is_first_ever` or `is_comeback` or `only_warmups`.
* Base `3` + Completion `0–3` ($\min(3, \lfloor 3 \times \text{sets\_done} / \text{effective\_planned\_sets} \rfloor)$, or `3` if `effective_planned_sets == 0`) + Progress `0–3` ($\min(3, \lfloor 4 \times \sum \text{pts} / \text{exercises\_compared} \rfloor)$, or `2` if `exercises_compared == 0`) + `1` for any PR (capped at 10).



### B. Headline Priority Order (First Match Wins)

1. **First workout ever** (`prior_workouts_count == 0`)
2. **First workout after a break** (`days_since_last_workout >= 14`)
3. **Only warm-ups done** (`working_sets_done == 0` and `warmup_sets_done > 0`)
4. **All-new exercises / Baseline session** (`exercises_compared == 0` and `working_sets_done > 0`)
5. **Rough day, held steady or progressed** (`is_rough_day == true` and `unforgiven_down_count == 0` and `forgiven_down_count == 0`)
6. **Rough day, scaled back smartly** (`is_rough_day == true` and `forgiven_down_count > 0` and `unforgiven_down_count == 0`)
7. **Short session, habit kept** (`sets_done < 0.75 * effective_planned_sets` and `pr_count == 0`)
8. **A PR and up on at least half** (`pr_count > 0` and `up_count >= 0.5 * exercises_compared`)
9. **Up on at least half (or PR in a focused session)** (`up_count >= 0.5 * exercises_compared` OR `pr_count > 0`)
10. **Everything the same** (`same_count == exercises_compared`, with `up_count == 0` and `unforgiven_down_count == 0`)
11. **Mixed session** (`unforgiven_down_count > 0` and `unforgiven_down_count <= 0.5 * exercises_compared`)
12. **Lighter day / Mostly down** (`unforgiven_down_count > 0.5 * exercises_compared`)

### C. Wins Priority Order (Max 2 Total, Max 1 Per Exercise)

1. **PRs** (Weight PR > Rep PR > Hold duration PR on any exercise)
2. **Biggest gain in best set** (`UP` exercise with the largest % increase in weight or reps/seconds)
3. **More total work at same or heavier weight** (`UP` exercise where total work rose at $\ge$ last session's weight)
4. **Effort mastery (New)** (`SAME` exercise where weight & total reps/time $\ge$ last time, `Pain == 0`, and `Hard` sets decreased or `Easy` sets increased)
5. **All effective planned sets done** (`sets_done >= effective_planned_sets` and `effective_planned_sets > 0`)
6. **Weekly target hit** (`workouts_this_week == weekly_target` on the session that crosses the threshold)
7. **Exercise returned after a long gap / Baseline set** (exercise not done in $\ge 42$ days, or first time logging a new exercise when `exercises_compared == 0`)

### D. Watch Priority Order (Max 1 Item; Max 2 Only if #1 and #3 Both Match)

1. **Pain logged (Tag or Note):**
* *1a (Repeat Pain — 2nd session in a row with Pain tag):* Recommend immediate exercise swap.
* *1b (Single session Pain tag):* Note that next session is stepped back; swap if pain returns.
* *1c (Pain keyword in note without Pain tag, negation excluded):* Gentle prompt reminding the user they can tag the set as `Pain` to auto-adjust next time.


2. **Deload signal** (Sustained high volume + effort, or multi-day low recovery + today's strain, or $\ge 2$ fatigued plateaus; suppressed on `is_comeback`).
3. **Plateaued lift** ($\ge 4$ sessions over $\ge 21$ days in last 42 days where neither best set nor total work in last 2 beat earlier ones; suppressed on `is_comeback`).
4. **Unforgiven drop** (`UNFORGIVEN_DOWN` on an exercise; suppressed if Headline #12 already frames the whole session as lighter).
5. **Planned exercise skipped with no swap** (A planned exercise had `0` sets logged, `unplanned_exercises_logged == 0`, `is_rough_day == false`, and Headline #7 did not fire).

### E. Next Time Priority Order (Max 3 Explicit Changes + 1 Summary Line)

* *Shown only on the newest workout containing each exercise.*
* *Calculated assuming normal recovery next time, EXCEPT `Pain` always steps back, and exercises that hit top-of-range/target reps with `Hard` effort explicitly explain why they are holding steady.*

1. **Pain step-back or swap:**
* Repeat pain (2× in a row): Swap exercise suggestion.
* Weighted pain: `-1 load step (-2.5 kg / -5 lb)`.
* Bodyweight / hold pain: `-2 reps` or `-10 s` (or easier variation).


2. **Ready for a harder bodyweight variation** (2 workouts in a row, $\ge 2$ sets at ready-rep threshold or $1.5\times$ hold target, no `Hard` or `Pain`).
3. **Load increase** (`+2.5 kg / +5 lb` and rep reset to bottom of range, or after hitting single rep target $2\times$ in a row with `Easy/OK`).
4. **Rep or hold increase** (`+1 rep` or `+5 s` when all sets were completed with `Easy/OK` effort).
5. **Holding weight to consolidate `Hard` effort** (Only shown as an explicit slot if the user otherwise hit the top of their rep range or progressed, so they aren't confused why weight isn't rising: *"{exercise}: stay at {weight} until it feels smoother"*).

* **Folded remainder:** *"Everything else: same numbers."*
* **Footnote (always shown beneath Next Time if user links health data or had a rough day):** *"Targets assume normal recovery—the app eases off automatically on low-recovery days."*

---

## 3. Templates (English & Formal Modern Standard Arabic)

### English Templates (EN)

#### Score Line (When Score Is Hidden)

* **First workout ever:** `Baseline session logged—your starting numbers are locked in.`
* **First after a break:** `Welcome back—today is all about getting moving again, no score needed.`
* **Only warm-ups:** `Mobility and warm-up logged—every bit of movement keeps the habit alive.`

#### 1. Headlines (EN) —Picked deterministically by `workout_id % variant_count`

* **H1. First workout ever:**
* V1: `Workout #1 is in the books. Your baselines are set.`
* V2: `First session done—the hardest step is behind you.`
* V3: `Starting line crossed. Every future workout builds from here.`


* **H2. First workout after a break ($\ge 14$ days):**
* V1: `Good to have you back. Showing up after a break is the biggest win.`
* V2: `Back in rhythm—today sets the floor for your next block.`
* V3: `You broke the gap and got back under the bar. That's what counts.`


* **H3. Only warm-ups done:**
* V1: `Warm-up session complete—way better than skipping the gym entirely.`
* V2: `Loosened up and kept the routine intact.`


* **H4. All-new exercises (Baseline session):**
* V1: `Fresh exercises logged—your starting numbers are dialed in.`
* V2: `New movements on the board. Next time we build on these baselines.`


* **H5. Rough day, held steady or progressed:**
* V1: `Low-tank day, rock-solid numbers. You managed your energy well.`
* V2: `You held your ground on a tough day—that takes real grit.`
* V3: `Strong showing despite a rough day. Nothing gave way.`


* **H6. Rough day, scaled back smartly:**
* V1: `Smart call dialing it back today—you protected the habit without forcing it.`
* V2: `Tough recovery day, sensible adjustments. You'll bounce back fresher.`
* V3: `You listened to your body and got the work in safely.`


* **H7. Short session (`< 75%` of effective planned sets, no PR):**
* V1: `Short and focused—getting part of it done beats zero every time.`
* V2: `Quick session in the bank. Consistency comes before perfection.`
* V3: `Tight on time, still showed up. The streak stays alive.`


* **H8. A PR and up on at least half:**
* V1: `New personal record and moving up across the board.`
* V2: `Big day—a fresh PR and gains on most of your lifts.`
* V3: `Firing on all cylinders: new PR and steady progress throughout.`


* **H9. Up on at least half (or PR in a short session):**
* V1: `Solid forward momentum—numbers climbed on most of your work.`
* V2: `Clear progress today. The consistency is paying off.`
* V3: `Brick by brick—you nudged the numbers up right on schedule.`


* **H10. Everything the same:**
* V1: `Locked in at your working numbers—owning a weight comes before adding more.`
* V2: `Steady across the board. Repeating solid reps is how strength sticks.`
* V3: `Right on target. You held every lift steady today.`


* **H11. Mixed session:**
* V1: `Give-and-take session—pushed ahead on some lifts, paced yourself on others.`
* V2: `A balanced day in the gym: some climbs, some lighter sets.`
* V3: `Normal training rhythm—up in a few spots, steady or lighter in others.`


* **H12. Lighter day / Mostly down:**
* V1: `A lighter session today—strength isn't a straight line, and showing up still counts.`
* V2: `Numbers dipped today, which happens in every training block. Rest up for next time.`
* V3: `Gravity felt a little heavier today—log it, recover, and come back fresh.`



#### 2. Wins Templates (EN)

* **W1. PR (Weight / Reps / Hold):**
* Weight PR: `{exercise} PR: {weight} {unit} × {reps}`
* Rep PR: `{exercise} PR: {reps} reps at {weight} {unit}` (or `{exercise} PR: {reps} reps` for bodyweight)
* Hold PR: `{exercise} PR: held for {seconds} s`


* **W2. Biggest gain in best set:**
* Weight jump: `{exercise}: best set climbed from {old_weight} → {new_weight} {unit}`
* Rep jump: `{exercise}: best set climbed from {old_reps} → {new_reps} reps`
* Hold jump: `{exercise}: best hold climbed from {old_seconds} → {new_seconds} s`


* **W3. More total work at same/heavier weight:**
* `{exercise}: more total work at {weight} {unit} ({old_reps_total} → {new_reps_total} total reps)`


* **W4. Effort mastery (Same numbers, easier effort):**
* `{exercise}: same numbers as last time, noticeably smoother effort`


* **W5. All effective planned sets done:**
* Normal day: `All {sets_done} planned sets completed`
* Rough day (adjusted target): `All {sets_done} adjusted sets completed for today's recovery`


* **W6. Weekly target hit:**
* `Weekly goal reached: {done_this_week} of {weekly_target} workouts done`


* **W7. Return after long gap / New baseline:**
* Gap ($\ge 6$ weeks): `{exercise}: back in rotation for the first time in {weeks} weeks`
* New baseline: `Baseline set for {count} new {count, plural, one {exercise} other {exercises}}`



#### 3. Watch Templates (EN)

* **WA1a. Repeat Pain (2 sessions in a row):**
* `{exercise}: pain logged two sessions in a row—swap it for a pain-free variation next time`


* **WA1b. Single-session Pain tag:**
* `{exercise}: stepped back for next time after pain—if it hurts again, swap it out`


* **WA1c. Pain word in note (no Pain tag):**
* `{exercise}: you noted discomfort—mark a set as "Pain" anytime you want next workout's load lowered automatically`


* **WA2. Deload signal:**
* `Fatigue is stacking up—consider making your next 2–3 sessions a lighter deload (~70% sets or slightly lighter weights)`


* **WA3. Plateaued lift:**
* `{exercise}: steady for {sessions} sessions—try resting an extra 30 s between sets or switching to a close variation`


* **WA4. Unforgiven drop:**
* `{exercise}: dipped from {old_best} to {new_best} today—we'll keep next time's target steady so you can regroup`


* **WA5. Planned exercise skipped (no swap, normal day):**
* `Ran out of time for {exercise}? Put it first in line next session so it doesn't lag behind`



#### 4. Next Time Templates (EN)

* **NT1a. Pain swap (repeat pain):** `{exercise}: swap for a pain-free alternative`
* **NT1b. Pain step-back (weighted):** `{exercise}: -{step} {unit} ({next_weight} {unit}) after pain`
* **NT1c. Pain step-back (bodyweight / hold):** `{exercise}: eased to {next_target} after pain`
* **NT2. Harder variation ready:** `{exercise}: ready to level up to {harder_exercise}`
* **NT3. Load increase:**
* Rep-range reset: `{exercise}: +{step} {unit} ({next_weight} {unit} for {bottom_reps} reps)`
* Single-target jump: `{exercise}: +{step} {unit} ({next_weight} {unit})`


* **NT4. Rep or hold increase:**
* Reps: `{exercise}: +1 rep ({next_reps} reps at {weight} {unit})` (or `{exercise}: +1 rep ({next_reps} reps)` for bodyweight)
* Holds: `{exercise}: +{step_seconds} s ({next_seconds} s)`


* **NT5. Holding steady after `Hard` effort at top of range:**
* `{exercise}: stay at {weight} {unit} × {reps} until all sets feel solid`


* **NT_ELSE. Unchanged exercises summary:**
* `Everything else: same numbers.`


* **NT_NOTE. Auto-regulation footnote:**
* `Targets assume normal recovery—the app eases off automatically on low-recovery days.`



---

### Formal Modern Standard Arabic Templates (AR — العربية الفصحى المعاصرة)

*All numbers use Western digits (`0–9`). Plural rules explicitly handle Arabic grammatical categories: **`zero` (0)**, **`one` (1 — مفرد)**, **`two` (2 — مثنى)**, **`few` (3–10 — جمع مجرور)**, and **`many/other` (11+ — مفرد منصوب تمييزاً)**.*

#### Helper Plural Macros for Arabic (`{count}` → Formatted Noun Phrase)

To keep templates clean and grammatically exact in code, bind these five plural branches per unit:

* **Sets (`{sets_plural}`):**
* `0`: `0 مجموعة` (أو `دون مجموعات`)
* `1`: `مجموعة واحدة`
* `2`: `مجموعتين` (أو `مجموعتان` في حالة الرفع)
* `3–10`: `{n} مجموعات`
* `11+`: `{n} مجموعة`


* **Reps (`{reps_plural}`):**
* `0`: `0 تكرار`
* `1`: `تكرار واحد`
* `2`: `تكرارين` (أو `تكراران` في حالة الرفع)
* `3–10`: `{n} تكرارات`
* `11+`: `{n} تكراراً`


* **Seconds (`{seconds_plural}`):**
* `0`: `0 ثانية`
* `1`: `ثانية واحدة`
* `2`: `ثانيتين` (أو `ثانيتان`)
* `3–10`: `{n} ثوانٍ`
* `11+`: `{n} ثانية`


* **Exercises (`{exercises_plural}`):**
* `0`: `0 تمرين`
* `1`: `تمرين واحد`
* `2`: `تمرينين` (أو `تمرينان`)
* `3–10`: `{n} تمارين`
* `11+`: `{n} تمريناً`


* **Workouts/Sessions (`{sessions_plural}`):**
* `0`: `0 جلسة`
* `1`: `جلسة واحدة`
* `2`: `جلستين` (أو `جلستان`)
* `3–10`: `{n} جلسات`
* `11+`: `{n} جلسة`


* **Weeks (`{weeks_plural}`):**
* `0`: `أقل من أسبوع`
* `1`: `أسبوع واحد`
* `2`: `أسبوعين`
* `3–10`: `{n} أسابيع`
* `11+`: `{n} أسبوعاً`



#### Score Line (AR — When Score Is Hidden)

* **First workout ever:** `تم تسجيل جلستك التأسيسية—أرقامك المبدئية جاهزة للبناء عليها.`
* **First after a break:** `عوداً حميداً—الهدف اليوم هو استعادة النشاط دون الحاجة إلى تقييم رقمي.`
* **Only warm-ups:** `تم تسجيل الإحماء والمرونة—كل حركة تحافظ على استمرارية عادتك الرياضية.`

#### 1. Headlines (AR)

* **H1. First workout ever:**
* V1: `أتممت تمرينك الأول بنجاح، وتحددت أرقامك الأساسية.`
* V2: `اكتملت الجلسة الأولى—لقد تجاوزت الخطوة الأصعب.`
* V3: `نقطة انطلاق ممتازة؛ كل تمرين قادم سيبني على ما أنجزته اليوم.`


* **H2. First workout after a break ($\ge 14$ days):**
* V1: `عودة موفقة! الحضور بعد الانقطاع هو المكسب الأهم.`
* V2: `عدت إلى المسار الصحيح—تمرين اليوم يمهّد الطريق لأسابيعك القادمة.`
* V3: `كسرت فترة التوقف وعدت للتمرين، وهذا هو جوهر الاستمرارية.`


* **H3. Only warm-ups done:**
* V1: `جلسة إحماء خفيفة—أفضل بكثير من تخطي التمرين بالكامل.`
* V2: `حركت مفاصلك وحافظت على روتينك اليومي.`


* **H4. All-new exercises (Baseline session):**
* V1: `تم تسجيل تمارين جديدة—أرقامك المرجعية جاهزة للجلسة القادمة.`
* V2: `حركات جديدة في سجلك؛ سنبدأ التدرج عليها من التمرين القادم.`


* **H5. Rough day, held steady or progressed:**
* V1: `ثبات رائع رغم انخفاض الاستشفاء—أدرت طاقتك بذكاء اليوم.`
* V2: `حافظت على مستواك في يوم مرهق، وهذا دليل على تطور قوتك.`
* V3: `أداء متماسك رغم الإجهاد؛ لم يتراجع أي من أرقامك.`


* **H6. Rough day, scaled back smartly:**
* V1: `قرار ذكي بتخفيف الحمل اليوم—حافظت على العادة دون إجهاد مفرط.`
* V2: `تعديل مدروس يناسب مستوى استشفائك اليوم؛ ستعود أقوى في الجلسة القادمة.`
* V3: `استمعت لجسمك وأنجزت التمرين بأمان ووعي.`


* **H7. Short session (`< 75%` of effective planned sets, no PR):**
* V1: `جلسة مختصرة ومركزة—إنجاز جزء من التمرين يتفوق دائماً على التوقف التام.`
* V2: `تمرين سريع في رصيدك؛ الاستمرارية أهم من الكمال.`
* V3: `رغم ضيق الوقت، سجلت حضورك وحافظت على سلسلة التزامك.`


* **H8. A PR and up on at least half:**
* V1: `رقم قياسي جديد وتقدم واضح في معظم تمارينك اليوم.`
* V2: `جلسة استثنائية—كسرت رقمك الشخصي ورفعت الأداء في أغلب الحركات.`
* V3: `أداء في القمة: رقم قياسي جديد وتطور مستمر عبر التمرين.`


* **H9. Up on at least half (or PR in a short session):**
* V1: `تقدم ملموس اليوم—ارتفعت أرقامك في أغلب التمارين.`
* V2: `خطوة ثابتة للأمام؛ التزامك بدأ يؤتي ثماره بوضوح.`
* V3: `تطور تدريجي ممتاز وفي موعده تماماً.`


* **H10. Everything the same:**
* V1: `ثبات محكم على أرقامك الحالية—إتقان الوزن يسبق دائماً زيادته.`
* V2: `استقرار كامل في الأداء؛ تكرار الأرقام القوية هو ما يرسّخ القوة.`
* V3: `أداء مطابق للخطة؛ حافظت على ثبات جميع تمارينك اليوم.`


* **H11. Mixed session:**
* V1: `جلسة متوازنة—تقدمت في بعض التمارين وهدّأت الإيقاع في أخرى.`
* V2: `يوم طبيعي في التمرين: صعود في بعض الأرقام وتدرج هادئ في الباقي.`
* V3: `إيقاع متزن بين رفع الأداء في حركات والحفاظ على الطاقة في غيرها.`


* **H12. Lighter day / Mostly down:**
* V1: `جلسة أخف اليوم—القوة لا تسير في خط مستقيم، وحضورك يظل هو الأهم.`
* V2: `تراجعت الأرقام قليلاً اليوم وهذا طبيعي في مسار التدريب؛ خذ قسطاً من الراحة للقادم.`
* V3: `بدت الأوزان أثقل اليوم—سجّل التمرين، استرح جيداً، وعد بنشاط في الجلسة القادمة.`



#### 2. Wins Templates (AR — Handling Plurals `0, 1, 2, 3–10, 11+`)

* **W1. PR (Weight / Reps / Hold):**
* Weight PR: `رقم قياسي في {exercise}: {weight} {unit} × {reps_plural}`
*(Examples: `× تكرار واحد` [1], `× تكرارين` [2], `× 5 تكرارات` [3–10], `× 12 تكراراً` [11+])*
* Rep PR: `رقم قياسي في {exercise}: {reps_plural} بوزن {weight} {unit}` (أو لوزن الجسم: `رقم قياسي في {exercise}: {reps_plural}`)
* Hold PR: `رقم قياسي في {exercise}: ثبات لمدة {seconds_plural}`
*(Examples: `ثانية واحدة` [1], `ثانيتين` [2], `10 ثوانٍ` [3–10], `45 ثانية` [11+])*


* **W2. Biggest gain in best set:**
* Weight jump: `{exercise}: ارتفعت أفضل مجموعة من {old_weight} ← {new_weight} {unit}`
* Rep jump: `{exercise}: ارتفعت أفضل مجموعة من {old_reps} ← {new_reps_plural}`
* Hold jump: `{exercise}: زاد زمن الثبات من {old_seconds} ← {new_seconds_plural}`


* **W3. More total work at same/heavier weight:**
* `{exercise}: حجم عمل أعلى بوزن {weight} {unit} (مجموع التكرارات: {old_reps_total} ← {new_reps_total_plural})`


* **W4. Effort mastery (Same numbers, easier effort):**
* `{exercise}: نفس الأرقام السابقة ولكن بجهد أسهل وتحكم أفضل`


* **W5. All effective planned sets done:**
* Normal day:
* `1`: `أتممت مجموعة واحدة مخططة بالكامل`
* `2`: `أتممت المجموعتين المخططتين بالكامل (2 من 2)`
* `3–10`: `أتممت جميع المجموعات المخططة ({sets_done} مجموعات)`
* `11+`: `أتممت جميع المجموعات المخططة ({sets_done} مجموعة)`


* Rough day (adjusted target): `أتممت جميع المجموعات المعدّلة لليوم ({sets_done_plural}) بما يناسب استشفاءك`


* **W6. Weekly target hit:**
* `1`: `حققت هدفك الأسبوعي: تمرين واحد من أصل 1 هذا الأسبوع`
* `2`: `حققت هدفك الأسبوعي: تمرينان (2 من 2) هذا الأسبوع`
* `3–10`: `حققت هدفك الأسبوعي: {done_this_week} من {weekly_target} تمارين هذا الأسبوع`
* `11+`: `حققت هدفك الأسبوعي: {done_this_week} من {weekly_target} تمريناً هذا الأسبوع`


* **W7. Return after long gap / New baseline:**
* Gap: `{exercise}: عودة للتمرين لأول مرة منذ {weeks_plural}` *(e.g., `أسبوعين` [2], `6 أسابيع` [3–10], `12 أسبوعاً` [11+])*
* New baseline:
* `1`: `تحديد الرقم المبدئي لتمرين واحد جديد`
* `2`: `تحديد الأرقام المبدئية لتمرينين جديدين`
* `3–10`: `تحديد الأرقام المبدئية لـ {count} تمارين جديدة`
* `11+`: `تحديد الأرقام المبدئية لـ {count} تمريناً جديداً`





#### 3. Watch Templates (AR)

* **WA1a. Repeat Pain (2 sessions in a row):**
* `{exercise}: سُجّل ألم في جلستين متتاليتين—استبدله بتمرين بديل مريح في المرة القادمة`


* **WA1b. Single-session Pain tag:**
* `{exercise}: خفّضنا الحمل درجة واحدة للمرة القادمة بعد شعورك بالألم—إذا تكرر الألم، استبدل التمرين`


* **WA1c. Pain word in note (no Pain tag):**
* `{exercise}: ذكرت وجود انزعاج في ملاحظاتك—يمكنك اختيار وسم "ألم" على المجموعة ليقوم التطبيق بتخفيف حمل الجلسة القادمة تلقائياً`


* **WA2. Deload signal:**
* `بدأ الإجهاد يتراكم—يُستحسن جعل الجلستين أو الـ 3 جلسات القادمة أخف حملاً (تخفيف المجموعات إلى 70% أو تقليل الأوزان قليلاً)`


* **WA3. Plateaued lift:**
* `{exercise}: استقر الأداء منذ {sessions_plural}—جرّب زيادة الراحة 30 ثانية بين المجموعات أو التبديل إلى حركة مشابهة`
*(Examples: `4 جلسات` [3–10], `11 جلسة` [11+])*


* **WA4. Unforgiven drop:**
* `{exercise}: انخفض الأداء من {old_best} إلى {new_best} اليوم—سنُبقي هدف الجلسة القادمة ثابتاً لتستعيد إيقاعك`


* **WA5. Planned exercise skipped (no swap, normal day):**
* `لم يتسع الوقت لتمرين {exercise}؟ ابدأ به جلستك القادمة كي يتوازن تقدمك`



#### 4. Next Time Templates (AR — Handling Plurals)

* **NT1a. Pain swap (repeat pain):** `{exercise}: استبدله بتمرين بديل غير مؤلم`
* **NT1b. Pain step-back (weighted):** `{exercise}: أخف بدرجة (-{step} {unit} ← {next_weight} {unit}) بعد الألم`
* **NT1c. Pain step-back (bodyweight / hold):** `{exercise}: تخفيف الهدف إلى {next_target} بعد الألم`
* **NT2. Harder variation ready:** `{exercise}: جاهز للانتقال إلى النسخة الأصعب: {harder_exercise}`
* **NT3. Load increase:**
* Rep-range reset: `{exercise}: +{step} {unit} ({next_weight} {unit} لـ {bottom_reps_plural})`
* Single-target jump: `{exercise}: +{step} {unit} ({next_weight} {unit})`


* **NT4. Rep or hold increase:**
* Reps (`+1` is always singular, target uses plural rules): `{exercise}: +1 تكرار ({next_reps_plural} بوزن {weight} {unit})` (أو لوزن الجسم: `{exercise}: +1 تكرار ({next_reps_plural})`)
* Holds (`+5 s` is in 3–10 branch): `{exercise}: +{step_seconds} ثوانٍ ({next_seconds_plural})`


* **NT5. Holding steady after `Hard` effort at top of range:**
* `{exercise}: البقاء على {weight} {unit} × {reps_plural} حتى يصبح الأداء أكثر سلاسة`


* **NT_ELSE. Unchanged exercises summary:**
* `بقية التمارين: نفس الأرقام السابقة.`


* **NT_NOTE. Auto-regulation footnote:**
* `الأهداف تفترض استشفاءً طبيعياً—يقوم التطبيق بتخفيف الحمل تلقائياً في أيام الاستشفاء المنخفض.`



---

## 4. Test Cases

| # | Input State & Set-by-Set Log | Score | Expected Headline | Expected Wins (Max 2) | Expected Watch (Max 1–2) | Expected Next Time (Max 3 + Else) |
| --- | --- | --- | --- | --- | --- | --- |
| **1. Rep-Range Load Graduation** | **Plan:** 6 sets (2 lifts, range 8–12). Recovery: 78%.<br>

<br>• **Squat:** Last `3×12 @ 60 kg`. Today `3×8 @ 62.5 kg` (All `OK`).<br>

<br>• **Bench:** Last `3×12 @ 40 kg`. Today `3×8 @ 42.5 kg` (All `OK`). | **10/10**<br>

<br>*(Base 3 + Comp 3 + Prog 3 [both UP under revised rule] + PR 1)* | **H8:** New personal record and moving up across the board. | 1. `Squat PR: 62.5 kg × 8`<br>

<br>2. `Bench: best set climbed from 40 → 42.5 kg` *(1 win per lift enforced)* | *None* | • `Squat: +1 rep (9 reps at 62.5 kg)`<br>

<br>• `Bench: +1 rep (9 reps at 42.5 kg)` |
| **2. Rough Day + 67% Adjusted Volume + Forgiven Drop** | **Plan:** 9 sets (3 lifts). **Recovery: 41%**.<br>

<br>• **Deadlift:** Last `3×8 @ 80 kg`. Today `2×8 @ 75 kg` (`OK`).<br>

<br>• **Row:** Last `3×10 @ 45 kg`. Today `2×10 @ 45 kg` (`OK`).<br>

<br>• **Plank:** Last `3×45 s`. Today `2×45 s` (`Easy`).<br>

<br>*(6/9 raw sets = 100% of 6-set rough-day target)* | **8/10**<br>

<br>*(Base 3 + Comp 3 + Prog 2 [Deadlift forgiven as SAME])* | **H6:** Smart call dialing it back today—you protected the habit without forcing it. *(Does NOT fire <75% or Mostly Down)* | 1. `Plank: same numbers as last time, noticeably smoother effort`<br>

<br>2. `All 6 adjusted sets completed for today's recovery` | *None* *(Drops & skipped sets forgiven by rough day)* | • `Row: +1 rep (11 reps at 45 kg)`<br>

<br>• `Plank: +5 s (50 s)`<br>

<br>• `Everything else: same numbers.`<br>

<br>*(Footnote shown)* |
| **3. Early Stop Due to Pain on Set 1** | **Plan:** 6 sets (2 lifts, range 8–12). Recovery: 80%.<br>

<br>• **Overhead Press:** Last `3×10 @ 35 kg`. Today Set 1: `1×6 @ 35 kg` (**`Pain`**), Sets 2–3 skipped.<br>

<br>• **Lat Pulldown:** Last `3×10 @ 50 kg`. Today `3×11 @ 50 kg` (`OK`).<br>

<br>*(4/4 effective planned sets done)* | **8/10**<br>

<br>*(Base 3 + Comp 3 + Prog 2 [OHP forgiven as SAME, Lat UP])* | **H5:** Low-tank day, rock-solid numbers. *(Or H6 if OHP counted as forgiven drop)* | 1. `Lat Pulldown: best set climbed from 10 → 11 reps`<br>

<br>2. `All 4 adjusted sets completed for today's recovery` | **WA1b:** `Overhead Press: stepped back for next time after pain—if it hurts again, swap it out` | • `Overhead Press: -2.5 kg (32.5 kg) after pain`<br>

<br>• `Lat Pulldown: +1 rep (12 reps at 50 kg)` |
| **4. Top of Rep Range Hit, But Marked `Hard**` | **Plan:** 6 sets (range 8–10). Recovery: 75%.<br>

<br>• **RDL:** Last `3×9 @ 70 kg` (`OK`). Today `3×10 @ 70 kg` (Set 3 **`Hard`**).<br>

<br>• **Curl:** Last `3×10 @ 12.5 kg` (`Hard`). Today `3×10 @ 12.5 kg` (All `OK`). | **9/10**<br>

<br>*(Base 3 + Comp 3 + Prog 3 [RDL UP, Curl SAME])* | **H9:** Solid forward momentum—numbers climbed on most of your work. | 1. `RDL: best set climbed from 9 → 10 reps`<br>

<br>2. `Curl: same numbers as last time, noticeably smoother effort` | *None* *(No nagging Watch item for Hard)* | • `Curl: +2.5 kg (15 kg for 8 reps)`<br>

<br>• `RDL: stay at 70 kg × 10 until all sets feel solid` |
| **5. Comeback After 21-Day Break with Lighter Weights** | **Plan:** 6 sets. **Last workout: 21 days ago**.<br>

<br>• **Squat:** Last `3×10 @ 70 kg`. Today `3×8 @ 60 kg` (`OK`).<br>

<br>• **Push-up:** Last `3×12`. Today `3×10` (`OK`). | **Hidden**<br>

<br>*(Kind comeback line shown instead of score)* | **H2:** Good to have you back. Showing up after a break is the biggest win. | 1. `All 6 planned sets completed` | *None* *(Unforgiven drops & plateaus suppressed on comeback)* | • `Squat: +1 rep (9 reps at 60 kg)`<br>

<br>• `Push-up: +1 rep (11 reps)` |
| **6. All-New Exercises (Routine Switch, `exercises_compared = 0`)** | **Plan:** 6 sets. Active user (trained 3 days ago).<br>

<br>• **Goblet Squat (New):** `3×10 @ 20 kg` (`OK`).<br>

<br>• **Side Plank (New):** `3×30 s` (`Easy/OK`, ready-target 30 s). | **8/10**<br>

<br>*(Base 3 + Comp 3 + Prog 2 [baseline default])* | **H4:** Fresh exercises logged—your starting numbers are dialed in. | 1. `All 6 planned sets completed`<br>

<br>2. `Baseline set for 2 new exercises` | *None* | • `Goblet Squat: +1 rep (11 reps at 20 kg)`<br>

<br>• `Side Plank: +5 s (35 s)` |
| **7. Normal 1-Rep Fluctuation vs. True Drop** | **Plan:** 6 sets (range 8–12). Recovery: 82%.<br>

<br>• **Bench:** Last `10, 9, 9 @ 60 kg`. Today `9, 9, 9 @ 60 kg` (`OK` — 1-rep top-set dip, $-2.5\%$ e1RM).<br>

<br>• **Row:** Last `3×10 @ 50 kg`. Today `3×7 @ 50 kg` (`OK` — 3-rep drop, $-7.5\%$ e1RM). | **7/10**<br>

<br>*(Base 3 + Comp 3 + Prog 1 [Bench SAME, Row DOWN])* | **H11:** Give-and-take session—pushed ahead on some lifts, paced yourself on others. | 1. `All 6 planned sets completed` | **WA4:** `Row: dipped from 50 kg × 10 to 50 kg × 7 today—we'll keep next time's target steady so you can regroup` | • `Bench: +1 rep (10 reps at 60 kg)`<br>

<br>• `Everything else: same numbers.` |
| **8. Bodyweight Progression Ready + Unplanned Exercise Swap** | **Plan:** Push-up (3 sets), Leg Extension (3 sets).<br>

<br>• **Push-up:** `3×15` (`Easy`, 2nd session in a row at ready threshold).<br>

<br>• **Leg Extension:** Skipped (0 sets), but user logged **Lunges (Unplanned):** `3×10 @ 15 kg` (`OK`, same as last time). | **8/10**<br>

<br>*(Base 3 + Comp 3 + Prog 2)* | **H9:** Solid forward momentum—numbers climbed on most of your work. | 1. `Push-up: more total work (42 → 45 total reps)`<br>

<br>2. `All 6 planned sets completed` | *None* *(Leg Extension skip suppressed because Lunges was swapped in)* | • `Push-up: ready to level up to Diamond Push-up`<br>

<br>• `Lunges: +1 rep (11 reps at 15 kg)` |

---

## 5. Additional Improvements Worth Making

1. **Pass the Deterministic Debrief Payload Directly into the "Ask the Coach" Button Context:**
* One of the main reasons you are replacing the auto-AI debrief is that the AI previously contradicted the app's own numbers. If a user taps *"Ask the coach about this workout"*, inject the exact deterministic JSON payload (`score`, `headline`, `wins`, `watch`, `next_time_targets`, and `exercise_statuses`) into the system prompt of that chat session with the instruction: *"Treat these calculated statuses and next-workout targets as ground truth; never recalculate or contradict them."* That bridges your deterministic card and your optional AI chat seamlessly.


2. **Instant Visual Feedback When Editing a Set:**
* Since the debrief is calculated on the fly and never stored, if a user opens the debrief, realizes they typed `8 kg` instead of `80 kg` on Squat, and fixes the set, animate the Score/Win/Headline transition (a brief highlight pulse on the updated line) so they immediately see that the debrief reacted to their edit.


3. **One-Tap Action Buttons on Actionable "Watch" / "Next Time" Lines:**
* When **Next Time** says `Push-up: ready to level up to Diamond Push-up` or **Watch** says `Overhead Press: pain logged two sessions in a row—swap it for a pain-free variation next time`, add a tiny inline action chip (`[Swap in plan]` / `[Level up for next time]`). Letting the user accept a variation upgrade or pain swap right from the 10-second debrief saves them from hunting through their routine editor before their next workout.


4. **Arabic RTL Directionality (`\u2066` / `dir="ltr"` on Compound Math Strings):**
* In Arabic, strings containing arrows and multiplication signs with Western digits—such as `80 ← 85 كجم` or `62.5 كجم × 8`—can suffer from BiDi (bidirectional text) reordering if mixed with Latin unit abbreviations or arrows. Always use the leftward arrow (`←`) for progression in RTL (`من 80 إلى 85 كجم` or `80 ← 85 كجم` in RTL reading order) and localize units (`كجم`, `رطل`, `ث`) rather than mixing `kg`/`lb` into Arabic sentences.
