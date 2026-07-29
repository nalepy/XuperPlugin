# Next Blocker — XuperPlugin portalCore

## Status (2026-07-29 session 12)

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

### Unidbg status (the closest path — 40 total iterations)
- ✅ libexec.so fully loads (all 63 ctors pass with fixes)
- ✅ Singleton classname buffer populated at 0x120868f0 ("android/content/pm/ApplicationInfo"
  and "targetSdkVersion" strings — confirmed byte-by-byte via write watches)
- ✅ GOT[0x12082340] → 0x120868e0 → 0x120868f0 pointer chain: WORKING (FIX fires)
- ✅ Sanity check returns 0 (skip init path, no deadlock trap)
- ✅ CTOR-PATCH, CTOR-SKIP hooks confirmed firing
- ❌ **Blocker: crash at 0x1203725c** — **intentional anti-tamper trap, NOT self-decryption.**
  The instruction at 0x1203725c is VALID code that reads from unmapped memory when
  anti-tamper conditions aren't met. This is a DESIGNED crash, not corruption.
  - MEM_WRITE guard (run37-38): 256 writes caught + restored correctly. Crash still
    occurs because the instruction itself is designed to fault, not corrupted.
  - CodeHook skip (run34-35,39-40): Hook fires at 0x1203725c, advances PC by 2/4, but
    unicorn validates intermediate addresses and throws UC_ERR_INSN_INVALID at 0x1203725d
    (T32 instruction boundary between hook point and skip target).
  - mem_protect + mem_write bx lr: UC_ERR_NOMEM (unicorn native tracks memory, not unidbg)
  - **Fix needed:** Find the CALLER (BL/BLX) of 0x1203725c via Ghidra and NOP IT,
    OR use unicorn native uc_mem_write to write bx lr (0x4770) at the crash address,
    OR use unidbg Memory.patch() API for runtime code patching.
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
