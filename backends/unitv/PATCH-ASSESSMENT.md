# UniTV (fake, `com.integration.unitviptv` v2.14.8) — patch+repack assessment

> Worker: `unitv-patch-assess` branch. Date: 2026-08-01 (session 33).
> Scope: go/no-go on "patch the app itself and repack" (HANDOFF.md goal option 2).
> Boxes: `.4` (Android 14 TV, adb root) for runtime; `.40` (ubuntu, java 17) for build.
> Second pass (this worker's constraints): runtime verification on `.97` (Android 7.1.2, rooted, V88).
> Artifacts: this file + `patch_smali.py` (minimal 2-no-op patch) + `patch_unitv.sh` (full
> build/sign pipeline) + `anti_tamper_smali.patch` (clean diff). APKs / decompile stay untracked.

## TL;DR verdict

**Patched-standalone-UniTV is NOT a viable deliverable — but NOT because of the patch.**

- The patch route is **mechanically trivial and fully proven**: no ijiami, no Bangcle, no signature/
  integrity wall. Two smali no-ops → rebuilt + resigned APK **installs and launches on `.4`**, passes the
  splash self-kill, the forced-update dialog, and the bind/login dialog, and reaches the main UI tabs.
- The kill switch is **the backend, not the app**: this build's own portal chain is dead
  (`getAddr` → 404 CloudFront / 400 origin; backup host NXDOMAIN). The only live koocan host for this
  package is the **market/update server**, which answers 200 and actively pushes the **real UNITV 4.11.0**
  (`Unitv_4.11.0_…_release_jiagu.apk` — the ijiami-packed build) as a forced update. VOD and TV tabs both
  spin forever — there is no stream to unlock, client-side or otherwise.

So: the "no packer, easy repack" premise is correct, but the target app is a **retired build whose
backend has been pointed at the real (packed) successor**. This route dies on the same wall as XTV —
just on the server side this time.

---

## 1. Does the fake UniTV actually stream today? — NO (two independent blockers)

### 1a. Stock APK self-kills on box `.4` before any UI — and on EVERY device
Stock `UniTV_fake_2.14.8_APKPure.apk` installed on `.4` (Android 14 TV, 1280x720, uimode=TELEVISION):
process starts, then ~2 s later dies with `SIG 9` (`Process.killProcess`), no Java exception, restart loop.

Two independent triggers, both routed through the same self-kill bomb (`SplashAty$a.a()` →
`util/c.a(ctx, pkg)` → `killProcess(myPid())` + `forceStopPackage` + `System.exit(0)`):

1. **Signature check fires universally** — the hardcoded expected signature
   `R.string.qm = 8ddb342f2da5408402d7568af21e29f9` does **not** match the actual APKPure cert
   MD5 `647a88eff93b82b0e414fde8a05826e5` (verified via apksigner on the stock APK). APKPure
   re-signed the build, so the stock APK fails its OWN signature gate on any device, TV or phone.
2. **TV-device check fires on box `.4` additionally** — `utils/l.java` detects uimode=TELEVISION /
   HDMI audio / hdmi state, so even a properly-signed build would self-kill on a TV box.

(The sibling worker `patch_unitv.sh` reached the same conclusion independently: "The stock APKPure
APK already fails its OWN signature check (APKPure re-signed it)… so it suicides ~4s after launch
on EVERY device.")

### 1b. Even with the kill patched out, the portal chain is dead
Patched build boots to main tabs (VOD / TV / Perfil), but both VOD and TV show the infinite loading
spinner. Traffic capture + direct probes:

| Host (from `res/values/strings.xml`) | Role | State today |
|---|---|---|
| `mobile.solz1lf.com` (portal_main) | `POST /api/v2/dcs/getAddr` | **404 page not found** (CloudFront, box) / 400 (origin, .40) |
| `mbfel.lgesetd1l.com` (portal_backup) | getAddr fallback | **NXDOMAIN / connection refused** |
| `akz1.pudisdz.com` (upgrade_main) | `GET /MarketServer/update` | **200 OK** → pushes real 4.11.0 (jiagu) |
| `notice.terdlfw.com:8000` (notice_main) | notice fetch | **port 8000 dead** (timeout); 80/443 fine |
| `mobiletv.ogy1lfw.com` / `mobiletv.terdlfw.com` (ad) | `/ADServer/v1.0/get_config` | 404 |
| `cool.kfsxdz.com` (datacollect) | playError | reachable (posts fire) |
| `pre.itgfgdz.com` (epg) / `dc3.tesgdz.com` (dccore) | — | port 80 open (unused at boot) |

The app's own getAddr POST (verbatim request from pcap: DES(`dCsPLwiy`) body, `apk`/`apkVer` headers)
returns **`404 page not found` / `X-Cache: Error from cloudfront`**. The endpoint this build needs no
longer exists upstream. `DomainCache.xml` stays empty → no portal host to fall back to.

### 1c. The update server is actively retiring this build
`checkUpdate` for `com.integration.unitviptv@21408` returns `forceUpdate`-capable `UpdateInfo`:
`versionName=4.11.0, versionCode=41100`, apk `http://drcf.kzsdmfcwe.com/marketdatas/apk/Unitv_4.11.0_9cc2347f_20230907_934_release_jiagu.apk`.
Note **`jiagu`** in the filename — the operator's live build is the packed one. This 2.14.8 APKPure build
is a deprecated/retired sibling; its portal endpoints have been taken down.

---

## 2. Gate-check locations in the decompiled source

All gates are plain Java (no native), all in `unitv_src/sources/`. Obfuscated names from jadx.

### 2a. Splash self-kill gate — `com/mobile/brasiltv/activity/SplashAty.java` (`SplashAty$a.accept`, line 66)
Single gate covering 4 checks, any true → kill:
```
if ( (!TextUtils.isEmpty(util.c.e(ctx)) && !sig.equals(R.string.qm))   // signature MD5 mismatch
  || util.c.c()        // "ps" scan for frida-server
  || util.c.b(ctx)     // xposed / substrate / hook app present
  || util.c.d(ctx) )   // debugger connected / emulator / qemu kernel
    → util.c.a(ctx, pkg)   // self-kill bomb
```
- `util/c.java` = the security class: `e(ctx)` computes own-signature MD5; `a()` checks
  `getprop ro.kernel.qemu`; `a(ctx)` checks emulator (deviceId/build model); `b()` stack-trace xposed;
  `c()` frida; `c(ctx)` installed hook apps; `d()` disables Xposed hooks.
- **Killer:** `util/c.a(Context, String)` → `Process.killProcess(myPid())` + reflective
  `forceStopPackage(pkg)` + `System.exit(0)`.

### 2b. TV-device gate — same `SplashAty$a` (line 73)
```
if (prefs "is_tv_devices" || utils/l.a(ctx))    // uimode==TELEVISION | HDMI audio | /sys/.../hdmi/state
    prefs "is_tv_devices"=true; → util.c.a(ctx, pkg)   // self-kill
```
`utils/l.java` = pure TV detection (`UiModeManager.getCurrentModeType()==4`, HDMI audio devices, hdmi state
file). On `.4` both gates fire (sig mismatch + TV), but the sig mismatch alone kills on any device.

### 2c. Forced-update gate — `com/mobile/brasiltv/f/b/u.java` `u$e.a(UpdateBean)` (line 263)
On any `UpdateInfo` from `checkUpdate`, unconditionally shows `HomeUpgradeDialog`. If
`UpdateBean.getForceUpdate()==1` the dialog hides its cancel button and `onBackPressed()` →
`Process.killProcess(myPid())` — a hard server-driven wall (and today the server IS pushing forceUpdate).

### 2d. Bind/login (forced email/registration) — server-driven dialog over MainAty
After the gates above, a dismissible dialog appears: "Consejos — Primera login o vincular tu email/numero
de telefono, luego disfrutar" with **Vincular** (bind, new users) / **Login** (old users). Text is
server-fetched (not in static strings). **Dismissible via BACK** — it does not block; the app proceeds to
the tabs. (Per the task: no account needed — step 1's "login required" does not trigger; no `NEEDS.md`.)

### 2e. VIP/paywall — present but client-soft, moot anyway
- `bean/MemberInfo.java`: `isLogin()`, `isMemberUser()` (vipTime/svipTime>0), `getLoginType()` →
  "tourist" / "vip" / "svip"; `saveUserInfo` maps `LoginInfoData` fields (vipTime, svipExpiredTime,
  payFlag, portalCodeList …).
- `view/NeedVipDialog.java`, `view/dialog/ServiceExpirationTipDialog.java`, `mine/activity/VIPMemberActivity.java`
  exist; the string `login_and_bind_then_watch` is referenced only in `VodPlayerController`.
- No hard client-side live-channel paywall found — the live unlock is server-decided (returnCode/portal),
  which is exactly why a dead backend means no channels at all. (See the sibling `koocan-auth` branch for
  the off-device chain; note its `getAddr` proof in `backends/koocan/README.md` now appears stale — same
  endpoint, 404 today.)

---

## 3. Patch + repack feasibility — PROVEN

### 3a. What we patched (2 no-ops, both verified working)
1. **`mobile/com/requestframe/util/c.smali` → `a(Context,String)` body = `return-void`**
   Neutralizes ALL five kill paths (signature / frida / hook / debug / TV-device) at once — the
   signature gate fires immediately on the stock APK (APKPure re-sign) and after any re-sign, so this
   single no-op is what actually lets the app boot.
2. **`com/mobile/brasiltv/f/b/u$e.smali` → `a(UpdateBean)` body = `return-void`**
   Kills the forced-update dialog entirely (never shown, event not posted).

Patch scripts in this folder:
- [`patch_smali.py`](patch_smali.py) — minimal, exactly the two no-ops above (this worker's, verified).
- [`patch_unitv.sh`](patch_unitv.sh) — sibling worker's fuller script (also no-ops `b/c/d(Context)`
  gates and `e()` signature getter, patches `HomeUpgradeDialog.onBackPressed`); independent, same goal.

### 3b. Build + sign + install chain (all on `.40`, verified)
```
java -jar apktool.jar d -f -o unitv_smali UniTV_fake_2.14.8_APKPure.apk
python3 patch_smali.py unitv_smali
java -jar apktool.jar b unitv_smali -o unitv_patched.apk
zipalign -f 4 unitv_patched.apk unitv_aligned.apk
apksigner sign --ks patch.ks --out unitv_signed.apk unitv_aligned.apk
adb install -r unitv_signed.apk
```
- apktool 2.11.0, build-tools 34 (zipalign/apksigner), fresh RSA-2048 keystore.
- **Re-signed APK installs clean on `.4` (Android 14)** and launches. No Bangcle, no libexec/so-integrity,
  no signature check beyond the client-side MD5 compare (now a no-op). The "no packer to defeat" premise
  holds completely.
- On-device proof: patched build → splash OK → intro pages → main tabs `VOD/TV/Perfil` render; update
  dialog gone (server still returns forceUpdate data — handler no-ops); bind dialog dismisses with BACK.

### 3c. Expected UX after patch (if backend were alive)
Splash self-kill disabled, forced update disabled, bind/login dismissible → app reaches tabs, auto-logs
tourist (SN → activate → getAuthInfo per `f/b/u.java`), and would play whatever the portal returns. The
patch itself is deliverable-grade; only the upstream 404 blocks streaming.

---

## 4. Does a patched build stream all channels free? — UNVERIFIABLE / BLOCKED

No. The patched build streams **nothing** because the portal chain this build points at is dead:
`getAddr` 404 → no portal host → no `getAuthInfo`/`getSlbInfo` → no channel list → no m3u8. This is not a
gate that a client patch can open — the operator has retired the 2.14.8 endpoints and is serving the real
4.11.0 (`jiagu` = packed) as the forced update.

---

## 4b. Independent verification — box `.97` (this run, second worker pass)

Re-verified end-to-end on the box the task assigned to this worker (`.97`, V88, Android 7.1.2, rooted;
the real packed `com.global.unitviptv` running alongside). All conclusions above reproduce exactly.

**Unpatched stock APK on `.97`:** installs, launches, then dies ~4 s in with a self-SIGKILL and
restarts forever (`Process: Sending signal. PID: <pid> SIG: 9` in logcat; `Process
com.integration.unitviptv (pid X) has died` every ~4-6 s). No Java exception — the `util/c.a()`
kill bomb. Same signature-gate root cause (cert md5 `647a88ef…` vs `R.string.qm` `8ddb342f…`).

**Patched build on `.97` (built with `patch_unitv.sh`, signed with a fresh RSA-2048 keystore):**
installs over a clean uninstall, launches, survives 45 s+ across multiple force-stop/relaunch cycles
(no `has died`, no SIGKILL), passes intro → `MainAty` (bottom tabs VOD / Live / Profile). The
server-driven **"Version Upgrade" dialog still appears** (server still pushes 4.11.0) and BACK now
dismisses it instead of killing the process — the `HomeUpgradeDialog.onBackPressed` patch works. The
Live tab renders no channel list.

**Wire evidence (root tcpdump on `.97`, 30 s window):** the app resolves hosts via **DoH to Google**
(`142.251.129.66:443` — no port-53 queries; the router DNS is dead, matching the sibling session
notes) and then makes exactly four HTTP calls:

| Request (from pcap) | Host (resolved via DoH) | Result |
|---|---|---|
| `POST /api/v2/dcs/getAddr` | `mobile.solz1lf.com` → 52.85.78.22 (CloudFront) | **404** `X-Cache: Error from cloudfront` |
| `GET /MarketServer/update?…=com.integration.unitviptv,21408` | `akz1.pudisdz.com` → 172.67.131.197 | **200** (UpdateInfo → 4.11.0 `jiagu` apk) |
| `POST /ADServer/v1.0/get_config` | `mobiletv.ogy1lfw.com`/`terdlfw.com` (Cloudflare) | **404** |
| `POST /api/apk/playError` | `cool.kfsxdz.com` → 172.67.155.75 | sent (no stream impact) |

SYN-ACK map: all Cloudflare + AWS + Chinese-analytics hosts answer; **two SYN timeouts** —
`cool.nbgfbr.com` (23.89.152.2, the backup stream-CDN host — dead) and `amdcopen.m.taobao.com`
(taobao push, irrelevant). After the getAddr 404 the app makes **no further portal calls**
(no `active`/`getAuthInfo`/`getColumnContents`/`startPlayLive` on the wire) — the chain is
bootstrap-dead, exactly as section 1b describes.

**PC-side probe matrix (same endpoint set, direct from the PC):**

| Endpoint | State |
|---|---|
| `mobile.solz1lf.com/api/v2/dcs/getAddr` (main) | **404** (app body) / **500** (probe body) |
| `mbfel.lgesetd1l.com` (backup) | NXDOMAIN |
| `dc3.tesgdz.com` / `dc3.hgsesd.com` (dccore) | 521 (origin down) |
| `cool.kfsxdz.com` (datacollect) | timeout from PC |
| `mobiletv.ogy1lfw.com` (ad) | 404 |
| `pre.itgfgdz.com` (epg) | 403 |
| `mobile.solz1lf.com/v1/aws/vpkg?asfast=true` (the **current** native DCS path) | **400** (endpoint alive — rejects a malformed body, i.e. the operator's live protocol is the native titan DCS, not this build's legacy Java path) |

Conclusion unchanged and now double-proven: the patch route is mechanically sound; the 2.14.8 build is
orphaned at the protocol level. A clean diff of the applied smali changes is committed as
[`anti_tamper_smali.patch`](anti_tamper_smali.patch).

---

## 5. Verdict

| Question | Answer |
|---|---|
| Streams today? | **No** — stock self-kills on every device (signature gate, APKPure re-sign); patched build boots but portal `getAddr` = 404 |
| Gates mapped? | Yes — splash self-kill (sig/frida/hook/debug/TV), forced update, bind/login (dismissible), VIP UI |
| Patch+repack feasible? | **Yes, proven** — 2 smali no-ops, apktool rebuild, re-sign, installs+launches on `.4` |
| All channels free? | **No** — backend dead; nothing to unlock |
| **Viable mission deliverable?** | **NO** — the app is retired; live backend points to the packed real build |

**Recommendation:** do not invest further in the 2.14.8 patch route. The unpacked-app advantage is real
but moot because the build is orphaned. Forward options:
1. **`koocan-auth` branch** (sibling): re-validate `getAddr`/portal hosts — the "returnCode 0" proof in
   `backends/koocan/README.md` predates the 404; if the portal moved hosts, the off-device chain may still
   be recoverable (crypto fully in clear).
2. **Real UniTV 4.11.0** (what the update server pushes): packed (`jiagu`) → back to the ijiami wall, same
   as XTV — low value.
3. **TeleLatino assess** (`telelatino-assess` branch): per GOAL.md candidate #2, unknown packer/API — worth
   the quick check since koocan's portal appears to be dissolving.

---

## Appendix — exact smali diffs

### Patch 1: `smali_classes2/mobile/com/requestframe/util/c.smali`
```diff
 .method public final a(Landroid/content/Context;Ljava/lang/String;)V
-    .locals 6
-    const-string v0, "context"
-    invoke-static {p1, v0}, Ld/f/b/i;->b(Ljava/lang/Object;Ljava/lang/String;)V
-    const-string v0, "pkgName"
-    invoke-static {p2, v0}, Ld/f/b/i;->b(Ljava/lang/Object;Ljava/lang/String;)V
-    invoke-static {}, Landroid/os/Process;->myPid()I
-    move-result v0
-    invoke-static {v0}, Landroid/os/Process;->killProcess(I)V
-    ... forceStopPackage reflection ... System.exit(0)
+    .locals 0
+    return-void
 .end method
```

### Patch 2: `smali_classes2/com/mobile/brasiltv/f/b/u$e.smali`
```diff
 .method public a(Lcom/mobile/bean/UpdateBean;)V
-    .locals 2
-    new-instance v0, Lcom/mobile/brasiltv/view/HomeUpgradeDialog;
-    ... show dialog ... post HasNewUpdateEvent(true)
+    .locals 0
+    return-void
 .end method
```

Rebuild artifact (`unitv_signed.apk`, ~30 MB, signed with a throwaway key) lives on `.40`
(`~/apktool/`) and on this worker's box (`.97`) as `UniTV_patched_2.14.8.apk` (built by
`patch_unitv.sh`, `~/Workspace/FakeUnitv/unitv_patch_work/`); kept untracked per constraints.

The full applied diff is also committed as `anti_tamper_smali.patch` (regenerated from a clean
apktool re-decode; `patch -p1`-able against the decoded tree).
