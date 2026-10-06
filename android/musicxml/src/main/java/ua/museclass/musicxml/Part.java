package ua.museclass.musicxml;

import java.util.Collections;
import java.util.List;

/**
 * Партія — самостійний об'єкт зі своїм ключем, тональністю, розміром і
 * строєм. Фортепіанний part з кількома staff дає кілька партій.
 */
public final class Part {
    private final String name;
    private final String instrument;
    private final String clef;
    private final Meter meter;
    private final int fifths;
    private final int transpose;
    private final List<Measure> measures;

    Part(String name, String instrument, String clef, Meter meter, int fifths, int transpose,
         List<Measure> measures) {
        this.name = name;
        this.instrument = instrument;
        this.clef = clef;
        this.meter = meter;
        this.fifths = fifths;
        this.transpose = transpose;
        this.measures = measures;
    }

    public String name() {
        return name;
    }

    public String instrument() {
        return instrument;
    }

    /** treble / bass / alto / perc. */
    public String clef() {
        return clef;
    }

    /** Початковий розмір; зміни — у {@link Bar#meter()}. */
    public Meter meter() {
        return meter;
    }

    /** Початкова тональність: кількість дієзів (+) чи бемолів (−). */
    public int fifths() {
        return fifths;
    }

    /**
     * Стрій з протилежним знаком, як у прототипі: для труби in B♭ це 2 —
     * записане звучить на 2 півтони нижче.
     */
    public int transpose() {
        return transpose;
    }

    public List<Measure> measures() {
        return Collections.unmodifiableList(measures);
    }
}
