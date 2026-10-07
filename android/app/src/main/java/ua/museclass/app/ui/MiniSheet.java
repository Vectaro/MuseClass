package ua.museclass.app.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.core.content.ContextCompat;

import ua.museclass.app.R;

/**
 * Обкладинка-«папір» (.cover / .mini) з малюнком miniSheet(seed) з прототипу:
 * п'ять ліній і дев'ять нот у viewBox 150×60, розтягнуто без збереження
 * пропорцій (preserveAspectRatio="none"), тож і на картці, і в мініатюрі
 * малюнок той самий. Знизу — тверда тінь 2 px (.cover box-shadow 0 2px 0).
 */
public class MiniSheet extends View {
    private static final int INK = 0xFF1B1B1E;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final Path clip = new Path();
    private final float d;
    private int seed = 2;
    private float radius;
    private boolean shadow;

    public MiniSheet(Context c, AttributeSet a) {
        super(c, a);
        d = getResources().getDisplayMetrics().density;
        radius = 7 * d;
    }

    /** seed — як у прототипі: картка i+2, рядок списку i+5. */
    public void set(int seed, float radiusDp, boolean shadow) {
        this.seed = seed;
        this.radius = radiusDp * d;
        this.shadow = shadow;
        invalidate();
    }

    private double rnd(int n) {
        return ((seed * 9301L + n * 49297L) % 233280) / 233280.0;
    }

    @Override
    protected void onDraw(Canvas c) {
        float sh = shadow ? 2 * d : 0;
        float w = getWidth(), h = getHeight() - sh;
        if (shadow) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(ContextCompat.getColor(getContext(), R.color.cover_shadow));
            r.set(0, sh, w, h + sh);
            c.drawRoundRect(r, radius, radius, paint);
        }
        r.set(0, 0, w, h);
        paint.setColor(ContextCompat.getColor(getContext(), R.color.paper));
        c.drawRoundRect(r, radius, radius, paint);

        c.save();
        clip.reset();
        clip.addRoundRect(r, radius, radius, Path.Direction.CW);
        c.clipPath(clip);
        c.scale(w / 150f, h / 60f);
        paint.setColor(INK);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(.8f);
        paint.setAlpha(Math.round(255 * .8f));
        for (int i = 0; i < 5; i++) c.drawLine(6, 14 + i * 7, 141, 14 + i * 7, paint);
        paint.setAlpha(255);
        float x = 16;
        for (int i = 0; i < 9; i++) {
            float y = (float) (14 + Math.floor(rnd(i) * 5) * 3.5 + 3.5);
            c.save();
            c.rotate(-20, x, y);
            paint.setStyle(Paint.Style.FILL);
            c.drawOval(x - 3, y - 2.3f, x + 3, y + 2.3f, paint);
            c.restore();
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(.9f);
            c.drawLine(x + 2.8f, y, x + 2.8f, y - 13, paint);
            x += 13;
        }
        c.restore();
    }
}
