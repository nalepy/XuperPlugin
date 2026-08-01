#!/usr/bin/env python3
"""
portal_probe.py — quick off-device portalCore gate probe for TeleLatino.
Reuses the koocan request wrap (DES dCsPLwiy for getAddr; 3DES for portalCore)
with the TeleLatino identity. Confirms whether the version gate
(portal200001) is still active, and records the exact header set.
"""
import json
import urllib.request

APK = "com.global.latinotv"
APK_VER = "54608"
SPKG_VER = "2024-11-15 19:08:51_29_14.1_4.9.170"
SN = "ca0e53edac957b8f6f187528933355f1"
PORTAL = "http://emowvv.dqiswip4.xyz"

def base_headers(apk=APK, ver=APK_VER, spkg=SPKG_VER):
    return {
        "Content-Type": "application/json;charset=utf-8",
        "apk": apk,
        "apkVer": ver,
        "spkgVer": spkg,
        "User-Agent": "okhttp/4.12.0",
        "Accept-Encoding": "gzip",
        "Cache-Control": "no-store",
        "NoLog": "true",
    }

def get_addr():
    from Crypto.Cipher import DES
    body = {"sn": SN, "type": 1, "authCode": "", "authVersion": "", "reserve1": "AA:BB:CC:DD:EE:FF"}
    js = json.dumps(body, separators=(",", ":"))
    pad = 8 - (len(js.encode()) % 8)
    data = js.encode() + bytes([pad]) * pad
    enc = DES.new(b"dCsPLwiy", DES.MODE_ECB).encrypt(data).hex().upper()
    payload = json.dumps({"data": enc, "len": len(js.encode())}, separators=(",", ":"))
    req = urllib.request.Request("http://emowvv.dqiswip4.xyz/api/v2/dcs/getAddr",
                                 data=payload.encode(), headers=base_headers(), method="POST")
    with urllib.request.urlopen(req, timeout=15) as r:
        j = json.loads(r.read())
        raw = DES.new(b"dCsPLwiy", DES.MODE_ECB).decrypt(bytes.fromhex(j["data"]))
        print("[getAddr] returnCode:", j.get("returnCode"), "->", raw[:j["len"]].decode()[:200])

def portal_call(path, bean, apk=APK, ver=APK_VER, spkg=SPKG_VER):
    hdrs = base_headers(apk, ver, spkg)
    body = json.dumps(bean, separators=(",", ":"))
    req = urllib.request.Request(PORTAL + path, data=body.encode(), headers=hdrs, method="POST")
    try:
        with urllib.request.urlopen(req, timeout=15) as r:
            print(f"[{path}] {r.status}", r.read()[:300].decode("utf-8", "replace"))
    except urllib.error.HTTPError as e:
        print(f"[{path}] HTTP {e.code}", e.read()[:300].decode("utf-8", "replace"))

if __name__ == "__main__":
    import sys
    get_addr()
    bean = {"sn": SN, "userId": "25885636", "portalCode": "latinotv", "type": "0",
            "columnId": 1, "page": 1, "pageSize": 50}
    for ver in (["54608"] if len(sys.argv) < 2 else sys.argv[1:]):
        print(f"--- apkVer={ver} ---")
        portal_call("/api/portalCore/v3/getColumnContents", bean, ver=ver)
        portal_call("/api/portalCore/v6/getLiveData", bean, ver=ver)
