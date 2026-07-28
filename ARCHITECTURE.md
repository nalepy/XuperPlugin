# XTV Stream Architecture (reverse-engineered 2026-07-26)

Complete map of how XTV (`com.android.mgstv` v4.34.5) authenticates and streams.
Captured via transparent MITM on TV box `.4` routed through laptop `.40`.

## Three-tier stream pipeline

```
  ┌─ portalCore API (CERT-PINNED, HTTPS) ──────────────────┐
  │  Hosts: espjey.ysnihrwtg.com, sxowvd.jzvqwcyor.com,     │
  │         yrqucu.czxenpyba.com, eskna.ucpjdhivl.com       │
  │  Hands out ONE-TIME (playlist-path, d-cookie) pairs.    │
  │  Could NOT decrypt — app pins its cert.                 │
  └────────────────────────┬───────────────────────────────┘
                           │ each grants one playlist fetch
                           ▼
  ┌─ Playlist (cdsr.higoesutn.com:80, HTTP, Cloudflare) ───┐
  │  GET /<opaque-path>  Cookie: d=<1100ch>; s=<44>; t=<44> │
  │  → 200 application/vnd.apple.mpegurl (~1080 bytes)      │
  │  ONE-TIME USE: refetch same path+d → 409 Conflict.      │
  │  Lists 6 live segments (~24s window).                   │
  └────────────────────────┬───────────────────────────────┘
                           │ segment URLs (full, open)
                           ▼
  ┌─ Segments (magloud.y6oseldsc.online:80, HTTP) ─────────┐
  │  GET /live/<key>/<key>_cyx_cj_<rd>.ts                   │
  │  → 200 video/mp2t (~500-850 KB each)                    │
  │  FULLY OPEN — no cookies, no auth required.             │
  │  No directory index (can't enumerate without playlist). │
  │  Backup CDN: caeo.wvdbozpfc.com                         │
  └─────────────────────────────────────────────────────────┘
```

## Playlist body format

```
#EXTM3U
#EXT-X-VERSION:3
#EXT-X-ALLOW-CACHE:YES
#EXT-X-MEDIA-SEQUENCE:2621
#EXT-X-TARGETDURATION:5
#EXT-SEGMENT:0-564187/rd=5199132911        <- byte-range + rd id (non-standard)
#EXTINF:4.004, no desc
http://magloud.y6oseldsc.online/live/cyx_93531158996778016/cyx_93531158996778016_cyx_cj_5199132911.ts
... 6 segments total ...
```

- `rd=` increments by segment duration in ms (4004 ≈ 4.004s). Roughly tracks wall time.
- `.ts` filename embeds the `rd`. Segments are open, so filenames are the only thing needed.
- `M3uProxyServer.rewriteM3u` now drops `#EXT-SEGMENT` lines and passes the direct
  magloud URLs through as standard HLS (segments need no proxy — they are open).

## Cookies

| Cookie | Length | Role | Lifetime |
|--------|--------|------|----------|
| `d` | ~1100 ch | one-time playlist token | single fetch (409 after) |
| `s` | 44 ch base64url | session | ~30 min |
| `t` | 44 ch base64url | session | ~30 min |

`d` shares a ~1000-char common prefix across fetches; only the ~200-char tail changes.
Next `d` is NOT in Set-Cookie or playlist body — it comes from the pinned portalCore API.

## Device identity (from /sdcard/.properties on box, 3DES-encrypted)

- `KEY_SP_SN` = `ca0e53edac957b8f6f187528933355f1` (device SN — works as routing `d` for the OLD 23.94.64.155 API, gives HTTP 400 not 404)
- `key_device_id_magis` = `694951876` (userId)

## Other endpoints seen

- EPG: `vgwbm.uwfyobivh.com/epg/v2/live/app/utc-3/26?md5=...` → 403 (needs auth)
- Notice: `nxiqj.jgrqyxupl.com/notice/api/get_notice?pkg=com.android.mgstv&v=434` → 200 JSON
- Update: `iyut.xgw3sdzoac.com/MarketServer/update?action=checkUpdate`
- GeoIP: `ip-api.com/json/`

## Wire capture update (2026-07-27, tcpdump on `.4`)

### Session 7 — streaming (playback)

While **live channels were playing**, root `tcpdump` (port 80/443) showed **TLS SNI**
to `sfgknh.qho3cnsyil.com` and payload strings for `rokbd.ysrkwctjg.com`,
`vgwbm.uwfyobivh.com`. These hosts were **not** in the old heap-only portalCore list
(`espjey.ysnihrwtg.com`, …).

### Session 10 — cold start + port 80 (bootstrap hunt)

| Capture | Filter | Packets | Takeaway |
|---------|--------|---------|----------|
| `portal_cold.pcap` | tcp 80 or 443, 50s after force-stop + launch | 846 | New SNI `34fhwevf.cbcf4gg3f.com`; pool SNIs `sxowvd`, `emowvv`; HTTP Host on EPG/ad hosts |
| `portal_switch.pcap` | tcp 443, channel keys only | 98 | No portal SNIs (analytics only) — **cold start required** |
| `sgyc_port80.pcap` | tcp 80, 45s cold start | 141 | `sgyc` → WebSocket `/v1/imagine`; `ycout` → WS `/v1/ws/...` — **not portalCore** |

Still **no** cleartext `POST …/api/portalCore/` in these pcaps → app likely uses **HTTPS**
with **opaque encrypted path** (OkHttp interceptors) on the real DES-resolved host.

| Observation | Detail |
|-------------|--------|
| Artifacts | `_session/portal_cold.pcap`, `portal_switch.pcap`, `sgyc_port80.pcap` |
| Not in pcap | `magloud`, `cdsr`, `m3u8`, `portalCore` cleartext |
| Plugin probe | 19 bootstrap hosts → **`portal200001`** or **403/404**; log tags **`[SYN]`** when only stream-key fallback channels |
| `s`/`t` on device | not in `shared_prefs`; `cache.config.xml` has `key_user_id`, `KEY_SP_SN` (= `d` prefix) |

Implication: the **portal API host pool in production shifts** (CF front domains). Wire
discovery must separate **ancillary** hosts (WS, EPG, ads) from **portalCore**. Top TLS
candidate from cold start: `34fhwevf.cbcf4gg3f.com` (404 on logical API path from plugin).
Full session notes: [SESSION-2026-07-27.md](SESSION-2026-07-27.md).

Analysis tools: `scripts/deep_analyze_pcap.py`; capture: `_session/capture_portal.sh`,
`_session/capture_sgyc_port80.sh`.

## What works NOW (proven via curl with fresh cookies)

- ✅ Playlist fetch with d/s/t → 200 + segment list
- ✅ Segment .ts fetch with NO cookies → 200 + video
- ❌ Playlist without d → 404
- ❌ Playlist refetch (consumed d) → 409

## Refined model — flow re-analysis (2026-07-26 session 2)

Re-parsed the retained captures (`/tmp/xtvtr.flow` 2.3 MB, `/tmp/xtvcap.flow` 19 KB
on `.40`) with proper mitmproxy readers (`scratchpad/parse_*.py`). Corrections to
the model above:

- **`s`/`t` are STABLE session cookies** (constant across all playlist fetches);
  **`d` is a ~1200-char token with a stable ~1000-char prefix + a ROTATING
  ~200-char tail**; and **the playlist PATH also rotates every fetch**. So each
  ~4 s cycle needs a fresh `(path, d-tail)` pair — `d` is NOT purely one-time,
  its prefix persists for the session.
- **The next `(path, d-tail)` is NOT in any plaintext response** — cdsr playlist
  responses carry no Set-Cookie and only segment lines. The rotation source is
  the cert-pinned streaming API (unobserved).
- **The DarkDex "portalCore hosts" are mostly ancillary plaintext services**, not
  the streaming API:
  - `yvhcn.hxjebagrv.com` → ad server `POST /api/adserver/v3/get_content`
    (plain JSON: `ad_version, apk_versioncode, channel, os_version, pkg,
    platform, sn, user_id, ...`)
  - `zxiws.tcgwhnvym.com` / `nxiqj.jgrqyxupl.com` → `GET /notice/api/get_notice`
  - `vgwbm.uwfyobivh.com` / `rokbd.ysrkwctjg.com` → `GET /epg/v2/live/app/...`
  - `iyut.xgw3sdzoac.com` → `GET /MarketServer/update`
  - `sgyc.bfj1k2g4v.com` → **plaintext `ws://` websocket** `/v1/imagine`
    (HTTP 101 upgrade, ~28 s heartbeat; no data frames persisted in capture)
  - `23.94.64.155:30822` (the value in `XuperConfig.apiHost`) → **dead, 404**
- **The real streaming-seed API (espjey.ysnihrwtg.com family) never appears in
  any capture** — genuinely cert-pinned HTTPS, unobservable via MITM. We have no
  path / body / confirmed host / port for it.

Implication: a blind probe can't target an API we've never observed. The only
plaintext lead left is a **cold-start MITM** (proxy up before launch) to see if
the seed call at channel-open rides 80/443 unpinned. If it too is pinned, the
seed can only come from in-process observation (frida SSL-unpin / DEX), which is
blocked on this box (no Magisk → no LSPosed; ijiami anti-frida blocks frida spawn).

## THE REMAINING BLOCKER (updated 2026-07-27)

Continuous live playback needs fresh **server-signed** playlist URLs from portalCore
(`getLiveData` chain — see [NEXT-BLOCKER.md](NEXT-BLOCKER.md)).

**Progress:** Kotlin client sends correct 3DES + envelope; Win11 reaches portal hosts.
**Wire capture** identified **live** hostnames (`sfgknh`, `rokbd`, `vgwbm`) vs **old**
heap pool (`espjey…` → `portal200001`).

**Still blocked:**
1. Plugin probes get **403** (live hosts) or **portal200001** / **400** (old pool + stale `s`/`t`).
2. Fresh **`s`/`t` cookies** not in device prefs — MITM or Set-Cookie capture required.
3. Kotlin: `getColumnContents`, per-channel `getLiveData`, proxy refresh loop not wired.

Preferred path: refresh cookies → successful `getAuthInfo` → implement list/play in
`XuperApiClient` (OkHttp has no pinning). Fallback: Frida on **vendor** APK for native headers.

## MITM capture method (WORKING — 2026-07-26)

Key fix vs. earlier failures: **Android per-interface policy routing**. Setting
the main-table default route is not enough — must set the `wlan0` table:

```bash
# on .40, regular transparent proxy
sudo mitmdump --mode transparent --ssl-insecure -p 8080 -w /tmp/cap.flow &
sudo sysctl -w net.ipv4.ip_forward=1
sudo iptables -t nat -A POSTROUTING -o wlp7s0 -j MASQUERADE
sudo iptables -t nat -A PREROUTING -s 192.168.100.4 -p tcp --dport 80  -j REDIRECT --to-port 8080
sudo iptables -t nat -A PREROUTING -s 192.168.100.4 -p tcp --dport 443 -j REDIRECT --to-port 8080
# box-source-only redirect => laptop internet stays safe (no OUTPUT rule)

# on box (THE critical line — wlan0 policy table, not main):
adb shell su -c "ip route replace default via 192.168.100.40 dev wlan0 table wlan0"

# CA already installed: /data/misc/user/0/cacerts-added/c8750f0d.0

# cold-start app to force fresh auth+playback:
adb shell am force-stop com.android.mgstv
adb shell monkey -p com.android.mgstv -c android.intent.category.LAUNCHER 1

# parse flow with mitmproxy reader (strings-grep misses structure):
python3 -c 'from mitmproxy import io,http; ...'   # see parse scripts
```

Cleanup: `adb shell su -c "ip route replace default via 192.168.100.1 dev wlan0 table wlan0"` ;
`sudo iptables -t nat -F` ; `sudo killall mitmdump`.
