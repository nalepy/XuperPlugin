# TeleLatino — deep-dive verdict: is the portal gate beatable off-device?

Date: 2026-08-01 · Branch: `telelatino-deepdive` · Box `.4` (V76PRO, Android 14.1) · APK 5.46.8 (54608)

**Verdict: NOT the XTV dead identity-gate — the box's own registered identity + current
version PASSES the gate today (portalCore refreshes columns and streams). But off-device
replication is NOT yet complete: every probe (real SN, real version, real host, plain +
DES-wrapped bodies) still gets `portal200001`. The blocker is the per-brand DES/3DES
request-wrap keys, which are Bangcle-obfuscated and remain unpinned after this session.**

In XTV terms: TeleLatino has **no TLS-fingerprint wall** (the server answers portal200001
at the HTTP layer over both HTTP and HTTPS; my python HTTPS completes the handshake fine).
The identity is accepted — the gate is on the **request body crypto**, not on the
connection. That makes it a **version-gate, BEATABLE**, with one open item: the keys.

---

## 1. What the box gave us (the thing the stale probes lacked)

### 1.1 Registered identity (from `/data/data/com.global.latinotv/shared_prefs/`)

| Field | Value |
|---|---|
| `KEY_SP_SN` / `SP_SN_BACKUP` | `ca0e53edac957b8f6f187528933355f1` (md5-shaped, device-bound) |
| `key_user_id` / `key_device_id_latinotv` | `945257240` |
| `key_user_identity` | `1` (restored state) / `3` (earlier read) — device/free tier |
| `key_renew_flag` | `1` |
| `portal_code` (pref) | hex `3837535330736b7541787a7453514f6e7933574543513d3d` = base64 `87SS0skuAxztSQOny3WECQ==` = 16 raw bytes `f3b492d2c92e031ced4903a7cb758409` |
| version | `5.46.8` / `54608` (also `key_current_version=54608`) |
| `spkgVer` (real, from heap) | `2024-11-15 19:08:51_29_14.1_4.9.170` |
| model | `V76PRO` (Android 14.1) |
| appId used by play URLs | `com.spanish.latinotvod` (NOT `com.global.latinotv`!) |

The SN is **device-derived and survives pm-clear/reinstall** (it reappears after a fresh
install — device identity, not app-data identity).

### 1.2 Live domain pool (recovered from a live heap dump at 11:55 + MITM run + pcap)

- **dcs pool** (getAddr response, SN-keyed, stable across every dcs host): `sxowvd.jzvqwcyor.com | yrqucu.czxenpyba.com | emowvv.dqiswip4.xyz | espjey.ysnihrwtg.com`
- **portal pool**: `emowvv.dqiswip4.xyz | espjey.ysnihrwtg.com`
- **dcs hosts the app actually dials** (fresh installs): `dcs.xifhzu.com`, `dcs.dfhlnb.com` (both return the same portal pool)
- notice: `seh.utdfbgbtg.com` · EPG: `xipre.xifhzu.com` · MarketServer: `wetc.pvqox2zhlc.com` · adserver: `noak.trerdzu.com`
- **stream CDN: `vod-asia.coolita.com`** (43.168.116.128) — confirmed in the July-29 heap play URLs and in the boot3 pcap SNI while the app streamed
- play/edge hosts seen in play URLs: `skchzp.b9xuebbkt5.com`, `vdipbp.reyildgjq.com`, `goovdme.967sd1f.homes`, `eycba.q58l6j0a.com`

### 1.3 The app's own chain WORKS (on the box, today)

- At **12:32 and 12:52 today** the app rewrote `service_time_column_new_10001/10002/10006`
  in `cache.config.xml` — i.e. portalCore column refresh returned data (not `portal200001`).
- boot3 pcap (12:30–12:33): app streaming over TLS to `vod-asia.coolita.com` (43.168.115.129),
  plus DoH (8.8.8.8/1.1.1.1/223.5.5.5/9.9.9.10), notice 200, MarketServer 200, EPG 200.
- July-29 heap contains **real play URLs** with `tag=free` tokens and `expired=` timestamps —
  the free tier streams (matches the koocan family model).

## 2. Off-device replay — what succeeds and what does not

Reproduced by `backends/telelatino/telelatino_probe.py` (all with the box's real identity).

| Step | Result |
|---|---|
| DCS getAddr (HTTP/HTTPS, `dCsPLwiy`, real SN) | **`returnCode:"0"`** — pool above. No gate. |
| portalCore `v3/snToken` / `snToken` / `v8/active` / `config/get` / `getFree` | **`portal200001` 版本已停止使用** on EVERY host (emowvv, espjey, sxowvd, dcs.xifhzu.com, dcs.dfhlnb.com), both HTTP and HTTPS |
| Body variants tried | plain `{}`, flat `commonParams+specificParams` envelope (all real field values), DES-wrapped `{"data":hex,"len":N}` with 4+ key candidates, empty body → all gated |
| Header variants | `apk=com.global.latinotv` AND `apk=com.spanish.latinotvod` → both gated |
| Notice endpoint (no crypto) | 200, accepts identity — no gate |

The version fields sent are the box's REAL ones (54608, spkgVer from the heap) — the gate
does not open for them, yet the box app passes with the same fields. Conclusion: the gate
is not on version/SN/host/TLS — it is on the **request envelope crypto** the app applies
that the probe cannot replicate without the per-brand keys.

## 3. What the ASSESSMENT got wrong

- **"5 UUID key candidates" are NOT the portalCore response keys.** All five
  (`0e5e9c33-…`, `20799a27-…`, `4c087185-…`, `b700bce0-…`, `b972E8a5A4e0e8Ff`) were located
  in the heap as **AppMetrica/Yandex analytics** identifiers (db paths, api keys, app_set_id).
- The real request/response 3DES keys live in the **Bangcle-obfuscated constant region**
  (string clusters `\AoaTAka` / `pa*Tpe*` / `&@eT0f!8` are NOT plaintext anywhere in the heap)
  — a **live memory dump while a portalCore call is in flight** is required to pin them.
- The "pool rotated to a newer generation" hypothesis is **not supported**: the box's own
  pool (emowvv/espjey) is the same the probe hits, and the box app passes on it today.

## 4. Why the box capture stalled (what's still needed)

- **MITM is blocked**: the app **validates TLS certs** (my Xuper MITM CA got
  `SSLV3_ALERT_CERTIFICATE_UNKNOWN` / ConnectionReset; Android 14 APEX store complicates CA
  injection). A cert-pinning bypass (frida) is required to see the cleartext request.
- **Frida attach works** (hooks install, class enumeration succeeds) but the app **dies at
  cold start when frida-server is present** (Bangcle anti-frida watchdog, SIG 9), and my
  pm-clear/reinstall churn broke the app's SecNeo unpack state (`NoClassDefFoundError` on
  TheRouter service classes) mid-session — the box needs a clean reboot + fresh install to
  be usable again.
- **Remaining open item**: capture the exact `CustomParamsSerializer` output + the 3DES
  request-wrap keys from a running app (frida on a healthy cold start, or a MITM with a
  frida cert-bypass). With those, the koocan client flow ports 1:1.

## 5. If we get the keys — the play-URL format is already known

From the July-29 + live heap (real captured values):

```
play URL query params (free tier, tag=free):
  app_id=com.spanish.latinotvod&tag=e9b37d&scheme=md5-01&media_code=cyx_<code>
  &expired=1785933090&token=<32-hex>
  (variant: sign_type=cs|goog&link=icdn|google&dev_id=<sn>&main_addr=<host>
   &spared_addr=<host>&auth_id=<userId>_com.spanish.latinotvod__0
   &ctrl_type=account&media_encrypted=0&client_ip=<ip>&user_id=<userId>
   &app_ver=54608&session_id=<10-char>&tag=short&token=<32-hex>)
```

- Channel codes: `cyx_*` (e.g. `cyx_50fdcc0817d61_720p`, `cyx_22443479513272736478`,
  `cyx_354592115773659000267304`); channel names live in `getColumnContents` results.
- Stream CDN: `vod-asia.coolita.com` (TLS, `http/1.1`); segments/playlist one-time tokens.
- The channel list (`all_Column_key=3816`, cached md5 `ca9a033c…`/`e81bed35…` with expiry)
  is cached on the box — parity check possible once the chain is live.

## 6. Bottom line

| Question | Answer |
|---|---|
| Is it the XTV Ranger identity-gate (TLS wall)? | **NO** — no DoHttpSec, standard Android TLS, server answers HTTP+HTTPS at the app layer |
| Does the current version + registered identity pass? | **YES** — the box app streams and refreshes columns today |
| Is off-device replication working yet? | **NO** — portalCore still `portal200001`; request-wrap keys unpinned |
| Beatable? | **BEATABLE — version-gate, not identity-gate.** One open item: the DES/3DES request keys (frida on a healthy app, or MITM + cert bypass). |
