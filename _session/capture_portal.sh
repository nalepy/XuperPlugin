#!/system/bin/sh
rm -f /sdcard/portal_cold.pcap
timeout 50 tcpdump -i any -s0 -w /sdcard/portal_cold.pcap tcp and \( port 80 or port 443 \) 2>&1
