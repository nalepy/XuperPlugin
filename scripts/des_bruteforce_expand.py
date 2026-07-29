#!/usr/bin/env python3
"""
Expanded DES-ECB/PKCS5 key search for domain|DES blobs (notice host).
On hit: print host and optionally probe notice + portalCore paths.
"""
from __future__ import annotations

import argparse
import base64
import hashlib
import itertools
import re
import ssl
import sys
import urllib.error
import urllib.request
from pathlib import Path

from Crypto.Cipher import DES, DES3

ROOT = Path(__file__).resolve().parent.parent

BLOBS: dict[str, bytes] = {
    "B1_50012": base64.b64decode("Sz0JjjU4YRgGRpH1paF7wlkgQ43Df/4y"),
    "B2_403": base64.b64decode("4hv+FZGcrdsJh3Y7+zl8w1kgQ43Df/4y"),
    "B3_403": base64.b64decode("MP5TBkYzwo1YVMusyj8vxlkgQ43Df/4y"),
}

LAST_BLOCK_CT = bytes.fromhex("5920438dc37ffe32")
KEY_B64_3DES = "2b494e53756c664c2f44465245733572"

BIN_8 = [
    ("bin_fc96f3", bytes.fromhex("fc96f36d4e61dba7")),
    ("bin_06a066", bytes.fromhex("06a0663fc4e8eff9")),
    ("bin_6f376d", bytes.fromhex("6f376d91fe84f373")),
    ("bin_301101", bytes.fromhex("30110175f1bf0980")),
    ("bin_545866", bytes.fromhex("5458669a4c39daaf")),
    ("cfg_portal", bytes.fromhex("9d3e68bf02d358ad")),
    ("cfg_account", bytes.fromhex("2609dd20aa5d6331")),
    ("body_K1", bytes.fromhex("d9be3de1ee77ef9e")),
    ("body_K2", bytes.fromhex("9cebae1cd9fe38e3")),
    ("body_K3", bytes.fromhex("ae76e39ef7df9ef6")),
    ("ijiami_0", bytes.fromhex("1efea263c3e0665d")),
    ("ijiami_8", bytes.fromhex("9c12a40030110175")),
    ("ijiami_16", bytes.fromhex("f1bf09805458669a")),
    ("ijiami_24", bytes.fromhex("4c39daaf8bb043f1")),
]

SN = bytes.fromhex("ca0e53edac957b8f6f187528933355f1")
B29_HEX = (
    "4f6f786b4b5a7a3933666842554e6c55717338584b71325a3635436b4e463736583442714b"
    "345572434a504c556e72384136647252773d3d"
)
RESERVE1_HEX = "76356c476568424f4a38334761645a697957757344673d3d"
PORTAL_HEX_MGSTV = "6e54356f76774c54574b303d"  # ASCII hex of nT5ovwLTWK0=


def pkcs5_unpad(data: bytes) -> bytes | None:
    if not data:
        return None
    pad = data[-1]
    if pad < 1 or pad > 8 or len(data) < pad:
        return None
    if data[-pad:] != bytes([pad]) * pad:
        return None
    return data[:-pad]


def looks_hostname(pt: bytes) -> bool:
    body = pkcs5_unpad(pt)
    if body is None:
        return False
    if not body.endswith(b".com"):
        return False
    if not all(32 <= c < 127 for c in body):
        return False
    host = body.decode("ascii", errors="ignore")
    if not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9.\-]{2,253}\.com", host):
        return False
    return True


def add_key(keys: dict[bytes, str], key: bytes, name: str) -> None:
    if len(key) != 8:
        return
    if key not in keys:
        keys[key] = name


def hash_slices(data: bytes, label: str, keys: dict[bytes, str]) -> None:
    for alg in ("md5", "sha1", "sha256"):
        h = hashlib.new(alg, data).digest()
        for i in range(0, min(len(h), 24), 8):
            add_key(keys, h[i : i + 8], f"{label}_{alg}_{i // 8}")


def build_candidates() -> dict[bytes, str]:
    keys: dict[bytes, str] = {}

    for name, k in BIN_8:
        add_key(keys, k, name)
        add_key(keys, k[::-1], f"{name}_rev")

    for name, k in BIN_8:
        for name2, k2 in BIN_8:
            if name >= name2:
                continue
            add_key(keys, bytes(a ^ b for a, b in zip(k, k2)), f"xor_{name}_{name2}")

    ascii_strings = [
        "noticeap",
        "get_noti",
        "get_notice",
        "/notice/api/get_notice",
        "portalCo",
        "portalCore",
        "getSlbInfo",
        "masnew",
        "masnew!!",
        "com.andr",
        "com.android.mgstv",
        "com.android.msandroid",
        "msandroid",
        "mgstv",
        "Ian20jesus",
        "V76PRO",
        "walley",
        "43405",
        "169355704",
        "nestor.ale@gmail.com",
        "domain|DES",
        "domainDES",
        "needEncrypt",
        "MAC_DES_KEY",
        "DESede",
        "DESede/ECB/PKCS5Padding",
        "Sz0JjjU4",
        "kgQ43Df/4y",
        "50012",
        "403",
        "app_api",
        "EventDbModel",
        "BBDatabase",
        "nT5ovwLTWK0=",
        "JgndIKpdYzE=",
        "6e54356f76774c54574b303d",
        "2b494e53756c664c2f44465245733572",
        "+INSulfL/DFREs5r",
        "BrazilTV",
        "brasiltv",
        "combrasiltv",
        "combrasiltvaslgklxckbcombrasiltv",
        "xuper",
        "com.xuper.plugin",
        "spkgVer",
        "apkVer",
        "userToken",
        "portalCode",
        "snToken",
        "getAuthInfo",
        "getLiveData",
        "getPropertiesInfo",
        "ijiami",
        "ijiami.dat",
        "libexec.so",
        "nb.b",
        "rd.c",
        "lb.a",
        "lb.b",
        "ca0e53edac957b8f6f187528933355f1",
        "1d66b674-7642-4c59-ab18-6215fbe57d94",
        "6da3c458-b2de-4798-86a7-57028fb25b27",
    ]

    for s in ascii_strings:
        b = s.encode("utf-8")
        add_key(keys, b[:8].ljust(8, b"\0"), f"ascii8_{s[:24]}")
        hash_slices(b, f"str_{s[:20]}", keys)

    for i in range(4):
        add_key(keys, SN[i * 8 : (i + 1) * 8], f"sn_chunk_{i}")

    b29 = bytes.fromhex(B29_HEX)
    r1 = bytes.fromhex(RESERVE1_HEX)
    for label, blob in [("b29", b29), ("reserve1", r1)]:
        add_key(keys, blob[:8], f"{label}_0")
        add_key(keys, blob[8:16], f"{label}_1")
        hash_slices(blob, label, keys)
        try:
            inner = base64.b64decode(blob)
            add_key(keys, inner[:8].ljust(8, b"\0"), f"{label}_b64_0")
            hash_slices(inner, f"{label}_inner", keys)
        except Exception:
            pass

    try:
        portal_ascii = bytes.fromhex(PORTAL_HEX_MGSTV)
        add_key(keys, portal_ascii[:8], "portal_hex_ascii8")
        hash_slices(portal_ascii, "portal_hex", keys)
        portal_bin = base64.b64decode(portal_ascii)
        add_key(keys, portal_bin[:8].ljust(8, b"\0"), "portal_b64dec8")
        hash_slices(portal_bin, "portal_b64dec", keys)
    except Exception:
        pass

    body3 = base64.b64decode(KEY_B64_3DES)
    for i in range(3):
        add_key(keys, body3[i * 8 : (i + 1) * 8], f"wire3des_{i}")

    common = [
        b"12345678",
        b"password",
        b"00000000",
        b"FFFFFFFF",
        b"abcdefgh",
        b"87654321",
    ]
    for i, k in enumerate(common):
        add_key(keys, k, f"common_{i}")

    # 4-byte chunk combinations from binary patterns
    chunks: list[tuple[str, bytes]] = []
    for name, val in BIN_8:
        for off in range(0, max(1, len(val) - 3)):
            chunks.append((f"{name}[{off}]", val[off : off + 4]))

    for (n1, c1), (n2, c2) in itertools.product(chunks, repeat=2):
        if len(c1) == 4 and len(c2) == 4:
            add_key(keys, c1 + c2, f"chunk_{n1}_{n2}")

    return keys


def last_block_filter(key: bytes) -> bool:
    suffixes = [
        b".com\x04\x04\x04\x04",
        b"com\x05\x05\x05\x05\x05",
        b"om\x06\x06\x06\x06\x06\x06",
        b"m\x07\x07\x07\x07\x07\x07\x07",
        b".com\x03\x03\x03",
        b"om.com\x02\x02",
    ]
    cipher = DES.new(key, DES.MODE_ECB)
    for suf in suffixes:
        if len(suf) != 8:
            continue
        if cipher.encrypt(suf) == LAST_BLOCK_CT:
            return True
    return False


def pkcs5_pad(data: bytes) -> bytes:
    pad = 8 - (len(data) % 8)
    return data + bytes([pad] * pad)


KNOWN_HOST_GUESSES = [
    "nxiqj.jgrqyxupl.com",
    "zxiws.tcgwhnvym.com",
    "ioermd.l7hsgo8g.com",
    "34fhwevf.cbcf4gg3f.com",
]


def encrypt_match_keys(keys: dict[bytes, str], ct: bytes) -> list[tuple[str, str]]:
    out: list[tuple[str, str]] = []
    for host in KNOWN_HOST_GUESSES:
        if not host.endswith(".com"):
            continue
        pt = pkcs5_pad(host.encode("ascii"))
        if len(pt) != len(ct):
            continue
        for key, name in keys.items():
            enc = DES.new(key, DES.MODE_ECB).encrypt(pt)
            if enc == ct:
                out.append((name, host))
    return out


def decrypt_hit(key: bytes, ct: bytes) -> bytes | None:
    pt = DES.new(key, DES.MODE_ECB).decrypt(ct)
    if looks_hostname(pt):
        return pkcs5_unpad(pt)
    return None


def to_hex_ascii(s: str) -> str:
    return "".join(f"{ord(c):02x}" for c in s)


def encrypt_body_3des(plain: str) -> str:
    key = base64.b64decode(KEY_B64_3DES)
    cipher = DES3.new(key, DES3.MODE_ECB)
    raw = plain.encode("utf-8")
    pad = 8 - (len(raw) % 8)
    padded = raw + bytes([pad] * pad)
    ct = cipher.encrypt(padded)
    b64 = base64.b64encode(ct).decode("ascii")
    return to_hex_ascii(b64)


def probe_host(host: str, timeout: float = 25.0) -> None:
    import json

    notice_paths = [
        ("GET", f"/notice/api/get_notice?pkg=com.android.mgstv&v=43405", None),
        ("GET", f"/notice/api/get_notice?pkg=com.android.msandroid&v=43405", None),
        ("POST", "/notice/api/get_notice", json.dumps({"pkg": "com.android.mgstv", "v": "43405"})),
    ]
    portal_body = {
        "apkVersion": "43405",
        "appId": "com.android.msandroid",
        "b29": B29_HEX,
        "reserve1": RESERVE1_HEX,
        "sn": SN.hex(),
        "portalCode": "masnew",
        "userId": "169355704",
        "userToken": "1d66b674-7642-4c59-ab18-6215fbe57d94",
        "model": "V76PRO",
        "product": "walley",
        "sdkVer": 29,
        "sysVersion": "2024-11-15 19:08:51_29_14.1_4.9.170",
        "lang": "es",
    }
    portal_paths = [
        ("POST", "/api/portalCore/v15/getSlbInfo", json.dumps(portal_body, separators=(",", ":"))),
        ("POST", "/api/portalCore/v9/getAuthInfo", json.dumps(portal_body, separators=(",", ":"))),
    ]

    ctx = ssl.create_default_context()
    for scheme in ("https", "http"):
        for method, path, body in notice_paths + portal_paths:
            url = f"{scheme}://{host}{path}"
            headers = {
                "User-Agent": "okhttp/4.12.0",
                "Accept": "*/*",
            }
            data = None
            if body is not None:
                if path.startswith("/api/portalCore"):
                    wire = encrypt_body_3des(body)
                    data = wire.encode("ascii")
                    headers["Content-Type"] = "application/json;charset=utf-8"
                    headers["apkVer"] = "43405"
                    headers["spkgVer"] = "43405"
                    headers["apk"] = "com.android.msandroid"
                else:
                    data = body.encode("utf-8")
                    headers["Content-Type"] = "application/json;charset=utf-8"
            req = urllib.request.Request(url, data=data, method=method, headers=headers)
            try:
                with urllib.request.urlopen(req, timeout=timeout, context=ctx) as resp:
                    code = resp.getcode()
                    snippet = resp.read(200)
            except urllib.error.HTTPError as e:
                code = e.code
                snippet = e.read(200) if e.fp else b""
            except Exception as e:
                print(f"  {scheme} {method} {path}: ERR {e}")
                continue
            print(f"  {scheme} {method} {path}: HTTP {code} body={snippet[:80]!r}")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--probe", action="store_true", help="Probe host on hit")
    parser.add_argument("--fast-last-block", action="store_true", help="Pre-filter by last block")
    args = parser.parse_args()

    keys = build_candidates()
    print(f"candidate_keys={len(keys)}")

    for blob_name, ct in BLOBS.items():
        for name, host in encrypt_match_keys(keys, ct):
            print(f"ENCRYPT_MATCH key={name} blob={blob_name} host={host}")

    hits: list[tuple[str, bytes, str, str]] = []
    tested = 0
    last_block_pass = 0

    for key, name in keys.items():
        if args.fast_last_block and not last_block_filter(key):
            tested += 1
            continue
        if args.fast_last_block:
            last_block_pass += 1
        for blob_name, ct in BLOBS.items():
            tested += 1
            host_b = decrypt_hit(key, ct)
            if host_b:
                host = host_b.decode("ascii")
                hits.append((name, key, blob_name, host))

    print(f"tests_run={tested} last_block_pass={last_block_pass}")
    if not hits:
        print("RESULT=MISS")
        return 1

    seen_hosts: set[str] = set()
    for name, key, blob_name, host in hits:
        print(f"HIT key={name} hex={key.hex()} blob={blob_name} host={host}")
        if host not in seen_hosts:
            seen_hosts.add(host)
            if args.probe:
                print(f"PROBE {host}:")
                probe_host(host)
    print("RESULT=HIT")
    return 0


if __name__ == "__main__":
    sys.exit(main())
