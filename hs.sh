#!/system/bin/sh
rm -f /data/local/tmp/hostscan.bin
for r in $(grep -E "dalvik-main|rw-p.*00:00 0" /proc/27656/maps | awk '{print $1}'); do
  s=${r%-*}
  e=${r#*-}
  dd if=/proc/27656/mem bs=4096 skip=$((0x$s/4096)) count=$(((0x$e-0x$s)/4096)) 2>/dev/null
done > /data/local/tmp/hostscan.bin
