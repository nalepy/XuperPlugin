import paramiko

local = r"C:\Users\Nestor\Workspace\Xuper\XuperPlugin\_scratch\UnpackV50.java"
remote = "/home/nestor/xtv-ghidra/harness/src/main/java/com/xtv/UnpackV50.java"

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
    print("\n$", cmd)
    _, out, err = c.exec_command(cmd, timeout=timeout)
    o = out.read().decode("utf-8", "replace")
    e = err.read().decode("utf-8", "replace")
    if o:
        print(o[-15000:])
    if e:
        print("STDERR:", e[-5000:])
    return o, e


MVN = "export PATH=~/xtv-ghidra/maven/bin:$PATH"
run(f"{MVN} && cd ~/xtv-ghidra/harness && mvn -q compile 2>&1 | tail -30")
for mode in ["multi"]:
    log = f"/tmp/unpack_v51_{mode}.log"
    run(
        f"{MVN} && cd ~/xtv-ghidra/harness && "
        f'CP="target/classes:$(cat ~/xtv-ghidra/cp.txt)" && '
        f"timeout 180 java -Xmx3g -Djava.library.path=~/xtv-ghidra/nativelib "
        f'-cp "$CP" com.xtv.UnpackV50 {mode} > {log} 2>&1; '
        f"echo EXIT:$?; wc -l {log}; tail -200 {log}"
    )
c.close()
