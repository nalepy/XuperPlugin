# End-to-end verification — channel → m3u8 → .ts (ffprobe OK)

**VERIFIED 2026-08-01 ~20:45 UTC on `.4`.** The full chain works with the
heap-derived hash — no portalCore, no 3DES key, no vendor app in the final
fetch (the app only mints the playlist through the P2P mesh).

## The verified chain

```
Cinemax HD (UI card)
  -> play-state JSON: "program":"cyx-Cinemax" "media":"cyx-C9EB0B2644979328E598EAFED311"
  -> app requests the P2P peer 108.181.133.189:33984 with an obfuscated
     routing path (/1dqiwamvp, /ihwtipnfkpgrdbrum) + a session Cookie
  -> peer relays to the CDN, returns:
     #EXTM3U ... cyx-C9EB0B2644979328E598EAFED311/cyx-C9EB0B2644979328E598EAFED311_xycjco_<rd>.ts
     (nginx/1.29.0, application/vnd.apple.mpegurl, rd rolls ~5000/5s)
  -> app fetches the .ts segments through the same peer connection
```

## Evidence

1. Live playlist captured from the peer (`_session/tl_seg.pcap`, stream
   `108.181.133.189:33984 -> 192.168.100.4:58492`): the segment lines use
   EXACTLY the heap-derived hash:
   `cyx-C9EB0B2644979328E598EAFED311/cyx-C9EB0B2644979328E598EAFED311_xycjco_53375869.ts`

2. 1.16 MB of MPEG-TS carved from the same peer stream (`_session/tl_cinemax.ts`,
   6205 TS packets, 0x47 sync verified).

3. ffprobe:
```
codec_name=hevc  codec_type=video  width=1280  height=720
codec_name=aac   codec_type=audio
format_name=mpegts  duration=5.167945  bit_rate=1805808
```

## Notes on the fetch path

- Direct `GET /live/cyx-<HASH>.m3u8` to the peer returns **410 Gone** — the
  peer requires the obfuscated mesh routing path + session Cookie the app
  negotiates at connection setup (minted per-play; ~30s playlist lifetime).
- So the standalone client needs either (a) replicate the Ranger P2P mesh
  handshake, or (b) re-serve from the app once per playlist (the harvest
  sidestep). The HASH mapping itself is now fully known and stable.
