# TeleLatino — Standalone Live Free Tier Recipe

**Status: PARTIAL — channel name→hash mapping BLOCKED by portalCore version gate.**

Date: 2026-08-01 · Session: telelatino-free-flow

---

## The live free pipeline (confirmed)

```
[app launch → auto-login (cached) → FREE tab selected]
        │
DCS getAddr   POST emowvv.dqiswip4.xyz/api/v2/dcs/getAddr  →  returnCode:0  (OPEN)
        │  DES/ECB/PKCS5("dCsPLwiy")
        │  Returns: dcsClientUrl (portal hosts)
        │
EPG           GET xipre.xifhzu.com/epg/v2/live/app/utc-3/26?md5=fc9548268cd91bd1506d8fb142cf8972
        │  Header: apk=com.spanish.latinotvod  (NOT com.global.latinotv!)
        │  Returns: 344 channels with programList — NO playlist URLs
        │
portalCore    ❌ GATED — portal200001 (version discontinued)
  getColumnContents / getLiveData → channel hash + playlist URL
        │
        ▼
P2P proxy     108.181.133.189:33984  (Ranger P2P mesh)
        │  Relays HTTP requests to CDN
        ▼
m3u8 playlist  nginx/1.29.0, Content-Type: application/vnd.apple.mpegurl
        │  Path: /live/cyx-<CHANNEL_HASH>.m3u8
        │  Segments: cyx-<HASH>/cyx-<HASH>_xycjco_<rd>.ts  (relative)
        │  rd rolls ~5000 every 5s
        ▼
TS segments    GET /live/cyx-<HASH>/cyx-<HASH>_xycjco_<rd>.ts
               (proxied through P2P, served directly by CDN)
```

## What works off-device TODAY

| Step | Status | Details |
|------|--------|---------|
| DCS getAddr | ✅ OPEN | `POST emowvv.dqiswip4.xyz/api/v2/dcs/getAddr`, DES key `dCsPLwiy`, returns `returnCode:0` |
| EPG channel list | ✅ OPEN | 344 channels, `GET xipre.xifhzu.com/epg/v2/live/app/utc-3/26?md5=fc9548268cd91bd1506d8fb142cf8972`, header `apk: com.spanish.latinotvod` |
| Notice | ✅ OPEN | `GET nxiqj.jgrqyxupl.com/notice/api/get_notice` |
| portalCore (all) | ❌ GATED | `portal200001` ("版本已停止使用" = version discontinued) — universal gate across all endpoints |

## The key insight: m3u8 path IS predictable

From `BBDatabase.db` on `.4` (live device database):

```
Channel name           → Channel hash                         → m3u8 path
cyx-Cinemax           → cyx-C9EB0B2644979328E598EAFED311    → /live/cyx-C9EB0B2644979328E598EAFED311.m3u8
cyx-LaRedHD           → cyx-1F3251F9425197449B94E006D8EB    → /live/cyx-1F3251F9425197449B94E006D8EB.m3u8
```

**The opaque path in the pcap** (`/yqixawdzjdmit`, `/xzi/cpgkpty/vmkc1hdvqslfclytfx`) is the **P2P proxy's internal routing path** — NOT the CDN path. The CDN path is the simple `/live/cyx-<HASH>.m3u8`.

## The blocker: channel name → hash mapping

The EPG returns **344 human-readable channel codes** (`cyx-ESPN2_Central`, `cyx-HBO2`, `cyx-Cinemax`, etc.). The app converts these to **hex hashes** (`cyx-C9EB0B2644979328E598EAFED311`) through portalCore's `getColumnContents` or `getLiveData` calls — which are gated with `portal200001`.

### Why portalCore is gated
- `portal200001` is a **server-side version gate** (not identity-gate)
- The portal hosts (`emowvv.dqiswip4.xyz`, `espjey.ysnihrwtg.com`) reject ALL current client versions
- NOT bypassable by header/body manipulation (tested: 10 identity combinations, all rejected)
- The app on `.4` works because it cached portalCore data from **before the gate was activated**

### How the app on `.4` works around the gate
1. The app has cached `EventDbModel` rows in `BBDatabase.db` with `state:1` (logged in)
2. The `res` field contains a **3DES-encrypted portalCore response** with full channel mappings
3. The app uses Titan Ranger P2P SDK — which has its own DNS and proxy layer
4. Live streaming works through the cached portalCore data + P2P mesh

### What's needed to complete the recipe
1. **3DES response keys** for TeleLatino (different from koocan's `b940e017-…` / `c6768bbe-…`)
2. OR a **newer APK** (>5.46.8, versionCode >54608) that clears the version gate
3. OR find the channel hash derivation algorithm (not a simple MD5/SHA of the name)

## Partial off-device recipe (what we CAN do)

### 1. getAddr — resolve portal hosts
```python
import json, urllib.request
from Crypto.Cipher import DES

SN = "ca0e53edac957b8f6f187528933355f1"
DES_KEY_DCS = b"dCsPLwiy"

body = {"sn": SN, "type": 1, "authCode": "", "authVersion": "", "reserve1": "AA:BB:CC:DD:EE:FF"}
js = json.dumps(body, separators=(",", ":"))
pad = 8 - (len(js.encode()) % 8)
data = js.encode() + bytes([pad]) * pad
enc = DES.new(DES_KEY_DCS, DES.MODE_ECB).encrypt(data).hex().upper()
payload = json.dumps({"data": enc, "len": len(js.encode())}, separators=(",", ":"))

hdrs = {"Content-Type": "application/json;charset=utf-8", "apk": "com.global.latinotv",
        "apkVer": "54608", "spkgVer": "2024-11-15 19:08:51_29_14.1_4.9.170",
        "User-Agent": "okhttp/4.12.0"}

req = urllib.request.Request("http://emowvv.dqiswip4.xyz/api/v2/dcs/getAddr",
                             data=payload.encode(), headers=hdrs, method="POST")
with urllib.request.urlopen(req, timeout=15) as resp:
    r = json.loads(resp.read())
    # Decrypt response with same DES key
    raw = DES.new(DES_KEY_DCS, DES.MODE_ECB).decrypt(bytes.fromhex(r["data"]))
    print(raw[:r["len"]].decode())
    # → returnCode:0, dcsClientUrl: emowvv.dqiswip4.xyz|espjey.ysnihrwtg.com
```

### 2. EPG — get channel list
```python
url = "http://xipre.xifhzu.com/epg/v2/live/app/utc-3/26?md5=fc9548268cd91bd1506d8fb142cf8972"
hdrs = {"apk": "com.spanish.latinotvod", "apkVer": "54608",
        "spkgVer": "2024-11-15 19:08:51_29_14.1_4.9.170",
        "User-Agent": "okhttp/4.12.0", "Accept-Encoding": "gzip"}

req = urllib.request.Request(url, headers=hdrs, method="GET")
with urllib.request.urlopen(req, timeout=15) as resp:
    channels = json.loads(resp.read())
    # → 344 channels, each with channelCode + programList
    # channelCode examples: cyx-ESPN2_Central, cyx-HBO2, cyx-Cinemax
```

### 3. Fetch m3u8 + segment (works with a KNOWN channel hash)
```python
# Known mapping from BBDatabase.db:
# cyx-Cinemax → cyx-C9EB0B2644979328E598EAFED311

import socket

channel_hash = "C9EB0B2644979328E598EAFED311"
m3u8_path = f"/live/cyx-{channel_hash}.m3u8"

# Fetch through P2P peer
s = socket.socket()
s.settimeout(10)
s.connect(("108.181.133.189", 33984))
req = f"GET {m3u8_path} HTTP/1.1\r\nHost: 108.181.133.189:33984\r\nConnection: close\r\n\r\n"
s.sendall(req.encode())
resp = b""
while True:
    data = s.recv(8192)
    if not data: break
    resp += data
s.close()

# Parse m3u8 — segments are relative paths like:
# cyx-C9EB0B2644979328E598EAFED311/cyx-C9EB0B2644979328E598EAFED311_xycjco_<rd>.ts
# Convert to absolute: /live/cyx-<HASH>/cyx-<HASH>_xycjco_<rd>.ts
```

**Note:** The m3u8 is short-lived (~30s). The peer may return 410 if the playlist has expired.
The app's Ranger P2P library refreshes it automatically.

### 4. ffprobe a segment
```bash
# Once you have a valid segment URL through the peer:
curl -s "http://108.181.133.189:33984/live/cyx-C9EB0B2644979328E598EAFED311/cyx-C9EB0B2644979328E598EAFED311_xycjco_37540113.ts" -o test.ts
ffprobe test.ts
```

## Identity & credentials

| Field | Value |
|-------|-------|
| Package | `com.global.latinotv` |
| APK version | `54608` / `5.46.8` |
| SN (device) | `ca0e53edac957b8f6f187528933355f1` |
| Device ID | `945257240` |
| User ID (cached) | `25885636` |
| Account email | `nestor.ale@gmail.com` |
| Account password | `Ian20jesus` |
| EPG package header | `com.spanish.latinotvod` (NOT com.global.latinotv!) |
| DES request key | `dCsPLwiy` |
| spkgVer | `2024-11-15 19:08:51_29_14.1_4.9.170` |
| Hardware | `sun50iw9p1` (Allwinner H616) |

## Key hosts

| Host | Role | Status |
|------|------|--------|
| `emowvv.dqiswip4.xyz` | DCS + portal | getAddr: ✅, portalCore: ❌ portal200001 |
| `espjey.ysnihrwtg.com` | DCS backup | getAddr: ✅, portalCore: ❌ portal200001 |
| `xipre.xifhzu.com` | EPG | ✅ 200, 344 channels |
| `nxiqj.jgrqyxupl.com` | Notice | ✅ status:0 |
| `108.181.133.189:33984` | P2P proxy peer | ✅ proxies m3u8 + segments |
| `172.234.252.82:9999` | P2P tracker | connected |

## 3DES response key candidates (from DEX strings)

| UUID | Tested as 3DES key | Result |
|------|-------------------|--------|
| `4c087185-05c8-4683-901d-e1e4d8707c04` | app_b64decode→24B, 3DES/ECB | ❌ bad padding |
| `b700bce0-91c7-47df-a593-747ae941bf34` | app_b64decode→24B, 3DES/ECB | ❌ bad padding |
| `0e5e9c33-f8c3-4568-86c5-2e4f57523f72` | app_b64decode→24B, 3DES/ECB | ❌ bad padding |
| `20799a27-fa80-4b36-b2db-0f8141f24180` | app_b64decode→24B, 3DES/ECB | ❌ bad padding |
| `629a824d-c717-4ba5-bc0f-3f3968554d01` | app_b64decode→24B, 3DES/ECB | ❌ bad padding |

Also tested: `b972E8a5A4e0e8Ff` (3DES, DES), `dCsPLwiy` (3DES, DES), `AoaTAka`, `pa*Tpe*`, `&@eT0f!8`,
all koocan keys, PBEWITHMD5ANDDES variants with account credentials — none decrypt.

**The TeleLatino app uses a different crypto scheme than koocan** (different 3DES key or
different algorithm entirely like AES).

## Next steps to complete the recipe

1. **Memory dump** of the running app on `.4` — hook `javax.crypto.Cipher.getInstance`
   via Frida to capture the actual key + algorithm used for portalCore response decryption.

2. **Obtain newer APK** (>5.46.8) — clears the version gate, enables direct portalCore calls.

3. **Decrypt BBDatabase `res` field** — contains the full portalCore response with channel mappings.
   Encrypted value (base64): `F4TKB+KlCp05jf15qS6BKFcPQbvU2bGYqQAHgq6DJ2u...` (72 bytes, 9 DES blocks)

## Device automation notes

The app on `.4` (192.168.100.4:5555) currently streams free channels through `LiveFreeActivity`.
The FREE tab is selected (tab index 4 in shared prefs). Login is cached (user 25885636).

To re-trigger the startup flow (dismiss paywall → FREE):
```
adb shell am force-stop com.global.latinotv
adb shell am start -n com.global.latinotv/com.interactive.brasiliptv.ui.activity.WelcomeActivity
```
The app auto-logins from cached credentials and lands on the FREE tab.
