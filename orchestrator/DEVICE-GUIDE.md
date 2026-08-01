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
