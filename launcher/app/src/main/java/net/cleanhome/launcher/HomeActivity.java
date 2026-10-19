package net.cleanhome.launcher;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Apple TV style home screen.
 *
 * Navigation is a small deterministic state machine over three zones:
 *   TOP  (Inputs, Wi-Fi, Settings pills)
 *   DOCK (up to four tiles in the band)
 *   GRID (4-column RecyclerView that slides in from below)
 * Up/Down always move exactly one row. Left/Right clamp at the ends and never fall through.
 */
public class HomeActivity extends Activity implements GridAdapter.Listener {

    private enum Zone { TOP, DOCK, GRID }

    private static final String PREFS = "home";

    private View root, header, wallpaperDim;
    private ImageView wallpaper;
    private TextView greeting, clock, date, dockName;
    private TextView pillInputs, pillWifi, pillSettings;
    private LinearLayout dockBand;
    private RecyclerView grid;
    private GridLayoutManager gridLm;
    private GlowView glow;
    private HighlightView highlight;

    private BannerLoader loader;
    private GridAdapter adapter;
    private final List<TileView> dockTiles = new ArrayList<>();
    private final List<AppEntry> dockApps = new ArrayList<>();
    private final TextView[] pills = new TextView[3];

    private Zone zone = Zone.DOCK;
    private int topIdx = 0, dockIdx = 0, gridIdx = 0;
    private int cols = 4;
    private boolean gridShown = false;
    private boolean resumed = false;
    private boolean appsLoaded = false;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private ConnectivityManager.NetworkCallback netCallback;

    // ------------------------------------------------------------------ lifecycle

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Fonts.init(this);
        setContentView(R.layout.activity_home);

        root = findViewById(R.id.root);
        header = findViewById(R.id.header);
        wallpaper = findViewById(R.id.wallpaper);
        wallpaperDim = findViewById(R.id.wallpaper_dim);
        greeting = findViewById(R.id.greeting);
        clock = findViewById(R.id.clock);
        date = findViewById(R.id.date);
        dockName = findViewById(R.id.dock_name);
        pillInputs = findViewById(R.id.pill_inputs);
        pillWifi = findViewById(R.id.pill_wifi);
        pillSettings = findViewById(R.id.pill_settings);
        dockBand = findViewById(R.id.dock_band);
        grid = findViewById(R.id.grid);
        glow = findViewById(R.id.glow);
        highlight = findViewById(R.id.highlight);

        greeting.setTypeface(Fonts.light());
        clock.setTypeface(Fonts.thin());
        date.setTypeface(Fonts.light());
        dockName.setTypeface(Fonts.light());
        pills[0] = pillInputs; pills[1] = pillWifi; pills[2] = pillSettings;
        for (int i = 0; i < pills.length; i++) {
            final int idx = i;
            pills[i].setTypeface(Fonts.medium());
            pills[i].setOnFocusChangeListener((v, has) -> { if (has) onPillFocused(idx); });
        }
        pillInputs.setOnClickListener(v -> InputsPopup.show(this, v));
        pillWifi.setOnClickListener(v -> openSettings(Settings.ACTION_WIFI_SETTINGS));
        pillSettings.setOnClickListener(v -> openSettings(Settings.ACTION_SETTINGS));

        loader = new BannerLoader(this);
        cols = Math.max(1, getResources().getInteger(R.integer.grid_columns));
        int tw = getResources().getDimensionPixelSize(R.dimen.grid_tile_w);
        int th = getResources().getDimensionPixelSize(R.dimen.grid_tile_h);
        int gap = getResources().getDimensionPixelSize(R.dimen.grid_gap);
        adapter = new GridAdapter(loader, this, tw, th, gap);
        gridLm = new GridLayoutManager(this, cols);
        grid.setLayoutManager(gridLm);
        grid.setAdapter(adapter);
        grid.setItemAnimator(null);
        grid.setHasFixedSize(true);
        grid.setItemViewCacheSize(cols * 2);
        ViewGroup.LayoutParams glp = grid.getLayoutParams();
        glp.width = cols * (tw + gap) + gap;
        grid.setLayoutParams(glp);

        setupWallpaper();
        dockName.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> syncGlowCenter());
        root.getViewTreeObserver().addOnPreDrawListener(preDraw);
        register(packageReceiver, packageFilter());
        loadApps();
    }

    @Override protected void onResume() {
        super.onResume();
        resumed = true;
        updateClock();
        register(tickReceiver, tickFilter());
        registerNetwork();
    }

    @Override protected void onPause() {
        super.onPause();
        resumed = false;
        unregisterQuietly(tickReceiver);
        unregisterNetwork();
        saveFocus();
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        unregisterQuietly(packageReceiver);
        root.getViewTreeObserver().removeOnPreDrawListener(preDraw);
        io.shutdownNow();
    }

    /** HOME pressed while we are already on screen: go back to the top like Apple TV. */
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (resumed && gridShown) {
            zone = Zone.DOCK;
            dockIdx = Math.min(dockIdx, Math.max(0, dockTiles.size() - 1));
            showGrid(false, true);
            focusDock(dockIdx);
        }
    }

    // ------------------------------------------------------------------ apps

    private void loadApps() {
        io.execute(() -> {
            final AppRepository.Result r = AppRepository.load(HomeActivity.this);
            main.post(() -> applyApps(r));
        });
    }

    private void applyApps(AppRepository.Result r) {
        boolean first = !appsLoaded;
        appsLoaded = true;
        // A package broadcast (an app updating itself) must not move the focus: remember it,
        // touch only what changed, then put the focus back where it was.
        Zone keepZone = zone;
        int keepTop = topIdx, keepDock = dockIdx, keepGrid = gridIdx;
        if (!r.dock.equals(dockApps)) {
            dockApps.clear();
            dockApps.addAll(r.dock);
            buildDock();
        } else {
            refreshDockBanners();
        }
        if (!adapter.sameItems(r.grid)) adapter.setItems(r.grid);
        else adapter.notifyDataSetChanged();   // rebind banners that may have been evicted
        if (r.dock.isEmpty() && r.grid.isEmpty()) {
            Toast.makeText(this, R.string.no_apps, Toast.LENGTH_LONG).show();
        }
        if (first) {
            restoreFocus();
        } else {
            zone = keepZone; topIdx = keepTop; dockIdx = keepDock; gridIdx = keepGrid;
            clampFocus();
        }
    }

    private void refreshDockBanners() {
        for (int i = 0; i < dockTiles.size(); i++) {
            final TileView t = dockTiles.get(i);
            final int idx = i;
            loader.load(dockApps.get(i), (a, bmp, accent) -> {
                if (bmp != null) t.banner.setImageBitmap(bmp);
                if (zone == Zone.DOCK && dockIdx == idx) glow.setColor(accent, true);
            });
        }
    }

    private void buildDock() {
        dockBand.removeAllViews();
        dockTiles.clear();
        LayoutInflater inf = LayoutInflater.from(this);
        int tw = getResources().getDimensionPixelSize(R.dimen.dock_tile_w);
        int th = getResources().getDimensionPixelSize(R.dimen.dock_tile_h);
        int gap = getResources().getDimensionPixelSize(R.dimen.dock_tile_gap);
        for (int i = 0; i < dockApps.size(); i++) {
            final AppEntry app = dockApps.get(i);
            final int idx = i;
            final TileView t = (TileView) inf.inflate(R.layout.item_tile, dockBand, false);
            t.setTileSize(tw, th);
            t.app = app;
            t.label.setText(app.label);
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) t.getLayoutParams();
            lp.leftMargin = i == 0 ? 0 : gap;
            t.setLayoutParams(lp);
            t.setOnClickListener(v -> launch(app));
            t.setOnLongClickListener(v -> { appInfo(app); return true; });
            t.setOnFocusChangeListener((v, has) -> {
                t.onFocusProxy(has);
                if (has) onDockFocused(idx);
            });
            loader.load(app, (a, bmp, accent) -> {
                if (bmp != null) t.banner.setImageBitmap(bmp);
                if (zone == Zone.DOCK && dockIdx == idx) glow.setColor(accent, true);
            });
            dockBand.addView(t);
            dockTiles.add(t);
        }
    }

    private void launch(AppEntry app) {
        PackageManager pm = getPackageManager();
        Intent i = pm.getLeanbackLaunchIntentForPackage(app.packageName);
        if (i == null) i = pm.getLaunchIntentForPackage(app.packageName);
        if (i == null) i = new Intent(Intent.ACTION_MAIN).setComponent(app.component);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            startActivity(i);
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, getString(R.string.cannot_open, app.label), Toast.LENGTH_SHORT).show();
        }
    }

    private void appInfo(AppEntry app) {
        Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + app.packageName));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { startActivity(i); } catch (ActivityNotFoundException ignored) {}
    }

    private void openSettings(String action) {
        Intent i = new Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(i);
        } catch (ActivityNotFoundException e) {
            if (!Settings.ACTION_SETTINGS.equals(action)) openSettings(Settings.ACTION_SETTINGS);
        }
    }

    // ------------------------------------------------------------------ navigation

    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        if (e.getAction() != KeyEvent.ACTION_DOWN) return super.dispatchKeyEvent(e);
        switch (e.getKeyCode()) {
            case KeyEvent.KEYCODE_DPAD_UP:    move(0, -1); return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:  move(0, 1);  return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:  move(-1, 0); return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT: move(1, 0);  return true;
            case KeyEvent.KEYCODE_BACK:
                if (gridShown) move(0, -1000);
                else if (zone == Zone.TOP && !dockTiles.isEmpty()) { zone = Zone.DOCK; focusDock(dockIdx); }
                return true; // a home screen never finishes on Back
            case KeyEvent.KEYCODE_MENU: {
                View f = getCurrentFocus();
                if (f instanceof TileView && ((TileView) f).app != null) { appInfo(((TileView) f).app); return true; }
                return super.dispatchKeyEvent(e);
            }
            default:
                return super.dispatchKeyEvent(e);
        }
    }

    /** dx/dy in {-1,0,1}; dy of -1000 means "all the way up" (Back from the grid). */
    private void move(int dx, int dy) {
        if (!appsLoaded) return;
        boolean up = dy < 0, down = dy > 0;
        switch (zone) {
            case TOP:
                if (dx != 0) { topIdx = clamp(topIdx + dx, 0, pills.length - 1); focusTop(topIdx); }
                else if (down && !dockTiles.isEmpty()) { zone = Zone.DOCK; focusDock(dockIdx); }
                break;
            case DOCK:
                if (dx != 0) { dockIdx = clamp(dockIdx + dx, 0, dockTiles.size() - 1); focusDock(dockIdx); }
                else if (up) { zone = Zone.TOP; focusTop(topIdx); }
                else if (down && adapter.getItemCount() > 0) {
                    zone = Zone.GRID;
                    gridIdx = Math.min(dockIdx, adapter.getItemCount() - 1);
                    showGrid(true, true);
                    focusGrid(gridIdx);
                }
                break;
            case GRID: {
                int n = adapter.getItemCount();
                int row = gridIdx / cols, col = gridIdx % cols;
                int lastRow = (n - 1) / cols;
                if (dy <= -1000 || (up && row == 0)) {
                    zone = Zone.DOCK;
                    dockIdx = clamp(col, 0, dockTiles.size() - 1);
                    showGrid(false, true);
                    focusDock(dockIdx);
                } else if (up) {
                    focusGrid(gridIdx - cols);
                } else if (down) {
                    if (row < lastRow) focusGrid(Math.min(gridIdx + cols, n - 1));
                } else if (dx < 0) {
                    if (col > 0) focusGrid(gridIdx - 1);
                } else if (dx > 0) {
                    if (col < cols - 1 && gridIdx + 1 < n) focusGrid(gridIdx + 1);
                }
                break;
            }
        }
    }

    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }

    private void focusTop(int i) {
        if (pills.length == 0) return;
        pills[i].requestFocus();
    }

    private void focusDock(int i) {
        if (dockTiles.isEmpty()) return;
        dockTiles.get(clamp(i, 0, dockTiles.size() - 1)).requestFocus();
    }

    private void focusGrid(final int i) {
        final int n = adapter.getItemCount();
        if (n == 0) return;
        gridIdx = clamp(i, 0, n - 1);
        focusGridAttempt(gridIdx, 0);
    }

    private void focusGridAttempt(final int i, final int attempt) {
        View v = gridLm.findViewByPosition(i);
        if (v == null) {
            if (attempt == 0) gridLm.scrollToPositionWithOffset(i, 0);
            if (attempt < 12) main.postDelayed(() -> focusGridAttempt(i, attempt + 1), 16);
            return;
        }
        keepRowInView(v);
        v.requestFocus();
    }

    /** Scroll just enough that the focused row stays inside a comfortable band. */
    private void keepRowInView(View v) {
        int padTop = grid.getPaddingTop();
        int padBottom = grid.getPaddingBottom();
        int top = v.getTop(), bottom = v.getBottom();
        int limit = grid.getHeight() - padBottom;
        if (top < padTop) grid.smoothScrollBy(0, top - padTop);
        else if (bottom > limit) grid.smoothScrollBy(0, bottom - limit);
    }

    private void onPillFocused(int idx) {
        zone = Zone.TOP; topIdx = idx;
        highlight.animate().alpha(0f).setDuration(160).start();
        glow.show(false);
        dockName.animate().alpha(0.45f).setDuration(200).start();
    }

    private void onDockFocused(int idx) {
        zone = Zone.DOCK; dockIdx = idx;
        AppEntry app = dockApps.get(idx);
        highlight.setVisibility(View.VISIBLE);
        highlight.animate().alpha(1f).setDuration(160).start();
        glow.show(true);
        glow.setColor(loader.cachedAccent(app), true);
        if (!app.label.contentEquals(dockName.getText())) {
            dockName.animate().alpha(0f).setDuration(90).withEndAction(() -> {
                dockName.setText(app.label);
                dockName.animate().alpha(1f).setDuration(160).start();
            }).start();
        } else {
            dockName.animate().alpha(1f).setDuration(160).start();
        }
    }

    @Override public void onTileFocus(int position, TileView tile) {
        zone = Zone.GRID;
        if (position >= 0) gridIdx = position;
        highlight.setVisibility(View.VISIBLE);
        highlight.animate().alpha(1f).setDuration(160).start();
    }

    @Override public void onTileClick(AppEntry app) { launch(app); }
    @Override public void onTileLongClick(AppEntry app) { appInfo(app); }

    private void showGrid(boolean show, boolean animate) {
        if (gridShown == show) return;
        gridShown = show;
        float h = root.getHeight();
        if (show) grid.setVisibility(View.VISIBLE);
        float headerTo = show ? -h : 0f;
        float gridTo = show ? 0f : h;
        if (!animate || h == 0) {
            header.setTranslationY(headerTo);
            grid.setTranslationY(gridTo);
            glow.setAlpha(show ? 0f : 1f);
            if (!show) grid.setVisibility(View.INVISIBLE);
            return;
        }
        header.animate().translationY(headerTo).setDuration(340).setInterpolator(new DecelerateInterpolator(1.8f)).start();
        glow.animate().alpha(show ? 0f : 1f).setDuration(300).start();
        grid.animate().translationY(gridTo).setDuration(340).setInterpolator(new DecelerateInterpolator(1.8f))
                .withEndAction(() -> { if (!gridShown) grid.setVisibility(View.INVISIBLE); }).start();
    }

    private void syncGlowCenter() {
        dockName.getLocationInWindow(locA);
        root.getLocationInWindow(locB);
        // Centre on the name, slightly below it so the band's top edge sits inside the glow.
        glow.setCenter(locA[0] - locB[0] + dockName.getWidth() / 2f - header.getTranslationX(),
                locA[1] - locB[1] + dockName.getHeight() * 0.9f - header.getTranslationY());
    }

    // ------------------------------------------------------------------ highlight

    private final int[] locA = new int[2], locB = new int[2];

    private final ViewTreeObserver.OnPreDrawListener preDraw = () -> {
        View f = getCurrentFocus();
        if (f instanceof TileView) {
            ImageView b = ((TileView) f).banner;
            b.getLocationInWindow(locA);
            root.getLocationInWindow(locB);
            float w = b.getWidth() * b.getScaleX();
            float h = b.getHeight() * b.getScaleY();
            highlight.moveTo(locA[0] - locB[0], locA[1] - locB[1], w, h, true);
        }
        return true;
    };

    // ------------------------------------------------------------------ focus memory

    private void saveFocus() {
        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        p.edit().putInt("zone", zone.ordinal())
                .putInt("idx", zone == Zone.GRID ? gridIdx : zone == Zone.DOCK ? dockIdx : topIdx)
                .apply();
    }

    private void restoreFocus() {
        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        int z = p.getInt("zone", Zone.DOCK.ordinal());
        int idx = p.getInt("idx", 0);
        if (z == Zone.GRID.ordinal() && adapter.getItemCount() > 0) {
            zone = Zone.GRID;
            gridIdx = clamp(idx, 0, adapter.getItemCount() - 1);
            root.post(() -> { showGrid(true, false); focusGrid(gridIdx); });
        } else if (!dockTiles.isEmpty()) {
            zone = Zone.DOCK;
            dockIdx = clamp(idx, 0, dockTiles.size() - 1);
            root.post(() -> focusDock(dockIdx));
        } else {
            zone = Zone.TOP; topIdx = 0;
            root.post(() -> focusTop(0));
        }
    }

    /** After a package change the lists may have shrunk; keep focus on something valid. */
    private void clampFocus() {
        if (zone == Zone.GRID) {
            if (adapter.getItemCount() == 0) { zone = Zone.DOCK; showGrid(false, true); focusDock(0); }
            else focusGrid(gridIdx);
        } else if (zone == Zone.DOCK) {
            if (dockTiles.isEmpty()) { zone = Zone.TOP; focusTop(topIdx); }
            else { dockIdx = clamp(dockIdx, 0, dockTiles.size() - 1); focusDock(dockIdx); }
        } else {
            focusTop(clamp(topIdx, 0, pills.length - 1));
        }
    }

    // ------------------------------------------------------------------ clock & greeting

    private void updateClock() {
        Calendar now = Calendar.getInstance();
        int hour = now.get(Calendar.HOUR_OF_DAY);
        String fmt = getString(R.string.clock_format).trim();
        boolean h24 = "24".equals(fmt) || ("system".equals(fmt) && android.text.format.DateFormat.is24HourFormat(this));
        String pattern = h24 ? "HH:mm" : "h:mm";
        clock.setText(new SimpleDateFormat(pattern, Locale.getDefault()).format(now.getTime()));
        String datePattern = android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEEdMMMM");
        date.setText(new SimpleDateFormat(datePattern, Locale.getDefault()).format(now.getTime()));
        int g = hour < 5 ? R.string.greeting_night : hour < 12 ? R.string.greeting_morning
                : hour < 18 ? R.string.greeting_afternoon : R.string.greeting_evening;
        String name = getString(R.string.greeting_name).trim();
        greeting.setText(name.isEmpty() ? getString(g) : getString(g) + ", " + name);
    }

    private final BroadcastReceiver tickReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { updateClock(); }
    };

    private static IntentFilter tickFilter() {
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_TIME_TICK);
        f.addAction(Intent.ACTION_TIME_CHANGED);
        f.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        return f;
    }

    // ------------------------------------------------------------------ network pill (event driven, no polling)

    private void registerNetwork() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return;
        if (Build.VERSION.SDK_INT >= 31) {
            netCallback = new ConnectivityManager.NetworkCallback(ConnectivityManager.NetworkCallback.FLAG_INCLUDE_LOCATION_INFO) {
                @Override public void onCapabilitiesChanged(Network n, NetworkCapabilities caps) { main.post(() -> showNetwork(caps)); }
                @Override public void onLost(Network n) { main.post(() -> showNetwork(null)); }
            };
        } else {
            netCallback = new ConnectivityManager.NetworkCallback() {
                @Override public void onCapabilitiesChanged(Network n, NetworkCapabilities caps) { main.post(() -> showNetwork(caps)); }
                @Override public void onLost(Network n) { main.post(() -> showNetwork(null)); }
            };
        }
        try {
            cm.registerDefaultNetworkCallback(netCallback);
        } catch (Exception e) {
            netCallback = null;
            showNetwork(null);
        }
    }

    private void unregisterNetwork() {
        if (netCallback == null) return;
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        try { if (cm != null) cm.unregisterNetworkCallback(netCallback); } catch (Exception ignored) {}
        netCallback = null;
    }

    private void showNetwork(NetworkCapabilities caps) {
        if (caps == null) { pillWifi.setText(R.string.pill_offline); return; }
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) { pillWifi.setText(R.string.pill_ethernet); return; }
        if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) { pillWifi.setText(R.string.pill_wifi); return; }
        String ssid = null;
        if (Build.VERSION.SDK_INT >= 29 && caps.getTransportInfo() instanceof WifiInfo) {
            ssid = cleanSsid(((WifiInfo) caps.getTransportInfo()).getSSID());
        }
        if (ssid == null) {
            try {
                WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                if (wm != null) ssid = cleanSsid(wm.getConnectionInfo().getSSID());
            } catch (Exception ignored) {}
        }
        pillWifi.setText(ssid == null ? getString(R.string.pill_wifi) : ssid);
    }

    private static String cleanSsid(String s) {
        if (s == null) return null;
        s = s.trim();
        if (s.startsWith("\"") && s.endsWith("\"") && s.length() >= 2) s = s.substring(1, s.length() - 1);
        if (s.isEmpty() || "<unknown ssid>".equals(s) || "0x".equals(s)) return null;
        return s;
    }

    // ------------------------------------------------------------------ package changes

    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            String pkg = i.getData() == null ? null : i.getData().getSchemeSpecificPart();
            if (pkg != null) loader.evict(pkg); else loader.clear();
            main.removeCallbacks(reload);
            main.postDelayed(reload, 800);
        }
    };

    private final Runnable reload = this::loadApps;

    private static IntentFilter packageFilter() {
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_CHANGED);
        f.addAction(Intent.ACTION_PACKAGE_REPLACED);
        f.addDataScheme("package");
        return f;
    }

    private void register(BroadcastReceiver r, IntentFilter f) {
        // All of these are protected system broadcasts; Android 13+ still wants an export flag.
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(r, f, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(r, f);
    }

    private void unregisterQuietly(BroadcastReceiver r) {
        try { unregisterReceiver(r); } catch (IllegalArgumentException ignored) {}
    }

    // ------------------------------------------------------------------ wallpaper

    private void setupWallpaper() {
        int id = getResources().getIdentifier("wallpaper", "drawable", getPackageName());
        if (id == 0) return;
        wallpaper.setImageResource(id);
        wallpaper.setVisibility(View.VISIBLE);
        int dim = clamp(getResources().getInteger(R.integer.wallpaper_dim_percent), 0, 100);
        wallpaperDim.setAlpha(dim / 100f);
        wallpaperDim.setVisibility(View.VISIBLE);
    }
}
