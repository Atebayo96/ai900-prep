package net.cleanhome.launcher;

import android.content.Context;
import android.graphics.Typeface;

/**
 * Outfit (SIL Open Font License) variable font, built once per weight.
 * Typeface.Builder with font variation settings is available from API 26.
 */
public final class Fonts {
    private static Typeface thin, light, regular, medium;

    private Fonts() {}

    public static void init(Context c) {
        if (regular != null) return;
        thin = build(c, 200);
        light = build(c, 300);
        regular = build(c, 400);
        medium = build(c, 500);
    }

    private static Typeface build(Context c, int weight) {
        try {
            return new Typeface.Builder(c.getAssets(), "fonts/Outfit-Variable.ttf")
                    .setFontVariationSettings("'wght' " + weight)
                    .build();
        } catch (Exception e) {
            return Typeface.create(Typeface.SANS_SERIF, weight >= 500 ? Typeface.BOLD : Typeface.NORMAL);
        }
    }

    public static Typeface thin() { return thin; }
    public static Typeface light() { return light; }
    public static Typeface regular() { return regular; }
    public static Typeface medium() { return medium; }
}
