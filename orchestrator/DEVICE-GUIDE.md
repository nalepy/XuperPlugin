# DEVICE-GUIDE — how workers handle a rooted box unattended

**You are headless. There is NO human to tap dialogs or answer prompts. NEVER wait for manual input.**
The owner will see messages on the screen and will NOT act on them — it is entirely your job to handle or
report. Boxes are rooted, so almost everything is scriptable via adb.

## Permissions (never let a permission dialog appear or block)
- **Install with all runtime perms granted:** `adb -s <box> install -g -r <apk>` (the `-g` grants every
  runtime permission at install — dialogs won't appear).
- **Grant extras explicitly** if something still pops:
  - `adb -s <box> shell pm grant <pkg> android.permission.<X>`
  - `appops set <pkg> SYSTEM_ALERT_WINDOW allow`   (display-over-other-apps)
  - `appops set <pkg> REQUEST_INSTALL_PACKAGES allow`
  - `appops set <pkg> ACTIVATE_VPN allow`          (VPN consent — the classic blocker)
- **UI fallback for any stray dialog:** `adb -s <box> shell uiautomator dump /sdcard/ui.xml` → pull/parse
  it → `adb -s <box> shell input tap <x> <y>` on the "ALLOW"/"OK"/"ACCEPT" node. Or `input keyevent 22`
  (right) + `66` (enter) to move to the default button.

## Login / register dialog — do NOT block
- If the task can proceed as **guest / device-activate**, do that and continue.
- If a (free) account is genuinely required to advance, write `backends/<name>/NEEDS.md` on your branch,
  commit it, and **STOP** — the orchestrator relays the credential request to the owner. Never invent,
  brute, or guess credentials.

## "Update to a newer version" dialog — do NOT accept it
- **Never accept an in-app update** — it changes the build under test and may re-pack/re-sign it.
- Dismiss/skip it: find the "Later"/"Skip"/"×"/"Not now" node via `uiautomator dump` + `input tap`, or
  block the update-check endpoint (hosts file / iptables) so it stops nagging.
- If the app **hard-blocks without updating** (forced-update wall), that IS a finding — record the exact
  version-gate message + what triggers it, and report it (it's central to the mission's "forever" goal).

## Anti-tamper you may hit (record + neutralize, don't get stuck)
- Suicide loops / root-Xposed detection / competitor-app force-stop (e.g. UniTV kills `com.global.latinotv`).
  Identify the check, no-op it in a patched build if patching, or note it and work around (e.g. uninstall
  the competitor app, or read what you need from memory before the kill fires).

## Golden rule
If you'd be tempted to "wait for the user to accept" — STOP that thought. Script it, or write NEEDS.md and
exit. The loop is: automate → if truly blocked on a secret, NEEDS.md + stop. Never hang.

## Connectivity (Tailscale — boxes reachable from anywhere)
The boxes are on a home LAN reached from any location via a Tailscale **subnet router** on `.40`
(`xtv40-subnet`, routes `192.168.100.0/24` + `192.168.3.0/24` approved). **adb uses the SAME IPs**
(`adb connect 192.168.100.4|8|97:5555`) whether the orchestrator host is on-LAN or roaming — no address
changes. Off-LAN the latency is higher (fine for control, slow for big memory dumps). If adb flaps after
a network change, retry `adb disconnect <ip>:5555 && adb connect <ip>:5555`. `.40` + the boxes must stay
powered at home (a TV HDMI-CEC auto-off will sleep an HDMI-connected box — keep boxes unplugged from TVs).

## Dialog / paywall dead-end matrix (owner knowledge — 2026-08-01)
Fresh-install + launch can throw dialogs. NEVER wait for the owner — automate each:
| Dialog | Action |
|---|---|
| **Permission (folders/storage/etc)** after fresh install | `install -g` at install; else `pm grant`/`appops`. Auto-tap ALLOW via uiautomator. |
| **Login / register** | Continue as guest/device-activate if possible; else write `NEEDS.md` (creds available for some accounts — check `orchestrator/.env`). |
| **Update to newer version** | **UPDATED POLICY (2026-08-01): ACCEPT the update — it delivers the current whitelist-passing build, which is prime intel** (version gates want the NEWER version). BEFORE accepting: (1) pull + save the current APK (`adb pull /data/app/<pkg>/*/base.apk _session/apks/<pkg>-<ver>.apk`) so you can reinstall/rollback; (2) capture the update URL/new versionCode if visible (logcat/strings — it's a download endpoint worth noting). Then accept, note the NEW versionCode, and re-analyze it (it likely passes the gate the old one failed). Keep the old APK saved. |
| **PAY / VIP / Premium (full channels)** | **THE critical one.** Select the **FREE** option to start streaming free channels. Per-app behavior: **TeleLatino** = free tier streams some channels ✅; **UniTV** = free tier streams some ✅; **YouCine** = closes (no free tier) ❌; **BrasilTV** = closes ❌; XTV = paywall. If the app closes with no free tier, that's a dead end — record it, don't loop. |

**Golden rule for dialogs:** paywall → tap FREE; update → skip; permission → grant; login → guest or
NEEDS.md. If the app has no free tier and closes, it's a go/no-go data point, not a stall.
