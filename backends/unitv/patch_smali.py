#!/usr/bin/env python3
"""UniTV (fake, com.integration.unitviptv v2.14.8) — smali patch for a free standalone build.

Usage:
    java -jar apktool.jar d -f -o unitv_smali UniTV_fake_2.14.8_APKPure.apk
    python3 patch_smali.py unitv_smali
    java -jar apktool.jar b unitv_smali -o unitv_patched.apk
    zipalign -f 4 unitv_patched.apk unitv_aligned.apk
    apksigner sign --ks patch.ks --out unitv_signed.apk unitv_aligned.apk

What it does (see PATCH-ASSESSMENT.md):
  1. util/c.a(Context,String)  -> no-op. This is the app's self-kill bomb
     (killProcess + forceStopPackage + System.exit). It is reached by the splash
     gate on ANY of: signature-MD5 mismatch (fires after re-sign), frida present,
     xposed/hook present, debugger/emulator, or TV-device detection.
  2. f/b/u$e.a(UpdateBean)      -> no-op. Suppresses the forced-update dialog
     (HomeUpgradeDialog with forceUpdate==1 hides its cancel and kills on BACK).

Stdlib only. Idempotent (safe to re-run).
"""

import os
import sys


def nop_method(path: str, method_sig: str) -> None:
    """Replace the body of the first method whose header matches `method_sig`
    with a bare `return-void`. The `.end method` line is preserved."""
    src = open(path, encoding="utf-8").read()
    start = src.index(method_sig)
    body_start = start + len(method_sig)
    end = src.index(".end method", start)
    patched = src[:body_start] + "\n    .locals 0\n\n    return-void\n" + src[end:]
    open(path, "w", encoding="utf-8").write(patched)
    print(f"patched: {path} [{method_sig}]")


def main() -> None:
    root = sys.argv[1] if len(sys.argv) > 1 else "unitv_smali"
    patches = [
        (
            os.path.join(root, "smali_classes2", "mobile", "com", "requestframe", "util", "c.smali"),
            ".method public final a(Landroid/content/Context;Ljava/lang/String;)V",
        ),
        (
            os.path.join(root, "smali_classes2", "com", "mobile", "brasiltv", "f", "b", "u$e.smali"),
            ".method public a(Lcom/mobile/bean/UpdateBean;)V",
        ),
    ]
    for path, sig in patches:
        if not os.path.exists(path):
            print(f"MISSING (skipping): {path}")
            continue
        nop_method(path, sig)
    print("done. rebuild: java -jar apktool.jar b <root> -o unitv_patched.apk")


if __name__ == "__main__":
    main()
