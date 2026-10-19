# examples/ — per-brand notes

Each file starts with a header that says **which apps claim HOME on that platform and what to
do before disabling the stock launcher**, followed by package names that community debloat
lists commonly flag. Names change between firmware versions, so treat them as hints:
`python3 tools/classify.py backup/latest --show <package>` shows a package's activities and
services before you decide.

| TV / box | File |
| --- | --- |
| Any Google TV set (TCL, Hisense, Sony 2021+, Chromecast with Google TV, onn.) | [google-tv.md](google-tv.md) |
| Xiaomi Mi TV / Mi Box (PatchWall) | [xiaomi.md](xiaomi.md) |
| TCL (Android TV and Google TV) | [tcl.md](tcl.md) |
| Hisense (Android TV / Google TV; not VIDAA-only sets) | [hisense.md](hisense.md) |
| Sony Bravia | [sony.md](sony.md) |
| Nvidia Shield TV | [nvidia-shield.md](nvidia-shield.md) |
| OnePlus / Realme / Oppo TVs | [oneplus-realme.md](oneplus-realme.md) |

Not covered: Samsung Tizen, LG webOS, Amazon Fire TV (different OS, no `pm` tooling).
