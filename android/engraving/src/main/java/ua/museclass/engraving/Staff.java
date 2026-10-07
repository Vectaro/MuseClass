package ua.museclass.engraving;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import ua.museclass.musicxml.Bar;
import ua.museclass.musicxml.Measure;
import ua.museclass.musicxml.Meter;
import ua.museclass.musicxml.Note;
import ua.museclass.musicxml.Pitch;

/**
 * Один нотний стан — порт renderStaff з prototype/engine.js, рядок у рядок, щоб
 * еталони прототипу збігались. Малює все, що прототип кладе в тіло стану:
 * лінії, ключ, ключові знаки, розмір, ноти, паузи, знаки, додаткові лінійки,
 * штилі, прапорці, в'язки, форшлаги, артикуляцію, репризи, зміни розміру й
 * тональності, тактові риски, номери тактів.
 *
 * Поки ні: ліги, тріолі, вольти, написи, динаміка, текст пісні, фермата —
 * у прототипі це окремі шари (deco / over / under).
 *
 * Свідомо інакше за прототип: такти можна розтягнути (stretch) — переносимо
 * системи за шириною екрана й заповнюємо рядок; при stretch = 1 координати
 * ті самі, що в engine.js.
 */
public final class Staff {
    static final double GAP = 5;
    private static final Map<String, Integer> CLEF_TOP = new HashMap<>();
    private static final Map<String, int[]> SHARP_DIA = new HashMap<>();
    private static final Map<String, int[]> FLAT_DIA = new HashMap<>();
    private static final int[] SHARP_ORDER = {3, 0, 4, 1, 5, 2, 6};
    private static final int[] FLAT_ORDER = {6, 2, 5, 1, 4, 0, 3};

    static {
        CLEF_TOP.put("treble", 38);
        CLEF_TOP.put("bass", 26);
        CLEF_TOP.put("alto", 32);
        CLEF_TOP.put("perc", 38);
        SHARP_DIA.put("perc", new int[]{38, 35, 39, 36, 33, 37, 34});
        SHARP_DIA.put("treble", new int[]{38, 35, 39, 36, 33, 37, 34});
        SHARP_DIA.put("bass", new int[]{24, 28, 25, 29, 26, 30, 27});
        SHARP_DIA.put("alto", new int[]{31, 28, 32, 29, 26, 30, 27});
        FLAT_DIA.put("perc", new int[]{34, 37, 33, 36, 32, 35, 31});
        FLAT_DIA.put("treble", new int[]{34, 37, 33, 36, 32, 35, 31});
        FLAT_DIA.put("bass", new int[]{20, 23, 19, 22, 18, 21, 17});
        FLAT_DIA.put("alto", new int[]{27, 30, 26, 29, 25, 28, 24});
    }

    /** Такт для показу (it у прототипі): номер, перша подія, ноти, другий голос, позначки. */
    public static final class Item {
        public final int n;
        public final int ev;
        final List<Note> notes;
        final List<Note> v2;
        final Bar bar;

        Item(int n, int ev, Measure m) {
            this.n = n;
            this.ev = ev;
            this.notes = m.notes();
            this.v2 = m.voice2();
            this.bar = m.bar();
        }

        public int events() {
            return notes.size();
        }
    }

    /** Такти партії підряд, без стискання пауз (toDisp з compress = false). */
    public static List<Item> items(List<Measure> measures) {
        List<Item> out = new ArrayList<>();
        int ev = 0;
        for (int i = 0; i < measures.size(); i++) {
            Item it = new Item(i, ev, measures.get(i));
            out.add(it);
            ev += it.notes.size();
        }
        return out;
    }

    /** Параметри одного стану — як opts у renderStaff. */
    public static final class Options {
        public String clef = "treble";
        public int fifths;
        public Meter meter = new Meter(4, 4);
        public double top;
        /** Розмір на початку (перша система). */
        public boolean timesig;
        /** Подвійна кінцева риска після останнього такту. */
        public boolean end;
        public boolean barNumbers = true;
        /** У скільки разів розтягнути такти; 1 — як у прототипі. */
        public double stretch = 1;
    }

    /** Результат: фігури і ширина стану. */
    public static final class Result {
        public final List<Shape> shapes;
        public final double padL;
        public final double total;

        Result(List<Shape> shapes, double padL, double total) {
            this.shapes = Collections.unmodifiableList(shapes);
            this.padL = padL;
            this.total = total;
        }
    }

    private Staff() {
    }

    // ---- дрібниці з engine.js ----

    static double yOf(int d, String clef) {
        Integer t = CLEF_TOP.get(clef);
        return ((t == null ? 38 : t) - d) * GAP;
    }

    static int dia(Pitch p) {
        return p.octave * 7 + p.step;
    }

    static int[] keyAlters(int fifths) {
        int[] a = new int[7];
        for (int i = 0; i < Math.min(fifths, 7); i++) a[SHARP_ORDER[i]] = 1;
        for (int i = 0; i < Math.min(-fifths, 7); i++) a[FLAT_ORDER[i]] = -1;
        return a;
    }

    static int keyAccidentals(String clef, int fifths) {
        return "perc".equals(clef) ? 0 : Math.min(Math.abs(fifths), 7);
    }

    /** Ширина «голови» стану до першого такту: ключ, ключові знаки, розмір. */
    public static double padLeft(String clef, int fifths, boolean timesig) {
        return 52 + keyAccidentals(clef, fifths) * 9 + (timesig ? 20 : 0);
    }

    private static final double[][] BASES = {{6, 4, 1}, {4, 4, 0}, {3, 2, 1}, {2, 2, 0}, {1.5, 1, 1}, {1, 1, 0},
            {.75, .5, 1}, {.5, .5, 0}, {.375, .25, 1}, {.25, .25, 0}, {.125, .125, 0}};

    /** Записана тривалість → [основа, крапка]; з допуском, як baseOf. */
    static double[] baseOf(double d) {
        for (double[] t : BASES) if (Math.abs(d - t[0]) < 1e-6) return new double[]{t[1], t[2]};
        double[] best = BASES[BASES.length - 1];
        for (double[] t : BASES) if (Math.abs(d - t[0]) < Math.abs(d - best[0])) best = t;
        return new double[]{best[1], best[2]};
    }

    static double notated(Note n) {
        return n.tuplet() != null ? n.duration() * n.tuplet().actual / n.tuplet().normal : n.duration();
    }

    static int flags(double base) {
        if (base == 0.5) return 1;
        if (base == 0.25) return 2;
        if (base == 0.125) return 3;
        return 0;
    }

    /** Крок по горизонталі після ноти (cx += …). */
    private static double advance(Note n) {
        return 26 + 16 * Math.min(n.duration(), 2) + (n.pitches().size() > 1 ? 6 : 0)
                + (n.articulations().isEmpty() ? 0 : 4) + n.graces().size() * 13;
    }

    private static boolean repStart(Bar b) {
        return b != null && ("start".equals(b.repeat()) || "both".equals(b.repeat()));
    }

    private static boolean repEnd(Bar b) {
        return b != null && ("end".equals(b.repeat()) || "both".equals(b.repeat()));
    }

    private static boolean change(Bar b) {
        return b != null && (b.meter() != null || b.fifths() != null);
    }

    /** Природна ширина такту — measureWidth з прототипу. */
    public static double measureWidth(Item it) {
        double w = 20;
        for (Note n : it.notes) w += advance(n);
        if (!it.v2.isEmpty()) {
            double w2 = 20;
            for (Note n : it.v2) w2 += 26 + 16 * Math.min(n.duration(), 2);
            w = Math.max(w, w2);
        }
        if (it.bar != null && it.bar.repeat() != null) w += 14;
        if (change(it.bar)) w += 34;
        boolean lyric = false;
        for (Note n : it.notes) lyric |= n.lyric() != null;
        if (lyric) w = Math.max(w, 20 + it.notes.size() * 30);
        return Math.max(w, 72);
    }

    // ---- верстка ----

    private static final class Head {
        final Pitch pt;
        final int d;
        final double y;
        double x;

        Head(Pitch pt, int d, double y) {
            this.pt = pt;
            this.d = d;
            this.y = y;
        }
    }

    /** Розставлена подія (rec у прототипі). */
    private static final class Rec {
        Item it;
        Note n;
        int ev;
        double x;
        double base;
        boolean dot;
        final List<Head> heads = new ArrayList<>();
        final List<Shape.Accidental> acc = new ArrayList<>();
        boolean rest;
        int voice;
        boolean up;
        double ymin;
        double ymax;
        Double beamY;
    }

    public static Result render(List<Item> items, Options o) {
        return new Staff.Builder(items, o).build();
    }

    private static final class Builder {
        final List<Item> items;
        final Options o;
        final String clef;
        int fifths;
        final double top;
        final double padL;
        final double[] widths;
        final double total;
        final List<Shape> s = new ArrayList<>();
        double kx;

        Builder(List<Item> items, Options o) {
            this.items = items;
            this.o = o;
            clef = o.clef;
            fifths = o.fifths;
            top = o.top;
            padL = padLeft(clef, fifths, o.timesig);
            widths = new double[items.size()];
            double sum = 0;
            for (int i = 0; i < items.size(); i++) {
                widths[i] = measureWidth(items.get(i)) * o.stretch;
                sum += widths[i];
            }
            total = padL + sum + 16;
        }

        double y(Pitch p) {
            return top + yOf(dia(p), clef);
        }

        void keySignature() {
            int[] tbl = fifths > 0 ? SHARP_DIA.get(clef) : FLAT_DIA.get(clef);
            if (tbl == null) tbl = fifths > 0 ? SHARP_DIA.get("treble") : FLAT_DIA.get("treble");
            int n = keyAccidentals(clef, fifths);
            for (int i = 0; i < n; i++) {
                s.add(new Shape.Accidental(kx, top + yOf(tbl[i], clef) + 5, fifths > 0 ? 1 : -1));
                kx += 9;
            }
        }

        void clef(double x) {
            switch (clef) {
                case "bass": {
                    double yF = top + 10;
                    s.add(new Shape.Glyph(x, yF, Glyphs.BASS, false, 0));
                    s.add(new Shape.Circle(x + 16, yF - 5, 1.9));
                    s.add(new Shape.Circle(x + 16, yF + 5, 1.9));
                    break;
                }
                case "perc":
                    s.add(new Shape.Rect(x + 2, top + 8, 4.4, 24));
                    s.add(new Shape.Rect(x + 10, top + 8, 4.4, 24));
                    break;
                case "alto":
                    s.add(new Shape.Rect(x, top, 2.6, 40));
                    s.add(new Shape.Rect(x + 4, top, 1.4, 40));
                    s.add(new Shape.Glyph(x, top, Glyphs.ALTO_UP, false, 0));
                    s.add(new Shape.Glyph(x, top, Glyphs.ALTO_DOWN, false, 0));
                    break;
                default: {
                    double gx = x + 4, gy = top + 30;
                    s.add(new Shape.Glyph(gx, gy, Glyphs.TREBLE, false, 0));
                    double[] e = Glyphs.TREBLE_EYE;
                    s.add(new Shape.Circle(gx + e[0], gy + e[1], e[2]));
                }
            }
        }

        void restGlyph(double base, double cx) {
            if (base >= 4) {
                s.add(new Shape.Rect(cx - 6, top + 10, 12, 5));
            } else if (base >= 2) {
                s.add(new Shape.Rect(cx - 6, top + 15, 12, 5));
            } else if (base >= 1) {
                s.add(new Shape.Glyph(cx, top, Glyphs.REST_QUARTER, false, 0));
            } else {
                int n = base <= .25 ? 2 : 1;
                s.add(new Shape.Glyph(cx, top, n > 1 ? Glyphs.REST_STEM_2 : Glyphs.REST_STEM_1, false, 1.6));
                for (int i = 0; i < n; i++) {
                    double y = top + 11 + i * 8;
                    s.add(new Shape.Circle(cx + 3.2, y, 1.9));
                    s.add(new Shape.Glyph(cx, y, Glyphs.REST_HOOK, false, 1.3));
                }
            }
        }

        void artGlyph(String kind, double x, double y, boolean up) {
            switch (kind) {
                case "staccato":
                    s.add(new Shape.Circle(x, y, 1.7));
                    break;
                case "tenuto":
                    s.add(new Shape.Rect(x - 4.5, y - .9, 9, 1.8));
                    break;
                case "accent":
                    s.add(new Shape.Glyph(x, y, Glyphs.ACCENT, false, 1.6));
                    break;
                case "marcato":
                    s.add(new Shape.Glyph(x, y, up ? Glyphs.MARCATO_UP : Glyphs.MARCATO_DOWN, false, 1.7));
                    break;
                default:
                    // фермата — шар над станом, поки не малюємо
            }
        }

        Rec place(Item it, Note n, int ev, double cx, int voice, Map<String, Integer> acc) {
            double[] bd = baseOf(notated(n));
            Rec r = new Rec();
            r.it = it;
            r.n = n;
            r.ev = ev;
            r.x = cx;
            r.base = bd[0];
            r.dot = bd[1] != 0;
            r.rest = n.isRest();
            r.voice = voice;
            if (!r.rest) {
                for (Pitch p : n.pitches()) r.heads.add(new Head(p, dia(p), y(p)));
                // зверху вниз; сортування стабільне, як у JS
                Collections.sort(r.heads, (a, b) -> Integer.compare(b.d, a.d));
                double avg = 0;
                for (Head h : r.heads) avg += h.y;
                avg /= r.heads.size();
                r.up = voice == 2 ? false : (voice == 1 || avg > top + 20);
                r.ymin = Double.MAX_VALUE;
                r.ymax = -Double.MAX_VALUE;
                for (Head h : r.heads) {
                    r.ymin = Math.min(r.ymin, h.y);
                    r.ymax = Math.max(r.ymax, h.y);
                }
                double ax = cx - 16;
                int[] key = keyAlters(fifths);
                for (Head h : r.heads) {
                    if (h.pt.unpitched) continue;
                    String k = h.pt.step + ":" + h.pt.octave;
                    Integer prev = acc.get(k);
                    int cur = prev != null ? prev : key[h.pt.step];
                    if (h.pt.alter != cur) {
                        if (Math.abs(h.pt.alter) <= 2) r.acc.add(new Shape.Accidental(ax, h.y + 5, h.pt.alter));
                        acc.put(k, h.pt.alter);
                        ax -= 10;
                    }
                }
            } else {
                r.up = voice != 2;
                r.ymin = top + 14;
                r.ymax = top + 24;
            }
            return r;
        }

        Result build() {
            for (int i = 0; i < 5; i++) {
                s.add(new Shape.Line(padL - 8, top + i * 10, total - 6, top + i * 10, 1, .85f));
            }
            clef(22);
            kx = 50;
            keySignature();
            if (o.timesig) {
                s.add(new Shape.Text(kx + 5, top + 19, String.valueOf(o.meter.beats), 19, true, true, Shape.Tone.INK));
                s.add(new Shape.Text(kx + 5, top + 38, String.valueOf(o.meter.beatType), 19, true, true, Shape.Tone.INK));
            }
            final int startFifths = fifths;
            final double k = o.stretch;

            // ---- прохід 1: позиції ----
            List<Rec> laid = new ArrayList<>();
            double x = padL;
            for (int mi = 0; mi < items.size(); mi++) {
                Item it = items.get(mi);
                double w = widths[mi], x0 = x;
                Map<String, Integer> acc = new HashMap<>();
                // прототип тут лишає початкову тональність — знаки після зміни ключа
                // рахуються від старої; у нас — від тієї, що діє в такті
                if (it.bar != null && it.bar.fifths() != null) fifths = it.bar.fifths();
                double off = repStart(it.bar) ? 26 : 16;
                if (change(it.bar)) off += 30;
                int evi = it.ev;
                for (Note n : it.notes) {
                    double cx = x0 + off * k;
                    List<Note> gr = n.graces();
                    for (int gi = 0; gi < gr.size(); gi++) {
                        Note g = gr.get(gi);
                        if (g.pitches().isEmpty()) continue;
                        double gy = y(g.pitches().get(0));
                        double gx = cx - 10 - (gr.size() - gi - 1) * 11;
                        s.add(new Shape.Ellipse(gx, gy, 4.1, 3.1, true, 0, -1));
                        s.add(new Shape.Line(gx + 3.9, gy, gx + 3.9, gy - 22, 1.1));
                        s.add(new Shape.Glyph(gx, gy, Glyphs.GRACE_FLAG, false, 0));
                        s.add(new Shape.Line(gx - 3, gy - 9, gx + 8, gy - 15, 1.1));
                    }
                    laid.add(place(it, n, evi, cx, 0, acc));
                    off += advance(n);
                    evi++;
                }
                if (!it.v2.isEmpty()) { // другий голос: штилі вниз
                    double off2 = 16;
                    for (Note n : it.v2) {
                        laid.add(place(it, n, -1, x0 + off2 * k, 2, acc));
                        off2 += 26 + 16 * Math.min(n.duration(), 2);
                    }
                }
                x += w;
            }

            // ---- в'язки ----
            List<List<Rec>> beams = new ArrayList<>();
            List<Rec> run = new ArrayList<>();
            for (int i = 0; i < laid.size(); i++) {
                Rec r = laid.get(i);
                Rec prev = i > 0 ? laid.get(i - 1) : null;
                if (prev != null && (prev.it != r.it || prev.voice != r.voice)) flush(run, beams);
                if (r.rest || r.base >= 1) {
                    flush(run, beams);
                    continue;
                }
                String pos = r.n.tuplet() != null ? r.n.tuplet().pos : null;
                if ("start".equals(pos)) flush(run, beams);
                if (prev != null && (prev.n.tuplet() != null) != (r.n.tuplet() != null)) flush(run, beams);
                run.add(r);
                if (r.n.tuplet() != null) {
                    if ("stop".equals(pos)) flush(run, beams);
                    continue;
                }
                if (run.size() >= 4) flush(run, beams);
            }
            flush(run, beams);
            Set<Rec> beamed = new HashSet<>();
            for (List<Rec> g : beams) {
                int ups = 0;
                for (Rec r : g) if (r.up) ups++;
                Rec g0 = g.get(0);
                boolean up = g0.voice == 2 ? false : (g0.voice == 1 || ups * 2 >= g.size());
                double by = up ? Double.MAX_VALUE : -Double.MAX_VALUE;
                for (Rec r : g) by = up ? Math.min(by, r.ymin) : Math.max(by, r.ymax);
                by = up ? by - 30 : by + 30;
                for (Rec r : g) {
                    r.up = up;
                    r.beamY = by;
                    beamed.add(r);
                }
            }

            // ---- прохід 2: ноти ----
            for (Rec r : laid) {
                double cx = r.x;
                if (r.rest) {
                    restGlyph(r.base, cx);
                    if (r.dot) s.add(new Shape.Circle(cx + 9, top + 13, 1.7));
                    continue;
                }
                s.addAll(r.acc);
                for (double ly = top - 10; ly >= r.ymin - 2; ly -= 10) s.add(new Shape.Line(cx - 10, ly, cx + 10, ly, 1));
                for (double ly = top + 50; ly <= r.ymax + 2; ly += 10) s.add(new Shape.Line(cx - 10, ly, cx + 10, ly, 1));
                boolean filled = r.base <= 1;
                Integer prevD = null;
                boolean shifted = false;
                for (Head h : r.heads) {
                    shifted = prevD != null && Math.abs(prevD - h.d) == 1 ? !shifted : false;
                    prevD = h.d;
                    double ox = shifted ? (r.up ? 11.8 : -11.8) : 0, hx = cx + ox;
                    h.x = hx;
                    if (h.pt.unpitched) {
                        s.add(new Shape.Glyph(hx, h.y, Glyphs.CROSS_HEAD, false, filled ? 0 : 1.6));
                    } else {
                        s.add(new Shape.Ellipse(hx, h.y, 6.3, 4.7, filled, filled ? 0 : 1.9, r.ev));
                    }
                    if (r.dot) s.add(new Shape.Circle(hx + 11, h.y - 2, 1.7));
                }
                if (r.base < 4) {
                    double sx = r.up ? cx + 5.9 : cx - 5.9, sy = r.up ? r.ymin - 33 : r.ymax + 33;
                    if (!beamed.contains(r)) {
                        s.add(new Shape.Line(sx, r.up ? r.ymax : r.ymin, sx, sy, 1.5));
                        int nf = flags(r.base);
                        for (int f = 0; f < nf; f++) {
                            double fy = sy + (r.up ? 1 : -1) * f * 6;
                            s.add(new Shape.Glyph(sx, fy, Glyphs.FLAG, !r.up, 0));
                        }
                    }
                }
                if (!r.n.articulations().isEmpty()) {
                    boolean below = r.up;
                    double ay = below ? r.ymax + 11 : r.ymin - 11;
                    for (String a : r.n.articulations()) {
                        if ("fermata".equals(a)) continue;
                        artGlyph(a, cx, ay, !below);
                        ay += below ? 7 : -7;
                    }
                }
            }

            // ---- в'язки: штилі й балки ----
            for (List<Rec> g : beams) {
                boolean up = g.get(0).up;
                double by = g.get(0).beamY;
                for (Rec r : g) {
                    double sx = up ? r.x + 5.9 : r.x - 5.9;
                    s.add(new Shape.Line(sx, up ? r.ymax : r.ymin, sx, by, 1.5));
                }
                double x1 = up ? g.get(0).x + 5.9 : g.get(0).x - 5.9;
                double x2 = up ? g.get(g.size() - 1).x + 5.9 : g.get(g.size() - 1).x - 5.9;
                int levels = 1;
                for (Rec r : g) levels = Math.max(levels, Math.max(1, flags(r.base)));
                for (int lv = 0; lv < levels; lv++) {
                    double yy = by + (up ? 1 : -1) * lv * 5.5;
                    if (lv == 0) {
                        s.add(new Shape.Rect(x1, yy - (up ? 0 : 4), x2 - x1, 4));
                        continue;
                    }
                    List<Rec> seg = new ArrayList<>();
                    for (int i = 0; i < g.size(); i++) {
                        Rec r = g.get(i);
                        boolean need = Math.max(1, flags(r.base)) > lv;
                        if (need) seg.add(r);
                        if ((!need || i == g.size() - 1) && !seg.isEmpty()) {
                            double a = up ? seg.get(0).x + 5.9 : seg.get(0).x - 5.9;
                            double b = up ? seg.get(seg.size() - 1).x + 5.9 : seg.get(seg.size() - 1).x - 5.9;
                            s.add(new Shape.Rect(a, yy - (up ? 0 : 4), Math.max(b - a, 7), 4));
                            seg.clear();
                        }
                    }
                }
            }

            // ---- такти, репризи, зміни ----
            fifths = startFifths;
            x = padL;
            for (int mi = 0; mi < items.size(); mi++) {
                Item it = items.get(mi);
                double w = widths[mi], x0 = x;
                Bar b = it.bar;
                if (repStart(b)) {
                    s.add(new Shape.Rect(x0 + 1, top, 3.4, 40));
                    s.add(new Shape.Rect(x0 + 6.5, top, 1.2, 40));
                    s.add(new Shape.Circle(x0 + 12, top + 15, 1.9));
                    s.add(new Shape.Circle(x0 + 12, top + 25, 1.9));
                }
                if (b != null && b.fifths() != null && b.fifths() != fifths) {
                    fifths = b.fifths();
                    kx = x0 + 4;
                    keySignature();
                }
                if (b != null && b.meter() != null) {
                    s.add(new Shape.Text(x0 + 4, top + 19, String.valueOf(b.meter().beats), 17, true, true, Shape.Tone.INK));
                    s.add(new Shape.Text(x0 + 4, top + 37, String.valueOf(b.meter().beatType), 17, true, true, Shape.Tone.INK));
                }
                x += w;
                boolean last = mi == items.size() - 1;
                if (repEnd(b)) {
                    s.add(new Shape.Circle(x - 12, top + 15, 1.9));
                    s.add(new Shape.Circle(x - 12, top + 25, 1.9));
                    s.add(new Shape.Rect(x - 7.5, top, 1.2, 40));
                    s.add(new Shape.Rect(x - 4.4, top, 3.4, 40));
                } else {
                    s.add(new Shape.Line(x, top, x, top + 40, last && o.end ? 3 : 1.1));
                    if (last && o.end) s.add(new Shape.Line(x - 5, top, x - 5, top + 40, 1.1));
                }
                boolean fermata = false;
                for (Note n : it.notes) fermata |= n.articulations().contains("fermata");
                if (o.barNumbers && !(b != null && !b.ending().isEmpty()) && !fermata) {
                    s.add(new Shape.Text(x0 + 2, top - 8, String.valueOf(it.n + 1), 9.5, false, false, Shape.Tone.MUTED));
                }
            }
            return new Result(s, padL, total);
        }

        private static void flush(List<Rec> run, List<List<Rec>> beams) {
            if (run.size() > 1) beams.add(new ArrayList<>(run));
            run.clear();
        }
    }
}
