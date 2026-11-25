# Plan audit report (Phases 2-4)

Branch `audit/plan-quality`, base `ee42c06`. Design and owner decisions: `docs/PLAN_AUDIT_DECISIONS.md`.

## Commits
- `a9a83f0` Android: plan builder volume allocation, SHORT_PLAN note, catalog fixes, progression S1-S7, `stopHold` under the live lock.
- `b08f392` Web: the same, ported (Gemini Hydrogen); S1 lighter-set fix applied by hand.
- `7d74315` Phase 3 wording EN + formal AR, "More tools", web overlays pruned; permanent matrix and 24-week tests (Codex Astra).
- `8570f50` Option B high-rep bridge (owner decision): when the next load step is over 20 % heavier, reps climb to the top of the range + 3 first.
- Gate 3 fixes commit: five defects found by Codex Astra (see Gates).

## Results
| Check | Result |
|---|---|
| Android unit tests + `assembleDebug` | 622 pass, build OK |
| Web `tsc`, vitest, `vite build` | clean, 755 pass, build OK |
| Android == Web plans | byte-identical on all 32,340 matrix combos; findings identical; guarded by `tools/plan-matrix.golden` |
| Standard plans (3-6 days x 45-60 min) | 92.18 % with zero findings (was 4.2 %); the rest are BELOW_MEV only |
| 1-2 day / short plans | SHORT_PLAN note only, no BELOW_MEV |
| ABOVE_MRV / junk volume / overlap | none in the whole matrix |
| Browser, web build, EN + AR | Progress > "More tools" / «أدوات أخرى» collapsed by default, expands to "Max strength estimate" / «تقدير أقصى قوة»; no 1RM/Epley/PR text; RTL, no sideways scroll |

## Progression (D2)
S1 top weight for every set, lighter sets restart at the minimum; S2 bodyweight never below 1, missed session -> min(minimum, best), two missed -> EASIER; S3 holds by best set on the 5 s grid; S4 untagged back-off at the same top weight; S5 no cut when one load step is over 10 % (MISSED, not BACK_OFF); S6 transfer 0.9 / 0.54 / 0.72, flyes excluded; S7 harder version at min(15, top + 2). `rx_why_back_off` reworded (no longer only at failure); new `rx_why_easier`.

## Wording (D3)
Q7 "Max strength estimate" in "More tools" on both platforms. Q8 plain hint, no "Epley", "Lifts N× your body weight". Q9 "Best single (est.)", record badge "Best", PRs -> personal records / new bests. Q11 "Build muscle", "slow down each rep". Q12 unchanged. Web overlay entries identical to the base were removed; deliberate web-only wording kept: `today_rest_week`, `live_warmup_done` (EN), `live41_set_type_restpause_desc` (AR), `backup_saved`.

## Owner decisions after the first report
1. **Light-dumbbell load-up jumps: Option B.** A step over 20 % (5 -> 7.5 kg) waits for the top of the range + 3 reps, also on all-Easy sessions. The 24-week simulator still shows one shorter stall at 7.5 kg (week 10) for its slow-gaining lifter; it ends at a heavier weight.
2. **Remaining BELOW_MEV** (mostly 3-day x 45 min straight-set plans): left as is; time-crunched plans will be handled by supersets in the next branch.

## Web Plan check, in the browser (390 x 844)
"General fitness, 6 days, Gym, Some experience": no Plan check card. After raising Back squat to 10 sets: three plain warnings (glutes 23 a week over 16; glutes 16 and quads 12 on Wednesday over 10), same in formal Arabic, RTL, no sideways scroll.

## Gates
Gate 1: Gemini Argon, 18 points, all applied. Gate 2: Codex read-only sandbox could not read files three times on Windows; switched to its auto-approved mode with a no-edit brief. Gate 3: Gemini Argon APPROVED; Codex Astra CHANGES with five valid defects (S7 fixed-count items used 15 reps; live cut could write 0 reps; zero-gain pull sets allowed when other rules also blocked the push set; fill tie-break by fewer sets instead of day then plan order; a core FOCUS pick could be removed before main lifts). All five fixed on both platforms; Codex re-review APPROVED.
