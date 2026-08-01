// capture_dohttpsec2.js — anti-detection + NativeJni DoHttpSec capture for mgstv on Android 7.1.2.
// Phase 1: block ijiami's anti-frida (ptrace, /proc scans, exit/kill) — before app code runs.
// Phase 2: find NativeJni in the REAL classloader (ijiami custom loader) and hook the DoHttpSec
//          method `a` — dump plaintext JSON spec (url/headers/body incl b29/reserve1/userToken)
//          and the DECRYPTED response. Also hook qd.a.a.k (token setter) + qd.a.a.d/h (getters).
// Output: console + /data/local/tmp/dohttp_cap.log (persists even if the app kills itself).

var TAG = "[DHC]";
var LOGPATH = "/data/local/tmp/dohttp_cap.log";

function log(msg) {
    try { console.log(msg); send(msg); } catch (e) {}
}
function fappend(text) {
    try {
        var f = new File(LOGPATH, "a");
        f.write(text + "\n");
        f.flush();
        f.close();
    } catch (e) {}
}
function jstr(j) {
    if (j === null || j === undefined) return "null";
    try { return j.toString(); } catch (e) { return "<unprintable>"; }
}

// ================= PHASE 1: anti-detection =================
function findExp(name) {
    try { return Module.getGlobalExportByName(name); } catch (e) {}
    return null;
}
try {
    var ptrace = findExp("ptrace");
    if (ptrace) {
        Interceptor.attach(ptrace, {
            onEnter: function (args) { this.req = args[0].toInt32(); },
            onLeave: function (rv) { if (this.req === 0) rv.replace(ptr(0)); }
        });
        log(TAG + " ptrace hooked");
    }
} catch (e) { log(TAG + " ptrace fail " + e); }

["fopen", "open", "open64", "openat", "openat64"].forEach(function (fn) {
    var a = findExp(fn);
    if (!a) return;
    try {
        Interceptor.attach(a, {
            onEnter: function (args) {
                try {
                    var p = args[0].readCString();
                    if (!p && fn.indexOf("openat") !== -1) p = args[1].readCString();
                    if (p && p.indexOf("/proc/") !== -1 &&
                        (p.indexOf("status") !== -1 || p.indexOf("maps") !== -1 ||
                         p.indexOf("task") !== -1 || p.indexOf("stat") !== -1 ||
                         p.indexOf("wchan") !== -1)) {
                        this.block = true;
                        if (fn.indexOf("openat") !== -1) {
                            args[1] = Memory.allocUtf8String("/dev/null");
                        } else {
                            args[0] = Memory.allocUtf8String("/dev/null");
                        }
                    }
                } catch (e) {}
            }
        });
    } catch (e) {}
});
log(TAG + " proc-scan fds hooked (incl openat)");

try {
    ["access", "faccessat"].forEach(function (fn) {
        var a = findExp(fn);
        if (!a) return;
        Interceptor.attach(a, {
            onEnter: function (args) {
                try {
                    var p = args[0].readCString();
                    if (!p && fn === "faccessat") p = args[1].readCString();
                    if (p && p.indexOf("/proc/") !== -1 && p.indexOf("status") !== -1) {
                        this.block = true;
                    }
                } catch (e) {}
            },
            onLeave: function (rv) {
                if (this.block) rv.replace(ptr(-1));
            }
        });
    });
    log(TAG + " access hooks");
} catch (e) {}

try {
    var strstr = findExp("strstr");
    if (strstr) {
        Interceptor.attach(strstr, {
            onEnter: function (args) {
                try {
                    var n = args[1].readCString();
                    if (n && (n.indexOf("frida") !== -1 || n.indexOf("gum-js") !== -1 ||
                              n.indexOf("linjector") !== -1 || n.indexOf("xposed") !== -1 ||
                              n.indexOf("libexec") !== -1)) {
                        this.block = true;
                        args[1] = Memory.allocUtf8String("zz_notfound_zz");
                    }
                } catch (e) {}
            },
            onLeave: function (rv) { if (this.block) rv.replace(ptr(0)); }
        });
        log(TAG + " strstr hooked");
    }
} catch (e) {}

["exit", "_exit", "exit_group"].forEach(function (fn) {
    var a = findExp(fn);
    if (!a) return;
    try {
        Interceptor.attach(a, {
            onEnter: function (args) {
                log(TAG + " BLOCKED " + fn + "(" + args[0].toInt32() + ")");
                args[0] = ptr(0);
            }
        });
    } catch (e) {}
});

try {
    var kill = findExp("kill");
    if (kill) {
        Interceptor.attach(kill, {
            onEnter: function (args) {
                var sig = args[1].toInt32();
                if (sig === 9 || sig === 6) {
                    log(TAG + " BLOCKED kill(sig=" + sig + ")");
                    args[1] = ptr(0);
                }
            }
        });
    }
} catch (e) {}

// raise/tgkill/abort — self-kill paths the watchdog often uses instead of kill()
["raise", "tgkill", "abort"].forEach(function (fn) {
    var a = findExp(fn);
    if (!a) return;
    try {
        Interceptor.attach(a, {
            onEnter: function (args) {
                var sig = -1;
                if (fn === "raise") sig = args[0].toInt32();
                if (fn === "tgkill") sig = args[2].toInt32();
                if (sig === 9 || sig === 6 || sig === 4) {
                    log(TAG + " BLOCKED " + fn + "(sig=" + sig + ")");
                    args[0] = ptr(0);
                    if (fn === "tgkill") args[2] = ptr(0);
                }
            }
        });
        log(TAG + " " + fn + " hooked");
    } catch (e) {}
});

// pthread_create — log detection threads (gum-js-loop etc.)
try {
    var ptc = findExp("pthread_create");
    if (ptc) {
        Interceptor.attach(ptc, {
            onEnter: function (args) {
                try {
                    this.start = args[2];
                } catch (e) {}
            }
        });
    }
} catch (e) {}

try {
    var sc = findExp("syscall");
    if (sc) {
        Interceptor.attach(sc, {
            onEnter: function (args) {
                var nr = args[0].toInt32();
                if (nr === 26) { // ptrace
                    log(TAG + " BLOCKED syscall ptrace");
                    args[1] = ptr(-1);
                }
                if (nr === 37) { // kill
                    var sig = args[2].toInt32();
                    if (sig === 9 || sig === 6) {
                        log(TAG + " BLOCKED syscall kill(" + sig + ")");
                        args[2] = ptr(0);
                    }
                }
            }
        });
        log(TAG + " syscall hooked");
    }
} catch (e) {}

// ================= PHASE 2: Java capture =================
var hooked = false;

function hookInFactory(factory, tag) {
    var C = null;
    try { C = factory.use("com.titan.ranger.NativeJni"); } catch (e) { return; }
    try {
        var a = C.a;
        var ovs = a.overloads;
        for (var i = 0; i < ovs.length; i++) {
            (function (ov) {
                ov.implementation = function () {
                    var argl = Array.prototype.slice.call(arguments);
                    var line = TAG + " [" + tag + "] NativeJni.a(" + argl.map(jstr).join(", ") + ")";
                    log(line);
                    fappend(line);
                    var r = ov.apply(this, arguments);
                    if (r !== null && r !== undefined) {
                        var rl = TAG + " [" + tag + "] NativeJni.a RETURN=" + jstr(r);
                        log(rl);
                        fappend(rl);
                    }
                    return r;
                };
            })(ovs[i]);
        }
        hooked = true;
        log(TAG + " NativeJni.a hooked via " + tag + " (" + ovs.length + " overloads)");
    } catch (e) {
        log(TAG + " hook fail via " + tag + ": " + e);
    }
    // qd.a.a.k setter + d/h getters
    try {
        var qd = factory.use("qd.a.a");
        var ks = qd.k.overloads;
        for (var i = 0; i < ks.length; i++) {
            (function (ov) {
                ov.implementation = function () {
                    var a = Array.prototype.slice.call(arguments);
                    var line = TAG + " [" + tag + "] qd.a.a.k(" + a.map(jstr).join(", ") + ")";
                    log(line);
                    fappend(line);
                    return ov.apply(this, arguments);
                };
            })(ks[i]);
        }
        ["d", "h"].forEach(function (mn) {
            try {
                var ovs2 = qd[mn].overloads;
                ovs2.forEach(function (ov) {
                    ov.implementation = function () {
                        var r = ov.apply(this, arguments);
                        var line = TAG + " [" + tag + "] qd.a.a." + mn + "() = " + jstr(r);
                        log(line);
                        fappend(line);
                        return r;
                    };
                });
            } catch (e) {}
        });
        log(TAG + " qd.a.a hooked via " + tag);
    } catch (e) {}
}

Java.perform(function () {
    log(TAG + " Java ready, scanning classloaders");
    var tries = 0;
    var t = setInterval(function () {
        tries++;
        if (!hooked) {
            try {
                var loaders = Java.enumerateClassLoadersSync();
                for (var i = 0; i < loaders.length; i++) {
                    var factory = Java.ClassFactory.get(loaders[i]);
                    hookInFactory(factory, "loader#" + i);
                    if (hooked) break;
                }
            } catch (e) {}
        }
        if (hooked || tries > 200) clearInterval(t);
    }, 500);
});
