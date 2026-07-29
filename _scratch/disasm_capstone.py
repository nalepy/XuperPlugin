#!/usr/bin/env python3
import paramiko

script = r'''
python3 -m pip install --user capstone 2>&1 | tail -5
python3 <<'PY'
from capstone import *
md = Cs(CS_ARCH_ARM, CS_MODE_THUMB)
regions = {
0x1202e2b0: bytes.fromhex('314e00f08ff958b1002080b44ff0010700df80bcb04203d944424df08ae80460'),
0x1202e4b0: bytes.fromhex('02466846214611f019fa01460220002908bf012013e00af11a0040f2ff32c117'),
0x12037250: bytes.fromhex('fff70cff004880bd040001000cf072b9b0b5084d002300227d44914208d0845c'),
}
for ea, b in regions.items():
    print('=== %x ===' % ea)
    for i in md.disasm(b, ea):
        print('0x%x:\t%s\t%s' % (i.address, i.mnemonic, i.op_str))
print('--- BL at 2e4ba ---')
for i in md.disasm(bytes.fromhex('11f019fa'), 0x1202e4ba):
    print(i.mnemonic, i.op_str)
PY
'''

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
_, o, e = c.exec_command(script, timeout=180)
print(o.read().decode("utf-8", "replace"))
err = e.read().decode("utf-8", "replace")
if err:
    print("ERR", err[-1500:])
c.close()
