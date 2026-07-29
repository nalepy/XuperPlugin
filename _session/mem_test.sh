#!/system/bin/sh
PKG=com.android.mgstv
PID=$(pidof "$PKG")
echo PID=$PID
grep dalvik-main "/proc/$PID/maps" | head -3
ls -la /data/local/tmp/*.bin 2>/dev/null | head -5
# quick small region test
r=$(grep dalvik-main "/proc/$PID/maps" | head -1 | awk '{print $1}')
echo region=$r
s=${r%-*}
e=${r#*-}
start=$((16#$s))
end=$((16#$e))
len=$((end - start))
echo len=$len
count=4
skip=$((start / 4096))
dd if="/proc/$PID/mem" bs=4096 skip=$skip count=$count of=/data/local/tmp/memtest.bin 2>&1
ls -la /data/local/tmp/memtest.bin
