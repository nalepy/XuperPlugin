#!/usr/bin/env python3
"""Dump decrypted code around wchan PC via short unidbg run, or from libexec file."""
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

remote_helper = r"""
#!/usr/bin/env python3
# Install capstone if needed and disasm file bytes (encrypted - may fail)
# Better: use baseline Unpack to dump mem after load hangs
import subprocess, os, sys
print('files', os.listdir('/home/nestor/xtv-ghidra/harness/src/main/java/com/xtv'))
print('logs', subprocess.getoutput('ls -lt /tmp/unpack*.log 2>/dev/null | head -10'))
# search for wchan string in so
so='/tmp/apkx/assets/ijm_lib/armeabi/libexec.so'
data=open(so,'rb').read()
for s in [b'wchan', b'/proc/self', b'/proc/']:
    i=0; n=0
    while n<5:
        j=data.find(s,i)
        if j<0: break
        print(s, hex(j), data[max(0,j-16):j+48])
        i=j+1; n+=1
# readelf segments
print(subprocess.getoutput(f'readelf -l {so} | head -35'))
"""

sftp = c.open_sftp()
with sftp.file("/tmp/inspect_wchan.py", "w") as f:
    f.write(remote_helper)
sftp.close()

_, out, err = c.exec_command("python3 /tmp/inspect_wchan.py", timeout=30)
print(out.read().decode("utf-8", "replace"))
print(err.read().decode("utf-8", "replace")[-1000:])

# Add a tiny DumpWchan Java that loads lib, hooks 0x1202e39d to dump 64 bytes then exit
dump_java = r'''
package com.xtv;
import com.github.unidbg.*;
import com.github.unidbg.arm.backend.*;
import com.github.unidbg.linux.android.*;
import com.github.unidbg.linux.android.dvm.*;
import com.github.unidbg.memory.Memory;
import unicorn.ArmConst;
import java.io.File;

public class DumpWchan extends AbstractJni {
  public static void main(String[] a) throws Exception {
    AndroidEmulator emulator = AndroidEmulatorBuilder.for32Bit()
      .setProcessName("com.android.mgstv")
      .addBackendFactory(new Unicorn2Factory(false))
      .build();
    Memory memory = emulator.getMemory();
    memory.setLibraryResolver(new AndroidResolver(23));
    VM vm = emulator.createDalvikVM(new File("/tmp/apkx"));
    vm.setJni(new DumpWchan());
    vm.setVerbose(false);
    Backend b = emulator.getBackend();
    final int[] dumped = {0};
    b.hook_add_new(new CodeHook() {
      public void hook(Backend be, long address, int size, Object user) {
        if (dumped[0]++ > 0) return;
        System.out.println("HIT wchan PC=0x"+Long.toHexString(address));
        try {
          for (long base : new long[]{0x1202e360L, 0x1202e380L, 0x1202e390L, 0x1202e39dL, 0x1202e3a0L}) {
            byte[] mem = be.mem_read(base, 32);
            System.out.printf("MEM@%x: ", base);
            for (byte x: mem) System.out.printf("%02x", x&0xff);
            System.out.println();
          }
          long lr=be.reg_read(ArmConst.UC_ARM_REG_LR).longValue();
          long r0=be.reg_read(ArmConst.UC_ARM_REG_R0).longValue();
          long sp=be.reg_read(ArmConst.UC_ARM_REG_SP).longValue();
          System.out.printf("LR=%x R0=%x SP=%x%n", lr, r0, sp);
        } catch (Throwable t) { System.out.println("dump fail "+t); }
        // force process end
        System.exit(0);
      }
      public void onAttach(UnHook u) {}
      public void detach() {}
    }, 0x1202e390L, 0x1202e3b0L, null);
    // also bx lr at 0x1203725c so we can get past later if needed
    b.hook_add_new(new CodeHook() {
      public void hook(Backend be, long address, int size, Object user) {
        long lr=be.reg_read(ArmConst.UC_ARM_REG_LR).longValue();
        if (lr==0xffff0000L || lr==0) {
          be.reg_write(ArmConst.UC_ARM_REG_PC, 0x1203732aL);
        } else {
          be.reg_write(ArmConst.UC_ARM_REG_PC, lr);
        }
      }
      public void onAttach(UnHook u) {}
      public void detach() {}
    }, 0x1203725cL, 0x1203725cL, null);
    System.out.println("loading...");
    DalvikModule dm = vm.loadLibrary(new File("/tmp/apkx/assets/ijm_lib/armeabi/libexec.so"), true);
    System.out.println("loaded base="+Long.toHexString(dm.getModule().base));
    System.out.println("never hit wchan range");
  }
}
'''

with open(r"C:\Users\Nestor\Workspace\Xuper\XuperPlugin\_scratch\DumpWchan.java", "w", encoding="utf-8") as f:
    f.write(dump_java)

sftp = c.open_sftp()
sftp.put(r"C:\Users\Nestor\Workspace\Xuper\XuperPlugin\_scratch\DumpWchan.java",
         "/home/nestor/xtv-ghidra/harness/src/main/java/com/xtv/DumpWchan.java")
sftp.close()

cmd = (
    "export PATH=~/xtv-ghidra/maven/bin:$PATH && "
    "cd ~/xtv-ghidra/harness && mvn -q compile 2>&1 | tail -20 && "
    'CP="target/classes:$(cat ~/xtv-ghidra/cp.txt)" && '
    "timeout 90 java -Xmx3g -Djava.library.path=~/xtv-ghidra/nativelib "
    '-cp "$CP" com.xtv.DumpWchan > /tmp/dump_wchan.log 2>&1; '
    "echo EXIT:$?; cat /tmp/dump_wchan.log"
)
_, out, err = c.exec_command(cmd, timeout=150)
print(out.read().decode("utf-8", "replace")[-12000:])
print(err.read().decode("utf-8", "replace")[-2000:])
c.close()
print("done")
