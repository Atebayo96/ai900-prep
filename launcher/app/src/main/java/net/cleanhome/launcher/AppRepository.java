package net.cleanhome.launcher;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Finds every launchable app and splits it into dock + grid according to config.xml.
 * Leanback apps are preferred; plain LAUNCHER apps (no banner) are added after them.
 */
public final class AppRepository {

    public static final class Result {
        public final List<AppEntry> dock = new ArrayList<>();
        public final List<AppEntry> grid = new ArrayList<>();
    }

    private AppRepository() {}

    public static Result load(Context c) {
        PackageManager pm = c.getPackageManager();
        String self = c.getPackageName();
        Set<String> hidden = new HashSet<>(Arrays.asList(c.getResources().getStringArray(R.array.hidden_packages)));
        hidden.add(self);

        // package -> entry; leanback first so it wins over the phone-style activity.
        Map<String, AppEntry> byPackage = new LinkedHashMap<>();
        collect(pm, Intent.CATEGORY_LEANBACK_LAUNCHER, true, hidden, byPackage);
        collect(pm, Intent.CATEGORY_LAUNCHER, false, hidden, byPackage);

        List<AppEntry> all = new ArrayList<>(byPackage.values());
        final Collator collator = Collator.getInstance();
        all.sort((a, b) -> collator.compare(a.label, b.label));

        Result r = new Result();
        String[] dockPkgs = c.getResources().getStringArray(R.array.dock_packages);
        Set<String> inDock = new HashSet<>();
        for (String p : dockPkgs) {
            AppEntry e = byPackage.get(p.trim());
            if (e != null && inDock.add(e.packageName)) r.dock.add(e);
        }
        for (AppEntry e : all) {
            if (!inDock.contains(e.packageName)) r.grid.add(e);
        }
        // Top the dock up to four so the band never looks half empty.
        while (r.dock.size() < 4 && !r.grid.isEmpty()) {
            r.dock.add(r.grid.remove(0));
        }
        return r;
    }

    private static void collect(PackageManager pm, String category, boolean leanback,
                                Set<String> hidden, Map<String, AppEntry> out) {
        Intent probe = new Intent(Intent.ACTION_MAIN).addCategory(category);
        List<ResolveInfo> found;
        try {
            found = pm.queryIntentActivities(probe, 0);
        } catch (Exception e) {
            return;
        }
        for (ResolveInfo ri : found) {
            if (ri.activityInfo == null) continue;
            String pkg = ri.activityInfo.packageName;
            if (hidden.contains(pkg) || out.containsKey(pkg)) continue;
            CharSequence lbl = ri.loadLabel(pm);
            String label = lbl == null ? pkg : lbl.toString().trim();
            out.put(pkg, new AppEntry(pkg,
                    new ComponentName(pkg, ri.activityInfo.name), label, leanback));
        }
    }
}
