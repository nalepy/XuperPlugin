
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
