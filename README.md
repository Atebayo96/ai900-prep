# Android TV debloat + Apple TV style launcher

A toolkit for turning a stock Android TV / Google TV set into a quiet, pitch-black, Apple TV style
home screen, entirely over ADB from a computer on the same Wi-Fi. Nothing is uninstalled and
everything is logged, so one script puts the TV back the way it was.

Works on Android TV / Google TV sets and boxes (Xiaomi, TCL, Hisense, Sony, OnePlus, Realme,
Nvidia Shield, Chromecast with Google TV, ...). It does **not** work on Samsung Tizen, LG webOS
or Fire TV. The launcher needs Android 8.0 (API 26) or newer.

```
tools/        connect, backup, classify, debloat, install, screenshot, key injection
launcher/     the launcher app (Java, AndroidX RecyclerView, Outfit font, no network, no blur)
examples/     per-brand notes: which apps claim HOME and which packages are usually bloat
build-and-install.sh   build the APK and install it as the default home
backup/<timestamp>/    created by tools/backup.sh; contains restore.sh
```

(This repository also holds an unrelated AI-900 study page in `index.html`.)

---

## 0. Before you start

**On the TV:** Settings → Device Preferences → About → press OK on "Build" 7 times → back →
Developer options → **USB debugging ON** (and **Network / ADB debugging ON** if shown). Keep the TV on.

**USB power warning.** Some boxes cut power to their USB port while USB debugging is on: a USB
speaker, hub or drive plugged into the TV can go dead until debugging is switched off again.
Check what is plugged into the TV before you begin, and switch debugging off when you are done.

**On the computer:** `adb` (Android platform-tools), Python 3, JDK 17+ (only for building the
launcher). macOS: `brew install android-platform-tools openjdk@17`.

If you are driving this with a coding assistant, paste the prompt at the bottom of this file and
tell it you cloned this repo so it reuses `tools/` and `launcher/`.

---

## 1. Connect

```
tools/connect.sh              # scans the ARP table / mDNS for a host with port 5555 open
tools/connect.sh 192.168.1.50 # or give the IP (TV: Settings → Network → your network)
```

The TV shows **"Allow USB debugging?"** the first time: choose *Allow* and tick *Always allow*.
The script writes `.tv.env` (IP, port, backend) and everything else goes through `tools/tv.sh`:

```
tools/tv.sh shell "pm list packages | grep -i netflix"
tools/tv.sh screenshot shot.png
tools/tv.sh key DPAD_DOWN DPAD_CENTER
tools/tv.sh push local.apk /data/local/tmp/x.apk
```

**macOS "No route to host"** although `nc -z <ip> 5555` succeeds: that is macOS Local Network
privacy blocking adb. Do not debug it. `connect.sh` detects it and switches to the Python
`adb-shell` client in `.venv` (`TV_BACKEND=py`). That client reuses `~/.android/adbkey`, pushes
files as base64 chunks through the shell when the native push hangs, and verifies the md5.

**Android 14 `adb install` hangs**: it is waiting for an on-screen confirmation. The scripts never
use `adb install`; they push the APK and run `pm install -r -g` over the shell.

---

## 2. Back up

```
tools/backup.sh
```

Creates `backup/<timestamp>/` (and `backup/latest` → it) with the full package list,
enabled/disabled/system/user lists, `getprop`, the system/secure/global settings tables,
`dumpsys package`, every app that claims HOME, the current default home, TV inputs and a
screenshot. It also writes **`restore.sh`**: every package the later steps disable is appended to
`disabled-by-us.txt`, and `restore.sh` re-enables them all and restores the original home.

---

## 3. Classify and debloat

```
python3 tools/classify.py backup/latest
```

Sorts every package into buckets and writes `backup/latest/plan.tsv` plus `protected.txt`:

| Bucket | Default | Examples |
| --- | --- | --- |
| core | keep, never disabled | TV/tuner/HDMI input services, remote + Bluetooth, keyboard, Play Store/Services, settings |
| launcher | keep until step 4 | stock home, PatchWall, Google TV setup wizard |
| telemetry | disable | analytics, reporting, crash/usage uploaders |
| content | disable | PatchWall, recommendation rows, ad clients, content hubs |
| background | disable | cleaners, boosters, "TV manager", notification/reminder services |
| preloaded | disable | bundled browser, gallery, game centre, app stores |
| streaming / voice / cast / update | **ask** | Netflix…, Assistant, Chromecast built-in, Miracast, OTA |
| unknown | ask | manufacturer packages no rule matched |

Manufacturer package names often say nothing about what they do; look before deciding:

```
python3 tools/classify.py backup/latest --show com.xiaomi.mitv.xmsf
```

Edit the `recommend` column of `plan.tsv` (`disable` / `keep`) to match your answers, then:

```
tools/debloat.sh backup/latest/plan.tsv                 # telemetry,content,background,preloaded
tools/debloat.sh backup/latest/plan.tsv --categories streaming,voice,cast,update   # after the checklist
tools/debloat.sh my-list.txt                            # or a plain list of package names
```

`debloat.sh` runs **one `pm disable-user --user 0` per shell call** (chained commands can stop
silently after the first), in batches of five, takes a screenshot after every batch into
`backup/latest/screens/`, prints the focused window (so a crash dialog is obvious) and waits for
you: Enter to continue, `u` to undo the batch, `q` to stop. Protected packages are refused unless
`--force`. Nothing is ever uninstalled.

Re-enable a single app at any time:

```
tools/enable.sh com.xiaomi.mitv.tvmanager
```

---

## 4. The launcher

```
./build-and-install.sh                   # build, install, set as default home
./build-and-install.sh --disable-stock   # ... and then disable the stock launcher (asks per package)
```

The build script finds (or downloads) an Android SDK, reads the TV's SDK level and passes it as
`minSdk`, builds the debug APK, installs it with `pm install -r -g`, grants the location
permission the Wi-Fi name needs, lists every app that claims HOME, sets ours with
`cmd package set-home-activity`, presses HOME, takes a screenshot and checks the resumed activity.

On most TVs the stock home still appears at this point: it declares its HOME intent filter with a
higher priority than any third-party launcher can, and Android starts the highest priority home
before it consults the default. That is why `--disable-stock` exists: it disables the other HOME
apps one at a time, pressing HOME and taking a screenshot after each. On Google TV the
setup wizard `com.google.android.tungsten.setupwraith` takes over HOME once the stock launcher is
disabled; the script notices, offers to disable it too and records it for `restore.sh`.
See `examples/google-tv.md`.

### What it looks like

- Pitch-black background (optional dimmed wallpaper).
- Top left: greeting, a large thin clock and the date in **Outfit** (SIL Open Font License, bundled).
- Top right: frosted pill buttons **Inputs**, **Wi-Fi** (shows the network name, or Ethernet/Offline)
  and **Settings**. Inputs opens a compact glass dropdown listing HDMI ports, AV and Live TV from
  the TV input framework and switches through the TV app.
- Bottom: a slightly lighter rounded band holding your four dock apps. Above it, the focused dock
  app's name in large light type with an ambient glow in a colour sampled from its banner.
- Press Down: the header slides away and the remaining apps appear in a 4-column grid.
- One translucent glass highlight springs from tile to tile; the focused tile scales up slightly
  and shows its name underneath. No per-tile borders.
- D-pad is deterministic: Up/Down move exactly one row, Up from the dock goes to the pills, the
  ends of the top bar and of every row clamp (nothing falls through). The last focused tile is
  remembered across restarts. Back returns to the top; Menu / long-press opens App info.
- Light on a 1 GB TV: no blur, no video, no network polling (clock and Wi-Fi update from system
  broadcasts), banners cached at 400×225 RGB-565.

### Change the dock, hide apps, greeting

Edit **`launcher/app/src/main/res/values/config.xml`**, then `./build-and-install.sh`:

```xml
<string-array name="dock_packages">     <!-- four package names, left to right -->
    <item>com.netflix.ninja</item>
    <item>com.google.android.youtube.tv</item>
    <item>com.amazon.amazonvideo.livingroom</item>
    <item>com.plexapp.android</item>
</string-array>
<string-array name="hidden_packages">   <!-- never shown in the grid -->
<string name="greeting_name">Sam</string>
<string name="clock_format">system</string>   <!-- system | 12 | 24 -->
```

Find a package name: `tools/tv.sh shell "pm list packages | grep -i youtube"`.
Missing dock packages are skipped and the dock is topped up from the grid.

### Wallpaper

Put an image at `launcher/app/src/main/res/drawable-nodpi/wallpaper.png` (or `.jpg`, 1920×1080)
and rebuild. It is picked up automatically; `wallpaper_dim_percent` in `config.xml` darkens it
(55 by default). Delete the file for pitch black.

### Inputs on unusual TVs

If the dropdown cannot switch inputs, set `live_tv_package` (the TV app) and/or
`input_switch_component` (the maker's own input picker, `pkg/activity`) in `config.xml`.
List the TV app with `tools/tv.sh shell "dumpsys tv_input | head -40"`.

### Wi-Fi name shows just "Wi-Fi"

Android hides the SSID unless the app holds a location permission **and** location is on.
`set-launcher.sh` grants the permission; turn location on in Settings → Device Preferences →
Location, or accept the plain "Wi-Fi" label.

---

## 5. Verify

```
tools/walk-launcher.sh      # scripted D-pad tour, one screenshot per step in screens/walk-*/
tools/key.sh DPAD_DOWN DPAD_RIGHT DPAD_CENTER   # any sequence, then a screenshot
tools/screenshot.sh
```

Look at every image: focus must move exactly one tile per press, never leave the row at the ends,
and the highlight must sit on the focused tile.

---

## 6. Restore

```
tools/restore-all.sh                 # runs backup/latest/restore.sh
backup/<timestamp>/restore.sh        # or a specific backup
tools/enable.sh <package>            # one app
```

`restore.sh` re-enables the original home apps, resets the default home, re-enables every package in
`disabled-by-us.txt` and presses HOME. The launcher stays installed but inactive;
`tools/tv.sh shell "pm uninstall net.cleanhome.launcher"` removes it.

Finally, switch **USB debugging off** on the TV (USB power).

---

## Prompt for a coding assistant

> I cloned this repo (Android TV debloat + launcher). Reuse `tools/` and `launcher/`; only edit
> `launcher/app/src/main/res/values/config.xml` for my dock apps. Connect to my TV over ADB
> (tools/connect.sh), back up (tools/backup.sh), classify (tools/classify.py) and show me the
> plan; ask me with a checklist which streaming, voice, cast and update apps I use before
> touching those; disable in batches with tools/debloat.sh and look at every screenshot; build and
> install the launcher with ./build-and-install.sh, then --disable-stock; walk the UI with
> tools/walk-launcher.sh and fix anything you see. Ask me what is plugged into the TV's USB ports
> first and remind me to turn USB debugging off at the end.
