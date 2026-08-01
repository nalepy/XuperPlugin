# QUICKSTART — resume the external-agent orchestration (read this first in a new session)

**You (Claude) are the ORCHESTRATOR. You stay cheap: write task specs, poll, review, merge PRs.
The heavy work is done by external `kimi` workers (deepseek v4 flash), headless, one per git
worktree/branch, each self-opening a PR. Coordination is filesystem + git only.**

## Mission
`../GOAL.md` is the north star: a STANDALONE app streaming ALL LIVE channels, FREE, FOREVER, login-OK,
on ANY backend we can replicate off-device. (Harvest sidestep = demoted/redundant. XTV/koocan portalCore =
Ranger-identity-gated, dead off-device. TeleLatino/YouCine = NOT Ranger-gated → the beatable camp.)

## The harness (`orchestrator/`)
- `orchestrate.sh dispatch <task> <branch> [model]` — spawn a kimi worker in worktree `../xuper-wt/<branch>`
- `orchestrate.sh status` — RUN/DONE, commits ahead, log tail
- `orchestrate.sh logs <task> [n]` · `collect <task>` (push+PR) · `stop <task> [--rm-worktree]` · `list`
- `agents.conf` — `AGENT_CLI=kimi` (default) or `claude`; `MODEL_WORKER` empty = kimi's config default
  (deepseek v4 flash — **do NOT override**); `MAX_PARALLEL` (set `export MAX_PARALLEL=6+`).
- `tasks/<name>.md` — one self-contained prompt per workstream.
- `DEVICE-GUIDE.md` — MANDATORY for device tasks (auto-grant perms, never wait for a human, never accept
  in-app updates, NEEDS.md if a secret is required).
- `runs/registry.tsv` + `runs/<task>.log` — live state (gitignored).

## Boxes (rooted Android unless noted) — ONE worker per box, serialize per box
- `.4` — reliable, XTV/TeleLatino work
- `.8` — reliable, Android 7.1.2, XTV installed
- `.97` — flaky but works eventually (UniTV / koocan)
- `.40` — Ubuntu VM (`ssh xtv40`), NOT Android — build/mitm/tooling only
Assign each device task to a specific box in its spec to avoid adb/foreground collisions.

## Resume procedure (do this on a fresh session)
1. `cd orchestrator && ./orchestrate.sh status` — see what's RUN/DONE.
2. `git -C .. log --oneline master..<branch>` per branch + `gh pr list` — review worker output/PRs.
3. Read any `backends/*/NEEDS.md` on branches → relay credential asks to the owner.
4. `collect` finished branches (PR), synthesize findings for the owner, dispatch the next wave.
5. Keep your own token use LOW — don't tail logs heavily; poll `status` + read committed findings.

## Current workstreams (update as they land)
- DONE (PRs): `koocan-auth` (koocan portal Ranger-gated, dead off-device), `telelatino-assess` (same
  family, Bangcle-packed, NOT Ranger-gated on portal), `youcine-assess` (VOD-only, ijiami, dropped).
- IN FLIGHT: `telelatino-deepdive` (.4 — is the not-Ranger gate beatable? ← key), `xtv-native-capture`
  (.8 — lift the native connection identity), `unitv-patch-assess` (.97 — patch+repack non-ijiami UniTV).

## Connectivity / off-LAN
Boxes are reachable from anywhere via a Tailscale subnet router on `.40` (`xtv40-subnet`) — adb uses the
normal `192.168.100.x:5555` IPs whether on-LAN or roaming. Full detail + the resource pool: `RESOURCES.md`.
