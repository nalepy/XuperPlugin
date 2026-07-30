# Next Blocker — 0xe18da000 characterized (self-inflicted); generalized fix attempt didn't advance further

## Status (2026-07-30 session 23, part 6)

Characterized `0xe18da000` as requested: it's the **same bug class** as `0x7b290` — PC free-running through auto-mapped zero pages after branching to a garbage pointer — not an independent new mechanism. Confirmed deterministic (identical address across reruns). Traced its likely source to **our own injected SINGLETON2 stub bytes being misread as data**. Built a generalized detector (same-LR-streak) instead of hardcoding this one address; it fires correctly but produces a *different*, not-better crash (`UC_ERR_MAP` instead of `UC_ERR_FETCH_UNMAPPED`), with no further SINGLETON2 dispatch progress (still stuck at 2). Net: characterization succeeded, the fix attempt didn't yet convert into forward progress.

## Characterization of 0xe18da000

Confirmed deterministic — identical value across reruns before any fix attempt. The last fetch before hitting the cap:

```
>>> [EventMemHook] FETCH LIMIT REACHED (1501) ... requestedAddr=0xe18da000 seenRange=[0x7b290,0xe18da000]
    LR=0x12038273 r0=0xe12fff1e r1=0x7f002100
```

- `LR=0x12038273` — inside the *same page* (`0x12038000`) as every earlier crash address this session (`0x120381c1`, etc.) — this page is clearly a control-flow hub for whatever's going wrong.
- `r1=0x7f002100` — this is **our own** SINGLETON self-pointer, the exact value session 21 wrote into the fake struct's offset 0 (`safePage[0..3] = 0x7f002100`). Code is reading our synthetic struct as data here.
- `r0=0xe12fff1e` — this is **literally the 4 bytes of our SINGLETON2 `BX LR` stub** (`1E FF 2F E1` little-endian). The walk's address range (`0xe13fd000`-`0xe18d9000`) shares the same leading byte (`0xe1`) as this value — strong circumstantial evidence the runaway's target address is *derived directly from our own stub bytes being read as data instead of executed as code*.

Full trajectory: LR stayed frozen at `0x12038273` for **every one of ~1245 consecutive fetches** (fetch #256 through #1500), climbing `+0x1000` each time, from `0xe13fd000` to `0xe18d9000` — a textbook straight-line free-run through zero-filled pages (zero bytes decode as ARM `ANDEQ r0,r0,r0`, a no-op, so PC just keeps incrementing through page after page instead of branching).

**Conclusion: this isn't an independent new obstacle. It's the same "branch to garbage → free-run through auto-mapped zero" bug as 0x7b290, just triggered by a different bad value** — one that traces back to our own synthetic SINGLETON2 stub being dual-used as both code (safe when executed) and data (garbage when read as a value).

## Generalization attempt (this session) — partial

Built a same-LR-streak detector in the `EventMemHook` (8+ consecutive unmapped-fetch events with identical LR ⇒ bounce PC back to LR instead of continuing to map), reasoning that real code changes LR on every call/return while a free-run never does. It correctly fired:

```
>>> [runaway-detect] 8 consecutive fetches with frozen LR=0x12038273 at addr=0xe1306000 — bouncing PC back to LR instead of mapping further
WARN ... emulate RX@0x120381c1[libexec.so]0x381c1 exception ... UC_ERR_MAP ...
>>> N.l threw: java.lang.IllegalStateException: Invalid boolean value=-1
>>> SINGLETON2 dispatch count after N.l: 2   <- unchanged, no further progress
```

Bouncing raw `PC=LR` mid-free-run (unlike the 0x7b290 case, where the caller was in a clean retry loop) landed us back in `0x12038273` without whatever the *proper* return sequence would have done (stack/register state the real return path would have set up), producing a **new error type** (`UC_ERR_MAP`, "Invalid memory mapping" — different from the `UC_ERR_FETCH_UNMAPPED` we'd been seeing) at the same `0x120381c1` hub address. Not worse in outcome (still returns -1), but not better either — SINGLETON2 dispatch count didn't advance past 2, so this bounce didn't unlock further real progress the way the 0x7b290 fix did.

## Status (2026-07-30 session 23, part 5) — real forward progress

The bounce-back fix for the `0x7b290` runaway **works**. Execution now gets meaningfully further than any prior run this session: it walks a real 10-entry class-descriptor table, dispatches through the SINGLETON2 stub twice (first time ever, all session), and only then crashes at a new, different address.

## What changed and why it works

Session 23 part 4 found `0x7b290` is the stale on-disk default from the ctor-skipped pointer table, and that a single one-shot bounce (redirect PC back to LR) just deferred the problem by one iteration — the caller immediately re-entered the same broken call and free-ran through zero memory anyway. Fix: **bounce every single time** (bounded to 5000, as a safety valve against a genuine infinite loop), not just once.

Turns out it's not an infinite loop or a random free-run — it's a **real, bounded table walk**. Register `r1` at each hit steps through distinct addresses ~0x20-0x30 bytes apart, all inside the module's own data section:

```
>>> [runaway-origin@0x7b290] hit #1  LR=0x12037689 r1=0x1208d670 ...
>>> [runaway-origin@0x7b290] hit #2  LR=0x12037689 r1=0x1208d350 ...
>>> [runaway-origin@0x7b290] hit #3  LR=0x12037689 r1=0x1208d37a ...
>>> [runaway-origin@0x7b290] hit #4  LR=0x12037689 r1=0x1208d390 ...
...
>>> [runaway-origin@0x7b290] hit #10 LR=0x12037689 r1=0x1208d460 ...
```

This is a genuine table of ~10 (or more — logging capped at 10) class-descriptor-like entries whose dispatch/callback field is *uniformly* the same stale default (`0x7b290`, unpopulated by the skipped ctors, same root cause as session 21/22's `0x12082340`/`0x1207b400` findings, just a different table instance). Bouncing back after each failed entry lets the walk's own loop logic advance to the next entry naturally, instead of getting stuck free-running through zero memory on the very first one.

## Result of getting past it

```
>>> SINGLETON2 dispatch count before N.l: 0
>>> SINGLETON2 dispatch count after N.l: 2      <- first-ever nonzero this session
>>> SINGLETON2 dispatch count before b2b: 2
>>> SINGLETON2 dispatch count after b2b: 2
>>> [EventMemHook] FETCH LIMIT REACHED (1501), NOT mapping requestedAddr=0xe18da000 seenRange=[0x7b290,0xe18da000]
>>> N.l threw: java.lang.IllegalStateException: Invalid boolean value=-1
```

This directly overturns session 23 part 1's finding ("SINGLETON2 dispatch count always 0") — that was true only because execution never got far enough to reach it. Getting past the table-walk obstacle lets N.l reach and use the fake dispatch table for real, twice.

It still eventually dies — but now at a wildly different, much larger address (`0xe18da000`, ~3.6GB) instead of the deterministic climb from `0x7b290`. That's a new, distinct frontier, not the same bug resurfacing.

## Key files modified this session

| File | Changes |
|------|---------|
| `_scratch/Unpack.java` | (cumulative, see parts 1-4) + **new**: `0x7b290` CodeHook now bounces PC→LR on *every* hit (bounded at 5000, was one-shot), with hit-register logging (first 10, then every 1000th) |

## To reproduce

```bash
cd C:/Users/Nestor/Workspace/Xuper/XuperPlugin
python _scratch/run_lever_remote2.py
```

## Next steps

1. **Find the actual instruction at/near 0x12038273 that reads r0/r1 as data.** This is now the real target — not another address to bounce past, but the read site that's misinterpreting our SINGLETON2 stub bytes as a data value. If we can identify exactly what it's trying to compute (a hash? an offset lookup? a checksum?), we can either feed it a value that resolves harmlessly, or figure out what real data it *should* be reading and populate that instead of our generic stub.
2. **Try disassembling around 0x12038273** using the existing `_scratch/disasm_*.py` capstone scripts (already used for similar addresses this project) — dump the actual instruction bytes there and a window around it, offline, without needing another remote round-trip.
3. **Reconsider what SINGLETON2 should contain.** It currently holds the `BX LR` ARM opcode (`0xE12FFF1E`) so that *executing* it is a safe no-op. But something is *reading* those same bytes as a data value and deriving a bad pointer from them. Since a single 4-byte value can't simultaneously be "safe when executed" and "safe when read as data" without knowing what the reader expects, the fix likely needs to happen at the read site (redirect what it fetches), not at the stub itself.
4. **The same-LR-streak generalized detector is directionally right but not sufficient on its own** — it correctly identifies free-runs address-agnostically, but a raw `PC=LR` bounce isn't a clean substitute for a proper function return (missing stack/register cleanup the real return path would do), hence the `UC_ERR_MAP` follow-on error. Keep the detector for diagnostics (it's a good tripwire/logging tool) but don't rely on it alone to fix this specific case — pair it with a proper fix at the read site once found (per step 1).
5. Keep the session-23-part-5 pattern (bounce every hit at a *known, specific* address, matched to a real retry loop) as the proven template for the `0x7b290` class of bug — that one **is** fully resolved. This new one needs the read-site fix, not another bounce point.
