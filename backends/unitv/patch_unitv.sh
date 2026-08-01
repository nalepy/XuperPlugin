#!/usr/bin/env bash
# =============================================================================
# patch_unitv.sh — build a patched "fake UniTV" v2.14.8 APK (com.integration.unitviptv)
#
# What it patches (see PATCH-ASSESSMENT.md for the full map):
#   1. mobile/com/requestframe/util/c.smali  (the anti-tamper class):
#        - a(Context,String)V        killProcess + forceStopPackage + exit  -> no-op
#        - b(Context)Z               root/xposed gate (b/c/e/f checks)      -> false
#        - c()Z                      frida-server ps check                  -> false
#        - d(Context)Z               debugger / qemu / emulator check       -> false
#        - e(Context)Ljava/lang/String;  signature md5 check               -> ""
#      The stock APKPure APK already fails its OWN signature check (APKPure
#      re-signed it; hardcoded md5 8ddb342f... != cert md5 647a88ef...), so it
#      suicides ~4s after launch on EVERY device. e() -> "" kills that and the
#      re-sign trip at once.
#   2. com/mobile/brasiltv/view/HomeUpgradeDialog.smali  onBackPressed
#      forceUpdate==1 previously did Process.killProcess(myPid()); now it
#      always dismisses (server-driven forced-update cannot brick the app).
#
# Usage:
#   ./patch_unitv.sh [path/to/UniTV_fake_2.14.8_APKPure.apk]
#
# Requires: java, apktool.jar (APKTOOL_JAR env or /c/Apktool/apktool.jar),
#           zipalign + apksigner (ANDROID_BUILD_TOOLS env or the default path
#           below), keytool (JDK), python3.
# Outputs: <workdir>/UniTV_patched_2.14.8.apk  (aligned + signed + verified)
# =============================================================================
set -euo pipefail

APK="${1:-$HOME/Workspace/FakeUnitv/UniTV_fake_2.14.8_APKPure.apk}"
APKTOOL_JAR="${APKTOOL_JAR:-/c/Apktool/apktool.jar}"
BT="${ANDROID_BUILD_TOOLS:-$HOME/AppData/Local/Android/Sdk/build-tools/37.0.0}"
WORK="${UNITV_PATCH_WORK:-$HOME/Workspace/FakeUnitv/unitv_patch_work}"
KS="$WORK/unitv.keystore"
KS_PASS="unitvpatch"
KS_ALIAS="unitv"
OUT="$WORK/UniTV_patched_2.14.8.apk"

echo "[*] apktool decode: $APK"
java -jar "$APKTOOL_JAR" d -f -o "$WORK/apk" "$APK"

echo "[*] applying smali patches (anchored, verified)"
export WORK
python3 - <<'PYEOF'
import io, os, sys

C = "smali_classes2/mobile/com/requestframe/util/c.smali"
HUD = "smali_classes2/com/mobile/brasiltv/view/HomeUpgradeDialog.smali"

def load(p):
    with io.open(p, encoding="utf-8") as f:
        return f.read()

def save(p, s):
    with io.open(p, "w", encoding="utf-8", newline="\n") as f:
        f.write(s)

def patch(path, old, new, tag):
    s = load(path)
    if old not in s:
        sys.exit(f"PATCH FAILED [{tag}]: anchor not found in {path}")
    if s.count(old) != 1:
        sys.exit(f"PATCH FAILED [{tag}]: anchor not unique ({s.count(old)}) in {path}")
    save(path, s.replace(old, new, 1))
    print(f"  ok  {tag}")

root = os.environ["WORK"] + "/apk"
c_path = f"{root}/{C}"
hud_path = f"{root}/{HUD}"

# --- 1. c.a(Context,String)V -> return-void (no-op the kill+forceStop+exit) ---
old = """\
.method public final a(Landroid/content/Context;Ljava/lang/String;)V
    .locals 6

    const-string v0, "context"
"""
new = """\
.method public final a(Landroid/content/Context;Ljava/lang/String;)V
    .locals 6

    return-void

    const-string v0, "context"
"""
patch(c_path, old, new, "c.a(Context,String)V no-op")

# --- 2. c.b(Context)Z -> false (root/xposed gate) ---
old = """\
.method public final b(Landroid/content/Context;)Z
    .locals 1

    const-string v0, "context"

    invoke-static {p1, v0}, Ld/f/b/i;->b(Ljava/lang/Object;Ljava/lang/String;)V

    invoke-virtual {p0}, Lmobile/com/requestframe/util/c;->b()Z
"""
new = """\
.method public final b(Landroid/content/Context;)Z
    .locals 1

    const-string v0, "context"

    invoke-static {p1, v0}, Ld/f/b/i;->b(Ljava/lang/Object;Ljava/lang/String;)V

    const/4 v0, 0x0

    return v0

    invoke-virtual {p0}, Lmobile/com/requestframe/util/c;->b()Z
"""
patch(c_path, old, new, "c.b(Context)Z -> false")

# --- 3. c.c()Z -> false (frida-server detection) ---
old = """\
.method public final c()Z
    .locals 6

    const/4 v0, 0x0
"""
new = """\
.method public final c()Z
    .locals 6

    const/4 v0, 0x0

    return v0
"""
patch(c_path, old, new, "c.c()Z -> false")

# --- 4. c.d(Context)Z -> false (debugger/emulator gate) ---
old = """\
.method public final d(Landroid/content/Context;)Z
    .locals 3

    const-string v0, "context"

    invoke-static {p1, v0}, Ld/f/b/i;->b(Ljava/lang/Object;Ljava/lang/String;)V

    :try_start_0
"""
new = """\
.method public final d(Landroid/content/Context;)Z
    .locals 3

    const-string v0, "context"

    invoke-static {p1, v0}, Ld/f/b/i;->b(Ljava/lang/Object;Ljava/lang/String;)V

    const/4 v0, 0x0

    return v0

    :try_start_0
"""
patch(c_path, old, new, "c.d(Context)Z -> false")

# --- 5. c.e(Context)Ljava/lang/String; -> "" (signature md5 -> empty) ---
old = """\
.method public final e(Landroid/content/Context;)Ljava/lang/String;
    .locals 3

    const-string v0, "context"

    invoke-static {p1, v0}, Ld/f/b/i;->b(Ljava/lang/Object;Ljava/lang/String;)V

    :try_start_0
"""
new = """\
.method public final e(Landroid/content/Context;)Ljava/lang/String;
    .locals 3

    const-string v0, "context"

    invoke-static {p1, v0}, Ld/f/b/i;->b(Ljava/lang/Object;Ljava/lang/String;)V

    const-string v0, ""

    return-object v0

    :try_start_0
"""
patch(c_path, old, new, "c.e(Context) -> \"\"")

# --- 6. HomeUpgradeDialog.onBackPressed: force-update kill -> always dismiss ---
old = """\
    if-ne v0, v1, :cond_0

    invoke-static {}, Landroid/os/Process;->myPid()I
"""
new = """\
    goto :cond_0

    invoke-static {}, Landroid/os/Process;->myPid()I
"""
patch(hud_path, old, new, "HomeUpgradeDialog force-update kill -> dismiss")

print("[*] all smali patches applied")
PYEOF

echo "[*] apktool build"
java -jar "$APKTOOL_JAR" b "$WORK/apk" -o "$WORK/patched-unsigned.apk"

echo "[*] zipalign"
"$BT/zipalign" -f 4 "$WORK/patched-unsigned.apk" "$WORK/patched-aligned.apk"

if [ ! -f "$KS" ]; then
    echo "[*] generating debug keystore"
    keytool -genkeypair -keystore "$KS" -storepass "$KS_PASS" -alias "$KS_ALIAS" \
        -dname "CN=UnitV Patch, O=unitv-patch, C=US" -keyalg RSA -keysize 2048 -validity 10000
fi

echo "[*] apksigner sign"
if [ -f "$BT/lib/apksigner.jar" ]; then
    java -jar "$BT/lib/apksigner.jar" sign --ks "$KS" --ks-pass "pass:$KS_PASS" --key-pass "pass:$KS_PASS" \
        --ks-key-alias "$KS_ALIAS" --out "$OUT" "$WORK/patched-aligned.apk"
    echo "[*] apksigner verify"
    java -jar "$BT/lib/apksigner.jar" verify --print-certs "$OUT" | head -5
elif [ -f "$BT/apksigner" ]; then
    "$BT/apksigner" sign --ks "$KS" --ks-pass "pass:$KS_PASS" --key-pass "pass:$KS_PASS" \
        --ks-key-alias "$KS_ALIAS" --out "$OUT" "$WORK/patched-aligned.apk"
    echo "[*] apksigner verify"
    "$BT/apksigner" verify --print-certs "$OUT" | head -5
else
    echo "apksigner not found in $BT"; exit 1
fi

echo "[*] DONE: $OUT"
ls -la "$OUT"
