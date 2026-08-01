# Worker task: assess TeleLatino as a standalone-backend candidate

You are a worker agent in an isolated git worktree on branch `telelatino-assess`, off `master`, in the
XuperPlugin repo. Commit small, push your branch, do NOT touch `master`. Read root `GOAL.md` first.

## Why
`GOAL.md` mission = a STANDALONE free-all-channels app on **any** off-device-replicable backend. koocan/UniTV
is the lead (`backends/koocan/`). TeleLatino is a named alternate — assess whether it's easier/cleaner.

## Your goal — a go/no-go assessment (do NOT build a full client yet)
1. Obtain the TeleLatino Android APK (APKPure/APKMirror or similar). Record version + package name.
2. **Packing check:** is it ijiami-packed (look for `assets/ijiami.dat`, `libexec.so`, `s/h/e/l/l/N`)? If
   NOT packed → decompiles clean (jadx) → far easier, like the fake UniTV. If packed → note it (harder).
3. **API family:** does it use the same portalCore/DES design as XTV/koocan (grep decompiled/strings for
   `portalCore`, `getAddr`, `getSlbInfo`, `getAuthInfo`, DES keys, `snToken`)? Reuse of that design means our
   existing crypto/flow largely cross-applies.
4. **Off-device probe:** if you can find its `getAddr`/portal endpoint + request crypto, do ONE off-device
   request (like koocan's `getAddr`) and report whether it returns success or a version-gate.
5. **Free-channels question:** any sign the full live lineup is free / device-activate, vs paid VIP.

## Constraints
- Off-device analysis; boxes: prefer `.4` if you need a device, `.40` ubuntu available. `.97` flaky — avoid.
- You MAY spawn kimi subagents for parallel sub-steps.
- Do NOT commit the APK or large decompiled trees to git (gitignore them). Commit only findings + any small scripts.

## Deliverable
`backends/telelatino/ASSESSMENT.md` on your branch: version/package, packed?, API family match?, off-device
probe result, free-vs-paid signal, and a one-line verdict (easier than koocan? worth pursuing?). Then stop.
