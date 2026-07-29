#!/system/bin/sh
PKG=com.android.mgstv
PID=$(pidof "$PKG")
LOG=/data/local/tmp/portal_hunt.log
echo "=== portal hunt $(date) PID=$PID ===" > "$LOG"
# zap + live data trigger
for i in 1 2 3 4 5; do
  input keyevent 166
  sleep 1
  input keyevent 22
  sleep 0.5
done
sleep 2
echo "--- netstat established (mgstv) ---" >> "$LOG"
netstat -tnp 2>/dev/null | grep "$PID" >> "$LOG"
ss -tnp 2>/dev/null | grep "$PID" >> "$LOG"
echo "--- /proc/net/tcp peers ---" >> "$LOG"
cat /proc/net/tcp >> "$LOG"
# heap scan
OUT=/data/local/tmp/hostscan_hunt.bin
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
echo "--- heap strings portal/okhttp ---" >> "$LOG"
strings "$OUT" | grep portalCore | sort -u >> "$LOG"
strings "$OUT" | grep OkHttp | sort -u >> "$LOG"
strings "$OUT" | grep -E 'https?://[a-z0-9.-]+\.(com|xyz|online)/api' | sort -u >> "$LOG"
strings "$OUT" | grep 'service_name":"portal' | sort -u >> "$LOG"
cp "$LOG" /sdcard/portal_hunt.log
wc -l "$LOG"
cat "$LOG"
