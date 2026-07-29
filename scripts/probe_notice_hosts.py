#!/usr/bin/env python3
"""Follow-up: notice host probe + DES pad check for zxiws/nxiqj."""
from __future__ import annotations

import base64
import ssl
import urllib.error
import urllib.request

from Crypto.Cipher import DES

BLOB = base64.b64decode("Sz0JjjU4YRgGRpH1paF7wlkgQ43Df/4y")
print("blob len", len(BLOB), "last8", BLOB[-8:].hex())

for host in ["zxiws.tcgwhnvym.com", "nxiqj.jgrqyxupl.com"]:
    pt = host.encode()
    pad = 8 - (len(pt) % 8)
    padded = pt + bytes([pad] * pad)
    print(f"{host}: len={len(pt)} pad={pad} lastPT={padded[-8]!r}")

sn = "ca0e53edac957b8f6f187528933355f1"
uid = "169355704"
ctx = ssl.create_default_context()
for h in ["zxiws.tcgwhnvym.com", "nxiqj.jgrqyxupl.com"]:
    path = f"/notice/api/get_notice?pkg=com.android.mgstv&v=43405&sn={sn}&userId={uid}&language=es"
    for scheme in ("http", "https"):
        url = f"{scheme}://{h}{path}"
        try:
            req = urllib.request.Request(
                url,
                headers={
                    "User-Agent": "okhttp/4.12.0",
                    "Accept": "*/*",
                    "apkVer": "43405",
                    "spkgVer": "2024-11-15 19:08:51_29_14.1_4.9.170",
                    "apk": "com.android.msandroid",
                },
            )
            with urllib.request.urlopen(req, timeout=15, context=ctx) as resp:
                body = resp.read()[:200]
                print(f"{scheme} {h} -> {resp.status} {body!r}")
        except urllib.error.HTTPError as e:
            print(f"{scheme} {h} -> HTTP {e.code} {e.read()[:120]!r}")
        except Exception as e:
            print(f"{scheme} {h} -> {type(e).__name__}: {e}")
