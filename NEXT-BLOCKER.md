# Next Blocker — XuperPlugin portalCore

## Status (2026-07-29 session 14 — lever close)

**Dalvik/SNI/static-DES host discovery exhausted.** Notice hosts solved without DES.
**Unidbg lever fixed:** exported `JNI_OnLoad` stubs to `0x12043544`; Thumb `callFunction(0x43545)`
works; harness can force `JNI_VERSION_1_6` — but **natives are not registered** (init still
hits NULL `vtable+0x40` → `kill()` retry). **Next:** fix that object/vtable so JNI completes
naturally → `N.b2b` → DES/portal domain.

Full log: [`SESSION-2026-07-29.md`](SESSION-2026-07-29.md). Handoff: [`HANDOFF.md`](HANDOFF.md).

---

## Session 14 — unidbg lever fix (2026-07-29 ~17:00–17:50)

| Wrong assumption | Reality |
|------------------|---------|
| Export @ `0x1203725d` = anti-tamper to PC-skip | **`b.w #0x12043544`** — real JNI body |
| `0x1202e39d` = hang loop | **`open("/proc/self/wchan")`**; V50 PC-skip broke open |
| Permanent `bx lr` @ stub | Aborts JNI when LR = unidbg sentinel |
| `callFunction(0x43544)` | Even = ARM → bogus SWI `0x3b5f0`; use **`0x43545`** |
| Forced `JNI_VERSION` = done | **No** — `RegisterNatives` skipped; `N.l`/`N.b2b` missing |

**Working recipe:** `_scratch/Unpack.java` + `_scratch/run_lever_remote.py` on `.40`.
After load: patch `push.w`@`0x12043548`; soft-skip null `blx r1`@`0x120370c6`; soft-ret
`kill()`@`0x12037b80` (2nd hit → force `0x10006`+sentinel). Log shows forced SUCCESS.

### Next (ordered) — current

1. **P0:** Populate singleton/vtable so `[obj+0x40]` is callable — init check must pass without `kill()`.
2. Confirm `RegisterNatives` for `s/h/e/l/l/N` (`l`, `b2b`).
3. `N.b2b(ijiami.dat)` → dump DEX; scan DES key + portal FQDN.
4. Plugin probe until `returnCode=0` → product path.

**Do not:** PC-skip wchan `0x1202e39d`; prefer UnpackV50 load path; even JNI offset; re-hunt dalvik hosts; re-brute notice DES.

---

## Session 14 — heap pivot + audit of prior mistakes (2026-07-29)

| Prior mistake | Correction |
|---------------|--------------|
| Treat `34fhwevf` / tcpdump SNI as portal API host | Heap: `main_addr=34fhwevf…` on **signed CDN URLs**; HTTP/HTTPS `/api/portalCore/*` → **404** |
| Re-added `sgyc` to bootstrap | Heap: `p2p_main_addr=sgyc…` WebSocket tracker only |
| TeleLatino / Brasil hosts first in XTV bootstrap | Probed with XTV `userToken` → misleading 403; moved to `SISTER_APP_HOSTS` |
| `d1t5kow2rdtotr.cloudfront.net` in bootstrap | `spared_addr` CDN only |
| Comment: wire path encrypted | **Wrong** — paths plaintext; **body** 3DES (matches NEXT-BLOCKER) |
| Blocker: “OkHttp TLS fingerprint” | Same client gets JSON `portal200001` — **version gate**, not TLS mimicry |
| unidbg as only host-discovery path | **Heap scan** finds API paths + CDN map in minutes on `.4` |

### Heap artifacts (`.4`, PID live Home)
- Tooling: `_session/heap_hostscan.sh`, `_session/heap_extract.sh`, `_session/heap_portal.txt`
- `getLiveDataSuccess`, `api/portalCore/v15/getSlbInfo`, v6/v8/v3 paths
- Failover config strings: `emowvv.dqiswip4.xyz|espjey.ysnihrwtg.com`, etc.

### Plugin changes
- `PORTAL_BOOTSTRAP_HOSTS` trimmed (~45 XTV-relevant); `SISTER_APP_HOSTS` separated
- `probePortalBootstrap()`: **`getSlbInfo` line per host** before auth/live

### Session 14 cont — spkgVer + TeleLatino + BBDatabase (2026-07-29 ~16:20)

| Action | Result |
|--------|--------|
| Patch `spkgVer` = sysVersion stamp + fresh userToken; reinstall + probe | Still **`portal200001`** on dfcsq/emowvv/espjey/… |
| `BBDatabase.EventDbModel` `app_api` row | `domain\|DES=Sz0JjjU4…` for **`/notice/api/get_notice`** (status 50012) |
| TeleLatino `classes.dex` | SecNeo: **7 class_defs**, 20MB strings — jadx useless for domain crypto |
| Notice-context DES key tries | 33 keys, **0 hits** |
| GET `/notice/api/get_notice` on pool | 403/404 — no useful body |

**Correction to earlier narrative:** decrypting `domain|DES` yields the **notice** API host
(telemetry/notices), which may share DES key material with portal domain config but is not
itself the portalCore SLB hostname.

Artifacts: `_session/BBDatabase.db`, `_session/TeleLatino.apk`, `_session/heap_domain.txt`,
`scripts/parse_bbdatabase.py`, `scripts/des_notice_keys.py`.

---

### Session 14 cont — heap notice ctx ([heap dump near DES blob](959e6e9f-889e-40d6-8ff9-dde4ca62a283))

**Resolved without DES key:** live heap shows plaintext notice URLs:

- `http://zxiws.tcgwhnvym.com/notice/api/get_notice?pkg=…&v=43405&sn=…`
- `http://nxiqj.jgrqyxupl.com/notice/api/get_notice?…`

`Sz0JjjU4…` remains ciphertext in the same cluster as `Host: zxiws…` + BB `httpStatus=50012`.
Decrypt key still useful for EPG blobs (`4hv+…`, `MP5T…`) and any portal domain|DES, but
**notice host discovery is done** — both hosts already in bootstrap (CF 522/timeout historically).

Artifact: `_session/heap_notice_ctx.txt`

---

**Static DES expand ([notice host decrypt and probe](ef005fa5-6c6f-4d8b-bd8b-83a522482c7f)):**
**MISS** — 5,000 keys × 3 blobs, 0 last-block hits, 0 encrypt-matches to known notice hosts.
Script: `scripts/des_bruteforce_expand.py`. Key is **not** derivable from envelope/config fields;
continue unidbg / native only.

---

**Portal host hunt ([portal host native heap hunt](3374492b-6628-4ec6-8291-f6d78989dde0)):**
**Conclusive negative** — no new portal API FQDN. Heap has relative `api/portalCore/*` only;
cold SNI = known pool (`emowvv`/`sxowvd`) → `portal200001`; `sfgknh` OkHttp peer → portalCore **403**.
Report: `_session/portal_host_hunt.txt`. Dalvik-only host discovery is exhausted.

---

**Unidbg v50 (superseded by lever fix above):**
`_scratch/UnpackV50.java` multi-level hooks stalled on wchan PC-skip regression.
**Use `_scratch/Unpack.java`**, not V50, for continued work.

---

### Parallel session 14 sweep — closed

| Track | Result |
|-------|--------|
| Heap near DES blob | Notice hosts = `zxiws` / `nxiqj` (plaintext); DES still needed for other blobs |
| Static DES 5k keys | MISS |
| Portal host hunt | Conclusive negative (dalvik/SNI) |
| Unidbg lever | Real JNI entered; forced VERSION; **vtable+0x40 still blocks RegisterNatives** |

---

## Status (archive — session 12)

**Full API pipeline mapped. 3DES body crypto proven against real servers. 65+ hosts
probed across 4 apps, 2 regions — version gate is universal. DES domain key encrypted
inside ijiami libexec.so, recoverable via unidbg (36 iterations, one self-decryption
trap remaining). Sister apps revealed proto structure but no weak app. frida blocked
by ijiami v4. Plugin builds + deploys + probes 65 hosts from device.**

---

## What's been accomplished (sessions 1–12)

### API format — fully recovered
- portalCore endpoints: `getAuthInfo(v9)`, `getLiveData(v6/v7)`, `getSlbInfo(v15)`,
  `getColumnContents(v3)`, `getPropertiesInfo` (TeleLatino only)
- EPG: `epg/v2/getLineUps`, `epg/v2/getAllMatch`, `epg/v2/getTeamEvent`
- Request envelope captured from live app heap: `{apkVersion, appId, b29, reserve1, sn,
  portalCode, userId, userToken, columnId, dataVersion, pageNum, pageSize, ...}`
- Response chain: `GetLiveDataResultData` → `liveAddressList` → `LiveAddress` → `playCode`
  → signed CDN playlist URL (cdsr/bmagon/yuwc) → open magloud segments
- CDN token format: `app_id=...&scheme=md5-01&media_code=...&expired=...&token=<32hex>`
- **Tokens are server-signed** (`sign_type=cfl/cs/goog`), not client-forgeable

### Body crypto — 100% proven
- Algorithm: `toHex(Base64(DESede/ECB/PKCS5(plaintext)))`
- Key: Base64-decode(`2b494e53756c664c2f44465245733572`) = 24 bytes
  `d9be3de1ee77ef9e9cebae1cd9fe38e3ae76e39ef7df9ef6`
- OpenSSL pipeline matches XuperCrypto byte-for-byte → validated against real servers
- Paths are PLAINTEXT, only body is encrypted
- Extra headers: `apkVer`, `spkgVer`, `apk`

### Host discovery — 65+ hosts probed
| Pool | Hosts | Result |
|------|-------|--------|
| XTV old pool (espjey, sxowvd, ...) | ~20 | `portal200001` version-gate |
| XTV live wire (rokbd, vgwbm, ...) | ~10 | 403 CF WAF |
| Brasil TV heap (bxvxjj, cqrkgyod, ...) | 17 | 403/404/portal200001 |
| TeleLatino DES-resolved (joqotx, wetc) | 2 | 403 CF WAF / 404 |
| Heap dump (banamyi, bmagon, cdsr, ...) | 27 | portal200001/403/404 |

**Conclusion: version gate + CF WAF are universal.** The app bypasses both through
its native HTTP layer (libexec.so), which our curl/OkHttp cannot replicate.

### Portal code — app-specific, not a bypass
- XTV: `portalCode="masnew"` (plaintext string)
- TeleLatino: `portalCode="87SS0skuAxztSQOny3WECQ=="` (hex of base64)
- Brasil TV: `portalCode="masnew"` (same as XTV)
- Different portal_code does NOT bypass version gate

### User identity
- XTV logged-in: `userId=169355704`, `userToken=<UUID>` (rotates per auto-login)
- XTV visitor: `userId=694951876` (device-linked)
- TeleLatino free: `userId=945257240`, `key_user_identity=1`
- userToken captured fresh from live heap each session

---

## What was learned from sister apps

### Brasil TV (com.interactive.brasiliptv)
- **Same ijiami protection** as XTV (libexec.so in /data/data, ijiami.dat in APK)
- **Brazilian channel pool** (different portals, same API format)
- **Hardware-locked** on Android 4.4.2 HTV3 box (`.37`)
- **New APK versions auto-install** as updates (app self-updates)
- DEX is a stub (13KB) — real code encrypted in ijiami.dat
- 17 new portalCore hosts extracted from process memory on `.4`

### TeleLatino (com.global.latinotv)
- **SecNeo protection** (different vendor, same category as ijiami)
- **20MB classes.dex** — clean from ijiami/APK perspective, but SecNeo encrypts code
- **String constants NOT encrypted** — `domain_DES=`, `DESedeKeySpec`, `SecretKeySpec`,
  `IvParameterSpec`, `getDomain`, `setDomain`, `domainKey` all readable
- **DESede/CBC mode** for domain config (different from body DESede/ECB)
- **App runs without login** — reached HomeActivity as free user on `.4`
- **`api/portalCore/v7/getLiveData`** (v7, not v6 like XTV)
- **`portal_code=87SS0skuAxztSQOny3WECQ==`** (hex(base64))
- **`getPropertiesInfo` endpoint** (TeleLatino-only)
- **baksmali decompiled only 7 stub classes** (SecNeo wrapper)
- **EventDbModel** stores `cipherStr` — encrypted events, maybe domain data

### YouCine
- **ijiami-protected** (same as XTV/BrasilTV)
- **Vendor cert:** `CN=xxl, OU=OTT, O=XXL` (different vendor)
- Not installed (paywall required)

### Cross-app summary
All 4 apps share the same codebase (com.interactive.brasiliptv → obfuscated s/h/e/l/l),
same API format (portalCore v6-v9), same 3DES body crypto, same device envelope fields.
Different protection vendors (ijiami vs SecNeo), different regional pools, different
portal_code values. **No app is "weak" — all have encryption/protection layers.**

---

## DES domain key — the one blocker

### What we know
- Three DES domain blobs in config: `Sz0JjjU4YRgGRpH1paF7wlkgQ43Df/4y` etc.
- All share suffix `lkgQ43Df/4y` = ".com" + PKCS5 padding (8 bytes)
- Algorithm: **DES/ECB** (confirmed: XOR shows identical last 8 bytes)
- Key: 8 bytes (single DES, 56-bit)
- Different from body 3DES key (24 bytes for DESede/ECB)
- TeleLatino uses DESede/CBC (different from XTV's DES/ECB for domain config)
- 2,352 static key candidates tested → zero matches
- Key encrypted inside libexec.so's ijiami protection layer

### Attempted recovery methods
| Method | Result |
|--------|--------|
| Static binary analysis (libexec.so) | Key encrypted in ijiami, not plaintext |
| Body key truncation (DES-ECB) | Garbage — different key |
| XOR cryptanalysis | Confirmed DES/ECB, 8-byte key, .com suffix |
| Live process diff | Only ARM relocations, no key material |
| Full memory dump (.4 root) | Found hosts but key not in plaintext |
| Frida (spawn/attach/API) | ijiami v4 blocks agent injection at kernel level |
| Frida de-signatured (335 string replacements) | Broke protocol handshake |
| Florida/hluda (pre-built) | No ARM binaries available |
| Unidbg off-device emulation | **36 iterations, closest approach** |

### Unidbg status (49 total iterations — session 13: 9 more runs)

- ✅ libexec.so fully loads (all 63 ctors pass with fixes)
- ✅ Singleton classname buffer populated at 0x120868f0
- ✅ GOT[0x12082340] → 0x120868e0 → 0x120868f0 pointer chain: WORKING
- ✅ Sanity check returns 0, CTOR-PATCH fires, CTOR12-SKIP fires
- ✅ .init_array parsed: 63 ctors, **ctor[12]=0x12037289 = anti-tamper function containing crash**
- ✅ **CRASH-BYPASS (v7, approach G):** brute-force jump to safe PC 0x1202e2b7 worked —
  execution reached deeper into JNI_OnLoad (LR=0x1202e4bb) before secondary crash.
  Proves bypass is possible.
- ✅ **Decrypted instruction bytes dumped (v9):** `00bf 72b9 b0b5 084d` at 0x1203725c.
  First instruction is NOP (0xbf00), second is CBNZ loop — self-decryption confirmed working,
  anti-tamper code is a loop that branches to NULL.

- ❌ **Blocker: crash at 0x1203725c** — **intentional anti-tamper, reached via multiple paths.**
  #### Approaches tried (session 13, runs 41-49):
  | # | Approach | Result |
  |---|----------|--------|
  | v1 | bx lr via mem_write in CodeHook | ∞ loop: LR=0xffff0000 (unidbg sentinel), dispatch re-enters |
  | v2 | POP {PC} from stack | savedLR=0x0, jumps to NULL → FETCH_UNMAPPED |
  | v3 | Auto-map unmapped reads + NULL page | FETCH_PROT at 0x0 (loaded NULL function pointer) |
  | v4 | BL-SKIP at suspected caller 0x120370c8 | Never fired — wrong call path |
  | v5 | CTOR12-SKIP at 0x12037288 | Hook fired, ctor skipped, crash still happens (JNI_OnLoad path) |
  | v6 | Pre-map 0x1000-0x1000000 (16MB) | No help — crash is FETCH from computed NULL, not unmapped read |
  | v7 | Brute-force jump to safe PC 0x1202e2b7 | **BEST RESULT:** bypass fired, reached deep JNI_OnLoad, secondary crash at stacked code |
  | v8 | Capture LR at ctor entry, use at crash | LR always 0xffff0000 (sentinel), fallback gives 0x0 |
  | v9 | Dump decrypted bytes + NOP | Decrypted bytes: `00bf 72b9 b0b5 084d` — CBNZ creates ∞ loop with NOP |

  #### Key findings:
  - Crash site reached from MULTIPLE paths: init_array ctor dispatch AND JNI_OnLoad call chain
  - LR always 0xffff0000 = unidbg's init_array dispatch sentinel — no real call frame
  - After self-decryption, code at 0x1203725c is: NOP + CBNZ (loop) + branch-to-NULL
  - Pre-mapping memory doesn't help — the code INTENTIONALLY computes NULL pointer and branches to it
  - Stack-smashing bypass (v7) proves the anti-tamper CAN be skipped, but cascading checks cause secondary crashes

  #### Next approaches to try:
  1. **Ghidra disassembly** of decrypted code (0x1203725c-0x12037270) to understand exact instruction sequence
  2. **Multi-level bypass chain:** hook each anti-tamper site in sequence (0x1203725c, then 0x1202e4bb, etc.)
  3. **Ctor-level blanket skip:** hook ALL 63 ctors and skip ones near crash range, let JNI_OnLoad path through
  4. **Unicorn native API:** use `uc_mem_write` directly (bypass unidbg Memory tracking) to write bx lr at crash site BEFORE code executes — need to check if Unicorn2Backend exposes raw uc_engine
  5. **Pivot to .37:** extract native libs via telnet, static analysis for DES key (bypasses unidbg entirely)

  After fix: JNI_OnLoad returns JNI_VERSION_1_6 → RegisterNatives fires → N.l + N.b2b
  callable → call N.b2b(ijiami.dat) → decrypted DEX → extract DES key → decrypt
  portalCore host → returnCode=0 → full pipeline live.

---

## Plugin state
- **Build:** `export JAVA_HOME="C:/Program Files/Microsoft/jdk-17.0.18.8-hotspot"`,
  `./gradlew :app:assembleDebug --no-daemon`
- **Deploy:** uninstall old, install new, `am start ConfigActivity`, tap Test Session
- **probePortalBootstrap():** 65 hosts, dual getAuthInfo+getLiveData, honest [SYN]/[V-GATE]/[HTML] tags
- **Config:** userToken, portalCode, userId, b29, reserve1, appId all from live captures
- **Result:** All hosts version-gate (portal200001) or CF-WAF-block (403). Same as curl from Win11.
  Plugin's OkHttp cannot replicate native TLS fingerprint.

---

## .37 HTV3 box
- **Telnet:** 192.168.100.100:2323 / 192.168.3.109:2323 (Servers Ultimate, unstable)
- **Root:** SuperSU daemonsu broken (su symlink missing, can't write /system)
  KingRoot, TowelRoot, Framaroot all failed. No internet = TowelRoot blocked.
- **Apps:** XTV + Brasil TV + TeleLatino + YouCine installed
- **APKs recovered:** All 4 copied to Win11 at C:/Users/Nestor/Workspace/Xuper/brasiltv/
- **Useful for:** APK extraction (done), optional shell operations

---

## Next steps (ordered by impact)

### 1. Unidbg — NOP the crash at 0x1203725c via caller hook ⭐⭐⭐
Three approaches, try in order:
- **A) Ghidra disassembly** on .40: find the BL/BLX that calls 0x1203725c, hook that
  CALL site and skip it (prevent the crash function from being entered at all).
- **B) unicorn native mem_write:** use `emulator.getBackend().mem_write()` which
  may bypass unidbg's memory tracking and write bx lr directly to 0x1203725c.
- **C) unidbg Memory.patch():** check if `emulator.getMemory().patch()` or similar
  API exists for runtime code patching within unidbg's memory model.

After fix: JNI_OnLoad reaches normal return → RegisterNatives fires → N.l + N.b2b
callable → call N.b2b(ijiami.dat) → DEX decrypted → DES key extracted.

### 2. Decompile TeleLatino DEX for DES key derivation code
Re-download baksmali from: `https://bitbucket.org/JesusFreke/smali/downloads/baksmali-2.5.2.jar`
Decompile 20MB classes.dex. SecNeo doesn't encrypt strings — `domain_DES=`, `DESedeKeySpec`,
`SecretKeySpec`, `IvParameterSpec`, `getDomain`, `setDomain`, `domainKey` all readable.
The DESede/CBC domain decryption code may reveal key derivation algorithm.

### 3. XTV heap dump on .4 after channel switch
Root on .4, trigger getLiveData by changing channel, dump dalvik heap, search for
portalCore host adjacent to `/api/portalCore/v6/getLiveData` in memory. Already works
for TeleLatino (found joqotx/wetc). Do the same for XTV.

### 4. .37 native lib recovery via HTTP
Brasil TV native libs are world-readable: `cat /data/data/com.interactive.brasiliptv/lib/*.so`
(11.4MB). Start Python HTTP server on Win11, have .37 download and run analysis script
that searches for DES key patterns, then uploads results.

### 5. After DES key recovery
Decrypt domain|DES blobs → get XTV portalCore host → probe with plugin →
returnCode=0 → implement getColumnContents → getLiveData(channelId) →
M3uProxyServer refresh loop → continuous live TV.

---

## Files and locations

| What | Where |
|------|-------|
| Plugin source | `C:/Users/Nestor/Workspace/Xuper/XuperPlugin/` |
| Config (live values) | `XuperApiClient.kt` XuperConfig defaults |
| Brasil TV + sister APKs | `C:/Users/Nestor/Workspace/Xuper/brasiltv/*.apk` |
| Frida scripts | `C:/Users/Nestor/Workspace/Xuper/*.js` |
| Frida server | `/data/local/tmp/frida-server-arm` on `.4` |
| Session backup | `C:/Users/Nestor/Workspace/Xuper/_session/com.android.mgstv_data.tar.gz` |
| Libexec.so + ijiami.dat | `C:/Users/Nestor/Workspace/Xuper/XuperPlugin/_assets/` |
| Heap dumps | `C:/Users/Nestor/Workspace/Xuper/_session/*.bin` |
| .40 harness | `~/xtv-ghidra/harness/src/main/java/com/xtv/Unpack.java` |
| .40 assets | `/tmp/apkx/assets/ijm_lib/armeabi/libexec.so`, `/tmp/apkx/assets/ijiami.dat` |
