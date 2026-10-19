#!/usr/bin/env bash
# build-and-install.sh — build the launcher (debug APK) and install it on the TV as the default home.
#
#   ./build-and-install.sh                   build + install + set as home
#   ./build-and-install.sh --disable-stock   ...and disable the stock launcher (asks per package)
#   ./build-and-install.sh --no-install      just build launcher/app/build/outputs/apk/debug/app-debug.apk
#
# Needs: JDK 17+ (brew install openjdk@17, or Android Studio's bundled JDK), Python 3, internet
# for the first build. If no Android SDK is found it downloads the command-line tools into
# launcher/.sdk (about 300 MB) and installs platform 34 + build-tools 34.
set -eu
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
L="$ROOT/launcher"
INSTALL=1; EXTRA=""
for a in "$@"; do case "$a" in --no-install) INSTALL=0;; --disable-stock|--yes) EXTRA="$EXTRA $a";; esac; done

# --- JDK
if [ -z "${JAVA_HOME:-}" ] && [ -d "/Applications/Android Studio.app/Contents/jbr/Contents/Home" ]; then
  export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
fi
if [ -n "${JAVA_HOME:-}" ]; then export PATH="$JAVA_HOME/bin:$PATH"; fi
if ! command -v java >/dev/null 2>&1; then
  echo "No java found. Install JDK 17+: brew install openjdk@17 (macOS) / apt install openjdk-17-jdk (Debian)"; exit 1
fi
JV="$(java -version 2>&1 | head -1 | sed -E 's/.*"([0-9]+)(\.[0-9]+)*.*/\1/')"
[ "${JV:-0}" -ge 17 ] || { echo "Java $JV found; need 17+."; exit 1; }

# --- Android SDK
SDK=""
for c in "${ANDROID_HOME:-}" "${ANDROID_SDK_ROOT:-}" "$HOME/Library/Android/sdk" "$HOME/Android/Sdk" "/opt/android-sdk" "$L/.sdk"; do
  [ -n "$c" ] && [ -d "$c/platforms" ] && { SDK="$c"; break; }
done
if [ -z "$SDK" ]; then
  SDK="$L/.sdk"
  echo "No Android SDK found; downloading command-line tools into $SDK"
  case "$(uname -s)" in Darwin) os=mac;; *) os=linux;; esac
  mkdir -p "$SDK/cmdline-tools"
  curl -sSL -o "$SDK/cmdtools.zip" "https://dl.google.com/android/repository/commandlinetools-${os}-11076708_latest.zip"
  (cd "$SDK/cmdline-tools" && unzip -q ../cmdtools.zip && rm -rf latest && mv cmdline-tools latest) && rm -f "$SDK/cmdtools.zip"
  yes | "$SDK/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$SDK" "platform-tools" "platforms;android-34" "build-tools;34.0.0" >/dev/null
fi
echo "sdk.dir=$SDK" > "$L/local.properties"
export ANDROID_HOME="$SDK"
export PATH="$SDK/platform-tools:$PATH"

# --- minSdk from the TV when it is connected (so the APK matches the TV's Android version)
MIN=""
if [ -f "$ROOT/.tv.env" ]; then
  MIN="$("$ROOT/tools/tv.sh" shell "getprop ro.build.version.sdk" 2>/dev/null | tr -d '\r ' || true)"
fi
case "$MIN" in ''|*[!0-9]*) MIN="";; esac
if [ -n "$MIN" ] && [ "$MIN" -lt 26 ]; then echo "TV runs API $MIN; the launcher needs Android 8.0 (API 26) or newer."; exit 1; fi
PROP=""; [ -n "$MIN" ] && PROP="-PminSdk=$MIN" && echo "TV SDK level: $MIN"

# --- build
echo "== Building"
(cd "$L" && ./gradlew --quiet assembleDebug $PROP)
APK="$L/app/build/outputs/apk/debug/app-debug.apk"
ls -la "$APK"
[ "$INSTALL" = 1 ] || exit 0

# --- install + set home
[ -f "$ROOT/.tv.env" ] || { echo "TV not connected. Run tools/connect.sh first, then: tools/set-launcher.sh $APK"; exit 1; }
# shellcheck disable=SC2086
"$ROOT/tools/set-launcher.sh" "$APK" $EXTRA
