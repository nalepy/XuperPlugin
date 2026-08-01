# GOAL (master) — a STANDALONE free IPTV app, all channels, forever

> **This is the north star. It supersedes the framing of `GOAL0/1/2.md`** (which are XTV-specific and now
> largely historical/reference). Read this first. Updated 2026-08-01 (session 33) after the owner reset the
> mission.

## The mission (owner's words, session 33)
Build **our own STANDALONE app/plugin** that streams **all channels, for free, forever**. It must NOT
depend on any vendor app running on a box. The **operator/backend does not matter** — XTV, UniTV, koocan,
TeleLatino, BrasilTV, anything — pick whichever we can **fully replicate off-device**. **Logging in with a
username/password is acceptable.** The deliverable is a self-contained app the user actually uses instead
of the vendor's.

## Definition of DONE (all must hold)
1. **Standalone** — our app performs auth + stream-URL resolution itself. No vendor app running, **no
   memory-harvest**, no `.97`/`.4` box in the loop.
2. **All channels** — the full live lineup the operator offers (validate the channel count vs the vendor
   app), playable as standard HLS in VLC/TiviMate/Kodi via our local proxy.
3. **Free** — no payment. A free account / device-activate / free tier that unlocks the live channels is
   fine. Username/password login is allowed.
4. **Forever (sustainable)** — the app refreshes its own tokens/sessions and keeps playing across the
   normal session/token expiry (playlist tokens ~30 s, session ~30 min). No manual re-capture.

## What is NO LONGER the goal (demoted this session)
- **The harvest sidestep (session 32, `scripts/hls_harvester.py`)** — reads the UniTV app's live memory on
  `.97` and re-serves its m3u8. **Not the deliverable:** it only mirrors UniTV's own channels, so the user
  could just run UniTV directly (better UI). **Keep it only as a technical validation / emergency fallback.**
- **Cracking XTV (`com.android.mgstv`) specifically** — its portalCore is **un-replicable off-device**
  (bound to the native Titan-Ranger DoHttpSec connection identity; proven by two independent
  investigations). XTV is only reachable via the harvest sidestep = redundant. **XTV is OUT as a
  standalone target.** (`GOAL1.md` = superseded; `GOAL2.md` XTV-wire-diff body = historical.)

## Backend candidates — ranked by crackability (pick the winner, build on it)
| # | Operator / app | Packed? | Off-device auth | Status |
|---|---|---|---|---|
| **1** | **koocan / UniTV (fake, `com.integration.unitviptv`)** | **No ijiami** | **PROVEN — `getAddr` → `returnCode:"0"`** | **LEAD.** Crypto fully in clear (DES req keys `dCsPLwiy`/`b940e017`/`D#a!t-a&`; **cleartext-UUID 3DES response keys** `b940e017-…`/`c6768bbe-…`). Client recipe: `_session/fakeunitv_intel/koocan_client.py`. |
| 2 | TeleLatino | unknown | untested | Assess: obtain APK, check for ijiami, check if same portalCore family. Owner named it as acceptable. |
| 3 | BrasilTV (real) | **ijiami-packed** (`Xuper/brasiltv/`) | untested | The codebase UniTV/koocan reskins. Real build is packed → harder; the *fake* UniTV is the unpacked sibling → prefer koocan. |
| 4 | XTV / mgstv | ijiami | **DEAD off-device** (Ranger identity) | OUT for standalone (see above). |

## THE PLAN (koocan-first — the reachable standalone path)
Work the koocan/UniTV chain to a full, self-contained, all-channels stream, then port it into the plugin.

### Phase A — finish the off-device koocan auth chain (in python first; tools already exist)
Use `_session/fakeunitv_intel/koocan_client.py` (+ `mint_tokens.py`). Proven so far:
`dcs getAddr → returnCode:"0"` (portal/dcs hosts resolved). Remaining, in order:
1. **Find the live portal host.** `portalcore.koocan.com` is NXDOMAIN here; the working host comes from a
   fuller `getAddr`/config field, not the hardcoded one. Resolve it (read the `dcsClientUrl`/alias chain,
   or the box `.properties`/heap the sibling agent has).
2. **snToken → device activate.** `SN = md5(snToken + "cloudstream")`; then `/api/portalCore/v3/active`.
   Determine whether device-activate alone yields a streamable token, or a **free account login** is needed
   (`/api/portalCore/v3/login` or `/api/MMS/terminal/login`, password = `md5(pwd+"cloudstream")`).
3. **getAuthInfo + getSlbInfo (v5)** → the DES-decrypted **stream host pool** + play-URL builder.
4. **getColumnContents / getLiveData** → the **full channel list** + per-channel playlist path.
5. **Fetch one live `.m3u8` + `.ts`** off-device end-to-end → confirm playback (ffplay). Confirm the
   channel **count matches the vendor app** (the "all channels" bar).

### Phase B — sustain it (the "forever" requirement)
- Nail down token/session lifetimes and the refresh calls; build a mint-on-demand loop (the playlist token
  is one-time, ~30 s window — same pattern the plugin's `M3uProxyServer` already handles).
- Confirm the free tier / device-activate gives **all** live channels (not a paywalled subset). If some
  channels need a (free) account, script the login.

### Phase C — port into the standalone plugin
- Move the proven python flow into Kotlin under `app/src/main/java/com/xuper/plugin/`. `XuperApiClient.kt`
  + `XuperCrypto.kt` already have DES/3DES scaffolding — retarget to koocan (keys/hosts/endpoints above).
  `M3uProxyServer.kt` already serves standard HLS; feed it the koocan playlist.
- Ship: build on `.40`, install, point VLC/TiviMate at the local proxy, verify **all channels, no vendor
  app, survives token expiry**.

## Coordination
The sibling **FakeUniTV** agent (`Workspace/FakeUnitv/`) is actively driving the koocan RE and may already
have the live portal host / a working account. **Re-fetch `Workspace/FakeUnitv/` for their latest** before
re-deriving anything; a stable snapshot is preserved in **`_session/fakeunitv_intel/`**. Do not disrupt
their `.4`/`.97` boxes.

## Open questions to resolve early
- Does koocan device-activate give **all live channels for free**, or is a (free) account / paid VIP
  needed for the full lineup? (Determines whether Phase A step 2 needs login.)
- Is TeleLatino easier/cleaner than koocan (unpacked? same API)? Worth a quick assess if koocan stalls.
- Channel-count parity: how many live channels does the vendor app show vs what our chain returns?
