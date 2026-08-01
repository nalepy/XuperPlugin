package mobile.com.requestframe.cloudstream;

import android.text.TextUtils;
import com.umeng.commonsdk.proguard.ap;
import java.security.SecureRandom;
import java.util.Locale;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.DESKeySpec;
import mobile.com.requestframe.cloudstream.bean.GetAddrResult;
import mobile.com.requestframe.cloudstream.response.SlbDesResult;
import mobile.com.requestframe.util.h;

/* loaded from: C:\Users\Nestor\AppData\Local\Temp\unitv\classes2.dex */
public class b {

    /* renamed from: d, reason: collision with root package name */
    private static final char[] f9284d = "0123456789ABCDEF".toCharArray();

    /* renamed from: e, reason: collision with root package name */
    private static final String f9285e = b.class.getSimpleName();

    /* renamed from: a, reason: collision with root package name */
    public static String f9281a = "==RiXVKU";

    /* renamed from: b, reason: collision with root package name */
    public static String f9282b = "dCsPLwiy";

    /* renamed from: c, reason: collision with root package name */
    public static String f9283c = "D#a!t-a&";

    /* renamed from: f, reason: collision with root package name */
    private static byte[] f9286f = {0, 0, 0, 0, 0, 0, 0, 0};

    public static String a(String str, String str2) {
        try {
            byte[] a2 = a(str.getBytes("utf-8"), str2);
            return a(a2, a2.length);
        } catch (Exception e2) {
            h.d(f9285e, e2.toString());
            return "";
        }
    }

    public static String a(GetAddrResult getAddrResult, String str) {
        String b2 = b(getAddrResult.getData(), str);
        h.a(f9285e, "解密后的数据 = " + b2);
        if (!TextUtils.isEmpty(b2)) {
            try {
                return b2.substring(0, b2.length() - ((8 - (getAddrResult.getLen().intValue() % 8)) % 8));
            } catch (Exception unused) {
                h.a(f9285e, "decryptStr: Exception ");
            }
        }
        return "";
    }

    public static String a(SlbDesResult slbDesResult, String str) {
        String str2 = "";
        String b2 = b(slbDesResult.getData(), str);
        h.a(f9285e, "解密后的数据 = " + b2);
        if (!TextUtils.isEmpty(b2)) {
            try {
                int length = b2.length() - ((8 - (slbDesResult.getLen() % 8)) % 8);
                if (length <= 0 || length > b2.length()) {
                    h.a(f9285e, "解密后的数据 decryptStr: index " + length);
                } else {
                    str2 = b2.substring(0, length);
                }
            } catch (Exception unused) {
                h.a(f9285e, "decryptStr: Exception ");
            }
        }
        return str2;
    }

    public static String a(byte[] bArr, int i) {
        StringBuilder sb = new StringBuilder();
        for (int i2 = 0; i2 < i; i2++) {
            sb.append(f9284d[(bArr[i2] & 255) >> 4]);
            sb.append(f9284d[bArr[i2] & ap.m]);
        }
        return sb.toString().trim().toUpperCase(Locale.US);
    }

    public static byte[] a(String str) {
        String upperCase = str.trim().replace(" ", "").toUpperCase(Locale.US);
        int length = upperCase.length() / 2;
        byte[] bArr = new byte[length];
        for (int i = 0; i < length; i++) {
            int i2 = i * 2;
            int i3 = i2 + 1;
            bArr[i] = (byte) (Integer.decode("0x" + upperCase.substring(i2, i3) + upperCase.substring(i3, i3 + 1)).intValue() & 255);
        }
        return bArr;
    }

    public static byte[] a(byte[] bArr, String str) {
        try {
            SecureRandom secureRandom = new SecureRandom();
            SecretKey generateSecret = SecretKeyFactory.getInstance("DES").generateSecret(new DESKeySpec(str.getBytes()));
            Cipher cipher = Cipher.getInstance("DES/ECB/PKCS5Padding");
            cipher.init(1, generateSecret, secureRandom);
            return cipher.doFinal(bArr);
        } catch (Throwable th) {
            h.d(f9285e, th.toString());
            return null;
        }
    }

    public static String b(String str, String str2) {
        String str3;
        try {
            str3 = new String(b(a(str), str2), "utf-8");
        } catch (Exception e2) {
            h.a(f9285e, "decryptStr: " + e2);
            str3 = "";
        }
        return str3;
    }

    public static byte[] b(byte[] bArr, String str) {
        SecureRandom secureRandom = new SecureRandom();
        SecretKey generateSecret = SecretKeyFactory.getInstance("DES").generateSecret(new DESKeySpec(str.getBytes()));
        Cipher cipher = Cipher.getInstance("DES/ECB/NoPadding");
        cipher.init(2, generateSecret, secureRandom);
        return cipher.doFinal(bArr);
    }
}
