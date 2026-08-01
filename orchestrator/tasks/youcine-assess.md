# Worker task: YouCine — standalone-backend go/no-go assessment

Branch `youcine-assess` off `master`, isolated worktree. Commit small, push, open a PR. Do NOT touch
`master`. Read `GOAL.md` first. YouCine is named in the same `portal200001` version-gate family as
XTV/BrasilTV/TeleLatino/koocan — assess whether it's a better standalone target than TeleLatino/koocan.

## Input
APK already present: `Xuper/brasiltv/YouCine.apk` (relative to the Workspace root — copy it into your
worktree scratch, do NOT commit it). Package name via `aapt dump badging`.

## Answer these (mirror the TeleLatino assessment)
1. **Version / package** (versionName/Code, min/target SDK).
2. **Packing:** ijiami (`ijiami.dat`/`libexec.so`/`s/h/e/l/l/N`)? Bangcle/SecNeo (`libDexHelper.so`/
   `com.secneo.apkwrapper`)? or **unpacked** (clean jadx)? Unpacked = best (patch+repack possible).
3. **API family:** same portalCore/DCS/DES design (grep strings for `portalCore`, `getAddr`, `getSlbInfo`,
   `snToken`, DES/3DES keys, `koocan`)? Does the koocan/TeleLatino crypto cross-apply?
4. **CRITICAL — is the portal flow Ranger-gated?** Look for `libranger-jni.so`/`DoHttpSec` and whether the
   portalCore flow actually goes through it (the XTV/koocan killer) or NOT (like TeleLatino → beatable).
5. **Off-device probe:** if you can find its `getAddr` endpoint + crypto, do ONE off-device request; report
   `returnCode:0` (host resolution works) vs `portal200001`/error. (getAddr may be SN-keyed — note if so.)
6. **Free channels?** any sign the full live lineup is free / device-activate vs paid VIP.

## Constraints
- Off-device/static analysis; box `.4` if you need a device, `.40` ubuntu available, avoid `.97`.
- MAY spawn kimi subagents. Do NOT commit the APK or large decompiled trees (gitignore).

## Deliverable
`backends/youcine/ASSESSMENT.md`: version/package, packing, API-family match, **Ranger-gated on portal? (the
deciding factor)**, off-device probe result, free-vs-paid signal, and a one-line verdict — rank it vs
koocan/TeleLatino for a standalone build. Then stop.
