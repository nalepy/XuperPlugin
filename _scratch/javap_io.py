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
ls ~/xtv-ghidra/jdk21/bin/javap ~/xtv-ghidra/jdk8u492-b09/bin/javap 2>&1
JAVA=~/xtv-ghidra/jdk21/bin
[ -x $JAVA/javap ] || JAVA=~/xtv-ghidra/jdk8u492-b09/bin
JAR=~/.m2/repository/com/github/zhkl0228/unidbg-api/0.9.10-SNAPSHOT/unidbg-api-0.9.10-SNAPSHOT.jar
JAR2=~/.m2/repository/com/github/zhkl0228/unidbg-android/0.9.10-SNAPSHOT/unidbg-android-0.9.10-SNAPSHOT.jar
$JAVA/javap -classpath "$JAR" com.github.unidbg.file.IOResolver
echo ---
$JAVA/javap -classpath "$JAR" com.github.unidbg.file.FileResult
echo ---
$JAVA/javap -classpath "$JAR2" com.github.unidbg.linux.file.ByteArrayFileIO
"""
_, o, e = c.exec_command(cmd, timeout=30)
print(o.read().decode("utf-8", "replace"))
print(e.read().decode("utf-8", "replace")[-2000:])
c.close()
