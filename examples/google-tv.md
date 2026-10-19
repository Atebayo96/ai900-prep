# Google TV (TCL, Hisense, Sony 2021+, Chromecast with Google TV, onn., Walmart, Xiaomi 2023+)

## HOME claimers — read before disabling the stock launcher
Google TV has **two** apps with the HOME intent:

| Package | What it is |
| --- | --- |
| `com.google.android.apps.tv.launcherx` | the Google TV home screen |
| `com.google.android.tungsten.setupwraith` | the first-run setup wizard; it also claims HOME |

Order of operations (tools/set-launcher.sh does exactly this):
1. Install the new launcher and run `cmd package set-home-activity --user 0 net.cleanhome.launcher/.HomeActivity`.
2. Press HOME, take a screenshot, confirm `dumpsys activity activities | grep ResumedActivity` shows `net.cleanhome.launcher`.
3. Only then `pm disable-user --user 0 com.google.android.apps.tv.launcherx`.
4. Press HOME again. If the screen shows the setup/“Google TV” splash or the resumed activity is
   `com.google.android.tungsten.setupwraith`, the wizard has taken over HOME. Disable it too:
   `pm disable-user --user 0 com.google.android.tungsten.setupwraith`, set the home activity again, press HOME.
   Both packages are written to `backup/latest/disabled-by-us.txt`, so `restore.sh` brings them back.

Older Android TV (not Google TV) sets have `com.google.android.tvlauncher` instead and no setupwraith problem.

## Commonly disabled on Google TV
| Package | Bucket |
| --- | --- |
| `com.google.android.tvrecommendations` | content — feeds the stock launcher's rows |
| `com.google.android.apps.tv.dreamx` | content — ambient/screensaver (keep if you like it) |
| `com.google.android.backdrop` | content — ambient mode backdrop |
| `com.google.android.feedback` | telemetry |
| `com.google.android.play.games` | preloaded |
| `com.google.android.videos` | streaming — Google TV app/Play Movies (ask) |
| `com.google.android.youtube.tvmusic` | streaming (ask) |
| `com.google.android.katniss` | voice — Assistant / voice search (ask) |
| `com.google.android.apps.mediashell` | cast — Chromecast built-in (ask) |
| `com.google.android.tts` | voice — text-to-speech; keep unless you never use Assistant |

## Keep
`com.google.android.tv` (Live Channels / the TV app), `com.android.tv.settings`, `com.google.android.tv.remote.service`
(remote pairing), `com.google.android.inputmethod.latin` (Gboard), `com.google.android.gms`, `com.android.vending`,
`com.google.android.tv.frameworkpackagestubs`, anything with `tvinput`/`cec`/`hdmi` in the name.
