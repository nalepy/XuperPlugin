package com.xtv;
import com.github.unidbg.*;
import com.github.unidbg.arm.backend.*;
import com.github.unidbg.linux.android.*;
import com.github.unidbg.linux.android.dvm.*;
import com.github.unidbg.memory.Memory;
import unicorn.ArmConst;
import java.io.File;
import java.io.FileOutputStream;

public class WideScan extends AbstractJni {

    // Minimal JNI mocks — just enough to get JNI_OnLoad through
    @Override
    public DvmObject<?> getStaticObjectField(BaseVM vm, DvmClass c, String sig) {
        if (sig.contains("Build->HARDWARE")) return new StringObject(vm, "qcom");
        if (sig.contains("Build->MODEL")) return new StringObject(vm, "SM-G973F");
        if (sig.contains("Build->MANUFACTURER")) return new StringObject(vm, "samsung");
        if (sig.contains("Build->BRAND")) return new StringObject(vm, "samsung");
        if (sig.contains("Build->DEVICE")) return new StringObject(vm, "beyond1");
        if (sig.contains("Build->FINGERPRINT"))
            return new StringObject(vm, "samsung/beyond1ltexx/beyond1:9/PPR1.180610.011/G973FXXU1ASCA:user/release-keys");
        if (sig.contains("Build->TAGS")) return new StringObject(vm, "release-keys");
        if (sig.contains("Build->TYPE")) return new StringObject(vm, "user");
        if (sig.contains("Build->ID")) return new StringObject(vm, "PPR1.180610.011");
        if (sig.contains("VERSION->RELEASE")) return new StringObject(vm, "6.0.1");
        if (sig.contains("VERSION->CODENAME")) return new StringObject(vm, "REL");
        System.out.println(">>> UNMOCKED gSOF: "+sig);
        return super.getStaticObjectField(vm, c, sig);
    }
    @Override
    public int getStaticIntField(BaseVM vm, DvmClass c, String sig) {
        if (sig.contains("SDK_INT")) return 23;
        return super.getStaticIntField(vm, c, sig);
    }
    @Override
    public DvmObject<?> callStaticObjectMethodV(BaseVM vm, DvmClass c, String sig, VaList vl) {
        if (sig.contains("currentPackageName")) return new StringObject(vm, "com.android.mgstv");
        if (sig.contains("currentActivityThread"))
            return vm.resolveClass("android/app/ActivityThread").newObject(null);
        System.out.println(">>> UNMOCKED cSOMV: "+sig);
        return super.callStaticObjectMethodV(vm, c, sig, vl);
    }
    @Override
    public DvmObject<?> getObjectField(BaseVM vm, DvmObject<?> obj, String sig) {
        if (sig.endsWith("Ljava/lang/String;")) {
            String fn = sig.substring(sig.lastIndexOf("->")+2, sig.lastIndexOf(":"));
            if (fn.equals("processName")) return new StringObject(vm, "com.android.mgstv");
            return new StringObject(vm, "/data/app/com.android.mgstv-1");
        }
        try {
            String cn = sig.substring(sig.lastIndexOf(":L")+2, sig.length()-1).replace('/','.');
            return vm.resolveClass(cn).newObject(null);
        } catch (Throwable t) { return super.getObjectField(vm, obj, sig); }
    }
    @Override
    public int getIntField(BaseVM vm, DvmObject<?> obj, String sig) { return 23; }

    @Override
    public DvmObject<?> callObjectMethod(BaseVM vm, DvmObject<?> obj, String sig, VarArg va) {
        if (sig.contains("getBytes")) {
            StringObject so = (StringObject) obj;
            return new com.github.unidbg.linux.android.dvm.array.ByteArray(vm, so.getValue().getBytes());
        }
        return super.callObjectMethod(vm, obj, sig, va);
    }

    public WideScan() throws Exception {
        AndroidEmulator emu = AndroidEmulatorBuilder.for32Bit()
                .setProcessName("com.android.mgstv")
                .addBackendFactory(new Unicorn2Factory(false))
                .build();
        Memory memory = emu.getMemory();
        memory.setLibraryResolver(new AndroidResolver(23));
        VM vm = emu.createDalvikVM();
        vm.setJni(this);
        vm.setVerbose(true);  // trace JNI calls in N.l
        final Backend be = emu.getBackend();

        // Single hook: short-circuit sanity function like run21
        be.hook_add_new(new CodeHook() {
            public void hook(Backend b, long addr, int sz, Object u) {
                long lr = b.reg_read(ArmConst.UC_ARM_REG_LR).longValue();
                b.reg_write(ArmConst.UC_ARM_REG_R0, 0L);
                b.reg_write(ArmConst.UC_ARM_REG_PC, lr);
            }
            public void onAttach(UnHook h) {} public void detach() {}
        }, 0x1202e5d4L, 0x1202e5d6L, null);

        System.out.println(">>> loading libexec.so ...");
        File so = new File("/tmp/apkx/assets/ijm_lib/armeabi/libexec.so");
        DalvikModule dm = null;
        try {
            dm = vm.loadLibrary(so, true);
            System.out.println(">>> loadLibrary OK, base=0x"+Long.toHexString(dm.getModule().base));
            dm.callJNI_OnLoad(emu);
            System.out.println(">>> JNI_OnLoad SUCCESS!");
        } catch (Throwable t) {
            System.out.println(">>> FAILED: "); t.printStackTrace(System.out);
        }

        // After JNI_OnLoad: pre-populate the GOT chain that N.l/N.r/N.ra use.
        // The crash in N.l does: GOT[0x1203b548] -> struct_a[0] -> struct_b[0x188].
        // Create fake structures in scratch memory.
        final long SCRATCH_A = 0x7f003000L;  // struct_a: just a pointer
        final long SCRATCH_B = 0x7f004000L;  // struct_b: fields at +0x109, +0x188
        be.mem_map(SCRATCH_A, 0x1000, unicorn.UnicornConst.UC_PROT_READ | unicorn.UnicornConst.UC_PROT_WRITE);
        be.mem_map(SCRATCH_B, 0x1000, unicorn.UnicornConst.UC_PROT_READ | unicorn.UnicornConst.UC_PROT_WRITE);

        // struct_a[0] = pointer to a valid structure (just point back to itself for safety)
        // The native code does GOT -> *GOT -> **GOT -> [result+0x188]
        // GOT = &struct_a, struct_a[0] = &struct_inner, struct_inner[0] = &anything_valid (vtable-like),
        // anything_valid[0x188] = string pointer
        final long STRUCT_INNER = 0x7f005000L;  // the middle structure
        final long STRUCT_VTBL = 0x7f006000L;   // the innermost "vtable" object
        be.mem_map(STRUCT_INNER, 0x1000, unicorn.UnicornConst.UC_PROT_READ | unicorn.UnicornConst.UC_PROT_WRITE);
        be.mem_map(STRUCT_VTBL, 0x1000, unicorn.UnicornConst.UC_PROT_READ | unicorn.UnicornConst.UC_PROT_WRITE);

        // struct_a[0] = pointer to struct_inner
        byte[] ptrInner = new byte[]{(byte)(STRUCT_INNER&0xff),(byte)((STRUCT_INNER>>8)&0xff),(byte)((STRUCT_INNER>>16)&0xff),(byte)((STRUCT_INNER>>24)&0xff)};
        be.mem_write(SCRATCH_A, ptrInner);

        // struct_inner[0] = pointer to struct_vtbl (the "vtable" the native code reads)
        byte[] ptrVtbl = new byte[]{(byte)(STRUCT_VTBL&0xff),(byte)((STRUCT_VTBL>>8)&0xff),(byte)((STRUCT_VTBL>>16)&0xff),(byte)((STRUCT_VTBL>>24)&0xff)};
        be.mem_write(STRUCT_INNER, ptrVtbl);

        // struct_vtbl[0x109] = 0 (skip kill trap guard)
        be.mem_write(STRUCT_VTBL + 0x109, new byte[]{0});
        // struct_vtbl[0x188] = pointer to scratch string (processName)
        byte[] scratchName = "com.android.mgstv\0".getBytes();
        final long NAME_ADDR = 0x7f007000L;
        be.mem_map(NAME_ADDR, 0x1000, unicorn.UnicornConst.UC_PROT_READ | unicorn.UnicornConst.UC_PROT_WRITE);
        be.mem_write(NAME_ADDR, scratchName);
        byte[] ptrName = new byte[]{(byte)(NAME_ADDR&0xff),(byte)((NAME_ADDR>>8)&0xff),(byte)((NAME_ADDR>>16)&0xff),(byte)((NAME_ADDR>>24)&0xff)};
        be.mem_write(STRUCT_VTBL + 0x188, ptrName);

        // GOT entries use PC-relative OFFSETS (ldr r0,[pc,#N]; add r0,pc pattern)
        // offset = target - (instruction_addr + 4)
        // GOT[0x1203b548] loaded by ldr at 0x1203b524 → offset = target - 0x1203b528
        long gotOffset0 = SCRATCH_A - 0x1203b528L;
        be.mem_write(0x1203b548L, new byte[]{
            (byte)(gotOffset0&0xff),(byte)((gotOffset0>>8)&0xff),
            (byte)((gotOffset0>>16)&0xff),(byte)((gotOffset0>>24)&0xff)});
        System.out.println(">>> GOT[0x1203b548] = offset 0x"+Long.toHexString(gotOffset0)+" -> 0x"+Long.toHexString(SCRATCH_A));
        // Debug: verify the GOT chain reads correctly
        try {
            int v1 = be.mem_read(0x1203b548L, 4)[0] & 0xff | ((be.mem_read(0x1203b548L,4)[1]&0xff)<<8) | ((be.mem_read(0x1203b548L,4)[2]&0xff)<<16) | ((be.mem_read(0x1203b548L,4)[3]&0xff)<<24);
            long addr1 = v1 + 0x1203b528L;
            System.out.println(">>> verify: GOT[0x1203b548]="+v1+" + PC = 0x"+Long.toHexString(addr1));
            int v2 = be.mem_read(addr1, 4)[0]&0xff | ((be.mem_read(addr1,4)[1]&0xff)<<8) | ((be.mem_read(addr1,4)[2]&0xff)<<16) | ((be.mem_read(addr1,4)[3]&0xff)<<24);
            System.out.println(">>> verify: *GOT = 0x"+Integer.toHexString(v2));
            int v3 = be.mem_read(v2, 4)[0]&0xff | ((be.mem_read(v2,4)[1]&0xff)<<8) | ((be.mem_read(v2,4)[2]&0xff)<<16) | ((be.mem_read(v2,4)[3]&0xff)<<24);
            System.out.println(">>> verify: **GOT = 0x"+Integer.toHexString(v3));
            int v4 = be.mem_read(v3 + 0x188, 4)[0]&0xff | ((be.mem_read(v3+0x188,4)[1]&0xff)<<8) | ((be.mem_read(v3+0x188,4)[2]&0xff)<<16) | ((be.mem_read(v3+0x188,4)[3]&0xff)<<24);
            System.out.println(">>> verify: [**GOT+0x188] = 0x"+Integer.toHexString(v4));
        } catch (Throwable t) { System.out.println(">>> verify failed: "+t.getMessage()); }

        // GOT[0x12082340] = same scratch (for other code paths)
        be.mem_write(0x12082340L, new byte[]{(byte)(SCRATCH_A&0xff),(byte)((SCRATCH_A>>8)&0xff),(byte)((SCRATCH_A>>16)&0xff),(byte)((SCRATCH_A>>24)&0xff)});

        // Direct singleton at 0x120868e0 = pointer to struct_a (vtable-like)
        be.mem_write(0x120868e0L, new byte[]{(byte)(SCRATCH_A&0xff),(byte)((SCRATCH_A>>8)&0xff),(byte)((SCRATCH_A>>16)&0xff),(byte)((SCRATCH_A>>24)&0xff)});

        System.out.println(">>> GOT chain pre-populated: GOT->0x7f003000->0x7f004000");


        // Crash-site debug hook: log registers at 0x1203b520 before N.l
        be.hook_add_new(new CodeHook() {
            public void hook(Backend b, long addr, int sz, Object u) {
                long r0 = b.reg_read(ArmConst.UC_ARM_REG_R0).longValue();
                long r1 = b.reg_read(ArmConst.UC_ARM_REG_R1).longValue();
                long r4 = b.reg_read(ArmConst.UC_ARM_REG_R4).longValue();
                System.out.println(">>> CRASH-SITE r0=0x"+Long.toHexString(r0)+" r1=0x"+Long.toHexString(r1)+" r4=0x"+Long.toHexString(r4));
            }
            public void onAttach(UnHook h) {} public void detach() {}
        }, 0x1203b520L, 0x1203b522L, null);

        // Now try calling N.l directly to trigger decryption
        System.out.println(">>> trying N.l to trigger decrypt...");
        DvmClass N = vm.resolveClass("s/h/e/l/l/N");
        DvmClass AppC = vm.resolveClass("android/app/Application");
        DvmObject<?> app = AppC.newObject(null);
        try {
            boolean lr = N.callStaticJniMethodBoolean(emu,
                "l(Landroid/app/Application;Ljava/lang/String;)Z",
                app, "/data/app/com.android.mgstv-1/base.apk");
            System.out.println(">>> N.l returned: "+lr);
        } catch (Throwable t) {
            System.out.println(">>> N.l threw: "+t.getMessage());
        }

        // Wide scan: 0x12000000-0x13000000 (16MB), search + dump DEX headers
        System.out.println(">>> scanning 0x40000000-0x50000000 for DEX magic (heap region)...");
        int PG=0x10000, found=0;  // 64KB pages for speed
        for (long a=0x40000000L; a<0x50000000L; a+=PG) {
            byte[] b;
            try { b = be.mem_read(a, PG); } catch (Throwable t) { continue; }
            for (int i=0; i<=b.length-8; i++) {
                if (b[i]=='d' && b[i+1]=='e' && b[i+2]=='x' && b[i+3]=='\n'
                        && b[i+4]=='0' && b[i+5]=='3' && b[i+6]=='5') {
                    long dexAddr = a+i;
                    int fsize = ((b[i+0x20]&0xff)|((b[i+0x21]&0xff)<<8)
                            |((b[i+0x22]&0xff)<<16)|((b[i+0x23]&0xff)<<24));
                    System.out.println(">>> DEX@0x"+Long.toHexString(dexAddr)+" file_size="+fsize);
                    found++;
                    if (fsize > 100000 && fsize < 50_000_000) {
                        try {
                            byte[] dex = be.mem_read(dexAddr, fsize);
                            String fn = "/tmp/apkx/dex_"+Long.toHexString(dexAddr)+"_.dex";
                            java.nio.file.Files.write(new File(fn).toPath(), dex);
                            System.out.println(">>> SAVED "+fn+" ("+fsize+" bytes)");
                        } catch (Throwable t) {
                            System.out.println(">>> dump failed: "+t.getMessage());
                        }
                    }
                }
            }
        }
        System.out.println(">>> found "+found+" DEX headers total");
    }

    public static void main(String[] args) throws Exception {
        new WideScan();
        System.out.println(">>> done");
    }
}
