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
import java.util.LinkedHashSet;

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

    // Trace-only diagnostic hook: prints a label (+ optional register value) up to 8 times,
    // only while jniPhase is active. regConst < 0 means "just print the label, no register".
    private static void addTrace(final Backend backend, final boolean[] jniPhase, long addr,
                                  final String label, final int regConst) {
        final int[] n = new int[1];
        backend.hook_add_new(new CodeHook() {
            public void hook(Backend b, long address, int size, Object user) {
                if (!jniPhase[0]) return;
                if (n[0]++ >= 8) return;
                if (regConst >= 0) {
                    long v = backend.reg_read(regConst).longValue();
                    System.out.println(">>> [trace] " + label + " = 0x" + Long.toHexString(v));
                } else {
                    System.out.println(">>> [trace] " + label);
                }
            }
            public void onAttach(com.github.unidbg.arm.backend.UnHook unHook) {}
            public void detach() {}
        }, addr, addr, null);
    }

    // Like addTrace but dumps multiple named registers at once (session 18 transform-chain trace).
    private static void addRegDump(final Backend backend, final boolean[] jniPhase, long addr,
                                    final String label, final int[] regConsts) {
        final int[] n = new int[1];
        final String[] regNames = new String[regConsts.length];
        for (int i = 0; i < regConsts.length; i++) {
            regNames[i] = regConsts[i] == ArmConst.UC_ARM_REG_R0 ? "r0"
                    : regConsts[i] == ArmConst.UC_ARM_REG_R1 ? "r1"
                    : regConsts[i] == ArmConst.UC_ARM_REG_R2 ? "r2"
                    : regConsts[i] == ArmConst.UC_ARM_REG_R4 ? "r4"
                    : ("reg" + i);
        }
        backend.hook_add_new(new CodeHook() {
            public void hook(Backend b, long address, int size, Object user) {
                if (!jniPhase[0]) return;
                if (n[0]++ >= 6) return;
                StringBuilder sbh = new StringBuilder(">>> [18] " + label + ":");
                for (int i = 0; i < regConsts.length; i++) {
                    long v = backend.reg_read(regConsts[i]).longValue();
                    sbh.append(' ').append(regNames[i]).append("=0x").append(Long.toHexString(v));
                }
                System.out.println(sbh);
            }
            public void onAttach(com.github.unidbg.arm.backend.UnHook unHook) {}
            public void detach() {}
        }, addr, addr, null);
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
        backend.mem_map(SCRATCH, 0x1000,
                UnicornConst.UC_PROT_READ | UnicornConst.UC_PROT_WRITE | UnicornConst.UC_PROT_EXEC);
        // Tiny Thumb stub used to fill NULL vtable slots so real `blx` calls land somewhere
        // valid instead of being PC-skipped. `movs r0,#1; bx lr` == real call, real return.
        final long VTABLE_STUB = SCRATCH + 0x800;
        backend.mem_write(VTABLE_STUB, new byte[]{
                (byte) 0x01, (byte) 0x20, // movs r0, #1
                (byte) 0x70, (byte) 0x47  // bx lr
        });
        System.out.println(">>> VTABLE_STUB written @0x" + Long.toHexString(VTABLE_STUB));
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

        // 0x120370c6 = `blx r1` where r1 = *(obj+0x40) (vtable slot), NULL on this build.
        // Prior approach PC-skipped the whole call (fake ret=1) - downstream code never saw a
        // real call happen, so kill()-retry loop persisted. Fix: force r1 to a real, valid stub
        // and let `blx` execute for real. Real call + real return, everything downstream (frame,
        // LR, any state the callee is expected to touch) looks authentic.
        backend.hook_add_new(new CodeHook() {
            int n;
            public void hook(Backend b, long address, int size, Object user) {
                if (!jniPhase[0]) return;
                long r0 = backend.reg_read(ArmConst.UC_ARM_REG_R0).longValue();
                long r1before = backend.reg_read(ArmConst.UC_ARM_REG_R1).longValue();
                long lr = backend.reg_read(ArmConst.UC_ARM_REG_LR).longValue();
                long sp = backend.reg_read(ArmConst.UC_ARM_REG_SP).longValue();
                // NOTE (verified via live capstone disasm): r0 here is r5/JavaVM* (overwritten by
                // `mov r0,r5` at 0x120370c4, right after r1 was already loaded from the real
                // vtable slot at 0x120370c2). r0 is NOT the vtable object - it's just the call
                // arg. The real fix is the pre-write to VTABLE_SLOT_40 (0x12086930) above; this
                // hook is now only a safety net if that pre-write didn't take.
                if (r1before == 0L) {
                    backend.reg_write(ArmConst.UC_ARM_REG_R1, VTABLE_STUB | 1L);
                }
                if (n++ < 16) {
                    System.out.println(">>> vtable-fixup blx-r1 @" + Long.toHexString(address)
                            + " #" + n + " r0(JavaVM*)=0x" + Long.toHexString(r0)
                            + " origR1=0x" + Long.toHexString(r1before)
                            + " lr=0x" + Long.toHexString(lr) + " sp=0x" + Long.toHexString(sp)
                            + (r1before == 0L ? (" -> FORCED r1=0x" + Long.toHexString(VTABLE_STUB | 1L)) : " -> r1 already valid (pre-write worked), no override"));
                    try {
                        byte[] slotMem = backend.mem_read(0x12086930L, 4);
                        StringBuilder sbh = new StringBuilder();
                        for (byte x : slotMem) sbh.append(String.format("%02x", x & 0xff));
                        System.out.println(">>> VTABLE_SLOT_40 (0x12086930) live value: " + sbh);
                    } catch (Throwable t) {
                        System.out.println(">>> VTABLE_SLOT_40 mem_read failed: " + t);
                    }
                    try {
                        byte[] spMem = backend.mem_read(sp, 0x40);
                        StringBuilder sbh = new StringBuilder();
                        for (byte x : spMem) sbh.append(String.format("%02x", x & 0xff));
                        System.out.println(">>> stack[sp..sp+0x40]: " + sbh);
                    } catch (Throwable t) {
                        System.out.println(">>> sp mem_read failed: " + t);
                    }
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
            int n;
            public void hook(Backend backend2, long address, int size, Object user) {
                long lr = backend.reg_read(ArmConst.UC_ARM_REG_LR).longValue();
                backend.reg_write(ArmConst.UC_ARM_REG_R0, 1L);
                backend.reg_write(ArmConst.UC_ARM_REG_PC, lr);
                if (jniPhase[0] && n++ < 8) {
                    System.out.println(">>> [trace] 0x1207b5a0 forced-ret1 during JNI, lr=0x" + Long.toHexString(lr));
                }
            }
            public void onAttach(com.github.unidbg.arm.backend.UnHook unHook) {}
            public void detach() {}
        }, 0x1207b5a0L, 0x1207b5a2L, null);

        // --- Trace-only hooks: which way does 0x12037a80's branch chain actually go? ---
        addTrace(backend, jniPhase, 0x12037aa4L, "cmp-r0-after-P2+0x188 (r0)", ArmConst.UC_ARM_REG_R0);
        addTrace(backend, jniPhase, 0x12037aa8L, "P2+0x188 nonzero, fell through to blx-0x1207b5a0 path", -1);
        addTrace(backend, jniPhase, 0x12037ac0L, "cmp-r0(pid)-vs-r5(threshold): r0", ArmConst.UC_ARM_REG_R0);
        addTrace(backend, jniPhase, 0x12037ac0L, "  ...r5", ArmConst.UC_ARM_REG_R5);
        addTrace(backend, jniPhase, 0x12037ac4L, "pid<=threshold path NOT taken to flag-block (fallthrough)", -1);
        addTrace(backend, jniPhase, 0x12037b4aL, "ENTERED flag-check block (0x12037b4a)", -1);

        // 0x12037a10/0x12037a6a: `ldr.w r6,[r0,#0x35c]` then `blx r6` - offset 0x35c (860/4=215)
        // matches the well-known JNINativeInterface index for RegisterNatives on 32-bit Android.
        // If real, these are the ACTUAL RegisterNatives(env, clazz, methods, nMethods) calls -
        // BEFORE the kill()-anti-tamper region, not after. Trace args + real return value
        // (don't override anything - let unidbg's own JNIEnv handle it for real).
        for (final long callAddr : new long[]{0x12037a16L, 0x12037a72L}) {
            backend.hook_add_new(new CodeHook() {
                int n;
                public void hook(Backend b, long address, int size, Object user) {
                    if (!jniPhase[0] || n++ >= 6) return;
                    long r0 = backend.reg_read(ArmConst.UC_ARM_REG_R0).longValue();
                    long r1 = backend.reg_read(ArmConst.UC_ARM_REG_R1).longValue();
                    long r2 = backend.reg_read(ArmConst.UC_ARM_REG_R2).longValue();
                    long r3 = backend.reg_read(ArmConst.UC_ARM_REG_R3).longValue();
                    long r6 = backend.reg_read(ArmConst.UC_ARM_REG_R6).longValue();
                    System.out.println(">>> [RegisterNatives?] blx @0x" + Long.toHexString(address)
                            + " target(r6)=0x" + Long.toHexString(r6)
                            + " env(r0)=0x" + Long.toHexString(r0)
                            + " clazz(r1)=0x" + Long.toHexString(r1)
                            + " methods(r2)=0x" + Long.toHexString(r2)
                            + " nMethods(r3)=0x" + Long.toHexString(r3));
                }
                public void onAttach(com.github.unidbg.arm.backend.UnHook unHook) {}
                public void detach() {}
            }, callAddr, callAddr, null);
        }
        addTrace(backend, jniPhase, 0x12037a18L, "RegisterNatives#1 returned (r0)", ArmConst.UC_ARM_REG_R0);
        addTrace(backend, jniPhase, 0x12037a74L, "RegisterNatives#2 returned (r0)", ArmConst.UC_ARM_REG_R0);

        // ENTRY of 0x12037a80 itself - the RegisterNatives?-block hooks never fired, meaning
        // our current path reaches 0x12037a80/a92 WITHOUT falling through from 0x120379d0's
        // RegisterNatives calls. Capture LR here to find the *actual* caller/entry condition.
        backend.hook_add_new(new CodeHook() {
            int n;
            public void hook(Backend b, long address, int size, Object user) {
                if (!jniPhase[0] || n++ >= 6) return;
                long lr = backend.reg_read(ArmConst.UC_ARM_REG_LR).longValue();
                long r0 = backend.reg_read(ArmConst.UC_ARM_REG_R0).longValue();
                long r4 = backend.reg_read(ArmConst.UC_ARM_REG_R4).longValue();
                System.out.println(">>> [ENTRY] 0x12037a80 lr=0x" + Long.toHexString(lr)
                        + " r0=0x" + Long.toHexString(r0) + " r4=0x" + Long.toHexString(r4));
            }
            public void onAttach(com.github.unidbg.arm.backend.UnHook unHook) {}
            public void detach() {}
        }, 0x12037a80L, 0x12037a80L, null);
        // Also trace the earlier fork point right after our vtable fix: does the "blx #0x1207b310"
        // continuation (0x120370e0) and the big obfuscated function at 0x120370f0 take the
        // success or failure branch at its own first gate (0x120370fa call + 0x12037100 beq)?
        addTrace(backend, jniPhase, 0x120370faL, "ENTRY big-fn 0x120370f0 (r0=arg)", ArmConst.UC_ARM_REG_R0);
        addTrace(backend, jniPhase, 0x120370feL, "big-fn gate1 result (r0) before beq 0x120371a6", ArmConst.UC_ARM_REG_R0);
        addTrace(backend, jniPhase, 0x120371a6L, "big-fn gate1 TAKEN (early-exit branch)", -1);

        // 0x1201e378: real init call reached after the FLAG_X fix (session 15c) - crashes on
        // an unmapped read inside here. Log entry args + a few registers to find the bad ptr
        // before it dereferences it.
        backend.hook_add_new(new CodeHook() {
            int n;
            public void hook(Backend b, long address, int size, Object user) {
                if (!jniPhase[0] || n++ >= 4) return;
                long r0 = backend.reg_read(ArmConst.UC_ARM_REG_R0).longValue();
                long lr = backend.reg_read(ArmConst.UC_ARM_REG_LR).longValue();
                System.out.println(">>> [ENTRY] 0x1201e378 r0(arg)=0x" + Long.toHexString(r0)
                        + " lr=0x" + Long.toHexString(lr));
                try {
                    byte[] argMem = backend.mem_read(r0, 0x20);
                    StringBuilder sbh = new StringBuilder();
                    for (byte x : argMem) sbh.append(String.format("%02x", x & 0xff));
                    System.out.println(">>> *r0[0x20]: " + sbh);
                } catch (Throwable t) {
                    System.out.println(">>> *r0 read failed: " + t);
                }
            }
            public void onAttach(com.github.unidbg.arm.backend.UnHook unHook) {}
            public void detach() {}
        }, 0x1201e378L, 0x1201e378L, null);
        // 0x1201e378's own body (disasm'd session 15d): GOT_Y chain `ldr r6,[pc]; ldr r0,[r6];
        // ldr r0,[r0,#0x10]; ldr r5,[r0,#0x40]; blx r5` - same double-indirection shape as our
        // known GOT slots. Trace it live to find which link in the chain is bad.
        backend.hook_add_new(new CodeHook() {
            int n;
            public void hook(Backend b, long address, int size, Object user) {
                if (!jniPhase[0] || n++ >= 4) return;
                long r6 = backend.reg_read(ArmConst.UC_ARM_REG_R6).longValue();
                System.out.println(">>> [1e378] r6(GOT_Y)=0x" + Long.toHexString(r6));
                try {
                    byte[] m = backend.mem_read(r6, 4);
                    System.out.println(">>> [1e378] *(GOT_Y) = 0x" + Long.toHexString(
                            (m[0]&0xffL)|((m[1]&0xffL)<<8)|((m[2]&0xffL)<<16)|((m[3]&0xffL)<<24)));
                } catch (Throwable t) {
                    System.out.println(">>> [1e378] *(GOT_Y) read failed: " + t);
                }
            }
            public void onAttach(com.github.unidbg.arm.backend.UnHook unHook) {}
            public void detach() {}
        }, 0x1201e398L, 0x1201e398L, null);
        backend.hook_add_new(new CodeHook() {
            int n;
            public void hook(Backend b, long address, int size, Object user) {
                if (!jniPhase[0] || n++ >= 4) return;
                long r0 = backend.reg_read(ArmConst.UC_ARM_REG_R0).longValue();
                System.out.println(">>> [1e378] before ldr r5,[r0,#0x40]: r0=0x" + Long.toHexString(r0));
                try {
                    byte[] m = backend.mem_read(r0 + 0x40, 4);
                    System.out.println(">>> [1e378] *(r0+0x40) = 0x" + Long.toHexString(
                            (m[0]&0xffL)|((m[1]&0xffL)<<8)|((m[2]&0xffL)<<16)|((m[3]&0xffL)<<24)));
                } catch (Throwable t) {
                    System.out.println(">>> [1e378] *(r0+0x40) read failed: " + t);
                }
            }
            public void onAttach(com.github.unidbg.arm.backend.UnHook unHook) {}
            public void detach() {}
        }, 0x1201e39aL, 0x1201e39aL, null);
        addTrace(backend, jniPhase, 0x1201e3aeL, "before blx r5 (r5=target)", ArmConst.UC_ARM_REG_R5);
        // Confirm reachability of VTABLE_STUB itself on this second call (does the blx r5
        // actually land here, or does it fault before even reaching the target?).
        addTrace(backend, jniPhase, 0x7f000800L, "VTABLE_STUB entry reached", -1);
        addTrace(backend, jniPhase, 0x1201e3b0L, "after blx r5 returned, r0", ArmConst.UC_ARM_REG_R0);

        // --- Session 16: register-dump chain for 0x12037c18 (session 15d/15e blocker) ---
        // Partial disasm (NEXT-BLOCKER.md session15d): sb(r9) = *(new GOT slot, NOT our known
        // 0x12082340/P1/P2 chain); r0=*(sb); r1=*(r0+0x24); r0=*(r0+0x44) (call arg);
        // r4=*(r1+0x38) (fn ptr); blx r4. Log every link live instead of hand-resolving literals.
        backend.hook_add_new(new CodeHook() {
            int n;
            public void hook(Backend b, long address, int size, Object user) {
                if (!jniPhase[0] || n++ >= 6) return;
                long sb = backend.reg_read(ArmConst.UC_ARM_REG_R9).longValue();
                System.out.println(">>> [12037c18] at 0x12037c3a: sb(r9)=0x" + Long.toHexString(sb));
                try {
                    byte[] m = backend.mem_read(sb, 4);
                    System.out.println(">>> [12037c18] *(sb) = 0x" + Long.toHexString(
                            (m[0]&0xffL)|((m[1]&0xffL)<<8)|((m[2]&0xffL)<<16)|((m[3]&0xffL)<<24)));
                } catch (Throwable t) {
                    System.out.println(">>> [12037c18] *(sb) read failed: " + t);
                }
            }
            public void onAttach(com.github.unidbg.arm.backend.UnHook unHook) {}
            public void detach() {}
        }, 0x12037c3aL, 0x12037c3aL, null);
        backend.hook_add_new(new CodeHook() {
            int n;
            public void hook(Backend b, long address, int size, Object user) {
                if (!jniPhase[0] || n++ >= 6) return;
                long r0 = backend.reg_read(ArmConst.UC_ARM_REG_R0).longValue();
                System.out.println(">>> [12037c18] at 0x12037c3e: r0(obj)=0x" + Long.toHexString(r0));
                try {
                    byte[] m = backend.mem_read(r0 + 0x24, 4);
                    System.out.println(">>> [12037c18] *(obj+0x24) = 0x" + Long.toHexString(
                            (m[0]&0xffL)|((m[1]&0xffL)<<8)|((m[2]&0xffL)<<16)|((m[3]&0xffL)<<24)));
                } catch (Throwable t) {
                    System.out.println(">>> [12037c18] *(obj+0x24) read failed: " + t);
                }
            }
            public void onAttach(com.github.unidbg.arm.backend.UnHook unHook) {}
            public void detach() {}
        }, 0x12037c3eL, 0x12037c3eL, null);
        backend.hook_add_new(new CodeHook() {
            int n;
            public void hook(Backend b, long address, int size, Object user) {
                if (!jniPhase[0] || n++ >= 6) return;
                long r0 = backend.reg_read(ArmConst.UC_ARM_REG_R0).longValue();
                System.out.println(">>> [12037c18] at 0x12037c40: r0(obj)=0x" + Long.toHexString(r0)
                        + " (about to deref +0x44 for call arg)");
                try {
                    byte[] m = backend.mem_read(r0 + 0x44, 4);
                    System.out.println(">>> [12037c18] *(obj+0x44) = 0x" + Long.toHexString(
                            (m[0]&0xffL)|((m[1]&0xffL)<<8)|((m[2]&0xffL)<<16)|((m[3]&0xffL)<<24)));
                } catch (Throwable t) {
                    System.out.println(">>> [12037c18] *(obj+0x44) read failed: " + t);
                }
            }
            public void onAttach(com.github.unidbg.arm.backend.UnHook unHook) {}
            public void detach() {}
        }, 0x12037c40L, 0x12037c40L, null);
        backend.hook_add_new(new CodeHook() {
            int n;
            public void hook(Backend b, long address, int size, Object user) {
                if (!jniPhase[0] || n++ >= 6) return;
                long r1 = backend.reg_read(ArmConst.UC_ARM_REG_R1).longValue();
                System.out.println(">>> [12037c18] at 0x12037c42: r1(that)=0x" + Long.toHexString(r1)
                        + " (about to deref +0x38 for fn ptr)");
                try {
                    byte[] m = backend.mem_read(r1 + 0x38, 4);
                    System.out.println(">>> [12037c18] *(that+0x38) = 0x" + Long.toHexString(
                            (m[0]&0xffL)|((m[1]&0xffL)<<8)|((m[2]&0xffL)<<16)|((m[3]&0xffL)<<24)));
                } catch (Throwable t) {
                    System.out.println(">>> [12037c18] *(that+0x38) read failed: " + t);
                }
            }
            public void onAttach(com.github.unidbg.arm.backend.UnHook unHook) {}
            public void detach() {}
        }, 0x12037c42L, 0x12037c42L, null);
        addTrace(backend, jniPhase, 0x12037c4cL, "0x12037c18 fn ptr (r4) right before blx", ArmConst.UC_ARM_REG_R4);

        // Session 16 cont: new crash past 0x12037c18 - UC_ERR_READ_UNMAPPED addr=0x12280001,
        // PC=0x1203a314. Dump full register file at that PC (before it faults) to find which
        // register holds the bad pointer/offset, live, instead of guessing from static bytes.
        backend.hook_add_new(new CodeHook() {
            int n;
            public void hook(Backend b, long address, int size, Object user) {
                if (!jniPhase[0] || n++ >= 6) return;
                long r0dump = backend.reg_read(ArmConst.UC_ARM_REG_R0).longValue();
                try {
                    byte[] entry = backend.mem_read(r0dump, 0x10);
                    StringBuilder eh = new StringBuilder();
                    for (byte x : entry) eh.append(String.format("%02x", x & 0xff));
                    System.out.println(">>> [3a314] entry@0x" + Long.toHexString(r0dump) + " bytes=" + eh);
                } catch (Throwable t) {
                    System.out.println(">>> [3a314] entry read failed: " + t);
                }
                StringBuilder sbh = new StringBuilder(">>> [3a314] regs:");
                int[] regs = {ArmConst.UC_ARM_REG_R0, ArmConst.UC_ARM_REG_R1, ArmConst.UC_ARM_REG_R2,
                        ArmConst.UC_ARM_REG_R3, ArmConst.UC_ARM_REG_R4, ArmConst.UC_ARM_REG_R5,
                        ArmConst.UC_ARM_REG_R6, ArmConst.UC_ARM_REG_R7, ArmConst.UC_ARM_REG_R8,
                        ArmConst.UC_ARM_REG_R9, ArmConst.UC_ARM_REG_R10, ArmConst.UC_ARM_REG_R11,
                        ArmConst.UC_ARM_REG_R12, ArmConst.UC_ARM_REG_LR, ArmConst.UC_ARM_REG_SP};
                String[] names = {"r0","r1","r2","r3","r4","r5","r6","r7","r8","sb(r9)","sl(r10)","fp(r11)","ip(r12)","lr","sp"};
                for (int i = 0; i < regs.length; i++) {
                    long v = backend.reg_read(regs[i]).longValue();
                    sbh.append(' ').append(names[i]).append("=0x").append(Long.toHexString(v));
                }
                System.out.println(sbh);
                // Session 20: dump the shared key/nonce buffer (r2, same stack addr every call
                // per session16 notes) to see if IT is itself uninitialized garbage - if so,
                // that's likely the true root cause behind the entries buffer not decrypting
                // into readable text (feeding the 0x12026d74 tokenizer real strings).
                try {
                    long keyPtr = backend.reg_read(ArmConst.UC_ARM_REG_R2).longValue();
                    byte[] key = backend.mem_read(keyPtr, 0x20);
                    StringBuilder kh = new StringBuilder();
                    for (byte x : key) kh.append(String.format("%02x", x & 0xff));
                    System.out.println(">>> [3a314] key/nonce buf @0x" + Long.toHexString(keyPtr) + ": " + kh);
                } catch (Throwable t) {
                    System.out.println(">>> [3a314] key buf read failed: " + t);
                }
            }
            public void onAttach(com.github.unidbg.arm.backend.UnHook unHook) {}
            public void detach() {}
        }, 0x1203a314L, 0x1203a314L, null);
        // Also trace entry to the enclosing function (nearest hook we know, 0x1203a1d8) with LR
        // this time, to see if this is a fresh call path (not the init_array ctor dispatch).
        backend.hook_add_new(new CodeHook() {
            int n;
            public void hook(Backend b, long address, int size, Object user) {
                if (!jniPhase[0] || n++ >= 6) return;
                long lr = backend.reg_read(ArmConst.UC_ARM_REG_LR).longValue();
                System.out.println(">>> [3a1d8-during-jni] hit while jniPhase, lr=0x" + Long.toHexString(lr));
            }
            public void onAttach(com.github.unidbg.arm.backend.UnHook unHook) {}
            public void detach() {}
        }, 0x1203a1d8L, 0x1203a1daL, null);
        // Session 16 cont: exact faulting instruction is at 0x1203a36e (crash: byte read
        // address=0x12280001). Dump full regs at the EXACT faulting PC to see which register
        // holds/derives the bad address right before the read executes.
        backend.hook_add_new(new CodeHook() {
            int n;
            public void hook(Backend b, long address, int size, Object user) {
                if (!jniPhase[0] || n++ >= 8) return;
                StringBuilder sbh = new StringBuilder(">>> [3a36e] regs:");
                int[] regs = {ArmConst.UC_ARM_REG_R0, ArmConst.UC_ARM_REG_R1, ArmConst.UC_ARM_REG_R2,
                        ArmConst.UC_ARM_REG_R3, ArmConst.UC_ARM_REG_R4, ArmConst.UC_ARM_REG_R5,
                        ArmConst.UC_ARM_REG_R6, ArmConst.UC_ARM_REG_R7, ArmConst.UC_ARM_REG_R8,
                        ArmConst.UC_ARM_REG_R9, ArmConst.UC_ARM_REG_R10, ArmConst.UC_ARM_REG_R11,
                        ArmConst.UC_ARM_REG_R12, ArmConst.UC_ARM_REG_LR, ArmConst.UC_ARM_REG_SP};
                String[] names = {"r0","r1","r2","r3","r4","r5","r6","r7","r8","sb(r9)","sl(r10)","fp(r11)","ip(r12)","lr","sp"};
                for (int i = 0; i < regs.length; i++) {
                    long v = backend.reg_read(regs[i]).longValue();
                    sbh.append(' ').append(names[i]).append("=0x").append(Long.toHexString(v));
                }
                System.out.println(sbh);
            }
            public void onAttach(com.github.unidbg.arm.backend.UnHook unHook) {}
            public void detach() {}
        }, 0x1203a36eL, 0x1203a36eL, null);

        // Session 17: cheapest-first fix per NEXT-BLOCKER.md session16 recommendation #1.
        // 0x12037dac: `asr.w sl, r6, #4` computes the decrypt-loop's iteration bound from an
        // obfuscated size (fed ultimately from sp+0x20 at function entry, not yet traced back).
        // Real data at the entries buffer (0x12240484) only has ~2 plausible real entries
        // (index 4 has a real in-module pointer 0x120112e8; index 5 looks like an end sentinel
        // 0xfffffff0 pattern) - the computed sl is ~12,700x too large. `asr.w` is a 4-byte
        // Thumb-2 instruction, so hook the NEXT address (0x12037db0, `movs r6,#0`) - hooking
        // 0x12037dac itself fires BEFORE asr.w executes and our forced value would just get
        // overwritten by it.
        backend.hook_add_new(new CodeHook() {
            int n;
            public void hook(Backend b, long address, int size, Object user) {
                if (!jniPhase[0]) return;
                long before = backend.reg_read(ArmConst.UC_ARM_REG_R10).longValue();
                backend.reg_write(ArmConst.UC_ARM_REG_R10, 6L);
                if (n++ < 4) {
                    System.out.println(">>> [17] sl(r10) forced: was 0x" + Long.toHexString(before) + " -> 6");
                }
            }
            public void onAttach(com.github.unidbg.arm.backend.UnHook unHook) {}
            public void detach() {}
        }, 0x12037db0L, 0x12037db0L, null);
        // After the (now-bounded) fixed-size loop exits, disasm shows a SECOND, trailing call to
        // the same decrypt routine at 0x12037dce: `add r2,sp,#0x128; mov r0,r4; mov r1,r5; bl
        // 0x1203a314` - this time r1=r5, the "remaining bytes" countdown register that was
        // decremented alongside our forced sl loop but never reset (started from the same wrong
        // huge size). r4 is left at buffer+6*0x10=0x122404e4 (right where the earlier crash was).
        // IMPORTANT (session 17 correction): r5 is used AGAIN right after this, at 0x12037e0a
        // (`mov r0,r5`) as an argument to a real string/class-table resolver (`bl 0x12026d74`) -
        // forcing r5 itself to 0 earlier (first attempt) corrupted THAT call too, producing a
        // null className passed into a REAL FindClass call further downstream (DalvikVM$3,
        // unidbg's real FindClass implementation, crashed on Pointer.getString() of a null arg).
        // Surgical fix: hook AFTER the `mov r1,r5` at 0x12037dcc has already copied r5 into r1
        // (i.e. hook the `bl` itself, 0x12037dce) and zero ONLY r1 there - r5 is left completely
        // untouched for its later, legitimate use.
        backend.hook_add_new(new CodeHook() {
            int n;
            public void hook(Backend b, long address, int size, Object user) {
                if (!jniPhase[0]) return;
                long before = backend.reg_read(ArmConst.UC_ARM_REG_R1).longValue();
                backend.reg_write(ArmConst.UC_ARM_REG_R1, 0L);
                if (n++ < 4) {
                    System.out.println(">>> [17] trailing-decrypt call arg r1 forced: was 0x"
                            + Long.toHexString(before) + " -> 0 (r5 left untouched)");
                }
            }
            public void onAttach(com.github.unidbg.arm.backend.UnHook unHook) {}
            public void detach() {}
        }, 0x12037dceL, 0x12037dceL, null);

        // The crash moved further! Now hits DalvikVM$3 (unidbg's REAL FindClass) with a null
        // className, via an interrupt at PC=unidbg@0xfffe00b4, LR=0x120378c5. That LR sits right
        // after a `ldr r0,[r4]; ldr r2,[r0,#0x18]; ...; blx r2` sequence around 0x120378aa-b2
        // (per the much-earlier 0x12037878 disasm) - dump every register there to see whether
        // this is genuinely calling through the real env (r4 = real JNIEnv, not our P2) and what
        // r1 (would-be className arg) actually is at the call site.
        backend.hook_add_new(new CodeHook() {
            int n;
            public void hook(Backend b, long address, int size, Object user) {
                if (!jniPhase[0] || n++ >= 6) return;
                StringBuilder sbh = new StringBuilder(">>> [FindClass-site] regs @0x" + Long.toHexString(address) + ":");
                int[] regs = {ArmConst.UC_ARM_REG_R0, ArmConst.UC_ARM_REG_R1, ArmConst.UC_ARM_REG_R2,
                        ArmConst.UC_ARM_REG_R3, ArmConst.UC_ARM_REG_R4, ArmConst.UC_ARM_REG_R5,
                        ArmConst.UC_ARM_REG_R6, ArmConst.UC_ARM_REG_LR};
                String[] names = {"r0","r1","r2","r3","r4","r5","r6","lr"};
                for (int i = 0; i < regs.length; i++) {
                    long v = backend.reg_read(regs[i]).longValue();
                    sbh.append(' ').append(names[i]).append("=0x").append(Long.toHexString(v));
                }
                System.out.println(sbh);
            }
            public void onAttach(com.github.unidbg.arm.backend.UnHook unHook) {}
            public void detach() {}
        }, 0x120378aaL, 0x120378b2L, null);

        // Session 18: trace the transform chain between the decrypt loop exit and the FindClass
        // call, per NEXT-BLOCKER.md session17's next-steps plan. Register dumps at each of the
        // 4 unexplored call sites (0x12026d74, 0x1203f9b0, both 0x1207b630 calls, both/all
        // 0x1207b400 calls) to see which is supposed to populate *(P2+0x18c+4) and why it isn't.
        // FIXED (was mistimed): hooking 0x12037e0a fires BEFORE `mov r0,r5;mov r1,fp;mov r2,sl`
        // execute, so it read stale registers, not the real args. Hook 0x12037e10 (the `bl`
        // itself) instead - fires after all 3 movs have run.
        addRegDump(backend, jniPhase, 0x12037e10L, "before bl 0x12026d74 (r0=r5,r1=fp,r2=sl)",
                new int[]{ArmConst.UC_ARM_REG_R0, ArmConst.UC_ARM_REG_R1, ArmConst.UC_ARM_REG_R2});
        addRegDump(backend, jniPhase, 0x12037e14L, "after bl 0x12026d74 returned",
                new int[]{ArmConst.UC_ARM_REG_R0});
        addRegDump(backend, jniPhase, 0x12037e1cL, "before bl 0x1203f9b0 (r4=prev result)",
                new int[]{ArmConst.UC_ARM_REG_R4});
        addRegDump(backend, jniPhase, 0x12037e20L, "after bl 0x1203f9b0, byte at [r4]",
                new int[]{ArmConst.UC_ARM_REG_R0, ArmConst.UC_ARM_REG_R4});
        addRegDump(backend, jniPhase, 0x12037e2cL, "before 1st blx 0x1207b630 (r0=r4,r1=r8)",
                new int[]{ArmConst.UC_ARM_REG_R0, ArmConst.UC_ARM_REG_R1});
        addRegDump(backend, jniPhase, 0x12037e30L, "after 1st blx 0x1207b630",
                new int[]{ArmConst.UC_ARM_REG_R0});
        addRegDump(backend, jniPhase, 0x12037e3cL, "before 2nd blx 0x1207b630 (r0=0,r1=r8)",
                new int[]{ArmConst.UC_ARM_REG_R0, ArmConst.UC_ARM_REG_R1});
        addRegDump(backend, jniPhase, 0x12037e40L, "after 2nd blx 0x1207b630",
                new int[]{ArmConst.UC_ARM_REG_R0});
        addRegDump(backend, jniPhase, 0x12037e46L, "before 1st blx 0x1207b400 (r0=r4,r1=[sp+0x18])",
                new int[]{ArmConst.UC_ARM_REG_R0, ArmConst.UC_ARM_REG_R1});
        addRegDump(backend, jniPhase, 0x12037e4aL, "after 1st blx 0x1207b400",
                new int[]{ArmConst.UC_ARM_REG_R0});

        // ROOT CAUSE FOUND (session 18): the call at 0x12037c4c that we fixed with P2+0x24/+0x38
        // (VTABLE_STUB) isn't just a boolean gate - ground-truth disasm shows its call site sets
        // up TWO BY-REFERENCE OUTPUT ARGS right before the call:
        //   0x12037c46: add r2,sp,#0x24   ; r2 = &sp[0x24]  (out param)
        //   0x12037c48: add r3,sp,#0x20   ; r3 = &sp[0x20]  (out param)
        //   0x12037c4c: blx r4            ; our stub - does nothing to *r2/*r3!
        // The real function is meant to WRITE a real entry-count/size into those two stack
        // slots. Our stub only returns r0=1 and touches nothing else, so sp[0x20]/sp[0x24] stay
        // as whatever garbage was already on the stack (confirmed: r5 loaded from there later
        // was 0xffffffff). This is exactly why the `sl`/`r5` bound we hand-forced to 6 never
        // matched the REAL semantic count the transform chain needed downstream (it just needed
        // "doesn't crash the XOR loop", not "is the true count"). Fix: write real values
        // directly into *(sp+0x20) and *(sp+0x24) at the call site, before the stub runs -
        // using 2, matching the one real-looking pointer-bearing entry (index 4, which itself
        // contained a literal 0x00000002 right after its real pointer field).
        // NOTE: a CodeHook at 0x12037c46-0x12037c4c (the call site itself, before the stub call)
        // mysteriously never fires despite the code demonstrably executing (malloc at 0x12037c52
        // right after DOES run, confirmed via P2+0x18c getting a real pointer). Root cause of
        // that specific non-firing not resolved - possibly a translation-block/hook-registration
        // quirk specific to this address in this unidbg version. Worked around by hooking the
        // READ point instead (0x12037c64's `ldrd r5,r4,[sp,#0x20]`, confirmed reached - its
        // effects are visible in later diagnostics) and overriding the REGISTERS directly right
        // after the load, rather than the memory before it.
        backend.hook_add_new(new CodeHook() {
            int n;
            public void hook(Backend b, long address, int size, Object user) {
                if (!jniPhase[0]) return;
                long r5before = backend.reg_read(ArmConst.UC_ARM_REG_R5).longValue();
                long r4before = backend.reg_read(ArmConst.UC_ARM_REG_R4).longValue();
                backend.reg_write(ArmConst.UC_ARM_REG_R5, 2L);
                backend.reg_write(ArmConst.UC_ARM_REG_R4, 2L);
                if (n++ < 4) {
                    System.out.println(">>> [18] post-ldrd override: r5 was 0x" + Long.toHexString(r5before)
                            + " r4 was 0x" + Long.toHexString(r4before) + " -> both forced to 2 (real count)");
                }
            }
            public void onAttach(com.github.unidbg.arm.backend.UnHook unHook) {}
            public void detach() {}
        }, 0x12037c68L, 0x12037c68L, null);

        // Also: full 0x3c-byte dump of the malloc'd method-table buffer right before FindClass,
        // to see whether ANY of it got populated or the whole thing is still zero.
        backend.hook_add_new(new CodeHook() {
            int n;
            public void hook(Backend b, long address, int size, Object user) {
                if (!jniPhase[0] || n++ >= 4) return;
                try {
                    byte[] tbl = backend.mem_read(0x120868f0L + 0x18cL, 4); // P2+0x18c (P2 not yet in scope here)
                    long tblPtr = (tbl[0]&0xffL)|((tbl[1]&0xffL)<<8)|((tbl[2]&0xffL)<<16)|((tbl[3]&0xffL)<<24);
                    System.out.println(">>> [18] *(P2+0x18c) table ptr = 0x" + Long.toHexString(tblPtr));
                    if (tblPtr != 0) {
                        byte[] full = backend.mem_read(tblPtr, 0x3c);
                        StringBuilder sbh = new StringBuilder();
                        for (byte x : full) sbh.append(String.format("%02x", x & 0xff));
                        System.out.println(">>> [18] method-table[0x3c] @0x" + Long.toHexString(tblPtr) + ": " + sbh);
                    }
                } catch (Throwable t) {
                    System.out.println(">>> [18] method-table dump failed: " + t);
                }
            }
            public void onAttach(com.github.unidbg.arm.backend.UnHook unHook) {}
            public void detach() {}
        }, 0x120378aaL, 0x120378aaL, null);

        // Wide execution trace: log every DISTINCT address actually executed across the whole
        // span from our vtable fix to the kill() block, in visit order. This reconstructs the
        // real path in one run instead of bisecting address-by-address across many round trips.
        {
            final LinkedHashSet<Long> seen = new LinkedHashSet<>();
            final int[] printed = {0};
            backend.hook_add_new(new CodeHook() {
                public void hook(Backend b, long address, int size, Object user) {
                    if (!jniPhase[0]) return;
                    if (seen.add(address) && printed[0]++ < 400) {
                        System.out.println(">>> [walk] 0x" + Long.toHexString(address));
                    }
                }
                public void onAttach(com.github.unidbg.arm.backend.UnHook unHook) {}
                public void detach() {}
            }, 0x120370e0L, 0x12037b90L, null);
        }
        // Session 16: second wide walk trace covering the region PAST 0x12037c18 (which we just
        // cleared) through to the new crash site at 0x1203a314, to see the real path taken
        // instead of guessing.
        {
            final LinkedHashSet<Long> seen2 = new LinkedHashSet<>();
            final int[] printed2 = {0};
            backend.hook_add_new(new CodeHook() {
                public void hook(Backend b, long address, int size, Object user) {
                    if (!jniPhase[0]) return;
                    if (seen2.add(address) && printed2[0]++ < 400) {
                        System.out.println(">>> [walk2] 0x" + Long.toHexString(address));
                    }
                }
                public void onAttach(com.github.unidbg.arm.backend.UnHook unHook) {}
                public void detach() {}
            }, 0x12037c18L, 0x1203a400L, null);
        }

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
                    long r8 = backend.reg_read(ArmConst.UC_ARM_REG_R8).longValue();
                    int n = ++killHits[0];
                    System.out.println(">>> kill() hit #" + n + " lr=0x" + Long.toHexString(lr)
                            + " r8=0x" + Long.toHexString(r8));
                    try {
                        byte[] r8mem = backend.mem_read(r8, 0x10);
                        StringBuilder sbh = new StringBuilder();
                        for (byte x : r8mem) sbh.append(String.format("%02x", x & 0xff));
                        System.out.println(">>> *r8[0x10]: " + sbh);
                        byte[] flagByte = backend.mem_read(r8 + 0x109, 1);
                        System.out.println(">>> *(r8+0x109) = 0x" + String.format("%02x", flagByte[0] & 0xff));
                    } catch (Throwable t) {
                        System.out.println(">>> r8 mem_read failed: " + t);
                    }
                    // CONFIRMED (session 15 experiment): letting the real `svc #0` execute here
                    // throws `UnsupportedOperationException: SIGKILL pid=X is fatal and does not
                    // return (emulated abort)` - unidbg deliberately models SIGKILL as truly
                    // non-returning (matches real Linux semantics: SIGKILL can't be caught). This
                    // is NOT something any register/memory patch can neutralize post-hoc - the
                    // only real fix is preventing this SVC from ever being reached. Until the
                    // true earlier gate is found (see NEXT-BLOCKER.md session 15 notes), keep the
                    // forced-completion fallback so the harness stays in a working, testable state.
                    if (n >= 2) {
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
            // Diagnostic-only: the `cmp r0,r1` at 0x120370d2 right after the `bl 0x12037878`
            // check that follows our vtable fix. Whichever way this compare goes decides
            // pass/fail for the CALLER of JNI_OnLoad's init - want to see both operands live.
            backend.hook_add_new(new CodeHook() {
                int n;
                public void hook(Backend b, long address, int size, Object user) {
                    if (!jniPhase[0]) return;
                    if (n++ >= 8) return;
                    long r0 = backend.reg_read(ArmConst.UC_ARM_REG_R0).longValue();
                    long r1 = backend.reg_read(ArmConst.UC_ARM_REG_R1).longValue();
                    long r6 = backend.reg_read(ArmConst.UC_ARM_REG_R6).longValue();
                    long sp = backend.reg_read(ArmConst.UC_ARM_REG_SP).longValue();
                    System.out.println(">>> post-vtable cmp @0x120370d2 #" + n
                            + " r0=0x" + Long.toHexString(r0) + " r1=0x" + Long.toHexString(r1)
                            + " r6=0x" + Long.toHexString(r6) + " sp=0x" + Long.toHexString(sp)
                            + " equal=" + (r0 == r1));
                }
                public void onAttach(com.github.unidbg.arm.backend.UnHook unHook) {}
                public void detach() {}
            }, 0x120370d2L, 0x120370d2L, null);
            System.out.println(">>> calling JNI_OnLoad ...");
            long sb = 0x120868f0L, sa = 0x120868e0L;
            backend.mem_write(sa, new byte[]{(byte)sb,(byte)(sb>>8),(byte)(sb>>16),(byte)(sb>>24)});
            backend.mem_write(0x12082340L, new byte[]{(byte)sa,(byte)(sa>>8),(byte)(sa>>16),(byte)(sa>>24)});
            System.out.println(">>> FIX: GOT->singleton->buf");
            // Ground truth from live capstone disasm of DECRYPTED@0x12037090 (session 15):
            //   0x120370ba: ldr r0,[pc,#0x30]; add r0,pc   -> r0 = GOT slot 0x12082340 (verified
            //               by resolving the PC-relative literal: pc(0x120370c0)+lit(0x4b280))
            //   0x120370be: ldr r0,[r0]                    -> r0 = P1 = *(0x12082340)
            //   0x120370c0: ldr r0,[r0]                    -> r0 = P2 = *P1  (the "singleton")
            //   0x120370c2: ldr r1,[r0,#0x40]               -> r1 = *(P2+0x40)  <- vtable slot
            //   0x120370c4: mov r0,r5                       -> r0 is OVERWRITTEN with r5 (JavaVM*)
            //               before the call - "obj(r0)" seen at the blx hook is JavaVM*, a red
            //               herring, NOT the vtable object. P2 is the real object.
            // Harness already sets *0x12082340=sa(0x120868e0), *sa=sb(0x120868f0) - so P1=sa,
            // P2=sb=0x120868f0. Session notes call sb the "classname buffer": ctors populate a
            // string there, not a vtable, so P2+0x40 (=0x12086930) is naturally zero/garbage.
            // Real fix: populate *that exact* slot with a valid function pointer so the call at
            // 0x120370c6 succeeds NATURALLY off the real object chain - no register hijack.
            final long P2 = sb; // 0x120868f0
            final long VTABLE_SLOT_40 = P2 + 0x40; // 0x12086930
            byte[] vtableSlotPtr = new byte[]{
                    (byte) (VTABLE_STUB | 1L), (byte) ((VTABLE_STUB >> 8) & 0xff),
                    (byte) ((VTABLE_STUB >> 16) & 0xff), (byte) ((VTABLE_STUB >> 24) & 0xff)
            };
            try {
                backend.mem_write(VTABLE_SLOT_40, vtableSlotPtr);
                byte[] verify = backend.mem_read(VTABLE_SLOT_40, 4);
                StringBuilder sbh = new StringBuilder();
                for (byte x : verify) sbh.append(String.format("%02x", x & 0xff));
                System.out.println(">>> VTABLE_SLOT_40 @0x" + Long.toHexString(VTABLE_SLOT_40)
                        + " (P2=0x" + Long.toHexString(P2) + ") pre-populated -> " + sbh);
            } catch (Throwable t) {
                System.out.println(">>> VTABLE_SLOT_40 pre-write FAILED: " + t);
            }
            // Second real bug found via live disasm of @0x12037a80:
            //   0x12037a9c: ldr.w r0,[r8]        -> r0 = *(r8) = P2 (same object, r8 = GOT chain)
            //   0x12037aa0: ldr.w r0,[r0,#0x188] -> r0 = *(P2+0x188)   <- a second guard field
            //   0x12037aa4: cmp r0,#0
            //   0x12037aa6: beq #0x12037b40      -> zero here jumps STRAIGHT into the kill() block
            // CTOR-PATCH already writes +0x188 on a DIFFERENT object (0x120923c0, unrelated) -
            // that never touches P2's own +0x188. Populate it directly so this guard passes too.
            final long P2_SLOT_188 = P2 + 0x188; // 0x12086a78
            try {
                backend.mem_write(P2_SLOT_188, new byte[]{
                        (byte) (SCRATCH & 0xff), (byte) ((SCRATCH >> 8) & 0xff),
                        (byte) ((SCRATCH >> 16) & 0xff), (byte) ((SCRATCH >> 24) & 0xff)
                });
                System.out.println(">>> P2_SLOT_188 @0x" + Long.toHexString(P2_SLOT_188)
                        + " pre-populated -> scratch ptr (non-zero)");
            } catch (Throwable t) {
                System.out.println(">>> P2_SLOT_188 pre-write FAILED: " + t);
            }
            // Third guard on the same object, read inside the kill()-block itself:
            //   0x12037b4a: ldr.w r0,[r8]           -> r0 = *(r8) = P2
            //   0x12037b4e: ldrb.w r0,[r0,#0x109]   -> byte at P2+0x109
            //   0x12037b52: cbz r0, #0x12037b78     -> zero skips an extra SIGABRT(0,6) detour
            final long P2_SLOT_109 = P2 + 0x109; // 0x120869f9
            try {
                backend.mem_write(P2_SLOT_109, new byte[]{0});
                System.out.println(">>> P2_SLOT_109 @0x" + Long.toHexString(P2_SLOT_109) + " zeroed");
            } catch (Throwable t) {
                System.out.println(">>> P2_SLOT_109 pre-write FAILED: " + t);
            }
            // FOUND VIA WIDE WALK TRACE (session 15c): the real fork isn't in the vtable/P2
            // object at all. `bl 0x12037878` right after our vtable fix leads to a function
            // whose FIRST real decision is:
            //   0x12037892: ldr r0,[pc,#0x310]; add r0,pc  -> resolves to 0x12092944 (verified
            //               via the same literal-resolution method that correctly found
            //               0x12082340 earlier - both alignments agree here)
            //   0x12037896: ldrb r0,[r0]                    -> FLAG_X byte
            //   0x12037898: cmp r0,#0
            //   0x1203789a: beq.w #0x12037a92                -> ZERO takes the anti-tamper exit
            //               straight to the kill()-region we've been stuck in (confirmed: the
            //               wide walk trace showed execution jump directly from 0x1203789a to
            //               0x12037a92, skipping everything in between)
            // Non-zero instead falls through to `bl 0x1201e378; bl 0x12037c18` and then a real
            // JNIEnv vtable chain - very likely the actual init path leading to the real
            // RegisterNatives calls found earlier at 0x120379d0-0x12037a80.
            final long FLAG_X = 0x12092944L;
            try {
                byte[] before = backend.mem_read(FLAG_X, 1);
                backend.mem_write(FLAG_X, new byte[]{1});
                System.out.println(">>> FLAG_X @0x" + Long.toHexString(FLAG_X)
                        + " was 0x" + String.format("%02x", before[0] & 0xff) + " -> set to 1");
            } catch (Throwable t) {
                System.out.println(">>> FLAG_X pre-write FAILED: " + t);
            }
            // Session 15d: FLAG_X got us into 0x1201e378, which crashed on an unmapped read.
            // Traced it live: `ldr r6,[pc]->0x120868e0(=sa); ldr r0,[r6]->0x120868f0(=P2, our
            // own singleton!); ldr r0,[r0,#0x10]` - P2+0x10 (0x12086900) was never populated by
            // us, held garbage (0x412f6d70), then `ldr r5,[r0,#0x40]` read 0x412f6d70+0x40 =
            // 0x412f6db0 -> unmapped, crash. Fix: point P2+0x10 at P2 itself - P2+0x40 is
            // already our real, valid VTABLE_STUB, so this reuses working infrastructure
            // instead of building a whole new fake object.
            final long P2_SLOT_10 = P2 + 0x10; // 0x12086900
            try {
                backend.mem_write(P2_SLOT_10, new byte[]{
                        (byte) (P2 & 0xff), (byte) ((P2 >> 8) & 0xff),
                        (byte) ((P2 >> 16) & 0xff), (byte) ((P2 >> 24) & 0xff)
                });
                System.out.println(">>> P2_SLOT_10 @0x" + Long.toHexString(P2_SLOT_10)
                        + " -> self-ptr P2 (0x" + Long.toHexString(P2) + ")");
            } catch (Throwable t) {
                System.out.println(">>> P2_SLOT_10 pre-write FAILED: " + t);
            }
            // Session 15d cont: full disasm of 0x1201e378 shows it's a repeated pattern -
            // `ldr r0,[r6]->P2; ldr r0,[r0,#0x10]->P2 (our self-ref); ldr rX,[r0,#OFFSET]; blx
            // rX` - i.e. P2 is being used as a fake "env"/interface object at MANY offsets, not
            // just +0x40. Offsets +0x60 and +0x5c (the latter called 5x, a DeleteLocalRef-style
            // cleanup pattern) are hit right after the one we already fixed. Populate them too
            // with the same real, valid stub - each call just needs to return non-zero so the
            // `cbz/cmp+beq -> fail` checks after each one pass.
            for (long off : new long[]{0x60L, 0x5cL, 0x18L, 0x68L, 0x35cL}) {
                long slot = P2 + off;
                try {
                    backend.mem_write(slot, new byte[]{
                            (byte) (VTABLE_STUB | 1L), (byte) ((VTABLE_STUB >> 8) & 0xff),
                            (byte) ((VTABLE_STUB >> 16) & 0xff), (byte) ((VTABLE_STUB >> 24) & 0xff)
                    });
                    System.out.println(">>> P2+0x" + Long.toHexString(off) + " @0x" + Long.toHexString(slot)
                            + " -> VTABLE_STUB");
                } catch (Throwable t) {
                    System.out.println(">>> P2+0x" + Long.toHexString(off) + " pre-write FAILED: " + t);
                }
            }
            // Session 16: `bl 0x12037c18` fully traced live (register-dump hooks at each link).
            // sb(r9) resolved to 0x120868e0 = OUR OWN `sa` - i.e. the "different GOT slot" from
            // session 15d/15e was a false lead; it's the SAME known chain. *(sb)=P2 (0x120868f0),
            // confirmed identical object, not a second one. The real bug: `obj+0x24` (P2+0x24,
            // 0x12086914) was never populated by us - held 0 (this object's memory starts as
            // legit zero, not garbage, since nothing here happens to write it) - so "that"=0,
            // then `*(that+0x38)` = `*(0x38)` reads 0 off the mapped null page -> fn ptr=0 ->
            // `blx r4` jumps to PC=0x0 -> UC_ERR_FETCH_PROT. This exactly matches the crash
            // (`PC=0x0, LR=0x12037c4f`) reported at the top of session 16's blocker.
            // Fix: P2+0x24 -> P2 (self-ref, same trick as P2+0x10), P2+0x38 -> VTABLE_STUB.
            // (P2+0x44, the call ARG, stays 0 - VTABLE_STUB's `movs r0,#1; bx lr` ignores args.)
            final long P2_SLOT_24 = P2 + 0x24; // 0x12086914
            final long P2_SLOT_38 = P2 + 0x38; // 0x12086928
            try {
                backend.mem_write(P2_SLOT_24, new byte[]{
                        (byte) (P2 & 0xff), (byte) ((P2 >> 8) & 0xff),
                        (byte) ((P2 >> 16) & 0xff), (byte) ((P2 >> 24) & 0xff)
                });
                backend.mem_write(P2_SLOT_38, new byte[]{
                        (byte) (VTABLE_STUB | 1L), (byte) ((VTABLE_STUB >> 8) & 0xff),
                        (byte) ((VTABLE_STUB >> 16) & 0xff), (byte) ((VTABLE_STUB >> 24) & 0xff)
                });
                System.out.println(">>> P2_SLOT_24 @0x" + Long.toHexString(P2_SLOT_24)
                        + " -> self-ptr P2 (0x" + Long.toHexString(P2) + ")");
                System.out.println(">>> P2_SLOT_38 @0x" + Long.toHexString(P2_SLOT_38) + " -> VTABLE_STUB");
            } catch (Throwable t) {
                System.out.println(">>> P2_SLOT_24/38 pre-write FAILED: " + t);
            }
            // Safety net: re-assert EXEC on SCRATCH right before the call. Session 15d saw a
            // `blx r5` to the SAME VTABLE_STUB address that a prior `blx r1` (0x120370c6, same
            // run) executed successfully, fail with FETCH_PROT the second time - re-mapping to
            // rule out a permission/TB-cache quirk before digging further.
            try {
                backend.mem_protect(SCRATCH, 0x1000,
                        UnicornConst.UC_PROT_READ | UnicornConst.UC_PROT_WRITE | UnicornConst.UC_PROT_EXEC);
                byte[] recheck = backend.mem_read(VTABLE_STUB, 4);
                StringBuilder sbh = new StringBuilder();
                for (byte x : recheck) sbh.append(String.format("%02x", x & 0xff));
                System.out.println(">>> SCRATCH re-protected EXEC; VTABLE_STUB bytes still: " + sbh);
            } catch (Throwable t) {
                System.out.println(">>> SCRATCH re-protect FAILED: " + t);
            }
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
            // Post-call decrypted-code dumps for offline capstone disasm (ctors have run by now).
            for (long[] range : new long[][]{{0x12037090L, 0x60}, {0x12037b40L, 0xa0}, {0x12037a80L, 0x60},
                    {0x12037860L, 0x60}, {0x120379d0L, 0xb0}, {0x120370e0L, 0x80},
                    {0x1201e378L, 0x180}, {0x12037c18L, 0x60}, {0x1203a2c0L, 0x140}, {0x12037c50L, 0x180},
                    {0x12037dc8L, 0xa0}, {0x120378a0L, 0x40}, {0x12026d74L, 0x100}}) {
                try {
                    long ea = range[0];
                    int len = (int) range[1];
                    byte[] chunk = backend.mem_read(ea, len);
                    StringBuilder sbh = new StringBuilder();
                    for (byte x : chunk) sbh.append(String.format("%02x", x & 0xff));
                    System.out.println(">>> DECRYPTED@0x" + Long.toHexString(ea) + ": " + sbh);
                } catch (Throwable t) {
                    System.out.println(">>> decrypted dump failed @0x" + Long.toHexString(range[0]) + ": " + t);
                }
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
