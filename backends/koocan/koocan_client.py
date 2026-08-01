#!/usr/bin/env python3
"""
koocan_client.py — off-device client for the fake UniTV (koocan.com) backend.

Reverse-engineered from unitv_src/sources (v2.14.8, com.integration.unitviptv).
Replicates the app's exact request/response crypto:

  Request wrap (DES):
    - DCS getAddr  : DES/ECB/PKCS5("dCsPLwiy")    hex-upper, body {"data":..,"len":..}
    - SLB / getVod : DES/ECB/PKCS5("b940e017")    (f4280a[0:8]) form data/len
    - data collect : DES/ECB/PKCS5("D#a!t-a&")
  Response wrap (3DES):
    - portalCore/MMS JSON: data field = hex(base64(3DES/ECB/PKCS5(plaintext)))
      3DES key = first 24 bytes of the app's custom base64 decode of the key string
      (the app and server share this "broken" decoder, so it round-trips)

Headers on portalCore/MMS/OtherModel requests:
    Content-Type: application/json;charset=utf-8
    apk: com.integration.unitviptv
    apkVer: 21408
    spkgVer: 2018-12-18 15:24:39_5.1.1_3.14.29

Boot flow (from com.mobile.brasiltv.f.b.u CloudStream task):
    DCS getAddr -> snToken -> SN=md5(snToken+"cloudstream") -> activate/login
Usage:
    python3 koocan_client.py dcs            # DCS getAddr -> portal hosts
    python3 koocan_client.py chain          # full chain; stops at the first gate
    python3 koocan_client.py sn             # snToken (needs -H portal host)
    python3 koocan_client.py activate       # activate device (needs sn/snToken)
    python3 koocan_client.py authinfo       # getAuthInfo
    python3 koocan_client.py slb            # getSlbInfo v5

STATUS (2026-08-01, Phase A worker): DCS getAddr works off-device (SN-keyed) and
resolves the live portal hosts (mgdcs.jhwi1elw.com / ouwfg.hzmono.com). Every
portalCore call (snToken/active/login/getAuthInfo/getSlbInfo/...) is hard-gated
with {"returnCode":"portal200001","errorMessage":"版本已停止使用"} for ALL
identity/version/body/transport variants — the same native Titan-Ranger
connection-identity gate as XTV. The Java 3DES request path (this client) is
accepted (HTTP 200 + structured JSON) but the origin rejects it at the
connection level. See backends/koocan/FINDINGS.md + NEEDS.md.
"""

import argparse
import base64
import hashlib
import json
import sys
import urllib.request
import urllib.error

# ---------------------------------------------------------------------------
# Crypto — faithful replicas of the app
# ---------------------------------------------------------------------------

_B64_TBL = [-1] * 256
_ALPHA = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
for _i, _ch in enumerate(_ALPHA):
    _B64_TBL[ord(_ch)] = _i


def app_b64decode(s: str) -> bytes:
    """Replica of com.brasiltv.a.a.a.a (sun.misc BASE64Decoder port).
    Skips \\n \\r; anything else, including '-', maps through the 256 table."""
    out = bytearray()
    data = s.encode("latin-1")
    i = 0
    n = len(data)
    while i < n:
        c = data[i]
        if c in (10, 13):
            i += 1
            continue
        atom = bytearray(4)
        atom[0] = c
        got = 1
        while got < 4 and i + 1 < n:
            i += 1
            cc = data[i]
            if cc in (10, 13):
                continue
            atom[got] = cc
            got += 1
        size = 4
        if atom[3] == 61:  # '='
            size = 3
        if atom[2] == 61:  # '='
            size = 2
        v0 = _B64_TBL[atom[0] & 0xFF]
        v1 = _B64_TBL[atom[1] & 0xFF]
        v2 = _B64_TBL[atom[2] & 0xFF]
        v3 = _B64_TBL[atom[3] & 0xFF]
        if size >= 2:
            out.append(((v0 << 2) & 0xFC) | ((v1 >> 4) & 3))
        if size >= 3:
            out.append(((v1 << 4) & 0xF0) | ((v2 >> 2) & 0x0F))
        if size >= 4:
            out.append(((v2 << 6) & 0xC0) | (v3 & 0x3F))
        i += 1
    return bytes(out)


def des3_key(key_str: str) -> bytes:
    """3DES key derivation used by com.brasiltv.a.b.b:
    base64-decode (app decoder) -> first 24 bytes."""
    return app_b64decode(key_str)[:24]


def hex2bytes(hexstr: str) -> bytes:
    h = hexstr.strip().replace(" ", "").upper()
    return bytes.fromhex(h)


def bytes2hex(b: bytes) -> str:
    return b.hex().upper()


def des_encrypt_ecb_pkcs5(plain: str, key: bytes) -> str:
    """DES/ECB/PKCS5Padding encrypt -> hex (mobile.com.requestframe.cloudstream.b.a)"""
    from Crypto.Cipher import DES
    pad = 8 - (len(plain.encode("utf-8")) % 8)
    if pad == 8:
        pad = 8  # PKCS5 always pads
    data = plain.encode("utf-8") + bytes([pad]) * pad
    cipher = DES.new(key, DES.MODE_ECB)
    return bytes2hex(cipher.encrypt(data))


def des_decrypt_ecb_nopad_hex(data_hex: str, key: bytes, plain_len: int) -> str:
    """DES/ECB/NoPadding decrypt + strip padding by plain_len (cloudstream.b)"""
    from Crypto.Cipher import DES
    cipher = DES.new(key, DES.MODE_ECB)
    raw = cipher.decrypt(hex2bytes(data_hex))
    strip = (8 - (plain_len % 8)) % 8
    keep = len(raw) - strip
    return raw[:keep].decode("utf-8", "replace")


def des3_encrypt_pkcs5(plain: str, key_str: str) -> str:
    """portalCore/MMS request body encrypt -> hex(base64(3DES-ECB-PKCS5)).
    Faithful to com.brasiltv.a.b.b.a(): 3DES -> standard base64 (newlines
    stripped) -> per-char lowercase hex (com.brasiltv.a.b.a.b)."""
    from Crypto.Cipher import DES3
    from Crypto.Util.Padding import pad
    data = pad(plain.encode("utf-8"), 8)
    cipher = DES3.new(des3_key(key_str), DES3.MODE_ECB)
    b64 = base64.b64encode(cipher.encrypt(data)).decode("ascii")
    return b64.encode("utf-8").hex()


def des3_decrypt_pkcs5(data_hex: str, key_str: str) -> str:
    """portalCore/MMS response data decrypt:
    hex -> utf8 string -> app base64 decode -> 3DES/ECB/PKCS5"""
    from Crypto.Cipher import DES3
    s = hex2bytes(data_hex).decode("utf-8", "replace")
    payload = app_b64decode(s)
    cipher = DES3.new(des3_key(key_str), DES3.MODE_ECB)
    return cipher.decrypt(payload).decode("utf-8", "replace")


# ---------------------------------------------------------------------------
# Constants (from the decompiled sources + res/values/strings.xml)
# ---------------------------------------------------------------------------

APK = "com.integration.unitviptv"
APK_VER = "21408"
SPKG_VER = "2018-12-18 15:24:39_5.1.1_3.14.29"

DCS_HOSTS = ["http://mobile.solz1lf.com", "http://mbfel.lgesetd1l.com"]   # portal_main/backup
DCCORE_HOSTS = ["http://dc3.tesgdz.com", "http://dc3.hgsesd.com"]         # dccore_main/backup
DATA_COLLECT_HOSTS = ["http://cool.kfsxdz.com", "http://cool.nbgfbr.com"] # datacollect
AD_HOSTS = ["http://mobiletv.ogy1lfw.com", "http://mobiletv.terdlfw.com"] # ad
EPG_HOSTS = ["http://pre.itgfgdz.com", "http://pre.utedbr.com"]           # epg
PORTAL_CORE = ["http://portalcore.koocan.com", "http://portalcore-b.koocan.com"]
MMS_HOST = "http://vip.wisecloud.koocan.com"

DES_KEY_DCS = b"dCsPLwiy"        # f9282b
DES_KEY_SLB = b"b940e017"        # f4280a[0:8]
DES_KEY_COLLECT = b"D#a!t-a&"    # f9283c
DES3_KEY_RESP = "b940e017-cfea-4aa0-b69d-3a82b6428ed3"   # f4280a
DES3_KEY_DOMAIN = "c6768bbe-189f-4d9d-b35c-f235a9fd7587" # f4282c
RESPONSE_KEY_SUBS = "NxZZ7EYgaJiJSBHjnq7sDxYvYRm32tPQ"

PORTAL_CODE = "koocanmobile2"
LANG = "1"  # 1=pt/zh, 2=zh-hant, 3=en

# Device-fingerprint fields merged into every portalCore body
# (mobile.com.requestframe.f.a.b() — the CommonParams interceptor)
COMMON_PARAMS = {
    "loginType": "3",
    "appLanguage": "en",
    "apkVersion": int(APK_VER),
    "sysVersion": "5.1.1",           # Build.VERSION.RELEASE on the fake-app device
    "appId": APK,
    "hardwareInfo": "rk30board",
    "model": "V88",
    "product": "rk322x_box",
    "cpu": "armeabi-v7a",
}


def base_headers():
    return {
        "Content-Type": "application/json;charset=utf-8",
        "apk": APK,
        "apkVer": APK_VER,
        "spkgVer": SPKG_VER,
    }


def http_post(url: str, body: str, headers=None, timeout=20):
    req = urllib.request.Request(url, data=body.encode("utf-8") if body is not None else None,
                                 headers=headers or base_headers(), method="POST")
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            return resp.status, resp.read().decode("utf-8", "replace"), dict(resp.headers)
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8", "replace"), dict(e.headers)
    except Exception as e:
        return 0, str(e), {}


# ---------------------------------------------------------------------------
# API calls
# ---------------------------------------------------------------------------

def dcs_get_addr(sn: str, mac: str = "", host: str = None):
    """POST /api/v2/dcs/getAddr — body GetAddrBean DES-wrapped (key dCsPLwiy)."""
    body = {"sn": sn, "type": 1, "authCode": "", "authVersion": "", "reserve1": mac}
    json_str = json.dumps(body, separators=(",", ":"), ensure_ascii=False)
    data = des_encrypt_ecb_pkcs5(json_str, DES_KEY_DCS)
    payload = json.dumps({"data": data, "len": len(json_str.encode("utf-8"))},
                         separators=(",", ":"))
    hosts = [host] if host else DCS_HOSTS
    for h in hosts:
        st, text, hdrs = http_post(h + "/api/v2/dcs/getAddr", payload)
        print(f"[dcs] {h}/api/v2/dcs/getAddr -> {st}")
        if st != 200:
            print("      ", text[:300])
            continue
        try:
            j = json.loads(text)
            dec = des_decrypt_ecb_nopad_hex(j["data"], DES_KEY_DCS, j["len"])
            print("[dcs] decrypted:", dec)
            return json.loads(dec)
        except Exception as e:
            print("[dcs] parse/decrypt failed:", e, "raw:", text[:300])
    return None


def _portal_body(bean: dict) -> str:
    """Merge CommonParams into the bean and 3DES-wrap -> hex body string."""
    merged = dict(COMMON_PARAMS)
    for k, v in bean.items():
        if v is not None:
            merged[k] = v
    json_str = json.dumps(merged, separators=(",", ":"), ensure_ascii=False)
    return des3_encrypt_pkcs5(json_str, DES3_KEY_RESP)


def portal_call(host: str, path: str, bean: dict, key_str: str = DES3_KEY_RESP):
    """Generic portalCore POST — body = hex(base64(3DES(commonParams+bean))),
    response data field decrypted with the same 3DES key."""
    body = _portal_body(bean)
    st, text, hdrs = http_post(host + path, body)
    print(f"[portal] POST {host}{path} -> {st}")
    if st != 200:
        print("      ", text[:300])
        return None
    try:
        j = json.loads(text)
        print("[portal] raw resp:", text[:200])
        if j.get("returnCode") == "0" and j.get("data"):
            dec = des3_decrypt_pkcs5(j["data"], key_str)
            print("[portal] decrypted data:", dec[:500])
            return json.loads(dec) if dec else {}
        return j
    except Exception as e:
        print("[portal] decrypt/parse failed:", e)
        print("      raw:", text[:300])
    return None


def sn_token(host: str):
    """POST /api/portalCore/snToken — no bean (empty body still gets
    CommonParams merged + 3DES-wrapped by the app's interceptor)."""
    body = _portal_body({})
    st, text, hdrs = http_post(host + "/api/portalCore/snToken", body)
    print(f"[sn] POST {host}/api/portalCore/snToken -> {st}")
    if st != 200:
        print("   ", text[:300])
        return None
    try:
        j = json.loads(text)
        print("[sn] raw:", text[:300])
        if j.get("returnCode") == "0" and j.get("data"):
            dec = des3_decrypt_pkcs5(j["data"], DES3_KEY_RESP)
            print("[sn] decrypted:", dec[:300])
            return json.loads(dec)
        return j
    except Exception as e:
        print("[sn] failed:", e, text[:300])
    return None


def activate(host: str, sn: str, sn_token: str, mac: str = ""):
    bean = {"sn": sn, "snToken": sn_token, "authVersion": "", "authCode": "",
            "macAddr": mac, "reserve1": mac}
    return portal_call(host, "/api/portalCore/v3/active", bean)


def login(host: str, user: str, pwd: str, sn: str, mac: str = "", account_type: str = "1"):
    bean = {"accountType": account_type, "areaCode": "", "userName": user,
            "password": pwd, "sn": sn, "type": None, "macAddr": mac,
            "verificationCode": None, "verificationToken": None}
    return portal_call(host, "/api/portalCore/v3/login", bean)


def get_auth_info(host: str, user_token: str, user_id: str):
    bean = {"userToken": user_token, "userId": user_id, "type": LANG,
            "portalCode": PORTAL_CODE, "lang": LANG}
    return portal_call(host, "/api/portalCore/v3/getAuthInfo", bean)


def get_slb_info(host: str, user_token: str, user_id: str, sn: str, app_ver: str = "2.14.8"):
    bean = {"userToken": user_token, "userId": user_id, "portalCode": PORTAL_CODE,
            "type": "1", "appVer": app_ver, "lang": LANG, "sn": sn}
    return portal_call(host, "/api/portalCore/v5/getSlbInfo", bean)


def get_column_contents(host: str, user_token: str, user_id: str):
    bean = {"userToken": user_token, "userId": user_id, "portalCode": PORTAL_CODE,
            "type": "0", "columnId": 1, "page": 1, "pageSize": 1000}
    return portal_call(host, "/api/portalCore/v3/getColumnContents", bean)


def mms_login(host: str, user: str, pwd: str, terminal_id: str, mac: str = ""):
    md5pwd = hashlib.md5((pwd + "cloudstream").encode()).hexdigest()
    bean = {"type": "phone", "terminalType": "Android", "terminalModel": "Mobile",
            "versionInfo": APK_VER, "appName": APK, "terminalId": terminal_id,
            "userName": user, "password": md5pwd, "oldUsercode": "", "customer": "",
            "touristUid": 0, "macAddr": mac, "simCode": "", "timeZone": "", "vpnStatus": ""}
    return portal_call(host, "/api/MMS/terminal/login", bean)


def resolve_portal_hosts(sn: str, mac: str = ""):
    """getAddr -> (primary, backup) portal base URLs from dcsClientUrl.
    SN-keyed: unknown SNs get 404 (device must be registered with the backend)."""
    r = dcs_get_addr(sn, mac)
    if not r or r.get("returnCode") != "0":
        print("[resolve] getAddr failed — no portal hosts")
        return []
    urls = [u for u in (r.get("dcsClientUrl") or "").split("|") if u]
    print(f"[resolve] portal hosts: {urls}")
    return urls


def run_chain(sn: str, mac: str = "AA:BB:CC:DD:EE:FF", host: str = None):
    """Full off-device auth chain: getAddr -> snToken -> activate -> authInfo
    -> slb -> columns. Stops at the first hard gate and reports where."""
    print("=== koocan chain step 1/5: DCS getAddr (resolve portal host) ===")
    hosts = resolve_portal_hosts(sn, mac) if not host else [host]
    if not hosts:
        print("[chain] STOP: no portal host (SN unknown to DCS?)")
        return False
    portal = hosts[0]

    print("=== step 2/5: snToken -> SN=md5(snToken+cloudstream) -> active ===")
    stok = sn_token(portal)
    if not stok or stok.get("returnCode") != "0":
        print(f"[chain] STOP at snToken: {stok}")
        return False
    sn_token_val = stok.get("data", {}).get("snToken") or stok.get("snToken")
    if not sn_token_val:
        print(f"[chain] snToken response has no snToken value: {stok}")
        return False
    sn2 = hashlib.md5((sn_token_val + "cloudstream").encode()).hexdigest()
    print(f"[chain] SN = md5(snToken + cloudstream) = {sn2}")
    act = activate(portal, sn2, sn_token_val, mac)
    if not act or act.get("returnCode") != "0":
        print(f"[chain] STOP at active: {act}")
        return False

    print("=== step 3/5: getAuthInfo + getSlbInfo (v5) ===")
    uid = act.get("data", {}).get("userId") or act.get("userId")
    utok = act.get("data", {}).get("userToken") or act.get("userToken")
    print(f"[chain] userId={uid} userToken={utok}")
    ai = get_auth_info(portal, utok, uid)
    if not ai or ai.get("returnCode") != "0":
        print(f"[chain] STOP at getAuthInfo: {ai}")
        return False
    slb = get_slb_info(portal, utok, uid, sn2)
    if not slb or slb.get("returnCode") != "0":
        print(f"[chain] STOP at getSlbInfo: {slb}")
        return False

    print("=== step 4/5: getColumnContents / getLiveData (channel list) ===")
    cols = get_column_contents(portal, utok, uid)
    if not cols or cols.get("returnCode") != "0":
        print(f"[chain] STOP at getColumnContents: {cols}")
        return False

    print("=== step 5/5: fetch a live .m3u8 + .ts ===")
    print("[chain] chain reached stream stage (m3u8 fetch not yet wired)")
    return True


# ---------------------------------------------------------------------------
# CLI
# ---------------------------------------------------------------------------

def main():
    p = argparse.ArgumentParser(description="koocan fake-UniTV off-device client")
    p.add_argument("cmd", choices=["dcs", "sn", "activate", "login", "authinfo",
                                   "slb", "columns", "mmslogin", "dcs-test", "chain"])
    p.add_argument("-H", "--host", help="portal host (default: DCS-resolved or portalcore.koocan.com)")
    p.add_argument("--sn", default="147107feb03d65bf30773f8b604642cb", help="device SN (default: box SN)")
    p.add_argument("--sntoken", default="", help="SN token")
    p.add_argument("--mac", default="AA:BB:CC:DD:EE:FF")
    p.add_argument("-u", "--user", default="")
    p.add_argument("-p", "--pwd", default="")
    p.add_argument("--token", default="", help="userToken")
    p.add_argument("--uid", default="", help="userId")
    args = p.parse_args()

    if args.cmd == "dcs":
        r = dcs_get_addr(args.sn, args.mac, args.host)
        if r:
            print("\n[result] dcsClientUrl:", r.get("dcsClientUrl"))
            print("[result] dcsClientUrlAlias:", r.get("dcsClientUrlAlias"))
            print("[result] returnCode:", r.get("returnCode"))
        return

    if args.cmd == "dcs-test":
        # probe both DCS hosts and show raw responses for key sanity checks
        for h in DCS_HOSTS:
            body = {"sn": args.sn, "type": 1, "authCode": "", "authVersion": "", "reserve1": args.mac}
            js = json.dumps(body, separators=(",", ":"))
            data = des_encrypt_ecb_pkcs5(js, DES_KEY_DCS)
            payload = json.dumps({"data": data, "len": len(js.encode())}, separators=(",", ":"))
            st, text, _ = http_post(h + "/api/v2/dcs/getAddr", payload)
            print(f"--- {h} -> {st}")
            print(text[:400])
            try:
                j = json.loads(text)
                dec = des_decrypt_ecb_nopad_hex(j["data"], DES_KEY_DCS, j["len"])
                print("decrypted:", dec)
            except Exception as e:
                print("decrypt err:", e)
        return

    if args.cmd == "chain":
        run_chain(args.sn, args.mac, args.host)
        return

    host = args.host or PORTAL_CORE[0]

    if args.cmd == "sn":
        sn_token(host)
    elif args.cmd == "activate":
        activate(host, args.sn, args.sntoken, args.mac)
    elif args.cmd == "login":
        login(host, args.user, args.pwd, args.sn, args.mac)
    elif args.cmd == "authinfo":
        get_auth_info(host, args.token, args.uid)
    elif args.cmd == "slb":
        get_slb_info(host, args.token, args.uid, args.sn)
    elif args.cmd == "columns":
        get_column_contents(host, args.token, args.uid)
    elif args.cmd == "mmslogin":
        mms_login(host, args.user, args.pwd, args.mac, args.mac)


if __name__ == "__main__":
    main()
