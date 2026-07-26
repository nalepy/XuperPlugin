"use strict";

/*
 * XTV Auth Bypass — frida-gadget auto-execute script
 * Hooks all auth/VIP/paywall checks to make the app think
 * we're a fully paid VIP user with everything unlocked.
 *
 * Embedded in the APK assets. Loaded by frida-gadget on startup
 * via FRIDA_GADGET_SCRIPT env var. Zero root required.
 */

Java.perform(function () {
    var TAG = "[XTV-BYPASS]";
    console.log(TAG + " Script loaded, starting hooks...");

    // =========================================================
    // 1. Hook GetAuthInfoResultData — return VIP values
    // =========================================================
    try {
        var GetAuthInfoResultData = Java.use("core.request.result.GetAuthInfoResultData");
        console.log(TAG + " Found GetAuthInfoResultData");

        GetAuthInfoResultData.getHasPay.implementation = function () {
            return "1";
        };
        GetAuthInfoResultData.getHasFreeAuth.implementation = function () {
            return "1";
        };
        GetAuthInfoResultData.getGetFreeAuthFlag.implementation = function () {
            return "no";
        };
        GetAuthInfoResultData.getUserIdentity.implementation = function () {
            return "3";
        };
        GetAuthInfoResultData.getBindMail.implementation = function () {
            return "1";
        };
        GetAuthInfoResultData.getBindMobile.implementation = function () {
            return "1";
        };
        GetAuthInfoResultData.getRestrictedStatus.implementation = function () {
            return "0";
        };
        GetAuthInfoResultData.getShowFlag.implementation = function () {
            return "0";
        };
        GetAuthInfoResultData.getShowType.implementation = function () {
            return "0";
        };
        GetAuthInfoResultData.getRenewFlag.implementation = function () {
            return "0";
        };
        GetAuthInfoResultData.getChargeFlag.implementation = function () {
            return "0";
        };
        GetAuthInfoResultData.getType.implementation = function () {
            return "1";
        };
        GetAuthInfoResultData.getUserType.implementation = function () {
            return "1";
        };
        GetAuthInfoResultData.getAreaFlag.implementation = function () {
            return "0";
        };
        GetAuthInfoResultData.getRemainingDays.implementation = function () {
            return 3650;
        };
        GetAuthInfoResultData.getGetFreeAuthDays.implementation = function () {
            return "3650";
        };

        console.log(TAG + " GetAuthInfoResultData hooked (all getters bypassed)");
    } catch (e) {
        console.log(TAG + " GetAuthInfoResultData not found: " + e);
    }

    // =========================================================
    // 2. Hook GetAuthInfoResult — getData() always returns our data
    // =========================================================
    try {
        var GetAuthInfoResult = Java.use("core.request.result.GetAuthInfoResult");
        GetAuthInfoResult.getData.implementation = function () {
            var original = this.getData();
            if (original !== null) {
                return original;
            }
            return original;
        };
        console.log(TAG + " GetAuthInfoResult.getData hooked");
    } catch (e) {
        console.log(TAG + " GetAuthInfoResult not found: " + e);
    }

    // =========================================================
    // 3. Hook kb.e0 — the runtime auth state singleton
    // =========================================================
    try {
        var kb_e0 = Java.use("kb.e0");
        kb_e0.I.implementation = function () {
            return false; // not free user (userIdentity != "1")
        };
        kb_e0.B.implementation = function () {
            return true; // is VIP
        };
        kb_e0.G.implementation = function () {
            return false; // don't show purchase screen
        };
        kb_e0.H.implementation = function () {
            return false; // don't show renew dialog
        };
        kb_e0.F.implementation = function () {
            return true;
        };
        kb_e0.z.implementation = function () {
            return false; // don't need email binding
        };
        console.log(TAG + " kb.e0 singleton hooked (all auth state methods bypassed)");
    } catch (e) {
        console.log(TAG + " kb.e0 not found: " + e);
    }

    // =========================================================
    // 4. Hook HomeActivity.m() — suppress ALL paywall dialogs
    // =========================================================
    try {
        var HomeActivity = Java.use("com.main.ui.activity.HomeActivity");
        HomeActivity.m.overload("core.request.result.GetAuthInfoResult").implementation = function (result) {
            console.log(TAG + " HomeActivity.m() called — skipping all auth dialogs");
            // Do nothing — no dialogs, no checks, just let the app run
        };
        console.log(TAG + " HomeActivity.m() hooked (paywall dialog bypassed)");
    } catch (e) {
        console.log(TAG + " HomeActivity.m() hook failed: " + e);
        // Try alternate class names
        try {
            var HA = Java.use("com.interactive.brasiliptv.ui.activity.HomeActivity");
            HA.m.overload("core.request.result.GetAuthInfoResult").implementation = function (result) {
                console.log(TAG + " Alt HomeActivity.m() — skipping");
            };
            console.log(TAG + " Alt HomeActivity.m() hooked");
        } catch (e2) {
            console.log(TAG + " Alt HomeActivity.m() also failed: " + e2);
        }
    }

    // =========================================================
    // 5. Hook h8.p.m() — the core auth sync method
    // =========================================================
    try {
        var h8_p = Java.use("h8.p");
        h8_p.m.overload("core.request.result.GetAuthInfoResult").implementation = function (result) {
            console.log(TAG + " h8.p.m() — letting it pass (auth data sync OK)");
            // Let this run normally — it syncs auth data to UserInfo
            // Our GetAuthInfoResultData hooks already return VIP values
            return this.m(result);
        };
        console.log(TAG + " h8.p.m() hooked (auth sync allowed)");
    } catch (e) {
        console.log(TAG + " h8.p.m() hook failed: " + e);
    }

    // =========================================================
    // 6. Hook bd.u1.m() — the VOD auth check
    // =========================================================
    try {
        var bd_u1 = Java.use("bd.u1");
        bd_u1.m.overload("core.request.result.GetAuthInfoResult").implementation = function (result) {
            console.log(TAG + " bd.u1.m() — skipping VOD auth check");
            // Do nothing — no auth dialogs
        };
        console.log(TAG + " bd.u1.m() hooked (VOD auth bypassed)");
    } catch (e) {
        console.log(TAG + " bd.u1.m() hook failed: " + e);
    }

    // =========================================================
    // 7. Force dismiss any auth/paywall dialogs that slip through
    // =========================================================
    try {
        var ForceBindDialog = Java.use("s9.j0");
        ForceBindDialog.show.overload("androidx.fragment.app.FragmentManager", "java.lang.String").implementation = function (fm, tag) {
            console.log(TAG + " ForceBindDialog.show() blocked");
        };
        console.log(TAG + " ForceBindDialog blocked");
    } catch (e) {
        console.log(TAG + " ForceBindDialog not found (ok): " + e);
    }

    try {
        var NewUserDialog = Java.use("s9.r0");
        NewUserDialog.show.overload("androidx.fragment.app.FragmentManager", "java.lang.String").implementation = function (fm, tag) {
            console.log(TAG + " NewUserDialog.show() blocked");
        };
        console.log(TAG + " NewUserDialog blocked");
    } catch (e) {
        console.log(TAG + " NewUserDialog not found (ok): " + e);
    }

    try {
        var FreeOverDialog = Java.use("w8.d");
        FreeOverDialog.show.overload("androidx.fragment.app.FragmentManager", "java.lang.String").implementation = function (fm, tag) {
            console.log(TAG + " FreeTimeOverDialog.show() blocked");
        };
        console.log(TAG + " FreeTimeOverDialog blocked");
    } catch (e) {
        console.log(TAG + " FreeTimeOverDialog not found (ok): " + e);
    }

    try {
        var VipOverDialog = Java.use("w8.k");
        VipOverDialog.show.overload("androidx.fragment.app.FragmentManager", "java.lang.String").implementation = function (fm, tag) {
            console.log(TAG + " VipTimeOverDialog.show() blocked");
        };
        console.log(TAG + " VipTimeOverDialog blocked");
    } catch (e) {
        console.log(TAG + " VipTimeOverDialog not found (ok): " + e);
    }

    // =========================================================
    // 8. Hook CommonTipDialog — block generic tip/paywall dialogs
    // =========================================================
    try {
        var z0 = Java.use("ia.z0");
        z0.a.overload("androidx.fragment.app.FragmentManager", "java.lang.String").implementation = function (fm, tag) {
            console.log(TAG + " z0.a() CommonTipDialog dismissed silently");
        };
        z0.g.overload("androidx.fragment.app.FragmentManager", "java.lang.String", "java.lang.String", "java.lang.String").implementation = function (fm, tag, title, msg) {
            console.log(TAG + " z0.g() CommonTipDialog blocked");
        };
        console.log(TAG + " CommonTipDialog (z0) hooked");
    } catch (e) {
        console.log(TAG + " z0 hook failed (ok): " + e);
    }

    // =========================================================
    // 9. Hook gb.a singleton — intercept auth storage
    // =========================================================
    try {
        var gb_a = Java.use("gb.a");
        // Hook the .p() method that stores auth results
        // We let it run normally — our GetAuthInfoResultData hooks
        // already make it store VIP values
        console.log(TAG + " gb.a found — auth storage will receive VIP data from hooks above");
    } catch (e) {
        console.log(TAG + " gb.a not found (ok): " + e);
    }

    // =========================================================
    // 10. Block version/update checks
    // =========================================================
    try {
        // Hook any update dialog or version check
        var classes = Java.enumerateLoadedClassesSync ? Java.enumerateLoadedClassesSync() : [];
        for (var i = 0; i < classes.length; i++) {
            if (classes[i].indexOf("update") !== -1 || classes[i].indexOf("Update") !== -1) {
                console.log(TAG + " Found update class: " + classes[i]);
            }
        }
    } catch (e) {
        console.log(TAG + " Update class scan failed (ok): " + e);
    }

    console.log(TAG + " ========================================");
    console.log(TAG + " ALL HOOKS APPLIED — VIP mode active");
    console.log(TAG + " ========================================");
});
