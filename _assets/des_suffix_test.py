#!/usr/bin/env python3
"""
Targeted last-block verification:
Encrypt known suffixes with each candidate key and check if the
ciphertext matches 5920438dc37ffe32 (the common last block).
"""

import base64
import binascii

from Crypto.Cipher import DES, DES3

LAST_BLOCK_CT = bytes.fromhex("5920438dc37ffe32")

# ─── Candidate 8-byte DES keys ───
CANDIDATES_8BYTE = [
    ("bin_fc96f3", "fc96f36d4e61dba7"),
    ("bin_06a066", "06a0663fc4e8eff9"),
    ("bin_6f376d", "6f376d91fe84f373"),
    ("bin_301101", "30110175f1bf0980"),
    ("bin_545866", "5458669a4c39daaf"),
    ("cfg_portal", "9d3e68bf02d358ad"),
    ("cfg_account", "2609dd20aa5d6331"),
    ("body_K1", "d9be3de1ee77ef9e"),
    ("body_K2", "9cebae1cd9fe38e3"),
    ("body_K3", "ae76e39ef7df9ef6"),
]

# ─── Candidate 24-byte 3DES keys ───
CANDIDATES_24BYTE = [
    ("body_3des", "d9be3de1ee77ef9e9cebae1cd9fe38e3ae76e39ef7df9ef6"),
    ("combo_3011_5458_fc96", "30110175f1bf09805458669a4c39daaffc96f36d4e61dba7"),
    ("combo_fc96_3011_5458", "fc96f36d4e61dba730110175f1bf09805458669a4c39daaf"),
    ("combo_06a0_6f37_fc96", "06a0663fc4e8eff96f376d91fe84f373fc96f36d4e61dba7"),
    ("ijiami_hdr24", "1efea263c3e0665d9c12a40030110175f1bf09805458669a"),
]

# ─── Suffix patterns for the last 8 bytes ───
# Each pattern: (name, 8-byte plaintext)
SUFFIXES = []

# .com domains with various lengths (PKCS5 padding to 24 total)
# For domain length 20: padding=4, last block = '.com\x04\x04\x04\x04'
# For domain length 19: padding=5, last block = '.com\x05\x05\x05\x05\x05' (too many bytes!)
# Wait, 24 total bytes means: L chars + P padding = 24. P = 24-L.
# Last block is bytes 16-23 = chars 16..L-1 + padding bytes.
# If domain is 20 chars: chars 16-19 = last 4 chars (".com"), then 4 padding bytes \x04
# If domain is 19 chars: chars 16-18 = last 3 chars (".com" only if prefix has 16 chars), then 5 padding bytes \x05
# Actually: 19-char domain: bytes 0-15 = chars 0-15, bytes 16-18 = chars 16-18, bytes 19-23 = \05\x05\x05\x05
# For ".com" at end: char 16 = '.', char 17 = 'c', char 18 = 'o', bytes 19-23 = \x05\x05\x05\x05
# Wait no, 19-char domain means last char is at offset 18. For "foo.com" (7 chars):
#   char 16 doesn't exist (domain is too short)
# The suffix ".com" is at the end of the domain. For domain length L, the first char of ".com" is at offset L-4.
# So for L=19: chars [15-18] = the LAST 4 chars which are ".com", meaning block 2 = ".com" (bytes 16-18) + \x05...
# But bytes 16-18 = chars 16-18 = '.', 'c', 'o' and byte 19 = 'm' (oh wait, that's byte 19, not part of block 2 start)
# Hmm, I need to map this more carefully.
# Block 2 starts at byte 16 of the padded plaintext.
# For a 19-char domain with 5 padding bytes:
#   Bytes 0-15: chars 0-15 (first 16 chars of domain)
#   Bytes 16-18: chars 16-18 (last 3 chars of domain)
#   Bytes 19-23: \x05\x05\x05\x05\x05 (5 padding bytes)
# But we know the ciphertext is 24 bytes. PKCS5 padding for 19-char plaintext adds 5 bytes:
#   19 + 5 = 24. ✓
# And block 2 = byte 16..23 = char[16] char[17] char[18] \x05 \x05 \x05 \x05 \x05
# For this to be identical across ALL domains, char[16], char[17], char[18] must be identical.
# If domain is 19 chars ending in ".com": char[15]='.', char[16]='c', char[17]='o', char[18]='m'
# Block 2 = 'c' 'o' 'm' \x05 \x05 \x05 \x05 \x05 = "com\x05\x05\x05\x05\x05"
# This IS identical for all 19-char .com domains! ✓

# For L=17: 7 padding bytes. char[16] = last 1 char before padding.
#   For 17-char domain ending in ".com": char[16] = 'm'
#   Block 2 = 'm' + \x07\x07\x07\x07\x07\x07\x07
#   This varies if the char before ".com" differs between domains.

# For L=18: 6 padding bytes. chars[16-17] = last 2 chars.
#   For 18-char domain: chars[16]='o', chars[17]='m'
#   Block 2 = "om" + \x06\x06\x06\x06\x06\x06
#   But only if domain ends in ".com" and length is 18. For this to work, prefix(domain) must be 14 chars.
#   Not all domains have the same total length!

# So the suffix pattern only works for specific domain lengths.
# Most common .com suffix patterns:
# L=20: Block 2 = ".com\x04\x04\x04\x04"
# L=21: Block 2 = "m.com\x03\x03\x03"
# L=19: Block 2 = "com\x05\x05\x05\x05\x05"
# L=22: Block 2 = "om.com\x02\x02"
# L=18: Block 2 = "om\x06\x06\x06\x06\x06\x06"
# L=23: Block 2 = "com.com\x01"
# L=17: Block 2 = "m\x07\x07\x07\x07\x07\x07\x07" (but 'm' would differ unless same-char before .com)

# Also try with "|DES" suffix.
# "domain.com|DES" without subdomain = 14 + "|DES" = 18 chars
# With PKCS5: 18 + 6 padding = 24. Block 2 = "S\x06\x06\x06\x06\x06\x06" if domain is "domain.com|DES"
# But wait, "ysnihrwtg.com|DES" = 18 chars:
#   Block 0: "ysnihrwt"
#   Block 1: "g.com|DE"
#   Block 2: "S\x06\x06\x06\x06\x06\x06"
# This works for any 18-char domain ending in "|DES"!
# Actually no, char 17 = 'S' (last char of |DES). So block 2 = "S\x06\x06\x06\x06\x06\x06"
# This IS identical for all 18-char domains ending in "|DES" ✓

# And "subdomain.domain.com|DES" = longer, probably padded differently.

# Let me try ALL reasonable patterns:

# Pure .com suffix with PKCS5
for length in range(17, 25):
    padding = 24 - length
    if padding <= 0:
        continue
    pad_byte = padding
    pad_bytes = bytes([pad_byte] * padding)

    # Calculate the last-block plaintext based on .com suffix
    # The last 4 chars of the domain are ".com"
    # In the padded plaintext, the domain occupies bytes 0..length-1
    # Block 2 = bytes[16:24] = chars[16:20] (if length>=20) else chars[16:length] + padding

    domain_suffix = ".com"

    # chars that lie within block 2 (bytes 16-23):
    # These are chars at offsets 16, 17, 18, 19 of the domain
    # But only chars up to length-1 exist
    # Actually: bytes 16-23 contain:
    #   - chars[16] through char[length-1] of the domain
    #   - then pad bytes

    # The domain is: XXXXXXXXXXXXXXXX.com (16+4=20 chars)
    # Block 2: chars[16], chars[17], chars[18], chars[19] = ".com" (4 chars)
    #           + padding bytes

    # What I really need is: what are the last (min(4, 24-length)) chars of the domain?
    # For length >= 20: the last 4 chars ".com" are in block 2
    # For length 19: chars[16]=c, chars[17]=o, chars[18]=m (only 3 chars in block 2)
    # For length 18: chars[16]=o, chars[17]=m (only 2 chars in block 2)
    # For length 17: chars[16]=m (only 1 char in block 2)

    if length < 17:
        continue  # Would require < 1 padding

    chars_in_block2 = min(8, length - 16)
    if chars_in_block2 <= 0:
        chars_in_block2 = 0

    suffix_part = ""
    if length >= 20:
        # At least 4 chars of domain in block 2
        suffix_part = ".com"  # chars[16:20]
        remaining_chars = chars_in_block2 - 4
        # chars before ".com" that are in block 2
        # This would be domain-specific, so we can't predict
        # Only works if domains share the 4 chars before position 16
        # Let's just skip the variable parts and test the .com-only case
        if remaining_chars > 0:
            continue  # Block 2 has variable chars before ".com" -- can't be identical
    elif length == 19:
        suffix_part = "com"  # chars[16:19]
    elif length == 18:
        suffix_part = "om"   # chars[16:18]
    elif length == 17:
        suffix_part = "m"    # char[16]
    else:
        continue

    plaintext = suffix_part.encode('ascii') + pad_bytes
    assert len(plaintext) == 8, f"Suffix for L={length}: got {len(plaintext)} bytes"
    name = f"suffix_L{length}_com"
    SUFFIXES.append((name, plaintext))

# Also try with "|DES" suffix patterns
# Format: DOMAIN|DES padded to 24
# Where DOMAIN = subdomain.domain.tld (varies)
# If DOMAIN|DES has length L (17..23), padding = 24-L
# Block 2 = chars[16:length-1] + padding

# For "X.com|DES" = 9 chars, too short
# For "XX.com|DES" = 10 chars, too short
# OK let me think about this differently.

# If format is "sub.domain.tld|DES":
# "espjey.ysnihrwtg.com|DES" = 26 chars (19+1+5) → padded to 32 (4 blocks) -- nope, too big

# What about just "domain|DES" without hostname?
# Then only 3 DES blobs would be in the config (one per host), each encrypting the full hostname.

# Let me try more suffix patterns
# Without "|DES" suffix, just plain hostname with PKCS5

# Try .xyz TLD suffix patterns
for length in range(17, 25):
    padding = 24 - length
    if padding <= 0:
        continue
    pad_byte = padding
    pad_bytes = bytes([pad_byte] * padding)

    if length >= 20:
        # At least 4 chars in block 2 = ".xyz" + more
        if length < 20:
            continue
        chars_in_block2 = length - 16
        if chars_in_block2 > 4:
            continue  # variable chars before .xyz
        # length == 20: chars[16:20] = ".xyz"
        suffix_part = ".xyz"
    elif length == 19:
        suffix_part = "xyz"   # chars[16:19] = "xyz"
    elif length == 18:
        suffix_part = "yz"    # chars[16:18] = "yz"
        continue  # probably domain-specific
    elif length == 17:
        suffix_part = "z"     # char[16] = "z"
        continue  # probably domain-specific
    else:
        continue

    plaintext = suffix_part.encode('ascii') + pad_bytes
    if len(plaintext) != 8:
        continue
    name = f"suffix_L{length}_xyz"
    SUFFIXES.append((name, plaintext))

# Try with "|DES" directly as the last block (if NoPadding)
SUFFIXES.append(("nodepad_|DES", b"com|DES\x00\x00\x00"))
SUFFIXES.append(("nodepad_.com|DE", b".com|DES"))
SUFFIXES.append(("nodepad_S|DES", b"S|DES\x00\x00\x00\x00"))
SUFFIXES.append(("nodepad_E|DES", b"E|DES\x00\x00\x00\x00"))

# Try XOR-derived suffix: if all three plaintexts differ only in first 16 bytes
# and share last 4 chars of .com, the XOR of ciphertext blocks 0 and 1 should reveal
# the XOR of the plaintext differences (if ECB mode with same key)
# But this doesn't help us find the key directly.

# Additional known domain suffix patterns
# The last 8 bytes might be a common suffix like "rwtg.com" if domains share prefix+TLD
# Try: "...com" padded
# Actually, what if the plaintext is shorter, like just the subdomain part?
# "espjey.ysnihrwtg" = 16 chars (without .com) → padded to 24 with 8 \x08
# No, that can't be it because .xyz domains are 15 chars without TLD.

# Try every permutation of .com + PKCS5
print("=" * 90)
print("LAST-BLOCK SUFFIX MATCHING TEST")
print("=" * 90)
print(f"Target ciphertext: {LAST_BLOCK_CT.hex()}")
print(f"Suffix patterns to test: {len(SUFFIXES)}")
print()

# Test each 8-byte candidate with DES-ECB
print("--- Single DES keys ---")
for cand_name, cand_hex in CANDIDATES_8BYTE:
    key = bytes.fromhex(cand_hex)
    for suffix_name, plaintext in SUFFIXES:
        try:
            cipher = DES.new(key, DES.MODE_ECB)
            ct = cipher.encrypt(plaintext)
            if ct == LAST_BLOCK_CT:
                print(f"  *** MATCH! *** key={cand_name} suffix={suffix_name}")
            elif ct[:4] == LAST_BLOCK_CT[:4] or ct[-4:] == LAST_BLOCK_CT[-4:]:
                print(f"  PARTIAL: key={cand_name} suffix={suffix_name} ct={ct.hex()} vs {LAST_BLOCK_CT.hex()}")
        except Exception as e:
            pass

# Test 3DES keys
print("\n--- 3DES keys ---")
for cand_name, cand_hex in CANDIDATES_24BYTE:
    key = bytes.fromhex(cand_hex)
    for suffix_name, plaintext in SUFFIXES:
        try:
            cipher = DES3.new(key, DES3.MODE_ECB)
            ct = cipher.encrypt(plaintext)
            if ct == LAST_BLOCK_CT:
                print(f"  *** MATCH! *** key={cand_name} suffix={suffix_name}")
            elif ct[:4] == LAST_BLOCK_CT[:4]:
                print(f"  PARTIAL(prefix): key={cand_name} suffix={suffix_name} ct={ct.hex()}")
        except Exception as e:
            pass

# Also try the full decryption: encrypt known hosts (or substrings) and compare
print("\n" + "=" * 90)
print("KNOWN-HOST ENCRYPTION MATCHING")
print("=" * 90)

KNOWN_HOSTS = [
    "espjey.ysnihrwtg.com",
    "sxowvd.jzvqwcyor.com",
    "yrqucu.czxenpyba.com",
    "emowvv.dqiswip4.xyz",
    "fuxok.nguvmqhpk.com",
    "mptec.dhkrxuzcy.com",
    "dfcsq.divqohamz.com",
    "ogvkxy.4kcvozfrt.com",
    "fwmlba.athi5owcm.com",
    "eajmnp.hcgv1dt8.com",
    "krdwvd.swcaw7qx.com",
    "cdsr.higoesutn.com",
    "sgyc.bfj1k2g4v.com",
    "34fhwevf.cbcf4gg3f.com",
    "hbyyqx.qtg20rybb.xyz",
    "ioermd.l7hsgo8g.com",
    "jpktl.gczpjqyfu.com",
]

BLOB1 = base64.b64decode("Sz0JjjU4YRgGRpH1paF7wlkgQ43Df/4y")
BLOB2 = base64.b64decode("4hv+FZGcrdsJh3Y7+zl8w1kgQ43Df/4y")
BLOB3 = base64.b64decode("MP5TBkYzwo1YVMusyj8vxlkgQ43Df/4y")

def pad_pkcs5(data, block_size=8):
    pad_len = block_size - (len(data) % block_size)
    if pad_len == 0:
        pad_len = block_size  # Always pad, even for exact multiples
    return data + bytes([pad_len] * pad_len)

# For each key and each host, try various formats
for cand_name, cand_hex in CANDIDATES_8BYTE:
    key = bytes.fromhex(cand_hex)
    cipher = DES.new(key, DES.MODE_ECB)

    for host in KNOWN_HOSTS:
        # Try host as-is with PKCS5
        for suffix_ext in ["", "|DES"]:
            plaintext = (host + suffix_ext).encode('ascii')
            padded = pad_pkcs5(plaintext)

            if len(padded) == 24:  # Only test 3-block plaintexts
                ct = cipher.encrypt(padded)
                if ct == BLOB1:
                    print(f"  *** BLOB1 MATCH! *** key={cand_name} host={host}{suffix_ext}")
                if ct == BLOB2:
                    print(f"  *** BLOB2 MATCH! *** key={cand_name} host={host}{suffix_ext}")
                if ct == BLOB3:
                    print(f"  *** BLOB3 MATCH! *** key={cand_name} host={host}{suffix_ext}")

# For 3DES keys
for cand_name, cand_hex in CANDIDATES_24BYTE:
    key = bytes.fromhex(cand_hex)
    cipher = DES3.new(key, DES3.MODE_ECB)

    for host in KNOWN_HOSTS:
        for suffix_ext in ["", "|DES"]:
            plaintext = (host + suffix_ext).encode('ascii')
            padded = pad_pkcs5(plaintext)

            if len(padded) == 24:
                ct = cipher.encrypt(padded)
                if ct == BLOB1:
                    print(f"  *** BLOB1 MATCH! *** key={cand_name} host={host}{suffix_ext}")
                if ct == BLOB2:
                    print(f"  *** BLOB2 MATCH! *** key={cand_name} host={host}{suffix_ext}")
                if ct == BLOB3:
                    print(f"  *** BLOB3 MATCH! *** key={cand_name} host={host}{suffix_ext}")

# Also try without PKCS5 (NoPadding)
print("\n--- NoPadding mode ---")
for cand_name, cand_hex in CANDIDATES_8BYTE:
    key = bytes.fromhex(cand_hex)
    cipher = DES.new(key, DES.MODE_ECB)

    for host in KNOWN_HOSTS:
        for suffix_ext in ["", "|DES", "|des"]:
            plaintext = (host + suffix_ext).encode('ascii')
            if len(plaintext) == 24:  # Exact 24 bytes
                ct = cipher.encrypt(plaintext)
                if ct == BLOB1:
                    print(f"  *** BLOB1 MATCH! *** key={cand_name} host={host}{suffix_ext} (NoPadding)")
                if ct == BLOB2:
                    print(f"  *** BLOB2 MATCH! *** key={cand_name} host={host}{suffix_ext} (NoPadding)")
                if ct == BLOB3:
                    print(f"  *** BLOB3 MATCH! *** key={cand_name} host={host}{suffix_ext} (NoPadding)")

for cand_name, cand_hex in CANDIDATES_24BYTE:
    key = bytes.fromhex(cand_hex)
    cipher = DES3.new(key, DES3.MODE_ECB)

    for host in KNOWN_HOSTS:
        for suffix_ext in ["", "|DES", "|des"]:
            plaintext = (host + suffix_ext).encode('ascii')
            if len(plaintext) == 24:
                ct = cipher.encrypt(plaintext)
                if ct == BLOB1:
                    print(f"  *** BLOB1 MATCH! *** key={cand_name} host={host}{suffix_ext} (NoPadding)")
                if ct == BLOB2:
                    print(f"  *** BLOB2 MATCH! *** key={cand_name} host={host}{suffix_ext} (NoPadding)")
                if ct == BLOB3:
                    print(f"  *** BLOB3 MATCH! *** key={cand_name} host={host}{suffix_ext} (NoPadding)")

print("\nDone.")
