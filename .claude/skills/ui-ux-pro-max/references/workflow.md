# Search Workflow, Design System Persistence and Dials

Load when running `scripts/search.py` beyond a single targeted query. Every command below is run from the repo root.

## Invocation

```bash
python ".claude/skills/ui-ux-pro-max/scripts/search.py" "<query>" --domain <domain>
```

If `python` is missing try `python3`, then `py -3`. No external dependencies.

## Query contract

1. New page or system-wide direction: `--design-system`.
2. Targeted concern or component bug: one explicit `--domain`.
3. Known stack: `--stack jetpack-compose` (ForgeQuest is Android only).

Build each query around one intent with 2-5 terms. Check the returned domain, top result and fit for a mobile Android product before using it. Retry once with a narrower query or explicit domain. If still empty, say no verified match was found and label any general advice as a fallback. Never persist unverified output, never put private project data in queries.

Accessibility: search one observable outcome at a time (`"error summary validation" --domain ux`, `"icon button accessible label" --domain icons`), then the stack. Text layout bugs: search the UX outcome first (`"badge chip label wraps" --domain ux`), then `--stack jetpack-compose`.

## Domains

| Need | Domain | Example |
|---|---|---|
| Product patterns | `product` | `"fitness coach" --domain product` |
| Styles | `style` | `"soft minimal" --domain style` |
| Palettes | `color` | `"wellness calm" --domain color` |
| Font pairings | `typography` | `"friendly modern" --domain typography` |
| Single Google Fonts | `google-fonts` | `"sans variable" --domain google-fonts` |
| Charts | `chart` | `"progress trend" --domain chart` |
| UX practice | `ux` | `"focus not obscured" --domain ux` |
| Icons | `icons` | `"decorative icon aria hidden" --domain icons` |
| Native interface rules | `web` | `"accessibilityLabel touch safe-areas" --domain web` |

Skip `landing`, `gsap` and `react`: they target web pages and are out of scope for ForgeQuest Android (see the Exclusion Table in SKILL.md). Auto-detect can misroute overlapping words ("font" hits `typography` and `google-fonts`); pass `--domain` explicitly.

## Design system

```bash
python ".claude/skills/ui-ux-pro-max/scripts/search.py" "<product> <keywords>" --design-system [-p "Name"]
```

ForgeQuest already has a design system: `DESIGN.md` (Soft Coach). Use `--design-system` only to explore alternatives, and never persist over DESIGN.md.

## Persist (Master + Overrides)

Add `--persist -p "Name" --output-dir "<project-root>"`. This writes `design-system/<slug>/MASTER.md` and a `pages/` folder. `--page "<name>"` adds an override file. An existing MASTER.md is skipped unless `--force`; read it first and never use `--force` without explicit user approval.

Retrieval for a page: read MASTER.md, then `pages/<page>.md` if present (it overrides Master).

## Dials (optional, 1-10)

`--variance`, `--motion`, `--density` tune `--design-system` output.

| Dial | Low 1-3 | Mid 4-7 | High 8-10 |
|---|---|---|---|
| `--variance` | Centered, minimal | Balanced | Bold, asymmetric |
| `--motion` | Subtle micro-interactions | Standard | Complex (attaches web GSAP snippets: ignore on Android) |
| `--density` | Spacious | Standard | Dense, dashboard-like |

## Zero results

Do not fabricate. Retry once, then fall back to the priority order in SKILL.md and say the advice came from built-in defaults, not a database match.

## Output formats

`-f ascii` (default), `-f markdown`, `--json`.

## Problem lookup

| Problem | Read |
|---|---|
| Dark mode contrast | `quick-reference-b.md` rule 6: `color-dark-mode`, `color-accessible-pairs` |
| Animations feel unnatural | `quick-reference-b.md` rule 7 |
| Form UX poor | `quick-reference-b.md` rule 8 |
| Navigation confusing | `quick-reference-b.md` rule 9 |
| Layout breaks on small screens | `quick-reference-a.md` rule 5 |
| Jank | `quick-reference-a.md` rule 3 |
