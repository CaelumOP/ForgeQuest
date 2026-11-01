# Plan: fix everything left over (2026-10-31, decisions made)

This comes from reading all 17 sessions plus the open list. Google sign-in for everyone: done by the user.

## Release 2.17.0, in 5 parts (one builder at a time, Android + web for each)

### A. Small fixes (Sonnet)
1. **Every bottom sheet opens fully,** also with system animations off (the journal sheet fix, applied to the other ~14 sheets).
2. **The Android journal week always starts on Monday,** the same as Home and the web. Today it follows the phone language.
3. **Cleanup:**
   - fix the deprecation warnings left by the library upgrade (`LocalClipboardManager`, `rememberModalBottomSheetState`, auto-mirrored icons)
   - remove the unused old Arabic font package from the web app

### B. Math design leftovers from the Oct 24 review (Opus medium, domain math)
1. **XP cap:** at most 25 working sets per workout earn set-XP, so junk volume earns nothing extra. **Decided: only new workouts.** Workouts on or after a fixed cut-off date (the release day, as a constant) are capped; older workouts keep their XP.
2. **Badge aliases:**
   - Pull-up badges also count chin-ups, and weighted pull-up badges also count weighted chin-ups.
   - Dip badges count weighted dips too.
   - Today a pull-up done with +20 kg never unlocks the weighted badge.
3. **New "25 workouts" badge,** between 10 and 50 (EN + AR, with an icon).
4. **Recovery time per muscle group** (body map + muscle sheet), instead of 48 h for all:

   | Group | Hours |
   |---|---|
   | Large: quads, hamstrings, glutes, lower back | 60 |
   | Standard: chest, upper back, shoulders, traps, biceps, triceps | 48 |
   | Small/postural: abs, obliques, calves, forearms, tibialis | 36 |

   **Decided: use them.** These are the review's numbers; it said there's no exact source for them.

### C. New exercises for weak muscle groups (Sonnet, Opus reviews the catalog data)
- **Adductors** (none today), e.g. Copenhagen plank (short and long lever), side-lying adduction, Cossack squat, band adduction, and a sumo/wide squat with adductors listed.
- **Tibialis** (only the tib raise today), e.g. heel walks, band dorsiflexion, wall tibialis raise.
- **Each exercise gets everything the others have:**
  - EN + formal AR names and cues
  - muscles, equipment, tier and movement pattern, so swaps and "harder version" work
  - web catalog parity
- **Optional:** fill obliques/lower back/forearms if a tier gap shows up.

### D. "Just log" mode (Opus medium)
For someone who only wants to count and save their training, without the soft consistency side.

**Decided scope:**
- **How to turn it on:** a switch in Profile, plus one onboarding choice ("Build a habit" / "Just log my training").
- **Hides:**
  - the streak and weekly target card, and the target nudge
  - XP, levels and rank (the header badge), and badges
  - the readiness card and recovery nudges
  - the debrief's motivational headline (the facts and next-time numbers stay)
  - consistency reminders
- **Keeps:** logging, journal, PRs, progress charts, food, coach and backup.
- **Nothing is deleted:** turning it off brings everything back.

### E. Check on the emulator (Sonnet QA)
- A QA build with the fake Gemini server. Test:
  - the "Ask the coach about this workout" button
  - the coach "thinking" shape
  - the photo-scan loading shape
- Swipe and Undo in Food and the plan editor.
- Everything from A–D, in English, Arabic and at 200% text.
- Screenshots to the user before release.

## Housekeeping (no release needed)
- **Old worktrees:** remove the ones whose branches are merged (about 10).
- **Docs:** commit `docs/math/` and the plans worth keeping, and delete the one-off briefs.
- **The persona-testing skill:** a task card is already up.

## Waiting on something else
- **material3 stable 1.5:** not out yet (newest is 1.5.0-beta02). Switch in a small release when it ships.
- **Real iPhone test of the web app:** needs your iPhone (checklist in `docs/plans/web-phase3-plan.md`).
- **Gemini bridge phone setup:** needs a walk-through with you on your phone.
- **2- and 4-day starter plans:** the built-in plans have Hemo video timestamps, so new ones need the videos you want. Later, when you pick them.
- **Deferred Material polish:** the card-to-screen transition, the floating rest/Finish toolbar, and a warm-up chip redesign.
- **Code tidy-ups:** plan-editor drag code, a proper warm-up flag instead of the block name, and the "check first" duplicates in the cleanup audit.
