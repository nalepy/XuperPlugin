#!/usr/bin/env python3
"""Read key sections of the remote log."""
import paramiko

c = paramiko.SSHClient()
c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
c.connect("192.168.100.40", username="nestor", password="ian20jesus",
          look_for_keys=False, allow_agent=False, timeout=15)

# Get DECRYPTED@ lines
stdin, stdout, stderr = c.exec_command(
    "grep 'DECRYPTED@' /tmp/unpack_lever.log", timeout=30)
print("=== DECRYPTED LINES ===")
for line in stdout.read().decode("utf-8", "replace").splitlines():
    print(line)
    if "1203a6a" in line or "12037dc8" in line or "1203a2c0" in line or "1203a314" in line:
        print("    ^^^ KEY ^^^")

# Get walk2 tail — last 50 addresses
stdin2, stdout2, stderr2 = c.exec_command(
    "grep 'walk2]' /tmp/unpack_lever.log | tail -60", timeout=30)
print("\n=== LAST 60 WALK2 ===")
for line in stdout2.read().decode("utf-8", "replace").splitlines():
    print(line)

# Get trace/blx lines  
stdin3, stdout3, stderr3 = c.exec_command(
    "grep -E 'trace@|before bl|after bl' /tmp/unpack_lever.log", timeout=30)
print("\n=== TRACE/BL LINES ===")
for line in stdout3.read().decode("utf-8", "replace").splitlines():
    print(line)

c.close()
