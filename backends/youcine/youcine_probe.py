#!/usr/bin/env python3
"""
youcine_probe.py — off-device probe for the YouCine backend (com.world.youcinetv).

Assessment evidence (2026-08-01): YouCine v1.11.1 is an ijiami-packed reskin of the
BrasilTV/CineTV app (com.interactive.brasiliptv codebase), which belongs to the same
koocan/portalCore platform family as koocan/UniTV, XTV, TeleLatino and BrasilTV.

The DCS getAddr request crypto is identical to koocan/TeleLatino: DES/ECB/PKCS5 with
key "dCsPLwiy" and the {"data": hex, "len": n} envelope. Response decrypts with the
same key + len strip.

Usage:
    python3 youcine_probe.py dcs        # DCS getAddr -> portal host (family-proof)
    python3 youcine_probe.py sn         # portalCore snToken (expect version gate)
    python3 youcine_probe.py notice     # notice endpoint (identity acceptance)
"""

import argparse
import json
import urllib.request
import urllib.error

from Crypto.Cipher import DES

APK = "com.world.youcinetv"
APK_VER = "11101"  # 1.11.1

DCS_HOSTS = ["http://espjey.ysnihrwtg.com", "http://sxowvd.jzvqwcyor.com"]
NOTICE_HOSTS = ["http://nxiqj.jgrqyxupl.com", "http://zxiws.tcgwhnvym.com"]
PORTAL_HOST = "http://emowvv.dqiswip4.xyz"  # from dcsClientUrl (primary)

DES_KEY_DCS = b"dCsPLwiy"  # same key as koocan/UniTV / TeleLatino


def des_encrypt_ecb_pkcs5(plain: str, key: bytes) -> str:
    pad = 8 - (len(plain.encode("utf-8")) % 8)
    data = plain.encode("utf-8") + bytes([pad]) * pad
    return DES.new(key, DES.MODE_ECB).encrypt(data).hex().upper()


def des_decrypt_ecb_nopad_hex(data_hex: str, key: bytes, plain_len: int) -> str:
    raw = DES.new(key, DES.MODE_ECB).decrypt(bytes.fromhex(data_hex))
    strip = (8 - (plain_len % 8)) % 8
    return raw[: len(raw) - strip].decode("utf-8", "replace")


def base_headers():
    return {
        "Content-Type": "application/json;charset=utf-8",
        "apk": APK,
        "apkVer": APK_VER,
        "spkgVer": "2024-11-15 19:08:51_29_14.1_4.9.170",
        "User-Agent": "okhttp/4.12.0",
        "Accept": "application/json",
    }


def http_post(url, body=None, headers=None, timeout=12, method="POST"):
    req = urllib.request.Request(
        url,
        data=body.encode("utf-8") if body is not None else None,
        headers=headers or base_headers(),
        method=method,
    )
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            return resp.status, resp.read().decode("utf-8", "replace")
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8", "replace")
    except Exception as e:
        return 0, f"{type(e).__name__}: {e}"


def dcs_get_addr(sn, mac="AA:BB:CC:DD:EE:FF", host=None):
    body = {"sn": sn, "type": 1, "authCode": "", "authVersion": "", "reserve1": mac}
    js = json.dumps(body, separators=(",", ":"))
    data = des_encrypt_ecb_pkcs5(js, DES_KEY_DCS)
    payload = json.dumps({"data": data, "len": len(js.encode())}, separators=(",", ":"))
    hosts = [host] if host else DCS_HOSTS
    for h in hosts:
        st, text = http_post(h + "/api/v2/dcs/getAddr", payload)
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


def sn_token(host=PORTAL_HOST):
    st, text = http_post(host + "/api/portalCore/v3/snToken", "{}")
    print(f"[sn] POST {host}/api/portalCore/v3/snToken -> {st}")
    print("     ", text[:300])


def notice(pkg=APK, sn="", uid=""):
    for h in NOTICE_HOSTS:
        url = (f"{h}/notice/api/get_notice?pkg={pkg}&v={APK_VER}"
               f"&sn={sn}&userId={uid}&language=es")
        st, text = http_post(url, None, {
            "apk": pkg, "apkVer": APK_VER,
            "spkgVer": "2024-11-15 19:08:51_29_14.1_4.9.170",
            "User-Agent": "okhttp/4.12.0",
        })
        print(f"[notice] {url} -> {st} {text[:200]}")


def main():
    p = argparse.ArgumentParser(description="YouCine off-device probe")
    p.add_argument("cmd", choices=["dcs", "sn", "notice"])
    p.add_argument("--sn", default="ca0e53edac957b8f6f187528933355f1")
    p.add_argument("--uid", default="169355704")
    p.add_argument("--host", default=None)
    args = p.parse_args()
    if args.cmd == "dcs":
        r = dcs_get_addr(args.sn, host=args.host)
        if r:
            print("\n[result] dcsClientUrl:", r.get("dcsClientUrl"))
            print("[result] dcsClientUrlAlias:", r.get("dcsClientUrlAlias"))
            print("[result] returnCode:", r.get("returnCode"))
    elif args.cmd == "sn":
        sn_token(args.host or PORTAL_HOST)
    elif args.cmd == "notice":
        notice(sn=args.sn, uid=args.uid)


if __name__ == "__main__":
    main()
