#!/system/bin/sh
# Heap scan after channel activity — compare portal/host strings.
PKG=com.android.mgstv
PID=$(pidof "$PKG")
if [ -z "$PID" ]; then
  echo "ERROR: $PKG not running"
  exit 1
fi
echo "PID=$PID"
OUT=/data/local/tmp/hostscan_zap.bin
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
strings "$OUT" | grep -E 'portalCore|getLiveData|main_addr|OkHttp http|https?://[a-z0-9.-]+\.(com|xyz|online)' | sort -u | head -250 > /data/local/tmp/heap_zap.txt
cp /data/local/tmp/heap_zap.txt /sdcard/heap_zap.txt
chmod 644 /sdcard/heap_zap.txt
wc -l /data/local/tmp/heap_zap.txt
head -100 /data/local/tmp/heap_zap.txt
