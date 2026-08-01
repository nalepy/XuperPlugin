#!/usr/bin/env python3
"""
verify_stream.py — end-to-end check: fetch /live/cyx-<HASH>.m3u8 through the
P2P proxy peer and download one .ts segment. Use a hash the app on .4 is
CURRENTLY playing (playlists are minted ~30s and 410 after expiry).
Usage: python3 verify_stream.py <hash-without-cyx-prefix>
"""
import socket
import sys
import subprocess
import os

HASH = sys.argv[1] if len(sys.argv) > 1 else "C9EB0B2644979328E598EAFED311"  # Cinemax
PEER = ("108.181.133.189", 33984)
paths = [f"/live/cyx-{HASH}.m3u8", f"/live/cyx-{HASH}/cyx-{HASH}_xycjco_1.ts"]

def raw_get(path):
    s = socket.socket()
    s.settimeout(15)
    s.connect(PEER)
    req = f"GET {path} HTTP/1.1\r\nHost: {PEER[0]}:{PEER[1]}\r\nConnection: close\r\n\r\n"
    s.sendall(req.encode())
    resp = b""
    while True:
        try:
            d = s.recv(8192)
        except socket.timeout:
            break
        if not d:
            break
        resp += d
    s.close()
    return resp

print(f"[verify] hash=cyx-{HASH} peer={PEER[0]}:{PEER[1]}")
m = raw_get(paths[0])
print(f"[verify] m3u8 -> {len(m)} bytes")
head, _, body = m.partition(b"\r\n\r\n")
print("  status:", head.split(b"\r\n")[0].decode('latin-1', 'replace'))
if body:
    print("  body head:", body[:300].decode('latin-1', 'replace').replace('\n', ' | '))
    # find first .ts segment (absolute or relative)
    import re
    ts = None
    for line in body.decode('latin-1', 'replace').splitlines():
        line = line.strip()
        if line and not line.startswith('#') and line.endswith('.ts'):
            ts = line
            break
    if ts:
        if ts.startswith('/'):
            seg_path = ts
        else:
            seg_path = f"/live/cyx-{HASH}/{ts}"
        print(f"[verify] segment: {ts} -> GET {seg_path}")
        t = raw_get(seg_path)
        _, _, tbody = t.partition(b"\r\n\r\n")
        open(f"_session/verify_{HASH[:8]}.ts", 'wb').write(tbody)
        print(f"[verify] segment -> {len(tbody)} bytes saved")
        if len(tbody) > 188:
            r = subprocess.run(["ffprobe", "-v", "error", "-show_entries",
                                "format=format_name,duration", "-of", "default=nw=1",
                                f"_session/verify_{HASH[:8]}.ts"],
                               capture_output=True, text=True)
            print("[verify] ffprobe:", r.stdout.strip() or r.stderr.strip())
        else:
            print("[verify] segment too small — likely 410/expired")
    else:
        print("[verify] no .ts line found in playlist")
