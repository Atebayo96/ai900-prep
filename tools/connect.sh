#!/usr/bin/env bash
# connect.sh — find the TV on the LAN and connect over ADB TCP (port 5555).
#
#   tools/connect.sh              scan the ARP table / mDNS for a host with port 5555 open
#   tools/connect.sh 192.168.1.50 use this IP
#
# Writes .tv.env (TV_IP, TV_PORT, TV_BACKEND) at the repo root for the other scripts.
set -eu
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$HERE/.." && pwd)"
PORT="${TV_PORT:-5555}"
ADB="${ADB:-adb}"

cat <<'MSG'
Before we start, on the TV:
  Settings > Device Preferences > About > press OK on "Build" 7 times, then back >
  Developer options > USB debugging ON (and Network / ADB debugging ON if your TV shows it).
  Keep the TV on.

!! USB power warning: some boxes cut power to their USB port while USB debugging is on.
   A USB speaker, hub or drive plugged into the TV can go dead until debugging is switched off again.
   Tell your assistant what is plugged into the TV's USB ports before continuing.

MSG

port_open() {  # host port
  if command -v nc >/dev/null 2>&1; then
    nc -z -G 1 "$1" "$2" >/dev/null 2>&1 || nc -z -w 1 "$1" "$2" >/dev/null 2>&1
  else
    (exec 3<>"/dev/tcp/$1/$2") >/dev/null 2>&1
  fi
}

IP="${1:-}"
if [ -z "$IP" ]; then
  echo "Scanning for a host with port $PORT open..."
  CANDIDATES=""
  # 1. mDNS: Android TV advertises its remote service; adb-tls hosts show up too.
  if command -v dns-sd >/dev/null 2>&1; then
    # macOS: dns-sd never exits on its own, so bound it with a timeout via background+kill
    for svc in _androidtvremote2._tcp _adb-tls-connect._tcp _googlecast._tcp; do
      ( dns-sd -B "$svc" local >/tmp/.tv_mdns.$$ 2>/dev/null & p=$!; sleep 2; kill $p 2>/dev/null ) || true
      if grep -q "$svc" /tmp/.tv_mdns.$$ 2>/dev/null; then echo "  mDNS saw $svc services (a TV or Chromecast is on this LAN)"; fi
      rm -f /tmp/.tv_mdns.$$
    done
  elif command -v avahi-browse >/dev/null 2>&1; then
    avahi-browse -rtp _androidtvremote2._tcp 2>/dev/null | awk -F';' '$1=="="{print $8}' | while read -r h; do echo "  mDNS: $h"; done
  fi
  # 2. ARP table: everything this computer has recently talked to.
  for h in $(arp -a 2>/dev/null | grep -oE '\(?[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+\)?' | tr -d '()' | sort -u); do
    case "$h" in 224.*|239.*|255.*|*.255) continue;; esac
    if port_open "$h" "$PORT"; then echo "  $h has port $PORT open"; CANDIDATES="$CANDIDATES $h"; fi
  done
  set -- $CANDIDATES
  if [ $# -eq 1 ]; then IP="$1"
  elif [ $# -gt 1 ]; then echo "Several candidates:$CANDIDATES"; echo "Re-run with the TV's IP: tools/connect.sh <ip>"; exit 2
  else
    echo "Nothing found. Read the IP from the TV (Settings > Network > your network) and re-run: tools/connect.sh <ip>"
    echo "(Mac users: if arp shows the TV but the port looks closed, ADB/Network debugging is probably still off.)"
    exit 2
  fi
fi

echo "Using TV at $IP:$PORT"
if ! port_open "$IP" "$PORT"; then
  echo "Port $PORT is closed on $IP. Turn on USB debugging / Network debugging on the TV and try again."; exit 3
fi

BACKEND=adb
echo "When you press Enter, the TV will show 'Allow USB debugging?' — pick Allow and tick 'Always allow from this computer'."
read -r -p "Press Enter to connect... " _ || true
OUT="$("$ADB" connect "$IP:$PORT" 2>&1 || true)"
echo "$OUT"
if echo "$OUT" | grep -qiE "no route to host|failed to connect|cannot connect"; then
  echo
  echo "Stock adb cannot reach a port that is open. On macOS that is Local Network privacy blocking adb."
  echo "Switching to the Python adb-shell client (no need to debug the stock adb)."
  BACKEND=py
  if [ ! -x "$ROOT/.venv/bin/python" ]; then
    python3 -m venv "$ROOT/.venv"
    "$ROOT/.venv/bin/pip" install -q -r "$HERE/requirements.txt"
  fi
  "$ROOT/.venv/bin/python" "$HERE/tvadb.py" --host "$IP" --port "$PORT" status
else
  for _ in 1 2 3 4 5 6 7 8 9 10 11 12; do
    STATE="$("$ADB" devices | awk -v s="$IP:$PORT" '$1==s{print $2}')"
    [ "$STATE" = device ] && break
    [ "$STATE" = unauthorized ] && echo "Waiting for you to accept the prompt on the TV..."
    sleep 5
  done
  [ "$STATE" = device ] || { echo "TV is '$STATE'. Accept the prompt on the TV and re-run."; exit 4; }
  "$ADB" -s "$IP:$PORT" shell getprop ro.product.model
fi

cat > "$ROOT/.tv.env" <<ENV
TV_IP=$IP
TV_PORT=$PORT
TV_BACKEND=$BACKEND
ENV
echo "Saved to $ROOT/.tv.env (backend: $BACKEND)."
echo "Remember to switch USB debugging OFF on the TV when you are done (USB power!)."
