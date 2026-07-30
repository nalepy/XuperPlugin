# Next Blocker — a second, unhooked veneer-table walk consumes any FETCH budget we give it

## Status (2026-07-30 session 23, part 3)

Three clean bisection experiments this session, all falsifying prior theories and narrowing hard on one answer: N.l's `-1` is caused by `UC_ERR_FETCH_UNMAPPED` crashing mid-execution, at whatever moment our own on-demand FETCH-mapping safety cap gets hit — and **that cap always gets hit, no matter how high we set it**, because the thing consuming it is an unbounded walk we haven't actually killed (only ONE of its entry points is hooked).

## Falsified this session

1. ~~Blanket SINGLETON stub kills real subroutines~~ — disproven. Single-address hook on the SINGLETON2 stub (0x7f003000), active the whole run: **0 hits**, always. N.l/b2b never dispatch through it.
2. ~~b2b is a standalone decrypt path~~ — disproven. Called unconditionally regardless of `l()`'s result: returns `null` after only 2 instructions, gated by the same uninitialized state as `l()`.
3. ~~FETCH LIMIT=50 was cutting the walk off early, just raise it~~ — disproven three times over:
   - cap=50 → dies at fetch **51**
   - cap=300 → dies at fetch **301**
   - cap=1500 → dies at fetch **1501**
   Every single time, the crash lands at the exact same relative point: `cap+1`. This isn't "the walk needs about N more pages" — it's an **effectively unbounded** loop that will consume literally any budget we hand it. There is no finite cap below the ~2000-mapping Unicorn-corruption ceiling (session 22) that lets it finish on its own.
4. ~~On-demand mapping churn corrupts the unrelated page at 0x12038000~~ — disproven. A canary `mem_read` of 0x12038000 every 10 fetches (up to 1500) never once failed. Continuously forcing `mem_map`+`mem_protect` RWX on that page every 10 fetches didn't change the outcome either — same crash, same address, same relative timing.

## Current understanding

The crash is always `UC_ERR_FETCH_UNMAPPED` at `0x120381c1` (inside `libexec.so`, page `0x12038000` — same page session 21 already found the "secondary loader" was making non-exec). The page itself checks out fine by every probe we've thrown at it. The actual mechanism: some loop is requesting fresh unmapped-fetch pages continuously; our `EventMemHook` auto-maps each one on demand up to a cap, then refuses further mapping past the cap — and whatever page it's asking for at exactly `cap+1` happens to be the one needed to keep real control flow going, so denying it kills execution.

Session 22 already reverse-engineered **one** instance of this mechanism: the veneer table at `0x1207b400` dispatches via chained ARM `LDR PC` trampolines to every 4KB page from `0x7b290` upward, and the *first* entry point into it (a `BL` at `0x1203767c`) is already hooked and killed (`>>> [scan-kill@0x1203767c] nuking scan call #1` fires exactly once, successfully, every run). But something is still generating an apparently-endless stream of fresh unmapped-fetch requests afterward — almost certainly a **second, unhooked entry point into that same veneer table**, or a second independent walk of the same shape, that our single kill-hook doesn't cover because it's keyed to one exact address rather than the whole mechanism.

## Key files modified this session

| File | Changes |
|------|---------|
| `_scratch/Unpack.java` | b2b unconditional call; post-N.l SINGLETON dump; SINGLETON2 whole-run dispatch-count hook; FETCH LIMIT bisected 50→300→1500 (left at 1500); periodic (every 10 fetches) forced re-map+re-protect of 0x12038000 (proven inconsequential, left in as a harmless safety net) |

## To reproduce

```bash
cd C:/Users/Nestor/Workspace/Xuper/XuperPlugin
python _scratch/run_lever_remote2.py
```

## Next steps

1. **Kill the whole veneer table, not one entry point.** Instead of a single-address hook at `0x1203767c`, install a *range* hook covering the veneer table itself (`0x1207b400` onward — session 22 already mapped its shape: 16-byte trampolines, one per target page). Any PC landing anywhere in that range should immediately force `R0=0` and redirect out, the same way the existing kill-hook does — this should catch the second/unhooked entry point regardless of how it got there.
2. **Find the second entry point concretely** — if (1) doesn't fully resolve it, dump the CALL SITE for the fetches that are consuming the budget (LR register at the moment `EventMemHook` maps each page) to find what's driving this second walk, the same way `[SINGLETON2 dispatch #n] called from LR=...` was used earlier for the vtable question.
3. Once N.l can complete without crashing, **re-check the SINGLETON dispatch count** (already instrumented, 0 hits so far) — if it's still 0 after this walk is properly killed, that's the next real thing to chase (dispatch table genuinely never used, vs. never reached because of this crash).
4. `b2b` is not worth re-testing independently until `l()` gets past this crash — it's confirmed to depend on the same init state.
