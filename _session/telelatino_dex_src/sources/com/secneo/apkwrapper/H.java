package com.secneo.apkwrapper;

import android.app.Application;
import android.content.Context;
import android.content.ServiceConnection;
import android.content.pm.ApplicationInfo;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/* loaded from: C:\Users\Nestor\Workspace\Xuper\XuperPlugin\_session\telelatino_dex\classes.dex */
public class H {
    public static Application sApp;
    public static ApplicationInfo sAppInfo;
    private static Boolean a = Boolean.FALSE;
    public static String PKGNAME = "com.global.latinotv";
    public static String b = "com.interactive.brasiliptv.app.AppWrapper";
    public static String c = "androidx.core.app.CoreComponentFactory";
    public static String d = "###SOPHIX###";
    public static String e = "###HAVEX86###";
    public static String f = "###HAVEX8664###";
    public static String g = "DexHelper-x86";
    public static String ARM_LIBRARY = "DexHelper";
    public static String h = "com.secneo.apkwrapper.AW";
    public static String PC_FLAG = "1000";
    private static Messenger i = null;
    private static Messenger j = null;
    private static final ServiceConnection sConnection = new b();

    public static class a extends Handler {
        /* JADX WARN: Unreachable blocks removed: 1, instructions: 1 */
        @Override // android.os.Handler
        public void handleMessage(Message message) throws Exception {
            try {
                int[] iArr = new int[0];
                if (message.what != 1000) {
                    super.handleMessage(message);
                    return;
                }
                try {
                    if (H.sApp != null) {
                        H.us(H.sApp.getApplicationContext());
                    }
                } catch (Throwable unused) {
                }
                H.c(message.arg2);
            } catch (Exception ex1) {
                throw ex1;
            }
        }
    }

    /* JADX WARN: Unreachable blocks removed: 2, instructions: 2 */
    public static int a(int i2) throws Exception {
        try {
            int[] iArr = new int[0];
            try {
                BufferedReader bufferedReader = new BufferedReader(new FileReader("/proc/self/status"));
                while (true) {
                    try {
                        String line = bufferedReader.readLine();
                        if (line == null) {
                            bufferedReader.close();
                            return 0;
                        }
                        if (i2 == 0) {
                            if (line.startsWith("TracerPid:")) {
                                int i3 = Integer.parseInt(line.substring(10).trim());
                                bufferedReader.close();
                                return i3;
                            }
                        } else if (i2 == 1 && line.startsWith("PPid:")) {
                            int i4 = Integer.parseInt(line.substring(5).trim());
                            bufferedReader.close();
                            return i4;
                        }
                    } finally {
                    }
                }
            } catch (IOException e2) {
                Exception ex1 = new RuntimeException("read tracer", e2);
                throw ex1;
            }
        } catch (Exception ex12) {
            throw ex12;
        }
    }

    /* JADX WARN: Unreachable blocks removed: 1, instructions: 1 */
    public static int a(byte[] bArr) throws Exception {
        try {
            int[] iArr = new int[0];
            return bArr[3] | ((((((0 | bArr[0]) << 8) | bArr[1]) << 8) | bArr[2]) << 8);
        } catch (Exception ex1) {
            throw ex1;
        }
    }

    /* JADX WARN: Unreachable blocks removed: 1, instructions: 1 */
    static /* synthetic */ Messenger a() throws Exception {
        try {
            int[] iArr = new int[0];
            return j;
        } catch (Exception ex1) {
            throw ex1;
        }
    }

    /* JADX WARN: Unreachable blocks removed: 1, instructions: 1 */
    static /* synthetic */ Messenger a(Messenger messenger) throws Exception {
        try {
            int[] iArr = new int[0];
            j = messenger;
            return messenger;
        } catch (Exception ex1) {
            throw ex1;
        }
    }

    /* JADX WARN: Unreachable blocks removed: 1, instructions: 1 */
    public static Object a(Class<?> cls, Object obj, Object[] objArr, String str, Class<?>... clsArr) throws Exception {
        try {
            int[] iArr = new int[0];
            try {
                Method declaredMethod = cls.getDeclaredMethod(str, clsArr);
                declaredMethod.setAccessible(true);
                return declaredMethod.invoke(obj, objArr);
            } catch (IllegalAccessException e2) {
                e2.printStackTrace();
                return null;
            } catch (IllegalArgumentException e3) {
                e3.printStackTrace();
                return null;
            } catch (NoSuchMethodException e4) {
                e4.printStackTrace();
                return null;
            } catch (InvocationTargetException e5) {
                e5.printStackTrace();
                return null;
            }
        } catch (Exception ex1) {
            throw ex1;
        }
    }

    /* JADX WARN: Unreachable blocks removed: 1, instructions: 1 */
    public static void a(Context context) throws Exception {
        try {
            int[] iArr = new int[0];
            try {
                bs(context, Integer.parseInt(PC_FLAG));
            } catch (Throwable unused) {
            }
        } catch (Exception ex1) {
            throw ex1;
        }
    }

    /* JADX WARN: Unreachable blocks removed: 2, instructions: 2 */
    public static void a(ApplicationInfo applicationInfo) throws Exception {
        try {
            int[] iArr = new int[0];
            synchronized (a) {
                if (!a.booleanValue()) {
                    try {
                        System.loadLibrary(c() ? g : ARM_LIBRARY);
                    } catch (Throwable unused) {
                        b(applicationInfo);
                    }
                    a = Boolean.TRUE;
                }
            }
        } catch (Exception ex1) {
            throw ex1;
        }
    }

    /* JADX WARN: Unreachable blocks removed: 1, instructions: 1 */
    public static int b(int i2) throws Exception {
        try {
            int[] iArr = new int[0];
            try {
                FileInputStream fileInputStream = new FileInputStream("/system/bin/app_process");
                Throwable th = null;
                try {
                    fileInputStream.read();
                    fileInputStream.close();
                } finally {
                }
            } catch (IOException unused) {
            }
            SystemClock.sleep(2000L);
            return a(i2);
        } catch (Exception ex1) {
            throw ex1;
        }
    }

    /* JADX WARN: Unreachable blocks removed: 1, instructions: 1 */
    static /* synthetic */ Messenger b() throws Exception {
        try {
            int[] iArr = new int[0];
            return i;
        } catch (Exception ex1) {
            throw ex1;
        }
    }

    /* JADX WARN: Unreachable blocks removed: 1, instructions: 1 */
    static /* synthetic */ Messenger b(Messenger messenger) throws Exception {
        try {
            int[] iArr = new int[0];
            i = messenger;
            return messenger;
        } catch (Exception ex1) {
            throw ex1;
        }
    }

    /* JADX WARN: Removed duplicated region for block: B:75:0x0130 A[Catch: Exception -> 0x0139, TryCatch #3 {Exception -> 0x0139, blocks: (B:21:0x004c, B:23:0x0055, B:25:0x0071, B:27:0x008d, B:28:0x009f, B:47:0x00fc, B:48:0x00ff, B:71:0x0128, B:75:0x0130, B:77:0x0135, B:78:0x0138, B:65:0x0119, B:31:0x00ab, B:33:0x00c7, B:68:0x011f), top: B:81:0x004c, inners: #8 }] */
    /* JADX WARN: Removed duplicated region for block: B:77:0x0135 A[Catch: Exception -> 0x0139, TryCatch #3 {Exception -> 0x0139, blocks: (B:21:0x004c, B:23:0x0055, B:25:0x0071, B:27:0x008d, B:28:0x009f, B:47:0x00fc, B:48:0x00ff, B:71:0x0128, B:75:0x0130, B:77:0x0135, B:78:0x0138, B:65:0x0119, B:31:0x00ab, B:33:0x00c7, B:68:0x011f), top: B:81:0x004c, inners: #8 }] */
    /* JADX WARN: Unreachable blocks removed: 1, instructions: 1 */
    /*
        Code decompiled incorrectly, please refer to instructions dump.
    */
    public static void b(ApplicationInfo applicationInfo) throws Exception {
        int iD;
        ZipEntry entry;
        StringBuilder sb;
        InputStream inputStream;
        FileOutputStream fileOutputStream;
        try {
            int[] iArr = new int[0];
            if (Build.VERSION.SDK_INT >= 19 && (iD = d()) != 0) {
                String str = applicationInfo.sourceDir;
                File file = new File(applicationInfo.dataDir, ".cache");
                if (!file.exists()) {
                    file.mkdirs();
                }
                File file2 = new File(file, "lib" + g + ".so");
                try {
                    ZipFile zipFile = new ZipFile(str);
                    InputStream inputStream2 = null;
                    if (iD == 1) {
                        entry = zipFile.getEntry("lib/armeabi-v7a/lib" + g + ".so");
                        if (entry == null) {
                            entry = zipFile.getEntry("lib/armeabi/lib" + g + ".so");
                        }
                        if (entry == null) {
                            sb = new StringBuilder();
                            sb.append("lib/x86/lib");
                            sb.append(ARM_LIBRARY);
                            sb.append(".so");
                            entry = zipFile.getEntry(sb.toString());
                        }
                    } else if (iD == 2) {
                        entry = zipFile.getEntry("lib/arm64-v8a/lib" + g + ".so");
                        if (entry == null) {
                            sb = new StringBuilder();
                            sb.append("lib/x86_64/lib");
                            sb.append(ARM_LIBRARY);
                            sb.append(".so");
                            entry = zipFile.getEntry(sb.toString());
                        }
                    } else {
                        entry = null;
                    }
                    if (entry == null) {
                        return;
                    }
                    try {
                        inputStream = zipFile.getInputStream(entry);
                        try {
                            fileOutputStream = new FileOutputStream(file2);
                            try {
                                byte[] bArr = new byte[4096];
                                while (true) {
                                    int i2 = inputStream.read(bArr);
                                    if (i2 == -1) {
                                        break;
                                    } else {
                                        fileOutputStream.write(bArr, 0, i2);
                                    }
                                }
                                fileOutputStream.flush();
                                if (inputStream != null) {
                                    inputStream.close();
                                }
                            } catch (Exception e2) {
                                e = e2;
                                inputStream2 = inputStream;
                                try {
                                    e.printStackTrace();
                                    if (inputStream2 != null) {
                                        inputStream2.close();
                                    }
                                    if (fileOutputStream != null) {
                                        fileOutputStream.close();
                                    }
                                    sl(file2.getAbsolutePath());
                                } catch (Throwable th) {
                                    th = th;
                                    inputStream = inputStream2;
                                    if (inputStream != null) {
                                        inputStream.close();
                                    }
                                    if (fileOutputStream != null) {
                                        fileOutputStream.close();
                                    }
                                    throw th;
                                }
                            } catch (Throwable th2) {
                                th = th2;
                                if (inputStream != null) {
                                }
                                if (fileOutputStream != null) {
                                }
                                throw th;
                            }
                        } catch (Exception e3) {
                            e = e3;
                            fileOutputStream = null;
                        } catch (Throwable th3) {
                            th = th3;
                            fileOutputStream = null;
                        }
                    } catch (Exception e4) {
                        e = e4;
                        fileOutputStream = null;
                    } catch (Throwable th4) {
                        th = th4;
                        inputStream = null;
                        fileOutputStream = null;
                    }
                    fileOutputStream.close();
                    try {
                        sl(file2.getAbsolutePath());
                    } catch (Throwable th5) {
                        th5.printStackTrace();
                    }
                } catch (Exception unused) {
                }
            }
        } catch (Exception ex1) {
            throw ex1;
        }
    }

    public static native void bla(String str);

    public static native void blc();

    public static native boolean bli(String str);

    public static native boolean blq(String str);

    public static native void blr(String str);

    public static native boolean bls(long j2);

    public static native long blv();

    private static native Object bs(Context context, int i2);

    /* JADX WARN: Unreachable blocks removed: 1, instructions: 1 */
    static /* synthetic */ void c(int i2) throws Exception {
        try {
            int[] iArr = new int[0];
            he(i2);
        } catch (Exception ex1) {
            throw ex1;
        }
    }

    /* JADX WARN: Unreachable blocks removed: 1, instructions: 1 */
    public static boolean c() throws Exception {
        try {
            int[] iArr = new int[0];
            boolean zEqualsIgnoreCase = e.equalsIgnoreCase("true");
            boolean zEqualsIgnoreCase2 = f.equalsIgnoreCase("true");
            int iD = d();
            if (iD != 1 || zEqualsIgnoreCase) {
                return iD == 2 && !zEqualsIgnoreCase2;
            }
            return true;
        } catch (Exception ex1) {
            throw ex1;
        }
    }

    public static native int cis(int i2);

    /* JADX WARN: Unreachable blocks removed: 1, instructions: 1 */
    private static int d() throws Exception {
        FileInputStream fileInputStream;
        try {
            int[] iArr = new int[0];
            byte[] bArr = new byte[20];
            int i2 = 0;
            try {
                fileInputStream = new FileInputStream("/proc/self/exe");
                try {
                    fileInputStream.read(bArr);
                    int iA = a(new byte[]{0, 0, bArr[19], bArr[18]});
                    if (iA == 3 || iA == 6 || iA == 7) {
                        i2 = 1;
                    } else if (iA == 62) {
                        i2 = 2;
                    }
                } catch (Exception unused) {
                    if (fileInputStream != null) {
                        fileInputStream.close();
                    }
                    return i2;
                } catch (Throwable th) {
                    ex1 = th;
                    if (fileInputStream != null) {
                        try {
                            fileInputStream.close();
                        } catch (IOException unused2) {
                        }
                    }
                    throw ex1;
                }
            } catch (Exception unused3) {
                fileInputStream = null;
            } catch (Throwable th2) {
                ex1 = th2;
                fileInputStream = null;
            }
            try {
                fileInputStream.close();
            } catch (IOException unused4) {
            }
            return i2;
        } catch (Exception ex1) {
            throw ex1;
        }
    }

    public static native String d(String str);

    public static native Object[] gah();

    /* JADX WARN: Unreachable blocks removed: 1, instructions: 1 */
    public static Object getFieldValue(Class<?> cls, Object obj, String str) throws Exception {
        try {
            int[] iArr = new int[0];
            try {
                Field declaredField = cls.getDeclaredField(str);
                declaredField.setAccessible(true);
                return declaredField.get(obj);
            } catch (IllegalAccessException e2) {
                e2.printStackTrace();
                return null;
            } catch (IllegalArgumentException e3) {
                e3.printStackTrace();
                return null;
            } catch (NoSuchFieldException e4) {
                e4.printStackTrace();
                return null;
            }
        } catch (Exception ex1) {
            throw ex1;
        }
    }

    /* JADX WARN: Unreachable blocks removed: 1, instructions: 1 */
    public static Object getFieldValue(String str, Object obj, String str2) throws Exception {
        try {
            int[] iArr = new int[0];
            try {
                return getFieldValue(Class.forName(str), obj, str2);
            } catch (ClassNotFoundException e2) {
                e2.printStackTrace();
                return null;
            } catch (IllegalArgumentException e3) {
                e3.printStackTrace();
                return null;
            }
        } catch (Exception ex1) {
            throw ex1;
        }
    }

    public static native int gha(String str);

    public static native long ghc(String str);

    public static native int gv();

    private static native void he(int i2);

    public static native boolean is(int i2);

    /* JADX WARN: Unreachable blocks removed: 1, instructions: 1 */
    public static void main(String[] strArr) throws Exception {
        ParcelFileDescriptor parcelFileDescriptorAdoptFd;
        try {
            int[] iArr = new int[0];
            if (strArr.length != 4) {
                System.exit(1);
            }
            int i2 = 0;
            try {
                parcelFileDescriptorAdoptFd = ParcelFileDescriptor.adoptFd(Integer.parseInt(strArr[1]));
                try {
                    i2 = Integer.parseInt(strArr[3]);
                } catch (Exception unused) {
                    System.exit(1);
                    int iB = b(i2);
                    DataOutputStream dataOutputStream = new DataOutputStream(new ParcelFileDescriptor.AutoCloseOutputStream(parcelFileDescriptorAdoptFd));
                    try {
                        dataOutputStream.writeUTF(Integer.toString(iB));
                        dataOutputStream.close();
                    } finally {
                    }
                }
            } catch (Exception unused2) {
                parcelFileDescriptorAdoptFd = null;
            }
            try {
                int iB2 = b(i2);
                DataOutputStream dataOutputStream2 = new DataOutputStream(new ParcelFileDescriptor.AutoCloseOutputStream(parcelFileDescriptorAdoptFd));
                dataOutputStream2.writeUTF(Integer.toString(iB2));
                dataOutputStream2.close();
            } catch (Throwable unused3) {
                System.exit(1);
            }
        } catch (Exception ex1) {
            throw ex1;
        }
    }

    /* JADX WARN: Unreachable blocks removed: 1, instructions: 1 */
    public static boolean setFieldValue(Class<?> cls, Object obj, String str, Object obj2) throws Exception {
        try {
            int[] iArr = new int[0];
            try {
                Field declaredField = cls.getDeclaredField(str);
                declaredField.setAccessible(true);
                declaredField.set(obj, obj2);
                return true;
            } catch (IllegalAccessException e2) {
                e2.printStackTrace();
                return false;
            } catch (IllegalArgumentException e3) {
                e3.printStackTrace();
                return false;
            } catch (NoSuchFieldException e4) {
                e4.printStackTrace();
                return false;
            }
        } catch (Exception ex1) {
            throw ex1;
        }
    }

    /* JADX WARN: Unreachable blocks removed: 1, instructions: 1 */
    public static boolean setFieldValue(String str, Object obj, String str2, Object obj2) throws Exception {
        try {
            int[] iArr = new int[0];
            try {
                setFieldValue(Class.forName(str), obj, str2, obj2);
                return true;
            } catch (ClassNotFoundException e2) {
                e2.printStackTrace();
                return false;
            } catch (IllegalArgumentException e3) {
                e3.printStackTrace();
                return false;
            }
        } catch (Exception ex1) {
            throw ex1;
        }
    }

    public static native void sha(String str, int i2);

    /* JADX WARN: Unreachable blocks removed: 1, instructions: 1 */
    public static void showToast(String str, int i2) throws Exception {
        try {
            int[] iArr = new int[0];
            Application application = sApp;
            if (application == null || application.getBaseContext() == null) {
                return;
            }
            new Handler(Looper.getMainLooper()).post(new com.secneo.apkwrapper.a(str, i2));
        } catch (Exception ex1) {
            throw ex1;
        }
    }

    private static native void sl(String str);

    public static native int sn(String str);

    public static native void us(Context context);
}
