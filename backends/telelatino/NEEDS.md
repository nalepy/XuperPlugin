# NEEDS — what blocks further progress

## 1. Newer TeleLatino APK (>5.46.8)

The portalCore version gate (`portal200001` = "version discontinued") rejects
v5.46.8 (versionCode 54608, build 2026-07-09). The gate is a **version whitelist**,
not an identity wall — getAddr and EPG work fine off-device with the current SN.
The portal pool has rotated to a newer generation.

**Action:** Obtain the latest TeleLatino APK. Install on `.4` or `.40`, capture
the new SN + spkgVer + host pool, retest portalCore.

## 2. Account credentials

The app requires email + password login after device activation. Cached on `.4`:
- Email: `nestor.ale@gmail.com`
- Password MD5: `62513c1dec921de3015a0b22574512f4`

**Action:** Owner to provide the plaintext password for `nestor.ale@gmail.com`,
or create/confirm a working TeleLatino account.

## 3. 3DES response keys (lower priority — needed after version gate cleared)

The portalCore encrypts responses with 3DES/ECB. The response keys are in an
obfuscated memory region (5 UUID candidates + string clusters identified in
ASSESSMENT.md). Recoverable via memory dump of a running app on a rooted box,
or via Frida hook on `encryptThreeDESECB`/`decryptThreeDESECB`.

**Action:** Cross-compile vmread.c for armeabi-v7a (using `syscall()` not the
libc wrapper), dump the Java heap, search for UUID→3DES key mapping.
