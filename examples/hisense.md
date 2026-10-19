# Hisense (Android TV / Google TV models; VIDAA-only sets are not Android)

## HOME claimers
Google TV models: `com.google.android.apps.tv.launcherx` + `com.google.android.tungsten.setupwraith` (see google-tv.md).
Android TV models: `com.google.android.tvlauncher`; some add `com.hisense.launcher`.

## Commonly disabled
| Package | Bucket |
| --- | --- |
| `com.hisense.tv.ads*`, `com.hisense.adservice` | content |
| `com.hisense.freetv`, `com.hisense.vidaa.free`, `com.hisense.contenthub` | content |
| `com.hisense.datacollect`, `com.hisense.statistics`, `com.hisense.uploadlog` | telemetry |
| `com.hisense.tvmanager`, `com.hisense.cleaner` | background |
| `com.hisense.browser`, `com.hisense.gallery`, `com.hisense.appstore` | preloaded |
| `com.hisense.anyview`, `com.hisense.tvshare`, `com.hisense.eshare` | cast duplicate (ask) |
| `com.hisense.update*`, `com.hisense.ota` | update (ask) |

## Keep
`com.hisense.tv` / `com.hisense.tvinput*` / `com.hisense.livetv`, `com.hisense.settings`, `com.hisense.remote*`,
`com.hisense.bt*`, `com.hisense.cec`, `com.mediatek.*`, `com.mstar.*`.
