#!/usr/bin/env python3
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
cmd = r"""
export PATH=~/xtv-ghidra/jdk21/bin:$PATH
for j in unidbg-api unidbg-android unidbg-dynarmic; do
  JAR=$(ls -1 ~/.m2/repository/com/github/zhkl0228/$j/0.9.10-SNAPSHOT/*.jar 2>/dev/null | grep -v sources | grep -v javadoc | head -1)
  echo "== $j $JAR"
done
JAR=~/.m2/repository/com/github/zhkl0228/unidbg-api/0.9.10-SNAPSHOT/unidbg-api-0.9.10-SNAPSHOT.jar
jar tf "$JAR" | grep -i Factory | head -20
ls -la ~/xtv-ghidra/nativelib/
# try find unicorn1
find ~/.m2/repository/com/github/zhkl0228 -name '*unicorn*' 2>/dev/null | head -20
"""
_, o, e = c.exec_command(cmd, timeout=30)
print(o.read().decode())
print(e.read().decode()[-500:])
c.close()
