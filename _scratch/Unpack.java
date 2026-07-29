package com.xtv;
import com.github.unidbg.*;
import com.github.unidbg.arm.backend.Backend;
import com.github.unidbg.arm.backend.Unicorn2Factory;
import com.github.unidbg.arm.backend.CodeHook;
import com.github.unidbg.arm.backend.WriteHook;
import com.github.unidbg.linux.android.*;
import com.github.unidbg.linux.android.dvm.*;
import com.github.unidbg.linux.android.dvm.array.ByteArray;
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
        byte[] nameBytes = "com.android.mgstv\0".getBytes();
        backend.mem_write(SCRATCH, nameBytes);
        System.out.println(">>> scratch string ptr=0x"+Long.toHexString(SCRATCH));

        // ---- APPROACH J: permanent bx lr patch at crash site ----
        // Write bx lr (0x4770) at 0x1203725c BEFORE library loads.
        // Any code path reaching here returns immediately (via bx lr -> unidbg sentinel 0xffff0000).
        // No hook needed — the patch is permanent in emulated memory.
        byte[] bxLr = new byte[]{(byte)0x70, (byte)0x47};  // bx lr (Thumb)
        // Can't write yet — memory not mapped. Will write after loadLibrary maps the region.
        // Instead, use a WriteHook to patch on first write to the code page, OR
        // use a one-shot CodeHook that writes bx lr then unhooks.
        final boolean[] patched = new boolean[1];
        backend.hook_add_new(new CodeHook() {
            public void hook(Backend b, long addr, int sz, Object u) {
                if (!patched[0]) {
                    b.mem_write(0x1203725cL, new byte[]{(byte)0x70, (byte)0x47});
                    patched[0] = true;
                    System.out.println(">>> PERMANENT: wrote bx lr at 0x1203725c");
                    // Don't resume — let bx lr execute via PC=0x1203725c
                    b.reg_write(ArmConst.UC_ARM_REG_PC, 0x1203725cL);
                } else {
                    // Already patched, just let bx lr execute (it's already there)
                    b.reg_write(ArmConst.UC_ARM_REG_PC, 0x1203725cL);
                }
            }
            public void onAttach(com.github.unidbg.arm.backend.UnHook u) {}
            public void detach() {}
        }, 0x1203725cL, 0x1203725cL, null);
        System.out.println(">>> PERMANENT: one-shot bx lr patcher at 0x1203725c");

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
            System.out.println(">>> calling JNI_OnLoad ...");
            long sb = 0x120868f0L, sa = 0x120868e0L;
            backend.mem_write(sa, new byte[]{(byte)sb,(byte)(sb>>8),(byte)(sb>>16),(byte)(sb>>24)});
            backend.mem_write(0x12082340L, new byte[]{(byte)sa,(byte)(sa>>8),(byte)(sa>>16),(byte)(sa>>24)});
            System.out.println(">>> FIX: GOT->singleton->buf");
            dm.callJNI_OnLoad(emulator);
            System.out.println(">>> JNI_OnLoad returned JNI_VERSION_1_6 (SUCCESS!)");
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
