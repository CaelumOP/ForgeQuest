# ForgeQuest: workout score (for review)

Prepared 2026-10-27. This is a proposed rule, not shipped yet. It replaces a 4–10 "session score" that an AI model used to guess. The app will calculate the score on the phone from the workout's own numbers, and show it with the reasons behind it:

> **8/10** · All 12 sets · Up on 3 of 4 · 1 PR

The AI then only writes a few sentences around the score. It may not change it.

> **v2 (2026-10-27, after review). This replaces sections 2–4 below, which describe v1.**
> Code: `app/.../domain/WorkoutScore.kt` and `web/src/domain/workout-score.ts`.
>
> - **Warm-ups:** sets and plan items in the `Warm-up` block (stored value, any case) count for nothing: not in `doneSets`, `plannedSets` or the trends. A warm-up PR still gives the +1.
> - **Saved planned sets:** each workout stores `plannedSets` (the plan day's work sets) when it's finished. Scores use it; older workouts fall back to the plan as it is now.
> - **Trend:** `total` for WEIGHTED (heaviest > 0) counts only sets with `weightKg ≥ 0.6 × heaviest`, for today and last time. UP is unchanged. **DOWN** only if `best < 0.98 × lastBest` and `total ≤ lastTotal` and not (`heaviest > lastHeaviest` and `total ≥ 0.5 × lastTotal`). SAME otherwise.
> - **Rough day:** recovery < 50 → every DOWN counts as SAME (0.5). A PAIN set → only that exercise's DOWN counts as SAME. Either one → effective planned = `ceil(0.67 × planned)`. SAME stays 0.5.
> - **Progress:** `min(3, floor(4 × sum / compared))`; `compared = 0` → 2. **Completion gate:** below 0.75 of the effective planned sets, progress = 0 (the PR +1 stays).
> - **Display flags:** `rough`, `firstEver` (first finished workout with done sets), `afterBreak` (the Welcome back rule), `showNumber = !(firstEver || afterBreak)`.
> - **Anchors:** all SAME = 8; 1 UP + 3 SAME = 8; 2 UP + 2 SAME = 9 (10 with a PR); 1 DOWN + 3 SAME = 7; rough day all SAME or all DOWN = 8; first workout = 8; 2 of 9 sets + 1 UP + PR = 4.
> - Not adopted: median of the last 3 workouts, the all-EASY deload rule.

---

## Brief for the reviewer

**What ForgeQuest is.** A personal workout and nutrition app for one person and a small group of friends, in English and Arabic. The users range from beginners doing calisthenics in a park, to home workouts with dumbbells or kettlebells, to full-gym lifters. Most are men aged about 18–35. Some wear a Fitbit, which gives a daily recovery score of 1–99.

**The product goal.** Keep people training consistently for months **without burning out or dropping out**. When there's a trade-off, we prefer slow, safe and sustainable over fast and aggressive. The score should feel fair, encourage showing up and doing the planned work, reward real progress, and **never punish honesty** (reporting pain, or easing off on a bad-recovery day).

**Constraints.**
- Everything runs on the phone: no server, no account.
- It must be simple enough to unit-test and explain in one sentence per part.
- It may only use the inputs listed below.
- It's shown after every workout and on every past workout in the journal. It's recalculated from the stored data each time, so editing a set changes it.

**What we want from you.**
1. Is this rule **sensible and evidence-based** for motivating long-term adherence? Cite sources where they exist: progressive overload rates by training age, autoregulation/RPE, readiness-based training, gamification and self-determination research on scores and feedback.
2. List problems, ranked by how much they would mislead or demotivate a user. Give a concrete case for each ("intermediate lifter on a 3×5 plan who adds 2.5 kg every other week scores X most sessions, which feels like Y").
3. For each fix, give the **exact replacement rule and constants**, plus 3–5 input → output test cases we can paste into unit tests.
4. Say what you'd **leave alone**. Don't add complexity for little gain.
5. Answer the open questions at the end.

---

## 1. Inputs

For one finished workout:

| Input | What it is |
|---|---|
| `doneSets` | Every set ticked done in this workout. Each set has `exerciseId`, `reps?`, `seconds?`, `weightKg?` (added load; 0 or null for bodyweight), `effort` (`EASY`, `OK`, `HARD`, `PAIN`, or empty, which means OK), and `isPr`. Unticked sets are deleted when the workout is finished. |
| `kind` per exercise | `REPS` (bodyweight reps, e.g. push-ups), `HOLD` (seconds, e.g. plank) or `WEIGHTED` (reps × load, e.g. dumbbell bench). |
| `lastTime` per exercise | The done sets of the most recent earlier workout that had this exercise. "Earlier" means an earlier date, or the same date and an earlier start time. Missing if the exercise was never done before. |
| `plannedSets` | The sum of `sets` over the plan day's items this workout was started from. Null for a free workout (no plan day). It reads the plan as it is now, so a plan edited later changes old scores slightly. |
| `recovery` | That day's recovery score, 1–99, from the watch. Missing for users without a watch. |
| `isPr` per set | Already set by the app when the workout is finished: the workout's best set for an exercise is a PR when its set score (below) beats every earlier set of that exercise. The first time an exercise is done never counts as a PR. |

### Set score (already in the app, used for PRs too)

`Records.score(kind, reps, seconds, weightKg, exerciseId)`, with `extra = max(weightKg, 0)`:

- **REPS:** `0` if reps = 0, otherwise `reps + 0.35 × extra`
- **HOLD:** `0` if seconds = 0, otherwise `seconds + 1.0 × extra`
- **WEIGHTED:** `0` if reps = 0. Then:
  - `body = 75 kg × share`, where share is 1.0 for weighted pull-ups, chin-ups and dips, 0.65 for weighted push-ups, and 0 otherwise
  - if `extra = 0` and `body = 0`: `reps / 100`
  - otherwise `effReps = reps` when reps ≤ 12, else `12 + 3 × ln(1 + reps − 12)`, and the score is `(body + extra) × (1 + effReps / 30) − body` (Epley, with diminishing returns above 12 reps)

---

## 2. The rule

### 2.1 Rough day

`roughDay` is true when **either**:
- recovery that day is below 50 (the app's "Ease off" band is 34–49 and its "Recover" band is below 34), **or**
- any done set in the workout is marked `PAIN`.

### 2.2 Each exercise compared with last time

Only exercises that have a `lastTime` are compared. For both this workout and last time:

- `best` = the highest set score over that exercise's sets
- `total` = the sum of reps (the sum of seconds for HOLD)
- `heaviest` = the highest `weightKg` (null counts as 0)

Then:
- **UP** if `best > lastBest`, **or** (`total > lastTotal` **and** `heaviest ≥ lastHeaviest`)
- **DOWN** if `best < lastBest` **and** `total ≤ lastTotal`
- **SAME** otherwise

Value: UP = 1, SAME = 0.5, DOWN = 0. **On a rough day, SAME and DOWN both count 1** (holding steady is full marks, and drops aren't penalised).

### 2.3 Points

| Part | Points |
|---|---|
| Finishing | **3** (always) |
| Completion: `doneSets.count / plannedSets` | 1.0 or more = **3**, ≥ 0.75 = **2**, ≥ 0.5 = **1**, below = **0**. A free workout (no plan) = **3** |
| Progress: `floor(3 × sum(values) / compared)` | **0–3**. When nothing can be compared yet (`compared = 0`): **1** |
| PR: any done set has `isPr` | **+1** |

`score = clamp(finishing + completion + progress + pr, 4, 10)`

What the numbers are meant to say:
- **7:** you did all the planned work and matched last time
- **8–10:** you progressed
- **4–6:** sets were skipped, or the numbers dropped on a normal day

### 2.4 Output

```
score: Int          // 4..10
doneSets: Int
plannedSets: Int?   // null = free workout
up: Int             // exercises that went UP
compared: Int       // exercises that had a last time
prs: Int            // sets with isPr
```

The reasons line is built from the output:
- sets: "All 12 sets" (or "12 sets" for a free workout), or "10 of 12 sets"
- "Up on 3 of 4", only when `compared > 0`
- "1 PR" / "2 PRs", only when `prs > 0`

---

## 3. Worked examples

**Set comparisons (WEIGHTED, last time 3 × 8 @ 60 kg: best = 60 × (1 + 8/30) = 76.0, total 24, heaviest 60):**

| Today | best | total | heaviest | Result |
|---|---|---|---|---|
| 3 × 8 @ 62.5 kg | 79.17 | 24 | 62.5 | UP (best is higher) |
| 8, 8, 9 @ 60 kg | 78.0 | 25 | 60 | UP |
| 3 × 8 @ 60 kg | 76.0 | 24 | 60 | SAME |
| 3 × 8 @ 57.5 kg | 72.83 | 24 | 57.5 | DOWN |
| 3 × 10 @ 55 kg | 73.33 | 30 | 55 | SAME (best lower, but more total reps at a lighter load) |

**REPS (push-ups), last time 12, 10, 8 (best 12, total 30):** today 12, 11, 9 gives best 12 (not higher), total 32 > 30, heaviest 0 ≥ 0 → **UP**.

**Whole workouts:**

| Case | Completion | Progress | PR | Score |
|---|---|---|---|---|
| All 12 planned sets, all 4 exercises SAME, normal day | 3 | floor(1.5) = 1 | 0 | **7** |
| All sets, 3 UP + 1 SAME, one PR | 3 | floor(2.625) = 2 | 1 | **9** |
| First workout ever, all sets (nothing to compare) | 3 | 1 | 0 | **7** |
| Rough day (recovery 40), all sets, all SAME | 3 | 3 | 0 | **9** |
| 6 of 12 sets, everything DOWN, normal day | 1 | 0 | 0 | **4** |
| 2 of 12 sets, DOWN | 0 | 0 | 0 | 3 → clamped to **4** |
| Free workout, 2 exercises, both UP | 3 | 3 | 0 | **9** |
| All sets, 2 UP + 2 DOWN, normal day | 3 | floor(1.5) = 1 | 0 | **7** |

---

## 4. Open questions

1. **Is "matching last time = 7" the right anchor?** Intermediate lifters can't add weight or reps every session. Many sessions on a sensible plan will be SAME, so this person sees 7 most of the time. Is that motivating ("solid, on plan") or discouraging? Should SAME count more than 0.5?
2. **The rough-day rule** gives full progress marks for any result. A single PAIN tap on one set turns the whole workout into a rough day. Should it apply only to the exercise with the pain? Can it be gamed, and does that matter for a self-tracking app?
3. **A rough day scores higher than a normal day with the same numbers** (9 vs 7). That's on purpose (it rewards easing off), but is it odd?
4. **Warm-up and circuit blocks** count like any other sets, both in completion and in progress. Should warm-ups be left out?
5. **Comparing with only the single last session** is noisy: one bad day makes the next ordinary session look like an UP. Should it compare with the best of the last 2–3 sessions, or a rolling average?
6. **Completion above the plan:** extra sets are capped at full marks. Should doing more than planned earn anything, or would that encourage overreaching?
7. **The PR bonus** is +1 however many PRs. Is a flat bonus right?
8. **Planned deload weeks** (lighter on purpose) would score DOWN on everything. The app has no deload flag yet. Is that a real problem, and what's the simplest fix?
9. **EASY on every set** (the person could have done more) doesn't change the score. Should it, or should that stay a coaching tip only?
10. **Scale:** is a 4–10 number with reasons the right display, or would something else serve adherence better (e.g. only the three facts, a 3-level label, or a streak-style "on plan" check)?
