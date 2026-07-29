#!/system/bin/sh
# Cold start + 50s tcpdump for portal SNI
rm -f /sdcard/portal_hunt_cold.pcap
am force-stop com.android.mgstv
sleep 2
(sleep 3; monkey -p com.android.mgstv -c android.intent.category.LAUNCHER 1) &
timeout 50 tcpdump -i any -nn -s0 -w /sdcard/portal_hunt_cold.pcap 'tcp and (port 80 or port 443)' 2>/data/local/tmp/tcpdump_hunt.log
ls -la /sdcard/portal_hunt_cold.pcap
