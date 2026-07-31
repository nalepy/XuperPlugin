# Next Blocker — JNI-env-mprotect RULED OUT (part 18, valid positive control); next suspect = guest mprotect LINUX SYSCALL (SVC r7=125)

## Update (session 23 part 18) — JNI hooks done right: PROVEN live, and N.l makes ZERO JNI-env calls before the crash → EXEC-loss is NOT JNI-triggered

Rebuilt #2 correctly (harness `_scratch/Unpack.java`, log `_scratch/p18_output.log` / `.40:~/xtv-ghidra/p18.log`):
- **Installed the 8 JNI SVC hooks BEFORE `loadLibrary`** (part 14 installed them after, missing the init window — its whole "zero hits" was an artifact).
- **Range-hooked `stub_base .. stub_base+8`**, not a single address. This is the fix that matters: FindClass fires at **both** `0xfffe00b0` and `0xfffe00b4` (base **+4**) — a single-addr hook (part 14) structurally misses the +4, exactly the `Unpack.java:819` warning.
- **Added a real positive control:** snapshot JNI counts right before N.l. Result **PASS — 8 FindClass hits** (lr `0x120378c5`/`0x1203799b`/`0x120379e3`/`0x12037a41`, all libexec pre-N.l class resolution). Hooks are demonstrably live.

**With the hooks PROVEN firing, the negative is now trustworthy (unlike part 14):**
```
[p18 POSITIVE-CONTROL] PASS — 8 JNI hook hits so far (incl. FindClass base+4); hooks are LIVE
[p18 POST-N.l] JNI-env calls made DURING N.l = 0 (before=8 after=8)
[p18 VERDICT] N.l made ZERO JNI-env calls before the 0x120381c1 fault
```
**No JNI env function is called between N.l entry and the `0x120381c1` FETCH_PROT fault.** The JNI-env-triggered-mprotect branch (reopened by the part-15 audit) is now **ruled out with evidence**, not left unconfirmed.

Confirmed along the way: FindClass entry `r0 = 0xfffe12a0` = the JNIEnv pointer = the crash's `arg[0]`. So `s/h/e/l/l/N.l` genuinely receives JNIEnv as arg0 (it's a JNI method), but the fault is a code-fetch on a de-EXEC'd page, not a JNI dispatch.

### Next suspect (part 19) — guest calls the `mprotect` LINUX SYSCALL directly (SVC, r7=125)
Real Linux syscalls do **not** go through the JNIEnv function table — they hit unidbg's `SyscallHandler`. An `mprotect(0x12038000, len, prot_without_EXEC)` issued by guest code (the packer's own anti-tamper), or unidbg's own `AndroidElfLoader` PT_LOAD re-processing, would strip EXEC exactly as observed and would be **invisible to every hook tried so far** (all of which targeted JNI stubs or guest `bl` targets). This is the one un-instrumented path left.

**Concrete build for next session:**
- Hook the ARM `svc` path / override `SyscallHandler` (or add a code hook on the syscall stub) and log every `mprotect`/`mmap2`/`mprotect`-family syscall: NR (r7), r0=addr, r1=len, r2=prot. Flag any whose `[addr, addr+len)` covers `0x12038000` and whose prot lacks `PROT_EXEC`.
- ARM32 syscall numbers: `mprotect`=125, `mmap2`=192, `munmap`=91. Watch all three.
- Also instrument unidbg host-side: `AndroidElfLoader.java`'s own `mem_protect` on `PT_LOAD` segments (flagged part 14) — put a breakpoint/log there to see if a second load pass re-permissions the segment.
- If the syscall path is also clean, the remaining possibility is a Unicorn-internal TB/permission quirk keyed to this specific sub-page — at which point test mapping `0x12038000` as its OWN standalone page (separate `mem_map`, not a sub-range of the big segment) so nothing can split it.

---

## Update (session 23 part 17) — dropped the lone static protect, crash byte-identical → region-split DEAD; pivot to JNI

Part 16 cleared our dynamic protect churn and left exactly one suspect: the single static `mem_protect(0x120381c0 & ~0xfff, 0x2000, RWX)` right before N.l (`Unpack.java:1680`), a 0x2000 sub-range of the larger libexec segment (module base `0x12000000`) — the Unicorn region-split theory.

Gated it behind a new flag `BISECT_NO_STATIC_PROTECT = true` (Unpack.java:39) and ran on `.40`.

**Result: byte-for-byte identical crash** (`p17.log` on .40). Marker `[BISECT p17] static pre-N.l protect DISABLED` fired at line 694, then line 771:
```
RX@0x120381c1[libexec.so]0x381c1 ... UC_ERR_FETCH_PROT ...
address=0x120381c1, arguments=[unidbg@0xfffe12a0, 1064202203, 914356853, 1359953204]
```
Same PC, same FETCH_PROT, same dispatch count 3. **The static protect was a no-op** — it neither caused the EXEC loss nor masked it (crash did NOT move earlier). **Region-split hypothesis is ruled out.** The flag stays `true` (proven irrelevant; leaving it on removes the dead sub-range protect for good).

**New live lead (the pivot):** the faulting call's `arg[0] = 0xfffe12a0` sits inside the JNI SVC-stub region (`0xfffeXXXX`). The function crashing at `0x120381c1` is being handed a **JNIEnv-shaped pointer** — direct evidence the JNI-dispatch branch (reopened by the part-15 audit) is where the EXEC-loss trigger lives. This is now the top hypothesis, not just "not ruled out."

**Next step = #2, being built this session (part 18):** JNI SVC hooks done correctly —
- install the 8 env-function hooks BEFORE `JNI_OnLoad`/init (part 14 installed them after; that's why it saw zero hits)
- positive-control the FindClass hook against the known init call (log lines 409-413) before trusting any zero-hit result
- hook a small range `stub_base .. stub_base+8`, not a single address (the `Unpack.java:819` note shows FindClass fires at base **+4**)
- goal: identify which JNI fn fires immediately before `0x120381c1`, and whether its SVC handler triggers a host-side mprotect that strips EXEC from `0x12038000`.

---

## Update (session 23 part 16) — bisection: disabled all our dynamic mem_protect on 0x12038000, crash IDENTICAL

After the part-15 audit reset my thinking to "suspect our own machinery first," ran a clean bisection. We had **three different-sized** `mem_protect` calls hammering the same base `0x12038000` (0x1000 in the EventMemHook canary, 0x2000 in the SINGLETON2-dispatch nudge, 0x4000 in the LastResortHook) — inconsistent sizes on one base is a known way to fragment Unicorn's internal region/permission tables. Strong prior suspect for silently clearing EXEC on a sub-page.

Gated all three off (`BISECT_NO_DYNAMIC_PROTECT = true`), leaving only ONE static `mem_protect(0x12038000, 0x2000, RWX)` right before N.l.

**Result: byte-for-byte identical crash.** Same address `0x120381c1`, same `UC_ERR_FETCH_PROT`, same `N.l threw`, same `SINGLETON2 dispatch count 0→3`, same dispatch LRs (0x1203a6a5, 0x1203a6b5, 0x12038273). Our dynamic per-hook protect churn is **definitively not the cause** of the EXEC loss — clean negative, this is what the bisection was for.

**Not yet ruled out (the honest remaining sub-hypothesis):** the single *static* pre-N.l `mem_protect(0x12038000, 0x2000, RWX)` itself. The page was originally mapped by `loadLibrary` as part of one large libexec.so segment (base 0x12000000); calling `mem_protect` on a 0x2000 *sub-range* of that larger mapping is exactly what splits Unicorn's region list, and a split could leave a neighboring sub-page mis-permissioned. The bisection removed the dynamic churn but not this static split. **This is the next thing to test:** either drop the static protect entirely (see if the crash moves earlier — proving something else makes it non-exec — or stays), or replace it with a protect of the full segment-aligned region so no split occurs.

Also still open (from part 15): the JNI-triggered-mprotect branch, and the real JNI call sequence during N.l (needs hooks installed before init + a positive control).

## CORRECTION (session 23 part 15 audit) — parts 13-14 over-claimed; retract the "JNI ruled out" conclusion

An audit of parts 13-14 against the actual `output.log` found the headline conclusion is **false**:

- Part 14 claimed "zero real JNI calls happen at all before the crash." But the same run's log shows **FindClass being called** at lines 409-413 (`r2=0xfffe00b0`, the real FindClass SVC stub, invoked via `blx r2`) during JNI_OnLoad init.
- The reason the part-14 hooks showed zero hits: they were **installed at line 682, after the init-phase JNI activity at lines 402-413 had already happened**. The hooks only covered the window from just-before-N.l (line 738) onward. "Zero hits" means "no JNI env call during N.l's window," NOT "no JNI dispatch at all."
- Compounding flaw: **no positive control was ever run.** A single-address `CodeHook` was placed at each SVC stub *base* (e.g. FindClass at `0xfffe00b0`), but this session's own earlier note (`_scratch/Unpack.java:819`) records the FindClass interrupt firing at `0xfffe00b4` (base **+4**). It was never confirmed that a CodeHook at the stub base fires at all when the function is called — so even "no JNI during N.l" is not safely established.

**What is actually still true:** parts 13-14 did NOT rule out a JNI-triggered mprotect. That whole branch is back open. The `vm.getJNIEnv()` address-resolution technique is still valid and useful; the *conclusions drawn from the zero-hit result* are retracted.

**Corrected next step for the JNI angle:** re-run the SVC hooks but (a) install them BEFORE JNI_OnLoad / the init phase, not before N.l, so they cover the whole run; (b) add a positive control — confirm the FindClass hook actually fires on the known init-phase call at line 409 before trusting any zero-hit result; (c) hook a small range around each stub (base .. base+8) or use an InterruptHook keyed on SVC, not a single-address CodeHook, to avoid the +0/+4 miss.

---

## (RETRACTED — see correction above) Update (session 23 part 14) — zero real JNI calls happen at all; structural finding, not just another eliminated guess

Extended part 13's technique to 8 JNI env functions at once (`FindClass`, `GetObjectClass`, `IsInstanceOf`, `GetMethodID`, `NewObject`, `CallObjectMethodV`, `CallBooleanMethodV`, `CallVoidMethodV`), each hooked at its real runtime SVC address via `vm.getJNIEnv()` (same ground-truth method as part 13, offsets from unidbg-android's `DalvikVM.java`, fetched from GitHub). All 8 installed cleanly with real resolved addresses (`0xfffe00b0` through `0xfffe0430`).

**Zero hits on all 8, for the entire run.** Not one real JNI env function is called before the crash.

**This reframes the whole session.** Every "JNI-vtable-shaped" indirect call the parallel agents identified in parts 10-11 (the `ldr r3,[rX,#N]; blx r3` pattern through a global struct at offsets 0x10/0x1c/0x38/0x44/0x98/0xc4/0xd4/0x18c) is dispatching through **our own fake `SINGLETON` struct** (`0x7f002000`, the blanket-filled stub we authored this session), not through unidbg's real `JNIEnv` (which lives at a completely different address, resolved via `vm.getJNIEnv()`). They *looked* JNI-vtable-shaped because that's genuinely the pattern the packer's code was written against — but since `SINGLETON` is synthetic, none of those calls ever reach real JNI dispatch. The old session 18/21 "NewObjectV" comment likely describes either a different code path entirely (something before N.l is invoked, not traced this session) or intended/expected behavior on real Android that this emulation's fake-struct approach structurally can't reach.

**Consequence**: the "search unidbg source for host-side JNI-triggered mprotect" direction from part 13 is now also closed — there's no JNI call for it to hang off of. If the mprotect is host-side at all, it's triggered by something other than a JNI env function call (maybe by an SVC unrelated to JNI, maybe by unidbg's ELF-loader path if some code re-triggers a `dlopen`/library-load pass — worth checking for that specifically, `AndroidElfLoader.java` has its own `mem_protect` call tied to `PT_LOAD` segment processing, found during this search but not yet investigated for relevance).

## Update (session 23 part 13) — went host-level, got real unidbg source, ruled out the old NewObjectV theory for good

Pulled unidbg-android 0.9.10-SNAPSHOT source directly from GitHub (`gh search code` / `gh api`, no auth needed — public repo `zhkl0228/unidbg`) instead of guessing. Found `DalvikVM.java`'s actual `_NewObjectV` implementation:

```java
DvmClass dvmClass = classMap.get(clazz.toIntPeer());
DvmMethod dvmMethod = dvmClass == null ? null : dvmClass.getMethod(jmethodID.toIntPeer());
if (dvmMethod == null) { throw new BackendException(); }
```

This lines up exactly with the old session 18/21 comment ("NewObjectV triggers re-entry... self-nukes the page with mprotect") — a `BackendException` thrown from *inside* an SVC handler (Java code, not guest ARM) during a bad/unregistered class lookup is a very plausible source of whatever internal unidbg cleanup does the mprotect.

**Tested it directly instead of assuming.** `VM` exposes `getJNIEnv()`; walked `env->impl->functions[0x74]` (matching `DalvikVM`'s own `impl.setPointer(0x74, _NewObjectV)`) to get the *real* SVC stub address at runtime (`0xfffe0220` this run), and hooked it directly — not a guess, the actual dispatch point.

**Result: the hook never fired.** `_NewObjectV` is never called at all during this entire run, right up to the crash. This **definitively rules out** the specific NewObjectV theory — not "not found in the bytes we looked at" like earlier rounds, but confirmed via ground truth at the real dispatch point that it simply never happens. Most likely explanation: our existing NOP patch at `0x12038226` (confirmed applied every run, described back in session 18/21 as removing exactly this NewObjectV call) is doing its job completely — it was never partially effective as part 9 assumed, it just doesn't cover *this* particular EXEC-loss symptom because that symptom has a different cause than the one that patch was written for.

**Where this leaves us**: the true trigger for `0x12038000` losing EXEC is still unknown. Every specific theory tested this session (SINGLETON stub, FETCH LIMIT, mapping-churn corruption, guest-code mprotect SVC, NewObjectV) has been concretely ruled out with real evidence, not just left unconfirmed. That's real progress in the "eliminate the impossible" sense, but there's no remaining strong hypothesis queued up — the next session needs fresh ideas, not more testing of what's already been tried.

**Session close-out**: 22 remote runs, 13 parts, 10 parallel agents, real unidbg GitHub source consulted, across one very long session. Two real bugs fixed and confirmed (SINGLETON2 dispatch 0→3), one anti-tamper kill-switch discovered, and five distinct theories for the remaining EXEC-loss bug definitively eliminated with hard evidence. This is the actual stopping point.

## Update (session 23 part 12) — entry-counter data kills the "short-circuit the 2nd call" idea

Tried the cheapest test first, as planned: added a single-address `CodeHook` at `0x120381c0` (the crashing function's own entry) to count successful entries and log each one's `LR` (caller). Result:

```
>>> [fn@0x120381c0 entry #1] called from LR=0xffff0000
```

**Only one successful entry, ever** — confirming the "re-entry" theory. But its `LR` is `0xffff0000`, a synthetic sentinel, not a real guest-code address — this is the harness's own top-level JNI dispatch calling `N.l()` directly from Java (via `N.callStaticJniMethodBoolean`), not a normal `bl`/`blx` from other ARM code. So this is the *original*, first-ever call into this function, not a recursive self-call from within its own body as part 9 assumed.

**A `CodeHook` architecturally cannot catch the second (failing) entry** — Unicorn throws the permission fault *before* any fetch-time hook can fire at that PC, so there is no way to log the second entry's caller directly.

**Fallback: searched all ~4KB of already-captured bytes from this session for any direct `bl`/`blx`/`b` instruction targeting `0x120381c0`.** Wrote `_scratch/find_caller.py`, checked all 12 dumps (window`[0x180:0x300]`, all 3 part-9 candidates, all 6 part-11 internal callees) — **found nothing**. The second call isn't in any code we've disassembled so far.

**Conclusion**: the cheap short-circuit idea doesn't have an implementation path with what we currently know — we can't intercept the failing fetch, and we don't have the caller's address to intercept instead. This doesn't contradict part 11's "host-side mprotect" theory; it's consistent with it. Confirms the next real step is host-level (Java/unidbg) instrumentation, not more guest-code hunting — there's no cheap guest-code trick left to try.

## Update (session 23 part 11) — 7-agent sweep of the whole remaining call graph, mprotect not found in guest code

Dumped and dispatched agents for: the 7-entry veneer-trampoline cluster, plus the 6 remaining unexplored internal callees (`0x12025230`/`0x12025484`, `0x120375fc`/`0x12037488`, `0x1203b684`/`0x1203b6f8`). This exhausts every call target flagged since part 9.

**Veneer trampolines decoded precisely** (ARM-mode ADD/ADD/LDR long-branch glue, one agent did the exact immediate-rotate arithmetic): all 7 resolve to pointer-table slots in a tight span, `0x1208244c`–`0x1208263c`, incrementing by exactly 4 bytes per "over-read" copy — confirming these dispatch through the **same ctor-skipped pointer table** (`0x12082340` region) already responsible for this session's other two fixed bugs. We haven't dumped the *contents* of those specific slots yet.

**All 6 internal callees disassembled, none contain the mprotect call:**
- `0x12025230`/`0x12025484` — crypto plumbing: a CRC/hash-style digest loop and CBC-style block XOR chaining with pad-stripping. No SVC, no page-size constant. Not it.
- `0x120375fc`/`0x12037488` — a **mutually-recursive pair** (each calls the other) that matches the old "NewObjectV triggers re-entry" comment almost exactly: sequence-lock/reentrancy guards, six JNI-vtable-shaped indirect calls per function (offsets 0x24/0x34/0x44/0x54/0x98/0x18c off an env-like pointer), a byte-XOR name-decode loop, calls into the crypto pair above to resolve/cache a class or method ID. No SVC, no page-size constant either — but structurally this is exactly the code path the self-nuke should live behind.
- `0x1203b684`/`0x1203b6f8` — turned out to be **one function** (the `0x1203b684` capture starts mid-literal-pool, real entry is `0x1203b6f8`). **Contains real SVCs** — `svc #0` with `r7=0x14` (`getpid`) and `r7=0x25` (`kill`) — but it's an **anti-tamper self-destruct switch** (`kill(getpid(), SIGABRT)` then `kill(getpid(), SIGKILL)`), gated behind a flag byte and two vtable calls. Not memory-protection code. No `r7=0x7d` (mprotect's syscall number) anywhere.

## Conclusion for this thread of investigation

**Zero guest-code SVCs with mprotect's syscall number (125/`0x7d`) exist anywhere in the ~10 functions traced this session.** Combined with session 21's own earlier note — *"SVC-based InterruptHook doesn't fire because unidbg's ARM32SyscallHandler wraps the mprotect from host code, not from a unicorn SVC"* — the likely explanation is that **the actual mprotect call is not guest ARM code at all**. It's plausibly unidbg's own Java-side memory manager doing it internally as a side effect of handling the JNI `NewObjectV` call (or similar), triggered from the *host* runtime rather than anything emulated. If that's right, no amount of further guest-code disassembly will find it — the search needs to move to a different layer entirely (hooking unidbg's own DVM/class-loading Java methods, or instrumenting `Memory.mprotect` calls at the host level, rather than tracing more ARM call graphs).

**Session checkpoint**: 20 remote runs, 11 parts, 7 parallel agents in the final sweep (10 agents total this session). Two real bugs found and fixed (`0x7b290` table-walk, `SINGLETON+0x1a4` double-indirection — SINGLETON2 dispatch count 0→3), a genuine anti-tamper kill-switch mechanism discovered as a bonus, and this specific EXEC-loss investigation now correctly reframed as "wrong layer" rather than "wrong address" — a materially different, harder problem than more disassembly can solve. This is the natural stopping point for this session.

## Update (session 23 part 10) — 3 parallel agents dumped and disassembled all 3 candidates

Dispatched 3 parallel agents, one per unexplored call target from part 9 (`0x1203b520`, `0x1203a760`, `0x1203a7d4`), each given raw hex bytes and told to disassemble with capstone and hunt for an SVC/mprotect-style pattern. Results:

- **`0x1203b520`: cleared.** A tiny (0x24-byte) helper — double-dereferences a global, reads a field, conditionally calls one external function, returns a bool. No SVC, no page-size constant, no JNI-vtable call. Not the culprit.
- **`0x1203a760` / `0x1203a7d4`: same territory, more context.** Both dumps turned out to cover the **same 3 concatenated functions** (two small ~116-byte "cached-lookup, invalidate on generation-counter mismatch" helpers, then one large function starting at `0x1203a838`/`0x1203a8ac`). No SVC and no `0x1000`/`0x12038000`-style literal found in either — the "mod-16"/"mod-1024" arithmetic that looked page-alignment-shaped is actually an ordinary compiler idiom for a switch/hash dispatch, not pointer alignment. **Real finding**: a genuine 26-byte XOR decrypt loop at `0x1203aa3a-0x1203aa52` (matches the "entry-decrypt XOR" work from earlier sessions), followed by calls into `0x12025230`/`0x12025484` with the decrypted buffer — this looks like it decrypts and resolves a class/method name, then does JNI-style dispatch through a global struct (offsets 0x10/0x1c/0x38/0x44/0x98/0xc4/0xd4 — shaped exactly like a JNIEnv vtable).

**Key connection**: the unresolved call targets from both large-function dumps — `0x1207b2d0`, `0x1207b2e0`, `0x1207b310`, `0x1207b640`, `0x1207ba70`, `0x1207ba80`, `0x1207ba90` — are all 16 bytes apart from their neighbors and sit in the exact same address neighborhood as `0x1207b400`/`0x1207b630`/`0x1207b7d0`, which **session 22 already characterized as the veneer table** ("dispatches via ARM-mode LDR PC entries... each entry a 16-byte position-independent trampoline"). These aren't raw syscalls — they're PIC jump-stub entries in that same table, each hopping to some real target elsewhere (possibly real libc functions, resolved through the same ctor-skipped pointer-table mechanism behind this whole session's other bugs).

**Where this leaves us**: neither the two small cache-helpers nor the ~350 bytes we could see of the large function contain a direct SVC or literal page-protect call. The self-nuke, if it's in this call graph at all, is at least one more veneer-trampoline hop away — behind `0x1207b2d0`/`0x1207b640`/etc., or inside `0x12025230`/`0x12025484`/`0x12037488`/`0x120375fc`/`0x1203b684`/`0x1203b6f8`, none of which are dumped yet. Each hop so far has cost one dump + one disasm pass; chasing the veneer trampolines to their real targets is a materially bigger, multi-hop search, not a quick next step.

**Session checkpoint**: 18 remote runs, 10 parts, 3 parallel agents, across this single long session. Two real bugs found and fixed, N.l demonstrably runs further than at session start (SINGLETON2 dispatch 0→3), and the remaining blocker is now precisely bounded (somewhere behind the veneer trampoline cluster) even though not resolved. Good natural stopping point — flagging for a fresh session rather than continuing to compound context.

## Update (session 23 part 9) — crash address identified as a function's OWN entry point

Extended the offline-disasm technique (`_scratch/disasm_381ea.py`, dumped `window[0x180:0x200]` alongside the existing `[0x200:0x300]`) and got proper alignment this time. **`0x120381c0` (reported as `0x120381c1` with the Thumb bit) is not mid-function — it's a function's own entry prologue**: `push {r4,r5,r6,r7,lr}` / `push.w {r8,sb,sl,fp}` / `sub.w sp,sp,#0x5000` (a 20KB stack frame — this function is called repeatedly, likely once per class/entry processed).

Traced the full body of this function: it's the SAME code we already disassembled in part 7/8 (`0x1203820e` onward is reached via its internal `cbz r0, 0x1203820e` branch when the first `bl 0x1203b520` call returns 0). **This function's first invocation succeeds completely** — it's the one that reaches our fixed `SINGLETON+0x1a4` double-indirection call (dispatch #3, `LR=0x12038273`) and continues fine from there. The crash is a **second, later call back into this same function's entry point** (`0x120381c0`), by which point its page has lost EXEC.

This directly matches an **existing, already-applied session-18/21 comment and patch** at `0x12038226`: *"The call goes through env->functions[25] (NewObjectV in JNI table) and triggers re-entry to 0x120381c0 via a path that self-nukes the page with mprotect."* That `blx r3` is confirmed NOP'd successfully every run (`BLX_R3 pre-patch @0x12038226: 9847 -> post: 00bf`), yet we're still hitting the exact symptom that patch describes. Either that NOP doesn't fully close the self-nuke path, or a **different** internal call is responsible — three call targets inside this function are still unexplored: `bl 0x1203b520` (called at entry, before the found/not-found branch), `bl 0x1203a760`, and `bl 0x1203a7d4` (called later, right before the `beq 0x120381ea` branch). Any of these could be the actual mprotect trigger, or this could be intentional decrypt-execute-reprotect packer behavior that genuinely requires re-triggering a decrypt step before every re-entry (a materially harder problem than a simple permission patch).

**This is now the natural next investigation** — but it requires disassembling three more unexplored subroutines, each potentially as deep as the ones already traced. Flagging as a good checkpoint given session length (~16 remote runs across 9 parts).

## Session 23 summary (2026-07-30) — real fixes landed, current state below

This was a long session (7+ parts, ~14 remote runs). Net result: **two real bugs found and fixed**, N.l now runs measurably further than at session start, and the current blocker is well-characterized even though not yet resolved. Quick timeline:

1. **Falsified**: blanket SINGLETON stub blocking real subroutines (it wasn't — 0 dispatches, execution never got that far yet).
2. **Falsified**: `b2b` as an independent decrypt path (it's gated by the same init state as `l()`).
3. **Falsified**: FETCH LIMIT cap being "too low" (bisected 50→300→1500, always dies at cap+1 — the thing consuming it was genuinely unbounded, not close to finishing).
4. **Found root cause #1 (FIXED)**: `0x7b290` — a ctor-skipped pointer table's stale on-disk default, read and branched to, landing PC in auto-mapped zero memory where it free-ran forever. Fixed by bouncing PC back to LR on every hit (not just once) — turned out to be a real, bounded ~10-entry table walk once given the chance to advance entry-to-entry.
5. **Result of fix #1**: SINGLETON2 dispatch count went from 0 (all session) to 2 — first real evidence N.l reaches and uses the fake vtable.
6. **Found root cause #2 (FIXED)**: `0xe1...` — same bug class as #1, traced via offline capstone disassembly (`_scratch/disasm_38273.py`) to `SINGLETON+0x1a4` holding our blanket-fill default (`0x7f003000`) but being **double-dereferenced** by the caller (reads SINGLETON2's raw `BX LR` opcode bytes as a data pointer instead of executing them). Fixed with a pointer-cell at `SINGLETON2+0x100` holding the value `0x7f003000`, so the double dereference resolves correctly.
7. **Result of fix #2**: free-run gone entirely, SINGLETON2 dispatch count climbed to 3 — new all-time high. Execution now fails on a **different, cleaner** error.

## Current blocker

```
WARN ... emulate RX@0x120381c1[libexec.so]0x381c1 exception ... UC_ERR_FETCH_PROT (Fetch from non-executable memory) ...
>>> N.l threw: java.lang.IllegalStateException: Invalid boolean value=-1
>>> SINGLETON2 dispatch count after N.l: 3
```

This is **session 21's original finding**, now showing through cleanly: page `0x12038000` (containing `0x120381c1`) gets its EXEC permission stripped by something session 21 called "the secondary loader," mid-execution — despite us `mem_protect`-ing it to RWX once, right before calling N.l.

**Deterministic across reruns** — same address, same error type, same args, every time (confirmed 3x in a row with identical `arguments=[...1709804316]`).

## Fix attempts tried this session, both inconclusive/negative

1. **Raw `UC_HOOK_MEM_FETCH_PROT` hook** (session 21's pre-built "Plan F", reflection-based, was disabled for corruption risk). Re-enabled just this one hook (split the old blanket `ENABLE_RAW_HOOKS` flag into three independent flags — `ENABLE_FETCH_PROT_HOOK`/`ENABLE_WRITE_PROT_HOOK`/leave UNMAPPED off since our safe `EventMemHook` already covers that). Result: fired once for an unrelated, spurious address (`0x0`, garbage PC) — never fired for the real `0x120381c1` fault — and the real crash's error type changed run-to-run (`FETCH_PROT` → `UC_ERR_MAP`) despite identical inputs, confirming the original "reflection corrupts internal state" warning. **Reverted** (`ENABLE_FETCH_PROT_HOOK` back to `false`).
2. **Piggyback `mem_protect` on the SINGLETON2 dispatch hook** (safe, no reflection, fires 3x reliably). No effect — crash identical to the unpatched baseline. This tells us something useful: **whatever strips EXEC happens strictly after the 3rd SINGLETON2 dispatch and before reaching `0x120381c1`** — our nudge fires during/before that window, not inside it, so it doesn't help. A periodic nudge from an earlier checkpoint isn't enough; the fix needs to land inside that specific narrow window.

## Key files modified this session

| File | Changes |
|------|---------|
| `_scratch/Unpack.java` | b2b unconditional call; SINGLETON2 dispatch-count hook; FETCH LIMIT bisection instrumentation; `0x7b290` bounce-every-hit fix; same-LR-streak runaway detector; `SINGLETON+0x1a4` double-indirection fix; `ENABLE_RAW_HOOKS` split into 3 flags; SINGLETON2-dispatch re-protect nudge (didn't help, left in — harmless) |
| `_scratch/disasm_38273.py` | **new** — offline capstone disassembler, reusable for any future "what's actually at this runtime address" question without a remote round-trip |

## To reproduce

```bash
cd C:/Users/Nestor/Workspace/Xuper/XuperPlugin
python _scratch/run_lever_remote2.py
```

## Next steps (updated, part 14 — the JNI-dispatch branch of the tree is fully closed now)

**Ruled out with hard evidence, don't re-test these:**
- Blanket SINGLETON stub blocking dispatch (falsified — dispatch count now 3)
- FETCH LIMIT being too low (bisected 50→300→1500, always dies at cap+1 back when this was still the active bug — since fixed)
- On-demand mapping churn corrupting `0x12038000` (canary reads + forced re-protect never failed)
- A guest-code `mprotect`-numbered SVC anywhere in the ~10 traced functions (exhaustive disasm sweep, zero hits)
- ~~Any real JNI env function call~~ **[RETRACTED — see correction at top]** The "zero JNI hits" result was an artifact: hooks were installed after the init-phase JNI activity (FindClass fires at output.log:409, hooks installed at :682), and no positive control confirmed the hooks fire at all. The JNI-triggered-mprotect branch is NOT ruled out. (What IS still supported: the SINGLETON-based indirect calls at `0x7f002000` are our own fake struct — but that does not mean real JNIEnv is never used; FindClass through the real env at `0xfffe00b0` demonstrably happens during init.)

**Promising directions for a fresh session:**
1. **`AndroidElfLoader.java`'s `mem_protect` call, tied to ELF `PT_LOAD` segment processing** — found during the source search but not yet investigated. If anything triggers a second library-load/relocation pass (even implicitly, inside unidbg's own bookkeeping) that overlaps our page in address space, this is the mechanism that would silently reprotect it. Worth checking whether `dlopen`/`System.loadLibrary`-equivalent ever fires a second time this session.
2. **Look for unidbg's own internal mprotect/mem_protect calls not tied to JNI at all** — since JNI dispatch is now ruled out, broaden the source search to `Memory`/`Backend`/`AbstractLoader` reachable from other native→Java bridges (real Linux syscalls like `mmap`/`brk`/`mprotect` handled by `ARM32SyscallHandler`, or internal relocation/symbol-resolution code) rather than JNI-specific ones.
3. **Alternative pivot: accept it and re-trigger instead of prevent.** If this really is decrypt-execute-reprotect packer behavior (still not ruled out), stop trying to stop the reprotect and instead figure out what re-populates/re-decrypts this page on a fresh call, then trigger that ourselves before the second entry to `0x120381c0`.
4. `SINGLETON2 dispatch count` remains the best available progress metric (0→2→3 across this session's two real fixes) — any future fix attempt should be checked against whether it moves that number further.
5. Bonus finding worth remembering for later: `0x1203b6f8` is a genuine anti-tamper kill-switch (`kill(getpid(), SIGABRT)` → `kill(getpid(), SIGKILL)`), gated behind a flag byte at `ctx+0x188` and two vtable calls. Not relevant to this bug, but worth knowing it exists if future runs start dying with SIGABRT/SIGKILL instead of the usual exceptions.
6. `gh search code`/`gh api` against `zhkl0228/unidbg` worked cleanly with no auth needed this session — a fast, reliable way to get ground truth about unidbg's behavior instead of guessing from symptoms. Reuse it early next time. Same for `vm.getJNIEnv()` → function-table-offset → real SVC address → `CodeHook`, now a proven, reusable technique for any future "is X actually being called" question.
