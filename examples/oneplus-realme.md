# OnePlus TV / Realme TV / Oppo TV (Android TV 9–11, "OxygenPlay"/"ColorOS TV" layers)

## HOME claimers
`com.google.android.tvlauncher` (stock) and the brand layer home:
`com.oneplus.tv.launcher` / `net.oneplus.tv.launcher` (OxygenPlay), `com.realme.tv.launcher` / `com.oppo.tv.launcher`.
Set the new home first, then disable both.

## Commonly disabled
| Package | Bucket |
| --- | --- |
| `*.oxygenplay*`, `com.oneplus.tv.contentservice`, `com.realme.tv.contentservice` | content — the OxygenPlay/content rows |
| `*.tv.analytics`, `*.tv.feedback`, `*.tv.statistics`, `com.oneplus.tv.dataplatform` | telemetry |
| `com.oneplus.tv.connect`, `com.realme.tv.connect` (phone app bridge) | background (ask — needed for the phone remote app) |
| `*.tv.gallery`, `*.tv.browser`, `*.tv.appstore` | preloaded |
| `*.tv.share`, `*.tv.cast`, `*.tv.mirror` | cast duplicate (ask) |
| `*.tv.update`, `*.tv.ota` | update (ask) |

## Keep
`*.tv.tvinput*`, `*.tv.settings`, `*.tv.remote*`, `*.tv.bluetooth*`, `*.tv.hdmi*`, `com.mediatek.*`.
