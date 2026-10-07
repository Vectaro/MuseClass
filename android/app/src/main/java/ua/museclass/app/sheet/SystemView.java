package ua.museclass.app.sheet;

import android.content.Context;
import android.graphics.Canvas;
import android.view.View;

import ua.museclass.engraving.Sheet;

/** Одна система нот. Висоту бере з верстки, ширину — від батька. */
public final class SystemView extends View {
    private Sheet.System system;
    private float scale = 1;
    private SheetPainter painter;

    public SystemView(Context context) {
        super(context);
    }

    public void bind(Sheet.System system, float scale, SheetPainter painter) {
        this.system = system;
        this.scale = scale;
        this.painter = painter;
        setContentDescription(getResources().getString(ua.museclass.app.R.string.sheet_system,
                system.from + 1, system.to));
        requestLayout();
        invalidate();
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int w = MeasureSpec.getSize(widthSpec);
        int h = system == null ? 0 : (int) Math.ceil(system.height * scale) + getPaddingTop() + getPaddingBottom();
        setMeasuredDimension(w, h);
    }

    @Override
    protected void onDraw(Canvas c) {
        if (system == null || painter == null) return;
        c.save();
        c.translate(getPaddingLeft(), getPaddingTop());
        c.scale(scale, scale);
        painter.draw(c, system.shapes);
        c.restore();
    }
}
