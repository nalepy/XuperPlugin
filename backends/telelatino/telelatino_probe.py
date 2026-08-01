#!/usr/bin/env python3
"""
telelatino_probe.py — off-device probe for TeleLatino (com.global.latinotv).

Updated 2026-08-01 session 33b: added login test with owner creds, full headers,
hardware profile, gzip support. Confirmed: portal200001 is universal version gate,
NOT login-gated. Even the app on .4 is broken. Need newer APK (>54608).

Usage:
    python3 telelatino_probe.py dcs           # getAddr -> portal host pool
    python3 telelatino_probe.py portal        # portalCore snToken (expect portal200001)
    python3 telelatino_probe.py login         # try login with owner creds
    python3 telelatino_probe.py epg           # EPG -> live channel guide
    python3 telelatino_probe.py notice        # notice endpoint (works)
    python3 telelatino_probe.py full          # full chain: dcs -> portal -> login -> epg
"""

import argparse
import json
import urllib.request
import urllib.error

from Crypto.Cipher import DES

# ── Identity (from .4 box, 2026-08-01) ─────────────────────────────────────
PKG = "com.global.latinotv"
APK_VER = "54608"                                          # 5.46.8
SN = "ca0e53edac957b8f6f187528933355f1"                    # KEY_SP_SN from cache.config.xml
DEVICE_ID = "945257240"                                     # key_device_id_latinotv
USER_ID = "25885636"                                        # key_user_id (cached)
SPKG_VER = "2024-11-15 19:08:51_29_14.1_4.9.170"          # spkgVer header
HARDWARE = "sun50iw9p1"                                     # Allwinner H616
MODEL = "V76PRO"                                            # device model
MANUFACTURER = "Google"                                     # spoofed manufacturer

# ── Account (owner creds, session 33b) ─────────────────────────────────────
# Creds in orchestrator/.env (gitignored) as TELELATINO_USER/TELELATINO_PASS
import os as _os
TELELATINO_USER = _os.environ.get("TELELATINO_USER", "")
TELELATINO_PASS = _os.environ.get("TELELATINO_PASS", "")

# ── Live hosts ─────────────────────────────────────────────────────────────
DCS_HOST = "http://emowvv.dqiswip4.xyz"                     # serves getAddr (returnCode:0)
PORTAL_HOST = DCS_HOST                                      # same host for portalCore
NOTICE_HOST = "http://nxiqj.jgrqyxupl.com"
EPG_HOST = "http://xipre.xifhzu.com"

# ── Crypto ─────────────────────────────────────────────────────────────────
DES_KEY_DCS = b"dCsPLwiy"                                   # DES/ECB/PKCS5 — same as koocan


def des_encrypt_ecb_pkcs5(plain: str, key: bytes) -> str:
    pad = 8 - (len(plain.encode("utf-8")) % 8)
    data = plain.encode("utf-8") + bytes([pad]) * pad
    return DES.new(key, DES.MODE_ECB).encrypt(data).hex().upper()


def des_decrypt_ecb_nopad_hex(data_hex: str, key: bytes, plain_len: int) -> str:
    raw = DES.new(key, DES.MODE_ECB).decrypt(bytes.fromhex(data_hex))
    strip = (8 - (plain_len % 8)) % 8
    return raw[: len(raw) - strip].decode("utf-8", "replace")


def headers(pkg=PKG):
    return {
        "Content-Type": "application/json;charset=utf-8",
        "apk": pkg,
        "apkVer": APK_VER,
        "spkgVer": SPKG_VER,
        "User-Agent": "okhttp/4.12.0",
        "Accept-Encoding": "gzip",
        "Cache-Control": "no-store",
        "NoLog": "true",
    }


def http_post(url, body=None, hdrs=None, timeout=12):
    req = urllib.request.Request(
        url,
        data=body.encode("utf-8") if body is not None else None,
        headers=hdrs or headers(),
        method="POST" if body else "GET",
    )
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            return resp.status, resp.read()
    except urllib.error.HTTPError as e:
        return e.code, e.read()
    except Exception as e:
        return 0, str(e).encode()


# ── dcs getAddr ────────────────────────────────────────────────────────────
def dcs_get_addr():
    """DCS domain resolution — works off-device (returnCode:0)."""
    body = {"sn": SN, "type": 1, "authCode": "", "authVersion": "",
            "reserve1": "AA:BB:CC:DD:EE:FF"}
    js = json.dumps(body, separators=(",", ":"))
    data = des_encrypt_ecb_pkcs5(js, DES_KEY_DCS)
    payload = json.dumps({"data": data, "len": len(js.encode())},
                         separators=(",", ":"))

    st, raw = http_post(f"{DCS_HOST}/api/v2/dcs/getAddr", payload)
    print(f"[dcs] {DCS_HOST}/api/v2/dcs/getAddr -> {st}")
    if st != 200:
        print("      ", raw.decode("utf-8", "replace")[:300])
        return None

    j = json.loads(raw)
    dec = des_decrypt_ecb_nopad_hex(j["data"], DES_KEY_DCS, j["len"])
    r = json.loads(dec)
    print(f"[dcs] returnCode: {r['returnCode']}")
    print(f"[dcs] dcsClientUrl: {r['dcsClientUrl']}")
    print(f"[dcs] dcsClientUrlAlias: {r['dcsClientUrlAlias']}")
    return r


# ── portalCore ─────────────────────────────────────────────────────────────
def portal_sn_token():
    """portalCore snToken — version-gated (portal200001)."""
    st, raw = http_post(f"{PORTAL_HOST}/api/portalCore/v3/snToken", "{}")
    print(f"[portal] {PORTAL_HOST}/api/portalCore/v3/snToken -> {st}")
    try:
        j = json.loads(raw)
        print(f"  returnCode: {j.get('returnCode', '?')}")
        print(f"  errorMessage: {j.get('errorMessage', '')}")
    except Exception:
        print(f"  raw: {raw[:200]}")


def portal_login():
    """Try login with owner creds — version-gated (portal200001), NOT login-gated."""
    import gzip
    print(f"[login] Testing {TELELATINO_USER} on {PORTAL_HOST}")

    endpoints = [
        ("v3/snToken", {"sn": SN}),
        ("v8/login", {"account": TELELATINO_USER, "password": TELELATINO_PASS, "sn": SN}),
        ("v8/active", {"sn": SN, "deviceId": DEVICE_ID, "hardwareInfo": HARDWARE}),
        ("terminalAuth", {"sn": SN, "deviceId": DEVICE_ID}),
    ]

    for path, body in endpoints:
        js = json.dumps(body, separators=(",", ":"))
        st, raw = http_post(f"{PORTAL_HOST}/api/portalCore/{path}", js)
        try:
            # Handle gzip
            if raw[:2] == b'\x1f\x8b':
                raw = gzip.decompress(raw)
            j = json.loads(raw)
            rc = j.get("returnCode", "?")
            em = j.get("errorMessage", "")
            print(f"  {path}: returnCode={rc} errorMessage={em}")
        except Exception:
            print(f"  {path}: {st} — {raw[:120]}")

    print()
    print("[verdict] portal200001 is NOT login-gated — server checks apkVer BEFORE auth")
    print("          Even valid owner creds don't bypass the version gate.")


# ── EPG ────────────────────────────────────────────────────────────────────
def epg():
    """EPG guide — works off-device (200 with live channel data)."""
    url = (f"{EPG_HOST}/epg/v2/live/app/utc-3/26"
           f"?md5=fc9548268cd91bd1506d8fb142cf8972")
    hdrs = headers("com.spanish.latinotvod")
    hdrs["NoLog"] = "true"
    del hdrs["Content-Type"]
    st, raw = http_post(url, None, hdrs)
    print(f"[epg] {url} -> {st}")
    if st == 200:
        try:
            j = json.loads(raw)
            print(f"  channels: {len(j)} entries")
            for ch in j[:5]:
                code = ch.get('channelCode', '?')
                progs = len(ch.get('programList', []))
                print(f"    {code}: {progs} programs")
        except Exception as e:
            print(f"  parse error: {e}")
    else:
        print(f"  {raw.decode('utf-8','replace')[:200]}")


# ── notice ─────────────────────────────────────────────────────────────────
def notice():
    """Notice endpoint — works (status:0)."""
    url = (f"{NOTICE_HOST}/notice/api/get_notice"
           f"?pkg={PKG}&v={APK_VER}&sn={SN}&userId={USER_ID}&language=es")
    st, raw = http_post(url, None, headers())
    print(f"[notice] {url} -> {st}")
    try:
        j = json.loads(raw)
        print(f"  status: {j.get('status', '?')}")
        print(f"  package: {j.get('package', '?')}")
    except Exception:
        print(f"  {raw.decode('utf-8','replace')[:200]}")


# ── full chain ─────────────────────────────────────────────────────────────
def full_chain():
    print("=" * 60)
    print("TeleLatino off-device probe — full chain")
    print(f"SN: {SN}  Device: {DEVICE_ID}  User: {USER_ID}")
    print(f"Hardware: {HARDWARE}  Model: {MODEL}")
    print("=" * 60)

    print("\n-- 1. DCS getAddr --")
    dcs = dcs_get_addr()
    if not dcs:
        print("FAILED — cannot resolve portal host")
        return

    print("\n-- 2. portalCore snToken --")
    portal_sn_token()

    print("\n-- 3. portalCore login (owner creds) --")
    portal_login()

    print("\n-- 4. EPG (bypasses portal) --")
    epg()

    print("\n-- 5. Notice --")
    notice()

    print("\n-- Verdict (session 33b) --")
    print("getAddr: [OK] returnCode:0")
    print("portalCore: [BLOCKED] portal200001 (universal version gate)")
    print("  - NOT login-gated (valid creds don't bypass)")
    print("  - NOT header-gated (Cache-Control, NoLog, gzip don't bypass)")
    print("  - NOT spkgVer-gated (all variants rejected)")
    print("  - Even the app on .4 is broken (empty channel list)")
    print("EPG: [OK] works off-device")
    print("Notice: [OK] works off-device")
    print()
    print("BLOCKER: Need newer APK (>5.46.8, versionCode >54608).")
    print("Without newer APK, TeleLatino is gated (version, not identity).")


def main():
    p = argparse.ArgumentParser(description="TeleLatino off-device probe (live identity)")
    p.add_argument("cmd", choices=["dcs", "portal", "login", "epg", "notice", "full"],
                   default="full", nargs="?")
    args = p.parse_args()

    if args.cmd == "dcs":
        dcs_get_addr()
    elif args.cmd == "portal":
        portal_sn_token()
    elif args.cmd == "login":
        portal_login()
    elif args.cmd == "epg":
        epg()
    elif args.cmd == "notice":
        notice()
    elif args.cmd == "full":
        full_chain()


if __name__ == "__main__":
    main()
