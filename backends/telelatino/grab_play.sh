#!/system/bin/sh
# grab_play.sh — dump dalvik-main region of the app + extract play-state JSON
PID=$(pidof com.global.latinotv | awk '{print $1}')
LINE=$(grep '\[anon:dalvik-main space (region space)\]' /proc/$PID/maps | head -1 | awk '{print $1}')
S=${LINE%-*}; E=${LINE#*-}
SKIP=$((0x$S / 4096)); COUNT=$(((0x$E - 0x$S) / 4096))
dd if=/proc/$PID/mem bs=4096 skip=$SKIP count=$COUNT of=/data/local/tmp/grab.bin 2>/dev/null
grep -ao '"program":"cyx[^"]*"' /data/local/tmp/grab.bin | sort -u | tail -2
grep -ao '"media":"cyx[^"]*"' /data/local/tmp/grab.bin | sort -u | tail -2
grep -ao '"title":"[^"]*"' /data/local/tmp/grab.bin | sort -u | tail -2
echo "---full-json---"
grep -ao '{"buffer":[^}]*}' /data/local/tmp/grab.bin | tail -1
