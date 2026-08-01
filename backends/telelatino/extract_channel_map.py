#!/usr/bin/env python3
"""
extract_channel_map.py — mine the TeleLatino app's Java heap dump (hprof) for
channel records. The hprof stores strings as printable UTF-8 tokens separated by
binary object metadata. Channel rows serialize in this order:

    <signed-url-A> <hash-A> 1 <uuid> <NAME> <cyx-CODE> [] <NAME> 1 0 <BUSS>
    h264 x" icon <icon-url> poster <poster-url> 4 <signed-url-B> <hash-B> ...

Verified ground truth:
    cyx-Cinemax -> C9EB0B2644979328E598EAFED311   (recipe + this heap's 4-field)
    cyx-LaRedHD -> 1F3251F9425197449B94E006D8EB   (recipe + this heap's 4-field)
So for each `<cyx-CODE> []` row, the hash is the `media_code=` value in the
signed URL that follows the `4` marker (which follows the poster URL).

Usage: python3 extract_channel_map.py <hprof> <out.json>
"""
import re
import sys
import json

TOKEN_RE = re.compile(rb'[!-~ ]{1,}')          # printable runs (len>=1)
IMG_RE = re.compile(rb'https?://[^\s"\'<>]{4,}')  # icon/poster URLs
URL_RE = re.compile(rb'media_code=(cyx[_-][A-Za-z0-9_-]{2,64})')

def tokenize(data):
    toks = []
    for m in TOKEN_RE.finditer(data):
        t = m.group().decode('latin-1')
        t = t.rstrip('!"\'')        # hprof strings are followed by 0x21 / 0x22 bytes
        if len(t) < 2 and not (len(t) == 1 and t.isdigit()):
            continue
        if not (t[0].isalnum() or t[0] in '/[]'):
            continue
        if not any(c.isalnum() for c in t):
            if t != '[]':
                continue
        toks.append((m.start(), t))
    return toks

def main():
    if len(sys.argv) < 3:
        print(__doc__)
        sys.exit(1)
    data = open(sys.argv[1], 'rb').read()
    toks = tokenize(data)
    print(f"tokens: {len(toks)}")

    # channel codes: cyx-Name / cyx_name tokens that are NOT themselves hashes
    # (a hash is 28 hex chars; identity codes are cyx_<long digits/hex>).
    def looks_code(t):
        if not (t.startswith('cyx-') or t.startswith('cyx_')):
            return False
        body = t[4:]
        if len(body) == 28 and all(c in '0123456789abcdefABCDEF' for c in body):
            return False  # a hash token
        return True

    pairs = {}      # code -> Counter(hash)
    identity = set()  # cyx_ codes used verbatim as media_code
    row_counts = 0

    for i, (off, tok) in enumerate(toks):
        if not looks_code(tok):
            continue
        code = tok
        # Per-occurrence: the record's own hash sits after its `4` marker
        # (followed by <URL> <HASH> <1> <uuid>). Find the FIRST `4` after the
        # code token, then the signed URL after it. If no `4` in window, fall
        # back to the first signed URL. Neighbor chains add votes for OTHER
        # hashes; the modal vote across serializations still wins.
        h = None
        jend = min(i + 120, len(toks))
        i4 = None
        for j in range(i + 1, jend):
            if toks[j][1] == '4':
                i4 = j
                break
        if i4 is not None:
            for k in range(i4 + 1, min(i4 + 14, len(toks))):
                u = re.search(rb'media_code=(cyx[_-][A-Za-z0-9_-]{2,64})',
                              toks[k][1].encode('latin-1'))
                if u:
                    h = u.group(1).decode('latin-1')
                    break
        if h is None:
            for j in range(i + 1, jend):
                u = re.search(rb'media_code=(cyx[_-][A-Za-z0-9_-]{2,64})',
                              toks[j][1].encode('latin-1'))
                if u:
                    h = u.group(1).decode('latin-1')
                    break
        if h:
            pairs.setdefault(code, {}).__setitem__(h, pairs.get(code, {}).get(h, 0) + 1)
            row_counts += 1

    # identity channels: any signed-URL media_code == its own code token
    for off, t in toks:
        u = re.search(rb'media_code=(cyx[_-][A-Za-z0-9_-]{2,64})',
                      t.encode('latin-1'))
        if u:
            mc = u.group(1).decode('latin-1')
            if mc.startswith('cyx_'):
                identity.add(mc)

    # modal hash per code: the same channel serializes identically across the
    # e9b37d / free / ts listings, so the most-voted candidate is the hash.
    mapped = {}
    multi = {}
    for k, votes in sorted(pairs.items()):
        best = max(votes.items(), key=lambda kv: (kv[1], -len(kv[0])))
        mapped[k] = best[0]
        if len(votes) > 1:
            multi[k] = votes

    out = {
        "mapped": mapped,
        "multi_hash_channels": multi,
        "identity_codes_seen": sorted(identity),
        "stats": {"channel_rows_scanned": row_counts,
                  "mapped_count": len(mapped),
                  "multi_count": len(multi),
                  "identity_count": len(identity)},
    }
    with open(sys.argv[2], 'w', encoding='utf-8') as f:
        json.dump(out, f, indent=1, ensure_ascii=False)
    print(f"rows: {row_counts}, mapped: {len(mapped)}, multi: {len(multi)}, identity: {len(identity)}")

if __name__ == '__main__':
    main()
