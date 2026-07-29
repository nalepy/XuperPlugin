#!/system/bin/sh
# Extract from existing zap dump (disk already freed by deleting hostscan.bin)
strings /data/local/tmp/hostscan_zap.bin | grep -E 'portalCore|getLiveData|main_addr|OkHttp http' | sort -u > /sdcard/heap_zap.txt
chmod 644 /sdcard/heap_zap.txt
wc -l /sdcard/heap_zap.txt
head -100 /sdcard/heap_zap.txt
