#!/usr/bin/env python3
"""Upload patched Unpack.java (JNI lever fix), compile, run on .40, fetch logs."""
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


def run(cmd, timeout=660):
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
    f"timeout 3600 java -Xmx3g -Djava.library.path=~/xtv-ghidra/nativelib "
    f'-cp "$CP" com.xtv.Unpack > {log} 2>&1; '
    f"echo EXIT:$?; wc -l {log}"
)

# Now fetch logs back for inspection
import glob as glob2
log_local = r"C:\Users\Nestor\Workspace\Xuper\XuperPlugin\_scratch\output.log"
hs_err_local = r"C:\Users\Nestor\Workspace\Xuper\XuperPlugin\_scratch\hs_err.log"
sftp = c.open_sftp()
try:
    sftp.get(log, log_local)
    print(f"\n--- fetched {log} -> {log_local}")
except Exception as ex:
    print(f"\n--- could not fetch {log}: {ex}")
# find hs_err log
try:
    stdin, stdout, stderr = c.exec_command("ls /home/nestor/xtv-ghidra/harness/hs_err_pid*.log 2>/dev/null", timeout=10)
    hs_path = stdout.read().decode().strip().split("\n")[0]
    if hs_path:
        sftp.get(hs_path, hs_err_local)
        print(f"--- fetched {hs_path} -> {hs_err_local}")
    else:
        print("--- no hs_err log found")
except Exception as ex:
    print(f"--- could not fetch hs_err: {ex}")
sftp.close()
c.close()
