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
import java.io.File;
import java.io.FileOutputStream;

public class Unpack extends AbstractJni {

    // ---- Android runtime mocks ----
    // JNI_OnLoad walks ActivityThread -> mBoundApplication -> appInfo/info.
    // Provide minimal proxies so the native code doesn't crash on unmocked calls.

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

    // getObjectField: mock the ActivityThread → AppBindData → ApplicationInfo chain,
    // plus common fields the native JNI_OnLoad probes. For unmocked String-returning
    // fields, return plausible defaults instead of throwing — keeps the emulation
    // running through field-discovery loops without one-at-a-time whack-a-mole.
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
        // Fallback: if the return type is a String, supply a plausible value
        // instead of throwing. Log it so we know what the native code probed.
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
        // For object-typed fields, return a generic proxy
        if (signature.contains(":L")) {
            System.out.println(">>> MOCKED-FALLBACK object field signature=" + signature);
            String clsName = signature.substring(signature.lastIndexOf(":L") + 2,
                    signature.length() - 1).replace('/', '.');
            return vm.resolveClass(clsName).newObject(null);
        }
        System.out.println(">>> UNMOCKED getObjectField (no fallback) signature=" + signature);
        return super.getObjectField(vm, dvmObject, signature);
    }

    // getIntField: mock targetSdkVersion, etc on ApplicationInfo
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
        vm.setVerbose(true);  // capture RegisterNatives
        vm.setVerboseMethodOperation(true);
        vm.setVerboseFieldOperation(true);
        vm.setJni(this);

        final Backend backend = emulator.getBackend();

        final long SCRATCH = 0x7f000000L;
        backend.mem_map(SCRATCH, 0x1000, unicorn.UnicornConst.UC_PROT_READ | unicorn.UnicornConst.UC_PROT_WRITE);
        byte[] nameBytes = "com.android.mgstv\0".getBytes();
        backend.mem_write(SCRATCH, nameBytes);
        System.out.println(">>> scratch string ptr=0x"+Long.toHexString(SCRATCH));

        // Sanity function at 0x1202e5d4: when called from JNI_OnLoad (LR=0x1202e2b7),
        // return 0 ("OK"). When called from the gating ctor (LR=0x1203a1f3),
        // return NON-ZERO to force the ctor into the INITIALIZATION path
        // (which actually allocates and populates the singleton).
        backend.hook_add_new(new CodeHook() {
            public void hook(Backend backend2, long address, int size, Object user) {
                long lr = backend.reg_read(ArmConst.UC_ARM_REG_LR).longValue();
                long retVal;
                if (lr == 0x1203a1f3L) {
                    retVal = 1L; // force init path for ctor
                } else {
                    retVal = 0L; // "OK" for JNI_OnLoad (bypasses the kill trap there)
                }
                backend.reg_write(ArmConst.UC_ARM_REG_R0, retVal);
                backend.reg_write(ArmConst.UC_ARM_REG_PC, lr);
                System.out.println(">>> SANITY-HOOK@0x1202e5d4 -> return "+retVal+", LR=0x"+Long.toHexString(lr));
            }
            public void onAttach(com.github.unidbg.arm.backend.UnHook unHook) {}
            public void detach() {}
        }, 0x1202e5d4L, 0x1202e5d6L, null);

        // Patch the gating ctor's integrity checks so the initialization path succeeds:
        // 1. [object+0x188] = scratch (non-null process name)
        // 2. [object+0x109] = 1 (byte flag, prevents kill trap)
        // 3. Hook getpid() return to be > threshold (at 0x1203a218, r0 = getpid result)
        // 4. Opaque predicate at 0x1203a280: cbz r1 → force r1=1 (skip infinite loop)
        // 5. Function at 0x1207b5a0 call: force return 1 (at 0x1203a20c: cbz r0 → skip)
        backend.hook_add_new(new CodeHook() {
            public void hook(Backend backend2, long address, int size, Object user) {
                final long OBJ = 0x120923c0L;
                byte[] ptrBytes = new byte[] {
                        (byte) (SCRATCH & 0xff), (byte) ((SCRATCH >> 8) & 0xff),
                        (byte) ((SCRATCH >> 16) & 0xff), (byte) ((SCRATCH >> 24) & 0xff)
                };
                // Patch fields BEFORE integrity checks run
                backend.mem_write(OBJ + 0x188, ptrBytes);
                backend.mem_write(OBJ + 0x109, new byte[] { 0 });  // 0=skip final kill(-1,SIGABRT)
                // Hook bls at 0x1203a21e: force NOT taken (getpid <= threshold check)
                System.out.println(">>> CTOR-PATCH: +0x188->scratch, +0x109=0");
            }
            public void onAttach(com.github.unidbg.arm.backend.UnHook unHook) {}
            public void detach() {}
        }, 0x1203a1d8L, 0x1203a1daL, null);

        // At 0x1203a21c: cmp r0,r5 → set CPSR so bls NOT taken (C=1,Z=0)
        backend.hook_add_new(new CodeHook() {
            public void hook(Backend backend2, long address, int size, Object user) {
                // Clear Z and set C in CPSR (bls taken when C=0 or Z=1)
                long cpsr = backend.reg_read(ArmConst.UC_ARM_REG_CPSR).longValue();
                cpsr |= (1 << 29);  // set C flag
                cpsr &= ~(1 << 30); // clear Z flag
                backend.reg_write(ArmConst.UC_ARM_REG_CPSR, cpsr);
            }
            public void onAttach(com.github.unidbg.arm.backend.UnHook unHook) {}
            public void detach() {}
        }, 0x1203a21cL, 0x1203a21eL, null);

        // Hook: cbz r1 at 0x1203a280 → force r1=1 (opaque predicate passes)
        backend.hook_add_new(new CodeHook() {
            public void hook(Backend backend2, long address, int size, Object user) {
                backend.reg_write(ArmConst.UC_ARM_REG_R1, 1L);
                System.out.println(">>> HOOK: forced opaque-predicate r1=1");
            }
            public void onAttach(com.github.unidbg.arm.backend.UnHook unHook) {}
            public void detach() {}
        }, 0x1203a280L, 0x1203a282L, null);

        // Hook: function call at 0x1207b5a0 → force return 1
        backend.hook_add_new(new CodeHook() {
            public void hook(Backend backend2, long address, int size, Object user) {
                long lr = backend.reg_read(ArmConst.UC_ARM_REG_LR).longValue();
                backend.reg_write(ArmConst.UC_ARM_REG_R0, 1L);
                backend.reg_write(ArmConst.UC_ARM_REG_PC, lr);
                System.out.println(">>> HOOK: forced check-func@0x1207b5a0 return=1");
            }
            public void onAttach(com.github.unidbg.arm.backend.UnHook unHook) {}
            public void detach() {}
        }, 0x1207b5a0L, 0x1207b5a2L, null);

        // Hook: at 0x1203a20c cbz r0 — if this fires (r0=0 from check func), force skip
        backend.hook_add_new(new CodeHook() {
            public void hook(Backend backend2, long address, int size, Object user) {
                // Already handled by hooking the function itself
            }
            public void onAttach(com.github.unidbg.arm.backend.UnHook unHook) {}
            public void detach() {}
        }, 0x1203a20cL, 0x1203a20eL, null);

        // Write watches: catch who sets the singleton + GOT slot
        backend.hook_add_new(new WriteHook() {
            public void hook(Backend backend2, long address, int size, long value, Object user) {
                System.out.println(">>> WRITE-WATCH singleton addr=0x"+Long.toHexString(address)+
                        " size="+size+" value=0x"+Long.toHexString(value)+
                        " PC=0x"+Long.toHexString(backend.reg_read(ArmConst.UC_ARM_REG_PC).longValue()));
            }
            public void onAttach(com.github.unidbg.arm.backend.UnHook unHook) {}
            public void detach() {}
        }, 0x120868e0L, 0x120868e0L + 0x60, null);
        backend.hook_add_new(new WriteHook() {
            public void hook(Backend backend2, long address, int size, long value, Object user) {
                System.out.println(">>> WRITE-WATCH got_slot addr=0x"+Long.toHexString(address)+
                        " size="+size+" value=0x"+Long.toHexString(value)+
                        " PC=0x"+Long.toHexString(backend.reg_read(ArmConst.UC_ARM_REG_PC).longValue()));
            }
            public void onAttach(com.github.unidbg.arm.backend.UnHook unHook) {}
            public void detach() {}
        }, 0x12082340L, 0x12082340L + 8, null);

        System.out.println(">>> loading libexec.so ...");
        File so = new File("/tmp/apkx/assets/ijm_lib/armeabi/libexec.so");
        DalvikModule dm = null;
        try {
            dm = vm.loadLibrary(so, true);
            System.out.println(">>> loadLibrary returned base=0x"+Long.toHexString(dm.getModule().base));
            System.out.println(">>> calling JNI_OnLoad ...");
            dm.callJNI_OnLoad(emulator);
            System.out.println(">>> JNI_OnLoad returned JNI_VERSION_1_6 (success!)");
        } catch (Throwable t) {
            System.out.println(">>> loadLibrary/JNI_OnLoad threw: ");
            t.printStackTrace(System.out);
        }

        // Now call N.b2b() with ijiami.dat to decrypt the real DEX
        if (dm != null) {
            try {
                // Singleton force-write (must happen before any IJiami native calls)
                final long VTABLE = 0x7f001000L;
                final long SINGLETON = 0x7f002000L;
                backend.mem_map(VTABLE, 0x1000, unicorn.UnicornConst.UC_PROT_READ | unicorn.UnicornConst.UC_PROT_WRITE);
                backend.mem_map(SINGLETON, 0x1000, unicorn.UnicornConst.UC_PROT_READ | unicorn.UnicornConst.UC_PROT_WRITE);

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
                System.out.println(">>> FORCE-WROTE singleton@0x120868e0->vtable@0x7f001000, GOT->0x7f002000");

                System.out.println(">>> reading ijiami.dat ...");
                byte[] ijiamiBytes = java.nio.file.Files.readAllBytes(
                        new File("/tmp/apkx/assets/ijiami.dat").toPath());
                System.out.println(">>> ijiami.dat size="+ijiamiBytes.length);

                System.out.println(">>> resolving classes ...");
                DvmClass N = vm.resolveClass("s/h/e/l/l/N");

                // Use android.app.Application directly — unidbg has built-in
                // support for getAssets() on Application (AbstractJni handles it).
                // s.h.e.l.l.S extends Application but without DEX loaded unidbg
                // doesn't know the hierarchy, so CallObjectMethod fails.
                DvmClass AppClass = vm.resolveClass("android/app/Application");
                DvmObject<?> app = AppClass.newObject(null);
                System.out.println(">>> mock app: "+app);

                System.out.println(">>> calling N.l(Application, path) ...");
                boolean lResult = N.callStaticJniMethodBoolean(emulator,
                        "l(Landroid/app/Application;Ljava/lang/String;)Z",
                        app, "/data/app/com.android.mgstv-1/base.apk");
                System.out.println(">>> N.l returned: "+lResult);

                if (lResult) {
                    // If N.l succeeded, try b2b to decrypt
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
                } else {
                    System.out.println(">>> N.l returned false, trying b2b anyway...");
                    DvmObject<?> byteArray = new ByteArray(vm, ijiamiBytes);
                    DvmObject<?> b2bResult = N.callStaticJniMethodObject(emulator,
                            "b2b([BI)[B", byteArray, ijiamiBytes.length);
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
