#!/usr/bin/env python3
"""
classify.py — sort the TV's packages into buckets and write a disable plan.

  python3 tools/classify.py backup/latest            -> backup/latest/plan.tsv + protected.txt, prints a report
  python3 tools/classify.py backup/latest --show pkg -> components of one package (what does it do?)

Buckets:
  core        never touch: TV/tuner/HDMI input stack, remote + Bluetooth, keyboard, Play Store/Services, system
  launcher    stock launcher stack (disable only after the new launcher is the default home)
  telemetry   manufacturer analytics / reporting / diagnostics            -> disable
  content     PatchWall, recommendation rows, ads, content/channel feeds    -> disable
  background  duplicate share/cast helpers, cleaners, boosters, managers    -> disable (cast variants -> ask)
  preloaded   bundled apps (browser, gallery, games, stores)                -> disable
  streaming   Netflix, YouTube, Prime ...                                   -> ask
  voice       Assistant / voice search / TTS                                -> ask
  cast        Chromecast built-in, Miracast                                 -> ask
  update      firmware / app updaters                                       -> ask
  unknown     nothing matched; look at its components                       -> ask

Patterns are hints collected from community debloat lists; the report shows each package's
activities/services so a human can confirm before anything is disabled.
"""
import os
import re
import sys
from collections import defaultdict

# ----------------------------------------------------------------------------- rules
PROTECTED_EXACT = {
    "android", "com.android.systemui", "com.android.settings", "com.android.tv.settings",
    "com.android.bluetooth", "com.android.bluetoothmidiservice", "com.android.inputdevices",
    "com.android.keychain", "com.android.shell", "com.android.packageinstaller", "com.android.certinstaller",
    "com.android.externalstorage", "com.android.mtp", "com.android.location.fused", "com.android.phone",
    "com.android.server.telecom", "com.android.proxyhandler", "com.android.captiveportallogin",
    "com.android.vpndialogs", "com.android.dynsystem", "com.android.localtransport", "com.android.storagemanager",
    "com.android.companiondevicemanager", "com.android.intentresolver", "com.android.se", "com.android.nfc",
    "com.android.tv", "com.google.android.tv", "com.android.providers.tv",
    "com.google.android.gms", "com.google.android.gsf", "com.google.android.gsf.login", "com.android.vending",
    "com.google.android.ext.services", "com.google.android.ext.shared", "com.google.android.packageinstaller",
    "com.google.android.permissioncontroller", "com.google.android.modulemetadata",
    "com.google.android.tv.remote.service", "com.google.android.tv.frameworkpackagestubs",
    "com.google.android.inputmethod.latin", "com.google.android.tv.inputmethod", "com.android.inputmethod.latin",
    "com.google.android.leanbackkeyboard", "com.google.android.tungsten.overscan",
    "com.google.android.apps.tv.launcherx.wrapper",
    "com.google.android.sdksandbox", "com.google.android.adservices.api", "com.google.mainline.telemetry",
    "com.google.android.onetimeinitializer", "com.google.android.setupwizard", "com.google.android.syncadapters.contacts",
    "com.google.android.tv.bugreportsender",
}
PROTECTED_RE = re.compile(r"""(?x)
    ^com\.android\.(providers|networkstack|wifi|cellbroadcast|hotspot2|carrierconfig|ons|stk|tv\.)   # framework
  | ^com\.google\.android\.(overlay|networkstack|cellbroadcast|captiveportallogin|wifi|tv\.(frameworkpackagestubs|remote|tvinput|axel))
  | ^com\.google\.android\.tungsten\.setupwraith$      # Google TV setup wizard; only set-launcher.sh touches it
  | ^com\.(mediatek|mstar|realtek|amlogic|droidlogic|novatek|hisilicon|rtk|mtk|mt|sei|cvte|skyworth\.tvos|vestel)\.   # chipset / TV firmware
  | (tvinput|tv_input|tvinputservice|inputsource|inputservice|sourcemanager|tvservice|tvsystem|tvmiddleware|\.tis$|tuner|dvb|atsc|isdb|hdmi|\bcec\b|hdmicec|cecservice)
  | (bluetooth|btsettings|remotecontrol|remote\.service|rcservice|\bir\b|irkey|keyboard|inputmethod|ime$|leanbackkeyboard)
  | (^com\.android\.(systemui|settings)|\.settings$|tvsettings|\.tv\.settings)
  | (factory|engineer|hwtest|aging|burnin|sysoper|tvos|platform|framework|service\.core|coreservice)
""", re.I)

LAUNCHER_RE = re.compile(r"(tvlauncher|launcherx|leanbacklauncher|\.launcher$|\.home$|patchwall$|mitv\.tvhome|tcl\.launcher|hisense\.launcher|vidaa|sony\.dtv\.launcher)", re.I)

RULES = [  # (bucket, recommendation, regex)
    ("telemetry", "disable", re.compile(r"(analytic|metric|telemetry|statistic|\bstat\b|stats\b|report(er|ing|service)?$|logupload|uploadlog|logservice|feedback|crash|diagnos|tracking|tracker|appusage|usagestat|datacollect|collector|bigdata|clientreport|survey|insight|cloudlog|dataus|bugreport|\bqos\b|experience|quality)", re.I)),
    ("content", "disable", re.compile(r"(patchwall|recommend|promo|advert|\bads?\b|adsdk|adservice|adplay|adsupport|banner|contenthub|content(provider|service)|channel|waterfall|discover|curat|cinema|featured|entertain|screensaver|dream|backdrop|ambient|wallpaper|quickadvertise|mitv\.(tvhome|payment|shop)|tvrecommendations|appsmate|gamecenter|tclchannel|tcl\.waterfall|vidaa\.?(free|channels)|hisense\.?(ad|hub)|sony\.dtv\.(tvx|bravia\.?(core|notification|promo)))", re.I)),
    ("background", "disable", re.compile(r"(cleaner|clean\b|boost|speedup|memory|optimiz|accelerat|tvmanager|security|antivirus|filemanager|explorer|mediacenter|mediaplayer|ui_mediacenter|notereminder|usercenter|member|mall|shop|payment|wallet|account(center)?|sync(service)?$|push(service)?$|msg(service)?$|message|notify|notification(center)?|reminder|weather|calendar|calculator|clock$|email|mail|help|support|faq|feedback|tutorial|demo|guide|useragreement|privacy|eula)", re.I)),
    ("preloaded", "disable", re.compile(r"(browser|chrome$|gallery|photo|music$|player$|game|appstore|appmarket|market|store$|office|reader|pdf|calendar|news|sport|fitness|karaoke|education|kids|aptoide|opera|firefox|vlc$)", re.I)),
        ("streaming", "ask", re.compile(r"(netflix|youtube|amazon\.amazonvideo|primevideo|disney|hulu|\bhbo|wbd\.stream|\bmax\b|plex|spotify|tubi|pluto|peacock|paramount|cbs\.ott|apple\.atve|dazn|crunchyroll|twitch|kodi|jellyfin|emby|stremio|zattoo|\bsling\b|\bespn\b|vudu|showtime|starz|\bcrave\b|iplayer|\bitv\b|channel4|\bsky\b|now\.tv|rakuten|\bviki\b|\bmubi\b|curiosity|deezer|tidal|pandora|iheart|tunein|google\.android\.(videos|youtube|play\.games)|mx\.player|mxtech|smarttube|iptv|tivimate)", re.I)),
    ("voice", "ask", re.compile(r"(katniss|voice|assistant|speech|\btts\b|texttospeech|alexa|xiaoai|xiaomi\.voice|aivs|nuance|asr\b)", re.I)),
    ("cast", "ask", re.compile(r"(mediashell|chromecast|\bcast\b|miracast|wfd|wifidisplay|mirror|screenshare|screen_share|dlna|airplay|airscreen|eshare|anyview|multiscreen|lebo|hpplay|smartview|allshare|tvshare|share$|mishare|mitv\.share|quickshare|nearby)", re.I)),
    ("update", "ask", re.compile(r"(update|upgrade|\bota\b|fota|fwupdate|swupdate|systemupdate|appupdate|mitv\.upgrade|tcl\.update|hisense\.update|sony\.dtv\.update)", re.I)),
]

COMPONENT_HINTS = [
    ("telemetry", re.compile(r"(Analytic|Metric|Telemetry|Statistic|Report|LogUpload|Collector|Tracking|Usage|Crash)", re.I)),
    ("content", re.compile(r"(Recommend|Promo|Advert|\bAd\b|Ads|Banner|Channel|Feed|Content|Discover)", re.I)),
    ("update", re.compile(r"(Update|Upgrade|Ota|Fota)", re.I)),
    ("cast", re.compile(r"(Cast|Miracast|Wfd|Mirror|Dlna)", re.I)),
]

MAKER_RE = re.compile(r"^(com|net|org|tv)\.(xiaomi|mi|mitv|tcl|hisense|vidaa|sony|oneplus|realme|oppo|nvidia|philips|tpv|sharp|skyworth|coocaa|vestel|toshiba|hitachi|jvc|panasonic|haier|konka|changhong|hkc|metz|bush|thomson|funai|kivi|xgimi|anker|nebula|dangbei|wanjia|iflytek|bbk|vivo|lenovo|letv|leeco|aoc|mecool|ugoos|beelink|t95|x96)\b", re.I)


# ----------------------------------------------------------------------------- parsing
def read_packages(bk):
    pkgs = {}
    with open(os.path.join(bk, "packages-all.txt"), errors="replace") as f:
        for line in f:
            line = line.strip()
            if not line.startswith("package:"):
                continue
            body = line[len("package:"):]
            if "=" in body:
                path, _, name = body.rpartition("=")
            else:
                path, name = "", body
            pkgs[name] = path
    return pkgs


def read_set(bk, fname):
    out = set()
    p = os.path.join(bk, fname)
    if not os.path.exists(p):
        return out
    with open(p, errors="replace") as f:
        for line in f:
            line = line.strip()
            if line.startswith("package:"):
                out.add(line[len("package:"):].rpartition("=")[2] if "=" in line else line[len("package:"):])
    return out


def read_components(bk):
    """Map package -> set of component class names, scraped from dumpsys package."""
    comps = defaultdict(set)
    p = os.path.join(bk, "dumpsys-package.txt")
    if not os.path.exists(p):
        return comps
    rx = re.compile(r"\b([a-zA-Z][\w]*(?:\.[\w]+)+)/(\.?[\w.$]+)")
    with open(p, errors="replace") as f:
        for line in f:
            for pkg, cls in rx.findall(line):
                if cls.startswith("."):
                    cls = cls[1:]
                comps[pkg].add(cls.rsplit(".", 1)[-1])
    return comps


def read_home_claimers(bk):
    out = set()
    p = os.path.join(bk, "home-activities.txt")
    if os.path.exists(p):
        with open(p, errors="replace") as f:
            for m in re.finditer(r"\b([A-Za-z0-9_.]+)/[A-Za-z0-9_.$]+", f.read()):
                out.add(m.group(1))
    return out


# ----------------------------------------------------------------------------- classification
def classify(pkg, comps, is_system, is_home):
    if pkg in PROTECTED_EXACT or PROTECTED_RE.search(pkg):
        if LAUNCHER_RE.search(pkg) or is_home:
            return "launcher", "keep", "claims HOME (disable only via set-launcher.sh)"
        return "core", "keep", "system / TV input / remote / keyboard / Play stack"
    if is_home or LAUNCHER_RE.search(pkg):
        return "launcher", "keep", "claims HOME (disable only via set-launcher.sh)"
    for bucket, rec, rx in RULES:
        m = rx.search(pkg)
        if m:
            return bucket, rec, f"name matches '{m.group(0)}'"
    joined = " ".join(sorted(comps))
    for bucket, rx in COMPONENT_HINTS:
        m = rx.search(joined)
        if m:
            rec = "disable" if bucket in ("telemetry", "content") else "ask"
            return bucket, rec, f"component '{m.group(0)}'"
    if not is_system:
        return "user-installed", "keep", "installed by you"
    if MAKER_RE.search(pkg):
        return "unknown", "ask", "manufacturer package, purpose unclear: check components"
    if pkg.startswith(("com.android.", "com.google.android.")):
        return "core", "keep", "AOSP/Google system package not matched by any rule"
    return "unknown", "ask", "no rule matched"


def main():
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    bk = sys.argv[1]
    pkgs = read_packages(bk)
    comps = read_components(bk)
    system = read_set(bk, "packages-system.txt")
    disabled = read_set(bk, "packages-disabled.txt")
    homes = read_home_claimers(bk)

    if "--show" in sys.argv:
        name = sys.argv[sys.argv.index("--show") + 1]
        print(name, "(system)" if name in system else "(user)", "DISABLED" if name in disabled else "")
        for c in sorted(comps.get(name, [])):
            print("  ", c)
        return

    rows = []
    for pkg in sorted(pkgs):
        bucket, rec, why = classify(pkg, comps.get(pkg, set()), pkg in system, pkg in homes)
        if pkg in disabled:
            rec, why = "keep", "already disabled; " + why
        summary = ", ".join(sorted(comps.get(pkg, []))[:6])
        rows.append((pkg, bucket, rec, why, summary))

    with open(os.path.join(bk, "plan.tsv"), "w") as f:
        f.write("package\tbucket\trecommend\treason\tcomponents\n")
        for r in rows:
            f.write("\t".join(r) + "\n")
    with open(os.path.join(bk, "protected.txt"), "w") as f:
        for r in rows:
            if r[1] in ("core", "launcher"):
                f.write(r[0] + "\n")

    order = ["telemetry", "content", "background", "preloaded", "streaming", "voice", "cast", "update", "unknown", "launcher", "user-installed", "core"]
    by = defaultdict(list)
    for r in rows:
        by[r[1]].append(r)
    for b in order:
        if not by[b]:
            continue
        print(f"\n== {b.upper()}  ({len(by[b])})")
        for pkg, _, rec, why, summary in by[b]:
            flag = {"disable": "D", "ask": "?", "keep": " "}[rec]
            print(f" [{flag}] {pkg:<52} {why}")
            if summary and b not in ("core", "user-installed", "streaming"):
                print(f"      {summary}")
    n_d = sum(1 for r in rows if r[2] == "disable")
    n_a = sum(1 for r in rows if r[2] == "ask")
    print(f"\n{len(rows)} packages: {n_d} suggested to disable, {n_a} to ask about, rest kept.")
    print(f"Plan: {os.path.join(bk, 'plan.tsv')}   Protected list: {os.path.join(bk, 'protected.txt')}")
    print("Edit the 'recommend' column (disable/keep) then run: tools/debloat.sh backup/latest/plan.tsv")


if __name__ == "__main__":
    main()
