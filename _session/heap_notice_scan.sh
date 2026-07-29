#!/system/bin/sh
# Notice/portal heap scan — stream strings to /sdcard, no large /data/local/tmp bins
PKG=com.android.mgstv
PID=$(pidof "$PKG")
if [ -z "$PID" ]; then
  echo "ERROR: $PKG not running"
  exit 1
fi
echo "PID=$PID"
rm -f /data/local/tmp/hostscan*.bin /data/local/tmp/hostscan_live.bin 2>/dev/null

RAW=/sdcard/heap_notice_strings.txt
CTX=/sdcard/heap_notice_ctx_raw.txt
OUT=/sdcard/heap_notice_ctx.txt
rm -f "$RAW" "$CTX" "$OUT"

# dalvik-main only (smaller)
(
for r in $(grep dalvik-main "/proc/$PID/maps" | awk '{print $1}'); do
  s=${r%-*}; e=${r#*-}
  start=$((16#$s)); end=$((16#$e)); len=$((end-start))
  [ "$len" -le 0 ] && continue
  dd if="/proc/$PID/mem" bs=4096 skip=$((start/4096)) count=$((len/4096+1)) 2>/dev/null
done
) | strings > "$RAW"

echo "strings lines: $(wc -l < "$RAW")"

# Targeted hits
grep -n -E 'Sz0JjjU4|lkgQ43Df|get_notice|domain\|DES|domain_DES|notice/api|/api/portalCore' "$RAW" | head -200 > "$CTX.hitlines"

# FQDN near portal paths
grep -E 'portalCore|get_notice|notice/api' "$RAW" | head -80 >> "$CTX.hitlines"

# Context: lines containing blob or keywords + neighbors (toy context via grep -C on raw file)
{
  echo "=== KEYWORD HITS (unique) ==="
  grep -E 'Sz0JjjU4|lkgQ43Df|get_notice|domain\|DES|domain_DES|notice/api' "$RAW" | sort -u | head -100
  echo ""
  echo "=== FQDN candidates (*.com *.xyz *.online) ==="
  grep -oE '[a-z0-9][-a-z0-9]{0,62}\.(com|xyz|online)(/[a-zA-Z0-9_./-]*)?' "$RAW" | sort -u | head -200
  echo ""
  echo "=== https URLs ==="
  grep -oE 'https?://[a-z0-9][-a-z0-9.]*\.(com|xyz|online)[^[:space:]"<>]{0,120}' "$RAW" | sort -u | head -80
  echo ""
  echo "=== portalCore adjacent (same line) ==="
  grep -E 'portalCore|getSlbInfo|get_notice' "$RAW" | sort -u | head -60
} > "$OUT"

# Extended context around Sz0J blob using awk on strings file
echo "" >> "$OUT"
echo "=== CONTEXT around Sz0JjjU4 (line ±3) ===" >> "$OUT"
grep -n 'Sz0JjjU4' "$RAW" | head -20 | while IFS=: read -r num rest; do
  start=$((num - 5)); [ "$start" -lt 1 ] && start=1
  end=$((num + 5))
  sed -n "${start},${end}p" "$RAW" | tr '\n' '|'
  echo ""
done >> "$OUT"

chmod 644 "$RAW" "$OUT" "$CTX.hitlines" 2>/dev/null
ls -la "$RAW" "$OUT"
wc -l "$OUT"
head -120 "$OUT"
