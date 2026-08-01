#!/system/bin/sh
# home_walk.sh — tap Home 'Vivo gratis' cards, parse the CURRENT play-state
# JSON ({"buffer":...} object with program/media/title), record it. Scrolls
# down between passes. Waits for Home focus before each tap.
# Usage: sh home_walk.sh <max_cards>
OUT=/data/local/tmp/tl_home_pairs.txt
: > "$OUT"
MAX=${1:-40}

ROW1_X="143 339 535 731 927 1123"
ROW2_X="143 339 535"
Y1=389
Y2=566

goto_home() {
  local n=0
  while [ $n -lt 6 ]; do
    F=$(dumpsys window 2>/dev/null | grep mCurrentFocus)
    case "$F" in
      *HomeActivity*) return 0;;
    esac
    input keyevent 4
    sleep 1
    n=$((n+1))
  done
  return 1
}

tap_and_grab() {
  local x=$1 y=$2
  input tap $x $y
  sleep 3
  PID=$(pidof com.global.latinotv | awk '{print $1}')
  [ -z "$PID" ] && { echo "TAP x=$x y=$y NO-APP" >> $OUT; goto_home; return; }
  LINE=$(grep '\[anon:dalvik-main space (region space)\]' /proc/$PID/maps | head -1 | awk '{print $1}')
  S=${LINE%-*}; E=${LINE#*-}
  dd if=/proc/$PID/mem bs=4096 skip=$((0x$S/4096)) count=$(((0x$E-0x$S)/4096)) of=/data/local/tmp/g.bin 2>/dev/null
  JSON=$(grep -ao '{"buffer":[^}]*}' /data/local/tmp/g.bin | tail -1)
  PROG=$(echo "$JSON" | grep -ao '"program":"cyx[^"]*"')
  MEDIA=$(echo "$JSON" | grep -ao '"media":"cyx[^"]*"')
  TITLE=$(echo "$JSON" | grep -ao '"title":"[^"]*"')
  echo "TAP x=$x y=$y $PROG $MEDIA $TITLE" >> $OUT
  goto_home
  sleep 1
}

N=0
while [ $N -lt $MAX ]; do
  for x in $ROW1_X; do
    tap_and_grab $x $Y1; N=$((N+1))
    [ $N -ge $MAX ] && break
  done
  [ $N -ge $MAX ] && break
  for x in $ROW2_X; do
    tap_and_grab $x $Y2; N=$((N+1))
    [ $N -ge $MAX ] && break
  done
  [ $N -ge $MAX ] && break
  input swipe 640 600 640 200 400
  sleep 2
done
echo "DONE" >> $OUT
