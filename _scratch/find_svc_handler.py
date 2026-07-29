#!/usr/bin/env python3
import paramiko
import zipfile
import io

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
cmd = r'''
JAR=$(find ~/.m2/repository/com/github/zhkl0228 -name 'unidbg-android*.jar' | head -1)
echo JAR=$JAR
export PATH=~/xtv-ghidra/jdk21/bin:$PATH
# extract ARM32SyscallHandler around hook
cd /tmp && rm -rf udbg_src && mkdir udbg_src && cd udbg_src
jar xf "$JAR" com/github/unidbg/linux/ARM32SyscallHandler.class 2>/dev/null
javap -c -p com.github.unidbg.linux.ARM32SyscallHandler 2>&1 | head -5
# use strings / python to find svc number logic in all jars
python3 <<'PY'
import zipfile,glob,re
for j in glob.glob('/home/nestor/.m2/repository/com/github/zhkl0228/**/*.jar', recursive=True):
    if 'android' not in j and 'api' not in j and 'linux' not in j: continue
    try:
        z=zipfile.ZipFile(j)
    except Exception:
        continue
    for n in z.namelist():
        if 'SyscallHandler' in n and n.endswith('.class'):
            data=z.read(n)
            if b'svc number' in data:
                print('found', j, n)
                # pull nearby strings
                for m in re.finditer(b'svc number.{0,20}', data):
                    print(' ', m.group())
PY
'''
_, o, e = c.exec_command(cmd, timeout=60)
print(o.read().decode())
print(e.read().decode()[-500:])
c.close()
