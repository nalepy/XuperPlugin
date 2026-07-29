#!/usr/bin/env python3
import paramiko

c = paramiko.SSHClient()
c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
c.connect(
    "192.168.100.40",
    username="nestor",
    password="ian20jesus",
    look_for_keys=False,
    allow_agent=False,
    timeout=15,
)
cmd = r"""
export PATH=~/xtv-ghidra/jdk21/bin:$PATH
JAR=~/.m2/repository/com/github/zhkl0228/unidbg-android/0.9.10-SNAPSHOT/unidbg-android-0.9.10-SNAPSHOT.jar
javap -c -p -classpath "$JAR" com.github.unidbg.linux.ARM32SyscallHandler > /tmp/arm32svc.javap 2>&1
wc -l /tmp/arm32svc.javap
grep -n 'svc number\|handleInterrupt\|intno\|swi\|SvcMemory\|NR' /tmp/arm32svc.javap | head -40
# show lines around first 'svc number'
grep -n 'svc number' /tmp/arm32svc.javap
# pull github raw if needed
python3 - <<'PY'
import pathlib
text=pathlib.Path('/tmp/arm32svc.javap').read_text(errors='replace')
# find method hook
idx=text.find('hook(com.github.unidbg.arm.backend.Backend')
print('idx', idx)
print(text[idx:idx+2500])
PY
"""
_, o, e = c.exec_command(cmd, timeout=60)
print(o.read().decode()[:12000])
c.close()
