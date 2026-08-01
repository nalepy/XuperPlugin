#!/usr/bin/env bash
# notify.sh "<subject>" "<message>" — send a Telegram alert via Hermes on VM1.
# Creds stay on VM1 (uses `hermes send`); nothing secret is stored locally or in git.
#
# Security:
#  - subject + message are base64-encoded locally and decoded on the remote, so NO
#    caller-controlled text ever reaches the remote shell parser (no command injection).
#  - host key is verified via known_hosts (accept-new: trust first use, reject on change).
set -u
KEY="${HERMES_KEY:-$HOME/Workspace/Oracle/A1-VM1-ubuntu-ashburn-193.122.142.132.key}"
HOST="${HERMES_HOST:-ubuntu@193.122.142.132}"
SUBJ="${1:-[XuperPlugin orchestrator]}"
MSG="${2:-alert}"

# base64 (charset [A-Za-z0-9+/=]) is safe to embed in the remote command string.
S64="$(printf '%s' "$SUBJ" | base64 | tr -d '\n')"
M64="$(printf '%s' "$MSG"  | base64 | tr -d '\n')"

ssh -i "$KEY" -o StrictHostKeyChecking=accept-new -o ConnectTimeout=15 "$HOST" \
  "s=\"\$(printf %s '$S64' | base64 -d)\"; m=\"\$(printf %s '$M64' | base64 -d)\"; \
   printf '%s' \"\$m\" | ~/.local/bin/hermes send --quiet --to telegram --subject \"\$s\" -f -"
