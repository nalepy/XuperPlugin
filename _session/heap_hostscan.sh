#!/system/bin/sh
# Scan dalvik-main rw regions for portalCore host strings (root).
PKG=com.android.mgstv
PID=$(pidof "$PKG")
if [ -z "$PID" ]; then
  echo "ERROR: $PKG not running"
  exit 1
fi
echo "PID=$PID"
OUT=/data/local/tmp/hostscan.bin
rm -f "$OUT"
for r in $(grep -E "dalvik-main|rw-p.*00:00 0" "/proc/$PID/maps" | awk '{print $1}'); do
  s=${r%-*}
  e=${r#*-}
  start=$((16#$s))
  end=$((16#$e))
  len=$((end - start))
  [ "$len" -le 0 ] && continue
  count=$((len / 4096 + 1))
  skip=$((start / 4096))
  dd if="/proc/$PID/mem" bs=4096 skip=$skip count=$count 2>/dev/null
done >> "$OUT"
ls -la "$OUT"
strings "$OUT" | grep -E 'portalCore|getLiveData|/api/portal|\.com|\.xyz|\.online' | sort -u | head -120
