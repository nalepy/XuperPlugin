# Next Blocker — SINGLETON dispatch table is fully faked, b2b is not independent

## Status (2026-07-30 session 23)

Confirmed root cause of the `-1` return: it's not a missing-asset or wrong-method problem. The SINGLETON dispatch table (0x7f002000) that session 21 blanket-filled with harmless `BX LR` stubs (to survive anti-tamper crashes) is **read but never populated** by N.l — every real subroutine that should run through it is now a no-op. `b2b` is gated by the same uninitialized state, not independently callable.

## Wins (session 23)

1. **b2b called unconditionally** (removed the `if (nOk)` gate in Unpack.java) — proved b2b is not a standalone decrypt path
2. **SINGLETON bytes dumped pre/post N.l** — byte-for-byte identical (`0x01`, `0x0030007f`, `0x0021007f`) before and after N.l runs. N.l's traced execution path never writes through the dispatch table at all.
3. **b2b execution traced**: only 2 instructions (`0x12039458`, `0x1203945a`) execute before it returns `null` — an early guard check bails immediately, same as `l()`'s -1.

## Current state

```
>>> N.l threw: java.lang.IllegalStateException: Invalid boolean value=-1
>>> post-N.l SINGLETON byte[0x7f0022e2]=0x01        (unchanged from pre-N.l)
>>> post-N.l SINGLETON byte[0x7f002138]=0x0030007f  (unchanged from pre-N.l)
>>> post-N.l SINGLETON byte[0x7f002000]=0x0021007f  (unchanged from pre-N.l)
>>> calling b2b regardless of N.l result (nOk=false) ...
>>> [walk2] 0x12039458
>>> [walk2] 0x1203945a
>>> b2b returned: null
```

## Root cause (confirmed)

Session 21's fix for anti-tamper crashes was to fill the *entire* SINGLETON page with a fake `BX LR` stub at every 4-byte slot, so any dispatch-table call is a harmless no-op. This solved the crash but also silently deletes every real subroutine the table used to point to — including whatever does actual key derivation / decryption setup. `l()` correctly detects "not initialized" and returns -1; `b2b` checks the same state and bails with `null`. Both are behaving *correctly* given a dispatch table full of no-ops — this isn't a bug in the harness's JNI plumbing, it's the direct cost of the blanket-stub survival hack.

The old theories (missing JNI asset I/O, wrong entry method, wrong ijiami.dat format) are now deprioritized — no evidence supports them, and the blanket-stub explanation fully accounts for the observed behavior.

## Key files modified this session

| File | Changes |
|------|---------|
| `_scratch/Unpack.java` | b2b call moved outside `if (nOk)`, wrapped in its own try/catch; added post-N.l SINGLETON byte dump (0x7f0022e2, 0x7f002138, 0x7f002000) for pre/post comparison |

## To reproduce

```bash
cd C:/Users/Nestor/Workspace/Xuper/XuperPlugin
python _scratch/run_lever_remote2.py
```

## Next steps

1. **Selective un-stubbing** — instead of blanket-filling SINGLETON with `BX LR`, identify which specific offsets are read via BLX/BX during the `[walk2]` trace (0x12037c18–0x12038158 range recorded in output.log) and disassemble each call site to find which original function pointer belongs there.
2. **Recover pre-overwrite table values** — dump the on-disk/original bytes at 0x12082340 and 0x120868e0 (before session 21's force-write) to see what real function addresses the loader would have installed, then decide per-slot: real pointer vs safe stub.
3. **Narrow the stub scope** — only replace the specific offsets known to trigger the anti-tamper/scan crash (traced in sessions 18-21) with the `BX LR` stub; leave all other slots pointing at their real targets so genuine init/decrypt code executes.
4. **Re-run with EventMemHook WRITE tracking** on the SINGLETON page to catch the first attempted real write, if any slot does get restored to a real pointer — that'll show exactly which offset that is.
