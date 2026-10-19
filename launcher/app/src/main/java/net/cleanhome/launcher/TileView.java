package net.cleanhome.launcher;

import android.content.Context;
import android.graphics.Outline;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Banner + label. Scales up a little when focused and fades the label in. */
public class TileView extends LinearLayout {
    private static final float FOCUS_SCALE = 1.08f;
    public ImageView banner;
    public TextView label;
    public AppEntry app;

    public TileView(Context c, AttributeSet a) { super(c, a); }

    @Override protected void onFinishInflate() {
        super.onFinishInflate();
        banner = findViewById(R.id.banner);
        label = findViewById(R.id.label);
        label.setTypeface(Fonts.regular());
        final float radius = getResources().getDimension(R.dimen.tile_radius);
        banner.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), radius);
            }
        });
        banner.setClipToOutline(true);
        setOnFocusChangeListener((v, has) -> applyFocus(has, true));
        applyFocus(false, false);
    }

    public void setTileSize(int w, int h) {
        banner.getLayoutParams().width = w;
        banner.getLayoutParams().height = h;
        label.getLayoutParams().width = w;
        requestLayout();
    }

    /** Used by adapters that install their own focus listener. */
    public void onFocusProxy(boolean focused) { applyFocus(focused, true); }

    private void applyFocus(boolean focused, boolean animate) {
        float s = focused ? FOCUS_SCALE : 1f;
        float a = focused ? 1f : 0f;
        if (animate) {
            banner.animate().scaleX(s).scaleY(s).setDuration(180).start();
            label.animate().alpha(a).setDuration(180).start();
        } else {
            banner.setScaleX(s); banner.setScaleY(s);
            label.setAlpha(a);
        }
    }
}
