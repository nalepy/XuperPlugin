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
# dump runtime bytes around 0x120370c0 via a one-off - use last approach: run small java or
# read from SO file at offset 0x370c0 (decrypted may differ - need emulator)
# Quick: add to harness... for now disasm file bytes and also ask remote to hexdump from a tiny script

cmd = r"""
python3 <<'PY'
from capstone import *
# static file bytes (may be packed)
from pathlib import Path
data=Path('/tmp/apkx/assets/ijm_lib/armeabi/libexec.so').read_bytes()
md=Cs(CS_ARCH_ARM, CS_MODE_THUMB)
off=0x370b0
print('=== file @0x370b0 ===')
for i in md.disasm(data[off:off+64], 0x12000000+off):
    print(f'0x{i.address:x}:\t{i.mnemonic}\t{i.op_str}')
PY
# Also pull runtime from previous approach - run jdb? Use existing Unpack mem dump by patching runner
# Read /tmp if we write a dump file next run
"""
_, o, e = c.exec_command(cmd, timeout=30)
print(o.read().decode())
c.close()
