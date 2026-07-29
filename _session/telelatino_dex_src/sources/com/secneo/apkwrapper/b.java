package com.secneo.apkwrapper;

import android.content.ComponentName;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.Process;
import android.os.RemoteException;
import com.secneo.apkwrapper.H;

/* loaded from: C:\Users\Nestor\Workspace\Xuper\XuperPlugin\_session\telelatino_dex\classes.dex */
class b implements ServiceConnection {
    b() {
    }

    /* JADX WARN: Unreachable blocks removed: 1, instructions: 1 */
    @Override // android.content.ServiceConnection
    public void onServiceConnected(ComponentName componentName, IBinder iBinder) throws Exception {
        try {
            int[] iArr = new int[0];
            try {
                if (H.a() == null) {
                    H.a(new Messenger(new H.a()));
                }
                if (H.b() == null) {
                    H.b(new Messenger(iBinder));
                }
                Message messageObtain = Message.obtain((Handler) null, 1000);
                messageObtain.arg1 = Process.myPid();
                messageObtain.replyTo = H.a();
                H.b().send(messageObtain);
            } catch (RemoteException unused) {
            }
        } catch (Exception ex1) {
            throw ex1;
        }
    }

    /* JADX WARN: Unreachable blocks removed: 1, instructions: 1 */
    @Override // android.content.ServiceConnection
    public void onServiceDisconnected(ComponentName componentName) throws Exception {
        try {
            int[] iArr = new int[0];
        } catch (Exception ex1) {
            throw ex1;
        }
    }
}
