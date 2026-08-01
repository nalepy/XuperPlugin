# FakeUniTV — handoff / starting guidance

> Self-contained starter for the **fake UniTV** project (separate from XTV/XuperPlugin). This app is
> **NOT ijiami-packed** — the whole thing decompiled to clean Java, so it's far easier than XTV. Everything
> here is read straight from the decompiled source in this folder.

## What this is
- **`UniTV_fake_2.14.8_APKPure.apk`** (30 MB) — the "fake" UniTV (APKPure build, v2.14.8). **No ijiami**
  (no `libexec.so` / `ijiami.dat`) → fully decompilable. (The *real* UniTV, `unitv-celular-uniitv.com.br.apk`,
  IS ijiami-packed and was left in `Xuper/brasiltv/` — don't confuse them.)
- **`unitv_src/sources/`** — the full jadx decompile (~10,169 files). Package family is `com.brasiltv` +
  `mobile.com.requestframe` — this app is a **reskin of the BrasilTV codebase**, but points at the
  **koocan.com** backend (a different operator from XTV's portalCore hosts).

## Backend (koocan.com family) — pulled from the source
- **Portal API:** `portalcore.koocan.com`, `portalcore-b.koocan.com` (backup)
- **Others:** `mix-api.koocan.com`, `vip.wisecloud.koocan.com`, `subtitle.koocan.com`, `cs.ecqun.com/mobile/rand`
- **Stream / CDN hosts:** `mobiletv.ogy1lfw.com`, `mobiletv.terdlfw.com`, `cool.kfsxdz.com`, `cool.nbgfbr.com`,
  `dc3.hgsesd.com`, `dc3.tesgdz.com`
- **API endpoints (two families):**
  - `/api/portalCore/*` — same design as XTV: `getAuthInfo`, `addFavorite`, `bindEmail`, `addSubscribe`,
    `checkVerifiCode`, `getAreaCode`, … (intel from XTV's portalCore work cross-applies)
  - `/api/MMS/terminal/*` — `login`, `authInfo`, `countryCode`, `exchange`, `interest`; plus `/api/MMS/register`,
    `sendCode`, `checkCode`, `bind`, `reset`

## Crypto — DES, keys in cleartext (the big win)
`unitv_src/sources/mobile/com/requestframe/cloudstream/b.java` — the request/response crypto class:
- Uses `javax.crypto` **DES** (`DESKeySpec`), **zero IV** (`{0,0,0,0,0,0,0,0}`).
- **3 hardcoded keys:** `f9281a="==RiXVKU"`, `f9282b="dCsPLwiy"`, `f9283c="D#a!t-a&"`
  (also `com/brasiltv/a/b/b.java` → `"D#a!t-a&"`).
- Methods: `a(str,key)` = encrypt → hex; `b(data,key)` = hex → decrypt; plus helpers that decrypt
  `GetAddrResult` / `SlbDesResult` beans (an **SLB / getAddr host-resolution step** — the stream-server
  pool is DES-decrypted at runtime, mirroring XTV's `getSlbInfo`).
- Chinese debug strings confirm it (`解密后的数据` = "decrypted data").

## Networking stack
Standard **retrofit2 + okhttp3** (both in `unitv_src/sources/`). The core request layer is
`mobile.com.requestframe.*` (`cloudstream/` = the crypto + response beans, `util/` = helpers). Because
it's plain retrofit, the whole auth → stream flow is traceable by reading interfaces + interceptors —
**no emulation, no unpacking.**

## Goal (same two options as XTV — pick per the deliverable)
1. **Own client** (like XuperPlugin for XTV): replicate the koocan portalCore/MMS auth + stream-URL
   resolution in our own app/plugin. Easiest path — the DES + endpoints are all here in clear.
2. **Patch the app** for free streaming: since there's no ijiami, you can edit smali and **repack normally**
   (no packer to defeat) — dramatically easier than XTV's repack wall.

## Suggested first steps
1. Regenerate/browse the decompile (already in `unitv_src/sources/`). Grep for `portalCore` and `MMS/terminal`
   retrofit interfaces to find the request/response beans.
2. Trace **`getAuthInfo` / MMS `login`** → what fields it signs, what token it returns, whether it's
   DES-wrapped via `cloudstream/b.a()`.
3. Trace the **getAddr / SLB** flow (`GetAddrResult`, `SlbDesResult` in `cloudstream/`) → that DES-decrypts
   the current **stream host pool** and the play-URL builder → the actual `.m3u8`/`.ts` endpoints on the
   `mobiletv.*` / `cool.*` CDNs.
4. Reproduce one authenticated request off-device (python/okhttp) to a live koocan host and confirm it's
   accepted (watch for a version-gate like XTV's `portal200001` — if it appears, same playbook as GOAL2).

## Files in this folder
- `UniTV_fake_2.14.8_APKPure.apk` — the fake APK (non-ijiami)
- `unitv_src/sources/` — full decompiled Java (entry crypto: `mobile/com/requestframe/cloudstream/b.java`)
- `HANDOFF.md` — this file

## Cross-reference (XTV project, sibling)
The XTV work in `../Xuper/XuperPlugin/` (`GOAL0/1/2.md`, `ARCHITECTURE.md`) documents the **same
portalCore stream architecture** (portalCore → playlist → open segments) and the `getSlbInfo`
host-resolution pattern — directly relevant here since UniTV uses the same API design.
