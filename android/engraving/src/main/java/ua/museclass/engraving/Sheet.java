package ua.museclass.engraving;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import ua.museclass.musicxml.Bar;
import ua.museclass.musicxml.Meter;
import ua.museclass.musicxml.Part;

/**
 * Партія, розкладена на системи під задану ширину. Не як у прототипі (2–3
 * такти на рядок): такти набираються жадібно, поки влазять, і розтягуються на
 * всю ширину. Останній рядок розтягуємо, лише якщо він заповнений хоча б на
 * {@link Options#minFill}, інакше лишаємо природну ширину.
 */
public final class Sheet {
    public static final class Options {
        /** Скільки вільного місця над і під станом, крім того, що займають ноти. */
        public double pad = 8;
        public double minFill = 0.7;
        public boolean barNumbers = true;
    }

    /** Одна система: фігури вже зсунуті так, що верх системи — y = 0. */
    public static final class System {
        public final List<Shape> shapes;
        public final double width;
        public final double height;
        /** Такти [from, to), нумерація з 0. */
        public final int from;
        public final int to;
        /** y верхньої лінії стану в системі. */
        public final double staffTop;

        System(List<Shape> shapes, double width, double height, int from, int to, double staffTop) {
            this.shapes = Collections.unmodifiableList(shapes);
            this.width = width;
            this.height = height;
            this.from = from;
            this.to = to;
            this.staffTop = staffTop;
        }
    }

    /** Лінії стану — від лівого краю, ключ стоїть на них (прототип починає їх після ключа). */
    static final double STAFF_START = 12;

    private Sheet() {
    }

    public static List<System> layout(Part part, double width, Options opt) {
        List<Staff.Item> items = Staff.items(part.measures());
        int n = items.size();
        double[] w = new double[n];
        for (int i = 0; i < n; i++) w[i] = Staff.measureWidth(items.get(i));

        // тональність і розмір, що діють з початку кожного такту (зміна в самому такті — теж)
        int[] key = new int[n];
        Meter[] meter = new Meter[n];
        int fifths = part.fifths();
        Meter m = part.meter();
        for (int i = 0; i < n; i++) {
            Bar b = part.measures().get(i).bar();
            if (b != null && b.fifths() != null) fifths = b.fifths();
            if (b != null && b.meter() != null) m = b.meter();
            key[i] = fifths;
            meter[i] = m;
        }

        List<System> out = new ArrayList<>();
        int from = 0;
        while (from < n) {
            boolean first = from == 0;
            double padL = Staff.padLeft(part.clef(), key[from], first);
            double room = width - padL - 16;
            double sum = w[from];
            int to = from + 1;
            while (to < n && sum + w[to] <= room) sum += w[to++];
            boolean last = to == n;
            double k = room / sum;
            // останній рядок не розтягуємо, якщо він короткий; занадто широкий один такт — стискаємо
            if (last && k > 1 && sum < room * opt.minFill) k = 1;

            Staff.Options o = new Staff.Options();
            o.clef = part.clef();
            o.fifths = key[from];
            o.meter = first ? part.meter() : meter[from];
            o.timesig = first;
            o.end = last;
            o.barNumbers = opt.barNumbers;
            o.stretch = k;
            o.staffStart = STAFF_START;
            Staff.Result r = Staff.render(items.subList(from, to), o);
            out.add(place(r, from, to, opt.pad));
            from = to;
        }
        return out;
    }

    /** Знаходить, скільки ноти й позначки виходять за стан, і зсуває систему вниз. */
    private static System place(Staff.Result r, int from, int to, double pad) {
        double[] box = {Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
        for (Shape s : r.shapes) s.bounds(box);
        // щонайменше місце під номер такту зверху і під штилі вниз знизу
        double above = Math.max(-box[1], 18) + pad;
        double below = Math.max(box[3], 40 + 12) + pad;
        List<Shape> moved = new ArrayList<>(r.shapes.size());
        for (Shape s : r.shapes) moved.add(s.moved(above));
        return new System(moved, r.total, above + below, from, to, above);
    }
}
