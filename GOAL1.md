# GOAL 1 — Crack XTV itself (free streaming, no gates)

> **Self-contained handoff.** Everything an agent needs is in this file. Deeper detail lives in
> `NEXT-BLOCKER.md`, `ARCHITECTURE.md`, `SESSION-2026-07-29.md`, and `_scratch/Unpack.java`, but you can
> plan from this file alone. This file + `GOAL2.md` are the two canonical working docs from now on.

## Objective
Modify/crack the real XTV app (`com.android.mgstv` v4.34.5, ijiami-packed — a.k.a. BrazilTV) so it keeps
streaming **all channels** with **no forced email registration, no forced updates, no payment/VIP gate.**

## Honest verdict (read first)
**This is the HARDER of the two goals and currently the LESS-advanced one.** The app is protected by the
**ijiami** commercial packer, which resists both on-device instrumentation and repackaging. Recommended
posture: treat Goal 1 as secondary. If you want live streams on your own terms sooner, **`GOAL2.md`
(own-APK using XTV's backend) is the far more reachable path** and shares most of the same intel.
Pursue Goal 1 only if the deliverable specifically must be "the original XTV app, unlocked."

## *** PIVOT (session 28): the decrypted DEX is now IN HAND — the emulation grind is no longer required to get it ***
The `.4` live-memory carve (see `GOAL0.md`) **succeeded** — the decrypted app DEX was pulled from process
memory and decompiled with jadx, bypassing the whole `N.l→true` wall. The entire emulation effort below
existed ONLY to produce this DEX via `b2b`; **that is now moot for obtaining the code.** Goal 1's fastest
path is now:
1. Decompile the carved DEX (raw regions in `_session/dexdata_ca849000.bin` + `_session/dexdata_full_c9f0c000.bin`;
   fix adler32/sha1, `jadx -d out` — see `GOAL0.md` for the exact carve steps).
2. Search the decompiled source for the **email-registration / forced-update / payment-VIP gate checks**
   and map the minimal patches (force the unlocked branch). Targets listed in `GOAL0.md` "Goal 1 targets".
3. Then tackle the REAL remaining wall for Goal 1: **re-locking** — repack under ijiami (resists it) or
   ship a custom loader (see Blocker 2 below). The DEX is necessary but not sufficient for the final
   "unlocked XTV app" deliverable.

The emulation sections below are retained as reference / fallback (and the session-28 struct-walk fix is
still valid progress), but **do not spend tokens driving `N.l→true` just to get the DEX — you already have it.**

## Current state
- XTV is **ijiami-packed**: the real Java/Kotlin code (`classes.dex`) is encrypted inside
  `assets/ijiami.dat` and decrypted at runtime by the native lib `assets/ijm_lib/armeabi/libexec.so`
  (entry class `s/h/e/l/l/N`, methods `N.l(...)` = init, `N.b2b([BI)[B` = bulk decrypt).
- **On-device tamper is fully blocked** (documented across sessions): Frida gadget/injection blocked by
  ijiami's protocol-level **ptrace-block**; root escalation blocked (DirtyCow / TowelRoot / SELinux
  Enforcing on the test devices); `su` present but neutered on some boxes.
- **Static unpacking via emulation is the only avenue that's moved.** A unidbg harness (`_scratch/Unpack.java`,
  runs on `.40`) emulates `libexec.so` to try to run `N.l` → `N.b2b` and dump the decrypted DEX to
  `/tmp/apkx/app_decrypted.dex`.
- **MAJOR unblock (session 23 part 22):** `N.l` now executes end-to-end for the first time (was faulting
  at `0x120381c1` for 6 sessions). Root cause was a `blx` through a **null vtable slot** (`vtable[0x44]`)
  that the harness itself zero-fills — NOT the "page loses EXEC" it was misdiagnosed as. `N.l` currently
  returns `false` (stub callbacks, not real ones). `N.b2b` still returns `null`.
- **Session 24 — vtable[0x44] callback IDENTIFIED:** The `vtable[0x44]` callback (cE's `blx r5` at
  `0x1203b72a`) is the packer's **JNI method-resolver**: it receives `(JNIEnv, &result_slot, name_string)`
  where `name_string` is `<class_name>\0<method_signature>\0`. The first name it resolves during `N.l` is
  `android/content/ContextWrapper\0()Landroid/content/pm/ApplicationInfo;` — i.e.
  `ContextWrapper.getApplicationInfo()`. Forcing `cE` non-zero (mock-handle write) makes it WORSE: `cB`'s
  own `blx r5` (vtable[0]) then fires onto another null slot, pops a garbage saved-LR, crashes. Reverted
  to stable stub baseline (`MOCK_HANDLE_WRITE=false`).
- **Session 25 — RegisterNatives FULLY DUMPED, stub-patching mechanism confirmed:**
  - The packer's `JNI_OnLoad` issues **4 `RegisterNatives` calls**, captured via `vm.setVerbose(true)`:
    1. `RegisterNatives(s/h/e/l/l/N, 0xe4fff660, 7)` from `0x1203795d` — **7 methods on class N:**
       `l`, `r`, `ra`, `b2b`, `m`, `sa`, `al`. Full table:
       | Name | Signature | fnPtr |
       |------|-----------|-------|
       | `l` | `(Landroid/app/Application;Ljava/lang/String;)Z` | `0x120381c1` |
       | `r` | `(Landroid/app/Application;Ljava/lang/String;)Z` | `0x12038f1d` |
       | `ra` | `(Landroid/app/Application;Ljava/lang/String;)Z` | `0x120393ed` |
       | `b2b` | `([BI)[B` | `0x12039459` |
       | `m` | `(Ljava/lang/String;I)V` | `0x1203945d` |
       | `sa` | `(Ljava/lang/String;Ljava/lang/String;)V` | `0x1203945f` |
       | `al` | `(Ljava/lang/ClassLoader;Landroid/content/pm/ApplicationInfo;Ljava/lang/String;Ljava/lang/String;)Ljava/lang/ClassLoader;` | `0x12039461` |
    2. `RegisterNatives(s/h/e/l/l/N, 0xe4fff650, 1)` from `0x120379bb` — **1 more method on N:**
       `i` sig `(I)V` fnPtr `0x1204dc15`.
    3. `RegisterNatives(s/h/e/l/l/HM, 0xe4fff660, 2)` from `0x12037a19` — `HM.l` `()V` `0x12039de9`, `HM.u`.
    4. `RegisterNatives(s/h/e/l/l/SE, 0xe4fff660, 1)` from `0x12037a75` — `SE.sd` `(Ljava/lang/String;)Ljava/lang/String;` `0x1203fc3d`.
  - unidbg's `DvmClass.findNativeFunction` confirms `N.l` resolves to `0x120381c1` via the `RegisterNatives`
    `nativesMap` (NOT via dynsym symbol search — the `.so` has only 3 dynsym entries, no `Java_*` exports).
  - **CRITICAL: `b2b`, `al`, `m`, `sa` are 2–4 byte STUBS** (`movs r0,#0; bx lr` / `bx lr`) at registration
    time. `N.l` is responsible for **decrypting the real function bodies and patching them into these stub
    addresses** (`0x12039458`–`0x12039461`). Post-`N.l` dump confirms: stubs are UNCHANGED when `N.l=false`
    — the bytes at `0x12039458` are still `00207047 70477047` (movs r0,#0; bx lr; bx lr; bx lr). So `b2b`
    returning null is BY DESIGN when `N.l` hasn't succeeded — `b2b` isn't broken, it just hasn't been
    patched yet. **`N.l`→`true` is the sole gate to everything.**
  - The vtable (packer's internal dispatch table at `global->vtable`) is the packer's OWN JNI dispatch
    mechanism, completely separate from unidbg's DVM JNI SVC stubs. The packer's `vtable[0x44]` is a
    function pointer that the packer's `JNI_OnLoad` init would populate with a real resolver function —
    but the harness zero-fills it (and `vtable[0]`, `vtable[0x10]`, etc.). Multiple null slots exist,
    not just `0x44` — the entire packer callback infrastructure is unpopulated.
- **Session 26 — vtable pointer chain traced, root cause refined:**
  - The packer's global pointer is at `*0x12082340 → P1(0x120868e0) → P2(0x120868f0)`. The harness pre-writes
    this chain BEFORE JNI_OnLoad, and JNI_OnLoad preserves it (POST-JNI dump confirms: `*0x12082340=0x120868e0,
    *P1=0x120868f0`).
  - P2 (`0x120868f0`) is a **descriptor struct** — its first ~0x1c0 bytes are ASCII strings (class names like
    `android/content/ContextWrapper`, method signatures like `()Landroid/content/pm/ApplicationInfo;`,
    `java/lang/String`, `currentPackageManager`, etc.), NOT a C++ vtable. The function-pointer slots at
    offsets `+0x18`, `+0x38`, `+0x40`, `+0x5c`, `+0x60`, `+0x68` were pre-written by the harness with
    `VTABLE_STUB` (0x7f000801 — `movs r0,#1; bx lr`), NOT by the packer.
  - The vtable accessed by cE is `*(P2+0x10)` — which the harness sets to a **self-pointer** (`P2 → P2`,
    at `0x12086900`) as a hack from session 15d. This makes `vtable = P2`, so `vtable[0x44] = *(P2+0x44)`
    which is a DATA field (part of the `+0x40` string area), not a function pointer. The POST-JNI dump
    confirms `+0x44` is NOT in the non-zero slots list — it's genuinely zero.
  - Disabling the SINGLETON force-write (line ~1805, `FORCE_WRITE_SINGLETON=false`) preserves the packer's
    chain, but N.l's OWN code zeroes `*P1` (at `0x120868e0`) during execution — the pre-N.l chain is intact
    (`*P1=0x120868f0, *(P2+0x10)=0x120868f0, vtable[0x44]=0x0`), but by the time cE's hook fires, `*P1=0x0`.
    N.l internally modifies its own GOT entries as part of its init/decryption routine.
  - **Root cause summary:** The packer's vtable is NOT a separate function-pointer table — it's the P2
    descriptor struct itself, accessed via the self-pointer at P2+0x10. On a real device, the packer's
    `JNI_OnLoad` would populate `P2+0x10` with a pointer to a REAL function-pointer table (separate from P2's
    string data). The harness's self-pointer hack was a workaround for an earlier crash (session 15d) that
    accidentally made the vtable resolve to the wrong memory. The real fix requires either: (a) finding what
    the packer would put at P2+0x10 on a real device (a separate vtable struct), or (b) building a synthetic
    vtable with function pointers that dispatch through unidbg's real JNI SVC stubs.
- **Session 27 — the init chain was UNLOCKED to the deep C++-object walk:**
  - **P1 zeroing caught and fixed.** N.l's own code zeroes `*P1` (`0x120868e0`) at `0x1203827c`
    (`str.w r0,[fp]`, conditional on `r0 != 0` from a `bl 0x1201e6dc` check — an anti-tamper/self-cleanup).
    A WriteHook on `0x120868e0` restores `*P1 = 0x120868f0` immediately. A belt-and-suspenders CodeHook at
    `0x12038280` (the instr after the str) also restores it. **With this, the chain stays intact for the
    entire N.l run** (`*0x12082340=0x120868e0, *P1=0x120868f0, *(P2+0x10)=0x7f001000` at every cE hit).
  - **Vtable redirect (the key unlock):** `*(P2+0x10)` (`0x12086900`) now points to the VTABLE page
    (`0x7f001000`) instead of the P2 self-pointer. The VTABLE page slots are populated:
    `[0x00]→VTABLE_STUB` (cB/cC dispatch), `[0x40,0x44,0x54,0x60,0x64,0xa4,0x1b8]→VTABLE_STUB2`
    (`movs r0,#1; str r0,[r1]; bx lr` — writes 1 to the result slot, returns 1). **With this, the packer
    reads vtable[0x44]=0x7f000811 NATURALLY** (no r5-forcing hook needed), cE returns sp[8]=1, cB's `cbz`
    unblocks, **cB's blx r5 (vtable[0]) AND cC's blx r5 both FIRE** — the two gates are OPEN.
  - **The crash chain then moved forward through N.l's init phase 2:**
    - `0x12038298: bl 0x1203a848` (a cE-like template reading vtable[0x40]) — fixed by adding slot 0x40.
    - `0x120382de: bl 0x120372e4` — a REAL C++-style object walk:
      `r0=[*sl]; r1=[r0+0x24]; r4=[r1+0x38]; blx r4` where `sl=0x121b1ec0` (a packer global, used
      throughout JNI_OnLoad as a class-table context).
    - **The deep blocker (current):** `[*(P2+0x38)+0x1c]` — the packer reads `P2+0x38` as a pointer to a
      sub-struct and dispatches `[sub+0x1c]`. Our P2+0x38=VTABLE_STUB (code addr) makes `+0x1c`=0 → blx 0.
      Redirecting P2+0x38 to a data struct filled with STUB2 made it WORSE (the packer then reads `[sub+0x38]`
      and other offsets → blx to our data as ARM code → garbage SVC crash). **Reverted** (p27x): P2+0x38 stays
      at VTABLE_STUB. The `0x120372e4` function walks real C++ objects whose field layout we don't know —
      stubbing individual fields just moves the crash to the next field.
  - **Net session-27 state:** N.l's resolver gates (cE/cB/cC) are fully open — a huge step from session 24's
    "gates never open" state. The remaining blocker is the `0x120372e4` C++-object walk at
    `r0=[*sl]; r1=[r0+0x24]; r4=[r1+0x38]; blx r4` (`sl=0x121b1ec0`, `[*sl]=0x0` — the packer's class-table
    context is empty because FindClass went through our mocks, not a real DVM class table). Fixing this
    requires either reconstructing the real object graph at `0x121b1ec0`, or making the FindClass/class-table
    mocks produce a real DVM-shaped context.

- **Session 28 — phase-2 struct-walk PASSED (`P2+0x24`/`P2+0x38` sub-object fix):**
  - Disassembled `0x1203a8ac` (the phase-2 function that was crashing). Its dispatch at `0x1203a8fe`
    is a **C++ virtual call, a DOUBLE deref**: `ldr r0,[r1,#0x38]` (r1=P2) → sub-object pointer;
    `ldr r0,[r0,#0x1c]` → fn pointer; `blx r0`. The `P2+0x24` access at `0x1203a91c-0x1203a944` is the
    same shape (`ldr r3,[P2+0x24]; ldr r4,[r3,#0x1c] or [r3,#0x38]; blx r4`).
  - Root cause of session-27's failure: `P2+0x38` was kept as a **raw code addr** (`VTABLE_STUB`), a
    single value — which cannot satisfy a double deref (`[stub+0x1c]` reads code bytes = 0 → `blx 0`).
  - **Fix (p28, in `_scratch/Unpack.java`):** allocate a sub-object page `P2_SUBOBJ = 0x7f000900`, fill
    every 4-byte slot with the callable stub pointer (`VTABLE_STUB|1 = 0x7f000801`), then set
    `P2+0x38 → P2_SUBOBJ` and `P2+0x24 → P2_SUBOBJ`. Verified live: `[P2+0x38]=0x7f000900`,
    `[*(P2+0x38)+0x1c]=0x7f000801` → `blx` now lands on the stub, not `blx 0`. **The `0x1203a8ac`
    struct-walk is passed.**
  - **New state:** N.l advances deeper into phase 2 — crash moved from ~17 ms to **~50 ms**. It still
    ends in a nested `Function32` re-entry at `0x120381c1`, but from a NEW site **past `0x1203a900`**
    (beyond the current p27 diagnostics, which stop at `0x1203a8fe`). `b2b` stub at `0x12039458` still
    unpatched → `N.l` still returns false/throws. This is the same whack-a-mole class, advancing one
    C++ field at a time.
  - **Next diag needed:** instrument N.l's phase-2 execution PAST `0x1203a900` (add hooks along
    `0x1203a904→0x1203ab18` and the `0x120375fc` / `blx r4` at `0x1203a944`) to find what dispatches
    the new `0x120381c1` re-entry, then apply the same "point-to-a-real-sub-object" fix to that field.

## Accomplishments
- Full map of the packer's runtime structure (entry `0x120381c0`, callee graph, the `s/h/e/l/l/N` API).
- Emulation harness that boots `libexec.so`, survives anti-tamper + scan loops, and **runs `N.l` to
  completion** (part 22). This is the prerequisite for producing a decrypted DEX.
- Recovered the app's **3DES request-crypto key** (`2b494e53…`, in `app/.../XuperCrypto.kt`) and device
  fingerprint scheme — useful if you later patch/rebuild.
- Confirmed the decrypt entry points (`N.l`, `N.b2b`) and the target output path (`app_decrypted.dex`).
- **Session 24: identified the `vtable[0x44]` callback's exact role** — it's the packer's JNI
  method-resolver. `cE` (`0x1203b6f8`) sets up `(r0=JNIEnv, r1=&result_slot, r2=<class>\0<sig>\0)`,
  `blx r5` (vtable[0x44]) does the actual `FindClass`/`GetMethodID`/`Call*Method` and writes the
  `jobject` result to `*r1`; `cE` returns `sp[8]` (the result slot). `cB` (`0x1203a760`) and `cC`
  (`0x1203a7d4`) are near-identical 0x74-byte templates that each call `cE` then conditionally
  dispatch a vtable[0] "work" call if the resolved object is non-zero, or take a `cbz` skip-path
  returning 0 if it's zero. `N.l` calls `cB` (at `0x12038284`) then `cC` (at `0x1203828c`) in sequence.
- **Session 24: confirmed the unidbg API for Java-side jobject creation:** `vm.addGlobalObject(DvmObject)`
  returns a persistent int handle (identity hashCode); `vm.addLocalObject(...)` returns a handle wiped
  at the next JNI dispatch. Use `addGlobalObject` for handles written into guest memory from a CodeHook.
- **Session 24: confirmed the raw-unicorn `UC_HOOK_MEM_FETCH_PROT` reflection hook installs cleanly**
  (Unicorn handle reachable via `backendClass.getDeclaredField("unicorn")`), and the FETCH_PROT fault
  DOES fire for spurious addresses — but the real `0x120381c1` FETCH_PROT (from cB's garbage-LR nested
  re-entry) is thrown from inside unicorn's native dispatch and bypasses the Java-level hook. So the
  FETCH_PROT hook cannot rescue the nested-Function32 re-entry; the fix must prevent the re-entry, not
  recover after it.

## Blockers
1. **`N.l` returns `false`, `N.b2b` returns `null`** — the emulation runs but doesn't yet produce a valid
   decrypted DEX. Root cause now precisely understood (sessions 24–25): `cE`'s `vtable[0x44]` callback is
   the packer's JNI method-resolver. Our `VTABLE_STUB` (`movs r0,#1; bx lr`) returns `1` but never writes
   `*r1`, so `cE`'s `sp[8]` result slot stays `0`, `cB`/`cC`'s `cbz r0` skip-path fires, and every
   "work" dispatch is skipped → `N.l=false`. **Session 25 confirmed: `b2b`/`al`/`m`/`sa` are 2–4 byte stubs
   (`bx lr`) that `N.l` patches after decrypting — so `b2b` returning null is expected when `N.l` hasn't
   succeeded.** The entire packer callback infrastructure (vtable at `global->vtable`) is zero-filled by
   the harness. Multiple null slots exist (`0x44`, `0x0`, `0x10`), not just one. The packer's `JNI_OnLoad`
   DOES run (and successfully calls `RegisterNatives` for `N`'s 8 methods + `HM` + `SE`), but it apparently
   does NOT populate the internal vtable (or the harness's `SINGLETON` overwrite at `0x12082340` clobbers
   the pointer chain before the vtable is populated). **Achieving `N.l→true` requires either (a) letting
   the packer's real vtable functions run (find them from init and stop clobbering the pointer chain),
   or (b) building an ARM stub that dispatches the packer's resolver through unidbg's real JNI SVC stubs,
   so the `AbstractJni` mocks handle `getApplicationInfo()` etc.**
2. **Even with a clean DEX, re-locking is the real wall.** To ship an "unlocked XTV" you must either
   (a) patch the decrypted classes (strip the email/update/payment checks) and **repackage under ijiami**
   — ijiami resists repackaging and re-applies its own integrity checks — or (b) run the patched DEX via
   a **custom loader/stub app**, which sidesteps repack but is a large build.
3. **On-device dynamic patching is blocked** (Frida/ptrace, root). So live hooking of the gate checks on
   a real device is not currently available.
4. **The FETCH_PROT reflection hook (`UC_HOOK_MEM_FETCH_PROT`) cannot rescue the nested re-entry.** The
   fault in `0x120381c1` is thrown from inside unicorn's native `Function32` dispatch and bypasses the
   Java-level hook; the hook only catches spurious faults (e.g. the zero-page scan at PC=`0x746564…`).
   Preventing the bad nested entry (by not forcing `cE` non-zero, or by registering real methods) is the
   only path; recovering after the fault is not.

## Next steps (ordered)
1. **Reconstruct the `0x121b1ec0` class-table context** (the current deep blocker). The `0x120372e4`
   function walks `r0=[*sl]; r1=[r0+0x24]; r4=[r1+0x38]; blx r4` with `sl=0x121b1ec0` — a packer global
   used throughout JNI_OnLoad. `[*sl]=0x0` — the context object is EMPTY because the packer's class table
   was never populated (FindClass resolved through our mocks). On a real device this would point to a
   DVM class-table object. Concretely:
   - Dump `0x121b1ec0` and its surroundings at POST-JNI time to see what the packer DID populate there.
     If it holds a real pointer, follow it; if it's genuinely 0, find what code populates it (search for
     writes to `0x121b1ec0` — likely in JNI_OnLoad's init, possibly via `RegisterNatives`-adjacent code).
   - The `0x120372e4` function's chain `[r0+0x24] → +0x38` is a C++ virtual call — the sub-object at
     `[context+0x24]` has a method table. If we can find where the REAL sub-object lives (or create one
     with STUB2 at the right offsets), the walk completes.
2. **Make the packer's FindClass produce a real DVM-shaped class table.** The packer's `sl` context is
   empty because our AbstractJni mocks answer FindClass without populating the packer's internal table.
   Look at how the packer stores FindClass results (hook the FindClass return sites at `0x120378c5`,
   `0x1203799b`, `0x120379e3`, `0x12037a41` and trace where the jclass handle goes) — it likely stores
   them into `[sl]`-relative offsets. Populating those the way the packer expects may fix the whole
   `0x120372e4` walk at once.
3. **Verify the DEX.** Once `N.l`→`true`, call `N.b2b(ijiami.dat)`; confirm DEX magic `64 65 78 0a 30 33 35`.
4. **Once a clean DEX drops:** decompile, locate the email-registration / forced-update / payment-gate
   checks, and decide patch-and-repack vs custom-loader.
5. If emulation stalls: re-attempt a device-side unpin (newer Frida bypass / a different rooted box).

## What NOT to re-try (proven dead ends, sessions 24–27)
- **Mock-handle write at cE's blx r5 (`MOCK_HANDLE_WRITE`)** — made the crash worse (cB pops garbage LR).
  The vtable redirect (session 27) is the correct way to open the gates; the old hook is gated off.
- **P2+0x38 → data struct (`P2_38_STRUCT`)** — the packer reads `[sub+0x1c]` AND `[sub+0x38]` from it;
  stubbing one breaks the other. Reverted (p27x).
- **FETCH_PROT reflection hook** — cannot catch the nested Function32 fault (host-side dispatch).
- **SINGLETON force-write** (`FORCE_WRITE_SINGLETON`) — destroys the packer's real chain; keep DISABLED.
- **P2+0x10 self-pointer** — makes vtable = P2 (data struct); replaced by the VTABLE-page redirect.

## Kill-criterion
If, after producing a decrypted DEX, ijiami repackaging proves infeasible **and** a custom-loader rebuild
is out of scope, Goal 1 is not worth further tokens — deliver **Goal 2** instead.

---

## Sessions 24–27 handoff (the concrete code to build on)

### What changed in `_scratch/Unpack.java` (sessions 24–27)
- **cE hook (`INSTALL_BLX_R5_HOOK`, `0x1203b72a`)** now dumps the `<class>\0<sig>\0` name from `r2`, the
  full pointer chain, AND, when `r5==0`, can optionally write a real unidbg `jobject` handle
  (`vm.addGlobalObject(getMockApplicationInfo(vm))`) to `*r1` before forcing `r5=VTABLE_STUB`.
  **`MOCK_HANDLE_WRITE=false`** (disabled — it makes the crash WORSE, see Blocker 1).
- **cB-blx hook (`0x1203a7a4`)** and **cC-blx hook (`0x1203a818`)** — force `r5=VTABLE_STUB`, write `1` to
  `*r1`, re-assert `RWX` on `0x12038000`. Not reached in the stable baseline.
- **RegisterNatives hooks** cover ALL 4 call sites (`0x1203795c`, `0x120379ba`, `0x12037a16`, `0x12037a72`)
  and dump each `JNINativeMethod` entry.
- **FindClass hook** dumps class names, stashes handle→name in `findClassNameMap`.
- **POST-JNI / PRE-N.l / at-cE chain dumps** (p26) — traced `0x12082340 → P1(0x120868e0) → P2(0x120868f0)`,
  `vtable = *(P2+0x10)`.
- **`FORCE_WRITE_SINGLETON=false`** (p26) — the SINGLETON force-write at `0x12082340`/`0x120868e0` is now
  DISABLED; the packer's own chain is preserved. Verified: chain intact PRE-N.l, but N.l's own code zeroes
  `*P1` (at `0x120868e0`) during execution — so even with the force-write off, cE's vtable chain breaks
  mid-N.l. This is the current frontier.
- **`findClassNameMap`**, **`readCString(Backend,long,int)`**, **`le32val(byte[])`** helpers added.
- Key harness flags: `INSTALL_BLX_R5_HOOK`, `MOCK_HANDLE_WRITE`, `FORCE_WRITE_SINGLETON`, `cB/cC-blx` hooks,
  `INSTALL_CALLEE_BRACKET`, `INSTALL_SYSCALL_HOOK`, `INSTALL_JNI_HOOKS_PRELOAD`.
- **Always positive-control any hook** before trusting a zero/negative result.

### The stable baseline (pure stub — DON'T LOSE THIS)
```
[p22 FIX] r5 was 0 (empty vtable[0x44]); forced to VTABLE_STUB name='android/content/ContextWrapper||()Landroid/content/pm/Applicatio'
[p22 FIX] r5 was 0 (empty vtable[0x44]); forced to VTABLE_STUB name='android/content/ContextWrapper||()Landroid/content/pm/Applicatio'
>>> N.l returned: false
>>> SINGLETON2 dispatch count after N.l: 3
>>> post-N.l STUBS@0x12039458: 0020704770477047... (UNCHANGED — N.l didn't patch stubs)
>>> b2b returned: null
```
This is the STABLE state. No crash, no progress. `N.l` runs to completion, reports failure because the
resolver stub says "no object." The stubs at `0x12039458`–`0x12039461` stay as `bx lr` — `b2b`/`al` are
unpatched. **The fix requires making the packer's vtable[0x44] resolver actually dispatch through unidbg's
JNI so `AbstractJni` mocks can answer `getApplicationInfo()`.** See Next steps.

### Reference files (repo)
- `NEXT-BLOCKER.md` — full part-by-part investigation log (parts 16→22, the FETCH_PROT saga + solve). Does
  NOT cover sessions 24–27; this `GOAL1.md` is the canonical doc for those.
- `_scratch/p22_output.log` — the original stable-baseline run (`N.l returned: false`, stub path).
- `_scratch/p24_baseline_output.log` — the session-24 stable baseline (confirms pure-stub still works).
- `_scratch/p23d_output.log` — the mock-handle path reaching `cB-blx` and `cC-blx` then crashing (garbage LR).
- `_scratch/p25w_output.log` — session-25 RegisterNatives dump with ALL 4 call sites captured (N, N, HM, SE).
  The KEY file — full 8-method `N` registration table with addresses and signatures.
- `_scratch/p25v_output.log` — verbose run: `Find native function Java_s_h_e_l_l_N_l => 0x120381c1` (DVM routing works).
- `_scratch/p25y_output.log` — post-N.l stubs UNCHANGED (`00207047 70477047`).
- `_scratch/p26_output.log` — POST-JNI vtable dump: `*0x12082340=0x120868e0, *P1=0x120868f0`, P2 is a DATA
  struct (ASCII class names/signatures) with VTABLE_STUB at +0x18/0x38/0x40/0x5c/0x60/0x68.
- `_scratch/p26c_output.log` — chain intact PRE-N.l, N.l's own code zeroes `*P1` during execution.
- `_scratch/p27q_output.log` — FIRST run with cB/cC gates fully open (vtable redirect) — phase-2 dispatches fire.
- `_scratch/p27t_output.log` — crash moved to `0x120372e4` (`bl 0x120382de`), P2+0x38 struct experiment.
- `_scratch/p27v_output.log` — the `0x1203731c` dispatch diag: `sl=0x121b1ec0, [*sl]=0x0, r4=0x7f000900`.
- `_scratch/p27w_output.log` — the p27o (P2+0x38→struct) crash: `svc number: 2065` garbage-syscall crash.
- `_scratch/p27x_output.log` — final stable state (p27o reverted): `P2+0x38` back at VTABLE_STUB.
- `_scratch/disasm_cE.py` — offline capstone disassembler for `cE` (`0x1203b6f8`). Reusable.
- `_scratch/a848_hex.txt`, `_scratch/a372e4_hex.txt` — runtime dumps of `0x1203a848` and `0x120372e4`.
- `_scratch/Unpack.java` — the harness: RegisterNatives entry dumps, FindClass name dumps, MOCK_HANDLE_WRITE,
  FORCE_WRITE_SINGLETON, P1-restore WriteHook, vtable redirect, phase-2 diag hooks.

---

## Handoff / ops (verified working session 23)

### Machines
| Host | Addr | Role | Access |
|------|------|------|--------|
| Win11 `.5` | local | Orchestration (this box) | git-bash, `ssh`, `sshpass`, `scp` present |
| Ubuntu `.40` | `192.168.100.40` | **unidbg emulation host** + plugin build | `ssh xtv40` (key-based, see below) |
| TV box `.4` | `192.168.100.4:5555` | rooted test device (network ADB) | `adb connect 192.168.100.4:5555` |
| Android `.37` | `192.168.100.37:2222` | rooted KitKat (SSH) | `ssh root@…:2222` (DSA host key; paramiko must be pinned `2.11.0`) |

### `.40` access — IMPORTANT
- **SSH is key-based now.** Alias `xtv40` in `~/.ssh/config`, key `~/.ssh/id_xtv40`, user `nestor`.
  The old password `ian20jesus` is **dead** — do not use it.
- `/tmp` is **wiped on every reboot.** The asset tree `/tmp/apkx/` disappears. Rebuild it from the APK
  (which survives in `_assets/`):
  ```bash
  ssh xtv40 'mkdir -p /tmp/apkx && cd /tmp/apkx && unzip -oq ~/xtv-ghidra/harness/_assets/live_base.apk'
  # yields /tmp/apkx/assets/ijm_lib/armeabi/libexec.so (435600 bytes) and /tmp/apkx/assets/ijiami.dat
  ```
- `.40` connection drops intermittently; just retry (`ping -n 1 192.168.100.40`, then re-run).

### Emulation harness — build & run
- Local working copy: **`_scratch/Unpack.java`** (NOT the root `Unpack.java`). Remote Maven project:
  `~/xtv-ghidra/harness`, class `com.xtv.Unpack`, source path
  `~/xtv-ghidra/harness/src/main/java/com/xtv/Unpack.java`.
  ```bash
  scp _scratch/Unpack.java xtv40:~/xtv-ghidra/harness/src/main/java/com/xtv/Unpack.java
  ssh xtv40 'export PATH=~/xtv-ghidra/maven/bin:$PATH; cd ~/xtv-ghidra/harness && mvn -q compile \
    && CP="target/classes:$(cat ~/xtv-ghidra/cp.txt)" \
    && timeout 60 java -Xmx3g -Djava.library.path=~/xtv-ghidra/nativelib -cp "$CP" com.xtv.Unpack'
  ```
- Key harness flags (all in `_scratch/Unpack.java`): `INSTALL_BLX_R5_HOOK` (the working part-22 fix at
  `0x1203b72a`), `INSTALL_CALLEE_BRACKET`, `INSTALL_SYSCALL_HOOK`, `INSTALL_JNI_HOOKS_PRELOAD`.
- **Always positive-control any hook** before trusting a zero/negative result — parts 14 & 19 were
  wrong because hooks weren't confirmed live. This lesson is why part 22 succeeded.

### Reference files (repo)
- `NEXT-BLOCKER.md` — full part-by-part investigation log (parts 16→22, the FETCH_PROT saga + solve).
- `ARCHITECTURE.md` — stream pipeline, hosts, cookies, MITM method.
- `_scratch/p21_disasm.txt` — disassembly of the crash region (`0x120381c0`, `0x1203a760`, `0x1203b6f8`).
- `_scratch/p22_output.log` — the run where `N.l returned: false` (blocker solved).

### Commit convention
`session 23 part NN: <summary>` with the trailers already used in the repo history.
