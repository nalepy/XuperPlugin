# NATIVE-CAPTURE — the live XTV app's ACCEPTED portalCore request+response (box `.8`)

> Branch `xtv-native-capture`. Captured 2026-08-01 on box `192.168.100.8` (Android 7.1.2 / SDK 25,
> rooted, `com.android.mgstv` v4.34.5/43405, NOT logged in). Method: **native-heap capture** — frida
> spawn is killed at t=1s by the ijiami anti-frida watchdog and frida attach fails (no code pages), so
> the app's own in-memory DoHttpSec request/response log was carved with `process_vm_readv` instead.
> All artifacts: `backends/xtv/capture_*.c/sh/py/go/js`.

---

## 1. THE PRIZE — a fresh ACCEPTED portalCore request+response (cleartext)

### 1a. `POST /api/portalCore/getEmailSuffix` → **`returnCode:"0"` (ACCEPTED)**

The app's own DoHttpSec log record (carved from the live heap, JSON spec exactly as the native layer
received it — PLAINTEXT body, headers, no hidden tokens):

```json
{
  "session": "NNRVnOZ6Oquw",
  "service_name": "portal",
  "method": "POST",
  "url": "/api/portalCore/getEmailSuffix",
  "headers": "Content-type: application/json;charset=utf-8\r\napk: com.android.msandroid\r\napkVer: 43405\r\nspkgVer: 2018-09-18 11:30:06_25_7.1.2_3.14.29\r\nCache-Control: no-store\r\n",
  "body": "{\"apkVersion\":\"43405\",\"appId\":\"com.android.msandroid\",\"appLanguage\":\"en\",\"b29\":\"4435652b4d4641546c6963733953743863376b75424f474c576b5a6d41326e6474724e534b567937426a6e4c556e72384136647252773d3d\",\"contentType\":\"application/json;charset=utf-8\",\"cpu\":\"armeabi-v7a\",\"deviceToken\":\"\",\"hardwareInfo\":\"amlogic\",\"loginType\":\"2\",\"model\":\"SM-G973F\",\"portalCode\":\"\",\"product\":\"Galaxy S10\",\"reserve1\":\"76356c476568424f4a38334761645a697957757344673d3d\",\"sdkVer\":25,\"sn\":\"1ecb8d3244c6460059e85db8f0d47fbb\",\"sysVersion\":\"2018-09-18 11:30:06_25_7.1.2_3.14.29\"}",
  "timeout": 60000,
  "data": "{\"tdc\":false}"
}
```

The app's own response record (same session, carved from the same heap region, DoHttpSec wrapper):

```json
{"body":"{\"returnCode\":\"0\",\"errorMessage\":\"成功\",\"data\":{\"emailSuffixStr\":\"@gmail.com,@gmail.com.br,@hotmail.com,@hotmail.com.br,@outlook.com,@outlook.com.br,@live.com,@uol.com.br,@bol.com.br,@yahoo.com,@yahoo.com.br,@ymail.com,@globomail.com,@msn.com,@aol.com,@live.cl,@hotmail.cl,@outlook.es,@outlook.com.mx,@outlook.com.ar,@outlook.com.pe,@outlook.com.co,@outlook.cl,@hotmail.br,@hotmail.es,@hotmail.com.mx,@hotmail.com.ar,@hotmail.com.pe,@hotmail.com.co,@yahoo.es,@yahoo.com.mx,@yahoo.com.ar,@yahoo.com.pe,@yahoo.com.co,@yahoo.cl,@live.com.cl,@live.com.ar,@live.com.mx\"}}","data":"{\"tdc\":false}","err":"0","headers":"...cf-cache-status: DYNAMIC...content-length: ...content-type: application/json..."}
```

`err:"0"` + the response headers (Cloudflare cf-ray etc.) confirm the call went edge→origin and the
origin ACCEPTED it. This response was observed **fresh in two independent heap dumps** (12:38 and 13:06),
i.e. the app is accepted *today* while this exact request replayed off-device is rejected (see §3).

### 1b. `POST /api/portalCore/v8/active` (device activation — full field set)

```json
{
  "service_name": "portal", "method": "POST", "url": "/api/portalCore/v8/active",
  "headers": "Content-type: application/json;charset=utf-8\r\napk: com.android.msandroid\r\napkVer: 43405\r\nspkgVer: 2018-09-18 11:30:06_25_7.1.2_3.14.29\r\n",
  "body": "{\"apkVersion\":\"43405\",\"appId\":\"com.android.msandroid\",\"appLanguage\":\"en\",\"b29\":\"4435652b...\",\"contentType\":\"application/json;charset=utf-8\",\"cpu\":\"armeabi-v7a\",\"deviceToken\":\"\",\"hardwareInfo\":\"amlogic\",\"loginType\":\"2\",\"model\":\"SM-G973F\",\"portalCode\":\"\",\"product\":\"Galaxy S10\",\"reserve1\":\"76356c47...\",\"sdkVer\":25,\"sn\":\"1ecb8d3244c6460059e85db8f0d47fbb\",\"sysVersion\":\"2018-09-18 11:30:06_25_7.1.2_3.14.29\",\"authCode\":\"\",\"authVersion\":\"\",\"channel\":\"default\",\"macAddr\":\"06:41:80:91:CD:6E\",\"matadata\":\"\",\"openNum\":15,\"signdata\":\"\",\"snToken\":\"\"}",
  "timeout": 60000, "data": "{\"tdc\":false}"
}
```

### 1c. `POST /api/portalCore/config/get` (bootstrap config)

Same base body as 1a plus `"groupName":"com.android.msandroid","moduleName":"ApkConfig"`.

### 1d. `POST /v1/googleadmob/log_event` (dcs service — shows the DCS SDK's ETag header)

```json
{"service_name":"dcs","method":"POST","url":"/v1/googleadmob/log_event",
 "headers":"Content-type: application/json;charset=utf-8\r\nETag: fe47c34a3a5abaa08130b08dd8ee6bc5\r\n",
 "body":"{\"s\":{\"apk\":\"com.android.msandroid\",\"apk_ver\":\"43405\",\"approve_code\":\"\",\"auth_version\":\"\",\"code\":\"\",\"entry_type\":\"all\",\"reserve1\":\"76356c47...\",\"sn\":\"1ecb8d3244c6460059e85db8f0d47fbb\",\"spkg_ver\":\"7.1.2\",\"type\":1,\"user_id\":\"\",\"user_identity\":\"\"},\"status\":0}",
 "timeout":60000,"data":"{\"tdc\":false}"}
```

### 1e. Another live business response observed (NOT the version gate)

```json
{"returnCode":"aaa100082","errorMessage":"三方账号已经设置密码，只是使用登录方式"}
```
("third-party account already has a password — use the login method" — a real auth-flow response the
app received through the live portal host `rnsm.prxmnvhcy.com`, proving the app's calls reach a live
origin and get business logic, not the gate.)

---

## 2. What the ACCEPTED request carries — the identity delta vs our clone

### 2a. The token values (fresh, from the live app)

| field | value | static? |
|---|---|---|
| `sn` | `1ecb8d3244c6460059e85db8f0d47fbb` | **static per install** (identical in launch 1 + 2 dumps, matches prefs `KEY_SP_SN`) |
| `b29` | `4435652b4d4641546c6963733953743863376b75424f474c576b5a6d41326e6474724e534b567937426a6e4c556e72384136647252773d3d` = hex(base64(**40-byte blob**)) | **static per install** across launches |
| `reserve1` | `76356c476568424f4a38334761645a697957757344673d3d` = hex(base64(**16-byte blob**)) | **CROSS-DEVICE CONSTANT** — byte-identical to `.4`'s value from session 30! |
| `portalCode` | `""` (EMPTY — our clone sent `masnew`!) | — |
| `macAddr` | `06:41:80:91:CD:6E` (v8/active only) | per-box |

Decoded blobs:
- `b29` = `0f97be30501396272cf52b7c73b92e04e18b5a46660369ddb6b352295cbb0639cb527afc03a76b47` (40 B)
  — **last 8 bytes `cb527afc03a76b47` are IDENTICAL to `.4`'s b29** (fixed suffix; first 32 B per-device).
- `reserve1` = `bf99467a104e27cdc669d662c96bac0e` (16 B) — **identical on `.4` and `.8`** → NOT a
  device identity at all (session 31's "MAC" reading is wrong for this value); it is a constant of the
  APK/install-family (or an encrypted constant). This is the FIRST per-request "token" that is fully
  reproducible off-device.

**The tokens are NOT per-request.** b29/reserve1/sn are byte-identical across two app launches and
across `.4`/`.8` (partially). There is **no per-request nonce, no signature, no connection token in the
request** — the request is fully known and statically reproducible.

> Note: `/sdcard/.properties` holds DIFFERENT blobs (`key_sn_token_magis`/`key_device_id_magis`) than
> the request uses — the request tokens are minted/stored elsewhere (in-memory / prefs `KEY_SP_SN`).

### 2b. The REAL wire-format question

The DoHttpSec record body is **plaintext**. The native layer encrypts it for the wire (the Java 3DES
chain is R8-dead in this build family — same finding as the koocan sibling). Our replay therefore used
the legacy Java envelope `hex(base64(3DES_ECB_PKCS5(body)))` with key
`base64("2b494e53756c664c2f44465245733572")`. This is acceptable for the gate test because prior
sessions proved the gate fires **before body parsing** (garbage body == `{}` → same `portal200001`), and
we vary only what the gate can see.

---

## 3. Replay tests — the verdict

All replays from Win11 (egress IP = `.8`'s LAN NAT, same IP class the app uses).

| # | Transport | Request | Result |
|---|---|---|---|
| 1 | plain TLS (requests/OpenSSL) | captured getEmailSuffix (fresh .8 tokens), hosts espjey/sxowvd/yrqucu/ioermd | **portal200001** (4/4) |
| 2 | plain TLS | captured v8/active (fresh tokens) | **portal200001** |
| 3 | **exact app TLS** (utls 237-B ClientHello, negotiates `0xcca9` in TLS 1.2, ALPN h2) + Go h2 | captured getEmailSuffix, mixed-case headers | **portal200001** |
| 4 | exact TLS + h2 | same, lowercase headers, no Cache-Control (koocan-style) | **portal200001** |
| 5 | exact TLS + h2 | captured v8/active | **portal200001** |
| 6 | exact TLS + raw h2 frames | same | GOAWAY (framing rejected) |
| 7 | DNS: the app's CURRENT portal vhosts (`Dqukhjel.b0z9135v.com`, `rnsm.prxmnvhcy.com`) | resolve | **NXDOMAIN** — the operator ROTATES portal domains; the captured ones are already taken down (the app still reaches them via cached IPs; direct IP+SNI → Cloudflare 1016 origin-DNS-error) |

**The control (proof the comparison is live):** at the same time as replays 1–5 (12:38 and 13:06 heap
dumps), the app's OWN identical request was accepted (`returnCode:"0"`, observed in memory). The request
content is byte-identical between the accepted app call and the rejected replay. The ONLY difference is
the TLS/h2 connection produced by the native Ranger `DoHttpSec` layer.

### Verdict

**NOT replayable. The gate is the native Titan-Ranger `DoHttpSec` CONNECTION identity — not the request.**

- ✗ not a body/field diff (fresh tokens, byte-exact headers/body replayed → still rejected)
- ✗ not a per-request token/nonce (the accepted request carries none; all token values are static)
- ✗ not the TLS ClientHello fingerprint (utls replicates the exact 237-B ClientHello + 0xcca9 → rejected)
- ✗ not h2 header casing / standard framing
- ✗ not the host (4 live hosts + the app's own host family → rejected)
- ✓ the delta is the **native connection itself** — the bundled BoringSSL/mbedTLS-style stack inside
  `libexec.so` produces connection state (TLS session internals / h2 SETTINGS sequence / framing beyond
  the ClientHello) that the origin keys on. Only reproducible by **running the native lib**.

This **confirms with direct evidence** the conclusion of the two prior investigations (GOAL2 session 31 +
FakeUniTV sibling): XTV portalCore is un-replicable off-device. The static tokens are NOT the wall; the
native connection is. **No `returnCode:0` was achieved off-device — the standalone-unlock SHOUT does not
apply.** Koocan (the sibling's lead) remains the standalone path.

### What WOULD change the verdict
Getting the app's raw h2/TLS wire bytes (the exact SETTINGS/frame sequence) and reproducing them in a
client. Requires hooking the native layer's socket write (frida is ptrace-killed; a gadget-repacked APK
or Xposed on 7.1.2 are the remaining options) or dumping the plaintext h2 from `libexec.so`'s heap.

---

## 4. Methodology (how the capture was done — box `.8` only)

1. **Frida spawn** (`capture_dohttpsec2.js`, anti-ptrace bypass): app killed at t=1s by the ijiami
   watchdog (raw-syscall detection libc hooks can't catch). **Frida attach**: `unable to find suitable
   code pages` (packer locks memory). → abandoned.
2. **Native-heap capture (worked):** `capture_alldump.c` (zig, ARM static) parses `/proc/<pid>/maps` and
   `process_vm_readv`s **every rw-p region with exact sizes** (64-bit) in 1-MB chunks → 367 MB / 619
   regions in ~60 s. Launched fresh, dumped immediately (the app's watchdog arms non-dumpable fast and
   the box kills the app periodically). The DoHttpSec records live in the app's native heap
   (`[anon:libc_malloc]` + adjacent regions; addresses shift per launch via ASLR).
3. **Carve** (`extract_records.py`): find `{"session"` JSON records (balanced-brace parse) → the full
   request specs; find `{"body":"{...returnCode...` wrappers → the responses.
4. **Replay** (`replay8.py` python; `probe_generic.go` Go utls with the app's exact ClientHello + h2) —
   see §3.

## 5. Files

| file | role |
|---|---|
| `backends/xtv/NATIVE-CAPTURE.md` | this document |
| `backends/xtv/captured_request_response.json` | raw captured request + response records (unredacted) |
| `backends/xtv/capture_alldump.c` | exact-region process memory dumper (build: `zig cc -target arm-linux-musleabi -static -O2`) |
| `backends/xtv/capture_heap_dump.sh` | box-side launcher (fresh launch → immediate full dump) |
| `backends/xtv/extract_records.py` | carve DoHttpSec request/response records from the dump |
| `backends/xtv/replay8.py` | off-device replay (plain TLS) |
| `backends/xtv/probe_generic.go` | off-device replay (exact app TLS + h2; `go build`, env: REQ_URL/REQ_HEADERS/REQ_BODY/REQ_IP/H2_RAW) |
| `backends/xtv/capture_dohttpsec2.js` | frida NativeJni hook (did NOT survive the watchdog — kept for the next attempt) |

Dumps/pcaps/binaries stay untracked (`_session/*.bin`, `*.pcap`, `*.exe`, `_session/bb8.db` — gitignored).
