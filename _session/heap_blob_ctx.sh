#!/system/bin/sh
# Pull context around DES blobs from previous dump if present, else scan prefs/files
strings /data/local/tmp/hostscan_live.bin 2>/dev/null | grep -n 'Sz0JjjU4\|4hv+FZG\|MP5TBkYz\|lkgQ43Df\|domain' | head -40 > /sdcard/heap_blob_ctx.txt
# Also dump cache.config / mgstv prefs for domain fields
for f in /data/data/com.android.mgstv/shared_prefs/*.xml /data/data/com.android.mgstv/files/*config* /data/data/com.android.mgstv/databases/*.db; do
  [ -f "$f" ] || continue
  echo "=== $f ===" >> /sdcard/heap_blob_ctx.txt
  strings "$f" 2>/dev/null | grep -E 'domain|DES|portal|host|Sz0J|masnew' | head -30 >> /sdcard/heap_blob_ctx.txt
done
chmod 644 /sdcard/heap_blob_ctx.txt
# Note: hostscan_live.bin was deleted by previous script — check
ls -la /data/local/tmp/hostscan_live.bin 2>/dev/null
wc -l /sdcard/heap_blob_ctx.txt
cat /sdcard/heap_blob_ctx.txt
