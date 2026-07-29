#!/usr/bin/env python3
"""Try notice-context DES keys against domain|DES blob Sz0J..."""
from __future__ import annotations

import base64
import hashlib
from Crypto.Cipher import DES

BLOB = base64.b64decode("Sz0JjjU4YRgGRpH1paF7wlkgQ43Df/4y")
assert len(BLOB) % 8 == 0

# Known: last 8 ciphertext bytes XOR should equal ".com\\x04\\x04\\x04\\x04" under DES-ECB
SUFFIX = b".com" + bytes([4, 4, 4, 4])


def looks_ok(pt: bytes) -> bool:
    if not pt.endswith(SUFFIX) and not pt.endswith(b".com"):
        # check PKCS5
        pad = pt[-1]
        if 1 <= pad <= 8 and pt.endswith(bytes([pad]) * pad):
            body = pt[:-pad]
            return b"." in body and all(32 <= c < 127 for c in body)
        return False
    body = pt
    pad = pt[-1]
    if 1 <= pad <= 8 and pt.endswith(bytes([pad]) * pad):
        body = pt[:-pad]
    return all(32 <= c < 127 for c in body) and b"." in body


def try_key(name: str, key: bytes) -> None:
    key = key[:8]
    if len(key) < 8:
        key = key.ljust(8, b"\0")
    try:
        pt = DES.new(key, DES.MODE_ECB).decrypt(BLOB)
    except Exception as e:
        print(f"FAIL {name}: {e}")
        return
    if looks_ok(pt) or all(32 <= c < 127 for c in pt):
        print(f"HIT? {name} key={key.hex()} pt={pt!r}")


candidates: list[tuple[str, bytes]] = []
# notice / portal related ASCII
for s in [
    "noticeap",
    "get_noti",
    "portalCo",
    "masnew!!",
    "masnew\0\0",
    "com.andr",
    "msandroi",
    "16935570",
    "nestor.a",
    "Ian20jes",
    "V76PRO!!",
    "walley!!",
    "43405!!!",
    "domain|D",
    "domainDES",
    "Sz0JjjU4",
]:
    candidates.append((s, s.encode()[:8]))

# hashes
for label, raw in [
    ("md5_notice", b"notice"),
    ("md5_get_notice", b"/notice/api/get_notice"),
    ("md5_masnew", b"masnew"),
    ("md5_sn", b"ca0e53edac957b8f6f187528933355f1"),
    ("md5_userid", b"169355704"),
    ("md5_appid", b"com.android.msandroid"),
]:
    h = hashlib.md5(raw).digest()
    candidates.append((label + "_0", h[:8]))
    candidates.append((label + "_1", h[8:]))

# sn bytes
sn = bytes.fromhex("ca0e53edac957b8f6f187528933355f1")
candidates.append(("sn0", sn[:8]))
candidates.append(("sn1", sn[8:16]))
candidates.append(("sn2", sn[16:24]))
candidates.append(("sn3", sn[24:32]))

# body 3DES subkeys already tried - include again for completeness
body = bytes.fromhex("d9be3de1ee77ef9e9cebae1cd9fe38e3ae76e39ef7df9ef6")
candidates.append(("body0", body[:8]))

print(f"testing {len(candidates)} keys, blob len={len(BLOB)}")
for name, key in candidates:
    try_key(name, key)
print("done")
