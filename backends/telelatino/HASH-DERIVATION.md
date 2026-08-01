# TeleLatino — Hash Derivation Analysis (`cyx-<HASH>`)

**Verdict: NOT DERIVABLE — the `cyx-<28-hex>` m3u8 hash is a server-assigned channel
identifier, not a pure function of any channel identity we possess.** Every digest,
encoding, salt, HMAC, KDF, truncation, and fixed-transform hypothesis is ruled out
below. **However**, the analysis uncovered a second, important class of channels for
which the m3u8 key IS the EPG code itself (identity — derivable trivially, no
portalCore needed). Details follow.

Date: 2026-08-01 · Branch: `telelatino-hash-derive` · Offline, no device touched.

---

## 1. The question

The live pipeline serves playlists as `/live/cyx-<HASH>.m3u8` with segments
`/live/cyx-<HASH>/cyx-<HASH>_xycjco_<rd>.ts`. The recipe (`STANDALONE-RECIPE.md`)
mapped two channels from `BBDatabase.db` on `.4`:

```
cyx-Cinemax   → cyx-C9EB0B2644979328E598EAFED311
cyx-LaRedHD   → cyx-1F3251F9425197449B94E006D8EB
```

The EPG (open endpoint) gives 344 human channel codes but **no** hashes. If the hash
were a pure function of something we already have (channel code, display name, EPG
id/position, a fixed salt), portalCore becomes irrelevant. This worker tested that
hypothesis exhaustively with pure compute.

## 2. Ground-truth corpus

Three high-confidence (identity → hash) pairs, from committed artifacts and the
sibling live worker's heap capture (sibling worktree `telelatino-hash-live`, files
referenced below — not part of this branch's commits):

| Channel code | Hash | Confidence | Evidence |
|---|---|---|---|
| `cyx-Cinemax` | `C9EB0B2644979328E598EAFED311` | HIGH | `STANDALONE-RECIPE.md` (BBDatabase.db on `.4`) |
| `cyx-LaRedHD` | `1F3251F9425197449B94E006D8EB` | HIGH | recipe + heap play-response JSON (`program`→`media`) + `_session/peer_cap*.txt` m3u8 segments |
| `cyx-SyfyHD` | `F94D98EB4360be8eA43C337FF832` | HIGH | `heap_small.bin` clean channel record (name/code/name + icon + signed-URL `media_code`) |

Corpus file: `corpus/hash_corpus.json`. EPG codes: `corpus/channels_344.txt`.

## 3. What the app's memory actually reveals

The sibling live worker's `_session/heap_small.bin` (16 MB Java heap) contains the
app's cached channel list and playback-token structures. Three decisive facts:

### 3a. The play response pairs `program` (code) ↔ `media` (hash) — server-supplied

```json
{"err":0,"res":"{...\"buss\":\"live\",\"media\":\"cyx-1F3251F9425197449B94E006D8EB\",
  \"play_url\":\"mem://127.0.0.1:39545\",\"program\":\"cyx-LaRedHD\",
  \"snapinfo_url\":\"http://127.0.0.1:39545/vod/0/cyx-1F3251F9425197449B94E006D8EB.snapinfo\",
  \"title\":\"LA Red HD\"...}"}
```

The hash arrives from the server in the play/getLiveData response. It is **received,
not computed** client-side.

### 3b. Signed playback URLs mint the same hash as `media_code`

```
app_id=com.spanish.latinotvod&tag=e9b37d&scheme=md5-01&media_code=cyx-C9EB0B2644979328E598EAFED311&expired=1786217659&token=<32hex>
```

The `media_code` in the signed URL is the same value that appears in the m3u8 path.
`tag=e9b37d` / `e9b37dff-a143-3bf6-8d38-16d3dd06365b` are fixed per install — tested
as salts, no effect.

### 3c. TWO channel classes exist — and one needs NO derivation

Parsing the heap's channel records (name → code → icon → signed URL) yields 129
clean records:

- **124 coded channels**: `media_code == channel_code` exactly. Their EPG code IS
  their m3u8 key. Example: `VIDEOROLA` → `cyx_95688897993085317747001185` →
  signed URL `media_code=cyx_95688897993085317747001185`.
- **5 human-named channels**: `media_code` is a separate 28-hex hash
  (`cyx-SyfyHD` → `cyx-F94D98EB4360be8eA43C337FF832`, `cyx-TELEMUNDO3USA` → …).

Cross-checking the EPG against the heap's media codes: **68 of 344 EPG codes appear
verbatim as `media_code` values** — all `cyx_*` style (e.g. `cyx_8CDD138E450A6EC58`).

### 3d. Consequence for the standalone client

| EPG code style | Count | m3u8 key | Derivable? |
|---|---|---|---|
| `cyx_<hex/digits>` (underscore) | 173 | the code itself | ✅ IDENTITY — no portalCore |
| `cyx-<28hex>` (already a hash) | 1 | the code itself | ✅ IDENTITY |
| `cyx-<Name>` (human, e.g. Cinemax/LaRedHD) | 107 | separate 28-hex | ❌ NOT DERIVABLE |
| other formats (suffix `_cyx_480p` etc.) | 62 | unknown | ❓ untested / likely identity |

For the 174 identity channels the standalone client can build
`/live/<channelCode>.m3u8` directly from the open EPG. **The 107 human-named
channels — including Cinemax and LaRedHD — still require the portalCore mapping.**
That mapping is confirmed as the only path for them (matches the live worker's plan).

## 4. Hypothesis space tested (ALL ruled out)

Harness: `hash_derive.py` (committed). Sweep vs the 3 ground-truth pairs, requiring a
formula to reproduce **both** `cyx-Cinemax` and `cyx-LaRedHD` (and ideally all 3).

### Inputs (per channel)
- Channel code: `cyx-Cinemax`, `Cinemax`, `cyxCinemax`, `cyx-Cinemax` (all case
  variants, dash/underscore/no-separator, URL-encoded, UTF-16LE/BE)
- Display name from heap: `Cinemax HD`, `LA Red HD`, `USA NETWORK HD` (all case +
  separator variants)
- Raw channel code (`50fdcc0817d61` for Cinemax from `.4` prefs) + `_720p` suffixes
- EPG position: list index (0-based/1-based, zero-padded), real 344-list index
- Structured forms: JSON `{"channelCode":…}`, query strings, `/live/…` path fills,
  m3u8 path fills
- All of the above × 40 salts (both sides, dash/underscore separators)

### Salts (40)
`cyx`, `cyx-`, `cyx_`, `latinotv`, `latino`, `com.global.latinotv`,
`com.spanish.latinotvod`, `dCsPLwiy`, `cloudstream`, `54608`, `spkgVer`, `xycjco`,
`720p`, EPG md5 param, device SN, device id, user id, koocan DES/3DES keys,
5 TeleLatino 3DES key candidates, tag UUID `e9b37dff-…`, brand strings, EPG path
parts (`utc-3`, `26`, `v2/live/app`, `epg`).

### Functions (17 digest families)
`md5`, `sha1`, `sha224`, `sha256`, `sha384`, `sha512`, `sha3_224/256/384/512`,
`blake2b`, `blake2s`, `ripemd160`, `sm3`, `md5-sha1`, `sha512_224`, `sha512_256`

### Encodings / transforms
- hex (lower/upper), base64, base64url, base32, base36, base62 of every digest
- every 28-char window of every digest (targets are 28 hex chars)
- drop-4-hex-char truncations at every offset (28 = 32−4), drop-2-byte variants
- double hashing: `hash2(hash1_hex)` and `hash2(hash1_bytes)` for all pairs
- digest-concat: `hash2(digest(in)+digest(salt))` both orders
- HMAC (md5/sha1/sha256/sm3) with every salt as key AND as message
- CRC32 (hex/dec), pbkdf2-hmac-sha256 with device salts
- XOR-mask / additive-delta / byte-permutation **fixed-transform consistency** across
  channels (the strongest pure-function signature: if `target = T(digest(input))`
  with a shared T, two pairs must show identical per-channel deltas — none found)

### Result
```
cyx-Cinemax → C9EB0B2644979328E598EAFED311: NO HIT
cyx-LaRedHD → 1F3251F9425197449B94E006D8EB: NO HIT
cyx-SyfyHD  → F94D98EB4360be8eA43C337FF832: NO HIT
channels with >=1 hit: 0/3
fixed-transform consistency: no shared transform found
```

## 5. Why it is not a digest at all

Beyond the exhaustive negative result, the hash format itself argues against a
client-computed digest:

- **Length**: 28 hex chars = 14 bytes = 112 bits — not a standard digest length.
- **Case**: the 28 chars are not uniform-case. Known hashes include
  `F94D98EB4360be8eA43C337FF832` (mixed case) next to all-uppercase
  `C9EB0B2644979328E598EAFED311`. A hex digest of a string would be uniform case
  after any normalization; mixed case is what a **random server token** looks like.
- **Provenance**: the value appears verbatim in server responses (play response
  `media`, signed URL `media_code`, snapinfo URL). Nothing in the (unpacked-stub)
  DEX computes it — the app stores what the server sends.

## 6. Ruled-out vs live-worker implication

- **Ruled out**: md5/sha1/sha2/sha3/blake/ripemd/sm3 of channel code/name/title/raw
  code/index ± 40 salts; every re-encoding and truncation of every digest; double
  hashing; HMAC; KDFs; fixed byte-transforms. Also ruled out: a stable per-channel
  integer (EPG index) as the hash input.
- **Confirmed**: for the 174 coded channels the m3u8 key is the EPG code itself
  (identity) — a standalone client can derive those with zero crypto.
- **Only path for the 107 human-named channels**: the live worker's portalCore
  mapping (or a future newer-APK unlock). This worker's verdict is
  **NOT DERIVABLE** for that class.

## 6a. Live verification attempt (open endpoints only)

The P2P peer (`108.181.133.189:33984`) was probed with the known-good LaRedHD hash
path, an identity-channel underscore path, and garbage dash paths. All returned
`HTTP/1.1 410 Gone` — including garbage — confirming the recipe's note that m3u8s
are short-lived (~30 s) and the peer 410s expired/unminted playlists uniformly. So a
fresh m3u8 fetch cannot distinguish identity from hash paths without a freshly minted
playlist (which requires the gated portalCore). The identity claim rests on the heap
record structure (§3c), not on live playback.

## 7. Reproduce

```bash
# fetch the open EPG (344 channels) — network OK, no gate
python3 - <<'EOF' | tee corpus/channels_344.txt   # see hash_derive.py load_channels
import json, urllib.request
req = urllib.request.Request(
    "http://xipre.xifhzu.com/epg/v2/live/app/utc-3/26?md5=fc9548268cd91bd1506d8fb142cf8972",
    headers={"apk": "com.spanish.latinotvod", "apkVer": "54608",
             "spkgVer": "2024-11-15 19:08:51_29_14.1_4.9.170", "User-Agent": "okhttp/4.12.0"})
d = json.loads(urllib.request.urlopen(req, timeout=20).read())
print("\n".join(ch["channelCode"] for ch in d))
EOF

# run the full hypothesis sweep vs the ground-truth corpus
python3 backends/telelatino/hash_derive.py
```

## 8. Files

| File | Purpose |
|---|---|
| `backends/telelatino/hash_derive.py` | hypothesis harness (sweep + fixed-transform check) |
| `backends/telelatino/corpus/hash_corpus.json` | ground-truth pairs, identity-channel list, EPG split |
| `backends/telelatino/corpus/channels_344.txt` | the 344 EPG channel codes |
| `backends/telelatino/corpus/identity_table_174.csv` | 174 channels whose m3u8 key = EPG code (identity) |
| `backends/telelatino/HASH-DERIVATION.md` | this report |
