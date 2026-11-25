# Master upgrade decisions log (append-only)

- 2026-11-26 Stage 1: Codex `exec -s read-only` cannot read files on this Windows setup (3 failures: policy rejects shell reads; `--approve-for-me` cannot combine with `-s`). Fallback: `--approve-for-me` with a no-edit brief, then `git status` check that nothing changed. Used for all Codex reviews.
- 2026-11-26 Stage 1 Gate 3: Argon APPROVED while Codex found 5 valid defects; both reviewers run on every Gate 3 from now on, findings verified by the main thread.
- 2026-11-26 Stage 1: Option B high-rep bridge kept the light-dumbbell simulator exception (one stall at 7.5 kg, week 10, ends heavier).
- 2026-11-26 Gate answered: all defaults + overrides #3 (short post-round rest on <=30 min), #5 (clean A/B split), #22 HIDE not kill, #20 guard; no Gemini Carbon.
