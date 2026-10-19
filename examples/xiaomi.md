# Xiaomi Mi TV / Mi Box / Redmi TV (PatchWall, Android TV 9–11; 2023+ sets are Google TV — see google-tv.md too)

## HOME claimers
| Package | What it is |
| --- | --- |
| `com.mitv.tvhome` | PatchWall (the Xiaomi home with ads and content rows) |
| `com.google.android.tvlauncher` | stock Android TV home (Xiaomi lets you toggle between the two) |

Set the new home with set-launcher.sh first, then disable **both** of the above. The Home button on
Xiaomi remotes has a separate "PatchWall" button on some models (`KEYCODE_HOME` vs a vendor key);
if the PatchWall key still opens something after disabling, check `dumpsys activity activities`.

## Commonly disabled
| Package | Bucket |
| --- | --- |
| `com.mitv.tvhome` | launcher (PatchWall) — after the new home is set |
| `com.mitv.quickadvertise` | content — PatchWall ad client |
| `com.xiaomi.mitv.payment` | content |
| `com.xiaomi.mitv.analytics`, `com.xiaomi.mitv.xmsf` (push/reporting) | telemetry |
| `com.xiaomi.mitv.tvmanager` | background — "cleaner/booster" |
| `com.xiaomi.mibox.gamecenter` | content |
| `com.mitv.assistant.gallery` | preloaded gallery |
| `com.xiaomi.mitv.appstore` | preloaded store (ask) |
| `com.xiaomi.mitv.share` / `com.mitv.share` | cast — Mi share/mirroring duplicate (ask) |
| `com.xiaomi.mitv.upgrade` | update — Xiaomi OTA client (ask; keep if you want firmware updates) |
| `com.xiaomi.mitv.settings` | **keep** — Xiaomi settings pages (picture/sound) live here |

## Keep
`com.mitv.tvinput`, `com.mediatek.*`, `com.xiaomi.mitv.remotecontrol`, `com.xiaomi.mitv.settings`,
`com.android.tv.settings`, Google core packages.
