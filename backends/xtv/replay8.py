#!/usr/bin/env python3
"""replay8.py — replay the CAPTURED accepted portalCore request from Win11.
Tests whether the captured request (fresh b29/reserve1/sn from .8) is accepted off-device.
The gate is pre-body, so our 3DES envelope is fine — the result isolates content-vs-connection."""
import json, base64, sys, ssl, urllib3, warnings
warnings.filterwarnings("ignore")
import requests
from Crypto.Cipher import DES3
from Crypto.Util.Padding import pad

KEY_B64 = "2b494e53756c664c2f44465245733572"

# ---- the captured .8 request (from the live app's DoHttpSec heap log) ----
BODY_PLAIN = {
    "apkVersion": "43405",
    "appId": "com.android.msandroid",
    "appLanguage": "en",
    "b29": "4435652b4d4641546c6963733953743863376b75424f474c576b5a6d41326e6474724e534b567937426a6e4c556e72384136647252773d3d",
    "contentType": "application/json;charset=utf-8",
    "cpu": "armeabi-v7a",
    "deviceToken": "",
    "hardwareInfo": "amlogic",
    "loginType": "2",
    "model": "SM-G973F",
    "portalCode": "",
    "product": "Galaxy S10",
    "reserve1": "76356c476568424f4a38334761645a697957757344673d3d",
    "sdkVer": 25,
    "sn": "1ecb8d3244c6460059e85db8f0d47fbb",
    "sysVersion": "2018-09-18 11:30:06_25_7.1.2_3.14.29",
}

HEADERS = {
    "Content-type": "application/json;charset=utf-8",
    "apk": "com.android.msandroid",
    "apkVer": "43405",
    "spkgVer": "2018-09-18 11:30:06_25_7.1.2_3.14.29",
    "Cache-Control": "no-store",
}

HOSTS = ["eskna.ucpjdhivl.com", "espjey.ysnihrwtg.com", "sxowvd.jzvqwcyor.com",
         "yrqucu.czxenpyba.com", "ioermd.l7hsgo8g.com"]
ENDPOINTS = ["/api/portalCore/getEmailSuffix", "/api/portalCore/config/get", "/api/portalCore/v8/active"]

def wire(body_dict, extra=None):
    b = dict(body_dict)
    if extra: b.update(extra)
    plain = json.dumps(b, separators=(",", ":")).encode()
    key = base64.b64decode(KEY_B64)
    ct = DES3.new(key, DES3.MODE_ECB).encrypt(pad(plain, 8))
    return base64.b64encode(ct).hex(), plain


def try_one(host, ep, body_dict, extra=None, tag=""):
    wb, plain = wire(body_dict, extra)
    url = f"https://{host}{ep}"
    try:
        r = requests.post(url, data=wb, headers=HEADERS, timeout=40, verify=False)
        body = r.text[:400]
        verdict = "PORTAL200001" if "portal200001" in body else ("RETURNCODE-0" if ('"returnCode":"0"' in body or '"returnCode":0' in body) else "OTHER")
        print(f"[{tag}] {host}{ep} -> HTTP {r.status_code} [{verdict}] {body[:220]}", flush=True)
        return verdict, r.status_code, body
    except Exception as e:
        print(f"[{tag}] {host}{ep} -> ERR {e}", flush=True)
        return "ERR", 0, str(e)


print("=== replay captured getEmailSuffix (fresh .8 tokens) ===", flush=True)
results = {}
for h in HOSTS:
    v, c, b = try_one(h, ENDPOINTS[0], BODY_PLAIN, tag="getEmailSuffix")
    results[(h, ENDPOINTS[0])] = (v, c, b)

print("\n=== replay captured v8/active (device activation) ===", flush=True)
ACTIVE_EXTRA = {"authCode": "", "authVersion": "", "channel": "default",
                "macAddr": "06:41:80:91:CD:6E", "matadata": "", "openNum": 15,
                "signdata": "", "snToken": ""}
for h in ["eskna.ucpjdhivl.com", "espjey.ysnihrwtg.com"]:
    v, c, b = try_one(h, ENDPOINTS[2], BODY_PLAIN, ACTIVE_EXTRA, tag="v8/active")
    results[(h, ENDPOINTS[2])] = (v, c, b)

json.dump({f"{k[0]}{k[1]}": {"verdict": v, "http": c, "body": b} for k, v in results.items()},
          open("replay_results.json", "w"), indent=1)
print("\nresults saved to replay_results.json", flush=True)
