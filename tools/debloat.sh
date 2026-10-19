#!/usr/bin/env bash
# debloat.sh — disable packages in small batches, one pm command per shell call,
# with a screenshot after every batch and an undo for the batch you just did.
#
#   tools/debloat.sh backup/latest/plan.tsv                    rows marked "disable" in the default categories
#   tools/debloat.sh backup/latest/plan.tsv --categories telemetry,content
#   tools/debloat.sh my-list.txt                               plain list of package names (# comments ok)
#   options: --batch N (default 5)  --yes (no pauses)  --dry-run  --force (allow protected packages)
#
# Never uninstalls anything: "pm disable-user --user 0" only. Everything is logged to
# backup/latest/disabled-by-us.txt, which restore.sh reads.
set -eu
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$HERE/.." && pwd)"
TV="$HERE/tv.sh"
BK="$ROOT/backup/latest"
[ -d "$BK" ] || { echo "Run tools/backup.sh first."; exit 1; }

SRC=""; CATS="telemetry,content,background,preloaded"; BATCH=5; YES=0; DRY=0; FORCE=0
while [ $# -gt 0 ]; do
  case "$1" in
    --categories) CATS="$2"; shift;;
    --batch) BATCH="$2"; shift;;
    --yes) YES=1;;
    --dry-run) DRY=1;;
    --force) FORCE=1;;
    -*) echo "unknown option $1"; exit 1;;
    *) SRC="$1";;
  esac; shift
done
[ -f "$SRC" ] || { sed -n '2,12p' "$0"; exit 1; }

# Build the list of candidates.
LIST="$(mktemp)"
if head -1 "$SRC" | grep -q $'^package\t'; then
  awk -F'\t' -v cats=",$CATS," 'NR>1 && $3=="disable" && index(cats, ","$2",") {print $1}' "$SRC" > "$LIST"
else
  grep -vE '^\s*(#|$)' "$SRC" | awk '{print $1}' > "$LIST"
fi
PROT="$BK/protected.txt"
INSTALLED="$BK/packages-all.txt"

echo "Candidates: $(wc -l < "$LIST" | tr -d ' ')  (batch $BATCH, categories: $CATS)"
[ "$DRY" = 1 ] && { cat "$LIST"; rm -f "$LIST"; exit 0; }
mkdir -p "$BK/screens"

batch_n=0; count=0; batch_pkgs=""
pause_after_batch() {
  batch_n=$((batch_n + 1))
  sleep 1.5
  shot="$BK/screens/batch-$batch_n.png"
  "$TV" screenshot "$shot" >/dev/null || true
  focus="$("$TV" shell "dumpsys window | grep -E 'mCurrentFocus|mFocusedApp'" 2>/dev/null | tr -s ' ' | head -2 || true)"
  echo
  echo "Batch $batch_n done: $batch_pkgs"
  echo "Screenshot: $shot"
  echo "Focus now:  $focus"
  if echo "$focus" | grep -qiE 'Application Error|AppNotResponding|crash'; then
    echo "!! A crash dialog is on screen. Consider undoing this batch."
  fi
  if [ "$YES" != 1 ]; then
    printf 'Look at the screenshot. [Enter] continue, [u] undo this batch, [q] stop: '
    read -r ans || ans=q
    case "$ans" in
      u|U) for p in $batch_pkgs; do "$HERE/enable.sh" "$p"; done; echo "Batch $batch_n undone.";;
      q|Q) echo "Stopped."; rm -f "$LIST"; exit 0;;
    esac
  fi
  batch_pkgs=""; count=0
}

while IFS= read -r p || [ -n "$p" ]; do
  [ -n "$p" ] || continue
  if ! grep -q "package:.*=$p\$" "$INSTALLED" && ! grep -qx "package:$p" "$INSTALLED"; then
    echo "skip (not installed): $p"; continue
  fi
  if [ "$FORCE" != 1 ] && [ -f "$PROT" ] && grep -qx "$p" "$PROT"; then
    echo "skip (protected, see $PROT): $p"; continue
  fi
  if grep -qx "$p" "$BK/disabled-by-us.txt"; then echo "skip (already disabled): $p"; continue; fi
  # One pm command per shell call. Chained commands can silently stop after the first.
  out="$("$TV" shell "pm disable-user --user 0 $p" 2>&1 || true)"
  if echo "$out" | grep -q "new state: disabled"; then
    echo "disabled: $p"
    echo "$p" >> "$BK/disabled-by-us.txt"
    batch_pkgs="$batch_pkgs $p"; count=$((count + 1))
  else
    echo "FAILED:   $p -> $(echo "$out" | tr -d '\r' | head -1)"
  fi
  [ "$count" -ge "$BATCH" ] && pause_after_batch
done < "$LIST"
[ "$count" -gt 0 ] && pause_after_batch
rm -f "$LIST"
echo "Log: $BK/disabled-by-us.txt   Restore: $BK/restore.sh"
