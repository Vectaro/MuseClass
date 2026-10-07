package ua.museclass.app.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.core.content.ContextCompat;

import ua.museclass.app.R;

/**
 * «Штрихова лінійка» між секціями (.staffrule): смуга 9 px, лінії по 1 px через
 * кожні 3 px кольору --line з прозорістю .55 — схоже на нотний стан.
 */
public class StaffRule extends View {
    private final Paint paint = new Paint();
    private final float px;

    public StaffRule(Context c, AttributeSet a) {
        super(c, a);
        px = getResources().getDisplayMetrics().density;
        paint.setColor(ContextCompat.getColor(c, R.color.line));
        paint.setAlpha(Math.round(255 * .55f));
    }

    @Override
    protected void onMeasure(int w, int h) {
        setMeasuredDimension(MeasureSpec.getSize(w), Math.round(9 * px));
    }

    @Override
    protected void onDraw(Canvas c) {
        for (float y = 0; y < 9 * px - .5f; y += 3 * px) c.drawRect(0, y, getWidth(), y + px, paint);
    }
}
