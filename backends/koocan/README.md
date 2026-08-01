# backends/koocan — off-device koocan/UniTV client (standalone candidate — BLOCKED)

See root `GOAL.md` (north star) and `GOAL2.md` Session 33. koocan DCS `getAddr` accepts
off-device clients (SN-keyed) and resolves the live portal hosts
(`mgdcs.jhwi1elw.com` / `ouwfg.hzmono.com`); the crypto is fully in clear here. **However
(Phase A worker, 2026-08-01): every portalCore call past `getAddr` is hard-gated with
`portal200001` for all identities/versions/bodies/transports — the same native
Titan-Ranger connection-identity wall as XTV.** See `FINDINGS.md` (what works + the gate)
and `NEEDS.md` (what would unblock). Entry: `koocan_client.py` (DES/3DES + endpoints +
`chain` runner), `mint_tokens.py` (device-token crypto), `cloudstream_crypto_b.java`
(decompiled crypto reference), `vmread.c` (watchdog-beating memory reader source). Full
live cookies/binaries live untracked in the sibling FakeUniTV workspace.
