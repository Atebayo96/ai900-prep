#!/usr/bin/env bash
# restore-all.sh [backup-dir] — run the restore script of the latest (or given) backup.
set -eu
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$HERE/.." && pwd)"
BK="${1:-$ROOT/backup/latest}"
[ -x "$BK/restore.sh" ] || { echo "no restore.sh in $BK (run tools/backup.sh first)"; exit 1; }
exec "$BK/restore.sh"
