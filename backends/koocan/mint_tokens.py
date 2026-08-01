#!/usr/bin/env python3
"""mint_tokens.py — reproduce the UniTV b29 / reserve1 device token blobs.

Verified against the box's /sdcard/.properties store (2026-08-01):
  key_sn_token      = 34766752414c4541383048356d56395258444d74763438762f6b76493437646f746c693645506d2f6962372b665539646769704768513d3d
  key_device_id     = 62746263564f4562364c4a4e6f56664f537139324c673d3d

Format (confirmed by decryption):
  b29      = hex( base64( 3DES-ECB-PKCS5( SN, props_key ) ) )     -> 88 hex chars (40 ciphertext bytes)
  reserve1 = hex( base64( 3DES-ECB-PKCS5( userId, props_key ) ) ) -> 56 hex chars (16 ciphertext bytes)

  props_key = base64decode("2b494e53756c774c2f44465245733572")   (24 bytes; note: '6c77' = "lw",
              the .properties store key, ONE byte off the request-body key "2b494e53756c664c..."
              which has '6c66' = "lf").

IMPORTANT (session finding): these blobs are STATIC per device. The app re-writes them
unchanged on every launch. A fresh off-device replay of (static blobs + FRESH userToken +
exact TLS/h2) STILL gets {"returnCode":"portal200001"} — the gate additionally requires the
native Titan-Ranger DoHttpSec connection identity (see the session report). This encoder is
therefore necessary-but-not-sufficient for an off-device client.
"""
import base64
import binascii
import sys
from Crypto.Cipher import DES3
from Crypto.Util.Padding import pad

PROPS_KEY_B64 = "2b494e53756c774c2f44465245733572"
BODY_KEY_B64 = "2b494e53756c664c2f44465245733572"

REAL_SN = "147107feb03d65bf30773f8b604642cb"
REAL_UID = "563086836"

# Ground-truth .properties values (for verification)
EXPECTED_B29_HEX = "34766752414c4541383048356d56395258444d74763438762f6b76493437646f746c693645506d2f6962372b665539646769704768513d3d"
EXPECTED_R1_HEX = "62746263564f4562364c4a4e6f56664f537139324c673d3d"


def mint(plaintext: str, key_b64: str = PROPS_KEY_B64) -> str:
    """Return hex(base64(3DES-ECB-PKCS5(plaintext))) — the wire/token format."""
    key = base64.b64decode(key_b64)
    if len(key) != 24:
        raise ValueError(f"key must decode to 24 bytes, got {len(key)}")
    cipher = DES3.new(key, DES3.MODE_ECB)
    ct = cipher.encrypt(pad(plaintext.encode("utf-8"), 8))
    return binascii.hexlify(base64.b64encode(ct)).decode("ascii")


def unmint(token_hex: str, key_b64: str = PROPS_KEY_B64) -> bytes:
    """Inverse: token_hex -> plaintext bytes."""
    key = base64.b64decode(key_b64)
    cipher = DES3.new(key, DES3.MODE_ECB)
    raw = base64.b64decode(binascii.unhexlify(token_hex))
    return cipher.decrypt(raw)


def main():
    sn = sys.argv[1] if len(sys.argv) > 1 else REAL_SN
    uid = sys.argv[2] if len(sys.argv) > 2 else REAL_UID
    b29 = mint(sn)
    r1 = mint(uid)
    print(f"SN       = {sn}")
    print(f"userId   = {uid}")
    print(f"b29      = {b29}")
    print(f"reserve1 = {r1}")
    print()
    print(f"b29 matches .properties : {b29 == EXPECTED_B29_HEX}")
    print(f"reserve1 matches store  : {r1 == EXPECTED_R1_HEX}")
    print()
    print("decrypted b29 (check)   :", unmint(b29).rstrip(b"\x00").decode(errors="replace"))
    print("decrypted reserve1 check:", unmint(r1).rstrip(b"\x00").decode(errors="replace"))


if __name__ == "__main__":
    main()
