# Next Blocker — Get the portalCore `startPlayLive` request format

## Goal

Make XuperPlugin self-sustaining: fetch fresh `(playlist-path, d-cookie)` pairs on
its own so live TV plays continuously (no manual cookie paste, no ~24s cap).

Those pairs come only from the **cert-pinned portalCore API**
(`espjey.ysnihrwtg.com`, `sxowvd.jzvqwcyor.com`, `yrqucu.czxenpyba.com`, ...).
Our plugin's OkHttp has no pinning, so it CAN call those hosts — we just need the
exact request: host, path, headers, and the 3DES-encrypted body fields.

See [ARCHITECTURE.md](ARCHITECTURE.md) for the full stream pipeline.

## Why plain jadx won't work (confirmed 2026-07-26)

The APK is **ijiami-packed** (v4). Outer `classes.dex` is a 14 KB stub with 4 classes
in package `s.h.e.l.l`:
- `A.java` — `AppComponentFactory` subclass, hooks `instantiateClassLoader`
- `S.java` — `Application` subclass, calls `DETool.loadDEso()` to decrypt `ijiami.dat`
- `N.java` — native helper, `System.load("libexec.so")`, provides `al()`, `b2b()`, `l()`, `r()`, `ra()`
- `C.java` — native callback `i(int)`

Real app class: `com.interactive.brasiliptv.app.AppWrapper` — loaded by N.al() after decryption.
Real code in `assets/ijiami.dat` (4.5 MB, encrypted) + `assets/ijm_lib/armeabi/libexec.so`,
decrypted into memory at runtime by `com.ijm.dataencryption.DETool`.

**DEX header wiping confirmed:** ijiami v4 wipes DEX magic bytes (`dex\n035`) from memory
after class loading. Scanning 512MB dalvik region space found 0 DEX headers. Both
BlackDex and DarkDex failed to recover DEX files.

**Current status (2026-07-26 ~02:00):**
All on-device DEX dumpers exhausted (BlackDex, DarkDex, memory scan). Next best path:
capture fresh s/t cookies via MITM, then probe portalCore API directly with 16 known hosts.

**UPDATE — session 2 (2026-07-26 ~09:00): blind-probe + MITM avenue CLOSED.**
- Re-analyzed retained captures: `s`/`t` stable, `d`=prefix+rotating-tail, PATH rotates
  per fetch; rotation source is NOT in any plaintext response. The 11 DarkDex
  "portalCore hosts" are ancillary plaintext services (ads/notice/EPG/update) +
  a plaintext `ws://` heartbeat (`sgyc.bfj1k2g4v.com/v1/imagine`). See ARCHITECTURE.md
  "Refined model" section. `23.94.64.155:30822` (plugin's apiHost) is DEAD (404).
- **Cold-start MITM disqualifier (decisive):** brought mitm up BEFORE launch, drove
  XTV into a live channel. Result: NO cdsr, NO seed, NO login on TCP 80/443 — only
  the `sgyc` ws (101, **0 data frames**, reconnecting ~12s) + ip-api. Player STALLED.
  Old captures only "worked" because the seed was fetched before mitm was applied.
  ⇒ **The streaming seed is genuinely cert-pinned (and/or QUIC/h3). MITM can NEVER
  capture it. Blind-probe is impossible — we have zero observations of that API.**
- **frida spawn+child-gating retried → same TimedOutError wall** (spawn coordination).
  Box has NO Magisk (system test-keys root) ⇒ LSPosed/Xposed not installable.
- **Remaining viable path = OFF-DEVICE only:** Ghidra + unidbg to emulate
  `assets/ijm_lib/armeabi/libexec.so` (435 KB, not stripped, decryptor; native fns
  registered via RegisterNatives — no clean export; 63 init_array ctors) and decrypt
  `assets/ijiami.dat` (4.56 MB) → recover DEX → JADX → read `startPlayLive`. HIGH
  effort, multi-session. All assets already extracted on `.40` at `/tmp/apkx/`.
  On-device runtime tools (frida/LLDB/QBDI/DBI) all fight ijiami anti-debug+fork.

**UPDATE — session 3 (2026-07-26 ~10:20): OFF-DEVICE EMULATION PIPELINE WORKS. One blocker left.**

Built the whole off-device rig on `.40` under `~/xtv-ghidra/`:
- Ghidra 11.3.2 headless + JDK21 (`~/xtv-ghidra/ghidra_11.3.2_PUBLIC`, `jdk21`).
- capstone/pyelftools venv (`~/xtv-ghidra/venv`) + analysis scripts (`~/xtv-ghidra/scripts/`).
- Maven 3.9.9 (`~/xtv-ghidra/maven`) + unidbg harness project (`~/xtv-ghidra/harness`).

Findings:
- **`libexec.so` is SELF-ENCRYPTING.** Static Ghidra is walled: exec segment entropy
  7.5–7.88 in quarters 1–3 (q0=5.4 loader stub); JNI_OnLoad bytes decode to garbage in
  both ARM and Thumb. The real code (JNI_OnLoad @ raw 0x3725c, RegisterNatives table,
  the .dat decrypt fn) is ciphertext at rest, unpacked by the 63 `init_array` ctors at
  runtime. Native code NEVER references `"ijiami.dat"` ⇒ the Java `DETool` reads the file
  and passes the buffer to a RegisterNatives method (name still encrypted).
- **unidbg emulation is the key and it RUNS.** Correct combo (critical): the published
  unicorn **1.0.15 native is broken** (split build, `undefined symbol: helper_div_i32`)
  on BOTH linux_64 and linux_arm64. Working matched pair = **unidbg-android 0.9.8 +
  unicorn 1.0.14** (self-contained 5 MB native). Extract `natives/linux_64/libunicorn_java.so`
  from the 1.0.14 jar onto `-Djava.library.path`. Runs on x86-64 `.40` directly.
  (unidbg 0.9.9 needs unicorn 1.0.15 API `reg_read(int)->long` → forces the broken native.)
- Under emulation the ctors run, **.text self-decrypts**, and real ARM/Thumb executes
  through the ijiami loader (verified: PC executing valid Thumb at 0x4001f804 and decrypted
  code at 0x402b6xxx; module base 0x40000000). A wide mem-scan dumped 1.8 MB of decrypted
  pages (`/tmp/apkx/mem_scan.bin` + `.idx`). Only tiny stub DEX (156/280 B) present so far —
  the **app DEX from ijiami.dat is decrypted only AFTER JNI_OnLoad**, which we can't reach yet.

**THE ONE REMAINING BLOCKER (session 3):**
An early ctor does `mmap2(start=0x4001e000, len=0x5e4f4, prot=RW, flags=MAP_FIXED|ANON)` —
ijiami munmaps its own .text mapping and re-maps decrypted code in place. unidbg **0.9.8's**
`AbstractLoader.munmap` throws `IllegalStateException: munmap aligned=0x5f000, start=0x4001e000`
because the unmap range is partial AND spans the gap between LOAD1 (ends 0x40054b1c) and
LOAD2 (0x400804f0) — a partially-unmapped range unicorn can't `mem_unmap`. This aborts the
ctor (→ `UC_ERR_FETCH_UNMAPPED`), so JNI_OnLoad returns JNI_ERR (0xffffffff) and the .dat
decrypt is never reached.

**Two fixes (either unblocks — pick one next session):**
1. **Working unicorn 1.0.15 native** → use unidbg-android **0.9.9**, whose `munmap` is the
   newer robust version (splits blocks, handles removed==null / adjacent regions). Build the
   `zhkl0228/unicorn` fork's `libunicorn_java.so` for linux_64 (cmake/gcc, self-contained).
   Cleanest: one C build unblocks the robust loader.
2. **Patch unidbg 0.9.8 from source** (needs JDK8 — `Module` ambiguity blocks JDK17 build):
   make `mmap2` MAP_FIXED clamp the `munmap` to actually-mapped pages (skip gaps) and/or
   split MemoryMap blocks like master does. Rebuild `unidbg-android`, relink harness.
   Master source is already cloned at `~/xtv-ghidra/unidbg` (its `munmap` is the robust one
   to port back).

Once past the remap: let JNI_OnLoad finish → hook RegisterNatives (unidbg logs the method
table) → call the decrypt native on the `ijiami.dat` bytes → dump the real app DEX → jadx →
grep `startPlayLive`. Fallback: after JNI_OnLoad, re-run the wide mem-scan; the app DEX
(multi-MB, `dex\n035` magic, real `file_size`) will be sitting decrypted in memory.

Harness entry: `~/xtv-ghidra/harness/src/main/java/com/xtv/Unpack.java`
Run: `cd ~/xtv-ghidra/harness && CP="target/classes:$(cat ~/xtv-ghidra/cp.txt)" && \
  java -Djava.library.path=~/xtv-ghidra/nativelib -cp "$CP" com.xtv.Unpack`

**UPDATE — session 4 (2026-07-26 ~16:00): session-3 blocker SOLVED + 2 more traps cracked. New 4th blocker.**

The documented mmap/munmap blocker is fixed and `loadLibrary()` now fully succeeds (all 63
ctors run, returns `base=0x12000000`); execution reaches `JNI_OnLoad`. Three durable fixes,
all committed to the unidbg source tree on `.40` (grep `XTV-PATCH`):

1. **munmap gap-spanning MAP_FIXED remap → FIXED.** Key correction to session-3's premise:
   - unidbg **master (`0.9.10-SNAPSHOT`, cloned at `~/xtv-ghidra/unidbg`) ships a working
     self-contained `unicorn2` backend** (`backend/unicorn2`, prebuilt `libunicorn.so`,
     `nm -D` confirms `helper_div_i32_*` defined, all 31 JNI entry points). **No unicorn
     native build was needed.** But `AndroidEmulatorBuilder` silently uses the OLD broken
     backend unless you explicitly register **`Unicorn2Factory`** — done in `Unpack.java`.
   - master's "robust" `munmap` still does NOT handle ijiami's case (it only splits when
     `start` == a tracked block base; ijiami starts mid-block and spans TWO segments across
     the LOAD1/LOAD2 gap → always hits the throwing branch). Real patch required:
     `AndroidElfLoader.clampedMunmapForFixedRemap()` — unmaps/re-tracks only the overlapping
     sub-ranges across N blocks, skipping gaps. Fires once for the remap, unions to `0x5f000`.
   - Building unidbg from source needs **JDK8** (JDK17/21 hit the `Module` ambiguity even on
     master) → `~/xtv-ghidra/jdk8u492-b09`; build with `-Dgpg.skip=true`. Installed to `~/.m2`
     as `0.9.10-SNAPSHOT`.
2. **`kill(0, SIGKILL)` anti-emu trap → FIXED.** A ctor calls `kill(0,9)` (never returns on
   real Linux); unidbg returned 0 → fell through into dead bytes → `UC_ERR_INSN_INVALID`.
   Patched `AndroidSyscallHandler.kill()` to throw when `sig==9` w/ no handler.
3. **Debugger deadlock → FIXED.** No SLF4J binding ⇒ NOP logger `isWarnEnabled()`=false ⇒
   `handleEmuException` took the interactive-debugger branch, blocking on `Scanner.nextLine()`.
   Added `slf4j-simple:2.0.16` to harness pom (INFO level routes exceptions to `log.warn`).
   Also: always launch with `</dev/null` + `timeout` so it can never block on stdin.

**THE 4th BLOCKER (ijiami anti-tamper — genuine RE, not a unidbg limit):**
With all 3 fixes, `JNI_OnLoad` runs: it's a thunk → real fn that calls `(*vm)->GetEnv(...)`,
then derefs a GOT-cached singleton (`GOT@0x12082340 → 0x120868e0 → NULL`) and calls vtable
slot `+0x40` → null-deref → returns `0xffffffff`. A ctor at `.so`-relative **`0x3a1d8`** does
an obfuscated integrity check: reads that singleton's fields, `getpid()` (raw `svc`, `r7=0x14`),
opaque-predicate integer arithmetic vs a threshold, opens `/proc/self/status`, hits the `kill`
trap. The singleton's real init appears gated behind this check passing cleanly in a genuine
(non-emulated) process. App DEX NOT decrypted yet (mem-scan `/tmp/apkx/mem_scan2.bin` shows
only the same 156B/280B stub fragments).

**RECOMMENDED NEXT STEP:** in the harness add `hook_add(UC_HOOK_MEM_WRITE)` watching address
`0x120868e0` — catch whoever is *supposed* to allocate/populate the singleton, identify the
gating condition, and stub `/proc/self/status` + getpid + the opaque predicate so the check
passes. Then JNI_OnLoad completes → RegisterNatives → call the `.dat` decrypt native → app DEX
→ jadx → `startPlayLive`. Reusable tool: `~/xtv-ghidra/scripts/scan2_disasm.py` (thumb-aware
capstone disasm vs the mem-scan). Run logs `run3.log`–`run6.log` show the full progression.

## Two tracks (either one unblocks us)

### Track A — Capture plaintext portalCore by defeating cert pinning (fastest)

If we see ONE real `startPlayLive` request/response in the clear, we have the
format immediately — no DEX archaeology.

The blocker is app-level cert pinning (mitm CA is already trusted by the box for
non-pinned traffic — that's how we decrypted playlist/segment traffic). Options:

1. **BlackDex / FART / Youpk on-device unpackers** — they hook the ART class
   loader from a *separate* helper app and dump decrypted DEX. Because they don't
   inject frida into the target, they often dodge ijiami's anti-frida. Try BlackDex
   first (self-contained APK, no PC frida-server):
   ```
   adb -s 192.168.100.4:5555 install BlackDex.apk
   # open BlackDex on box, select com.android.mgstv, dump
   # output: /sdcard/Android/data/io.va.blackdex/... *.dex
   adb pull <dumped_dex_dir> /tmp/xtv_dex/
   ```
   Then Track B step 2 (jadx the dumped dex) — this also yields the pinning +
   request-encryption code.

2. **Frida SSL-unpinning** — blocked last session by ijiami anti-frida + the
   multi-process fork. If retrying, use the spawn + child-gating recipe already
   written in [docs on remote] `XTV-CAPTURE-STATUS.md` (bypass_anti.js pre-resume,
   enable_child_gating, inject into the forked child). Add a universal
   SSL-unpinning script (hook `okhttp3.CertificatePinner.check`,
   `X509TrustManager`, and the native pinning if any) to the child.

### Track B — Static reversal of the decrypted DEX

Once you have decrypted DEX (from Track A step 1, or any ijiami unpacker):

1. **Install jadx** (not on `.40` yet):
   ```
   sudo snap install jadx        # or download release jar; java-17 is present
   ```
2. **Decompile**:
   ```
   jadx -d /tmp/xtv_src /tmp/xtv_dex/*.dex   # or the reassembled apk
   ```
3. **Find the API interface** — grep the decompiled source:
   ```
   grep -rEl "portalCore|startPlayLive|getLiveData|snToken" /tmp/xtv_src
   grep -rn "startPlayLive\|liveAddressList\|playCode" /tmp/xtv_src
   ```
   Look for the Retrofit interface (annotations `@POST("...portalCore/v4/startPlayLive")`,
   `@Body`) and its call site. Note:
   - exact **host** (base URL — likely built from an obfuscated domain list; the
     app rotates `espjey/sxowvd/yrqucu...`; check the domain provider class)
   - exact **path** (`/api/portalCore/vN/startPlayLive`)
   - **request body fields** before encryption (channelCode, portalCode, userId,
     userToken, columnId, type, and any device/sn fields)
   - which **headers/cookies** the call carries (does it send s/t? a signature?)
4. **Confirm the crypto** — the request body is 3DES-wrapped by the interceptor
   already reproduced in `XuperCrypto.kt` (key `2b494e53...`). Verify the
   startPlayLive endpoint is `needEncrypt` (default true) or annotated
   `needEncrypt:false`. Check the OkHttp interceptor chain (classes `nb.b`,
   `rd.c`, `jd/a`) for how paths get encrypted too — the wire paths are opaque,
   so there may be a **path-encryption** step, not just body encryption.
5. **Find the CertificatePinner** — grep `CertificatePinner|sha256/|checkServerTrusted`
   to confirm what we must NOT replicate (our OkHttp simply omits pinning).

## What "solved" looks like

A documented request we can reproduce in `XuperApiClient.startPlayLive()`:

```
POST https://<portalcore-host>/api/portalCore/v4/startPlayLive   (or encrypted path)
Cookie: s=<session>; t=<session>            (if required)
Body (3DES-encrypted via XuperCrypto):
  { "channelCode": "...", "portalCode": "6e54356f76774c54574b303d",
    "userId": "694951876", "type": "live", ... }
Response (3DES-decrypted):
  { "data": { "liveAddressList": [ { "playCode"/"url": "http://cdsr.higoesutn.com/<path>?..." } ] } }
```

Once we can call it: the returned URL IS the fresh playlist (path + the one-time
`d` either in the URL or a Set-Cookie). Feed it into `M3uProxyServer`, which
already fetches the playlist and passes the open magloud segments through. The
proxy loop re-calls startPlayLive each cycle → continuous live, no manual capture.

---

# SESSION 5 — 2026-07-26 (18:03–19:45 PST)

## Off-device unidbg (Paths A + C)

**Achieved:**
- JNI_OnLoad returns `JNI_VERSION_1_6` (SUCCESS) — sanity function short-circuit works
- Full RegisterNatives table captured:
  - `N`: 7 methods — `l`, `r`, `ra` (real code @ 0x381c1–0x393ed), `b2b`/`m`/`sa`/`al` (trampoline stubs @ 0x39459–0x39461)
  - `C`: 1 method, `HM`: 2, `SE`: 1 (`sd(String)String`)
- 27 harness iterations (`Unpack.java`), 9 minimal harness iterations (`WideScan.java`)
- GOT chain verified correct (PC-relative offset fix)
- `ijiami.ajm` (2.5MB, `indl01` magic) + `IJMDal.Data` (17KB) discovered — DEX split across containers
- Embedded stub DEX in libexec.so: 156B + 280B (same as before)

**Blocked:**
- Singleton at `0x120868e0` never populated — ctor integrity gate ends in infinite-loop trap
- `N.l` crashes on `[r0,#0x188]` null deref — GOT entries zero
- No DEX decrypted — ctors behind integrity gate never execute
- `.40` OOM-wedged 3 times (wide hooks)

**Key repo files:** `Unpack.java` (22KB, 27 iterations), `WideScan.java` (12KB, 9 iterations)

## On-device frida-gadget (Path B)

**Achieved:**
- BlackDex32 installs/runs but hangs at "Desempaquetando" — ijiami v4 blocks DEX extraction
- libexec.so NOT visible in live process maps — loaded anonymously
- Full RegisterNatives would be captured if frida worked
- **frida-gadget APK built and installed** — `XTV_gadget_v3.apk` works
  - `libfrida-gadget-arm.so` (17.9.1) injected into APK lib/armeabi-v7a/
  - `System.loadLibrary("frida-gadget")` added to `S.smali` `attachBaseContext`
  - Built via Python zipfile (avoids apktool signing issues)
  - Signed with Android debug keystore

**Blocked:**
- Listen mode: app becomes zombie immediately (ijiami kills before frida connects)
- Script mode (v3): app survives but script output silent
- Script reads from `/data/local/tmp/` blocked by SELinux
- `send()` output not reaching `logcat -s Frida:*`
- Need to verify gadget actually hooks (try `Process.enumerateModules()` in script, or write output to `/sdcard/`)

## New assets
- `XTV_gadget_v3.apk` (script mode, app survives) — `C:/Users/Nestor/Workspace/Xuper/`
- `XTV_gadget_v4.apk` (script mode, app data path) — zombie
- `XTV_gadget_v5.apk` (listen+resume) — zombie
- `hook_gadget.js` — native anti-kill + DEX dump hooks
- `hook_native.js` — minimal send()-based debug script
- `APK build chain`: `xtv_gadget/build/apk/classes.dex` (modified smali), `frida_gadget/libfrida-gadget-arm.so`

---

## Immediate next actions (checklist)

- [ ] **Fix gadget script execution**: use `/sdcard/` path (SELinux permissive) or embed script as APK asset
- [ ] **Verify gadget hooks**: `Process.enumerateModules()` in script, check console output
- [ ] **Switch to listen+wait mode**: pauses app until frida connects, bypasses ijiami kill timing
- [ ] **Once frida connected**: hook `DETool.loadDEso`, capture decrypted DEX buffer
- [ ] **DEX recovered** → `jadx` decompile → find `startPlayLive` endpoint + body format
- [ ] **Implement** `XuperApiClient.startPlayLive()` → wire to `M3uProxyServer` → continuous live TV

---

# SESSION 5 — PTrace memory dump (2026-07-26 20:56)

**Findings:**
- App with clean XTV_clean.apk shows UI on screen (screenshot captured) — DEX decrypts successfully
- Dalvik-main space (0x12c00000-0x32c00000): zero app class descriptors (`Lcom/interactive/...`) found in any region
- Custom ijiami ClassLoader (`N.al`) loads DEX in-memory — NOT in standard dalvik regions
- App cycles crash/restart every ~3-5 seconds — too fast for reliable /proc/PID/mem reads
- Two anonymous executable regions found: 28KB + 380KB — latter matches libexec.so size
- `/proc/PID/mem` reads return 0 bytes when process dies mid-read

**Root cause:** App is unstable on .4 after reboot — crashes and restarts rapidly. Without ptrace (PTRACE_ATTACH to freeze process), live memory reads fail because process dies during the dump.

**Frida-gadget 16.5.9 results:** Same failure as 17.9.1 — gadget connects on TCP port but dies during frida protocol handshake. Version mismatch ruled out (16→16 and 17→17 both fail identically). Root cause: Android 10 kills gadget process during protocol negotiation (likely ANR timeout or SELinux restriction on thread creation in blocked process).

**Next step:** Compile a static ARM binary that uses ptrace to:
1. `PTRACE_ATTACH` to mgstv PID
2. Read `/proc/PID/maps` to find all anonymous rw regions
3. Read those regions via `PTRACE_PEEKDATA` or `/proc/PID/mem`
4. Search for DEX magic + `startPlayLive` strings
5. `PTRACE_DETACH`

**Alternatively:** Install original working APK (pre-gadget) fresh, which might be more stable than current device state.

## Reference: what we already have

- Session cookies `s`/`t` (44-char) captured; ~30 min lifetime. Re-capture via the
  MITM method in [ARCHITECTURE.md](ARCHITECTURE.md) (the working `wlan0`-table route fix).
- Device: `KEY_SP_SN=ca0e53edac957b8f6f187528933355f1`, `userId=694951876`,
  `portalCode=6e54356f76774c54574b303d`, streamKey `cyx_93531158996778016`.
- 3DES scheme + key already in `XuperCrypto.kt`.
- Segments (magloud) are open — no auth needed once we have the playlist.
- `RegisterNatives` table fully known — decrypt function is `N.l`/`N.r`/`N.ra` at known addresses
- `.4` device: adb root, SDK 29, ARM 32-bit, test-keys image
