package com.xtv;

import com.github.unidbg.*;
import com.github.unidbg.arm.backend.Backend;
import com.github.unidbg.arm.backend.CodeHook;
import com.github.unidbg.arm.backend.Unicorn2Factory;
import com.github.unidbg.linux.android.*;
import com.github.unidbg.linux.android.dvm.*;
import com.github.unidbg.linux.android.dvm.array.ByteArray;
import com.github.unidbg.memory.Memory;
import unicorn.ArmConst;
import unicorn.UnicornConst;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Session 14 / iteration 51: remove bad wchan PC-skip (broke open syscall);
 * anti-tamper resumes to CTOR12_SKIP_OUT / JNI_RESUME (never sentinel LR);
 * soft-skip SIGKILL site 0x1203a2de.
 */
public class UnpackV50 extends AbstractJni {

    private static final long JNI_RESUME_PC = 0x1202e2baL;       // v7 best resume (post-BL in JNI_OnLoad)
    private static final long ANTI_TAMPER_PC = 0x1203725cL;
    private static final long CTOR12_ENTRY = 0x12037289L;
    private static final long CTOR12_SKIP_OUT = 0x1203732aL;     // guessed ctor epilogue (tune next run)
    private static final long SECONDARY_TRAP = 0x1202e4bbL;
    private static final long SECONDARY_RESUME = 0x1202e4c1L;
    /** After NOP+cbnz at 0x1203725c/5e — force fall-through past the tamper loop. */
    private static final long ANTI_TAMPER_FALLTHROUGH = 0x12037266L;

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
        if ("android/os/Build$VERSION->SDK_INT:I".equals(signature)) return 23;
        return super.getStaticIntField(vm, dvmClass, signature);
    }

    @Override
    public DvmObject<?> callStaticObjectMethodV(BaseVM vm, DvmClass dvmClass, String signature, VaList vaList) {
        switch (signature) {
            case "android/app/ActivityThread->currentPackageName()Ljava/lang/String;":
                return new StringObject(vm, "com.android.mgstv");
            case "android/app/ActivityThread->currentActivityThread()Landroid/app/ActivityThread;":
                return getMockActivityThread(vm);
            default:
                return super.callStaticObjectMethodV(vm, dvmClass, signature, vaList);
        }
    }

    @Override
    public DvmObject<?> callObjectMethod(BaseVM vm, DvmObject<?> dvmObject, String signature, VarArg varArg) {
        if ("java/lang/String->getBytes()[B".equals(signature)) {
            return new ByteArray(vm, ((StringObject) dvmObject).getValue().getBytes());
        }
        return super.callObjectMethod(vm, dvmObject, signature, varArg);
    }

    @Override
    public DvmObject<?> getObjectField(BaseVM vm, DvmObject<?> dvmObject, String signature) {
        switch (signature) {
            case "android/app/ActivityThread->mBoundApplication:Landroid/app/ActivityThread$AppBindData;":
                return getMockAppBindData(vm);
            case "android/app/ActivityThread$AppBindData->appInfo:Landroid/content/pm/ApplicationInfo;":
                return getMockApplicationInfo(vm);
            case "android/app/ActivityThread$AppBindData->info:Landroid/app/LoadedApk;":
                return vm.resolveClass("android/app/LoadedApk").newObject(null);
            case "android/app/ActivityThread$AppBindData->processName:Ljava/lang/String;":
                return new StringObject(vm, "com.android.mgstv");
            default:
                break;
        }
        if (signature.endsWith("Ljava/lang/String;")) {
            String fieldName = signature.substring(signature.lastIndexOf("->") + 2, signature.lastIndexOf(":"));
            String val = fieldName.contains("Dir") || fieldName.contains("Path")
                    ? "/data/app/com.android.mgstv-1"
                    : fieldName.equals("processName") ? "com.android.mgstv"
                    : fieldName.equals("className") ? "com.interactive.brasiliptv.app.AppWrapper"
                    : "/data/app/com.android.mgstv-1";
            return new StringObject(vm, val);
        }
        if (signature.contains(":L")) {
            String clsName = signature.substring(signature.lastIndexOf(":L") + 2, signature.length() - 1).replace('/', '.');
            return vm.resolveClass(clsName).newObject(null);
        }
        return super.getObjectField(vm, dvmObject, signature);
    }

    @Override
    public int getIntField(BaseVM vm, DvmObject<?> dvmObject, String signature) {
        if ("android/content/pm/ApplicationInfo->targetSdkVersion:I".equals(signature)) return 23;
        return super.getIntField(vm, dvmObject, signature);
    }

    private static void scanInterestingStrings(Backend backend, long start, long end) {
        System.out.println(">>> MEM-SCAN " + Long.toHexString(start) + ".." + Long.toHexString(end));
        final int chunk = 0x10000;
        List<String> hits = new ArrayList<>();
        for (long addr = start; addr < end; addr += chunk) {
            int len = (int) Math.min(chunk, end - addr);
            byte[] buf;
            try {
                buf = backend.mem_read(addr, len);
            } catch (Throwable t) {
                continue;
            }
            extractAscii(buf, addr, hits, ".com");
            extractAscii(buf, addr, hits, "domain");
            extractAscii(buf, addr, hits, "portal");
            extractAscii(buf, addr, hits, "get_notice");
            extractAscii(buf, addr, hits, "DES");
        }
        int shown = 0;
        for (String h : hits) {
            System.out.println(">>> MEM-HIT: " + h);
            if (++shown >= 40) break;
        }
        System.out.println(">>> MEM-SCAN total hits=" + hits.size());
    }

    private static void extractAscii(byte[] buf, long base, List<String> hits, String needle) {
        String s = new String(buf, StandardCharsets.ISO_8859_1);
        int idx = 0;
        while ((idx = s.indexOf(needle, idx)) >= 0) {
            int from = Math.max(0, idx - 24);
            int to = Math.min(s.length(), idx + 48);
            String slice = s.substring(from, to).replaceAll("[^\\x20-\\x7e]", ".");
            hits.add("0x" + Long.toHexString(base + from) + " \"" + slice + "\"");
            idx += needle.length();
        }
    }

    private static void installLegacyHooks(final Backend backend, final long scratch) {
        backend.hook_add_new(new CodeHook() {
            public void hook(Backend b, long address, int size, Object user) {
                long lr = backend.reg_read(ArmConst.UC_ARM_REG_LR).longValue();
                long retVal = (lr == 0x1203a1f3L) ? 1L : 0L;
                backend.reg_write(ArmConst.UC_ARM_REG_R0, retVal);
                backend.reg_write(ArmConst.UC_ARM_REG_PC, lr);
            }
            public void onAttach(com.github.unidbg.arm.backend.UnHook unHook) {}
            public void detach() {}
        }, 0x1202e5d4L, 0x1202e5d6L, null);

        backend.hook_add_new(new CodeHook() {
            public void hook(Backend b, long address, int size, Object user) {
                final long obj = 0x120923c0L;
                byte[] ptrBytes = new byte[] {
                        (byte) (scratch & 0xff), (byte) ((scratch >> 8) & 0xff),
                        (byte) ((scratch >> 16) & 0xff), (byte) ((scratch >> 24) & 0xff)
                };
                backend.mem_write(obj + 0x188, ptrBytes);
                backend.mem_write(obj + 0x109, new byte[] { 0 });
            }
            public void onAttach(com.github.unidbg.arm.backend.UnHook unHook) {}
            public void detach() {}
        }, 0x1203a1d8L, 0x1203a1daL, null);

        backend.hook_add_new(new CodeHook() {
            public void hook(Backend b, long address, int size, Object user) {
                long cpsr = backend.reg_read(ArmConst.UC_ARM_REG_CPSR).longValue();
                cpsr |= (1 << 29);
                cpsr &= ~(1 << 30);
                backend.reg_write(ArmConst.UC_ARM_REG_CPSR, cpsr);
            }
            public void onAttach(com.github.unidbg.arm.backend.UnHook unHook) {}
            public void detach() {}
        }, 0x1203a21cL, 0x1203a21eL, null);

        backend.hook_add_new(new CodeHook() {
            public void hook(Backend b, long address, int size, Object user) {
                backend.reg_write(ArmConst.UC_ARM_REG_R1, 1L);
            }
            public void onAttach(com.github.unidbg.arm.backend.UnHook unHook) {}
            public void detach() {}
        }, 0x1203a280L, 0x1203a282L, null);

        backend.hook_add_new(new CodeHook() {
            public void hook(Backend b, long address, int size, Object user) {
                long lr = backend.reg_read(ArmConst.UC_ARM_REG_LR).longValue();
                backend.reg_write(ArmConst.UC_ARM_REG_R0, 1L);
                backend.reg_write(ArmConst.UC_ARM_REG_PC, lr);
            }
            public void onAttach(com.github.unidbg.arm.backend.UnHook unHook) {}
            public void detach() {}
        }, 0x1207b5a0L, 0x1207b5a2L, null);

        // Soften SIGKILL from anti-debug (DumpWchan saw kill from 0x1203a2de)
        backend.hook_add_new(new CodeHook() {
            int n;
            public void hook(Backend b, long address, int size, Object user) {
                long lr = backend.reg_read(ArmConst.UC_ARM_REG_LR).longValue();
                long resume = (lr >= 0x12000000L && lr < 0x12200000L) ? lr : 0x1203a2e8L;
                backend.reg_write(ArmConst.UC_ARM_REG_R0, 0L);
                backend.reg_write(ArmConst.UC_ARM_REG_PC, resume);
                if (n++ < 8) {
                    System.out.println(">>> SKIP SIGKILL site@0x" + Long.toHexString(address)
                            + " -> PC=0x" + Long.toHexString(resume));
                }
            }
            public void onAttach(com.github.unidbg.arm.backend.UnHook unHook) {}
            public void detach() {}
        }, 0x1203a2deL, 0x1203a2e0L, null);
    }

    /** Approach 1: controlled PC jumps at known trap sites (no bx lr). */
    private static void installMultiLevelBypass(final Backend backend, final long[] lastLibLr,
                                                final boolean[] jniPhase) {
        backend.hook_add_new(new CodeHook() {
            public void hook(Backend b, long address, int size, Object user) {
                long lr = backend.reg_read(ArmConst.UC_ARM_REG_LR).longValue();
                if (lr >= 0x12000000L && lr < 0x12200000L && lr != 0xffff0000L) {
                    lastLibLr[0] = lr;
                }
            }
            public void onAttach(com.github.unidbg.arm.backend.UnHook unHook) {}
            public void detach() {}
        }, 0x1202e2b0L, 0x1202e2c4L, null);

        backend.hook_add_new(new CodeHook() {
            int n;
            public void hook(Backend b, long address, int size, Object user) {
                long lr = backend.reg_read(ArmConst.UC_ARM_REG_LR).longValue();
                long resume;
                // Never bx to unidbg sentinel — that re-enters init_array forever.
                boolean badLr = (lr == 0xffff0000L || lr == 0L || lr < 0x12000000L || lr >= 0x12200000L);
                if (jniPhase[0]) {
                    resume = (!badLr && lastLibLr[0] != 0) ? lastLibLr[0]
                            : (!badLr ? lr : JNI_RESUME_PC);
                } else {
                    // init_array: skip past anti-tamper body to guessed epilogue
                    resume = CTOR12_SKIP_OUT;
                }
                backend.reg_write(ArmConst.UC_ARM_REG_R2, 0L);
                backend.reg_write(ArmConst.UC_ARM_REG_R0, 0L);
                backend.reg_write(ArmConst.UC_ARM_REG_PC, resume);
                System.out.println(">>> BYPASS@0x1203725c #" + (++n) + " jni=" + jniPhase[0]
                        + " LR=0x" + Long.toHexString(lr) + " -> PC=0x" + Long.toHexString(resume));
            }
            public void onAttach(com.github.unidbg.arm.backend.UnHook unHook) {}
            public void detach() {}
        }, ANTI_TAMPER_PC, ANTI_TAMPER_PC, null);

        backend.hook_add_new(new CodeHook() {
            int n;
            public void hook(Backend b, long address, int size, Object user) {
                if (!jniPhase[0]) return;
                backend.reg_write(ArmConst.UC_ARM_REG_R0, 0L);
                backend.reg_write(ArmConst.UC_ARM_REG_PC, SECONDARY_RESUME);
                System.out.println(">>> BYPASS@0x1202e4bb #" + (++n) + " -> PC=0x" + Long.toHexString(SECONDARY_RESUME));
            }
            public void onAttach(com.github.unidbg.arm.backend.UnHook unHook) {}
            public void detach() {}
        }, SECONDARY_TRAP, SECONDARY_TRAP, null);

        final long[] extraTraps = {0x1202e4b8L, 0x1202e4bdL};
        final long[] extraResume = {0x1202e4c1L, 0x1202e4c5L};
        for (int i = 0; i < extraTraps.length; i++) {
            final long trap = extraTraps[i];
            final long resume = extraResume[i];
            backend.hook_add_new(new CodeHook() {
                int n;
                public void hook(Backend b, long address, int size, Object user) {
                    if (!jniPhase[0]) return;
                    backend.reg_write(ArmConst.UC_ARM_REG_PC, resume);
                    System.out.println(">>> BYPASS@0x" + Long.toHexString(trap) + " #" + (++n)
                            + " -> PC=0x" + Long.toHexString(resume));
                }
                public void onAttach(com.github.unidbg.arm.backend.UnHook unHook) {}
                public void detach() {}
            }, trap, trap, null);
        }
    }

    /** Approach 2: skip ctor[12] and nearby init_array entries in crash cluster. */
    private static void installCtorBlanket(final Backend backend) {
        final long[] ctorEntries = {
                CTOR12_ENTRY,
                0x12037288L,
                0x12037291L,
                0x120372a5L,
                0x120372b9L
        };
        for (long entry : ctorEntries) {
            backend.hook_add_new(new CodeHook() {
                int n;
                public void hook(Backend b, long address, int size, Object user) {
                    backend.reg_write(ArmConst.UC_ARM_REG_R0, 0L);
                    backend.reg_write(ArmConst.UC_ARM_REG_PC, CTOR12_SKIP_OUT);
                    System.out.println(">>> CTOR-SKIP@0x" + Long.toHexString(entry) + " #" + (++n));
                }
                public void onAttach(com.github.unidbg.arm.backend.UnHook unHook) {}
                public void detach() {}
            }, entry, entry, null);
        }
    }

    /** Approach 3: after map, patch decrypted site with NOPs (backend mem_write = uc path). */
    private static void patchAntiTamperSite(Backend backend) {
        try {
            byte[] before = backend.mem_read(ANTI_TAMPER_PC, 8);
            System.out.print(">>> PATCH before@0x1203725c: ");
            for (byte x : before) System.out.printf("%02x ", x & 0xff);
            System.out.println();
            byte[] nops = new byte[] {(byte) 0x00, (byte) 0xbf, (byte) 0x00, (byte) 0xbf,
                    (byte) 0x00, (byte) 0xbf, (byte) 0x00, (byte) 0x20};
            backend.mem_write(ANTI_TAMPER_PC, nops);
            byte[] after = backend.mem_read(ANTI_TAMPER_PC, 8);
            System.out.print(">>> PATCH after@0x1203725c: ");
            for (byte x : after) System.out.printf("%02x ", x & 0xff);
            System.out.println();
        } catch (Throwable t) {
            System.out.println(">>> PATCH failed: " + t);
        }
    }

    private final String mode;

    public UnpackV50(String mode) throws Exception {
        this.mode = mode;
        System.out.println(">>> UnpackV50 mode=" + mode);

        AndroidEmulator emulator = AndroidEmulatorBuilder.for32Bit()
                .setProcessName("com.android.mgstv")
                .addBackendFactory(new Unicorn2Factory(false))
                .build();
        System.out.println(">>> backend class=" + emulator.getBackend().getClass().getName());

        Memory memory = emulator.getMemory();
        memory.setLibraryResolver(new AndroidResolver(23));
        VM vm = emulator.createDalvikVM();
        vm.setVerbose(true);
        vm.setJni(this);

        final Backend backend = emulator.getBackend();
        final long scratch = 0x7f000000L;
        backend.mem_map(scratch, 0x1000, UnicornConst.UC_PROT_READ | UnicornConst.UC_PROT_WRITE);
        backend.mem_write(scratch, "com.android.mgstv\0".getBytes());

        final long[] lastLibLr = new long[1];
        final boolean[] jniPhase = new boolean[1];
        installLegacyHooks(backend, scratch);

        // Baseline-style: during init_array only, return from anti-tamper via bx-lr semantics
        // WITHOUT leaving a permanent bx-lr that loops on sentinel during JNI.
        final boolean[] initPatched = new boolean[1];
        backend.hook_add_new(new CodeHook() {
            public void hook(Backend b, long address, int size, Object user) {
                if (jniPhase[0]) return; // JNI handled by multi-level bypass
                long lr = backend.reg_read(ArmConst.UC_ARM_REG_LR).longValue();
                if (!initPatched[0]) {
                    initPatched[0] = true;
                    System.out.println(">>> INIT: anti-tamper hit LR=0x" + Long.toHexString(lr));
                }
                // End this init Function32 cleanly (same as baseline bx lr → sentinel)
                backend.reg_write(ArmConst.UC_ARM_REG_R0, 0L);
                backend.reg_write(ArmConst.UC_ARM_REG_PC, 0xffff0000L);
            }
            public void onAttach(com.github.unidbg.arm.backend.UnHook unHook) {}
            public void detach() {}
        }, ANTI_TAMPER_PC, ANTI_TAMPER_PC, null);

        if ("ctor".equals(mode)) {
            installCtorBlanket(backend);
        }

        File so = new File("/tmp/apkx/assets/ijm_lib/armeabi/libexec.so");
        System.out.println(">>> loading libexec.so ...");
        DalvikModule dm = null;
        try {
            dm = vm.loadLibrary(so, true);
            System.out.println(">>> loadLibrary base=0x" + Long.toHexString(dm.getModule().base));

            if ("patch".equals(mode)) {
                patchAntiTamperSite(backend);
            }

            // Install JNI-phase bypass ONLY after load (avoids breaking open(/proc/self/wchan))
            if (!"ctor".equals(mode)) {
                installMultiLevelBypass(backend, lastLibLr, jniPhase);
            }

            long sb = 0x120868f0L, sa = 0x120868e0L;
            backend.mem_write(sa, new byte[]{(byte) sb, (byte) (sb >> 8), (byte) (sb >> 16), (byte) (sb >> 24)});
            backend.mem_write(0x12082340L, new byte[]{(byte) sa, (byte) (sa >> 8), (byte) (sa >> 16), (byte) (sa >> 24)});

            System.out.println(">>> calling JNI_OnLoad ...");
            jniPhase[0] = true;
            dm.callJNI_OnLoad(emulator);
            System.out.println(">>> JNI_OnLoad OK");
        } catch (Throwable t) {
            System.out.println(">>> JNI_OnLoad failed:");
            t.printStackTrace(System.out);
        }

        scanInterestingStrings(backend, 0x12000000L, 0x120c0000L);
        scanInterestingStrings(backend, 0x7f000000L, 0x7f010000L);

        if (dm != null) {
            try {
                final long vtable = 0x7f001000L;
                final long singleton = 0x7f002000L;
                backend.mem_map(vtable, 0x1000, UnicornConst.UC_PROT_READ | UnicornConst.UC_PROT_WRITE);
                backend.mem_map(singleton, 0x1000, UnicornConst.UC_PROT_READ | UnicornConst.UC_PROT_WRITE);
                backend.mem_write(0x12082340L, le32(singleton));
                backend.mem_write(0x120868e0L, le32(vtable));

                byte[] ijiamiBytes = java.nio.file.Files.readAllBytes(
                        new File("/tmp/apkx/assets/ijiami.dat").toPath());
                DvmClass n = vm.resolveClass("s/h/e/l/l/N");
                DvmObject<?> app = vm.resolveClass("android/app/Application").newObject(null);

                boolean lResult = n.callStaticJniMethodBoolean(emulator,
                        "l(Landroid/app/Application;Ljava/lang/String;)Z",
                        app, "/data/app/com.android.mgstv-1/base.apk");
                System.out.println(">>> N.l=" + lResult);

                DvmObject<?> b2bResult = n.callStaticJniMethodObject(emulator,
                        "b2b([BI)[B", new ByteArray(vm, ijiamiBytes), ijiamiBytes.length);
                if (b2bResult instanceof ByteArray) {
                    byte[] dex = ((ByteArray) b2bResult).getValue();
                    System.out.println(">>> b2b len=" + dex.length + " magic="
                            + (dex.length >= 4 ? String.format("%02x%02x%02x%02x", dex[0], dex[1], dex[2], dex[3]) : "?"));
                    java.nio.file.Files.write(new File("/tmp/apkx/app_decrypted_v50.dex").toPath(), dex);
                } else {
                    System.out.println(">>> b2b result: " + b2bResult);
                }
            } catch (Throwable t) {
                System.out.println(">>> N.* failed:");
                t.printStackTrace(System.out);
            }
        }
    }

    private static byte[] le32(long v) {
        return new byte[]{(byte) v, (byte) (v >> 8), (byte) (v >> 16), (byte) (v >> 24)};
    }

    public static void main(String[] args) {
        String mode = (args.length > 0) ? args[0] : "multi";
        try {
            new UnpackV50(mode);
        } catch (Throwable t) {
            t.printStackTrace(System.out);
        }
        System.out.println(">>> done mode=" + mode);
    }
}
