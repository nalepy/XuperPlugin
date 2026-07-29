import paramiko

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
cmd = (
    "export PATH=~/xtv-ghidra/maven/bin:$PATH && cd ~/xtv-ghidra/harness && "
    'CP="target/classes:$(cat ~/xtv-ghidra/cp.txt)" && '
    "timeout 180 java -Xmx3g -Djava.library.path=~/xtv-ghidra/nativelib "
    '-cp "$CP" com.xtv.Unpack > /tmp/unpack_baseline.log 2>&1; '
    "echo EXIT:$?; wc -l /tmp/unpack_baseline.log; tail -80 /tmp/unpack_baseline.log"
)
_, o, e = c.exec_command(cmd, timeout=300)
print(o.read().decode(errors="replace"))
c.close()
