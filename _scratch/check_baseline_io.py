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
export PATH=~/xtv-ghidra/maven/bin:$PATH
# find ByteArrayFileIO
python3 - <<'PY'
from pathlib import Path
cp=Path('/home/nestor/xtv-ghidra/cp.txt').read_text().strip().split(':')
import subprocess
for j in cp:
  if not j.endswith('.jar'): continue
  out=subprocess.getoutput(f'jar tf "{j}" | grep -i FileIO | head -20')
  if out.strip():
    print('===', j)
    print(out)
PY
# quick baseline 45s
cd ~/xtv-ghidra/harness
CP="target/classes:$(cat ~/xtv-ghidra/cp.txt)"
timeout 45 java -Xmx3g -Djava.library.path=~/xtv-ghidra/nativelib -cp "$CP" com.xtv.Unpack > /tmp/unpack_base45.log 2>&1
echo BASE_EXIT:$?
wc -l /tmp/unpack_base45.log
tail -30 /tmp/unpack_base45.log
"""
_, o, e = c.exec_command(cmd, timeout=120)
print(o.read().decode("utf-8", "replace")[-8000:])
print(e.read().decode("utf-8", "replace")[-1000:])
c.close()
