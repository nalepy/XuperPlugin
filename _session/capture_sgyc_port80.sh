#!/system/bin/sh
# 45s TCP/80 capture during XTV cold start + channel keys (tcpdump as root)
OUT=/data/local/tmp/sgyc_port80.pcap
rm -f "$OUT"
su -c "timeout 45 tcpdump -i any -s0 -w $OUT tcp port 80" 2>/dev/null &
TD=$!
sleep 2
am force-stop com.android.mgstv
sleep 1
am start -n com.android.mgstv/com.interactive.brasiliptv.ui.activity.WelcomeActivity
sleep 8
input keyevent 166
sleep 3
input keyevent 167
sleep 3
input keyevent 166
sleep 3
input keyevent 167
sleep 3
input keyevent 166
sleep 3
input keyevent 167
sleep 5
wait "$TD" 2>/dev/null
