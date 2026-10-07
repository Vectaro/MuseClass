package ua.museclass.engraving;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Контур у мові SVG-шляхів, переведений в абсолютні команди M / L / C / Q / Z
 * відносно початку гліфа. Розуміє M L H V C Q Z і їхні відносні варіанти —
 * усе, чим малює prototype/engine.js.
 */
public final class PathData {
    /** Одна команда: тип і точки (для C — три, для Q — дві, для M/L — одна). */
    public static final class Cmd {
        public final char op;
        public final double[] pts;

        Cmd(char op, double... pts) {
            this.op = op;
            this.pts = pts;
        }
    }

    private static final Pattern TOKEN = Pattern.compile(
            "[MmLlHhVvCcQqZz]|[-+]?(?:\\d+\\.?\\d*|\\.\\d+)(?:[eE][-+]?\\d+)?");

    private final List<Cmd> cmds;
    public final double firstX;
    public final double firstY;
    /** Заливка за правилом even-odd: вкладений контур — дірка (черевце бемоля). */
    public final boolean evenOdd;

    private PathData(List<Cmd> cmds, boolean evenOdd) {
        this.cmds = Collections.unmodifiableList(cmds);
        this.evenOdd = evenOdd;
        Cmd c = cmds.isEmpty() ? null : cmds.get(0);
        firstX = c == null ? 0 : c.pts[0];
        firstY = c == null ? 0 : c.pts[1];
    }

    public List<Cmd> commands() {
        return cmds;
    }

    public static PathData parse(String d) {
        return parse(d, false);
    }

    public static PathData parseEvenOdd(String d) {
        return parse(d, true);
    }

    private static PathData parse(String d, boolean evenOdd) {
        List<String> t = new ArrayList<>();
        Matcher m = TOKEN.matcher(d);
        while (m.find()) t.add(m.group());
        List<Cmd> out = new ArrayList<>();
        double x = 0, y = 0, sx = 0, sy = 0;
        char op = 0;
        int i = 0;
        while (i < t.size()) {
            String s = t.get(i);
            if (Character.isLetter(s.charAt(0))) {
                op = s.charAt(0);
                i++;
                if (op == 'Z' || op == 'z') {
                    out.add(new Cmd('Z'));
                    x = sx;
                    y = sy;
                    continue;
                }
            }
            boolean rel = Character.isLowerCase(op);
            double ox = rel ? x : 0, oy = rel ? y : 0;
            switch (Character.toUpperCase(op)) {
                case 'M':
                    x = ox + num(t, i++);
                    y = oy + num(t, i++);
                    sx = x;
                    sy = y;
                    out.add(new Cmd('M', x, y));
                    op = rel ? 'l' : 'L'; // далі пари — неявні L
                    break;
                case 'L':
                    x = ox + num(t, i++);
                    y = oy + num(t, i++);
                    out.add(new Cmd('L', x, y));
                    break;
                case 'H':
                    x = ox + num(t, i++);
                    out.add(new Cmd('L', x, y));
                    break;
                case 'V':
                    y = oy + num(t, i++);
                    out.add(new Cmd('L', x, y));
                    break;
                case 'C': {
                    double x1 = ox + num(t, i++), y1 = oy + num(t, i++);
                    double x2 = ox + num(t, i++), y2 = oy + num(t, i++);
                    x = ox + num(t, i++);
                    y = oy + num(t, i++);
                    out.add(new Cmd('C', x1, y1, x2, y2, x, y));
                    break;
                }
                case 'Q': {
                    double x1 = ox + num(t, i++), y1 = oy + num(t, i++);
                    x = ox + num(t, i++);
                    y = oy + num(t, i++);
                    out.add(new Cmd('Q', x1, y1, x, y));
                    break;
                }
                default:
                    throw new IllegalArgumentException("непідтримана команда шляху: " + op);
            }
        }
        return new PathData(out, evenOdd);
    }

    private static double num(List<String> t, int i) {
        return Double.parseDouble(t.get(i));
    }
}
