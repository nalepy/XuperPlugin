#!/usr/bin/env python3
"""XuperPlugin sidestep harvester — reads the Unitv app's live m3u8 window from
.97's memory, serves standard HLS whose segments come from the OPEN CDN.

PROVEN (session 32): the portalCore gate is native-minted tokens we cannot forge,
BUT the Unitv segment tier is OPEN on the CDN hosts (a76ckxbfx.lpqmscuto.com,
tuyt.wtyzqunkv.com) — predictable URLs, no auth. The live window (channel +
variant + rd values) is readable from the running app's memory on the rooted box.

Usage: python hls_harvester.py [--port 8000]
"""
import argparse
import re
import socket
import subprocess
import sys
import time
import http.server
import threading

SERIAL = "192.168.100.97:5555"
# use the working adb (WinGet platform-tools); the PATH adb daemon is flaky
ADB_BIN = "C:/Users/Nestor/AppData/Local/Microsoft/WinGet/Packages/Google.PlatformTools_Microsoft.Winget.Source_8wekyb3d8bbwe/platform-tools/adb.exe"
ADB = [ADB_BIN, "-s", SERIAL]
VMREAD = "/data/local/tmp/vmread"

CDN_FALLBACKS = ["a76ckxbfx.lpqmscuto.com", "tuyt.wtyzqunkv.com"]

SEG_RE = re.compile(rb'(pt_[A-Za-z0-9_]+)_([a-z0-9]+)_(\d{6,12})\.ts')

_last_region = 0  # region that held the newest rd last time


def sh(cmd, timeout=25):
    try:
        return subprocess.run(cmd, capture_output=True, timeout=timeout).stdout
    except Exception:
        return b""


def get_pid():
    return sh(ADB + ["shell", "su", "-c", "pidof com.global.unitviptv"]).decode().strip()


def probe_cdn_host(ch, var, rd, timeout=4):
    """Pick the CDN host that actually serves this channel's segment."""
    for host in CDN_FALLBACKS:
        try:
            s = socket.create_connection((host, 80), timeout=timeout)
            req = (f"GET /live/{ch}/{ch}_{var}_{rd}.ts HTTP/1.1\r\n"
                   f"Host: {host}\r\nConnection: close\r\n"
                   f"Range: bytes=0-4096\r\n\r\n")
            s.send(req.encode())
            resp = b""
            s.settimeout(timeout)
            try:
                while True:
                    c = s.recv(65536)
                    if not c:
                        break
                    resp += c
            except socket.timeout:
                pass
            s.close()
            if b"206" in resp[:64] or b"200" in resp[:64]:
                return host
        except Exception:
            continue
    return CDN_FALLBACKS[0]


def find_live_window(pid):
    """Scan malloc regions for segment NAMES (pt_<ch>_<var>_<rd>.ts) — the
    live window region changes on app restart, so scan the last-known region
    first, then a couple of other small regions; keep the global max rd."""
    global _last_region
    out = sh(ADB + ["shell", "su", "-c", f"cat /proc/{pid}/maps"]).decode("utf-8", "replace")
    regions = []
    for line in out.splitlines():
        parts = line.split()
        if len(parts) >= 6 and parts[5] == "[anon:libc_malloc]":
            rng = parts[0].split("-")
            if len(rng) == 2:
                regions.append((int(rng[0], 16), int(rng[1], 16)))
    if _last_region:
        regions.sort(key=lambda r: (r[0] != _last_region, r[0]))
    best = None  # (rd, channel, variant)
    best_region = 0
    _rds_found = set()
    # scan the cached best region first (it holds the live window), plus
    # fallbacks in case the app restarted and moved regions. Read only 1MB
    # per region (segment names are dense) to keep cycles fast.
    for start, end in regions:
        size = min(end - start, 6 * 1024 * 1024)
        b = None
        for attempt in range(3):  # vmread is intermittently EPERM — retry
            sh(ADB + ["shell", "su", "-c",
                      f"{VMREAD} {pid} {start:x} {size} /data/local/tmp/h5.bin"])
            b = sh(ADB + ["shell", "su", "-c", "cat /data/local/tmp/h5.bin"])
            if b:
                break
        if not b:
            continue
        for m in SEG_RE.finditer(b):
            rd = int(m.group(3))
            _rds_found.add(rd)
            if best is None or rd > best[0]:
                best = (rd, m.group(1).decode(), m.group(2).decode())
                best_region = start
    if not best:
        return None
    _last_region = best_region
    rd_max, ch, var = best
    host = probe_cdn_host(ch, var, rd_max)
    all_rds = sorted(_rds_found, reverse=True)[:6]
    lines = ["#EXTM3U", "#EXT-X-VERSION:3", "#EXT-X-TARGETDURATION:6",
             "#EXT-X-MEDIA-SEQUENCE:" + str(all_rds[-1])]
    for rd in reversed(all_rds):
        lines.append("#EXTINF:5.0,")
        lines.append(f"http://{host}/live/{ch}/{ch}_{var}_{rd}.ts")
    return ("\n".join(lines) + "\n").encode(), ch, var, host, str(rd_max)


class Harvester:
    def __init__(self):
        self.lock = threading.Lock()
        self.playlist = b"#EXTM3U\n#EXT-X-VERSION:3\n"
        self.last_error = ""
        self.info = ""
        self.running = True

    def harvest_once(self):
        pid = get_pid()
        if not pid:
            self.last_error = "app not running"
            return
        r = find_live_window(pid)
        if not r:
            self.last_error = "no live window in memory"
            return
        pl, ch, var, host, rd = r
        with self.lock:
            self.playlist = pl
            self.info = f"ch={ch} var={var} host={host} newest_rd={rd}"
            self.last_error = ""

    def loop(self, interval=2.5):
        while self.running:
            try:
                self.harvest_once()
            except Exception as e:
                self.last_error = str(e)
            time.sleep(interval)


class Handler(http.server.BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path.startswith("/live.m3u8") or self.path == "/":
            with h.lock:
                body = h.playlist
                info = h.info
            self.send_response(200)
            self.send_header("Content-Type", "application/vnd.apple.mpegurl")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)
            sys.stderr.write(f"[m3u8] {info}\n")
        elif self.path == "/status":
            body = (h.info + " | err: " + h.last_error).encode()
            self.send_response(200)
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)
        else:
            self.send_response(404)
            self.end_headers()

    def log_message(self, *a):
        pass


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--port", type=int, default=8000)
    args = ap.parse_args()

    h = Harvester()
    t = threading.Thread(target=h.loop, daemon=True)
    t.start()

    srv = http.server.HTTPServer(("0.0.0.0", args.port), Handler)
    print(f"HLS harvester: http://0.0.0.0:{args.port}/live.m3u8 (LAN-reachable)")
    print("Reading .97 app memory; segments served from the open CDN.")
    try:
        srv.serve_forever()
    except KeyboardInterrupt:
        h.running = False
