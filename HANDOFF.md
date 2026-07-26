# HANDOFF — Orchestrating from Win11 (.5) across TV box (.4) and Ubuntu (.40)

For the next agent continuing XuperPlugin. Read [README.md](README.md),
[ARCHITECTURE.md](ARCHITECTURE.md), [NEXT-BLOCKER.md](NEXT-BLOCKER.md) first.

## TL;DR

- **Do we still need MITM at .40 for the next step?** **No.** The next blocker
  (reverse `startPlayLive`) is solved by dumping the decrypted DEX on the box and
  reading it with jadx — no traffic interception. MITM only comes back to
  re-capture fresh `s`/`t` session cookies (~30 min lifetime) for end-to-end
  testing, or if you fall back to the harder frida SSL-unpin route.
- **Orchestration stays on Win11 (.5)** until `.40` is proven able to run the
  whole loop without killing its own internet. Win11 is the safety net.

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
- Box `.4`: rooted, `su` no password. Laptop `.40`: `nestor` / `ian20jesus`.
- Plugin appId `com.xuper.plugin`, launch `.ConfigActivity`.
- Device: `userId=694951876`, `portalCode=6e54356f76774c54574b303d`,
  streamKey `cyx_93531158996778016`, SN `ca0e53edac957b8f6f187528933355f1`.
- Playlist host `cdsr.higoesutn.com:80`; segments `magloud.y6oseldsc.online` (open).
- Pinned portalCore hosts: `espjey.ysnihrwtg.com`, `sxowvd.jzvqwcyor.com`,
  `yrqucu.czxenpyba.com`, `eskna.ucpjdhivl.com`, `ernsm.prxmnvhcy.com`.
