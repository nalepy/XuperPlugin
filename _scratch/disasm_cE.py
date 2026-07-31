#!/usr/bin/env python3
"""Disassemble cE (0x1203b6f8) from the runtime dump in p22_output.log.
Goal: understand what vtable[0x44]'s callback does — its return value
semantics, what branch follows it, and why N.l returns false with our
VTABLE_STUB (movs r0,#1; bx lr) instead of the real callback."""
import capstone

# Hex from p22_output.log line 721: FN2@0x1203b6f8 window[0:0x300]
hex_str = ("bfb506af0b46104979440c6821680391002102910d4979440968096809694d6c0b49"
           "0c4a79447a44cde9002102a90a4a7a44a84702982168039a914204bf04b0b0bd3ff"
           "0eaed00bf346c04002e6c040020200500ee1f0500c41f0500f0b503af2de9000b0c2"
           "03ff030ee002444600460846045497944416044497944d1f80080d8f80010896b89"
           "68884710b1bde8000bf0bd3f483b4d78444470d8f80000d0f8880100283fd03a213"
           "ff0feee00283cd080b44ff0140700df80bc0446a946a84238d9a4f1560001b2c1f3"
           "4951014401f47c41401a00b2411c414340000130484306213ff07aed0d4666423f"
           "f0feed05b10660066004f10d0001b2c1f38461014421f01f01401a00b2411c821c"
           "41435143c21c5143021d05305143484314213ff05ced29b1fee7a94604e0a946002"
           "401e04ff0ff34d8f8000090f8090188b12046062180b44ff0250700df80bc48454"
           "94603d945423ff0c8ed05604ff47a70e3f77df800200921c8f80000204680b44ff02"
           "50700df80bc484503d944423ff0b4ed0460fede00f0ffff1d010000ca6b0400b271"
           "0500f0b503af2de9000b0d46012101604d487844d0f80080d8f80000d0f88801002"
           "841d03a213ff076ee00283cd080b44ff0140700df80bc044610f5805f34d9a4f156"
           "0001b2c1f34951014401f47c41401a00b2411c414340000130484306213ff0f2ec8"
           "94666423ff076edb9f1000f18bf0660066004f10d0001b2c1f38461014421f01f01"
           "401a00b2411c821c41435143c21c5143021d05305143484314213ff0d2ecb1b1fe"
           "e70024a5f14a00c11700eb916121f03f01401a411c821c41435143c21c043051434"
           "8430a213ff0bcec19b1fee74ff0ff34e7e7d8f80000032d184e08d190f8fe1029b1"
           "0f2040f034e8d8f8000098b190f8090180b12046062180b44ff0250700df80bcb04"
           "203d945423ff022ed05604ff47a70e2f7d6ff00200921c8f80000204680b44ff025"
           "0700df80bcb04203d944423ff00eed0460fede00bf00f0ffffa06a0400f0b503af2d"
           "e9fc0b81462b480b469046784400220668306805902848cde9032278440568")

code = bytes.fromhex(hex_str)

# cE starts at 0x1203b6f8 in Thumb mode (address & 1 == 1 means Thumb)
base = 0x1203b6f8

md = capstone.Cs(capstone.CS_ARCH_ARM, capstone.CS_MODE_THUMB)
md.detail = True

# Disassemble the whole window, flag the critical blx r5 at 0x1203b72a
print("=== cE function @0x1203b6f8 (Thumb), 0x300 bytes ===")
for insn in md.disasm(code, base):
    marker = ""
    if insn.address == 0x1203b72a:
        marker = "  <<< blx r5 (vtable[0x44] callback — the fault point)"
    elif insn.address == 0x1203b72c:
        marker = "  <<< instruction AFTER blx r5 returns"
    # Also flag any blx, cbz, cbnz, bne, beq, b instructions near the callback
    if 0x1203b720 <= insn.address <= 0x1203b740 and insn.mnemonic.startswith(("blx", "cbz", "cbnz", "bne", "beq", "b.", "b ", "bl ")):
        marker += f"  [BRANCH: {insn.mnemonic} {insn.op_str}]"
    print(f"  0x{insn.address:08x}:  {insn.mnemonic:8s} {insn.op_str}{marker}")

# Focus: print instructions 0x1203b710 - 0x1203b740 with raw bytes
print("\n=== FOCUS: 0x1203b710 - 0x1203b740 ===")
for insn in md.disasm(code, base):
    if 0x1203b710 <= insn.address <= 0x1203b740:
        raw = insn.bytes.hex()
        print(f"  0x{insn.address:08x}:  {raw:12s}  {insn.mnemonic:8s} {insn.op_str}")