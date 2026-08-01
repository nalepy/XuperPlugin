# TeleLatino — BBDatabase `res` 3DES decrypt harness + offline key hunt

Date: 2026-08-01 · Branch: `telelatino-crypto-offline` · Worker: offline-only (no device, no network)

## LOUD VERDICT

**NO KEY IN COMMITTED ARTIFACTS — the `res` ciphertext is NOT decryptable offline.**
TeleLatino's 3DES response keys are NOT among the 5 UUIDs in the dex string table, the
obfuscated key clusters, the koocan keys, or anything recoverable from the committed
heaps/docs. The harness is ready so that **the instant `telelatino-hash-live` carves the
real keys from `.4`'s memory, decryption is ONE command:**

```bash
python3 backends/telelatino/telelatino_bbdb.py --key '<carved-key>'          # auto hex/b64/raw
python3 backends/telelatino/telelatino_bbdb.py --key '<carved-key>' --mode CBC   # if not ECB
python3 backends/telelatino/telelatino_bbdb.py --key '<key>' --mode CBC --prepend-iv  # iv-first blobs
```

The live worker's key can be dropped in as-is (hex, base64, or raw string; the harness
auto-detects). When a key produces `[decrypt] OK — PKCS7 valid`, the channel→hash rows
print immediately.

---

## 1. Ciphertext characterization

| Property | Value |
|---|---|
| Source | `_session/BBDatabase.db` → `EventDbModel` row `id=301`, `eventId=app`, `notIntactEvent=1` |
| Field | `reserveA` JSON → `parameter[]` → `{"name":"res","value":"<b64>"}` |
| `res` value | `qRDqHj8si1HTcN6YKoPmVt9cICZZoTaRTLGgbu4FdlNsr+l+7WWC4EuD0qt1tiZZhK8KoyEkUFw=` |
| Encoding | **standard base64** (76 chars, `+`/`/`/`=` present) |
| Decoded | 56 bytes, hex `a910ea1e3f2c8b51d370de982a83e656df5c202659a136914cb1a06eee0576536cafe97eed6582e04b83d2ab75b6265984af0aa32124505c` |
| Block alignment | 56 % 8 == **0** → 7 DES blocks → 3DES/DES-class cipher, PKCS5/7 padding |
| Entropy | ~5.6 bits/byte (uniform → ciphertext, not plaintext) |
| Plaintext guess | non-ASCII binary; a successful decrypt should be **UTF-8 JSON** (`looks_json` gate) |
| Likely mode | **ECB** (app heap strings: `DESede/ECB/PKCS5Padding`, `encryptThreeDESECB(string, KEY)`) |
| Notes | NOT koocan's `hex(base64(3DES))` envelope — this is **raw base64(3DES-ct)**. One sample only in the whole repo. |

Row context: `uname=nestor.ale@gmail.com`, user `25885636`, `appVer=43405` (com.android.mgstv
build lineage), captured pre-version-gate while the app still worked. Second `res`-carrying row
would let us confirm IV/ECB; with one sample, ECB-PKCS7 is the primary hypothesis, CBC-zero-IV
and CBC-prepended-IV are covered as alternates.

## 2. PROOF: koocan keys FAIL on TeleLatino `res`

Both koocan 3DES response keys, applied with koocan's exact derivation
(`app_b64decode(key_str)[:24]`), do **not** decrypt the TeleLatino blob:

```
koocan b940e017-cfea-4aa0-b69d-3a82b6428ed3
  key24=6fde347b4d7bfdc7de6bfe1a6b4fdbebd77fddaf366fae36
  ECB: FAIL — PKCS7 pad check FAILED (not this key / wrong mode)
  CBC: FAIL — PKCS7 pad check FAILED (not this key / wrong mode)
koocan c6768bbe-189f-4d9d-b35c-f235a9fd7587
  key24=73aefaf1b6deff5f3d7ffe1df5dfdbdf973f7f6df96bd7dd
  ECB: FAIL — PKCS7 pad check FAILED (not this key / wrong mode)
  CBC: FAIL — PKCS7 pad check FAILED (not this key / wrong mode)
koocan DES 'dCsPLwiy' as DES/ECB: FAIL — PKCS7 pad check FAILED
koocan DES 'b940e017' as DES/ECB: FAIL — PKCS7 pad check FAILED
koocan DES 'D#a!t-a&' as DES/ECB: FAIL — PKCS7 pad check FAILED
```

Failure mode is exact: each candidate decrypts the 56-byte blob, the last byte is
**not** a valid PKCS7 pad (observed last bytes: `0x65`, `0x98`, … — random garbage, no
`0x01`–`0x08` repetition), so no plaintext survives. **TeleLatino's keys are different from
koocan's** — confirmed by direct test, not assumption. (The earlier FINDINGS "3DES keys not
recovered" claim is now upgraded: the failure is measured.)

## 3. Offline key hunt — the committed-artifact scan

`telelatino_bbdb.py --scan` exhaustively tried **193,458 (key, mode) attempts**:

1. **5 UUIDs from the TeleLatino dex string table** (`0e5e9c33-…`, `20799a27-…`,
   `4c087185-…`, `629a824d-…`, `b700bce0-…`) — each through 6 derivation families:
   - app-family broken b64 decode `[:24]` / `[:16]` (koocan pattern)
   - standard b64 `[:24]` / `[:16]`, raw-string `[:24/16/8]`, zero-padded
   - **dash-stripped hex → 16B 2-key 3DES, + first-8 → 24B `k1k2k1`**
   - md5 / sha1 digests `[:16]` / `[:24]`
   - every key × ECB, CBC-zero-IV, CBC-prepended-IV
2. **Obfuscated clusters** `\AoaTAka`, `\pa*Tpe*`, `&@eT0f!8`, `b972E8a5A4e0e8Ff`
   (8-byte DES request keys, same class as koocan's `dCsPLwiy`) — as DES, 3DES k×3, and
   all derivations.
3. **koocan keys** (`b940e017-…`, `c6768bbe-…`, `dCsPLwiy`, `b940e017`, `D#a!t-a&`) — fail
   (Section 2).
4. **9,379 artifact-derived candidates** — every base64/hex/ASCII string ≥16 chars mined
   from the committed dex string table (`_session/telelatino_dex/classes.dex`),
   `xtv_dex/dex_strings.txt`, heap context extracts (`heap_domain/blob/notice/portal/zap`),
   and the FINDINGS/ASSESSMENT docs; every 48-byte window around `DESede` / `3DES` /
   `BBDatabase` / `getLiveData` / `portalCore` / `res` / `KEY` / `DES3`; all derivations.
5. **PBE family** — `PBEWITHMD5ANDDES-CBC` (8B) and `PBEWithSHAAnd3KeyTripleDES` (24B)
   key schedules over 15 password/identity candidates (email, password, SN, uid, device id,
   app id, `cloudstream`…) × 6 salts × 3 iteration counts.

Result: **0 JSON hits.** Every decrypt that passed the PKCS7 gate was a non-UTF-8 random
artifact (e.g. 16B of the RxJava class name `MaybeToObservableObserver` — a false positive
that the strict `looks_json` gate rejects). The key is **not** in the committed artifacts.

**Why the dex UUIDs are not the key:** in the koocan family the UUID → custom-b64 → 24B
derivation round-trips because both sides share the "broken" decoder. Here all five UUIDs
fail that derivation (and 5 more plausible schedules). The Bangcle/SecNeo packing keeps the
real constants in the encrypted region (ASSESSMENT.md: "exact key constants are in an
obfuscated region"), so static string-mining cannot reach them — consistent with the
`telelatino-hash-live` worker having to carve them from `.4`'s memory.

## 4. Hand-off — usage of `telelatino_bbdb.py`

```bash
# self-test (validates the harness, not the key)
python3 backends/telelatino/telelatino_bbdb.py --selftest

# prove koocan keys fail (Section 2, reproducible)
python3 backends/telelatino/telelatino_bbdb.py --proof-koocan

# full offline key hunt (Section 3, reproducible)
python3 backends/telelatino/telelatino_bbdb.py --scan [--verbose]

# decrypt with a carved key — auto-detect hex/b64/raw
python3 backends/telelatino/telelatino_bbdb.py --key '<carved-key>'
python3 backends/telelatino/telelatino_bbdb.py --key '<key>' --mode CBC
python3 backends/telelatino/telelatino_bbdb.py --key '<key>' --mode CBC --iv <hexiv>
python3 backends/telelatino/telelatino_bbdb.py --key '<key>' --mode CBC --prepend-iv
python3 backends/telelatino/telelatino_bbdb.py --key-format appb64 --key '<uuid>'
python3 backends/telelatino/telelatino_bbdb.py --ciphertext '<b64>' --key '<key>'   # no DB needed
python3 backends/telelatino/telelatino_bbdb.py --db /path/BBDatabase.db --key '<key>'
```

Key formats accepted (auto or `--key-format {hex,b64,appb64,raw}`): 16- or 24-byte 3DES
keys, 8-byte DES keys. Success prints `[decrypt] OK — PKCS7 valid` + the JSON plaintext
(→ the channel→hash rows). Exit codes: 0 = decrypted/selftest pass, 1 = key failed,
2 = usage/data error, 3 = scan found nothing.

## 5. What the live worker needs to return

Any of: (a) the **24-byte (or 16-byte) 3DES response key** in any encoding — drop straight
into `--key`; (b) the **UUID-style key string** if the app still uses the custom-b64
derivation — `--key-format appb64`; (c) the **obfuscated cluster region** around the key
constants from the heap (ASSESSMENT.md's `\AoaTAka`/`\pa*Tpe*`/`&@eT0f!8`/`b972E8a5A4e0e8Ff`
clusters are request-side, not the response key). Once one key lands, `--scan` + `--key`
together will confirm and emit the rows. If the carved key still fails, grab a **second**
`res` row (any later EventDbModel entry) — with two samples we can distinguish ECB vs CBC
and recover the IV.
