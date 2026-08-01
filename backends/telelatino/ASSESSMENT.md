# TeleLatino — standalone-backend assessment (go/no-go)

Date: 2026-08-01 · Branch: `telelatino-assess` · APK: `_session/TeleLatino.apk` (35 MB, 2076 entries)

**Verdict: worth pursuing as a SECOND pick, but NOT easier than koocan.** Same API family, and the
DCS getAddr + request crypto cross-apply 1:1 (same DES key `dCsPLwiy`), but the app is Bangcle-hardened
(no clean jadx), and the portalCore step currently answers a **version-gate** (`portal200001`) from the
reachable domain pool. koocan remains the lead.

---

## 1. Version / package

| | |
|---|---|
| Package | `com.global.latinotv` ("Tele Latino") |
| versionName | `5.46.8` |
| versionCode | `54608` |
| Build | 2026-07-09 23:55 (zip timestamps) |
| minSdk / target | 19 / 33 (compileSdk 34, Android 14) |
| Source | already present in `_session/TeleLatino.apk` (captured earlier); aapt badging verified |

Launcher entry is `com.secneo.apkwrapper.AW`; real UI classes: `com.main.ui.activity.HomeActivity`,
`com.vod.ui.activity.VodDetailsActivity`, `com.mine.ui.activity.PurchaseActivity` (per therouter routeMap).

## 2. Packing check — **PACKED (Bangcle/SecNeo), NOT ijiami**

- No `assets/ijiami.dat`, no `libexec.so`, no `s/h/e/l/l/N` → **not ijiami**.
- Bangcle (`SecNeo`) markers: `lib/arm64-v8a/libDexHelper.so` + `libdexjni.so` (+ armeabi-v7a copies),
  `com.secneo.apkwrapper.{AW,AP,CP,H,a,b}` classes, `assets/meta-data/{manifest.mf,rsa.sig,rsa.pub}`.
- `classes.dex` (20 MB) is a **shell**: dexdump shows only the 7 `secneo` classes
  (`class_defs_size=7`), but the blob carries the original dex's **string_data in plaintext**
  (83,938 strings recoverable; `portalCore` ×164). Code/method bodies are not recoverable statically.
- Consequence: jadx gives only the shell (8 stub files). Static RE is limited to string-mining unless a
  memory dump is taken (the sibling FakeUnitv/koocan workflow already has the tooling for this).
- `libranger-jni.so` (Titan-Ranger) is bundled, but only media/report beans
  (`com/titan/ranger/bean/{Media,Program,Service,report/*}`) are referenced — **no DoHttpSec gating**
  on the portal flow (the 3 `DoHttpSec` hits are stray strings in the language list). XTV's
  Ranger-bound portalCore death does NOT apply here.

## 3. API family — **SAME portalCore/DCS/DES design as XTV/koocan; crypto cross-applies**

TeleLatino is a reskin of the same koocan platform (literal `koocan.com` string in the dex). All the
familiar machinery is present in the plaintext string table:

- **DCS getAddr**: `DcsGetAddrBean(sn=`, `Lcore/request/bean/DcsGetAddrBean`, `Lcom/dcs/bean/OtherJniException`,
  response fields `dcsClientUrl`, `dcsClientUrlAlias`, `domain_DES`, `domain_is_security`, `desHost`,
  `content://com.assistant.voice.provider/dcsAddr` cache provider. Same `/api/v2/dcs/getAddr` path as koocan.
- **Domain-slot config**: `assets/domain_test.json` — `portal_main/backup`, `notice_main/backup`,
  `dccore_main/backup`, `datacollect_main/backup`, `ad_main/backup`, `epg_main/backup`… (same slot scheme
  as koocan's host lists; real values are filled per-install).
- **portalCore endpoints** (templated `{agreement}://{ip}/api/portalCore/...`):
  `v3/snToken`, `v8/active`, `v8/login`, `terminalAuth`, `config/get`, `getHome`, `v2/getFree`,
  `register`, `pwdCheck`, `qr/token`, `v3/getColumnContents`, `v9/getAuthInfo`, `v14|v15/getSlbInfo`,
  `v7/getLiveData`, epg `v2/getLineUps` etc. (newer version numbers than koocan's v3/v5, same family).
- **Notice**: identical `http://%s/notice/api/get_notice?pkg=%s&v=%s&sn=%s&userId=%s&language=` template
  seen live in the XTV box captures.
- **Crypto**: `DESede/ECB/PKCS5Padding` + `encryptThreeDESECB/decryptThreeDESECB(string, KEY)`,
  `domain|DES` domain encryption (24-byte blobs sharing a 6-byte ciphertext suffix = common plaintext tail
  like `.com`+pad), plus 5 UUID-shaped strings (`0e5e9c33-…`, `20799a27-…`, `4c087185-…`, `629a824d-…`,
  `b700bce0-…`) — the same "UUID → custom-b64 → 24-byte 3DES key" response-key pattern as koocan's
  `b940e017-…`/`c6768bbe-…`. Exact key constants are in an obfuscated region (string clusters like
  `\AoaTAka`, `\pa*Tpe*`, `&@eT0f!8`, `b972E8a5A4e0e8Ff`), so a memory dump is needed to pin them down.

**Proven cross-application**: the koocan DCS recipe works unchanged (below).

## 4. Off-device probe — **getAddr SUCCESS + portal VERSION-GATE**

Script: `backends/telelatino/telelatino_probe.py` (reproduces all of the below).

| Step | Endpoint / host | Result |
|---|---|---|
| DCS getAddr | `POST http://espjey.ysnihrwtg.com/api/v2/dcs/getAddr` (also `sxowvd.jzvqwcyor.com`) body `{"data": DES_ECB_PKCS5(json,"dCsPLwiy"), "len":N}` | **200 `{"len":166,"data":…}` → decrypts (same `dCsPLwiy` key, len-strip) to `{"dcsClientUrl":"http://emowvv.dqiswip4.xyz\|http://espjey.ysnihrwtg.com\|","dcsClientUrlAlias":"BUZISKCONJTL\|WKXFYQAMPGDI\|","errorMessage":"success!","returnCode":"0"}`** — **SUCCESS, no gate** |
| Portal snToken | `POST http://emowvv.dqiswip4.xyz/api/portalCore/v3/snToken` (+`/snToken`, `v2/getFree`, `config/get`, `getHome` on emowvv/espjey/dfcsq/ioermd) | **`{"returnCode":"portal200001","errorMessage":"版本已停止使用"}`** = "version discontinued" — **VERSION GATE**. Uniform across every pkg (`com.global.latinotv`, `com.android.mgstv`, `com.android.msandroid`, `com.mobile.brasiltv`, `com.integration.unitviptv`), version (54608, 50145656, 43405, 5.46.8, 21408), spkgVer (with/without/guessed-current), and body style (plain `{}`, DES-wrapped with 12 key candidates, raw hex) tried |
| Notice | `GET http://nxiqj.jgrqyxupl.com/notice/api/get_notice?pkg=com.global.latinotv&v=54608&sn=…&userId=…&language=es` | **200 `{"status":0,"info":"","package":"com.global.latinotv","inner":[],…}`** — accepts TeleLatino identity, **no gate** (inner empty = no notices for this sn) |

Interpretation of the gate: `portal200001` is the platform's "your app version is out of service, update"
response (the app itself ships the matching UI strings `cdn_ea17` "This version has been discontinued…"
and `account_version_disable` "That version is out of service."). The reachable domain pool serves a
newer whitelist than any version we tried — i.e. **the pool has rotated to a newer app generation**.
It is NOT a hard wall for the platform: the sibling box captures (days old) show the family streaming
live with `tag=free` tokens from these same platforms, and the notice endpoint is live. A fresh
install's own dcs host pool (delivered per-install, obfuscated in this build) is expected to return a
portal that accepts the current version. Resolving this needs either the current version's `spkgVer`,
or a memory dump of a running TeleLatino box to recover its live dcs/portal host pool + exact keys.

## 5. Free-vs-paid signal — **free tier for live, paid for VOD/membership extras**

- Captured family traffic (this same platform, days ago): play URLs carry **`tag=free`**
  (`…&expired=1785365513&tag=free&check_play_ip=true&token=…`), `ctrl_type=account` → the live lineup
  streams on the **free/account tier**; no payment involved.
- TeleLatino dex: `v2/getFree`, `FreeTimeBean/FreeResult/FreeProduct/FreeDataBean`, `register`/`pwdCheck`
  (free account creation), `terminalAuth` (device activate), `getInviteCode`, coupons
  (`ApkQueryCouponResult`, `dialog_show_coupon_congratulation`), `membership_time_has_increased_tips`.
- Paid side exists but is VOD/extra: `PurchaseActivity`, `package/getOrderInfo`,
  `getExchangeOrderInfo`, `updateBindEmailOrPwd`, `v8/login` account login.
- Bottom line: same model as koocan — **device-activate / free account unlocks live channels**;
  paid membership is for VOD/premium extras. Need the same Phase-B confirmation (channel-count parity)
  that koocan still needs.

## 6. Verdict

**Not easier than koocan — keep koocan as the lead, treat TeleLatino as a viable fallback.**

- Same backend family & crypto (DCS getAddr proven off-device with the *same* key) → the koocan client
  flow ports almost verbatim once the per-brand keys/versions are extracted.
- But: **packed with Bangcle** (not clean like the fake UniTV), key constants obfuscated → a memory dump
  is required for the exact request/response keys (string-mining alone won't finish the client).
- Current reachable portal pool answers **version-gate `portal200001`** → needs the current version /
  fresh host pool to proceed (same open item as koocan's "find the live portal host").
- Free-tier signal is positive (tag=free on live streams), matching the GOAL.md free-forever bar.

Next step if koocan stalls: dump a running TeleLatino box (same tooling the FakeUnitv workflow uses) to
recover the live dcs host pool + obfuscated key constants, then rerun the probe against the fresh pool.
