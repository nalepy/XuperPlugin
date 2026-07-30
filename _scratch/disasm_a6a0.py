#!/usr/bin/env python3
"""Disassemble the 0x1203a6a0 region from the decrypted dump."""
import paramiko

hex_a6a0 = "7944a847daf8000090f8e21111b9806b806b8047274920467944fcf79fff20b9daf80000012180f8e311234920467944"

# Build remote script with proper escaping
remote_script_lines = [
    "import binascii, capstone",
    "code = binascii.unhexlify('" + hex_a6a0 + "')",
    "# Thumb mode",
    "md = capstone.Cs(capstone.CS_ARCH_ARM, capstone.CS_MODE_THUMB)",
    "base = 0x1203a6a0",
    "print('=== 0x1203a6a0 (Thumb-2, %d bytes) ===' % len(code))",
    "for i in md.disasm(code, base):",
    "    print('0x%x:  %-10s %s' % (i.address, i.mnemonic, i.op_str))",
    "",
    "# Also try ARM mode",
    "md2 = capstone.Cs(capstone.CS_ARCH_ARM, capstone.CS_MODE_ARM)",
    "print()",
    "print('=== 0x1203a6a0 (ARM mode) ===')",
    "for i in md2.disasm(code, base):",
    "    print('0x%x:  %-10s %s' % (i.address, i.mnemonic, i.op_str))",
]

remote_script = "\n".join(remote_script_lines)

c = paramiko.SSHClient()
c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
c.connect("192.168.100.40", username="nestor", password="ian20jesus",
          look_for_keys=False, allow_agent=False, timeout=15)

cmd = f"python3 << 'PYEOF'\n{remote_script}\nPYEOF"
stdin, stdout, stderr = c.exec_command(cmd, timeout=60)
o = stdout.read().decode("utf-8", "replace")
e = stderr.read().decode("utf-8", "replace")
if o: print(o[-5000:])
if e: print("STDERR:", e[-2000:])

# Also disassemble the wider 0x12037dc8 region - the function that calls the chain
# leading to 0x1203a6a4 - second part of DECRYPTED@0x12037dc8
hex_37dc8 = "4aaa2046294602f0a1fa0020099d07904aa84ff4807143f080eae1480df11c0a78440590df4878448346df4878448046de4878440690de4878440390dd4878440490284659465246eef7b0ff002800f09681044607f0c8fd207800252328f0d02046414643f000ec00250028e9d004460020414643f0f8eb06990646204643f0dcea002868d00499204643f0d6ea00287ad0ca492046794443f0ceea002800f0"

remote_script2_lines = [
    "import binascii, capstone",
    "code = binascii.unhexlify('" + hex_37dc8 + "')",
    "md = capstone.Cs(capstone.CS_ARCH_ARM, capstone.CS_MODE_THUMB)",
    "base = 0x12037dc8",
    "print('=== 0x12037dc8 (Thumb-2, %d bytes) ===' % len(code))",
    "for i in md.disasm(code, base):",
    "    print('0x%x:  %-10s %s' % (i.address, i.mnemonic, i.op_str))",
    "    if i.address > base + 0x100:",
    "        break",
]

remote_script2 = "\n".join(remote_script2_lines)
cmd2 = f"python3 << 'PYEOF'\n{remote_script2}\nPYEOF"
stdin2, stdout2, stderr2 = c.exec_command(cmd2, timeout=60)
o2 = stdout2.read().decode("utf-8", "replace")
e2 = stderr2.read().decode("utf-8", "replace")
if o2: print("\n" + o2[-5000:])
if e2: print("STDERR2:", e2[-2000:])

c.close()
