#!/system/bin/sh
LOG=/data/local/tmp/sni_zap.log
echo start $(date) > "$LOG"
logcat -c
(input keyevent 166; sleep 0.8; input keyevent 166; sleep 0.8; input keyevent 166) &
if command -v tcpdump >/dev/null 2>&1; then
  timeout 12 tcpdump -i any -nn -s 0 -A 'tcp port 443' 2>/dev/null | grep -iE 'Server Name|portalCore|qho3cnsyil|sfgknh' >> "$LOG"
else
  timeout 12 logcat -v brief | grep -iE 'OkHttp|portalCore|portal|qho3cnsyil|sfgknh|getLiveData' >> "$LOG"
fi
echo end $(date) >> "$LOG"
wc -l "$LOG"
tail -80 "$LOG"
cp "$LOG" /sdcard/sni_zap.log
