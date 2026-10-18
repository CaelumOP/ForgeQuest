# Next coding session (Opus): providers, plan import, copy-prompt button, carbs

Branch `claude/hopeful-meitner-lkx0er`. Start with `docs/HANDOFF-progress.md` (verify the Progress screen builds and
looks right), then do the items below in order. Build and screenshot per item, tests before each commit, English and
Arabic strings for every new label. Don't open a PR; the owner merges.

## 1. Second free AI provider: Groq (no paid providers, ever)

Goal: chat answers in under 2 seconds. Gemini stays for meal scans and plans; Groq takes chat when the person has a
Groq key, with automatic fallback to Gemini.

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

## Still open from before

Coach first-token time measured under 2 s on a phone; the hard-test pass (Phase 4 of the earlier prompt) was cut short.
