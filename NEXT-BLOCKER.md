# Next Blocker — N.l returns -1 after clean execution

## Status (2026-07-30 session 22)

**N.l executes cleanly with no crashes.** All blockers from session 21 solved. The new blocker: N.l returns -1 (decryption failure) even though the anti-tamper, scan, and page protections are all resolved.

## Wins (session 22)

1. **Anti-tamper BL at 0x12038240 bypassed** — `b.reg_write(PC, 0x12038245)` skips the corrupting BL
2. **Memory scan killed at 0x1203767c** — hook forces R0=0 + PC=0x12037681, bypassing the veneer-table page walk that took 801 FETCH events
3. **page_collection_lock_arm crash root-caused**: 1MB pre-map or >~2000 4KB on-demand mappings corrupt Unicorn internals. 4KB mapping at moderate counts (50-200) is safe.
4. **Backend proxy, raw Unicorn hooks, walk traces** all disabled during N.l — minimal interference
5. **APK file (35MB live_base.apk) uploaded** to remote, available via IOResolver
6. **FETCH events down from 801→51** (cut by scan-kill hook)

## Current state

```
>>> N.l threw: java.lang.IllegalStateException: Invalid boolean value=-1
```

N.l's method `l(Application, String)` executes fully but returns -1. Post-N.l probes show zero output — no decrypted DEX materialized anywhere. 453 non-zero pages = only libexec.so.

## Root cause analysis

The `-1` return is not a crash — it's a legitimate "decryption failed" sentinel from N.l. Likely causes:

1. **Missing JNI asset access** — N.l probably reads DEX data from the APK via JNI asset APIs (`AAssetManager`, `AssetManager`), not via `open()` syscalls. The IOResolver never fires (`[IO] providing base.apk` not printed).

2. **Decryption key/initialization wrong** — the SINGLETON, vtable, dispatch table, and GOT entries may have incorrect values. N.l derives encryption keys from runtime state that may not be fully replicated.

3. **Wrong JNI method** — `l(Application, String)Z` might not be the main unpack entry. Other methods like `b(Ljava/lang/String;)[B` (b2b — byte-to-byte decrypt) are needed after `l` completes setup.

4. **ijiami.dat format** — N.l reads the 4.5MB dat file but may expect a different format or additional files.

## Key files modified this session

| File | Changes |
|------|---------|
| `_scratch/Unpack.java` | Pre-map disabled; scan-kill hook at 0x1203767c; LastResortHook forces R0=0; PAGE@0x12038000 re-protect enabled; FETCH limit 50; walk traces disabled during N.l; Backend proxy disabled; raw Unicorn hooks disabled; APK IOResolver added |
| `_scratch/run_lever_remote2.py` | New detached-run script with APK upload, polling, log fetch |
| `_scratch/run_lever_remote.py` | Updated timeout |
| `_assets/live_base.apk` | Uploaded to remote harness directory |

## Recovered during session

- **Scan architecture**: veneer table at 0x1207b400 dispatches via ARM-mode LDR PC entries to every 4KB page from 0x7b290 upward. Each entry is a 16-byte position-independent trampoline.
- **Scan exit**: CBZ at 0x12037664 exits when R0=0 after BLX. Setting R0=0 in the LastResortHook hits this exit.
- **Second scan call**: BL at 0x1203767c → 0x1207b7d0 is the actual page-walk dispatcher. LR from all FETCH events = 0x12037681.
- **Pointer table at 0x12082340**: Contains stale on-disk defaults (including 0x7b290) — never populated by skipped ctors.

## To reproduce

```bash
cd C:/Users/Nestor/Workspace/Xuper/XuperPlugin
python _scratch/run_lever_remote2.py
```

## Next steps

1. **Add JNI asset I/O logging** — trace what files/memory N.l reads during execution via unidbg's `IOResolver` and `FileIO` hooks
2. **Try calling `b2b` after N.l** — the byte-to-byte decrypt method at 0x12039400. Even if `l` returns -1, `b2b` might produce output from the ijiami.dat
3. **Dump SINGLETON state pre/post N.l** — check if the dispatch table was populated correctly
4. **Identify the actual unpack JNI method** — search the Java decompiled source (telelatino_jadx/) for the native method declarations and the correct call sequence
5. **Add EventMemHook for WRITE** with persistent storage — capture N.l's decryption writes before they're lost
