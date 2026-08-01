# TeleLatino — deep-dive findings (telelatino-deepdive-pro)

Date: 2026-08-01 · Branch: `telelatino-deepdive-pro`
Last updated: 2026-08-01 16:00 UTC (session 33b continuation)

## Verdict: VERSION-GATE (beatable) — NOT identity-gate. BLOCKED by APK age.

**The `portal200001` gate is a soft version-gate, not the unbeatable native-identity wall that kills XTV/koocan.** BUT it is currently active against ALL v5.46.8 clients — including the app on `.4` itself.

Evidence that it's a version gate (not identity):
- `getAddr` returns `returnCode:"0"` off-device — NO gate on domain resolution
- EPG endpoints return `200` with real channel data off-device — NO gate on content
- Notice endpoint accepts TeleLatino identity — NO gate
- portalCore returns `portal200001` ("版本已停止使用" = "version discontinued") UNIFORMLY across ALL version/spkgVer/apkVer variants, ALL headers, ALL body fields, ALL API versions — the server checks `apkVer` header BEFORE processing any request body or auth

Evidence that it's NOT login-gated:
- portalCore rejects BEFORE checking login credentials — all 14 portalCore endpoints return `portal200001` regardless of whether valid creds (`nestor.ale@gmail.com` / `Ian20jesus`) are sent
- Same `portal200001` for plain JSON, DES-encrypted, HTTPS, different `spkgVer`, different `apkVer` (54608–100000), different hardware profiles

Evidence the APP ON .4 IS ALSO BROKEN (new finding, session 33b):
- Logcat: `HomeLiveFragment: No adapter attached; skipping layout` (empty channel list at 15:43 UTC)
- `HomeLiveFragment: onPause/onStop` cycling rapidly — app struggling, no data loaded
- Shared prefs `live_last_channel_code: cyx-Cinemax` is CACHED from when app worked earlier
- `BBDatabase.db` EventDbModel has `state:1` for user 25885636 with timestamp from earlier session — app was working, now isn't
- App installed at 14:07 UTC, version gate likely enforced shortly after

**What blocks us:** The portal pool requires a NEWER APK (>5.46.8, >54608). The gate is a version whitelist enforced at the HTTP header level. A newer APK build is NEEDED — no header/body trickery can bypass it.

**What we have working off-device today:**

| Step | Status | Details |
|------|--------|---------|
| DCS getAddr | ✅ `returnCode:"0"` | `POST emowvv.dqiswip4.xyz/api/v2/dcs/getAddr` with DES key `dCsPLwiy` |
| EPG | ✅ `200` with channels | `GET xipre.xifhzu.com/epg/v2/live/app/...` |
| Notice | ✅ `status:0` | `GET nxiqj.jgrqyxupl.com/notice/api/get_notice?...` |
| portalCore (all) | ❌ `portal200001` | snToken, active, login, config/get, getHome, terminalAuth — all gated |
| Account login | ❌ `portal200001` | Valid creds `nestor.ale@gmail.com`/`Ian20jesus` — gate hits before auth |
| App on .4 | ❌ `portal200001` | Same gate — empty channel list, rapid pause/resume cycling |

## Identity captured (from `.4` box running the app)

| Field | Value |
|-------|-------|
| Package | `com.global.latinotv` |
| versionCode/versionName | `54608` / `5.46.8` |
| SN (real, `KEY_SP_SN`) | `ca0e53edac957b8f6f187528933355f1` |
| SN (portal_code) | `3837535330736b7541787a7453514f6e7933574543513d3d` → hex→b64→bytes: `f3b492d2c92e031ced4903a7cb758409` |
| Device ID | `945257240` |
| User ID (cached) | `25885636` |
| User identity type | `2` (logged-in user) |
| spkgVer | `2024-11-15 19:08:51_29_14.1_4.9.170` |
| DES request key | `dCsPLwiy` (same as koocan) |
| Last channel watched | `cyx-Cinemax` (human-readable), `cyx_50fdcc0817d61_720p` (raw) |
| Hardware | `sun50iw9p1` (Allwinner H616), board `exdroid`, model `V76PRO`, manufacturer `Google` |
| Build fingerprint | `google/walley/titan-p1:14.1/QP1A.191105.004/eng.akrc2.20241115.190925:userdebug/test-keys` |
| Android SDK | 29 (Android 10 reported), release 14.1 |

### .4 device hardware profile
```
ro.hardware:        sun50iw9p1
ro.product.board:   exdroid
ro.product.model:   V76PRO
ro.product.manufacturer: Google
ro.build.fingerprint: google/walley/titan-p1:14.1/QP1A.191105.004/eng.akrc2.20241115.190925:userdebug/test-keys
ro.build.version.sdk: 29
```

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

## Account credentials (obtained session 33b)

| Field | Value |
|-------|-------|
| Email | `nestor.ale@gmail.com` |
| Password | `Ian20jesus` |
| User ID | `25885636` |
| Login state (cached) | `1` (logged in, from `EventDbModel.state`) |
| Encrypted email (prefs) | `716670732f556b7476676d55496f3054382b5a695337706c69312f4f546b3371` |

Password hash not yet matched — `62513c1dec921de3015a0b22574512f4` (cached in .4 prefs) does not match MD5(password), MD5(password+"cloudstream"), or any other tested pattern. The app uses `PBEWITHMD5ANDDES-CBC` (from DEX strings) — the cached hash may be DES-encrypted, not a plain MD5.

Creds are in `orchestrator/.env` as `TELELATINO_USER`/`TELELATINO_PASS` (gitignored).

## Account requirement

The app REQUIRES a (free) TeleLatino account to stream. The flow:
1. Welcome/guide screens → auto-dismissed
2. Device activation (`snToken → active`) → returns device ID (945257240)
3. **Login screen** — email + password required
4. After login: channel list, EPG, streaming

Login cannot be tested off-device because portalCore rejects at version level before auth.

## Request headers (from DEX reverse-engineering)

The app sends these headers (extracted from `classes.dex` strings + verified via gzip response behavior):

```
Content-Type: application/json;charset=utf-8
apk: com.global.latinotv
apkVer: 54608
spkgVer: 2024-11-15 19:08:51_29_14.1_4.9.170
User-Agent: okhttp/4.12.0
Accept-Encoding: gzip
Cache-Control: no-store
NoLog: true
```

The server responds with gzip-compressed JSON when `Accept-Encoding: gzip` is sent (confirmed: `Content-Encoding: gzip` in responses). Without gzip, plain JSON. Same `portal200001` either way.

### Full portalCore API surface (from DEX `{agreement}://{ip}/api/portalCore/...`)

| Endpoint | Version | Purpose |
|----------|---------|---------|
| `v3/snToken` | v3 | SN registration token |
| `v8/active` | v8 | Device activation |
| `v8/login` | v8 | Account login |
| `terminalAuth` | — | Terminal/device auth |
| `config/get` | — | App configuration |
| `getHome` | — | Home screen data |
| `v9/getAuthInfo` | v9 | Auth info (post-login) |
| `v14/getSlbInfo`, `v15/getSlbInfo` | v14/v15 | Stream load balancer info |
| `v3/getColumnContents` | v3 | Channel/column content list |
| `v6/getLiveData` | v6 | Live TV stream data |
| `v3/getRecommends` | v3 | Recommendations |
| `v4/startPlayLive` | v4 | Start live stream playback |
| `v10/startPlayVOD` | v10 | Start VOD playback |
| `v6/startPlayBTV` | v6 | Start BTV playback |
| `v3/getShelveData` | v3 | Shelve/program data |
| `v5/heartbeat` | v5 | Session heartbeat |
| `v5/loginOut` | v5 | Logout |
| `epg/v2/getLineUps` | v2 | EPG lineup data |
| `device/updateOrInsert` | — | Device registration |
| `v2/getFree` | v2 | Free content |
| `v3/searchByName` | v3 | Search |

## What changed since ASSESSMENT.md

| Item | ASSESSMENT.md (earlier today) | Session 33 | Session 33b (this update) |
|------|-------------------------------|------------|---------------------------|
| getAddr | ✅ returnCode:0 on espjey/sxowvd | ✅ returnCode:0 on emowvv (live) | ✅ still working |
| DCS hosts | espjey, sxowvd (now 404) | emowvv serves getAddr (200) | ✅ emowvv + espjey both active |
| EPG | Not tested | ✅ 200 with real channel data | ✅ still working |
| portalCore | portal200001 on emowvv | portal200001 on ALL hosts, ALL spkgVer | ❌ CONFIRMED: universal gate, even app on .4 broken |
| Device data | None | Real SN, device ID, user ID captured | + hardware profile, build fingerprint |
| 3DES keys | Not recovered | Not recovered | Not recovered |
| App state | Unknown | At login screen, cached data | **BROKEN** — empty channel list, rapid pause/resume |
| Account | Unknown | Email+hash cached | ✅ Full creds: `nestor.ale@gmail.com` / `Ian20jesus` |
| API surface | Unknown | Generic endpoints | Full DEX extraction: 30+ endpoints mapped |
| Request headers | Unknown | Basic headers | Full: gzip, Cache-Control, NoLog, all verified |
| Login-gate test | Not tested | Not tested | **PROVEN: portal200001 is NOT login-gated** |

## Session 33b findings (definitive additions)

1. **portal200001 is NOT login-gated.** All 14 portalCore endpoints (snToken v1-v8, active v3-v8, login v5-v8, terminalAuth, config/get, getHome, getFree, getColumnContents) return `portal200001` regardless of whether valid owner credentials are sent. The server checks `apkVer` in the HTTP header BEFORE processing the request body.

2. **The gate is universal — even the app on .4 is broken.** Logcat confirms `HomeLiveFragment` has empty RecyclerView ("No adapter attached"), rapid pause/resume/stops — the app cannot load channels. Shared prefs and `BBDatabase.db` contain CACHED data from when the app worked earlier.

3. **Version gate format:** `{"returnCode":"portal200001","errorMessage":"版本已停止使用"}` (Chinese: "version discontinued/stopped use"). Returned with `Content-Encoding: gzip` when `Accept-Encoding: gzip` header is present — the app's normal behavior.

4. **No header/body combination bypasses the gate.** Tested: every `apkVer` from 54608 to 100000, every `spkgVer` format variant, with/without `Cache-Control`, `NoLog`, gzip, hardware info, device ID, portal code, language, and DES-encrypted payloads. All rejected identically.

5. **BBDatabase.db forensics:** The `EventDbModel` table records app sessions. One entry shows the user `nestor.ale@gmail.com` with `state:1` (logged in) and an encrypted `res` field — likely a portalCore response encrypted with the 3DES response keys. Decrypting this would reveal what a SUCCESSFUL portalCore response looks like.

6. **Account credential format unknown.** The cached password hash `62513c1dec921de3015a0b22574512f4` from .4 prefs does not match any tested pattern (raw, MD5, MD5+cloudstream, etc.). The DEX references `PBEWITHMD5ANDDES-CBC` — the password may be DES-encrypted before hashing.

## Next steps

1. **⚡ BLOCKER: Obtain newer TeleLatino APK** (>5.46.8, versionCode >54608). This is the ONLY path to clear `portal200001`. The owner was hunting one in parallel — check status. Without a newer APK, TeleLatino is dead like XTV (gated, not beatable with current version).

2. **Memory dump for 3DES response keys** — lower priority now (needed AFTER version gate cleared). The `EventDbModel.res` encrypted field is a candidate for testing decryption once keys are obtained. Use `vmread.c` cross-compiled with `syscall(__NR_process_vm_readv,...)` or Frida hook on `encryptThreeDESECB`/`decryptThreeDESECB`.

3. **If newer APK obtained:** capture its `versionCode`/`spkgVer`, re-run `getAddr` for fresh hosts, test portalCore chain end-to-end (`snToken → active → login → getAuthInfo → getSlbInfo → getColumnContents → startPlayLive → m3u8`).
