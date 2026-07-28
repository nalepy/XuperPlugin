#!/usr/bin/env python3
"""Extract TLS SNI and HTTP Host from portal capture pcaps."""
from __future__ import annotations

import json
import re
import sys
from collections import Counter, defaultdict
from pathlib import Path

from scapy.all import IP, Raw, TCP, rdpcap

KNOWN = {
    "hbyyqx.qtg20rybb.xyz",
    "emowvv.dqiswip4.xyz",
    "dfcsq.divqohamz.com",
    "sfgknh.qho3cnsyil.com",
    "rokbd.ysrkwctjg.com",
    "iyut.xgw3sdzoac.com",
    "vgwbm.uwfyobivh.com",
    "espjey.ysnihrwtg.com",
    "sxowvd.jzvqwcyor.com",
    "yrqucu.czxenpyba.com",
    "fuxok.nguvmqhpk.com",
    "mptec.dhkrxuzcy.com",
    "xsvs.evlslb.com",
    "xsvs.vfltbr.com",
    "cdsr.higoesutn.com",
    "magloud.y6oseldsc.online",
    "vdes.medika7c7.com",
    "34fhwevf.cbcf4gg3f.com",
    "eskna.ucpjdhivl.com",
    "yvhcn.hxjebagrv.com",
    "zxiws.tcgwhnvym.com",
    "nxiqj.jgrqyxupl.com",
    "sgyc.bfj1k2g4v.com",
    "ycout.yxponte.com",
}

SKIP_SUBSTR = (
    "yandex",
    "google",
    "gstatic",
    "globalsign",
    "appmetrica",
    "gvt1",
    "android",
    "webvisor",
)


def parse_sni(payload: bytes) -> str | None:
    if len(payload) < 43 or payload[0] != 0x16:
        return None
    i = 5
    if i >= len(payload) or payload[i] != 0x01:
        return None
    i += 4 + 2 + 32
    sid_len = payload[i]
    i += 1 + sid_len
    cs_len = int.from_bytes(payload[i : i + 2], "big")
    i += 2 + cs_len
    comp_len = payload[i]
    i += 1 + comp_len
    if i + 2 > len(payload):
        return None
    ext_len = int.from_bytes(payload[i : i + 2], "big")
    i += 2
    end = i + ext_len
    while i + 4 <= end:
        etype = int.from_bytes(payload[i : i + 2], "big")
        elen = int.from_bytes(payload[i + 2 : i + 4], "big")
        i += 4
        if etype == 0 and elen >= 5:
            j = i + 2
            while j + 3 <= i + elen:
                name_type = payload[j]
                name_len = int.from_bytes(payload[j + 1 : j + 3], "big")
                j += 3
                if name_type == 0 and name_len > 0:
                    return payload[j : j + name_len].decode("ascii", "replace")
                j += name_len
        i += elen
    return None


PORTAL_HOST_MARKERS = ("sgyc", "ycout")


def classify_body(body: bytes) -> str:
    if not body:
        return "empty"
    sample = body[:200]
    text = sample.decode("utf-8", errors="replace").strip()
    if text.startswith(("{", "[")):
        try:
            json.loads(text)
            return "json_cleartext"
        except json.JSONDecodeError:
            pass
    printable = sum(1 for b in sample if 32 <= b < 127 or b in (9, 10, 13))
    if len(sample) and printable / len(sample) > 0.85 and text.startswith(("{", "[")):
        return "json_cleartext"
    if len(sample) and printable / len(sample) > 0.9:
        return "text_cleartext"
    return "binary_or_encrypted"


def extract_portal_http_requests(pcap: Path) -> list[dict]:
    """First HTTP request line + Host + body preview for sgyc/ycout hosts."""
    findings: list[dict] = []
    seen: set[tuple[str, str, str]] = set()
    pkts = rdpcap(str(pcap))
    req_start = re.compile(rb"^(GET|POST|PUT|HEAD|OPTIONS)\s+(\S+)\s+HTTP/", re.M)

    for p in pkts:
        if not p.haslayer(TCP) or not p.haslayer(Raw):
            continue
        if int(p[TCP].dport) != 80 and int(p[TCP].sport) != 80:
            continue
        raw = bytes(p[Raw].load)
        if not req_start.search(raw[:64]):
            continue
        text = raw.decode("latin-1", errors="replace")
        first_line = text.split("\r\n", 1)[0]
        host_m = re.search(r"Host:\s*([^\r\n]+)", text, re.I)
        if not host_m:
            continue
        host = host_m.group(1).strip().split(":")[0]
        if not any(m in host.lower() for m in PORTAL_HOST_MARKERS):
            continue
        method_m = re.match(r"^(GET|POST|PUT|HEAD|OPTIONS)\s+(\S+)", first_line)
        if not method_m:
            continue
        method, path = method_m.group(1), method_m.group(2)
        key = (host, method, path)
        if key in seen:
            continue
        seen.add(key)
        headers: dict[str, str] = {}
        for name in ("apkVer", "apk", "Cookie"):
            hm = re.search(rf"^{name}:\s*([^\r\n]+)", text, re.I | re.M)
            if hm:
                headers[name] = hm.group(1).strip()
        body_sep = raw.find(b"\r\n\r\n")
        body = raw[body_sep + 4 : body_sep + 4 + 200] if body_sep >= 0 else b""
        findings.append(
            {
                "host": host,
                "method": method,
                "path": path,
                "request_line": first_line,
                "headers": headers,
                "body_preview_hex": body[:200].hex() if body else "",
                "body_preview_repr": body[:200].decode("utf-8", errors="replace"),
                "body_kind": classify_body(body),
            }
        )
    return findings


def print_portal_http(findings: list[dict]) -> None:
    print("\n=== Portal HTTP (sgyc / ycout) ===")
    if not findings:
        print("(no matching HTTP requests in cleartext segments)")
        return
    for i, f in enumerate(findings, 1):
        print(f"\n--- request {i} ---")
        print(f"request_line: {f['request_line']}")
        print(f"host: {f['host']}")
        print(f"method: {f['method']}")
        print(f"path: {f['path']}")
        for k, v in f["headers"].items():
            show = v if len(v) <= 120 else v[:117] + "..."
            print(f"header {k}: {show}")
        print(f"body_kind: {f['body_kind']}")
        if f["body_preview_repr"].strip():
            prev = f["body_preview_repr"].replace("\n", "\\n")
            if len(prev) > 200:
                prev = prev[:200] + "..."
            print(f"body_preview: {prev}")
        elif f["body_preview_hex"]:
            print(f"body_preview_hex: {f['body_preview_hex'][:120]}...")


def tag(host: str) -> str:
    return "pool" if host in KNOWN else "NEW"


def analyze(pcap: Path) -> None:
    snis: dict[str, dict] = defaultdict(lambda: {"n": 0, "dst": set()})
    hosts: dict[str, int] = defaultdict(int)
    pkts = rdpcap(str(pcap))
    for p in pkts:
        if not p.haslayer(TCP) or not p.haslayer(Raw):
            continue
        raw = bytes(p[Raw].load)
        dst = p[IP].dst if p.haslayer(IP) else "?"
        dport = int(p[TCP].dport)
        sport = int(p[TCP].sport)
        if dport == 443 or sport == 443:
            sni = parse_sni(raw)
            if sni:
                snis[sni]["n"] += 1
                snis[sni]["dst"].add(dst)
        if dport == 80 or sport == 80:
            text = raw.decode("latin-1", errors="ignore")
            m = re.search(r"Host:\s*([^\r\n]+)", text, re.I)
            if m:
                h = m.group(1).strip().split(":")[0]
                hosts[h] += 1

    print(f"=== {pcap.name} ({len(pkts)} packets) ===\n")
    print("=== TLS SNI ===")
    for h, v in sorted(snis.items(), key=lambda x: -x[1]["n"]):
        dsts = sorted(v["dst"])[:5]
        print(f"{v['n']:4d} [{tag(h)}] {h} -> {dsts}")

    print("\n=== HTTP Host (cleartext) ===")
    for h, c in sorted(hosts.items(), key=lambda x: -x[1]):
        if any(s in h for s in SKIP_SUBSTR):
            continue
        print(f"{c:4d} [{tag(h)}] {h}")

    blob = pcap.read_bytes().decode("latin-1", errors="ignore")
    pat = re.findall(r"[a-z0-9]{4,}\.[a-z0-9]{2,12}\.(?:com|xyz|online|net)", blob)
    cnt = Counter(pat)
    print("\n=== domain-like strings (top hits, filtered) ===")
    for dom, n in cnt.most_common(80):
        if any(s in dom for s in SKIP_SUBSTR):
            continue
        print(f"{n:3d} [{tag(dom)}] {dom}")

    portal = extract_portal_http_requests(pcap)
    print_portal_http(portal)


if __name__ == "__main__":
    paths = sys.argv[1:] or [str(Path(__file__).resolve().parent / "portal_cold.pcap")]
    for p in paths:
        analyze(Path(p))
