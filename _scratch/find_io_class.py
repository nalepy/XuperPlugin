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
cat ~/xtv-ghidra/cp.txt | tr ':' '\n' | while read j; do
  [ -f "$j" ] || continue
  jar tf "$j" 2>/dev/null | grep -i 'ByteArrayFileIO\|IOResolver\|DirectoryFileIO' | head -5 && echo "IN $j"
done | head -40
"""
_, o, e = c.exec_command(cmd, timeout=60)
print(o.read().decode("utf-8", "replace"))
print(e.read().decode("utf-8", "replace")[-500:])
c.close()
