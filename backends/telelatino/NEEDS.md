# NEEDS — what blocks further progress

**Updated 2026-08-01 (branch `telelatino-hash-live`).**

## Status: channel→hash mapping OBTAINED (285/344 EPG + 956 heap catalog)

The one blocker named in the task — the channel name→hash mapping — is
**solved** via the heap harvest + live tap-walk (see CHANNEL-HASH.md,
TAP-WALK-VALIDATION.md, E2E-VERIFIED.md). 59 EPG codes remain unmapped
(see below), but they are not reachable by any path available on `.4`:

1. **No in-app update exists.** `GET wetc.pvqox2zhlc.com/MarketServer/update?action=checkUpdate&packagenamesAndVersioncodes=com.global.latinotv,54608` returns `<ApkInfo><list rows="0"/></ApkInfo>`. The version gate cannot be cleared by updating — there is nothing newer.
2. **portalCore is hard-gated off-device.** `getColumnContents`/`getLiveData` return `portal200001` ("版本已停止使用") for apkVer 54608, 60203, 99999 (portal_probe.py). getAddr and EPG remain open.
3. **The 59 unmapped EPG codes are not in the app's cached channel list** (ESPN/FoxSports variants, C5N, TelefeHD, Boomerang, A24, MTV Live, Telemundo Internacional, etc.) — the free-tier live list on `.4` is a subset of the 344-EPG. They can only be mapped by a future build that passes the gate, or by a full (non-free) account whose portalCore data is cached.

## What would unblock the remaining 59

- A **newer TeleLatino APK** actually served by the operator (currently: none —
  update server returns rows=0). Install, let it load, re-run the heap harvest.
- A **full VIP account** whose portalCore response is cached on-device (the
  free account's cached list is what we mined).
- The **3DES response key** for the `res` field (keys.md) — would decrypt
  historical portalCore responses, but the portal itself is gated, so this is
  lower value now.

## Non-blocking open items

- `res` field 3DES decrypt: PORTAL_KEY + 12 other heap key constants recorded
  (keys.md); no derivation hit. Not needed for the delivered mapping.
