#!/system/bin/sh
# Targeted strings: domain DES blobs, host near portal, failover pairs
BIN=/data/local/tmp/hostscan_live.bin
PID=$(pidof com.android.mgstv)
[ -z "$PID" ] && echo "no pid" && exit 1
echo PID=$PID
# Reuse smaller dump if space tight: only dalvik-main
rm -f "$BIN"
for r in $(grep "dalvik-main" "/proc/$PID/maps" | awk '{print $1}'); do
  s=${r%-*}; e=${r#*-}
  start=$((16#$s)); end=$((16#$e)); len=$((end-start))
  [ "$len" -le 0 ] && continue
  dd if="/proc/$PID/mem" bs=4096 skip=$((start/4096)) count=$((len/4096+1)) 2>/dev/null
done >> "$BIN"
ls -la "$BIN"
OUT=/sdcard/heap_domain.txt
strings "$BIN" | grep -E 'domain_DES|Sz0JjjU4|lkgQ43Df|getDomain|setDomain|DES|portalCore|https?://[a-z0-9]{3,}\.[a-z0-9.-]+\.(com|xyz)/api' | sort -u > "$OUT"
chmod 644 "$OUT"
wc -l "$OUT"
head -80 "$OUT"
rm -f "$BIN"
