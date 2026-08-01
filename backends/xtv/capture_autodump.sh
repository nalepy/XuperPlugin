#!/system/bin/sh
# autodump.sh — loop: every 5 min, if mgstv is running, alldump to /sdcard/auto_<ts> and grep hosts.
PKG=com.android.mgstv
VM=/data/local/tmp/alldump
LOG=/data/local/tmp/autodump.log
while true; do
  PID=$(pidof $PKG)
  PID=${PID%% *}
  if [ -n "$PID" ]; then
    TS=$(date +%s)
    D=/sdcard/auto_$TS
    mkdir -p $D
    echo "[$TS] pid=$PID dumping" >> $LOG
    $VM $PID $D > /dev/null 2>&1
    echo "[$TS] done: $(ls $D | wc -l) regions" >> $LOG
    # extract hostnames near portal records
    grep -h -a -o -E '[a-z0-9]{4,20}\.[a-z0-9]{3,10}\.[a-z]{2,3}' $D/dmp_*.bin 2>/dev/null | sort | uniq -c | sort -rn | head -20 >> $LOG
    rm -rf $D
  fi
  sleep 300
done
