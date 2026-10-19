#!/usr/bin/env bash
# screenshot.sh [out.png] — grab the TV screen (default: screens/<timestamp>.png) and print the path.
set -eu
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$HERE/.." && pwd)"
out="${1:-$ROOT/screens/$(date +%Y%m%d-%H%M%S).png}"
mkdir -p "$(dirname "$out")"
"$HERE/tv.sh" screenshot "$out"
