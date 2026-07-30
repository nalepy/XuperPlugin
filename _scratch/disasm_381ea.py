#!/usr/bin/env python3
"""Disassemble PAGE@0x12038000 window[0x180:0x300] to trace the path to the
0x120381c1 EXEC-permission crash (session 23 part 9)."""
import capstone

hex180 = "a34d05005b09feff714d0500510dfeffd74c05008d0bfeffa54c05001c4e0500014e0500b34d0500aa4d0500a54d05000c4c0500d74d0500e24d0500de4d0500f0b503af2de9000fadf5a04d91b08246d6481e4615467844d0f80080d8f8000047f8240c03f09cf988b14ff00009d8f8000057f8241c884201bf48460df5a04d"
hex200 = "11b0bde8000f08bff0bd43f082e8c84851462a467844d0f800b0dbf800000069436ec44878449847dbf80000012180f81111c14a7a441178002900f0d1800b9203f086f9284602f0b3f8dbf8001081f880002846b9497944fff7d0f9dbf80010b0fa80f0400981f8ec00d1f8a400006880475046e6f732fa10b10020cbf800005046294602f06cfa5046294602f0a2fa0028aad00446504602f0d6fa4ff000090028a4d00a902946daf80000426d50469047dbf80010c86008692146026e50469047dbf80010486408693146026e50469047dbf800108864d1f8b8018047fff701f8284602f0e2fa284602f037fcdbf8001081f8030192487844e6f747fa0028"

data = bytes.fromhex(hex180) + bytes.fromhex(hex200)
base = 0x12038180

md = capstone.Cs(capstone.CS_ARCH_ARM, capstone.CS_MODE_THUMB)
md.detail = True

print(f"window base=0x{base:x} len={len(data)}")
print("targets: 0x120381c1 (crash), 0x120381ea (branch target), 0x12038273 (dispatch#3 LR)")
print("-" * 70)
for insn in md.disasm(data, base):
    tags = []
    if insn.address <= 0x120381c1 < insn.address + insn.size:
        tags.append("<== 0x120381c1 CRASH")
    if insn.address == 0x120381ea:
        tags.append("<== 0x120381ea BRANCH TARGET")
    if insn.address == 0x12038273:
        tags.append("<== dispatch#3 LR (return point)")
    marker = "  " + " ".join(tags) if tags else ""
    print(f"0x{insn.address:08x}:\t{insn.bytes.hex():16s}\t{insn.mnemonic}\t{insn.op_str}{marker}")
