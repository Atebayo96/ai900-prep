#!/usr/bin/env bash
# enable.sh PACKAGE [PACKAGE...] — re-enable one or more packages (undo a single debloat line).
set -eu
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$HERE/.." && pwd)"
for p in "$@"; do
  "$HERE/tv.sh" shell "pm enable --user 0 $p"
  if [ -f "$ROOT/backup/latest/disabled-by-us.txt" ]; then
    grep -v -x "$p" "$ROOT/backup/latest/disabled-by-us.txt" > "$ROOT/backup/latest/.tmp" || true
    mv "$ROOT/backup/latest/.tmp" "$ROOT/backup/latest/disabled-by-us.txt"
  fi
done
