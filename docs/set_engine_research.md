# Set-engine research (bounded, read-only)

Produced 2026-11-24 by Gemini 4 Neon (High) through agy-read; checked by Claude against the Android code. Numbers are planning defaults, not medical advice.

VERDICT: Deterministic RIR-autoregulated double progression with an implicit ~2–3 RIR blank default, capped Epley e1RM, pattern-ratio cold starts, 5–10% intra-session failure backoff, and one-tap prefilled logging covers ~95% of evidence-based hypertrophy/strength programming with zero LLM latency.

*(Grounded in public web-search synthesis of app docs and exercise-science literature, established sports-science standards, and read-only inspection of ForgeQuest's existing domain logic.)*

---

### 1. RIR-Autoregulated Double Progression & Calisthenics Equivalents
* **Implicit-normal default:** Optional RIR tracking is standard (Alpha Progression); leaving effort blank implies a productive working set at ~2–3 RIR (Helms et al. 2016; Refalo et al. 2023).
* **Double-progression load trigger:** Climb reps within a prescribed range (e.g., 8–12) until every working set hits `repsMax` at normal effort (≥1–2 RIR), then add one equipment step and reset reps to `repsMin` (RP Hypertrophy, Alpha Progression).
* **Typical increments:** Barbell upper +2.5 kg (5 lb), lower +5 kg (10 lb); dumbbells +1–2 kg/hand; machines +1 stack step (2.5–5 kg). When the smallest physical step exceeds ~10% of current load (e.g., 8→10 kg lateral raises), extend reps instead of forcing the jump (Alpha Progression, RP Hypertrophy).
* **Effort/stall rules:** Target hit at 0 RIR → **hold** load/reps next session; reps missed at 0 RIR → **hold once**; **2 consecutive stalls/failures** → deload/step down 5–10% (or scheduled mesocycle deload every 4–6 weeks cutting sets ~50%, RP Hypertrophy).
* **Calisthenics equivalents:** Grow +1 rep/session to a hypertrophy ceiling of ~12–15 clean reps; past that, either jump one leverage tier (e.g., incline → flat → archer push-up) or add external load (+2.5 kg vest/belt) and reset to `repsMin`.
* **Use in ForgeQuest:** Extend [Progression.kt](file:///G:/AntiGravity%20projects/ForgeQuest/app/src/main/java/com/forgequest/app/domain/Progression.kt) and [SetEffort.kt](file:///G:/AntiGravity%20projects/ForgeQuest/app/src/main/java/com/forgequest/app/domain/SetEffort.kt) so blank=`OK` (~2–3 RIR) progresses normally, `EASY` (≥3 RIR) steps up immediately, `HARD`/`FAILURE` (0–1 RIR) holds, and 2 consecutive `FAILURE` or missed-`repsMin` sessions step down 1 increment (5–10%). Pick load step from equipment profile (`2.5 kg` barbell upper / `5 kg` lower / `1–2 kg` DB / machine step), falling back to `+1 rep` (up to `repsMax + 3`) if `step / weight > 0.10`. For `ExerciseKind.REPS`, climb to `min(repsMax + 2, 15)` then surface `HarderHint` (tier jump via `ExerciseTiers`) or weighted `+2.5 kg` transition.

### 2. e1RM Formulas, Rep Caps & PR Hygiene
* **Core formulas:**
  * **Epley (1985):** $\text{e1RM} = w \times (1 + r / 30)$ — smooth, monotonic, common default in Strong/Hevy.
  * **Brzycki (1993):** $\text{e1RM} = w \times \frac{36}{37 - r}$ — Boostcamp's standard (1–12 reps); accurate in 5–10 reps, diverges steeply >12 reps and blows up at 37.
  * **RIR-adjusted e1RM:** Replace $r$ with $r_{\text{eff}} = r + \text{RIR}$ (Helms et al. 2016; Zourdos et al. 2016; Alpha Progression's 10RM/RIR model).
* **Valid rep bounds:** Both formulas degrade above 10–12 reps (LeSuer et al. 1997; Reynolds et al. 2006), and lifter RIR accuracy degrades past ~3–4 RIR (Hackett et al. 2017). Boostcamp caps e1RM computation at 1–12 reps.
* **PR detection hygiene:** Award at most **1 PR per exercise per session** (the highest-scoring qualifying set), exclude warm-ups/dropsets/rest-pause, suppress PRs on an exercise's first-ever session, and remove live PR badges immediately if the set is edited or unchecked (Boostcamp, Hevy, Strong).
* **Use in ForgeQuest:** Standardize e1RM on Epley for `1..12` reps (ignore sets `>12` reps for e1RM; evaluate rep/volume PRs separately). Use raw `r` (no RIR inflation) for PRs so unverified effort taps cannot mint phantom PRs, and use `r_eff = min(r + rirOffset, 12)` (`FAILURE=0, HARD=1, OK=2, EASY=3`) only for internal load projection. Keep [PrPicker.kt](file:///G:/AntiGravity%20projects/ForgeQuest/app/src/main/java/com/forgequest/app/domain/PrPicker.kt)'s one-PR-per-exercise, non-warmup, non-first-session invariant.

### 3. Hybrid Baseline Calibration (Cold Start → Per-Exercise)
* **Pattern cold start vs. per-exercise:** New exercises without history inherit a load estimate from a stronger-evidence lift in the same movement pattern (`PUSH`, `PULL`, `SQUAT`, `HINGE`, `CORE`), then switch strictly to per-exercise history once **1–2 working sets** (or 1 session) are logged (Alpha Progression cross-lift inference).
* **Cross-exercise pattern ratios** (ExRx / StrengthLevel / Kilgore population ratios):
  * Horizontal push: Incline BB ≈ `0.80–0.85×` Flat BB Bench; DB Bench (each) ≈ `0.35–0.40×` Flat BB (total `~0.75×`).
  * Vertical push: Strict OHP ≈ `0.60–0.65×` Flat BB Bench.
  * Pull: BB Row ≈ `0.80–0.90×` Bench; Lat Pulldown ≈ `0.70–0.80×` bodyweight or `0.85×` Bench.
  * Lower: Front Squat ≈ `0.80–0.85×` Back Squat; RDL ≈ `0.70–0.80×` Conventional Deadlift; Leg Press ≈ `2.0–2.5×` Back Squat.
* **Bodyweight rep-capacity tiers:** Push-ups (`<10` beginner / `10–25` intermediate / `25+` advanced); Pull-ups (`0` assisted/negatives / `1–5` low-rep / `6–12` standard / `12+` weighted or harder leverage); Dips (`0–5` / `6–15` / `15+`).
* **Use in ForgeQuest:** When an exercise has 0 logged working sets, look up the user's most recent working e1RM in the same [CorePattern](file:///G:/AntiGravity%20projects/ForgeQuest/app/src/main/java/com/forgequest/app/domain/VariationCycle.kt#L7-L18), multiply by a deterministic `patternRatio`, discount by `0.90` (safety margin targeting ~3 RIR), and snap to equipment step; fall back to `bodyweight × sex × tier` defaults if the pattern is empty. Switch to per-exercise history immediately after 1 working set is logged. Bucket bodyweight first-set reps into `<6` (step down tier), `6–12` (hold tier), `>12` (step up tier).

### 4. Intra-Session Adjustment After Unexpected Failure
* **Load/rep backoff:** When a set hits unexpected 0 RIR (or misses `repsMin` by ≥2 reps) early in an exercise, RP Hypertrophy and Alpha Progression reduce load by **~5–10%** on remaining sets (or trim 1–2 reps) so subsequent sets remain in the productive 1–3 RIR zone instead of grinding into systemic fatigue.
* **Rest extension:** Adding **+30–60 s** after an unplanned failure set restores phosphocreatine and central drive, preserving total volume load (Schoenfeld et al. 2016; Refalo et al. 2024).
* **Use in ForgeQuest:** When a non-final working set is ticked with `SetEffort.FAILURE` (or `reps <= repsMin - 2`), deterministically adjust remaining undone sets of that exercise by `-5%` weight (`-10%` if missed by ≥3 reps), snapped down at least 1 load step (or `-1 rep` for bodyweight), and add `+45 s` (cap `240 s`) to the active [RestTimer](file:///G:/AntiGravity%20projects/ForgeQuest/app/src/main/java/com/forgequest/app/ui/workouts/LiveWorkoutViewModel.kt#L49-L54) with a dismissible inline note.

### 5. Weekly Volume Landmarks, Balance & Recovery Overlap
* **RP Volume Landmarks (hard sets/muscle/week, intermediates — Israetel/RP; Schoenfeld et al. 2017):**
  * **Chest:** MV 6 · MEV 8–10 · MAV 12–20 · MRV 22+
  * **Back:** MV 6 · MEV 8–10 · MAV 14–22 · MRV 25+
  * **Quads:** MV 6 · MEV 6–8 · MAV 12–18 · MRV 20+ | **Hams:** MV 4 · MEV 4–6 · MAV 10–16 · MRV 20+
  * **Glutes:** MV 0–2 · MEV 0–4 · MAV 6–16 · MRV 20+
  * **Side Delts:** MV 6 · MEV 8–10 · MAV 16–22 · MRV 25+ | **Rear Delts:** MV 0 · MEV 0–6 · MAV 10–16 · MRV 20+
  * **Biceps:** MV 4 · MEV 6–8 · MAV 14–20 · MRV 26+ | **Triceps:** MV 4 · MEV 6–8 · MAV 10–15 · MRV 20+
  * **Calves:** MV 6 · MEV 8–10 · MAV 12–16 · MRV 20+ | **Abs:** MV 0 · MEV 0–4 · MAV 10–20 · MRV 25+
* **Hard-set & fractional rules:** A hard set is taken to **≤3–4 RIR** (Israetel/RP; Baz-Valle et al. 2022). Count primary movers as **1.0 set** and synergists as **0.5 set** (Pelland et al. 2024; RP; Boostcamp/Hevy muscle graphs).
* **Balance, ordering & overlap:** Target **1:1 to 1:1.5 Push:Pull** (pull bias) and **~1:1 anterior:posterior lower** (Quads:Hams+Glutes). Sequence **compounds before isolations** (Simão et al. 2012; Nunes et al. 2020). Per-session productive cap is ~8–12 sets/muscle (Schoenfeld & Krieger 2018); exceeding **>10 hard sets for the same muscle within <48 h** signals recovery overlap.
* **Use in ForgeQuest:** Tally weekly sets using `countsForScore` (`1.0` primary, `0.5` secondary; `EASY` sets weighted `0.5` or excluded from hard-set count). Emit deterministic plan/recap advisories when any muscle sits `< MEV` or `> MRV`, `Push / Pull > 1.2`, an isolation precedes a same-muscle compound, or rolling 48 h hard sets for a primary muscle exceed `10`.

### 6. Rest-Time Guidance by Exercise Type & Effort
* **Literature:** Longer rests (2–3 min) outperform ≤60 s for strength and hypertrophy by preserving rep volume and mechanical tension (Schoenfeld et al. 2016; Grgic et al. 2017; Singer et al. 2024 Bayesian meta-analysis).
* **Practical targets:** Multi-joint compounds **120–180 s** (heavy strength 180–300 s); single-joint isolations/core **60–120 s** (90 s default); **+30–60 s** after 0 RIR/failure sets.
* **Use in ForgeQuest:** Set default `restSeconds` deterministically from exercise profile: `180 s` lower/barbell compounds, `120–150 s` upper compounds, `90 s` isolations/core, preserving [effectiveRestSeconds](file:///G:/AntiGravity%20projects/ForgeQuest/app/src/main/java/com/forgequest/app/ui/workouts/LiveWorkoutViewModel.kt#L74-L92)'s `0 s` warm-up, `15 s` rest-pause, and `10 s` superset mid-round rules; auto-add `+45 s` on `FAILURE` sets while keeping [RestBar](file:///G:/AntiGravity%20projects/ForgeQuest/app/src/main/java/com/forgequest/app/ui/workouts/LiveWorkoutTimers.kt#L98-L150) 60/90/120/180 s presets.

### 7. Fatigue Cost of Failure vs. Non-Failure Sets
* **Diminishing hypertrophy returns:** Refalo et al. (2023 *Sports Med*; 2024) show a small hypertrophy edge from 2 RIR → 0 RIR, paired with sharply higher acute neuromuscular fatigue, velocity loss, and perceived discomfort/soreness.
* **Recovery kinetics:** Stopping at 1–3 RIR (~20% velocity loss) restores force/CMJ/velocity within **~24 h**, whereas sets to failure (0 RIR / ≥40% velocity loss) suppress neuromuscular performance and elevate CK/lactate/ammonia for **48–72 h** (Pareja-Blanco et al. 2017, 2020; Vieira et al. 2021/2022).
* **Use in ForgeQuest:** Weight session fatigue per working set as `EASY=0.5`, `OK/HARD=1.0`, `FAILURE=1.5`; extend a primary muscle's recovery-readiness window from `48 h` to `72 h` whenever ≥2 compound sets hit `FAILURE`, and surface a soft debrief nudge if >30% of a week's compound sets are logged as `FAILURE`.

### 8. Low-Friction Mobile Set-Logging UX
* **One-tap happy path:** Prefill target/previous weight and reps so an on-plan set requires a **single tap** to log (Strong, Hevy, Boostcamp, Alpha Progression).
* **Non-blocking effort input:** Keep RPE/RIR chips optional and single-tap toggleable so blank defaults cleanly to normal effort (Alpha Progression; ForgeQuest `SetEffort.chips`).
* **Ergonomics & clarity:** Minimum **48×48 dp** touch targets (Material 3) with tabular numerals for jitter-free timers/numbers; compact inline previous/next cues (e.g., `Last 80×10 · +1 rep`) directly on the card without modal popups (RP, Boostcamp); live PR badge capped to **once per lift** (Boostcamp).
* **Use in ForgeQuest:** Keep [SetRow](file:///G:/AntiGravity%20projects/ForgeQuest/app/src/main/java/com/forgequest/app/ui/workouts/LiveWorkoutSets.kt#L133-L150) prefilled from `Progression.next` with a `≥48.dp` one-tap tick, optional `EASY / HARD / FAIL` chips (blank = neutral ~2–3 RIR), a one-line inline cue derived from `Progression.Reason` (`LOAD_UP`, `MORE_REPS`, `HARD`, `MISSED`), tabular digits, and `PrPicker`'s single live gold badge per exercise.
