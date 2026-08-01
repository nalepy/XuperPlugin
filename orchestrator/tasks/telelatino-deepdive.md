# Worker task: TeleLatino deep-dive — is its portal gate BEATABLE off-device?

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
