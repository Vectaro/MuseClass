package ua.museclass.engraving;

/**
 * Контури з prototype/engine.js, переведені у відносні координати: кожен
 * гліф малюється від своєї опорної точки (у коментарі — яка це точка). Знаків
 * альтерації в прототипі як контурів немає (там текст ♯♭♮), тож вони
 * намальовані тут — на Android 𝄪 і 𝄫 у системних шрифтах не гарантовані.
 */
public final class Glyphs {
    private Glyphs() {
    }

    /** Скрипковий ключ; опора — (x + 4, top + 30), тобто лінія соль. Плюс «очко» CLEF_EYE. */
    public static final PathData TREBLE = PathData.parse(
            "M-7.6 22.6 L-7.8 24.0 L-7.6 25.4 L-7.0 26.5 L-6.1 27.4 L-4.9 28.1 L-3.6 28.4 L-2.2 28.4"
            + " L-0.7 28.0 L0.7 27.4 L2.0 26.3 L3.2 25.0 L4.2 23.3 L4.9 21.2 L5.1 17.7 L5.2 14.1 L5.2 10.5"
            + " L5.2 6.8 L5.1 3.1 L5.0 -0.6 L4.8 -4.2 L4.6 -7.8 L4.4 -11.2 L4.2 -14.5 L3.9 -17.6 L3.6 -20.5"
            + " L3.4 -23.1 L3.1 -25.5 L3.1 -27.8 L3.3 -30.0 L3.7 -31.9 L4.3 -33.7 L5.1 -35.2 L5.9 -36.4"
            + " L6.8 -37.2 L7.6 -37.7 L8.4 -37.9 L9.2 -37.7 L10.1 -37.1 L10.9 -36.0 L11.6 -34.4 L11.8 -32.7"
            + " L11.7 -31.1 L11.3 -29.3 L10.5 -27.5 L9.5 -25.8 L8.3 -24.0 L6.8 -22.2 L5.2 -20.4 L3.5 -18.7"
            + " L1.7 -17.1 L-0.2 -15.5 L-2.1 -14.0 L-4.3 -12.4 L-6.3 -10.8 L-8.1 -9.3 L-9.7 -7.7 L-11.0 -6.1"
            + " L-12.0 -4.6 L-12.8 -3.0 L-13.3 -1.4 L-13.6 0.3 L-13.6 1.9 L-13.3 3.5 L-12.8 5.0 L-12.0 6.4"
            + " L-10.9 8.3 L-9.4 9.9 L-7.6 11.2 L-5.6 12.1 L-3.5 12.6 L-1.3 12.8 L0.9 12.7 L3.0 12.2 L5.1 11.4"
            + " L7.0 10.3 L8.6 8.8 L10.0 6.9 L10.9 4.7 L11.3 2.5 L11.4 0.5 L11.1 -1.4 L10.5 -3.1 L9.7 -4.6"
            + " L8.6 -5.8 L7.4 -6.8 L6.0 -7.5 L4.6 -7.9 L3.1 -8.1 L1.6 -7.9 L0.1 -7.5 L-1.2 -6.7 L-2.3 -5.9"
            + " L-3.3 -5.0 L-4.1 -4.0 L-4.7 -3.0 L-5.1 -1.9 L-5.4 -0.8 L-5.5 0.2 L-5.4 1.3 L-5.2 2.2 L-4.8 3.1"
            + " L-4.3 3.9 L-3.6 4.5 L-2.7 5.0 L-2.5 4.6 L-3.2 4.1 L-3.8 3.5 L-4.2 2.8 L-4.5 2.0 L-4.6 1.2"
            + " L-4.6 0.3 L-4.4 -0.6 L-4.1 -1.5 L-3.6 -2.4 L-3.0 -3.2 L-2.3 -4.0 L-1.4 -4.7 L-0.4 -5.3"
            + " L0.8 -5.8 L1.9 -6.1 L3.0 -6.1 L4.1 -5.9 L5.1 -5.5 L6.0 -4.9 L6.8 -4.1 L7.5 -3.1 L8.0 -2.1"
            + " L8.4 -0.8 L8.5 0.5 L8.3 2.1 L7.9 3.7 L7.2 5.3 L6.2 6.6 L5.0 7.6 L3.6 8.5 L2.1 9.1 L0.5 9.4"
            + " L-1.2 9.5 L-2.9 9.3 L-4.5 8.9 L-5.9 8.2 L-7.2 7.3 L-8.3 6.2 L-9.2 4.8 L-10.0 3.6 L-10.4 2.6"
            + " L-10.7 1.5 L-10.8 0.4 L-10.7 -0.8 L-10.4 -2.0 L-9.8 -3.4 L-9.0 -4.8 L-8.0 -6.3 L-6.6 -7.8"
            + " L-5.1 -9.4 L-3.2 -11.1 L-1.1 -12.8 L0.7 -14.4 L2.5 -16.1 L4.3 -17.9 L6.0 -19.7 L7.6 -21.5"
            + " L9.1 -23.4 L10.3 -25.2 L11.4 -27.1 L12.2 -29.0 L12.6 -30.9 L12.8 -32.8 L12.5 -34.6 L11.9 -36.4"
            + " L10.9 -37.9 L9.8 -38.8 L8.5 -39.2 L7.2 -39.0 L5.9 -38.4 L4.8 -37.4 L3.7 -36.0 L2.9 -34.3"
            + " L2.1 -32.4 L1.6 -30.3 L1.3 -27.9 L1.2 -25.5 L1.4 -22.9 L1.6 -20.3 L1.9 -17.4 L2.1 -14.3"
            + " L2.3 -11.0 L2.5 -7.6 L2.7 -4.1 L2.8 -0.5 L3.0 3.2 L3.0 6.8 L3.0 10.5 L3.0 14.1 L2.9 17.6"
            + " L2.7 20.8 L2.2 22.4 L1.5 23.8 L0.6 24.9 L-0.3 25.7 L-1.4 26.3 L-2.4 26.7 L-3.5 26.8 L-4.5 26.6"
            + " L-5.4 26.3 L-6.1 25.8 L-6.7 25.0 L-7.1 24.0 L-7.2 22.6 Z");
    /** Очко завитка скрипкового ключа відносно тієї ж опори. */
    static final double[] TREBLE_EYE = {-2.6, 4.8, 1.3};

    /** Басовий ключ; опора — (x, лінія фа). Дві крапки — окремо. */
    public static final PathData BASS = PathData.parse(
            "M-4 -5 c7 -2 14 2 14 9 c0 11 -11 18 -20 22 c8 -2 15 -9 15 -19 c0 -6 -3 -10 -7 -10 c-3 0 -5 2 -5 5 z");
    /** Альтовий ключ, верхня й нижня дуги; опора — (x, top). */
    public static final PathData ALTO_UP = PathData.parse(
            "M7 0 c8 0 6 9 2 10 c-3 1 -3 1 -3 1 c0 0 0 0 3 1 c4 1 6 8 -2 8 z");
    public static final PathData ALTO_DOWN = PathData.parse(
            "M7 40 c8 0 6 -9 2 -10 c-3 -1 -3 -1 -3 -1 c0 0 0 0 3 -1 c4 -1 6 -8 -2 -8 z");

    /** Чвертна пауза; опора — (cx, top). */
    public static final PathData REST_QUARTER = PathData.parse(
            "M-1.5 6 c3 3.4 4.6 5 5.4 6.6 c.8 1.6 .4 2.8 -1.4 4.6"
            + " c-2 2 -2.6 3.4 -1.8 5.2 c.6 1.4 2 2.8 3.2 3.8 c-2.6 -1.2 -5.2 -1.6 -6.6 -.6"
            + " c-1.6 1.2 -1.2 3.4 .6 5.4 c-3 -2 -4.8 -4.2 -4.6 -6.2 c.2 -2 2 -3 4.6 -2.6"
            + " c-2.4 -2.6 -3.4 -4.6 -2.8 -6.4 c.4 -1.4 1.8 -2.8 3.4 -4.2 l0 0 z");
    /** Стебло вісімної / шістнадцятої паузи (штрих 1.6); опора — (cx, top). */
    public static final PathData REST_STEM_1 = PathData.parse("M3.5 9 L-3 12");
    public static final PathData REST_STEM_2 = PathData.parse("M3.5 9 L-3 20");
    /** Гачок паузи (штрих 1.3); опора — (cx, y крапки). */
    public static final PathData REST_HOOK = PathData.parse("M3.2 1.4 c1.6 .6 3 1.4 4 2.6");
    /** Прапорець штиля вгору; опора — кінець штиля. Для штиля вниз — дзеркально. */
    public static final PathData FLAG = PathData.parse("M0 0 q9 5 8 15 q-3 -8 -8 -9");
    /** Прапорець форшлагу; опора — центр головки форшлагу. */
    public static final PathData GRACE_FLAG = PathData.parse("M3.9 -22 q6 3 5.5 10 q-2 -5.5 -5.5 -6");
    /** Головка ударних (хрестик); опора — центр головки. */
    public static final PathData CROSS_HEAD = PathData.parse(
            "M-6 -4.6 l1.9 -1.9 L0 -1.6 l4.1 -3 l1.9 1.9 L1.8 0 l4.1 4.6 l-1.9 1.9"
            + " L0 1.6 l-4.1 3 l-1.9 -1.9 L-1.8 0 Z");
    /** Акцент (штрих 1.6); опора — центр. */
    public static final PathData ACCENT = PathData.parse("M-5 -3 L5 0 L-5 3");
    /** Марcато (штрих 1.7) вістрям угору / вниз; опора — центр. */
    public static final PathData MARCATO_UP = PathData.parse("M-4 3.2 L0 -3.2 L4 3.2");
    public static final PathData MARCATO_DOWN = PathData.parse("M-4 -3.2 L0 3.2 L4 -3.2");

    // ---- знаки альтерації: опора — (лівий край, базова лінія) як у тексті розміру 15,
    //      центр знака на y − 5 ----

    private static final double C = -5;

    public static final PathData SHARP = PathData.parse(
            rect(1.6, C - 6.5, 1.1, 14.5) + rect(5.0, C - 8.0, 1.1, 14.5)
            + slab(0.2, C - 1.6, 7.4, C - 3.6, 2.2) + slab(0.2, C + 3.6, 7.4, C + 1.6, 2.2));
    public static final PathData NATURAL = PathData.parse(
            rect(1.4, C - 8.0, 1.1, 12.5) + rect(5.4, C - 4.5, 1.1, 12.5)
            + slab(1.4, C - 1.8, 6.5, C - 3.2, 2.0) + slab(1.4, C + 3.4, 6.5, C + 2.0, 2.0));
    public static final PathData FLAT = PathData.parseEvenOdd(flat(0));
    public static final PathData DOUBLE_FLAT = PathData.parseEvenOdd(flat(0) + flat(5.2));
    public static final PathData DOUBLE_SHARP = PathData.parse(
            "M1 " + (C - 4) + " L3 " + (C - 4) + " L4 " + (C - 1.5) + " L5 " + (C - 4) + " L7 " + (C - 4)
            + " L7 " + (C - 2) + " L5.2 " + C + " L7 " + (C + 2) + " L7 " + (C + 4) + " L5 " + (C + 4)
            + " L4 " + (C + 1.5) + " L3 " + (C + 4) + " L1 " + (C + 4) + " L1 " + (C + 2)
            + " L2.8 " + C + " L1 " + (C - 2) + " Z");

    public static PathData accidental(int alter) {
        switch (alter) {
            case 2:
                return DOUBLE_SHARP;
            case 1:
                return SHARP;
            case 0:
                return NATURAL;
            case -1:
                return FLAT;
            default:
                return DOUBLE_FLAT;
        }
    }

    /** Символ, яким знак записаний у прототипі (ACC_GLYPH) — для звірки. */
    static String accidentalChar(int alter) {
        switch (alter) {
            case 2:
                return "𝄪";
            case 1:
                return "♯";
            case 0:
                return "♮";
            case -1:
                return "♭";
            default:
                return "𝄫";
        }
    }

    private static String rect(double x, double y, double w, double h) {
        return "M" + x + " " + y + " L" + (x + w) + " " + y + " L" + (x + w) + " " + (y + h)
                + " L" + x + " " + (y + h) + " Z ";
    }

    /** Похила смуга від (x1, y1) до (x2, y2) завтовшки t по вертикалі. */
    private static String slab(double x1, double y1, double x2, double y2, double t) {
        return "M" + x1 + " " + (y1 - t / 2) + " L" + x2 + " " + (y2 - t / 2) + " L" + x2 + " " + (y2 + t / 2)
                + " L" + x1 + " " + (y1 + t / 2) + " Z ";
    }

    /** Бемоль: штиль і «черевце» з порожниною; dx — зсув для дубль-бемоля. */
    private static String flat(double dx) {
        double s = 1.4 + dx, cy = C + 1;
        return "M" + s + " " + (C - 12) + " L" + (s + 1.1) + " " + (C - 12)
                + " L" + (s + 1.1) + " " + (cy - 1.6)
                + " C" + (s + 2.6) + " " + (cy - 3.6) + " " + (s + 6.4) + " " + (cy - 4.2) + " " + (s + 6.2) + " " + (cy - 1.4)
                + " C" + (s + 6.0) + " " + (cy + 1.0) + " " + (s + 3.4) + " " + (cy + 2.8) + " " + s + " " + (cy + 4.6)
                + " Z M" + (s + 1.1) + " " + (cy - 0.2)
                + " C" + (s + 2.4) + " " + (cy - 2.0) + " " + (s + 4.6) + " " + (cy - 2.4) + " " + (s + 4.4) + " " + (cy - 0.8)
                + " C" + (s + 4.2) + " " + (cy + 0.6) + " " + (s + 2.6) + " " + (cy + 2.0) + " " + (s + 1.1) + " " + (cy + 3.0)
                + " Z ";
    }
}
