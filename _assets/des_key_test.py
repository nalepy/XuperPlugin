#!/usr/bin/env python3
"""
Comprehensive DES key candidate tester for domain|DES blobs.
Tests every candidate from binary scan + heap carve against all three blobs.
"""

import base64
import binascii
import struct
import sys

# ─── The three domain|DES blobs (base64 encoded) ───
BLOBS = {
    "B1_50012": "Sz0JjjU4YRgGRpH1paF7wlkgQ43Df/4y",
    "B2_403":   "4hv+FZGcrdsJh3Y7+zl8w1kgQ43Df/4y",
    "B3_403":   "MP5TBkYzwo1YVMusyj8vxlkgQ43Df/4y",
}

# ─── Key candidates (all as hex strings) ───

# From binary scan (libexec.so) - repeating 8-byte patterns
CANDIDATES_8BYTE = [
    ("bin_fc96f3", "fc96f36d4e61dba7", "6x repeats in key data area @0x64a60+"),
    ("bin_06a066", "06a0663fc4e8eff9", "3x repeats in ijiami config header @0x64920"),
    ("bin_6f376d", "6f376d91fe84f373", "3x repeats in key data area @0x64960"),
    ("bin_301101", "30110175f1bf0980", "16-byte pair part1 @0x6490c"),
    ("bin_545866", "5458669a4c39daaf", "16-byte pair part2 @0x64930"),
]

# From config.xml (heap carve)
CANDIDATES_CONFIG = [
    ("cfg_portal", "9d3e68bf02d358ad", "portal_code from config.xml"),
    ("cfg_account", "2609dd20aa5d6331", "account_type from config.xml"),
]

# From binary - base64 fragments (6 bytes, will be zero-padded to 8)
CANDIDATES_FRAGMENTS = [
    ("bin_03JtED", "d3726d1031560000", "'03JtEDFW' @0x32d16 (padded to 8)"),
    ("bin_pjh78M", "a6387bf0ce940000", "'pjh78M6U' @0x3d123 (padded to 8)"),
    ("bin_SEASAC", "4840120021d80000", "'SEASACHY' @0x555dc (padded to 8)"),
]

# From body 3DES key (XuperCrypto.DEFAULT_KEY) - broken into three 8-byte DES subkeys
BODY_3DES = "d9be3de1ee77ef9e9cebae1cd9fe38e3ae76e39ef7df9ef6"
BODY_SUBKEYS = [
    ("body_K1", "d9be3de1ee77ef9e", "Body 3DES key bytes 0-7"),
    ("body_K2", "9cebae1cd9fe38e3", "Body 3DES key bytes 8-15"),
    ("body_K3", "ae76e39ef7df9ef6", "Body 3DES key bytes 16-23"),
]

# From binary - the full ijiami config header (32 bytes)
IJIAMI_HEADER = "1efea263c3e0665d9c12a40030110175f1bf09805458669a4c39daaf8bb043f1"

# Derived / combined candidates
COMBINED = [
    ("combo_fc96_3x", "fc96f36d4e61dba7" * 3, "fc96f3... repeated 3x for 3DES"),
    ("combo_06a0_3x", "06a0663fc4e8eff9" * 3, "06a066... repeated 3x for 3DES"),
    ("combo_6f37_3x", "6f376d91fe84f373" * 3, "6f376d... repeated 3x for 3DES"),
    ("combo_3011_5458_fc96", "30110175f1bf09805458669a4c39daaffc96f36d4e61dba7", "pair combo"),
    ("combo_3011_fc96_5458", "30110175f1bf0980fc96f36d4e61dba75458669a4c39daaf", "pair combo"),
    ("combo_fc96_3011_5458", "fc96f36d4e61dba730110175f1bf09805458669a4c39daaf", "pair combo"),
    ("combo_5458_fc96_3011", "5458669a4c39daaffc96f36d4e61dba730110175f1bf0980", "pair combo"),
    ("combo_06a0_6f37_fc96", "06a0663fc4e8eff96f376d91fe84f373fc96f36d4e61dba7", "3-key from 3 patterns"),
    ("combo_6f37_fc96_06a0", "6f376d91fe84f373fc96f36d4e61dba706a0663fc4e8eff9", "3-key variant"),
    ("combo_2key_3011-5458", "30110175f1bf09805458669a4c39daaf30110175f1bf0980", "2-key 3DES (K1-K2-K1)"),
    ("combo_2key_5458-3011", "5458669a4c39daaf30110175f1bf09805458669a4c39daaf", "2-key 3DES (K2-K1-K2)"),
    ("combo_2key_fc96-06a0", "fc96f36d4e61dba706a0663fc4e8eff9fc96f36d4e61dba7", "2-key 3DES (K1-K2-K1)"),
    ("combo_2key_fc96-6f37", "fc96f36d4e61dba76f376d91fe84f373fc96f36d4e61dba7", "2-key 3DES (K1-K2-K1)"),
    # ijiami header - first 24 bytes as 3DES key
    ("ijiami_hdr24", IJIAMI_HEADER[:48], "ijiami header bytes 0-23"),
    ("ijiami_hdr24b", IJIAMI_HEADER[8:56], "ijiami header bytes 8-31"),
    # portal_code padded to 24 bytes
    ("cfg_portal_3x", "9d3e68bf02d358ad" * 3, "portal_code repeated 3x for 3DES"),
    ("cfg_account_3x", "2609dd20aa5d6331" * 3, "account_type repeated 3x for 3DES"),
]

ALL_CANDIDATES = CANDIDATES_8BYTE + CANDIDATES_CONFIG + CANDIDATES_FRAGMENTS + BODY_SUBKEYS + COMBINED


def hex_to_bytes(hex_str):
    """Convert hex string to bytes."""
    return binascii.unhexlify(hex_str)


def is_printable_ascii(data, min_printable=4):
    """Check if data contains enough printable ASCII to be a readable domain."""
    printable = sum(1 for b in data if 32 <= b < 127)
    # Also check for common domain characters
    domain_chars = sum(1 for b in data if (48 <= b <= 57) or (65 <= b <= 90) or (97 <= b <= 122) or b in [46, 45, 95])
    return printable >= min_printable and domain_chars >= min_printable


def try_pycryptodome():
    """Test using pycryptodome. Returns results dict."""
    try:
        from Crypto.Cipher import DES, DES3
    except ImportError:
        return None

    results = []

    for blob_name, blob_b64 in BLOBS.items():
        ct = base64.b64decode(blob_b64)
        assert len(ct) == 24, f"{blob_name} decodes to {len(ct)} bytes, expected 24"

        for cand_name, cand_hex, cand_desc in ALL_CANDIDATES:
            key = hex_to_bytes(cand_hex)

            # Single DES (8-byte key)
            if len(key) == 8:
                for mode_name, cipher_ctor, iv in [
                    ("DES-ECB", lambda k: DES.new(k, DES.MODE_ECB), None),
                    ("DES-CBC", lambda k: DES.new(k, DES.MODE_CBC, iv=b'\x00'*8), b'\x00'*8),
                ]:
                    try:
                        cipher = cipher_ctor(key)
                        pt = cipher.decrypt(ct)
                        if is_printable_ascii(pt):
                            results.append(("HIT", blob_name, cand_name, mode_name, cand_desc, pt))
                        else:
                            results.append(("MISS", blob_name, cand_name, mode_name, cand_desc, pt))
                    except Exception as e:
                        results.append(("ERR", blob_name, cand_name, mode_name, cand_desc, str(e)))

            # Triple DES (16 or 24 byte key)
            if len(key) in (16, 24):
                for mode_name, cipher_ctor, iv in [
                    ("3DES-ECB", lambda k: DES3.new(k, DES3.MODE_ECB), None),
                    ("3DES-CBC", lambda k: DES3.new(k, DES3.MODE_CBC, iv=b'\x00'*8), b'\x00'*8),
                ]:
                    try:
                        cipher = cipher_ctor(key)
                        pt = cipher.decrypt(ct)
                        if is_printable_ascii(pt):
                            results.append(("HIT", blob_name, cand_name, mode_name, cand_desc, pt))
                        else:
                            results.append(("MISS", blob_name, cand_name, mode_name, cand_desc, pt))
                    except Exception as e:
                        results.append(("ERR", blob_name, cand_name, mode_name, cand_desc, str(e)))

    return results


def try_openssl():
    """Test using openssl command-line. Returns results dict."""
    import subprocess
    import tempfile
    import os

    results = []

    for blob_name, blob_b64 in BLOBS.items():
        # Write ciphertext to temp file
        with tempfile.NamedTemporaryFile(delete=False, suffix='.bin') as f:
            ct = base64.b64decode(blob_b64)
            f.write(ct)
            ct_path = f.name

        for cand_name, cand_hex, cand_desc in ALL_CANDIDATES:
            key = hex_to_bytes(cand_hex)

            # Single DES (8-byte key)
            if len(key) == 8:
                for mode, openssl_cipher in [("DES-ECB", "des-ecb"), ("DES-CBC", "des-cbc")]:
                    iv_arg = ["-iv", "0000000000000000"] if "cbc" in openssl_cipher else []
                    try:
                        result = subprocess.run(
                            ["openssl", "enc", "-d", f"-{openssl_cipher}", "-K", cand_hex,
                             "-nopad"] + iv_arg + ["-in", ct_path],
                            capture_output=True, timeout=5
                        )
                        pt = result.stdout
                        if is_printable_ascii(pt):
                            results.append(("HIT", blob_name, cand_name, mode, cand_desc, pt))
                        else:
                            results.append(("MISS", blob_name, cand_name, mode, cand_desc, pt))
                    except Exception as e:
                        results.append(("ERR", blob_name, cand_name, mode, cand_desc, str(e)))

            # Triple DES (16 or 24 byte key)
            if len(key) in (16, 24):
                for mode, openssl_cipher in [("3DES-ECB", "des-ede3"), ("3DES-CBC", "des-ede3-cbc")]:
                    iv_arg = ["-iv", "0000000000000000"] if "cbc" in openssl_cipher else []
                    try:
                        result = subprocess.run(
                            ["openssl", "enc", "-d", f"-{openssl_cipher}", "-K", cand_hex,
                             "-nopad"] + iv_arg + ["-in", ct_path],
                            capture_output=True, timeout=5
                        )
                        pt = result.stdout
                        if is_printable_ascii(pt):
                            results.append(("HIT", blob_name, cand_name, mode, cand_desc, pt))
                        else:
                            results.append(("MISS", blob_name, cand_name, mode, cand_desc, pt))
                    except Exception as e:
                        results.append(("ERR", blob_name, cand_name, mode, cand_desc, str(e)))

        os.unlink(ct_path)

    return results


def try_manual_des_ecb(ct_bytes, key_bytes):
    """
    Pure-Python DES-ECB decrypt (for when no crypto libraries are available).
    Implements full DES algorithm.
    """
    # DES constants
    IP = [58, 50, 42, 34, 26, 18, 10, 2, 60, 52, 44, 36, 28, 20, 12, 4,
          62, 54, 46, 38, 30, 22, 14, 6, 64, 56, 48, 40, 32, 24, 16, 8,
          57, 49, 41, 33, 25, 17, 9, 1, 59, 51, 43, 35, 27, 19, 11, 3,
          61, 53, 45, 37, 29, 21, 13, 5, 63, 55, 47, 39, 31, 23, 15, 7]

    FP = [40, 8, 48, 16, 56, 24, 64, 32, 39, 7, 47, 15, 55, 23, 63, 31,
          38, 6, 46, 14, 54, 22, 62, 30, 37, 5, 45, 13, 53, 21, 61, 29,
          36, 4, 44, 12, 52, 20, 60, 28, 35, 3, 43, 11, 51, 19, 59, 27,
          34, 2, 42, 10, 50, 18, 58, 26, 33, 1, 41, 9, 49, 17, 57, 25]

    E = [32, 1, 2, 3, 4, 5, 4, 5, 6, 7, 8, 9, 8, 9, 10, 11, 12, 13,
         12, 13, 14, 15, 16, 17, 16, 17, 18, 19, 20, 21, 20, 21, 22, 23,
         24, 25, 24, 25, 26, 27, 28, 29, 28, 29, 30, 31, 32, 1]

    P = [16, 7, 20, 21, 29, 12, 28, 17, 1, 15, 23, 26, 5, 18, 31, 10,
         2, 8, 24, 14, 32, 27, 3, 9, 19, 13, 30, 6, 22, 11, 4, 25]

    S_BOXES = [
        [[14, 4, 13, 1, 2, 15, 11, 8, 3, 10, 6, 12, 5, 9, 0, 7],
         [0, 15, 7, 4, 14, 2, 13, 1, 10, 6, 12, 11, 9, 5, 3, 8],
         [4, 1, 14, 8, 13, 6, 2, 11, 15, 12, 9, 7, 3, 10, 5, 0],
         [15, 12, 8, 2, 4, 9, 1, 7, 5, 11, 3, 14, 10, 0, 6, 13]],
        [[15, 1, 8, 14, 6, 11, 3, 4, 9, 7, 2, 13, 12, 0, 5, 10],
         [3, 13, 4, 7, 15, 2, 8, 14, 12, 0, 1, 10, 6, 9, 11, 5],
         [0, 14, 7, 11, 10, 4, 13, 1, 5, 8, 12, 6, 9, 3, 2, 15],
         [13, 8, 10, 1, 3, 15, 4, 2, 11, 6, 7, 12, 0, 5, 14, 9]],
        [[10, 0, 9, 14, 6, 3, 15, 5, 1, 13, 12, 7, 11, 4, 2, 8],
         [13, 7, 0, 9, 3, 4, 6, 10, 2, 8, 5, 14, 12, 11, 15, 1],
         [13, 6, 4, 9, 8, 15, 3, 0, 11, 1, 2, 12, 5, 10, 14, 7],
         [1, 10, 13, 0, 6, 9, 8, 7, 4, 15, 14, 3, 11, 5, 2, 12]],
        [[7, 13, 14, 3, 0, 6, 9, 10, 1, 2, 8, 5, 11, 12, 4, 15],
         [13, 8, 11, 5, 6, 15, 0, 3, 4, 7, 2, 12, 1, 10, 14, 9],
         [10, 6, 9, 0, 12, 11, 7, 13, 15, 1, 3, 14, 5, 2, 8, 4],
         [3, 15, 0, 6, 10, 1, 13, 8, 9, 4, 5, 11, 12, 7, 2, 14]],
        [[2, 12, 4, 1, 7, 10, 11, 6, 8, 5, 3, 15, 13, 0, 14, 9],
         [14, 11, 2, 12, 4, 7, 13, 1, 5, 0, 15, 10, 3, 9, 8, 6],
         [4, 2, 1, 11, 10, 13, 7, 8, 15, 9, 12, 5, 6, 3, 0, 14],
         [11, 8, 12, 7, 1, 14, 2, 13, 6, 15, 0, 9, 10, 4, 5, 3]],
        [[12, 1, 10, 15, 9, 2, 6, 8, 0, 13, 3, 4, 14, 7, 5, 11],
         [10, 15, 4, 2, 7, 12, 9, 5, 6, 1, 13, 14, 0, 11, 3, 8],
         [9, 14, 15, 5, 2, 8, 12, 3, 7, 0, 4, 10, 1, 13, 11, 6],
         [4, 3, 2, 12, 9, 5, 15, 10, 11, 14, 1, 7, 6, 0, 8, 13]],
        [[4, 11, 2, 14, 15, 0, 8, 13, 3, 12, 9, 7, 5, 10, 6, 1],
         [13, 0, 11, 7, 4, 9, 1, 10, 14, 3, 5, 12, 2, 15, 8, 6],
         [1, 4, 11, 13, 12, 3, 7, 14, 10, 15, 6, 8, 0, 5, 9, 2],
         [6, 11, 13, 8, 1, 4, 10, 7, 9, 5, 0, 15, 14, 2, 3, 12]],
        [[13, 2, 8, 4, 6, 15, 11, 1, 10, 9, 3, 14, 5, 0, 12, 7],
         [1, 15, 13, 8, 10, 3, 7, 4, 12, 5, 6, 11, 0, 14, 9, 2],
         [7, 11, 4, 1, 9, 12, 14, 2, 0, 6, 10, 13, 15, 3, 5, 8],
         [2, 1, 14, 7, 4, 10, 8, 13, 15, 12, 9, 0, 3, 5, 6, 11]]
    ]

    PC1 = [57, 49, 41, 33, 25, 17, 9, 1, 58, 50, 42, 34, 26, 18,
           10, 2, 59, 51, 43, 35, 27, 19, 11, 3, 60, 52, 44, 36,
           63, 55, 47, 39, 31, 23, 15, 7, 62, 54, 46, 38, 30, 22,
           14, 6, 61, 53, 45, 37, 29, 21, 13, 5, 28, 20, 12, 4]

    PC2 = [14, 17, 11, 24, 1, 5, 3, 28, 15, 6, 21, 10, 23, 19, 12, 4,
           26, 8, 16, 7, 27, 20, 13, 2, 41, 52, 31, 37, 47, 55, 30, 40,
           51, 45, 33, 48, 44, 49, 39, 56, 34, 53, 46, 42, 50, 36, 29, 32]

    SHIFTS = [1, 1, 2, 2, 2, 2, 2, 2, 1, 2, 2, 2, 2, 2, 2, 1]

    def permute(block, table):
        result = 0
        for bit_pos in table:
            result <<= 1
            if block & (1 << (64 - bit_pos)):
                result |= 1
        return result

    def permute_56(block, table):
        """Permute a 64-bit block using 56-entry table (PC1)."""
        result = 0
        for bit_pos in table:
            result <<= 1
            if block & (1 << (64 - bit_pos)):
                result |= 1
        return result

    def permute_48(block, table):
        """Permute a 56-bit block using 48-entry table (PC2)."""
        result = 0
        for bit_pos in table:
            result <<= 1
            if block & (1 << (56 - bit_pos)):
                result |= 1
        return result

    # Convert key to 64-bit integer
    key_int = int.from_bytes(key_bytes, 'big')

    # PC1 permutation
    key_56 = permute_56(key_int, PC1)

    # Split into C and D
    C = key_56 >> 28
    D = key_56 & ((1 << 28) - 1)

    # Generate 16 subkeys
    subkeys = []
    for shift in SHIFTS:
        C = ((C << shift) | (C >> (28 - shift))) & ((1 << 28) - 1)
        D = ((D << shift) | (D >> (28 - shift))) & ((1 << 28) - 1)
        combined = (C << 28) | D
        subkey = permute_48(combined, PC2)
        subkeys.append(subkey)

    # Decrypt each block
    result = bytearray()
    for block_offset in range(0, len(ct_bytes), 8):
        block = ct_bytes[block_offset:block_offset + 8]
        if len(block) < 8:
            break
        block_int = int.from_bytes(block, 'big')

        # Initial permutation
        block_int = permute(block_int, IP)

        # Split into L and R
        L = block_int >> 32
        R = block_int & ((1 << 32) - 1)

        # 16 rounds (reverse for decryption)
        for round_idx in range(15, -1, -1):
            subkey = subkeys[round_idx]

            # Expand R to 48 bits
            R_expanded = 0
            for bit_pos in E:
                R_expanded <<= 1
                if R & (1 << (32 - bit_pos)):
                    R_expanded |= 1

            # XOR with subkey
            R_expanded ^= subkey

            # S-box substitution
            sbox_output = 0
            for sbox_idx in range(8):
                six_bits = (R_expanded >> (42 - sbox_idx * 6)) & 0x3F
                row = ((six_bits >> 4) & 2) | (six_bits & 1)
                col = (six_bits >> 1) & 0xF
                sbox_output <<= 4
                sbox_output |= S_BOXES[sbox_idx][row][col]

            # P permutation on 32-bit output
            new_R = 0
            for bit_pos in P:
                new_R <<= 1
                if sbox_output & (1 << (32 - bit_pos)):
                    new_R |= 1

            # Swap
            L, R = R, L ^ new_R

        # Final swap (undo last swap)
        L, R = R, L

        # Combine and final permutation
        combined = (L << 32) | R
        combined = permute(combined, FP)
        result.extend(combined.to_bytes(8, 'big'))

    return bytes(result)


def try_purepython():
    """Pure Python DES implementation (no external deps)."""
    results = []

    for blob_name, blob_b64 in BLOBS.items():
        ct = base64.b64decode(blob_b64)

        for cand_name, cand_hex, cand_desc in ALL_CANDIDATES:
            key = hex_to_bytes(cand_hex)

            # Only single DES supported in pure Python
            if len(key) == 8:
                try:
                    pt = try_manual_des_ecb(ct, key)
                    if is_printable_ascii(pt):
                        results.append(("HIT", blob_name, cand_name, "DES-ECB(pure)", cand_desc, pt))
                    else:
                        results.append(("MISS", blob_name, cand_name, "DES-ECB(pure)", cand_desc, pt))
                except Exception as e:
                    results.append(("ERR", blob_name, cand_name, "DES-ECB(pure)", cand_desc, str(e)))

    return results


def format_bytes(b):
    """Format bytes for display."""
    hex_str = b.hex()
    ascii_str = ''.join(chr(x) if 32 <= x < 127 else '.' for x in b)
    return f"{hex_str}  [{ascii_str}]"


def main():
    print("=" * 90)
    print("DES KEY CANDIDATE TESTER")
    print("=" * 90)
    print()

    # Try pycryptodome first
    print("[*] Trying pycryptodome...")
    results = try_pycryptodome()

    if results is None:
        print("[!] pycryptodome not available, trying pure Python DES...")
        results = try_purepython()

    if not results:
        print("[!] No results generated. Falling back to pure Python DES.")
        results = try_purepython()

    # Filter and display
    hits = [r for r in results if r[0] == "HIT"]
    misses = [r for r in results if r[0] == "MISS"]
    errors = [r for r in results if r[0] == "ERR"]

    print(f"\n{'='*90}")
    print(f"SUMMARY: {len(hits)} HITS, {len(misses)} MISSES, {len(errors)} ERRORS")
    print(f"{'='*90}")

    if hits:
        print(f"\n{'='*90}")
        print("HITS (potential key found!)")
        print(f"{'='*90}")
        for status, blob, cand, mode, desc, pt in hits:
            print(f"\n  BLOB:     {blob}")
            print(f"  CANDIDATE: {cand}")
            print(f"  MODE:     {mode}")
            print(f"  DESC:     {desc}")
            print(f"  PLAINTEXT: {format_bytes(pt)}")

    if errors:
        print(f"\n{'='*90}")
        print("ERRORS")
        print(f"{'='*90}")
        for status, blob, cand, mode, desc, err in errors:
            print(f"  {blob} | {cand} | {mode} | {err}")

    # Show interesting misses (any with printable characters)
    interesting = [(s, b, c, m, d, p) for s, b, c, m, d, p in results
                   if s == "MISS" and is_printable_ascii(p, min_printable=2)]
    if interesting:
        print(f"\n{'='*90}")
        print("INTERESTING MISSES (some printable content)")
        print(f"{'='*90}")
        for status, blob, cand, mode, desc, pt in interesting:
            print(f"  {blob} | {cand} | {mode} | {format_bytes(pt)}")

    # Also show the first few misses as sample
    print(f"\n{'='*90}")
    print("SAMPLE MISSES (first 10)")
    print(f"{'='*90}")
    for status, blob, cand, mode, desc, pt in misses[:10]:
        print(f"  {blob} | {cand} | {mode} | {format_bytes(pt[:8])}...")

    # Now also test each 8-byte block separately with each candidate
    print(f"\n{'='*90}")
    print("PER-BLOCK DES-ECB TEST (individual 8-byte blocks)")
    print(f"{'='*90}")

    for blob_name, blob_b64 in BLOBS.items():
        ct = base64.b64decode(blob_b64)
        print(f"\n  --- {blob_name} ---")
        for block_idx in range(3):
            block = ct[block_idx*8:(block_idx+1)*8]

            print(f"\n    Block {block_idx}: {block.hex()}")

            for cand_name, cand_hex, cand_desc in CANDIDATES_8BYTE + CANDIDATES_CONFIG + BODY_SUBKEYS:
                key = hex_to_bytes(cand_hex)
                if len(key) != 8:
                    continue
                try:
                    pt = try_manual_des_ecb(block, key)
                    printable = sum(1 for b in pt if 32 <= b < 127)
                    if printable >= 4:
                        print(f"      {cand_name:20s}: {format_bytes(pt)}  *** PRINTABLE ({printable}/8) ***")
                    elif printable >= 1:
                        print(f"      {cand_name:20s}: {format_bytes(pt)}  ({printable}/8 printable)")
                except Exception:
                    pass

    # Additional test: check if the body 3DES key works on individual blocks
    print(f"\n{'='*90}")
    print("3DES KEY PER-BLOCK TEST (body key)")
    print(f"{'='*90}")

    body_key = hex_to_bytes(BODY_3DES)
    try:
        from Crypto.Cipher import DES3
        for blob_name, blob_b64 in BLOBS.items():
            ct = base64.b64decode(blob_b64)
            for block_idx in range(3):
                block = ct[block_idx*8:(block_idx+1)*8]
                try:
                    cipher = DES3.new(body_key, DES3.MODE_ECB)
                    pt = cipher.decrypt(block)
                    print(f"  {blob_name} Block {block_idx}: {format_bytes(pt)}")
                except Exception as e:
                    print(f"  {blob_name} Block {block_idx}: Error - {e}")
    except ImportError:
        print("  (pycryptodome not available for 3DES single-block test)")

    # XOR analysis of plaintext differences
    print(f"\n{'='*90}")
    print("CROSS-BLOB XOR ANALYSIS")
    print(f"{'='*90}")

    ct1 = base64.b64decode(BLOBS["B1_50012"])
    ct2 = base64.b64decode(BLOBS["B2_403"])
    ct3 = base64.b64decode(BLOBS["B3_403"])

    print(f"  B1^B2: {bytes([a ^ b for a, b in zip(ct1, ct2)]).hex()}")
    print(f"  B1^B3: {bytes([a ^ b for a, b in zip(ct1, ct3)]).hex()}")
    print(f"  B2^B3: {bytes([a ^ b for a, b in zip(ct2, ct3)]).hex()}")
    print(f"  Last 8 bytes identical: {ct1[16:] == ct2[16:] == ct3[16:]}")

    return hits


if __name__ == "__main__":
    main()
