# Worker backend benchmark (2026-08-01)

Benchmarked the **9 cheap worker backends** on an identical, small coding task. Results live in
`_bench/` (per-backend `fib.py` + `run.out`, `SUMMARY.tsv`). All 9 **passed** the task (correct
`fib(20)==6765`), so the ranking below is about speed / token-efficiency / code quality, not correctness.

## Task
Write `fib.py` (iterative `fib(n)`, print `fib(20)`, `assert fib(20)==6765`), run it, confirm output.

## Results (fastest → slowest)
| backend | wall_s | out_chars* | quality | notes |
|---|---|---|---|---|
| **kimi-flash** | **9.8** | 308 | clean, correct | fastest; cheapest |
| kimi-pro | 13.1 | 301 | clean, 11 lines | good cheap pro |
| opencode-flash | 14.6 | 338 | clean | |
| opencode-bigpickle | 15.3 | 343 | clean | free Zen model |
| opencode-pro | 17.6 | 334 | **best** (guards + `__main__`) | top code quality |
| command-pro | 20.8 | **11** | clean | tersest reply = fewest tokens |
| command-flash | 23.2 | **11** | clean | tersest reply |
| claude-flash | 24.6 | 200 | clean | |
| claude-pro | 33.3 | 200 | clean | slowest |

\* `out_chars` = output size, a **token proxy** (the CLIs don't report exact token counts in print mode).
`command-*` reply is a terse one-liner → 11 chars → minimal wasted tokens. kimi/opencode print reasoning.

## Recommendations (per task type)
- **Speed + cheapest:** `kimi-flash` (10s, clean).
- **Best code quality:** `opencode-pro` (defensive, readable) — use for "write it well" tasks.
- **Most token-efficient (terse, chatty-free):** `command-flash`/`command-pro` — good for high-volume
  fan-out where you want the answer, not the commentary.
- **Pro reasoning at low cost:** `kimi-pro` (13s) or `opencode-pro` (quality) — prefer over `claude-pro`
  (33s) unless you specifically need claude-code machinery.
- **claude-pro/flash:** slowest — only worth it if a task needs claude-code's skills/subagents; otherwise
  the kimi/command/opencode deepseek variants are faster and cheaper for the same model.

## Caveats
- Small single-file task; ranking may differ on long, tool-heavy RE work (e.g. deep-seek-heavy tasks favor
  pro variants; the telelatino PRO worker is the live example).
- Token counts are a proxy (out_chars/4), not API-verified usage.
- `claude-frontier` (Anthropic) was intentionally excluded — it's the expensive reference, not a worker.

## claude-frontier — PENDING (session-limited)
The consumer **Pro** account (OAuth, `.credentials.json`) is **session-limited** (resets ~3:40pm
America/Asunción). Valid frontier model ids on this account: `opus`, `sonnet`, `haiku`, `claude-opus-5`
(specific-version ids like `opus-4.8`/`sonnet-5`/`haiku-4.5` are NOT valid here — use the short aliases).
IMPORTANT: the deepseek env vars (`ANTHROPIC_BASE_URL=https://api.deepseek.com/anthropic` +
`ANTHROPIC_AUTH_TOKEN` + `ANTHROPIC_MODEL`) route `claude` to deepseek and OVERRIDE OAuth — to hit the
Pro account you must `env -u ANTHROPIC_BASE_URL -u ANTHROPIC_AUTH_TOKEN -u ANTHROPIC_MODEL claude …`.
Re-run the 4 frontier rows via `_bench/frontier-*.sh` (or the loop in this doc) after the limit resets.

## Round 2 — creative task (Space Invaders HTML game, 2026-08-01)
All 9 backends, same task ("pure HTML 80s Space Invaders", self-contained, canvas+JS). Each saved to
`_bench/games/<backend>/space_invaders.html` (openable in a browser to judge quality).
| backend | wall_s | html_bytes | result |
|---|---|---|---|
| opencode-flash | **24.3** | 4917 | ✅ canvas+js |
| kimi-flash | 30.4 | **11554** | ✅ canvas+js |
| claude-flash | 34.2 | 10668 | ✅ canvas+js |
| kimi-pro | 46.1 | 7379 | ✅ canvas+js |
| opencode-pro | 49.9 | 7994 | ✅ canvas+js |
| opencode-bigpickle | 56.3 | **12305** | ✅ canvas+js (most verbose out=6121) |
| command-pro | 58.8 | 0 | ❌ NO_FILE (failed to write) |
| claude-pro | 69.6 | 11781 | ✅ canvas+js |
| command-flash | **136.8** | 10109 | ✅ canvas+js (slowest) |
**Takeaways:** opencode-flash fastest, kimi-flash best size-per-time, opencode-bigpickle largest output
(verbose). **command-pro failed a creative write task** (worked on fib) — noted as a reliability caveat.
All games are in `_bench/games/` for the owner to eyeball.
