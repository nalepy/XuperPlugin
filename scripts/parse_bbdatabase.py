#!/usr/bin/env python3
import re
import sqlite3
from pathlib import Path

db = Path(__file__).resolve().parent.parent / "_session" / "BBDatabase.db"
con = sqlite3.connect(db)
tables = [r[0] for r in con.execute("SELECT name FROM sqlite_master WHERE type='table'")]
print("tables:", tables)
for t in tables:
    cols = [c[1] for c in con.execute(f"PRAGMA table_info({t})")]
    print(f"  {t}: {cols}")
    rows = con.execute(f"SELECT * FROM {t}").fetchall()
    print(f"    rows={len(rows)}")
    for row in rows:
        s = "|".join("" if x is None else str(x) for x in row)
        if re.search(r"domain|DES|portal|Sz0J|host", s, re.I):
            print("---", t)
            print(s[:800])
            print()

# also extract all domain|DES values via regex over whole db bytes
blob = db.read_bytes()
for m in re.finditer(rb'domain\|DES["\']?\s*[,:]?\s*"?value"?\s*[:=]\s*"([^"]+)"', blob):
    print("REGEX", m.group(1).decode())
for m in re.finditer(rb'Sz0JjjU4YRgGRpH1paF7wlkgQ43Df/4y|4hv\+FZGcrdsJh3Y7\+zl8w1kgQ43Df/4y|MP5TBkYzwo1YVMusyj8vxlkgQ43Df/4y', blob):
    print("BLOB at", m.start(), m.group().decode())
