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

## What works NOW (proven via curl with fresh cookies)

- ✅ Playlist fetch with d/s/t → 200 + segment list
- ✅ Segment .ts fetch with NO cookies → 200 + video
- ❌ Playlist without d → 404
- ❌ Playlist refetch (consumed d) → 409

## THE REMAINING BLOCKER

Continuous live playback needs a fresh (path, d) for every ~24s window.
Those come only from the **cert-pinned portalCore API**. To make XuperPlugin
self-sustaining (no manual cookie paste), we must either:

1. **Reverse the portalCore startPlayLive request from the DEX** (jadx) and
   replicate it in XuperApiClient (our OkHttp has no cert pinning, so we CAN
   call the pinned hosts). This is the clean path.
2. **Defeat cert pinning on the box** (frida) to observe the request — blocked
   last session by ijiami anti-frida + multi-process fork.

Path 1 is preferred. Need: exact host, path, encrypted request body format,
and which session fields (userId, portalCode, s/t) the startPlayLive call needs.

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
