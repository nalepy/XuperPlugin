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
JAR=~/.m2/repository/com/github/zhkl0228/unidbg-android/0.9.10-SNAPSHOT/unidbg-android-0.9.10-SNAPSHOT.jar
javap -c -p -classpath "$JAR" com.github.unidbg.linux.android.dvm.DalvikModule > /tmp/dm.javap 2>&1
python3 - <<'PY'
from pathlib import Path
t=Path('/tmp/dm.javap').read_text(errors='replace')
i=t.find('callJNI_OnLoad')
print(t[i:i+2000])
PY
# Svc constants
JAR2=~/.m2/repository/com/github/zhkl0228/unidbg-api/0.9.10-SNAPSHOT/unidbg-api-0.9.10-SNAPSHOT.jar
javap -classpath "$JAR2" -verbose com.github.unidbg.Svc 2>&1 | head -80
"""
_, o, e = c.exec_command(cmd, timeout=60)
print(o.read().decode()[:8000])
c.close()
