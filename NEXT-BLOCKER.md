# Next Blocker — Get the portalCore `startPlayLive` request format

## Goal

Make XuperPlugin self-sustaining: fetch fresh `(playlist-path, d-cookie)` pairs on
its own so live TV plays continuously (no manual cookie paste, no ~24s cap).

Those pairs come only from the **cert-pinned portalCore API**
(`espjey.ysnihrwtg.com`, `sxowvd.jzvqwcyor.com`, `yrqucu.czxenpyba.com`, ...).
Our plugin's OkHttp has no pinning, so it CAN call those hosts — we just need the
exact request: host, path, headers, and the 3DES-encrypted body fields.

See [ARCHITECTURE.md](ARCHITECTURE.md) for the full stream pipeline.

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

## Immediate next actions (checklist)

- [ ] Get a decrypted DEX dump (BlackDex on box is the lowest-friction try).
- [ ] `snap install jadx` on `.40`; decompile the dump.
- [ ] Locate `startPlayLive` Retrofit def + call site; record host/path/body.
- [ ] Confirm body (and path?) encryption via `XuperCrypto` scheme.
- [ ] Implement/verify `XuperApiClient.startPlayLive()` against a pinned host
      (our OkHttp, no pinning) → expect a playlist URL back.
- [ ] Wire `M3uProxyServer` to re-call startPlayLive per playlist cycle.
- [ ] End-to-end: plugin serves continuous live to VLC/StreamVault, no manual cookies.

## Reference: what we already have

- Session cookies `s`/`t` (44-char) captured; ~30 min lifetime. Re-capture via the
  MITM method in [ARCHITECTURE.md](ARCHITECTURE.md) (the working `wlan0`-table route fix).
- Device: `KEY_SP_SN=ca0e53edac957b8f6f187528933355f1`, `userId=694951876`,
  `portalCode=6e54356f76774c54574b303d`, streamKey `cyx_93531158996778016`.
- 3DES scheme + key already in `XuperCrypto.kt`.
- Segments (magloud) are open — no auth needed once we have the playlist.
