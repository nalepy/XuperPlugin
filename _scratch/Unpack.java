package com.xtv;
import com.github.unidbg.*;
import com.github.unidbg.arm.backend.Backend;
import com.github.unidbg.arm.backend.Unicorn2Factory;
import com.github.unidbg.arm.backend.CodeHook;
import com.github.unidbg.arm.backend.WriteHook;
import com.github.unidbg.file.FileResult;
import com.github.unidbg.file.IOResolver;
import com.github.unidbg.linux.android.*;
import com.github.unidbg.linux.android.dvm.*;
import com.github.unidbg.linux.android.dvm.array.ByteArray;
import com.github.unidbg.linux.file.ByteArrayFileIO;
import com.github.unidbg.memory.Memory;
import unicorn.ArmConst;
import unicorn.UnicornConst;
import java.io.File;
import java.io.FileOutputStream;

public class Unpack extends AbstractJni {

    private DvmObject<?> mMockActivityThread;
    private DvmObject<?> mMockAppBindData;
    private DvmObject<?> mMockApplicationInfo;

    private DvmObject<?> getMockActivityThread(BaseVM vm) {
        if (mMockActivityThread != null) return mMockActivityThread;
        mMockActivityThread = vm.resolveClass("android/app/ActivityThread").newObject(null);
        return mMockActivityThread;
    }
    private DvmObject<?> getMockAppBindData(BaseVM vm) {
        if (mMockAppBindData != null) return mMockAppBindData;
        mMockAppBindData = vm.resolveClass("android/app/ActivityThread$AppBindData").newObject(null);
        return mMockAppBindData;
    }
    private DvmObject<?> getMockApplicationInfo(BaseVM vm) {
        if (mMockApplicationInfo != null) return mMockApplicationInfo;
        mMockApplicationInfo = vm.resolveClass("android/content/pm/ApplicationInfo").newObject(null);
        return mMockApplicationInfo;
    }

    @Override
    public DvmObject<?> getStaticObjectField(BaseVM vm, DvmClass dvmClass, String signature) {
        switch (signature) {
            case "android/os/Build->HARDWARE:Ljava/lang/String;": return new StringObject(vm, "qcom");
            case "android/os/Build->MODEL:Ljava/lang/String;": return new StringObject(vm, "SM-G973F");
            case "android/os/Build->MANUFACTURER:Ljava/lang/String;": return new StringObject(vm, "samsung");
            case "android/os/Build->BRAND:Ljava/lang/String;": return new StringObject(vm, "samsung");
            case "android/os/Build->DEVICE:Ljava/lang/String;": return new StringObject(vm, "beyond1");
            case "android/os/Build->PRODUCT:Ljava/lang/String;": return new StringObject(vm, "beyond1lte");
            case "android/os/Build->BOARD:Ljava/lang/String;": return new StringObject(vm, "exynos9820");
            case "android/os/Build->FINGERPRINT:Ljava/lang/String;":
                return new StringObject(vm, "samsung/beyond1ltexx/beyond1:9/PPR1.180610.011/G973FXXU1ASCA:user/release-keys");
            case "android/os/Build->TAGS:Ljava/lang/String;": return new StringObject(vm, "release-keys");
            case "android/os/Build->TYPE:Ljava/lang/String;": return new StringObject(vm, "user");
            case "android/os/Build->ID:Ljava/lang/String;": return new StringObject(vm, "PPR1.180610.011");
            case "android/os/Build->HOST:Ljava/lang/String;": return new StringObject(vm, "SWDD5722");
            case "android/os/Build->USER:Ljava/lang/String;": return new StringObject(vm, "dpi");
            case "android/os/Build->DISPLAY:Ljava/lang/String;": return new StringObject(vm, "PPR1.180610.011.G973FXXU1ASCA");
            case "android/os/Build->BOOTLOADER:Ljava/lang/String;": return new StringObject(vm, "G973FXXU1ASCA");
            case "android/os/Build->SERIAL:Ljava/lang/String;": return new StringObject(vm, "unknown");
            case "android/os/Build->RADIO:Ljava/lang/String;": return new StringObject(vm, "unknown");
            case "android/os/Build->ODM_SKU:Ljava/lang/String;": return new StringObject(vm, "");
            case "android/os/Build->SKU:Ljava/lang/String;": return new StringObject(vm, "");
            case "android/os/Build$VERSION->RELEASE:Ljava/lang/String;": return new StringObject(vm, "6.0.1");
            case "android/os/Build$VERSION->INCREMENTAL:Ljava/lang/String;": return new StringObject(vm, "G973FXXU1ASCA");
            case "android/os/Build$VERSION->CODENAME:Ljava/lang/String;": return new StringObject(vm, "REL");
            default:
                System.out.println(">>> UNMOCKED getStaticObjectField signature=" + signature);
                return super.getStaticObjectField(vm, dvmClass, signature);
        }
    }

    @Override
    public int getStaticIntField(BaseVM vm, DvmClass dvmClass, String signature) {
        switch (signature) {
            case "android/os/Build$VERSION->SDK_INT:I": return 23;
            default:
                System.out.println(">>> UNMOCKED getStaticIntField signature=" + signature);
                return super.getStaticIntField(vm, dvmClass, signature);
        }
    }

    @Override
    public DvmObject<?> callStaticObjectMethodV(BaseVM vm, DvmClass dvmClass, String signature, VaList vaList) {
        switch (signature) {
            case "android/app/ActivityThread->currentPackageName()Ljava/lang/String;":
                return new StringObject(vm, "com.android.mgstv");
            case "android/app/ActivityThread->currentActivityThread()Landroid/app/ActivityThread;":
                System.out.println(">>> MOCKED currentActivityThread() -> proxy");
                return getMockActivityThread(vm);
            default:
                System.out.println(">>> UNMOCKED callStaticObjectMethodV signature=" + signature);
                return super.callStaticObjectMethodV(vm, dvmClass, signature, vaList);
        }
    }

    @Override
    public DvmObject<?> callObjectMethod(BaseVM vm, DvmObject<?> dvmObject, String signature, VarArg varArg) {
        switch (signature) {
            case "java/lang/String->getBytes()[B": {
                StringObject string = (StringObject) dvmObject;
                return new ByteArray(vm, string.getValue().getBytes());
            }
            default:
                System.out.println(">>> UNMOCKED callObjectMethod signature=" + signature);
                return super.callObjectMethod(vm, dvmObject, signature, varArg);
        }
    }

    @Override
    public DvmObject<?> getObjectField(BaseVM vm, DvmObject<?> dvmObject, String signature) {
        switch (signature) {
            case "android/app/ActivityThread->mBoundApplication:Landroid/app/ActivityThread$AppBindData;":
                System.out.println(">>> MOCKED ActivityThread.mBoundApplication");
                return getMockAppBindData(vm);
            case "android/app/ActivityThread$AppBindData->appInfo:Landroid/content/pm/ApplicationInfo;":
                System.out.println(">>> MOCKED AppBindData.appInfo");
                return getMockApplicationInfo(vm);
            case "android/app/ActivityThread$AppBindData->info:Landroid/app/LoadedApk;":
                System.out.println(">>> MOCKED AppBindData.info -> LoadedApk proxy");
                return vm.resolveClass("android/app/LoadedApk").newObject(null);
            case "android/app/ActivityThread$AppBindData->processName:Ljava/lang/String;":
                System.out.println(">>> MOCKED AppBindData.processName");
                return new StringObject(vm, "com.android.mgstv");
            default:
                break;
        }
        if (signature.endsWith("Ljava/lang/String;")) {
            String fieldName = signature.substring(signature.lastIndexOf("->") + 2,
                    signature.lastIndexOf(":"));
            String val;
            if (fieldName.contains("Dir") || fieldName.contains("Path"))
                val = "/data/app/com.android.mgstv-1";
            else if (fieldName.equals("processName"))
                val = "com.android.mgstv";
            else if (fieldName.equals("className"))
                val = "com.interactive.brasiliptv.app.AppWrapper";
            else
                val = "/data/app/com.android.mgstv-1";
            System.out.println(">>> MOCKED-FALLBACK String field " + fieldName + " -> \"" + val + "\"");
            return new StringObject(vm, val);
        }
        if (signature.contains(":L")) {
            System.out.println(">>> MOCKED-FALLBACK object field signature=" + signature);
            String clsName = signature.substring(signature.lastIndexOf(":L") + 2,
                    signature.length() - 1).replace('/', '.');
            return vm.resolveClass(clsName).newObject(null);
        }
        System.out.println(">>> UNMOCKED getObjectField (no fallback) signature=" + signature);
        return super.getObjectField(vm, dvmObject, signature);
    }

    @Override
    public int getIntField(BaseVM vm, DvmObject<?> dvmObject, String signature) {
        switch (signature) {
            case "android/content/pm/ApplicationInfo->targetSdkVersion:I":
                return 23;
            default:
                System.out.println(">>> UNMOCKED getIntField signature=" + signature);
                return super.getIntField(vm, dvmObject, signature);
        }
    }

    private static byte[] le32(long v) {
        return new byte[] {
                (byte) (v), (byte) (v >> 8), (byte) (v >> 16), (byte) (v >> 24)
        };
    }

    public Unpack() throws Exception {
        AndroidEmulator emulator = AndroidEmulatorBuilder.for32Bit()
                .setProcessName("com.android.mgstv")
                .addBackendFactory(new Unicorn2Factory(false))
                .build();
        System.out.println(">>> backend class=" + emulator.getBackend().getClass().getName());
        Memory memory = emulator.getMemory();
        memory.setLibraryResolver(new AndroidResolver(23));
        VM vm = emulator.createDalvikVM();
        vm.setVerbose(false);
        vm.setJni(this);

        final Backend backend = emulator.getBackend();

        final long SCRATCH = 0x7f000000L;
        backend.mem_map(SCRATCH, 0x1000, UnicornConst.UC_PROT_READ | UnicornConst.UC_PROT_WRITE);
        // Absorb null+offset reads (e.g. [0x109] during JNI) instead of UC_ERR_READ_UNMAPPED
        try {
            backend.mem_map(0L, 0x1000, UnicornConst.UC_PROT_READ | UnicornConst.UC_PROT_WRITE);
            System.out.println(">>> mapped page0 for null-offset reads");
        } catch (Throwable t) {
            System.out.println(">>> page0 map skipped: " + t);
        }
        byte[] nameBytes = "com.android.mgstv\0".getBytes();
        backend.mem_write(SCRATCH, nameBytes);
        System.out.println(">>> scratch string ptr=0x"+Long.toHexString(SCRATCH));

        // ---- LEVER: exported JNI_OnLoad @ 0x1203725d is `b.w #0x12043544` ----
        // Do NOT NOP that site. init_array: return via sentinel. JNI: jump to real target.
        final long STUB_PC = 0x1203725cL;
        final long REAL_JNI_ONLOAD = 0x12043544L;
        final long BL_CALLEE_F8EC = 0x1203f8ecL; // bl'd from 0x1202e4b6; null-fetches inside
        final boolean[] jniPhase = new boolean[1];
        final boolean[] initHit = new boolean[1];
        final int[] hitCount = new int[1];

        emulator.getSyscallHandler().addIOResolver(new IOResolver() {
            @Override
            @SuppressWarnings({"rawtypes", "unchecked"})
            public FileResult resolve(Emulator emulator, String pathname, int oflags) {
                if ("/proc/self/wchan".equals(pathname)) {
                    return FileResult.success(new ByteArrayFileIO(oflags, pathname, "0\n".getBytes()));
                }
                if ("/proc/self/status".equals(pathname)) {
                    String s = "Name:\tmgstv\nState:\tS\nPid:\t1\nPPid:\t0\nTracerPid:\t0\n";
                    return FileResult.success(new ByteArrayFileIO(oflags, pathname, s.getBytes()));
                }
                return null;
            }
        });

        backend.hook_add_new(new CodeHook() {
            public void hook(Backend b, long addr, int sz, Object u) {
                long lr = backend.reg_read(ArmConst.UC_ARM_REG_LR).longValue();
                int n = ++hitCount[0];
                if (jniPhase[0]) {
                    // Thumb entry must set CPSR.T via PC|1 — even PC runs as ARM and blows up as svc.
                    backend.reg_write(ArmConst.UC_ARM_REG_LR, 0xffff0000L);
                    backend.reg_write(ArmConst.UC_ARM_REG_PC, REAL_JNI_ONLOAD | 1L);
                    if (n <= 40) {
                        System.out.println(">>> stub #" + n + " -> REAL_JNI 0x"
                                + Long.toHexString(REAL_JNI_ONLOAD)
                                + "|1 wasLR=0x" + Long.toHexString(lr));
                    }
                    return;
                }
                boolean goodLr = (lr >= 0x12000000L && lr < 0x12200000L);
                long resume = goodLr ? lr : 0xffff0000L;
                backend.reg_write(ArmConst.UC_ARM_REG_R0, 0L);
                backend.reg_write(ArmConst.UC_ARM_REG_PC, resume);
                if (n <= 40) {
                    System.out.println(">>> stub #" + n + " init LR=0x" + Long.toHexString(lr)
                            + " -> PC=0x" + Long.toHexString(resume));
                }
                if (!initHit[0]) initHit[0] = true;
            }
            public void onAttach(com.github.unidbg.arm.backend.UnHook u) {}
            public void detach() {}
        }, STUB_PC, STUB_PC, null);
        System.out.println(">>> LEVER: stub->0x12043544, callee 0x1203f8ec soft-return");

        // NOTE: do NOT CodeHook 0x12043548 — Unicorn2 installs svc trampolines for code hooks;
        // that produced bogus `svc number: 0x3b5f0` on the push.w site. Patch bytes instead.

        backend.hook_add_new(new CodeHook() {
            int n;
            public void hook(Backend b, long address, int size, Object user) {
                if (!jniPhase[0]) return;
                long lr = backend.reg_read(ArmConst.UC_ARM_REG_LR).longValue();
                backend.reg_write(ArmConst.UC_ARM_REG_R0, 0L);
                backend.reg_write(ArmConst.UC_ARM_REG_PC, lr);
                if (n++ < 8) {
                    System.out.println(">>> soft-ret @0x1203f8ec #" + n
                            + " -> LR=0x" + Long.toHexString(lr));
                }
            }
            public void onAttach(com.github.unidbg.arm.backend.UnHook unHook) {}
            public void detach() {}
        }, BL_CALLEE_F8EC, BL_CALLEE_F8EC, null);

        // Null-call: 0x120370c6 blx r1 when vtable+0x40 is NULL (LR lands at 0x120370c9)
        backend.hook_add_new(new CodeHook() {
            int n;
            public void hook(Backend b, long address, int size, Object user) {
                if (!jniPhase[0]) return;
                backend.reg_write(ArmConst.UC_ARM_REG_R0, 1L); // non-zero: avoid fail→SIGKILL path
                backend.reg_write(ArmConst.UC_ARM_REG_PC, 0x120370c9L);
                if (n++ < 16) {
                    long r1 = backend.reg_read(ArmConst.UC_ARM_REG_R1).longValue();
                    System.out.println(">>> skip blx-r1 @" + Long.toHexString(address)
                            + " #" + n + " r1=0x" + Long.toHexString(r1) + " ret=1");
                }
            }
            public void onAttach(com.github.unidbg.arm.backend.UnHook unHook) {}
            public void detach() {}
        }, 0x120370c6L, 0x120370c6L, null);

        // kill() svc site CodeHook unused — mem NOP at 0x12037b8a instead (preserve frame)

        // Soft-skip SIGKILL site seen by DumpWchan
        backend.hook_add_new(new CodeHook() {
            int n;
            public void hook(Backend b, long address, int size, Object user) {
                long lr = backend.reg_read(ArmConst.UC_ARM_REG_LR).longValue();
                long resume = (lr >= 0x12000000L && lr < 0x12200000L) ? lr : (address + 2);
                backend.reg_write(ArmConst.UC_ARM_REG_R0, 0L);
                backend.reg_write(ArmConst.UC_ARM_REG_PC, resume);
                if (n++ < 8) {
                    System.out.println(">>> SKIP SIGKILL@0x1203a2de -> 0x" + Long.toHexString(resume));
                }
            }
            public void onAttach(com.github.unidbg.arm.backend.UnHook unHook) {}
            public void detach() {}
        }, 0x1203a2deL, 0x1203a2e0L, null);

        // Sanity function
        backend.hook_add_new(new CodeHook() {
            public void hook(Backend backend2, long address, int size, Object user) {
                long lr = backend.reg_read(ArmConst.UC_ARM_REG_LR).longValue();
                backend.reg_write(ArmConst.UC_ARM_REG_R0, 0L);
                backend.reg_write(ArmConst.UC_ARM_REG_PC, lr);
            }
            public void onAttach(com.github.unidbg.arm.backend.UnHook unHook) {}
            public void detach() {}
        }, 0x1202e5d4L, 0x1202e5d6L, null);

        // CTOR-PATCH
        backend.hook_add_new(new CodeHook() {
            public void hook(Backend backend2, long address, int size, Object user) {
                final long OBJ = 0x120923c0L;
                byte[] ptrBytes = new byte[] {
                        (byte) (SCRATCH & 0xff), (byte) ((SCRATCH >> 8) & 0xff),
                        (byte) ((SCRATCH >> 16) & 0xff), (byte) ((SCRATCH >> 24) & 0xff)
                };
                backend.mem_write(OBJ + 0x188, ptrBytes);
                backend.mem_write(OBJ + 0x109, new byte[] { 0 });
                System.out.println(">>> CTOR-PATCH: +0x188->scratch, +0x109=0");
            }
            public void onAttach(com.github.unidbg.arm.backend.UnHook unHook) {}
            public void detach() {}
        }, 0x1203a1d8L, 0x1203a1daL, null);

        // CTOR-SKIP
        backend.hook_add_new(new CodeHook() {
            public void hook(Backend backend2, long address, int size, Object user) {
                long lr = backend.reg_read(ArmConst.UC_ARM_REG_LR).longValue();
                backend.reg_write(ArmConst.UC_ARM_REG_R0, 0L);
                backend.reg_write(ArmConst.UC_ARM_REG_PC, lr);
            }
            public void onAttach(com.github.unidbg.arm.backend.UnHook unHook) {}
            public void detach() {}
        }, 0x1203a1d8L, 0x1203a1daL, null);

        // bls at 0x1203a21e: skip
        backend.hook_add_new(new CodeHook() {
            public void hook(Backend backend2, long address, int size, Object user) {
                backend.reg_write(ArmConst.UC_ARM_REG_PC, 0x1203a220L);
            }
            public void onAttach(com.github.unidbg.arm.backend.UnHook unHook) {}
            public void detach() {}
        }, 0x1203a21eL, 0x1203a220L, null);

        // cbz r1 → force r1=1
        backend.hook_add_new(new CodeHook() {
            public void hook(Backend backend2, long address, int size, Object user) {
                backend.reg_write(ArmConst.UC_ARM_REG_R1, 1L);
            }
            public void onAttach(com.github.unidbg.arm.backend.UnHook unHook) {}
            public void detach() {}
        }, 0x1203a280L, 0x1203a282L, null);

        // func@0x1207b5a0 → force return 1
        backend.hook_add_new(new CodeHook() {
            public void hook(Backend backend2, long address, int size, Object user) {
                long lr = backend.reg_read(ArmConst.UC_ARM_REG_LR).longValue();
                backend.reg_write(ArmConst.UC_ARM_REG_R0, 1L);
                backend.reg_write(ArmConst.UC_ARM_REG_PC, lr);
            }
            public void onAttach(com.github.unidbg.arm.backend.UnHook unHook) {}
            public void detach() {}
        }, 0x1207b5a0L, 0x1207b5a2L, null);

        System.out.println(">>> loading libexec.so ...");
        File so = new File("/tmp/apkx/assets/ijm_lib/armeabi/libexec.so");
        DalvikModule dm = null;
        try {
            dm = vm.loadLibrary(so, true);
            System.out.println(">>> loadLibrary returned base=0x"+Long.toHexString(dm.getModule().base));
            try {
                com.github.unidbg.Module mod = dm.getModule();
                com.github.unidbg.Symbol jniSym = mod.findSymbolByName("JNI_OnLoad", false);
                System.out.println(">>> JNI_OnLoad symbol=" + (jniSym == null ? "null"
                        : ("0x" + Long.toHexString(jniSym.getAddress()))));
            } catch (Throwable t) {
                System.out.println(">>> JNI_OnLoad symbol lookup failed: " + t);
            }
            try {
                for (long ea : new long[]{0x1202e2b0L, 0x1202e4b0L, 0x12037250L, 0x12043544L, 0x1203f8ecL, 0x120370b0L, 0x12037b80L, 0x12037aa0L}) {
                    byte[] chunk = backend.mem_read(ea, 32);
                    System.out.print(">>> MEM@0x" + Long.toHexString(ea) + ": ");
                    for (byte x : chunk) System.out.printf("%02x", x & 0xff);
                    System.out.println();
                }
            } catch (Throwable t) {
                System.out.println(">>> MEM dump failed: " + t);
            }
            // Unicorn2 chokes on push.w {r8,r9,r10} @ 0x12043548 (bogus SWI 0x3b5f0).
            // Replace with: sub sp, #12; nop  (keep frame size; epilogue pop loads garbage — ok).
            try {
                byte[] before = backend.mem_read(0x12043548L, 4);
                System.out.print(">>> before push.w patch: ");
                for (byte x : before) System.out.printf("%02x", x & 0xff);
                System.out.println();
                backend.mem_write(0x12043548L, new byte[]{
                        (byte) 0x83, (byte) 0xb0, // sub sp, #0xc
                        (byte) 0x00, (byte) 0xbf  // nop
                });
                byte[] after = backend.mem_read(0x12043548L, 4);
                System.out.print(">>> after push.w patch:  ");
                for (byte x : after) System.out.printf("%02x", x & 0xff);
                System.out.println();
            } catch (Throwable t) {
                System.out.println(">>> push.w patch failed: " + t);
            }
            // Don't rely on mem patch alone (Unicorn TB cache). Hook kill() entry.
            // Caller retries kill in a loop — after first soft-ret, exit JNI Function32 cleanly.
            final int[] killHits = new int[1];
            backend.hook_add_new(new CodeHook() {
                public void hook(Backend b, long address, int size, Object user) {
                    long lr = backend.reg_read(ArmConst.UC_ARM_REG_LR).longValue();
                    int n = ++killHits[0];
                    if (n >= 2) {
                        // Break kill-retry loop: finish emulated JNI with JNI_VERSION_1_6
                        backend.reg_write(ArmConst.UC_ARM_REG_R0, 0x00010006L);
                        backend.reg_write(ArmConst.UC_ARM_REG_PC, 0xffff0000L);
                        System.out.println(">>> kill() #" + n + " -> force JNI_VERSION + sentinel");
                        return;
                    }
                    backend.reg_write(ArmConst.UC_ARM_REG_R0, 0L);
                    backend.reg_write(ArmConst.UC_ARM_REG_PC, lr);
                    System.out.println(">>> kill() entry soft-ret #" + n
                            + " -> 0x" + Long.toHexString(lr));
                }
                public void onAttach(com.github.unidbg.arm.backend.UnHook unHook) {}
                public void detach() {}
            }, 0x12037b80L, 0x12037b80L, null);
            System.out.println(">>> calling JNI_OnLoad ...");
            long sb = 0x120868f0L, sa = 0x120868e0L;
            backend.mem_write(sa, new byte[]{(byte)sb,(byte)(sb>>8),(byte)(sb>>16),(byte)(sb>>24)});
            backend.mem_write(0x12082340L, new byte[]{(byte)sa,(byte)(sa>>8),(byte)(sa>>16),(byte)(sa>>24)});
            System.out.println(">>> FIX: GOT->singleton->buf");
            // Bypass export stub: call real Thumb JNI_OnLoad directly.
            jniPhase[0] = true;
            // Offset must be odd for Thumb — even 0x43544 runs as ARM and raises bogus SWI.
            System.out.println(">>> jniPhase=true, callFunction REAL_JNI thumb 0x43545");
            Number jniRet = dm.getModule().callFunction(emulator, 0x43545,
                    ((com.github.unidbg.linux.android.dvm.VM) vm).getJavaVM(),
                    null);
            long jniVal = jniRet.longValue() & 0xffffffffL;
            System.out.println(">>> REAL_JNI returned: 0x" + Long.toHexString(jniVal)
                    + " (" + jniRet + ")");
            if (jniVal == 0x00010006L) {
                System.out.println(">>> JNI_OnLoad SUCCESS (JNI_VERSION_1_6)");
            }
        } catch (Throwable t) {
            System.out.println(">>> loadLibrary/JNI_OnLoad threw: ");
            t.printStackTrace(System.out);
        }

        if (dm != null) {
            try {
                final long VTABLE = 0x7f001000L;
                final long SINGLETON = 0x7f002000L;
                backend.mem_map(VTABLE, 0x1000, UnicornConst.UC_PROT_READ | UnicornConst.UC_PROT_WRITE);
                backend.mem_map(SINGLETON, 0x1000, UnicornConst.UC_PROT_READ | UnicornConst.UC_PROT_WRITE);

                byte[] singPtr = new byte[] {
                    (byte)(SINGLETON & 0xff), (byte)((SINGLETON>>8)&0xff),
                    (byte)((SINGLETON>>16)&0xff), (byte)((SINGLETON>>24)&0xff)
                };
                backend.mem_write(0x12082340L, singPtr);
                byte[] vtPtr = new byte[] {
                    (byte)(VTABLE & 0xff), (byte)((VTABLE>>8)&0xff),
                    (byte)((VTABLE>>16)&0xff), (byte)((VTABLE>>24)&0xff)
                };
                backend.mem_write(0x120868e0L, vtPtr);
                for (int i = 0; i < 64; i++) {
                    backend.mem_write(VTABLE + i*4, new byte[]{0,0,0,0});
                }
                System.out.println(">>> FORCE-WROTE singleton/vtable");

                byte[] ijiamiBytes = java.nio.file.Files.readAllBytes(
                        new File("/tmp/apkx/assets/ijiami.dat").toPath());
                System.out.println(">>> ijiami.dat size="+ijiamiBytes.length);

                DvmClass N = vm.resolveClass("s/h/e/l/l/N");
                DvmClass AppClass = vm.resolveClass("android/app/Application");
                DvmObject<?> app = AppClass.newObject(null);
                System.out.println(">>> mock app: "+app);

                System.out.println(">>> calling N.l(Application, path) ...");
                boolean lResult = N.callStaticJniMethodBoolean(emulator,
                        "l(Landroid/app/Application;Ljava/lang/String;)Z",
                        app, "/data/app/com.android.mgstv-1/base.apk");
                System.out.println(">>> N.l returned: "+lResult);

                DvmObject<?> byteArray = new ByteArray(vm, ijiamiBytes);
                DvmObject<?> b2bResult = N.callStaticJniMethodObject(emulator,
                        "b2b([BI)[B", byteArray, ijiamiBytes.length);
                if (b2bResult instanceof ByteArray) {
                    byte[] dexBytes = ((ByteArray) b2bResult).getValue();
                    System.out.println(">>> b2b returned byte["+dexBytes.length+"]");
                    String magic = dexBytes.length >= 8 ?
                        new String(dexBytes, 0, 8).replaceAll("[^\\x20-\\x7e]", ".") : "???";
                    System.out.println(">>> first 8 bytes: "+magic);
                    java.nio.file.Files.write(new File("/tmp/apkx/app_decrypted.dex").toPath(), dexBytes);
                    System.out.println(">>> wrote /tmp/apkx/app_decrypted.dex");
                } else {
                    System.out.println(">>> b2b returned: " + b2bResult);
                }
            } catch (Throwable t) {
                System.out.println(">>> decrypt call threw: ");
                t.printStackTrace(System.out);
            }
        }
    }
    public static void main(String[] args) {
        try { new Unpack(); } catch (Throwable t) { t.printStackTrace(System.out); }
        System.out.println(">>> done");
    }
}
