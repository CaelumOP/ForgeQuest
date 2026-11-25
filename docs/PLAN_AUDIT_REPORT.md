# Plan audit report (Phases 2-4)

Branch `audit/plan-quality`, base `ee42c06`. Design and owner decisions: `docs/PLAN_AUDIT_DECISIONS.md`.

## Commits
- `a9a83f0` Android: plan builder volume allocation, SHORT_PLAN note, catalog fixes, progression S1-S7, `stopHold` under the live lock.
- `b08f392` Web: the same, ported (Gemini Hydrogen); S1 lighter-set fix applied by hand.
- `7d74315` Phase 3 wording EN + formal AR, "More tools", web overlays pruned; permanent matrix and 24-week tests (Codex Astra).

## Results
| Check | Result |
|---|---|
| Android unit tests + `assembleDebug` | 616 pass, build OK |
| Web `tsc`, vitest, `vite build` | clean, 749 pass, build OK |
| Android == Web plans | byte-identical on all 32,340 matrix combos; findings identical; guarded by `tools/plan-matrix.golden` |
| Standard plans (3-6 days x 45-60 min) | 92.3 % with zero findings (was 4.2 %); the rest are BELOW_MEV only |
| 1-2 day / short plans | SHORT_PLAN note only, no BELOW_MEV |
| ABOVE_MRV / junk volume / overlap | none in the whole matrix |
| Browser, web build, EN + AR | Progress > "More tools" / «أدوات أخرى» collapsed by default, expands to "Max strength estimate" / «تقدير أقصى قوة»; no 1RM/Epley/PR text; RTL, no sideways scroll |

## Progression (D2)
S1 top weight for every set, lighter sets restart at the minimum; S2 bodyweight never below 1, missed session -> min(minimum, best), two missed -> EASIER; S3 holds by best set on the 5 s grid; S4 untagged back-off at the same top weight; S5 no cut when one load step is over 10 % (MISSED, not BACK_OFF); S6 transfer 0.9 / 0.54 / 0.72, flyes excluded; S7 harder version at min(15, top + 2). `rx_why_back_off` reworded (no longer only at failure); new `rx_why_easier`.

## Wording (D3)
Q7 "Max strength estimate" in "More tools" on both platforms. Q8 plain hint, no "Epley", "Lifts N× your body weight". Q9 "Best single (est.)", record badge "Best", PRs -> personal records / new bests. Q11 "Build muscle", "slow down each rep". Q12 unchanged. Web overlay entries identical to the base were removed; deliberate web-only wording kept: `today_rest_week`, `live_warmup_done` (EN), `live41_set_type_restpause_desc` (AR), `backup_saved`.

## Open for the owner
1. **Light-dumbbell load-up jumps.** 5 -> 7.5 kg is +50 %; a slow lifter then repeats the same MISSED prescription for about two weeks until the reps come. It follows S5 (no cut when the step is over 10 %); no locked decision covers the load-up step size. The 24-week test accepts it but requires the stall to end at a heavier weight.
2. **Remaining 7.7 % BELOW_MEV**, mostly 3-day NEW/STRENGTH plans at 45 min with limited equipment.

## Gates
Gate 1: Gemini Argon, 18 points, all applied. Gate 2: none needed. Gate 3: not yet run, both Gemini Argon and Codex hit their plan quotas during the review; rerun pending.
