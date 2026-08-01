# SESSION RESUME — 2026-08-01 16:40 PST (machine shutting down)

**Resume point:** TeleLatino standalone recipe in progress, blocked on the channel-hash mapping.
Fresh session: read `GOAL.md` + this file, then `orchestrator/QUICKSTART.md` for orchestration state.

## TeleLatino live-free standalone recipe — state
MAPPED (in `backends/telelatino/LIVE-FREE-TIER.md`):
- getAddr open (returnCode 0), EPG channel list open, segments predictable
  `/live/cyx-<HASH>/cyx-<HASH>_xycjco_<rd>.ts`, P2P mesh `<hash>_file.peer`, playlist nginx 1692B.
- **m3u8 path is predictable: `/live/cyx-<HASH>.m3u8`** (worker finding).

BLOCKER:
- The **channel name→hash mapping** comes from **portalCore (GATED, portal200001)** — it's a version
  whitelist at the `apkVer` header (NOT login, NOT identity). The whitelist wants a NEWER latinotv build
  (>5.46.8). The 6.2.3/60203 msandroid APK did NOT pass (different package).
- TeleLatino's **3DES response keys are DIFFERENT from koocan's** — koocan keys (b940e017-…/c6768bbe-…)
  do NOT decrypt TeleLatino's BBDatabase `res` field. Get TeleLatino's keys from the running app's memory
  (it has them loaded).

NEXT STEPS (for the resume):
1. **Get the channel-hash mapping** — either (a) the updated policy says ACCEPT the in-app update on `.4`
   to get the whitelist-passing build, then portalCore opens → mapping; or (b) carve TeleLatino's 3DES
   keys from the running app memory, decrypt the BBDatabase `res` field (has channel mappings); or
   (c) accept-update on the box while a pcap runs to capture the current accepted getLiveData response.
2. Then the standalone recipe completes: getAddr → hash → `/live/cyx-<HASH>.m3u8` → segments.

## Orchestration state
- 7 PRs open (#1–#7) from earlier workers (koocan-auth, telelatino-assess, youcine, unitv-patch,
  xtv-native-capture, telelatino-deepdive ×2).
- Worker palette: 10 CLIs (agents.conf). Benchmarks in `orchestrator/BENCHMARK.md` (fib + games rounds).
- Devices: .4/.8/.97 (adb via Tailscale subnet router on .40), reachable off-LAN.
- Alerts: watch-alerts.sh → Telegram via Hermes (running; will die with this machine — relaunch on resume:
  `cd orchestrator && nohup ./watch-alerts.sh >/dev/null 2>&1 &`).
- Free-flow worker stopped at 16:40 (partial findings in log `orchestrator/runs/telelatino-free-flow.log`).
