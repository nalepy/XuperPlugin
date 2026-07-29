#!/system/bin/sh
strings /data/local/tmp/hostscan.bin | grep -E 'portalCore|getLiveData|main_addr=|OkHttp http' | sort -u | head -200 > /data/local/tmp/heap_portal.txt
wc -l /data/local/tmp/heap_portal.txt
head -80 /data/local/tmp/heap_portal.txt
