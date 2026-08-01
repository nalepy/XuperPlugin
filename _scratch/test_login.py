#!/usr/bin/env python3
"""Test TeleLatino portalCore login with owner creds + various hash formats."""

import hashlib
import json
import urllib.request
import urllib.error
import sys

SN = "ca0e53edac957b8f6f187528933355f1"
DEVICE_ID = "945257240"
USER_ID = "25885636"
USER = "nestor.ale@gmail.com"
PASS = "Ian20jesus"
PORTAL = "http://emowvv.dqiswip4.xyz"
SPKG = "2024-11-15 19:08:51_29_14.1_4.9.170"

from Crypto.Cipher import DES
DES_KEY_DCS = b"dCsPLwiy"

def des_encrypt_ecb_pkcs5(plain, key):
    pad = 8 - (len(plain.encode("utf-8")) % 8)
    data = plain.encode("utf-8") + bytes([pad]) * pad
    return DES.new(key, DES.MODE_ECB).encrypt(data).hex().upper()

def des_decrypt_ecb_nopad_hex(data_hex, key, plain_len):
    raw = DES.new(key, DES.MODE_ECB).decrypt(bytes.fromhex(data_hex))
    strip = (8 - (plain_len % 8)) % 8
    return raw[:len(raw)-strip].decode("utf-8", "replace")

def hdrs():
    return {"Content-Type": "application/json;charset=utf-8",
            "apk": "com.global.latinotv", "apkVer": "54608",
            "spkgVer": SPKG,
            "User-Agent": "okhttp/4.12.0"}

def post(path, body_dict):
    body = json.dumps(body_dict, separators=(",",":")).encode()
    try:
        r = urllib.request.Request(f"{PORTAL}{path}", data=body, headers=hdrs(), method="POST")
        with urllib.request.urlopen(r, timeout=15) as resp:
            return resp.status, resp.read()
    except urllib.error.HTTPError as e:
        return e.code, e.read()
    except Exception as e:
        return 0, str(e).encode()

def print_resp(label, st, raw):
    try:
        j = json.loads(raw)
        rc = j.get('returnCode', j.get('code', '?'))
        msg = j.get('errorMessage', j.get('message', j.get('msg', '')))
        print(f"  [{label}] {st}: returnCode={rc} msg={msg[:100]}")
        for k in ['token', 'userToken', 'userId', 'snToken', 'data', 'len']:
            if k in j and isinstance(j[k], str) and len(j[k]) < 200:
                print(f"    {k}: {j[k]}")
        return j
    except:
        print(f"  [{label}] {st}: {raw.hex()[:120]}")
        return None

# ── Step 1: Confirm portal200001 on snToken ──
print("=== 1. snToken (baseline - expect portal200001) ===")
st, raw = post("/api/portalCore/v3/snToken", {})
print_resp("snToken-v3", st, raw)

# ── Step 2: Try login with various password hash schemes ──
print("\n=== 2. Login attempts ===")

# raw password
st, raw = post("/api/portalCore/v8/login",
               {"account": USER, "password": PASS, "sn": SN})
print_resp("login-raw", st, raw)

# MD5(password)
st, raw = post("/api/portalCore/v8/login",
               {"account": USER, "password": hashlib.md5(PASS.encode()).hexdigest(), "sn": SN})
print_resp("login-md5", st, raw)

# MD5(password + "cloudstream") — standard koocan pattern
st, raw = post("/api/portalCore/v8/login",
               {"account": USER, "password": hashlib.md5((PASS + "cloudstream").encode()).hexdigest(),
                "sn": SN})
print_resp("login-md5+cs", st, raw)

# MD5("cloudstream" + password) — reversed
st, raw = post("/api/portalCore/v8/login",
               {"account": USER, "password": hashlib.md5(("cloudstream" + PASS).encode()).hexdigest(),
                "sn": SN})
print_resp("login-cs+md5", st, raw)

# No password at all
st, raw = post("/api/portalCore/v8/login",
               {"account": USER, "password": "", "sn": SN})
print_resp("login-nopass", st, raw)

# ── Step 3: Try active → login sequence ──
print("\n=== 3. active -> login sequence ===")
st, raw = post("/api/portalCore/v3/active", {"sn": SN})
j = print_resp("active-v3", st, raw)

# After active, try login again
st, raw = post("/api/portalCore/v8/login",
               {"account": USER, "password": PASS, "sn": SN})
print_resp("login-after-active-raw", st, raw)

# ── Step 4: Try v5 login ──
print("\n=== 4. v5/v6 login variants ===")
for ver in ["v5", "v6", "v7"]:
    st, raw = post(f"/api/portalCore/{ver}/login",
                   {"account": USER, "password": PASS, "sn": SN})
    print_resp(f"login-{ver}", st, raw)

# ── Step 5: Try snToken → login sequence ──
print("\n=== 5. snToken -> DES-wrapped login ===")
# First snToken wrapped in DES (like koocan)
sn_body = {"sn": SN}
sn_js = json.dumps(sn_body, separators=(",",":"))
sn_enc = des_encrypt_ecb_pkcs5(sn_js, DES_KEY_DCS)
sn_payload = json.dumps({"data": sn_enc, "len": len(sn_js.encode())}, separators=(",",":")).encode()

try:
    r = urllib.request.Request(f"{PORTAL}/api/portalCore/v3/snToken",
                               data=sn_payload, headers=hdrs(), method="POST")
    with urllib.request.urlopen(r, timeout=15) as resp:
        raw = resp.read()
        j = json.loads(raw)
        print_resp("snToken-DES", resp.status, raw)
        if "data" in j:
            dec = des_decrypt_ecb_nopad_hex(j["data"], DES_KEY_DCS, j["len"])
            print(f"    decrypted: {dec[:200]}")
except Exception as e:
    print(f"  snToken-DES error: {e}")

# ── Step 6: Try config/get and getHome after snToken ──
print("\n=== 6. Other portalCore endpoints ===")
for path in ["/api/portalCore/config/get", "/api/portalCore/getHome",
             "/api/portalCore/terminalAuth", "/api/portalCore/v2/getFree",
             "/api/portalCore/v3/getColumnContents"]:
    st, raw = post(path, {"sn": SN})
    rc = print_resp(path.split("/")[-1], st, raw)

# ── Step 7: Try alternate portal hosts ──
print("\n=== 7. Alternate hosts ===")
for host in ["http://espjey.ysnihrwtg.com",
             "http://sxowvd.xifhzu.com"]:
    for path in ["/api/portalCore/v3/snToken", "/api/portalCore/v8/login"]:
        try:
            body = json.dumps({"sn": SN} if "snToken" in path else
                            {"account": USER, "password": PASS, "sn": SN},
                            separators=(",",":")).encode()
            r = urllib.request.Request(f"{host}{path}", data=body, headers=hdrs(), method="POST")
            with urllib.request.urlopen(r, timeout=10) as resp:
                raw = resp.read()
                j = json.loads(raw)
                rc = j.get('returnCode', '?')
                print(f"  {host}{path} -> rc={rc}")
        except Exception as e:
            print(f"  {host}{path} -> {type(e).__name__}: {str(e)[:60]}")

print("\n=== DONE ===")
