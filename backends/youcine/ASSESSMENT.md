# YouCine — standalone-backend assessment (go/no-go)

Date: 2026-08-01 · Branch: `youcine-assess` · APK: `_scratch/YouCine.apk` (32.8 MB, from `Xuper/brasiltv/YouCine.apk`; not committed)

**Verdict: NOT a better target than koocan/TeleLatino — same portalCore family, same portal
version-gate (`portal200001`), and packed with ijiami (fully encrypted dex: worse for static RE than
TeleLatino's Bangcle, which at least leaked its string table). One genuine positive: the portal flow is
NOT Ranger-gated (no `DoHttpSec` anywhere) — beatable like TeleLatino, not dead like XTV. koocan
remains the lead.**

---

## 1. Version / package

| | |
|---|---|
| Package | `com.world.youcinetv` ("YouCine") |
| versionName / versionCode | `1.11.1` / `11101` |
| minSdk / target | 16 / 28 (compileSdk 30) — **legacy build** |
| Real codebase | `com.interactive.brasiliptv` (BrasilTV/CineTV) — launcher `com.interactive.brasiliptv.ui.activity.WelcomeActivity`, `HomeActivity`, coolx TV framework, Tinker hotfix (shell dex: `com.interactive.brasiliptv.app.TinkerApp`; META-INF `Brasiliptv_cinetvRelease.kotlin_module`) |
| Shell | application class `s.h.e.l.l.S` (ijiami) |

aapt badging verified. TV app (leanback launcher).

## 2. Packing check — **PACKED (ijiami), fully encrypted**

- **ijiami markers all present**: `assets/ijiami.dat` (4.2 MB), `assets/IJMDal.Data` (17 KB),
  `assets/ijiami.ajm` (1.7 MB), `assets/signed.bin` (84 KB), `lib/{armeabi,armeabi-v7a}/libexec.so` +
  `libexecmain.so` (+ `_x86` variants), application class `s.h.e.l.l.S` (`s/h/e/l/l/N` family).
- `classes.dex` is a 51 KB shell: 34 class_defs = boost_multidex loader + shell stubs only.
- **Worse than TeleLatino's Bangcle shell**: TeleLatino's shell carried the original dex's string
  table in plaintext (83k strings, `portalCore` ×164) — here `ijiami.dat` is fully high-entropy
  (4 MB, no recoverable strings; header carries a key blob `6972feb0-…-efc91297a`). Nothing but the
  manifest/resources is readable statically.
- `libranger-jni.so` (Titan-Ranger) is bundled — see §4 for the deciding analysis.

## 3. API family — **SAME koocan/portalCore/DCS/DES platform; crypto cross-applies**

YouCine is a reskin of the same `com.interactive.brasiliptv` codebase that the family shares.
Evidence from the decrypted BrasilTV dex (`Workspace/Xuper/app_dex.dex`, same package/classes as
YouCine's manifest) + YouCine's own assets:

- **Domain-slot config**: YouCine `assets/domain_test.json` is byte-for-byte the same slot scheme as
  TeleLatino/koocan (`portal_main/backup`, `notice_main/backup`, `dccore_main/backup`,
  `datacollect_main/backup`, `ad_main/backup`, `epg_main/backup`, plus upgrade/market/epg4b/diamond).
- **DCS**: `dcs_main/dcs_backup/dcs_internal/dcs_realtime`, `key_dcs`, `key_dcs_all_url`,
  `dcsInternalDomain`, `Lcom/dcs/bean/*` (DomainInfo, ServiceUrl, URLInfo, V1/N1 beans, SSLJniException).
- **portalCore endpoints**: `/getSlbInfo`, `/getColumnContents` (beans `GetSlbInfoBeanResultData`,
  `FreeDataBean`), `ActiveResult`/`LoginResult`, `{agreement}://{ip}/api/adserver/{report,
  v3/get_content}` templating — same family. **`portal200001`** version-gate string present in the dex.
- **Crypto**: `encryptThreeDESECB(string, KEY)` / `decryptThreeDESECB(string, KEY)`,
  `DESede/ECB/PKCS5Padding` — same 3DES-response/request pattern as koocan/TeleLatino.
- **Proven cross-application**: the koocan DCS recipe works unchanged on YouCine identity (below).
  Note: YouCine's *own* dex is encrypted, so exact key constants must come from a memory dump of the
  running app — same requirement as TeleLatino (koocan's keys are in clear).

## 4. CRITICAL — portal flow is **NOT Ranger-gated** (the deciding factor)

`libranger-jni.so` is bundled and the Titan-Ranger SDK is integrated, but it is **media-session +
telemetry only**, exactly like TeleLatino — not the XTV killer:

- **`DoHttpSec`: 0 occurrences in the entire decrypted family dex** (XTV's portal-bound native
  identity gate is absent).
- The only app class touching `NativeJni`/`JniHandler` is `hc/m0` (Titan session manager,
  HandlerThread "handlerTitan"): `NativeJni->f(session, service_name, method, url, headers, body,
  data)` is the SDK's own `AppApi` report channel (`titan_ver`/`appApi.titan_ver`), and the other
  natives are invoked with `workPath = getDir("luna")` + `playerCallback`/`rangerCallback`
  (player-session init), `Env`/`Program` beans, and error/event beans (`mc/d -> K(ec/d, ec/e, ec/c)`) —
  i.e. playback + reporting, not request wrapping.
- The **domain/DCS manager (`b3/a` — `key_portal`, `key_dcs`, `key_epg`, `key_notice`…) has zero
  ranger references**, and the OkHttp client builder registers no ranger interceptor (only the
  efs-sdk analytics interceptor). Domain resolution and portal calls run plain.
- Consequence: **Ranger does not gate the portal flow → beatable like TeleLatino, NOT dead like XTV.**

*Provenance caveat:* this analysis is on the decrypted BrasilTV dex of the identical codebase
(YouCine's own dex is ijiami-encrypted; the manifest proves same package/classes). A memory dump of a
running YouCine would confirm 100%, but the wiring is class-level identical and `DoHttpSec` is
structurally absent.

## 5. Off-device probe — **getAddr SUCCESS + portal VERSION-GATE** (same as TeleLatino)

Script: `backends/youcine/youcine_probe.py` (koocan/TeleLatino recipe: DES/ECB/PKCS5 `dCsPLwiy`,
`{"data": hex, "len": n}` envelope).

| Step | Endpoint / host | Result |
|---|---|---|
| DCS getAddr | `POST http://espjey.ysnihrwtg.com/api/v2/dcs/getAddr`, body `{"data": DES_ECB_PKCS5(json,"dCsPLwiy"), "len":N}`, pkg `com.world.youcinetv`/v `11101` | **200 → decrypts (same key, len-strip) to `{"dcsClientUrl":"http://emowvv.dqiswip4.xyz\|http://espjey.ysnihrwtg.com\|",…,"returnCode":"0"}` — SUCCESS, no gate, NOT SN-keyed** (shared family pool) |
| Portal snToken/active/getFree | `emowvv.dqiswip4.xyz` + `espjey.ysnihrwtg.com`, DES-wrapped + plain bodies | **`{"returnCode":"portal200001","errorMessage":"版本已停止使用"}` = "version discontinued" — VERSION GATE**, uniform across endpoints |
| Portal config/get | same hosts | `returnCode:"500"` (reaches handler; not gate-shaped) |
| Notice | `nxiqj.jgrqyxupl.com` / `zxiws.tcgwhnvym.com` | 404 / timeout — this notice pool has rotated since the TeleLatino run (side endpoint, not load-bearing) |

Interpretation: identical to TeleLatino — the reachable portal pool serves a newer app generation than
v1.11.1 (a legacy targetSdk-28 build). Not a hard wall (family streams live from these platforms with
`tag=free` tokens); needs the current-gen `spkgVer` or a live install's own dcs host pool (memory dump).

## 6. Free-vs-paid signal — **free tier with free-live tabs + free VIP trial; VIP for extras**

- **Pre-provisioned accounts**: YouCine ships `assets/cloud_account.json` with **50 masked accounts**
  (14× type 0, 27× type 1, 9× type 2) — the family's shared cloud-account login.
- **Free UI in YouCine's own resources**: `fragment_freefragment`, `icon_free_live.png` /
  `icon_free_kids.png` / `icon_free_tv.png`, `dialog_freetime_over`, free-experience buttons — a free
  live lineup is a first-class tab.
- **Free VIP trial**: `"%d dias de VIP para testar"` / `pode obter %d dias VIP novamente` (earn VIP
  days again), `dialog_viptime_over`.
- Paid side is membership extras: "Por favor, torne-se um VIP primeiro", `%d dias de assinatura`
  (subscription days), `Login now` / SMS login / bound-account activation (device-activate + login).
- Bottom line: same model as koocan/TeleLatino — **device-activate / free tier unlocks live
  channels; VIP covers premium extras**. Channel-count parity still needs a live Phase-B run.

## 7. Verdict

**Rank: koocan (lead) > TeleLatino ≈ YouCine. YouCine is NOT a better standalone target.**

- Same family & crypto (DCS getAddr proven off-device with the *same* `dCsPLwiy` key; 3DES response
  pattern identical) → the koocan client flow ports verbatim once per-brand keys are extracted.
- **Not Ranger-gated** (no `DoHttpSec`, ranger = media/report only) → portal flow is beatable, the
  XTV death does not apply.
- But: **ijiami-packed with a fully encrypted dex** (worse than TeleLatino's Bangcle for static RE —
  no recoverable string table, no key constants), and the reachable portal pool answers the same
  **version-gate `portal200001`** as TeleLatino → a memory dump (live box) is required for keys +
  current host pool either way, with no advantage over TeleLatino.
- Free-tier signal is positive (free live tabs + free VIP trial + 50 shared accounts), matching the
  GOAL.md free-forever bar.

Next step if koocan stalls: dump a running YouCine box (same FakeUnitv tooling) to recover the
decrypted dex (key constants) + live dcs/portal host pool, then rerun the probe against the fresh pool
— same work as TeleLatino, same expected outcome.
