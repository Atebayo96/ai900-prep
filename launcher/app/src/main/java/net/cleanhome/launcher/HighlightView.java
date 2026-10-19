package net.cleanhome.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import androidx.dynamicanimation.animation.FloatPropertyCompat;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

/**
 * The single translucent "glass" highlight. Four springs (x, y, w, h) chase the focused
 * tile's rectangle, so it visibly travels from tile to tile instead of each tile having
 * its own border. Drawn with one gradient fill and a hairline; no blur.
 */
public class HighlightView extends View {
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private float x, y, w = 10, h = 10, radius;
    private float pad; // highlight sits this far outside the tile
    private final SpringAnimation sx, sy, sw, sh;
    private boolean placed = false;
    private float tx, ty, tw, th; // current spring targets, to avoid re-arming every frame

    public HighlightView(Context c, AttributeSet a) {
        super(c, a);
        float d = c.getResources().getDisplayMetrics().density;
        radius = c.getResources().getDimension(R.dimen.tile_radius) + 5 * d;
        pad = 6 * d;
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(1.5f * d);
        stroke.setColor(0x66FFFFFF);
        sx = spring(new FloatPropertyCompat<HighlightView>("x") {
            @Override public float getValue(HighlightView v) { return v.x; }
            @Override public void setValue(HighlightView v, float val) { v.x = val; v.invalidate(); }
        });
        sy = spring(new FloatPropertyCompat<HighlightView>("y") {
            @Override public float getValue(HighlightView v) { return v.y; }
            @Override public void setValue(HighlightView v, float val) { v.y = val; v.invalidate(); }
        });
        sw = spring(new FloatPropertyCompat<HighlightView>("w") {
            @Override public float getValue(HighlightView v) { return v.w; }
            @Override public void setValue(HighlightView v, float val) { v.w = val; v.rebuildShader(); v.invalidate(); }
        });
        sh = spring(new FloatPropertyCompat<HighlightView>("h") {
            @Override public float getValue(HighlightView v) { return v.h; }
            @Override public void setValue(HighlightView v, float val) { v.h = val; v.rebuildShader(); v.invalidate(); }
        });
    }

    private SpringAnimation spring(FloatPropertyCompat<HighlightView> p) {
        SpringAnimation s = new SpringAnimation(this, p);
        s.setSpring(new SpringForce().setStiffness(420f).setDampingRatio(0.78f));
        return s;
    }

    /** Move to a rectangle in the parent's coordinates (left, top, width, height). */
    public void moveTo(float left, float top, float width, float height, boolean animate) {
        float nx = left - pad, ny = top - pad, nw = width + 2 * pad, nh = height + 2 * pad;
        if (placed && Math.abs(nx - tx) < 0.5f && Math.abs(ny - ty) < 0.5f
                && Math.abs(nw - tw) < 0.5f && Math.abs(nh - th) < 0.5f) {
            return; // same target as last time: nothing to do (keeps the pre-draw hook idle)
        }
        tx = nx; ty = ny; tw = nw; th = nh;
        if (!placed || !animate) {
            sx.cancel(); sy.cancel(); sw.cancel(); sh.cancel();
            x = nx; y = ny; w = nw; h = nh;
            rebuildShader();
            invalidate();
            placed = true;
            return;
        }
        sx.animateToFinalPosition(nx);
        sy.animateToFinalPosition(ny);
        sw.animateToFinalPosition(nw);
        sh.animateToFinalPosition(nh);
    }

    public void setRadius(float px) { radius = px + pad - 1; invalidate(); }

    private void rebuildShader() {
        fill.setShader(new LinearGradient(0, 0, 0, Math.max(1, h),
                new int[]{0x38FFFFFF, 0x14FFFFFF, 0x1EFFFFFF}, new float[]{0f, 0.6f, 1f}, Shader.TileMode.CLAMP));
    }

    @Override protected void onDraw(Canvas c) {
        if (!placed) return;
        c.save();
        c.translate(x, y);
        r.set(0, 0, w, h);
        if (fill.getShader() == null) rebuildShader();
        c.drawRoundRect(r, radius, radius, fill);
        r.inset(stroke.getStrokeWidth() / 2, stroke.getStrokeWidth() / 2);
        c.drawRoundRect(r, radius, radius, stroke);
        c.restore();
    }
}
