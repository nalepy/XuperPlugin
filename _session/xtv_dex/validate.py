import base64, json, requests, warnings
warnings.filterwarnings("ignore")
from Crypto.Cipher import DES3
from Crypto.Util.Padding import pad, unpad

KEY = base64.b64decode("2b494e53756c664c2f44465245733572")  # 24 bytes 3DES
def enc(plain):
    c=DES3.new(KEY, DES3.MODE_ECB)
    ct=c.encrypt(pad(plain.encode(),8))
    b64=base64.b64encode(ct).decode()
    return b64.encode().hex()   # per-char hex of ascii base64
def dec(wire):
    try:
        b64=bytes.fromhex(wire).decode()
        ct=base64.b64decode(b64)
        c=DES3.new(KEY, DES3.MODE_ECB)
        return unpad(c.decrypt(ct),8).decode('utf-8','replace')
    except Exception as e:
        return f"<dec fail: {e}>"

B29="4f6f786b4b5a7a3933666842554e6c55717338584b71325a3635436b4e463736583442714b345572434a504c556e72384136647252773d3d"
RES1="76356c476568424f4a38334761645a697957757344673d3d"
common=dict(apkVersion="43405",appId="com.android.msandroid",appLanguage="es",
  cpu="armeabi-v7a",deviceToken="",hardwareInfo="sun50iw9p1",loginType="2",
  model="V76PRO",portalCode="masnew",product="walley",reserve1=RES1,sdkVer=29,
  sn="ca0e53edac957b8f6f187528933355f1",sysVersion="2024-11-15 19:08:51_29_14.1_4.9.170",
  lang="es",type="1",userId="169355704",userToken="6da3c458-b2de-4798-86a7-57028fb25b27")

def body(key29):
    d=dict(common); d[key29]=B29; return json.dumps(d)

hosts=["espjey.ysnihrwtg.com","dfcsq.divqohamz.com","yrqucu.czxenpyba.com",
       "eskna.ucpjdhivl.com","cdsr.higoesutn.com","fuxok.nguvmqhpk.com","mptec.dhkrxuzcy.com"]
hdr={"Content-Type":"application/json;charset=utf-8","apk":"com.android.msandroid",
     "apkVer":"43405","spkgVer":common["sysVersion"],"Cache-Control":"no-store",
     "User-Agent":"okhttp/4.12.0","Accept":"*/*"}
path="/api/portalCore/v9/getAuthInfo"
for h in hosts:
    for label,key in (("b29",False),("B29",True)):
        try:
            wire=enc(body("B29" if key else "b29"))
            r=requests.post(f"http://{h}{path}",data=wire,headers=hdr,timeout=12)
            dd=dec(r.text) if r.text else ""
            rc=""
            try: rc=json.loads(dd).get("returnCode","")
            except: rc=dd[:60]
            print(f"{h:28} {label}  HTTP {r.status_code}  returnCode={rc}")
        except Exception as e:
            print(f"{h:28} {label}  ERR {type(e).__name__}: {str(e)[:50]}")
