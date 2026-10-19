package net.cleanhome.launcher;

import android.graphics.Bitmap;
import android.graphics.Color;

/**
 * Picks a glow colour from a banner: the average of its more saturated pixels,
 * lifted so it reads as a soft ambient tint. Works on a 32x18 downsample, so it is cheap.
 */
public final class ColorSampler {
    private ColorSampler() {}

    public static int accent(Bitmap src) {
        Bitmap s = Bitmap.createScaledBitmap(src, 32, 18, true);
        long r = 0, g = 0, b = 0; int n = 0;
        long r2 = 0, g2 = 0, b2 = 0; int n2 = 0;
        float[] hsv = new float[3];
        for (int y = 0; y < 18; y++) {
            for (int x = 0; x < 32; x++) {
                int c = s.getPixel(x, y);
                int cr = Color.red(c), cg = Color.green(c), cb = Color.blue(c);
                Color.RGBToHSV(cr, cg, cb, hsv);
                r2 += cr; g2 += cg; b2 += cb; n2++;
                if (hsv[1] > 0.35f && hsv[2] > 0.25f) { r += cr; g += cg; b += cb; n++; }
            }
        }
        if (s != src) s.recycle();
        int out;
        if (n >= 12) out = Color.rgb((int) (r / n), (int) (g / n), (int) (b / n));
        else if (n2 > 0) out = Color.rgb((int) (r2 / n2), (int) (g2 / n2), (int) (b2 / n2));
        else return 0xFF4A4A52;
        // Normalise brightness so dark banners still glow and neon ones do not blind.
        Color.colorToHSV(out, hsv);
        hsv[1] = Math.min(1f, hsv[1] * 1.15f);
        hsv[2] = Math.max(0.55f, Math.min(0.85f, hsv[2]));
        return Color.HSVToColor(hsv);
    }
}
