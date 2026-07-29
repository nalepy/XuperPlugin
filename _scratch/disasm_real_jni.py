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
# Pull 64 bytes at REAL_JNI from last run via a tiny java... or from hex we have + more via remote mem
# Re-run short dump only
cmd = r'''
export PATH=~/xtv-ghidra/maven/bin:$PATH
cd ~/xtv-ghidra/harness
CP="target/classes:$(cat ~/xtv-ghidra/cp.txt)"
# quick python disasm of known bytes + expand via reading so file after... use log MEM
python3 <<'PY'
from capstone import *
md=Cs(CS_ARCH_ARM, CS_MODE_THUMB)
b=bytes.fromhex('f0b503af2de900070c46054690b10321a5f11500c8f2010151fb00018a1302eb')
print('=== REAL_JNI 0x12043544 ===')
for i in md.disasm(b, 0x12043544):
    print(f'0x{i.address:x}:\t{i.mnemonic}\t{i.op_str}')
b2=bytes.fromhex('f0b503af4df804bd72b10a4b7b441b6811f8014b05781c5d5e5da64206d11db1')
print('=== callee 0x1203f8ec ===')
for i in md.disasm(b2, 0x1203f8ec):
    print(f'0x{i.address:x}:\t{i.mnemonic}\t{i.op_str}')
PY
'''
_, o, e = c.exec_command(cmd, timeout=60)
print(o.read().decode())
c.close()
