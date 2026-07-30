#!/usr/bin/env python3
"""Disassemble the live PAGE@0x12038000 window[0x200:0x300] dump around 0x12038273."""
import capstone

hexstr = "11b0bde8000f08bff0bd43f082e8c84851462a467844d0f800b0dbf800000069436ec44878449847dbf80000012180f81111c14a7a441178002900f0d1800b9203f086f9284602f0b3f8dbf8001081f880002846b9497944fff7d0f9dbf80010b0fa80f0400981f8ec00d1f8a400006880475046e6f732fa10b10020cbf800005046294602f06cfa5046294602f0a2fa0028aad00446504602f0d6fa4ff000090028a4d00a902946daf80000426d50469047dbf80010c86008692146026e50469047dbf80010486408693146026e50469047dbf800108864d1f8b8018047fff701f8284602f0e2fa284602f037fcdbf8001081f8030192487844e6f747fa0028"
data = bytes.fromhex(hexstr)
base = 0x12038200

md = capstone.Cs(capstone.CS_ARCH_ARM, capstone.CS_MODE_THUMB)
md.detail = True

print(f"window base=0x{base:x} len={len(data)} target=0x12038273")
print("-" * 70)
for insn in md.disasm(data, base):
    marker = "  <== 0x12038273" if insn.address <= 0x12038273 < insn.address + insn.size else ""
    marker2 = " <== 0x12038270-0x12038280 (crash-adjacent window)" if 0x12038268 <= insn.address <= 0x12038290 else ""
    print(f"0x{insn.address:08x}:\t{insn.bytes.hex():16s}\t{insn.mnemonic}\t{insn.op_str}{marker}{marker2}")
