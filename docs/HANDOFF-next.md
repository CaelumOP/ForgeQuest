# Next coding session (Opus): leftovers, plan import, copy-prompt button, providers, carbs

Branch `claude/hopeful-meitner-lkx0er`. Build and screenshot per item, tests before each commit, English and Arabic
strings for every new label. Don't open a PR; the owner merges.

## Where things stand (from the last Opus session, stopped at the 5-hour limit)

Fixed and measured on the emulator: the "No internet" coach bug was Gemini's free-tier queue (10–65 s before the first
byte) running past the app's 45 s timeout. Chat now runs on gemini-3.5-flash-lite with Flash as fallback: first words
in 0.7–0.9 s, short reply ~1.3 s, full plan ~4 s, meal scan ~2.7 s. Timeouts say "The coach didn't answer in time",
every error bubble has "Copy error details", a failed stream falls back to a plain request, and the system prompt went
from ~1,360 to ~800 tokens with no data dropped. gemini-3.8-flash is capped at 20 requests a day on the free tier.
Arabic UI is complete and checked (dark/light, 1.3× text, RTL fixes for steppers, muscle map, fractions, titles).
UX from the two-week simulated use: steppers repeat while held, each set shows last time's numbers, rest timer has
−15 s, "Tick all & finish", the summary shows what's next, Quick log offers recents, ring numbers are labelled, backups
can be saved straight to a file. Muscle map back view is clean; light-mode text passes contrast.

## 0. Finish what that session left (do these first, about 1.5 h)

- Verify the Progress screen per `docs/HANDOFF-progress.md` (written without a build; expect small compile fixes).
- Re-test Profile → Your data → Import file through the system picker (the earlier tap hit the preview, not the file).
- Reminder and App lock: set a screen lock on the emulator and check both end to end.
- Coach ⋮ model picker: the model names under each option stay blank until the first message after a reset. Load the
  model list when the menu opens (or from the cached discovery result), not only after a send.
- Update SPEC.md's status section with everything above and the measured coach timings.

## 1. Second free AI provider: Groq (no paid providers, ever) — now lower priority

Speed is solved (first words under a second on Flash-Lite), so Groq's value is a second free quota pool for days when
Gemini's daily cap bites, plus resilience when Google queues. Do items 2 and 3 before this; skip it entirely if the
owner is happy with Flash-Lite's limits after a week of use. Gemini stays for meal scans and plans; Groq takes chat
when the person has a Groq key, with automatic fallback to Gemini.

- Make the client OpenAI-compatible: `GeminiClient` becomes `AiClient(provider)` speaking the `/chat/completions`
  shape (messages, `stream: true` SSE with `data:` lines and `[DONE]`, `response_format: {type: "json_object"}` for JSON,
  image parts as `image_url` data URIs). Gemini's OpenAI-compatible endpoint is
  `https://generativelanguage.googleapis.com/v1beta/openai/`; Groq's is `https://api.groq.com/openai/v1/`. Keep the
  current Gemini-native path only if the compatible one loses something we need (thinking off, model discovery).
- Second encrypted key slot in `SettingsRepository` (same Keystore scheme as the Gemini key). Profile → AI keys: Gemini
  (required for scans) and Groq (optional, "fastest chat"). Onboarding mentions Groq in one line, no extra step.
- Routing in `CoachRepository`: chat → Groq if key present (pick its fastest vision-capable model from `/models`,
  prefer the current Llama 4 Scout-class model), else Gemini Flash. Plans and scans → Gemini. Weekly check-in → same as
  chat. Any Groq failure before the first token falls back to Gemini silently; log which provider answered and how
  long to first token (the model menu Opus built can show "answered by Groq · 0.8 s").
- Keep `AiError` messages honest per provider (rate limit, bad key, timeout ≠ offline).

## 2. Plan import fixes (makes docs/prompts/plan-from-youtube.md work end to end)

- Paste a plan: if the pasted text parses as `PlanDraft` JSON (strip a ```json fence if present), import it directly
  with the preview sheet; only non-JSON text goes to the AI converter.
- `WorkoutRepository.applyPlan`: when an item carries a non-blank `videoUrl`, update the existing exercise's link too,
  not just newly created custom exercises. Keep user-edited links if the import's link is identical to the built-in one.

## 3. "Copy prompt" button in the plan menu

In the plan editor's action list (Switch to a ready-made plan / Paste a plan / Import from Google Docs) add a row
"Make a plan from YouTube with Gemini" with subtitle "Copies a prompt. Paste it into the Gemini app with the video
links, then paste the JSON here." Tapping it copies the contents of `docs/prompts/plan-from-youtube.md` (ship it as a
raw resource, `res/raw/plan_prompt.txt`, so the app and the doc stay one file) to the clipboard and shows a snackbar
"Prompt copied". Arabic string for the row; the prompt itself stays in English because Gemini reads it fine.

## 4. Optional carbs (ask the owner before starting; they wanted to see the cost first)

- Room v3 migration: `carbs REAL NOT NULL DEFAULT 0` on `foods` and `meals`. Settings: `carbsGoal: Int`, `trackCarbs:
  Boolean = false`, both in `BackupSettings`.
- When tracking is on: Carbs field in the food editor and Quick log; scan prompt asks for carbs per item and the result
  sheet shows it; Food day totals and the 7-day card gain a carbs line; Progress gets a carbs tile; coach summary
  includes carbs; Profile → Goals shows a carbs goal with a suggestion = calories left after protein (4 kcal/g) and a
  25% fat share, divided by 4.
- Home rings: generalize `ConcentricRings` to a list. With tracking on, three rings (outer→inner: protein, carbs,
  calories) at 13 dp stroke in the same 210 dp; centre still shows protein big and calories small; "Carbs left" joins the
  stat row. With tracking off nothing changes anywhere.

## 5. Log or move a past workout (about 2 h, owner's call)

From the History screen: "Log a past workout" picks a date and opens the live logger for that day (session.epochDay set
to the chosen day, startedAt/finishedAt on that date); and in a session's ⋮ menu, "Change date" moves it. Streak,
records and the consistency grid already key off epochDay, so they follow automatically; re-run record recomputation
for the affected exercises after a date change.

## Still open from before

The hard-test pass (Phase 4 of the earlier prompt) ended before the Progress screen existed; give the new screen the
same treatment once it builds.
