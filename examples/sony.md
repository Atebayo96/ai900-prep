# Sony Bravia (Android TV 8–10; 2021+ models are Google TV, see google-tv.md)

## HOME claimers
Android TV: `com.google.android.tvlauncher`. Google TV: `com.google.android.apps.tv.launcherx` + setupwraith.
Sony does not ship its own launcher.

## Commonly disabled
| Package | Bucket |
| --- | --- |
| `com.sony.dtv.tvx` | content — "TV channel list/recommendations" rows (keep if you use the EPG) |
| `com.sony.dtv.bravia.notification`, `com.sony.dtv.braviacore.promo` | content |
| `com.sony.dtv.sonyselect`, `com.sony.dtv.sonyshelf` | content — Sony Select store rows |
| `com.sony.dtv.bravia.core.analytics`, `com.sony.dtv.cae`, `com.sony.dtv.feedback` | telemetry |
| `com.sony.dtv.photosharing`, `com.sony.dtv.photosharingplus` | preloaded |
| `com.sony.dtv.networkapp.wifidirect` | cast duplicate (ask) |
| `com.sony.dtv.update*`, `com.sony.dtv.sysupdate` | update (ask) |
| `com.sony.dtv.bravia.helpguide*`, `com.sony.dtv.helpcenter` | background |

## Keep
`com.sony.dtv.tvinput.*` (ALL of them: tuner, HDMI, external inputs), `com.sony.dtv.tvsystem*`,
`com.sony.dtv.sourcemanager`, `com.sony.dtv.settings*`, `com.sony.dtv.btirrc*` (remote), `com.sony.dtv.hdmicec*`,
`com.sony.dtv.ime*`, `com.sony.dtv.tvx` if you watch broadcast TV through the Sony app.
