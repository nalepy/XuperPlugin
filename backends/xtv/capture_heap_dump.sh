#!/system/bin/sh
# heap_dump8.sh v4 — launch mgstv fresh; vmread 8MB from EVERY rw-p region (dalvik, anon, ashmem).
PKG=com.android.mgstv
OUT=/sdcard
VM=/data/local/tmp/vmread

am force-stop $PKG
sleep 1
monkey -p $PKG -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1

for i in 1 2 3 4 5 6 7 8 9 10; do
  PID=$(pidof $PKG)
  [ -n "$PID" ] && break
  sleep 1
done
PID=${PID%% *}
echo "pid=$PID" > $OUT/heap8_run.txt
[ -z "$PID" ] && { echo "no pid" >> $OUT/heap8_run.txt; exit 1; }

cat /proc/$PID/maps > $OUT/heap8_maps.txt

cat $OUT/heap8_maps.txt | while read line; do
  set -- $line
  [ $# -lt 5 ] && continue
  perms=$2
  case "$perms" in rw-p) ;; *) continue ;; esac
  start=${1%%-*}
  [ -z "$start" ] && continue
  fn="$OUT/v4_$start.bin"
  [ -f "$fn" ] && continue
  echo "dump $start" >> $OUT/heap8_run.txt
  $VM $PID $start 8388608 "$fn" >> $OUT/heap8_run.txt 2>&1
done
echo "DONE" >> $OUT/heap8_run.txt
ls -la $OUT/v4_*.bin >> $OUT/heap8_run.txt 2>/dev/null
