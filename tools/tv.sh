#!/usr/bin/env bash
# tv.sh — one entry point for talking to the TV. Every other script goes through this.
#
#   tools/tv.sh shell "<command>"        run one shell command (one pm command per call!)
#   tools/tv.sh push <local> <remote>    copy a file to the TV (base64 chunks on the py backend)
#   tools/tv.sh pull <remote> <local>    copy a file from the TV
#   tools/tv.sh screenshot <out.png>     grab the screen
#   tools/tv.sh key KEYCODE [KEYCODE...] inject remote presses (DPAD_DOWN, DPAD_CENTER, HOME, BACK ...)
#   tools/tv.sh install <apk>            push + "pm install -r -g" (never hangs on Android 14 prompts)
#   tools/tv.sh status                   is the TV reachable / authorised?
#
# Backend is chosen by tools/connect.sh and stored in .tv.env at the repo root:
#   TV_BACKEND=adb   stock adb binary (default)
#   TV_BACKEND=py    Python adb-shell client in .venv (macOS "No route to host" workaround)
set -eu
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$HERE/.." && pwd)"
# shellcheck disable=SC1091
[ -f "$ROOT/.tv.env" ] && . "$ROOT/.tv.env"
TV_IP="${TV_IP:-}"
TV_PORT="${TV_PORT:-5555}"
TV_BACKEND="${TV_BACKEND:-adb}"
ADB="${ADB:-adb}"
PY="${PY:-$ROOT/.venv/bin/python}"
TMP_REMOTE="/data/local/tmp"

die() { echo "tv.sh: $*" >&2; exit 1; }
[ -n "$TV_IP" ] || die "TV_IP is not set. Run tools/connect.sh first (or: TV_IP=192.168.1.50 tools/tv.sh ...)."

serial="$TV_IP:$TV_PORT"
cmd="${1:-}"; [ -n "$cmd" ] || { sed -n '2,20p' "$0"; exit 1; }
shift

py() { "$PY" "$HERE/tvadb.py" --host "$TV_IP" --port "$TV_PORT" "$@"; }

case "$cmd" in
  shell)
    [ $# -ge 1 ] || die "shell needs a command"
    if [ "$TV_BACKEND" = py ]; then py shell "$*"; else "$ADB" -s "$serial" shell "$@"; fi ;;
  push)
    [ $# -eq 2 ] || die "push <local> <remote>"
    if [ "$TV_BACKEND" = py ]; then py push "$1" "$2"; else "$ADB" -s "$serial" push "$1" "$2"; fi ;;
  pull)
    [ $# -eq 2 ] || die "pull <remote> <local>"
    if [ "$TV_BACKEND" = py ]; then py pull "$1" "$2"; else "$ADB" -s "$serial" pull "$1" "$2"; fi ;;
  screenshot)
    out="${1:-screenshot.png}"
    if [ "$TV_BACKEND" = py ]; then py screenshot "$out"
    else
      "$ADB" -s "$serial" exec-out screencap -p > "$out" 2>/dev/null || {
        # Some adbd builds mangle exec-out; fall back to a file on the TV.
        "$ADB" -s "$serial" shell screencap -p "$TMP_REMOTE/_shot.png" >/dev/null
        "$ADB" -s "$serial" pull "$TMP_REMOTE/_shot.png" "$out" >/dev/null
        "$ADB" -s "$serial" shell rm -f "$TMP_REMOTE/_shot.png" >/dev/null
      }
    fi
    # PNG magic check so a broken grab never passes silently
    if ! head -c 8 "$out" | od -An -c | grep -q 'P   N   G'; then die "screenshot at $out is not a PNG"; fi
    echo "$out" ;;
  key)
    [ $# -ge 1 ] || die "key KEYCODE [...]"
    for k in "$@"; do
      case "$k" in KEYCODE_*) kc="$k" ;; [0-9]*) kc="$k" ;; *) kc="KEYCODE_$k" ;; esac
      if [ "$TV_BACKEND" = py ]; then py shell "input keyevent $kc"; else "$ADB" -s "$serial" shell input keyevent "$kc"; fi
      sleep 0.35
    done ;;
  install)
    [ $# -eq 1 ] || die "install <apk>"
    apk="$1"; [ -f "$apk" ] || die "no such file: $apk"
    remote="$TMP_REMOTE/$(basename "$apk")"
    "$0" push "$apk" "$remote"
    # -r replace, -g grant runtime permissions (Wi-Fi name needs location), -t allow debug builds.
    out="$("$0" shell "pm install -r -g -t $remote" 2>&1 || true)"
    echo "$out"
    "$0" shell "rm -f $remote" >/dev/null 2>&1 || true
    echo "$out" | grep -q Success || die "install failed" ;;
  status)
    if [ "$TV_BACKEND" = py ]; then py status
    else "$ADB" devices | grep "$serial" || { echo "not connected"; exit 1; }; fi ;;
  *) die "unknown command: $cmd" ;;
esac
