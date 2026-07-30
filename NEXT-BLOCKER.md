# Next Blocker — 0xe1... free-run FIXED; back to session 21's original 0x120381c1 EXEC-protection loss

## Status (2026-07-30 session 23, part 7) — real fix landed

Patched the actual bug behind the `0xe1...` free-run (traced via offline capstone disassembly, no remote round-trip needed to find it). **It's fixed**: the free-run no longer happens, and `SINGLETON2 dispatch count` advanced again (2→3, a new all-time high). Execution now fails later, on a cleaner, more specific error — session 21's original `0x120381c1` EXEC-permission problem, now unmasked instead of being buried under the free-run crash.

## The bug and the fix

Disassembled the live runtime bytes at `0x12038200-0x12038300` (pulled via one dump, `_scratch/disasm_38273.py`, decoded offline — no extra remote run needed). Found the exact instruction chain causing the `0xe1...` free-run:

```
fp = *(0x12082340)          ; -> SINGLETON (0x7f002000), via the pointer-table write we already do
r1 = *fp                    ; -> *(SINGLETON+0) -> our own override -> 0x7f002100
0x1203826a: ldr.w r0, [r1, #0xa4]   ; r0 = *(0x7f0021a4)  = SINGLETON + 0x1a4
0x1203826e: ldr   r0, [r0]          ; r0 = *r0            (double indirection!)
0x12038270: blx   r0                ; call it
```

`SINGLETON + 0x1a4` held our blanket-fill default (`0x7f003000`, the SINGLETON2 BX-LR-stub address). Single-indirection callers elsewhere correctly `blx` that value directly and hit real, executable stub code. This call site instead **dereferences it one more time** (`r0 = *r0`) — reading SINGLETON2's raw opcode bytes (`0xE12FFF1E`, the literal `BX LR` encoding) as if they were a pointer *value*, then branching to that garbage, landing in unmapped territory and free-running through auto-mapped zero pages exactly as session 23 parts 4-6 characterized.

Fix: added a small pointer-cell at `SINGLETON2+0x100` (`0x7f003100`) whose *content* is the value `0x7f003000`, and pointed `SINGLETON+0x1a4` at that cell instead of at SINGLETON2 directly. Now the double dereference resolves correctly: `*(SINGLETON+0x1a4)` → `0x7f003100` → `*(0x7f003100)` → `0x7f003000` → `blx 0x7f003000` → real, safe BX-LR stub. (First attempt patched `SINGLETON+0xa4` directly and missed the extra `*fp` hop through the `+0x000` override — corrected to `+0x1a4` once the full chain was traced.)

## Result

```
>>> SINGLETON+0x1a4 double-indirection fix: points to 0x7f003100 which holds 0x7f003000
>>> SINGLETON2 dispatch count before N.l: 0
WARN ... emulate RX@0x120381c1[libexec.so]0x381c1 exception ... UC_ERR_FETCH_PROT ...
>>> N.l threw: java.lang.IllegalStateException: Invalid boolean value=-1
>>> SINGLETON2 dispatch count after N.l: 3      <- new high (was 2)
```

No `[runaway-detect]` line at all — the free-run is gone. The error type also changed meaningfully: **`UC_ERR_FETCH_PROT`** ("Fetch from non-executable memory"), not `UC_ERR_FETCH_UNMAPPED`/`UC_ERR_MAP`. This means `0x120381c1`'s page (`0x12038000`) **is mapped now**, just not executable at that moment — this is exactly session 21's original finding ("the page at 0x120381c0 was made non-exec by secondary loader"), now showing through cleanly instead of being masked by the free-run crash that was happening first.

## Key files modified this session

| File | Changes |
|------|---------|
| `_scratch/Unpack.java` | (cumulative, parts 1-6) + **new**: `SINGLETON+0x1a4` double-indirection fix (pointer-cell at `SINGLETON2+0x100`) |
| `_scratch/disasm_38273.py` | **new** — offline capstone disassembler for the `0x12038200-0x300` byte window, used to find the exact double-indirection instruction chain without a remote round-trip |

## To reproduce

```bash
cd C:/Users/Nestor/Workspace/Xuper/XuperPlugin
python _scratch/run_lever_remote2.py
```

## Next steps

1. **Fix the EXEC-permission loss at 0x120381c1 (page 0x12038000) for real, not just once before N.l.** We already `mem_protect` that page to RWX right before calling N.l (`>>> PAGE@0x12038000 re-protected EXEC before N.l`), but something re-removes EXEC on it during N.l's own execution. Same class of problem the earlier `0x12038000` canary checks explored (session 23 part 3) — but those checks were *data* reads (which don't reveal EXEC-bit loss). Add a periodic **re-`mem_protect`** (not just re-`mem_map`) of that page during N.l, or — better — find what's un-protecting it (likely an `mprotect`-equivalent SVC call session 21 called "secondary loader") and hook that call directly.
2. **Disassemble around 0x120381c1 itself** the same way part 7 did for `0x12038273` — pull the byte window offline, decode with capstone, understand what's supposed to be there and why the protection keeps getting stripped.
3. Now that `SINGLETON2 dispatch count` is climbing (0→2→3 across this session's fixes), each new dispatch is a signal real code is reaching further into the fake vtable — keep tracking it as a progress metric for future fixes.
4. The `same-LR-streak` runaway detector (session 23 part 6) stays installed as a safety net/diagnostic — didn't need to fire this run, but costs nothing to leave in for future free-run-class bugs.
