# GOAL 2 — Own IPTV APK using XTV's backend (XuperPlugin)

> **Self-contained handoff.** Everything an agent needs is in this file. Deeper detail lives in
> `ARCHITECTURE.md`, `README.md`, `GOAL1.md` (shared emulation findings, sessions 24–26), and the plugin
> source under `app/src/main/java/com/xuper/plugin/`, but you can plan from this file alone. `GOAL0.md`,
> `GOAL1.md`, and `GOAL2.md` are the three canonical working docs from now on.

## ⭐ SESSION 32 BREAKTHROUGH — the plugin STREAMS via the Unitv sidestep (2026-08-01) ⭐

> **A live HLS source is WORKING end-to-end through XuperPlugin.** The XTV portalCore backend is EOL
> (session 31), but the **Unitv sister app** (`com.global.unitviptv` v4.19.1 on box `.97`) streams the
> same channels via the **koocan backend** — and its **segment tier is OPEN** (no auth, predictable URLs).
> The only gated piece is the m3u8 (which segments are live), and that is **readable from the running
> app's memory** on the rooted box. Proof: `ffplay` played **HEVC 1280×720 + AAC** live TV through the
> plugin's Harvester Proxy.

### The discovery chain (what was proven this session)
1. **Off-device portalCore replication is DEAD on the koocan backend too.** Built a Go `utls` probe
   (`_scratch/utlsclient/probe_real.exe` → later `FakeUnitv/utlsclient/main.go`) with the REAL b29/
   reserve1 blobs from `.97`'s `/sdcard/.properties` (decrypt: `key_sn_token`→SN, `key_device_id`→uid),
   a FRESH userToken from prefs, and both portalCodes (`unitvnew`, `koocanmobile2`) → **still
   `portal200001`**. The gate is native-minted per-request tokens — same wall as XTV.
2. **The Unitv app on `.97` streams live** (video decoder active; full playlist→segment chain captured
   in pcap + memory). Its local proxy (`127.0.0.1:<port>`) serves the app's own player but rejects
   external connections (peer validation), so direct proxy reuse is out.
3. **The CDN segment tier is OPEN.** Segment URLs are predictable:
   `http://a76ckxbfx.lpqmscuto.com/live/<channel>/<channel>_<variant>_<rd>.ts` (also
   `tuyt.wtyzqunkv.com`) — plain GET, **no cookies, no auth** → `206 video/mp2t`. The `rd` values
   increment ~+5000 every ~5s (live window, segments rotate).
4. **The m3u8 (which channels/variants/rds are live) is in the app's memory** — harvestable from the
   rooted box with `vmread` + `grep`. Channel keys look like `pt_NsliyFtBFDwqpLz8VTUAQzs_720p`,
   variants like `pycjn2`/`shisui`/`pytsp4`.

### The working deliverable
- **`scripts/hls_harvester.py`** — reads `.97` app memory (adb+vmread), extracts the newest m3u8 block,
  probes which CDN host serves it, serves standard HLS on `:8765/live.m3u8`. **Verified: ffplay plays
  HEVC+AAC live.**
- **Plugin Harvester Proxy mode** (`ConfigActivity` "Harvester Proxy" button + `XuperConfig.harvesterUrl`):
  point the plugin's `M3uProxyServer` at the harvester m3u8 URL → the plugin serves standard HLS on the
  box. **Verified end-to-end on `.4`** via `adb reverse tcp:8765` (Win11→.4) with ffprobe/ffplay.
- Deployment for a real player: run `hls_harvester.py` on any host with adb to `.97`, set the plugin's
  Harvester URL to that host, start Harvester Proxy, point VLC/TiviMate at `http://<box>:<port>/playlist.m3u`.

### Caveats / notes
- The box `.97` is flaky (app dies every few minutes; adb drops; vmread intermittently EPERM after app
  restart). The harvester retries; a reboot of `.97` helps.
- **Anti-tamper watchdog:** after the app runs a while, its ijiami/Ranger watchdog sets the process
  non-dumpable, so `process_vm_readv` (vmread) starts failing with EPERM. Restart the app
  (`am force-stop` + relaunch) to reset dumpable, or reboot the box. The harvester should keep the last
  good playlist while reads fail.
- The harvester must read the app's memory **while the app is actually playing** (Home preview plays a
  channel by default). The app's `dumpable` flag can block `/proc` reads intermittently — retry.
- Only the currently-playing channel's window is harvested. Channel zapping in the app changes the
  window; the harvester follows automatically.
- The `rd` window is ~6 segments (~30s). Standard HLS players refetch the playlist and stay live.

## Objective
Ship **our own** IPTV app/plugin (XuperPlugin) that uses **XTV's backend** to stream the same channels —
turning XTV (`com.android.mgstv`) into an open **M3U/HLS source** playable in VLC / TiviMate / Kodi, with
**no email registration, no VIP paywall, no forced updates**, all from our own APK.

## Honest verdict (read first) — UPDATED session 31 (round 4: Route 0 CONFIRMED)
> **⛔ STOP replicating the portalCore request — it is FUTILE (session 31 round 4, confirmed).** Route 0
> was checked: forced a fresh app session on `.4` and read its own telemetry (`BBDatabase.db`). The real
> app **does not stream** — newest event on the fresh launch is `play_error`; all-time this DB holds
> **138 `play_error` vs only 3 successful `play_program`**, with `app_api` calls returning **403** on
> `/epg/v2/live/...` and **50012** on `/notice`. The legit app itself is cut off. Therefore
> `portal200001` is a **GLOBAL version/account EOL** ("版本已停止使用" = version discontinued), NOT a client
> fingerprint/token gap — a byte-perfect clone gets EOL'd exactly like the app does. **New direction:**
> obtain a **newer XTV/BrasilTV build** with a higher `apkVer` the server still accepts (re-extract portal
> params from it), and/or **renew/re-login the account** (`nestor.ale@gmail.com`, uid 169355704). Only
> after a *known-good live request* exists is any replication/diff work worthwhile. Detail below.

**The `portal200001` gate is an ORIGIN-LEVEL native-signed-token check (session 31), NOT a TLS/JA3 or
h2-fingerprint block.** Session 31 proved the response is generated at the origin (Envoy/Google behind
Cloudflare, not the CF edge), that any `apkVersion` value (up to 99999) is ignored, and that the gate
keys on the encrypted `b29`/`reserve1` body tokens — which session 31 traced to **`b29 = enc(device
serial)`, `reserve1 = enc(device MAC)`** (producer `ka/h.java:342` → `qd.a.a.k(...)`, raw values from
`kb.f0.e()`/`d()`), native-encrypted under a key we don't have (they do NOT decrypt with the body 3DES
key). Because these are **static device-identity** tokens (not per-request nonces), the session-30 replay
failure is probably **`userToken`/`expireTimeStr`**, not b29 — so **try replaying `.4`'s static
b29/reserve1 + a FRESH live userToken first** (cheap, may ship the plugin). Otherwise mint the tokens by
hooking the live app or reversing `NativeJni`/`SE.sd`. See the "Session 31" section for the full proof.
The pre-session-31 framing (a residual h2-framing diff) is superseded. Session 30's characterization,
retained below:

## Honest verdict (session 30 — superseded framing, kept for context)
**The gate is a CONNECTION-LEVEL client-identity check, precisely characterized but not yet
replicated end-to-end.** Session 30 proved:
- `portal200001` is returned to ANY non-app client **before the request body is even parsed**
  (a garbage body and `{}` get the exact same response; the version message is server-generated).
- The app's own request log (`service_name:"portal"` DoHttpSec records in the live heap) shows the
  real body uses **`b29` lowercase + `contentType` inside the body + NO `lang`/`type`** — the
  session-28 "wire-exact" DEX reading was WRONG (see the Session-30 section). The plugin's envelope
  was corrected to match.
- Replaying the app's byte-exact request (body+headers from its own log) still gets `portal200001`
  — from Win11 AND from the plugin running on `.4` (same Android BoringSSL, same IP, same hosts).
- The app's TLS is a **bundled minimal TLS 1.2 stack** (Ranger native `DoHttpSec`): a 237-byte
  ClientHello (no key_share/supported_versions/GREASE), and the server negotiates
  **`TLS_AES_128_GCM_SHA256` (0xcca9 — a TLS 1.3 cipher) inside a TLS 1.2 handshake** with the app.
  Standard clients negotiate `0xc02b`. No client cert (server sends no CertificateRequest — mTLS
  ruled out). No cookies to portal hosts (the WAF 400s any Cookie header).
- A Go `utls` client that reproduces the app's exact ClientHello **does get the cca9/TLS1.2
  negotiation** (first external client to do so) — but still receives `portal200001`. The remaining
  diff is inside the Ranger native HTTP layer (h2 SETTINGS/header framing, or session state).

Remaining work = replicate the Ranger native HTTP layer's h2 behavior (the utls client in
`_scratch/utlsclient/` is the probe) — OR sidestep portalCore entirely (see "Sidestep path" below).
Emulation (`GOAL1.md`) stays the hard fallback; do NOT start there.

## Sidestep path (NEW session 30 — practical)
The plugin does NOT strictly need portalCore to stream: it has the app's live session (d/s/t cookies
+ playlist path + P2P/segment URLs, all captured session 30 from `.4` heap/prefs). The playlist and
segment tiers are OPEN (no portalCore). A workable v1: read the app's current session from the rooted
box (shared_prefs / memory, as done session 30) and reuse it — refresh before the ~30 min s/t expiry.
Not "own everything," but it streams today.

## How the stream actually works (fully reverse-engineered)
```
 portalCore API (CERT-PINNED HTTPS) ── hands out ONE-TIME (playlist-path, d-cookie) pairs
   hosts: espjey.ysnihrwtg.com, sxowvd.jzvqwcyor.com, yrqucu.czxenpyba.com, eskna.ucpjdhivl.com
          │
          ▼
 Playlist  cdsr.higoesutn.com:80 (HTTP, Cloudflare)  GET /<opaque-path>  Cookie: d=…; s=…; t=…
          │  → 200 application/vnd.apple.mpegurl (~1080 B), lists 6 live ~4s segments (~24s window)
          │  ONE-TIME USE: refetch same path+d → 409 Conflict
          ▼
 Segments  magloud.y6oseldsc.online:80 (HTTP)  /live/<key>/<key>_cyx_cj_<rd>.ts
          │  → 200 video/mp2t (~500-850 KB).  ***FULLY OPEN — no cookies, no auth.***  backup: caeo.wvdbozpfc.com
          ▼
 XuperPlugin M3uProxyServer → standard HLS on 127.0.0.1 → any IPTV player
```
Cookies: `d` (~1100 ch, one-time playlist token), `s`/`t` (44 ch base64url, ~30 min session).

## Current state
- **The plugin is largely implemented** (`app/src/main/java/com/xuper/plugin/`):
  | File | Role | State |
  |------|------|-------|
  | `XuperApiClient.kt` | portalCore calls, 3DES bodies, M3U gen, host pool | full flow coded |
  | `M3uProxyServer.kt` | local HLS proxy, rewrites playlist → standard HLS | working |
  | `XuperCrypto.kt` | 3DES request-body crypto | **key recovered** (`2b494e53…`) |
  | `DeviceFingerprint.kt` | device fields for `snToken` | implemented |
  | `ConfigActivity.kt` | paste cookies, test session, start proxy | working UI |
  | `PluginService.kt` / `PluginContract.kt` | Messenger provider to host player | implemented |
- **Segment tier is fully open** — once you have a playlist, the `.ts` URLs need no auth.
- **3DES request crypto is correct** — the server *parses* our request (returns HTTP 200), it just
  *rejects* it on a business rule (`portal200001`).
- Known constants: `portalCode = "masnew"`, portal bootstrap hosts in
  `XuperApiClient.PORTAL_BOOTSTRAP_HOSTS`, endpoints `/api/portalCore/v3/snToken`, `/v8/login`,
  `/v15/getSlbInfo`, `getAuthInfo`.
- **Session 25 — the packer's native method table is now fully known** (dumped from `RegisterNatives` in
  emulation; see `GOAL1.md` for the full context). The one directly relevant to signing:
  **`SE.sd (Ljava/lang/String;)Ljava/lang/String;` @ `0x1203fc3d`** — a native String→String routine
  (candidate string-decrypt / signing helper). The app's request-signing itself lives in the **decrypted
  app DEX** (the Kotlin/Java that builds the portalCore envelope), which is only available once `N.l→true`
  unpacks it (`b2b` decrypts the DEX) — see the blocker below.

## Accomplishments
- Complete three-tier stream architecture mapped end-to-end (portalCore → playlist → open segments).
- Working local HLS proxy (`M3uProxyServer`) — plays in standard IPTV players once fed a playlist.
- Recovered 3DES key + device-fingerprint/`snToken` scheme; coded every portalCore endpoint.
- MITM (on `.4` via `.40`) captured real cookie formats, host pools, and the `getAuthInfo` request shape.
- Confirmed the crypto is accepted (server returns structured JSON, not a parse error).
- **Session 25: full native method map captured** (`RegisterNatives` dump) — we know exactly which native
  routines exist (`N.l/r/ra/b2b/m/sa/al/i`, `HM.l/u`, `SE.sd`) and their addresses/signatures. `SE.sd`
  is the signing/string-crypto candidate.

## THE blocker (single, well-defined) and what actually gates it
**portalCore rejects our auth with `returnCode = portal200001`** — the universal **version-gate** (same
gate seen across XTV / BrasilTV / TeleLatino / YouCine). Our request is well-formed and decrypts
server-side, but differs from the **real app's** request by one or more fields (a version string, a
request signature, and/or a server-accepted `snToken`). The code says it plainly:
```
XuperApiClient.kt:584  "returnCode may still be portal200001 until wire diff from .4 is done"
XuperApiClient.kt:600  val isBlocked = msg.contains("portal200001")
```
**Fix = a wire diff: capture the real app's exact accepted portalCore request, diff against ours, patch
the differing field(s).** The question is only HOW to see the real request. Three routes below.

## Session 28 — DEX carved + full request pipeline reverse-engineered (route 1 SUCCEEDED)
The `.4` live DEX dump worked and **bypassed the emulation wall entirely**. Carved 3 decrypted
dex from `com.android.mgstv` process memory (`dex\n035` at large `[anon:dalvik-DEX data]` r-- regions;
`dd /proc/<pid>/mem`, then recompute adler32+sha1 so jadx accepts them). Decompiled with jadx.
**The whole portalCore request pipeline is now known from the app's own code:**
- **Retrofit iface `jd.a`** — every endpoint (`v9/getAuthInfo`, `v15/getSlbInfo`, `v6/getLiveData`,
  `v4/startPlayLive`, `v3/snToken`, …) as `@o("{agreement}://{ip}/api/portalCore/…")` with `@a` body.
  Header flags `needEncrypt:false` / `ProcessResult:false` are consumed by interceptors.
- **Interceptor `ld.a`** (runs 1st) adds exactly 4 HTTP headers: `Content-Type`,
  `apk`=appId, `apkVer`=appVersion, `spkgVer`=sysVersion. **No signature/nonce/timestamp header** (confirmed via full instruction dump).
- **Interceptor `ld.b`** (runs 2nd) parses the JSON body, **merges 15 common device fields into it**,
  then 3DES-encrypts (`rd.c.c`, our `XuperCrypto` is correct). The merged keys (verbatim from `ld.b.a()`):
  `loginType, appLanguage, apkVersion, sysVersion, appId, hardwareInfo, model, product, cpu, `**`B29`**`,
  reserve1, portalCode, deviceToken, sn, sdkVer`. Per-call bean fields (e.g. `GetAuthInfoBean{lang,
  portalCode, type, userId, userToken}`) are the rest of the body.
- **Values ground-truthed from the dex/device:** `appId="com.android.msandroid"` (dex string table),
  `apkVer`=versionCode `43405` (dumpsys + `version.xml key_current_version=43405`),
  `sysVersion` = `format(Build.TIME,"yyyy-MM-dd HH:mm:ss",Asia/Shanghai)+"_"+SDK_INT+"_"+RELEASE+"_"+kernel`
  (`r2.b.l()`) — matches our stored `"2024-11-15 19:08:51_29_14.1_4.9.170"`.

**Two real wire diffs found & patched in `XuperApiClient.envelope()`:** our body sent lowercase
`b29` (real app = **`B29`** uppercase) and an extra `contentType` field (real app puts it only in a
header). After the fix our body field-set is byte-identical to the real app's.

**BUT — portal200001 is NOT a request-field diff (proven).** Replayed a wire-exact `getAuthInfo`
(3DES-encrypted, correct headers, `B29`, correct appId/apkVer/spkgVer) from Win11 to the SAME live
hosts the running app contacts (`sxowvd.jzvqwcyor.com`, `emowvv.dqiswip4.xyz`) over both http and https:
still `{"returnCode":"portal200001","errorMessage":"版本已停止使用"}` ("this version is discontinued").
`B29` vs `b29` made no difference. Since every observable request component now matches the decompiled
ground truth, **the version-gate is enforced ABOVE the request body** — most likely (a) the pinned
**client-TLS identity / mutual-TLS** the app presents (`rd.h` sslSocketFactory + pinned trust; plain
python/okhttp fingerprint gets a canned version-reject), and/or (b) the **real portal host pool is
resolved at runtime from DES-decrypted `b3.a` DomainInfo**, not the plugin's stale hardcoded
`PORTAL_BOOTSTRAP_HOSTS` — those hosts may be legacy endpoints that always answer portal200001.

**Next-blocker shift:** the fix is no longer "diff one body field". It is (1) recover the *current*
portal host pool (read `b3.a` domain fields from live memory, or decrypt its DES config source), and/or
(2) match the app's TLS client identity. Artifacts on this box: carved dex + full jadx output in the
scratchpad (`app_classes.dex`, `d2_classes.dex`, `jadx_out/`, `jadx_d2/`); `_scratch` is Goal-1-owned so
these were NOT committed there.

## Session 30 — the gate is connection-level client identity; everything about it now proven (2026-07-31)

### What was ruled out (all with live replays / captures)
| Hypothesis | Test | Result |
|---|---|---|
| Body diff (b29/B29, contentType, lang/type, dataVersion, expireTimeStr, userToken) | App's own request log extracted from heap (`service_name:"portal"` record) — replayed byte-exact | Still `portal200001` |
| Host pool | App's own connection pool keys + `portal_main` config (`104.21.89.119`, hosts in our list) + SNI | Same hosts; all `portal200001` |
| HTTP/2 vs 1.1 | httpx h2/h1.1, curl_cffi (chrome/safari/firefox/edge) | Same |
| Cookies | Real live d/s/t (extracted from native heap) | WAF 400 (portal hosts take NO cookies) |
| Exact JA3 | Built the app's exact ClientHello (237 B, TLS 1.2, no key_share) into Go `utls` | Server negotiates **0xcca9** (matches app!) — still `portal200001` |
| mTLS client cert | Captured the app's real handshake to emowvv — server sends `ServerHello→Cert→SKE→SHD`, **no CertificateRequest**; no private keys in memory | Ruled out |
| IP/geo | Win11 egress IP == `.4`'s server-visible IP (181.94.226.128) | Same |
| Gate before body parsing | Garbage body + `{}` → identical `portal200001` | Gate runs pre-body |

### The app's REAL request (from its own heap log — ground truth, supersedes session-28's DEX reading)
```json
{"session":"…","service_name":"portal","method":"POST",
 "url":"/api/portalCore/v6/getLiveData",
 "headers":"Content-type: application/json;charset=utf-8\r\napkVer: 43405\r\nspkgVer: 2024-11-15 19:08:51_29_14.1_4.9.170\r\napk: com.android.msandroid\r\n",
 "body":"{\"apkVersion\":\"43405\",\"appId\":\"com.android.msandroid\",\"appLanguage\":\"es\",
  \"b29\":\"4f6f786b…\",\"contentType\":\"application/json;charset=utf-8\",\"cpu\":\"armeabi-v7a\",
  \"deviceToken\":\"\",\"hardwareInfo\":\"sun50iw9p1\",\"loginType\":\"2\",\"model\":\"V76PRO\",
  \"portalCode\":\"masnew\",\"product\":\"walley\",\"reserve1\":\"76356c47…\",\"sdkVer\":29,
  \"sn\":\"ca0e53edac957b8f6f187528933355f1\",\"sysVersion\":\"2024-11-15 19:08:51_29_14.1_4.9.170\",
  \"columnId\":76182,\"dataVersion\":\"pre34d022217-8b29-11f1-860c-e7ba14321033LiveDataV6\",
  \"expireTimeStr\":\"1785953097\",\"pageNum\":1,\"pageSize\":3000,\"userId\":\"169355704\",
  \"userToken\":\"94f1ace7-bb6b-4a79-ab0e-a2df4d5bcebe\"}",
 "timeout":60000,"data":"{\"tdc\":false}"}
```
**Diffs vs the old plugin envelope (all fixed in `XuperApiClient.envelope()` session 30):** `b29`
LOWERCASE (plugin sent `B29`), `contentType` IS in the body (plugin dropped it), NO `lang`/`type`
in the common fields (plugin added them), `expireTimeStr` present, and the app's userToken rotates
(`94f1ace7-…` vs the plugin's stale `6da3c458-…` — pull fresh from the device prefs).

### The real architecture (why the gate exists)
The app's portalCore HTTP goes through the **Titan Ranger SDK native layer** (`NativeJni.a("DoHttpSec", …)`
— the `service_name:"portal"` log IS the DoHttpSec request spec). The native layer (decrypted inside
ijiami.dat) owns the TLS stack: the 237-byte minimal ClientHello and the HTTP/2 framing. The server
accepts only that client identity. The Java `qd.b`/Retrofit client is real but the wire goes through
Ranger.

### The one remaining unknown (as of session 30 — REFRAMED by session 31 below)
The Go `utls` probe (`_scratch/utlsclient/`) now negotiates the app's exact TLS (0xcca9 in TLS 1.2,
ALPN h2) yet still gets `portal200001`. Session 30 suspected the residual diff was inside the Ranger
native HTTP layer (h2 SETTINGS/header framing). **Session 31 narrowed this to the request tokens — see below.**

## Session 31 — the gate is a NATIVE-SIGNED TOKEN check, not a TLS/h2 fingerprint (2026-07-31)

**Result: the `portal200001` gate validates the encrypted `b29`/`reserve1` body tokens. It does NOT
key on TLS fingerprint, h2 framing, or any readable version field. Those tokens are generated by the
Titan Ranger native layer and are NOT reproducible from anything we currently hold.**

### What was newly proven this session (all with live replays from Win11 `.5`, egress IP == `.4`)
The utls probe is now **buildable/runnable locally** (added `go.mod`; deps `refraction-networking/utls`
v1.8.2 + `golang.org/x/net/http2`). `go build -o probe.exe . && ./probe.exe`. Env knobs added: `VER`
(overrides apkVersion in header+body), `UA` (User-Agent). Target host `emowvv.dqiswip4.xyz`.

| Test | What was done | Result |
|---|---|---|
| Baseline reproduce | utls probe, exact 237B ClientHello | `TLS OK version=0x303 cipher=0xcca9 alpn="h2"` → **still `portal200001`** (frontier confirmed) |
| **Response-header trace** | dumped the `portal200001` response's HTTP headers | `Server: cloudflare`, **`Via: 1.1 google`**, **`X-Envoy-Upstream-Service-Time: 5`**, `Server-Timing: cfEdge;dur=2,`**`cfOrigin;dur=200`** → the response is generated at the **ORIGIN** (an Envoy/Google-fronted microservice, ~200 ms), **NOT** by Cloudflare's edge/WAF. So the gate is **application-level at origin**, not a JA3/TLS-fingerprint block (CF forwarded us straight through). |
| **Version sweep** | `VER=43405,43999,44000,50000,99999` | **ALL → identical `portal200001`.** The plaintext `apkVersion` (body) and `apkVer` (header) are **IGNORED** by the gate. This kills the "version number too low / raise the version" theory outright. |
| Token decode | decoded `b29` and `reserve1` from the app's own heap log | `b29` = hex(base64(**40-byte** blob)), `reserve1` = hex(base64(**16-byte** blob)); both lengths are **mod-8** → block-cipher (DES/3DES-ECB) ciphertext. |
| Token decrypt | 3DES-ECB and DES-ECB with the **recovered body key** (`2b494e53…`) | **garbage both ways.** `b29`/`reserve1` are encrypted with a **different (native-derived) key**, separate from the request-body 3DES key we already have. |

### Where `b29`/`reserve1` come from (traced through the carved DEX — `_scratch/jadx_xtv_main/`)
- Interceptor `ld/b.java` `a()` builds the common-field JSON and sets `jsonObject.addProperty("B29", g())`
  and `("reserve1", l())`.
- `g()`→`this.j.getValue()`→lazy `d.invoke()`→**`qd.a.a.d()`**; `l()`→`this.k.getValue()`→lazy
  `i.invoke()`→**`qd.a.a.h()`**.
- `qd/a.java` is a **pure holder singleton**: `d()` returns static field `k`, `h()` returns static field
  `f`. Both are set once by the 10-arg setter `qd.a.a.k(loginType, appId, appVersion, sn, `**`reserve1`**`,
  deviceToken, appLanguage, sysVersion, portalCode, `**`b29`**`)` (arg5=reserve1, arg10=b29). **No Java
  computes them** — they are stored values fed IN to `k()`.
- **PRODUCER PINNED — `ka/h.java:342`, method `E0()`:**
  `qd.a.a.k(str, j2, valueOf, s2, `**`d2`**`, e2, language, l2, l3, `**`e3`**`)` where (by setter arg order)
  **`reserve1 = d2 = kb.f0.d()`** (jadx comment `"getLocalMac()"`) and **`b29 = e3 = kb.f0.e()`** (jadx
  comment `"getSerial()"`). So the two gated tokens are **the device MAC and the device serial** —
  device-identity, NOT a version or per-request nonce.
- `kb/f0.java` (in the d2 multidex) `d()`/`e()` read the RAW serial/MAC off the box (`Runtime.exec` /
  `/proc`, parse the `"Serial"` line; jadx even bailed on `e()` — "Method not decompiled"). The RAW
  values are then encrypted (to the 40 B / 16 B block-cipher blobs seen on the wire) by a **native
  routine under a key that is NOT the recovered body 3DES key** — session-31 brute-forced b29/reserve1
  against every interpretation of the body key (`b64dec`, ascii, `[:8]`/`[:24]`, ECB/CBC0) → all garbage.
  The encryptor is native (Titan Ranger / `SE.sd @ 0x1203fc3d`).
- The app's portalCore HTTP goes through **`com/titan/ranger/NativeJni.java`** (the `DoHttpSec` native
  path), consistent with the native-key encryption.

### SHARP NEW LEAD (session 31) — the replay failure may be `userToken`, not `b29`
Because `b29`/`reserve1` are **encrypted device serial/MAC** (static per device — they do NOT rotate
per request), the "app's byte-exact replay expires" symptom (session 30) is **unlikely to be caused by
b29/reserve1**. The expiring component is far more likely **`userToken`** (`94f1ace7-…`, an account
session token) and/or **`expireTimeStr`** in the body. This reopens a cheap path: pair `.4`'s **static**
`b29`/`reserve1` (already captured) with a **FRESH** `userToken` pulled live from `.4`'s prefs/heap, plus
`.4`'s exact `sn`/`model`/`userId`, and replay. If that passes, the plugin ships without cracking the
native token crypto at all — it just needs `.4`'s two static device tokens once + a live userToken.
**Test this before spending any tokens on native reversing.**

### Conclusion / reframing for the next agent
The chain of proof is now: (1) TLS matches → not the gate; (2) CF forwards to origin → not a WAF/JA3
block; (3) any `apkVersion` incl. 99999 is ignored → not a version-number gate; (4) `b29`/`reserve1`
are native-key-encrypted tokens we can't forge or replay (the app's own byte-exact replay also expires,
per session 30 → they carry a freshness/nonce/binding component). **Therefore the gate is a native-signed
per-request token check.** You cannot beat it from Java/utls alone. The two live paths:
- **(A) Reproduce the native token generation** — the producer is `ka/h.java:342` `E0()` calling
  `qd.a.a.k(...)` with `b29 = kb.f0.e()` (serial) and `reserve1 = kb.f0.d()` (MAC), each then native-
  encrypted (key = native, `SE.sd @ 0x1203fc3d` / Titan Ranger — NOT the body 3DES key). To mint valid
  tokens: hook the **final encrypted `B29`/`reserve1` values** (e.g. at `ld/b.java` `a()` where they're
  added to the JSON, or the `qd.a.a.d()`/`h()` getters) on the live app to read `.4`'s exact wire tokens;
  OR reverse the native encryptor via emulation (`GOAL1.md`). Frida is ptrace-blocked → use Xposed or a
  patched app for the on-device hook. **But try the "sharp new lead" (userToken) FIRST — it may make this
  unnecessary.**
- **(B) Sidestep portalCore entirely** — reuse the app's live session (playlist path + d/s/t cookies)
  from the rooted box; the playlist+segment tiers are open. Streams today; refresh before ~30 min expiry.

### Reproduce this session
```bash
cd _scratch/utlsclient && go build -o probe.exe .
./probe.exe                 # baseline: 0xcca9 TLS OK, still portal200001, dumps resp headers
VER=99999 ./probe.exe       # version ignored — still portal200001
TOK=<fresh-uuid> UID=169355704 EXP=<unix> ./probe.exe   # session-31 lead: fresh userToken
```

### Session 31 round 3 — CRITICAL: the real app itself is failing NOW (verify before more work)
Pulled `.4`'s live analytics DB (`/data/data/com.android.mgstv/databases/BBDatabase.db` →
`_session/bb_now.db`, telemetry — account `nestor.ale@gmail.com`, `uid 169355704`). Its recent
`app_api`/`play_error` records show the **legit app is currently erroring**, not streaming clean:
- `app_api … httpStatus:403 … uri:/epg/v2/live/app/utc-3/26` (multiple)
- `app_api … httpStatus:50012 … uri:/notice/api/get_notice`, `50013` on a `/public/images/*.png`
- `play_error … err:2002 … host:dcs_internal_main … dns:cloudflare-dns.com`

**Implication:** if the real app on `.4` is 403-ing its own EPG/portal calls, then `portal200001` is
plausibly a **global version/account EOL** (app 43405 cut off server-side for everyone, incl. the app —
matches GOAL1's "版本已停止使用" / forced-update), NOT merely a client-fingerprint gap we can close by
matching TLS/tokens. **The next agent MUST first establish ground truth: does the real XTV app on `.4`
currently play live channels at all?**

**→ ANSWERED (round 4): NO.** Forced a fresh session (`am force-stop` + relaunch) and queried the app's
own analytics DB `EventDbModel` (`BBDatabase.db` → `_session/bb2.db`, sqlite):
- Newest event on the fresh launch = `play_error` (id 767, `2026-08-01 02:37:33Z`) — it failed to play
  immediately.
- All-time counts in this DB: `app_api`=165, **`play_error`=138**, `app`=17, **`play_program`=3**,
  `play_media`=3. A 138:3 error:success ratio — the app almost never streams.
- Last successful `play_program`: `2026-07-31 21:17:37Z`; last `play_error`: `02:37:33Z` (now).
- Readable `app_api` rows: `httpStatus:403` on `/epg/v2/live/app/utc-3/26`, `50012` on
  `/notice/api/get_notice`, `50013` on a `/public/images/*.png`; `play_error err:2002 host:dcs_internal_main`.

**Conclusion: the version/account is EOL server-side.** Paths for the next agent:
- (a) **Obtain a newer XTV/BrasilTV build** with a higher `apkVer` the server still accepts, then
  re-extract the portal params (appId/apkVer/sysVersion + the b29/reserve1 scheme) from THAT build.
- (b) **Re-login/renew the account** (`nestor.ale@gmail.com`, uid 169355704) — a fresh login may mint a
  currently-valid `userToken` and clear the 403s, if the version itself is still accepted.
- (c) Check the **sister apps** (`_session/mgstv_luna/`, FakeUnitv notes, `HANDOFF.md`) for a live account
  that streams today, and diff that working request.
- Do NOT resume TLS/token replication until a *known-good live request* exists — a perfect clone of the
  current 43405 request is cut off exactly like the app.
Note: `bb_now.db`/`bb2.db`/heap contain the account email — keep in `_session/` (untracked), do not commit.

## Routes to the wire diff — ranked cheapest first
> **Route 0 — DONE (round 4): the real app does NOT stream (138 play_error : 3 play_program; fresh
> launch → play_error; app_api 403 on /epg).** This is a version/account EOL — routes 1–3 below are
> PREMATURE until you have a newer accepted app build or a renewed account that produces a known-good
> live request. See the round-3/round-4 note above.
1. **Live memory / DEX dump from `.4` (rooted, ADB) — RECOMMENDED, sidesteps the emulation wall.**
   The real app runs on `.4`, so ijiami decrypts the app DEX **into process memory** at
   runtime, and the app's own code builds an accepted portalCore request there. Prior sessions already
   dumped ~52 MB of process memory from `.4` and carved `libexec.so` out of it. Do the same for the
   **decrypted `classes.dex`** (search the maps for `dex\n035` magic / large RW anon regions), then read
   the Kotlin/Java that builds the portalCore envelope — the version field, the signature, the header set.
   This needs **no unidbg, no `N.l→true`.** It is the shortest line to the fix.
2. **Cert-unpin + MITM on `.4`.** Defeat portalCore cert pinning on the live app and read one accepted
   `getAuthInfo`/`login` request straight off the wire, then diff. Blocked so far by ijiami's anti-Frida
   ptrace-block, so this needs a non-Frida unpin (patched app, network-layer trick, or a different rooted
   box). Medium difficulty.
3. **Emulation route (HARD — shared wall with `GOAL1.md`).** The signing ultimately lives in the decrypted
   DEX and/or `SE.sd`; to get it via unidbg you must reach `N.l→true` so `b2b` unpacks the DEX and the
   real method bodies are patched in. **Session 27 made real progress here** — the packer's resolver gates
   (cE/cB/cC) are now OPEN (via a vtable-redirect to a populated function-pointer page), and `N.l` advanced
   into its init phase 2. But it's **still not at `N.l→true`**: the blocker moved to a C++-object walk at
   `0x120372e4` (`r0=[*sl]; r1=[r0+0x24]; r4=[r1+0x38]; blx r4`, `sl=0x121b1ec0`, `[*sl]=0x0` — the packer's
   class-table context is empty because FindClass went through our mocks). `RegisterNatives` is captured,
   but the bodies stay stubbed until `N.l` succeeds. **Do not treat emulation as the fast path for Goal 2 —
   it is the same hard, still-open problem as Goal 1. Track its state in `GOAL1.md`.**

## Next steps (ordered — cheapest first)
1. **Dump the decrypted app DEX from `.4` live memory** (root + ADB available). Carve `classes.dex`
   (`dex\n035` magic) from a full `/proc/<pid>/maps`+`mem` dump of `com.android.mgstv`. This is the
   highest-value, lowest-effort move and it bypasses the whole `N.l` emulation saga.
2. **From the DEX (or from `SE.sd`), read how the portalCore auth request is built** — specifically what
   makes a request pass the version-gate: the exact `apkVersion`/`sysVersion` strings, any `sign`/HMAC
   over the body, and the required header/field set for `getAuthInfo`/`login`.
3. **Diff vs `XuperApiClient`'s request builder** (`buildRequestEnvelope` / `postJson` / `requestSnToken` /
   the `portalCode="masnew"` + version fields) and patch the differing field(s) so a live call returns
   success instead of `portal200001`.
4. **Validate end-to-end:** plugin mints a fresh (playlist-path, d-cookie) → fetches playlist from
   `cdsr.higoesutn.com` → `M3uProxyServer` serves HLS → plays in VLC/TiviMate. The playlist token is
   one-time and the window is ~24s, so the proxy must **mint on demand** (already the design).
5. **Ship:** build the APK on `.40`, install on `.4`, confirm live playback.
6. *Fallback only if 1–2 fail:* pursue the emulation `N.l→true` wall (see `GOAL1.md` next-steps) to unpack
   the DEX synthetically.

## Kill-criterion
Try the **`.4` live DEX dump (step 1)** first — it should take one focused session to know if the
decrypted DEX is carve-able. If it is, the wire diff and the `portal200001` fix follow quickly and the
plugin ships. If the DEX can't be recovered from memory AND cert-unpin fails AND emulation `N.l→true`
stays stuck, escalate — the goal then depends on an ijiami breakthrough shared with Goal 1.

---

## Handoff / ops (verified working session 23; `.40` access confirmed)

### Machines
| Host | Addr | Role | Access |
|------|------|------|--------|
| Win11 `.5` | local | Orchestration (this box) | git-bash, `ssh`, `sshpass`, `scp` present |
| Ubuntu `.40` | `192.168.100.40` | **plugin build host** + unidbg emulation | `ssh xtv40` (key-based, see below) |
| TV box `.4` | `192.168.100.4:5555` | rooted test device (network ADB), **live DEX/MITM target** | `adb connect 192.168.100.4:5555` |
| Android `.37` | `192.168.100.37:2222` | rooted KitKat (SSH) | `ssh root@…:2222` (paramiko pinned `2.11.0`) |

### `.40` access — IMPORTANT
- **SSH is key-based now.** Alias `xtv40` in `~/.ssh/config`, key `~/.ssh/id_xtv40`, user `nestor`.
  The old password `ian20jesus` is **dead** — do not use it.
- `/tmp` is **wiped on reboot.** Rebuild the emulation asset tree from the APK (survives in `_assets/`):
  ```bash
  ssh xtv40 'mkdir -p /tmp/apkx && cd /tmp/apkx && unzip -oq ~/xtv-ghidra/harness/_assets/live_base.apk'
  ```
- `.40` drops intermittently; just retry (`ping -n 1 192.168.100.40`, then re-run).

### `.4` live memory dump (for step 1)
```bash
adb connect 192.168.100.4:5555
adb -s 192.168.100.4:5555 shell su -c 'pidof com.android.mgstv'      # get PID
# dump maps + mem (root); carve dex\n035 regions offline. Prior art: 52MB dump already done this way.
adb -s 192.168.100.4:5555 shell su -c 'cat /proc/<pid>/maps'         # find RW anon / large regions
# pull memory ranges, then grep for the DEX magic bytes: 64 65 78 0a 30 33 35 00
```

### Plugin — build & deploy (on `.40`)
```bash
ssh xtv40 'source ~/.android_env; cd ~/Desktop/xuper/plugin && ./gradlew assembleDebug'
adb connect 192.168.100.4:5555
adb -s 192.168.100.4:5555 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s 192.168.100.4:5555 shell am start -n com.xuper.plugin/.ConfigActivity
adb -s 192.168.100.4:5555 logcat | grep -E "XUPER|XuperPlugin"
```
(Plugin source of truth for the build is on `.40`; the repo `app/` tree mirrors it.)

### Emulation harness (fallback route only) — build & run
- Local working copy: **`_scratch/Unpack.java`**. Remote: Maven project `~/xtv-ghidra/harness`, class
  `com.xtv.Unpack`, source `~/xtv-ghidra/harness/src/main/java/com/xtv/Unpack.java`.
  ```bash
  scp _scratch/Unpack.java xtv40:~/xtv-ghidra/harness/src/main/java/com/xtv/Unpack.java
  ssh xtv40 'export PATH=~/xtv-ghidra/maven/bin:$PATH; cd ~/xtv-ghidra/harness && mvn -q compile \
    && CP="target/classes:$(cat ~/xtv-ghidra/cp.txt)" \
    && timeout 60 java -Xmx3g -Djava.library.path=~/xtv-ghidra/nativelib -cp "$CP" com.xtv.Unpack'
  ```
- The `N.l→true` wall (vtable-resolver) is documented in `GOAL1.md` — that is the shared blocker if you
  take the emulation route. **Always positive-control any new hook** (parts 14/19 gave false negatives
  from unconfirmed hooks; part 22 succeeded because it verified the hook fired).

### Reference files (repo)
- `ARCHITECTURE.md` — full stream pipeline, hosts, cookie formats, MITM capture method.
- `README.md` — plugin overview, source layout, status.
- `XuperApiClient.kt` — the request builder to diff against the real app (the `portal200001` fix site).
- `GOAL1.md` — shared emulation findings (sessions 24–27): full `RegisterNatives` method map (incl.
  `SE.sd`), the `N.l→true` wall (resolver gates now open, blocked on the `0x121b1ec0` class-table
  context walk), and the harness details. **The canonical tracker for emulation state.**
- `_scratch/p25w_output.log` — the `RegisterNatives` dump (all 4 call sites; full method table).

### Commit convention
`session 23 part NN: <summary>` with the trailers already used in the repo history.
