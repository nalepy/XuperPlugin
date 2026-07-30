# Next Blocker — 0x120381c0 is a function that gets re-entered after its own page loses EXEC

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

## Next steps

1. **Narrow the window precisely.** We know EXEC is lost strictly between SINGLETON2 dispatch #3 and `0x120381c1`. Add a CodeHook at `0x120381c1` itself (or a few bytes before it) that, on the *first* hit, force-reasserts RWX on the page before the fetch actually happens — a hook fires on successful fetch, but we need to catch it *before* the permission check, which a plain `CodeHook` at that exact address can't do (the fault happens before the hook could fire). Instead: hook the SINGLETON2 dispatch #3 call site specifically (not all dispatches) and single-step or trace forward a short, bounded window from there to find the actual mprotect-equivalent call.
2. **Reuse `_scratch/disasm_38273.py`** (extend it to cover a wider byte range, e.g. `0x12038100-0x12038300`) against a dump taken right after dispatch #3, to find what code runs between the 3rd dispatch and the fault — this is now a known, bounded search window instead of an open-ended one.
3. Per session 21's own notes (already in this file's history): the mprotect causing this "wraps... from host code, not from a unicorn SVC" — meaning it's unidbg's own Java-side memory manager doing it as a side effect of something (likely still the `NewObjectV`/JNI dispatch path), not a guest CPU instruction we can NOP. The `blx r3` at `0x12038226` is already NOP'd (an existing, older fix) — check whether that patch is still being applied correctly and is actually upstream of this specific fault, or whether there's a second, unpatched call doing the same thing.
4. `SINGLETON2 dispatch count` is the best available progress metric right now (0→2→3 across this session's two real fixes) — any future fix attempt should be checked against whether it moves that number.
