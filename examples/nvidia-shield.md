# Nvidia Shield TV (Android TV 9–11)

## HOME claimers
`com.google.android.tvlauncher` only (plus `com.google.android.tvrecommendations` feeding it).
Shield firmware is clean compared with TV sets; the main wins are the launcher ads and recommendations.

## Commonly disabled
| Package | Bucket |
| --- | --- |
| `com.google.android.tvrecommendations` | content |
| `com.nvidia.shield.ask` / `com.nvidia.feedback` | telemetry — Shield feedback/analytics |
| `com.nvidia.tegrazone3` | preloaded — GeForce NOW/games store (ask) |
| `com.nvidia.shieldtech.hooks` | keep — controller/accessory support |
| `com.nvidia.ota` | update — keep unless you never want updates (ask) |
| `com.plexapp.mediaserver.smb` | streaming — Plex Media Server (ask) |

## Keep
`com.nvidia.shieldtech.*`, `com.nvidia.shield.remote*`, `com.nvidia.tvinput*`, `com.nvidia.stats` is telemetry but harmless.
