#!/usr/bin/env python3
"""Grep the remote log for 3a6a0 hook and key register values."""
import paramiko

c = paramiko.SSHClient()
c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
c.connect("192.168.100.40", username="nestor", password="ian20jesus",
          look_for_keys=False, allow_agent=False, timeout=15)

# Get 3a6a0 hooks
stdin, stdout, stderr = c.exec_command(
    "grep -n '3a6a0\\|FETCH from unmapped' /tmp/unpack_lever.log", timeout=30)
print("=== 3a6a0 hooks & FETCH ===")
lines = stdout.read().decode("utf-8", "replace").splitlines()
for line in lines:
    print(line)

print("\n=== FIRST FETCH ===")
for line in lines:
    if 'FETCH' in line:
        print(line)
        break

print("\n=== FETCH COUNT ===")
fetch_lines = [l for l in lines if 'FETCH' in l]
print(f"Total FETCH lines: {len(fetch_lines)}")

# Also get the full first FETCH region head  
stdin2, stdout2, stderr2 = c.exec_command(
    "grep -n 'FETCH from unmapped' /tmp/unpack_lever.log | head -3", timeout=15)
print("\n=== HEAD FETCH ===")
print(stdout2.read().decode())

# Check if there's output between walk2 end and FETCH start
stdin3, stdout3, stderr3 = c.exec_command(
    "grep -n '3a6a0' /tmp/unpack_lever.log", timeout=15)
print("\n=== 3a6a0 lines ===")
print(stdout3.read().decode()[:2000])

c.close()
