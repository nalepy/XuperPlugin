#!/usr/bin/env python3
"""Disassemble libexec.so around JNI body / secondary trap."""
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
# file base in emu is 0x12000000; file offset = addr - 0x12000000 for ET_DYN loaded at that base
# libexec may have different ELF vaddr — check
cmd = r"""
SO=/tmp/apkx/assets/ijm_lib/armeabi/libexec.so
ls -la $SO
readelf -l $SO 2>/dev/null | head -20
# try llvm-objdump or objdump thumb
for t in llvm-objdump objdump; do which $t; done
# dump bytes around file offsets 0x2e2b0 and 0x2e4b0 and 0x3725c
python3 - <<'PY'
from pathlib import Path
p=Path('/tmp/apkx/assets/ijm_lib/armeabi/libexec.so')
data=p.read_bytes()
# Heuristic: search for ELF load vaddr of first PT_LOAD
import struct
assert data[:4]==b'\x7fELF'
e_phoff=struct.unpack_from('<I', data, 28)[0]
e_phentsize=struct.unpack_from('<H', data, 42)[0]
e_phnum=struct.unpack_from('<H', data, 44)[0]
loads=[]
for i in range(e_phnum):
    off=e_phoff+i*e_phentsize
    p_type,p_offset,p_vaddr,p_paddr,p_filesz,p_memsz,p_flags,p_align=struct.unpack_from('<IIIIIIII', data, off)
    if p_type==1:
        loads.append((p_offset,p_vaddr,p_filesz))
        print(f'PT_LOAD file=0x{p_offset:x} vaddr=0x{p_vaddr:x} filesz=0x{p_filesz:x}')
base=0x12000000
# unidbg typically maps so that module.base = load bias; symbol 0x1203725d => file offset via bias
# bias = base - first_load_vaddr (often 0)
bias=base - loads[0][1]
print('bias', hex(bias))
def fo(ea):
    return ea - bias - loads[0][1] + loads[0][0] if False else ea - base + loads[0][0] - loads[0][1] + loads[0][0]*0
# simpler: file_off = ea - base + (p_offset - p_vaddr) for containing segment
def file_off(ea):
    va=ea-base+loads[0][1]  # if base is bias+vaddr0... actually module.base is load address of ELF
    # In unidbg, Module.base is typically the address where ELF is loaded (vaddr 0 maps to base)
    off=ea-base
    print(f'ea=0x{ea:x} -> raw off 0x{off:x}')
    return off
for ea in [0x1202e2b0, 0x1202e4b0, 0x12037250]:
    off=file_off(ea)
    chunk=data[off:off+64]
    print(ea.to_bytes(4,'little').hex(), 'bytes:', chunk.hex())
PY
# Capstone disasm if present
python3 - <<'PY'
try:
    from capstone import *
except Exception as e:
    print('no capstone', e)
    raise SystemExit
from pathlib import Path
data=Path('/tmp/apkx/assets/ijm_lib/armeabi/libexec.so').read_bytes()
base=0x12000000
md=Cs(CS_ARCH_ARM, CS_MODE_THUMB)
for start in [0x2e2b0, 0x2e4a0, 0x37250]:
    print(f'\\n=== thumb @ file+0x{start:x} (ea 0x{base+start:x}) ===')
    for insn in md.disasm(data[start:start+80], base+start):
        print(f'0x{insn.address:x}:\\t{insn.mnemonic}\\t{insn.op_str}')
PY
"""
_, o, e = c.exec_command(cmd, timeout=60)
print(o.read().decode("utf-8", "replace"))
err = e.read().decode("utf-8", "replace")
if err:
    print("STDERR", err[-2000:])
c.close()
