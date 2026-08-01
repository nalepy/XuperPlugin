#!/usr/bin/env python3
"""Transparent logging relay for the luna HTTP server (23.95.95.186:13159).

The box's outbound luna traffic is DNAT'd to localhost:13159 and forwarded
here via `adb reverse tcp:13159 tcp:13159`. This relay logs every request
(path + full Cookie header) then forwards it to the real luna server.
"""
import socket
import threading
import time
import sys

REAL_HOST = "23.95.95.186"
REAL_PORT = 13159
LISTEN_PORT = 13159
LOG = open("luna_relay.log", "a", buffering=1)


def log(msg):
    print(f"[{time.strftime('%H:%M:%S')}] {msg}", flush=True)


def forward(sock_client, target_host, target_port):
    """Forward client socket to target; return (request_bytes, response_bytes)."""
    up = b""
    down = b""
    try:
        sock_up = socket.create_connection((target_host, target_port), timeout=15)
        # pump client -> target, capturing request
        while True:
            data = sock_client.recv(65536)
            if not data:
                break
            up += data
            sock_up.sendall(data)
            if b"\r\n\r\n" in up:
                # request head captured; keep reading but log only once
                break
        # keep draining remaining request body if any
        sock_client.settimeout(1.0)
        try:
            while True:
                data = sock_client.recv(65536)
                if not data:
                    break
                up += data
                sock_up.sendall(data)
        except socket.timeout:
            pass
        sock_client.settimeout(30.0)
        # pump target -> client
        while True:
            data = sock_up.recv(65536)
            if not data:
                break
            down += data
            sock_client.sendall(data)
    except Exception as e:
        log(f"relay error: {e}")
    finally:
        try:
            sock_up.close()
        except Exception:
            pass
    return up, down


def handle(conn, addr):
    try:
        conn.settimeout(30.0)
        up, down = forward(conn, REAL_HOST, REAL_PORT)
        # log the request head + cookie
        head = up.split(b"\r\n\r\n")[0]
        text = head.decode("latin1", "replace")
        lines = text.split("\r\n")
        reqline = lines[0] if lines else ""
        cookie = ""
        for l in lines[1:]:
            if l.lower().startswith("cookie:"):
                cookie = l
        resp_line = ""
        if b"\r\n" in down:
            resp_line = down.split(b"\r\n")[0].decode("latin1", "replace")
        log(f"{addr[0]} {reqline} -> {resp_line}")
        if cookie:
            log(f"  COOKIE: {cookie[:80]}...")
            with open("luna_relay_cookies.txt", "a") as f:
                f.write(f"{time.strftime('%Y-%m-%d %H:%M:%S')} {reqline}\n{cookie}\n")
    except Exception as e:
        log(f"handle error: {e}")
    finally:
        try:
            conn.close()
        except Exception:
            pass


def main():
    srv = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    srv.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    srv.bind(("127.0.0.1", LISTEN_PORT))
    srv.listen(64)
    log(f"relay listening on 127.0.0.1:{LISTEN_PORT} -> {REAL_HOST}:{REAL_PORT}")
    while True:
        conn, addr = srv.accept()
        threading.Thread(target=handle, args=(conn, addr), daemon=True).start()


if __name__ == "__main__":
    main()
