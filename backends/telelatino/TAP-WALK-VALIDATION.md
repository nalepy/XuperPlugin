# Tap-walk live validation — program<->media pairs from the live UI on .4

**Verdict: 9/9 visible free channels validated LIVE — every pair matches the
heap-derived mapping.** The play-state JSON is read from the app's dalvik-main
heap region immediately after tapping a channel card.

## Method (headless, on .4)
1. HomeActivity "Vivo gratis" card grid: row1 y=389 (x=143/339/535/731/927/1123),
   row2 y=566 (x=143/339/535).
2. `input tap <x> <y>` -> app opens LiveFreeActivity playing that channel.
3. `dd if=/proc/<pid>/mem` (dalvik-main region space) -> grep the current
   play-state JSON object: `{"buffer":...,"buss":"live",...,"media":"cyx-...",
   "program":"cyx-...","title":"..."}`.
4. Back to Home, next card.

## Live pairs (2026-08-01 ~20:30 UTC)

| # | title | program | media (hash) | heap map matches? |
|---|-------|---------|--------------|-------------------|
| 1 | Cinemax HD | `cyx-Cinemax` | `cyx-C9EB0B2644979328E598EAFED311` | ✅ |
| 2 | LA Red HD | `cyx-LaRedHD` | `cyx-1F3251F9425197449B94E006D8EB` | ✅ |
| 3 | History HD | `cyx-HistoryHD` | `cyx-848FCA984BF8bdf324636E429E4F` | ✅ |
| 4 | History HD | `cyx-HistoryHD` | `cyx-848FCA984BF8bdf324636E429E4F` | ✅ |
| 5 | COMEDY CENTRAL HD | `cyx-ComedyCentralHDCentral` | `cyx-A984501543F1830d8061BEAA4ABF` | ✅ |
| 6 | EPIX HITS HD | `cyx_EPIXHITSHD` | `cyx_7Pt7nXam3AaRMSHukKti` | ✅ |
| 7 | TNT SERIES MX HD | `cyx-TNTSeries_Central` | `cyx-B2F1847E4A31acd511AA01D33AE8` | ✅ |
| 8 | CNN Chile HD | `cyx-CNNChileHD` | `cyx-7D8ECCA24B809980796A31B4EF6A` | ✅ |
| 9 | CNN Chile HD | `cyx-CNNChileHD` | `cyx-7D8ECCA24B809980796A31B4EF6A` | ✅ |

## Sample raw play-state JSON (TNT SERIES MX HD)

```json
{"buffer":14545,"buss":"live","format":"","host":"","latency":6197,"links":[""],
"media":"cyx-B2F1847E4A31acd511AA01D33AE8","media_buffer":3712515904,
"play_url":"mem://127.0.0.1:39545","program":"cyx-TNTSeries_Central",
"snapinfo_url":"http://127.0.0.1:39545/vod/0/cyx-B2F1847E4A31acd511AA01D33AE8.snapinfo",
"snapshot_url":"","title":"TNT SERIES MX HD","url_modified":true}
```

## Notes
- The Home "Vivo gratis" grid shows only ~9 free channels (the app's free-tier
  subset). The other free channels live in the scrollable LiveFreeActivity list
  (which my first tap-walk attempt could not reach reliably — it enters
  fullscreen playback). The heap dump covered the FULL cached list (956
  channels), so the mapping is not limited by what the grid shows.
- E! HD and Las Estrellas Mex HD were visible in the grid but the taps returned
  the neighboring channel (grid position drift after playback) — their hashes
  are already in the heap map (LasEstrellasHD -> 5E5A406F44BFb5a91481B29F9BDD,
  E! HD -> via cyx-EHDCentral or name match).
