package ua.museclass.engraving;

import java.util.Locale;

/**
 * Одна фігура нотного стану в координатах engine.js: міжлінійний проміжок 10,
 * y росте вниз. Кольорів тут немає — лише роль: чорнило або приглушене (номери
 * тактів); справжні кольори паперу й чорнила задає той, хто малює.
 */
public abstract class Shape {
    /** Чорнило чи приглушений колір (номери тактів). */
    public enum Tone { INK, MUTED }

    public final Tone tone;

    Shape(Tone tone) {
        this.tone = tone;
    }

    /** Зсув по вертикалі — коли систему кладуть під іншу. */
    abstract Shape moved(double dy);

    abstract void bounds(double[] box);

    /** Короткий запис для звірки з прототипом: тип і ключові числа. */
    public abstract String canon();

    static String f(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }

    static void grow(double[] box, double x, double y) {
        box[0] = Math.min(box[0], x);
        box[1] = Math.min(box[1], y);
        box[2] = Math.max(box[2], x);
        box[3] = Math.max(box[3], y);
    }

    public static final class Line extends Shape {
        public final double x1, y1, x2, y2, width;
        /** 1 або менше: лінії стану в прототипі напівпрозорі (.85). */
        public final float alpha;

        Line(double x1, double y1, double x2, double y2, double width, float alpha) {
            super(Tone.INK);
            this.x1 = x1;
            this.y1 = y1;
            this.x2 = x2;
            this.y2 = y2;
            this.width = width;
            this.alpha = alpha;
        }

        Line(double x1, double y1, double x2, double y2, double width) {
            this(x1, y1, x2, y2, width, 1f);
        }

        @Override
        Shape moved(double dy) {
            return new Line(x1, y1 + dy, x2, y2 + dy, width, alpha);
        }

        @Override
        void bounds(double[] b) {
            grow(b, x1, y1);
            grow(b, x2, y2);
        }

        @Override
        public String canon() {
            return "L " + f(x1) + " " + f(y1) + " " + f(x2) + " " + f(y2) + " " + f(width);
        }
    }

    public static final class Rect extends Shape {
        public final double x, y, w, h;

        Rect(double x, double y, double w, double h) {
            super(Tone.INK);
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }

        @Override
        Shape moved(double dy) {
            return new Rect(x, y + dy, w, h);
        }

        @Override
        void bounds(double[] b) {
            grow(b, x, y);
            grow(b, x + w, y + h);
        }

        @Override
        public String canon() {
            return "R " + f(x) + " " + f(y) + " " + f(w) + " " + f(h);
        }
    }

    /** Головка ноти: еліпс, повернутий на −20°. Порожня — обведена. */
    public static final class Ellipse extends Shape {
        public final double cx, cy, rx, ry, stroke;
        public final boolean filled;
        /** Номер події в партії (як data-e у прототипі); −1 — не подія (другий голос, форшлаг). */
        public final int event;

        Ellipse(double cx, double cy, double rx, double ry, boolean filled, double stroke, int event) {
            super(Tone.INK);
            this.cx = cx;
            this.cy = cy;
            this.rx = rx;
            this.ry = ry;
            this.filled = filled;
            this.stroke = stroke;
            this.event = event;
        }

        @Override
        Shape moved(double dy) {
            return new Ellipse(cx, cy + dy, rx, ry, filled, stroke, event);
        }

        @Override
        void bounds(double[] b) {
            grow(b, cx - rx - 1, cy - rx - 1);
            grow(b, cx + rx + 1, cy + rx + 1);
        }

        @Override
        public String canon() {
            return "E " + f(cx) + " " + f(cy) + " " + f(rx) + " " + f(ry) + " " + (filled ? 1 : 0);
        }
    }

    public static final class Circle extends Shape {
        public final double cx, cy, r;

        Circle(double cx, double cy, double r) {
            super(Tone.INK);
            this.cx = cx;
            this.cy = cy;
            this.r = r;
        }

        @Override
        Shape moved(double dy) {
            return new Circle(cx, cy + dy, r);
        }

        @Override
        void bounds(double[] b) {
            grow(b, cx - r, cy - r);
            grow(b, cx + r, cy + r);
        }

        @Override
        public String canon() {
            return "C " + f(cx) + " " + f(cy) + " " + f(r);
        }
    }

    /**
     * Контур (ключ, пауза, прапорець…) у точці (x, y). flipY — дзеркально по
     * вертикалі відносно y (прапорці штилів униз). stroke 0 — залитий.
     */
    public static final class Glyph extends Shape {
        public final double x, y, stroke;
        public final PathData path;
        public final boolean flipY;

        Glyph(double x, double y, PathData path, boolean flipY, double stroke) {
            super(Tone.INK);
            this.x = x;
            this.y = y;
            this.path = path;
            this.flipY = flipY;
            this.stroke = stroke;
        }

        @Override
        Shape moved(double dy) {
            return new Glyph(x, y + dy, path, flipY, stroke);
        }

        @Override
        void bounds(double[] b) {
            for (PathData.Cmd c : path.commands()) {
                for (int i = 0; i + 1 < c.pts.length; i += 2) {
                    grow(b, x + c.pts[i], y + (flipY ? -c.pts[i + 1] : c.pts[i + 1]));
                }
            }
        }

        @Override
        public String canon() {
            return "P " + f(x + path.firstX) + " " + f(y + (flipY ? -path.firstY : path.firstY));
        }
    }

    /**
     * Знак альтерації або ключовий знак. (x, y) — як у прототипі, де це текст
     * шрифтом 15: лівий край і базова лінія; центр знака — на y − 5.
     */
    public static final class Accidental extends Shape {
        public final double x, y;
        /** −2..2: дубль-бемоль … дубль-дієз; 0 — бекар. */
        public final int alter;

        Accidental(double x, double y, int alter) {
            super(Tone.INK);
            this.x = x;
            this.y = y;
            this.alter = alter;
        }

        @Override
        Shape moved(double dy) {
            return new Accidental(x, y + dy, alter);
        }

        @Override
        void bounds(double[] b) {
            grow(b, x - 1, y - 18);
            grow(b, x + 12, y + 4);
        }

        @Override
        public String canon() {
            return "T " + f(x) + " " + f(y) + " 15 " + Glyphs.accidentalChar(alter);
        }
    }

    /** Текст: цифри розміру (жирні, із засічками) і номери тактів. */
    public static final class Text extends Shape {
        public final double x, y, size;
        public final String text;
        public final boolean bold;
        public final boolean serif;

        Text(double x, double y, String text, double size, boolean bold, boolean serif, Tone tone) {
            super(tone);
            this.x = x;
            this.y = y;
            this.text = text;
            this.size = size;
            this.bold = bold;
            this.serif = serif;
        }

        @Override
        Shape moved(double dy) {
            return new Text(x, y + dy, text, size, bold, serif, tone);
        }

        @Override
        void bounds(double[] b) {
            grow(b, x, y - size);
            grow(b, x + size * 0.6 * text.length(), y + size * 0.25);
        }

        @Override
        public String canon() {
            return "T " + f(x) + " " + f(y) + " " + new java.text.DecimalFormat("0.##",
                    java.text.DecimalFormatSymbols.getInstance(Locale.ROOT)).format(size) + " " + text;
        }
    }
}
