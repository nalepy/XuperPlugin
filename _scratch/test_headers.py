#!/usr/bin/env python3
"""Test portalCore with FULL headers replicated from app DEX analysis + .4 hardware."""

import hashlib, json, urllib.request, urllib.error, ssl, time, uuid

SN = "ca0e53edac957b8f6f187528933355f1"
DEVICE_ID = "945257240"
USER_ID = "25885636"
USER = "nestor.ale@gmail.com"
PASS = "Ian20jesus"
PORTAL = "http://emowvv.dqiswip4.xyz"
SPKG = "2024-11-15 19:08:51_29_14.1_4.9.170"
HARDWARE = "sun50iw9p1"
BOARD = "exdroid"
MODEL = "V76PRO"
MANUFACTURER = "Google"
FINGERPRINT = "google/walley/titan-p1:14.1/QP1A.191105.004/eng.akrc2.20241115.190925:userdebug/test-keys"

ctx = ssl.create_default_context()
ctx.check_hostname = False
ctx.verify_mode = ssl.CERT_NONE

def post(path, body_dict=None, extra_hdrs=None):
    hdrs = {
        "Content-Type": "application/json;charset=utf-8",
        "apk": "com.global.latinotv",
        "apkVer": "54608",
        "spkgVer": SPKG,
        "User-Agent": "okhttp/4.12.0",
        "Accept-Encoding": "gzip",
        "Cache-Control": "no-store",
        "NoLog": "true",
    }
    if extra_hdrs:
        hdrs.update(extra_hdrs)

    body = json.dumps(body_dict or {}, separators=(",",":")).encode()
    try:
        r = urllib.request.Request(f"{PORTAL}{path}", data=body, headers=hdrs, method="POST")
        with urllib.request.urlopen(r, timeout=15, context=ctx if "https" in PORTAL else None) as resp:
            return resp.status, resp.read()
    except urllib.error.HTTPError as e:
        return e.code, e.read()
    except Exception as e:
        return 0, str(e).encode()

def p(label, st, raw):
    try:
        j = json.loads(raw)
        rc = j.get('returnCode', j.get('code', '?'))
        msg = j.get('errorMessage', j.get('message', ''))
        marker = "*** DIFFERENT ***" if rc != "portal200001" else ""
        print(f"  [{label}] {st}: returnCode={rc} {marker}")
        if rc != "portal200001":
            print(f"    FULL: {json.dumps(j, indent=2)[:500]}")
        elif msg:
            print(f"    errorMessage: {msg}")
    except:
        print(f"  [{label}] {st}: {raw[:100]}")

# ── Test 1: snToken with different body fields ──
print("=== 1. snToken variants ===")
bodies = [
    ("empty", {}),
    ("sn", {"sn": SN}),
    ("sn+deviceId", {"sn": SN, "deviceId": DEVICE_ID}),
    ("sn+deviceId+hardware", {"sn": SN, "deviceId": DEVICE_ID, "hardwareInfo": HARDWARE}),
    ("full-device", {
        "sn": SN, "deviceId": DEVICE_ID, "deviceIdHash": hashlib.md5(DEVICE_ID.encode()).hexdigest(),
        "hardwareInfo": HARDWARE, "board": BOARD, "model": MODEL,
        "manufacturer": MANUFACTURER, "fingerprint": FINGERPRINT
    }),
    ("with-portalCode", {"sn": SN, "portalCode": "BUZISKCONJTL"}),
    ("with-language", {"sn": SN, "language": "es", "country": "AR"}),
]
for label, body in bodies:
    st, raw = post("/api/portalCore/v3/snToken", body)
    p(f"snToken-{label}", st, raw)

# ── Test 2: Different API versions ──
print("\n=== 2. snToken API versions ===")
for ver in ["v1", "v2", "v3", "v4", "v5", "v6", "v7", "v8"]:
    st, raw = post(f"/api/portalCore/{ver}/snToken", {"sn": SN})
    p(f"snToken-{ver}", st, raw)

# ── Test 3: active with hardware info ──
print("\n=== 3. active variants ===")
for ver in ["v3", "v4", "v5", "v6", "v7", "v8"]:
    body = {"sn": SN, "deviceId": DEVICE_ID, "hardwareInfo": HARDWARE}
    st, raw = post(f"/api/portalCore/{ver}/active", body)
    p(f"active-{ver}", st, raw)

# ── Test 4: login with full device info ──
print("\n=== 4. login variants ===")
login_bodies = [
    ("basic", {"account": USER, "password": PASS, "sn": SN}),
    ("full", {"account": USER, "password": PASS, "sn": SN, "deviceId": DEVICE_ID,
              "hardwareInfo": HARDWARE}),
    ("md5+cs", {"account": USER, "password": hashlib.md5((PASS+"cloudstream").encode()).hexdigest(),
                "sn": SN}),
]
for label, body in login_bodies:
    st, raw = post("/api/portalCore/v8/login", body)
    p(f"login-{label}", st, raw)

# ── Test 5: terminalAuth ──
print("\n=== 5. terminalAuth ===")
body = {"sn": SN, "deviceId": DEVICE_ID, "hardwareInfo": HARDWARE}
st, raw = post("/api/portalCore/terminalAuth", body)
p("terminalAuth", st, raw)

# ── Test 6: Different spkgVer values ──
print("\n=== 6. spkgVer variants ===")
# The spkgVer format: date_sdk_release_kernel
# Maybe we need a NEWER one
spkg_vers = [
    "2024-11-15 19:08:51_29_14.1_4.9.170",  # current (.4)
    "2026-08-01 15:00:00_29_14.1_4.9.170",  # today's date
    "2026-08-01 15:00:00_34_14.1_4.9.170",  # SDK 34
    "2026-08-01 15:00:00_34_14.0_4.9.170",  # SDK 34, Android 14
    "2026-08-01 15:00:00_35_15.0_5.10.170", # SDK 35, Android 15
    "2026-07-09 23:55:00_29_14.1_4.9.170",  # day before build
    "2026-07-09 23:55:00_34_14.1_4.9.170",  # SDK 34, day before
]
for spkg in spkg_vers:
    hdrs = {
        "Content-Type": "application/json;charset=utf-8",
        "apk": "com.global.latinotv", "apkVer": "54608",
        "spkgVer": spkg, "User-Agent": "okhttp/4.12.0",
        "Cache-Control": "no-store", "NoLog": "true",
        "Accept-Encoding": "gzip",
    }
    body = json.dumps({"sn": SN, "deviceId": DEVICE_ID, "hardwareInfo": HARDWARE},
                      separators=(",",":")).encode()
    try:
        r = urllib.request.Request(f"{PORTAL}/api/portalCore/v3/snToken",
                                   data=body, headers=hdrs, method="POST")
        with urllib.request.urlopen(r, timeout=12, context=ctx) as resp:
            j = json.loads(resp.read())
            rc = j.get('returnCode', '?')
            marker = " *** DIFFERENT ***" if rc != "portal200001" else ""
            print(f"  spkg={spkg[:30]}... -> rc={rc}{marker}")
    except Exception as e:
        print(f"  spkg={spkg[:30]}... -> {type(e).__name__}: {str(e)[:60]}")

# ── Test 7: Different apkVer values ──
print("\n=== 7. apkVer variants ===")
for apk_ver in ["54608", "54609", "54700", "55000", "60000", "100000"]:
    hdrs = {
        "Content-Type": "application/json;charset=utf-8",
        "apk": "com.global.latinotv", "apkVer": apk_ver,
        "spkgVer": SPKG, "User-Agent": "okhttp/4.12.0",
        "Cache-Control": "no-store",
    }
    body = json.dumps({"sn": SN}, separators=(",",":")).encode()
    try:
        r = urllib.request.Request(f"{PORTAL}/api/portalCore/v3/snToken",
                                   data=body, headers=hdrs, method="POST")
        with urllib.request.urlopen(r, timeout=12, context=ctx) as resp:
            j = json.loads(resp.read())
            rc = j.get('returnCode', '?')
            marker = " *** DIFFERENT ***" if rc != "portal200001" else ""
            print(f"  apkVer={apk_ver} -> rc={rc}{marker}")
    except Exception as e:
        print(f"  apkVer={apk_ver} -> {type(e).__name__}: {str(e)[:60]}")

print("\n=== DONE ===")
