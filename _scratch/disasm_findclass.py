#!/usr/bin/env python3
"""Disassemble the FindClass-site code at 0x120378a0 + the area around 0x120378c2."""
import paramiko, binascii

hex_378a0 = "e6f76afd204600f0b7f920688269bf487844d0f80090d9f80000d0f88c014168204690470546d9f800000069816e20468847002d50d0b648072378441890b548"

script = f"""
import binascii, capstone
code = binascii.unhexlify('{hex_378a0}')
md = capstone.Cs(capstone.CS_ARCH_ARM, capstone.CS_MODE_THUMB)
base = 0x120378a0
print("=== 0x120378a0 (64 bytes) ===")
for i in md.disasm(code, base):
    print(f"0x{{i.address:x}}:  {{i.mnemonic:10s}} {{i.op_str}}")
print()

# Also the 0x12037f20 success handler after dispatch match
hex_37f20 = "04564690a768b191021a40f2ff128d1713eb0c51401a411c41430343000631214643f016eb00287cd00026828962f0840156a89126010f460628c11a91001a40f2ff1200eb90611a40821c62194143052141432846214343f016eb"
code_37f20 = binascii.unhexlify(hex_37f20)
print("=== 0x12037f20 (success handler, ~80 bytes) ===")
for i in md.disasm(code_37f20, 0x12037f20):
    print(f"0x{{i.address:x}}:  {{i.mnemonic:10s}} {{i.op_str}}")
    if i.address > 0x12037f20 + 0x80:
        break
print()

# The area after the 3 dispatch calls - 0x12038146 (walk2 end point)
hex_38146 = "2046c26d90477944784498201a4602f062f900469021a60de60e40f2ff1200eb90611a40821c62194143052141432846214343f016eb002861d000230268020c0268121c9a4232d002e00023a288020c129a4244d1"
code_38146 = binascii.unhexlify(hex_38146)
print("=== 0x12038146 (walk2 end, ~80 bytes) ===")
for i in md.disasm(code_38146, 0x12038146):
    print(f"0x{{i.address:x}}:  {{i.mnemonic:10s}} {{i.op_str}}")
    if i.address > 0x12038146 + 0x80:
        break
"""

c = paramiko.SSHClient()
c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
c.connect("192.168.100.40", username="nestor", password="ian20jesus",
          look_for_keys=False, allow_agent=False, timeout=15)

stdin, stdout, stderr = c.exec_command(
    f"cat > /tmp/disasm_findclass.py << 'PYEOF'\n{script}\nPYEOF\npython3 /tmp/disasm_findclass.py",
    timeout=60)
o = stdout.read().decode("utf-8", "replace")
e = stderr.read().decode("utf-8", "replace")
if o: print(o[-20000:])
if e: print("STDERR:", e[-5000:])
c.close()
