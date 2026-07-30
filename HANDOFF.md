# HANDOFF — Orchestrating from Win11 (.5) across TV box (.4) and Ubuntu (.40)

For the next agent continuing XuperPlugin. Read [README.md](README.md),
[ARCHITECTURE.md](ARCHITECTURE.md), [NEXT-BLOCKER.md](NEXT-BLOCKER.md),
[SESSION-2026-07-29.md](SESSION-2026-07-29.md) first.

## TL;DR (session 17 — 2026-07-29 ~21:00) — READ THIS FIRST

**Biggest milestone yet: reached unidbg's REAL `FindClass` implementation** (`DalvikVM$3`) for
the first time in this whole project — past every fake-vtable/anti-tamper gate, into actual
DVM/JNI bridge activity.

**Session16's decrypt-loop blocker cleared** with two fixes in `_scratch/Unpack.java`:
1. `sl`(r10) forced to `6` right after it's computed (hook `0x12037db0`, the instruction AFTER
   `asr.w sl,r6,#4` at `0x12037dac` — hooking `0x12037dac` itself fires too early and gets
   overwritten by the instruction it's supposed to patch).
2. A trailing second call to the same decrypt routine (`0x12037dce`) reuses the leftover `r5`
   "remaining bytes" register as a byte-length arg — **zero only `r1` at the call site**, not
   `r5` itself (zeroing `r5` broke its later legitimate reuse at `0x12037e0a` as an arg to a real
   string-table resolver, corrupting downstream JNI setup in a way that was hard to notice at
   first — a good lesson: don't clobber a register just because it's the wrong value at ONE use
   site if it's reused later for something unrelated).

**New blocker, precisely diagnosed:** `FindClass(env, className)` is called for real (ground-truth
disasm confirms real JNIEnv `r4`≈`0xfffe12a0`, real fn ptr from JNIEnv+0x18) but `className`
(`r1`) is NULL. It's read from `*(malloc'd-0x3c-byte-buffer + 4)` — that buffer (allocated at
`0x12037c50-56`, stored at `P2+0x18c`) is almost certainly the real native-method table (0x3c =
6× `JNINativeMethod` structs, matching our `sl=6` exactly) but nothing has written real
name/sig/fnPtr data into it yet. The transform chain between the decrypt loop and this call
(`0x12026d74`, `0x1203f9b0`, `0x1207b630`×2, `0x1207b400`×3) hasn't been traced — that's the
session 18 starting point. Full disasm + exact next steps in NEXT-BLOCKER.md's session 17 section.

**Bonus, already ready:** the DES/portal-host analysis pipeline (`scripts/analyze_decrypted_dex.py`,
built and tested by a concurrent session against real TeleLatino DEX + the plugin's own source)
is sitting ready to point at `/tmp/apkx/app_decrypted.dex` the moment `N.b2b` finally returns it.

## TL;DR (session 16 — 2026-07-29 ~20:50)

**`0x12037c18` (session15d/e's blocker) genuinely cleared.** Session15d/e's "second fake object
via a different GOT slot" theory was **wrong** — live register-dump hooks proved `sb`(r9)
resolves to our own known `sa` GOT slot; there's only one singleton object (`P2`), same as every
other blocker. The real bug: `P2+0x24` (never populated) held `0`, so the call chain's `that`
pointer was `0`, so `*(0+0x38)` read `0` off the mapped null page, so `blx r4` jumped to `PC=0`.
**Fix:** `P2+0x24` = self-ref `P2` (0x12086914), `P2+0x38` = `VTABLE_STUB|1` (0x12086928).
Verified via walk trace: execution now reaches `0x120378a4`/`0x120378a6`, past `0x12037c18`.

**New blocker — a different class of bug entirely:** a self-decrypting native-method table walk
crashes with `UC_ERR_READ_UNMAPPED, address=0x12280001` at `PC=0x1203a36e`, inside a small
per-entry XOR-decrypt routine (`0x1203a314`) called in a loop (`0x12037dac`-`0x12037dc6`) for a
loop bound (`sl`/r10) that's ~12,700x larger than the real table (only ~2 plausible real entries
found at the buffer base `0x12240484`). Not a null-pointer bug — a buffer-overrun-shaped bug
(loop bound vs. real allocation size mismatch). Root cause (where the true count comes from) not
yet traced. Full disasm (capstone ground truth) of both the decrypt routine and its caller loop
is in NEXT-BLOCKER.md's session 16 section, along with a concrete first thing to try (force `sl`
to a small safe value via a CodeHook at `0x12037dac`, cheapest-fix-first per this project's
proven methodology).

**Next:** try forcing `sl`/r10 small at `0x12037dac`; if that doesn't stick, trace back further
(before `0x12037c50`) to find where the entries buffer + true count are set up. See
NEXT-BLOCKER.md session 16 section for the exact plan.

## TL;DR (session 15e — 2026-07-29 ~19:55)

**BTV binaries extracted from .37. unidbg confirmed past 0x1203725c — new crash at 0x12043545.**

1. **.37 extraction (no root needed):** APKs are world-readable at `/data/app/*.apk`. `unzip -p`
   pulled `assets/ijm_lib/armeabi/libexec.so` (435KB) + `assets/ijiami.dat` (4.2MB) from
   BrasilTV APK. Root attempts (DirtyCow, TowelRoot) all blocked on this kernel (3.10.33, SELinux
   enforcing). SSH via plink + DSA host key, password empty, u0_a70 only.

2. **BTV libexec.so = XTV libexec.so:** All hook addresses identical (0x37289 ctor, 0x3725c crash
   site, 0x2e5d4 sanity, 0x3a1d8 ctor-patch, etc.). Swapped file paths in Unpack.java, recompiled.

3. **unidbg run (session15e):** 0x1203725c blocker **fully bypassed** — execution walked through
   0x1203724c-0x12037250 cleanly, jumped to 0x12037878 region, reached `[ENTRY] 0x1201e378` which
   completed and returned. **New crash:** `UC_ERR_FETCH_PROT` at PC=0x0, LR=0x12037c4f, inside
   JNI function at 0x12043545 — null function pointer call from second-level object deref.
   This matches the session15d "second object needed for bl 0x12037c18" blocker exactly.

**Next:** build second fake object for 0x12037c18 call (different GOT slot via sb/r9, needs
`*(obj+0x24)→*(that+0x38)` chain + `*(obj+0x44)` arg). See NEXT-BLOCKER.md session15d section.

**Git note:** Two agents shared git index — `_assets/brtv_*` landed in 8361a82 instead of
3ebe428. No data loss, just wrong commit message. Verified consistent in be5a5eb.

## TL;DR (session 15d — 2026-07-29 ~19:35)

**The old P0 (`vtable+0x40`), the unconditional `kill(pid,SIGKILL)` blocker, AND the full
`0x1201e378` function are all now genuinely fixed/cleared** — not hacked, actually executing
real code and returning normally. This is by far the furthest any session has reached.

1. `vtable+0x40` — real object `P2 = *(*(0x12082340))` (`=0x120868f0`). Fixed `P2+0x40`,
   `P2+0x188`, `P2+0x109`.
2. `kill(pid,SIGKILL)` anti-tamper region — bypassed via `FLAG_X` at `0x12092944` (was 0, a
   branch gate at `0x1203789a` that took the anti-tamper exit; set to 1, confirmed via wide
   execution-walk trace: zero kill() hits, real init path taken instead).
3. `0x1201e378` — turned out to reuse `P2` as a fake "env" object at FIVE more offsets
   (`+0x10` self-ref, `+0x60`, `+0x5c` ×5 cleanup calls, `+0x18`, `+0x68`, `+0x35c`), all
   populated with the same real, valid stub. **Confirmed complete + returns to caller.**

**New blocker:** the next call, `bl 0x12037c18`, needs a **second, separate fake object** — it
resolves a different GOT slot (register `sb`/r9) and reads `*(obj+0x24)` then `*(that+0x38)`
for its function pointer, plus `*(obj+0x44)` for the call arg. Not yet built/fixed — the
literal pool address for that GOT slot falls outside what's been dumped so far. See
[`NEXT-BLOCKER.md`](NEXT-BLOCKER.md) session 15d section for the exact disasm and next-step plan.

Harness (`_scratch/Unpack.java`) no longer needs the forced-`JNI_VERSION_1_6`/kill()-loop hack
to get this far — that code path is effectively dead now (harmless to leave as a safety net).

**Technique unlock for future sessions (still the single most valuable takeaway):** (1) dump
runtime memory *after* `JNI_OnLoad` executes (ctors decrypt the code by then) and disassemble
those exact bytes with capstone over SSH to `.40` — gives 100%-accurate ground truth, unlike
prior sessions' static/pre-decryption guessing. (2) When stuck on "why does execution reach X
instead of Y", don't bisect address-by-address — add ONE wide-range `CodeHook` logging every
distinct address visited (`LinkedHashSet`, capped print count) across the whole suspect region
in a single run; it reconstructs the real path immediately instead of many slow round trips.
(3) When a fake-env-shaped `blx` call is only checked for "non-zero return", a cheap
self-referencing pointer (point the object's own slot back at itself, reusing an already-real
function pointer elsewhere on it) is usually enough — don't build a whole new fake object until
you've confirmed the cheap trick doesn't satisfy the check.

## TL;DR (session 14 lever close — 2026-07-29 ~17:50)

**Host discovery exhausted** (dalvik/SNI/static DES). Notice hosts = `zxiws`/`nxiqj` without key.

**Unidbg breakthrough (partial):** exported `JNI_OnLoad` @ `0x1203725d` is `b.w #0x12043544`.
Call real body with **Thumb odd** offset `0x43545`. Forced `JNI_VERSION_1_6` achievable, but
init still fails on NULL `vtable+0x40` → `kill()` retry — **`N.l`/`N.b2b` not registered**.

**Single next step:** fix singleton/vtable slot `+0x40` so JNI completes naturally, then
`N.b2b(ijiami.dat)` → DES/portal domain. Harness: `_scratch/Unpack.java` + `run_lever_remote.py`.
Do **not** use UnpackV50 load path or PC-skip wchan `0x1202e39d`.

---

## TL;DR (session 14 sweep close — 2026-07-29 ~16:54)

**Four parallel tracks finished.** Notice hosts identified without DES (`zxiws`/`nxiqj`).
Static DES + dalvik portal-host hunt exhausted (MISS / negative). Unidbg v50 later
superseded by lever fix (see TL;DR above).

---

## TL;DR (session 14 cont — 2026-07-29 16:20)

**spkgVer fix deployed + probed:** still `portal200001` on all JSON hosts — header mismatch
was real but **not** the gate.

**BBDatabase:** `domain|DES=Sz0JjjU4…` → `/notice/api/get_notice` (notice host), not portalCore.
**TeleLatino:** SecNeo stub DEX — jadx useless.

---

## TL;DR (session 14 — 2026-07-29)

**Pivot (worked):** live XTV dalvik heap scan — CDN `main_addr` ≠ portal API; bootstrap cleaned;
`getSlbInfo` in probe. Portal API FQDN remains native-only; version gate unchanged on pool.

---

## TL;DR (session 13 close — 2026-07-29 15:48)

Crash @ `0x1203725c` was treated as anti-tamper. **(Session 14 correction:** that export is a
**branch stub** to real JNI @ `0x12043544`.) Full API + 3DES proven; 65+ hosts version-gated.

---

## TL;DR (archive — session 9, 2026-07-27)

**Done:** plugin builds + deploys + probes hosts (dual getAuthInfo/getLiveData).
Cookie interference fixed. b29/reserve1 = STATIC. getLiveData body enriched with
live fields. 3DES+envelope proven. VOD structure mapped.

**Blocked:** version-gate on pool; app DES-resolved host not in pool.

---

## TL;DR (archive — session 6, 2026-07-26)

> ⭐ **SESSION 6 BREAKTHROUGH (21:30) — see archive in `NEXT-BLOCKER.md`.**
> Vendor-signed **`XTV_4.34.5.apk`** only on `.4` (no debug re-sign). Heap pipeline:
> getAuthInfo → getLiveData → signed CDN → open magloud. Tokens server-signed.

## Machine topology (verified 2026-07-26)

| Host | IP | Role | Reached from Win11 by |
|------|-----|------|----------------------|
| **Win11** | 192.168.100.5 | **Orchestrator** (this machine, runs the agent) | — |
| TV box | 192.168.100.4 | Target device (rooted, Android 10, ijiami XTV) | **direct adb** |
| Ubuntu laptop | 192.168.100.40 | Build host + **unidbg** (`~/xtv-ghidra/harness`) | **paramiko SSH** |

**Why Win11 orchestrates:** it holds two INDEPENDENT control channels —
direct `adb` to `.4` and `SSH` to `.40`. If a MITM route change or iptables slip
kills `.40`'s internet, Win11 still controls `.4` AND can SSH `.40` to recover.
Never make device control depend on `.40`.

## Verified connection commands (from Win11)

Direct adb to the box (root confirmed — `uid=0`):
```bash
C:/adb/adb.exe connect 192.168.100.4:5555
C:/adb/adb.exe -s 192.168.100.4:5555 shell su -c "id"
```
Use `C:/adb/adb.exe` explicitly (also on PATH as `adb`).

SSH to `.40` — use inline paramiko (the `~/bin/ssh40.py` helper prints via cp1252
and crashes on emoji/unicode output; write results to a file or decode utf-8):
```python
import paramiko
c = paramiko.SSHClient(); c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
c.connect('192.168.100.40', username='nestor', password='ian20jesus',
          look_for_keys=False, allow_agent=False, timeout=10)
_, out, _ = c.exec_command('...'); print(out.read().decode('utf-8','replace'))
```
Gotcha: reading remote files that contain emoji — always `.decode('utf-8','replace')`,
never let it hit Windows cp1252 stdout raw.

## Division of labor (who does what)

- **Win11 (agent brain):** all `adb` device ops (install, launch, logcat, push/pull,
  DEX dump retrieval), all SSH orchestration, git commits/pushes, editing plugin
  source in `C:\Users\Nestor\Workspace\Xuper\XuperPlugin`.
- **.40 (build/analysis host):** `./gradlew assembleDebug`, jadx decompile, storing
  large artifacts, mitm (only when re-capturing cookies).
- **.4 (device):** runs XTV + XuperPlugin; source of the decrypted DEX dump.

Plugin source lives in TWO places — keep them in sync:
- Win11 repo (authoritative, git): `C:\Users\Nestor\Workspace\Xuper\XuperPlugin`
- `.40` build copy: `/home/nestor/Desktop/xuper/plugin`
Push edits Win11 → `.40` via SFTP before building (see `upload_src.py` pattern in git history).

## Build + deploy loop (Win11-orchestrated)

1. Edit source on Win11.
2. SFTP the changed `.kt` files to `/home/nestor/Desktop/xuper/plugin/app/src/main/java/com/xuper/plugin/`.
3. SSH `.40`: `cd ~/Desktop/xuper/plugin && source ~/.android_env && ./gradlew assembleDebug`
   (SDK at `/home/nestor/android-sdk`; `local.properties` already fixed).
4. Pull APK to Win11 OR install straight from `.40`? **Install from Win11 direct adb**
   to keep device ops independent:
   ```bash
   # copy APK .40 -> Win11 via SFTP, then:
   C:/adb/adb.exe -s 192.168.100.4:5555 install -r app-debug.apk
   ```
   (If signature mismatch: `adb uninstall com.xuper.plugin` first.)
5. Launch + watch: `C:/adb/adb.exe -s 192.168.100.4:5555 shell am start -n com.xuper.plugin/.ConfigActivity`
   then `... logcat -d | grep -E "XUPER|XuperPlugin"`.

## THE NEXT BLOCKER — execution plan (no MITM)

Goal: get the `startPlayLive` request format so the plugin can fetch fresh
playlist tokens itself. Full context in [NEXT-BLOCKER.md](NEXT-BLOCKER.md).

**Step 1 — Dump decrypted DEX from the ijiami app (on .4, driven from Win11):**
- Get a BlackDex APK (self-contained on-device unpacker; dodges ijiami anti-frida
  better than PC frida). Install from Win11:
  `C:/adb/adb.exe -s 192.168.100.4:5555 install BlackDex.apk`
- Open BlackDex on the box (drive via `adb shell input`/`monkey`, or ask user to
  tap once), select `com.android.mgstv`, dump.
- Pull the dumped dex to Win11:
  `C:/adb/adb.exe -s 192.168.100.4:5555 pull /sdcard/Android/data/io.va.blackdex/... C:\...\xtv_dex\`
- Alternatives if BlackDex fails: FART, Youpk, or frida runtime dump using the
  spawn + child-gating recipe in `docs/XTV-CAPTURE-STATUS.md` (on `.40`).

**Step 2 — Decompile (jadx):**
- Win11 has java 17 but no jadx. Either:
  - install on `.40`: `sudo snap install jadx` (password `ian20jesus`), push the
    dex there, `jadx -d /tmp/xtv_src /tmp/xtv_dex/*.dex`, grep remotely; OR
  - download the jadx release zip on Win11 and run `jadx.bat` (java present).
- The raw APK at `/home/nestor/Desktop/xuper/apk/XTV_4.34.5.apk` is ijiami-packed —
  jadx on it shows only the loader stub. Must use the Step-1 dump.

**Step 3 — Extract the request contract:**
```
grep -rEl "portalCore|startPlayLive|getLiveData|snToken|liveAddressList|playCode" <src>
```
Record: host (how the obfuscated domain list is chosen), path, pre-encryption
body fields, headers/cookies sent, whether the PATH is encrypted (wire paths are
opaque), and the `CertificatePinner` (so we know to omit it). Confirm the body
uses the 3DES scheme already in `XuperCrypto.kt` (key `2b494e53...`).

**Step 4 — Implement in the plugin:**
- Fill in `XuperApiClient.startPlayLive()` with the real host/path/body. Our OkHttp
  has no pinning, so it can call the pinned hosts directly.
- Have `M3uProxyServer` re-call `startPlayLive` each playlist cycle → the returned
  URL is the fresh playlist (path + one-time `d`) → it already passes the open
  magloud segments through.

**Step 5 — End-to-end test:**
- Need fresh `s`/`t` cookies (they expire ~30 min). Re-capture via MITM (below).
- Start the proxy, point VLC/StreamVault at `http://127.0.0.1:<port>/playlist.m3u`,
  confirm continuous live with no manual cookie paste.

## MITM — ONLY when re-capturing cookies (safe procedure)

Not needed for DEX reversal. When you do need fresh `s`/`t`:

Run mitm + iptables on `.40` (via SSH), but keep the redirect **box-source-only**
so `.40`'s own traffic is never redirected (this is what keeps `.40` online):
```bash
sudo mitmdump --mode transparent --ssl-insecure -p 8080 -w /tmp/cap.flow &
sudo sysctl -w net.ipv4.ip_forward=1
sudo iptables -t nat -A POSTROUTING -o wlp7s0 -j MASQUERADE
sudo iptables -t nat -A PREROUTING -s 192.168.100.4 -p tcp --dport 80  -j REDIRECT --to-port 8080
sudo iptables -t nat -A PREROUTING -s 192.168.100.4 -p tcp --dport 443 -j REDIRECT --to-port 8080
```
**Critical box route fix (Android per-interface policy routing) — from Win11 adb:**
```bash
C:/adb/adb.exe -s 192.168.100.4:5555 shell su -c "ip route replace default via 192.168.100.40 dev wlan0 table wlan0"
```
Cold-start XTV, let it play ~30s, parse the flow with the mitmproxy reader
(strings-grep misses structure — see `parse_flow.py` in git history / prior session).

**ALWAYS recover after (from Win11, so it works even if .40 net is flaky):**
```bash
C:/adb/adb.exe -s 192.168.100.4:5555 shell su -c "ip route replace default via 192.168.100.1 dev wlan0 table wlan0"
# then SSH .40:
sudo iptables -t nat -F ; sudo killall mitmdump
```
The CA is already installed on the box: `/data/misc/user/0/cacerts-added/c8750f0d.0`.

## Safety rules (do not break)

1. Never add an `OUTPUT`-chain REDIRECT on `.40` — that reroutes `.40`'s own
   traffic into mitm and kills its internet when mitm stops. Box-source-only
   `PREROUTING` is safe.
2. Always run MITM recovery (route restore + iptables flush + kill mitm) at the
   end of any capture, even on error. Prefer issuing the box route-restore from
   Win11 adb (independent of `.40`).
3. Keep device control on Win11's direct adb, not tunneled through `.40` SSH —
   the SSH→adb indirection hung in a prior session and couples you to `.40`.
4. Don't commit ephemeral tokens (`s`/`t`/`d`) to git — they expire. Keep them in
   scratch/notes only.

## When orchestration can move to .40 (migration criteria)

Move the agent/orchestration onto `.40` only once ALL hold:
- MITM capture runs start→recover with **zero** loss of `.40` internet across
  several cycles (box-source-only redirect + reliable recovery proven).
- A single script on `.40` does capture → parse → (future) call startPlayLive →
  serve, and cleans up on any exit path (trap/finally).
- `.40` can `adb` to `.4` and rebuild/redeploy without manual help.
Until then: Win11 stays the brain; `.40` is just the build/analysis muscle.

## Quick reference

- XTV: `com.android.mgstv` v4.34.5 (ijiami-packed). Creds `nestor.ale@gmail.com` / `Ian20jesus`.
- Real app class (inside encrypted DEX): `com.interactive.brasiliptv.app.AppWrapper`
- Box `.4`: rooted, `su` no password. Laptop `.40`: `nestor` / `ian20jesus`.
- Plugin appId `com.xuper.plugin`, launch `.ConfigActivity`.
- Device: `userId=694951876`, `portalCode=6e54356f76774c54574b303d`,
  streamKey `cyx_93531158996778016`, SN `ca0e53edac957b8f6f187528933355f1`.
- Playlist host `cdsr.higoesutn.com:80`; segments `magloud.y6oseldsc.online` (open).
- **Jadx 1.5.3 installed on Win11**: `C:/Users/Nestor/Downloads/jadx/bin/jadx.bat`
  with `JAVA_HOME="C:/Program Files/Microsoft/jdk-17.0.18.8-hotspot"`.
- **DarkDex APK**: `C:/Users/Nestor/Downloads/DarkDex.apk` (installed on box as `com.darkdex`)

## portalCore hosts discovered (DarkDex intel, 2026-07-26)

Previously known 5 pinned hosts: `espjey.ysnihrwtg.com`, `sxowvd.jzvqwcyor.com`,
`yrqucu.czxenpyba.com`, `eskna.ucpjdhivl.com`, `ernsm.prxmnvhcy.com`.

Additional hosts from DarkDex intel (total 11 more):
`bmagon.sxcrwendu.com`, `vgwbm.uwfyobivh.com`, `yvhcn.hxjebagrv.com`,
`zxiws.tcgwhnvym.com`, `rokbd.ysrkwctjg.com`, `nxiqj.jgrqyxupl.com`,
`sfgknh.qho3cnsyil.com`, `jpktl.gczpjqyfu.com`, `iyut.xgw3sdzoac.com`,
`ioermd.l7hsgo8g.com`, `hbyyqx.qtg20rybb.xyz`.

CDN auth endpoint: `vdes.medika7c7.com` with query params including
`auth_id=694951876_com.android.msandroid__0`, `user_id=694951876`,
`ctrl_type=account`, `app_id=com.android.msandroid`.

DarkDex extracted config: `"apkVersion":"43405","appId":"com.android.msandroid"`.
Note: appId differs from package name (`com.android.mgstv`).

## ijiami v4 dumping attempts (2026-07-26)

| Tool | Result | Detail |
|------|--------|--------|
| BlackDex32 v3.2 | HUNG | Progress dialog "Desempaquetando…" frozen. ijiami anti-tamper suspected. |
| DarkDex | 0 DEX, 1249 URLs, 12 classes | Root mode, full mem dump. Header-wiped DEX not recoverable. Intel file at `C:/Users/Nestor/Downloads/xtv_intel.txt`. |
| Manual /proc/PID/mem dump | 0 DEX magic | 512MB dalvik region scanned with grep `\x64\x65\x78\x0a\x30\x33\x35` — none found. |
| Stub DEX decompilation | 4 classes found | `s.h.e.l.l` package: AppComponentFactory, Application, native loader, callback. Real app class: `com.interactive.brasiliptv.app.AppWrapper`. Loader uses `DETool.loadDEso()` for decryption. |
| libexec.so strings | No API strings | Native lib only handles decryption — actual API code is in encrypted `ijiami.dat` (4.5MB) decrypted at runtime. |

## Updated next steps (session 15c — supersedes session 15/14 lines below for priority)

1. **Unidbg P0 (new):** `vtable+0x40` fixed AND `kill(pid,SIGKILL)` anti-tamper region bypassed
   (session 15c, via `FLAG_X` at `0x12092944`). Current blocker: disassemble `0x1201e378`'s body
   (not yet done) to find what produces the unmapped-read address `0x412f6db0` at entry — likely
   an uninitialized field left over from earlier ctor-skip hacks, not another anti-tamper gate.
   See [`NEXT-BLOCKER.md`](NEXT-BLOCKER.md) session 15c section for the exact repro/dump.
2. Once `0x1201e378`/`0x12037c18` complete cleanly, confirm `RegisterNatives` fires (offset
   `0x35c` calls at `0x120379d0`-`0x12037a80`, JNINativeInterface index 215) for `s/h/e/l/l/N`.
3. `N.b2b(ijiami.dat)` → DES key + portal domain.
4. Plugin probe for `returnCode=0`.

<details><summary>session 14 (superseded — kept for history)</summary>

1. ~~Unidbg P0: fix NULL `vtable+0x40` at check `0x120370c6`~~ — done in session 15, see above.
2. `N.b2b(ijiami.dat)` → DES key + portal domain.
3. Plugin probe for `returnCode=0`.

</details>

## Updated next steps (archive — early session)

**Priority A — MITM capture fresh s/t cookies:**
1. Follow MITM procedure in [ARCHITECTURE.md](ARCHITECTURE.md) (box-source-only PREROUTING on `.40`).
2. Cold-start XTV, let it play ~30s, capture traffic.
3. Parse flow with `parse_flow.py` pattern — extract `s`/`t` cookies from playlist requests.

**Priority B — Probe portalCore API with fresh cookies:**
1. Use the 16 portalCore hosts (5 known + 11 from DarkDex).
2. Try paths: `/api/portalCore/v4/startPlayLive`, `/api/portalCore/v3/startPlayLive`, etc.
3. Try body fields: `{channelCode, portalCode, userId, userToken, type:"live", columnId?}`
4. Use plugin's OkHttp client (no cert pinning) + 3DES encryption from `XuperCrypto.kt`.
5. First response that returns `liveAddressList` → we have the format.

**Priority C — Frida DEX dump (fallback):**
1. Install `frida-server-16.x.x-android-arm` on box.
2. Hook `N.b2b(byte[], int)` native method during XTV startup — capture decrypted DEX bytes.
3. Or hook `DETool.loadDEso()` to intercept the decryption call.
4. Write decrypted byte array to disk, then jadx it.
5. Requires bypassing ijiami anti-frida (fork+ptrace).

**Priority D — Pure static (research):**
1. The AWAKE wiki says ijiami.dat has `SM4` cipher with `per-chunk key derivation`.
2. The plaintext-MD5 integrity tag is visible in the header.
3. Key material may be in `libexec.so` `.rodata` section.
