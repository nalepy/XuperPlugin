#!/usr/bin/env python3
"""
Exhaustive DES key derivation: XOR combinations, hashes, substrings.
Tests every derivable 8-byte key against the known-plaintext approach.
"""

import hashlib
import base64
import itertools
import struct
from Crypto.Cipher import DES

LAST_BLOCK_CT = bytes.fromhex("5920438dc37ffe32")
BLOB1 = base64.b64decode("Sz0JjjU4YRgGRpH1paF7wlkgQ43Df/4y")
BLOB2 = base64.b64decode("4hv+FZGcrdsJh3Y7+zl8w1kgQ43Df/4y")
BLOB3 = base64.b64decode("MP5TBkYzwo1YVMusyj8vxlkgQ43Df/4y")

# ─── Known strings from the app ───
KNOWN_STRINGS = [
    "combrasiltvaslgklxckbcombrasiltv",  # F0 AES key
    "combrasiltv",
    "brasiltv",
    "com.android.mgstv",  # package name
    "mgstv",
    "Xuper",
    "com.xuper.plugin",
    "2b494e53756c664c2f44465245733572",  # body 3DES key base64
    "+INSulfL/DFREs5r",  # body 3DES key decoded
    "DESede",
    "DESede/ECB/PKCS5Padding",
    "ijiami",
    "ijiami.dat",
    "portalCore",
    "getAuthInfo",
    "getSlbInfo",
    "snToken",
    "domain|DES",
    "needEncrypt",
    "nb.b",
    "rd.c",
    "lb.a",
    "lb.b",
    # F0 fallback ANDROID_ID (hex)
    "4b4d354a69546a7636736d2f73776a2b705834316d3874536576774470327448",
    "KM5JiTjv6sm/swj+pX41m8tSevwDp2tH",  # base64 decoded F0 fallback
    # MAC_DES_KEY from heap
    "MAC_DES_KEY",
    # portal_code / account_type
    "nT5ovwLTWK0=",  # portal_code base64
    "JgndIKpdYzE=",  # account_type base64
    "SK",
    "BrazilTV",
    "braziltv",
    "xuper",
]

# ─── Binary patterns from libexec.so ───
BIN_PATTERNS = {
    "fc96f3": bytes.fromhex("fc96f36d4e61dba7"),
    "06a066": bytes.fromhex("06a0663fc4e8eff9"),
    "6f376d": bytes.fromhex("6f376d91fe84f373"),
    "301101": bytes.fromhex("30110175f1bf0980"),
    "545866": bytes.fromhex("5458669a4c39daaf"),
    "ijiami_hdr_0_8": bytes.fromhex("1efea263c3e0665d"),
    "ijiami_hdr_8_16": bytes.fromhex("9c12a40030110175"),
    "ijiami_hdr_16_24": bytes.fromhex("f1bf09805458669a"),
    "ijiami_hdr_24_32": bytes.fromhex("4c39daaf8bb043f1"),
}

# ─── Deserialize to bytes ───
STR_BYTES = {s: s.encode('utf-8') for s in KNOWN_STRINGS[:40]}  # limit to avoid memory explosion

def make_key_8bytes(data):
    """Derive an 8-byte DES key from data by truncating or hashing."""
    # Truncate to 8
    if len(data) >= 8:
        return data[:8]
    # Pad with zeros
    return data + b'\x00' * (8 - len(data))

def hash_8bytes(data, alg='md5'):
    h = hashlib.new(alg, data).digest()
    return h[:8]

def xor_bytes(a, b):
    return bytes(x ^ y for x, y in zip(a, b))

def test_key(key_bytes, key_name):
    """Test if this key decrypts the last block to a known suffix pattern."""
    if len(key_bytes) != 8:
        return False

    # Test suffixes
    suffixes = [
        (".com\x04\x04\x04\x04", "L20_com"),
        ("com\x05\x05\x05\x05\x05", "L19_com"),
        ("om\x06\x06\x06\x06\x06\x06", "L18_com"),
        ("m\x07\x07\x07\x07\x07\x07\x07", "L17_com"),
        (".xyz\x04\x04\x04\x04", "L20_xyz"),
        (".com\x00\x00\x00\x00", "NoPad_com"),
    ]

    for suffix_bytes, suffix_name in suffixes:
        try:
            cipher = DES.new(key_bytes, DES.MODE_ECB)
            ct = cipher.encrypt(suffix_bytes.encode('ascii') if suffix_name.startswith("L") else suffix_bytes)
            if ct == LAST_BLOCK_CT:
                return True, suffix_name
        except:
            pass
    return False, None

def pad_pkcs5(data, block_size=8):
    pad_len = block_size - (len(data) % block_size)
    return data + bytes([pad_len] * pad_len)

print("=" * 90)
print("DERIVED KEY TESTING")
print("=" * 90)

tested = 0
hits = []

# 1. Test all string-derived keys
print("\n--- String-derived keys ---")
for s in KNOWN_STRINGS:
    b = s.encode('utf-8')

    # Truncated
    key = make_key_8bytes(b)
    matched, suffix = test_key(key, f"trunc_{s[:20]}")
    if matched:
        hits.append((f"str_trunc_{s}", key.hex(), suffix))

    # MD5
    key = hash_8bytes(b, 'md5')
    matched, suffix = test_key(key, f"md5_{s[:20]}")
    if matched:
        hits.append((f"str_md5_{s}", key.hex(), suffix))

    # SHA1
    key = hash_8bytes(b, 'sha1')
    matched, suffix = test_key(key, f"sha1_{s[:20]}")
    if matched:
        hits.append((f"str_sha1_{s}", key.hex(), suffix))

    # SHA256
    key = hash_8bytes(b, 'sha256')
    matched, suffix = test_key(key, f"sha256_{s[:20]}")
    if matched:
        hits.append((f"str_sha256_{s}", key.hex(), suffix))

    tested += 4

# 2. XOR all binary patterns with each other
print("\n--- XOR of binary patterns ---")
pattern_names = list(BIN_PATTERNS.keys())
pattern_vals = list(BIN_PATTERNS.values())

for i in range(len(pattern_vals)):
    for j in range(i, len(pattern_vals)):
        a_val = pattern_vals[i]
        b_val = pattern_vals[j]
        if len(a_val) < 8 or len(b_val) < 8:
            continue
        xored = xor_bytes(a_val[:8], b_val[:8])
        matched, suffix = test_key(xored, f"xor_{pattern_names[i]}_{pattern_names[j]}")
        if matched:
            hits.append((f"bin_xor_{pattern_names[i]}_{pattern_names[j]}", xored.hex(), suffix))
        tested += 1

# 3. XOR patterns with themselves shifted
print("\n--- Self-XOR rotated patterns ---")
for name, val in BIN_PATTERNS.items():
    if len(val) < 8:
        continue
    for shift in range(1, 8):
        rotated = val[-shift:] + val[:-shift]
        xored = xor_bytes(val, rotated)
        matched, suffix = test_key(xored, f"selfxor_{name}_shift{shift}")
        if matched:
            hits.append((f"bin_selfxor_{name}_shift{shift}", xored.hex(), suffix))
        tested += 1

# 4. XOR binary patterns with constants
print("\n--- XOR with constants ---")
constants = {
    "0xff": b'\xff' * 8,
    "0x55": b'\x55' * 8,
    "0xaa": b'\xaa' * 8,
    "DEADBEEF": b'\xde\xad\xbe\xef' * 2,
    "CAFEBABE": b'\xca\xfe\xba\xbe' * 2,
    "01234567": b'\x00\x11\x22\x33\x44\x55\x66\x77',
    "DES_key_": b'DES_key_',
    "domainDE": b'domainDE',
}

for const_name, const_val in constants.items():
    for pname, pval in BIN_PATTERNS.items():
        if len(pval) < 8:
            continue
        xored = xor_bytes(pval[:8], const_val)
        matched, suffix = test_key(xored, f"xor_{pname}_{const_name}")
        if matched:
            hits.append((f"bin_xor_{pname}_{const_name}", xored.hex(), suffix))
        tested += 1

# 5. XOR ijiami header sub-blocks with each other
print("\n--- XOR ijiami header sub-blocks ---")
hdr_names = ["ijiami_hdr_0_8", "ijiami_hdr_8_16", "ijiami_hdr_16_24", "ijiami_hdr_24_32"]
hdr_vals = [BIN_PATTERNS[n] for n in hdr_names]

for i in range(len(hdr_vals)):
    for j in range(i+1, len(hdr_vals)):
        xored = xor_bytes(hdr_vals[i], hdr_vals[j])
        matched, suffix = test_key(xored, f"hdr_xor_{hdr_names[i]}_{hdr_names[j]}")
        if matched:
            hits.append((f"hdr_xor_{hdr_names[i]}_{hdr_names[j]}", xored.hex(), suffix))
        tested += 1

# Also XOR header with trailing pattern
trailing = BIN_PATTERNS["06a066"]
for name in hdr_names:
    xored = xor_bytes(BIN_PATTERNS[name], trailing)
    matched, suffix = test_key(xored, f"hdr_xor_{name}_06a066")
    if matched:
        hits.append((f"hdr_xor_{name}_06a066", xored.hex(), suffix))
    tested += 1

# 6. Try NOT of binary patterns
print("\n--- NOT of binary patterns ---")
for name, val in BIN_PATTERNS.items():
    if len(val) < 8:
        continue
    notted = bytes(~b & 0xff for b in val)
    matched, suffix = test_key(notted, f"not_{name}")
    if matched:
        hits.append((f"bin_not_{name}", notted.hex(), suffix))
    tested += 1

# 7. Concatenate key chunks
print("\n--- Concatenated chunks ---")
# Try all 4-byte combinations of binary patterns
chunks_4byte = []
for name, val in BIN_PATTERNS.items():
    for offset in range(0, len(val) - 3):
        chunks_4byte.append((f"{name}[{offset}:{offset+4}]", val[offset:offset+4]))

for (n1, c1), (n2, c2) in itertools.product(chunks_4byte, repeat=2):
    if len(c1) == 4 and len(c2) == 4:
        combined = c1 + c2
        matched, suffix = test_key(combined, f"comb_{n1}_{n2}")
        if matched:
            hits.append((f"bin_comb_{n1}_{n2}", combined.hex(), suffix))
        tested += 1
        if tested > 50000:
            break

# 8. Try full-blob decryption for candidates that pass last-block test
print(f"\n{'='*90}")
print(f"Results: {len(hits)} potential keys found out of {tested} tested")
print(f"{'='*90}")

if hits:
    print("\nVERIFYING CANDIDATES (full blob decryption):")
    for key_name, key_hex, suffix in hits:
        key = bytes.fromhex(key_hex)
        cipher = DES.new(key, DES.MODE_ECB)

        for blob_name, blob_data in [("BLOB1_50012", BLOB1), ("BLOB2_403", BLOB2), ("BLOB3_403", BLOB3)]:
            pt = cipher.decrypt(blob_data)
            ascii_repr = ''.join(chr(b) if 32 <= b < 127 else '.' for b in pt)
            has_dot = '.' in ascii_repr
            print(f"  {key_name} | {blob_name} | pt={pt.hex()}  [{ascii_repr}] {'*** DOMAIN-LIKE ***' if has_dot else ''}")
else:
    print("\nNo keys matched the last-block suffix pattern.")
    print("The DES domain key is not derivable from static binary analysis alone.")

# 9. Additional: test full decryption of all three blobs with the F0 AES key
# (just in case the domain blobs use AES despite "DES" naming)
print(f"\n{'='*90}")
print("AES-128-ECB FULL DECRYPTION (using F0 key)")
print(f"{'='*90}")
from Crypto.Cipher import AES
f0_key = "combrasiltvaslgklxckbcombrasiltv".encode('utf-8')[:16]
cipher = AES.new(f0_key, AES.MODE_ECB)
for blob_name, blob_data in [("BLOB1_50012", BLOB1), ("BLOB2_403", BLOB2), ("BLOB3_403", BLOB3)]:
    # AES block size is 16, 24 bytes doesn't divide evenly
    # Try with padding to 32
    padded = blob_data + b'\x00' * 8
    pt = cipher.decrypt(padded)
    ascii_repr = ''.join(chr(b) if 32 <= b < 127 else '.' for b in pt)
    print(f"  {blob_name}: {pt.hex()}  [{ascii_repr}]")

print("\nDone.")
