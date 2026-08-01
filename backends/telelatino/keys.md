# TeleLatino — carved keys / crypto status

**Status: PORTAL_KEY constant recovered from live heap; BBDatabase `res`
decrypt NOT yet achieved (no candidate derivation produced a hit). The
channel→hash mapping did NOT depend on this — the heap carried the channel
records in plaintext.**

Date: 2026-08-01 · Branch: `telelatino-hash-live` · Source: `_session/tl_heap_live.hprof` (46 MB Java heap, live process on `.4`)

---

## 1. PORTAL_KEY — recovered (new)

The app's heap string table contains a `PORTAL_KEY` constant:

```
PORTAL_KEY (hex) = 3971464d6d4d47797179716e4c5a454874615948436149356978735357426a58774b4173347776744b496d6a70707234477a316b54513d3d
PORTAL_KEY (utf8) = 9qFMmMGyqyqnLZEHtaYHCaI5ixsSWBjXwKAs4wvtKImjppr4Gz1kTQ==
```

That is a **base64 string** (56 chars). Nearby heap strings confirm the app's
crypto surface: `encryptThreeDESECB(string, KEY)`, `encryption(plainData,
encryptKey)`, `internalKeys`, `DESede/ECB/PKCS7Padding`, `DESede/CBC/PKCS7Padding`.

**Tested derivations against the BBDatabase `res` blob (3DES/ECB, 3DES/CBC,
DES, AES; raw / app-b64 / std-b64 / md5 / sha256 / sha1 key forms): NO HIT.**
The `res` blob is likely encrypted with a per-session or different constant
than the recovered static keys, or it predates a key rotation. The heap does
NOT contain the res ciphertext (old DB row), so the key schedule for it was
never resident.

## 2. Other heap key-ish constants (recorded for completeness)

| hex constant | utf8 | note |
|---|---|---|
| `47733965796f4c345a4f457a6b596e4a7252545a54673d3d` | `Gs9eyoL4ZOEzkYnJrRTZTg==` | b64, 12B key — no hit |
| `4d5176554356796852304b6e465a6b6a642b4c2b79513d3d` | `MQvUCVyhR0KnFZkjd+L+yQ==` | b64, 12B key — no hit |
| `3837535330736b7541787a7453514f6e7933574543513d3d` | `87SS0skuAxztSQOny3WECQ==` | = portal_code (device) — not a key |
| `76356c476568424f4a38334761645a697957757344673d3d` | `v5lGehBOJ83GadZiyWusDg==` | b64 — no hit |
| `4f6f786b4b5a7a3933666842554e6c55717338584b71325a3635436b4e463736583442714b345572434a504c556e72384136647252773d3d` | `OoxkKZz93fhBUNlUqs8XKq2Z65CkNF76X4BqK4UrCJPLUnr8A6drRw==` | b64 — no hit |
| `3134394435423652514b557934393179334b623363673d3d` | `149D5B6RQKUy491y3Kb3cg==` | b64 — no hit |
| `4344376e64455535647a6f4e4b6c65686f7633654b413d3d` | `CD7ndEU5dzoNKlehov3eKA==` | b64 — no hit |
| `6e62477848576c783878684771306d39566f4b6a33673d3d` | `nbGxHWlx8xhGq0m9VoKj3g==` | b64 — no hit |
| `485059655758394773667941376a637a4c50796e59773d3d` | `HPYeWX9GsfyA7jczLPynYw==` | b64 — no hit |
| `624843643831436564585063694948456f586b3376413d3d` | `bHCd81CedXPciIHEoXk3vA==` | b64 — no hit |
| `48323441545a2b74764274625a322f4f3542644559513d3d` | `H24ATZ+tvBtbZ2/O5BdEYQ==` | b64 — no hit |

## 3. Known-candidate keys (from FINDINGS) — re-tested, no hit on res

All 5 UUID candidates (`4c087185-…`, `b700bce0-…`, `0e5e9c33-…`, `20799a27-…`,
`629a824d-…`) — NOTE: `20799a27-fa80-4b36-b2db-0f8141f24180` is the **AppMetrica
analytics component id** (DB filenames in heap), NOT a crypto key. `b700bce0-…`
appears in the same string cluster as `encryptThreeDESECB` — still no decrypt
hit. koocan's keys (`b940e017-…`, `c6768bbe-…`) — no hit. `dCsPLwiy` (DES, used
for getAddr) — no hit on res.

## 4. BBDatabase res — what it is

`EventDbModel` row 302 (event `app`, start 1785626780575) reserveA contains:

```json
{"parameter":[
  {"name":"res","value":"F4TKB+KlCp05jf15qS6BKFcPQbvU2bGYqQAHgq6DJ2u2EjX/hLAc9kuD0qt1tiZZLkrLeYmsDT4p9XkgE7mRZUX/CMhXfaL/"},
  {"name":"uname","value":"nestor.ale@gmail.com"},
  {"name":"state","value":"1"},
  {"name":"uid","value":"25885636"}]}
```

72 ciphertext bytes = 9 DES blocks, standard base64. The plaintext would be a
portalCore response from when the app last logged in (pre-gate). Decrypting it
was NOT required for the mapping — the live heap contained the same channel
data in plaintext (see CHANNEL-HASH.md).

## 5. What this means for the standalone goal

The channel→hash mapping is fully delivered (285/344 EPG + 956 catalog + live
validation + end-to-end TS). The 3DES key remains the only missing crypto
piece; it matters only for replaying portalCore responses offline, and the
portalCore is hard-gated anyway (portal200001 for all apkVer — see
portal_probe.py results). Priority for the standalone client: re-serve the
heap-derived mapping + replicate the P2P mesh playlist fetch (E2E-VERIFIED.md).
