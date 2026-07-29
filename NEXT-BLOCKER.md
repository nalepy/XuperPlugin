# Next Blocker — XuperPlugin portalCore

## Status (2026-07-29 session 15e — BTV extraction + unidbg confirms 0x1203725c bypassed)

**BrasilTV libexec.so + ijiami.dat extracted from .37** without root — APKs are world-readable.
BTV libexec.so is byte-identical to XTV at all hook addresses (ctors, crash site, sanity, GOT).
Unidbg with BTV binary confirms: 0x1203725c blocker **fully bypassed**, execution reaches
0x1201e378 (completes) then crashes at 0x12043545 (null function pointer, LR=0x12037c4f).

**Current blocker unchanged from session15d:** `bl 0x12037c18` needs a second fake object with
different GOT slot chain (`sb`/r9 → `*(obj+0x24)` → `*(that+0x38)`) + `*(obj+0x44)` call arg.

Full log: [`SESSION-2026-07-29.md`](SESSION-2026-07-29.md). Handoff: [`HANDOFF.md`](HANDOFF.md).

---

## Session 15e — .37 extraction + BTV unidbg confirmation (2026-07-29 ~19:18–19:55)

### .37 APK extraction
- SSH via plink + DSA host key (`ssh-dss 1024 SHA256:TC5s4tWq...`), password empty
- Shell as u0_a70 (Servers Ultimate), no root — DirtyCow, TowelRoot all blocked
- APKs world-readable: `/data/app/com.android.mgstv-1.apk` (35MB), `/data/app/com.interactive.brasiliptv-2.apk` (34MB)
- libexec.so at `assets/ijm_lib/armeabi/libexec.so` inside APK (NOT `lib/armeabi/`)
- Extracted via `unzip -p` → `/htv/` (vfat, fmask=0000, writable+exec)
- Transferred to Win11 via plink `cat` pipe → `_assets/brtv_libexec.so` (435,634 bytes)
- Also extracted `_assets/brtv_ijiami.dat` (4,247,902 bytes)
- Both uploaded to .40 at `/tmp/apkx/assets/ijm_lib/armeabi/libexec_brtv.so` + `/tmp/apkx/assets/ijiami_brtv.dat`

### Root attempts on .37 (all failed)
- **DirtyCow (CVE-2016-5195):** Compiled ARM binary (static, 703KB), ran 200M iterations.
  `/proc/self/mem` writable but writes return I/O error — kernel 3.10.33 has backported fix.
- **TowelRoot (CVE-2014-3153):** APK downloaded to /htv, `pm install` killed (no
  `INSTALL_PACKAGES` permission). libexploit.so extracted, loader compiled, but SELinux
  blocks execstack. Metasploit futex source compilation blocked by missing Android NDK headers.
- **CVE-2015-3636 (PingPong):** Too complex for ad-hoc implementation (physmap spray, 200+ lines).

### BTV vs XTV binary comparison
- INIT_ARRAY same (0x82144, 252 bytes, 63 ctors)
- All hook addresses byte-identical: 0x37289, 0x3725c, 0x2e5d4, 0x3a1d8, 0x3a21e, 0x3a280, 0x370c8
- Swapped `/tmp/apkx/assets/ijm_lib/armeabi/libexec_brtv.so` into Unpack.java, recompiled clean

### Unidbg run (session15e)
- 0x1203725c: **fully bypassed** — no crash, walked through to 0x12037878 region
- 0x1201e378: completed and returned to caller (VTABLE_STUB slots pre-populated working)
- **New crash:** `UC_ERR_FETCH_PROT` at PC=0x0, inside JNI function at 0x12043545,
  LR=0x12037c4f — null function pointer from second-level object deref chain.
  Matches session15d blocker: `bl 0x12037c18` needs second fake object.

### Git note
Two agents shared git index — `_assets/brtv_*` binaries landed in commit 8361a82 (session15d
"disasm") instead of 3ebe428 (intended "extract" commit). Verified consistent in be5a5eb.
No data loss — commit messages only.

---

## Status (2026-07-29 session 15d — `0x1201e378` fully cleared; new object needed for `0x12037c18`)

**`0x1201e378` (the function reached after bypassing kill()) now completes successfully and
returns to its caller.** It turned out to reuse our `P2` object as a fake "env"/interface at
FIVE MORE offsets beyond `+0x40` (`+0x60`, `+0x5c` ×5, `+0x18`, `+0x68`, `+0x35c`) — populated
all of them with the same real, valid stub; confirmed via trace hooks the whole function walks
through cleanly and returns (`[walk] 0x120378a4` = the instruction right after its `bl` returns).

**New blocker:** the very next call, `bl 0x12037c18`, crashes with `FETCH_PROT` almost
immediately. Its disasm (already captured, partial — only 0x40 bytes so far) shows it uses a
**different GOT slot entirely** (via register `sb`/r9, not our known `r6`/`0x12082340` chain),
reading `*(obj+0x24)` then `*(that+0x38)` for a function pointer, plus `*(obj+0x44)` for the
call's `this` arg — i.e. a **second, separate fake object** needs to exist, not just more
offsets on `P2`. Not yet resolved/fixed.

Full log: [`SESSION-2026-07-29.md`](SESSION-2026-07-29.md). Handoff: [`HANDOFF.md`](HANDOFF.md).

---

## Session 15 — ground-truth disasm + real vtable fix + new kill() blocker (2026-07-29 ~17:55–18:30)

Previous sessions guessed at the meaning of `0x120370c0`'s "singleton/vtable+0x40" from static
byte dumps taken **before** self-decryption ran, and from stale file-offset disasm. This session
dumped the **runtime, post-decryption** bytes directly from the live unidbg process (after
`JNI_OnLoad` executes, since ctors decrypt this code as a side effect) and ran real capstone
disasm against them over SSH. That produced exact, verified ground truth for the first time.

### Verified disasm: `0x120370ba`–`0x120370e0` (the vtable+0x40 check)
```
0x120370ba: ldr r0,[pc,#0x30]   ; r0 = literal
0x120370bc: add r0,pc           ; r0 = GOT slot 0x12082340  (resolved: pc(0x120370c0)+lit(0x4b280))
0x120370be: ldr r0,[r0]         ; r0 = P1 = *(0x12082340)
0x120370c0: ldr r0,[r0]         ; r0 = P2 = *P1            <- the real "singleton" object
0x120370c2: ldr r1,[r0,#0x40]   ; r1 = *(P2+0x40)          <- the vtable slot (was NULL)
0x120370c4: mov r0,r5           ; r0 OVERWRITTEN with r5 (JavaVM*) - "obj(r0)" seen at the
                                ;   call site is a red herring, NOT the vtable object
0x120370c6: blx r1              ; call
0x120370c8: ldr r0,[sp]
0x120370ca: bl #0x12037878      ; another check right after
0x120370ce..: cmp/compare, success path returns; else `blx #0x1207b310`
```
Harness already sets `*0x12082340=sa(0x120868e0)`, `*sa=sb(0x120868f0)` → so **P1=sa, P2=sb**.
Session 14 called `sb` the "classname buffer" — correct, ctors write a string there, so
`P2+0x40` was naturally NULL. That's the real bug, now fixed for real (see below) — **not**
by PC-skipping the `blx` (session 14's approach), but by populating the actual memory the
real instructions read, so the call executes with full real semantics (real LR, real return).

### Real fixes applied (in `_scratch/Unpack.java`, all verified by dumping the values back)
1. **`VTABLE_STUB`** — 4-byte Thumb stub `movs r0,#1; bx lr` written into the SCRATCH page
   (mapped R|W|**X** now, was R|W only). This is a genuine, valid, callable function — not a
   PC-skip.
2. **`P2+0x40` (`0x12086930`) = VTABLE_STUB|1`** — written *before* calling `JNI_OnLoad`, so
   `blx r1` at `0x120370c6` now calls real code and returns normally (verified: hook log shows
   `r1 already valid (pre-write worked), no override`).
3. **`P2+0x188` (`0x12086a78`) = SCRATCH ptr (non-zero)** — a second guard found via disasm of
   `0x12037a80` (`ldr r0,[r0,#0x188]; cmp r0,#0; beq →kill()`). Was 0 (CTOR-PATCH only ever
   wrote a *different* object's `+0x188`, at `0x120923c0`, never P2's). Fixing this skips one
   of the early direct-to-kill() branches (verified via trace hook: `cmp-r0-after-P2+0x188 (r0)
   = 0x7f000000`, non-zero, branch not taken).
4. **`P2+0x109` (`0x120869f9`) = 0`** — a third guard, read inside the kill()-block itself
   (`ldrb r0,[r0,#0x109]; cbz r0,→skip-abort-detour`). Zeroed to skip the extra SIGABRT(6) call.

### New true blocker: unconditional `kill(pid, SIGKILL)`, confirmed via live experiment
Even with all 3 object fields correctly populated, execution still reaches `0x12037b80` and
issues `kill(pid, 9)`. Traced the **entire branch chain** with trace-only hooks (no `jniPhase`
side effects) to find out why:
```
0x12037a92-9c: r8 = *(0x12082340) = P1(sa)      ; SAME got-chain, one less deref than P2
0x12037a9c-a0: r0 = *(*(r8)+0x188) = *(P2+0x188) ; our fix #3 above → non-zero → branch #1 skipped
0x12037aa8-ae: blx #0x1207b5a0 (forced ret=1 by an existing session-13 hook) → branch #2 skipped
0x12037ab2-bc: real getpid() svc → r4 = pid (e.g. 0x171f = 5905)
0x12037abe-c2: r5 = literal constant 0xfffff000 (resolved via disasm); cmp r0(pid),r5; bls
               → ALWAYS TAKEN (any real pid ≪ 0xfffff000) → unconditionally jumps to 0x12037b4a
```
Critically, this `bls` branch target is **`0x12037b4a`, not `0x12037b40`** — it skips the
`movs r4,#0` reset at `0x12037b42`, so **r4 keeps the real pid** all the way to the final
`svc` at `0x12037b88` (`kill(r4=realpid, r1=9)`). This branch is **unconditional** (the
0xfffff000 threshold is not a real gate, it's a "this is always true" pid-vs-huge-constant
check) — meaning **every path that reaches `0x12037a92` ends in a real `kill(pid,9)`**,
regardless of the P2 object fields.

**Direct experiment (proves this is not patchable post-hoc):** temporarily let the real `svc`
execute instead of PC-skipping it. Result:
```
java.lang.UnsupportedOperationException: SIGKILL pid=5919 is fatal and does not return (emulated abort)
	at com.github.unidbg.linux.AndroidSyscallHandler.kill(AndroidSyscallHandler.java:712)
```
unidbg **deliberately** models `kill(_, SIGKILL)` as non-returning (matches real Linux: SIGKILL
can't be caught). No register or memory patch after the `svc` can undo this — the only real
fix is preventing this code from being *reached* at all. Reverted this experiment; harness is
back to the existing (session 13) soft-ret + forced-`JNI_VERSION_1_6` fallback, which works but
means we still get a **forced**, not natural, completion — so `RegisterNatives` still never
fires and `N.l`/`N.b2b` remain unregistered (same symptom as session 14, different, now fully
understood root cause).

### Do NOT (session 15 additions)
- Don't try to "fix" the `getpid()`-vs-`0xfffff000` compare — it's not a real gate, always true.
- Don't let the real `svc #0` execute at `0x12037b5e`/`0x12037b88` — unidbg throws, confirmed.
- Don't confuse `obj(r0)` at the `0x120370c6` `blx` hook with the vtable object — it's `r5`
  (JavaVM*), overwritten right before the call. The real object is `P2` (`=*(*(0x12082340))`).

### Important lead — probable real `RegisterNatives` call site (NOT yet confirmed reached)
Disassembly of `0x120379d0`-`0x12037a80` (dumped this session, not yet in prior sessions) shows
a pattern of indirect calls through a JNIEnv-shaped vtable (`ldr r0,[r4]; ldr r2,[r0,#OFFSET];
blx r2`) at offsets `0x18`, `0x68`, and — critically — **`0x35c`** twice
(`0x12037a10`/`0x12037a6a`: `ldr.w r6,[r0,#0x35c]; ... blx r6`). Offset `0x35c` (`860/4=215`) is
the well-known `JNINativeInterface` table index for **`RegisterNatives`** on 32-bit Android —
each call is immediately followed by `cmp r0,#0; bmi → failure path` (JNI's real "negative on
error" convention), which is exactly RegisterNatives' real signature/semantics.

**However:** added entry-hooks at `0x12037a80`, `0x120370fa`, `0x120370fe`, `0x120371a6`, and at
the two suspected `blx r6` call sites themselves (`0x12037a16`, `0x12037a72`) — **none fired**
in a full run, even though hooks *inside* the same `0x12037a80` block (`0x12037aa4`, `0x12037ac0`,
`0x12037b4a`) fire every time. This means the current execution path reaches `~0x12037a92`
(mid-block) via some indirect jump that **skips both `0x12037a80`'s own entry instruction and
all of `0x120379d0`-`0x12037a80`'s RegisterNatives-shaped code** — i.e., **the RegisterNatives
calls found this session are very likely NOT on our current path at all**, or are reached only
via a different caller/branch we haven't identified yet. Do not assume they're the fix without
re-verifying reachability first.

### RESOLVED this session — the `0x12037a80`/`0x12037a92` mystery, and the kill() blocker itself
The lead above (RegisterNatives-shaped code at `0x120379d0`-`0x12037a80` not being reached) was
correct: **it wasn't the real path at all.** A wide execution-walk hook (log every distinct
address visited across `0x120370e0`-`0x12037b90`, one run) found the *actual* fork:

```
0x120370c8-0x120370ca: (right after our vtable fix)  bl #0x12037878    <- calls this, for real
0x12037878-0x1203789a: real function prologue, then:
  0x12037892: ldr r0,[pc,#0x310]; add r0,pc  -> resolves to 0x12092944 (verified: literal
              0x5b0ac + pc(0x12037898) — same resolution method that correctly found
              0x12082340 earlier)
  0x12037896: ldrb r0,[r0]                    -> FLAG_X byte, was 0
  0x12037898: cmp r0,#0
  0x1203789a: beq.w #0x12037a92                -> ZERO takes this branch straight into the
              anti-tamper/kill() region (0x12037a80-b40) that sessions 14/15a/15b got stuck in.
              This is *why* `0x12037a80`'s own entry instruction and the RegisterNatives-shaped
              code at `0x120379d0` never appeared reachable - they're on the OTHER (real-init)
              side of this branch, never the anti-tamper side.
```

**Fix:** `mem_write(0x12092944, {1})` — set FLAG_X non-zero *before* calling `JNI_OnLoad`.
**Verified via the same walk trace:** execution now goes `0x1203789a → 0x1203789e → 0x120378a0`
(the real-init side: `bl 0x1201e378; bl 0x12037c18`) instead of `→ 0x12037a92`. **Zero kill()
hits in this run** — the entire anti-tamper/kill()-loop blocker from sessions 14/15a/15b is
gone. This is the furthest any session has gotten into this binary.

### `0x1201e378` traced fully and CLEARED (session 15d)
Its unmapped read (`address=0x412f6db0`) was `*(garbage_from_P2+0x10)+0x40` — traced live and
confirmed the *actual* chain: `ldr r6,[pc]->0x120868e0(=sa)`; `ldr r0,[r6]->P2(0x120868f0)`;
`ldr r0,[r0,#0x10]` (P2+0x10, which we'd never touched — held garbage `0x412f6d70`); `ldr
r5,[r0,#0x40]` → `0x412f6d70+0x40=0x412f6db0` → unmapped. **Fix:** point `P2+0x10` at `P2`
itself (self-reference) — `P2+0x40` is already our real, valid `VTABLE_STUB`, so this reuses
working infrastructure. Verified: the chain resolves to our stub, `blx` succeeds, returns `1`.

Full disasm of `0x1201e378` (0x180 bytes dumped, all resolved) showed it's a **repeated pattern**
— `P2` used as a fake "env"/interface object at FIVE more offsets: `+0x60`, `+0x5c` (called 5×,
a `DeleteLocalRef`-style cleanup pattern), `+0x18`, `+0x68`, `+0x35c`. Populated **all** of them
with the same real stub. **Verified via the wide walk trace: `0x1201e378` now completes and
returns to its caller** (`[walk] 0x120378a4` — the instruction right after its `bl` returns).
This is the deepest any session has gotten.

### New blocker (session 15d) — `bl 0x12037c18` needs a SECOND, separate fake object
The very next call in the same caller (`0x120378a4: mov r0,r4; bl 0x12037c18`) crashes almost
immediately with `FETCH_PROT`. Partial disasm (only first 0x40 bytes dumped so far) shows it
does **not** reuse our `r6`/`0x12082340`/`P2` chain — it resolves a **different** GOT slot into
register `sb`(r9), then:
```
0x12037c3a: ldr.w r0,[sb]           ; r0 = *(new_obj)
0x12037c3e: ldr r1,[r0,#0x24]       ; r1 = *(new_obj+0x24)
0x12037c40: ldr r0,[r0,#0x44]       ; r0 = *(new_obj+0x44)   <- becomes the call's "this" arg
0x12037c42: ldr r4,[r1,#0x38]       ; r4 = *(r1+0x38)         <- the actual function pointer
0x12037c4c: blx r4
```
So this needs: (a) the GOT slot feeding `sb` to point at a real object, (b) that object's
`+0x24` to point at ANOTHER object with a valid `+0x38` function pointer, (c) that SAME first
object's `+0x44` to be a valid arg (probably fine to be the same self-referencing trick as `P2`,
but needs its own slot). Not yet resolved — the GOT literal for this hasn't been computed
(instruction is `ldr r0,[pc,#0x3a8]` at `0x12037c24`, whose literal falls outside the 0x40 bytes
dumped so far; need a wider dump, same technique as always).

### Next steps (ordered) — session 16
1. **Resolve the new GOT slot** feeding `sb` at `0x12037c32-36` (dump ≥0x400 bytes from
   `0x12037c18` to reach the literal pool, or just extend the existing dump range in
   `_scratch/Unpack.java`'s dump loop and resolve the literal exactly like `FLAG_X`/`GOT_X`
   were resolved this session).
2. Check whether that slot is uninitialized (garbage, same story as everything else this
   session) or points somewhere valid already. If uninitialized: decide whether to point it at
   `P2` (reusing existing infra, if the `+0x24`→`+0x38` chain can tolerate it) or build a small
   second fake object at a fresh `mem_map`'d address with its own `+0x38` stub — mirror
   whichever is simpler once the exact expected shape is confirmed via live trace hooks
   (register dumps at `0x12037c3a`/`0x12037c3e`/`0x12037c40`/`0x12037c42`, same pattern used for
   every fix this session).
3. Once `0x12037c18` completes, the walk trace should reach the real `RegisterNatives` calls at
   `0x120379d0`-`0x12037a80` (offset `0x35c` = JNINativeInterface index 215) — confirm `N.l`/
   `N.b2b` resolve after that.
4. Only then: `N.b2b(ijiami.dat)` → DES key + portal domain → plugin probe.

### Do NOT (session 15c/d additions)
- Don't reintroduce the kill()-hook's forced-completion path as the primary strategy — FLAG_X
  bypasses the entire anti-tamper region for real now; the kill()-hook is dead code on this path
  (harmless to leave in as a safety net, but don't rely on it going forward).
- Don't assume every "fake env" offset needs its own distinct object — `P2` self-referencing
  worked for `+0x10`/`+0x40`/`+0x60`/`+0x5c`/`+0x18`/`+0x68`/`+0x35c` because the calling code
  only checks "is the return value non-zero", not real semantics. Try the cheap self-ref trick
  before building anything more elaborate.

### Reproduce (updated for session 15d's harness)
Same as before — `_scratch/Unpack.java` + `_scratch/run_lever_remote.py`, unchanged interface.
All diagnostics (`[trace]`/`[walk]`/`[ENTRY]`/`[1e378]` lines, every `P2+0x..`/`FLAG_X`/
`P2_SLOT_..` print) are gated so they don't affect behavior, just visibility. Current run
crashes inside `0x12037c18` (a *new*, further-in* FETCH_PROT) — that's the expected/correct
frontier; it means everything before it (vtable+0x40, the kill() region, all of `0x1201e378`)
is now genuinely working, not just forced.

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
