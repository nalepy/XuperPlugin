#!/usr/bin/env python3
"""Quick portalCore probe — HTTP vs HTTPS on heap-identified host."""
from __future__ import annotations

import base64
import json
import ssl
import urllib.request

KEY_B64 = "2b494e53756c664c2f44465245733572"
from Crypto.Cipher import DES3  # pycryptodome

HOST = "34fhwevf.cbcf4gg3f.com"
PATH = "/api/portalCore/v6/getLiveData"

BODY = {
    "apkVersion": "43405",
    "appId": "com.android.msandroid",
    "b29": "4f6f786b4b5a7a3933666842554e6c55717338584b71325a3635436b4e463736583442714b345572434a504c556e72384136647252773d3d",
    "reserve1": "76356c476568424f4a38334761645a697957757344673d3d",
    "sn": "ca0e53edac957b8f6f187528933355f1",
    "portalCode": "masnew",
    "userId": "169355704",
    "userToken": "1d66b674-7642-4c59-ab18-6215fbe57d94",
    "columnId": 76182,
    "dataVersion": "pre3194bfb81-899a-11f1-b41c-e7ba14321033LiveDataV6",
    "pageNum": 1,
    "pageSize": 3000,
    "model": "V76PRO",
    "product": "walley",
    "sdkVer": 29,
    "sysVersion": "2024-11-15 19:08:51_29_14.1_4.9.170",
    "lang": "es",
    "type": "1",
}


def to_hex_ascii(s: str) -> str:
    return "".join(f"{ord(c):02x}" for c in s)


def encrypt_body(plain: str) -> str:
    key = base64.b64decode(KEY_B64)
    cipher = DES3.new(key, DES3.MODE_ECB)
    pad = 8 - (len(plain.encode()) % 8)
    padded = plain.encode() + bytes([pad] * pad)
    ct = cipher.encrypt(padded)
    b64 = base64.b64encode(ct).decode("ascii")
    return to_hex_ascii(b64)


def decrypt_body(wire: str) -> str:
    hex_ascii = bytes.fromhex(wire).decode("ascii")
    ct = base64.b64decode(hex_ascii)
    key = base64.b64decode(KEY_B64)
    cipher = DES3.new(key, DES3.MODE_ECB)
    pt = cipher.decrypt(ct)
    pad = pt[-1]
    return pt[:-pad].decode("utf-8", errors="replace")


def probe(scheme: str) -> None:
    url = f"{scheme}://{HOST}{PATH}"
    wire = encrypt_body(json.dumps(BODY, separators=(",", ":")))
    req = urllib.request.Request(
        url,
        data=wire.encode("ascii"),
        method="POST",
        headers={
            "Content-Type": "application/json;charset=utf-8",
            "Accept": "*/*",
            "apkVer": "43405",
            "spkgVer": "43405",
            "apk": "com.android.msandroid",
            "User-Agent": "okhttp/4.12.0",
        },
    )
    ctx = ssl.create_default_context()
    try:
        with urllib.request.urlopen(req, timeout=30, context=ctx) as resp:
            raw = resp.read().decode("utf-8", errors="replace")
            dec = decrypt_body(raw.strip()) if raw.strip() else raw
            print(f"\n{scheme.upper()} {resp.status} len={len(raw)}")
            print(dec[:400])
    except Exception as e:
        print(f"\n{scheme.upper()} ERR: {e}")


if __name__ == "__main__":
    for s in ("http", "https"):
        probe(s)
