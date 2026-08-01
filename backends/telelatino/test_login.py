#!/usr/bin/env python3
"""
test_login.py — Test portalCore with free account login + msandroid v60203 identity.

Session 33b/33c: Free creds (nestor.ale@gmail.com / Ian20jesus) + newer msandroid APK (v6.2.3/60203).
Goal: find which combination clears portal200001 gate.

Tests:
  1. portalCore snToken with msandroid v60203 identity
  2. portalCore login with TeleLatino identity + free creds
  3. portalCore snToken with TeleLatino identity (control)
  4. Full login chain if any returns returnCode:0
"""

import argparse
import hashlib
import json
import sys
import urllib.request
import urllib.error

from Crypto.Cipher import DES

# ── Identity: TeleLatino (from .4 box) ─────────────────────────────────────
TL_PKG = "com.global.latinotv"
TL_VER = "54608"
TL_SN = "ca0e53edac957b8f6f187528933355f1"
TL_SPKG = "2024-11-15 19:08:51_29_14.1_4.9.170"
TL_DEVICE = "945257240"
TL_USER_ID = "25885636"

# ── Identity: msandroid (newer APK v6.2.3) ─────────────────────────────────
MS_PKG = "com.msandroid.mobile"
MS_VER = "60203"
MS_SPKG = "2026-05-14 00:00:00_29_14.1_4.9.170"  # guessed; real value from device
MS_SN = TL_SN  # same physical box would have same SN

# ── Account ─────────────────────────────────────────────────────────────────
# Creds in orchestrator/.env (gitignored) as TELELATINO_USER/TELELATINO_PASS
import os as _os
import sys as _sys
USER = _os.environ.get("TELELATINO_USER", "")
PASS = _os.environ.get("TELELATINO_PASS", "")
if not USER or not PASS:
    _sys.exit("ERROR: Set TELELATINO_USER and TELELATINO_PASS env vars. Source orchestrator/.env first.")

# ── Hosts ───────────────────────────────────────────────────────────────────
PORTAL_HOST = "http://emowvv.dqiswip4.xyz"
DCS_HOST = PORTAL_HOST

# ── Crypto ──────────────────────────────────────────────────────────────────
DES_KEY_DCS = b"dCsPLwiy"

# 3DES response key candidates from ASSESSMENT.md — not confirmed for TeleLatino
DES3_KEY_CANDIDATES = [
    "b940e017-cfea-4aa0-b69d-3a82b6428ed3",   # koocan resp key (f4280a)
    "c6768bbe-189f-4d9d-b35c-f235a9fd7587",   # koocan domain key (f4282c)
    "NxZZ7EYgaJiJSBHjnq7sDxYvYRm32tPQ",       # koocan resp subs
    "0e5e9c33-0000-0000-0000-000000000000",   # ASSESSMENT.md UUID candidate 1
    "20799a27-0000-0000-0000-000000000000",   # ASSESSMENT.md UUID candidate 2
    "4c087185-0000-0000-0000-000000000000",   # ASSESSMENT.md UUID candidate 3
    "629a824d-0000-0000-0000-000000000000",   # ASSESSMENT.md UUID candidate 4
    "b700bce0-0000-0000-0000-000000000000",   # ASSESSMENT.md UUID candidate 5
]


def des_encrypt_ecb_pkcs5(plain, key):
    pad = 8 - (len(plain.encode("utf-8")) % 8)
    data = plain.encode("utf-8") + bytes([pad]) * pad
    return DES.new(key, DES.MODE_ECB).encrypt(data).hex().upper()


def des_decrypt_ecb_nopad_hex(data_hex, key, plain_len):
    raw = DES.new(key, DES.MODE_ECB).decrypt(bytes.fromhex(data_hex))
    strip = (8 - (plain_len % 8)) % 8
    return raw[:len(raw) - strip].decode("utf-8", "replace")


def make_headers(pkg, ver, spkg):
    return {
        "Content-Type": "application/json;charset=utf-8",
        "apk": pkg,
        "apkVer": ver,
        "spkgVer": spkg,
        "User-Agent": "okhttp/4.12.0",
    }


def http_post(url, body=None, hdrs=None, timeout=15):
    req = urllib.request.Request(
        url,
        data=body.encode("utf-8") if body is not None else None,
        headers=hdrs,
        method="POST" if body else "GET",
    )
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            return resp.status, resp.read()
    except urllib.error.HTTPError as e:
        return e.code, e.read()
    except Exception as e:
        return 0, str(e).encode()


# ── DCS getAddr ─────────────────────────────────────────────────────────────
def dcs_get_addr(sn, pkg=TL_PKG, ver=TL_VER, spkg=TL_SPKG):
    body = {"sn": sn, "type": 1, "authCode": "", "authVersion": "",
            "reserve1": "AA:BB:CC:DD:EE:FF"}
    js = json.dumps(body, separators=(",", ":"))
    data = des_encrypt_ecb_pkcs5(js, DES_KEY_DCS)
    payload = json.dumps({"data": data, "len": len(js.encode())}, separators=(",", ":"))
    hdrs = make_headers(pkg, ver, spkg)
    st, raw = http_post(f"{DCS_HOST}/api/v2/dcs/getAddr", payload, hdrs)
    print(f"[dcs] {DCS_HOST}/api/v2/dcs/getAddr -> {st}")
    if st != 200:
        print("      ", raw.decode("utf-8", "replace")[:300])
        return None
    j = json.loads(raw)
    dec = des_decrypt_ecb_nopad_hex(j["data"], DES_KEY_DCS, j["len"])
    r = json.loads(dec)
    print(f"[dcs] returnCode: {r.get('returnCode')}")
    return r


# ── portalCore calls ────────────────────────────────────────────────────────
def portal_sn_token(host, pkg=TL_PKG, ver=TL_VER, spkg=TL_SPKG):
    """POST /api/portalCore/v3/snToken — device token request."""
    hdrs = make_headers(pkg, ver, spkg)
    st, raw = http_post(f"{host}/api/portalCore/v3/snToken", "{}", hdrs)
    print(f"[snToken] {host}/api/portalCore/v3/snToken -> {st}")
    try:
        j = json.loads(raw)
        rc = j.get("returnCode", "?")
        em = j.get("errorMessage", "")
        print(f"  returnCode: {rc}")
        if em:
            # decode from UTF-8
            try:
                print(f"  errorMessage: {em}")
            except:
                print(f"  errorMessage (bytes): {em.encode('utf-8', errors='replace')}")
        return j
    except Exception as e:
        print(f"  parse error: {e} | raw: {raw[:200]}")
    return None


def portal_login(host, user, pwd, sn, pkg=TL_PKG, ver=TL_VER, spkg=TL_SPKG):
    """POST /api/portalCore/v3/login — email/password login."""
    bean = {
        "accountType": "1",
        "areaCode": "",
        "userName": user,
        "password": pwd,
        "sn": sn,
        "type": None,
        "macAddr": "",
        "verificationCode": None,
        "verificationToken": None,
    }
    body = json.dumps(bean, separators=(",", ":"))
    hdrs = make_headers(pkg, ver, spkg)
    st, raw = http_post(f"{host}/api/portalCore/v3/login", body, hdrs)
    print(f"[login] {host}/api/portalCore/v3/login -> {st}")
    try:
        j = json.loads(raw)
        rc = j.get("returnCode", "?")
        em = j.get("errorMessage", "")
        print(f"  returnCode: {rc}")
        if em:
            print(f"  errorMessage: {em}")
        data = j.get("data", "")
        if data:
            print(f"  data present ({len(data)} chars) — needs 3DES decrypt")
        return j
    except Exception as e:
        print(f"  parse error: {e} | raw: {raw[:200]}")
    return None


def portal_active(host, sn, sn_token, pkg=TL_PKG, ver=TL_VER, spkg=TL_SPKG):
    """POST /api/portalCore/v3/active — activate device."""
    bean = {
        "sn": sn,
        "snToken": sn_token,
        "authVersion": "",
        "authCode": "",
        "macAddr": "",
        "reserve1": "",
    }
    body = json.dumps(bean, separators=(",", ":"))
    hdrs = make_headers(pkg, ver, spkg)
    st, raw = http_post(f"{host}/api/portalCore/v3/active", body, hdrs)
    print(f"[active] {host}/api/portalCore/v3/active -> {st}")
    try:
        j = json.loads(raw)
        rc = j.get("returnCode", "?")
        em = j.get("errorMessage", "")
        print(f"  returnCode: {rc}")
        if em:
            print(f"  errorMessage: {em}")
        return j
    except Exception as e:
        print(f"  parse error: {e} | raw: {raw[:200]}")
    return None


def portal_get_auth_info(host, user_token, user_id, pkg=TL_PKG, ver=TL_VER, spkg=TL_SPKG):
    """POST /api/portalCore/v3/getAuthInfo."""
    bean = {
        "userToken": user_token,
        "userId": user_id,
        "type": "1",
        "portalCode": "latinotv",
        "lang": "1",
    }
    body = json.dumps(bean, separators=(",", ":"))
    hdrs = make_headers(pkg, ver, spkg)
    st, raw = http_post(f"{host}/api/portalCore/v3/getAuthInfo", body, hdrs)
    print(f"[getAuthInfo] {host}/api/portalCore/v3/getAuthInfo -> {st}")
    try:
        j = json.loads(raw)
        rc = j.get("returnCode", "?")
        em = j.get("errorMessage", "")
        print(f"  returnCode: {rc}")
        if em:
            print(f"  errorMessage: {em}")
        return j
    except Exception as e:
        print(f"  parse error: {e} | raw: {raw[:200]}")
    return None


# ── Main test suite ─────────────────────────────────────────────────────────
def run_tests():
    print("=" * 70)
    print("TeleLatino portalCore gate tests")
    print(f"Free account: {USER}")
    print(f"TeleLatino: {TL_PKG} v{TL_VER}  new msandroid: {MS_PKG} v{MS_VER}")
    print("=" * 70)

    # ── Test 1: DCS getAddr (baseline, always works) ──
    print("\n-- Test 1: DCS getAddr (baseline) --")
    dcs_r = dcs_get_addr(TL_SN)
    if not dcs_r:
        print("FAIL: DCS unreachable — network issue")
        return

    # ── Test 2: portalCore snToken — TeleLatino identity (control) ──
    print("\n-- Test 2: snToken with TeleLatino identity (control) --")
    r = portal_sn_token(PORTAL_HOST, TL_PKG, TL_VER, TL_SPKG)
    tl_gated = r and r.get("returnCode") == "portal200001"
    if tl_gated:
        print("  -> Still portal200001 (as expected)")

    # ── Test 3: portalCore snToken — msandroid v60203 identity ──
    print("\n-- Test 3: snToken with msandroid v6.2.3 identity --")
    r = portal_sn_token(PORTAL_HOST, MS_PKG, MS_VER, MS_SPKG)
    ms_gated = r and r.get("returnCode") == "portal200001"
    if r and r.get("returnCode") == "0":
        print("  *** VERSION-GATE CLEARED by msandroid v60203! ***")
    elif ms_gated:
        print("  -> Still portal200001 (version 60203 also gated)")

    # ── Test 4: portalCore login — TeleLatino identity with free creds ──
    print(f"\n-- Test 4: login with free account (TeleLatino identity) --")
    r = portal_login(PORTAL_HOST, USER, PASS, TL_SN, TL_PKG, TL_VER, TL_SPKG)
    if r and r.get("returnCode") == "0":
        print("  *** LOGIN SUCCESS with free account! ***")
    elif r and r.get("returnCode") == "portal200001":
        print("  -> portal200001 (login also version-gated)")
    elif r:
        print(f"  -> returnCode: {r.get('returnCode')}")

    # ── Test 5: portalCore login — msandroid identity with free creds ──
    print(f"\n-- Test 5: login with msandroid v60203 + free creds --")
    r = portal_login(PORTAL_HOST, USER, PASS, MS_SN, MS_PKG, MS_VER, MS_SPKG)
    if r and r.get("returnCode") == "0":
        print("  *** LOGIN SUCCESS with msandroid + free creds! ***")
    elif r and r.get("returnCode") == "portal200001":
        print("  -> portal200001 (both version-gated)")
    elif r:
        print(f"  -> returnCode: {r.get('returnCode')}")

    # ── Test 6: Try portalCore snToken WITHOUT spkgVer header ──
    print(f"\n-- Test 6: snToken without spkgVer header --")
    hdrs = make_headers(TL_PKG, TL_VER, "")
    st, raw = http_post(f"{PORTAL_HOST}/api/portalCore/v3/snToken", "{}", hdrs)
    print(f"[snToken-no-spkg] -> {st}")
    try:
        j = json.loads(raw)
        print(f"  returnCode: {j.get('returnCode', '?')}")
    except:
        print(f"  raw: {raw[:200]}")

    # ── Test 7: Try portalCore snToken with OTHER koocan version ──
    print(f"\n-- Test 7: snToken with koocan identity v2.14.8 --")
    r = portal_sn_token(PORTAL_HOST, "com.integration.unitviptv", "21408",
                        "2018-12-18 15:24:39_5.1.1_3.14.29")
    if r and r.get("returnCode") != "portal200001":
        print(f"  -> returnCode: {r.get('returnCode', '?')} (different from portal200001!)")

    # ── Summary ──
    print("\n" + "=" * 70)
    print("SUMMARY")
    print("=" * 70)
    print("If ANY test above returned returnCode:0, the gate is BEATABLE.")
    print("If ALL returned portal200001, need: newer APK OR correct spkgVer.")
    print("If login returned a different error (not portal200001),")
    print("  that endpoint is un-gated — check credentials.")


if __name__ == "__main__":
    run_tests()
