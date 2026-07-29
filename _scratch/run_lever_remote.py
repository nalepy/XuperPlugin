#!/usr/bin/env python3
"""Upload patched Unpack.java (JNI lever fix), compile, run on .40."""
import paramiko

local = r"C:\Users\Nestor\Workspace\Xuper\XuperPlugin\_scratch\Unpack.java"
remote = "/home/nestor/xtv-ghidra/harness/src/main/java/com/xtv/Unpack.java"

c = paramiko.SSHClient()
c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
c.connect(
    "192.168.100.40",
    username="nestor",
    password="ian20jesus",
    look_for_keys=False,
    allow_agent=False,
    timeout=15,
)
sftp = c.open_sftp()
with open(local, "rb") as f:
    sftp.putfo(f, remote)
sftp.close()
print("uploaded", remote)


def run(cmd, timeout=300):
    print("\n$", cmd[:200], "..." if len(cmd) > 200 else "")
    _, out, err = c.exec_command(cmd, timeout=timeout)
    o = out.read().decode("utf-8", "replace")
    e = err.read().decode("utf-8", "replace")
    if o:
        print(o[-20000:])
    if e:
        print("STDERR:", e[-5000:])
    return o, e


MVN = "export PATH=~/xtv-ghidra/maven/bin:$PATH"
run(f"{MVN} && cd ~/xtv-ghidra/harness && mvn -q compile 2>&1 | tail -40")
log = "/tmp/unpack_lever.log"
run(
    f"{MVN} && cd ~/xtv-ghidra/harness && "
    f'CP="target/classes:$(cat ~/xtv-ghidra/cp.txt)" && '
    f"timeout 45 java -Xmx3g -Djava.library.path=~/xtv-ghidra/nativelib "
    f'-cp "$CP" com.xtv.Unpack > {log} 2>&1; '
    f"echo EXIT:$?; wc -l {log}; "
    f"grep -E 'LEVER|INIT anti|JNI |jniPhase|JNI_OnLoad|loadLibrary|MEM@|b2b|SUCCESS|threw|SIGKILL|secondary|anti-tamper|done|symbol' {log} | head -100; "
    f"echo '---TAIL---'; tail -80 {log}"
)
c.close()
