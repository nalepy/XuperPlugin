#!/usr/bin/env python3
"""Fetch the full unpack_lever.log from .40 through paramiko."""
import paramiko

c = paramiko.SSHClient()
c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
c.connect("192.168.100.40", username="nestor", password="ian20jesus",
          look_for_keys=False, allow_agent=False, timeout=15)

sftp = c.open_sftp()
with sftp.open("/tmp/unpack_lever.log", "r") as f:
    data = f.read().decode("utf-8", "replace")
sftp.close()
c.close()

# Print lines containing key patterns
import re
for line in data.split("\n"):
    if any(p in line for p in ["DISPATCH", ">>> [17]", ">>> [18]", ">>> [walk2]",
                                 ">>> [FindClass", "after 1st blx", "before 1st blx",
                                 "REG-DUMP", ">>> [tr", "decrypt call", "find method",
                                 ">>> N.l", ">>> b2b", "post-vtable", "LEVER", "REAL_JNI",
                                 "SUCCESS", "FORCE-WROTE", "table ptr", "method-table"]):
        print(line[:300])
