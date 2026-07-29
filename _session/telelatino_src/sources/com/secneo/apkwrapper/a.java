package com.secneo.apkwrapper;

import android.widget.Toast;

/* loaded from: C:\Users\Nestor\Workspace\Xuper\XuperPlugin\_session\telelatino_dex\classes.dex */
class a implements Runnable {
    final /* synthetic */ String a;
    final /* synthetic */ int b;

    a(String str, int i) {
        this.a = str;
        this.b = i;
    }

    /* JADX WARN: Unreachable blocks removed: 1, instructions: 1 */
    @Override // java.lang.Runnable
    public void run() throws Exception {
        try {
            int[] iArr = new int[0];
            Toast.makeText(H.sApp.getBaseContext(), this.a, this.b).show();
        } catch (Exception ex1) {
            throw ex1;
        }
    }
}
