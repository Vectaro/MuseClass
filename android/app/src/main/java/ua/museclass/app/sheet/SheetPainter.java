package ua.museclass.app.sheet;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import ua.museclass.engraving.Glyphs;
import ua.museclass.engraving.PathData;
import ua.museclass.engraving.Shape;

/**
 * Малює фігури з :engraving на Canvas у їхніх одиницях (масштаб задає той, хто
 * кличе). Контури гліфів переводяться в {@link Path} один раз і далі лише
 * зсуваються матрицею.
 */
public final class SheetPainter {
    private final SheetColors colors;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Typeface serifBold = Typeface.create(Typeface.SERIF, Typeface.BOLD);
    private final Typeface sans = Typeface.SANS_SERIF;
    private final Map<PathData, Path> paths = new IdentityHashMap<>();
    private final RectF oval = new RectF();

    public SheetPainter(SheetColors colors) {
        this.colors = colors;
        fill.setStyle(Paint.Style.FILL);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeJoin(Paint.Join.ROUND);
    }

    public SheetColors colors() {
        return colors;
    }

    public void draw(Canvas c, List<Shape> shapes) {
        for (Shape s : shapes) draw(c, s);
    }

    private void draw(Canvas c, Shape s) {
        int color = s.tone == Shape.Tone.MUTED ? colors.muted : colors.ink;
        fill.setColor(color);
        stroke.setColor(color);
        if (s instanceof Shape.Line) {
            Shape.Line l = (Shape.Line) s;
            stroke.setStrokeWidth((float) l.width);
            stroke.setAlpha(Math.round(255 * l.alpha * (Color.alpha(color) / 255f)));
            c.drawLine((float) l.x1, (float) l.y1, (float) l.x2, (float) l.y2, stroke);
            stroke.setAlpha(Color.alpha(color));
        } else if (s instanceof Shape.Rect) {
            Shape.Rect r = (Shape.Rect) s;
            c.drawRect((float) r.x, (float) r.y, (float) (r.x + r.w), (float) (r.y + r.h), fill);
        } else if (s instanceof Shape.Ellipse) {
            Shape.Ellipse e = (Shape.Ellipse) s;
            oval.set((float) (e.cx - e.rx), (float) (e.cy - e.ry), (float) (e.cx + e.rx), (float) (e.cy + e.ry));
            c.save();
            c.rotate(-20, (float) e.cx, (float) e.cy);
            if (e.filled) {
                c.drawOval(oval, fill);
            } else {
                stroke.setStrokeWidth((float) e.stroke);
                c.drawOval(oval, stroke);
            }
            c.restore();
        } else if (s instanceof Shape.Circle) {
            Shape.Circle ci = (Shape.Circle) s;
            c.drawCircle((float) ci.cx, (float) ci.cy, (float) ci.r, fill);
        } else if (s instanceof Shape.Glyph) {
            Shape.Glyph g = (Shape.Glyph) s;
            c.save();
            c.translate((float) g.x, (float) g.y);
            if (g.flipY) c.scale(1, -1);
            if (g.stroke > 0) {
                stroke.setStrokeWidth((float) g.stroke);
                c.drawPath(path(g.path), stroke);
            } else {
                c.drawPath(path(g.path), fill);
            }
            c.restore();
        } else if (s instanceof Shape.Accidental) {
            Shape.Accidental a = (Shape.Accidental) s;
            c.save();
            c.translate((float) a.x, (float) a.y);
            c.drawPath(path(Glyphs.accidental(a.alter)), fill);
            c.restore();
        } else if (s instanceof Shape.Text) {
            Shape.Text t = (Shape.Text) s;
            text.setColor(color);
            text.setTextSize((float) t.size);
            text.setTypeface(t.bold && t.serif ? serifBold : t.serif ? Typeface.SERIF : sans);
            c.drawText(t.text, (float) t.x, (float) t.y, text);
        }
    }

    private Path path(PathData d) {
        Path p = paths.get(d);
        if (p != null) return p;
        p = new Path();
        if (d.evenOdd) p.setFillType(Path.FillType.EVEN_ODD);
        for (PathData.Cmd cmd : d.commands()) {
            double[] v = cmd.pts;
            switch (cmd.op) {
                case 'M':
                    p.moveTo((float) v[0], (float) v[1]);
                    break;
                case 'L':
                    p.lineTo((float) v[0], (float) v[1]);
                    break;
                case 'C':
                    p.cubicTo((float) v[0], (float) v[1], (float) v[2], (float) v[3], (float) v[4], (float) v[5]);
                    break;
                case 'Q':
                    p.quadTo((float) v[0], (float) v[1], (float) v[2], (float) v[3]);
                    break;
                default:
                    p.close();
            }
        }
        paths.put(d, p);
        return p;
    }
}
