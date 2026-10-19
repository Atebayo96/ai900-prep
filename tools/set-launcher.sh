#!/usr/bin/env bash
# set-launcher.sh — install the launcher APK, make it the default home, optionally disable the stock one.
#
#   tools/set-launcher.sh launcher/app/build/outputs/apk/debug/app-debug.apk
#   tools/set-launcher.sh <apk> --disable-stock      also disable the other HOME apps (asks per package)
#   tools/set-launcher.sh --home-only                just (re)set the default home + check it
#
# Steps: push + "pm install -r -g" (no on-screen prompt, so it cannot hang on Android 14),
# grant the location permission the Wi-Fi name needs, list every app that claims HOME,
# set ours with "cmd package set-home-activity", press HOME, screenshot, and verify that
# our activity is the one resumed. On Google TV the setup wizard (tungsten.setupwraith)
# can take over after the stock launcher is disabled; this script detects and handles that.
set -eu
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$HERE/.." && pwd)"
TV="$HERE/tv.sh"
PKG="net.cleanhome.launcher"
ACT="$PKG/.HomeActivity"
BK="$ROOT/backup/latest"
APK=""; DISABLE=0; HOME_ONLY=0; YES=0
while [ $# -gt 0 ]; do
  case "$1" in
    --disable-stock) DISABLE=1;;
    --home-only) HOME_ONLY=1;;
    --yes) YES=1;;
    *) APK="$1";;
  esac; shift
done
record() { [ -d "$BK" ] && echo "$1" >> "$BK/disabled-by-us.txt" || echo "(no backup dir; not recorded) $1"; }
ask() { [ "$YES" = 1 ] && return 0; printf '%s [y/N] ' "$1"; read -r a || a=n; case "$a" in y|Y) return 0;; *) return 1;; esac; }

if [ "$HOME_ONLY" != 1 ]; then
  [ -f "$APK" ] || { echo "APK not found: $APK (run ./build-and-install.sh)"; exit 1; }
  echo "== Installing $APK"
  "$TV" install "$APK"
  SDK="$("$TV" shell "getprop ro.build.version.sdk" | tr -d '\r ')"
  echo "== Granting permissions for the Wi-Fi network name (SDK $SDK)"
  for perm in android.permission.ACCESS_FINE_LOCATION android.permission.ACCESS_COARSE_LOCATION; do
    "$TV" shell "pm grant $PKG $perm" >/dev/null 2>&1 || true
  done
  [ "${SDK:-0}" -ge 33 ] && "$TV" shell "pm grant $PKG android.permission.NEARBY_WIFI_DEVICES" >/dev/null 2>&1 || true
  "$TV" shell "appops set $PKG COARSE_LOCATION allow" >/dev/null 2>&1 || true
  "$TV" shell "appops set $PKG FINE_LOCATION allow" >/dev/null 2>&1 || true
fi

echo "== Apps that claim HOME"
HOMES="$("$TV" shell "cmd package query-activities -a android.intent.action.MAIN -c android.intent.category.HOME" | grep -oE '[A-Za-z0-9_.]+/[A-Za-z0-9_.$]+' | sort -u || true)"
echo "$HOMES" | sed 's/^/  /'

echo "== Setting default home to $ACT"
"$TV" shell "cmd package set-home-activity --user 0 $ACT" || "$TV" shell "cmd package set-home-activity $ACT"
"$TV" key HOME >/dev/null; sleep 2
mkdir -p "$ROOT/screens"
SHOT="$ROOT/screens/home-$(date +%Y%m%d-%H%M%S).png"
"$TV" screenshot "$SHOT" >/dev/null
RESUMED="$("$TV" shell "dumpsys activity activities | grep -E 'mResumedActivity|ResumedActivity' | head -1" | tr -d '\r' || true)"
echo "Resumed: $RESUMED"
echo "Screenshot: $SHOT"
if echo "$RESUMED" | grep -q "$PKG"; then echo "OK: the new launcher is on screen."; else echo "!! Something else is on screen. Check the screenshot before disabling anything."; fi

if [ "$DISABLE" = 1 ]; then
  echo "== Disabling other HOME apps (never the setup wizard first)"
  for comp in $HOMES; do
    p="${comp%%/*}"
    [ "$p" = "$PKG" ] && continue
    case "$p" in com.google.android.tungsten.setupwraith) continue;; esac
    if ask "Disable $p ?"; then
      out="$("$TV" shell "pm disable-user --user 0 $p" || true)"
      echo "  $out"; echo "$out" | grep -q "new state: disabled" && record "$p"
      "$TV" key HOME >/dev/null; sleep 2
      SHOT="$ROOT/screens/home-after-$p.png"; "$TV" screenshot "$SHOT" >/dev/null; echo "  screenshot: $SHOT"
      RESUMED="$("$TV" shell "dumpsys activity activities | grep -E 'mResumedActivity|ResumedActivity' | head -1" | tr -d '\r' || true)"
      echo "  resumed: $RESUMED"
      if echo "$RESUMED" | grep -q tungsten.setupwraith; then
        echo "  !! Google TV's setup wizard took over HOME (see examples/google-tv.md)."
        if ask "  Disable com.google.android.tungsten.setupwraith too?"; then
          out="$("$TV" shell "pm disable-user --user 0 com.google.android.tungsten.setupwraith" || true)"
          echo "  $out"; echo "$out" | grep -q "new state: disabled" && record com.google.android.tungsten.setupwraith
          "$TV" shell "cmd package set-home-activity --user 0 $ACT" >/dev/null || true
          "$TV" key HOME >/dev/null; sleep 2
          SHOT="$ROOT/screens/home-after-setupwraith.png"; "$TV" screenshot "$SHOT" >/dev/null; echo "  screenshot: $SHOT"
        fi
      fi
    fi
  done
fi
echo "Done. Walk the UI with tools/walk-launcher.sh and look at every screenshot."
