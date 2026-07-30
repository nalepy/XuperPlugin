# Next Blocker — XuperPlugin portalCore

## Status (2026-07-29 session 18 — root cause of the null className fully traced to an unexplored parser function returning failure)

**Traced the null-className bug one level deeper than session 17 left it.** The malloc'd
method-table buffer (`P2+0x18c`) stays empty because the call that's supposed to populate it
(`bl 0x12026d74`, called from `0x12037e10` with `r0`=entries-buffer, `r1`=a format/template
string pointer, `r2`=a stack output buffer) **returns 0 (failure)**, even after fixing its
upstream inputs. `0x12026d74` itself has never been disassembled — that's the session 19
starting point.

**Fixes applied this session, both confirmed working via live traces:**
1. **The real root cause from session17** (by-reference output args at the P2+0x24/+0x38 call
   site, `0x12037c46-4c`: `r2=&sp[0x24]`, `r3=&sp[0x20]` — our `VTABLE_STUB` never writes to
   them, leaving garbage) is now understood precisely, but a `CodeHook` placed directly on that
   call site (`0x12037c46`-`0x12037c4c`) **mysteriously never fires**, despite the code
   demonstrably executing (a `malloc()` call 4 bytes later, at `0x12037c52`, clearly runs — its
   result lands in `P2+0x18c` as expected). Root cause of the non-firing hook not resolved; worked
   around by hooking the *read* point instead (`0x12037c68`, right after `ldrd r5,r4,[sp,#0x20]`
   at `0x12037c64` — confirmed reached) and overriding `r5`/`r4` directly to `2` there.
2. Fixed a **self-inflicted diagnostic timing bug**: an earlier register-dump hook at `0x12037e0a`
   was reading `r0`/`r1`/`r2` *before* the three `mov` instructions that actually set them up for
   the `bl 0x12026d74` call executed — showing stale garbage and wasting real debugging time
   chasing a phantom lead. Moved the hook to `0x12037e10` (the `bl` itself, after all three
   `mov`s complete) — args are now confirmed **fully legitimate**: `r0=0x12240484` (the entries
   buffer base), `r1=0x1208ccf0` (a real in-module pointer, presumably a format string), `r2` a
   valid stack address. **Lesson for future sessions: when register-dump values look like garbage
   right after a fix, check hook TIMING (which side of the `mov`/setup instructions it's on)
   before concluding the fix itself is wrong.**

Despite fully legitimate-looking arguments, `0x12026d74` still returns `0`. This function has
never been disassembled in this project — it's a good candidate for a real utility/parser
function (its low address, far from the `0x1203xxxx` cluster everything else lives in, suggests
a shared helper rather than more obfuscated anti-tamper logic).

Full log: [`SESSION-2026-07-29.md`](SESSION-2026-07-29.md) ("Session 18" section). Handoff:
[`HANDOFF.md`](HANDOFF.md).

### Next steps (ordered) — session 19
1. Disassemble `0x12026d74` (same technique as always: dump post-decryption runtime bytes via a
   wider entry in the `DECRYPTED@0x...` dump loop, then capstone over SSH on `.40`) to find why
   it returns 0 given seemingly-valid `r0`/`r1`/`r2`.
2. Register-dump hooks inside `0x12026d74` itself once disassembled, to catch the actual failure
   condition live (comparisons, early-return branches) rather than guessing from outside.
3. Consider whether the `r1` "format string" argument needs to be something SPECIFIC (not just
   any non-null in-module pointer) — it's resolved via a PC-relative literal at `0x12037dec`-ish
   that was never independently verified to contain a sensible string; dump and print it as a
   C-string to check.
4. Once `0x12026d74` succeeds and the method table gets real name/sig/fnPtr data, confirm
   `FindClass` finally gets a real className, and that `RegisterNatives` fires
   (`0x120379d0`-`0x12037a80`, offset `0x35c`) — still never observed reached in this project.
5. `N.b2b(ijiami.dat)` → decrypted DEX → `scripts/analyze_decrypted_dex.py` (already built and
   tested) → DES key + portal domain → plugin probe.

### Do NOT (session 18 additions)
- Don't assume a `CodeHook` that fails to fire means the address isn't executed — verify via an
  independent side effect first (like we did with the `malloc()` result landing in `P2+0x18c`)
  before concluding the code path is different than expected.
- Don't trust register-dump values without checking hook placement is AFTER whatever `mov`/setup
  instructions the disasm shows leading into the call — a hook one instruction too early reads
  stale registers and looks exactly like a "everything's garbage" finding.

---

## Status (2026-07-29 session 17 — decrypt-loop bypassed; REACHED REAL `FindClass` for the first time; null className pinpointed to an unpopulated malloc'd method-table field)

**Biggest milestone yet: execution now reaches unidbg's REAL, built-in `FindClass` JNI implementation** (`DalvikVM$3`, via a genuine interrupt-based call trampoline at `PC=unidbg@0xfffe00b4`) — the first time ANY run in this project has gotten past all the fake-vtable-object anti-tamper gates into actual DVM/JNI bridge activity.

**Fix applied to clear the session16 decrypt-loop blocker:**
1. `sl`(r10) forced to `6` right after `asr.w sl,r6,#4` at `0x12037dac` (hook the NEXT instruction, `0x12037db0` — hooking `0x12037dac` itself fires before `asr.w` executes and gets overwritten). Bounds the per-entry XOR-decrypt loop to the ~2 plausible real entries instead of ~12,700 bogus ones.
2. A **second, trailing** call to the same decrypt routine at `0x12037dce` (`mov r1,r5; bl 0x1203a314`) reuses the stale `r5` "remaining bytes" countdown as a byte-length argument — with `sl` truncated early, `r5` is still a huge leftover value, so this call also walks off the buffer. **Do NOT** fix this by zeroing `r5` itself (tried first, broke a *later* legitimate reuse of `r5` at `0x12037e0a` as an argument to a real string-table resolver `bl 0x12026d74`, corrupting downstream JNI setup). **Correct fix:** zero only `r1` at the call site (`0x12037dce`, after `mov r1,r5` already copied it, before `bl` executes) — leaves `r5` itself untouched for its later use.

**With both fixes, the decrypt-loop region is now fully clear** (confirmed via `[walk2]` trace reaching `0x12038146`-`0x1203815c`, then `[walk]` reaching `0x120378aa`-`0x120378c2` in the outer caller — new territory).

**New blocker — precisely diagnosed, not yet fixed:** at `0x120378aa`-`0x120378c2` (ground-truth disasm, not guesses):
```
0x120378aa: ldr r0,[r4]          ; r4 = REAL JNIEnv (0xfffe12a0-ish, unidbg-internal - NOT our P2!)
0x120378ac: ldr r2,[r0,#0x18]    ; r2 = REAL FindClass fn ptr (0xfffe00b0, matches JNI spec offset 0x18)
0x120378ae-b2: (GOT chain) sb=*(0x12082340)=P1(sa); r0=*(sb)=P2 (0x120868f0)
0x120378ba: ldr r0,[r0,#0x18c]   ; r0 = *(P2+0x18c) = a malloc(0x3c) pointer (allocated earlier at
                                  ; 0x12037c50-56, size 0x3c = exactly 6× JNINativeMethod structs
                                  ; {name,sig,fnPtr} @ 0xc bytes each - matches our sl=6!)
0x120378be: ldr r1,[r0,#4]       ; r1 = *(malloc_ptr+4) = the className arg for FindClass - NULL,
                                  ; because nothing ever wrote real data into this malloc'd buffer
0x120378c0: mov r0,r4             ; r0 = real env
0x120378c2: blx r2                ; FindClass(env, className=NULL) -> crash in DalvikVM$3
```
**Root cause:** the malloc'd `0x3c`-byte buffer at `P2+0x18c` is almost certainly the REAL native-method table (6 `JNINativeMethod` entries — matches the 6 decrypted entries exactly), but the code between the decrypt loop (`0x12037dc8`) and this `FindClass` call (`0x120378aa`) — which calls `0x12026d74`, `0x1203f9b0`, `0x1207b630` (×2), `0x1207b400` (×3) — is presumably responsible for transforming the raw decrypted entries into real name/signature/fnPtr pointers written into that malloc'd buffer, and it never got there (or got there with wrong inputs) because of our sl/r1 truncation, or because those calls themselves hit more fake/unpopulated objects we haven't found yet. **Not yet traced.**

Full log: [`SESSION-2026-07-29.md`](SESSION-2026-07-29.md) ("Session 17" section). Handoff: [`HANDOFF.md`](HANDOFF.md).

### Next steps (ordered) — session 18
1. Trace the transformation chain between the decrypt loop exit (`0x12037dc8`) and the
   `FindClass` call (`0x120378aa`) live — register-dump hooks at `0x12026d74` (entry args),
   `0x1203f9b0`, and both `0x1207b630`/`0x1207b400` call sites (same methodology as every fix
   this project) to see which one is supposed to write the real name string pointer into
   `P2+0x18c+4` and why it isn't.
2. Alternative/parallel-cheap check: dump `*(P2+0x18c)` (the malloc'd 0x3c-byte buffer) in full
   right before the `FindClass` call — if ALL of it is zero (not just offset+4), the transform
   never ran or ran with a null source; if only some fields are zero, it's a partial/ordering bug.
3. Once `FindClass` gets a real className, it should resolve `s/h/e/l/l/N` (or similar) and let
   `RegisterNatives` finally run for real — confirm via the walk trace reaching
   `0x120379d0`-`0x12037a80` (never yet observed reached in this project) and via `N.l`/`N.b2b`
   resolving (the `IllegalArgumentException: find method failed` should finally stop appearing).
4. `N.b2b(ijiami.dat)` → decrypted DEX → DES key + portal domain → plugin probe. The dry-run
   pipeline for this step is already built and tested: `scripts/analyze_decrypted_dex.py`
   (session16 DES-prep track) — point it at `/tmp/apkx/app_decrypted.dex` once it exists.

### Do NOT (session 17 additions)
- Don't zero `r5` itself to fix the trailing-decrypt-call crash — it's reused legitimately right
  after (`0x12037e0a`) as an argument to a real string/table resolver; zero only `r1` at the call
  site instead (`0x12037dce`), after the `mov r1,r5` copy already happened.
- Don't assume `r0=0x12082340` seen right before the `FindClass` `blx` is the className arg — it's
  an intermediate GOT-chain value (`sb`/P1), overwritten again before the real args (`r0`=env,
  `r1`=className) are set. Read `r1` specifically for the className pointer.

---

## Status (2026-07-29 session 16 — `0x12037c18` REALLY fixed; new blocker is a self-decrypting native-method table with a bogus iteration count)

**`bl 0x12037c18` (session15d/e's blocker) is fully cleared, for real** — not a second fake
object as session15d/e hypothesized. Live register-dump hooks (methodology from the task brief)
proved: `sb`(r9) resolves to our **own already-known** `sa` GOT slot (`0x120868e0`) — the
"different GOT slot" theory in session15d/e was a false lead, corrected this session. `*(sb)=P2`
(same singleton). The real bug was simply that **`P2+0x24`** (`0x12086914`) — a slot nobody had
populated — held `0`, so the call's `that` pointer was `0`, so `*(that+0x38)` read off the mapped
null page as `0`, so `blx r4` jumped to `PC=0` → `FETCH_PROT`. **Fix:** `P2+0x24` = self-ref `P2`
(same trick as `P2+0x10`), `P2+0x38` (`0x12086928`) = `VTABLE_STUB|1`. Verified via the same
register-dump hooks: `that=P2`, `*(that+0x38)=VTABLE_STUB`, `blx` succeeds, stub entered, `r0=1`
returned. Confirmed via walk trace: execution reaches `0x120378a4`/`0x120378a6` (the instructions
right after `bl 0x12037c18`), something no prior run reached.

**New blocker, different in kind — not a null-pointer/vtable-chain bug:** immediately after,
the same enclosing function runs into a **self-decrypting native-method table walk** that crashes
with `UC_ERR_READ_UNMAPPED, address=0x12280001, size=1` at `PC=0x1203a36e` (inside a small
per-entry XOR-decrypt routine at `0x1203a314`, called in a loop from `0x12037dbc`). The loop's
iteration bound (`sl`/r10) is wildly larger than the real table (which, from what's actually at
the base pointer `0x12240484`, appears to hold only ~5-6 real entries) — the loop walks roughly
12,700 non-existent 0x10-byte entries past the real data before hitting unmapped memory. Root
cause not yet found: needs tracing back to where the entries buffer and its size/count are set
up, further back than anything dumped this session. See session 16 section below for full
disasm (via capstone on `.40`, ground truth) of both the decrypt routine and its caller loop.

**Do NOT** re-introduce the "second fake object for `sb`/r9" theory from session15d/e — it's now
disproven; `sb` is our own `sa`. Do NOT assume `cbz r4` at `0x12037c5e` (branching between a
"build string table" path at `0x12037cac` and the "decrypt existing table" path at `0x12037da4`)
is caused by our fixes — `r4` there is the *entries buffer pointer* (`0x12240484`), preserved
across `0x12037c18`'s call frame (it `push {r4,r5,r6,r7,lr}`s and restores them), not the fn ptr
we resolved inside that call. Taking the decrypt path is the CORRECT branch given a non-null
buffer pointer; the bug is downstream (the loop bound), not the branch choice itself.

Full log: [`SESSION-2026-07-29.md`](SESSION-2026-07-29.md) (see "Session 16 (unidbg lever)"
section — the OTHER "Session 16 (DES pipeline prep)" section is unrelated prep work by a
concurrent agent, ignore for this track). Handoff: [`HANDOFF.md`](HANDOFF.md).

---

## Status (2026-07-29 session 15e — BTV extraction + unidbg confirms 0x1203725c bypassed)

**BrasilTV libexec.so + ijiami.dat extracted from .37** without root — APKs are world-readable.
BTV libexec.so is byte-identical to XTV at all hook addresses (ctors, crash site, sanity, GOT).
Unidbg with BTV binary confirms: 0x1203725c blocker **fully bypassed**, execution reaches
0x1201e378 (completes) then crashes at 0x12043545 (null function pointer, LR=0x12037c4f).

**Current blocker unchanged from session15d:** `bl 0x12037c18` needs a second fake object with
different GOT slot chain (`sb`/r9 → `*(obj+0x24)` → `*(that+0x38)`) + `*(obj+0x44)` call arg.

Full log: [`SESSION-2026-07-29.md`](SESSION-2026-07-29.md). Handoff: [`HANDOFF.md`](HANDOFF.md).

---

## Session 16 — `0x12037c18` really cleared; new self-decrypting method-table blocker (2026-07-29 ~20:10-20:50)

### Method used (per the task's proven recipe)
Added register-dump `CodeHook`s at each exact address in session15d/e's partial disasm
(`0x12037c3a`, `0x12037c3e`, `0x12037c40`, `0x12037c42`, `0x12037c4c`) instead of hand-resolving
the literal pool. First run (no fix yet) gave 100% ground truth in one shot:

```
[12037c18] at 0x12037c3a: sb(r9)=0x120868e0        <- THIS IS OUR OWN `sa`! Not a new GOT slot.
[12037c18] *(sb) = 0x120868f0                       <- = P2, our own singleton, confirmed.
[12037c18] at 0x12037c3e: r0(obj)=0x120868f0        <- obj = P2
[12037c18] *(obj+0x24) = 0x0                        <- P2+0x24, never populated -> 0
[12037c18] at 0x12037c40: r0(obj)=0x120868f0 (about to deref +0x44 for call arg)
[12037c18] *(obj+0x44) = 0x0                        <- call arg, unused by our stub, harmless
[12037c18] at 0x12037c42: r1(that)=0x0 (about to deref +0x38 for fn ptr)
[12037c18] *(that+0x38) = 0x0                       <- *(0+0x38) reads the mapped null page -> 0
[trace] 0x12037c18 fn ptr (r4) right before blx = 0x0
FETCH_PROT at PC=0x0                                <- matches the crash reported at session start
```

**Session15d/e's "second, separate fake object via a different GOT slot" theory is DISPROVEN.**
`sb` is our own known `sa` (`0x120868e0`); `*(sb)` is our own known `P2`. There is only ONE
object here, same as every other blocker this session — the bug is just an unpopulated offset,
`P2+0x24`, that nobody had written yet (session15d only got as far as `P2+0x10`/`+0x18`/`+0x35c`
etc., never `+0x24`).

### Real fix applied
```java
final long P2_SLOT_24 = P2 + 0x24; // 0x12086914 -> self-ptr P2 (same trick as P2+0x10)
final long P2_SLOT_38 = P2 + 0x38; // 0x12086928 -> VTABLE_STUB|1
```
Verified via the same register-dump hooks on a second run: `that=P2` (`0x120868f0`),
`*(that+0x38)=VTABLE_STUB` (`0x7f000801`), `blx r4` succeeds, `VTABLE_STUB entry reached` fires,
`r0=1` returned. **Confirmed via the wide walk trace: execution reaches `0x120378a4`/`0x120378a6`**
— the two instructions immediately after `bl 0x12037c18` in the caller — something no prior
session's run ever reached. `0x12037c18` is genuinely, naturally cleared.

### New blocker: a self-decrypting native-method table, walked with a bogus iteration count
The very next thing that happens (still inside the same enclosing function) is qualitatively
different from every blocker so far — not a null pointer, a **loop that reads far past the end
of a small real data buffer**. Disassembled via capstone on `.40` (ground truth, not guesses) —
two ranges:

**`0x12037c50`-`0x12037dcc` (the caller):**
```
0x12037c50: movs r0,#0x3c; blx #0x1207b3c0        ; malloc(0x3c) -> r0
0x12037c56: ldr.w r1,[sb]; str.w r0,[r1,#0x18c]    ; P2+0x18c = malloc'd ptr (a slot we'd never
                                                    ;   touched before - not needed for this path)
0x12037c5e: cbz r4, #0x12037cac                    ; r4 = ENTRIES BUFFER PTR (preserved across
                                                    ;   0x12037c18's call frame - that function
                                                    ;   push{r4,r5,r6,r7,lr}s and restores them,
                                                    ;   so r4 here is NOT the fn ptr resolved
                                                    ;   inside that call - a wrong assumption to
                                                    ;   avoid making). r4=0x12240484 (non-null) in
                                                    ;   our run -> falls through, does NOT take
                                                    ;   the 0x12037cac "build string table" branch.
0x12037cac..0x12037da2: (NOT taken this run) resolves ~15 string-constant pointers via a
    function ptr r4 (re-loaded fresh here, unrelated to the r4 above) and writes them into the
    struct at P2+0x18c - looks like first-time JNI method name/signature string resolution.
0x12037da4: str.w r8,[sp,#8]; add.w r8,sp,#0x128
0x12037dac: asr.w sl, r6, #4      ; sl(r10) = LOOP BOUND, r6 computed by an obfuscated
                                   ; size-rounding formula (0x12037c74-0x12037c9e: asrs/muls
                                   ; pattern typical of a custom allocator's size-class rounding)
                                   ; fed from a count loaded off the STACK at sp+0x20 via
                                   ; `ldrd r5,r4,[sp,#0x20]` at 0x12037c64 - i.e. the TRUE
                                   ; entry count comes from whatever wrote sp+0x20 BEFORE this
                                   ; function was entered - not yet traced, needs a caller-of-
                                   ; caller dump.
0x12037db0: movs r6,#0            ; i = 0
0x12037db2: cmp r6,sl; bge #0x12037dc8   ; loop while i < sl
0x12037db6: mov r0,r4; movs r1,#0x10; mov r2,r8; bl #0x1203a314   ; decrypt entry[i] in place
0x12037dc0: adds r4,#0x10; subs r5,#0x10; adds r6,#1; b #0x12037db2
```

**`0x1203a2c0`-`0x1203a3ae` (the per-entry XOR decrypt routine, called once per 0x10-byte entry):**
```
0x1203a314: push {r4,r5,r6,r7,lr}; add r7,sp,#0xc; str r8,[sp,#-4]!
            ; args: r0=entry ptr (buffer+i*0x10), r1=0x10 (byte count), r2=key ptr (STACK addr,
            ; same 0xe4fff528 every call - a shared key/nonce buffer set up once before the loop)
0x1203a31c: cmp r1,#0; ble #0x1203a380      ; skip if count<=0
0x1203a320: movs r3,#0
loop @0x1203a322: cmp r3,r1; bge #0x1203a3aa
  0x1203a326: ldrb r4,[r0,r3]; ldrb r5,[r2,#1]; eors r4,r5; strb r4,[r0,r3]   ; byte0 ^= key[1]
  0x1203a32e: adds r4,r0,r3                                                  ; r4 = entry ptr
  0x1203a334: ldrb r5,[r4,#2]; ldrb.w r8,[r4,#4]; ... eor with key[3]/key[5]/key[7]/key[9]/
              key[0xa]/key[0xd]/key[0xf]/key[0x11] into entry bytes {2,4,6,8,9,0xb,0xd,0xf}
  0x1203a36e: ldrb r5,[r4,#0xd]     <- THE EXACT FAULTING INSTRUCTION. r4 = entry ptr (buf+i*0x10).
  0x1203a332: adds r3,#0x10; b #0x1203a322    ; (this routine only ever runs ONE 0x10-byte block
                                               ; per call since r1=0x10 fixed - the OUTER loop in
                                               ; the caller is what advances entry-to-entry)
0x1203a380..0x1203a3a8: (else branch, not taken - some other size/hash finalization + a spin-wait
    on `blx #0x1207b2d0` result, unrelated to our path)
```

### The actual data at the entries buffer (dumped live, `entry@0x...` prints before each decrypt)
```
0x12240484: 00000000000000000000000000000000   (index 0 - all zero)
0x12240494: 00000000000000000000000000000000   (index 1 - all zero)
0x122404a4: 00000000000000000000000000000000   (index 2 - all zero)
0x122404b4: 00000000000000000000000000000000   (index 3 - all zero)
0x122404c4: 00000000e8012012e801201202000000   (index 4 - contains 0x120112e8 TWICE - a real
                                                 in-module pointer! - then 0x00000002)
0x122404d4: fc010000f0ffffffffffffffffffffff   (index 5 - 0x000001fc then 0xfffffff0 x3 - looks
                                                 like an "unused/end" sentinel pattern)
```
The loop keeps going past index 5 (confirmed via more `[3a36e]`/`[3a314]` hits at
`0x122404e4`/`0x122404f4`, i.e. index 6/7) and crashes reading `address=0x12280001` — this is
`~0x31b70` bytes / `0x10` = **~12,727 iterations** past the buffer start, i.e. `sl` is orders of
magnitude larger than any plausible real method count for this class. **The loop bound (`sl`) is
the bug**, not the branch choice, not our fixes.

### Hypotheses for the root cause (not yet confirmed — session 17 starting point)
1. The entries buffer (`0x12240484`) and/or the count feeding `sl` may depend on an earlier
   ctor/init step that one of the session 13/14-era `CTOR-SKIP`/`CTOR-PATCH` hooks short-circuited
   — same "uninitialized due to an earlier skip" pattern as every other blocker this session
   (`P2+0x10`, `P2+0x24`, `FLAG_X`, etc). If so, the real fix is finding and populating whatever
   that earlier step should have written (a real small count, e.g. matching the ~2 real-looking
   entries at index 4), not skipping code here.
2. Alternatively, the count could come from a legitimately-decrypted value elsewhere that just
   hasn't been reached yet in the right order because of a fix ordering issue in this harness.

### Recommended next steps (session 17)
1. **Cheapest first (per methodology):** add a `CodeHook` at `0x12037dac` (`asr.w sl, r6, #4`)
   that overrides `r10` immediately after with a small, safe value (e.g. `2`-`6`, matching the
   apparent real entry at index 4) and see if the walk then proceeds cleanly past the decrypt
   loop into the real `RegisterNatives` calls. Fast to try, doesn't require finding the true
   root cause first.
2. If that doesn't satisfy downstream checks (e.g. a checksum over the decrypted table that
   expects the REAL count), trace back further: dump registers/stack at the ENTRY to this whole
   enclosing function (before `0x12037c50`, i.e. wherever `r4`/`r6`/`sp+0x20` are first set up —
   likely the caller of the function that itself calls `0x1201e378`/`0x12037c18`) to find where
   the true count and entries-buffer pointer come from, and whether an earlier ctor-skip broke it.
3. Once the decrypt loop completes without crashing, confirm the walk trace reaches
   `0x120379d0`-`0x12037a80` (offset `0x35c` = JNINativeInterface index 215 = `RegisterNatives`)
   for real this time — this has never actually been observed reached in ANY run this project,
   despite being flagged as "the probable real call site" since session 15.
4. Confirm `N.l`/`N.b2b` resolve (the `IllegalArgumentException: find method failed` that's
   printed every run so far should finally stop). Then `N.b2b(ijiami.dat)` → DES key + portal
   domain → plugin probe.

### Do NOT (session 16 additions)
- Don't reintroduce the "second fake object via a different GOT slot" theory for `sb`/r9 from
  session15d/e — disproven this session; `sb` is our own `sa`, same singleton chain.
- Don't confuse the `cbz r4` branch at `0x12037c5e` with anything our earlier fixes touch — `r4`
  there is the entries-buffer pointer (preserved across `0x12037c18`'s call frame via its own
  `push`/`pop {r4,...}`), not the fn ptr resolved inside that call. Taking the "decrypt" branch
  (non-null buffer) is objectively correct; the bug is downstream in the loop bound.
- Don't assume the crash is another null-pointer/vtable-chain bug like every prior blocker this
  project — this one is a buffer-overrun-shaped bug (loop bound vs. real allocation size
  mismatch), a genuinely different class of problem. Verify with live traces before patching.

### Reproduce
Same harness/script, unchanged interface — `_scratch/Unpack.java` + `_scratch/run_lever_remote.py`.
All new session 16 diagnostics are gated on `jniPhase[0]` and capped (6-8 prints), consistent
with the existing pattern. The crash is deterministic: `UC_ERR_READ_UNMAPPED, address=0x12280001,
size=1, PC=0x1203a36e` every run, ~270ms in.

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
