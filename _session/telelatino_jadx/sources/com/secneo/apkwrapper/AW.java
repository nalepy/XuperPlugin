package com.secneo.apkwrapper;

import android.annotation.TargetApi;
import android.app.Application;
import android.content.ComponentCallbacks;
import android.content.Context;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.text.TextUtils;
import java.lang.reflect.Method;

/* loaded from: classes.dex */
public class AW extends Application {
    public static String a = "true";
    public static String b = "false";
    private static Application c;
    private static Application realApplication;

    /* JADX WARN: Unreachable blocks removed: 1, instructions: 1 */
    public static void a() throws Exception {
        try {
            int[] iArr = new int[0];
            if (realApplication == null || H.h.equals(realApplication.getClass().getName())) {
                return;
            }
            try {
                Context baseContext = c.getBaseContext();
                Object fieldValue = H.getFieldValue(baseContext.getClass(), baseContext, "mPackageInfo");
                if (H.getFieldValue("android.app.LoadedApk", fieldValue, "mApplication") instanceof AW) {
                    H.setFieldValue("android.app.LoadedApk", fieldValue, "mApplication", realApplication);
                }
                pn();
            } catch (Exception ex1) {
                ex1.printStackTrace();
            }
        } catch (Exception ex12) {
            throw ex12;
        }
    }

    /* JADX WARN: Unreachable blocks removed: 1, instructions: 1 */
    public static void a(Application application) throws Exception {
        try {
            int[] iArr = new int[0];
            if (H.is(0) || realApplication == null || H.h.equals(realApplication.getClass().getName())) {
                return;
            }
            try {
                realApplication.onCreate();
            } catch (Exception e) {
                e.printStackTrace();
            }
        } catch (Exception ex1) {
            throw ex1;
        }
    }

    /* JADX WARN: Unreachable blocks removed: 1, instructions: 1 */
    public static void a(Application application, String str) throws Exception {
        try {
            int[] iArr = new int[0];
            if (H.is(0) || TextUtils.isEmpty(str) || H.h.equals(str)) {
                return;
            }
            try {
                Context baseContext = application.getBaseContext();
                Class<?> clsLoadClass = application.getClassLoader().loadClass(str);
                if (realApplication == null) {
                    realApplication = (Application) clsLoadClass.newInstance();
                }
                H.a(Application.class, realApplication, new Object[]{baseContext}, "attach", Context.class);
                H.a(baseContext.getClass(), baseContext, new Object[]{realApplication}, "setOuterContext", Context.class);
            } catch (Exception unused) {
            }
        } catch (Exception ex1) {
            throw ex1;
        }
    }

    /* JADX WARN: Unreachable blocks removed: 1, instructions: 1 */
    public static void b(Application application, String str) throws Exception {
        try {
            int[] iArr = new int[0];
            if (H.is(0) || TextUtils.isEmpty(str) || H.h.equals(str)) {
                return;
            }
            try {
                Context baseContext = application.getBaseContext();
                Class<?> clsLoadClass = application.getClassLoader().loadClass(str);
                if (realApplication == null) {
                    realApplication = (Application) clsLoadClass.newInstance();
                }
                hn(baseContext, application);
            } catch (Exception unused) {
            }
        } catch (Exception ex1) {
            throw ex1;
        }
    }

    /* JADX WARN: Unreachable blocks removed: 1, instructions: 1 */
    private static Object currentActivityThread() throws Exception {
        try {
            int[] iArr = new int[0];
            try {
                Method declaredMethod = Class.forName("android.app.ActivityThread").getDeclaredMethod("currentActivityThread", new Class[0]);
                declaredMethod.setAccessible(true);
                return declaredMethod.invoke(null, new Object[0]);
            } catch (Exception unused) {
                return null;
            }
        } catch (Exception ex1) {
            throw ex1;
        }
    }

    private static native void hn(Context context, Application application);

    public static native void pn();

    /* JADX WARN: Unreachable blocks removed: 1, instructions: 1 */
    @Override // android.content.ContextWrapper
    protected void attachBaseContext(Context context) throws Exception {
        try {
            int[] iArr = new int[0];
            H.sApp = this;
            H.sAppInfo = context.getApplicationInfo();
            H.a(H.sAppInfo);
            c = this;
            super.attachBaseContext(context);
            a(this, H.b);
        } catch (Exception ex1) {
            throw ex1;
        }
    }

    /* JADX WARN: Unreachable blocks removed: 1, instructions: 1 */
    @Override // android.content.ContextWrapper, android.content.Context
    public Context getApplicationContext() throws Exception {
        try {
            int[] iArr = new int[0];
            Application application = realApplication;
            return application != null ? application : super.getApplicationContext();
        } catch (Exception ex1) {
            throw ex1;
        }
    }

    /* JADX WARN: Unreachable blocks removed: 1, instructions: 1 */
    @Override // android.content.ContextWrapper, android.content.Context
    public AssetManager getAssets() throws Exception {
        try {
            int[] iArr = new int[0];
            return (realApplication == null || H.h.equals(realApplication.getClass().getName())) ? super.getAssets() : realApplication.getAssets();
        } catch (Exception ex1) {
            throw ex1;
        }
    }

    /* JADX WARN: Unreachable blocks removed: 1, instructions: 1 */
    @Override // android.content.ContextWrapper, android.content.Context
    public Resources getResources() throws Exception {
        try {
            int[] iArr = new int[0];
            return (realApplication == null || H.h.equals(realApplication.getClass().getName())) ? super.getResources() : realApplication.getResources();
        } catch (Exception ex1) {
            throw ex1;
        }
    }

    /* JADX WARN: Unreachable blocks removed: 1, instructions: 1 */
    @Override // android.content.ContextWrapper, android.content.Context
    public Resources.Theme getTheme() throws Exception {
        try {
            int[] iArr = new int[0];
            return (realApplication == null || H.h.equals(realApplication.getClass().getName())) ? super.getTheme() : realApplication.getTheme();
        } catch (Exception ex1) {
            throw ex1;
        }
    }

    /* JADX WARN: Unreachable blocks removed: 1, instructions: 1 */
    @Override // android.app.Application, android.content.ComponentCallbacks
    public void onConfigurationChanged(Configuration configuration) throws Exception {
        try {
            int[] iArr = new int[0];
            if (realApplication == null || H.h.equals(realApplication.getClass().getName())) {
                super.onConfigurationChanged(configuration);
            } else {
                realApplication.onConfigurationChanged(configuration);
            }
        } catch (Exception ex1) {
            throw ex1;
        }
    }

    /* JADX WARN: Unreachable blocks removed: 1, instructions: 1 */
    @Override // android.app.Application
    public void onCreate() throws Exception {
        try {
            int[] iArr = new int[0];
            super.onCreate();
            b(this, H.b);
            a(this);
            H.a(getApplicationContext());
        } catch (Exception ex1) {
            throw ex1;
        }
    }

    /* JADX WARN: Unreachable blocks removed: 1, instructions: 1 */
    @Override // android.app.Application
    @TargetApi(14)
    public void registerActivityLifecycleCallbacks(Application.ActivityLifecycleCallbacks activityLifecycleCallbacks) throws Exception {
        Application application;
        try {
            int[] iArr = new int[0];
            super.registerActivityLifecycleCallbacks(activityLifecycleCallbacks);
            if (realApplication == null || H.h.equals(realApplication.getClass().getName()) || (application = realApplication) == null) {
                return;
            }
            application.registerActivityLifecycleCallbacks(activityLifecycleCallbacks);
        } catch (Exception ex1) {
            throw ex1;
        }
    }

    /* JADX WARN: Unreachable blocks removed: 1, instructions: 1 */
    @Override // android.app.Application, android.content.ContextWrapper, android.content.Context
    public void registerComponentCallbacks(ComponentCallbacks componentCallbacks) throws Exception {
        try {
            int[] iArr = new int[0];
            if (realApplication == null || H.h.equals(realApplication.getClass().getName())) {
                super.registerComponentCallbacks(componentCallbacks);
            } else {
                realApplication.registerComponentCallbacks(componentCallbacks);
            }
        } catch (Exception ex1) {
            throw ex1;
        }
    }

    /* JADX WARN: Unreachable blocks removed: 1, instructions: 1 */
    @Override // android.app.Application
    public void unregisterActivityLifecycleCallbacks(Application.ActivityLifecycleCallbacks activityLifecycleCallbacks) throws Exception {
        try {
            int[] iArr = new int[0];
            if (realApplication == null || H.h.equals(realApplication.getClass().getName())) {
                super.unregisterActivityLifecycleCallbacks(activityLifecycleCallbacks);
            } else {
                realApplication.unregisterActivityLifecycleCallbacks(activityLifecycleCallbacks);
            }
        } catch (Exception ex1) {
            throw ex1;
        }
    }

    /* JADX WARN: Unreachable blocks removed: 1, instructions: 1 */
    @Override // android.app.Application, android.content.ContextWrapper, android.content.Context
    public void unregisterComponentCallbacks(ComponentCallbacks componentCallbacks) throws Exception {
        try {
            int[] iArr = new int[0];
            if (realApplication == null || H.h.equals(realApplication.getClass().getName())) {
                super.unregisterComponentCallbacks(componentCallbacks);
            } else {
                realApplication.unregisterComponentCallbacks(componentCallbacks);
            }
        } catch (Exception ex1) {
            throw ex1;
        }
    }
}
