#!/usr/bin/env python3
"""Atomic sidestep test: read app memory -> extract CURRENT channel + proxy port
from the SAME snapshot -> probe the local proxy immediately."""
import re, subprocess, time, sys, socket

SERIAL = "192.168.100.97:5555"
ADB = ["adb", "-s", SERIAL]

def sh(cmd, timeout=20):
    try:
        return subprocess.run(cmd, capture_output=True, timeout=timeout).stdout
    except Exception:
        return b""

pid = sh(ADB + ["shell", "su", "-c", "pidof com.global.unitviptv"]).decode().strip()
print("pid:", pid)
if not pid:
    sys.exit(1)

# dump all malloc regions
out = sh(ADB + ["shell", "su", "-c", f"cat /proc/{pid}/maps"]).decode("utf-8", "replace")
regions = []
for line in out.splitlines():
    parts = line.split()
    if len(parts) >= 6 and parts[5] == "[anon:libc_malloc]":
        rng = parts[0].split("-")
        if len(rng) == 2:
            regions.append((int(rng[0], 16), int(rng[1], 16)))
blob = b""
for start, end in regions[:16]:
    size = end - start
    sh(ADB + ["shell", "su", "-c", f"/data/local/tmp/vmread {pid} {start:x} {size} /data/local/tmp/f.bin"])
    b = sh(ADB + ["shell", "su", "-c", "cat /data/local/tmp/f.bin"])
    if b:
        blob += b
print(f"dumped {len(blob)}")

# current channel: from m3u8 file_ids
m3u8s = set(re.findall(rb'([a-z0-9_]{8,50}\.m3u8)', blob))
channels = set()
for m in m3u8s:
    mm = re.match(rb'([a-z0-9_]+)\.m3u8', m)
    if mm: channels.add(mm.group(1).decode())
print("channels:", list(channels)[:8])

# proxy ports from mem:// or 127.0.0.1:port
ports = set()
for m in re.finditer(rb'mem://127\.0\.0\.1:(\d+)', blob):
    ports.add(int(m.group(1)))
for m in re.finditer(rb'127\.0\.0\.1:(\d+)', blob):
    ports.add(int(m.group(1)))
# also global listeners
out2 = sh(ADB + ["shell", "su", "-c", "cat /proc/net/tcp"]).decode("utf-8", "replace")
for line in out2.splitlines():
    parts = line.split()
    if len(parts) > 3 and parts[3] == "0A":
        lp = int(parts[1].split(":")[1], 16)
        if lp > 1024 and lp < 65535:
            ports.add(lp)
print("ports:", sorted(ports)[:12])

# probe each channel x each port
for ch in list(channels)[:4]:
    for port in sorted(ports)[:8]:
        for path in [f"/live/{ch}.m3u8", f"/live/{ch}/{ch}.m3u8", f"/vod/0/{ch}.m3u8", f"/vod/0/{ch}.snapinfo"]:
            try:
                s = socket.create_connection(("127.0.0.1", port), timeout=5)
                req = f"GET {path} HTTP/1.1\r\nHost: :0\r\nConnection: close\r\n\r\n"
                s.send(req.encode())
                resp = b""
                s.settimeout(4)
                try:
                    while True:
                        c = s.recv(65536)
                        if not c: break
                        resp += c
                except socket.timeout: pass
                s.close()
                head, _, body = resp.partition(b"\r\n\r\n")
                line1 = head.split(b"\r\n")[0].decode("utf-8", "replace") if head else "?"
                if "200" in line1 or "206" in line1:
                    print(f"!!! port {port} {path} -> {line1} body={len(body)}")
                    print("    ", body[:400])
                elif "404" not in line1 and "400" not in line1 and line1 != "?":
                    print(f"port {port} {path[:55]} -> {line1}")
            except Exception as e:
                pass
