package net.cleanhome.launcher;

import android.content.ComponentName;

/** One launchable app. */
public final class AppEntry {
    public final String packageName;
    public final ComponentName component;
    public final String label;
    /** True when the activity was found through LEANBACK_LAUNCHER (so it has a banner). */
    public final boolean leanback;

    public AppEntry(String packageName, ComponentName component, String label, boolean leanback) {
        this.packageName = packageName;
        this.component = component;
        this.label = label;
        this.leanback = leanback;
    }

    @Override public boolean equals(Object o) {
        return o instanceof AppEntry && ((AppEntry) o).component.equals(component);
    }

    @Override public int hashCode() { return component.hashCode(); }
}
