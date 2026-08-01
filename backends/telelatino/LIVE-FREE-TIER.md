# TeleLatino live FREE tier — mapped from live pcap (2026-08-01)
Captured on `.4` while the app (5.46.8) streamed free channels (after dismissing the paywall → free option).

## The stream pipeline (all OPEN, no portalCore)
```
[paywall dismiss → select FREE]  ← the app's own UI step (agent must automate this)
        │
EPG channel list  (GET xipre.xifhzu.com/epg/v2/live/app/... → 200, open)
        │
Playlist/m3u8     opaque path GET (e.g. /yqixawdzjdmit) → 200 nginx, 1692B m3u8
        │           #EXT-SEGMENT:0-223155,.../rd=37505079
        │           cyx-<CH>/cyx-<CH>_xycjco_<rd>.ts
        ▼
Segments          GET /live/cyx-C9EB0B2644979328E598EAFED311/cyx-..._xycjco_37540113.ts
                   → open (predictable pattern, same family as luna/unitv)
        + P2P mesh: <hash>_file.peer / _proxy.peer / /v1/segment / /v1/status (keyed by SN ca0e53ed...)
```

## KEY FACTS
- **Version-gate (portal200001) does NOT block live free streaming** — the app streams via the open
  EPG + playlist + segment tier. portalCore (VOD/sub/login) stays gated but isn't needed for live.
- Live channel in capture: `cyx-C9EB0B2644979328E598EAFED311`; variant `xycjco`; segment rd rolls ~5s.
- Playlist served by nginx/1.29.0, 1692B (identical format to luna m3u8). Opaque path (e.g.
  `/yqixawdzjdmit`) is the per-channel playlist route.
- Peers: IPs `108.181.133.189:33984`, LAN `192.168.100.99:8001`; peer names `<md5>_file.peer`,
  `ca0e53edac957b8f6f187528933355f1_proxy.peer` (SN-keyed).
- pcap evidence: `_session/tl_live.pcap` (6.4MB, 16:20 UTC).

## THE MISSING LINK (next step)
How does the app obtain the **opaque playlist path** (`/yqixawdzjdmit`)? If it's derivable from the
channel code / EPG, a standalone client can mint it. If it comes from a (free) auth/session response,
that call must be replicated. Trace it: dismiss paywall → free → observe the request that returns the
opaque path (heap carve or second pcap filtered to the app, looking at the response to EPG/channel calls).
