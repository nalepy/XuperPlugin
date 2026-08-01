#!/usr/bin/env python3
# extract_records.py — carve DoHttpSec request/response records from an alldump output dir.
# usage: extract_records.py <glob-of-bins> [out.json]
import re, glob, json, sys

def extract(path_pattern, out):
    records = []
    for fn in glob.glob(path_pattern):
        try:
            data = open(fn, 'rb').read()
        except Exception:
            continue
        for m in re.finditer(rb'\{"session"', data):
            s = m.start(); depth = 0; instr = False; esc = False; e = s
            while e < len(data):
                c = data[e]
                if instr:
                    if esc: esc = False
                    elif c == 0x5c: esc = True
                    elif c == 0x22: instr = False
                else:
                    if c == 0x22: instr = True
                    elif c == 0x7b: depth += 1
                    elif c == 0x7d:
                        depth -= 1
                        if depth == 0: break
                e += 1
            seg = data[s:e+1]
            try:
                rec = json.loads(seg.decode('utf-8', errors='replace').replace('\/', '/'))
                records.append({"file": fn, "offset": hex(s), "record": rec})
            except Exception:
                pass
    json.dump(records, open(out, 'w'), indent=1)
    print(f"{len(records)} records -> {out}")

if __name__ == "__main__":
    pat = sys.argv[1] if len(sys.argv) > 1 else "dmp*.bin"
    out = sys.argv[2] if len(sys.argv) > 2 else "records.json"
    extract(pat, out)
