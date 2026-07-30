#!/usr/bin/env python3
"""Grep the remote log for specific patterns."""
import paramiko

c = paramiko.SSHClient()
c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
c.connect("192.168.100.40", username="nestor", password="ian20jesus",
          look_for_keys=False, allow_agent=False, timeout=15)

stdin, stdout, stderr = c.exec_command(
    "grep -E '3a6a4|DECRYPTED@0x1203a6a|LastResort|SINGLETON|BLX_R12|FETCH|REGDUMP' /tmp/unpack_lever.log | tail -60",
    timeout=30)
o = stdout.read().decode("utf-8", "replace")
e = stderr.read().decode("utf-8", "replace")
print("=== GREP RESULTS ===")
print(o)
if e: print("STDERR:", e[-3000:])

# Also get last 200 lines of the log
stdin2, stdout2, stderr2 = c.exec_command("tail -200 /tmp/unpack_lever.log", timeout=30)
o2 = stdout2.read().decode("utf-8", "replace")
print("\n=== LAST 200 LINES ===")
print(o2[-20000:])

c.close()
