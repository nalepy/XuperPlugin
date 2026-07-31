package com.xtv;
import com.github.unidbg.*;
import com.github.unidbg.arm.backend.Backend;
import com.github.unidbg.arm.backend.Unicorn2Factory;
import com.github.unidbg.arm.backend.CodeHook;
import com.github.unidbg.arm.backend.EventMemHook;
import com.github.unidbg.arm.backend.InterruptHook;
import com.github.unidbg.arm.backend.WriteHook;
import com.github.unidbg.file.FileResult;
import com.github.unidbg.file.IOResolver;
import com.github.unidbg.linux.android.*;
import com.github.unidbg.linux.android.dvm.*;
import com.github.unidbg.linux.android.dvm.array.ByteArray;
import com.github.unidbg.linux.file.ByteArrayFileIO;
import com.github.unidbg.memory.Memory;
import com.github.unidbg.pointer.UnidbgPointer;
import com.sun.jna.Pointer;
import unicorn.ArmConst;
import unicorn.UnicornConst;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Proxy;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.util.LinkedHashSet;

public class Unpack extends AbstractJni {

    // Session 23 part 16 BISECT: when true, disable all dynamic (per-hook) mem_protect/
    // mem_map churn on page 0x12038000 — leaving only ONE clean static protect before N.l.
    // Isolates whether OUR inconsistent-size protect calls fragment the page's exec perms.
    static final boolean BISECT_NO_DYNAMIC_PROTECT = true;

    // Session 23 part 17 BISECT: after part 16 cleared our dynamic protect churn, isolate the
    // LONE remaining static pre-N.l protect (mem_protect on a 0x2000 sub-range of the larger
    // libexec segment mapped at module base) — the region-split suspect. When true, that protect
    // is skipped so the page's raw EXEC state at N.l is observed unmodified.
    static final boolean BISECT_NO_STATIC_PROTECT = true;

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
                if ("/data/app/com.android.mgstv-1/base.apk".equals(pathname)) {
                    try {
                        byte[] apk = java.nio.file.Files.readAllBytes(
                            new java.io.File("_assets/live_base.apk").toPath());
                        System.out.println(">>> [IO] providing base.apk: " + apk.length + " bytes");
                        return FileResult.success(new ByteArrayFileIO(oflags, pathname, apk));
                    } catch (Throwable t) {
                        System.out.println(">>> [IO] base.apk FAILED: " + t);
                    }
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
        // Session 20 CORRECTION: live [3a314]/[3a36e] regdumps proved session18's "r4/r5=2 = real
        // count" theory was WRONG. `ldrd r5,r4,[sp,#0x20]` loads r5=*(sp+0x20), r4=*(sp+0x24) - the
        // exact two by-ref out-params (r3=&sp[0x20], r2=&sp[0x24]) our VTABLE_STUB no-ops at
        // 0x12037c46-4c. The DECRYPT LOOP (0x12037db2-dc6) then uses r4 directly as the per-entry
        // POINTER (`mov r0,r4; ...; adds r4,#0x10` each iteration) and r5 as a byte countdown - so
        // sp+0x24/r4 is semantically the REAL ENTRIES BUFFER POINTER the (unimplemented) real
        // callee was supposed to resolve, not a "count". Forcing r4=2 sent the loop walking
        // 0x2,0x12,0x22,... (inside the null-absorb page, all-zero garbage) instead of the real
        // buffer at 0x12240484 - confirmed live this session: entry@0x2/0x12/0x22/... bytes=all-00.
        // Fix: set r4 to the REAL entries buffer base (0x12240484, stable across every session's
        // dumps) and r5 to a real byte-length matching our sl=6 forced iteration count (6*0x10=0x60).
        // Session 20 correction #2: live dump revealed the ldrd's NATURAL (unforced) values are
        // NOT garbage at all - r4(sp+0x24)=0x12240484 (the real entries buffer - exactly right!)
        // and r5(sp+0x20)=0x12201840 (a real in-module pointer, plausible legitimate data, not
        // 0xffffffff garbage as session18 originally claimed). Something upstream of the
        // VTABLE_STUB call already spills the correct values onto these stack slots - our stub
        // "doing nothing" was actually fine; overriding r4/r5 here at all (session18's original
        // r4=2 hack, and this session's first ENTRIES_BUF/0x60 attempt) was unnecessary and only
        // risked clobbering already-correct state. Now PURE TRACE, no override, to see how the
        // real unmodified values flow through the rest of the function.
        final long ENTRIES_BUF = 0x12240484L;
        backend.hook_add_new(new CodeHook() {
            int n;
            public void hook(Backend b, long address, int size, Object user) {
                if (!jniPhase[0]) return;
                long r5 = backend.reg_read(ArmConst.UC_ARM_REG_R5).longValue();
                long r4 = backend.reg_read(ArmConst.UC_ARM_REG_R4).longValue();
                if (n++ < 4) {
                    System.out.println(">>> [20] post-ldrd NATURAL (no override): r5=0x" + Long.toHexString(r5)
                            + " r4=0x" + Long.toHexString(r4) + " (ENTRIES_BUF=0x" + Long.toHexString(ENTRIES_BUF) + ")");
                }
            }
            public void onAttach(com.github.unidbg.arm.backend.UnHook unHook) {}
            public void detach() {}
        }, 0x12037c68L, 0x12037c68L, null);

        // Session 20: investigate the anomaly first (per NEXT-BLOCKER.md session20 next-steps#0)
        // - the 0x1203a314 entry-decrypt hook didn't fire at all in the wip run, despite firing
        // every session19 run with "the same harness state otherwise". Trace the control-flow
        // fork that decides this: the cbz r4 branch at 0x12037c5e (decrypt path 0x12037da4 vs
        // "build string table" path 0x12037cac) happens BEFORE our r4/r5=2 override at 0x12037c68
        // in program order, so it can't be caused by that override directly - but trace it live
        // instead of assuming. Also dump r6 right as it enters the size-rounding formula
        // (0x12037c74) and right before asr.w computes sl (0x12037dac), to see whether our
        // r4/r5=2 override (which happens in between, at 0x12037c68) changed what THAT formula
        // computes - if r6 comes out 0 or negative here, sl could end up <=0 and the loop body
        // (which calls 0x1203a314) would never execute even with the sl-force hook still active.
        backend.hook_add_new(new CodeHook() {
            int n;
            public void hook(Backend b, long address, int size, Object user) {
                if (!jniPhase[0] || n++ >= 4) return;
                long r4 = backend.reg_read(ArmConst.UC_ARM_REG_R4).longValue();
                System.out.println(">>> [20] cbz-r4 @0x12037c5e r4=0x" + Long.toHexString(r4)
                        + (r4 == 0 ? " -> ZERO, taking build-string-table branch (0x12037cac)"
                                   : " -> non-zero, falling through to decrypt path (0x12037da4)"));
            }
            public void onAttach(com.github.unidbg.arm.backend.UnHook unHook) {}
            public void detach() {}
        }, 0x12037c5eL, 0x12037c5eL, null);
        addRegDump(backend, jniPhase, 0x12037c74L, "entry to size-rounding formula (r4=post-override)",
                new int[]{ArmConst.UC_ARM_REG_R4});
        backend.hook_add_new(new CodeHook() {
            int n;
            public void hook(Backend b, long address, int size, Object user) {
                if (!jniPhase[0] || n++ >= 4) return;
                long r6 = backend.reg_read(ArmConst.UC_ARM_REG_R6).longValue();
                System.out.println(">>> [20] pre-asr @0x12037dac r6(pre-shift)=0x" + Long.toHexString(r6)
                        + " (sl will be forced to 6 regardless by the existing 0x12037db0 hook,"
                        + " but this shows what the REAL computed value would have been)");
            }
            public void onAttach(com.github.unidbg.arm.backend.UnHook unHook) {}
            public void detach() {}
        }, 0x12037dacL, 0x12037dacL, null);
        // Unconditional (not gated <N) confirmation that the sl-force hook itself is reached -
        // if this line is MISSING from the log, 0x12037db0 was never executed this run, meaning
        // control flow diverged before it (most likely: the cbz r4 branch above went the OTHER
        // way, into the string-table-building path which never reaches the decrypt loop at all).
        backend.hook_add_new(new CodeHook() {
            public void hook(Backend b, long address, int size, Object user) {
                if (!jniPhase[0]) return;
                System.out.println(">>> [20] REACHED 0x12037db0 (decrypt-loop entry) - confirms decrypt path taken");
            }
            public void onAttach(com.github.unidbg.arm.backend.UnHook unHook) {}
            public void detach() {}
        }, 0x12037db0L, 0x12037db0L, null);

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

        // ===== Session 23 part 18: JNI SVC hooks installed PRE-loadLibrary =====
        // Part 14 installed these AFTER loadLibrary, i.e. after JNI_OnLoad/init had already run,
        // so its "zero hits" was an artifact of late installation (see part-15 audit). Install
        // BEFORE loadLibrary so the whole run — init phase included — is covered. Range-hook
        // base..base+8 (FindClass fires at base+4 per Unpack.java:819, a single-addr hook misses it).
        // Shared counters (jniHits) feed the post-load POSITIVE CONTROL below.
        final java.util.Map<String,int[]> jniHits = new java.util.concurrent.ConcurrentHashMap<>();
        final java.util.Map<String,Long> jniStub = new java.util.concurrent.ConcurrentHashMap<>();
        final boolean INSTALL_JNI_HOOKS_PRELOAD = true;
        if (INSTALL_JNI_HOOKS_PRELOAD) {
            try {
                Pointer envP = vm.getJNIEnv();
                Pointer funcTableP = envP.getPointer(0);
                Object[][] jniTargets = new Object[][]{
                    {"FindClass", 0x18L},
                    {"GetObjectClass", 0x7cL},
                    {"IsInstanceOf", 0x80L},
                    {"GetMethodID", 0x84L},
                    {"NewObject", 0x70L},
                    {"CallObjectMethodV", 0x8cL},
                    {"CallBooleanMethodV", 0x98L},
                    {"CallVoidMethodV", 0xf8L},
                };
                for (Object[] jt : jniTargets) {
                    final String name = (String) jt[0];
                    long offset = (Long) jt[1];
                    Pointer p = funcTableP.getPointer(offset);
                    if (p == null) { System.out.println(">>> [p18 " + name + "] SVC stub null, skip"); continue; }
                    final long addr = ((UnidbgPointer) p).toUIntPeer();
                    jniHits.put(name, new int[1]);
                    jniStub.put(name, addr);
                    backend.hook_add_new(new CodeHook() {
                        public void hook(Backend b, long address, int size, Object user) {
                            int n = ++jniHits.get(name)[0];
                            long pc = b.reg_read(ArmConst.UC_ARM_REG_PC).longValue();
                            long lr = b.reg_read(ArmConst.UC_ARM_REG_LR).longValue();
                            long r0 = b.reg_read(ArmConst.UC_ARM_REG_R0).longValue();
                            long r1 = b.reg_read(ArmConst.UC_ARM_REG_R1).longValue();
                            long r2 = b.reg_read(ArmConst.UC_ARM_REG_R2).longValue();
                            // Always surface any JNI call whose return addr is inside N.l's danger
                            // page (0x12038xxx) — that's the call sequence leading to the crash.
                            boolean nearCrash = (lr >= 0x12038000L && lr < 0x12039000L);
                            if (n <= 8 || nearCrash) {
                                System.out.println(">>> [p18 JNI " + name + "] hit #" + n
                                    + " pc=0x" + Long.toHexString(pc)
                                    + " lr=0x" + Long.toHexString(lr)
                                    + " r0=0x" + Long.toHexString(r0)
                                    + " r1=0x" + Long.toHexString(r1)
                                    + " r2=0x" + Long.toHexString(r2)
                                    + (nearCrash ? "  <<< NEAR 0x12038 CRASH WINDOW" : ""));
                            }
                        }
                        public void onAttach(com.github.unidbg.arm.backend.UnHook unHook) {}
                        public void detach() {}
                    }, addr, addr + 8, null);   // RANGE base..base+8 (the +4 fix)
                    System.out.println(">>> [p18] " + name + " range-hook 0x" + Long.toHexString(addr)
                        + "..0x" + Long.toHexString(addr + 8));
                }
            } catch (Throwable t) {
                System.out.println(">>> [p18] pre-load JNI hook setup FAILED: " + t);
                t.printStackTrace(System.out);
            }
        }

        System.out.println(">>> loading libexec.so ...");
        File so = new File("/tmp/apkx/assets/ijm_lib/armeabi/libexec.so");
        DalvikModule dm = null;
        try {
            dm = vm.loadLibrary(so, true);
            System.out.println(">>> loadLibrary returned base=0x"+Long.toHexString(dm.getModule().base));

            // ===== Session 23 part 18: POSITIVE CONTROL =====
            // JNI_OnLoad just ran, and the init phase is KNOWN to call FindClass (output.log
            // lines 409-413). If our pre-load FindClass hook shows ZERO hits, the hooks are not
            // firing at all and every downstream "no JNI" conclusion is worthless — this is the
            // exact check part 14 skipped. Confirm the hook fires before trusting any result.
            if (INSTALL_JNI_HOOKS_PRELOAD) {
                // Empirical (part 18): this packer's JNI_OnLoad does NOT call FindClass — libexec
                // defers all its FindClass calls to the pre-N.l class-resolution phase (8 calls,
                // lr in 0x120378xx-0x12037axx). So this init-phase snapshot is EXPECTED to read 0.
                // The REAL positive control is the pre-N.l tally further below (nonzero there).
                System.out.println(">>> [p18] JNI hit snapshot at loadLibrary return (init phase, may be 0):");
                for (java.util.Map.Entry<String,int[]> e : jniHits.entrySet()) {
                    if (e.getValue()[0] > 0)
                        System.out.println(">>>   " + e.getKey() + " = " + e.getValue()[0]);
                }
            }
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
            // Session 21: override dispatch literal at 0x12082498 (entry 0 of ARM-mode table
            // at 0x1207b400). The table returns 0xffffffff (not-found sentinel) because entries
            // 0-3 are identical dummy data that don't match the "s/h/e/l/l/\0" format string.
            // Fix: point the literal at an ARM `bx lr` stub so the caller gets its r0 back as
            // a non-negative "match address" instead of the sentinel.
            final long ARM_STUB = SCRATCH + 0xC00; // 0x7f000c00, within the existing RWX page
            try {
                byte[] preRead = backend.mem_read(0x12082498L, 4);
                System.out.print(">>> DISPATCH_LITERAL pre-override @0x12082498: ");
                for (byte x : preRead) System.out.printf("%02x", x & 0xff);
                System.out.println();
                backend.mem_write(ARM_STUB, new byte[]{
                        (byte) 0x1e, (byte) 0xff, (byte) 0x2f, (byte) 0xe1  // ARM bx lr
                });
                byte[] stubAddr = new byte[]{
                        (byte) (ARM_STUB & 0xff), (byte) ((ARM_STUB >> 8) & 0xff),
                        (byte) ((ARM_STUB >> 16) & 0xff), (byte) ((ARM_STUB >> 24) & 0xff)
                };
                // Address must be even (bit 0=0) for ARM mode — dispatch uses `ldr pc,[...]`
                // which reads a full target address, not thumb interwork.
                backend.mem_write(0x12082498L, stubAddr);
                byte[] verify = backend.mem_read(0x12082498L, 4);
                System.out.print(">>> DISPATCH_LITERAL @0x12082498 -> ");
                for (byte x : verify) System.out.printf("%02x", x & 0xff);
                System.out.println(" (ARM_STUB @0x" + Long.toHexString(ARM_STUB) + ")");
            } catch (Throwable t) {
                System.out.println(">>> DISPATCH_OVERRIDE FAILED: " + t);
            }
            // Session 21: the method table at P2+0x18c is all zeros because entry processing
            // loop never populated it. FindClass gets null className -> crash. Hook a broad
            // range covering all known FindClass call sites and inject a valid class name for
            // "s/h/e/l/l/N" whenever r1 (className ptr) is 0 before a blx to env->FindClass.
            final long CLASS_NAME_ADDR = SCRATCH + 0x100;
            try {
                backend.mem_write(CLASS_NAME_ADDR, "s/h/e/l/l/N\0".getBytes());
                System.out.println(">>> CLASS_NAME_STR @0x" + Long.toHexString(CLASS_NAME_ADDR));
            } catch (Throwable t) {
                System.out.println(">>> CLASS_NAME_STR write FAILED: " + t);
            }
            backend.hook_add_new(new CodeHook() {
                int n;
                public void hook(Backend b, long address, int size, Object user) {
                    long r1 = b.reg_read(ArmConst.UC_ARM_REG_R1).longValue();
                    if (r1 == 0L) {
                        b.reg_write(ArmConst.UC_ARM_REG_R1, CLASS_NAME_ADDR);
                        if (n++ < 4) {
                            long r0 = b.reg_read(ArmConst.UC_ARM_REG_R0).longValue();
                            long r2 = b.reg_read(ArmConst.UC_ARM_REG_R2).longValue();
                            System.out.println(">>> [FindClass-fix@" + Long.toHexString(address)
                                + "] r0=0x" + Long.toHexString(r0)
                                + " r1=0->classname r2=0x" + Long.toHexString(r2));
                        }
                    }
                }
                public void onAttach(com.github.unidbg.arm.backend.UnHook unHook) {}
                public void detach() {}
            }, 0x120378a0L, 0x12037b40L, null);
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
            // Note: gap region 0x120378dc-0x120379cf added to find the 2nd FindClass call site (LR=0x1203799b in crash)
            for (long[] range : new long[][]{{0x12037090L, 0x60}, {0x12037b40L, 0xa0}, {0x12037a80L, 0x60},
                    {0x12037860L, 0x60}, {0x120378dcL, 0xf4},  // gap: covers 0x120378dc-0x120379cf
                    {0x120379d0L, 0xb0}, {0x120370e0L, 0x80},
                    {0x1201e378L, 0x180}, {0x12037c18L, 0x60}, {0x1203a2c0L, 0x140}, {0x12037c50L, 0x180},
                    {0x12037dc8L, 0xa0}, {0x120378a0L, 0x40}, {0x12026d74L, 0x100},
                    {0x1207b400L, 0x100}, {0x1207b630L, 0x100}, {0x1203f9b0L, 0x100}, {0x1208ccf0L, 0x40},
                    {0x120823e0L, 0x180},  // dispatch literal pool
                    {0x12082340L, 0x40},   // BEFORE patching: original entries at the pointer-table site we overwrite with SINGLETON: 0x1207b400 entries 0-12 use base 0x12082408+offset, 630 uses 0x12081638+offset; on-disk shows 0x0007b290 but these are ctor-populated at runtime
                    {0x12240484L, 0x100},  // the entries buffer itself — XOR-decrypted but what does it actually contain?
                    {0x1214f3e4L, 0x40},   // dispatch target of 0x1207b400 entry 0 — likely a libc function (strstr?), see if bytes are decodable
                    {0x120381c0L, 0xc0},   // the FETCH_PROT crash site — capture decrypted bytes for disassembly
                    {0x1203a6a0L, 0x30},   // LR=0x1203a6a5 from FETCH_UNMAPPED loop — what's here?
                    {0x1203a570L, 0xd0},   // code between last BLX_R12 trace (0x1203a649) and the blx r5 at 0x1203a6a2
                    {0x1203a070L, 0x200},  // the full unrolled-call-table area covering 0x1203a3d5-0x1203a649
                    {0x12039400L, 0x200},  // b2b code region — 0x12039458 is entry point, capture for disasm
                    {0x12037660L, 0x20},   // dispatch table: LDR R12,[R6,#?]/BLX R12 pair at 0x12037660-2 — see why R12=0
                    {0x1203b570L, 0x40},   // dispatch at 0x1203b577 — LR from every fetch miss; see what branches to decrypted pages
                    }) {
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
                final long SINGLETON = 0x7f002000L;     // data page: code reads [r10] from here as a struct pointer
                final long SINGLETON2 = 0x7f003000L;    // code page: BX LR stub for BLX R12 / BLX r5 trampoline
                backend.mem_map(VTABLE, 0x1000, UnicornConst.UC_PROT_READ | UnicornConst.UC_PROT_WRITE);
                backend.mem_map(SINGLETON, 0x1000, UnicornConst.UC_PROT_READ | UnicornConst.UC_PROT_WRITE | UnicornConst.UC_PROT_EXEC);
                backend.mem_map(SINGLETON2, 0x1000, UnicornConst.UC_PROT_READ | UnicornConst.UC_PROT_WRITE | UnicornConst.UC_PROT_EXEC);

                // Fill the whole SINGLETON page with SAFE pointers (0x7f003000 = SINGLETON2 BX LR stub)
                // so any field read as a function pointer returns a harmless BX LR. This covers:
                //   - struct+0x38 LDR chain (0x1203a6ae)
                //   - struct+offset calls through blx r0/ip/r5
                //   - vtable dispatch through any offset on the struct
                byte[] safePage = new byte[0x1000];
                for (int i = 0; i < safePage.length; i += 4) {
                    safePage[i]   = (byte)0x00;
                    safePage[i+1] = (byte)0x30;
                    safePage[i+2] = (byte)0x00;
                    safePage[i+3] = (byte)0x7f;
                }
                // Override +0x000: ptr to data area at 0x7f002100
                safePage[0] = (byte)0x00; safePage[1] = (byte)0x21;
                safePage[2] = (byte)0x00; safePage[3] = (byte)0x7f;
                // +0x1e2 needs to be 1 for CBNZ skip at 0x1203a6ac; the
                // 0x7f003000 fill below already preserves the rest correctly.
                safePage[0x100 + 0x1e2] = (byte)0x01;
                // Session 23 part 7 (corrected): the crash-site chain is actually
                //   fp = *(0x12082340)        -> SINGLETON (0x7f002000)
                //   r1 = *fp                  -> *(SINGLETON+0)  -> our own override, 0x7f002100
                //   r0 = *(r1 + 0xa4)          -> *(0x7f0021a4)   -> i.e. SINGLETON + 0x1a4
                //   r0 = *r0                   -> reads SINGLETON2's raw BX-LR opcode bytes
                //                                 (0xE12FFF1E) as if it were a pointer value
                //   blx r0                     -> calls 0xE12FFF1E-ish garbage -> free-run crash
                // First attempt patched SINGLETON+0xa4 directly, missing the extra `*fp`
                // hop through the +0x000 override (0x7f002100) — the real double-indirection
                // slot is SINGLETON+0x1a4 (0x7f0021a4), not SINGLETON+0xa4. Point THAT at a
                // small pointer-cell (SINGLETON2+0x100) whose CONTENT is 0x7f003000, so the
                // second dereference resolves back to the real, executable BX-LR stub.
                final long PTR_CELL = SINGLETON2 + 0x100; // 0x7f003100
                final int DBL_INDIR_OFFSET = 0x1a4;
                safePage[DBL_INDIR_OFFSET]   = (byte)(PTR_CELL & 0xff);
                safePage[DBL_INDIR_OFFSET+1] = (byte)((PTR_CELL >> 8) & 0xff);
                safePage[DBL_INDIR_OFFSET+2] = (byte)((PTR_CELL >> 16) & 0xff);
                safePage[DBL_INDIR_OFFSET+3] = (byte)((PTR_CELL >> 24) & 0xff);
                backend.mem_write(SINGLETON, safePage);
                try {
                    backend.mem_write(PTR_CELL, new byte[]{
                        (byte)(SINGLETON2 & 0xff), (byte)((SINGLETON2 >> 8) & 0xff),
                        (byte)((SINGLETON2 >> 16) & 0xff), (byte)((SINGLETON2 >> 24) & 0xff)
                    });
                    System.out.println(">>> SINGLETON+0x1a4 double-indirection fix: points to 0x"
                        + Long.toHexString(PTR_CELL) + " which holds 0x" + Long.toHexString(SINGLETON2));
                } catch (Throwable t) {
                    System.out.println(">>> SINGLETON+0x1a4 double-indirection fix FAILED: " + t);
                }
                // Verify critical bytes
                byte[] check = backend.mem_read(0x7f0022e2L, 1);
                System.out.println(">>> SINGLETON data: byte[0x7f0022e2] = 0x" + String.format("%02x", check[0] & 0xff));
                check = backend.mem_read(0x7f002138L, 4);
                System.out.printf(">>> SINGLETON data: byte[0x7f002138] = 0x%02x%02x%02x%02x%n",
                    check[0]&0xff, check[1]&0xff, check[2]&0xff, check[3]&0xff);
                check = backend.mem_read(SINGLETON, 4);
                System.out.printf(">>> SINGLETON data: byte[0x7f002000] = 0x%02x%02x%02x%02x%n",
                    check[0]&0xff, check[1]&0xff, check[2]&0xff, check[3]&0xff);

                // SINGLETON2 code page: write ARM BX LR stub
                backend.mem_write(SINGLETON2, new byte[]{
                    (byte)0x1E, (byte)0xFF, (byte)0x2F, (byte)0xE1  // BX LR
                });

                // Session 23: single-address hook (cheap, no wide-range corruption risk)
                // counting every dispatch through the fake vtable stub, active for the
                // whole run (not gated by jniPhase). Answers: does N.l/b2b ever actually
                // call through SINGLETON2 at all, and from where (LR = caller site)?
                final int[] singleton2Hits = new int[1];
                backend.hook_add_new(new CodeHook() {
                    public void hook(Backend b, long address, int size, Object user) {
                        int n = ++singleton2Hits[0];
                        if (n <= 30) {
                            long lr = b.reg_read(ArmConst.UC_ARM_REG_LR).longValue();
                            System.out.println(">>> [SINGLETON2 dispatch #" + n + "] called from LR=0x"
                                    + Long.toHexString(lr));
                        }
                        // Session 23 part 8: the raw UC_HOOK_MEM_FETCH_PROT hook (reflection)
                        // proved unreliable (perturbed the crash instead of fixing it). Piggyback
                        // a plain, already-safe mem_protect nudge on this hook instead — it's a
                        // real, frequently-firing checkpoint in the execution timeline, cheap to
                        // re-assert RWX on 0x12038000 every time we pass through it.
                        // Session 23 part 16 BISECT: disabled — this nudge (size 0x2000) plus the
                        // canary (0x1000) plus the LastResort (0x4000) do inconsistent-size
                        // mem_protect on the same base, a known Unicorn perm-fragmentation cause.
                        // Testing whether OUR churn is what strips exec from this page.
                        if (!BISECT_NO_DYNAMIC_PROTECT) {
                            try {
                                b.mem_protect(0x12038000L, 0x2000,
                                    UnicornConst.UC_PROT_READ | UnicornConst.UC_PROT_WRITE | UnicornConst.UC_PROT_EXEC);
                            } catch (Throwable t) { /* best-effort */ }
                        }
                    }
                    public void onAttach(com.github.unidbg.arm.backend.UnHook unHook) {}
                    public void detach() {}
                }, SINGLETON2, SINGLETON2, null);
                System.out.println(">>> SINGLETON2 dispatch-count hook installed");

                // Session 23 part 12: cheap, single-address entry counter at 0x120381c0 (the
                // function whose re-entry crashes with UC_ERR_FETCH_PROT once its page loses
                // EXEC). A CodeHook only fires on a SUCCESSFUL fetch, so this can only ever
                // count successful entries — but that's exactly what we need: hard data on how
                // many times this function is entered successfully, and each entry's caller
                // (LR), before whichever entry fails. If it turns out to be called more than
                // once successfully, the caller pattern here tells us where the fix belongs.
                final int[] fn381c0Hits = new int[1];
                try {
                    backend.hook_add_new(new CodeHook() {
                        public void hook(Backend b, long address, int size, Object user) {
                            int n = ++fn381c0Hits[0];
                            long lr = b.reg_read(ArmConst.UC_ARM_REG_LR).longValue();
                            System.out.println(">>> [fn@0x120381c0 entry #" + n + "] called from LR=0x"
                                    + Long.toHexString(lr));
                        }
                        public void onAttach(com.github.unidbg.arm.backend.UnHook unHook) {}
                        public void detach() {}
                    }, 0x120381c0L, 0x120381c0L, null);
                    System.out.println(">>> fn@0x120381c0 entry-counter hook installed");
                } catch (Throwable t) {
                    System.out.println(">>> fn@0x120381c0 entry-counter hook FAILED: " + t);
                }

                // Session 23 part 13: pivot to host-level (unidbg source-grounded) diagnosis.
                // Fetched unidbg-android 0.9.10-SNAPSHOT source (DalvikVM.java) directly from
                // GitHub. NewObjectV's real implementation:
                //   DvmClass dvmClass = classMap.get(clazz.toIntPeer());
                //   DvmMethod dvmMethod = dvmClass == null ? null : dvmClass.getMethod(jmethodID.toIntPeer());
                //   if (dvmMethod == null) { throw new BackendException(); }
                // The old session18/21 comment ("NewObjectV triggers re-entry... self-nukes the
                // page with mprotect") lines up exactly with this: if our SINGLETON-based fake
                // dispatch ever feeds a garbage/unregistered "clazz" pointer into NewObjectV,
                // this throws a BackendException from INSIDE the SVC handler (Java code, not
                // guest ARM) — a strong candidate for whatever unwind/cleanup path in unidbg's
                // own Backend then does the mprotect. Hook the real _NewObjectV SVC stub
                // directly (found via vm.getJNIEnv() -> functions table offset 0x74, matching
                // DalvikVM's own `impl.setPointer(0x74, _NewObjectV)`) to see clazz/jmethodID
                // for every call before any crash — ground truth instead of more guessing.
                try {
                    Pointer env = vm.getJNIEnv();
                    Pointer funcTable = env.getPointer(0);
                    Pointer newObjectVPtr = funcTable.getPointer(0x74);
                    long newObjectVAddr = ((UnidbgPointer) newObjectVPtr).toUIntPeer();
                    System.out.println(">>> _NewObjectV SVC stub resolved at 0x" + Long.toHexString(newObjectVAddr));
                    final int[] newObjectVHits = new int[1];
                    backend.hook_add_new(new CodeHook() {
                        public void hook(Backend b, long address, int size, Object user) {
                            int n = ++newObjectVHits[0];
                            long r1 = b.reg_read(ArmConst.UC_ARM_REG_R1).longValue(); // clazz
                            long r2 = b.reg_read(ArmConst.UC_ARM_REG_R2).longValue(); // jmethodID
                            long r3 = b.reg_read(ArmConst.UC_ARM_REG_R3).longValue(); // va_list
                            long lr = b.reg_read(ArmConst.UC_ARM_REG_LR).longValue();
                            System.out.println(">>> [_NewObjectV SVC] hit #" + n
                                    + " clazz=0x" + Long.toHexString(r1)
                                    + " jmethodID=0x" + Long.toHexString(r2)
                                    + " va_list=0x" + Long.toHexString(r3)
                                    + " LR=0x" + Long.toHexString(lr));
                        }
                        public void onAttach(com.github.unidbg.arm.backend.UnHook unHook) {}
                        public void detach() {}
                    }, newObjectVAddr, newObjectVAddr, null);
                    System.out.println(">>> _NewObjectV SVC hook installed");
                } catch (Throwable t) {
                    System.out.println(">>> _NewObjectV SVC hook FAILED: " + t);
                    t.printStackTrace(System.out);
                }

                // Session 23 part 14 (SUPERSEDED by part 18): this installed the same 8 JNI hooks
                // but AFTER loadLibrary, so it missed the init phase and mis-reported "zero hits".
                // Part 18 moved a corrected, positive-controlled version BEFORE loadLibrary. This
                // block is gated off to avoid double-installing on the same stub addresses.
                if (!INSTALL_JNI_HOOKS_PRELOAD) try {
                    Pointer env2 = vm.getJNIEnv();
                    Pointer funcTable2 = env2.getPointer(0);
                    Object[][] jniTargets = new Object[][]{
                        {"FindClass", 0x18L},
                        {"GetObjectClass", 0x7cL},
                        {"IsInstanceOf", 0x80L},
                        {"GetMethodID", 0x84L},
                        {"NewObject", 0x70L},
                        {"CallObjectMethodV", 0x8cL},
                        {"CallBooleanMethodV", 0x98L},
                        {"CallVoidMethodV", 0xf8L},
                    };
                    for (Object[] jt : jniTargets) {
                        final String name = (String) jt[0];
                        long offset = (Long) jt[1];
                        Pointer p = funcTable2.getPointer(offset);
                        if (p == null) {
                            System.out.println(">>> [" + name + "] SVC stub is null, skipping");
                            continue;
                        }
                        long addr = ((UnidbgPointer) p).toUIntPeer();
                        final int[] hits = new int[1];
                        backend.hook_add_new(new CodeHook() {
                            public void hook(Backend b, long address, int size, Object user) {
                                int n = ++hits[0];
                                if (n <= 5) {
                                    long r1 = b.reg_read(ArmConst.UC_ARM_REG_R1).longValue();
                                    long r2 = b.reg_read(ArmConst.UC_ARM_REG_R2).longValue();
                                    long lr = b.reg_read(ArmConst.UC_ARM_REG_LR).longValue();
                                    System.out.println(">>> [" + name + " SVC] hit #" + n
                                            + " r1=0x" + Long.toHexString(r1)
                                            + " r2=0x" + Long.toHexString(r2)
                                            + " LR=0x" + Long.toHexString(lr));
                                }
                            }
                            public void onAttach(com.github.unidbg.arm.backend.UnHook unHook) {}
                            public void detach() {}
                        }, addr, addr, null);
                        System.out.println(">>> [" + name + "] SVC hook installed at 0x" + Long.toHexString(addr));
                    }
                } catch (Throwable t) {
                    System.out.println(">>> multi-JNI SVC hook setup FAILED: " + t);
                    t.printStackTrace(System.out);
                }

                // Write the SINGLETON address into the binary's pointer table so code that loads
                // from 0x12082340 gets a struct pointer (not the BX LR stub address).
                byte[] singDataPtr = new byte[] {
                    (byte)(SINGLETON & 0xff), (byte)((SINGLETON>>8)&0xff),
                    (byte)((SINGLETON>>16)&0xff), (byte)((SINGLETON>>24)&0xff)
                };
                backend.mem_write(0x12082340L, singDataPtr);
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

                // Session 21: the page at 0x120381c0 was made non-exec by secondary loader.
                // Try to re-protect it + dump its bytes for analysis before N.l.
                try {
                    byte[] pageBytes = backend.mem_read(0x12038000L, 0x2000);
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < 64 && i < pageBytes.length; i++)
                        sb.append(String.format("%02x", pageBytes[i] & 0xff));
                    System.out.println(">>> PAGE@0x12038000 before N.l: " + sb);
                    if (!BISECT_NO_STATIC_PROTECT) {
                        backend.mem_protect(0x120381c0L & ~0xfff, 0x2000,
                                UnicornConst.UC_PROT_READ | UnicornConst.UC_PROT_WRITE | UnicornConst.UC_PROT_EXEC);
                        System.out.println(">>> PAGE@0x12038000 re-protected EXEC before N.l");
                    } else {
                        System.out.println(">>> [BISECT p17] static pre-N.l protect DISABLED — observing raw EXEC state");
                    }
                    // Session 23 part 7: dump the window around 0x12038273 (offset 0x200-0x2ff)
                    // as one hex blob so it can be disassembled offline with capstone, without
                    // needing another remote round-trip. This is the read site misinterpreting
                    // our SINGLETON2 stub bytes as data (session 23 part 6 characterization).
                    StringBuilder win = new StringBuilder();
                    for (int i = 0x200; i < 0x300 && i < pageBytes.length; i++)
                        win.append(String.format("%02x", pageBytes[i] & 0xff));
                    System.out.println(">>> PAGE@0x12038000 window[0x200:0x300]: " + win);
                    // Session 23 part 9: dispatch #3's LR (0x12038273) confirms it's the now-fixed
                    // double-indirection call site returning normally. Tracing forward from there:
                    // ...bl 0x1203a7d4; cmp r0,#0; beq 0x120381ea -- a conditional branch BACK to
                    // 0x120381ea, right next to the 0x120381c1 crash site. Need that earlier region
                    // (below our 0x200 window start) to see what's actually there.
                    StringBuilder win2 = new StringBuilder();
                    for (int i = 0x180; i < 0x200 && i < pageBytes.length; i++)
                        win2.append(String.format("%02x", pageBytes[i] & 0xff));
                    System.out.println(">>> PAGE@0x12038000 window[0x180:0x200]: " + win2);
                } catch (Throwable t) {
                    System.out.println(">>> PAGE@0x12038000 pre-protect failed: " + t);
                }

                // Session 23 part 10: dump the 3 remaining unexplored call targets from the
                // 0x120381c0 function body (bl 0x1203b520, bl 0x1203a760, bl 0x1203a7d4) so
                // they can be disassembled offline in parallel — any of the three could be
                // the actual mprotect/self-nuke trigger session 18/21 partially patched.
                for (long fnAddr : new long[]{0x1203b520L, 0x1203a760L, 0x1203a7d4L}) {
                    try {
                        byte[] fnBytes = backend.mem_read(fnAddr, 0x300);
                        StringBuilder fsb = new StringBuilder();
                        for (byte x : fnBytes) fsb.append(String.format("%02x", x & 0xff));
                        System.out.println(">>> FN@0x" + Long.toHexString(fnAddr) + " window[0:0x300]: " + fsb);
                    } catch (Throwable t) {
                        System.out.println(">>> FN@0x" + Long.toHexString(fnAddr) + " dump FAILED: " + t);
                    }
                }

                // Session 23 part 11: dump the next tier of candidates — the veneer-table
                // trampoline cluster (16-byte PIC stubs, session 22) and the still-unexplored
                // internal callees fed by the XOR-decrypted buffer, to keep chasing the
                // mprotect self-nuke trigger.
                for (long fnAddr : new long[]{
                        0x1207b2d0L, 0x1207b2e0L, 0x1207b310L, 0x1207b640L,
                        0x1207ba70L, 0x1207ba80L, 0x1207ba90L}) {
                    try {
                        byte[] fnBytes = backend.mem_read(fnAddr, 0x40);
                        StringBuilder fsb = new StringBuilder();
                        for (byte x : fnBytes) fsb.append(String.format("%02x", x & 0xff));
                        System.out.println(">>> VENEER@0x" + Long.toHexString(fnAddr) + " window[0:0x40]: " + fsb);
                    } catch (Throwable t) {
                        System.out.println(">>> VENEER@0x" + Long.toHexString(fnAddr) + " dump FAILED: " + t);
                    }
                }
                for (long fnAddr : new long[]{
                        0x12025230L, 0x12025484L, 0x12037488L,
                        0x120375fcL, 0x1203b684L, 0x1203b6f8L}) {
                    try {
                        byte[] fnBytes = backend.mem_read(fnAddr, 0x300);
                        StringBuilder fsb = new StringBuilder();
                        for (byte x : fnBytes) fsb.append(String.format("%02x", x & 0xff));
                        System.out.println(">>> FN2@0x" + Long.toHexString(fnAddr) + " window[0:0x300]: " + fsb);
                    } catch (Throwable t) {
                        System.out.println(">>> FN2@0x" + Long.toHexString(fnAddr) + " dump FAILED: " + t);
                    }
                }

                // Dump bytes around 0x12038270 — critical site where walk2 reaches but safety
                // hook doesn't fire (meaning r0 != 0 there). Disassemble to understand why.
                try {
                    byte[] code270 = backend.mem_read(0x12038270L, 32);
                    StringBuilder sb270 = new StringBuilder(">>> CODE@0x12038270[0..+32]: ");
                    for (byte x : code270) sb270.append(String.format("%02x", x & 0xff));
                    System.out.println(sb270);
                } catch (Throwable t) {
                    System.out.println(">>> CODE@0x12038270 dump FAILED: " + t);
                }

                // Patch the `blx r3` at 0x12038226 to `nop; nop`. The call goes through
                // env->functions[25] (NewObjectV in JNI table) and triggers re-entry to
                // 0x120381c0 via a path that self-nukes the page with mprotect. We verified
                // SVC-based InterruptHook doesn't fire because unidbg's ARM32SyscallHandler
                // wraps the mprotect from host code, not from a unicorn SVC. Skipping the
                // call lets us see what the code does next (it'll likely fail on null data
                // or unpopulated structure, but at least we'll get a different error).
                try {
                    byte[] pre = backend.mem_read(0x12038226L, 2);
                    System.out.print(">>> BLX_R3 pre-patch @0x12038226: ");
                    for (byte x : pre) System.out.printf("%02x", x & 0xff);
                    backend.mem_write(0x12038226L, new byte[]{0x00, (byte) 0xbf});
                    byte[] post = backend.mem_read(0x12038226L, 2);
                    System.out.print(" -> post: ");
                    for (byte x : post) System.out.printf("%02x", x & 0xff);
                    System.out.println(" (nop)");
                } catch (Throwable t) {
                    System.out.println(">>> BLX_R3 patch FAILED: " + t);
                }

                // Re-NOP the `bl #0x1201e6dc` at 0x12038274 — it triggers a memory-scan loop in libexec.so
                // (LR=0x1201e725) that walks 0x1000+ upward through unmapped pages indefinitely.
                // With the blanket SINGLETON fill, the subsequent code (bl #0x1203a760 etc.) should
                // work correctly since all struct fields return safe pointers.
                try {
                    byte[] pre274 = backend.mem_read(0x12038274L, 4);
                    System.out.print(">>> BL_1e6dc pre-patch @0x12038274: ");
                    for (byte x : pre274) System.out.printf("%02x", x & 0xff);
                    backend.mem_write(0x12038274L, new byte[]{0x00, (byte)0xbf, 0x00, (byte)0xbf});
                    byte[] post274 = backend.mem_read(0x12038274L, 4);
                    System.out.print(" -> post: ");
                    for (byte x : post274) System.out.printf("%02x", x & 0xff);
                    System.out.println(" (two nops)");
                } catch (Throwable t) {
                    System.out.println(">>> BL_1e6dc patch FAILED: " + t);
                }
                // The code at 0x1203827c does `str.w r0, [fp]` which zeros the struct base pointer
                // at [0x7f002000] (fp=sl=r10=0x7f002000). Re-write it after each such call.
                try {
                    backend.hook_add_new(new CodeHook() {
                        public void hook(Backend b, long address, int size, Object user) {
                            // Re-write [0x7f002000] = ptr to 0x7f002100
                            b.mem_write(0x7f002000L, new byte[]{
                                (byte)0x00, (byte)0x21, (byte)0x00, (byte)0x7f
                            });
                        }
                        public void onAttach(com.github.unidbg.arm.backend.UnHook unHook) {}
                        public void detach() {}
                    }, 0x1203827eL, 0x1203827eL, null);
                    System.out.println(">>> SINGLETON base-pointer restore hook at 0x1203827e");
                } catch (Throwable t) {
                    System.out.println(">>> SINGLETON restore hook FAILED: " + t);
                }

                // Scan decrypted code region for ALL Thumb SVC instructions (0xdf??) and hook each
                // one. Thumb SVC encoding (little-endian): bytes = [imm8, 0xdf]. We previously
                // only caught svc #0 (00 df); now catch any immediate value.
                {
                    int nSvc = 0;
                    try {
                        byte[] region = backend.mem_read(0x12037000L, 0x5000);
                        for (int i = 0; i + 1 < region.length; i += 2) {
                            int b0 = region[i] & 0xff;
                            int b1 = region[i+1] & 0xff;
                            if (b1 == 0xdf) { // Thumb SVC: [imm8, 0xdf]
                                final long svcAddr = 0x12037000L + i;
                                backend.hook_add_new(new CodeHook() {
                                    int hits;
                                    public void hook(Backend b, long address, int size, Object user) {
                                        long r7 = b.reg_read(ArmConst.UC_ARM_REG_R7).longValue();
                                        long r0 = b.reg_read(ArmConst.UC_ARM_REG_R0).longValue();
                                        long r1 = b.reg_read(ArmConst.UC_ARM_REG_R1).longValue();
                                        long r2 = b.reg_read(ArmConst.UC_ARM_REG_R2).longValue();
                                        if (hits++ < 16) {
                                            System.out.println(">>> [SVC@" + Long.toHexString(svcAddr)
                                                + " svc#" + b0 + "] r7=" + r7
                                                + " r0=0x" + Long.toHexString(r0)
                                                + " r1=0x" + Long.toHexString(r1)
                                                + " r2=0x" + Long.toHexString(r2));
                                        }
                                    }
                                    public void onAttach(com.github.unidbg.arm.backend.UnHook unHook) {}
                                    public void detach() {}
                                }, svcAddr, svcAddr, null);
                                nSvc++;
                            }
                        }
                    } catch (Throwable t) {
                        System.out.println(">>> SVC scan failed: " + t);
                    }
                    System.out.println(">>> SVC scan: " + nSvc + " SVC hooks installed");
                }

                // ===== Session 23 part 19: syscall interceptor (InterruptHook) =====
                // Location/timing-agnostic catch for the mprotect that strips EXEC from 0x12038000.
                // A guest `svc` raises a Unicorn interrupt; unidbg's SyscallHandler dispatches it.
                // We fire on the same interrupt and read the ARM-EABI syscall regs: r7=NR, r0=addr,
                // r1=len, r2=prot. Unlike the static SVC-byte scan above (which only hooks svc bytes
                // already present in 0x12037000-0x1203c000 at scan time), this catches svc issued
                // from anywhere and from code decrypted INTO memory DURING N.l — the un-instrumented
                // path from part 18's verdict. PROT_EXEC=4; mprotect=125, mmap2=192, munmap=91 (ARM32).
                final boolean INSTALL_SYSCALL_HOOK = true;
                final int[] syscallSeen = {0};
                final int[] intAll = {0};   // POSITIVE CONTROL: total interrupt fires (any NR)
                if (INSTALL_SYSCALL_HOOK) {
                    try {
                        backend.hook_add_new(new com.github.unidbg.arm.backend.InterruptHook() {
                            public void hook(Backend b, int intno, int swi, Object user) {
                                try {
                                    long nr = b.reg_read(ArmConst.UC_ARM_REG_R7).longValue();
                                    // POSITIVE CONTROL: prove this hook actually fires on guest svc.
                                    // If total stays 0 while the run makes syscalls, the InterruptHook
                                    // path is not wired to svc in this backend and any "0 mprotect"
                                    // result is meaningless (must subclass SyscallHandler instead).
                                    int t = ++intAll[0];
                                    if (t <= 8) {
                                        System.out.println(">>> [p19 INT-CTRL] interrupt #" + t
                                            + " intno=" + intno + " swi=" + swi + " r7(NR)=" + nr);
                                    }
                                    if (nr != 125 && nr != 192 && nr != 91) return; // mem syscalls only
                                    long a0 = b.reg_read(ArmConst.UC_ARM_REG_R0).longValue() & 0xffffffffL;
                                    long a1 = b.reg_read(ArmConst.UC_ARM_REG_R1).longValue() & 0xffffffffL;
                                    long a2 = b.reg_read(ArmConst.UC_ARM_REG_R2).longValue() & 0xffffffffL;
                                    long pc = b.reg_read(ArmConst.UC_ARM_REG_PC).longValue() & 0xffffffffL;
                                    String name = nr == 125 ? "mprotect" : nr == 192 ? "mmap2" : "munmap";
                                    boolean coversPage = (a0 <= 0x12038000L && 0x12038000L < a0 + a1);
                                    boolean stripsExec = (nr == 125) && ((a2 & 0x4L) == 0);
                                    if (syscallSeen[0]++ < 40 || coversPage) {
                                        System.out.println(">>> [p19 SYSCALL " + name + "] pc=0x" + Long.toHexString(pc)
                                            + " addr=0x" + Long.toHexString(a0)
                                            + " len=0x" + Long.toHexString(a1)
                                            + " prot=0x" + Long.toHexString(a2)
                                            + (coversPage ? "  <<< COVERS 0x12038000" : "")
                                            + (coversPage && stripsExec ? "  *** STRIPS EXEC — THIS IS THE BUG ***" : ""));
                                    }
                                } catch (Throwable t) { /* best-effort, never perturb the run */ }
                            }
                            public void onAttach(com.github.unidbg.arm.backend.UnHook unHook) {}
                            public void detach() {}
                        }, null);
                        System.out.println(">>> [p19] syscall InterruptHook installed (mprotect/mmap2/munmap)");
                    } catch (Throwable t) {
                        System.out.println(">>> [p19] syscall InterruptHook FAILED: " + t);
                        t.printStackTrace(System.out);
                    }
                }
                // Pre-map a large low-memory range for the libexec memory scan loop (libexec 0x3b72d)
                // and the N.l decrypted-code output region (~0x11000000-0x12000000). The scan walks
                // linearly from 0x1000 upward through every page. On real hardware the linker data
                // structures would cause the scan to terminate; here we give it enough zero-filled
                // pages so the iteration finishes before the cap.
                // Note: 1MB chunks at 0x12000000 collide with libexec.so's mapping, causing unicorn
                // to reject the entire 1MB. Using 64KB fallback for any failed 1MB attempt ensures
                // pages just below libexec are still covered.
                // No pre-map — maps pages on demand via EventMemHook at 4KB granularity.
                // This avoids page_collection_lock_arm crashes from 1MB chunks.
                // 4KB on-demand mappings do eventually crash at ~2000+ count, so keep FETCH limit moderate.
                System.out.println(">>> Pre-map DISABLED — using EventMemHook on-demand");

                // Safety net: EventMemHook for UNMAPPED access (READ/WRITE/FETCH). Catches:
                //   * WRITE_UNMAPPED — N.l decrypt writes to pages above 0x10000000; with the
                //     extended pre-map (0x1000-0x11000000) these should land, but this hook
                //     catches anything that slips past.
                //   * FETCH_UNMAPPED — code branches to decrypted pages; if the write missed
                //     (no WRITE_UNMAPPED fell through), we still map and let it fail gracefully.
                final int[] fetchCount = {0};
                final long[] minFetchAddr = {Long.MAX_VALUE};
                final long[] maxFetchAddr = {0};
                final int FETCH_CAP = 1500;
                // Session 23 part 6: the 0x7b290 table-walk and the NEW runaway found at
                // fetch #256+ (LR frozen at 0x12038273 for ~1245 straight fetches, climbing
                // 0xe13fd000->0xe18d9000 one page at a time) are the same bug class — PC
                // branches to a garbage/uninitialized pointer, lands in unmapped memory we
                // auto-map as zero, and zero bytes decode as ARM `ANDEQ r0,r0,r0` (no-op), so
                // PC free-runs straight through every subsequent page forever. Real code
                // would eventually branch/call/return and change LR; a free-run doesn't touch
                // LR at all. So: N consecutive Fetch-unmapped events with an IDENTICAL LR is a
                // reliable, address-agnostic runaway signal — generalizes beyond hardcoding
                // each specific bad address (0x7b290 was one instance; there may be others).
                final long[] lastLR = {-1L};
                final int[] sameLRStreak = {0};
                final boolean[] runawayBounced = {false};
                final int RUNAWAY_STREAK_THRESHOLD = 8;
                backend.hook_add_new(new EventMemHook() {
                    public boolean hook(Backend b, long address, int size, long value, Object user,
                            EventMemHook.UnmappedType type) {
                        if (type == EventMemHook.UnmappedType.Fetch) {
                            if (address < minFetchAddr[0]) minFetchAddr[0] = address;
                            if (address > maxFetchAddr[0]) maxFetchAddr[0] = address;

                            long lrNow0 = b.reg_read(ArmConst.UC_ARM_REG_LR).longValue();
                            if (lrNow0 == lastLR[0]) {
                                sameLRStreak[0]++;
                            } else {
                                lastLR[0] = lrNow0;
                                sameLRStreak[0] = 1;
                                runawayBounced[0] = false;
                            }
                            if (sameLRStreak[0] >= RUNAWAY_STREAK_THRESHOLD) {
                                if (!runawayBounced[0]) {
                                    System.out.println(">>> [runaway-detect] " + sameLRStreak[0]
                                        + " consecutive fetches with frozen LR=0x" + Long.toHexString(lrNow0)
                                        + " at addr=0x" + Long.toHexString(address)
                                        + " — bouncing PC back to LR instead of mapping further");
                                    runawayBounced[0] = true;
                                }
                                b.reg_write(ArmConst.UC_ARM_REG_PC, lrNow0);
                                return true;
                            }

                            // Session 23: the cap of 50 was cutting off a walk (0x7b290-0xad000,
                            // ~50 pages) mid-stream, causing UC_ERR_FETCH_UNMAPPED at 0x120381c1
                            // which unidbg then reports as N.l returning a garbage -1 — NOT a real
                            // decrypt-failure sentinel. Raised to 300, still well under the ~2000
                            // on-demand-mapping count that was found to corrupt Unicorn internals.
                            if (fetchCount[0]++ >= FETCH_CAP) {
                                long lrAtCap = b.reg_read(ArmConst.UC_ARM_REG_LR).longValue();
                                long r0AtCap = b.reg_read(ArmConst.UC_ARM_REG_R0).longValue();
                                long r1AtCap = b.reg_read(ArmConst.UC_ARM_REG_R1).longValue();
                                System.out.println(">>> [EventMemHook] FETCH LIMIT REACHED (" + fetchCount[0] + "), NOT mapping"
                                    + " requestedAddr=0x" + Long.toHexString(address)
                                    + " seenRange=[0x" + Long.toHexString(minFetchAddr[0])
                                    + ",0x" + Long.toHexString(maxFetchAddr[0]) + "]"
                                    + " LR=0x" + Long.toHexString(lrAtCap)
                                    + " r0=0x" + Long.toHexString(r0AtCap)
                                    + " r1=0x" + Long.toHexString(r1AtCap));
                                return false;
                            }
                        }
                        String tag = type == EventMemHook.UnmappedType.Fetch ? "FETCH"
                                : type == EventMemHook.UnmappedType.Write ? "WRITE"
                                : "READ";
                        // Fast 4KB mapping — no verbose logging to avoid slowing down the scan
                        long pageStart = address & ~0xfffL;
                        // Session 23: log every FETCH address near the cap (last 30) to see
                        // exactly what range is being walked right before the crash, plus the
                        // usual sparse sampling for the full run.
                        if ((fetchCount[0] & 0xff) == 0 || fetchCount[0] >= FETCH_CAP - 30) {
                            long lrNow = b.reg_read(ArmConst.UC_ARM_REG_LR).longValue();
                            System.out.println(">>> [EventMemHook] " + tag + " #" + fetchCount[0]
                                + " mapping 4KB at 0x" + Long.toHexString(pageStart)
                                + " (raw addr 0x" + Long.toHexString(address) + ") LR=0x" + Long.toHexString(lrNow));
                        }
                        try {
                            b.mem_map(pageStart, 0x1000L,
                                UnicornConst.UC_PROT_READ | UnicornConst.UC_PROT_WRITE | UnicornConst.UC_PROT_EXEC);
                        } catch (Throwable t) {
                            // page already mapped or overlapping; skip
                        }
                        // Session 23: canary read never failed across 300 fetches (every 10th
                        // checked) yet N.l still dies with UC_ERR_FETCH_UNMAPPED at 0x120381c1
                        // (inside that same page) right when the FETCH LIMIT trips. Data reads
                        // succeeding while an instruction fetch fails points at something
                        // un-mapping/un-EXEC'ing this page specifically for code fetch, possibly
                        // repeatedly (session 21 saw "secondary loader" do this once already).
                        // Force it back to mapped+RWX continuously, not just once before N.l.
                        // Session 23 part 16 BISECT: gated — this canary re-map (0x1000) is one
                        // of the three inconsistent-size protect calls under suspicion.
                        if (!BISECT_NO_DYNAMIC_PROTECT && fetchCount[0] % 10 == 0) {
                            try {
                                b.mem_map(0x12038000L, 0x1000L,
                                    UnicornConst.UC_PROT_READ | UnicornConst.UC_PROT_WRITE | UnicornConst.UC_PROT_EXEC);
                            } catch (Throwable t) { /* already mapped, expected */ }
                            try {
                                b.mem_protect(0x12038000L, 0x1000L,
                                    UnicornConst.UC_PROT_READ | UnicornConst.UC_PROT_WRITE | UnicornConst.UC_PROT_EXEC);
                            } catch (Throwable t) {
                                System.out.println(">>> [canary] re-protect 0x12038000 FAILED at fetchCount="
                                        + fetchCount[0] + ": " + t);
                            }
                        }
                        return true;
                    }
                    public void onAttach(com.github.unidbg.arm.backend.UnHook unHook) {}
                    public void detach() {}
                }, 112, null); // UC_HOOK_MEM_UNMAPPED mask: READ(16)|WRITE(32)|FETCH(64) = 112

                // Session 23 part 4: 0x7b290 is the exact stale on-disk default from the
                // ctor-skipped pointer table at 0x12082340 (session 22). Something is reading
                // that unpopulated slot and branching to it, landing PC in zero-filled memory
                // where it free-runs (0x00000000 decodes as ARM `ANDEQ r0,r0,r0`, a no-op) until
                // our FETCH cap cuts it off ~1500+ pages later. Catch the ORIGIN: fire once,
                // dump the caller (LR) and registers, then bounce straight back instead of
                // letting it wander into the zero wasteland at all.
                final int[] runawayHits = {0};
                try {
                    backend.hook_add_new(new CodeHook() {
                        public void hook(Backend b, long address, int size, Object user) {
                            int n = ++runawayHits[0];
                            long lr = b.reg_read(ArmConst.UC_ARM_REG_LR).longValue();
                            if (n <= 10 || n % 1000 == 0) {
                                long r0 = b.reg_read(ArmConst.UC_ARM_REG_R0).longValue();
                                long r1 = b.reg_read(ArmConst.UC_ARM_REG_R1).longValue();
                                long r2 = b.reg_read(ArmConst.UC_ARM_REG_R2).longValue();
                                long r3 = b.reg_read(ArmConst.UC_ARM_REG_R3).longValue();
                                System.out.println(">>> [runaway-origin@0x7b290] hit #" + n + " LR=0x" + Long.toHexString(lr)
                                        + " r0=0x" + Long.toHexString(r0) + " r1=0x" + Long.toHexString(r1)
                                        + " r2=0x" + Long.toHexString(r2) + " r3=0x" + Long.toHexString(r3));
                            }
                            // Bounce straight back to the caller (session 23 part 4 learned a
                            // one-shot bounce just gets immediately re-entered by the same
                            // caller retrying the same garbage-pointer call, then free-runs for
                            // real on the second, unguarded hit). Bound the retries though —
                            // if this is a genuine infinite tight loop rather than a bounded
                            // retry-with-incrementing-candidate, give up after 5000 and fall
                            // back to letting it proceed normally (known crash, but informative).
                            if (n <= 5000) {
                                b.reg_write(ArmConst.UC_ARM_REG_PC, lr);
                            } else if (n == 5001) {
                                System.out.println(">>> [runaway-origin@0x7b290] giving up after 5000 bounces, letting it proceed normally");
                            }
                        }
                        public void onAttach(com.github.unidbg.arm.backend.UnHook unHook) {}
                        public void detach() {}
                    }, 0x7b290L, 0x7b290L, null);
                    System.out.println(">>> Runaway-origin hook installed at 0x7b290");
                } catch (Throwable t) {
                    System.out.println(">>> Runaway-origin hook FAILED: " + t);
                }

                // DISABLED: Backend proxy — was causing page_collection_lock_arm crashes.
                // The secondary loader's mprotect is already handled by re-protecting
                // PAGE@0x12038000 before N.l with RWX. No need for dynamic interception.
                System.out.println(">>> Backend proxy DISABLED (pre-protecting via static mem_protect)");
                // Still need realBackendRef for the raw unicorn hooks check below
                final Backend[] realBackendRef = new Backend[]{emulator.getBackend()};
                // Session 23 part 8: split the old blanket ENABLE_RAW_HOOKS flag into three.
                // UNMAPPED is already covered safely by our own EventMemHook (mask 112, no
                // reflection) — leave that one off to avoid double-handling/corruption risk.
                // We now genuinely hit UC_ERR_FETCH_PROT (part 7's fix got us here cleanly),
                // and this FETCH_PROT hook is the one built specifically for that — try it.
                final boolean ENABLE_RAW_HOOKS = false;
                // Session 23 part 8: tried it — fired once for a spurious unrelated address
                // (0x0, garbage PC) and the real 0x120381c1 fault still happened right after,
                // just with a DIFFERENT error type (UC_ERR_MAP instead of FETCH_PROT) than the
                // unpatched run. Same address, different error across otherwise-identical runs
                // = the reflection-based hook perturbing state, matching the original warning
                // this was disabled for. Reverted; use the plain mem_protect nudge inside the
                // already-trusted SINGLETON2 dispatch hook instead (see below).
                final boolean ENABLE_FETCH_PROT_HOOK = false;
                final boolean ENABLE_WRITE_PROT_HOOK = false;

                // Session 21 Plan F: register UC_HOOK_MEM_FETCH_PROT directly on the raw Unicorn
                // native handle. The EventMemHook API (type mask 64) only handles UNMAPPED fetches,
                // not PROT fetches. The real mprotect goes through Memory.mprotect → Unicorn.mem_protect
                // which bypasses Backend entirely, so neither the backend proxy nor EventMemHook
                // can catch it. A UC_HOOK_MEM_FETCH_PROT fires at the native level when the fetch
                // fails due to protection; we re-protect on the spot and return true to retry.
                if (ENABLE_FETCH_PROT_HOOK && realBackendRef[0] != null) {
                    try {
                        // Access the unicorn field from Unicorn2Backend
                        Class<?> backendClass = realBackendRef[0].getClass();
                        Field unicornField = null;
                        try {
                            unicornField = backendClass.getDeclaredField("unicorn");
                        } catch (NoSuchFieldException e) {
                            // try superclass
                            unicornField = backendClass.getSuperclass().getDeclaredField("unicorn");
                        }
                        unicornField.setAccessible(true);
                        final Object unicorn = unicornField.get(realBackendRef[0]);
                        System.out.println(">>> [RawUnicorn] got Unicorn handle: "
                            + unicorn.getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(unicorn)));

                        // Find the unicorn-level EventMemHook interface
                        Class<?> uehClass = Class.forName("com.github.unidbg.arm.backend.unicorn.EventMemHook");

                        // Create the callback via dynamic proxy
                        Object fetchProtHook = Proxy.newProxyInstance(
                            uehClass.getClassLoader(),
                            new Class<?>[]{uehClass},
                            new InvocationHandler() {
                                public Object invoke(Object proxy, Method method, Object[] args2) throws Throwable {
                                    if ("hook".equals(method.getName())) {
                                        // args: Unicorn unicorn, long address, int size, long value, Object user
                                        long addr = (Long) args2[1];
                                        try {
                                            // Log PC/LR at fault time
                                            Method regRead = unicorn.getClass().getMethod("reg_read", int.class);
                                            long pc = (Long) regRead.invoke(unicorn, 15);
                                            long lr = (Long) regRead.invoke(unicorn, 14);
                                            System.out.println(">>> [UC_HOOK_MEM_FETCH_PROT] fault at 0x"
                                                + Long.toHexString(addr) + "  PC=0x" + Long.toHexString(pc)
                                                + "  LR=0x" + Long.toHexString(lr));
                                            // Re-protect the faulting page with exec
                                            long pageStart = addr & ~0xfffL;
                                            Method mp = unicorn.getClass().getMethod("mem_protect",
                                                long.class, long.class, int.class);
                                            mp.invoke(unicorn, pageStart, 0x1000L,
                                                UnicornConst.UC_PROT_READ | UnicornConst.UC_PROT_WRITE | UnicornConst.UC_PROT_EXEC);
                                            System.out.println(">>> [UC_HOOK_MEM_FETCH_PROT] re-protected page 0x"
                                                + Long.toHexString(pageStart) + " RWX OK");
                                            return Boolean.TRUE; // handled — retry fetch
                                        } catch (Throwable t2) {
                                            System.out.println(">>> [UC_HOOK_MEM_FETCH_PROT] re-protect FAILED: " + t2);
                                            return Boolean.FALSE;
                                        }
                                    }
                                    if ("toString".equals(method.getName())) return "FetchProtHook";
                                    // onAttach/detach — no-op
                                    return null;
                                }
                            });

                        // Register UC_HOOK_MEM_FETCH_PROT = 1 << 9 = 512
                        Method hookAddMem = unicorn.getClass().getMethod("hook_add_new",
                            uehClass, int.class, Object.class);
                        Object result = hookAddMem.invoke(unicorn, fetchProtHook, 512, null);
                        System.out.println(">>> [RawUnicorn] UC_HOOK_MEM_FETCH_PROT installed (result="
                            + result.getClass().getSimpleName() + ")");

                    } catch (Throwable t) {
                        System.out.println(">>> [RawUnicorn] FETCH_PROT hook FAILED: " + t);
                        t.printStackTrace(System.out);
                    }
                } else {
                    System.out.println(">>> [RawUnicorn] no realBackendRef, skipping FETCH_PROT hook");
                }

                // Session 21c: UC_HOOK_MEM_WRITE_PROT (mask 256) — catch writes to write-protected
                // pages. The secondary loader at 0x120381c1 keeps crashing with UC_ERR_WRITE_PROT
                // because it tries to write to a page that had its write permission removed by the
                // host-side mprotect (which bypasses the Backend proxy). Re-enable write on the
                // target page and retry.
                if (ENABLE_WRITE_PROT_HOOK && realBackendRef[0] != null) {
                    try {
                        Class<?> backendClass = realBackendRef[0].getClass();
                        Field unicornField = null;
                        try {
                            unicornField = backendClass.getDeclaredField("unicorn");
                        } catch (NoSuchFieldException e) {
                            unicornField = backendClass.getSuperclass().getDeclaredField("unicorn");
                        }
                        unicornField.setAccessible(true);
                        final Object unicornWp = unicornField.get(realBackendRef[0]);

                        Class<?> uehClass = Class.forName("com.github.unidbg.arm.backend.unicorn.EventMemHook");
                        Object writeProtHook = Proxy.newProxyInstance(
                            uehClass.getClassLoader(),
                            new Class<?>[]{uehClass},
                            new InvocationHandler() {
                                public Object invoke(Object proxy, Method method, Object[] args2) throws Throwable {
                                    if ("hook".equals(method.getName())) {
                                        long addr = (Long) args2[1];
                                        int size = (Integer) args2[2];
                                        long pc = 0, lr = 0;
                                        try {
                                            Method regRead = unicornWp.getClass().getMethod("reg_read", int.class);
                                            pc = (Long) regRead.invoke(unicornWp, 15);
                                            lr = (Long) regRead.invoke(unicornWp, 14);
                                        } catch (Throwable t) {}
                                        System.out.println(">>> [UC_HOOK_MEM_WRITE_PROT] write to protected 0x"
                                            + Long.toHexString(addr) + " size=" + size
                                            + " PC=0x" + Long.toHexString(pc)
                                            + " LR=0x" + Long.toHexString(lr));
                                        // Re-enable write permission on the faulting page
                                        long pageStart = addr & ~0xfffL;
                                        Method mp = unicornWp.getClass().getMethod("mem_protect",
                                            long.class, long.class, int.class);
                                        mp.invoke(unicornWp, pageStart, 0x1000L,
                                            UnicornConst.UC_PROT_READ | UnicornConst.UC_PROT_WRITE | UnicornConst.UC_PROT_EXEC);
                                        System.out.println(">>> [UC_HOOK_MEM_WRITE_PROT] re-protected page 0x"
                                            + Long.toHexString(pageStart) + " RWX OK");
                                        return Boolean.TRUE; // handled — retry write
                                    }
                                    if ("toString".equals(method.getName())) return "WriteProtHook";
                                    return null;
                                }
                            });
                        Method hookAddMem = unicornWp.getClass().getMethod("hook_add_new",
                            uehClass, int.class, Object.class);
                        Object result = hookAddMem.invoke(unicornWp, writeProtHook, 256, null);
                        System.out.println(">>> [RawUnicorn] UC_HOOK_MEM_WRITE_PROT installed (result="
                            + result.getClass().getSimpleName() + ")");
                    } catch (Throwable t) {
                        System.out.println(">>> [RawUnicorn] WRITE_PROT hook FAILED: " + t);
                        t.printStackTrace(System.out);
                    }
                }

                // Session 21b: also handle UC_HOOK_MEM_UNMAPPED (mask 0x10) for read/write
                // from unmapped pages. The decrypted N.l code reads from a computed address
                // 0xe13000a6 which isn't mapped yet. Auto-map on access.
                if (ENABLE_RAW_HOOKS && realBackendRef[0] != null) {
                    try {
                        // Re-use the unicorn handle already obtained above
                        Class<?> backendClass = realBackendRef[0].getClass();
                        Field unicornField = null;
                        try {
                            unicornField = backendClass.getDeclaredField("unicorn");
                        } catch (NoSuchFieldException e) {
                            unicornField = backendClass.getSuperclass().getDeclaredField("unicorn");
                        }
                        unicornField.setAccessible(true);
                        final Object unicorn2 = unicornField.get(realBackendRef[0]);

                        Class<?> uehClass = Class.forName("com.github.unidbg.arm.backend.unicorn.EventMemHook");
                        Object readUnmappedHook = Proxy.newProxyInstance(
                            uehClass.getClassLoader(),
                            new Class<?>[]{uehClass},
                            new InvocationHandler() {
                                public Object invoke(Object proxy, Method method, Object[] args2) throws Throwable {
                                    if ("hook".equals(method.getName())) {
                                        long addr = (Long) args2[1];
                                        int size = (Integer) args2[2];
                                        long pc = 0, lr = 0;
                                        try {
                                            Method regRead = unicorn2.getClass().getMethod("reg_read", int.class);
                                            pc = (Long) regRead.invoke(unicorn2, 15);
                                            lr = (Long) regRead.invoke(unicorn2, 14);
                                        } catch (Throwable t) {}
                                        System.out.println(">>> [UC_HOOK_MEM_UNMAPPED] addr=0x"
                                            + Long.toHexString(addr) + " size=" + size
                                            + " PC=0x" + Long.toHexString(pc)
                                            + " LR=0x" + Long.toHexString(lr));
                                        // Map the faulting page RWX so access can proceed
                                        long pageStart = addr & ~0xfffL;
                                        Method mp = unicorn2.getClass().getMethod("mem_map",
                                            long.class, long.class, int.class);
                                        mp.invoke(unicorn2, pageStart, 0x1000L,
                                            UnicornConst.UC_PROT_READ | UnicornConst.UC_PROT_WRITE | UnicornConst.UC_PROT_EXEC);
                                        System.out.println(">>> [UC_HOOK_MEM_UNMAPPED] mapped page 0x"
                                            + Long.toHexString(pageStart) + " RWX OK");
                                        return Boolean.TRUE;
                                    }
                                    if ("toString".equals(method.getName())) return "UnmappedHook";
                                    return null;
                                }
                            });
                        Method hookAddMem = unicorn2.getClass().getMethod("hook_add_new",
                            uehClass, int.class, Object.class);
                        Object result = hookAddMem.invoke(unicorn2, readUnmappedHook, 0x10, null);
                        System.out.println(">>> [RawUnicorn] UC_HOOK_MEM_UNMAPPED installed (result="
                            + result.getClass().getSimpleName() + ")");
                    } catch (Throwable t) {
                        System.out.println(">>> [RawUnicorn] UC_HOOK_MEM_UNMAPPED FAILED: " + t);
                        t.printStackTrace(System.out);
                    }
                }

                // Plan E: CodeHook at 0x12037660 that re-protects the code page right before
                // BLX R6 (0x12037662) re-enters the decrypted code region. This is the last
                // possible moment to restore exec before FETCH_PROT fires — the mprotect happens
                // somewhere in unidbg's host layer and does NOT go through emulator.getBackend().
                // Write ARM BX LR stub at SINGLETON2 so BLX R12 returns to caller
                try {
                    // BX LR in ARM: 0xE12FFF1E — already written at SINGLETON2 above
                    System.out.println(">>> Wrote BX LR stub at SINGLETON2 (0x7f003000)");
                } catch (Throwable t) {
                    System.out.println(">>> SINGLETON BX LR stub FAILED: " + t);
                }

                try {
                    backend.hook_add_new(new CodeHook() {
                        int hit;
                        public void hook(Backend b, long address, int size, Object user) {
                            long r6 = b.reg_read(ArmConst.UC_ARM_REG_R6).longValue();
                            long r12 = b.reg_read(ArmConst.UC_ARM_REG_R12).longValue();
                            // Dump the actual data at R6+0x2d8 to see what the LDR reads
                            long ptrAt2d8 = 0;
                            long ptrAt2d4 = 0;
                            long ptrAt2dc = 0;
                            long ptrAt0 = 0;
                            long ptrAt100 = 0;
                            try {
                                byte[] d2d8 = b.mem_read(r6 + 0x2d8, 4);
                                ptrAt2d8 = (long)(d2d8[0] & 0xff) | ((long)(d2d8[1] & 0xff) << 8)
                                    | ((long)(d2d8[2] & 0xff) << 16) | ((long)(d2d8[3] & 0xff) << 24);
                                byte[] d2d4 = b.mem_read(r6 + 0x2d4, 4);
                                ptrAt2d4 = (long)(d2d4[0] & 0xff) | ((long)(d2d4[1] & 0xff) << 8)
                                    | ((long)(d2d4[2] & 0xff) << 16) | ((long)(d2d4[3] & 0xff) << 24);
                                byte[] d2dc = b.mem_read(r6 + 0x2dc, 4);
                                ptrAt2dc = (long)(d2dc[0] & 0xff) | ((long)(d2dc[1] & 0xff) << 8)
                                    | ((long)(d2dc[2] & 0xff) << 16) | ((long)(d2dc[3] & 0xff) << 24);
                                // Also dump R6+0 and R6+0x100
                                byte[] d0 = b.mem_read(r6, 4);
                                ptrAt0 = (long)(d0[0] & 0xff) | ((long)(d0[1] & 0xff) << 8)
                                    | ((long)(d0[2] & 0xff) << 16) | ((long)(d0[3] & 0xff) << 24);
                                byte[] d100 = b.mem_read(r6 + 0x100, 4);
                                ptrAt100 = (long)(d100[0] & 0xff) | ((long)(d100[1] & 0xff) << 8)
                                    | ((long)(d100[2] & 0xff) << 16) | ((long)(d100[3] & 0xff) << 24);
                            } catch (Throwable t) {}
                            if (hit < 3) {
                                System.out.println(">>> [LastResortHook@0x12037660] R6=0x" + Long.toHexString(r6)
                                    + " R12=0x" + Long.toHexString(r12)
                                    + " [R6+0]=0x" + Long.toHexString(ptrAt0)
                                    + " [R6+0x100]=0x" + Long.toHexString(ptrAt100)
                                    + " [R6+0x2d4]=0x" + Long.toHexString(ptrAt2d4)
                                    + " [R6+0x2d8]=0x" + Long.toHexString(ptrAt2d8)
                                    + " [R6+0x2dc]=0x" + Long.toHexString(ptrAt2dc));
                            }
                            hit++;
                            try {
                                // Session 23 part 16 BISECT: gated — the third inconsistent-size
                                // protect (0x4000 here vs 0x2000 and 0x1000 elsewhere). Register
                                // writes below stay (functional scan-bypass, not protect churn).
                                if (!BISECT_NO_DYNAMIC_PROTECT) {
                                    b.mem_protect(0x12038000L, 0x4000,
                                        UnicornConst.UC_PROT_READ | UnicornConst.UC_PROT_WRITE | UnicornConst.UC_PROT_EXEC);
                                }
                                // Force R12 to SINGLETON2 (ARM mode, bit 0 = 0) — BX LR stub
                                b.reg_write(ArmConst.UC_ARM_REG_R12, 0x7f003000L);
                                // Force R0=0 so CBZ at 0x12037664 takes exit branch, skipping scan
                                b.reg_write(ArmConst.UC_ARM_REG_R0, 0L);
                            } catch (Throwable t) {
                                System.out.println(">>> [LastResortHook] FAILED: " + t);
                            }
                        }
                        public void onAttach(com.github.unidbg.arm.backend.UnHook unHook) {}
                        public void detach() {}
                    }, 0x12037660L, 0x12037660L, null);
                    System.out.println(">>> Last-resort CodeHook installed at 0x12037660");
                } catch (Throwable t) {
                    System.out.println(">>> Last-resort CodeHook FAILED: " + t);
                }

                // Dump the bytes at 0x12037662 — last address before FETCH_PROT re-entry
                try {
                    byte[] dmp = backend.mem_read(0x12037660L, 12);
                    StringBuilder sb = new StringBuilder();
                    for (byte b : dmp) sb.append(String.format("%02x", b & 0xff));
                    System.out.println(">>> CODE@0x12037660: " + sb);
                } catch (Throwable t) {
                    System.out.println(">>> CODE@0x12037660 read FAILED: " + t);
                }

                // Trace: CodeHook at 0x12037662 (BLX R12) and SINGLETON entry
                try {
                    backend.hook_add_new(new CodeHook() {
                        public void hook(Backend b, long address, int size, Object user) {
                            long r6 = b.reg_read(ArmConst.UC_ARM_REG_R6).longValue();
                            long r12 = b.reg_read(ArmConst.UC_ARM_REG_R12).longValue();
                            long lr = b.reg_read(ArmConst.UC_ARM_REG_LR).longValue();
                            long r0 = b.reg_read(ArmConst.UC_ARM_REG_R0).longValue();
                            long r1 = b.reg_read(ArmConst.UC_ARM_REG_R1).longValue();
                            long r2 = b.reg_read(ArmConst.UC_ARM_REG_R2).longValue();
                            long sp = b.reg_read(ArmConst.UC_ARM_REG_SP).longValue();
                            System.out.println(">>> [trace@BLX_R12] R6=0x" + Long.toHexString(r6)
                                + " R12=0x" + Long.toHexString(r12)
                                + " LR=0x" + Long.toHexString(lr)
                                + " R0=0x" + Long.toHexString(r0)
                                + " R1=0x" + Long.toHexString(r1)
                                + " R2=0x" + Long.toHexString(r2)
                                + " SP=0x" + Long.toHexString(sp));
                        }
                        public void onAttach(com.github.unidbg.arm.backend.UnHook unHook) {}
                        public void detach() {}
                    }, 0x12037662L, 0x12037662L, null);
                } catch (Throwable t) {
                    System.out.println(">>> BLX_R12 trace hook FAILED: " + t);
                }
                try {
                    backend.hook_add_new(new CodeHook() {
                        int hits;
                        public void hook(Backend b, long address, int size, Object user) {
                            if (hits++ < 8) {
                                long insn = b.reg_read(ArmConst.UC_ARM_REG_R0).longValue();
                                System.out.println(">>> [trace@SINGLETON2+0x"
                                    + Long.toHexString(address - 0x7f003000L)
                                    + "] R0=0x" + Long.toHexString(insn));
                                // Dump 4 bytes of code at this address
                                try {
                                    byte[] code = b.mem_read(address, 4);
                                    StringBuilder sb = new StringBuilder();
                                    for (byte x : code) sb.append(String.format("%02x", x & 0xff));
                                    System.out.println(">>>   code: " + sb);
                                } catch (Throwable t) {}
                            }
                        }
                        public void onAttach(com.github.unidbg.arm.backend.UnHook unHook) {}
                        public void detach() {}
                    }, 0x7f003000L, 0x7f003fffL, null);
                } catch (Throwable t) {
                    System.out.println(">>> SINGLETON2 trace hook FAILED: " + t);
                }

                // Session 21: dump regs at 0x1203a6a0 — right before `blx r5` (0x1203a6a2)
                // that branches to unmapped pages. r5=0x0 confirmed from log. Fix: force r5 to
                // SINGLETON2 BX LR stub (0x7f003000) so `blx r5` returns immediately.
                try {
                    backend.hook_add_new(new CodeHook() {
                        int n;
                        public void hook(Backend b, long address, int size, Object user) {
                            // Always force r5 to SINGLETON2 BX LR stub — this is the fix
                            b.reg_write(ArmConst.UC_ARM_REG_R5, 0x7f003000L);
                            // Dump regs only first 3 hits for diagnosis
                            if (n++ >= 3) return;
                            StringBuilder sbh = new StringBuilder(">>> [3a6a0] regs (r5 FORCED to SINGLETON2):");
                            int[] regs = {ArmConst.UC_ARM_REG_R0, ArmConst.UC_ARM_REG_R1, ArmConst.UC_ARM_REG_R2,
                                    ArmConst.UC_ARM_REG_R3, ArmConst.UC_ARM_REG_R4, ArmConst.UC_ARM_REG_R5,
                                    ArmConst.UC_ARM_REG_R6, ArmConst.UC_ARM_REG_R7, ArmConst.UC_ARM_REG_R8,
                                    ArmConst.UC_ARM_REG_R9, ArmConst.UC_ARM_REG_R10, ArmConst.UC_ARM_REG_R11,
                                    ArmConst.UC_ARM_REG_R12, ArmConst.UC_ARM_REG_LR, ArmConst.UC_ARM_REG_SP,
                                    ArmConst.UC_ARM_REG_PC};
                            String[] names = {"r0","r1","r2","r3","r4","r5","r6","r7","r8","sb(r9)","sl(r10)","fp(r11)","ip(r12)","lr","sp","pc"};
                            for (int i = 0; i < regs.length; i++) {
                                long v = b.reg_read(regs[i]).longValue();
                                sbh.append(' ').append(names[i]).append("=0x").append(Long.toHexString(v));
                            }
                            System.out.println(sbh);
                            // Dump bytes around 0x1203a6a0 — extended to 48 bytes to capture beyond CBZ skip
                            try {
                                byte[] code = b.mem_read(address - 8, 48);
                                StringBuilder csb = new StringBuilder(">>> [3a6a0] code[-8..+40]: ");
                                for (byte x : code) csb.append(String.format("%02x", x & 0xff));
                                System.out.println(csb);
                            } catch (Throwable t) {}
                        }
                        public void onAttach(com.github.unidbg.arm.backend.UnHook unHook) {}
                        public void detach() {}
                    }, 0x1203a6a0L, 0x1203a6a0L, null);
                    System.out.println(">>> Regdump hook installed at 0x1203a6a0");

                // Session 21: diagnostic hook at 0x1203a6b0 — right after CBZ skip, before the second bad branch
                // LR=0x1203a6b5 from FETCH log indicates branch at 0x1203a6b2. Dump regs to find which has 0x0.
                try {
                    backend.hook_add_new(new CodeHook() {
                        int n;
                        public void hook(Backend b, long address, int size, Object user) {
                            if (n++ >= 2) return;
                            StringBuilder sbh = new StringBuilder(">>> [6b0] regs (AFTER CBZ skip, pre-branch):");
                            int[] regs = {ArmConst.UC_ARM_REG_R0, ArmConst.UC_ARM_REG_R1, ArmConst.UC_ARM_REG_R2,
                                    ArmConst.UC_ARM_REG_R3, ArmConst.UC_ARM_REG_R4, ArmConst.UC_ARM_REG_R5,
                                    ArmConst.UC_ARM_REG_R6, ArmConst.UC_ARM_REG_R7, ArmConst.UC_ARM_REG_R8,
                                    ArmConst.UC_ARM_REG_R9, ArmConst.UC_ARM_REG_R10, ArmConst.UC_ARM_REG_R11,
                                    ArmConst.UC_ARM_REG_R12, ArmConst.UC_ARM_REG_LR, ArmConst.UC_ARM_REG_SP,
                                    ArmConst.UC_ARM_REG_PC};
                            String[] names = {"r0","r1","r2","r3","r4","r5","r6","r7","r8","sb(r9)","sl(r10)","fp(r11)","ip(r12)","lr","sp","pc"};
                            for (int i = 0; i < regs.length; i++) {
                                long v = b.reg_read(regs[i]).longValue();
                                sbh.append(' ').append(names[i]).append("=0x").append(Long.toHexString(v));
                            }
                            System.out.println(sbh);
                            // Dump 64 bytes from 0x1203a6a0 to see full post-CBZ code
                            try {
                                byte[] code = b.mem_read(0x1203a6a0L, 64);
                                StringBuilder csb = new StringBuilder(">>> [6b0] code[0x1203a6a0..+64]: ");
                                for (byte x : code) csb.append(String.format("%02x", x & 0xff));
                                System.out.println(csb);
                            } catch (Throwable t) {}
                        }
                        public void onAttach(com.github.unidbg.arm.backend.UnHook unHook) {}
                        public void detach() {}
                    }, 0x1203a6b0L, 0x1203a6b0L, null);
                    System.out.println(">>> Diagnostic hook installed at 0x1203a6b0");
                } catch (Throwable t) {
                    System.out.println(">>> 6b0 hook FAILED: " + t);
                }

                // Session 21 v2: safety-net hook at 0x1203a6b2 (BLX R0) — force R0 to SINGLETON2 BX LR stub.
                // This catches any case where CBNZ doesn't skip AND the LDR+LDR path produces 0x0 in R0.
                try {
                    backend.hook_add_new(new CodeHook() {
                        public void hook(Backend b, long address, int size, Object user) {
                            // Force R0 to SINGLETON2 BX LR stub so BLX R0 returns safely
                            b.reg_write(ArmConst.UC_ARM_REG_R0, 0x7f003000L);
                            System.out.println(">>> [6b2] safety net: forced R0=0x7f003000");
                        }
                        public void onAttach(com.github.unidbg.arm.backend.UnHook unHook) {}
                        public void detach() {}
                    }, 0x1203a6b2L, 0x1203a6b2L, null);
                    System.out.println(">>> Safety-net hook installed at 0x1203a6b2");
                } catch (Throwable t) {
                    System.out.println(">>> 6b2 hook FAILED: " + t);
                }

                // Session 21d: bypass at 0x12038240 — the anti-tamper BL that branches to 0x0.
                // The instruction is `f003 f986 = BL target_that_evaluates_to_0x0`. Unlike BX/BLX
                // to a register, forcing registers won't help — we skip the instruction entirely
                // by writing the fall-through address (0x12038244) directly to PC. The hook also
                // diagnoses instruction bytes and regs on first hit.
                try {
                    backend.hook_add_new(new CodeHook() {
                        int n;
                        public void hook(Backend b, long address, int size, Object user) {
                            if (n++ < 2) {
                                try {
                                    byte[] code = b.mem_read(address, 6);
                                    StringBuilder csb = new StringBuilder(">>> [38240] code: ");
                                    for (byte x : code) csb.append(String.format("%02x", x & 0xff));
                                    System.out.println(csb);
                                } catch (Throwable t) {}
                                StringBuilder sbh = new StringBuilder(">>> [38240] regs:");
                                int[] regs = {0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15};
                                String[] names = {"r0","r1","r2","r3","r4","r5","r6","r7","r8","r9","r10","r11","r12","sp","lr","pc"};
                                for (int i = 0; i < regs.length; i++) {
                                    long v = b.reg_read(regs[i]).longValue();
                                    sbh.append(' ').append(names[i]).append("=0x").append(Long.toHexString(v));
                                }
                                System.out.println(sbh);
                            }
                            // Bypass: skip the BL by writing PC to the fall-through address (0x12038245 = Thumb bit set)
                            b.reg_write(ArmConst.UC_ARM_REG_PC, 0x12038245L);
                        }
                        public void onAttach(com.github.unidbg.arm.backend.UnHook unHook) {}
                        public void detach() {}
                    }, 0x12038240L, 0x12038240L, null);
                    System.out.println(">>> Safety-net hook installed at 0x12038240 (BL skip)");
                } catch (Throwable t) {
                    System.out.println(">>> 38240 hook FAILED: " + t);
                }

                // Session 21 v3: safety-net hook at 0x12038270 (BLX R0) — force R0 to SINGLETON2.
                // After the 6b2 safety net returns, execution continues to another BLX R0 at 0x38270
                // where R0 is also 0, triggering another FETCH-walk crash.
                try {
                    backend.hook_add_new(new CodeHook() {
                        public void hook(Backend b, long address, int size, Object user) {
                            long r0 = b.reg_read(ArmConst.UC_ARM_REG_R0).longValue();
                            if (r0 == 0) {
                                b.reg_write(ArmConst.UC_ARM_REG_R0, 0x7f003000L);
                                System.out.println(">>> [38270] safety net: R0 was 0, forced R0=0x7f003000");
                            } else {
                                System.out.println(">>> [38270] trace: R0=0x" + Long.toHexString(r0) + " (not zero, passing through)");
                            }
                        }
                        public void onAttach(com.github.unidbg.arm.backend.UnHook unHook) {}
                        public void detach() {}
                    }, 0x12038270L, 0x12038270L, null);
                    System.out.println(">>> Safety-net hook installed at 0x12038270");
                } catch (Throwable t) {
                    System.out.println(">>> 38270 hook FAILED: " + t);
                }

                // Session 21f: directly hook the scan BL at 0x1203767c (BL -> 0x1207b7d0).
                // This is the actual memory-scan call that jumps through the veneer table
                // to every 4KB page from 0x7b290 upward. We skip it: set R0=0 (not-found)
                // and PC=0x12037681 (return address), short-circuiting the entire scan.
                try {
                    backend.hook_add_new(new CodeHook() {
                        int n;
                        public void hook(Backend b, long address, int size, Object user) {
                            if (n++ < 2) System.out.println(">>> [scan-kill@0x1203767c] nuking scan call #" + n);
                            b.reg_write(ArmConst.UC_ARM_REG_R0, 0L);
                            b.reg_write(ArmConst.UC_ARM_REG_PC, 0x12037681L);
                        }
                        public void onAttach(com.github.unidbg.arm.backend.UnHook unHook) {}
                        public void detach() {}
                    }, 0x1203767cL, 0x1203767cL, null);
                    System.out.println(">>> Scan-kill hook installed at 0x1203767c");
                } catch (Throwable t) {
                    System.out.println(">>> Scan-kill hook FAILED: " + t);
                }

                } catch (Throwable t) {
                    System.out.println(">>> 3a6a0 hook FAILED: " + t);
                }

                // N.l runs without bypass now — dispatch table calls go through SINGLETON
                // BX LR stubs (0x7f003000). Let it complete its control flow naturally.

                // Pre-N.l sanity: dump the regular decrypted-output landing zone
                // (pattern so far: base+0xb2000 where base=max_premap_boundary).
                // With 0x20000000 premap, candidate = 0x200b2000.
                for (long probe : new long[]{0x100b2000L, 0x110b2000L, 0x200b2000L, 0x20101000L,
                        0x11fb8000L, 0x11f01000L, 0x11ffe000L}) {
                    try {
                        byte[] buf = backend.mem_read(probe, 16);
                        StringBuilder sb = new StringBuilder(">>> pre-N.l probe @0x" + Long.toHexString(probe) + ": ");
                        for (byte x : buf) sb.append(String.format("%02x", x & 0xff));
                        System.out.println(sb);
                    } catch (Throwable t) {
                        System.out.println(">>> pre-N.l probe @0x" + Long.toHexString(probe) + " FAILED: " + t);
                    }
                }

                // ===== Session 23 part 18: REAL positive control + pre-N.l JNI snapshot =====
                // libexec's pre-N.l class resolution has now run. If ANY JNI hook fired, the
                // technique is proven live (the check part 14 skipped). Snapshot totals so the
                // post-N.l delta shows exactly which JNI fns, if any, N.l itself calls.
                int jniTotalBeforeNl = 0;
                if (INSTALL_JNI_HOOKS_PRELOAD) {
                    System.out.println(">>> [p18 PRE-N.l] JNI totals:");
                    for (java.util.Map.Entry<String,int[]> e : jniHits.entrySet()) {
                        jniTotalBeforeNl += e.getValue()[0];
                        if (e.getValue()[0] > 0)
                            System.out.println(">>>   " + e.getKey() + " = " + e.getValue()[0]);
                    }
                    if (jniTotalBeforeNl > 0)
                        System.out.println(">>> [p18 POSITIVE-CONTROL] PASS — " + jniTotalBeforeNl
                            + " JNI hook hits so far (incl. FindClass base+4); hooks are LIVE, so"
                            + " 'no JNI during N.l' below is a TRUSTWORTHY negative.");
                    else
                        System.out.println(">>> [p18 POSITIVE-CONTROL] *** FAIL *** — 0 JNI hits before N.l;"
                            + " hooks not firing, switch to an SVC-number InterruptHook before trusting anything.");
                }

                System.out.println(">>> calling N.l(Application, path) ...");
                System.out.println(">>> SINGLETON2 dispatch count before N.l: " + singleton2Hits[0]);
                boolean nOk = false;
                // Disable walk trace hooks during N.l to avoid Unicorn internal corruption
                // from too many CodeHook callbacks during deep recursive decryption.
                boolean savedJniPhase = jniPhase[0];
                jniPhase[0] = false;
                try {
                    boolean lResult = N.callStaticJniMethodBoolean(emulator,
                            "l(Landroid/app/Application;Ljava/lang/String;)Z",
                            app, "/data/app/com.android.mgstv-1/base.apk");
                    System.out.println(">>> N.l returned: "+lResult);
                    nOk = true;
                } catch (Throwable t) {
                    System.out.println(">>> N.l threw: " + t);
                }
                jniPhase[0] = savedJniPhase;
                System.out.println(">>> SINGLETON2 dispatch count after N.l: " + singleton2Hits[0]);

                // ===== Session 23 part 18: post-N.l JNI delta = calls N.l itself made =====
                if (INSTALL_JNI_HOOKS_PRELOAD) {
                    int jniTotalAfterNl = 0;
                    for (int[] v : jniHits.values()) jniTotalAfterNl += v[0];
                    int during = jniTotalAfterNl - jniTotalBeforeNl;
                    System.out.println(">>> [p18 POST-N.l] JNI-env calls made DURING N.l = " + during
                        + " (before=" + jniTotalBeforeNl + " after=" + jniTotalAfterNl + ")");
                    System.out.println(">>> [p18 VERDICT] " + (during == 0
                        ? "N.l made ZERO JNI-env calls before the 0x120381c1 fault -> the EXEC-loss is"
                          + " NOT triggered by a JNIEnv function (valid: hooks proven live pre-N.l)."
                          + " Next suspect: guest mprotect LINUX SYSCALL (SVC r7=125), unhooked so far."
                        : "N.l DID call JNI-env fns -> inspect the '<<< NEAR 0x12038 CRASH WINDOW' lines above."));
                }
                if (INSTALL_SYSCALL_HOOK) {
                    System.out.println(">>> [p19 SYSCALL SUMMARY] total interrupts seen = " + intAll[0]
                        + "; mprotect/mmap2/munmap = " + syscallSeen[0] + ".");
                    if (intAll[0] == 0) {
                        System.out.println(">>> [p19 INT-CTRL] *** FAIL *** — 0 interrupts fired. The Backend"
                            + " InterruptHook is NOT wired to guest svc in this backend; the '0 mprotect' result"
                            + " is MEANINGLESS. Next: subclass ARM32SyscallHandler (override hook/mprotect) instead.");
                    } else if (syscallSeen[0] == 0) {
                        System.out.println(">>> [p19 INT-CTRL] PASS (" + intAll[0] + " interrupts) but ZERO mem-syscalls"
                            + " -> the EXEC-strip is genuinely NOT a guest mprotect/mmap2/munmap. Next check unidbg"
                            + " host-side (AndroidElfLoader PT_LOAD re-protect), then map 0x12038000 as its own page.");
                    } else {
                        System.out.println(">>> [p19] mem-syscalls occurred; scan for '<<< COVERS 0x12038000' /"
                            + " '*** STRIPS EXEC ***' above to see if one hit our page.");
                    }
                }

                // Session 23: re-check the SINGLETON bytes we force-wrote pre-N.l.
                // If N.l did any real init, these should differ from our fake BX-LR-stub
                // fill; if unchanged, N.l never wrote through the dispatch table at all.
                try {
                    byte[] c1 = backend.mem_read(0x7f0022e2L, 1);
                    byte[] c2 = backend.mem_read(0x7f002138L, 4);
                    byte[] c3 = backend.mem_read(SINGLETON, 4);
                    System.out.printf(">>> post-N.l SINGLETON byte[0x7f0022e2]=0x%02x%n", c1[0]&0xff);
                    System.out.printf(">>> post-N.l SINGLETON byte[0x7f002138]=0x%02x%02x%02x%02x%n",
                        c2[0]&0xff, c2[1]&0xff, c2[2]&0xff, c2[3]&0xff);
                    System.out.printf(">>> post-N.l SINGLETON byte[0x7f002000]=0x%02x%02x%02x%02x%n",
                        c3[0]&0xff, c3[1]&0xff, c3[2]&0xff, c3[3]&0xff);
                } catch (Throwable t) {
                    System.out.println(">>> post-N.l SINGLETON dump failed: " + t);
                }

                // Post-N.l: dump candidate landing zones and scan for non-zero pages
                for (long probe : new long[]{0x100b2000L, 0x110b2000L, 0x200b2000L, 0x20101000L,
                        0x11fb8000L, 0x11f01000L, 0x11ffe000L}) {
                    try {
                        byte[] buf = backend.mem_read(probe, 16);
                        StringBuilder sb = new StringBuilder(">>> post-N.l probe @0x" + Long.toHexString(probe) + ": ");
                        for (byte x : buf) sb.append(String.format("%02x", x & 0xff));
                        System.out.println(sb);
                    } catch (Throwable t) {
                        System.out.println(">>> post-N.l probe @0x" + Long.toHexString(probe) + " FAILED: " + t);
                    }
                }
                // Broader scan: find any non-zero 4K pages in the 0x10000000-0x21000000 range
                int nonZero = 0;
                for (long pg = 0x10000000L; pg < 0x21000000L; pg += 0x1000L) {
                    try {
                        byte[] buf = backend.mem_read(pg, 4);
                        int v = (buf[0] & 0xff) | ((buf[1] & 0xff) << 8)
                              | ((buf[2] & 0xff) << 16) | ((buf[3] & 0xff) << 24);
                        if (v != 0) {
                            if (nonZero++ < 20) {
                                System.out.println(">>> post-N.l non-zero page @0x" + Long.toHexString(pg)
                                    + " first32=0x" + Integer.toHexString(v));
                            }
                        }
                    } catch (Throwable t) { /* unmapped, skip */ }
                }
                System.out.println(">>> post-N.l scan: " + nonZero + " non-zero pages in 0x10000000-0x21000000");

                // Session 23: call b2b unconditionally, even when N.l failed/threw.
                // b2b([BI)[B takes the raw ijiami.dat bytes directly — it may not
                // depend on state that l() sets up, so a failed l() shouldn't block it.
                System.out.println(">>> calling b2b regardless of N.l result (nOk=" + nOk + ") ...");
                System.out.println(">>> SINGLETON2 dispatch count before b2b: " + singleton2Hits[0]);
                try {
                    DvmObject<?> byteArray = new ByteArray(vm, ijiamiBytes);
                    DvmObject<?> b2bResult = N.callStaticJniMethodObject(emulator,
                            "b2b([BI)[B", byteArray, ijiamiBytes.length);
                    System.out.println(">>> SINGLETON2 dispatch count after b2b: " + singleton2Hits[0]);
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
                    System.out.println(">>> b2b threw: " + t);
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
