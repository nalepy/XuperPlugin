// Minimal proof-of-life + DEX dumper for ijiami-packed XTV
var TAG = "[GK]";

// Write to skips the File API issues - use send() for logcat
function log(msg) {
    send(TAG + " " + msg);
}

log("Script loaded. PID=" + Process.id);

// Hook kill - just log, don't block
var killPtr = Module.findExportByName(null, "kill");
if (killPtr) {
    Interceptor.attach(killPtr, {
        onEnter: function (args) {
            log("kill(" + args[0].toInt32() + ", " + args[1].toInt32() + ")");
        }
    });
}

// Hook exit - just log
var exitPtr = Module.findExportByName(null, "exit") || Module.findExportByName(null, "_exit");
if (exitPtr) {
    Interceptor.attach(exitPtr, {
        onEnter: function (args) {
            log("exit(" + args[0] + ")");
        }
    });
}

// Hook abort
var abortPtr = Module.findExportByName(null, "abort");
if (abortPtr) {
    Interceptor.attach(abortPtr, {
        onEnter: function (args) {
            log("abort()");
        }
    });
}

log("Native hooks installed. Starting Java hooks in 5s...");

setTimeout(function () {
    Java.perform(function () {
        log("Java VM ready, enumerating classes...");
        Java.enumerateLoadedClasses({
            onMatch: function (className) {
                if (className.indexOf("brasiliptv") >= 0 ||
                    className.indexOf("interactive") >= 0 ||
                    className.indexOf("DETool") >= 0 ||
                    className.indexOf("AppWrapper") >= 0) {
                    log("INTERESTING: " + className);
                }
            },
            onComplete: function () {
                log("Class enumeration complete");
            }
        });
    });
}, 5000);
