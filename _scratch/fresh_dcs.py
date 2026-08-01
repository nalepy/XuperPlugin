#!/usr/bin/env python3
"""
Fresh DCS getAddr probe — get current live domain pool.
Tests multiple DCS hosts to find the one currently serving TeleLatino.
"""
import json, urllib.request, urllib.error
from Crypto.Cipher import DES

SN = "ca0e53edac957b8f6f187528933355f1"
DES_KEY_DCS = b"dCsPLwiy"
SPKG = "2024-11-15 19:08:51_29_14.1_4.9.170"

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
            "spkgVer": SPKG, "User-Agent": "okhttp/4.12.0"}

# Primary DCS hosts from ASSESSMENT.md + FINDINGS.md
DCS_HOSTS = [
    "http://emowvv.dqiswip4.xyz",
    "http://espjey.ysnihrwtg.com",
    "http://wetc.pvqox2zhlc.com",
    "http://sfgknh.qho3cnsyil.com",
    "http://tpst.twpisacnb.com",
]

for host in DCS_HOSTS:
    body = {"sn": SN, "type": 1, "authCode": "", "authVersion": "",
            "reserve1": "AA:BB:CC:DD:EE:FF"}
    js = json.dumps(body, separators=(",", ":"))
    enc = des_encrypt_ecb_pkcs5(js, DES_KEY_DCS)
    payload = json.dumps({"data": enc, "len": len(js.encode())}, separators=(",", ":"))

    try:
        r = urllib.request.Request(f"{host}/api/v2/dcs/getAddr",
                                   data=payload.encode(), headers=hdrs(), method="POST")
        with urllib.request.urlopen(r, timeout=10) as resp:
            raw = resp.read()
            j = json.loads(raw)
            if "data" in j:
                dec = des_decrypt_ecb_nopad_hex(j["data"], DES_KEY_DCS, j["len"])
                rj = json.loads(dec)
                rc = rj.get("returnCode", "?")
                print(f"[{host}] getAddr -> returnCode={rc}")
                if rc == "0":
                    for k in ["dcsClientUrl", "dcsClientUrlAlias", "portalCode"]:
                        if k in rj:
                            print(f"  {k}: {rj[k]}")
                    # Also print ALL keys
                    for k, v in rj.items():
                        if isinstance(v, str) and len(v) < 200:
                            print(f"  {k}: {v}")
            else:
                print(f"[{host}] -> {resp.status}: {raw.hex()[:100]}")
    except urllib.error.HTTPError as e:
        raw = e.read()
        print(f"[{host}] -> {e.code}: {raw[:200]}")
    except Exception as e:
        print(f"[{host}] -> {type(e).__name__}: {str(e)[:80]}")
