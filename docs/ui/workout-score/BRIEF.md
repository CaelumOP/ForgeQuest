# ForgeQuest: how the workout score and coach debrief look (for design review)

Real screenshots from the Android app (Pixel 7 emulator, build 2.15.0 draft, 2026-10-27). Attach the 6 PNGs in this folder.

## What the app is
A personal workout and nutrition app for a few friends, in English and Arabic (right-to-left). The users range from park beginners to gym lifters, mostly men aged 18–35. The goal is **consistency for months without burnout**: calm, encouraging, no guilt. The look is Material 3, light and dark themes, with teal as the main colour and gold for scores and records.

## What's on screen
After every workout the app calculates a **score from 4 to 10** from the person's own numbers:
- 3 points for finishing
- up to 3 for doing the planned sets
- up to 3 for beating last time
- +1 for a personal record (PR)

It shows the score with its reasons: `8/10 · All 9 sets · Up on 2 of 3 · 1 PR`. If the person has saved an AI key, a "Coach's debrief" adds one summary sentence, a highlight, sometimes a "watch" item, and a "Next time" tip. The score never depends on the AI.

| File | Screen |
|---|---|
| `1-journal-en.png` | History (journal): every past workout card, with the score line under it (gold), an old AI summary line, "+ Add a note" and "Coach debrief" buttons |
| `2-finish-en.png` | The "Workout complete" screen: sets / time / XP, level bar, new record, next workout, then the score + coach card |
| `3-debrief-sheet-en.png` | The bottom sheet opened by "Coach debrief" in the journal |
| `4-no-ai-key-en.png` | The same sheet when no AI key is saved: score card plus a hint |
| `5-journal-ar.png` | The journal in Arabic (RTL) |
| `6-debrief-sheet-ar.png` | The sheet in Arabic |

The coach text in the screenshots is canned test text, so ignore its wording. In 5 and 6, "2 من 9 مجموعة" has since been changed to "المجموعات: 2 من 9".

## What we'd like from you
1. **Does the score read at a glance?** Is "7/10" plus a dotted reasons line clear, or would another form work better: a small ring or bar, three icon chips (sets, progress, PR), a word label ("Solid", "Strong")? Keep in mind it must stay calm, not like a test grade.
2. **The journal row:** the score sits *outside* the white card, under it, and the buttons sit under that. Should the score move inside the card? Is the row too tall now?
3. **The finish screen:** what order should the cards go in? Is the score card in the right place (last, just above Done)? Should the score get more presence, like a big number near the top?
4. **Low scores:** how can a 4/10 or 5/10 look honest but not discouraging, given the goal is to keep people coming back?
5. **Arabic/RTL:** anything that reads wrong in 5 and 6 (alignment, the "7/10" position, mixed English exercise names)?
6. **Anything else** that looks off: spacing, colour contrast (the gold text on white), touch targets, hierarchy.

For each suggestion, describe it concretely (what moves where, sizes and colours as Material 3 roles), and say why it helps. Rank them by impact. Say what you'd leave alone.
