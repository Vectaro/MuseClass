package ua.museclass.app.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.core.content.ContextCompat;

import ua.museclass.app.R;

/**
 * Перемикач .sw: доріжка 40×23 (--line / бурштинова), кружечок 17 (--muted /
 * --on-accent). Стан — isSelected(), натискання перемикає.
 */
public class PillSwitch extends View {
    public interface Listener {
        void onChanged(PillSwitch s, boolean on);
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final float d;
    private Listener listener;

    public PillSwitch(Context c, AttributeSet a) {
        super(c, a);
        d = getResources().getDisplayMetrics().density;
        setClickable(true);
        setFocusable(true);
        setOnClickListener(v -> {
            setSelected(!isSelected());
            if (listener != null) listener.onChanged(this, isSelected());
        });
    }

    public void setListener(Listener l) {
        listener = l;
    }

    @Override
    public void setSelected(boolean selected) {
        super.setSelected(selected);
        invalidate();
    }

    @Override
    public CharSequence getAccessibilityClassName() {
        return android.widget.Switch.class.getName();
    }

    @Override
    protected void onMeasure(int w, int h) {
        setMeasuredDimension(Math.round(40 * d), Math.round(23 * d));
    }

    @Override
    protected void onDraw(Canvas c) {
        boolean on = isSelected();
        paint.setColor(ContextCompat.getColor(getContext(), on ? R.color.amber : R.color.line));
        r.set(0, 0, getWidth(), getHeight());
        c.drawRoundRect(r, getHeight() / 2f, getHeight() / 2f, paint);
        paint.setColor(ContextCompat.getColor(getContext(), on ? R.color.on_accent : R.color.muted));
        float cx = (on ? 20 : 3) * d + 8.5f * d;
        c.drawCircle(cx, getHeight() / 2f, 8.5f * d, paint);
    }
}
