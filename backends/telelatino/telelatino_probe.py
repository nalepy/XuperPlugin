#!/usr/bin/env python3
"""
telelatino_probe.py — off-device probe for the TeleLatino backend (com.global.latinotv).

Deep-dive update (2026-08-01, branch telelatino-deepdive):
  * The box's REAL registered identity is now known and baked in below:
      SN      = ca0e53edac957b8f6f187528933355f1
      userId  = 945257240
      version = 5.46.8 / 54608, spkgVer = 2024-11-15 19:08:51_29_14.1_4.9.170
      model   = V76PRO, appId (play URLs) = com.spanish.latinotvod
      portalCode pref = 87SS0skuAxztSQOny3WECQ== (16 bytes f3b492d2c92e031ced4903a7cb758409)
  * getAddr -> returnCode:"0" (same dCsPLwiy key as koocan). Portal pool:
      emowvv.dqiswip4.xyz | espjey.ysnihrwtg.com   (dcs: sxowvd.jzvqwcyor.com etc.)
  * portalCore (snToken/active/config/get/getFree) -> portal200001 on every host
    (emowvv, espjey, sxowvd, dcs.xifhzu.com, dcs.dfhlnb.com), HTTP and HTTPS, with the
    box's real identity. Gate is at the HTTP layer (no TLS wall) — the missing piece is
    the Bangcle-obfuscated per-brand DES/3DES request-wrap keys (see FINDINGS.md).

Usage:
    python3 telelatino_probe.py dcs          # DCS getAddr -> returnCode:0 (works)
    python3 telelatino_probe.py sn           # portalCore snToken (expect portal200001)
    python3 telelatino_probe.py sn-envelope  # snToken with flat commonParams+specificParams
    python3 telelatino_probe.py active       # portalCore v8/active
    python3 telelatino_probe.py notice       # notice endpoint (no crypto, works)
"""

import argparse
import json
import sys
import urllib.request
import urllib.error

try:
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
except Exception:
    pass

from Crypto.Cipher import DES

APK = "com.global.latinotv"
APK_APP_ID = "com.spanish.latinotvod"      # appId used in play URLs
APK_VER = "54608"
SPKG_VER = "2024-11-15 19:08:51_29_14.1_4.9.170"   # real value (from heap)
SN = "ca0e53edac957b8f6f187528933355f1"             # box's registered SN
USER_ID = "945257240"
MODEL = "V76PRO"
PORTAL_CODE = "87SS0skuAxztSQOny3WECQ=="            # prefs portal_code (base64)

DCS_HOSTS = [
    "http://sxowvd.jzvqwcyor.com",
    "http://espjey.ysnihrwtg.com",
    "http://dcs.xifhzu.com",
    "http://dcs.dfhlnb.com",
]
PORTAL_HOSTS = [
    "https://emowvv.dqiswip4.xyz",
    "https://espjey.ysnihrwtg.com",
    "https://sxowvd.jzvqwcyor.com",
    "https://dcs.xifhzu.com",
    "https://dcs.dfhlnb.com",
]
NOTICE_HOSTS = ["http://seh.utdfbgbtg.com", "http://nxiqj.jgrqyxupl.com"]

DES_KEY_DCS = b"dCsPLwiy"  # same as koocan/UniTV


def des_encrypt_ecb_pkcs5(plain: str, key: bytes) -> str:
    pad = 8 - (len(plain.encode("utf-8")) % 8)
    data = plain.encode("utf-8") + bytes([pad]) * pad
    return DES.new(key, DES.MODE_ECB).encrypt(data).hex().upper()


def des_decrypt_ecb_nopad_hex(data_hex: str, key: bytes, plain_len: int) -> str:
    raw = DES.new(key, DES.MODE_ECB).decrypt(bytes.fromhex(data_hex))
    strip = (8 - (plain_len % 8)) % 8
    return raw[: len(raw) - strip].decode("utf-8", "replace")


def base_headers(apk=APK):
    return {
        "Content-Type": "application/json;charset=utf-8",
        "apk": apk,
        "apkVer": APK_VER,
        "spkgVer": SPKG_VER,
        "User-Agent": "okhttp/4.12.0",
        "Accept": "application/json",
    }


def http_post(url, body=None, headers=None, timeout=12, method="POST", ssl_ctx=None):
    req = urllib.request.Request(
        url,
        data=body.encode("utf-8") if body is not None else None,
        headers=headers or base_headers(),
        method=method,
    )
    try:
        with urllib.request.urlopen(req, timeout=timeout, context=ssl_ctx) as resp:
            return resp.status, resp.read().decode("utf-8", "replace")
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8", "replace")
    except Exception as e:
        return 0, f"{type(e).__name__}: {e}"


def https_ctx():
    import ssl
    ctx = ssl.create_default_context()
    ctx.check_hostname = False
    ctx.verify_mode = ssl.CERT_NONE
    return ctx


def dcs_get_addr(host=None):
    body = {"sn": SN, "type": 1, "authCode": "", "authVersion": "", "reserve1": "AA:BB:CC:DD:EE:FF"}
    js = json.dumps(body, separators=(",", ":"))
    data = des_encrypt_ecb_pkcs5(js, DES_KEY_DCS)
    payload = json.dumps({"data": data, "len": len(js.encode())}, separators=(",", ":"))
    hosts = [host] if host else DCS_HOSTS
    for h in hosts:
        st, text = http_post(h + "/api/v2/dcs/getAddr", payload)
        print(f"[dcs] {h}/api/v2/dcs/getAddr -> {st}")
        if st != 200:
            print("      ", text[:200])
            continue
        try:
            j = json.loads(text)
            dec = des_decrypt_ecb_nopad_hex(j["data"], DES_KEY_DCS, j["len"])
            print("[dcs] decrypted:", dec)
            return json.loads(dec)
        except Exception as e:
            print("[dcs] parse/decrypt failed:", e, "raw:", text[:200])
    return None


def common_params():
    """The app's CommonParams (flat fields), real values where known."""
    return {
        "apkVersion": APK_VER,
        "appId": APK_APP_ID,
        "appLanguage": "es",
        "b29": "",
        "cpu": "",
        "deviceToken": "",
        "hardwareInfo": "",
        "loginType": "0",
        "model": MODEL,
        "portalCode": PORTAL_CODE,
        "product": "",
        "sn": SN,
        "spkgVer": SPKG_VER,
        "token": "",
        "userId": USER_ID,
        "version": "5.46.8",
    }


def portal_call(host, path, body_dict, label, ssl_ctx):
    js = json.dumps(body_dict, separators=(",", ":"))
    st, text = http_post(host + path, js, timeout=12, ssl_ctx=ssl_ctx)
    print(f"[{label}] POST {host}{path} -> {st}")
    print(f"   body: {js[:220]}")
    print(f"   resp: {text[:220]}")
    return text


def sn_token():
    ctx = https_ctx()
    for h in PORTAL_HOSTS:
        for path in ["/api/portalCore/v3/snToken", "/api/portalCore/snToken"]:
            portal_call(h, path, {"sn": SN, "type": 1, "authCode": "", "authVersion": "",
                                  "reserve1": "AA:BB:CC:DD:EE:FF"}, "sn", ctx)
        print()


def sn_token_envelope():
    ctx = https_ctx()
    for h in PORTAL_HOSTS[:2]:
        body = dict(common_params())
        body.update({"sn": SN, "type": 1, "authCode": "", "authVersion": "", "reserve1": "AA:BB:CC:DD:EE:FF"})
        portal_call(h, "/api/portalCore/v3/snToken", body, "sn-env", ctx)
        print()


def active():
    ctx = https_ctx()
    for h in PORTAL_HOSTS[:2]:
        portal_call(h, "/api/portalCore/v8/active",
                    {"sn": SN, "snToken": "", "authVersion": "", "authCode": "",
                     "macAddr": "AA:BB:CC:DD:EE:FF", "reserve1": "AA:BB:CC:DD:EE:FF"},
                    "active", ctx)
        print()


def notice():
    for h in NOTICE_HOSTS:
        url = (f"{h}/notice/api/get_notice?pkg={APK}&v={APK_VER}&sn={SN}"
               f"&userId={USER_ID}&language=es")
        st, text = http_post(url, method="GET")
        print(f"[notice] GET {url[:140]} -> {st}")
        print("   resp:", text[:220])


def main():
    p = argparse.ArgumentParser(description="TeleLatino off-device probe")
    p.add_argument("cmd", choices=["dcs", "sn", "sn-envelope", "active", "notice"])
    p.add_argument("-H", "--host", help="override dcs host")
    args = p.parse_args()

    if args.cmd == "dcs":
        r = dcs_get_addr(args.host)
        if r:
            print("\n[result] dcsClientUrl:", r.get("dcsClientUrl"))
            print("[result] dcsClientUrlAlias:", r.get("dcsClientUrlAlias"))
            print("[result] returnCode:", r.get("returnCode"))
    elif args.cmd == "sn":
        sn_token()
    elif args.cmd == "sn-envelope":
        sn_token_envelope()
    elif args.cmd == "active":
        active()
    elif args.cmd == "notice":
        notice()


if __name__ == "__main__":
    main()
