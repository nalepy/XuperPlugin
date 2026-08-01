# FINDINGS — off-device koocan auth chain (Phase A, 2026-08-01)

Status: **BLOCKED at step 2 (snToken).** The koocan portalCore tier is hard-gated
off-device — the same native connection-identity wall as XTV. DCS `getAddr` works,
but nothing past it does.

## What works (proven live this session)
- `python3 backends/koocan/koocan_client.py dcs-test --sn 147107feb03d65bf30773f8b604642cb`
  -> 200, decrypted `{"returnCode":"0","dcsClientUrl":"http://mgdcs.jhwi1elw.com|http://ouwfg.hzmono.com|","errorMessage":"success!"}`
- **Live portal host: `http://mgdcs.jhwi1elw.com` (primary), `http://ouwfg.hzmono.com` (backup)**
  — from the DCS `getAddr` `dcsClientUrl` chain. `portalcore.koocan.com` is NXDOMAIN (retired).
  The DCS `dcsClientUrlAlias` (`nwqyvhtrbwof|ksthjowrhomz|`) is NXDOMAIN too and is only used
  for payment URLs, not portal resolution.
- **DCS getAddr is SN-keyed**: only the box's registered SN resolves hosts (200); any other
  SN -> 404. So a koocan account/device SN is a hard prerequisite for even host resolution.
- The portalCore **request body crypto is fully replicated**: every POST body is
  `hex(base64(3DES-ECB-PKCS5(commonParams + bean)))` with key
  `b940e017-cfea-4aa0-b69d-3a82b6428ed3` (confirmed from decompiled
  `mobile/com/requestframe/f/b.java` interceptor + `com/brasiltv/a/b/b.java`). Plain-JSON
  bodies get Jetty 400; the encrypted envelope gets 200 + structured JSON. **This was the
  missing piece vs the earlier "400 from snToken" note.**

## The gate (blocker)
- Every portalCore call — `snToken`, `v3/active`, `v3/v8 login`, `v9/getAuthInfo`,
  `v4/v5/v14/v15 getSlbInfo`, `v3/getColumnContents`, `v6/v7 getLiveData`,
  `config/get`, `device/updateOrInsert`, `getPropertiesInfo` — returns
  `{"returnCode":"portal200001","errorMessage":"版本已停止使用"}` ("version discontinued").
- Tried and rejected (all identical `portal200001`):
  - identities: fake app (`com.integration.unitviptv` 21408), real app
    (`com.global.unitviptv` 41901), swept `apkVer`/`apkVersion` 21408/41901/99999
  - bodies: encrypted, plain JSON, garbage (`ZZZ...`), `{}`, full real-app CommonParams
    with minted `b29`/`reserve1` (3DES of SN/userId via the `.properties` key), fresh
    userToken + userId from the box prefs
  - transports: http/https, hosts mgdcs/ouwfg/jzwbhc.38euvwci.com/nwnyxz.yxe84tbu.us/
    dc3.*/mobiletv.*, with and without app headers (lowercase h2-style included)
- **A garbage body gets the same `portal200001`** -> the gate fires before body parse,
  i.e. it is a connection/client-identity check at the origin — byte-for-byte the same
  behavior as XTV's Titan-Ranger gate (GOAL2 sessions 30-31).
- The current DCS protocol of the real app is the native `/v1/aws/vpkg?asfast=true`
  (ETag `253e356337327bf207d71939e3043047`, body `{"tdc":false}`) — returns 400 to us
  (native-titan framing, not replicable without libexec.so reversing).
- Cross-checked: the sibling FakeUniTV agent independently proved the same
  ("static blobs + FRESH userToken + exact TLS/h2 STILL gets portal200001 — the gate
  additionally requires the native Titan-Ranger DoHttpSec connection identity").

## Answers to the Phase A questions
| Question | Answer |
|---|---|
| Live portal host? | `mgdcs.jhwi1elw.com` / `ouwfg.hzmono.com` (from DCS getAddr) |
| Login needed? | **No login can help** — the gate fires on `snToken`, the *first* portalCore call, before any account state; every identity/version/body variant is rejected |
| Channel count? | **Unreachable off-device** — `getColumnContents`/`getLiveData` are behind the same gate |
| Play-URL format? | Unreachable off-device (sibling's captured luna m3u8 shows the segment tier is open, but the m3u8 itself is native-minted; see `luna_live_m3u8.txt`) |

## What would unblock
See `NEEDS.md`. Short version: the gate is the native Titan-Ranger connection identity
inside `libexec.so` (native minting of per-request tokens). That is a native-RE task,
not a credentials task. An account/SN alone will NOT unlock portalCore.

## Reproduce
```bash
python3 backends/koocan/koocan_client.py chain                      # stops at snToken
python3 backends/koocan/koocan_client.py dcs-test --sn <SN>          # host resolution
```
