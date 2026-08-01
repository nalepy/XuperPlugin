# Worker task: UniTV patch+repack — a standalone free app WITHOUT off-device replication

Branch `unitv-patch-assess` off `master`, isolated worktree. Commit small, push, open a PR. Do NOT touch
`master`. Read `GOAL.md` + `backends/koocan/HANDOFF.md` first.

## The idea (why this dodges the wall)
Off-device portalCore replication is dead (native Titan-Ranger identity gate — koocan/XTV proven). BUT the
**fake UniTV (`com.integration.unitviptv`, APKPure v2.14.8) is NOT ijiami-packed** — it decompiles clean
(`Workspace/FakeUnitv/unitv_src/sources/`). So instead of replicating the backend, **patch the app itself
and repack normally** (no packer to defeat). The patched app keeps its own native layer → portalCore auth
still works natively → it streams — but with the gates removed. Deliverable = a standalone free APK the user
runs directly. (HANDOFF.md "Goal option 2".)

## Answer these (go/no-go on the patch route)
1. **Does the fake UniTV actually stream today?** Install `Workspace/FakeUnitv/UniTV_fake_2.14.8_APKPure.apk`
   on box `.4`, launch, try to play a live channel. Does it work (backend still alive for this build)? If it
   only shows a login/paywall/dead-version screen, note exactly what blocks.
2. **Locate the gate checks in the decompiled source** (`unitv_src/sources/`): forced email/registration,
   forced update / version check, VIP/payment paywall. Grep `bindEmail`, `forceUpdate`, `isVip`, `isPay`,
   `pay`, `member`, `expire`, `login` enforcement; map each to a smali method returning a boolean/branch.
3. **Feasibility of patch+repack:** can you smali-patch those branches (force unlocked) and rebuild with
   apktool + re-sign? Any Bangcle/integrity/signature check that resists repack (should be none — it's the
   *fake*/unpacked build)? Confirm a rebuilt+resigned APK installs and launches on `.4`.
4. **Does a patched build stream all channels free?** If the gates are purely client-side and the backend
   accepts the app's native identity, a patched build should stream the full lineup. Verify or report the
   blocker.

## Constraints
- Use box `.4` (reliable). `.40` ubuntu for build/apktool if handy. Avoid `.97`.
- MAY spawn kimi subagents. Do NOT commit the APK(s) or full decompiled tree (gitignore); commit findings +
  the patch (smali diff / apktool patch script) only.
- Do NOT need any account unless step 1 shows login is required — if so, write `backends/unitv/NEEDS.md`.

## Deliverable
`backends/unitv/PATCH-ASSESSMENT.md`: does it stream today?, the gate-check locations, patch+repack
feasibility (incl. re-sign/install proof), and a verdict — is "patched standalone UniTV APK" a viable
deliverable for the mission (all channels, free, forever)? Include the smali patch if you got one working.
Then stop.
