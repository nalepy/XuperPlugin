#!/usr/bin/env python3
"""
hash_derive.py — TeleLatino channel-hash derivation harness.

Tests whether the `/live/cyx-<HASH>.m3u8` channel hash is a pure function of
channel identity (channel code / name / EPG position / raw channel code + salts).

Ground truth (from committed artifacts):
    cyx-Cinemax  -> C9EB0B2644979328E598EAFED311   (raw code 50fdcc0817d61)
    cyx-LaRedHD  -> 1F3251F9425197449B94E006D8EB

Target hashes are 28 hex chars (14 bytes) — nonstandard length, so the harness
checks not only exact digest equality but every 28-char window of every digest,
plus integer-rebase encodings (base36/base32/base62) and HMAC truncations.

Usage:
    python3 hash_derive.py                # run full hypothesis sweep vs ground truth
    python3 hash_derive.py --table        # (after a formula match) emit 344-channel table
"""

import argparse
import base64
import hashlib
import hmac
import itertools
import json
import os
import struct
import sys
import urllib.parse
import zlib

HERE = os.path.dirname(os.path.abspath(__file__))
CORPUS = os.path.join(HERE, "corpus", "hash_corpus.json")
CHANNELS = os.path.join(HERE, "corpus", "channels_344.txt")

# ---------------------------------------------------------------------------
# corpus
# ---------------------------------------------------------------------------


def load_corpus():
    with open(CORPUS, encoding="utf-8") as f:
        return json.load(f)


def load_channels():
    with open(CHANNELS, encoding="utf-8") as f:
        return [ln.strip() for ln in f if ln.strip()]


# ---------------------------------------------------------------------------
# candidate input generators
# ---------------------------------------------------------------------------

B62_ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"
B36_ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ"
B32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"


def to_base(n: int, alphabet: str) -> str:
    if n == 0:
        return alphabet[0]
    base = len(alphabet)
    out = []
    while n:
        n, r = divmod(n, base)
        out.append(alphabet[r])
    return "".join(reversed(out))


def rebase_forms(hexdigest: str) -> dict:
    """Integer re-basings of a hex digest — catches 'hash then base36/32' schemes."""
    n = int(hexdigest, 16)
    return {
        "b36": to_base(n, B36_ALPHABET),
        "b62": to_base(n, B62_ALPHABET),
        "b32": to_base(n, B32_ALPHABET),
    }


def code_variants(code: str):
    """All textual forms of one channel code/name."""
    name = code
    for prefix in ("cyx-", "cyx_", "cyx"):
        if name.startswith(prefix):
            name = name[len(prefix):]
            break
    v = {
        "code": code,
        "name": name,
        "code_lower": code.lower(),
        "name_lower": name.lower(),
        "name_upper": name.upper(),
        "cyx+" + name: "cyx" + name,
        "cyx-" + name: "cyx-" + name,
        "cyx_" + name: "cyx_" + name,
        "cyx-upper-" + name: "CYX-" + name,
        "name+cyx": name + "cyx",
        "name+cyx-": name + "cyx-",
        "code_nodash": code.replace("-", ""),
        "code_nounderscore": code.replace("_", ""),
        "code_underscore": code.replace("-", "_"),
        "code_dash": code.replace("_", "-"),
        "urlq": urllib.parse.quote(code, safe=""),
        "urlq_plus": urllib.parse.quote_plus(code, safe=""),
        "urlq_name": urllib.parse.quote(name, safe=""),
    }
    # name with separators normalized
    v["name_dash"] = name.replace("_", "-")
    v["name_under"] = name.replace("-", "_")
    v["name_nospace"] = name.replace(" ", "")
    # structured forms (JSON / query-string / url template fill)
    v["json_code"] = '{"channelCode":"%s"}' % code
    v["json_code2"] = '{"channelCode":"%s"}' % name
    v["query_code"] = "channelCode=" + code
    v["query_name"] = "channelCode=" + name
    v["query_code2"] = "channelCode=%s&type=1" % code
    v["epg_url_fill"] = "/epg/v2/live/app/utc-3/26/%s" % name
    v["live_path"] = "/live/cyx-%s" % name
    v["live_path_code"] = "/live/%s" % code
    v["m3u8_path"] = "/live/cyx-%s.m3u8" % name
    # utf-16 variants (java String.getBytes("UTF-16LE") / UTF-16BE) — stored as bytes
    v["name_utf16le"] = name.encode("utf-16le")
    v["code_utf16le"] = code.encode("utf-16le")
    v["name_utf16be"] = name.encode("utf-16be")
    v["code_utf16be"] = code.encode("utf-16be")
    v["cyx_name_utf16le"] = ("cyx-" + name).encode("utf-16le")
    v["cyx_name_utf16be"] = ("cyx-" + name).encode("utf-16be")
    return v


def raw_code_variants(raw: str):
    if not raw:
        return {}
    try:
        dec = str(int(raw, 16))
        dec_plain = str(int(raw))
    except ValueError:
        dec, dec_plain = "", ""
    v = {
        "raw": raw,
        "raw_lower": raw.lower(),
        "raw_upper": raw.upper(),
        "cyx_" + raw: "cyx_" + raw,
        "cyx-" + raw: "cyx-" + raw,
        "cyx" + raw: "cyx" + raw,
        "cyx_" + raw + "_720p": "cyx_" + raw + "_720p",
        "cyx-" + raw + "-720p": "cyx-" + raw + "-720p",
        "raw_720p": raw + "_720p",
        "raw_hex2dec": dec,
    }
    if dec_plain:
        v["raw_dec_plain"] = dec_plain
    return v


# ---------------------------------------------------------------------------
# digest / encoding functions
# ---------------------------------------------------------------------------


def digest_family(data: bytes, algo: str) -> str:
    return hashlib.new(algo, data).hexdigest()


def hmac_family(data: bytes, key: bytes, algo: str) -> str:
    dm = hashlib.new(algo)  # raises for unsupported algos
    return hmac.new(key, data, lambda: hashlib.new(algo)).hexdigest()


def base64_forms(hexdigest: str):
    raw = bytes.fromhex(hexdigest)
    return {
        "b64": base64.b64encode(raw).decode(),
        "b64url": base64.urlsafe_b64encode(raw).decode().rstrip("="),
        "b32": base64.b32encode(raw).decode().rstrip("="),
        "b16": raw.hex().upper(),
    }


HASHES = ["md5", "sha1", "sha224", "sha256", "sha384", "sha512",
          "sha3_224", "sha3_256", "sha3_384", "sha3_512", "blake2b", "blake2s",
          "ripemd160", "sm3", "md5-sha1", "sha512_224", "sha512_256"]
HMAX = ["md5", "sha1", "sha256", "sm3"]


def all_outputs(data: bytes, key_salts=()):
    """Every candidate output string for a given input byte string."""
    outs = {}
    h_by_algo = {}
    for algo in HASHES:
        h = digest_family(data, algo)
        h_by_algo[algo] = h
        outs[f"{algo}"] = h
        outs[f"{algo}.upper"] = h.upper()
        outs[f"{algo}.b64"] = base64_forms(h)["b64"]
        outs[f"{algo}.b64url"] = base64_forms(h)["b64url"]
        outs[f"{algo}.b36"] = rebase_forms(h)["b36"]
        outs[f"{algo}.b62"] = rebase_forms(h)["b62"]
        outs[f"{algo}.b32"] = rebase_forms(h)["b32"]
        for rn, rv in base64_forms(h).items():
            outs[f"{algo}.{rn}"] = rv
        # drop-4-hex-char truncations (target is 28 hex = 32-4) at every offset
        if len(h) >= 28 + 4:
            for i in range(len(h) - 28 + 1):
                outs[f"{algo}.drop4[{i}]"] = (h[:i] + h[i + 4:]).upper()
                outs[f"{algo}.drop4[{i}].lower"] = h[:i] + h[i + 4:]
        # drop-2-bytes (4 hex) at byte granularity, uppercase
        if len(h) == 32:
            for i in range(0, 29, 2):
                outs[f"{algo}.drop2bytes[{i}]"] = (h[:i] + h[i + 4:]).upper()
    # double hashing: digest the hex digest, and digest the raw bytes again
    for a1 in HASHES:
        h1 = h_by_algo[a1]
        for a2 in HASHES:
            d1 = digest_family(h1.encode(), a2)
            outs[f"{a2}({a1}hex)"] = d1
            outs[f"{a2}({a1}hex).upper"] = d1.upper()
            d2 = digest_family(h_by_algo[a1].encode("ascii"), a2)
            outs[f"{a2}({a1}bytes)"] = d2
            outs[f"{a2}({a1}bytes).upper"] = d2.upper()
    # crc32
    crc = zlib.crc32(data) & 0xFFFFFFFF
    outs["crc32.hex"] = "%08X" % crc
    outs["crc32.hex.lower"] = "%08x" % crc
    outs["crc32.dec"] = str(crc)
    # hmac with candidate salts as keys
    for k in key_salts:
        kb = k.encode()
        for algo in HMAX:
            try:
                outs[f"hmac-{algo}(key={k})"] = hmac_family(data, kb, algo)
                outs[f"hmac-{algo}(key={k}).upper"] = hmac_family(data, kb, algo).upper()
                # hmac with data as key, salt as message (swap)
                outs[f"hmac-{algo}(msg={k})"] = hmac_family(kb, data, algo)
                outs[f"hmac-{algo}(msg={k}).upper"] = hmac_family(kb, data, algo).upper()
            except Exception:
                pass
    # digest-concat double hashing: hash(digest(input) + digest(salt)) and reverse
    for k in key_salts:
        kb = k.encode()
        for a1 in ("md5", "sha1", "sha256"):
            try:
                d_in = digest_family(data, a1).encode()
                d_s = digest_family(kb, a1).encode()
                for a2 in ("md5", "sha1", "sha256"):
                    outs[f"{a2}({a1}(in)+{a1}(salt={k}))"] = digest_family(d_in + d_s, a2)
                    outs[f"{a2}({a1}(salt={k})+{a1}(in))"] = digest_family(d_s + d_in, a2)
            except Exception:
                pass
    return outs


def match_target(outputs: dict, target: str):
    """target is 28-hex (or any-length) — check exact + every window + rebases."""
    t = target.upper()
    tlow = target.lower()
    hits = []
    for label, val in outputs.items():
        if not val:
            continue
        if val == t or val == tlow:
            hits.append((label, val, "EXACT"))
            continue
        # window match: any 28-char window of a longer output
        for i in range(len(val) - len(target) + 1):
            win = val[i:i + len(target)]
            if win == t or win == tlow:
                hits.append((label, val, f"window[{i}:{i + len(target)}]"))
                break
    return hits


# ---------------------------------------------------------------------------
# sweep
# ---------------------------------------------------------------------------


def fixed_transform_check(gt, inputs_by_channel, salts):
    """If target = T(digest(input)) with the SAME T for every channel, then for any
    two channels the per-channel delta (digest -> target) must be identical.

    Tests T = XOR-mask, additive offset, and per-byte permutation equality.
    A hit here proves pure-function-of-input even before T is named.
    """
    print("\n" + "=" * 78)
    print("Fixed-transform consistency (target = T(digest(input)), same T for all)")
    print("=" * 78)
    if len(gt) < 2:
        print("  need >=2 channels")
        return
    ch0, ch1 = gt[0], gt[1]
    t0 = bytes.fromhex(ch0["cdn_hash"])
    t1 = bytes.fromhex(ch1["cdn_hash"])
    if len(t0) != len(t1):
        print("  target lengths differ — skip")
        return

    def digests_for(ch):
        out = {}
        for lab, val in inputs_by_channel[ch].items():
            if isinstance(val, str):
                data = val.encode()
            else:
                data = val
            for algo in HASHES:
                out[(lab, algo)] = digest_family(data, algo)
        return out

    d0 = digests_for(ch0["channel_code"])
    d1 = digests_for(ch1["channel_code"])
    hits = []
    for key, h0 in d0.items():
        h1 = d1.get(key)
        if not h1 or len(h0) != len(t0):
            continue
        b0, b1 = bytes.fromhex(h0), bytes.fromhex(h1)
        x0 = bytes(a ^ b for a, b in zip(b0, t0))
        x1 = bytes(a ^ b for a, b in zip(b1, t1))
        if x0 == x1 and x0 != bytes(len(t0)):
            hits.append((key, "XOR-mask", x0.hex()))
        # additive (mod 256) delta
        a0 = bytes((a + b) % 256 for a, b in zip(b0, t0))
        a1 = bytes((a + b) % 256 for a, b in zip(b1, t1))
        if a0 == a1 and a0 != bytes(len(t0)):
            hits.append((key, "ADD-delta", a0.hex()))
        # byte-permutation: is target a permutation of digest bytes?
        if sorted(b0) == sorted(t0) and sorted(b1) == sorted(t1):
            hits.append((key, "PERMUTATION", ""))
    for lab, kind, detail in hits:
        print(f"  CONSISTENT-HIT input={lab!r} T={kind} {detail}")
    if not hits:
        print("  no shared fixed transform found")
    return hits


def sweep():
    corpus = load_corpus()
    gt = corpus["ground_truth"]
    salts = corpus["salts_candidates"]

    # per-channel input surface
    inputs_by_channel = {}  # channel -> [(label, bytes)]
    epg_index = {ch["channel_code"]: i for i, ch in enumerate(gt)}
    channels_epg = load_channels()
    for i, ch in enumerate(gt):
        inputs = {}
        for lab, val in code_variants(ch["channel_code"]).items():
            inputs[f"code:{lab}"] = val
        for lab, val in raw_code_variants(ch.get("raw_channel_code") or "").items():
            inputs[f"raw:{lab}"] = val
        # EPG position (1-based and 0-based) + zero-padded forms
        for pad in (0, 1, 2, 3):
            p0 = f"{i:0{pad}d}" if pad else str(i)
            p1 = f"{i + 1:0{pad}d}" if pad else str(i + 1)
            inputs[f"idx0[{pad}]"] = p0
            inputs[f"idx1[{pad}]"] = p1
        # REAL EPG position in the 344-channel list
        if channels_epg:
            real = channels_epg.index(ch["channel_code"]) if ch["channel_code"] in channels_epg else None
            if real is not None:
                for pad in (0, 1, 2, 3, 4):
                    pr = f"{real:0{pad}d}" if pad else str(real)
                    inputs[f"real_idx0[{pad}]"] = pr
                    inputs[f"real_idx1[{pad}]"] = str(real + 1) if not pad else f"{real + 1:0{pad}d}"
        inputs_by_channel[ch["channel_code"]] = inputs

    # input × salt concatenations
    def salted(inputs):
        extra = {}
        for lab, val in inputs.items():
            if val is None or val == "":
                continue
            b = val if isinstance(val, bytes) else val.encode()
            for s in salts:
                if not s:
                    continue
                sb = s.encode()
                extra[f"{lab}+salt[{s}]"] = b + sb
                extra[f"salt[{s}]+{lab}"] = sb + b
                extra[f"{lab}+sep-+salt[{s}]"] = b + b"-" + sb
                extra[f"{lab}+sep_+salt[{s}]"] = b + b"_" + sb
        return extra

    results = []
    for ch in gt:
        code = ch["channel_code"]
        target = ch["cdn_hash"]
        inputs = inputs_by_channel[code]
        inputs = {**inputs, **salted(inputs)}
        hits_for_channel = []
        for lab, val in inputs.items():
            if isinstance(val, str):
                data = val.encode()
            else:
                data = val
            outs = all_outputs(data, key_salts=salts)
            for hit in match_target(outs, target):
                hits_for_channel.append((lab, hit[0], hit[2]))
        results.append((ch, hits_for_channel))

    # report
    print("=" * 78)
    print("TeleLatino hash-derivation sweep vs ground truth")
    print("=" * 78)
    for ch, hits in results:
        print(f"\n### {ch['channel_code']}  target={ch['cdn_hash']}")
        if not hits:
            print("    NO HIT — no digest/encoding/window/salt combination matched.")
        else:
            for lab, fn, how in hits:
                print(f"    HIT  input={lab!r}  fn={fn!r}  {how}")

    # cross-channel consistency: formulas that hit BOTH channels
    print("\n" + "=" * 78)
    print("Cross-channel consistency (formula must hit BOTH channels)")
    print("=" * 78)
    all_hits = {}
    for ch, hits in results:
        for lab, fn, how in hits:
            key = (lab, fn)
            all_hits.setdefault(key, []).append((ch["channel_code"], how))
    for key, lst in sorted(all_hits.items()):
        chans = [c for c, _ in lst]
        tag = "BOTH ✅" if len(lst) == len(gt) else "partial"
        print(f"  [{tag}] input={key[0]!r} fn={key[1]!r} -> {chans}")

    n_channels_hit = sum(1 for _, h in results if h)
    print(f"\nchannels with >=1 hit: {n_channels_hit}/{len(gt)}")

    fixed_transform_check(gt, inputs_by_channel, salts)
    return results


# ---------------------------------------------------------------------------
# table generation (post-derivation)
# ---------------------------------------------------------------------------


def gen_table(formula_input: str, formula_fn: str):
    """Rebuild the 344-channel table once a formula is confirmed.

    formula_input: template with {name} / {code} placeholders.
    formula_fn: one of the digest labels produced by all_outputs().
    """
    channels = load_channels()
    rows = []
    for code in channels:
        name = code
        for p in ("cyx-", "cyx_", "cyx"):
            if name.startswith(p):
                name = name[len(p):]
                break
        inp = formula_input.format(name=name, code=code)
        outs = all_outputs(inp.encode())
        out = outs.get(formula_fn)
        if out is None:
            raise SystemExit(f"unknown formula fn {formula_fn}")
        hash28 = out.upper()
        rows.append((code, hash28, f"/live/cyx-{hash28}.m3u8"))
    return rows


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--table", action="store_true",
                    help="emit 344-channel table (requires confirmed formula)")
    ap.add_argument("--input", default=None, help="formula input template, e.g. 'cyx-{name}'")
    ap.add_argument("--fn", default=None, help="formula digest fn label, e.g. 'md5.upper'")
    args = ap.parse_args()

    if args.table:
        if not (args.input and args.fn):
            raise SystemExit("--table requires --input and --fn")
        rows = gen_table(args.input, args.fn)
        out_path = os.path.join(HERE, "corpus", "channel_hash_table_344.csv")
        with open(out_path, "w", encoding="utf-8") as f:
            f.write("channel_code,cyx_hash,m3u8_path\n")
            for r in rows:
                f.write(",".join(r) + "\n")
        print(f"wrote {len(rows)} rows -> {out_path}")
        return

    sweep()


if __name__ == "__main__":
    main()
