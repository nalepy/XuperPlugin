#!/usr/bin/env python3
"""Read bytes around 0x12038270 from the runtime dump and disassemble."""
import capstone

# Bytes from the PAGE@0x12038000 output — but that only had 64 bytes.
# Read from the full dump if it exists, otherwise extract from the log.
# Actually, let's read from the log which has the full PAGE@ dump.
# The log had 0x2000 bytes starting from 0x12038000, shown as hex in lines.

# Let's read the live memory via the Unpack.java dump.
# But for now, use capstone on some known bytes.
# The walk2 trace showed code executing up to 0x12038270.
# Let's check what bytes are in /proc/... of the java process.

import os
import subprocess

# Try to find the runtime dump files
for f in os.listdir('/tmp/apkx/'):
    if 'dump' in f.lower() or 'runtime' in f.lower() or '38000' in f:
        print(f"Found: {f}")

# Try to read bytes directly - the Unpack.java doesn't write them as files
# Instead, re-run with a small capstone snippet inside the java harness
# For now, manually decode what we know:
# The code at 0x12038270 in the original libexec.so (pre-decryption) is different
# from post-decryption. We need post-decryption bytes.

# From the log: the Unpack.java reads PAGE@0x12038000 with mem_read(0x12038000, 0x2000)
# But only prints first 64 bytes. We need offset 0x270.

print("Need to read memory at 0x12038270 from live process.")
print("Checking if there's an automated dump...")
