package net.cleanhome.launcher;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Compact glass dropdown under the Inputs pill: HDMI ports, AV and Live TV.
 * Inputs come from the TV input framework (TvInputManager); switching is done by
 * asking the TV app to show the passthrough channel of that input.
 */
public final class InputsPopup {

    private static final class Entry {
        final String label; final Runnable action;
        Entry(String l, Runnable a) { label = l; action = a; }
    }

    private InputsPopup() {}

    public static void show(final Activity act, View anchor) {
        final List<Entry> entries = collect(act);
        LayoutInflater inf = LayoutInflater.from(act);
        LinearLayout list = new LinearLayout(act);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setBackgroundResource(R.drawable.popup_bg);
        int p = (int) (8 * act.getResources().getDisplayMetrics().density);
        list.setPadding(p, p, p, p);

        final PopupWindow popup = new PopupWindow(list,
                act.getResources().getDimensionPixelSize(R.dimen.popup_width),
                ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setOutsideTouchable(true);
        popup.setElevation(0);

        if (entries.isEmpty()) {
            TextView t = (TextView) inf.inflate(R.layout.item_input, list, false);
            t.setText(R.string.inputs_none);
            t.setTypeface(Fonts.regular());
            t.setFocusable(false);
            list.addView(t);
        }
        for (final Entry e : entries) {
            TextView t = (TextView) inf.inflate(R.layout.item_input, list, false);
            t.setText(e.label);
            t.setTypeface(Fonts.regular());
            t.setOnClickListener(v -> { popup.dismiss(); e.action.run(); });
            list.addView(t);
        }
        int xoff = anchor.getWidth() - popup.getWidth();
        int yoff = (int) (8 * act.getResources().getDisplayMetrics().density);
        popup.showAsDropDown(anchor, xoff, yoff);
        if (list.getChildCount() > 0) list.getChildAt(0).requestFocus();
    }

    private static List<Entry> collect(final Activity act) {
        List<Entry> out = new ArrayList<>();
        TvInputManager tim = (TvInputManager) act.getSystemService(Context.TV_INPUT_SERVICE);
        List<TvInputInfo> inputs = new ArrayList<>();
        boolean hasTuner = false;
        if (tim != null) {
            try { inputs.addAll(tim.getTvInputList()); } catch (Exception ignored) {}
        }
        List<Entry> hdmi = new ArrayList<>(), other = new ArrayList<>();
        for (final TvInputInfo info : inputs) {
            if (info.getType() == TvInputInfo.TYPE_TUNER) { hasTuner = true; continue; }
            if (!info.isPassthroughInput()) continue;
            CharSequence l = null;
            try { l = info.loadLabel(act); } catch (Exception ignored) {}
            String label = (l == null || l.length() == 0) ? typeName(info) : l.toString();
            Entry e = new Entry(label, () -> switchTo(act, info));
            if (info.getType() == TvInputInfo.TYPE_HDMI) hdmi.add(e); else other.add(e);
        }
        Collections.sort(hdmi, (a, b) -> a.label.compareToIgnoreCase(b.label));
        Collections.sort(other, (a, b) -> a.label.compareToIgnoreCase(b.label));
        out.addAll(hdmi);
        out.addAll(other);
        boolean wantLive = act.getResources().getBoolean(R.bool.inputs_show_live_tv);
        if (wantLive && (hasTuner || liveTvIntent(act) != null)) {
            out.add(new Entry(act.getString(R.string.input_live_tv), () -> openLiveTv(act)));
        }
        return out;
    }

    private static String typeName(TvInputInfo info) {
        switch (info.getType()) {
            case TvInputInfo.TYPE_HDMI: return "HDMI";
            case TvInputInfo.TYPE_COMPOSITE: return "AV";
            case TvInputInfo.TYPE_COMPONENT: return "Component";
            case TvInputInfo.TYPE_SCART: return "SCART";
            case TvInputInfo.TYPE_VGA: return "VGA";
            case TvInputInfo.TYPE_DVI: return "DVI";
            case TvInputInfo.TYPE_DISPLAY_PORT: return "DisplayPort";
            case TvInputInfo.TYPE_SVIDEO: return "S-Video";
            default: return "Input";
        }
    }

    private static void switchTo(Activity act, TvInputInfo info) {
        Uri uri = TvContract.buildChannelUriForPassthroughInput(info.getId());
        Intent view = new Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        String liveTv = act.getString(R.string.live_tv_package).trim();
        if (!liveTv.isEmpty()) view.setPackage(liveTv);
        if (start(act, view)) return;
        view.setPackage(null);
        if (start(act, view)) return;
        // Maker-specific input picker from config.xml, if given.
        String comp = act.getString(R.string.input_switch_component).trim();
        if (!comp.isEmpty()) {
            ComponentName cn = ComponentName.unflattenFromString(comp);
            if (cn != null && start(act, new Intent(Intent.ACTION_MAIN).setComponent(cn).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))) return;
        }
        Toast.makeText(act, act.getString(R.string.cannot_open, info.getId()), Toast.LENGTH_SHORT).show();
    }

    private static Intent liveTvIntent(Activity act) {
        PackageManager pm = act.getPackageManager();
        String liveTv = act.getString(R.string.live_tv_package).trim();
        if (!liveTv.isEmpty()) {
            Intent i = pm.getLeanbackLaunchIntentForPackage(liveTv);
            if (i == null) i = pm.getLaunchIntentForPackage(liveTv);
            if (i != null) return i;
        }
        Intent view = new Intent(Intent.ACTION_VIEW, TvContract.Channels.CONTENT_URI);
        List<ResolveInfo> handlers = pm.queryIntentActivities(view, 0);
        if (!handlers.isEmpty()) return view;
        for (String pkg : new String[]{"com.google.android.tv", "com.android.tv"}) {
            Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
            if (i != null) return i;
        }
        return null;
    }

    private static void openLiveTv(Activity act) {
        Intent i = liveTvIntent(act);
        if (i == null || !start(act, i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))) {
            Toast.makeText(act, act.getString(R.string.cannot_open, act.getString(R.string.input_live_tv)), Toast.LENGTH_SHORT).show();
        }
    }

    private static boolean start(Activity act, Intent i) {
        try {
            if (i.resolveActivity(act.getPackageManager()) == null) return false;
            act.startActivity(i);
            return true;
        } catch (ActivityNotFoundException | SecurityException e) {
            return false;
        }
    }
}
