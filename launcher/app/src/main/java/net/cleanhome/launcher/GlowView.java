package net.cleanhome.launcher;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

/** Ambient radial glow behind the dock app name. One gradient, no blur. */
public class GlowView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int color = 0xFF4A4A52;
    private ValueAnimator anim;
    private float cx = -1, cy = -1;

    public GlowView(Context c, AttributeSet a) { super(c, a); setAlpha(0f); }

    public void setColor(int target, boolean animate) {
        if (anim != null) anim.cancel();
        if (!animate) { color = target; rebuild(); invalidate(); return; }
        anim = ValueAnimator.ofObject(new ArgbEvaluator(), color, target);
        anim.setDuration(320);
        anim.addUpdateListener(a -> { color = (int) a.getAnimatedValue(); rebuild(); invalidate(); });
        anim.start();
    }

    public void show(boolean on) {
        animate().alpha(on ? 1f : 0.35f).setDuration(260).start();
    }

    /** Centre of the glow in this view's coordinates (the dock name's centre). */
    public void setCenter(float x, float y) {
        if (Math.abs(x - cx) < 1 && Math.abs(y - cy) < 1) return;
        cx = x; cy = y;
        rebuild();
        invalidate();
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) { rebuild(); }

    private void rebuild() {
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;
        float x = cx < 0 ? w / 2f : cx;
        float y = cy < 0 ? h * 0.55f : cy;
        float radius = w * 0.30f;   // fades to nothing well inside the screen: no hard edges
        int inner = (color & 0x00FFFFFF) | 0x6E000000;
        int mid = (color & 0x00FFFFFF) | 0x24000000;
        paint.setShader(new RadialGradient(x, y, radius,
                new int[]{inner, mid, 0x00000000}, new float[]{0f, 0.4f, 1f}, Shader.TileMode.CLAMP));
    }

    @Override protected void onDraw(Canvas c) {
        if (paint.getShader() == null) return;
        c.drawRect(0, 0, getWidth(), getHeight(), paint);
    }
}
