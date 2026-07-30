#!/usr/bin/env python3
"""Upload, compile, launch detached, then fetch output."""
import paramiko, time

local = r"C:\Users\Nestor\Workspace\Xuper\XuperPlugin\_scratch\Unpack.java"
remote = "/home/nestor/xtv-ghidra/harness/src/main/java/com/xtv/Unpack.java"
remote_log = "/tmp/unpack_lever.log"
apk_local = r"C:\Users\Nestor\Workspace\Xuper\XuperPlugin\_assets\live_base.apk"
apk_remote = "/home/nestor/xtv-ghidra/harness/_assets/live_base.apk"

c = paramiko.SSHClient()
c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
c.connect("192.168.100.40", username="nestor", password="ian20jesus",
          look_for_keys=False, allow_agent=False, timeout=15)

# Upload Unpack.java
sftp = c.open_sftp()
with open(local, "rb") as f:
    sftp.putfo(f, remote)
# Upload APK
import os
if os.path.exists(apk_local):
    c.exec_command("mkdir -p ~/xtv-ghidra/harness/_assets", timeout=10)
    with open(apk_local, "rb") as f:
        sftp.putfo(f, apk_remote)
    print("uploaded APK", os.path.getsize(apk_local), "bytes")
sftp.close()
print("uploaded", remote)

# Compile
MVN = "export PATH=~/xtv-ghidra/maven/bin:$PATH"
c.exec_command(f"{MVN} && cd ~/xtv-ghidra/harness && mvn -q compile 2>&1 | tail -20", timeout=120)
time.sleep(5)
print("compiled")

# Launch detached with nohup
c.exec_command(f"rm -f {remote_log}", timeout=10)
c.exec_command(
    f"cd ~/xtv-ghidra/harness && "
    f'CP="target/classes:$(cat ~/xtv-ghidra/cp.txt)" && '
    f"nohup timeout 1800 java -Xmx3g -Djava.library.path=~/xtv-ghidra/nativelib "
    f'-cp "$CP" com.xtv.Unpack > {remote_log} 2>&1 &',
    timeout=10)
print("launched detached (30 min timeout)")

# Poll for completion
for i in range(360):  # up to 30 min
    time.sleep(5)
    _, out, _ = c.exec_command(f"wc -l {remote_log} 2>/dev/null; "
                                f"tail -1 {remote_log} 2>/dev/null", timeout=10)
    check = out.read().decode("utf-8", "replace").strip()
    lines = check.split("\n")
    if lines and "done" in lines[-1].lower():
        print("DONE detected!")
        break
    if lines and "threw" in lines[-1].lower():
        print("N.l threw detected!")
        break
    if lines and "returned" in lines[-1].lower():
        print("N.l returned detected!")
        break
    if i % 12 == 0:  # every 60s
        wc = lines[0] if lines else "?"
        print(f"  [{i*5}s] {wc}")

# Fetch log
log_local = r"C:\Users\Nestor\Workspace\Xuper\XuperPlugin\_scratch\output.log"
sftp = c.open_sftp()
try:
    sftp.get(remote_log, log_local)
    print(f"fetched {remote_log} -> {log_local}")
except Exception as ex:
    print(f"fetch failed: {ex}")
sftp.close()
c.close()

# Quick grep
import re
with open(log_local, "r") as f:
    content = f.read()
for pat in [r"N\.l (returned|threw)", r"b2b returned", r"non-zero page",
            r"FETCH LIMIT", r"Pre-map done", r"first 8", r"SUCCESS", r"dex"]:
    for m in re.finditer(pat, content, re.IGNORECASE):
        ctx = content[max(0,m.start()-20):m.end()+80]
        print(f"  [{pat}] ...{ctx}...")
