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

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) { rebuild(); }

    private void rebuild() {
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;
        float cx = w / 2f, cy = h * 0.95f;
        float radius = Math.max(w * 0.36f, h * 0.9f);
        int inner = (color & 0x00FFFFFF) | 0x5A000000;
        int mid = (color & 0x00FFFFFF) | 0x22000000;
        paint.setShader(new RadialGradient(cx, cy, radius,
                new int[]{inner, mid, 0x00000000}, new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
    }

    @Override protected void onDraw(Canvas c) {
        if (paint.getShader() == null) return;
        c.drawRect(0, 0, getWidth(), getHeight(), paint);
    }
}
