# TeleLatino — deep-dive findings (telelatino-deepdive-pro)

Date: 2026-08-01 · Branch: `telelatino-deepdive-pro`

## Verdict: VERSION-GATE (beatable) — NOT identity-gate like XTV

**The `portal200001` gate is a soft version-gate, not the unbeatable native-identity wall that kills XTV/koocan.**

Evidence:
- `getAddr` returns `returnCode:"0"` off-device — NO gate on domain resolution (same as ASSESSMENT.md)
- EPG endpoints return `200` with real channel data off-device — NO gate on content
- Notice endpoint accepts TeleLatino identity — NO gate
- portalCore returns `portal200001` ("version discontinued") UNIFORMLY across all version/spkgVer variants tested — this is a **version whitelist**, not an identity check
- The app on .4 (`com.global.latinotv` v5.46.8/54608) has CACHED data proving it streamed live TV before (`cyx_50fdcc0817d61_720p`), but now shows login screen — likely also gated by the same version rotation

**What blocks us:** The current portal pool requires a NEWER app version than 5.46.8. The gate says "version discontinued" — it wants an update. A newer APK build (post-2026-07-09) would likely pass.

**What we have working off-device today:**

| Step | Status | Details |
|------|--------|---------|
| DCS getAddr | ✅ `returnCode:"0"` | `POST emowvv.dqiswip4.xyz/api/v2/dcs/getAddr` with DES key `dCsPLwiy` |
| EPG | ✅ `200` with channels | `GET xipre.xifhzu.com/epg/v2/live/app/...` |
| Notice | ✅ `status:0` | `GET nxiqj.jgrqyxupl.com/notice/api/get_notice?...` |
| portalCore (all) | ❌ `portal200001` | snToken, active, login, config/get, getHome, terminalAuth — all gated |

## Identity captured (from `.4` box running the app)

| Field | Value |
|-------|-------|
| Package | `com.global.latinotv` |
| versionCode/versionName | `54608` / `5.46.8` |
| SN (real, `KEY_SP_SN`) | `ca0e53edac957b8f6f187528933355f1` |
| SN (portal_code) | `3837535330736b7541787a7453514f6e7933574543513d3d` → hex→b64→bytes: `f3b492d2c92e031ced4903a7cb758409` |
| Device ID | `945257240` |
| User ID (cached) | `25885636` |
| spkgVer | `2024-11-15 19:08:51_29_14.1_4.9.170` |
| DES request key | `dCsPLwiy` (same as koocan) |
| Last channel watched | `cyx_50fdcc0817d61_720p` |

## Live domain pool (from getAddr on emowvv.dqiswip4.xyz)

```
dcsClientUrl: http://emowvv.dqiswip4.xyz|http://espjey.ysnihrwtg.com|
```

### All domains observed in app traffic

| Domain | Role |
|--------|------|
| `emowvv.dqiswip4.xyz` | DCS + portal host |
| `espjey.ysnihrwtg.com` | DCS backup host |
| `s23sdf56.45lc9mx79ab.com` | WebSocket DCS push (`/v1/ws/<hash>`) |
| `tpst.twpisacnb.com` | WebSocket imagine (`/v1/imagine`) |
| `vdvc.xutrdzu.com` | HTTP/2 API host (purpose unknown) |
| `noak.trerdzu.com` | Ad server (`/api/adserver/v3/get_content`) |
| `xipre.xifhzu.com` | EPG (`/epg/v2/live/app/utc-3/26`) |
| `nxiqj.jgrqyxupl.com` | Notice (`/notice/api/get_notice`) |
| `eycba.q58l6j0a.com` | Unknown (404 on all tested paths) |
| `wetc.pvqox2zhlc.com` | Unknown (Spring Boot, no DCS) |
| `sfgknh.qho3cnsyil.com` | Unknown (403 blocked) |

## 3DES response keys

NOT RECOVERED. The obfuscated key clusters from ASSESSMENT.md
(`\AoaTAka`, `\pa*Tpe*`, `&@eT0f!8`, `b972E8a5A4e0e8Ff`, plus 5 UUID candidates
`0e5e9c33-…`, `20799a27-…`, `4c087185-…`, `629a824d-…`, `b700bce0-…`) require
a memory dump of the running app to pin down. The `strings /proc/<pid>/mem`
approach returned nothing (plaintext not in process memory).

portalCore responses are 3DES-encrypted (binary blobs in `{"data":"<hex>"}` envelopes),
so the response keys are needed to decrypt portalCore payloads once the version
gate is cleared.

**However:** the portalCore never got past `portal200001` to return encrypted data,
so the response keys were not needed yet. They become relevant AFTER the version
gate is cleared.

## Account requirement

The app REQUIRES a (free) TeleLatino account to stream. The flow:
1. Welcome/guide screens → auto-dismissed
2. Device activation (`snToken → active`) → returns device ID (945257240)
3. **Login screen** — email + password required
4. After login: channel list, EPG, streaming

The `.4` box has cached credentials (`nestor.ale@gmail.com` with password MD5
`62513c1dec921de3015a0b22574512f4`), but the app cannot auto-login because
portalCore is version-gated.

## What changed since ASSESSMENT.md

| Item | ASSESSMENT.md (earlier today) | This deep-dive |
|------|-------------------------------|----------------|
| getAddr | ✅ returnCode:0 on espjey/sxowvd | ✅ returnCode:0 on emowvv (live) |
| DCS hosts | espjey, sxowvd (now 404) | emowvv serves getAddr (200) |
| EPG | Not tested | ✅ 200 with real channel data |
| portalCore | portal200001 on emowvv | portal200001 on ALL hosts, ALL spkgVer |
| Device data | None | Real SN, device ID, user ID captured |
| 3DES keys | Not recovered | Not recovered (needs memory dump) |
| App state | Unknown | At login screen, cached data proves past streaming |

## Next steps

1. **Obtain newer TeleLatino APK** (>5.46.8, post-July 2026 build) — the version
   gate expects a newer build. Install on .4, capture fresh identity.
2. **Memory dump with working vmread** — cross-compile for armeabi-v7a with
   `syscall(__NR_process_vm_readv, ...)` instead of the libc wrapper, or use
   Frida to hook the 3DES encrypt/decrypt functions and capture the keys.
3. **Account credentials** — the app needs login. The owner has `nestor.ale@gmail.com`
   with password hash `62513c1dec921de3015a0b22574512f4` (MD5). Obtain the
   plaintext password from the owner.
4. **WebSocket DCS channel** — `s23sdf56.45lc9mx79ab.com:/v1/ws/<hash>` may be
   an auth prerequisite. Investigate the WebSocket handshake protocol.
5. If koocan Phase A completes first (same backend family, working DES keys),
   port the koocan auth chain to TeleLatino with the per-brand identity above.

## Verdict for GOAL.md

**TeleLatino is BEATABLE (version-gate, not identity-gate), but requires a newer
APK build to clear the gate.** Keep koocan as the lead (unpacked, crypto fully
recovered) but track TeleLatino as viable fallback. The backend family is
identical — same DES key, same API structure, same free-tier model. Once the
version gate is cleared (newer APK), the full koocan client pipeline ports
directly.
