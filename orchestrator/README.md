# orchestrator/ — external worker-agent harness

The **orchestrator** (an expensive Claude session) dispatches **cheaper worker agents** (default: `kimi`,
also `claude`) into **isolated git worktrees/branches**, polls them, and collects their work as PRs.
CLI-agnostic — swap the worker CLI in `agents.conf` without touching the harness. Coordination is
filesystem + git only (no screen-watching).

## Files
- `orchestrate.sh` — the harness (dispatch / status / logs / collect / stop / list).
- `agents.conf` — worker CLI + model + concurrency + the launch adapter (`kimi -p … --auto`, `claude -p …`).
- `tasks/<name>.md` — one self-contained prompt per workstream (the worker's spec).
- `runs/` — per-worker logs + `registry.tsv` (gitignored).

## Model split (the whole point)
- Orchestrator = this Claude session (expensive) — writes tasks, reviews diffs, merges PRs.
- Workers = `kimi --auto` (cheap, fully autonomous) — one per branch, in its own worktree/process.
- Set `MODEL_WORKER` / `MAX_PARALLEL` in `agents.conf` (or env) to throttle cost.

## Flow
```bash
cd orchestrator
./orchestrate.sh list                              # see task specs
./orchestrate.sh dispatch koocan-auth koocan-auth  # spawn a worker on branch koocan-auth
./orchestrate.sh status                            # RUN/DONE, commits ahead, log tail
./orchestrate.sh logs koocan-auth 60               # tail its output
./orchestrate.sh collect koocan-auth               # push branch + open PR (needs gh)
./orchestrate.sh stop koocan-auth --rm-worktree    # kill + drop worktree
```

## How a worker is launched (headless)
`agents.conf` → `agent_exec`:
- kimi: `kimi -p "<task>" --auto --output-format text [-m MODEL]`  (`--auto` = never blocks on questions)
- claude: `claude -p "<task>" --permission-mode acceptEdits [--model MODEL]`
Each runs with its worktree as CWD, output appended to `runs/<task>.log`, PID recorded in `runs/registry.tsv`.

## Notes / limits
- Workers can't do interactive auth (login flows). If a task needs credentials, the worker writes
  `runs/<task>.needs` and stops; the orchestrator gets them from the owner.
- Each worker burns tokens under the account — `MAX_PARALLEL` caps concurrency.
- One worktree per branch; `master` stays untouched — workers push branches, orchestrator PRs them.
- This is a PROTOTYPE. Nothing auto-launches. The orchestrator (Claude) runs the subcommands deliberately.
