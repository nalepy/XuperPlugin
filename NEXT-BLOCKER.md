# Next Blocker — past the 0x7b290 table-walk obstacle; SINGLETON2 finally dispatches; new crash further downstream

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

1. **Characterize the new crash at 0xe18da000.** Is this address a real code target (dereferenced from somewhere sane) or another stale/garbage pointer artifact? Given its size (~3.6GB, well outside any 32-bit ARM Android process's real address space norms), it smells like another uninitialized-read situation rather than legitimate code — worth adding a similar one-shot LR/register dump hook once its exact value stabilizes across runs (check if it's deterministic first — rerun once without changing anything to confirm reproducibility before building a targeted hook).
2. **Log all 10 table-walk entries, not just the first ten hits' registers** — bump the log cap or check if the table only has ~10 entries (walk may have exited the table cleanly and moved on to unrelated code, in which case the crash at 0xe18da000 is genuinely a separate, later mechanism).
3. **Investigate the two SINGLETON2 dispatches** — now that they're finally happening, capture the LR (caller) for each one (the SINGLETON2 hook already logs LR per-hit, capped at 30 — check those two entries specifically) to learn what real code is trying to call through the fake vtable, now that we know it's actually reached.
4. Keep the bounce-forever pattern (proven to work) as the template for any future stale-pointer-table obstacle — it's a generalizable technique now, not a one-off hack.
