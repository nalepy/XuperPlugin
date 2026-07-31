# GOAL 0 — Obtain the decrypted `classes.dex` (the keystone for BOTH goals)

> **Self-contained handoff.** This is the prerequisite that unlocks `GOAL1.md` (crack XTV) and
> `GOAL2.md` (own IPTV APK) at once. Everything needed is here; deeper detail in `ARCHITECTURE.md`,
> `GOAL1.md`, `GOAL2.md`. These three GOAL*.md files are the canonical working docs.

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

## Current state
- **Route 1 (`.4` live-memory carve) is IN PROGRESS** — a subagent is running it now. Result pending;
  do not assume the outcome until it reports.
- **Route 2 (emulation)** is paused at a clean checkpoint. Session 28 passed the phase-2 struct-walk
  (`P2+0x24`/`P2+0x38` sub-object fix), N.l advanced ~17ms→~50ms, still not `true`. See `GOAL1.md`.
- No decrypted DEX in hand yet.

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

### Goal 2 targets — the portalCore request signing (beats `portal200001`)
- Search for: `portalCore`, `getAuthInfo`, `snToken`, `masnew` (the `portalCode`), `getSlbInfo`,
  `login`; and the fields `apkVersion`, `sysVersion`, `sign`, `signature`, `timestamp`, `nonce`.
- Extract the EXACT accepted request envelope: which version string it sends, whether it computes a
  `sign`/HMAC over the body, the full header/field set, and whether it calls the native
  `SE.sd (String→String)` @ `0x1203fc3d` for string-decrypt/signing (from the RegisterNatives dump).
- **Diff against `app/src/main/java/com/xuper/plugin/XuperApiClient.kt`** (our request builder) and
  patch the differing field(s). 3DES body crypto is already correct (key `2b494e53…` in
  `XuperCrypto.kt`); the gap is a value/signature, which the DEX shows. This finishes Goal 2.

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
