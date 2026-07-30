#!/usr/bin/env python3
"""Disassemble decrypted 0x1203a314 via paramiko + capstone on .40."""
import paramiko
import binascii

# Extended decrypted dump from DECRYPTED@0x1203a2c0 (0x140 bytes)
# Contains 0x1203a314 at offset 0x54
hexstr_a2c0 = (
    "90e805604ff47a70e4f744fb00200921c8f80000204680b44ff0250700df80bc"
    "484503d9444241f07ce80460fede00bf00f0ffff5a810400b886050003487844"
    "0068006802497944c16270473e80040022260500f0b503af4df8048d00292fdd"
    "00238b4241dac45c55786c40c454c418d6781033a57894f804807540a5705579"
    "94f806c085ea08052571d57994f808e085ea0c05a571557a85ea0e052572657a9"
    "67a75406572e57a567b7540e572657bd67b75406573e57b567c7540e573d0e7a"
    "1f11300c11700eb916121f03f01401a411c821c41435143c21c0430514348430a"
    "2140f096ef0029fdd15df8048bf0bdf0b503af2de9c0070446cc487844d0f800"
    "80d8f8000001902046c949c94e79447e44b047c8497944d1f800a0daf8001081"
    "f881002046c5497944b047daf80010b0fa80f0400981f804012046c0497944"
)

code_a2c0 = binascii.unhexlify(hexstr_a2c0)

# 0x1203a314 is at offset 0x54 from 0x1203a2c0
OFFSET_3a314 = 0x54
code_3a314 = code_a2c0[OFFSET_3a314:]

print(f"0x1203a314 bytes ({len(code_3a314)} bytes): {code_3a314.hex()}")

c = paramiko.SSHClient()
c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
c.connect("192.168.100.40", username="nestor", password="ian20jesus",
          look_for_keys=False, allow_agent=False, timeout=15)

# Write a temp python script
script = f"""import binascii, capstone
hexstr = '{code_3a314.hex()}'
code = binascii.unhexlify(hexstr)
md = capstone.Cs(capstone.CS_ARCH_ARM, capstone.CS_MODE_THUMB)
base = 0x1203a314
print("=== 0x1203a314 (Thumb-2) ===")
for i in md.disasm(code, base):
    print(f"0x{{i.address:x}}:  {{i.mnemonic:10s}} {{i.op_str}}")
    if i.address > base + 0x100:
        break
print()
# Also try ARM mode (some of this may be data tables with ARM-style encoding)
# and also disassemble the FULL 0x1203a2c0 range
print("=== FULL 0x1203a2c0 (0x140 bytes, Thumb-2) ===")
full_code = binascii.unhexlify('{hexstr_a2c0}')
md2 = capstone.Cs(capstone.CS_ARCH_ARM, capstone.CS_MODE_THUMB)
for i in md2.disasm(full_code, 0x1203a2c0):
    print(f"0x{{i.address:x}}:  {{i.mnemonic:10s}} {{i.op_str}}")
    if i.address > 0x1203a2c0 + 0x140:
        break
# Also 0x1203f9b0 which is nearby and may be related
print("\\n=== 0x1203f9b0 (Thumb-2) ===")
# DECRYPTED@0x1203f9b0 from the log
hex_3f9b0 = "f0b503af4df804bd0446b8b320463bf0c8ec2318591ea14212d391f90020d1b20d2a03da0939012908d909e04ab2202a02da0d2902d003e0202901d10138e8e70025421c03f801596119994212d86657f0b20d2e03da0938012808d90ae046b2202e02da0d2802d004e0202802d10135013ae9e715b120463bf0daed20465df804bbf0bdb0b502af0c46002521463bf070ec10b101350130f8e72846b0bdf0b503af4df8048d16460c460546fff7eaff3060042101eb80003bf0aaec80460646284621463bf0dced10b101c60025f7e740465df8048bf0bd014a7a44e7f772b9c22e0500"
code_3f9b0 = binascii.unhexlify(hex_3f9b0)
md3 = capstone.Cs(capstone.CS_ARCH_ARM, capstone.CS_MODE_THUMB)
for i in md3.disasm(code_3f9b0, 0x1203f9b0):
    print(f"0x{{i.address:x}}:  {{i.mnemonic:10s}} {{i.op_str}}")
    if i.address > 0x1203f9b0 + 0x140:
        break
# Also 0x12037dc8 - the loop that calls 0x1203a314  
print("\\n=== 0x12037dc8 (Thumb-2) - the loop that calls 0x1203a314 ===")
hex_37dc8 = "4aaa2046294602f0a1fa0020099d07904aa84ff4807143f080eae1480df11c0a78440590df4878448346df4878448046de4878440690de4878440390dd4878440490284659465246eef7b0ff002800f09681044607f0c8fd207800252328f0d02046414643f000ec00250028e9d004460020414643f0f8eb06990646204643f0dcea002868d00499204643f0d6ea00287ad0ca492046794443f0ceea002800f0"
code_37dc8 = binascii.unhexlify(hex_37dc8)
md4 = capstone.Cs(capstone.CS_ARCH_ARM, capstone.CS_MODE_THUMB)
for i in md4.disasm(code_37dc8, 0x12037dc8):
    print(f"0x{{i.address:x}}:  {{i.mnemonic:10s}} {{i.op_str}}")
    if i.address > 0x12037dc8 + 0xc0:
        break
"""

stdin, stdout, stderr = c.exec_command(f"cat > /tmp/disasm_3a314.py << 'PYEOF'\n{script}\nPYEOF\npython3 /tmp/disasm_3a314.py", timeout=60)
o = stdout.read().decode("utf-8", "replace")
e = stderr.read().decode("utf-8", "replace")
if o: print(o[-30000:])
if e: print("STDERR:", e[-5000:])
c.close()
