# Set-engine overhaul: decisions log (append-only)

This file stands in for the requested `docs/DECISIONS.md`: on Windows that name is the same file as the existing, tracked `docs/decisions.md` (the web PWA decision log, outside this overhaul's fence), so it would have overwritten it.

## 2026-11-24 Phase 0: git safety and tool preflight

- Branch: `overhaul/set-engine-and-ux`, cut from `release/4.2` at `186cc29`. Uncommitted user work carried over untouched and never staged by this overhaul: `CLAUDE.md`, `web/CLAUDE.md` (modified) plus untracked `.agents/`, `.repowise/`, `AGENTS.md`, `design-research/`, `review-shots/`, `tools/motion-video/`, `tools/qa/`, `worker/test/score41-*`, `worker/wrangler.mig0004.toml`, `web/.claude/`, `worker/.claude/`, `node_modules/`, `.claude/launch.json`, `docs/cardio-feasibility.md`. Commits in this overhaul stage explicit paths only.
- `agy models` (agy 1.5.x) lists, among others: `gemini-4-neon-{low,medium,high,xhigh}`, `gemini-4-hydrogen-{low,medium,high,xhigh}`, `gemini-4-argon-{low,medium,high}`, `gemini-4-carbon-*`, `gemini-pro-orchestrator-neon` (Orchestrator (Neon)), `gemini-pro-orchestrator`, `gemini-pro-orchestrator-argon`, `gemini-3.8-flash-*`.
- `agy --help` flags: `-p/--print`, `--model`, `--effort low|medium|high|xhigh|max`, `--mode accept-edits|auto|plan`, `--print-timeout`, `--settings`, `--output-format`, `--json-schema`, `--sandbox`, `--dangerously-skip-permissions`.
- Invocation: through the owner's wrappers `D:/Claude/bin/agy-read` (plan mode, read/search tools only) and `D:/Claude/bin/agy-build` (per-run edit + command permissions via `--settings`). These are the owner's standing rule and avoid `--dangerously-skip-permissions`. Every job is logged to `D:/Claude/logs/agy-jobs.ndjson`.
- Model routing for this run: Phase 1 audit = `gemini-4-neon-xhigh` (read); research = `gemini-4-neon-high` (read); subsystem builds = `gemini-pro-orchestrator-neon` or `gemini-4-neon-high` via agy-build, one at a time; reviews = `gemini-4-argon-high` (owner's Gate rule) plus the cold Phase 5 review.
- Codex: `D:/Claude/tools/codex/codex.cmd exec --help` works (`-m`, `-c key=value`, `-C <dir>`, `-s/--sandbox`, prompt from stdin with `-`). Phase 5 cold review will use `codex exec -m gpt-6-astra -s read-only -C <repo> - < brief.md` (flags re-checked against `--help` before running).

## 2026-11-24 Phase 2: spec lock and build environment

- Owner answered the Question Gate: "Accept all defaults", plus a hold-timer request (overshoot past target and 5 s stepper). Both locked in `docs/TRAINING_SCIENCE_SPEC.md` section 0.
- Gradle on this machine fails with `java.io.IOException: Unable to establish loopback connection` (Bash sandbox, PowerShell and unsandboxed alike). Cause: JDK 17 creates its selector-wakeup Unix-domain socket in `%TEMP%`. Fix without touching system settings: `JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=D:\Claude\tmp\jdk`, run unsandboxed. Overriding `java.io.tmpdir` as well breaks the daemon (`ValueSnapshotter`), so only the socket dir is set. Wrapper: `.claude/scratch/gw.sh`.
- `~/.claude/hooks/quiet.py ./gradlew ...` reports `Exit: 0` even when cmd cannot find the command; it is not used for Gradle here.
- Baseline before any change: `./gradlew testDebugUnitTest` exit 0, 481 tests, 0 failures.
- Gate 1 (Argon) on the spec: CHANGES, 12 points; all accepted as spec section 11 (A1-A12), except effort editing in History session detail, deferred to the report.
