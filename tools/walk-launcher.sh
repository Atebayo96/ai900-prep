#!/usr/bin/env bash
# walk-launcher.sh — scripted D-pad tour of the launcher with a screenshot after every step.
# Look at each image: focus must move exactly one tile/row per press and never fall off the ends.
set -eu
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$HERE/.." && pwd)"
DIR="$ROOT/screens/walk-$(date +%Y%m%d-%H%M%S)"
mkdir -p "$DIR"
step() { name="$1"; shift; [ $# -gt 0 ] && "$HERE/tv.sh" key "$@"; sleep 0.7; "$HERE/tv.sh" screenshot "$DIR/$name.png" >/dev/null; echo "$DIR/$name.png"; }
"$HERE/tv.sh" key HOME >/dev/null; sleep 1.5
step 01-home
step 02-dock-right DPAD_RIGHT
step 03-dock-right-again DPAD_RIGHT
step 04-dock-end DPAD_RIGHT DPAD_RIGHT DPAD_RIGHT   # must clamp at the last dock tile
step 05-top-bar DPAD_UP
step 06-top-right-end DPAD_RIGHT DPAD_RIGHT DPAD_RIGHT  # must stay on Settings
step 07-top-left-end DPAD_LEFT DPAD_LEFT DPAD_LEFT DPAD_LEFT  # must stay on Inputs
step 08-inputs-open DPAD_CENTER
step 09-inputs-closed BACK
step 10-back-to-dock DPAD_DOWN
step 11-grid-row1 DPAD_DOWN
step 12-grid-row2 DPAD_DOWN
step 13-grid-row3 DPAD_DOWN
step 14-grid-left-end DPAD_LEFT DPAD_LEFT DPAD_LEFT DPAD_LEFT DPAD_LEFT
step 15-grid-up DPAD_UP
step 16-grid-up-to-dock DPAD_UP DPAD_UP DPAD_UP
step 17-back-noop BACK
echo "Tour saved in $DIR"
