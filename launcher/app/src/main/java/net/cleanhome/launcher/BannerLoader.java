package net.cleanhome.launcher;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Loads app banners on one background thread, downscaled to tile size and kept in a small
 * RGB_565 cache (about 170 KB each), plus an accent colour sampled from the banner.
 * Nothing is fetched from the network.
 */
public final class BannerLoader {

    public interface Callback {
        void onLoaded(AppEntry app, Bitmap bitmap, int accent);
    }

    private static final int W = 400, H = 225;
    private final Context ctx;
    private final PackageManager pm;
    private final ExecutorService exec = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final LruCache<String, Bitmap> bitmaps = new LruCache<>(64);
    private final LruCache<String, Integer> accents = new LruCache<>(128);

    public BannerLoader(Context c) {
        ctx = c.getApplicationContext();
        pm = ctx.getPackageManager();
    }

    public Bitmap cached(AppEntry app) { return bitmaps.get(app.packageName); }

    public int cachedAccent(AppEntry app) {
        Integer a = accents.get(app.packageName);
        return a == null ? 0xFF4A4A52 : a;
    }

    public void load(final AppEntry app, final Callback cb) {
        Bitmap hit = bitmaps.get(app.packageName);
        if (hit != null) {
            cb.onLoaded(app, hit, cachedAccent(app));
            return;
        }
        exec.execute(() -> {
            final Bitmap bmp = render(app);
            final int accent = bmp == null ? 0xFF4A4A52 : ColorSampler.accent(bmp);
            if (bmp != null) {
                bitmaps.put(app.packageName, bmp);
                accents.put(app.packageName, accent);
            }
            main.post(() -> cb.onLoaded(app, bmp, accent));
        });
    }

    public void clear() {
        bitmaps.evictAll();
        accents.evictAll();
    }

    public void evict(String packageName) {
        bitmaps.remove(packageName);
        accents.remove(packageName);
    }

    private Bitmap render(AppEntry app) {
        Drawable d = null;
        try {
            ActivityInfo ai = pm.getActivityInfo(app.component, 0);
            d = ai.loadBanner(pm);
            if (d == null) d = pm.getApplicationBanner(ai.applicationInfo);
            if (d == null) {
                Drawable icon = ai.loadIcon(pm);
                return iconTile(icon, app.label);
            }
        } catch (Exception e) {
            return null;
        }
        try {
            Bitmap b = Bitmap.createBitmap(W, H, Bitmap.Config.RGB_565);
            Canvas c = new Canvas(b);
            c.drawColor(0xFF1C1C1E);
            d.setBounds(0, 0, W, H);
            d.draw(c);
            return b;
        } catch (Exception e) {
            return null;
        }
    }

    /** Apps with no TV banner get a dark tile with their icon centred. */
    private Bitmap iconTile(Drawable icon, String label) {
        Bitmap b = Bitmap.createBitmap(W, H, Bitmap.Config.RGB_565);
        Canvas c = new Canvas(b);
        c.drawColor(0xFF1C1C1E);
        if (icon != null) {
            int s = (int) (H * 0.5f);
            int l = (W - s) / 2, t = (H - s) / 2;
            icon.setBounds(l, t, l + s, t + s);
            icon.draw(c);
        } else {
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setColor(Color.WHITE);
            p.setTextSize(36);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTypeface(Fonts.medium());
            c.drawText(label, W / 2f, H / 2f + 12, p);
        }
        return b;
    }
}
