# Next Blocker — NewObjectV theory definitively ruled out via ground-truth Java hook; true trigger still unknown

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

## Next steps (updated, part 13 — five theories eliminated, need a fresh one)

**Ruled out with hard evidence, don't re-test these:**
- Blanket SINGLETON stub blocking dispatch (falsified — dispatch count now 3)
- FETCH LIMIT being too low (bisected 50→300→1500, always dies at cap+1 back when this was still the active bug — since fixed)
- On-demand mapping churn corrupting `0x12038000` (canary reads + forced re-protect never failed)
- A guest-code `mprotect`-numbered SVC anywhere in the ~10 traced functions (exhaustive disasm sweep, zero hits)
- `NewObjectV` being called at all before the crash (hooked the real SVC dispatch point via `vm.getJNIEnv()`, confirmed via unidbg's actual GitHub source — zero hits, it's just not called, likely because the existing `0x12038226` NOP already fully suppresses it)

**Promising directions for a fresh session:**
1. **Use the same `vm.getJNIEnv()` technique on OTHER JNI function table entries.** We now have a proven, cheap, ground-truth method (unidbg's `VM.getJNIEnv()` → function table → real SVC address → `CodeHook`) — apply it to other candidates besides NewObjectV: `FindClass`, `GetMethodID`, `CallObjectMethodV`, or whichever env function the *actual* re-entry into `0x120381c0` goes through, once identified.
2. **Look for unidbg's own internal mprotect/mem_protect calls directly in source**, rather than guessing which JNI function triggers it. Search `zhkl0228/unidbg` (via `gh search code "mem_protect"` or `"mprotect"` in the relevant repo) for every call site in `Memory`/`Backend`/DVM class-loading code, and check which are reachable from a native→Java bridge (SVC handler) rather than pure Java-side setup — that narrows the host-side search a lot faster than reading files top to bottom.
3. **Trace what SVC (if any) fires right before the crash.** We have `_NewObjectV`'s resolved SVC address as a working example — do the same for a handful of other high-traffic env functions and log every hit right up to the crash, to see which one (if any) fires last before `0x120381c0`'s second entry.
4. **Alternative pivot: accept it and re-trigger instead of prevent.** If this really is decrypt-execute-reprotect packer behavior (still not ruled out), stop trying to stop the reprotect and instead figure out what re-populates/re-decrypts this page on a fresh call, then trigger that ourselves before the second entry to `0x120381c0`.
5. `SINGLETON2 dispatch count` remains the best available progress metric (0→2→3 across this session's two real fixes) — any future fix attempt should be checked against whether it moves that number further.
6. Bonus finding worth remembering for later: `0x1203b6f8` is a genuine anti-tamper kill-switch (`kill(getpid(), SIGABRT)` → `kill(getpid(), SIGKILL)`), gated behind a flag byte at `ctx+0x188` and two vtable calls. Not relevant to this bug, but worth knowing it exists if future runs start dying with SIGABRT/SIGKILL instead of the usual exceptions.
7. `gh search code`/`gh api` against `zhkl0228/unidbg` worked cleanly with no auth needed this session — a fast, reliable way to get ground truth about unidbg's behavior instead of guessing from symptoms. Reuse it early next time instead of re-deriving via trial and error.
