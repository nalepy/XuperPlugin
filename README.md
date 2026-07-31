# XuperPlugin

Lightweight Android provider plugin that turns XTV (BrazilTV, `com.android.mgstv`)
into an open M3U source playable in any IPTV player (VLC, TiviMate, Kodi,
StreamVault) — no email registration, no VIP paywall, no forced updates.

It authenticates against XTV's backend using captured session tokens, fetches the
live playlist, and serves standard HLS locally via a built-in proxy.

## Status — 2026-07-31 (session 30)

- ✅ DEX carved + decompiled (`GOAL0`); portalCore pipeline known; app's real request captured
- ✅ Plugin envelope corrected to the app's verified body (`b29` lowercase, `contentType` in body)
- ✅ Gate characterized: `portal200001` is a **connection-level client-identity check** (Ranger-native
  TLS, 0xcca9-in-TLS1.2) — not a request diff. `utls` probe replicates the TLS; h2 layer is the
  remaining unknown. See [GOAL2.md](GOAL2.md) Session 30.
- 📄 Latest session: [SESSION-2026-07-31.md](SESSION-2026-07-31.md)

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
| [HANDOFF.md](HANDOFF.md) | **Start here** — Win11 orchestration, topology, session TL;DR |
| [SESSION-2026-07-29.md](SESSION-2026-07-29.md) | **Latest session** — lever fix, findings, next steps |
| [NEXT-BLOCKER.md](NEXT-BLOCKER.md) | Current blocker + session archive |
| [ARCHITECTURE.md](ARCHITECTURE.md) | Stream pipeline, cookies, MITM method, wire host pool |
| [SESSION-2026-07-27.md](SESSION-2026-07-27.md) | Earlier session notes |

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
