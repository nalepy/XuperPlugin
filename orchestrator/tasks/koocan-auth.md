# Worker task: finish the off-device koocan auth chain (Phase A)

You are a worker agent in an isolated git worktree on branch `koocan-auth`, off `master`, in the
XuperPlugin repo. Orchestrator coordinates via git — commit small, push your branch, do NOT touch
`master`. Read `GOAL.md` (north star) and `GOAL2.md` "Session 33" first.

## Runtime environment
- Boxes: **`.4` is the reliable rooted device — prefer it.** `.97` is flaky (avoid). Ubuntu `.40` is alive
  (`ssh xtv40`) if you need a Linux host. But this task is **off-device** — only touch a box if you must
  read a value (e.g. a SN); do NOT build a memory-harvest.
- You MAY spawn your own kimi subagents for parallel sub-steps if it helps. Keep everything on your branch.

## Context (already proven)
- Tools + intel are in `backends/koocan/` (tracked) — especially `koocan_client.py` (full DES/3DES crypto
  + koocan endpoints) and `mint_tokens.py`. (Full live cookies/binaries: untracked `_session/fakeunitv_intel/`.)
- PROVEN working off-device: `python3 backends/koocan/koocan_client.py dcs-test --sn <SN>` →
  `getAddr` returns `{"returnCode":"0","dcsClientUrl":"...","errorMessage":"success!"}`. koocan accepts
  off-device clients (no version-gate, no native identity) — unlike XTV.

## Your goal
Drive the koocan chain to a **full, all-channels, off-device live stream** and document the exact recipe.
Ordered steps (extend `koocan_client.py`; keep it runnable, commit after each working step):
1. Resolve the **live portal host** (`portalcore.koocan.com` is NXDOMAIN; the working host comes from the
   `getAddr` `dcsClientUrl`/alias chain or the box config). Confirm a reachable portal host.
2. `snToken` → `SN = md5(snToken+"cloudstream")` → `/api/portalCore/v3/active`. Determine whether device
   activate alone yields a streamable token, or a **free account login** is needed
   (`/api/portalCore/v3/login` or `/api/MMS/terminal/login`, pwd = `md5(pwd+"cloudstream")`).
3. `getAuthInfo` + `getSlbInfo` (v5) → DES-decrypt the **stream host pool** + play-URL builder.
4. `getColumnContents` / `getLiveData` → the **full channel list** + per-channel playlist path.
5. Fetch one live `.m3u8` + one `.ts` end-to-end; confirm with `ffplay`/`ffprobe`. Report the **channel
   count** so we can check parity with the vendor app.

## Constraints
- Off-device only (run from this box). Do NOT depend on the `.97`/`.4` boxes or any memory-harvest —
  that path is explicitly demoted (see `GOAL.md`).
- If a koocan account is required, STOP and write what's needed to `backends/koocan/NEEDS.md` on your
  branch, commit it (the orchestrator will get credentials from the owner). Do not guess/brute credentials.
- Commit working increments to branch `koocan-auth` with clear messages; push the branch. Do not merge.

## Deliverable
- An extended `koocan_client.py` (or a new `backends/koocan/` module) that runs the full chain to a live
  playlist, plus a short `backends/koocan/FINDINGS.md`: live portal host, whether login is needed, the
  channel count, and the play-URL format. Then stop and let the orchestrator review.
