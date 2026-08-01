# Worker task: TeleLatino — trace the FREE-tier playlist path (standalone recipe)

Branch `telelatino-free-flow` off `master`, isolated worktree. Commit small, push, PR. Do NOT touch
`master`. Read first: `GOAL.md`, `backends/telelatino/LIVE-FREE-TIER.md` (just written — the live free
tier is MAPPED), and the `telelatino-deepdive-pro` branch findings.

## Context
- The TeleLatino app on `.4` (5.46.8) **streams free channels** once you **dismiss the paywall and
  select the FREE option** (this is the step earlier workers missed → that's why they saw portal200001/
  empty channels).
- Live free tier is OPEN: EPG channel list (200), playlist m3u8 (nginx, 1692B, `rd=` + `cyx-*_xycjco_<rd>.ts`
  segments), segments (predictable URLs). **No portalCore needed for live.**
- MISSING LINK: how the app gets the **opaque playlist path** (e.g. `GET /yqixawdzjdmit` — observed in
  `_session/tl_live.pcap`). If it's derivable from channel/EPG → standalone client mints it. If it needs a
  (free) auth/session response → replicate that call.

## Goal — a full STANDALONE off-device recipe for TeleLatino live free
1. On `.4`: launch TeleLatino → **automate dismissing the paywall + selecting FREE** (uiautomator dump +
   input tap; follow `orchestrator/DEVICE-GUIDE.md`). Confirm it reaches free streaming (logcat / pcap
   shows segment fetches).
2. **Trace the opaque playlist path**: fresh pcap (or heap carve like `xtv-native-capture` did) filtered
   to the app. Identify which response contains the opaque path (`/yqixawdzjdmit`-style) and what request
   produces it (EPG channel metadata? a free-auth/login? `getLiveData`? a native call?). Capture it.
3. **Reproduce off-device**: getAddr (open) → channel list (EPG) → opaque playlist path → m3u8 → one
   `.ts`. ffprobe the segment. If it works, you have the standalone recipe.
4. Report: the exact recipe (URLs + how the opaque path is obtained), and whether a free account/session
   is needed (creds for `nestor.ale@gmail.com` are in `orchestrator/.env` as `TELELATINO_USER`/`PASS` if
   a free login helps).

## Constraints
- Box `.4` ONLY. Creds from `orchestrator/.env`. Headless — never wait for a human. If blocked on a
  secret, write `backends/telelatino/NEEDS.md` and stop.
- Do NOT commit the APK / large pcaps (gitignore); commit findings + small scripts.

## Deliverable
`backends/telelatino/STANDALONE-RECIPE.md`: the paywall→free automation, the opaque-path origin, and a
**working off-device sequence** (commands/URLs) that fetches a live TeleLatino channel's m3u8 + a segment.
Then stop.
