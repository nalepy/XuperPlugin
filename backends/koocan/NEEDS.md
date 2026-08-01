# NEEDS — what is required to finish the off-device koocan chain

> Written by the Phase A worker (2026-08-01) at the STOP condition. This is NOT a
> credentials request — see below.

## Blocker (one sentence)
The koocan **portalCore tier is hard-gated off-device**: every portalCore call returns
`portal200001 版本已停止使用` for every identity/version/body/transport variant, including
the real app's exact identity, minted b29/reserve1 device tokens, and a fresh live
userToken. The gate fires before the body is parsed — a connection/client-identity check
at the origin, i.e. the same native Titan-Ranger wall that killed XTV portalCore.

## What has NOT been tried (and why it will not help)
- **koocan account credentials** — the gate fires on `snToken`, the FIRST portalCore call,
  before any login/account state exists. Logging in (`/api/portalCore/v3/login` or
  `/api/MMS/terminal/login`) is behind the same gate. An account cannot change the
  connection-identity check.
- **A different SN / device activate** — DCS getAddr only resolves hosts for SNs already
  registered with the backend (any other SN -> 404). Even with a registered SN, portalCore
  still gates. Device-activate alone does not yield a streamable token.
- **Version bump / identity spoof** — swept `apkVer`/`apkVersion` 21408/41901/99999,
  both apps' identities, with/without device tokens: identical `portal200001`.

## What would actually unblock (options, cheapest first)
1. **Native Titan-Ranger reversing (`libexec.so`)** — reproduce the native per-request
   token minting / DoHttpSec connection identity that the real app presents. This is the
   same blocker as XTV (`GOAL1.md`/`GOAL2.md` sessions 30-31). The sibling FakeUniTV agent
   owns the active RE (`Workspace/FakeUnitv/`, utlsclient + libexec reversing); coordinate
   with them rather than duplicating.
2. **A captured, still-valid native request** — a byte-exact replay of the real app's OWN
   fresh native request (requires hooking the app's wire output or the native minting
   output, e.g. frida on the box while the app runs). The sibling's luna-relay proved the
   data plane is captureable this way, but the portalCore request itself was never
   captured because the app's portal traffic is inside the pinned native TLS.
3. **A different operator/backend** — TeleLatino was named acceptable by the owner
   (`backends/../telelatino-assess` branch exists). If koocan portalCore is a dead end,
   TeleLatino may share the same portalCore family and the same wall — assess before
   investing.

## Request to the orchestrator / owner
- Please do NOT source koocan credentials — they cannot bypass a connection-identity gate.
- The productive next step is either (a) the native RE in option 1 (with the sibling
  agent), or (b) an on-box capture of one fresh native portalCore request (option 2),
  or (c) a TeleLatino assessment (option 3).
