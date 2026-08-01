# GOAL 0 — Obtain the decrypted `classes.dex` (the keystone for BOTH goals)

> **⚠ CONTEXT CHANGED — read `GOAL.md` first (session 33).** The mission is now a STANDALONE app on any
> off-device-replicable backend (koocan/UniTV leads); XTV is OUT for standalone. This DEX work (✅ done)
> stays useful as reference, but XTV is no longer the target. `GOAL.md` is the north star.

> **Self-contained handoff.** This is the prerequisite that unlocks `GOAL1.md` (crack XTV) and
> `GOAL2.md` (own IPTV APK) at once. Everything needed is here; deeper detail in `ARCHITECTURE.md`,
> `GOAL1.md`, `GOAL2.md`. These three GOAL*.md files are the canonical working docs.

> **STATUS: ✅ ACHIEVED (session 28).** The decrypted DEX was carved from `.4` live memory and
> decompiled with jadx — the emulation wall was bypassed. This goal is essentially DONE; what remains is
> *using* the DEX (see the extraction results below, and `GOAL1.md` / `GOAL2.md`).

## Objective
Recover the **decrypted application DEX** (`classes.dex`, possibly multidex) of XTV
(`com.android.mgstv` v4.34.5, ijiami-packed). The real app code is encrypted inside
`assets/ijiami.dat` and only exists in cleartext at runtime after the native loader decrypts it.

## Why this is the ONE thing worth focusing on
A single artifact answers both goals:
- **Goal 1 (crack XTV):** the decrypted DEX contains the **email-registration / forced-update /
  payment-VIP gate checks** — find them, patch them, repack or custom-load.
- **Goal 2 (own APK):** the decrypted DEX contains the **portalCore request builder** — how the app
  signs/versions an *accepted* auth request (the field(s) that beat the `portal200001` version-gate).
Neither goal needs anything else first. Goal 1's entire unidbg grind (`N.l→true` → `b2b`) exists ONLY
to produce this DEX; Goal 2's `portal200001` fix is a wire diff the DEX reveals directly. **Get the DEX
once, unlock both.**

## Routes to obtain it — ranked cheapest first
1. **Live-memory carve from the rooted `.4` box (RECOMMENDED — in progress).** ijiami decrypts the DEX
   into process memory at runtime, so on the running device it's plaintext in RAM. Dump
   `/proc/<pid>/mem` for `com.android.mgstv` (root + ADB on `.4`) and carve regions starting with the
   DEX magic. Prior sessions already dumped ~52 MB from `.4` and carved `libexec.so` this way — same
   method, different target. **No unidbg, no `N.l→true`, no cert-unpin.**
2. **Emulation `b2b` route (HARD — see `GOAL1.md`).** Drive the unidbg harness until `N.l→true`, then
   call `N.b2b(ijiami.dat)` which decrypts and writes `/tmp/apkx/app_decrypted.dex`. Currently blocked
   on the packer's C++-object init walk (whack-a-mole, session 28 advanced it but N.l still returns
   false). This is the fallback if route 1 fails.
3. **Other on-device dump tooling** (a DEX-dumper that hooks `DexFile`/`OpenMemory`/`defineClass`),
   if a non-Frida method is available — ijiami's anti-Frida ptrace-block has defeated Frida so far.

## How to recognize / verify the DEX
- **Magic bytes:** `64 65 78 0a 30 33 35 00` (`dex\n035\0`); the version digits may be `035`/`037`/
  `038`/`039`. Grep the memory dump for `dex\n0`.
- After the magic: a valid DEX header (checksum, SHA-1 signature, `file_size`, `header_size=0x70`,
  `endian_tag=0x12345678`). Use `header.file_size` to know how many bytes to carve.
- Multidex: there may be several DEX blobs — carve **every** `dex\n0` hit, not just the first.
- Sanity-check with `baksmali`/`jadx` — it should decompile to real class names (not garbage).

## Current state — *** ACHIEVED (session 28): DEX CARVED via route 1 ***
- **Route 1 (`.4` live-memory carve) SUCCEEDED.** The decrypted DEX was carved from
  `com.android.mgstv` process memory on `.4` and decompiled with jadx. This **bypassed the emulation
  wall entirely** — no `N.l→true` needed.
  - Method that worked: `dd /proc/<pid>/mem` over the large `[anon:dalvik-DEX data]` r-- regions (found
    via the maps), grep `dex\n035`. Memory-dumped DEX have a **stale adler32** — jadx loads 0 classes
    until you **recompute the adler32 checksum + SHA-1 signature** in the header; after that it
    decompiles cleanly. Carved **3 dex** (main ~9.07 MB + a 12.1 MB multidex holding `p2`/`r2`).
  - **✅ The carved DEX is PERSISTED in the repo:** `_session/xtv_dex/` —
    `app_classes_fixed.dex` (~9 MB, checksum-fixed, jadx-ready), `d2_classes.dex` (~12 MB multidex),
    `dex_strings.txt`, `validate.py` (the checksum-fix script), `portal.pcap`, `maps3.txt`, and a
    `README.md` with provenance + carve steps. The 59 MB jadx-decompiled source was NOT committed
    (derivable) — regenerate with `jadx -d out _session/xtv_dex/app_classes_fixed.dex`.
- **Route 2 (emulation)** is no longer required to obtain the DEX — demoted to fallback. It's paused at
  a clean checkpoint (session 28 passed the phase-2 struct-walk; N.l still not `true`). See `GOAL1.md`.

## Once the DEX is in hand — extraction plan (run BOTH; this is the payoff)
Decompile first: `jadx -d out app_decrypted.dex` (or `baksmali d` for smali). Then:

### Goal 1 targets — the gate checks to patch
- Search decompiled source / strings for: `register`, `email`, `bindEmail`, `login` enforcement;
  `forceUpdate`, `versionCode`, `mustUpdate`, `upgrade`; `vip`, `isVip`, `isPay`, `pay`, `member`,
  `expire`, `trial`, `paywall`.
- Map each gate to a method returning a boolean/branch; plan the minimal patch (force the "unlocked"
  branch — return `true`/`false`/no-op) in smali.
- Note: shipping requires **repack under ijiami** (resists it) or a **custom loader** — see `GOAL1.md`
  blocker 2. The DEX is necessary but not sufficient for Goal 1's final deliverable.

### Goal 2 targets — DONE (session 28): pipeline found, blocker MOVED (see `GOAL2.md`)
The DEX revealed the whole portalCore request pipeline from the app's own code:
- Retrofit `jd.a`; interceptor `ld.a` adds 4 headers (`apk`=appId, `apkVer`, `spkgVer`, `Content-Type`)
  with **NO signature/nonce/timestamp**; interceptor `ld.b` merges the device fields then 3DES-encrypts.
  Ground-truthed `appId="com.android.msandroid"`, `apkVer=43405`.
- **CORRECTION (session 30):** the session-28 DEX reading of the *body* was WRONG. The app's own
  request log (heap `service_name:"portal"` DoHttpSec record) shows the real body uses **`b29`
  lowercase**, **`contentType` INSIDE the body**, and **no `lang`/`type`** in the common fields.
  The plugin envelope was corrected accordingly (`XuperApiClient.kt`, session 30).
- **`portal200001` is an ORIGIN-LEVEL native-signed-TOKEN gate (refined session 31; was mis-framed as
  TLS-identity in session 30).** Session 31 proved: the response is generated at the **origin**
  (Envoy/Google behind Cloudflare — `Via: 1.1 google`, `X-Envoy-Upstream-Service-Time`, `cfOrigin;dur=200`),
  NOT at the CF edge, so it is **not** a JA3/TLS-fingerprint block (the Go `utls` probe already matches the
  app's exact `0xcca9`-in-TLS1.2 handshake and still gets gated). Any `apkVersion` value (up to 99999) is
  **ignored**. The gate keys on the encrypted **`b29`/`reserve1`** body tokens, which are minted by the
  **Titan Ranger native layer** (`NativeJni`/`DoHttpSec`, `SE.sd @ 0x1203fc3d`) under a native key — they
  do NOT decrypt with the recovered body 3DES key. Beat it only by minting fresh native tokens (hook
  `qd.a.a.k()` inputs on the live app, or reverse the native crypto) or by sidestepping portalCore. Full
  proof: `GOAL2.md` Session 31.
- **UPDATE (session 33): the EOL claim is RETRACTED — the app works daily.** The owner confirmed XTV
  4.34.5 streams fine and is logged in on `.4`; the session-31 "EOL" read was wrong (per-channel/EPG
  telemetry failures, and the app streams via the dcs tier, not the portalCore `getLiveData` we cloned).
  **True cause of `portal200001` for our clone:** XTV portalCore binds to the app's **native Titan-Ranger
  DoHttpSec connection identity** — un-replicable off-device (confirmed by this project + the FakeUniTV
  sibling agent, who tested fresh userToken + cracked b29/reserve1 and still got `portal200001`).
  **b29/reserve1 crypto is now cracked** (props key `base64decode("2b494e53756c`**`77`**`4c…")`,
  b29=enc(SN)/reserve1=enc(userId) — `_session/fakeunitv_intel/mint_tokens.py`) but still insufficient.
  **New standalone hope: koocan/UniTV** — a *different, non-ijiami* backend whose portalCore **DOES accept
  off-device clients** (`getAddr` → `returnCode:"0"` this session). Detail: `GOAL2.md` Session 33 + the
  fetched intel in `_session/fakeunitv_intel/`.

### Goal 1 targets — still pending (decompiled sources now local)
- The gate-check search (email-reg / forced-update / VIP-payment) is NOT done yet. The decompiled
  sources were regenerated locally (session 30): `_scratch/jadx_xtv_main/` (main DEX) and
  `_scratch/jadx_xtv_d2/` (multidex) — grep targets for `portal200001`, `forceUpdate`, `isVip`, etc.
  See `GOAL1.md`.

## Kill-criterion
Route 1 (`.4` carve) should take one focused session to know if the DEX is recoverable from memory.
If it carves out → decompile → run BOTH extraction plans → Goal 1 and Goal 2 both unblock. If the DEX
can't be recovered from memory AND emulation `N.l→true` stays stuck AND no on-device dumper works, the
project depends on an ijiami breakthrough — escalate.

---

## Handoff / ops (verified working session 23)

### Machines
| Host | Addr | Role | Access |
|------|------|------|--------|
| Win11 `.5` | local | Orchestration (this box) | git-bash, `ssh`, `sshpass`, `scp` present |
| Ubuntu `.40` | `192.168.100.40` | unidbg emulation host + plugin build | `ssh xtv40` (key-based, see below) |
| TV box `.4` | `192.168.100.4:5555` | **rooted device — live DEX carve target** | `adb connect 192.168.100.4:5555` |
| Android `.37` | `192.168.100.37:2222` | rooted KitKat (SSH) | `ssh root@…:2222` (paramiko pinned `2.11.0`) |

### `.4` live-memory DEX carve (route 1)
```bash
adb connect 192.168.100.4:5555
PID=$(adb -s 192.168.100.4:5555 shell su -c 'pidof com.android.mgstv')
adb -s 192.168.100.4:5555 shell su -c "cat /proc/$PID/maps"          # find RW/anon + large regions
# for each candidate region [start-end], dump and carve:
adb -s 192.168.100.4:5555 shell su -c "dd if=/proc/$PID/mem bs=4096 skip=<start/4096> count=<pages> 2>/dev/null" > region.bin
# then offline: grep -aboP 'dex\n0' region.bin  -> for each hit, read header.file_size, carve that many bytes
```

### `.40` access — IMPORTANT
- **SSH is key-based now.** Alias `xtv40` in `~/.ssh/config`, key `~/.ssh/id_xtv40`, user `nestor`.
  The old password `ian20jesus` is **dead**.
- `/tmp` wiped on reboot — rebuild the emulation asset tree from the APK (survives in `_assets/`):
  `ssh xtv40 'mkdir -p /tmp/apkx && cd /tmp/apkx && unzip -oq ~/xtv-ghidra/harness/_assets/live_base.apk'`
- `.40` drops intermittently; retry.

### Reference files (repo)
- `GOAL1.md` — crack-XTV handoff; the emulation `b2b` route to the DEX (route 2) + patch/repack plan.
- `GOAL2.md` — own-APK handoff; where the DEX's request-signing gets diffed into `XuperApiClient.kt`.
- `ARCHITECTURE.md` — stream pipeline, hosts, cookies, MITM method.
- `_scratch/p25w_output.log` — RegisterNatives dump (native method map incl. `SE.sd @ 0x1203fc3d`).

### Commit convention
`<type>: <summary>` with the trailers already used in repo history.
