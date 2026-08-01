# TeleLatino — Channel name→hash mapping (live harvest)

**VERDICT: MAPPING OBTAINED — 285/344 EPG channels mapped (+956 heap catalog,
+9 live-validated, +1 end-to-end TS verified).**

Date: 2026-08-01 · Branch: `telelatino-hash-live` · Device: `.4` (exclusive)

---

## How it was obtained (NO 3DES keys needed)

The hash is a **server-assigned token** delivered in the play/getLiveData
response (proven by the sibling `telelatino-hash-derive` worker — NOT
derivable). The app's **Java heap dump** (`_session/tl_heap_live.hprof`,
46 MB, `am dumpheap`-style from the live process on `.4`) contains the full
cached channel list **in plaintext**, with each channel row serialized as:

```
<NAME> <cyx-CODE> [] <NAME> 1 0 <LIVE[,TVOD]> h264 x" IP
icon <icon-url> poster <poster-url> 4 <signed-URL media_code=cyx-<HASH>>
```

The signed URL that follows the record's own `4` marker carries the channel's
m3u8 hash (`/live/cyx-<HASH>.m3u8`). Verified against all three known-good
ground truths:

| channel code | hash | check |
|---|---|---|
| `cyx-Cinemax` | `C9EB0B2644979328E598EAFED311` | ✅ matches recipe (BBDatabase) |
| `cyx-LaRedHD` | `1F3251F9425197449B94E006D8EB` | ✅ matches recipe + play JSON + peer segments |
| `cyx-SyfyHD` | `F94D98EB4360be8eA43C337FF832` | ✅ matches derive corpus (heap record) |

Additional spot-checks: `cyx-HistoryHD` → `848FCA984BF8bdf324636E429E4F`
(consistent across 3 independent serializations in the heap),
`cyx-AMC` → `D08E1E3D47688ed3A7F115FEB16A` (the `4`-field URL right after the
AMC row).

### Two channel classes (per derive worker)

| EPG code style | m3u8 key | derivable? |
|---|---|---|
| `cyx_<hex/digits>` (underscore) | the code itself | ✅ IDENTITY — no portalCore |
| `cyx-<Name>` (human, e.g. Cinemax) | separate 28-hex | ❌ server token — heap harvest needed |
| `cyx-<28hex>` | the code itself | ✅ IDENTITY |

---

## The mapping

### 1. EPG identity channels (174) — code == m3u8 key

All EPG codes of the form `cyx_<hex/digits>` (173) and `cyx-<28hex>` (1) are
their own m3u8 key. Full list: `backends/telelatino/epg_map.json` + the derive worker's
`corpus/identity_table_174.csv`.

### 2. EPG human-named channels mapped from heap (69)

Full table in `backends/telelatino/channel-hash-table.csv` (this file's
companion). Key entries:

| EPG code | m3u8 hash | source |
|---|---|---|
| `cyx-Cinemax` | `C9EB0B2644979328E598EAFED311` | heap 4-field + recipe |
| `cyx-LaRedHD` | `1F3251F9425197449B94E006D8EB` | heap 4-field + recipe |
| `cyx-SyfyHD` | `F94D98EB4360be8eA43C337FF832` | heap 4-field + corpus |
| `cyx-HistoryHD` | `848FCA984BF8bdf324636E429E4F` | heap (3 serializations) |
| `cyx-AMC` | `D08E1E3D47688ed3A7F115FEB16A` | heap 4-field |
| `cyx-A&EHD` | `7BCA096543B49d9fF5E93008926A` | heap 4-field |
| `cyx-AnimalPlanetHD` | `DC112CF245F1b821EACF9B31649E` | heap 4-field |
| `cyx-CinecanalHDCentral` | `222C0D274909bcf12CEACB4D8AA7` | heap 4-field |

### 3. Full heap catalog (956 channels) — bonus

The heap's channel list is a SUPERSET of the EPG (956 mapped codes, including
827 not present in the 344-EPG — regional/extra channels). All in
`backends/telelatino/heap_channel_map.json` / `backends/telelatino/epg_map.json`.

### 4. Unmapped EPG codes (59)

Not present in the app's cached channel list on `.4` — the app's live list is
a subset of the EPG (ESPN/FoxSports variants, C5N, TelefeHD, Boomerang, A24,
MTV Live, Telemundo Internacional, etc.). Proven NOT reachable via the update
path: the MarketServer update check returns `<ApkInfo><list rows="0"/></ApkInfo>`
(no newer build exists to clear the version gate), and portalCore is hard-gated
(portal200001) for every apkVer (54608/60203/99999). See NEEDS.md.

### 5. Live validation + end-to-end proof

- `TAP-WALK-VALIDATION.md`: 9/9 free channels tapped on the live UI, play-state
  JSON program↔media pairs ALL match the heap map.
- `E2E-VERIFIED.md`: Cinemax → hash → live m3u8 (through the P2P peer) → 1.16 MB
  real HEVC .ts, ffprobe OK.

---

## Files

| File | Purpose |
|---|---|
| `backends/telelatino/channel-hash-table.csv` | the recovered mapping (EPG + heap) |
| `backends/telelatino/extract_channel_map.py` | heap hprof → channel hash extractor |
| `backends/telelatino/tap_walker.sh` | on-device tap→play-state-json walker |
| `backends/telelatino/grab_play.sh` | on-device play-state JSON extractor |
| `backends/telelatino/verify_stream.py` | m3u8/segment fetch through the P2P peer |
| `backends/telelatino/portal_probe.py` | off-device portalCore gate probe |
| `backends/telelatino/TAP-WALK-VALIDATION.md` | live UI validation evidence |
| `backends/telelatino/E2E-VERIFIED.md` | end-to-end m3u8+ts proof |
| `backends/telelatino/keys.md` | carved keys + BBDatabase res status |
| `backends/telelatino/heap_channel_map.json` | extractor output (all 956 + identity 934) |
| `backends/telelatino/epg_map.json` | EPG-mapped + extra-heap split |

## 3DES keys / BBDatabase — status

SEE `keys.md`. `PORTAL_KEY` constant recovered from the live heap
(`9qFMmMGyqyqnLZEHtaYHCaI5ixsSWBjXwKAs4wvtKImjppr4Gz1kTQ==`, base64) plus the
`e9b37dff-a143-3bf6-8d38-16d3dd06365b` tag UUID (per-install signed-URL salt).
The BBDatabase `res` 3DES decrypt produced no hit with any candidate — but the
channel mapping never depended on it (heap carried plaintext records).
