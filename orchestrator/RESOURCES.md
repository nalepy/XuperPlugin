# RESOURCES — the optional pool the orchestrator can assign to workers

**All optional.** Assign a resource to a worker ONLY when the task needs it. Pure code/analysis workstreams
use none. **Serialize shared resources** — one worker per box/port/account at a time (put the assignment in
the task spec). Reliable boxes preferred for flaky-sensitive work.

## Rooted Android boxes (for on-device work: install/run/adb/hook/dump)
| Box | Addr | Notes | Reliability |
|-----|------|-------|-------------|
| `.4`  | `192.168.100.4:5555`  | XTV/TeleLatino work | reliable |
| `.8`  | `192.168.100.8:5555`  | Android 7.1.2 (SM-G973F), XTV installed | reliable |
| `.97` | `192.168.100.97:5555` | UniTV / koocan | flaky (works eventually; reboot if it drops) |

## Linux host (builds, apktool, mitmproxy, emulation, relays)
| Host | Access | Notes |
|------|--------|-------|
| `.40` Ubuntu laptop | `ssh xtv40` (key `~/.ssh/id_xtv40`) | `/tmp` wiped on reboot; unidbg harness lives here |

## Oracle Always-Free VMs (cloud compute; keys in `~/Workspace/Oracle/`) — use if a task needs cloud/geo/scale
| VM | Region | Addr | Running services (do NOT disrupt) |
|----|--------|------|-----------------------------------|
| VM1 | Ashburn US   | `ubuntu@193.122.142.132` | Marangatu, myagent, Hermes, Ollama |
| VM2 | São Paulo BR | `ubuntu@159.112.180.199` | MyGPS, Open WebUI, SearXNG, Ollama |
| VM3 | London UK    | `ubuntu@145.241.219.139` | **live FinanceAutomation** (gunicorn :8760), FINEP, Travel — be careful |
Good for: geo-varied network probes (3 regions + home), extra parallel build/compute, hosting a relay/proxy.
Caveat: they run the owner's real services — don't clobber ports/processes; spin up isolated dirs/ports.

## Assignment rules
- Device task → one Android box, named explicitly in the spec (`.4`/`.8` reliable; `.97` last).
- Build/mitm/emulation → `.40`.
- Geo/scale/extra compute → a VM (isolated, non-disruptive).
- No hardware need → none; run purely in the worktree.
> This is XTV-project-specific. The `orchestrate-agents` skill stays generic — a project only needs a
> RESOURCES.md like this if it has external hardware to hand out.

## Off-LAN access (Tailscale) — added 2026-08-01
`.40` runs Tailscale as a **subnet router** `xtv40-subnet` (100.107.11.42), advertising+approved for
`192.168.100.0/24` + `192.168.3.0/24` (key-expiry disabled). Any tailnet node with `--accept-routes`
(Win11 `desktop-8rlei5c` has it) reaches the boxes at their normal LAN IPs (`192.168.100.4/8/97:5555`)
from anywhere. Harness IPs unchanged. **Requirement:** `.40` + the boxes must stay powered at home.
Verified from VM1 (remote): all 3 Android adb ports open via tailnet. Latency higher off-LAN — fine for
control, sluggish for large memory dumps.
