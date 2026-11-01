---
# Format: google-labs-code/design.md (tokens in the YAML groups, prose explains why)
name: ForgeQuest
description: Soft Coach. A calm training partner, roomy soft cards, one teal accent, gentle motion with no bounce.
colors:
  # Dark / light. Same values as Android Theme.kt (FitColors) and web styles.css. Each light accent is >= 4.5:1 on cards.
  background: { dark: "#131314", light: "#F6F8FC" }
  card: { dark: "#1E1F20", light: "#FFFFFF" }
  card-high: { dark: "#282A2C", light: "#EDF1F7" }
  divider: { dark: "#333537", light: "#E1E5EA" }
  text: { dark: "#E3E3E3", light: "#1F1F1F" }
  text-muted: { dark: "#9AA0A6", light: "#5F6368" }
  accent: { dark: "#5CD6C0", light: "#00705F" }        # teal: the ONLY interactive colour
  accent-track: { dark: "#1C2E2B", light: "#D5F0EB" }
  on-accent: { dark: "#00382E", light: "#FFFFFF" }
  data-blue: { dark: "#8AB4F8", light: "#0B57D0" }     # data only: rings, charts, stats
  data-gold: { dark: "#FDD663", light: "#8A6100" }
  data-purple: { dark: "#C58AF9", light: "#7B2CBF" }
  danger: { dark: "#F28B82", light: "#B8372F" }        # coral: delete, errors, over-limit only
typography:
  display: { fontFamily: Manrope, fontWeight: 600, fontSize: 34px, lineHeight: 40px, letterSpacing: -0.5px }
  headline: { fontFamily: Manrope, fontWeight: 600, fontSize: 24px, lineHeight: 32px, letterSpacing: -0.3px }
  title: { fontFamily: Manrope, fontWeight: 600, fontSize: 20px, lineHeight: 26px }
  section: { fontFamily: system, fontWeight: 500, fontSize: 16px, lineHeight: 22px }
  body: { fontFamily: system, fontSize: 16px, lineHeight: 24px }
  body-small: { fontFamily: system, fontSize: 14px, lineHeight: 22px }
  label: { fontFamily: system, fontWeight: 500, fontSize: 14px, lineHeight: 20px }
  caption: { fontFamily: system, fontSize: 12px, lineHeight: 16px }
  number: { fontFamily: Manrope, fontFeature: tnum }
  arabic: { fontFamily: IBM Plex Sans Arabic, letterSpacing: 0 }   # every style, same sizes
rounded:
  sm: 12px     # inputs, thumbnails, small tiles inside a card
  md: 20px     # tiles and blocks nested in a card, snackbars
  lg: 28px     # cards, dialogs, bottom sheets
  full: 9999px # buttons, chips, segmented tabs, badges
spacing:
  xxs: 4px
  xs: 8px
  sm: 12px
  md: 16px
  lg: 24px
  xl: 32px
  xxl: 48px
  gutter: 20px        # screen edge to card
  card-padding: 24px
  card-gap: 16px
motion:
  # No overshoot anywhere. Springs are critically damped or close to it.
  spring-fast: { dampingRatio: 0.9, stiffness: 700 }
  spring-default: { dampingRatio: 0.9, stiffness: 380 }
  spring-slow: { dampingRatio: 1.0, stiffness: 200 }
  ease-standard: cubic-bezier(0.2, 0, 0, 1)
  ease-enter: cubic-bezier(0.05, 0.7, 0.1, 1)
  ease-exit: cubic-bezier(0.3, 0, 0.8, 0.15)
  duration-short: 200ms
  duration-medium: 300ms
  duration-long: 500ms
  duration-ring: 900ms
  reduced-motion: 120ms fade, nothing moves
components:
  card: { backgroundColor: "{colors.card}", rounded: "{rounded.lg}", padding: "{spacing.card-padding}" }
  tile: { backgroundColor: "{colors.card-high}", rounded: "{rounded.md}", padding: "{spacing.md}" }
  button-primary: { backgroundColor: "{colors.accent}", textColor: "{colors.on-accent}", rounded: "{rounded.full}", height: 52px }
  button-secondary: { backgroundColor: "{colors.card-high}", textColor: "{colors.text}", rounded: "{rounded.full}", height: 52px }
  button-text: { textColor: "{colors.accent}", minHeight: 48px }
  chip: { backgroundColor: "{colors.card-high}", selectedBackground: "{colors.accent}", rounded: "{rounded.full}" }
  segmented-tabs: { backgroundColor: "{colors.card-high}", selectedBackground: "{colors.card}", rounded: "{rounded.full}" }
  list-row: { minHeight: 64px, iconBadge: 40px, iconBadgeColor: "{colors.accent-track}", iconColor: "{colors.accent}" }
  switch: { checkedTrack: "{colors.accent}", uncheckedTrack: "{colors.card-high}" }
  input: { backgroundColor: "{colors.card-high}", rounded: "{rounded.sm}", fontSize: 16px }
  progress-line: { height: 8px, rounded: "{rounded.full}" }
---

# ForgeQuest design system: Soft Coach

## Overview
ForgeQuest helps people stay consistent without burning out. The interface should feel like a calm coach: it
shows today's next step, praises progress quietly, and never shouts. Soft Coach (chosen 2026-11-01) keeps the
Material 3 base and today's palette, and changes how they are used: more room, softer corners, one accent colour,
slower motion with no bounce.

**Dials** (idea from Taste Skill): variety 2/10 (every screen looks like the same app), motion 3/10, density 4/10.

**Fixed:** Home keeps its order and the big nutrition rings: header, rings, stats row, today's workout card,
readiness. Soft Coach changes spacing, corners and colour use there, never the layout.

## Colors
- **Teal is the only interactive colour.** Primary buttons, selected chips and tabs, switches, links, focus rings,
  list-row icon badges, generic progress. If it can be tapped or is "on", it's teal or neutral.
- **Blue, gold and purple are data colours.** They appear only where they name a measurement: the nutrition rings,
  macro stats, charts, the muscle map. Never on buttons, chips or decoration.
- **Coral means danger.** Delete, errors, over a limit. Not for "you missed a day": missed days are neutral grey.
- Tracks (pale tints) are backgrounds for their own colour only.
- Surfaces step up in tone (background → card → card-high). No shadows, no gradients, no glows, no blur.

## Typography
Manrope for display, titles and numbers; the system sans for body and labels; IBM Plex Sans Arabic for every
style in Arabic, untracked, with direction taken from the text. Numbers that change in place use tabular figures.
Body small gets a taller line (22) for an airier read. Everything must still work at 200% font scale: text
wraps to two lines instead of being cut, rows grow taller, nothing overlaps.
Fonts ship with the app (Android `res/font`, web `/fonts`, precached by the service worker), never from a font
CDN, and only in the weights the styles use: Manrope 500–600, Plex Arabic 400 / 500 / 600.

## Layout
- Screen gutter 20, card padding 24, gap between cards 16. Inside a card: 16 between groups, 8 within a group.
- Settings and option cards: icon + title + subtitle on top, a full-width segmented control below. No chips
  squeezed beside a title, no horizontally scrolling chip rows for 5 options or fewer.
- Logical directions only (start/end), so Arabic mirrors for free. Directional icons flip in RTL.
- Tap targets at least 48 dp on Android, 44 px on web.

## Elevation & Depth
Flat. Depth comes from tone steps only. Dialogs and sheets sit on a scrim.

## Shapes
Three corner sizes plus full: sm 12, md 20, lg 28. Cards, dialogs and sheets are lg. Things nested in a card are
md or sm. Buttons, chips, tabs and badges are fully round. No other radii.

## Components
Card, Tile, Primary / Secondary / Text button, Chip, SegmentedTabs, Switch, Stepper, ListRow, SectionTitle,
StatColumn, ProgressLine, Ring, EmptyState, Undo snackbar, Dialog, Input. Android: `ui/components/Common.kt` and
friends. Web: `web/src/ui/kit`. Screens use these and don't restyle them. The live catalogue is the web page
`#/design` (not linked in the app).

## Do's and Don'ts
- Do show one clear next step per card.
- Do use kind, short copy: "Nice work", not "Goal failed".
- Do keep secondary actions as text buttons or in a menu.
- Don't add colour for decoration, celebration confetti, badges that pulse, or streak-loss alarms.
- Don't use left/right, hard-coded radii or hard-coded spacing in screens: use the tokens.
- Don't animate layout on every state change. Motion explains a change; it isn't decoration.

## Motion
- **Approach:** minimal-functional. Things fade and slide a short way, settle without overshoot, and stop.
- **Springs:** fast for presses and toggles, default for content moving, slow for large surfaces. All damped 0.9+.
- **Easing:** enter = ease-enter, exit = ease-exit, move = ease-standard. Web uses the same curves as CSS variables.
- **Durations:** short 200 (presses, toggles), medium 300 (content swaps, sheets), long 500 (screens).
- **The one authored moment:** the nutrition rings fill over 900 ms with ease-enter when Home opens and after
  logging food or finishing a workout. When a ring reaches its goal, one soft haptic tick. Nothing else celebrates.
- **Reduced motion** (system setting): every animation becomes a 120 ms fade or an instant change.

## Loading & Waiting
- **Local first.** Logging food, ticking a set or saving a plan changes the screen at once. Mistakes are fixed
  with the Undo snackbar, not a confirm dialog beforehand.
- **One tap, one request.** A button that starts network work (AI, Drive backup, sync) shows its busy state and
  ignores more taps until the work ends. Timeouts end in a kind message that says what to do next.
- **Nothing jumps.** A card waiting for content keeps its final size and shows either still placeholder blocks
  in card-high (sm / md corners) or one small neutral indicator. No shimmer sweeps: they are a gradient and
  decorative motion.

## Decisions Log
- 2026-11-01: Soft Coach picked over Quiet Material and Clear Instrument.
- 2026-11-01: Inputs reviewed: gstack (ideas only: this file's format, the authored moment, audit → fix → verify),
  Instagram "ten taste skills" (keep design.md format and UI UX Pro Max; adapt Taste dials, Better Typography,
  Impeccable checklists, Hue; skip shadcn/ui, Hallmark themes, Interface Design).
- 2026-11-01: Palette hex values kept (contrast already verified); only their use changes.
- 2026-11-01: Corners 8 sizes → 3 (12 / 20 / 28) + full. Card corners 24 → 28, card padding 20 → 24.
- 2026-11-01: Springs lose their bounce (damping 0.6–0.8 → 0.9–1.0).
- 2026-11-01: Input reviewed: Instagram "app-performance toolkit" (31 speed tips). Kept the client-side ones as
  Loading & Waiting rules, the font rule and a slow-device check; skipped the server-side ones (no backend).
