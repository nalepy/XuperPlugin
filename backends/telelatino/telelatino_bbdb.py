#!/usr/bin/env python3
"""
telelatino_bbdb.py — offline 3DES decrypt harness for TeleLatino's BBDatabase `res` field.

TeleLatino (com.global.latinotv) stores encrypted API responses in the local
BBDatabase (EventDbModel.reserveA -> parameter "res", base64). This harness:

  1. Reads a BBDatabase, extracts the `res` ciphertext (base64 or hex).
  2. 3DES-decrypts it given a key (CLI --key), supporting ECB + CBC, PKCS7.
  3. Self-tests itself on a known plaintext (validates the harness, not the key).
  4. PROVES the koocan keys fail on TeleLatino's `res` (--proof-koocan).
  5. Hunts offline for the key (--scan) across the committed artifacts:
     dex string tables, heap context extracts, and known key clusters/UUIDs.
     Any 24-byte / 16-byte key candidate near crypto strings is derived via
     several plausible key schedules and brute-tried against the `res`
     ciphertext. A hit = valid PKCS7 + JSON-looking plaintext.

Usage:
    # decrypt with an explicit key (base64, hex, or raw string)
    python3 telelatino_bbdb.py --key <key> [--mode ecb|cbc] [--iv <hex>]
    python3 telelatino_bbdb.py --db /path/BBDatabase.db --key <key>
    python3 telelatino_bbdb.py --selftest
    python3 telelatino_bbdb.py --proof-koocan
    python3 telelatino_bbdb.py --scan [--verbose]

OFFLINE ONLY — never touches a device, account, or network.
"""

import argparse
import base64
import hashlib
import json
import re
import sqlite3
import sys
from pathlib import Path

# console may be cp1252 on Windows — never crash on non-ASCII plaintext
try:
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    sys.stderr.reconfigure(encoding="utf-8", errors="replace")
except Exception:
    pass

try:
    from Crypto.Cipher import DES3, DES
except ImportError:
    sys.exit("[!] pycryptodome required:  pip install pycryptodome")

# ---------------------------------------------------------------------------
# The app family's "broken" base64 decoder (com.brasiltv.a.a.a.a /
# sun.misc BASE64Decoder port) — koocan uses it for the UUID -> 24-byte
# 3DES key derivation, TeleLatino is the same backend family.
# ---------------------------------------------------------------------------

_B64_TBL = [-1] * 256
for _i, _ch in enumerate("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"):
    _B64_TBL[ord(_ch)] = _i


def app_b64decode(s: str) -> bytes:
    out = bytearray()
    data = s.encode("latin-1")
    i, n = 0, len(data)
    while i < n:
        c = data[i]
        if c in (10, 13):
            i += 1
            continue
        atom = bytearray(4)
        atom[0] = c
        got = 1
        while got < 4 and i + 1 < n:
            i += 1
            cc = data[i]
            if cc in (10, 13):
                continue
            atom[got] = cc
            got += 1
        size = 4
        if atom[3] == 61:
            size = 3
        if atom[2] == 61:
            size = 2
        v0 = _B64_TBL[atom[0] & 0xFF]
        v1 = _B64_TBL[atom[1] & 0xFF]
        v2 = _B64_TBL[atom[2] & 0xFF]
        v3 = _B64_TBL[atom[3] & 0xFF]
        if size >= 2:
            out.append(((v0 << 2) & 0xFC) | ((v1 >> 4) & 3))
        if size >= 4:
            out.append(((v1 << 4) & 0xF0) | ((v2 >> 2) & 0x0F))
        if size >= 4:
            out.append(((v2 << 6) & 0xC0) | (v3 & 0x3F))
        i += 1
    return bytes(out)


# ---------------------------------------------------------------------------
# Ciphertext extraction from the BBDatabase
# ---------------------------------------------------------------------------

def find_res(db_path: str) -> list:
    """Return [(row_id, eventId, ciphertext_b64), ...] for every `res` parameter."""
    out = []
    con = sqlite3.connect(f"file:{db_path}?mode=ro", uri=True)
    try:
        rows = con.execute(
            "SELECT id, eventId, reserveA FROM EventDbModel WHERE reserveA IS NOT NULL"
        ).fetchall()
    finally:
        con.close()
    for rid, event, reserve in rows:
        try:
            obj = json.loads(reserve)
        except Exception:
            continue
        for p in obj.get("parameter", []):
            if p.get("name") == "res" and p.get("value"):
                out.append((rid, event, p["value"]))
    return out


# ---------------------------------------------------------------------------
# Decrypt core
# ---------------------------------------------------------------------------

def unpad_pkcs7(pt: bytes) -> bytes | None:
    if len(pt) == 0:
        return None
    pad = pt[-1]
    if not (1 <= pad <= 8 and len(pt) >= pad):
        return None
    if pt[-pad:] != bytes([pad]) * pad:
        return None
    return pt[:-pad]


def decrypt_res(ciphertext_b64: str, key: bytes, mode: str = "ECB",
                iv: bytes | None = None, encoding: str = "base64",
                prepend_iv: bool = False) -> tuple:
    """Return (ok, detail). detail = plaintext str on success, else reason."""
    try:
        if encoding == "base64":
            ct = base64.b64decode(ciphertext_b64)
        elif encoding == "hex":
            ct = bytes.fromhex(ciphertext_b64)
        elif encoding == "appb64":
            ct = app_b64decode(ciphertext_b64)
        else:
            return False, f"unknown encoding {encoding}"
    except Exception as e:
        return False, f"ciphertext decode failed: {e}"

    if len(ct) % 8 != 0:
        return False, f"ciphertext len {len(ct)} not block-aligned (8)"
    if len(ct) == 0:
        return False, "empty ciphertext"

    # CBC with a prepended IV: iv = first 8 bytes, body = the rest
    if mode == "CBC" and prepend_iv:
        if len(ct) < 16:
            return False, "ciphertext too short for prepended-IV CBC"
        iv = ct[:8]
        ct = ct[8:]

    klen = len(key)
    if klen == 8:
        algo, make = DES, None
        if mode == "CBC":
            algo, make = DES, lambda: DES.new(key, DES.MODE_CBC, iv or b"\x00" * 8)
    elif klen in (16, 24):
        algo, make = DES3, None
        if mode == "CBC":
            make = lambda: DES3.new(key, DES3.MODE_CBC, iv or b"\x00" * 8)
    else:
        return False, f"key len {klen} not valid for DES(8)/3DES(16|24)"

    try:
        if mode == "ECB":
            pt = algo.new(key, algo.MODE_ECB).decrypt(ct)
        else:
            pt = make().decrypt(ct)
    except ValueError as e:
        return False, f"{algo.__name__} {mode} failed: {e}"

    plain = unpad_pkcs7(pt)
    if plain is None:
        return False, "PKCS7 pad check FAILED (not this key / wrong mode)"
    try:
        text = plain.decode("utf-8")
    except UnicodeDecodeError:
        return False, "PKCS7 ok but NOT valid UTF-8 (random-decrypt, not this key)"
    return True, text


def looks_json(text: str) -> bool:
    """Strict: the entire plaintext must parse as one JSON document."""
    try:
        json.loads(text)
        return True
    except Exception:
        return False


# ---------------------------------------------------------------------------
# Key candidates + derivation (offline hunt)
# ---------------------------------------------------------------------------

# Known key clusters / UUIDs mined from the committed dex string table
# (_session/telelatino_dex/classes.dex via `strings`).
TL_UUIDS = [
    "0e5e9c33-f8c3-4568-86c5-2e4f57523f72",
    "20799a27-fa80-4b36-b2db-0f8141f24180",
    "4c087185-05c8-4683-901d-e1e4d8707c04",
    "629a824d-c717-4ba5-bc0f-3f3968554d01",
    "b700bce0-91c7-47df-a593-747ae941bf34",
]
TL_CLUSTERS = [
    "\\AoaTAka", "\\pa*Tpe*", "&@eT0f!8", "b972E8a5A4e0e8Ff",
    "dCsPLwiy",           # DCS DES request key (shared with koocan, PROVEN)
]
KOOCAN_KEYS = [
    "b940e017-cfea-4aa0-b69d-3a82b6428ed3",
    "c6768bbe-189f-4d9d-b35c-f235a9fd7587",
]
HEAP_EXTRA = [
    "Sz0JjjU4YRgGRpH1paF7wlkgQ43Df/4y",      # domain|DES ciphertext blob (24B)
    "daxvJN28yEwAFBTTYScGzlkgQ43Df/4y",      # domain|DES ciphertext blob (24B)
]

CRYPTO_HINTS = ("DESede", "3DES", "DES-EDE", "res", "BBDatabase",
                "getLiveData", "portalCore", "KEY", "DES3")


def derive_keys(candidate: str) -> list:
    """Turn one candidate string into plausible DES/3DES key byte-strings."""
    keys = set()
    raw = candidate.encode("latin-1")

    # 1) app-family broken b64 decoder, truncated like koocan ([:24])
    try:
        b = app_b64decode(candidate)
        if len(b) >= 24:
            keys.add(b[:24])
        if len(b) >= 16:
            keys.add(b[:16])
    except Exception:
        pass

    # 2) standard base64 (only when it decodes cleanly)
    try:
        b = base64.b64decode(candidate)
        if len(b) >= 24:
            keys.add(b[:24])
        if len(b) >= 16:
            keys.add(b[:16])
    except Exception:
        pass
    try:
        b = base64.b64decode(candidate + "=" * (-len(candidate) % 4))
        if len(b) >= 24:
            keys.add(b[:24])
        if len(b) >= 16:
            keys.add(b[:16])
    except Exception:
        pass

    # 3) raw string bytes (24 / 16 / 8)
    for n in (24, 16, 8):
        if len(raw) >= n:
            keys.add(raw[:n])
    if len(raw) <= 24:
        keys.add(raw.ljust(24, b"\x00"))
        keys.add(raw.ljust(16, b"\x00"))
        keys.add(raw.ljust(8, b"\x00"))

    # 4) hex-decoded (candidates that are pure hex, e.g. b972E8a5A4e0e8Ff)
    if re.fullmatch(r"[0-9a-fA-F]{16,48}", candidate):
        try:
            b = bytes.fromhex(candidate)
            if len(b) in (8, 16, 24):
                keys.add(b)
        except Exception:
            pass
    # 4b) UUID stripped of dashes -> 32 hex chars -> 16-byte 2-key 3DES,
    #     and -> 16B + first 8B -> 24-byte key (k1k2k1)
    stripped = re.sub(r"[-{}]", "", candidate)
    if re.fullmatch(r"[0-9a-fA-F]{24,48}", stripped):
        try:
            b = bytes.fromhex(stripped)
            if len(b) == 16:
                keys.add(b)
                keys.add(b + b[:8])     # k1k2k1
            elif len(b) == 24:
                keys.add(b)
                keys.add(b[:16])        # 2-key form
        except Exception:
            pass

    # 5) md5/sha1 digests (PBE-family fallbacks)
    for n in (16, 24):
        d = hashlib.md5(raw).digest()
        keys.add(d[:n])
        keys.add(d.ljust(n, b"\x00"))
    keys.add(hashlib.sha1(raw).digest()[:24])
    keys.add(hashlib.sha1(raw).digest()[:16])

    # 6) repeat an 8-byte core to 24 (2-key / 3-key EDE constructions)
    if len(raw) == 8:
        keys.add(raw * 3)
        keys.add(raw * 2 + raw)
    return [k for k in keys if len(k) in (8, 16, 24)]


def pbe_md5_des(password: bytes, salt: bytes, iterations: int = 1) -> bytes:
    """PBEWITHMD5ANDDES-CBC key schedule (SunJCE-compatible).
    Key = first 8 bytes of MD5(pass + salt) iterated to 16 bytes."""
    base = password + salt
    h = base
    for _ in range(iterations):
        h = hashlib.md5(h).digest()
    if len(h) < 16:
        h = (h + hashlib.md5(base).digest())[:16]
    return h[:8]


def pbe_sha1_3des(password: bytes, salt: bytes, iterations: int = 1000) -> bytes:
    """PBEWithSHAAnd3KeyTripleDES key schedule (SunJCE-compatible).
    Key = first 24 bytes of iterated SHA-1(pass + salt) blocks."""
    base = password + salt
    out = bytearray()
    counter = 1
    while len(out) < 24:
        h = hashlib.sha1(base + counter.to_bytes(4, "big")).digest()
        for _ in range(iterations - 1):
            h = hashlib.sha1(h).digest()
        out += h
        counter += 1
    return bytes(out[:24])


PBE_SALTS = [b"", b"salt", b"SALT", b"cloudstream", b"telelatino", b"latinotv"]
PBE_ITER = [1, 2, 1000]
PBE_PASSWORDS = [
    "nestor.ale@gmail.com", "Ian20jesus", "nestor.ale", "Ian20",
    "cloudstream", "telelatino", "latinotv", "TeleLatino", "Latinotv",
    "dCsPLwiy", "com.global.latinotv", "25885636", "945257240",
    "169355704", "ca0e53edac957b8f6f187528933355f1",
]


def pbe_keys() -> list:
    """PBE-derived DES/3DES keys from the known account/device identity."""
    keys = []
    for pw in PBE_PASSWORDS:
        pb = pw.encode("utf-8")
        for salt in PBE_SALTS:
            for it in PBE_ITER:
                keys.append(pbe_md5_des(pb, salt, it))          # 8B DES
                keys.append(pbe_sha1_3des(pb, salt, it))        # 24B 3DES
    return keys


def scan_artifacts(project_root: Path) -> list:
    """Collect candidate strings from committed artifacts."""
    cands = set()
    files = []
    for pat in ("_session/telelatino_dex/classes.dex",
                "_session/xtv_dex/dex_strings.txt",
                "_session/heap_domain.txt",
                "_session/heap_blob_ctx.txt",
                "_session/heap_notice_ctx.txt",
                "_session/heap_portal.txt",
                "_session/heap_zap.txt",
                "backends/telelatino/FINDINGS.md",
                "backends/telelatino/ASSESSMENT.md"):
        p = project_root / pat
        if p.exists():
            files.append(p)

    for p in files:
        try:
            data = p.read_bytes()
        except Exception:
            continue
        # ASCII strings >= 6 chars (dex: the whole table is ASCII runs)
        for m in re.finditer(rb"[\x20-\x7e]{6,}", data):
            s = m.group().decode("ascii", "replace")
            # base64-ish / key-ish strings
            if re.fullmatch(r"[A-Za-z0-9+/=]{16,64}", s):
                cands.add(s)
            if re.search(r"[0-9a-fA-F]{16,64}", s):
                cands.add(s)
            # windows near crypto hints: expand to 64 chars around any hint
            for hint in CRYPTO_HINTS:
                idx = s.find(hint)
                if idx >= 0:
                    lo = max(0, idx - 48)
                    hi = min(len(s), idx + 48)
                    cands.add(s[lo:hi])

    return sorted(cands)


# ---------------------------------------------------------------------------
# CLI
# ---------------------------------------------------------------------------

def parse_key(key_str: str, force: str = None) -> bytes:
    if force == "hex":
        return bytes.fromhex(key_str)
    if force == "b64":
        return base64.b64decode(key_str)
    if force == "appb64":
        return app_b64decode(key_str)
    if force == "raw":
        return key_str.encode("latin-1")
    # auto: hex, then base64, then raw
    if re.fullmatch(r"[0-9a-fA-F]{16,48}", key_str):
        try:
            b = bytes.fromhex(key_str)
            if len(b) in (8, 16, 24):
                return b
        except Exception:
            pass
    try:
        b = base64.b64decode(key_str + "=" * (-len(key_str) % 4))
        if len(b) in (8, 16, 24):
            return b
    except Exception:
        pass
    return key_str.encode("latin-1")


SELFTEST_KEY = b"\x11\x22\x33\x44\x55\x66\x77\x88\x99\x00\xaa\xbb\xcc\xdd\xee\xff\x01\x23\x45\x67\x89\xab\xcd\xef"
SELFTEST_IV = bytes(range(8))
SELFTEST_PLAIN = '{"selftest":true,"msg":"telelatino_bbdb harness OK"}'
SELFTEST_CT = None  # computed at runtime


def run_selftest() -> bool:
    from Crypto.Util.Padding import pad
    ct = DES3.new(SELFTEST_KEY, DES3.MODE_CBC, SELFTEST_IV).encrypt(
        pad(SELFTEST_PLAIN.encode(), 8))
    b64 = base64.b64encode(ct).decode()
    ok, text = decrypt_res(b64, SELFTEST_KEY, mode="CBC", iv=SELFTEST_IV)
    if ok and text == SELFTEST_PLAIN:
        print(f"[selftest] PASS  3DES-CBC/PKCS7 round-trip  plain={SELFTEST_PLAIN!r}")
        return True
    print(f"[selftest] FAIL  ok={ok} text={text!r}")
    return False


def run_proof_koocan(ciphertext_b64: str) -> None:
    print("\n=== PROOF: koocan 3DES response keys FAIL on TeleLatino res ===")
    print(f"ciphertext: {ciphertext_b64[:40]}... ({len(ciphertext_b64)} b64 chars)")
    for ks in KOOCAN_KEYS:
        # koocan's exact derivation: app_b64decode(key_str)[:24]
        k24 = app_b64decode(ks)[:24]
        for mode in ("ECB", "CBC"):
            ok, det = decrypt_res(ciphertext_b64, k24, mode=mode)
            print(f"  koocan {ks}\n    key24={k24.hex()}\n    {mode}: "
                  f"{'DECRYPTED: ' + det[:120] if ok else 'FAIL — ' + det}")
    # and the 8-byte DES request keys via EDE-repeat
    for ks in ("dCsPLwiy", "b940e017", "D#a!t-a&"):
        k = ks.encode("latin-1")
        for trial, label in ((k, "DES/ECB"), (k * 3, "3DES(k*3)/ECB")):
            ok, det = decrypt_res(ciphertext_b64, trial)
            print(f"  koocan DES '{ks}' as {label}: "
                  f"{'DECRYPTED: ' + det[:120] if ok else 'FAIL — ' + det}")


def run_scan(ciphertext_b64: str, project_root: Path, verbose: bool) -> bool:
    print("\n=== OFFLINE KEY HUNT ===")
    print("candidates: 5 TeleLatino UUIDs + 4 obfuscated clusters + DCS key +")
    print("            2 koocan keys + artifacts scan (dex/heaps/docs)")
    all_cands = list(TL_UUIDS) + list(TL_CLUSTERS) + list(KOOCAN_KEYS) + list(HEAP_EXTRA)
    art = scan_artifacts(project_root)
    art = [a for a in art if a not in all_cands]
    print(f"artifact-derived candidates: {len(art)}")
    if verbose:
        for a in art:
            print("   ", repr(a[:90]))

    tried = 0
    hits = []

    def attempt(cand, key, mode, prepend_iv=False):
        nonlocal tried
        tried += 1
        ok, det = decrypt_res(ciphertext_b64, key, mode=mode,
                              prepend_iv=prepend_iv)
        if ok:
            score = "JSON" if looks_json(det) else "plain"
            hits.append((cand, key.hex(), mode, det, score))
            if looks_json(det):
                print(f"\n[!] KEY FOUND via candidate {cand!r}\n"
                      f"    key={key.hex()} ({len(key)}B) mode={mode} "
                      f"prepend_iv={prepend_iv}\n"
                      f"    plaintext: {det[:400]}")
                return True
        return False

    def try_all(cand, key):
        for mode in ("ECB", "CBC"):
            if attempt(cand, key, mode):
                return True
        if attempt(cand, key, "CBC", prepend_iv=True):
            return True
        return False

    for cand in all_cands + art:
        for key in derive_keys(cand):
            if try_all(cand, key):
                return True

    # PBE family: keys derived from the account/device identity (password,
    # email, SN, uid...) via PBEWITHMD5ANDDES / PBEWithSHAAnd3KeyTripleDES.
    print("PBE-derived candidates: "
          f"{len(PBE_PASSWORDS)} passwords x {len(PBE_SALTS)} salts x "
          f"{len(PBE_ITER)} iters")
    for key in pbe_keys():
        if try_all("PBE", key):
            return True
    print(f"\nscan complete: {tried} (key,mode) attempts, 0 JSON hits")
    if hits:
        for cand, khex, mode, det, score in hits:
            print(f"  near-miss: cand={cand!r} key={khex} {mode} "
                  f"({score}, {len(det)}B)")
    return False


def main():
    root = Path(__file__).resolve().parent.parent.parent
    db_default = root / "_session" / "BBDatabase.db"

    p = argparse.ArgumentParser(description="TeleLatino BBDatabase res 3DES harness")
    p.add_argument("--db", default=str(db_default), help="path to BBDatabase.db")
    p.add_argument("--key", help="3DES/DES key: hex, base64, or raw string")
    p.add_argument("--key-format", choices=["auto", "hex", "b64", "appb64", "raw"],
                   default="auto")
    p.add_argument("--mode", choices=["ECB", "CBC"], default="ECB")
    p.add_argument("--iv", help="CBC IV as hex (default: 8 zero bytes)")
    p.add_argument("--prepend-iv", action="store_true",
                   help="CBC: treat first 8 ct bytes as the IV")
    p.add_argument("--selftest", action="store_true")
    p.add_argument("--proof-koocan", action="store_true")
    p.add_argument("--scan", action="store_true")
    p.add_argument("--verbose", action="store_true")
    p.add_argument("--ciphertext", help="override: decrypt this b64/hex string instead of DB row")
    args = p.parse_args()

    if args.selftest:
        sys.exit(0 if run_selftest() else 1)

    # fetch ciphertext
    if args.ciphertext:
        samples = [(None, "override", args.ciphertext)]
    else:
        samples = find_res(args.db)
        if not samples:
            print(f"[!] no `res` parameter found in {args.db}")
            sys.exit(2)
    rid, event, ct = samples[0]
    print(f"[db] row id={rid} eventId={event}")
    print(f"[db] res ciphertext: {ct}")

    if args.proof_koocan:
        run_proof_koocan(ct)

    if args.scan:
        found = run_scan(ct, root, args.verbose)
        if not found:
            print("\nVERDICT: NO KEY IN COMMITTED ARTIFACTS -> needs live carve "
                  "(telelatino-hash-live)")
            sys.exit(3)

    if args.key:
        iv = bytes.fromhex(args.iv) if args.iv else None
        try:
            key = parse_key(args.key, args.key_format)
        except Exception as e:
            print(f"[!] key parse failed: {e}")
            sys.exit(2)
        print(f"[decrypt] key={key.hex()} ({len(key)}B) mode={args.mode} "
              f"iv={'zero' if iv is None else iv.hex()} "
              f"prepend_iv={args.prepend_iv}")
        ok, det = decrypt_res(ct, key, mode=args.mode, iv=iv,
                              prepend_iv=args.prepend_iv)
        if ok:
            print(f"[decrypt] OK — PKCS7 valid, {len(det)} bytes:")
            print("-" * 60)
            print(det)
            print("-" * 60)
            if looks_json(det):
                try:
                    j = json.loads(det)
                    print("[decrypt] plaintext is valid JSON")
                    if "parameter" in str(type(j)):
                        pass
                    # pretty-print channel/hash rows if present
                    if isinstance(j, dict):
                        for kk, vv in j.items():
                            s = json.dumps(vv, ensure_ascii=False)
                            if len(s) < 200:
                                print(f"  {kk}: {s}")
                except Exception:
                    pass
        else:
            print(f"[decrypt] FAIL — {det}")
            sys.exit(1)
    elif not (args.proof_koocan or args.scan):
        print("[!] nothing to do: pass --key, --scan, --proof-koocan, or --selftest")
        sys.exit(2)


if __name__ == "__main__":
    main()
