# HANDOFF — Orchestrating from Win11 (.5) across TV box (.4) and Ubuntu (.40)

For the next agent continuing XuperPlugin. Read [README.md](README.md),
[ARCHITECTURE.md](ARCHITECTURE.md), [NEXT-BLOCKER.md](NEXT-BLOCKER.md) first.

## TL;DR (session 12 — 2026-07-29)

> **READ [`NEXT-BLOCKER.md`](NEXT-BLOCKER.md) for full status.**
> Full log: [`SESSION-2026-07-27.md`](SESSION-2026-07-27.md).

**Done:** Full API format recovered. 3DES body crypto 100% proven. 65+ hosts probed
across XTV + 3 sister apps — version gate + CF WAF are universal. Plugin builds +
deploys + probes. Sister APKs analyzed (BrasilTV ijiami, TeleLatino SecNeo, YouCine
ijiami). Unidbg on `.40` — 36 iterations, singleton buffer + GOT fix working, one
self-decrypting ctor trap remains before DEX decryption.

**Blocker:** DES domain key inside libexec.so's ijiami layer. Unidbg is one ctor
away. frida definitively blocked by ijiami v4.

**Next:** `.40` — identify self-decrypting ctor via Ghidra, NOP it → JNI_OnLoad succeeds
→ RegisterNatives → call N.b2b(ijiami.dat) → get decrypted DEX → extract DES key
→ decrypt portalCore host → `returnCode=0` → wire pipeline.

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
| Ubuntu laptop | 192.168.100.40 | Build host (gradle, Android SDK, jadx, mitm) | **paramiko SSH** |

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

## Updated next steps

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
