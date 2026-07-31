# GOAL 2 — Own IPTV APK using XTV's backend (XuperPlugin)

> **Self-contained handoff.** Everything an agent needs is in this file. Deeper detail lives in
> `ARCHITECTURE.md`, `README.md`, `GOAL1.md` (shared emulation findings, sessions 24–26), and the plugin
> source under `app/src/main/java/com/xuper/plugin/`, but you can plan from this file alone. This file +
> `GOAL1.md` are the two canonical working docs from now on.

## Objective
Ship **our own** IPTV app/plugin (XuperPlugin) that uses **XTV's backend** to stream the same channels —
turning XTV (`com.android.mgstv`) into an open **M3U/HLS source** playable in VLC / TiviMate / Kodi, with
**no email registration, no VIP paywall, no forced updates**, all from our own APK.

## Honest verdict (read first)
**This is the REACHABLE goal and it is ~90% built.** The full plugin exists and works up to one specific
server-side rejection (`portal200001`). The remaining work is a **wire diff** — learn exactly how the real
app signs its portalCore auth request, and patch the one differing field in our client. **The cheapest way
to get that is NOT the unidbg emulation** (which is stuck on the hard `N.l→true` wall — see `GOAL1.md`).
The cheapest way is to **read the app's own request-building code from a live memory dump on the rooted
`.4` box, or to cert-unpin and capture one real request off the wire.** Prioritize those over emulation.

## How the stream actually works (fully reverse-engineered)
```
 portalCore API (CERT-PINNED HTTPS) ── hands out ONE-TIME (playlist-path, d-cookie) pairs
   hosts: espjey.ysnihrwtg.com, sxowvd.jzvqwcyor.com, yrqucu.czxenpyba.com, eskna.ucpjdhivl.com
          │
          ▼
 Playlist  cdsr.higoesutn.com:80 (HTTP, Cloudflare)  GET /<opaque-path>  Cookie: d=…; s=…; t=…
          │  → 200 application/vnd.apple.mpegurl (~1080 B), lists 6 live ~4s segments (~24s window)
          │  ONE-TIME USE: refetch same path+d → 409 Conflict
          ▼
 Segments  magloud.y6oseldsc.online:80 (HTTP)  /live/<key>/<key>_cyx_cj_<rd>.ts
          │  → 200 video/mp2t (~500-850 KB).  ***FULLY OPEN — no cookies, no auth.***  backup: caeo.wvdbozpfc.com
          ▼
 XuperPlugin M3uProxyServer → standard HLS on 127.0.0.1 → any IPTV player
```
Cookies: `d` (~1100 ch, one-time playlist token), `s`/`t` (44 ch base64url, ~30 min session).

## Current state
- **The plugin is largely implemented** (`app/src/main/java/com/xuper/plugin/`):
  | File | Role | State |
  |------|------|-------|
  | `XuperApiClient.kt` | portalCore calls, 3DES bodies, M3U gen, host pool | full flow coded |
  | `M3uProxyServer.kt` | local HLS proxy, rewrites playlist → standard HLS | working |
  | `XuperCrypto.kt` | 3DES request-body crypto | **key recovered** (`2b494e53…`) |
  | `DeviceFingerprint.kt` | device fields for `snToken` | implemented |
  | `ConfigActivity.kt` | paste cookies, test session, start proxy | working UI |
  | `PluginService.kt` / `PluginContract.kt` | Messenger provider to host player | implemented |
- **Segment tier is fully open** — once you have a playlist, the `.ts` URLs need no auth.
- **3DES request crypto is correct** — the server *parses* our request (returns HTTP 200), it just
  *rejects* it on a business rule (`portal200001`).
- Known constants: `portalCode = "masnew"`, portal bootstrap hosts in
  `XuperApiClient.PORTAL_BOOTSTRAP_HOSTS`, endpoints `/api/portalCore/v3/snToken`, `/v8/login`,
  `/v15/getSlbInfo`, `getAuthInfo`.
- **Session 25 — the packer's native method table is now fully known** (dumped from `RegisterNatives` in
  emulation; see `GOAL1.md` for the full context). The one directly relevant to signing:
  **`SE.sd (Ljava/lang/String;)Ljava/lang/String;` @ `0x1203fc3d`** — a native String→String routine
  (candidate string-decrypt / signing helper). The app's request-signing itself lives in the **decrypted
  app DEX** (the Kotlin/Java that builds the portalCore envelope), which is only available once `N.l→true`
  unpacks it (`b2b` decrypts the DEX) — see the blocker below.

## Accomplishments
- Complete three-tier stream architecture mapped end-to-end (portalCore → playlist → open segments).
- Working local HLS proxy (`M3uProxyServer`) — plays in standard IPTV players once fed a playlist.
- Recovered 3DES key + device-fingerprint/`snToken` scheme; coded every portalCore endpoint.
- MITM (on `.4` via `.40`) captured real cookie formats, host pools, and the `getAuthInfo` request shape.
- Confirmed the crypto is accepted (server returns structured JSON, not a parse error).
- **Session 25: full native method map captured** (`RegisterNatives` dump) — we know exactly which native
  routines exist (`N.l/r/ra/b2b/m/sa/al/i`, `HM.l/u`, `SE.sd`) and their addresses/signatures. `SE.sd`
  is the signing/string-crypto candidate.

## THE blocker (single, well-defined) and what actually gates it
**portalCore rejects our auth with `returnCode = portal200001`** — the universal **version-gate** (same
gate seen across XTV / BrasilTV / TeleLatino / YouCine). Our request is well-formed and decrypts
server-side, but differs from the **real app's** request by one or more fields (a version string, a
request signature, and/or a server-accepted `snToken`). The code says it plainly:
```
XuperApiClient.kt:584  "returnCode may still be portal200001 until wire diff from .4 is done"
XuperApiClient.kt:600  val isBlocked = msg.contains("portal200001")
```
**Fix = a wire diff: capture the real app's exact accepted portalCore request, diff against ours, patch
the differing field(s).** The question is only HOW to see the real request. Three routes below.

## Routes to the wire diff — ranked cheapest first
1. **Live memory / DEX dump from `.4` (rooted, ADB) — RECOMMENDED, sidesteps the emulation wall.**
   The real app runs and streams on `.4`, so ijiami decrypts the app DEX **into process memory** at
   runtime, and the app's own code builds an accepted portalCore request there. Prior sessions already
   dumped ~52 MB of process memory from `.4` and carved `libexec.so` out of it. Do the same for the
   **decrypted `classes.dex`** (search the maps for `dex\n035` magic / large RW anon regions), then read
   the Kotlin/Java that builds the portalCore envelope — the version field, the signature, the header set.
   This needs **no unidbg, no `N.l→true`.** It is the shortest line to the fix.
2. **Cert-unpin + MITM on `.4`.** Defeat portalCore cert pinning on the live app and read one accepted
   `getAuthInfo`/`login` request straight off the wire, then diff. Blocked so far by ijiami's anti-Frida
   ptrace-block, so this needs a non-Frida unpin (patched app, network-layer trick, or a different rooted
   box). Medium difficulty.
3. **Emulation route (HARD — shared wall with `GOAL1.md`).** The signing ultimately lives in the decrypted
   DEX and/or `SE.sd`; to get it via unidbg you must reach `N.l→true` so `b2b` unpacks the DEX and the
   real method bodies are patched in. **Session 27 made real progress here** — the packer's resolver gates
   (cE/cB/cC) are now OPEN (via a vtable-redirect to a populated function-pointer page), and `N.l` advanced
   into its init phase 2. But it's **still not at `N.l→true`**: the blocker moved to a C++-object walk at
   `0x120372e4` (`r0=[*sl]; r1=[r0+0x24]; r4=[r1+0x38]; blx r4`, `sl=0x121b1ec0`, `[*sl]=0x0` — the packer's
   class-table context is empty because FindClass went through our mocks). `RegisterNatives` is captured,
   but the bodies stay stubbed until `N.l` succeeds. **Do not treat emulation as the fast path for Goal 2 —
   it is the same hard, still-open problem as Goal 1. Track its state in `GOAL1.md`.**

## Next steps (ordered — cheapest first)
1. **Dump the decrypted app DEX from `.4` live memory** (root + ADB available). Carve `classes.dex`
   (`dex\n035` magic) from a full `/proc/<pid>/maps`+`mem` dump of `com.android.mgstv`. This is the
   highest-value, lowest-effort move and it bypasses the whole `N.l` emulation saga.
2. **From the DEX (or from `SE.sd`), read how the portalCore auth request is built** — specifically what
   makes a request pass the version-gate: the exact `apkVersion`/`sysVersion` strings, any `sign`/HMAC
   over the body, and the required header/field set for `getAuthInfo`/`login`.
3. **Diff vs `XuperApiClient`'s request builder** (`buildRequestEnvelope` / `postJson` / `requestSnToken` /
   the `portalCode="masnew"` + version fields) and patch the differing field(s) so a live call returns
   success instead of `portal200001`.
4. **Validate end-to-end:** plugin mints a fresh (playlist-path, d-cookie) → fetches playlist from
   `cdsr.higoesutn.com` → `M3uProxyServer` serves HLS → plays in VLC/TiviMate. The playlist token is
   one-time and the window is ~24s, so the proxy must **mint on demand** (already the design).
5. **Ship:** build the APK on `.40`, install on `.4`, confirm live playback.
6. *Fallback only if 1–2 fail:* pursue the emulation `N.l→true` wall (see `GOAL1.md` next-steps) to unpack
   the DEX synthetically.

## Kill-criterion
Try the **`.4` live DEX dump (step 1)** first — it should take one focused session to know if the
decrypted DEX is carve-able. If it is, the wire diff and the `portal200001` fix follow quickly and the
plugin ships. If the DEX can't be recovered from memory AND cert-unpin fails AND emulation `N.l→true`
stays stuck, escalate — the goal then depends on an ijiami breakthrough shared with Goal 1.

---

## Handoff / ops (verified working session 23; `.40` access confirmed)

### Machines
| Host | Addr | Role | Access |
|------|------|------|--------|
| Win11 `.5` | local | Orchestration (this box) | git-bash, `ssh`, `sshpass`, `scp` present |
| Ubuntu `.40` | `192.168.100.40` | **plugin build host** + unidbg emulation | `ssh xtv40` (key-based, see below) |
| TV box `.4` | `192.168.100.4:5555` | rooted test device (network ADB), **live DEX/MITM target** | `adb connect 192.168.100.4:5555` |
| Android `.37` | `192.168.100.37:2222` | rooted KitKat (SSH) | `ssh root@…:2222` (paramiko pinned `2.11.0`) |

### `.40` access — IMPORTANT
- **SSH is key-based now.** Alias `xtv40` in `~/.ssh/config`, key `~/.ssh/id_xtv40`, user `nestor`.
  The old password `ian20jesus` is **dead** — do not use it.
- `/tmp` is **wiped on reboot.** Rebuild the emulation asset tree from the APK (survives in `_assets/`):
  ```bash
  ssh xtv40 'mkdir -p /tmp/apkx && cd /tmp/apkx && unzip -oq ~/xtv-ghidra/harness/_assets/live_base.apk'
  ```
- `.40` drops intermittently; just retry (`ping -n 1 192.168.100.40`, then re-run).

### `.4` live memory dump (for step 1)
```bash
adb connect 192.168.100.4:5555
adb -s 192.168.100.4:5555 shell su -c 'pidof com.android.mgstv'      # get PID
# dump maps + mem (root); carve dex\n035 regions offline. Prior art: 52MB dump already done this way.
adb -s 192.168.100.4:5555 shell su -c 'cat /proc/<pid>/maps'         # find RW anon / large regions
# pull memory ranges, then grep for the DEX magic bytes: 64 65 78 0a 30 33 35 00
```

### Plugin — build & deploy (on `.40`)
```bash
ssh xtv40 'source ~/.android_env; cd ~/Desktop/xuper/plugin && ./gradlew assembleDebug'
adb connect 192.168.100.4:5555
adb -s 192.168.100.4:5555 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s 192.168.100.4:5555 shell am start -n com.xuper.plugin/.ConfigActivity
adb -s 192.168.100.4:5555 logcat | grep -E "XUPER|XuperPlugin"
```
(Plugin source of truth for the build is on `.40`; the repo `app/` tree mirrors it.)

### Emulation harness (fallback route only) — build & run
- Local working copy: **`_scratch/Unpack.java`**. Remote: Maven project `~/xtv-ghidra/harness`, class
  `com.xtv.Unpack`, source `~/xtv-ghidra/harness/src/main/java/com/xtv/Unpack.java`.
  ```bash
  scp _scratch/Unpack.java xtv40:~/xtv-ghidra/harness/src/main/java/com/xtv/Unpack.java
  ssh xtv40 'export PATH=~/xtv-ghidra/maven/bin:$PATH; cd ~/xtv-ghidra/harness && mvn -q compile \
    && CP="target/classes:$(cat ~/xtv-ghidra/cp.txt)" \
    && timeout 60 java -Xmx3g -Djava.library.path=~/xtv-ghidra/nativelib -cp "$CP" com.xtv.Unpack'
  ```
- The `N.l→true` wall (vtable-resolver) is documented in `GOAL1.md` — that is the shared blocker if you
  take the emulation route. **Always positive-control any new hook** (parts 14/19 gave false negatives
  from unconfirmed hooks; part 22 succeeded because it verified the hook fired).

### Reference files (repo)
- `ARCHITECTURE.md` — full stream pipeline, hosts, cookie formats, MITM capture method.
- `README.md` — plugin overview, source layout, status.
- `XuperApiClient.kt` — the request builder to diff against the real app (the `portal200001` fix site).
- `GOAL1.md` — shared emulation findings (sessions 24–27): full `RegisterNatives` method map (incl.
  `SE.sd`), the `N.l→true` wall (resolver gates now open, blocked on the `0x121b1ec0` class-table
  context walk), and the harness details. **The canonical tracker for emulation state.**
- `_scratch/p25w_output.log` — the `RegisterNatives` dump (all 4 call sites; full method table).

### Commit convention
`session 23 part NN: <summary>` with the trailers already used in the repo history.
