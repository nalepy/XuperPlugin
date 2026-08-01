#!/usr/bin/env bash
# notify.sh "<subject>" "<message>" — send a Telegram alert via Hermes on VM1.
# Creds stay on VM1 (uses `hermes send`); nothing secret is stored locally or in git.
set -u
KEY="${HERMES_KEY:-$HOME/Workspace/Oracle/A1-VM1-ubuntu-ashburn-193.122.142.132.key}"
HOST="${HERMES_HOST:-ubuntu@193.122.142.132}"
SUBJ="${1:-[XuperPlugin orchestrator]}"
MSG="${2:-alert}"
printf '%s' "$MSG" | ssh -i "$KEY" -o StrictHostKeyChecking=no -o ConnectTimeout=15 "$HOST" \
  "~/.local/bin/hermes send --quiet --to telegram --subject \"$(printf '%s' "$SUBJ" | sed 's/\"/'\''/g')\" -f -"
