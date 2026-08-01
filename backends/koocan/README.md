# backends/koocan — off-device koocan/UniTV client (the standalone LEAD)

See root `GOAL.md` (north star) and `GOAL2.md` Session 33. koocan portalCore ACCEPTS off-device
clients (`getAddr` -> returnCode 0); crypto fully in clear here. Entry: `koocan_client.py` (DES/3DES
+ endpoints), `mint_tokens.py` (device-token crypto), `cloudstream_crypto_b.java` (decompiled crypto
reference), `vmread.c` (watchdog-beating memory reader source). Goal: finish the auth chain to an
all-channels off-device stream, then port to the Kotlin plugin. Full live cookies/binaries live
untracked in `_session/fakeunitv_intel/`.
