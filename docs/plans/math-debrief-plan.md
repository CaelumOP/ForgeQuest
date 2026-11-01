# Plan: math debrief (no AI) + score fixes, Android + web

Approved 2026-10-27, after Gemini's review. This replaces the AI debrief with one calculated from the user's numbers. The AI stays behind one button, "Ask the coach about this workout".

## Rules everything follows
- **One source of truth.** The debrief reuses existing code:
  - `WorkoutScore` for trends, PRs, rough day, first ever and after a break
  - `Progression.next` for next time
  - `Trends.analyzeFatigueAndPlateaus` for plateau and deload
  - `ExerciseTiers` for a harder version

  The inputs to `Progression.next` are built by one shared function, used by both the prefill and the debrief.
- **Calculated on the fly, never stored.** The `sessions.debrief` column and the backup field stay (never-delete list) but are no longer written or shown.
- **Pure domain.** It returns typed lines (a sealed type plus arguments), not text. The UI turns them into EN or formal AR strings.
- **Deterministic wording variety.** Each headline has 2–3 variants, picked by `sessionId % count`.
- **Parity.** Android and web have the same rules and the same test table.

## A. Score fixes (`WorkoutScore.trend`, `workout-score.ts`)
Today's score scale and points stay as they are. Only the per-exercise trend changes.

1. **Working sets for total work.** The reference weight is the heaviest weight among sets with 3 or more reps (the heaviest overall if every set has 1–2 reps). The total counts sets at ≥ 60% of that reference.
2. **UP when any of these holds:**
   - best set improved (`Records.score`)
   - total work rose at the same or a heavier top weight
   - **new:** a WEIGHTED exercise whose top weight rose with total ≥ 60% of last time. Following the app's own reset (3×12 → 3×8, heavier) is progress.
3. **DOWN only when all three hold:**
   - **best set dropped:**
     - WEIGHTED: best < 0.95 × last, or the same top weight with the best set's reps down by 2 or more
     - REPS: best reps down by 2 or more and below 0.95 × last
     - HOLD: best < 0.85 × last
   - **total fell:** total < 0.95 × last
   - **not a heavier reset:** a heavier top weight with total ≥ 50% of last time doesn't count
4. **SAME otherwise.** A one-rep wobble at the same weight is SAME.
5. Rough-day forgiveness, the 67% planned sets, warm-ups, first ever and after a break are unchanged.

Old journal scores are recalculated with these rules. They can only stay the same or go up.

## B. Plateau and deload fixes (`Trends`, `trends.ts`)
- **Plateau:**
  - the exercise was done 4+ times over 21+ days within the last 42 days
  - the last 2 workouts beat **neither** the earlier best set **nor** the earlier best total work (the total uses rule A1)
  - the existing exclusions stay: unweighted holds and jumping jacks
- **Deload volume trigger:** compare this week against the average of the **active** weeks among the previous 4 (weeks with 6 or more sets). It needs at least 1 active week; with none, there is no volume trigger. The ≥ 135% and ≥ 18 sets rules stay.
- **Other deload triggers:** the recovery trigger is unchanged; the plateau trigger now uses the new plateau rule.

## C. The debrief (`domain/WorkoutDebriefMath.kt`, `web/src/domain/debrief.ts`)
**Per exercise** (warm-ups left out), computed from these inputs:
- this workout's sets and the previous time's sets
- the raw trend (A); "forgiven" when low recovery, or pain on that exercise
- PR flags
- pain: `SetEffort.of(effort, note)`, the same as Progression
- repeat pain: pain in this workout **and** in the previous time
- the effort counts (Easy and Hard)
- gap: days since this exercise was last done
- `newest`: no later workout contains this exercise

**Headline (first match):**
1. first workout ever
2. first after a break
3. only warm-ups
4. all exercises new (nothing to compare)
5. rough day with no drop at all, even after forgiveness: "held steady"
6. rough day where drops were forgiven and none were left unforgiven: "scaled back smartly"
7. done sets under 75% of the **effective** planned sets (the same number the score uses), and no PR
8. a PR, and UP on at least half of the compared exercises
9. UP on at least half, or any PR
10. all SAME
11. unforgiven drops on half or fewer
12. unforgiven drops on more than half

**Wins (max 2, max 1 per exercise, in order):**
1. **PR** (weight PR > rep PR > hold PR)
2. **biggest % gain** in the best set among UP exercises
3. **more total work** at the same or heavier weight
4. **effort mastery:** SAME, the same top weight, total ≥ last time, no pain, and fewer Hard sets or more Easy sets than last time
5. **all effective planned sets done**
6. **weekly target hit:** this workout makes the week's count equal the target from the days/week setting, so it shows only on the workout that hits it
7. **an exercise back after 42+ days**, or "starting numbers set for N new exercises" when headline 4 applies

**Watch (max 1; 2 only when pain and deload both apply):**
1. **Pain:**
   - repeat pain: "swap it for a pain-free option"
   - otherwise: "next time is eased; if it hurts again, swap it"
   - bodyweight or hold: "next time keeps the same numbers; if it hurts again, swap it", because Progression keeps their reps
2. **Deload signal.** Not shown on a first workout or after a break.
3. **Plateau** on an exercise in this workout. Not shown after a break.
4. **Unforgiven drop.** Not shown when headline 12 applies.
5. **Skipped exercise:** a planned exercise with no done sets. Not shown when:
   - an unplanned exercise was logged (that's a swap)
   - it's a rough day
   - headline 7 applies
   - it's a first workout or after a break
- "Felt hard" and "low recovery" are no longer watch items.

**Next time (max 3 change lines + "Everything else: same numbers" when anything is unchanged):**
- **Where it shows:** only for exercises where `newest` is true, and not on warm-ups.
- **How it's worked out:** `Progression.next` with `last` = this workout, `previous` = the time before, `lowReadiness = false`. Compare the prefill with this workout's numbers.
- **Order:**
  1. **swap**, on repeat pain
  2. **lighter after pain** (WEIGHTED, the weight went down)
  3. **ready for a harder version**: hold limit reached, or bodyweight `readyForHarder`. The name comes from `ExerciseTiers`, or "slower tempo" when there is none.
  4. **load up**: new weight and reps
  5. **+1 rep**, or **+5 s**
  6. **hold steady while it feels hard**: top of the range hit, but a Hard set kept it
- **Footnote** "The app eases off by itself on a low-recovery day." when health data is on or it was a rough day.

**Coach button:** "Ask the coach about this workout", shown only when a Gemini or Groq key is saved. It opens the coach chat with a ready message:
- the facts the old debrief prompt used
- the calculated debrief
- "Treat these numbers and next-time targets as fact; don't recalculate or contradict them."

## D. Every place it's used
| Place | Change |
|---|---|
| Finish screen | The card shows at once: score, reasons, headline, wins, watch, next time, footnote, coach button. No loading, no Try again. |
| Journal workout sheet | Same card for that workout; next time only for exercises where `newest` is true. |
| Journal rows | Unchanged. |
| Profile → Coach and AI | Remove the "Workout debrief" switch. The pref and backup field stay, ignored. |
| Removed | `CoachRepository.generateDebrief`, `DEBRIEF_TIMEOUT_MS`, the retry/waiting/failed plumbing, and the loading/failed/no-key strings. `WorkoutDebrief` decode stays for old backups. |
| Web | The same, in `WorkoutSummary`, `SessionScreen` and settings. |

## E. Wording rules
- Short and plain, at most one line on a phone where possible.
- No barbell/gym-only words ("under the bar"), and none of Gemini's cheesy lines.
- English uses no long dashes; it uses a colon or a full stop.
- Arabic is formal MSA, with Western digits and proper plurals (0, 1, 2, 3–10, 11+, via `plurals`). Use «من 80 إلى 85» rather than arrows next to numbers, and the app's existing unit words.
- Starting points: Gemini's templates in `docs/plans/math-debrief-gemini-reply.md`, tightened.

## F. Build order
1. **Opus medium:** A, B and C in the domain on both platforms, the shared Progression-input function, and the scenario tests. Coordinator reviews the diff.
2. **Sonnet:** D and E (UI, strings, AI removal, coach button) on both platforms.
3. **CI and QA build, then the emulator.** Screenshots for the user: a PR day, a rough day, first ever, pain, the reset day, and Arabic.
4. **Release** only after the user's OK.
