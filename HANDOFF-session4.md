# HANDOFF — session 4 (2026-07-26 ~18:00 PST)

Goal (unchanged): recover the `startPlayLive` request format from the ijiami-packed XTV app
(`com.android.mgstv`) so XuperPlugin can self-fetch fresh live playlist tokens. Deep technical
detail lives in **`NEXT-BLOCKER.md`** (read sessions 3 + 4). This file is the operational
resume brief.

## TL;DR status
- **Off-device unidbg emulation WORKS.** The session-3 mmap/munmap blocker is SOLVED. The
  `.so` fully loads (all 63 ctors), execution reaches `JNI_OnLoad`.
- **Blocked at the 4th gate:** an ijiami anti-tamper ctor never populates a cached-JNI
  singleton (`0x120868e0`), so `JNI_OnLoad` null-derefs → returns `0xffffffff`. App DEX not
  decrypted yet.
- **OPERATIONAL PROBLEM:** every heavy Sonnet worker run OOM-wedges the `.40` box (ping-up,
  SSH-dead), needing a manual reboot. Happened twice. Cause: `UC_HOOK_MEM_WRITE` over wide
  ranges + full-address-space mem-scans balloon RAM (even 30 GB). **Next attempts must be
  surgical (single-page hook, heap cap, no wide scan).**

## Environment / access
- Work box: **`.40`** = `192.168.100.40`, user `nestor`, pw in helper. All rig under
  `~/xtv-ghidra/`.
- Run a command on `.40`:  `python /c/Users/Nestor/bin/ssh40.py "<cmd>"`  (exec-only, fresh
  shell each call; use absolute paths / `cd X && ...`).
- **MSYS path gotcha (bit us):** Git Bash rewrites `/tmp/...` args into Windows paths before
  a native `python.exe` sees them. When passing Linux paths to a local python helper, pass
  them WITHOUT a leading slash and re-add it in the script (see `/tmp/put40b.py`), or the
  file lands in the wrong remote dir.
- **Upload a file to `.40`** (SFTP via paramiko, handles MSYS): local helper `/tmp/put40b.py`:
  `python /tmp/put40b.py "<C:/local/path>" "tmp/apkx/assets/.../file"` (remote arg has NO
  leading slash). Recreate it if gone — it's: connect paramiko, `mkdir -p dirname`, `sftp.put`.
- **`.4`** = the live TV box (`192.168.100.4`) — DO NOT disturb for this work. All emulation
  is off-device on `.40`.

## /tmp WIPES ON REBOOT — restore assets first
After any `.40` reboot, `/tmp/apkx/` is gone. Restore the two assets from the Windows copies:
- Windows copies: `C:\Users\Nestor\Workspace\Xuper\XuperPlugin\_assets\{libexec.so, ijiami.dat}`
  (libexec.so = 435600 B armeabi; ijiami.dat = 4560834 B; libexec md5 `d01ad0b95a4f85ff555f68a4c4860205`).
- Restore:
  ```
  python /tmp/put40b.py "C:/Users/Nestor/Workspace/Xuper/XuperPlugin/_assets/libexec.so" "tmp/apkx/assets/ijm_lib/armeabi/libexec.so"
  python /tmp/put40b.py "C:/Users/Nestor/Workspace/Xuper/XuperPlugin/_assets/ijiami.dat"  "tmp/apkx/assets/ijiami.dat"
  ```
- `~/xtv-ghidra/` (home) SURVIVES reboot: patched unidbg, jdk8/jdk21, harness, scripts, venv.

## What's built & where (all on `.40`, survives reboot)
- **Patched unidbg `0.9.10-SNAPSHOT`** installed in `~/.m2/.../unidbg-android/0.9.10-SNAPSHOT/`.
  Source patches in `~/xtv-ghidra/unidbg/unidbg-android/src/main/java/com/github/unidbg/linux/`
  (grep **`XTV-PATCH`**):
  1. `AndroidElfLoader.clampedMunmapForFixedRemap()` — fixes ijiami's MAP_FIXED self-remap
     that spans the LOAD1/LOAD2 gap (the session-3 blocker).
  2. `AndroidSyscallHandler.kill()` — throws on `kill(_, SIGKILL)` (anti-emu trap).
  - Build needs **JDK8** (`~/xtv-ghidra/jdk8u492-b09`; JDK17/21 hit `Module` ambiguity) +
    `-Dgpg.skip=true`. Rebuild: `cd ~/xtv-ghidra/unidbg && JAVA_HOME=~/xtv-ghidra/jdk8u492-b09 ./mvnw -pl unidbg-android -am install -DskipTests -Dgpg.skip=true`.
- Master unidbg ships a WORKING self-contained `unicorn2` backend (no native build needed) —
  but you MUST register **`Unicorn2Factory`** in the emulator builder or it falls back to the
  broken old unicorn. (The published unicorn 1.0.15 native is broken: `helper_div_i32`.)
- Harness: `~/xtv-ghidra/harness/` (`pom.xml`, `src/main/java/com/xtv/Unpack.java`,
  `src/main/resources/simplelogger.properties`). Needs `slf4j-simple:2.0.16` (without a
  binding, unidbg drops into an interactive debugger on exceptions and BLOCKS on stdin).
  Compile: `cd ~/xtv-ghidra/harness && export PATH=~/xtv-ghidra/maven/bin:$PATH && mvn -q compile`.
  Classpath cache: `~/xtv-ghidra/cp.txt` (rebuild if pom changes:
  `mvn -q dependency:build-classpath -Dmdep.outputFile=$HOME/xtv-ghidra/cp.txt`).
- Analysis tool: `~/xtv-ghidra/scripts/scan2_disasm.py` (thumb-aware capstone disasm vs a
  mem-scan). Ghidra 11.3.2 + jdk21 + capstone venv also present.

## RUN SAFELY (avoid the OOM that keeps wedging the box)
ALWAYS:
```
cd ~/xtv-ghidra/harness
CP="target/classes:$(cat ~/xtv-ghidra/cp.txt)"
nohup timeout 240 java -Xmx3g -Djava.library.path=~/xtv-ghidra/nativelib \
  -cp "$CP" com.xtv.Unpack </dev/null >~/xtv-ghidra/runN.log 2>&1 &
```
- `-Xmx3g` hard heap cap, `timeout 240`, `</dev/null` (never block on stdin).
- **NO wide mem-scans.** Do NOT iterate/dump 0x1000..0x50000000. Hook ONLY the exact bytes
  you need; dump ONLY the module region (base `0x12000000`) straight to disk in chunks,
  never into a Java collection/StringBuilder.
- Monitor: `.40` has 30 GB but the emulation still blew it via wide instrumentation. If
  `free`/`load` spikes, `kill -9` the JVM immediately.

## THE 4th BLOCKER (current)
`JNI_OnLoad` (thunk → real fn at `.so`+0x37b4a) calls `GetEnv`, then derefs GOT-cached
singleton `GOT@0x12082340 → 0x120868e0 → NULL` and calls vtable slot `+0x40` → null-deref →
`0xffffffff`. The gating ctor at **`.so`+0x3a1d8** does an obfuscated integrity check:
`getpid()` (raw svc `r7=0x14`), reads `/proc/self/status`, opaque-predicate compare vs a
threshold, then hits the `kill` trap. The singleton's real init appears gated behind this
check passing in a genuine process; under emulation it doesn't fire, so `0x120868e0` stays NULL.

## NEXT STEP (surgical — do this, not another giant worker)
1. Add `UC_HOOK_MEM_WRITE` on EXACTLY `0x120868e0`–`0x120868e8` (8 bytes) to log PC+value of
   whoever writes the singleton. (Narrow range only — this is what avoids the OOM.)
2. Stub the anti-tamper so the gating ctor's check PASSES: virtualize `/proc/self/status`
   (`TracerPid: 0`, normal fields) via unidbg's IOResolver; make `getpid()` return a plausible
   pid; if a specific opaque predicate still fails, hook that instruction in `.so`+0x3a1d8 and
   force the taken branch.
3. Goal: `JNI_OnLoad` returns `JNI_OK`. Then unidbg logs `RegisterNatives` — capture
   (name, sig, fnptr); identify the decrypt native (the one `DETool` calls with the ijiami.dat
   buffer).
4. Let flow proceed (or call the decrypt native on ijiami.dat), then dump ONLY the module +
   heap region and scan for the real app DEX (`dex\n035\0` magic, large `file_size` at
   header+0x20; multi-MB — not the known 156B/280B stubs). Dump to `/tmp/apkx/app_*.dex`.
5. `jadx` the DEX (install on `.40` if missing). Grep `startPlayLive`, `portalCore`,
   `liveAddressList`, `playCode`, `@POST`, `needEncrypt`. Extract host, path, request-body
   fields, cookies/headers (s/t?), and encryption (3DES key `2b494e53...` per `XuperCrypto.kt`).
6. Expect possibly ONE more anti-check at the decrypt call — push through it too.

## Cost note
Each big Sonnet worker run = ~500k tokens AND has twice wedged the box. Recommend: drive the
next step SURGICALLY (minimal harness edits, controlled runs) rather than dispatching another
open-ended 500k-token worker. Hard-leash if delegating.

## Immediate state right now (session 4 end)
- `.40` is currently OOM-wedged (ping-up, SSH-dead) — **needs a reboot** before resuming.
- The background worker `a4b4a1b90047ea38e` is dormant (was polling for `.40` to return).
- After reboot: restore assets (above) → apply the surgical NEXT STEP.

---

# SESSION 5 (2026-07-26 ~18:03–18:33 PST) — off-device unidbg ARMS RACE

## What was achieved

1. **`.40` rebooted + assets restored.** `/tmp/apkx/` wiped on reboot — restored from Windows copies.

2. **JNI_OnLoad now returns `JNI_VERSION_1_6` (SUCCESS).** The anti-tamper sanity check was short-circuited with a return value of 0. This lets JNI_OnLoad proceed past the gate and RegisterNatives executes fully.

3. **Full RegisterNatives table captured (run22.log, verbose mode):**
   ```
   Class s/h/e/l/l/N (7 methods):
     l(Landroid/app/Application;Ljava/lang/String;)Z     @ 0x120381c1  — init/load
     r(Landroid/app/Application;Ljava/lang/String;)Z     @ 0x12038f1d  — run
     ra(Landroid/app/Application;Ljava/lang/String;)Z    @ 0x120393ed  — run-asset
     b2b([BI)[B                                         @ 0x12039459  — bytes-to-bytes (STUB/TRAMPOLINE)
     m(Ljava/lang/String;I)V                             @ 0x1203945d  — stub
     sa(Ljava/lang/String;Ljava/lang/String;)V            @ 0x1203945f  — stub
     al(Ljava/lang/ClassLoader;Landroid/content/pm/ApplicationInfo;Ljava/lang/String;Ljava/lang/String;)Ljava/lang/ClassLoader;
                                                          @ 0x12039461  — DEX classloader (stub)
   Class s/h/e/l/l/C:  i(I)V                            @ 0x1204dc15
   Class s/h/e/l/l/HM: l()V, u(Ljava/lang/Object;Ljava/lang/Object;)V
   Class s/h/e/l/l/SE: sd(Ljava/lang/String;)Ljava/lang/String;  @ 0x1203fc3d
   ```
   **Key finding:** `b2b`, `m`, `sa`, `al` are all registered at addresses within 8 bytes of each other (0x39459–0x39461). Disassembly confirms these are TRAMPOLINES/THUNKS, not real function code. The real function implementations are at `l`, `r`, `ra` (0x381c1–0x393ed).

4. **`N.b2b` called → returns null silently.** No JNI activity during the call — confirms it's a stub. The real decrypt logic is in `l`/`r`/`ra`.

5. **`N.l` called → calls `getAssets()` on Application → crashes.** The native code does a JNI callback `(*env)->CallObjectMethodV(app, getAssets_id)` but unidbg's DalvikVM can't resolve the method because the class hierarchy isn't set up (no DEX loaded). When using `android.app.Application` directly instead of `S`, gets past getAssets but hits NULL deref at 0x188 — the singleton GOT entry at 0x1203b548 is still zero.

6. **The 4th blocker — singleton never populated — ROOT CAUSE IDENTIFIED:**
   The gating ctor at 0x1203a1d8 is a complex integrity gate. FULL disassembly (run27 analysis):
   - When sanity function returns 0 → takes EARLY RETURN (just sets flag, skips init)
   - When sanity returns non-zero → enters INITIALIZATION path
   - Init path checks: `[obj+0x188]`!=NULL, check-func@0x1207b5a0 returns non-zero, getpid()>threshold, opaque predicate math, then `cbz r1`
   - **Success path (r1!=0) leads to `b #0x1203a282` — INFINITE SELF-LOOP TRAP**
   - **Failure path (r1==0) leads to kill(-1, SIGABRT) via `[obj+0x109]` guard**
   - The `+0x109` kill can be skipped by setting it to 0, but then ctor returns with r4=-1 without populating the singleton
   - **The ctor is DESIGNED to never succeed under emulation — "success" is a deadlock**

7. **Harness evolution (Unpack.java, 22KB, 27 iterations):**
   - Comprehensive Android Build mock (getStaticObjectField, getStaticIntField, callStaticObjectMethodV, getObjectField, getIntField)
   - Smart fallback for unmocked String-returning fields
   - Force-written singleton/vtable at scratch addresses
   - Multiple code hooks for integrity check bypass (sanity, getpid, opaque predicate, bls branch, check function)
   - `.40` copy at `~/xtv-ghidra/harness/src/main/java/com/xtv/Unpack.java`

## Current blocker
Singleton GOT entries (0x12082340, 0x1203b548, others) never populated because the ctor integrity gate is an impenetrable deadlock trap. Native methods like N.l crash reading NULL + 0x188. N.b2b is a stub/trampoline that returns null without doing anything.

## Three alternate paths forward

### Path A — Force-write GOT entries manually
Identify ALL GOT entries the native methods read, calculate what values they should contain, force-write them all before calling natives. Requires static analysis of every GOT-referencing instruction.

### Path B — On-device BlackDex retry
BlackDex hung previously (ijiami v4). Try:
- Different BlackDex version (v3.3.0 instead of latest)
- NGProxy or other ART hooking tool
- Native Frida gadget injection (libfrida-gadget.so bundled in APK) instead of frida-server

### Path C — Raw ARM function call via Module.callFunction
Call the REAL decrypt function (at N.l's address 0x120381c1, or one of the other addresses) directly via `Module.callFunction(emulator, offset, args...)` with manually-setup ARM registers for JNI calling convention, bypassing the JNI bridge entirely. Complex but bypasses all Java callback issues.

## Files modified this session
- `Unpack.java` — 22KB harness with comprehensive unidbg mocks + hooks (NEW, committed)
- `HANDOFF-session4.md` — updated with session 5 findings
- `.40: ~/xtv-ghidra/harness/src/main/java/com/xtv/Unpack.java` — deployed
- `/tmp/apkx/assets/` — restored after reboot

## What's proven / reference (from earlier sessions)
- Stream arch: portalCore (cert-pinned) → cdsr playlist (one-time d cookie) → magloud segments
  (open). See `ARCHITECTURE.md`. Device: `KEY_SP_SN=ca0e53edac957b8f6f187528933355f1`,
  `userId=694951876`, `portalCode=6e54356f76774c54574b303d`, streamKey `cyx_93531158996778016`.
- Static Ghidra is walled (libexec.so self-encrypting). MITM/blind-probe/on-device frida all
  closed. Off-device unidbg is the only live path — and it's working, one gate from the DEX.
