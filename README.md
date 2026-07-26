# XuperPlugin

Lightweight Android provider plugin that turns XTV (BrazilTV, `com.android.mgstv`)
into an open M3U source playable in any IPTV player (VLC, TiviMate, Kodi,
StreamVault) — no email registration, no VIP paywall, no forced updates.

It authenticates against XTV's backend using captured session tokens, fetches the
live playlist, and serves standard HLS locally via a built-in proxy.

## Status — 2026-07-26

- ✅ Plugin builds, deploys, launches on TV box (`.4`, Android 10, Allwinner)
- ✅ MITM capture working (fixed Android `wlan0` policy routing)
- ✅ Full XTV stream architecture reverse-engineered — see [ARCHITECTURE.md](ARCHITECTURE.md)
- ✅ Proven: segments (magloud CDN) are **open**; only the playlist needs auth
- ✅ `M3uProxyServer` rewrites the real playlist format to standard HLS
- ⛔ **Blocker:** continuous live needs fresh one-time playlist tokens from the
  **cert-pinned portalCore API** — see [NEXT-BLOCKER.md](NEXT-BLOCKER.md)

## How it works

```
 cert-pinned portalCore API ──> one-time (playlist path + d cookie)
                                          │
                                          ▼
 cdsr.higoesutn.com playlist ──> lists 6 open magloud .ts segments (~24s)
                                          │
                                          ▼
 XuperPlugin M3uProxyServer ──> standard HLS on 127.0.0.1 ──> any IPTV player
```

Full detail (hosts, cookies, formats, capture method) in [ARCHITECTURE.md](ARCHITECTURE.md).

## Docs

| Doc | What |
|-----|------|
| [ARCHITECTURE.md](ARCHITECTURE.md) | Reverse-engineered 3-tier stream pipeline, cookies, formats, working MITM capture method |
| [NEXT-BLOCKER.md](NEXT-BLOCKER.md) | Concrete plan to unblock continuous playback (unpack ijiami DEX → reverse `startPlayLive`) |

## Source layout (`app/src/main/java/com/xuper/plugin/`)

| File | Role |
|------|------|
| `XuperApiClient.kt` | HTTP client, config, cookies, portalCore calls, M3U generation |
| `M3uProxyServer.kt` | Local HTTP proxy — fetches playlist, rewrites to standard HLS, serves segments |
| `ConfigActivity.kt` | Settings UI — paste cookies, test session, route check, start proxy |
| `PluginService.kt` | Messenger service exposing provider URL to the host player app |
| `XuperCrypto.kt` | 3DES request-body crypto (recovered from app: key `2b494e53...`) |
| `DeviceFingerprint.kt` | Device ID collection for snToken (kb/f0 + r2/g mirror) |
| `PluginContract.kt` | Plugin manifest + Messenger message contract |

## Build & deploy (on `.40` Ubuntu)

```bash
source ~/.android_env
cd ~/Desktop/xuper/plugin
./gradlew assembleDebug
adb -s 192.168.100.4:5555 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s 192.168.100.4:5555 shell am start -n com.xuper.plugin/.ConfigActivity
adb -s 192.168.100.4:5555 logcat | grep -E "XUPER|XuperPlugin"
```

## Environment

- Repo/build host: laptop `.40` (Ubuntu 24.04). SSH via paramiko (`~/bin/ssh40.py`).
- Test box: `192.168.100.4:5555` (rooted, network ADB).
- XTV package: `com.android.mgstv` v4.34.5 (ijiami-packed).
