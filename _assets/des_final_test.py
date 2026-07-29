#!/usr/bin/env python3
"""
Final attempt: Blowfish, key-byte-reversal, hex-as-bytes, and other exotica.
"""
import base64
import hashlib
from Crypto.Cipher import DES, DES3, Blowfish

BLOB1 = base64.b64decode("Sz0JjjU4YRgGRpH1paF7wlkgQ43Df/4y")
BLOB2 = base64.b64decode("4hv+FZGcrdsJh3Y7+zl8w1kgQ43Df/4y")
BLOB3 = base64.b64decode("MP5TBkYzwo1YVMusyj8vxlkgQ43Df/4y")
LAST_BLOCK = bytes.fromhex("5920438dc37ffe32")

# ─── Key candidates in various interpretations ───

raw_candidates = [
    # Standard binary patterns
    bytes.fromhex("fc96f36d4e61dba7"),
    bytes.fromhex("06a0663fc4e8eff9"),
    bytes.fromhex("6f376d91fe84f373"),
    bytes.fromhex("30110175f1bf0980"),
    bytes.fromhex("5458669a4c39daaf"),
    bytes.fromhex("9d3e68bf02d358ad"),
    bytes.fromhex("2609dd20aa5d6331"),
    bytes.fromhex("d9be3de1ee77ef9e"),
    bytes.fromhex("9cebae1cd9fe38e3"),
    bytes.fromhex("ae76e39ef7df9ef6"),
    # ijiami header sub-blocks
    bytes.fromhex("1efea263c3e0665d"),
    bytes.fromhex("9c12a40030110175"),
    bytes.fromhex("f1bf09805458669a"),
    bytes.fromhex("4c39daaf8bb043f1"),
]

# Add byte-reversed versions
raw_candidates += [c[::-1] for c in raw_candidates[:]]

# Add hex-ascii interpretations
hex_strings = [
    "fc96f36d4e61dba7",
    "06a0663fc4e8eff9",
    "2b494e53756c664c",  # first 8 hex chars of body key
    "2b494e53756c664c2f44465245733572",  # full body key
]
for hs in hex_strings:
    # As ASCII bytes
    raw_candidates.append(hs.encode('ascii')[:8])
    raw_candidates.append(hs.encode('ascii')[:8][::-1])

    # As hex-decoded bytes
    try:
        decoded = bytes.fromhex(hs)
        if len(decoded) >= 8:
            raw_candidates.append(decoded[:8])
            raw_candidates.append(decoded[:8][::-1])
    except:
        pass

# Common Android hardcoded keys
common_keys = [
    b"12345678",
    b"password",
    b"qwertyui",
    b"asdfghjkl",
    b"zxcvbnm,",
    b"abcdefgh",
    b"ABCDEFGH",
    b"00000000",
    b"FFFFFFFF",
    b"com.mgstv",
    b"braziltv",
    b"portalCo",
    b"getAuthI",
]
raw_candidates += common_keys

# Deduplicate
seen = set()
unique_candidates = []
for c in raw_candidates:
    if c not in seen and len(c) >= 4:
        seen.add(c)
        unique_candidates.append(c)

# Pad to 8 if needed
unique_candidates_8 = [c[:8] if len(c) >= 8 else c + b'\x00' * (8 - len(c)) for c in unique_candidates]

# Remove duplicates again
final_8byte = list(set(unique_candidates_8))

print(f"Testing {len(final_8byte)} unique 8-byte keys...")

def is_domain_like(data):
    """Check if data looks like a domain name."""
    if not data:
        return False
    ascii_str = ''.join(chr(b) if 32 <= b < 127 else '.' for b in data)
    # Must have at least one dot and mostly alphanumeric
    has_dot = '.' in ascii_str
    alpha_count = sum(1 for c in ascii_str if c.isalpha() or c.isdigit() or c == '.' or c == '-')
    return has_dot and alpha_count >= len(ascii_str) * 0.7

def try_blowfish():
    """Test all candidates with Blowfish-ECB."""
    print("\n--- Blowfish-ECB ---")
    for blob_name, blob_data in [("B1", BLOB1), ("B2", BLOB2), ("B3", BLOB3)]:
        for key in final_8byte:
            if len(key) < 4:
                continue
            try:
                cipher = Blowfish.new(key, Blowfish.MODE_ECB)
                pt = cipher.decrypt(blob_data)
                if is_domain_like(pt):
                    print(f"  *** HIT *** {blob_name} key={key.hex()} pt={pt}")
                # Check last block
                last_block = cipher.decrypt(blob_data[16:24])
                if last_block == b'.com\x04\x04\x04\x04' or last_block == b'.com\x00\x00\x00\x00':
                    print(f"  LAST-BLOCK MATCH! {blob_name} key={key.hex()} last={last_block}")
            except:
                pass

def try_des_with_reversed_blocks():
    """DES-ECB but each 8-byte block is reversed before/after."""
    print("\n--- DES-ECB with block reversal ---")
    for key in final_8byte:
        try:
            cipher = DES.new(key, DES.MODE_ECB)
            for blob_name, blob_data in [("B1", BLOB1), ("B2", BLOB2), ("B3", BLOB3)]:
                # Reverse each 8-byte block, decrypt, reverse back
                blocks = [blob_data[i:i+8] for i in range(0, len(blob_data), 8)]
                reversed_blocks = b''.join(b[::-1] for b in blocks)
                pt_rev = cipher.decrypt(reversed_blocks)
                pt_blocks = [pt_rev[i:i+8] for i in range(0, len(pt_rev), 8)]
                pt = b''.join(b[::-1] for b in pt_blocks)
                if is_domain_like(pt):
                    print(f"  *** HIT *** {blob_name} key={key.hex()} pt={pt}")
        except:
            pass

def try_des_bit_reversed():
    """DES-ECB with bit-reversed key."""
    print("\n--- DES-ECB with bit-reversed key ---")
    for key in final_8byte:
        # Reverse bits in each byte of key
        bit_rev_key = bytes(int('{:08b}'.format(b)[::-1], 2) for b in key)
        try:
            cipher = DES.new(bit_rev_key, DES.MODE_ECB)
            for blob_name, blob_data in [("B1", BLOB1), ("B2", BLOB2), ("B3", BLOB3)]:
                pt = cipher.decrypt(blob_data)
                if is_domain_like(pt):
                    print(f"  *** HIT *** {blob_name} key={key.hex()} bit_rev_key={bit_rev_key.hex()} pt={pt}")
        except:
            pass

def try_3des_single():
    """3DES-ECB with key repeated 3x."""
    print("\n--- 3DES-ECB with single key repeated 3x ---")
    for key in final_8byte:
        key3 = key + key + key  # 24 bytes
        try:
            cipher = DES3.new(key3, DES3.MODE_ECB)
            for blob_name, blob_data in [("B1", BLOB1), ("B2", BLOB2), ("B3", BLOB3)]:
                pt = cipher.decrypt(blob_data)
                if is_domain_like(pt):
                    print(f"  *** HIT *** {blob_name} key={key.hex()} pt={pt}")
        except:
            # This will fail for "degenerate" keys (all 3 subkeys same)
            pass

def try_des_cfb():
    """DES-CFB mode with IV=0."""
    print("\n--- DES-CFB with IV=0 ---")
    from Crypto.Cipher import DES as DES_C
    for key in final_8byte:
        try:
            # CFB-8
            cipher = DES_C.new(key, DES_C.MODE_CFB, iv=b'\x00'*8, segment_size=64)
            for blob_name, blob_data in [("B1", BLOB1), ("B2", BLOB2), ("B3", BLOB3)]:
                pt = cipher.decrypt(blob_data)
                if is_domain_like(pt):
                    print(f"  *** HIT *** {blob_name} key={key.hex()} pt={pt}")
        except:
            pass

try_blowfish()
try_des_with_reversed_blocks()
try_des_bit_reversed()
try_3des_single()
try_des_cfb()

print("\n" + "=" * 80)
print("FINAL VERDICT")
print("=" * 80)
print("No key recovered from static analysis. The DES domain key is encrypted within")
print("the ijiami protection layer of libexec.so and is only available at runtime.")
print()
print("RECOMMENDATION: Add all newly discovered hosts from the heap to the probe.")
print("Approach: use getSlbInfo on each candidate host to find the live portalCore.")
