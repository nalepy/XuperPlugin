# Next Blocker — Get the portalCore `startPlayLive` request format

## Goal

Make XuperPlugin self-sustaining: fetch fresh `(playlist-path, d-cookie)` pairs on
its own so live TV plays continuously (no manual cookie paste, no ~24s cap).

Those pairs come only from the **cert-pinned portalCore API**
(`espjey.ysnihrwtg.com`, `sxowvd.jzvqwcyor.com`, `yrqucu.czxenpyba.com`, ...).
Our plugin's OkHttp has no pinning, so it CAN call those hosts — we just need the
exact request: host, path, headers, and the 3DES-encrypted body fields.

See [ARCHITECTURE.md](ARCHITECTURE.md) for the full stream pipeline.

---

# ⭐ SESSION 6 — BREAKTHROUGH (2026-07-26 ~21:30 PST): the "instability" was self-inflicted

**READ THIS FIRST. It corrects 5 sessions of wrong diagnosis.**

## Root cause of the on-device "crash loop" — FOUND
The app was NOT unstable. It was **SIGKILLing itself every ~200ms** because the
installed APK (`XTV_gadget_v11.apk`) was **re-signed with the Android debug keystore**
(`CN=Android Debug`) for frida-gadget injection. ijiami v4's native anti-tamper reads
the APK signature at load, sees it is not the vendor cert, and `kill`s its own process.

logcat proof (pristine vs tampered):
```
Zygote: Process NNNNN exited due to signal 9 (Killed)   # right after loading libexec.so
```
signal 9 = SIGKILL, no tombstone, no SIGSEGV — a deliberate self-kill, not a crash.

**Vendor cert (REQUIRED for the app to run):**
- `Owner: CN=sgm, OU=sgmtv, O=sgmtv`  SHA1 `7F:B3:0E:14:94:75:B4:11:65:81:A3:95:E9:07:65:0B:C5:70:F6:64`
- Pristine vendor-signed APKs on Win11: `XTV_4.34.5.apk`, `XTV_clean.apk`, `installed_base.apk`
  (all exactly 35,272,343 bytes). **`XTV_aligned.apk` = `CN=Fake` re-sign — DO NOT USE.**

## Why this killed EVERY on-device track
frida-gadget / BlackDex / ptrace all failed for the SAME upstream reason, not their own:
- **frida-gadget** requires modifying the APK → re-sign → SIGKILL. Dead on arrival.
- **frida-server / ptrace / BlackDex** were tested against the re-signed crash-looping
  app → process suicides in 200ms → "TimedOutError" / "0 bytes read". Not anti-frida.

## THE FIX (done)
```
adb -s 192.168.100.4:5555 uninstall com.android.mgstv
adb -s 192.168.100.4:5555 install -r XTV_4.34.5.apk    # vendor-signed
adb -s 192.168.100.4:5555 shell monkey -p com.android.mgstv -c android.intent.category.LAUNCHER 1
```
Result: **app boots, renders WelcomeActivity, process STAYS ALIVE (stable, state S).**
Genuine signature → anti-tamper passes → **DEX decrypts in memory** → the anti-tamper
singleton that dead-locked the off-device unidbg emulator (`0x120868e0`) populates
NORMALLY here. Root `/proc/PID/mem` reads now succeed (process no longer dies mid-read).

## Current on-device path (in progress)
- App stable, pid resident. Memory-scanned all readable regions of the live process.
- `startPlayLive` NOT in cleartext (either string-encrypted on-demand, or the live code
  path not yet reached in visitor mode).
- **KEYWORD HITS in dalvik heap `0x12c00000-0x14580000` (92×** `portalCore|liveAddressList|playCode|brasiliptv|snToken`**)** — the networking-layer
  strings ARE resident as live Java String objects. Next: carve that region, read the
  real `portalCore/vN/...` paths + body fields with `strings`+context.
- On-device scan pattern that WORKS: `setsid sh script` run inside an **attached** adb
  shell (Bash run_in_background). A one-shot `adb shell "nohup ... &"` gets REAPED by
  adbd when the session closes — the child dies. Keep the adb shell attached.
- WARNING: `busybox grep -a -b -o $'dex\n035'` over big regions HANGS (pathological on
  binary). Don't full-scan all regions with `-o`. Carve the KW region directly + `strings`.

## ⭐⭐ FINDINGS — API format recovered from live heap (session 6, 21:52)
Carved dalvik heap `0x12c00000-0x14580000` (26 MB, pulled to
`_session/heap1.bin`), ran `strings`+grep. The endpoint is **NOT** `startPlayLive` — the
real live-playlist call is **`getLiveData`**. All portalCore endpoints resident in cleartext:

| purpose | endpoint (path) |
|---|---|
| **LIVE PLAYLIST (the goal)** | `api/portalCore/v6/getLiveData` |
| auth / entitlement | `api/portalCore/v9/getAuthInfo` |
| server-load-balance (host discovery) | `api/portalCore/v15/getSlbInfo` |
| others | `api/portalCore/getFavorite`, `.../device/updateOrInsert`, `.../checkForceBind`, `.../bindEmailGiftDays` |

- **getLiveData request fields** (from bean setters in heap): `channelID` (a.k.a.
  `setChannels`), `columnId`, `portalCode`, `userToken` (`setUserToken`), `userId`
  (`setUserId`), `liveType`.
- **Response chain:** `core.request.result.GetLiveDataResultData` → `liveAddressList`
  → `core.request.result.LiveAddress` → `playCode` (the playable URL/code).
- Auth beans: `GetAuthInfoBean` → `GetAuthInfoResult` → `GetAuthInfoResultData`.
- **portalCore HOST is DES-encrypted**, not cleartext. Config carries `"domain|DES"`
  values (e.g. notice host = base64 `Sz0JjjU4YRgGRpH1paF7wlkgQ43Df/4y`), decrypted with
  the 3DES key in `XuperCrypto.kt` (`2b494e53…`). The serving portalCore host is chosen
  at runtime via `getSlbInfo`. Code path shows `baseUrl == null` guard.
- EPG template: `{protocol}://{ip}/epg/v2/live/app/utc{timezone}/{liveType}`.
- Plaintext ancillary (already known, NOT the seed): `notice/api/get_notice` on
  `nxiqj.jgrqyxupl.com` / `zxiws.tcgwhnvym.com`; portal assets on `sfgknh.qho3cnsyil.com`.

**What's still needed (next step):** the assembled full URL + exact JSON body only exist
in memory AFTER the app makes the call. In visitor mode parked on WelcomeActivity it
hasn't fired getLiveData yet. So: **log in (`nestor.ale@gmail.com`/`Ian20jesus`) → open a
live channel → re-carve the heap.** That materializes `https://<slb-host>/api/portalCore/
v6/getLiveData` + the encrypted request body + a fresh `userToken`, all at once. Then
decrypt the body with `XuperCrypto` to read exact field values, and replicate in
`XuperApiClient.getLiveData()` (rename from the placeholder `startPlayLive`).

## ⭐⭐⭐ LIVE CAPTURE (session 6b, 22:07) — logged in + streaming → full chain recovered
Logged in via the app UI (`nestor.ale@gmail.com`/`Ian20jesus`), opened a live channel,
re-carved the dalvik heap while streaming (`_session/heap_live.bin`, 32 MB). Everything
below is REAL captured data, not inferred:

- **Logged-in account:** `user_id=169355704` (NOTE: the visitor id `694951876` is NOT the
  account — logging in yields a DIFFERENT user_id used in the playlist call).
- **Live playlist request (cdsr host, cleartext HTTP):**
  ```
  http://cdsr.higoesutn.com/v3/youshi/?media_encrypted=0&app_id=com.android.msandroid
    &link=cf&user_id=169355704&sign_type=cfl&spared_addr=&client_ip=181.94.226.128
    &expired=1785128816&tag=free&check_play_ip=true&token=73CF14BD52BB12FAA03797653F69245D
  ```
  This is the playlist URL `getLiveData` resolves to. `token` = 32-hex (MD5). It returns
  the m3u8 whose segments live on the OPEN magloud CDN (no per-segment auth).
- **Per-channel license (from getLiveData `program` JSON, one per channel):**
  ```
  app_id=com.android.msandroid&tag=free&scheme=md5-01&media_code=<MEDIA_CODE>
    &expired=1785712153&token=<32-HEX-MD5>
  ```
  `media_code` examples: `cyx_vPeWHohcPR6vDG`, `cys_2088365543...`, `cyx-0038862747...`.
  `scheme=md5-01` ⇒ token is an MD5 over (app_id, media_code, expired, tag, + a secret).
- **Player `program` JSON shape:** `{"buss":"live","cause":"user","from":"channellist",
  "media":"<code>","medias":[{"license":"<the license string>","quality":"480p",
  "vcodec":"h265", ...}],"name":"<code>"}`.
- The full pipeline is now proven end-to-end: **getLiveData (portalCore, DES host via
  getSlbInfo) → per-channel license (md5-01) → `cdsr.higoesutn.com/v3/youshi/` playlist
  URL+token → open magloud segments.** `M3uProxyServer` already handles the last hop.

### TOKEN SIGNING — RESOLVED (session 6b hunt): server-side, NOT client-computable
Tested 17 salt-less MD5 formulas against a known playlist token
(`73cf14bd52bb12faa03797653f69245d`) — ZERO matches. The full signed request is:
```
session_id=0EbrXbdNgoP&app_ver=43405&auth_id=169355704_com.android.msandroid__0
 &dev_id=933355f1&main_addr=http://cdsr.higoesutn.com/v3/youshi/&media_encrypted=0
 &app_id=com.android.msandroid&link=cf&user_id=169355704&sign_type=cfl&spared_addr=
 &client_ip=181.94.226.128&expired=1785128816&tag=free&check_play_ip=true&token=<MD5>
```
`sign_type=cfl` signs (session_id, auth_id, main_addr, link, tag, user_id, expired,
client_ip, …) with a SERVER-HELD salt. Proof it's server-side: `session_id` is a
server-issued random (changes per request) yet is part of the signed input, and `tag=free`
vs `tag=short` / `link=cf` vs `link=akamai` each flip the token. ⇒ **the client cannot
forge these URLs.** Continuous live MUST fetch fresh signed URLs from getAuthInfo/getLiveData.
- 3 CDN hosts rotate via getSlbInfo: `cdsr.higoesutn.com`, `bmagon.sxcrwendu.com`,
  `yuwc.swzablvpm.com`. `link=cf` (Cloudflare) | `link=akamai`.
- `session_id` + `auth_id=<userId>_com.android.msandroid__0` come from **getAuthInfo**,
  then feed **getLiveData** which returns the fully-signed playlist URL.

---

# 🔨 IMPLEMENTATION PLAN — next session (pure Kotlin; spec is KNOWN, no more reversing)

All reversing is done. What follows is a build spec against `XuperApiClient.kt`. Existing
infra to reuse (already in the file): `postJson(host, logicalPath, body, encrypt=true)`
(3DES via `XuperCrypto.encryptBody`/`decryptBody`), `portalUrl()`, `requestBuilder()`
(adds `Cookie` when `isSessionReady()`), `config` (userId, portalCode, cookieD/S/T, email,
password, streamUserKey, cdnMain/Backup). Pattern to copy: `requestSnToken()`.

## Confirmed facts (from heap_live.bin, logged-in + streaming)
- **portalCore paths are PLAINTEXT** on the wire (`/api/portalCore/v9/getAuthInfo` seen
  verbatim). Only the BODY is 3DES. So `postJson` as-is should work; no path encryption.
- Endpoints (all POST, portalCore host): `v15/getSlbInfo`, `v9/getAuthInfo`,
  `v6/getLiveData`, `v3/getColumnContents`, `device/updateOrInsert`, `getFavorite`.
- **getSlbInfo REQUEST template** (this is the SLB call — note empty session_id/host):
  ```
  tag=slb&link=icdn&sign_type=cs&app_id=com.android.msandroid&app_ver=43405
   &user_id=169355704&session_id=&auth_id=169355704_com.android.msandroid__0
   &host=&client_ip=181.94.226.128&expired=1785128816&token=<MD5>
  ```
- **getLiveData RESPONSE = a LIST of pre-signed CDN addresses** (`liveAddressList`), one per
  provider, each a full query string with its own server-signed `token`:
  | link | sign_type | example main_addr |
  |------|-----------|-------------------|
  | cf | cfl | cdsr.higoesutn.com/v3/youshi/, yuwc.swzablvpm.com |
  | akamai | cfl | bmagon.sxcrwendu.com/v3/youshi/ |
  | icdn | cs | 34fhwevf.cbcf4gg3f.com, qimg.83xkvhlta.com (spared_addr on cloudfront) |
  | google | goog | mygd.ihfjsrkdw.com |
  Each carries `group=<64hex>` (entitlement), `ctrl_type=account`, `expired`, `client_ip`.
  Player picks one; we prefer whichever segments are open (magloud) — `M3uProxyServer` handles it.

## Phase 0 — DONE (22:45). Exact getAuthInfo request body captured:
```json
{"apkVersion":"43405","appId":"com.android.msandroid","appLanguage":"es",
 "b29":"<hex(base64) device blob>","contentType":"application/json;charset=utf-8",
 "cpu":"armeabi-v7a","deviceToken":"","hardwareInfo":"sun50iw9p1","loginType":"2",
 "model":"V76PRO","portalCode":"masnew","product":"walley","reserve1":"<hex blob>",
 "sdkVer":29,"sn":"ca0e53edac957b8f6f187528933355f1",
 "sysVersion":"2024-11-15 19:08:51_29_14.1_4.9.170","lang":"es","type":"1",
 "userId":"169355704","userToken":"42eebacb-1a56-46d4-8f8e-94ba32e5b99d"}
```
POST `/api/portalCore/v9/getAuthInfo`, extra headers `apkVer/spkgVer/apk`, body 3DES.
CORRECTIONS baked into `XuperConfig`: `portalCode="masnew"` (not the old hex),
`userToken="42eebacb-...b99d"` (login UUID), `userId="169355704"` (logged-in, not visitor).
b29/reserve1 are captured device blobs, reused as-is (regen is a later concern).
IMPLEMENTED in `XuperApiClient.kt` (compiles clean): `envelope()` builds this exact body;
`getSlbInfo()` (v15), `getAuthInfo()` (v9), `getLiveData()` (v6) all send it via `postJson`
(3DES). Runtime-filled config: `portalHost`, `sessionId`, `authId`.
STILL TO CONFIRM (needs a live response): getLiveData's per-channel fields (channelID/
columnId) + whether getLiveData returns a channel list or per-channel liveAddressList
(getColumnContents v3 may be the list call). Run the calls on-device to see the response.

## (reference) how Phase 0 was mined — offline, no device
The wire bodies are 3DES-encrypted, but the PLAINTEXT request JSON is in the heap before
encryption. Grep `_session/heap_live.bin` for the getAuthInfo/getLiveData request objects:
```
strings -n 6 heap_live.bin | grep -aiE 'GetAuthInfoBean|GetLiveDataBean|"channelID"|"columnId"|"portalCode"|"userToken"|"liveType"|"portalCodeList"'
```
Goal: exact field names + JSON shape for each request body. (Known so far: getLiveData
carries channelID, columnId, portalCode, userToken, liveType; getAuthInfo carries the
account/verification_token.) Also confirm which call the s/t cookies vs `verification_token`
authenticate.

## Session 6c progress (22:50) — code written, host + response shape still to validate
IMPLEMENTED + compiles clean (`compileDebugKotlin` OK): `envelope()`, `getSlbInfo()`,
`getAuthInfo()`, enriched `getLiveData()`; `XuperConfig` corrected (portalCode=masnew,
real userToken UUID, userId 169355704, device envelope fields).

Confirmed endpoint roles:
- **getColumnContents (v3)** = the CHANNEL LIST (`GetColumnContentsResultData` →
  `liveColumnList`/`childColumnList`/`channelList`/`channelListTotalSize`). ← implement next
  as the list source (current `getLiveData()` channel-list parse really belongs here).
- **getLiveData (v6)** = PER-CHANNEL playback (returns `program`+`medias[].license` for one
  channel). Takes `channelID`. Refactor `getLiveData()` → `getLiveData(channelId)` returning
  the signed playlist URL, once the request body is confirmed.

Candidate portalCore/SLB API hosts (from heap; `apiHost` default 23.94.64.155:30822 is DEAD):
`xsvs.evlslb.com`, `xsvs.vfltbr.com` (evl**slb**), `banamyi.vb1kivdlvc.com`. Try these as
the bootstrap host for getSlbInfo/getAuthInfo.

DEAD END (don't retry): `domain|DES` config blobs (`Sz0Jjj…`, etc.) do NOT decrypt with the
body 3DES key (2b494e53…) under DESede/DES × ECB/CBC × key-variants — all garbage. That blob
uses a different key/scheme. Skip it; use getSlbInfo + the candidate hosts instead.

Crypto confirmed (XuperCrypto.kt): body = `toHex(Base64(DESede/ECB/PKCS5(plain)))`,
key = Base64-decode("2b494e53756c664c2f44465245733572") = 24 bytes
`d9be3de1ee77ef9e9cebae1cd9fe38e3ae76e39ef7df9ef6`. Paths PLAINTEXT (only body encrypted).

NEXT (do first next session): validate by calling getAuthInfo against a candidate host
(deploy plugin or a standalone 3DES client). A 200 + parseable `data` proves envelope+3DES+
host all correct; then wire getColumnContents → getLiveData(channelId) → M3uProxyServer.

## Phase 1 — bootstrap host + getSlbInfo  → `XuperApiClient.getSlbInfo()`
- Bootstrap portalCore host: the DES-encrypted `domain|DES` config in `assets/` OR the known
  rotating list (`espjey.ysnihrwtg.com`, `sxowvd.jzvqwcyor.com`, `yrqucu.czxenpyba.com`, …).
  Decrypt `domain|DES` with `XuperCrypto` (3DES key `2b494e53…`) to get the first host.
- `postJson(host, "/api/portalCore/v15/getSlbInfo", body, encrypt=true)`; parse
  `GetSlbInfoBeanResultData` → the serving portalCore host(s).

## Phase 2 — getAuthInfo  → `XuperApiClient.getAuthInfo()`
- `postJson(host, "/api/portalCore/v9/getAuthInfo", body, encrypt=true)`.
- Parse `GetAuthInfoResultData` → **session_id**, **auth_id** (`<userId>_com.android.msandroid__0`),
  entitlement/token. Stash in `config`.

## Phase 3 — getLiveData  → `XuperApiClient.getLiveData(channelId)`
- Body: `{channelID, columnId, portalCode, userToken, liveType}` (confirm names in Phase 0).
- `postJson(host, "/api/portalCore/v6/getLiveData", body, encrypt=true)`.
- Parse `GetLiveDataResultData.liveAddressList[]` → pick an address (prefer one whose
  segments are open). Each entry is the full signed playlist URL (`playCode`).

## Phase 4 — wire to M3uProxyServer
- Hand the chosen signed playlist URL to `M3uProxyServer` (already fetches the m3u8 + passes
  open magloud segments). Map channels via `getColumnContents` (v3) if a channel list is needed.

## Phase 5 — refresh loop
- Tokens expire (`expired` epoch; playlist ~hours, license ~7d). Re-call getLiveData each
  cycle (or on 403) to refresh the signed URL. No client-side token computation — proven
  server-signed (`sign_type=cfl/cs/goog`, server salt).

## Verify at each phase
Our OkHttp has NO cert pinning → we CAN call the pinned portalCore hosts. Log each response
and diff against the real responses in `heap_live.bin`. If a call returns 401/409, the
`verification_token`/cookies are the auth gap → re-check Phase 0.

## Open questions (resolve during impl)
1. Exact request-body field names/order for getAuthInfo + getLiveData (Phase 0 grep).
2. Bootstrap host source: DES `domain|DES` config vs hardcoded rotating list.
3. Does getAuthInfo need the `verification_token` (config.xml), the s/t cookies, or both?
4. getSlbInfo `token` in its own request is server-signed too — can we get away WITHOUT
   getSlbInfo by using a known-good portalCore host directly? (Test: skip SLB, call
   getAuthInfo on a decrypted `domain|DES` host.)
2. If token must come from getLiveData: implement the portalCore `getLiveData` call
   (DES host from getSlbInfo, body fields channelID/columnId/portalCode/userToken/liveType,
   3DES via XuperCrypto). Refresh loop each cycle → continuous live.
3. Tokens seen expire `expired=1785712153` (licenses) / `1785128816` (playlist) — Unix
   epoch; short-lived, so the refresh call is required for continuous play.

### Session save/restore — VALIDATED
`save_session.sh` captured the logged-in `/data/data/com.android.mgstv` (4.8 MB tar,
`_session/com.android.mgstv_data.tar.gz`). Login persists in `shared_prefs/config.xml`
(`user_name`, `last_login_user_name`, `verification_token`, `user_password_new`,
`portal_code`). Confirmed the app relaunches LOGGED IN (userId 169355704, not visitor).
`restore_session.sh` reinstates it after any reinstall (auto uid-chown + restorecon).
Captured heaps: `_session/heap1.bin` (visitor), `_session/heap_live.bin` (logged-in+streaming).

## Preferred long-term capture: frida-SERVER (not gadget)
Since the killer is the SIGNATURE, attach to the UNMODIFIED vendor APK with frida-SERVER
(separate root process, no APK mod → no re-sign → no SIGKILL). Defeat ijiami runtime
anti-frida with a **de-signatured frida fork (Florida `Ylarod/Florida`, or hluda)** —
all `frida`/`gum-js-loop`/`gmain`/port-27042/D-Bus strings randomized. Cross-compile for
arm SDK29, run as root, attach to genuine app. First time both preconditions hold at once
(stable genuine app + invisible agent) — real shot where every prior session failed.

## Session save/restore (login survives reinstall)
Login lives in `/data/data/com.android.mgstv` and is wiped by uninstall. Root scripts on
Win11 at `C:/Users/Nestor/Workspace/Xuper/`:
- **`save_session.sh`** — run AFTER logging in. force-stops app, `tar czf` the data dir,
  pulls to `_session/com.android.mgstv_data.tar.gz`.
- **`restore_session.sh`** — after reinstalling the VENDOR APK, pushes+extracts the tar,
  then **chowns to the CURRENT app uid + `restorecon`** (uid changes every install — this
  step is mandatory or the app can't read its own files).

Flow: install `XTV_4.34.5.apk` → login (`nestor.ale@gmail.com` / `Ian20jesus`) →
`save_session.sh` → any future reinstall: install vendor APK → `restore_session.sh` → done.
(frida-server route never reinstalls → login never wipes; save/restore is the safety net.)

## MSYS path gotcha (bit us repeatedly this session)
Git Bash rewrites `adb` remote args like `/data/local/tmp/x` into
`C:/Program Files/Git/data/...`. Prefix the command with `MSYS_NO_PATHCONV=1` (and push
local files using a Windows `C:/...` path), or the push/shell silently targets the wrong path.

---

## Why plain jadx won't work (confirmed 2026-07-26)

The APK is **ijiami-packed** (v4). Outer `classes.dex` is a 14 KB stub with 4 classes
in package `s.h.e.l.l`:
- `A.java` — `AppComponentFactory` subclass, hooks `instantiateClassLoader`
- `S.java` — `Application` subclass, calls `DETool.loadDEso()` to decrypt `ijiami.dat`
- `N.java` — native helper, `System.load("libexec.so")`, provides `al()`, `b2b()`, `l()`, `r()`, `ra()`
- `C.java` — native callback `i(int)`

Real app class: `com.interactive.brasiliptv.app.AppWrapper` — loaded by N.al() after decryption.
Real code in `assets/ijiami.dat` (4.5 MB, encrypted) + `assets/ijm_lib/armeabi/libexec.so`,
decrypted into memory at runtime by `com.ijm.dataencryption.DETool`.

**DEX header wiping confirmed:** ijiami v4 wipes DEX magic bytes (`dex\n035`) from memory
after class loading. Scanning 512MB dalvik region space found 0 DEX headers. Both
BlackDex and DarkDex failed to recover DEX files.

**Current status (2026-07-26 ~02:00):**
All on-device DEX dumpers exhausted (BlackDex, DarkDex, memory scan). Next best path:
capture fresh s/t cookies via MITM, then probe portalCore API directly with 16 known hosts.

**UPDATE — session 2 (2026-07-26 ~09:00): blind-probe + MITM avenue CLOSED.**
- Re-analyzed retained captures: `s`/`t` stable, `d`=prefix+rotating-tail, PATH rotates
  per fetch; rotation source is NOT in any plaintext response. The 11 DarkDex
  "portalCore hosts" are ancillary plaintext services (ads/notice/EPG/update) +
  a plaintext `ws://` heartbeat (`sgyc.bfj1k2g4v.com/v1/imagine`). See ARCHITECTURE.md
  "Refined model" section. `23.94.64.155:30822` (plugin's apiHost) is DEAD (404).
- **Cold-start MITM disqualifier (decisive):** brought mitm up BEFORE launch, drove
  XTV into a live channel. Result: NO cdsr, NO seed, NO login on TCP 80/443 — only
  the `sgyc` ws (101, **0 data frames**, reconnecting ~12s) + ip-api. Player STALLED.
  Old captures only "worked" because the seed was fetched before mitm was applied.
  ⇒ **The streaming seed is genuinely cert-pinned (and/or QUIC/h3). MITM can NEVER
  capture it. Blind-probe is impossible — we have zero observations of that API.**
- **frida spawn+child-gating retried → same TimedOutError wall** (spawn coordination).
  Box has NO Magisk (system test-keys root) ⇒ LSPosed/Xposed not installable.
- **Remaining viable path = OFF-DEVICE only:** Ghidra + unidbg to emulate
  `assets/ijm_lib/armeabi/libexec.so` (435 KB, not stripped, decryptor; native fns
  registered via RegisterNatives — no clean export; 63 init_array ctors) and decrypt
  `assets/ijiami.dat` (4.56 MB) → recover DEX → JADX → read `startPlayLive`. HIGH
  effort, multi-session. All assets already extracted on `.40` at `/tmp/apkx/`.
  On-device runtime tools (frida/LLDB/QBDI/DBI) all fight ijiami anti-debug+fork.

**UPDATE — session 3 (2026-07-26 ~10:20): OFF-DEVICE EMULATION PIPELINE WORKS. One blocker left.**

Built the whole off-device rig on `.40` under `~/xtv-ghidra/`:
- Ghidra 11.3.2 headless + JDK21 (`~/xtv-ghidra/ghidra_11.3.2_PUBLIC`, `jdk21`).
- capstone/pyelftools venv (`~/xtv-ghidra/venv`) + analysis scripts (`~/xtv-ghidra/scripts/`).
- Maven 3.9.9 (`~/xtv-ghidra/maven`) + unidbg harness project (`~/xtv-ghidra/harness`).

Findings:
- **`libexec.so` is SELF-ENCRYPTING.** Static Ghidra is walled: exec segment entropy
  7.5–7.88 in quarters 1–3 (q0=5.4 loader stub); JNI_OnLoad bytes decode to garbage in
  both ARM and Thumb. The real code (JNI_OnLoad @ raw 0x3725c, RegisterNatives table,
  the .dat decrypt fn) is ciphertext at rest, unpacked by the 63 `init_array` ctors at
  runtime. Native code NEVER references `"ijiami.dat"` ⇒ the Java `DETool` reads the file
  and passes the buffer to a RegisterNatives method (name still encrypted).
- **unidbg emulation is the key and it RUNS.** Correct combo (critical): the published
  unicorn **1.0.15 native is broken** (split build, `undefined symbol: helper_div_i32`)
  on BOTH linux_64 and linux_arm64. Working matched pair = **unidbg-android 0.9.8 +
  unicorn 1.0.14** (self-contained 5 MB native). Extract `natives/linux_64/libunicorn_java.so`
  from the 1.0.14 jar onto `-Djava.library.path`. Runs on x86-64 `.40` directly.
  (unidbg 0.9.9 needs unicorn 1.0.15 API `reg_read(int)->long` → forces the broken native.)
- Under emulation the ctors run, **.text self-decrypts**, and real ARM/Thumb executes
  through the ijiami loader (verified: PC executing valid Thumb at 0x4001f804 and decrypted
  code at 0x402b6xxx; module base 0x40000000). A wide mem-scan dumped 1.8 MB of decrypted
  pages (`/tmp/apkx/mem_scan.bin` + `.idx`). Only tiny stub DEX (156/280 B) present so far —
  the **app DEX from ijiami.dat is decrypted only AFTER JNI_OnLoad**, which we can't reach yet.

**THE ONE REMAINING BLOCKER (session 3):**
An early ctor does `mmap2(start=0x4001e000, len=0x5e4f4, prot=RW, flags=MAP_FIXED|ANON)` —
ijiami munmaps its own .text mapping and re-maps decrypted code in place. unidbg **0.9.8's**
`AbstractLoader.munmap` throws `IllegalStateException: munmap aligned=0x5f000, start=0x4001e000`
because the unmap range is partial AND spans the gap between LOAD1 (ends 0x40054b1c) and
LOAD2 (0x400804f0) — a partially-unmapped range unicorn can't `mem_unmap`. This aborts the
ctor (→ `UC_ERR_FETCH_UNMAPPED`), so JNI_OnLoad returns JNI_ERR (0xffffffff) and the .dat
decrypt is never reached.

**Two fixes (either unblocks — pick one next session):**
1. **Working unicorn 1.0.15 native** → use unidbg-android **0.9.9**, whose `munmap` is the
   newer robust version (splits blocks, handles removed==null / adjacent regions). Build the
   `zhkl0228/unicorn` fork's `libunicorn_java.so` for linux_64 (cmake/gcc, self-contained).
   Cleanest: one C build unblocks the robust loader.
2. **Patch unidbg 0.9.8 from source** (needs JDK8 — `Module` ambiguity blocks JDK17 build):
   make `mmap2` MAP_FIXED clamp the `munmap` to actually-mapped pages (skip gaps) and/or
   split MemoryMap blocks like master does. Rebuild `unidbg-android`, relink harness.
   Master source is already cloned at `~/xtv-ghidra/unidbg` (its `munmap` is the robust one
   to port back).

Once past the remap: let JNI_OnLoad finish → hook RegisterNatives (unidbg logs the method
table) → call the decrypt native on the `ijiami.dat` bytes → dump the real app DEX → jadx →
grep `startPlayLive`. Fallback: after JNI_OnLoad, re-run the wide mem-scan; the app DEX
(multi-MB, `dex\n035` magic, real `file_size`) will be sitting decrypted in memory.

Harness entry: `~/xtv-ghidra/harness/src/main/java/com/xtv/Unpack.java`
Run: `cd ~/xtv-ghidra/harness && CP="target/classes:$(cat ~/xtv-ghidra/cp.txt)" && \
  java -Djava.library.path=~/xtv-ghidra/nativelib -cp "$CP" com.xtv.Unpack`

**UPDATE — session 4 (2026-07-26 ~16:00): session-3 blocker SOLVED + 2 more traps cracked. New 4th blocker.**

The documented mmap/munmap blocker is fixed and `loadLibrary()` now fully succeeds (all 63
ctors run, returns `base=0x12000000`); execution reaches `JNI_OnLoad`. Three durable fixes,
all committed to the unidbg source tree on `.40` (grep `XTV-PATCH`):

1. **munmap gap-spanning MAP_FIXED remap → FIXED.** Key correction to session-3's premise:
   - unidbg **master (`0.9.10-SNAPSHOT`, cloned at `~/xtv-ghidra/unidbg`) ships a working
     self-contained `unicorn2` backend** (`backend/unicorn2`, prebuilt `libunicorn.so`,
     `nm -D` confirms `helper_div_i32_*` defined, all 31 JNI entry points). **No unicorn
     native build was needed.** But `AndroidEmulatorBuilder` silently uses the OLD broken
     backend unless you explicitly register **`Unicorn2Factory`** — done in `Unpack.java`.
   - master's "robust" `munmap` still does NOT handle ijiami's case (it only splits when
     `start` == a tracked block base; ijiami starts mid-block and spans TWO segments across
     the LOAD1/LOAD2 gap → always hits the throwing branch). Real patch required:
     `AndroidElfLoader.clampedMunmapForFixedRemap()` — unmaps/re-tracks only the overlapping
     sub-ranges across N blocks, skipping gaps. Fires once for the remap, unions to `0x5f000`.
   - Building unidbg from source needs **JDK8** (JDK17/21 hit the `Module` ambiguity even on
     master) → `~/xtv-ghidra/jdk8u492-b09`; build with `-Dgpg.skip=true`. Installed to `~/.m2`
     as `0.9.10-SNAPSHOT`.
2. **`kill(0, SIGKILL)` anti-emu trap → FIXED.** A ctor calls `kill(0,9)` (never returns on
   real Linux); unidbg returned 0 → fell through into dead bytes → `UC_ERR_INSN_INVALID`.
   Patched `AndroidSyscallHandler.kill()` to throw when `sig==9` w/ no handler.
3. **Debugger deadlock → FIXED.** No SLF4J binding ⇒ NOP logger `isWarnEnabled()`=false ⇒
   `handleEmuException` took the interactive-debugger branch, blocking on `Scanner.nextLine()`.
   Added `slf4j-simple:2.0.16` to harness pom (INFO level routes exceptions to `log.warn`).
   Also: always launch with `</dev/null` + `timeout` so it can never block on stdin.

**THE 4th BLOCKER (ijiami anti-tamper — genuine RE, not a unidbg limit):**
With all 3 fixes, `JNI_OnLoad` runs: it's a thunk → real fn that calls `(*vm)->GetEnv(...)`,
then derefs a GOT-cached singleton (`GOT@0x12082340 → 0x120868e0 → NULL`) and calls vtable
slot `+0x40` → null-deref → returns `0xffffffff`. A ctor at `.so`-relative **`0x3a1d8`** does
an obfuscated integrity check: reads that singleton's fields, `getpid()` (raw `svc`, `r7=0x14`),
opaque-predicate integer arithmetic vs a threshold, opens `/proc/self/status`, hits the `kill`
trap. The singleton's real init appears gated behind this check passing cleanly in a genuine
(non-emulated) process. App DEX NOT decrypted yet (mem-scan `/tmp/apkx/mem_scan2.bin` shows
only the same 156B/280B stub fragments).

**RECOMMENDED NEXT STEP:** in the harness add `hook_add(UC_HOOK_MEM_WRITE)` watching address
`0x120868e0` — catch whoever is *supposed* to allocate/populate the singleton, identify the
gating condition, and stub `/proc/self/status` + getpid + the opaque predicate so the check
passes. Then JNI_OnLoad completes → RegisterNatives → call the `.dat` decrypt native → app DEX
→ jadx → `startPlayLive`. Reusable tool: `~/xtv-ghidra/scripts/scan2_disasm.py` (thumb-aware
capstone disasm vs the mem-scan). Run logs `run3.log`–`run6.log` show the full progression.

## Two tracks (either one unblocks us)

### Track A — Capture plaintext portalCore by defeating cert pinning (fastest)

If we see ONE real `startPlayLive` request/response in the clear, we have the
format immediately — no DEX archaeology.

The blocker is app-level cert pinning (mitm CA is already trusted by the box for
non-pinned traffic — that's how we decrypted playlist/segment traffic). Options:

1. **BlackDex / FART / Youpk on-device unpackers** — they hook the ART class
   loader from a *separate* helper app and dump decrypted DEX. Because they don't
   inject frida into the target, they often dodge ijiami's anti-frida. Try BlackDex
   first (self-contained APK, no PC frida-server):
   ```
   adb -s 192.168.100.4:5555 install BlackDex.apk
   # open BlackDex on box, select com.android.mgstv, dump
   # output: /sdcard/Android/data/io.va.blackdex/... *.dex
   adb pull <dumped_dex_dir> /tmp/xtv_dex/
   ```
   Then Track B step 2 (jadx the dumped dex) — this also yields the pinning +
   request-encryption code.

2. **Frida SSL-unpinning** — blocked last session by ijiami anti-frida + the
   multi-process fork. If retrying, use the spawn + child-gating recipe already
   written in [docs on remote] `XTV-CAPTURE-STATUS.md` (bypass_anti.js pre-resume,
   enable_child_gating, inject into the forked child). Add a universal
   SSL-unpinning script (hook `okhttp3.CertificatePinner.check`,
   `X509TrustManager`, and the native pinning if any) to the child.

### Track B — Static reversal of the decrypted DEX

Once you have decrypted DEX (from Track A step 1, or any ijiami unpacker):

1. **Install jadx** (not on `.40` yet):
   ```
   sudo snap install jadx        # or download release jar; java-17 is present
   ```
2. **Decompile**:
   ```
   jadx -d /tmp/xtv_src /tmp/xtv_dex/*.dex   # or the reassembled apk
   ```
3. **Find the API interface** — grep the decompiled source:
   ```
   grep -rEl "portalCore|startPlayLive|getLiveData|snToken" /tmp/xtv_src
   grep -rn "startPlayLive\|liveAddressList\|playCode" /tmp/xtv_src
   ```
   Look for the Retrofit interface (annotations `@POST("...portalCore/v4/startPlayLive")`,
   `@Body`) and its call site. Note:
   - exact **host** (base URL — likely built from an obfuscated domain list; the
     app rotates `espjey/sxowvd/yrqucu...`; check the domain provider class)
   - exact **path** (`/api/portalCore/vN/startPlayLive`)
   - **request body fields** before encryption (channelCode, portalCode, userId,
     userToken, columnId, type, and any device/sn fields)
   - which **headers/cookies** the call carries (does it send s/t? a signature?)
4. **Confirm the crypto** — the request body is 3DES-wrapped by the interceptor
   already reproduced in `XuperCrypto.kt` (key `2b494e53...`). Verify the
   startPlayLive endpoint is `needEncrypt` (default true) or annotated
   `needEncrypt:false`. Check the OkHttp interceptor chain (classes `nb.b`,
   `rd.c`, `jd/a`) for how paths get encrypted too — the wire paths are opaque,
   so there may be a **path-encryption** step, not just body encryption.
5. **Find the CertificatePinner** — grep `CertificatePinner|sha256/|checkServerTrusted`
   to confirm what we must NOT replicate (our OkHttp simply omits pinning).

## What "solved" looks like

A documented request we can reproduce in `XuperApiClient.startPlayLive()`:

```
POST https://<portalcore-host>/api/portalCore/v4/startPlayLive   (or encrypted path)
Cookie: s=<session>; t=<session>            (if required)
Body (3DES-encrypted via XuperCrypto):
  { "channelCode": "...", "portalCode": "6e54356f76774c54574b303d",
    "userId": "694951876", "type": "live", ... }
Response (3DES-decrypted):
  { "data": { "liveAddressList": [ { "playCode"/"url": "http://cdsr.higoesutn.com/<path>?..." } ] } }
```

Once we can call it: the returned URL IS the fresh playlist (path + the one-time
`d` either in the URL or a Set-Cookie). Feed it into `M3uProxyServer`, which
already fetches the playlist and passes the open magloud segments through. The
proxy loop re-calls startPlayLive each cycle → continuous live, no manual capture.

---

# SESSION 5 — 2026-07-26 (18:03–19:45 PST)

## Off-device unidbg (Paths A + C)

**Achieved:**
- JNI_OnLoad returns `JNI_VERSION_1_6` (SUCCESS) — sanity function short-circuit works
- Full RegisterNatives table captured:
  - `N`: 7 methods — `l`, `r`, `ra` (real code @ 0x381c1–0x393ed), `b2b`/`m`/`sa`/`al` (trampoline stubs @ 0x39459–0x39461)
  - `C`: 1 method, `HM`: 2, `SE`: 1 (`sd(String)String`)
- 27 harness iterations (`Unpack.java`), 9 minimal harness iterations (`WideScan.java`)
- GOT chain verified correct (PC-relative offset fix)
- `ijiami.ajm` (2.5MB, `indl01` magic) + `IJMDal.Data` (17KB) discovered — DEX split across containers
- Embedded stub DEX in libexec.so: 156B + 280B (same as before)

**Blocked:**
- Singleton at `0x120868e0` never populated — ctor integrity gate ends in infinite-loop trap
- `N.l` crashes on `[r0,#0x188]` null deref — GOT entries zero
- No DEX decrypted — ctors behind integrity gate never execute
- `.40` OOM-wedged 3 times (wide hooks)

**Key repo files:** `Unpack.java` (22KB, 27 iterations), `WideScan.java` (12KB, 9 iterations)

## On-device frida-gadget (Path B)

**Achieved:**
- BlackDex32 installs/runs but hangs at "Desempaquetando" — ijiami v4 blocks DEX extraction
- libexec.so NOT visible in live process maps — loaded anonymously
- Full RegisterNatives would be captured if frida worked
- **frida-gadget APK built and installed** — `XTV_gadget_v3.apk` works
  - `libfrida-gadget-arm.so` (17.9.1) injected into APK lib/armeabi-v7a/
  - `System.loadLibrary("frida-gadget")` added to `S.smali` `attachBaseContext`
  - Built via Python zipfile (avoids apktool signing issues)
  - Signed with Android debug keystore

**Blocked:**
- Listen mode: app becomes zombie immediately (ijiami kills before frida connects)
- Script mode (v3): app survives but script output silent
- Script reads from `/data/local/tmp/` blocked by SELinux
- `send()` output not reaching `logcat -s Frida:*`
- Need to verify gadget actually hooks (try `Process.enumerateModules()` in script, or write output to `/sdcard/`)

## New assets
- `XTV_gadget_v3.apk` (script mode, app survives) — `C:/Users/Nestor/Workspace/Xuper/`
- `XTV_gadget_v4.apk` (script mode, app data path) — zombie
- `XTV_gadget_v5.apk` (listen+resume) — zombie
- `hook_gadget.js` — native anti-kill + DEX dump hooks
- `hook_native.js` — minimal send()-based debug script
- `APK build chain`: `xtv_gadget/build/apk/classes.dex` (modified smali), `frida_gadget/libfrida-gadget-arm.so`

---

## Immediate next actions (checklist)

- [ ] **Fix gadget script execution**: use `/sdcard/` path (SELinux permissive) or embed script as APK asset
- [ ] **Verify gadget hooks**: `Process.enumerateModules()` in script, check console output
- [ ] **Switch to listen+wait mode**: pauses app until frida connects, bypasses ijiami kill timing
- [ ] **Once frida connected**: hook `DETool.loadDEso`, capture decrypted DEX buffer
- [ ] **DEX recovered** → `jadx` decompile → find `startPlayLive` endpoint + body format
- [ ] **Implement** `XuperApiClient.startPlayLive()` → wire to `M3uProxyServer` → continuous live TV

---

# SESSION 5 — PTrace memory dump (2026-07-26 20:56)

**Findings:**
- App with clean XTV_clean.apk shows UI on screen (screenshot captured) — DEX decrypts successfully
- Dalvik-main space (0x12c00000-0x32c00000): zero app class descriptors (`Lcom/interactive/...`) found in any region
- Custom ijiami ClassLoader (`N.al`) loads DEX in-memory — NOT in standard dalvik regions
- App cycles crash/restart every ~3-5 seconds — too fast for reliable /proc/PID/mem reads
- Two anonymous executable regions found: 28KB + 380KB — latter matches libexec.so size
- `/proc/PID/mem` reads return 0 bytes when process dies mid-read

**Root cause:** App is unstable on .4 after reboot — crashes and restarts rapidly. Without ptrace (PTRACE_ATTACH to freeze process), live memory reads fail because process dies during the dump.

**Frida-gadget 16.5.9 results:** Same failure as 17.9.1 — gadget connects on TCP port but dies during frida protocol handshake. Version mismatch ruled out (16→16 and 17→17 both fail identically). Root cause: Android 10 kills gadget process during protocol negotiation (likely ANR timeout or SELinux restriction on thread creation in blocked process).

**Next step:** Compile a static ARM binary that uses ptrace to:
1. `PTRACE_ATTACH` to mgstv PID
2. Read `/proc/PID/maps` to find all anonymous rw regions
3. Read those regions via `PTRACE_PEEKDATA` or `/proc/PID/mem`
4. Search for DEX magic + `startPlayLive` strings
5. `PTRACE_DETACH`

**Alternatively:** Install original working APK (pre-gadget) fresh, which might be more stable than current device state.

## Reference: what we already have

- Session cookies `s`/`t` (44-char) captured; ~30 min lifetime. Re-capture via the
  MITM method in [ARCHITECTURE.md](ARCHITECTURE.md) (the working `wlan0`-table route fix).
- Device: `KEY_SP_SN=ca0e53edac957b8f6f187528933355f1`, `userId=694951876`,
  `portalCode=6e54356f76774c54574b303d`, streamKey `cyx_93531158996778016`.
- 3DES scheme + key already in `XuperCrypto.kt`.
- Segments (magloud) are open — no auth needed once we have the playlist.
- `RegisterNatives` table fully known — decrypt function is `N.l`/`N.r`/`N.ra` at known addresses
- `.4` device: adb root, SDK 29, ARM 32-bit, test-keys image
