#!/usr/bin/env bash
# key.sh KEY [KEY...] — inject remote presses, then print a screenshot path.
#   tools/key.sh DPAD_DOWN DPAD_DOWN DPAD_CENTER
#   tools/key.sh HOME            (KEYCODE_ prefix is optional)
set -eu
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
"$HERE/tv.sh" key "$@"
sleep 0.6
"$HERE/screenshot.sh"
