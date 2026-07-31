# XTV decrypted DEX — the GOAL0 keystone (persisted session 28)

Carved from `com.android.mgstv` (v4.34.5, ijiami-packed) **live process memory** on the rooted `.4`
box, then checksum-fixed so decompilers accept it. This is the decrypted app code that unlocks both
Goal 1 (find/patch gates) and Goal 2 (portalCore request pipeline). See `../../GOAL0.md`.

## Files
| File | What |
|------|------|
| `app_classes_fixed.dex` | Main DEX (~9 MB), adler32+sha1 recomputed — **jadx/baksmali load it cleanly** |
| `d2_classes.dex` | Multidex #2 (~12 MB) — holds `p2` / `r2` (device/version helpers) |
| `dex_strings.txt` | Extracted string table from the main DEX (grep target) |
| `validate.py` | The checksum-fix script (recomputes adler32 + SHA-1 in the DEX header) — reusable for future carves |
| `portal.pcap` | Wire capture of the replayed `getAuthInfo` (still `portal200001` — proves the gate is above the body) |
| `maps3.txt` | `/proc/<pid>/maps` of the live app — shows the `[anon:dalvik-DEX data]` regions the DEX was carved from |

## Regenerate the decompiled source (not committed — 59 MB / 11k files, derivable)
```bash
jadx -d jadx_out  app_classes_fixed.dex
jadx -d jadx_d2   d2_classes.dex
```

## How it was carved (reproducible on `.4`)
1. `adb -s 192.168.100.4:5555 shell su -c 'pidof com.android.mgstv'`
2. `cat /proc/<pid>/maps` → find the large `[anon:dalvik-DEX data]` r-- regions (see `maps3.txt`).
3. `dd if=/proc/<pid>/mem` over each region → grep `dex\n035` → carve `header.file_size` bytes.
4. Run `validate.py` on the carved blob to recompute adler32 + SHA-1 (memory-dumped DEX have a stale
   checksum; jadx loads 0 classes until this is fixed).

## What was already extracted from it (session 28)
- **Goal 2:** the full portalCore request pipeline (Retrofit `jd.a`; interceptors `ld.a`/`ld.b`; 15 body
  fields incl. `B29`; `appId=com.android.msandroid`, `apkVer=43405`). Body now byte-exact in
  `XuperApiClient.kt`. `portal200001` proven to be enforced **above** the body (host pool / TLS). See `GOAL2.md`.
- **Goal 1:** the gate-check search (email-reg / forced-update / VIP-payment) is **NOT done yet** — run it
  against `jadx_out/` (regenerate first). See `GOAL1.md` + `GOAL0.md` "Goal 1 targets".
