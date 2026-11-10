---
name: forgequest-design
description: Use before changing any ForgeQuest screen or component, on Android (Compose) or web (Preact). Applies the Soft Coach design system in DESIGN.md - tokens, accent rule, corners, spacing, motion, RTL and 200% text.
---

# ForgeQuest design (Soft Coach)

Read `DESIGN.md` at the repo root first. It is the source of truth; this skill is the checklist.

## Where the tokens live
- Android: `ui/theme/Tokens.kt` (`ForgeShape`, `ForgeSpace`), `ui/theme/ForgeMotion.kt`, colours in `ui/theme/Theme.kt` (`Fit.colors`), components in `ui/components/`.
- Web: CSS variables in `web/src/styles.css` (`--r-*`, `--sp-*`, `--ease-*`, `--dur-*`, colours), components in `web/src/ui/kit`. Catalogue: `#/design`.
- A value used on one platform has the same value on the other. Change both, and DESIGN.md, together.

## Checklist for every UI change
1. Corners: only 12 / 20 / 28 / full. Spacing: only the scale (4 8 12 16 24 32 48, gutter 20, card padding 24).
2. Teal is the only interactive colour. Blue, gold, purple only for data. Coral only for danger.
3. Use the shared components; don't restyle them in a screen.
4. Motion: no overshoot, ForgeMotion / CSS motion vars only, reduced motion respected. Nothing celebrates except the ring fill.
5. Home is one screen, no scroll at normal text: header, big rings, stats row, streak with this month's line, today's workout last with the readiness verdict as its first row.
6. Arabic: logical start/end only, directional icons flip, Plex Arabic, no tracking. Strings stay in sync EN + formal AR.
7. 200% text: wraps, never clips or overlaps. Tap targets 48 dp / 44 px.
8. Loading: local actions show at once (Undo, not confirm); network buttons go busy and ignore repeat taps; waiting cards keep their size, no shimmer.
9. Verify with real screenshots (emulator / browser), not mock-ups. For anything that loads or calls the network, also try it slow: browser DevTools "Slow 4G" with 4x CPU throttling, or a low-end emulator. Busy states show, nothing sends twice, nothing jumps when content arrives.
