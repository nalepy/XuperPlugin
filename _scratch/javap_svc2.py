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
python3 <<'PY'
from pathlib import Path
text=Path('/tmp/arm32svc.javap').read_text(errors='replace')
idx=text.find('hook(com.github.unidbg.arm.backend.Backend')
chunk=text[idx:idx+6000]
print(chunk)
PY
"""
_, o, e = c.exec_command(cmd, timeout=30)
print(o.read().decode()[:10000])
c.close()
