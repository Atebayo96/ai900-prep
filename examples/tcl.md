# TCL (Android TV 9–11 and Google TV)

## HOME claimers
Google TV models: `com.google.android.apps.tv.launcherx` + `com.google.android.tungsten.setupwraith` (see google-tv.md).
Older Android TV models: `com.google.android.tvlauncher`. Some firmwares add a TCL home wrapper (`com.tcl.launcher*`).

## Commonly disabled
| Package | Bucket |
| --- | --- |
| `com.tcl.waterfall.overseas` / `com.tcl.tclchannel` | content — "TCL Channel" rows |
| `com.tcl.ad`, `com.tcl.adsupport` | content |
| `com.tcl.usercenter`, `com.tcl.smartTVPlus` | background |
| `com.tcl.notereminder`, `com.tcl.ui_mediaCenter`, `com.tcl.gallery` | preloaded / background |
| `com.tcl.browser`, `com.tcl.appmarket2`, `com.tcl.appstore` | preloaded |
| `com.tcl.tvweishi` | background — cleaner |
| `com.tcl.uploadlog`, `com.tcl.bi`, `com.tcl.statistics`, `com.tcl.xian.StartandroidService` | telemetry |
| `com.tcl.tshare` / `com.tcl.eshare` | cast duplicate (ask) |
| `com.tcl.update` / `com.tcl.upgrade*` | update (ask) |

## Keep
`com.tcl.tv` (the TV/tuner app), `com.tcl.tvinput`, `com.tcl.settings`, `com.tcl.hdmi*`, `com.tcl.cec*`,
`com.tcl.remotecontrol`, `com.tcl.bt*`, `com.mediatek.*`/`com.mstar.*`/`com.realtek.*`.
