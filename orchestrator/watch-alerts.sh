#!/usr/bin/env bash
# watch-alerts.sh — background daemon. Watches for "needs your input" signals and
# escalates to Telegram (via notify.sh -> Hermes) if UNANSWERED after a threshold.
#
# Signals:
#   - any  <worktrees>/*/backends/*/NEEDS.md   (a worker stopped needing a secret/decision)
#   - any  orchestrator/alerts/*.pending       (a manual alert dropped by the orchestrator)
# Ack = the signal file disappears (NEEDS.md removed / .pending deleted) before the threshold.
#
# Run:  nohup ./watch-alerts.sh >/dev/null 2>&1 &   (or via the harness run_in_background)
set -u
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(cd "$HERE/.." && pwd)"
ALERTS="$HERE/alerts"; mkdir -p "$ALERTS"
THRESH="${ALERT_THRESHOLD:-600}"   # seconds unanswered before escalating (default 10 min)
POLL="${ALERT_POLL:-60}"

# baseline: signals already present at startup are treated as already-handled
# (only NEW asks that appear after launch will escalate). Avoids false alarms on relaunch.
shopt -s nullglob
for s in "$REPO"/../*-wt/*/backends/*/NEEDS.md "$ALERTS"/*.pending; do
  [ -f "$s" ] || continue
  id="$(printf '%s' "$s" | md5sum | cut -c1-12)"
  [ -f "$ALERTS/$id.sent" ] || { date +%s > "$ALERTS/$id.seen"; date +%s > "$ALERTS/$id.sent"; }
done

while true; do
  shopt -s nullglob
  signals=( "$REPO"/../*-wt/*/backends/*/NEEDS.md "$ALERTS"/*.pending )
  live=""
  for s in "${signals[@]}"; do
    [ -f "$s" ] || continue
    id="$(printf '%s' "$s" | md5sum | cut -c1-12)"
    live="$live $id"
    seen="$ALERTS/$id.seen"; sent="$ALERTS/$id.sent"
    [ -f "$seen" ] || date +%s > "$seen"
    age=$(( $(date +%s) - $(cat "$seen" 2>/dev/null || date +%s) ))
    if [ ! -f "$sent" ] && [ "$age" -ge "$THRESH" ]; then
      body="Unanswered ${THRESH}s — a worker needs your input:%0A$s%0A%0AReview: cd orchestrator && ./orchestrate.sh status"
      "$HERE/notify.sh" "[XuperPlugin] NEEDS INPUT" "$body" && date +%s > "$sent"
    fi
  done
  # clean markers for signals that were acked (file gone)
  for m in "$ALERTS"/*.seen "$ALERTS"/*.sent; do
    [ -e "$m" ] || continue
    mid="$(basename "$m")"; mid="${mid%.*}"
    case " $live " in *" $mid "*) ;; *) rm -f "$ALERTS/$mid.seen" "$ALERTS/$mid.sent" ;; esac
  done
  sleep "$POLL"
done
