# Worker task: TeleLatino deep-dive — is its portal gate BEATABLE off-device?

> **MANDATORY (device): follow `orchestrator/DEVICE-GUIDE.md` — install with `-g`, auto-grant perms via pm grant/appops, uiautomator-tap stray dialogs, NEVER wait for a human, NEVER accept in-app updates; if a secret/account is truly required write NEEDS.md and stop.**

Branch `telelatino-deepdive` off `master`, isolated worktree. Commit small, push, open a PR. Do NOT touch
`master`. Read `GOAL.md` + the `telelatino-assess` branch's `backends/telelatino/ASSESSMENT.md` first
(key finding: **TeleLatino's portal flow is NOT Ranger-DoHttpSec-gated**, unlike XTV/koocan — so its
`portal200001` may be a *soft version-gate*, not the unbeatable native-identity wall).

## The one question to answer
Is TeleLatino's `portal200001` a **version-gate we can beat off-device** (right version/host/keys → a real
`returnCode:0` from a portalCore call), or the **same dead identity-gate** as XTV/koocan?

## Method (use box `.4` — reliable; `.40` ubuntu if needed; avoid `.97`)
1. **Get a registered SN + the live domain pool.** `getAddr` is SN-keyed (only registered SNs resolve).
   Install/run TeleLatino (`com.global.latinotv`, APK in `_session/TeleLatino.apk`) on `.4`, capture its
   real SN + the `getAddr` `dcsClientUrl` domain pool + the exact `apkVer`/`spkgVer`/version fields it sends
   (logcat / prefs / a memory dump via `backends/koocan/vmread.c`). This gives you the *current, accepted*
   identity — the thing our stale probes lacked.
2. **Pin the 3DES response keys.** ASSESSMENT.md lists 5 UUID candidates + obfuscated key clusters; resolve
   the real request/response keys from a memory dump of the running app (same technique as koocan).
3. **Replay the full chain off-device** with the box's real version + live host + correct keys:
   `snToken → active/login → getAuthInfo → getSlbInfo → getColumnContents/getLiveData`. If ANY returns
   `returnCode:0`, the gate is beatable → capture the channel list + play-URL format.
4. **Decide the verdict:** does off-device replication work once you match the box's real version/host/keys
   (→ version-gate, BEATABLE → standalone path!), or does it still `portal200001` even byte-exact from the
   box's own identity (→ identity-gate, dead like XTV)?

## Constraints
- If it needs a (free) TeleLatino account to activate, write `backends/telelatino/NEEDS.md` on your branch,
  commit, and stop — the orchestrator gets creds from the owner. Don't brute/guess.
- You MAY spawn kimi subagents. Don't commit the APK / large dumps (gitignore them).

## Deliverable
Update `backends/telelatino/FINDINGS.md`: the verdict (version-gate beatable vs identity-gate dead), the
live host + accepted version + keys if found, any `returnCode:0` reached, channel count + play-URL format,
and a `telelatino_probe.py` that reproduces. Then stop. If BEATABLE, say so loudly — it becomes the plan.

## UPDATE (session 33b) — creds available, CONTINUE
- **FREE account (works, streams free channels NOW):** `nestor.ale@gmail.com` / `Ian20jesus` — in `orchestrator/.env` as `TELELATINO_USER`/`TELELATINO_PASS` (gitignored). Read them from there.
- The owner is ALSO hunting a **newer TeleLatino APK** (post-2026-07-09) in parallel — if one appears, that's the cleanest version-gate unlock.
- **Resume from your FINDINGS:** you proved getAddr/EPG/notice pass off-device and portalCore is a VERSION whitelist. Now:
  1. **Login off-device with the free creds** → getAuthInfo → does portalCore now return `returnCode:0` (i.e. is the gate a *login*-gated whitelist, not just version)? 
  2. Capture the **3DES response keys** from the box memory (the last unknown).
  3. Pull the **FREE channel list** + one live `.m3u8`/`.ts` end-to-end off-device → ffprobe it.
  4. If a newer APK exists, diff its versionCode/whitelist handling.
- Commit findings + working code to your branch, push, PR.
