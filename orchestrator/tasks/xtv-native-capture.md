# Worker task: capture ONE fresh ACCEPTED native portalCore request from the live XTV app

Branch `xtv-native-capture` off `master`, isolated worktree. Commit small, push, open a PR. Do NOT touch
`master`. Read `GOAL.md`, `GOAL2.md` (sessions 30-31), and `backends/koocan/FINDINGS.md` first.

## DEVICE: box `.8` ONLY (rooted, Android 7.1.2, `com.android.mgstv` installed). Do NOT touch `.4`/`.97`.
`.8` is a fresh, OLDER Android (7.1.2) box — prior frida/cert-unpin attempts were on newer boxes; retry here,
the anti-tamper may be weaker.

## The prize (why this cracks the whole family)
Every off-device portalCore clone gets `portal200001` because the gate is a **native Titan-Ranger
DoHttpSec CONNECTION-IDENTITY check** — not the body (we already replicate the body/crypto). XTV and
koocan share this native layer, so **understanding what makes the app's OWN connection accepted unlocks
both.** The real app on a box IS accepted — so the answer is observable on `.8`.

## Goal — get ONE fresh, ACCEPTED, DECRYPTED portalCore request+response, and identify the identity token
Capture the live XTV app's real portalCore call (`snToken`/`getAuthInfo`/`getLiveData`) in cleartext and
find what the accepted request carries that our off-device clone does not. Try these in order:
1. **Cert-unpin + MITM on `.8`** (Android 7.1.2 → system CA trust is easier). Install a MITM CA
   (`_session/mitm-ca.pem` exists), route the app's portal traffic through mitmproxy on `.40` or locally,
   and defeat the Ranger TLS pinning (patch/hook the pin check, or use the older-Android system-CA path).
   Capture the decrypted `/api/portalCore/*` request+response. This is the cleanest — you SEE the accepted
   request and the `returnCode:0` response.
2. **Frida hook the native `DoHttpSec`** (`libexec.so` / Titan-Ranger). ijiami ptrace-blocks frida on newer
   boxes; on 7.1.2 retry frida-gadget/spawn. Hook the DoHttpSec entry to dump its plaintext input (URL,
   headers, body) AND any per-request token / connection state it injects. `backends/koocan/vmread.c` is a
   watchdog-beating memory reader if you need raw memory instead.
3. **Heap capture:** dump the app's memory while it makes a portal call (the `service_name:"portal"`
   DoHttpSec record lives in the heap — prior sessions found it) and carve the full request + the response.

## The one thing to answer
What does the ACCEPTED request have that our clone lacks — a header, a per-connection token/nonce, a
signature, a specific h2 framing, or a TLS client identity? Is it **static/replayable** (→ we can bolt it
onto our off-device client → standalone win) or **per-connection native-derived** (→ only reproducible by
running the native lib)? Capture the raw bytes either way.

## Constraints
- Box `.8` only. `.40` ubuntu for mitmproxy/tooling if handy.
- MAY spawn kimi subagents. Do NOT commit APKs/dumps/pcaps (gitignore); commit the captured request/response
  (redact nothing technical), your capture scripts, and findings.
- Coordinate mentally with the sibling FakeUniTV agent's utls/libexec work (`Workspace/FakeUnitv/`) — don't
  duplicate; build on it.

## Deliverable
`backends/xtv/NATIVE-CAPTURE.md`: the captured accepted portalCore request+response (raw), the identity
delta vs our clone, and the verdict — replayable (standalone unlock) or native-only (needs the lib). Plus
the capture script. If you get a `returnCode:0` off-device by adding the identity, SHOUT it. Then stop.
