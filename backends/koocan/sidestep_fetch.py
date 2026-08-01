#!/usr/bin/env python3
"""Decisive sidestep test: launch app -> wait for streaming -> dump memory ->
extract d-cookie + opaque GET paths + m3u8 content -> fetch CDN from Win11."""
import re, subprocess, time, sys, socket

SERIAL = "192.168.100.97:5555"
ADB = ["adb", "-s", SERIAL]

def sh(cmd, timeout=25):
    try:
        return subprocess.run(cmd, capture_output=True, timeout=timeout).stdout
    except Exception:
        return b""

def connect():
    sh(["adb", "kill-server"])
    sh(["adb", "start-server"])
    sh(ADB + ["connect", SERIAL])

connect()
print("launching app...")
sh(ADB + ["shell", "su", "-c", "am force-stop com.global.unitviptv; sleep 1; monkey -p com.global.unitviptv -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1"])
time.sleep(8)
connect()
time.sleep(35)

pid = None
for i in range(10):
    pid = sh(ADB + ["shell", "su", "-c", "pidof com.global.unitviptv"]).decode().strip()
    if pid:
        break
    time.sleep(6)
print("pid:", pid)
if not pid:
    sys.exit(1)

# wait for decoder activity
for i in range(8):
    n = sh(ADB + ["shell", "su", "-c", "logcat -d -t 30 2>/dev/null | grep -c ROCKCHIP_VIDEO_DEC"]).decode().strip()
    if n and int(n) > 0:
        print("streaming active, decoder events:", n)
        break
    time.sleep(5)

# dump malloc regions
out = sh(ADB + ["shell", "su", "-c", f"cat /proc/{pid}/maps"]).decode("utf-8", "replace")
regions = []
for line in out.splitlines():
    parts = line.split()
    if len(parts) >= 6 and parts[5] == "[anon:libc_malloc]":
        rng = parts[0].split("-")
        if len(rng) == 2:
            regions.append((int(rng[0], 16), int(rng[1], 16)))
blob = b""
for start, end in regions[:18]:
    size = end - start
    sh(ADB + ["shell", "su", "-c", f"/data/local/tmp/vmread {pid} {start:x} {size} /data/local/tmp/q.bin"])
    b = sh(ADB + ["shell", "su", "-c", "cat /data/local/tmp/q.bin"])
    if b:
        blob += b
print(f"dumped {len(blob)} bytes")
open("unitv_final2.bin", "wb").write(blob)

# extract
cookies = set(re.findall(rb'Cookie: d=([A-Za-z0-9_-]{40,})', blob))
opaque = set(re.findall(rb'GET (/(?:[a-z0-9]{4,40}|live/[^\x00-\x1f]{5,120})) HTTP', blob))
m3u8s = set(re.findall(rb'([a-z0-9_]{6,50}\.m3u8)', blob))
print("d-cookies:", [c[:50] for c in cookies])
print("opaque GETs:", [o.decode()[:90] for o in list(opaque)[:10]])
print("m3u8:", [m.decode() for m in list(m3u8s)[:6]])

# fetch each opaque path with each d-cookie from Win11
for ck in cookies:
    for path in list(opaque)[:8]:
        p = path.decode()
        try:
            s = socket.create_connection(("23.95.95.186", 13159), timeout=8)
            req = f"GET {p} HTTP/1.1\r\nHost: 23.95.95.186:13159\r\nConnection: close\r\nCookie: d={ck.decode()}\r\n\r\n"
            s.send(req.encode())
            resp = b""
            while True:
                c = s.recv(65536)
                if not c: break
                resp += c
            s.close()
            head, _, body = resp.partition(b"\r\n\r\n")
            line1 = head.split(b"\r\n")[0].decode('utf-8', 'replace') if head else "?"
            print(f"GET {p[:60]} d={ck[:20]}... -> {line1} body={len(body)}")
            if b'#EXT' in body:
                print("   M3U8:", body[:400])
            if b'MP2T' in head or b'video' in head:
                print("   >>> SEGMENT CONTENT!")
        except Exception as e:
            print(f"GET {p[:50]}: ERR {str(e)[:60]}")
