#!/usr/bin/env python3
import paramiko
import tempfile
import os

chunks = {
    "e2b0": (0x1202e2b0, bytes.fromhex("314e00f08ff958b1002080b44ff0010700df80bcb04203d944424df08ae80460")),
    "e4b0": (0x1202e4b0, bytes.fromhex("02466846214611f019fa01460220002908bf012013e00af11a0040f2ff32c117")),
    "7250": (0x12037250, bytes.fromhex("fff70cff004880bd040001000cf072b9b0b5084d002300227d44914208d0845c")),
}

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
for name, (ea, data) in chunks.items():
    path = f"/tmp/chunk_{name}.bin"
    with sftp.file(path, "wb") as f:
        f.write(data)
    cmd = f"objdump -D -b binary -m arm -M force-thumb --adjust-vma=0x{ea:x} {path}"
    _, o, e = c.exec_command(cmd, timeout=30)
    print(o.read().decode())
sftp.close()
c.close()
